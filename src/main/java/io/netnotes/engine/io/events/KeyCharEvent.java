package io.netnotes.engine.io.events;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public final class KeyCharEvent implements InputEvent {
    private final NoteBytesReadOnly sourceId;
    private final int codepoint;
    private final int stateFlags;

    public KeyCharEvent(NoteBytesReadOnly sourceId, int codepoint, int stateFlags) {
        this.sourceId = sourceId;
        this.codepoint = codepoint;
        this.stateFlags = stateFlags;
    }

    @Override
    public NoteBytesReadOnly getSourceId() { return sourceId; }
    public int codepoint() { return codepoint; }
    public int stateFlags() { return stateFlags; }
}
