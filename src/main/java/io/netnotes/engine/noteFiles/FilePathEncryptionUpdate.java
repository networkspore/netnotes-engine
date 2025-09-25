package io.netnotes.engine.noteFiles;

import java.io.File;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.SecretKey;

import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.messaging.task.TaskMessages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.LongCounter;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;

public class FilePathEncryptionUpdate {

    public static CompletableFuture<NoteBytesObject> updatePathLedgerEncryption(
        File pathLedger,
        SecretKey oldSecretKey, SecretKey newSecretKey, int batchSize,
        AsyncNoteBytesWriter progressWriter, ExecutorService execService) {
    
        return CompletableFuture
            .supplyAsync(() -> {
                
                if(
                    !pathLedger.exists() || 
                    !pathLedger.isFile() || 
                    pathLedger.length() < CryptoService.AES_IV_SIZE
                ){
                    throw new RuntimeException("Path ledger file is not valid");
                }
                
                if(oldSecretKey == null || newSecretKey == null){
                    throw new RuntimeException("Valid keys required");
                }

                if(progressWriter == null){
                    throw new RuntimeException("Progress update callback cannot be null");
                }
                    
                PipedOutputStream decryptedOutput = new PipedOutputStream();
                PipedOutputStream parsedOutput = new PipedOutputStream();
            
                try {
                    long ledgerLength = pathLedger.length();

                    // Chain the operations: decrypt -> parse -> encrypt -> return file
                    CompletableFuture<NoteBytesObject> decryptFuture = 
                        FileStreamUtils.performDecryption(pathLedger, decryptedOutput, oldSecretKey, execService);
                    
                    progressWriter.writeAsync(ProgressMessage.getProgressMessage("reCryptFiles",12, ledgerLength, "Decryption started"));
                    
                    CompletableFuture<Void> parseFuture = 
                        parseStreamUpdateEncryption(batchSize, ledgerLength, oldSecretKey, newSecretKey, decryptedOutput, parsedOutput, progressWriter, execService);
                    
                    CompletableFuture<NoteBytesObject> saveFuture = 
                        FileStreamUtils.saveEncryptedFileSwap(pathLedger, newSecretKey, parsedOutput);
                    
                    // Wait for all operations to complete and return the result file
                    return CompletableFuture.allOf(decryptFuture, parseFuture, saveFuture)
                        .thenCompose(v -> saveFuture) // Return the encrypted file info
                        .join(); // Block for this async operation
                    
                        
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process existing data file", e);
                }finally{
                    StreamUtils.safeClose(decryptedOutput);
                    StreamUtils.safeClose(parsedOutput);
                }
               
            }, execService);
    }

    public static CompletableFuture<Void> parseStreamUpdateEncryption(int batchSize, long fileSize,
        SecretKey oldKey, SecretKey newKey, PipedOutputStream decryptedOutput, PipedOutputStream parsedOutput,
        AsyncNoteBytesWriter progressWriter, ExecutorService execService
    ){
        return CompletableFuture
            .runAsync(() -> {
                Semaphore fileUpdateSemaphore = new Semaphore(batchSize, true);
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                try (PipedInputStream decryptedInput = new PipedInputStream(decryptedOutput);
                    NoteBytesReader reader = new NoteBytesReader(decryptedInput);
                    NoteBytesWriter writer = new NoteBytesWriter(parsedOutput)
                ) {

                    LongCounter byteCounter = new LongCounter(12);
                    
                    ProgressMessage.writeAsync(NoteMessaging.Status.STARTED,
                        12, fileSize, "Beginning file re-encryption", progressWriter);
                    
                    recursivelyParseQueueEncryption(futures, fileUpdateSemaphore, byteCounter, fileSize, 
                        fileSize + byteCounter.get(), fileSize + byteCounter.get(), oldKey, newKey, reader, writer, 
                        progressWriter, execService);
                    
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse and update data structure", e);
                }
                
                AtomicInteger completedCount = new AtomicInteger(0);
                int totalFutures = futures.size();

                CompletableFuture.allOf(
                    futures.stream()
                        .map(future -> future.whenComplete((result, ex) -> {
                            int completed = completedCount.incrementAndGet();
                            ProgressMessage.writeAsync(NoteMessaging.Status.UPDATED,
                                completed, totalFutures, NoteMessaging.General.SUCCESS, progressWriter);
                    })).toArray(CompletableFuture[]::new)
                ).join();

        }, execService);

    }


    public static void recursivelyParseQueueEncryption( List<CompletableFuture<Void>> futures, Semaphore fileUpdateSemaphore, LongCounter byteCounter, long fileSize,
        long rootSize, long levelsize, SecretKey oldKey, SecretKey newKey, NoteBytesReader reader, NoteBytesWriter writer, 
        AsyncNoteBytesWriter progressWriter, ExecutorService execService
    ) throws Exception {
       
        while(byteCounter.get() < rootSize && byteCounter.get() < levelsize){
            
            NoteBytes rootKey = reader.nextNoteBytes();
            if(rootKey == null){
                return;
            }
            byteCounter.add(writer.write(rootKey));
            if(rootKey.equals(NotePathFactory.FILE_PATH)){
                NoteBytes filePath = reader.nextNoteBytes();
                byteCounter.add(writer.write(filePath));
                futures.add(queueEncryptionUpdate(fileUpdateSemaphore, byteCounter, fileSize, oldKey, newKey, filePath, 
                    progressWriter, execService));
            }else{
                NoteBytesMetaData metaData = reader.nextMetaData();
                if(rootKey.getByteDecoding().getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
                    byteCounter.add(writer.write(metaData));
                    recursivelyParseQueueEncryption(futures, fileUpdateSemaphore, byteCounter, fileSize, byteCounter.get() + metaData.getLength(), 
                        byteCounter.get() + metaData.getLength(), oldKey, newKey, reader, writer, progressWriter,
                        execService);
                }else{
                    byteCounter.add(writer.write(metaData));
                    StreamUtils.readWriteNextBytes(metaData.getLength(), reader, writer);
                }
            }
            
        }
    }

    private static CompletableFuture<Void> queueEncryptionUpdate(Semaphore fileUpdateSemaphore, LongCounter byteCounter, 
        long fileSize, SecretKey oldKey, SecretKey newKey, NoteBytes filePath, AsyncNoteBytesWriter progressWriter,
        ExecutorService execService
    ) throws InterruptedException {
        return  CompletableFuture.runAsync(() -> {
            String filePathString = filePath.getAsString();
            File file = new File(filePathString);
            if(file.exists() && file.isFile() && file.length() > 12){
                try{
                    fileUpdateSemaphore.acquire();
                    try{
                        File tmpFile = new File(file.getAbsolutePath() + ".tmp");
                        ProgressMessage.writeAsync(NoteMessaging.General.INFO,
                            byteCounter.get(), fileSize, file.getAbsolutePath(), progressWriter);
                        
                        FileStreamUtils.updateFileEncryption(oldKey, newKey, file, tmpFile);
   
                    }catch(Exception e){
                        TaskMessages.writeErrorAsync(file.getAbsolutePath(), e.getMessage(), e, progressWriter);
                    }finally{
                        fileUpdateSemaphore.release();
                    }
                    return;
                }catch(InterruptedException e){
                    TaskMessages.writeErrorAsync(file.getAbsolutePath(), e.getMessage(), e, progressWriter);
                    return;    
                }
              
            }
        }, execService);

    } 
    
}
