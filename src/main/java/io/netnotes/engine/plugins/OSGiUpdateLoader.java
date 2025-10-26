package io.netnotes.engine.plugins;


import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.engine.utils.github.GitHubAPI;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

public class OSGiUpdateLoader {
    public final static String DEFAULT_BRANCH = "main";
    
    private final GitHubInfo m_gitHubInfo;
    private final ExecutorService m_execService;

    private String m_branch;
    private String m_filePath;//available_apps.json

    public OSGiUpdateLoader(GitHubInfo gitHubInfo, String filePath, ExecutorService execService) {
        this(gitHubInfo, DEFAULT_BRANCH, filePath, execService);
    }

    public OSGiUpdateLoader(GitHubInfo gitHubInfo, String branch, String filePath, ExecutorService execService) {
        m_gitHubInfo = gitHubInfo;
        m_execService = execService;
        m_branch = branch;
        m_filePath = filePath;
    }
    


    public CompletableFuture<List<OSGiPluginInformation>> loadAvailableApps() {
        String userString = m_gitHubInfo.getUser();
        String projectString = m_gitHubInfo.getProject();
        String availableAppsUrl = GitHubAPI.GITHUB_USER_CONTENT + "/" +
                                 userString + "/" + projectString + "/"+ m_branch+ "/" + m_filePath;

        return CompletableFuture.supplyAsync(()->{
            try(
                InputStream inputStream = UrlStreamHelpers.getHttpUrlConnection(availableAppsUrl).getInputStream();
                JsonReader reader = new JsonReader(new InputStreamReader(inputStream));
            ) {
                return readAvailableApps(reader);

            }catch(IOException e){
                throw new CompletionException("Stream failed to complete", e);
            } catch (URISyntaxException e) {
                throw new CompletionException("Invalid URL syntax", e);
            }
        }, m_execService);
    }
    
    private List<OSGiPluginInformation> readAvailableApps(JsonReader reader) throws IOException {
        List<OSGiPluginInformation> apps = new ArrayList<>();

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("apps".equals(name)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    try {
                        OSGiPluginInformation appInfo = OSGiPluginInformation.read(reader);
                        if (appInfo != null) apps.add(appInfo);
                    } catch (Exception e) {
                        System.err.println("Error parsing app entry: " + e.getMessage());
                        reader.skipValue(); // skip malformed entry
                    }
                }
                reader.endArray();
            } else {
                reader.skipValue(); // skip unknown fields
            }
        }
        reader.endObject();

        return apps;
    }


    public boolean saveAvailableApps(File saveFile, List<OSGiPluginInformation> apps) {
        if (apps == null || apps.isEmpty()) {
            System.err.println("No apps to save.");
            return false;
        }


        if (saveFile == null) {
            System.out.println("Save canceled by user.");
            return false;
        }

        // Write JSON
        try (JsonWriter writer = new JsonWriter(new FileWriter(saveFile))) {
            writer.setIndent("  "); // pretty-print

            writer.beginObject();
            writer.name("apps");
            writer.beginArray();

            for (OSGiPluginInformation appInfo : apps) {
                if (appInfo != null) {
                    appInfo.write(writer); // use your class’s streaming writer
                }
            }

            writer.endArray();
            writer.endObject();
            
        } catch (IOException e) {
            System.err.println("Failed to save apps: " + e.getMessage());
            e.printStackTrace();
            return false;
        }

        System.out.println("Saved " + apps.size() + " apps to: " + saveFile.getAbsolutePath());
        return true;
    }
    
}