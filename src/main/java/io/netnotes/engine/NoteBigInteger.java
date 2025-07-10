package io.netnotes.engine;

import java.math.BigInteger;

public class NoteBigInteger extends NoteBytes {
    public NoteBigInteger(BigInteger bigInt){
        super(bigInt.toByteArray());
    }

}
