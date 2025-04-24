package io.netnotes.engine.adapters;

import java.util.concurrent.Future;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public interface AdapterNoteInterface {

    Future<?> sendNote(String adapterId, String note, EventHandler<WorkerStateEvent> onReply, EventHandler<WorkerStateEvent> onFailed);
}
