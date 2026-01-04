package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.ClientRenderManager.RenderState;
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
    
    private MenuNavigator menuNavigator;
    
    public MainMenuScreen(String name, SystemTerminalContainer terminal) {
        super(name, terminal);
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    /**
     * MenuNavigator is the active renderable
     * We return empty state
     */
    @Override
    public RenderState getRenderState() {
        return RenderState.builder().build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        // Create menu navigator if needed
        if (menuNavigator == null) {
            menuNavigator = new MenuNavigator(terminal);
        }
        
        // Build and show main menu
        MenuContext menu = buildMainMenu();
        menuNavigator.showMenu(menu);
        
        // MenuNavigator becomes the active renderable
        // (It calls terminal.getRenderManager().setActive(this) internally)
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void onHide() {
        if (menuNavigator != null) {
            menuNavigator.cleanup();
            menuNavigator = null;
        }
    }
    
    // ===== MENU BUILDING =====
    
    private MenuContext buildMainMenu() {
        RuntimeAccess access = terminal.getSystemAccess();
        ContextPath menuPath = terminal.getSessionPath().append("menu", "main");
        
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
                    terminal.showScreen("node-manager");
                } else {
                    // Show error (would need error screen or dialog)
                    terminal.printError("Node manager requires system access");
                }
            });
        
        menu.addItem("files", 
            "File Browser", 
            "Browse encrypted files", 
            () -> {
                // TODO: Implement file browser
                terminal.println("File browser not yet implemented");
            });
        
        menu.addItem("settings", 
            "Settings", 
            "System configuration", 
            () -> {
                terminal.showScreen("settings");
            });
        
        menu.addItem("lock", 
            "Lock System", 
            "Lock terminal", 
            () -> {
                terminal.lock();
            });
        
        menu.addSeparator("System");
        
        menu.addItem("close", 
            "Close Terminal", 
            "Hide terminal window", 
            () -> {
                terminal.close();
            });
        
        return menu;
    }
}