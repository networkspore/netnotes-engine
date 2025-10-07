package io.netnotes.engine.noteBytes.collections;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

public class NoteBytesConcurrentMap implements ConcurrentMap<NoteBytes, NoteBytes>{
    private ConcurrentHashMap<NoteBytes, NoteBytes> m_pairs = null;


    public NoteBytesConcurrentMap(byte[] bytes) {
        init(bytes);
    }

    public NoteBytesConcurrentMap(){
        m_pairs = new ConcurrentHashMap<>();
    }

    public NoteBytesConcurrentMap(NoteBytesObject noteBytes){
        init(noteBytes.get());
    }

    public NoteBytesConcurrentMap(NoteBytesPair[] pairs){
        m_pairs = new ConcurrentHashMap<>();
        for(NoteBytesPair pair : pairs){
            m_pairs.put(pair.getKey(), pair.getValue());
        }
    }

    public NoteBytesConcurrentMap(Stream<NoteBytesPair> stream){
        init(stream);
    }

    public NoteBytesConcurrentMap(NoteBytesReader reader, int length) throws IOException{
        init(reader, length);
    }

    public NoteBytesConcurrentMap(NoteBytesReader reader) throws IOException{
        init(reader);
    }

    public static ConcurrentHashMap<NoteBytes, NoteBytes> getHashMap(Stream<NoteBytesPair> stream){
        return stream.collect(Collectors.toMap(NoteBytesPair::getKey, NoteBytesPair::getValue,(existing, replacement) -> existing, ConcurrentHashMap::new));
    }

    public static ConcurrentHashMap<NoteBytes, NoteBytes> getHashMap(NoteBytesReader reader) throws IOException{
         // Parse plugin entries
 
        ConcurrentHashMap<NoteBytes, NoteBytes> map = new ConcurrentHashMap<>();
        NoteBytes key = reader.nextNoteBytes();
        NoteBytes value = reader.nextNoteBytes();

        while (key != null || value != null) {
          
            map.put(key, value);
            
            key = reader.nextNoteBytes();
            value = reader.nextNoteBytes();
        }
        return map;
    }

    public static ConcurrentHashMap<NoteBytes, NoteBytes> getHashMap(NoteBytesReader reader, int length) throws IOException{
         // Parse plugin entries
        int bytesRemaining = length;
        ConcurrentHashMap<NoteBytes, NoteBytes> map = new ConcurrentHashMap<>();
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

    public static ConcurrentHashMap<NoteBytes, NoteBytes> getHashMap(byte[] bytes) {
        int length = bytes.length;
        ConcurrentHashMap<NoteBytes, NoteBytes> map = new ConcurrentHashMap<>();
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



    public NoteBytesObject getNoteBytesObject() {

        byte[] bytes = new byte[byteLength_w_MetaData()];
        int offset = 0;
        for(Map.Entry<NoteBytes, NoteBytes> entry : m_pairs.entrySet()) {
            offset = NoteBytes.writeNote(entry.getKey(), bytes, offset);
            offset = NoteBytes.writeNote(entry.getValue(), bytes, offset);
        }
        return new NoteBytesObject(bytes);
    }



    public NoteBytes get(NoteBytes key) {
        return m_pairs.get(key);
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
    

 

    @Override
    public void clear() {
        m_pairs.clear();
    }

    @Override
    public boolean containsKey(Object key) {
        return m_pairs.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return m_pairs.containsValue(value);
    }

    @Override
    public Set<Entry<NoteBytes, NoteBytes>> entrySet() {
        return m_pairs.entrySet();
    }

    @Override
    public NoteBytes get(Object key) {
        return m_pairs.get(NoteBytes.of(key));
    }

    @Override
    public Set<NoteBytes> keySet() {
        return m_pairs.keySet();
    }


    @Override
    public NoteBytes put(NoteBytes key, NoteBytes value) {
        return m_pairs.put(key, value);
    }

    @Override
    public void putAll(Map<? extends NoteBytes, ? extends NoteBytes> m) {
        m_pairs.putAll(m);
    }

    @Override
    public NoteBytes remove(Object key) {
        return m_pairs.remove(key);
    }

    @Override
    public Collection<NoteBytes> values() {
        return m_pairs.values();
    }

    @Override
    public NoteBytes putIfAbsent(NoteBytes key, NoteBytes value) {
        return m_pairs.putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return m_pairs.remove(key, value);
    }

    @Override
    public NoteBytes replace(NoteBytes key, NoteBytes value) {
        return m_pairs.replace(key, value);
    }

    @Override
    public boolean replace(NoteBytes key, NoteBytes oldValue, NoteBytes newValue) {
        return m_pairs.replace(key, oldValue, newValue);
    }
}
