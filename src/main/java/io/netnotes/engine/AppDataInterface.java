package io.netnotes.engine;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteFile.NoteFile;
import io.netnotes.engine.utils.Version;
import javafx.stage.Stage;

public interface AppDataInterface {
    void shutdown();
    NoteBytes[] getDefaultNodeIds();
    NodeInformation[] getSupportedNodes();
    boolean isNodeSupported(NoteBytes nodeId);
    Version getCurrentVersion();
    String getGitHubUser();
    String getGitHubProject();
    void removeAppResource(String resource) throws IOException;
    void addAppResource(String resource) throws IOException;
    HostServicesInterface getHostServices();
    AppData getAppData();
    NoteBytes getCurrentGUINodeId();
    Stage getStage();
    ExecutorService getExecService();
    CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path);
        
}
