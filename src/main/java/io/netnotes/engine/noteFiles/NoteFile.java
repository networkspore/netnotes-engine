package io.netnotes.engine.noteFiles;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;

import io.netnotes.engine.utils.CollectionHelpers;
import io.netnotes.engine.utils.VirtualExecutors;
import io.netnotes.engine.utils.streams.StreamUtils;




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

    private final NoteStringArrayReadOnly m_notePath;
    private final NoteFileInterface m_noteFileInterface;
    private final NoteUUID noteUUID;
    private final String pathString;
    private AtomicBoolean closed = new AtomicBoolean(false);
    
    public NoteFile(NoteStringArrayReadOnly notePath, ManagedNoteFileInterface noteFileInterface) throws IllegalStateException {
        this.m_notePath = notePath;
        this.m_noteFileInterface = noteFileInterface;
        this.noteUUID = NoteUUID.createLocalUUID128();
       
        this.pathString = notePath.getAsString();
       
        noteFileInterface.addReference(this);
    }


    public String getUrlPathString(){
        return pathString;
    }

    public NoteUUID getId(){
        return noteUUID;
    }

    private void checkNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("NoteFile has been closed");
        }
    }
    public void shutdown() {
        close();
    }

    @Override
    public void close() {
        if (!closed.getAndSet(true)) {
            // Remove reference from the interface
            m_noteFileInterface.removeReference(noteUUID);
        }
    }

    public void forceClose(){
        closed.set(true);
    }

    public CompletableFuture<Void> readOnly(PipedOutputStream readOutput) {
        checkNotClosed();
        return m_noteFileInterface.acquireLock()
            .thenApply(v -> {
                PipedOutputStream decryptedOutput = new PipedOutputStream();
                CompletableFuture<NoteBytesObject> decryptFuture = m_noteFileInterface.decryptFile(decryptedOutput);

                PipedOutputStream encryptOutput = new PipedOutputStream();
              
                CompletableFuture<Void> voidFuture = 
                    StreamUtils.duplicateEntireStream(decryptedOutput, readOutput, encryptOutput, null);
                    
                CompletableFuture<NoteBytesObject> encryptFuture = 
                    m_noteFileInterface.saveEncryptedFileSwap(encryptOutput);
           
                return CompletableFuture.allOf(decryptFuture,voidFuture, encryptFuture);
               
            }).thenAccept(v -> m_noteFileInterface.releaseLock());
    }

    /**
     * retrieves entire bucket if written with CompletableFuture<Void> write(NoteBytes noteBytes) 
     * otherwise retrieves first notebytes in bucket, based on initial 5 bytes metadata 
     * Note: write(NoteBytes.get()) will not write initial metadata
     * @return next NoteBytes
     */
    public CompletableFuture<NoteBytes> readNoteBytes() {
        PipedOutputStream outputStream = new PipedOutputStream();
        CompletableFuture<Void> readFuture = readOnly(outputStream);

        CompletableFuture<NoteBytes> readNoteBytesFuture = CompletableFuture.supplyAsync(()->{
            try(
                NoteBytesReader reader = new NoteBytesReader(new PipedInputStream(outputStream, StreamUtils.PIPE_BUFFER_SIZE));
            ){
                return reader.nextNoteBytes();
            } catch (IOException e) {
                throw new CompletionException("Failed to read", e);
            }
        }, VirtualExecutors.getVirtualExecutor());

        return CompletableFuture.allOf(readFuture, readNoteBytesFuture).thenCompose((v)->readNoteBytesFuture);
    }


    /***
     * reads all bytes from file
     * @return 
     */
    public CompletableFuture<byte[]> readBytes() {
        PipedOutputStream outputStream = new PipedOutputStream();
        CompletableFuture<Void> readFuture = readOnly(outputStream);

         CompletableFuture<byte[]> readByteFuture = CompletableFuture.supplyAsync(()->{
            try(
                PipedInputStream inputStream = new PipedInputStream(outputStream, StreamUtils.PIPE_BUFFER_SIZE);
                UnsynchronizedByteArrayOutputStream byteStream = new UnsynchronizedByteArrayOutputStream((int)m_noteFileInterface.fileSize());
            ){
                inputStream.transferTo(byteStream);
                return byteStream.toByteArray();
            } catch (IOException e) {
                throw new CompletionException("Failed to read", e);
            }
        }, VirtualExecutors.getVirtualExecutor());

        return CompletableFuture.allOf(readFuture, readByteFuture).thenCompose((v)->readByteFuture);
    }
 


    public CompletableFuture<NoteBytesObject> writeOnly(PipedOutputStream pipedOutput) {
        checkNotClosed();
        return m_noteFileInterface.acquireLock()
            .thenCompose(v -> m_noteFileInterface.saveEncryptedFileSwap(pipedOutput))
            .whenComplete((result, throwable) -> m_noteFileInterface.releaseLock());
    }

    public CompletableFuture<Void> write(NoteBytesMap noteBytesMap) {
        return write(noteBytesMap.getNoteBytesObject());
    }
    /**
     * Appends initial 5 bytes of metadata, then writes value
     * @param noteBytes
     * @return
     */
    public CompletableFuture<Void> write(NoteBytes noteBytes) {
        PipedOutputStream outputStream = new PipedOutputStream();
        
        CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(()->{
            try(NoteBytesWriter writer = new NoteBytesWriter(outputStream)){
                writer.write(noteBytes);
            }catch(IOException e){
                throw new CompletionException(e);
            }
        }, VirtualExecutors.getVirtualExecutor());

        CompletableFuture<NoteBytesObject> writeOnlyFuture = writeOnly(outputStream);

        return CompletableFuture.allOf(writeFuture, writeOnlyFuture);
    }

    public CompletableFuture<Void> write(byte[] bytes) {
        PipedOutputStream outputStream = new PipedOutputStream();
        
        CompletableFuture<Void> writeFuture = CompletableFuture.runAsync(()->{
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, VirtualExecutors.getVirtualExecutor());

        CompletableFuture<NoteBytesObject> writeOnlyFuture = writeOnly(outputStream);

        return CompletableFuture.allOf(writeFuture, writeOnlyFuture);
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
    public CompletableFuture<NoteBytesObject> readWrite(
            PipedOutputStream inParseStream, 
            PipedOutputStream modifiedInParseStream) {
     
        return withExclusiveAccess(fileInterface -> 
            fileInterface.readWriteFile(inParseStream, modifiedInParseStream));
    }
    
    public static CompletableFuture<NoteBytesObject> readWriteNoteFile(NoteFile noteFile, PipedOutputStream outStream, 
        PipedOutputStream modifiedStream
    ) {

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



    /**
     * Get InputStream for reading file contents
     * 
     * This enables stream-based access for consumers like OSGi that need
     * to read bundles without materializing entire file in memory.
     * 
     * The stream is decrypted on-the-fly as bytes are read.
     * 
     * LIFECYCLE:
     * - Caller MUST close the returned InputStream when done
     * - Closing the stream also closes internal pipes and waits for decryption
     * - If error occurs during read, IOException is thrown to caller
     * 
     * RESOURCE MANAGEMENT:
     * - PipedOutputStream/InputStream are created and linked
     * - readOnly() starts async decryption in background
     * - Stream wrapper ensures proper cleanup in close()
     * 
     * @return InputStream that reads decrypted file contents
     * @throws IOException if stream setup fails
     */
    public InputStream getInputStream() throws IOException {
        checkNotClosed();
        
        // Create piped streams for async decryption
        PipedOutputStream decryptOutput = new PipedOutputStream();
        PipedInputStream decryptInput = new PipedInputStream(
            decryptOutput,
            StreamUtils.PIPE_BUFFER_SIZE
        );
        
        // Start async decryption in background
        // readOnly() handles: decrypt → duplicate → re-encrypt → swap
        CompletableFuture<Void> decryptFuture = readOnly(decryptOutput)
            .exceptionally(ex -> {
                // On decryption error, close streams to signal failure
                StreamUtils.safeClose(decryptInput);
                StreamUtils.safeClose(decryptOutput);
                System.err.println("[NoteFile] Decryption error for " + 
                    pathString + ": " + ex.getMessage());
                return null;
            });
        
        // Wrap in InputStream that ensures proper cleanup
        return new InputStream() {
            private final AtomicBoolean streamClosed = new AtomicBoolean(false);
            
            @Override
            public int read() throws IOException {
                if (streamClosed.get()) {
                    throw new IOException("Stream closed");
                }
                
                try {
                    return decryptInput.read();
                } catch (IOException e) {
                    // Check if decryption failed
                    if (decryptFuture.isCompletedExceptionally()) {
                        throw new IOException("Decryption failed", 
                            decryptFuture.handle((v, ex) -> ex).join());
                    }
                    throw e;
                }
            }
            
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (streamClosed.get()) {
                    throw new IOException("Stream closed");
                }
                
                try {
                    return decryptInput.read(b, off, len);
                } catch (IOException e) {
                    // Check if decryption failed
                    if (decryptFuture.isCompletedExceptionally()) {
                        throw new IOException("Decryption failed", 
                            decryptFuture.handle((v, ex) -> ex).join());
                    }
                    throw e;
                }
            }
            
            @Override
            public void close() throws IOException {
                if (streamClosed.compareAndSet(false, true)) {
                    try {
                        // Close input stream first (stops reading)
                        decryptInput.close();
                        
                        // Close output stream (stops writing)
                        decryptOutput.close();
                        
                        // Wait for background decryption to complete
                        // This ensures file swap completes properly
                        try {
                            decryptFuture.join();
                        } catch (Exception e) {
                            // If decryption failed, wrap in IOException
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            throw new IOException("Decryption did not complete successfully", cause);
                        }
                        
                    } catch (IOException e) {
                        // Ensure streams are closed even if error occurs
                        StreamUtils.safeClose(decryptInput);
                        StreamUtils.safeClose(decryptOutput);
                        throw e;
                    }
                }
            }
            
            @Override
            public int available() throws IOException {
                if (streamClosed.get()) {
                    return 0;
                }
                return decryptInput.available();
            }
            
            @Override
            public long skip(long n) throws IOException {
                if (streamClosed.get()) {
                    throw new IOException("Stream closed");
                }
                return decryptInput.skip(n);
            }
            
            @Override
            public synchronized void mark(int readlimit) {
                // PipedInputStream doesn't support mark/reset
            }
            
            @Override
            public synchronized void reset() throws IOException {
                throw new IOException("mark/reset not supported");
            }
            
            @Override
            public boolean markSupported() {
                return false;
            }
        };
    }

    public OutputStream getOutputStream() throws IOException {
        checkNotClosed();


        PipedOutputStream pipeOut = new PipedOutputStream();

        // Launch async write pipeline
        CompletableFuture<NoteBytesObject> writeFuture = writeOnly(pipeOut)
            .exceptionally(ex -> {
                StreamUtils.safeClose(pipeOut);
                System.err.println("[NoteFile] Write/encrypt error for " +
                    pathString + ": " + ex.getMessage());
                return null;
            });

        // Wrap output so we can intercept close() and errors
        return new OutputStream() {
            private final AtomicBoolean closed = new AtomicBoolean(false);

            @Override
            public void write(int b) throws IOException {
                ensureOpen();
                try {
                    pipeOut.write(b);
                } catch (IOException e) {
                    rethrowWriteError(e);
                }
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                ensureOpen();
                try {
                    pipeOut.write(b, off, len);
                } catch (IOException e) {
                    rethrowWriteError(e);
                }
            }

            private void rethrowWriteError(IOException e) throws IOException {
                if (writeFuture.isCompletedExceptionally()) {
                    Throwable cause = writeFuture.handle((v, ex) -> ex).join();
                    throw new IOException("Async write failed", cause);
                }
                throw e;
            }

            private void ensureOpen() throws IOException {
                if (closed.get()) {
                    throw new IOException("Stream closed");
                }
            }

            @Override
            public void flush() throws IOException {
                ensureOpen();
                pipeOut.flush();
            }

            @Override
            public void close() throws IOException {
                if (closed.compareAndSet(false, true)) {
                    try {
                        pipeOut.close(); // signals EOF to writer
                        try {
                            writeFuture.join();
                        } catch (Exception e) {
                            Throwable cause = e.getCause() != null ? e.getCause() : e;
                            throw new IOException("Async write did not complete successfully", cause);
                        }
                    } finally {
                        StreamUtils.safeClose(pipeOut);
                    }
                }
            }
        };
    }


    public boolean isClosed() { return closed.get(); }

    public NoteStringArrayReadOnly getPath() { return m_notePath; }

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
        return resourceLocation != null ? CollectionHelpers.class.getResource(resourceLocation) : null;
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
        void addReference(NoteFile noteFile);
        void removeReference(NoteUUID noteUUID);
        int getReferenceCount();
        CompletableFuture<NoteBytesObject> decryptFile(PipedOutputStream pipedOutput) ;
        CompletableFuture<NoteBytesObject> saveEncryptedFileSwap( PipedOutputStream pipedOutputStream);
        CompletableFuture<NoteBytesObject> readWriteFile(PipedOutputStream inParseStream, PipedOutputStream modifiedInParseStream);
        CompletableFuture<Void> acquireLock();
        void releaseLock();
        boolean isLocked();
        boolean isClosed();
        CompletableFuture<Void> perpareForShutdown(AsyncNoteBytesWriter progressWriter);
        CompletableFuture<Void> prepareForKeyUpdate();
        void completeKeyUpdate();
    }
    
}
