package io.netnotes.engine.noteBytes.collections;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteBytesPairEphemeral implements AutoCloseable {
    private NoteBytesEphemeral m_key;
    private NoteBytesEphemeral m_value;


    public NoteBytesPairEphemeral(NoteBytesEphemeral key, NoteBytesEphemeral value){
        m_key = key;
        m_value = value;
    }

    public NoteBytesPairEphemeral(NoteBytes key, NoteBytesEphemeral value){
        m_key = new NoteBytesEphemeral(key.copy());
        m_value = value;
    }

    public NoteBytesPairEphemeral(NoteBytes key, NoteBytes value){
        m_key = new NoteBytesEphemeral(key.copy());
        m_value = new NoteBytesEphemeral(value.copy());
    }

    public NoteBytesPairEphemeral(NoteBytes key, byte[] value){
        m_key = new NoteBytesEphemeral(key.copy());
        m_value = new NoteBytesEphemeral(value);
    }

     public NoteBytesPairEphemeral(NoteBytes key, long value){
        m_key = new NoteBytesEphemeral(key.copy());
        m_value = new NoteBytesEphemeral(value);
    }

     public NoteBytesPairEphemeral(NoteBytes key, String value){
        m_key = new NoteBytesEphemeral(key.copy());
        m_value = new NoteBytesEphemeral(value);
    }


    public NoteBytesEphemeral getKey(){
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

    public byte[] get(){
        byte[] bytes = new byte[byteLength()];
        NoteBytesPairEphemeral.write(this, bytes, 0);
        return bytes;
    }

    public static int write(NoteBytesPairEphemeral pair, byte[] dst, int dstOffset){
        dstOffset = NoteBytes.writeNote(pair.getKey(), dst, dstOffset);
        return NoteBytes.writeNote(pair.getValue(), dst, dstOffset);
    }


    public void close(){
        m_key.clear();
        m_value.close();
    }
}
