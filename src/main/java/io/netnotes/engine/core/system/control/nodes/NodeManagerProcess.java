package io.netnotes.engine.core.system.control.nodes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
 * NodeManagerProcess - Manages plugin/node lifecycle
 * 
 * Responsibilities:
 * - Browse available nodes (from registry/marketplace)
 * - Install/uninstall nodes
 * - Enable/disable nodes
 * - Manage node versions
 * - Configure node settings
 * - Monitor node status
 * 
 * Architecture:
 * - Uses NodeRegistry for persistence
 * - Uses NodeGroupManager for grouping versions
 * - Uses NodeInstaller for download/installation
 * - Uses MenuContext for UI navigation
 * - GUI-independent (delegates to UIRenderer)
 * 
 * States:
 * - INITIALIZING: Loading registry
 * - READY: Operational
 * - BROWSING: Showing available nodes
 * - INSTALLING: Installing a node
 * - UNINSTALLING: Removing a node
 * - CONFIGURING: Adjusting node settings
 */
public class NodeManagerProcess extends FlowProcess {
    
    private final BitFlagStateMachine state;
    private final AppData appData;
    private final UIRenderer uiRenderer;
    
    // Node management components
    private NodeRegistry nodeRegistry;
    private NodeGroupManager groupManager;
    private NodeInstaller installer;
    private NodeMarketplace marketplace;
    
    // Current view state
    private MenuContext currentMenu;
    private NodeGroup selectedGroup;
    
    // Message dispatch
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_routedMsgMap = 
        new ConcurrentHashMap<>();
    
    // States
    public static final long INITIALIZING = 1L << 0;
    public static final long READY = 1L << 1;
    public static final long BROWSING = 1L << 2;
    public static final long INSTALLING = 1L << 3;
    public static final long UNINSTALLING = 1L << 4;
    public static final long CONFIGURING = 1L << 5;
    public static final long ERROR = 1L << 6;
    
    public NodeManagerProcess(AppData appData, UIRenderer uiRenderer) {
        super(ProcessType.BIDIRECTIONAL);
        this.appData = appData;
        this.uiRenderer = uiRenderer;
        this.state = new BitFlagStateMachine("node-manager");
        
        setupMessageMapping();
        setupStateTransitions();
    }
    
    private void setupMessageMapping() {
        // Menu navigation
        m_routedMsgMap.put(UICommands.UI_MENU_SELECTED, this::handleMenuSelection);
        m_routedMsgMap.put(UICommands.UI_BACK, this::handleBack);
        
        // Node operations
        m_routedMsgMap.put(new NoteBytesReadOnly("install_node"), this::handleInstallNode);
        m_routedMsgMap.put(new NoteBytesReadOnly("uninstall_node"), this::handleUninstallNode);
        m_routedMsgMap.put(new NoteBytesReadOnly("enable_node"), this::handleEnableNode);
        m_routedMsgMap.put(new NoteBytesReadOnly("disable_node"), this::handleDisableNode);
        m_routedMsgMap.put(new NoteBytesReadOnly("configure_node"), this::handleConfigureNode);
        m_routedMsgMap.put(new NoteBytesReadOnly("refresh_marketplace"), this::handleRefreshMarketplace);
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(INITIALIZING, (old, now, bit) -> {
            System.out.println("[NodeManager] INITIALIZING - Loading node registry");
        });
        
        state.onStateAdded(READY, (old, now, bit) -> {
            System.out.println("[NodeManager] READY - Node manager operational");
        });
        
        state.onStateAdded(BROWSING, (old, now, bit) -> {
            System.out.println("[NodeManager] BROWSING - Showing available nodes");
        });
        
        state.onStateAdded(INSTALLING, (old, now, bit) -> {
            System.out.println("[NodeManager] INSTALLING - Installing node");
        });
        
        state.onStateAdded(ERROR, (old, now, bit) -> {
            System.err.println("[NodeManager] ERROR - Error occurred");
        });
    }
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(INITIALIZING);
        
