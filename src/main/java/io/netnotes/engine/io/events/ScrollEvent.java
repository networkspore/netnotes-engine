package io.netnotes.engine.io.events;

import io.netnotes.engine.io.ContextPath;

public final class ScrollEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private final double xOffset;
    private final double yOffset;
    private final double mouseX;
    private final double mouseY;
    private final int stateFlags;

    public ScrollEvent(ContextPath sourcePath, double xOffset, double yOffset, double mouseX, double mouseY, int stateFlags) {
        this.sourcePath = sourcePath;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.stateFlags = stateFlags;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }

    public double xOffset() { return xOffset; }
    public double yOffset() { return yOffset; }
    public double mouseX() { return mouseX; }
    public double mouseY() { return mouseY; }
    public int stateFlags() { return stateFlags; }
}
