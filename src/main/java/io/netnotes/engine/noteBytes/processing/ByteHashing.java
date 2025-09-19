package io.netnotes.engine.noteBytes.processing;

import java.math.BigDecimal;
import java.math.BigInteger;

import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
import io.netnotes.ove.crypto.digest.Blake2b;
public class ByteHashing  {
    


    public static int getUFT16HiByteShift(boolean isBigEndian){
        return isBigEndian ?  8 : 0;
    }

    public static int getUFT16LoByteShift(boolean isBigEndian){
        return isBigEndian ?  0 : 8;
    }
   



    public int integersToSignedHashCode(int[] integers, int signum) {
      int hashCode = 0;

      for(int i = 0; i < integers.length; ++i) {
         hashCode = (int)((long)(31 * hashCode) + ((long)integers[i] & 4294967295L));
      }

      return hashCode * signum;
   }

   public int integersToHashCode(int[] integers) {
      int hashCode = 0;

      for(int i = 0; i < integers.length; ++i) {
         hashCode = (int)((long)(31 * hashCode) + ((long)integers[i] & 4294967295L));
      }

      return hashCode;
   }


    public static long[] makeLongArrayEven(long[] longs){
        int longsLen = longs.length;
        boolean isLongsMod2Zero  = longsLen % 2 == 0;
        long[] l = isLongsMod2Zero ? longs : new long[longs.length + 1];
        
        int startPoint = l.length == longs.length ? longs.length : 0; 

        for(int i = startPoint; i < longsLen ; i++){
            l[i] = longs[i];
        }
        return l;
    }

    public static int[] longsToIntegersHash(long[] longs){
        longs = makeLongArrayEven(longs);

        int[] ints = new int[longs.length / 2];
        int j = 0;
        for(int i = 0 ; i < ints.length; i++){
            long long1 = longs[j];
            j++;
            long long2 = longs[j];
            j++;
            long hilo = long1 ^ long2;
        
            ints[i] = (int)(hilo >> 32) ^ (int)hilo;
        }
        
        return ints;
    }

    public static long[] bytesToLongs(byte[] bytes){
        int byteLen = bytes.length;
        int longLen = (int) Math.ceil(byteLen / 8);
        long[] longs = new long[longLen];
        int j = 0;
        int k = 0;
        for(int i = 0; i < bytes.length ; i++){
            longs[j] =  longs[j] << 8 | (long)( bytes[i] & 255);
            boolean isAdvance = (k + 1 ==8);
            k = isAdvance ? 0 : k + 1;
        }
        return longs;
    }



    public static byte[] digestBytesToBytes(byte[] bytes){
        return digestBytesToBytes(bytes, 32);
    }

    public static byte[] digestBytesToBytes(byte[] input, int digestLength, int offset, int len) {
        final Blake2b digest = Blake2b.Digest.newInstance(digestLength);

        byte[] bytes = new byte[len];
        System.arraycopy(input, offset, bytes, 0, len);

        digest.update(bytes);

        return digest.digest();
    }


    
    public static int[] UTF16BytesToInts(byte[] value, boolean islittleEndian) {
        int length = value.length >> 1;
        int[] ints = new int[length];

        for (int i = 0; i < length; i++) {
            ints[i] = (int) getCharUTF16(value, i, !islittleEndian);
            
        }
        return ints;
    }


    public static byte[] digestBytesToBytes(byte[] bytes, int digestLength) {
        final Blake2b digest = Blake2b.Digest.newInstance(digestLength);

        digest.update(bytes);

        return digest.digest();
    }
   
    public int hashLongToInt(long l){
        return (int)(l ^ l >>> 32);
    }
  