        return initialize()
            .thenRun(() -> {
                state.removeState(INITIALIZING);
                state.addState(READY);
                
                // Show main menu
                showMainMenu();
            })
            .exceptionally(ex -> {
                System.err.println("[NodeManager] Initialization failed: " + ex.getMessage());
                state.addState(ERROR);
                return null;
            });
    }
    
    private CompletableFuture<Void> initialize() {
        // Initialize components
        nodeRegistry = new NodeRegistry(appData);
        groupManager = new NodeGroupManager();
        installer = new NodeInstaller(appData);
        marketplace = new NodeMarketplace(appData.getExecService());
        
        // Load registry
        return nodeRegistry.initialize()
            .thenCompose(v -> marketplace.loadAvailableNodes())
            .thenAccept(availableNodes -> {
                // Build grouped view
                groupManager.buildFromRegistry(
                    nodeRegistry.getAllNodes(),
                    availableNodes
                );
                
                System.out.println("[NodeManager] Loaded " + 
                    availableNodes.size() + " available nodes, " +
                    nodeRegistry.getAllNodes().size() + " installed");
            });
    }
    
    // ===== MENU SYSTEM =====
    
    private void showMainMenu() {
        ContextPath menuPath = contextPath.append("menu", "main");
        MenuContext menu = new MenuContext(menuPath, "Node Manager", uiRenderer);
        
        // Statistics
        String stats = String.format(
            "Available: %d | Installed: %d | Enabled: %d",
            groupManager.getAvailableCount(),
            groupManager.getInstalledCount(),
            groupManager.getEnabledCount()
        );
        menu.addInfoItem("stats", stats);
        menu.addSeparator("Actions");
        
        // Main actions
        menu.addItem("browse", "Browse Available Nodes", 
            "Explore and install new nodes", () -> {
            state.addState(BROWSING);
            showBrowseMenu();
        });
        
        menu.addItem("installed", "Manage Installed Nodes",
            "View and configure installed nodes", () -> {
            showInstalledMenu();
        });
        
        menu.addItem("refresh", "Refresh Marketplace",
            "Check for new nodes and updates", () -> {
            refreshMarketplace();
        });
        
        menu.addItem("back", "Back to System Menu", () -> {
            // Notify parent to return to system menu
            notifyParent("close_node_manager");
        });
        
        currentMenu = menu;
        menu.display();
    }
    
    private void showBrowseMenu() {
        List<NodeGroup> groups = groupManager.getBrowseGroups();
        
        if (groups.isEmpty()) {
            uiRenderer.render(UIProtocol.showMessage(
                "No nodes available. Try refreshing the marketplace."));
            showMainMenu();
            return;
        }
        
        ContextPath menuPath = contextPath.append("menu", "browse");
        MenuContext menu = new MenuContext(menuPath, "Browse Nodes", uiRenderer, currentMenu);
        
        menu.addInfoItem("info", "Select a node to view details");
        menu.addSeparator("Available Nodes");
        
        // Group nodes by category
        Map<String, List<NodeGroup>> byCategory = groups.stream()
            .collect(Collectors.groupingBy(g -> g.getNodeInfo().getCategory()));
        
        for (Map.Entry<String, List<NodeGroup>> entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<NodeGroup> categoryGroups = entry.getValue();
            
            menu.addSubMenu(category, category, subMenu -> {
                for (NodeGroup group : categoryGroups) {
                    subMenu.addItem(
                        group.getNodeId(),
                        group.getNodeInfo().getName(),
                        group.getNodeInfo().getDescription(),
                        () -> showNodeDetails(group)
                    );
                }
                return subMenu;
            });
        }
        
        menu.addSeparator("");
        menu.addItem("back", "Back to Main Menu", () -> showMainMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    private void showInstalledMenu() {
        List<NodeGroup> groups = groupManager.getInstalledGroups();
        
        if (groups.isEmpty()) {
            uiRenderer.render(UIProtocol.showMessage(
                "No nodes installed. Browse the marketplace to install nodes."));
            showMainMenu();
            return;
        }
        
        ContextPath menuPath = contextPath.append("menu", "installed");
        MenuContext menu = new MenuContext(menuPath, "Installed Nodes", uiRenderer, currentMenu);
        
        menu.addInfoItem("info", "Manage your installed nodes");
        menu.addSeparator("Installed Nodes");
        
        for (NodeGroup group : groups) {
            NodeMetadata enabled = group.getEnabledVersion();
            String status = enabled != null ? "✓ Enabled" : "○ Disabled";
            
            menu.addItem(
                group.getNodeId(),
                group.getNodeInfo().getName() + " - " + status,
                group.getInstalledVersions().size() + " version(s) installed",
                () -> showInstalledNodeDetails(group)
            );
        }
        
        menu.addSeparator("");
        menu.addItem("back", "Back to Main Menu", () -> showMainMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    private void showNodeDetails(NodeGroup group) {
        selectedGroup = group;
        
        ContextPath menuPath = contextPath.append("menu", "node-details", group.getNodeId());
        MenuContext menu = new MenuContext(menuPath, 
            group.getNodeInfo().getName(), uiRenderer, currentMenu);
        
        // Node information
        NodeInformation info = group.getNodeInfo();
        String description = String.format(
            "%s\n\nCategory: %s\nAuthor: %s\nVersion: %s",
            info.getDescription(),
            info.getCategory(),
            info.getAuthor(),
            info.getLatestVersion()
        );
        menu.addInfoItem("description", description);
        menu.addSeparator("Actions");
        
        // Actions based on installation status
        if (group.hasInstalledVersions()) {
            menu.addItem("manage", "Manage Versions",
                "View and configure installed versions", () -> {
                showInstalledNodeDetails(group);
            });
            
            menu.addItem("uninstall", "Uninstall All Versions",
                "⚠️ Remove all installed versions", () -> {
                confirmUninstallAll(group);
            });
        } else {
            menu.addItem("install", "Install Latest Version",
                "Download and install this node", () -> {
                installLatestVersion(group);
            });
        }
        
        menu.addItem("back", "Back to Browse", () -> showBrowseMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    private void showInstalledNodeDetails(NodeGroup group) {
        selectedGroup = group;
        
        ContextPath menuPath = contextPath.append("menu", "installed-details", group.getNodeId());
        MenuContext menu = new MenuContext(menuPath,
            group.getNodeInfo().getName() + " - Versions", uiRenderer, currentMenu);
        
        List<NodeMetadata> versions = group.getInstalledVersions();
        NodeMetadata enabled = group.getEnabledVersion();
        
        menu.addInfoItem("info", versions.size() + " version(s) installed");
        menu.addSeparator("Installed Versions");
        
        for (NodeMetadata version : versions) {
            boolean isEnabled = version.equals(enabled);
            String status = isEnabled ? "✓ Enabled" : "○ Disabled";
            
            menu.addSubMenu(
                version.getNodeId(),
                version.getVersion() + " - " + status,
                versionMenu -> {
                    versionMenu.addInfoItem("details",
                        "Version: " + version.getVersion() + "\n" +
                        "Status: " + (isEnabled ? "Enabled" : "Disabled"));
                    versionMenu.addSeparator("Actions");
                    
                    if (!isEnabled) {
                        versionMenu.addItem("enable", "Enable This Version",
                            () -> enableVersion(version));
                    } else {
                        versionMenu.addItem("disable", "Disable",
                            () -> disableVersion(version));
                    }
                    
                    versionMenu.addItem("configure", "Configure",
                        () -> configureNode(version));
                    
                    versionMenu.addItem("uninstall", "Uninstall",
                        "⚠️ Remove this version", () -> {
                        confirmUninstall(version);
                    });
                    
                    return versionMenu;
                }
            );
        }
        
        menu.addSeparator("");
        menu.addItem("back", "Back to Installed", () -> showInstalledMenu());
        
        currentMenu = menu;
        menu.display();
    }
    
    // ===== NODE OPERATIONS =====
    
    private void installLatestVersion(NodeGroup group) {
        state.addState(INSTALLING);
        
        uiRenderer.render(UIProtocol.showMessage(
            "Installing " + group.getNodeInfo().getName() + "..."));
        
        marketplace.getLatestRelease(group.getNodeInfo())
            .thenCompose(release -> {
                if (release == null) {
                    throw new RuntimeException("No releases available");
                }
                return installer.installNode(release, true);
            })
            .thenAccept(metadata -> {
                state.removeState(INSTALLING);
                
                // Refresh group manager
                refreshGroupManager();
                
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Successfully installed " + metadata.getName() + 
                    " version " + metadata.getVersion()));
                
                // Return to browse menu
                showBrowseMenu();
            })
            .exceptionally(ex -> {
                state.removeState(INSTALLING);
                
                uiRenderer.render(UIProtocol.showError(
                    "Installation failed: " + ex.getMessage()));
                
                showNodeDetails(group);
                return null;
            });
    }
    
    private void confirmUninstall(NodeMetadata metadata) {
        // Show confirmation menu
        ContextPath menuPath = contextPath.append("menu", "confirm-uninstall");
        MenuContext menu = new MenuContext(menuPath,
            "Confirm Uninstall", uiRenderer, currentMenu);
        
        menu.addInfoItem("warning",
            "⚠️ Are you sure you want to uninstall " + 
            metadata.getName() + " version " + metadata.getVersion() + "?");
        menu.addSeparator("Actions");
        
        menu.addItem("confirm", "Yes, Uninstall",
            "This cannot be undone", () -> {
            uninstallVersion(metadata);
        });
        
        menu.addItem("cancel", "No, Cancel", () -> {
            showInstalledNodeDetails(selectedGroup);
        });
        
        currentMenu = menu;
        menu.display();
    }
    
    private void confirmUninstallAll(NodeGroup group) {
        ContextPath menuPath = contextPath.append("menu", "confirm-uninstall-all");
        MenuContext menu = new MenuContext(menuPath,
            "Confirm Uninstall All", uiRenderer, currentMenu);
        
        menu.addInfoItem("warning",
            "⚠️ Are you sure you want to uninstall ALL versions of " + 
            group.getNodeInfo().getName() + "?\n\n" +
            "This will remove " + group.getInstalledVersions().size() + " version(s).");
        menu.addSeparator("Actions");
        
        menu.addItem("confirm", "Yes, Uninstall All",
            "This cannot be undone", () -> {
            uninstallAllVersions(group);
        });
        
        menu.addItem("cancel", "No, Cancel", () -> {
            showNodeDetails(group);
        });
        
        currentMenu = menu;
        menu.display();
    }
    
    private void uninstallVersion(NodeMetadata metadata) {
        state.addState(UNINSTALLING);
        
        uiRenderer.render(UIProtocol.showMessage(
            "Uninstalling " + metadata.getName() + "..."));
        
        nodeRegistry.unregisterNode(metadata.getNodeId())
            .thenCompose(v -> appData.getNoteFileService().deleteNoteFilePath(
                metadata.getNodePath(), false, null))
            .thenRun(() -> {
                state.removeState(UNINSTALLING);
                
                // Refresh group manager
                refreshGroupManager();
                
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Successfully uninstalled " + metadata.getName()));
                
                // Return to installed menu
                showInstalledMenu();
            })
            .exceptionally(ex -> {
                state.removeState(UNINSTALLING);
                
                uiRenderer.render(UIProtocol.showError(
                    "Uninstall failed: " + ex.getMessage()));
                
                showInstalledNodeDetails(selectedGroup);
                return null;
            });
    }
    
    private void uninstallAllVersions(NodeGroup group) {
        state.addState(UNINSTALLING);
        
        List<NodeMetadata> versions = group.getInstalledVersions();
        
        uiRenderer.render(UIProtocol.showMessage(
            "Uninstalling " + versions.size() + " version(s) of " + 
            group.getNodeInfo().getName() + "..."));
        
        CompletableFuture<?>[] futures = versions.stream()
            .map(v -> nodeRegistry.unregisterNode(v.getNodeId())
                .thenCompose(x -> appData.getNoteFileService().deleteNoteFilePath(
                    v.getNodePath(), false, null)))
            .toArray(CompletableFuture[]::new);
        
        CompletableFuture.allOf(futures)
            .thenRun(() -> {
                state.removeState(UNINSTALLING);
                
                // Refresh group manager
                refreshGroupManager();
                
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Successfully uninstalled all versions"));
                
                // Return to browse menu
                showBrowseMenu();
            })
            .exceptionally(ex -> {
                state.removeState(UNINSTALLING);
                
                uiRenderer.render(UIProtocol.showError(
                    "Uninstall failed: " + ex.getMessage()));
                
                showNodeDetails(group);
                return null;
            });
    }
    
    private void enableVersion(NodeMetadata metadata) {
        nodeRegistry.setNodeEnabled(metadata.getNodeId(), true)
            .thenRun(() -> {
                // Refresh group manager
                refreshGroupManager();
                
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Enabled " + metadata.getName()));
                
                // Refresh current view
                showInstalledNodeDetails(selectedGroup);
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Enable failed: " + ex.getMessage()));
                return null;
            });
    }
    
    private void disableVersion(NodeMetadata metadata) {
        nodeRegistry.setNodeEnabled(metadata.getNodeId(), false)
            .thenRun(() -> {
                // Refresh group manager
                refreshGroupManager();
                
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Disabled " + metadata.getName()));
                
                // Refresh current view
                showInstalledNodeDetails(selectedGroup);
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Disable failed: " + ex.getMessage()));
                return null;
            });
    }
    
    private void configureNode(NodeMetadata metadata) {
        state.addState(CONFIGURING);
        
        // TODO: Implement node configuration
        // This would show a configuration menu specific to the node
        uiRenderer.render(UIProtocol.showMessage(
            "Node configuration coming soon"));
        
        state.removeState(CONFIGURING);
    }
    
    private void refreshMarketplace() {
        uiRenderer.render(UIProtocol.showMessage(
            "Refreshing marketplace..."));
        
        marketplace.loadAvailableNodes()
            .thenAccept(availableNodes -> {
                groupManager.buildFromRegistry(
                    nodeRegistry.getAllNodes(),
                    availableNodes
                );
                
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Marketplace refreshed - " + 
                    availableNodes.size() + " nodes available"));
                
                showMainMenu();
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Refresh failed: " + ex.getMessage()));
                return null;
            });
    }
    
    private void refreshGroupManager() {
        marketplace.loadAvailableNodes()
            .thenAccept(availableNodes -> {
                groupManager.buildFromRegistry(
                    nodeRegistry.getAllNodes(),
                    availableNodes
                );
            })
            .exceptionally(ex -> {
                System.err.println("[NodeManager] Failed to refresh group manager: " + 
                    ex.getMessage());
                return null;
            });
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
            } else {
                System.err.println("[NodeManager] Unknown command: " + cmd);
                return CompletableFuture.completedFuture(null);
            }
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private CompletableFuture<Void> handleMenuSelection(
            NoteBytesMap msg, RoutedPacket packet) {
        // Delegate to current menu
        if (currentMenu != null) {
            // Menu handles its own navigation
        }
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
    
    private CompletableFuture<Void> handleInstallNode(
            NoteBytesMap msg, RoutedPacket packet) {
        // TODO: Handle install request from message
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleUninstallNode(
            NoteBytesMap msg, RoutedPacket packet) {
        // TODO: Handle uninstall request from message
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleEnableNode(
            NoteBytesMap msg, RoutedPacket packet) {
        // TODO: Handle enable request from message
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleDisableNode(
            NoteBytesMap msg, RoutedPacket packet) {
        // TODO: Handle disable request from message
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleConfigureNode(
            NoteBytesMap msg, RoutedPacket packet) {
        // TODO: Handle configure request from message
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleRefreshMarketplace(
            NoteBytesMap msg, RoutedPacket packet) {
        refreshMarketplace();
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException("NodeManager does not handle streams");
    }
    
    // ===== PARENT COMMUNICATION =====
    
    private void notifyParent(String event) {
        if (parentPath != null) {
            NoteBytesMap notify = new NoteBytesMap();
            notify.put(Keys.CMD, new NoteBytes(event));
            emitTo(parentPath, notify.getNoteBytesObject());
        }
    }
    
    // ===== GETTERS =====
    
    public BitFlagStateMachine getState() {
        return state;
    }
    
    public NodeRegistry getNodeRegistry() {
        return nodeRegistry;
    }
    
    public NodeGroupManager getGroupManager() {
        return groupManager;
    }
    
    public boolean isReady() {
        return state.hasState(READY);
    }
}