package io.netnotes.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.concurrent.Semaphore;

import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class NoteBytes {

    public final static int MAX_SHORT_INDEX_VALUE = NoteShort.UNSIGNED_MAX;
    private Semaphore m_semaphore = null;

    private ByteDecoding m_byteDecoding = ByteDecoding.RAW_BYTES;
    private byte[] m_value = null;

    public NoteBytes( byte[] value){
        this(value, ByteDecoding.RAW_BYTES);
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

        m_byteDecoding = byteDecoding;
        set(value);
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
        return new String(getAsCharsUTF8());
    }

    public char[] decodeCharArray(){
        return m_value.length > 0 ? ByteDecoding.bytesToCharArray(m_value, m_byteDecoding) : new char[0];
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

    public int getAsInteger(){
        byte[] bytes = Arrays.copyOf( m_value, 4);

        return m_byteDecoding != null && m_byteDecoding.isLittleEndian() ? ByteDecoding.bytesToIntLittleEndian(bytes) : ByteDecoding.bytesToIntBigEndian(bytes);
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

    public static byte[] read255MaxBytes(DataInputStream dis) throws IOException{
        byte[] bytes = new byte[dis.readUnsignedByte()];
        dis.read(bytes);
        return bytes;
    }


    public static byte[] readShortBytes(DataInputStream dataInputStream) throws IOException{
        int length = dataInputStream.readUnsignedShort();
        byte[] bytes = new byte[length];
        dataInputStream.read(bytes);
        return bytes;
    }

     public static byte[] readBytes(DataInputStream dataInputStream) throws IOException{
        int length = dataInputStream.readInt();
        byte[] bytes = new byte[length];
        dataInputStream.read(bytes);
        return bytes;
    }

    public static byte[] readBytes(ByteArrayInputStream bais) throws IOException{
        byte[] lengthBytes = new byte[4];
        bais.read(lengthBytes);
        int length = ByteDecoding.bytesToIntBigEndian(lengthBytes);
        byte[] bytes = new byte[length];
        bais.read(bytes);
        return bytes;
    }



    public static void writeShortBytes( byte[] bytes,  DataOutputStream dos) throws IOException{
        int length = bytes.length;
        if(length > MAX_SHORT_INDEX_VALUE){
            throw new IOException(NoteConstants.ERROR_OUT_OF_RANGE);
        }
        dos.writeShort((short) length);
        dos.write(bytes);
    }

    public static int writeBytes( byte[] bytes,  DataOutputStream dos) throws IOException{
        int length = bytes.length;
        if(length > Integer.MAX_VALUE){
            throw new IOException(NoteConstants.ERROR_OUT_OF_RANGE);
        }
        dos.writeInt(length);
        dos.write(bytes);
        return length + 4;
    }

    public static int writeBytes( byte[] bytes,  ByteArrayOutputStream dos) throws IOException{
        int length = bytes.length;
        if(length > Integer.MAX_VALUE){
            throw new IOException(NoteConstants.ERROR_OUT_OF_RANGE);
        }
        byte[] lengthBytes = ByteDecoding.intToBytesBigEndian(length);
        dos.write(lengthBytes, 0, 4);
        dos.write(bytes, 0, length);
        return length + 4;
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

    public static int writeNote(NoteBytes noteBytes, DataOutputStream dos) throws IOException{
        return writeBytes(noteBytes.getBytes(),dos);
    }

    public static int writeNote(NoteBytes noteBytes, ByteArrayOutputStream baos) throws IOException{
        return writeBytes(noteBytes.getBytes(), baos);
    }
  
    public static NoteBytes readNote(DataInputStream dis) throws IOException{
        return new NoteBytes(readBytes(dis));
    }

    public static NoteBytes readNote(ByteArrayInputStream bais) throws IOException{
         return new NoteBytes(readBytes(bais));
    }

    public static NoteBytes readNote(byte[] bytes, int offset, ByteDecoding byteDecoding){
        int size = byteDecoding.isLittleEndian() ?  ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
        byte[] dst = new byte[size];
        System.arraycopy(bytes, offset + 4, dst, 0, size);
        return new NoteBytes(dst, byteDecoding);
    }


    public static void writeShortNote(NoteBytes noteBytes, DataOutputStream dos) throws IOException{
        writeShortBytes(noteBytes.getBytes(), dos);
    }

    public static NoteBytes readShortNote(DataInputStream dis) throws IOException{
        return new NoteBytes(readShortBytes(dis));
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
        if(m_byteDecoding.getCharacterEncoding() != ByteDecoding.NO_ENCODING ){
            byte[] value = m_value;
            byte[] bytes = ByteDecoding.charsToByteArray(str.toCharArray(), m_byteDecoding);
            return Arrays.equals(bytes, value);
        }
        return false;
    }




    public int byteLength(){
        return getBytes().length;
    }
    
    public NotePairTree getAsNotePairTree(){
        return new NotePairTree(getBytes());
    }

    public NoteBytesArray getAsNoteBytesArray(){
        return new NoteBytesArray(get());
    }


    public JsonElement getAsJsonElement(){
        
        return new JsonParser().parse(new String(decodeCharArray()));
    }

    @Override
    public String toString(){
        return new String(decodeCharArray());
    }
}
