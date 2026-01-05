package io.netnotes.engine.core.system.control.terminal;


import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.netnotes.engine.core.system.control.containers.Container;
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
   
    private volatile Renderable currentRenderable;
    

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


    @Override
    protected void setupStateTransitions() {
        // VISIBLE: Container is visible
        stateMachine.onStateAdded(Container.STATE_VISIBLE, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Now visible");
        });
        
        // HIDDEN: Container is hidden
        stateMachine.onStateAdded(Container.STATE_HIDDEN, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Now hidden");
        });
        
        // FOCUSED: Container has input focus
        stateMachine.onStateAdded(Container.STATE_FOCUSED, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Now focused");
        });
        
        stateMachine.onStateRemoved(Container.STATE_FOCUSED, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Focus lost");
        });
        
        // ACTIVE: Container is actively rendering
        stateMachine.onStateAdded(Container.STATE_ACTIVE, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Now active (ready to render)");
        });
        
        stateMachine.onStateRemoved(Container.STATE_ACTIVE, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] No longer active");
        });
        
  
    
    }

    public TerminalContainerHandle(String name) {
        super(TerminalContainerHandle.builder(name));
    }
    
    public static TerminalBuilder builder(String name) {
        return new TerminalBuilder(name);
    }
    

    /**
     * Set active renderable for this container
     * 
     * Simply stores the renderable. Render manager will poll it.
     * 
     * Usage:
     * <pre>
     * MyScreen screen = new MyScreen();
     * terminal.setRenderable(screen);
     * terminal.invalidate(); // Signal change
     * </pre>
     */
    public void setRenderable(Renderable renderable) {
        Renderable old = this.currentRenderable;
        this.currentRenderable = renderable;
        
        if (old != renderable) {
            // New renderable - increment generation
            nextRenderGeneration();
            invalidate();
            
            Log.logMsg(String.format(
                "[TerminalContainerHandle:%s] Renderable changed (gen=%d)",
                getId(), getCurrentRenderGeneration()
            ));
        }
    }

    /**
     * Get current renderable (for render manager to poll)
     */
    public Renderable getRenderable() {
        return currentRenderable;
    }

    /**
     * Clear renderable
     */
    public void clearRenderable() {
        this.currentRenderable = null;
        clearDirtyFlag();
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
  
            Log.logMsg("[TerminalContainerHandle] ClientRenderManager started (gen=1)");
        });
    }


    @Override
    protected void onContainerResized(ContainerResizeEvent event) {
        setDimensions(event.getWidth(), event.getHeight());
        
        // Increment generation on resize (layout change)
        nextRenderGeneration();
        
        // Invalidate to trigger re-render
        invalidate();
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
        command.put(ContainerCommands.CONTAINER_ID, getId().toNoteBytes());
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
        command.put(ContainerCommands.CONTAINER_ID, getId().toNoteBytes());
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
        command.put(ContainerCommands.CONTAINER_ID, getId().toNoteBytes());
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
        command.put(ContainerCommands.CONTAINER_ID, getId().toNoteBytes());
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
        command.put(ContainerCommands.CONTAINER_ID, getId().toNoteBytes());
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
        command.put(ContainerCommands.CONTAINER_ID, getId().toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Hide cursor
     */
    public CompletableFuture<Void> hideCursor(long generation) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_HIDE_CURSOR);
        command.put(ContainerCommands.CONTAINER_ID, getId().toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Clear from cursor to end of line
     */
    public CompletableFuture<Void> clearLine(long generation) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_LINE);
        command.put(ContainerCommands.CONTAINER_ID, getId().toNoteBytes());
        command.put(ContainerCommands.RENDERER_ID, rendererId);
        return sendRenderCommand(command, generation);
    }
    
    /**
     * Clear specific line
     */
    public CompletableFuture<Void> clearLine(int row, long generation) {
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, TerminalCommands.TERMINAL_CLEAR_LINE_AT);
        command.put(ContainerCommands.CONTAINER_ID, getId().toNoteBytes());
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
        command.put(ContainerCommands.CONTAINER_ID, getId().toNoteBytes());
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
        command.put(ContainerCommands.CONTAINER_ID, getId().toNoteBytes());
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
        command.put(ContainerCommands.CONTAINER_ID, getId().toNoteBytes());
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
    public BatchBuilder batch() {
        return new BatchBuilder(getId(), rendererId, getCurrentRenderGeneration());
    }

    /**
     * Create batch with specific generation
     */
    public BatchBuilder batch(long generation) {
        return new BatchBuilder(getId(), rendererId, generation);
    }

    /**
     * Execute a batch of commands
     * 
     * This sends the entire batch as a single command over the stream,
     * waits for completion, and returns a single future.
     * 
     * @param batch The batch builder with commands
     * @return CompletableFuture that completes when batch is done
     */
    public CompletableFuture<Void> executeBatch(BatchBuilder batch) {
        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Check generation before sending
        if (!isRenderGenerationCurrent(batch.getGeneration())) {
            Log.logMsg("[TerminalContainerHandle] Skipping batch - stale generation: " + 
                batch.getGeneration() + " (current: " + getCurrentRenderGeneration() + ")");
            return CompletableFuture.completedFuture(null);
        }
        
        NoteBytesMap batchCommand = batch.build();
        return sendRenderCommand(batchCommand, batch.getGeneration());
    }

}