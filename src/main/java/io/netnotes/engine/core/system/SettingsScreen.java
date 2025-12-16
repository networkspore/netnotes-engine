package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.core.system.control.MenuNavigator;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.InputDevice;


/**
 * SettingsScreen - System settings
 * Uses MenuNavigatorProcess for keyboard navigation
 */
class SettingsScreen extends TerminalScreen {
    
    private MenuNavigator menuNavigator;
    
    public SettingsScreen(String name, SystemTerminalContainer terminal, InputDevice keyboard) {
        super(name, terminal, keyboard);
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        // Create menu navigator
        menuNavigator = new MenuNavigator(
            terminal,
            keyboard
        );
        
        // Build settings menu
        MenuContext menu = buildSettingsMenu();
        
        // Register and start navigator
   
        menuNavigator.showMenu(menu);
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void onHide() {
        if (menuNavigator != null) {
            menuNavigator.cleanup();
            menuNavigator = null;
        }
    }
    
    private MenuContext buildSettingsMenu() {
        ContextPath menuPath = terminal.getSessionPath().append("menu", "settings");
        MenuContext menu = new MenuContext(menuPath, "Settings");
        
        menu.addItem("change-password", "Change Master Password", 
            "⚠️ Re-encrypts all files",
            () -> {
                terminal.showScreen("change-password");
            });
        
        menu.addItem("bootstrap", "Bootstrap Configuration",
            "View and manage system bootstrap settings",
            () -> terminal.showScreen("bootstrap"));
        
        menu.addSeparator("Navigation");
        
        menu.addItem("back", "← Back to Main Menu", () -> {
            terminal.showScreen("main-menu");
        });
        
        return menu;
    }
    
    @Override
    public CompletableFuture<Void> render() {
        // MenuNavigator handles all rendering
        return CompletableFuture.completedFuture(null);
    }
}

