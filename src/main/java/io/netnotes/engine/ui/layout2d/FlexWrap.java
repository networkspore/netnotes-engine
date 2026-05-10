package io.netnotes.engine.ui.layout2d;

/**
 * FlexWrap - controls whether flex items wrap onto multiple lines.
 *
 * Corresponds to CSS flex-wrap property.
 *
 * Values:
 * - NOWRAP: All flex items in one line (default)
 * - WRAP: Flex items wrap onto multiple lines
 * - WRAP_REVERSE: Flex items wrap but direction is reversed
 */
public enum FlexWrap {
    /** All flex items in one line (default) */
    NOWRAP,

    /** Flex items wrap onto multiple lines */
    WRAP,

    /** Flex items wrap but direction is reversed */
    WRAP_REVERSE
}
