package io.netnotes.engine.core.system.control.nodes;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.noteFiles.NoteFile;

/**
 * NodeRegistry - Persistence layer for installed nodes
 * 
 * Similar to OSGiPluginRegistry but for the system node manager.
 * Stores metadata about installed nodes in an encrypted NoteFile.
 * 
 * Registry Format:
 * [HEADER] "nodes-registry" -> {version: "1.0.0"}
 * [NODE_ID] -> {
 *   enabled: boolean,
 *   data: {
 *     nodeId: string,
 *     name: string,
 *     version: string,
 *     category: string,
 *     ... (NodeMetadata)
 *   }
 * }
 */
public class NodeRegistry {
    
    public static final String NODES = "nodes";
    public static final String REGISTRY_NAME = "nodes-registry";
    public static final String REGISTRY_VERSION = "1.0.0";
    
    public static final NoteStringArrayReadOnly NODES_REGISTRY_PATH = 
        new NoteStringArrayReadOnly(NODES, REGISTRY_NAME);
    
    private static final NoteBytes REGISTRY_HEADER = new NoteBytes(REGISTRY_NAME);
    private static final NoteBytes ENABLED_KEY = new NoteBytes("enabled");
    private static final NoteBytes DATA_KEY = new NoteBytes("data");
    
    private final AppData appData;
    private NoteFile registryFile;
    
    // In-memory cache
    private final ConcurrentHashMap<String, NodeMetadata> installedNodes;
    
    public NodeRegistry(AppData appData) {
        this.appData = appData;
        this.installedNodes = new ConcurrentHashMap<>();
    }
    
    /**
     * Initialize registry - load from NoteFile or create new
     */
    public CompletableFuture<Void> initialize() {
        return appData.getAppDataInterface(new NoteBytesReadOnly(NODES))
            .getNoteFile(NODES_REGISTRY_PATH)
            .thenCompose(noteFile -> {
                this.registryFile = noteFile;
                
                if (noteFile.isFile()) {
                    return loadRegistry();
                } else {
                    // Create empty registry
                    return saveRegistry();
                }
            });
    }
    
    /**
     * Register a newly installed node
     */
    public CompletableFuture<Void> registerNode(NodeMetadata metadata) {
        installedNodes.put(metadata.getNodeId(), metadata);
        System.out.println("[NodeRegistry] Registered: " + metadata.getName() + 
            " version " + metadata.getVersion());
        return saveRegistry();
    }
    
