package io.netnotes.engine.core.system.control.terminal.elements;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderElement;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderState;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderable;
import io.netnotes.engine.core.system.control.terminal.TextStyle;

/**
 * TerminalProgressBar - REFACTORED for pull-based rendering
 * 
 * DUAL USAGE PATTERNS:
 * 
 * 1. STANDALONE RENDERABLE:
 *    TerminalProgressBar progress = new TerminalProgressBar(terminal, 10, 20, 50);
 *    progress.show(); // Becomes active renderable
 *    progress.updatePercent(50); // Auto-invalidates
 * 
 * 2. COMPONENT IN ANOTHER RENDERABLE:
 *    TerminalProgressBar progress = new TerminalProgressBar(terminal, 5, 10, 40)
 *        .withParent(this); // Set parent screen
 *    
 *    RenderState.builder()
 *        .add(progress.asRenderElement())
 *        .build();
 *    
 *    progress.updatePercent(50); // Invalidates parent
 * 
 * DIRTY FLAG TRACKING:
 * - Implements needsRender() and clearRenderFlag()
 * - Calls invalidate() after state changes
 * - Invalidates parent if component, terminal if standalone
 * 
 * Renders progress bars in various styles:
 * - |10%|=====-------| (CLASSIC)
 * - [████░░░░░░] 40%  (BLOCKS)
 * - ▓▓▓▓▓▓░░░░ 60%    (SHADED)
 * - >>>>>>>--- 70%    (ARROWS)
 */
public class TerminalProgressBar implements TerminalRenderable {
    
    private final TerminalContainerHandle terminal;
    private final int row;
    private final int col;
    private final int width;
    private final Style style;
    
    // Mutable state (for updates)
    private volatile double currentPercent = 0;
    private volatile String currentMessage = null;
    
    // DIRTY FLAG for rendering
    private volatile boolean needsRender = false;
    
    // PARENT RENDERABLE (for composition pattern)
    private TerminalRenderable parentRenderable = null;
    
    public enum Style {
        /** |10%|=====-------| */
        CLASSIC,
        
        /** [████░░░░░░] 40% */
        BLOCKS,
        
        /** ▓▓▓▓▓▓░░░░ 60% */
        SHADED,
        
        /** >>>>>>>--- 70% */
        ARROWS
    }
    
    public TerminalProgressBar(TerminalContainerHandle terminal, int row, int col, int width) {
        this(terminal, row, col, width, Style.CLASSIC);
    }
    
    public TerminalProgressBar(TerminalContainerHandle terminal, int row, int col, int width, Style style) {
        this.terminal = terminal;
        this.row = row;
        this.col = col;
        this.width = Math.max(10, width); // Minimum 10 chars
        this.style = style;
    }
    
    // ===== CONFIGURATION =====
    
