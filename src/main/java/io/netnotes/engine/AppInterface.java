package io.netnotes.engine;

import java.io.IOException;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.utils.Version;
import javafx.stage.Stage;

public interface AppInterface {
    void shutdown();
    NoteBytes[] getDefaultNodeIds();
    Node createNode(NoteBytes nodeId);
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
}
