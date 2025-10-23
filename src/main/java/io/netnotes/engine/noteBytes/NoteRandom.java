package io.netnotes.engine.noteBytes;

import io.netnotes.engine.crypto.RandomService;

public class NoteRandom extends NoteBytes {
    private NoteRandom(byte[] bytes){
        super(bytes);
    }
    public NoteRandom(int length){
        super(getRandomBytes(length));
    }
    
    public static NoteRandom createRandom(int length){
        return new NoteRandom(getRandomBytes(length));
    }

    public static byte[] getRandomBytes(int length){
        return RandomService.getRandomBytes(length);
    }
}
