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

import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.messaging.task.TaskMessages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.utils.Utils;

public class NotePathFactory {
    public static final NoteBytes FILE_PATH = new NoteBytes(new byte[]{0x01});
    public static final int FILE_PATH_TOTAL_BYTE_LENGTH = FILE_PATH.byteLength() + NoteBytesMetaData.STANDARD_META_DATA_SIZE;
    public static final int PATH_LENGTH_WARNING = 512; //excessive size

    private final ExecutorService m_execService;
    private final Semaphore m_dataSemaphore;
    private final ScheduledExecutorService m_schedualedExecutor;
    private final SettingsData m_settingsData;

    public NotePathFactory(SettingsData settingsData){

        m_execService = Executors.newVirtualThreadPerTaskExecutor();
        m_schedualedExecutor = Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory());
        m_dataSemaphore = new Semaphore(1, true);
        m_settingsData = settingsData;
    }

    public ScheduledExecutorService getScheduledExecutor(){
        return m_schedualedExecutor;
    }



    public Semaphore getDataSemaphore(){
        return m_dataSemaphore;
    }



    public ExecutorService getExecService(){
        return m_execService;
    }




    public static File createNewDataFile(File dataDir) {     
        NoteUUID noteUUID = NoteUUID.createLocalUUID128();
        String encodedUUID = noteUUID.getAsUrlSafeString();
        File dataFile = new File(dataDir.getAbsolutePath() + "/" + encodedUUID + ".dat");
        return dataFile;
    }
    

    public File getFilePathLedger() throws IOException{
        File dataDir = m_settingsData.getDataDir();

        File idDataFile = new File(dataDir.getAbsolutePath() + "/data.dat");
        return idDataFile;
    }


    protected CompletableFuture<NoteBytes> getNoteFilePath(NoteStringArrayReadOnly path) {
        AtomicBoolean lockAcquired = new AtomicBoolean(false);
        
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    getDataSemaphore().acquire();
                    lockAcquired.set(true);
                    return getFilePathLedger();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted", e);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to get ID data file", e);
                }
            }, getExecService())
            .thenCompose(filePathLedger -> {
                return FactoryPathProcess.getOrCreateIdDataFile(filePathLedger, path, getSecretKey(), getExecService());
                
            })
            .whenComplete((result, throwable) -> {
                // Always release semaphore
                if (lockAcquired.getAndSet(false)) {
                    getDataSemaphore().release();
                }
                
                if (throwable != null) {
                    Utils.writeLogMsg("AppData.getIdDataFileAsync", throwable);
                }
            });
    }


     protected CompletableFuture<NoteBytesObject> updateFilePathLedgerEncryption(
        AsyncNoteBytesWriter progressWriter,
        NoteBytesEphemeral oldPassword,
        NoteBytesEphemeral newPassword,
        int batchSize
     ) {
        AtomicBoolean lockAcquired = new AtomicBoolean(false);
        
        return CompletableFuture
            .supplyAsync(() -> {

                try {
                    progressWriter.writeAsync(ProgressMessage
                        .getProgressMessage(NoteMessaging.Status.STARTING, 0, -1, "Verifying password"));
                    
                    SettingsData.verifyPassword(oldPassword, getSettingsData().getAppKey());
                    progressWriter.writeAsync(ProgressMessage
                        .getProgressMessage(NoteMessaging.Status.STARTING, 0, -1, "Aquiring file path ledger"));
                    getDataSemaphore().acquire();
                    lockAcquired.set(true);
                    getSettingsData().updatePassword(oldPassword, newPassword);
                    progressWriter.writeAsync(ProgressMessage
                        .getProgressMessage(NoteMessaging.Status.STARTING, 0, -1, "Created new key"));
                    return getFilePathLedger();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted", e);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to get file path ledger", e);
                } catch (VerifyError | InvalidKeySpecException | NoSuchAlgorithmException e) {
                     throw new RuntimeException("Failed to update password", e);
                } 
            }, getExecService())
            .thenCompose(filePathLedger -> {
                return FilePathEncryptionUpdate.updatePathLedgerEncryption(filePathLedger, getSettingsData(). getOldKey(), getSecretKey(), batchSize, progressWriter, getExecService());
            })
            .whenComplete((result, throwable) -> {
                if (lockAcquired.getAndSet(false)) {
                    getDataSemaphore().release();
                }
                if (throwable != null) {
                    Utils.writeLogMsg("NoteFileFactory.updateFilePathLedgerEncryption", throwable);
                    progressWriter.writeAsync(ProgressMessage
                    .getProgressMessage(NoteMessaging.Status.STOPPING, 0, -1, NoteMessaging.General.ERROR,
                    new NoteBytesPair[]{
                        new NoteBytesPair("errorMsg", TaskMessages.createErrorMessage(
                            NoteMessaging.Status.STOPPING, 
                            "termination error", throwable)) 
                    }            
                ));
                }
            });
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

}
