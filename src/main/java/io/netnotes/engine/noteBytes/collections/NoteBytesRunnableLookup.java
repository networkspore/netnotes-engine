package io.netnotes.engine.noteBytes.collections;

import java.util.concurrent.Executor;

import io.netnotes.engine.noteBytes.NoteBytes;

public class NoteBytesRunnableLookup {
    private NoteBytesRunnablePair[] array;

    public NoteBytesRunnableLookup(NoteBytesRunnablePair... array){
        this.array = array;
    }

    public NoteBytesRunnablePair[] getNoteBytesPairArray(){
        return array;
    }

    public boolean run(NoteBytes key){
        Runnable runnable = lookup(key);
        boolean isRunnable = runnable != null;
        if(isRunnable){
            runnable.run();
        }
        return isRunnable;
    }

    public boolean execute(NoteBytes key, Executor exec){
        Runnable runnable = lookup(key);
        boolean isRunnable = runnable != null;
        if(isRunnable){
            exec.execute(runnable);
        }
        return isRunnable;
    }


    public Runnable lookup(NoteBytes key) {
        int low = 0;
        int high = array.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            NoteBytesRunnablePair entry = array[mid];

            int cmp = key.compare(entry.getKey()); //compareBytes(key, entry.key.bytes());
            if (cmp == 0) return entry.getRunnable();

            if (cmp < 0) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }

        return null;
    }
}
