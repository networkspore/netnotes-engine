package io.netnotes.engine.noteFiles.notePath;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.messaging.task.TaskMessages;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteFiles.DiskSpaceValidation;
import io.netnotes.engine.noteFiles.FileStreamUtils;
import io.netnotes.engine.noteFiles.ManagedNoteFileInterface;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.utils.VirtualExecutors;
import io.netnotes.engine.utils.streams.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

/**
 * NoteFileService - Encrypted file registry and operations
 * 
 * REFACTORED ARCHITECTURE:
 * - Works with SettingsData through NotePathFactory
 * - Provides recovery operations for AppData
 * - Does NOT expose SettingsData to external callers
 * - Coordinates file operations with password/key management
 */
public class NoteFileService extends NotePathFactory {
    private final Map<NoteStringArrayReadOnly, ManagedNoteFileInterface> m_registry = new ConcurrentHashMap<>();

    public NoteFileService(SettingsData settingsData) {
        super(settingsData);
    }
    
    // ===== STANDARD FILE OPERATIONS =====
    
    public CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly notePath) {
        ManagedNoteFileInterface existing = m_registry.get(notePath);
        if (existing != null) {
            return CompletableFuture.completedFuture(new NoteFile(notePath, existing));
        }

        return acquireLock()
            .thenCompose(filePathLedger ->super.getNoteFilePath(filePathLedger, notePath))
            .thenApply(filePath -> {
                ManagedNoteFileInterface noteFileInterface = m_registry.computeIfAbsent(notePath,
                    k -> new ManagedNoteFileInterface(k, filePath, this));
                return new NoteFile(notePath, noteFileInterface);
            }).whenComplete((result, failure)->{
                releaseLock();
            });
    }
    
    // Called by ManagedNoteFileInterface when it has no more references
    public void cleanupInterface(NoteStringArrayReadOnly path, ManagedNoteFileInterface expectedInterface) {
        m_registry.remove(path, expectedInterface);
    }
    
    // ===== KEY UPDATE COORDINATION =====
    
    public CompletableFuture<File> prepareAllForKeyUpdate() {
        return acquireLock()
            .thenCompose(filePathLedger -> {
                List<CompletableFuture<Void>> lockFutures = m_registry.values().stream()
                    .map(ManagedNoteFileInterface::prepareForKeyUpdate)
                    .collect(Collectors.toList());
                
                return CompletableFuture.allOf(lockFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> filePathLedger);
            });
    }

    public CompletableFuture<File> prepareAllForShutdown() {
        return acquireLock()
            .thenCompose(filePathLedger -> {
                List<CompletableFuture<Void>> lockFutures = m_registry.values().stream()
                    .map(ManagedNoteFileInterface::perpareForShutdown)
                    .collect(Collectors.toList());
                
                return CompletableFuture.allOf(lockFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v-> filePathLedger);
            });
    }
    
    public void completeKeyUpdateForAll() {
        m_registry.values().forEach(ManagedNoteFileInterface::completeKeyUpdate);
        releaseLock();
    }
    
    // ===== PASSWORD CHANGE OPERATIONS =====
    
    /**
     * Update file path ledger encryption (normal password change)
     * 
     * This is the PRIMARY password change operation.
     * Coordinates with SettingsData to:
     * 1. Update SettingsData (BCrypt, salt, keys)
     * 2. Re-encrypt all files in ledger
     * 
     * Called by AppData.changePassword()
     */
    public CompletableFuture<NoteBytesObject> updateFilePathLedgerEncryption(
        AsyncNoteBytesWriter progressWriter,
        NoteBytesEphemeral oldPassword,
        NoteBytesEphemeral newPassword,
        int batchSize
    ) {
        ProgressMessage.writeAsync(ProtocolMesssages.STARTING, 0, 4, 
            "Acquiring file locks", progressWriter);

        return prepareAllForKeyUpdate()
            .thenCompose(filePathLedger->
                super.updateFilePathLedgerEncryption(filePathLedger, progressWriter, 
                    oldPassword, newPassword, batchSize))
            .whenComplete((result, throwable) -> {
                ProgressMessage.writeAsync(ProtocolMesssages.STOPPING, 0, -1, 
                    "Releasing locks", progressWriter);
                completeKeyUpdateForAll();
                StreamUtils.safeClose(progressWriter);
            });
    }

    // ===== RECOVERY OPERATIONS =====
    /*
    * Complete password change recovery
    * Re-encrypts files from OLD key to NEW key (current)
    * 
    * Used when password change was interrupted mid-operation.
    * Files are still encrypted with OLD key and need updating.
    * 
    * @param filePaths Files to update (still encrypted with OLD key)
    * @param oldKey Key to decrypt with (previous encryption key)
    * @param batchSize Concurrent batch size
    * @param progressWriter Progress tracking
    * @return Success/failure
    */
    public CompletableFuture<Boolean> completePasswordChangeForFiles(
            List<String> filePaths,
            SecretKey oldKey,
            int batchSize,
            AsyncNoteBytesWriter progressWriter) {
        
        System.out.println(String.format(
            "[NoteFileService] COMPLETE: Re-encrypting %d files (OLD → NEW key)",
            filePaths.size()));
        
        // Get current (NEW) key from SettingsData
        SecretKey newKey = getSettingsData().getSecretKey();
        
        return reEncryptFiles(
            filePaths,
            oldKey,      // Decrypt with OLD
            newKey,      // Encrypt with NEW (current)
            "COMPLETE",
            batchSize,
            progressWriter
        );
    }


    /**
     * Rollback password change
     * Re-encrypts files from NEW key back to OLD key
     * 
     * Used when user wants to restore previous password.
     * Files were successfully updated to NEW key but need reverting.
     * 
     * @param filePaths Files to rollback (encrypted with NEW key)
     * @param oldKey Key to encrypt with (restore to this)
     * @param batchSize Concurrent batch size
     * @param progressWriter Progress tracking
     * @return Success/failure
     */
    public CompletableFuture<Boolean> rollbackPasswordChangeForFiles(
            List<String> filePaths,
            SecretKey oldKey,
            int batchSize,
            AsyncNoteBytesWriter progressWriter) {
        
        System.out.println(String.format(
            "[NoteFileService] ROLLBACK: Re-encrypting %d files (NEW → OLD key)",
            filePaths.size()));
        
        // Get current (was NEW) key from SettingsData
        SecretKey currentKey = getSettingsData().getSecretKey();
        
        return reEncryptFiles(
            filePaths,
            currentKey,  // Decrypt with current (was NEW)
            oldKey,      // Encrypt with OLD (restore)
            "ROLLBACK",
            batchSize,
            progressWriter
        );
    }


    /**
     * Generic re-encryption operation (PRIVATE)
     * 
     * @param operation Label for logging ("COMPLETE" or "ROLLBACK")
     */
    private CompletableFuture<Boolean> reEncryptFiles(
            List<String> filePaths,
            SecretKey decryptKey,
            SecretKey encryptKey,
            String operation,
            int batchSize,
            AsyncNoteBytesWriter progressWriter) {
        
        if (filePaths == null || filePaths.isEmpty()) {
            System.out.println("[NoteFileService] No files to re-encrypt");
            return CompletableFuture.completedFuture(true);
        }
        
        System.out.println(String.format(
            "[NoteFileService] %s: Processing %d files with batch size %d",
            operation, filePaths.size(), batchSize));
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Semaphore semaphore = new Semaphore(batchSize, true);
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                AtomicInteger completed = new AtomicInteger(0);
                AtomicInteger failed = new AtomicInteger(0);
                
                for (String filePath : filePaths) {
                    File file = new File(filePath);
                    
                    if (!file.exists() || !file.isFile()) {
                        System.err.println("[NoteFileService] File not found: " + filePath);
                        failed.incrementAndGet();
                        
                        if (progressWriter != null) {
                            TaskMessages.writeErrorAsync(operation,
                                "File not found: " + filePath,
                                new IOException("File not found"), progressWriter);
                        }
                        continue;
                    }
                    
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            semaphore.acquire();
                            try {
                                File tmpFile = new File(file.getAbsolutePath() + ".tmp");
                                
                                if (progressWriter != null) {
                                    ProgressMessage.writeAsync(ProtocolMesssages.STARTING,
                                        completed.get(), filePaths.size(), 
                                        operation + ": " + filePath, 
                                        progressWriter);
                                }
                                
                                // Re-encrypt: decryptKey → encryptKey
                                FileStreamUtils.updateFileEncryption(
                                    decryptKey, encryptKey, file, tmpFile, progressWriter);
                                
                                completed.incrementAndGet();
                                
                                if (progressWriter != null) {
                                    ProgressMessage.writeAsync(ProtocolMesssages.SUCCESS,
                                        completed.get(), filePaths.size(), 
                                        operation + ": " + filePath, 
                                        progressWriter);
                                }
                                
                            } finally {
                                semaphore.release();
                            }
                        } catch (Exception e) {
                            failed.incrementAndGet();
                            System.err.println("[NoteFileService] " + operation + 
                                " failed: " + filePath + " - " + e.getMessage());
                            
                            if (progressWriter != null) {
                                TaskMessages.writeErrorAsync(operation,
                                    filePath, e, progressWriter);
                            }
                        }
                    }, getExecService());
                    
                    futures.add(future);
                }
                
                // Wait for all to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                int failedCount = failed.get();
                boolean success = failedCount == 0;
                
                System.out.println(String.format(
                    "[NoteFileService] %s complete: %d succeeded, %d failed",
                    operation, completed.get(), failedCount));
                
                return success;
                
            } catch (Exception e) {
                System.err.println("[NoteFileService] " + operation + 
                    " operation failed: " + e.getMessage());
                return false;
            }
        }, VirtualExecutors.getVirtualExecutor());
    }



    // ===== DISK SPACE VALIDATION =====
    
    /**
     * Validate disk space for re-encryption operation
     * 
     * Checks if there's enough space to create temporary files for all
     * encrypted files during password change operation.
     */
    public CompletableFuture<DiskSpaceValidation> validateDiskSpaceForReEncryption() {
        return acquireLock()
            .thenCompose(ledgerFile -> {
                try {
                    return collectFilePathsFromLedger(ledgerFile)
                        .thenApply(files -> calculateDiskSpaceRequirements(files, ledgerFile))
                        .whenComplete((validation, ex) -> {
                            releaseLock();
                        });
                } catch (Exception e) {
                    releaseLock();
                    throw new RuntimeException("Failed to validate disk space", e);
                }
            });
    }
        
    /**
     * Collect all file paths from the encrypted ledger
     * Uses the established pattern: decrypt -> parse -> collect
     */
    private CompletableFuture<List<File>> collectFilePathsFromLedger(File ledgerFile) {
        if (!ledgerFile.exists() || !ledgerFile.isFile()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            List<File> files = new ArrayList<>();
            PipedOutputStream decryptedOutput = new PipedOutputStream();
            
            try {
                // Start decryption using factory's method (handles secret key internally)
                CompletableFuture<NoteBytesObject> decryptFuture = 
                    performDecryption(ledgerFile, decryptedOutput);
                
                // Parse in parallel
                PipedInputStream decryptedInput = new PipedInputStream(decryptedOutput);
                
                try (NoteBytesReader reader = new NoteBytesReader(decryptedInput)) {
                    // Parse root level - don't need to track bytes for collection
                    parseRootLevelForFiles(reader, files);
                }
                
                // Wait for decryption to complete
                decryptFuture.join();
                
            } catch (IOException e) {
                System.err.println("[NoteFileService] Error reading ledger: " + e.getMessage());
            } finally {
                StreamUtils.safeClose(decryptedOutput);
            }
            
            return files;
            
        }, getExecService());
    }
        

   
    // ===== FILE DELETION =====
    
    public CompletableFuture<NotePath> deleteNoteFilePath(
            NoteStringArrayReadOnly path, 
            boolean recursive, 
            AsyncNoteBytesWriter progressWriter) {

        if(path == null || path.byteLength() == 0 || path.size() == 0){
            StreamUtils.safeClose(progressWriter);
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Invalid path provided"));
        }

        if(progressWriter != null){
            ProgressMessage.writeAsync(ProtocolMesssages.STARTING,
                0, 4, "Acquiring lock", progressWriter);
        }
        
        List<ManagedNoteFileInterface> interfaceList = new ArrayList<>();

        return acquireLock().thenCompose((filePathLedger)->{
            
            if(!filePathLedger.exists() || !filePathLedger.isFile() || 
                filePathLedger.length() <= CryptoService.AES_IV_SIZE){
                throw new IllegalArgumentException(
                    "Invalid ledger, inaccessible or insufficient size provided");
            }

            NotePath notePath = new NotePath(filePathLedger, path, recursive, progressWriter);

            notePath.progressMsg(ProtocolMesssages.STARTING, 2, 4,
                "Initial lock acquired, preparing registry interfaces");
            
            return prepareForShutdown(path, recursive, interfaceList)
                .thenCompose(v->deleteNoteFilePath(notePath));
                
        }).whenComplete((notePath, ex)->{
            int toRemoveSize = interfaceList.size();
            for(int i = 0; i < toRemoveSize; i++){
                ManagedNoteFileInterface managedInterface = interfaceList.get(i);
                boolean isDeleted = !managedInterface.isFile();
                
                if(managedInterface.isLocked()){
                    managedInterface.releaseLock();
                }
                
                notePath.progressMsg(ProtocolMesssages.STOPPING, i, toRemoveSize, 
                    managedInterface.getId().getAsString(), new NoteBytesPair[]{
                        new NoteBytesPair(Keys.STATUS, 
                            !managedInterface.isLocked() && isDeleted ? 
                                ProtocolMesssages.SUCCESS : ProtocolMesssages.FAILED
                        )
                    });
            }
            
            StreamUtils.safeClose(progressWriter);
            releaseLock();
        });
    }
    
    private CompletableFuture<Void> prepareForShutdown(
            NoteStringArrayReadOnly path, 
            boolean recursive,
            List<ManagedNoteFileInterface> interfaceList) {
   
        if(recursive){
            List<CompletableFuture<Void>> list = new ArrayList<>();
            
            Iterator<Map.Entry<NoteStringArrayReadOnly,ManagedNoteFileInterface>> iterator = 
                m_registry.entrySet().iterator();
            
            while(iterator.hasNext()) {
                Map.Entry<NoteStringArrayReadOnly,ManagedNoteFileInterface> entry = 
                    iterator.next();
                list.add(entry.getValue().perpareForShutdown());
                if(interfaceList != null){
                    interfaceList.add(entry.getValue());
                }
                iterator.remove(); 
            }
            
            return CompletableFuture.allOf(list.toArray(new CompletableFuture[0]));
        } else {
            ManagedNoteFileInterface managedInterface = m_registry.remove(path);
            if(managedInterface != null){
                if(interfaceList != null){
                    interfaceList.add(managedInterface);
                }
                return managedInterface.perpareForShutdown();
            } else {
                return CompletableFuture.completedFuture(null);
            }
        }
    }
    
    // ===== MAINTENANCE =====
    
    public int cleanupUnusedInterfaces() {
        int removed = 0;
        Iterator<Map.Entry<NoteStringArrayReadOnly, ManagedNoteFileInterface>> iterator = 
            m_registry.entrySet().iterator();
            
        while (iterator.hasNext()) {
            Map.Entry<NoteStringArrayReadOnly, ManagedNoteFileInterface> entry = 
                iterator.next();
            ManagedNoteFileInterface noteInterface = entry.getValue();
            
            if (noteInterface.getReferenceCount() == 0 && !noteInterface.isLocked()) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }
    
    public int getRegistrySize() {
        return m_registry.size();
    }
    
    public Map<NoteStringArrayReadOnly, Integer> getReferenceCounts() {
        return m_registry.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getReferenceCount()
            ));
    }

    public CompletableFuture<Integer> getFileCount() {
        return validateDiskSpaceForReEncryption()
            .thenApply(DiskSpaceValidation::getNumberOfFiles);
    }
}