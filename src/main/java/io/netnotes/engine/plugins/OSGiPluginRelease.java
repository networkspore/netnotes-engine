package io.netnotes.engine.plugins;


import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.utils.github.GitHubAsset;

public class OSGiPluginRelease {    
    
    private final GitHubAsset m_asset;
    private final OSGiPluginInformation m_appInfo;
    
    private final NoteStringArrayReadOnly m_pluginPath;
    private final String m_pluginId;
    private CompletableFuture<NoteFile> m_noteFileFuture = null;



    public OSGiPluginRelease(OSGiPluginInformation pluginInfo, GitHubAsset asset) {
        m_appInfo = pluginInfo;
        m_asset = asset;
        m_pluginPath = new NoteStringArrayReadOnly(
            OSGiPluginRegistry.PLUGINS,
            m_appInfo.getName(),
            m_asset.getTagName(),
            m_asset.getNodeId()
        );
        m_pluginId = m_pluginPath.getAsString();
    }

    public NoteStringArrayReadOnly getPluginNotePath(){
        return m_pluginPath;
    }

    public String getPluginId(){
        return m_pluginId;
    }

    public NoteStringArrayReadOnly getImageNotePath(){
        return m_appInfo.getImageNotePath();
    }

    public NoteStringArrayReadOnly getSmallImageNotePath(){
        return m_appInfo.getSmallImageNotePath();
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

    

    public CompletableFuture<NoteFile> getPluginNoteFile(AppDataInterface appData){
        if(m_noteFileFuture == null){
            m_noteFileFuture = appData.getNoteFile(getPluginNotePath());
            return m_noteFileFuture;
        }else{
            return m_noteFileFuture;
        }
    }



    public CompletableFuture<Void> shutdown(){
        if(m_noteFileFuture != null){
            if(m_noteFileFuture.isDone()){
                m_noteFileFuture.join().close();
                m_noteFileFuture = null;
                return CompletableFuture.completedFuture(null);
            }else{
                CompletableFuture<NoteFile> noteFile = m_noteFileFuture;
                m_noteFileFuture = null;
                return noteFile.handle((result, ex) -> {
                    
                    if (ex != null) {
                        System.err.println("Shutdown failed: " + ex.getMessage());
                    }else{
                        result.close();
                    }
                    return null;
                });
            }
            
        }else{
            return CompletableFuture.completedFuture(null);
        }
    }
}