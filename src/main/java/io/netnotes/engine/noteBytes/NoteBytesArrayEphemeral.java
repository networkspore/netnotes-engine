package io.netnotes.engine.noteBytes;

import java.lang.ref.Cleaner;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.utils.LoggingHelpers.Log;

public class NoteBytesArrayEphemeral extends NoteBytes implements AutoCloseable {
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

    public NoteBytesArrayEphemeral(byte[] value){
        super(value);
        this.cleanable = cleaner.register(this, new EphemeralCleanupState(value));
    }

    @Override
    public NoteBytesArrayEphemeral copy(){
        if(byteLength() > 0){
            byte[] bytes = get();
            byte[] newbytes = new byte[bytes.length];
            System.arraycopy(bytes,0, newbytes, 0, bytes.length);
            return new NoteBytesArrayEphemeral(newbytes);
        }else{
            return new NoteBytesArrayEphemeral(new byte[0]);
        }
    }

    @Override
    public NoteBytesArrayEphemeral copyOf(int length){
        if(byteLength() > 0 && length > 0){
            int maxLen = Math.min(length, byteLength());
            byte[] bytes = get();
            byte[] newbytes = new byte[maxLen];
            System.arraycopy(bytes,0, newbytes, 0, maxLen);
            return new NoteBytesArrayEphemeral(newbytes);
        }else{
            return new NoteBytesArrayEphemeral(new byte[0]);
        }
    }


    @Override
    public void close() {
        cleanable.clean(); // Trigger cleanup action manually
    }

    @Override 
    public void destroy(){
        cleanable.clean();
    }


    public Stream<NoteBytesEphemeral> getAsReadOnlyStream(){
        
        byte[] bytes = super.getBytesInternal();
        Stream.Builder<NoteBytesEphemeral> noteBytesBuilder = Stream.builder();
        int length = bytes.length;
        int offset = 0;
        
        while(offset < length){
            NoteBytesEphemeral noteBytes = NoteBytesEphemeral.readNote(bytes, offset);
            noteBytesBuilder.accept(noteBytes);
            offset += (5 + noteBytes.byteLength());
        }
        return noteBytesBuilder.build();
    
    }

    public NoteBytesEphemeral getAt(int index){
     
        byte[] bytes = getBytesInternal();
        int length = bytes.length;
        int offset = 0;
        int counter = 0;

        while(offset < length){
            byte type = bytes[offset];
            offset++;
            int size = ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += 4;
            if(counter == index){
                byte[] dst = new byte[size];
                System.arraycopy(bytes, offset, dst, 0, size);
                return new NoteBytesEphemeral(dst, type);
            }
            offset += size;
            counter++;
        }
        return null;
        
    }

    public NoteBytesEphemeral getFirst(){
        int length = byteLength();
        if(length == 0){
            return null;
        }
        byte[] bytes = getBytesInternal();
        byte type = bytes[0];
        int size = ByteDecoding.bytesToIntBigEndian(bytes, 1);
        int offset = NoteBytesMetaData.STANDARD_META_DATA_SIZE;
        byte[] dst = new byte[size];
        System.arraycopy(bytes, offset, dst, 0, size);
        return new NoteBytesEphemeral(dst, type);
        
    }

    public NoteBytesEphemeral getLast(){
     
        byte[] bytes = getBytesInternal();
        int length = bytes.length;
        int offset = 0;
        while(offset < length){
            byte type = bytes[offset];
            offset++;
            int size = ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += 4;
            if(offset + size == length){
                byte[] dst = new byte[size];
                System.arraycopy(bytes, offset, dst, 0, size);
                return new NoteBytesEphemeral(dst, type);
            }
            offset += size;
        }
        return null;
        
    }
    
    @Override
    public int hashCode(){
        return Arrays.hashCode(get());
    }

    public NoteBytesEphemeral get(int index){
        return getAt(index);
    }


    public int size(){
        byte[] bytes = getBytesInternal();
        if(bytes == null){
            return -1;
        }else if(bytes.length == 0){
            return 0;
        }else{
            int length = bytes.length;
            int offset = 0;
            int counter = 0;
           
            while(offset < length){
                offset++;
                int size = ByteDecoding.bytesToIntBigEndian(bytes, offset);
                offset += 4 + size;
                counter++;
            }
            return counter;
        }
    }

