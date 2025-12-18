package io.netnotes.engine.noteBytes;

import java.util.Arrays;
import java.util.Calendar;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.processing.ByteEncoding;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.ByteEncoding.EncodingType;
import io.netnotes.engine.utils.HardwareInfo;

public class NoteUUID extends NoteBytesReadOnly {

    private static final AtomicInteger m_atomicInteger = new AtomicInteger(createSequenceRand());
    private static final AtomicInteger m_lastTime = new AtomicInteger(getIntTimeStamp());
    private NoteUUID(byte[] bytes){
        super(bytes, NoteBytesMetaData.STRING_ISO_8859_1_TYPE);
    }
      
    public static byte[] nanoTimeHash(){
        return ByteDecoding.intToBytesBigEndian(splitMix64(System.nanoTime()));
    }

    public static byte[] currentTimeStampBytes(){
        return ByteDecoding.longToBytesBigEndian(System.currentTimeMillis());
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
		byte[] nanoTime = nanoTimeHash();
		byte[] randomBytes = RandomService.getRandomBytes(4);
		byte[] timeSequence = createTimeSequenceBytes64();

        byte[] bytes = new byte[] {
            timeSequence[0],    timeSequence[1],    timeSequence[2],    timeSequence[3],
            timeSequence[4],    timeSequence[5],    timeSequence[6],    timeSequence[7],
            nanoTime[0],        nanoTime[1],        nanoTime[2],        nanoTime[3],
            randomBytes[0],     randomBytes[1],     randomBytes[2],     randomBytes[3]
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

    public static NoteUUID fromNoteBytes(NoteBytes notebytes){
        byte[] bytes = notebytes.getBytes(notebytes.byteLength());
    
        return ByteDecoding.isStringType(notebytes.getType())
            ? new NoteUUID(bytes)
            : fromUnencodedBytes(bytes);
        
    }

    public static NoteUUID fromUnencodedBytes(byte[] bytes){
        return new NoteUUID(ByteEncoding.encodeBytes(bytes, EncodingType.BASE_64_URL_SAFE));

    }

    public static long getNextUUID64() {
       
        return ByteDecoding.bytesToLongBigEndian(createTimeSequenceBytes64());
    }

    public static int getNextSequenceRand(){
        return getNextSequenceRand(getIntTimeStamp());
    }

    private static int createSequenceRand(){
        return ByteDecoding.bytesToIntBigEndian(RandomService.getRandomBytes(4)) & 0x7FFFFFFF;
    }

    private static int getNextSequenceRand(int now) {
        int rand = createSequenceRand();
        
        while (true) {
            int last = m_lastTime.get();

            if (last == now) {
                
                return m_atomicInteger.incrementAndGet();
            }

            // Timestamp changed → try to update the timestamp
            if (m_lastTime.compareAndSet(last, now)) {
                // Successfully updated timestamp → reset counter
                m_atomicInteger.set(rand);
                return rand; // first value for this new window
            }

            // Else: another thread changed it first → loop and retry
        }
    }

    public static byte[] createTimeSequenceBytes64() {
        int now = getIntTimeStamp();
        int seq = getNextSequenceRand(now);

        byte[] intTimestamp = ByteDecoding.intToBytesBigEndian(now);
        byte[] seqBytes = ByteDecoding.intToBytesBigEndian(seq);
        
        return new byte[] {
            intTimestamp[0],  intTimestamp[1],  intTimestamp[2],    intTimestamp[3],
            seqBytes[0],      seqBytes[1],      seqBytes[2],      seqBytes[3]   
        };
    }

    /**
     * Current timestamp YYY/MM-SSSSS
     * Hour/Min/Sec in 16s resolution
     * @return
     */
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
