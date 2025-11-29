package io.netnotes.engine.core;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import io.netnotes.engine.core.system.control.nodes.INode;
import io.netnotes.engine.core.system.control.nodes.NodeController;
import io.netnotes.engine.core.system.control.nodes.NodePaths;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.process.FlowProcessRegistry;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.notePath.NoteFileService;
import io.netnotes.engine.utils.VirtualExecutors;

/**
 * AppData - Enhanced with proper node system integration
 * 
 * ARCHITECTURE CLARIFICATION:
 * 
 * AppData is the CENTRAL HUB that:
 * 1. Manages SettingsData (passwords, encryption keys)
 * 2. Manages NoteFileService (encrypted file system)
 * 3. Coordinates NodeController (runtime node lifecycle)
 * 4. Provides sandboxed AppDataInterface to nodes
 * 
 * SEPARATION OF CONCERNS:
 * 
 * - AppData: System-wide coordination and resource management
 *   - Holds SettingsData (never exposed)
 *   - Holds NoteFileService (file system)
 *   - Holds NodeController (node lifecycle)
 *   - Creates sandboxed interfaces for nodes
 * 
 * - NodeController: Runtime node lifecycle (like systemd)
 *   - Load/unload nodes
 *   - Monitor health
 *   - Inter-node routing
 *   - Enforces runtime policies
 * 
 * - NodeManagerProcess: Package management UI (like apt-get)
 *   - Browse repositories
 *   - Install/uninstall packages
 *   - Update package lists
 *   - REQUEST AppData to load/unload nodes (doesn't do it directly)
 * 
 * - InstallationRegistry: Installation metadata (what's installed)
 *   - Stored in NoteFiles via NoteFileService
 *   - Managed by NodeManagerProcess
 *   - Read by NodeController when loading
 * 
 * DATA FLOW EXAMPLES:
 * 
 * INSTALL A PACKAGE:
 * User → NodeManagerProcess → Download files → NoteFileService.save()
 *      → InstallationRegistry.register() → Done
 * 
 * LOAD A NODE:
 * User → NodeManagerProcess → Request to AppData
 *      → AppData.loadNode() → NodeController.loadNode()
 *      → NodeLoader reads from NoteFileService
 *      → Node initialized with sandboxed AppDataInterface
 *      → Done
 * 
 * NODE ACCESSES FILE:
 * Node → AppDataInterface.getNoteFile(path)
 *      → Validate path via NodePaths.canAccess()
 *      → If allowed: NoteFileService.getNoteFile(validatedPath)
 *      → Return NoteFile to node
 */
public class AppData {

    private final NoteFileService m_noteFileRegistry;
    private final Map<NoteBytesReadOnly, INode> m_nodeRegistry = new ConcurrentHashMap<>();
    
    // Node system coordination
    private NodeController nodeController;
    private FlowProcessRegistry processRegistry;

    public AppData(SettingsData settingsData){
        if (settingsData == null) {
            throw new IllegalArgumentException("SettingsData cannot be null");
        }

        m_noteFileRegistry = new NoteFileService(settingsData);
    }

    // ===== INITIALIZATION =====
    
    /**
     * Initialize AppData with node system support
     * Call this after constructing AppData
     */
    public CompletableFuture<Void> initializeNodeSystem(FlowProcessRegistry processRegistry) {
        this.processRegistry = processRegistry;
        this.nodeController = new NodeController(this, processRegistry);
        
        // Register NodeController in the ProcessRegistry
        ContextPath controllerPath = ContextPath.of("system", "controller");
        processRegistry.registerProcess(nodeController, controllerPath);
        
        // Start NodeController (loads auto-start nodes)
        return nodeController.run()
            .thenRun(() -> {
                System.out.println("[AppData] Node system initialized");
            });
    }

    // ===== PUBLIC API (NO SettingsData exposure) =====

    public ExecutorService getExecService(){
        return VirtualExecutors.getVirtualExecutor();
    }

    public ScheduledExecutorService getSchedualedExecService(){
        return VirtualExecutors.getVirtualSchedualedExecutor();
    }

    public Map<NoteBytesReadOnly, INode> nodeRegistry(){
        return m_nodeRegistry;
    }

    public NoteFileService getNoteFileService(){
        return m_noteFileRegistry;
    }
    
    /**
     * Get the NodeController
     * Used by SystemSessionProcess to coordinate with node system
     */
    public NodeController getNodeController() {
        return nodeController;
    }

    // ===== SANDBOXED INTERFACES =====
    
