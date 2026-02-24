package io.netnotes.engine.ui.renderer.layout;

import java.util.ArrayList;
import java.util.List;

import io.netnotes.engine.state.BitFlagStateMachine.StateSnapshot;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.renderer.BatchBuilder;
import io.netnotes.engine.ui.renderer.Renderable;
import io.netnotes.engine.ui.renderer.RenderableStates;

/**
 * LayoutNode - Tree node for layout calculation
 * 
 * UNIFIED STATE INJECTION: All factory methods return region only.
 * computeEffectiveStates() injects ALL state after calculation.
 * 
 * GROUP CALLBACK SUPPORT: Nodes can belong to groups with predicated callbacks
 * that execute once for all members when the first member is encountered.
 */
public abstract class LayoutNode<
    B extends BatchBuilder<S>,
    R extends Renderable<B,P,S,LC,LD,LCB,GCB,?,?,G,R>,
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    LD extends LayoutData<B,R,S,LD,?>,
    LC extends LayoutContext<B,R,P,S,LD,LCB,LC,L>,
    LCB extends LayoutCallback<B,R,P,S,LC,LD,LCB>,
    GCB extends GroupLayoutCallback<B,R,P,S,LD,LC,L,GCB>,
    G extends LayoutGroup<B,R,P,S,LD,LC,GCB,L,?,G>,
    L extends LayoutNode<B,R,P,S,LD,LC,LCB,GCB,G,L>
> {
    protected final R renderable;
    protected L parent;
    private LCB callback;
    LD calculatedLayout;
    private final List<L> children = new ArrayList<>();
    protected R positionAnchor = null;
    protected G group = null;

  

    public LayoutNode(R renderable) {
        this.renderable = renderable;
    }

    public void addChild(L child) {
        children.add(child);
        child.parent = self();
    }

    public S getRequestedRegion() {
        return renderable.getRequestedRegion();
    }

    @SuppressWarnings("unchecked")
    public L self() {
        return (L) this;
    }

    public String getName() { return renderable.getName(); }

    /**
     * Calculate layout for this node
     * If part of a group and first member reached, executes group callbacks
     * Otherwise executes individual callback
     */
    public void calculate(LC context) {
        // If part of a group and it's already calculated, skip
        if (callback != null) {
            calculatedLayout = callback.calculate(context);
        }
        injectToCalculatedData();
    }

    public void injectToCalculatedData(){

        S calculatedRegion = calculatedLayout != null
            ? calculatedLayout.getSpatialRegion()
            : null;

        if (calculatedRegion == null) {
            if (renderable.hasRequestedRegion()) {
                calculatedRegion = renderable.getRequestedRegion();
            } else {
                calculatedRegion = renderable.getRegion();
            }
        }
        
        // Ensure we have a LayoutData instance
        if (calculatedLayout == null) {
            calculatedLayout = obtainLayoutData();
        }
        
        // Ensure we have a valid region
        if (calculatedRegion == null) {
            calculatedRegion = renderable.getRegionPool().obtain();
            calculatedRegion.setToIdentity();
        }

        calculatedLayout.setSpatialRegion(calculatedRegion);
        
        StateSnapshot snap = renderable.getStateMachine().getSnapshot();

        injectEffectiveStates(snap, calculatedLayout);

    }

    protected boolean checkHiddenDesired(StateSnapshot snap){
        boolean wasDesired = snap.hasState(RenderableStates.STATE_HIDDEN_DESIRED);
        Boolean isDesired = calculatedLayout.getHidden();
        if(isDesired != null){
            return isDesired;
        }
        return wasDesired;
    }


    protected boolean checkInvisibleDesired(StateSnapshot snap){
        boolean wasDesired = snap.hasState(RenderableStates.STATE_INVISIBLE_DESIRED);
        Boolean isDesired = calculatedLayout.getInvisible();
        if(isDesired != null){
            return isDesired;
        }
        return wasDesired;
    }

    protected boolean checkEnabledDesired(StateSnapshot snap){
        boolean wasDesired = snap.hasState(RenderableStates.STATE_ENABLED_DESIRED);
        Boolean isDesired = calculatedLayout.getEnabled();
        if(isDesired != null){
            return isDesired;
        }
        return wasDesired;
    }

  
    /**
     * UNIFIED STATE INJECTION - Single source of truth
     * All state changes flow through here, regardless of calculation path
     */
    private void injectEffectiveStates(StateSnapshot snap, LD layoutData) {
        R parentRenderable = parent != null ? parent.getRenderable() : null;
        boolean effectivelyVisible = parentRenderable != null ? parentRenderable.isEffectivelyVisible() : true;
        boolean effectivelyEnabled = parentRenderable != null ? parentRenderable.isEffectivelyEnabled() : true;

        boolean hiddenDesired = checkHiddenDesired(snap);
        boolean invisibleDesired = checkInvisibleDesired(snap);
        boolean enabledDesired = checkEnabledDesired(snap);
        
        effectivelyEnabled = effectivelyEnabled && enabledDesired;
        effectivelyVisible = effectivelyVisible && !hiddenDesired && !invisibleDesired;
        
        // Inject RESOLVED states (always)
        layoutData.setEffectivelyVisible(effectivelyVisible);
        layoutData.setEffectivelyEnabled(effectivelyEnabled);
        
        // Inject MODIFIER states (only if changed)
        if (hiddenDesired != renderable.hasState(RenderableStates.STATE_HIDDEN)) {
            layoutData.setHidden(hiddenDesired);
        }
        if (invisibleDesired != renderable.hasState(RenderableStates.STATE_INVISIBLE)) {
            layoutData.setInvisible(invisibleDesired);
        }
        if (enabledDesired != renderable.hasState(RenderableStates.STATE_ENABLED_DESIRED)) {
            layoutData.setEnabled(enabledDesired);
        }
    }


    public void clear() {
        if (calculatedLayout != null) {
            recycleLayoutData(calculatedLayout);
            calculatedLayout = null;
        }
    }

    // ===== ABSTRACT FACTORIES =====
    
    /**
     * Obtain LayoutData from pool
     */
    protected abstract LD obtainLayoutData();
    
    /**
     * Recycle LayoutData to pool
     */
    protected abstract void recycleLayoutData(LD layoutData);

    // ===== GETTERS/SETTERS =====

    public void setPositionAnchor(R anchor) {
        this.positionAnchor = anchor;
    }
    
    public R getPositionAnchor() {
        return positionAnchor;
    }

    public boolean isFloating() {
        return renderable.isFloating();
    }

    public LD getCalculatedLayout() {
        return calculatedLayout;
    }

    public R getRenderable() {
        return renderable;
    }

    public L getParent() {
        return parent;
    }

    public List<L> getChildren() {
        return children;
    }

    public int getDepth() {
        return renderable.getDepth();
    }

    public void setCallback(LCB callback) {
        this.callback = callback;
    }

    public void setGroup(G group) {
        if (this.group != null) {
            this.group.removeMember(self());
        }
        this.group = group;
        if (group != null) {
            group.addMember(self());
        }
    }
    
    public G getGroup() {
        return group;
    }

    public void clearGroupCalculation() {
        if (calculatedLayout != null) {
            recycleLayoutData(calculatedLayout);
            calculatedLayout = null;
        }
    }

    public boolean isGrouped() {
        return group != null;
    }
}