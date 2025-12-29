package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public final class ScrollEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private final double xOffset;
    private final double yOffset;
    private final double mouseX;
    private final double mouseY;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;

    public ScrollEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int stateFlags,  double xOffset, double yOffset, double mouseX, double mouseY) {
        this.sourcePath = sourcePath;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.stateFlags = stateFlags;
        this.typeBytes = typeBytes;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }

    public double xOffset() { return xOffset; }
    public double yOffset() { return yOffset; }
    public double mouseX() { return mouseX; }
    public double mouseY() { return mouseY; }
    @Override
    public int getStateFlags() { return stateFlags; }

    public void setStateFlags(int flags) { stateFlags = flags; }
    @Override
    public NoteBytesReadOnly getEventTypeBytes(){
        return typeBytes;
    }
}
