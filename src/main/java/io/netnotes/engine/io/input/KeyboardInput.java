package io.netnotes.engine.io.input;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.input.events.ExecutorConsumer;
import io.netnotes.engine.io.input.events.InputEventFactory;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
    private Map<String, ExecutorConsumer<RoutedEvent>> m_consumerMap = new ConcurrentHashMap<>();
    
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
        
        channel.getReadyFuture().complete(null);
        
        channel.startReceiving(input -> {
            System.out.println("GUI keyboard stream ready: " + contextPath);
            
            try (NoteBytesReader reader = new NoteBytesReader(input)) {
                NoteBytesReadOnly nextBytes = reader.nextNoteBytesReadOnly();
                
                while (nextBytes != null && active) {
                    // Check if this is sourceId prefix
                    if (nextBytes.getType() == NoteBytesMetaData.STRING_TYPE) {
                        
                        if (!nextBytes.equalsString(getName())) {
                            System.err.println("SourceId mismatch: expected " + 
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
                System.err.println("GUI keyboard stream error: " + e.getMessage());
                active = false;
            }
        });
    }
    
    /**
     * Emit event to all registered consumers
     * Same pattern as ClaimedDevice
     */
    private void emitEvent(RoutedEvent event) {
        m_consumerMap.forEach((k, consumer) -> consumer.accept(event));
    }
    
    @Override
    public void addEventConsumer(String id, ExecutorConsumer<RoutedEvent> eventConsumer) {
        m_consumerMap.computeIfAbsent(id, (k) -> eventConsumer);
    }
    
    @Override
    public ExecutorConsumer<RoutedEvent> removeEventConsumer(String id) {
        return m_consumerMap.remove(id);
    }
    
    @Override
    public ExecutorConsumer<RoutedEvent> getEventConsumer(String id) {
        return m_consumerMap.get(id);
    }
    
    
    @Override
    public void release() {
        active = false;
        m_consumerMap.clear();
        
        if (streamChannel != null) {
            try {
                streamChannel.close();
            } catch (IOException e) {
                System.err.println("Error closing stream: " + e.getMessage());
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