    /**
     * Get scoped interface for a specific path
     * This provides isolated access to the file system with automatic path scoping
     * 
     * @deprecated Use getNodeInterface() for nodes, or getSystemInterface() for system code
     */
    @Deprecated
    public AppDataInterface getAppDataInterface(String... path){
        return getAppDataInterface(ContextPath.of(path));
    }
    
    /**
     * @deprecated Use getNodeInterface() for nodes, or getSystemInterface() for system code
     */
    @Deprecated
    public AppDataInterface getAppDataInterface(ContextPath scopedPath){
        if(scopedPath == null){
            throw new IllegalArgumentException("scopedPath cannot be null");
        }
        return new AppDataInterface(){

            @Override
            public void shutdown() {
                AppData.this.shutdown(null);
            }

            @Override
            public CompletableFuture<io.netnotes.engine.noteFiles.NoteFile> getNoteFile(ContextPath path) {
                if(path == null ){
                    return CompletableFuture.failedFuture(new NullPointerException("Path is null"));
                }
                return CompletableFuture.supplyAsync(()->{
                    if(path.startsWith(scopedPath)){
            
                        return path.getSegments();
                    
                    }else{
                        return scopedPath.append(path).getSegments();
                    }
                }, io.netnotes.engine.utils.VirtualExecutors.getVirtualExecutor())
                    .thenCompose(scopedPath->AppData.this.m_noteFileRegistry.getNoteFile(scopedPath));
            }
        };
    }
    
    /**
     * Create a sandboxed AppDataInterface for a node
     * 
     * This enforces access control rules defined in NodePaths:
     * - Nodes can access /system/nodes/runtime/{packageId}/
     * - Nodes can access /user/nodes/{packageId}/
     * - Nodes can READ (not write) /system/nodes/packages/
     * - Nodes CANNOT access other nodes' paths
     * 
     * @param packageId The node's package identifier
     * @return Sandboxed interface with enforced access control
     */
    public AppDataInterface getNodeInterface(String packageId) {
        if (packageId == null || packageId.isEmpty()) {
            throw new IllegalArgumentException("packageId cannot be null or empty");
        }
        
        NodePaths.NodeSandbox sandbox = new NodePaths.NodeSandbox(packageId);
        
        return new AppDataInterface() {
            
            @Override
            public void shutdown() {
                // Nodes cannot shutdown the entire system
                throw new UnsupportedOperationException(
                    "Nodes cannot shutdown AppData - use node.shutdown() instead");
            }
            
            @Override
            public CompletableFuture<io.netnotes.engine.noteFiles.NoteFile> getNoteFile(ContextPath path) {
                if (path == null) {
                    return CompletableFuture.failedFuture(
                        new NullPointerException("Path is null"));
                }
                
                return CompletableFuture.supplyAsync(() -> {
                    // Validate access (assuming read for now, write will be checked by NoteFile)
                    ContextPath validatedPath = sandbox.validateAndResolve(path, false);
                    
                    if (validatedPath == null) {
                        throw new SecurityException(
                            "Node '" + packageId + "' cannot access path: " + path);
                    }
                    
                    return validatedPath.getSegments();
                    
                }, io.netnotes.engine.utils.VirtualExecutors.getVirtualExecutor())
                .thenCompose(segments -> m_noteFileRegistry.getNoteFile(segments));
            }
        };
    }
    
    /**
     * Create an AppDataInterface for system code (not sandboxed)
     * Used by NodeManagerProcess, InstallationRegistry, etc.
     * 
     * @param systemArea System area identifier (e.g., "node-manager", "installation-registry")
     * @return Interface with access to system paths
     */
    public AppDataInterface getSystemInterface(String systemArea) {
        if (systemArea == null || systemArea.isEmpty()) {
            throw new IllegalArgumentException("systemArea cannot be null or empty");
        }
        
        return new AppDataInterface() {
            
            @Override
            public void shutdown() {
                AppData.this.shutdown(null);
            }
            
            @Override
            public CompletableFuture<io.netnotes.engine.noteFiles.NoteFile> getNoteFile(ContextPath path) {
                if (path == null) {
                    return CompletableFuture.failedFuture(
                        new NullPointerException("Path is null"));
                }
                
                // System code has unrestricted access
                return m_noteFileRegistry.getNoteFile(path.getSegments());
            }
        };
    }

    // ===== PASSWORD OPERATIONS (no SettingsData exposure) =====

    public CompletableFuture<Boolean> verifyPassword(io.netnotes.engine.noteBytes.NoteBytesEphemeral password) {
        return getSettingsData().verifyPassword(password);
    }

