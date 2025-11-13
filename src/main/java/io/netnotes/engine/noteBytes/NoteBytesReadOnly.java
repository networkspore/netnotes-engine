package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteBytesReadOnly extends NoteBytes {
    

    public NoteBytesReadOnly( String value){
        this( ByteDecoding.stringToBytes(value,  NoteBytesMetaData.STRING_TYPE), NoteBytesMetaData.STRING_TYPE);
    }
    public NoteBytesReadOnly(char[] chars){
        this( chars, NoteBytesMetaData.STRING_TYPE);
    }

    public NoteBytesReadOnly(byte b){
        this(new byte[]{ b }, NoteBytesMetaData.BYTE_TYPE);
    }

    public NoteBytesReadOnly(int integer){
        this(ByteDecoding.intToBytesBigEndian(integer), NoteBytesMetaData.INTEGER_TYPE);
    }

    public NoteBytesReadOnly(long l){
        this(ByteDecoding.longToBytesBigEndian(l), NoteBytesMetaData.LONG_TYPE);
    }

    public NoteBytesReadOnly( char[] value, byte type){
        this( ByteDecoding.charsToByteArray(value, type), type);
    }

    public NoteBytesReadOnly(float l){
        this(ByteDecoding.floatToBytesBigEndian(l), NoteBytesMetaData.FLOAT_TYPE);
    }


    public NoteBytesReadOnly(Object value){
        this(NoteBytes.of(value));
    }

    public NoteBytesReadOnly(NoteBytes value){
        super(value.get(), value.getType());
    }

   
    public NoteBytesReadOnly( byte[] value, byte type){
        super(value, type);
    }

    public static NoteBytesReadOnly readNote(byte[] bytes, int offset){
        return readNote(bytes, offset, false);
    }

    public static NoteBytesReadOnly readNote(byte[] bytes, int offset, boolean isLittleEndian){
        byte type = bytes[offset];
        offset++;
        int size = isLittleEndian ? ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
        byte[] dst = new byte[size];
        System.arraycopy(bytes, offset + 4, dst, 0, size);
        return new NoteBytesReadOnly(dst, type);
    }

    @Override 
    public byte[] get(){
        byte[] bytes = super.get();
        byte[] newBytes = new byte[bytes.length];
        System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
        return newBytes;
    }

    
    @Override
    public NoteBytesReadOnly copy(){
        return new NoteBytesReadOnly(get(), getType());
    }

    @Override
    public void set(byte[] disabled){

    }

    @Override
    public void set(byte[] disabled, byte type){

    }

    @Override
    public void setType(byte disabled){

    }

    @Override
    public void clear(){

    }

    public byte getAsByte(){
        return getBytesInternal()[0];
    }
    
 
    @Override
    public void ruin(){
        super.ruin();
    }

}
