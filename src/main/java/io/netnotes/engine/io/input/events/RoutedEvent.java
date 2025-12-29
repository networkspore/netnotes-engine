package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;

public interface RoutedEvent {

    ContextPath getSourcePath();
    NoteBytes getEventTypeBytes();
    int getStateFlags();
    void setStateFlags(int flags);
}