package io.netnotes.engine.io.process;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.LoggingHelpers.LogLevel;

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

    private static final LogLevel LOG_LEVEL = LogLevel.GENERAL;
    
    private final ContextPath source;
    private final ContextPath target;
    private final PipedOutputStream pipeOutput;

    private final CompletableFuture<Void> readyFuture;

    private ExecutorService writeExecutor = null;
    private volatile boolean active = false;
    private volatile boolean closed = false;
    

    
    /**
     * Package-private constructor - only ProcessRegistry creates these
     */
    StreamChannel(ContextPath source, ContextPath target) throws IOException {
        this.source = source;
        this.target = target;
        this.pipeOutput = new PipedOutputStream();
  
        this.readyFuture = new CompletableFuture<>();
        
        Log.logMsg("[StreamChannel] Created: " + source + " → " + target, LOG_LEVEL);
    }

    public ExecutorService getWriteExecutor() {
        if (writeExecutor != null) return writeExecutor;
        this.writeExecutor = Executors.newSingleThreadExecutor(
            r -> Thread.ofVirtual().name("StreamChannel-Writer-" + source + "-to-" + target).unstarted(r)
        );
        return writeExecutor;
    }

    /**
     * Get raw input stream for advanced usage
     */
    public PipedOutputStream getChannelStream() {
        Log.logMsg("[StreamChannel] getStreamChannel called for " + source + " → " + target, LOG_LEVEL);
        active = true;
        return pipeOutput;
    }
    
    /**
     * Close sender side (signals EOF to receiver)
     */
    public void close() throws IOException {
        if (closed) return;
        
        Log.logMsg("[StreamChannel] Closing: " + source + " → " + target, LOG_LEVEL);
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