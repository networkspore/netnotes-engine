package io.netnotes.engine.io.input.events.mouseEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.noteBytes.NoteBytesReadOnly;

public final class MouseMoveEvent extends RoutedMouseEvent {
    private final ContextPath sourceId;
    private final double x;
    private final double y;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;

    public MouseMoveEvent(ContextPath sourceId, NoteBytesReadOnly typeBytes, int stateFlags, double x, double y) {
        this.sourceId = sourceId;
        this.x = x;
        this.y = y;
        this.stateFlags = stateFlags;
        this.typeBytes = typeBytes;
    }

    @Override
    public ContextPath getSourcePath() { return sourceId; }
    public double x() { return x; }
    public double y() { return y; }
    @Override
    public int getStateFlags() { return stateFlags; }

    public void setStateFlags(int flags) { stateFlags = flags; }
    @Override
    public NoteBytesReadOnly getEventTypeBytes(){
        return typeBytes;
    }
}
