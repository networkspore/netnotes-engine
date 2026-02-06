package io.netnotes.engine.core.system.control.terminal;

import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.ui.BatchBuilder;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * TerminalBatchBuilder - Build atomic batches of terminal commands with clip region support
 * 
 * NOTE: This builder manages which commands are included based on clip regions.
 * It does NOT modify command content - boundary enforcement happens at the TerminalRenderable level.
 * 
 * Coordinate System: Uses x,y where:
 *   x = horizontal position (0 = left)
 *   y = vertical position (0 = top)
 *   
 * All parameters follow the convention: x before y
 */
public class TerminalBatchBuilder extends BatchBuilder<TerminalRectangle>{
    
    public TerminalBatchBuilder() {
        super();
    }
    
    @Override
    public void addCommand(NoteBytesMap cmd) {
        commands.add(cmd.toNoteBytes());
    }
    
    @Override
    public void addCommand(NoteBytes cmd) {
        commands.add(cmd);
    }
    
    // ===== COMMAND FILTERING METHODS =====
    // These methods filter which commands get added to the batch based on clip region.
    // Actual boundary enforcement (clamping/clipping of command content) happens in TerminalRenderable.
    
    /**
     * Clear screen (always executes, not filtered)
     */
    public void clear() {
        NoteBytesObject cmd = TerminalCommands.clear();
        addCommand(cmd);
    }
    
    /**
     * Print text (not position-based, not filtered)
     */
    public void print(String text) {
        print(text, TextStyle.NORMAL);
    }
    
    public void print(String text, TextStyle style) {
        NoteBytesObject cmd = TerminalCommands.print(text, style);
        addCommand(cmd);
    }
    
    /**
     * Print line (not position-based, not filtered)
     */
    public void println(String text) {
        println(text, TextStyle.NORMAL);
    }
    
    public void println(String text, TextStyle style) {
        NoteBytesObject cmd = TerminalCommands.println(text, style);
        addCommand(cmd);
    }
    
    /**
     * Print at position - CHECKS CLIP REGION
     * Only adds command if position intersects clip region.
     * TerminalRenderable will handle actual text clipping/clamping.
     */
    public void printAt(int x, int y, String text) {
        printAt(x, y, text, TextStyle.NORMAL);
    }
    
    public void printAt(int x, int y, String text, TextStyle style) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Skip if position is completely outside clip region
            if (y < clip.getY() || y >= clip.getY() + clip.getHeight()) {
                return;
            }
            
            if (x >= clip.getX() + clip.getWidth()) {
                return;
            }
            
