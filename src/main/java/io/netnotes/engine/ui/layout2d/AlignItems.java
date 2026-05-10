package io.netnotes.engine.ui.layout2d;

public enum AlignItems {
    FLEX_START,
    FLEX_END,
    CENTER,
    STRETCH,
    BASELINE;

    /**
     * Convert to the equivalent AlignSelf for per-child resolution.
     * Called by the layout container when a child's AlignSelf is AUTO.
     */
    public AlignSelf toAlignSelf() {
        return switch (this) {
            case FLEX_START -> AlignSelf.FLEX_START;
            case FLEX_END   -> AlignSelf.FLEX_END;
            case CENTER     -> AlignSelf.CENTER;
            case STRETCH    -> AlignSelf.STRETCH;
            case BASELINE   -> AlignSelf.BASELINE;
        };
    }
}