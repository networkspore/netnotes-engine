package io.netnotes.engine;

public class NoteBoolean extends NoteBytes {
    public NoteBoolean(boolean value){
        super(ByteDecoding.booleanToBytes(value));
    }


}
