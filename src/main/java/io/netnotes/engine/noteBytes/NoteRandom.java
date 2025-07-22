package io.netnotes.engine.noteBytes;

import io.netnotes.engine.Utils;

public class NoteRandom extends NoteBytes {
    public NoteRandom(int length){
        super(getRandomBytes(length));
    }

    public static byte[] getRandomBytes(int length){
        return Utils.getRandomBytes(length);
    }
}
