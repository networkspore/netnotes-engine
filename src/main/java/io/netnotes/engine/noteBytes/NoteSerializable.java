package io.netnotes.engine.noteBytes;

import java.io.IOException;

public class NoteSerializable extends NoteBytes {
    public NoteSerializable(Object obj) throws IOException{
        super(ByteDecoding.serializeObject(obj), ByteDecoding.SERIALIZABLE_OBJECT);
    }

    
}
