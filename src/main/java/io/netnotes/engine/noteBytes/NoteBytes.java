package io.netnotes.engine.noteBytes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonPrimitive;

import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.collections.NoteBytesConcurrentMapEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesMapEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.collections.NoteBytesPairEphemeral;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteEncoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.ByteEncoding.EncodingType;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class NoteBytes {

    public final static int MAX_SHORT_INDEX_VALUE = NoteShort.UNSIGNED_MAX;
    public static volatile int clearanceVerifier = 0;
    private byte[] m_value = new byte[0];
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

    public NoteBytes( NoteBytes value){
         this(value.get(), value.getType());
    }

    public NoteBytes(boolean value){
        this(ByteDecoding.booleanToBytes(value), NoteBytesMetaData.BOOLEAN_TYPE);
    }

    public NoteBytes( String value){
        this( ByteDecoding.stringToBytes(value,  NoteBytesMetaData.STRING_TYPE), NoteBytesMetaData.STRING_TYPE);
    }
    public NoteBytes(char[] chars){
        this( chars, NoteBytesMetaData.STRING_TYPE);
    }

    public NoteBytes(int integer){
        this(ByteDecoding.intToBytesBigEndian(integer), NoteBytesMetaData.INTEGER_TYPE);
    }

    public NoteBytes(double d){
        this(ByteDecoding.doubleToBytesBigEndian(d), NoteBytesMetaData.DOUBLE_TYPE);
    }

    public NoteBytes(long l){
        this(ByteDecoding.longToBytesBigEndian(l), NoteBytesMetaData.LONG_TYPE);
    }

    public NoteBytes(BigInteger bigInteger){
        this(bigInteger.toByteArray(), NoteBytesMetaData.BIG_INTEGER_TYPE);
    }

    public NoteBytes(BigDecimal bigDecimal){
        this(ByteDecoding.bigDecimalToScaleAndBigInteger(bigDecimal), NoteBytesMetaData.BIG_DECIMAL_TYPE);
    }

    public NoteBytes( char[] value, byte type){
        this( ByteDecoding.charsToByteArray(value, type), type);
    }

    public boolean isLittleEndian(){
        return ByteDecoding.isLittleEndian(m_type);
    }
 
    public ByteBuffer getByteByffer(){
        return ByteBuffer.wrap(this instanceof NoteBytesReadOnly readOnly ? readOnly.getBytesInternal() : this.get());
    }

    public void set(byte[] value, byte type) {
        if(isRuined()){
            throw new IllegalStateException("NoteBytes data has been ruined and can no longer be accessed");
        }
        m_value = value != null ? value : new byte[0];
        m_type = type;
        dataUpdated();
    }

    public void set( byte[] value){
        set(value, m_type);
    }

    public void set(String value){
        byte type = NoteBytesMetaData.STRING_TYPE;
        set(ByteDecoding.stringToBytes(value, type), type);
    }

    protected void setInternal(byte[] value, byte type) {
        if(isRuined()){
            throw new IllegalStateException("NoteBytes data has been ruined and can no longer be accessed");
        }
        m_value = value != null ? value : new byte[0];
        m_type = type;
        dataUpdated();
    }

    protected void setInternal(byte[] value) {
        setInternal(value, getType());
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

    public Float getAsFloat(){
        byte[] bytes = m_value;
        byte type = m_type;
        return type == NoteBytesMetaData.FLOAT_LE_TYPE ? ByteDecoding.bytesToFloatLittleEndian(bytes) :
            ByteDecoding.bytesToFloatBigEndian(bytes);
    }

    public static int byteToInt(byte b){
        return b & 0xff;
    }
   

    public NoteBytes concat(NoteBytes... list) {
        int len = byteLength();

        // Count total length
        for (NoteBytes b : list) {
            len += b.byteLength();
        }

        // Allocate once
        byte[] dst = new byte[len];

        // Copy first
        int pos = 0;
        byte[] aBytes = get();
        System.arraycopy(aBytes, 0, dst, 0, aBytes.length);
        pos += aBytes.length;
        byte type = getType();
        // Copy rest
        for (NoteBytes b : list) {
            byte[] bBytes = b instanceof NoteBytesReadOnly readOnly ? readOnly.getBytesInternal() : b.get();
            if(b.getType() != type){
               type = NoteBytesMetaData.RAW_BYTES_TYPE;
            }
            System.arraycopy(bBytes, 0, dst, pos, bBytes.length);
            pos += bBytes.length;
        }

        return new NoteBytes(dst, type);
    }
    
 
    protected byte[] getBytesInternal(){
        if(isRuined()){
            throw new IllegalStateException("NoteBytes data has been ruined and can no longer be accessed");
        }
        return m_value;
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

    public byte[] getBytes(int length){
        byte[] bytes = get();
        return Arrays.copyOf(bytes, length);
    }

    public char[] getFromUTF8AsChars(){
        return ByteDecoding.bytesToCharArray(m_value, NoteBytesMetaData.STRING_TYPE);
    }

    public char[] getFromISO_8859_1AsChars(){
        return ByteDecoding.bytesToCharArray(m_value, NoteBytesMetaData.STRING_ISO_8859_1_TYPE);
    }

    public char[] getFromASCIIAsChars(){
        return ByteDecoding.bytesToCharArray(m_value, NoteBytesMetaData.STRING_US_ASCII_TYPE);
    }

    public char[] getFromUTF16AsChars(){
        return ByteDecoding.bytesToCharArray(m_value, NoteBytesMetaData.STRING_UTF16_TYPE);
    }

    public char[] getFromCodePointAsChars(){
        return ByteDecoding.bytesToCharArray(m_value, NoteBytesMetaData.NOTE_INTEGER_ARRAY_TYPE);
    }

    public String getAsString(){
        return ByteDecoding.bytesToString(m_value, m_type);
    }

    public String getAsUrlSafeString(){
        return ByteDecoding.bytesToUrlSafeString(m_value);
    }

    @Override
    public String toString(){
        return ByteDecoding.displayByteValueAsString(m_value, m_type);
    }

    public char[] getAsChars(){
        return ByteDecoding.bytesToCharArray(m_value, m_type);
    }


    public void setInteger(int value){

        set(ByteDecoding.isLittleEndian(m_type) ? ByteDecoding.intToBytesLittleEndian(value) : ByteDecoding.intToBytesBigEndian(value));
    }

    public Object getAsObject() throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(m_value);
            ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        }
    }


    public boolean getAsBoolean(){
        
        byte[] bytes = m_value;
        int len = bytes.length;
        return len == 0 ? false : ByteDecoding.bytesToBoolean(bytes);  
    }

    public double getAsDouble(){
   
        byte[] bytes = m_value;
        int len = bytes.length;
        return len == 0 ? 0 : ( m_type == NoteBytesMetaData.DOUBLE_LE_TYPE  ? ByteDecoding.bytesToDoubleLittleEndian(bytes) : ByteDecoding.bytesToDoubleBigEndian(bytes));
    }

    public byte getAsByte(){
        byte[] bytes = m_value;
        if(byteLength() == 0){
            return 0;
        }
        return bytes[0];
    }

    public int getAsInt(){
        byte[] bytes = m_value;
        int len = bytes.length;
        return len == 0 ? 0 : m_type == NoteBytesMetaData.INTEGER_LE_TYPE ? ByteDecoding.bytesToIntLittleEndian(bytes) : ByteDecoding.bytesToIntBigEndian(bytes);
    }

    public short getAsShort(){
        byte[] bytes = m_value;
        int len = bytes.length;
        return len == 0 ? 0 : m_type == NoteBytesMetaData.SHORT_LE_TYPE ? ByteDecoding.bytesToShortLittleEndian(bytes) : ByteDecoding.bytesToShortBigEndian(bytes);
    }

     public long getAsLong(){
        byte[] bytes = m_value;
        int len = bytes.length;
        return len == 0 ? 0 : m_type == NoteBytesMetaData.LONG_LE_TYPE 
            ? ByteDecoding.bytesToLongLittleEndian(bytes) 
            : ByteDecoding.bytesToLongBigEndian(bytes);
    }

    public BigDecimal getAsBigDecimal(){
        byte[] bytes = m_value;
        int len = bytes.length;
        if(len < 5) {
            return BigDecimal.ZERO;
        }
        return ByteDecoding.bytesToBigDecimal(bytes);
    }

    
    public int getAsIntegerLittleEndian(){
        byte[] bytes = m_value;
        int len = bytes.length;
        return len == 0 ? 0 : ByteDecoding.bytesToIntLittleEndian(Arrays.copyOf(bytes, 4));
    }

     public InputStream getAsInputStream() {
        return new ByteArrayInputStream(this instanceof NoteBytesReadOnly readOnly ? readOnly.getBytesInternal() : this.get());
    }


    public String getAsStringUTF8(){
        byte[] bytes = m_value;
        return new String(bytes);
    }

    public String getAsStringISO_8859_1(){
        return new String(m_value, StandardCharsets.ISO_8859_1);
    }


    public BigInteger getAsBigInteger(){
        int len = byteLength();
        return len == 0 || (len == 1 && m_value[0] == 0) ? BigInteger.ZERO : new BigInteger(m_value);
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
            String errMsg = "insufficient destination length";
            Log.logError("[NoteBytes.writeNote] " + errMsg +
                " dstLength=" + dstLength +
                " dstOffset=" + dstOffset +
                " required=" + (dstOffset + metaDataSize + srcLength)
            );
            throw new IndexOutOfBoundsException(errMsg);
        }
    }

    public static int writeNote(byte[] bytes, byte[] dst, int dstOffset){
        int dstLength = dst.length;
        final int metaDataSize = NoteBytesMetaData.STANDARD_META_DATA_SIZE;
        
        int srcLength = bytes.length;
   
        if(dstLength >= dstOffset + metaDataSize + srcLength){
            dstOffset = NoteBytesMetaData.write(NoteBytesMetaData.RAW_BYTES_TYPE, srcLength, dst, dstOffset);
            System.arraycopy(bytes, 0, dst, dstOffset, srcLength);
            return dstOffset + srcLength;
        }else{
            throw new IndexOutOfBoundsException("insufficient destination length");
        }
    }

    public NoteBytes copy(){
        if(byteLength() > 0){
            byte[] bytes = m_value;
            byte[] newbytes = new byte[bytes.length];
            System.arraycopy(bytes,0, newbytes, 0, bytes.length);
            return new NoteBytes(newbytes, m_type);
        }else{
            return new NoteBytes(new byte[0], m_type);
        }
    }

    public NoteBytesReadOnly readOnly(){

        if(this instanceof NoteBytesReadOnly readOnly){
            return readOnly;
        }
        if(byteLength() > 0){
            byte[] bytes = m_value;
            byte[] newbytes = new byte[bytes.length];
            System.arraycopy(bytes,0, newbytes, 0, bytes.length);
            return new NoteBytesReadOnly(newbytes, m_type);
        }else{
            return new NoteBytesReadOnly(new byte[0], m_type);
        }
    }

    public NoteBytes copyOf(int length){
        return new NoteBytes(Arrays.copyOf(m_value, length), m_type);
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
        byte[] bytes = m_value;
        return bytes.length == 0 ? 0 : HashServices.getHashCode(bytes, m_type);
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
        if(obj != null && obj instanceof NoteBytes noteBytesObj){
  
            if(noteBytesObj.isRuined()){
                return false;
            }
            byte objType = noteBytesObj.getType();
            byte thisType = getType();
            if(objType != thisType){
                return false;
            }
            byte[] objValue =  noteBytesObj instanceof NoteBytesReadOnly readOnly ? readOnly.getBytesInternal() : noteBytesObj.get();
            if(byteLength() != objValue.length){
                return false;
            }
            return equalsBytes(objValue);
        }
        if(obj instanceof byte[] bytes){
            return equalsBytes(bytes);
        }
        if(obj instanceof String str){
            return equalsString(str);
        }
        return false;
    }

    public boolean containsBytes(byte[] bytes){
        return ByteDecoding.containsBytes(m_value, bytes);
    }

    public boolean containsBytes(NoteBytes bytes){
        return ByteDecoding.containsBytes(m_value, bytes.get());
    }

    public int compare(NoteBytes noteBytes){
        byte[] bytes = noteBytes instanceof NoteBytesReadOnly readOnly ? readOnly.getBytesInternal() : noteBytes.get();
        return compareBytes(bytes);
    }

    public int compareBytes(byte[] bytes){
        return Arrays.compare( this instanceof NoteBytesReadOnly readOnly ? readOnly.getBytesInternal() : get(), bytes);
    }

    public boolean constantTimmEqualsBytes(byte[] bytes){
        return ByteDecoding.constantTimeCompare(this instanceof NoteBytesReadOnly readOnly ? readOnly.getBytesInternal() : get(), bytes);
    }

    public boolean constantTimeEquals(NoteBytes noteBytes){
        return ByteDecoding.constantTimeCompare(this instanceof NoteBytesReadOnly readOnly 
            ? readOnly.getBytesInternal() : get(), noteBytes instanceof NoteBytesReadOnly readOnly ? readOnly.getBytesInternal() : noteBytes.get());
    }

    public boolean equalsBytes(byte[] bytes){
        if(isRuined()){
            return false;
        }
        byte[] value = this instanceof NoteBytesReadOnly readOnly ? readOnly.getBytesInternal() : get();
        
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
            RandomService.getSecureRandom().nextBytes(m_value);
            Arrays.fill(m_value, (byte) 0);
            for (int i = 0; i < m_value.length; i++) {
                clearanceVerifier ^= m_value[i]; 
                if (m_value[i] != 0) {
                    System.err.println("Warning: Memory clear verification failed " + m_value[i] + " " + clearanceVerifier);
                }
            }
            Thread.yield();
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
        return m_value.length;
    }


    public NoteBytesObject getAsNoteBytesObject(){
        return new NoteBytesObject(m_value);
    }

    public NoteBytesMap getAsNoteBytesMap(){
        return getAsMap();
    }

    public NoteBytesMap getAsMap(){
        return new NoteBytesMap(m_value);
    }



    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getAsHashMap() {
        byte[] bytes = get();
        int length = bytes.length;
        Map<Object, Object> rawMap = new HashMap<>();

        int offset = 0;
        while (offset < length) {
            NoteBytes keyBytes = NoteBytes.readNote(bytes, offset);
            offset += NoteBytesMetaData.STANDARD_META_DATA_SIZE + keyBytes.byteLength();

            NoteBytes valueBytes = NoteBytes.readNote(bytes, offset);
            offset += NoteBytesMetaData.STANDARD_META_DATA_SIZE + valueBytes.byteLength();

            Object key = from(keyBytes);
            Object value = from(valueBytes);

            rawMap.put(key, value);
        }

        // Safe unchecked cast since contents are strongly typed by NoteBytes
        return (Map<K, V>) rawMap;
    }

    public NoteBytesArray getAsNoteBytesArray(){
        return new NoteBytesArray(get());
    }

    public NoteBytesArrayReadOnly getAsNoteBytesArrayReadOnly(){
        return new NoteBytesArrayReadOnly(get());
    }

    public NoteIntegerArray getAsNoteIntegerArray(){
        return new NoteIntegerArray(m_value);
    }


    public boolean isEmpty(){
        return m_value.length == 0;
    }

    
    public JsonElement getAsJsonElement() {

        return createPrimitiveForType(this);
    }

    public static JsonPrimitive createPrimitiveForType(NoteBytes noteBytes){
        switch(noteBytes.getType()) {
            case NoteBytesMetaData.BIG_DECIMAL_TYPE:
                return new JsonPrimitive(noteBytes.getAsBigDecimal());
            case NoteBytesMetaData.BIG_INTEGER_TYPE:
                return new JsonPrimitive(noteBytes.getAsBigInteger());
            case NoteBytesMetaData.LONG_LE_TYPE:
            case NoteBytesMetaData.LONG_TYPE:
                return new JsonPrimitive(noteBytes.getAsLong());
            case NoteBytesMetaData.INTEGER_LE_TYPE:
            case NoteBytesMetaData.INTEGER_TYPE:
                return new JsonPrimitive(noteBytes.getAsInt());
            case NoteBytesMetaData.DOUBLE_LE_TYPE:
            case NoteBytesMetaData.DOUBLE_TYPE:
                return new JsonPrimitive(noteBytes.getAsDouble());
            case NoteBytesMetaData.BOOLEAN_TYPE:
                return new JsonPrimitive(noteBytes.getAsBoolean());
            case NoteBytesMetaData.NOTE_INTEGER_ARRAY_TYPE:
                return new JsonPrimitive(noteBytes.getAsString());
            case NoteBytesMetaData.STRING_TYPE:
                return new JsonPrimitive(noteBytes.getAsString());
            default:
                if(ByteDecoding.isStringType(noteBytes.getType())){
                    return new JsonPrimitive(noteBytes.getAsString());
                }else{
                    return new JsonPrimitive(noteBytes.toString());
                }
        }
    }

    public JsonObject getAsJsonObject(){
        byte type = m_type;
        try{
            switch(type){
                case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                    return new NoteBytesObject(m_value).getAsJsonObject();
                case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                    return new NoteBytesArray(m_value).getAsJsonObject();
                default:
                    JsonObject json = new JsonObject();
                    json.add("value", createPrimitiveForType(this));
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
                    return new NoteBytesObject(m_value).getAsJsonArray();
                case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                    return new NoteBytesArray(m_value).getAsJsonArray();
                default:
                    JsonArray jsonArray = new JsonArray();
                    jsonArray.add(createPrimitiveForType(this));
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

    public byte[] getHash(int digestLength){
        byte[] bytes = this instanceof NoteBytesReadOnly readOnly ? readOnly.getBytesInternal() : this.get();

        return HashServices.digestBytesToBytes(bytes, digestLength);
    }



    public NoteUUID getAsNoteUUID(){ 
        return NoteUUID.fromStringBytes(getBytesInternal());
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
            case NoteBytesMetaData.NOTE_INTEGER_ARRAY_TYPE:
                return new NoteIntegerArray(bytes);
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
        }else if(obj instanceof ContextPath path){
            return path.getSegments();
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
        }  else if (obj instanceof byte[]) {
            return new NoteBytes((byte[]) obj);
        }else if(obj instanceof Byte[]){
            return new NoteBytes(ByteDecoding.unboxBytes((Byte[]) obj));
        } else if (obj instanceof char[]) {
            return new NoteBytes((char[]) obj);
        } else if (obj instanceof JsonElement element) {
            return fromJson(element);
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
            return ((NoteBytesMap)obj).toNoteBytes();
        }else if(obj instanceof NoteSerializable){
            return (NoteSerializable) obj;
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

    private static Object from(NoteBytes noteBytes) {
        byte[] data = noteBytes.get();

        switch (noteBytes.getType()) {
            case NoteBytesMetaData.LONG_TYPE:
                return new NoteLong(data).getAsLong();
            case NoteBytesMetaData.STRING_TYPE:
                return new NoteString(data).getAsString();
            case NoteBytesMetaData.INTEGER_TYPE:
                return new NoteInteger(data).getAsInt();
            case NoteBytesMetaData.BOOLEAN_TYPE:
                return new NoteBoolean(data).getAsBoolean();
            case NoteBytesMetaData.DOUBLE_TYPE:
                return new NoteDouble(data).getAsDouble();
            case NoteBytesMetaData.FLOAT_TYPE:
                return new NoteFloat(data).getAsFloat();
            case NoteBytesMetaData.BIG_DECIMAL_TYPE:
                return new NoteBigDecimal(data).getAsBigDecimal();
            case NoteBytesMetaData.BIG_INTEGER_TYPE:
                return new NoteBigInteger(data).getAsBigInteger();
            default:
                return noteBytes;
        }
    }

    public static NoteBytes fromJson(JsonObject json){
        NoteBytesPair[] pairs = new NoteBytesPair[json.size()];
        int i = 0;
        for( Map.Entry<String,JsonElement> entry : json.entrySet()){
            String key = entry.getKey();
            JsonElement element = entry.getValue();
            pairs[i] = new NoteBytesPair(key, fromJson(element));
            i++;
        }
        return new NoteBytesObject(pairs);
    }

    public static NoteBytes fromJson(JsonPrimitive primitive){
        if(primitive.isJsonArray()){
            return fromJson(primitive.getAsJsonArray());
        }else if(primitive.isNumber()){
            return new NoteBytes(primitive.getAsBigDecimal());
        }else if(primitive.isString()){
            return new NoteBytes(primitive.getAsString());
        }else if(primitive.isBoolean()){
            return new NoteBytes(primitive.getAsBoolean());
        }else if(primitive.isJsonArray()){
            return fromJson(primitive.getAsJsonArray());
        }else if(primitive.isJsonObject()){
            return fromJson(primitive.getAsJsonObject());
        }else if(primitive.isJsonNull()){
            return new NoteBytes(new byte[0], NoteBytesMetaData.NULL_TYPE);
        }else{
            return new NoteBytes(primitive.getAsString());
        }
    }

    public static NoteBytes fromJson(JsonArray json){
        NoteBytes[] array = new NoteBytes[json.size()];
        for(int i = 0; i < json.size(); i++){
            JsonElement entry = json.get(i);
            array[i] = fromJson(entry);
        }
        return new NoteBytesArray(array);
    }

 

    public static NoteBytes fromJson(JsonElement element){
        if(element instanceof JsonObject json){
            return fromJson(json);
        }else if(element instanceof JsonArray array){
            return fromJson(array);
        }else if(element.isJsonArray()){
            return fromJson(element.getAsJsonArray());
        }else if(element.isJsonObject()){
            return fromJson(element.getAsJsonObject());
        }else if(element.isJsonNull()){
            return new NoteBytes(new byte[0], NoteBytesMetaData.NULL_TYPE);
        }else if(element.isJsonPrimitive()){
            return fromJson(element.getAsJsonPrimitive());
        }else{
            return new NoteBytes(element.getAsString());
        }

    }

     public static JsonElement toJson(NoteBytes noteBytes){
        byte type = noteBytes.getType();
        switch(type){
            case NoteBytesMetaData.BYTE_TYPE:
                return new JsonPrimitive(noteBytes.getAsByte());
            case NoteBytesMetaData.SHORT_TYPE:
                return new JsonPrimitive(noteBytes.getAsShort());
            case NoteBytesMetaData.INTEGER_TYPE:
                return new JsonPrimitive(noteBytes.getAsInt());
            case NoteBytesMetaData.FLOAT_TYPE:
                return new JsonPrimitive(noteBytes.getAsFloat());
            case NoteBytesMetaData.DOUBLE_TYPE:
                return new JsonPrimitive(noteBytes.getAsDouble());
            case NoteBytesMetaData.LONG_TYPE:
                return new JsonPrimitive(noteBytes.getAsLong());
            case NoteBytesMetaData.BOOLEAN_TYPE:
                return new JsonPrimitive(noteBytes.getAsBoolean());
            case NoteBytesMetaData.STRING_UTF16_TYPE:
            case NoteBytesMetaData.STRING_ISO_8859_1_TYPE:
            case NoteBytesMetaData.STRING_US_ASCII_TYPE:
            case NoteBytesMetaData.STRING_TYPE:
            case NoteBytesMetaData.NOTE_INTEGER_ARRAY_TYPE:
            case NoteBytesMetaData.STRING_UTF16_LE_TYPE:
                return new JsonPrimitive(noteBytes.getAsString());
            case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                return  noteBytes.getAsJsonObject();
            case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                return noteBytes.getAsJsonArray();
            case NoteBytesMetaData.BIG_INTEGER_TYPE:
                return new JsonPrimitive(noteBytes.getAsBigInteger());
            case NoteBytesMetaData.BIG_DECIMAL_TYPE:
                return new JsonPrimitive(noteBytes.getAsBigDecimal());
            case NoteBytesMetaData.SHORT_LE_TYPE:
                return new JsonPrimitive(noteBytes.getAsShort());
            case NoteBytesMetaData.INTEGER_LE_TYPE:
                return new JsonPrimitive(noteBytes.getAsInt());
            case NoteBytesMetaData.FLOAT_LE_TYPE:
                return new JsonPrimitive(noteBytes.getAsFloat());
            case NoteBytesMetaData.DOUBLE_LE_TYPE:
                return new JsonPrimitive(noteBytes.getAsDouble());
            case NoteBytesMetaData.LONG_LE_TYPE:
                return new JsonPrimitive(noteBytes.getAsLong());
            case NoteBytesMetaData.IMAGE_TYPE:
            case NoteBytesMetaData.VIDEO_TYPE:
            case NoteBytesMetaData.SERIALIZABLE_OBJECT_TYPE:
            case NoteBytesMetaData.NOTE_BYTES_ENCRYPTED_TYPE:
            case NoteBytesMetaData.RAW_BYTES_TYPE:
            default:
                return new JsonPrimitive(Base64.getEncoder().encodeToString(noteBytes.get()));
        }

    }

    public String toEncodedString(EncodingType type){
        return ByteEncoding.encodeString(get(), type);
    }

    public String toHexString(){
        return ByteEncoding.encodeString(get(), EncodingType.BASE_16);
    }
}
