package io.netnotes.engine.core.system.control.terminal.input;

import java.util.function.Consumer;

import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.io.input.KeyRunTable;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyCharEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.events.KeyCharEvent;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteIntegerArray;
import io.netnotes.engine.noteBytes.collections.NoteBytesRunnablePair;

/**
 * InputReader - Reusable text input handler with cursor support
 * 
 * Features:
 * - Handles both regular and ephemeral events (secure input devices)
 * - Full Unicode support via NoteIntegerArray (codepoint-based)
 * - Supports arrow keys for cursor movement
 * - Backspace, Delete, Home, End, word navigation
 * - Visual cursor position feedback
 * - Maximum length limits
 * - Callback when input is complete (Enter pressed)
 * 
 * Usage:
 * <pre>
 * InputReader reader = new InputReader(terminal, 10, 20, 40);
 * reader.setOnComplete(text -> {
 *     Log.logMsg("User entered: " + text);
 * });
 * keyboard.setEventConsumer(reader.getEventConsumer());
 * </pre>
 */
public class TerminalInputReader {
    public static final int DEFAULT_MAX_LENGTH = 256;
    
    private final TerminalContainerHandle terminal;
    private final int displayRow;
    private final int displayCol;
    private final int maxLength;
    
    private final NoteIntegerArray buffer = new NoteIntegerArray();
    private int cursorPos = 0; // codepoint index
    private final KeyRunTable keyRunTable;
    private Consumer<String> onComplete;
    private Consumer<String> onEscape;
    private NoteBytesReadOnly keyDownHandlerId = null;
    private NoteBytesReadOnly KeyCharHandlerId = null;
    
    /**
     * Create input reader with default max length
     */
    public TerminalInputReader(TerminalContainerHandle terminal, int row, int col) {
        this(terminal, row, col, DEFAULT_MAX_LENGTH);

    }
    
    /**
     * Create input reader with specified max length
     * 
     * @param terminal Terminal for display
     * @param row Display row for input
     * @param col Display column for input start
     * @param maxLength Maximum input length (in codepoints)
     */
    public TerminalInputReader(TerminalContainerHandle terminal, int row, int col, int maxLength) {
        this.terminal = terminal;
        this.displayRow = row;
        this.displayCol = col;
        this.maxLength = maxLength;
        
        // Setup key handlers
        this.keyRunTable = new KeyRunTable(
            new NoteBytesRunnablePair(KeyCodeBytes.ENTER, this::handleEnter),
            new NoteBytesRunnablePair(KeyCodeBytes.ESCAPE, this::handleEscape),
            new NoteBytesRunnablePair(KeyCodeBytes.BACKSPACE, this::handleBackspace),
            new NoteBytesRunnablePair(KeyCodeBytes.DELETE, this::handleDelete),
            new NoteBytesRunnablePair(KeyCodeBytes.LEFT, this::handleLeft),
            new NoteBytesRunnablePair(KeyCodeBytes.RIGHT, this::handleRight),
            new NoteBytesRunnablePair(KeyCodeBytes.HOME, this::handleHome),
            new NoteBytesRunnablePair(KeyCodeBytes.END, this::handleEnd)
        );

        keyDownHandlerId = terminal.addKeyDownHandler(this::handleKeyDown);
        KeyCharHandlerId = terminal.addKeyCharHandler(this::handleKeyChar);
    }
    

    /**
     * Set callback for when user presses Enter
     */
    public void setOnComplete(Consumer<String> onComplete) {
        this.onComplete = onComplete;
    }
    
    /**
     * Set callback for when user presses Escape
     */
    public void setOnEscape(Consumer<String> onEscape) {
        this.onEscape = onEscape;
    }
    
    /**
     * Get current input text
     */
    public String getText() {
        return buffer.toString();
    }
    
    /**
     * Get current input as NoteIntegerArray (codepoints)
     */
    public NoteIntegerArray getBuffer() {
        return buffer.copy();
    }
    
    /**
     * Clear input buffer and reset cursor
     */
    public void clear() {
        buffer.clear();
        cursorPos = 0;
        redraw();
    }
    
    /**
     * Set initial text in buffer
     */
    public void setText(String text) {
        buffer.clear();
        buffer.append(text);
        cursorPos = buffer.size();
        redraw();
    }
    
