package io.netnotes.engine.noteBytes;

import java.util.concurrent.locks.ReentrantLock;

public class NoteBytesAsync extends NoteBytes{

      private final ReentrantLock m_lock = new ReentrantLock();

    public NoteBytesAsync(byte[] bytes){
        super(bytes);        
    }
 
    public NoteBytesAsync( String value){
        super(value);
    }

    public NoteBytesAsync(long _long){
        super(_long);
    }

    public NoteBytesAsync( Byte[] value, byte type){
        super(value, type);
    }

    public NoteBytesAsync( char[] value){
        super(value);
    }
    
    public NoteBytesAsync( String value, byte type){
        super(value, type);
    }


    public NoteBytesAsync( char[] value, byte type){
        super(value, type);
    }

    public NoteBytesAsync( byte[] value, byte type){
        super(value, type);
    }

    public NoteBytesAsync(NoteBytes noteBytes){
        super(noteBytes.get(), noteBytes.getType());
    }

    protected void acquireLock() throws InterruptedException {
        m_lock.lockInterruptibly();
    }

    protected void releaseLock() {
        m_lock.unlock();
    }


    public ReentrantLock getLock(){
        return m_lock;
    }

}
