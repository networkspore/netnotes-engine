package io.netnotes.engine;

public class NoteRandom extends NoteBytes {
    public NoteRandom(int length){
        super(getRandomBytes(length));
    }

    public static byte[] getRandomBytes(int length){
        return Utils.getRandomBytes(length);
    }
}
