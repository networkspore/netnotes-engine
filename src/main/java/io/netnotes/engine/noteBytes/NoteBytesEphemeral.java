package io.netnotes.engine.noteBytes;

import java.lang.ref.Cleaner;
import java.util.Arrays;

import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.collections.NoteBytesPairEphemeral;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteBytesEphemeral extends NoteBytes implements AutoCloseable {
    private static final Cleaner cleaner = Cleaner.create();
    private final Cleaner.Cleanable cleanable;


    private static class EphemeralCleanupState implements Runnable {
        private byte[] dataToClean;
      
        EphemeralCleanupState(byte[] data) {
            this.dataToClean = data;
        }

   
        @Override
        public void run() {
            // Best effort clearing when GC'd
            if (dataToClean != null && dataToClean.length > 0) {
         
                RandomService.getSecureRandom().nextBytes(dataToClean);
                Arrays.fill(dataToClean, (byte) 0);
               
                for (int i = 0; i < dataToClean.length; i++) {
                    clearanceVerifier ^= dataToClean[i]; 
                    if (dataToClean[i] != 0) {
                        System.err.println("Warning: Memory clear verification failed at index " + i);
                    }
                }
                Thread.yield();
            }
        }
    }

    public NoteBytesEphemeral( byte[] value){
        super(value , NoteBytesMetaData.RAW_BYTES_TYPE);
        this.cleanable = cleaner.register(this, new EphemeralCleanupState(value));
    }
    
    public NoteBytesEphemeral(NoteBytesPairEphemeral[] pairs){
        this(noteBytePairsEphemeralToByteArray(pairs), NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE);
    }
    public NoteBytesEphemeral(NoteBytesPairEphemeral pair){
        this(noteBytePairsEphemeralToByteArray(new NoteBytesPairEphemeral[]{pair}), NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE);
    }


    public NoteBytesEphemeral( byte[] value, byte type){
        super(value , type);
        this.cleanable = cleaner.register(this, new EphemeralCleanupState(get()));
    }

    public NoteBytesEphemeral(NoteBytes other) {
        super(other.getBytes(), other.getType());
        this.cleanable = cleaner.register(this, new EphemeralCleanupState(get()));
    }


    public NoteBytesEphemeral(boolean value){
        this(ByteDecoding.booleanToBytes(value), NoteBytesMetaData.BOOLEAN_TYPE);
    }

    public NoteBytesEphemeral( String value){
        this( ByteDecoding.stringToBytes(value,  NoteBytesMetaData.STRING_TYPE), NoteBytesMetaData.STRING_TYPE);
    }
    public NoteBytesEphemeral(char[] chars){
        this( chars, NoteBytesMetaData.STRING_TYPE);
    }

     public NoteBytesEphemeral( char[] value, byte type){
        this( ByteDecoding.charsToByteArray(value, type), type);
    }

    public NoteBytesEphemeral(int integer){
        this(ByteDecoding.intToBytesBigEndian(integer), NoteBytesMetaData.INTEGER_TYPE);
    }


    public NoteBytesEphemeral(long l){
        this(ByteDecoding.longToBytesBigEndian(l), NoteBytesMetaData.LONG_TYPE);
    }


    @Override
    public void set(byte[] bytes, byte type){
        
    }


    @Override
    public NoteBytesEphemeral copy(){
        if(byteLength() > 0){
            byte[] bytes = get();
            byte[] newbytes = new byte[bytes.length];
            System.arraycopy(bytes,0, newbytes, 0, bytes.length);
            return new NoteBytesEphemeral(newbytes, getType());
        }else{
            return new NoteBytesEphemeral(new byte[0], getType());
        }
    }

    @Override
    public NoteBytesEphemeral copyOf(int length){
        if(byteLength() > 0 && length > 0){
            int maxLen = Math.min(length, byteLength());
            byte[] bytes = get();
            byte[] newbytes = new byte[maxLen];
            System.arraycopy(bytes,0, newbytes, 0, maxLen);
            return new NoteBytesEphemeral(newbytes, getType());
        }else{
            return new NoteBytesEphemeral(new byte[0], getType());
        }
    }

    public static NoteBytesEphemeral readNote(byte[] bytes, int offset){
        byte type = bytes[offset];
        offset++;
        int size = ByteDecoding.bytesToIntBigEndian(bytes, offset);
        byte[] dst = new byte[size];
        System.arraycopy(bytes, offset + 4, dst, 0, size);
        return new NoteBytesEphemeral(dst, type);
    }

     /**
     * Explicitly clean up resources - called by try-with-resources
     */
    public void close() {
        cleanable.clean(); // Trigger cleanup action manually
    }

    @Override 
    public void destroy(){
        cleanable.clean();
    }

    public static byte[] noteBytePairsEphemeralToByteArray(NoteBytesPairEphemeral[] pairs) {
        int byteLength = 0;
        for(NoteBytesPairEphemeral pair : pairs){
            byteLength += pair.byteLength();
        }
        byte[] bytes = new byte[byteLength];
        int offset = 0;
        for(NoteBytesPairEphemeral pair : pairs) {
            offset = NoteBytes.writeNote(pair.getKey(), bytes, offset);
            offset = NoteBytes.writeNote(pair.getValue(), bytes, offset);
        }
        return bytes;
    }
    
    /**
    * Create an ephemeral copy of existing NoteBytes for temporary use
    */
    public static NoteBytesEphemeral fromExisting(NoteBytes source) {
        return new NoteBytesEphemeral(source);
    }

    /**
    * Create an ephemeral copy of existing NoteBytes for temporary use
    */
    public static NoteBytesEphemeral fromExisting(NoteBytesReadOnly source) {
        return new NoteBytesEphemeral(source);
    }

    @Override
    public void clear(){
        close();
    }

    public static NoteBytesEphemeral create(Object obj) {
        return new NoteBytesEphemeral(NoteBytes.of(obj));
    }
}
