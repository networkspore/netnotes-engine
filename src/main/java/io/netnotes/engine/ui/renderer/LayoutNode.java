package io.netnotes.engine.ui.renderer;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.renderer.layout.GroupLayoutCallback;
import io.netnotes.engine.ui.renderer.layout.LayoutCallback;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * LayoutNode - One node in the layout tree, paired 1:1 with a Renderable.
 *
 * TWO-CALLBACK MODEL:
 *
 *   contentCallback  — answers "what size do I need to fit my content?"
 *                      fires on-demand, bottom-up, when a parent or group
 *                      callback calls context.getMeasuredSize() on this node.
 *                      null if this node is not content-sized.
 *                      Fires at most once per pass (gated by contentMeasured).
 *
 *   layoutCallback   — answers "given my committed space, how do I arrange
 *                      things / what is my position?"
 *                      fires top-down during the normal depth-sorted pass.
 *                      null if this node is purely reactive (accepts whatever
 *                      its parent or group sets).
 *
 * Either or both callbacks may be null. A node with both null is still
 * managed if it belongs to a member group — the group's callbacks speak
 * for it.
 *
 * OWNED GROUPS:
 * A node owns the groups whose members are its direct children.
 * The manager fires owned groups immediately after committing this node,
 * so group callbacks always have valid parent geometry.
 *
 * SINGLE COMMIT:
 * calculatedLayout is committed by applyNode() exactly once per pass.
 * Content callbacks and group callbacks only populate calculatedLayout —
 * they never touch apply. clear() happens after apply state
 */
public abstract class LayoutNode<
    B extends BatchBuilder<S>,
    R extends Renderable<B,P,S,L,LC,LD,LCB,GCB,?,G,R>,
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    LD extends LayoutData<B,R,S,LD,?>,
    LC extends LayoutContext<B,R,P,S,LD,LCB,LC,L>,
    LCB extends LayoutCallback<B,R,P,S,LC,LD,LCB>,
    GCB extends GroupLayoutCallback<B,R,P,S,LD,LC,L,GCB>,
    G extends LayoutGroup<B,R,P,S,LD,LC,GCB,L,G>,
    L extends LayoutNode<B,R,P,S,LD,LC,LCB,GCB,G,L>
