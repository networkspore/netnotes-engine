package io.netnotes.engine.plugins;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

import io.netnotes.engine.AppData;
import io.netnotes.engine.Node;
import io.netnotes.engine.NoteFile;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class OSGiNodePluginFactory {
    public static final NoteBytes PLUGIN_FACTORY_VERSION = new NoteBytes(1);
    // Plugin registry file path
    private static final NoteStringArrayReadOnly PLUGINS_REGISTRY_PATH = new NoteStringArrayReadOnly(".", "plugins", "registry");
    

    // Plugin metadata keys

    private final AppData appData;
    private final BundleContext bundleContext;
    private final ServiceTracker<Node, Node> nodeTracker;
    private final Map<NoteBytesReadOnly, Bundle> pluginBundles = new ConcurrentHashMap<>();
    
    


     public OSGiNodePluginFactory(AppData appData, BundleContext bundleContext) {
        this.appData = appData;
        this.bundleContext = bundleContext;
        
        // Set up service tracker for Node services
        this.nodeTracker = new ServiceTracker<>(bundleContext, Node.class, null);
        this.nodeTracker.open();
    }
    
    /**
     * Bootstrap method - discovers and loads all plugins from persistence
     */
    public CompletableFuture<Void> initializeAllPlugins() {
        return appData.getNoteFileRegistry()
            .getNoteFile(PLUGINS_REGISTRY_PATH)
            .thenCompose(this::loadPluginRegistry)
            .thenCompose(this::startDiscoveredPlugins);
    }
    
    /**
     * Load plugin registry file and parse plugin metadata
     */
    private CompletableFuture<Stream<PluginMetaData>> loadPluginRegistry(NoteFile registryFile) {
        return CompletableFuture.supplyAsync(() -> {
            PipedOutputStream readOnlyOutput = new PipedOutputStream();
            
            // Start the read operation
            registryFile.readOnly(readOnlyOutput)
                .thenRun(() -> {}); // Just consume the result
            
            try (PipedInputStream inputStream = new PipedInputStream(readOnlyOutput);
                 NoteBytesReader reader = new NoteBytesReader(inputStream)) {
                
                // Read header
                NoteBytes header = reader.nextNoteBytes();
                if(header.equals(PLUGIN_FACTORY_VERSION)){
                    
                }
                NoteBytesMetaData headerMetaData = reader.nextMetaData();
                
                if (headerMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
                    throw new IOException("Invalid plugin registry body type");
                }
                
                Stream.Builder<PluginMetaData> builder = Stream.builder();

                NoteBytes value = reader.nextNoteBytes();

                while(value != null){

                    builder.accept(new PluginMetaData(value));

                    value = reader.nextNoteBytes();

                }

                return builder.build();
                
            } catch (IOException e) {
                throw new RuntimeException("Failed to load plugin registry", e);
            }
        }, appData.getExecService());
    }
 
    /**
     * Start all discovered plugins
     */
    private CompletableFuture<Void> startDiscoveredPlugins(Stream<PluginMetaData> plugins) {
        return CompletableFuture.allOf(
            plugins 
            .filter(map -> map.isEnabled())
            .map(map -> loadAndStartPlugin(map.getPluginId(), map))
            .toArray(CompletableFuture[]::new));
    }
    
    /**
     * Load and start individual plugin
     */
    private CompletableFuture<ConcurrentHashMap<NoteBytesReadOnly, Node>> loadAndStartPlugin(NoteBytesReadOnly pluginId, PluginMetaData metadata) {
        return appData.getNoteFileRegistry()
            .getNoteFile(metadata.geNotePath())
            .thenCompose(pluginFile -> loadPluginFromNoteFile(pluginId, pluginFile))
            .thenCompose(this::registerPluginNodes);
    }
    
    /**
     * Load plugin bundle from NoteFile stream
     */
    private CompletableFuture<Bundle> loadPluginFromNoteFile(NoteBytesReadOnly pluginId, NoteFile pluginFile) {
        return CompletableFuture.supplyAsync(() -> {
            PipedOutputStream pluginStream = new PipedOutputStream();
            
            // Start reading plugin file
            CompletableFuture<Void> readFuture = pluginFile.readOnly(pluginStream)
                .thenRun(() -> {});
            
            try (PipedInputStream bundleStream = new PipedInputStream(pluginStream)) {
                
                // Install bundle directly from stream
                String bundleLocation = "plugin://" + pluginId.getAsUrlSafeString();
                Bundle bundle = bundleContext.installBundle(bundleLocation, bundleStream);
                
                // Store bundle reference
                pluginBundles.put(pluginId, bundle);
                
                // Start the bundle
                bundle.start();
                
                // Wait for read to complete
                readFuture.join();
                
                return bundle;
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to load plugin: " + pluginId, e);
            }
        }, appData.getExecService());
    }
    
    /**
     * Register nodes provided by the plugin bundle
     */
    private CompletableFuture<ConcurrentHashMap<NoteBytesReadOnly, Node>> registerPluginNodes(Bundle bundle) {
        return CompletableFuture.supplyAsync(() -> {
            // Wait briefly for services to register
            try { Thread.sleep(100); } catch (InterruptedException e) {}

            // Get currently available Node services
            Node[] nodes = nodeTracker.getServices(new Node[0]);
            ConcurrentHashMap<NoteBytesReadOnly, Node> map = new ConcurrentHashMap<>();
            if (nodes != null) {

                for (Node node : nodes) {
                    // Check if this node came from our bundle
                    ServiceReference<Node> ref = nodeTracker.getServiceReference();
                    if (ref != null && ref.getBundle().equals(bundle)) {
                        map.putIfAbsent(node.getNodeId(), node);
                    }
                }
            }
            return map;
        }, appData.getExecService());
    }

    /**
     * Shutdown all plugins
     */
    public CompletableFuture<Void> shutdownAllPlugins() {
        return CompletableFuture.runAsync(() -> {
            pluginBundles.values().forEach(bundle -> {
                try {
                    bundle.stop();
                    bundle.uninstall();
                } catch (BundleException e) {
                    // Log error but continue
                    System.err.println("Failed to stop plugin bundle: " + e.getMessage());
                }
            });
            
            pluginBundles.clear();
            nodeTracker.close();
        }, appData.getExecService());
    }
    
    
   
}