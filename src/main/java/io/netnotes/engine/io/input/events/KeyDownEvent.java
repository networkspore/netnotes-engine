package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;

public final class KeyDownEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private final NoteBytes keyCodeBytes;
    private final NoteBytes scancodeBytes;
    private final int stateFlags;

    private int keyCodeCache = -1;
    private int scanCodeCache = -1;
    

    public KeyDownEvent(ContextPath sourcePath, NoteBytes key, NoteBytes scancode, int stateFlags) {
        this.sourcePath = sourcePath;
        this.keyCodeBytes = key;
        this.scancodeBytes = scancode;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public NoteBytes getKeyCodeBytes() { return keyCodeBytes; }
    public NoteBytes getScancodeBytes() { return scancodeBytes; }
    public int stateFlags() { return stateFlags; }

    public int getScanCode(){
        if(scanCodeCache != -1){
            return scanCodeCache;
        }
        scanCodeCache = scancodeBytes.getAsInt();
        return scanCodeCache;
    }

    public int getKeyCode(){
        if(keyCodeCache != -1){
            return keyCodeCache;
        }
        keyCodeCache = keyCodeBytes.getAsInt();
        return keyCodeCache;
    }
}

