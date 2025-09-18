package io.netnotes.engine.noteBytes;

import java.nio.CharBuffer;

import org.bouncycastle.util.Arrays;

import io.netnotes.engine.noteBytes.ByteDecoding.NoteBytesMetaData;

public class NoteBytesReadOnly extends NoteBytes {
    

    public NoteBytesReadOnly( byte[] value){
        super(value , ByteDecoding.RAW_BYTES);
    }
    public NoteBytesReadOnly( String value){
        super(value.toCharArray());
    }

    public NoteBytesReadOnly(long _long){
        super(ByteDecoding.longToBytesBigEndian(_long), NoteBytesMetaData.LONG_TYPE);
    }

    public NoteBytesReadOnly( Byte[] value){
        super(ByteDecoding.unboxBytes(value));
    }

    public NoteBytesReadOnly( char[] value){
        super( ByteDecoding.charsToBytes(value), ByteDecoding.STRING_UTF8);
    }
    
    public NoteBytesReadOnly( String value, ByteDecoding byteDecoding){
        super(ByteDecoding.charsToByteArray(CharBuffer.wrap(value), byteDecoding), byteDecoding);
    }

    public NoteBytesReadOnly( CharBuffer charBuffer, ByteDecoding byteDecoding){
        super( ByteDecoding.charsToByteArray(charBuffer, byteDecoding), byteDecoding);
    }


    public NoteBytesReadOnly( char[] value, ByteDecoding byteDecoding){
        super( ByteDecoding.charsToByteArray(value, byteDecoding), byteDecoding);
    }

    public NoteBytesReadOnly( byte[] value, ByteDecoding byteDecoding){
        super(value, byteDecoding);
    }

    public NoteBytesReadOnly( byte[] value, byte type){
        super(value, ByteDecoding.getDecodingFromType(type));
    }

    public NoteBytesReadOnly(NoteBytes other) {
        super(other.getBytes(), other.getByteDecoding());
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
        return new NoteBytesReadOnly(dst, ByteDecoding.getDecodingFromType(type));
    }
    
    @Override
    public NoteBytesReadOnly copy(){
        return new NoteBytesReadOnly(Arrays.copyOf(get(), byteLength()), getByteDecoding());
    }

    @Override
    public void set(byte[] disabled){

    }

    @Override
    public void set(byte[] disabled, ByteDecoding disabledByteDecoding){

    }

    @Override
    public void setByteDecoding(ByteDecoding disabled){

    }

    @Override
    public void clear(){

    }

 
    @Override
    public void ruin(){
        super.ruin();
    }

}
