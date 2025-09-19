package io.netnotes.engine.noteBytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.utils.Utils;

public class NoteBytesArrayAsync extends NoteBytesAsync{

    public NoteBytesArrayAsync(){
        super(new byte[0], ByteDecoding.NOTE_BYTES_ARRAY);
    }

    public NoteBytesArrayAsync(byte[] bytes){
        super(bytes, ByteDecoding.NOTE_BYTES_ARRAY);
    }

    public NoteBytesArrayAsync(NoteBytes[] noteBytes){
        this(getBytesFromArray(noteBytes));
    }

    public NoteBytesArrayAsync(byte[] bytes, ByteDecoding byteDecoding){
        super(bytes,byteDecoding);
    }
    
    public static byte[] getBytesFromArray(NoteBytes[] noteBytesArray){
        int length = noteBytesArray != null && noteBytesArray.length > 0 ? noteBytesArray.length : 0;
        if(length > 0){
            byte[] dstBytes = new byte[getByteLength(noteBytesArray)];
            int dstOffset = 0;
            for(int i = 0; i < noteBytesArray.length; i ++){
                NoteBytes noteBytes = noteBytesArray[i];
                byte type = noteBytes.getByteDecoding().getType();
                byte[] intBytes = ByteDecoding.intToBytesBigEndian(noteBytes.byteLength());
                byte[] buffer = noteBytes.get();                
                dstBytes[dstOffset++] = type;
                dstOffset = Utils.arrayCopy(intBytes, 0, dstBytes, dstOffset, 4);
                dstOffset = Utils.arrayCopy(buffer, 0, dstBytes, dstOffset, buffer.length);
            }
            return dstBytes;
        }
        return new byte[0];
    }

    public static int getByteLength(NoteBytes[] noteBytesArray){
        int size = 0;
        for(int i = 0; i < noteBytesArray.length; i ++){
            NoteBytes noteBytes = noteBytesArray[i];
            size += (5 + noteBytes.byteLength());
        }
        return size;
    }

    public static NoteBytesArray create(NoteBytes[] noteBytesArray){
        return new NoteBytesArray(getBytesFromArray(noteBytesArray));
    }

