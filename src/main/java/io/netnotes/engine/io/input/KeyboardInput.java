package io.netnotes.engine.io.input;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.input.events.InputEventFactory;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

import java.io.IOException;
import java.io.PipedInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * BaseKeyboardInput - GUI keyboard input source (fallback when no secure USB input)
 * 
 * Harmonized with ClaimedDevice:
 * - Receives input via StreamChannel
 * - Emits RoutedEvents through consumer pattern
 * - Implements InputDevice interface
 */
public class KeyboardInput extends FlowProcess implements InputDevice {
    
    private StreamChannel streamChannel;
    private volatile boolean active = false;
    
    // Event consumer pattern (same as ClaimedDevice)
    private Consumer<RoutedEvent> m_onEventConsumer = null;
    
    public KeyboardInput(String inputId) {
        super(inputId, ProcessType.SOURCE);
    }
    
    @Override
    public CompletableFuture<Void> run() {
        // Waits for stream channel to be established
        return getCompletionFuture();
    }
    
    /**
     * Handle stream channel from GUI input source
     * Same pattern as ClaimedDevice
     */
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        this.streamChannel = channel;
        this.active = true;
        
        
        VirtualExecutors.getVirtualExecutor().execute(() -> {
  
            Log.logMsg("GUI keyboard stream ready: " + contextPath);
            
            try (NoteBytesReader reader = new NoteBytesReader(new PipedInputStream(channel.getChannelStream(), StreamUtils.PIPE_BUFFER_SIZE))) {
                channel.getReadyFuture().complete(null);
                NoteBytesReadOnly nextBytes = reader.nextNoteBytesReadOnly();
                while (nextBytes != null && active) {
                    // Check if this is sourceId prefix
                    if (nextBytes.getType() == NoteBytesMetaData.STRING_TYPE) {
                        
                        if (!nextBytes.equalsString(getName())) {
                            Log.logError("SourceId mismatch: expected " + 
                                getName() + ", got " + nextBytes);
                            break;
                        }
                        
                        // Read payload
                        NoteBytesReadOnly payload = reader.nextNoteBytesReadOnly();
                        if (payload == null) break;
                        
                        // Convert to RoutedEvent and emit
                        emitEvent(InputEventFactory.from(contextPath, payload));
                    }
                    
                    nextBytes = reader.nextNoteBytesReadOnly();
                }
                
            } catch (IOException e) {
                Log.logError("GUI keyboard stream error: " + e.getMessage());
                active = false;
            }
        });
    }
    
    /**
     * Emit event to all registered consumers
     * Same pattern as ClaimedDevice
     */
    private void emitEvent(RoutedEvent event) {
        if(m_onEventConsumer != null){
            m_onEventConsumer.accept(event);
        }
    }
    
    @Override
    public void setEventConsumer(Consumer<RoutedEvent> eventConsumer) {
        m_onEventConsumer = eventConsumer;
    }
    
    
    @Override
    public Consumer<RoutedEvent> getEventConsumer() {
        return m_onEventConsumer;
    }
    
    
    @Override
    public void release() {
        active = false;
        m_onEventConsumer = null;
        
        if (streamChannel != null) {
            try {
                streamChannel.close();
            } catch (IOException e) {
                Log.logError("Error closing stream: " + e.getMessage());
            }
        }
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Source doesn't receive messages via packet routing
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== GETTERS =====
 
    
    public boolean isActive() {
        return active;
    }
}