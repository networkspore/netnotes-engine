package io.netnotes.engine.core.system.control.containers;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * TerminalContainerHandle - Terminal-style container operations
 * 
 * Extends ContainerHandle with terminal/console-specific operations.
 * 
 * NOW SIMPLIFIED:
 * - No message wrapping (sendTerminalCommand writes directly to stream!)
 * - Clean separation: lifecycle → service, rendering → stream
 * - Matches IODaemon/ClaimedDevice pattern
 * 
 * Usage:
 * <pre>
 * TerminalContainerHandle terminal = new TerminalContainerHandle(containerId, "my-terminal");
 * registry.registerChild(ownerPath, terminal);
 * registry.startProcess(terminal.getContextPath());
 * 
 * // Clear screen - goes directly through stream!
 * terminal.clear();
 * 
 * // Print menu at position
 * terminal.printAt(5, 10, "=== Main Menu ===", TextStyle.BOLD);
 * terminal.printAt(7, 10, "1. Files");
 * terminal.printAt(8, 10, "2. Settings");
 * </pre>
 */
public class TerminalContainerHandle extends ContainerHandle {
    
    // Terminal dimensions (if known)
    private volatile int rows = 24;
    private volatile int cols = 80;
    
    /**
     * Constructor - creates terminal-style container
     */
    public TerminalContainerHandle(ContainerId containerId, String name) {
        super(containerId, name);
    }
    
    public TerminalContainerHandle(
        ContainerId containerId, 
        String name,
        ContextPath containerServicePath
    ) {
        super(containerId, name, containerServicePath);
    }
    
    // ===== TERMINAL-SPECIFIC OPERATIONS =====
    
    /**
     * Clear the entire terminal screen
     */
    public CompletableFuture<Void> clear() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        
        return sendRenderCommand(command);  // Direct to stream!
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
        command.put(Keys.CMD,TerminalCommands.TERMINAL_PRINT_AT);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.ROW, row);
        command.put(Keys.COL, col);
        command.put(Keys.TEXT, text);
        command.put(Keys.STYLE, style.toNoteBytes());
        
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
        
        return sendRenderCommand(command);
    }
    
    /**
     * Show cursor (make visible)
     */
    public CompletableFuture<Void> showCursor() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_SHOW_CURSOR);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        
        return sendRenderCommand(command);
    }
    
    /**
     * Hide cursor (useful during menu rendering)
     */
    public CompletableFuture<Void> hideCursor() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_HIDE_CURSOR);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        
        return sendRenderCommand(command);
    }
    
    /**
     * Clear from cursor to end of line
     */
    public CompletableFuture<Void> clearLine() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_LINE);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        
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
        
        return sendRenderCommand(command);
    }
    
    /**
     * Print a horizontal line (separator)
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
     * Set terminal dimensions (if terminal reports size change)
     */
    public void setDimensions(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
    }
    
    /**
     * Get terminal rows
     */
    public int getRows() {
        return rows;
    }
    
    /**
     * Get terminal columns
     */
    public int getCols() {
        return cols;
    }
    
    // ===== HIGH-LEVEL HELPERS =====
    
    /**
     * Print a status/footer line at bottom of terminal
     */
    public CompletableFuture<Void> printStatusLine(String text) {
        return printAt(rows - 1, 0, text, TextStyle.INVERSE);
    }
    
    /**
     * Print a title/header at top
     */
    public CompletableFuture<Void> printTitle(String text) {
        return printAt(0, (cols - text.length()) / 2, text, TextStyle.BOLD);
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
    
    // ===== BATCH OPERATIONS =====
    
    /**
     * Begin a batch of terminal operations
     * UI can buffer these and render all at once (reduces flicker)
     */
    public CompletableFuture<Void> beginBatch() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_BEGIN_BATCH);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        
        return sendRenderCommand(command);
    }
    
    /**
     * End batch and render all buffered operations
     */
    public CompletableFuture<Void> endBatch() {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_END_BATCH);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        
        return sendRenderCommand(command);
    }
    
    // ===== TEXT STYLING =====
    
    /**
     * Text style options for terminal rendering
     */
    public static class TextStyle {
        public static final TextStyle NORMAL = new TextStyle();
        public static final TextStyle BOLD = new TextStyle().bold();
        public static final TextStyle INVERSE = new TextStyle().inverse();
        public static final TextStyle UNDERLINE = new TextStyle().underline();
        
        // Semantic colors
        public static final TextStyle ERROR = new TextStyle().color(Color.RED).bold();
        public static final TextStyle SUCCESS = new TextStyle().color(Color.GREEN);
        public static final TextStyle WARNING = new TextStyle().color(Color.YELLOW);
        public static final TextStyle INFO = new TextStyle().color(Color.CYAN);
        
        private Color foreground = Color.DEFAULT;
        private Color background = Color.DEFAULT;
        private boolean bold = false;
        private boolean inverse = false;
        private boolean underline = false;
        
        public TextStyle() {}
        
        public TextStyle color(Color fg) {
            this.foreground = fg;
            return this;
        }
        
        public TextStyle bgColor(Color bg) {
            this.background = bg;
            return this;
        }
        
        public TextStyle bold() {
            this.bold = true;
            return this;
        }
        
        public TextStyle inverse() {
            this.inverse = true;
            return this;
        }
        
        public TextStyle underline() {
            this.underline = true;
            return this;
        }
        
        public NoteBytesMap toNoteBytes() {
            NoteBytesMap map = new NoteBytesMap();
            map.put(Keys.FOREGROUND, foreground.name());
            map.put(Keys.BACKGROUND, background.name());
            map.put(Keys.BOLD, bold);
            map.put(Keys.INVERSE, inverse);
            map.put(Keys.UNDERLINE, underline);
            return map;
        }
    }
    
    /**
     * Terminal colors (standard 16-color palette)
     */
    public enum Color {
        DEFAULT,
        BLACK, RED, GREEN, YELLOW,
        BLUE, MAGENTA, CYAN, WHITE,
        BRIGHT_BLACK, BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW,
        BRIGHT_BLUE, BRIGHT_MAGENTA, BRIGHT_CYAN, BRIGHT_WHITE
    }
    
    /**
     * Box drawing styles
     */
    public enum BoxStyle {
        SINGLE,   // ─ │ ┌ ┐ └ ┘
        DOUBLE,   // ═ ║ ╔ ╗ ╚ ╝
        ROUNDED,  // ─ │ ╭ ╮ ╰ ╯
        THICK     // ━ ┃ ┏ ┓ ┗ ┛
    }
}