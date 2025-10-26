package io.netnotes.engine.utils.github;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netnotes.engine.utils.streams.UrlStreamHelpers;

import com.google.gson.JsonArray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;


public class GitHubAPI {

    public final static String GITHUB_API_URL = "https://api.github.com";
    public final static String GITHUB_USER_CONTENT = "https://raw.githubusercontent.com";

    public final static String REPOS = "repos";
    public final static String RELEASES = "releases";
    public final static String LATEST = "latest";
    public final static String CONTENTS = "contents";

    private final GitHubInfo gitHubInfo;


    public GitHubAPI(GitHubInfo info){
        gitHubInfo = info;
    }

    public static String getUrlLatestRelease(GitHubInfo gitHubInfo){
        return GITHUB_API_URL + "/" +REPOS+"/" + gitHubInfo.getUser() + "/" + gitHubInfo.getProject() + "/" +RELEASES + "/" + LATEST;
    }

    public String getUrlLatestRelease(){
        return getUrlLatestRelease(gitHubInfo);
    }
    
    public String getUrlAllReleases(){
        return getUrlAllReleases(gitHubInfo);
    }
    public static String getUrlAllReleases(GitHubInfo gitHubInfo){
        return GITHUB_API_URL + "/" + REPOS + "/" + gitHubInfo.getUser() + "/" + gitHubInfo.getProject() + "/" + RELEASES;
    }
    
    public  String getUrlContentsPath( String path){
        return getUrlContentsPath(gitHubInfo, path);
    }

    public static String getUrlContentsPath(GitHubInfo gitHubInfo, String path){
        return GitHubAPI.GITHUB_API_URL + "/"+REPOS +"/" + gitHubInfo.getUser() + "/" + gitHubInfo.getProject() + 
            "/"+CONTENTS+"/" + path;
    }


    public CompletableFuture<GitHubAsset[]> getAssetsAllLatestRelease(ExecutorService execService){
        return UrlStreamHelpers.getUrlJsonArray(getUrlAllReleases(),execService).thenApply(allReleases -> {
        
            JsonElement elementObject = allReleases.get(0);

            if (elementObject != null && elementObject.isJsonObject()) {
                    JsonObject gitHubApiJson = elementObject.getAsJsonObject();
                String tagName = gitHubApiJson.get("tag_name").getAsString();
                JsonElement assetsElement = gitHubApiJson.get("assets");
                if (assetsElement != null && assetsElement.isJsonArray()) {
                    
                    JsonArray assetsArray = assetsElement.getAsJsonArray();
                    if (assetsArray.size() > 0) {
                        int assetArraySize = assetsArray.size();
                        GitHubAsset[] assetArray = new GitHubAsset[assetArraySize]; 

                        for(int i = 0; i < assetArraySize ; i++){
                            JsonElement assetElement = assetsArray.get(i);

                            if (assetElement != null && assetElement.isJsonObject()) {
                                JsonObject assetObject = assetElement.getAsJsonObject();

                                JsonElement downloadUrlElement = assetObject.get("browser_download_url");
                                JsonElement nameElement = assetObject.get("name");
                                JsonElement contentTypeElement = assetObject.get("content_type");
                                JsonElement sizeElement = assetObject.get("size");

                                if (downloadUrlElement != null && downloadUrlElement.isJsonPrimitive()) {
                                    
                                    String url = downloadUrlElement.getAsString();
                                    String name = nameElement.getAsString();
                                    long contentSize = sizeElement.getAsLong();
                                    String contentTypeString = contentTypeElement.getAsString();

                                    assetArray[i] = new GitHubAsset(name, url, contentTypeString, contentSize, tagName);

                                }
                            }
                        }
                        return assetArray; 
                    }else{
                       return null;
                    }
                }else{
                    throw new IllegalStateException("Expected Json Object");
                }
            }else{
                throw new IllegalStateException("Expected Json Object");
            }
           
        });
    }


    public CompletableFuture<GitHubAsset[]> getAssetsLatestRelease(ExecutorService execService){
        return UrlStreamHelpers.getUrlJson(getUrlLatestRelease(),execService).thenApply(gitHubApiJson->{
            
            String tagName = gitHubApiJson.get("tag_name").getAsString();
            JsonElement assetsElement = gitHubApiJson.get("assets");
            if (assetsElement != null && assetsElement.isJsonArray()) {
                
                JsonArray assetsArray = assetsElement.getAsJsonArray();
                if (assetsArray.size() > 0) {
                    int assetArraySize = assetsArray.size();
                    GitHubAsset[] assetArray = new GitHubAsset[assetArraySize]; 

                    for(int i = 0; i < assetArraySize ; i++){
                        JsonElement assetElement = assetsArray.get(i);

                        if (assetElement != null && assetElement.isJsonObject()) {
                            JsonObject assetObject = assetElement.getAsJsonObject();

                            JsonElement downloadUrlElement = assetObject.get("browser_download_url");
                            JsonElement nameElement = assetObject.get("name");
                            JsonElement contentTypeElement = assetObject.get("content_type");
                            JsonElement sizeElement = assetObject.get("size");

                            if (downloadUrlElement != null && downloadUrlElement.isJsonPrimitive()) {
                                
                                String url = downloadUrlElement.getAsString();
                                String name = nameElement.getAsString();
                                long contentSize = sizeElement.getAsLong();
                                String contentTypeString = contentTypeElement.getAsString();

                                assetArray[i] = new GitHubAsset(name, url, contentTypeString, contentSize, tagName);

                            }
                        }
                    }
                    return assetArray;
                }else{
                    return null;
                }
            }else{
                throw new IllegalStateException("Assets are not in JsonArray format");
            }
        });


    }


    public static String getFileSha(String apiUrl, String token) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(apiUrl).toURL().openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            if (conn.getResponseCode() == 200) {
                String response = readResponse(conn);
                JsonObject json = JsonParser.parseString(response).getAsJsonObject();
                return json.get("sha").getAsString();
            }
        } catch (Exception e) {
            // File not found or unauthorized
        }
        return null;
    }

    public static String readResponse(HttpURLConnection conn) throws IOException {
        try (InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}