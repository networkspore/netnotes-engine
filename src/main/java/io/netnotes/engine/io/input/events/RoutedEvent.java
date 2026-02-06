package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;

public abstract class RoutedEvent {
    private boolean isConsumed = false;
    public boolean isConsumed() { return isConsumed; }
    public void setConsumed(boolean isConsumed) { this.isConsumed = isConsumed; }
    public abstract ContextPath getSourcePath();
    public abstract NoteBytes getEventTypeBytes();
    public abstract int getStateFlags();
    public abstract void setStateFlags(int flags);
}