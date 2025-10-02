package io.netnotes.engine.noteFiles;

import java.io.File;
import java.io.IOException;
import java.io.PipedOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.SecretKey;

import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.SettingsData.InvalidPasswordException;

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
        NoteUUID noteUUID = NoteUUID.createLocalUUID128();
        String encodedUUID = noteUUID.getAsUrlSafeString();
        File dataFile = new File(dataDir.getAbsolutePath() + "/" + encodedUUID + ".dat");
        return dataFile;
    }
    

    public File getFilePathLedger() {
        File dataDir = m_settingsData.getDataDir();

        File idDataFile = new File(dataDir.getAbsolutePath() + "/data.dat");
        return idDataFile;
    }

    private SettingsData getSettingsData(){
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
            return FileStreamUtils.saveEncryptedFileSwap(file, getSecretKey(), pipedOutputStream);
        }else{
            return FileStreamUtils.saveEncryptedFile(file, getSecretKey(), pipedOutputStream);
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
        }, getExecService()).thenCompose(notePath-> FactoryFilePathGet.getOrCreateNoteFilePath(notePath, 
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
                    ProgressMessage.writeAsync(NoteMessaging.Status.STARTING, 3, 4, 
                        "Created new key",progressWriter);
                 } catch (IOException | InvalidPasswordException | InvalidKeySpecException | NoSuchAlgorithmException e) {
                     throw new RuntimeException("Failed to update password", e);
                } 
            }, getExecService())
            .thenCompose(v -> {
                ProgressMessage.writeAsync(NoteMessaging.Status.STARTING, 4, 4, 
                        "Opening file path ledger",progressWriter);
                return FilePathEncryptionUpdate.updatePathLedgerEncryption(filePathLedger, getSettingsData(). getOldKey(), getSecretKey(), batchSize, progressWriter, getExecService());
            });
    }

    public CompletableFuture<Void> verifyPassword(NoteBytesEphemeral password){
        return CompletableFuture.runAsync(() -> {
            SettingsData.verifyPassword(password, getSettingsData().getBCryptKey());
        });
    }
    
    protected CompletableFuture<NoteBytesObject> deleteNoteFilePath(File filePathLedger, NoteStringArrayReadOnly path, boolean recursive){
       
        return CompletableFuture.supplyAsync(() -> {
            if(!m_locked.get()){
                throw new IllegalStateException("Lock required");
            }
               
            if (
                filePathLedger == null ||
                path == null || 
                path.byteLength() == 0
            ) {
                throw new IllegalArgumentException("Required parameters cannot be null");
            }

            NotePath notePath = new NotePath(filePathLedger, path, recursive);
            notePath.checkDataDir();

            if(
                !filePathLedger.exists() || 
                !filePathLedger.isFile() || 
                filePathLedger.length() <= CryptoService.AES_IV_SIZE
            ){
                throw new RuntimeException("File path ledger is unavailable or contains no information");
            }
            
            if(notePath.getSize() == 0){
                throw new IllegalArgumentException("Path does not contain any valid elements");
            }

            return notePath;

        }, getExecService())
            .thenCompose(notePath-> FactoryFilePathDelete.deleteNoteFilePath(notePath,getSecretKey(), getExecService()));
    }
}
