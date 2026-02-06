package io.netnotes.engine.io.input.events.mouseEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public final class MouseButtonUpEvent extends RoutedMouseEvent {
    private final ContextPath sourcePath;
    private final int button;
    private final double x;
    private final double y;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;

    public MouseButtonUpEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes , int stateFlags, int button, double x, double y) {
        this.sourcePath = sourcePath;
        this.button = button;
        this.x = x;
        this.y = y;
        this.stateFlags = stateFlags;
        this.typeBytes = typeBytes;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public int button() { return button; }
    public double x() { return x; }
    public double y() { return y; }
    @Override
    public int getStateFlags() { return stateFlags; }

    public void setStateFlags(int flags) { stateFlags = flags; }
    @Override
    public NoteBytesReadOnly getEventTypeBytes() { return typeBytes; }
}

