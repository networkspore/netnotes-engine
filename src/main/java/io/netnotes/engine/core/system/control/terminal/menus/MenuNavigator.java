package io.netnotes.engine.core.system.control.terminal.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import io.netnotes.engine.core.system.control.terminal.BatchBuilder;
import io.netnotes.engine.core.system.control.terminal.RenderManager;
import io.netnotes.engine.core.system.control.terminal.RenderManager.RenderElement;
import io.netnotes.engine.core.system.control.terminal.RenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.RenderManager.Renderable;
import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.function.Consumer;

/**
 * MenuNavigator - REFACTORED for BatchBuilder-based rendering
 * 
 * KEY CHANGES:
 * 1. RenderElement now uses BatchBuilder instead of (terminal, gen) lambda
 * 2. All rendering operations add commands to batch
 * 3. No direct terminal.print() calls
 * 4. Clean separation: getRenderState() builds description, RenderManager renders it
 */
public class MenuNavigator implements Renderable {

    private final BitFlagStateMachine state;
    private final TerminalContainerHandle terminal;
    private final RenderManager renderManager;
    private ScrollableMenuItem menuItemRenderer;
    private int horizontalScrollOffset = 0;
    
    // Navigation state
    private final Stack<MenuContext> navigationStack = new Stack<>();
    private MenuContext currentMenu;
    
    // Selection state
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    
    // Layout parameters
    private static final int MAX_VISIBLE_ITEMS = 15;
    
    // Event handlers
    private final Consumer<RoutedEvent> keyboardConsumer;
    private final Consumer<RoutedEvent> resizeConsumer;
    
    // Key mapping table
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
    
