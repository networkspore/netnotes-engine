package io.netnotes.engine.noteBytes.processing;

public class IntCounter {
    private int m_count = 0;
    
    public IntCounter(){
        m_count = 0;
    }
    public IntCounter(int initialValue){
        m_count = initialValue;
    }

    public void increment(){
        this.m_count++;
    }
    public void decrement(){
        this.m_count--;
    }

    public void add(int count) { 
        this.m_count += count; 
    }

    public void subtract(int count){
        this.m_count -= count;
    }

    public void multiply(int count){
        this.m_count *= count;
    }

    public void divide(int count){
        this.m_count /= count;
    }

    public int get() { 
        return m_count; 
    }

    public void set(int count){ 
        this.m_count = count; 
    }
}