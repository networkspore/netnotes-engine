package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;

public final class KeyDownEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private final int key;
    private final int scancode;
    private final int stateFlags;

    public KeyDownEvent(ContextPath sourcePath, int key, int scancode, int stateFlags) {
        this.sourcePath = sourcePath;
        this.key = key;
        this.scancode = scancode;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public int getKeyCode() { return key; }
    public int scancode() { return scancode; }
    public int stateFlags() { return stateFlags; }
}

