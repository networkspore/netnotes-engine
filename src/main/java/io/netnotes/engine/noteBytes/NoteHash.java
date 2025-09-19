package io.netnotes.engine.noteBytes;

import java.math.BigInteger;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteHashing;

public class NoteHash extends NoteBytes {

    private int m_hashSize = 32;
    private byte[] m_hash;
    public byte[] getHash() {
        return m_hash;
    }

    public void setHash(byte[] hash) {
        this.m_hash = hash;
    }

    public int getHashDigestSize() {
        return m_hashSize;
    }

    public void setHashDigestSize(int hashDigestSize) {
        this.m_hashSize = hashDigestSize;
    }

    public NoteHash(byte[] bytes){
        super(bytes, ByteDecoding.RAW_BYTES);
    }

    public NoteHash(String string){
        super(string);
    }

    @Override
    public void dataUpdated(){
        updateHash();
    }


    public void updateHash(){
        m_hash =  ByteHashing.digestBytesToBytes(getBytes(), m_hashSize);
    }

    public BigInteger getHashAsBigIntegerMagnitude(){
        return getHashAsBigIntegerMagnitude(false);
    }
    public BigInteger getHashAsBigIntegerMagnitude(boolean isLittleEndian){
        return new BigInteger(1, m_hash);
    }
}
