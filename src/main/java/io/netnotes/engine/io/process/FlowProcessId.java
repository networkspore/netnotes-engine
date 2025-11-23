package io.netnotes.engine.io.process;


import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.utils.AtomicSequence;

/**
 * ProcessId - Unique identifier for a process.
 * 
 * Can be converted to/from sourceId (NoteBytesReadOnly INTEGER)
 * for use with the routing registry.
 */
public final class FlowProcessId {
    private final long value;
    
    public FlowProcessId(long value) {
        this.value = value;
    }

    public FlowProcessId(){
        this.value = AtomicSequence.getNextSequenceLong();
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
    public long asLong() {
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
        return Long.hashCode(value);
    }

    public String asString(){
        byte[] bytes = ByteDecoding.longToBytesBigEndian(value);
        return ByteDecoding.bytesToUrlSafeString(bytes);
    }
    
    @Override
    public String toString() {
        return "ProcessId(" + asString() + ")";
    }
}