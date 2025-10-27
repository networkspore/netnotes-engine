package io.netnotes.engine.plugins;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

public class OSGiPluginInformation {

    private String m_appId;
    private String m_pluginName;
    private String m_iconUrl;
    private String m_smallIconUrl;
    private String m_description;
    private OSGiAvailablePluginFileInfo m_gitHubJar;

    public OSGiPluginInformation(String appId, String pluginName, String iconUrl, String smallIconUrl, String description, OSGiAvailablePluginFileInfo gitHubJar){
        m_appId = appId;
        m_pluginName = pluginName;
        m_iconUrl = iconUrl;
        m_smallIconUrl = smallIconUrl;
        m_description = description;
        m_gitHubJar = gitHubJar;
    }

    public String getDescription(){
        return m_description;
    }

    public String getAppId(){
        return m_appId;
    }

    public String getName(){
        return m_pluginName;
    }

    public String getIcon(){
        return m_iconUrl;
    }

    public String getSmallIcon(){
        return m_smallIconUrl;
    }

    public OSGiAvailablePluginFileInfo getGitHubJar(){
        return m_gitHubJar;
    }

    public JsonObject getJsonObject() {
        JsonObject json = new JsonObject();

        if (m_appId != null) json.addProperty("appId", m_appId);
        json.addProperty("pluginName", m_pluginName);
        json.addProperty("iconUrl", m_iconUrl);
        json.addProperty("smallIconUrl", m_smallIconUrl);
        json.addProperty("description", m_description);
        json.add("gitHubFiles", m_gitHubJar.getJsonObject());
        

        return json;
    }

    public NoteBytesObject getNoteBytesObject() {

        NoteBytesPair appId = new NoteBytesPair("appId", m_appId);
        NoteBytesPair pluginName = new NoteBytesPair("pluginName", m_pluginName);
        NoteBytesPair iconUrl = new NoteBytesPair("iconUrl", m_iconUrl);
        NoteBytesPair smallIconUrl = new NoteBytesPair("smallIconUrl", m_smallIconUrl);
        NoteBytesPair description = new NoteBytesPair("description", m_description);
        NoteBytesPair gitHubJar = new NoteBytesPair("gitHubJar", m_gitHubJar.getNoteBytesObject());

        return new NoteBytesObject(new NoteBytesPair[]{
            appId,
            pluginName,
            iconUrl,
            smallIconUrl,
            description,
            gitHubJar
        });
    }

     public static OSGiPluginInformation of(NoteBytesMap map) {
        if (map == null) return null;

        String appId = map.has("appId") ? 
            map.getByString("appId").getAsString() : null;
        String pluginName = map.has("pluginName") ? map.getByString("pluginName").getAsString() : null;
        String iconUrl = map.has("iconUrl") ? map.getByString("iconUrl").getAsString() : null;
        String smallIconUrl = map.has("smallIconUrl") ? map.getByString("smallIconUrl").getAsString() : iconUrl;
        String description = map.has("description") ? description = map.getByString("description").getAsString() : null;
        OSGiAvailablePluginFileInfo gitHubJar = map.has("gitHubJar") ? 
            OSGiAvailablePluginFileInfo.of(map.getByString("gitHubJar").getAsNoteBytesMap()) : null;

        if(appId == null || pluginName == null || iconUrl == null ||  smallIconUrl == null || description == null 
            || gitHubJar == null)
        {
            throw new IllegalArgumentException("Corrupt file info");
        }

        return new OSGiPluginInformation(appId, pluginName, iconUrl, smallIconUrl, description, gitHubJar);
    }

    public static OSGiPluginInformation of(JsonObject json) {
        if (json == null) return null;

        String appId = json.has("appId") && !json.get("appId").isJsonNull() ? 
            json.get("appId").getAsString() : null;
        String pluginName = json.has("pluginName") ? json.get("pluginName").getAsString() : null;
        String iconUrl = json.has("iconUrl") ? json.get("iconUrl").getAsString() : null;
        String smallIconUrl = json.has("smallIconUrl") ? json.get("smallIconUrl").getAsString() : iconUrl;
        String description = json.has("description") ? description = json.get("description").getAsString() : null;
        OSGiAvailablePluginFileInfo gitHubJar = json.has("gitHubJar") ? 
            OSGiAvailablePluginFileInfo.of(json.get("gitHubJar").getAsJsonObject()) : null;

        if(appId == null || pluginName == null || iconUrl == null ||  smallIconUrl == null || description == null 
            || gitHubJar == null)
        {
            throw new IllegalArgumentException("Corrupt file info");
        }

        return new OSGiPluginInformation(appId, pluginName, iconUrl, smallIconUrl, description, gitHubJar);
    }

    public static OSGiPluginInformation read(JsonReader reader) throws IOException {
        String appId = null;
        String pluginName = null;
        String iconUrl = null;
        String smallIconUrl = null;
        String description = null;
        OSGiAvailablePluginFileInfo gitHubJar = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "appId":
                    appId = reader.nextString();
                    break;
                case "pluginName":
                    pluginName = reader.nextString();
                    break;
                case "iconUrl":
                    iconUrl = reader.nextString();
                    break;
                case "smallIconUrl":
                    smallIconUrl = reader.nextString();
                    break;
                case "description":
                    description = reader.nextString();
                    break;
                case "gitHubJar":
                    gitHubJar = OSGiAvailablePluginFileInfo.read(reader);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        smallIconUrl = smallIconUrl == null ? iconUrl : smallIconUrl;

        if(appId == null || pluginName == null || iconUrl == null ||  smallIconUrl == null || description == null 
            || gitHubJar == null)
        {
            throw new IOException("Corrupt file info");
        }

        return new OSGiPluginInformation(appId, pluginName, iconUrl, smallIconUrl, description, gitHubJar);
    }

    public void write(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("appId").value(m_appId != null ? m_appId : null);
        writer.name("pluginName").value(m_pluginName);
        writer.name("iconUrl").value(m_iconUrl);
        writer.name("smallIconUrl").value(m_smallIconUrl);
        writer.name("description").value(m_description);
        writer.name("gitHubJar");
        m_gitHubJar.write(writer);
        writer.endObject();
    }
    
}