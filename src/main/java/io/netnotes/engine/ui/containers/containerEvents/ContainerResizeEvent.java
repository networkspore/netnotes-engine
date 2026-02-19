package io.netnotes.engine.ui.containers.containerEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.noteBytes.NoteBytesReadOnly;

public abstract class ContainerResizeEvent<
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>
> extends RoutedContainerEvent {
    private S region;
    private final ContextPath sourcePath;
    private int stateFlags;
    private final NoteBytesReadOnly typeBytes;
    
    public ContainerResizeEvent(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags, S region) {
        this.sourcePath = sourcePath;
        this.region = region;
        this.stateFlags = flags;
        this.typeBytes = typeBytes;
    }

    
    public S getRegion() { return region; }
   

    public S getAndConsumeRegion() { 
        setConsumed(true);
        S oldRegion = region;
        region = null;
        return oldRegion;
    }
    
    @Override
    public String toString() {
        return String.format("ContainerResizeEvent[%s, source=%s]", region.toString(), getSourcePath());
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