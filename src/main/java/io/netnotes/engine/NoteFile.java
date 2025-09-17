package io.netnotes.engine;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.messaging.TaskMessages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReader;
import io.netnotes.engine.noteBytes.NoteBytesTree;
import io.netnotes.engine.noteBytes.NoteBytesWriter;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.NoteJsonObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.ByteDecoding.NoteBytesMetaData;
import com.google.gson.JsonObject;

import io.netnotes.engine.utils.Utils;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;




/*
 // Reusable builder - no garbage created
OperationBuilder builder = noteFile.newOperation();

// Use multiple times
builder.readWrite(stream1, stream2).thenRun(() -> System.out.println("Op 1 done"));
builder.readWrite(stream3, stream4).thenRun(() -> System.out.println("Op 2 done"));

// Custom operations
builder.execute(fileInterface -> {
    // Your custom operation
    return fileInterface.decryptFile(customStream);
});
 */

public class NoteFile implements AutoCloseable  {

    private final NoteStringArrayReadOnly m_path;
    private final NoteFileInterface m_noteFileInterface;
    private final NoteBytes noteUUID;
    private boolean closed = false;
    
    public NoteFile(NoteStringArrayReadOnly path, ManagedNoteFileInterface noteFileInterface) { // Change to ManagedNoteFileInterface
        this.m_path = path;
        this.m_noteFileInterface = noteFileInterface;
        this.noteUUID = NoteUUID.createLocalUUID128ReadOnly();
        
        // ADD: Register this NoteFile with the interface
        noteFileInterface.addReference(noteUUID);
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("NoteFile has been closed");
        }
    }
    public void shutdown() {
        close();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            // Remove reference from the interface
            m_noteFileInterface.removeReference(noteUUID);
        }
    }



    public CompletableFuture<NoteBytesObject> readOnly(PipedOutputStream readOutput) {
        return m_noteFileInterface.acquireLock()
            .thenCompose(v -> {
                PipedOutputStream decryptedOutput = new PipedOutputStream();
                m_noteFileInterface.decryptFile(decryptedOutput);

                PipedOutputStream encryptOutput = new PipedOutputStream();
                try{
                    StreamUtils.pipedOutputDuplicate(decryptedOutput, readOutput, encryptOutput, null);

                    return m_noteFileInterface.encryptFile(encryptOutput);
                }catch(IOException e){
                    throw new RuntimeException("Duplication failed", e);
                }finally{
                    try{
                        encryptOutput.close();
                    }catch(IOException e){

                    }
                }
            }).whenComplete((result, throwable) -> m_noteFileInterface.releaseLock());
    }
    
    
 


    public CompletableFuture<NoteBytesObject> writeOnly(PipedOutputStream pipedOutput) {
        checkNotClosed();
        return m_noteFileInterface.acquireLock()
            .thenCompose(v -> m_noteFileInterface.encryptFile(pipedOutput))
            .whenComplete((result, throwable) -> m_noteFileInterface.releaseLock());
    }

    public CompletableFuture<NoteBytesObject> readWriteNoLock(PipedOutputStream inParseStream, PipedOutputStream modifiedInParseStream) {
        checkNotClosed();
        return m_noteFileInterface.readWriteFile(inParseStream, modifiedInParseStream);
    }
    
    // Functional approach inspired by Files.newOutputStream - no session objects created
    public <T> CompletableFuture<T> withExclusiveAccess(Function<NoteFileInterface, CompletableFuture<T>> operation) {
        checkNotClosed();
        return m_noteFileInterface.acquireLock()
            .thenCompose(v -> operation.apply(m_noteFileInterface))
            .whenComplete((result, throwable) -> m_noteFileInterface.releaseLock());
    }
    
    // Specialized read-write operation using functional approach
    public CompletableFuture<NoteBytesObject> readWriteLock(
            PipedOutputStream inParseStream, 
            PipedOutputStream modifiedInParseStream) {
        checkNotClosed();
        return withExclusiveAccess(fileInterface -> 
            fileInterface.readWriteFile(inParseStream, modifiedInParseStream));
    }
    
    public static CompletableFuture<NoteBytesObject> readWriteNoteFile(
            NoteFile noteFile,
            PipedOutputStream outStream, 
            PipedOutputStream modifiedStream) {
        return noteFile.readWriteNoLock(outStream, modifiedStream);
    }
    
    // Fluent builder-style API that creates minimal objects
    public OperationBuilder newOperation() {
        checkNotClosed();
        return new OperationBuilder(this);
    }
    
    // Lightweight builder that gets reused and doesn't hold state
    public static class OperationBuilder {
        private final NoteFile noteFile;
        
        OperationBuilder(NoteFile noteFile) {
            this.noteFile = noteFile;
        }
        
        public CompletableFuture<NoteBytesObject> readWrite(
                PipedOutputStream inParseStream,
                PipedOutputStream modifiedInParseStream) {
            return noteFile.readWriteNoLock(inParseStream, modifiedInParseStream);
        }
        
        public <T> CompletableFuture<T> execute(Function<NoteFileInterface, CompletableFuture<T>> operation) {
            return noteFile.withExclusiveAccess(operation);
        }
        
        // Reusable builder - can be used multiple times
        public OperationBuilder reset() {
            return this; // No state to reset
        }
    }
  
    public CompletableFuture<NoteBytesObject> performComplexOperation(
            Function<PipedOutputStream, CompletableFuture<Void>> readProcessor,
            Function<PipedOutputStream, CompletableFuture<NoteBytesObject>> writeProcessor) {
        
        return withExclusiveAccess(fileInterface -> {
            PipedOutputStream inStream = new PipedOutputStream();
            PipedOutputStream outStream = new PipedOutputStream();
            
            // Start read operation
            //CompletableFuture<NoteBytesObject> readFuture = 
            fileInterface.decryptFile(inStream);
            
            // Process the read stream
            //CompletableFuture<Void> processFuture = 
            readProcessor.apply(inStream);
            
            // Write the processed result
            return writeProcessor.apply(outStream);
        });
    }



    /*public Future<?> getFileBytes(){

    }*/
    
    // Getters

    public NoteBytes getNoteUUID() { return noteUUID; }
    public boolean isClosed() { return closed; }

    public NoteStringArrayReadOnly getPath() { return m_path; }

    public boolean isLocked(){
        return m_noteFileInterface.isLocked();
    }

    public ExecutorService getExecService(){
        return m_noteFileInterface.getExecService();
    }

    public boolean isFile(){
        return m_noteFileInterface.isFile();
    }

    public static URL getResourceURL(String resourceLocation){
        return resourceLocation != null ? Utils.class.getResource(resourceLocation) : null;
    }





    // helper: read exactly length bytes from reader, write them to writer (in chunks), return the collected bytes
    public static byte[] readAndForwardBytes(int length, NoteBytesReader reader, NoteBytesWriter writer, byte[] buffer) throws IOException {
        if (length <= 0) return new byte[0];
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(length, StreamUtils.BUFFER_SIZE));
        int remaining = length;
        while (remaining > 0) {
            int toRead = Math.min(buffer.length, remaining);
            int r = reader.read(buffer, 0, toRead);
            if (r == -1) throw new EOFException("Unexpected EOF while reading value");
            int actually = Math.min(r, remaining);
            baos.write(buffer, 0, actually);
            // forward the exact bytes read to the writer
            if (actually == buffer.length) {
                writer.write(buffer);
            } else {
                byte[] chunk = new byte[actually];
                System.arraycopy(buffer, 0, chunk, 0, actually);
                writer.write(chunk);
            }
            remaining -= actually;
            // if r > actually (shouldn't happen) remaining logic handles it
         }
         return baos.toByteArray();
     }
 /*

    public Future<?> getFileNoteBytesObject(EventHandler<WorkerStateEvent> onSucceeded,EventHandler<WorkerStateEvent> onRead,EventHandler<WorkerStateEvent> onWritten, EventHandler<WorkerStateEvent> onFailed){
        return getFileNoteBytes(NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE, onSucceeded, onRead, onWritten, onFailed);
    }

    public Future<?> getFileNoteBytesArray(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onRead,EventHandler<WorkerStateEvent> onWritten, EventHandler<WorkerStateEvent> onFailed){
        return getFileNoteBytes(NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE, onSucceeded,onRead, onWritten, onFailed);
    }

     public Future<?> getFileNoteBytesTree(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onRead,EventHandler<WorkerStateEvent> onWritten, EventHandler<WorkerStateEvent> onFailed){
        return getFileNoteBytes(NoteBytesMetaData.NOTE_BYTES_TREE_TYPE, onSucceeded,onRead, onWritten, onFailed);
    }
   
   public Future<?> getFileNoteBytes(byte noteBytesType, EventHandler<WorkerStateEvent> onSucceeded,EventHandler<WorkerStateEvent> onRead,EventHandler<WorkerStateEvent> onWritten, EventHandler<WorkerStateEvent> onFailed) {
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException {
                PipedOutputStream outParseStream = new PipedOutputStream();
                PipedOutputStream inParseStream = new PipedOutputStream();
                
                try {
                    readWriteBytes(inParseStream, outParseStream, onRead, onWritten, onFailed);
                    try (PipedInputStream inputStream = new PipedInputStream(inParseStream);
                         ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                        
                        byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
                        int length = 0;
                        while ((length = inputStream.read(buffer)) != -1) {
                            outParseStream.write(buffer, 0, length);
                            outParseStream.flush();
                            byteArrayOutputStream.write(buffer, 0, length);
                        }
                        byte[] bytes = byteArrayOutputStream.toByteArray();
                        switch (noteBytesType) {
                            case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                                return new NoteBytesObject(bytes);
                            case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                                return new NoteBytesArray(bytes);
                            case NoteBytesMetaData.NOTE_BYTES_TREE_TYPE:
                                return new NoteBytesTree(bytes);
                            default:
                                return new NoteBytes(bytes);
                        }
                    } finally {
                        
                    }
                } catch (Exception e) {
                    
                    throw e;
                }
            }
        };
        task.setOnFailed(onFailed);
        task.setOnSucceeded(onSucceeded);
        return getExecService().submit(task);
    }

  
    public Future<?> saveEncryptedFile(PipedOutputStream pipedOutputStream, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        return getNoteFileInterface().saveEncryptedFile(this, pipedOutputStream, onSucceeded, onFailed);
    }

    public Future<?> saveFileBytes(byte[] bytes, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        return getNoteFileInterface().saveEncryptedFile(this, bytes, onSucceeded, onFailed);
    }

    public Future<?> saveFileNoteBytes(NoteBytes noteBytes, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        return saveFileBytes(noteBytes.get(), onSucceeded, onFailed);
    }

    public Future<?> saveFileJson(JsonObject json, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        return saveFileNoteBytes(new NoteJsonObject(json), onSucceeded, onFailed);
    }
    */

    public interface NoteFileInterface{
        ExecutorService getExecService();
        boolean isFile();
        long fileSize();
        void addReference(NoteBytes noteFileUUID);
        void removeReference(NoteBytes noteFileUUID);
        int getReferenceCount();
        CompletableFuture<NoteBytesObject> decryptFile(PipedOutputStream pipedOutput) ;
        CompletableFuture<NoteBytesObject> encryptFile( PipedOutputStream pipedOutputStream);
        CompletableFuture<NoteBytesObject> readWriteFile(PipedOutputStream inParseStream, PipedOutputStream modifiedInParseStream);
        CompletableFuture<Void> acquireLock();
        void releaseLock();
        boolean isLocked();
        CompletableFuture<Void> prepareForKeyUpdate();
        void completeKeyUpdate();
    }
    
}
