package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
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
    private PasswordPrompt passwordPrompt;

    public NodeManagerScreen(String name, SystemTerminalContainer terminal) {
        super(name, terminal);
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
        passwordPrompt = new PasswordPrompt(terminal)
            .withTitle("Node Manager Authentication")
            .withPrompt("Enter password:")
            .withTimeout(30)
            .onPassword(this::handlePassword)
            .onTimeout(this::handleTimeout)
            .onCancel(() -> terminal.goBack());
        
        return passwordPrompt.show();
    }



    private void handlePassword(io.netnotes.engine.noteBytes.NoteBytesEphemeral password) {
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
                    .thenCompose(v -> terminal.printAt(11, 10, "Press any key to retry..."))
                    .thenRun(() -> terminal.waitForKeyPress(() -> promptForPassword()));
                return null;
            });
    }

    private void handleTimeout() {
        terminal.clear()
            .thenCompose(v -> terminal.printError("Authentication timeout"))
            .thenCompose(v -> terminal.printAt(11, 10, "Press any key..."))
            .thenRun(() -> terminal.waitForKeyPress(() -> terminal.goBack()));
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
       
        currentView = View.INSTALLED_PACKAGES;
        
        if (installedScreen == null && nodeCommands != null) {
            installedScreen = new InstalledPackagesScreen(
                "installed-packages",
                terminal,
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

        currentView = View.RUNNING_INSTANCES;
        
        if (instancesScreen == null) {
            instancesScreen = new RunningInstancesScreen(
                "running-instances",
                terminal,
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
        currentView = View.BROWSE_PACKAGES;
        
        if (browseScreen == null) {
            browseScreen = new BrowsePackagesScreen(
                "browse-packages",
                terminal,
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

    private NoteBytesReadOnly handlerId = null;

    private void removeKeyDownHandler(){
        if(handlerId != null){
            terminal.removeKeyDownHandler(handlerId);
            handlerId = null;
        }
    }

    //TODO: use MenuNavigator
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
                removeKeyDownHandler();
                MenuContext.MenuItem selected = items.get(selectedIndex[0]);
                menu.navigate(selected.name);
            }),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, () -> {
                removeKeyDownHandler();
                goBack();
            })
        );
        
        handlerId = terminal.addKeyDownHandler(event -> {
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
        if (passwordPrompt != null && passwordPrompt.isActive()) {
            passwordPrompt.cancel();
            passwordPrompt = null;
        }
        
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