    public NoteBytesEphemeral[] getAsArray(){
    
        int size = size();
        NoteBytesEphemeral[] arr = new NoteBytesEphemeral[size];
        byte[] bytes = getBytesInternal();
        int length = bytes.length;
        int offset = 0;
        int i = 0;
        while(offset < length){
            NoteBytesEphemeral noteBytes = NoteBytesEphemeral.readNote(bytes, offset);
            arr[i] = noteBytes;
            i++;
            offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + noteBytes.byteLength());
        }
        return arr;
    }

    @Override
    public byte[] get(){
        return super.getBytesInternal();
    }

    public List<NoteBytesEphemeral> getAsReadOnlyList(){
        return getAsReadOnlyStream().toList();
    }

    public boolean isEmpty() { return size() == 0; }
    

    public boolean contains(NoteBytes noteBytes){
        return indexOf(noteBytes) != -1;
    }

    public boolean arrayStartsWith(NoteBytes noteBytes){        
        byte[] src = getBytesInternal();
        byte[] dst = noteBytes.get();
        int length = src.length;
        int dstLength = dst.length;

        if(length > dstLength){
            return false;
        }
        if(length == dstLength){
            return noteBytes.equals(this);
        }
        
        return Arrays.equals(src, 0, length, dst, 0, dstLength);
    }
    
    public int indexOf(NoteBytes noteBytes){
        byte[] a = getBytesInternal();
        int length = a.length;
        byte[] b = noteBytes.get(); 
        byte bType = noteBytes.getType();
        int offset = 0;
        int counter = 0;
        while(offset < length){
            byte aType = a[offset];
            offset++;
            int size = ByteDecoding.bytesToIntBigEndian(a, offset);
            offset += 4;

            if(aType == bType &&  Arrays.equals(a, offset, size, b, 0, b.length)){
                return counter;
            }
            offset += size;
            counter++;
        }
        return -1;
    }


    public int findOffsetForIndex(int index) {
        int offset = 0;
        int count = 0;
        byte[] bytes = getBytesInternal();
        int length = bytes.length;

        while (offset < length && count < index) {
            if (offset + 5 > length) break; // invalid/truncated note
            int srcLen = ByteDecoding.bytesToIntBigEndian(bytes, offset + 1);
            offset += 5 + srcLen;
            count++;
        }

        return offset; // if index == count, this is insert position
    }



    @Override
    public void set(byte[] disabled){

    }

    @Override
    public void set(byte[] disabled, byte type){

    }

    @Override
    public void setType(byte type){

    }

    @Override
    public void clear(){
        close();
    }

    @Override
    public void ruin(){
        close();
        super.ruin();
    }

    @Override
    public JsonElement getAsJsonElement(){
        return getAsJsonArray();
    }
    
    @Override
    public JsonArray getAsJsonArray(){
        byte[] bytes = getBytesInternal();
       
        if(bytes != null){
            int length = bytes.length;
            if(length == 0){
                return new JsonArray();
            }
            JsonArray jsonArray = new JsonArray();
    
            int offset = 0;
    
            while(offset < length){
                NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
                byte type = noteBytes.getType();
                
                if(type == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE){
                    jsonArray.add(noteBytes.getAsJsonArray());
                } else if(type == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
                    jsonArray.add(noteBytes.getAsJsonObject());
                } else {
                    jsonArray.add(noteBytes.getAsJsonElement());
                }
                
                offset += 5 + noteBytes.byteLength(); // 1 byte type + 4 bytes length + content
            }
           
            return jsonArray;
        }
        return null;
    }

    @Override
    public JsonObject getAsJsonObject() {
        byte[] bytes = getBytesInternal();
        if(bytes != null){
             int length = bytes.length;
            if(length == 0){
                return new JsonObject();
            }
            JsonObject jsonObject = new JsonObject();
          
            int offset = 0;
            int i = 0;
            while(offset < length){
                NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
                byte type = noteBytes.getType();
                
                if(type == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE){
                    jsonObject.add(i + "", noteBytes.getAsJsonArray());
                } else if(type == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
                    jsonObject.add(i + "", noteBytes.getAsJsonObject());
                } else {
                    jsonObject.add(i + "", noteBytes.getAsJsonElement());
                }
                
                offset += 5 + noteBytes.byteLength(); // 1 byte type + 4 bytes length + content
                i++;
            }
            return jsonObject;
        }
        return null;
    }

    @Override 
    public String toString(){
        return getAsJsonArray().toString();
    }
}
