package io.netnotes.engine.noteBytes;

public class NoteHex extends NoteBytes {
    public NoteHex(String hex){
        super(fromHexString(hex), ByteDecoding.HEX);
    }

    public static byte[] fromHexString(String hex){
        return ByteDecoding.decodeString(hex, ByteDecoding.BASE_16);
    }

}
