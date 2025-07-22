package io.netnotes.engine.noteBytes;

import io.netnotes.engine.ByteDecoding;

public class NoteDouble extends NoteBytes {
    public NoteDouble(double value){
        super(ByteDecoding.doubleToBytesBigEndian(value));
    }


}
