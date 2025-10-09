package io.netnotes.engine.noteBytes.collections;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteBytesPairEphemeral implements AutoCloseable {
    private NoteBytes m_key;
    private NoteBytesEphemeral m_value;


    public NoteBytesPairEphemeral(Object key, Object value){
        m_key = NoteBytes.of(key);
        m_value = NoteBytesEphemeral.create(value);
    }

    public NoteBytes getKey(){
        return m_key;
    }

    public NoteBytesEphemeral getValue(){
        return m_value;
    }

     public static NoteBytesPairEphemeral read(byte[] bytes, int offset){
        NoteBytesEphemeral key = NoteBytesEphemeral.readNote(bytes, offset);
        offset += (5 + key.byteLength()); // 1 byte type + 4 bytes length
        
        NoteBytesEphemeral value = NoteBytesEphemeral.readNote(bytes, offset);
        return new NoteBytesPairEphemeral(key, value);
    }

    public int byteLength(){
        return getKey().byteLength() + 
            getValue().byteLength() + 
            (NoteBytesMetaData.STANDARD_META_DATA_SIZE * 2);
    }

    public void close(){
        m_key.clear();
        m_value.close();
    }
}
