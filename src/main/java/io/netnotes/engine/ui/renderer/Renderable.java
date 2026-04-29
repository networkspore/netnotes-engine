package io.netnotes.engine.ui.renderer;

import io.netnotes.engine.ui.PooledRegion;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.SpatialRegionPool;
import io.netnotes.engine.ui.VisibilityPredicate;
import io.netnotes.engine.ui.renderer.layout.GroupLayoutCallback;
import io.netnotes.engine.ui.renderer.layout.LayoutCallback;
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
import io.netnotes.engine.state.ConcurrentBitFlagStateMachine;
import io.netnotes.engine.state.BitFlagStateMachine.StateSnapshot;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.LoggingHelpers.LogLevel;

import io.netnotes.engine.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.engine.virtualExecutors.VirtualExecutors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
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

    protected S damage = null;
    protected boolean childrenDirty = false;
    protected boolean pendingInvalidate = false;

    // ===== SPATIAL REGION POOL =====
    // Strategy: Thread-local pools eliminate allocation overhead
    // Regions are recycled after use, reused in subsequent invalidations
    
    protected final SpatialRegionPool<S> regionPool;
    
    // ===== IDENTITY =====
    protected final String name;
    protected final ConcurrentBitFlagStateMachine stateMachine;
    
    // ===== HIERARCHY =====
    protected R parent = null;
    protected final List<R> children = new ArrayList<>();
    protected int zOrder = 0;

    
    // ===== LAYOUT - DIMENSION AGNOSTIC =====
    protected S region = null;
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
    private static AtomicLong FOCUS_REQUEST_SEQ = new AtomicLong(0);
    protected int focusIndex = -1;
    private long focusRequestToken = 0;
    
    protected volatile RenderableLayoutManagerHandle<R,LCB,G,GCB> layoutManager = null;
    protected Consumer<R> onRenderRequest = null;
    protected BiConsumer<S,S> notifyOnRegionChanged = null;
    protected Consumer<R> notifyRemovedFromLayout = null;
    protected VisibilityPredicate<R> visibilityPolicy = null;
    protected BiConsumer<R, StateSnapshot> onVisibilityChanged = null;
    protected BiConsumer<R, Boolean> onFocusChanged = null;
    protected Consumer<S> damageAccumulator = null;
    protected volatile RenderPhase renderPhase = RenderPhase.DETACHED;
    protected boolean batching = false;

    private R[] renderBuffer;
    private R[] sortScratch;
    private long[] sortKeys;
    private int renderCount;
    /**
     * Constructor
     * 
     * @param name Component name for debugging
     * @param regionPool Pool for spatial region allocation 
     */
    protected Renderable(String name, SpatialRegionPool<S> regionPool) {
        this.name = name;
        this.regionPool = regionPool;
        this.stateMachine = new ConcurrentBitFlagStateMachine(name);
        this.stateMachine.setSerialExecutor(uiExecutor);
        this.region = regionPool.obtain();
        this.region.setToIdentity();
        
        setupBaseTransitions();
        setupStateTransitions();
        setupEventHandlers();
    }

    public void setOnVisibilityChanged(BiConsumer<R, StateSnapshot> onVisibilityChanged){
        assertUiThread();
        this.onVisibilityChanged = onVisibilityChanged;
    }

    public void setVisibilityPolicy(VisibilityPredicate<R> visibilityPolicy){
        assertUiThread();
        this.visibilityPolicy = visibilityPolicy;
    }
    public void setNotifyRemovedFromLayout(Consumer<R> notifyRemovedFromLayout){
        assertUiThread();
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
        stateMachine.onStateAdded(RenderableStates.STATE_EFFECTIVELY_HIDDEN, (old, now, bit) -> {
            if(onVisibilityChanged != null){
                onVisibilityChanged.accept(self(), stateMachine.getSnapshot());
            }

            onHidden();
        });
        
        stateMachine.onStateRemoved(RenderableStates.STATE_EFFECTIVELY_HIDDEN, (old, now, bit) -> {
            if(onVisibilityChanged != null){
                onVisibilityChanged.accept(self(), stateMachine.getSnapshot());
            }
            // Request layout to restore size
            onUnhide();
        });

        stateMachine.onStateAdded(RenderableStates.STATE_EFFECTIVELY_INVISIBLE, (old, now, bit) -> {
            if(onVisibilityChanged != null){
                onVisibilityChanged.accept(self(), stateMachine.getSnapshot());
            }

            onInvisible();
        });
        
        stateMachine.onStateRemoved(RenderableStates.STATE_EFFECTIVELY_INVISIBLE, (old, now, bit) -> {
            if(onVisibilityChanged != null){
                onVisibilityChanged.accept(self(), stateMachine.getSnapshot());
            }
            // Request layout to restore size
            onInvisibleRemoved();
        });
        

        // Focus state changes
        stateMachine.onStateAdded(RenderableStates.STATE_FOCUSED, (old, now, bit) -> {
            onFocusGained();
        });
        
        stateMachine.onStateRemoved(RenderableStates.STATE_FOCUSED, (old, now, bit) -> {
            onFocusLost();
        });

        stateMachine.onStateAdded(RenderableStates.DESTROYED, (old, now, bit) ->{
            destroyInternal();
        });
    }


    
    protected abstract void setupStateTransitions(); 

    public boolean hasState(int state) {
        return stateMachine.hasState(state);
    }
    
    // ===== HIERARCHY =====
    
    public R getParent() { assertUiThread(); return parent; }
    public List<R> getChildren() { assertUiThread(); return new ArrayList<>(children); }
    
    public void addChild(R child) {
        addChild(child, null);
    }


    public void addChild(R child, LCB layoutCb){
        if(uiExecutor.isCurrentThread()){
            runTreeMutationWhenLayoutIdle(() -> addChildInternal(child, layoutCb));
        }else{
            uiExecutor.runLater(
                () -> runTreeMutationWhenLayoutIdle(() -> addChildInternal(child, layoutCb))
            );
        }
    }

    private void runTreeMutationWhenLayoutIdle(Runnable mutation) {
        RenderableLayoutManagerHandle<R, LCB,G, GCB> lm = layoutManager;
        if (lm != null && lm.isLayoutExecuting()) {
            // Structural tree mutations must run after the pass so damage
            // snapshots are computed from committed geometry.
            lm.runWhenLayoutIdle(mutation);
            return;
        }
        mutation.run();
    }

    private void addChildInternal(R child,LCB layoutCallback) {
        if (isDestroyed()) return;

        if (child.parent != null) {
            child.parent.removeChild(child);
        }

        children.add(child);
        child.parent = self();
        child.setDamageAccumulator(this.damageAccumulator);
        childrenDirty();

        if (layoutCallback  != null) childLayoutCallbacks.put(child, layoutCallback);

        child.stateMachine.addState(RenderableStates.STATE_ATTACHED);

        if (layoutManager != null) {
            layoutManager.registerChild(child, layoutCallback);
            requestLayoutUpdate();
        }
        child.requestLayoutUpdate();
    }

    void childrenDirty() {
        childrenDirty = true;
    }



    // ===== REQUESTED REGION SYSTEM =====

    public LCB getChildLayoutCallback(R child)  { return childLayoutCallbacks.get(child); }

    protected void ensureRequestedRegion() {
        assertUiThread();
        if (requestedRegion == null) {
            requestedRegion = regionPool.obtain();
            requestedRegion.copyFrom(region);
        }
    }

    public void clearRequestedRegion(){
        assertUiThread();
        S oldRequestedRegion = requestedRegion;
        requestedRegion = null;
        if(oldRequestedRegion != null){
            regionPool.recycle(oldRequestedRegion);
        }
    }
    
    public S getRequestedRegion() {
        assertUiThread();
        if (requestedRegion != null) {
            S copy = regionPool.obtain();
            copy.copyFrom(requestedRegion);
            return copy;
        }
        return getRegion();
    }
    
    public boolean hasRequestedRegion() {
        assertUiThread();
        return requestedRegion != null;
    }

    protected void notifyContentChanged(){
        if(isSizedByContent()){
            requestLayoutUpdate();
        }
        invalidate();
    }

    public void setPosition(P position) {
        assertUiThread();

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
        assertUiThread();
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
        assertUiThread();
        ensureRequestedRegion();
        requestedRegion.copyFrom(region);
        requestLayoutUpdate();
    }

    public void setBounds(S bounds){
        assertUiThread();
        setRegion(bounds);
    }
    
    public void removeChild(R child) {
        if(uiExecutor.isCurrentThread()){
            runTreeMutationWhenLayoutIdle(() -> removeChildInternal(child));
        }else{
            uiExecutor.runLater(
                () -> runTreeMutationWhenLayoutIdle(() -> removeChildInternal(child))
            );
        }
    }

    protected void removeChildInternal(R child) {
        boolean removed = children.remove(child);
        if (!removed) return;
        childrenDirty();
        
        // Damage the region where child was
        if (child.region != null && !child.region.isEmpty()) {
            try (PooledRegion<S> childLocal = regionPool.obtainPooled()) {
                S damage = childLocal.get();
                damage.copyFrom(child.region);
                damage.translate(child.region.getParentAbsolutePosition());
                propagateDamageUp(damage);
            }
        }
        child.setDamageAccumulator(null);
        child.pendingInvalidate = false;
        childLayoutCallbacks.remove(child);
        removeLayoutGroupMember(child);
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
            runTreeMutationWhenLayoutIdle(this::clearChildrenInternal);
        }else{
            uiExecutor.runLater(
                () -> runTreeMutationWhenLayoutIdle(this::clearChildrenInternal)
            );
        }
    }

    private void clearChildrenInternal() {
        if (children.isEmpty()) return;

        final List<R> removed = new ArrayList<>(children);

        children.clear();
        childrenDirty();

        // Build one union damage rect from all removed children.
        // child.region is already in parent-local coords — no transform needed.
        S unionDamage = null;
        for (R child : removed) {
            if (child.region != null && !child.region.isEmpty()) {
                if (unionDamage == null) {
                    unionDamage = regionPool.obtain();
                    unionDamage.copyFrom(child.region);
                    unionDamage.setPosition(child.region.getAbsolutePosition());
                } else {
                    try(PooledRegion<S> pooledRegion = regionPool.obtainPooled();){
                        S childRegion = pooledRegion.get();
                        childRegion.copyFrom(child.region);
                        childRegion.setPosition(child.region.getAbsolutePosition());
                        unionDamage.unionInPlace(childRegion);
                    }
                }
            }
        }

        if (unionDamage != null) {
            propagateDamageUp(unionDamage);
            regionPool.recycle(unionDamage);
        }

        for (R child : removed) {
            child.setDamageAccumulator(null);
            child.pendingInvalidate = false;   // drain deferred invalidate
            removeLayoutGroupMember(child);
            child.parent = null;
            child.stateMachine.removeState(RenderableStates.STATE_ATTACHED);
            if (layoutManager != null) layoutManager.unregister(child);
        }

        Log.logMsg("[Renderable:" + name + "] clearChildren() removed " + removed.size() + " children", LOG_LEVEL);
    }
        
    public int getZOrder() { assertUiThread(); return zOrder; }
    
    public void setZOrder(int z) {
        if(uiExecutor.isCurrentThread()){
            setZOrderInternal(z);
        }else{
            uiExecutor.runLater(()->setZOrderInternal(z));
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
            uiExecutor.runLater(this::sortChildrenInternal);
        }
        
    }

    private void sortChildrenInternal() {
        children.sort(Comparator.comparingInt(Renderable::getZOrder));
        childrenDirty();
    }
    
    // ===== SPATIAL OPERATIONS =====

    public S getRegion() {
        assertUiThread();
        S copy = regionPool.obtain();
        copy.copyFrom(region);
        return copy;
    }

    public S getAbsoluteRegion() {
        assertUiThread();
        S copy = regionPool.obtain();
        copy.copyFrom(region);
        copy.setPosition(region.getAbsolutePosition());
        return copy;
    }

    public S getEffectiveAbsoluteRegion(){
        assertUiThread();
        if (isVisible()) {
            return getAbsoluteRegion();
        }
        return null;
    }
  
    public boolean hitTest(P point) {
        return region.containsPoint(point);
    }
    
    public R hitTestChildren(P point) {
        assertUiThread();
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
    protected abstract void onInvalidateRequested(R renderable, S damage);
    protected abstract void onPendingInvalidateSet(R renderable, String text);
    protected abstract void onInvalidateDeferred(R renderable, String text);
    /**
     * @param localRegion Region to invalidate in local coordinates, null for full invalidation
     */
    public void invalidate(S localRegion) {
        onInvalidateRequested(self(), localRegion);
        if(!uiExecutor.isCurrentThread()){
            S copy = localRegion != null ? localRegion.copy(regionPool) : null;
            uiExecutor.runLater(()->{
                try{
                    invalidate(copy);
                }finally{
                    if(copy != null) regionPool.recycle(copy);
                }
            });
            return;
        }
        RenderableLayoutManagerHandle<R, LCB,G, GCB> lm = layoutManager;
        if (lm == null || !isStarted()) {
            // Coordinates are not final yet — defer. The post-layout
            // invalidateFromLayout() will emit correct damage after commit.
            pendingInvalidate = true;
            onPendingInvalidateSet(self(), "noLayoutManagerOrNotStarted");
            return;
        }

        if (lm.isLayoutExecuting()) {
            if (lm.isInCurrentPass(self())) {
                // This node is being committed in the active pass — let applyLayoutData()
                // consume pendingInvalidate after final geometry is known.
                pendingInvalidate = true;
                onInvalidateDeferred(self(), "inCurrentLayoutPass");
                return;
            }
            // This node is outside the active pass. Defer to preserve ordering
            // with in-flight layout commits and use fresh absolute transforms.
            deferInvalidateWhenLayoutIdle(lm, localRegion);
            return;
        }

        if(localRegion != null){
            localRegion.translate(region.getParentAbsolutePosition());
            invalidateImmediate(localRegion);
        }else{
            invalidateImmediate(null);
        }
    }

    private void deferInvalidateWhenLayoutIdle(
        RenderableLayoutManagerHandle<R, LCB,G, GCB> lm,
        S localRegion
    ) {
        lm.deferInvalidateWhenLayoutIdle(() -> {
            if (isDestroyed() || region == null) {
                if (localRegion != null) regionPool.recycle(localRegion);
                return;
            }

            if (layoutManager == null || !isStarted()) {
                pendingInvalidate = true;
                if (localRegion != null) regionPool.recycle(localRegion);
                return;
            }

            if (localRegion != null) {
                localRegion.translate(region.getParentAbsolutePosition());
                invalidateImmediate(localRegion);
            } else {
                invalidateImmediate(null);
            }
        });
    }

    protected abstract void onInvalidateImmediate(R renderable, S damage);
    /**
     * Unconditional damage accumulation and propagation.
     */
    private void invalidateImmediate(S absoluteDamage) {
        onInvalidateImmediate(self(), absoluteDamage);
        //RenderableTraceAspect.onInvalidateImmediate(this, absoluteDamage);
        if (absoluteDamage == null) {
            if (damage != null) regionPool.recycle(damage);
            damage = regionPool.obtain();
            damage.copyFrom(region);
            damage.translate(region.getParentAbsolutePosition());
        } else {


            if (damage == null) {
                damage = regionPool.obtain();
                damage.set(absoluteDamage);
            } else {
                damage.unionInPlace(absoluteDamage);

                try (PooledRegion<S> fullNode = regionPool.obtainPooled()) {
                    S absFullNode = fullNode.get();
                    absFullNode.copyFrom(region);
                    absFullNode.translate(region.getParentAbsolutePosition());
                    if (damage.contains(absFullNode)) {
                        damage.set(absFullNode);
                    }
                }
            }
            regionPool.recycle(absoluteDamage);
        }

        propagateDamageToParent();

        if (parent == null) {
            // Root handoff: translate the final damage region to absolute
            // coordinates before it enters the container accumulator.
            onDamageReported(self(), damage);
            reportDamage(damage);
            scheduleRender();
        }
    }

    protected abstract void onDamageReported(R renderable, S damage);

    private boolean renderRequested = false;

    private void scheduleRender() {
        RenderableLayoutManagerHandle<R, LCB, G, GCB> lm = layoutManager;
        if (lm != null) {
            lm.requestRender(self());
            return;
        }

        if (renderRequested) return;
        renderRequested = true;
        
        uiExecutor.runLater(() -> {
            renderRequested = false;
            if (onRenderRequest != null) {
                onRenderRequest.accept(self());
            }
        });
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
        if (damageTarget == null || damage == null) return;

        damageTarget.propagateDamageUp(damage);
    }

    protected abstract void onDamagePropagated(R renderable, R parent, S absChildDamage);

    /**
     * Child invalidated - accumulate damage
     * 
     * @param childDamageInOurLocalSpace Damage region in our local coordinate space
     *                                   (caller will recycle this after we return)
     */
    protected void propagateDamageUp(S absChildDamage) {
        onDamagePropagated(self(), parent, absChildDamage);
        childrenDirty = true;

        if (damage == null) {
            damage = regionPool.obtain();
            damage.set(absChildDamage);
        } else {
            damage.unionInPlace(absChildDamage);
        }

        propagateDamageToParent();

        if (parent == null) {
            Log.logMsg("[Renderable: " + getName() + "] propagateDamageUp reached root"
                + " - localDamage: " + damage, LOG_LEVEL);
            reportDamage(damage);
            scheduleRender();
        }
    }
    
    /**
     * Convenience: invalidate entire node
     */
    public void invalidate() {
        invalidate(null);
    }
    
    // ===== RENDERING =====
    protected abstract void onToBatchStart(R renderable);
    protected abstract void onToBatchEnd(R rendreable, boolean b);
      /**
     * Root-level entry point. Called by ContainerHandle with no incoming clip.
     * Guards the common "nothing at all to do" case before allocating anything.
     */
    public void toBatch(B batch) {
        assertUiThread();
        onToBatchStart(self());
        if (damage == null && !childrenDirty) {
            onToBatchEnd(self(), false);
            return;
        }

        S fullSpace = regionPool.obtain();
        fullSpace.copyFrom(region);
        fullSpace.translate(region.getParentAbsolutePosition());
        toBatch(batch, fullSpace);

        regionPool.recycle(fullSpace);
        onToBatchEnd(self(), damage != null || childrenDirty);
    }

    /**
     * Public entry point. Called by parent nodes and ContainerHandle.
     *
     * @param batch      target batch builder
     * @param clipRegion clip in absolute screen coordinates
     */
    public void toBatch(B batch, S clipRegion) {
        assertUiThread();
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
            absBounds.translate(region.getParentAbsolutePosition());

            if (!absBounds.intersects(clipRegion)) return;

            try (PooledRegion<S> visibleClipPooled = regionPool.obtainPooled()) {
                S visibleClip = visibleClipPooled.get();
                visibleClip.copyFrom(absBounds.intersection(clipRegion));

                boolean hasSelfDamage = damage != null;
                boolean isForced      = forcedRegion != null && absBounds.intersects(forcedRegion);
                S childClipRegion     = getChildClipRegion(clipRegion, visibleClip);

                if (hasSelfDamage) {
    
                    try (PooledRegion<S> renderClipPooled = regionPool.obtainPooled()) {
                        S renderClip = renderClipPooled.get();
                        renderClip.copyFrom(damage.intersection(visibleClip));
                        

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
                                renderChildrenByLayer(batch, childClipRegion, renderClip);
                    
                            } catch (Exception e) {
                                Log.logError("[Renderable:" + getName() + "] renderChildren exception", e);
                            }
                        }
                        recycleDamage();
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
                        renderChildrenByLayer(batch, childClipRegion, visibleClip);
          
                    } catch (Exception e) {
                        Log.logError("[Renderable:" + getName() + "] renderChildren (forced) exception", e);
                    }
                    recycleDamage();
                }
                
                if (childrenDirty) {
                    try {
                        renderChildrenByLayer(batch, childClipRegion, null);
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

    private void recycleDamage() {
        S dm = damage;
        damage = null;
        if (dm != null) { 
            regionPool.recycle(dm); 
        }
    }
    
    protected abstract R[] createRenderableArray(int size);

    /**
     * Resolve the clip region used when rendering this node's children.
     *
     * Default behavior clips children to this node's own visible bounds
     * (self bounds intersected with incoming clip). Subclasses may return
     * {@code incomingClip} to allow children to render outside this node's
     * bounds while still respecting ancestor clipping.
     *
     * Implementations should return either {@code incomingClip} or
     * {@code visibleClip}; allocating a new region here is discouraged.
     */
    protected S getChildClipRegion(S incomingClip, S visibleClip) {
        return visibleClip;
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
        assertUiThread();
        if (childrenDirty) {
            int size = children.size();
            if (renderBuffer == null || renderBuffer.length < size) {
                renderBuffer  = createRenderableArray(size);
                sortScratch   = createRenderableArray(size);
                sortKeys      = new long[size];
            }

            int count = 0;
            for (int i = 0; i < size; i++) {
                R child = children.get(i);
                if (child.getRenderingParent() == self()) {
                    // bit layout (64 bits total):
                    // [63:60] layerIndex —  4 bits  (0–15)
                    // [59:28] zOrder     — 32 bits  (biased unsigned)
                    // [27:0]  index      — 28 bits  (0–268,435,455)
                   sortKeys[count] = ((long) child.getLayerIndex() << 60)
                        | (((long) child.getZOrder() - Integer.MIN_VALUE) << 28)
                        | (long) count;
                    renderBuffer[count] = child;
                    count++;
                }
            }

            Arrays.sort(sortKeys, 0, count);

            // reconstruct renderBuffer in sorted order via embedded index
            for (int i = 0; i < count; i++) {
                sortScratch[i] = renderBuffer[(int)(sortKeys[i] & 0xFFFFFFFL)];
            }

            // swap so renderBuffer holds sorted refs; sortScratch becomes next rebuild's workspace
            R[] tmp    = renderBuffer;
            renderBuffer = sortScratch;
            sortScratch  = tmp;

            // null stale refs to avoid GC retention
            for (int i = count; i < renderCount; i++) {
                renderBuffer[i] = null;
            }

            renderCount  = count;
            childrenDirty = false;
        }

        for (int i = 0; i < renderCount; i++) {
            renderBuffer[i].toBatchInternal(batch, visibleClip, forcedRegion);
        }
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
        assertUiThread();
        if (!isVisible()) {
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

    protected abstract void onPhaseAdvance(R renderable, String was, String next);

    void advanceRenderPhase(RenderPhase next) {
        RenderPhase was = this.renderPhase;
        if (was != next){
            this.renderPhase = next;
            onPhaseAdvance(self(), was.name(), next.name());
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
    public abstract S measureContent(LC[] childContexts);

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
        return RenderableStates.isRenderableFocusable(stateMachine.getSnapshot());
    }
    
    public void setFocusable(boolean f) { stateMachine.addState(RenderableStates.STATE_FOCUSABLE); }
    public boolean hasFocus() { return stateMachine.hasState(RenderableStates.STATE_FOCUSED); }

    public void setOnFocusChanged(BiConsumer<R,Boolean> onFocusChanged){
        assertUiThread();
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
    
    public int getFocusIndex() { assertUiThread(); return focusIndex; }
    public void setFocusIndex(int index) { assertUiThread(); this.focusIndex = index; }
    
    public List<R> getFocusableDescendants() {
        assertUiThread();
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
        assertUiThread();
        if (isFocusable()) {
            result.add(self());
        }
        for (R child : children) {
            child.collectFocusable(result);
        }
    }
    
    // ===== VISIBILITY & STATE =====
    

    public boolean isVisible() {
        return RenderableStates.isRenderableVisible(stateMachine.getSnapshot());
        
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
        assertUiThread();
        stateMachine.addState(RenderableStates.STATE_ATTACHED);
        requestLayoutUpdate();
    }

    public void detachFromRoot() {
        assertUiThread();
        stateMachine.removeState(RenderableStates.STATE_ATTACHED);
        requestLayoutUpdate();
    }

  
    



    public boolean isEffectivelyHidden() {
        return stateMachine.hasState(RenderableStates.STATE_EFFECTIVELY_HIDDEN);
    }

    public boolean isEffectivelyInvisible() {
        return stateMachine.hasState(RenderableStates.STATE_EFFECTIVELY_INVISIBLE);
    }

    public void collapse() {
        setVisible(false);
    }
    
    public void expand() {
        setVisible(true);
    }
    


    /**
     * Does not force hide
     *  
     * @param visible
     */
    public void setVisible(boolean visible) {
        if(!uiExecutor.isCurrentThread()){
            uiExecutor.runLater(()->setVisible(visible));
            return;
        }
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
    
    public boolean isHiddenDesired() {
        return stateMachine.hasState(RenderableStates.STATE_HIDDEN_DESIRED);
    }

    public boolean isHidden() {
        return stateMachine.hasAnyState(RenderableStates.STATE_HIDDEN_DESIRED, RenderableStates.STATE_EFFECTIVELY_HIDDEN);
    }

    public void setInvisible(boolean invisible) {
        assertUiThread();
        StateSnapshot state = stateMachine.getSnapshot();
        if (invisible && (
            !state.hasState(RenderableStates.STATE_INVISIBLE_DESIRED)
            || state.hasState(RenderableStates.STATE_HIDDEN_DESIRED)
        )) {
            stateMachine.addState(RenderableStates.STATE_INVISIBLE_DESIRED);
            stateMachine.removeState(RenderableStates.STATE_HIDDEN_DESIRED);
            stateMachine.removeState(RenderableStates.STATE_FORCED_HIDDEN_DESIRED);
            requestLayoutUpdate();
        } else if(!invisible && state.hasState(RenderableStates.STATE_INVISIBLE_DESIRED)){
            stateMachine.removeState(RenderableStates.STATE_INVISIBLE_DESIRED);
            requestLayoutUpdate();
        }
    }

    public boolean isInvisibleDesired() {
        return stateMachine.hasState(RenderableStates.STATE_INVISIBLE_DESIRED);
    }
            
    public boolean isInvisible() {
        return stateMachine.hasAnyState(
            RenderableStates.STATE_INVISIBLE_DESIRED,
            RenderableStates.STATE_EFFECTIVELY_INVISIBLE
        );
    }
    

    // ===== FOCUS API =====
    
    public void requestFocus() {
        assertUiThread();
        if (!isFocusable()) {
            return;
        }
        focusRequestToken = FOCUS_REQUEST_SEQ.incrementAndGet();
        stateMachine.addState(RenderableStates.STATE_FOCUS_DESIRED);
        if (layoutManager != null) {
            layoutManager.requestFocus(self());
        }
    }

    public void clearFocus() {
        assertUiThread();
        if (hasFocus()) {
            stateMachine.removeState(RenderableStates.STATE_FOCUSED);
        }
        stateMachine.removeState(RenderableStates.STATE_FOCUS_DESIRED);
    }

    public void focus() {
        assertUiThread();
        if (isFocusable()) {
            stateMachine.addState(RenderableStates.STATE_FOCUSED);
            stateMachine.removeState(RenderableStates.STATE_FOCUS_DESIRED);
        }
    }

    long getFocusRequestToken() {
        return focusRequestToken;
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

    public boolean isHiddenForced(){
        return stateMachine.hasAllStates(RenderableStates.STATE_FORCED_HIDDEN_DESIRED, RenderableStates.STATE_HIDDEN_DESIRED);
    }
  
    /***
     * Hides a renderable until show() is called
     */
    public void hide() {
        stateMachine.addState(RenderableStates.STATE_FORCED_HIDDEN_DESIRED);
        setVisible(false);
    }

    public void show() {
        stateMachine.removeState(RenderableStates.STATE_FORCED_HIDDEN_DESIRED);
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
    protected void onInvisible() {}
    protected void onInvisibleRemoved() {}

    /*Clean up handlers if not needed*/
    protected void onRemovedFromLayout() { }

    /* Called by layoutmanager when renderable is removed 
     * to be destroyed
     */
    void removedFromLayout() {
        if(notifyRemovedFromLayout != null){ notifyRemovedFromLayout.accept(self()); }
        clearLayoutManager();
    
        recycleDamage();
        onRemovedFromLayout();

    }
    /**
     * destroy() is intentionally non-recursive — children are NOT destroyed
     * here. Callers that need child teardown must destroy children explicitly
     * before or after calling destroy() on the parent.
     */
    public void destroy() {
        if(uiExecutor.isCurrentThread()){
            destroyInternal();
        }else{
            uiExecutor.runLater(this::destroyInternal);
        }
   
    }

    protected void onInternalDestroying() { }

    private final void destroyInternal(){
     
        onInternalDestroying();
        onDestroying();
        stateMachine.addState(RenderableStates.DESTROYED);
        if (!children.isEmpty()) {
            clearChildrenInternal();
        }
        
        recycleDamage();
        childGroups.clear();
        childLayoutCallbacks.clear();
        stateMachine.clearAllStates();
        cleanupEventHandlers();
        if (region != null) {
            S oldRegion = region;
            region = null;
            regionPool.recycle(oldRegion);
        }
        if (requestedRegion != null) {
            S oldRegion = requestedRegion;
            requestedRegion = null;
            regionPool.recycle(oldRegion);
        }
    }

    protected void onDestroying(){ }



    



    // ===== UTILITIES =====
    
    @SuppressWarnings("unchecked")
    protected R self() { return (R) this; }
    
    public String getName() { return name; }
    public ConcurrentBitFlagStateMachine getStateMachine() { return stateMachine; }
    
    public R getRoot() {
        assertUiThread();
        R node = self();
        while (node.parent != null) {
            node = node.parent;
        }
        return node;
    }
    
    public int getDepth() {
        assertUiThread();
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
        assertUiThread();
        onRenderRequest = onRequest;
        if (onRequest != null) {
            attachAsRoot();
        } else {
            detachFromRoot();
        }
    }

    public void setDamageAccumulator(Consumer<S> accumulator) {
        assertUiThread();
        this.damageAccumulator = accumulator;
        // Propagate to children — they share the same frame accumulator
        for (R child : getChildren()) {
            child.setDamageAccumulator(accumulator);
        }
    }


    private void reportDamage(S absoluteRegion) {
        if (damageAccumulator != null && absoluteRegion != null) {
            damageAccumulator.accept(absoluteRegion.copy(regionPool));
        }
    }

    public SerializedVirtualExecutor getUIExecutor() {
        return uiExecutor;
    }

    protected final void assertUiThread() {
        if (layoutManager != null && !uiExecutor.isCurrentThread()) {
            throw new IllegalStateException(
                "Method must be called on UI thread. " +
                "Current thread: " + Thread.currentThread()
            );
        }
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
        assertUiThread();
        return damage != null || childrenDirty;
    }

    /**
     * Clear the render flag after successful render
     * Called by ContainerHandle after toBatch() completes
     * 
     * Note: toBatch() already clears damage regions during rendering,
     * but this provides explicit confirmation that render completed
     */
    public void clearRenderFlag() {
        assertUiThread();
        recycleDamage();
        childrenDirty = false;
    }

    public S copyAbsoluteDamage(){
        assertUiThread();
        return damage.copy(regionPool);
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
        if(manager == null){
            onLayoutManagerCleared();
        }
        this.layoutManager = manager;
        onLayoutManagerSet();
    }

    protected void onLayoutManagerSet() {}
    protected void onLayoutManagerCleared() {}

    public void clearLayoutManager() {
        assertUiThread();
        onLayoutManagerCleared();
        this.layoutManager = null;
        
    }



    public void beginLayoutBatch() {
        assertUiThread();
        RenderableLayoutManagerHandle<R, LCB,G, GCB> lm = this.layoutManager;
        if(lm != null){
            this.batching = true;
            lm.beginBatch();
        }
    }

    /**
     * End batching and trigger single layout pass
     */
    public void endLayoutBatch(){
        assertUiThread();
        if(this.layoutManager != null){
            this.layoutManager.endBatch();
            this.batching = false;
        }
    }

    /**
     * Execute operations within a batch transaction
     * Ensures single layout pass regardless of how many dirty marks
     */
    public void batch(Runnable operations) {
        assertUiThread();

        if(this.layoutManager != null){
            if(batching){
                // Already in a batch — just run the operations
                operations.run();
                return;
            }
            batching = true;
            this.layoutManager.beginBatch();
            try {
                operations.run();
            } finally {
                this.layoutManager.endBatch();
                batching = false;
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
        S spatialDamage = layoutData.hasRegion()
            ? applySpatialChange(layoutData)
            : null;

        boolean hasStateChanges = layoutData.hasStateChanges()
            ? applyStateChanges(layoutData)
            : false;

        boolean hadPendingInvalidate = pendingInvalidate;

        if (spatialDamage != null || hasStateChanges || pendingInvalidate) {
            pendingInvalidate = false;
            invalidateImmediate(spatialDamage);
        }

        onApplyLayoutData(self(), layoutData.hasRegion(), hasStateChanges, hadPendingInvalidate);
    }

    protected abstract void onApplyLayoutData(R renderable, boolean hasRegion, boolean hasStateChanges, boolean hadPendingInvalidate);

    /**
     * 
     * @param layoutData
     * @return returns union of old damage and new damage
     */
    private S applySpatialChange(LD layoutData) {
   
        try (PooledRegion<S> pooledMerged = regionPool.obtainPooled()) {
            S merged = pooledMerged.get();

            layoutData.mergeIntoRegion(
                region, 
                merged
            );

            boolean changed = !region.equals(merged);
            if (!changed) {
                clearRequestedRegion();
                return null;
            }

            S oldRegion = region.copy(regionPool);
            
            region.copyFrom(merged);
            clearRequestedRegion();

            S unionRegionChange = regionPool.obtain();
            unionRegionChange.copyFrom(oldRegion);
            unionRegionChange.translate(oldRegion.getParentAbsolutePosition());
            merged.translate(merged.getParentAbsolutePosition());
            unionRegionChange.unionInPlace(merged);

            onRegionChanged(oldRegion, region);
            if (notifyOnRegionChanged != null) {
                notifyOnRegionChanged.accept(oldRegion, region);
            } else {
                regionPool.recycle(oldRegion);
            }

            return unionRegionChange;
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
        assertUiThread();
        notifyOnRegionChanged = onRegionChanged;
    }

    /**
     * Apply state changes - immediate state machine updates
     * State machine callbacks execute on uiExecutor (deferred)
     */
    private boolean applyStateChanges(LD layoutData) {

        StateSnapshot beforeState = stateMachine.getSnapshot();

        Boolean applyHidden = layoutData.getHidden();
        Boolean applyEffectivelyHidden = layoutData.getEffectivelyHidden();

        Boolean applyInvisible = layoutData.getInvisible();
        
        Boolean applyEffectivelyInvisible = layoutData.getEffectivelyInvisible();
        
        if(Boolean.TRUE.equals(applyHidden)){
            applyStateIfPresent(applyHidden, RenderableStates.STATE_HIDDEN_DESIRED);
            if(applyInvisible != null || applyEffectivelyInvisible != null){
                applyStateIfPresent(false, RenderableStates.STATE_INVISIBLE_DESIRED);
                applyStateIfPresent(false, RenderableStates.STATE_EFFECTIVELY_INVISIBLE);
            }
        }else{
            applyStateIfPresent(applyHidden, RenderableStates.STATE_HIDDEN_DESIRED);
            applyStateIfPresent(applyInvisible, RenderableStates.STATE_INVISIBLE_DESIRED);
            applyStateIfPresent(applyEffectivelyInvisible, RenderableStates.STATE_EFFECTIVELY_INVISIBLE);
        }
        
        applyStateIfPresent(applyEffectivelyHidden, RenderableStates.STATE_EFFECTIVELY_HIDDEN);
        

        StateSnapshot snapShot = stateMachine.getSnapshot();

        boolean isVisible = RenderableStates.isRenderableVisible(snapShot);

        if(isVisible != stateMachine.hasState(RenderableStates.STATE_RENDERABLE)){
            if(isVisible){
                stateMachine.addState(RenderableStates.STATE_RENDERABLE);
            }else{
                stateMachine.removeState(RenderableStates.STATE_RENDERABLE);
            }
        }

        return handleStateTransitionDamage(beforeState, stateMachine.getSnapshot());
    }

    private boolean handleStateTransitionDamage(StateSnapshot beforeState, StateSnapshot afterState) {
        boolean wasVisible = RenderableStates.isRenderableVisible(beforeState);
        boolean isVisibleNow = RenderableStates.isRenderableVisible(afterState);

        if (wasVisible && !isVisibleNow) {
            childrenDirty = false;
            return true;
        }

        if (!wasVisible && isVisibleNow) {
            childrenDirty = true;
            return true;
        }

        boolean wasInvisible = RenderableStates.isRenderableInvisible(beforeState);
        boolean isInvisible = RenderableStates.isRenderableInvisible(afterState);

        if (wasInvisible && !isInvisible) {
            childrenDirty = true;
        }

        if (!wasInvisible && isInvisible) {
            childrenDirty = false;
        }

        return wasInvisible != isInvisible;
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
            uiExecutor.runLater(()->makeFloatingInternal(anchor));
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
        childrenDirty();
        if (parent != null) {
            parent.childrenDirty();
        }
        // Migrate to floating layer in layout manager
        if (layoutManager != null) {
            layoutManager.migrateToFloating(self(), anchor);
        }
        
        pendingInvalidate = true;
        requestLayoutUpdate();
  
    }
    
    /**
     * Make this renderable static (normal parent-child rendering)
     */
    public void makeStatic() {
        
        if(!uiExecutor.isCurrentThread()){
            uiExecutor.runLater(this::makeStaticInternal);
        }else{
            makeStaticInternal();
        }
    }

    private void makeStaticInternal(){
        if (!isFloating) {
            return;
        }
        this.isFloating = false;
        this.positionAnchor = null;
        this.layerIndex = LAYER_NORMAL;
        this.logicalParent = null;
        this.renderingParent = null;
        if (parent != null) {
            parent.childrenDirty();
        }
        
        // Migrate back to regular layout
        if (layoutManager != null) {
            layoutManager.migrateToRegular(self());
        }
        
        pendingInvalidate = true;
        requestLayoutUpdate();
    }
    
    /**
     * Set layer index for rendering order
     */
    public void setLayerIndex(int layer) {
        assertUiThread();
        if (this.layerIndex != layer) {
            this.layerIndex = layer;
            if (!isFloating && parent != null) {
                parent.childrenDirty();
            }
            invalidate();  // Re-render in new layer
        }
    }

    /**
     * Set position anchor for floating elements
     */
    public void setPositionAnchor(R anchor) {
        assertUiThread();
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
        assertUiThread();
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


    public abstract int getMinSize(int axis);

    /**
     * Check if there are any pending groups
     */
    public boolean hasChildGroups() {
        return !childGroups.isEmpty();
    }
    


    public void clearChildGroups() {
        assertUiThread();
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
        assertUiThread();
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
        assertUiThread();
        addToChildGroup(renderable, groupId);          // always
        if (isAttachedToLayoutManager()) {
            layoutManager.addToLayoutGroup(renderable, groupId);
        }
    }

    public GCB getLayoutGroupCallback(String groupId){
        assertUiThread();
        return getChildLayoutGroupCallback(groupId);

    }
    
    /**
     * Get the groupId of a renderable or null if not exists
     * 
     * @param renderable renderable to query
     * @return group ID or null
     */
    public String getLayoutGroupIdByRenderable(R renderable) {
        assertUiThread();
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
        assertUiThread();
        Iterator<Entry<String, GSE>> it = childGroups.entrySet().iterator();
        while (it.hasNext()) {
            Entry<String, GSE> stateEntry = it.next();
            GSE state = stateEntry.getValue();
            List<R> members = state.getMembers();
            members.remove(renderable);
        }
        if (isAttachedToLayoutManager()) {
            layoutManager.removeLayoutGroupMember(renderable);
        }
    }
    
    /**
     * Destroy a group and remove all members
     * 
     * @param groupId group to destroy
     */
    public void removeLayoutGroup(String groupId) {
        assertUiThread();
        childGroups.remove(groupId);
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
        assertUiThread();
        createChildGroup(groupId);
        GSE state = childGroups.get(groupId);
        state.setLayoutCallback(callback);
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
        assertUiThread();
        return layerIndex;
    }
    
    public R getPositionAnchor() {
        assertUiThread();
        return positionAnchor;
    }
    
    public R getLogicalParent() {
        assertUiThread();
        return logicalParent != null ? logicalParent : parent;
    }
    
    public R getRenderingParent() {
        assertUiThread();
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
