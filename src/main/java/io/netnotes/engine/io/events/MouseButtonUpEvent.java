package io.netnotes.engine.io.events;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public final class MouseButtonUpEvent implements InputEvent {
    private final NoteBytesReadOnly sourceId;
    private final int button;
    private final double x;
    private final double y;
    private final int stateFlags;

    public MouseButtonUpEvent(NoteBytesReadOnly sourceId, int button, double x, double y, int stateFlags) {
        this.sourceId = sourceId;
        this.button = button;
        this.x = x;
        this.y = y;
        this.stateFlags = stateFlags;
    }

    @Override
    public NoteBytesReadOnly getSourceId() { return sourceId; }
    public int button() { return button; }
    public double x() { return x; }
    public double y() { return y; }
    public int stateFlags() { return stateFlags; }
}

