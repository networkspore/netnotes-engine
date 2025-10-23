package io.netnotes.engine.noteBytes.processing;

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

import java.util.Collections;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteShort;

import io.netnotes.engine.utils.CollectionHelpers;

public class ByteDecoding{

    public static final int MAX_SHORT_BYTES_SIZE = NoteShort.UNSIGNED_MAX + 2;
    public static final byte[] SHORT_MAX_BYTES = { (byte) 0xFF, (byte) 0xFF };
    public static final int MAX_SHORT_ITEMS = (int) Math.floor(Integer.MAX_VALUE / MAX_SHORT_BYTES_SIZE);

    public static final int CODE_POINT_BYTE_SIZE = Integer.BYTES;

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



    public static boolean isLittleEndian(byte type){
        switch(type){
            case NoteBytesMetaData.LONG_LE_TYPE:
            case NoteBytesMetaData.DOUBLE_LE_TYPE:
            case NoteBytesMetaData.INTEGER_LE_TYPE:
            case NoteBytesMetaData.STRING_UTF16_LE_TYPE:
            case NoteBytesMetaData.SHORT_LE_TYPE:
            case NoteBytesMetaData.FLOAT_LE_TYPE:
                return true;
            default:
                return false;
        }
    }

    public static boolean isBigEndian(byte type){
        return !isLittleEndian(type);
    }





    //chars

    public static byte[] stringToBytes(String string, byte type){
        switch(type){
            case NoteBytesMetaData.STRING_UTF16_TYPE:
                return string.getBytes(StandardCharsets.UTF_16);
            case NoteBytesMetaData.STRING_US_ASCII_TYPE:
                return string.getBytes(StandardCharsets.US_ASCII);
            case NoteBytesMetaData.STRING_ISO_8859_1_TYPE:
                return string.getBytes(StandardCharsets.ISO_8859_1);
            case NoteBytesMetaData.STRING_UTF16_LE_TYPE:
                return string.getBytes(StandardCharsets.UTF_16LE);
            case NoteBytesMetaData.STRING_TYPE:
            default:
                return string.getBytes();
        }
    }

    

  

    public static String bytesToString(byte[] bytes, byte type){
        switch(type){
            case NoteBytesMetaData.STRING_UTF16_TYPE:
                return new String(bytes, StandardCharsets.UTF_16);
            case NoteBytesMetaData.STRING_US_ASCII_TYPE:
                return new String(bytes, StandardCharsets.US_ASCII);
            case NoteBytesMetaData.STRING_ISO_8859_1_TYPE:
                return new String(bytes, StandardCharsets.ISO_8859_1);
            case NoteBytesMetaData.STRING_UTF16_LE_TYPE:
                return new String(bytes, StandardCharsets.UTF_16LE);
             case NoteBytesMetaData.STRING_TYPE:
                return new String(bytes);
            case NoteBytesMetaData.BIG_INTEGER_TYPE:
                return bytesToBigInteger(bytes).toString();
            case NoteBytesMetaData.BIG_DECIMAL_TYPE:
                return bytesToBigDecimal(bytes).toString();
            case NoteBytesMetaData.SHORT_TYPE:
                return bytesToShortBigEndian(bytes) + "";
            case NoteBytesMetaData.SHORT_LE_TYPE:
                return bytesToShortLittleEndian(bytes) + "";
            case NoteBytesMetaData.BOOLEAN_TYPE:
                return bytesToBoolean(bytes) ? "true" : "false";
            case NoteBytesMetaData.INTEGER_TYPE:
                return bytesToIntBigEndian(bytes) + "";
            case NoteBytesMetaData.INTEGER_LE_TYPE:
                return bytesToIntLittleEndian(bytes) + "";
            case NoteBytesMetaData.DOUBLE_TYPE:
                return bytesToDoubleBigEndian(bytes) + "";
            case NoteBytesMetaData.DOUBLE_LE_TYPE:
                return bytesToDoubleLittleEndian(bytes) + "";
            case NoteBytesMetaData.FLOAT_TYPE:
                return bytesToFloatBigEndian(bytes) + "";
            case NoteBytesMetaData.FLOAT_LE_TYPE:
                return bytesToFloatLittleEndian(bytes) + "";
            case NoteBytesMetaData.LONG_TYPE:
                return bytesToLongBigEndian(bytes) + "";
            case NoteBytesMetaData.LONG_LE_TYPE:
                return bytesToLongLittleEndian(bytes) + "";
            case NoteBytesMetaData.NOTE_INTEGER_ARRAY_TYPE:
                return codePointBytesToString(bytes);
            default:
                return new String(bytes);
        }

 
    }

