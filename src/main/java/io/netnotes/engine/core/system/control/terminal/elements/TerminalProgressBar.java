package io.netnotes.engine.core.system.control.terminal.elements;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.containers.TerminalContainerHandle;

/**
 * ProgressBar - Terminal progress bar renderer
 * 
 * Renders progress bars in various styles:
 * - |10%|=====-------| (default)
 * - [████░░░░░░] 40%
 * - ▓▓▓▓▓▓░░░░ 60%
 * - >>>>>>>--- 70%
 */
public class TerminalProgressBar {
    
    private final TerminalContainerHandle terminal;
    private final int row;
    private final int col;
    private final int width;
    private final Style style;
    
    private double currentPercent = 0;
    
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
    
    /**
     * Update progress (0-100)
     */
    public CompletableFuture<Void> update(double percent) {
        percent = Math.max(0, Math.min(100, percent));
        this.currentPercent = percent;
        return render();
    }
    
    /**
     * Update progress with message
     */
    public CompletableFuture<Void> update(double percent, String message) {
        return update(percent)
            .thenCompose(v -> terminal.printAt(row + 1, col, message));
    }
    
    /**
     * Render the progress bar
     */
    private CompletableFuture<Void> render() {
        String bar = switch (style) {
            case CLASSIC -> renderClassic();
            case BLOCKS -> renderBlocks();
            case SHADED -> renderShaded();
            case ARROWS -> renderArrows();
        };
        
        return terminal.printAt(row, col, bar);
    }
    
    private String renderClassic() {
        // |10%|=====-------|
        int barWidth = width - 6; // Account for |XX%| and outer bars
        int filled = (int) ((currentPercent / 100.0) * barWidth);
        int empty = barWidth - filled;
        
        return String.format("|%2d%%|%s%s|",
            currentPercent,
            "=".repeat(filled),
            "-".repeat(empty)
        );
    }
    
    private String renderBlocks() {
        // [████░░░░░░] 40%
        int barWidth = width - 6; // Account for [] and " XX%"
        int filled = (int) ((currentPercent / 100.0) * barWidth);
        int empty = barWidth - filled;
        
        return String.format("[%s%s] %2d%%",
            "█".repeat(filled),
            "░".repeat(empty),
            currentPercent
        );
    }
    
    private String renderShaded() {
        // ▓▓▓▓▓▓░░░░ 60%
        int barWidth = width - 4; // Account for " XX%"
        int filled = (int) ((currentPercent / 100.0) * barWidth);
        int empty = barWidth - filled;
        
        return String.format("%s%s %2d%%",
            "▓".repeat(filled),
            "░".repeat(empty),
            currentPercent
        );
    }
    
    private String renderArrows() {
        // >>>>>>>--- 70%
        int barWidth = width - 4; // Account for " XX%"
        int filled = (int) ((currentPercent / 100.0) * barWidth);
        int empty = barWidth - filled;
        
        return String.format("%s%s %2d%%",
            ">".repeat(filled),
            "-".repeat(empty),
            currentPercent
        );
    }
    
    /**
     * Complete the progress bar (set to 100%)
     */
    public CompletableFuture<Void> complete() {
        return update(100);
    }
    
    /**
     * Complete with message
     */
    public CompletableFuture<Void> complete(String message) {
        return update(100, message);
    }
    
    /**
     * Clear the progress bar area
     */
    public CompletableFuture<Void> clear() {
        return terminal.clearLine(row)
            .thenCompose(v -> terminal.clearLine(row + 1));
    }
}
