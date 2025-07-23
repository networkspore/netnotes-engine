package io.netnotes.engine.noteBytes;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.bouncycastle.util.encoders.Base32;
import org.bouncycastle.util.encoders.Hex;

import io.netnotes.engine.Utils;

public class ByteDecoding{


    public static final byte NO_FLAG = 0x00;

    public final static byte RAW_BYTES_TYPE = NO_FLAG;



    public final static byte STRING_TYPE = 0x40;
    public final static byte INTEGER_TYPE = 0x41;
    public final static byte DOUBLE_TYPE = 0x42;
    public final static byte LONG_TYPE = 0x43;
    public final static byte FLOAT_TYPE = 0x44;
    public final static byte SHORT_TYPE = 0x45;
    public final static byte BIG_INTEGER_TYPE = 0x46;
    public final static byte BIG_DECIMAL_TYPE = 0x47;
    public final static byte NOTE_BYTE_PAIR_TYPE = 0x48;


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

    public final static ByteDecoding RAW_BYTES = rawBytesNoDecoding();
    public final static ByteDecoding RAW_BYTES_BASE64_IISO = rawBytesBase64ISODecoding();
    public final static ByteDecoding RAW_BYTES_BASE16 = rawBytesBase16ISODecoding();
    public final static ByteDecoding STRING_UTF8 = StringUTF8Decoding();
    public final static ByteDecoding STRING_UTF16 = StringUTF16Decoding();
    public final static ByteDecoding BOOLEAN = booleanDecoding();
    public final static ByteDecoding INTEGER = integerDecoding();
    public final static ByteDecoding DOUBLE = doubleDecoding();
    public final static ByteDecoding LONG = longDecoding();
    public final static ByteDecoding FLOAT = floatDecoding();
    public final static ByteDecoding SHORT = shortDecoding();
    public final static ByteDecoding BIG_INTEGER = bigIntegerDecoding();
    public final static ByteDecoding BIG_DECIMAL = bigDecimalDecoding();
    public final static ByteDecoding NOTE_BYTE_PAIR = noteBytePairDecoding();

    public static final int MAX_SHORT_BYTES_SIZE = NoteShort.UNSIGNED_MAX + 2;
    public static final byte[] SHORT_MAX_BYTES = { (byte) 0xFF, (byte) 0xFF };
    public static final int MAX_SHORT_ITEMS = (int) Math.floor(Integer.MAX_VALUE / MAX_SHORT_BYTES_SIZE);


    private byte[] m_bytes;

    public ByteDecoding(byte... bytes){
        m_bytes = bytes;
    }


  
    public byte[] getByteArray(){
        return m_bytes;
    }

    public static ByteDecoding noteBytePairDecoding(){
        return new ByteDecoding(NOTE_BYTE_PAIR_TYPE, BIG_ENDIAN, UTF_8);
    }

    public static ByteDecoding rawBytesBase64ISODecoding(){
        return new ByteDecoding(RAW_BYTES_TYPE, BIG_ENDIAN, ISO_8859_1, BASE_64);
    }

     public static ByteDecoding noteUUIDDecoding(){
        return new ByteDecoding(RAW_BYTES_TYPE, BIG_ENDIAN, ISO_8859_1, URL_SAFE);
    }

    public static ByteDecoding rawBytesBase16ISODecoding(){
        return new ByteDecoding(RAW_BYTES_TYPE, BIG_ENDIAN, ISO_8859_1, BASE_16);
    }

    public static ByteDecoding rawBytesNoDecoding(){
        return new ByteDecoding(RAW_BYTES_TYPE, BIG_ENDIAN, UTF_8);
    }

    public static ByteDecoding StringUTF8Decoding(){
        return new ByteDecoding(STRING_TYPE, BIG_ENDIAN, UTF_8);
    }

    public static ByteDecoding StringUTF16Decoding(){
        return new ByteDecoding(STRING_TYPE, BIG_ENDIAN, UTF_16);
    }

