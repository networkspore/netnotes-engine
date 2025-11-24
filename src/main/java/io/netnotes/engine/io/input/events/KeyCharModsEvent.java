package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;

public final class KeyCharModsEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private final NoteBytes codepoint;
    private final int stateFlags;

    public KeyCharModsEvent(ContextPath sourcePath, NoteBytes codepoint, int stateFlags) {
        this.sourcePath = sourcePath;
        this.codepoint = codepoint;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public NoteBytes codepoint() { return codepoint; }
    public int stateFlags() { return stateFlags; }
}

