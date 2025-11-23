package io.netnotes.engine.io.input;

import io.netnotes.engine.io.events.ExecutorConsumer;
import io.netnotes.engine.io.events.RoutedEvent;

/**
 * InputDevice - Common interface for all input sources
 * 
 * Implemented by:
 * - ClaimedDevice (USB keyboard from IODaemon)
 * - BaseKeyboardInput (GUI keyboard fallback)
 * 
 * Contract:
 * 1. Receives input via StreamChannel
 * 2. Converts to RoutedEvents
 * 3. Emits to registered consumers
 * 4. Consumers are thread-safe (ExecutorConsumer)
 * 
 * Usage Pattern:
 * 
 *   // Create reader
 *   CompletableFuture<NoteBytesEphemeral> passwordFuture = new CompletableFuture<>();
 *   PasswordReader reader = new PasswordReader(passwordFuture);
 *   
 *   // Register with input device
 *   inputDevice.addEventConsumer("password-reader", reader.getEventConsumer());
 *   
 *   // Wait for password
 *   passwordFuture.thenAccept(password -> {
 *       try {
 *           // Use password
 *       } finally {
 *           password.close();
 *       }
 *   });
 *   
 *   // Cleanup
 *   inputDevice.removeEventConsumer("password-reader");
 */
public interface InputDevice {
    
    /**
     * Add event consumer
     * Consumer will receive all input events from this device
     * 
     * @param id Unique consumer ID
     * @param consumer Event consumer (thread-safe)
     */
    void addEventConsumer(String id, ExecutorConsumer<RoutedEvent> consumer);
    
    /**
     * Remove event consumer
     * 
     * @param id Consumer ID
     * @return Removed consumer, or null if not found
     */
    ExecutorConsumer<RoutedEvent> removeEventConsumer(String id);
    
    /**
     * Get event consumer
     * 
     * @param id Consumer ID
     * @return Consumer, or null if not found
     */
    ExecutorConsumer<RoutedEvent> getEventConsumer(String id);
    
    /**
     * Check if device is active
     * 
     * @return true if device is receiving input
     */
    boolean isActive();
    
    /**
     * Release device resources
     */
    void release();
}

/**
 * Example Implementation Pattern:
 * 
 * public class ClaimedDevice extends FlowProcess implements InputDevice {
 *     
 *     private Map<String, ExecutorConsumer<RoutedEvent>> m_consumerMap = new ConcurrentHashMap<>();
 *     private StreamChannel streamChannel;
 *     private volatile boolean active = false;
 *     
 *     @Override
 *     public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
 *         this.streamChannel = channel;
 *         this.active = true;
 *         
 *         channel.startReceiving(input -> {
 *             try (NoteBytesReader reader = new NoteBytesReader(input)) {
 *                 NoteBytesReadOnly nextBytes = reader.nextNoteBytesReadOnly();
 *                 
 *                 while (nextBytes != null && active) {
 *                     // Read sourceId prefix
 *                     if (nextBytes.getType() == STRING_TYPE) {
 *                         if (!nextBytes.equalsString(deviceId)) break;
 *                         
 *                         // Read payload
 *                         NoteBytesReadOnly payload = reader.nextNoteBytesReadOnly();
 *                         if (payload == null) break;
 *                         
 *                         // Convert to event and emit
 *                         emitEvent(InputEventFactory.from(contextPath, payload));
 *                     }
 *                     
 *                     nextBytes = reader.nextNoteBytesReadOnly();
 *                 }
 *             }
 *         });
 *     }
 *     
 *     private void emitEvent(RoutedEvent event) {
 *         m_consumerMap.forEach((k, consumer) -> consumer.accept(event));
 *     }
 *     
 *     @Override
 *     public void addEventConsumer(String id, ExecutorConsumer<RoutedEvent> consumer) {
 *         m_consumerMap.put(id, consumer);
 *     }
 *     
 *     @Override
 *     public ExecutorConsumer<RoutedEvent> removeEventConsumer(String id) {
 *         return m_consumerMap.remove(id);
 *     }
 *     
 *     @Override
 *     public ExecutorConsumer<RoutedEvent> getEventConsumer(String id) {
 *         return m_consumerMap.get(id);
 *     }
 *     
 *     @Override
 *     public boolean isActive() {
 *         return active;
 *     }
 *     
 *     @Override
 *     public void release() {
 *         active = false;
 *         m_consumerMap.clear();
 *         if (streamChannel != null) {
 *             streamChannel.close();
 *         }
 *     }
 * }
 */
