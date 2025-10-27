package io.netnotes.engine.plugins;


import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.utils.github.GitHubAsset;

public class OSGiPluginRelease {    
    
    private final GitHubAsset m_asset;
    private final OSGiPluginInformation m_appInfo;
    

    public OSGiPluginRelease(OSGiPluginInformation pluginInfo, GitHubAsset asset) {
        m_appInfo = pluginInfo;
        m_asset = asset;
    }

    public NoteStringArrayReadOnly createAssetPath(){
        return new NoteStringArrayReadOnly(
            OSGiPluginRegistry.PLUGINS,
            m_appInfo.getName(),
            m_asset.getTagName(),
            m_asset.getNodeId()
        );
    }

    public String getAppName(){
        return m_appInfo.getName();
    }

    public String getAssetName(){
        return m_asset.getName();
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

    public static OSGiPluginRelease of(NoteBytesMap map){
        NoteBytes asset = map.getByString("asset");
        NoteBytes pluginInfo = map.getByString("pluginInfo");

        return new OSGiPluginRelease(OSGiPluginInformation.of(pluginInfo.getAsNoteBytesMap()), GitHubAsset.of( asset.getAsNoteBytesMap()));
    }

    public NoteBytesObject getNoteBytesObject(){
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("asset", m_asset.getNoteBytesObject()),
            new NoteBytesPair("pluginInfo", m_appInfo.getNoteBytesObject())
        });
    }
}