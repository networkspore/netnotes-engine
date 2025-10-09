package io.netnotes.engine.noteBytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class NoteBytesArray extends NoteBytes{

    public NoteBytesArray(){
        super(new byte[0], NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE);
    }

    public NoteBytesArray(byte[] bytes){
        super(bytes, NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE);
    }

    public NoteBytesArray(NoteBytes[] noteBytes){
        this(getBytesFromArray(noteBytes));
    }

    public NoteBytesArray(Stream<NoteBytes> stream){
        this(getBytesFromStream(stream));
    }

  
    public static byte[]  getBytesFromStream(Stream<NoteBytes> stream){
        NoteBytes[] noteBytesArray = stream.toArray(NoteBytes[]::new);
        return getBytesFromArray(noteBytesArray);
    }
    
    public static byte[] getBytesFromArray(NoteBytes[] noteBytesArray){
        int length = noteBytesArray != null && noteBytesArray.length > 0 ? noteBytesArray.length : 0;
        if(length > 0){

            int size = getByteLength(noteBytesArray);
            byte[] bytes = new byte[size];
            int offset = 0;
            for(NoteBytes value : noteBytesArray) {
                offset = NoteBytes.writeNote(value, bytes, offset);
            }
        }
        return new byte[0];
    }

    public static int getByteLength(NoteBytes[] noteBytesArray){
        int size = 0;
        for(int i = 0; i < noteBytesArray.length; i ++){
            NoteBytes noteBytes = noteBytesArray[i];
            size += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + noteBytes.byteLength());
        }
        return size;
    }

    public static NoteBytesArray create(NoteBytes[] noteBytesArray){
        return new NoteBytesArray(getBytesFromArray(noteBytesArray));
    }

    public Stream<NoteBytes> getAsStream(){
        return Arrays.stream(getAsArray());       
    }

    public NoteBytes[] getAsArray(){
        int size = size();
        NoteBytes[] arr = new NoteBytes[size];
        byte[] bytes = get();
        int length = bytes.length;
        int offset = 0;
        int i = 0;
        while(offset < length){
            NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
            arr[i] = noteBytes;
            i++;
            offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + noteBytes.byteLength());
        }
        return arr;
    }

    

    public List<NoteBytes> getAsList(){
        return Arrays.asList(getAsArray());        
    }

    public NoteBytes getAt(int index){
     
        byte[] bytes = get();
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
                return NoteBytes.of(dst, type);
            }
            offset += size;
            counter++;
        }
        return null;
        
    }


    public boolean contains(NoteBytes noteBytes){
        return indexOf(noteBytes) != -1;
    }

    public boolean arrayStartsWith(NoteBytes noteBytes){        
        byte[] src = get();
        byte[] dst = noteBytes.get();
        int srcLength = src.length;
        int dstLength = dst.length;

        if(srcLength > dstLength){
            return false;
        }
        if(srcLength == dstLength){
            return noteBytes.equals(this);
        }
        
        return Arrays.equals(src, 0, srcLength, dst, 0, dstLength);
    }
    
    public int indexOf(NoteBytes noteBytes){
        byte[] bytes = get();
        int length = bytes.length;
        int offset = 0;
        int counter = 0;
        while(offset < length){
            offset++;
            int size = ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += 4;
            byte[] buffer = new byte[size];
            System.arraycopy(bytes, offset, buffer, 0, size);
            if(Arrays.equals(noteBytes.get(), buffer)){
                return counter;
            }
            offset += size;
            counter++;
        }
        return -1;
    }
   

    public void add(NoteBytes noteBytes){
       
        byte[] bytes = get();
        int length = bytes.length;
        byte[] dst = Arrays.copyOf(bytes, length + 5 + noteBytes.byteLength());
        writeNote(noteBytes, dst, length);
        set(dst);
        
    }

 

    public boolean add(int index, NoteBytes noteBytes) {
        if (noteBytes == null) {
            return false;
        }
      
        byte[] bytes = get();
        int length = bytes.length;
        
        if (index < 0 || (length > 0 && index >= size())) {
            return false;
        }
        // Pre-calculate required size to avoid buffer resizing
        int newSize = length + 5 + noteBytes.byteLength();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(newSize)) {
            int offset = 0;
            int currentIndex = 0;
            // Copy elements before index
            while (offset < length && currentIndex < index) {
                NoteBytes currentNote = NoteBytes.readNote(bytes, offset);
                writeNote(currentNote, outputStream);
                offset += 5 + currentNote.byteLength();
                currentIndex++;
            }
            // Insert new element at index
            writeNote(noteBytes, outputStream);
            // Copy remaining elements
            while (offset < length) {
                NoteBytes currentNote = NoteBytes.readNote(bytes, offset);
                writeNote(currentNote, outputStream);
                offset += 5 + currentNote.byteLength();
            }
            set(outputStream.toByteArray());
            return true;
        } catch (IOException e) {
            return false;
        }
       
    }
    
    public NoteBytes remove(NoteBytes noteBytes) {
        byte[] bytes = get();
        int length = bytes.length;
        byte[] removeBytes = noteBytes.get(); 
        int noteBytesLength = removeBytes.length;

        if (bytes == null || length == 0) {
            return null;
        }
        if(length < noteBytesLength){
            return null;
        }
        int dstLength =length - noteBytesLength;
        byte[] dst = new byte[dstLength];
      
        NoteBytes removedBytes = null;
        int offset = 0;
        int dstOffset = 0;
        while (offset < length) {
            byte type = bytes[offset];
            offset ++;

            int srcLength = ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += 4;

            byte[] src = new byte[srcLength];
            System.arraycopy(bytes, offset, src, 0, srcLength);
            offset += srcLength;

            if (removedBytes == null && Arrays.equals(src, noteBytes.get())) {
                removedBytes = new NoteBytes(src, type);
            } else if(dstLength >= dstOffset + 5 + srcLength) {
                dstOffset = NoteBytesMetaData.write(type, srcLength, dst, dstOffset);
                System.arraycopy(src, 0, dst, dstOffset, srcLength);
                dstOffset += srcLength;
            }else{
                return null;
            }
        }
        if(removedBytes != null){
            set(dst);
        }
        return removedBytes;
    }

    public NoteBytes remove(int noteBytesIndex) {
      
        byte[] bytes = get();
        int byteLength = bytes.length;
        if (bytes == null || byteLength == 0) {
            return null;
        }
        
        try (UnsynchronizedByteArrayOutputStream outputStream = new UnsynchronizedByteArrayOutputStream()) {
            int offset = 0;
            int currentIndex = 0;
            NoteBytes removedBytes = null;
            
            while (offset < byteLength) {
                // Read metadata (1 byte type + 4 bytes length)
                 if(offset + 5 > byteLength){
                    throw new IllegalStateException("Corrupt data detected");
                }
                byte type = bytes[offset];
                offset++;
               
                int size = ByteDecoding.bytesToIntBigEndian(bytes, offset);
                offset += 4;

                if (offset + size > byteLength) {
                    break;
                }
                if (currentIndex == noteBytesIndex) {
                    // Store the bytes being removed
                    byte[] contentBytes = new byte[size];
                    System.arraycopy(bytes, offset, contentBytes, 0, size);
                    removedBytes = new NoteBytes(contentBytes, type);
                } else {
                    // Write metadata
                    outputStream.write(type);
                    outputStream.write(ByteDecoding.intToBytesBigEndian(size));
                    // Write content
                    outputStream.write(bytes, offset, size);
                }
                
                offset += 5 + size; // Move to next entry (5 bytes metadata + content)
                currentIndex++;
            }

            if(removedBytes != null){
                set(outputStream.toByteArray());
            }
            return removedBytes;
            
        } catch (IOException e) {
            return null;
        }
            
       
    }

    public int size(){
        byte[] bytes = get();
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

      @Override
     public JsonElement getAsJsonElement(){
        return getAsJsonArray();
    }
    
    @Override
    public JsonArray getAsJsonArray(){
        byte[] bytes = get();
        if(bytes != null){
            if(bytes.length == 0){
                return new JsonArray();
            }
            JsonArray jsonArray = new JsonArray();
            int length = bytes.length;
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
        byte[] bytes = get();
        if(bytes != null){
            if(bytes.length == 0){
                return new JsonObject();
            }
            JsonObject jsonObject = new JsonObject();
            int length = bytes.length;
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
