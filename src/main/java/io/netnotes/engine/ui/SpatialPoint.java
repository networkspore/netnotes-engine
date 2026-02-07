package io.netnotes.engine.ui;

import io.netnotes.engine.noteBytes.NoteBytesObject;

public abstract class SpatialPoint<P> {
    
    public abstract P subtract(P point);

    public abstract P add(P point);

    public abstract NoteBytesObject toNoteBytes();
}
