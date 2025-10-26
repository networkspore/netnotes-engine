package io.netnotes.engine.utils.github;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class GitHubInfo {

    private final String m_user;
    private final  String m_project;

    public GitHubInfo(String user, String project){
        m_user = user;
        m_project = project;
    }

    public String getUser() {
        return m_user;
    }


    public String getProject() {
        return m_project;
    }


    public JsonObject getJsonObject(){
        JsonObject json = new JsonObject();
        json.addProperty("user", m_user);
        json.addProperty("project", m_project);
        return json;
    }

    public static GitHubInfo of(JsonObject json) {
        if (json == null) return null;

        String user = json.has("user") && !json.get("user").isJsonNull() ? json.get("user").getAsString() : null;
        String info = json.has("project") && !json.get("project").isJsonNull() ? json.get("project").getAsString() : null;
        
        return user != null && info != null ? new GitHubInfo(user, info) : null;
    }

    public static GitHubInfo read(JsonReader reader) throws IOException {
        String user = null;
        String project = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "user":
                    user = reader.nextString();
                    break;
                case "project":
                    project = reader.nextString();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        return new GitHubInfo(user, project);
    }

     public void write(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("user").value(m_user);
        writer.name("project").value(m_project);
        writer.endObject();
    }
}