    public CompletableFuture<io.netnotes.engine.noteBytes.NoteBytesObject> changePassword(
            io.netnotes.engine.noteBytes.NoteBytesEphemeral oldPassword,
            io.netnotes.engine.noteBytes.NoteBytesEphemeral newPassword,
            int batchSize,
            AsyncNoteBytesWriter progressWriter) {
        
        if (oldPassword == null || newPassword == null) {
            CompletableFuture<io.netnotes.engine.noteBytes.NoteBytesObject> failed = CompletableFuture.failedFuture(
                new IllegalArgumentException("Passwords cannot be null"));
            if (progressWriter != null) {
                io.netnotes.engine.messaging.task.TaskMessages.writeErrorAsync("AppData", "Invalid password parameters", 
                    new IllegalArgumentException("Passwords cannot be null"), progressWriter);
            }
            return failed;
        }
        
        if (progressWriter != null) {
            io.netnotes.engine.messaging.task.ProgressMessage.writeAsync("AppData", 0, -1, "Starting password change", progressWriter);
        }
        
        return m_noteFileRegistry.updateFilePathLedgerEncryption(
            progressWriter,
            oldPassword,
            newPassword,
            batchSize
        )
        .thenApply(result -> {
            if (progressWriter != null) {
                io.netnotes.engine.messaging.task.ProgressMessage.writeAsync("AppData", 0, -1, "Password change complete", progressWriter);
            }
            System.out.println("[AppData] Password change completed successfully");
            return result;
        })
        .exceptionally(ex -> {
            System.err.println("[AppData] Password change failed: " + ex.getMessage());
            if (progressWriter != null) {
                io.netnotes.engine.messaging.task.TaskMessages.writeErrorAsync("AppData", "Password change failed: " + ex.getMessage(), 
                    ex, progressWriter);
            }
            throw new RuntimeException("Password change failed", ex);
        });
    }

    // ===== RECOVERY OPERATIONS =====

    public boolean hasOldKeyForRecovery() {
        return getSettingsData().hasOldKey();
    }

    public void clearOldKey() {
        getSettingsData().clearOldKey();
    }

    private SettingsData getSettingsData(){
        return m_noteFileRegistry.getSettingsData();
    }

    public CompletableFuture<Boolean> performRecovery(
            NoteFileService.FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter,
            int batchSize) {
        
        java.util.List<String> filesNeedingUpdate = analysis.getFilesNeedingUpdate();

        javax.crypto.SecretKey oldKey = getSettingsData().getOldKey();
        javax.crypto.SecretKey currentKey = getSettingsData().getSecretKey();

        return m_noteFileRegistry.reEncryptFiles(
            filesNeedingUpdate,
            oldKey,
            currentKey,
            "RECOVERY",
            batchSize,
            progressWriter
        );
    }

    public CompletableFuture<Boolean> performTempFileCleanup(
            NoteFileService.FileEncryptionAnalysis analysis){
        return m_noteFileRegistry.cleanupFiles(analysis.getFilesNeedingCleanup());
    }

    public CompletableFuture<Boolean> performSwap(
            NoteFileService.FileEncryptionAnalysis analysis, 
            AsyncNoteBytesWriter progressWriter) {
        return m_noteFileRegistry.performFinishSwaps(analysis.getFilesNeedingSwap());
    }

    public CompletableFuture<Boolean> performRollback(
            NoteFileService.FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter,
            int batchSize) {
        
        java.util.List<String> filesToRevert = analysis.getFilesNeedingUpdate();
        
        if (filesToRevert.isEmpty()) {
            System.out.println("[AppData] No files to revert");
            return CompletableFuture.completedFuture(true);
        }
        
        javax.crypto.SecretKey fromKey = getSettingsData().getOldKey();
        javax.crypto.SecretKey toKey = getSettingsData().getSecretKey();
        
        return m_noteFileRegistry.reEncryptFiles(
            filesToRevert,
            fromKey,
            toKey,
            "ROLLBACK",
            batchSize,
            progressWriter
        );
    }

