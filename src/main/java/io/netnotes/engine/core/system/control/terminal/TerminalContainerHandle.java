package io.netnotes.engine.core.system.control.terminal;


import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.netnotes.engine.core.system.control.containers.ContainerCommands;
import io.netnotes.engine.core.system.control.containers.ContainerConfig;
import io.netnotes.engine.core.system.control.containers.ContainerHandle;
import io.netnotes.engine.core.system.control.containers.ContainerType;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.terminal.elements.TerminalTextBox;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.input.events.containers.ContainerResizeEvent;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * TerminalContainerHandle - Terminal-style container with keyboard utilities
 */
public class TerminalContainerHandle extends ContainerHandle {
    
    // Temporary key wait state
    private CompletableFuture<RoutedEvent> keyWaitFuture = null;
    private CompletableFuture<Void> anyKeyFuture = null;
    private NoteBytesReadOnly handlerId = null;
    private RenderManager renderManager;
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
        if (anyKeyFuture != null) {
            // Already waiting, return existing future
            return anyKeyFuture;
        }
        Log.logMsg("[TerminalContainerHandle] waitForKeyPress");

        anyKeyFuture = new CompletableFuture<>();
          
        
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
    
    @Override
    public CompletableFuture<Void> run() {
 
        return super.run().thenAccept(v -> {
            // Start render manager after stream is ready
            this.renderManager = new RenderManager(this);
            renderManager.start();
            Log.logMsg("[TerminalContainerHandle] RenderManager started");
        });
    }

    public RenderManager getRenderManager() {
        return renderManager;
    }

    @Override
    protected void onContainerResized(ContainerResizeEvent event) {
        setDimensions(event.getWidth(), event.getHeight());
        
        // Invalidate to trigger re-render with new dimensions
        if (renderManager != null) {
            renderManager.invalidate();
        }
    }
    
    @Override
    public void onStop() {
        if (renderManager != null) {
            renderManager.stop();
        }
        super.onStop();
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
        if (keyWaitFuture != null) {
            // Already waiting, return existing future
            return keyWaitFuture;
        }
        
        keyWaitFuture = new CompletableFuture<>();
        
   
        
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
        if (keyWaitFuture == null) {
            return;
        }
        
        // Remove temporary handler
        if (handlerId != null) {
            removeKeyDownHandler(handlerId);
            handlerId = null;
        }
        
        // Complete the future
        if (keyWaitFuture != null && !keyWaitFuture.isDone()) {
            keyWaitFuture.complete(event);
        }
        
        keyWaitFuture = null;
    }

    private void handleKeyWaitComplete() {
        if (anyKeyFuture == null) {
            return;
        }
         Log.logMsg("[TerminalContainerHandle] waitComplete");
        // Remove temporary handler
        if (handlerId != null) {
            removeKeyDownHandler(handlerId);
            handlerId = null;
        }
        
        // Complete the future
        if (anyKeyFuture != null && !anyKeyFuture.isDone()) {
            anyKeyFuture.complete(null);
        }
        
        anyKeyFuture = null;
    }
    
    /**
     * Cancel key wait (if needed)
     */
    public void cancelKeyWait() {
        if (anyKeyFuture == null) {
            return;
        }
        
        // Remove temporary handler
        if (handlerId != null) {
            removeKeyDownHandler(handlerId);
            handlerId = null;
        }
        
        // Cancel the future
        if (keyWaitFuture != null && !keyWaitFuture.isDone()) {
            keyWaitFuture.cancel(false);
        }
        
        keyWaitFuture = null;
    }

