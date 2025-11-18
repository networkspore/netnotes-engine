package io.netnotes.engine.io.process;


import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * ProcessId - Unique identifier for a process.
 * 
 * Can be converted to/from sourceId (NoteBytesReadOnly INTEGER)
 * for use with the routing registry.
 */
public final class FlowProcessId {
    private final int value;
    
    public FlowProcessId(int value) {
        this.value = value;
    }
    
    /**
     * Convert to sourceId for registry routing
     */
    public NoteBytesReadOnly asSourceId() {
        return new NoteBytesReadOnly(value);
    }
    
    /**
     * Create from sourceId
     */
    public static FlowProcessId fromSourceId(NoteBytesReadOnly sourceId) {
        return new FlowProcessId(sourceId.getAsInt());
    }
    
    /**
     * Get integer value
     */
    public int asInt() {
        return value;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FlowProcessId)) return false;
        return value == ((FlowProcessId) obj).value;
    }
    
    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }
    
    @Override
    public String toString() {
        return "ProcessId(" + value + ")";
    }
}