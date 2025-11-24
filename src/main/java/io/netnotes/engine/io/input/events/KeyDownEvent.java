package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;

public final class KeyDownEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private final NoteBytes key;
    private final NoteBytes scancode;
    private final int stateFlags;

    public KeyDownEvent(ContextPath sourcePath, NoteBytes key, NoteBytes scancode, int stateFlags) {
        this.sourcePath = sourcePath;
        this.key = key;
        this.scancode = scancode;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public NoteBytes getKeyCode() { return key; }
    public NoteBytes getScancode() { return scancode; }
    public int stateFlags() { return stateFlags; }
}

