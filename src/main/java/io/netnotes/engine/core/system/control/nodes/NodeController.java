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
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
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
 * KEY CHANGES:
 * - Uses NoteBytesReadOnly for instance IDs (not String)
 * - Manages node instances (not packages)
 * - FlowProcessRegistry injected (not stored in AppData)
 */
public class NodeController extends FlowProcess {
    public static final class CMDS{
        public static final NoteBytesReadOnly LOAD_NODE = new NoteBytesReadOnly("load_node");
        public static final NoteBytesReadOnly UNLOAD_NODE = new NoteBytesReadOnly("unload_node");
        public static final NoteBytesReadOnly LIST_NODES = ProtocolMesssages.ITEM_LIST;
        public static final NoteBytesReadOnly NODE_STATUS = ProtocolMesssages.STATUS;
    }

 
    private final BitFlagStateMachine state;
    private final AppData appData;
    private final FlowProcessRegistry processRegistry;
    private final InstallationRegistry installationRegistry;

    // Node registry (runtime instances): instanceId → NodeInstance
    // Uses NoteBytesReadOnly for IDs
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
    
    public NodeController(
            AppData appData,
            FlowProcessRegistry processRegistry,
            InstallationRegistry installationRegistry) {

        super(ProcessType.BIDIRECTIONAL);
        this.appData = appData;
        this.processRegistry = processRegistry;
        this.installationRegistry = installationRegistry;
        this.state = new BitFlagStateMachine("node-controller");
        setupMsgExecutorMap();
        this.nodeLoader = new NodeLoader(appData);
        this.nodeRouter = new NodeRouter(this);
    }
    
