package io.netnotes.engine.core.system;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.TerminalRenderState;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.io.ContextPath;

/**
 * SettingsScreen - System settings
 * Uses MenuNavigator for keyboard navigation
 */
class SettingsScreen extends TerminalScreen {
    
    private MenuNavigator menuNavigator;
    
    public SettingsScreen(String name, SystemApplication terminal) {
        super(name, terminal);
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    /**
     * MenuNavigator is the active renderable
     * We return empty state
     */
    @Override
    public TerminalRenderState getRenderState() {
        return TerminalRenderState.builder().build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        menuNavigator = new MenuNavigator(systemApplication.getTerminal());
        
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
        ContextPath menuPath = systemApplication.getTerminal().getContextPath().append("menu", "settings");
        MenuContext menu = new MenuContext(menuPath, "Settings");
        
        menu.addItem("change-password", "Change Master Password", 
            "⚠️ Re-encrypts all files",
            () -> {
                systemApplication.showScreen("change-password");
            });
        
        menu.addItem("system-setup", "System Setup",
            "Configure IODaemon and keyboard devices",
            () -> systemApplication.showScreen("system-setup"));
        
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
            systemApplication.showScreen("main-menu");
        });
        
        return menu;
    }
}