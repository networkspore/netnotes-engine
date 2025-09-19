package io.netnotes.engine.noteBytes;

import java.math.BigInteger;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;

public class NoteBigInteger extends NoteBytes {
    public NoteBigInteger(BigInteger bigInt){
        super(bigInt.toByteArray(), ByteDecoding.BIG_INTEGER);
    }
    public NoteBigInteger(byte[] bytes){
        super(bytes, ByteDecoding.BIG_INTEGER);
    }
}
