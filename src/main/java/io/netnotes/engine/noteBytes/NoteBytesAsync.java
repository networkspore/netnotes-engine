package io.netnotes.engine.noteBytes;

import java.nio.CharBuffer;
import java.util.concurrent.locks.ReentrantLock;

import io.netnotes.engine.noteBytes.ByteDecoding.NoteBytesMetaData;

public class NoteBytesAsync extends NoteBytes{

      private final ReentrantLock m_lock = new ReentrantLock();

    public NoteBytesAsync(byte[] bytes){
        super(bytes);        
    }
 

    public NoteBytesAsync( String value){
        this(value.toCharArray());
    }

    public NoteBytesAsync(long _long){
        this(ByteDecoding.longToBytesBigEndian(_long), NoteBytesMetaData.LONG_TYPE);
    }

    public NoteBytesAsync( Byte[] value){
        this(ByteDecoding.unboxBytes(value));
    }

    public NoteBytesAsync( char[] value){
        this( ByteDecoding.charsToBytes(value), ByteDecoding.STRING_UTF8);
    }
    
    public NoteBytesAsync( String value, ByteDecoding byteDecoding){
        this(ByteDecoding.charsToByteArray(CharBuffer.wrap(value), byteDecoding), byteDecoding);
    }

    public NoteBytesAsync( CharBuffer charBuffer, ByteDecoding byteDecoding){
        this( ByteDecoding.charsToByteArray(charBuffer, byteDecoding), byteDecoding);
    }


    public NoteBytesAsync( char[] value, ByteDecoding byteDecoding){
        this( ByteDecoding.charsToByteArray(value, byteDecoding), byteDecoding);
    }

    public NoteBytesAsync( byte[] value, ByteDecoding byteDecoding){
        super(value, byteDecoding);
    }

    public NoteBytesAsync( byte[] value, byte type){
        this(value, ByteDecoding.getDecodingFromType(type));
    }

    protected void acquireLock() throws InterruptedException {
        m_lock.lockInterruptibly();
    }

    protected void releaseLock() {
        m_lock.unlock();
    }

       public void set(byte[] value, ByteDecoding byteDecoding) {
        try {
            acquireLock();
            super.set(value, byteDecoding);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            releaseLock();
        }
    }

    public ReentrantLock getLock(){
        return m_lock;
    }

}
