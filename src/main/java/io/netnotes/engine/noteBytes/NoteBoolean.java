package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteBoolean extends NoteBytes {
    public NoteBoolean(boolean value){
        super(ByteDecoding.booleanToBytes(value), NoteBytesMetaData.BOOLEAN_TYPE);
    }

    public NoteBoolean(byte[] bytes){
        super(bytes, NoteBytesMetaData.BOOLEAN_TYPE);
    }

}