            // Check if any part of the text would be visible
            int endX = x + text.length();
            if (endX <= clip.getX()) {
                return;  // Text ends before clip region starts
            }
        }
        
        // Add command - TerminalRenderable will handle boundary enforcement
        addCommand(TerminalCommands.printAt(x, y, text, style));
    }
    
    /**
     * Print text centered vertically within a region
     */
    public void printAtCenterY(TerminalRectangle region, int x, String text) {
        NoteBytesObject cmd = TerminalCommands.printAtCenterY(region, x, text);
        addCommand(cmd);
    }
    
    /**
     * Print text centered horizontally within a region
     */
    public void printAtCenterX(TerminalRectangle region, int y, String text) {
        NoteBytesObject cmd = TerminalCommands.printAtCenterX(region, y, text);
        addCommand(cmd);
    }
    
    /**
     * Print text centered both horizontally and vertically within a region
     */
    public void printAtCenter(TerminalRectangle region, String text) {
        NoteBytesObject cmd = TerminalCommands.printAtCenter(region, text);
        addCommand(cmd);
    }
    
    /**
     * Move cursor (not filtered - cursor operations are global)
     */
    public void moveCursor(int x, int y) {
        NoteBytesObject cmd = TerminalCommands.moveCursor(x, y);
        addCommand(cmd);
    }
    
    public void showCursor() {
        NoteBytesObject cmd = TerminalCommands.showCursor();
        addCommand(cmd);
    }
    
    public void hideCursor() {
        NoteBytesObject cmd = TerminalCommands.hideCursor();
        addCommand(cmd);
    }
    
    /**
     * Clear line at cursor (not position-based, not filtered)
     */
    public void clearLine() {
        NoteBytesObject cmd = TerminalCommands.clearLine();
        addCommand(cmd);
    }
    
    /**
     * Clear specific line - CHECKS CLIP REGION
     * TerminalRenderable will enforce that clear only affects its bounds.
     */
    public void clearLineAt(int y) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Skip if line is outside clip region
            if (y < clip.getY() || y >= clip.getY() + clip.getHeight()) {
                return;
            }
        }
        
        addCommand(TerminalCommands.clearLineAt(y));
    }
    
    /**
     * Clear rectangular region - CHECKS CLIP REGION
     * TerminalRenderable will clamp the region to its bounds.
     */
    public void clearRegion(TerminalRectangle region) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Check if region intersects clip region at all
            if (!region.intersects(clip)) {
                return;
            }
        }
        
        addCommand(TerminalCommands.clearRegion(region));
    }

    /**
     * Draw box - CHECKS CLIP REGION
     * TerminalRenderable will handle boundary enforcement of box drawing.
     */
    public void drawBox(TerminalRectangle region, String title, BoxStyle boxStyle) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Check if box intersects clip region
            if (!region.intersects(clip)) {
                return;
            }
        }
        title = (title != null && !title.isEmpty()) ? title : "";
        addCommand(TerminalCommands.drawBox(region, title, boxStyle));
    }
    
    /**
     * Draw box (no title)
     */
    public void drawBox(TerminalRectangle region, BoxStyle boxStyle) {
        drawBox(region, null, boxStyle);
    }
    
 

    
    /**
     * Draw horizontal line - CHECKS CLIP REGION
     * TerminalRenderable will clamp line to its bounds.
     */
    public void drawHLine(int x, int y, int length) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Skip if line is outside clip region
            if (y < clip.getY() || y >= clip.getY() + clip.getHeight()) {
                return;
            }
            
            int endX = x + length;
            if (endX <= clip.getX() || x >= clip.getX() + clip.getWidth()) {
                return;
            }
        }
        
        addCommand(TerminalCommands.drawHLine(x, y, length));
    }

    /**
     * Draw vertical line - CHECKS CLIP REGION
     * TerminalRenderable will clamp line to its bounds.
     */
    public void drawVLine(int x, int y, int length) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Skip if line is outside clip region
            if (x < clip.getX() || x >= clip.getX() + clip.getWidth()) {
                return;
            }
            
            int endY = y + length;
            if (endY <= clip.getY() || y >= clip.getY() + clip.getHeight()) {
                return;
            }
        }
        
        addCommand(TerminalCommands.drawVLine(x, y, length));
    }

    /**
     * Fill region with character - CHECKS CLIP REGION
     * TerminalRenderable will clamp region to its bounds.
     */
    public void fillRegion(TerminalRectangle region, char fillChar, TextStyle style) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Check if region intersects clip region
            if (!region.intersects(clip)) {
                return;
            }
        }
        
        addCommand(TerminalCommands.fillRegion(region, fillChar, style));
    }
    
    /**
     * Fill region with character (code point version)
     */
    public void fillRegion(TerminalRectangle region, int cp, TextStyle style) {
        TerminalRectangle clip = getCurrentClipRegion();
        
        if (clip != null) {
            // Check if region intersects clip region
            if (!region.intersects(clip)) {
                return;
            }
        }
        
        addCommand(TerminalCommands.fillRegion(region, cp, style));
    }
    
    /**
     * Fill region with character (code point convenience method)
     */
    public void fillRegion(int x, int y, int width, int height, int cp, TextStyle style) {
        fillRegion(new TerminalRectangle(x, y, width, height), cp, style);
    }
}