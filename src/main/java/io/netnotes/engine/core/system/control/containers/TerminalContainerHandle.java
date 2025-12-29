package io.netnotes.engine.core.system.control.containers;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.netnotes.engine.core.system.control.terminal.TerminalCommands;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.terminal.elements.TerminalTextBox;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.input.events.EventHandlerRegistry.RoutedEventHandler;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * TerminalContainerHandle - Terminal-style container with keyboard utilities
 */
public class TerminalContainerHandle extends ContainerHandle {
    
    // Temporary key wait state
    private volatile boolean isWaitingForKey = false;
    private CompletableFuture<RoutedEvent> keyWaitFuture = null;
    private CompletableFuture<Void> anyKeyFuture = null;
    private NoteBytesReadOnly handlerId = null;
    private List<RoutedEventHandler> savedKeyHandlers = null;
    
    /**
     * Constructor - creates terminal-style container
     */
    protected TerminalContainerHandle(TerminalBuilder builder) {
        super(builder);
        
        // Set initial size from config if provided
        ContainerConfig config = builder.containerConfig;
        if (config.getWidth() != null) {
            this.setDimensions(config.getWidth(), config.getHeight()); 
        }
    }
    
    public TerminalContainerHandle(String name) {
        super(TerminalContainerHandle.builder(name));
    }
    
    public static TerminalBuilder builder(String name) {
        return new TerminalBuilder(name);
    }
    
    // ===== BUILDER =====
    
    public static class TerminalBuilder extends ContainerHandle.Builder {
        
        public TerminalBuilder(String name) {
            super(name, ContainerType.TERMINAL);
        }
        
        @Override
        public TerminalBuilder name(String name) {
            super.name(name);
            return this;
        }
        
        @Override
        public TerminalBuilder type(ContainerType type) {
            return this;
        }
        
        @Override
        public TerminalBuilder title(String title) {
            super.title(title);
            return this;
        }
        
        @Override
        public TerminalBuilder config(ContainerConfig config) {
            super.config(config);
            return this;
        }
        
        @Override
        public TerminalBuilder renderingService(ContextPath path) {
            super.renderingService(path);
            return this;
        }
        
        @Override
        public TerminalBuilder autoFocus(boolean autoFocus) {
            super.autoFocus(autoFocus);
            return this;
        }
        
        public TerminalBuilder size(int cols, int rows) {
            super.config(new ContainerConfig().withSize(cols, rows));
            return this;
        }
        
