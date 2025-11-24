package io.netnotes.engine.noteBytes.collections;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObjectEphemeral;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteBytesMapEphemeral implements AutoCloseable{
    private HashMap<NoteBytesEphemeral, NoteBytesEphemeral> m_pairs = null;


    public NoteBytesMapEphemeral(){
        m_pairs = new HashMap<>();
    }

    public NoteBytesMapEphemeral(NoteBytesEphemeral noteBytes) throws IOException{
        init(noteBytes.get());
    }

    public NoteBytesMapEphemeral(NoteBytesPairEphemeral[] pairs){
        m_pairs = new HashMap<>();
        for(NoteBytesPairEphemeral pair : pairs){
            m_pairs.put(pair.getKey(), pair.getValue());
        }
    }


    public static HashMap<NoteBytesEphemeral, NoteBytesEphemeral> getHashMap(byte[] bytes) throws IOException{
        int length = bytes.length;
        HashMap<NoteBytesEphemeral, NoteBytesEphemeral> map = new HashMap<>();
        if(length > 0){
            int offset = 0;
            while(offset < length) {
                NoteBytesEphemeral key = NoteBytesEphemeral.readNote(bytes, offset);
                offset += (5 + key.byteLength()); // 1 byte type + 4 bytes length
                NoteBytesEphemeral value = NoteBytesEphemeral.readNote(bytes, offset);
                offset += (5 + value.byteLength()); // 1 byte type + 4 bytes length
                map.put(key, value);
            }
           
        }
        return map;
    }
    public void init(byte[] bytes) throws IOException{
        if(m_pairs != null){
            close();
        }
        m_pairs = getHashMap(bytes);
    }

    public int rawByteLength(){
        int length = 0; 
        for(Map.Entry<NoteBytesEphemeral, NoteBytesEphemeral> entry : m_pairs.entrySet()) {
            length += (entry.getKey().byteLength() + entry.getValue().byteLength() );
        }
        return length;
    }

    public int byteLength_w_MetaData(){
        int length = 0; 
        for(Map.Entry<NoteBytesEphemeral, NoteBytesEphemeral> entry : m_pairs.entrySet()) {
            length += (entry.getKey().byteLength() + entry.getValue().byteLength()  + 10);
        }
        return length;
    }


    public NoteBytesEphemeral getNoteBytesEphemeral() {

        byte[] bytes = new byte[byteLength_w_MetaData()];
        int offset = 0;
        for(Map.Entry<NoteBytesEphemeral, NoteBytesEphemeral> entry : m_pairs.entrySet()) {
            offset = NoteBytes.writeNote(entry.getKey(), bytes, offset);
            offset = NoteBytes.writeNote(entry.getValue(), bytes, offset);
        }
        return new NoteBytesEphemeral(bytes, NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE);
    }

    public NoteBytesObjectEphemeral getNoteBytesObjectEphemeral() {

        byte[] bytes = new byte[byteLength_w_MetaData()];
        int offset = 0;
        for(Map.Entry<NoteBytesEphemeral, NoteBytesEphemeral> entry : m_pairs.entrySet()) {
            offset = NoteBytesEphemeral.writeNote(entry.getKey(), bytes, offset);
            offset = NoteBytesEphemeral.writeNote(entry.getValue(), bytes, offset);
        }
        return new NoteBytesObjectEphemeral(bytes);
    }



    public NoteBytesEphemeral get(NoteBytes key) {
        return m_pairs.get(key);
    }



    public NoteBytesPairEphemeral getFirst(){
        return getAtIndex(0);
    }

    public NoteBytesPairEphemeral getAtIndex(int index){
        int i = 0;
        for(Map.Entry<NoteBytesEphemeral, NoteBytesEphemeral> entry : m_pairs.entrySet()) {
            if(i == index){
                return new NoteBytesPairEphemeral(entry.getKey(), entry.getValue());
            }
            i++;
        }
        return null;
    }


    public boolean isEmpty() {
        return m_pairs.size() == 0;
    }

    public void add(NoteBytesPairEphemeral pair) {
        m_pairs.put(pair.getKey(), pair.getValue());
    }

    public void addAll(Map<? extends NoteBytes, ? extends NoteBytes> m) {
        for(Map.Entry<? extends NoteBytes, ? extends NoteBytes> pair : m.entrySet()){
            add(new NoteBytesPairEphemeral(new NoteBytesEphemeral(pair.getKey()), new NoteBytesEphemeral(pair.getValue())));
        }
    }

    public NoteBytesEphemeral remove(String keyString) {
        return remove(new NoteBytes(keyString));
    }

    public NoteBytesEphemeral remove(NoteBytes key) {
        return m_pairs.remove(key);
    }

    

    public NoteBytesPairEphemeral removeAt(int index) {
        int i = 0;
        NoteBytesPairEphemeral value = null;
        for(Map.Entry<NoteBytesEphemeral, NoteBytesEphemeral> entry : m_pairs.entrySet()) {
            value = index == i ? new NoteBytesPairEphemeral(entry.getKey(), entry.getValue()) : value;
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
    
    
    public void close() {
        for(Map.Entry<NoteBytesEphemeral, NoteBytesEphemeral> entry : m_pairs.entrySet()) {
            entry.getKey().close();
            entry.getValue().close();
        }
        
        m_pairs.clear();
        
    }


    public void clear() {
        
        close();
       
    }

    
    public boolean containsKey(Object key) {
        return m_pairs.containsKey(key);
    }

    
    public boolean containsValue(Object value) {
        return m_pairs.containsValue(value);
    }

    
    public Set<Entry<NoteBytesEphemeral, NoteBytesEphemeral>> entrySet() {
        return m_pairs.entrySet();
    }

    
    public NoteBytesEphemeral get(Object key) {
        return m_pairs.get(NoteBytes.of(key));
    }

    
    public Set<NoteBytesEphemeral> keySet() {
        return m_pairs.keySet();
    }


    
    public NoteBytesEphemeral put(NoteBytesEphemeral key, NoteBytesEphemeral value) {
        return m_pairs.put(key, value);
    }

    
    public void putAll(Map<? extends NoteBytesEphemeral, ? extends NoteBytesEphemeral> m) {
        m_pairs.putAll(m);
    }

    
    public NoteBytesEphemeral remove(Object key) {
        return m_pairs.remove(key);
    }

    
    public Collection<NoteBytesEphemeral> values() {
        return m_pairs.values();
    }
}
