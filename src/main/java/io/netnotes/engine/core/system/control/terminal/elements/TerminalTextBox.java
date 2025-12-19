package io.netnotes.engine.core.system.control.terminal.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle.BoxStyle;

/**
 * TextBox - Composable text box primitive for terminal UIs
 * 
 * A TextBox is a bordered region containing text content. It's the fundamental
 * building block for terminal interfaces:
 * - Title boxes (headers, section labels)
 * - Content boxes (multi-line text display)
 * - Input boxes (user input fields)
 * - Message boxes (dialogs, confirmations)
 * - List boxes (menus, selections)
 * 
 * Design Philosophy:
 * - Immutable (builder pattern for construction)
 * - Composable (can nest or arrange multiple boxes)
 * - Flexible (extensive configuration options)
 * - Efficient (batches rendering operations)
 * 
 * Example:
 * <pre>
 * TextBox.builder()
 *     .position(5, 20)
 *     .size(40, 10)
 *     .title("User Profile", TitlePlacement.INSIDE_TOP)
 *     .content("Name: John Doe", "Email: john@example.com")
 *     .style(BoxStyle.DOUBLE)
 *     .padding(2)
 *     .build()
 *     .render(terminal);
 * </pre>
 */
public class TerminalTextBox {
    
    // Position and dimensions
    private final int row;
    private final int col;
    private final int width;
    private final int height;
    
    // Styling
    private final BoxStyle boxStyle;
    private final TerminalContainerHandle.TextStyle textStyle;
    
    // Title
    private final String title;
    private final TitlePlacement titlePlacement;
    private final TerminalContainerHandle.TextStyle titleStyle;
    
    // Content
    private final List<String> contentLines;
    private final ContentAlignment contentAlignment;
    private final int padding;
    
    // Behavior
    private final boolean scrollable;
    private final int scrollOffset;
    
    // Text overflow handling
    private final TextOverflow textOverflow;
    private final int horizontalScrollOffset;
    
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
    
    /**
     * Render this text box to the terminal
     */
    public CompletableFuture<Void> render(TerminalContainerHandle terminal) {
        return terminal.beginBatch()
            .thenCompose(v -> renderBox(terminal))
            .thenCompose(v -> renderTitle(terminal))
            .thenCompose(v -> renderContent(terminal))
            .thenCompose(v -> terminal.endBatch());
    }
    
    private CompletableFuture<Void> renderBox(TerminalContainerHandle terminal) {
        // Draw border based on title placement
        if (titlePlacement == TitlePlacement.BORDER_TOP && title != null) {
            // Title in border (traditional style)
            return terminal.drawBox(row, col, width, height, title, boxStyle);
        } else {
   
            // Just draw the box
            return terminal.drawBox(row, col, width, height, "", boxStyle);
        }
    }
    
