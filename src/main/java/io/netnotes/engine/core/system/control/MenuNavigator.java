package io.netnotes.engine.core.system.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.netnotes.engine.core.system.control.containers.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.containers.TerminalContainerHandle.BoxStyle;
import io.netnotes.engine.core.system.control.containers.TerminalContainerHandle.TextStyle;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyCharEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.exec.ExecutorConsumer;
import io.netnotes.engine.utils.exec.SerializedVirtualExecutor;

/**
 * MenuNavigator - Keyboard-driven terminal menu navigation
 * 
 * REFACTORED: Just a helper class, NOT a FlowProcess
 * - Registers with InputDevice via setEventConsumer()
 * - Handles up/down/enter/escape navigation
 * - Renders to TerminalContainerHandle
 * 
 * Usage:
 * ```java
 * MenuNavigator navigator = new MenuNavigator(terminal, keyboard);
 * navigator.showMenu(myMenu);
 * ```
 */
public class MenuNavigator {

    private final BitFlagStateMachine state;
    private final TerminalContainerHandle terminal;
    private final InputDevice keyboardInput;
    
    // Navigation state
    private final Stack<MenuContext> navigationStack = new Stack<>();
    private MenuContext currentMenu;
    
    // Selection state
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    
    // Layout parameters
    private static final int MENU_START_ROW = 3;
    private static final int MENU_MARGIN = 2;
    private static final int MAX_VISIBLE_ITEMS = 15;
    
