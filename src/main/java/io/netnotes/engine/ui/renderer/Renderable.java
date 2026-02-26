package io.netnotes.engine.ui.renderer;

import io.netnotes.engine.ui.PooledRegion;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.SpatialRegionPool;
import io.netnotes.engine.ui.VisibilityPredicate;
import io.netnotes.engine.ui.renderer.layout.GroupCallbackEntry;
import io.netnotes.engine.ui.renderer.layout.GroupLayoutCallback;
import io.netnotes.engine.ui.renderer.layout.LayoutCallback;
import io.netnotes.engine.ui.renderer.layout.LayoutContext;
import io.netnotes.engine.ui.renderer.layout.LayoutData;
import io.netnotes.engine.ui.renderer.layout.LayoutGroup;
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
    LC extends LayoutContext<B,R,P,S,LD,LCB,LC,?>,
    LD extends LayoutData<B,R,S,LD,?>,
    LCB extends LayoutCallback<B,R,P,S,LC,LD,LCB>,
    GCB extends GroupLayoutCallback<B,R,P,S,LD,LC,?,GCB>,
    GCE extends GroupCallbackEntry<G,GCB,GCE>,
    GSE extends Renderable.GroupStateEntry<R,GCE,GSE>,
    G extends LayoutGroup<B,R,P,S,LD,LC,GCB,?,GCE,G>,
    R extends Renderable<B,P,S,LC,LD,LCB,GCB,GCE,GSE,G,R>
