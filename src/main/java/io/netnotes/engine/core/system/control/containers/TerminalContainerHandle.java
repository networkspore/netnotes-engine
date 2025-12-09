package io.netnotes.engine.core.system.control.containers;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * TerminalContainerHandle - Terminal-style container operations
 * 
 * Extends ContainerHandle with terminal/console-specific operations:
 * - Character-based rendering (not pixel-based)
 * - Cursor positioning
 * - Text styling (colors, bold, etc.)
 * - Line-based and positioned text output
 * - Menu rendering support
 * 
 * This is NOT limited to line-by-line output - terminals support:
 * - Positioned text (print at row, col)
 * - Full-screen redraws
 * - Color and styling
 * - Box drawing characters
 * 
 * UI Implementation Examples:
 * - Desktop: Terminal emulator widget
 * - Web: Terminal.js / xterm.js component
 * - Console: ANSI escape codes
 * - Mobile: Terminal view with virtual keyboard
 * 
 * Usage:
 * <pre>
 * TerminalContainerHandle terminal = new TerminalContainerHandle(containerId);
 * registry.registerChild(ownerPath, terminal);
 * registry.startProcess(terminal.getContextPath());
 * 
 * // Clear screen
 * terminal.clear();
 * 
 * // Print menu at position
 * terminal.printAt(5, 10, "=== Main Menu ===", TextStyle.BOLD);
 * terminal.printAt(7, 10, "1. Files");
 * terminal.printAt(8, 10, "2. Settings");
 * 
 * // Update status line
 * terminal.printStatusLine("Ready");
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
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_clear");
        msg.put("container_id", getId().toNoteBytes());
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * Print text at current cursor position
     * Appends text and advances cursor (like System.out.print)
     */
    public CompletableFuture<Void> print(String text) {
        return print(text, TextStyle.NORMAL);
    }
    
    /**
     * Print styled text at current cursor position
     */
    public CompletableFuture<Void> print(String text, TextStyle style) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_print");
        msg.put("container_id", getId().toNoteBytes());
        msg.put("text", text);
        msg.put("style", style.toNoteBytes());
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * Print text at current cursor position + newline
     * Like System.out.println
     */
    public CompletableFuture<Void> println(String text) {
        return println(text, TextStyle.NORMAL);
    }
    
    /**
     * Print styled text + newline
     */
    public CompletableFuture<Void> println(String text, TextStyle style) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_println");
        msg.put("container_id", getId().toNoteBytes());
        msg.put("text", text);
        msg.put("style", style.toNoteBytes());
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * Print text at specific screen position
     * Row/col are 0-based
     * 
     * This is KEY for menu rendering - you can position text anywhere!
     */
    public CompletableFuture<Void> printAt(int row, int col, String text) {
        return printAt(row, col, text, TextStyle.NORMAL);
    }
    
    /**
     * Print styled text at specific position
     */
    public CompletableFuture<Void> printAt(int row, int col, String text, TextStyle style) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_print_at");
        msg.put("container_id", getId().toNoteBytes());
        msg.put("row", row);
        msg.put("col", col);
        msg.put("text", text);
        msg.put("style", style.toNoteBytes());
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * Move cursor to position (without printing)
     */
    public CompletableFuture<Void> moveCursor(int row, int col) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_move_cursor");
        msg.put("container_id", getId().toNoteBytes());
        msg.put("row", row);
        msg.put("col", col);
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * Show cursor (make visible)
     */
    public CompletableFuture<Void> showCursor() {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_show_cursor");
        msg.put("container_id", getId().toNoteBytes());
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * Hide cursor (useful during menu rendering)
     */
    public CompletableFuture<Void> hideCursor() {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_hide_cursor");
        msg.put("container_id", getId().toNoteBytes());
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * Clear from cursor to end of line
     */
    public CompletableFuture<Void> clearLine() {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_clear_line");
        msg.put("container_id", getId().toNoteBytes());
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * Clear specific line
     */
    public CompletableFuture<Void> clearLine(int row) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_clear_line_at");
        msg.put("container_id", getId().toNoteBytes());
        msg.put("row", row);
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * Clear rectangular region
     * Useful for clearing menu areas without full screen clear
     */
    public CompletableFuture<Void> clearRegion(int startRow, int startCol, int endRow, int endCol) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_clear_region");
        msg.put("container_id", getId().toNoteBytes());
        msg.put("start_row", startRow);
        msg.put("start_col", startCol);
        msg.put("end_row", endRow);
        msg.put("end_col", endCol);
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * Draw a box at specified position
     * Uses box-drawing characters (Unicode)
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
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_draw_box");
        msg.put("container_id", getId().toNoteBytes());
        msg.put("start_row", startRow);
        msg.put("start_col", startCol);
        msg.put("width", width);
        msg.put("height", height);
        msg.put("title", title);
        msg.put("box_style", boxStyle.name());
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * Print a horizontal line (separator)
     */
    public CompletableFuture<Void> drawHLine(int row, int startCol, int length) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_draw_hline");
        msg.put("container_id", getId().toNoteBytes());
        msg.put("row", row);
        msg.put("start_col", startCol);
        msg.put("length", length);
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * Set terminal dimensions (if terminal reports size change)
     * ContainerService can call this on resize events
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
     * 
     * Example:
     * terminal.beginBatch()
     *     .thenCompose(v -> terminal.clear())
     *     .thenCompose(v -> terminal.printTitle("Menu"))
     *     .thenCompose(v -> terminal.printAt(5, 10, "Option 1"))
     *     .thenCompose(v -> terminal.printAt(6, 10, "Option 2"))
     *     .thenCompose(v -> terminal.endBatch());
     */
    public CompletableFuture<Void> beginBatch() {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_begin_batch");
        msg.put("container_id", getId().toNoteBytes());
        
        return sendTerminalCommand(msg);
    }
    
    /**
     * End batch and render all buffered operations
     */
    public CompletableFuture<Void> endBatch() {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put("cmd", "terminal_end_batch");
        msg.put("container_id", getId().toNoteBytes());
        
        return sendTerminalCommand(msg);
    }
    
    // ===== INTERNAL =====
    
    /**
     * Send terminal command through ContainerService
     * Uses standard updateContainer mechanism
     */
    private CompletableFuture<Void> sendTerminalCommand(NoteBytesMap command) {
        // Wrap in update command
        NoteBytesMap updates = new NoteBytesMap();
        updates.put("terminal_command", command);
        
        return updateContainer(updates);
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
            map.put("fg", foreground.name());
            map.put("bg", background.name());
            map.put("bold", bold);
            map.put("inverse", inverse);
            map.put("underline", underline);
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