package io.netnotes.engine.plugins;


import io.netnotes.engine.utils.github.GitHubAsset;

public class OSGiPluginRelease {
    private final String m_version;
    private final String m_tagName;
    private final GitHubAsset m_asset;
    private final OSGiPluginInformation m_appInfo;
    private final long m_publishedDate;
    
    public OSGiPluginRelease(OSGiPluginInformation pluginInfo, GitHubAsset asset, String version, 
                     String tagName, long publishedDate) {
        m_appInfo = pluginInfo;
        m_asset = asset;
        m_version = version;
        m_tagName = tagName;
        m_publishedDate = publishedDate;
    }
    
    public OSGiPluginInformation getPluginInfo() {
        return m_appInfo;
    }
    
    public GitHubAsset getAsset() {
        return m_asset;
    }
    
    public String getVersion() {
        return m_version;
    }
    
    public String getTagName() {
        return m_tagName;
    }
    
    public long getPublishedDate() {
        return m_publishedDate;
    }
    
    public String getDownloadUrl() {
        return m_asset.getUrl();
    }
    
    public long getSize() {
        return m_asset.getSize();
    }
}