     public static ByteDecoding booleanDecoding(){
        return new ByteDecoding(RAW_BYTES_TYPE, BIG_ENDIAN);
    }

    public static ByteDecoding integerDecoding(){
        return new ByteDecoding(INTEGER_TYPE, BIG_ENDIAN);
    }
    public static ByteDecoding longDecoding(){
        return new ByteDecoding(LONG_TYPE, BIG_ENDIAN);
    }
    public static ByteDecoding doubleDecoding(){
        return new ByteDecoding(DOUBLE_TYPE, BIG_ENDIAN);
    }
    public static ByteDecoding floatDecoding(){
        return new ByteDecoding(FLOAT_TYPE, BIG_ENDIAN);
    }
    public static ByteDecoding shortDecoding(){
        return new ByteDecoding(SHORT_TYPE, BIG_ENDIAN);
    }
     public static ByteDecoding bigIntegerDecoding(){
        return new ByteDecoding(BIG_INTEGER_TYPE, BIG_ENDIAN);
    }
    public static ByteDecoding bigDecimalDecoding(){
        return new ByteDecoding(BIG_DECIMAL_TYPE, BIG_ENDIAN);
    }


    public int hashCode(){
        return isLittleEndian() ? bytesToIntLittleEndian(m_bytes) : bytesToIntBigEndian(m_bytes);
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

    public static byte[] charsToByteArray(char[] chars, ByteDecoding byteDecoding){
        ByteBuffer byteBuffer = charsToBytes(CharBuffer.wrap(chars), byteDecoding);
        return Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
    }

    public static char[] bytesToCharArray(byte[] bytes, ByteDecoding byteDecoding){
        CharBuffer charBuffer = bytesToChars(ByteBuffer.wrap(bytes), byteDecoding);
        return Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());
    }

