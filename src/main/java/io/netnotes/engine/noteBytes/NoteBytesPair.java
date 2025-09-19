package io.netnotes.engine.noteBytes;

import java.math.BigDecimal;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;

public class NoteBytesPair {
    private NoteBytes m_key;
    private NoteBytes m_value;


    public NoteBytesPair(String key, String value){
        this(key.toCharArray(), value.toCharArray());
    }

    public NoteBytesPair(String key, char[] value){
        this(key.toCharArray(), value);
    }

    public NoteBytesPair(char[] key, char[] value){
        m_key = new NoteBytes(key);
        m_value =new NoteBytes(value);
    }

    public NoteBytesPair(String key, NoteBytes value){
        this(new NoteBytes(key), value);    
    }

    public NoteBytesPair(String key, Object value){
        this(new NoteBytes(key), NoteBytes.createNoteBytes(value));    
    }
    public NoteBytesPair(NoteBytes key, Object value){
        this(key, NoteBytes.createNoteBytes(value));    
    }

    public NoteBytesPair(NoteBytes key, NoteBytes value){
        m_key = key;
        m_value = value;
    }

    public NoteBytesPair(String key, Byte[] value, byte type){
        m_key = new NoteBytes(key);
        m_value = new NoteBytes(ByteDecoding.unboxBytes(value), ByteDecoding.of(type));
    }

    public NoteBytesPair(NoteBytes key, NoteBytes value, byte type){
        m_key = key;
        m_value = new NoteBytes(value.get(), ByteDecoding.of(type));
    }

    public NoteBytesPair(NoteBytes key, NoteBytes value, ByteDecoding byteDecoding){
        m_key = key;
        m_value = new NoteBytes(value.get(), byteDecoding);
    }

    public NoteBytes getKey(){
        return m_key;
    }

    public String getKeyAsString(){
        return m_key.getAsString();
    }

    public NoteBytes getValue(){
        return m_value;
    }

    public NoteBytesReadOnly getValueAsReadOnly(){
        return getValueAsReadOnly();
    }

    public NoteBytesObject getValueAsNoteBytesObject(){
        return getValue().getAsNoteBytesObject();
    }

    public NoteBytesArray getValueAsNoteBytesArray(){
        return getValue().getAsNoteBytesArray();
    }

    public NoteBytesArrayReadOnly getValueAsNoteBytesArrayReadOnly(){
        return getValue().getAsNoteBytesArrayReadOnly();
    }


    public static NoteBytesPair read(byte[] bytes, int offset){
        NoteBytes key = NoteBytes.readNote(bytes, offset);
        offset += (5 + key.byteLength()); // 1 byte type + 4 bytes length
        
        NoteBytes value = NoteBytes.readNote(bytes, offset);
        return new NoteBytesPair(key, value);
    }




    public NoteBytesObject getAsNoteBytesObject(){
        return getValue() != null ? getValueAsNoteBytesObject() : null;
    }

    public NoteBytesArray getAsNoteBytesArray(){
        return getValue() != null ? getValueAsNoteBytesArray() : null;
    }

    public BigDecimal getAsBigDecimal(){
        return getValue() != null ? getValue().getAsBigDecimal() : null;
    }

    public long getAsLong(){
        return getValue() != null ? getValue().getAsLong() : null;
    }

    public int getAsInt(){
        return getValue() != null ? getValue().getAsInt() : null;
    }

    public short getAsShort(){
        return getValue() != null ? getValue().getAsShort() : null;
    }

    public byte getAsByte(){
        return getValue() != null ? getValue().getAsByte() : null;
    }

    public double getAsDouble(){
        return getValue() != null ? getValue().getAsDouble() : null;
    }

    public boolean getAsBoolean(){
        return getValue() != null ? getValue().getAsBoolean() : null;
    }

    public String getAsString(){
        return getValue() != null ? getValue().getAsString() : null;
    }

}