    /**
     * Set parent renderable (for composition pattern)
     * When progress bar is part of another renderable, invalidate parent instead
     */
    public TerminalProgressBar withParent(TerminalRenderable parent) {
        this.parentRenderable = parent;
        return this;
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    /**
     * Get render state for pull-based rendering
     * Thread-safe read-only state capture
     */
    @Override
    public TerminalRenderState getRenderState() {
        return TerminalRenderState.builder()
            .add(asRenderElement())
            .build();
    }
    
    @Override
    public boolean needsRender() {
        return needsRender;
    }
    
    @Override
    public void clearRenderFlag() {
        this.needsRender = false;
    }
    
    /**
     * Mark as needing render
     * 
     * TWO CASES:
     * 1. Standalone: Invalidate terminal (we're the active renderable)
     * 2. Component: Invalidate parent (parent will re-render us)
     */
    private void invalidate() {
        this.needsRender = true;
        
        if (parentRenderable != null) {
            // We're a component - invalidate parent
            // Parent is responsible for calling terminal.invalidate()
            if (parentRenderable instanceof Invalidatable inv) {
                inv.invalidate();
            } else {
                // Fallback: invalidate terminal directly
                terminal.invalidate();
            }
        } else {
            // We're standalone - invalidate terminal directly
            terminal.invalidate();
        }
    }
    
    /**
     * Convert this ProgressBar to a RenderElement
     * Allows embedding in other components' RenderState
     */
    public TerminalRenderElement asRenderElement() {
        // Capture current state for rendering (thread-safe snapshot)
        final double percent = this.currentPercent;
        final String message = this.currentMessage;
        final int currentRow = this.row;
        final int currentCol = this.col;
        final int currentWidth = this.width;
        final Style currentStyle = this.style;
        
        return (batch) -> {
            // Render progress bar
            String bar = generateBarString(percent, currentWidth, currentStyle);
            batch.printAt(currentRow, currentCol, bar, TextStyle.NORMAL);
            
            // Render message if present
            if (message != null && !message.isEmpty()) {
                // Clear the line first to remove old message
                int messageRow = currentRow + 1;
                batch.clearLine(messageRow);
                batch.printAt(messageRow, currentCol, message, TextStyle.NORMAL);
            }
        };
    }
    
    /**
     * Generate progress bar string based on style
     */
    private String generateBarString(double percent, int width, Style style) {
        // Clamp percent to valid range
        int percentInt = (int) Math.max(0, Math.min(100, percent));
        
        return switch (style) {
            case CLASSIC -> generateClassic(percentInt, width);
            case BLOCKS -> generateBlocks(percentInt, width);
            case SHADED -> generateShaded(percentInt, width);
            case ARROWS -> generateArrows(percentInt, width);
        };
    }
    
    private String generateClassic(int percent, int width) {
        // |10%|=====-------|
        int barWidth = width - 6; // Account for |XX%| and outer bars
        if (barWidth < 1) barWidth = 1;
        
        int filled = (int) ((percent / 100.0) * barWidth);
        int empty = barWidth - filled;
        
        return String.format("|%2d%%|%s%s|",
            percent,
            "=".repeat(Math.max(0, filled)),
            "-".repeat(Math.max(0, empty))
        );
    }
    
    private String generateBlocks(int percent, int width) {
        // [████░░░░░░] 40%
        int barWidth = width - 6; // Account for [] and " XX%"
        if (barWidth < 1) barWidth = 1;
        
        int filled = (int) ((percent / 100.0) * barWidth);
        int empty = barWidth - filled;
        
        return String.format("[%s%s] %2d%%",
            "█".repeat(Math.max(0, filled)),
            "░".repeat(Math.max(0, empty)),
            percent
        );
    }
    
    private String generateShaded(int percent, int width) {
        // ▓▓▓▓▓▓░░░░ 60%
        int barWidth = width - 4; // Account for " XX%"
        if (barWidth < 1) barWidth = 1;
        
        int filled = (int) ((percent / 100.0) * barWidth);
        int empty = barWidth - filled;
        
        return String.format("%s%s %2d%%",
            "▓".repeat(Math.max(0, filled)),
            "░".repeat(Math.max(0, empty)),
            percent
        );
    }
    
    private String generateArrows(int percent, int width) {
        // >>>>>>>--- 70%
        int barWidth = width - 4; // Account for " XX%"
        if (barWidth < 1) barWidth = 1;
        
        int filled = (int) ((percent / 100.0) * barWidth);
        int empty = barWidth - filled;
        
        return String.format("%s%s %2d%%",
            ">".repeat(Math.max(0, filled)),
            "-".repeat(Math.max(0, empty)),
            percent
        );
    }
    
    // ===== STATE UPDATE METHODS =====
    
    /**
     * Update progress percentage (0-100)
     * Thread-safe setter
     * 
     * For pull-based rendering, this just updates state and invalidates.
     * The ClientRenderManager will pull the new state on next render cycle.
     */
    public synchronized void updatePercent(double percent) {
        double clampedPercent = Math.max(0, Math.min(100, percent));
        
        if (this.currentPercent != clampedPercent) {
            this.currentPercent = clampedPercent;
            invalidate();
        }
    }
    
    /**
     * Update progress with message
     */
    public synchronized void updatePercent(double percent, String message) {
        boolean changed = false;
        
        double clampedPercent = Math.max(0, Math.min(100, percent));
        if (this.currentPercent != clampedPercent) {
            this.currentPercent = clampedPercent;
            changed = true;
        }
        
        if (!java.util.Objects.equals(this.currentMessage, message)) {
            this.currentMessage = message;
            changed = true;
        }
        
        if (changed) {
            invalidate();
        }
    }
    
    /**
     * Set message without changing percent
     */
    public synchronized void setMessage(String message) {
        if (!java.util.Objects.equals(this.currentMessage, message)) {
            this.currentMessage = message;
            invalidate();
        }
    }
    
    /**
     * Clear the message
     */
    public synchronized void clearMessage() {
        if (this.currentMessage != null) {
            this.currentMessage = null;
            invalidate();
        }
    }
    
    /**
     * Complete the progress (set to 100%)
     */
    public void complete() {
        updatePercent(100);
    }
    
    /**
     * Complete with message
     */
    public void complete(String message) {
        updatePercent(100, message);
    }
    
    /**
     * Reset to 0%
     */
    public void reset() {
        synchronized (this) {
            boolean changed = false;
            
            if (this.currentPercent != 0) {
                this.currentPercent = 0;
                changed = true;
            }
            
            if (this.currentMessage != null) {
                this.currentMessage = null;
                changed = true;
            }
            
            if (changed) {
                invalidate();
            }
        }
    }
    
    // ===== SHOW (for standalone use) =====
    
    /**
     * Show as standalone progress bar (becomes active renderable)
     */
    public void show() {
        terminal.setRenderable(this);
        invalidate();
    }
    
    // ===== LEGACY PUSH-BASED RENDERING =====
    
    /**
     * Legacy update method with immediate rendering
     * Uses push-based rendering for backward compatibility
     * 
     * @deprecated Use updatePercent() with pull-based rendering instead
     */
    @Deprecated
    public CompletableFuture<Void> update(double percent) {
        updatePercent(percent);
        return renderImmediate();
    }
    
    /**
     * Legacy update with message
     * 
     * @deprecated Use updatePercent(percent, message) with pull-based rendering instead
     */
    @Deprecated
    public CompletableFuture<Void> update(double percent, String message) {
        updatePercent(percent, message);
        return renderImmediate();
    }
    
    /**
     * Immediate push-based rendering (legacy)
     */
    private CompletableFuture<Void> renderImmediate() {
        String bar = generateBarString(currentPercent, width, style);

        if (currentMessage != null && !currentMessage.isEmpty()) {
            return terminal.executeBatch(
                terminal.batch()
                    .printAt(row, col, bar, TextStyle.NORMAL)
                    .clearLine(row + 1)
                    .printAt(row + 1, col, currentMessage, TextStyle.NORMAL)
            );
        } else {
            return terminal.printAt(row, col, bar, TextStyle.NORMAL);
        }
    }
    
    /**
     * Clear the progress bar area (legacy)
     */
    @Deprecated
    public CompletableFuture<Void> clear() {
        return terminal.executeBatch(terminal.batch()
            .clearLine(row)
            .clearLine(row + 1));
    }
    
    // ===== GETTERS =====
    
    public double getCurrentPercent() {
        return currentPercent;
    }
    
    public String getCurrentMessage() {
        return currentMessage;
    }
    
    public int getRow() {
        return row;
    }
    
    public int getCol() {
        return col;
    }
    
    public int getWidth() {
        return width;
    }
    
    public Style getStyle() {
        return style;
    }
    
    /**
     * Check if complete
     */
    public boolean isComplete() {
        return currentPercent >= 100;
    }
    
    /**
     * Get progress as fraction (0.0 to 1.0)
     */
    public double getFraction() {
        return currentPercent / 100.0;
    }
    
    // ===== HELPER INTERFACE =====
    
    
}