    public static char[] bytesToCharArray(byte[] bytes){
        CharBuffer charBuffer = bytesToChars(ByteBuffer.wrap(bytes), ByteDecoding.STRING_UTF8);
        return Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());
    }

    public static ByteBuffer charsToBytes(CharBuffer charBuffer, ByteDecoding byteDecoding){
        ByteBuffer byteBuffer;
        switch(byteDecoding.getCharacterEncoding()){
            case ByteDecoding.UTF_16:
                byteBuffer = byteDecoding.isLittleEndian() ? StandardCharsets.UTF_16LE.encode(charBuffer) : StandardCharsets.UTF_16.encode(charBuffer);
                break;
            case ByteDecoding.ISO_8859_1:
                byteBuffer = StandardCharsets.ISO_8859_1.encode(charBuffer);
                break;
            case ByteDecoding.US_ASCII:
                byteBuffer = StandardCharsets.US_ASCII.encode(charBuffer);
                break;
            case ByteDecoding.UTF_8:
            default:
                byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        }

        return byteBuffer;
    }

    public static CharBuffer bytesToChars(ByteBuffer byteBuffer, ByteDecoding byteDecoding){
        return bytesToChars(byteBuffer, byteDecoding.getCharacterEncoding(), byteDecoding.isLittleEndian());
    }

    public static CharBuffer bytesToChars(ByteBuffer byteBuffer, byte characterEncoding, boolean isLittleEndian){
        CharBuffer charBuffer;
        switch(characterEncoding){
            case ByteDecoding.UTF_16:
                charBuffer = isLittleEndian ? StandardCharsets.UTF_16LE.decode(byteBuffer) : StandardCharsets.UTF_16.decode(byteBuffer);
                break;
            case ByteDecoding.ISO_8859_1:
                charBuffer = StandardCharsets.ISO_8859_1.decode(byteBuffer);
                break;
            case ByteDecoding.US_ASCII:
                charBuffer = StandardCharsets.US_ASCII.decode(byteBuffer);
                break;
            case ByteDecoding.UTF_8:
            default:
                charBuffer = StandardCharsets.UTF_8.decode(byteBuffer);
        }

        return charBuffer;
    }

    public byte[] getRawBytesAsEncoded(byte[] bytes, ByteDecoding byteDecoding){
        return encodeBytes(bytes, byteDecoding.getBaseEncoding());
    }

    public static byte[] encodeBytes( byte[] bytes,byte baseEncoding){
      
        switch(baseEncoding){
            case ByteDecoding.BASE_32:
                return Base32.encode(bytes);
            case ByteDecoding.BASE_16:
                return Hex.decode(bytes);
            case ByteDecoding.BASE_64:
                return Base64.getEncoder().encode(bytes);
            case ByteDecoding.URL_SAFE:
                return Base64.getUrlEncoder().encode(bytes);
            default:
            case ByteDecoding.BASE_10:
                return bytes;
        }
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

    public static String encodeBytesString( byte[] bytes, byte baseEncoding){
      
        switch(baseEncoding){
            case ByteDecoding.BASE_32:
                return Base32.toBase32String(bytes);
            case ByteDecoding.BASE_16:
                return Hex.toHexString(bytes);
            case ByteDecoding.BASE_64:
                return Base64.getEncoder().encodeToString(bytes);
            case ByteDecoding.URL_SAFE:
                return Base64.getUrlEncoder().encodeToString(bytes);
            default:
            case ByteDecoding.BASE_10:
                return new BigInteger(bytes).toString();
        }
    }
    
     public static byte[] decodeBytes( byte[] bytes, byte baseEncoding){
      
        switch(baseEncoding){
            case ByteDecoding.BASE_32:
                return Base32.decode(bytes);
            case ByteDecoding.BASE_16:
                return Hex.decode(bytes);
            case ByteDecoding.BASE_64:
                return Base64.getDecoder().decode(bytes);
            case ByteDecoding.URL_SAFE:
                return Base64.getUrlDecoder().decode(bytes);
            default:
                return bytes;
        }
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

     public static char[] isoBytesToChars(byte[] bytes){
        
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        CharBuffer charBuffer = StandardCharsets.ISO_8859_1.decode(byteBuffer);
        char[] chars = Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());
        byteBuffer.clear();
        charBuffer.clear();
        return chars;
    }


    public static char[] getBase64Chars(byte[] rawBytes){
        return getCharsFromBytes(java.util.Base64.getEncoder().encode(rawBytes));
    }
    

    public static char[] getCharsFromBytes(byte[] encodedBytes){
        ByteBuffer byteBuffer = ByteBuffer.wrap(encodedBytes);
        CharBuffer charBuffer = StandardCharsets.UTF_8.decode(byteBuffer);
        return Arrays.copyOfRange(charBuffer.array(), charBuffer.position(), charBuffer.limit());
    }


    public static byte[] charsToBytes(char[] chars){
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
        byteBuffer.clear();
        return bytes;
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



    public static Byte[] boxBytes(byte[] bytes){
        Byte[] byteObjects = new Byte[bytes.length];
        int i = 0;
        for(byte b: bytes)
            byteObjects[i++] = b; 
        return byteObjects;
    }

    public static byte[] unboxBytes(Byte[] byteObjects){
        byte[] bytes = new byte[byteObjects.length];
        int i = 0;
        for(Byte b: byteObjects)
            bytes[i++] = b; 
        return bytes;
    }

    public static byte[] booleanToBytes(boolean value){
        return new byte[] { value ? (byte) 1 : (byte) 0};
    }

    public static boolean bytesToBoolean(byte[] bytes, int offset){
        return bytes.length > offset && bytes[offset] == (byte) 1;
    }

    public static boolean bytesToBoolean(byte[] bytes){
        return bytes.length > 0 && bytes[0] == (byte) 1;
    }
    public static byte[] bigDecimalToScaleAndBigInteger(BigDecimal bigDecimal){
        int scale = bigDecimal.scale();
        byte[] scaleBytes = intToBytesBigEndian(scale);
        byte[] bytes = bigIntegerToSignedBigEndianBytes(bigDecimal.unscaledValue());

        return Utils.appendBytes(scaleBytes, bytes);
    }

    public static BigDecimal scaleAndBigIntegerBytesToBigDecimal(byte[] bytes){
        return new BigDecimal(new BigInteger(bytes, 4, bytes.length-4), bytesToIntBigEndian(bytes, 0));
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





    public static BigInteger bytesToBigInteger(byte[] bytes){
       
        return bytesToBigIntegerBigEndian(bytes);
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

     public static  byte[] intToBytesLittleEndian(int myInteger){
        byte[] bytes = new byte[4];
        bytes[0] = (byte)(myInteger >>> 0);
        bytes[1] = (byte)(myInteger >>> 8);
        bytes[2] = (byte)(myInteger >>> 16);
        bytes[3] = (byte)(myInteger >>> 24);
        return bytes;
    }

    public static  byte[] intToBytesBigEndian(int myInteger){
        byte[] bytes = new byte[4];
        bytes[0] = (byte)(myInteger >>> 24);
        bytes[1] = (byte)(myInteger >>> 16);
        bytes[2] = (byte)(myInteger >>> 8);
        bytes[3] = (byte)(myInteger >>> 0);
        return bytes;
    }

    public static int bytesToIntLittleEndian(byte [] byteBarray){
        return ByteBuffer.wrap(byteBarray).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public static int bytesToIntLittleEndian(byte [] byteBarray, int offset){
        return ByteBuffer.wrap(byteBarray, offset, Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public static int bytesToInt(byte [] bytes, ByteDecoding byteDecoding){
        return byteDecoding.isLittleEndian() ? bytesToIntLittleEndian(bytes) : bytesToShortBigEndian(bytes);
    }

    public static int bytesToIntBigEndian(byte [] byteBarray){
        return ByteBuffer.wrap(byteBarray).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    public static int bytesToIntBigEndian(byte [] byteBarray, int offset){
        return ByteBuffer.wrap(byteBarray, offset, Integer.BYTES).order(ByteOrder.BIG_ENDIAN).getInt();
    }
    //long

    public static  byte[] longToBytesLittleEndian(int myInteger){
        byte[] bytes = new byte[4];

        bytes[0] = (byte)(myInteger >>> 0);
        bytes[1] = (byte)(myInteger >>> 8);
        bytes[2] = (byte)(myInteger >>> 16);
        bytes[3] = (byte)(myInteger >>> 24);
        bytes[4] = (byte)(myInteger >>> 32);
        bytes[5] = (byte)(myInteger >>> 40);
        bytes[6] = (byte)(myInteger >>> 48);
        bytes[7] = (byte)(myInteger >>> 56);
        return bytes;
    }

 

    public static  byte[] longToBytesLittleEndian(long l){
        ByteBuffer byteBuffer = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(l);
        
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
        byteBuffer.clear();
        return bytes;
    }

    public static long bytesToLongLittleEndian(byte [] byteBarray){
        return ByteBuffer.wrap(byteBarray).order(ByteOrder.LITTLE_ENDIAN).getInt();

    }

    public static long bytesToLongLittleEndian(byte [] byteBarray, int offset){
        return ByteBuffer.wrap(byteBarray, offset, Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).getInt();

    }

    public static  byte[] longToBytesBigEndian(long myInteger){
        byte[] bytes = new byte[4];
        bytes[0] = (byte)(myInteger >>> 56);
        bytes[1] = (byte)(myInteger >>> 48);
        bytes[2] = (byte)(myInteger >>> 40);
        bytes[3] = (byte)(myInteger >>> 32);
        bytes[4] = (byte)(myInteger >>> 32);
        bytes[5] = (byte)(myInteger >>> 16);
        bytes[6] = (byte)(myInteger >>> 8);
        bytes[7] = (byte)(myInteger >>> 0);
        return bytes;
    }

    public static long bytesToLongBigEndian(byte[] byteBarray, int offset){
        return ByteBuffer.wrap(byteBarray, offset, Long.BYTES).order(ByteOrder.BIG_ENDIAN).getLong();
    }

    public static long bytesToLongBigEndian(byte[] byteBarray){
        return ByteBuffer.wrap(byteBarray).order(ByteOrder.BIG_ENDIAN).getLong();
    }

    //double
    public static  byte[] bigDoubleToBytesLittleEndian(double _double){
        ByteBuffer byteBuffer = ByteBuffer.allocate(Double.BYTES).order(ByteOrder.LITTLE_ENDIAN).putDouble(_double);
         byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
         byteBuffer.clear();
         return bytes;
    }

    public static double bytesToDoubleLittleEndian(byte [] byteBarray){
        return ByteBuffer.wrap(byteBarray).order(ByteOrder.LITTLE_ENDIAN).getDouble();

    }

    public static double bytesToDoubleLittleEndian(byte [] byteBarray, int offset){
        return ByteBuffer.wrap(byteBarray, offset, Double.BYTES).order(ByteOrder.LITTLE_ENDIAN).getDouble();

    }

    public static  byte[] doubleToBytesBigEndian(double _double){
        ByteBuffer byteBuffer = ByteBuffer.allocate(Double.BYTES).order(ByteOrder.BIG_ENDIAN).putDouble(_double);
        return Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
    }

    public static double bytesToDoubleBigEndian(byte[] byteBarray){
        return ByteBuffer.wrap(byteBarray).order(ByteOrder.BIG_ENDIAN).getDouble();
    }

    public static double bytesToDoubleBigEndian(byte[] byteBarray, int offset){
        return ByteBuffer.wrap(byteBarray, offset, Double.BYTES).order(ByteOrder.BIG_ENDIAN).getDouble();
    }

    //float
    public static  byte[] bigFloatToBytesLittleEndian(float _float){
        ByteBuffer byteBuffer = ByteBuffer.allocate(Float.BYTES).order(ByteOrder.LITTLE_ENDIAN).putFloat(_float);
         byte[] bytes =  Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
         byteBuffer.clear();
         return bytes;
    }

    public static float bytesToFloatLittleEndian(byte [] byteBarray){
        return ByteBuffer.wrap(byteBarray).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    public static float bytesToFloatLittleEndian(byte [] byteBarray, int offset){
        return ByteBuffer.wrap(byteBarray, offset, Float.BYTES).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    public static  byte[] floatToBytesBigEndian(float _float){
        ByteBuffer byteBuffer = ByteBuffer.allocate(Float.BYTES).order(ByteOrder.BIG_ENDIAN).putFloat(_float);
        return Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
    }

    public static float bytesToFloatBigEndian(byte[] byteBarray){
        return ByteBuffer.wrap(byteBarray).order(ByteOrder.BIG_ENDIAN).getFloat();
    }

    public static float bytesToFloatBigEndian(byte[] byteBarray, int offset){
        return ByteBuffer.wrap(byteBarray, offset, Float.BYTES).order(ByteOrder.BIG_ENDIAN).getFloat();
    }

    //short
    public static  byte[] shortToBytesLittleEndian(short _short){
        ByteBuffer byteBuffer = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort(_short);
         byte[] bytes =  Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
         byteBuffer.clear();
         return bytes;
    }

    public static short bytesToShortLittleEndian(byte[] byteBarray){
        return ByteBuffer.wrap(byteBarray).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    public static short bytesToShortLittleEndian(byte[] byteBarray, int offset){
        return ByteBuffer.wrap(byteBarray, offset, Short.BYTES).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    public static  byte[] shortToBytesBigEndian(short _short){
        ByteBuffer byteBuffer = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.BIG_ENDIAN).putShort(_short);

        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit());
        byteBuffer.clear();
        return bytes;
    }

    public static short bytesToShortBigEndian(byte[] byteBarray){
        return ByteBuffer.wrap(byteBarray).order(ByteOrder.BIG_ENDIAN).getShort();
    }

    public static short bytesToShortBigEndian(byte[] byteBarray, int offset){
        return ByteBuffer.wrap(byteBarray, offset, Short.BYTES).order(ByteOrder.BIG_ENDIAN).getShort();
    }


}