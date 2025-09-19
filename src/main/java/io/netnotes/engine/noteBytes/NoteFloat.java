package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;

public class NoteFloat extends NoteBytes{
    public NoteFloat(float _float){
        super(ByteDecoding.floatToBytesBigEndian(_float), ByteDecoding.FLOAT);
    }

    public NoteFloat(byte[] bytes){
        super(bytes, ByteDecoding.FLOAT);
    }
}
