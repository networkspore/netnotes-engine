package io.netnotes.engine.core.system.control.nodes;

import io.netnotes.engine.core.NoteFileServiceInterface;
import io.netnotes.engine.core.ScopedNoteFileInterface;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.core.system.control.nodes.security.NodeSecurityPolicy;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * NodeController - 
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * CLEAN RESPONSIBILITIES:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * 1. Receives fully-formed NodeLoadRequest (all decisions pre-made)
 * 2. Loads INode from package using NodeLoader
 * 3. Creates scoped interfaces (file + process) with security enforcement
 * 4. Initializes INode with interfaces
 * 5. Tracks running instances in NodeInstanceRegistry
 * 6. Handles unload requests
 * 
 * DOES NOT:
 * - Make namespace decisions (done at install)
 * - Make security decisions (done at install)
 * - Make inheritance decisions (done at install)
 * - Manage package installation (that's NodeManager's job)
 */
public class NodeController extends FlowProcess {
    
    private final NoteFileServiceInterface fileService;
    private final NodeLoader nodeLoader;
    private final NodeInstanceRegistry instanceRegistry;
    
    public NodeController(
        String name,
        NoteFileServiceInterface fileService
    ) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.fileService = fileService;
        this.nodeLoader = new NodeLoader(name + "-loader", registry, fileService);
        this.instanceRegistry = new NodeInstanceRegistry();
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════
    
    @Override
    public CompletableFuture<Void> run() {
        return initialize()
            .thenCompose(v -> getCompletionFuture());
    }
    
