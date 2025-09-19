package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;

public class NoteLong extends NoteBytes{
    public NoteLong(long l){
        super(ByteDecoding.longToBytesBigEndian(l), ByteDecoding.LONG);
    }

    public NoteLong(byte[] bytes){
        super(bytes, ByteDecoding.LONG);
    }
}
