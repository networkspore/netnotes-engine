package io.netnotes.engine.noteBytes;

public class NoteDouble extends NoteBytes {
    public NoteDouble(double value){
        super(ByteDecoding.doubleToBytesBigEndian(value), ByteDecoding.DOUBLE);
    }


}