    /**
     * Unregister a node (remove from registry)
     */
    public CompletableFuture<Void> unregisterNode(String nodeId) {
        NodeMetadata removed = installedNodes.remove(nodeId);
        if (removed != null) {
            System.out.println("[NodeRegistry] Unregistered: " + removed.getName());
            return saveRegistry();
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Update node metadata
     */
    public CompletableFuture<Void> updateNode(String nodeId, NodeMetadata metadata) {
        if (installedNodes.containsKey(nodeId)) {
            installedNodes.put(nodeId, metadata);
            System.out.println("[NodeRegistry] Updated: " + metadata.getName());
            return saveRegistry();
        }
        return CompletableFuture.failedFuture(
            new IllegalArgumentException("Node not found: " + nodeId));
    }
    
    /**
     * Enable or disable a node
     */
    public CompletableFuture<Void> setNodeEnabled(String nodeId, boolean enabled) {
        NodeMetadata metadata = installedNodes.get(nodeId);
        if (metadata != null) {
            metadata.setEnabled(enabled);
            System.out.println("[NodeRegistry] Node " + metadata.getName() + 
                " " + (enabled ? "enabled" : "disabled"));
            return saveRegistry();
        }
        return CompletableFuture.failedFuture(
            new IllegalArgumentException("Node not found: " + nodeId));
    }
    
    /**
     * Get all installed nodes
     */
    public List<NodeMetadata> getAllNodes() {
        return new ArrayList<>(installedNodes.values());
    }
    
    /**
     * Get only enabled nodes
     */
    public List<NodeMetadata> getEnabledNodes() {
        return installedNodes.values().stream()
            .filter(NodeMetadata::isEnabled)
            .collect(Collectors.toList());
    }
    
    /**
     * Get a specific node by ID
     */
    public NodeMetadata getNode(String nodeId) {
        return installedNodes.get(nodeId);
    }
    
    /**
     * Check if a node is installed
     */
    public boolean isNodeInstalled(String nodeId) {
        return installedNodes.containsKey(nodeId);
    }
    
    // ===== PERSISTENCE =====
    
    /**
     * Save registry to NoteFile
     */
    private CompletableFuture<Void> saveRegistry() {
        if (registryFile == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Registry not initialized"));
        }
        
        PipedOutputStream outputStream = new PipedOutputStream();
        
        CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(() -> {
            try (NoteBytesWriter writer = new NoteBytesWriter(outputStream)) {
                // Write header
                writer.write(new NoteBytesObject(new NoteBytesPair(
                    REGISTRY_HEADER,
                    new NoteBytesObject(new NoteBytesPair(
                        NoteMessaging.Keys.VERSION, REGISTRY_VERSION
                    ))
                )));
                
                // Write each node
                for (NodeMetadata node : installedNodes.values()) {
                    writer.write(new NoteBytesPair(
                        node.getNodeId(),
                        new NoteBytesObject(
                            new NoteBytesPair(ENABLED_KEY, NoteBytes.of(node.isEnabled())),
                            new NoteBytesPair(DATA_KEY, node.getNoteBytesObject())
                        )
                    ));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to write registry", e);
            }
        }, appData.getExecService());
        
        CompletableFuture<NoteBytesObject> saveFuture = 
            registryFile.writeOnly(outputStream);
        
        return CompletableFuture.allOf(writeFuture, saveFuture)
            .thenRun(() -> {
                System.out.println("[NodeRegistry] Saved " + installedNodes.size() + " nodes");
            });
    }
    
    /**
     * Load registry from NoteFile
     */
    private CompletableFuture<Void> loadRegistry() {
        PipedOutputStream readOutput = new PipedOutputStream();
        
        CompletableFuture<NoteBytesObject> readFuture = 
            registryFile.readOnly(readOutput);
        
        CompletableFuture<List<NodeMetadata>> parseFuture = 
            CompletableFuture.supplyAsync(() -> {
                try (
                    PipedInputStream inputStream = new PipedInputStream(readOutput);
                    NoteBytesReader reader = new NoteBytesReader(inputStream)
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
                    
                    // Skip version check for now
                    reader.skipData(headerMetaData.getLength());
                    
                    // Read nodes
                    List<NodeMetadata> nodes = new ArrayList<>();
                    NoteBytes nodeId = reader.nextNoteBytes();
                    
                    while (nodeId != null) {
                        NoteBytes value = reader.nextNoteBytes();
                        
                        if (value != null && 
                            value.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                            
                            NoteBytesMap map = value.getAsNoteBytesMap();
                            boolean enabled = map.get(ENABLED_KEY).getAsBoolean();
                            NoteBytesMap data = map.get(DATA_KEY).getAsNoteBytesMap();
                            
                            NodeMetadata metadata = NodeMetadata.of(data);
                            metadata.setEnabled(enabled);
                            nodes.add(metadata);
                        }
                        
                        nodeId = reader.nextNoteBytes();
                    }
                    
                    return nodes;
                    
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read registry", e);
                }
            }, appData.getExecService());
        
        return CompletableFuture.allOf(readFuture, parseFuture)
            .thenAccept(v -> {
                List<NodeMetadata> nodes = parseFuture.join();
                installedNodes.clear();
                nodes.forEach(node -> installedNodes.put(node.getNodeId(), node));
                System.out.println("[NodeRegistry] Loaded " + nodes.size() + " nodes");
            });
    }
    
    /**
     * Shutdown registry
     */
    public void shutdown() {
        if (registryFile != null) {
            registryFile.close();
        }
    }
}