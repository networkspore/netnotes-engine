package io.netnotes.engine.plugins;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.framework.FrameworkFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.launch.Framework;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.utils.Version;

public class OSGiPluginFactory {
    public static final String PLUGINS = "plugins";
    public static final NoteBytesReadOnly HEADER = new NoteBytesReadOnly(PLUGINS);  
    public static final String PLUGIN_DATA_V1 = "1.0.0";
    // Plugin registry file path
    public static final NoteStringArrayReadOnly PLUGINS_REGISTRY_PATH = new NoteStringArrayReadOnly(PLUGINS);
    
    private final AppDataInterface m_appData;
    private final Framework m_framework;
    private String m_pluginSaveFormat = PLUGIN_DATA_V1;

    public OSGiPluginFactory(AppDataInterface appDataInterface){
        m_appData = appDataInterface;
        m_framework = new FrameworkFactory().newFramework(new HashMap<>());
    }
    

   /* public CompletableFuture<NoteBytesObject> addPlugin(NoteFile pluginsNoteFile, List<PluginMetaData> pluginList){
        PipedOutputStream readOutput = new PipedOutputStream();
        PipedOutputStream writeOutput = new PipedOutputStream();
        
        if(m_pluginSaveFormat == null){
            return CompletableFuture.failedFuture(new CompletionException("savePlugins.m_pluginsSaveFormat NullPointerException",
                new NullPointerException("m_pluginSaveFormat is null")));
        }

         pluginsNoteFile.readWrite(readOutput, writeOutput);


        switch(m_pluginSaveFormat){
            case PLUGIN_DATA_V1:
                savePluginsRegistryV1(writeOnlyOutput, pluginList);
            break;
            default:
                return CompletableFuture.failedFuture(new IllegalStateException("No suitable save format selected"));
        }

        return;
    }*/

    private CompletableFuture<Void> savePluginsRegistryV1(PipedOutputStream outputStream, List<PluginMetaData> pluginsList){
        return CompletableFuture.runAsync(()->{
            try(NoteBytesWriter writer = new NoteBytesWriter(outputStream)){
                writer.write(new NoteBytesPair(HEADER, new NoteBytesPair[]{
                    new NoteBytesPair(NoteMessaging.Headings.VERSION_KEY, PLUGIN_DATA_V1)
                }));
                
                for(PluginMetaData pluginMetaData : pluginsList){
                    writer.write(pluginMetaData.getSaveData());
                }
                 
            }catch(IOException e){
                throw new CompletionException("savePlugins IOException", e);
            }
        });
    }

    public CompletableFuture<Stream<PluginMetaData>> getSavedPlugins(NoteFile pluginsNoteFile) {
       
        PipedOutputStream readOnlyOutput = new PipedOutputStream();
        
        // Start the read operation
        pluginsNoteFile.readOnly(readOnlyOutput);
        
        return CompletableFuture.supplyAsync(() -> {
            try (
                NoteBytesReader reader = new NoteBytesReader(new PipedInputStream(readOnlyOutput));
            ) {
                
                // Read header
                NoteBytes nextNoteBytes = reader.nextNoteBytes();
                if(nextNoteBytes != null && !nextNoteBytes.equals(HEADER)){
                    throw new IllegalStateException("Invalid file header");
                }
                NoteBytesMetaData nextMetaData = reader.nextMetaData();

                
                if (nextMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                    throw new IllegalStateException("Object format expected for plugins data");
                }
                    
                NoteBytesMap headerMap = new NoteBytesMap(reader.readNextBytes(nextMetaData.getLength()));

                NoteBytes versionBytes = headerMap.get(NoteMessaging.Headings.VERSION_KEY);
                String pluginHeaderVersion =versionBytes != null ? versionBytes.getAsString() : PLUGIN_DATA_V1;
                
                switch(pluginHeaderVersion){
                    case PLUGIN_DATA_V1:
                        return loadPluginMetaDataV1(reader);
                }

                throw new IllegalStateException("Cannot read the plugin data version");
                
            } catch (IOException e) {
                throw new RuntimeException("Failed to load plugin registry", e);
            }
        }, m_appData.getExecService());
    }

    
    public CompletableFuture<Map<NoteBytesReadOnly, Bundle>> loadEnabledPluginBundles(NoteFile noteFile) {
        return getSavedPlugins(noteFile)
            .thenCompose(pluginsStream -> filterEnabledLoadToHashMap(pluginsStream));
          
    }
    
