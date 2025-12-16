package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;


import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.PasswordReader;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;

/**
 * NodeManagerScreen - Main hub for node management
 * 
 * REFACTORED: Menu-driven, delegates to specialized screens
 * - InstalledPackagesScreen - View/manage installed packages
 * - RunningInstancesScreen - View/manage running nodes
 * - BrowsePackagesScreen - Browse and install new packages
 * - Each screen handles its own lifecycle
 * 
 * No "user" concept - just password confirmation for security operations
 */
class NodeManagerScreen extends TerminalScreen {
    
    private enum View {
        MAIN_MENU,
        INSTALLED_PACKAGES,
        RUNNING_INSTANCES,
        BROWSE_PACKAGES
    }
    
    private final ContextPath basePath;
    private View currentView = View.MAIN_MENU;
    
    // Sub-screens (created on demand)
    private InstalledPackagesScreen installedScreen;
    private RunningInstancesScreen instancesScreen;
    private BrowsePackagesScreen browseScreen;
    private NodeCommands nodeCommands = null;
    private MenuContext currentMenu;
    
    public NodeManagerScreen(String name, SystemTerminalContainer terminal, InputDevice keyboard) {
        super(name, terminal, keyboard);
        this.basePath = ContextPath.of("node-manager");
        
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        if (nodeCommands == null) {
            return promptForPassword();
        }
        currentView = View.MAIN_MENU;
        return render();
    }

    private CompletableFuture<Void> promptForPassword() {
    return terminal.clear()
        .thenCompose(v -> terminal.printTitle("Node Manager Authentication"))
        .thenCompose(v -> terminal.printAt(7, 10, "Enter password:"))
        .thenCompose(v -> terminal.moveCursor(7, 26))
        .thenRun(this::startPasswordAuth);
    }