        @Override
        public TerminalContainerHandle build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("name is required");
            }
            return new TerminalContainerHandle(this);
        }
    }
    
    // ===== KEYBOARD WAITING UTILITY =====
    
    /**
     * Wait for any key press, then execute action
     * 
     * This temporarily removes ALL keyboard event handlers,
     * waits for a single key press, then restores handlers
     * and executes the provided action.
     * 
     * Usage:
     * <pre>
     * terminal.println("Press any key to continue...")
     *     .thenCompose(v -> terminal.waitForKeyPress())
     *     .thenRun(() -> showNextScreen());
     * </pre>
     * 
     * @return CompletableFuture<KeyDownEvent> that completes when key pressed
     */
    public CompletableFuture<Void> waitForKeyPress() {
        if (isWaitingForKey) {
            // Already waiting, return existing future
            return anyKeyFuture;
        }
        
        isWaitingForKey = true;
        anyKeyFuture = new CompletableFuture<>();
        
        // Save current keyboard handlers
        savedKeyHandlers = new ArrayList<>(clearKeyDownHandlers());
        
       
        
        // Create temporary handler that captures ANY key press
        Consumer<RoutedEvent> consumer = event -> {
            if (event instanceof KeyDownEvent) {
                handleKeyWaitComplete();
            }else if( event instanceof EphemeralKeyDownEvent){
                handleKeyWaitComplete();
            }
        };
        
        // Register temporary handler
        handlerId = addKeyDownHandler(consumer);
        
        return anyKeyFuture;
    }
    
    /**
     * Wait for key press, then execute action
     * 
     * Convenience method that chains the action after key press.
     * 
     * @param action Action to execute after key press
     * @return CompletableFuture that completes when action done
     */
    public CompletableFuture<Void> waitForKeyPress(Runnable action) {
        return waitForKeyPress()
            .thenRun(action);
    }
    
  
    
    /**
     * Wait for SPECIFIC key press
     * 
     * Only completes when the specified key is pressed.
     * Other keys are ignored.
     * 
     * @param keyCode Keyboard.KeyCodeBytes
     * @return CompletableFuture<KeyDownEvent> that completes when key pressed
     */
    public CompletableFuture<RoutedEvent> waitForKey(NoteBytes keyCodeBytes) {
        if (isWaitingForKey) {
            // Already waiting, return existing future
            return keyWaitFuture;
        }
        
        isWaitingForKey = true;
        keyWaitFuture = new CompletableFuture<>();
        
        // Save current keyboard handlers
        savedKeyHandlers = new ArrayList<>(clearKeyDownHandlers());
        
        // Create temporary handler that captures key presses
        Consumer<RoutedEvent> consumer = event -> {
            if (event instanceof KeyDownEvent keyDown) {
                if(keyDown.getKeyCodeBytes().equals(keyCodeBytes)){
                    handleKeyWaitComplete(event);
                }
            }else if(event instanceof EphemeralKeyDownEvent keyDown){
                if(keyDown.getKeyCodeBytes().equals(keyCodeBytes)){
                    handleKeyWaitComplete(event);
                }
            }
        };
        
        // Register temporary handler
        handlerId = addKeyDownHandler(consumer);
        
        return keyWaitFuture;
    }
    
    /**
     * Wait for Enter key
     * 
     * Common case - wait for Enter.
     */
    public CompletableFuture<Void> waitForEnter() {
        return waitForKey(KeyCodeBytes.ENTER)
            .thenAccept(k -> {
                if(k instanceof EphemeralRoutedEvent ephemeralRoutedEvent){
                    ephemeralRoutedEvent.close();
                }
            }); // Convert to Void
    }
    
    /**
     * Wait for Escape key
     */
    public CompletableFuture<Void> waitForEscape() {
        return waitForKey(KeyCodeBytes.ESCAPE)
            .thenAccept(k -> {
                 if(k instanceof EphemeralRoutedEvent ephemeralRoutedEvent){
                    ephemeralRoutedEvent.close();
                }
            });
    }
    
    /**
     * Handle key wait completion
     */
    private void handleKeyWaitComplete(RoutedEvent event) {
        if (!isWaitingForKey) {
            return;
        }
        
        // Remove temporary handler
        if (handlerId != null) {
            removeKeyDownHandler(handlerId);
            handlerId = null;
        }
        
        // Restore saved handlers
        if (savedKeyHandlers != null) {
            for (RoutedEventHandler handler : savedKeyHandlers) {
                addKeyDownHandler(handler);
            }
            savedKeyHandlers = null;
        }
        
        // Complete the future
        if (keyWaitFuture != null && !keyWaitFuture.isDone()) {
            keyWaitFuture.complete(event);
        }
        
        isWaitingForKey = false;
        keyWaitFuture = null;
    }

    private void handleKeyWaitComplete() {
        if (!isWaitingForKey) {
            return;
        }
        
        // Remove temporary handler
        if (handlerId != null) {
            removeKeyDownHandler(handlerId);
            handlerId = null;
        }
        
        // Restore saved handlers
        if (savedKeyHandlers != null) {
            for (RoutedEventHandler handler : savedKeyHandlers) {
                addKeyDownHandler(handler);
            }
            savedKeyHandlers = null;
        }
        
        // Complete the future
        if (anyKeyFuture != null && !anyKeyFuture.isDone()) {
            anyKeyFuture.complete(null);
        }
        
        isWaitingForKey = false;
        anyKeyFuture = null;
    }
    
    /**
     * Cancel key wait (if needed)
     */
    public void cancelKeyWait() {
        if (!isWaitingForKey) {
            return;
        }
        
        // Remove temporary handler
        if (handlerId != null) {
            removeKeyDownHandler(handlerId);
            handlerId = null;
        }
        
        // Restore saved handlers
        if (savedKeyHandlers != null) {
            for (RoutedEventHandler handler : savedKeyHandlers) {
                addKeyDownHandler(handler);
            }
            savedKeyHandlers = null;
        }
        
        // Cancel the future
        if (keyWaitFuture != null && !keyWaitFuture.isDone()) {
            keyWaitFuture.cancel(false);
        }
        
        isWaitingForKey = false;
        keyWaitFuture = null;
    }
    
    /**
     * Check if currently waiting for key press
     */
    public boolean isWaitingForKeyPress() {
        return isWaitingForKey;
    }
    
    // ===== TERMINAL COMMANDS =====
    
    /**
     * Clear the entire terminal screen
     */
    public CompletableFuture<Void> clear() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command);
    }
    
    /**
     * Print text at current cursor position
     */
    public CompletableFuture<Void> print(String text) {
        return print(text, TextStyle.NORMAL);
    }
    
    /**
     * Print styled text at current cursor position
     */
    public CompletableFuture<Void> print(String text, TextStyle style) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_PRINT);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.TEXT, text);
        command.put(Keys.STYLE, style.toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command);
    }
    
    /**
     * Print text + newline
     */
    public CompletableFuture<Void> println(String text) {
        return println(text, TextStyle.NORMAL);
    }
    
    /**
     * Print styled text + newline
     */
    public CompletableFuture<Void> println(String text, TextStyle style) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_PRINTLN);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.TEXT, text);
        command.put(Keys.STYLE, style.toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command);
    }
    
    /**
     * Print text at specific screen position
     */
    public CompletableFuture<Void> printAt(int row, int col, String text) {
        return printAt(row, col, text, TextStyle.NORMAL);
    }
    
    /**
     * Print styled text at specific position
     */
    public CompletableFuture<Void> printAt(int row, int col, String text, TextStyle style) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_PRINT_AT);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.ROW, row);
        command.put(Keys.COL, col);
        command.put(Keys.TEXT, text);
        command.put(Keys.STYLE, style.toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command);
    }
    
    /**
     * Move cursor to position
     */
    public CompletableFuture<Void> moveCursor(int row, int col) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_MOVE_CURSOR);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.ROW, row);
        command.put(Keys.COL, col);
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command);
    }
    
    /**
     * Show cursor
     */
    public CompletableFuture<Void> showCursor() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_SHOW_CURSOR);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command);
    }
    
    /**
     * Hide cursor
     */
    public CompletableFuture<Void> hideCursor() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_HIDE_CURSOR);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command);
    }
    
    /**
     * Clear from cursor to end of line
     */
    public CompletableFuture<Void> clearLine() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_LINE);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command);
    }
    
    /**
     * Clear specific line
     */
    public CompletableFuture<Void> clearLine(int row) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_LINE_AT);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.ROW, row);
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command);
    }
    
    /**
     * Clear rectangular region
     */
    public CompletableFuture<Void> clearRegion(int startRow, int startCol, int endRow, int endCol) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_REGION);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(TerminalCommands.START_ROW, startRow);
        command.put(TerminalCommands.START_COL, startCol);
        command.put(TerminalCommands.END_ROW, endRow);
        command.put(TerminalCommands.END_COL, endCol);
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command);
    }
    
    /**
     * Draw a box
     */
    public CompletableFuture<Void> drawBox(
        int startRow, int startCol, 
        int width, int height,
        String title
    ) {
        return drawBox(startRow, startCol, width, height, title, BoxStyle.SINGLE);
    }
    
    /**
     * Draw a box (border only, no title)
     */
    public CompletableFuture<Void> drawBox(
        int startRow, int startCol, 
        int width, int height,
        BoxStyle boxStyle
    ) {
        return drawBox(startRow, startCol, width, height, null, boxStyle);
    }
    
    /**
     * Draw styled box
     */
    public CompletableFuture<Void> drawBox(
        int startRow, int startCol, 
        int width, int height,
        String title,
        BoxStyle boxStyle
    ) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_DRAW_BOX);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(TerminalCommands.START_ROW, startRow);
        command.put(TerminalCommands.START_COL, startCol);
        command.put(Keys.WIDTH, width);
        command.put(Keys.HEIGHT, height);
        command.put(Keys.TITLE, title);
        command.put(TerminalCommands.BOX_STYLE, boxStyle.name());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command);
    }
    
    /**
     * Create TextBox builder
     */
    public TerminalTextBox.Builder textBox() {
        return TerminalTextBox.builder();
    }
    
    /**
     * Quick text box with title
     */
    public CompletableFuture<Void> drawTextBox(
        int row, int col,
        int width, int height,
        String title
    ) {
        return TerminalTextBox.builder()
            .position(row, col)
            .size(width, height)
            .title(title, TerminalTextBox.TitlePlacement.INSIDE_CENTER)
            .style(BoxStyle.SINGLE)
            .build()
            .render(this);
    }
    
    /**
     * Quick text box with content
     */
    public CompletableFuture<Void> drawTextBox(
        int row, int col,
        int width, int height,
        String title,
        String... content
    ) {
        return TerminalTextBox.builder()
            .position(row, col)
            .size(width, height)
            .title(title, TerminalTextBox.TitlePlacement.INSIDE_TOP)
            .content(content)
            .style(BoxStyle.SINGLE)
            .build()
            .render(this);
    }
    
    public CompletableFuture<Void> drawTextBox(TerminalTextBox textBox) {
        return textBox.render(this);
    }
    
    /**
     * Draw horizontal line
     */
    public CompletableFuture<Void> drawHLine(int row, int startCol, int length) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_DRAW_HLINE);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.ROW, row);
        command.put(TerminalCommands.START_COL, startCol);
        command.put(Keys.LENGTH, length);
        return sendRenderCommand(command);
    }

    
    /**
     * Get terminal rows
     */
    public int getRows() {
        return height;
    }
    
    /**
     * Get terminal columns
     */
    public int getCols() {
        return width;
    }
    
    // ===== HIGH-LEVEL HELPERS =====
    
    /**
     * Print status line at bottom
     */
    public CompletableFuture<Void> printStatusLine(String text) {
        return printAt(getRows() - 1, 0, text, TextStyle.INVERSE);
    }
    
    /**
     * Print title at top
     */
    public CompletableFuture<Void> printTitle(String text) {
        return printAt(0, (getCols() - text.length()) / 2, text, TextStyle.BOLD);
    }
    
    /**
     * Print error message
     */
    public CompletableFuture<Void> printError(String text) {
        return println(text, TextStyle.ERROR);
    }
    
    /**
     * Print success message
     */
    public CompletableFuture<Void> printSuccess(String text) {
        return println(text, TextStyle.SUCCESS);
    }
    
    /**
     * Print warning
     */
    public CompletableFuture<Void> printWarning(String text) {
        return println(text, TextStyle.WARNING);
    }
    

    
    // ===== BATCH OPERATIONS =====
    
    /**
     * Begin batch of terminal operations
     */
    public CompletableFuture<Void> beginBatch() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_BEGIN_BATCH);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        return sendRenderCommand(command);
    }
    
    /**
     * End batch and render
     */
    public CompletableFuture<Void> endBatch() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_END_BATCH);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        return sendRenderCommand(command);
    }
}