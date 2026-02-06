package io.netnotes.engine.core.system.control.terminal;

import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

/**
 * Terminal command factory methods
 * 
 * Coordinate System: Uses x,y where:
 *   x = horizontal position (0 = left)
 *   y = vertical position (0 = top)
 *   
 * All parameters follow the convention: x before y
 * Regions are represented using TerminalRectangle objects
 */
public class TerminalCommands {
    public static final String PRESS_ANY_KEY = "Press any key to continue...";

    // Command type constants
    public static final NoteBytesReadOnly TERMINAL_CLEAR = 
        new NoteBytesReadOnly("clear");
    public static final NoteBytesReadOnly TERMINAL_PRINT = 
        new NoteBytesReadOnly("print");
    public static final NoteBytesReadOnly TERMINAL_PRINTLN = 
        new NoteBytesReadOnly("println");
    public static final NoteBytesReadOnly TERMINAL_PRINT_AT = 
        new NoteBytesReadOnly("print_at");    
    public static final NoteBytesReadOnly TERMINAL_MOVE_CURSOR = 
        new NoteBytesReadOnly("move_cursor");
    public static final NoteBytesReadOnly TERMINAL_SHOW_CURSOR = 
        new NoteBytesReadOnly("show_cursor");
    public static final NoteBytesReadOnly TERMINAL_HIDE_CURSOR = 
        new NoteBytesReadOnly("hide_cursor");
    public static final NoteBytesReadOnly TERMINAL_CLEAR_LINE = 
        new NoteBytesReadOnly("clear_line");
    public static final NoteBytesReadOnly TERMINAL_CLEAR_LINE_AT = 
        new NoteBytesReadOnly("clear_line_at");
    public static final NoteBytesReadOnly TERMINAL_CLEAR_REGION = 
        new NoteBytesReadOnly("clear_region");
    public static final NoteBytesReadOnly TERMINAL_DRAW_BOX = 
        new NoteBytesReadOnly("draw_box");
    public static final NoteBytesReadOnly TERMINAL_DRAW_HLINE = 
        new NoteBytesReadOnly("draw_hline");
    public static final NoteBytesReadOnly TERMINAL_DRAW_VLINE = 
        new NoteBytesReadOnly("draw_vline");
    public static final NoteBytesReadOnly TERMINAL_FILL_REGION = 
        new NoteBytesReadOnly("fill_region");
    public static final NoteBytesReadOnly TERMINAL_RESIZE = 
        new NoteBytesReadOnly("resize");

    // Additional parameter constants
    public static final NoteBytesReadOnly BOX_STYLE = 
        new NoteBytesReadOnly("box_style");
    public static final NoteBytesReadOnly CODE_POINT =
        new NoteBytesReadOnly("code_point");

    // ===== SCREEN OPERATIONS =====
    
