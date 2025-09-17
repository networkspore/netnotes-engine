package io.netnotes.engine.noteBytes;

import java.nio.CharBuffer;
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
