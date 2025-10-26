package io.netnotes.engine.utils.github;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class GitHubUser {
    private String m_login;
    private long m_id;
    private String m_nodeId;
    private String m_url;
    private String m_htmlUrl;
    private String m_type;

    public GitHubUser(String login, long id, String nodeId, String url, String htmlUrl, String type) {
        m_login = login;
        m_id = id;
        m_nodeId = nodeId;
        m_url = url;
        m_htmlUrl = htmlUrl;
        m_type = type;
    }

    // Getters
    public String getLogin() { return m_login; }
    public long getId() { return m_id; }
    public String getNodeId() { return m_nodeId; }
    public String getUrl() { return m_url; }
    public String getHtmlUrl() { return m_htmlUrl; }
    public String getType() { return m_type; }

    // --------------------
    // JSON Serialization
    // --------------------
    public JsonObject getJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("login", m_login);
        json.addProperty("id", m_id);
        json.addProperty("node_id", m_nodeId);
        json.addProperty("url", m_url);
        json.addProperty("html_url", m_htmlUrl);
        json.addProperty("type", m_type);
        return json;
    }

    public void write(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("login").value(m_login);
        writer.name("id").value(m_id);
        writer.name("node_id").value(m_nodeId);
        writer.name("url").value(m_url);
        writer.name("html_url").value(m_htmlUrl);
        writer.name("type").value(m_type);
        writer.endObject();
    }

    // --------------------
    // JSON Deserialization
    // --------------------
    public static GitHubUser read(JsonReader reader) throws IOException {
        String login = null;
        long id = -1;
        String nodeId = null;
        String url = null;
        String htmlUrl = null;
        String type = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "login" -> login = reader.nextString();
                case "id" -> id = reader.nextLong();
                case "node_id" -> nodeId = reader.nextString();
                case "url" -> url = reader.nextString();
                case "html_url" -> htmlUrl = reader.nextString();
                case "type" -> type = reader.nextString();
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        return new GitHubUser(login, id, nodeId, url, htmlUrl, type);
    }
}
