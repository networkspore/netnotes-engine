package io.netnotes.engine.core.system.control.containers;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.TerminalCommands;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.terminal.elements.TerminalTextBox;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * TerminalContainerHandle - Terminal-style container operations
 * 
 * Extends ContainerHandle with terminal/console-specific operations.
 * 
 * CRITICAL: All terminal operations now wait for stream to be ready first!
 * 
 * Usage:
 * <pre>
 * TerminalContainerHandle terminal = new TerminalContainerHandle(containerId, "my-terminal");
 * registry.registerChild(ownerPath, terminal);
 * registry.startProcess(terminal.getContextPath());
 * 
 * // Wait for ready, then use
 * terminal.waitUntilReady().thenCompose(v -> {
 *     return terminal.clear()
 *         .thenCompose(v2 -> terminal.printAt(5, 10, "=== Main Menu ===", TextStyle.BOLD))
 *         .thenCompose(v2 -> terminal.printAt(7, 10, "1. Files"))
 *         .thenCompose(v2 -> terminal.printAt(8, 10, "2. Settings"));
 * });
 * </pre>
 */
public class TerminalContainerHandle extends ContainerHandle {
    
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

    public TerminalContainerHandle(String name){
        super(TerminalContainerHandle.builder(name));
    }


    public static TerminalBuilder builder(String name) {
        return new TerminalBuilder(name);
    }
    


    public static class TerminalBuilder extends ContainerHandle.Builder {
        
        public TerminalBuilder(String name) {
            // Force terminal type
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
        
        /**
         * Terminal-specific: set cols and rows
         */
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
     * Row/col are 0-based
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
     * Move cursor to position (without printing)
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
     * Show cursor (make visible)
     */
    public CompletableFuture<Void> showCursor() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_SHOW_CURSOR);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command);
    }
    
    /**
     * Hide cursor (useful during menu rendering)
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
     * Draw a box at specified position
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
     * This is the primitive - keeps existing behavior if title is null/empty
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
     * Create a TextBox builder positioned on this terminal
     * Convenience method for complex text box scenarios
     */
    public TerminalTextBox.Builder textBox() {
        return TerminalTextBox.builder();
    }

    /**
     * Quick text box - most common case
     * Box with title inside, centered
     */
    public CompletableFuture<Void> drawTextBox(
        int row, int col,
        int width, int height,
        String title
    ) {
        return TerminalTextBox.builder()
            .position(row, col)
            .size(width, height)
            .title(title, TerminalTextBox.TitlePlacement.INSIDE_TOP)
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
        return textBox
            .render(this);
    }
        
    /**
     * Print a horizontal line (separator)
     */
    public CompletableFuture<Void> drawHLine(int row, int startCol, int length) {
        return waitUntilReady().thenCompose(v -> {
            NoteBytesMap command = new NoteBytesMap();
            command.put(Keys.CMD, TerminalCommands.TERMINAL_DRAW_HLINE);
            command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
            command.put(Keys.ROW, row);
            command.put(TerminalCommands.START_COL, startCol);
            command.put(Keys.LENGTH, length);
            
            return sendRenderCommand(command);
        });
    }
    
    /**
     * Set terminal dimensions (if terminal reports size change)
     */
    public void setDimensions(int rows, int cols) {
        this.height = rows;
        this.width = cols;
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
     * Print a status/footer line at bottom of terminal
     */
    public CompletableFuture<Void> printStatusLine(String text) {
        return printAt(getRows() - 1, 0, text, TextStyle.INVERSE);
    }
    
    /**
     * Print a title/header at top
     */
    public CompletableFuture<Void> printTitle(String text) {
        return printAt(0, (getCols() - text.length()) / 2, text, TextStyle.BOLD);
    }
    
    /**
     * Print error message in red
     */
    public CompletableFuture<Void> printError(String text) {
        return println(text, TextStyle.ERROR);
    }
    
    /**
     * Print success message in green
     */
    public CompletableFuture<Void> printSuccess(String text) {
        return println(text, TextStyle.SUCCESS);
    }
    
    /**
     * Print warning in yellow
     */
    public CompletableFuture<Void> printWarning(String text) {
        return println(text, TextStyle.WARNING);
    }

    @Override
    protected void handleContainerResized(int changedWidth, int changedHeight) {
        this.width = changedWidth;
        this.height = changedHeight;
        super.handleContainerResized(changedWidth, changedHeight);
    }
    
    // ===== BATCH OPERATIONS =====
    
    /**
     * Begin a batch of terminal operations
     * UI can buffer these and render all at once (reduces flicker)
     */
    public CompletableFuture<Void> beginBatch() {
        return waitUntilReady().thenCompose(v -> {
            NoteBytesMap command = new NoteBytesMap();
            command.put(Keys.CMD, TerminalCommands.TERMINAL_BEGIN_BATCH);
            command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
            
            return sendRenderCommand(command);
        });
    }
    
    /**
     * End batch and render all buffered operations
     */
    public CompletableFuture<Void> endBatch() {
        return waitUntilReady().thenCompose(v -> {
            NoteBytesMap command = new NoteBytesMap();
            command.put(Keys.CMD, TerminalCommands.TERMINAL_END_BATCH);
            command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
            
            return sendRenderCommand(command);
        });
    }
    
   
}