> {

    private Map<R, LCB> childCallbacks = new HashMap<>();

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


    
    // ===== LAYOUT - DIMENSION AGNOSTIC =====
 
    protected S region;
    protected S requestedRegion = null;
    protected boolean layoutInProgress = false;
 
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
    
    protected RenderableLayoutManagerHandle<R, LCB,G,GCE, GCB> layoutManager = null;
    protected Consumer<R> onRenderRequest = null;
    protected BiConsumer<S,S> notifyOnRegionChanged = null;
    protected Consumer<R> notifyRemovedFromLayout = null;
    protected VisibilityPredicate<R> visibilityPolicy = null;
    protected BiConsumer<R, Boolean> onVisibilityChanged = null;
    protected BiConsumer<R, Boolean> onFocusChanged = null;
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

    protected void setupBaseTransitions() {
        // Visibility state changes
        stateMachine.onStateAdded(RenderableStates.STATE_RENDERABLE, (old, now, bit) -> {
            onRenderable();
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
                onVisibilityChanged.accept(self(), true);
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
        
        // Enabled state changes
        stateMachine.onStateRemoved(RenderableStates.STATE_EFFECTIVELY_ENABLED, (old, now, bit) -> {
            // Clear focus when disabled
            if (hasFocus()) {
                clearFocus();
            }
            onDisabled();
        });
        
        stateMachine.onStateAdded(RenderableStates.STATE_EFFECTIVELY_ENABLED, (old, now, bit) -> {
            onEnabled();
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

    public void addChild(R child, LCB callback){
        
        uiExecutor.executeFireAndForget(() -> addChildInternal(child, callback));
    }

    private void addChildInternal(R child, LCB callback) {
        if(isDestroyed()){ return; }

        if (child.parent != null) {
            child.parent.removeChild(child);
        }
        
        children.add(child);
        child.parent = self();
        
        if (callback != null) {
            childCallbacks.put(child, callback);
        }
        
        child.stateMachine.addState(RenderableStates.STATE_ATTACHED);
      
        
        if (layoutManager != null) layoutManager.registerChild(child, childCallbacks.get(child));
        child.markVisibilityDirty();
    }


    // ===== REQUESTED REGION SYSTEM =====
    
    private void ensureRequestedRegion() {
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
    
    private void notifyRequestPending() {
        if (layoutManager != null) layoutManager.markRequestPending(self());
    }
    
    public void setPosition(P position) {
        // Silent no-op while hidden - use setHiddenRegion() for explicit staging
        if (isHidden()) {
            return;
        }
        
        ensureRequestedRegion();
        requestedRegion.setPosition(position);
        notifyRequestPending();
    }
    
    /***
     * Set Region and propagate size
     * 
     * @param region Spatial Region
     */
    public void setRegion(S region) {
        ensureRequestedRegion();
        requestedRegion.copyFrom(region);
        notifyRequestPending();
    }

    public void setBounds(S bounds){
        setRegion(bounds);
    }

    public LCB getChildCallback(R child) {
        return childCallbacks.get(child);
    }
    
    public void removeChild(R child) {
        uiExecutor.execute(() -> removeChildInternal(child));
    }

    protected void removeChildInternal(R child) {
        boolean removed = children.remove(child);
        if (!removed) return;
        
        // Damage the region where child was
        if (child.region != null && !child.region.isEmpty()) {
            S parentLocalDamage = regionPool.obtain();
            parentLocalDamage.copyFrom(child.region);
            parentLocalDamage.transformByParent(child.region);
            invalidateChild(parentLocalDamage);
            regionPool.recycle(parentLocalDamage);
        }
        
        childCallbacks.remove(child);
        child.parent = null;
        child.stateMachine.removeState(RenderableStates.STATE_ATTACHED);
        
        if (layoutManager != null) layoutManager.unregister(child);
    }
        
    public int getZOrder() { return zOrder; }
    
    public void setZOrder(int z) {
        if (this.zOrder != z) {
            this.zOrder = z;
            if (parent != null) {
                parent.sortChildren();
                parent.invalidate();
            }
        }
    }
    
    protected void sortChildren() {
        children.sort(Comparator.comparingInt(Renderable::getZOrder));
    }
    
    // ===== SPATIAL OPERATIONS =====
    
    /**
     * Get allocated region (copy)
     * Callers can mutate the returned region without affecting our state
     */
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
  
    /**
     * Check if a point is within this renderable
     * Point coordinates are in parent's local space
     */
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
     * Invalidate with damage region
     * 
     * THREADING: Must be called from a synchronized context (layout, event handlers).
     * Does NOT wrap in serialExec - damage tracking is local mutable state.
     * 
     * ALLOCATION STRATEGY:
     * 1. Reuse existing localDamage via unionInPlace
     * 2. Only allocate when localDamage is null
     * 3. Recycle intermediate regions immediately
     * 4. absoluteDamage updated in-place
     * 
     * @param localRegion Region to invalidate in local coordinates, null for full invalidation
     */
    public void invalidate(S localRegion) {
        // Accumulate local damage
        if (localRegion == null) {
            // Full invalidation - replace any existing damage
            if (localDamage != null) {
                regionPool.recycle(localDamage);
            }
            localDamage = regionPool.obtain();
            localDamage.copyFrom(region);
        } else {
            if (localDamage == null) {
                // First damage - obtain from pool and copy
                localDamage = regionPool.obtain();
                localDamage.copyFrom(localRegion);
            } else {
                // Merge with existing damage IN PLACE - zero allocation
                localDamage.unionInPlace(localRegion);
                
                // Optimization: If damage covers entire bounds, simplify
                if (localDamage.contains(region)) {
                    localDamage.copyFrom(region);
                }
            }
        }
        
        // Update absolute damage (reuses existing absoluteDamage object)
        updateAbsoluteDamage();
        
        // Propagate to parent (uses temporary region from pool, recycled immediately)
        propagateDamageToParent();
        
        // Notify render system if we're root
        if (parent == null && onRenderRequest != null) {
            Log.logMsg("[Renderable:" + getName() + "] invalidate → firing onRenderRequest");
            onRenderRequest.accept(self());
        }else if (parent == null) {
            Log.logMsg("[Renderable:" + getName() + "] invalidate → onRenderRequest is NULL, render lost");
        }
    }
    
    /**
     * Update absolute damage from local damage + region transform
     * Reuses absoluteDamage object to avoid allocation
     */
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
        
        // Set to local damage then transform to absolute coordinates
        absoluteDamage.copyFrom(localDamage);
        
        absoluteDamage.setParentAbsolutePosition(
            region.getParentAbsolutePosition()
        );
    }

    public abstract boolean isSizedByContent();
    
   /**
     * Propagate damage to parent
     */
    protected void propagateDamageToParent() {
        // Damage RENDERING parent (floating layer if floating)
        R damageTarget = isFloating ? renderingParent : parent;
        
        if (damageTarget == null || localDamage == null) return;
        
        try (PooledRegion<S> pooled = regionPool.obtainPooled()) {
            S parentLocalDamage = pooled.get();
            
            parentLocalDamage.copyFrom(localDamage);
            parentLocalDamage.transformByParent(region);
            
            damageTarget.invalidateChild(parentLocalDamage);
        }
    }

    /**
     * Child invalidated - accumulate damage
     * 
     * @param childDamageInOurLocalSpace Damage region in our local coordinate space
     *                                   (caller will recycle this after we return)
     */
    protected void invalidateChild(S childDamageInOurLocalSpace) {
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
     * Render with damage-based clipping
     * 
     * ALLOCATION STRATEGY:
     * - Obtains temporary regions for bounds/clip calculations
     * - Recycles them immediately after use
     * - Clears damage regions after rendering
     * 
     * @param batch The batch builder to render into
     * @param clipRegion The clip region in absolute coordinates
     */
    public void toBatch(B batch, S clipRegion) {
        Log.logMsg("[Renderable:" + getName() + "] toBatch children check"
        + " visible=" + isVisible()
        + " STATE_EFFECTIVELY_VISIBLE=" + stateMachine.hasState(RenderableStates.STATE_EFFECTIVELY_VISIBLE)
        + " childrenDirty=" + childrenDirty
        + " children=" + children.size()
        + " renderableChildren=" + children.stream()
            .filter(c -> c.getRenderingParent() == this)
            .map(R::getName)
            .collect(Collectors.joining(","))
    );

        if (!isVisible()) {
            Log.logMsg("[Renderable:" + getName() + "] toBatch notVisible exiting");
            return;
        }
      
        // Calculate absolute bounds
        try (PooledRegion<S> absBoundsPooled = regionPool.obtainPooled()) {
            S absBounds = absBoundsPooled.get();
            absBounds.copyFrom(region);
            
            // Transform to absolute using RENDERING parent chain
            R node = getRenderingParent();
            while (node != null) {
                absBounds.transformByParent(node.region);
                node = node.getRenderingParent();
            }
            
            Log.logMsg("[Renderable:" + getName() + "] toBatch renderingParent: " +(node == null ? "renderingParent null " : node.getName()));
            
            if (!absBounds.intersects(clipRegion)) {
                 Log.logMsg("[Renderable:" + getName() + "] toBatch absBounds does not intersect exiting:"
                    + "absBound:" + (absBounds != null ? absBounds.toString() : "null")
                    + "clipRegion: " + (clipRegion != null ? clipRegion.toString() : "null")
                );
                return;
            }
            
            // Render self if damaged
            try (PooledRegion<S> childClipPooled = regionPool.obtainPooled()) {
                S childClip = childClipPooled.get();
                childClip.copyFrom(absBounds.intersection(clipRegion));
                
                boolean shouldRenderSelf = false;
                if (absoluteDamage != null) {
                    shouldRenderSelf = absoluteDamage.intersects(clipRegion);
                }
                
                if (shouldRenderSelf) {
                    try (PooledRegion<S> renderClipPooled = regionPool.obtainPooled()) {
                        S renderClip = renderClipPooled.get();
                        renderClip.copyFrom(absoluteDamage.intersection(clipRegion));
                        
                        batch.pushClipRegion(renderClip);
                        Log.logMsg("[Renderable:" + getName() + "] renderingSelf");
                        renderSelf(batch);
                        batch.popClipRegion();
                    }catch(Exception clipException){
                         Log.logError("[Renderable:" + getName() + "] toBatch clipException ", clipException);
                    }
                    
                    if (localDamage != null) {
                        regionPool.recycle(localDamage);
                        localDamage = null;
                    }
                    if (absoluteDamage != null) {
                        regionPool.recycle(absoluteDamage);
                        absoluteDamage = null;
                    }
                }else{
                    Log.logMsg("[Renderable:" + getName() + "] not renderingself");
                }
                
                // Render children in layer order
                if (childrenDirty) {
                    renderChildrenByLayer(batch, childClip);
                    childrenDirty = false;
                }else{
                    Log.logMsg("[Renderable:" + getName() + "] childrenNotDirty");
                }
            }
        }catch(Exception e){
             Log.logError("[Renderable:" + getName() + "] toBatch failed:", e);
        }
    }

    /**
     * Render children sorted by layer and z-index
     */
    protected void renderChildrenByLayer(B batch, S childClip) {
         Log.logMsg("[Renderable:" + getName() + "] renderChildrenByLayer");
        // Get rendering children (those in our rendering subtree)
        List<R> renderChildren = children.stream()
            .filter(c -> c.getRenderingParent() == this)
            .sorted(Comparator
                .comparingInt(R::getLayerIndex)
                .thenComparingInt(R::getZOrder))
            .collect(Collectors.toList());
        Log.logMsg("[Renderable:" + getName() + "] renderChildren: " + renderChildren.size());
        for (R child : renderChildren) {
            Log.logMsg("[Renderable:" + getName() + "] toBatch: " + child.getName());
            try {
                child.toBatch(batch, childClip);
            } catch (Exception e) {
                Log.logError("[Renderable:" + getName() + "] toBatch failed for child: " + child.getName(), e);
            }
        }
    }


    
    /**
     * Root-level render - creates full-space clip region
     */
    public void toBatch(B batch) {
        if (absoluteDamage == null && !childrenDirty) {
            return;  // Nothing to render
        }
        
        S fullSpace = regionPool.obtain();
        fullSpace.copyFrom(region);
        
        toBatch(batch, fullSpace);
        
        regionPool.recycle(fullSpace);
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
        return focusable && isVisible() && isEnabled();
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
        markVisibilityDirty();
    }
    
    public void detachFromRoot() {
        stateMachine.removeState(RenderableStates.STATE_ATTACHED);
        markVisibilityDirty();
    }

  
    public void setVisible(boolean visible) {
        if (visible && !(
            stateMachine.hasAnyState(RenderableStates.STATE_HIDDEN_DESIRED, RenderableStates.STATE_INVISIBLE_DESIRED)
        )) {
         
            if(visibilityPolicy != null && !visibilityPolicy.allowVisibilityChange(self(), visible)){
                return;
            }
        
            // Clear both modifiers - let effective state flow through
            stateMachine.removeStates(RenderableStates.STATE_HIDDEN_DESIRED, RenderableStates.STATE_INVISIBLE_DESIRED);
            markVisibilityDirty();
        } else if(!visible && !stateMachine.hasState(RenderableStates.STATE_HIDDEN_DESIRED)) {
            if(visibilityPolicy != null && !visibilityPolicy.allowVisibilityChange(self(), visible)){
                return;
            }
            // Default to HIDDEN (collapses space - most common case)
            stateMachine.addState(RenderableStates.STATE_HIDDEN_DESIRED);
            markVisibilityDirty();
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
            markVisibilityDirty();
        } else if(!hidden && state.hasState(RenderableStates.STATE_HIDDEN_DESIRED)){
            stateMachine.removeState(RenderableStates.STATE_HIDDEN_DESIRED);
            markVisibilityDirty();
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
            stateMachine.removeState(RenderableStates.STATE_HIDDEN_DESIRED); // Mutually exclusive
            markVisibilityDirty();
        } else if(!invisible && state.hasState(RenderableStates.STATE_INVISIBLE_DESIRED)){
            stateMachine.removeState(RenderableStates.STATE_INVISIBLE_DESIRED);
            markVisibilityDirty();
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
    

    
    /**
     * Check if enabled considering parent state
     */
    public boolean isEffectivelyEnabled() {
        return stateMachine.hasState(RenderableStates.STATE_EFFECTIVELY_ENABLED);
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
        return isEffectivelyVisible() && isEffectivelyEnabled();
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
        Log.logMsg("[ContainerHandle] + shouldRender()? " + shouldRender);
        return shouldRender;
    }
  
    public void hide() {
        setVisible(false);
    }

    public void show() {
        setVisible(true);
    }

    protected void onRenderable() {}
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
    protected void removedFromLayout() {
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
        childGroups.clear();
        childCallbacks.clear();
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

    
    public boolean isEnabled() {
        return stateMachine.hasState(RenderableStates.STATE_EFFECTIVELY_ENABLED);
    }

 
    protected void markVisibilityDirty() {
        if (layoutManager != null) layoutManager.markVisibilityDirty(self());
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

        Log.logMsg("[Renderable: "+getName()+"] + needsRender? " + needsRender + "localDmg: " + localDamage + " absDmg:" + absoluteDamage + " chrnDrty:" + childrenDirty);
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


    public void setLayoutManager(RenderableLayoutManagerHandle<R, LCB,G,GCE, GCB> manager) {
        this.layoutManager = manager;
    }

    public void clearLayoutManager() {

        this.layoutManager = null;
    }


    public void beginLayoutBatch() { 
        RenderableLayoutManagerHandle<R, LCB,G,GCE, GCB> lm = this.layoutManager;
        if(lm != null){
            lm.beginBatch();
        }
    }
    
    /**
     * End batching and trigger single layout pass
     */
    public void endLayoutBatch(){
        RenderableLayoutManagerHandle<R, LCB,G,GCE, GCB> lm = this.layoutManager;
        if(lm != null){
            lm.endBatch();
        }
    }
    
    /**
     * Execute operations within a batch transaction
     * Ensures single layout pass regardless of how many dirty marks
     */
    public void batch(Runnable operations) {
        RenderableLayoutManagerHandle<R, LCB,G,GCE, GCB> lm = this.layoutManager;
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
        Log.logMsg("[Renderable:" + getName() + "] applyLayoutData"
            + "\n\t applyLayoutData region=" + (layoutData.hasRegion() ? layoutData.getSpatialRegion().toString() : "null")
            + "\n\t onRenderRequest: " + (onRenderRequest != null)
            + "\n\t parent==null:    " + (parent == null)
        );

        layoutInProgress = true;
        try {
            // Apply spatial changes - IMMEDIATE
            if (layoutData.hasRegion()) {
                applySpatialChange(layoutData.getSpatialRegion());
            }
            
            // Apply state changes - IMMEDIATE state machine update
            // Callbacks from state transitions will be deferred by state machine
            if (layoutData.hasStateChanges()) {
                applyStateChanges(layoutData);
            }
            
        } finally {
            layoutInProgress = false;
        }
        Log.logMsg("[Renderable:" + getName() + "] applyLayoutData after invalidate"
            + "\n\t localDamage:     " + (localDamage != null)
            + "\n\t absoluteDamage:  " + (absoluteDamage != null)
        );
    }

    /**
     * Apply spatial change - immediate update, deferred reactions
     */
    private void applySpatialChange(S newRegion) {
        boolean changed = !region.equals(newRegion);
        
        if (!changed) {
            clearRequestedRegion();
            return;
        }
        
       

        if(notifyOnRegionChanged != null){
            S oldRegion = regionPool.obtain();
            oldRegion.copyFrom(region);
            
    
            region.copyFrom(newRegion);
            clearRequestedRegion();
            
            invalidate(newRegion);
            uiExecutor.executeFireAndForget(()->{
                notifyOnRegionChanged.accept(oldRegion, region);
            });
            
        }else{

            region.copyFrom(newRegion);
            clearRequestedRegion();
            
            invalidate(newRegion);
        }
    }

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
        // Apply MODIFIER states (may have been set by callback)
        applyStateIfPresent(layoutData.getHidden(), RenderableStates.STATE_HIDDEN);
        applyStateIfPresent(layoutData.getInvisible(), RenderableStates.STATE_INVISIBLE);
        applyStateIfPresent(layoutData.getEnabled(), RenderableStates.STATE_ENABLED_DESIRED);
        // Apply RESOLVED states (computed by manager post-callback)
        applyStateIfPresent(layoutData.getEffectivelyVisible(), RenderableStates.STATE_EFFECTIVELY_VISIBLE);
        applyStateIfPresent(layoutData.getEffectivelyEnabled(), RenderableStates.STATE_EFFECTIVELY_ENABLED);


        StateSnapshot snapShot = stateMachine.getSnapshot();

        boolean isRenderable = snapShot.hasState(RenderableStates.STATE_EFFECTIVELY_VISIBLE)
            && snapShot.hasState(RenderableStates.STATE_EFFECTIVELY_ENABLED);

        if(isRenderable != stateMachine.hasState(RenderableStates.STATE_RENDERABLE)){
            if(isRenderable){
                stateMachine.addState(RenderableStates.STATE_RENDERABLE);
            }else{
                stateMachine.removeState(RenderableStates.STATE_RENDERABLE);
            }
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
        if (isFloating && positionAnchor == anchor) {
            return;  // Already floating with same anchor
        }
        
        uiExecutor.executeFireAndForget(() -> {
            this.isFloating = true;
            this.positionAnchor = anchor;
            this.layerIndex = LAYER_FLOATING;
            this.logicalParent = parent;  // Preserve for events
            
            // Migrate to floating layer in layout manager
            if (layoutManager != null) {
                layoutManager.migrateToFloating(self(), anchor);
            }
            
            // Mark for re-layout with floating constraints
            requestLayoutUpdate();
        });
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
            this.renderingParent = null;
            
            // Migrate back to regular layout
            if (layoutManager != null) {
                layoutManager.migrateToRegular(self());
            }
            
            requestLayoutUpdate();
        });
    }
    
    /**
     * Set layer index for rendering order
     */
    public void setLayerIndex(int layer) {
        if (this.layerIndex != layer) {
            this.layerIndex = layer;
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
        Log.logMsg("[Renderable:" + getName() + "] addToChildGroup " + renderable.getName()
            + " groupId=" + groupId
        );
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
            if(members.remove(renderable)){
                if(state.getMembers().size() == 0 && state.getCallbacks().size() == 0){
                    it.remove();
                }
            }
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
    protected void registerChildGroupCallback(String groupId, GCE callbackEntry) {
        createChildGroup(groupId);
        GSE state = childGroups.get(groupId);
        state.getCallbacks().put(callbackEntry.getCallbackId(), callbackEntry);
    }

    protected void unregisterChildGroupCallback(String groupId, String callbackId){
        GSE state = childGroups.get(groupId);
        state.getCallbacks().remove(callbackId);
        if(state.getCallbacks().size() == 0 && state.getMembers().size() == 0){
            childGroups.remove(groupId);
        }
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

    private GCE getChildLayoutGroupCallback(String groupId, String callbackId){
        GSE groupState = childGroups.get(groupId);
        if(groupState == null){ return null; }
        return groupState.getCallbacks().get(callbackId);
    }

    public Map<String,GSE> getChildGroups(){
        return childGroups;
    }    
    
    Map<String,GSE> collectChildGroups(){
        Map<String,GSE> copy = new HashMap<>();
        for (Map.Entry<String,GSE> entry : childGroups.entrySet()) {
            GSE snapshot = createGroupStateEntry();
            snapshot.getMembers().addAll(entry.getValue().getMembers());
            snapshot.getCallbacks().putAll(entry.getValue().getCallbacks());
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
    
    /**
     * Clear all pending state
     */
    protected void clear() {
        childGroups.clear();
    }
    


    public void clearChildGroups() {
        clear();
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

    public GCE getLayoutGroupCallback(String groupId, String callbackId){
        if(isAttachedToLayoutManager()){
            return layoutManager.getLayoutGroupCallback(groupId, callbackId);
        }else{
            return getChildLayoutGroupCallback(groupId, callbackId);
        }
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
    public void registerGroupCallback(String groupId, GCE entry) {
        registerChildGroupCallback(groupId, entry);    // always
        if (isAttachedToLayoutManager()) {
            layoutManager.registerLayoutGroupCallback(groupId, entry);
        }
    }
    
    protected abstract GCE createGroupCallbackEntry(GCB groupCallback);
    
    public void registerGroupCallback(String groupId, GCB groupCallback) {
        registerGroupCallback(groupId,createGroupCallbackEntry(groupCallback));
    }

    public void unregisterLayoutGroupCallback(String groupId, String callbackId) {
        unregisterChildGroupCallback(groupId, callbackId);  // always
        if (isAttachedToLayoutManager()) {
            layoutManager.unregisterLayoutGroupCallback(groupId, callbackId);
        }
    }

    
    
    

    public static class GroupStateEntry<
        R extends Renderable<?,?,?,?,?,?,?,GCE,GSE,?,R>,
        GCE extends GroupCallbackEntry<?,?,GCE>,
        GSE extends GroupStateEntry<R,GCE,GSE>
    >{
        private List<R> members = new ArrayList<>();
        private HashMap<String, GCE> callbacks = new HashMap<>();

        public List<R> getMembers(){
            return members;
        }
        public Map<String,GCE> getCallbacks(){
            return callbacks;
        }
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
        return renderingParent != null ? renderingParent : parent;
    }

}
