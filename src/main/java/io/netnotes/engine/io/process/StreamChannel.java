package io.netnotes.engine.io.process;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

/**
 * StreamChannel - Unidirectional pipe communication between processes
 * 
 * FIXED: Now handles piped stream threading internally!
 * 
 * PipedOutputStream/PipedInputStream require the writer and reader to be on
 * separate threads to avoid deadlock. This class handles that internally with:
 * - Async write queue (sender writes to queue, dedicated thread writes to pipe)
 * - Async read handling (receiver gets data on dedicated thread)
 * 
 * Flow:
 * 1. Sender requests channel from registry
 * 2. Registry creates channel and routes to receiver
 * 3. Receiver calls startReceiving() - sets up read thread
 * 4. Receiver signals ready via readyFuture
 * 5. Sender gets channel and can start writing (writes go to queue)
 * 6. Internal write thread drains queue to pipe
 * 7. Receiver's read thread consumes from pipe
 * 
 * For bidirectional: create two separate channels (A→B and B→A)
 */
public class StreamChannel {
    
    private final ContextPath source;
    private final ContextPath target;
    private final PipedOutputStream pipeOutput;

    private final CompletableFuture<Void> readyFuture;
    
    // Async write infrastructure
    private BlockingQueue<byte[]> writeQueue = null;
    private ExecutorService writeExecutor = null;
    private volatile boolean active = false;
    private volatile boolean closed = false;
    
    // Wrapper output stream that writes to queue instead of directly to pipe
    private QueuedOutputStream queuedOutput = null;
    
    /**
     * Package-private constructor - only ProcessRegistry creates these
     */
    StreamChannel(ContextPath source, ContextPath target) throws IOException {
        this.source = source;
        this.target = target;
        this.pipeOutput = new PipedOutputStream();
  
        this.readyFuture = new CompletableFuture<>();
        
        Log.logMsg("[StreamChannel] Created: " + source + " → " + target);
    }

    public ExecutorService getWriteExecutor() {
        if (writeExecutor != null) return writeExecutor;
        this.writeExecutor = Executors.newSingleThreadExecutor(
            r -> Thread.ofVirtual().name("StreamChannel-Writer-" + source + "-to-" + target).unstarted(r)
        );
        return writeExecutor;
    }

    public OutputStream getQueuedOutputStream(){
        if (queuedOutput != null) return queuedOutput;
        this.writeQueue = new LinkedBlockingQueue<>();
        this.writeExecutor = Executors.newSingleThreadExecutor(
            r -> Thread.ofVirtual().name("StreamChannel-Writer-" + source + "-to-" + target).unstarted(r)
        );
        this.queuedOutput = new QueuedOutputStream();
        
        startWritePump();
     
        active = true; // Mark as active when first accessed
        return queuedOutput;
    }

    /**
     * Get raw input stream for advanced usage
     */
    public PipedOutputStream getChannelStream() {
        Log.logMsg("[StreamChannel] getStreamChannel called for " + source + " → " + target );
        active = true;
        return pipeOutput;
    }

    
    
    /**
     * Internal write pump - drains queue and writes to pipe on dedicated thread
     */
    private void startWritePump() {
        Log.logMsg("[StreamChannel] startWritePump called for " + source + " → " + target +"\n\t waiting for ready... readyFuture: ");
        readyFuture.thenRunAsync(()-> {
        Log.logMsg("[StreamChannel] readyFuture called for: " + source + " → " + target);

            try {
               while (!closed || (writeQueue != null && !writeQueue.isEmpty())) {
                    byte[] data = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (data != null) {
                        try {
                            pipeOutput.write(data);
                            pipeOutput.flush();
                        } catch (IOException e) {
                            Log.logError("[StreamChannel] Write pump error: " + e.getMessage());
                            break;
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.logMsg("[StreamChannel] Write pump interrupted");
            } finally {
                StreamUtils.safeClose(pipeOutput);
             
                Log.logMsg("[StreamChannel] Write pump stopped for " + source + " → " + target);
            }
        }, writeExecutor);
    }
    
    // ===== SENDER SIDE (Write) =====
    

    
    /**
     * OutputStream wrapper that queues writes instead of directly writing to pipe
     */
    private class QueuedOutputStream extends OutputStream {
        
        @Override
        public void write(int b) throws IOException {
            write(new byte[] { (byte) b });
        }
        
        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
            
            // Copy the relevant portion
            byte[] data = new byte[len];
            System.arraycopy(b, off, data, 0, len);
            
            // Queue for async write
            try {
                writeQueue.put(data);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Write interrupted", e);
            }
        }
        
        @Override
        public void flush() throws IOException {
            // No-op - write pump handles flushing
        }
        
        @Override
        public void close() throws IOException {
            StreamChannel.this.close();
        }
    }
    
    /**
     * Close sender side (signals EOF to receiver)
     */
    public void close() throws IOException {
        if (closed) return;
        
        Log.logMsg("[StreamChannel] Closing: " + source + " → " + target);
        closed = true;
        active = false;
        
        // Shutdown write executor (will finish draining queue)
        if(writeExecutor != null){
            writeExecutor.shutdown();
            try {
                writeExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                writeExecutor.shutdownNow();
            }
        }

   
    }
    
    
    // ===== STATUS =====
    
    public boolean isActive() {
        return active;
    }
    
    public boolean isClosed() {
        return closed;
    }
    
    public CompletableFuture<Void> getReadyFuture() {
        return readyFuture;
    }
    
    public ContextPath getSource() {
        return source;
    }
    
    public ContextPath getTarget() {
        return target;
    }


    @Override
    public String toString() {
        return String.format("StreamChannel{%s → %s, active=%s, closed=%s}",
            source, target, active, closed);
    }
}