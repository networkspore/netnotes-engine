package io.netnotes.engine.io.events;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public final class MouseMoveEvent implements InputEvent {
    private final NoteBytesReadOnly sourceId;
    private final double x;
    private final double y;
    private final int stateFlags;

    public MouseMoveEvent(NoteBytesReadOnly sourceId, double x, double y, int stateFlags) {
        this.sourceId = sourceId;
        this.x = x;
        this.y = y;
        this.stateFlags = stateFlags;
    }

    @Override
    public NoteBytesReadOnly getSourceId() { return sourceId; }
    public double x() { return x; }
    public double y() { return y; }
    public int stateFlags() { return stateFlags; }
}
