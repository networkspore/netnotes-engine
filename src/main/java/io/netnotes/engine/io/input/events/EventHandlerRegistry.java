package io.netnotes.engine.io.input.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * EventHandlerRegistry - Efficient multi-handler event dispatch system
 * 
 * Features:
 * - Type-safe handlers via generics
 * - Multiple handlers per event type
 * - O(1) lookup via ConcurrentHashMap
 * - Thread-safe registration/unregistration
 * - Handler priority support (optional)
 * 
 * Usage:
 * <pre>
 * EventHandlerRegistry registry = new EventHandlerRegistry();
 * 
 * // Register handlers
 * registry.register(EventBytes.EVENT_CONTAINER_RESIZE, this::handleResize);
 * registry.register(EventBytes.EVENT_KEY_DOWN, this::handleKeyDown);
 * 
 * // Dispatch events
 * ContainerResizeEvent event = new ContainerResizeEvent(path, 80, 24);
 * registry.dispatch(event);
 * </pre>
 */
public class EventHandlerRegistry {
    
    /**
     * Map of event types to lists of handlers
     * ConcurrentHashMap for thread-safe registration
     * ArrayList for fast iteration during dispatch
     */
    private final Map<NoteBytesReadOnly, List<RoutedEventHandler>> handlers = 
        new ConcurrentHashMap<>();
    
    /**
     * Event handler wrapper with priority support
     */
    public static class RoutedEventHandler {
        final NoteBytesReadOnly id = NoteUUID.createLocalUUID64();
        final Consumer<RoutedEvent> handler;
        final int priority;
        boolean added = false;
        
        public RoutedEventHandler(Consumer<RoutedEvent> handler, int priority) {
            this.handler = handler;
            this.priority = priority;
        }

        public RoutedEventHandler(Consumer<RoutedEvent> handler) {
            this(handler, 0);
        }

        public NoteBytesReadOnly getId() {
            return id;
        }

        public Consumer<RoutedEvent> getHandler() {
            return handler;
        }

        public int getPriority() {
            return priority;
        }

        public boolean isAdded(){
            return this.added;
        }
    }

   
    
    /**
     * Register a handler for an event type (default priority 0)
     * 
     * @param eventType The event type constant from EventBytes
     * @param handlers The handler to invoke
     * @return This registry for chaining
     */
    public void register(NoteBytesReadOnly eventType, List<RoutedEventHandler> handlers) {
        for(RoutedEventHandler handler : handlers){
            register(eventType, handler);
        }
    }

    /**
     * Register a handler for an event type (default priority 0)
     * 
     * @param eventType The event type constant from EventBytes
     * @param handler The handler to invoke
     * @return This registry for chaining
     */
    public NoteBytesReadOnly register(NoteBytesReadOnly eventType, Consumer<RoutedEvent> handler) {
        return register(eventType, new RoutedEventHandler(handler, 0));
    }

    public NoteBytesReadOnly registerIfEmpty(NoteBytesReadOnly eventType, Consumer<RoutedEvent> handler) {
        List<RoutedEventHandler> handlers = getEventHandlers(eventType);
        if(handlers == null || handlers.isEmpty()){
            return register(eventType, new RoutedEventHandler(handler, 0));
        }else{
            return null;
        }
    }
    
    /**
     * Register a handler with priority (higher = called first)
     * 
     * @param eventType The event type constant from EventBytes
     * @param handler The handler to invoke
     * @param priority Handler priority (higher values called first)
     * @return This registry for chaining
     */
    public NoteBytesReadOnly register(
            NoteBytesReadOnly eventType,
            RoutedEventHandler wrapper) {
 
        handlers.compute(eventType, (k, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            
            // Insert in priority order (descending)
            int insertPos = 0;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).priority < wrapper.priority) {
                    insertPos = i;
                    break;
                }
                insertPos = i + 1;
            }
            