    public MenuNavigator(TerminalContainerHandle terminal) {
        this.terminal = terminal;
        this.renderManager = terminal.getRenderManager();
        this.state = new BitFlagStateMachine("menu-navigator");
        
        this.keyboardConsumer = this::handleKeyboardEvent;
        this.resizeConsumer = this::handleResizeEvent;
        
        setupStateTransitions();
        state.addState(IDLE);
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(IDLE, (old, now, bit) -> {
            terminal.removeKeyDownHandler(keyboardConsumer);
            terminal.removeResizeHandler(resizeConsumer);
        });
        
        state.onStateAdded(DISPLAYING_MENU, (old, now, bit) -> {
            terminal.addKeyDownHandler(keyboardConsumer);
            terminal.addResizeHandler(resizeConsumer);
            
            if (currentMenu != null) {
                renderManager.setActive(this);
            }
        });
        
        state.onStateAdded(WAITING_PASSWORD, (old, now, bit) -> {
            terminal.removeKeyDownHandler(keyboardConsumer);
        });
        
        state.onStateRemoved(WAITING_PASSWORD, (old, now, bit) -> {
            if (state.hasState(DISPLAYING_MENU)) {
                terminal.addKeyDownHandler(keyboardConsumer);
            }
        });
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    /**
     * Build render state (description of what to draw)
     * This is FAST - just builds data structures, doesn't render
     */
    @Override
    public RenderState getRenderState() {
        if (currentMenu == null) {
            return RenderState.builder().build();
        }
        
        RenderState.Builder stateBuilder = RenderState.builder();
        MenuDimensions dims = calculateMenuDimensions();
        
        // Add all render elements
        stateBuilder.add(createHeaderElement(dims));
        stateBuilder.addIf(navigationStack.size() > 0, createBreadcrumbElement(dims));
        
        String description = currentMenu.getDescription();
        stateBuilder.addIf(description != null && !description.isEmpty(), 
            createDescriptionElement(dims));
        
        stateBuilder.addAll(createMenuItemElements(dims));
        stateBuilder.add(createFooterElement(dims));
        
        return stateBuilder.build();
    }
    
    // ===== RENDER ELEMENT CREATORS =====
    
    /**
     * Create header element
     * Returns a function that adds commands to BatchBuilder
     */
    private RenderElement createHeaderElement(MenuDimensions dims) {
        String title = currentMenu.getTitle();
        
        return batch -> {
            batch.drawBox(0, dims.boxCol, dims.boxWidth, 3, title, BoxStyle.SINGLE);
            
            int titleRow = 1;
            int titleCol = dims.boxCol + (dims.boxWidth - title.length()) / 2;
            batch.printAt(titleRow, titleCol, title, TextStyle.BOLD);
        };
    }
    
    /**
     * Create breadcrumb element
     */
    private RenderElement createBreadcrumbElement(MenuDimensions dims) {
        List<String> trail = new ArrayList<>();
        MenuContext current = currentMenu;
        
        while (current != null) {
            trail.add(0, current.getTitle());
            current = current.getParent();
        }
        
        String breadcrumb = String.join(" > ", trail);
        int textCol = dims.boxCol + (dims.boxWidth - breadcrumb.length()) / 2;
        
        return batch -> {
            batch.printAt(3, textCol, breadcrumb, TextStyle.INFO);
        };
    }
    
    /**
     * Create description element
     */
    private RenderElement createDescriptionElement(MenuDimensions dims) {
        String description = currentMenu.getDescription();
        String[] lines = description.split("\n");
        int descRow = 5;
        
        return batch -> {
            for (int i = 0; i < lines.length && i < 8; i++) {
                String line = lines[i];
                int row = descRow + i;
                int col = dims.boxCol + 4;
                batch.printAt(row, col, line, TextStyle.NORMAL);
            }
        };
    }
    
    /**
     * Create menu items elements
     */
    private List<RenderElement> createMenuItemElements(MenuDimensions dims) {
        List<RenderElement> elements = new ArrayList<>();
        
        List<MenuContext.MenuItem> items = new ArrayList<>(currentMenu.getItems());
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        
        // Clamp selection
        if (selectedIndex >= selectableItems.size()) {
            selectedIndex = Math.max(0, selectableItems.size() - 1);
        }
        
        int totalItems = items.size();
        int visibleStart = scrollOffset;
        int visibleEnd = Math.min(visibleStart + MAX_VISIBLE_ITEMS, totalItems);
        
        String description = currentMenu.getDescription();
        int descriptionLines = 0;
        if (description != null && !description.isEmpty()) {
            descriptionLines = Math.min(description.split("\n").length + 1, 9);
        }
        
        final int startRow = 5 + descriptionLines;
        int selectableIndex = 0;
        
        // Create element for each visible item
        for (int i = visibleStart; i < visibleEnd; i++) {
            MenuContext.MenuItem item = items.get(i);
            final int currentRow = startRow + (i - visibleStart);
            
            boolean isSelected = (item.type != MenuContext.MenuItemType.SEPARATOR &&
                                item.type != MenuContext.MenuItemType.INFO &&
                                selectableIndex == selectedIndex);
            
            elements.add(createMenuItemElement(item, currentRow, isSelected, dims));
            
            if (item.type != MenuContext.MenuItemType.SEPARATOR &&
                item.type != MenuContext.MenuItemType.INFO) {
                selectableIndex++;
            }
        }
        
        // Scroll indicators
        if (scrollOffset > 0) {
            int indicatorCol = dims.boxCol + dims.boxWidth / 2 - 5;
            int indicatorRow = startRow - 1;
            elements.add(batch -> 
                batch.printAt(indicatorRow, indicatorCol, "↑ More above", TextStyle.INFO));
        }
        
        if (visibleEnd < totalItems) {
            int indicatorCol = dims.boxCol + dims.boxWidth / 2 - 5;
            int indicatorRow = startRow + (visibleEnd - visibleStart);
            elements.add(batch -> 
                batch.printAt(indicatorRow, indicatorCol, "↓ More below", TextStyle.INFO));
        }
        
        return elements;
    }
    
    /**
     * Create single menu item element
     */
    private RenderElement createMenuItemElement(
            MenuContext.MenuItem item,
            int row,
            boolean isSelected,
            MenuDimensions dims) {
        
        int contentStartCol = dims.boxCol + 2;
        int contentWidth = dims.itemContentWidth;
        
        return switch (item.type) {
            case SEPARATOR -> batch -> {
                String separatorLine = "─".repeat(contentWidth);
                batch.printAt(row, contentStartCol, separatorLine, TextStyle.NORMAL);
                
                if (item.description != null && !item.description.isEmpty()) {
                    int labelCol = contentStartCol + 
                        (contentWidth - item.description.length()) / 2;
                    batch.printAt(row, labelCol, 
                        " " + item.description + " ", TextStyle.BOLD);
                }
            };
            
            case INFO -> batch -> {
                int infoCol = contentStartCol + 
                    (contentWidth - item.description.length()) / 2;
                batch.printAt(row, infoCol, item.description, TextStyle.INFO);
            };
            
            default -> batch -> {
                String badge = item.badge != null ? " [" + item.badge + "]" : "";
                String itemText = item.description + badge;
                
                if (isSelected) {
                    // Highlighted with scroll
                    addHighlightedItemToBatch(
                        batch,
                        row,
                        contentStartCol,
                        contentWidth,
                        itemText,
                        horizontalScrollOffset
                    );
                } else {
                    // Normal item
                    String displayText = "  " + truncateText(itemText, contentWidth - 2);
                    TextStyle style = item.enabled ? TextStyle.NORMAL : TextStyle.INFO;
                    batch.printAt(row, contentStartCol, displayText, style);
                }
            };
        };
    }
    
    /**
     * Add highlighted menu item to batch
     */
    private void addHighlightedItemToBatch(
            BatchBuilder batch,
            int row,
            int col,
            int maxWidth,
            String text,
            int scrollOffset) {
        
        // Selection indicator
        String indicator = "> ";
        
        // Available width after indicator
        int textWidth = maxWidth - indicator.length();
        
        // Apply scroll offset
        String displayText;
        if (text.length() <= textWidth) {
            // Fits without scrolling
            displayText = text;
        } else {
            // Apply horizontal scroll
            int startIdx = Math.min(scrollOffset, Math.max(0, text.length() - textWidth));
            int endIdx = Math.min(text.length(), startIdx + textWidth);
            displayText = text.substring(startIdx, endIdx);
        }
        
        // Pad to fill width
        displayText = String.format("%-" + textWidth + "s", displayText);
        
        // Add to batch
        batch.printAt(row, col, indicator + displayText, TextStyle.INVERSE);
    }
    
    /**
     * Create footer element
     */
    private RenderElement createFooterElement(MenuDimensions dims) {
        int footerRow = terminal.getRows() - 2;
        
        String help = currentMenu.hasParent() || !navigationStack.isEmpty() 
            ? "↑↓: Navigate  ←→: Scroll Text  Enter: Select  ESC: Back  Home/End: Jump"
            : "↑↓: Navigate  ←→: Scroll Text  Enter: Select  Home/End: Jump";
        
        int helpCol = dims.boxCol + (dims.boxWidth - help.length()) / 2;
        
        return batch -> {
            batch.drawHLine(footerRow - 1, dims.boxCol, dims.boxWidth);
            batch.printAt(footerRow, helpCol, help, TextStyle.INFO);
        };
    }
    
    // ===== KEYBOARD EVENT HANDLING =====
    
    private void handleKeyboardEvent(RoutedEvent event) {
        if (!state.hasState(DISPLAYING_MENU)) {
            return;
        }
        
        if (event instanceof EphemeralRoutedEvent ephemeralEvent) {
            try (ephemeralEvent) {
                if (ephemeralEvent instanceof EphemeralKeyDownEvent ekd) {
                    keyRunTable.run(ekd.getKeyCodeBytes());
                }
            }
            return;
        }
        
        if (event instanceof KeyDownEvent keyDown) {
            keyRunTable.run(keyDown.getKeyCodeBytes());
        }
    }
    
    private void handleResizeEvent(RoutedEvent event) {
        if (state.hasState(DISPLAYING_MENU) && currentMenu != null) {
            horizontalScrollOffset = 0;
            renderManager.incrementGeneration(); // Resize = new generation
        }
    }
    
    // ===== NAVIGATION HANDLERS =====
    
    private void handleNavigateUp() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = (selectedIndex - 1 + selectableItems.size()) % selectableItems.size();
        
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        }
        
