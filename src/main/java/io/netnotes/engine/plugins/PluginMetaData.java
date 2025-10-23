package io.netnotes.engine.plugins;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

public class PluginMetaData {
    public static final NoteBytes NOTE_PATH_KEY = new NoteBytes("path");
    public static final NoteBytes VERSION_KEY = new NoteBytes("version");
    public static final NoteBytes ENABLED_KEY = new NoteBytes("enabled");
    public static final NoteBytes PLUGIN_ID_KEY = new NoteBytes("id");

    private NoteStringArrayReadOnly m_notePath;
    private String m_version;
    private boolean m_enabled;
    private final NoteBytes m_id;


    public PluginMetaData( NoteBytes id, String version, boolean enabled, NoteStringArrayReadOnly path){
        m_id = id;
        m_notePath = path;
        m_version = version;
        m_enabled = enabled;
    }


    public NoteBytes getPluginId() {
        return m_id;
    }

    public NoteStringArrayReadOnly geNotePath() {
        return m_notePath;
    }

    public String getVersion() {
        return m_version;
    }

    public boolean isEnabled() {
        return m_enabled;
    }

    public void setNotePath(NoteStringArrayReadOnly notePath) {
        this.m_notePath = notePath;
    }

    public void setVersion(String version) {
        this.m_version = version;
    }

    public void setEnabled(boolean enabled) {
        this.m_enabled = enabled;
    }


    public NoteBytesPair getSaveData(){
        NoteBytesMap map = new NoteBytesMap();

        map.put(ENABLED_KEY, NoteBytes.of(m_enabled));
        map.put(VERSION_KEY, NoteBytes.of(m_version));
        map.put(NOTE_PATH_KEY, m_notePath);
   
        return new NoteBytesPair(m_id, map.getNoteBytesObject());
    }
}
