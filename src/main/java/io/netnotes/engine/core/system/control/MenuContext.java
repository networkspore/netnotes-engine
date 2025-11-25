package io.netnotes.engine.core.system.control;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import io.netnotes.engine.core.system.control.ui.*;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * MenuContext - Hierarchical menu navigation with UI protocol
 * 
 * Features:
 * - Action items (execute code)
 * - Sub-menus (navigate deeper)
 * - Protected sub-menus (password required)
 * - Information display items (show text, no action)
 * - Separators (visual grouping)
 * - Back navigation
 * - Breadcrumb tracking
 */
public class MenuContext {
    
    private final ContextPath currentPath;
    private final MenuContext parent;
    private final Map<String, MenuItem> items = new LinkedHashMap<>();
    private final String title;
    private final String description; // Optional subtitle/description
    private final UIRenderer uiRenderer;
    
    private PasswordGate passwordGate;
    private volatile boolean unlocked = false;
    
    // Callbacks
    private Consumer<String> onItemSelected;
    private Runnable onBack;
    
    public MenuContext(ContextPath path, String title, UIRenderer uiRenderer) {
        this(path, title, null, uiRenderer, null);
    }
    
    public MenuContext(ContextPath path, String title, UIRenderer uiRenderer, MenuContext parent) {
        this(path, title, null, uiRenderer, parent);
    }
    
    public MenuContext(ContextPath path, String title, String description, UIRenderer uiRenderer, MenuContext parent) {
        this.currentPath = path;
        this.title = title;
        this.description = description;
        this.uiRenderer = uiRenderer;
        this.parent = parent;
    }
    
    // ===== MENU BUILDING =====
    
    /**
     * Add action item
     */
    public MenuContext addItem(String name, String description, Runnable action) {
        items.put(name, new MenuItem(name, description, MenuItemType.ACTION, action));
        return this;
    }
    
    /**
     * Add action item with icon/badge
     */
    public MenuContext addItem(String name, String description, String badge, Runnable action) {
        MenuItem item = new MenuItem(name, description, MenuItemType.ACTION, action);
        item.badge = badge;
        items.put(name, item);
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
    
    /**
     * Add information item (displays text, no action)
     */
    public MenuContext addInfoItem(String name, String description) {
        items.put(name, new MenuItem(name, description, MenuItemType.INFO, null));
        return this;
    }
    
    /**
     * Add separator for visual grouping
     */
    public MenuContext addSeparator(String label) {
        String sepId = "separator-" + items.size();
        items.put(sepId, new MenuItem(sepId, label, MenuItemType.SEPARATOR, null));
        return this;
    }
    
    /**
     * Add back navigation item explicitly
     * (Note: Back is usually automatic if parent exists)
     */
    public MenuContext addBackItem(String description) {
        items.put("back", new MenuItem("back", description != null ? description : "Back", 
            MenuItemType.BACK, null));
        return this;
    }
    
    // ===== DISPLAY =====
    
    /**
     * Display this menu via UI protocol
     */
    public CompletableFuture<NoteBytesMap> display() {
        // Convert items to protocol format
        List<UIProtocol.MenuItem> uiItems = new ArrayList<>();
        
        for (MenuItem item : items.values()) {
            UIProtocol.MenuItem uiItem = new UIProtocol.MenuItem(
                item.name,
                item.description,
                convertType(item.type),
                item.type == MenuItemType.PROTECTED_SUBMENU
            );
            uiItems.add(uiItem);
        }
        
        // Build command
        NoteBytesMap menuCmd = UIProtocol.showMenu(
            title,
            currentPath.toString(),
            uiItems,
            parent != null
        );
        
        // Add description if present
        if (description != null && !description.isEmpty()) {
            menuCmd.put("description", new io.netnotes.engine.noteBytes.NoteBytes(description));
        }
        
        // Add breadcrumb trail
        menuCmd.put("breadcrumb", new io.netnotes.engine.noteBytes.NoteBytes(getBreadcrumb()));
        
        // Render and wait for user selection
        return uiRenderer.render(menuCmd);
    }
    
    /**
     * Get breadcrumb trail (path from root to current)
     */
    private String getBreadcrumb() {
        List<String> trail = new ArrayList<>();
        MenuContext current = this;
        
        while (current != null) {
            trail.add(0, current.title);
            current = current.parent;
        }
        
        return String.join(" > ", trail);
    }
    
    // ===== NAVIGATION =====
    
    /**
     * Navigate to item by name
     */
    public CompletableFuture<MenuContext> navigate(String itemName) {
        MenuItem item = items.get(itemName);
        if (item == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown item: " + itemName));
        }
        
        // Notify callback
        if (onItemSelected != null) {
            onItemSelected.accept(itemName);
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
            case BACK -> {
                if (onBack != null) {
                    onBack.run();
                }
                yield CompletableFuture.completedFuture(parent);
            }
            case INFO, SEPARATOR -> CompletableFuture.completedFuture(this); // Stay
        };
    }
    
    /**
     * Back to parent
     */
    public MenuContext back() {
        if (onBack != null) {
            onBack.run();
        }
        return parent;
    }
    
    /**
     * Find item by name
     */
    public MenuItem getItem(String name) {
        return items.get(name);
    }
    
    /**
     * Get all items
     */
    public Collection<MenuItem> getItems() {
        return Collections.unmodifiableCollection(items.values());
    }
    
    // ===== PASSWORD PROTECTION =====
    
    public void setPasswordGate(PasswordGate gate) {
        this.passwordGate = gate;
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
    
    // ===== CALLBACKS =====
    
    /**
     * Set callback for when item is selected
     */
    public void setOnItemSelected(Consumer<String> callback) {
        this.onItemSelected = callback;
    }
    
    /**
     * Set callback for when back is pressed
     */
    public void setOnBack(Runnable callback) {
        this.onBack = callback;
    }
    
    // ===== GETTERS =====
    
    public String getTitle() {
        return title;
    }
    
    public String getDescription() {
        return description;
    }
    
    public ContextPath getPath() {
        return currentPath;
    }
    
    public MenuContext getParent() {
        return parent;
    }
    
    public boolean hasParent() {
        return parent != null;
    }
    
    // ===== UTILITIES =====
    
    /**
     * Convert internal type to UI protocol type
     */
    private UIProtocol.MenuItemType convertType(MenuItemType type) {
        return switch (type) {
            case ACTION -> UIProtocol.MenuItemType.ACTION;
            case SUBMENU -> UIProtocol.MenuItemType.SUBMENU;
            case PROTECTED_SUBMENU -> UIProtocol.MenuItemType.PROTECTED_SUBMENU;
            case INFO, SEPARATOR, BACK -> UIProtocol.MenuItemType.ACTION;
        };
    }
    
    // ===== INNER CLASSES =====
    
    /**
     * Menu item
     */
    public static class MenuItem {
        public final String name;
        public final String description;
        public final MenuItemType type;
        public final Object target;
        public String badge; // Optional badge/icon
        public boolean enabled = true;
        
        MenuItem(String name, String description, MenuItemType type, Object target) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.target = target;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public void setBadge(String badge) {
            this.badge = badge;
        }
    }
    
    /**
     * Menu item types
     */
    public enum MenuItemType {
        ACTION,              // Execute runnable
        SUBMENU,            // Navigate to sub-menu
        PROTECTED_SUBMENU,  // Navigate after password
        INFO,               // Display only (no action)
        SEPARATOR,          // Visual separator
        BACK                // Explicit back navigation
    }
}