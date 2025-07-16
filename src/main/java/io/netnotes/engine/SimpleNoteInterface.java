package io.netnotes.engine;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public interface SimpleNoteInterface {

    
    boolean sendNote(NoteBytesObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed);

    Object sendNote(NoteBytesObject note);

    void sendMessage(int code, long timeStamp,NoteBytes networkId, String str);


    void shutdown();

}
