package io.netnotes.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

public class NoteBytePair {
    private NoteBytes m_key;
    private NoteBytes m_value;


    public NoteBytePair(String key, byte[] value){
        this(key.toCharArray(), value);
    }
    public NoteBytePair(String key, Byte[] value){
        this(key.toCharArray(), ByteDecoding.unboxBytes(value));
    }

    public NoteBytePair(String key, char[] value){
        this(key.toCharArray(), value);
    }

    public NoteBytePair(char[] key, char[] value){
        this(key, ByteDecoding.charsToBytes(value));
    }

    public NoteBytePair(char[] key, byte[] value){
        this(ByteDecoding.charsToBytes(key), value);
    }

    public NoteBytePair(byte[] key, byte[] value){
        m_key = new NoteBytes(key);
        m_value = new NoteBytes(value);
    }

     public NoteBytePair(String key, NoteBytes value){
        m_key = new NoteBytes(key);
        m_value = value;
    }

    public NoteBytePair(NoteBytes key, NoteBytes value){
        m_key = key;
        m_value = value;
    }


    public NoteBytes getKey(){
        return m_key;
    }

    public NoteBytes getValue(){
        return m_value;
    }

    public NotePairTree getValueAsNotePairTree(){
        return getValue().getAsNotePairTree();
    }

    public NoteBytesArray getValueAsNoteBytesArray(){
        return getValue().getAsNoteBytesArray();
    }

    public static NoteBytePair read(DataInputStream dis) throws IOException, EOFException{
        NoteBytes key = NoteBytes.readNote(dis);
        NoteBytes value = NoteBytes.readNote(dis);
        return new NoteBytePair(key, value);
    }

    public static NoteBytePair read(ByteArrayInputStream bais) throws IOException{
        NoteBytes key = NoteBytes.readNote(bais);
        NoteBytes value = NoteBytes.readNote(bais);
        return new NoteBytePair(key, value);
    }

    public static NoteBytePair read(byte[] bytes, int offset){
        NoteBytes key = NoteBytes.readNote(bytes, offset);
        NoteBytes value = NoteBytes.readNote(bytes, offset + (4 + key.byteLength()));
        return new NoteBytePair(key, value);
    }


    public static int writeNoteBytePair(NoteBytePair pair, ByteArrayOutputStream outputStream) throws IOException{
        return write(pair.getKey(), pair.getValue(), outputStream);
    }

    public static int write( NoteBytes key, NoteBytes value, ByteArrayOutputStream outputStream) throws IOException{
        int len = NoteBytes.writeNote(key, outputStream);
        len += NoteBytes.writeNote(value, outputStream);
        return len;
    }


    public static void writeNoteBytePair(NoteBytePair pair, DataOutputStream outputStream) throws IOException{
        write(pair.getKey(), pair.getValue(), outputStream);
    }


    public static void write( NoteBytes key, NoteBytes value, DataOutputStream dos) throws IOException{
        NoteBytes.writeNote(key, dos);
        NoteBytes.writeNote(value, dos);
    }

    public void write( DataOutputStream dos) throws IOException{
        NoteBytePair.write(this.getKey(), this.getValue(), dos);
    }


}
