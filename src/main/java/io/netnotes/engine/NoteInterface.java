package io.netnotes.engine;

import java.util.concurrent.Future;

import com.google.gson.JsonObject;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public interface NoteInterface {

    String getNetworkId();

    Future<?> sendNote(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed);

    void addMsgListener(NoteMsgInterface listener);

    boolean removeMsgListener(NoteMsgInterface listener);


}
