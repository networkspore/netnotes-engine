package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;

public class NoteHex extends NoteBytes {
    public NoteHex(String hex){
        super(fromHexString(hex), ByteDecoding.HEX);
    }

    public static byte[] fromHexString(String hex){
        return ByteDecoding.decodeString(hex, ByteDecoding.BASE_16);
    }

}
