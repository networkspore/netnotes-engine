package io.netnotes.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger; 
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class NoteBytesObject extends NoteBytes{

    public NoteBytesObject(){
        super(new byte[0], ByteDecoding.NOTE_BYTE_PAIR);
    }

    public NoteBytesObject(byte[] bytes){
        super(bytes, ByteDecoding.NOTE_BYTE_PAIR);        
    }

    public NoteBytesObject(NoteBytesPair[] pairs){
        this(noteBytePairsToByteArray(pairs));
    }

    public NoteBytesObject(InputStream inputStream) throws IOException{
        this(Utils.readInputStreamAsBytes(inputStream));
    }

    public static byte[] noteBytePairsToByteArray(NoteBytesPair[] pairs){
        byte[] bytes = new byte[0];
        int offset = 0;
        for(NoteBytesPair pair : pairs){
            int length = bytes.length;
            byte[] dstBytes = Arrays.copyOf(bytes, length + 8 + pair.getKey().byteLength() + pair.getValue().byteLength());
            offset = NoteBytes.writeNote(pair.getKey(), dstBytes, offset);
            offset = NoteBytes.writeNote(pair.getValue(), dstBytes, offset);
        }
        return bytes;
    }

    public Stream<NoteBytesPair> getAsStream() {
        return getAsStream(get(), getByteDecoding());
    }

    public static Stream<NoteBytesPair> getAsStream(byte[] bytes, ByteDecoding byteDecoding){
        Stream.Builder<NoteBytesPair> noteBytesBuilder = Stream.builder();
        int length = bytes.length;
        int offset = 0;
        NoteBytesPair pair;
        while(offset < length){
            pair = NoteBytesPair.read(bytes, offset, byteDecoding);
            noteBytesBuilder.accept(pair);
            offset += 8 + pair.getKey().byteLength() + pair.getValue().byteLength();
        }
        return noteBytesBuilder.build();
    }


    public NoteBytesPair[] getAsArray(){
        return getAsStream().toArray(NoteBytesPair[]::new);
    }


    public Map<NoteBytes, NoteBytes> getAsMap(){
        return getAsStream().collect(Collectors.toMap(NoteBytesPair::getKey, NoteBytesPair::getValue));
    }

    public void clear() {
        byte[] bytes = get();
        for(int i = 0; i < bytes.length; i++){
            bytes[i] = (byte) 0;
        }
        set(new byte[0]);
    }

    public NoteBytesPair get(String keyString){
        return get(new NoteBytes(keyString));
    }

    public NoteBytesPair get(NoteBytes key){

        byte[] bytes = get();
        int length = bytes.length;
        
        if(bytes != null && length > 0){
            int offset = 0;
            ByteDecoding byteDecoding = getByteDecoding();
            while(offset < length){
                NoteBytesPair pair = NoteBytesPair.read(bytes, offset, byteDecoding);
                if(pair.getKey().equals(key)){
                    return pair;
                }
                offset += 8 + pair.getKey().byteLength() + pair.getValue().byteLength();
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
            ByteDecoding byteDecoding = getByteDecoding();
            while(offset < length){
                NoteBytesPair pair = NoteBytesPair.read(bytes, offset, byteDecoding);
                if(i == index){
                    return pair;
                }
                offset += 8 + pair.getKey().byteLength() + pair.getValue().byteLength();
                i++;
            }
        }
        return null;
    }


    public boolean isEmpty() {
        return get().length == 0;
    }

    public void add(NoteBytesPair pair){
        byte[] bytes = getBytes();
        int length = bytes.length;
        byte[] newBytes = Arrays.copyOf(bytes, length + 8 + pair.getKey().byteLength() + pair.getValue().byteLength());

        int offset = NoteBytes.writeNote(pair.getKey(), newBytes, length);
        NoteBytes.writeNote(pair.getValue(), newBytes, offset);

        set(newBytes);
    }

    public void add(NoteBytes key, NoteBytes value) {
        add(new NoteBytesPair(key, value));       
    }

    public void add(String key, NoteBytes value) {
        add(new NoteBytes(key), value);
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

    public void add(String key, NoteBytesObject value) {
        add(key, (NoteBytes) value);
    }

    public void addAll(Map<? extends NoteBytes, ? extends NoteBytes> m) {
        for(Map.Entry<? extends NoteBytes, ? extends NoteBytes> entry : m.entrySet()){
            add(entry.getKey(), entry.getValue());
        }
    }

    public NoteBytesPair remove(String keyString) {
        return remove(new NoteBytes(keyString));
    }

    public NoteBytesPair remove(NoteBytes key) {
        byte[] bytes = get();
        int length = bytes.length;
        if(bytes != null && length > 0){
    
            int offset = 0;
            int dstOffset = 0;
            NoteBytesPair removedPair = null;
            byte[] dstBytes = new byte[0];
            while(offset < length){
                byte[] keyIntBytes = new byte[4];
                for(int i =0 ; i < 4 ; i++){
                    keyIntBytes[i] = bytes[offset];
                    offset ++;
                }
                int keySize = ByteDecoding.bytesToIntBigEndian(keyIntBytes);
                byte[] keyBytes = new byte[keySize];
                for(int i = 0; i < keySize && offset < length; i++){
                    keyBytes[i] = bytes[offset];
                    offset++;
                }

                //
                byte[] valueIntBytes = new byte[4];
                for(int i = 0 ; i < 4 ; i++){
                    valueIntBytes[i] = bytes[offset];
                    offset ++;
                }
                int valueSize = ByteDecoding.bytesToIntBigEndian(valueIntBytes);
                byte[] valueBytes = new byte[valueSize];
                for(int i = 0; i < valueSize && offset < length; i++){
                    valueBytes[i] = bytes[offset];
                    offset++;
                }

                NoteBytes keyNoteBytes = removedPair == null ? new NoteBytes(keyBytes) : null;
                boolean isKey = keyNoteBytes != null && keyNoteBytes.equals(key);
                removedPair = isKey ? new NoteBytesPair(keyBytes, valueBytes) : removedPair;

                dstBytes  = !isKey ? Arrays.copyOf(dstBytes, dstBytes.length + 8 + keyBytes.length + valueBytes.length) : dstBytes;
                dstOffset = !isKey ? Utils.arrayCopy(keyIntBytes,  0, dstBytes, dstOffset,        4) : dstOffset;
                dstOffset = !isKey ? Utils.arrayCopy(keyBytes,     0, dstBytes, dstOffset, keyBytes.length) : dstOffset;
                dstOffset = !isKey ? Utils.arrayCopy(valueIntBytes,0, dstBytes, dstOffset,        4) : dstOffset;
                dstOffset = !isKey ? Utils.arrayCopy(valueBytes,   0, dstBytes, dstOffset, valueBytes.length) : dstOffset;

            }

            set(dstBytes);
            return removedPair;
           
        }

        return null;
        
    }

    

    public NoteBytesPair removeAt(int pairIndex) {
        byte[] bytes = get();
        int length = bytes.length;
        if(bytes != null && length > 0){

                int offset = 0;
                int index = 0;
                NoteBytesPair removedPair = null;
                byte[] dstBytes = new byte[0];
                int dstOffset = 0;
                while(offset < length){
                    byte[] keyIntBytes = new byte[4];
                    for(int i =0 ; i < 4 ; i++){
                        keyIntBytes[i] = bytes[offset];
                        offset ++;
                    }
                    int keySize = ByteDecoding.bytesToIntBigEndian(keyIntBytes);
                    byte[] keyBytes = new byte[keySize];
                    for(int i = 0; i < keySize && offset < length; i++){
                        keyBytes[i] = bytes[offset];
                        offset++;
                    }
                    //
                    byte[] valueIntBytes = new byte[4];
                    for(int i = 0 ; i < 4 ; i++){
                        valueIntBytes[i] = bytes[offset];
                        offset ++;
                    }
                    int valueSize = ByteDecoding.bytesToIntBigEndian(valueIntBytes);
                    byte[] valueBytes = new byte[valueSize];
                    for(int i = 0; i < valueSize && offset < length; i++){
                        valueBytes[i] = bytes[offset];
                        offset++;
                    }
                    boolean isKey = removedPair == null && pairIndex == index;
                    removedPair = isKey ? new NoteBytesPair(keyBytes, valueBytes) : removedPair;
                    
                    dstBytes  = !isKey ? Arrays.copyOf(dstBytes, dstBytes.length + 8 + keyBytes.length + valueBytes.length) : dstBytes;
                    dstOffset = !isKey ? Utils.arrayCopy(keyIntBytes,  0, dstBytes, dstOffset,        4) : dstOffset;
                    dstOffset = !isKey ? Utils.arrayCopy(keyBytes,     0, dstBytes, dstOffset, keyBytes.length) : dstOffset;
                    dstOffset = !isKey ? Utils.arrayCopy(valueIntBytes,0, dstBytes, dstOffset,        4) : dstOffset;
                    dstOffset = !isKey ? Utils.arrayCopy(valueBytes,   0, dstBytes, dstOffset, valueBytes.length) : dstOffset;
                    index ++;
                }

                set(dstBytes);
                return removedPair;
           
        }

        return null;
        
    }
    public int size(){
        byte[] bytes = get();
        int length = bytes.length;
        int offset = 0;
        int index = 0;
        while(offset < length){
            int size  = ByteDecoding.bytesToIntBigEndian(bytes, offset);
            offset += size + 4;
            index++;
        }
        return index/2;
    }

    public static int writePairs( ByteArrayOutputStream outputStream, NoteBytesPair[] pairs) throws IOException{
        int len = 0;
        for(NoteBytesPair pair : pairs){
            len += NoteBytesPair.writeNoteBytePair(pair, outputStream);
        }
        return len;
    }
}
