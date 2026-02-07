package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.utils.noteBytes.NoteUUID;
import io.netnotes.noteBytes.NoteBytesReadOnly;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * EventFilter - Filter events by type and source
 * 
 * Allows filtering events in the EventHandlerRegistry to only dispatch
 * events matching specific criteria (event type and/or source path).
 * 
 * Usage:
 * <pre>
 * // Filter specific keyboard from specific source
 * EventFilter filter = EventFilter.builder()
 *     .eventType(EventBytes.EVENT_KEY_DOWN)
 *     .sourcePath(passwordKeyboardPath)
 *     .build();
 * 
 * registry.register(EventBytes.EVENT_KEY_DOWN, handler, filter);
 * 
 * // Filter any event from specific source
 * EventFilter sourceFilter = EventFilter.forSource(keyboardPath);
 * 
 * // Filter specific event type from any source
 * EventFilter typeFilter = EventFilter.forType(EventBytes.EVENT_KEY_DOWN);
 * </pre>
 */
public class EventFilter implements Predicate<RoutedEvent> {

    private final String id;
    private final NoteBytesReadOnly eventType;
    private final ContextPath sourcePath;
    private final Predicate<RoutedEvent> customPredicate;
    private final Class<? extends RoutedEvent> eventClass;
    private boolean enabled = true;
    
    private EventFilter(Builder builder) {
        this.id = builder.id;
        this.eventType = builder.eventType;
        this.sourcePath = builder.sourcePath;
        this.eventClass = builder.eventClass;
        this.customPredicate = builder.customPredicate;
        this.enabled = builder.enabled;
    }

    public String getId(){
        return id;
    }

    public boolean isEnabled(){
        return enabled;
    }
    
    @Override
    public boolean test(RoutedEvent event) {
        if (event == null) {
            return false;
        }

        if (eventClass != null && !eventClass.isInstance(event)) {
            return false;
        }
        
        // Check event type if specified
        if (eventType != null) {
            if (!eventType.equals(event.getEventTypeBytes())) {
                return false;
            }
        }
        
        // Check source path if specified
        if (sourcePath != null) {
            ContextPath eventSource = event.getSourcePath();
            if (eventSource == null || !sourcePath.equals(eventSource)) {
                return false;
            }
        }
        
        // Check custom predicate if specified
        if (customPredicate != null) {
            return customPredicate.test(event);
        }
        
        return true;
    }
    
    /**
     * Create filter for specific event type only
     */
    public static EventFilter forType(NoteBytesReadOnly eventType) {
        return builder().eventType(eventType).build();
    }
    
    /**
     * Create filter for specific source only
     */
    public static EventFilter forSource(ContextPath sourcePath) {
        return builder().sourcePath(sourcePath).build();
    }
    
    /**
     * Create filter for type and source
     */
    public static EventFilter forTypeAndSource(
            NoteBytesReadOnly eventType, 
            ContextPath sourcePath) {
        return builder()
            .eventType(eventType)
            .sourcePath(sourcePath)
            .build();
    }
    
    /**
     * Create filter that accepts all events
     */
    public static EventFilter acceptAll() {
        return builder().build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public NoteBytesReadOnly getEventType() {
        return eventType;
    }
    
    public ContextPath getSourcePath() {
        return sourcePath;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventFilter)) return false;
        EventFilter that = (EventFilter) o;
        return Objects.equals(eventType, that.eventType) &&
               Objects.equals(sourcePath, that.sourcePath);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(eventType, sourcePath);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("EventFilter[");
        if (eventType != null) {
            sb.append("type=").append(EventBytes.getEventName(eventType));
        }
        if (sourcePath != null) {
            if (eventType != null) sb.append(", ");
            sb.append("source=").append(sourcePath);
        }
        if (customPredicate != null) {
            if (eventType != null || sourcePath != null) sb.append(", ");
            sb.append("custom");
        }
        sb.append("]");
        return sb.toString();
    }
    

    public static class Builder {
        private String id = null;
        private NoteBytesReadOnly eventType;
        private ContextPath sourcePath;
        private Predicate<RoutedEvent> customPredicate;
        private Class<? extends RoutedEvent> eventClass;
        private boolean enabled = true;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder enabled(boolean enabled){
            this.enabled = enabled;
            return this;
        }

        public Builder eventType(NoteBytesReadOnly eventType) {
            this.eventType = eventType;
            return this;
        }
        
        public Builder sourcePath(ContextPath sourcePath) {
            this.sourcePath = sourcePath;
            return this;
        }
        
        public Builder customPredicate(Predicate<RoutedEvent> predicate) {
            this.customPredicate = predicate;
            return this;
        }

        public Builder eventClass(Class<? extends RoutedEvent> eventClass) {
            this.eventClass = eventClass;
            return this;
        }

        public EventFilter build() {
            if(id == null){
                id = NoteUUID.createSafeUUID64();
            }
            return new EventFilter(this);
        }
    }
}