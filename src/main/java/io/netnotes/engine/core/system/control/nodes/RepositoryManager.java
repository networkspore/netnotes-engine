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
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.utils.VirtualExecutors;
import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RepositoryManager - Manages repository list (System Service)
 * 
 * REFACTORED:
 * - No longer stores AppData reference
 * - Receives path from parent at construction
 * - Uses only AppDataInterface for file access
 * - Parent decides where repository manager lives
 * 
 * LIFECYCLE:
 * - Created by AppData during system initialization
 * - Lives for entire application lifetime
 * - Maintains NoteFile reference for efficient access
 * - Closed during shutdown
 * 
 * STORAGE:
 * - Path: {myPath}/sources (parent decides base path)
 * - Format: NoteBytesMap { repositoryId -> Repository }
 * - NoteFile cached as instance field (no repeated ledger access)
 * 
 * EXTERNAL FORMAT (from GitHub):
 * Fetches packages.json from each repository URL:
 * {
 *   "repository": "Official Netnotes",
 *   "packages": [...]
 * }
 */
public class RepositoryManager {
    
    private final ContextPath myPath;           // NEW: Track my own path
    private final AppDataInterface dataInterface;
    private final ConcurrentHashMap<NoteBytesReadOnly, Repository> repositories;
    
    // Cached NoteFile - expensive to get, cheap to keep
    // should last the duration of RepositoryManager and then be closed
    private NoteFile sourcesFile;
    
    /**
     * Constructor - called by AppData
     * 
     * OLD: RepositoryManager(AppData appData)
     * NEW: RepositoryManager(ContextPath myPath, AppDataInterface dataInterface)
     * 
     * @param myPath Where this manager lives (given by parent)
     * @param dataInterface Scoped interface for file access
     */
    public RepositoryManager(
            ContextPath myPath,
            AppDataInterface dataInterface) {
        
        if (myPath == null) {
            throw new IllegalArgumentException("myPath cannot be null");
        }
        
        if (dataInterface == null) {
            throw new IllegalArgumentException("dataInterface cannot be null");
        }
        
        this.myPath = myPath;
        this.dataInterface = dataInterface;
        this.repositories = new ConcurrentHashMap<>();
    }
    
    /**
     * Initialize - load repositories from NoteFile
     * 
     * Gets NoteFile ONCE and stores for entire lifecycle.
     */
    public CompletableFuture<Void> initialize() {
        System.out.println("[RepositoryManager] Initializing at: " + myPath);
        
        // I know MY path, so I create files under ME
        ContextPath sourcesPath = myPath.append("sources");
        
        return dataInterface.getNoteFile(sourcesPath)
            .thenAccept(file -> {
                this.sourcesFile = file;
                System.out.println("[RepositoryManager] NoteFile acquired and cached");
            })
            .thenCompose(v -> loadRepositories())
            .exceptionallyCompose(ex -> {
                // First run - add default repository
                System.out.println("[RepositoryManager] No existing sources found, adding defaults");
                addDefaultRepository();
                return saveRepositories();
            });
    }
    
    /**
     * Load repositories from cached NoteFile
     */
    private CompletableFuture<Void> loadRepositories() {
        return sourcesFile.readNoteBytes()
            .thenAccept(noteBytesObj -> {
                // Deserialize from NoteBytes format
                NoteBytesMap reposMap = noteBytesObj.getAsNoteBytesMap();
                
                System.out.println("[RepositoryManager] Found " + reposMap.size() + " repositories");
                
                for (Map.Entry<NoteBytes, NoteBytes> entry : reposMap.entrySet()) {
                    NoteBytes repoId = entry.getKey();
                    try {
                        Repository repo = Repository.fromNoteBytes(entry.getValue().getAsNoteBytesMap());
                        repositories.put(repoId.readOnly(), repo);
                        
                        System.out.println("[RepositoryManager] Loaded: " + 
                            repo.getName() + (repo.isEnabled() ? "" : " (disabled)"));
                            
                    } catch (Exception e) {
                        System.err.println("[RepositoryManager] Failed to load repository " + 
                            repoId + ": " + e.getMessage());
                    }
                }
            });
    }
    
    /**
     * Add default official repository on first run
     */
    private void addDefaultRepository() {
        GitHubNodeRepository official = new GitHubNodeRepository("official","Official Netnotes Repository",
            new GitHubInfo("networkspore", "Netnotes-Resources"),
            "packages/main/packages.json",
            "packages/main/key.gpg",
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
    private CompletableFuture<List<PackageInfo>> fetchPackagesFromRepository(Repository repo) {
        
        return UrlStreamHelpers.getUrlContentAsString(repo.getUrl())
            .thenCompose(jsonString ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        System.out.println("[RepositoryManager] Fetching from: " + repo.getName());
                        
                        // Fetch JSON from URL (EXTERNAL format)
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
                                    new NoteBytesReadOnly(packageId),
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
                    
                }, VirtualExecutors.getVirtualExecutor())
            );
    }
    
    /**
     * Save repositories to cached NoteFile
     * 
     * ✅ Uses cached NoteFile - no ledger access!
     * 
     * Format: NoteBytesMap { repositoryId -> Repository.toNoteBytes() }
     */
    private CompletableFuture<Void> saveRepositories() {
        if (sourcesFile == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("RepositoryManager not initialized - sourcesFile is null"));
        }
        
        NoteBytesMap reposMap = new NoteBytesMap();
        
        // Serialize each repository
        for (Repository repo : repositories.values()) {
            reposMap.put(
                new NoteBytes(repo.getId()),
                repo.toNoteBytes()
            );
        }
        
        // Write to cached NoteFile (no ledger access!)
        return sourcesFile.write(reposMap.getNoteBytesObject())
            .exceptionally(ex -> {
                System.err.println("[RepositoryManager] Failed to save: " + 
                    ex.getMessage());
                throw new RuntimeException("Failed to save repository sources", ex);
            });
    }
    
    /**
     * Shutdown - ensure sources are saved and NoteFile closed
     */
    public CompletableFuture<Void> shutdown() {
        System.out.println("[RepositoryManager] Shutting down, saving sources");
        
        return saveRepositories()
            .whenComplete((v, ex) -> {
                if (ex != null) {
                    System.err.println("[RepositoryManager] Error during shutdown save: " + 
                        ex.getMessage());
                }
                
                // ✅ Close cached NoteFile
                if (sourcesFile != null) {
                    sourcesFile.close();
                    System.out.println("[RepositoryManager] NoteFile closed");
                }
            })
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
    
    /**
     * Get my path (for debugging)
     */
    public ContextPath getPath() {
        return myPath;
    }
    
    // REMOVED: getAppData() - no more AppData reference
}