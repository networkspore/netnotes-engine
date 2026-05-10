package io.netnotes.engine.ui.layout2d;

/**
 * FlexBasis - specifies the initial main-size of a flex item before any flex grow/shrink adjustments.
 *
 * Corresponds to CSS flex-basis property.
 *
 * Values:
 * - AUTO: Use the item's size as determined by flex-grow and flex-shrink (default)
 * - pixels(int): Use the specified pixel value
 * - percent(double): Use the specified percentage of the container
 * - CONTENT: Use the item's content size (equivalent to FIT_CONTENT)
 *
 * Flex-basis is used to determine the "base" size before flex-grow and flex-shrink
 * are applied. If not specified, it defaults to "auto".
 */
public final class FlexBasis {
    private enum Type { AUTO, PIXELS, PERCENT, CONTENT }

    public static final FlexBasis AUTO = new FlexBasis(Type.AUTO, 0, 0.0);
    public static final FlexBasis CONTENT = new FlexBasis(Type.CONTENT, 0, 0.0);

    private final Type type;
    private final int pixels;
    private final double percent;

    private FlexBasis(Type type, int pixels, double percent) {
        this.type = type;
        this.pixels = pixels;
        this.percent = percent;
    }

    public static FlexBasis auto() {
        return AUTO;
    }

    public static FlexBasis content() {
        return CONTENT;
    }

    public static FlexBasis pixels(int pixels) {
        return new FlexBasis(Type.PIXELS, pixels, 0.0);
    }

    public static FlexBasis percent(double percent) {
        return new FlexBasis(Type.PERCENT, 0, percent);
    }

    /**
     * Convenience method for percentage values as integers (0-100).
     * Internally stored as a double (e.g., 50 → 0.5).
     * @param percent The percentage value (0-100)
     * @return A FlexBasis with the percentage value
     */
    public static FlexBasis percent(int percent) {
        return new FlexBasis(Type.PERCENT, 0, percent / 100.0);
    }

    public int getPixels() {
        return pixels;
    }

    public double getPercent() {
        return percent;
    }

    public boolean isAuto() {
        return type == Type.AUTO;
    }

    public boolean isContent() {
        return type == Type.CONTENT;
    }

    public boolean isPixels() {
        return type == Type.PIXELS;
    }

    public boolean isPercent() {
        return type == Type.PERCENT;
    }

    /**
     * Convert this FlexBasis to a pixel value based on the container size.
     * @param containerSize The container's main-axis size in pixels
     * @return The calculated pixel value
     */
    public int toPixels(int containerSize) {
        return switch (type) {
            case PIXELS -> pixels;
            case PERCENT -> (int) Math.round(percent * containerSize);
            case AUTO, CONTENT -> 0;
        };
    }

}
