package io.netnotes.engine.noteBytes;

import java.lang.ref.Cleaner;
import java.util.Arrays;

import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.utils.LoggingHelpers.Log;

public class NoteBytesReadOnlyEphemeral extends NoteBytesReadOnly implements AutoCloseable {
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
                        Log.logError("Warning: Memory clear verification failed at index " + i);
                    }
                }
                Thread.yield();
            }
        }
    }

    public NoteBytesReadOnlyEphemeral( String value){
        this( ByteDecoding.stringToBytes(value,  NoteBytesMetaData.STRING_TYPE), NoteBytesMetaData.STRING_TYPE);
    }
    public NoteBytesReadOnlyEphemeral(char[] chars){
        this( chars, NoteBytesMetaData.STRING_TYPE);
    }

    public NoteBytesReadOnlyEphemeral(byte b){
        this(new byte[]{ b }, NoteBytesMetaData.BYTE_TYPE);
    }

    public NoteBytesReadOnlyEphemeral(int integer){
        this(ByteDecoding.intToBytesBigEndian(integer), NoteBytesMetaData.INTEGER_TYPE);
    }

    public NoteBytesReadOnlyEphemeral(long l){
        this(ByteDecoding.longToBytesBigEndian(l), NoteBytesMetaData.LONG_TYPE);
    }

    public NoteBytesReadOnlyEphemeral( char[] value, byte type){
        this( ByteDecoding.charsToByteArray(value, type), type);
    }

    public NoteBytesReadOnlyEphemeral(float l){
        this(ByteDecoding.floatToBytesBigEndian(l), NoteBytesMetaData.FLOAT_TYPE);
    }


    public NoteBytesReadOnlyEphemeral(Object value){
        this(NoteBytes.of(value));
    }

    public NoteBytesReadOnlyEphemeral(NoteBytes value){
        super(value.get(), value.getType());
        this.cleanable = cleaner.register(this, new EphemeralCleanupState(value.get()));
    }

   
    public NoteBytesReadOnlyEphemeral( byte[] value, byte type){
        super(value, type);
        this.cleanable = cleaner.register(this, new EphemeralCleanupState(value));
    }

    public static NoteBytesReadOnlyEphemeral readNote(byte[] bytes, int offset){
        return readNote(bytes, offset, false);
    }

    public static NoteBytesReadOnlyEphemeral readNote(byte[] bytes, int offset, boolean isLittleEndian){
        byte type = bytes[offset];
        offset++;
        int size = isLittleEndian ? ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
        byte[] dst = new byte[size];
        System.arraycopy(bytes, offset + 4, dst, 0, size);
        return new NoteBytesReadOnlyEphemeral(dst, type);
    }


    
    @Override
    public NoteBytesReadOnlyEphemeral copy(){
        return new NoteBytesReadOnlyEphemeral(Arrays.copyOf(get(), byteLength()), getType());
    }

    @Override
    public NoteBytesReadOnlyEphemeral copyOf(int length){
        if(byteLength() > 0 && length > 0){
            int maxLen = Math.min(length, byteLength());
            byte[] bytes = get();
            byte[] newbytes = new byte[maxLen];
            System.arraycopy(bytes,0, newbytes, 0, maxLen);
            return new NoteBytesReadOnlyEphemeral(newbytes, getType());
        }else{
            return new NoteBytesReadOnlyEphemeral(new byte[0], getType());
        }
    }

    @Override
    public void set(byte[] disabled){

    }

    @Override
    public void set(byte[] disabled, byte type){

    }

    @Override
    public void setType(byte disabled){

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
 
    @Override
    public void ruin(){
        super.ruin();
    }

}
