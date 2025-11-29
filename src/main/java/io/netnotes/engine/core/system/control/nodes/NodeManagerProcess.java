package io.netnotes.engine.core.system.control.nodes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.ui.UICommands;
import io.netnotes.engine.core.system.control.ui.UIProtocol;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;

/**
 * NodeManagerProcess - Package manager for nodes (like apt-get)
 * 
 * SCOPE DEFINITION:
 * - Manages INSTALLATION metadata (what's available, what's installed)
 * - Handles REPOSITORIES (multiple sources, like apt sources.list)
 * - Parses MANIFESTS (JSON format, describes how to load nodes)
 * - Checks for UPDATES (compares installed vs available versions)
 * - Downloads/installs/uninstalls packages
 * 
 * NOT IN SCOPE:
 * - Runtime node lifecycle (that's AppData's job)
 * - OSGi bundle management (AppData coordinates with OSGi)
 * - Active node instances (AppData manages those)
 * - Process subscription routing (ProcessRegistry handles)
 * 
 * Architecture:
 * - NodeManager is LAZY - builds up when needed, dies when done
 * - Installation registry is SEPARATE from runtime registry (AppData)
 * - User is in FULL CONTROL - can add/remove repositories
 * - NO central authority - facilitates user choice
 * 
 * Analogy: apt-get vs running processes
 * - NodeManager = apt-get (package management)
 * - AppData = systemd (service/runtime management)
 */
public class NodeManagerProcess extends FlowProcess {
    
    private final BitFlagStateMachine state;
    private final AppData appData;
    private final UIRenderer uiRenderer;
    
    // Package management (installation metadata ONLY)

    private PackageCache packageCache;
    
    // Current view state
    private MenuContext currentMenu;
    
    // Message dispatch
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_routedMsgMap = 
        new ConcurrentHashMap<>();
    
    // States
    public static final long INITIALIZING = 1L << 0;
    public static final long READY = 1L << 1;
    public static final long BROWSING = 1L << 2;
    public static final long INSTALLING = 1L << 3;
    public static final long UNINSTALLING = 1L << 4;
    public static final long UPDATING_REPOS = 1L << 5;
    
    public NodeManagerProcess(AppData appData, UIRenderer uiRenderer) {
        super(ProcessType.BIDIRECTIONAL);
        this.appData = appData;
        this.uiRenderer = uiRenderer;
        this.state = new BitFlagStateMachine("node-manager");
        
        setupMessageMapping();
        setupStateTransitions();
    }

    private InstallationRegistry getInstallationRegistry() {
        return appData.getInstallationRegistry();
    }

    private RepositoryManager getRepositoryManager() {
        return appData.getRepositoryManager();
    }
    
    private void setupMessageMapping() {
        m_routedMsgMap.put(UICommands.UI_MENU_SELECTED, this::handleMenuSelection);
        m_routedMsgMap.put(UICommands.UI_BACK, this::handleBack);
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(INITIALIZING, (old, now, bit) -> {
            System.out.println("[NodeManager] INITIALIZING - Loading installation registry");
        });
        
        state.onStateAdded(READY, (old, now, bit) -> {
            System.out.println("[NodeManager] READY - Package manager operational");
        });
    }
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(INITIALIZING);
        
