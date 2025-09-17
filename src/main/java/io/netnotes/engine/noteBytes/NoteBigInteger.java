package io.netnotes.engine.noteBytes;

import java.math.BigInteger;

public class NoteBigInteger extends NoteBytes {
    public NoteBigInteger(BigInteger bigInt){
        super(bigInt.toByteArray(), ByteDecoding.BIG_INTEGER);
    }

}
