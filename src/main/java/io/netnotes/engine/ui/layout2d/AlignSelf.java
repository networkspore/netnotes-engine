package io.netnotes.engine.ui.layout2d;

/**
 * AlignSelf - controls alignment of a single flex item within its cross axis.
 *
 * Corresponds to CSS align-self property.
 *
 * Values:
 * - AUTO: Inherit from parent (default)
 * - FLEX_START: Align with start of cross axis
 * - FLEX_END: Align with end of cross axis
 * - CENTER: Align with center of cross axis
 * - STRETCH: Stretch to fill cross axis
 * - BASELINE: Align items on their baselines
 */
public enum AlignSelf {
    /** Inherit from parent (default) */
    AUTO,

    /** Align with start of cross axis */
    FLEX_START,

    /** Align with end of cross axis */
    FLEX_END,

    /** Align with center of cross axis */
    CENTER,

    /** Stretch to fill cross axis */
    STRETCH,

    /** Align items on their baselines */
    BASELINE
}
