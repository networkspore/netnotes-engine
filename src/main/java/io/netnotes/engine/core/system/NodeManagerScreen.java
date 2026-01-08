package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.TerminalRenderState;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderable;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * NodeManagerScreen - REFACTORED for composable pull-based rendering
 * 
 * KEY CHANGES:
 * - Screen owns MenuNavigator as a component (not standalone renderable)
 * - getRenderState() delegates to MenuNavigator's asRenderElement()
 * - MenuNavigator invalidates parent screen, not terminal directly
 * - Enables composition: menu + progress bar + status text, etc.
 */
class NodeManagerScreen extends TerminalScreen {
    
    private enum State {
        AUTHENTICATING,     // PasswordPrompt active
        SHOWING_MENU,       // MenuNavigator component
        SHOWING_SUBSCREEN   // Sub-screen active (different renderable)
    }
    
    private volatile State currentState = State.AUTHENTICATING;
    
    private final ContextPath basePath;
    private NodeCommands nodeCommands = null;
    private PasswordPrompt passwordPrompt;
    
    // MenuNavigator as COMPONENT (not standalone renderable)
    private MenuNavigator menuNavigator;
    
    // Sub-screens (created on demand, these ARE standalone renderables)
    private InstalledPackagesScreen installedScreen;
    private RunningInstancesScreen instancesScreen;
    private BrowsePackagesScreen browseScreen;

