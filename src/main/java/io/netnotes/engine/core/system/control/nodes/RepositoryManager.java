package io.netnotes.engine.core.system.control.nodes;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.core.AppData;

/**
 * RepositoryManager - Manages repository list (like /etc/apt/sources.list)
 * 
 * Stores list of repositories in encrypted NoteFile
 * User can add/remove repositories freely
 */
public class RepositoryManager {
    private final AppData appData;
    private final ConcurrentHashMap<String, Repository> repositories;
    
    public RepositoryManager(AppData appData) {
        this.appData = appData;
        this.repositories = new ConcurrentHashMap<>();
    }
    
    public CompletableFuture<Void> initialize() {
        // Load repositories from NoteFile
        // For now, add default repository
        Repository official = new Repository(
            "official",
            "Official Netnotes Repository",
            "https://netnotes.io/repo/packages.json",
            "https://netnotes.io/repo/key.gpg",
            true
        );
        repositories.put(official.getId(), official);
        
        return CompletableFuture.completedFuture(null);
    }
    
    public List<Repository> getRepositories() {
        return new ArrayList<>(repositories.values());
    }
    
    public void addRepository(Repository repo) {
        repositories.put(repo.getId(), repo);
        // TODO: Save to NoteFile
    }
    
    public void removeRepository(String repoId) {
        repositories.remove(repoId);
        // TODO: Save to NoteFile
    }
    
    public void setRepositoryEnabled(String repoId, boolean enabled) {
        Repository repo = repositories.get(repoId);
        if (repo != null) {
            repo.setEnabled(enabled);
            // TODO: Save to NoteFile
        }
    }
    
    /**
     * Update all repositories (like apt-get update)
     * Fetches package lists from all enabled repositories
     */
    public CompletableFuture<List<PackageInfo>> updateAllRepositories() {
        List<CompletableFuture<List<PackageInfo>>> futures = new ArrayList<>();
        
        for (Repository repo : repositories.values()) {
            if (repo.isEnabled()) {
                futures.add(fetchPackagesFromRepository(repo));
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<PackageInfo> allPackages = new ArrayList<>();
                futures.forEach(f -> allPackages.addAll(f.join()));
                return allPackages;
            });
    }
    
    private CompletableFuture<List<PackageInfo>> fetchPackagesFromRepository(
            Repository repo) {
        
        return CompletableFuture.supplyAsync(() -> {
            // TODO: Fetch and parse packages.json from repo.getUrl()
            // TODO: Verify with key if repo.hasKey()
            
            System.out.println("[RepositoryManager] Fetching from: " + repo.getName());
            
            // For now, return mock data
            return new ArrayList<PackageInfo>();
            
        }, appData.getExecService());
    }
    
    public void shutdown() {
        // Save repositories to NoteFile
    }
}