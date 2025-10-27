package io.netnotes.engine.utils.github;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public class GitHubAsset {
    private String m_name;
    private String m_label;
    private String m_url;
    private String m_browserDownloadUrl;
    private String m_contentType;
    private long m_size;
    private long m_downloadCount;
    private String m_state;
    private String m_tagName;
    private String m_nodeId;
    private long m_id;
    private Instant m_createdAt;
    private Instant m_updatedAt;
    private GitHubUser m_uploader;
    private String m_digest;

    public GitHubAsset(String name, String label, String url, String browserDownloadUrl, String contentType,
                       long size, long downloadCount, String state, String tagName, String nodeId, long id,
                       Instant createdAt, Instant updatedAt, GitHubUser uploader, String digest) {
        m_name = name;
        m_label = label;
        m_url = url;
        m_browserDownloadUrl = browserDownloadUrl;
        m_contentType = contentType;
        m_size = size;
        m_downloadCount = downloadCount;
        m_state = state;
        m_tagName = tagName;
        m_nodeId = nodeId;
        m_id = id;
        m_createdAt = createdAt;
        m_updatedAt = updatedAt;
        m_uploader = uploader;
        m_digest = digest;
    }

    // --------------------
    // Getters
    // --------------------
    public String getName() { return m_name; }
    public String getLabel() { return m_label; }
    public String getUrl() { return m_url; }
    public String getBrowserDownloadUrl() { return m_browserDownloadUrl; }
    public String getContentType() { return m_contentType; }
    public long getSize() { return m_size; }
    public long getDownloadCount() { return m_downloadCount; }
    public String getState() { return m_state; }
    public String getTagName() { return m_tagName; }
    public String getNodeId() { return m_nodeId; }
    public long getId() { return m_id; }
    public Instant getCreatedAt() { return m_createdAt; }
    public Instant getUpdatedAt() { return m_updatedAt; }
    public GitHubUser getUploader() { return m_uploader; }
    public String getDigest() { return m_digest; }

    // --------------------
    // JSON Serialization
    // --------------------
    public JsonObject getJsonObject() {
        JsonObject json = new JsonObject();
        json.addProperty("name", m_name);
        json.addProperty("label", m_label);
        json.addProperty("url", m_url);
        json.addProperty("browser_download_url", m_browserDownloadUrl);
        json.addProperty("content_type", m_contentType);
        json.addProperty("size", m_size);
        json.addProperty("download_count", m_downloadCount);
        json.addProperty("state", m_state);
        json.addProperty("tag_name", m_tagName);
        json.addProperty("node_id", m_nodeId);
        json.addProperty("id", m_id);
        json.addProperty("created_at", m_createdAt != null ? m_createdAt.toString() : null);
        json.addProperty("updated_at", m_updatedAt != null ? m_updatedAt.toString() : null);
        if (m_uploader != null) {
            json.add("uploader", m_uploader.getJsonObject());
        }
        json.addProperty("digest", m_digest);
        return json;
    }

    public void write(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("name").value(m_name);
        writer.name("label").value(m_label);
        writer.name("url").value(m_url);
        writer.name("browser_download_url").value(m_browserDownloadUrl);
        writer.name("content_type").value(m_contentType);
        writer.name("size").value(m_size);
        writer.name("download_count").value(m_downloadCount);
        writer.name("state").value(m_state);
        writer.name("tag_name").value(m_tagName);
        writer.name("node_id").value(m_nodeId);
        writer.name("id").value(m_id);
        writer.name("created_at").value(m_createdAt != null ? m_createdAt.toString() : null);
        writer.name("updated_at").value(m_updatedAt != null ? m_updatedAt.toString() : null);
        if (m_uploader != null) {
            writer.name("uploader");
            m_uploader.write(writer);
        }
        writer.name("digest").value(m_digest);
        writer.endObject();
    }

    public static GitHubAsset of(NoteBytesMap map){
        NoteBytes nameBytes = map.getByString("name");
        NoteBytes labelBytes = map.getByString("label");
        NoteBytes urlBytes = map.getByString("url");
        NoteBytes browserDlBytes = map.getByString("browser_download_url");
        NoteBytes contentTypeBytes = map.getByString("content_type");
        NoteBytes sizeBytes = map.getByString("size");
        NoteBytes dlCountBytes = map.getByString("download_count");
        NoteBytes stateBytes = map.getByString("state");
        NoteBytes tagNameBytes = map.getByString("tag_name");
        NoteBytes nodeIDBytes = map.getByString("node_id");
        NoteBytes idBytes = map.getByString("id");
        NoteBytes createdAtBytes = map.getByString("created_at");
        NoteBytes updatedAtBytes = map.getByString("updated_at");
        NoteBytes uploaderBytes = map.getByString("uploader");
        NoteBytes digestBytes = map.getByString("digest");

        return new GitHubAsset(
            nameBytes != null ? nameBytes.getAsString() : "",
            labelBytes != null ? labelBytes.getAsString() : "",
            urlBytes != null ? urlBytes.getAsString() : "",
            browserDlBytes != null ? browserDlBytes.getAsString() : "",
            contentTypeBytes != null ? contentTypeBytes.getAsString() : "",
            sizeBytes != null ? sizeBytes.getAsLong() : -1,
            dlCountBytes != null ? dlCountBytes.getAsLong() : -1,
            stateBytes != null ? stateBytes.getAsString() : "",
            tagNameBytes != null ? tagNameBytes.getAsString() : "",
            nodeIDBytes != null ? nodeIDBytes.getAsString() : "",
            idBytes != null ? idBytes.getAsLong() : -1,
            createdAtBytes != null ? parseInstantSafe(createdAtBytes.getAsString()) : Instant.MIN,
            updatedAtBytes != null ? parseInstantSafe(updatedAtBytes.getAsString()) :  Instant.MIN,
            uploaderBytes != null ? GitHubUser.of(uploaderBytes.getAsNoteBytesMap()) : 
                new GitHubUser("", -1, "","", "", ""),
            digestBytes != null ? digestBytes.getAsString() : ""
        );

    }

    public NoteBytesObject getNoteBytesObject(){
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("name", m_name),
            new NoteBytesPair("label",m_label),
            new NoteBytesPair("url",m_url),
            new NoteBytesPair("browser_download_url",m_browserDownloadUrl),
            new NoteBytesPair("content_type",m_contentType),
            new NoteBytesPair("size",m_size),
            new NoteBytesPair("download_count",m_downloadCount),
            new NoteBytesPair("state",m_state),
            new NoteBytesPair("tag_name",m_tagName),
            new NoteBytesPair("node_id",m_nodeId),
            new NoteBytesPair("id",m_id),
            new NoteBytesPair("created_at",m_createdAt != null ? m_createdAt.toString() : null),
            new NoteBytesPair("updated_at",m_updatedAt != null ? m_updatedAt.toString() : null),
            new NoteBytesPair("uploader", m_uploader != null ? m_uploader : "" ),
            new NoteBytesPair("digest",m_digest)
        });
    }

    // --------------------
    // JSON Deserialization
    // --------------------
    public static GitHubAsset read(JsonReader reader) throws IOException {
        String name = null, label = null, url = null, browserDownloadUrl = null, contentType = null,
               state = null, tagName = null, nodeId = null, digest = null;
        long size = 0, downloadCount = 0, id = 0;
        Instant createdAt = null, updatedAt = null;
        GitHubUser uploader = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String fieldName = reader.nextName();
            switch (fieldName) {
                case "name" -> name = reader.nextString();
                case "label" -> label = reader.nextString();
                case "url" -> url = reader.nextString();
                case "browser_download_url" -> browserDownloadUrl = reader.nextString();
                case "content_type" -> contentType = reader.nextString();
                case "size" -> size = reader.nextLong();
                case "download_count" -> downloadCount = reader.nextLong();
                case "state" -> state = reader.nextString();
                case "tag_name" -> tagName = reader.nextString();
                case "node_id" -> nodeId = reader.nextString();
                case "id" -> id = reader.nextLong();
                case "created_at" -> createdAt = parseInstantSafe(reader.nextString());
                case "updated_at" -> updatedAt = parseInstantSafe(reader.nextString());
                case "uploader" -> uploader = GitHubUser.read(reader);
                case "digest" -> digest = reader.nextString();
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        return new GitHubAsset(name, label, url, browserDownloadUrl, contentType,
                               size, downloadCount, state, tagName, nodeId, id,
                               createdAt, updatedAt, uploader, digest);
    }

    private static Instant parseInstantSafe(String iso) {
        if (iso == null) return null;
        try { return Instant.parse(iso); }
        catch (DateTimeParseException e) { return null; }
    }
}
