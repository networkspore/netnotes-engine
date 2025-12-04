package io.netnotes.engine.core.system.control.containers;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;

public class ContainerId {
    private final NoteBytesReadOnly id;
    
    private ContainerId(NoteBytesReadOnly id) {
        this.id = id;
    }
    
    public static ContainerId generate() {
        return new ContainerId(NoteUUID.createLocalUUID128());
    }
    
    public static ContainerId of(NoteBytesReadOnly id) {
        return new ContainerId(id);
    }
    
    public NoteBytesReadOnly toNoteBytes() {
        return id;
    }
    
    public static ContainerId fromNoteBytes(NoteBytes bytes) {
        return new ContainerId(bytes.readOnly());
    }
    
    @Override
    public String toString() {
        return id.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ContainerId other)) return false;
        return id.equals(other.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
