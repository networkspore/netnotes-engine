package io.netnotes.engine.core.system.control.terminal;

import io.netnotes.engine.core.system.control.containers.ContainerId;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.ui.BatchBuilder;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * BatchBuilder - Build atomic batches of terminal commands
 * 
 * BENEFITS:
 * - Single command sent over stream (atomic)
 * - Single future to wait on (simpler error handling)
 * - No begin/end batch state management
 * - No risk of "stuck in batch" state
 * - Commands execute serially in container executor
 * 
 * Usage:
 * <pre>
 * BatchBuilder batch = terminal.batch()
 *     .clear()
 *     .printAt(0, 0, "Header", TextStyle.BOLD)
 *     .drawBox(2, 0, 40, 10, "Content", BoxStyle.SINGLE)
 *     .println("Status: Ready");
 * 
 * terminal.executeBatch(batch).thenRun(() -> {
 *     System.out.println("Batch complete!");
 * });
 * </pre>
 */
public class TerminalBatchBuilder extends BatchBuilder {
    
    
    public TerminalBatchBuilder(ContainerId containerId, NoteBytes rendererId, long generation) {
        super(containerId, rendererId, generation);
    }
    
 
    
    /**
     * Add raw command to batch
     */
    @Override
    public TerminalBatchBuilder addCommand(NoteBytesMap cmd) {
        commands.add(cmd.toNoteBytes());
        return this;
    }
    
    /**
     * Add raw command bytes to batch
     */
    @Override
    public TerminalBatchBuilder addCommand(NoteBytes cmd) {
        commands.add(cmd);
        return this;
    }
    
    
    // ===== CONVENIENCE METHODS =====
    
    /**
     * Clear screen
     */
    public TerminalBatchBuilder clear() {
        NoteBytesMap cmd = TerminalCommands.clear();
        return addCommand(cmd);
    }
    
    /**
     * Print text
     */
    public TerminalBatchBuilder print(String text) {
        return print(text, TextStyle.NORMAL);
    }
    
    /**
     * Print styled text
     */
    public TerminalBatchBuilder print(String text, TextStyle style) {
        NoteBytesMap cmd = TerminalCommands.print(text, style);
        return addCommand(cmd);
    }
    
    /**
     * Print line
     */
    public TerminalBatchBuilder println(String text) {
        return println(text, TextStyle.NORMAL);
    }
    
    /**
     * Print styled line
     */
    public TerminalBatchBuilder println(String text, TextStyle style) {
        NoteBytesMap cmd = TerminalCommands.println(text, style);
        return addCommand(cmd);
    }
    
    /**
     * Print at position
     */
    public TerminalBatchBuilder printAt(int row, int col, String text) {
        return printAt(row, col, text, TextStyle.NORMAL);
    }
    
    /**
     * Print styled text at position
     */
    public TerminalBatchBuilder printAt(int row, int col, String text, TextStyle style) {
        NoteBytesMap cmd = TerminalCommands.printAt(row, col, text, style);
        return addCommand(cmd);
    }
    
    /**
     * Move cursor
     */
    public TerminalBatchBuilder moveCursor(int row, int col) {
        NoteBytesMap cmd = TerminalCommands.moveCursor(row, col);
        return addCommand(cmd);
    }
    
    /**
     * Show cursor
     */
    public TerminalBatchBuilder showCursor() {
        NoteBytesMap cmd = TerminalCommands.showCursor();
        return addCommand(cmd);
    }
    
    /**
     * Hide cursor
     */
    public TerminalBatchBuilder hideCursor() {
        NoteBytesMap cmd = TerminalCommands.hideCursor();
        return addCommand(cmd);
    }
    
    /**
     * Clear line at cursor
     */
    public TerminalBatchBuilder clearLine() {
        NoteBytesMap cmd = TerminalCommands.clearLine();
        return addCommand(cmd);
    }
    
    /**
     * Clear specific line
     */
    public TerminalBatchBuilder clearLineAt(int row) {
        NoteBytesMap cmd = TerminalCommands.clearLineAt(row);
        return addCommand(cmd);
    }
    
    /**
     * Clear region
     */
    public TerminalBatchBuilder clearRegion(int startRow, int startCol, int endRow, int endCol) {
        NoteBytesMap cmd = TerminalCommands.clearRegion(startRow, startCol, endRow, endCol);
        return addCommand(cmd);
    }
    
    /**
     * Draw box
     */
    public TerminalBatchBuilder drawBox(
        int startRow, int startCol,
        int width, int height,
        String title,
        BoxStyle boxStyle
    ) {
        NoteBytesMap cmd = TerminalCommands.drawBox(startRow, startCol, width, height, title, boxStyle);
        return addCommand(cmd);
    }
    
    /**
     * Draw box (no title)
     */
    public TerminalBatchBuilder drawBox(
        int startRow, int startCol,
        int width, int height,
        BoxStyle boxStyle
    ) {
        return drawBox(startRow, startCol, width, height, null, boxStyle);
    }
    
    /**
     * Draw horizontal line
     */
    public TerminalBatchBuilder drawHLine(int row, int startCol, int length) {
        NoteBytesMap cmd = TerminalCommands.drawHLine(row, startCol, length);
        return addCommand(cmd);
    }
}