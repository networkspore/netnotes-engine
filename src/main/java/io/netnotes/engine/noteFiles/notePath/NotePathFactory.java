package io.netnotes.engine.noteFiles.notePath;

import java.io.File;
import java.io.IOException;
import java.io.PipedOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.SecretKey;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.SettingsData.InvalidPasswordException;
import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.IntCounter;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteFiles.DiskSpaceValidation;
import io.netnotes.engine.noteFiles.FileStreamUtils;
import io.netnotes.engine.utils.streams.StreamUtils;

public class NotePathFactory {
   

    private final ExecutorService m_execService;
    private final Semaphore m_dataSemaphore;
    private final ScheduledExecutorService m_schedualedExecutor;
    private final SettingsData m_settingsData;
    private final AtomicBoolean m_locked = new AtomicBoolean(false);

    public NotePathFactory(SettingsData settingsData){

        m_execService = Executors.newVirtualThreadPerTaskExecutor();
        m_schedualedExecutor = Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory());
        m_dataSemaphore = new Semaphore(1, true);
        m_settingsData = settingsData;
    }

    public ScheduledExecutorService getScheduledExecutor(){
        return m_schedualedExecutor;
    }



    protected Semaphore getDataSemaphore(){
        return m_dataSemaphore;
    }


    protected CompletableFuture<File> acquireLock() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                m_dataSemaphore.acquire();
                m_locked.set(true);
                return getFilePathLedger();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }, getExecService());
    }

    protected boolean isLocked() {
        return m_locked.get();
    }

    protected void releaseLock() {
        if (m_locked.getAndSet(false)) {
            m_dataSemaphore.release();
        
        }
    }

    public ExecutorService getExecService(){
        return m_execService;
    }

    


    public static File generateNewDataFile(File dataDir) {     
        String encodedUUID = NoteUUID.createSafeUUID128();
        File dataFile = new File(dataDir.getAbsolutePath() + "/" + encodedUUID + ".dat");
        return dataFile;
    }
    

    public File getFilePathLedger() {
        File dataDir = SettingsData.getDataDir();

        File idDataFile = new File(dataDir.getAbsolutePath() + "/data.dat");
        return idDataFile;
    }

    public SettingsData getSettingsData(){
        return m_settingsData;
    }

    private SecretKey getSecretKey(){
        return getSettingsData().getSecretKey();
    }

   public CompletableFuture<NoteBytesObject> performDecryption(
        File file, 
        PipedOutputStream pipedOutput
    ){
        return FileStreamUtils.performDecryption(file, pipedOutput, getSecretKey(), getExecService());
    }
  
    public CompletableFuture<NoteBytesObject> saveEncryptedFileSwap(
        File file,
        PipedOutputStream pipedOutputStream
    ) {
        if(file.exists() && file.isFile()){
            return FileStreamUtils.saveEncryptedFileSwap(file, getSecretKey(), pipedOutputStream, getExecService());
        }else{
            return FileStreamUtils.saveEncryptedFile(file, getSecretKey(), pipedOutputStream, getExecService());
        }
    }


    protected CompletableFuture<NoteBytes> getNoteFilePath(File notePathLedger, NoteStringArrayReadOnly path) {
       
        return CompletableFuture.supplyAsync(()->{
            if(!m_locked.get()){
                throw new IllegalStateException("Lock required");
            }
            if (
                path == null || 
                notePathLedger == null
            ) {
                throw new IllegalArgumentException("Required parameters cannot be null");
            }
            NotePath notePath = new NotePath(notePathLedger, path);

            notePath.checkDataDir();

            return notePath;
        }, getExecService()).thenCompose(notePath-> NotePathGet.getOrCreateNoteFilePath(notePath, 
            getSecretKey(), getExecService()));
    }


     protected CompletableFuture<NoteBytesObject> updateFilePathLedgerEncryption(
        File filePathLedger,
        AsyncNoteBytesWriter progressWriter,
        NoteBytesEphemeral oldPassword,
        NoteBytesEphemeral newPassword,
        int batchSize
     ) { 
        return CompletableFuture
            .runAsync(() -> {
                if(!m_locked.get()){
                    throw new IllegalStateException("Lock required");
                }
                try {
                    getSettingsData().updatePassword(oldPassword, newPassword);
                    ProgressMessage.writeAsync(ProtocolMesssages.STARTING, 3, 4, 
                        "Created new key",progressWriter);
                 } catch (IOException | InvalidPasswordException | InvalidKeySpecException | NoSuchAlgorithmException e) {
                     throw new RuntimeException("Failed to update password", e);
                } 
            }, getExecService())
            .thenCompose(v -> {
                ProgressMessage.writeAsync(ProtocolMesssages.STARTING, 4, 4, 
                        "Opening file path ledger",progressWriter);
                return NotePathReEncryption.updatePathLedgerEncryption(filePathLedger, getSettingsData(). getOldKey(), getSecretKey(), batchSize, progressWriter, getExecService());
            });
    }

    public CompletableFuture<Void> verifyPassword(NoteBytesEphemeral password){
        return CompletableFuture.runAsync(() -> {
            HashServices.verifyBCryptPassword(password, getSettingsData().getBCryptKey());
        });
    }
    
    protected CompletableFuture<NotePath> deleteNoteFilePath(NotePath notePath){

        notePath.progressMsg(ProtocolMesssages.STARTING,3, 4, "Initializing pipeline");
        
        File ledger = notePath.getPathLedger();
        SecretKey secretKey = getSecretKey();
        ExecutorService execService = getExecService();
        PipedOutputStream decryptedOutput = new PipedOutputStream();
        PipedOutputStream parsedOutput = new PipedOutputStream();

        CompletableFuture<NoteBytesObject> decryptFuture = 
            FileStreamUtils.performDecryption(ledger, decryptedOutput, secretKey, execService);
        
        CompletableFuture<Void> parseFuture = 
            NotePathDelete.parseStreamForRoot(notePath, secretKey, decryptedOutput, parsedOutput, execService);
        
        CompletableFuture<NoteBytesObject> saveFuture = 
            FileStreamUtils.saveEncryptedFileSwap(ledger, secretKey, parsedOutput, execService);
        
        return CompletableFuture.allOf(decryptFuture, parseFuture, saveFuture)
            .whenComplete((v, ex) -> {
                StreamUtils.safeClose(decryptedOutput);
                StreamUtils.safeClose(parsedOutput);

            }).thenCompose(v-> 
                CompletableFuture.allOf(notePath.getCompletableList().toArray(new CompletableFuture[0]))
            .thenCompose(nv->
                CompletableFuture.completedFuture(notePath)
            ));
    
    }

     /**
     * Parse bucket (nested structure) to collect file paths
     */
    public static void parseBucketForFiles(
            NoteBytesReader reader, 
            List<File> files, 
            IntCounter byteCounter,
            int bucketEnd) throws IOException {
        
        while (byteCounter.get() < bucketEnd) {
            NoteBytes key = reader.nextNoteBytes();
            
            if (key == null) {
                break;
            }
            
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
                    
                    byteCounter.add(NoteBytesMetaData.STANDARD_META_DATA_SIZE);
                    int nestedEnd = byteCounter.get() + metaData.getLength();
                    
                    parseBucketForFiles(reader, files, byteCounter, nestedEnd);
                }
            }
        }
    }


    /**
     * Calculate disk space requirements based on collected files
     */
    public static DiskSpaceValidation calculateDiskSpaceRequirements(
            List<File> files, 
            File ledgerFile) {
        
        int fileCount = files.size();
        long totalSize = 0;
        long[] largestBatchSizes = new long[10];
        
        for (File file : files) {
            long size = file.length();
            totalSize += size;
            DiskSpaceValidation.updateLargestBatchSizes(largestBatchSizes, size);
        }
        
        long availableSpace = ledgerFile.getUsableSpace();
        long requiredSpace = totalSize;
        long bufferSpace = Math.min(
            (long) (requiredSpace * 0.20),
            1024L * 1024 * 1024 // 1GB
        );
        
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
     * Parse root level of ledger to collect file paths
     * Similar to NotePathReEncryption.rootLevelParse but only collecting paths
     */
    public static void parseRootLevelForFiles(NoteBytesReader reader, List<File> files) 
            throws IOException {
        
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
                    
                    // Track position to know bucket boundaries
                    IntCounter byteCounter = new IntCounter(0);
                    byteCounter.add(rootKey.byteLength());
                    byteCounter.add(NoteBytesMetaData.STANDARD_META_DATA_SIZE);
                    
                    int bucketEnd = byteCounter.get() + metaData.getLength();
                    
                    // Recursively collect file paths within bucket
                    parseBucketForFiles(reader, files, byteCounter, bucketEnd);
                }
            }
            
            rootKey = reader.nextNoteBytes();
        }
    }
}
