package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteBase64 extends NoteBytes{
    public NoteBase64(byte[] bytes){
        super(bytes, NoteBytesMetaData.BASE_64_TYPE);
    }

    public NoteBase64(String string){
        super(ByteDecoding.stringToBytes(string, NoteBytesMetaData.BASE_64_TYPE), NoteBytesMetaData.BASE_64_TYPE);
    }


}
