package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;

public final class KeyCharEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private final int codepoint;
    private final int stateFlags;

    public KeyCharEvent(ContextPath sourcePath, int codepoint, int stateFlags) {
        this.sourcePath = sourcePath;
        this.codepoint = codepoint;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public int getCodepoint() { return codepoint; }
    public int stateFlags() { return stateFlags; }
}
