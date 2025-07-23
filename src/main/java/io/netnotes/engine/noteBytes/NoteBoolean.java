package io.netnotes.engine.noteBytes;

public class NoteBoolean extends NoteBytes {
    public NoteBoolean(boolean value){
        super(ByteDecoding.booleanToBytes(value));
    }


}
