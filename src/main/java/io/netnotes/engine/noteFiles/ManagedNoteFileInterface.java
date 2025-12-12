package io.netnotes.engine.noteFiles;

import java.io.File;

import java.io.PipedOutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.task.TaskMessages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.notePath.NoteFileService;
import io.netnotes.engine.utils.LoggingHelpers.Log;

public class ManagedNoteFileInterface implements NoteFile.NoteFileInterface {
    private final File m_file;
    private final Semaphore m_semaphore = new Semaphore(1, true);
    private final AtomicBoolean m_locked = new AtomicBoolean(false);

    private final Map<NoteUUID, NoteFile> m_activeReferences = new ConcurrentHashMap<>();
    private final NoteFileService m_noteFileService;
    private final NoteStringArrayReadOnly m_pathKey;
    private final String path;
    private AtomicBoolean m_isClosed = new AtomicBoolean(false);
    
   public ManagedNoteFileInterface(NoteStringArrayReadOnly pathKey, NoteBytes noteFilePath, NoteFileService noteFileService ) {
        this.m_file = new File(noteFilePath.getAsString());
        this.m_noteFileService = noteFileService;
        this.m_pathKey = pathKey;
        this.path = pathKey.getAsString();   
    }

    public NoteStringArrayReadOnly getId(){
        return m_pathKey;
    }

    public void addReference(NoteFile noteFile) throws IllegalStateException {
      
        if(m_isClosed.get()){
            throw  new IllegalStateException("Interface is cloased");
        }
        m_activeReferences.putIfAbsent(noteFile.getId(), noteFile);    

    }
    


    public void removeReference(NoteUUID uuid) {
        m_activeReferences.remove(uuid);
        
        // If no more references and not locked, schedule cleanup
        if (m_activeReferences.isEmpty() && !isLocked()) {
            scheduleCleanup();
        }
    }
    
    public int getReferenceCount() {
        return m_activeReferences.size();
    }


    
    private void scheduleCleanup() {
        if(!m_isClosed.get()){
            CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS, getExecService())
            .execute(() -> {
                // Double-check cleanup conditions
                if (m_activeReferences.isEmpty() && !isLocked()) {
                    m_noteFileService.cleanupInterface(m_pathKey, this);
                }
            });
        }
    }
    

    @Override
    public void releaseLock() {
        if (m_locked.getAndSet(false)) {
            m_semaphore.release();
            
            // ADD: Check for cleanup after releasing lock
            if (m_activeReferences.isEmpty() && !m_isClosed.get()) {
                scheduleCleanup();
            }
        }
    }
    
    @Override
    public CompletableFuture<NoteBytesObject> readWriteFile(PipedOutputStream inParseStream, PipedOutputStream modifiedInParseStream) {
        if (!isLocked()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("readWriteEncryptedFile called without holding lock"));
        }
        if(m_isClosed.get()){
            return CompletableFuture.failedFuture(
                new IllegalStateException("readWriteEncryptedFile called while interface is cloased"));
        }
        
        // Start both operations concurrently
        // Read operation: decrypt file content into inParseStream
        //CompletableFuture<NoteBytesObject> readFuture = 
        decryptFile(inParseStream);
        // Write operation: encrypt from modifiedInParseStream to temp file, then swap
        CompletableFuture<NoteBytesObject> writeFuture =  this.m_noteFileService.saveEncryptedFileSwap(m_file, modifiedInParseStream);
        
        // Both operations run concurrently through the piped streams
        // The write will naturally wait for read data to be available through the pipes
        // Return the write future since it represents the completion of the entire operation
        return writeFuture;
    }
    
    @Override
    public ExecutorService getExecService() {
        return  this.m_noteFileService.getExecService();
    }
    
    @Override
    public CompletableFuture<NoteBytesObject> decryptFile(PipedOutputStream pipedOutput) {
        if (!isLocked()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("readWriteEncryptedFile called without holding lock"));
        }
        if(m_isClosed.get()){
            return CompletableFuture.failedFuture(
                new IllegalStateException("readWriteEncryptedFile called while interface is cloased"));
        }
        
       
        
        return  this.m_noteFileService.performDecryption(m_file, pipedOutput);
    }
    

    @Override
    public CompletableFuture<NoteBytesObject> saveEncryptedFileSwap(PipedOutputStream pipedOutputStream) {
        if (!isLocked()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("readWriteEncryptedFile called without holding lock"));
        }
        if(m_isClosed.get()){
            return CompletableFuture.failedFuture(
                new IllegalStateException("readWriteEncryptedFile called while interface is cloased"));
        }
        
        return this.m_noteFileService.saveEncryptedFileSwap(m_file, pipedOutputStream);
    }
    
    @Override
    public boolean isFile() {
        return m_file.isFile();
    }
    
    @Override
    public long fileSize() {
        return m_file.length();
    }
    
    @Override
    public CompletableFuture<Void> acquireLock() {
        return CompletableFuture.runAsync(() -> {
            if(!m_isClosed.get()){
                try {
                    m_semaphore.acquire();
                    m_locked.set(true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException("aquire lock interrupted", e);
                }
            }else{
                 throw new CompletionException("Inteface has been closed", new IllegalStateException(""));
            }
        }, getExecService());
    }
    
    @Override
    public boolean isClosed(){
        return m_isClosed.get();
    }
    
    @Override
    public boolean isLocked() {
        return m_locked.get();
    }
    
    @Override
    public CompletableFuture<Void> prepareForKeyUpdate() {
        return acquireLock(); // For key updates, we need exclusive access
    }

    public CompletableFuture<Void> perpareForShutdown(){
        return perpareForShutdown(null);
    }

    @Override
    public CompletableFuture<Void> perpareForShutdown(AsyncNoteBytesWriter writer){

        return acquireLock()
            .exceptionally(ex -> {
                String msg = "Aquiring a lock failed";
                if(writer != null){
                    TaskMessages.writeErrorAsync(path, msg, ex, writer);
                }
                Log.logError(msg + " for " + path);
                ex.printStackTrace();
                return null;
            }).thenApply((v)->{
            m_isClosed.set(true);
          
            Iterator<Map.Entry<NoteUUID, NoteFile>> iterator = m_activeReferences.entrySet().iterator();
            while(iterator.hasNext()) {
                Map.Entry<NoteUUID, NoteFile> entry = iterator.next();
                entry.getValue().forceClose();
                iterator.remove(); 
            }
            if(writer != null){
                writer.writeAsync(TaskMessages.createSuccessResult(path, NoteMessaging.Status.CLOSED));
            }
            return null;
        });

    }
    
    
    @Override
    public void completeKeyUpdate() {
        releaseLock();
    }
}

