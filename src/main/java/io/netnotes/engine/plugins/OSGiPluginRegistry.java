package io.netnotes.engine.plugins;



import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.noteFiles.NoteFile;
/**
 * Manages the plugin registry - a NoteFile that stores information about installed plugins.
 * The registry persists plugin metadata including: plugin ID, version, JAR path, and enabled status.
 */
public class OSGiPluginRegistry {
    public static final String PLUGINS = "plugins";
    public static final String REGISTRY_NAME = "plugins-registry";

    public static final NoteStringArrayReadOnly PLUGINS_REGISTRY_PATH = 
        new NoteStringArrayReadOnly(new String[]{PLUGINS, REGISTRY_NAME});

    private static final String REGISTRY_VERSION = "1.0.0";
   
    private static final NoteBytes REGISTRY_HEADER = new NoteBytes(REGISTRY_NAME);
    
    
    private final AppDataInterface m_appData;
    private final ExecutorService m_execService;
    

    // In-memory cache of installed plugins
    private final ConcurrentHashMap<NoteBytes, PluginMetaData> m_installedPlugins;
    
    public OSGiPluginRegistry(AppDataInterface appData, ExecutorService execService) {
        m_appData = appData;
        m_execService = execService;
        m_installedPlugins = new ConcurrentHashMap<>();
    
    }
    
    /**
     * Initialize the registry - load existing plugins or create new registry.
     */
    public CompletableFuture<Void> initialize() {
        return loadRegistry()
            .exceptionally(error -> {
                // If registry doesn't exist or fails to load, create empty one
                System.out.println("No existing registry found or error loading, creating new: " + error.getMessage());
                return new ArrayList<PluginMetaData>();
            })
            .thenAccept(plugins -> {
                m_installedPlugins.clear();
                plugins.forEach(plugin -> 
                    m_installedPlugins.put(plugin.getPluginId(), plugin)
                );
                System.out.println("Loaded " + plugins.size() + " plugins from registry");
               
            });
    }

