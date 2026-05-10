package io.netnotes.engine.ui.layout2d;

/**
 * FlexShrink - controls how much a flex item will shrink relative to other items.
 *
 * Corresponds to CSS flex-shrink property.
 *
 * Values:
 * - NONE: Don't shrink (equivalent to flex-shrink: 0)
 * - SMALL: Shrink slightly (equivalent to flex-shrink: 1)
 * - MEDIUM: Shrink moderately (equivalent to flex-shrink: 2)
 * - LARGE: Shrink substantially (equivalent to flex-shrink: 3)
 * - FULL: Shrink to fit (equivalent to flex-shrink: 10)
 *
 * A value of 1 is the default for flex items.
 * A value of 0 means the item will not shrink even if it overflows.
 */
public enum FlexShrink {
    /** Don't shrink (flex-shrink: 0) */
    NONE(0),

    /** Shrink slightly (flex-shrink: 1) */
    SMALL(1),

    /** Shrink moderately (flex-shrink: 2) */
    MEDIUM(2),

    /** Shrink substantially (flex-shrink: 3) */
    LARGE(3),

    /** Shrink to fit (flex-shrink: 10) */
    FULL(10);

    private final int value;

    FlexShrink(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * Get the flex-shrink value as an integer.
     * @return The numeric flex-shrink value
     */
    public int toInt() {
        return value;
    }

    /**
     * Check if this item should shrink to fit available space.
     * @return true if FULL or any value >= 10
     */
    public boolean isFull() {
        return value >= 10;
    }

    /**
     * Check if this item should not shrink.
     * @return true if NONE or value is 0
     */
    public boolean isNone() {
        return value == 0;
    }

    public static FlexShrink of(int value){
        for(FlexShrink flexShrink : FlexShrink.values()){
            if(flexShrink.value == value){
                return flexShrink;
            }
        }

        throw new IllegalArgumentException("FlexShrink does not support: " + value);
    }
}
