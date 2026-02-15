package io.netnotes.engine.io.input;


import io.netnotes.engine.io.input.events.EventHandlerRegistry;

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
    

    EventHandlerRegistry getEventHandlerRegistry();
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