    public static CharBuffer bytesToChars(ByteBuffer byteBuffer, byte type) {
        switch (type) {
            case NoteBytesMetaData.STRING_UTF16_TYPE:
                return StandardCharsets.UTF_16.decode(byteBuffer);
            case NoteBytesMetaData.STRING_UTF16_LE_TYPE:
                return StandardCharsets.UTF_16LE.decode(byteBuffer);
            case NoteBytesMetaData.STRING_ISO_8859_1_TYPE:
                return StandardCharsets.ISO_8859_1.decode(byteBuffer);
            case NoteBytesMetaData.STRING_US_ASCII_TYPE:
                return StandardCharsets.US_ASCII.decode(byteBuffer);
            case NoteBytesMetaData.STRING_TYPE:
            default:
                return StandardCharsets.UTF_8.decode(byteBuffer);
            
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

    /**
     * Converts a string to code point bytes
     */
    public static byte[] stringToCodePointBytes(String str) {
        if (str == null || str.isEmpty()) {
            return new byte[0];
        }

        int[] codePoints = str.codePoints().toArray();
        return codePointsToBytes(codePoints);
    }

     /**
     * Converts code points array to bytes
     */
    public static byte[] codePointsToBytes(int[] codePoints) {
        if (codePoints == null || codePoints.length == 0) {
            return new byte[0];
        }

        byte[] bytes = new byte[codePoints.length * CODE_POINT_BYTE_SIZE];
        int offset = 0;

        for (int codePoint : codePoints) {
            byte[] cpBytes = ByteDecoding.intToBytesBigEndian(codePoint);
            System.arraycopy(cpBytes, 0, bytes, offset, CODE_POINT_BYTE_SIZE);
            offset += CODE_POINT_BYTE_SIZE;
        }

        return bytes;
    }

     /**
     * Converts code point bytes to String
     */
    public static String codePointBytesToString(byte[] bytes){
         int[] codePoints = bytesToCodePoints(bytes);
        if (codePoints.length == 0) {
            return "";
        }
        return new String(codePoints, 0, codePoints.length);
    }

     /**
     * Converts bytes to code points array
     */
    public static int[] bytesToCodePoints(byte[] bytes) {

        if (bytes == null || bytes.length == 0) {
            return new int[0];
        }

        int length = bytes.length / CODE_POINT_BYTE_SIZE;
        int[] codePoints = new int[length];
        int offset = 0;

        for (int i = 0; i < length; i++) {
            codePoints[i] = ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += CODE_POINT_BYTE_SIZE;
        }

        return codePoints;
    }

    public static byte[] parseBuffer(ByteBuffer buffer){
        if(buffer.remaining() > 0){
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }else{
            return new byte[0];
        }
    }

    public static char getCharUTF16(byte[] val, int index, boolean isBigEndian) {
        assert index >= 0 && index < lengthDiv2(val) : "Trusted caller missed bounds check";
        index <<= 1; // index *= 2
        return (char)(((val[index++] & 0xff) << getUFT16HiByteShift(isBigEndian)) |
                      ((val[index]   & 0xff) << getUFT16LoByteShift(isBigEndian)));
    }
    public static int getUFT16HiByteShift(boolean isBigEndian){
        return isBigEndian ?  8 : 0;
    }

    public static int getUFT16LoByteShift(boolean isBigEndian){
        return isBigEndian ?  0 : 8;
    }
   
    public static int lengthDiv2(byte[] value) {
        return value.length >> 1; // length / 2
    }


    public static char[] parseBuffer(CharBuffer buffer){
         if(!buffer.isEmpty()){
            char[] chars = new char[buffer.remaining()];
            buffer.get(chars);
            return chars;
        }else{
            return new char[0];
         }
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
       
        return parseBuffer(charBuffer);
    }

    public static byte[] utf16BytesToUtf8Bytes(byte[] bytes){
        return charsToBytes(utf16BytesToChars(bytes));
    }

    public static byte[] charsToISOBytes(char[] chars){
        
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.ISO_8859_1.encode(charBuffer);
        return parseBuffer(byteBuffer);
    }



    public static char[] getBase64Chars(byte[] rawBytes){
        return getCharsFromBytes(java.util.Base64.getEncoder().encode(rawBytes));
    }
    

    public static char[] getCharsFromBytes(byte[] encodedBytes){
        ByteBuffer byteBuffer = ByteBuffer.wrap(encodedBytes);
        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(byteBuffer);
        return parseBuffer(charBuffer);
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

    public static IntStream getBytestoIntStream( ByteBuffer byteBuffer, int length, byte type){
        byte[] bytes = new byte[length];
        byteBuffer.get(bytes);
        CharBuffer charBuffer = ByteDecoding.bytesToChars(ByteBuffer.wrap(bytes), type);
        
        return charBuffer.codePoints();
    }

    public static BigDecimal readAsBigDecimal(NoteBytes value){
        try{
            switch(value.getType()){
                case NoteBytesMetaData.BIG_INTEGER_TYPE:
                    return new BigDecimal(value.getAsBigInteger());
                case NoteBytesMetaData.BIG_DECIMAL_TYPE:
                    return value.getAsBigDecimal();
                case NoteBytesMetaData.INTEGER_TYPE:
                case NoteBytesMetaData.INTEGER_LE_TYPE:
                    return new BigDecimal(value.getAsInt());
                case NoteBytesMetaData.LONG_TYPE:
                case NoteBytesMetaData.LONG_LE_TYPE:
                    return BigDecimal.valueOf(value.getAsLong());
                case NoteBytesMetaData.SHORT_TYPE:
                case NoteBytesMetaData.SHORT_LE_TYPE:
                    return new BigDecimal(value.getAsShort());
                case NoteBytesMetaData.DOUBLE_TYPE:
                case NoteBytesMetaData.DOUBLE_LE_TYPE:
                    return BigDecimal.valueOf(value.getAsDouble());
                case NoteBytesMetaData.FLOAT_TYPE:
                case NoteBytesMetaData.FLOAT_LE_TYPE:
                    return BigDecimal.valueOf(value.getAsFloat());
                case NoteBytesMetaData.STRING_UTF16_TYPE:
                case NoteBytesMetaData.STRING_US_ASCII_TYPE:
                case NoteBytesMetaData.STRING_ISO_8859_1_TYPE:
                case NoteBytesMetaData.STRING_UTF16_LE_TYPE:
                case NoteBytesMetaData.STRING_TYPE:
                    return new BigDecimal(bytesToString(value.getBytes(), value.getType()));
            }
        }catch(Exception e){
            System.err.println(e.toString());
            e.printStackTrace();
        }

        return BigDecimal.ZERO;
    }

    public static BigDecimal forceAsBigDecimal(NoteBytes value){
        try{
            switch(value.getType()){
                case NoteBytesMetaData.BIG_INTEGER_TYPE:
                    return new BigDecimal(value.getAsBigInteger());
                case NoteBytesMetaData.BIG_DECIMAL_TYPE:
                    return value.getAsBigDecimal();
                case NoteBytesMetaData.INTEGER_TYPE:
                case NoteBytesMetaData.INTEGER_LE_TYPE:
                    return new BigDecimal(value.getAsInt());
                case NoteBytesMetaData.LONG_TYPE:
                case NoteBytesMetaData.LONG_LE_TYPE:
                    return BigDecimal.valueOf(value.getAsLong());
                case NoteBytesMetaData.SHORT_TYPE:
                case NoteBytesMetaData.SHORT_LE_TYPE:
                    return new BigDecimal(value.getAsShort());
                case NoteBytesMetaData.DOUBLE_TYPE:
                case NoteBytesMetaData.DOUBLE_LE_TYPE:
                    return BigDecimal.valueOf(value.getAsDouble());
                case NoteBytesMetaData.FLOAT_TYPE:
                case NoteBytesMetaData.FLOAT_LE_TYPE:
                    return BigDecimal.valueOf(value.getAsFloat());
                case NoteBytesMetaData.STRING_UTF16_TYPE:
                case NoteBytesMetaData.STRING_US_ASCII_TYPE:
                case NoteBytesMetaData.STRING_ISO_8859_1_TYPE:
                case NoteBytesMetaData.STRING_UTF16_LE_TYPE:
                case NoteBytesMetaData.STRING_TYPE:
                    return new BigDecimal(bytesToString(value.getBytes(), value.getType()));
                case NoteBytesMetaData.NOTE_INTEGER_ARRAY_TYPE:
                    return new BigDecimal(codePointBytesToString(value.get()));
            }
           
            return new BigDecimal(bytesToString(value.getBytes(), value.getType()));
        }catch(Exception e){
            return new BigDecimal(new BigInteger(value.get()));
        }
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
        return parseBuffer(bigEndianBuffer);
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
        return parseBuffer(littleEndianBuffer);
    }







    public static BigInteger bytesToBigIntegerBigEndian(byte[] bytes){
        return new BigInteger(bytes);
    }

    public static BigInteger bytesToBigIntegerBigEndian(byte[] bytes, int offset, int len){
        return new BigInteger(bytes, offset, len);
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

    public static int bytesToInt(byte[] bytes, byte type) {
        return isLittleEndian(type) ? 
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
    public static byte[] charsToByteArray(char[] chars, byte type) {
        ByteBuffer byteBuffer = charsToBytes(CharBuffer.wrap(chars), type);
        return parseBuffer(byteBuffer);
    }

    public static char[] readValueAsChars(byte[] bytes, byte type) {
        switch(type){
            case NoteBytesMetaData.STRING_UTF16_TYPE:
            case NoteBytesMetaData.STRING_UTF16_LE_TYPE:
            case NoteBytesMetaData.STRING_ISO_8859_1_TYPE:
            case NoteBytesMetaData.STRING_US_ASCII_TYPE:
            case NoteBytesMetaData.STRING_TYPE:
                //Avoid String conversion
                CharBuffer charBuffer = bytesToChars(ByteBuffer.wrap(bytes), type);
                return parseBuffer(charBuffer);
            default:
                //compatibility for non-String types: convert to string then to char
                return bytesToString(bytes, type).toCharArray();
        }
     
    }

    public static char[] bytesToCharArray(byte[] bytes) {
        CharBuffer charBuffer = bytesToChars(ByteBuffer.wrap(bytes), NoteBytesMetaData.STRING_TYPE);
        return parseBuffer(charBuffer);
    }

    public static ByteBuffer charsToBytes(CharBuffer charBuffer, byte type) {
        switch (type) {
            case NoteBytesMetaData.STRING_UTF16_TYPE:
                return StandardCharsets.UTF_16.encode(charBuffer);
            case NoteBytesMetaData.STRING_UTF16_LE_TYPE:
                return StandardCharsets.UTF_16LE.encode(charBuffer);
            case NoteBytesMetaData.STRING_ISO_8859_1_TYPE:
                return StandardCharsets.ISO_8859_1.encode(charBuffer);
            case NoteBytesMetaData.STRING_US_ASCII_TYPE:
                return StandardCharsets.US_ASCII.encode(charBuffer);
            case NoteBytesMetaData.UTF_8_TYPE:
            default:
                return StandardCharsets.UTF_8.encode(charBuffer);
        }
    }


    

    // BigDecimal/BigInteger methods
    public static byte[] bigDecimalToScaleAndBigInteger(BigDecimal bigDecimal) {
        int scale = bigDecimal.scale();
        byte[] scaleBytes = intToBytesBigEndian(scale);
        byte[] bytes = bigDecimal.unscaledValue().toByteArray();
        return CollectionHelpers.appendBytes(scaleBytes, bytes);
    }

    public static BigDecimal bytesToBigDecimal(byte[] bytes) {
        return new BigDecimal(new BigInteger(bytes, 4, bytes.length - 4), bytesToIntBigEndian(bytes, 0));
    }

    public static BigInteger bytesToBigInteger(byte[] bytes) {
        return new BigInteger(bytes);
    }

   

    // Utility methods
    public static byte[] charsToBytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        return parseBuffer(byteBuffer);
    }

    public static char[] isoBytesToChars(byte[] bytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        CharBuffer charBuffer = StandardCharsets.ISO_8859_1.decode(byteBuffer);
        return parseBuffer(charBuffer);
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

    public static byte[] unboxInts(Integer[] integerObjects){
        byte[] bytes = new byte[integerObjects.length];
        int i = 0;
        for(Integer myInt: integerObjects)
            bytes[i++] = (byte) (myInt & 0xff); 
        return bytes;
    }

    
    public static byte[] serializeObject(Object e) throws IOException{
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(baos)) {
            out.writeObject(e);
            return baos.toByteArray();
        }
    }
}