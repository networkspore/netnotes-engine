package io.netnotes.engine.noteBytes;

import java.util.Arrays;

public class NoteBytesReadOnly extends NoteBytes {
    

    public NoteBytesReadOnly( Object value){
        this(NoteBytes.createNoteBytes(value));
    }

    public NoteBytesReadOnly(NoteBytes value){
        super(value.get(), value.getByteDecoding());
    }

    public NoteBytesReadOnly(byte[] bytes, ByteDecoding byteDecoding){
        super(bytes, byteDecoding);
    }

    public NoteBytesReadOnly( byte[] value, byte type){
        this(value, ByteDecoding.getDecodingFromType(type));
    }

    public static NoteBytesReadOnly readNote(byte[] bytes, int offset){
        return readNote(bytes, offset, false);
    }

    public static NoteBytesReadOnly readNote(byte[] bytes, int offset, boolean isLittleEndian){
        byte type = bytes[offset];
        offset++;
        int size = isLittleEndian ? ByteDecoding.bytesToIntLittleEndian(bytes, offset) : ByteDecoding.bytesToIntBigEndian(bytes, offset);
        byte[] dst = new byte[size];
        System.arraycopy(bytes, offset + 4, dst, 0, size);
        return new NoteBytesReadOnly(dst, ByteDecoding.getDecodingFromType(type));
    }
    
    @Override
    public NoteBytesReadOnly copy(){
        return new NoteBytesReadOnly(Arrays.copyOf(get(), byteLength()), getByteDecoding());
    }

    @Override
    public void set(byte[] disabled){

    }

    @Override
    public void set(byte[] disabled, ByteDecoding disabledByteDecoding){

    }

    @Override
    public void setByteDecoding(ByteDecoding disabled){

    }

    @Override
    public void clear(){

    }

 
    @Override
    public void ruin(){
        super.ruin();
    }

}