    /**
     * Execute rendering operations within a batch with generation check
     * 
     * This ensures:
     * 1. All operations in the batch use the SAME generation
     * 2. The batch is atomic (begin -> operations -> end)
     * 3. Stale renders are prevented via generation check
     * 
     * Usage:
     * <pre>
     * terminal.batchWithGeneration(gen, () -> {
     *     terminal.clear(gen);
     *     terminal.printAt(0, 0, "Header", TextStyle.BOLD, gen);
     *     terminal.drawBox(2, 0, 40, 10, "Content", BoxStyle.SINGLE, gen);
     * });
     * </pre>
     * 
     * @param generation The generation to use for all operations
     * @param operations Runnable containing the rendering operations
     * @return CompletableFuture that completes when batch is done
     */
    public CompletableFuture<Void> batchWithGeneration(long generation, Runnable operations) {
        // Check if generation is still current before starting
        if (!isRenderGenerationCurrent(generation)) {
            Log.logMsg("[TerminalContainerHandle] Skipping batch - stale generation: " + 
                generation + " (current: " + getCurrentRenderGeneration() + ")");
            return CompletableFuture.completedFuture(null);
        }
        
        return beginBatch(generation)
            .thenRun(() -> {
                // Double-check generation before executing operations
                if (isRenderGenerationCurrent(generation)) {
                    try {
                        operations.run();
                    } catch (Exception e) {
                        Log.logError("[TerminalContainerHandle] Error in batch operations: " + 
                            e.getMessage());
                        throw new RuntimeException("Batch operations failed", e);
                    }
                } else {
                    Log.logMsg("[TerminalContainerHandle] Generation changed during batch setup");
                }
            })
            .thenCompose(v -> {
                // Only end batch if generation is still current
                if (isRenderGenerationCurrent(generation)) {
                    return endBatch(generation);
                } else {
                    Log.logMsg("[TerminalContainerHandle] Skipping endBatch - generation changed");
                    return CompletableFuture.completedFuture(null);
                }
            })
            .exceptionally(ex -> {
                Log.logError("[TerminalContainerHandle] Batch failed: " + ex.getMessage());
                // Try to end batch anyway to prevent renderer getting stuck
                try {
                    endBatch(generation).join();
                } catch (Exception e) {
                    // Ignore - already handling an error
                }
                return null;
            });
    }

    /**
     * Convenience overload using current generation
     */
    public CompletableFuture<Void> batch(Runnable operations) {
        return batchWithGeneration(getCurrentRenderGeneration(), operations);
    }
        
    /**
     * Check if currently waiting for key press
     */
    public boolean isWaitingForKeyPress() {
        return keyWaitFuture != null;
    }
    
    // ===== TERMINAL COMMANDS =====
    
    /**
     * Clear the entire terminal screen
     */
    public CompletableFuture<Void> clear(long generation) {
        
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
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
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_PRINT);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.TEXT, text);
        command.put(Keys.STYLE, style.toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
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
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_PRINTLN);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.TEXT, text);
        command.put(Keys.STYLE, style.toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
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
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_PRINT_AT);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.ROW, row);
        command.put(Keys.COL, col);
        command.put(Keys.TEXT, text);
        command.put(Keys.STYLE, style.toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Move cursor to position
     */
    public CompletableFuture<Void> moveCursor(int row, int col, long generation) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_MOVE_CURSOR);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.ROW, row);
        command.put(Keys.COL, col);
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command, generation);
    }
    public CompletableFuture<Void> moveCursor(int row, int col) {
        return moveCursor(row, col, getCurrentRenderGeneration());
    }
    /**
     * Show cursor
     */
    public CompletableFuture<Void> showCursor(long generation) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_SHOW_CURSOR);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Hide cursor
     */
    public CompletableFuture<Void> hideCursor(long generation) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_HIDE_CURSOR);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Clear from cursor to end of line
     */
    public CompletableFuture<Void> clearLine(long generation) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_LINE);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Clear specific line
     */
    public CompletableFuture<Void> clearLine(int row, long generation) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_LINE_AT);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.ROW, row);
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Clear rectangular region
     */
    public CompletableFuture<Void> clearRegion(int startRow, int startCol, int endRow, int endCol, long generation) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_REGION);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(TerminalCommands.START_ROW, startRow);
        command.put(TerminalCommands.START_COL, startCol);
        command.put(TerminalCommands.END_ROW, endRow);
        command.put(TerminalCommands.END_COL, endCol);
        command.put(ContainerCommands.RENDERER_ID, rendererId);
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
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_DRAW_BOX);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(TerminalCommands.START_ROW, startRow);
        command.put(TerminalCommands.START_COL, startCol);
        command.put(Keys.WIDTH, width);
        command.put(Keys.HEIGHT, height);
        command.put(Keys.TITLE, title != null ? title : "");
        command.put(TerminalCommands.BOX_STYLE, boxStyle.name());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
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
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_DRAW_HLINE);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        command.put(Keys.ROW, row);
        command.put(TerminalCommands.START_COL, startCol);
        command.put(Keys.LENGTH, length);
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command, generation);
    }

    public CompletableFuture<Void> drawHLine(int row, int startCol, int length) {
        return drawHLine(row, startCol, length, getCurrentRenderGeneration());
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
    public CompletableFuture<Void> beginBatch(long generation) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_BEGIN_BATCH);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        return sendRenderCommand(command, generation);
    }

    public CompletableFuture<Void> beginBatch() {
        return beginBatch(getCurrentRenderGeneration());
    }

    /**
     * End batch and render
     */
    public CompletableFuture<Void> endBatch(long generation) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_END_BATCH);
        command.put(Keys.CONTAINER_ID, getId().toNoteBytes());
        return sendRenderCommand(command, generation);
    }
}