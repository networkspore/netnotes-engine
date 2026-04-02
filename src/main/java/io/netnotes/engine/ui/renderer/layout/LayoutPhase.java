package io.netnotes.engine.ui.renderer.layout;

/**
 * LayoutPhase — controls which spatial axes are committed during each pass
 * of the three-phase layout in RenderableLayoutManager.
 *
 * The group callback (e.g. VStack.layoutAllChildren) always writes all four
 * axes (x, y, width, height) because it has no axis awareness.  The layout
 * manager uses the phase to mask out the axes that should not be committed
 * in a given pass, based on each renderable's per-axis SizePreference:
 *
 *   TOP_DOWN  — commits x/y (positions always come from parent) and any
 *               dimension whose preference is FILL, PERCENT, or STATIC.
 *               FIT_CONTENT dimensions are masked out: they haven't been
 *               measured bottom-up yet and would be stale.
 *
 *   BOTTOM_UP — commits only FIT_CONTENT dimensions (width or height).
 *               x/y positions and FILL/PERCENT/STATIC dimensions are masked
 *               out: they were correctly committed in TOP_DOWN.
 *
 *   REPAIR    — commits all four axes unconditionally.  Used in Phase 3 to
 *               re-run top-down positioning after FIT_CONTENT sizes are known,
 *               so containers like SSW re-centre wizardCard at the correct y.
 */
public enum LayoutPhase {
    TOP_DOWN,
    BOTTOM_UP,
    REPAIR
}
