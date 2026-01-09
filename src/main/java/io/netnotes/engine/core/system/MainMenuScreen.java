package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.TerminalCommands;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderState;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderable;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.io.ContextPath;

/**
 * MainMenuScreen - REFACTORED for pull-based rendering
 * 
 * Main application menu using MenuNavigator.
 * MenuNavigator is already pull-based, so this screen
 * acts as a coordinator and returns empty RenderState.
 */
class MainMenuScreen extends TerminalScreen {
    
    private final MenuNavigator menuNavigator;
    
    public MainMenuScreen(String name, SystemApplication systemApplication) {
        super(name, systemApplication);
        menuNavigator = new MenuNavigator(systemApplication).withParent(this);
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    /**
     * MenuNavigator is the active renderable
     * We return empty state
     */
    @Override
    public TerminalRenderState getRenderState() {
        return TerminalRenderState.builder()
            .add(batch -> batch.clear())
            // Future: Add header/status bar here
            .add(menuNavigator.asRenderElement())
            // Future: Add footer/system info here
            .build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        super.onShow();
        
        // Build and show main menu
        MenuContext menu = buildMainMenu();
        menuNavigator.showMenu(menu);
        

        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void onHide() {

        menuNavigator.cleanup();
        
    }
    
    // ===== MENU BUILDING =====
    
    private MenuContext buildMainMenu() {
        RuntimeAccess access = systemApplication.getSystemAccess();
        ContextPath menuPath = systemApplication.getContextPath().append("menu", "main");
        
        MenuContext menu = new MenuContext(
            menuPath, 
            "Main Menu",
            "Welcome to Netnotes",
            null
        );
        
        // Add menu items
        menu.addItem("nodes", 
            "Node Manager", 
            access != null ? "Manage installed nodes" : "Not available",
            () -> {
                if (access != null) {
                    systemApplication.showScreen("node-manager");
                } else {
                    showError("Node manager requires system access");
                }
            });
        
        menu.addItem("files", 
            "File Browser", 
            "Browse encrypted files", 
            () -> {
                // TODO: Implement file browser
                showError("File browser not yet implemented");
            });
        
        menu.addItem("settings", 
            "Settings", 
            "System configuration", 
            () -> {
                systemApplication.showScreen("settings");
            });
        
        menu.addItem("lock", 
            "Lock System", 
            "Lock terminal", 
            () -> {
                systemApplication.lock();
            });
        
        menu.addSeparator("System");
        
        menu.addItem("close", 
            "Close Terminal", 
            "Hide terminal window", 
            () -> {
                systemApplication.close();
            });
        
        return menu;
    }

    private void showError(String message) {
       TerminalRenderable errorRenderable = () -> {
            return TerminalRenderState.builder()
                .add(batch -> {
                    int row = systemApplication.getHeight() / 2;
                    batch.printAt(row, 10, message, TextStyle.ERROR);
                    batch.printAt(row + 2, 10, TerminalCommands.PRESS_ANY_KEY, TextStyle.NORMAL);
                    batch.showCursor();
                    batch.moveCursor(row + 2, 29);
                })
                .build();
        };
        
        systemApplication.setRenderable(errorRenderable);
        systemApplication.invalidate();
        
        systemApplication.waitForKeyPress()
            .thenRun(() -> {
                systemApplication.setRenderable(this);
                invalidate();
            });
    }
}