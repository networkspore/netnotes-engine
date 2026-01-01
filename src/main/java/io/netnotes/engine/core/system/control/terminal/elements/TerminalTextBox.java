package io.netnotes.engine.core.system.control.terminal.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.RenderManager.RenderElement;
import io.netnotes.engine.core.system.control.terminal.RenderManager.RenderState;
import io.netnotes.engine.core.system.control.terminal.RenderManager.Renderable;
import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;

/**
 * TextBox - Composable text box primitive for terminal UIs
 * 
 * DUAL INTERFACE:
 * 1. As Renderable - can be active screen in RenderManager
 * 2. As RenderElement - can be part of another component's RenderState
 * 
 * Usage as Renderable:
 * <pre>
 * TerminalTextBox textBox = TerminalTextBox.builder()
 *     .position(5, 20)
 *     .size(40, 10)
 *     .title("User Profile")
 *     .content("Name: John Doe", "Email: john@example.com")
 *     .build();
 * 
 * renderManager.setActive(textBox);  // Pull-based rendering
 * </pre>
 * 
 * Usage as RenderElement:
 * <pre>
 * RenderState.builder()
 *     .add(textBox.asRenderElement())  // Part of larger UI
 *     .build();
 * </pre>
 * 
 * Legacy push-based rendering still supported:
 * <pre>
 * textBox.render(terminal);  // Direct rendering
 * </pre>
 */
public class TerminalTextBox implements Renderable {
    
    // Position and dimensions
    private final int row;
    private final int col;
    private final int width;
    private final int height;
    
    // Styling
    private final BoxStyle boxStyle;
    private final TextStyle textStyle;
    
    // Title
    private final String title;
    private final TitlePlacement titlePlacement;
    private final TextStyle titleStyle;
    
    // Content
    private final List<String> contentLines;
    private final ContentAlignment contentAlignment;
    private final int padding;
    
    // Behavior
    private final boolean scrollable;
    private int scrollOffset;  // Mutable for scrolling
    
    // Text overflow handling
    private final TextOverflow textOverflow;
    private int horizontalScrollOffset;  // Mutable for scrolling
    
