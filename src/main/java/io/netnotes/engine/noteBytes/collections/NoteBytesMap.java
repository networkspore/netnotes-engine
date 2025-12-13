package io.netnotes.engine.noteBytes.collections;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteString;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;

public class NoteBytesMap{
    private HashMap<NoteBytes, NoteBytes> m_pairs = null;


    public NoteBytesMap(byte[] bytes) {
        init(bytes);
    }

    public NoteBytesMap(){
        m_pairs = new HashMap<>();
    }

    public NoteBytesMap(NoteBytesObject noteBytes){
        init(noteBytes.get());
    }

    public NoteBytesMap(NoteBytesPair[] pairs){
        m_pairs = new HashMap<>();
        for(NoteBytesPair pair : pairs){
            m_pairs.put(pair.getKey(), pair.getValue());
        }
    }

    public NoteBytesMap(Stream<NoteBytesPair> stream){
        init(stream);
    }

    public NoteBytesMap(NoteBytesReader reader, int length) throws IOException{
        init(reader, length);
    }

    public NoteBytesMap(NoteBytesReader reader) throws IOException{
        init(reader);
    }

    public static HashMap<NoteBytes, NoteBytes> getHashMap(Stream<NoteBytesPair> stream){
        return stream.collect(Collectors.toMap(NoteBytesPair::getKey, NoteBytesPair::getValue,(existing, replacement) -> existing, HashMap::new));
    }

    public static HashMap<NoteBytes, NoteBytes> getHashMap(NoteBytesReader reader) throws IOException{
         // Parse plugin entries
 
        HashMap<NoteBytes, NoteBytes> map = new HashMap<>();
        NoteBytes key = reader.nextNoteBytes();
        NoteBytes value = reader.nextNoteBytes();

        while (key != null || value != null) {
          
            map.put(key, value);
            
            key = reader.nextNoteBytes();
            value = reader.nextNoteBytes();
        }
        return map;
    }

    public static HashMap<NoteBytes, NoteBytes> getHashMap(NoteBytesReader reader, int length) throws IOException{
         // Parse plugin entries
        int bytesRemaining = length;
        HashMap<NoteBytes, NoteBytes> map = new HashMap<>();
        while (bytesRemaining > 0) {
            NoteBytes key = reader.nextNoteBytes();
            NoteBytes value = reader.nextNoteBytes();
            
            if (key == null || value == null) {
                throw new IOException("Unexpected end of stream");
            }
            
            map.put(key, value);
            
            bytesRemaining -= (key.byteLength() + value.byteLength() + 
                                (NoteBytesMetaData.STANDARD_META_DATA_SIZE * 2));
        }
        return map;
    }

