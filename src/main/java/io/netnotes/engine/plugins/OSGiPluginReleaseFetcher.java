package io.netnotes.engine.plugins;


import io.netnotes.engine.utils.github.GitHubAPI;
import io.netnotes.engine.utils.github.GitHubAsset;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class OSGiPluginReleaseFetcher {
    private final ExecutorService m_execService;
    
    public OSGiPluginReleaseFetcher(ExecutorService execService) {
        m_execService = execService;
    }
    
    public CompletableFuture<List<OSGiPluginRelease>> fetchReleasesForApp(boolean includeBetas, OSGiPluginInformation appInfo) {
        OSGiPluginFileInfo[] gitHubFiles = appInfo.getGitHubFiles();
        
        if (gitHubFiles == null || gitHubFiles.length == 0) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        // Use the first GitHub file info to get releases
        OSGiPluginFileInfo fileInfo = gitHubFiles[0];

        
        GitHubAPI api = new GitHubAPI(fileInfo.getGitHubInfo());
        
        return api.getAssets(includeBetas, m_execService)
            .thenApply(assets -> {
                List<OSGiPluginRelease> releases = new ArrayList<>();
                
                if (assets != null) {
                    for (GitHubAsset asset : assets) {
                        // Check if this asset matches any of the app's GitHub files
                        for (OSGiPluginFileInfo ghFile : gitHubFiles) {
                            if (assetMatchesFileInfo(asset, ghFile)) {
             
                                OSGiPluginRelease release = new OSGiPluginRelease(appInfo, asset);
                                releases.add(release);
                                break;
                            }
                        }
                    }
                }
                
                return releases;
            });
    }
    
    private boolean assetMatchesFileInfo(GitHubAsset asset, OSGiPluginFileInfo fileInfo) {
        String assetName = asset.getName();
        String fileName = fileInfo.getFileName();
        String fileExt = fileInfo.getFileExt();
        
        return assetName.startsWith(fileName) && assetName.endsWith(fileExt);
    }
    
}