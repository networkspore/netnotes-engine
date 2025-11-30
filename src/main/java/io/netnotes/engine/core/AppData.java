package io.netnotes.engine.core;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import io.netnotes.engine.core.system.control.nodes.INode;
import io.netnotes.engine.core.system.control.nodes.InstallationRegistry;
import io.netnotes.engine.core.system.control.nodes.NodeController;
import io.netnotes.engine.core.system.control.nodes.RepositoryManager;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.process.FlowProcessRegistry;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.notePath.NoteFileService;
import io.netnotes.engine.utils.VirtualExecutors;

/**
 * AppData - System-level resource manager
 * 
 * KEY ARCHITECTURAL CHANGES:
 * 1. Path structure unified via PathArchitecture
 * 2. AppDataInterface creation uses factory methods
 * 3. Sandboxing enforced at interface creation
 * 4. Node registry uses INSTANCE IDs (not package IDs)
 */
public class AppData {
    private final ContextPath myPath;
    private final NoteFileService m_noteFileRegistry;
    
    // Node registry: instanceId → INode
    // Uses NoteBytesReadOnly for IDs (more efficient than String, provides type info)
    // Changed from packageId to instanceId to support multiple instances
    private final Map<NoteBytesReadOnly, INode> m_nodeRegistry = new ConcurrentHashMap<>();
    
    // System Services (Always Available)
    private InstallationRegistry installationRegistry;
    private RepositoryManager repositoryManager;
    
    // Node system coordination
    private NodeController nodeController;

    public AppData(SettingsData settingsData){
        if (settingsData == null) {
            throw new IllegalArgumentException("SettingsData cannot be null");
        }
        this.myPath = ContextPath.of("system");
        m_noteFileRegistry = new NoteFileService(settingsData);
        
        initializeSystemServices();
        initializeNodeSystem(FlowProcessRegistry.getInstance());
    }

    private void initializeSystemServices() {
        // InstallationRegistry lives under me
        ContextPath registryPath = myPath.append("registry");
        AppDataInterface registryInterface = createScopedInterface(registryPath);
        this.installationRegistry = new InstallationRegistry(
            registryPath,
            registryInterface
        );
        
        // RepositoryManager lives under me
        ContextPath repoPath = myPath.append("repositories");
        AppDataInterface repoInterface = createScopedInterface(repoPath);
        this.repositoryManager = new RepositoryManager(
            repoPath,
            repoInterface
        );
    }

    /**
     * Initialize node system
     */
    public CompletableFuture<Void> initializeNodeSystem(
            FlowProcessRegistry processRegistry) {
        
        if (processRegistry == null) {
            throw new IllegalArgumentException("FlowProcessRegistry cannot be null");
        }
        
        System.out.println("[AppData] Initializing node system...");
        
        // Initialize system services first
        return installationRegistry.initialize()
            .thenCompose(v -> repositoryManager.initialize())
            .thenCompose(v -> {
                System.out.println("[AppData] System services initialized");
                
                // Create NodeController under /system/controller
                ContextPath controllerPath = myPath.append("controller");
                
                // NodeLoader needs interface to read package files
                ContextPath packagesPath = myPath.append("nodes", "packages");
                AppDataInterface loaderInterface = createScopedInterface(packagesPath);
                
                this.nodeController = new NodeController(
                    controllerPath,      // Where controller lives
                    this,                // For creating node interfaces
                    processRegistry,
                    installationRegistry
                );
                
                // Register in ProcessRegistry
                processRegistry.registerProcess(nodeController, controllerPath);
                
                // Start NodeController
                return nodeController.run();
            })
            .thenRun(() -> {
                System.out.println("[AppData] Node system initialized");
            });
    }

    
    // ===== PUBLIC API =====

    public ExecutorService getExecService(){
        return VirtualExecutors.getVirtualExecutor();
    }

    public ScheduledExecutorService getSchedualedExecService(){
        return VirtualExecutors.getVirtualSchedualedExecutor();
    }

    /**
     * Get node registry (by instance ID)
     * 
     * Uses NoteBytesReadOnly for IDs instead of String:
     * - More efficient (no String object overhead)
     * - Type information preserved
     * - Better interop with messaging system
     * 
     * CHANGED: Now uses instance IDs instead of package IDs
     * This allows multiple instances of the same package
     */
    public Map<NoteBytesReadOnly, INode> nodeRegistry(){
        return m_nodeRegistry;
    }

    public NoteFileService getNoteFileService(){
        return m_noteFileRegistry;
    }
    
    public NodeController getNodeController() {
        return nodeController;
    }
    
    public InstallationRegistry getInstallationRegistry() {
        return installationRegistry;
    }
    
    public RepositoryManager getRepositoryManager() {
        return repositoryManager;
    }

    // ===== INTERFACE =====
    /**
     * Create scoped interface for given paths
     * 
     * @param primaryPath Primary location (where interface "lives")
     * @param altPath Alternative location (optional, can be null)
     * @return Scoped interface
     */
    /**
     * Create scoped interface for given paths
     * No identity, just paths
     * 
     * @param primaryPath Primary location
     * @param altPath Alternative location (optional)
     * @return Scoped interface
     */
    public AppDataInterface createScopedInterface(
            ContextPath primaryPath,
            ContextPath altPath) {
        
        if (primaryPath == null) {
            throw new IllegalArgumentException("primaryPath cannot be null");
        }
        
        if (!primaryPath.isAbsolute()) {
            throw new IllegalArgumentException("primaryPath must be absolute");
        }
        
        if (altPath != null && !altPath.isAbsolute()) {
            throw new IllegalArgumentException("altPath must be absolute");
        }
        
        return new ScopedAppDataInterface(
            m_noteFileRegistry,
            primaryPath,
            altPath
        );
    }

