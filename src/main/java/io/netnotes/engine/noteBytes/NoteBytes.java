package io.netnotes.engine.noteBytes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import com.google.gson.JsonPrimitive;

import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.collections.NoteBytesArrayList;
import io.netnotes.engine.noteBytes.collections.NoteBytesConcurrentMapEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesMapEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.collections.NoteBytesPairEphemeral;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteHashing;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class NoteBytes {

    public final static int MAX_SHORT_INDEX_VALUE = NoteShort.UNSIGNED_MAX;
    
    private byte[] m_value = null;
    private byte m_type = NoteBytesMetaData.RAW_BYTES_TYPE;

    public NoteBytes( byte[] value, byte type){
        m_value = value != null ? value : new byte[0];
        m_type = type;
    }

    public NoteBytes( byte[] value){
        this(value , NoteBytesMetaData.RAW_BYTES_TYPE);
    }
    
    public NoteBytes( Byte[] value, byte type){
        this(ByteDecoding.unboxBytes(value), type);
    }

    public NoteBytes( Object value){
         this(of(value));
    }

    public NoteBytes( NoteBytes value){
         this(value.get(), value.getType());
    }

    public NoteBytes( String value, byte type){
        this(ByteDecoding.stringToBytes(value, type), type);
    }
    public NoteBytes( String value){
        this(value, NoteBytesMetaData.STRING_TYPE);
    }


    public NoteBytes( char[] value, byte type){
        this( ByteDecoding.charsToByteArray(value, type), type);
    }

    public boolean isLittleEndian(){
        return ByteDecoding.isLittleEndian(m_type);
    }
 
    public ByteBuffer getByteByffer(){
        return ByteBuffer.wrap(getBytes());
    }

    public void set(byte[] value, byte type) {
        m_value = value != null ? value : new byte[0];
        m_type = type;
        dataUpdated();
    }

    public void set( byte[] value){
        set(value, m_type);
    }


    public byte getType(){
        return m_type;
    }

    public void setType(byte type){
        m_type = type;
        dataUpdated();
    }
    

    public void dataUpdated(){
    
    }


    public static int byteToInt(byte b){
        return b & 0xff;
    }
   
    public byte[] getAsBase64Decoded(){
        return Base64.getDecoder().decode(m_value);
    }

    public byte[] getAsBase64Encoded(){
        return Base64.getEncoder().encode(m_value);
    }
    public byte[] getAsUrlSafeBase64Encoded(){
        return Base64.getUrlEncoder().encode(m_value);
    }


    public String getAsBase64String(){
        return ByteDecoding.bytesToString(m_value, NoteBytesMetaData.BASE_64_TYPE);
    }

     public char[] getAsUrlSafeChars(){ 
        return ByteDecoding.bytesToCharArray(getAsUrlSafeBase64Encoded(), NoteBytesMetaData.STRING_US_ASCII_TYPE);
    }

    public String getAsUrlSafeString(){ 
        return ByteDecoding.bytesToString(get(),  NoteBytesMetaData.URL_SAFE_TYPE);
    }

    public String getAsHexString(){
       return ByteDecoding.bytesToString(get(),  NoteBytesMetaData.BASE_16_TYPE);
    }

    public String getAsBase32String(){
       return ByteDecoding.bytesToString(get(),  NoteBytesMetaData.BASE_32_TYPE);
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
        return ByteDecoding.bytesToCharArray(getBytes(), NoteBytesMetaData.UTF_8_TYPE);
    }

    public String getAsString(){
        return ByteDecoding.bytesToString(m_value, m_type);
    }

    @Override
    public String toString(){
        switch(m_type){
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
                return getAsString();
        }
    }

    public char[] decodeCharArray(){
        return get().length > 0 ? ByteDecoding.bytesToCharArray(getBytes(), m_type) : new char[0];
    }

    public char[] getChars(){
        return decodeCharArray();
    }


    public CharBuffer getAsCharBuffer(){
        byte[] bytes = getBytes();
        if(bytes != null && bytes.length > 0){
            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            return ByteDecoding.bytesToChars(byteBuffer, m_type);
            
        }
        return CharBuffer.wrap( new char[0]);
    }

    public void setValueInteger(int value){

        set(ByteDecoding.isLittleEndian(m_type) ? ByteDecoding.intToBytesLittleEndian(value) : ByteDecoding.intToBytesBigEndian(value));
    }


    public boolean getAsBoolean(){
        
        byte[] bytes = get();
        int len = bytes.length;
        return len == 0 ? false : ByteDecoding.bytesToBoolean(bytes);  
    }

    public double getAsDouble(){
   
        byte[] bytes = get();
        int len = bytes.length;
        return len == 0 ? 0 : ( m_type == NoteBytesMetaData.DOUBLE_LE_TYPE  ? ByteDecoding.bytesToDoubleLittleEndian(bytes) : ByteDecoding.bytesToDoubleBigEndian(bytes));
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
        return len == 0 ? 0 : m_type == NoteBytesMetaData.INTEGER_LE_TYPE ? ByteDecoding.bytesToIntLittleEndian(bytes) : ByteDecoding.bytesToIntBigEndian(bytes);
    }

    public short getAsShort(){
        byte[] bytes = get();
        int len = bytes.length;
        return len == 0 ? 0 : m_type == NoteBytesMetaData.SHORT_LE_TYPE ? ByteDecoding.bytesToShortLittleEndian(bytes) : ByteDecoding.bytesToShortBigEndian(bytes);
    }

     public long getAsLong(){
        byte[] bytes = get();
        int len = bytes.length;
        return len == 0 ? 0 : m_type == NoteBytesMetaData.LONG_LE_TYPE ? ByteDecoding.bytesToIntLittleEndian(bytes) : ByteDecoding.bytesToIntBigEndian(bytes);
    }

    public BigDecimal getAsBigDecimal(){
        byte[] bytes = get();
        int len = bytes.length;
        if(len < 5) {
            return BigDecimal.ZERO;
        }
        return ByteDecoding.bytesToBigDecimal(bytes);
    }

    
    public int getAsIntegerLittleEndian(){
        byte[] bytes = get();
        int len = bytes.length;
        return len == 0 ? 0 : ByteDecoding.bytesToIntLittleEndian(Arrays.copyOf(bytes, 4));
    }

     public InputStream getAsInputStream() {
        return new ByteArrayInputStream(get());
    }


    public String getAsUTF8(){
        byte[] bytes = get();
        return new String(bytes);
    }

    public String getAsISO(){
        return new String(get(), StandardCharsets.ISO_8859_1);
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
        final int metaDataSize = NoteBytesMetaData.STANDARD_META_DATA_SIZE;
        byte[] src = noteBytes.get(); 
        int srcLength = src.length;
   
        if(dstLength >= dstOffset + metaDataSize + srcLength){
            dstOffset = NoteBytesMetaData.write(noteBytes.getType(), srcLength, dst, dstOffset);
            System.arraycopy(src, 0, dst, dstOffset, srcLength);
            return dstOffset + srcLength;
        }else{
            throw new IndexOutOfBoundsException("insufficient destination length");
        }
    }

    public NoteBytes copy(){
        return new NoteBytes(Arrays.copyOf(get(), byteLength()), m_type);
    }

    public static int writeNote(NoteBytes noteBytes, OutputStream outputStream) throws IOException {
        byte type = noteBytes.getType();
        byte[] bytes = noteBytes.get();
        outputStream.write(type);
        outputStream.write(ByteDecoding.intToBytesBigEndian(noteBytes.byteLength()));
        outputStream.write(bytes);
        return NoteBytesMetaData.STANDARD_META_DATA_SIZE + bytes.length;
    }

    public static NoteBytes readNote(byte[] src, int srcOffset){
        final int metaDataSize = NoteBytesMetaData.STANDARD_META_DATA_SIZE;

        if (src.length < srcOffset + metaDataSize) {
            throw new IndexOutOfBoundsException("insufficient source length for metadata");
        }

        // 1. Read type
        byte type = src[srcOffset];

        // 2. Read length
        int length = 
            ((src[srcOffset + 1] & 0xFF) << 24) |
            ((src[srcOffset + 2] & 0xFF) << 16) |
            ((src[srcOffset + 3] & 0xFF) << 8)  |
            (src[srcOffset + 4] & 0xFF);

        if (src.length < srcOffset + metaDataSize + length) {
            throw new IndexOutOfBoundsException("insufficient source length for data");
        }

        // 3. Copy payload
        byte[] data = new byte[length];
        System.arraycopy(src, srcOffset + metaDataSize, data, 0, length);

        // 4. Construct NoteBytes
        
        return new NoteBytes(data, type);
    }

    public static NoteBytes readNote(byte[] bytes, int offset, boolean isLittleEndian){
        byte type = bytes[offset];
        offset++;
        int size = isLittleEndian ? ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
        byte[] dst = new byte[size];
        System.arraycopy(bytes, offset + 4, dst, 0, size);
        return new NoteBytes(dst, type);
    }


    @Override
    public int hashCode(){
        byte[] bytes = get();
        return bytes.length == 0 ? 0 : ByteHashing.getHashCode(bytes, m_type);
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
            byte objType = noteBytesObj.getType();
            byte thisType = m_type;
            if(objType != thisType){
                return false;
            }
            byte[] objValue = noteBytesObj.get();
            if(byteLength() != objValue.length){
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
        if(str == null){
            throw new NullPointerException("NoteBytes.equalsString str is null");
        }
        return getAsString().equals(str);
    }

    public boolean startsWithString(String str){
        if(str == null){
            throw new NullPointerException("NoteBytes.startsWithString str is null");
        }
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
        
        switch(m_type) {
            case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                return getAsJsonObject();
            case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                return getAsJsonArray();
            case NoteBytesMetaData.BIG_DECIMAL_TYPE:
                return new JsonPrimitive(getAsBigDecimal());
            case NoteBytesMetaData.BIG_INTEGER_TYPE:
                return new JsonPrimitive(getAsBigInteger());
            case NoteBytesMetaData.LONG_LE_TYPE:
            case NoteBytesMetaData.LONG_TYPE:
                return new JsonPrimitive(getAsLong());
            case NoteBytesMetaData.INTEGER_LE_TYPE:
            case NoteBytesMetaData.INTEGER_TYPE:
                return new JsonPrimitive(getAsInt());
            case NoteBytesMetaData.DOUBLE_LE_TYPE:
            case NoteBytesMetaData.DOUBLE_TYPE:
                return new JsonPrimitive(getAsDouble());
            case NoteBytesMetaData.BOOLEAN_TYPE:
                return new JsonPrimitive(getAsBoolean());
            case NoteBytesMetaData.STRING_TYPE:
            case NoteBytesMetaData.STRING_UTF16_TYPE:
            default:
                return new JsonPrimitive(getAsString());
        }
    }

    public JsonObject getAsJsonObject(){
        byte type = m_type;
        try{
            switch(type){

                case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                    return new NoteBytesObject(get()).getAsJsonObject();
                case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                    return new NoteBytesArray(get()).getAsJsonObject();
                default:
                    JsonObject json = new JsonObject();
                    json.add("value", getAsJsonElement());
                    json.add("type", new JsonPrimitive((int) m_type));
                    return json;
            }
        }catch(Exception e){
            return new JsonObject();
        }
    }

    public JsonArray getAsJsonArray(){
        byte type = m_type;
        try{
            switch(type){
                case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                    return new NoteBytesObject(get()).getAsJsonArray();
                case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                    return new NoteBytesArray(get()).getAsJsonArray();
                default:
                    JsonArray jsonArray = new JsonArray();
                    jsonArray.add(getAsJsonObject());
                    return jsonArray;
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
                return new NoteBytes(new byte[]{(byte)(primitive.getAsBoolean() ? 1 : 0)}, NoteBytesMetaData.BOOLEAN_TYPE);
            } else if (primitive.isNumber()) {
                String numbericString = primitive.getAsString();
                int numbericStringLength = numbericString.length();
                if (numbericString.contains(".")) {
                    return numbericStringLength > 14 ? new NoteBigDecimal(new BigDecimal(numbericString)) : new NoteDouble(primitive.getAsDouble());
                } else {
                    return numbericStringLength > 18 ? new NoteBigInteger(new BigInteger(numbericString)) : (numbericStringLength > 9 ? new NoteLong(primitive.getAsLong()) : new NoteInteger(primitive.getAsInt()));
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

    public static NoteBytes of(byte[] bytes, byte type){
        switch(type){
            case NoteBytesMetaData.LONG_TYPE:
                return new NoteLong(bytes);
            case NoteBytesMetaData.DOUBLE_TYPE:
                return new NoteDouble(bytes);
            case NoteBytesMetaData.INTEGER_TYPE:
                return new NoteInteger(bytes);
            case NoteBytesMetaData.STRING_TYPE:
                return new NoteString(bytes);
            case NoteBytesMetaData.BOOLEAN_TYPE:
                return new NoteBoolean(bytes);
            case NoteBytesMetaData.SHORT_TYPE:
                return new NoteShort(bytes);
            case NoteBytesMetaData.FLOAT_TYPE:
                return new NoteFloat(bytes);
            case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                return new NoteBytesArray(bytes);
            case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                return new NoteBytesObject(bytes);
            case NoteBytesMetaData.BIG_DECIMAL_TYPE:
                return new NoteBigDecimal(bytes);
            case NoteBytesMetaData.BIG_INTEGER_TYPE:
                return new NoteBigDecimal(bytes);
            case NoteBytesMetaData.SERIALIZABLE_OBJECT_TYPE:
                return new NoteSerializable(bytes);
            case NoteBytesMetaData.RAW_BYTES_TYPE:
            default:
                return new NoteBytes(bytes, type);
        }
    }

    public static NoteBytes of(Object obj) {
        if (obj == null) {
            throw new NullPointerException("Cannot create NoteBytes from null object");
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
        } else if (obj instanceof NoteBytesPairEphemeral) {
            return new NoteBytesEphemeral(new NoteBytesPairEphemeral[]{(NoteBytesPairEphemeral) obj});
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
        }else if(obj instanceof NoteSerializable){
            return (NoteSerializable) obj;
        }else if(obj instanceof NoteBytesArrayList){
            return ((NoteBytesArrayList) obj).getNoteBytesArray();
        }else if(obj instanceof NoteBytes){
            return (NoteBytes) obj;
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
