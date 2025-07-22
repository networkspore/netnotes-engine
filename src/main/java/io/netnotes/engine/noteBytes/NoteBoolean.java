package io.netnotes.engine.noteBytes;

import io.netnotes.engine.ByteDecoding;

public class NoteBoolean extends NoteBytes {
    public NoteBoolean(boolean value){
        super(ByteDecoding.booleanToBytes(value));
    }


}
