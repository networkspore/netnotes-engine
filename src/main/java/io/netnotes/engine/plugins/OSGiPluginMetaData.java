package io.netnotes.engine.plugins;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.AppDataInterface;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteFiles.NoteFile;

public class OSGiPluginMetaData {

    public static final NoteBytes ENABLED_KEY = new NoteBytes("enabled");
    public static final NoteBytes DATA_KEY = new NoteBytes("data");

    private final NoteFile m_noteFile;
    private final String m_pluginId;
    private final OSGiPluginRelease m_release;
    private boolean m_enabled;


    public OSGiPluginMetaData(OSGiPluginRelease release, NoteFile noteFile, boolean enabled){
        m_noteFile = noteFile;
        m_pluginId = noteFile.getUrlPathString();
        m_release = release;
        m_enabled = enabled;
    }

    public NoteFile getNoteFile(){
        return m_noteFile;
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

    public static CompletableFuture<OSGiPluginMetaData> of(NoteBytesMap map, AppDataInterface appData){
        NoteBytes enabled = map.get(ENABLED_KEY);
        NoteBytes data = map.get(DATA_KEY);
        
        OSGiPluginRelease release = OSGiPluginRelease.of(data.getAsNoteBytesMap());
        
        return appData.getNoteFile(release.createNotePath()).thenCompose(noteFile->CompletableFuture.completedFuture(new OSGiPluginMetaData(release,noteFile, enabled.getAsBoolean())));

    }


    public NoteBytesPair getSaveData(){
        return new NoteBytesPair(getPluginId(), new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(ENABLED_KEY, NoteBytes.of(m_enabled)),
            new NoteBytesPair(DATA_KEY, m_release.getNoteBytesObject())
            
        }));
    }
}
