package io.netnotes.engine.core.system.control.terminal.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import io.netnotes.engine.core.system.control.terminal.RenderManager;
import io.netnotes.engine.core.system.control.terminal.RenderManager.RenderElement;
import io.netnotes.engine.core.system.control.terminal.RenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.RenderManager.Renderable;
import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.terminal.elements.TerminalTextBox;
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
 * MenuNavigator - REFACTORED for pull-based rendering
 * 
 * KEY CHANGES:
 * - Implements Renderable interface
 * - Does NOT call terminal.print/draw methods directly
 * - Instead, builds RenderState that describes what to draw
 * - RenderManager pulls this state and does the actual rendering
 * 
 * BEFORE (push):
 *   handleNavigateDown() -> renderMenu() -> terminal.print(...) [PUSH]
 * 
 * AFTER (pull):
 *   handleNavigateDown() -> invalidate() -> [later] getRenderState() -> RenderManager draws [PULL]
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
    
    // Event consumer for keyboard events
    private final Consumer<RoutedEvent> keyboardConsumer;
    
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
    
    // Resize handler
    private final Consumer<RoutedEvent> resizeConsumer;
        
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
        
        // Create keyboard event consumer
        this.keyboardConsumer = this::handleKeyboardEvent;
        
        // Create resize event consumer
        this.resizeConsumer = this::handleResizeEvent;
        
        setupStateTransitions();
        state.addState(IDLE);
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(IDLE, (old, now, bit) -> {
            // Unregister event handlers
            terminal.removeKeyDownHandler(keyboardConsumer);
            terminal.removeResizeHandler(resizeConsumer);
        });
        
        state.onStateAdded(DISPLAYING_MENU, (old, now, bit) -> {
            // Register event handlers through terminal
            terminal.addKeyDownHandler(keyboardConsumer);
            terminal.addResizeHandler(resizeConsumer);
            
            if (currentMenu != null) {
                // Make this menu the active renderable
                renderManager.setActive(this);
            }
        });
        
        state.onStateAdded(WAITING_PASSWORD, (old, now, bit) -> {
            // Unregister - password reader will handle input
            terminal.removeKeyDownHandler(keyboardConsumer);
        });
        
        state.onStateRemoved(WAITING_PASSWORD, (old, now, bit) -> {
            // Re-register when password session ends
            if (state.hasState(DISPLAYING_MENU)) {
                terminal.addKeyDownHandler(keyboardConsumer);
            }
        });
    }
    
    // ===== RENDERABLE INTERFACE IMPLEMENTATION =====
    
    /**
     * PULL: RenderManager calls this to get what to draw
     * 
     * This method should be FAST and thread-safe.
     * It builds a description of what to draw, but doesn't draw anything itself.
     */
    @Override
    public RenderState getRenderState() {
        if (currentMenu == null) {
            return RenderState.builder().build();
        }
        
        RenderState.Builder stateBuilder = RenderState.builder();
        
        // Build render elements (descriptions of what to draw)
        MenuDimensions dims = calculateMenuDimensions();
        
        // Add header element
        stateBuilder.add(createHeaderElement(dims));
        
        // Add breadcrumb element (if applicable)
        RenderElement breadcrumb = createBreadcrumbElement(dims);
        if (breadcrumb != null) {
            stateBuilder.add(breadcrumb);
        }
        
        // Add description element (if applicable)
        RenderElement description = createDescriptionElement(dims);
        if (description != null) {
            stateBuilder.add(description);
        }
        
        // Add menu items elements
        stateBuilder.addAll(createMenuItemElements(dims));
        
        // Add footer element
        stateBuilder.add(createFooterElement(dims));
        
        return stateBuilder.build();
    }
    
    /**
     * Create header render element
     */
    private RenderElement createHeaderElement(MenuDimensions dims) {
        String title = currentMenu.getTitle();
        
        return (terminal, gen) -> {
            TerminalTextBox.builder()
                .position(0, dims.getBoxCol())
                .size(dims.getBoxWidth(), 3)
                .title(title, TerminalTextBox.TitlePlacement.INSIDE_CENTER)
                .style(BoxStyle.SINGLE)
                .titleStyle(TextStyle.BOLD)
                .contentAlignment(TerminalTextBox.ContentAlignment.CENTER)
                .build()
                .render(terminal);
        };
    }
    
    /**
     * Create breadcrumb render element (or null if not needed)
     */
    private RenderElement createBreadcrumbElement(MenuDimensions dims) {
        List<String> trail = new ArrayList<>();
        MenuContext current = currentMenu;
        
        while (current != null) {
            trail.add(0, current.getTitle());
            current = current.getParent();
        }
        
        if (trail.size() <= 1) {
            return null;
        }
        
        String breadcrumb = String.join(" > ", trail);
        int textCol = dims.getBoxCol() + (dims.getBoxWidth() - breadcrumb.length()) / 2;
        
        return (terminal, gen) -> {
            terminal.printAt(3, textCol, breadcrumb, TextStyle.INFO, gen);
        };
    }
    
    /**
     * Create description render element (or null if not needed)
     */
    private RenderElement createDescriptionElement(MenuDimensions dims) {
        String description = currentMenu.getDescription();
        
        if (description == null || description.isEmpty()) {
            return null;
        }
        
        String[] lines = description.split("\n");
        int descRow = 5;
        
        return (terminal, gen) -> {
            for (int i = 0; i < lines.length && i < 8; i++) {
                String line = lines[i];
                int row = descRow + i;
                int col = dims.getBoxCol() + 4;
                terminal.printAt(row, col, line, TextStyle.NORMAL, gen);
            }
        };
    }
    
    /**
     * Create menu items render elements
     */
    private List<RenderElement> createMenuItemElements(MenuDimensions dims) {
        List<RenderElement> elements = new ArrayList<>();
        
        List<MenuContext.MenuItem> items = new ArrayList<>(currentMenu.getItems());
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        
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
        
        // Add scroll indicators
        if (scrollOffset > 0) {
            int indicatorCol = dims.getBoxCol() + dims.getBoxWidth() / 2 - 5;
            elements.add((terminal, gen) -> 
                terminal.printAt(startRow - 1, indicatorCol, "↑ More above", TextStyle.INFO, gen));
        }
        
        int moreBelowRow = startRow + (visibleEnd - visibleStart);
        if (visibleEnd < totalItems) {
            int indicatorCol = dims.getBoxCol() + dims.getBoxWidth() / 2 - 5;
            elements.add((terminal, gen) -> 
                terminal.printAt(moreBelowRow, indicatorCol, "↓ More below", TextStyle.INFO, gen));
        }
        
        return elements;
    }
    
    /**
     * Create single menu item render element
     */
    private RenderElement createMenuItemElement(
            MenuContext.MenuItem item, 
            int row, 
            boolean isSelected,
            MenuDimensions dims) {
        
        int contentStartCol = dims.getBoxCol() + 2;
        int contentWidth = dims.getItemContentWidth();
        
        return switch (item.type) {
            case SEPARATOR -> (terminal, gen) -> {
                String separatorLine = "─".repeat(contentWidth);
                terminal.printAt(row, contentStartCol, separatorLine, TextStyle.NORMAL, gen);
                
                if (item.description != null && !item.description.isEmpty()) {
                    int labelCol = contentStartCol + 
                        (contentWidth - item.description.length()) / 2;
                    terminal.printAt(row, labelCol, 
                        " " + item.description + " ", TextStyle.BOLD, gen);
                }
            };
            
            case INFO -> (terminal, gen) -> {
                int infoCol = contentStartCol + (contentWidth - item.description.length()) / 2;
                terminal.printAt(row, infoCol, item.description, TextStyle.INFO, gen);
            };
            
            default -> (terminal, gen) -> {
                String badge = item.badge != null ? " [" + item.badge + "]" : "";
                String itemText = item.description + badge;
                
                if (isSelected) {
                    menuItemRenderer.renderHighlighted(
                        terminal,
                        row,
                        contentStartCol,
                        contentWidth,
                        itemText,
                        horizontalScrollOffset
                    );
                } else {
                    String displayText = "  " + menuItemRenderer.truncateText(itemText, contentWidth - 2);
                    TextStyle style = item.enabled ? TextStyle.NORMAL : TextStyle.INFO;
                    terminal.printAt(row, contentStartCol, displayText, style, gen);
                }
            };
        };
    }
    
    /**
     * Create footer render element
     */
    private RenderElement createFooterElement(MenuDimensions dims) {
        int footerRow = terminal.getRows() - 2;
        
        String help = currentMenu.hasParent() || !navigationStack.isEmpty() 
            ? "↑↓: Navigate  ←→: Scroll Text  Enter: Select  ESC: Back  Home/End: Jump"
            : "↑↓: Navigate  ←→: Scroll Text  Enter: Select  Home/End: Jump";
        
        int helpCol = dims.getBoxCol() + (dims.getBoxWidth() - help.length()) / 2;
        
        return (terminal, gen) -> {
            terminal.drawHLine(footerRow - 1, dims.getBoxCol(), dims.getBoxWidth(), gen);
            terminal.printAt(footerRow, helpCol, help, TextStyle.INFO, gen);
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
    
    // ===== RESIZE EVENT HANDLING =====
    
    private void handleResizeEvent(RoutedEvent event) {
        if (state.hasState(DISPLAYING_MENU) && currentMenu != null) {
            horizontalScrollOffset = 0;
            renderManager.invalidate(); // Mark dirty, don't render directly
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
        
        // DON'T call renderMenu() - just invalidate
        renderManager.invalidate();
    }
    
    private void handleNavigateDown() {
        Log.logMsg("[MenuNavigator] DOWN pressed");
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty()) return;
        
        selectedIndex = (selectedIndex + 1) % selectableItems.size();
        
        if (selectedIndex >= scrollOffset + MAX_VISIBLE_ITEMS) {
            scrollOffset = selectedIndex - MAX_VISIBLE_ITEMS + 1;
        }
        
        // DON'T call renderMenu() - just invalidate
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
                // Show error - but using pull-based rendering would require
                // a separate error screen renderable
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
        String itemText = item.description + (item.badge != null ? " [" + item.badge + "]" : "");
        
        if (menuItemRenderer != null && itemText.length() > menuItemRenderer.getMaxWidth()) {
            int maxOffset = menuItemRenderer.getMaxScrollOffset(itemText);
            horizontalScrollOffset = Math.min(maxOffset, horizontalScrollOffset + 5);
            renderManager.invalidate();
        }
    }

    private void handleScrollLeft() {
        List<MenuContext.MenuItem> selectableItems = getSelectableItems();
        if (selectableItems.isEmpty() || selectedIndex >= selectableItems.size()) return;
        
        MenuContext.MenuItem item = selectableItems.get(selectedIndex);
        String itemText = item.description + (item.badge != null ? " [" + item.badge + "]" : "");
        
        if (menuItemRenderer != null && itemText.length() > menuItemRenderer.getMaxWidth()) {
            horizontalScrollOffset = Math.max(0, horizontalScrollOffset - 5);
            renderManager.invalidate();
        }
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
    
    public void refreshMenu() {
        if (state.hasState(DISPLAYING_MENU) && currentMenu != null) {
            renderManager.invalidate();
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
        terminal.removeKeyDownHandler(keyboardConsumer);
        terminal.removeResizeHandler(resizeConsumer);
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
        
        this.menuItemRenderer = new ScrollableMenuItem(itemContentWidth, 
            ScrollableMenuItem.TextOverflowStrategy.SCROLL_ON_SELECT);
        
        return new MenuDimensions(boxWidth, boxCol, itemContentWidth);
    }
    
    // ===== GETTERS =====
    
    public BitFlagStateMachine getState() { return state; }
    public MenuContext getCurrentMenu() { return currentMenu; }
    public boolean hasMenu() { return currentMenu != null; }
    public boolean isDisplayingMenu() { return state.hasState(DISPLAYING_MENU); }
    public boolean isWaitingForPassword() { return state.hasState(WAITING_PASSWORD); }
    public TerminalContainerHandle getTerminal() { return terminal; }
}