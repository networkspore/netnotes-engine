package io.netnotes.engine;

import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public interface NetworksDataInterface {
    void sendNote(NoteBytes toId, PipedOutputStream sendData, PipedOutputStream replyData, EventHandler<WorkerStateEvent> onFailed);
    void sendNote(NoteStringArrayReadOnly toId, PipedOutputStream sendData, PipedOutputStream replyData, EventHandler<WorkerStateEvent> onFailed);

    CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path);
    
    ExecutorService getExecService();    

}
