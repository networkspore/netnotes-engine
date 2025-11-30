package io.netnotes.engine.core.system.control.nodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.core.AppDataInterface;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.FlowProcessRegistry;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.VirtualExecutors;

/**
 * NodeController - Central node lifecycle manager
 * 
 * REFACTORED:
 * - Uses packageId (not instanceId) - one instance per package
 * - Receives path from parent at construction
 * - Builds node paths locally (no PathArchitecture)
 * - Passes explicit paths to node interfaces
 */
public class NodeController extends FlowProcess {
    public static final class CMDS {
        public static final NoteBytesReadOnly LOAD_NODE = new NoteBytesReadOnly("load_node");
        public static final NoteBytesReadOnly UNLOAD_NODE = new NoteBytesReadOnly("unload_node");
        public static final NoteBytesReadOnly LIST_NODES = ProtocolMesssages.ITEM_LIST;
        public static final NoteBytesReadOnly NODE_STATUS = ProtocolMesssages.STATUS;
    }

    private final BitFlagStateMachine state;
    private final AppData appData;  // Still need for creating interfaces
    private final FlowProcessRegistry processRegistry;
    private final InstallationRegistry installationRegistry;

    // Node registry: packageId → NodeInstance (ONE instance per package)
    private final Map<NoteBytesReadOnly, NodeInstance> nodes = new ConcurrentHashMap<>();
    
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_routedMsgExecutorMap = new HashMap<>();

    // Node loader (OSGi integration)
    private final NodeLoader nodeLoader;
    
    // Inter-node routing
    private final NodeRouter nodeRouter;
    
    // States
    private static final long INITIALIZING = 1L << 0;
    private static final long READY = 1L << 1;
    private static final long LOADING_NODE = 1L << 2;
    private static final long UNLOADING_NODE = 1L << 3;
    
    /**
     * Constructor
     * 
     * OLD: NodeController(AppData, FlowProcessRegistry, InstallationRegistry)
     * NEW: NodeController(ContextPath, AppData, FlowProcessRegistry, InstallationRegistry)
     * 
     * @param myPath Where controller lives (given by parent)
     * @param appData For creating node interfaces
     * @param processRegistry For registering nodes in network
     * @param installationRegistry For package metadata
     */
    public NodeController(
            ContextPath myPath,
            AppData appData,
            FlowProcessRegistry processRegistry,
            InstallationRegistry installationRegistry) {

        super(ProcessType.BIDIRECTIONAL);
        this.contextPath = myPath;  // Set FlowProcess path
        this.appData = appData;
        this.processRegistry = processRegistry;
        this.installationRegistry = installationRegistry;
        this.state = new BitFlagStateMachine("node-controller");
        
        setupMsgExecutorMap();
        
        // NodeLoader needs interface to read package files
        ContextPath packagesPath = ContextPath.of("system", "nodes", "packages");
        AppDataInterface loaderInterface = appData.createScopedInterface(packagesPath);
        this.nodeLoader = new NodeLoader(loaderInterface);
        
        this.nodeRouter = new NodeRouter(this);
    }
    
