package io.netnotes.engine.io.process;


import java.util.Objects;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteUUID;

/**
 * ProcessId - Unique identifier for a process.
 * 
 */
public final class FlowProcessId {
    private final ContextPath path;
    private final String uuid;

    public FlowProcessId(ContextPath path, String nodeUuid) {
        this.path = Objects.requireNonNull(path, "path cannot be null");
        this.uuid = nodeUuid;
    }

    public FlowProcessId(ContextPath path) {
        this(path, null);
    }

    public String getNextCorrelationId() { 
        return asString() + NoteUUID.createSafeUUID64();
       
    }
    
    public ContextPath getPath() {
        return path;
    }

    public String getUuid() {
        return uuid;
    }

    public boolean isDistributed() {
        return uuid != null;
    }
    
    
    /**
     * Full identity string
     * Local:       "/system/base/io-daemon"
     * Distributed: "node-abc123:/system/base/io-daemon"
     */
    public String asString() {
        if (uuid != null) {
            return uuid + ":" + path.toString();
        }
        return path.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FlowProcessId)) return false;
        FlowProcessId other = (FlowProcessId) obj;
        
        if (uuid != null) {
            // Distributed: must match both uuid and path
            return uuid.equals(other.uuid) && path.equals(other.path);
        } else {
            // Local: path is sufficient
            return path.equals(other.path);
        }
    }
    
    @Override
    public int hashCode() {
        return uuid != null 
            ? Objects.hash(uuid, path) 
            : path.hashCode();
    }
    
    @Override
    public String toString() {
        return asString();
    }
}