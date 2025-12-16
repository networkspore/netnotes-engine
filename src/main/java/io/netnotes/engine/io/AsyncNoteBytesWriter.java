package io.netnotes.engine.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;

public class AsyncNoteBytesWriter {
    private final NoteBytesWriter writer;
    private final BlockingQueue<WriteRequest> writeQueue = new LinkedBlockingQueue<>();
    private final ExecutorService writeExecutor;
    private volatile boolean running = true;
    private final String name;
    public AsyncNoteBytesWriter(OutputStream out) {
        this(null, out);
    }

    public AsyncNoteBytesWriter(String name, OutputStream out) {
        this.name = name == null
            ? "AsyncWriter-" + NoteUUID.getNextUUID64()
            : name;

        writeExecutor = Executors.newSingleThreadExecutor(
            r -> Thread.ofVirtual().name(name).unstarted(r)
        );
        this.writer = new NoteBytesWriter(out);
        startWriteLoop();
    }

    public String getName(){
        return name;
    }
    
    private void startWriteLoop() {
        writeExecutor.submit(() -> {
            try {
                while (running || !writeQueue.isEmpty()) {
                    WriteRequest request = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (request != null) {
                        try {
                            request.write(writer);
                            request.complete();
                        } catch (IOException e) {
                            request.completeExceptionally(e);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
    
    public CompletableFuture<Void> writeAsync(NoteBytesObject obj) {
        WriteRequest request = new SingleWriteRequest(obj);
        writeQueue.offer(request);
        return request.future;
    }
    
    public CompletableFuture<Void> writeAsync(NoteBytes bytes) {
        WriteRequest request = new SingleWriteRequest(bytes);
        writeQueue.offer(request);
        return request.future;
    }

    /**
     * Write routed message atomically: [STRING:deviceId][OBJECT:payload]
     * Guarantees no interleaving between deviceId and payload.
     */
    public CompletableFuture<Void> writeRoutedMessageAsync(String deviceId, NoteBytesObject payload) {
        WriteRequest request = new RoutedWriteRequest(deviceId, payload);
        writeQueue.offer(request);
        return request.future;
    }
    
    /**
     * Write routed message atomically: [STRING:deviceId][PAYLOAD]
     * For forwarding arbitrary payload types.
     */
    public CompletableFuture<Void> writeRoutedMessageAsync(String deviceId, NoteBytesReadOnly payload) {
        WriteRequest request = new RoutedWriteRequest(deviceId, payload);
        writeQueue.offer(request);
        return request.future;
    }

    
    public void writeSync(NoteBytesObject obj) throws IOException {
        try {
            writeAsync(obj).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Write interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("Write failed", e.getCause());
        }
    }

    public void writeRoutedMessageSync(String deviceId, NoteBytesObject payload) throws IOException {
        try {
            writeRoutedMessageAsync(deviceId, payload).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Write interrupted", e);
        } catch (ExecutionException e) {
            throw new IOException("Write failed", e.getCause());
        }
    }
    
    public void shutdown() {
        running = false;
        writeExecutor.shutdown();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return writeExecutor.awaitTermination(timeout, unit);
    }
    
    private abstract static class WriteRequest {
        protected final CompletableFuture<Void> future = new CompletableFuture<>();
        
        abstract void write(NoteBytesWriter writer) throws IOException;
        
        void complete() {
            future.complete(null);
        }
        
        void completeExceptionally(Throwable t) {
            future.completeExceptionally(t);
        }
    }

    /**
     * Single data write (non-routed)
     */
    private static class SingleWriteRequest extends WriteRequest {
        private final Object data;
        
        SingleWriteRequest(Object data) {
            this.data = data;
        }
        
        @Override
        void write(NoteBytesWriter writer) throws IOException {
            if (data instanceof NoteBytesObject) {
                writer.write((NoteBytesObject) data);
            } else if (data instanceof NoteBytes) {
                writer.write((NoteBytes) data);
            }
            writer.flush();
        }
    }
    
    /**
     * Routed message write - atomic [STRING:deviceId][payload]
     */
    private static class RoutedWriteRequest extends WriteRequest {
        private final String deviceId;
        private final Object payload;
        
        RoutedWriteRequest(String deviceId, Object payload) {
            this.deviceId = deviceId;
            this.payload = payload;
        }
        
        @Override
        void write(NoteBytesWriter writer) throws IOException {
            // Write both parts atomically
            writer.write(new NoteBytes(deviceId));
            
            if (payload instanceof NoteBytesObject) {
                writer.write((NoteBytesObject) payload);
            } else if (payload instanceof NoteBytesReadOnly) {
                writer.write((NoteBytesReadOnly) payload);
            } else if (payload instanceof NoteBytes) {
                writer.write((NoteBytes) payload);
            }
            
            writer.flush();
        }
    }
}