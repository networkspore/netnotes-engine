package io.netnotes.engine.core.system.control.nodes.security;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.ui.UIProtocol;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.utils.github.*;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;
import io.netnotes.engine.utils.VirtualExecutors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * GitHubNavigator - Browse package source code before installation
 * 
 * Provides transparency by allowing users to:
 * - View repository structure
 * - Read source files
 * - Review README and documentation
 * - Check commit history
 * - Verify it matches the package
 * 
 * Flow:
 * 1. Show repository overview
 * 2. Navigate directory tree
 * 3. View file contents
 * 4. Return to installation with confidence (or decline)
 */
public class GitHubNavigator {
    
    private final GitHubInfo gitHubInfo;
    private final UIRenderer uiRenderer;
    private final ContextPath menuPath;
    private final String packageName;
    private final String version;
    
    // Navigation state
    private String currentPath = "";
    private final Stack<String> pathHistory = new Stack<>();
    
    // Result
    private CompletableFuture<NavigationResult> resultFuture = new CompletableFuture<>();
    
    public GitHubNavigator(
            GitHubInfo gitHubInfo,
            String packageName,
            String version,
            UIRenderer uiRenderer,
            ContextPath basePath) {
        
        this.gitHubInfo = gitHubInfo;
        this.packageName = packageName;
        this.version = version;
        this.uiRenderer = uiRenderer;
        this.menuPath = basePath.append("github-browser");
    }
    
    /**
     * Start navigation and wait for user decision
     */
    public CompletableFuture<NavigationResult> navigate() {
        showOverview();
        return resultFuture;
    }
    
    /**
     * Show repository overview
     */
    private void showOverview() {
        uiRenderer.render(UIProtocol.showMessage("Loading repository information..."));
        
        // Fetch README and basic repo info
        String readmeUrl = GitHubAPI.getUrlUserContentPath(
            gitHubInfo,
            gitHubInfo.getBranch(),
            "README.md"
        );
        
        UrlStreamHelpers.getUrlContentAsString(readmeUrl)
            .thenAccept(readme -> {
                MenuContext menu = new MenuContext(
                    menuPath.append("overview"),
                    gitHubInfo.getUser() + "/" + gitHubInfo.getProject(),
                    uiRenderer
                );
                
                // Repository info
                StringBuilder info = new StringBuilder();
                info.append("Package: ").append(packageName).append("\n");
                info.append("Version: ").append(version).append("\n");
                info.append("Source: GitHub\n");
                info.append("Branch: ").append(gitHubInfo.getBranch()).append("\n\n");
                
                menu.addInfoItem("repo_info", info.toString());
                
                // README preview (first 500 chars)
                if (readme != null && !readme.isEmpty()) {
                    String preview = readme.length() > 500 ? 
                        readme.substring(0, 500) + "..." : readme;
                    menu.addInfoItem("readme_preview", "README:\n" + preview);
                }
                
                menu.addSeparator("Browse Repository");
                
                menu.addItem(
                    "browse_files",
                    "📁 Browse Files",
                    "Navigate repository file structure",
                    () -> browseDirectory("")
                );
                
                menu.addItem(
                    "view_readme",
                    "📄 View Full README",
                    "Read complete README file",
                    () -> viewFile("README.md", readme)
                );
                
                menu.addItem(
                    "view_manifest",
                    "📋 View Package Manifest",
                    "Review package.json / manifest.json",
                    () -> viewManifest()
                );
                
                menu.addItem(
                    "releases",
                    "🏷️  View Releases",
                    "See release history and versions",
                    () -> viewReleases()
                );
                
                menu.addSeparator("Decision");
                
                menu.addItem(
                    "approve",
                    "✓ Continue Installation",
                    "Proceed with package installation",
                    () -> completeNavigation(true)
                );
                
                menu.addItem(
                    "decline",
                    "✗ Cancel Installation",
                    "Do not install this package",
                    () -> completeNavigation(false)
                );
                
                menu.display();
            })
            .exceptionally(ex -> {
                // README not found, continue without it
                showOverview();
                return null;
            });
    }
    