            list.add(insertPos, wrapper);
            wrapper.added = true;
            return list;
        });
        
        return wrapper.id;
    }

    public  List<RoutedEventHandler> getEventHandlers(NoteBytesReadOnly eventType){
        return handlers.get(eventType);
    }

    public Map<NoteBytesReadOnly, List<RoutedEventHandler>> getHandlersMap(){
        return handlers;
    }
    
    /**
     * Unregister a specific handler
     * 
     * @param eventType The event type
     * @param handler The handler to remove
     */
    public List<RoutedEventHandler> unregister(
            NoteBytesReadOnly eventType,
            Consumer<RoutedEvent> handler) {
        
        return handlers.computeIfPresent(eventType, (k, list) -> {
            list.removeIf(wrapper -> wrapper.handler == handler);
            return list.isEmpty() ? null : list;
        });
    }

     /**
     * Unregister a specific handler
     * 
     * @param eventType The event type
     * @param handler The handler to remove
     */
    public List<RoutedEventHandler> unregister(
            NoteBytesReadOnly eventType,
            RoutedEventHandler eventHander) {
        
        return handlers.computeIfPresent(eventType, (k, list) -> {
            list.removeIf(wrapper -> wrapper.id.equals(eventHander.id));
            return list.isEmpty() ? null : list;
        });
    }


    public List<RoutedEventHandler> unregister(NoteBytesReadOnly eventType) {
        return handlers.remove(eventType);
    }

     /**
     * Unregister a specific handler
     * 
     * @param eventType The event type
     * @param handler The handler to remove
     */
    public  List<RoutedEventHandler> unregister(
            NoteBytesReadOnly eventType,
            NoteBytesReadOnly id) {
        
        return handlers.computeIfPresent(eventType, (k, list) -> {
            list.removeIf(wrapper -> wrapper.id.equals(id));
            return list.isEmpty() ? null : list;
        });
    }
    


    /**
     * Clear all handlers
     */
    public void clear() {
        handlers.clear();
    }
    
    /**
     * Dispatch an event to all registered handlers
     * Encrypted packets are not dispatched
     * 
     * @param event The event to dispatch
     * @return true if any handlers were invoked
     */
    public boolean dispatch(RoutedEvent event ) {
   
        NoteBytes eventType = event.getEventTypeBytes();
        List<RoutedEventHandler> eventHandlers = handlers.get(eventType);
        
        if (eventHandlers == null || eventHandlers.isEmpty()) {
            return false;
        }
        
        // Invoke all handlers in priority order
        for (RoutedEventHandler wrapper : eventHandlers) {
            try {
                 wrapper.handler.accept(event);
                
                // Stop propagation if event is consumed
                if (EventBytes.StateFlags.hasFlag(
                        event.getStateFlags(), 
                        EventBytes.StateFlags.STATE_CONSUMED)) {
                    break;
                }
            } catch (Exception e) {
                Log.logError(String.format(
                    "[EventHandlerRegistry] Error dispatching %s: %s",
                    EventBytes.getEventName(eventType),
                    e.getMessage()
                ));
            }
        }
        
        return true;
    }
    
    /**
     * Check if any handlers are registered for an event type
     * 
     * @param eventType The event type to check
     * @return true if handlers exist
     */
    public boolean hasHandlers(NoteBytesReadOnly eventType) {
        List<RoutedEventHandler> list = handlers.get(eventType);
        return list != null && !list.isEmpty();
    }
    
    /**
     * Get the number of handlers for an event type
     * 
     * @param eventType The event type
     * @return Handler count
     */
    public int getHandlerCount(NoteBytesReadOnly eventType) {
        List<RoutedEventHandler> list = handlers.get(eventType);
        return list != null ? list.size() : 0;
    }
    
    /**
     * Get total number of registered handlers across all types
     * 
     * @return Total handler count
     */
    public int getTotalHandlerCount() {
        return handlers.values().stream()
            .mapToInt(List::size)
            .sum();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("EventHandlerRegistry[\n");
        
        handlers.forEach((type, list) -> {
            sb.append(String.format("  %s: %d handler(s)\n", 
                EventBytes.getEventName(type), list.size()));
        });
        
        sb.append("]");
        return sb.toString();
    }
}