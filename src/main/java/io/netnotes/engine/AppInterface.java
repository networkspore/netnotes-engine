package io.netnotes.engine;

import java.io.IOException;

import io.netnotes.engine.controls.Version;

public interface AppInterface {
    void shutdown();
    String[] getDefaultAppIds();
    String[] getDefaultNetworkIds();
    String[] getDefaultAdapters();
    Network createApp(String networkId);
    Network createNetwork(String networkId);
    NetworkInformation[] getSupportedNetworks();
    NetworkInformation[] getSupportedApps();
    Version getCurrentVersion();
    String getGitHubUser();
    String getGitHubProject();
    void removeAppResource(String resource) throws IOException;
    void addAppResource(String resource) throws IOException;
}