    private TerminalTextBox(Builder builder) {
        this.row = builder.row;
        this.col = builder.col;
        this.width = builder.width;
        this.height = builder.height;
        this.boxStyle = builder.boxStyle;
        this.textStyle = builder.textStyle;
        this.title = builder.title;
        this.titlePlacement = builder.titlePlacement;
        this.titleStyle = builder.titleStyle;
        this.contentLines = new ArrayList<>(builder.contentLines);
        this.contentAlignment = builder.contentAlignment;
        this.padding = builder.padding;
        this.scrollable = builder.scrollable;
        this.scrollOffset = builder.scrollOffset;
        this.textOverflow = builder.textOverflow;
        this.horizontalScrollOffset = builder.horizontalScrollOffset;
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
     * Convert this TextBox to a RenderElement
     * Allows using TextBox as part of another component's RenderState
     */
    public RenderElement asRenderElement() {
        // Capture current state for rendering
        final int currentRow = this.row;
        final int currentCol = this.col;
        final int currentWidth = this.width;
        final int currentHeight = this.height;
        final BoxStyle currentBoxStyle = this.boxStyle;
        final TextStyle currentTextStyle = this.textStyle;
        final String currentTitle = this.title;
        final TitlePlacement currentTitlePlacement = this.titlePlacement;
        final TextStyle currentTitleStyle = this.titleStyle;
        final List<String> currentContentLines = new ArrayList<>(this.contentLines);
        final ContentAlignment currentContentAlignment = this.contentAlignment;
        final int currentPadding = this.padding;
        final boolean currentScrollable = this.scrollable;
        final int currentScrollOffset = this.scrollOffset;
        final TextOverflow currentTextOverflow = this.textOverflow;
        final int currentHorizontalScrollOffset = this.horizontalScrollOffset;
        
        return (terminal, gen) -> {
            renderWithGeneration(
                terminal, gen,
                currentRow, currentCol, currentWidth, currentHeight,
                currentBoxStyle, currentTextStyle,
                currentTitle, currentTitlePlacement, currentTitleStyle,
                currentContentLines, currentContentAlignment, currentPadding,
                currentScrollable, currentScrollOffset,
                currentTextOverflow, currentHorizontalScrollOffset
            );
        };
    }
    
    /**
     * Internal rendering method used by both pull and push modes
     */
    private void renderWithGeneration(
            TerminalContainerHandle terminal, long gen,
            int row, int col, int width, int height,
            BoxStyle boxStyle, TextStyle textStyle,
            String title, TitlePlacement titlePlacement, TextStyle titleStyle,
            List<String> contentLines, ContentAlignment contentAlignment, int padding,
            boolean scrollable, int scrollOffset,
            TextOverflow textOverflow, int horizontalScrollOffset) {
        
        // Draw box border
        if (titlePlacement == TitlePlacement.BORDER_TOP && title != null) {
            terminal.drawBox(row, col, width, height, title, boxStyle, gen);
        } else {
            terminal.drawBox(row, col, width, height, "", boxStyle, gen);
        }
        
        // Draw title (if not in border)
        if (title != null && !title.isEmpty() && titlePlacement != TitlePlacement.BORDER_TOP) {
            renderTitleAtPosition(terminal, gen, row, col, width, height, 
                title, titlePlacement, titleStyle, contentAlignment);
        }
        
        // Draw content
        if (!contentLines.isEmpty()) {
            renderContentLines(terminal, gen, row, col, width, height,
                contentLines, contentAlignment, padding, textStyle,
                scrollable, scrollOffset, textOverflow, horizontalScrollOffset,
                titlePlacement, title);
        }
    }
    
    private void renderTitleAtPosition(
            TerminalContainerHandle terminal, long gen,
            int row, int col, int width, int height,
            String title, TitlePlacement placement, TextStyle style,
            ContentAlignment contentAlignment) {
        
        switch (placement) {
            case INSIDE_TOP -> {
                int titleRow = row + 1;
                int titleCol = calculateAlignment(col, width, title.length(), contentAlignment);
                terminal.printAt(titleRow, titleCol, title, style, gen);
            }
            case INSIDE_CENTER -> {
                int titleRow = row + height / 2;
                int titleCol = calculateAlignment(col, width, title.length(), ContentAlignment.CENTER);
                terminal.printAt(titleRow, titleCol, title, style, gen);
            }
            case INSIDE_BOTTOM -> {
                int titleRow = row + height - 2;
                int titleCol = calculateAlignment(col, width, title.length(), contentAlignment);
                terminal.printAt(titleRow, titleCol, title, style, gen);
            }
            case ABOVE_BOX -> {
                int titleRow = row - 1;
                int titleCol = calculateAlignment(col, width, title.length(), ContentAlignment.CENTER);
                terminal.printAt(titleRow, titleCol, title, style, gen);
            }
            case BELOW_BOX -> {
                int titleRow = row + height;
                int titleCol = calculateAlignment(col, width, title.length(), ContentAlignment.CENTER);
                terminal.printAt(titleRow, titleCol, title, style, gen);
            }
            case BORDER_TOP -> {
                // Already handled in renderBox
            }
        }
    }
    
    private void renderContentLines(
            TerminalContainerHandle terminal, long gen,
            int row, int col, int width, int height,
            List<String> contentLines, ContentAlignment alignment, int padding,
            TextStyle textStyle, boolean scrollable, int scrollOffset,
            TextOverflow textOverflow, int horizontalScrollOffset,
            TitlePlacement titlePlacement, String title) {
        
        // Calculate content area
        int contentStartRow = calculateContentStartRow(row, padding, titlePlacement, title);
        int contentHeight = calculateContentHeight(height, padding, titlePlacement, title);
        int contentWidth = width - (2 * padding) - 2; // -2 for borders
        
        // Determine which lines to show (with scrolling)
        int startLine = scrollable ? scrollOffset : 0;
        int endLine = Math.min(contentLines.size(), startLine + contentHeight);
        
        // Render each visible line
        for (int i = startLine; i < endLine; i++) {
            String line = contentLines.get(i);
            String displayLine = processLineOverflow(line, contentWidth, textOverflow, horizontalScrollOffset);
            
            int lineRow = contentStartRow + (i - startLine);
            int lineCol = calculateAlignment(col, width, displayLine.length(), alignment);
            
            terminal.printAt(lineRow, lineCol, displayLine, textStyle, gen);
        }
        
        // Render scroll indicators if scrollable
        if (scrollable && contentLines.size() > contentHeight) {
            if (scrollOffset > 0) {
                int indicatorCol = col + width - 3;
                terminal.printAt(contentStartRow, indicatorCol, "↑", TextStyle.INFO, gen);
            }
            
            if (endLine < contentLines.size()) {
                int indicatorCol = col + width - 3;
                int indicatorRow = contentStartRow + contentHeight - 1;
                terminal.printAt(indicatorRow, indicatorCol, "↓", TextStyle.INFO, gen);
            }
        }
        
        // Render horizontal scroll indicators if needed
        if (textOverflow == TextOverflow.SCROLL) {
            renderHorizontalScrollIndicators(terminal, gen, row, col, width,
                contentStartRow, contentHeight, contentWidth, 
                contentLines, horizontalScrollOffset);
        }
    }
    
    private void renderHorizontalScrollIndicators(
            TerminalContainerHandle terminal, long gen,
            int row, int col, int width,
            int contentStartRow, int contentHeight, int contentWidth,
            List<String> contentLines, int horizontalScrollOffset) {
        
        // Check if any line needs horizontal scrolling
        boolean hasLongLines = contentLines.stream()
            .anyMatch(line -> line.length() > contentWidth);
        
        if (!hasLongLines) {
            return;
        }
        
        // Show left indicator if scrolled right
        if (horizontalScrollOffset > 0) {
            int indicatorRow = contentStartRow + contentHeight / 2;
            int indicatorCol = col + 1;
            terminal.printAt(indicatorRow, indicatorCol, "◄", TextStyle.INFO, gen);
        }
        
        // Show right indicator if more content to the right
        boolean hasMoreRight = contentLines.stream()
            .anyMatch(line -> line.length() > horizontalScrollOffset + contentWidth);
        
        if (hasMoreRight) {
            int indicatorRow = contentStartRow + contentHeight / 2;
            int indicatorCol = col + width - 2;
            terminal.printAt(indicatorRow, indicatorCol, "►", TextStyle.INFO, gen);
        }
    }
    
    /**
     * Process line based on overflow strategy
     */
    private String processLineOverflow(String line, int availableWidth, 
                                      TextOverflow overflow, int horizontalOffset) {
        if (line.length() <= availableWidth) {
            return line;
        }
        
        return switch (overflow) {
            case WRAP -> line.substring(0, availableWidth);
            case TRUNCATE -> line.substring(0, Math.max(0, availableWidth - 3)) + "...";
            case SCROLL -> {
                int start = Math.min(horizontalOffset, 
                    Math.max(0, line.length() - availableWidth));
                int end = Math.min(line.length(), start + availableWidth);
                yield line.substring(start, end);
            }
            case TRUNCATE_START -> "..." + line.substring(Math.max(0, line.length() - availableWidth + 3));
            case FADE -> line.substring(0, availableWidth);
        };
    }
    
    private int calculateContentStartRow(int row, int padding, 
                                        TitlePlacement titlePlacement, String title) {
        int startRow = row + 1 + padding; // +1 for top border
        
        if (titlePlacement == TitlePlacement.INSIDE_TOP && title != null) {
            startRow += 1; // Title takes one line
        }
        
        return startRow;
    }
    
    private int calculateContentHeight(int height, int padding,
                                       TitlePlacement titlePlacement, String title) {
        int availableHeight = height - 2 - (2 * padding); // -2 for borders
        
        if ((titlePlacement == TitlePlacement.INSIDE_TOP || 
             titlePlacement == TitlePlacement.INSIDE_BOTTOM) && title != null) {
            availableHeight -= 1;
        }
        
        return Math.max(1, availableHeight);
    }
    
    private int calculateAlignment(int col, int width, int textLength, ContentAlignment alignment) {
        int availableWidth = width - 2; // Exclude borders
        
        return switch (alignment) {
            case LEFT -> col + 1 + padding;
            case CENTER -> col + (availableWidth - textLength) / 2;
            case RIGHT -> col + availableWidth - textLength - padding - 1;
        };
    }
    
    // ===== LEGACY PUSH-BASED RENDERING =====
    
    /**
     * Legacy render method for backward compatibility
     * Uses push-based rendering with automatic generation
     */
    public CompletableFuture<Void> render(TerminalContainerHandle terminal) {
        long gen = terminal.getCurrentRenderGeneration();
        
        return terminal.batchWithGeneration(gen, () -> {
            renderWithGeneration(
                terminal, gen,
                this.row, this.col, this.width, this.height,
                this.boxStyle, this.textStyle,
                this.title, this.titlePlacement, this.titleStyle,
                this.contentLines, this.contentAlignment, this.padding,
                this.scrollable, this.scrollOffset,
                this.textOverflow, this.horizontalScrollOffset
            );
        });
    }
    
    // ===== SCROLLING METHODS =====
    
    /**
     * Scroll vertically (if scrollable)
     * Returns new offset, or -1 if can't scroll
     */
    public synchronized int scrollVertical(int delta) {
        if (!scrollable) {
            return -1;
        }
        
        int contentHeight = calculateContentHeight(height, padding, titlePlacement, title);
        int maxOffset = Math.max(0, contentLines.size() - contentHeight);
        
        int newOffset = Math.max(0, Math.min(maxOffset, scrollOffset + delta));
        
        if (newOffset != scrollOffset) {
            scrollOffset = newOffset;
            return scrollOffset;
        }
        
        return -1; // No change
    }
    
    /**
     * Scroll horizontally
     * Returns new offset, or -1 if can't scroll
     */
    public synchronized int scrollHorizontal(int delta) {
        if (textOverflow != TextOverflow.SCROLL) {
            return -1;
        }
        
        int contentWidth = width - (2 * padding) - 2;
        
        // Find longest line
        int maxLineLength = contentLines.stream()
            .mapToInt(String::length)
            .max()
            .orElse(0);
        
        int maxOffset = Math.max(0, maxLineLength - contentWidth);
        
        int newOffset = Math.max(0, Math.min(maxOffset, horizontalScrollOffset + delta));
        
        if (newOffset != horizontalScrollOffset) {
            horizontalScrollOffset = newOffset;
            return horizontalScrollOffset;
        }
        
        return -1; // No change
    }
    
    // ===== BUILDER =====
    
    public static Builder builder() {
        return new Builder();
    }
    
    public Builder toBuilder() {
        return new Builder()
            .position(row, col)
            .size(width, height)
            .style(boxStyle)
            .textStyle(textStyle)
            .title(title, titlePlacement)
            .titleStyle(titleStyle)
            .content(contentLines.toArray(new String[0]))
            .contentAlignment(contentAlignment)
            .padding(padding)
            .scrollable(scrollable, scrollOffset)
            .textOverflow(textOverflow)
            .horizontalScrollOffset(horizontalScrollOffset);
    }
    
    /**
     * Create a copy with horizontal scroll adjusted
     */
    public TerminalTextBox withHorizontalScroll(int offset) {
        return toBuilder()
            .horizontalScrollOffset(offset)
            .build();
    }
    
    /**
     * Create a copy with vertical scroll adjusted
     */
    public TerminalTextBox withVerticalScroll(int offset) {
        return toBuilder()
            .scrollOffset(offset)
            .build();
    }
    
    // ===== ENUMS =====
    
    public enum TextOverflow {
        WRAP, TRUNCATE, SCROLL, TRUNCATE_START, FADE
    }
    
    public enum TitlePlacement {
        INSIDE_TOP, INSIDE_CENTER, INSIDE_BOTTOM, 
        BORDER_TOP, ABOVE_BOX, BELOW_BOX
    }
    
    public enum ContentAlignment {
        LEFT, CENTER, RIGHT
    }
    
    // ===== BUILDER CLASS =====
    
    public static class Builder {
        private int row = 0;
        private int col = 0;
        private int width = 40;
        private int height = 10;
        private BoxStyle boxStyle = BoxStyle.SINGLE;
        private TextStyle textStyle = TextStyle.NORMAL;
        private String title = null;
        private TitlePlacement titlePlacement = TitlePlacement.INSIDE_TOP;
        private TextStyle titleStyle = TextStyle.BOLD;
        private List<String> contentLines = new ArrayList<>();
        private ContentAlignment contentAlignment = ContentAlignment.LEFT;
        private int padding = 1;
        private boolean scrollable = false;
        private int scrollOffset = 0;
        private TextOverflow textOverflow = TextOverflow.TRUNCATE;
        private int horizontalScrollOffset = 0;
        
        public Builder position(int row, int col) {
            this.row = row;
            this.col = col;
            return this;
        }
        
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }
        
        public Builder style(BoxStyle style) {
            this.boxStyle = style;
            return this;
        }
        
        public Builder textStyle(TextStyle style) {
            this.textStyle = style;
            return this;
        }
        
        public Builder title(String title) {
            return title(title, TitlePlacement.INSIDE_TOP);
        }
        
        public Builder title(String title, TitlePlacement placement) {
            this.title = title;
            this.titlePlacement = placement;
            return this;
        }
        
        public Builder titleStyle(TextStyle style) {
            this.titleStyle = style;
            return this;
        }
        
        public Builder content(String... lines) {
            for (String line : lines) {
                this.contentLines.add(line);
            }
            return this;
        }
        
        public Builder addLine(String line) {
            this.contentLines.add(line);
            return this;
        }
        
        public Builder contentLines(List<String> lines) {
            this.contentLines.clear();
            this.contentLines.addAll(lines);
            return this;
        }
        
        public Builder clearContent() {
            this.contentLines.clear();
            return this;
        }
        
        public Builder contentAlignment(ContentAlignment alignment) {
            this.contentAlignment = alignment;
            return this;
        }
        
        public Builder padding(int padding) {
            this.padding = padding;
            return this;
        }
        
        public Builder scrollable(boolean scrollable) {
            return scrollable(scrollable, 0);
        }
        
        public Builder scrollable(boolean scrollable, int scrollOffset) {
            this.scrollable = scrollable;
            this.scrollOffset = scrollOffset;
            return this;
        }
        
        public Builder scrollOffset(int offset) {
            this.scrollOffset = offset;
            return this;
        }
        
        public Builder textOverflow(TextOverflow overflow) {
            this.textOverflow = overflow;
            return this;
        }
        
        public Builder horizontalScrollOffset(int offset) {
            this.horizontalScrollOffset = offset;
            return this;
        }
        
        public TerminalTextBox build() {
            return new TerminalTextBox(this);
        }
    }
}