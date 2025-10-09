package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class NoteFloat extends NoteBytes{
    public NoteFloat(float _float){
        super(ByteDecoding.floatToBytesBigEndian(_float), NoteBytesMetaData.FLOAT_TYPE);
    }

    public NoteFloat(byte[] bytes){
        super(bytes, NoteBytesMetaData.FLOAT_TYPE);
    }
}
