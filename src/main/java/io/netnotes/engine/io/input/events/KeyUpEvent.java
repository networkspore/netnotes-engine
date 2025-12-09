package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;

public final class KeyUpEvent implements RoutedEvent {
     private final ContextPath sourcePath;
    private final NoteBytes keyCodeBytes;
    private final NoteBytes scanCodeBytes;
    private final int stateFlags;

    private int keyCodeCache = -1;
    private int scanCodeCache = -1;

    public KeyUpEvent(ContextPath sourcePath,  NoteBytes key, NoteBytes scancode, int stateFlags) {
        this.sourcePath = sourcePath;
        this.keyCodeBytes = key;
        this.scanCodeBytes = scancode;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public NoteBytes getKeyCodeBytes() { return keyCodeBytes; }
    public NoteBytes getScanCodeBytes() { return scanCodeBytes; }

    public int getScanCode(){
        if(scanCodeCache != -1){
            return scanCodeCache;
        }
        scanCodeCache = scanCodeBytes.getAsInt();
        return scanCodeCache;
    }

    public int getKeyCode(){
        if(keyCodeCache != -1){
            return keyCodeCache;
        }
        keyCodeCache = keyCodeBytes.getAsInt();
        return keyCodeCache;
    }
    public int stateFlags() { return stateFlags; }
}
