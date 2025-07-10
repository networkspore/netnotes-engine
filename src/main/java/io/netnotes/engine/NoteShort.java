package io.netnotes.engine;

import scorex.util.encode.Base16;
import scorex.util.encode.Base64;

public class NoteShort extends NoteBytes{
    public static final int UNSIGNED_MAX = 65535;
    
    public final static int SIZE = 2;

    public NoteShort (short s){
        super(ByteDecoding.shortToBytesBigEndian(s), ByteDecoding.SHORT);
    }


        public NoteShort(byte[] bytes){
        super(bytes, ByteDecoding.SHORT);
    }
    

    public static NoteShort create(byte a, byte b){
        return new NoteShort(ByteDecoding.bytesToShortBigEndian(new byte[]{a,b}));
    }


    public short getShort(){

        return getShort(getByteDecoding().isLittleEndian());
    }

    public short getShort(boolean isLittleEndian){
        
        return isLittleEndian ? ByteDecoding.bytesToShortLittleEndian(getBytes()) : ByteDecoding.bytesToShortBigEndian(getBytes()) ;
    }

    public byte getMsb(){
        return getMsb(getByteDecoding().isLittleEndian());
    }

    public byte getMsb(boolean isLittleEndian){
        byte[] bytes = getBytes();
        return bytes[isLittleEndian ? 1 : 0];
    }

    public void setMsb(byte a){
        setMsb(a, getByteDecoding().isLittleEndian());
    }
    public void setMsb(byte a, boolean isLittleEndian){
        byte[] bytes = getBytes();
        bytes[isLittleEndian ? 1 : 0] = a;
        set(bytes);
    }


    public byte getLsb(){
        return getLsb(getByteDecoding().isLittleEndian());
    }

    public byte getLsb(boolean isLittleEndian){
        byte[] bytes = getBytes();
        return bytes[isLittleEndian ? 0 : 1];
    }

    public void setLsb(byte b){
        setLsb(b, getByteDecoding().isLittleEndian());
    }
    public void setLsb(byte b, boolean isLittleEndian){
        byte[] bytes = getBytes();
        bytes[isLittleEndian ? 0 : 1] = b;
        set(bytes);
    }




    public char getAsUTF16Char(){
        return ByteHashing.getCharUTF16(getBytes(), 0,(getByteDecoding().isLittleEndian()));
    }

    public char[] getAsCodePoint(){
        return Character.toChars(getShort());
    }

    public String getBase64String(){
        return Base64.encode(getBytes());
    }
    public String toHexString(){
        return Base16.encode(getBytes());
    }

    public String asString(){
        return new String(getBytes());
    }

    public String toString(){
        return getShort() + "";
    }

    

}