     /**
     * Handle input events from device
     * Supports both regular and ephemeral events
     */
    private void handleKeyDown(RoutedEvent event) {
        if (event instanceof KeyDownEvent keyDown) {
            handleKeyDown(keyDown);
        } else if (event instanceof EphemeralKeyDownEvent keyDown) {
            handleEphemeralKeyDown(keyDown);
            keyDown.close();
        }
    }

    private void handleKeyChar(RoutedEvent event){
        if(event instanceof KeyCharEvent keyChar){
            handleKeyChar(keyChar);
        }if (event instanceof EphemeralKeyCharEvent keyChar) {
            handleEphemeralChar(keyChar);
            keyChar.close();
        }
    }

     /**
     * Handle ephemeral key down (for special keys)
     */
    private void handleEphemeralKeyDown(EphemeralKeyDownEvent event) {
        // lookup keycode and run
        keyRunTable.run(event.getKeyCodeBytes());
    }
    

    /**
     * Handle regular key down events
     */
    private void handleKeyDown(KeyDownEvent event) {
        keyRunTable.run(event.getKeyCodeBytes());
    }
    
    /**
     * Handle regular character events
     */
    private void handleKeyChar(KeyCharEvent event) {
        NoteBytes codepointBytes = event.getCodepointData();
        if (codepointBytes != null) {
            int codepoint = codepointBytes.getAsInt();
            insertCodePoint(codepoint);
        }
    }
    
    /**
     * Handle ephemeral character events
     */
    private void handleEphemeralChar(EphemeralKeyCharEvent event) {
        NoteBytes codepointBytes = event.getCodepointBytes();
        if (codepointBytes != null) {
            int codepoint = codepointBytes.getAsInt();
            insertCodePoint(codepoint);
        }
    }
    
    /**
     * Insert codepoint at cursor position
     */
    private void insertCodePoint(int codepoint) {
        if (buffer.size() >= maxLength) {
            return; // At max length
        }
        
        if (Character.isValidCodePoint(codepoint) && !Character.isISOControl(codepoint)) {
            buffer.insertCodePoint(cursorPos, codepoint);
            cursorPos++;
            redraw();
        }
    }
    
    /**
     * Handle Enter key - complete input
     */
    private void handleEnter() {
        if (onComplete != null) {
            onComplete.accept(buffer.toString());
        }
    }
    
    /**
     * Handle Escape key - cancel input
     */
    private void handleEscape() {
        if (onEscape != null) {
            onEscape.accept(buffer.toString());
        }
    }
    
    /**
     * Handle Backspace - delete codepoint before cursor
     */
    private void handleBackspace() {
        if (cursorPos > 0) {
            buffer.deleteCodePointAt(cursorPos - 1);
            cursorPos--;
            redraw();
        }
    }
    
    /**
     * Handle Delete - delete codepoint at cursor
     */
    private void handleDelete() {
        if (cursorPos < buffer.size()) {
            buffer.deleteCodePointAt(cursorPos);
            redraw();
        }
    }
    
    /**
     * Handle Left Arrow - move cursor left
     */
    private void handleLeft() {
        if (cursorPos > 0) {
            cursorPos--;
            updateCursorPosition();
        }
    }
    
    /**
     * Handle Right Arrow - move cursor right
     */
    private void handleRight() {
        if (cursorPos < buffer.size()) {
            cursorPos++;
            updateCursorPosition();
        }
    }
    
    /**
     * Handle Home - move cursor to start
     */
    private void handleHome() {
        cursorPos = 0;
        updateCursorPosition();
    }
    
    /**
     * Handle End - move cursor to end
     */
    private void handleEnd() {
        cursorPos = buffer.size();
        updateCursorPosition();
    }
    
    /**
     * Redraw the entire input line
     * Handles proper display width calculation for Unicode
     */
    private void redraw() {
        // Clear the line
        terminal.clearLine(displayRow);
        
        // Print the current buffer
        String text = buffer.toString();
        terminal.printAt(displayRow, displayCol, text);
        
        // Position cursor
        updateCursorPosition();
    }
    
    /**
     * Update cursor position without redrawing text
     * Uses display width calculation for proper Unicode positioning
     */
    private void updateCursorPosition() {
        // Calculate display width up to cursor position
        int displayWidth = buffer.getDisplayWidth(cursorPos);
        terminal.moveCursor(displayRow, displayCol + displayWidth);
    }
    
    /**
     * Close the reader and clean up
     */
    public void close() {
        buffer.clear();
        cursorPos = 0;
        terminal.removeKeyCharHandler(KeyCharHandlerId);
        terminal.removeKeyDownHandler(keyDownHandlerId);
    }
}
