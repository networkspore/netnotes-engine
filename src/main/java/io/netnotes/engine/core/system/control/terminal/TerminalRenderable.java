package io.netnotes.engine.core.system.control.terminal;

import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.terminal.layout.TerminalGroupCallbackEntry;
import io.netnotes.engine.core.system.control.terminal.layout.TerminalLayoutCallback;
import io.netnotes.engine.core.system.control.terminal.layout.TerminalLayoutContext;
import io.netnotes.engine.core.system.control.terminal.layout.TerminalLayoutData;
import io.netnotes.engine.core.system.control.terminal.layout.TerminalLayoutGroup;
import io.netnotes.engine.core.system.control.terminal.layout.TerminalLayoutGroupCallback;
import io.netnotes.engine.core.system.control.ui.Point2D;
import io.netnotes.engine.core.system.control.ui.Renderable;
import io.netnotes.engine.core.system.control.ui.SpatialRegionPool;

/**
 * TerminalRenderable - Abstract base class for terminal renderables
 * 
 * Provides terminal-specific rendering capabilities on top of base Renderable
 * 
 * HIERARCHY:
 * - Renderable<B, R, S> - Base class with state machine, events, invalidation
 * - TerminalRenderable - Terminal-specific rendering (this class)
 * - TerminalScreen - Application screens (menus, forms, etc.)
 * - Terminal UI Components - Buttons, text boxes, etc.
 * 
 * ARCHITECTURE:
 * - Uses TerminalRectangle (2D) as spatial region type
 * - Integrates with TerminalBatchBuilder for rendering
 * - Provides terminal-specific helper methods (x,y operations)
 * - Maintains consistency with base Renderable damage tracking
 * 
 * COORDINATE SYSTEM:
 * - x = horizontal position (0 = left)
 * - y = vertical position (0 = top)
 * - All methods use (x, y) parameter ordering
 * 
 * BOUNDARY ENFORCEMENT:
 * - All rendering operations respect component bounds
 * - Operations are automatically clipped/clamped based on mode
 * - Prevents components from affecting areas outside their bounds
    //<B,P,S,LC,LD,LCB,GCB,G,R>
*/
public abstract class TerminalRenderable extends Renderable<
    TerminalBatchBuilder,
    Point2D,
    TerminalRectangle,
    TerminalLayoutContext,
    TerminalLayoutData,
    TerminalLayoutCallback,
    TerminalLayoutGroupCallback,
    TerminalGroupCallbackEntry,
    TerminalRenderable.TerminalGroupStateEntry,
    TerminalLayoutGroup,
    TerminalRenderable
