package io.netnotes.engine.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.crypto.SecretKey;

import io.netnotes.engine.core.system.control.nodes.INode;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.messaging.task.TaskMessages;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.noteFiles.notePath.NoteFileService;
import io.netnotes.engine.noteFiles.notePath.NoteFileService.FileEncryptionAnalysis;
import io.netnotes.engine.utils.VirtualExecutors;

/**
 * AppData - Primary application data interface
 * 
 * ARCHITECTURE PRINCIPLE: SettingsData Encapsulation
 * - SettingsData is NEVER exposed outside this class
 * - All password/key operations go through AppData methods
 * - External code interacts ONLY with AppData's public API
 * 
 * Responsibilities:
 * - Manages NoteFileService (encrypted file registry)
 * - Manages INode registry (plugin/node instances)
 * - Provides scoped AppDataInterface for isolated access
 * - Coordinates password changes across SettingsData AND NoteFiles
 * - Handles recovery operations (complete/rollback)
 * 
 * Key Design:
 * - Holds private reference to SettingsData (never exposed)
 * - All encrypted file operations delegate to NoteFileService
 * - Password changes coordinate BOTH SettingsData AND file updates
 * - Recovery operations are atomic and trackable
 */
public class AppData {

    private final NoteFileService m_noteFileRegistry;
    private final Map<NoteBytesReadOnly, INode> m_nodeRegistry = new ConcurrentHashMap<>();

    public AppData(SettingsData settingsData){
        if (settingsData == null) {
            throw new IllegalArgumentException("SettingsData cannot be null");
        }

        m_noteFileRegistry = new NoteFileService(settingsData);
    }

    // ===== PUBLIC API (NO SettingsData exposure) =====

    public ExecutorService getExecService(){
        return m_noteFileRegistry.getExecService();
    }

    public ScheduledExecutorService getSchedualedExecService(){
        return m_noteFileRegistry.getScheduledExecutor();
    }

    public Map<NoteBytesReadOnly, INode> nodeRegistry(){
        return m_nodeRegistry;
    }

    public NoteFileService getNoteFileService(){
        return m_noteFileRegistry;
    }

