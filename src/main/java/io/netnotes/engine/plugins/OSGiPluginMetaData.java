package io.netnotes.engine.plugins;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

public class OSGiPluginMetaData {

    public static final NoteBytes ENABLED_KEY = new NoteBytes("enabled");
    public static final NoteBytes DATA_KEY = new NoteBytes("data");

    private final NoteStringArrayReadOnly m_notePath;
    private final String m_pluginId;
    private final OSGiPluginRelease m_release;
    private boolean m_enabled;


    public OSGiPluginMetaData(OSGiPluginRelease release, boolean enabled){
        m_notePath = release.createAssetPath();
        m_pluginId = m_notePath.getHash32();
        m_release = release;
        m_enabled = enabled;
    }


    public String getPluginId() {
        return m_pluginId;
    }

    public String getName(){
        return m_release.getAppName();
    }

    public NoteStringArrayReadOnly geNotePath() {
        return m_notePath;
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

        return new OSGiPluginMetaData(OSGiPluginRelease.of(data.getAsNoteBytesMap()), enabled.getAsBoolean());
    }


    public NoteBytesPair getSaveData(){
        return new NoteBytesPair(getPluginId(), new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(ENABLED_KEY, NoteBytes.of(m_enabled)),
            new NoteBytesPair(DATA_KEY, m_release.getNoteBytesObject())
            
        }));
    }
}
