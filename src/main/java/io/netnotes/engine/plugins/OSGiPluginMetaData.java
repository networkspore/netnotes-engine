package io.netnotes.engine.plugins;


import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteFiles.NoteFile;

public class OSGiPluginMetaData {

    public static final NoteBytes ENABLED_KEY = new NoteBytes("enabled");
    public static final NoteBytes DATA_KEY = new NoteBytes("data");

    private final NoteStringArrayReadOnly m_notePath;
    private final String m_pluginId;
    private final OSGiPluginRelease m_release;
    private boolean m_enabled;

    private CompletableFuture<NoteFile> m_noteFileFuture = null;


    public OSGiPluginMetaData(OSGiPluginRelease release,boolean enabled){
        m_notePath = release.createNotePath();
        m_pluginId = m_notePath.getAsString();
        m_release = release;
        m_enabled = enabled;
    }

    public NoteStringArrayReadOnly getNotePath(){
        return m_notePath;
    }

    

    public CompletableFuture<NoteFile> getNoteFile(AppDataInterface appData){
        if(m_noteFileFuture == null){
            m_noteFileFuture = appData.getNoteFile(m_notePath);
            return m_noteFileFuture;
        }else{
            return m_noteFileFuture;
        }
    }



    public String getPluginId() {
        return m_pluginId;
    }

    public String getName(){
        return m_release.getAppName();
    }


    public boolean isEnabled() {
        return m_enabled;
    }

    public void setEnabled(boolean enabled) {
        this.m_enabled = enabled;
    }

    public OSGiPluginRelease getRelease(){
        return m_release;
    }

    @Override
    public String toString(){
        return m_release.getAssetName();
    }

    public static OSGiPluginMetaData of(NoteBytesMap map){
        NoteBytes enabled = map.get(ENABLED_KEY);
        NoteBytes data = map.get(DATA_KEY);
        
        OSGiPluginRelease release = OSGiPluginRelease.of(data.getAsNoteBytesMap());
        
        return new OSGiPluginMetaData(release, enabled.getAsBoolean());

    }


    public NoteBytesPair getSaveData(){
        return new NoteBytesPair(getPluginId(), new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(ENABLED_KEY, NoteBytes.of(m_enabled)),
            new NoteBytesPair(DATA_KEY, m_release.getNoteBytesObject())
            
        }));
    }

    public CompletableFuture<Void> shutdown(){
        if(m_noteFileFuture != null){
            if(m_noteFileFuture.isDone()){
                m_noteFileFuture.join().close();
                m_noteFileFuture = null;
                return CompletableFuture.completedFuture(null);
            }else{
                return m_noteFileFuture .handle((result, ex) -> {
                    
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
