package io.netnotes.engine.core.system.control.ui.layout;

import java.util.List;

import io.netnotes.engine.core.system.control.ui.BatchBuilder;
import io.netnotes.engine.core.system.control.ui.Renderable;
import io.netnotes.engine.core.system.control.ui.SpatialPoint;
import io.netnotes.engine.core.system.control.ui.SpatialRegion;

/**
 * Context for layout calculation
 * 
 * HIDDEN SEMANTICS:
 * - invisible: children can calculate (parent provides bounds)
 * - hidden: children cannot calculate (parent bounds = null)
 * 
 * Null region forces explicit handling - no accidental calculations against phantom space.
 */
public abstract class LayoutContext<
    B extends BatchBuilder<S>,
    R extends Renderable<B,P,S,LC,LD,LCB,?,?,?,?,R>,
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    LD extends LayoutData<B,R,S,LD,?>,
    LCB extends LayoutCallback<B,R,P,S,LC,LD,LCB>,
    LC extends LayoutContext<B,R,P,S,LD,LCB,LC,L>,
    L extends LayoutNode<B,R,P,S,LD,LC,LCB,?,?,L>
> {
    private L node = null;
    private L parent = null;
    protected boolean parentEffectivelyVisible = true;
    protected boolean parentEffectivelyEnabled = true;

    public LayoutContext() {}

    public void initialize(L node) {
        this.node = node;
        this.parent = node.getParent();
        this.parentEffectivelyVisible = true;
        this.parentEffectivelyEnabled = true;
        
        if (parent != null) {
            R parentRenderable = parent.getRenderable();
            this.parentEffectivelyVisible = parentRenderable.isEffectivelyVisible();
            this.parentEffectivelyEnabled = parentRenderable.isEffectivelyEnabled();
        }
    }

    public void reset() {
        this.node = null;
        this.parent = null;
        this.parentEffectivelyVisible = true;
        this.parentEffectivelyEnabled = true;
    }

    public boolean getParentEffectivelyVisible() {
        return parentEffectivelyVisible;
    }

    public boolean getParentEffectivelyEnabled() {
        return parentEffectivelyEnabled;
    }

    /**
     * Parent region for layout calculation
     * 
     * Returns null if parent is hidden - child must handle explicitly.
     * Returns valid region if parent is invisible - child can calculate.
     */
    public S getParentRegion() {
        L parent = node.getParent();
        if (parent == null) {
            return null;
        }

        // Invisible parent = valid bounds, children can calculate
        return parent.getRenderable().getRegion();
    }

    public S getCurrentRegion() {
        R renderable = node.getRenderable();
        if( renderable.isHidden()) {
            return null;
        }
        return renderable.getRegion();
    }
    
    public S getCurrentAbsoluteRegion() {
        R renderable = node.getRenderable();
        if( renderable.isHidden()) {
            return null;
        }
        return renderable.getAbsoluteRegion();
    }

    
    L getParentNode() {
        return node.getParent();
    }
    
    public R getRenderable() {
        return node.getRenderable();
    }
    
    public S getRequestedRegion() {
        return node.getRenderable().getRequestedRegion();
    }
    
    public boolean hasRequestedRegion() {
        return node.getRenderable().hasRequestedRegion();
    }

     public R getSibling(int index) {
        L parent = node.getParent();
        if (parent == null) return null;
        
        List<L> siblings = parent.getChildren();
        if (index < 0 || index >= siblings.size()) return null;
        
        L sibling = siblings.get(index);
        return sibling.getRenderable();
     }
    
    public S getSiblingProposedRegion(int index) {
        R sibling = getSibling(index);
        if (sibling == null) return null;

        S requested = sibling.getRequestedRegion();
        if (requested != null) return requested;
        return sibling.getRegion();
    }

    public R getRoot(){
        R root = getRenderable();
        while (root.getParent() != null) {
            root = root.getParent();
        }
        return root;
    }

    public S getViewportRegion() {
        return getRoot().getRegion();
    }

    /**
     * Anchor region for floating positioning
     * Returns null if anchor is hidden - undefined position
     */
    public S getAnchorAbsoluteRegion() {
        L node = getNode();
        if (!node.isFloating()) {
            return null;
        }
        
        R anchor = node.getPositionAnchor();
        if (anchor == null) {
            return null;
        }
        
        if (anchor.isHidden()) {
            return null;
        }
        
        return anchor.getAbsoluteRegion();
    }
    
    public S getAnchorRegion() {
        L node = getNode();
        if (!node.isFloating()) {
            return null;
        }
        
        R anchor = node.getPositionAnchor();
        if (anchor == null) {
            return null;
        }
        
        if (anchor.isHidden()) {
            return null;
        }
        
        return anchor.getRegion();
    }
    
    public boolean wouldOverflowViewport(S region) {
        S viewport = getViewportRegion();
        return !viewport.contains(region);
    }
    
    protected L getNode() {
        return node;
    }

    @SuppressWarnings("unchecked")
    protected LC self() {
        return (LC) this;
    }
}