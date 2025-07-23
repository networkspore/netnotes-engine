package io.netnotes.engine.noteBytes;

import java.math.BigDecimal;

public class NoteBytesPair {
    private NoteBytes m_key;
    private NoteBytes m_value;


    public NoteBytesPair(String key, byte[] value){
        this(key.toCharArray(), value);
    }
    public NoteBytesPair(String key, Byte[] value){
        this(key.toCharArray(), ByteDecoding.unboxBytes(value));
    }

    public NoteBytesPair(String key, String value){
        this(key.toCharArray(), value.toCharArray());
    }

    public NoteBytesPair(String key, char[] value){
        this(key.toCharArray(), value);
    }

    public NoteBytesPair(char[] key, char[] value){
        this(ByteDecoding.charsToBytes(key), ByteDecoding.charsToBytes(value));
    }

    public NoteBytesPair(char[] key, byte[] value){
        this(new NoteBytes(key), new NoteBytes(value));
    }

    public NoteBytesPair(byte[] key, byte[] value){
        this(new NoteBytes(key), new NoteBytes(value));
    }

    public NoteBytesPair(String key, NoteBytes value){
        this(new NoteBytes(key), value);    
    }

    public NoteBytesPair(NoteBytes key, NoteBytes value){
        m_key = key;
        m_value = value;
    }

    public NoteBytesPair(String key, boolean value){
        this(key, new NoteBoolean(value));
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

    public NoteBytesObject getValueAsNoteBytesObject(){
        return getValue().getAsNoteBytesObject();
    }

    public NoteBytesArray getValueAsNoteBytesArray(){
        return getValue().getAsNoteBytesArray();
    }



    public static NoteBytesPair read(byte[] bytes, int offset, ByteDecoding byteDecoding){
        NoteBytes key = NoteBytes.readNote(bytes, offset, byteDecoding);
        NoteBytes value = NoteBytes.readNote(bytes, offset + (4 + key.byteLength()), byteDecoding);
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
