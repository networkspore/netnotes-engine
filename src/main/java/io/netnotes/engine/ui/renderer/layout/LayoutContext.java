package io.netnotes.engine.ui.renderer.layout;

import java.util.List;

import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.renderer.BatchBuilder;
import io.netnotes.engine.ui.renderer.Renderable;

public abstract class LayoutContext<
    B extends BatchBuilder<S>,
    R extends Renderable<B,P,S,L,LC,LD,LCB,?,?,?,R>,
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    LD extends LayoutData<B,R,S,LD,?>,
    LCB extends LayoutCallback<B,R,P,S,LC,LD,LCB>,
    LC extends LayoutContext<B,R,P,S,LD,LCB,LC,L>,
    L extends LayoutNode<B,R,P,S,LD,LC,LCB,?,?,L>
> {
    private L node   = null;
    private L parent = null;
    protected boolean parentEffectivelyVisible = true;
    private S measuredContentBounds = null;


    public LayoutContext() {}

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void initialize(L node) {
        this.node   = node;
        this.parent = node.getParent();
        this.parentEffectivelyVisible = true;

        if (parent != null) {
            LD parentCalculated = parent.getCalculatedLayout();
            if (parentCalculated != null && parentCalculated.getEffectivelyVisible() != null) {
                this.parentEffectivelyVisible = parentCalculated.getEffectivelyVisible();
            } else {
                this.parentEffectivelyVisible = parent.getRenderable().isEffectivelyVisible();
            }
        }
    }

    public void reset() {
        measuredContentBounds = null;
        this.node                   = null;
        this.parent                 = null;
        this.parentEffectivelyVisible = true;
    }


    // ── Parent geometry ───────────────────────────────────────────────────────

    /**
     * The region within which this node should lay itself out.
     *
     * Returns null if the parent is hidden — the callback must not produce
     * geometry. Returns a valid region if the parent is invisible — the node
     * still occupies space.
     *
     * Uses the parent's in-flight calculatedLayout if available (parent was
     * dirty this pass), otherwise the live committed region.
     *
     * The returned region is a fresh pool copy; callers are responsible for
     * recycling it.
     */
    public S getParentRegion() {
        if (parent == null) return null;

        LD parentCalculated = parent.getCalculatedLayout();
        if (parentCalculated != null && parentCalculated.hasRegion()) {
            S copy = parent.getRenderable().getRegionPool().obtain();
            copy.copyFrom(parentCalculated.getSpatialRegion());
            return copy;
        }

        return parent.getRenderable().getRegion();
    }

    // ── Current node geometry ─────────────────────────────────────────────────

    public S getCurrentRegion() {
        R r = node.getRenderable();
        return r.isHidden() ? null : r.getRegion();
    }

    public S getCurrentAbsoluteRegion() {
        R r = node.getRenderable();
        return r.isHidden() ? null : r.getAbsoluteRegion();
    }

    public S getRequestedRegion() {
        return node.getRenderable().getRequestedRegion();
    }

    public boolean hasRequestedRegion() {
        return node.getRenderable().hasRequestedRegion();
    }

    public S getMeasuredContentBounds() {
        return measuredContentBounds;
    }

    void setContentMeasurement(S contentBounds) {
        this.measuredContentBounds = contentBounds;
    }

    // ── Sibling access ────────────────────────────────────────────────────────

    public R getSibling(int index) {
        if (parent == null) return null;
        List<L> siblings = parent.getChildren();
        if (index < 0 || index >= siblings.size()) return null;
        return siblings.get(index).getRenderable();
    }

    public S getSiblingProposedRegion(int index) {
        R sibling = getSibling(index);
        if (sibling == null) return null;
        S requested = sibling.getRequestedRegion();
        return requested != null ? requested : sibling.getRegion();
    }

    // ── Viewport / anchor ─────────────────────────────────────────────────────

    public R getRoot() {
        R root = getRenderable();
        while (root.getParent() != null) root = root.getParent();
        return root;
    }

    public S getViewportRegion() {
        return getRoot().getRegion();
    }

    public boolean wouldOverflowViewport(S region) {
        return !getViewportRegion().contains(region);
    }

    public S getAnchorAbsoluteRegion() {
        L n = getNode();
        if (!n.isFloating()) return null;
        R anchor = n.getPositionAnchor();
        if (anchor == null || anchor.isHidden()) return null;
        return anchor.getAbsoluteRegion();
    }

    public S getAnchorRegion() {
        L n = getNode();
        if (!n.isFloating()) return null;
        R anchor = n.getPositionAnchor();
        if (anchor == null || anchor.isHidden()) return null;
        return anchor.getRegion();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean getParentEffectivelyVisible() { return parentEffectivelyVisible; }
    public R       getRenderable()               { return node.getRenderable(); }

    protected L getNode()    { return node; }
    L getParentNode()        { return parent; }

    @SuppressWarnings("unchecked")
    protected LC self()      { return (LC) this; }


}