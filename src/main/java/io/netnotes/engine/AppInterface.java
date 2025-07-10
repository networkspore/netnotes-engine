package io.netnotes.engine;

import java.io.IOException;

import io.netnotes.engine.adapters.Adapter;

public interface AppInterface {
    void shutdown();
    NoteBytes[] getDefaultAppIds();
    NoteBytes[] getDefaultNetworkIds();
    NoteBytes[] getDefaultAdapters();
    Network createApp(NoteBytes networkId);
    Network createNetwork(NoteBytes networkId);
    Adapter createAdapter(NoteBytes networkId);
    NetworkInformation[] getSupportedNetworks();
    NetworkInformation[] getSupportedApps();
    NetworkInformation[] getSupportedAdapters();
    Version getCurrentVersion();
    String getGitHubUser();
    String getGitHubProject();
    void removeAppResource(String resource) throws IOException;
    void addAppResource(String resource) throws IOException;
}
