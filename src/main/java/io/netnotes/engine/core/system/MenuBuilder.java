package io.netnotes.engine.core.system;

import io.netnotes.engine.core.system.control.MenuContext;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * MenuBuilder - Static helpers for constructing common menus
 * 
 * Reduces code duplication and size of SystemSessionProcess
 */
public class MenuBuilder {
    
    /**
     * Build locked menu (shown when system is locked)
     */
    static MenuContext buildLockedMenu(
            ContextPath basePath,
            NoteBytesMap bootstrapConfig,
            Runnable onReconfigure,
            Runnable onInstallSecureInput) {
        
        ContextPath menuPath = basePath.append("menu", "locked");
        MenuContext menu = new MenuContext(menuPath, "System Locked");
        
        // Bootstrap configuration accessible before unlock
        menu.addSubMenu("bootstrap", "Bootstrap Configuration", subMenu -> {
            subMenu.addItem("view_config", "View Current Configuration", () -> {
                // Handled by caller
            });
            
            subMenu.addItem("install_secure", "Install Secure Input",
                "Requires administrator privileges",
                onInstallSecureInput);
            
            subMenu.addItem("reconfigure", "Run Bootstrap Wizard",
                "⚠️ Advanced: Reconfigure system",
                onReconfigure);
            
            return subMenu;
        });
        
        menu.addItem("about", "About", () -> {
            // Show about info
        });
        
        return menu;
    }
    
    /**
     * Build main menu (shown when system is unlocked)
     */
    static MenuContext buildMainMenu(
            ContextPath basePath,
            Runnable onNodes,
            Runnable onSettings,
            Runnable onLock) {
        
        ContextPath menuPath = basePath.append("menu", "main");
        MenuContext menu = new MenuContext(menuPath, "Main Menu");
        
        menu.addItem("nodes", "Node Manager", onNodes);
        
        menu.addItem("files", "File Browser", () -> {
            // TODO: Launch file browser
        });
        
        menu.addItem("settings", "Settings", onSettings);
        
        menu.addItem("lock", "Lock System", onLock);
        
        menu.addItem("about", "About System", () -> {
            // Show about
        });
        
        return menu;
    }
    
    /**
     * Build settings menu
     */
    static MenuContext buildSettingsMenu(
            ContextPath basePath,
            MenuContext parent,
            Runnable onChangePassword,
            Runnable onBootstrapConfig,
            Runnable onBack) {
        
        ContextPath menuPath = basePath.append("menu", "settings");
        MenuContext menu = new MenuContext(menuPath, "Settings", parent);
        
        menu.addItem("change_password", "Change Master Password",
            "⚠️ Re-encrypts all files",
            onChangePassword);
        
        menu.addItem("bootstrap", "Bootstrap Configuration", 
            onBootstrapConfig);
        
        menu.addItem("back", "Back to Main Menu", onBack);
        
        return menu;
    }
}