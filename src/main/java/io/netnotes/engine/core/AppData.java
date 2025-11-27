package io.netnotes.engine.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.crypto.SecretKey;

import io.netnotes.engine.core.nodes.INode;
import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.messaging.task.TaskMessages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.noteFiles.notePath.NoteFileService;
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

    private SettingsData getSettingsData(){
        return m_noteFileRegistry.getSettingsData();
    }

    /**
     * Complete interrupted password change (recovery option 1)
     * 
     * Re-encrypts remaining files from OLD key to NEW key.
     * Used when password change was interrupted mid-operation.
     * 
     * Requirements:
     * - Old key must be available (hasOldKeyForRecovery() returns true)
     * - New password is already active in SettingsData
     * 
     * @param filePaths Files that need re-encryption
     * @param oldPassword OLD password (if old key not in memory)
     * @param batchSize Concurrent batch size
     * @param progressWriter Progress tracking
     * @return Success/failure result
     */
    public CompletableFuture<Boolean> completePasswordChange(
        List<String> filePaths,
        NoteBytesEphemeral oldPassword,
        int batchSize,
        AsyncNoteBytesWriter progressWriter) 
    {
        
        if (progressWriter != null) {
            ProgressMessage.writeAsync("AppData", 0, -1, 
                "Starting recovery: completing password change", progressWriter);
        }
        
        // Get old key (from memory or derive from password)
        return getOldKey(oldPassword)
            .thenCompose(oldKey -> {
            
          
                
                // EXPLICIT: Complete password change
                return m_noteFileRegistry.completePasswordChangeForFiles(
                    filePaths,
                    oldKey,      // Only pass OLD key - method gets NEW key itself
                    batchSize,
                    progressWriter
                );
            })
            .thenApply(success -> {
                if (success) {
                    getSettingsData().clearOldKey();
                    
                    if (progressWriter != null) {
                        ProgressMessage.writeAsync("AppData", 0, -1, 
                            "Recovery complete - all files updated", progressWriter);
                    }
                }
                return success;
            });
    }

    /**
     * Rollback to old password (recovery option 2)
     * 
     * This reverts BOTH:
     * 1. SettingsData to old password/key
     * 2. Successfully updated files back to old encryption
     * 
     * Requirements:
     * - Old key must be available (hasOldKeyForRecovery() returns true)
     * - User provides OLD password for verification
     * 
     * @param successfulFiles Files that were successfully updated (need rollback)
     * @param oldPassword OLD password (for verification and rollback)
     * @param batchSize Concurrent batch size
     * @param progressWriter Progress tracking
     * @return Success/failure result
     */
    public CompletableFuture<Boolean> rollbackPasswordChange(
        List<String> successfulFiles,
        NoteBytesEphemeral oldPassword,
        int batchSize,
        AsyncNoteBytesWriter progressWriter
    ) {
        
        if (progressWriter != null) {
            ProgressMessage.writeAsync("AppData", 0, -1, 
                "Starting rollback to old password", progressWriter);
        }
        
        // Step 1: Verify old password
        return getSettingsData().verifyOldPassword(oldPassword)
            .thenCompose(valid -> {
                if (!valid) {
                    throw new IllegalArgumentException("Invalid old password");
                }
                
                if (progressWriter != null) {
                    ProgressMessage.writeAsync("AppData", 0, -1, 
                        "Old password verified, rolling back SettingsData", progressWriter);
                }
                
                // Step 2: Rollback SettingsData (swap keys back)
                return rollbackSettingsData();
            })
            .thenCompose(v -> {
                if (progressWriter != null) {
                    ProgressMessage.writeAsync("AppData", 0, -1, 
                        "SettingsData rolled back, re-encrypting files", progressWriter);
                }
                
                // Step 3: Get old key for rollback
                return getOldKey(oldPassword);
            })
            .thenCompose(oldKey -> {
              
                
                if (oldKey == null) {
                    throw new IllegalStateException("Old key not available for rollback");
                }
                
                // EXPLICIT: Rollback files
                return m_noteFileRegistry.rollbackPasswordChangeForFiles(
                    successfulFiles,
                    oldKey,      // Only pass OLD key - method gets current key itself
                    batchSize,
                    progressWriter
                );
            })
            .thenApply(success -> {
                if (success) {
                    getSettingsData().clearOldKey();
                    
                    if (progressWriter != null) {
                        ProgressMessage.writeAsync("AppData", 0, -1, 
                            "Rollback complete - system restored to old password", 
                            progressWriter);
                    }
                }
                return success;
            });
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
     * Rollback SettingsData to old password state
     * This is an internal operation coordinated by AppData
     */
    private CompletableFuture<Void> rollbackSettingsData() {
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
     * Get recovery keys for file operations
     * Returns both old and new keys for re-encryption operations
     */
    private CompletableFuture<SecretKey> getOldKey(NoteBytesEphemeral oldPassword) {
        return CompletableFuture.supplyAsync(() -> {
     
            // Old key - check if in memory
            SecretKey oldKey = getSettingsData().getOldKey();
            
            // If old key not in memory and password provided, derive it
            if (oldKey == null && oldPassword != null) {
                NoteBytes oldSalt = getSettingsData().oldSalt();
                if (oldSalt != null) {
                    try {
                        oldKey = CryptoService.createKey(
                            oldPassword, oldSalt);
                        System.out.println("[AppData] Old key derived from password");
                    } catch (Exception e) {
                        System.err.println("[AppData] Failed to derive old key: " + e.getMessage());
                        throw new CompletionException("cannot complete password change", new IllegalStateException(
                           "Old key not available"));
                    }
                }
            }
            
            return oldKey;
            
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