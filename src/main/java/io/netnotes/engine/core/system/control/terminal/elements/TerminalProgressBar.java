package io.netnotes.engine.core.system.control.terminal.elements;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.ClientRenderManager;
import io.netnotes.engine.core.system.control.terminal.ClientRenderManager.RenderElement;
import io.netnotes.engine.core.system.control.terminal.ClientRenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.ClientRenderManager.Renderable;
import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.terminal.TextStyle;

/**
 * ProgressBar - Terminal progress bar renderer
 * 
 * DUAL INTERFACE:
 * 1. As Renderable - can be active screen in ClientRenderManager (for long operations)
 * 2. As RenderElement - can be part of another component's RenderState
 * 
 * Renders progress bars in various styles:
 * - |10%|=====-------| (CLASSIC)
 * - [████░░░░░░] 40%  (BLOCKS)
 * - ▓▓▓▓▓▓░░░░ 60%    (SHADED)
 * - >>>>>>>--- 70%    (ARROWS)
 * 
 * Usage as Renderable (for long-running operations):
 * <pre>
 * TerminalProgressBar progress = new TerminalProgressBar(terminal, 10, 20, 50);
 * renderManager.setActive(progress);
 * 
 * // Update from worker thread
 * progress.updatePercent(25);
 * renderManager.invalidate();
 * </pre>
 * 
 * Usage as RenderElement (part of larger UI):
 * <pre>
 * TerminalProgressBar progress = new TerminalProgressBar(terminal, 5, 10, 40);
 * 
 * RenderState.builder()
 *     .add(createHeader())
 *     .add(progress.asRenderElement())  // Embed in larger UI
 *     .add(createFooter())
 *     .build();
 * </pre>
 * 
 * Legacy push-based rendering:
 * <pre>
 * progress.update(50).thenAccept(v -> {
 *     // Progress rendered
 * });
 * </pre>
 */
public class TerminalProgressBar implements Renderable {
    
    private final TerminalContainerHandle terminal;
    private final int row;
    private final int col;
    private final int width;
    private final Style style;
    
    // Mutable state (for updates)
    private volatile double currentPercent = 0;
    private volatile String currentMessage = null;
    
    // For pull-based rendering invalidation
    private ClientRenderManager renderManager = null;
    
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
        this.renderManager = terminal.getRenderManager();
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    /**
     * Get render state for pull-based rendering
     * Thread-safe read-only state capture
     */
    @Override
    public RenderState getRenderState() {
        return RenderState.builder()
            .add(asRenderElement())
            .build();
    }
    
    /**
     * Convert this ProgressBar to a RenderElement
     * Allows embedding in other components' RenderState
     */
    public RenderElement asRenderElement() {
        // Capture current state for rendering
        final double percent = this.currentPercent;
        final String message = this.currentMessage;
        final int currentRow = this.row;
        final int currentCol = this.col;
        final int currentWidth = this.width;
        final Style currentStyle = this.style;
        
        return (terminal) -> {
            // Render progress bar
            String bar = generateBarString(percent, currentWidth, currentStyle);
            terminal.printAt(currentRow, currentCol, bar, TextStyle.NORMAL);
            
            // Render message if present
            if (message != null && !message.isEmpty()) {
                // Clear the line first to remove old message
                int messageRow = currentRow + 1;
                terminal.clearLine(messageRow);
                terminal.printAt(messageRow, currentCol, message, TextStyle.NORMAL);
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
            
            // If we're the active renderable, invalidate
            if (renderManager != null && renderManager.getActive() == this) {
                renderManager.invalidate();
            }
        }
    }
    
    /**
     * Update progress with message
     */
    public synchronized void updatePercent(double percent, String message) {
        this.currentMessage = message;
        updatePercent(percent);
    }
    
    /**
     * Clear the message
     */
    public synchronized void clearMessage() {
        if (this.currentMessage != null) {
            this.currentMessage = null;
            
            if (renderManager != null && renderManager.getActive() == this) {
                renderManager.invalidate();
            }
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
        updatePercent(0);
        clearMessage();
    }
    
    // ===== LEGACY PUSH-BASED RENDERING =====
    
    /**
     * Legacy update method with immediate rendering
     * Uses push-based rendering for backward compatibility
     * 
     * @deprecated Use updatePercent() with pull-based rendering instead
     */
    public CompletableFuture<Void> update(double percent) {
        updatePercent(percent);
        return renderImmediate();
    }
    
    /**
     * Legacy update with message
     * 
     * @deprecated Use updatePercent(percent, message) with pull-based rendering instead
     */
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
         }else{
            return terminal.printAt(row, col, bar, TextStyle.NORMAL);
         }
    }
    
    /**
     * Clear the progress bar area (legacy)
     */
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
}