    private Stream<PluginMetaData> loadPluginMetaDataV1(NoteBytesReader reader) throws IOException{
        Stream.Builder<PluginMetaData> builder = Stream.builder();
        NoteBytes pluginIdBytes = reader.nextNoteBytes();
        while(pluginIdBytes != null && pluginIdBytes.byteLength() > 0){
            NoteBytes value = reader.nextNoteBytes();
            if(value.getByteDecoding().getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
                NoteBytesMap map = new NoteBytesMap(value.get());
                NoteBytes enabled = map.get(PluginMetaData.ENABLED_KEY);
                if(enabled != null && enabled.getAsBoolean()){
                    NoteBytes pathValue = map.get(PluginMetaData.NOTE_PATH_KEY);
                    if(pathValue != null && pathValue.getByteDecoding().getType() == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE){
                        NoteBytes versionValue = map.get(PluginMetaData.VERSION_KEY);

                        PluginMetaData pluginMetaData = new PluginMetaData(
                            new NoteBytesReadOnly(pluginIdBytes),
                            versionValue != null ? versionValue.getAsString() : Version.UKNOWN_VERSION,
                            true,
                            new NoteStringArrayReadOnly(pathValue)
                        );
                        builder.accept(pluginMetaData);
                    }
                }
            }
            
            pluginIdBytes = reader.nextNoteBytes();
        }

        return builder.build();
    }
 
    private CompletableFuture<Map<NoteBytesReadOnly, Bundle>> filterEnabledLoadToHashMap(Stream<PluginMetaData> plugins) { 
        
        ConcurrentHashMap<NoteBytesReadOnly, Bundle> hashMap = new ConcurrentHashMap<>();
        BundleContext ctx = m_framework.getBundleContext();
        return CompletableFuture.allOf( plugins.filter(metaData->metaData.isEnabled()).map(map ->
            loadPlugin(ctx, map.getPluginId(), map) .thenAccept(bundle -> hashMap.put(map.getPluginId(), bundle))
            .exceptionally(ex -> {
                System.err.println("Failed to load plugin: " + map.getPluginId() + " err: " + ex.getMessage());
                return null;
            })).toArray(CompletableFuture[]::new)).thenCompose(v ->CompletableFuture.completedFuture(hashMap));
    }
    
    private CompletableFuture<Bundle> loadPlugin(BundleContext ctx, NoteBytesReadOnly pluginId, PluginMetaData metadata) {
        return m_appData.getNoteFile(metadata.geNotePath())
            .thenCompose(pluginFile -> loadPluginFromNoteFile(ctx, pluginId, pluginFile));
    }
    
    private CompletableFuture<Bundle> loadPluginFromNoteFile(BundleContext ctx, NoteBytesReadOnly pluginId,
        NoteFile pluginFile
    ) {
        
        PipedOutputStream pluginStream = new PipedOutputStream();
        
        pluginFile.readOnly(pluginStream);
      
        return CompletableFuture.supplyAsync(()->{
            try (PipedInputStream bundleStream = new PipedInputStream(pluginStream)) {
                // Install bundle directly from stream
                String bundleLocation = "plugin://" + pluginId.getAsUrlSafeString();
                
                return ctx.installBundle(bundleLocation, bundleStream);
                
               
            } catch (Exception e) {
                throw new CompletionException("Failed to install bundle", e);
            }
        }, m_appData.getExecService());
 
    }


   
    public void shutdown() throws Exception {
        m_framework.stop();
        m_framework.waitForStop(5000);
    }
 
    
    /**
     * Register nodes provided by the plugin bundle
     */
    /*private CompletableFuture<ConcurrentHashMap<NoteBytesReadOnly, Node>> registerPluginNodes(Bundle bundle) {
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
    }*/

    /**
     * Shutdown all plugins
     */
    /*public CompletableFuture<Void> shutdownAllPlugins() {
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
    }*/
    
    
   
}