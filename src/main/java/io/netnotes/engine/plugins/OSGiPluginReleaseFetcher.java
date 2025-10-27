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
        OSGiAvailablePluginFileInfo gitHubJar = appInfo.getGitHubJar();
        
        if (gitHubJar == null) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    
        GitHubAPI api = new GitHubAPI(gitHubJar.getGitHubInfo());
        
        return api.getAssets(includeBetas, m_execService)
            .thenApply(assets -> {
                List<OSGiPluginRelease> releases = new ArrayList<>();
                
                if (assets != null) {
                    for (GitHubAsset asset : assets) {
                        // Check if this asset matches any of the app's GitHub files
                    
                        if (assetMatchesFileInfo(asset, gitHubJar)) {
            
                            OSGiPluginRelease release = new OSGiPluginRelease(appInfo, asset);
                            releases.add(release);
                            break;
                        }
                        
                    }
                }
                
                return releases;
            });
    }
    
    private boolean assetMatchesFileInfo(GitHubAsset asset, OSGiAvailablePluginFileInfo fileInfo) {
        String assetName = asset.getName();
        String fileName = fileInfo.getFileName();
        String fileExt = fileInfo.getFileExt();
        
        return assetName.startsWith(fileName) && assetName.endsWith(fileExt);
    }
    
}