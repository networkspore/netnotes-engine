package io.netnotes.engine.core.system.control.terminal.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.containers.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.terminal.elements.TerminalTextBox;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;

import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.ExecutorConsumer;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;

/**
 * MenuNavigator - Keyboard-driven terminal menu navigation
 * 
 */
public class MenuNavigator {



    private final BitFlagStateMachine state;
    private final TerminalContainerHandle terminal;
    private final InputDevice keyboardInput;
    private ScrollableMenuItem menuItemRenderer;
    private int horizontalScrollOffset = 0;  // For currently selected item

    
    // Navigation state
    private final Stack<MenuContext> navigationStack = new Stack<>();
    private MenuContext currentMenu;
    
    // Selection state
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    
    // Layout parameters
    private static final int MAX_VISIBLE_ITEMS = 15;
    
    // Keyboard event consumer
    private final ExecutorConsumer<RoutedEvent> keyboardConsumer;
    
    private final KeyRunTable keyRunTable = new KeyRunTable(new NoteBytesRunnablePair[]{
        new NoteBytesRunnablePair(KeyCodeBytes.UP, this::handleNavigateUp),
        new NoteBytesRunnablePair(KeyCodeBytes.DOWN, this::handleNavigateDown),
        new NoteBytesRunnablePair(KeyCodeBytes.LEFT, this::handleScrollLeft),
        new NoteBytesRunnablePair(KeyCodeBytes.RIGHT, this::handleScrollRight),
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
            .thenCompose(v -> terminal.hideCursor())
            .thenCompose(v -> renderHeader())
            .thenCompose(v -> renderBreadcrumb())
            .thenCompose(v -> renderDescription())  // ADD THIS LINE
            .thenCompose(v -> renderMenuItems())
            .thenCompose(v -> renderFooter())  // Remove renderDescription() call here if exists
            .thenCompose(v -> terminal.endBatch())
            .exceptionally(ex -> {
                terminal.clear()
                    .thenCompose(v1 -> terminal.printError(
                        "Failed to display menu: " + ex.getMessage()))
                    .thenCompose(v1 -> terminal.println("\nPress ESC to go back"));
                return null;
            });
    }
    
    /**
     * Render header (centered) using TextBox
     */
    private CompletableFuture<Void> renderHeader() {
        MenuDimensions dims = calculateMenuDimensions();
        String title = currentMenu.getTitle();
        
        return TerminalTextBox.builder()
            .position(0, dims.getBoxCol())
            .size(dims.getBoxWidth(), 3)
            .title(title, TerminalTextBox.TitlePlacement.INSIDE_TOP)
            .style(BoxStyle.SINGLE)
            .titleStyle(TextStyle.BOLD)
            .contentAlignment(TerminalTextBox.ContentAlignment.CENTER)
            .build()
            .render(terminal);
    }
    
    /**
     * Render breadcrumb (centered, below the header box)
     */
    private CompletableFuture<Void> renderBreadcrumb() {
        MenuDimensions dims = calculateMenuDimensions();
        
        List<String> trail = new ArrayList<>();
        MenuContext current = currentMenu;
        
        while (current != null) {
            trail.add(0, current.getTitle());
            current = current.getParent();
        }
        
        // Only show breadcrumb if there's a trail (more than just current menu)
        if (trail.size() <= 1) {
            return CompletableFuture.completedFuture(null);
        }
        
        String breadcrumb = String.join(" > ", trail);
        
        // Center the breadcrumb text below the header box (row 3)
        int textCol = dims.getBoxCol() + (dims.getBoxWidth() - breadcrumb.length()) / 2;
        
        return terminal.printAt(3, textCol, breadcrumb, TextStyle.INFO);
    }

    
        
   /**
     * Render menu items (centered with proper padding)
     */
    private CompletableFuture<Void> renderMenuItems() {
        MenuDimensions dims = calculateMenuDimensions();
        List<MenuContext.MenuItem> items = new ArrayList<>(currentMenu.getItems());
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        
        if (selectedIndex >= selectableItems.size()) {
            selectedIndex = Math.max(0, selectableItems.size() - 1);
        }
        
        int totalItems = items.size();
        int visibleStart = scrollOffset;
        int visibleEnd = Math.min(visibleStart + MAX_VISIBLE_ITEMS, totalItems);
        
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        
        // Calculate start row - leave space for description if present
        String description = currentMenu.getDescription();
        int descriptionLines = 0;
        if (description != null && !description.isEmpty()) {
            descriptionLines = Math.min(description.split("\n").length + 1, 9); // +1 for spacing
        }
        
        final int startRow = 5 + descriptionLines; // Below header, breadcrumb, and description
        int selectableIndex = 0;
        
        for (int i = visibleStart; i < visibleEnd; i++) {
            MenuContext.MenuItem item = items.get(i);
            final int currentRow = startRow + (i - visibleStart);
            
            boolean isSelected = (item.type != MenuContext.MenuItemType.SEPARATOR &&
                                item.type != MenuContext.MenuItemType.INFO &&
                                selectableIndex == selectedIndex);
            
            future = future.thenCompose(v -> renderMenuItem(item, currentRow, isSelected, dims));
            
            if (item.type != MenuContext.MenuItemType.SEPARATOR &&
                item.type != MenuContext.MenuItemType.INFO) {
                selectableIndex++;
            }
        }
        
        // Scroll indicators
        if (scrollOffset > 0) {
            int indicatorCol = dims.getBoxCol() + dims.getBoxWidth() / 2 - 5;
            future = future.thenCompose(v -> 
                terminal.printAt(startRow - 1, indicatorCol, "↑ More above", 
                    TextStyle.INFO));
        }
        
        int moreBelowRow = startRow + (visibleEnd - visibleStart);
        
        if (visibleEnd < totalItems) {
            int indicatorCol = dims.getBoxCol() + dims.getBoxWidth() / 2 - 5;
            future = future.thenCompose(v -> 
                terminal.printAt(moreBelowRow, indicatorCol, "↓ More below", TextStyle.INFO));
        }
        
        return future;
    }
        
    /**
     * Render single menu item with improved highlighting and horizontal scroll
     */
    private CompletableFuture<Void> renderMenuItem(
            MenuContext.MenuItem item, 
            int row, 
            boolean isSelected,
            MenuDimensions dims) {
        
        CompletableFuture<Void> future;
        
        // Calculate content area (inside the box margins)
        int contentStartCol = dims.getBoxCol() + 2;
        int contentWidth = dims.getItemContentWidth();
        
        switch (item.type) {
            case SEPARATOR:
                // Draw separator line
                String separatorLine = "─".repeat(contentWidth);
                future = terminal.printAt(row, contentStartCol, separatorLine, TextStyle.NORMAL)
                    .thenCompose(v -> {
                        if (item.description != null && !item.description.isEmpty()) {
                            // Center separator label
                            int labelCol = contentStartCol + 
                                (contentWidth - item.description.length()) / 2;
                            return terminal.printAt(row, labelCol, 
                                " " + item.description + " ", TextStyle.BOLD);
                        }
                        return CompletableFuture.completedFuture(null);
                    });
                break;
                
            case INFO:
                // Center info text
                int infoCol = contentStartCol + (contentWidth - item.description.length()) / 2;
                future = terminal.printAt(row, infoCol, item.description, TextStyle.INFO);
                break;
                
            default:
                // Build item text
                String badge = item.badge != null ? " [" + item.badge + "]" : "";
                String itemText = item.description + badge;
                
                if (isSelected) {
                    // Use scrollable menu item renderer for highlighted items
                    future = menuItemRenderer.renderHighlighted(
                        terminal,
                        row,
                        contentStartCol,
                        contentWidth,
                        itemText,
                        horizontalScrollOffset
                    );
                } else {
                    // Not selected - left aligned with padding, truncate if needed
                    String displayText = "  " + menuItemRenderer.truncateText(itemText, contentWidth - 2);
                    TextStyle style = item.enabled ? TextStyle.NORMAL : TextStyle.INFO;
                    
                    future = terminal.printAt(row, contentStartCol, displayText, style);
                }
                break;
        }
        
        return future;
    }
    private CompletableFuture<Void> renderDescription() {
        String description = currentMenu.getDescription();
        
        if (description == null || description.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        MenuDimensions dims = calculateMenuDimensions();
        
        // Show description in a box above the menu items
        String[] lines = description.split("\n");
        int descRow = 5; // Below header and breadcrumb
        
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        
        for (int i = 0; i < lines.length && i < 8; i++) { // Max 8 lines
            String line = lines[i];
            final int row = descRow + i;
            
            // Center or left-align the description text
            int col = dims.getBoxCol() + 4; // Left-aligned with padding
            
            future = future.thenCompose(v -> 
                terminal.printAt(row, col, line, TextStyle.NORMAL));
        }
        
        return future;
    }
    
    /**
     * Render footer (centered)
     */
    private CompletableFuture<Void> renderFooter() {
        MenuDimensions dims = calculateMenuDimensions();
        int footerRow = terminal.getRows() - 2;
        
        String help = currentMenu.hasParent() || !navigationStack.isEmpty() 
            ? "↑↓: Navigate  ←→: Scroll Text  Enter: Select  ESC: Back  Home/End: Jump"
            : "↑↓: Navigate  ←→: Scroll Text  Enter: Select  Home/End: Jump";
        
        // Center help text
        int helpCol = dims.getBoxCol() + (dims.getBoxWidth() - help.length()) / 2;
        
        return terminal.drawHLine(footerRow - 1, dims.getBoxCol(), dims.getBoxWidth())
            .thenCompose(v -> terminal.printAt(footerRow, helpCol, help, TextStyle.INFO));
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

    /**
     * Calculate optimal menu dimensions based on content
     */
    private MenuDimensions calculateMenuDimensions() {
        List<MenuContext.MenuItem> items = new ArrayList<>(currentMenu.getItems());
        
        int maxTextLength = currentMenu.getTitle().length();
        
        // Find longest item text
        for (MenuContext.MenuItem item : items) {
            int itemLength = item.description.length();
            if (item.badge != null) {
                itemLength += item.badge.length() + 3; // " [badge]"
            }
            maxTextLength = Math.max(maxTextLength, itemLength);
        }
        
        // Add padding (4 chars on each side for margins, plus 2 for box borders)
        int contentWidth = maxTextLength + 8;
        
        // Ensure minimum and maximum bounds
        int boxWidth = Math.max(40, Math.min(contentWidth, terminal.getCols() - 8));
        
        // Center horizontally
        int boxCol = (terminal.getCols() - boxWidth) / 2;
        
        // Initialize menu item renderer with the calculated width
        int itemContentWidth = boxWidth - 4; // Account for borders and padding
        this.menuItemRenderer = new ScrollableMenuItem(itemContentWidth, 
            ScrollableMenuItem.TextOverflowStrategy.SCROLL_ON_SELECT);
        
        return new MenuDimensions(boxWidth, boxCol, itemContentWidth);
    }

    private void handleScrollRight() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty() || selectedIndex >= selectableItems.size()) return;
        
        MenuContext.MenuItem item = selectableItems.get(selectedIndex);
        String itemText = item.description + (item.badge != null ? " [" + item.badge + "]" : "");
        
        // Only scroll if text is longer than display width
        if (menuItemRenderer != null && itemText.length() > menuItemRenderer.getMaxWidth()) {
            int maxOffset = menuItemRenderer.getMaxScrollOffset(itemText);
            horizontalScrollOffset = Math.min(maxOffset, horizontalScrollOffset + 5);
            renderMenu();
        }
    }


    private void handleScrollLeft() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty() || selectedIndex >= selectableItems.size()) return;
        
        MenuContext.MenuItem item = selectableItems.get(selectedIndex);
        String itemText = item.description + (item.badge != null ? " [" + item.badge + "]" : "");
        
        // Only scroll if text is longer than display width
        if (menuItemRenderer != null && itemText.length() > menuItemRenderer.getMaxWidth()) {
            horizontalScrollOffset = Math.max(0, horizontalScrollOffset - 5);
            renderMenu();
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