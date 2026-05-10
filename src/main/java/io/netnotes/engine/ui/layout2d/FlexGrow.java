package io.netnotes.engine.ui.layout2d;

/**
 * FlexGrow - controls how much a flex item will grow relative to other items.
 *
 * Corresponds to CSS flex-grow property.
 *
 * Values:
 * - NONE: Don't grow (equivalent to flex-grow: 0)
 * - SMALL: Grow slightly (equivalent to flex-grow: 1)
 * - MEDIUM: Grow moderately (equivalent to flex-grow: 2)
 * - LARGE: Grow substantially (equivalent to flex-grow: 3)
 * - FULL: Grow to fill available space (equivalent to flex-grow: 10)
 *
 * A value of 0 means the item will not grow (default for most children).
 * A value of 10+ can be used for items that should take available space.
 */
public enum FlexGrow {
    /** Don't grow (flex-grow: 0) */
    NONE(0),

    /** Grow slightly (flex-grow: 1) */
    SMALL(1),

    /** Grow moderately (flex-grow: 2) */
    MEDIUM(2),

    /** Grow substantially (flex-grow: 3) */
    LARGE(3),

    /** Grow to fill available space (flex-grow: 10) */
    FULL(10);

    private final int value;

    FlexGrow(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * Get the flex-grow value as an integer.
     * @return The numeric flex-grow value
     */
    public int toInt() {
        return value;
    }

    /**
     * Check if this item should grow to fill available space.
     * @return true if FULL or any value >= 10
     */
    public boolean isFill() {
        return value >= 10;
    }

    public boolean isNonZero() {
        return value > 0;
    }

  


    /**
     * Check if this item should not grow.
     * @return true if NONE or value is 0
     */
    public boolean isNone() {
        return value == 0;
    }

    public static FlexGrow of(int value){
        for(FlexGrow flexGrow : FlexGrow.values()){
            if(value == flexGrow.value){
                return flexGrow;
            }
        }
        throw new IllegalArgumentException("FlexGrow does not support: " + value);
    }
}