    public static HashMap<NoteBytes, NoteBytes> getHashMap(byte[] bytes) {
        int length = bytes.length;
        HashMap<NoteBytes, NoteBytes> map = new HashMap<>();
        if(length > 0){
            int offset = 0;
            while(offset < length) {
                NoteBytes key = NoteBytes.readNote(bytes, offset);
                offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + key.byteLength()); 
                NoteBytes value = NoteBytes.readNote(bytes, offset);
                offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + value.byteLength());
                map.put(key, value);
            }
           
        }
        return map;
    }
    public void init(NoteBytesReader reader) throws IOException{
        if(m_pairs != null){
            clear();
        }
        m_pairs = getHashMap(reader);
    }
    public void init(NoteBytesReader reader, int length) throws IOException{
        if(m_pairs != null){
            clear();
        }
        m_pairs = getHashMap(reader, length);
    }
    public void init(byte[] bytes){
        if(m_pairs != null){
            clear();
        }
        m_pairs = getHashMap(bytes);
    }

    public void init(Stream<NoteBytesPair> stream){
        if(m_pairs != null){
            clear();
        }
        m_pairs = getHashMap(stream);
    }

    public int rawByteLength(){
        int length = 0; 
        for(Map.Entry<NoteBytes, NoteBytes> entry : m_pairs.entrySet()) {
            length += (entry.getKey().byteLength() + entry.getValue().byteLength() );
        }
        return length;
    }

    public int byteLength_w_MetaData(){
        int length = 0; 
        for(Map.Entry<NoteBytes, NoteBytes> entry : m_pairs.entrySet()) {
            length += (entry.getKey().byteLength() + entry.getValue().byteLength()  + (NoteBytesMetaData.STANDARD_META_DATA_SIZE *2));
        }
        return length;
    }



    public NoteBytesObject toNoteBytes() {

        byte[] bytes = new byte[byteLength_w_MetaData()];
        int offset = 0;
        for(Map.Entry<NoteBytes, NoteBytes> entry : m_pairs.entrySet()) {
            offset = NoteBytes.writeNote(entry.getKey(), bytes, offset);
            offset = NoteBytes.writeNote(entry.getValue(), bytes, offset);
        }
        return new NoteBytesObject(bytes);
    }

    public NoteBytesReadOnly toNoteBytesReadOnly() {

        byte[] bytes = new byte[byteLength_w_MetaData()];
        int offset = 0;
        for(Map.Entry<NoteBytes, NoteBytes> entry : m_pairs.entrySet()) {
            offset = NoteBytes.writeNote(entry.getKey(), bytes, offset);
            offset = NoteBytes.writeNote(entry.getValue(), bytes, offset);
        }
        return new NoteBytesReadOnly(bytes, NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE);
    }



    public NoteBytes get(NoteBytes key) {
        return m_pairs.get(key);
    }

    public NoteBytesReadOnly getReadOnly(NoteBytes key) {
        NoteBytes value = m_pairs.get(key);

        return value != null ? value.readOnly() : null;
    }

    public NoteBytesReadOnly getReadOnly(String key) {
        NoteBytes value = m_pairs.get(key);

        return value != null ? value.readOnly() : null;
    }

    public NoteBytesPair getFirst(){
        return getAtIndex(0);
    }

    public NoteBytesPair getAtIndex(int index){
        int i = 0;
        for(Map.Entry<NoteBytes, NoteBytes> entry : m_pairs.entrySet()) {
            if(i == index){
                return new NoteBytesPair(entry.getKey(), entry.getValue());
            }
            i++;
        }
        return null;
    }


    public boolean isEmpty() {
        return m_pairs.size() == 0;
    }

    public void add(NoteBytesPair pair) {
        m_pairs.put(pair.getKey(), pair.getValue());
    }

    public void addAll(Map<? extends NoteBytes, ? extends NoteBytes> m) {
        for(Map.Entry<? extends NoteBytes, ? extends NoteBytes> pair : m.entrySet()){
            m_pairs.put(pair.getKey(), pair.getValue());
        }
    }

    public NoteBytes remove(String keyString) {
        return remove(new NoteBytes(keyString));
    }

    public NoteBytes remove(NoteBytes key) {
        return m_pairs.remove(key);
    }

    

    public NoteBytesPair removeAt(int index) {
        int i = 0;
        NoteBytesPair value = null;
        for(Map.Entry<NoteBytes, NoteBytes> entry : m_pairs.entrySet()) {
            value = index == i ? new NoteBytesPair(entry.getKey(), entry.getValue()) : value;
            if(value != null){
                m_pairs.remove(entry.getKey());
                break;
            }    
            i++;
        }
        return value;
    }

    public int size() {
        return m_pairs.size();
    }
    

 

    
    public void clear() {
        m_pairs.clear();
    }

    
    public boolean containsKey(NoteBytes key) {
        return m_pairs.containsKey(key);
    }

    public boolean has(String key) {
        return m_pairs.containsKey(new NoteString(key));
    }

    
    public boolean containsValue(NoteBytes value) {
        return m_pairs.containsValue(value);
    }

    
    public Set<Entry<NoteBytes, NoteBytes>> entrySet() {
        return m_pairs.entrySet();
    }

    
    public NoteBytes get(String key) {
        return m_pairs.get(new NoteBytes(key));
    }

    public NoteBytes getOrDefault(NoteBytes key, NoteBytes defaultValue){
        NoteBytes keyBytes = m_pairs.get(key);
        return keyBytes != null ? keyBytes : defaultValue;
    }



    public NoteBytes getByString(String key) {
        return m_pairs.get(new NoteString(key));
    }

    
    public Set<NoteBytes> keySet() {
        return m_pairs.keySet();
    }

    public void put(NoteBytes key, ContextPath contextPath){
        m_pairs.put(key, contextPath.getSegments());
    }

    public void put(NoteBytes key, NoteBytesMap map){
        m_pairs.put(key, map.toNoteBytes());
    }

    public void put(NoteBytes key, boolean b){
        m_pairs.put(key, new NoteBytes(b));
    }

    public void put(String key, NoteBytesMap map){
        m_pairs.put(new NoteBytes( key), map.toNoteBytes());
    }

    public void put(String key, long l){
        m_pairs.put(new NoteBytes( key), new NoteBytes(l));
    }

    public void put(NoteBytes key, long l){
        m_pairs.put(key, new NoteBytes(l));
    }

    public void put(String key, int i){
        m_pairs.put(new NoteBytes( key), new NoteBytes(i));
    }

    public void put(NoteBytes key, int i){
        m_pairs.put( key, new NoteBytes(i));
    }

    public void put(String key, boolean b){
        m_pairs.put(new NoteBytes( key), new NoteBytes(b));
    }

    public void put(String key, NoteBytes[] n){
        m_pairs.put(new NoteBytes( key), new NoteBytesArrayReadOnly(n));
    }

    public void put(NoteBytes key, NoteBytes value) {
        m_pairs.put(key, value);
    }

    public void put(String key, String value) {
        m_pairs.put(new NoteString(key), new NoteString(value));
    }

    public void put(NoteBytes key, String value) {
        m_pairs.put(key, new NoteString(value));
    }

     public void put(String key, NoteBytes value) {
        m_pairs.put(new NoteString(key), value);
    }

    public NoteBytes putIfAbsent(Object key, Object value) {
        return m_pairs.putIfAbsent(NoteBytes.of(key), NoteBytes.of(value));
    }

    public NoteBytes computeIfPresent(NoteBytes key,
            BiFunction<? super NoteBytes, ? super NoteBytes, ? extends NoteBytes> remappingFunction) {
        return m_pairs.computeIfPresent(key, remappingFunction);
    }

    public NoteBytes computeIfAbsent(NoteBytes key,
            java.util.function.Function<? super NoteBytes, ? extends NoteBytes> mappingFunction) {
        return m_pairs.computeIfAbsent(key, mappingFunction);
    }

    public NoteBytes compute(NoteBytes key,
            BiFunction<? super NoteBytes, ? super NoteBytes, ? extends NoteBytes> remappingFunction) {
        return m_pairs.compute(key, remappingFunction);
    }

    public NoteBytes merge(NoteBytes key, NoteBytes value,
            BiFunction<? super NoteBytes, ? super NoteBytes, ? extends NoteBytes> remappingFunction) {
        return m_pairs.merge(key, value, remappingFunction);
    }

    public void putAll(Map<? extends NoteBytes, ? extends NoteBytes> m) {
        m_pairs.putAll(m);
    }

    public void forEach(BiConsumer<? super NoteBytes,? super NoteBytes> biConsumer){
        m_pairs.forEach(biConsumer);
    }
    

    public boolean has(NoteBytes noteBytes){
        return m_pairs.containsKey(noteBytes);
    }

    
    public Collection<NoteBytes> values() {
        return m_pairs.values();
    }

    public Map<NoteBytes, NoteBytes> getAsMap(){
        return m_pairs;
    }

  
}
