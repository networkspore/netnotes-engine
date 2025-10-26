package io.netnotes.engine.plugins;

import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.utils.github.GitHubFileInfo;

public class OSGiPluginInformation {

    private NoteBytes m_appId;
    private String m_pluginName;
    private String m_iconUrl;
    private String m_smallIconUrl;
    private String m_description;
    private GitHubFileInfo[] m_gitHubFiles;

    public OSGiPluginInformation(NoteBytes appId, String pluginName, String iconUrl, String smallIconUrl, String description, GitHubFileInfo... gitHubFiles){
        m_appId = appId;
        m_pluginName = pluginName;
        m_iconUrl = iconUrl;
        m_smallIconUrl = smallIconUrl;
        m_description = description;
        m_gitHubFiles = gitHubFiles;
    }

    public String getDescription(){
        return m_description;
    }

    public NoteBytes getAppId(){
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

    public GitHubFileInfo[] getGitHubFiles(){
        return m_gitHubFiles;
    }

    public JsonObject getJsonObject() {
        JsonObject json = new JsonObject();

        if (m_appId != null) json.addProperty("appId", m_appId.getAsString());
        json.addProperty("pluginName", m_pluginName);
        json.addProperty("iconUrl", m_iconUrl);
        json.addProperty("smallIconUrl", m_smallIconUrl);
        json.addProperty("description", m_description);

        if (m_gitHubFiles != null) {
            JsonArray array = new JsonArray();
            for (GitHubFileInfo fileInfo : m_gitHubFiles) {
                if (fileInfo != null) array.add(fileInfo.getJsonObject());
            }
            json.add("gitHubFiles", array);
        }

        return json;
    }

    public static OSGiPluginInformation of(JsonObject json) {
        if (json == null) return null;

        NoteBytes appId = json.has("appId") && !json.get("appId").isJsonNull() ?
            new NoteBytes(json.get("appId").getAsString()) : null;
        String pluginName = json.has("pluginName") ? json.get("pluginName").getAsString() : null;
        String iconUrl = json.has("iconUrl") ? json.get("iconUrl").getAsString() : null;
        String smallIconUrl = json.has("smallIconUrl") ? json.get("smallIconUrl").getAsString() : iconUrl;
        String description = json.has("description") ? description = json.get("description").getAsString() : null;
        GitHubFileInfo[] gitHubFiles = null;

        if (json.has("gitHubFiles") && json.get("gitHubFiles").isJsonArray()) {
            var array = json.getAsJsonArray("gitHubFiles");
            gitHubFiles = new GitHubFileInfo[array.size()];
            for (int i = 0; i < array.size(); i++) {
                gitHubFiles[i] = GitHubFileInfo.of(array.get(i).getAsJsonObject());
            }
        }

        if(appId == null || pluginName == null || iconUrl == null ||  smallIconUrl == null || description == null 
            || gitHubFiles == null)
        {
            throw new IllegalArgumentException("Corrupt file info");
        }

        return new OSGiPluginInformation(appId, pluginName, iconUrl, smallIconUrl, description, gitHubFiles);
    }

    public static OSGiPluginInformation read(JsonReader reader) throws IOException {
        NoteBytes appId = null;
        String pluginName = null;
        String iconUrl = null;
        String smallIconUrl = null;
        String description = null;
        GitHubFileInfo[] gitHubFiles = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "appId":
                    appId = new NoteBytes(reader.nextString());
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
                case "gitHubFiles":
                    reader.beginArray();
                    var files = new java.util.ArrayList<GitHubFileInfo>();
                    while (reader.hasNext()) {
                        files.add(GitHubFileInfo.read(reader));
                    }
                    reader.endArray();
                    gitHubFiles = files.toArray(new GitHubFileInfo[0]);
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        smallIconUrl = smallIconUrl == null ? iconUrl : smallIconUrl;

        if(appId == null || pluginName == null || iconUrl == null ||  smallIconUrl == null || description == null 
            || gitHubFiles == null)
        {
            throw new IOException("Corrupt file info");
        }

        return new OSGiPluginInformation(appId, pluginName, iconUrl, smallIconUrl, description, gitHubFiles);
    }

    public void write(JsonWriter writer) throws IOException {
        writer.beginObject();

        writer.name("appId").value(m_appId != null ? m_appId.getAsString() : null);
        writer.name("pluginName").value(m_pluginName);
        writer.name("iconUrl").value(m_iconUrl);
        writer.name("smallIconUrl").value(m_smallIconUrl);
        writer.name("description").value(m_description);

        writer.name("gitHubFiles");
        if (m_gitHubFiles != null) {
            writer.beginArray();
            for (GitHubFileInfo fileInfo : m_gitHubFiles) {
                if (fileInfo != null) fileInfo.write(writer);
            }
            writer.endArray();
        } else {
            writer.nullValue();
        }

        writer.endObject();
    }
    
}