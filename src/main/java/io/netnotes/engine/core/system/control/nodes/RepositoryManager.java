package io.netnotes.engine.core.system.control.nodes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netnotes.engine.core.AppDataInterface;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * RepositoryManager - Manages repository list (like /etc/apt/sources.list)
 * 
 * STORAGE FORMAT:
 * Path: /system/nodes/repositories/sources
 * Format: NoteBytesMap where:
 *   - Key: repositoryId (String)
 *   - Value: Repository.toNoteBytes() (NoteBytesMap)
 * 
 * EXTERNAL FORMAT (from GitHub):
 * Fetches packages.json from each repository URL:
 * {
 *   "repository": "Official Netnotes",
 *   "packages": [
 *     {
 *       "id": "example-node",
 *       "name": "Example Node",
 *       "version": "1.0.0",
 *       "category": "utilities",
 *       "description": "Example node package",
 *       "download_url": "https://github.com/.../package.jar",
 *       "size": 1024000,
 *       "manifest": { ... }
 *     }
 *   ]
 * }
 */
public class RepositoryManager {
    private final AppDataInterface systemInterface;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, Repository> repositories;
    
    private static final ContextPath SOURCES_PATH = 
        NodePaths.REPOSITORIES.append("sources");
    
    public RepositoryManager(AppDataInterface systemInterface, ExecutorService executor) {
        this.systemInterface = systemInterface;
        this.executor = executor;
        this.repositories = new ConcurrentHashMap<>();
    }
    
    /**
     * Initialize - load repositories from NoteFile
     */
    public CompletableFuture<Void> initialize() {
        System.out.println("[RepositoryManager] Loading from: " + SOURCES_PATH);
        
        return systemInterface.getNoteFile(SOURCES_PATH)
            .thenCompose(file -> file.nextNoteBytes())
            .thenAccept(noteBytesObj -> {
                // Deserialize from NoteBytes format
                NoteBytesMap reposMap = noteBytesObj.getAsNoteBytesMap();
                
                System.out.println("[RepositoryManager] Found " + 
                    reposMap.size() + " repositories");
                
                for (NoteBytes repoId : reposMap.keySet()) {
                    try {
                        NoteBytesMap repoData = reposMap.get(repoId).getAsNoteBytesMap();
                        Repository repo = Repository.fromNoteBytes(repoData);
                        repositories.put(repoId.getAsString(), repo);
                        
                        System.out.println("[RepositoryManager] Loaded: " + 
                            repo.getName() + (repo.isEnabled() ? "" : " (disabled)"));
                            
                    } catch (Exception e) {
                        System.err.println("[RepositoryManager] Failed to load repository " + 
                            repoId.getAsString() + ": " + e.getMessage());
                    }
                }
            })
            .exceptionally(ex -> {
                // First run - add default repository
                System.out.println("[RepositoryManager] No existing sources found, adding defaults");
                addDefaultRepository();
                return saveRepositories().join();
            });
    }
    
    /**
     * Add default official repository on first run
     */
    private void addDefaultRepository() {
        Repository official = new Repository(
            "official",
            "Official Netnotes Repository",
            "https://raw.githubusercontent.com/netnotes/packages/main/packages.json",
            "https://raw.githubusercontent.com/netnotes/packages/main/key.gpg",
            true
        );
        repositories.put(official.getId(), official);
        System.out.println("[RepositoryManager] Added default repository: " + 
            official.getName());
    }
    
    /**
     * Get all repositories
     */
    public List<Repository> getRepositories() {
        return new ArrayList<>(repositories.values());
    }
    
    /**
     * Add a new repository
     */
    public CompletableFuture<Void> addRepository(Repository repo) {
        repositories.put(repo.getId(), repo);
        System.out.println("[RepositoryManager] Added repository: " + repo.getName());
        return saveRepositories();
    }
    