    private CompletableFuture<Void> renderTitle(TerminalContainerHandle terminal) {
        if (title == null || title.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return switch (titlePlacement) {
            case INSIDE_TOP -> {
                int titleRow = row + 1;
                int titleCol = calculateAlignment(title.length(), contentAlignment);
                yield terminal.printAt(titleRow, titleCol, title, titleStyle);
            }
            case INSIDE_CENTER -> {
                int titleRow = row + height / 2;
                int titleCol = calculateAlignment(title.length(), ContentAlignment.CENTER);
                yield terminal.printAt(titleRow, titleCol, title, titleStyle);
            }
            case INSIDE_BOTTOM -> {
                int titleRow = row + height - 2;
                int titleCol = calculateAlignment(title.length(), contentAlignment);
                yield terminal.printAt(titleRow, titleCol, title, titleStyle);
            }
            case BORDER_TOP -> {
                // Already handled in renderBox
                yield CompletableFuture.completedFuture(null);
            }
            case ABOVE_BOX -> {
                int titleRow = row - 1;
                int titleCol = calculateAlignment(title.length(), ContentAlignment.CENTER);
                yield terminal.printAt(titleRow, titleCol, title, titleStyle);
            }
            case BELOW_BOX -> {
                int titleRow = row + height;
                int titleCol = calculateAlignment(title.length(), ContentAlignment.CENTER);
                yield terminal.printAt(titleRow, titleCol, title, titleStyle);
            }
        };
    }
    
    private CompletableFuture<Void> renderContent(TerminalContainerHandle terminal) {
        if (contentLines.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Calculate content area
        int contentStartRow = calculateContentStartRow();
        int contentHeight = calculateContentHeight();
        int contentWidth = width - (2 * padding) - 2; // -2 for borders
        
        // Determine which lines to show (with scrolling)
        int startLine = scrollable ? scrollOffset : 0;
        int endLine = Math.min(contentLines.size(), startLine + contentHeight);
        
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        
        for (int i = startLine; i < endLine; i++) {
            String line = contentLines.get(i);
            String displayLine = processLineOverflow(line, contentWidth);
            
            int lineRow = contentStartRow + (i - startLine);
            int lineCol = calculateAlignment(displayLine.length(), contentAlignment);
            
            final String finalLine = displayLine;
            future = future.thenCompose(v -> 
                terminal.printAt(lineRow, lineCol, finalLine, textStyle));
        }
        
        // Render scroll indicators if scrollable
        if (scrollable && contentLines.size() > contentHeight) {
            if (scrollOffset > 0) {
                int indicatorCol = col + width - 3;
                future = future.thenCompose(v -> 
                    terminal.printAt(contentStartRow, indicatorCol, "↑", 
                        TerminalContainerHandle.TextStyle.INFO));
            }
            
            if (endLine < contentLines.size()) {
                int indicatorCol = col + width - 3;
                int indicatorRow = contentStartRow + contentHeight - 1;
                future = future.thenCompose(v -> 
                    terminal.printAt(indicatorRow, indicatorCol, "↓", 
                        TerminalContainerHandle.TextStyle.INFO));
            }
        }
        
        // Render horizontal scroll indicators if needed
        if (textOverflow == TextOverflow.SCROLL) {
            future = future.thenCompose(v -> renderHorizontalScrollIndicators(
                terminal, contentStartRow, contentHeight, contentWidth));
        }
        
        return future;
    }
    
    /**
     * Process line based on overflow strategy
     */
    private String processLineOverflow(String line, int availableWidth) {
        if (line.length() <= availableWidth) {
            return line;
        }
        
        return switch (textOverflow) {
            case WRAP -> {
                // Wrapping is handled by breaking into multiple lines
                // For now, just truncate (proper wrap needs line splitting)
                yield line.substring(0, availableWidth);
            }
            case TRUNCATE -> {
                // Cut off with ellipsis
                yield line.substring(0, Math.max(0, availableWidth - 3)) + "...";
            }
            case SCROLL -> {
                // Show a window into the text based on horizontal offset
                int start = Math.min(horizontalScrollOffset, 
                    Math.max(0, line.length() - availableWidth));
                int end = Math.min(line.length(), start + availableWidth);
                yield line.substring(start, end);
            }
            case TRUNCATE_START -> {
                // Show end of text with leading ellipsis
                yield "..." + line.substring(Math.max(0, line.length() - availableWidth + 3));
            }
            case FADE -> {
                // Truncate but without ellipsis (fade effect in rich terminals)
                yield line.substring(0, availableWidth);
            }
        };
    }
    
    /**
     * Render horizontal scroll indicators (◄ ►)
     */
    private CompletableFuture<Void> renderHorizontalScrollIndicators(
            TerminalContainerHandle terminal,
            int contentStartRow,
            int contentHeight,
            int contentWidth) {
        
        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        
        // Check if any line needs horizontal scrolling
        boolean hasLongLines = contentLines.stream()
            .anyMatch(line -> line.length() > contentWidth);
        
        if (!hasLongLines) {
            return future;
        }
        
        // Show left indicator if scrolled right
        if (horizontalScrollOffset > 0) {
            int indicatorRow = contentStartRow + contentHeight / 2;
            int indicatorCol = col + 1;
            future = future.thenCompose(v -> 
                terminal.printAt(indicatorRow, indicatorCol, "◄", 
                    TerminalContainerHandle.TextStyle.INFO));
        }
        
        // Show right indicator if more content to the right
        boolean hasMoreRight = contentLines.stream()
            .anyMatch(line -> line.length() > horizontalScrollOffset + contentWidth);
        
        if (hasMoreRight) {
            int indicatorRow = contentStartRow + contentHeight / 2;
            int indicatorCol = col + width - 2;
            future = future.thenCompose(v -> 
                terminal.printAt(indicatorRow, indicatorCol, "►", 
                    TerminalContainerHandle.TextStyle.INFO));
        }
        
        return future;
    }
    
    private int calculateContentStartRow() {
        int startRow = row + 1 + padding; // +1 for top border
        
        // Adjust for title if inside
        if (titlePlacement == TitlePlacement.INSIDE_TOP && title != null) {
            startRow += 1; // Title takes one line
        }
        
        return startRow;
    }
    
    private int calculateContentHeight() {
        int availableHeight = height - 2 - (2 * padding); // -2 for borders
        
        // Adjust for title if inside
        if ((titlePlacement == TitlePlacement.INSIDE_TOP || 
             titlePlacement == TitlePlacement.INSIDE_BOTTOM) && title != null) {
            availableHeight -= 1;
        }
        
        return Math.max(1, availableHeight);
    }
    
    private int calculateAlignment(int textLength, ContentAlignment alignment) {
        int availableWidth = width - 2; // Exclude borders
        
        return switch (alignment) {
            case LEFT -> col + 1 + padding;
            case CENTER -> col + (availableWidth - textLength) / 2;
            case RIGHT -> col + availableWidth - textLength - padding - 1;
        };
    }
    
    /**
     * Create a new builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create a builder from this box (for modifications)
     */
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
     * Useful for handling left/right navigation
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
    
    /**
     * How to handle text that exceeds the box width
     */
    public enum TextOverflow {
        /** Wrap text to next line (increases vertical space usage) */
        WRAP,
        
        /** Truncate with ellipsis "Long text beco..." */
        TRUNCATE,
        
        /** Horizontal scroll - show a sliding window into the text */
        SCROLL,
        
        /** Show end of text "...omes too long" */
        TRUNCATE_START,
        
        /** Truncate without ellipsis (for fade effects) */
        FADE
    }
    
    /**
     * Where to place the title
     */
    public enum TitlePlacement {
        INSIDE_TOP,      // Inside box, top row, respects alignment
        INSIDE_CENTER,   // Inside box, vertically centered
        INSIDE_BOTTOM,   // Inside box, bottom row
        BORDER_TOP,      // Embedded in top border (classic style)
        ABOVE_BOX,       // Above the box (separate line)
        BELOW_BOX        // Below the box (separate line)
    }
    
    /**
     * How to align content
     */
    public enum ContentAlignment {
        LEFT,
        CENTER,
        RIGHT
    }
    
    /**
     * Box drawing style
     
    public enum BoxStyle {
        SINGLE,
        DOUBLE,
        ROUNDED,
        THICK
    }*/
    
    // ===== BUILDER =====
    
    public static class Builder {
        private int row = 0;
        private int col = 0;
        private int width = 40;
        private int height = 10;
        private BoxStyle boxStyle = BoxStyle.SINGLE;
        private TerminalContainerHandle.TextStyle textStyle = TerminalContainerHandle.TextStyle.NORMAL;
        private String title = null;
        private TitlePlacement titlePlacement = TitlePlacement.INSIDE_TOP;
        private TerminalContainerHandle.TextStyle titleStyle = TerminalContainerHandle.TextStyle.BOLD;
        private List<String> contentLines = new ArrayList<>();
        private ContentAlignment contentAlignment = ContentAlignment.LEFT;
        private int padding = 1;
        private boolean scrollable = false;
        private int scrollOffset = 0;
        private TextOverflow textOverflow = TextOverflow.TRUNCATE;
        private int horizontalScrollOffset = 0;
        
        /**
         * Set position (row, col)
         */
        public Builder position(int row, int col) {
            this.row = row;
            this.col = col;
            return this;
        }
        
        /**
         * Set size (width, height)
         */
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }
        
        /**
         * Set box drawing style
         */
        public Builder style(BoxStyle style) {
            this.boxStyle = style;
            return this;
        }
        
        /**
         * Set text style for content
         */
        public Builder textStyle(TerminalContainerHandle.TextStyle style) {
            this.textStyle = style;
            return this;
        }
        
        /**
         * Set title with default placement (INSIDE_TOP)
         */
        public Builder title(String title) {
            return title(title, TitlePlacement.INSIDE_TOP);
        }
        
        /**
         * Set title with specific placement
         */
        public Builder title(String title, TitlePlacement placement) {
            this.title = title;
            this.titlePlacement = placement;
            return this;
        }
        
        /**
         * Set title style
         */
        public Builder titleStyle(TerminalContainerHandle.TextStyle style) {
            this.titleStyle = style;
            return this;
        }
        
        /**
         * Add content lines
         */
        public Builder content(String... lines) {
            for (String line : lines) {
                this.contentLines.add(line);
            }
            return this;
        }
        
        /**
         * Add content line
         */
        public Builder addLine(String line) {
            this.contentLines.add(line);
            return this;
        }
        
        /**
         * Set content from list
         */
        public Builder contentLines(List<String> lines) {
            this.contentLines.clear();
            this.contentLines.addAll(lines);
            return this;
        }
        
        /**
         * Clear content
         */
        public Builder clearContent() {
            this.contentLines.clear();
            return this;
        }
        
        /**
         * Set content alignment
         */
        public Builder contentAlignment(ContentAlignment alignment) {
            this.contentAlignment = alignment;
            return this;
        }
        
        /**
         * Set padding (space between border and content)
         */
        public Builder padding(int padding) {
            this.padding = padding;
            return this;
        }
        
        /**
         * Enable/disable scrolling
         */
        public Builder scrollable(boolean scrollable) {
            return scrollable(scrollable, 0);
        }
        
        /**
         * Enable scrolling with offset
         */
        public Builder scrollable(boolean scrollable, int scrollOffset) {
            this.scrollable = scrollable;
            this.scrollOffset = scrollOffset;
            return this;
        }
        
        /**
         * Set scroll offset (for scrollable boxes)
         */
        public Builder scrollOffset(int offset) {
            this.scrollOffset = offset;
            return this;
        }
        
        /**
         * Set text overflow strategy
         */
        public Builder textOverflow(TextOverflow overflow) {
            this.textOverflow = overflow;
            return this;
        }
        
        /**
         * Set horizontal scroll offset (for SCROLL overflow)
         */
        public Builder horizontalScrollOffset(int offset) {
            this.horizontalScrollOffset = offset;
            return this;
        }
        
        /**
         * Build the TextBox
         */
        public TerminalTextBox build() {
            return new TerminalTextBox(this);
        }
    }
}