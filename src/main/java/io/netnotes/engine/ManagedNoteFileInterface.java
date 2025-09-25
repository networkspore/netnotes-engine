package io.netnotes.engine;

import java.io.File;

import java.io.PipedOutputStream;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.noteFiles.NoteFileRegistry;

public class ManagedNoteFileInterface implements NoteFile.NoteFileInterface {
    private final File file;
    private final Semaphore semaphore = new Semaphore(1, true);
    private final AtomicBoolean locked = new AtomicBoolean(false);

    private final Set<NoteBytes> activeReferences = ConcurrentHashMap.newKeySet();
    private final NoteFileRegistry registry;
    private final NoteStringArrayReadOnly registryKey;
    
   public ManagedNoteFileInterface(NoteBytes noteFilePath, NoteFileRegistry registry, NoteStringArrayReadOnly registryKey) {
        this.file = new File(noteFilePath.getAsString());
        this.registry = registry;
        this.registryKey = registryKey;   
    }

    public void addReference(NoteBytes noteFileUUID) {
        activeReferences.add(noteFileUUID);
    }
    
    public void removeReference(NoteBytes noteFileUUID) {
        activeReferences.remove(noteFileUUID);
        
        // If no more references and not locked, schedule cleanup
        if (activeReferences.isEmpty() && !isLocked()) {
            scheduleCleanup();
        }
    }
    
    public int getReferenceCount() {
        return activeReferences.size();
    }
    
    private void scheduleCleanup() {
        // Schedule cleanup after a delay to avoid thrashing
        CompletableFuture.delayedExecutor(30, TimeUnit.SECONDS, getExecService())
            .execute(() -> {
                // Double-check cleanup conditions
                if (activeReferences.isEmpty() && !isLocked()) {
                    registry.cleanupInterface(registryKey, this);
                }
            });
    }
    

    @Override
    public void releaseLock() {
        if (locked.getAndSet(false)) {
            semaphore.release();
            
            // ADD: Check for cleanup after releasing lock
            if (activeReferences.isEmpty()) {
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
        // These low-level operations assume lock is already held
        return  this.registry.performDecryption(file, pipedOutput);
    }
    

    @Override
    public CompletableFuture<NoteBytesObject> encryptFile(PipedOutputStream pipedOutputStream) {
        // These low-level operations assume lock is already held
        return  this.registry.saveEncryptedFileSwap(file, pipedOutputStream);
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
            try {
                semaphore.acquire();
                locked.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }, getExecService());
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
    public void completeKeyUpdate() {
        releaseLock();
    }
}

