package io.netnotes.engine.ui.renderer;

import io.netnotes.engine.ui.PooledRegion;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.SpatialRegionPool;
import io.netnotes.engine.ui.VisibilityPredicate;
import io.netnotes.engine.ui.renderer.layout.GroupLayoutCallback;
import io.netnotes.engine.ui.renderer.layout.LayoutCallback;
import io.netnotes.engine.ui.renderer.layout.LayoutContext;
import io.netnotes.engine.ui.renderer.layout.LayoutData;
import io.netnotes.engine.ui.renderer.layout.LayoutGroup;
import io.netnotes.engine.ui.renderer.layout.LayoutNode;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralKeyDownEvent;
import io.netnotes.engine.io.input.ephemeralEvents.EphemeralRoutedEvent;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.io.input.events.EventFilter;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.io.input.events.EventHandlerRegistry.RoutedEventHandler;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.state.BitFlagStateMachine.StateSnapshot;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.LoggingHelpers.LogLevel;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Renderable - Abstract base class for stateful, event-driven rendering
 * 
 * ARCHITECTURE:
 * - Generic spatial region support (2D rectangles, 3D boxes, etc.)
 * - Zero-allocation damage propagation via object pooling
 * - Event filtering by source path
 * - State-driven invalidation with parent propagation
 * - Thread-safe visibility resolution
 * 
 * THREADING MODEL:
 * - Tree mutations (add/remove child) serialized via serialExec
 * - Damage tracking assumes caller synchronization (layout is already serialized)
 * - Visibility resolution is synchronous, walks tree immediately
 * - State machine transitions with callbacks use serialExec
 * - Rendering is single-threaded, reads damage atomically
 * 
 * @param <B> BatchBuilder type for rendering
 * @param <R> Concrete Renderable type (self-type)
 * @param <S> SpatialRegion type (UIRectangle, UIBox3D, etc.)
 */
public abstract class Renderable<
    B extends BatchBuilder<S>,
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    L extends LayoutNode<B,R,P,S,LD,LC,LCB,GCB,G,L>,
    LC extends LayoutContext<B,R,P,S,LD,LCB,LC,L>,
    LD extends LayoutData<B,R,S,LD,?>,
    LCB extends LayoutCallback<B,R,P,S,LC,LD,LCB>,
    GCB extends GroupLayoutCallback<B,R,P,S,LD,LC,L,GCB>,
    GSE extends Renderable.GroupStateEntry<R,GCB,GSE>,
    G extends LayoutGroup<B,R,P,S,LD,LC,GCB,L,G>,
    R extends Renderable<B,P,S,L,LC,LD,LCB,GCB,GSE,G,R>
