package io.netnotes.engine.noteBytes;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import org.bouncycastle.util.encoders.Base32;

import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class NoteBytes {

    public final static int MAX_SHORT_INDEX_VALUE = NoteShort.UNSIGNED_MAX;
    private Semaphore m_semaphore = null;

    private ByteDecoding m_byteDecoding = ByteDecoding.RAW_BYTES;
    private byte[] m_value = null;


    public NoteBytes( byte[] value){
        this(value , ByteDecoding.RAW_BYTES);
    }

    public NoteBytes( String value){
        this(value.toCharArray());
    }

    public NoteBytes( Byte[] value){
        this(ByteDecoding.unboxBytes(value));
    }

    public NoteBytes( char[] value){
        this( ByteDecoding.charsToBytes(value), ByteDecoding.STRING_UTF8);
    }

    public NoteBytes( byte[] value, ByteDecoding byteDecoding){
        set(value, byteDecoding);
    }

    public ByteBuffer getByteByffer(){
        return ByteBuffer.wrap(getBytes());
    }

    public void set( byte[] value, ByteDecoding byteDecoding){
        m_value = value != null ? value : new byte[0];
        m_byteDecoding = byteDecoding;
        update();
    }

    public void set( byte[] value){
        set(value, m_byteDecoding);
    }


    public ByteDecoding getByteDecoding(){
        return m_byteDecoding;
    }

    public void setByteDecoding(ByteDecoding byteDecoding){
        m_byteDecoding = byteDecoding;
        update();
    }
    
    public Semaphore getSemaphore(){
        m_semaphore = m_semaphore == null ? new Semaphore(1) : m_semaphore;
        return m_semaphore;
    }

    public void setSemaphore(Semaphore semaphore){
        m_semaphore = semaphore;
    }

    public void update(){
    
    }


    public static int byteToInt(byte b){
        return b & 0xff;
    }
   
    public byte[] getAsBase64Decoded(){
        return ByteDecoding.decodeBytes(get(), ByteDecoding.BASE_64);
    }

     public byte[] getAsBase64Encoded(){
        return ByteDecoding.encodeBytes(get(), ByteDecoding.BASE_64);
    }

    public String getAsBase64String(){
        return ByteDecoding.encodeBytesString(get(), ByteDecoding.BASE_64) ;
    }

     public char[] getAsUrlSafeChars(){ 
        return ByteDecoding.encodeBytesToChars(get(), ByteDecoding.URL_SAFE, ByteDecoding.ISO_8859_1);
    }

    public String getAsUrlSafeString(){ 
        return ByteDecoding.encodeBytesString(get(), ByteDecoding.URL_SAFE);
    }

    public String getAsHexString(){
       return ByteDecoding.encodeBytesString(get(), ByteDecoding.BASE_16);
    }

    public String getAsBase32String(){
       return ByteDecoding.encodeBytesString(get(), ByteDecoding.BASE_32);
    }

    public byte[] get(){
        return m_value;
    }

    public byte[] getBytes(){
        return get();
    }

    public char[] getAsCharsUTF8(){
        return ByteDecoding.bytesToCharArray(getBytes(), ByteDecoding.STRING_UTF8);
    }

    public String getAsString(){
        return toString();
    }

    @Override
    public String toString(){
        return new String(decodeCharArray());
    }

    public char[] decodeCharArray(){
        return m_value.length > 0 ? ByteDecoding.bytesToCharArray(m_value, m_byteDecoding) : new char[0];
    }

    public char[] getChars(){
        return decodeCharArray();
    }


    public CharBuffer getAsCharBuffer(){
        byte[] bytes = getBytes();
        if(bytes != null && bytes.length > 0){
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            return ByteDecoding.bytesToChars(byteBuffer, getByteDecoding());
            
        }
        return CharBuffer.wrap( new char[0]);
    }

    public void setValueInteger(int value){
        m_value = ByteDecoding.intToBytesBigEndian(value);
    }


    public void setValueIntegerLittleEndian(int value){
        m_value = ByteDecoding.intToBytesLittleEndian(value);
    }

    public boolean getAsBoolean(){
        byte[] bytes = get();
        return ByteDecoding.bytesToBoolean(bytes);  
    }

    public double getAsDouble(){
        byte[] bytes = Arrays.copyOf( m_value, 8);
    
        return m_byteDecoding != null && m_byteDecoding.isLittleEndian() ? ByteDecoding.bytesToDoubleLittleEndian(bytes) : ByteDecoding.bytesToDoubleBigEndian(bytes);
    }

    public byte getAsByte(){
        return Arrays.copyOf( m_value, 1)[0];
    }

    public int getAsInt(){
        byte[] bytes = Arrays.copyOf( m_value, 4);

        return m_byteDecoding != null && m_byteDecoding.isLittleEndian() ? ByteDecoding.bytesToIntLittleEndian(bytes) : ByteDecoding.bytesToIntBigEndian(bytes);
    }

    public short getAsShort(){
        byte[] bytes = Arrays.copyOf( m_value, 2);
        return m_byteDecoding != null && m_byteDecoding.isLittleEndian() ? ByteDecoding.bytesToShortLittleEndian(bytes) : ByteDecoding.bytesToShortBigEndian(bytes);
    }

     public int getAsLong(){
        byte[] bytes = Arrays.copyOf( m_value, 8);
        return m_byteDecoding != null && m_byteDecoding.isLittleEndian() ? ByteDecoding.bytesToIntLittleEndian(bytes) : ByteDecoding.bytesToIntBigEndian(bytes);
    }

    public BigDecimal getAsBigDecimal(){
        return m_value.length > 4 ? ByteDecoding.scaleAndBigIntegerBytesToBigDecimal(m_value) : null;
    }


    public int getAsIntegerLittleEndian(){
        return ByteDecoding.bytesToIntLittleEndian(Arrays.copyOf(m_value, 4));
    }


    public String getAsUTF8(){
        return new String(ByteDecoding.bytesToCharArray(m_value, ByteDecoding.STRING_UTF8));
    }

    public String getAsISO(){
        return new String(getAsISOChars());
    }

    public char[] getAsISOChars(){
        return ByteDecoding.isoBytesToChars(m_value);
    }

    



    public BigInteger getAsBigInteger(){
        int len = byteLength();
        return len == 0 || (len == 1 && get()[0] == 0) ? BigInteger.ZERO : new BigInteger(get());
    }

    public static int writeNote(NoteBytes noteBytes, byte[] dst, int dstOffset){
        int dstLength = dst.length;
        
        byte[] src = noteBytes.get(); 
        int srcLength = src.length;

        if(dstLength > dstOffset + 4 + srcLength){
            byte[] srcLengthBytes = ByteDecoding.intToBytesBigEndian(srcLength);

            System.arraycopy(srcLengthBytes, 0, dst, dstOffset, 4);
            System.arraycopy(src, 0, dst, dstOffset + 4, srcLength);

            return dstOffset + 4 + srcLength;
        }else{
            throw new RuntimeException("insufficient destination length");
        }
    }


    public static NoteBytes readNote(byte[] bytes, int offset, ByteDecoding byteDecoding){
        int size = byteDecoding.isLittleEndian() ?  ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
        byte[] dst = new byte[size];
        System.arraycopy(bytes, offset + 4, dst, 0, size);
        return new NoteBytes(dst, byteDecoding);
    }


    @Override
    public int hashCode(){
        return m_value.length == 0 ? 0 : ByteHashing.getHashCode(m_value, m_byteDecoding);
    }

    @Override
    public boolean equals(Object obj){
        byte[] value = m_value;
        if(value == null && obj == null){
            return true;
        }else if(value != null && obj != null && obj instanceof NoteBytes){
            NoteBytes noteBytesObj = (NoteBytes) obj;
            return noteBytesObj.compareBytes(value);
        }
        return false;
    }

    public boolean compareBytes(byte[] bytes){
        byte[] value = m_value;
        return Arrays.equals(value, bytes);
    }

    public boolean equalsString(String str){
        return str.equals(toString());
    }

    public void destroy(){
        if(m_value != null && m_value.length > 0){
            SecureRandom sr = new SecureRandom();
            sr.nextBytes(m_value);
            m_value = new byte[0];
        }
    }

    public boolean isDestroyed(){
        return byteLength() == 0;
    }

    public int byteLength(){
        return get().length;
    }
    
    public NoteBytesObject getAsNoteBytesObject(){
        return new NoteBytesObject(getBytes());
    }

    public NoteBytesArray getAsNoteBytesArray(){
        return new NoteBytesArray(get());
    }

    public boolean isEmpty(){
        return m_value.length == 0;
    }

    public JsonElement getAsJsonElement(){
        
        return new JsonParser().parse(new String(decodeCharArray()));
    }

    public JsonObject getAsJsonObject(){
        JsonElement element = getAsJsonElement();
        return element != null && !element.isJsonNull() && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public JsonArray getAsJsonArray(){
        JsonElement element = getAsJsonElement();
        return element != null && !element.isJsonNull() && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

}