    /**
     * Get scoped interface for a specific ID
     * This provides isolated access to the file system with automatic path scoping
     */
    public AppDataInterface getAppDataInterface(NoteBytesReadOnly id){
        return new AppDataInterface(){
            final NoteBytesReadOnly startingPath = new NoteBytesReadOnly(id.get(), NoteBytesMetaData.STRING_TYPE);
            
            @Override
            public void shutdown() {
                AppData.this.shutdown(null);
            }

            @Override
            public CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path) {
                if(path == null){
                    return CompletableFuture.failedFuture(new NullPointerException("Path is null"));
                }
                return CompletableFuture.supplyAsync(()->{
                    NoteBytesReadOnly[] originalPath = path.getAsArray();

                    boolean isStartingPath = originalPath.length > 0 ? originalPath[0].equals(startingPath) : false;

                    if(isStartingPath){
                        NoteBytesReadOnly[] copiedPath = new NoteBytesReadOnly[originalPath.length];
                        for(int i = 0; i < originalPath.length; i++){
                            copiedPath[i] = new NoteBytesReadOnly(originalPath[i].get(), NoteBytesMetaData.STRING_TYPE);
                        }
                        return new NoteStringArrayReadOnly(copiedPath);
                    
                    }else{
                        NoteBytesReadOnly[] copiedPath = new NoteBytesReadOnly[originalPath.length + 1];

                        copiedPath[0] = startingPath;
                        for(int i = 0; i < originalPath.length; i++){
                            copiedPath[i + 1] = new NoteBytesReadOnly(originalPath[i].get(), NoteBytesMetaData.STRING_TYPE);
                        }
                        return new NoteStringArrayReadOnly(copiedPath);
                    
                    }
                }, VirtualExecutors.getVirtualExecutor())
                    .thenCompose(scopedPath->AppData.this.m_noteFileRegistry.getNoteFile(scopedPath));
            }
        };
    }

    // ===== PASSWORD OPERATIONS (no SettingsData exposure) =====

    /**
     * Verify password
     * External code should use this instead of accessing SettingsData
     */
    public CompletableFuture<Boolean> verifyPassword(NoteBytesEphemeral password) {
        return getSettingsData().verifyPassword(password);
    }

    /**
     * Change master password
     * 
     * This is the PRIMARY password change operation.
     * Coordinates updating:
     * 1. SettingsData (BCrypt hash, salt, and keys)
     * 2. All NoteFiles in the file path ledger
     * 
     * @param oldPassword Current password (for verification)
     * @param newPassword New password to set
     * @param batchSize Concurrent batch size for file re-encryption
     * @param progressWriter Optional progress writer for UI feedback
     * @return Result of the password change operation
     */
    public CompletableFuture<NoteBytesObject> changePassword(
            NoteBytesEphemeral oldPassword,
            NoteBytesEphemeral newPassword,
            int batchSize,
            AsyncNoteBytesWriter progressWriter) {
        
        if (oldPassword == null || newPassword == null) {
            CompletableFuture<NoteBytesObject> failed = CompletableFuture.failedFuture(
                new IllegalArgumentException("Passwords cannot be null"));
            if (progressWriter != null) {
                TaskMessages.writeErrorAsync("AppData", "Invalid password parameters", 
                    new IllegalArgumentException("Passwords cannot be null"), progressWriter);
            }
            return failed;
        }
        
        if (progressWriter != null) {
            ProgressMessage.writeAsync("AppData", 0, -1, "Starting password change", progressWriter);
        }
        
        // Delegate to NoteFileService which coordinates with SettingsData
        return m_noteFileRegistry.updateFilePathLedgerEncryption(
            progressWriter,
            oldPassword,
            newPassword,
            batchSize
        )
        .thenApply(result -> {
            if (progressWriter != null) {
                ProgressMessage.writeAsync("AppData", 0, -1, "Password change complete", progressWriter);
            }
            System.out.println("[AppData] Password change completed successfully");
            return result;
        })
        .exceptionally(ex -> {
            System.err.println("[AppData] Password change failed: " + ex.getMessage());
            if (progressWriter != null) {
                TaskMessages.writeErrorAsync("AppData", "Password change failed: " + ex.getMessage(), 
                    ex, progressWriter);
            }
            throw new RuntimeException("Password change failed", ex);
        });
    }

    // ===== RECOVERY OPERATIONS =====

    /**
     * Check if old key is available for recovery
     * This is true if:
     * - System hasn't restarted since password change
     * - SettingsData still has old key/salt in memory
     */
    public boolean hasOldKeyForRecovery() {
        return  getSettingsData().hasOldKey();
    }

    public void clearOldKey() {
        getSettingsData().clearOldKey();
    }


    private SettingsData getSettingsData(){
        return m_noteFileRegistry.getSettingsData();
    }

    public CompletableFuture<Boolean> performRecovery(FileEncryptionAnalysis analysis,AsyncNoteBytesWriter progressWriter,
        int batchSize
    ) {
        List<String> filesNeedingUpdate = analysis.getFilesNeedingUpdate();

        SecretKey oldKey = getSettingsData().getOldKey();
        SecretKey currentKey = getSettingsData().getSecretKey();

        return m_noteFileRegistry.reEncryptFiles(
            filesNeedingUpdate,
            oldKey,
            currentKey,
            "RECOVERY",
            batchSize,
            progressWriter
        );
    
    }

    public CompletableFuture<Boolean> performTempFileCleanup(FileEncryptionAnalysis analysis){
        return m_noteFileRegistry.cleanupFiles(analysis.getFilesNeedingCleanup());
    }

    public CompletableFuture<Boolean> performSwap(FileEncryptionAnalysis analysis, AsyncNoteBytesWriter progressWriter
    ) {
        return m_noteFileRegistry.performFinishSwaps( analysis.getFilesNeedingSwap());
    }


    /**
     * Perform rollback re-encryption
     * Re-encrypts files that were updated back to old key
     * 
     * Used when user chooses to revert to old password
     */
    public CompletableFuture<Boolean> performRollback(
            FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter,
            int batchSize) {
        
        // After rollbackSettingsData(), keys are swapped:
        // - currentKey is now the OLD password's key
        // - oldKey is now the NEW password's key (that we want to revert from)
        
        List<String> filesToRevert = analysis.getFilesNeedingUpdate();
        
        if (filesToRevert.isEmpty()) {
            System.out.println("[AppData] No files to revert");
            return CompletableFuture.completedFuture(true);
        }
        
        SecretKey fromKey = getSettingsData().getOldKey(); // Actually the NEW key now
        SecretKey toKey = getSettingsData().getSecretKey(); // Actually the OLD key now
        
        return m_noteFileRegistry.reEncryptFiles(
            filesToRevert,
            fromKey,
            toKey,
            "ROLLBACK",
            batchSize,
            progressWriter
        );
    }

    /**
     * Perform comprehensive recovery (all actions)
     * 
     * Executes recovery operations in optimal order with resource monitoring:
     * 1. Complete re-encryption (adaptive batching)
     * 2. Finish file swaps
     * 3. Clean up temporary files
     * 
     * Tracks progress and updates analysis as operations complete
     */
    public CompletableFuture<Boolean> performComprehensiveRecovery(
            FileEncryptionAnalysis analysis,
            AsyncNoteBytesWriter progressWriter,
            int initialBatchSize) {
        
        System.out.println("[AppData] Starting comprehensive recovery");
        
        // Track which operations succeeded
        CompletableFuture<Boolean> recoveryChain = CompletableFuture.completedFuture(true);
        
        // Step 1: Complete re-encryption (if needed)
        if (!analysis.getFilesNeedingUpdate().isEmpty()) {
            recoveryChain = recoveryChain.thenCompose(prevSuccess -> {
                if (!prevSuccess) {
                    return CompletableFuture.completedFuture(false);
                }
                
                ProgressMessage.writeAsync("ComprehensiveRecovery", 0, 3,
                    "Step 1/3: Re-encrypting files", progressWriter);
                
                return performRecovery(analysis, progressWriter, initialBatchSize)
                    .thenApply(success -> {
                        if (success) {
                            // Mark files as completed in analysis
                            analysis.markFilesCompleted(analysis.getFilesNeedingUpdate());
                        }
                        return success;
                    });
            });
        }
        
        // Step 2: Finish file swaps (if needed)
        if (!analysis.getFilesNeedingSwap().isEmpty()) {
            recoveryChain = recoveryChain.thenCompose(prevSuccess -> {
                if (!prevSuccess) {
                    return CompletableFuture.completedFuture(false);
                }
                
                ProgressMessage.writeAsync("ComprehensiveRecovery", 1, 3,
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
        
        // Step 3: Clean up temporary files (if needed)
        if (!analysis.getFilesNeedingCleanup().isEmpty()) {
            recoveryChain = recoveryChain.thenCompose(prevSuccess -> {
                if (!prevSuccess) {
                    return CompletableFuture.completedFuture(false);
                }
                
                ProgressMessage.writeAsync("ComprehensiveRecovery", 2, 3,
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

    /**
     * Delete corrupted files from disk and ledger
     */
    public CompletableFuture<Boolean> deleteCorruptedFiles(List<String> corruptedFiles) {
        if (corruptedFiles == null || corruptedFiles.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        
        return m_noteFileRegistry.deleteCorruptedFiles(corruptedFiles);
    }

    
    /**
     * Verify old password (for recovery operations)
     * Used when system was restarted and old key needs to be derived
     */
    public CompletableFuture<Boolean> verifyOldPassword(NoteBytesEphemeral oldPassword) {
        return  getSettingsData().verifyOldPassword(oldPassword);
    }

    // ===== INTERNAL HELPERS =====

    /**
     * swaps current key with old key, reverses recovery direction
     */
    public CompletableFuture<Void> rollbackSettingsData() {
        return CompletableFuture.runAsync(() -> {
            try {
                getSettingsData().rollbackToOldPassword();
                System.out.println("[AppData] SettingsData rolled back to old password");
            } catch (Exception e) {
                System.err.println("[AppData] Failed to rollback SettingsData: " + e.getMessage());
                throw new RuntimeException("SettingsData rollback failed", e);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }


    /**
     * Shutdown AppData
     * Closes all open NoteFiles and releases resources
     */
    public CompletableFuture<Void> shutdown(AsyncNoteBytesWriter progressWriter){
        if(progressWriter != null){
            ProgressMessage.writeAsync("AppData", 0, -1, "Closing any open files", progressWriter);
        }
        
        // Shutdown SettingsData first
        getSettingsData().shutdown();
        
        // Then prepare all NoteFiles for shutdown
        return getNoteFileService().prepareAllForShutdown().exceptionally((ex)->{
                if(ex != null){
                    Throwable cause = ex.getCause();
                    String msg = "Error shutting down note file service: " + 
                        (cause == null ? ex.getMessage() : ex.getMessage() + ": " + cause.toString());
                    System.err.println(msg);
                    ex.printStackTrace();
                    if(progressWriter != null){
                        TaskMessages.writeErrorAsync("AppData", msg, ex, progressWriter);
                    }
                }
                return null;
            }).thenApply((v)->null);
    }
}