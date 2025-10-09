package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.processing.ByteDecoding;

public class NoteBytesReadOnly extends NoteBytes {
    

    public NoteBytesReadOnly( Object value){
        this(NoteBytes.of(value));
    }

    public NoteBytesReadOnly(NoteBytes value){
        super(value.get(), value.getType());
    }

   
    public NoteBytesReadOnly( byte[] value, byte type){
        super(value, type);
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
        return new NoteBytesReadOnly(dst, type);
    }

    @Override 
    public byte[] get(){
        byte[] bytes = super.get();
        byte[] newBytes = new byte[bytes.length];
        System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
        return newBytes;
    }

    
    @Override
    public NoteBytesReadOnly copy(){
        return new NoteBytesReadOnly(get(), getType());
    }

    @Override
    public void set(byte[] disabled){

    }

    @Override
    public void set(byte[] disabled, byte type){

    }

    @Override
    public void setType(byte disabled){

    }

    @Override
    public void clear(){

    }

    
 
    @Override
    public void ruin(){
        super.ruin();
    }

}
