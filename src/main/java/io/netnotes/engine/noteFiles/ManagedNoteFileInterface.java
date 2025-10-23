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

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteFiles.notePath.NoteFileService;

public class ManagedNoteFileInterface implements NoteFile.NoteFileInterface {
    private final File file;
    private final Semaphore semaphore = new Semaphore(1, true);
    private final AtomicBoolean locked = new AtomicBoolean(false);

    private final Map<NoteUUID, NoteFile> activeReferences = new ConcurrentHashMap<>();
    private final NoteFileService registry;
    private final NoteStringArrayReadOnly pathKey;
    private AtomicBoolean m_isClosed = new AtomicBoolean(false);
    
   public ManagedNoteFileInterface(NoteStringArrayReadOnly pathKey, NoteBytes noteFilePath, NoteFileService registry ) {
        this.file = new File(noteFilePath.getAsString());
        this.registry = registry;
        this.pathKey = pathKey;   
    }

    public NoteStringArrayReadOnly getId(){
        return pathKey;
    }

    public void addReference(NoteFile noteFile) throws IllegalStateException {
      
        if(m_isClosed.get()){
            throw  new IllegalStateException("Interface is cloased");
        }
        activeReferences.putIfAbsent(noteFile.getId(), noteFile);    

    }
    


    public void removeReference(NoteUUID uuid) {
        activeReferences.remove(uuid);
        
        // If no more references and not locked, schedule cleanup
        if (activeReferences.isEmpty() && !isLocked()) {
            scheduleCleanup();
        }
    }
    
    public int getReferenceCount() {
        return activeReferences.size();
    }


    
    private void scheduleCleanup() {
        if(!m_isClosed.get()){
            CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS, getExecService())
            .execute(() -> {
                // Double-check cleanup conditions
                if (activeReferences.isEmpty() && !isLocked()) {
                    registry.cleanupInterface(pathKey, this);
                }
            });
        }
    }
    

    @Override
    public void releaseLock() {
        if (locked.getAndSet(false)) {
            semaphore.release();
            
            // ADD: Check for cleanup after releasing lock
            if (activeReferences.isEmpty() && !m_isClosed.get()) {
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
        CompletableFuture<NoteBytesObject> writeFuture =  this.registry.saveEncryptedFileSwap(file, modifiedInParseStream);
        
        // Both operations run concurrently through the piped streams
        // The write will naturally wait for read data to be available through the pipes
        // Return the write future since it represents the completion of the entire operation
        return writeFuture;
    }
    
    @Override
    public ExecutorService getExecService() {
        return  this.registry.getExecService();
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
        
       
        
        return  this.registry.performDecryption(file, pipedOutput);
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
        
        return this.registry.saveEncryptedFileSwap(file, pipedOutputStream);
    }
    
    @Override
    public boolean isFile() {
        return file.isFile();
    }
    
    @Override
    public long fileSize() {
        return file.length();
    }
    
    @Override
    public CompletableFuture<Void> acquireLock() {
        return CompletableFuture.runAsync(() -> {
            if(!m_isClosed.get()){
                try {
                    semaphore.acquire();
                    locked.set(true);
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
        return locked.get();
    }
    
    @Override
    public CompletableFuture<Void> prepareForKeyUpdate() {
        return acquireLock(); // For key updates, we need exclusive access
    }

    @Override
    public CompletableFuture<Void> perpareForShutdown(){
      
        return acquireLock()
            .exceptionally(ex -> {
                System.err.println("Aquiring a lock failed for " + getId().getAsString()  + ": " + ex.getMessage());
                ex.printStackTrace();
                return null;
            }).thenApply((v)->{
            m_isClosed.set(true);
            Iterator<Map.Entry<NoteUUID, NoteFile>> iterator = activeReferences.entrySet().iterator();
            while(iterator.hasNext()) {
                Map.Entry<NoteUUID, NoteFile> entry = iterator.next();
                entry.getValue().forceClose();
                iterator.remove(); 
            }
            return null;
        });

    }
    
    
    @Override
    public void completeKeyUpdate() {
        releaseLock();
    }
}

