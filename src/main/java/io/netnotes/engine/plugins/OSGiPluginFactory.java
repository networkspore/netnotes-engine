package io.netnotes.engine.plugins;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.framework.FrameworkFactory;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.utils.Version;

/**
 * Enhanced OSGi Plugin Factory with NoteFile integration.
 */
public class OSGiPluginFactory {
    public static final String PLUGINS = "plugins";
    public static final NoteBytesReadOnly HEADER = new NoteBytesReadOnly(PLUGINS);  
    public static final String PLUGIN_DATA_V1 = "1.0.0";
    public static final NoteStringArrayReadOnly PLUGINS_REGISTRY_PATH = 
        new NoteStringArrayReadOnly(PLUGINS);
    
    private final AppDataInterface m_appData;
    private final Framework m_framework;
    private final OSGiBundleLoader m_bundleLoader;
    private String m_pluginSaveFormat = PLUGIN_DATA_V1;
    
    // Track loaded bundles
    private final Map<NoteBytes, Bundle> m_loadedBundles = new ConcurrentHashMap<>();

    public OSGiPluginFactory(AppDataInterface appDataInterface) {
        m_appData = appDataInterface;
        
        // Initialize OSGi framework
        Map<String, String> config = new HashMap<>();
        config.put("org.osgi.framework.storage.clean", "onFirstInit");
        m_framework = new FrameworkFactory().newFramework(config);
        
        try {
            m_framework.start();
            System.out.println("OSGi Framework started");
        } catch (BundleException e) {
            throw new RuntimeException("Failed to start OSGi framework", e);
        }
        
        m_bundleLoader = new OSGiBundleLoader(
            m_framework.getBundleContext(), 
            m_appData.getExecService()
        );
    }
    
    /**
     * Get the OSGi bundle context.
     */
    public BundleContext getBundleContext() {
        return m_framework.getBundleContext();
    }
    
    /**
     * Load a plugin from a NoteFile.
     */
    public CompletableFuture<Bundle> loadPluginFromNoteFile(NoteBytes pluginId, NoteFile noteFile) {
        return m_bundleLoader.loadBundleFromNoteFile(pluginId, noteFile)
            .thenApply(bundle -> {
                m_loadedBundles.put(pluginId, bundle);
                return bundle;
            });
    }
    
