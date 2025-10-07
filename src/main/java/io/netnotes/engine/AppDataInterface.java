package io.netnotes.engine;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteFiles.NoteFile;

public interface AppDataInterface {
    void shutdown();
    NoteBytes[] getDefaultNodeIds();
    boolean isNodeSupported(NoteBytes nodeId);

    HostServicesInterface getHostServices();
    NoteBytes getCurrentGUINodeId();
    ExecutorService getExecService();
    CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path);
        
}