    /**
     * Remove a repository
     */
    public CompletableFuture<Void> removeRepository(String repoId) {
        Repository removed = repositories.remove(repoId);
        if (removed != null) {
            System.out.println("[RepositoryManager] Removed repository: " + 
                removed.getName());
            return saveRepositories();
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Enable/disable a repository
     */
    public CompletableFuture<Void> setRepositoryEnabled(String repoId, boolean enabled) {
        Repository repo = repositories.get(repoId);
        if (repo != null) {
            repo.setEnabled(enabled);
            System.out.println("[RepositoryManager] Repository " + repo.getName() + 
                " " + (enabled ? "enabled" : "disabled"));
            return saveRepositories();
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Update all repositories (like apt-get update)
     * Fetches package lists from all enabled repositories
     */
    public CompletableFuture<List<PackageInfo>> updateAllRepositories() {
        List<CompletableFuture<List<PackageInfo>>> futures = new ArrayList<>();
        
        System.out.println("[RepositoryManager] Updating package lists from " + 
            repositories.values().stream().filter(Repository::isEnabled).count() + 
            " enabled repositories");
        
        for (Repository repo : repositories.values()) {
            if (repo.isEnabled()) {
                futures.add(fetchPackagesFromRepository(repo));
            }
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<PackageInfo> allPackages = new ArrayList<>();
                for (CompletableFuture<List<PackageInfo>> future : futures) {
                    try {
                        allPackages.addAll(future.join());
                    } catch (Exception e) {
                        System.err.println("[RepositoryManager] Failed to get packages: " + 
                            e.getMessage());
                    }
                }
                
                System.out.println("[RepositoryManager] Found " + allPackages.size() + 
                    " packages total");
                return allPackages;
            });
    }
    
    /**
     * Fetch packages from a single repository
     * 
     * This is where JSON comes in - repositories serve packages.json externally
     */
    private CompletableFuture<List<PackageInfo>> fetchPackagesFromRepository(
            Repository repo) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("[RepositoryManager] Fetching from: " + repo.getName());
                
                // Fetch JSON from URL (EXTERNAL format)
                String jsonString = fetchUrlAsString(repo.getUrl());
                JsonObject repoData = JsonParser.parseString(jsonString).getAsJsonObject();
                
                // Get packages array
                JsonArray packagesArray = repoData.getAsJsonArray("packages");
                
                List<PackageInfo> packages = new ArrayList<>();
                
                // Convert each package from JSON to internal PackageInfo
                for (JsonElement elem : packagesArray) {
                    JsonObject pkgJson = elem.getAsJsonObject();
                    
                    try {
                        // Create PackageInfo from external JSON
                        String packageId = pkgJson.get("id").getAsString();
                        String name = pkgJson.get("name").getAsString();
                        String version = pkgJson.get("version").getAsString();
                        String category = pkgJson.has("category") ? 
                            pkgJson.get("category").getAsString() : "uncategorized";
                        String description = pkgJson.has("description") ? 
                            pkgJson.get("description").getAsString() : "";
                        String downloadUrl = pkgJson.get("download_url").getAsString();
                        long size = pkgJson.has("size") ? 
                            pkgJson.get("size").getAsLong() : 0;
                        
                        // Parse manifest
                        JsonObject manifestJson = pkgJson.getAsJsonObject("manifest");
                        PackageManifest manifest = PackageManifest.fromJson(manifestJson);
                        
                        PackageInfo pkg = new PackageInfo(
                            packageId,
                            name,
                            version,
                            category,
                            description,
                            repo.getName(), // Which repo it came from
                            downloadUrl,
                            size,
                            manifest
                        );
                        
                        packages.add(pkg);
                        
                    } catch (Exception e) {
                        System.err.println("[RepositoryManager] Failed to parse package in " + 
                            repo.getName() + ": " + e.getMessage());
                    }
                }
                
                System.out.println("[RepositoryManager] " + repo.getName() + 
                    " provided " + packages.size() + " packages");
                
                return packages;
                
            } catch (Exception e) {
                System.err.println("[RepositoryManager] Failed to fetch from " + 
                    repo.getName() + ": " + e.getMessage());
                return new ArrayList<>();
            }
            
        }, executor);
    }
    
    /**
     * Fetch URL content as string
     */
    private String fetchUrlAsString(String urlString) throws Exception {
        URL url = new URL(urlString);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(url.openStream()))) {
            
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
            return content.toString();
        }
    }
    
    /**
     * Save repositories to NoteFile
     * 
     * Format: NoteBytesMap { repositoryId -> Repository.toNoteBytes() }
     */
    private CompletableFuture<Void> saveRepositories() {
        NoteBytesMap reposMap = new NoteBytesMap();
        
        // Serialize each repository
        for (Repository repo : repositories.values()) {
            reposMap.put(
                new NoteBytes(repo.getId()),
                repo.toNoteBytes()
            );
        }
        
        // Write to NoteFile
        return systemInterface.getNoteFile(SOURCES_PATH)
            .thenCompose(file -> file.writeOnly(reposMap.getNoteBytesObject()))
            .thenApply(v -> null)
            .exceptionally(ex -> {
                System.err.println("[RepositoryManager] Failed to save: " + 
                    ex.getMessage());
                throw new RuntimeException("Failed to save repository sources", ex);
            });
    }
    
    /**
     * Shutdown - ensure sources are saved
     */
    public CompletableFuture<Void> shutdown() {
        System.out.println("[RepositoryManager] Shutting down, saving sources");
        return saveRepositories()
            .thenRun(() -> {
                System.out.println("[RepositoryManager] Shutdown complete");
            });
    }
    
    /**
     * Get statistics
     */
    public String getStatistics() {
        long enabled = repositories.values().stream()
            .filter(Repository::isEnabled)
            .count();
        
        return String.format(
            "Repositories: %d total, %d enabled",
            repositories.size(),
            enabled
        );
    }
}