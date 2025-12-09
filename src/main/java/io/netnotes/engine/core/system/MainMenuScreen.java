package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.MenuNavigatorProcess;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.InputDevice;


/**
 * MainMenuScreen - Main application menu
 * Uses MenuNavigatorProcess for keyboard-driven navigation
 */
class MainMenuScreen extends TerminalScreen {
    
    private MenuNavigatorProcess menuNavigator;
    
    public MainMenuScreen(String name, SystemTerminalContainer terminal, InputDevice keyboard) {
        super(name, terminal, keyboard);
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        // Create menu navigator
        menuNavigator = new MenuNavigatorProcess(
            "main-menu-navigator",
            terminal,
            keyboard
        );
        
        // Build main menu
        MenuContext menu = buildMainMenu();
        
        // Register and start navigator
        return terminal.spawnPasswordProcess(menuNavigator)
            .thenRun(() -> {
                // Show the menu
                menuNavigator.showMenu(menu);
            });
    }
    
    @Override
    public void onHide() {
        if (menuNavigator != null) {
            terminal.getRegistry().unregisterProcess(menuNavigator.getContextPath());
            menuNavigator = null;
        }
    }
    
    private MenuContext buildMainMenu() {
        RuntimeAccess access = terminal.getSystemAccess();
        ContextPath menuPath = terminal.getSessionPath().append("menu", "main");
        
        MenuContext menu = new MenuContext(menuPath, "Main Menu");
        
        // Add menu items
        menu.addItem("nodes", "Node Manager", 
            access != null ? "Manage installed nodes" : "Not available",
            () -> {
                if (access != null) {
                    terminal.showScreen("node-manager");
                } else {
                    terminal.printError("Node manager requires system access");
                }
            });
        
        menu.addItem("files", "File Browser", "Browse encrypted files", () -> {
            terminal.println("File browser not yet implemented");
        });
        
        menu.addItem("settings", "Settings", "System configuration", () -> {
            terminal.showScreen("settings");
        });
        
        menu.addItem("lock", "Lock System", "Lock terminal", () -> {
            terminal.lock();
        });
        
        menu.addSeparator("System");
        
        menu.addItem("close", "Close Terminal", "Hide terminal window", () -> {
            terminal.close();
        });
        
        return menu;
    }
    
    @Override
    public CompletableFuture<Void> render() {
        // MenuNavigator handles all rendering
        return CompletableFuture.completedFuture(null);
    }
}