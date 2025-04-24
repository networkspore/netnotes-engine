package io.netnotes.engine.adapters.notes;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public interface NoteListener {
    EventHandler<WorkerStateEvent> getChangeHandler();

}
