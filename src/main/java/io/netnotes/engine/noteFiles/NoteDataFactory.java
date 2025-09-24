package io.netnotes.engine.noteFiles;

import java.io.File;
import java.io.IOException;
import java.io.PipedOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.crypto.SecretKey;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteFiles.FileStreamUtils.BulkUpdateConfig;
import io.netnotes.engine.noteFiles.FileStreamUtils.BulkUpdateResult;
import io.netnotes.engine.utils.Utils;

public class NoteDataFactory {
    public static final NoteBytes FILE_PATH = new NoteBytes(new byte[]{0x01});
    public static final int PATH_LENGTH_WARNING = 512;
    
    private final ExecutorService m_execService;
    private final Semaphore m_dataSemaphore;
    private final ScheduledExecutorService m_schedualedExecutor;
    private final SettingsData m_settingsData;

    public NoteDataFactory(SettingsData settingsData){

        m_execService = Executors.newVirtualThreadPerTaskExecutor();
        m_schedualedExecutor = Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory());
        m_dataSemaphore = new Semaphore(1);
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
    

    public File getIdDataFile() throws IOException{
        File dataDir = m_settingsData.getDataDir();

        File idDataFile = new File(dataDir.getAbsolutePath() + "/data.dat");
        return idDataFile;
    }


    protected CompletableFuture<File> getIdDataFile(NoteStringArrayReadOnly path) {
        AtomicBoolean lockAcquired = new AtomicBoolean(false);
        
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    getDataSemaphore().acquire();
                    lockAcquired.set(true);
                    return getIdDataFile();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted", e);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to get ID data file", e);
                }
            }, getExecService())
            .thenCompose(idDataFile -> {
                return FactoryPathProcess.getOrCreateIdDataFile(idDataFile, path, getSecretKey(), getExecService());
                
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

    protected CompletableFuture<BulkUpdateResult> updateAllFileEncryptionWithProgress(
        NoteBytesEphemeral oldPassword,
            NoteBytesEphemeral newPassword, BulkUpdateConfig config,
            Consumer<ProgressUpdate> progressCallback, ExecutorService execService) {
        
        if (newPassword.byteLength() > 0) {
            AtomicBoolean lockAcquired = new AtomicBoolean(false);
            
            try {
                SettingsData.verifyPassword(oldPassword, getSettingsData().getAppKey());
                return CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            getDataSemaphore().acquire();
                            lockAcquired.set(true);
                            return getIdDataFile();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Thread interrupted while acquiring semaphore", e);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to get ID data file", e);
                        }
                    }, getExecService())
                    .thenCompose(idDataFile -> {
                        if (!idDataFile.isFile()) {
                            try {
                                getSettingsData().updatePassword(oldPassword, newPassword);
                            } catch (VerifyError | InvalidKeySpecException | NoSuchAlgorithmException | IOException e) {
                                throw new RuntimeException("Password update failed", e);
                            }
                            progressCallback.accept(new ProgressUpdate(0, 0, "No data file found"));
                            return CompletableFuture.completedFuture(new BulkUpdateResult(0, 0, Collections.emptyList()));
                        }

                        return processDataFileForBulkUpdateWithProgress(idDataFile, config, progressCallback, getExecService());
                    })
                    .whenComplete((result, throwable) -> {
                        if (lockAcquired.getAndSet(false)) {
                            getDataSemaphore().release();
                        }

                        if (throwable != null) {
                            Utils.writeLogMsg("AppData.updateAllFileEncryptionWithProgress", throwable);
                            progressCallback.accept(new ProgressUpdate(-1, -1, "Error: " + throwable.getMessage()));
                        } else if (result != null) {
                            progressCallback.accept(new ProgressUpdate(result.getTotalFiles(), result.getTotalFiles(),
                                "Completed: " + result.toString()));
                        }
                    });
            } catch (Exception e) {
                CompletableFuture<BulkUpdateResult> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new RuntimeException("Could not start update", e));
                return failedFuture;
            }
        } else {
            CompletableFuture<BulkUpdateResult> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Password invalid"));
            return failedFuture;
        }
    }


    private CompletableFuture<BulkUpdateResult> processDataFileForBulkUpdateWithProgress(
        File idDataFile, BulkUpdateConfig config,
        Consumer<ProgressUpdate> progressCallback, ExecutorService execService) {
        
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    SecretKey oldAppKey = m_settingsData.getOldKey();
                    SecretKey newAppKey = m_settingsData.getSecretKey();
                    progressCallback.accept(new ProgressUpdate(0, 0, "Starting decryption and analysis..."));
                    
                    PipedOutputStream decryptedOutput = new PipedOutputStream();
                    PipedOutputStream reEncryptedOutput = new PipedOutputStream();
                    
                    CompletableFuture<NoteBytesObject> decryptFuture = 
                        FileStreamUtils.performDecryption(idDataFile, decryptedOutput, oldAppKey, execService);
                    
                    CompletableFuture<List<File>> parseAndUpdateFuture = 
                        NotePathFileDiscovery.parseDataStructureAndUpdateFilesWithStrategy(decryptedOutput, reEncryptedOutput, 
                                                                oldAppKey, newAppKey, config, progressCallback, execService);
                    
                    CompletableFuture<NoteBytesObject> saveFuture = 
                        FileStreamUtils.saveEncryptedFileSwap(idDataFile, newAppKey, reEncryptedOutput);
                    
                    return CompletableFuture.allOf(decryptFuture, parseAndUpdateFuture, saveFuture)
                        .thenCompose(v -> parseAndUpdateFuture.thenApply(updatedFiles -> 
                            NotePathFileDiscovery.createBulkUpdateResult(updatedFiles)))
                        .join();
                        
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process data file for bulk update", e);
                }
            }, execService);
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
