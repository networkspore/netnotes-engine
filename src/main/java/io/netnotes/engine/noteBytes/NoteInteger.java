package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteInteger extends NoteBytes  {
    public static final NoteInteger ZERO = new NoteInteger(0);
    public static final NoteInteger ONE = new NoteInteger(1);

    public NoteInteger(int integer){
        super(ByteDecoding.intToBytesBigEndian(integer), NoteBytesMetaData.INTEGER_TYPE);

    }

    public NoteInteger(byte[] bytes){
        super(bytes, NoteBytesMetaData.INTEGER_TYPE);
    }



    public static NoteInteger create(byte a, byte b, byte c, byte d){
        return new NoteInteger(new byte[]{a, b, c , d});
    }
    
    public int getInteger(){
        return getInteger(isLittleEndian());
    }
    public int getInteger(boolean isLittleEndian){
        return isLittleEndian? ByteDecoding.bytesToIntLittleEndian(getBytes()) : ByteDecoding.bytesToIntBigEndian(getBytes()); 
    }

    public void setInteger(int integer, boolean isLittleEndian){
        set(integer, isLittleEndian);
    }

    public void setInteger(int integer){
        set(integer);
    }

    public void setZero(){
        set(0);
    }

    public void set(int integer){
        set(integer, false);
    }

    public void set(int integer, boolean islittleEndian){
        set(islittleEndian ? ByteDecoding.intToBytesLittleEndian(integer) : ByteDecoding.intToBytesBigEndian(integer)); 
    }

    private void setIfNotFourBytes(){
        if(get().length != 4){
            set(new byte[4]);
        }
    }

    public void setA(int a){
        setIfNotFourBytes();
        byte[] bytes = getBytes();
        bytes[0] = (byte) a;
        set(bytes);
    }
     public void setB(int b){
        setIfNotFourBytes();
        byte[] bytes = getBytes();
        bytes[1] = (byte)b;
        set(bytes);
    }

    public void setC(int c){
        setIfNotFourBytes();
        byte[] bytes = getBytes();
        bytes[2] = (byte) c;
        set(bytes);
    }

    public void setD(int d){
        setIfNotFourBytes();
        byte[] bytes = getBytes();
        bytes[3] = (byte) d;
        set(bytes);
    }

    public int getA(){
        setIfNotFourBytes();
        byte[] bytes = getBytes();
        return bytes[0] & 0xFF;
    }
    public int getB(){
        setIfNotFourBytes();
        byte[] bytes = getBytes();
        return bytes[1] & 0xFF;
    }

    public int getC(){
        setIfNotFourBytes();
        byte[] bytes = getBytes();
        return bytes[2] & 0xFF;
    }

    public int getD(){
        setIfNotFourBytes();
        byte[] bytes = getBytes();
        return bytes[3] & 0xFF;
    }

    public static int getInteger(int a, int b, int c, int d, boolean isBigEndian){

        byte[] bytes = new byte[]{(byte)a, (byte) b, (byte) c, (byte) d};
        return isBigEndian ? ByteDecoding.bytesToIntBigEndian(bytes) : ByteDecoding.bytesToIntLittleEndian(bytes); 
        
    }

 
    public static int getInteger(int a, int b, int c, int d){
        return getInteger(a, b, c, d,true);
    }
    
}
