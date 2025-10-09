package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class NoteLong extends NoteBytes{
    public NoteLong(long l){
        super(ByteDecoding.longToBytesBigEndian(l), NoteBytesMetaData.LONG_TYPE);
    }

    public NoteLong(byte[] bytes){
        super(bytes, NoteBytesMetaData.LONG_TYPE);
    }
}
