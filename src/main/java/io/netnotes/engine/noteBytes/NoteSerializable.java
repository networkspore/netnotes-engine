package io.netnotes.engine.noteBytes;

import java.io.IOException;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteSerializable extends NoteBytes {
    public NoteSerializable(Object obj) throws IOException{
        super(ByteDecoding.serializeObject(obj), NoteBytesMetaData.SERIALIZABLE_OBJECT_TYPE);
    }

    public NoteSerializable(byte[] bytes){
        super(bytes, NoteBytesMetaData.SERIALIZABLE_OBJECT_TYPE);
    }
    
}
