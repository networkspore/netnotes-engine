package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public class NoteString extends NoteBytes {

    public NoteString(String key){
        this(key.getBytes());
    }

    public NoteString(char[] key){
        super(key);
    }

    public NoteString(byte[] bytes, byte type){
        super(bytes, type);
    }

    public NoteString(byte[] bytes){
        super(bytes, NoteBytesMetaData.STRING_TYPE);
    }
     public NoteString(){
        super(new byte[0], NoteBytesMetaData.STRING_TYPE);
    }

    public String getString(){
        return toString();
    }

    public void setString(NoteString str){
        set(str.getBytes(), str.getType());
    }

    public void setString(String str){
        set(ByteDecoding.stringToBytes(str, getType()));
    }


    @Override
    public String toString(){
        
        return getAsString();
    }







}
