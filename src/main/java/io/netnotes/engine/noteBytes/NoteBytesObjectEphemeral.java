package io.netnotes.engine.noteBytes;

import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;


import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.collections.NoteBytesPairEphemeral;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;



public class NoteBytesObjectEphemeral extends NoteBytes implements AutoCloseable{
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


    public NoteBytesObjectEphemeral(byte[] bytes){
        super(bytes, NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE);      
        this.cleanable = cleaner.register(this, new EphemeralCleanupState(bytes)); 
    }

    public NoteBytesObjectEphemeral(NoteBytesPairEphemeral[] pairs){
        this(noteBytePairsToByteArray(pairs));
    }

    public NoteBytesObjectEphemeral(NoteBytesPairEphemeral pair){
        this(pair.get());
    }



    public static byte[] noteBytePairsToByteArray(NoteBytesPairEphemeral[] pairs) {
        int byteLength = 0;
        for(NoteBytesPairEphemeral pair : pairs){
            byteLength += pair.byteLength();
        }
        byte[] bytes = new byte[byteLength];
        int offset = 0;
        for(NoteBytesPairEphemeral pair : pairs) {
            offset = NoteBytesPairEphemeral.write(pair, bytes, offset);
        }
        return bytes;
    }

    public Stream<NoteBytesPairEphemeral> getAsStream() {
        byte[] bytes = get();
        Stream.Builder<NoteBytesPairEphemeral> noteBytesBuilder = Stream.builder();
        int length = bytes.length;
        int offset = 0;
    
        while(offset < length) {
            NoteBytesPairEphemeral pair = NoteBytesPairEphemeral.read(bytes, offset);
            noteBytesBuilder.accept(pair);
            offset += 10 + pair.getKey().byteLength() + pair.getValue().byteLength(); // 2 bytes type + 8 bytes length
        }
        return noteBytesBuilder.build();
       
    }

    public NoteBytesPairEphemeral[] getAsArray(){
        return getAsList().toArray(new NoteBytesPairEphemeral[0]);
    }

    public ArrayList<NoteBytesPairEphemeral> getAsList(){
        ArrayList<NoteBytesPairEphemeral> pairs = new ArrayList<>();
        byte[] bytes = get();
      
        int length = bytes.length;
        int offset = 0;
    
        while(offset < length) {
            NoteBytesPairEphemeral pair = NoteBytesPairEphemeral.read(bytes, offset);
            pairs.add(pair);
            offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE *2) + pair.getKey().byteLength() + pair.getValue().byteLength(); // 2 bytes type + 8 bytes length
        }

        return pairs;
    }

    public List<NoteBytesPairEphemeral> getKeyValuePairs(){
        return getAsList();
    }



    public NoteBytesPairEphemeral get(String keyString){
        return get(new NoteBytes(keyString));
    }

    public boolean contains(NoteBytes key){
        byte[] bytes = get();
        int length = bytes.length;
        
        if(bytes != null && length > 0) {
            int offset = 0;
            while(offset < length) {
                NoteBytesPairEphemeral pair = NoteBytesPairEphemeral.read(bytes, offset);
                if(pair.getKey().equals(key)) {
                    return true;
                }
                offset += 10 + pair.getKey().byteLength() + pair.getValue().byteLength();
            }
        }
        return false;
    }
    public NoteBytesPairEphemeral get(NoteBytes key) {
      
        byte[] bytes = get();
        int length = bytes.length;
        
        if(bytes != null && length > 0) {
            int offset = 0;
            while(offset < length) {
                NoteBytesPairEphemeral pair = NoteBytesPairEphemeral.read(bytes, offset);
                if(pair.getKey().equals(key)) {
                    return pair;
                }
                offset += 10 + pair.getKey().byteLength() + pair.getValue().byteLength();
            }
        }
        return null;
    
    }



    public NoteBytesPairEphemeral getFirst(){
        return getAtIndex(0);
    }

    public NoteBytesPairEphemeral getAtIndex(int index){
      
        byte[] bytes = get();
        int length = bytes.length;
        if(bytes != null && length > 0){
            int offset = 0;
            int i = 0;
            while(offset < length){
                NoteBytesPairEphemeral pair = NoteBytesPairEphemeral.read(bytes, offset);
                if(i == index){
                    return pair;
                }
                offset += 10 + pair.getKey().byteLength() + pair.getValue().byteLength();
                i++;
            }
        }
        return null;
        
    }


    public boolean isEmpty() {
        return byteLength() == 0;
    }



    public int size() {
        byte[] bytes = get();
        if (bytes == null || bytes.length == 0) {
            return 0;
        }
        int length = bytes.length;
        int offset = 0;
        int counter = 0;
        
        while (offset < length) {
            offset ++; //+ 1 type
            offset += 4 + ByteDecoding.bytesToIntBigEndian(bytes, offset); // 4 bytes int length + content
            counter++;
        }
        return counter;
    }



    @Override
    public void clear(){
        cleanable.clean();
    }
    
    @Override
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
