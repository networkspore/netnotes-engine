package io.netnotes.engine.noteBytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;



public class NoteBytesObject extends NoteBytes{

    public NoteBytesObject(){
        super(new byte[0], NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE);
    }

    public NoteBytesObject(byte[] bytes){
        super(bytes, NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE);        
    }

    public NoteBytesObject(NoteBytesPair[] pairs){
        this(noteBytePairsToByteArray(pairs));
    }

    public NoteBytesObject(NoteBytesPair pair){
        this(pair.get());
    }



    public static byte[] noteBytePairsToByteArray(NoteBytesPair[] pairs) {
        int byteLength = 0;
        for(NoteBytesPair pair : pairs){
            byteLength += pair.byteLength();
        }
        byte[] bytes = new byte[byteLength];
        int offset = 0;
        for(NoteBytesPair pair : pairs) {
            offset = NoteBytesPair.write(pair, bytes, offset);
        }
        return bytes;
    }

    public Stream<NoteBytesPair> getAsStream() {
        byte[] bytes = get();
        Stream.Builder<NoteBytesPair> noteBytesBuilder = Stream.builder();
        int length = bytes.length;
        int offset = 0;
    
        while(offset < length) {
            NoteBytesPair pair = NoteBytesPair.read(bytes, offset);
            noteBytesBuilder.accept(pair);
            offset += 10 + pair.getKey().byteLength() + pair.getValue().byteLength(); // 2 bytes type + 8 bytes length
        }
        return noteBytesBuilder.build();
       
    }

    public NoteBytesPair[] getAsArray(){
        return getAsList().toArray(new NoteBytesPair[0]);
    }

    public ArrayList<NoteBytesPair> getAsList(){
        ArrayList<NoteBytesPair> pairs = new ArrayList<>();
        byte[] bytes = get();
      
        int length = bytes.length;
        int offset = 0;
    
        while(offset < length) {
            NoteBytesPair pair = NoteBytesPair.read(bytes, offset);
            pairs.add(pair);
            offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE *2) + pair.getKey().byteLength() + pair.getValue().byteLength(); // 2 bytes type + 8 bytes length
        }