    /**
     * Unload a plugin bundle.
     */
    public CompletableFuture<Void> unloadPlugin(NoteBytes pluginId) {
        Bundle bundle = m_loadedBundles.remove(pluginId);
        if (bundle != null) {
            return m_bundleLoader.unloadBundle(bundle);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Get a loaded bundle by plugin ID.
     */
    public Bundle getLoadedBundle(NoteBytes pluginId) {
        return m_loadedBundles.get(pluginId);
    }
    
    /**
     * Check if a plugin is loaded.
     */
    public boolean isPluginLoaded(NoteBytes pluginId) {
        return m_loadedBundles.containsKey(pluginId);
    }
    
    /**
     * Get all loaded bundles.
     */
    public Map<NoteBytes, Bundle> getLoadedBundles() {
        return new HashMap<>(m_loadedBundles);
    }

    /**
     * Save plugins registry to NoteFile.
     */
    private CompletableFuture<Void> savePluginsRegistryV1(
        PipedOutputStream outputStream, 
        List<PluginMetaData> pluginsList
    ) {
        return CompletableFuture.runAsync(() -> {
            try (NoteBytesWriter writer = new NoteBytesWriter(outputStream)) {
                writer.write(new NoteBytesPair(HEADER, new NoteBytesPair[]{
                    new NoteBytesPair(NoteMessaging.Headings.VERSION_KEY, PLUGIN_DATA_V1)
                }));
                
                for (PluginMetaData pluginMetaData : pluginsList) {
                    writer.write(pluginMetaData.getSaveData());
                }
                 
            } catch (IOException e) {
                throw new CompletionException("savePlugins IOException", e);
            }
        }, getExecService());
    }

    /**
     * Load saved plugins from NoteFile.
     */
    public CompletableFuture<Stream<PluginMetaData>> getSavedPlugins(NoteFile pluginsNoteFile) {
        PipedOutputStream readOnlyOutput = new PipedOutputStream();
        
        pluginsNoteFile.readOnly(readOnlyOutput);
        
        return CompletableFuture.supplyAsync(() -> {
            try (NoteBytesReader reader = new NoteBytesReader(new PipedInputStream(readOnlyOutput))) {
                
                // Read header
                NoteBytes nextNoteBytes = reader.nextNoteBytes();
                if (nextNoteBytes != null && !nextNoteBytes.equals(HEADER)) {
                    throw new IllegalStateException("Invalid file header");
                }
                NoteBytesMetaData nextMetaData = reader.nextMetaData();
                
                if (nextMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                    throw new IllegalStateException("Object format expected for plugins data");
                }
                    
                NoteBytesMap headerMap = new NoteBytesMap(reader.readNextBytes(nextMetaData.getLength()));
                NoteBytes versionBytes = headerMap.get(NoteMessaging.Headings.VERSION_KEY);
                String pluginHeaderVersion = versionBytes != null ? 
                    versionBytes.getAsString() : PLUGIN_DATA_V1;
                
                switch (pluginHeaderVersion) {
                    case PLUGIN_DATA_V1:
                        return loadPluginMetaDataV1(reader);
                }

                throw new IllegalStateException("Cannot read the plugin data version");
                
            } catch (IOException e) {
                throw new RuntimeException("Failed to load plugin registry", e);
            }
        }, getExecService());
    }

    protected ExecutorService getExecService() {
        return m_appData.getExecService();
    }
    
    /**
     * Load enabled plugin bundles from NoteFile.
     */
    public CompletableFuture<Map<NoteBytes, Bundle>> loadEnabledPluginBundles(NoteFile noteFile) {
        return getSavedPlugins(noteFile)
            .thenCompose(pluginsStream -> filterEnabledLoadToHashMap(pluginsStream));
    }
    
    /**
     * Load plugin metadata version 1.
     */
    private Stream<PluginMetaData> loadPluginMetaDataV1(NoteBytesReader reader) throws IOException {
        Stream.Builder<PluginMetaData> builder = Stream.builder();
        NoteBytes pluginIdBytes = reader.nextNoteBytes();
        
        while (pluginIdBytes != null && pluginIdBytes.byteLength() > 0) {
            NoteBytes value = reader.nextNoteBytes();
            if (value.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                NoteBytesMap map = new NoteBytesMap(value.get());
                NoteBytes enabled = map.get(PluginMetaData.ENABLED_KEY);
                
                if (enabled != null && enabled.getAsBoolean()) {
                    NoteBytes pathValue = map.get(PluginMetaData.NOTE_PATH_KEY);
                    if (pathValue != null && 
                        pathValue.getType() == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
                        NoteBytes versionValue = map.get(PluginMetaData.VERSION_KEY);

                        PluginMetaData pluginMetaData = new PluginMetaData(
                            pluginIdBytes,
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
 
    /**
     * Filter enabled plugins and load them.
     */
    private CompletableFuture<Map<NoteBytes, Bundle>> filterEnabledLoadToHashMap(
        Stream<PluginMetaData> plugins
    ) { 
        ConcurrentHashMap<NoteBytes, Bundle> hashMap = new ConcurrentHashMap<>();
        
        return CompletableFuture.allOf(
            plugins
                .filter(metaData -> metaData.isEnabled())
                .map(metadata -> 
                    loadPlugin(metadata)
                        .thenAccept(bundle -> hashMap.put(metadata.getPluginId(), bundle))
                        .exceptionally(ex -> {
                            System.err.println("Failed to load plugin: " + 
                                metadata.getPluginId() + " err: " + ex.getMessage());
                            return null;
                        })
                )
                .toArray(CompletableFuture[]::new)
        ).thenCompose(v -> CompletableFuture.completedFuture(hashMap));
    }
    
    /**
     * Load a plugin from metadata.
     */
    private CompletableFuture<Bundle> loadPlugin(PluginMetaData metadata) {
        return m_appData.getNoteFile(metadata.geNotePath())
            .thenCompose(pluginFile -> 
                loadPluginFromNoteFile(metadata.getPluginId(), pluginFile)
            );
    }
    
    /**
     * Shutdown the OSGi framework and all plugins.
     */
    public void shutdown() throws Exception {
        System.out.println("Shutting down OSGi framework...");
        
        // Unload all plugins first
        List<CompletableFuture<Void>> unloadFutures = new ArrayList<>();
        for (NoteBytes pluginId : new ArrayList<>(m_loadedBundles.keySet())) {
            unloadFutures.add(unloadPlugin(pluginId));
        }
        
        // Wait for all plugins to unload
        CompletableFuture.allOf(unloadFutures.toArray(new CompletableFuture[0])).join();
        
        // Stop the framework
        m_framework.stop();
        m_framework.waitForStop(5000);
        System.out.println("OSGi framework stopped");
    }
    
    /**
     * Get the OSGi framework instance.
     */
    public Framework getFramework() {
        return m_framework;
    }
}