package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteLong extends NoteBytes{

    public final static NoteLong NEG_ONE = new NoteLong(-1L);
    public final static NoteLong ZERO = new NoteLong(0L);
    public final static NoteLong ONE = new NoteLong(1L);
    public final static NoteLong TWO = new NoteLong(2L);

    public NoteLong(long l){
        super(ByteDecoding.longToBytesBigEndian(l), NoteBytesMetaData.LONG_TYPE);
    }

    public NoteLong(byte[] bytes){
        super(bytes, NoteBytesMetaData.LONG_TYPE);
    }
}
