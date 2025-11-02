package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteDouble extends NoteBytes {

    public static final NoteDouble ZERO = new NoteDouble(0.0);
    public static final NoteDouble ONE = new NoteDouble(1.0);

    public NoteDouble(double value){
        super(ByteDecoding.doubleToBytesBigEndian(value),  NoteBytesMetaData.DOUBLE_TYPE);
    }

    public NoteDouble(byte[] bytes){
        super(bytes, NoteBytesMetaData.DOUBLE_TYPE);
    }


}