    /**
     * Create scoped interface for single path
     */
    public AppDataInterface createScopedInterface(ContextPath path) {
        return createScopedInterface(path, null);
    }

    public ContextPath getPath() {
        return myPath;
    }

    // ===== PASSWORD OPERATIONS =====

    public CompletableFuture<Boolean> verifyPassword(
            io.netnotes.engine.noteBytes.NoteBytesEphemeral password) {
        return getSettingsData().verifyPassword(password);
    }

    public CompletableFuture<io.netnotes.engine.noteBytes.NoteBytesObject> changePassword(
            io.netnotes.engine.noteBytes.NoteBytesEphemeral oldPassword,
            io.netnotes.engine.noteBytes.NoteBytesEphemeral newPassword,
            int batchSize,
            AsyncNoteBytesWriter progressWriter) {
        
        if (oldPassword == null || newPassword == null) {
            CompletableFuture<io.netnotes.engine.noteBytes.NoteBytesObject> failed = 
                CompletableFuture.failedFuture(
                    new IllegalArgumentException("Passwords cannot be null"));
            if (progressWriter != null) {
                io.netnotes.engine.messaging.task.TaskMessages.writeErrorAsync(
                    "AppData", "Invalid password parameters", 
                    new IllegalArgumentException("Passwords cannot be null"), 
                    progressWriter);
            }
            return failed;
        }
        
        if (progressWriter != null) {
            io.netnotes.engine.messaging.task.ProgressMessage.writeAsync(
                "AppData", 0, -1, "Starting password change", progressWriter);
        }
        
        return m_noteFileRegistry.updateFilePathLedgerEncryption(
            progressWriter,
            oldPassword,
            newPassword,
            batchSize
        )
        .thenApply(result -> {
            if (progressWriter != null) {
                io.netnotes.engine.messaging.task.ProgressMessage.writeAsync(
                    "AppData", 0, -1, "Password change complete", progressWriter);
            }
            System.out.println("[AppData] Password change completed successfully");
            return result;
        })
        .exceptionally(ex -> {
            System.err.println("[AppData] Password change failed: " + ex.getMessage());
            if (progressWriter != null) {
                io.netnotes.engine.messaging.task.TaskMessages.writeErrorAsync(
                    "AppData", "Password change failed: " + ex.getMessage(), 
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
                if (!prevSuccess) return CompletableFuture.completedFuture(false);
                
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
                if (!prevSuccess) return CompletableFuture.completedFuture(false);
                
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
                if (!prevSuccess) return CompletableFuture.completedFuture(false);
                
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

    public CompletableFuture<Boolean> deleteCorruptedFiles(
            java.util.List<String> corruptedFiles) {
        if (corruptedFiles == null || corruptedFiles.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        
        return m_noteFileRegistry.deleteCorruptedFiles(corruptedFiles);
    }

    public CompletableFuture<Boolean> verifyOldPassword(
            io.netnotes.engine.noteBytes.NoteBytesEphemeral oldPassword) {
        return getSettingsData().verifyOldPassword(oldPassword);
    }

    public CompletableFuture<Void> rollbackSettingsData() {
        return CompletableFuture.runAsync(() -> {
            try {
                getSettingsData().rollbackToOldPassword();
                System.out.println("[AppData] SettingsData rolled back to old password");
            } catch (Exception e) {
                System.err.println("[AppData] Failed to rollback SettingsData: " + 
                    e.getMessage());
                throw new RuntimeException("SettingsData rollback failed", e);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }

    // ===== SHUTDOWN =====

    public CompletableFuture<Void> shutdown(){
        return shutdown(null);
    }

    public CompletableFuture<Void> shutdown(AsyncNoteBytesWriter progressWriter){
        if(progressWriter != null){
            io.netnotes.engine.messaging.task.ProgressMessage.writeAsync(
                "AppData", 0, -1, "Shutting down system services", progressWriter);
        }
        
        // Shutdown in reverse order of initialization
        
        // 1. Shutdown node system
        CompletableFuture<Void> nodeShutdown = nodeController != null 
            ? nodeController.shutdown()
            : CompletableFuture.completedFuture(null);
        
        return nodeShutdown.thenCompose(v -> {
            if(progressWriter != null){
                io.netnotes.engine.messaging.task.ProgressMessage.writeAsync(
                    "AppData", 0, -1, "Shutting down system services", progressWriter);
            }
            
            // 2. Shutdown system services
            return CompletableFuture.allOf(
                installationRegistry != null ? 
                    installationRegistry.shutdown() : CompletableFuture.completedFuture(null),
                repositoryManager != null ? 
                    repositoryManager.shutdown() : CompletableFuture.completedFuture(null)
            );
        })
        .thenCompose(v -> {
            if(progressWriter != null){
                io.netnotes.engine.messaging.task.ProgressMessage.writeAsync(
                    "AppData", 0, -1, "Closing encrypted file system", progressWriter);
            }
            
            // 3. Shutdown SettingsData
            getSettingsData().shutdown();
            
            // 4. Prepare all NoteFiles for shutdown
            return getNoteFileService().shutdown(progressWriter);
        })
        .exceptionally((ex)->{
            if(ex != null){
                Throwable cause = ex.getCause();
                String msg = "Error shutting down: " + 
                    (cause == null ? ex.getMessage() : 
                        ex.getMessage() + ": " + cause.toString());
                System.err.println(msg);
                ex.printStackTrace();
                if(progressWriter != null){
                    io.netnotes.engine.messaging.task.TaskMessages.writeErrorAsync(
                        "AppData", msg, ex, progressWriter);
                }
            }
            return null;
        });
    }
}