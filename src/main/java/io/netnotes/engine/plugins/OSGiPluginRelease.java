package io.netnotes.engine.plugins;


import io.netnotes.engine.utils.github.GitHubAsset;

public class OSGiPluginRelease {    
    
    private final GitHubAsset m_asset;
    private final OSGiPluginInformation m_appInfo;
    

    public OSGiPluginRelease(OSGiPluginInformation pluginInfo, GitHubAsset asset) {
        m_appInfo = pluginInfo;
        m_asset = asset;
    }
    
    public OSGiPluginInformation getPluginInfo() {
        return m_appInfo;
    }
    
    public GitHubAsset getAsset() {
        return m_asset;
    }

    public String getTagName() {
        return m_asset.getTagName();
    }
    
    public String getDownloadUrl() {
        return m_asset.getUrl();
    }
    
    public long getSize() {
        return m_asset.getSize();
    }
}