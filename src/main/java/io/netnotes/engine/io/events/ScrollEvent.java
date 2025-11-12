package io.netnotes.engine.io.events;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public final class ScrollEvent implements InputEvent {
    private final NoteBytesReadOnly sourceId;
    private final double xOffset;
    private final double yOffset;
    private final double mouseX;
    private final double mouseY;
    private final int stateFlags;

    public ScrollEvent(NoteBytesReadOnly sourceId, double xOffset, double yOffset, double mouseX, double mouseY, int stateFlags) {
        this.sourceId = sourceId;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.stateFlags = stateFlags;
    }

    @Override
    public NoteBytesReadOnly getSourceId() { return sourceId; }

    public double xOffset() { return xOffset; }
    public double yOffset() { return yOffset; }
    public double mouseX() { return mouseX; }
    public double mouseY() { return mouseY; }
    public int stateFlags() { return stateFlags; }
}
