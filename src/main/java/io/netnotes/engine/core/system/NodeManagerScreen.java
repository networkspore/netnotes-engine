package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.ClientRenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.ClientRenderManager;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.io.ContextPath;

/**
 * NodeManagerScreen - REFACTORED for pull-based rendering
 * 
 * Main hub for node management.
 * Authenticates with password, then shows menu.
 */
class NodeManagerScreen extends TerminalScreen {
    
    private enum State {
        AUTHENTICATING,     // PasswordPrompt active
        SHOWING_MENU,       // MenuNavigator active
        SHOWING_SUBSCREEN   // Sub-screen active
    }
    
    private volatile State currentState = State.AUTHENTICATING;
    
    private final ContextPath basePath;
    private NodeCommands nodeCommands = null;
    private PasswordPrompt passwordPrompt;
    private MenuNavigator menuNavigator;
    
    // Sub-screens (created on demand)
    private InstalledPackagesScreen installedScreen;
    private RunningInstancesScreen instancesScreen;
    private BrowsePackagesScreen browseScreen;

    public NodeManagerScreen(String name, SystemTerminalContainer terminal) {
        super(name, terminal);
        this.basePath = ContextPath.of("node-manager");
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    @Override
    public RenderState getRenderState() {
        return switch (currentState) {
            case AUTHENTICATING -> buildAuthenticatingState();
            case SHOWING_MENU -> buildMenuState();
            case SHOWING_SUBSCREEN -> buildSubScreenState();
        };
    }
    
    /**
     * PasswordPrompt is active, return empty
     */
    private RenderState buildAuthenticatingState() {
        return RenderState.builder().build();
    }
    
    /**
     * MenuNavigator is active, return empty
     */
    private RenderState buildMenuState() {
        return RenderState.builder().build();
    }
    
    /**
     * Sub-screen is active, delegate to it
     */
    private RenderState buildSubScreenState() {
        // Sub-screens handle their own rendering
        // They should be Renderable and active in ClientRenderManager
        return RenderState.builder().build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        if (nodeCommands == null) {
            // Need authentication
            currentState = State.AUTHENTICATING;
            invalidate();
            return promptForPassword();
        } else {
            // Already authenticated, show menu
            currentState = State.SHOWING_MENU;
            return showMainMenu();
        }
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    // ===== AUTHENTICATION =====
    
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
        // Show processing state
        currentState = State.AUTHENTICATING;
        
        // Make this screen active to show status
        terminal.getRenderManager().setActive(this);
        
        terminal.getSystemAccess().getAsymmetricPairs(password)
            .thenAccept(pairs -> {
                password.close();
                
                this.nodeCommands = new NodeCommands(terminal, pairs);
                currentState = State.SHOWING_MENU;
                
                showMainMenu();
            })
            .exceptionally(ex -> {
                password.close();
                
                // Show error and offer retry
                RenderState errorState = RenderState.builder()
                    .add((term) -> {
                        term.printAt(terminal.getRows() / 2, 10, 
                            "Authentication failed: " + ex.getMessage(), 
                            TextStyle.ERROR);
                        term.printAt(terminal.getRows() / 2 + 2, 10, 
                            "Press any key to retry...", 
                            TextStyle.NORMAL);
                    })
                    .build();
                
                terminal.getRenderManager().setActive(new ClientRenderManager.Renderable() {
                    @Override
                    public RenderState getRenderState() {
                        return errorState;
                    }
                });
                
                terminal.waitForKeyPress()
                    .thenRun(() -> promptForPassword());
                
                return null;
            });
    }

    private void handleTimeout() {
        // Show timeout message
        RenderState timeoutState = RenderState.builder()
            .add((term) -> {
                term.printAt(terminal.getRows() / 2, 10, 
                    "Authentication timeout", 
                    TextStyle.ERROR);
                term.printAt(terminal.getRows() / 2 + 2, 10, 
                    "Press any key...", 
                    TextStyle.NORMAL);
            })
            .build();
        
        terminal.getRenderManager().setActive(new ClientRenderManager.Renderable() {
            @Override
            public RenderState getRenderState() {
                return timeoutState;
            }
        });
        
        terminal.waitForKeyPress()
            .thenRun(() -> terminal.goBack());
    }
    
    // ===== MAIN MENU =====
    
    private CompletableFuture<Void> showMainMenu() {
        currentState = State.SHOWING_MENU;
        invalidate();
        
        if (menuNavigator == null) {
            menuNavigator = new MenuNavigator(terminal);
        }
        
        MenuContext menu = new MenuContext(basePath, "Node Manager")
            .addItem("installed", 
                "Installed Packages", 
                "View and manage installed packages", 
                this::showInstalledPackages)
            .addItem("running", 
                "Running Instances", 
                "View and manage running nodes", 
                this::showRunningInstances)
            .addItem("browse", 
                "Browse Available Packages", 
                "Install new packages", 
                this::showBrowsePackages)
            .addItem("back", 
                "Back to Main Menu", 
                this::goBack);
        
        menuNavigator.showMenu(menu);
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== SUB-SCREENS =====
    
    private void showInstalledPackages() {
        currentState = State.SHOWING_SUBSCREEN;
        invalidate();
        
        if (installedScreen == null && nodeCommands != null) {
            installedScreen = new InstalledPackagesScreen(
                "installed-packages",
                terminal,
                nodeCommands
            );
            installedScreen.setOnBack(() -> {
                installedScreen = null;
                currentState = State.SHOWING_MENU;
                showMainMenu();
            });
        }
        
        if (installedScreen != null) {
            installedScreen.onShow();
        }
    }
    
    private void showRunningInstances() {
        currentState = State.SHOWING_SUBSCREEN;
        invalidate();
        
        if (instancesScreen == null && nodeCommands != null) {
            instancesScreen = new RunningInstancesScreen(
                "running-instances",
                terminal,
                nodeCommands
            );
            instancesScreen.setOnBack(() -> {
                instancesScreen = null;
                currentState = State.SHOWING_MENU;
                showMainMenu();
            });
        }
        
        if (instancesScreen != null) {
            instancesScreen.onShow();
        }
    }
    
    private void showBrowsePackages() {
        currentState = State.SHOWING_SUBSCREEN;
        invalidate();
        
        if (browseScreen == null && nodeCommands != null) {
            browseScreen = new BrowsePackagesScreen(
                "browse-packages",
                terminal,
                nodeCommands
            );
            browseScreen.setOnBack(() -> {
                browseScreen = null;
                currentState = State.SHOWING_MENU;
                showMainMenu();
            });
        }
        
        if (browseScreen != null) {
            browseScreen.onShow();
        }
    }
    
    private void goBack() {
        terminal.goBack();
    }
    
    // ===== CLEANUP =====
    
    private void cleanup() {
        if (passwordPrompt != null && passwordPrompt.isActive()) {
            passwordPrompt.cancel();
            passwordPrompt = null;
        }
        
        if (menuNavigator != null) {
            menuNavigator.cleanup();
            menuNavigator = null;
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