        renderManager.invalidate();
    }
    
    private void handleNavigateDown() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = (selectedIndex + 1) % selectableItems.size();
        
        if (selectedIndex >= scrollOffset + MAX_VISIBLE_ITEMS) {
            scrollOffset = selectedIndex - MAX_VISIBLE_ITEMS + 1;
        }
        
        renderManager.invalidate();
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
                    state.addState(WAITING_PASSWORD);
                    notifyParentPasswordRequired(selectedItem.name);
                } else if (targetMenu == currentMenu) {
                    state.addState(DISPLAYING_MENU);
                } else {
                    showMenu(targetMenu);
                }
            })
            .exceptionally(ex -> {
                Log.logError("[MenuNavigator] Navigation failed: " + ex.getMessage());
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
            
            renderManager.invalidate();
        }
    }
    
    private void handlePageUp() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = Math.max(0, selectedIndex - MAX_VISIBLE_ITEMS);
        scrollOffset = Math.max(0, scrollOffset - MAX_VISIBLE_ITEMS);
        renderManager.invalidate();
    }
    
    private void handlePageDown() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = Math.min(selectableItems.size() - 1, 
            selectedIndex + MAX_VISIBLE_ITEMS);
        
        int maxScroll = Math.max(0, selectableItems.size() - MAX_VISIBLE_ITEMS);
        scrollOffset = Math.min(maxScroll, scrollOffset + MAX_VISIBLE_ITEMS);
        
        renderManager.invalidate();
    }
    
    private void handleHome() {
        selectedIndex = 0;
        scrollOffset = 0;
        renderManager.invalidate();
    }
    
    private void handleEnd() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = selectableItems.size() - 1;
        scrollOffset = Math.max(0, selectableItems.size() - MAX_VISIBLE_ITEMS);
        renderManager.invalidate();
    }
    
    private void handleScrollRight() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty() || selectedIndex >= selectableItems.size()) return;
        
        MenuContext.MenuItem item = selectableItems.get(selectedIndex);
        String itemText = item.description + 
            (item.badge != null ? " [" + item.badge + "]" : "");
        
        MenuDimensions dims = calculateMenuDimensions();
        int maxWidth = dims.itemContentWidth - 2; // Account for "> "
        
        if (itemText.length() > maxWidth) {
            int maxOffset = itemText.length() - maxWidth;
            horizontalScrollOffset = Math.min(maxOffset, horizontalScrollOffset + 5);
            renderManager.invalidate();
        }
    }

    private void handleScrollLeft() {
        if (horizontalScrollOffset > 0) {
            horizontalScrollOffset = Math.max(0, horizontalScrollOffset - 5);
            renderManager.invalidate();
        }
    }
    
    // ===== HELPERS =====
    
    private List<MenuContext.MenuItem> getSelectableItems() {
        if (currentMenu == null) {
            return List.of();
        }
        
        return currentMenu.getItems().stream()
            .filter(item -> item.type != MenuContext.MenuItemType.SEPARATOR &&
                           item.type != MenuContext.MenuItemType.INFO)
            .toList();
    }
    
    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
    
    private MenuDimensions calculateMenuDimensions() {
        List<MenuContext.MenuItem> items = new ArrayList<>(currentMenu.getItems());
        
        int maxTextLength = currentMenu.getTitle().length();
        
        for (MenuContext.MenuItem item : items) {
            int itemLength = item.description.length();
            if (item.badge != null) {
                itemLength += item.badge.length() + 3;
            }
            maxTextLength = Math.max(maxTextLength, itemLength);
        }
        
        int contentWidth = maxTextLength + 8;
        int maxAllowedWidth = terminal.getCols() - 8;
        int boxWidth = Math.max(40, Math.min(contentWidth, maxAllowedWidth));
        int boxCol = (terminal.getCols() - boxWidth) / 2;
        int itemContentWidth = boxWidth - 4;
        
        return new MenuDimensions(boxWidth, boxCol, itemContentWidth);
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
    
    public void refreshMenu() {
        if (state.hasState(DISPLAYING_MENU) && currentMenu != null) {
            renderManager.invalidate();
        }
    }
    
    // ===== PASSWORD HANDLING =====
    
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
        terminal.removeKeyDownHandler(keyboardConsumer);
        terminal.removeResizeHandler(resizeConsumer);
        renderManager.clearActive();
    }
    
    // ===== GETTERS =====
    
    public BitFlagStateMachine getState() { return state; }
    public MenuContext getCurrentMenu() { return currentMenu; }
    public boolean hasMenu() { return currentMenu != null; }
    public boolean isDisplayingMenu() { return state.hasState(DISPLAYING_MENU); }
    public boolean isWaitingForPassword() { return state.hasState(WAITING_PASSWORD); }
    public TerminalContainerHandle getTerminal() { return terminal; }
    
    // ===== INNER CLASSES =====
    
    /**
     * Menu dimensions helper
     */
    private record MenuDimensions(int boxWidth, int boxCol, int itemContentWidth) {}
}