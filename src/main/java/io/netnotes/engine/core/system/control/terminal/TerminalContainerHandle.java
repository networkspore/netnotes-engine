package io.netnotes.engine.core.system.control.terminal;


import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.netnotes.engine.core.system.control.containers.ContainerConfig;
import io.netnotes.engine.core.system.control.containers.ContainerHandle;
import io.netnotes.engine.core.system.control.containers.ContainerType;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.terminal.elements.TerminalTextBox;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * TerminalContainerHandle - Terminal-style container with keyboard utilities
 * 
 * QUEUE-BASED RENDERING:
 * - Container signals render need via consumer (set by manager)
 * - Only requests render when actually ready (all conditions met)
 * - Automatically re-requests when transitions to ready state
 */
public class TerminalContainerHandle extends ContainerHandle
<
    TerminalBatchBuilder, 
    TerminalRenderElement, 
    TerminalRenderable, 
    TerminalContainerHandle, 
    TerminalContainerHandle.TerminalBuilder
> {
    

    protected Consumer<TerminalContainerHandle> onRenderRequest;


    /**
     * Constructor - creates terminal-style container
     */
    protected TerminalContainerHandle(TerminalBuilder builder) {
        super(builder);
    }
    /**
     * Constructor - creates terminal-style container without builder
     * 
     * @param name
     */
    public TerminalContainerHandle(String name) {
        super(new TerminalBuilder(name));
    }


    @Override
    protected void setupStateTransitions() {
     
    }




    public static TerminalBuilder builder() {
        return new TerminalBuilder();
    }


    public static TerminalBuilder builder(String name) {
        return new TerminalBuilder(name);
    }

    // ===== BUILDER =====
    
    public static class TerminalBuilder extends ContainerHandle.Builder<TerminalBatchBuilder, TerminalRenderElement,TerminalRenderable, TerminalContainerHandle, TerminalBuilder> {
        
        public TerminalBuilder(String name) {
            super(name, ContainerType.TERMINAL);
        }

        public TerminalBuilder() {
            super(ContainerType.TERMINAL);
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
    
 
    
    @Override
    public CompletableFuture<Void> run() {
 
        return super.run().thenAccept(v -> {
            // Stream is now ready - request render if we're dirty
            Log.logMsg("[TerminalContainerHandle] Stream ready");
            
            if (isDirty()) {
                render();
            }
        });
    }


    

    /**
     * Check if has active renderable
     */
    public boolean hasRenderable() {
        return currentRenderable != null;
    }
        
    @Override
    public void onStop() {
        clearRenderable();
        super.onStop();
    }

    // ===== TERMINAL COMMANDS =====
    
    /**
     * Clear the entire terminal screen
     */
    public CompletableFuture<Void> clear(long generation) {
        
        NoteBytesMap command = TerminalCommands.clear();
        return sendRenderCommand(command, generation);
    }

    public CompletableFuture<Void> clear() {
        return clear(getCurrentRenderGeneration());
    }
    
    public CompletableFuture<Void> print(String text) {
        return print(text, TextStyle.NORMAL, getCurrentRenderGeneration());
    }
    /**
     * Print text at current cursor position
     */
    public CompletableFuture<Void> print(String text, long generation) {
        return print(text, TextStyle.NORMAL, generation);
    }
    
    /**
     * Print styled text at current cursor position
     */
    public CompletableFuture<Void> print(String text, TextStyle style, long generation) {
        NoteBytesMap command = TerminalCommands.print(text, style);
        return sendRenderCommand(command, generation);
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
    public CompletableFuture<Void> println(String text, TextStyle style, long generation) {
        NoteBytesMap command = TerminalCommands.println(text, style);
        return sendRenderCommand(command, generation);
    }

    public CompletableFuture<Void> println(String text, TextStyle style) {
        return println(text, style, getCurrentRenderGeneration());
    }
    
    /**
     * Print text at specific screen position
     */
    public CompletableFuture<Void> printAt(int row, int col, String text) {
        return printAt(row, col, text, TextStyle.NORMAL);
    }

     public CompletableFuture<Void> printAtCenterRowSpan(int rowEnd, int col, String text) {
        return sendRenderCommand(TerminalCommands.printAtCenterRowSpan(rowEnd, col, text));
    }


    public CompletableFuture<Void> printAtCenterRowSpan(int rowStart, int rowEnd, int col, String text) {
        return sendRenderCommand(TerminalCommands.printAtCenterRowSpan(rowStart, rowEnd, col, text));
    }

    public CompletableFuture<Void> printAtCenterColSpan(int row, int colEnd, String text) {
        return sendRenderCommand(TerminalCommands.printAtCenterColSpan(row, colEnd, text));
    }

    public CompletableFuture<Void> printAtCenterColSpan(int row, int colStart,  int colEnd, String text) {
       
        return sendRenderCommand(TerminalCommands.printAtCenterColSpan(row, colStart, colEnd, text));
    }


    public CompletableFuture<Void> printAtCenterSpan(int rowStart, int rowEnd, int colStart,  int colEnd, String text) {
       
        return sendRenderCommand(TerminalCommands.printAtCenterSpan(rowStart, rowEnd, colStart, colEnd, text));
    }

    /**
     * Print text at specific screen position
     */
    public CompletableFuture<Void> printAt(int row, int col, String text, long generation) {
        return printAt(row, col, text, TextStyle.NORMAL, generation);
    }

    public CompletableFuture<Void> printAt(int row, int col, String text, TextStyle style) {
        return printAt(row, col, text, style, getCurrentRenderGeneration());
    }

    /**
     * Print styled text at specific position
     */
    public CompletableFuture<Void> printAt(int row, int col, String text, TextStyle style, long generation) {
        NoteBytesMap command = TerminalCommands.printAt(row, col, text, style);
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Move cursor to position
     */
    public CompletableFuture<Void> moveCursor(int row, int col, long generation) {
        NoteBytesMap command = TerminalCommands.moveCursor(row, col);
        return sendRenderCommand(command, generation);
    }

    public CompletableFuture<Void> moveCursor(int row, int col) {
        return moveCursor(row, col, getCurrentRenderGeneration());
    }
    /**
     * Show cursor
     */
    public CompletableFuture<Void> showCursor(long generation) {
        NoteBytesMap command = TerminalCommands.showCursor();
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Hide cursor
     */
    public CompletableFuture<Void> hideCursor(long generation) {
        NoteBytesMap command = TerminalCommands.hideCursor();
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Clear from cursor to end of line
     */
    public CompletableFuture<Void> clearLine(long generation) {
        NoteBytesMap command = TerminalCommands.clearLine();
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Clear specific line
     */
    public CompletableFuture<Void> clearLine(int row, long generation) {
        NoteBytesMap command = TerminalCommands.clearLineAt(row);
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Clear rectangular region
     */
    public CompletableFuture<Void> clearRegion(int startRow, int startCol, int endRow, int endCol, long generation) {
        NoteBytesMap command = TerminalCommands.clearRegion(startRow, startCol, endRow, endCol);
        return sendRenderCommand(command, generation);
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

    public CompletableFuture<Void> drawBox(
        int startRow, int startCol, 
        int width, int height,
        String title,
        BoxStyle boxStyle
    ) {
        return drawBox(startRow, startCol, width, height, title, boxStyle, 
            getCurrentRenderGeneration());
    }
    
    /**
     * DrawBox with generation
     */
    public CompletableFuture<Void> drawBox(
        int startRow, int startCol, 
        int width, int height,
        String title,
        BoxStyle boxStyle,
        long generation
    ) {
        NoteBytesMap command = TerminalCommands.drawBox(startRow, startCol, width, height, title, boxStyle);
        return sendRenderCommand(command, generation);
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
     /**
     * DrawHLine with generation
     */
    public CompletableFuture<Void> drawHLine(int row, int startCol, int length, long generation) {
        NoteBytesMap command = TerminalCommands.drawHLine(row, startCol, length);
        return sendRenderCommand(command, generation);
    }

    public CompletableFuture<Void> drawHLine(int row, int startCol, int length) {
        return drawHLine(row, startCol, length, getCurrentRenderGeneration());
    }
    
 
    // ===== HIGH-LEVEL HELPERS =====
    
    /**
     * Print status line at bottom
     */
    public CompletableFuture<Void> printStatusLine(int row, String text) {
        return printAt(row, 0, text, TextStyle.INVERSE);
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
    

    /**
     * Create a new batch builder
     * 
     * Usage:
     * <pre>
     * BatchBuilder batch = terminal.batch()
     *     .clear()
     *     .printAt(0, 0, "Header", TextStyle.BOLD)
     *     .drawBox(2, 0, 40, 10, "Content", BoxStyle.SINGLE)
     *     .println("Ready!");
     * 
     * terminal.executeBatch(batch).thenRun(() -> {
     *     System.out.println("Batch complete!");
     * });
     * </pre>
     */
    @Override
    public TerminalBatchBuilder batch() {
        return new TerminalBatchBuilder(getId(), rendererId, getCurrentRenderGeneration());
    }

    /**
     * Create batch with specific generation
     */
    @Override
    public TerminalBatchBuilder batch(long generation) {
        return new TerminalBatchBuilder(getId(), rendererId, generation);
    }



}