    private void startPasswordAuth() {
        PasswordReader passwordReader = new PasswordReader();
        keyboard.setEventConsumer(passwordReader.getEventConsumer());
        
        passwordReader.setOnPassword(password -> {
            keyboard.setEventConsumer(null);
            
            terminal.printAt(9, 10, "Authenticating...")
                .thenCompose(v -> terminal.getSystemAccess().getAsymmetricPairs(password))
                .thenAccept(pairs -> {
                    password.close();
                    this.nodeCommands = new NodeCommands(terminal, pairs);
                    currentView = View.MAIN_MENU;
                    render();
                })
                .exceptionally(ex -> {
                    password.close();
                    terminal.clear()
                        .thenCompose(v -> terminal.printError("Authentication failed: " + ex.getMessage()))
                        .thenCompose(v -> terminal.printAt(11, 10, "Press any key to go back..."))
                        .thenRun(() -> waitForKeyPress(keyboard, () -> goBack()));
                    return null;
                });
            
            passwordReader.close();
        });
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    @Override
    public CompletableFuture<Void> render() {
        switch (currentView) {
            case MAIN_MENU:
                return renderMainMenu();
            case INSTALLED_PACKAGES:
                return renderInstalledPackages();
            case RUNNING_INSTANCES:
                return renderRunningInstances();
            case BROWSE_PACKAGES:
                return renderBrowsePackages();
            default:
                return CompletableFuture.completedFuture(null);
        }
    }
    
    // ===== MAIN MENU =====
    
    private CompletableFuture<Void> renderMainMenu() {
        currentMenu = new MenuContext(basePath, "Node Manager")
            .addItem("installed", "Installed Packages", "View and manage installed packages", 
                this::showInstalledPackages)
            .addItem("running", "Running Instances", "View and manage running nodes", 
                this::showRunningInstances)
            .addItem("browse", "Browse Available Packages", "Install new packages", 
                this::showBrowsePackages)
            .addItem("back", "Back to Main Menu", this::goBack);
        
        return terminal.clear()
            .thenCompose(v -> terminal.printTitle("Node Manager"))
            .thenCompose(v -> renderMenu(currentMenu, 5));
    }
    
    private void showInstalledPackages() {
        keyboard.setEventConsumer(null);
        currentView = View.INSTALLED_PACKAGES;
        
        if (installedScreen == null && nodeCommands != null) {
            installedScreen = new InstalledPackagesScreen(
                "installed-packages",
                terminal,
                keyboard,
                nodeCommands
            );
            installedScreen.setOnBack(() -> {
                installedScreen = null;
                currentView = View.MAIN_MENU;
                render();
            });
        }
        
        installedScreen.onShow();
    }
    
    private void showRunningInstances() {
        keyboard.setEventConsumer(null);
        currentView = View.RUNNING_INSTANCES;
        
        if (instancesScreen == null) {
            instancesScreen = new RunningInstancesScreen(
                "running-instances",
                terminal,
                keyboard,
                nodeCommands
            );
            instancesScreen.setOnBack(() -> {
                instancesScreen = null;
                currentView = View.MAIN_MENU;
                render();
            });
        }
        
        instancesScreen.onShow();
    }
    
    private void showBrowsePackages() {
        keyboard.setEventConsumer(null);
        currentView = View.BROWSE_PACKAGES;
        
        if (browseScreen == null) {
            browseScreen = new BrowsePackagesScreen(
                "browse-packages",
                terminal,
                keyboard,
                nodeCommands
            );
            browseScreen.setOnBack(() -> {
                browseScreen = null;
                currentView = View.MAIN_MENU;
                render();
            });
        }
        
        browseScreen.onShow();
    }
    
    private void goBack() {
        keyboard.setEventConsumer(null);
        terminal.goBack();
    }
    
    // ===== SUB-SCREEN RENDERING =====
    
    private CompletableFuture<Void> renderInstalledPackages() {
        if (installedScreen != null) {
            return installedScreen.render();
        }
        currentView = View.MAIN_MENU;
        return render();
    }
    
    private CompletableFuture<Void> renderRunningInstances() {
        if (instancesScreen != null) {
            return instancesScreen.render();
        }
        currentView = View.MAIN_MENU;
        return render();
    }
    
    private CompletableFuture<Void> renderBrowsePackages() {
        if (browseScreen != null) {
            return browseScreen.render();
        }
        currentView = View.MAIN_MENU;
        return render();
    }
    
    // ===== MENU RENDERING =====
    
    private CompletableFuture<Void> renderMenu(MenuContext menu, int startRow) {
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        
        int row = startRow;
        int index = 1;
        
        for (MenuContext.MenuItem item : menu.getItems()) {
            if (item.type == MenuContext.MenuItemType.SEPARATOR) {
                final int currentRow = row;
                future = future.thenCompose(v -> terminal.printAt(currentRow, 10, 
                    "─".repeat(40)));
                row++;
                continue;
            }
            
            final int currentRow = row;
            final String itemText = String.format("%d. %s", index, item.description);
            
            future = future.thenCompose(v -> terminal.printAt(currentRow, 10, itemText));
            
            row++;
            index++;
        }
        
        final int selectRow = row + 1;
        return future
            .thenCompose(v -> terminal.printAt(selectRow, 10, "Use arrow keys and Enter to select"))
            .thenRun(() -> startMenuNavigation(menu));
    }
    
    private void startMenuNavigation(MenuContext menu) {
        java.util.List<MenuContext.MenuItem> items = new java.util.ArrayList<>();
        for (MenuContext.MenuItem item : menu.getItems()) {
            if (item.type != MenuContext.MenuItemType.SEPARATOR) {
                items.add(item);
            }
        }
        
        final int[] selectedIndex = {0};
        
        KeyRunTable navigationKeys = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.UP, () -> {
                selectedIndex[0] = (selectedIndex[0] - 1 + items.size()) % items.size();
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.DOWN, () -> {
                selectedIndex[0] = (selectedIndex[0] + 1) % items.size();
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.ENTER, () -> {
                keyboard.setEventConsumer(null);
                MenuContext.MenuItem selected = items.get(selectedIndex[0]);
                menu.navigate(selected.name);
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, () -> {
                keyboard.setEventConsumer(null);
                goBack();
            })
        );
        
        keyboard.setEventConsumer(event -> {
            if (event instanceof EphemeralRoutedEvent ephemeral) {
                try (ephemeral) {
                    if (ephemeral instanceof EphemeralKeyDownEvent ekd) {
                        navigationKeys.run(ekd.getKeyCodeBytes());
                    }
                }
            } else if (event instanceof KeyDownEvent keyDown) {
                navigationKeys.run(keyDown.getKeyCodeBytes());
            }
        });
    }
    
    // ===== CLEANUP =====
    
    private void cleanup() {
        keyboard.setEventConsumer(null);
        
        if (installedScreen != null) {
            installedScreen.onHide();
            installedScreen = null;
        }
        
        if (instancesScreen != null) {
            instancesScreen.onHide();
            instancesScreen = null;
        }
        
        if (browseScreen != null) {
            browseScreen.onHide();
            browseScreen = null;
        }
    }
}