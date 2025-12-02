package io.netnotes.engine.core.system.control.nodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import io.netnotes.engine.core.NoteFileServiceInterface;
import io.netnotes.engine.core.ScopedNoteFileInterface;
import io.netnotes.engine.core.system.SystemProcess;
import io.netnotes.engine.core.system.control.nodes.security.NodeSecurityPolicy;
import io.netnotes.engine.core.system.control.nodes.security.PackageTrust;
import io.netnotes.engine.core.system.control.nodes.security.PathCapability;
import io.netnotes.engine.core.system.control.nodes.security.PolicyManifest;
import io.netnotes.engine.core.system.control.nodes.security.PolicyManifest.ClusterConfig;
import io.netnotes.engine.core.system.control.nodes.security.PolicyManifest.ClusterRole;
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

    public static final class Paths {
        public static final ContextPath RUNTIME = SystemProcess.RUNTIME_PATH;

    }

   

    private final ContextPath NODES_PATH = SystemProcess.NODES_PATH;
    private final ContextPath CLUSTERS_PATH = SystemProcess.CLUSTERS_PATH;

    private final BitFlagStateMachine state;
    private final NoteFileServiceInterface noteFileInterface;
    private InstallationRegistry installationRegistry;

    // Node registry: packageId → NodeInstance (ONE instance per package)
    private final Map<NoteBytesReadOnly, NodeInstance> nodes = new ConcurrentHashMap<>();

    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_routedMsgExecutorMap = new HashMap<>();

    // Node loader (OSGi integration)
    private NodeLoader nodeLoader;
    private final ContextPath ioDaemonPath = SystemProcess.IO_SERVICE_PATH;
    
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
        NoteFileServiceInterface noteFilenterface
    ) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.noteFileInterface = noteFilenterface;
        this.state = new BitFlagStateMachine(name + "-state");
     


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
        ContextPath installedFilePath = noteFileInterface.getBasePath().append(registryName);
        
        return noteFileInterface.getNoteFile(installedFilePath)
            .thenCompose(registryFile->{
    
                this.installationRegistry = new InstallationRegistry(registryName, registryFile );
                ContextPath path = registerChild(installationRegistry);
                System.out.println("[SystemRuntime] Registered InstallationRegistry at: " + path);
                return startChild(path)
                    .thenCompose(v -> installationRegistry.initialize());
            });
            
    }

    /**
     * Initialize NodeLoader with scoped interface
     * 
     */
    private CompletableFuture<Void> initializeNodeLoader() {
  
        ContextPath packagesProcessesPath = contextPath.append(loaderName);
        ContextPath packagesFilePath = noteFileInterface.getBasePath().append(loaderName);
  
    
        this.nodeLoader = new NodeLoader(registry, noteFileInterface);
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> loadAutoStartNodes() {
        // TODO: Query InstallationRegistry for packages with autoload=true
        // For now, just complete
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== NODE LOADING =====
    
    /**
     * Overload: Load standalone node (no cluster)
     */
    public CompletableFuture<NodeInstance> loadNode(
            NoteBytesReadOnly packageId,
            InstalledPackage installedPackage) {
        return loadNode(packageId, installedPackage, null);
    }

    /**
     * Load a node from an installed package
     * 
     * Clustering considerations:
     * - Standalone: nodeFlowPath = /nodes/{packageId}
     * - Cluster leader: nodeFlowPath = /nodes/clusters/{clusterId}/leader
     * - Cluster member: nodeFlowPath = /nodes/clusters/{clusterId}/members/{packageId}
     * - Shared leader: Multiple INodes share same leader path
     * 
     * @param packageId Package identifier
     * @param installedPackage Package metadata (optional, will lookup if null)
     * @param clusterConfig Cluster configuration (null for standalone)
     * @return NodeInstance wrapper
     */
    public CompletableFuture<NodeInstance> loadNode(
            NoteBytesReadOnly packageId,
            InstalledPackage installedPackage,
            ClusterConfig clusterConfig) {
        
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
        
        NodeSecurityPolicy policy = getOrCreatePolicy(packageId, pkg);

        System.out.println("  Capabilities: " + policy.getGrantedCapabilities().size());
        for (PathCapability cap : policy.getGrantedCapabilities()) {
            System.out.println("    - " + cap.getDescription());
        }
    
        // === COMPUTE PATHS (Cluster-aware) ===
        PathConfiguration pathConfig = computePaths(packageId, pkgIdStr, clusterConfig);
        
        System.out.println("[NodeController] Node paths:");
        System.out.println("  Flow:    " + pathConfig.nodeFlowPath);
        System.out.println("  Runtime: " + pathConfig.runtimePath);
        System.out.println("  User:    " + pathConfig.userPath);
        if (clusterConfig != null) {
            System.out.println("  Cluster: " + clusterConfig.getClusterId() + 
                " (role: " + clusterConfig.getRole() + ")");
        }
        
        return nodeLoader.loadNodeFromPackage(pkg)
            .thenCompose(inode -> {
                // Create node instance wrapper
                NodeInstance instance = new NodeInstance(
                    packageId,
                    pkg,
                    inode,
                    NodeState.LOADING
                );
                
                // Store instance
                nodes.put(packageId, instance);
                
                // === CREATE SCOPED FILE INTERFACE ===
                
                NoteFileServiceInterface nodeDataInterface = new ScopedNoteFileInterface(
                    noteFileInterface,
                    pathConfig.runtimePath,  // Primary: system runtime data
                    pathConfig.userPath      // Alternative: user data
                );
                
                instance.setDataInterface(nodeDataInterface);
                
                // === CREATE PROCESS INTERFACE WITH CAPABILITY ENFORCEMENT ===
                ProcessRegistryInterface nodeProcessInterface = createNodeInterface(
                    pathConfig.nodeFlowPath,
                    policy
                );
                
                instance.setState(NodeState.INITIALIZING);
                
                // === INITIALIZE INODE ===
                // Interface is passed to INode, not stored in instance
                // INode decides if/how to register itself in the process tree
                return inode.initialize(nodeDataInterface, nodeProcessInterface)
                    .thenApply((v) -> {
                        instance.setState(NodeState.RUNNING);
                        
                        // Check if INode registered itself
                        if (registry.exists(pathConfig.nodeFlowPath)) {
                            System.out.println("[NodeController] INode registered at: " + 
                                pathConfig.nodeFlowPath);
                        } else {
                            System.out.println("[NodeController] INode did NOT register " +
                                "(might be cluster member delegating to leader)");
                        }
                        
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
    private NodeSecurityPolicy getOrCreatePolicy(
        NoteBytesReadOnly packageId,
        InstalledPackage pkg) {
    
        // Check if policy already exists
        // (would be stored in InstallationRegistry)
        
        NodeSecurityPolicy policy = new NodeSecurityPolicy(
            packageId,
            pkg.getPackageId(),
            "system"  // TODO: Get actual user ID
        );
        
        // Default capabilities are granted by constructor:
        // - PathCapability.messageController() → SystemProcess.NODE_CONTROLLER_PATH
        // - PathCapability.ownRuntimeData() → NodeController.NODES_PATH + {self}
        // - PathCapability.ownUserData() → /user/{self}
        
        // Grant additional capabilities from manifest
        if (pkg.getManifest() != null) {
            PolicyManifest manifest = pkg.getManifest().getSecurityPolicy();
            if (manifest != null) {
                for (PathCapability cap : manifest.getRequestedCapabilities()) {
                    policy.grantCapability(cap);
                }
            }
        }
        
        return policy;
    }

    /**
    * Find all nodes (using enhanced registry)
    */
    public List<NodeInstance> findAllNodes() {
        // Use path-prefix query
        List<FlowProcess> processes = registry.findByPathPrefix(NODES_PATH);
        
        return processes.stream()
            .map(p -> {
                // Find corresponding NodeInstance
                for (NodeInstance instance : nodes.values()) {
                    if (instance.getNode().getContextPath().equals(p.getContextPath())) {
                        return instance;
                    }
                }
                return null;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Find all nodes in a cluster
     */
    public List<NodeInstance> findNodesInCluster(String clusterId) {
        ContextPath clusterPath = CLUSTERS_PATH.append(clusterId);
        
        // Find all processes under cluster path
        List<FlowProcess> processes = registry.findByPathPrefix(clusterPath);
        
        System.out.println("[NodeController] Found " + processes.size() + 
            " processes in cluster: " + clusterId);
        
        // Map to NodeInstances
        return processes.stream()
            .map(p -> {
                for (NodeInstance instance : nodes.values()) {
                    if (instance.getNode().getContextPath().equals(p.getContextPath())) {
                        return instance;
                    }
                }
                return null;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Example: Service discovery using path queries
     */
    public List<FlowProcess> findAvailableServices() {
        // Query all under services path - no intermediate process needed!
        return registry.findByPathPrefix(SystemProcess.SERVICES_PATH);
    }

    /**
     * Path configuration for a node
     */
    private static class PathConfiguration {
        ContextPath nodeFlowPath;   // Where INode registers in process tree
        ContextPath runtimePath;    // System runtime data
        ContextPath userPath;       // User data
    }

    /**
     * Cluster configuration
    
    public static class ClusterConfig {
        String clusterId;
        ClusterRole role;
        
        public ClusterConfig(String clusterId, ClusterRole role) {
            this.clusterId = clusterId;
            this.role = role;
        }
        
        public enum ClusterRole {
            LEADER,        // Registers itself, handles all cluster communication
            MEMBER,        // Doesn't register, delegates to leader
            SHARED_LEADER  // Multiple INodes share same leader registration
        }
    } */

    
    /**
     * Compute paths for node (cluster-aware)
     */
    private PathConfiguration computePaths(
            NoteBytesReadOnly packageId,
            String pkgIdStr,
            ClusterConfig clusterConfig) {
        
        PathConfiguration config = new PathConfiguration();
        
        if (clusterConfig == null) {
            // === STANDALONE NODE ===
            config.nodeFlowPath = NODES_PATH.append(pkgIdStr);
            config.runtimePath = noteFileInterface.getBasePath()
                .append(RUNTIME, pkgIdStr);
            config.userPath = noteFileInterface.getAltPath()
                .append(pkgIdStr);
            
        } else {
            // === CLUSTER NODE ===
            String clusterId = clusterConfig.getClusterId();
            
            switch (clusterConfig.getRole()) {
                case ClusterRole.LEADER:
                    // Leader registers at /nodes/clusters/{clusterId}/leader
                    config.nodeFlowPath = CLUSTERS_PATH
                        .append(clusterId)
                        .append("leader");
                    
                    // Leader's runtime data
                    config.runtimePath = noteFileInterface.getBasePath()
                        .append(RUNTIME, CLUSTERS, clusterId, "leader");
                    
                    // Leader's user data (shared cluster data)
                    config.userPath = noteFileInterface.getAltPath()
                        .append(CLUSTERS, clusterId, "shared");
                    break;
                    
                case ClusterRole.MEMBER:
                    // Member doesn't register itself - uses leader's path for communication
                    // But has its own data paths
                    config.nodeFlowPath = CLUSTERS_PATH
                        .append(clusterId)
                        .append("leader");  // Members use leader's path!
                    
                    // Member's private runtime data
                    config.runtimePath = noteFileInterface.getBasePath()
                        .append(RUNTIME, CLUSTERS, clusterId, "members", pkgIdStr);
                    
                    // Member's private user data
                    config.userPath = noteFileInterface.getAltPath()
                        .append(CLUSTERS, clusterId, "members", pkgIdStr);
                    break;

                case ClusterRole.SHARED_LEADER:
                    // Multiple INodes share the same leader path
                    config.nodeFlowPath = CLUSTERS_PATH
                        .append(clusterId)
                        .append("leader");
                    
                    // Shared runtime data
                    config.runtimePath = noteFileInterface.getBasePath()
                        .append(RUNTIME, CLUSTERS, clusterId, "shared");
                    
                    // Shared user data
                    config.userPath = noteFileInterface.getAltPath()
                        .append(CLUSTERS, clusterId, "shared");
                    break;
            }
        }
        
        return config;
    }

    private ProcessRegistryInterface createNodeInterface(
        ContextPath nodeFlowPath,
        NodeSecurityPolicy policy
    ) {
  
        // Wrap with security enforcement
        return new NodeProcessInterface(
            registry,
            nodeFlowPath,  // Node's base scope
            policy
        );
    }

    /**
     * Create a cluster with one leader and multiple members
     */
    public CompletableFuture<Void> createCluster(
            String clusterId,
            NoteBytesReadOnly leaderPackageId,
            List<NoteBytesReadOnly> memberPackageIds) {
       
        // 1. Load leader node
        ClusterConfig leaderConfig = new ClusterConfig(clusterId, ClusterRole.LEADER);
        
        return loadNode(leaderPackageId, null, leaderConfig)
            .thenCompose(leaderInstance -> {
                ClusterLeaderNode leader = (ClusterLeaderNode) leaderInstance.getNode();
                
                // 2. Load member nodes
                List<CompletableFuture<NodeInstance>> memberFutures = memberPackageIds.stream()
                    .map(memberId -> {
                        ClusterConfig memberConfig = new ClusterConfig(
                            clusterId, 
                            ClusterRole.MEMBER
                        );
                        return loadNode(memberId, null, memberConfig);
                    })
                    .toList();
                
                return CompletableFuture.allOf(
                    memberFutures.toArray(new CompletableFuture[0])
                ).thenAccept(v -> {
                    // 3. Wire members to leader
                    for (CompletableFuture<NodeInstance> memberFuture : memberFutures) {
                        NodeInstance memberInstance = memberFuture.join();
                        ClusterMemberNode member = (ClusterMemberNode) memberInstance.getNode();
                        
                        member.setLeader(leader);
                        leader.addMember(
                            memberInstance.getPackageId().getAsString(), 
                            member
                        );
                    }
                    
                    System.out.println("[NodeController] Cluster created: " + clusterId +
                        " (1 leader, " + memberPackageIds.size() + " members)");
                });
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