package io.netnotes.engine.noteFiles.notePath;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.IntCounter;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteFiles.DiskSpaceValidation;
import io.netnotes.engine.noteFiles.ManagedNoteFileInterface;
import io.netnotes.engine.noteFiles.NoteFile;
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
import java.util.stream.Collectors;


public class NoteFileService extends NotePathFactory {
    private final Map<NoteStringArrayReadOnly, ManagedNoteFileInterface> m_registry = new ConcurrentHashMap<>();

    public NoteFileService(SettingsData settingsData) {
        super(settingsData);
    }
    
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
        // Use atomic remove to ensure we only remove the expected interface
        m_registry.remove(path, expectedInterface);
    }
    
    // For key updates - acquire locks on all registered interfaces
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
                
                return CompletableFuture.allOf(lockFutures.toArray(new CompletableFuture[0])).thenApply(v-> filePathLedger);
            });
    }
    
    
    public void completeKeyUpdateForAll() {
        m_registry.values().forEach(ManagedNoteFileInterface::completeKeyUpdate);
        releaseLock();
    }
    
    // Manual cleanup method to remove unused interfaces (for maintenance)
    public int cleanupUnusedInterfaces() {
        int removed = 0;
        Iterator<Map.Entry<NoteStringArrayReadOnly, ManagedNoteFileInterface>> iterator = 
            m_registry.entrySet().iterator();
            
        while (iterator.hasNext()) {
            Map.Entry<NoteStringArrayReadOnly, ManagedNoteFileInterface> entry = iterator.next();
            ManagedNoteFileInterface noteInterface = entry.getValue();
            
            if (noteInterface.getReferenceCount() == 0 && !noteInterface.isLocked()) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }
    
    // Get current registry size for monitoring
    public int getRegistrySize() {
        return m_registry.size();
    }
    
    // Get reference counts for monitoring
    public Map<NoteStringArrayReadOnly, Integer> getReferenceCounts() {
        return m_registry.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getReferenceCount()
            ));
    }

 
    public CompletableFuture<NoteBytesObject> updateFilePathLedgerEncryption(
        AsyncNoteBytesWriter progressWriter,
        NoteBytesEphemeral oldPassword,
        NoteBytesEphemeral newPassword,
        int batchSize
    ) {
        ProgressMessage.writeAsync(ProtocolMesssages.STARTING, 0, 4, "Aquiring file locks", 
            progressWriter);

        return prepareAllForKeyUpdate()
            .thenCompose(filePathLedger->
                super.updateFilePathLedgerEncryption(filePathLedger, progressWriter, 
                    oldPassword, newPassword, batchSize)).whenComplete((result, throwable) -> {
                        ProgressMessage.writeAsync(ProtocolMesssages.STOPPING, 0, -1, "Releasing locks", 
                            progressWriter);
                        completeKeyUpdateForAll();
                        StreamUtils.safeClose(progressWriter);
                });
    }


    public CompletableFuture<Void> prepareForShutdown(NoteStringArrayReadOnly path, boolean recursive){
        List<ManagedNoteFileInterface> interfaceList = new ArrayList<>();
        return prepareForShutdown(path, recursive, interfaceList).thenAccept(v->{
            for(ManagedNoteFileInterface managedInterface : interfaceList){
                if(managedInterface.isLocked()){
                    managedInterface.releaseLock();
                }
            }
        });
    }
   
    public CompletableFuture<Void> prepareForShutdown(NoteStringArrayReadOnly path, boolean recursive, List<ManagedNoteFileInterface> interfaceList){
   
        if(recursive){
            List<CompletableFuture<Void>> list = new ArrayList<>();
            
            Iterator<Map.Entry<NoteStringArrayReadOnly,ManagedNoteFileInterface>> iterator = m_registry.entrySet().iterator();
            while(iterator.hasNext()) {
                Map.Entry<NoteStringArrayReadOnly,ManagedNoteFileInterface> entry = iterator.next();
                list.add(entry.getValue().perpareForShutdown());
                if(interfaceList != null){
                    interfaceList.add(entry.getValue());
                }
                iterator.remove(); 
            }
            
            return CompletableFuture.allOf(list.toArray(new CompletableFuture[0]));
        }else{
            ManagedNoteFileInterface managedInterface = m_registry.remove(path);
            if(managedInterface != null){
                if(interfaceList != null){
                    interfaceList.add(managedInterface);
                }
                return managedInterface.perpareForShutdown();
            }else{
                return CompletableFuture.completedFuture(null);
            }
        }
        
    }


    public CompletableFuture<NotePath> deleteNoteFilePath(NoteStringArrayReadOnly path, boolean recursive, 
        AsyncNoteBytesWriter progressWriter
     ) {

        if(path == null ||  path.byteLength() == 0 || path.size() == 0){
            StreamUtils.safeClose(progressWriter);
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid path provided"));
        }

        if(progressWriter != null){
            ProgressMessage.writeAsync(ProtocolMesssages.STARTING,
                    0, 4, "Aquiring lock", progressWriter);
        }
         List<ManagedNoteFileInterface> interfaceList = new ArrayList<>();

        return acquireLock().thenCompose((filePathLedger)->{
            
            if(!filePathLedger.exists() || !filePathLedger.isFile() || 
                filePathLedger.length() <= CryptoService.AES_IV_SIZE){
                throw new IllegalArgumentException("Invalid ledger, inaccessible or insufficient size provided");
            }

            NotePath notePath = new NotePath(filePathLedger, path, recursive, progressWriter);

            notePath.progressMsg(ProtocolMesssages.STARTING,2, 4,
                "Initial lock aquired, preparing registry interfaces");
            return prepareForShutdown(path, recursive, interfaceList).thenCompose(v->deleteNoteFilePath(notePath));
        }).whenComplete((notePath, ex)->{
            int toRemoveSize = interfaceList.size();
            for(int i = 0; i < toRemoveSize; i++){
                ManagedNoteFileInterface managedInterface= interfaceList.get(i);
                boolean isDeleted = !managedInterface.isFile();
                
                if(managedInterface.isLocked()){
                    managedInterface.releaseLock();
                }
                
                notePath.progressMsg(ProtocolMesssages.STOPPING, i, toRemoveSize, managedInterface.getId().getAsString(), new NoteBytesPair[]{
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



        
    /**
     * Parse root level of ledger to collect file paths
     * Similar to NotePathReEncryption.rootLevelParse but only collecting paths
     * Note: We don't track bytes since we're only reading, not writing
     */
    private void parseRootLevelForFiles(NoteBytesReader reader, List<File> files) throws IOException {
        
        NoteBytes rootKey = reader.nextNoteBytes();
        
        while (rootKey != null) {
            if (rootKey.equals(NotePath.FILE_PATH)) {
                // Found a file path entry at root level
                NoteBytes filePath = reader.nextNoteBytes();
                if (filePath != null) {
                    File file = new File(filePath.getAsString());
                    if (file.exists() && file.isFile()) {
                        files.add(file);
                    }
                }
                
            } else if (rootKey.getType() == NoteBytesMetaData.STRING_TYPE) {
                // This is a directory/bucket entry - recurse into it
                NoteBytesMetaData metaData = reader.nextMetaData();
                
                if (metaData != null && 
                    metaData.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                    
                    // We need to track position manually to know bucket boundaries
                    IntCounter byteCounter = new IntCounter(0);
                    
                    // Account for key and metadata we just read
                    byteCounter.add(rootKey.byteLength());
                    byteCounter.add(NoteBytesMetaData.STANDARD_META_DATA_SIZE);
                    
                    // Calculate bucket end
                    int bucketEnd = byteCounter.get() + metaData.getLength();
                    
                    // Recursively collect file paths within bucket
                    parseBucketForFiles(reader, files, byteCounter, bucketEnd);
                } else {
                    throw new IllegalStateException(
                        "Invalid value data detected: " + 
                        (metaData == null ? "metadata null" : "type: " + (int) metaData.getType())
                    );
                }
            } else {
                throw new IllegalStateException("Invalid key type: " + (int) rootKey.getType());
            }
            
            rootKey = reader.nextNoteBytes();
        }
    }


    /**
     * Parse bucket (nested structure) to collect file paths
     * Similar to NotePathReEncryption.recursivelyParseQueueEncryption
     * Uses IntCounter to track position within bucket boundaries
     */
    private void parseBucketForFiles(
            NoteBytesReader reader, 
            List<File> files, 
            IntCounter byteCounter,
            int bucketEnd) throws IOException {
        
        while (byteCounter.get() < bucketEnd) {
            NoteBytes key = reader.nextNoteBytes();
            
            if (key == null) {
                break;
            }
            
            // Track bytes read
            byteCounter.add(key.byteLength());
            
            if (key.equals(NotePath.FILE_PATH)) {
                // Found a file path
                NoteBytes filePath = reader.nextNoteBytes();
                if (filePath != null) {
                    byteCounter.add(filePath.byteLength());
                    
                    File file = new File(filePath.getAsString());
                    if (file.exists() && file.isFile()) {
                        files.add(file);
                    }
                }
                
            } else if (key.getType() == NoteBytesMetaData.STRING_TYPE) {
                // Nested bucket - recurse
                NoteBytesMetaData metaData = reader.nextMetaData();
                
                if (metaData != null && 
                    metaData.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                    
                    // Track metadata bytes
                    byteCounter.add(NoteBytesMetaData.STANDARD_META_DATA_SIZE);
                    
                    // Calculate nested bucket end
                    int nestedEnd = byteCounter.get() + metaData.getLength();
                    
                    // Recurse into nested bucket
                    parseBucketForFiles(reader, files, byteCounter, nestedEnd);
                } else {
                    throw new IllegalStateException(
                        "Invalid value data detected: " + 
                        (metaData == null ? "metadata null" : "type: " + (int) metaData.getType())
                    );
                }
            } else {
                throw new IllegalStateException("Invalid key type: " + (int) key.getType());
            }
        }
    }



    /**
     * Calculate disk space requirements based on collected files
     */
    private DiskSpaceValidation calculateDiskSpaceRequirements(
            List<File> files, 
            File ledgerFile) {
        
        int fileCount = files.size();
        long totalSize = 0;
        
        // Track largest files for batch size calculation
        long[] largestBatchSizes = new long[10];
        
        for (File file : files) {
            long size = file.length();
            totalSize += size;
            
            // Track largest files
            DiskSpaceValidation.updateLargestBatchSizes(largestBatchSizes, size);
        }
        
        // Get available disk space at ledger location
        long availableSpace = ledgerFile.getUsableSpace();
        
        // Required space = total size (for .tmp copies during re-encryption)
        long requiredSpace = totalSize;
        
        // Buffer space = 20% of required or 1GB, whichever is smaller
        long bufferSpace = Math.min(
            (long) (requiredSpace * 0.20),
            1024L * 1024 * 1024 // 1GB
        );
        
        // Valid if we have required + buffer space
        boolean isValid = availableSpace >= (requiredSpace + bufferSpace);
        
        System.out.println(String.format(
            "[NoteFileService] Disk space validation:\n" +
            "  Files found: %d\n" +
            "  Total size: %.2f MB\n" +
            "  Required space: %.2f MB\n" +
            "  Available space: %.2f MB\n" +
            "  Buffer space: %.2f MB\n" +
            "  Validation: %s",
            fileCount,
            totalSize / (1024.0 * 1024.0),
            requiredSpace / (1024.0 * 1024.0),
            availableSpace / (1024.0 * 1024.0),
            bufferSpace / (1024.0 * 1024.0),
            isValid ? "PASSED" : "FAILED"
        ));
        
        return new DiskSpaceValidation(
            isValid,
            fileCount,
            totalSize,
            requiredSpace,
            availableSpace,
            bufferSpace
        );
    }

    /**
     * Get file count (convenience method)
     */
    public CompletableFuture<Integer> getFileCount() {
        return validateDiskSpaceForReEncryption()
            .thenApply(DiskSpaceValidation::getNumberOfFiles);
    }

}