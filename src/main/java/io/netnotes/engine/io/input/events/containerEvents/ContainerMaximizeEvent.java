package io.netnotes.engine.io.input.events.containerEvents;


import io.netnotes.engine.io.ContextPath;
import io.netnotes.noteBytes.NoteBytesReadOnly;

public final class ContainerMaximizeEvent extends RoutedContainerEvent {
    private final ContextPath sourcePath;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;

    public ContainerMaximizeEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags) {
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
