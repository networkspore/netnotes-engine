package io.netnotes.engine.noteBytes;

import java.nio.ByteBuffer;
import java.util.Arrays;

import io.netnotes.engine.ByteDecoding;

public class NoteString extends NoteBytes {

    public NoteString(ByteBuffer byteBuffer, ByteDecoding byteDecoding){
        this(Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit() - byteBuffer.position()), byteDecoding);
    }

    public NoteString(String key){
        this(key.toCharArray());
    }

    public NoteString(char[] key){
        super(key);
    }

    public NoteString(byte[] bytes, ByteDecoding byteDecoding){
        super(bytes, byteDecoding);
    }

    public NoteString(byte[] bytes){
        super(bytes, ByteDecoding.STRING_UTF8);
    }
     public NoteString(){
        super(new byte[0], ByteDecoding.STRING_UTF8);
    }

    public String getString(){
        return toString();
    }

    public void setString(NoteString str){
        set(str.getBytes(), str.getByteDecoding());
    }

    public void setString(String str){
        set(ByteDecoding.charsToByteArray(str.toCharArray(), getByteDecoding()));
    }

    @Override
    public void set(byte[] bytes, ByteDecoding byteDecoding){
        super.set(bytes, byteDecoding);
    }


    @Override
    public String toString(){
        
        return new String(decodeCharArray());
    }



    public static NoteString getNoteStringFromBuffer(ByteBuffer encodedBytes, int length, ByteDecoding byteDecoding){
        if(length > 0){
            byte[] bytes = new byte[length];
            encodedBytes.get(bytes);
            return new NoteString(bytes, byteDecoding);
        }
        return new NoteString(new byte[0], ByteDecoding.STRING_UTF8);    
    } 


    public static NoteString copyNoteStringFromBuffer(ByteBuffer encodedBytes, int offset, int length, ByteDecoding byteDecoding){
        length = Math.min(length, encodedBytes.limit() - offset); 
        if(length > 0){
            return new NoteString(Arrays.copyOfRange(encodedBytes.array(), offset, length), byteDecoding);
        }
        return new NoteString(new byte[0], ByteDecoding.STRING_UTF8);    
    } 



}
