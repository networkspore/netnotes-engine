package io.netnotes.engine.io.input.events.containers;


import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public final class ContainerHiddenEvent implements RoutedEvent {
    private final ContextPath sourcePath;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;

    public ContainerHiddenEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags) {
        this.sourcePath = sourcePath;
        this.stateFlags = flags;
        this.typeBytes = typeBytes;
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
