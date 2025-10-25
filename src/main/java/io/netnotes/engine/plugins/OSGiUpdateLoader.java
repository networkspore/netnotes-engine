package io.netnotes.engine.plugins;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.engine.utils.github.GitHubFileInfo;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class OSGiUpdateLoader {
    private final GitHubInfo m_gitHubInfo;
    private final ExecutorService m_execService;
    
    public OSGiUpdateLoader(GitHubInfo gitHubInfo, ExecutorService execService) {
        m_gitHubInfo = gitHubInfo;
        m_execService = execService;
    }
    
    public CompletableFuture<List<OSGiPluginInformation>> loadAvailableApps() {
        String userString = m_gitHubInfo.getUser();
        String projectString = m_gitHubInfo.getProject();
        String availableAppsUrl = "https://raw.githubusercontent.com/" + 
                                 userString + "/" + projectString + "/main/available_apps.json";
        
        return UrlStreamHelpers.getUrlJson(availableAppsUrl, m_execService)
            .thenApply(this::parseAvailableApps);
    }
    
    private List<OSGiPluginInformation> parseAvailableApps(JsonObject availableAppsJson) {
        List<OSGiPluginInformation> apps = new ArrayList<>();
        
        JsonElement appsElement = availableAppsJson.get("apps");
        if (appsElement != null && appsElement.isJsonArray()) {
            JsonArray appsArray = appsElement.getAsJsonArray();
            
            for (JsonElement appElement : appsArray) {
                if (appElement.isJsonObject()) {
                    JsonObject appObj = appElement.getAsJsonObject();
                    
                    try {
                        OSGiPluginInformation appInfo = parseAppInfo(appObj);
                        if (appInfo != null) {
                            apps.add(appInfo);
                        }
                    } catch (Exception e) {
                        System.err.println("Error parsing app: " + e.getMessage());
                    }
                }
            }
        }
        
        return apps;
    }
    
    private OSGiPluginInformation parseAppInfo(JsonObject appObj) {
        // Parse basic info
        String appIdStr = appObj.get("appId").getAsString();
        String appName = appObj.get("name").getAsString();
        String description = appObj.get("description").getAsString();
        
        NoteBytes appId = new NoteBytes(appIdStr);
        
        // Parse icon URLs
        String iconUrl = appObj.has("icon") ? appObj.get("icon").getAsString() : null;
        String smallIconUrl = appObj.has("smallIcon") ? appObj.get("smallIcon").getAsString() : null;
        

        
        // Parse GitHub files
        List<GitHubFileInfo> gitHubFiles = new ArrayList<>();
        JsonElement filesElement = appObj.get("gitHubFiles");
        if (filesElement != null && filesElement.isJsonArray()) {
            JsonArray filesArray = filesElement.getAsJsonArray();
            
            for (JsonElement fileElement : filesArray) {
                if (fileElement.isJsonObject()) {
                    JsonObject fileObj = fileElement.getAsJsonObject();
                    
                    String user = fileObj.get("user").getAsString();
                    String project = fileObj.get("project").getAsString();
                    String fileName = fileObj.get("fileName").getAsString();
                    String fileExt = fileObj.get("fileExt").getAsString();
                    
                    GitHubInfo githubInfo = new GitHubInfo(user, project);
                    GitHubFileInfo fileInfo = new GitHubFileInfo(githubInfo, fileName, fileExt);
                    gitHubFiles.add(fileInfo);
                }
            }
        }
        
        return new OSGiPluginInformation(appId, appName, iconUrl, smallIconUrl, description, 
                                 gitHubFiles.toArray(new GitHubFileInfo[0]));
    }
}