    private CompletableFuture<Void> initialize() {
        System.out.println("[NodeController] Initializing at: " + contextPath);
        // Load auto-start nodes if needed
        return CompletableFuture.completedFuture(null);
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // LOAD NODE (Simplified)
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Load a node from a fully-formed request
     * 
     * ALL DECISIONS ALREADY MADE:
     * - Process configuration (namespace, inheritance)
     * - Security policy (capabilities)
     * - Package metadata
     * 
     * NodeController just applies the configuration
     */
    public CompletableFuture<NodeInstance> loadNode(NodeLoadRequest request) {
        // Validate request
        List<String> errors = request.validate();
        if (!errors.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Invalid request: " + errors));
        }
        
        InstalledPackage pkg = request.getPackage();
        ProcessConfig processConfig = request.getProcessConfig();
        NodeSecurityPolicy securityPolicy = request.getSecurityPolicy();
        // TODO: securitypolicy not used
        // Check if already loaded with this processId
        if (instanceRegistry.isLoaded(pkg.getPackageId(), processConfig.getProcessId())) {
            return CompletableFuture.failedFuture(
                new IllegalStateException(
                    "Package already loaded with processId: " + processConfig.getProcessId()));
        }
        
        System.out.println("[NodeController] Loading node:");
        System.out.println("  Package: " + pkg.getName() + " v" + pkg.getPackageId().getVersion());
        System.out.println("  ProcessId: " + processConfig.getProcessId());
        System.out.println("  Mode: " + processConfig.getMode());
        System.out.println("  Data root: " + processConfig.getDataRootPath());
        System.out.println("  Flow base: " + processConfig.getFlowBasePath());
        
        // Load INode from package
        return nodeLoader.loadNodeFromPackage(pkg)
            .thenCompose(inode -> initializeAndRegisterNode(pkg, inode));
    }
    
    /**
     * Initialize INode and register in runtime
     */
    private CompletableFuture<NodeInstance> initializeAndRegisterNode(
        InstalledPackage pkg,
        INode inode
    ) {
        // Create instance wrapper
        NodeInstance instance = new NodeInstance(pkg, inode);
        
        // Get configuration from package (already decided at install)
        ProcessConfig processConfig = pkg.getProcessConfig();
        NodeSecurityPolicy securityPolicy = pkg.getSecurityPolicy();
        
        // ===== CREATE SCOPED FILE INTERFACE =====
        // Scope: /data/nodes/{processId}
        NoteFileServiceInterface nodeFileInterface = new ScopedNoteFileInterface(
            fileService,
            processConfig.getDataRootPath()
        );
        
        // ===== CREATE SCOPED PROCESS INTERFACE WITH SECURITY =====
        // Scope: /system/nodes/{processId}
        ProcessRegistryInterface nodeProcessInterface = new NodeProcessInterface(
            registry,
            processConfig.getFlowBasePath(),
            securityPolicy
        );
        
        instance.setState(NodeState.INITIALIZING);
        
        // ===== INITIALIZE INODE =====
        return inode.initialize(nodeFileInterface, nodeProcessInterface)
            .thenApply(v -> {
                instance.setState(NodeState.RUNNING);
                
                // Register in instance registry
                instanceRegistry.register(instance);
                
                System.out.println("[NodeController] Node loaded successfully:");
                System.out.println("  Instance: " + instance.getInstanceId());
                System.out.println("  Package: " + pkg.getName());
                System.out.println("  ProcessId: " + processConfig.getProcessId());
                
                return instance;
            })
            .exceptionally(ex -> {
                System.err.println("[NodeController] Failed to initialize node: " + 
                    ex.getMessage());
                instance.setState(NodeState.CRASHED);
                throw new RuntimeException("Node initialization failed", ex);
            });
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // UNLOAD NODE
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Unload a running instance
     */
    public CompletableFuture<Void> unloadInstance(InstanceId instanceId) {
        NodeInstance instance = instanceRegistry.getInstance(instanceId);
        
        if (instance == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Instance not found: " + instanceId));
        }
        
        System.out.println("[NodeController] Unloading instance: " + instanceId);
        
        instance.setState(NodeState.STOPPING);
        
        return instance.getINode().shutdown()
            .thenRun(() -> {
                // Unregister from instance registry
                instanceRegistry.unregister(instanceId);
                
                instance.setState(NodeState.STOPPED);
                
                System.out.println("[NodeController] Instance unloaded: " + instanceId);
            })
            .exceptionally(ex -> {
                System.err.println("[NodeController] Error unloading instance: " + 
                    instanceId + " - " + ex.getMessage());
                return null;
            });
    }
    
    /**
     * Unload all instances of a package
     */
    public CompletableFuture<Void> unloadPackage(PackageId packageId) {
        List<NodeInstance> instances = 
            instanceRegistry.getInstancesByPackage(packageId);
        
        if (instances.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        System.out.println("[NodeController] Unloading " + instances.size() + 
            " instances of package: " + packageId);
        
        List<CompletableFuture<Void>> futures = instances.stream()
            .map(inst -> unloadInstance(inst.getInstanceId()))
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Unload all instances in a process namespace
     */
    public CompletableFuture<Void> unloadProcess(String processId) {
        List<NodeInstance> instances = 
            instanceRegistry.getInstancesByProcess(processId);
        
        if (instances.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        System.out.println("[NodeController] Unloading " + instances.size() + 
            " instances in process: " + processId);
        
        List<CompletableFuture<Void>> futures = instances.stream()
            .map(inst -> unloadInstance(inst.getInstanceId()))
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // QUERIES
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Get instance by ID
     */
    public NodeInstance getInstance(InstanceId instanceId) {
        return instanceRegistry.getInstance(instanceId);
    }
    
    /**
     * Get all instances of a package
     */
    public List<NodeInstance> getInstancesByPackage(PackageId packageId) {
        return instanceRegistry.getInstancesByPackage(packageId);
    }
    
    /**
     * Get all instances in a process namespace
     */
    public List<NodeInstance> getInstancesByProcess(String processId) {
        return instanceRegistry.getInstancesByProcess(processId);
    }
    
    /**
     * Get all running instances
     */
    public List<NodeInstance> getAllInstances() {
        return instanceRegistry.getAllInstances();
    }
    
    /**
     * Get running instances by state
     */
    public List<NodeInstance> getInstancesByState(NodeState state) {
        return getAllInstances().stream()
            .filter(inst -> inst.getState() == state)
            .toList();
    }
    
    /**
     * Check if package is loaded (any processId)
     */
    public boolean isPackageLoaded(PackageId packageId) {
        return instanceRegistry.isPackageLoaded(packageId);
    }
    
    /**
     * Check if specific package+process combination is loaded
     */
    public boolean isLoaded(PackageId packageId, NoteBytesReadOnly processId) {
        return instanceRegistry.isLoaded(packageId, processId);
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // MESSAGE HANDLING
    // ═══════════════════════════════════════════════════════════════════════
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Handle commands like:
        // - load_node (with NodeLoadRequest)
        // - unload_instance (with instanceId)
        // - unload_package (with packageId)
        // - list_instances
        // - instance_status (with instanceId)
        
        // For now, simplified
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        System.err.println("[NodeController] Unexpected stream channel from: " + fromPath);
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // SHUTDOWN
    // ═══════════════════════════════════════════════════════════════════════
    
    public CompletableFuture<Void> shutdown() {
        System.out.println("[NodeController] Shutting down - unloading " + 
            instanceRegistry.getAllInstances().size() + " instances");
        
        List<CompletableFuture<Void>> shutdownFutures = new ArrayList<>();
        
        for (NodeInstance instance : instanceRegistry.getAllInstances()) {
            shutdownFutures.add(unloadInstance(instance.getInstanceId()));
        }
        
        return CompletableFuture.allOf(
            shutdownFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                System.out.println("[NodeController] Shutdown complete");
            });
    }
}