    public Stream<NoteBytes> getAsStream(){
        try {
            acquireLock();
            try{
                byte[] bytes = get();
                Stream.Builder<NoteBytes> noteBytesBuilder = Stream.builder();
                int length = bytes.length;
                int offset = 0;
                
                while(offset < length){
                    NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
                    noteBytesBuilder.accept(noteBytes);
                    offset += (5 + noteBytes.byteLength());
                }
                return noteBytesBuilder.build();
            }finally{
                releaseLock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Stream.empty();
        } 
    }

    public NoteBytes[] getAsArray(){
        return getAsStream().toArray(NoteBytes[]::new);
    }

    

    public List<NoteBytes> getAsList(){
        return getAsStream().toList();
    }

    public NoteBytes getAt(int index){
        try {
            acquireLock();
            try{
                byte[] bytes = get();
                int length = bytes.length;
                int offset = 0;
                int counter = 0;
                boolean isLittleEndian = getByteDecoding().isLittleEndian();
                while(offset < length){
                    byte type = bytes[offset];
                    offset++;
                    int size = isLittleEndian ? ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
                    offset += 4;
                    if(counter == index){
                        byte[] dst = new byte[size];
                        System.arraycopy(bytes, offset, dst, 0, size);
                        return new NoteBytes(dst, ByteDecoding.of(type));
                    }
                    offset += size;
                    counter++;
                }
                return null;
            }finally {
                releaseLock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } 
    }


    public boolean contains(NoteBytes noteBytes){
        return indexOf(noteBytes) != -1;
    }

    
    public int indexOf(NoteBytes noteBytes){
        try {
            acquireLock();
            try {
                byte[] bytes = get();
                int length = bytes.length;
                int offset = 0;
                int counter = 0;
                boolean isLittleEndian = getByteDecoding().isLittleEndian();
                while(offset < length){
                    offset++;
                    int size = isLittleEndian ?  ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
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
            } finally {
                releaseLock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

   

    public void add(NoteBytes noteBytes){
        try {
            acquireLock();
            try{
                byte[] bytes = get();
                int length = bytes.length;
                byte[] dst = Arrays.copyOf(bytes, length + 5 + noteBytes.byteLength());
                writeNote(noteBytes, dst, length);
                set(dst);
            }finally {
                releaseLock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } 
    }

 

    public boolean add(int index, NoteBytes noteBytes) {
        if (noteBytes == null) {
            return false;
        }
        try {
            acquireLock();
            try{
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
            }finally {
                releaseLock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } 
    }
    
    public NoteBytes remove(NoteBytes noteBytes) {
        try {
            acquireLock();
            try{
                byte[] bytes = get();
                int length = bytes.length;
                
                if (bytes == null || length == 0) {
                    return null;
                }
                
                try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream(length);){
                    NoteBytes removedBytes = null;
                    int offset = 0;
                    
                    while (offset < length) {
                        byte type = bytes[offset];
                        int size = getByteDecoding().isLittleEndian() ? ByteDecoding.bytesToIntLittleEndian(bytes, offset + 1) : ByteDecoding.bytesToIntBigEndian(bytes, offset + 1);
                        
                        byte[] contentBytes = new byte[size];
                        System.arraycopy(bytes, offset + 5, contentBytes, 0, size);
                        
                        if (removedBytes == null && Arrays.equals(contentBytes, noteBytes.get())) {
                            removedBytes = new NoteBytes(contentBytes, ByteDecoding.of(type));
                        } else {
                            outputStream.write(type);
                            outputStream.write(getByteDecoding().isLittleEndian() ? ByteDecoding.intToBytesLittleEndian(size) : ByteDecoding.intToBytesBigEndian(size));
                            outputStream.write(contentBytes);
                        }
                        
                        offset += 5 + size;
                    }
                    
                    set(outputStream.toByteArray());
                    return removedBytes;
                } catch(IOException e){
                    return null;
                }
            }finally {
                releaseLock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } 
    }

    public NoteBytes remove(int noteBytesIndex) {
        try {
            acquireLock();
            try{
                byte[] bytes = get();
                if (bytes == null || bytes.length == 0) {
                    return null;
                }
                
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    int offset = 0;
                    int currentIndex = 0;
                    NoteBytes removedBytes = null;
                    
                    while (offset < bytes.length) {
                        // Read metadata (1 byte type + 4 bytes length)
                        byte type = bytes[offset];
                        int size = getByteDecoding().isLittleEndian() ? 
                            ByteDecoding.bytesToIntLittleEndian(bytes, offset + 1) : 
                            ByteDecoding.bytesToIntBigEndian(bytes, offset + 1);
                        
                        // Validate size to prevent buffer overrun
                        if (offset + 5 + size > bytes.length) {
                            break;
                        }
                        
                        if (currentIndex == noteBytesIndex) {
                            // Store the bytes being removed
                            byte[] contentBytes = new byte[size];
                            System.arraycopy(bytes, offset + 5, contentBytes, 0, size);
                            removedBytes = new NoteBytes(contentBytes, ByteDecoding.of(type));
                        } else {
                            // Write metadata
                            outputStream.write(type);
                            outputStream.write(getByteDecoding().isLittleEndian() ? 
                                ByteDecoding.intToBytesLittleEndian(size) : 
                                ByteDecoding.intToBytesBigEndian(size));
                            // Write content
                            outputStream.write(bytes, offset + 5, size);
                        }
                        
                        offset += 5 + size; // Move to next entry (5 bytes metadata + content)
                        currentIndex++;
                    }
                    
                    set(outputStream.toByteArray());
                    return removedBytes;
                    
                } catch (IOException e) {
                    return null;
                }
            }finally {
                releaseLock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public int size(){
        try {
            acquireLock();
            try{
                byte[] bytes = get();
                if(bytes == null){
                    return -1;
                }else if(bytes.length == 0){
                    return 0;
                }else{
                    int length = bytes.length;
                    int offset = 0;
                    int counter = 0;
                    boolean isLittleEndian = getByteDecoding().isLittleEndian();
                    while(offset < length){
                        offset++;
                        int size = isLittleEndian ? ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
                        offset += 4 + size;
                        counter++;
                    }
                    return counter;
                }
            }finally {
                releaseLock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
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
                byte type = noteBytes.getByteDecoding().getType();
                
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
                byte type = noteBytes.getByteDecoding().getType();
                
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

    @Override
    public void clear(){

    }
}