        return initialize()
            .thenRun(() -> {
                state.removeState(INITIALIZING);
                state.addState(READY);
                showMainMenu();
            });
    }
    
    private CompletableFuture<Void> initialize() {
        // Build UI
        showMainMenu();
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== MENU SYSTEM =====
    
    private void showMainMenu() {
        ContextPath menuPath = contextPath.append("menu", "main");
        MenuContext menu = new MenuContext(menuPath, "Node Manager", uiRenderer);
        
        // Statistics
        int installed = getInstallationRegistry().getInstalledPackages().size();
        int available = packageCache.getAllPackages().size();
        int repos = getRepositoryManager().getRepositories().size();
        
        menu.addInfoItem("stats", String.format(
            "Installed: %d | Available: %d | Repositories: %d",
            installed, available, repos
        ));
        menu.addSeparator("Package Management");
        
        menu.addItem("update", "Update Package Lists",
            "Fetch latest package information from repositories", () -> {
            updatePackageLists();
        });
        
        menu.addItem("search", "Search Packages",
            "Find packages by name or description", () -> {
            showSearchMenu();
        });
        
        menu.addItem("install", "Install Package",
            "Download and install a package", () -> {
            showInstallMenu();
        });
        
        menu.addItem("remove", "Remove Package",
            "Uninstall an installed package", () -> {
            showRemoveMenu();
        });
        
        menu.addItem("list", "List Installed",
            "Show all installed packages", () -> {
            showInstalledMenu();
        });
        
        menu.addSeparator("Repository Management");
        
        menu.addItem("repos", "Manage Repositories",
            "Add, remove, or configure repositories", () -> {
            showRepositoryMenu();
        });
        
        menu.addSeparator("Runtime Control");
        
        menu.addItem("load", "Load Node (Start)",
            "Request AppData to load a node into runtime", () -> {
            showLoadMenu();
        });
        
        menu.addItem("unload", "Unload Node (Stop)",
            "Request AppData to unload a running node", () -> {
            showUnloadMenu();
        });
        
        menu.addItem("status", "Runtime Status",
            "View loaded nodes from AppData", () -> {
            showRuntimeStatus();
        });
        
        menu.addSeparator("");
        menu.addItem("back", "Back to System Menu", () -> {
            notifyParent("close_node_manager");
        });
        
        currentMenu = menu;
        menu.display();
    }
    
    // ===== PACKAGE MANAGEMENT =====
    
    /**
     * Update package lists (like apt-get update)
     * Fetches latest manifests from all repositories
     */
    private void updatePackageLists() {
        state.addState(UPDATING_REPOS);
        
        uiRenderer.render(UIProtocol.showMessage(
            "Updating package lists from repositories..."));
        
        getRepositoryManager().updateAllRepositories()
            .thenAccept(packages -> {
                // Cache the packages
                packageCache.updateCache(packages);
                
                state.removeState(UPDATING_REPOS);
                
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Package lists updated - " + packages.size() + 
                    " packages available"));
                
                showMainMenu();
            })
            .exceptionally(ex -> {
                state.removeState(UPDATING_REPOS);
                
                uiRenderer.render(UIProtocol.showError(
                    "Update failed: " + ex.getMessage()));
                
                showMainMenu();
                return null;
            });
    }
    
    private void showSearchMenu() {
        // TODO: Implement search with filters
        uiRenderer.render(UIProtocol.showMessage("Search coming soon"));
        showMainMenu();
    }
    
    private void showInstallMenu() {
        List<PackageInfo> available = packageCache.getAllPackages();
        
        if (available.isEmpty()) {
            uiRenderer.render(UIProtocol.showMessage(
                "No packages available. Run 'Update Package Lists' first."));
            showMainMenu();
            return;
        }
        
        ContextPath menuPath = contextPath.append("menu", "install");
        MenuContext menu = new MenuContext(menuPath, "Install Package", 
            uiRenderer, currentMenu);
        
        menu.addInfoItem("info", "Select a package to install");
        menu.addSeparator("Available Packages");
        
        // Group by category
        Map<String, List<PackageInfo>> byCategory = 
            available.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    PackageInfo::getCategory));
        
        for (Map.Entry<String, List<PackageInfo>> entry : byCategory.entrySet()) {
            menu.addSubMenu(entry.getKey(), entry.getKey(), subMenu -> {
                for (PackageInfo pkg : entry.getValue()) {
                    subMenu.addItem(
                        pkg.getPackageId().getAsString(),
                        pkg.getName() + " (" + pkg.getVersion() + ")",
                        pkg.getDescription(),
                        () -> confirmInstall(pkg)
                    );
                }
                return subMenu;
            });
        }
        
        menu.addItem("back", "Back", () -> showMainMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    private void confirmInstall(PackageInfo pkg) {
        ContextPath menuPath = contextPath.append("menu", "confirm-install");
        MenuContext menu = new MenuContext(menuPath, "Confirm Install", 
            uiRenderer, currentMenu);
        
        String info = String.format(
            "Package: %s\nVersion: %s\nSize: %d KB\nRepository: %s\n\nInstall this package?",
            pkg.getName(),
            pkg.getVersion(),
            pkg.getSize() / 1024,
            pkg.getRepository()
        );
        menu.addInfoItem("details", info);
        menu.addSeparator("Actions");
        
        menu.addItem("confirm", "Yes, Install", () -> {
            installPackage(pkg);
        });
        
        menu.addItem("cancel", "No, Cancel", () -> {
            showInstallMenu();
        });
        
        currentMenu = menu;
        menu.display();
    }
    
    /**
     * Install a package (like apt-get install)
     * 1. Download package files
     * 2. Verify manifest
     * 3. Store in installation registry
     * 4. DO NOT load into runtime (user does that separately)
     */
    private void installPackage(PackageInfo pkg) {
        state.addState(INSTALLING);
        
        uiRenderer.render(UIProtocol.showMessage(
            "Installing " + pkg.getName() + "..."));
        
        // Download package
        PackageInstaller installer = new PackageInstaller(appData.getSystemInterface("packages"));
        
        installer.installPackage(pkg)
            .thenCompose(installedPkg -> {
                // Register in installation registry
                return getInstallationRegistry().registerPackage(installedPkg);
            })
            .thenRun(() -> {
                state.removeState(INSTALLING);
                
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Package installed: " + pkg.getName() + "\n\n" +
                    "To use this node, select 'Load Node (Start)' from the main menu."));
                
                showMainMenu();
            })
            .exceptionally(ex -> {
                state.removeState(INSTALLING);
                
                uiRenderer.render(UIProtocol.showError(
                    "Installation failed: " + ex.getMessage()));
                
                showInstallMenu();
                return null;
            });
    }
    
    private void showRemoveMenu() {
        List<InstalledPackage> installed = getInstallationRegistry().getInstalledPackages();
        
        if (installed.isEmpty()) {
            uiRenderer.render(UIProtocol.showMessage("No packages installed"));
            showMainMenu();
            return;
        }
        
        ContextPath menuPath = contextPath.append("menu", "remove");
        MenuContext menu = new MenuContext(menuPath, "Remove Package", 
            uiRenderer, currentMenu);
        
        menu.addInfoItem("info", "Select a package to remove");
        menu.addSeparator("Installed Packages");
        
        for (InstalledPackage pkg : installed) {
            menu.addItem(
                pkg.getPackageId().getAsString(),
                pkg.getName() + " (" + pkg.getVersion() + ")",
                () -> confirmRemove(pkg)
            );
        }
        
        menu.addItem("back", "Back", () -> showMainMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    private void confirmRemove(InstalledPackage pkg) {
        ContextPath menuPath = contextPath.append("menu", "confirm-remove");
        MenuContext menu = new MenuContext(menuPath, "Confirm Remove", 
            uiRenderer, currentMenu);
        
        menu.addInfoItem("warning", 
            "⚠️ Remove " + pkg.getName() + "?\n\n" +
            "This will delete the package files.\n" +
            "If the node is currently loaded, unload it first.");
        menu.addSeparator("Actions");
        
        menu.addItem("confirm", "Yes, Remove", () -> {
            removePackage(pkg);
        });
        
        menu.addItem("cancel", "No, Cancel", () -> {
            showRemoveMenu();
        });
        
        currentMenu = menu;
        menu.display();
    }
    
    private void removePackage(InstalledPackage pkg) {
        state.addState(UNINSTALLING);
        
        uiRenderer.render(UIProtocol.showMessage(
            "Removing " + pkg.getName() + "..."));
        
        // Check if loaded in runtime
        appData.nodeRegistry().values().stream()
            .filter(node -> pkg.getPackageId().equals(
                node.getNodeId().toString()))
            .findFirst()
            .ifPresent(node -> {
                uiRenderer.render(UIProtocol.showError(
                    "⚠️ Node is currently loaded in runtime!\n" +
                    "Unload it first via 'Unload Node (Stop)'."));
                state.removeState(UNINSTALLING);
                showRemoveMenu();
                return;
            });
        
        // Unregister and delete
        getInstallationRegistry().unregisterPackage(pkg.getPackageId())
            .thenCompose(v -> appData.getNoteFileService().deleteNoteFilePath(
                pkg.getInstallPath(), true, null))
            .thenRun(() -> {
                state.removeState(UNINSTALLING);
                
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Package removed: " + pkg.getName()));
                
                showMainMenu();
            })
            .exceptionally(ex -> {
                state.removeState(UNINSTALLING);
                
                uiRenderer.render(UIProtocol.showError(
                    "Removal failed: " + ex.getMessage()));
                
                showRemoveMenu();
                return null;
            });
    }
    
    private void showInstalledMenu() {
        List<InstalledPackage> installed = getInstallationRegistry().getInstalledPackages();
        
        if (installed.isEmpty()) {
            uiRenderer.render(UIProtocol.showMessage("No packages installed"));
            showMainMenu();
            return;
        }
        
        ContextPath menuPath = contextPath.append("menu", "installed");
        MenuContext menu = new MenuContext(menuPath, "Installed Packages", 
            uiRenderer, currentMenu);
        
        menu.addInfoItem("info", installed.size() + " package(s) installed");
        menu.addSeparator("Packages");
        
        for (InstalledPackage pkg : installed) {
            menu.addItem(
                pkg.getPackageId().getAsString(),
                pkg.getName() + " " + pkg.getVersion(),
                pkg.getDescription(),
                () -> showPackageDetails(pkg)
            );
        }
        
        menu.addItem("back", "Back", () -> showMainMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    private void showPackageDetails(InstalledPackage pkg) {
        ContextPath menuPath = contextPath.append("menu", "package-details");
        MenuContext menu = new MenuContext(menuPath, pkg.getName(), 
            uiRenderer, currentMenu);
        
        String details = String.format(
            "Name: %s\nVersion: %s\nCategory: %s\nRepository: %s\n\n%s",
            pkg.getName(),
            pkg.getVersion(),
            pkg.getCategory(),
            pkg.getRepository(),
            pkg.getDescription()
        );
        menu.addInfoItem("details", details);
        menu.addSeparator("Actions");
        
        menu.addItem("remove", "Remove Package", () -> {
            confirmRemove(pkg);
        });
        
        menu.addItem("back", "Back", () -> showInstalledMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    // ===== REPOSITORY MANAGEMENT =====
    
    private void showRepositoryMenu() {
        List<Repository> repos = getRepositoryManager().getRepositories();
        
        ContextPath menuPath = contextPath.append("menu", "repositories");
        MenuContext menu = new MenuContext(menuPath, "Repository Management", 
            uiRenderer, currentMenu);
        
        menu.addInfoItem("info", repos.size() + " repository(ies) configured");
        menu.addSeparator("Repositories");
        
        for (Repository repo : repos) {
            String status = repo.isEnabled() ? "✓ " : "○ ";
            menu.addItem(
                repo.getId(),
                status + repo.getName(),
                repo.getUrl(),
                () -> showRepositoryDetails(repo)
            );
        }
        
        menu.addSeparator("Actions");
        
        menu.addItem("add", "Add Repository",
            "Add a new package source", () -> {
            // TODO: Add repository flow
            uiRenderer.render(UIProtocol.showMessage("Add repository coming soon"));
        });
        
        menu.addItem("back", "Back", () -> showMainMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    private void showRepositoryDetails(Repository repo) {
        ContextPath menuPath = contextPath.append("menu", "repo-details");
        MenuContext menu = new MenuContext(menuPath, repo.getName(), 
            uiRenderer, currentMenu);
        
        String details = String.format(
            "Name: %s\nURL: %s\nStatus: %s\nKey: %s",
            repo.getName(),
            repo.getUrl(),
            repo.isEnabled() ? "Enabled" : "Disabled",
            repo.hasKey() ? "✓ Verified" : "⚠️ No key"
        );
        menu.addInfoItem("details", details);
        menu.addSeparator("Actions");
        
        if (repo.isEnabled()) {
            menu.addItem("disable", "Disable Repository", () -> {
                getRepositoryManager().setRepositoryEnabled(repo.getId(), false);
                showRepositoryMenu();
            });
        } else {
            menu.addItem("enable", "Enable Repository", () -> {
                getRepositoryManager().setRepositoryEnabled(repo.getId(), true);
                showRepositoryMenu();
            });
        }
        
        menu.addItem("remove", "Remove Repository", () -> {
            getRepositoryManager().removeRepository(repo.getId());
            showRepositoryMenu();
        });
        
        menu.addItem("back", "Back", () -> showRepositoryMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    // ===== RUNTIME CONTROL (Coordinates with AppData) =====
    
    /**
     * Load a node into runtime
     * This REQUESTS AppData to load the node (doesn't do it directly)
     */
    private void showLoadMenu() {
        List<InstalledPackage> installed = getInstallationRegistry().getInstalledPackages();
        
        if (installed.isEmpty()) {
            uiRenderer.render(UIProtocol.showMessage(
                "No packages installed. Install packages first."));
            showMainMenu();
            return;
        }
        
        ContextPath menuPath = contextPath.append("menu", "load");
        MenuContext menu = new MenuContext(menuPath, "Load Node", 
            uiRenderer, currentMenu);
        
        menu.addInfoItem("info", "Select a node to load into runtime");
        menu.addSeparator("Installed Packages");
        
        for (InstalledPackage pkg : installed) {
            // Check if already loaded
            boolean loaded = appData.nodeRegistry().values().stream()
                .anyMatch(node -> pkg.getPackageId().equals(node.getNodeId()));
            
            String label = pkg.getName() + 
                (loaded ? " (already loaded)" : "");
            
            menu.addItem(
                pkg.getPackageId().getAsString(),
                label,
                () -> {
                    if (loaded) {
                        uiRenderer.render(UIProtocol.showMessage(
                            "Node already loaded in runtime"));
                        showLoadMenu();
                    } else {
                        requestLoadNode(pkg);
                    }
                }
            );
        }
        
        menu.addItem("back", "Back", () -> showMainMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    /**
     * Request AppData to load a node
     * NodeManager does NOT load nodes - it just requests AppData to do it
     */
    private void requestLoadNode(InstalledPackage pkg) {
        uiRenderer.render(UIProtocol.showMessage(
            "Requesting AppData to load " + pkg.getName() + "..."));
        
        // Send message to AppData (via parent SystemSessionProcess)
        NoteBytesMap request = new NoteBytesMap();
        request.put(Keys.CMD, new NoteBytes("load_node"));
        request.put(new NoteBytes("package_id"), new NoteBytes(pkg.getPackageId()));
        request.put(new NoteBytes("install_path"), pkg.getInstallPath().getSegments());
        
        if (parentPath != null) {
            emitTo(parentPath, request.getNoteBytesObject());
        }
        
        // Show loading message
        uiRenderer.render(UIProtocol.showMessage(
            "Load request sent to AppData.\n" +
            "Check 'Runtime Status' to see loaded nodes."));
        
        showMainMenu();
    }
    
    private void showUnloadMenu() {
        // Get loaded nodes from AppData
        var loadedNodes = appData.nodeRegistry();
        
        if (loadedNodes.isEmpty()) {
            uiRenderer.render(UIProtocol.showMessage("No nodes loaded in runtime"));
            showMainMenu();
            return;
        }
        
        ContextPath menuPath = contextPath.append("menu", "unload");
        MenuContext menu = new MenuContext(menuPath, "Unload Node", 
            uiRenderer, currentMenu);
        
        menu.addInfoItem("info", "Select a node to unload from runtime");
        menu.addSeparator("Loaded Nodes");
        
        for (NoteBytesReadOnly nodeId : loadedNodes.keySet()) {
      
            menu.addItem(
                nodeId.getAsString(),
                "Node: " + nodeId.getAsString(),
                () -> requestUnloadNode(nodeId)
            );
        }
        
        menu.addItem("back", "Back", () -> showMainMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    private void requestUnloadNode(NoteBytesReadOnly nodeId) {
        uiRenderer.render(UIProtocol.showMessage(
            "Requesting AppData to unload node..."));
        
        // Send message to AppData
        NoteBytesMap request = new NoteBytesMap();
        request.put(Keys.CMD, new NoteBytes("unload_node"));
        request.put(new NoteBytes("node_id"), new NoteBytes(nodeId.getAsString()));
        
        if (parentPath != null) {
            emitTo(parentPath, request.getNoteBytesObject());
        }
        
        uiRenderer.render(UIProtocol.showMessage("Unload request sent to AppData"));
        showMainMenu();
    }
    
    private void showRuntimeStatus() {
        var loadedNodes = appData.nodeRegistry();
        
        ContextPath menuPath = contextPath.append("menu", "runtime-status");
        MenuContext menu = new MenuContext(menuPath, "Runtime Status", 
            uiRenderer, currentMenu);
        
        if (loadedNodes.isEmpty()) {
            menu.addInfoItem("status", "No nodes currently loaded in runtime");
        } else {
            menu.addInfoItem("status", 
                loadedNodes.size() + " node(s) loaded in runtime");
            menu.addSeparator("Loaded Nodes");
            
            for (var entry : loadedNodes.entrySet()) {
                menu.addInfoItem(
                    entry.getKey().getAsString(),
                    "✓ " + entry.getKey().getAsString()
                );
            }
        }
        
        menu.addSeparator("");
        menu.addItem("back", "Back", () -> showMainMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    // ===== MESSAGE HANDLERS =====
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        try {
            NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
            NoteBytesReadOnly cmd = msg.getReadOnly(Keys.CMD);
            
            if (cmd == null) {
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("'cmd' required"));
            }
            
            RoutedMessageExecutor executor = m_routedMsgMap.get(cmd);
            if (executor != null) {
                return executor.execute(msg, packet);
            }
            
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private CompletableFuture<Void> handleMenuSelection(
            NoteBytesMap msg, RoutedPacket packet) {
        // Menu handles navigation
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleBack(
            NoteBytesMap msg, RoutedPacket packet) {
        if (currentMenu != null && currentMenu.hasParent()) {
            currentMenu = currentMenu.getParent();
            currentMenu.display();
        } else {
            showMainMenu();
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException();
    }
    
    private void notifyParent(String event) {
        if (parentPath != null) {
            NoteBytesMap notify = new NoteBytesMap();
            notify.put(Keys.CMD, new NoteBytes(event));
            emitTo(parentPath, notify.getNoteBytesObject());
        }
    }
    
    /**
     * Shutdown - NodeManager is lazy, dies when not needed
     */
    public void shutdown() {
        //TODO: shudown?
    }
}