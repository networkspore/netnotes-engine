package io.netnotes.engine.noteBytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.bouncycastle.util.encoders.Base32;
import org.bouncycastle.util.encoders.Hex;

import io.netnotes.engine.utils.Utils;

public class ByteDecoding{


    public static final byte NO_FLAG = 0x00;

    

    public static class NoteBytesMetaData {
        public final static int STANDARD_META_DATA_SIZE = 5;

        public final static byte VIDEO_TYPE = 0x54;
        public final static byte IMAGE_TYPE = 0x53;
        public final static byte NOTE_BYTES_TREE_TYPE = 0x52;
        public final static byte NOTE_BYTES_ARRAY_TYPE = 0x51;
        public final static byte NOTE_BYTES_OBJECT_TYPE = 0x50;
        public final static byte BIG_DECIMAL_TYPE = 0x49;
        public final static byte BIG_INTEGER_TYPE = 0x48;
        public final static byte SHORT_TYPE = 0x47;
        public final static byte FLOAT_TYPE = 0x46;
        public final static byte LONG_TYPE = 0x45;
        public final static byte DOUBLE_TYPE = 0x44;
        public final static byte INTEGER_TYPE = 0x43;
        public final static byte STRING_UTF16_TYPE = 0x42;
        public final static byte STRING_TYPE = 0x41;
        public final static byte BOOLEAN_TYPE = 0x40;
        public final static byte SERIALIZABLE_OBJECT_TYPE = 0x02;
        public final static byte RAW_BYTES_TYPE = 0x01;

        private byte m_type;
        private int m_len;

        public NoteBytesMetaData(byte type, int len) {
            this.m_type = type;
            this.m_len = len;
        }

        public NoteBytesMetaData(byte type, byte[] byteLen) {
            this.m_type = type;
            setLen(byteLen);
        }

        public byte getType() {
            return m_type;
        }

        public int getLength() {
            return m_len;
        }

        public void setLength(int len) {
            this.m_len = len;
        }

        public void setLen(byte[] bytes) {
            m_len = ByteDecoding.bytesToInt(bytes, ByteDecoding.getDecodingFromType(m_type));
        }

        public void setType(byte type) {
            this.m_type = type;
        }
    }

    

    //Types
    
    //
    public final static byte BIG_ENDIAN = NO_FLAG;
    public final static byte LITTLE_ENDIAN = 0x60;


    public final static byte NO_ENCODING = NO_FLAG;
    public final static byte BASE_10 = 0x20;
    public final static byte BASE_32 = 0x23;
    public final static byte BASE_64 = 0x24;
    public final static byte URL_SAFE = 0x25;
    public final static byte BASE_16 = 0x26;

    public static final byte UTF_8 = NO_FLAG;
    public static final byte ISO_8859_1 = 0x11;
    public static final byte UTF_16 = 0x12;
    public static final byte US_ASCII = 0x13;


    public final static ByteDecoding HEX = hexDecoding();
    public final static ByteDecoding RAW_BYTES = rawBytesNoDecoding();
    public final static ByteDecoding STRING_BASE64_IISO = stringBase64ISODecoding();
    public final static ByteDecoding STRING_UTF8 = stringUTF8Decoding();
    public final static ByteDecoding STRING_UTF16 = stringUTF16Decoding();
    public final static ByteDecoding BOOLEAN = booleanDecoding();
    public final static ByteDecoding INTEGER = integerDecoding();
    public final static ByteDecoding INTEGER_LITTLE_ENDIAN = integerLittleEndianDecoding();
    public final static ByteDecoding DOUBLE = doubleDecoding();
    public final static ByteDecoding DOUBLE_LITTLE_ENDIAN = doubleLittleEndianDecoding();
    public final static ByteDecoding LONG = longDecoding();
    public final static ByteDecoding LONG_LITTLE_ENDIAN = longLittleEndianDecoding();
    public final static ByteDecoding FLOAT = floatDecoding();
    public final static ByteDecoding FLOAT_LITTLE_ENDIAN = floatLittleEndianDecoding();
    public final static ByteDecoding SHORT = shortDecoding();
    public final static ByteDecoding SHORT_LITTLE_ENDIAN = shortLittleEndianDecoding();
    public final static ByteDecoding BIG_INTEGER = bigIntegerDecoding();
    public final static ByteDecoding BIG_DECIMAL = bigDecimalDecoding();
    public final static ByteDecoding NOTE_BYTES_OBJECT = noteBytesObjectDecoding();
    public final static ByteDecoding NOTE_BYTES_ARRAY = noteBytesArrayDecoding();
    public final static ByteDecoding NOTE_BYTES_TREE = noteBytesObjectDecoding();
    public final static ByteDecoding SERIALIZABLE_OBJECT = serializableObjectDecoding();

    public static final int MAX_SHORT_BYTES_SIZE = NoteShort.UNSIGNED_MAX + 2;
    public static final byte[] SHORT_MAX_BYTES = { (byte) 0xFF, (byte) 0xFF };
    public static final int MAX_SHORT_ITEMS = (int) Math.floor(Integer.MAX_VALUE / MAX_SHORT_BYTES_SIZE);


    private byte[] m_bytes;

    public ByteDecoding(byte... bytes){
        m_bytes = bytes;
    }


