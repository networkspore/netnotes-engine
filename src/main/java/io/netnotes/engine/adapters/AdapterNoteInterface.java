package io.netnotes.engine.adapters;

import java.util.concurrent.Future;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public interface AdapterNoteInterface {

    String getAdapterId();

    Future<?> sendNote(String adapterId, String note, EventHandler<WorkerStateEvent> onReply, EventHandler<WorkerStateEvent> onFailed);

    int getConnectionStatus();

    String getName();

    String getDescription();

    void addMsgListener(AdapterMsgInterface listener);

    boolean removeMsgListener(AdapterMsgInterface listener);

    boolean isEnabled();
}
