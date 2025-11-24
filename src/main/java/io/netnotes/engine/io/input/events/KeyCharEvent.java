package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.Keyboard;
import io.netnotes.engine.noteBytes.NoteBytes;

public final class KeyCharEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private final NoteBytes codepoint;
    private final int stateFlags;

    public KeyCharEvent(ContextPath sourcePath, NoteBytes codepoint, int stateFlags) {
        this.sourcePath = sourcePath;
        this.codepoint = codepoint;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public NoteBytes getCodepoint() { return codepoint; }
    public int stateFlags() { return stateFlags; }
    public NoteBytes getUTF8() { return Keyboard.CodePointCharsByteRegistry.get(codepoint); }
}
