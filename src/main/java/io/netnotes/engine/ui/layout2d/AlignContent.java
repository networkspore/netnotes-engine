package io.netnotes.engine.ui.layout2d;

/**
 * AlignContent - controls how extra space is distributed in multi-line flex containers.
 *
 * Corresponds to CSS align-content property.
 *
 * Values:
 * - FLEX_START: Lines are packed toward the start of the cross axis
 * - FLEX_END: Lines are packed toward the end of the cross axis
 * - CENTER: Lines are packed toward the center of the cross axis
 * - SPACE_BETWEEN: Lines are evenly distributed with first and last lines at edges
 * - SPACE_AROUND: Lines are evenly distributed with equal space around each line
 * - SPACE_EVENLY: Lines are evenly distributed with equal space between each line
 * - STRETCH: Lines stretch to fill the container (default)
 */
public enum AlignContent {
    /** Lines packed toward start of cross axis */
    FLEX_START,

    /** Lines packed toward end of cross axis */
    FLEX_END,

    /** Lines packed toward center of cross axis */
    CENTER,

    /** Lines evenly distributed with first/last at edges */
    SPACE_BETWEEN,

    /** Lines evenly distributed with equal space around each */
    SPACE_AROUND,

    /** Lines evenly distributed with equal space between each */
    SPACE_EVENLY,

    /** Lines stretch to fill container (default) */
    STRETCH
}
