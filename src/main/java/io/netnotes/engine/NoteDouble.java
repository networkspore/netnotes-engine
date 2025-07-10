package io.netnotes.engine;

public class NoteDouble extends NoteBytes {
    public NoteDouble(double value){
        super(ByteDecoding.doubleToBytesBigEndian(value));
    }


}
