package io.netnotes.engine.noteBytes;

import java.util.Arrays;
import java.util.Calendar;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.processing.EncodingHelpers;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.EncodingHelpers.Encoding;
import io.netnotes.engine.utils.HardwareInfo;

public class NoteUUID extends NoteBytesReadOnly {

    private static final AtomicInteger m_atomicByte = new AtomicInteger(ByteDecoding.bytesToIntBigEndian(RandomService.getRandomBytes(4)));

    private NoteUUID(byte[] bytes){
        super(bytes, NoteBytesMetaData.STRING_ISO_8859_1_TYPE);
    }



      
    public static byte[] littleEndianNanoTimeHash(){
        return ByteDecoding.intToBytesLittleEndian(splitMix64(System.nanoTime()));
    }

    public static byte[] littleEndianCurrentTime(){
        return ByteDecoding.longToBytesLittleEndian(System.currentTimeMillis());
    }

    public static int getAndIncrementByte(){
        return m_atomicByte.updateAndGet((i)->i < 255 ? i + 1 : 0);

    }

    public static NoteUUID createLocalUUID128(){
        return fromUnencodedBytes(createTimeRndBytes());
    }

    public static String createSafeUUID128(){
        return createLocalUUID128().getAsString();
    }

    public static NoteUUID createLocalUUID64(){
        return fromUnencodedBytes(createTimeSequenceBytes64());
    }

    public static String createSafeUUID64(){
        return createLocalUUID64().getAsString();
    }
    

  
    public static byte[] intTimeStampBytes(){
        int timestamp = getIntTimeStamp();
        return ByteDecoding.intToBytesBigEndian(timestamp);
    }

    public static byte[] createTimeRndBytes(){
		byte[] nanoTime = littleEndianNanoTimeHash();
		byte[] randomBytes = RandomService.getRandomBytes(7);
		byte[] intTime = intTimeStampBytes();
        byte byteIncrement = (byte) getAndIncrementByte();
        byte[] bytes = new byte[] {
            intTime[0],     intTime[1],     intTime[2],     intTime[3],
            nanoTime[0],    nanoTime[1],    nanoTime[2],    nanoTime[3],
            randomBytes[0], randomBytes[1], randomBytes[2], randomBytes[3],
            randomBytes[4], randomBytes[5], randomBytes[6], byteIncrement
        };

        return bytes;
    }

    public static int splitMix64(long x) {
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return (int)x;
    }

    public static CompletableFuture<NoteUUID> createHardwareNoteUUID256(){ 
        return HardwareInfo.getCPUFingerPrint().thenApply(cpuHashBytes->{
            byte[] bytes = ByteDecoding.concat(createTimeRndBytes(), cpuHashBytes.getBytes(16));
            return fromUnencodedBytes(bytes);
        });
    }

    public static NoteUUID fromNoteUUIDString(String urlSafeString){
        return fromStringBytes(urlSafeString.getBytes());
    }

    public static NoteUUID fromStringBytes(byte[] bytes){
        return new NoteUUID(Arrays.copyOf(bytes, bytes.length));
    }

    public static NoteUUID fromUnencodedBytes(byte[] bytes){
        return new NoteUUID( EncodingHelpers.encodeBytes(bytes, Encoding.BASE_64_URL_SAFE));

    }

    public static long getNextUUID64() {
       
        return ByteDecoding.bytesToLongBigEndian(createTimeSequenceBytes64());
    }

    public static byte[] createTimeSequenceBytes64() {
       
        byte[] intTimestamp   = intTimeStampBytes();
        byte[] nanoTime = littleEndianNanoTimeHash();

        return new byte[] {
            intTimestamp[0],  intTimestamp[1],  intTimestamp[2],    intTimestamp[3],
            nanoTime[0],      nanoTime[1],      nanoTime[2],        nanoTime[3]   
        };
    }

     public static int getIntTimeStamp(){
         long now = System.currentTimeMillis();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);

        int year = cal.get(Calendar.YEAR) & 0x3FF;          // 10 bits
        int day  = cal.get(Calendar.DAY_OF_YEAR) & 0x1FF;   // 9 bits

        int seconds =
                cal.get(Calendar.HOUR_OF_DAY) * 3600 +
                cal.get(Calendar.MINUTE) * 60 +
                cal.get(Calendar.SECOND);

        int compressedTime = (seconds >>> 4) & 0x1FFF;      // 13 bits

        int timestamp =
                (year << 22) |           // 10 bits
                (day << 13)  |           // 9 bits
                compressedTime;          // 13 bits = full 32 bits
        return timestamp;
    }

}
