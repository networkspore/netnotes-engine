package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.RenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.io.ContextPath;

/**
 * SettingsScreen - System settings
 * Uses MenuNavigator for keyboard navigation
 */
class SettingsScreen extends TerminalScreen {
    
    private MenuNavigator menuNavigator;
    
    public SettingsScreen(String name, SystemTerminalContainer terminal) {
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
        menuNavigator = new MenuNavigator(terminal);
        
        MenuContext menu = buildSettingsMenu();
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
        
        menu.addItem("system-setup", "System Setup",
            "Configure IODaemon and keyboard devices",
            () -> terminal.showScreen("system-setup"));
        
       /* menu.addItem("appearance", "Appearance",
            "Terminal colors and themes",
            () -> {
                terminal.println("Appearance settings not yet implemented");
            });
        
        menu.addSeparator("Advanced");
        
        menu.addItem("export-backup", "Export Backup",
            "Export encrypted backup",
            () -> {
                terminal.println("Backup export not yet implemented");
            });
        
        menu.addItem("import-backup", "Import Backup",
            "Restore from backup",
            () -> {
                terminal.println("Backup import not yet implemented");
            });*/
        
        menu.addSeparator("Navigation");
        
        menu.addItem("back", "← Back to Main Menu", () -> {
            terminal.showScreen("main-menu");
        });
        
        return menu;
    }
}