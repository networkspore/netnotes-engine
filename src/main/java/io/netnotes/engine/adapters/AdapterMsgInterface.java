package io.netnotes.engine.adapters;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public interface AdapterMsgInterface {
    String getId();
    void msgReceived(String msg, long timeStamp, Runnable onRead, Runnable onRetry, EventHandler<WorkerStateEvent> onFailed);
}