> {
    

    private static final LogLevel LOG_LEVEL = LogLevel.GENERAL;


    private Map<R, LCB> childLayoutCallbacks  = new HashMap<>();

    protected S localDamage = null;      // Damage in local coords (what region of THIS node changed)
    protected S absoluteDamage = null;   // Damage in absolute coords (for efficient clipping)
    protected boolean childrenDirty = false;

    // ===== SPATIAL REGION POOL =====
    // Strategy: Thread-local pools eliminate allocation overhead
    // Regions are recycled after use, reused in subsequent invalidations
    
    protected final SpatialRegionPool<S> regionPool;
    
    // ===== IDENTITY =====
    protected final String name;
    protected final BitFlagStateMachine stateMachine;
    
    // ===== HIERARCHY =====
    protected R parent = null;
    protected final List<R> children = new ArrayList<>();
    protected int zOrder = 0;
    private List<R> sortedRenderChildren = null;

    
    // ===== LAYOUT - DIMENSION AGNOSTIC =====
    protected S region;
    protected S requestedRegion = null;
 
    protected final ConcurrentHashMap<String,GSE> childGroups  = new ConcurrentHashMap<>();
    
    // ===== Floating =====

    // Layer constants
    public static final int LAYER_NORMAL = 0;
    public static final int LAYER_FLOATING = 1;
    public static final int LAYER_MODAL = 2;
    public static final int LAYER_NOTIFICATION = 3;

    protected R logicalParent = null;      // For event routing, lifecycle
    protected R renderingParent = null;    // For clipping, rendering
    protected R positionAnchor = null;     // For floating position calculation
    protected boolean isFloating = false;
    protected int layerIndex = LAYER_NORMAL;


    
    // ===== EVENT HANDLING =====
    protected final EventHandlerRegistry eventRegistry = new EventHandlerRegistry();
    protected CompletableFuture<RoutedEvent> keyWaitFuture = null;
    protected CompletableFuture<Void> anyKeyFuture = null;
    protected NoteBytesReadOnly keyWaitHandlerId = null;

    //uiExecutor is re-entrant safe && defers execution during layout
    protected final SerializedVirtualExecutor uiExecutor = VirtualExecutors.getUiExecutor();

    // ===== FOCUS =====
    private static long FOCUS_REQUEST_SEQ = 0;
    protected boolean focusable = false;
    protected int focusIndex = -1;
    private long focusRequestToken = 0;
    
    protected RenderableLayoutManagerHandle<R,LCB,G,GCB> layoutManager = null;
    protected Consumer<R> onRenderRequest = null;
    protected BiConsumer<S,S> notifyOnRegionChanged = null;
    protected Consumer<R> notifyRemovedFromLayout = null;
    protected VisibilityPredicate<R> visibilityPolicy = null;
    protected BiConsumer<R, Boolean> onVisibilityChanged = null;
    protected BiConsumer<R, Boolean> onFocusChanged = null;
    protected Consumer<S> damageAccumulator = null;
    protected RenderPhase renderPhase = RenderPhase.DETACHED;
    /**
     * Constructor
     * 
     * @param name Component name for debugging
     * @param regionPool Pool for spatial region allocation 
     */
    protected Renderable(String name, SpatialRegionPool<S> regionPool) {
        this.name = name;
        this.regionPool = regionPool;
        this.stateMachine = new BitFlagStateMachine(name);
        this.stateMachine.setSerialExecutor(uiExecutor); //on state changes execute on executor
        this.region = regionPool.obtain();
        this.region.setToIdentity();
        
        stateMachine.addState(RenderableStates.STATE_ENABLED_DESIRED);
        
        setupBaseTransitions();
        setupStateTransitions();
        setupEventHandlers();
    }
    
    public void setOnVisibilityChanged(BiConsumer<R, Boolean> onVisibilityChanged){
        this.onVisibilityChanged = onVisibilityChanged;
    }

    public void setVisibilityPolicy(VisibilityPredicate<R> visibilityPolicy){
        this.visibilityPolicy = visibilityPolicy;
    }
    public void setNotifyRemovedFromLayout(Consumer<R> notifyRemovedFromLayout){
        this.notifyRemovedFromLayout = notifyRemovedFromLayout;
    }
    public abstract SpatialRegionPool<S> getRegionPool();

    protected abstract void setupEventHandlers();

    public boolean isDestroyed(){ return stateMachine.hasState(RenderableStates.DESTROYED); }
    public boolean isStarted() { return stateMachine.hasState(RenderableStates.STATE_STARTED); }

    protected void setupBaseTransitions() {
        // Visibility state changes
        stateMachine.onStateAdded(RenderableStates.STATE_RENDERABLE, (old, now, bit) -> {
            onRenderable();
        });

        stateMachine.onStateAdded(RenderableStates.STATE_STARTED, (old, now, bit) -> {
            Log.logMsg("[Renderable: "+name+"] onStarted" , LogLevel.IMPORTANT);
            onStarted();
        });

        stateMachine.onStateAdded(RenderableStates.STATE_ATTACHED, (old, now, bit) -> {
            onAttachedToParent();
        });

        stateMachine.onStateRemoved(RenderableStates.STATE_ATTACHED, (old, now, bit) -> {
            onRemovedFromParent();
        });
        
        stateMachine.onStateRemoved(RenderableStates.STATE_RENDERABLE, (old, now, bit) -> {
            // Clear focus when hidden
            if (hasFocus()) {
                clearFocus();
            }
            onHide();
        });
        
        // Hdden state changes
        stateMachine.onStateAdded(RenderableStates.STATE_HIDDEN, (old, now, bit) -> {
            if(onVisibilityChanged != null){
                onVisibilityChanged.accept(self(), false);
            }

            onHidden();
        });
        
        stateMachine.onStateRemoved(RenderableStates.STATE_HIDDEN, (old, now, bit) -> {
            if(onVisibilityChanged != null){
                onVisibilityChanged.accept(self(), true);
            }
            // Request layout to restore size
            onUnhide();
        });
        

        // Focus state changes
        stateMachine.onStateAdded(RenderableStates.STATE_FOCUSED, (old, now, bit) -> {
            onFocusGained();
        });
        
        stateMachine.onStateRemoved(RenderableStates.STATE_FOCUSED, (old, now, bit) -> {
            onFocusLost();
        });
    }
    
    protected abstract void setupStateTransitions(); 

    public boolean hasState(int state) {
        return stateMachine.hasState(state);
    }
    
    // ===== HIERARCHY =====
    
    public R getParent() { return parent; }
    public List<R> getChildren() { return new ArrayList<>(children); }
    
    public void addChild(R child) {
        addChild(child, null);
    }


    public void addChild(R child, LCB layoutCb){
        if(uiExecutor.isCurrentThread()){
            addChildInternal(child, layoutCb);
        }else{
            uiExecutor.executeFireAndForget(() -> addChildInternal(child, layoutCb));
        }
    }

    private void addChildInternal(R child,LCB layoutCallback) {
        if (isDestroyed()) return;

        if (child.parent != null) {
            child.parent.removeChild(child);
        }

        children.add(child);
        child.parent = self();
        child.setDamageAccumulator(this.damageAccumulator);
        invalidateRenderChildrenCache();

        if (layoutCallback  != null) childLayoutCallbacks.put(child, layoutCallback);

        child.stateMachine.addState(RenderableStates.STATE_ATTACHED);

        if (layoutManager != null) {
            layoutManager.registerChild(child, layoutCallback);
            requestLayoutUpdate();
        }
        child.requestLayoutUpdate();
    }

    void invalidateRenderChildrenCache() {
        sortedRenderChildren = null;
        childrenDirty = true;
    }

    private void invalidateRenderChildrenCache(R renderable) {
        if (renderable != null) {
            renderable.invalidateRenderChildrenCache();
        }
    }


    // ===== REQUESTED REGION SYSTEM =====

    public LCB getChildLayoutCallback(R child)  { return childLayoutCallbacks.get(child); }

    protected void ensureRequestedRegion() {
        if (requestedRegion == null) {
            requestedRegion = regionPool.obtain();
            requestedRegion.copyFrom(region);
        }
    }
    
    public void clearRequestedRegion(){
        S oldRequestedRegion = requestedRegion;
        requestedRegion = null;
        if(oldRequestedRegion != null){
            regionPool.recycle(oldRequestedRegion);
        }
    }
    
    public S getRequestedRegion() {
        if (requestedRegion != null) {
            S copy = regionPool.obtain();
            copy.copyFrom(requestedRegion);
            return copy;
        }
        return getRegion();
    }
    
    public boolean hasRequestedRegion() {
        return requestedRegion != null;
    }

    protected void notifyContentChanged(){
        if(isSizedByContent()){
            requestLayoutUpdate();
        }
        invalidate();
    }

    public void setPosition(P position) {
        // Silent no-op while hidden - use setHiddenRegion() for explicit staging
        if (isHidden()) {
            return;
        }
        
        ensureRequestedRegion();
        requestedRegion.setPosition(position);
        requestLayoutUpdate();
    }
    
    /***
     * Set Region and propagate size
     * 
     * @param region Spatial Region
     */
    public void setRegion(S region) {
        ensureRequestedRegion();
        requestedRegion.copyFrom(region);
        requestLayoutUpdate();
    }

    protected void applyRegionState(boolean isLayoutManaged, boolean isOffScreen){
        if(isLayoutManaged && isOffScreen){
            stateMachine.addStates(
                RenderableStates.STATE_CONTAINER_LAYOUT_MANAGED, 
                RenderableStates.STATE_CONTAINER_OFF_SCREEN);
        }else if(isLayoutManaged){
            stateMachine.addState(RenderableStates.STATE_CONTAINER_LAYOUT_MANAGED);
            stateMachine.removeState(RenderableStates.STATE_CONTAINER_OFF_SCREEN);
        }else if(isOffScreen){
            stateMachine.addState(RenderableStates.STATE_CONTAINER_OFF_SCREEN);
            stateMachine.removeState(RenderableStates.STATE_CONTAINER_LAYOUT_MANAGED);
        }else{
            stateMachine.removeStates(
                RenderableStates.STATE_CONTAINER_OFF_SCREEN,
                RenderableStates.STATE_CONTAINER_LAYOUT_MANAGED
            );
        }
    }

    void updateRegion(S region, boolean isLayoutManaged, boolean isOffScreen) {
        ensureRequestedRegion();
        requestedRegion.copyFrom(region);
        requestLayoutUpdate();
    }

    public void setBounds(S bounds){
        setRegion(bounds);
    }
    
    public void removeChild(R child) {
        if(uiExecutor.isCurrentThread()){
            removeChildInternal(child);
        }else{
            uiExecutor.execute(() -> removeChildInternal(child));
        }
    }

    protected void removeChildInternal(R child) {
        boolean removed = children.remove(child);
        if (!removed) return;
        invalidateRenderChildrenCache();
        
        // Damage the region where child was
        if (child.region != null && !child.region.isEmpty()) {
            try (PooledRegion<S> childLocal = regionPool.obtainPooled()) {
                S damage = childLocal.get();
                damage.copyFrom(child.region);
                damage.setToIdentityPosition();          // local origin = (0,0)
                damage.transformByParent(child.region);  // now in parent-local space
                propagateDamageUp(damage);
            }
        }
        child.setDamageAccumulator(null);
        childLayoutCallbacks.remove(child);
        removeChildGroupMember(child);
        child.parent = null;
        child.stateMachine.removeState(RenderableStates.STATE_ATTACHED);
        
        if (layoutManager != null) layoutManager.unregister(child);
    }

    /**
     * Removes all children in a single batched operation.
     *
     * Optimization over looping removeChild():
     * - One snapshot copy, one ArrayList.clear() (O(1) amortized vs O(n²))
     * - One childrenDirty pass instead of n passes
     * - Layout notifications are still fired per child (required for cleanup),
     *   but damage propagation is coalesced into a single full-region invalidation.
     *
     * Must be called on the uiExecutor (or will be dispatched there if not already).
     */
    public void clearChildren(){
        if(uiExecutor.isCurrentThread()){
            clearChildrenInternal();
        }else{
             uiExecutor.execute(this::clearChildrenInternal);
        }
    }

    private void clearChildrenInternal() {
        if (children.isEmpty()) return;

        // 1. Snapshot before clearing
        final List<R> removed = new ArrayList<>(children);

 
        children.clear();
        invalidateRenderChildrenCache();

        S unionDamage = null;
        for (R child : removed) {
            if (child.region != null && !child.region.isEmpty()) {
                if (unionDamage == null) {
                    unionDamage = regionPool.obtain();
                    unionDamage.copyFrom(child.region);
                } else {
                    S merged = unionDamage.union(child.region);
                    regionPool.recycle(unionDamage);
                    unionDamage = merged;
                }
            }
        }

        if (unionDamage != null) {
            propagateDamageUp(unionDamage);
            regionPool.recycle(unionDamage);
        }

        for (R child : removed) {
            child.setDamageAccumulator(null);
            removeChildGroupMember(child);
            child.parent = null;
            child.stateMachine.removeState(RenderableStates.STATE_ATTACHED);
            if (layoutManager != null) layoutManager.unregister(child);
        }

        Log.logMsg("[Renderable:" + name + "] clearChildren() removed " + removed.size() + " children", LOG_LEVEL);
    
    }
        
    public int getZOrder() { return zOrder; }
    
    public void setZOrder(int z) {
        if(uiExecutor.isCurrentThread()){
            setZOrderInternal(z);
        }else{
            uiExecutor.executeFireAndForget(()->setZOrderInternal(z));
        }
    }

    private void setZOrderInternal(int z){
        if (this.zOrder != z) {
            this.zOrder = z;
            if (parent != null) {
                parent.sortChildren();
                parent.invalidate();
            } else {
                invalidate();
            }
        }
    }
    
    protected void sortChildren() {
        if(uiExecutor.isCurrentThread()){
            sortChildrenInternal();
        }else{
            uiExecutor.executeFireAndForget(this::sortChildrenInternal);
        }
        
    }

    private void sortChildrenInternal() {
        children.sort(Comparator.comparingInt(Renderable::getZOrder));
        invalidateRenderChildrenCache();
    }
    
    // ===== SPATIAL OPERATIONS =====

    public S getRegion() {
        S copy = regionPool.obtain();
        copy.copyFrom(region);
        return copy;
    }

    public S getAbsoluteRegion() {
        S copy = regionPool.obtain();
        copy.copyFrom(region);
        copy.setPosition(region.getAbsolutePosition());
        return copy;
    }

    public S getEffectiveAbsoluteRegion(){
        if(isEffectivelyVisible() && !isHidden()){
            return getAbsoluteRegion();
        }else{
            return null;
        }
    }
  
    public boolean hitTest(P point) {
        return region.containsPoint(point);
    }
    
    public R hitTestChildren(P point) {
        for (int i = children.size() - 1; i >= 0; i--) {
            R child = children.get(i);
            if (!child.isVisible()) continue;
            
            if (child.hitTest(point)) {
                R deeperHit = child.hitTestChildren(point);
                return deeperHit != null ? deeperHit : child;
            }
        }
        return null;
    }
    
    // ===== DAMAGE TRACKING =====
    
    /**
     * @param localRegion Region to invalidate in local coordinates, null for full invalidation
     */
    public void invalidate(S localRegion) {
      
        // Accumulate local damage
        if (localRegion == null) {
            if (localDamage != null) {
                regionPool.recycle(localDamage);
            }
            localDamage = regionPool.obtain();
            localDamage.copyFrom(region);
        } else {
            if (localDamage == null) {
                localDamage = regionPool.obtain();
                localDamage.copyFrom(localRegion);
            } else {
                localDamage.unionInPlace(localRegion);
                
                if (localDamage.contains(region)) {
                    localDamage.copyFrom(region);
                }
            }
        }
        
        updateAbsoluteDamage();
        reportDamage(absoluteDamage);
        propagateDamageToParent();
        
        if (parent == null && onRenderRequest != null) {
            onRenderRequest.accept(self());
        }
    }

    protected void updateAbsoluteDamage() {
        if (localDamage == null) {
            if (absoluteDamage != null) {
                regionPool.recycle(absoluteDamage);
                absoluteDamage = null;
            }
            return;
        }
        
        if (absoluteDamage == null) {
            absoluteDamage = regionPool.obtain();
        }
        
        absoluteDamage.copyFrom(localDamage);
        
        absoluteDamage.setParentAbsolutePosition(
            region.getParentAbsolutePosition()
        );
      
    }

    // ===== PER-AXIS SIZE DEPENDENCY ==========================================
 
    public abstract int getNumSpatialAxes();
    public abstract boolean isPositionAxis(int axis);        // position vs size axis
    public abstract boolean isAxisParentDependent(int axis);
    public abstract boolean isAxisContentDependent(int axis);


    public boolean isSizedByContent() {
        int n = getNumSpatialAxes();
        for (int i = 0; i < n; i++) {
            if (!isPositionAxis(i) && getSizePreference(i) == SizePreference.FIT_CONTENT) {
                return true;
            }
        }
        return false;
    }

    public final boolean isSizedByParent() {
        int n = getNumSpatialAxes();
        for (int i = 0; i < n; i++) if (isAxisParentDependent(i)) return true;
        return false;
    }
    
   /**
     * Propagate damage to parent
     */
    protected void propagateDamageToParent() {
        R damageTarget = isFloating ? getLogicalParent() : getRenderingParent();
        
        if (damageTarget == null || localDamage == null) return;
        
        try (PooledRegion<S> pooled = regionPool.obtainPooled()) {
            S parentLocalDamage = pooled.get();
            
            parentLocalDamage.copyFrom(localDamage);
            parentLocalDamage.transformByParent(region);
            
            damageTarget.propagateDamageUp(parentLocalDamage);
        }
    }

    /**
     * Child invalidated - accumulate damage
     * 
     * @param childDamageInOurLocalSpace Damage region in our local coordinate space
     *                                   (caller will recycle this after we return)
     */
    protected void propagateDamageUp(S childDamageInOurLocalSpace) {
        childrenDirty = true;
        
        if (localDamage == null) {
            // First child damage - obtain and copy
            localDamage = regionPool.obtain();
            localDamage.set(childDamageInOurLocalSpace);
        } else {
            // Merge with existing damage IN PLACE
            localDamage.unionInPlace(childDamageInOurLocalSpace);
        }
        
        updateAbsoluteDamage();
        propagateDamageToParent();
        
        if (parent == null && onRenderRequest != null) {
            Log.logMsg("[Renderable: " + getName() +"] invalidateChild called renderRequest - localDamage: " + localDamage, LOG_LEVEL);
        
            onRenderRequest.accept(self());
        }
    }
    
    /**
     * Convenience: invalidate entire node
     */
    public void invalidate() {
        invalidate(null);
    }
    
    // ===== RENDERING =====
    
      /**
     * Root-level entry point. Called by ContainerHandle with no incoming clip.
     * Guards the common "nothing at all to do" case before allocating anything.
     */
    public void toBatch(B batch) {
        if (absoluteDamage == null && !childrenDirty) {
            return;
        }

        S fullSpace = regionPool.obtain();
        fullSpace.copyFrom(region);

        toBatch(batch, fullSpace);

        regionPool.recycle(fullSpace);
    }

    /**
     * Public entry point. Called by parent nodes and ContainerHandle.
     *
     * @param batch      target batch builder
     * @param clipRegion clip in absolute screen coordinates
     */
    public void toBatch(B batch, S clipRegion) {
        toBatchInternal(batch, clipRegion, null);
    }


    /**
     * Render this node into the batch, constrained to clipRegion (absolute coords).
     *
     * DAMAGE MODEL:
     *   hasSelfDamage  → renderSelf() repaints background within the damaged area.
     *                    Any child whose absolute bounds intersect that area loses its
     *                    on-screen pixels and MUST be restored, even if it carries no
     *                    own pending damage. forcedRegion propagates this requirement
     *                    recursively so grandchildren are also restored.
     *
     *   childrenDirty  → structural change (add / remove / reorder). All children
     *                    are visited; each renders only what its own damage flags say.
     *
     *   forcedRegion   → set by a parent that just repainted its background. Any node
     *                    whose absolute bounds intersect forcedRegion renders self +
     *                    propagates the forced region to its own children.
     *                    Never allocated here — it is always a region already owned
     *                    by the caller's pool scope.
     *
     * @param batch        target batch builder
     * @param clipRegion   incoming clip in absolute screen coordinates
     * @param forcedRegion nullable; absolute region whose pixels were just overwritten
     *                     by a parent's renderSelf() and must be restored
     */
    void toBatchInternal(B batch, S clipRegion, S forcedRegion) {
        if (!isVisible()) return;

        try (PooledRegion<S> absBoundsPooled = regionPool.obtainPooled()) {
            S absBounds = absBoundsPooled.get();
            absBounds.copyFrom(region);

            R node = getRenderingParent();
            while (node != null) {
                absBounds.transformByParent(node.region);
                node = node.getRenderingParent();
            }

            if (!absBounds.intersects(clipRegion)) return;

            try (PooledRegion<S> visibleClipPooled = regionPool.obtainPooled()) {
                S visibleClip = visibleClipPooled.get();
                visibleClip.copyFrom(absBounds.intersection(clipRegion));

                boolean hasSelfDamage = absoluteDamage != null;
                boolean isForced      = forcedRegion != null && absBounds.intersects(forcedRegion);

                if (hasSelfDamage) {
                    // Constrain renderSelf to the ACTUAL damaged area only.
                    // This prevents the background fill from overwriting undamaged cells
                    // (e.g. labels) that are already correct on the terminal.
                    try (PooledRegion<S> renderClipPooled = regionPool.obtainPooled()) {
                        S renderClip = renderClipPooled.get();
                        renderClip.copyFrom(absoluteDamage.intersection(visibleClip));

                        if (!renderClip.isEmpty()) {
                            batch.pushClipRegion(renderClip);
                            try {
                                renderSelf(batch);
                            } catch (Exception e) {
                                Log.logError("[Renderable:" + getName() + "] renderSelf exception", e);
                            }finally{
                                 batch.popClipRegion();
                            }

                            // renderSelf painted `renderClip`. Any child overlapping it was
                            // overwritten and must restore itself — pass renderClip as forcedRegion.
                            // Children outside renderClip are untouched — skip them.
                            try {
                                renderChildrenByLayer(batch, visibleClip, renderClip);
                    
                            } catch (Exception e) {
                                Log.logError("[Renderable:" + getName() + "] renderChildren exception", e);
                            }
                        }
                        if (localDamage != null) { regionPool.recycle(localDamage); localDamage = null; }
                        if (absoluteDamage != null) { regionPool.recycle(absoluteDamage); absoluteDamage = null; }
                    }

                

                } else if (isForced) {
                    // A parent painted over us — render with full visibleClip and
                    // propagate the forced region to our children.
                    batch.pushClipRegion(visibleClip);
                  
                    try {
                        renderSelf(batch);
                    } catch (Exception e) {
                        Log.logError("[Renderable:" + getName() + "] renderSelf (forced) exception", e);
                    }finally{
                        batch.popClipRegion();
                    }

                    try {
                        renderChildrenByLayer(batch, visibleClip, visibleClip);
          
                    } catch (Exception e) {
                        Log.logError("[Renderable:" + getName() + "] renderChildren (forced) exception", e);
                    }
                    S lD = localDamage;
                    localDamage = null;
                    if (lD != null) { 
                        regionPool.recycle(lD); 
                    }
                    S aD = absoluteDamage;
                    absoluteDamage = null;
                    if (aD != null) { 
                        regionPool.recycle(aD); 
                    }
                }
                
                if (childrenDirty) {
                    try {
                        renderChildrenByLayer(batch, visibleClip, null);
                    } catch (Exception e) {
                        Log.logError("[Renderable:" + getName() + "] renderChildren (structural) exception", e);
                    }
                   
                }

                childrenDirty = false;
            }
        } catch (Exception e) {
            Log.logError("[Renderable:" + getName() + "] toBatch failed", e);
        }
    }
    
    /**
     * Render all rendering-children sorted by layer then z-order.
     *
     * @param batch        target batch builder
     * @param visibleClip  absolute clip for this parent's visible area
     * @param forcedRegion nullable; absolute region that was just repainted by this
     *                     node's renderSelf — children overlapping it must restore
     *                     themselves even with no own pending damage
     */
    protected void renderChildrenByLayer(B batch, S visibleClip, S forcedRegion) {
        if (sortedRenderChildren == null) {
            sortedRenderChildren = children.stream()
                .filter(c -> c.getRenderingParent() == self())
                .sorted(Comparator.comparingInt(R::getLayerIndex)
                                .thenComparingInt(R::getZOrder))
                .collect(Collectors.toList());
        }
        for (R child : sortedRenderChildren) {
            child.toBatchInternal(batch, visibleClip, forcedRegion);
        }
        childrenDirty = false;
    }


    
   
    
    /**
     * Subclasses implement to render their content
     * Called with clip region already set in batch
     */
    protected abstract void renderSelf(B batch);
    

    /**
     * Request layout update - DELEGATES VIA CALLBACK
     * can mark dirty during layout
     * - State transitions queue during layout
     * - Fired after layout completes
     * - Resulting dirty marks processed in next pass
     */
    public void requestLayoutUpdate() {
        if (layoutManager != null) layoutManager.markLayoutDirty(self());
    }
    
   
    // ===== EVENT HANDLING =====
    

    public boolean dispatchEvent(RoutedEvent event) {
        if (!canReceiveEvents()) {
            return false;
        }
        
        boolean handled = false;
        if (!isKeyboardEvent(event) || !isFocusable() || hasFocus()) {
            handled = eventRegistry.dispatch(event);
        }
        
        if (event.isConsumed()) {
            return true;
        }
        
        // Use LOGICAL children for event bubbling
        if (!children.isEmpty()) {
            for (R child : getLogicalChildren()) {
                if (child.dispatchEvent(event)) {
                    break;
                }
                if (event.isConsumed()) {
                    break;
                }
            }
        }
        
        return handled;
    }

    void advanceRenderPhase(RenderPhase next) {
        RenderPhase was = this.renderPhase;
        if (was != next){
            this.renderPhase = next;
            
            switch(next){
                case APPLYING:
                    onApplying();
                    break;
                case COLLECTING:
                    onCollecting();
                    break;
                case DETACHED:
                    stateMachine.removeState(RenderableStates.STATE_STARTED);
                    break;
                case RENDERED:
                    onRendered();
                    if ( !isStarted()) {
                        stateMachine.addState(RenderableStates.STATE_STARTED);
                    }
                    break;
                case IDLE:
                    onIdle();
            }
        }else{
            Log.logError("[Renderable:" +name+"] "
                + " advanceRenderPhase was: " + was + " should be:" + next
            );
        }
    }

      /** Returns this node's current position in the render pipeline. */
    public RenderPhase getRenderPhase() {
        return renderPhase;
    }
    protected void onIdle() { }
    protected void onCollecting() { }
    protected void onApplying() { }
    protected void onRendered() {}

    /**
     * Measures the content of this renderable within the given context.
     * Implementations should update their requested region based on content size and layout rules
     * and set context.setContentMeasurement(measuredBounds)
     * @param context
     */
    public abstract void measureContent(LC context);

    private boolean isKeyboardEvent(RoutedEvent event) {
        NoteBytes type = event.getEventTypeBytes();
        return EventBytes.EVENT_KEY_DOWN.equals(type)
            || EventBytes.EVENT_KEY_UP.equals(type)
            || EventBytes.EVENT_KEY_REPEAT.equals(type)
            || EventBytes.EVENT_KEY_CHAR.equals(type);
    }

    protected List<R> getLogicalChildren() {
        // Filter to only logical children (not floating away)
        return children.stream()
            .filter(c -> !c.isFloating || c.getLogicalParent() == this)
            .collect(Collectors.toList());
    }
    
    public NoteBytesReadOnly addEventListener(
            NoteBytesReadOnly eventType,
            Consumer<RoutedEvent> handler) {
        return eventRegistry.register(eventType, handler);
    }
    
    public NoteBytesReadOnly addEventListener(
            NoteBytesReadOnly eventType,
            Consumer<RoutedEvent> handler,
            EventFilter filter) {
        return eventRegistry.register(eventType, handler, filter);
    }
    
    public void removeEventListener(NoteBytesReadOnly handlerId) {
        eventRegistry.unregister(handlerId);
    }
    
    public EventHandlerRegistry getEventRegistry() {
        return eventRegistry;
    }
    
    // ===== FOCUS =====
    
    public boolean isFocusable() {
        return focusable && isVisible();
    }
    
    public void setFocusable(boolean f) { this.focusable = f; }
    public boolean hasFocus() { return stateMachine.hasState(RenderableStates.STATE_FOCUSED); }

    public void setOnFocusChanged(BiConsumer<R,Boolean> onFocusChanged){
        this.onFocusChanged = onFocusChanged;
    }


    protected void onFocusGained() {
        onFocus();
        if(onFocusChanged != null){
            onFocusChanged.accept(self(), true);
        }
        invalidate();
    }
    
    protected void onFocusLost() {
        onBlur();
        if(onFocusChanged != null){
            onFocusChanged.accept(self(), false);
        }
        invalidate();
    }

    protected void onFocus() {}
    protected void onBlur() {}
    
    public int getFocusIndex() { return focusIndex; }
    public void setFocusIndex(int index) { this.focusIndex = index; }
    
    public List<R> getFocusableDescendants() {
        List<R> result = new ArrayList<>();
        collectFocusable(result);
        
        result.sort((a, b) -> {
            int ai = a.getFocusIndex();
            int bi = b.getFocusIndex();
            if (ai >= 0 && bi >= 0) return Integer.compare(ai, bi);
            if (ai >= 0) return -1;
            if (bi >= 0) return 1;
            return 0;
        });
        
        return result;
    }
    
    protected void collectFocusable(List<R> result) {
        if (isFocusable()) {
            result.add(self());
        }
        for (R child : children) {
            child.collectFocusable(result);
        }
    }
    
    // ===== VISIBILITY & STATE =====
    

    public boolean isVisible() {
        StateSnapshot snap = stateMachine.getSnapshot();
        return !snap.hasState(RenderableStates.STATE_HIDDEN) 
            && !snap.hasState(RenderableStates.STATE_INVISIBLE)
            && snap.hasState(RenderableStates.STATE_EFFECTIVELY_VISIBLE);
    }
        

    public boolean isRenderable() {
        return stateMachine.hasState(RenderableStates.STATE_RENDERABLE);
    }
    boolean isShowing() {
        return stateMachine.hasState(RenderableStates.STATE_RENDERABLE);
    }

    public boolean isActive() {
        return stateMachine.hasState(RenderableStates.STATE_RENDERABLE);
    }


    public boolean isAttached() {
        return stateMachine.hasState(RenderableStates.STATE_ATTACHED);
    }

    public void attachAsRoot() {
        stateMachine.addState(RenderableStates.STATE_ATTACHED);
        requestLayoutUpdate();
    }
    
    public void detachFromRoot() {
        stateMachine.removeState(RenderableStates.STATE_ATTACHED);
        requestLayoutUpdate();
    }

  
    public void setVisible(boolean visible) {
        if (visible) {
            // Only act if we need to clear visibility blockers
            if (!stateMachine.hasAnyState(RenderableStates.STATE_HIDDEN_DESIRED, RenderableStates.STATE_INVISIBLE_DESIRED)) {
                return; // Already visible, nothing to change
            }
            if (visibilityPolicy != null && !visibilityPolicy.allowVisibilityChange(self(), visible)) {
                return;
            }
            stateMachine.removeStates(RenderableStates.STATE_HIDDEN_DESIRED, RenderableStates.STATE_INVISIBLE_DESIRED);
            requestLayoutUpdate();
        } else if (!stateMachine.hasState(RenderableStates.STATE_HIDDEN_DESIRED)) {
            if (visibilityPolicy != null && !visibilityPolicy.allowVisibilityChange(self(), visible)) {
                return;
            }
            stateMachine.addState(RenderableStates.STATE_HIDDEN_DESIRED);
            requestLayoutUpdate();
        }
    }

    public boolean isEffectivelyVisible() {
        return stateMachine.hasState(RenderableStates.STATE_EFFECTIVELY_VISIBLE);
    }

    public void collapse() {
        setHidden(true);
    }
    
    public void expand() {
        setHidden(false);
    }
    
    public void setHidden(boolean hidden) {
        StateSnapshot state = stateMachine.getSnapshot();
        if (hidden
            && (!state.hasState(RenderableStates.STATE_HIDDEN_DESIRED)
              ||    state.hasState(RenderableStates.STATE_INVISIBLE_DESIRED)
            )
        ) {
            stateMachine.addState(RenderableStates.STATE_HIDDEN_DESIRED);
            stateMachine.removeState(RenderableStates.STATE_INVISIBLE_DESIRED); // Mutually exclusive
            requestLayoutUpdate();
        } else if(!hidden && state.hasState(RenderableStates.STATE_HIDDEN_DESIRED)){
            stateMachine.removeState(RenderableStates.STATE_HIDDEN_DESIRED);
            requestLayoutUpdate();
        }
    }
    
    public boolean isHidden() {
        return stateMachine.hasAnyState(RenderableStates.STATE_HIDDEN_DESIRED, RenderableStates.STATE_HIDDEN);
    }

   public  void setInvisible(boolean invisible) {
        StateSnapshot state = stateMachine.getSnapshot();
        if (invisible && (
            !state.hasState(RenderableStates.STATE_INVISIBLE_DESIRED)
            || state.hasState(RenderableStates.STATE_HIDDEN_DESIRED)
        )) {
            stateMachine.setState(RenderableStates.STATE_INVISIBLE_DESIRED);
            stateMachine.removeState(RenderableStates.STATE_HIDDEN_DESIRED);
            requestLayoutUpdate();
        } else if(!invisible && state.hasState(RenderableStates.STATE_INVISIBLE_DESIRED)){
            stateMachine.removeState(RenderableStates.STATE_INVISIBLE_DESIRED);
            requestLayoutUpdate();
        }
    }
            
    public boolean isInvisible() {
        return stateMachine.hasState(RenderableStates.STATE_INVISIBLE);
    }
    
    // ===== ENABLED/DISABLED API =====
    
    public void enable() {
        setEnabled(true);
    }
    
    public void disable() {
        setEnabled(false);
    }
    

    

    // ===== FOCUS API =====
    
    public void requestFocus() {
        if (!focusable) {
            return;
        }
        focusRequestToken = FOCUS_REQUEST_SEQ++;
        stateMachine.addState(RenderableStates.STATE_FOCUS_DESIRED);
        if (layoutManager != null) {
            layoutManager.requestFocus(self());
        }
    }
    
    public void clearFocus() {
        if (hasFocus()) {
            stateMachine.removeState(RenderableStates.STATE_FOCUSED);
        }
        stateMachine.removeState(RenderableStates.STATE_FOCUS_DESIRED);
    }

    public void focus() {
        if (isFocusable()) {
            stateMachine.addState(RenderableStates.STATE_FOCUSED);
            stateMachine.removeState(RenderableStates.STATE_FOCUS_DESIRED);
        }
    }

    long getFocusRequestToken() {
        return focusRequestToken;
    }
        

    // ===== STATE QUERIES =====
    
    /**
     * Can this renderable receive events?
     */
    public boolean canReceiveEvents() {
        return isEffectivelyVisible();
    }
    
    /**
     * Should this renderable participate in layout?
     */
    public boolean participatesInLayout() {
        return isAttached() && !isHidden();
    }
    
    /**
     * Should this renderable be rendered?
     */
    public boolean shouldRender() {
        
        boolean shouldRender =  stateMachine.hasState(RenderableStates.STATE_RENDERABLE) && needsRender();
        Log.logMsg("[ContainerHandle] + shouldRender()? " + shouldRender, LOG_LEVEL);
        return shouldRender;
    }
  
    public void hide() {
        setVisible(false);
    }

    public void show() {
        setVisible(true);
    }
    protected void onRemovedFromParent() {}
    protected void onAttachedToParent() {}
    protected void onRenderable() { }
    protected void onStarted() {}
    protected void onHide() {}
    protected void onHidden() {}
    protected void onUnhide() {}
    protected void onEnabled() {}
    protected void onDisabled() {}

    /*Clean up handlers if not needed*/
    protected void onRemovedFromLayout() { }

    /* Called by layoutmanager when renderable is removed 
     * to be destroyed
     */
    void removedFromLayout() {
        if(notifyRemovedFromLayout != null){ notifyRemovedFromLayout.accept(self()); }
        clearLayoutManager();
    
        // Recycle damage regions
        if (localDamage != null) {
            regionPool.recycle(localDamage);
            localDamage = null;
        }
        if (absoluteDamage != null) {
            regionPool.recycle(absoluteDamage);
            absoluteDamage = null;
        }
        // Child callbacks no longer needed
 
        onRemovedFromLayout();

    }

    public void destroy(){
        onDestroying();
        stateMachine.addState(RenderableStates.DESTROYED);
        clearOwnDamage();
        childGroups.clear();
        childLayoutCallbacks.clear();
        stateMachine.clearAllStates();
        cleanupEventHandlers();
        if(region != null){
            S oldRegion = region;
            region = null;
            regionPool.recycle(oldRegion);
        }
        if(requestedRegion != null){
            S oldRegion = requestedRegion;
            requestedRegion = null;
            regionPool.recycle(oldRegion);
        }
    }

    protected void onDestroying(){ }

    public void setEnabled(boolean enabled) {
        updateRequestState(RenderableStates.STATE_ENABLED_DESIRED, enabled);
    }

    private void updateRequestState(int state, boolean value) {
        if (value) {
            stateMachine.addState(state);
        } else {
            stateMachine.removeState(state);
        }
        // Trigger layout to recompute RESOLVED states
        if (layoutManager != null) {
            layoutManager.markLayoutDirty(self());
        }
    }

    



    // ===== UTILITIES =====
    
    @SuppressWarnings("unchecked")
    protected R self() { return (R) this; }
    
    public String getName() { return name; }
    public BitFlagStateMachine getStateMachine() { return stateMachine; }
    
    public R getRoot() {
        R node = self();
        while (node.parent != null) {
            node = node.parent;
        }
        return node;
    }
    
    public int getDepth() {
        int depth = 0;
        R node = parent;
        while (node != null) {
            depth++;
            node = node.parent;
        }
        return depth;
    }

    public void transitionTo(int from, int to) {
        if (stateMachine.hasState(from)) {
            stateMachine.removeState(from);
        }
        stateMachine.addState(to);
    }

    public void setRenderRequest(Consumer<R> onRequest) {
        onRenderRequest = onRequest;
        if (onRequest != null) {
            attachAsRoot();
        } else {
            detachFromRoot();
        }
    }

    public void setDamageAccumulator(Consumer<S> accumulator) {
        this.damageAccumulator = accumulator;
        // Propagate to children — they share the same frame accumulator
        for (R child : getChildren()) {
            child.setDamageAccumulator(accumulator);
        }
    }

    private void reportDamage(S absoluteRegion) {
        if (damageAccumulator != null) {
            damageAccumulator.accept(absoluteRegion.copy());
        }
    }

    public SerializedVirtualExecutor getUIExecutor() {
        return uiExecutor;
    }
    
    // ===== KEY EVENT HELPERS =====
    
    public NoteBytesReadOnly addKeyCharHandler(Consumer<RoutedEvent> handler) {
        return eventRegistry.register(EventBytes.EVENT_KEY_CHAR, handler);
    }
    
    public NoteBytesReadOnly addKeyCharHandler(
            Consumer<RoutedEvent> handler, 
            EventFilter filter) {
        return eventRegistry.register(EventBytes.EVENT_KEY_CHAR, handler, filter);
    }
    
    public NoteBytesReadOnly addKeyDownHandler(Consumer<RoutedEvent> handler) {
        return eventRegistry.register(EventBytes.EVENT_KEY_DOWN, handler);
    }
    
    public NoteBytesReadOnly addKeyDownHandler(
            Consumer<RoutedEvent> handler,
            EventFilter filter){
        return eventRegistry.register(EventBytes.EVENT_KEY_DOWN, handler, filter);
    }

    public List<RoutedEventHandler> removeKeyDownHandler(Consumer<RoutedEvent> consumer) {
        return eventRegistry.unregister(EventBytes.EVENT_KEY_DOWN, consumer);
    }
    
    public List<RoutedEventHandler> removeKeyDownHandler(NoteBytesReadOnly id) {
        return eventRegistry.unregister(EventBytes.EVENT_KEY_DOWN, id);
    }

      public List<RoutedEventHandler> removeKeyCharHandler(NoteBytesReadOnly id) {
        return eventRegistry.unregister(EventBytes.EVENT_KEY_CHAR, id);
    }
    
    public NoteBytesReadOnly addKeyUpHandler(Consumer<RoutedEvent> handler) {
        return eventRegistry.register(EventBytes.EVENT_KEY_UP, handler);
    }
    
    public NoteBytesReadOnly addKeyUpHandler(
            Consumer<RoutedEvent> handler,
            EventFilter filter) {
        return eventRegistry.register(EventBytes.EVENT_KEY_UP, handler, filter);
    }
    
    public List<RoutedEventHandler> removeKeyUpHandler(Consumer<RoutedEvent> handler) {
        return eventRegistry.unregister(EventBytes.EVENT_KEY_UP, handler);
    }
    
    public List<RoutedEventHandler> removeKeyUpHandler(NoteBytesReadOnly handlerId) {
        return eventRegistry.unregister(EventBytes.EVENT_KEY_UP, handlerId);
    }

    protected NoteBytesReadOnly addEventHandler(
            NoteBytesReadOnly eventType, 
            Consumer<RoutedEvent> handler) {
        return eventRegistry.register(eventType, handler);
    }
    
    protected NoteBytesReadOnly addEventHandler(
            NoteBytesReadOnly eventType,
            Consumer<RoutedEvent> handler,
            EventFilter filter) {
        return eventRegistry.register(eventType, handler, filter);
    }

    protected void removeEventHandler(NoteBytesReadOnly handlerId) {
        try {
            eventRegistry.unregister(handlerId);
        } catch (Exception e) {
            Log.logError("[" + name + "] Error removing handler: " + e.getMessage());
        }
    }
    
    protected void cleanupEventHandlers() {
        cancelKeyWait();
        eventRegistry.clear();
    }

    // ===== RENDER FLAG MANAGEMENT =====

    /**
     * Check if this renderable needs rendering
     * A renderable needs rendering if it or any child has damage
     * 
     * @return true if there's any damage to render
     */
    public boolean needsRender() {
        boolean needsRender =  localDamage != null || absoluteDamage != null || childrenDirty;
        return needsRender;
    }

    /**
     * Clear the render flag after successful render
     * Called by ContainerHandle after toBatch() completes
     * 
     * Note: toBatch() already clears damage regions during rendering,
     * but this provides explicit confirmation that render completed
     */
    public void clearRenderFlag() {
        // Damage should already be cleared by toBatch()
        // This is a safety net in case toBatch() wasn't called
        if (localDamage != null) {
            regionPool.recycle(localDamage);
            localDamage = null;
        }
        if (absoluteDamage != null) {
            regionPool.recycle(absoluteDamage);
            absoluteDamage = null;
        }
        childrenDirty = false;
    }

    public S copyAbsoluteDamage(){
        return absoluteDamage == null ? null : absoluteDamage.copy();
    }


    // ===== KEY WAITING =====

    public CompletableFuture<Void> waitForKeyPress() {
        if (anyKeyFuture != null) {
            return anyKeyFuture;
        }
        
        anyKeyFuture = new CompletableFuture<>();
        
        Consumer<RoutedEvent> consumer = event -> {
            if (event instanceof KeyDownEvent || event instanceof EphemeralKeyDownEvent) {
                handleKeyWaitComplete();
            }
        };
        
        keyWaitHandlerId = addKeyDownHandler(consumer);
        return anyKeyFuture;
    }
    
    public CompletableFuture<Void> waitForKeyPress(Runnable action) {
        return waitForKeyPress().thenRun(action);
    }
    
    public CompletableFuture<RoutedEvent> waitForKey(NoteBytes keyCodeBytes) {
        if (keyWaitFuture != null) {
            return keyWaitFuture;
        }
        
        keyWaitFuture = new CompletableFuture<>();
        
        Consumer<RoutedEvent> consumer = event -> {
            if (event instanceof KeyDownEvent keyDown) {
                if (keyDown.getKeyCodeBytes().equals(keyCodeBytes)) {
                    handleKeyWaitComplete(event);
                }
            } else if (event instanceof EphemeralKeyDownEvent keyDown) {
                if (keyDown.getKeyCodeBytes().equals(keyCodeBytes)) {
                    handleKeyWaitComplete(event);
                    keyDown.close();
                }
            }
        };
        
        keyWaitHandlerId = addKeyDownHandler(consumer);
        return keyWaitFuture;
    }
    
    public CompletableFuture<Void> waitForEnter() {
        return waitForKey(KeyCodeBytes.ENTER)
            .thenAccept(k -> {
                if (k instanceof EphemeralRoutedEvent ephemeral) {
                    ephemeral.close();
                }
            });
    }
    
    public CompletableFuture<Void> waitForEscape() {
        return waitForKey(KeyCodeBytes.ESCAPE)
            .thenAccept(k -> {
                if (k instanceof EphemeralRoutedEvent ephemeral) {
                    ephemeral.close();
                }
            });
    }
    
    private void handleKeyWaitComplete(RoutedEvent event) {
        if (keyWaitFuture == null) return;
        
        if (keyWaitHandlerId != null) {
            removeKeyDownHandler(keyWaitHandlerId);
            keyWaitHandlerId = null;
        }
        
        if (keyWaitFuture != null && !keyWaitFuture.isDone()) {
            keyWaitFuture.complete(event);
        }
        
        keyWaitFuture = null;
    }
    
    private void handleKeyWaitComplete() {
        if (anyKeyFuture == null) return;
        
        if (keyWaitHandlerId != null) {
            removeKeyDownHandler(keyWaitHandlerId);
            keyWaitHandlerId = null;
        }
        
        if (anyKeyFuture != null && !anyKeyFuture.isDone()) {
            anyKeyFuture.complete(null);
        }
        
        anyKeyFuture = null;
    }
    
    public void cancelKeyWait() {
        if (keyWaitHandlerId != null) {
            removeKeyDownHandler(keyWaitHandlerId);
            keyWaitHandlerId = null;
        }
        
        if (keyWaitFuture != null && !keyWaitFuture.isDone()) {
            keyWaitFuture.cancel(false);
        }
        
        keyWaitFuture = null;
        anyKeyFuture = null;
    }
    
    public boolean isWaitingForKeyPress() {
        return keyWaitFuture != null || anyKeyFuture != null;
    }

    // ===== SETTERS FOR CALLBACKS =====



  
    public void unregisterRenderable(){
        if (layoutManager != null) layoutManager.unregister(self());
    }


    public void setLayoutManager(RenderableLayoutManagerHandle<R, LCB,G,GCB> manager) {
        this.layoutManager = manager;
    }

    public void clearLayoutManager() {

        this.layoutManager = null;
    }


    public void beginLayoutBatch() { 
        RenderableLayoutManagerHandle<R, LCB,G, GCB> lm = this.layoutManager;
        if(lm != null){
            lm.beginBatch();
        }
    }
    
    /**
     * End batching and trigger single layout pass
     */
    public void endLayoutBatch(){
        RenderableLayoutManagerHandle<R, LCB,G, GCB> lm = this.layoutManager;
        if(lm != null){
            lm.endBatch();
        }
    }
    
    /**
     * Execute operations within a batch transaction
     * Ensures single layout pass regardless of how many dirty marks
     */
    public void batch(Runnable operations) {
        RenderableLayoutManagerHandle<R, LCB,G, GCB> lm = this.layoutManager;
        if(lm != null){
            lm.beginBatch();
            try {
                operations.run();
            } finally {
                lm.endBatch();
            }
        }
    }

    /**
     * Apply layout data - IMMEDIATE application for incremental layout
     * 
     * Deferral happens naturally:
     * - State machine transitions execute callbacks on uiExecutor
     * 
     * @param layoutData Data carrier (spatialRegion != null means damaged)
     */
    void applyLayoutData(LD layoutData) {
        if (layoutData.hasRegion() && layoutData.hasAnyAxisChange()) {
            applySpatialChange(layoutData);
        }
        
        // Apply state changes - IMMEDIATE state machine update
        // Callbacks from state transitions will be deferred by state machine
        if (layoutData.hasStateChanges()) {
            applyStateChanges(layoutData);
        }
    }

    /**
     * Axis-selective spatial update.
     *
     * Instead of replacing the entire region from the layout data, this method
     * calls {@code layoutData.mergeIntoRegion(current, target)} which copies the
     * current region into a scratch region and then overwrites only the axes that
     * were explicitly set (and not masked by the current LayoutPhase).  This
     * ensures a top-down pass never clobbers a FIT_CONTENT height committed by
     * the bottom-up pass, and vice versa.
     */
    private void applySpatialChange(LD layoutData) {
        try (PooledRegion<S> pooledMerged = regionPool.obtainPooled()) {
            S merged = pooledMerged.get();
            // Merge: unchanged axes come from current region; changed axes
            // come from layoutData (only the flagged ones).
            layoutData.mergeIntoRegion(region, merged);

            boolean changed = !region.equals(merged);
            if (!changed) {
                clearRequestedRegion();
                return;
            }

            S oldRegion = regionPool.obtain();
            oldRegion.copyFrom(region);

            region.copyFrom(merged);
            clearRequestedRegion();

            // Damage must include both old and new occupied areas.
            try (PooledRegion<S> pooledDamage = regionPool.obtainPooled()) {
                S damage = pooledDamage.get();
                damage.copyFrom(oldRegion);
                damage.unionInPlace(region);
                invalidate(damage);
            }

            onRegionChanged(oldRegion, region);
            if (notifyOnRegionChanged != null) {
                notifyOnRegionChanged.accept(oldRegion, region);
            } else {
                regionPool.recycle(oldRegion);
            }
        }
    }

    /**
     * Called after this renderable's region has been committed to a new value.
     * Override in subclasses to push derived bounds to children.
     *
     * The default implementation does nothing.
     * Subclasses that override this method are responsible for NOT recycling
     * oldRegion (the layout manager will recycle it if notifyOnRegionChanged
     * is null, as before).
     *
     * @param oldRegion the region before this update (do not hold a reference)
     * @param newRegion the region after this update (this.region — do not modify)
     */
    protected void onRegionChanged(S oldRegion, S newRegion) { }

    /***
     * RECOMMENDED: recycle oldRegion if not in use
     * 
     * @param onRegionChanged BiConsumer (oldRegion, newRegion)
     */
    public void setOnRegionChanged(BiConsumer<S,S> onRegionChanged){
        notifyOnRegionChanged = onRegionChanged;
    }

    /**
     * Apply state changes - immediate state machine updates
     * State machine callbacks execute on uiExecutor (deferred)
     */
    private void applyStateChanges(LD layoutData) {
        StateSnapshot beforeState = stateMachine.getSnapshot();

        // Apply MODIFIER states (may have been set by callback)
        applyStateIfPresent(layoutData.getHidden(), RenderableStates.STATE_HIDDEN);
        applyStateIfPresent(layoutData.getInvisible(), RenderableStates.STATE_INVISIBLE);
        applyStateIfPresent(layoutData.getEnabled(), RenderableStates.STATE_ENABLED_DESIRED);
        // Apply RESOLVED states (computed by manager post-callback)
        applyStateIfPresent(layoutData.getEffectivelyVisible(), RenderableStates.STATE_EFFECTIVELY_VISIBLE);

        StateSnapshot snapShot = stateMachine.getSnapshot();

        boolean isRenderable = snapShot.hasState(RenderableStates.STATE_EFFECTIVELY_VISIBLE);

        if(isRenderable != stateMachine.hasState(RenderableStates.STATE_RENDERABLE)){
            if(isRenderable){
                stateMachine.addState(RenderableStates.STATE_RENDERABLE);
            }else{
                stateMachine.removeState(RenderableStates.STATE_RENDERABLE);
            }
        }

        handleStateTransitionDamage(beforeState, stateMachine.getSnapshot());
    }

    private void handleStateTransitionDamage(StateSnapshot beforeState, StateSnapshot afterState) {
        boolean wasVisible = RenderableStates.isVisible(beforeState);
        boolean isVisibleNow = RenderableStates.isVisible(afterState);

        if (wasVisible && !isVisibleNow) {
            childrenDirty = false;
            invalidate();
            clearOwnDamage();
            return;
        }

        if (!wasVisible && isVisibleNow) {
            childrenDirty = true;
            invalidate();
            return;
        }
}

    private void clearOwnDamage() {
        if (localDamage != null) {
            regionPool.recycle(localDamage);
            localDamage = null;
        }
        if (absoluteDamage != null) {
            regionPool.recycle(absoluteDamage);
            absoluteDamage = null;
        }
    }

    private void applyStateIfPresent(Boolean value, int state) {
        if (value == null) return;
        
        boolean current = stateMachine.hasState(state);
        if (value && !current) {
            stateMachine.addState(state);
        } else if (!value && current) {
            stateMachine.removeState(state);
        }
    }

    // ===== FLOATING API =====

    /**
     * Make this renderable floating (escape parent bounds)
     * 
     * @param anchor Element to anchor position to (can be null for viewport-relative)
     */
    public void makeFloating(R anchor) {
        if(uiExecutor.isCurrentThread()){
            makeFloatingInternal(anchor);
        }else{
            uiExecutor.executeFireAndForget(()->makeFloatingInternal(anchor));
        }
    }

    private void makeFloatingInternal(R anchor){
        if (isFloating && positionAnchor == anchor) {
            return;  // Already floating with same anchor
        }
   
        this.isFloating = true;
        this.positionAnchor = anchor;
        this.layerIndex = LAYER_FLOATING;
        this.logicalParent = parent;  // Preserve for events
        invalidateRenderChildrenCache();
        if (parent != null) {
            invalidateRenderChildrenCache(parent);
        }
        // Migrate to floating layer in layout manager
        if (layoutManager != null) {
            layoutManager.migrateToFloating(self(), anchor);
        }
        
        // Force a frame so the inline composition is cleared and this node can
        // repaint from the floating layer even before the next layout update.
        invalidate();

        // Mark for re-layout with floating constraints
        requestLayoutUpdate();
  
    }
    
    /**
     * Make this renderable static (normal parent-child rendering)
     */
    public void makeStatic() {
        if (!isFloating) {
            return;
        }
        
        uiExecutor.executeFireAndForget(() -> {
            this.isFloating = false;
            this.positionAnchor = null;
            this.layerIndex = LAYER_NORMAL;
            this.logicalParent = null;
            this.renderingParent = null;
            if (parent != null) {
                invalidateRenderChildrenCache(parent);
            }
            
            // Migrate back to regular layout
            if (layoutManager != null) {
                layoutManager.migrateToRegular(self());
            }
            
            invalidate();
            requestLayoutUpdate();
        });
    }
    
    /**
     * Set layer index for rendering order
     */
    public void setLayerIndex(int layer) {
        if (this.layerIndex != layer) {
            this.layerIndex = layer;
            if (!isFloating && parent != null) {
                invalidateRenderChildrenCache(parent);
            }
            invalidate();  // Re-render in new layer
        }
    }
    
    /**
     * Set position anchor for floating elements
     */
    public void setPositionAnchor(R anchor) {
        if (this.positionAnchor != anchor) {
            this.positionAnchor = anchor;
            if (isFloating) {
                requestLayoutUpdate();  // Recalculate position
            }
        }
    }

    // ==== Grouping ====

    /**
     * Create a group
     */
    protected void createChildGroup(String groupId) {
        childGroups.putIfAbsent(groupId, createGroupStateEntry());
    }
    
    protected abstract GSE createGroupStateEntry();

    /**
     * Add renderable to group
     */
    protected void addToChildGroup(R renderable, String groupId) {
        createChildGroup(groupId);
        GSE state = childGroups.get(groupId);
        
        // Remove from any existing group
        for (GSE otherState : childGroups.values()) {
            otherState.getMembers().remove(renderable);
        }
        state.getMembers().add(renderable);
    }
    
    /**
     * Get group ID for renderable
     */
    protected String getChildLayoutGroupIdByRenderable(R renderable) {
        for (Map.Entry<String, GSE> entry : childGroups.entrySet()) {
            if (entry.getValue().getMembers().contains(renderable)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Remove renderable from its group
     */
    protected void removeChildGroupMember(R renderable) {
        Iterator<Entry<String, GSE>> it = childGroups.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, GSE> stateEntry = it.next();
            GSE state = stateEntry.getValue();
            List<R> members = state.getMembers();
            members.remove(renderable);
        }
    }
    
    /**
     * Destroy a group
     */
    protected void destroyChildGroup(String groupId) {
        childGroups.remove(groupId);
    }
    
    /**
     * Register callback for a group
     */
    protected void registerChildGroupCallback(String groupId, GCB callbackEntry) {
        createChildGroup(groupId);
        GSE state = childGroups.get(groupId);
        state.setLayoutCallback(callbackEntry);
    }


    /**
     * Get all group IDs
     */
    Set<String> getChildGroupIds() {
        return childGroups.keySet();
    }
    
    /**
     * Get group state
     */
    GSE getChildGroupState(String groupId) {
        return childGroups.get(groupId);
    }

    private GCB getChildLayoutGroupCallback(String groupId){
        GSE groupState = childGroups.get(groupId);
        if(groupState == null){ return null; }
        return groupState.getLayoutCallback();
    }

    public Map<String,GSE> getChildGroups(){
        return childGroups;
    }    
    
    Map<String,GSE> collectChildGroups(){
        Map<String,GSE> copy = new HashMap<>();
        for (Map.Entry<String,GSE> entry : childGroups.entrySet()) {
            GSE snapshot = createGroupStateEntry();
            snapshot.getMembers().addAll(entry.getValue().getMembers());
            snapshot.setLayoutCallback(entry.getValue().getLayoutCallback());
            copy.put(entry.getKey(), snapshot);
        }
        return copy;
    }

    // ====== Size Preference ======

    public abstract SizePreference getSizePreference(int axis);


    public abstract int getPreferredSize(int axis); 
    public abstract int getMinSize(int axis);


    protected void destroyChildGroups(){
        childGroups.clear();
    }

    /**
     * Check if there are any pending groups
     */
    public boolean hasChildGroups() {
        return !childGroups.isEmpty();
    }
    


    public void clearChildGroups() {
        childGroups.clear();
    }
    

    // ===== GROUP MANAGEMENT API =====
    // These methods automatically handle attached vs pending state
    
    /**
     * Create a group
     * 
     * @param groupId group identifier
     */
    public void createLayoutGroup(String groupId) {
        if (isAttachedToLayoutManager()) {
            layoutManager.createLayoutGroup(groupId);
        } else {
            createChildGroup(groupId);
        }
    }

    public boolean isAttachedToLayoutManager(){
        if(isDestroyed()){
            throw new IllegalStateException("Cannot modify renderable has been destroyed:" + getName());
        }
        return layoutManager != null;
    }
    
    /**
     * Add renderable to group
     * Renderables can only be part of one group at a time
     * Group is created if it does not exist
     * 
     * @param renderable renderable to add to the group
     * @param groupId group identifier to add the renderable to
     */
    public void addToLayoutGroup(R renderable, String groupId) {
        addToChildGroup(renderable, groupId);          // always
        if (isAttachedToLayoutManager()) {
            layoutManager.addToLayoutGroup(renderable, groupId);
        }
    }

    public GCB getLayoutGroupCallback(String groupId){
    
        return getChildLayoutGroupCallback(groupId);
        
    }
    
    /**
     * Get the groupId of a renderable or null if not exists
     * 
     * @param renderable renderable to query
     * @return group ID or null
     */
    public String getLayoutGroupIdByRenderable(R renderable) {
        if (isAttachedToLayoutManager() ) {
            return layoutManager.getLayoutGroupIdByRenderable(renderable);
        } else {
            return getChildLayoutGroupIdByRenderable(renderable);
        }
    }
    
    /**
     * Remove a renderable from its group
     * 
     * @param renderable renderable to remove
     */
    public void removeLayoutGroupMember(R renderable) {
        removeChildGroupMember(renderable);             // always
        if (isAttachedToLayoutManager()) {
            layoutManager.removeLayoutGroupMember(renderable);
        }
    }
    
    /**
     * Destroy a group and remove all members
     * 
     * @param groupId group to destroy
     */
    public void destroyLayoutGroup(String groupId) {
        destroyChildGroup(groupId);                    // always
        if (isAttachedToLayoutManager()) {
            layoutManager.destroyLayoutGroup(groupId);
        }
    }
        
    /**
     * Register callback for a group
     * 
     * Callback signature:
     *   (LC[] contexts, HashMap<String, LayoutDataInterface<LD>> layoutDataInterface)
     * 
     * Callback parameters:
     * - LC[] contexts: Array of contexts per group member
     * - HashMap<String, LayoutDataInterface<LD>>: map to apply LayoutData for group members
     *   - Key: String - name of Renderable
     *   - Value: LayoutDataInterface with:
     *     - String getLayoutDataName()
     *     - LD getLayoutData()
     *     - void setLayoutData(LD layoutData)
     * 
     * Example usage:
     * <pre>
     * registerGroupCallback("stack_items", "layout", null, (ctxs, layoutDataInterface) -> {
     *     int y = 0;
     *     for (LC ctx : ctxs) {
     *         String renderableName = ctx.getName();
     *         LD data = LayoutData.builder()
     *             .setY(y)
     *             .setHeight(itemHeight)
     *             .build();
     *         layoutDataInterface.get(renderableName).setLayoutData(data);
     *         y += itemHeight + spacing;
     *     }
     * });
     * </pre>
     * 
     * @param groupId group to add the callback to
     * @param callbackId unique callback identifier
     * @param predicate predicate to determine if the callback runs or null
     * @param callback callback to manage group members
     */
    public void setGroupLayoutCallback(String groupId, GCB callback){
        registerChildGroupCallback(groupId, callback);
        if (isAttachedToLayoutManager()) {
            layoutManager.setGroupLayoutCallback(groupId, callback);
        }
    }
    
    
    

    public static class GroupStateEntry<
        R extends Renderable<?,?,?,?,?,?,?,GCB,GSE,?,R>,
        GCB extends GroupLayoutCallback<?,R,?,?,?,?,?,GCB>,
        GSE extends GroupStateEntry<R,GCB,GSE>
    >{
        private List<R> members = new ArrayList<>();
        private GCB layoutCallback = null;

        public List<R> getMembers(){
            return members;
        }
        public GCB getLayoutCallback(){ return layoutCallback;}
        public void setLayoutCallback(GCB layoutCallback){ this.layoutCallback = layoutCallback;}
    }

    
    // ===== GETTERS =====
    
    public boolean isFloating() {
        return isFloating;
    }
    
    public int getLayerIndex() {
        return layerIndex;
    }
    
    public R getPositionAnchor() {
        return positionAnchor;
    }
    
    public R getLogicalParent() {
        return logicalParent != null ? logicalParent : parent;
    }
    
    public R getRenderingParent() {
        // A floating node is rendered exclusively by FloatingLayerManager.
        // Returning parent here would cause it to participate in the normal
        // child render loop, producing a double-render.
        if (isFloating()) {
            return null;
        }
        if (renderingParent != null) {
            return renderingParent;
        }
        return parent;
    }

}
