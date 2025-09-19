package io.netnotes.engine.noteBytes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Map;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.collections.NoteBytesConcurrentMapEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesMapEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesTree;
import io.netnotes.engine.noteBytes.collections.NoteBytesTreeAsync;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteHashing;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
import javafx.scene.image.Image;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class NoteBytes {

    public final static int MAX_SHORT_INDEX_VALUE = NoteShort.UNSIGNED_MAX;
 

    private ByteDecoding m_byteDecoding = ByteDecoding.RAW_BYTES;
    private byte[] m_value = null;


    public NoteBytes( byte[] value){
        this(value , ByteDecoding.RAW_BYTES);
    }
    
    public NoteBytes( Object value){
         this(createNoteBytes(value));
    }

    public NoteBytes( NoteBytes value){
         this(value.get(), value.getByteDecoding());
    }

    public NoteBytes( String value, ByteDecoding byteDecoding){
        this(ByteDecoding.charsToByteArray(CharBuffer.wrap(value), byteDecoding), byteDecoding);
    }

    public NoteBytes( CharBuffer charBuffer, ByteDecoding byteDecoding){
        this( ByteDecoding.charsToByteArray(charBuffer, byteDecoding), byteDecoding);
    }


    public NoteBytes( char[] value, ByteDecoding byteDecoding){
        this( ByteDecoding.charsToByteArray(value, byteDecoding), byteDecoding);
    }

    public NoteBytes( byte[] value, ByteDecoding byteDecoding){
        m_value = value != null ? value : new byte[0];
        m_byteDecoding = byteDecoding;
    }

    public NoteBytes( byte[] value, byte type){
        this(value, ByteDecoding.getDecodingFromType(type));
    }

    

    public ByteBuffer getByteByffer(){
        return ByteBuffer.wrap(getBytes());
    }

    public void set(byte[] value, ByteDecoding byteDecoding) {
  
            m_value = value != null ? value : new byte[0];
            m_byteDecoding = byteDecoding;
            dataUpdated();

    }

    public void set( byte[] value){
        set(value, m_byteDecoding);
    }


    public ByteDecoding getByteDecoding(){
        return m_byteDecoding;
    }

    public void setByteDecoding(ByteDecoding byteDecoding){
        m_byteDecoding = byteDecoding;
        dataUpdated();
    }
    


    public void dataUpdated(){
    
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
        if(isRuined()){
            throw new IllegalStateException("NoteBytes data has been ruined and can no longer be accessed");
        }
        return m_value;
    }

    public byte[] getBytes(){
        return get();
    }

    public char[] getAsCharsUTF8(){
        return ByteDecoding.bytesToCharArray(getBytes(), ByteDecoding.STRING_UTF8);
    }

    public String getAsString(){
        return new String(decodeCharArray());
        
    }

    @Override
    public String toString(){
        switch(getByteDecoding().getType()){
            case NoteBytesMetaData.BIG_INTEGER_TYPE:
                return getAsBigInteger().toString();
            case NoteBytesMetaData.LONG_TYPE:
                return String.valueOf(getAsLong());
            case NoteBytesMetaData.INTEGER_TYPE:
                return String.valueOf(getAsInt());
            case NoteBytesMetaData.SHORT_TYPE:
                return String.valueOf(getAsShort());
            case NoteBytesMetaData.DOUBLE_TYPE:
                return String.valueOf(getAsDouble());
            case NoteBytesMetaData.BOOLEAN_TYPE:
                return String.valueOf(getAsBoolean());
            case NoteBytesMetaData.BIG_DECIMAL_TYPE:
                return getAsBigDecimal().toString();
            case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                return getAsJsonObject().toString();
            case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                return getAsJsonArray().toString();
            case NoteBytesMetaData.STRING_TYPE:
            case NoteBytesMetaData.STRING_UTF16_TYPE:
            default:
                return new String(decodeCharArray());
        }
    }

    public char[] decodeCharArray(){
        return get().length > 0 ? ByteDecoding.bytesToCharArray(getBytes(), getByteDecoding()) : new char[0];
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

        set(getByteDecoding().isLittleEndian() ? ByteDecoding.intToBytesLittleEndian(value) : ByteDecoding.intToBytesBigEndian(value));
    }


    public boolean getAsBoolean(){
        
        byte[] bytes = get();
        int len = bytes.length;
        return len == 0 ? false : ByteDecoding.bytesToBoolean(bytes);  
    }

    public double getAsDouble(){
   
        byte[] bytes = get();
        int len = bytes.length;
        return len == 0 ? 0 : (m_byteDecoding.isLittleEndian() ? ByteDecoding.bytesToDoubleLittleEndian(bytes) : ByteDecoding.bytesToDoubleBigEndian(bytes));
    }

    public byte getAsByte(){
        byte[] bytes = get();
        if(byteLength() == 0){
            return 0;
        }
        return bytes[0];
    }

    public int getAsInt(){
        byte[] bytes = get();
        int len = bytes.length;
        return len == 0 ? 0 : m_byteDecoding.isLittleEndian() ? ByteDecoding.bytesToIntLittleEndian(bytes) : ByteDecoding.bytesToIntBigEndian(bytes);
    }

    public short getAsShort(){
        byte[] bytes = get();
        int len = bytes.length;
        return len == 0 ? 0 : m_byteDecoding.isLittleEndian() ? ByteDecoding.bytesToShortLittleEndian(bytes) : ByteDecoding.bytesToShortBigEndian(bytes);
    }

     public long getAsLong(){
        byte[] bytes = get();
        int len = bytes.length;
        return len == 0 ? 0 : m_byteDecoding.isLittleEndian() ? ByteDecoding.bytesToIntLittleEndian(bytes) : ByteDecoding.bytesToIntBigEndian(bytes);
    }

    public BigDecimal getAsBigDecimal(){
        byte[] bytes = get();
        int len = bytes.length;
        if(len < 5) {
            return BigDecimal.ZERO;
        }
        return ByteDecoding.scaleAndBigIntegerBytesToBigDecimal(bytes);
    }

    
    public int getAsIntegerLittleEndian(){
        byte[] bytes = get();
        int len = bytes.length;
        return len == 0 ? 0 : ByteDecoding.bytesToIntLittleEndian(Arrays.copyOf(bytes, 4));
    }

    public Image getAsImage() throws IOException {
        byte[] data = getBytes();
        InputStream is = new ByteArrayInputStream(data);
        return new Image(is);
    }

     public InputStream getAsInputStream() {
        return new ByteArrayInputStream(get());
    }


    public String getAsUTF8(){
        byte[] bytes = get();
        return new String(ByteDecoding.bytesToCharArray(bytes, ByteDecoding.STRING_UTF8));
    }

    public String getAsISO(){
        return new String(getAsISOChars());
    }

    public char[] getAsISOChars(){
        byte[] bytes = get();
        return ByteDecoding.isoBytesToChars(bytes);
    }


    public BigInteger getAsBigInteger(){
        int len = byteLength();
        return len == 0 || (len == 1 && get()[0] == 0) ? BigInteger.ZERO : new BigInteger(get());
    }

    public static int writeNote(NoteBytes noteBytes, byte[] dst, int dstOffset){
        int dstLength = dst.length;
        
        byte[] src = noteBytes.get(); 
        int srcLength = src.length;
        if(dstLength > dstOffset + 5 + srcLength){
            dst[dstOffset] = noteBytes.getByteDecoding().getType();
            byte[] srcLengthBytes = ByteDecoding.intToBytesBigEndian(srcLength);
            System.arraycopy(srcLengthBytes, 0, dst, dstOffset + 1, 4);
            System.arraycopy(src, 0, dst, dstOffset + 5, srcLength);
            return dstOffset + 5 + srcLength;
        }else{
            throw new RuntimeException("insufficient destination length");
        }
    }

      public NoteBytes copy(){
        return new NoteBytes(Arrays.copyOf(get(), byteLength()), getByteDecoding());
    }

    public static void writeNote(NoteBytes noteBytes, ByteArrayOutputStream outputStream) throws IOException {
        byte type = noteBytes.getByteDecoding().getType();
        outputStream.write(type);
        outputStream.write(ByteDecoding.intToBytesBigEndian(noteBytes.byteLength()));
        outputStream.write(noteBytes.get());
    }

    public static NoteBytes readNote(byte[] bytes, int offset){
        return readNote(bytes, offset, false);
    }

    public static NoteBytes readNote(byte[] bytes, int offset, boolean isLittleEndian){
        byte type = bytes[offset];
        offset++;
        int size = isLittleEndian ? ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
        byte[] dst = new byte[size];
        System.arraycopy(bytes, offset + 4, dst, 0, size);
        return new NoteBytes(dst, ByteDecoding.getDecodingFromType(type));
    }


    @Override
    public int hashCode(){
        byte[] bytes = get();
        return bytes.length == 0 ? 0 : ByteHashing.getHashCode(bytes, m_byteDecoding);
    }

    @Override
    public boolean equals(Object obj){
        if(isRuined()){ 
            return false;
        }
        if(obj == null){
            return false;
        }
        if(obj == this){
            return true;
        }
        if(obj != null && obj instanceof NoteBytes){
            NoteBytes noteBytesObj = (NoteBytes) obj;
            if(noteBytesObj.isRuined()){
                return false;
            }
            byte[] objValue = noteBytesObj.get();
            if(byteLength() != objValue.length){
                return false;
            }
            byte objType = noteBytesObj.getByteDecoding().getType();
            byte thisType = getByteDecoding().getType();
            if(objType != thisType){
                return false;
            }
            return compareBytes(objValue);
        }
        if(obj instanceof byte[]){
            return compareBytes((byte[]) obj);
        }
        if(obj instanceof String){
            return equalsString((String) obj);
        }
        return false;
    }

    public boolean compareBytes(byte[] bytes){
        if(isRuined()){
            return false;
        }
        byte[] value = get();
        if(value.length != bytes.length){
            return false;
        }
        if(value.length == 0 && bytes.length == 0){
            return true;
        }
        return Arrays.equals(value, bytes);
    }

    public boolean equalsString(String str){
        return getAsString().equals(str);
    }

    public boolean startsWithString(String str){
        return getAsString().startsWith(str);
    }


     /**
     * Clears the byte array by replacing with empty array.
     * Use destroy() for more secure clearing.
     */
    public void clear() {
        if (m_value == null) {
            throw new IllegalStateException("NoteBytes data has been ruined and can no longer be accessed");
        }
        
        if (m_value.length > 0) {
            m_value = new byte[0];
        }
    }
    


    /**
     * Securely destroys the byte array contents before clearing.
     * Attempts to prevent JIT optimization of the clearing process.
     */
    public void destroy() {
        if (m_value == null) {
            throw new IllegalStateException("NoteBytes data has been ruined and can no longer be accessed");
        }
        
        if (m_value.length > 0) {
            // Step 1: Fill with random data to make recovery harder
            RandomService.getSecureRandom().nextBytes(m_value);
            
            // Step 2: Zero out the array
            Arrays.fill(m_value, (byte) 0);
            // Step 5: Replace with empty array
            clear();
        }
    }

     /**
     * Completely ruins the NoteBytes object, making it unusable.
     * Use when the object should never be accessed again.
     */
    public void ruin() {
        if (m_value != null) {
            destroy();
        }
        m_value = null;
    }

    public boolean isRuined(){
        return m_value == null;
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

    public NoteBytesArrayReadOnly getAsNoteBytesArrayReadOnly(){
        return new NoteBytesArrayReadOnly(get());
    }

    public boolean isEmpty(){
        return get().length == 0;
    }


    public JsonElement getAsJsonElement() {
        byte type = getByteDecoding().getType();
        
        switch(type) {
            case NoteBytesMetaData.NOTE_BYTES_TREE_TYPE:
                return getAsJsonObject();
            case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                return getAsJsonObject();
            case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                return getAsJsonArray();
            case NoteBytesMetaData.BIG_DECIMAL_TYPE:
                return new JsonPrimitive(getAsBigDecimal());
            case NoteBytesMetaData.BIG_INTEGER_TYPE:
                return new JsonPrimitive(getAsBigInteger());
            case NoteBytesMetaData.LONG_TYPE:
                return new JsonPrimitive(getAsLong());
            case NoteBytesMetaData.INTEGER_TYPE:
                return new JsonPrimitive(getAsInt());
            case NoteBytesMetaData.DOUBLE_TYPE:
                return new JsonPrimitive(getAsDouble());
            case NoteBytesMetaData.BOOLEAN_TYPE:
                return new JsonPrimitive(getAsBoolean());
            case NoteBytesMetaData.STRING_TYPE:
            case NoteBytesMetaData.STRING_UTF16_TYPE:
                return new JsonPrimitive(getAsString());
            default:
                return new JsonPrimitive(getAsString());
        }
    }

    public JsonObject getAsJsonObject(){
        byte type = getByteDecoding().getType();
        try{
            switch(type){
                case NoteBytesMetaData.NOTE_BYTES_TREE_TYPE:
                    return new NoteBytesTree(get()).getAsJsonObject();
                case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                    return new NoteBytesObject(get()).getAsJsonObject();
                case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                    return new NoteBytesArray(get()).getAsJsonObject();
                default:
                    return new JsonParser().parse(getAsString()).getAsJsonObject();
            }
        }catch(Exception e){
            return new JsonObject();
        }
    }

    public JsonArray getAsJsonArray(){
        byte type = getByteDecoding().getType();
        try{
            switch(type){
                case NoteBytesMetaData.NOTE_BYTES_TREE_TYPE:
                    return new NoteBytesTreeAsync(get()).getAsJsonArray();
                case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                    return new NoteBytesObject(get()).getAsJsonArray();
                case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                    return new NoteBytesArray(get()).getAsJsonArray();
                default:
                    return new JsonParser().parse(getAsString()).getAsJsonArray();
            }
        }catch(Exception e){
            return new JsonArray();
        }
    }

    public static NoteBytesObject jsonObjectToNoteBytesObject(JsonObject jsonObject) {
        if (jsonObject == null) {
            return new NoteBytesObject();
        }
        NoteBytesObject noteBytesObject = new NoteBytesObject();
        
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            
            // Convert the value using existing jsonElementToNoteBytes method
            NoteBytes convertedValue = jsonElementToNoteBytes(value);
            
            // Add the key-value pair to NoteBytesObject
            noteBytesObject.add(key, convertedValue);
        }
        
        return noteBytesObject;
    }

    public static NoteBytesArray jsonArrayToNoteBytesArray(JsonArray jsonArray) {
        NoteBytesArray noteBytesArray = new NoteBytesArray();
        
        if (jsonArray != null) {
            for (JsonElement element : jsonArray) {
                if (element.isJsonObject()) {
                    // Convert JsonObject to NoteBytesObject
                    NoteBytesObject nbo = new NoteBytesObject();
                    JsonObject jsonObj = element.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : jsonObj.entrySet()) {
                        nbo.add(entry.getKey(), jsonElementToNoteBytes(entry.getValue()));
                    }
                    noteBytesArray.add(nbo);
                } else if (element.isJsonArray()) {
                    // Recursively convert nested JsonArray
                    noteBytesArray.add(jsonArrayToNoteBytesArray(element.getAsJsonArray()));
                } else {
                    // Convert JsonPrimitive to NoteBytes
                    noteBytesArray.add(jsonElementToNoteBytes(element));
                }
            }
        }
        return noteBytesArray;
    }

    public static NoteBytes jsonElementToNoteBytes(JsonElement element) {
        if (element.isJsonNull()) {
            return new NoteBytes(new byte[0]);
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return new NoteBytes(new byte[]{(byte)(primitive.getAsBoolean() ? 1 : 0)}, ByteDecoding.BOOLEAN);
            } else if (primitive.isNumber()) {
                if (primitive.getAsString().contains(".")) {
                    return new NoteBytes(ByteDecoding.doubleToBytesLittleEndian(primitive.getAsDouble()), ByteDecoding.DOUBLE);
                } else {
                    return new NoteBytes(ByteDecoding.longToBytesLittleEndian(primitive.getAsLong()), ByteDecoding.LONG);
                }
            } else {
                return new NoteBytes(primitive.getAsString());
            }
        } else if (element.isJsonArray()) {
            return jsonArrayToNoteBytesArray(element.getAsJsonArray());
        } else if (element.isJsonObject()) {
            NoteBytesObject nbo = new NoteBytesObject();
            JsonObject jsonObj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : jsonObj.entrySet()) {
                nbo.add(entry.getKey(), jsonElementToNoteBytes(entry.getValue()));
            }
            return nbo;
        }
        return new NoteBytes(new byte[0]);
    }

    public static NoteBytes createNoteBytes(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Cannot create NoteBytes from null object");
        }else if(obj instanceof NoteBytes){
            return (NoteBytes) obj;
        }else if (obj instanceof Boolean) {
            return new NoteBoolean((Boolean) obj);
        } else if (obj instanceof Integer) {
            return new NoteInteger((Integer) obj);
        } else if (obj instanceof Long) {
            return new NoteLong((Long) obj);
        } else if (obj instanceof Double) {
            return new NoteDouble((Double) obj);
        } else if (obj instanceof BigInteger) {
            return new NoteBigInteger((BigInteger) obj);
        } else if (obj instanceof BigDecimal) {
            return new NoteBigDecimal((BigDecimal) obj);
        } else if (obj instanceof String) {
            return new NoteString((String) obj);
        } else if (obj instanceof byte[]) {
            return new NoteBytes((byte[]) obj);
        }else if(obj instanceof Byte[]){
            return new NoteBytes(ByteDecoding.unboxBytes((Byte[]) obj));
        } else if (obj instanceof char[]) {
            return new NoteBytes((char[]) obj);
        } else if (obj instanceof JsonObject) {
            return new NoteJsonObject((JsonObject) obj);
        } else if (obj instanceof NoteBytesPair) {
            return new NoteBytesObject(new NoteBytesPair[]{(NoteBytesPair) obj});
        } else if (obj instanceof NoteBytesPair[]) {
            return new NoteBytesObject((NoteBytesPair[]) obj);
        } else if( obj instanceof NoteBytesMapEphemeral){
            return ((NoteBytesMapEphemeral) obj).getNoteBytesEphemeral();
        }else if( obj instanceof NoteBytesConcurrentMapEphemeral){
            return ((NoteBytesConcurrentMapEphemeral) obj).getNoteBytesEphemeral();
        } else if(obj instanceof NoteBytesMap){
            return ((NoteBytesMap)obj).getNoteBytesObject();
        }else if(obj instanceof Serializable){
            try{
                return new NoteSerializable(obj);
            }catch(IOException e){
                throw new IllegalArgumentException(e);
            }
        }
        
        throw new IllegalArgumentException("Unsuported type");
    }
}
