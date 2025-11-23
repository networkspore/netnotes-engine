package io.netnotes.engine.io.events;

import io.netnotes.engine.io.ContextPath;

public final class MouseButtonUpEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private final int button;
    private final double x;
    private final double y;
    private final int stateFlags;

    public MouseButtonUpEvent(ContextPath sourcePath, int button, double x, double y, int stateFlags) {
        this.sourcePath = sourcePath;
        this.button = button;
        this.x = x;
        this.y = y;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public int button() { return button; }
    public double x() { return x; }
    public double y() { return y; }
    public int stateFlags() { return stateFlags; }
}

