package io.netnotes.engine.io.input;

import org.bouncycastle.util.Arrays;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;

public class KeyRunTable {
    private final Runnable[] CP_TO_RUNNABLE_TABLE = new Runnable[127];
    private final int[] index = new int[1];

    public KeyRunTable(NoteBytesRunnablePair... keyRunnables){
        Arrays.fill(CP_TO_RUNNABLE_TABLE, null);
        setKeyRunnables(keyRunnables);        
    }

    public Runnable getKeyRunnable(NoteBytes key){
        index[0] = key.get()[3] & 0xFF;
        Runnable value = CP_TO_RUNNABLE_TABLE[index[0]];
        index[0] = 0;
        return value;
    }

    public void setKeyRunnables(NoteBytesRunnablePair... keyRunnables){
        for(NoteBytesRunnablePair runnablePair : keyRunnables){
            NoteBytes key = runnablePair.getKey();
            Runnable runnable = runnablePair.getRunnable();
            setKeyRunnable(key, runnable);
        }
    }

    public void setKeyRunnable(NoteBytes key, Runnable runnable){
        
        index[0] = key.get()[3] & 0xFF;
        if(index[0] > -1 && index[0]  < 127){
            CP_TO_RUNNABLE_TABLE[index[0]] = runnable;
        }
        index[0] = 0;
    }

    public void run(NoteBytes key){
        Runnable runnable = getKeyRunnable(key);
        if(runnable != null){
            runnable.run();
        }
    }
}
