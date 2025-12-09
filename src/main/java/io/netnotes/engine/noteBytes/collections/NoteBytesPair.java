package io.netnotes.engine.noteBytes.collections;

import java.math.BigDecimal;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteException;
import io.netnotes.engine.noteBytes.NoteInteger;
import io.netnotes.engine.noteBytes.NoteString;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteBytesPair {
    private NoteBytes m_key;
    private NoteBytes m_value;

    public NoteBytesPair(NoteBytes key, NoteBytes value){
        m_key = key;
        m_value = value;
    }

    public NoteBytesPair(NoteBytes key, int value){
        m_key = key;
        m_value = new NoteInteger(value);
    }

    public NoteBytesPair(char[] key, char[] value){
        m_key = new NoteString(key);
        m_value =new NoteString(value);
    }

    public NoteBytesPair(NoteBytes key, Throwable value){
        this(key, new NoteException(value));
    }

    public NoteBytesPair(NoteBytes key, long value){
        this(key, new NoteBytes(value));
    }
    public NoteBytesPair(NoteBytes key, ContextPath value){
        this(key, value.getSegments());
    }


    public NoteBytesPair(String key, String value){
        this(new NoteString(key), new NoteString(value));
    }

    public NoteBytesPair(String key, int value){
        this(new NoteString(key), new NoteBytes(value));
    }

    public NoteBytesPair(String key, long value){
        this(new NoteString(key), new NoteBytes(value));
    }

    public NoteBytesPair(String key, char[] value){
        this(new NoteString(key), new NoteBytes(value));
    }


    public NoteBytesPair(String key, NoteBytes value){
        this(new NoteString(key), value);    
    }

    public NoteBytesPair(NoteBytes key, String value){
        this(key, new NoteString(value));    
    }

    public NoteBytesPair(NoteBytes key, boolean value){
        this(key, new NoteBytes(value));    
    }
 
    public NoteBytesPair(String key, Byte[] value, byte type){
        m_key = new NoteBytes(key);
        m_value = new NoteBytes(ByteDecoding.unboxBytes(value), type);
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
        offset += (NoteBytesMetaData.STANDARD_META_DATA_SIZE + key.byteLength());
        NoteBytes value = NoteBytes.readNote(bytes, offset);
        return new NoteBytesPair(key, value);
    }

    public static int write(NoteBytesPair pair, byte[] dst, int dstOffset){
        dstOffset = NoteBytes.writeNote(pair.getKey(), dst, dstOffset);
        return NoteBytes.writeNote(pair.getValue(), dst, dstOffset);
    }

    public byte[] get(){
        byte[] bytes = new byte[byteLength()];
        NoteBytesPair.write(this, bytes, 0);
        return bytes;
    }

    public int byteLength(){
        return getKey().byteLength() + 
            getValue().byteLength() + 
            (NoteBytesMetaData.STANDARD_META_DATA_SIZE * 2);
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
