package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class NoteHex extends NoteBytes {
    public NoteHex(String hex){
        super(fromHexString(hex), NoteBytesMetaData.BASE_16_TYPE);
    }

    public NoteHex(byte[] bytes){
        super(bytes, NoteBytesMetaData.BASE_16_TYPE);
    }

    public static byte[] fromHexString(String hex){
        return ByteDecoding.stringToBytes(hex, NoteBytesMetaData.BASE_16_TYPE);
    }

}