    public static ByteDecoding getDecodingFromType(byte type) {
        switch(type) {
            case NoteBytesMetaData.STRING_TYPE: return STRING_UTF8;
            case NoteBytesMetaData.STRING_UTF16_TYPE: return STRING_UTF16;
            case NoteBytesMetaData.BOOLEAN_TYPE: return BOOLEAN;
            case NoteBytesMetaData.INTEGER_TYPE: return INTEGER;
            case NoteBytesMetaData.DOUBLE_TYPE: return DOUBLE;
            case NoteBytesMetaData.LONG_TYPE: return LONG;
            case NoteBytesMetaData.FLOAT_TYPE: return FLOAT;
            case NoteBytesMetaData.SHORT_TYPE: return SHORT;
            case NoteBytesMetaData.BIG_INTEGER_TYPE: return BIG_INTEGER;
            case NoteBytesMetaData.BIG_DECIMAL_TYPE: return BIG_DECIMAL;
            case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE: return NOTE_BYTES_ARRAY;
            case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE: return NOTE_BYTES_OBJECT;
            case NoteBytesMetaData.SERIALIZABLE_OBJECT_TYPE: return SERIALIZABLE_OBJECT;
            case NoteBytesMetaData.RAW_BYTES_TYPE:
            default: return RAW_BYTES;
        }
    }

    

    public static byte[] detectContentType(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return new byte[]{ NoteBytesMetaData.RAW_BYTES_TYPE };
        }
        
        // Store types with confidence scores (0-100)
        ArrayList<TypeScore> typeScores = new ArrayList<>();
        
        // Check for NoteBytesObject/Array first
        int objectType = peekNoteBytesObjectOrNoteBytesArray(bytes);
        if (objectType == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            typeScores.add(new TypeScore(NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE, 95)); // High confidence for object
        } else if (objectType == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
            typeScores.add(new TypeScore(NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE, 95)); // High confidence for array
        }
        // Check string type
        try {
            String str = new String(bytes, StandardCharsets.UTF_8);
            if (str.matches("\\A\\p{Print}+\\z")) {
                int confidence = calculateStringConfidence(str);
                typeScores.add(new TypeScore(NoteBytesMetaData.STRING_TYPE, confidence));
            }
        } catch(Exception e) {
            // Not a valid string
        }
        // Check numeric types based on byte length
        if (bytes.length == Integer.BYTES) {
            try {
                ByteBuffer.wrap(bytes).getInt(); // Validate integer
                typeScores.add(new TypeScore(NoteBytesMetaData.INTEGER_TYPE, 90));
            } catch(Exception e) {}
        }
        
        if (bytes.length == Long.BYTES) {
            try {
                ByteBuffer.wrap(bytes).getLong(); // Validate long
                typeScores.add(new TypeScore(NoteBytesMetaData.LONG_TYPE, 90)); 
            } catch(Exception e) {}
        }
        
        if (bytes.length == Double.BYTES) {
            try {
                double val = ByteBuffer.wrap(bytes).getDouble();
                if (!Double.isNaN(val) && !Double.isInfinite(val)) {
                    typeScores.add(new TypeScore(NoteBytesMetaData.DOUBLE_TYPE, 85));
                }
            } catch(Exception e) {}
        }
        
        if (bytes.length == Float.BYTES) {
            try {
                float val = ByteBuffer.wrap(bytes).getFloat();
                if (!Float.isNaN(val) && !Float.isInfinite(val)) {
                    typeScores.add(new TypeScore(NoteBytesMetaData.FLOAT_TYPE, 85));
                }
            } catch(Exception e) {}
        }
        if (bytes.length == Short.BYTES) {
            try {
                ByteBuffer.wrap(bytes).getShort(); // Validate short
                typeScores.add(new TypeScore(NoteBytesMetaData.SHORT_TYPE, 90));
            } catch(Exception e) {}
        }
        
        // Check BigInteger (up to 64 bytes)
        if (bytes.length <= 64) {
            try {
                new BigInteger(bytes);
                int confidence = calculateBigIntegerConfidence(bytes);
                typeScores.add(new TypeScore(NoteBytesMetaData.BIG_INTEGER_TYPE, confidence));
            } catch(Exception e) {}
        }
        
        // Check BigDecimal (scale + BigInteger)
        if (bytes.length > 4 && bytes.length <= 68) {
            try {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                int scale = buffer.getInt();
                byte[] unscaledBytes = new byte[bytes.length - 4];
                buffer.get(unscaledBytes);
                new BigDecimal(new BigInteger(unscaledBytes), scale);
                typeScores.add(new TypeScore(NoteBytesMetaData.BIG_DECIMAL_TYPE, 80));
            } catch(Exception e) {}
        }
        
        // Always add raw bytes as fallback with lowest confidence
        typeScores.add(new TypeScore(NoteBytesMetaData.RAW_BYTES_TYPE, 10));
        
        // Sort by confidence descending
        Collections.sort(typeScores, (a, b) -> b.confidence - a.confidence);
        
        // Convert to byte array of types
        byte[] types = new byte[typeScores.size()];
        for (int i = 0; i < types.length; i++) {
            types[i] = typeScores.get(i).type;
        }
        
