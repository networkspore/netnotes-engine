package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;

public final class MouseMoveEvent implements RoutedEvent {
    private final ContextPath sourceId;
    private final double x;
    private final double y;
    private final int stateFlags;

    public MouseMoveEvent(ContextPath sourceId, double x, double y, int stateFlags) {
        this.sourceId = sourceId;
        this.x = x;
        this.y = y;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourceId; }
    public double x() { return x; }
    public double y() { return y; }
    public int stateFlags() { return stateFlags; }
}
