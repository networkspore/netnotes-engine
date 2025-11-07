package io.netnotes.engine.utils;

import java.util.concurrent.atomic.AtomicInteger;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class AtomicSequence {
    private static final AtomicInteger m_atomicSequence = new AtomicInteger(-1);
    private static final AtomicInteger m_atomicHour = new AtomicInteger(-1);

    /*
     * Creates a 48-bit / 6 byte Atomic sequence
     */
    public static byte[] getNextSequence(){
        return getNextSequence(false, false);
    }

    public static NoteBytesReadOnly getNextSequenceReadOnly(){
        return new NoteBytesReadOnly(getNextSequence(), NoteBytesMetaData.RAW_BYTES_TYPE);
    }

    public static NoteBytesReadOnly getNextSequenceReadOnly(boolean aux0, boolean aux1){
        return new NoteBytesReadOnly(getNextSequence(aux0, aux1), NoteBytesMetaData.RAW_BYTES_TYPE);
    }

    /*
     * Creates a 48-bit / 6 byte Atomic sequence w/ 2 state booleans
     */
    public static byte[] getNextSequence(boolean aux0, boolean aux1){
        byte[] bytes = new byte[6];
        writeNextSequence(aux0, aux1, bytes, 0);
        return bytes;
    }

    private static volatile long m_cachedDayMillis = -1;
    private static volatile int m_cachedDayOfYear = -1;

    /*
    * Creates a 48-bit / 6 byte Atomic sequence w/ 2 state bits
    */
    public static void writeNextSequence(boolean aux0, boolean aux1, byte[] bytes, int offset) {
        long currentTimeMillis = System.currentTimeMillis();
        
        // Fast path: use cached day of year if still in same day
        long currentDayMillis = currentTimeMillis / 86400000L;
        int dayOfYear = m_cachedDayOfYear;
        
        if (currentDayMillis != m_cachedDayMillis) {
            // Slow path: recalculate day of year (rare - once per day)
            long days = currentDayMillis;
            int year = 1970;
            
            while (true) {
                int daysInYear = isLeapYear(year) ? 366 : 365;
                if (days < daysInYear) {
                    break;
                }
                days -= daysInYear;
                year++;
            }
            
            dayOfYear = ((int) days + 1) & 0x1FF;
            
            // Update cache (race condition is acceptable - all threads compute same value)
            m_cachedDayMillis = currentDayMillis;
            m_cachedDayOfYear = dayOfYear;
        }
        
        int hourOfDay = (int) ((currentTimeMillis / 3600000L) % 24) & 0x1F;
        int packedHour = (dayOfYear << 5) | hourOfDay;
        
        // Atomically detect hour rollover and reset
        int lastHour = m_atomicHour.get();
        if (packedHour != lastHour) {
            // Only one thread will win this race
            if (m_atomicHour.compareAndSet(lastHour, packedHour)) {
                m_atomicSequence.set(0);
            }
        }
        
        int seq = m_atomicSequence.getAndIncrement() & 0xFFFFFFFF;
        int auxBits = ((aux0 ? 1 : 0) << 1) | (aux1 ? 1 : 0);
        
        long packed = ((long) auxBits << (9 + 5 + 32))  // bits 47–46
                    | ((long) dayOfYear << (5 + 32))    // bits 45–37
                    | ((long) hourOfDay << 32)          // bits 36–32
                    | (seq & 0xFFFFFFFFL);              // bits 31–0
        
        bytes[offset]     = (byte) (packed >>> 40);
        bytes[offset + 1] = (byte) (packed >>> 32);
        bytes[offset + 2] = (byte) (packed >>> 24);
        bytes[offset + 3] = (byte) (packed >>> 16);
        bytes[offset + 4] = (byte) (packed >>> 8);
        bytes[offset + 5] = (byte) packed;
    }

    private static boolean isLeapYear(int year) {
        return (year % 4 == 0) && (year % 100 != 0 || year % 400 == 0);
    }

    ////
    /// Decoding
    ////
    

    /** 
    * decode aux0 from bytes
    *
    * @param bytes 6-bytes containing getAtomicSequence
    * @param offset where byte sequence starts
    * @return decoded boolean Aux0  
    */
    public static boolean decodeAtomicSequenceAux0(byte[] bytes, int offset) {
        if (bytes.length - offset < 6)
            throw new IllegalArgumentException("Need at least 6 bytes for aux0");

        // Extract bit 47
        return (bytes[offset] & 0b10000000) != 0;
    }

    public static boolean decodeAtomicSequenceAux1(byte[] bytes) {
        return decodeAtomicSequenceAux1(bytes, 0);
    }

    /** 
    * decode aux1 from bytes
    *
    * @param bytes 6-bytes containing getAtomicSequence
    * @param offset where byte sequence starts
    * @return decoded boolean Aux1  
    */
    public static boolean decodeAtomicSequenceAux1(byte[] bytes, int offset) {
        
        if (bytes.length - offset < 6)
            throw new IllegalArgumentException("Need at least 6 bytes for aux1");

        // Extract bit 46
        return (bytes[offset] & 0b01000000) != 0;
    }
    

    /** 
    * Decodes the 48-bit atomic sequence for reading
    *
    * @param bytes 6-bytes containing getAtomicSequence
    * @param offset where byte sequence starts
    * @return long48 value that contains Aux, day, time and sequence
    *  
    */
    public static long decodeAtomicSequence(byte[] bytes, int offset){
        // Reconstruct the 48-bit value
        return ((long) (bytes[offset] & 0xFF) << 40)
                    | ((long) (bytes[offset + 1] & 0xFF) << 32)
                    | ((long) (bytes[offset + 2] & 0xFF) << 24)
                    | ((long) (bytes[offset + 3] & 0xFF) << 16)
                    | ((long) (bytes[offset + 4] & 0xFF) << 8)
                    | ((long) (bytes[offset + 5] & 0xFF));
    }

    /** 
    * Reads the sequence (dayOfYear/hour/sequence)
    *
    * @param long48 decoded atomicSquenceLong
    * @return atomic sequence long
    *  
    */
    public static long readAtomicSequence(long long48) {
        // Mask off the top two bits (aux bits)
        return long48 & 0x3FFFFFFFFFFFL; // 46 bits (bits 45..0)
    }


    /** 
    * Decodes the 48-bit atomic sequence and reads the sequence
    *
    * @param bytes 6-bytes containing getAtomicSequence
    * @param offset where byte sequence starts
    * @return long (day, time and sequence)
    *  
    */
    public static long decodeAndReadAtomicSequence(byte[] bytes, int offset) {
        long long48 = decodeAtomicSequence(bytes, offset);
        // Mask off the top two bits (aux bits)
        return long48 & 0x3FFFFFFFFFFFL; // 46 bits (bits 45..0)
    }


    /** 
    * Reads the aux0 boolean
    *
    * @param long48 decoded atomicSquenceLong
    * @return aux0 boolean
    *  
    */
    public static boolean readAux0(long long48){
        return(long48 & 0x800000000000L) != 0;
    }

    /** 
    * Reads the aux1 boolean
    *
    * @param long48 decoded atomicSquenceLong
    * @return aux1 boolean
    *  
    */
    public static boolean readAux1(long long48){
        return (long48 & 0x400000000000L) != 0;
    }
    

    /** 
    * Reads the day of the year the decoded long squence.
    * @param long48 from decodeAtomicSequence
    * @return The day of the year.
    */
    public static int readDayOfYear(long long48) {
        return (int)((long48 >> 33) & 0x1FF); // top 2 bits are aux, next 9 bits = dayOfYear
    }

    
    /** 
    * Extracts the hour of day from the decoded long squence.
    *
    * @param long48 from decodeAtomicSequence
    * @return the hour of day
    */
    public static int getHourOfDay(long long48) {
        return (int)((long48 >> 28) & 0x1F); // after dayOfYear, next 5 bits = hourOfDay
    }
}
