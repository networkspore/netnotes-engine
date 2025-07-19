package io.netnotes.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
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

    public NoteBytesPair(String key, char[] value){
        this(key.toCharArray(), value);
    }

    public NoteBytesPair(char[] key, char[] value){
        this(key, ByteDecoding.charsToBytes(value));
    }

    public NoteBytesPair(char[] key, byte[] value){
        this(ByteDecoding.charsToBytes(key), value);
    }

    public NoteBytesPair(byte[] key, byte[] value){
        m_key = new NoteBytes(key);
        m_value = new NoteBytes(value);
    }

     public NoteBytesPair(String key, NoteBytes value){
        m_key = new NoteBytes(key);
        m_value = value;
    }

    public NoteBytesPair(NoteBytes key, NoteBytes value){
        m_key = key;
        m_value = value;
    }


    public NoteBytes getKey(){
        return m_key;
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

    public static NoteBytesPair read(DataInputStream dis) throws IOException, EOFException{
        NoteBytes key = NoteBytes.readNote(dis);
        NoteBytes value = NoteBytes.readNote(dis);
        return new NoteBytesPair(key, value);
    }

    public static NoteBytesPair read(ByteArrayInputStream bais) throws IOException{
        NoteBytes key = NoteBytes.readNote(bais);
        NoteBytes value = NoteBytes.readNote(bais);
        return new NoteBytesPair(key, value);
    }

    public static NoteBytesPair read(byte[] bytes, int offset, ByteDecoding byteDecoding){
        NoteBytes key = NoteBytes.readNote(bytes, offset, byteDecoding);
        NoteBytes value = NoteBytes.readNote(bytes, offset + (4 + key.byteLength()), byteDecoding);
        return new NoteBytesPair(key, value);
    }


    public static int writeNoteBytePair(NoteBytesPair pair, ByteArrayOutputStream outputStream) throws IOException{
        return write(pair.getKey(), pair.getValue(), outputStream);
    }

    public static int write( NoteBytes key, NoteBytes value, ByteArrayOutputStream outputStream) throws IOException{
        int len = NoteBytes.writeNote(key, outputStream);
        len += NoteBytes.writeNote(value, outputStream);
        return len;
    }


    public static void writeNoteBytePair(NoteBytesPair pair, DataOutputStream outputStream) throws IOException{
        write(pair.getKey(), pair.getValue(), outputStream);
    }


    public static void write( NoteBytes key, NoteBytes value, DataOutputStream dos) throws IOException{
        NoteBytes.writeNote(key, dos);
        NoteBytes.writeNote(value, dos);
    }

    public void write( DataOutputStream dos) throws IOException{
        NoteBytesPair.write(this.getKey(), this.getValue(), dos);
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
