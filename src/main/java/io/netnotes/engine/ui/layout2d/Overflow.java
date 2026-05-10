package io.netnotes.engine.ui.layout2d;



/**
 * Overflow - unified overflow handling for layout containers.
 *
 * Corresponds to CSS overflow property.
 *
 * Values:
 * - VISIBLE: Content may be rendered outside the container bounds
 * - HIDDEN: Content that overflows is clipped
 * - AUTO: Content is clipped but scrollable if needed
 * - SCROLL: Content is clipped and scrollable (scrollbars always visible)
 *
 * Note: AUTO and SCROLL are not yet fully implemented.
 * Currently, VISIBLE maps to OVERFLOW strategy and HIDDEN maps to CLIP strategy.
 */
public enum Overflow {
    /** Content may be rendered outside container bounds */
    VISIBLE,

    /** Content that overflows is clipped */
    HIDDEN,

    /** Content is clipped but scrollable if needed */
    AUTO,

    /** Content is clipped and scrollable (scrollbars always visible) */
    SCROLL;



    /**
     * Check if overflow should be clipped.
     * @return true if content is clipped (HIDDEN, AUTO, SCROLL)
     */
    public boolean isClipped() {
        return this != VISIBLE;
    }

    /**
     * Check if overflow allows content outside bounds.
     * @return true if content may be rendered outside (VISIBLE)
     */
    public boolean isVisible() {
        return this == VISIBLE;
    }

    /**
     * Check if overflow is scrollable (AUTO or SCROLL).
     * Callers should also verify scroll support is available,
     * as AUTO/SCROLL currently fall back to CLIP.
     * @return true if overflow is AUTO or SCROLL
     */
    public boolean isScrollable() {
        return this == AUTO || this == SCROLL;
    }
}
