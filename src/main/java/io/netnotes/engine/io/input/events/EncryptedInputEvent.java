package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public class EncryptedInputEvent implements RoutedEvent {
    
    private final ContextPath sourcePath;
    private final NoteBytesReadOnly encryptedPacket;

    public EncryptedInputEvent(ContextPath sourcePath, NoteBytesReadOnly encryptedPacket){
        this.sourcePath = sourcePath;
        this.encryptedPacket = encryptedPacket;
    }

    @Override
    public ContextPath getSourcePath(){ return sourcePath; }
    public NoteBytesReadOnly getEncryptedPacket(){ return encryptedPacket; }

}
