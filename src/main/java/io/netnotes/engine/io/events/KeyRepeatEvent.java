package io.netnotes.engine.io.events;

import io.netnotes.engine.io.ContextPath;

public final class KeyRepeatEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private final int key;
    private final int scancode;
    private final int stateFlags;

    public KeyRepeatEvent(ContextPath sourcePath, int key, int scancode, int stateFlags) {
        this.sourcePath = sourcePath;
        this.key = key;
        this.scancode = scancode;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public int key() { return key; }
    public int scancode() { return scancode; }
    public int stateFlags() { return stateFlags; }
}
