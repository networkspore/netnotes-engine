package io.netnotes.engine.ui.renderer;

/**
 * RenderPhase — per-node render lifecycle position.
 *
 * Models where a specific Renderable sits in the layout-to-screen pipeline.
 * Unlike the BitFlagStateMachine (which models a set of independently-true
 * attributes), phase models a position in an ordered sequence. Exactly one
 * phase is active at a time.
 *
 * OWNERSHIP:
 *   Each Renderable owns its own RenderPhase field. The RenderableLayoutManager
 *   drives transitions by calling advanceRenderPhase() as it processes nodes.
 *   A detached Renderable stays at DETACHED until a manager registers it.
 *
 * CYCLE per drain:
 *
 *   DETACHED ──register──► COLLECTING ──drain starts──► APPLYING
 *                               ▲                            │
 *                               │                    layout applied
 *                               │                        ▼
 *                        re-dirty after           
 *                         RENDERED ◄──frame applied──────┘
 */
public enum RenderPhase {

    /**
     * No layout manager is attached. The node may have pending child groups
     * but no layout pass has processed it. Starting state; also the state
     * after unregistration from a manager.
     */
    DETACHED,
   
    /**
     * Registered with a layout manager and marked dirty. The debounce window
     * is open; the drain has not yet started for this node. Also the state
     * a RENDERED node returns to when requestLayoutUpdate() fires.
     */
    COLLECTING,

    /**
     * The layout drain is active. Group callbacks (VStack.layoutAllChildren
     * etc.) have run and applyLayoutData() has been called on this node.
     */
    APPLYING,

    IDLE,

    /**
     * At least one frame containing this node has been committed to terminal
     * output. This is the correct phase for onStarted() to fire — the node
     * has been laid out and is visibly on screen.
     *
     * A new requestLayoutUpdate() after this point pushes the node back to
     * COLLECTING for the next dirty cycle.
     */
    RENDERED;

    /** True if this phase is strictly later in the pipeline than {@code other}. */
    public boolean isAfter(RenderPhase other) {
        return this.ordinal() > other.ordinal();
    }

    /** True if this phase is at or later in the pipeline than {@code other}. */
    public boolean isAtLeast(RenderPhase other) {
        return this.ordinal() >= other.ordinal();
    }
}