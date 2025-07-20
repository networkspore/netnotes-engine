package io.netnotes.engine;

import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;

public interface NetworksDataInterface {
    void sendNote(String toId, PipedOutputStream nbObject);

    ExecutorService getExecService();    

}
