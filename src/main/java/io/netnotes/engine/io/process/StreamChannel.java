package io.netnotes.engine.io.process;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.utils.VirtualExecutors;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * StreamChannel - Unidirectional pipe communication between processes
 * 
 * Created by ProcessRegistry when one process requests a stream to another.
 * Handles all the piping coordination and provides clean write/read helpers.
 * 
 * Flow:
 * 1. Sender requests channel from registry
 * 2. Registry creates channel and routes to receiver
 * 3. Receiver sets up read thread
 * 4. Receiver signals ready
 * 5. Sender gets channel and can start writing
 * 
 * For bidirectional: create two separate channels (A→B and B→A)
 */
public class StreamChannel {
    
    private final ContextPath source;
    private final ContextPath target;
    private final PipedOutputStream senderOutput;
    private final PipedInputStream receiverInput;
    private final CompletableFuture<Void> readyFuture;
    
    private volatile boolean active = false;
    private volatile boolean closed = false;
    
    /**
     * Package-private constructor - only ProcessRegistry creates these
     */
    StreamChannel(ContextPath source, ContextPath target) throws IOException {
        this.source = source;
        this.target = target;
        this.senderOutput = new PipedOutputStream();
        this.receiverInput = new PipedInputStream(senderOutput);
        this.readyFuture = new CompletableFuture<>();
    }
    
    // ===== SENDER SIDE (Write) =====
    
    /**
     * Write data to channel
     * Only call after channel is ready
     */
    public void write(byte[] data) throws IOException {
        if (!active) {
            throw new IllegalStateException("Channel not ready yet");
        }
        if (closed) {
            throw new IllegalStateException("Channel closed");
        }
        senderOutput.write(data);
    }
    
    /**
     * Write and flush
     */
    public void writeAndFlush(byte[] data) throws IOException {
        write(data);
        senderOutput.flush();
    }
    
    /**
     * Get raw output stream for advanced usage
     
    public PipedOutputStream getStream() {
        if (!active) {
            throw new IllegalStateException("Channel not ready yet");
        }
        return senderOutput;
    }*/

    public OutputStream getOutputStream() {
        if (!active) {
            throw new IllegalStateException("Channel not ready yet");
        }
        return senderOutput;
    }

    
    /**
     * Execute writer when channel is ready
     */
    public CompletableFuture<Void> whenReady(StreamWriter writer) {
        return readyFuture.thenRun(() -> {
            try {
                writer.write(this);
            } catch (IOException e) {
                throw new RuntimeException("Stream write failed", e);
            }
        });
    }
    
    /**
     * Close sender side (signals EOF to receiver)
     */
    public void close() throws IOException {
        closed = true;
        senderOutput.close();
    }
    
    // ===== RECEIVER SIDE (Read) =====
    
    /**
     * Start receiving on dedicated thread
     * Call this from receiveStreamMessage implementation
     */
    public void startReceiving(StreamReader reader) {
        VirtualExecutors.getVirtualExecutor().execute(() -> {
            try {
                // Process stream
                reader.read(receiverInput);
                
            } catch (IOException e) {
                Log.logError("Stream read error: " + e.getMessage());
            } finally {
                try {
                    receiverInput.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        });
    }
    
    /**
     * Get raw input stream for advanced usage
     */
    public PipedInputStream getInputStream() {
        return receiverInput;
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
    
    // ===== FUNCTIONAL INTERFACES =====
    
    @FunctionalInterface
    public interface StreamWriter {
        void write(StreamChannel channel) throws IOException;
    }
    
    @FunctionalInterface
    public interface StreamReader {
        void read(PipedInputStream input) throws IOException;
    }
    
    @Override
    public String toString() {
        return String.format("StreamChannel{%s → %s, active=%s, closed=%s}",
            source, target, active, closed);
    }
}