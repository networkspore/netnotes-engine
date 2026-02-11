package io.netnotes.engine.ui;

/**
 * TextPosition - Text alignment options for text input fields
 * 
 * Defines how text is positioned horizontally within the input field bounds.
 * Affects both rendering and scroll behavior when text exceeds field width.
 */
public enum TextPosition {
    /**
     * Text aligned to the left edge
     * - Cursor starts at position 0
     * - Text scrolls left when cursor moves right beyond visible area
     * - Most common for standard text input
     */
    LEFT,
    
    /**
     * Text centered horizontally
     * - Text grows outward from center
     * - Scrolls bidirectionally to keep cursor visible
     * - Useful for short, display-focused fields
     */
    CENTER,
    
    /**
     * Text aligned to the right edge
     * - Cursor starts at right edge
     * - Text scrolls right when cursor moves left beyond visible area
     * - Common for numeric or RTL language input
     */
    RIGHT
}