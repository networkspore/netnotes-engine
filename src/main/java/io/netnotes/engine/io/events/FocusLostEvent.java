package io.netnotes.engine.io.events;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public final class FocusLostEvent implements InputEvent {
    private final NoteBytesReadOnly sourceId;

    public FocusLostEvent(NoteBytesReadOnly sourceId) {
        this.sourceId = sourceId;
    }

    @Override
    public NoteBytesReadOnly getSourceId() { return sourceId; }
}

