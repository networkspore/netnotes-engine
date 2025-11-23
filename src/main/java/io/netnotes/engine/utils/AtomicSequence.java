package io.netnotes.engine.utils;

import java.util.Calendar;
import java.util.concurrent.atomic.AtomicInteger;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class AtomicSequence {
    private static final AtomicInteger m_atomicSequence = new AtomicInteger(-1);

    /*
     * Creates a 48-bit / 6 byte Atomic sequence
     */

    public static NoteBytesReadOnly getNextSequenceReadOnly(){
        return new NoteBytesReadOnly(getNextSequence(), NoteBytesMetaData.LONG_TYPE);
    }

    /*
     * Creates a 48-bit / 6 byte Atomic sequence w/ 2 state booleans
     */
    public static byte[] getNextSequence(){
        byte[] bytes = new byte[8];
        writeNextSequence(bytes, 0);
        return bytes;
    }


    /*
    * Creates a 48-bit / 6 byte Atomic sequence w/ 2 state bits
    */
    public static void writeNextSequence(byte[] bytes, int offset) {
        long value = getNextSequenceLong();
        for (int i = 0; i < 8; i++) {
            bytes[offset + i] = (byte) (value >>> (56 - (i * 8)));
        }
    }

    public static long getNextSequenceLong() {
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

   

        int seq = m_atomicSequence.getAndIncrement(); // 32-bit seq

        return ((long) timestamp << 32) | (seq & 0xFFFFFFFFL);
    }


    public final class SequenceIdDecoder {

        public static int decodeSequence(long value) {
            return (int) (value & 0xFFFFFFFFL);
        }

        public static int decodeTimestamp(long value) {
            return (int) ((value >>> 32) & 0xFFFFFFFFL);
        }

        public static int decodeYear(int timestamp) {
            return (timestamp >>> 22) & 0x3FF;  // 10 bits
        }

        public static int decodeDayOfYear(int timestamp) {
            return (timestamp >>> 13) & 0x1FF;  // 9 bits
        }

        public static int decodeCompressedTime(int timestamp) {
            return timestamp & 0x1FFF;         // 13 bits
        }

        public static int decodeSecondsSinceMidnight(int compressedTime) {
            return compressedTime << 4;        // multiply by 16
        }

        public static int decodeHour(int secondsSinceMidnight) {
            return secondsSinceMidnight / 3600;
        }

        public static int decodeMinute(int secondsSinceMidnight) {
            return (secondsSinceMidnight % 3600) / 60;
        }

        public static int decodeSecond(int secondsSinceMidnight) {
            return secondsSinceMidnight % 60;
        }

        public static SequenceId decode(long packed) {
            int seq       = decodeSequence(packed);
            int timestamp = decodeTimestamp(packed);

            int year           = decodeYear(timestamp);
            int dayOfYear      = decodeDayOfYear(timestamp);
            int compressed     = decodeCompressedTime(timestamp);
            int secondsMidnight = decodeSecondsSinceMidnight(compressed);

            int hour   = decodeHour(secondsMidnight);
            int minute = decodeMinute(secondsMidnight);
            int second = decodeSecond(secondsMidnight);

            return new SequenceId(
                packed,
                year,
                dayOfYear,
                hour,
                minute,
                second,
                seq
            );
        }

        public record SequenceId(
            long atomicSequence,
            int year,
            int dayOfYear,
            int hour,
            int minute,
            int second,
            int sequence
        ) {
            @Override
            public int hashCode(){
                return Long.hashCode(atomicSequence);
            }

            @Override
            public boolean equals(Object obj){
                if(obj == null){ return false; }
                if(obj == this){ return true; }
                if(obj instanceof Long l){
                    return l == atomicSequence;
                }
                if(obj instanceof SequenceId seqId){
                    return seqId.atomicSequence == atomicSequence;
                }
                if((obj instanceof NoteBytes nb) && (
                        nb.getType() == NoteBytesMetaData.LONG_TYPE 
                        || (nb.getType() == NoteBytesMetaData.RAW_BYTES_TYPE && nb.byteLength() == 8)
                    )){
                    return nb.getAsLong() == atomicSequence;
                }
                return false;
            }
        }
    }

}
