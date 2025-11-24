package io.netnotes.engine.noteBytes.collections;

import io.netnotes.engine.noteBytes.NoteBytes;

public class NoteBytesRunnablePair {
    private final NoteBytes key;
    private final Runnable runnable;

    public NoteBytesRunnablePair(NoteBytes key, Runnable runnable){
        this.key = key;
        this.runnable = runnable;
    }

    public NoteBytes getKey(){
        return key;
    }

    public Runnable getRunnable(){
        return runnable;
    }

    public void run(){
        runnable.run();
    }

    public int compare(NoteBytes key){
        return this.key.compare(key);
    }

    @Override
    public boolean equals(Object key){
        return this.key.equals(key);
    }

    @Override
    public int hashCode(){
        return this.key.hashCode();
    }
}
