package io.netnotes.engine.noteBytes.processing;

public class LongCounter {
    private long m_count = 0;
    
    public LongCounter(){
        m_count = 0;
    }
    public LongCounter(long initialValue){
        m_count = initialValue;
    }

    public void add(long count) { 
        this.m_count += count; 
    }

    public void subtract(long count){
        this.m_count -= count;
    }

    public void multiply(long count){
        this.m_count *= count;
    }

    public void divide(long count){
        this.m_count /= count;
    }

    public long get() { 
        return m_count; 
    }

    public void set(long count){ 
        this.m_count = count; 
    }
}