package io.netnotes.engine.core.system.control;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import io.netnotes.engine.core.system.control.ui.*;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * MenuContext - Menu navigation with UI protocol
 */
public class MenuContext {
    
    private final ContextPath currentPath;
    private final MenuContext parent;
    private final Map<String, MenuItem> items = new LinkedHashMap<>();
    private final String title;
    private final UIRenderer uiRenderer;
    
    private PasswordGate passwordGate;
    private volatile boolean unlocked = false;
    
    public MenuContext(ContextPath path, String title, UIRenderer uiRenderer) {
        this(path, title, uiRenderer, null);
    }
    
    public MenuContext(ContextPath path, String title, UIRenderer uiRenderer, MenuContext parent) {
        this.currentPath = path;
        this.title = title;
        this.uiRenderer = uiRenderer;
        this.parent = parent;
    }
    
    /**
     * Display this menu
     */
    public CompletableFuture<NoteBytesMap> display() {
        // Convert items to protocol format
        List<UIProtocol.MenuItem> uiItems = items.values().stream()
            .map(item -> new UIProtocol.MenuItem(
                item.name,
                item.description,
                convertType(item.type),
                item.type == MenuItemType.PROTECTED_SUBMENU
            ))
            .toList();
        
        // Build command
        NoteBytesMap menuCmd = UIProtocol.showMenu(
            title,
            currentPath.toString(),
            uiItems,
            parent != null
        );
        
        // Render and wait for user selection
        return uiRenderer.render(menuCmd);
    }
    
    /**
     * Add action item
     */
    public MenuContext addItem(String name, String description, Runnable action) {
        items.put(name, new MenuItem(name, description, MenuItemType.ACTION, action));
        return this;
    }
    
    /**
     * Add sub-menu
     */
    public MenuContext addSubMenu(
            String name,
            String description,
            Function<MenuContext, MenuContext> builder) {
        
        ContextPath subPath = currentPath.append(name);
        MenuContext subMenu = new MenuContext(subPath, description, uiRenderer, this);
        builder.apply(subMenu);
        
        items.put(name, new MenuItem(name, description, MenuItemType.SUBMENU, subMenu));
        return this;
    }
    
    /**
     * Add password-protected sub-menu
     */
    public MenuContext addProtectedSubMenu(
            String name,
            String description,
            PasswordGate gate,
            Function<MenuContext, MenuContext> builder) {
        
        ContextPath subPath = currentPath.append(name);
        MenuContext subMenu = new MenuContext(subPath, description, uiRenderer, this);
        subMenu.setPasswordGate(gate);
        builder.apply(subMenu);
        
        items.put(name, new MenuItem(name, description, MenuItemType.PROTECTED_SUBMENU, subMenu));
        return this;
    }
    
    public void setPasswordGate(PasswordGate gate) {
        this.passwordGate = gate;
    }
    
    /**
     * Navigate to item
     */
    public CompletableFuture<MenuContext> navigate(String itemName) {
        MenuItem item = items.get(itemName);
        if (item == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown item: " + itemName));
        }
        
        return switch (item.type) {
            case ACTION -> {
                ((Runnable) item.target).run();
                yield CompletableFuture.completedFuture(this); // Stay on same menu
            }
            case SUBMENU -> CompletableFuture.completedFuture((MenuContext) item.target);
            case PROTECTED_SUBMENU -> {
                MenuContext menu = (MenuContext) item.target;
                if (menu.needsUnlock()) {
                    // Return null to signal password needed
                    yield CompletableFuture.completedFuture(null);
                } else {
                    yield CompletableFuture.completedFuture(menu);
                }
            }
        };
    }
    
    /**
     * Back to parent
     */
    public MenuContext back() {
        return parent;
    }
    
    public boolean needsUnlock() {
        return passwordGate != null && !unlocked;
    }
    
    public CompletableFuture<Boolean> unlock(NoteBytesEphemeral password) {
        if (passwordGate == null) {
            return CompletableFuture.completedFuture(true);
        }
        
        return passwordGate.verify(password)
            .thenApply(success -> {
                if (success) unlocked = true;
                return success;
            });
    }
    
    // Helpers
    private UIProtocol.MenuItemType convertType(MenuItemType type) {
        return switch (type) {
            case ACTION -> UIProtocol.MenuItemType.ACTION;
            case SUBMENU -> UIProtocol.MenuItemType.SUBMENU;
            case PROTECTED_SUBMENU -> UIProtocol.MenuItemType.PROTECTED_SUBMENU;
        };
    }
    
    // Inner classes
    private static class MenuItem {
        final String name;
        final String description;
        final MenuItemType type;
        final Object target;
        
        MenuItem(String name, String description, MenuItemType type, Object target) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.target = target;
        }
    }
    
    private enum MenuItemType {
        ACTION,
        SUBMENU,
        PROTECTED_SUBMENU
    }
}