    public CompletableFuture<Boolean> performComprehensiveRecovery(
            NoteFileService.FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter,
            int initialBatchSize) {
        
        System.out.println("[AppData] Starting comprehensive recovery");
        
        CompletableFuture<Boolean> recoveryChain = CompletableFuture.completedFuture(true);
        
        if (!analysis.getFilesNeedingUpdate().isEmpty()) {
            recoveryChain = recoveryChain.thenCompose(prevSuccess -> {
                if (!prevSuccess) {
                    return CompletableFuture.completedFuture(false);
                }
                
                io.netnotes.engine.messaging.task.ProgressMessage.writeAsync(
                    "ComprehensiveRecovery", 0, 3,
                    "Step 1/3: Re-encrypting files", progressWriter);
                
                return performRecovery(analysis, progressWriter, initialBatchSize)
                    .thenApply(success -> {
                        if (success) {
                            analysis.markFilesCompleted(analysis.getFilesNeedingUpdate());
                        }
                        return success;
                    });
            });
        }
        
        if (!analysis.getFilesNeedingSwap().isEmpty()) {
            recoveryChain = recoveryChain.thenCompose(prevSuccess -> {
                if (!prevSuccess) {
                    return CompletableFuture.completedFuture(false);
                }
                
                io.netnotes.engine.messaging.task.ProgressMessage.writeAsync(
                    "ComprehensiveRecovery", 1, 3,
                    "Step 2/3: Finishing file swaps", progressWriter);
                
                return performSwap(analysis, progressWriter)
                    .thenApply(success -> {
                        if (success) {
                            analysis.markFilesCompleted(analysis.getFilesNeedingSwap());
                        }
                        return success;
                    });
            });
        }
        
        if (!analysis.getFilesNeedingCleanup().isEmpty()) {
            recoveryChain = recoveryChain.thenCompose(prevSuccess -> {
                if (!prevSuccess) {
                    return CompletableFuture.completedFuture(false);
                }
                
                io.netnotes.engine.messaging.task.ProgressMessage.writeAsync(
                    "ComprehensiveRecovery", 2, 3,
                    "Step 3/3: Cleaning up temporary files", progressWriter);
                
                return performTempFileCleanup(analysis)
                    .thenApply(success -> {
                        if (success) {
                            analysis.markFilesCompleted(analysis.getFilesNeedingCleanup());
                        }
                        return success;
                    });
            });
        }
        
        return recoveryChain.thenApply(finalSuccess -> {
            System.out.println("[AppData] Comprehensive recovery " + 
                (finalSuccess ? "succeeded" : "completed with errors"));
            return finalSuccess;
        });
    }

    public CompletableFuture<Boolean> deleteCorruptedFiles(java.util.List<String> corruptedFiles) {
        if (corruptedFiles == null || corruptedFiles.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        
        return m_noteFileRegistry.deleteCorruptedFiles(corruptedFiles);
    }

    public CompletableFuture<Boolean> verifyOldPassword(io.netnotes.engine.noteBytes.NoteBytesEphemeral oldPassword) {
        return getSettingsData().verifyOldPassword(oldPassword);
    }

    public CompletableFuture<Void> rollbackSettingsData() {
        return CompletableFuture.runAsync(() -> {
            try {
                getSettingsData().rollbackToOldPassword();
                System.out.println("[AppData] SettingsData rolled back to old password");
            } catch (Exception e) {
                System.err.println("[AppData] Failed to rollback SettingsData: " + e.getMessage());
                throw new RuntimeException("SettingsData rollback failed", e);
            }
        }, io.netnotes.engine.utils.VirtualExecutors.getVirtualExecutor());
    }

    // ===== SHUTDOWN =====

    public CompletableFuture<Void> shutdown(AsyncNoteBytesWriter progressWriter){
        if(progressWriter != null){
            io.netnotes.engine.messaging.task.ProgressMessage.writeAsync(
                "AppData", 0, -1, "Shutting down node system", progressWriter);
        }
        
        // Shutdown node system first
        CompletableFuture<Void> nodeShutdown = nodeController != null 
            ? nodeController.shutdown()
            : CompletableFuture.completedFuture(null);
        
        return nodeShutdown.thenCompose(v -> {
            if(progressWriter != null){
                ProgressMessage.writeAsync(
                    "AppData", 0, -1, "Closing any open files", progressWriter);
            }
            
            // Shutdown SettingsData
            getSettingsData().shutdown();
            
            // Prepare all NoteFiles for shutdown
            return getNoteFileService().shutdown();
        })
        .exceptionally((ex)->{
            if(ex != null){
                Throwable cause = ex.getCause();
                String msg = "Error shutting down: " + 
                    (cause == null ? ex.getMessage() : ex.getMessage() + ": " + cause.toString());
                System.err.println(msg);
                ex.printStackTrace();
                if(progressWriter != null){
                    io.netnotes.engine.messaging.task.TaskMessages.writeErrorAsync(
                        "AppData", msg, ex, progressWriter);
                }
            }
            return null;
        })
        .thenApply((v)->null);
    }
}