    private void setupMsgExecutorMap() {
        m_routedMsgExecutorMap.put(CMDS.LOAD_NODE, (map, packet) -> handleLoadNodeRequest(map));
        m_routedMsgExecutorMap.put(CMDS.UNLOAD_NODE, (map, packet) -> handleUnloadNodeRequest(map));
        m_routedMsgExecutorMap.put(CMDS.LIST_NODES, (map, packet) -> handleListNodesRequest(packet));
        m_routedMsgExecutorMap.put(CMDS.NODE_STATUS, (map, packet) -> handleNodeStatusRequest(map, packet));
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
        System.out.println("[NodeController] Initializing at: " + contextPath);
        
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
     * ONE instance per package - node manages its own internal instances
     * 
     * @param packageId Package identifier (NoteBytesReadOnly)
     * @param installedPackage Package metadata (optional, will lookup if null)
     * @return NodeInstance wrapper
     */
    public CompletableFuture<NodeInstance> loadNode(
            NoteBytesReadOnly packageId,
            InstalledPackage installedPackage) {
        
        if (nodes.containsKey(packageId)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Node already loaded: " + packageId.getAsString()));
        }
        
        state.addState(LOADING_NODE);
        
        String pkgIdStr = packageId.getAsString();
        System.out.println("[NodeController] Loading node: " + pkgIdStr);
        
        // Get package metadata
        InstalledPackage pkg = installedPackage != null ? 
            installedPackage : 
            installationRegistry.getPackage(packageId);
        
        if (pkg == null) {
            state.removeState(LOADING_NODE);
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Package not installed: " + pkgIdStr));
        }
        
        // === PATHS: Parent decides where node lives ===
        
        // FlowProcess path (for routing in network)
        ContextPath nodeFlowPath = contextPath.append("nodes", pkgIdStr);
        
        // Storage paths (following conventions)
        ContextPath runtimePath = ContextPath.of("system", "nodes", "runtime", pkgIdStr);
        ContextPath userPath = ContextPath.of("user", "nodes", pkgIdStr);
        
        System.out.println("[NodeController] Node paths:");
        System.out.println("  Flow:    " + nodeFlowPath);
        System.out.println("  Runtime: " + runtimePath);
        System.out.println("  User:    " + userPath);
        
        return nodeLoader.loadNodeFromPackage(pkg)
            .thenCompose(inode -> {
                // Create node instance wrapper
                NodeInstance instance = new NodeInstance(
                    packageId,
                    pkg,
                    inode,
                    NodeState.LOADING
                );
                
                nodes.put(packageId, instance);
                
                // === CREATE SCOPED INTERFACE ===
                // Node gets access to runtime + user paths
                AppDataInterface nodeInterface = appData.createScopedInterface(
                    runtimePath,  // Primary: system runtime data
                    userPath      // Alternative: user data
                );
                
                instance.setDataInterface(nodeInterface);
                
                // Register in FlowProcess network
                return registerNodeInNetwork(instance, nodeFlowPath)
                    .thenCompose(v -> {
                        // Initialize node with scoped interface
                        instance.setState(NodeState.INITIALIZING);
                        return inode.initialize(nodeInterface);
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
                        pkgIdStr + " - " + ex.getMessage());
                    nodes.remove(packageId);
                } else {
                    System.out.println("[NodeController] Node loaded successfully: " + 
                        pkgIdStr);
                    
                    // Register in AppData's registry
                    appData.nodeRegistry().put(packageId, instance.getNode());
                }
            });
    }
    
    /**
     * Register node in FlowProcess network
     */
    private CompletableFuture<Void> registerNodeInNetwork(
            NodeInstance instance,
            ContextPath nodeFlowPath) {
        
        INode node = instance.getNode();
        
        // Create FlowProcess adapter for the node
        NodeFlowAdapter adapter = new NodeFlowAdapter(node, nodeFlowPath, this);
        
        instance.setFlowAdapter(adapter);
        
        // Register in ProcessRegistry
        return CompletableFuture.runAsync(() -> {
            processRegistry.registerProcess(adapter, nodeFlowPath);
            System.out.println("[NodeController] Node registered at: " + nodeFlowPath);
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    // ===== NODE UNLOADING =====
    
    /**
     * Unload a running node
     * 
     * @param packageId Package identifier (NoteBytesReadOnly)
     */
    public CompletableFuture<Void> unloadNode(NoteBytesReadOnly packageId) {
        NodeInstance instance = nodes.get(packageId);
        
        if (instance == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Node not loaded: " + packageId.getAsString()));
        }
        
        state.addState(UNLOADING_NODE);
        
        String pkgIdStr = packageId.getAsString();
        System.out.println("[NodeController] Unloading node: " + pkgIdStr);
        
        instance.setState(NodeState.STOPPING);
        
        INode node = instance.getNode();
        
        // Shutdown sequence
        return CompletableFuture.runAsync(() -> {
            // Notify subscribers
            if (node.hasFlowSubscribers()) {
                NoteBytesMap shutdownEvent = new NoteBytesMap();
                shutdownEvent.put(Keys.CMD, ProtocolMesssages.SHUTDOWN);
                shutdownEvent.put(Keys.NODE_ID, new NoteBytes(pkgIdStr));
                
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
            appData.nodeRegistry().remove(packageId);
            
            instance.setState(NodeState.STOPPED);
            
            System.out.println("[NodeController] Node unloaded: " + pkgIdStr);
        })
        .whenComplete((result, ex) -> {
            state.removeState(UNLOADING_NODE);
            
            if (ex != null) {
                System.err.println("[NodeController] Error unloading node: " + 
                    pkgIdStr + " - " + ex.getMessage());
            }
        });
    }
    
    // ===== NODE QUERIES =====
    
    public NodeInstance getNode(NoteBytesReadOnly packageId) {
        return nodes.get(packageId);
    }
    
    public NodeInstance getNode(String packageId) {
        return getNode(new NoteBytesReadOnly(packageId));
    }
    
    public List<NodeInstance> getAllNodes() {
        return new ArrayList<>(nodes.values());
    }
    
    public List<NodeInstance> getNodesByState(NodeState state) {
        return nodes.values().stream()
            .filter(n -> n.getState() == state)
            .toList();
    }
    
    public boolean isNodeLoaded(NoteBytesReadOnly packageId) {
        return nodes.containsKey(packageId);
    }
    
    public boolean isNodeLoaded(String packageId) {
        return isNodeLoaded(new NoteBytesReadOnly(packageId));
    }
    
    // ===== INTER-NODE ROUTING =====
    
    public CompletableFuture<Void> routeNodeMessage(
            NoteBytesReadOnly fromNodeId,
            NoteBytesReadOnly toNodeId,
            RoutedPacket packet) {
        
        return nodeRouter.routeMessage(fromNodeId, toNodeId, packet);
    }
    
    public CompletableFuture<StreamChannel> requestNodeStreamChannel(
            NoteBytesReadOnly fromNodeId,
            NoteBytesReadOnly toNodeId) {
        
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
            
            RoutedMessageExecutor msgExec = m_routedMsgExecutorMap.get(cmd);
            if (msgExec != null) {
                return msgExec.execute(msg, packet);
            } else {
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unknown command: " + cmd.getAsString()));
            }
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<Void> handleLoadNodeRequest(NoteBytesMap msg) {
        NoteBytesReadOnly packageId = msg.getReadOnly(Keys.PACKAGE_ID);
        
        if (packageId == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("package_id required"));
        }
        
        return loadNode(packageId, null).thenApply(v -> null);
    }
    
    private CompletableFuture<Void> handleUnloadNodeRequest(NoteBytesMap msg) {
        NoteBytesReadOnly packageId = msg.getReadOnly(Keys.NODE_ID);
        
        if (packageId == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("node_id required"));
        }
        
        return unloadNode(packageId);
    }
    
    private CompletableFuture<Void> handleListNodesRequest(RoutedPacket original) {
        List<NodeInstance> allNodes = getAllNodes();
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.CMD, ProtocolMesssages.NODE_LIST);
        response.put(Keys.ITEM_COUNT, new NoteBytes(allNodes.size()));
        
        // TODO: Serialize node info
        
        reply(original, response.getNoteBytesObject());
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleNodeStatusRequest(
            NoteBytesMap msg, 
            RoutedPacket original) {
        
        NoteBytesReadOnly packageId = msg.getReadOnly(Keys.NODE_ID);
        NodeInstance instance = nodes.get(packageId);
        
        if (instance == null) {
            NoteBytesMap error = new NoteBytesMap();
            error.put(Keys.CMD, ProtocolMesssages.ERROR);
            error.put(ProtocolMesssages.ERROR, 
                new NoteBytes("Node not found: " + packageId.getAsString()));
            
            reply(original, error.getNoteBytesObject());
            return CompletableFuture.completedFuture(null);
        }
    
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.CMD, ProtocolMesssages.STATUS);
        response.put(Keys.NODE_ID, packageId);
        response.put(Keys.STATE, 
            new NoteBytes(instance.getState().toString()));
        response.put(Keys.ACTIVE, 
            new NoteBytes(instance.getNode().isActive()));
        
        reply(original, response.getNoteBytesObject());
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        System.err.println("[NodeController] Unexpected stream channel from: " + fromPath);
    }
    
    // ===== SHUTDOWN =====
    
    public CompletableFuture<Void> shutdown() {
        System.out.println("[NodeController] Shutting down - unloading " + 
            nodes.size() + " nodes");
        
        List<CompletableFuture<Void>> shutdownFutures = new ArrayList<>();
        
        for (NoteBytesReadOnly packageId : new ArrayList<>(nodes.keySet())) {
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
    
    public ContextPath getPath() {
        return contextPath;
    }
}