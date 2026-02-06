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
 * EventHandlerRegistry - Efficient multi-handler event dispatch with filtering
 * 
 * Features:
 * - Type-safe handlers via generics
 * - Multiple handlers per event type
 * - Event filtering by type and source
 * - O(1) lookup via ConcurrentHashMap
 * - Thread-safe registration/unregistration
 * - Handler priority support
 * 
 * Usage:
 * <pre>
 * EventHandlerRegistry registry = new EventHandlerRegistry();
 * 
 * // Register handlers with filters
 * EventFilter filter = EventFilter.forSource(keyboardPath);
 * registry.register(EventBytes.EVENT_KEY_DOWN, this::handleKeyDown, filter);
 * 
 * // Register without filter (accepts all matching event types)
 * registry.register(EventBytes.EVENT_CONTAINER_RESIZE, this::handleResize);
 * 
 * // Dispatch events - filtering happens automatically
 * registry.dispatch(event);
 * </pre>
 */
public class EventHandlerRegistry {
    
    /**
     * Map of event types to lists of handlers
     */
    private final Map<NoteBytesReadOnly, List<RoutedEventHandler>> handlers = 
        new ConcurrentHashMap<>();
    
    /**
     * Event handler wrapper with priority and filtering
     */
    public static class RoutedEventHandler {
        final NoteBytesReadOnly id = NoteUUID.createLocalUUID64();
        final Consumer<RoutedEvent> handler;
        final int priority;
        final EventFilter filter;
        boolean added = false;
        
        public RoutedEventHandler(
                Consumer<RoutedEvent> handler, 
                int priority,
                EventFilter filter) {
            this.handler = handler;
            this.priority = priority;
            this.filter = filter != null ? filter : EventFilter.acceptAll();
        }

        public RoutedEventHandler(Consumer<RoutedEvent> handler) {
            this(handler, 0, null);
        }
        
        public RoutedEventHandler(Consumer<RoutedEvent> handler, EventFilter filter) {
            this(handler, 0, filter);
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
        
        public EventFilter getFilter() {
            return filter;
        }

        public boolean isAdded() {
            return this.added;
        }
        
        /**
         * Test if this handler accepts the event
         */
        public boolean accepts(RoutedEvent event) {
            return filter.test(event);
        }
    }
    
    /**
     * Register multiple handlers at once
     */
    public void register(NoteBytesReadOnly eventType, List<RoutedEventHandler> handlers) {
        for (RoutedEventHandler handler : handlers) {
            register(eventType, handler);
        }
    }

    /**
     * Register a handler (default priority, no filter)
     */
    public NoteBytesReadOnly register(
            NoteBytesReadOnly eventType, 
            Consumer<RoutedEvent> handler) {
        return register(eventType, new RoutedEventHandler(handler, 0, null));
    }
    
    /**
     * Register a handler with event filter
     */
    public NoteBytesReadOnly register(
            NoteBytesReadOnly eventType,
            Consumer<RoutedEvent> handler,
            EventFilter filter) {
        return register(eventType, new RoutedEventHandler(handler, 0, filter));
    }
    
    /**
     * Register a handler with priority and filter
     */
    public NoteBytesReadOnly register(
            NoteBytesReadOnly eventType,
            Consumer<RoutedEvent> handler,
            int priority,
            EventFilter filter) {
        return register(eventType, new RoutedEventHandler(handler, priority, filter));
    }

    /**
     * Register only if no handlers exist for this event type
     */
    public NoteBytesReadOnly registerIfEmpty(
            NoteBytesReadOnly eventType, 
            Consumer<RoutedEvent> handler) {
        List<RoutedEventHandler> handlers = getEventHandlers(eventType);
        if (handlers == null || handlers.isEmpty()) {
            return register(eventType, new RoutedEventHandler(handler, 0, null));
        }
        return null;
    }
    
    /**
     * Register a wrapped handler
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

    public List<RoutedEventHandler> getEventHandlers(NoteBytesReadOnly eventType) {
        return handlers.get(eventType);
    }

    public Map<NoteBytesReadOnly, List<RoutedEventHandler>> getHandlersMap() {
        return handlers;
    }
    
    /**
     * Unregister by handler reference
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
     * Unregister by wrapper reference
     */
    public List<RoutedEventHandler> unregister(
            NoteBytesReadOnly eventType,
            RoutedEventHandler eventHandler) {
        
        return handlers.computeIfPresent(eventType, (k, list) -> {
            list.removeIf(wrapper -> wrapper.id.equals(eventHandler.id));
            return list.isEmpty() ? null : list;
        });
    }

    /**
     * Unregister all handlers for event type
     */
    public List<RoutedEventHandler> unregister(NoteBytesReadOnly eventType) {
        return handlers.remove(eventType);
    }

    /**
     * Unregister by handler ID
     */
    public List<RoutedEventHandler> unregister(
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
     * Dispatch an event to all registered handlers that accept it
     * 
     * Handlers are invoked in priority order. If a handler's filter
     * rejects the event, that handler is skipped. Dispatch stops when
     * an event is consumed.
     * 
     * @param event The event to dispatch
     * @return true if any handlers were invoked
     */
    public boolean dispatch(RoutedEvent event) {
        NoteBytes eventType = event.getEventTypeBytes();
        List<RoutedEventHandler> eventHandlers = handlers.get(eventType);
        
        if (eventHandlers == null || eventHandlers.isEmpty()) {
            return false;
        }
        
        boolean anyInvoked = false;
        
        // Invoke handlers in priority order, checking filters
        for (RoutedEventHandler wrapper : eventHandlers) {
            // Check if handler accepts this event
            if (!wrapper.accepts(event)) {
                continue;
            }
            
            try {
                wrapper.handler.accept(event);
                anyInvoked = true;
                
                // Stop if event is consumed
                if (event.isConsumed()) {
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
        
        return anyInvoked;
    }
    
    /**
     * Check if any handlers are registered for an event type
     */
    public boolean hasHandlers(NoteBytesReadOnly eventType) {
        List<RoutedEventHandler> list = handlers.get(eventType);
        return list != null && !list.isEmpty();
    }
    
    /**
     * Get the number of handlers for an event type
     */
    public int getHandlerCount(NoteBytesReadOnly eventType) {
        List<RoutedEventHandler> list = handlers.get(eventType);
        return list != null ? list.size() : 0;
    }
    
    /**
     * Get total number of registered handlers across all types
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
            
            for (RoutedEventHandler handler : list) {
                sb.append(String.format("    - priority=%d, filter=%s\n",
                    handler.priority, handler.filter));
            }
        });
        
        sb.append("]");
        return sb.toString();
    }
}