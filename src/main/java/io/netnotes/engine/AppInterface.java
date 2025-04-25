package io.netnotes.engine;

import java.io.IOException;

import io.netnotes.engine.adapters.Adapter;

public interface AppInterface {
    void shutdown();
    String[] getDefaultAppIds();
    String[] getDefaultNetworkIds();
    String[] getDefaultAdapters();
    Network createApp(String networkId, String locationId);
    Network createNetwork(String networkId, String locationId);
    Adapter createAdapter(String networkId);
    NetworkInformation[] getSupportedNetworks();
    NetworkInformation[] getSupportedApps();
    NetworkInformation[] getSupportedAdapters();
    Version getCurrentVersion();
    String getGitHubUser();
    String getGitHubProject();
    void removeAppResource(String resource) throws IOException;
    void addAppResource(String resource) throws IOException;
}
