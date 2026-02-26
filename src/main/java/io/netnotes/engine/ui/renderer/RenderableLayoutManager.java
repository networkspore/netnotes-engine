package io.netnotes.engine.ui.renderer;

import io.netnotes.engine.ui.FloatingLayerManager;
import io.netnotes.engine.ui.PooledRegion;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;

import io.netnotes.engine.ui.renderer.Renderable.GroupStateEntry;
import io.netnotes.engine.ui.renderer.layout.GroupCallbackEntry;
import io.netnotes.engine.ui.renderer.layout.GroupLayoutCallback;
import io.netnotes.engine.ui.renderer.layout.LayoutCallback;
import io.netnotes.engine.ui.renderer.layout.LayoutContext;
import io.netnotes.engine.ui.renderer.layout.LayoutData;
import io.netnotes.engine.ui.renderer.layout.LayoutGroup;
import io.netnotes.engine.ui.renderer.layout.LayoutNode;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.SerializedDebouncedExecutor;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * RenderableLayoutManager - MANDATORY manager for layout and visibility within a container
 * 
 * RESPONSIBILITIES:
 * - Calculate and apply positions/sizes for registered renderables
 * - Manage visibility resolution with global view (replaces Renderable's ArrayDeque)
 * - Topological sorting (parents before children)
 * - Debounced layout and visibility scheduling
 * - Group layout management with predicated callbacks
 * 
 * GROUP LAYOUT SEMANTICS:
 * - When any group member is marked dirty, all members are marked dirty
 * - Group callbacks execute once when the first member is encountered in sorted order
 * - Callbacks are filtered by predicates that can check group state
 * - Subsequent members retrieve pre-calculated layout data
 * - Group is cleaned up after the last member applies its layout
 * 
 * ARCHITECTURE:
 * - Every ContainerHandle MUST have a RenderableLayoutManager
 * - Renderables delegate visibility resolution to their manager
 * - Manager has complete view of scene graph - no circular reference issues
 * - Can use more efficient algorithms than per-node ArrayDeque approach
 * 
 * OWNERSHIP:
 * - One manager per ContainerHandle
 * - Manages only renderables within that container
 * - Does NOT manage container positions (separate system handles that)
 * 
 * @param <B> BatchBuilder type
 * @param <R> Renderable type
 * @param <P> SpatialPoint type
 * @param <S> SpatialRegion type
 * @param <LC> LayoutContext type
 * @param <LD> LayoutData type
 * @param <LCB> LayoutCallback type
 * @param <GCB> GroupLayoutCallback type
 * @param <L> LayoutNode type
 */
public abstract class RenderableLayoutManager<
    B extends BatchBuilder<S>,
    R extends Renderable<B,P,S,LC,LD,LCB,GCB,GCE,GSE,G,R>,
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    LC extends LayoutContext<B,R,P,S,LD,LCB,LC,L>,
    LD extends LayoutData<B,R,S,LD,?>,
    LCB extends LayoutCallback<B,R,P,S,LC,LD,LCB>,
    GCB extends GroupLayoutCallback<B,R,P,S,LD,LC,L,GCB>,
    GCE extends GroupCallbackEntry<G,GCB,GCE>,
    GSE extends GroupStateEntry<R,GCE,GSE>,
    G extends LayoutGroup<B,R,P,S,LD,LC,GCB,L,GCE,G>,
    L extends LayoutNode<B,R,P,S,LD,LC,LCB,GCB,G,L>
> {
    // Initial minimal delay
    public static final long DEFAULT_DEBOUNCE_MS = 3;
    protected final SerializedVirtualExecutor uiExecutor;

    // ===== SCHEDULING =====
    private long debounceDelayMs;
    private final SerializedDebouncedExecutor layoutDebouncer;

    // ===== ADAPTIVE DEBOUNCING =====
    private double avgExecutionTimeMs = 0.0;
    private volatile long currentDebounceMs = DEFAULT_DEBOUNCE_MS;
    
    // Configuration constants (can be made configurable if needed)
    private static final long MIN_DEBOUNCE_MS = 2;
    private static final long MAX_DEBOUNCE_MS = 100;
    private static final double EXECUTION_TIME_MULTIPLIER = 0.5;
    private static final long DEBOUNCE_PER_NODE_MICROS = 50;
    private static final double SMOOTHING_ALPHA = 0.3;
    
    // ===== REGISTRY =====
    protected final Map<R, L> renderableRegistry = new ConcurrentHashMap<>();
    protected final Map<R, L> floatingRegistry = new ConcurrentHashMap<>();

    protected final Map<String, G> groupRegistry = new ConcurrentHashMap<>();
    
    protected FloatingLayerManager<B,R,P,S,LC,LD,LCB> floatingLayer;
    private Consumer<R> focusRequester;
    private R pendingFocusRequest;
    
    // ===== DIRTY TRACKING =====
    protected Set<L> dirtyFloatingNodes = new LinkedHashSet<>();
    protected Set<L> dirtyLayoutNodes = new LinkedHashSet<>();
    protected final Set<L> pendingRequestNodes = new LinkedHashSet<>();
    
    private Consumer<Boolean> layoutStateListener; 

    private boolean batchingRequests = false;
    
    protected final String containerName;

    

    public RenderableLayoutManager(
        String containerName, 
        FloatingLayerManager<B,R,P,S,LC,LD,LCB> floatingLayer
    ) {
        this(containerName, floatingLayer,VirtualExecutors.getUiExecutor(), DEFAULT_DEBOUNCE_MS);
    }

    public RenderableLayoutManager(
        String containerName, 
        FloatingLayerManager<B,R,P,S,LC,LD,LCB> floatingLayer,
        SerializedVirtualExecutor uiExec,
        long debounceDelayMs
    ) {
        this.uiExecutor = uiExec;
        this.containerName = containerName;
        this.floatingLayer = floatingLayer;
        this.debounceDelayMs = debounceDelayMs;

        this.layoutDebouncer = new SerializedDebouncedExecutor(
                uiExec, 
                debounceDelayMs, 
                TimeUnit.MILLISECONDS
            );
    }

    public void setFocusRequester(Consumer<R> focusRequester) {
        this.focusRequester = focusRequester;
    }

    private void queueFocusRequest(R renderable) {
        if (renderable == null) {
            return;
        }
        pendingFocusRequest = renderable;
    }

    private void consumeFocusDesired(R renderable) {
        if (renderable != null && renderable.hasState(RenderableStates.STATE_FOCUS_DESIRED)) {
            renderable.getStateMachine().removeState(RenderableStates.STATE_FOCUS_DESIRED);
            queueFocusRequest(renderable);
        }
    }
    public void setLayoutStateListener(Consumer<Boolean> layoutStateListener){
        this.layoutStateListener = layoutStateListener;
    }

    private boolean isRegistered(R renderable) {
        return renderableRegistry.containsKey(renderable)
            || floatingRegistry.containsKey(renderable);
    }

    private void applyPendingFocusRequest() {
        if (pendingFocusRequest == null || focusRequester == null) {
            return;
        }
        R candidate = pendingFocusRequest;
        if (!isRegistered(candidate)) {
            pendingFocusRequest = null;
            return;
        }
        if (candidate.isFocusable()) {
            pendingFocusRequest = null;
            focusRequester.accept(candidate);
        }
    }

    // ===== ABSTRACT FACTORY METHODS =====
    
    protected abstract L createRenderableNode(R renderable);
    protected abstract LC createRenderableContext(L renderable);

    // ===== RENDERABLE REGISTRATION =====
    
    /**
     * Register a renderable with a layout callback
     * 
     * @param renderable The renderable to register
     * @param callback Callback to calculate layout (can be null for manual layout)
     */
    public void registerRenderable(R renderable, LCB callback) {
        registerRenderableInternal(renderable, callback);
    }

    private void registerRenderableInternal(R renderable, LCB callback){
        if (renderable.layoutManager != null) {
            renderable.layoutManager.unregister(renderable);
        }

        consumeFocusDesired(renderable);
        
        L node = createRenderableNode(renderable);
        node.setCallback(callback);
        renderableRegistry.put(renderable, node);
        
        buildParentChildRelations(node);
        markLayoutDirty(renderable);
        dirtyAffectedAncestors(node);
        
        Log.logMsg("[LayoutManager] Registered: " + renderable.getName());
    
        for (R child : renderable.getChildren()) {
            if (!renderableRegistry.containsKey(child)) {
                LCB childCallback = renderable.getChildCallback(child);
                registerRenderableInternal(child, childCallback);
            }
        }

        renderable.setLayoutManager(new ManagerHandle());


        Map<String, GSE> pendingGroups = renderable.collectChildGroups();
        if (!pendingGroups.isEmpty()) {
            registerPendingGroups(pendingGroups);
        }
    }

    private void registerPendingGroups(Map<String, GSE> pendingGroups){
        for(Map.Entry<String,GSE> mapEntry : pendingGroups.entrySet()){
            String groupId = mapEntry.getKey();
            GSE groupStateEntry = mapEntry.getValue();
            List<R> members = groupStateEntry.getMembers();
            Map<String,GCE> callbacks = groupStateEntry.getCallbacks();
        
            G group = groupRegistry.computeIfAbsent(groupId, id -> createEmptyGroup(id));

            for(R member : members){
                L node = renderableRegistry.get(member);
                if (node == null || node.getGroup() == group) continue;
                node.setGroup(group);
            }
            
            for(GCE callbackEntry : callbacks.values()){
                if (!group.hasCallback(callbackEntry.getCallbackId())) {
                    group.registerCallback(callbackEntry);
                }
            }

            for (L member : group.getMembers()) {
                markLayoutDirty(member.getRenderable());
            }
        }
    }

    private void dirtyAffectedAncestors(L node) {
        if (node.isFloating()) return;
        
        L current = node.getParent();
        while (current != null) {
            markLayoutDirty(current.getRenderable());
            
            if (!current.isSizedByChildren()) {
                break;
            }
            
            current = current.isFloating() ? null : current.getParent();
        }
    }

    /**
     * Unregister a renderable
     */
    public void unregisterRenderable(R renderable) {
        if(isCurrentThread()){
            unregisterRenderableInternal(renderable);
        }else{
            uiExecutor.executeFireAndForget(() -> unregisterRenderableInternal(renderable));
        }
    }

    private void unregisterRenderableInternal(R renderable) {
        L node = renderableRegistry.remove(renderable);
        if (node != null) {
            cleanupNode(node);
            Log.logMsg("[LayoutManager:" + containerName + "] Unregistered: " + renderable.getName());
        }
    }
        
    /**
     * Unregister a renderable tree (parent + all children)
     */
    public void unregisterRenderableTree(R renderable) {
        for (R child : renderable.getChildren()) {
            unregisterRenderableTree(child);
        }
        unregisterRenderable(renderable);
    }
    
    /**
     * Clean up a node's relationships
     */
    protected void cleanupNode(L node) {
        R renderable = node.getRenderable();
        if (pendingFocusRequest == renderable) {
            pendingFocusRequest = null;
        }
        
        // Group cleanup - setGroup(null) triggers group.removeMember(node)
        if (node.getGroup() != null) {
            G group = node.getGroup();
            node.setGroup(null);
            if (group.isEmpty() && !group.hasAnyCallbacks()) {
                groupRegistry.remove(group.getGroupId());
            }
        }
        
        // Recursive - prevents group membership leaks in subtree
        for (R child : new ArrayList<>(renderable.getChildren())) {
            L childNode = renderableRegistry.get(child);
            if (childNode != null) {
                renderableRegistry.remove(child);
                cleanupNode(childNode);
            }
        }
     
        dirtyLayoutNodes.remove(node);
        dirtyAffectedAncestors(node);
        L parent = node.getParent();
        if (parent != null) {
            parent.getChildren().remove(node);
        }

        renderable.removedFromLayout();
    }
    
    /**
     * Build parent-child relationships by walking up the scene graph
     */
    protected void buildParentChildRelations(L node) {
        R renderable = node.getRenderable();
        R parent = renderable.getParent();
        
        while (parent != null) {
            L parentNode = renderableRegistry.get(parent);
            if (parentNode != null) {
                // Found a registered parent, link them
                parentNode.addChild(node);
                break;
            }
            parent = parent.getParent();
        }
    }

    // ===== DIRTY MARKING =====
    
    /**
     * Mark a renderable as needing layout recalculation
     * If part of a group, marks all group members dirty
     */
    public void markLayoutDirty(R renderable) {
        L node = renderableRegistry.get(renderable);
        if (node != null) {
            G group = node.getGroup();
            if (group != null) {
                // Mark all group members dirty
                for (L member : group.getMembers()) {
                    dirtyLayoutNodes.add(member);
                }
            } else {
                dirtyLayoutNodes.add(node);
            }
            requestLayout();
        }
    }

    /**
     * Mark layout dirty and execute immediately, bypassing debounce.
     * Use for time-critical updates that can't wait.
     */
    public void markLayoutDirtyImmediate(R renderable) {
        L node = renderableRegistry.get(renderable);
        if (node != null) {
            G group = node.getGroup();
            if (group != null) {
                // Mark all group members dirty
                for (L member : group.getMembers()) {
                    dirtyLayoutNodes.add(member);
                }
            } else {
                dirtyLayoutNodes.add(node);
            }
            
            // Execute immediately - bypasses debouncing
            layoutDebouncer.executeNow(this::performUpdate);
        }
    }

    /**
     * Execute pending layout immediately if any dirty nodes exist.
     * Useful for forcing layout before critical operations.
     */
    public CompletableFuture<Void> flushLayout() {
        if (!dirtyLayoutNodes.isEmpty() || !dirtyFloatingNodes.isEmpty()) {
            return layoutDebouncer.executeNow(this::performUpdate);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Mark a renderable tree as layout dirty (parent + all children)
     */
    public void markLayoutDirtyTree(R renderable) {
        L node = renderableRegistry.get(renderable);
        if (node != null) {
            markNodeLayoutDirtyRecursive(node);
            requestLayout();
        }
    }
    
    protected void markNodeLayoutDirtyRecursive(L node) {
        dirtyLayoutNodes.add(node);
        for (L child : node.getChildren()) {
            markNodeLayoutDirtyRecursive(child);
        }
    }
    
    public void markVisibilityDirty(R renderable) {
        markLayoutDirty(renderable);
    }

    protected void requestLayout() {
        layoutDebouncer.submit(
            this::performUpdate, 
            debounceDelayMs, 
            TimeUnit.MILLISECONDS
        );
    }

    public void setDebounceDelay(long delayMs) {
        if (delayMs < 0) {
            throw new IllegalArgumentException("Delay must be non-negative");
        }
        
        this.debounceDelayMs = delayMs;
    }

    // ===== INCREMENTAL LAYOUT + VISIBILITY =====

  
    private void performUpdate() {
        if( layoutStateListener != null){
            layoutStateListener.accept(true);
        }
        try {
            performUpdateInternal();
        } finally {
            if(layoutStateListener != null){
                layoutStateListener.accept(false);
            }
        }
    } 


    protected void performUpdateInternal() {
        long startTime = System.nanoTime();
        
        Set<L> currentLayoutDirty = dirtyLayoutNodes;
        Set<L> currentFloatingDirty = dirtyFloatingNodes;

        Log.logMsg("[LayoutManager] currentLayoutDirt: " + currentLayoutDirty.size());

        dirtyLayoutNodes = new LinkedHashSet<>();
        dirtyFloatingNodes = new LinkedHashSet<>();

        Set<L> allDirtyNodes = new LinkedHashSet<>();
        allDirtyNodes.addAll(currentLayoutDirty);
        allDirtyNodes.addAll(currentFloatingDirty);

        int allDirtyNodesSize = allDirtyNodes.size();
         Log.logMsg("[LayoutManager] allDirtyNodesSize: " + currentLayoutDirty.size());
        // Now process the snapshot
        if (!allDirtyNodes.isEmpty()) {
            try {
                performUnifiedLayoutUpdate(allDirtyNodes);
            } catch (Exception e) {
                // Dirty nodes were already swapped — re-add them so next run can retry
                dirtyLayoutNodes.addAll(currentLayoutDirty);
                Log.logError("[LayoutManager:" + containerName + "] Layout update failed", e);
                throw e; // still propagate so we know it happened
            }
        }
                
        applyPendingFocusRequest();
    
        long endTime = System.nanoTime();
        double updateTimeMs = (endTime - startTime) / 1_000_000.0;
        
        // Calculate and apply adaptive debounce for next layout
        long nextDebounce = calculateAdaptiveDebounce(updateTimeMs, allDirtyNodesSize);
        setDebounceDelay(nextDebounce);
        
        Log.logMsg(String.format(
            "[LayoutManager:%s] Update %.2fms (nodes: %d, nextDebounce: %dms, avgExec: %.2fms)",
            containerName, updateTimeMs, allDirtyNodesSize, nextDebounce, avgExecutionTimeMs
        ));
    }

    protected void performUnifiedLayoutUpdate(Set<L> dirtyNodes) {
        // Reset all groups
        for (G group : groupRegistry.values()) {
            group.reset();
        }
        
        // Sort: regular by depth, then floating by depth
        List<L> sorted = unifiedTopologicalSort(new ArrayList<>(dirtyNodes));
        
        // Calculate and apply in sorted order
        for (L node : sorted) {
            G group = node.getGroup();
            boolean useGroup = group != null && group.hasAnyCallbacks();

            if (useGroup && group.getPassCounter() == 0) {
                // First group member: execute callbacks
                LC[] contexts = createContextArray(group.getMembers().size());
                for (int i = 0; i < group.getMembers().size(); i++) {
                    L member = group.getMembers().get(i);
                    contexts[i] = createRenderableContext(member);
                    contexts[i].initialize(member);
                }
                group.executeCallbacks(contexts);
                recycleLayoutContexts(contexts);
                
            } else if (!useGroup) {
                // Non-grouped: calculate individually
                LC context = createRenderableContext(node);
                context.initialize(node);
                node.calculate(context);
                apply(node);
                recycleLayoutContext(context);
            }

            if (useGroup) {
                node.injectToCalculatedData();
                apply(node);
                group.incrementPass();
            }
        }
    }

    protected List<L> unifiedTopologicalSort(List<L> nodes) {
        List<L> regular = new ArrayList<>();
        List<L> floating = new ArrayList<>();
        
        // Partition by floating status
        for (L node : nodes) {
            if (node.isFloating()) {
                floating.add(node);
            } else {
                regular.add(node);
            }
        }
        
        // Sort each partition by depth (parents before children)
        regular.sort(Comparator.comparingInt(L::getDepth));
        floating.sort(Comparator.comparingInt(L::getDepth));
        
        // Combine: regular first (potential anchors), then floating (dependents)
        List<L> result = new ArrayList<>(regular.size() + floating.size());
        result.addAll(regular);
        result.addAll(floating);
        
        return result;
    }


    protected abstract LC[] createContextArray(int size);

    /**
     * Apply calculated layout data to renderable
     */
    private void apply(L node) {
        LD calculatedLayout = node.getCalculatedLayout();
        if (calculatedLayout == null) return;

        R renderable = node.getRenderable();
        
        L parent = node.getParent();
        if (calculatedLayout.hasRegion() && parent != null) {
            S parentRegion = parent.getRenderable().getRegion();
            calculatedLayout.getSpatialRegion().setParentAbsolutePosition(
                parentRegion.getAbsolutePosition()
            );
        }

        renderable.applyLayoutData(calculatedLayout);

        // Layout changed spatial or visibility state — register damage so
        // toBatch has something to render. Without this, applyLayoutData
        // sets the region but no render ever fires.
        if (calculatedLayout.hasRegion() || calculatedLayout.hasStateChanges()) {
            renderable.invalidate();
        }

        node.clear();
        recycleLayoutData(calculatedLayout);
    }

    protected abstract void recycleLayoutData(LD layoutData);
    protected abstract void recycleLayoutContext(LC context);
    protected abstract void recycleLayoutContexts(LC[] context);
    // ===== TOPOLOGICAL SORTING =====
    
    /**
     * Sort nodes by depth in scene graph (parents before children)
    
    protected List<L> topologicalSort(List<L> nodes) {
        nodes.sort(Comparator.comparingInt(L::getDepth));
        return nodes;
    } */

    
    // ===== FLOATING REGISTRATION =====
    
    /**
     * Register a floating renderable
     * 
     * @param renderable The floating renderable
     * @param callback Layout callback
     * @param anchor Anchor element for positioning (can be null)
     */
    public void registerFloating(R renderable, LCB callback, R anchor) {
        consumeFocusDesired(renderable);
        L node = createRenderableNode(renderable);
        node.setCallback(callback);
        node.setPositionAnchor(anchor);
        floatingRegistry.put(renderable, node);
        floatingLayer.add(renderable);
        renderable.setLayoutManager(new ManagerHandle());
        markFloatingDirty(renderable);
        
        // Damage the rendering parent so it recomposites with the new floating layer
        R renderingParent = renderable.getRenderingParent();
        if (renderingParent != null) {
            renderingParent.invalidate();
        }
    }

    /**
     * Migrate renderable from regular to floating
     */
    public void migrateToFloating(R renderable, R anchor) {
        L node = renderableRegistry.remove(renderable);
        if (node != null) {
            cleanupNode(node);
            floatingRegistry.put(renderable, node);
            floatingLayer.add(renderable);
            node.setPositionAnchor(anchor);
            markFloatingDirty(renderable);
        }
    }

    /**
     * Migrate renderable from floating to regular
     */
    public void migrateToRegular(R renderable) {
        L node = floatingRegistry.remove(renderable);
        if (node != null) {
            damageRenderingParentAtFloatingRegion(renderable);
            dirtyFloatingNodes.remove(node);
            floatingLayer.remove(renderable);
            renderableRegistry.put(renderable, node);
            buildParentChildRelations(node);
            markLayoutDirty(renderable);
        }
    }
   
    // ===== GROUP MANAGEMENT =====

    protected abstract G createEmptyGroup(String groupId);

    /**
     * Create a new layout group
     */
    public void createGroup(String groupId) {
        if (isCurrentThread()) {
            createGroupIfAbsent(groupId);
        }else{
            uiExecutor.executeFireAndForget(() -> createGroupIfAbsent(groupId));
        }
    }

    private boolean isCurrentThread(){
        return uiExecutor.isCurrentThread();
    }

    private G createGroupIfAbsent(String groupId){
        return groupRegistry.computeIfAbsent(groupId, id -> createEmptyGroup(id));
    }

    /**
     * Add a renderable to a group
     */
    public void addToGroup(R renderable, String groupId) {
        if (isCurrentThread()) {
            addToGroupInternal(renderable, groupId);
        }else{
            uiExecutor.executeFireAndForget(() -> addToGroupInternal(renderable, groupId));
        }
    }
    
    private void addToGroupInternal(R renderable, String groupId) {

        L node = renderableRegistry.get(renderable);
        L floatingNode = node == null ? floatingRegistry.get(renderable) : null;
        if (node == null && floatingNode == null) {
            Log.logError("[RenderableLayoutManager.addToGroup] r:" + renderable.getName() + " not in registry (yet?)");
            return;
        }

        node = node == null ? floatingNode : node;
        Log.logMsg("[RenderableLayoutManager] addToGroupInternal r:" + node.getName());
        
        G group = createGroupIfAbsent(groupId);
        node.setGroup(group);
        for (L member : group.getMembers()) {
            markLayoutDirty(member.getRenderable());
        }

        dirtyAffectedAncestors(node);
    
    }

    public String getRenderableGroupId(R renderable){
        L node = renderableRegistry.get(renderable);
        if(node == null){ return null; }

        G group = node.getGroup();
        if(group == null ) { return null; }

        return group.getGroupId();
    }
    
    /**
     * Remove a renderable from its group
     */
    public void removeFromGroup(R renderable) {
        if (isCurrentThread()) {
            removeFromGroupInternal(renderable);
        }else{
            uiExecutor.executeFireAndForget(() -> removeFromGroupInternal(renderable));
        }
    }
    
    private void removeFromGroupInternal(R renderable) {
        L node = renderableRegistry.get(renderable);
        if (node == null) {
            node = floatingRegistry.get(renderable);
        }
        
        if (node != null && node.getGroup() != null) {
            G group = node.getGroup();
            
            node.setGroup(null);
            
            if (group.isEmpty()) {
                groupRegistry.remove(group.getGroupId());
            }
            
            markLayoutDirty(renderable);
            dirtyAffectedAncestors(node); 
        }
    }

    /**
     * Destroy a group and remove all members
     */
    public void destroyGroup(String groupId) {
        if(isCurrentThread()){
            destroyGroupInternal(groupId);
        }else{
            uiExecutor.executeFireAndForget(() -> destroyGroupInternal(groupId));
        }
    }
    
    private void destroyGroupInternal(String groupId) {
        G group = groupRegistry.remove(groupId);
        if (group != null) {
            List<L> members = group.getMembers();
            for (L member : members) {
                member.setGroup(null);
                markLayoutDirty(member.getRenderable());
            }
            group.cleanup();
        }
    }

    /**
     * Register a group callback with predicate for a specific group
     * The callback will execute when any member of the group is calculated
     * 
     * @param groupId The group to register callback for
     * @param callbackId Unique identifier for this callback
     * @param predicate Condition that determines if callback should execute
     * @param callback The group callback to execute
     */
    public void registerGroupCallback(String groupId,GCE entry) {
        if(isCurrentThread()){
            registerGroupCallbackInternal(groupId, entry);
        }else{
            uiExecutor.executeFireAndForget(() -> 
                registerGroupCallbackInternal(groupId, entry)
            );
        }
    }
    
    private void registerGroupCallbackInternal(
            String groupId,
            GCE entry
    ) {
        G group = groupRegistry.get(groupId);
        
        if(group == null){
            group = createGroupIfAbsent(groupId);
        }
  
        group.registerCallback(entry);
        // Mark all members dirty to trigger recalculation
        for (L member : group.getMembers()) {
            markLayoutDirty(member.getRenderable());
        }
        
    }

    public GCE getLayoutGroupCallback(String groupId, String callbackId){
        G group = groupRegistry.get(groupId);
        return group.getCallback(callbackId);
    }

    /**
     * Unregister a group callback from a group
     */
    public void unregisterGroupCallback(String groupId, String callbackId) {
        if(isCurrentThread()){
            unregisterGroupCallbackInternal(groupId, callbackId);
        }else{
            uiExecutor.executeFireAndForget(() -> 
                unregisterGroupCallbackInternal(groupId, callbackId)
            );
        }
    }
    
    private void unregisterGroupCallbackInternal(String groupId, String callbackId) {
        G group = groupRegistry.get(groupId);
        
        if (group != null) {
            group.unregisterCallback(callbackId);
        }
    }

    /**
     * Unregister floating renderable
     */
    public void unregisterFloating(R renderable) {
        if(isCurrentThread()){
            unregisterFloatingInternal(renderable);
        }else{
            uiExecutor.executeFireAndForget(()->{
                unregisterFloatingInternal(renderable);
            });
        }
    }

    private void damageRenderingParentAtFloatingRegion(R renderable) {
        R renderingParent = renderable.getRenderingParent();
        if (renderingParent == null) return;
        
        S absoluteRegion = renderable.getEffectiveAbsoluteRegion();
        if (absoluteRegion == null) return;
        
        // Convert absolute region to rendering parent's local space
        S parentAbsolute = renderingParent.getAbsoluteRegion();
        try (PooledRegion<S> pooled = renderable.getRegionPool().obtainPooled()) {
            S localDamage = pooled.get();
            localDamage.copyFrom(absoluteRegion);
            localDamage.subtractPosition(parentAbsolute.getPosition());
            renderingParent.invalidate(localDamage);
        } finally {
            renderable.getRegionPool().recycle(absoluteRegion);
            renderable.getRegionPool().recycle(parentAbsolute);
        }
    }

    protected void unregisterFloatingInternal(R renderable){
        L node = floatingRegistry.remove(renderable);
        if (node != null) {
            damageRenderingParentAtFloatingRegion(renderable);
            dirtyFloatingNodes.remove(node);
            floatingLayer.remove(renderable);
            if (pendingFocusRequest == renderable) {
                pendingFocusRequest = null;
            }
            renderable.removedFromLayout();
            Log.logMsg("[LayoutManager:" + containerName + "] Unregistered floating: " + renderable.getName());
        }
    }

    /**
     * Mark floating renderable as dirty
     */
    public void markFloatingDirty(R renderable) {
        L node = floatingRegistry.get(renderable);
        if (node != null) {
            dirtyFloatingNodes.add(node);
            requestLayout();
        }
    }
   
    // ===== DIAGNOSTICS =====
    
    /**
     * Get diagnostic information about the layout manager state
     */
    public String getDiagnostics() {
        return String.format(
            "LayoutManager[container=%s, renderables=%d, groups=%d, dirtyLayout=%d, " +
            "debounce=%dms, avgExec=%.2fms, pending=%s, executing=%s]",
            containerName,
            renderableRegistry.size(),
            groupRegistry.size(),
            dirtyLayoutNodes.size(),
            currentDebounceMs,
            avgExecutionTimeMs,
            layoutDebouncer.hasPending(),
            layoutDebouncer.isExecuting()
        );
    }

    /**
     * Handle implementation that delegates to this manager
     * Created once per registration, allows renderables to call manager methods
     */
    private class ManagerHandle implements RenderableLayoutManagerHandle<R, LCB,G,GCE, GCB> {

        @Override
        public void markLayoutDirtyImmediate(R renderable) {
            RenderableLayoutManager.this.markLayoutDirtyImmediate(renderable);
        }
        
        @Override
        public CompletableFuture<Void> flushLayout() {
            return RenderableLayoutManager.this.flushLayout();
        }

        @Override
        public void migrateToFloating(R renderable, R anchor) {
            RenderableLayoutManager.this.migrateToFloating(renderable, anchor);
        }
        
        @Override
        public void migrateToRegular(R renderable) {
            RenderableLayoutManager.this.migrateToRegular(renderable);
        }

        @Override
        public void markLayoutDirty(R renderable) {
            RenderableLayoutManager.this.markLayoutDirty(renderable);
        }
        
        @Override
        public void markVisibilityDirty(R renderable) {
            RenderableLayoutManager.this.markVisibilityDirty(renderable);
        }
        
        @Override
        public void markRequestPending(R renderable) {
            RenderableLayoutManager.this.markRequestPending(renderable);
        }
        
        @Override
        public void registerChild(R child, LCB callback) {
            RenderableLayoutManager.this.registerRenderable(child, callback);
        }
        
        @Override
        public void unregister(R renderable) {
            RenderableLayoutManager.this.unregisterRenderable(renderable);
        }
        
        @Override
        public void requestFocus(R renderable) {
            if (renderable == null) {
                return;
            }
            consumeFocusDesired(renderable);
            if (focusRequester != null && renderable.isFocusable()) {
                if (pendingFocusRequest == renderable) {
                    pendingFocusRequest = null;
                }
                focusRequester.accept(renderable);
            } else {
                queueFocusRequest(renderable);
            }
        }
        
        @Override
        public void beginBatch() {
            RenderableLayoutManager.this.beginRequestBatch();
        }
        
        @Override
        public void endBatch() {
            RenderableLayoutManager.this.endRequestBatch();
        }

        @Override
        public void createLayoutGroup(String groupId) {
            RenderableLayoutManager.this.createGroup(groupId);
        }

        @Override
        public void addToLayoutGroup(R renderable, String groupId) {
            RenderableLayoutManager.this.addToGroup(renderable, groupId);
        }

        @Override
        public String getLayoutGroupIdByRenderable(R renderable){
            return RenderableLayoutManager.this.getRenderableGroupId(renderable);
        }

        @Override
        public void removeLayoutGroupMember(R renderable) {
            RenderableLayoutManager.this.removeFromGroup(renderable);
        }

        @Override
        public void destroyLayoutGroup(String groupId) {
            RenderableLayoutManager.this.destroyGroup(groupId);
        }

        @Override
        public void registerLayoutGroupCallback(String groupId, GCE entry) {
            RenderableLayoutManager.this.registerGroupCallback(groupId, entry);
        }

        @Override
        public GCE getLayoutGroupCallback(String groupId, String callbackId){
            return RenderableLayoutManager.this.getLayoutGroupCallback(groupId, callbackId);
        }

        @Override
        public void unregisterLayoutGroupCallback(String groupId, String callbackId) {
            RenderableLayoutManager.this.unregisterGroupCallback(groupId, callbackId);
        }
    }

    /**
     * Shutdown the manager
     */
    public void shutdown() {
        renderableRegistry.clear();
        dirtyLayoutNodes.clear();
        groupRegistry.clear();
    }

    // ===== BATCHING API =====
    
    public void beginRequestBatch() {
        batchingRequests = true;
    }
    
    public void endRequestBatch() {
        batchingRequests = false;
        if (!pendingRequestNodes.isEmpty()) {
            requestLayout();
        }
    }
    
    public void batchRequests(Runnable requests) {
        beginRequestBatch();
        try {
            requests.run();
        } finally {
            endRequestBatch();
        }
    }
    
    protected void markRequestPending(R renderable) {
        L node = renderableRegistry.get(renderable);
        if (node != null) {
            pendingRequestNodes.add(node);
            dirtyAffectedAncestors(node); 
            if (!batchingRequests) {
                requestLayout();
            }
        }
    }

    private long calculateAdaptiveDebounce(double executionTimeMs, int dirtyNodeCount) {
        // Update exponential moving average of execution time
        if (avgExecutionTimeMs == 0.0) {
            avgExecutionTimeMs = executionTimeMs;
        } else {
            avgExecutionTimeMs = (SMOOTHING_ALPHA * executionTimeMs) 
                               + ((1.0 - SMOOTHING_ALPHA) * avgExecutionTimeMs);
        }
        
        // Base debounce from execution time (give breathing room proportional to work done)
        long executionBasedMs = (long) (avgExecutionTimeMs * EXECUTION_TIME_MULTIPLIER);
        
        // Additional debounce based on complexity (more nodes = more likely to have more updates)
        long complexityBasedMs = (dirtyNodeCount * DEBOUNCE_PER_NODE_MICROS) / 1000;
        
        // Combine: minimum + execution-based + complexity-based
        long adaptiveDelay = MIN_DEBOUNCE_MS + executionBasedMs + complexityBasedMs;
        
        // Clamp to reasonable bounds
        adaptiveDelay = Math.max(MIN_DEBOUNCE_MS, 
                                Math.min(MAX_DEBOUNCE_MS, adaptiveDelay));
        
        currentDebounceMs = adaptiveDelay;
    
        return adaptiveDelay;
    }
}
