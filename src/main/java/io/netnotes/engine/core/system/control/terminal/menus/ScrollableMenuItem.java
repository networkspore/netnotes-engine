package io.netnotes.engine.core.system.control.terminal.menus;


import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.terminal.TextStyle;

/**
 * ScrollableMenuItem - Handles rendering menu items with horizontal scroll
 * 
 * This enables menu items with very long text to be displayed in a fixed-width
 * menu without expanding the entire menu. When selected, the text can scroll
 * horizontally to reveal the full content.
 * 
 * Usage in MenuNavigator:
 * <pre>
 * ScrollableMenuItem itemRenderer = new ScrollableMenuItem(contentWidth);
 * itemRenderer.render(terminal, row, col, text, isSelected, horizontalOffset);
 * </pre>
 */
public class ScrollableMenuItem {
    
    private final int maxWidth;
    private final TextOverflowStrategy strategy;
    
    /**
     * Strategy for handling text that exceeds width
     */
    public enum TextOverflowStrategy {
        /** Simple truncate with ellipsis (default) */
        TRUNCATE,
        
        /** Horizontal scroll when selected, truncate when not */
        SCROLL_ON_SELECT,
        
        /** Always show scroll indicators if text is long */
        ALWAYS_SCROLL,
        
        /** Auto-scroll (marquee) when selected */
        MARQUEE
    }
    
    public ScrollableMenuItem(int maxWidth) {
        this(maxWidth, TextOverflowStrategy.SCROLL_ON_SELECT);
    }
    
    public ScrollableMenuItem(int maxWidth, TextOverflowStrategy strategy) {
        this.maxWidth = maxWidth;
        this.strategy = strategy;
    }
    
    /**
     * Render a menu item with overflow handling
     * 
     * @param terminal The terminal handle
     * @param row Row position
     * @param col Column position
     * @param text Full text to display
     * @param isSelected Whether this item is currently selected
     * @param scrollOffset Horizontal scroll offset (0 = start)
     * @param style Text style
     * @return Future that completes when rendering is done
     */
    public CompletableFuture<Void> render(
            TerminalContainerHandle terminal,
            int row,
            int col,
            String text,
            boolean isSelected,
            int scrollOffset,
            TextStyle style) {
        
        boolean needsScroll = text.length() > maxWidth;
        
        String displayText = switch (strategy) {
            case TRUNCATE -> truncateText(text, maxWidth);
            
            case SCROLL_ON_SELECT -> {
                if (isSelected && needsScroll) {
                    yield scrollText(text, maxWidth, scrollOffset);
                } else {
                    yield truncateText(text, maxWidth);
                }
            }
            
            case ALWAYS_SCROLL -> {
                if (needsScroll) {
                    yield scrollText(text, maxWidth, scrollOffset);
                } else {
                    yield text;
                }
            }
            
            case MARQUEE -> {
                if (isSelected && needsScroll) {
                    // For marquee, scrollOffset would be auto-incremented
                    yield marqueeText(text, maxWidth, scrollOffset);
                } else {
                    yield truncateText(text, maxWidth);
                }
            }
        };
        
        // Render the text
        CompletableFuture<Void> future = terminal.printAt(row, col, displayText, style);
        
        // Add scroll indicators if needed
        if (isSelected && needsScroll && shouldShowIndicators()) {
            future = future.thenCompose(v -> 
                renderScrollIndicators(terminal, row, col, text.length(), 
                    maxWidth, scrollOffset, style));
        }
        
        return future;
    }
    
    /**
     * Render a full-width highlight bar with scrollable text
     * This is the typical menu selection highlight
     */
    public CompletableFuture<Void> renderHighlighted(
            TerminalContainerHandle terminal,
            int row,
            int col,
            int highlightWidth,
            String text,
            int scrollOffset) {
        
        // Draw full-width highlight
        String highlightBar = " ".repeat(highlightWidth);
        
        return terminal.printAt(row, col, highlightBar, 
                TextStyle.INVERSE)
            .thenCompose(v -> {
                // Render text on top of highlight
                String displayText;
                boolean needsScroll = text.length() > highlightWidth - 4; // -4 for padding
                
                if (needsScroll && strategy != TextOverflowStrategy.TRUNCATE) {
                    displayText = "  " + scrollText(text, highlightWidth - 4, scrollOffset);
                } else {
                    displayText = "  " + truncateText(text, highlightWidth - 4);
                }
                
                return terminal.printAt(row, col, displayText, 
                    TextStyle.INVERSE);
            });
    }
    
    /**
     * Calculate maximum scroll offset for text
     */
    public int getMaxScrollOffset(String text) {
        return Math.max(0, text.length() - maxWidth);
    }
    
    /**
     * Get the max width configured for this renderer
     */
    public int getMaxWidth() {
        return maxWidth;
    }
    
    /**
     * Truncate text (public helper for non-selected items)
     */
    public String truncateText(String text, int width) {
        if (text.length() <= width) {
            return text;
        }
        return text.substring(0, Math.max(0, width - 3)) + "...";
    }
    
    /**
     * Calculate scroll offset to center a position in the viewport
     */
    public int centerScrollOffset(String text, int position) {
        int halfWidth = maxWidth / 2;
        int offset = position - halfWidth;
        return Math.max(0, Math.min(offset, getMaxScrollOffset(text)));
    }
    
    /**
     * Auto-increment scroll for marquee effect
     */
    public int incrementMarqueeOffset(String text, int currentOffset) {
        int maxOffset = text.length() + maxWidth; // Allow full wrap-around
        return (currentOffset + 1) % maxOffset;
    }
    
    // ===== PRIVATE HELPERS =====
    
    
    private String scrollText(String text, int width, int offset) {
        if (text.length() <= width) {
            return text;
        }
        
        int start = Math.min(offset, Math.max(0, text.length() - width));
        int end = Math.min(text.length(), start + width);
        return text.substring(start, end);
    }
    
    private String marqueeText(String text, int width, int offset) {
        if (text.length() <= width) {
            return text;
        }
        
        // Create a circular buffer effect
        String extended = text + "  "; // Add spacing between cycles
        int extendedLength = extended.length();
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < width; i++) {
            int pos = (offset + i) % extendedLength;
            result.append(extended.charAt(pos));
        }
        
        return result.toString();
    }
    
    private boolean shouldShowIndicators() {
        return strategy == TextOverflowStrategy.SCROLL_ON_SELECT ||
               strategy == TextOverflowStrategy.ALWAYS_SCROLL;
    }
    
    private CompletableFuture<Void> renderScrollIndicators(
            TerminalContainerHandle terminal,
            int row,
            int col,
            int textLength,
            int viewWidth,
            int scrollOffset,
            TextStyle style) {
        
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        
        // Left indicator (if scrolled right)
        if (scrollOffset > 0) {
            int indicatorCol = col - 2;
            future = future.thenCompose(v -> 
                terminal.printAt(row, indicatorCol, "◄", 
                    TextStyle.INFO));
        }
        
        // Right indicator (if more content to the right)
        if (scrollOffset + viewWidth < textLength) {
            int indicatorCol = col + viewWidth + 1;
            future = future.thenCompose(v -> 
                terminal.printAt(row, indicatorCol, "►", 
                    TextStyle.INFO));
        }
        
        return future;
    }
}