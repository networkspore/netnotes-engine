package io.netnotes.engine.noteBytes;

import java.util.Arrays;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteDecodingSecure;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

/*
 * =========================================================================
 * This class overrides methods from NoteBytes to add error-checked and 
 * secure handling of byte data in memory.
 * =========================================================================
 */

public class NoteBytesSecure extends NoteBytes implements AutoCloseable {


    public NoteBytesSecure( byte[] value){
        this(value , NoteBytesMetaData.RAW_BYTES_TYPE);
    }

    public NoteBytesSecure( String value){
        this(value.toCharArray());
    }

    public NoteBytesSecure(long _long){
        this(ByteDecodingSecure.longToBytesBigEndian(_long), NoteBytesMetaData.LONG_TYPE);
    }

    public NoteBytesSecure( Byte[] value){
        this(ByteDecodingSecure.unboxBytes(value));
    }

    public NoteBytesSecure( char[] value){
        this( ByteDecodingSecure.charsToBytes(value), NoteBytesMetaData.STRING_TYPE);
    }
    

    public NoteBytesSecure( char[] value, byte type){
        this( ByteDecodingSecure.charsToByteArray(value, type), type);
    }

   
    public NoteBytesSecure( byte[] value, byte type){
        super(value, type);
    }
    
 
    public void close(){
        destroy();
    }


    @Override
    public void set(byte[] value, byte type) {
        byte[] bytes = value != null ? Arrays.copyOf(value, value.length) : new byte[0];
        super.set(bytes, type);
    }

    @Override
    public void set( byte[] value){
        set(value, getType());
    }


    @Override
    public byte[] get(){
        if(byteLength() == 0){
            return new byte[0];
        }
        if(isRuined()){
            throw new IllegalStateException("NoteBytes data has been ruined and can no longer be accessed");
        }
        byte[] bytes = super.get();
        return Arrays.copyOf(bytes, bytes.length);
    }
    @Override
    public boolean compareBytes(byte[] bytes){
        byte[] value = get();
        return Arrays.equals(value, bytes);
    }
  
 

    @Override
    public void setInteger(int value){
        byte type = getType();
        boolean isLittleEndian = ByteDecoding.isLittleEndian(type);
        byte[] bytes = isLittleEndian ? ByteDecodingSecure.intToBytesLittleEndian(value) : ByteDecodingSecure.intToBytesBigEndian(value);
        super.set(bytes, type);
    }

    @Override
    public boolean getAsBoolean(){
        byte[] bytes = super.get();
        return ByteDecodingSecure.bytesToBoolean(bytes);  
    }

    @Override
    public double getAsDouble(){
        byte[] bytes =  super.get();
        boolean isLittleEndian = isLittleEndian();
        return  isLittleEndian ? ByteDecodingSecure.bytesToDoubleLittleEndian(bytes) : ByteDecodingSecure.bytesToDoubleBigEndian(bytes);
    }
   


    @Override
    public int getAsInt(){
        if(byteLength() == 0){
            return 0;
        }
        if(byteLength() > 4){
            throw new IllegalStateException("Expected up to 4 bytes for int conversion, got " + byteLength());
        }
         byte[] bytes =  super.get();

        return isLittleEndian() ? ByteDecodingSecure.bytesToIntLittleEndian(bytes) : ByteDecodingSecure.bytesToIntBigEndian(bytes);
    }
    @Override
    public short getAsShort(){
        if(byteLength() == 0){
            return 0;
        }
        if(byteLength() > 2){
            throw new IllegalStateException("Expected up to 2 bytes for short conversion, got " + byteLength());
        }
         byte[] bytes =  super.get();
        return isLittleEndian() ? ByteDecodingSecure.bytesToShortLittleEndian(bytes) : ByteDecodingSecure.bytesToShortBigEndian(bytes);
    }
    @Override
     public long getAsLong(){
        if(byteLength() == 0){
            return 0;
        }
        if(byteLength() > 8){
            throw new IllegalStateException("Expected up to 8 bytes for long conversion, got " + byteLength());
        }
        byte[] bytes =  super.get();
  
        return isLittleEndian() ? ByteDecodingSecure.bytesToIntLittleEndian(bytes) : ByteDecodingSecure.bytesToIntBigEndian(bytes);
    }

    
    @Override
    public int getAsIntegerLittleEndian(){
        if(byteLength() == 0){
            return 0;
        }
         if(byteLength() > 4){
            throw new IllegalStateException("Expected up to 4 bytes for int conversion, got " + byteLength());
        }
        return ByteDecodingSecure.bytesToIntLittleEndian(super.get());
    }



}