    /**
     * Browse directory contents
     */
    private void browseDirectory(String path) {
        uiRenderer.render(UIProtocol.showMessage("Loading directory..."));
        
        String apiUrl = GitHubAPI.getUrlRepoContentsPath(gitHubInfo, path);
        
        UrlStreamHelpers.getUrlJson(apiUrl, VirtualExecutors.getVirtualExecutor())
            .thenAccept(json -> {
                MenuContext menu = new MenuContext(
                    menuPath.append("browse").append(path.isEmpty() ? "root" : path),
                    "Browse: " + (path.isEmpty() ? "/" : path),
                    uiRenderer
                );
                
                if (json.isJsonArray()) {
                    JsonArray contents = json.getAsJsonArray();
                    
                    List<JsonObject> directories = new ArrayList<>();
                    List<JsonObject> files = new ArrayList<>();
                    
                    // Separate directories and files
                    for (JsonElement elem : contents) {
                        JsonObject item = elem.getAsJsonObject();
                        String type = item.get("type").getAsString();
                        
                        if ("dir".equals(type)) {
                            directories.add(item);
                        } else {
                            files.add(item);
                        }
                    }
                    
                    // Show directories first
                    if (!directories.isEmpty()) {
                        menu.addSeparator("Directories");
                        for (JsonObject dir : directories) {
                            String name = dir.get("name").getAsString();
                            String dirPath = dir.get("path").getAsString();
                            
                            menu.addItem(
                                "dir_" + name,
                                "📁 " + name,
                                "Open directory",
                                () -> {
                                    pathHistory.push(currentPath);
                                    currentPath = dirPath;
                                    browseDirectory(dirPath);
                                }
                            );
                        }
                    }
                    
                    // Show files
                    if (!files.isEmpty()) {
                        menu.addSeparator("Files");
                        for (JsonObject file : files) {
                            String name = file.get("name").getAsString();
                            String filePath = file.get("path").getAsString();
                            long size = file.get("size").getAsLong();
                            
                            String icon = getFileIcon(name);
                            String sizeStr = formatSize(size);
                            
                            menu.addItem(
                                "file_" + name,
                                icon + " " + name,
                                sizeStr,
                                () -> loadAndViewFile(filePath)
                            );
                        }
                    }
                }
                
                menu.addSeparator("Navigation");
                
                if (!pathHistory.isEmpty()) {
                    menu.addItem(
                        "back",
                        "← Back",
                        "Return to parent directory",
                        () -> {
                            currentPath = pathHistory.pop();
                            browseDirectory(currentPath);
                        }
                    );
                }
                
                menu.addItem(
                    "overview",
                    "🏠 Repository Overview",
                    "Return to repository overview",
                    () -> showOverview()
                );
                
                menu.display();
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError("Failed to load directory: " + ex.getMessage()));
                showOverview();
                return null;
            });
    }
    
    /**
     * Load and view a file
     */
    private void loadAndViewFile(String filePath) {
        uiRenderer.render(UIProtocol.showMessage("Loading file..."));
        
        String contentUrl = GitHubAPI.getUrlUserContentPath(
            gitHubInfo,
            gitHubInfo.getBranch(),
            filePath
        );
        
        UrlStreamHelpers.getUrlContentAsString(contentUrl)
            .thenAccept(content -> {
                viewFile(filePath, content);
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError("Failed to load file: " + ex.getMessage()));
                browseDirectory(currentPath);
                return null;
            });
    }
    
    /**
     * View file contents
     */
    private void viewFile(String fileName, String content) {
        MenuContext menu = new MenuContext(
            menuPath.append("view").append(fileName),
            "File: " + fileName,
            uiRenderer
        );
        
        // Show file content (truncate if too long)
        String displayContent = content;
        boolean truncated = false;
        
        if (content.length() > 5000) {
            displayContent = content.substring(0, 5000);
            truncated = true;
        }
        
        menu.addInfoItem("content", displayContent);
        
        if (truncated) {
            menu.addInfoItem("truncated", 
                "\n... (Content truncated, " + (content.length() - 5000) + 
                " more characters)\n\nView full file on GitHub");
        }
        
        menu.addSeparator("Actions");
        
        menu.addItem(
            "github",
            "🔗 View on GitHub",
            "Open file in web browser",
            () -> {
                String url = "https://github.com/" + 
                    gitHubInfo.getUser() + "/" +
                    gitHubInfo.getProject() + "/blob/" +
                    gitHubInfo.getBranch() + "/" + fileName;
                uiRenderer.render(UIProtocol.showMessage("GitHub URL: " + url));
                viewFile(fileName, content); // Redisplay
            }
        );
        
        menu.addItem(
            "back",
            "← Back to Files",
            "Return to directory",
            () -> browseDirectory(currentPath)
        );
        
        menu.display();
    }
    
    /**
     * View package manifest
     */
    private void viewManifest() {
        uiRenderer.render(UIProtocol.showMessage("Loading manifest..."));
        
        // Try common manifest file names
        String[] possibleNames = {
            "package.json",
            "manifest.json",
            "node-manifest.json"
        };
        
        CompletableFuture<String> manifestFuture = null;
        
        for (String name : possibleNames) {
            String url = GitHubAPI.getUrlUserContentPath(
                gitHubInfo,
                gitHubInfo.getBranch(),
                name
            );
            
            manifestFuture = UrlStreamHelpers.getUrlContentAsString(url)
                .exceptionally(ex -> null);
            
            // Try next if this one fails
            manifestFuture = manifestFuture.thenCompose(content -> {
                if (content != null) {
                    return CompletableFuture.completedFuture(content);
                }
                return CompletableFuture.completedFuture(null);
            });
        }
        
        manifestFuture.thenAccept(content -> {
            if (content != null) {
                viewFile("manifest.json", content);
            } else {
                uiRenderer.render(UIProtocol.showMessage("Manifest file not found in repository"));
                showOverview();
            }
        });
    }
    
    /**
     * View releases
     */
    private void viewReleases() {
        uiRenderer.render(UIProtocol.showMessage("Loading releases..."));
        
        GitHubAPI api = new GitHubAPI(gitHubInfo);
        
        api.getAssetsAllLatestRelease(VirtualExecutors.getVirtualExecutor())
            .thenAccept(assets -> {
                MenuContext menu = new MenuContext(
                    menuPath.append("releases"),
                    "Releases",
                    uiRenderer
                );
                
                if (assets.length == 0) {
                    menu.addInfoItem("no_releases", "No releases found");
                } else {
                    menu.addSeparator("Available Releases");
                    
                    for (GitHubAsset asset : assets) {
                        String releaseInfo = String.format(
                            "%s (%s)\nSize: %d KB\nDownloads: %d",
                            asset.getName(),
                            asset.getTagName(),
                            asset.getSize() / 1024,
                            asset.getDownloadCount()
                        );
                        
                        menu.addInfoItem("release_" + asset.getId(), releaseInfo);
                    }
                }
                
                menu.addItem(
                    "back",
                    "← Back",
                    "Return to overview",
                    () -> showOverview()
                );
                
                menu.display();
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError("Failed to load releases: " + ex.getMessage()));
                showOverview();
                return null;
            });
    }
    
    /**
     * Complete navigation
     */
    private void completeNavigation(boolean approved) {
        NavigationResult result = new NavigationResult(
            approved,
            gitHubInfo,
            pathHistory.size() > 0 // User actually browsed files
        );
        
        resultFuture.complete(result);
    }
    
    // ===== UTILITIES =====
    
    private String getFileIcon(String filename) {
        String lower = filename.toLowerCase();
        
        if (lower.endsWith(".java")) return "☕";
        if (lower.endsWith(".json")) return "📋";
        if (lower.endsWith(".md")) return "📄";
        if (lower.endsWith(".txt")) return "📝";
        if (lower.endsWith(".xml")) return "📰";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "⚙️";
        if (lower.endsWith(".jar")) return "📦";
        if (lower.endsWith(".class")) return "🔧";
        
        return "📄";
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
    
    // ===== RESULT =====
    
    public record NavigationResult(
        boolean approved,
        GitHubInfo gitHubInfo,
        boolean userBrowsedCode
    ) {
        /**
         * Did user actually review the code?
         */
        public boolean hasCodeReview() {
            return userBrowsedCode;
        }
        
        /**
         * Get trust modifier based on code review
         */
        public String getTrustModifier() {
            if (approved && userBrowsedCode) {
                return "User reviewed source code";
            } else if (approved) {
                return "User approved without code review";
            } else {
                return "User declined after review";
            }
        }
    }
}