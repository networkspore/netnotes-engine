package io.netnotes.engine;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public interface SimpleNoteInterface {

    
    boolean sendNote(NoteBytesObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed);

    Object sendNote(NoteBytesObject note);

    void sendMessage(int code, long timeStamp,NoteBytes networkId, String str);


    void shutdown();

}
