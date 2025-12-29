package io.netnotes.engine.io.input.events.containers;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public class ContainerResizeEvent implements RoutedEvent {
    private final int width;
    private final int height;
    private final ContextPath sourcePath;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;
    
    public ContainerResizeEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags, int width, int height) {
        this.sourcePath = sourcePath;
        this.width = width;
        this.height = height;
        this.stateFlags = flags;
        this.typeBytes = typeBytes;
    }
    
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    
    @Override
    public String toString() {
        return String.format("ContainerResizeEvent[%dx%d, source=%s]", 
            width, height, getSourcePath());
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }

    @Override
    public NoteBytesReadOnly getEventTypeBytes() {
        return typeBytes;
    }

     @Override
    public int getStateFlags() {
        return stateFlags;
    }
    @Override
    public void setStateFlags(int flags) {
        stateFlags = flags;
    }
}