> {


    private boolean clampCursor = true;          // Default to clamping cursor
    
    /**
     * Constructor
     * 
     * @param name Renderable name for debugging
     */
    protected TerminalRenderable(String name) {
        super(name, TerminalRectanglePool.getInstance());  
    }

    @Override
    public SpatialRegionPool<TerminalRectangle> getRegionPool() {
        return regionPool;
    }

    @Override
    protected void setupEventHandlers() { }
    
    @Override
    protected void setupStateTransitions() {
        // No custom transitions
    }
    
    // ===== CLIP MODE CONTROL =====
    

    public void setClampCursor(boolean clamp) {
        this.clampCursor = clamp;
    }
    
    public boolean isClampCursor() {
        return clampCursor;
    }
    
    // ===== TERMINAL-SPECIFIC HELPERS (x,y convention) =====
  
    /**
     * Get x coordinate (left edge) - local to this renderable
     */
    protected int getX() {
        return region.getX();
    }
    
    /**
     * Get y coordinate (top edge) - local to this renderable
     */
    protected int getY() {
        return region.getY();
    }
    
    /**
     * Get center y offset within this renderable's bounds (half-height)
     */
    protected int getCenterYOffset() {
        return region.getHeight() / 2;
    }
    
    /**
     * Get center x offset within this renderable's bounds (half-width)
     */
    protected int getCenterXOffset() {
        return region.getWidth() / 2;
    }
    
    /**
     * Center text horizontally
     * 
     * @param text Text to center
     * @return X position for centered text
     */
    protected int centerTextHorizontal(String text) {
        return Math.max(0, (region.getWidth() - text.length()) / 2);
    }
    
    /**
     * Check if position is within bounds (local coordinates)
     * 
     * @param x horizontal position (relative to this renderable)
     * @param y vertical position (relative to this renderable)
     */
    protected boolean isInBounds(int x, int y) {
        return x >= 0 && x < region.getWidth() && 
               y >= 0 && y < region.getHeight();
    }
    
    /**
     * Clamp x to valid range
     */
    protected int clampX(int x) {
        return Math.max(0, Math.min(x, region.getWidth() - 1));
    }
    
    /**
     * Clamp y to valid range
     */
    protected int clampY(int y) {
        return Math.max(0, Math.min(y, region.getHeight() - 1));
    }
    
    /**
     * Convert local x,y to absolute coordinates
     * Returns Point2D in absolute screen space
     */
    protected Point2D toAbsolute(int x, int y) {
        TerminalRectangle absReg = getAbsoluteRegion();
        Point2D result = new Point2D(absReg.getX() + x, absReg.getY() + y);
        regionPool.recycle(absReg);
        return result;
    }

    public int getAbsoluteX(){
        return getRegion().getAbsoluteX();
    }

    public int getAbsoluteY(){
        return getRegion().getAbsoluteY();
    }

    
    /**
     * Convert local x to absolute coordinate
     */
    private int toAbsoluteX(int localX) {
        return localX + getAbsoluteX();
    }
    
    /**
     * Convert local y to absolute coordinate
     */
    private int toAbsoluteY(int localY) {
        return localY + getAbsoluteY();
    }
    
    /**
     * Invalidate a specific region in terminal coordinates
     * 
     * @param x Starting x (relative to this renderable)
     * @param y Starting y (relative to this renderable)
     * @param width Width in columns
     * @param height Height in rows
     */
    protected void invalidateRegion(int x, int y, int width, int height) {
        TerminalRectangle region = regionPool.obtain();
        region.set(x, y, width, height);
        invalidate(region);
        regionPool.recycle(region);
    }
    
    /**
     * Invalidate a single cell
     * 
     * @param x horizontal position
     * @param y vertical position
     */
    protected void invalidateCell(int x, int y) {
        invalidateRegion(x, y, 1, 1);
    }
    
    /**
     * Invalidate a horizontal line
     * 
     * @param x Starting x
     * @param y vertical position
     * @param length Number of columns
     */
    protected void invalidateHLine(int x, int y, int length) {
        invalidateRegion(x, y, length, 1);
    }
    
    /**
     * Invalidate a vertical line
     * 
     * @param x horizontal position
     * @param y Starting y
     * @param length Number of rows
     */
    protected void invalidateVLine(int x, int y, int length) {
        invalidateRegion(x, y, 1, length);
    }
    
    // ===== 2D SPATIAL PROPERTIES =====
    
    /**
     * Get width
     */
    public int getWidth() {
        return region.getWidth();
    }
    
    /**
     * Get height
     */
    public int getHeight() {
        return region.getHeight();
    }
    
    /**
     * Get right edge (x + width)
     */
    public int getRight() {
        TerminalRectangle rect = region;
        return rect.getRight();
    }
    
    /**
     * Get bottom edge (y + height)
     */
    public int getBottom() {
        TerminalRectangle rect = region;
        return rect.getBottom();
    }
    
    /**
     * Get center X
     */
    public int getCenterX() {
        TerminalRectangle rect = region;
        return rect.getCenterX();
    }
    
    /**
     * Get center Y
     */
    public int getCenterY() {
        TerminalRectangle rect = region;
        return rect.getCenterY();
    }
    
    // ===== 2D CONVENIENCE SETTERS =====
    // These create temporary regions and delegate to setRegion()
    // NOT zero-allocation - use sparingly in hot paths
    
    /**
     * Get base region for mutations - requested if pending, else allocated
     * Returns null if hidden (mutations are no-op)
     * Caller must recycle returned region after use
     */
    private TerminalRectangle getMutationBase() {
        if (isHidden()) {
            return null;  // Hidden - no mutations allowed
        }
        
        return hasRequestedRegion() 
            ? getRequestedRegion().copy()
            : region.copy();
    }


    
    /**
     * Set X position (left edge)
     * Convenience method - creates temporary region
     */
    public void setX(int x) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.setX(x);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    /**
     * Set Y position (top edge)
     * Convenience method - creates temporary region
     */
    public void setY(int y) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.setY(y);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    /**
     * Set position (x, y)
     * Convenience method - creates temporary region
     */
    public void setPosition(int x, int y) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.setPosition(x, y);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    /**
     * Set width
     * Convenience method - creates temporary region
     */
    public void setWidth(int width) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.setWidth(width);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    /**
     * Set height
     * Convenience method - creates temporary region
     */
    public void setHeight(int height) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.setHeight(height);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    /**
     * Set size (width, height)
     * Convenience method - creates temporary region
     */
    public void setSize(int width, int height) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.setSize(width, height);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    /**
     * Set complete bounds
     * Convenience method - creates temporary region
     */
    public void setRegion(int x, int y, int width, int height) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.setBounds(x, y, width, height);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }

    public void setBounds(int x, int y, int width, int height){
        setRegion(x, y, width, height);
    }

    @Override
    public void setRegion(TerminalRectangle bounds) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.setBounds(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
        super.setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    /**
     * Translate by offset
     * Convenience method - creates temporary region
     */
    public void translate(int dx, int dy) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.translate(dx, dy);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    /**
     * Expand outward by amount (negative to shrink)
     * Convenience method - creates temporary region
     */
    public void expand(int dx, int dy) {
        TerminalRectangle base = getMutationBase();
        if (base == null) return;
        
        base.expand(dx, dy);
        setRegion(base);
        // Don't recycle - setRegion takes ownership
    }
    
    // ===== STRING REPRESENTATION =====
    
    /**
     * Get string representation with bounds
     */
    public String getBoundsString() {
        return String.format(
            "%s[x=%d, y=%d, w=%d, h=%d]",
            getName(), getX(), getY(), getWidth(), getHeight()
        );
    }
    
    /**
     * Get string representation with absolute bounds
     */
    public String getAbsoluteBoundsString() {

        String result = String.format("%s[absX=%d, absY=%d, w=%d, h=%d]",
            getName(), getAbsoluteX(), getAbsoluteY(), getWidth(), getHeight()
        );

        return result;
    }

    @Override
    protected TerminalGroupStateEntry createGroupStateEntry() {
        return new TerminalGroupStateEntry();
    }

    @Override
    protected TerminalGroupCallbackEntry createGroupCallbackEntry(TerminalLayoutGroupCallback groupCallback) {
        return new TerminalGroupCallbackEntry(groupCallback);
    }


    // ===== RENDERING COMMANDS WITH BOUNDARY ENFORCEMENT =====
    
    /**
     * Print text at position (local coordinates)
     * Automatically enforces boundaries based on clip mode
     */
    protected void printAt(TerminalBatchBuilder batch, int x, int y, String text) {
        printAt(batch, x, y, text, TextStyle.NORMAL);
    }
    
    protected void printAt(TerminalBatchBuilder batch, int x, int y, String text, TextStyle style) {
        if (!isEffectivelyVisible() || text.isEmpty()) return;
        if (y < 0 || y >= getHeight()) return;
        
        int absY = toAbsoluteY(y);
        int left = toAbsoluteX(Math.max(0, x));
        int right = toAbsoluteX(Math.min(getWidth(), x + text.length()));
        
        TerminalRectangle clip = batch.getCurrentClipRegion();
        if (clip != null) {
            if (absY < clip.getY() || absY >= clip.getY() + clip.getHeight()) return;
            left = Math.max(left, clip.getX());
            right = Math.min(right, clip.getX() + clip.getWidth());
        }
        
        if (right <= left) return;
        
        int startIdx = left - toAbsoluteX(x);
        int endIdx = right - toAbsoluteX(x);
        text = text.substring(Math.max(0, startIdx), Math.min(text.length(), endIdx));
        
        batch.printAt(left, absY, text, style);
    }
    
    /**
     * Clear a line at specified y position
     * NOTE: Converted to clearRegion to respect bounds
     */
    protected void clearLineAt(TerminalBatchBuilder batch, int y) {
        // Convert to clearRegion to properly enforce bounds
        clearRegion(batch, 0, y, getWidth(), 1);
    }

     protected void clear(TerminalBatchBuilder batch) {
        // Convert to clearRegion to properly enforce bounds
        clearRegion(batch, getX(), getY(), getWidth(), getHeight());
    }
    
    /**
     * Clear a rectangular region (local coordinates)
     */
    protected void clearRegion(TerminalBatchBuilder batch, int x, int y, int width, int height) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;
    
        int left = toAbsoluteX(Math.max(0, x));
        int top = toAbsoluteY(Math.max(0, y));
        int right = toAbsoluteX(Math.min(getWidth(), x + width));
        int bottom = toAbsoluteY(Math.min(getHeight(), y + height));
        
        TerminalRectangle clip = batch.getCurrentClipRegion();
        if (clip != null) {
            left = Math.max(left, clip.getX());
            top = Math.max(top, clip.getY());
            right = Math.min(right, clip.getX() + clip.getWidth());
            bottom = Math.min(bottom, clip.getY() + clip.getHeight());
        }
        
        if (right <= left || bottom <= top) return;
        
        TerminalRectangle region = regionPool.obtain();
        region.set(left, top, right - left, bottom - top, 0, 0);
        batch.clearRegion(region);
        regionPool.recycle(region);
    }
    
    /**
     * Clear a rectangular region using TerminalRectangle (local coordinates)
     */
    protected void clearRegion(TerminalBatchBuilder batch, TerminalRectangle region) {
        clearRegion(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight());
    }
    
    /**
     * Draw box (local coordinates)
     */
    protected void drawBox(TerminalBatchBuilder batch, int x, int y, int width, int height, String title, BoxStyle boxStyle) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;
        if (x < 0 || y < 0 || x + width > getWidth() || y + height > getHeight()) return;
        
        int absX = toAbsoluteX(x);
        int absY = toAbsoluteY(y);
        
        TerminalRectangle clip = batch.getCurrentClipRegion();
        if (clip != null) {
            if (absX < clip.getX() || absY < clip.getY() ||
                absX + width > clip.getX() + clip.getWidth() ||
                absY + height > clip.getY() + clip.getHeight()) return;
        }
        
        TerminalRectangle region = regionPool.obtain();
        region.set(absX, absY, width, height, 0, 0);
        batch.drawBox(region, title, boxStyle);
        regionPool.recycle(region);
    }
    
    protected void drawBox(TerminalBatchBuilder batch, int x, int y, int width, int height, BoxStyle boxStyle) {
        drawBox(batch, x, y, width, height, null, boxStyle);
    }
    
    /**
     * Draw box using TerminalRectangle (local coordinates)
     */
    protected void drawBox(TerminalBatchBuilder batch, TerminalRectangle region, String title, BoxStyle boxStyle) {
        drawBox(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight(), title, boxStyle);
    }
    
    protected void drawBox(TerminalBatchBuilder batch, TerminalRectangle region, BoxStyle boxStyle) {
        drawBox(batch, region, null, boxStyle);
    }
    
    /**
     * Draw horizontal line (local coordinates)
     */
    protected void drawHLine(TerminalBatchBuilder batch, int x, int y, int length) {
        if (!isEffectivelyVisible() || length <= 0 || y < 0 || y >= getHeight()) return;
        
        int absY = toAbsoluteY(y);
        int left = toAbsoluteX(Math.max(0, x));
        int right = toAbsoluteX(Math.min(getWidth(), x + length));
        
        TerminalRectangle clip = batch.getCurrentClipRegion();
        if (clip != null) {
            if (absY < clip.getY() || absY >= clip.getY() + clip.getHeight()) return;
            left = Math.max(left, clip.getX());
            right = Math.min(right, clip.getX() + clip.getWidth());
        }
        
        if (right <= left) return;
        
        batch.drawHLine(left, absY, right - left);
    }
    
    /**
     * Draw vertical line (local coordinates)
     */
    protected void drawVLine(TerminalBatchBuilder batch, int x, int y, int length) {
        if (!isEffectivelyVisible() || length <= 0 || x < 0 || x >= getWidth()) return;
        
        int absX = toAbsoluteX(x);
        int top = toAbsoluteY(Math.max(0, y));
        int bottom = toAbsoluteY(Math.min(getHeight(), y + length));
        
        TerminalRectangle clip = batch.getCurrentClipRegion();
        if (clip != null) {
            if (absX < clip.getX() || absX >= clip.getX() + clip.getWidth()) return;
            top = Math.max(top, clip.getY());
            bottom = Math.min(bottom, clip.getY() + clip.getHeight());
        }
        
        if (bottom <= top) return;
        
        batch.drawVLine(absX, top, bottom - top);
    }
    
    /**
     * Fill region with character (local coordinates)
     */
    protected void fillRegion(TerminalBatchBuilder batch, int x, int y, int width, int height, char fillChar, TextStyle style) {
        if (!isEffectivelyVisible() || width <= 0 || height <= 0) return;
        
        int left = toAbsoluteX(Math.max(0, x));
        int top = toAbsoluteY(Math.max(0, y));
        int right = toAbsoluteX(Math.min(getWidth(), x + width));
        int bottom = toAbsoluteY(Math.min(getHeight(), y + height));
        
        TerminalRectangle clip = batch.getCurrentClipRegion();
        if (clip != null) {
            left = Math.max(left, clip.getX());
            top = Math.max(top, clip.getY());
            right = Math.min(right, clip.getX() + clip.getWidth());
            bottom = Math.min(bottom, clip.getY() + clip.getHeight());
        }
        
        if (right <= left || bottom <= top) return;
        
        TerminalRectangle region = regionPool.obtain();
        region.set(left, top, right - left, bottom - top, 0, 0);
        batch.fillRegion(region, fillChar, style);
        regionPool.recycle(region);
    }
    
    /**
     * Fill region with character using TerminalRectangle (local coordinates)
     */
    protected void fillRegion(TerminalBatchBuilder batch, TerminalRectangle region, 
                             char fillChar, TextStyle style) {
        fillRegion(batch, region.getX(), region.getY(), region.getWidth(), region.getHeight(), 
                  fillChar, style);
    }
    
    /**
     * Move cursor (local coordinates)
     * Cursor is clamped to bounds if clampCursor is true
     */
    protected void moveCursor(TerminalBatchBuilder batch, int x, int y) {
        if (clampCursor) {
            x = clampX(x);
            y = clampY(y);
        }
        x = Math.max(0, Math.min(x, getWidth() - 1));
        y = Math.max(0, Math.min(y, getHeight() - 1));
        batch.moveCursor(toAbsoluteX(x), toAbsoluteY(y));
    }

    /**
     * Push clip region for nested clipping (local coordinates)
     */
    protected void pushClipRegion(TerminalBatchBuilder batch, int x, int y, int width, int height) {
        TerminalRectangle region = regionPool.obtain();
        region.set(toAbsoluteX(x), toAbsoluteY(y), width, height, 0, 0);
        batch.pushClipRegion(region);
        regionPool.recycle(region);
    }

    /**
     * Pop clip region
     */
    protected void popClipRegion(TerminalBatchBuilder batch) {
        batch.popClipRegion();
    }
    
    // ===== NON-POSITIONED OPERATIONS (pass-through) =====
    // These operations are not position-based and don't need boundary enforcement
    
    protected void print(TerminalBatchBuilder batch, String text) {
        batch.print(text);
    }
    
    protected void print(TerminalBatchBuilder batch, String text, TextStyle style) {
        batch.print(text, style);
    }
    
    protected void println(TerminalBatchBuilder batch, String text) {
        batch.println(text);
    }
    
    protected void println(TerminalBatchBuilder batch, String text, TextStyle style) {
        batch.println(text, style);
    }
    
    protected void showCursor(TerminalBatchBuilder batch) {
        batch.showCursor();
    }
    
    protected void hideCursor(TerminalBatchBuilder batch) {
        batch.hideCursor();
    }
    

    protected void clearLine(TerminalBatchBuilder batch) {
        clearRegion(batch, 0, 0, getWidth(), 1);
    }
    // ===== ABSTRACT METHODS =====
    
    /**
     * Subclasses implement to render their 2D content
     * Called with clip region already set in batch if using CLIP_REGION mode
     * 
     * The batch's coordinate system is in absolute screen space
     * Use the provided rendering methods which handle coordinate translation
     * 
     * All rendering coordinates are LOCAL to this renderable (0,0 = top-left of component)
     */
    @Override
    protected void renderSelf(TerminalBatchBuilder batch){}

    public class TerminalGroupStateEntry extends Renderable.GroupStateEntry<
        TerminalRenderable,
        TerminalGroupCallbackEntry,
        TerminalGroupStateEntry
    >{}
}