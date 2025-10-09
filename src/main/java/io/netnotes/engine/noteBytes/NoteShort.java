package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteHashing;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class NoteShort extends NoteBytes{
    public static final int UNSIGNED_MAX = 65535;
    
    public final static int SIZE = 2;

    public NoteShort (short s){
        super(ByteDecoding.shortToBytesBigEndian(s), NoteBytesMetaData.SHORT_TYPE);
    }


        public NoteShort(byte[] bytes){
        super(bytes, NoteBytesMetaData.SHORT_TYPE);
    }
    

    public static NoteShort create(byte a, byte b){
        return new NoteShort(ByteDecoding.bytesToShortBigEndian(new byte[]{a,b}));
    }

 
    public short getShort(){

        return getShort(isLittleEndian());
    }

    public short getShort(boolean isLittleEndian){
        
        return isLittleEndian ? ByteDecoding.bytesToShortLittleEndian(getBytes()) : ByteDecoding.bytesToShortBigEndian(getBytes()) ;
    }

    public byte getMsb(){
        return getMsb(isLittleEndian());
    }

    public byte getMsb(boolean isLittleEndian){
        byte[] bytes = getBytes();
        return bytes[isLittleEndian ? 1 : 0];
    }

    public void setMsb(byte a){
        setMsb(a, isLittleEndian());
    }
    public void setMsb(byte a, boolean isLittleEndian){
        byte[] bytes = getBytes();
        bytes[isLittleEndian ? 1 : 0] = a;
        set(bytes);
    }


    public byte getLsb(){
        return getLsb(isLittleEndian());
    }

    public byte getLsb(boolean isLittleEndian){
        byte[] bytes = getBytes();
        return bytes[isLittleEndian ? 0 : 1];
    }

    public void setLsb(byte b){
        setLsb(b, isLittleEndian());
    }
    public void setLsb(byte b, boolean isLittleEndian){
        byte[] bytes = getBytes();
        bytes[isLittleEndian ? 0 : 1] = b;
        set(bytes);
    }




    public char getAsUTF16Char(){
        return ByteHashing.getCharUTF16(getBytes(), 0,(isLittleEndian()));
    }

    public char[] getAsCodePoint(){
        return Character.toChars(getShort());
    }


    public String asString(){
        return new String(getBytes());
    }

    public String toString(){
        return getShort() + "";
    }

    

}