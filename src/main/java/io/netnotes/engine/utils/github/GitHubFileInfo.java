package io.netnotes.engine.utils.github;

import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class GitHubFileInfo {
    private final GitHubInfo m_githubInfo;
    private final String m_fileName;
    private final String m_fileExt;

    public GitHubFileInfo(GitHubInfo gitHubInfo, String fileName, String fileExt){
        m_githubInfo = gitHubInfo;
        m_fileName = fileName;
        m_fileExt = fileExt;
    }

    public GitHubInfo getGitHubInfo(){
        return m_githubInfo;
    }

    public String getFileName() {
        return m_fileName;
    }


    public String getFileExt() {
        return m_fileExt;
    }


     public JsonObject getJsonObject() {
        JsonObject json = new JsonObject();
        if (m_githubInfo != null) {
            json.add("githubInfo", m_githubInfo.getJsonObject());
        }
        json.addProperty("fileName", m_fileName);
        json.addProperty("fileExt", m_fileExt);
        return json;
    }
    
    public static GitHubFileInfo of(JsonObject json) {
        if (json == null) return null;

        GitHubInfo githubInfo = json.has("githubInfo") && json.get("githubInfo").isJsonObject() ?
            GitHubInfo.of(json.getAsJsonObject("githubInfo")) : null;
        String fileName = json.has("fileName") && !json.get("fileName").isJsonNull() ?
            fileName = json.get("fileName").getAsString() : null;
        String fileExt = json.has("fileExt") && !json.get("fileExt").isJsonNull() ?
            fileExt = json.get("fileExt").getAsString() : null;


        return githubInfo != null && fileName != null && fileExt != null ?
             new GitHubFileInfo(githubInfo, fileName, fileExt) : null;
    }

     public static GitHubFileInfo read(JsonReader reader) throws IOException {
        GitHubInfo githubInfo = null;
        String fileName = null;
        String fileExt = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "githubInfo":
                    githubInfo = GitHubInfo.read(reader);
                    break;
                case "fileName":
                    fileName = reader.nextString();
                    break;
                case "fileExt":
                    fileExt = reader.nextString();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        GitHubFileInfo fileInfo = githubInfo != null && fileName != null && fileExt != null ?
             new GitHubFileInfo(githubInfo, fileName, fileExt) : null;

        if(fileInfo == null){
            throw new IOException("File info corrupt");
        }

        return fileInfo;
    }

    public void write(JsonWriter writer) throws IOException {
        writer.beginObject();
        if (m_githubInfo != null) {
            writer.name("githubInfo");
            m_githubInfo.write(writer);
        } else {
            writer.name("githubInfo").nullValue();
        }
        writer.name("fileName").value(m_fileName);
        writer.name("fileExt").value(m_fileExt);
        writer.endObject();
    }
}
