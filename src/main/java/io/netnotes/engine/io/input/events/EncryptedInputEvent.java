package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;

public class EncryptedInputEvent extends RoutedEvent {
    
    private final ContextPath sourcePath;
    private final NoteBytes encryptedPacket;
    private int stateFlags = 0;

    public EncryptedInputEvent(ContextPath sourcePath, NoteBytes encryptedPacket){
        this.sourcePath = sourcePath;
        this.encryptedPacket = encryptedPacket;
    }

    @Override
    public ContextPath getSourcePath(){ return sourcePath; }
    public NoteBytes getEncryptedPacket(){ return encryptedPacket; }

    @Override
    public NoteBytesReadOnly getEventTypeBytes() {
        return null;
    }

     @Override
    public int getStateFlags() {
        return stateFlags;
    }
    @Override
    public void setStateFlags(int flags) {
        stateFlags = flags;
    }
    
    
}
