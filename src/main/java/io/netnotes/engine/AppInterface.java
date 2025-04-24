package io.netnotes.engine;

public interface AppInterface {
    void shutdown();
    String[] getDefaultAppIds();
    String[] getDefaultNetworkIds();
    String[] getDefaultAdapters();
    Network createApp(String networkId, String locationId);
    Network createNetwork(String networkId, String locationId);
    Network createAdapter(String networkId);
    NetworkInformation[] getSupportedNetworks();
    NetworkInformation[] getSupportedApps();
    NetworkInformation[] getSupportedAdapters();
    Version getCurrentVersion();
    String getGitHubUser();
    String getGitHubProject();
}
