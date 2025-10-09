package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class NoteDouble extends NoteBytes {
    public NoteDouble(double value){
        super(ByteDecoding.doubleToBytesBigEndian(value),  NoteBytesMetaData.DOUBLE_TYPE);
    }

    public NoteDouble(byte[] bytes){
        super(bytes, NoteBytesMetaData.DOUBLE_TYPE);
    }


}
