package io.netnotes.engine.io.events;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public class EncryptedInputEvent implements InputEvent {
    
    private final NoteBytesReadOnly sourceId;
    private final NoteBytesReadOnly encryptedPacket;

    public EncryptedInputEvent(NoteBytesReadOnly sourceId, NoteBytesReadOnly encryptedPacket){
        this.sourceId = sourceId;
        this.encryptedPacket = encryptedPacket;
    }

    @Override
    public NoteBytesReadOnly getSourceId(){ return sourceId; }
    public NoteBytesReadOnly getEncryptedPacket(){ return encryptedPacket; }

}
