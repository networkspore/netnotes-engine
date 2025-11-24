package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteException extends NoteBytes {
    public NoteException(Throwable obj) {
        super(ByteDecoding.serializeException(obj), NoteBytesMetaData.SERIALIZABLE_OBJECT_TYPE);
    }

    public NoteException(byte[] bytes){
        super(bytes, NoteBytesMetaData.SERIALIZABLE_OBJECT_TYPE);
    }
}
