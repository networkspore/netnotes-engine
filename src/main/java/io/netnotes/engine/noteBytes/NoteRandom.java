package io.netnotes.engine.noteBytes;

import io.netnotes.engine.crypto.RandomService;

public class NoteRandom extends NoteBytes {
    public NoteRandom(int length){
        super(getRandomBytes(length));
    }

    public static byte[] getRandomBytes(int length){
        return RandomService.getRandomBytes(length);
    }
}
