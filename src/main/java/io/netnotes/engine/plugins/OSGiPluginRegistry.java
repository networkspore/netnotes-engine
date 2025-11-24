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

import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.utils.VirtualExecutors;
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
    
    private NoteFile m_noteFile = null;
    private final ExecutorService m_execService;
    

    // In-memory cache of installed plugins
    private final ConcurrentHashMap<String, OSGiPluginMetaData> m_installedPlugins;
    
    public OSGiPluginRegistry() {
      
        m_execService = VirtualExecutors.getVirtualExecutor();
        m_installedPlugins = new ConcurrentHashMap<>();
    
    }



    /**
     * Initialize the registry - load existing plugins or create new registry.
     */
    public CompletableFuture<Void> initialize(NoteFile noteFile) {
        if(m_noteFile == null){
            m_noteFile = noteFile;

            return loadRegistry(m_noteFile)
                .exceptionally(error -> {
                    // If registry doesn't exist or fails to load, create empty one
                    System.out.println("No existing registry found or error loading, creating new: " + error.getMessage());
                    return new ArrayList<OSGiPluginMetaData>();
                })
                .thenAccept(plugins -> {
                    m_installedPlugins.clear();
                    plugins.forEach(plugin -> 
                        m_installedPlugins.put(plugin.getPluginId(), plugin)
                    );
                    System.out.println("Loaded " + plugins.size() + " plugins from registry");
                
                });
        }else{
            return CompletableFuture.completedFuture(null);
        }
    }

    public ConcurrentHashMap<String, OSGiPluginMetaData> getInstalledPlugins(){
        return m_installedPlugins;
    }

 
    /**
     * Register a newly installed plugin.
     */
    public CompletableFuture<Void> registerPlugin(OSGiPluginMetaData metadata) {
        m_installedPlugins.put(metadata.getPluginId(), metadata);
        System.out.println("Registered plugin: " + metadata.getName() + " " + metadata.getRelease().getTagName());
      
        return saveRegistry();
    }


    
    /**
     * Unregister a plugin (remove from registry).
     */
    public CompletableFuture<Void> unregisterPlugin(String pluginId) {
        OSGiPluginMetaData removed = m_installedPlugins.remove(pluginId);
        if (removed != null) {
            
            System.out.println("Unregistered plugin: " + removed);
            return removed.shutdown().thenAccept(v-> saveRegistry());
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Update plugin metadata (e.g., enable/disable, version change).
     */
    public CompletableFuture<Void> updatePlugin(String pluginId, OSGiPluginMetaData metadata) {
        if (m_installedPlugins.containsKey(pluginId)) {
            m_installedPlugins.put(pluginId, metadata);
            System.out.println("Updated plugin: " + metadata);
            return saveRegistry();
        }
        return CompletableFuture.failedFuture(
            new IllegalArgumentException("Plugin not found: " + metadata)
        );
    }
    
    /**
     * Get all installed plugins.
     */
    public List<OSGiPluginMetaData> getAllPlugins() {
        return new ArrayList<>(m_installedPlugins.values());
    }
    
    /**
     * Get only enabled plugins.
     */
    public List<OSGiPluginMetaData> getEnabledPlugins() {
        return m_installedPlugins.values().stream()
            .filter(OSGiPluginMetaData::isEnabled)
            .collect(Collectors.toList());
    }
    
    /**
     * Get a specific plugin by ID.
     */
    public OSGiPluginMetaData getPlugin(String pluginId) {
        return m_installedPlugins.get(pluginId);
    }
    
    /**
     * Check if a plugin is installed.
     */
    public boolean isPluginInstalled(String pluginId) {
        return m_installedPlugins.containsKey(pluginId);
    }
    
    /**
     * Enable or disable a plugin.
     */
    public CompletableFuture<Void> setPluginEnabled(String pluginId, boolean enabled) {
        OSGiPluginMetaData metadata = m_installedPlugins.get(pluginId);
        if (metadata != null) {
            metadata.setEnabled(enabled);
            System.out.println("Plugin " + metadata + " " + 
                             (enabled ? "enabled" : "disabled"));
            return saveRegistry();
        }
        return CompletableFuture.failedFuture(
            new IllegalArgumentException("Plugin not found: " + pluginId));
    }
    
    /**
     * Save the current registry state to the NoteFile.
     */
    private CompletableFuture<Void> saveRegistry() {
        if(m_noteFile == null){
            return CompletableFuture.failedFuture(new IllegalStateException("Registry is not initialized"));
        }
        return saveRegistry(m_noteFile);
    }

    private CompletableFuture<Void> saveRegistry(NoteFile noteFile){
        PipedOutputStream outputStream = new PipedOutputStream();
        
        CompletableFuture<Void> saveOutputStreamFuture = saveRegistry(outputStream);
        // Start write operation
        CompletableFuture<NoteBytesObject> writeFuture = 
            noteFile.writeOnly(outputStream);
     

        return CompletableFuture.allOf(saveOutputStreamFuture, writeFuture);

    }

    private CompletableFuture<Void> saveRegistry(PipedOutputStream outputStream){
        return CompletableFuture.runAsync(()->{
            try (NoteBytesWriter writer = new NoteBytesWriter(outputStream)) {
                // Write header
                writer.write(new NoteBytesObject(new NoteBytesPair(REGISTRY_HEADER, new NoteBytesObject( new NoteBytesPair[]{
                    new NoteBytesPair(NoteMessaging.Keys.VERSION, REGISTRY_VERSION)
                }))));
                
                // Write each plugin
                for (OSGiPluginMetaData plugin : m_installedPlugins.values()) {
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
    private CompletableFuture<List<OSGiPluginMetaData>> loadRegistry(NoteFile noteFile) {
    
        PipedOutputStream readOutput = new PipedOutputStream();
        CompletableFuture<NoteBytesObject> readFuture = noteFile.readOnly(readOutput);

        CompletableFuture<List<OSGiPluginMetaData>> loadFuture = 
            loadRegistry(readOutput);


        return CompletableFuture.allOf(readFuture, loadFuture).thenCompose(v->loadFuture);
       
    }

    private CompletableFuture<List<OSGiPluginMetaData>> loadRegistry(PipedOutputStream outputStream){
        return CompletableFuture.supplyAsync(() -> {
                                
            try ( 
                PipedInputStream inputStream = new PipedInputStream(outputStream);
                NoteBytesReader reader = new NoteBytesReader(inputStream);
            ) {
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
                NoteBytes versionBytes = headerMap.get(NoteMessaging.Keys.VERSION);
                String version = versionBytes != null ? 
                    versionBytes.getAsString() : REGISTRY_VERSION;
                
                // Load plugins based on version
                if (REGISTRY_VERSION.equals(version)) {
                    return loadPluginsV1(reader);
                } else {
                    throw new IllegalStateException("Unsupported registry version: " + version);
                }
            }catch(Exception e){
                throw new CompletionException("Failed to read registry", e);
            }
            
        }, m_execService);
    }
    
    /**
     * Load plugins from registry version 1.0.0.
     */
    private List<OSGiPluginMetaData> loadPluginsV1(NoteBytesReader reader) throws IOException {
        List<OSGiPluginMetaData> plugins = new ArrayList<>();
        
        NoteBytes pluginId = reader.nextNoteBytes();
        while (pluginId != null) {
            NoteBytes value = reader.nextNoteBytes();
            
            if (value != null && value.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                plugins.add(OSGiPluginMetaData.of(value.getAsNoteBytesMap()));
            }
            
            pluginId = reader.nextNoteBytes();
        }
        
        return plugins;
    }


    public void shutdown(){
        if(m_noteFile != null){
           
    
            m_noteFile.close();
        }
    }
    
}