package io.netnotes.engine.core.system.control.nodes;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.NoteFileServiceInterface;
import io.netnotes.engine.core.ScopedNoteFileInterface;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
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
    private InstallationRegistry installationRegistry;
    private InstallationExecutor installationExecutor;

    
    public NodeController(
        String name,
        NoteFileServiceInterface fileService
    ) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.fileService = fileService;
        this.nodeLoader = new NodeLoader(name + "-loader", registry, fileService);
        this.instanceRegistry = new NodeInstanceRegistry();
        this.installationExecutor = new InstallationExecutor(fileService);
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
        
        // Initialize installation registry
        return initializeInstallationRegistry()
            .thenRun(() -> {
                System.out.println("[NodeController] Initialization complete");
            });
    }

    private CompletableFuture<Void> initializeInstallationRegistry() {
        // Get registry file path
        ContextPath registryPath = CoreConstants.NODE_DATA_PATH
                .append("installation-registry");
        
        return fileService.getNoteFile(registryPath)
            .thenCompose(registryFile -> {
                this.installationRegistry = new InstallationRegistry(
                    "installation-registry",
                    registryFile
                );
                
                // Register as child process
                io.netnotes.engine.io.ContextPath regPath = registry.registerProcess(
                    installationRegistry,
                    contextPath.append("registry"),
                    contextPath,
                    registry
                );
                
                System.out.println("[NodeController] Registered InstallationRegistry at: " + regPath);
                
                // Start and initialize
                return registry.startProcess(regPath)
                    .thenCompose(v -> installationRegistry.initialize());
            });
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
 
        // Check if already loaded with this processId
        if (instanceRegistry.isLoaded(pkg.getPackageId(), processConfig.getProcessId())) {
            return CompletableFuture.failedFuture(
                new IllegalStateException(
                    "Package already loaded with processId: " + processConfig.getProcessId()));
        }
        
        System.out.println("[NodeController] Loading node:");
        System.out.println("  Package: " + pkg.getName() + " v" + pkg.getPackageId().getVersion());
        System.out.println("  ProcessId: " + processConfig.getProcessId());
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


    /**
     * Install a package
     */
    public CompletableFuture<InstalledPackage> installPackage(InstallationRequest request) {
        System.out.println("[NodeController] Installing package: " + 
            request.getPackageInfo().getName());
        
        // Execute installation
        return installationExecutor.executeInstallation(request)
            .thenCompose(installedPackage -> {
                // Register in installation registry
                return installationRegistry.registerPackage(installedPackage)
                    .thenApply(v -> installedPackage);
            })
            .thenCompose(installedPackage -> {
                // If loadImmediately flag is set, load it now
                if (request.shouldLoadImmediately()) {
                    NodeLoadRequest loadRequest = new NodeLoadRequest(installedPackage);
                    return loadNode(loadRequest)
                        .thenApply(instance -> installedPackage);
                }
                return CompletableFuture.completedFuture(installedPackage);
            });
    }

    /**
     * Uninstall a package
     */
    public CompletableFuture<Void> uninstallPackage(PackageId packageId, AsyncNoteBytesWriter progress) {
        System.out.println("[NodeController] Uninstalling package: " + packageId);
        
        // Check if package has running instances
        List<NodeInstance> instances = getInstancesByPackage(packageId);
        if (!instances.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException(
                    "Cannot uninstall package with running instances. Stop " + 
                    instances.size() + " instance(s) first."));
        }
        
        // Get installed package info
        InstalledPackage pkg = installationRegistry.getPackage(packageId);
        if (pkg == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Package not found: " + packageId));
        }
        
        // Unregister from installation registry
        return installationRegistry.unregisterPackage(packageId)
            .thenCompose(v -> {
                // Delete package files from storage
                return deletePackageFiles(pkg, progress);
            })
            .thenRun(() -> {
                System.out.println("[NodeController] Package uninstalled: " + packageId);
            });
    }

    /**
     * Delete package files from storage
     */
    private CompletableFuture<Void> deletePackageFiles(InstalledPackage pkg, AsyncNoteBytesWriter progress) {
        ContextPath installPath = pkg.getInstallPath();
        
        // Get the NoteFile for the install path
        return fileService.deleteNoteFile(installPath, false, progress)
            .thenAccept((notePath) -> {
                System.out.println("[NodeController] Deleted package files at: " + installPath);
            })
            .exceptionally(ex -> {
                System.err.println("[NodeController] Failed to delete package files: " + 
                    ex.getMessage());
                // Don't fail the uninstall if file deletion fails
                return null;
            });
        
    }

    /**
     * Delete package data directory
     */
    public CompletableFuture<Void> deletePackageData(PackageId packageId, AsyncNoteBytesWriter progress) {
        InstalledPackage pkg = installationRegistry.getPackage(packageId);
        if (pkg == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Package not found: " + packageId));
        }
        
        ContextPath dataPath = pkg.getProcessConfig().getDataRootPath();
        
        System.out.println("[NodeController] Deleting package data at: " + dataPath);
        
        return fileService.deleteNoteFile(dataPath, false, progress)
            .thenRun(() -> {
                System.out.println("[NodeController] Package data deleted");
            })
            .exceptionally(ex -> {
                System.err.println("[NodeController] Failed to delete package data: " + 
                    ex.getMessage());
                // Log but don't fail
                return null;
            });
    }

    /**
     * Update package configuration
     */
    public CompletableFuture<Void> updatePackageConfiguration(
        PackageId packageId,
        io.netnotes.engine.core.system.control.nodes.ProcessConfig newProcessConfig
    ) {
        System.out.println("[NodeController] Updating package configuration: " + packageId);
        
        // Check if package has running instances
        List<NodeInstance> instances = getInstancesByPackage(packageId);
        if (!instances.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException(
                    "Cannot update configuration with running instances. Stop " + 
                    instances.size() + " instance(s) first."));
        }
        
        // Get current package
        InstalledPackage currentPkg = installationRegistry.getPackage(packageId);
        if (currentPkg == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Package not found: " + packageId));
        }
        
        // Create updated package with new ProcessConfig
        InstalledPackage updatedPkg = new InstalledPackage(
            currentPkg.getPackageId(),
            currentPkg.getName(),
            currentPkg.getDescription(),
            currentPkg.getManifest(),
            newProcessConfig,  // NEW configuration
            currentPkg.getSecurityPolicy(),
            currentPkg.getRepository(),
            currentPkg.getInstalledDate(),
            currentPkg.getInstallPath()
        );
        
        // Update in registry (this will trigger save)
        return installationRegistry.registerPackage(updatedPkg)
            .thenRun(() -> {
                System.out.println("[NodeController] Configuration updated for: " + packageId);
                System.out.println("  New ProcessId: " + newProcessConfig.getProcessId());
            });
    }


    /**
     * Get installation registry (for RuntimeAccess)
     */
    public InstallationRegistry getInstallationRegistry() {
        return installationRegistry;
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
        
        // Unload all instances
        for (NodeInstance instance : instanceRegistry.getAllInstances()) {
            shutdownFutures.add(unloadInstance(instance.getInstanceId()));
        }
        
        // Shutdown installation registry
        if (installationRegistry != null) {
            shutdownFutures.add(installationRegistry.shutdown());
        }
        
        return CompletableFuture.allOf(
            shutdownFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                System.out.println("[NodeController] Shutdown complete");
            });
    }
}