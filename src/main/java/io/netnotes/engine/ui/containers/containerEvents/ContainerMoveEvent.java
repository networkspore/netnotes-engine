package io.netnotes.engine.ui.containers.containerEvents;
    
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.noteBytes.NoteBytesReadOnly;


public abstract class ContainerMoveEvent<P extends SpatialPoint<P>>

extends RoutedContainerEvent {
    private final ContextPath sourcePath;
    private final P point;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;

    public ContainerMoveEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags, P point) {
        this.sourcePath = sourcePath;
        this.point = point;
        this.stateFlags = flags;
        this.typeBytes = typeBytes;
    }

    @Override
    public ContextPath getSourcePath() { return sourcePath; }
    public P getPoint() { return point; }

    
    @Override
    public String toString() {
        return String.format("ContainerMoveEvent[pos=(%s), source=%s]", 
            point.toString(), getSourcePath());
    }

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
