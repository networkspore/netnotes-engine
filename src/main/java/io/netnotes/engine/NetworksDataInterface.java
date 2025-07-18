package io.netnotes.engine;

import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;

public interface NetworksDataInterface {
    void sendNote(NoteBytes toId, PipedOutputStream nbObject);

    ExecutorService getExecService();    

}
