package io.netnotes.engine.utils.github;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import com.google.gson.JsonObject;

import io.netnotes.engine.utils.HashData;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;




public class GitHubReleaseInfo {
    private String m_jarUrl = null;
    private String m_tagName = null;
    private String m_jarName = null;
    private HashData m_jarHashData = null;
    private String m_releaseUrl;
    private JsonObject m_releaseInfoJson = null;
    private GitHubAsset[] m_assets = new GitHubAsset[0];

    public GitHubReleaseInfo() {
    }

    public GitHubReleaseInfo(String appUrl, String tagName, String appName, HashData hashData, String releaseUrl) {
        m_jarUrl = appUrl;
        m_tagName = tagName;
        m_jarName = appName;
        m_jarHashData = hashData;
        m_releaseUrl = releaseUrl;
    }

    public void setReleaseInfoJson(JsonObject releaseInfo) {
        m_releaseInfoJson = releaseInfo;

        m_jarHashData = new HashData(
        m_releaseInfoJson.get("application").getAsJsonObject().get("hashData").getAsJsonObject());
    }

    public GitHubAsset[] getAssets() {
        return m_assets;
    }

    public void setAssets(GitHubAsset[] assets) {
        m_assets = assets;
        for (GitHubAsset asset : assets) {
            if (asset.getName().equals("releaseInfo.json")) {
                setReleaseUrl(asset.getUrl());

            } else {
                if (asset.getName().endsWith(".jar")) {

                    setJarName(asset.getName());
                    setTagName(asset.getTagName());
                    setJarUrl(asset.getUrl());

                }
            }
        }
    }

    public JsonObject getReleaseInfoJson() {
        return m_releaseInfoJson;
    }

    public String getReleaseUrl() {
        return m_releaseUrl;
    }

    public void setReleaseUrl(String releaseUrl) {
        m_releaseUrl = releaseUrl;
    }

    public String getJarUrl() {
        return m_jarUrl;
    }

    public String getTagName() {
        return m_tagName;
    }

    public String getJarName() {
        return m_jarName;
    }

    public HashData getJarHashData() {
        return m_jarHashData;
    }

    public void setJarUrl(String url) {
        m_jarUrl = url;
    }

    public void setTagName(String tagName) {
        m_tagName = tagName;
    }

    public void setJarName(String name) {
        m_jarName = name;
    }

    public void setJarHashData(HashData hashData) {
        m_jarHashData = hashData;
    }


    /*public CompletableFuture<GitHubJarRelease> checkForUpdates(String gitHubUser, String githubProject, ExecutorService execService){
        GitHubAPI gitHubAPI = new GitHubAPI(gitHubUser, githubProject);
        return gitHubAPI.getAssetsLatestRelease(execService).thenApply( assets->{
            GitHubJarRelease tmpInfo = new GitHubJarRelease();

            for(GitHubAsset asset : assets){
                if(asset.getName().equals("releaseInfo.json")){
                    tmpInfo.setReleaseUrl(asset.getUrl());
                    
                }else{
                    if(asset.getContentType().equals("application/x-java-archive")){
                        if(asset.getName().startsWith("netnotes-")){
                            
                            tmpInfo.setJarName(asset.getName());
                            tmpInfo.setTagName(asset.getTagName());
                            tmpInfo.setJarUrl(asset.getUrl());
                                                        
                        }
                    }
                }
            }
            return UrlStreamHelpers.getUrlJson(tmpInfo.getReleaseUrl(), execService).thenApply(releaseInfoJson->{
            GitHubJarRelease upInfo = new GitHubJarRelease(tmpInfo.getJarUrl(),tmpInfo.getTagName(),tmpInfo.getJarName(),null,tmpInfo.getReleaseUrl());
            upInfo.setReleaseInfoJson(releaseInfoJson);

                return upInfo;
            }).join();
        });

           
    }*/

    public CompletableFuture<GitHubReleaseInfo> checkForReleaseInfo(String gitHubUser, String githubProject, ExecutorService execService) {
        GitHubAPI gitHubAPI = new GitHubAPI(gitHubUser, githubProject);
        
        return gitHubAPI.getAssetsLatestRelease(execService)
            .thenCompose(assets -> {
                GitHubReleaseInfo tmpInfo = new GitHubReleaseInfo();
                
                for (GitHubAsset asset : assets) {
                    if (asset.getName().equals("releaseInfo.json")) {
                        tmpInfo.setReleaseUrl(asset.getUrl());
                    } else if (asset.getContentType().equals("application/x-java-archive")) {
                        if (asset.getName().startsWith("netnotes-")) {
                            tmpInfo.setJarName(asset.getName());
                            tmpInfo.setTagName(asset.getTagName());
                            tmpInfo.setJarUrl(asset.getUrl());
                        }
                    }
                }
                
                return UrlStreamHelpers.getUrlJson(tmpInfo.getReleaseUrl(), execService)
                    .thenApply(releaseInfoJson -> {
                        tmpInfo.setReleaseInfoJson(releaseInfoJson);
                        return tmpInfo;
                    });
            });
    }

}