    // Keyboard event consumer
    private final ExecutorConsumer<RoutedEvent> keyboardConsumer;
    private final KeyRunTable keyRunTable = new KeyRunTable(new NoteBytesRunnablePair[]{
        new NoteBytesRunnablePair(KeyCodeBytes.UP, this::handleNavigateUp),
        new NoteBytesRunnablePair(KeyCodeBytes.DOWN, this::handleNavigateDown),
        new NoteBytesRunnablePair(KeyCodeBytes.ENTER, this::handleSelectCurrent),
        new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, this::handleBack),
        new NoteBytesRunnablePair(KeyCodeBytes.PAGE_UP, this::handlePageUp),
        new NoteBytesRunnablePair(KeyCodeBytes.PAGE_DOWN, this::handlePageDown),
        new NoteBytesRunnablePair(KeyCodeBytes.HOME, this::handleHome),
        new NoteBytesRunnablePair(KeyCodeBytes.END, this::handleEnd),
    });
    
    // States
    public static final long IDLE = 1L << 0;
    public static final long DISPLAYING_MENU = 1L << 1;
    public static final long NAVIGATING = 1L << 2;
    public static final long WAITING_PASSWORD = 1L << 3;
    public static final long EXECUTING_ACTION = 1L << 4;
    
    public MenuNavigator(
            TerminalContainerHandle terminal,
            InputDevice keyboardInput) {
        
        this.terminal = terminal;
        this.keyboardInput = keyboardInput;
        this.state = new BitFlagStateMachine("menu-navigator");
        
        // Create keyboard event consumer with executor
        this.keyboardConsumer = new ExecutorConsumer<>(
            new SerializedVirtualExecutor(),
            this::handleKeyboardEvent
        );
        
        setupStateTransitions();
        state.addState(IDLE);
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(IDLE, (old, now, bit) -> {
            if (keyboardInput != null) {
                keyboardInput.setEventConsumer(null);
            }
        });
        
        state.onStateAdded(DISPLAYING_MENU, (old, now, bit) -> {
            if (keyboardInput != null) {
                keyboardInput.setEventConsumer(keyboardConsumer);
            }
            
            if (currentMenu != null) {
                renderMenu();
            }
        });
        
        state.onStateAdded(WAITING_PASSWORD, (old, now, bit) -> {
            // Unregister - password reader will handle input
            if (keyboardInput != null) {
                keyboardInput.setEventConsumer(null);
            }
        });
        
        state.onStateRemoved(WAITING_PASSWORD, (old, now, bit) -> {
            // Re-register when password session ends
            if (state.hasState(DISPLAYING_MENU) && keyboardInput != null) {
                keyboardInput.setEventConsumer(keyboardConsumer);
            }
        });
    }
    
    // ===== KEYBOARD EVENT HANDLING =====
    
    private void handleKeyboardEvent(RoutedEvent event) {
        if (!state.hasState(DISPLAYING_MENU)) {
            return;
        }

       
       
        // Handle ephemeral events (from secure input devices)
        if (event instanceof EphemeralRoutedEvent ephemeralEvent) {
            try (ephemeralEvent) {
                if (ephemeralEvent instanceof EphemeralKeyDownEvent ekd) {
                    keyRunTable.run(ekd.getKeyCodeBytes());
                }
            }
            return;
        }
        
        // Handle regular events (from GUI keyboards)
        if (event instanceof KeyDownEvent keyDown) {
            keyRunTable.run(keyDown.getKeyCodeBytes());
        }
    }
    
    // ===== NAVIGATION HANDLERS =====
    
    private void handleNavigateUp() {
        Log.logMsg("[MenuNavigator] UP pressed");
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = (selectedIndex - 1 + selectableItems.size()) % selectableItems.size();
        
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        }
        
        renderMenu();
    }
    
    private void handleNavigateDown() {
        Log.logMsg("[MenuNavigator] DOWN pressed");
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = (selectedIndex + 1) % selectableItems.size();
        
        if (selectedIndex >= scrollOffset + MAX_VISIBLE_ITEMS) {
            scrollOffset = selectedIndex - MAX_VISIBLE_ITEMS + 1;
        }
        
        renderMenu();
    }
    
    private void handleSelectCurrent() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        
        if (selectedIndex < 0 || selectedIndex >= selectableItems.size()) {
            return;
        }
        
        MenuContext.MenuItem selectedItem = selectableItems.get(selectedIndex);
        
        state.removeState(DISPLAYING_MENU);
        state.addState(NAVIGATING);
        
        currentMenu.navigate(selectedItem.name)
            .thenAccept(targetMenu -> {
                state.removeState(NAVIGATING);
                
                if (targetMenu == null) {
                    // Password required
                    state.addState(WAITING_PASSWORD);
                    notifyParentPasswordRequired(selectedItem.name);
                } else if (targetMenu == currentMenu) {
                    // Action executed, stay on same menu
                    state.addState(DISPLAYING_MENU);
                } else {
                    // Navigate to new menu
                    showMenu(targetMenu);
                }
            })
            .exceptionally(ex -> {
                terminal.clear()
                    .thenCompose(v -> terminal.printError(
                        "Navigation failed: " + ex.getMessage()))
                    .thenCompose(v -> terminal.println("\nPress any key to continue..."));
                
                state.removeState(NAVIGATING);
                state.addState(DISPLAYING_MENU);
                return null;
            });
    }
    
    private void handleBack() {
        if (navigationStack.isEmpty()) {
            notifyParentAtRoot();
        } else {
            MenuContext previousMenu = navigationStack.pop();
            currentMenu = previousMenu;
            selectedIndex = 0;
            scrollOffset = 0;
            
            state.removeState(WAITING_PASSWORD);
            state.removeState(EXECUTING_ACTION);
            state.addState(DISPLAYING_MENU);
        }
    }
    
    private void handlePageUp() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = Math.max(0, selectedIndex - MAX_VISIBLE_ITEMS);
        scrollOffset = Math.max(0, scrollOffset - MAX_VISIBLE_ITEMS);
        renderMenu();
    }
    
    private void handlePageDown() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = Math.min(selectableItems.size() - 1, 
            selectedIndex + MAX_VISIBLE_ITEMS);
        
        int maxScroll = Math.max(0, selectableItems.size() - MAX_VISIBLE_ITEMS);
        scrollOffset = Math.min(maxScroll, scrollOffset + MAX_VISIBLE_ITEMS);
        
        renderMenu();
    }
    
    private void handleHome() {
        selectedIndex = 0;
        scrollOffset = 0;
        renderMenu();
    }
    
    private void handleEnd() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = selectableItems.size() - 1;
        scrollOffset = Math.max(0, selectableItems.size() - MAX_VISIBLE_ITEMS);
        renderMenu();
    }
    
    private List<MenuContext.MenuItem> getSelectableItems() {
        if (currentMenu == null) {
            return List.of();
        }
        
        return new ArrayList<>(currentMenu.getItems()).stream()
            .filter(item -> item.type != MenuContext.MenuItemType.SEPARATOR &&
                           item.type != MenuContext.MenuItemType.INFO)
            .toList();
    }
    
    // ===== MENU DISPLAY =====
    
    public void showMenu(MenuContext menu) {
        if (menu == null) {
            return;
        }
        
        if (currentMenu != null && currentMenu != menu) {
            navigationStack.push(currentMenu);
        }
        
        currentMenu = menu;
        selectedIndex = 0;
        scrollOffset = 0;
        
        state.removeState(IDLE);
        state.removeState(NAVIGATING);
        state.removeState(EXECUTING_ACTION);
        state.addState(DISPLAYING_MENU);
    }
    
    private void renderMenu() {
        if (currentMenu == null) {
            return;
        }
        
        terminal.beginBatch()
            .thenCompose(v -> terminal.clear())
            .thenCompose(v -> renderHeader())
            .thenCompose(v -> renderBreadcrumb())
            .thenCompose(v -> renderMenuItems())
            .thenCompose(v -> renderDescription())
            .thenCompose(v -> renderFooter())
            .thenCompose(v -> terminal.endBatch())
            .exceptionally(ex -> {
                terminal.clear()
                    .thenCompose(v1 -> terminal.printError(
                        "Failed to display menu: " + ex.getMessage()))
                    .thenCompose(v1 -> terminal.println("\nPress ESC to go back"));
                return null;
            });
    }
    
    private CompletableFuture<Void> renderHeader() {
        String title = currentMenu.getTitle();
        int cols = terminal.getCols();
        int titleLen = title.length();
        int boxWidth = Math.min(cols - 4, Math.max(titleLen + 4, 40));
        
        return terminal.drawBox(0, MENU_MARGIN, boxWidth, 3, title, BoxStyle.SINGLE);
    }
    
    private CompletableFuture<Void> renderBreadcrumb() {
        List<String> trail = new ArrayList<>();
        MenuContext current = currentMenu;
        
        while (current != null) {
            trail.add(0, current.getTitle());
            current = current.getParent();
        }
        
        String breadcrumb = String.join(" > ", trail);
        return terminal.printAt(2, MENU_MARGIN, breadcrumb, TextStyle.INFO);
    }
    
    private CompletableFuture<Void> renderMenuItems() {
        List<MenuContext.MenuItem> items = new ArrayList<>(currentMenu.getItems());
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        
        if (selectedIndex >= selectableItems.size()) {
            selectedIndex = Math.max(0, selectableItems.size() - 1);
        }
        
        int totalItems = items.size();
        int visibleStart = scrollOffset;
        int visibleEnd = Math.min(visibleStart + MAX_VISIBLE_ITEMS, totalItems);
        
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        final int startRow = MENU_START_ROW;
        int selectableIndex = 0;
        
        for (int i = visibleStart; i < visibleEnd; i++) {
            MenuContext.MenuItem item = items.get(i);
            final int currentRow = startRow + (i - visibleStart);
            
            boolean isSelected = (item.type != MenuContext.MenuItemType.SEPARATOR &&
                                 item.type != MenuContext.MenuItemType.INFO &&
                                 selectableIndex == selectedIndex);
            
            future = future.thenCompose(v -> renderMenuItem(item, currentRow, isSelected));
            
            if (item.type != MenuContext.MenuItemType.SEPARATOR &&
                item.type != MenuContext.MenuItemType.INFO) {
                selectableIndex++;
            }
        }
        
        if (scrollOffset > 0) {
            future = future.thenCompose(v -> 
                terminal.printAt(MENU_START_ROW - 1, MENU_MARGIN, "↑ More above", 
                    TextStyle.INFO));
        }
        
        int moreBelowRow = startRow + (visibleEnd - visibleStart);
        
        if (visibleEnd < totalItems) {
            future = future.thenCompose(v -> 
                terminal.printAt(moreBelowRow, MENU_MARGIN, "↓ More below", TextStyle.INFO));
        }
        
        return future;
    }
    
    private CompletableFuture<Void> renderMenuItem(
            MenuContext.MenuItem item, 
            int row, 
            boolean isSelected) {
        
        CompletableFuture<Void> future;
        
        switch (item.type) {
            case SEPARATOR:
                future = terminal.printAt(row, MENU_MARGIN, "─".repeat(40), TextStyle.NORMAL)
                    .thenCompose(v -> {
                        if (item.description != null && !item.description.isEmpty()) {
                            return terminal.printAt(row, MENU_MARGIN + 2, 
                                " " + item.description + " ", TextStyle.BOLD);
                        }
                        return CompletableFuture.completedFuture(null);
                    });
                break;
                
            case INFO:
                future = terminal.printAt(row, MENU_MARGIN + 2, 
                    item.description, TextStyle.INFO);
                break;
                
            default:
                String prefix = isSelected ? "▶ " : "  ";
                String badge = item.badge != null ? " [" + item.badge + "]" : "";
                String text = prefix + item.description + badge;
                
                TextStyle style = isSelected ? TextStyle.INVERSE : TextStyle.NORMAL;
                
                if (!item.enabled) {
                    style = TextStyle.INFO;
                }
                
                future = terminal.printAt(row, MENU_MARGIN, text, style);
                break;
        }
        
        return future;
    }
    
    private CompletableFuture<Void> renderDescription() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        
        if (selectedIndex >= 0 && selectedIndex < selectableItems.size()) {
            MenuContext.MenuItem selected = selectableItems.get(selectedIndex);
            
            int descRow = terminal.getRows() - 4;
            
            String info = "";
            if (selected.type == MenuContext.MenuItemType.SUBMENU) {
                info = "→ Opens submenu";
            }
            
            if (!info.isEmpty()) {
                return terminal.printAt(descRow, MENU_MARGIN, info, TextStyle.INFO);
            }
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> renderFooter() {
        int footerRow = terminal.getRows() - 2;
        
        String help = currentMenu.hasParent() || !navigationStack.isEmpty() 
            ? "↑↓: Navigate  Enter: Select  ESC: Back  Home/End: Jump  PgUp/PgDn: Scroll"
            : "↑↓: Navigate  Enter: Select  Home/End: Jump  PgUp/PgDn: Scroll";
        
        return terminal.drawHLine(footerRow - 1, 0, terminal.getCols())
            .thenCompose(v -> terminal.printAt(footerRow, MENU_MARGIN, help, 
                TextStyle.INFO));
    }
    
    public void refreshMenu() {
        if (state.hasState(DISPLAYING_MENU) && currentMenu != null) {
            renderMenu();
        }
    }
    
    // ===== PARENT COMMUNICATION =====
    
    public void onPasswordSuccess(String menuItemName) {
        if (!state.hasState(WAITING_PASSWORD)) {
            return;
        }
        
        state.removeState(WAITING_PASSWORD);
        state.addState(NAVIGATING);
        
        currentMenu.navigate(menuItemName)
            .thenAccept(targetMenu -> {
                state.removeState(NAVIGATING);
                if (targetMenu != null) {
                    showMenu(targetMenu);
                } else {
                    state.addState(DISPLAYING_MENU);
                }
            })
            .exceptionally(ex -> {
                state.removeState(NAVIGATING);
                state.addState(DISPLAYING_MENU);
                return null;
            });
    }
    
    public void onPasswordCancelled() {
        state.removeState(WAITING_PASSWORD);
        state.addState(DISPLAYING_MENU);
    }
    
    private void notifyParentPasswordRequired(String itemName) {
        // Parent should listen for state change
    }
    
    private void notifyParentAtRoot() {
        // Parent should handle closing or showing main menu
    }
    
    public void cleanup() {
        if (keyboardInput != null) {
            keyboardInput.setEventConsumer(null);
        }
    }
    
    // ===== GETTERS =====
    
    public BitFlagStateMachine getState() { return state; }
    public MenuContext getCurrentMenu() { return currentMenu; }
    public boolean hasMenu() { return currentMenu != null; }
    public boolean isDisplayingMenu() { return state.hasState(DISPLAYING_MENU); }
    public boolean isWaitingForPassword() { return state.hasState(WAITING_PASSWORD); }
    public TerminalContainerHandle getTerminal() { return terminal; }
}