    /**
     * Clear entire screen
     */
    public static NoteBytesObject clear() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_CLEAR)
        });
    }

    // ===== TEXT OUTPUT =====
    
    /**
     * Print text at cursor position
     */
    public static NoteBytesObject print(String text, TextStyle style) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_PRINT),
            new NoteBytesPair(Keys.TEXT, text),
            new NoteBytesPair(Keys.STYLE, style.toNoteBytes())
        });
    }

    /**
     * Print line (with newline) at cursor position
     */
    public static NoteBytesObject println(String text, TextStyle style) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_PRINTLN),
            new NoteBytesPair(Keys.TEXT, text),
            new NoteBytesPair(Keys.STYLE, style.toNoteBytes())
        });
    }

    /**
     * Print text at specific position
     * @param x horizontal position
     * @param y vertical position
     * @param text text to print
     * @param style text style
     */
    public static NoteBytesObject printAt(int x, int y, String text, TextStyle style) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_PRINT_AT),
            new NoteBytesPair(Keys.X, x),
            new NoteBytesPair(Keys.Y, y),
            new NoteBytesPair(Keys.TEXT, text),
            new NoteBytesPair(Keys.STYLE, style.toNoteBytes())
        });
    }

    // ===== CENTERED TEXT HELPERS =====
    
    /**
     * Print text centered vertically within a region
     * @param region the bounding region
     * @param x horizontal position
     * @param text text to print
     */
    public static NoteBytesObject printAtCenterY(TerminalRectangle region, int x, String text) {
        int centerY = region.getY() + (region.getHeight() / 2);
        return printAt(x, centerY, text, TextStyle.NORMAL);
    }

    /**
     * Print text centered horizontally within a region
     * @param region the bounding region
     * @param y vertical position
     * @param text text to print
     */
    public static NoteBytesObject printAtCenterX(TerminalRectangle region, int y, String text) {
        int halfText = text.length() / 2;
        int centerX = region.getX() + (region.getWidth() / 2) - halfText;
        return printAt(Math.max(region.getX(), centerX), y, text, TextStyle.NORMAL);
    }

    /**
     * Print text centered both horizontally and vertically within a region
     * @param region the bounding region
     * @param text text to print
     */
    public static NoteBytesObject printAtCenter(TerminalRectangle region, String text) {
        int halfText = text.length() / 2;
        int centerX = region.getX() + (region.getWidth() / 2) - halfText;
        int centerY = region.getY() + (region.getHeight() / 2);
        return printAt(Math.max(region.getX(), centerX), centerY, text, TextStyle.NORMAL);
    }

    // ===== CURSOR OPERATIONS =====
    
    /**
     * Move cursor to position
     * @param x horizontal position
     * @param y vertical position
     */
    public static NoteBytesObject moveCursor(int x, int y) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_MOVE_CURSOR),
            new NoteBytesPair(Keys.X, x),
            new NoteBytesPair(Keys.Y, y)
        });
    }

    /**
     * Show cursor
     */
    public static NoteBytesObject showCursor() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_SHOW_CURSOR)
        });
    }

    /**
     * Hide cursor
     */
    public static NoteBytesObject hideCursor() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_HIDE_CURSOR)
        });
    }

    // ===== CLEAR OPERATIONS =====
    
    /**
     * Clear line at cursor position
     */
    public static NoteBytesObject clearLine() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_CLEAR_LINE)
        });
    }

    /**
     * Clear specific line
     * @param y vertical position of line
     */
    public static NoteBytesObject clearLineAt(int y) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_CLEAR_LINE_AT),
            new NoteBytesPair(Keys.Y, y)
        });
    }

    /**
     * Clear rectangular region
     * @param region the region to clear
     */
    public static NoteBytesObject clearRegion(TerminalRectangle region) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_CLEAR_REGION),
            new NoteBytesPair(Keys.REGION, region.toNoteBytes())
        });
    }

    // ===== DRAWING OPERATIONS =====
    
    /**
     * Draw box with border
     * @param region the box bounds
     * @param title optional title (can be null)
     * @param boxStyle box border style
     */
    public static NoteBytesObject drawBox(TerminalRectangle region, String title, BoxStyle boxStyle) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_DRAW_BOX),
            new NoteBytesPair(Keys.REGION, region.toNoteBytes()),
            new NoteBytesPair(Keys.TITLE, title != null ? title : ""),
            new NoteBytesPair(BOX_STYLE, boxStyle.name())
        });
    }

    /**
     * Draw box with border (no title)
     * @param region the box bounds
     * @param boxStyle box border style
     */
    public static NoteBytesObject drawBox(TerminalRectangle region, BoxStyle boxStyle) {
        return drawBox(region, null, boxStyle);
    }

  
    /**
     * Draw horizontal line
     * @param x starting horizontal position
     * @param y vertical position
     * @param length line length
     */
    public static NoteBytesObject drawHLine(int x, int y, int length) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_DRAW_HLINE),
            new NoteBytesPair(Keys.X, x),
            new NoteBytesPair(Keys.Y, y),
            new NoteBytesPair(Keys.LENGTH, length)
        });
    }

    /**
     * Draw vertical line
     * @param x horizontal position
     * @param y starting vertical position
     * @param length line length
     */
    public static NoteBytesObject drawVLine(int x, int y, int length) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_DRAW_VLINE),
            new NoteBytesPair(Keys.X, x),
            new NoteBytesPair(Keys.Y, y),
            new NoteBytesPair(Keys.LENGTH, length)
        });
    }

    // ===== FILL OPERATIONS =====
    
    /**
     * Fill rectangular region with character
     * @param region the region to fill
     * @param cp Unicode code point to fill with
     * @param style text style
     */
    public static NoteBytesObject fillRegion(TerminalRectangle region, int cp, TextStyle style) {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, TERMINAL_FILL_REGION),
            new NoteBytesPair(Keys.REGION, region.toNoteBytes()),
            new NoteBytesPair(CODE_POINT, cp),
            new NoteBytesPair(Keys.STYLE, style.toNoteBytes())
        });
    }

   

    /**
     * Fill rectangular region with character
     * @param region the region to fill
     * @param fillChar character to fill with
     * @param style text style
     */
    public static NoteBytesObject fillRegion(TerminalRectangle region, char fillChar, TextStyle style) {
        return fillRegion(region, (int) fillChar, style);
    }

}