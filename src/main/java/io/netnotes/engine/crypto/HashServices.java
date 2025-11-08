package io.netnotes.engine.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.EncodingHelpers;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.ove.crypto.digest.Blake2b;

public class HashServices {

     public static byte[] digestFile(File file) throws  IOException {

        return digestFileBlake2b(file,32);
        
    }
    

    public static byte[] digestFileBlake2b(File file, int digestLength) throws IOException {
        final Blake2b digest = Blake2b.Digest.newInstance(digestLength);
        try(
            FileInputStream fis = new FileInputStream(file);
        ){
            int bufferSize = file.length() < StreamUtils.BUFFER_SIZE ? (int) file.length() : StreamUtils.BUFFER_SIZE;

            byte[] byteArray = new byte[bufferSize];
            int bytesCount = 0;

            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            };

            byte[] hashBytes = digest.digest();

            return hashBytes;
        }
    }

    public static boolean verifyBCryptPassword(NoteBytesEphemeral ephemeralPassword, NoteBytes hash) {
        try(NoteBytesEphemeral password = ephemeralPassword.copy()){
            BCrypt.Result result = BCrypt.verifyer(BCrypt.Version.VERSION_2A, LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)).verify(password.get(), hash.getBytes());
            return result.verified;
        }
    }

   
    public static NoteBytesReadOnly getBcryptHash(NoteBytesEphemeral ephemeralPassword) {
        try(NoteBytesEphemeral password = ephemeralPassword.copy()){
            return new NoteBytesReadOnly( BCrypt.with(BCrypt.Version.VERSION_2A, RandomService.getSecureRandom(), LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)).hash(15, password.get()));
        }
    }


    public static CompletableFuture<byte[]> copyFileAndReturnHash(File inputFile, File outputFile, ExecutorService execService) {

        return CompletableFuture.supplyAsync(()->{
            long contentLength = -1;

            if (inputFile != null && inputFile.isFile() && outputFile != null && !inputFile.getAbsolutePath().equals(outputFile.getAbsolutePath())) {
                try{
                    contentLength = Files.size(inputFile.toPath());
                }catch(IOException e){
                    throw new CompletionException("Cannot read input file",e);
                }
            } else {
                throw new CompletionException("Cannot process input file", inputFile == null || outputFile == null ? 
                    new NullPointerException(inputFile == null ? "inputFile null" : "outputFile null") : new IllegalStateException("inputFile and outputFile are the same"));
            }
            final Blake2b digest = Blake2b.Digest.newInstance(32);

            try(
                FileInputStream inputStream = new FileInputStream(inputFile);
                FileOutputStream outputStream = new FileOutputStream(outputFile);
            ){
                byte[] buffer = new byte[contentLength < (long) StreamUtils.BUFFER_SIZE ? (int) contentLength : StreamUtils.BUFFER_SIZE];

                int length;
                //long copied = 0;

                while ((length = inputStream.read(buffer)) != -1) {

                    outputStream.write(buffer, 0, length);
                    digest.update(buffer, 0, length);

                  //  copied += (long) length;
                }



            } catch (FileNotFoundException e) {
                throw new CompletionException(e);
            } catch (IOException e) {
                throw new CompletionException(e);
            }

            return digest.digest();

        }, execService);
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


    public static String digestToUrlSafeString(byte[] bytes, int length){
        return EncodingHelpers.encodeUrlSafeString(digestBytesToBytes(bytes, length));
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
            ints[i] = (int) ByteDecoding.getCharUTF16(value, i, !islittleEndian);
            
        }
        return ints;
    }

    public static long digestBytesToLong(byte[] bytes) {
        final Blake2b digest = Blake2b.Digest.newInstance(8);

        digest.update(bytes);

        return ByteDecoding.bytesToLongBigEndian(digest.digest());
    }


    public static byte[] digestBytesToBytes(byte[] bytes, int digestLength) {
        final Blake2b digest = Blake2b.Digest.newInstance(digestLength);

        digest.update(bytes);

        return digest.digest();
    }

     public static byte[] digestFileToBytes(File file, int digestLength) throws IOException{
        final Blake2b digest = Blake2b.Digest.newInstance(digestLength);

        try(InputStream inputStream = Files.newInputStream(file.toPath())){
            byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
            int length = 0;

            while((length = inputStream.read(buffer)) != -1){
                digest.update(buffer, 0, length);
            }

            return digest.digest();
        }
    }
   
    public int hashLongToInt(long l){
        return (int)(l ^ l >>> 32);
    }
  
    public static int getHashCode(byte[] bytes, byte type){
        if(bytes.length == 0){
            return Integer.MIN_VALUE;
        }
        
        int code = -1;
    
        switch(type){
            case NoteBytesMetaData.BYTE_TYPE:
                code = bytes[0];
            case NoteBytesMetaData.SHORT_TYPE:
                code =  ByteDecoding.bytesToShortBigEndian(bytes) << 32;
                break;
            case NoteBytesMetaData.SHORT_LE_TYPE:
                code =  ByteDecoding.bytesToShortLittleEndian(bytes) << 32;
                break;
            case NoteBytesMetaData.INTEGER_TYPE:
                code = ByteDecoding.bytesToIntBigEndian(bytes);
                break;
            case NoteBytesMetaData.INTEGER_LE_TYPE:
                code =  ByteDecoding.bytesToIntLittleEndian(bytes);
                break;
            case NoteBytesMetaData.LONG_TYPE:
                code = Long.hashCode(ByteDecoding.bytesToLongBigEndian(bytes));
                break;
            case NoteBytesMetaData.LONG_LE_TYPE:
                code = Long.hashCode(ByteDecoding.bytesToLongLittleEndian(bytes));
                break;
            case NoteBytesMetaData.DOUBLE_TYPE:
                code = Double.hashCode(ByteDecoding.bytesToDoubleBigEndian(bytes));
                break;
             case NoteBytesMetaData.DOUBLE_LE_TYPE:
                code = Double.hashCode( ByteDecoding.bytesToDoubleLittleEndian(bytes));
                break;
            case NoteBytesMetaData.FLOAT_TYPE:
                code = Float.hashCode(ByteDecoding.bytesToFloatBigEndian(bytes));
                break;
             case NoteBytesMetaData.FLOAT_LE_TYPE:
                code = Float.hashCode(ByteDecoding.bytesToFloatLittleEndian(bytes));
                break;
            case NoteBytesMetaData.BIG_INTEGER_TYPE:
                code =  bytes.length < 1 ? -1 : new BigInteger(bytes).hashCode();
                break;
            case NoteBytesMetaData.BIG_DECIMAL_TYPE:
                code = bytes.length < 5 ? -1 : new BigDecimal(new BigInteger(bytes, 4, bytes.length-4), ByteDecoding.bytesToIntBigEndian(bytes, 0)).hashCode();
                break;
            case NoteBytesMetaData.STRING_TYPE:
                code = new String(bytes).hashCode();
                break;
            case NoteBytesMetaData.STRING_ISO_8859_1_TYPE:
                code = new String(bytes, StandardCharsets.ISO_8859_1).hashCode();
                break;
            case NoteBytesMetaData.STRING_US_ASCII_TYPE:
                code = new String(bytes, StandardCharsets.US_ASCII).hashCode();
                break;
            case NoteBytesMetaData.STRING_UTF16_TYPE:
                code = new String(bytes, StandardCharsets.UTF_16).hashCode();
                break;
            case NoteBytesMetaData.STRING_UTF16_LE_TYPE:
                code = new String(bytes, StandardCharsets.UTF_16LE).hashCode();
                break;
            default:
                code = Arrays.hashCode(bytes);
              
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
            char c =  ByteDecoding.getCharUTF16(value, i, !islittleEndian);
            h = 31 * h + c;
        }
        return h;
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
