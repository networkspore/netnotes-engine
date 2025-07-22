package io.netnotes.engine.noteBytes;

import java.math.BigInteger;

import io.netnotes.engine.ByteDecoding;
import io.netnotes.engine.ByteHashing;

public class NoteHash extends NoteBytes {

    private int m_hashSize = 16;
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
        super(bytes,ByteDecoding.RAW_BYTES_BASE64_IISO);
    }

    public NoteHash(String string){
        super(string);
    }

    @Override
    public void update(){
        super.update();
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
