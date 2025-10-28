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
    private final ConcurrentHashMap<String, OSGiPluginMetaData> m_installedPlugins;
    
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
                return new ArrayList<OSGiPluginMetaData>();
            })
            .thenAccept(plugins -> {
                m_installedPlugins.clear();
                plugins.forEach(plugin -> 
                    m_installedPlugins.put(plugin.getPluginId(), plugin)
                );
                System.out.println("Loaded " + plugins.size() + " plugins from registry");
               
            });
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
            return saveRegistry();
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
    private CompletableFuture<List<OSGiPluginMetaData>> loadRegistry() {
        return m_appData.getNoteFile(PLUGINS_REGISTRY_PATH)
            .thenCompose(noteFile -> {
                PipedOutputStream readOutput = new PipedOutputStream();
                noteFile.readOnly(readOutput); // async read start

                // supplyAsync returns a CompletableFuture<List<CompletableFuture<OSGiPluginMetaData>>>
                return CompletableFuture.supplyAsync(() -> {
                    List<CompletableFuture<OSGiPluginMetaData>> futures = new ArrayList<>();

                    try (NoteBytesReader reader = new NoteBytesReader(new PipedInputStream(readOutput))) {
                        // --- header parsing ---
                        NoteBytes header = reader.nextNoteBytes();
                        if (header == null || !header.equals(REGISTRY_HEADER)) {
                            throw new IllegalStateException("Invalid registry header");
                        }

                        NoteBytesMetaData headerMetaData = reader.nextMetaData();
                        if (headerMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                            throw new IllegalStateException("Invalid registry format");
                        }

                        NoteBytesMap headerMap = new NoteBytesMap(
                            reader.readNextBytes(headerMetaData.getLength())
                        );
                        NoteBytes versionBytes = headerMap.get(NoteMessaging.Headings.VERSION_KEY);
                        String version = (versionBytes != null)
                            ? versionBytes.getAsString()
                            : REGISTRY_VERSION;

                        if (REGISTRY_VERSION.equals(version)) {
                            loadPluginsV1(futures, reader);
                        } else {
                            throw new IllegalStateException("Unsupported registry version: " + version);
                        }
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }

                    return futures;
                }, m_execService);
            }).thenCompose(futures -> CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v ->
                    futures.stream()
                        .map(CompletableFuture::join)
                        .toList()
                )
            );
    }

    
    public void loadPluginsV1(List<CompletableFuture<OSGiPluginMetaData>> futures, NoteBytesReader reader) throws IOException {
        
        NoteBytes pluginId = reader.nextNoteBytes();
        while (pluginId != null) {
            NoteBytes value = reader.nextNoteBytes();
            
            if (value != null && value.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                futures.add(OSGiPluginMetaData.of(value.getAsNoteBytesMap(), m_appData));
            }
            
            pluginId = reader.nextNoteBytes();
        }

    }
    
}