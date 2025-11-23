package io.netnotes.engine.noteBytes;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.google.gson.JsonObject;

import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
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

    public NoteBytesReadOnly(BigInteger bigInteger){
        this(bigInteger.toByteArray(), NoteBytesMetaData.BIG_INTEGER_TYPE);
    }

    public NoteBytesReadOnly(BigDecimal bigDecimal){
        this(ByteDecoding.bigDecimalToScaleAndBigInteger(bigDecimal), NoteBytesMetaData.BIG_DECIMAL_TYPE);
    }

    public NoteBytesReadOnly(long l){
        this(ByteDecoding.longToBytesBigEndian(l), NoteBytesMetaData.LONG_TYPE);
    }

    public NoteBytesReadOnly(double l){
        this(ByteDecoding.doubleToBytesBigEndian(l), NoteBytesMetaData.DOUBLE_TYPE);
    }

    public NoteBytesReadOnly( char[] value, byte type){
        this( ByteDecoding.charsToByteArray(value, type), type);
    }

    public NoteBytesReadOnly(float l){
        this(ByteDecoding.floatToBytesBigEndian(l), NoteBytesMetaData.FLOAT_TYPE);
    }

     public NoteBytesReadOnly(boolean b){
        this(ByteDecoding.booleanToBytes(b), NoteBytesMetaData.BOOLEAN_TYPE);
    }

    public NoteBytesReadOnly(NoteBytes value){
        super(value.get(), value.getType());
    }

   
    public NoteBytesReadOnly( byte[] value, byte type){
        super(value, type);
    }
    public NoteBytesReadOnly( byte[] value){
        super(value);
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


      public static NoteBytesReadOnly of(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Cannot create NoteBytes from null object");
        }else if (obj instanceof Boolean) {
            return new NoteBytesReadOnly((Boolean) obj);
        } else if (obj instanceof Integer) {
            return new NoteBytesReadOnly((Integer) obj);
        } else if (obj instanceof Long) {
            return new NoteBytesReadOnly((Long) obj);
        } else if (obj instanceof Double) {
            return new NoteBytesReadOnly((Double) obj);
        } else if (obj instanceof BigInteger) {
            return new NoteBytesReadOnly((BigInteger) obj);
        } else if (obj instanceof BigDecimal) {
            return new NoteBytesReadOnly((BigDecimal) obj);
        } else if (obj instanceof String) {
            return new NoteBytesReadOnly((String) obj);
        }  else if (obj instanceof byte[]) {
            return new NoteBytesReadOnly((byte[]) obj, NoteBytesMetaData.RAW_BYTES_TYPE);
        }else if(obj instanceof Byte[]){
            return new NoteBytesReadOnly(ByteDecoding.unboxBytes((Byte[]) obj) , NoteBytesMetaData.RAW_BYTES_TYPE);
        } else if (obj instanceof char[]) {
            return new NoteBytesReadOnly((char[]) obj);
        } else if (obj instanceof JsonObject) {
            return new NoteBytesReadOnly(((JsonObject) obj).toString());
        } else if (obj instanceof NoteBytesPair) {
            return new NoteBytesObject(new NoteBytesPair[]{(NoteBytesPair) obj}).readOnly();
        } else if (obj instanceof NoteBytesPair[]) {
            return new NoteBytesObject((NoteBytesPair[]) obj).readOnly();
        } else if(obj instanceof NoteBytesMap){
            return ((NoteBytesMap)obj).getNoteBytesObject().readOnly();
        }else if(obj instanceof NoteSerializable){
            return ((NoteSerializable) obj).readOnly();
        }else if(obj instanceof NoteBytes){
            return ((NoteBytes) obj).readOnly();
        }else if(obj instanceof Serializable){
            try{
                return( new NoteSerializable(obj)).readOnly();
            }catch(IOException e){
                throw new IllegalArgumentException(e);
            }
        }
        
        throw new IllegalArgumentException("Unsuported type");
    }

    public static NoteBytesReadOnly of(byte[] bytes, byte type){
        return new NoteBytesReadOnly(bytes, type);
    }

}
