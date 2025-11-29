package io.netnotes.engine.core.system.control.nodes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.core.AppDataInterface;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.FlowProcessRegistry;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.VirtualExecutors;

/**
 * NodeController - Central node lifecycle manager
 * 
 * ARCHITECTURE:
 * - Runs as a FlowProcess child of SystemSessionProcess
 * - Provides sandboxed access to system resources
 * - Manages node loading/unloading (coordinates with OSGi)
 * - Tracks node state (starting, running, stopping)
 * - Enforces resource quotas and permissions
 * - Routes inter-node communication
 * 
 * SANDBOXING:
 * - Each node gets a scoped AppDataInterface (limited file access)
 * - Nodes communicate via FlowProcess messages (controlled routing)
 * - Direct streams for bulk data transfer (with permission checks)
 * - Resource quotas enforced at controller level
 * 
 * NODE LIFECYCLE:
 * 1. Load Request → Load from package → Initialize → Running
 * 2. Running → Shutdown Request → Cleanup → Unloaded
 * 3. Crash Detection → Auto-restart (if configured)
 * 
 * SEPARATION OF CONCERNS:
 * - NodeManagerProcess: Package installation/removal (apt-get)
 * - NodeController: Runtime lifecycle (systemd)
 * - AppData: System-level registry and resources
 * - INode: Individual node implementation
 */
public class NodeController extends FlowProcess {
    
    private final BitFlagStateMachine state;
    private final AppData appData;
    private final FlowProcessRegistry processRegistry;
    
    // Node registry (runtime instances)
    private final Map<String, NodeInstance> nodes = new ConcurrentHashMap<>();
    
    // Node loader (OSGi integration)
    private final NodeLoader nodeLoader;
    

    
    // Inter-node routing
    private final NodeRouter nodeRouter;
    
    // States
    private static final long INITIALIZING = 1L << 0;
    private static final long READY = 1L << 1;
    private static final long LOADING_NODE = 1L << 2;
    private static final long UNLOADING_NODE = 1L << 3;
    
    public NodeController(AppData appData, FlowProcessRegistry processRegistry) {
        super(ProcessType.BIDIRECTIONAL);
        this.appData = appData;
        this.processRegistry = processRegistry;
        this.state = new BitFlagStateMachine("node-controller");
        
        this.nodeLoader = new NodeLoader(appData);
        this.nodeRouter = new NodeRouter(this);
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(INITIALIZING);
        
        return initialize()
            .thenRun(() -> {
                state.removeState(INITIALIZING);
                state.addState(READY);
                
                System.out.println("[NodeController] Ready - Managing " + 
                    nodes.size() + " nodes");
            })
            .thenCompose(v -> getCompletionFuture());
    }
    
    private CompletableFuture<Void> initialize() {
        // Load auto-start nodes from configuration
        return loadAutoStartNodes()
            .thenRun(() -> {
                System.out.println("[NodeController] Initialized with " + 
                    nodes.size() + " auto-start nodes");
            });
    }
    
