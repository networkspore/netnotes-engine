package io.netnotes.engine.utils.github;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.utils.streams.UrlStreamHelpers;

import com.google.gson.JsonArray;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;


public class GitHubAPI {

    public final static String GITHUB_API_URL = "https://api.github.com";

    private String m_username;
    private String m_project;


    public GitHubAPI(String username, String project){
        m_username = username;
        m_project = project;
        
    }

    public String getUrlLatestRelease(){
        return GITHUB_API_URL + "/repos/" + m_username + "/" + m_project + "/releases/latest";
    }
    
    public String getUrlAllReleases(){
        return GITHUB_API_URL + "/repos/" + m_username + "/" + m_project + "/releases";
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


}