    public static int getHashCode(byte[] bytes, ByteDecoding byteDecoding){

        boolean isLittleEndian = byteDecoding.isLittleEndian();
        int code = -1;
    
        switch(byteDecoding.getType()){
            case NoteBytesMetaData.SHORT_TYPE:
                code =  (isLittleEndian ? ByteDecoding.bytesToShortLittleEndian(bytes) : ByteDecoding.bytesToShortBigEndian(bytes)) << 32;
                break;
            case NoteBytesMetaData.INTEGER_TYPE:
                code = isLittleEndian ?ByteDecoding.bytesToIntLittleEndian(bytes) : ByteDecoding.bytesToIntBigEndian(bytes);
                break;
            case NoteBytesMetaData.LONG_TYPE:
                code = Long.hashCode(isLittleEndian ?ByteDecoding.bytesToLongLittleEndian(bytes) : ByteDecoding.bytesToLongBigEndian(bytes));
                break;
            case NoteBytesMetaData.DOUBLE_TYPE:
                double d = (isLittleEndian ?ByteDecoding.bytesToDoubleBigEndian(bytes) : ByteDecoding.bytesToDoubleLittleEndian(bytes));
                code = Double.hashCode(d);
                break;
            case NoteBytesMetaData.FLOAT_TYPE:
                float f = (isLittleEndian ?ByteDecoding.bytesToFloatLittleEndian(bytes) : ByteDecoding.bytesToFloatBigEndian(bytes));
                code = Float.hashCode(f);
                break;
            case NoteBytesMetaData.BIG_INTEGER_TYPE:
                code =  bytes.length < 1 ? -1 : new BigInteger(bytes).hashCode();
                break;
            case NoteBytesMetaData.BIG_DECIMAL_TYPE:
                code = bytes.length < 5 ? -1 : new BigDecimal(new BigInteger(bytes, 4, bytes.length-4), isLittleEndian ? ByteDecoding.bytesToIntLittleEndian(bytes,0) :  ByteDecoding.bytesToIntBigEndian(bytes, 0)).hashCode();
                break;
            case NoteBytesMetaData.STRING_TYPE:
                switch(byteDecoding.getCharacterEncoding()){
                    case ByteDecoding.UTF_16:
                        code = hashCodeUTF16(bytes, byteDecoding.isLittleEndian());
                    break;
                    case ByteDecoding.ISO_8859_1:
                    case ByteDecoding.US_ASCII:
                    case ByteDecoding.UTF_8:
                    default:
                        code = hashCodeUTF8(bytes);

                }
                break;
            case NoteBytesMetaData.RAW_BYTES_TYPE:
            default:
                code = ByteDecoding.bytesToIntBigEndian(digestBytesToBytes(bytes,4));
              
        }
        

        return code;
    }



    public static int hashCodeUTF8(byte[] value) {
        int h = 0;
        for (byte v : value) {
            h = 31 * h + (v & 0xff);
        }
        return h;
    }


    
    public static int hashCodeUTF16(byte[] value, boolean islittleEndian) {
        int h = 0;
        int length = value.length >> 1;
        for (int i = 0; i < length; i++) {
            char c =  getCharUTF16(value, i, !islittleEndian);
            h = 31 * h + c;
        }
        return h;
    }

    public static char getCharUTF16(byte[] val, int index, boolean isBigEndian) {
        assert index >= 0 && index < length(val) : "Trusted caller missed bounds check";
        index <<= 1; // index *= 2
        return (char)(((val[index++] & 0xff) << getUFT16HiByteShift(isBigEndian)) |
                      ((val[index]   & 0xff) << getUFT16LoByteShift(isBigEndian)));
    }


    public static int length(byte[] value) {
        return value.length >> 1; // length / 2
    }


    public static long[] hashBytes16ToMsbLsb(byte[] data){

        long msb = 0L;
        long lsb = 0L;
        int i;
        for(i = 0; i < 8; ++i) {
            msb = msb << 8 | (long)(data[i] & 255);
        }

        for(i = 8; i < 16; ++i) {
            lsb = lsb << 8 | (long)(data[i] & 255);
        }

        return new long[] {msb, lsb};

    }

    public static long bytesToLong(byte[] bytes){
        long l = 0;
        for(int i = 0; i < 8; ++i) {
            l = l << 8 | (long)(bytes[i] & 255);
        }

        return l;
    }

    public static long[] hashBytes32ToMsbLsb(byte[] bytes){

        int i;
        long msb = 0L;
        for(i = 0; i < 8; ++i) {
            msb = msb << 8 | (long)(bytes[i] & 255);
        }

        long msb1 = 0L;
        for(i = 16; i < 24; ++i) {
            msb1 = msb1 << 8 | (long)(bytes[i] & 255);
        }

        long lsb = 0L;
        for(i = 8; i < 16; ++i) {
            lsb = lsb << 8 | (long)(bytes[i] & 255);
        }

        long lsb1 = 0L;
        for(i = 24; i < 32; ++i) {
            lsb1 = lsb1 << 8 | (long)(bytes[i] & 255);
        }


        return new long[]{msb,msb1, lsb, lsb1};
        
    }

}
