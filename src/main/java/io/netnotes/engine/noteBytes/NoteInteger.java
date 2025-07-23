package io.netnotes.engine.noteBytes;

public class NoteInteger extends NoteBytes  {

    public NoteInteger(int integer){
        super(ByteDecoding.intToBytesBigEndian(integer));

    }

    public NoteInteger(byte[] bytes){
        super(bytes);
    }

    public static int getInteger(int a, int b, int c, int d, ByteDecoding type){

        byte[] bytes = new byte[]{(byte)a, (byte) b, (byte) c, (byte) d};
        return type.isLittleEndian() ? ByteDecoding.bytesToIntLittleEndian(bytes) : ByteDecoding.bytesToIntBigEndian(bytes) ; 
        
    }

    public static int getInteger(int a, int b, int c, int d){
        return getInteger(a, b, c, d, ByteDecoding.INTEGER);
    }
    

    public static NoteInteger create(byte a, byte b, byte c, byte d){
        return new NoteInteger(new byte[]{a, b, c , d});
    }
    
    public int getInteger(){
        return getInteger(getByteDecoding().isLittleEndian());
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

    public void setA(int a){
        byte[] bytes = getBytes();
        bytes[0] = (byte) a;
        set(bytes);
    }
     public void setB(int b){
        byte[] bytes = getBytes();
        bytes[1] = (byte)b;
        set(bytes);
    }

    public void setC(int c){
        byte[] bytes = getBytes();
        bytes[2] = (byte) c;
        set(bytes);
    }

    public void setD(int d){
        byte[] bytes = getBytes();
        bytes[3] = (byte) d;
        set(bytes);
    }

    public int getA(){
        byte[] bytes = getBytes();
        return bytes[0] & 0xFF;
    }
    public int getB(){
        byte[] bytes = getBytes();
        return bytes[1] & 0xFF;
    }

    public int getC(){
        byte[] bytes = getBytes();
        return bytes[2] & 0xFF;
    }

    public int getD(){
        byte[] bytes = getBytes();
        return bytes[3] & 0xFF;
    }

}