    private CompletableFuture<Void> loadAutoStartNodes() {
        // TODO: Query InstallationRegistry for packages with autoload=true
        // For now, just complete
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== NODE LOADING =====
    
    /**
     * Load a node from an installed package
     * 
     * Process:
     * 1. Read package manifest
     * 2. Load JAR/bundle via NodeLoader (OSGi)
     * 3. Create sandboxed AppDataInterface
     * 4. Initialize node with sandbox
     * 5. Register in FlowProcess network
     * 6. Start node background tasks
     * 
     * @param packageId Installed package identifier
     * @param installPath Path to package files
     * @return NodeInstance wrapper
     */
    public CompletableFuture<NodeInstance> loadNode(
            String packageId,
            InstalledPackage installedPackage) {
        
        if (nodes.containsKey(packageId)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Node already loaded: " + packageId));
        }
        
        state.addState(LOADING_NODE);
        
        System.out.println("[NodeController] Loading node: " + packageId);
        
        return nodeLoader.loadNodeFromPackage(installedPackage)
            .thenCompose(inode -> {
                // Create node instance wrapper
                NodeInstance instance = new NodeInstance(
                    packageId,
                    installedPackage,
                    inode,
                    NodeState.LOADING
                );
                ContextPath packagePath = ContextPath.of("nodes", packageId);
                nodes.put(packageId, instance);
                
                // Create sandboxed interface
                AppDataInterface sandboxedInterface = appData.getAppDataInterface(packagePath);
                
                instance.setDataInterface(sandboxedInterface);
                
                // Register in FlowProcess network
                return registerNodeInNetwork(instance)
                    .thenCompose(v -> {
                        // Initialize node with sandbox
                        instance.setState(NodeState.INITIALIZING);
                        return inode.initialize(sandboxedInterface);
                    })
                    .thenCompose(v -> {
                        instance.setState(NodeState.RUNNING);
                        
                        // Start background tasks if node has them
                        if (inode.hasBackgroundTasks()) {
                            return inode.runBackgroundTasks()
                                .thenApply(voidResult -> instance);
                        } else {
                            return CompletableFuture.completedFuture(instance);
                        }
                    });
            })
            .whenComplete((instance, ex) -> {
                state.removeState(LOADING_NODE);
                
                if (ex != null) {
                    System.err.println("[NodeController] Failed to load node: " + 
                        packageId + " - " + ex.getMessage());
                    nodes.remove(packageId);
                } else {
                    System.out.println("[NodeController] Node loaded successfully: " + 
                        packageId);
                    
                    // Register in AppData's registry
                    appData.nodeRegistry().put(
                        new NoteBytesReadOnly(packageId), 
                        instance.getNode()
                    );
                }
            });
    }
    

    
    /**
     * Register node in FlowProcess network
     * Sets up routing and subscriptions
     */
    private CompletableFuture<Void> registerNodeInNetwork(NodeInstance instance) {
        INode node = instance.getNode();
        
        // Build context path: /system/controller/nodes/{packageId}
        ContextPath nodePath = contextPath.append("nodes", instance.getPackageId());
        
        // Create FlowProcess adapter for the node
        NodeFlowAdapter adapter = new NodeFlowAdapter(node, nodePath, this);
        
        instance.setFlowAdapter(adapter);
        
        // Register in ProcessRegistry
        return CompletableFuture.runAsync(() -> {
            processRegistry.registerProcess(adapter, nodePath);
            System.out.println("[NodeController] Node registered at: " + nodePath);
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    // ===== NODE UNLOADING =====
    
    /**
     * Unload a running node
     * 
     * Process:
     * 1. Notify subscribers of shutdown
     * 2. Stop background tasks
     * 3. Close all streams
     * 4. Call node.shutdown()
     * 5. Unregister from network
     * 6. Remove from registry
     */
    public CompletableFuture<Void> unloadNode(String packageId) {
        NodeInstance instance = nodes.get(packageId);
        
        if (instance == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Node not loaded: " + packageId));
        }
        
        state.addState(UNLOADING_NODE);
        
        System.out.println("[NodeController] Unloading node: " + packageId);
        
        instance.setState(NodeState.STOPPING);
        
        INode node = instance.getNode();
        
        // Shutdown sequence
        return CompletableFuture.runAsync(() -> {
            // Notify subscribers
            if (node.hasFlowSubscribers()) {
                NoteBytesMap shutdownEvent = new NoteBytesMap();
                shutdownEvent.put(Keys.CMD, new NoteBytes("node_shutdown"));
                shutdownEvent.put(new NoteBytes("node_id"), 
                    new NoteBytes(packageId));
                
                // Emit shutdown event
                RoutedPacket packet = new RoutedPacket(
                    node.getContextPath(),
                    node.getContextPath(),
                    shutdownEvent.readOnlyObject()
                );
                node.emitFlowEvent(packet);
            }
        }, VirtualExecutors.getVirtualExecutor())
        .thenCompose(v -> {
            // Shutdown node
            return node.shutdown();
        })
        .thenRun(() -> {
            // Unregister from network
            NodeFlowAdapter adapter = instance.getFlowAdapter();
            if (adapter != null) {
                processRegistry.unregisterProcess(adapter.getContextPath());
            }
            
            // Remove from registries
            nodes.remove(packageId);
            appData.nodeRegistry().remove(new NoteBytesReadOnly(packageId));
            
            instance.setState(NodeState.STOPPED);
            
            System.out.println("[NodeController] Node unloaded: " + packageId);
        })
        .whenComplete((result, ex) -> {
            state.removeState(UNLOADING_NODE);
            
            if (ex != null) {
                System.err.println("[NodeController] Error unloading node: " + 
                    packageId + " - " + ex.getMessage());
            }
        });
    }
    
    // ===== NODE QUERIES =====
    
    /**
     * Get node instance by package ID
     */
    public NodeInstance getNode(String packageId) {
        return nodes.get(packageId);
    }
    
    /**
     * Get all loaded nodes
     */
    public List<NodeInstance> getAllNodes() {
        return new ArrayList<>(nodes.values());
    }
    
    /**
     * Get nodes by state
     */
    public List<NodeInstance> getNodesByState(NodeState state) {
        return nodes.values().stream()
            .filter(n -> n.getState() == state)
            .toList();
    }
    
    /**
     * Check if node is loaded
     */
    public boolean isNodeLoaded(String packageId) {
        return nodes.containsKey(packageId);
    }
    
    // ===== INTER-NODE ROUTING =====
    
    /**
     * Route message from one node to another
     * Enforces permissions and quotas
     */
    public CompletableFuture<Void> routeNodeMessage(
            String fromNodeId,
            String toNodeId,
            RoutedPacket packet) {
        
        return nodeRouter.routeMessage(fromNodeId, toNodeId, packet);
    }
    
    /**
     * Request stream channel between nodes
     */
    public CompletableFuture<StreamChannel> requestNodeStreamChannel(
            String fromNodeId,
            String toNodeId) {
        
        return nodeRouter.requestStreamChannel(fromNodeId, toNodeId);
    }


    // ===== MESSAGE HANDLING =====
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        try {
            NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
            NoteBytesReadOnly cmd = msg.getReadOnly(Keys.CMD);
            
            if (cmd == null) {
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("'cmd' required"));
            }
            
            String cmdStr = cmd.getAsString();
            
            switch (cmdStr) {
                case "load_node":
                    return handleLoadNodeRequest(msg);
                    
                case "unload_node":
                    return handleUnloadNodeRequest(msg);
                    
                case "list_nodes":
                    return handleListNodesRequest(packet);
                    
                case "node_status":
                    return handleNodeStatusRequest(msg, packet);
                    
                default:
                    return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Unknown command: " + cmdStr));
            }
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private CompletableFuture<Void> handleLoadNodeRequest(NoteBytesMap msg) {
        String packageId = msg.get(new NoteBytes("package_id")).getAsString();
        
        // TODO: Get InstalledPackage from InstallationRegistry
        // For now, just fail
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("Load node not fully implemented"));
    }
    
    private CompletableFuture<Void> handleUnloadNodeRequest(NoteBytesMap msg) {
        String packageId = msg.get(new NoteBytes("node_id")).getAsString();
        return unloadNode(packageId);
    }
    
    private CompletableFuture<Void> handleListNodesRequest(RoutedPacket original) {
        List<NodeInstance> allNodes = getAllNodes();
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.CMD, new NoteBytes("node_list"));
        response.put(Keys.ITEM_COUNT, new NoteBytes(allNodes.size()));
        
        // Build node info list
        // TODO: Serialize node info
        
        reply(original, response.getNoteBytesObject());
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleNodeStatusRequest(
            NoteBytesMap msg, 
            RoutedPacket original) {
        
        String packageId = msg.get(new NoteBytes("node_id")).getAsString();
        NodeInstance instance = nodes.get(packageId);
        
        if (instance == null) {
            NoteBytesMap error = new NoteBytesMap();
            error.put(Keys.CMD, ProtocolMesssages.ERROR);
            error.put(ProtocolMesssages.ERROR, 
                new NoteBytes("Node not found: " + packageId));
            
            reply(original, error.getNoteBytesObject());
            return CompletableFuture.completedFuture(null);
        }
    
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.CMD, new NoteBytes("node_status"));
        response.put(new NoteBytes("node_id"), new NoteBytes(packageId));
        response.put(Keys.STATE, 
            new NoteBytes(instance.getState().toString()));
        response.put(new NoteBytes("active"), 
            new NoteBytes(instance.getNode().isActive()));
        
        reply(original, response.getNoteBytesObject());
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        // Stream channels for nodes are handled by NodeFlowAdapter
        System.err.println("[NodeController] Unexpected stream channel from: " + 
            fromPath);
    }
    
    // ===== SHUTDOWN =====
    
    public CompletableFuture<Void> shutdown() {
        System.out.println("[NodeController] Shutting down - unloading " + 
            nodes.size() + " nodes");
        
        List<CompletableFuture<Void>> shutdownFutures = new ArrayList<>();
        
        for (String packageId : new ArrayList<>(nodes.keySet())) {
            shutdownFutures.add(unloadNode(packageId));
        }
        
        return CompletableFuture.allOf(
            shutdownFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                System.out.println("[NodeController] Shutdown complete");
            });
    }
    
    // ===== GETTERS =====
    
    public BitFlagStateMachine getState() {
        return state;
    }
    
    public FlowProcessRegistry getProcessRegistry() {
        return processRegistry;
    }
    
    public NodeRouter getNodeRouter() {
        return nodeRouter;
    }
}