> {
    protected final R renderable;
    protected L parent;

  

    /**
     * Fires top-down during the normal pass.
     * Answers: given my space, how do I position / arrange things?
     * null if this node is purely reactive.
     */
    private LCB layoutCallback;

    /**
     * The result of whichever callback(s) have fired so far this pass.
     * May be partially populated (content axes only) before the layout
     * callback fires and adds position/fill axes.
     * Committed to the renderable exactly once by applyNode().
     */
    LD calculatedLayout;
    private LC inFlightContext = null;
    /**
     * True once contentCallback has fired this pass.
     * Prevents redundant re-measurement when multiple ancestors ask for
     * getMeasuredSize() on the same content-sized node.
     */
    boolean contentMeasured = false;

    private final List<L> children    = new ArrayList<>();
    private final List<G> ownedGroups = new ArrayList<>();

    /** The group this node is a MEMBER of (at most one). */
    private G memberGroup = null;

    protected R positionAnchor = null;

    public LayoutNode(R renderable) {
        this.renderable = renderable;
    }

    public LC  getInFlightContext()        { return inFlightContext; }
    public void setInFlightContext(LC ctx) { this.inFlightContext = ctx; }
    // ── Per-pass reset ────────────────────────────────────────────────────────

   
    public boolean isContentMeasured(){
        return contentMeasured;
    }

    public void setContentMeasured(boolean contentMeasured) {
        this.contentMeasured = contentMeasured;
    }

    // ── Tree management ───────────────────────────────────────────────────────

    public void addChild(L child) {
        children.add(child);
        child.parent = self();
    }

    public void removeChild(L child) {
        children.remove(child);
        child.parent = null;
    }

    // ── Owned group management ────────────────────────────────────────────────

    public void addOwnedGroup(G group) {
        if (!ownedGroups.contains(group)) {
            ownedGroups.add(group);
            group.setOwner(self());
        }
    }

    public void removeOwnedGroup(G group) {
        ownedGroups.remove(group);
        if (group.getOwner() == self()) {
            group.setOwner(null);
        }
    }

    public List<G> getOwnedGroups() { return ownedGroups; }

    // ── Member group management ───────────────────────────────────────────────

    public void setMemberGroup(G group) {
        if (this.memberGroup != null) {
            this.memberGroup.removeMember(self());
        }
        this.memberGroup = group;
        if (group != null) {
            group.addMember(self());
        }
    }

    public G       getMemberGroup()    { return memberGroup; }
    public boolean isGroupMember()     { return memberGroup != null; }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    /**
     * Set both callbacks at once. Either may be null.
     *
     * @param contentCallback fires on-demand for content measurement, or null
     * @param layoutCallback  fires top-down during the pass, or null
     */
    public void setLayoutCallback(LCB layoutCallback) {
        this.layoutCallback  = layoutCallback;
    }

    public LCB getLayoutCallback()  { return layoutCallback; }

    public boolean isManaged() {
        return layoutCallback != null || memberGroup != null;
    }

    /** True if this node participates in content-sized measurement. */
    public boolean isSizedByContent() {
        return renderable.isSizedByContent();
    }

    // ── Content measurement (bottom-up, on-demand) ────────────────────────────

    /**
     * Fire the content callback if it has not yet fired this pass.
     *
     * Called by LayoutContext.getMeasuredSize() when a parent or group
     * callback needs this node's intrinsic size. The content callback
     * may itself call getMeasuredSize() on its own children, recursing
     * bottom-up naturally. The contentMeasured gate ensures each node's
     * content callback fires at most once per pass regardless of how many
     * ancestors request its measurement.
     *
     * After this method returns, calculatedLayout contains at least the
     * content axes. Position and fill axes may be absent until the
     * layout callback fires (or the parent group sets them).
     *
     * @param context a freshly initialised context for this node
     */
    public void measureContent(LC context, LC[] childContexts) {
        if (contentMeasured) return;
        contentMeasured = true;

        if (renderable.isSizedByContent()) {
            S result = renderable.measureContent(childContexts);
            context.setContentMeasurement(result);
        }
    }

    // ── Layout calculation (top-down, during normal pass) ─────────────────────

    /**
     * Fire the layout callback. Called by the manager when it reaches this
     * node in depth order and calculatedLayout is still null (no parent or
     * group has pre-populated it).
     *
     * After this method returns, finalizeCalculatedLayout() is always called.
     */
    public void calculateLayout(LC context) {
        if (calculatedLayout != null) {
            // Pre-populated by a group callback. Individual callback must not fire.
            // If this node is not a group member, that is a bug — log it.
            if (!isGroupMember()) {
                Log.logError("[LayoutNode:" + getName() +
                    "] calculatedLayout pre-set but node is not a group member");
            }
            finalizeCalculatedLayout();
            return;
        }

        if (layoutCallback != null) {
            LD result = layoutCallback.calculate(context);
            if (result != null) {
                calculatedLayout = result;
            }
        }
        finalizeCalculatedLayout();
    }

    // ── Finalisation ──────────────────────────────────────────────────────────

    /**
     * Ensure calculatedLayout is complete and ready for applyNode():
     *   1. Obtain a LayoutData from pool if still null
     *   2. Ensure a spatial region exists (fallback to requested or live)
     *   3. Flag all axes if none were flagged (first-frame guarantee)
     *   4. Inject effective visibility states from the parent chain
     *
     * Called by:
     *   - calculateLayout() after the layout callback
     *   - The manager after firing a group callback for each member
     *   - Directly by the manager for pre-populated nodes before applyNode()
     */
    public void finalizeCalculatedLayout() {
        if (calculatedLayout == null) {
            calculatedLayout = obtainLayoutData();
        }

        S region = calculatedLayout.getSpatialRegion();
        if (region == null) {
            if (renderable.hasRequestedRegion()) {
                region = renderable.getRequestedRegion();
            } else {
                region = renderable.getRegion();
            }
            if (region == null) {
                IllegalStateException t =  new IllegalStateException("Renderable.region is null");
                Log.logError("[LayoutNode:" + renderable.getName() + "] finalizeCalculatedLayout", t);
                throw t;
            }
            calculatedLayout.setSpatialRegion(region);
        }

        // First-frame guarantee — flag all axes if layout callback set none
        if (!calculatedLayout.hasAnyAxisChange()) {
            int n = renderable.getNumSpatialAxes();
            for (int i = 0; i < n; i++) {
                calculatedLayout.setAxisChange(i);
            }
        }

        injectEffectiveStates();
    }

    private void injectEffectiveStates() {
        boolean parentEffectivelyHidden = false;
        boolean parentEffectivelyInvisible = false;

        if (parent != null) {
            parentEffectivelyHidden = parent.getRenderable().isEffectivelyHidden();
            parentEffectivelyInvisible = parent.getRenderable().isEffectivelyInvisible();
        }

        boolean hiddenDesired = resolveHiddenDesired();
        boolean invisibleDesired = resolveInvisibleDesired();
        boolean effectivelyHidden = parentEffectivelyHidden || hiddenDesired;
        boolean effectivelyInvisible = parentEffectivelyInvisible || invisibleDesired;

        boolean wasEffectivelyHidden = renderable.isEffectivelyHidden();
        if(wasEffectivelyHidden != effectivelyHidden){
            calculatedLayout.setEffectivelyHidden(effectivelyHidden);
        }

        boolean wasEffectivelyInvisible = renderable.isEffectivelyInvisible();
        if(wasEffectivelyInvisible != effectivelyInvisible){
            calculatedLayout.setEffectivelyInvisible(effectivelyInvisible);
        }
    }

    private boolean resolveHiddenDesired() {
        Boolean fromData = calculatedLayout.getHidden();
        return fromData != null ? fromData
            : renderable.hasState(RenderableStates.STATE_HIDDEN_DESIRED);
    }

    private boolean resolveInvisibleDesired() {
        Boolean fromData = calculatedLayout.getInvisible();
        return fromData != null ? fromData
            : renderable.hasState(RenderableStates.STATE_INVISIBLE_DESIRED);
    }

    // ── Post-apply cleanup ────────────────────────────────────────────────────

    /**
     * Release calculatedLayout to pool after applyNode() commits it.
     * Called by the manager immediately after applyNode().
     */
    public void clear() {
        if (calculatedLayout != null) {
            recycleLayoutData(calculatedLayout);
            calculatedLayout = null;
        }
        contentMeasured = false;
        inFlightContext = null;
    }

    // ── Abstract pool methods ─────────────────────────────────────────────────

    protected abstract LD obtainLayoutData();
    protected abstract void recycleLayoutData(LD layoutData);

    // ── Queries ───────────────────────────────────────────────────────────────

    public LD       getCalculatedLayout()   { return calculatedLayout; }
    public R        getRenderable()         { return renderable; }
    public L        getParent()             { return parent; }
    public List<L>  getChildren()           { return children; }
    public String   getName()               { return renderable.getName(); }
    public int      getDepth()              { return renderable.getDepth(); }
    public boolean  isFloating()            { return renderable.isFloating(); }

    public S getRequestedRegion()           { return renderable.getRequestedRegion(); }

    public void setPositionAnchor(R anchor) { this.positionAnchor = anchor; }
    public R    getPositionAnchor()         { return positionAnchor; }

    @SuppressWarnings("unchecked")
    public L self() { return (L) this; }
}
