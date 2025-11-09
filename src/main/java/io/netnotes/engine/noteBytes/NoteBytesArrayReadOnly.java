package io.netnotes.engine.noteBytes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteBytesArrayReadOnly extends NoteBytes {

    public NoteBytesArrayReadOnly(byte[] bytes){
        super(Arrays.copyOf(bytes, bytes.length), NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE);
    }

    public NoteBytesArrayReadOnly(NoteBytes[] noteBytes){
        this(NoteBytesArray.getBytesFromArray(noteBytes));
    }

    public Stream<NoteBytesReadOnly> getAsReadOnlyStream(){
        
        byte[] bytes = super.getBytesInternal();
        Stream.Builder<NoteBytesReadOnly> noteBytesBuilder = Stream.builder();
        int length = bytes.length;
        int offset = 0;
        
        while(offset < length){
            NoteBytesReadOnly noteBytes = NoteBytesReadOnly.readNote(bytes, offset);
            noteBytesBuilder.accept(noteBytes);
            offset += (5 + noteBytes.byteLength());
        }
        return noteBytesBuilder.build();
    
    }

    public NoteBytesArrayReadOnly copy(){
        return new NoteBytesArrayReadOnly(getBytesInternal());
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

    public NoteBytesReadOnly[] getAsArray(){
    
        int size = size();
        NoteBytesReadOnly[] arr = new NoteBytesReadOnly[size];
        byte[] bytes = getBytesInternal();
        int length = bytes.length;
        int offset = 0;
        int i = 0;
        while(offset < length){
            NoteBytesReadOnly noteBytes = NoteBytesReadOnly.readNote(bytes, offset);
            arr[i] = noteBytes;
            i++;
            offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + noteBytes.byteLength());
        }
        return arr;
    }

    @Override
    public byte[] get(){
        byte[] data = super.getBytesInternal();
        return Arrays.copyOf(data, data.length);
    }

    public List<NoteBytesReadOnly> getAsReadOnlyList(){
        return getAsReadOnlyStream().toList();
    }


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

    }

    @Override
    public void destroy(){
        ruin();
    }

    @Override
    public void ruin(){
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
