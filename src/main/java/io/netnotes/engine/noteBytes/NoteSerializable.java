package io.netnotes.engine.noteBytes;

import java.io.IOException;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;

public class NoteSerializable extends NoteBytes {
    public NoteSerializable(Object obj) throws IOException{
        super(ByteDecoding.serializeObject(obj), ByteDecoding.SERIALIZABLE_OBJECT);
    }

    public NoteSerializable(byte[] bytes){
        super(bytes, ByteDecoding.SERIALIZABLE_OBJECT);
    }
    
    public NoteSerializable(Byte[] bytes){
        super(ByteDecoding.unboxBytes(bytes), ByteDecoding.SERIALIZABLE_OBJECT);
    }
}
