package io.netnotes.engine.plugins;


import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.engine.noteFiles.FileStreamUtils;
import io.netnotes.engine.utils.github.GitHubAPI;
import io.netnotes.engine.utils.github.GitHubFileUploader;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedOutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

public class OSGiUpdateLoader {
    public final static String DEFAULT_BRANCH = "main";

    private final GitHubInfo m_gitHubInfo;
    private final ExecutorService m_execService;
    private final String m_branch;
    private final String m_filePath;

    public OSGiUpdateLoader(GitHubInfo gitHubInfo, String filePath, ExecutorService execService) {
        this(gitHubInfo, DEFAULT_BRANCH, filePath, execService);
    }

    public OSGiUpdateLoader(GitHubInfo gitHubInfo, String branch, String filePath, ExecutorService execService) {
        m_gitHubInfo = gitHubInfo;
        m_execService = execService;
        m_branch = branch;
        m_filePath = filePath;
    }
    
    public CompletableFuture<List<OSGiPluginInformation>> loadAvailableApps(){
        return loadAvailableApps(m_gitHubInfo, m_branch, m_filePath, m_execService);
    }

    public static CompletableFuture<List<OSGiPluginInformation>> loadAvailableApps(GitHubInfo gitHubInfo, String branch, 
        String filePath, ExecutorService execService
    ) {
        return CompletableFuture.supplyAsync(()->{
            String availableAppsUrl = GitHubAPI.getUrlUserContentPath(gitHubInfo, branch, filePath);
            try(
                InputStream inputStream = UrlStreamHelpers.newUrlStream(availableAppsUrl);
                JsonReader reader = new JsonReader(new InputStreamReader(inputStream));
            ) {
                return readAvailableApps(reader);

            }catch(IOException e){
                throw new CompletionException("Stream failed to complete", e);
            } catch (URISyntaxException e) {
                throw new CompletionException("Invalid URL syntax", e);
            }
        }, execService);
    }

    public static CompletableFuture< List<OSGiPluginInformation>> readAvailableAppsFromFile(File file){
        return CompletableFuture.supplyAsync(()->{
            try(
                InputStream inputStream = Files.newInputStream(file.toPath());
                JsonReader reader = new JsonReader(new InputStreamReader(inputStream));

            ){
                return readAvailableApps(reader);

            }catch(IOException e){
                throw new CompletionException("File could not be read", e);
            }
        });
    }
    
    public static List<OSGiPluginInformation> readAvailableApps(JsonReader reader) throws IOException {
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



    public CompletableFuture<Boolean> uploadAvailableApps(String token, String commitMsg, 
        List<OSGiPluginInformation> apps
    ) {
        if (apps == null || apps.isEmpty()) {
            System.err.println("No apps to save.");
            return CompletableFuture.completedFuture(false);
        }

        PipedOutputStream pipedOutputStream = new PipedOutputStream();
        CompletableFuture<Void> writerFuture = 
            pluginInformationToPipe(pipedOutputStream, apps);
        CompletableFuture<Boolean> uploadFuture = 
            GitHubFileUploader.uploadFile(m_gitHubInfo, token, m_filePath, commitMsg, pipedOutputStream, m_execService);
    
        return CompletableFuture.allOf(writerFuture, uploadFuture).thenCompose(v->uploadFuture);
    }

    public CompletableFuture<Void> pluginInformationToPipe(PipedOutputStream pipedOutputStream, 
        List<OSGiPluginInformation> apps
    ) {
        return CompletableFuture.runAsync(()->{
            try (
                PipedOutputStream outputStream = pipedOutputStream;
                JsonWriter writer = new JsonWriter(new OutputStreamWriter(outputStream));
            ) {
                writer.beginObject();
                writer.name("apps");
                writer.beginArray();
                for (OSGiPluginInformation appInfo : apps) {
                    if (appInfo != null) {
                        appInfo.write(writer);
                    }
                }
                writer.endArray();
                writer.endObject();
            } catch (IOException e) {
                System.err.println("Failed to save apps: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }


    public CompletableFuture<Boolean> saveAvailableAppsToFile(File saveFile, List<OSGiPluginInformation> apps) {
        if (apps == null || apps.isEmpty()) {
            System.err.println("No apps to save.");
            return CompletableFuture.completedFuture(false);
        }

        PipedOutputStream pipedOutputStream = new PipedOutputStream();
        CompletableFuture<Void> writerFuture = 
            pluginInformationToPipe(pipedOutputStream, apps);
        
        CompletableFuture<Boolean> saveFuture = FileStreamUtils.pipeToFile(pipedOutputStream, saveFile, m_execService);
    
        return CompletableFuture.allOf(writerFuture, saveFuture).thenCompose(v->saveFuture);
   
    }
    
}