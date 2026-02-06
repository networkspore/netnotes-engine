package io.netnotes.engine.io.input.events.keyboardEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public final class KeyDownEvent extends RoutedKeyboardEvent {
    private final ContextPath sourcePath;
    private final NoteBytes keyCodeBytes;
    private final NoteBytes scancodeBytes;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;

    private int keyCodeCache = -1;
    private int scanCodeCache = -1;
    

    public KeyDownEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int stateFlags, NoteBytes key, NoteBytes scancode) {
        this.sourcePath = sourcePath;
        this.keyCodeBytes = key;
        this.scancodeBytes = scancode;
        this.stateFlags = stateFlags;
        this.typeBytes = typeBytes;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public NoteBytes getKeyCodeBytes() { return keyCodeBytes; }
    public NoteBytes getScancodeBytes() { return scancodeBytes; }

    @Override
    public int getStateFlags() { return stateFlags; }

    public void setStateFlags(int flags) { stateFlags = flags; }

    @Override
    public NoteBytesReadOnly getEventTypeBytes(){
        return typeBytes;
    }
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

