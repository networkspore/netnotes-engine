package io.netnotes.engine.ui.renderer.layout;

import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.renderer.BatchBuilder;
import io.netnotes.engine.ui.renderer.Renderable;

/**
 * LayoutData - Desired output state from layout calculation
 * 
 * @param <B> BatchBuilder type
 * @param <R> Renderable type
 * @param <S> SpatialRegion type
 * @param <LD> Concrete LayoutData type
 * @param <LDB> Concrete Builder type
 */
public abstract class LayoutData<
    B extends BatchBuilder<S>,
    R extends Renderable<B,?,S,?,LD,?,?,?,?,?,R>,
    S extends SpatialRegion<?,S>,
    LD extends LayoutData<B,R,S,LD,LDB>,
    LDB extends LayoutData.Builder<B,R,S,LD,LDB>
> {
    protected S spatialRegion;

    
    protected Boolean invisible;
    protected Boolean hidden;
    protected Boolean enabled;
    protected Boolean effectivelyVisible;
    protected Boolean effectivelyEnabled;

    protected LayoutData(){}

    protected void initialize(LDB builder){
        this.invisible = builder.invisible;
        this.hidden = builder.hidden;
        this.enabled = builder.enabled;
        this.spatialRegion = builder.spatialRegion;
    }

    public void initialize(S spatialRegion){
        this.spatialRegion = spatialRegion;
    }

    public Boolean getInvisible() { return invisible; }
    public Boolean getHidden() { return hidden; }
    public Boolean getEnabled() { return enabled; }

    public Boolean getEffectivelyVisible() { return effectivelyVisible; }
    public Boolean getEffectivelyEnabled() { return effectivelyEnabled; }

    
    public boolean hasSpatialChanges() { return spatialRegion != null; }
   
    public boolean stateChanged(){
        return invisible != null || hidden != null || enabled != null;
    }

    public void reset(){
        spatialRegion = null;
        invisible = null;
        hidden = null;
        enabled = null;
        effectivelyVisible = null;
        effectivelyEnabled = null;
    }
    /**
     * Apply desired state to renderable
     * Subclasses implement dimension-specific application
    
    public void applyTo(R renderable){
        renderable.applyLayoutData(self());
    } */

    public void setSpatialRegion(S region) {
        this.spatialRegion = region;
    }
    
    public void setInvisible(boolean invisible) {
        this.invisible = invisible;
    }
    
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setEffectivelyVisible(boolean effectivelyVisible) {
        this.effectivelyVisible = effectivelyVisible;
    }

    public void setEffectivelyEnabled(boolean effectivelyEnabled) {
        this.effectivelyEnabled = effectivelyEnabled;
    }

  //  public void setRenderable(boolean renderable) {
   //     this.renderable = renderable;
   // }


    public void collapseRegion(){ spatialRegion.collapse(); }
    
    // ===== QUERIES (NO LOGIC) =====

 
    public boolean hasRegion() {
        return spatialRegion != null;
    }

    public S getSpatialRegion(){
        return spatialRegion;
    }
    
    public boolean hasStateChanges() {
        return invisible != null || hidden != null || enabled != null ||
            effectivelyVisible != null || effectivelyEnabled != null; //|| renderable != null;
    }
    
    
    // ===== RECYCLING =====
    
    /**
     * Recycle resources (for pooling)
     * Subclasses implement dimension-specific cleanup
     */
    public abstract void recycleRegion();
    
    @SuppressWarnings("unchecked")
    protected LD self() {
        return (LD) this;
    }
    
    /**
     * Builder base - accumulates desired state
     */
    public abstract static class Builder<
        B extends BatchBuilder<S>,
        R extends Renderable<B,?,S,?,LD,?,?,?,?,?,R>,
        S extends SpatialRegion<?,S>,
        LD extends LayoutData<B,R,S,LD,LDB>,
        LDB extends LayoutData.Builder<B,R,S,LD,LDB>
    > {
    
        protected Boolean invisible = null;
        protected Boolean hidden = null;
        protected Boolean enabled = null;
        protected S spatialRegion = null;
        
        @SuppressWarnings("unchecked")
        protected LDB self() {
            return (LDB) this;
        }

         public LDB invisible(boolean invisible) {
            this.invisible = invisible;
            return self();
        }
        
        public LDB hidden(boolean hidden) {
            this.hidden = hidden;
            return self();
        }
        
        public LDB region(S spatialRegioin){
            this.spatialRegion = spatialRegioin;
            return self();
        }

        public LDB enabled(boolean effectivelyEnabled) {
            this.enabled = effectivelyEnabled;
            return self();
        }

        public void reset(){
            this.invisible = null;
            this.hidden = null;
            this.enabled = null;
            this.spatialRegion = null;
        }   

        /**
         * Build immutable LayoutData from accumulated wishes
         */
        public abstract LD build();
    }
}