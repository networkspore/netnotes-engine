package io.netnotes.engine.io.processing;


import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * ProcessId - Unique identifier for a process.
 * 
 * Can be converted to/from sourceId (NoteBytesReadOnly INTEGER)
 * for use with the routing registry.
 */
public final class ProcessId {
    private final int value;
    
    public ProcessId(int value) {
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
    public static ProcessId fromSourceId(NoteBytesReadOnly sourceId) {
        return new ProcessId(sourceId.getAsInt());
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
        if (!(obj instanceof ProcessId)) return false;
        return value == ((ProcessId) obj).value;
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