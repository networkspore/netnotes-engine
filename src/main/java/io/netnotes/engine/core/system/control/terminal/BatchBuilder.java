package io.netnotes.engine.core.system.control.terminal;

import io.netnotes.engine.core.system.control.containers.ContainerCommands;
import io.netnotes.engine.core.system.control.containers.ContainerId;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
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
public class BatchBuilder {
    
    private final ContainerId containerId;
    private final NoteBytes rendererId;
    private final NoteBytesArray commands;
    private final long generation;
    
    public BatchBuilder(ContainerId containerId, NoteBytes rendererId, long generation) {
        this.containerId = containerId;
        this.rendererId = rendererId;
        this.generation = generation;
        this.commands = new NoteBytesArray();
    }
    
    /**
     * Build final batch command
     */
    public NoteBytesMap build() {
        NoteBytesMap batchCommand = new NoteBytesMap();
        batchCommand.put(Keys.CMD, TerminalCommands.TERMINAL_BATCH);
        batchCommand.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        batchCommand.put(ContainerCommands.RENDERER_ID, rendererId);
        batchCommand.put(ContainerCommands.GENERATION, generation);
        batchCommand.put(TerminalCommands.BATCH_COMMANDS, commands);
        return batchCommand;
    }
    
    /**
     * Add raw command to batch
     */
    public BatchBuilder addCommand(NoteBytesMap cmd) {
        commands.add(cmd.toNoteBytes());
        return this;
    }
    
    /**
     * Add raw command bytes to batch
     */
    public BatchBuilder addCommand(NoteBytes cmd) {
        commands.add(cmd);
        return this;
    }
    
    /**
     * Get generation this batch is for
     */
    public long getGeneration() {
        return generation;
    }
    
    /**
     * Get number of commands in batch
     */
    public int getCommandCount() {
        return commands.size();
    }
    
    /**
     * Check if batch is empty
     */
    public boolean isEmpty() {
        return commands.isEmpty();
    }
    
    // ===== CONVENIENCE METHODS =====
    
    /**
     * Clear screen
     */
    public BatchBuilder clear() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR);
        return addCommand(cmd);
    }
    
    /**
     * Print text
     */
    public BatchBuilder print(String text) {
        return print(text, TextStyle.NORMAL);
    }
    
    /**
     * Print styled text
     */
    public BatchBuilder print(String text, TextStyle style) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, TerminalCommands.TERMINAL_PRINT);
        cmd.put(Keys.TEXT, text);
        cmd.put(Keys.STYLE, style.toNoteBytes());
        return addCommand(cmd);
    }
    
    /**
     * Print line
     */
    public BatchBuilder println(String text) {
        return println(text, TextStyle.NORMAL);
    }
    
    /**
     * Print styled line
     */
    public BatchBuilder println(String text, TextStyle style) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, TerminalCommands.TERMINAL_PRINTLN);
        cmd.put(Keys.TEXT, text);
        cmd.put(Keys.STYLE, style.toNoteBytes());
        return addCommand(cmd);
    }
    
    /**
     * Print at position
     */
    public BatchBuilder printAt(int row, int col, String text) {
        return printAt(row, col, text, TextStyle.NORMAL);
    }
    
    /**
     * Print styled text at position
     */
    public BatchBuilder printAt(int row, int col, String text, TextStyle style) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, TerminalCommands.TERMINAL_PRINT_AT);
        cmd.put(Keys.ROW, row);
        cmd.put(Keys.COL, col);
        cmd.put(Keys.TEXT, text);
        cmd.put(Keys.STYLE, style.toNoteBytes());
        return addCommand(cmd);
    }
    
    /**
     * Move cursor
     */
    public BatchBuilder moveCursor(int row, int col) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, TerminalCommands.TERMINAL_MOVE_CURSOR);
        cmd.put(Keys.ROW, row);
        cmd.put(Keys.COL, col);
        return addCommand(cmd);
    }
    
    /**
     * Show cursor
     */
    public BatchBuilder showCursor() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, TerminalCommands.TERMINAL_SHOW_CURSOR);
        return addCommand(cmd);
    }
    
    /**
     * Hide cursor
     */
    public BatchBuilder hideCursor() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, TerminalCommands.TERMINAL_HIDE_CURSOR);
        return addCommand(cmd);
    }
    
    /**
     * Clear line at cursor
     */
    public BatchBuilder clearLine() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_LINE);
        return addCommand(cmd);
    }
    
    /**
     * Clear specific line
     */
    public BatchBuilder clearLine(int row) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_LINE_AT);
        cmd.put(Keys.ROW, row);
        return addCommand(cmd);
    }
    
    /**
     * Clear region
     */
    public BatchBuilder clearRegion(int startRow, int startCol, int endRow, int endCol) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_REGION);
        cmd.put(TerminalCommands.START_ROW, startRow);
        cmd.put(TerminalCommands.START_COL, startCol);
        cmd.put(TerminalCommands.END_ROW, endRow);
        cmd.put(TerminalCommands.END_COL, endCol);
        return addCommand(cmd);
    }
    
    /**
     * Draw box
     */
    public BatchBuilder drawBox(
        int startRow, int startCol,
        int width, int height,
        String title,
        BoxStyle boxStyle
    ) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, TerminalCommands.TERMINAL_DRAW_BOX);
        cmd.put(TerminalCommands.START_ROW, startRow);
        cmd.put(TerminalCommands.START_COL, startCol);
        cmd.put(Keys.WIDTH, width);
        cmd.put(Keys.HEIGHT, height);
        cmd.put(Keys.TITLE, title != null ? title : "");
        cmd.put(TerminalCommands.BOX_STYLE, boxStyle.name());
        return addCommand(cmd);
    }
    
    /**
     * Draw box (no title)
     */
    public BatchBuilder drawBox(
        int startRow, int startCol,
        int width, int height,
        BoxStyle boxStyle
    ) {
        return drawBox(startRow, startCol, width, height, null, boxStyle);
    }
    
    /**
     * Draw horizontal line
     */
    public BatchBuilder drawHLine(int row, int startCol, int length) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put(Keys.CMD, TerminalCommands.TERMINAL_DRAW_HLINE);
        cmd.put(Keys.ROW, row);
        cmd.put(TerminalCommands.START_COL, startCol);
        cmd.put(Keys.LENGTH, length);
        return addCommand(cmd);
    }
}