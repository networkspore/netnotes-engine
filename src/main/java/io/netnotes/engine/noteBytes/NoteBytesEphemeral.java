package io.netnotes.engine.noteBytes;

import java.io.IOException;
import java.io.Serializable;
import java.lang.ref.Cleaner;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import com.google.gson.JsonObject;

import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.collections.NoteBytesConcurrentMapEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesMapEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.collections.NoteBytesPairEphemeral;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class NoteBytesEphemeral extends NoteBytes implements AutoCloseable {
    private static final Cleaner cleaner = Cleaner.create();
    private final Cleaner.Cleanable cleanable;
    //public static volatile int clearanceVerifier = 0;

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
               
                /*for (int i = 0; i < dataToClean.length; i++) {
                    clearanceVerifier ^= dataToClean[i]; 
                    if (dataToClean[i] != 0) {
                        System.err.println("Warning: Memory clear verification failed at index " + i);
                    }
                }*/
                Thread.yield();
            }
        }
    }

    public NoteBytesEphemeral( byte[] value){
        super(value , NoteBytesMetaData.RAW_BYTES_TYPE);
        this.cleanable = cleaner.register(this, new EphemeralCleanupState(get()));
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
        destroy();
        cleanable.clean(); // Trigger cleanup action manually
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

    public static NoteBytesEphemeral create(Object obj) {
        if(obj == null){
            throw new NullPointerException("Cannot create null object");
        }else if(obj instanceof NoteBytesEphemeral){
            return (NoteBytesEphemeral) obj;
        }else if(obj instanceof NoteBytes){
            return new NoteBytesEphemeral((NoteBytes) obj);
        }else if(obj instanceof NoteBytesMapEphemeral){
            return ((NoteBytesMapEphemeral) obj).getNoteBytesEphemeral();
        }else if(obj instanceof NoteBytesConcurrentMapEphemeral){
            return ((NoteBytesConcurrentMapEphemeral) obj).getNoteBytesEphemeral();
        }else if(obj instanceof NoteBytesMap){
            return new NoteBytesEphemeral( ((NoteBytesMap) obj).getNoteBytesObject());
        }else if (obj instanceof Boolean) {
            return new NoteBytesEphemeral(new NoteBoolean((Boolean) obj).get());
        } else if (obj instanceof Integer) {
            return new NoteBytesEphemeral(new NoteInteger((Integer) obj));
        } else if (obj instanceof Long) {
            return new NoteBytesEphemeral(new NoteLong((Long) obj));
        } else if (obj instanceof Double) {
            return new NoteBytesEphemeral(new NoteDouble((Double) obj));
        } else if (obj instanceof BigInteger) {
            return new NoteBytesEphemeral(new NoteBigInteger((BigInteger) obj));
        } else if (obj instanceof BigDecimal) {
            return new NoteBytesEphemeral(new NoteBigDecimal((BigDecimal) obj));
        } else if (obj instanceof String) {
            return new NoteBytesEphemeral(new NoteString((String) obj));
        } else if (obj instanceof byte[]) {
            return new NoteBytesEphemeral((byte[]) obj);
        } else if (obj instanceof char[]) {
            return new NoteBytesEphemeral(new NoteBytes((char[]) obj));
        } else if (obj instanceof JsonObject) {
            return new NoteBytesEphemeral(new NoteJsonObject((JsonObject) obj));
        } else if (obj instanceof NoteBytesObject) {
            return new NoteBytesEphemeral((NoteBytesObject) obj);
        } else if (obj instanceof NoteBytesArray) {
            return new NoteBytesEphemeral((NoteBytesArray) obj);
        } else if (obj instanceof NoteBytesPair) {
            return new NoteBytesEphemeral(new NoteBytesObject(new NoteBytesPair[]{(NoteBytesPair) obj}));
        }else if (obj instanceof NoteBytesPair[]){
            return new NoteBytesEphemeral(new NoteBytesObject((NoteBytesPair[]) obj));
        }else if(obj instanceof Serializable){ 
            try{
                return new NoteBytesEphemeral(new NoteSerializable(obj));
            }catch(IOException e){
                throw new IllegalArgumentException(e);
            }
        }

        throw new IllegalArgumentException("Cannot create NoteBytesEphemeral from: " + obj.getClass().getName());
    }
}
