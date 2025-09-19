package io.netnotes.engine.plugins;

import java.io.IOException;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

public class PluginMetaData {
    public static final NoteBytes NOTE_PATH_KEY = new NoteBytes("path");
    public static final NoteBytes VERSION_KEY = new NoteBytes("version");
    public static final NoteBytes ENABLED_KEY = new NoteBytes("enabled");
    public static final NoteBytes PLUGIN_ID_KEY = new NoteBytes("id");

    private NoteStringArrayReadOnly m_notePath;
    private String m_version;
    private boolean m_enabled;
    private NoteBytesReadOnly m_id;

    public PluginMetaData(NoteBytes noteBytes){
        this(new NoteBytesMap(noteBytes));
    }
    public PluginMetaData(NoteBytesMap map){

        NoteBytes enabledValue = map.get(ENABLED_KEY);
        NoteBytes versionValue = map.get(VERSION_KEY);
        NoteBytes pathValue = map.get(NOTE_PATH_KEY);
        NoteBytes idValue = map.get(PLUGIN_ID_KEY);

        if(idValue == null){
            throw new RuntimeException("PluginMetaData missing key value");
        }

        m_notePath = new NoteStringArrayReadOnly(pathValue);
        m_version = versionValue.getAsString();
        m_enabled = enabledValue.getAsBoolean();
        m_id = new NoteBytesReadOnly(idValue);
    }

    public PluginMetaData(String version, boolean enabled, NoteStringArrayReadOnly path){
        m_notePath = path;
        m_version = version;
        m_enabled = enabled;
    }


    public NoteBytesReadOnly getPluginId() {
        return m_id;
    }

    public void setPluginId(NoteBytesReadOnly id) {
        this.m_id = id;
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


    public NoteBytesObject getNoteBytesObject(){
        NoteBytesMap map = new NoteBytesMap();

        map.put(ENABLED_KEY, m_enabled);
        map.put(VERSION_KEY, m_version);
        map.put(NOTE_PATH_KEY, m_notePath);
        map.put(PLUGIN_ID_KEY, m_id);
        return map.getNoteBytesObject();
    }
}