        return pairs;
    }

    public List<NoteBytesPair> getKeyValuePairs(){
        return getAsList();
    }


    public Map<NoteBytes, NoteBytes> getAsMap(){
        return getAsStream().collect(Collectors.toMap(NoteBytesPair::getKey, NoteBytesPair::getValue));
    }



    public NoteBytesPair get(String keyString){
        return get(new NoteBytes(keyString));
    }

    public boolean contains(NoteBytes key){
        byte[] bytes = get();
        int length = bytes.length;
        
        if(bytes != null && length > 0) {
            int offset = 0;
            while(offset < length) {
                NoteBytesPair pair = NoteBytesPair.read(bytes, offset);
                if(pair.getKey().equals(key)) {
                    return true;
                }
                offset += 10 + pair.getKey().byteLength() + pair.getValue().byteLength();
            }
        }
        return false;
    }
    public NoteBytesPair get(NoteBytes key) {
      
        byte[] bytes = get();
        int length = bytes.length;
        
        if(bytes != null && length > 0) {
            int offset = 0;
            while(offset < length) {
                NoteBytesPair pair = NoteBytesPair.read(bytes, offset);
                if(pair.getKey().equals(key)) {
                    return pair;
                }
                offset += 10 + pair.getKey().byteLength() + pair.getValue().byteLength();
            }
        }
        return null;
    
    }



    public NoteBytesPair getFirst(){
        return getAtIndex(0);
    }

    public NoteBytesPair getAtIndex(int index){
      
        byte[] bytes = get();
        int length = bytes.length;
        if(bytes != null && length > 0){
            int offset = 0;
            int i = 0;
            while(offset < length){
                NoteBytesPair pair = NoteBytesPair.read(bytes, offset);
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
        return get().length == 0;
    }

    public void add(NoteBytesPair pair) {

        byte[] bytes = getBytes();
        int length = bytes.length;
        byte[] newBytes = Arrays.copyOf(bytes, length + pair.byteLength());
        int offset = NoteBytes.writeNote(pair.getKey(), newBytes, length);
        NoteBytes.writeNote(pair.getValue(), newBytes, offset);
        set(newBytes);

    }

     public void add(NoteBytesPair[] pairs) {
        int pairsLength = 0;
        for(NoteBytesPair pair: pairs){
            pairsLength += pair.byteLength();
        }
        byte[] bytes = getBytes();
        int length = bytes.length;

        byte[] newBytes = Arrays.copyOf(bytes, length + pairsLength);
        int offset = length;
        for(NoteBytesPair pair : pairs){
            offset = NoteBytes.writeNote(pair.getKey(), newBytes, length);
            offset = NoteBytes.writeNote(pair.getValue(), newBytes, offset);
        }
        set(newBytes);

    }

    public void add(NoteBytes key, Object value){
        add(new NoteBytesPair(key, value));
    }

    public void add(String key, Object value){
        add(new NoteBytesPair(key, value));
    }

    public void add(NoteBytes key, NoteBytes value) {
        add(new NoteBytesPair(key, value));       
    }

     public void add(NoteBytes key, long value) {
        add(new NoteBytesPair(key, new NoteLong(value)));       
    }

    public void add(String key, NoteBytes value) {
        add(new NoteBytes(key), value);
    }

    public void add(String key, BigInteger value) {
        add(new NoteBytes(key), new NoteBigInteger(value));
    }

    public void add(String key, BigDecimal value) {
        add(new NoteBytes(key), new NoteBigDecimal(value));
    }

    public void add(String key, String value) {
        add(new NoteBytes(key), new NoteBytes(value));
    }

    public void add(String key, double value) {
        add(new NoteBytes(key), new NoteDouble(value));
    }

    public void add(String key, long value) {
        add(new NoteBytes(key), new NoteLong(value));
    }

    public void add(String key, boolean value) {
        add(new NoteBytes(key), new NoteBoolean(value));
    }

    public void add(String key, int value) {
        add(new NoteBytes(key), new NoteInteger(value));
    }

    public void add(String key, byte[] value) {
        add(new NoteBytes(key), new NoteBytes(value));
    }

    public void add(char[] key, byte[] value) {
        add(new NoteBytes(key), new NoteBytes(value));
    }

    public void add(char[] key, BigInteger value) {
        add(new NoteBytes(key), new NoteBigInteger(value));
    }

    public void add(char[] key, BigDecimal value) {
        add(new NoteBytes(key), new NoteBigDecimal(value));
    }

   
 
    public void addAll(Map<? extends NoteBytes, ? extends NoteBytes> m) {
      
        for(Map.Entry<? extends NoteBytes, ? extends NoteBytes> pair : m.entrySet()){
            byte[] bytes = getBytes();
            int length = bytes.length;
            byte[] newBytes = Arrays.copyOf(bytes, length + 10 + pair.getKey().byteLength() + pair.getValue().byteLength());
            int offset = NoteBytes.writeNote(pair.getKey(), newBytes, length);
            NoteBytes.writeNote(pair.getValue(), newBytes, offset);
            set(newBytes);
        }
    
    }

    public NoteBytesPair remove(String keyString) {
        return remove(new NoteBytes(keyString));
    }

    public NoteBytesPair remove(NoteBytes key) {
  
        byte[] bytes = get();
        int length = bytes.length;
        if (bytes == null || length == 0) {
            return null;
        }
        
        try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream(length);) {
            
            NoteBytesPair removedPair = null;
            int offset = 0;
            
            while (offset < length) {
                byte type = bytes[offset];
                int keySize = ByteDecoding.bytesToIntBigEndian(bytes, offset + 1);
                
                byte[] keyBytes = new byte[keySize];
                System.arraycopy(bytes, offset + 5, keyBytes, 0, keySize);
                
                int valueOffset = offset + 5 + keySize;
                byte valueType = bytes[valueOffset];
                int valueSize = ByteDecoding.bytesToIntBigEndian(bytes, valueOffset + 1);
                
                byte[] valueBytes = new byte[valueSize];
                System.arraycopy(bytes, valueOffset + 5, valueBytes, 0, valueSize);
                
                NoteBytes currentKey = new NoteBytes(keyBytes);
                if (removedPair == null && currentKey.equals(key)) {
                    removedPair = new NoteBytesPair(currentKey, new NoteBytes(valueBytes, valueType));
                } else {
                    outputStream.write(type);
                    outputStream.write(ByteDecoding.intToBytesBigEndian(keySize), 0, 4);
                    outputStream.write(keyBytes, 0, keySize);
                    
                    outputStream.write(valueType);
                    outputStream.write(ByteDecoding.intToBytesBigEndian(valueSize), 0, 4);
                    outputStream.write(valueBytes, 0, valueSize);
                }
                offset = valueOffset + 5 + valueSize;
            }
            
            set(outputStream.toByteArray());
            return removedPair;
        } catch (IOException e) {
            return null;
        }
    }

    

    public NoteBytesPair removeAt(int pairIndex) {
    
    
        byte[] bytes = get();
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(bytes.length)) {
            NoteBytesPair removedPair = null;
            int offset = 0;
            int currentIndex = 0;
            
            while (offset < bytes.length) {
                // Read key header (type + length)
                byte type = bytes[offset];
                int keySize = ByteDecoding.bytesToIntBigEndian(bytes, offset + 1);
                
                // Read key bytes
                byte[] keyBytes = new byte[keySize];
                System.arraycopy(bytes, offset + 5, keyBytes, 0, keySize);
                
                // Read value header
                int valueOffset = offset + 5 + keySize;
                byte valueType = bytes[valueOffset];
                int valueSize = ByteDecoding.bytesToIntBigEndian(bytes, valueOffset + 1);
                
                // Read value bytes
                byte[] valueBytes = new byte[valueSize];
                System.arraycopy(bytes, valueOffset + 5, valueBytes, 0, valueSize);
                
                // Check if this is the pair to remove
                if (currentIndex == pairIndex) {
                    removedPair = new NoteBytesPair(
                        new NoteBytes(keyBytes),
                        new NoteBytes(valueBytes, valueType)
                    );
                } else {
                    // Write key
                    outputStream.write(type);
                    outputStream.write(ByteDecoding.intToBytesBigEndian(keySize));
                    outputStream.write(keyBytes);
                    
                    // Write value
                    outputStream.write(valueType);
                    outputStream.write(ByteDecoding.intToBytesBigEndian(valueSize));
                    outputStream.write(valueBytes);
                }
                offset = valueOffset + 5 + valueSize;
                currentIndex++;
            }
            
            set(outputStream.toByteArray());
            return removedPair;
        }catch (IOException e) {
            return null;
        }
           
     
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
            NoteBytes noteBytes = NoteBytes.readNote(bytes, offset);
            offset += 5 + noteBytes.byteLength(); // 1 byte type + 4 bytes length + content
            counter++;
        }
        return counter;
    }

    @Override
    public JsonElement getAsJsonElement(){
        return getAsJsonObject();
    }
    
    @Override
    public JsonArray getAsJsonArray(){
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(getAsJsonObject());
        return jsonArray;
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

            while(offset < length){
                NoteBytesPair pair = NoteBytesPair.read(bytes, offset);
                NoteBytes value = pair.getValue();
                byte type = getType();
                
                if(type == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE){
                    jsonObject.add(pair.getKeyAsString(), value.getAsJsonArray());
                } else if(type == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
                    jsonObject.add(pair.getKeyAsString(), value.getAsJsonObject());
                } else {
                    jsonObject.add(pair.getKeyAsString(), value.getAsJsonElement());
                }
                offset += 10 + pair.getKey().byteLength() + pair.getValue().byteLength();
            }
            return jsonObject;
        }
        return null;
            
    }

    @Override 
    public String toString(){
        return getAsJsonObject().toString();
    }
    
}
