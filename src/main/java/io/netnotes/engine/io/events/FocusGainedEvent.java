package io.netnotes.engine.io.events;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public final class FocusGainedEvent implements InputEvent {
    private final NoteBytesReadOnly sourceId;

    public FocusGainedEvent(NoteBytesReadOnly sourceId) {
        this.sourceId = sourceId;
    }

    @Override
    public NoteBytesReadOnly getSourceId() { return sourceId; }
}
