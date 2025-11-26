package io.netnotes.engine.core;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import javax.crypto.SecretKey;

import io.netnotes.engine.core.nodes.INode;
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
 * Responsibilities:
 * - Manages NoteFileService (encrypted file registry)
 * - Manages INode registry (plugin/node instances)
 * - Provides scoped AppDataInterface for isolated access
 * - Coordinates password changes across all encrypted data
 * - Provides access to SettingsData for verification/operations
 * 
 * Key Design:
 * - Holds reference to SettingsData (for password operations)
 * - All encrypted file operations go through this interface
 * - Password changes update both SettingsData AND all NoteFiles
 */
public class AppData {

    private final NoteFileService m_noteFileRegistry;
    private final Map<NoteBytesReadOnly, INode> m_nodeRegistry = new ConcurrentHashMap<>();
    private final SettingsData m_settingsData;

    public AppData(SettingsData settingsData){
        if (settingsData == null) {
            throw new IllegalArgumentException("SettingsData cannot be null");
        }
        m_settingsData = settingsData;
        m_noteFileRegistry = new NoteFileService(settingsData);
    }

    /**
     * Get SettingsData (for password verification, etc.)
     */
    public SettingsData getSettingsData() {
        return m_settingsData;
    }

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

    public SecretKey getOldKey(){
        return getSettingsData().getOldKey();
    }

    public NoteBytes getOldSalt(){
        return getSettingsData().oldSalt();
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

    /**
     * Change master password
     * This coordinates updating:
     * 1. SettingsData (BCrypt hash and salt)
     * 2. All NoteFiles in the file path ledger
     * 
     * @param oldPassword Current password (for verification)
     * @param newPassword New password to set
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

    /**
     * Shutdown AppData
     * Closes all open NoteFiles and releases resources
     */
    public CompletableFuture<Void> shutdown(AsyncNoteBytesWriter progressWriter){
        if(progressWriter != null){
            ProgressMessage.writeAsync("AppData", 0, -1, "Closing any open files", progressWriter);
        }
        
        // Shutdown SettingsData first
        m_settingsData.shutdown();
        
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