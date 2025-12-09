package io.netnotes.engine.io.input;

import java.util.function.Consumer;

import io.netnotes.engine.io.input.events.RoutedEvent;

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
 */
public interface InputDevice {
    
    /**
     * Add event consumer
     * Consumer will receive all input events from this device
     * 
     * @param id Unique consumer ID
     * @param consumer Event consumer (thread-safe)
     */
    void setEventConsumer(Consumer<RoutedEvent> consumer);
    
 
    /**
     * Get event consumer
     * 
     * @param id Consumer ID
     * @return Consumer, or null if not found
     */
    Consumer<RoutedEvent> getEventConsumer();
    
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