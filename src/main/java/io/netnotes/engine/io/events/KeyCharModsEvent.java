package io.netnotes.engine.io.events;

import io.netnotes.engine.io.ContextPath;

public final class KeyCharModsEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private final int codepoint;
    private final int stateFlags;

    public KeyCharModsEvent(ContextPath sourcePath, int codepoint, int stateFlags) {
        this.sourcePath = sourcePath;
        this.codepoint = codepoint;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public int codepoint() { return codepoint; }
    public int stateFlags() { return stateFlags; }
}

