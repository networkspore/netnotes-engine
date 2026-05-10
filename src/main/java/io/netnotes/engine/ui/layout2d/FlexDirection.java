package io.netnotes.engine.ui.layout2d;

/**
 * FlexDirection - controls the direction of the main axis in a flex container.
 *
 * Corresponds to CSS flex-direction property.
 *
 * Values:
 * - ROW: Children flow from left to right (main axis horizontal)
 * - ROW_REVERSE: Children flow from right to left (main axis horizontal)
 * - COLUMN: Children flow from top to bottom (main axis vertical)
 * - COLUMN_REVERSE: Children flow from bottom to top (main axis vertical)
 */
public enum FlexDirection {
    /** Children flow from left to right (main axis horizontal) */
    ROW,

    /** Children flow from right to left (main axis horizontal) */
    ROW_REVERSE,

    /** Children flow from top to bottom (main axis vertical) */
    COLUMN,

    /** Children flow from bottom to top (main axis vertical) */
    COLUMN_REVERSE
}
