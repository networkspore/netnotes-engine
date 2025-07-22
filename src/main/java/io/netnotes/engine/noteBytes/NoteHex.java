package io.netnotes.engine.noteBytes;

import scorex.util.encode.Base16;

public class NoteHex extends NoteBytes {
    public NoteHex(String hex){
        super(fromHexString(hex));
    }

    public static byte[] fromHexString(String hex){
        return Base16.decode(hex).getOrElse(()->new byte[0]);
    }

}
