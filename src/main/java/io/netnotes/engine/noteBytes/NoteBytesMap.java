package io.netnotes.engine.noteBytes;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.netnotes.engine.noteBytes.ByteDecoding.NoteBytesMetaData;

public class NoteBytesMap implements Map<NoteBytes, NoteBytes>{
    private HashMap<NoteBytes, NoteBytes> m_pairs = null;


    public NoteBytesMap(byte[] bytes) throws IOException{
        init(bytes);
    }

    public NoteBytesMap(){
        m_pairs = new HashMap<>();
    }

    public NoteBytesMap(NoteBytes noteBytes){
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
                offset += (5 + key.byteLength()); // 1 byte type + 4 bytes length
                NoteBytes value = NoteBytes.readNote(bytes, offset);
                offset += (5 + value.byteLength()); // 1 byte type + 4 bytes length
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
            length += (entry.getKey().byteLength() + entry.getValue().byteLength()  + 10);
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
        return m_pairs.get(NoteBytes.createNoteBytes(key));
    }

    @Override
    public Set<NoteBytes> keySet() {
        return m_pairs.keySet();
    }


    public NoteBytes put(Object key, Object value) {
        
        return m_pairs.put(NoteBytes.createNoteBytes(key), NoteBytes.createNoteBytes(value));
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
}
