package io.netnotes.engine.plugins;


import io.netnotes.engine.utils.github.GitHubAPI;
import io.netnotes.engine.utils.github.GitHubAsset;
import io.netnotes.engine.utils.github.GitHubFileInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class OSGiPluginReleaseFetcher {
    private final ExecutorService m_execService;
    
    public OSGiPluginReleaseFetcher(ExecutorService execService) {
        m_execService = execService;
    }
    
    public CompletableFuture<List<OSGiPluginRelease>> fetchReleasesForApp(OSGiPluginInformation appInfo) {
        GitHubFileInfo[] gitHubFiles = appInfo.getGitHubFiles();
        
        if (gitHubFiles == null || gitHubFiles.length == 0) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        // Use the first GitHub file info to get releases
        GitHubFileInfo fileInfo = gitHubFiles[0];
        String user = fileInfo.getGitHubInfo().getUser();
        String project = fileInfo.getGitHubInfo().getProject();
        
        GitHubAPI api = new GitHubAPI(user, project);
        
        return api.getAssetsAllLatestRelease(m_execService)
            .thenApply(assets -> {
                List<OSGiPluginRelease> releases = new ArrayList<>();
                
                if (assets != null) {
                    for (GitHubAsset asset : assets) {
                        // Check if this asset matches any of the app's GitHub files
                        for (GitHubFileInfo ghFile : gitHubFiles) {
                            if (assetMatchesFileInfo(asset, ghFile)) {
                                String version = extractVersion(asset.getName(), 
                                                               ghFile.getFileName(), 
                                                               ghFile.getFileExt());
                                OSGiPluginRelease release = new OSGiPluginRelease(appInfo, asset, version, 
                                                                    asset.getTagName(), 
                                                                    System.currentTimeMillis());
                                releases.add(release);
                                break;
                            }
                        }
                    }
                }
                
                return releases;
            });
    }
    
    private boolean assetMatchesFileInfo(GitHubAsset asset, GitHubFileInfo fileInfo) {
        String assetName = asset.getName();
        String fileName = fileInfo.getFileName();
        String fileExt = fileInfo.getFileExt();
        
        return assetName.startsWith(fileName) && assetName.endsWith(fileExt);
    }
    
    private String extractVersion(String assetName, String fileName, String fileExt) {
        // Remove the fileName prefix and fileExt suffix to get the version
        String version = assetName;
        
        if (version.startsWith(fileName)) {
            version = version.substring(fileName.length());
        }
        
        if (version.endsWith(fileExt)) {
            version = version.substring(0, version.length() - fileExt.length());
        }
        
        // Clean up common separators
        version = version.replaceAll("^[-_]", "").replaceAll("[-_]$", "");
        
        return version.isEmpty() ? "latest" : version;
    }
}