package io.netnotes.engine.core.system.control.nodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.core.NoteFileServiceInterface;
import io.netnotes.engine.core.ScopedNoteFilenterface;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;

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

    public static final String registryName = "node-registry";
    public static final String loaderName = "node-loader";
    public static final String NODES = "nodes";
    public static final String RUNTIME = "runtime";

    private final BitFlagStateMachine state;
    private final NoteFileServiceInterface noteFilenterface;
    private InstallationRegistry installationRegistry;

    // Node registry: packageId → NodeInstance (ONE instance per package)
    private final Map<NoteBytesReadOnly, NodeInstance> nodes = new ConcurrentHashMap<>();

    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_routedMsgExecutorMap = new HashMap<>();

    // Node loader (OSGi integration)
    private NodeLoader nodeLoader;
    private final ContextPath nodesPath;
    
    // States
    private static final long INITIALIZING = 1L << 0;
    private static final long READY = 1L << 1;
    private static final long LOADING_NODE = 1L << 2;
    private static final long UNLOADING_NODE = 1L << 3;
    
    /**
     * Constructor
     * 
     * 
     * @param name Where controller lives (given by parent)
     * @param appData For creating node interfaces
     * @param processService For registering nodes in network
     * @param installationRegistry For package metadata
     */
    public NodeController(
        String name,
        NoteFileServiceInterface noteFilenterface,
        ContextPath nodesPath
    ) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.noteFilenterface = noteFilenterface;
        this.state = new BitFlagStateMachine(name + "-state");
        this.nodesPath = nodesPath;
        setupMsgExecutorMap();
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
        
        return initializeInstallationRegistry()
            .thenCompose(v -> initializeNodeLoader())
            .thenCompose(v -> loadAutoStartNodes())
            .thenRun(() -> {
                System.out.println("[NodeController] Initialized with " + 
                    nodes.size() + " auto-start nodes");
            });
    }

    private CompletableFuture<Void> initializeInstallationRegistry() {
   
        // Create child interface scoped to registry subtree
        ContextPath installedFilePath = noteFilenterface.getBasePath().append(registryName);
        
        return noteFilenterface.getNoteFile(installedFilePath)
            .thenCompose(registryFile->{
    
                this.installationRegistry = new InstallationRegistry(registryName, registryFile );
                ContextPath path = registryInterface.registerChild(installationRegistry);
                System.out.println("[SystemRuntime] Registered InstallationRegistry at: " + path);
                return registryInterface.startChild(path)
                    .thenCompose(v -> installationRegistry.initialize());
            });
            
    }

    /**
     * Initialize NodeLoader with scoped interface
     */
    private CompletableFuture<Void> initializeNodeLoader() {
  
        ContextPath packagesProcessesPath = contextPath.append(loaderName);
        ContextPath packagesFilePath = noteFilenterface.getBasePath().append(loaderName);
        // Create interface using our parent's createChildInterface
        ProcessRegistryInterface loaderProcessInterface = 
            registryInterface.createChildInterface(
                contextPath.append(contextPath),
                (caller, target) -> target.startsWith(packagesProcessesPath)
            );
                // NodeLoader needs interface to read package files
   
        NoteFileServiceInterface loaderFileInterface = new ScopedNoteFilenterface(noteFilenterface,
            packagesFilePath,
            null  // No alt path
        );
        this.nodeLoader = new NodeLoader(loaderProcessInterface, loaderFileInterface);
        
        return CompletableFuture.completedFuture(null);
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
        ContextPath nodeFlowPath = nodesPath.append(pkgIdStr);
        ContextPath runtimePath = noteFilenterface.getBasePath().append(RUNTIME, pkgIdStr);
        ContextPath userPath = noteFilenterface.getAltPath().append(pkgIdStr);
        
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
                NoteFileServiceInterface nodeDataInterface = new ScopedNoteFilenterface(
                    noteFilenterface,
                    runtimePath,  // Primary: system runtime data
                    userPath      // Alternative: user data
                );
                
                instance.setDataInterface(nodeDataInterface);
                ProcessRegistryInterface nodeProcessInterface = 
                    registryInterface.createChildInterface(
                        nodeFlowPath,
                        (caller, target) -> {
                            // Node can reach:
                            // - Its own children
                            // - Its parent (controller)
                            // - Other nodes (if controller allows)
                            return target.startsWith(nodeFlowPath) ||  // Own children
                                   target.equals(contextPath) ||        // Parent (controller)
                                   target.startsWith(nodesPath); // Sibling nodes
                        }
                    );
                instance.setState(NodeState.INITIALIZING);

                // Register in FlowProcess network
                return inode.initialize(nodeDataInterface, nodeProcessInterface) 
                    .thenApply((v) -> {
                        instance.setState(NodeState.RUNNING);
                        
                        return instance;
                    });
            })
            .whenComplete((v, ex) -> {
                state.removeState(LOADING_NODE);
                
                if (ex != null) {
                    System.err.println("[NodeController] Failed to load node: " + 
                        pkgIdStr + " - " + ex.getMessage());
                    nodes.remove(packageId);
                } else {
                    System.out.println("[NodeController] Node loaded successfully: " + 
                        pkgIdStr);
                }
            });
    }
    

    
    // ===== NODE UNLOADING =====
    
     /**
     * Unload a running node
     */
    public CompletableFuture<Void> unloadNode(NoteBytesReadOnly packageId) {
        NodeInstance instance = nodes.get(packageId);
        
        if (instance == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Node not loaded: " + 
                    packageId.getAsString()));
        }
        
        state.addState(UNLOADING_NODE);
        
        String pkgIdStr = packageId.getAsString();
        System.out.println("[NodeController] Unloading node: " + pkgIdStr);
        
        instance.setState(NodeState.STOPPING);
        
        INode node = instance.getNode();
        
        // Shutdown sequence
        return node.shutdown()
        .thenRun(() -> {
            // Remove from registries
            nodes.remove(packageId);
            
            instance.setState(NodeState.STOPPED);
            
            System.out.println("[NodeController] Node unloaded: " + pkgIdStr);
        })
        .whenComplete((v, ex) -> {
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
    

}