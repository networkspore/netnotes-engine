package io.netnotes.engine.noteBytes;

import java.math.BigInteger;

import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteBigInteger extends NoteBytes {
    public NoteBigInteger(BigInteger bigInt){
        super(bigInt.toByteArray(),  NoteBytesMetaData.BIG_INTEGER_TYPE);
    }
    public NoteBigInteger(byte[] bytes){
        super(bytes, NoteBytesMetaData.BIG_INTEGER_TYPE);
    }
}
