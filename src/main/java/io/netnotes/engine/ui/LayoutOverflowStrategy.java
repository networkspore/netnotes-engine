package io.netnotes.engine.ui;

/**
 * LayoutOverflowStrategy — controls how a stack container handles the case
 * where children's measured sizes exceed available space.
 *
 * Applied via TerminalVStack.setOverflowStrategy() and TerminalHStack equivalent.
 * Default is CLIP, which preserves all existing layout behavior exactly.
 *
 * The strategy is evaluated after both measurement passes complete and total
 * child height is known relative to available space. It does not affect how
 * children are measured — only how allocations are adjusted when the measured
 * total exceeds what the parent can provide.
 */
public enum LayoutOverflowStrategy {

    /**
     * Default behavior.
     *
     * FIT_CONTENT children keep their preferred sizes in full.
     * FILL children receive whatever space remains (may be 0).
     * FILL children whose minHeight exceeds fillHeight are inflated up to
     * minHeight, which can cause them to overflow the parent bounds.
     * Children placed out-of-bounds are hidden by the bounds check.
     */
    CLIP,

    /**
     * FILL children absorb the space deficit before FIT_CONTENT children
     * are affected.
     *
     * Algorithm:
     *   1. Measure all FIT_CONTENT children normally (preferred sizes).
     *   2. Compute remaining = available - totalFitHeight.
     *   3. Distribute remaining equally across FILL children.
     *      If remaining is negative, FILL children receive 0.
     *   4. minHeight is NOT enforced as a floor for FILL children — they may
     *      render below their declared minimum rather than overflow the parent.
     *   5. If total FIT alone exceeds available (no room for any FILL), FILL
     *      children are hidden and remaining FIT children are clipped in
     *      reverse-declaration order (last added hides first).
     *
     * Use case: wizard (FILL) inside a card that gains a visible menu
     * (FIT_CONTENT). With CLIP the wizard overflows and is hidden entirely.
     * With SHRINK_FILL the wizard compresses and renders at reduced height
     * rather than disappearing.
     */
    SHRINK_FILL,

    /**
     * All visible children (both FILL and FIT_CONTENT) are scaled
     * proportionally when total measured height exceeds available space.
     *
     * Each child receives: floor(preferred * (available / totalPreferred)).
     * minHeight / minWidth applies as a hard floor; any deficit from honoring
     * floors is redistributed proportionally across children that have room
     * to give.
     *
     * Use case: dashboards where every panel has equal claim to vertical space
     * and none should disappear while others retain their full size.
     */
    SHRINK_ALL,

    /**
     * Available space is divided equally among all visible children regardless
     * of their declared size preferences. Equivalent to CSS flexbox with
     * flex-grow=1 on every child. minHeight / minWidth is a hard floor.
     *
     * Use case: tab bars, menu rows, any layout where even distribution is
     * more important than respecting individual preferences.
     */
    DISTRIBUTE_EQUAL,

    /**
     * Children keep their preferred sizes. The container's primary axis
     * becomes scrollable; out-of-bounds children are rendered but clipped to
     * the viewport. The container must track a scroll offset.
     *
     * NOT YET IMPLEMENTED — falls back to CLIP until scroll support is added.
     */
    SCROLL,

    /**
     * Children render past parent bounds without being hidden or clipped.
     * The parent does not apply the out-of-bounds hidden flag.
     *
     * Intended for dropdowns, tooltips, and overlay containers that
     * legitimately need to exceed their layout parent's bounds.
     */
    OVERFLOW
}