    public ConcurrentHashMap<NoteBytes, PluginMetaData> getInstalledPlugins(){
        return m_installedPlugins;
    }

 
    /**
     * Register a newly installed plugin.
     */
    public CompletableFuture<Void> registerPlugin(PluginMetaData metadata) {
        m_installedPlugins.put(metadata.getPluginId(), metadata);
        System.out.println("Registered plugin: " + metadata.getPluginId().getAsString() + 
                          " version " + metadata.getVersion());
      
        return saveRegistry();
    }


    
    /**
     * Unregister a plugin (remove from registry).
     */
    public CompletableFuture<Void> unregisterPlugin(NoteBytes pluginId) {
        PluginMetaData removed = m_installedPlugins.remove(pluginId);
        if (removed != null) {
            System.out.println("Unregistered plugin: " + pluginId.getAsString());
            return saveRegistry();
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Update plugin metadata (e.g., enable/disable, version change).
     */
    public CompletableFuture<Void> updatePlugin(NoteBytes pluginId, PluginMetaData metadata) {
        if (m_installedPlugins.containsKey(pluginId)) {
            m_installedPlugins.put(pluginId, metadata);
            System.out.println("Updated plugin: " + pluginId.getAsString());
            return saveRegistry();
        }
        return CompletableFuture.failedFuture(
            new IllegalArgumentException("Plugin not found: " + pluginId.getAsString())
        );
    }
    
    /**
     * Get all installed plugins.
     */
    public List<PluginMetaData> getAllPlugins() {
        return new ArrayList<>(m_installedPlugins.values());
    }
    
    /**
     * Get only enabled plugins.
     */
    public List<PluginMetaData> getEnabledPlugins() {
        return m_installedPlugins.values().stream()
            .filter(PluginMetaData::isEnabled)
            .collect(Collectors.toList());
    }
    
    /**
     * Get a specific plugin by ID.
     */
    public PluginMetaData getPlugin(NoteBytes pluginId) {
        return m_installedPlugins.get(pluginId);
    }
    
    /**
     * Check if a plugin is installed.
     */
    public boolean isPluginInstalled(NoteBytes pluginId) {
        return m_installedPlugins.containsKey(pluginId);
    }
    
    /**
     * Enable or disable a plugin.
     */
    public CompletableFuture<Void> setPluginEnabled(NoteBytes pluginId, boolean enabled) {
        PluginMetaData metadata = m_installedPlugins.get(pluginId);
        if (metadata != null) {
            metadata.setEnabled(enabled);
            System.out.println("Plugin " + pluginId.getAsString() + " " + 
                             (enabled ? "enabled" : "disabled"));
            return saveRegistry();
        }
        return CompletableFuture.failedFuture(
            new IllegalArgumentException("Plugin not found: " + pluginId.getAsString())
        );
    }
    
    /**
     * Save the current registry state to the NoteFile.
     */
    private CompletableFuture<Void> saveRegistry() {
        return m_appData.getNoteFile(PLUGINS_REGISTRY_PATH)
            .thenCompose(noteFile -> saveRegistry(noteFile));
   
    }

    private CompletableFuture<Void> saveRegistry(NoteFile noteFile){
        PipedOutputStream outputStream = new PipedOutputStream();
        
        CompletableFuture<Void> saveOutputStreamFuture = saveRegistry(outputStream);
        // Start write operation
        CompletableFuture<io.netnotes.engine.noteBytes.NoteBytesObject> writeFuture = 
            noteFile.writeOnly(outputStream);
     

        return CompletableFuture.allOf(saveOutputStreamFuture, writeFuture);

    }

    private CompletableFuture<Void> saveRegistry(PipedOutputStream outputStream){
        return CompletableFuture.runAsync(()->{
            try (NoteBytesWriter writer = new NoteBytesWriter(outputStream)) {
                // Write header
                writer.write(new NoteBytesPair(REGISTRY_HEADER, new NoteBytesPair[]{
                    new NoteBytesPair(NoteMessaging.Headings.VERSION_KEY, REGISTRY_VERSION)
                }));
                
                // Write each plugin
                for (PluginMetaData plugin : m_installedPlugins.values()) {
                    writer.write(plugin.getSaveData());
                }
            } catch (IOException e) {
                throw new CompletionException("Error saving registry", e);
            }
            System.out.println("Saved registry with " + m_installedPlugins.size() + " plugins");
        }, m_execService);
    }
    
    /**
     * Load the registry from the NoteFile.
     */
    private CompletableFuture<List<PluginMetaData>> loadRegistry() {
        return m_appData.getNoteFile(PLUGINS_REGISTRY_PATH)
            .thenCompose(noteFile -> {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        PipedOutputStream readOutput = new PipedOutputStream();
                        
                        // Start read operation
                        noteFile.readOnly(readOutput);
                        
                        try (NoteBytesReader reader = new NoteBytesReader(
                            new PipedInputStream(readOutput)
                        )) {
                            // Read header
                            NoteBytes header = reader.nextNoteBytes();
                            if (header == null || !header.equals(REGISTRY_HEADER)) {
                                throw new IllegalStateException("Invalid registry header");
                            }
                            
                            NoteBytesMetaData headerMetaData = reader.nextMetaData();
                            if (headerMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                                throw new IllegalStateException("Invalid registry format");
                            }
                            
                            // Read version
                            NoteBytesMap headerMap = new NoteBytesMap(
                                reader.readNextBytes(headerMetaData.getLength())
                            );
                            NoteBytes versionBytes = headerMap.get(NoteMessaging.Headings.VERSION_KEY);
                            String version = versionBytes != null ? 
                                versionBytes.getAsString() : REGISTRY_VERSION;
                            
                            // Load plugins based on version
                            if (REGISTRY_VERSION.equals(version)) {
                                return loadPluginsV1(reader);
                            } else {
                                throw new IllegalStateException("Unsupported registry version: " + version);
                            }
                        }
                        
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to load plugin registry", e);
                    } finally {
                        noteFile.close();
                    }
                }, m_execService);
            });
    }
    
    /**
     * Load plugins from registry version 1.0.0.
     */
    private List<PluginMetaData> loadPluginsV1(NoteBytesReader reader) throws IOException {
        List<PluginMetaData> plugins = new ArrayList<>();
        
        NoteBytes pluginId = reader.nextNoteBytes();
        while (pluginId != null && pluginId.byteLength() > 0) {
            NoteBytes value = reader.nextNoteBytes();
            
            if (value != null && value.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                NoteBytesMap map = new NoteBytesMap(value.get());
                
                NoteBytes enabledBytes = map.get(PluginMetaData.ENABLED_KEY);
                boolean enabled = enabledBytes != null && enabledBytes.getAsBoolean();
                
                NoteBytes pathBytes = map.get(PluginMetaData.NOTE_PATH_KEY);
                if (pathBytes != null && pathBytes.getType() == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
                    NoteBytes versionBytes = map.get(PluginMetaData.VERSION_KEY);
                    String version = versionBytes != null ? versionBytes.getAsString() : "unknown";
                    
                    PluginMetaData metadata = new PluginMetaData(
                        pluginId,
                        version,
                        enabled,
                        new NoteStringArrayReadOnly(pathBytes)
                    );
                    
                    plugins.add(metadata);
                }
            }
            
            pluginId = reader.nextNoteBytes();
        }
        
        return plugins;
    }

    
}