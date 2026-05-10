package io.netnotes.engine.ui.layout2d;

/**
 * JustifyContent - controls how space is distributed along the main axis.
 *
 * Corresponds to CSS justify-content property.
 *
 * Values:
 * - FLEX_START: Items packed toward start (default)
 * - FLEX_END: Items packed toward end
 * - CENTER: Items centered
 * - SPACE_BETWEEN: Even spacing, first/last at edges
 * - SPACE_AROUND: Even spacing with half-size spaces at edges
 * - SPACE_EVENLY: Equal spacing everywhere
 *
 */
public enum JustifyContent {
    /** Items packed toward start of main axis (default) */
    FLEX_START,

    /** Items packed toward end of main axis */
    FLEX_END,

    /** Items centered on main axis */
    CENTER,

    /** Even spacing, first/last items at container edges */
    SPACE_BETWEEN,

    /** Even spacing with half-size spaces at edges */
    SPACE_AROUND,

    /** Equal spacing everywhere */
    SPACE_EVENLY;

   
}