    public NodeManagerScreen(String name, SystemApplication systemApplication) {
        super(name, systemApplication);
        this.basePath = ContextPath.of("node-manager");
        

        this.menuNavigator = new MenuNavigator(systemApplication.getTerminal()).withParent(this);
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    @Override
    public TerminalRenderState getRenderState() {
        return switch (currentState) {
            case AUTHENTICATING -> buildAuthenticatingState();
            case SHOWING_MENU -> buildMenuState();
            case SHOWING_SUBSCREEN -> buildSubScreenState();
        };
    }
    
    /**
     * PasswordPrompt is active - show status message
     */
    private TerminalRenderState buildAuthenticatingState() {
        int centerRow = systemApplication.getTerminal().getRows() / 2;
        int centerCol = systemApplication.getTerminal().getCols() / 2;
        
        return TerminalRenderState.builder()
            .add(batch -> {
                batch.clear();
                batch.printAt(centerRow, centerCol - 15, 
                    "Authenticating with Node Manager...", 
                    TextStyle.INFO);
            })
            .build();
    }
    
    /**
     * MenuNavigator is active - compose its render element
     */
    private TerminalRenderState buildMenuState() {
        // THIS IS THE KEY: MenuNavigator is now a COMPONENT
        // We compose it into our RenderState
        return TerminalRenderState.builder()
            .add(batch -> batch.clear()) // Clear screen first
            .add(menuNavigator.asRenderElement()) // Add menu rendering
            .build();
    }
    
    /**
     * Sub-screen is active - it handles its own rendering
     * Return empty state since sub-screen is the active renderable
     */
    private TerminalRenderState buildSubScreenState() {
        // Sub-screens are standalone renderables
        // They manage their own rendering lifecycle
        return TerminalRenderState.builder().build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        // Make THIS screen the active renderable
        super.onShow();
        
        if (nodeCommands == null) {
            // Need authentication
            currentState = State.AUTHENTICATING;
            invalidate();
            return promptForPassword();
        } else {
            // Already authenticated, show menu
            currentState = State.SHOWING_MENU;
            invalidate();
            return showMainMenu();
        }
    }
    
    @Override
    public void onHide() {
        cleanup();
    }
    
    // ===== AUTHENTICATION =====
    
    private CompletableFuture<Void> promptForPassword() {
        passwordPrompt = new PasswordPrompt(systemApplication)
            .withTitle("Node Manager Authentication")
            .withPrompt("Enter password:")
            .withTimeout(30)
            .onPassword(this::handlePassword)
            .onTimeout(this::handleTimeout)
            .onCancel(() -> systemApplication.goBack());
        
        return passwordPrompt.show();
    }
    
    private void handlePassword(NoteBytesEphemeral password) {
        // Show processing state
        currentState = State.AUTHENTICATING;
        invalidate();
        
        systemApplication.getSystemAccess().getAsymmetricPairs(password)
            .thenAccept(pairs -> {
                password.close();
                
                this.nodeCommands = new NodeCommands(systemApplication, pairs);
                currentState = State.SHOWING_MENU;
                
                showMainMenu();
            })
            .exceptionally(ex -> {
                password.close();
                showAuthError(ex.getMessage());
                return null;
            });
    }

    private void showAuthError(String errorMsg) {
        // Create temporary error renderable
        TerminalRenderable errorRenderable = () -> {
            int row = systemApplication.getTerminal().getRows() / 2;
            return TerminalRenderState.builder()
                .add(batch -> {
                    batch.clear();
                    batch.printAt(row, 10, 
                        "Authentication failed: " + errorMsg, 
                        TextStyle.ERROR);
                    batch.printAt(row + 2, 10, 
                        "Press any key to retry...", 
                        TextStyle.NORMAL);
                    batch.showCursor();
                    batch.moveCursor(row + 2, 36);

                })
                .build();
        };
        
        // Temporarily show error (don't change our state)
        systemApplication.setRenderable(errorRenderable);
        systemApplication.invalidate();
        
        systemApplication.getTerminal().waitForKeyPress()
            .thenRun(() -> {
                // Restore this screen as active
                systemApplication.getTerminal().setRenderable(this);
                promptForPassword();
            });
    }

   private void handleTimeout() {
        TerminalRenderable timeoutRenderable = () -> {
            return TerminalRenderState.builder()
                .add(batch -> {
                    batch.clear();
                    batch.printAt(systemApplication.getTerminal().getRows() / 2, 10, 
                        "Authentication timeout", 
                        TextStyle.ERROR);
                    batch.printAt(systemApplication.getTerminal().getRows() / 2 + 2, 10, 
                        "Press any key...", 
                        TextStyle.NORMAL);
                })
                .build();
        };
        
        systemApplication.getTerminal().setRenderable(timeoutRenderable);
        systemApplication.getTerminal().invalidate();
        
        systemApplication.getTerminal().waitForKeyPress()
            .thenRun(() -> systemApplication.goBack());
    }
    
    // ===== MAIN MENU =====
    
    private CompletableFuture<Void> showMainMenu() {
        currentState = State.SHOWING_MENU;
        invalidate(); // Trigger re-render with menu
        
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
        
        // Show menu in our MenuNavigator component
        menuNavigator.showMenu(menu);
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== SUB-SCREENS =====
    
    private void showInstalledPackages() {
        currentState = State.SHOWING_SUBSCREEN;
        
        if (installedScreen == null && nodeCommands != null) {
            installedScreen = new InstalledPackagesScreen(
                "installed-packages",
                systemApplication,
                nodeCommands
            );
            installedScreen.setOnBack(() -> {
                installedScreen = null;
                currentState = State.SHOWING_MENU;
                
                // PATCH: Restore this screen as active renderable
                systemApplication.setRenderable(this);
                showMainMenu();
            });
        }
        
        if (installedScreen != null) {
            // Sub-screen becomes the active renderable
            installedScreen.onShow();
        }
    }
    
    private void showRunningInstances() {
        currentState = State.SHOWING_SUBSCREEN;
        
        if (instancesScreen == null && nodeCommands != null) {
            instancesScreen = new RunningInstancesScreen(
                "running-instances",
                systemApplication,
                nodeCommands
            );
            instancesScreen.setOnBack(() -> {
                instancesScreen = null;
                currentState = State.SHOWING_MENU;
                
                // PATCH: Restore this screen as active renderable
                systemApplication.setRenderable(this);
                showMainMenu();
            });
        }
        
        if (instancesScreen != null) {
            instancesScreen.onShow();
        }
    }
    
    private void showBrowsePackages() {
        currentState = State.SHOWING_SUBSCREEN;
        
        if (browseScreen == null && nodeCommands != null) {
            browseScreen = new BrowsePackagesScreen(
                "browse-packages",
                systemApplication,
                nodeCommands
            );
            browseScreen.setOnBack(() -> {
                browseScreen = null;
                currentState = State.SHOWING_MENU;
                
                // PATCH: Restore this screen as active renderable
                systemApplication.setRenderable(this);
                showMainMenu();
            });
        }
        
        if (browseScreen != null) {
            browseScreen.onShow();
        }
    }
        
    private void goBack() {
        systemApplication.goBack();
    }
    
    // ===== CLEANUP =====
    
    private void cleanup() {
        if (passwordPrompt != null && passwordPrompt.isActive()) {
            passwordPrompt.cancel();
            passwordPrompt = null;
        }
        
        if (menuNavigator != null) {
            menuNavigator.cleanup();
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