package io.netnotes.engine.noteBytes;

import java.util.Arrays;

import io.netnotes.engine.noteBytes.ByteDecoding.NoteBytesMetaData;

/*
 * =========================================================================
 * This class overrides methods from NoteBytes to add error-checked and 
 * secure handling of byte data in memory.
 * =========================================================================
 */

public class NoteBytesSecure extends NoteBytes implements AutoCloseable {


    public NoteBytesSecure( byte[] value){
        this(value , ByteDecoding.RAW_BYTES);
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
        this( ByteDecodingSecure.charsToBytes(value), ByteDecodingSecure.STRING_UTF8);
    }
    

    public NoteBytesSecure( char[] value, ByteDecoding byteDecoding){
        this( ByteDecodingSecure.charsToByteArray(value, byteDecoding), byteDecoding);
    }

   
    public NoteBytesSecure( byte[] value, byte type){
        this(value, ByteDecodingSecure.getDecodingFromType(type));
    }
    
    public NoteBytesSecure( byte[] value, ByteDecoding byteDecoding){
        super(value, byteDecoding);
    }

    public void close(){
        destroy();
    }


    @Override
    public void set(byte[] value, ByteDecoding byteDecoding) {
        byte[] bytes = value != null ? Arrays.copyOf(value, value.length) : new byte[0];
        super.set(bytes, byteDecoding);
    }

    @Override
    public void set( byte[] value){
        super.set(value);
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
    public void setValueInteger(int value){
        ByteDecoding byteDecoding = getByteDecoding();
        boolean isLittleEndian = byteDecoding != null && byteDecoding.isLittleEndian();

        byte[] bytes = isLittleEndian ? ByteDecodingSecure.intToBytesLittleEndian(value) : ByteDecodingSecure.intToBytesBigEndian(value);
       
        if(byteDecoding != null && (byteDecoding.getType() == NoteBytesMetaData.INTEGER_TYPE || byteDecoding.getType() == NoteBytesMetaData.LONG_TYPE)){
            super.set(bytes, byteDecoding);
        }else{
            super.set(bytes, isLittleEndian ? ByteDecodingSecure.INTEGER_LITTLE_ENDIAN : ByteDecodingSecure.INTEGER);
        }
   
    }

    @Override
    public boolean getAsBoolean(){
        byte[] bytes = super.get();
        return ByteDecodingSecure.bytesToBoolean(bytes);  
    }

    @Override
    public double getAsDouble(){
        byte[] bytes =  super.get();
    
        return  getByteDecoding().isLittleEndian() ? ByteDecodingSecure.bytesToDoubleLittleEndian(bytes) : ByteDecodingSecure.bytesToDoubleBigEndian(bytes);
    }
    @Override
    public byte getAsByte(){
        if(byteLength() == 0){
            return 0;
        }
        if(byteLength() > 1){
            throw new IllegalStateException("Expected 1 byte for byte conversion, got " + byteLength());
        }
        byte[] bytes = Arrays.copyOf( super.get(), 1);
        return bytes[0];
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

        return getByteDecoding().isLittleEndian() ? ByteDecodingSecure.bytesToIntLittleEndian(bytes) : ByteDecodingSecure.bytesToIntBigEndian(bytes);
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
        return getByteDecoding().isLittleEndian() ? ByteDecodingSecure.bytesToShortLittleEndian(bytes) : ByteDecodingSecure.bytesToShortBigEndian(bytes);
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
  
        return getByteDecoding().isLittleEndian() ? ByteDecodingSecure.bytesToIntLittleEndian(bytes) : ByteDecodingSecure.bytesToIntBigEndian(bytes);
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