        return types;
    }

    private static class TypeScore {
        byte type;
        int confidence;
        
        TypeScore(byte type, int confidence) {
            this.type = type;
            this.confidence = confidence;
        }
    }
    private static int calculateStringConfidence(String str) {
        // Higher confidence for longer strings and strings with varied characters
        int length = str.length();
        long uniqueChars = str.chars().distinct().count();
        return Math.min(95, (int)(70 + (length * 0.1) + (uniqueChars * 0.2)));
    }
    private static int calculateBigIntegerConfidence(byte[] bytes) {
        // Higher confidence for longer numbers that aren't easily represented by other types
        return Math.min(85, 60 + (bytes.length * 2));
    }


    public static int peekNoteBytesObjectOrNoteBytesArray(byte[] bytes) {
        // Quick validation checks
        if (bytes == null || bytes.length < 5) {
            return -1;
        }
        int size = bytesToIntBigEndian(bytes, 1);
        if (size <= 0 || bytes.length < size + 5) {
            return -1;
        }
        // Check if it's a NoteBytesObject by verifying structure:
        // 1. Each key must be STRING_TYPE
        // 2. Must have even number of elements (key-value pairs)
        int offset = 5;
        int elementCount = 0;
        boolean isObject = true;
        // Skip if type is STRING_TYPE (direct array)
        if (bytes[0] != NoteBytesMetaData.STRING_TYPE) {
            while (offset < size + 5 && isObject) {
                // Check if we have enough bytes for next element
                if (offset + 5 > bytes.length) {
                    return -1;
                }
                // Verify key has STRING_TYPE
                if (bytes[offset] != NoteBytesMetaData.STRING_TYPE) {
                    isObject = false;
                    break;
                }
                // Get key length and skip key
                int keyLen = bytesToIntBigEndian(bytes, offset + 1);
                if (keyLen < 0 || offset + 5 + keyLen > bytes.length) {
                    return -1;
                }
                offset += 5 + keyLen;
                // Skip value
                if (offset + 5 > bytes.length) {
                    return -1;
                }
                int valueLen = bytesToIntBigEndian(bytes, offset + 1);
                if (valueLen < 0 || offset + 5 + valueLen > bytes.length) {
                    return -1;
                }
                offset += 5 + valueLen;
                elementCount++;
            }
            // Valid object must have even number of elements (key-value pairs)
            if (isObject && elementCount % 2 == 0) {
                return 1; // NoteBytesObject
            }
        }
        return 2; // NoteBytesArray
    }

  
    public byte[] getByteArray(){
        return m_bytes;
    }


    public static ByteDecoding stringBase64ISODecoding(){
        return new ByteDecoding(NoteBytesMetaData.STRING_TYPE, BIG_ENDIAN, ISO_8859_1, BASE_64);
    }

     public static ByteDecoding noteUUIDDecoding(){
        return new ByteDecoding(NoteBytesMetaData.RAW_BYTES_TYPE, BIG_ENDIAN, ISO_8859_1);
    }


    public static ByteDecoding hexDecoding(){
        return new ByteDecoding(NoteBytesMetaData.STRING_TYPE, BIG_ENDIAN, UTF_8, BASE_16);
    }

    public static ByteDecoding rawBytesNoDecoding(){
        return new ByteDecoding(NoteBytesMetaData.RAW_BYTES_TYPE, BIG_ENDIAN, ISO_8859_1);
    }

    public static ByteDecoding noteBytesObjectDecoding(){
        return new ByteDecoding(NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE, BIG_ENDIAN, UTF_8);
    }

     public static ByteDecoding serializableObjectDecoding(){
        return new ByteDecoding(NoteBytesMetaData.SERIALIZABLE_OBJECT_TYPE, BIG_ENDIAN, UTF_8);
    }



    public static ByteDecoding noteBytesTreeDecoding(){
        return new ByteDecoding(NoteBytesMetaData.NOTE_BYTES_TREE_TYPE, BIG_ENDIAN, UTF_8);
    }

    public static ByteDecoding noteBytesArrayDecoding(){
        return new ByteDecoding(NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE, BIG_ENDIAN, UTF_8);
    }

    public static ByteDecoding stringUTF8Decoding(){
        return new ByteDecoding(NoteBytesMetaData.STRING_TYPE, BIG_ENDIAN, UTF_8);
    }

    public static ByteDecoding stringUTF16Decoding(){
        return new ByteDecoding(NoteBytesMetaData.STRING_TYPE, BIG_ENDIAN, UTF_16);
    }

     public static ByteDecoding booleanDecoding(){
        return new ByteDecoding(NoteBytesMetaData.BOOLEAN_TYPE, BIG_ENDIAN);
    }

    public static ByteDecoding integerDecoding(){
        return new ByteDecoding(NoteBytesMetaData.INTEGER_TYPE, BIG_ENDIAN);
    }

    public static ByteDecoding integerLittleEndianDecoding(){
        return new ByteDecoding(NoteBytesMetaData.INTEGER_TYPE, LITTLE_ENDIAN);
    }

    public static ByteDecoding longDecoding(){
        return new ByteDecoding(NoteBytesMetaData.LONG_TYPE, BIG_ENDIAN);
    }
    public static ByteDecoding longLittleEndianDecoding(){
        return new ByteDecoding(NoteBytesMetaData.LONG_TYPE, LITTLE_ENDIAN);
    }

    public static ByteDecoding doubleDecoding(){
        return new ByteDecoding(NoteBytesMetaData.DOUBLE_TYPE, BIG_ENDIAN);
    }
    public static ByteDecoding doubleLittleEndianDecoding(){
        return new ByteDecoding(NoteBytesMetaData.DOUBLE_TYPE, LITTLE_ENDIAN);
    }
    public static ByteDecoding floatDecoding(){
        return new ByteDecoding(NoteBytesMetaData.FLOAT_TYPE, BIG_ENDIAN);
    }
    public static ByteDecoding floatLittleEndianDecoding(){
        return new ByteDecoding(NoteBytesMetaData.FLOAT_TYPE, LITTLE_ENDIAN);
    }
    public static ByteDecoding shortDecoding(){
        return new ByteDecoding(NoteBytesMetaData.SHORT_TYPE, BIG_ENDIAN);
    }
    public static ByteDecoding shortLittleEndianDecoding(){
        return new ByteDecoding(NoteBytesMetaData.SHORT_TYPE, LITTLE_ENDIAN);
    }

    public static ByteDecoding bigIntegerDecoding(){
        return new ByteDecoding(NoteBytesMetaData.BIG_INTEGER_TYPE, BIG_ENDIAN);
    }
    public static ByteDecoding bigDecimalDecoding(){
        return new ByteDecoding(NoteBytesMetaData.BIG_DECIMAL_TYPE, BIG_ENDIAN);
    }


    public byte getType(){
        return m_bytes != null && m_bytes.length > 0 ? getByteArray()[0] : NO_FLAG;
    }

    public byte getEndiness(){
        return m_bytes != null && m_bytes.length > 1 ?  getByteArray()[1] : NO_FLAG;
    }

    public byte getCharacterEncoding(){
        return m_bytes != null && m_bytes.length > 2 ? getByteArray()[2] : NO_FLAG;
    }

     public byte getBaseEncoding(){
        return m_bytes != null && m_bytes.length > 3 ?  getByteArray()[3] : NO_FLAG;
    }

    public boolean isLittleEndian(){
        return getEndiness() == LITTLE_ENDIAN;
    }

    public boolean isBigEndian(){
        return getEndiness() == BIG_ENDIAN;
    }

    public boolean isNotBigEndian(){
        return getEndiness() != BIG_ENDIAN;
    }

    public boolean isUTF16(){
        return getCharacterEncoding() == UTF_16;
    }
    public boolean isNotUTF8(){
        return getCharacterEncoding() != UTF_8;
    }

    public boolean isUTF8(){
        return getCharacterEncoding() == UTF_8;
    }

    public boolean isNotBase10(){
        return getBaseEncoding() != BASE_10;
    }

    public boolean isBase16(){
        return getBaseEncoding() == BASE_16;
    }

    public boolean isBase64(){
        return getEndiness() != BASE_64;
    }

    //chars

    public static byte[] charsToByteArray(CharBuffer buffer, ByteDecoding byteDecoding){
        ByteBuffer byteBuffer = charsToBytes(buffer, byteDecoding);
        return Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
    }



    public byte[] getRawBytesAsEncoded(byte[] bytes, ByteDecoding byteDecoding){
        return encodeBytes(bytes, byteDecoding.getBaseEncoding());
    }


    public static char[] encodeBytesToChars( byte[] bytes, byte baseEncoding, byte charEncoding){
        CharBuffer charBuffer = encodeBytesToCharBuffer(bytes, baseEncoding, charEncoding);
        char[] chars = Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());
        charBuffer.clear();
        return chars;
    }

    public static CharBuffer encodeBytesToCharBuffer( byte[] bytes, byte baseEncoding, byte charEncoding){
        return encodeBytesToCharBuffer(bytes, baseEncoding, charEncoding, false);
    }

    public static CharBuffer encodeBytesToCharBuffer( byte[] bytes, byte baseEncoding, byte charEncoding, boolean isLittleEndian){
        return bytesToChars(ByteBuffer.wrap(encodeBytes(bytes, baseEncoding)), charEncoding, isLittleEndian);
    }

    public static byte[] decodeString( String str, byte baseEncoding){
      
        switch(baseEncoding){
            case ByteDecoding.BASE_32:
                return Base32.decode(str);
            case ByteDecoding.BASE_16:
                return Hex.decode(str);
            case ByteDecoding.BASE_64:
                return Base64.getDecoder().decode(str);
            case ByteDecoding.URL_SAFE:
                return Base64.getUrlDecoder().decode(str);
            default:
                return numberStringToBytes(str);
        }
    }

    public static boolean isStringNumber(String number){
        String replaced = number.replaceAll("[^0-9.]", "");
        return replaced.length() == number.length();
    }

    public static byte[] numberStringToBytes(String number){
        return isStringNumber(number) ? new BigInteger(number).toByteArray() : null;
    }

    public static char[] copyOfRange(char[] original, int from, int to){
        int newLength = to - from;
        if (newLength < 0) {
            throw new IllegalArgumentException(from + " > " + to);
        } else {
            char[] copy = new char[newLength];
            System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
            return copy;
        }
    }



    public static int findSequenceInChars(char[] sequence, char[] chars, int startPos){
        int charLen = chars.length;
        for (int i = startPos; i < charLen; i++) {
            for (int sequenceLength = 1; sequenceLength <= (charLen - i) / 2; sequenceLength++) {
                boolean sequencesAreEqual = true;
                for (int j = 0; j < sequenceLength; j++) {
                    if (chars[i + j] != chars[i + sequenceLength + j]) {
                        sequencesAreEqual = false;
                        break;
                    }
                }
                if (sequencesAreEqual) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static int findSequenceInChars(char[] sequence, CharBuffer charBuffer){
        int charLen = charBuffer.limit();
        char[] chars = charBuffer.array();
        int sequenceLength = sequence.length;
        int endPos = charLen;
        for (int i = charBuffer.position(); i < charLen; i++) {
            
            if(i + sequenceLength > endPos){
                break;
            }
            boolean sequencesAreEqual = true;
            for (int j = 0; j < sequenceLength; j++) {
                if (chars[i + j] != sequence[j]) {
                    sequencesAreEqual = false;
                    break;
                }
            }
            if (sequencesAreEqual) {
                return i;
            }
            
        }
        return -1;
    }

    
    public static int findSequenceInBytes(byte[] sequence, ByteBuffer byteBuffer){
        int charLen = byteBuffer.limit();
        byte[] chars = byteBuffer.array();
        int sequenceLength = sequence.length;
        int endPos = charLen;
        for (int i = byteBuffer.position(); i < charLen; i++) {
            
            if(i + sequenceLength > endPos){
                break;
            }
            boolean sequencesAreEqual = true;
            for (int j = 0; j < sequenceLength; j++) {
                if (chars[i + j] != sequence[j]) {
                    sequencesAreEqual = false;
                    break;
                }
            }
            if (sequencesAreEqual) {
                return i;
            }
            
        }
        return -1;
    }

    public static int findSequenceInBytes(byte[] sequence, ByteBuffer byteBuffer, int startPos, int length){
        int charLen = byteBuffer.limit();
        byte[] chars = byteBuffer.array();
        startPos = startPos < byteBuffer.position() ? byteBuffer.position() : startPos > charLen ? charLen : startPos;
        length =  Math.min(byteBuffer.limit() - startPos, length);
        int sequenceLength = sequence.length;

        for (int i = startPos; i < length; i++) {

            if((i + sequenceLength) > length){
                return -1;
            }

            boolean sequencesAreEqual = true;

            for (int j = 0; j < sequenceLength; j++) {
                if (chars[i + j] != sequence[j]) {
                    sequencesAreEqual = false;
                    break;
                }
            }
            if (sequencesAreEqual) {
                return i;
            }
            
        }
        return -1;
    }


    public static char[] parseBuffer(CharBuffer buffer){
        if(!buffer.isEmpty()){
            return copyOfRange(buffer.array(),  buffer.position(), buffer.limit());
        }else{
            return new char[0];
        }
    }

    public static byte[] parseBuffer(ByteBuffer buffer){
        return Arrays.copyOfRange(buffer.array(),  buffer.position(), buffer.limit());
    }

    public static byte[] concat(byte[] a, byte[] b) {
        int lenA = a.length;
        int lenB = b.length;
        byte[] c = Arrays.copyOf(a, lenA + lenB);
        System.arraycopy(b, 0, c, lenA, lenB);
        return c;
    }


     public static char[] utf16BytesToChars(byte[] bytes){
        return utf16BytesToChars(bytes, 0,bytes.length);
     }

    public static char[] utf16BytesToChars(byte[] bytes, int offset, int length){
        
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, offset, length);
        CharBuffer charBuffer = StandardCharsets.UTF_16.decode(byteBuffer);
        char[] chars = Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());
        return chars;
    }

    public static byte[] utf16BytesToUtf8Bytes(byte[] bytes){
        return charsToBytes(utf16BytesToChars(bytes));
    }

    public static byte[] charsToISOBytes(char[] chars){
        
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.ISO_8859_1.encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
        byteBuffer.clear();
        charBuffer.clear();
        return bytes;
    }



    public static char[] getBase64Chars(byte[] rawBytes){
        return getCharsFromBytes(java.util.Base64.getEncoder().encode(rawBytes));
    }
    

    public static char[] getCharsFromBytes(byte[] encodedBytes){
        ByteBuffer byteBuffer = ByteBuffer.wrap(encodedBytes);
        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(byteBuffer);
        return Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());
    }



    public static char[] unboxCharacters(Character[] boxedChars){
        char[] chars = new char[boxedChars.length];
        for(int i =0 ; i < boxedChars.length ; i++){
            chars[i] = boxedChars[i];
        }
        return chars;
    }

    public static Character[] boxChars(char[] chars){
        Character[] boxedChars = new Character[chars.length];
        for(int i =0 ; i < chars.length ; i++){
            boxedChars[i] = chars[i];
        }
        return boxedChars;
    }

    public static IntStream characterStream(Character[] characters){
        return Stream.of(characters).flatMapToInt(c -> IntStream.of(c));
    }

    public static IntStream charStream(char[] chars){
        return charStream(chars, 0, chars.length);
    }

    public static IntStream charStream(char[] chars, int offset, int length){
        return IntStream.range(offset, length).map(i -> chars[i]);
    }

    public static String intStreamToString(IntStream stream){
        return stream.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    public static IntStream getBytestoIntStream( ByteBuffer byteBuffer, int length, ByteDecoding byteDecoding){
        byte[] bytes = new byte[length];
        byteBuffer.get(bytes);
        CharBuffer charBuffer = ByteDecoding.bytesToChars(ByteBuffer.wrap(bytes), byteDecoding);
        
        return charBuffer.codePoints();
    }


  
    public static BigDecimal bigDecimalToScaleAndBigInteger(byte[] scaleAndBigIntegerBytes){
        int scale = bytesToIntBigEndian(scaleAndBigIntegerBytes, 0);

        BigInteger unscaled = new BigInteger(scaleAndBigIntegerBytes, Integer.BYTES, scaleAndBigIntegerBytes.length - Integer.BYTES);
        
        return new BigDecimal(unscaled, scale);
    }



    public static long[] bigIntegerBytesToLongsBigEndian(byte[] bytes){
        int byteLen = bytes.length;
        int longLen = (int) Math.ceil(byteLen-1 / 8);
        long[] longs = new long[longLen];
        int j = 0;
        int k = 0;
        for(int i = 1; i < bytes.length ; i++){
            longs[j] =  longs[j] << 8 | (long)( bytes[i] & 255);
            boolean isAdvance = (k + 1 ==8);
            k = isAdvance ? 0 : k + 1;
        }
        return longs;
    }

    public byte[] unsignedBEToLE(byte[] bytes) {
        int byteLen = bytes.length;
        int startIndex = byteLen - 1;
        while( startIndex >= 0 && bytes[startIndex] == 0) {
            startIndex--;
        }
        byte[] leBytes = new byte[byteLen];
        for(int i = 0; i <= startIndex; i++){
            leBytes[i] = bytes[startIndex-i];
        }
        return leBytes;
    }

    public static byte[] bigIntegerToSignedBigEndianBytes(BigInteger bigInteger){
        return bigInteger.toByteArray();
    }

    public static byte[] littleEndianToBigEndian(byte[] littleEndianBytes) {
        return littleEndianToBigEndian(littleEndianBytes, 0, littleEndianBytes.length);
    }

    public static byte[] littleEndianToBigEndian(byte[] littleEndianBytes, int offset, int length) {
        ByteBuffer buffer = ByteBuffer.wrap(littleEndianBytes, offset, length);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer bigEndianBuffer = ByteBuffer.allocate(length);
        bigEndianBuffer.order(ByteOrder.BIG_ENDIAN);
        bigEndianBuffer.put(buffer);
        return Arrays.copyOfRange(bigEndianBuffer.array(), bigEndianBuffer.position(), bigEndianBuffer.limit());
    }

    public static byte[] bigEndianToLittleEndian(byte[] bigEndianBytes) {
        return bigEndianToLittleEndian(bigEndianBytes, 0, bigEndianBytes.length);
    }

    public static byte[] bigEndianToLittleEndian(byte[] bigEndianBytes, int offset, int length) {

        ByteBuffer buffer = ByteBuffer.wrap(bigEndianBytes, offset, length);
        buffer.order(ByteOrder.BIG_ENDIAN);
        ByteBuffer littleEndianBuffer = ByteBuffer.allocate(length);
        littleEndianBuffer.order(ByteOrder.BIG_ENDIAN);
        littleEndianBuffer.put(buffer);
        return Arrays.copyOfRange(littleEndianBuffer.array(), littleEndianBuffer.position(), littleEndianBuffer.limit());
    }







    public static BigInteger bytesToBigIntegerBigEndian(byte[] bytes){
        return new BigInteger(bytes);
    }

    public static BigInteger bytesToBigIntegerBigEndian(byte[] bytes, int offset, int len){
        return new BigInteger(bytes, offset, len);
    }

    public static  ByteBuffer longToByteBuffer(long l, byte endiness){
        return ByteBuffer.allocate(Long.BYTES).order(endiness == LITTLE_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).putLong(l);
    } 

    public static  ByteBuffer intToByteBuffer(int integer, byte endiness){
        return ByteBuffer.allocate(Integer.BYTES).order(endiness == LITTLE_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN).putInt(integer);
    } 

    public static  ByteBuffer intToByteBufferBigEndian(int integer){
        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(integer);
        return byteBuffer;
    }

    public static  ByteBuffer intToByteBufferLittleEndian(int integer){
        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(integer);
        return byteBuffer;
    }



    // ===== FAST BIT-SHIFTING CONVERSION METHODS =====

    // INTEGER CONVERSIONS (Big Endian - fastest path)
    public static byte[] intToBytesBigEndian(int value) {
        return new byte[]{
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }

    public static byte[] intToBytesLittleEndian(int value) {
        return new byte[]{
            (byte) value,
            (byte) (value >>> 8),
            (byte) (value >>> 16),
            (byte) (value >>> 24)
        };
    }

    public static int bytesToIntBigEndian(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
               ((bytes[1] & 0xFF) << 16) |
               ((bytes[2] & 0xFF) << 8) |
               (bytes[3] & 0xFF);
    }

    public static int bytesToIntBigEndian(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
               ((bytes[offset + 1] & 0xFF) << 16) |
               ((bytes[offset + 2] & 0xFF) << 8) |
               (bytes[offset + 3] & 0xFF);
    }

    public static int bytesToIntLittleEndian(byte[] bytes) {
        return (bytes[0] & 0xFF) |
               ((bytes[1] & 0xFF) << 8) |
               ((bytes[2] & 0xFF) << 16) |
               ((bytes[3] & 0xFF) << 24);
    }

    public static int bytesToIntLittleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) |
               ((bytes[offset + 1] & 0xFF) << 8) |
               ((bytes[offset + 2] & 0xFF) << 16) |
               ((bytes[offset + 3] & 0xFF) << 24);
    }

    // LONG CONVERSIONS (Big Endian - fastest path)
    public static byte[] longToBytesBigEndian(long value) {
        return new byte[]{
            (byte) (value >>> 56),
            (byte) (value >>> 48),
            (byte) (value >>> 40),
            (byte) (value >>> 32),
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) value
        };
    }

    public static byte[] longToBytesLittleEndian(long value) {
        return new byte[]{
            (byte) value,
            (byte) (value >>> 8),
            (byte) (value >>> 16),
            (byte) (value >>> 24),
            (byte) (value >>> 32),
            (byte) (value >>> 40),
            (byte) (value >>> 48),
            (byte) (value >>> 56)
        };
    }

    public static long bytesToLongBigEndian(byte[] bytes) {
        return ((long) (bytes[0] & 0xFF) << 56) |
               ((long) (bytes[1] & 0xFF) << 48) |
               ((long) (bytes[2] & 0xFF) << 40) |
               ((long) (bytes[3] & 0xFF) << 32) |
               ((long) (bytes[4] & 0xFF) << 24) |
               ((long) (bytes[5] & 0xFF) << 16) |
               ((long) (bytes[6] & 0xFF) << 8) |
               (long) (bytes[7] & 0xFF);
    }

    public static long bytesToLongBigEndian(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xFF) << 56) |
               ((long) (bytes[offset + 1] & 0xFF) << 48) |
               ((long) (bytes[offset + 2] & 0xFF) << 40) |
               ((long) (bytes[offset + 3] & 0xFF) << 32) |
               ((long) (bytes[offset + 4] & 0xFF) << 24) |
               ((long) (bytes[offset + 5] & 0xFF) << 16) |
               ((long) (bytes[offset + 6] & 0xFF) << 8) |
               (long) (bytes[offset + 7] & 0xFF);
    }

    public static long bytesToLongLittleEndian(byte[] bytes) {
        return (long) (bytes[0] & 0xFF) |
               ((long) (bytes[1] & 0xFF) << 8) |
               ((long) (bytes[2] & 0xFF) << 16) |
               ((long) (bytes[3] & 0xFF) << 24) |
               ((long) (bytes[4] & 0xFF) << 32) |
               ((long) (bytes[5] & 0xFF) << 40) |
               ((long) (bytes[6] & 0xFF) << 48) |
               ((long) (bytes[7] & 0xFF) << 56);
    }

    public static long bytesToLongLittleEndian(byte[] bytes, int offset) {
        return (long) (bytes[offset] & 0xFF) |
               ((long) (bytes[offset + 1] & 0xFF) << 8) |
               ((long) (bytes[offset + 2] & 0xFF) << 16) |
               ((long) (bytes[offset + 3] & 0xFF) << 24) |
               ((long) (bytes[offset + 4] & 0xFF) << 32) |
               ((long) (bytes[offset + 5] & 0xFF) << 40) |
               ((long) (bytes[offset + 6] & 0xFF) << 48) |
               ((long) (bytes[offset + 7] & 0xFF) << 56);
    }

    // SHORT CONVERSIONS (Big Endian - fastest path)
    public static byte[] shortToBytesBigEndian(short value) {
        return new byte[]{
            (byte) (value >>> 8),
            (byte) value
        };
    }

    public static byte[] shortToBytesLittleEndian(short value) {
        return new byte[]{
            (byte) value,
            (byte) (value >>> 8)
        };
    }

    public static short bytesToShortBigEndian(byte[] bytes) {
        return (short) (((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF));
    }

    public static short bytesToShortBigEndian(byte[] bytes, int offset) {
        return (short) (((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF));
    }

    public static short bytesToShortLittleEndian(byte[] bytes) {
        return (short) ((bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8));
    }

    public static short bytesToShortLittleEndian(byte[] bytes, int offset) {
        return (short) ((bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8));
    }

    // DOUBLE CONVERSIONS - Use bit manipulation via Double.doubleToLongBits
    public static byte[] doubleToBytesBigEndian(double value) {
        return longToBytesBigEndian(Double.doubleToLongBits(value));
    }

    public static byte[] doubleToBytesLittleEndian(double value) {
        return longToBytesLittleEndian(Double.doubleToLongBits(value));
    }

    public static double bytesToDoubleBigEndian(byte[] bytes) {
        return Double.longBitsToDouble(bytesToLongBigEndian(bytes));
    }

    public static double bytesToDoubleBigEndian(byte[] bytes, int offset) {
        return Double.longBitsToDouble(bytesToLongBigEndian(bytes, offset));
    }

    public static double bytesToDoubleLittleEndian(byte[] bytes) {
        return Double.longBitsToDouble(bytesToLongLittleEndian(bytes));
    }

    public static double bytesToDoubleLittleEndian(byte[] bytes, int offset) {
        return Double.longBitsToDouble(bytesToLongLittleEndian(bytes, offset));
    }

    // FLOAT CONVERSIONS - Use bit manipulation via Float.floatToIntBits
    public static byte[] floatToBytesBigEndian(float value) {
        return intToBytesBigEndian(Float.floatToIntBits(value));
    }

    public static byte[] floatToBytesLittleEndian(float value) {
        return intToBytesLittleEndian(Float.floatToIntBits(value));
    }

    public static float bytesToFloatBigEndian(byte[] bytes) {
        return Float.intBitsToFloat(bytesToIntBigEndian(bytes));
    }

    public static float bytesToFloatBigEndian(byte[] bytes, int offset) {
        return Float.intBitsToFloat(bytesToIntBigEndian(bytes, offset));
    }

    public static float bytesToFloatLittleEndian(byte[] bytes) {
        return Float.intBitsToFloat(bytesToIntLittleEndian(bytes));
    }

    public static float bytesToFloatLittleEndian(byte[] bytes, int offset) {
        return Float.intBitsToFloat(bytesToIntLittleEndian(bytes, offset));
    }

    // ===== UTILITY METHODS FOR DYNAMIC CONVERSION =====

    public static int bytesToInt(byte[] bytes, ByteDecoding byteDecoding) {
        return byteDecoding.isLittleEndian() ? 
            bytesToIntLittleEndian(bytes) : 
            bytesToIntBigEndian(bytes);
    }

    // Boolean conversions
    public static byte[] booleanToBytes(boolean value) {
        return new byte[]{value ? (byte) 1 : (byte) 0};
    }

    public static boolean bytesToBoolean(byte[] bytes) {
        return bytes.length > 0 && bytes[0] == (byte) 1;
    }

    public static boolean bytesToBoolean(byte[] bytes, int offset) {
        return bytes.length > offset && bytes[offset] == (byte) 1;
    }

    // Character conversion methods (using ByteBuffer for complexity)
    public static byte[] charsToByteArray(char[] chars, ByteDecoding byteDecoding) {
        ByteBuffer byteBuffer = charsToBytes(CharBuffer.wrap(chars), byteDecoding);
        return Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
    }

    public static char[] bytesToCharArray(byte[] bytes, ByteDecoding byteDecoding) {
        CharBuffer charBuffer = bytesToChars(ByteBuffer.wrap(bytes), byteDecoding);
        return Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());
    }

    public static char[] bytesToCharArray(byte[] bytes) {
        CharBuffer charBuffer = bytesToChars(ByteBuffer.wrap(bytes), STRING_UTF8);
        return Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());
    }

    public static ByteBuffer charsToBytes(CharBuffer charBuffer, ByteDecoding byteDecoding) {
        switch (byteDecoding.getCharacterEncoding()) {
            case UTF_16:
                return byteDecoding.isLittleEndian() ? 
                    StandardCharsets.UTF_16LE.encode(charBuffer) : 
                    StandardCharsets.UTF_16.encode(charBuffer);
            case ISO_8859_1:
                return StandardCharsets.ISO_8859_1.encode(charBuffer);
            case US_ASCII:
                return StandardCharsets.US_ASCII.encode(charBuffer);
            case UTF_8:
            default:
                return StandardCharsets.UTF_8.encode(charBuffer);
        }
    }

    public static CharBuffer bytesToChars(ByteBuffer byteBuffer, ByteDecoding byteDecoding) {
        return bytesToChars(byteBuffer, byteDecoding.getCharacterEncoding(), byteDecoding.isLittleEndian());
    }

    public static CharBuffer bytesToChars(ByteBuffer byteBuffer, byte characterEncoding, boolean isLittleEndian) {
        switch (characterEncoding) {
            case UTF_16:
                return isLittleEndian ? 
                    StandardCharsets.UTF_16LE.decode(byteBuffer) : 
                    StandardCharsets.UTF_16.decode(byteBuffer);
            case ISO_8859_1:
                return StandardCharsets.ISO_8859_1.decode(byteBuffer);
            case US_ASCII:
                return StandardCharsets.US_ASCII.decode(byteBuffer);
            case UTF_8:
            default:
                return StandardCharsets.UTF_8.decode(byteBuffer);
        }
    }

    // BigDecimal/BigInteger methods
    public static byte[] bigDecimalToScaleAndBigInteger(BigDecimal bigDecimal) {
        int scale = bigDecimal.scale();
        byte[] scaleBytes = intToBytesBigEndian(scale);
        byte[] bytes = bigDecimal.unscaledValue().toByteArray();
        return Utils.appendBytes(scaleBytes, bytes);
    }

    public static BigDecimal scaleAndBigIntegerBytesToBigDecimal(byte[] bytes) {
        return new BigDecimal(new BigInteger(bytes, 4, bytes.length - 4), bytesToIntBigEndian(bytes, 0));
    }

    public static BigInteger bytesToBigInteger(byte[] bytes) {
        return new BigInteger(bytes);
    }

    // Encoding methods (Base64, Base32, etc.)
    public static byte[] encodeBytes(byte[] bytes, byte baseEncoding) {
        switch (baseEncoding) {
            case BASE_32:
                return Base32.encode(bytes);
            case BASE_16:
                return Hex.encode(bytes);
            case BASE_64:
                return Base64.getEncoder().encode(bytes);
            case URL_SAFE:
                return Base64.getUrlEncoder().encode(bytes);
            default:
            case BASE_10:
                return bytes;
        }
    }

    public static String encodeBytesString(byte[] bytes, byte baseEncoding) {
        switch (baseEncoding) {
            case BASE_32:
                return Base32.toBase32String(bytes);
            case BASE_16:
                return Hex.toHexString(bytes);
            case BASE_64:
                return Base64.getEncoder().encodeToString(bytes);
            case URL_SAFE:
                return Base64.getUrlEncoder().encodeToString(bytes);
            default:
            case BASE_10:
                return new BigInteger(bytes).toString();
        }
    }

    public static byte[] decodeBytes(byte[] bytes, byte baseEncoding) {
        switch (baseEncoding) {
            case BASE_32:
                return Base32.decode(bytes);
            case BASE_16:
                return Hex.decode(bytes);
            case BASE_64:
                return Base64.getDecoder().decode(bytes);
            case URL_SAFE:
                return Base64.getUrlDecoder().decode(bytes);
            default:
                return bytes;
        }
    }

    // Utility methods
    public static byte[] charsToBytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
        byteBuffer.clear();
        return bytes;
    }

    public static char[] isoBytesToChars(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        CharBuffer charBuffer = StandardCharsets.ISO_8859_1.decode(byteBuffer);
        char[] chars = Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());
        byteBuffer.clear();
        charBuffer.clear();
        return chars;
    }

    public static Byte[] boxBytes(byte[] bytes) {
        Byte[] byteObjects = new Byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            byteObjects[i] = bytes[i];
        }
        return byteObjects;
    }

    public static byte[] unboxBytes(Byte[] byteObjects) {
        byte[] bytes = new byte[byteObjects.length];
        for (int i = 0; i < byteObjects.length; i++) {
            bytes[i] = byteObjects[i];
        }
        return bytes;
    }

    @Override
    public int hashCode() {
        return isLittleEndian() ? bytesToIntLittleEndian(m_bytes) : bytesToIntBigEndian(m_bytes);
    }

    public static byte[] serializeObject(Object e) throws IOException{
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(baos)) {
            out.writeObject(e);
            return baos.toByteArray();
        }
    }
}