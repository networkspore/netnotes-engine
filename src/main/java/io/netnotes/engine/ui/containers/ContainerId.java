package io.netnotes.engine.ui.containers;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;

public class ContainerId {
    private final NoteBytesReadOnly id;
    private final String idString;
    private ContainerId(NoteBytesReadOnly id) {
        this.id = id;
        this.idString = id.toString();
    }
    
    public static ContainerId generate() {
        return new ContainerId(NoteUUID.createLocalUUID64());
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

    public String idString(){
        return idString;
    }
    
    @Override
    public String toString() {
        return idString;
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
