package io.netnotes.engine.io.events;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public final class KeyDownEvent implements InputEvent {
    private final NoteBytesReadOnly sourceId;
    private final int key;
    private final int scancode;
    private final int stateFlags;

    public KeyDownEvent(NoteBytesReadOnly sourceId, int key, int scancode, int stateFlags) {
        this.sourceId = sourceId;
        this.key = key;
        this.scancode = scancode;
        this.stateFlags = stateFlags;
    }

    @Override
    public NoteBytesReadOnly getSourceId() { return sourceId; }
    public int key() { return key; }
    public int scancode() { return scancode; }
    public int stateFlags() { return stateFlags; }
}