    private void setupMsgExecutorMap(){
        m_routedMsgExecutorMap.put(CMDS.LOAD_NODE,      (map, packet)-> handleLoadNodeRequest(map));
        m_routedMsgExecutorMap.put(CMDS.UNLOAD_NODE,    (map, packet)-> handleUnloadNodeRequest(map));
        m_routedMsgExecutorMap.put(CMDS.LIST_NODES,     (map, packet)-> handleListNodesRequest(packet));
        m_routedMsgExecutorMap.put(CMDS.NODE_STATUS,    (map, packet)-> handleNodeStatusRequest(map, packet));
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
     * @param instanceId Unique instance identifier (NoteBytesReadOnly)
     * @param installedPackage Package metadata
     * @return NodeInstance wrapper
     */
    public CompletableFuture<NodeInstance> loadNode(
            NoteBytesReadOnly instanceId,
            InstalledPackage installedPackage) {
        
        if (nodes.containsKey(instanceId)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Node already loaded: " + instanceId.getAsString()));
        }
        
        state.addState(LOADING_NODE);
        
        String instanceIdStr = instanceId.getAsString();
        System.out.println("[NodeController] Loading node: " + instanceIdStr);
        
        InstalledPackage pkg = installedPackage == null ? 
            installationRegistry.getPackage(instanceId) : 
            installedPackage;
        
        return nodeLoader.loadNodeFromPackage(pkg)
            .thenCompose(inode -> {
                // Create node instance wrapper
                NodeInstance instance = new NodeInstance(
                    instanceId,
                    pkg,
                    inode,
                    NodeState.LOADING
                );
                
                ContextPath packagePath = ContextPath.of("nodes", instanceIdStr);
                nodes.put(instanceId, instance);
                
                // Create sandboxed interface using NoteBytesReadOnly
                AppDataInterface sandboxedInterface = appData.getNodeInterface(instanceId);
                
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
                        instanceIdStr + " - " + ex.getMessage());
                    nodes.remove(instanceId);
                } else {
                    System.out.println("[NodeController] Node loaded successfully: " + 
                        instanceIdStr);
                    
                    // Register in AppData's registry
                    appData.nodeRegistry().put(instanceId, instance.getNode());
                }
            });
    }
    
    /**
     * Register node in FlowProcess network
     */
    private CompletableFuture<Void> registerNodeInNetwork(NodeInstance instance) {
        INode node = instance.getNode();
        
        // Build context path: /system/controller/nodes/{packageId}
        ContextPath nodePath = contextPath.append("nodes", instance.getPackageId().getAsString());
        
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
     * @param instanceId Instance identifier (NoteBytesReadOnly)
     */
    public CompletableFuture<Void> unloadNode(NoteBytesReadOnly instanceId) {
        NodeInstance instance = nodes.get(instanceId);
        
        if (instance == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Node not loaded: " + instanceId.getAsString()));
        }
        
        state.addState(UNLOADING_NODE);
        
        String instanceIdStr = instanceId.getAsString();
        System.out.println("[NodeController] Unloading node: " + instanceIdStr);
        
        instance.setState(NodeState.STOPPING);
        
        INode node = instance.getNode();
        
        // Shutdown sequence
        return CompletableFuture.runAsync(() -> {
            // Notify subscribers
            if (node.hasFlowSubscribers()) {
                NoteBytesMap shutdownEvent = new NoteBytesMap();
                shutdownEvent.put(Keys.CMD, ProtocolMesssages.SHUTDOWN);
                shutdownEvent.put(Keys.NODE_ID, 
                    new NoteBytes(instanceIdStr));
                
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
            nodes.remove(instanceId);
            appData.nodeRegistry().remove(instanceId);
            
            instance.setState(NodeState.STOPPED);
            
            System.out.println("[NodeController] Node unloaded: " + instanceIdStr);
        })
        .whenComplete((result, ex) -> {
            state.removeState(UNLOADING_NODE);
            
            if (ex != null) {
                System.err.println("[NodeController] Error unloading node: " + 
                    instanceIdStr + " - " + ex.getMessage());
            }
        });
    }
    
    // ===== NODE QUERIES =====
    
    /**
     * Get node instance by ID (NoteBytesReadOnly)
     */
    public NodeInstance getNode(NoteBytesReadOnly instanceId) {
        return nodes.get(instanceId);
    }
    
    /**
     * Get node instance by ID (String)
     * @deprecated Use getNode(NoteBytesReadOnly)
     */
    @Deprecated
    public NodeInstance getNode(String instanceId) {
        return getNode(new NoteBytesReadOnly(instanceId));
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
    public boolean isNodeLoaded(NoteBytesReadOnly instanceId) {
        return nodes.containsKey(instanceId);
    }
    
    /**
     * Check if node is loaded
     * @deprecated Use isNodeLoaded(NoteBytesReadOnly)
     */
    @Deprecated
    public boolean isNodeLoaded(String instanceId) {
        return isNodeLoaded(new NoteBytesReadOnly(instanceId));
    }
    
    // ===== INTER-NODE ROUTING =====
    
    /**
     * Route message from one node to another
     */
    public CompletableFuture<Void> routeNodeMessage(
            NoteBytesReadOnly fromNodeId,
            NoteBytesReadOnly toNodeId,
            RoutedPacket packet) {
        
        return nodeRouter.routeMessage(fromNodeId, toNodeId, packet);
    }
    
    /**
     * Request stream channel between nodes
     */
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
            if(msgExec != null){
                return msgExec.execute(msg, packet);
            }else{
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Unknown command: " + cmd.getAsString()));
            }
          
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

  
    private CompletableFuture<Void> handleLoadNodeRequest(NoteBytesMap msg) {
        NoteBytesReadOnly packageId = msg.getReadOnly(Keys.PACKAGE_ID);
        
        // TODO: Get InstalledPackage from InstallationRegistry
        // For now, just fail
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("Load node not fully implemented"));
    }
    
    private CompletableFuture<Void> handleUnloadNodeRequest(NoteBytesMap msg) {
        NoteBytesReadOnly instanceId = msg.getReadOnly(Keys.NODE_ID);
        return unloadNode(instanceId);
    }
    
    private CompletableFuture<Void> handleListNodesRequest(RoutedPacket original) {
        List<NodeInstance> allNodes = getAllNodes();
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.CMD, ProtocolMesssages.NODE_LIST);
        response.put(Keys.ITEM_COUNT, new NoteBytes(allNodes.size()));
        
        // Build node info list
        // TODO: Serialize node info
        
        reply(original, response.getNoteBytesObject());
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleNodeStatusRequest(
            NoteBytesMap msg, 
            RoutedPacket original) {
        
        NoteBytesReadOnly instanceId = msg.getReadOnly(Keys.NODE_ID);
        NodeInstance instance = nodes.get(instanceId);
        
        if (instance == null) {
            NoteBytesMap error = new NoteBytesMap();
            error.put(Keys.CMD, ProtocolMesssages.ERROR);
            error.put(ProtocolMesssages.ERROR, 
                new NoteBytes("Node not found: " + instanceId.getAsString()));
            
            reply(original, error.getNoteBytesObject());
            return CompletableFuture.completedFuture(null);
        }
    
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.CMD, ProtocolMesssages.STATUS);
        response.put(Keys.NODE_ID, instanceId);
        response.put(Keys.STATE, 
            new NoteBytes(instance.getState().toString()));
        response.put(Keys.ACTIVE, 
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
        
        for (NoteBytesReadOnly instanceId : new ArrayList<>(nodes.keySet())) {
            shutdownFutures.add(unloadNode(instanceId));
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