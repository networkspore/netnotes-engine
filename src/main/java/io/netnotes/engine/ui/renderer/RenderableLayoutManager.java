package io.netnotes.engine.ui.renderer;

import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.renderer.Renderable.GroupStateEntry;
import io.netnotes.engine.ui.renderer.layout.GroupLayoutCallback;
import io.netnotes.engine.ui.renderer.layout.LayoutCallback;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.LoggingHelpers.LogLevel;
import io.netnotes.engine.virtualExecutors.SerializedDebouncedExecutor;
import io.netnotes.engine.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.engine.virtualExecutors.VirtualExecutors;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * RenderableLayoutManager - Single-pass layout manager.
 *
 * PASS MODEL:
 * One depth-sorted traversal per pass. Parents before children. Every dirty
 * node is visited exactly once. Each node is committed (applyNode) exactly
 * once. No phases, no re-entry, no epoch resets.
 *
 * TWO-CALLBACK MODEL:
 * Each node has up to two callbacks:
 *
 *   contentCallback — fires on-demand bottom-up when getMeasuredSize() is
 *     called on a content-sized node. Gated by node.contentMeasured so it
 *     fires at most once per pass. The contentCallback only populates
 *     calculatedLayout — it never triggers apply.
 *
 *   layoutCallback  — fires top-down during the normal pass when the manager
 *     reaches the node and calculatedLayout is still null. Handles positioning
 *     and fill-axis sizing given the committed parent space.
 *
 * GROUP MODEL:
 * Groups are owned by a parent node. The manager fires owned groups
 * immediately after committing the owner, giving group callbacks valid parent
 * geometry. Group content callbacks fire on-demand via getMeasuredSize(),
 * same as individual node content callbacks.
 *
 * VISIBILITY FLIP:
 * If applyNode detects a node just became effectively visible, its children
 * are queued for a follow-up pass — they have never been laid out while
 * visible. The node itself is committed. The follow-up pass runs before
 * ContainerHandle renders, triggered by requestLayout() writing into the
 * next-pass dirty set which is non-empty when layoutStateListener(false) fires.
 *
 * MID-PASS MUTATIONS:
 * performUpdateInternal swaps dirty sets at pass start. Any markLayoutDirty()
 * during the pass writes into the next-pass set. Add/remove child, visibility
 * change, and similar mutations are all safe by this construction.
 */
public abstract class RenderableLayoutManager<
    B extends BatchBuilder<S>,
    R extends Renderable<B,P,S,L,LC,LD,LCB,GCB,GSE,G,R>,
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    LC extends LayoutContext<B,R,P,S,LD,LCB,LC,L>,
    LD extends LayoutData<B,R,S,LD,?>,
    LCB extends LayoutCallback<B,R,P,S,LC,LD,LCB>,
    GCB extends GroupLayoutCallback<B,R,P,S,LD,LC,L,GCB>,
    GSE extends GroupStateEntry<R,GCB,GSE>,
    G extends LayoutGroup<B,R,P,S,LD,LC,GCB,L,G>,
    L extends LayoutNode<B,R,P,S,LD,LC,LCB,GCB,G,L>
> {

    // ── Diagnostics ───────────────────────────────────────────────────────────

    public enum DiagnosticMode {
        OFF, FAILURES, SUMMARY, TRACE;
        public boolean includes(DiagnosticMode minimum) {
            return minimum == null || ordinal() >= minimum.ordinal();
        }
    }


    private static final LogLevel LOG_LEVEL                    = LogLevel.GENERAL;
    private static final LogLevel DIAGNOSTIC_LOG_LEVEL         = LogLevel.IMPORTANT;
    private static final LogLevel ROUTINE_DIAGNOSTIC_LOG_LEVEL = LogLevel.GENERAL;
    private static final int      DIAGNOSTIC_NODE_SAMPLE_SIZE  = 4;
    private static final long     DIAGNOSTIC_LOG_THROTTLE_MS   = 250;

    public static final long DEFAULT_DEBOUNCE_MS = 2;
    private static final long[] LAYOUT_DEBOUNCE_STEPS_MS = new long[] {2L, 4L, 8L};
    private static final long LAYOUT_DEBOUNCE_MAX_WAIT_MS = 8L;
    private static final int LAYOUT_DEBOUNCE_SUPERSEDES_PER_STEP = 3;

    // ── Infrastructure ────────────────────────────────────────────────────────

    protected final SerializedVirtualExecutor uiExecutor;
    protected final String containerName;

    private final SerializedDebouncedExecutor layoutDebouncer;
    private volatile long minDebounceMs = DEFAULT_DEBOUNCE_MS;
    private volatile long currentDebounceMs = DEFAULT_DEBOUNCE_MS;

    // ── Registries ────────────────────────────────────────────────────────────

    protected final Map<R, L> renderableRegistry = new ConcurrentHashMap<>();
    protected final Map<R, L> floatingRegistry   = new ConcurrentHashMap<>();
    protected final Map<String, G> groupRegistry = new ConcurrentHashMap<>();

    protected FloatingLayerManager<B,R,P,S,LC,LD,LCB> floatingLayer;

    // ── Dirty tracking ────────────────────────────────────────────────────────
    private volatile boolean deferredLayoutRequested = false;

    protected Set<L> dirtyLayoutNodes   = new LinkedHashSet<>();
    protected Set<L> dirtyFloatingNodes = new LinkedHashSet<>();
    // Nodes selected for the active pass that have not yet been committed.
    private Set<L> currentPassNodes = null;
    // Mutable traversal for the active pass; supports mid-pass subtree injection.
    private List<L> currentPassTraversal = null;
    // Index of the next node to process in currentPassTraversal.
    private int currentPassCursor = 0;

    // ── Runtime state ─────────────────────────────────────────────────────────

    private volatile boolean batchingRequests = false;
    private volatile boolean layoutScheduled = false;


    /**
     * True while a layout pass is executing. Read by Renderable.invalidate()
     * to suppress render requests — damage still accumulates, render fires
     * once from layoutStateListener(false) when the pass completes.
     */
    private boolean layoutExecuting = false;
    private final Deque<Runnable> deferredInvalidations = new ArrayDeque<>();
    private final Deque<Runnable> deferredTreeMutations = new ArrayDeque<>();
    private final List<L> pendingInjections = new ArrayList<>();

    private final Set<R> committingNodes     = new LinkedHashSet<>();
    private Consumer<Boolean> layoutStateListener = null;
   // private Consumer<Set<L>>  onAfterLayoutPass = null;
    private Consumer<R>       focusRequester      = null;
    private R                 pendingFocusRequest  = null;
    private Consumer<R>       renderRequester      = null;
    private boolean           renderRequested      = false;
    private R                 renderRequestedBy    = null;

    private volatile DiagnosticMode diagnosticMode = DiagnosticMode.TRACE;
    private final Map<String, Long>  diagnosticLogTimes = new HashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public RenderableLayoutManager(
        String containerName,
        FloatingLayerManager<B,R,P,S,LC,LD,LCB> floatingLayer
    ) {
        this(containerName, floatingLayer, VirtualExecutors.getUiExecutor(), DEFAULT_DEBOUNCE_MS);
    }

    public RenderableLayoutManager(
        String containerName,
        FloatingLayerManager<B,R,P,S,LC,LD,LCB> floatingLayer,
        SerializedVirtualExecutor uiExec,
        long debounceDelayMs
    ) {
        this.uiExecutor      = uiExec;
        this.containerName   = containerName;
        this.floatingLayer   = floatingLayer;
        this.minDebounceMs = debounceDelayMs > 0 ? debounceDelayMs : DEFAULT_DEBOUNCE_MS;
        this.currentDebounceMs = this.minDebounceMs;
        this.layoutDebouncer = new SerializedDebouncedExecutor(
            uiExec,
            this.minDebounceMs,
            TimeUnit.MILLISECONDS,
            SerializedDebouncedExecutor.DebounceStrategy.STEPPED_TRAILING,
            LAYOUT_DEBOUNCE_STEPS_MS,
            LAYOUT_DEBOUNCE_MAX_WAIT_MS,
            LAYOUT_DEBOUNCE_SUPERSEDES_PER_STEP,
            null
        );
    }

    // ── Abstract factories ────────────────────────────────────────────────────

    protected abstract L    createRenderableNode(R renderable);
    protected abstract LC   createRenderableContext(L node);
    protected abstract LC[] createContextArray(int size);
    protected abstract G    createEmptyGroup(String groupId);
    protected abstract void recycleLayoutContext(LC context);
    protected abstract void recycleLayoutContexts(LC[] contexts);
    protected abstract void recycleLayoutData(LD layoutData);

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    /**
     * Register a renderable with separate content and layout callbacks.
     * Either callback may be null.
     *
     * @param renderable      the renderable to register
     * @param contentCallback fires on-demand for content measurement, or null
     * @param layoutCallback  fires top-down during the pass, or null
     */
    public void registerRenderable(R renderable, LCB layoutCallback) {
        uiExecutor.runRentrant(() -> registerRenderableInternal(renderable, layoutCallback));
    }

    private void registerRenderableInternal( R renderable, LCB layoutCallback ) {
         markLayoutDirtySubtree(renderable);

        if (renderable.layoutManager != null) {
            renderable.layoutManager.unregister(renderable);
        }
        consumeFocusDesired(renderable);

        L node = createRenderableNode(renderable);
        node.setLayoutCallback(layoutCallback);
        renderableRegistry.put(renderable, node);
        buildParentChildRelations(node);

        for (R child : renderable.getChildren()) {
            if (!renderableRegistry.containsKey(child)) {
                registerRenderableInternal(
                    child, 
                    renderable.getChildLayoutCallback(child)
                );
            }
        }

        renderable.setLayoutManager(new ManagerHandle());
        renderable.advanceRenderPhase(RenderPhase.COLLECTING);

        try {
            Map<String, GSE> pendingGroups = renderable.collectChildGroups();
            if (!pendingGroups.isEmpty()) {
                registerPendingGroups(node, pendingGroups);
            }
        } catch (Exception e) {
            Log.logError("[LayoutManager] collectChildGroups failed: " + renderable.getName(), e);
        }

        Log.logMsg("[LayoutManager] Registered: " + renderable.getName(), LOG_LEVEL);
    }

    private void registerPendingGroups(L ownerNode, Map<String, GSE> pendingGroups) {
        for (Map.Entry<String, GSE> entry : pendingGroups.entrySet()) {
            String groupId    = entry.getKey();
            GSE    groupState = entry.getValue();

            G group = groupRegistry.computeIfAbsent(groupId, id -> createEmptyGroup(id));
            L existingOwner = group.getOwner();
            if (existingOwner == null) {
                ownerNode.addOwnedGroup(group);
            } else if (existingOwner != ownerNode) {
                logFailureDiagnosticThrottled(
                    "group-owner-conflict-pending:" + groupId,
                    () -> String.format(
                        "[LayoutManager:%s] pending group '%s' owner conflict: existing=%s attempted=%s",
                        containerName, groupId, existingOwner.getName(), ownerNode.getName()));
                continue;
            }

            for (R member : groupState.getMembers()) {
                L memberNode = renderableRegistry.get(member);
                if (memberNode == null) continue;

                L memberParent = memberNode.getParent();
                if (memberParent != ownerNode) {
                    String memberParentName = memberParent != null ? memberParent.getName() : "<none>";
                    logFailureDiagnosticThrottled(
                        "group-owner-member-parent-mismatch-pending:" + groupId + ":" + memberNode.getName(),
                        () -> String.format(
                            "[LayoutManager:%s] skipping member %s for group '%s': owner=%s memberParent=%s",
                            containerName, memberNode.getName(), groupId, ownerNode.getName(), memberParentName));
                    continue;
                }

                if (memberNode.getMemberGroup() != group) {
                    memberNode.setMemberGroup(group);
                }
            }

            // The group's layout callback comes from the groupState entry
            GCB layoutCallback = groupState.getLayoutCallback();
            if (layoutCallback != null) {
                group.setLayoutCallback(layoutCallback);
            }

            for (L member : group.getMembers()) {
                markLayoutDirty(member.getRenderable());
            }
        }
    }

    public void unregisterRenderable(R renderable) {
       uiExecutor.runRentrant(() -> unregisterRenderableInternal(renderable));
    }

    private void unregisterRenderableInternal(R renderable) {
        L node = renderableRegistry.remove(renderable);
        if (node != null) {
            cleanupNode(node);
            renderable.advanceRenderPhase(RenderPhase.DETACHED);
            renderable.removedFromLayout();
            Log.logMsg("[LayoutManager] Unregistered: " + renderable.getName(), LOG_LEVEL);
        }
    }

    public void unregisterRenderableTree(R renderable) {
        for (R child : renderable.getChildren()) unregisterRenderableTree(child);
        unregisterRenderable(renderable);
    }

    protected void cleanupNode(L node) {
        R renderable = node.getRenderable();
        if (pendingFocusRequest == renderable) pendingFocusRequest = null;
        if (renderRequestedBy == renderable) renderRequestedBy = null;

        G memberGroup = node.getMemberGroup();
        if (memberGroup != null) {
            node.setMemberGroup(null);
            if (memberGroup.isEmpty() && !memberGroup.hasLayoutCallback()) {
                groupRegistry.remove(memberGroup.getGroupId());
            }
        }

        for (G owned : new ArrayList<>(node.getOwnedGroups())) {
            node.removeOwnedGroup(owned);
            owned.cleanup();
            groupRegistry.remove(owned.getGroupId());
        }

        for (R child : new ArrayList<>(renderable.getChildren())) {
            L childNode = renderableRegistry.remove(child);
            if (childNode != null) {
                cleanupNode(childNode);
                // Mirror what unregisterRenderableInternal() does for the top-level node.
                // Without this, descendants are stripped from the registry silently —
                // their clearLayoutManager(), notifyRemovedFromLayout, onRemovedFromLayout(),
                // and damage recycling are never called.
                child.advanceRenderPhase(RenderPhase.DETACHED);
                child.removedFromLayout();
            }
        }

        dirtyLayoutNodes.remove(node);
        L parent = node.getParent();
        if (parent != null) {
            parent.removeChild(node);
            dirtyAffectedAncestors(parent);
        }
        requestLayout();
    }

    protected void buildParentChildRelations(L node) {
        R renderable       = node.getRenderable();
        R parentRenderable = renderable.getParent();
        while (parentRenderable != null) {
            L parentNode = renderableRegistry.get(parentRenderable);
            if (parentNode != null) {
                parentNode.addChild(node);
                break;
            }
            parentRenderable = parentRenderable.getParent();
        }
    }

    // =========================================================================
    // DIRTY MARKING
    // =========================================================================

    public void markLayoutDirty(R renderable) {
        uiExecutor.runRentrant(() -> markLayoutDirtyInternal(renderable));
    }

    public void markLayoutDirtyImmediate(R renderable) {
        L node = findManagedNode(renderable);
        if (node == null) {
            logFailureDiagnosticThrottled(
                "missing-immediate:" + renderable.getName(),
                () -> String.format("[LayoutManager:%s] markLayoutDirtyImmediate dropped: %s",
                    containerName, renderable.getName()));
            return;
        }
        addToDirtySet(node);
        layoutDebouncer.executeNow(this::performUpdate);
    }

    private void markLayoutDirtyInternal(R renderable) {
        L node = findManagedNode(renderable);
        if (node == null) {
            logFailureDiagnosticThrottled(
                "missing-dirty:" + renderable.getName(),
                () -> String.format("[LayoutManager:%s] markLayoutDirty dropped: %s",
                    containerName, renderable.getName()));
            return;
        }
        addToDirtySet(node);
        renderable.advanceRenderPhase(RenderPhase.COLLECTING);
        requestLayout();
    }

    /**
     * Add node to dirty set. If the node is a group member, all group members
     * and the group owner are dirtied — they are laid out together.
     * Content-sized nodes dirty ancestors upward.
     */
    private void addToDirtySet(L node) {
        G memberGroup = node.getMemberGroup();
        if (memberGroup != null) {
            for (L member : memberGroup.getMembers()) {
                dirtyLayoutNodes.add(member);
                dirtyAffectedAncestors(member);
            }
            L owner = memberGroup.getOwner();
            if (owner != null) {
                dirtyLayoutNodes.add(owner);
                dirtyAffectedAncestors(owner);
            }
        } else {
            dirtyLayoutNodes.add(node);
            dirtyAffectedAncestors(node);
            // Fan out to owned group members — the group callback will re-fire
            // for this owner, so members need to be in the pass and their
            // content-sized ancestors need accurate geometry.
            for (G ownedGroup : node.getOwnedGroups()) {
                for (L member : ownedGroup.getMembers()) {
                    dirtyLayoutNodes.add(member);
                    dirtyAffectedAncestors(member);
                }
            }
        }
    }

    /**
     * Dirty ancestors upward while they are content-sized, stopping at and
     * including the first non-content-sized ancestor. That ancestor's layout
     * callback must re-run with accurate child geometry.
     */
    private void dirtyAffectedAncestors(L node) {
        if (node.isFloating()) return;
        L current = node.getParent();
        while (current != null) {
            dirtyLayoutNodes.add(current);
            if (!current.isSizedByContent()) break;
            current = current.isFloating() ? null : current.getParent();
        }
    }

    private void markLayoutDirtySubtree(R renderable) {
        if (renderable.isHiddenForced()) return;  // ← add this guard
        L node = renderableRegistry.get(renderable);
        if (node == null) return;
        addToDirtySet(node);
        for (R child : renderable.getChildren()) markLayoutDirtySubtree(child);
        requestLayout();
    }

    private L findManagedNode(R renderable) {
        L node = renderableRegistry.get(renderable);
        return node != null ? node : floatingRegistry.get(renderable);
    }

    // =========================================================================
    // SCHEDULING
    // =========================================================================

    public void setLayoutStateListener(Consumer<Boolean> listener) {
        this.layoutStateListener = listener;
    }

    /**
     * Set a callback to be invoked after a layout pass completes.
     * The callback receives the set of nodes that were processed in the layout pass.
     *
     * @param callback callback to invoke after layout pass completes
    
    public void setOnAfterLayoutPass(Consumer<Set<L>> callback) {
        this.onAfterLayoutPass = callback;
    } */

    /**
     * Called after a layout pass completes.
     * This is the hook that tests should use to know when layout has finished.
     
    protected void onAfterLayoutPass(Set<L> processedNodes) {
        if (onAfterLayoutPass != null) {
            onAfterLayoutPass.accept(processedNodes);
        }
    }*/
    

    

    public void setRenderRequester(Consumer<R> requester) {
        this.renderRequester = requester;
        if (isCurrentThread()) {
            fireRenderIfIdle();
        } else {
            uiExecutor.runLater(this::fireRenderIfIdle);
        }
    }


    protected void requestLayout() {
        if (batchingRequests) {
            deferredLayoutRequested = true;
            return;
        }
        layoutScheduled = true;
        layoutDebouncer.submit(this::performUpdate, minDebounceMs, TimeUnit.MILLISECONDS);
        currentDebounceMs = layoutDebouncer.getLastScheduledDelayMs();
    }

    public boolean hasPendingLayout() {
        return !dirtyLayoutNodes.isEmpty() || !dirtyFloatingNodes.isEmpty() || layoutScheduled;
    }

    public void requestRender(R requester) {
        if (!isCurrentThread()) {
            uiExecutor.runLater(() -> requestRender(requester));
            return;
        }
        R renderRoot = resolveRenderRoot(requester);
        if (requester != null && renderRoot == null) {
            return;
        }
        renderRequested = true;
        if (renderRoot != null) {
            renderRequestedBy = renderRoot;
        }
        fireRenderIfIdle();
    }

    private R resolveRenderRoot(R requester) {
        if (requester == null) return null;
        R root = requester;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        return isRegistered(root) ? root : null;
    }

    private void fireRenderIfIdle() {
        if (!renderRequested) return;
        if (layoutExecuting) return;
        if (batchingRequests) {                                                          
          deferredLayoutRequested = true;                                                          
          return;                                                                                                        
        }  
        if (!dirtyLayoutNodes.isEmpty() || !dirtyFloatingNodes.isEmpty()) return;
        if (renderRequester == null) return;
        
        R requester = renderRequestedBy;
        renderRequested = false;
        renderRequestedBy = null;
        renderRequester.accept(requester);
    }


    /**
     * Queue a structural tree mutation to run after the current pass.
     * If no pass is active, the mutation runs immediately.
     */
    public void runWhenLayoutIdle(Runnable mutation) {
        if (mutation == null) return;
        if (!isCurrentThread()) {
            uiExecutor.runLater(() -> runWhenLayoutIdle(mutation));
            return;
        }
        if (layoutExecuting) {
            deferredTreeMutations.addLast(mutation);
            return;
        }
        mutation.run();
    }

    public void invalidateWhenLayoutIdle(Runnable invalidation) {
        if(invalidation == null) return;
        if(!isCurrentThread()){
            uiExecutor.runLater(()->invalidateWhenLayoutIdle(invalidation));
            return;
        }
        if(layoutExecuting){
            deferredInvalidations.addLast(invalidation);
            return;
        }
        invalidation.run();
    }

    // =========================================================================
    // PASS EXECUTION
    // =========================================================================

    protected final void performUpdate() {
        if (!uiExecutor.isCurrentThread()) {
            throw new IllegalStateException(
                "performUpdate must run on uiExecutor thread. " +
                "Called from: " + Thread.currentThread() +
                " (isUIThread=" + uiExecutor.isCurrentThread() + ")"
            );
        }
        layoutScheduled = false;
        if (layoutStateListener != null) layoutStateListener.accept(true);
        try {
            performUpdateInternal();
        } finally {
            if (layoutStateListener != null) layoutStateListener.accept(false);
        }
        drainDeferredTreeMutations();
    }



    protected final void performUpdateInternal() {
        long startTime = System.nanoTime();

        Set<L> currentLayoutDirty   = dirtyLayoutNodes;
        Set<L> currentFloatingDirty = dirtyFloatingNodes;
        dirtyLayoutNodes   = new LinkedHashSet<>();
        dirtyFloatingNodes = new LinkedHashSet<>();

        Set<L> allDirty = new LinkedHashSet<>();
        allDirty.addAll(currentLayoutDirty);
        allDirty.addAll(currentFloatingDirty);
        int dirtyCount = allDirty.size();

        if (dirtyCount > 0) {
            logSummaryDiagnostic(String.format(
                "[LayoutManager:%s] pass start (layout=%d, floating=%d, nodes=%s)",
                containerName, currentLayoutDirty.size(), currentFloatingDirty.size(),
                summarizeNodes(allDirty)));
        }


        layoutExecuting = true;
        currentPassNodes = allDirty;          // expose to isInCurrentPass()
        try {
            if (!allDirty.isEmpty()) {
                try {
                    performUnifiedLayoutUpdate(allDirty);
                  //  onAfterLayoutPass(allDirty);
                } catch (Exception e) {
                    Log.logError(String.format(
                        "[LayoutManager:%s] layout pass failed (nodes=%s)",
                        containerName, summarizeNodes(allDirty)), e);
                    emmergencyReset(allDirty, currentLayoutDirty, currentFloatingDirty);
                    throw e;
                }
            }
            applyPendingFocusRequest();
        } finally {
            layoutExecuting  = false;
            currentPassNodes = null;          // pass is over
            currentPassTraversal = null;
            currentPassCursor = 0;
        }

        drainDefferedInvalidations();
   
        long endTime = System.nanoTime();
        double ms = (endTime - startTime) / 1_000_000.0;

        if (dirtyCount > 0) {
            logSummaryDiagnostic(String.format(
                "[LayoutManager:%s] pass end %.2fms (nodes=%d, nextDebounce=%dms)",
                containerName, ms, dirtyCount, currentDebounceMs));
        }

        if (!dirtyLayoutNodes.isEmpty() || !dirtyFloatingNodes.isEmpty()) {
            requestLayout();
        } else {
            fireRenderIfIdle();
        }
    }

    private void emmergencyReset(
        Set<L> allDirty, 
        Set<L> currentLayoutDirty, 
        Set<L> currentFloatingDirty
    ){
        for (L node : allDirty) {
            node.clear();
            node.setInFlightContext(null);
        }
        for (R r : committingNodes) {                                                                                      
          r.advanceRenderPhase(RenderPhase.IDLE);                                                                        
        } 
        committingNodes.clear(); 
        dirtyLayoutNodes.addAll(currentLayoutDirty);
        dirtyFloatingNodes.addAll(currentFloatingDirty);
    }

    private void drainDeferredTreeMutations() {
        while (!deferredTreeMutations.isEmpty()) {
            Runnable mutation = deferredTreeMutations.pollFirst();
            if (mutation == null) continue;
            try {
                mutation.run();
            } catch (Exception e) {
                Log.logError("[LayoutManager] deferred tree mutation failed", e);
            }
        }
    }

    protected void drainDefferedInvalidations(){
        while(!deferredInvalidations.isEmpty()){
            Runnable invalidation = deferredInvalidations.pollFirst();
            if(invalidation == null) continue;
            try{
                invalidation.run();
            } catch (Exception e){
                Log.logError("[LayoutManager] deferred invalidation failed", e);
            }
        }
    }

    public boolean isInCurrentPass(R renderable) {
        Set<L> pass = currentPassNodes;
        if (pass == null) return false;
        L node = renderableRegistry.get(renderable);
        if (node == null) node = floatingRegistry.get(renderable);
        return node != null && pass.contains(node);
    }

   

    // =========================================================================
    // UNIFIED LAYOUT PASS
    // =========================================================================

    protected void performUnifiedLayoutUpdate(Set<L> dirtyNodes) {
      
        Set<L> allDirty = new LinkedHashSet<>(dirtyNodes);
        expandDescendantsInto(allDirty);


        List<L> sorted = depthSort(new ArrayList<>(allDirty));

        logSummaryDiagnostic(String.format(
            "[LayoutManager:%s] layout pass count=%d nodes=%s",
            containerName, sorted.size(), summarizeNodes(sorted)));

        runContentPrePass(sorted, allDirty);
        currentPassTraversal = sorted;
        currentPassCursor = 0;
        while (currentPassTraversal != null && currentPassCursor < currentPassTraversal.size()) {
            if (!pendingInjections.isEmpty()) {
                mergeInjections(); // single atomic operation at a safe checkpoint
            }
            L node = currentPassTraversal.get(currentPassCursor++);
            processNode(node);
        }
    }

    private void mergeInjections() {
        int insertionPoint = currentPassCursor;
        List<L> tail = new ArrayList<>(
            currentPassTraversal.subList(insertionPoint, currentPassTraversal.size())
        );
        tail.addAll(pendingInjections);
        pendingInjections.clear();
        depthSort(tail);
        // Replace tail atomically — list is not touched between subList.clear and addAll
        currentPassTraversal.subList(insertionPoint, currentPassTraversal.size()).clear();
        currentPassTraversal.addAll(tail);
    }

    private void processNode(L node) {
        // Reuse in-flight context if content pre-pass created one,
        // otherwise create fresh — layout callback reads measuredContentBounds
        LC context = node.getInFlightContext();
        if (context == null) {
            context = createRenderableContext(node);
            node.setInFlightContext(context);
        }
        context.initialize(node);

        node.calculateLayout(context);
        applyNode(node); //calls node.clear
        markNodeCommitted(node);

        // calculatedLayout is null after this point. fireOwnedGroups
        // reads committed renderable state, not calculatedLayout.
        fireOwnedGroups(node);

        context.reset();
        recycleLayoutContext(context);
        node.setInFlightContext(null);
    }

    private void markNodeCommitted(L node) {
        Set<L> pass = currentPassNodes;
        if (pass != null) {
            pass.remove(node);
        }
    }

    private void fireOwnedGroups(L ownerNode) {
        for (G group : ownerNode.getOwnedGroups()) {
            if (group.getOwner() != ownerNode) continue;

            List<L> members = group.getMembers();
            LC[] contexts = createContextArray(members.size());

            for (int i = 0; i < members.size(); i++) {
                L member = members.get(i);
                // Invariant: owner in pass → all members in pass (enforced by
                // addToDirtySet fan-out + expandDescendantsInto). Violation here
                // means a structural bug in dirty propagation.
                if (currentPassNodes != null && !currentPassNodes.contains(member)) {
                    logFailureDiagnosticThrottled(
                        "group-member-not-in-pass:" + member.getName(),
                        () -> String.format(
                            "[LayoutManager:%s] group '%s' member '%s' missing from pass — owner '%s' in pass but member was not expanded",
                            containerName, group.getGroupId(), member.getName(), ownerNode.getName()));
                }
                LC ctx = member.getInFlightContext();
                if (ctx == null) {
                    ctx = createRenderableContext(member);
                    member.setInFlightContext(ctx);
                }
                ctx.initialize(member);
                contexts[i] = ctx;
            }

            group.executeLayoutCallback(contexts);
    

        }
    }

    private void runContentPrePass(List<L> dirtyNodes, Set<L> passNodes) {
        for (int i = dirtyNodes.size() - 1; i >= 0; i--) {
            L node = dirtyNodes.get(i);
            
            if (!node.isSizedByContent()) continue;
            if (node.isContentMeasured()) continue;
            measureSingleNode(node, passNodes);
        }
    }


    private LC measureSingleNode(L node, Set<L> passNodes) {
        LC context = node.getInFlightContext();
        if (context == null) {
            context = createRenderableContext(node);
            node.setInFlightContext(context);
        }
        context.initialize(node);

        if (!node.isContentMeasured()) {
            List<L> managedChildren = new ArrayList<>();
            for (L child : node.getChildren()) {
                if (child.getRenderable().isHiddenForced()) continue; 
                if (passNodes.contains(child)) {
                    managedChildren.add(child);
                } else {
                    Log.logMsg("[LayoutManager] skipping removed child during measurement: "
                        + child.getName(), LOG_LEVEL);
                }
            }
            LC[] childContexts = createContextArray(managedChildren.size());
            for (int i = 0; i < managedChildren.size(); i++) {
                childContexts[i] = ensureChildMeasurementContext(managedChildren.get(i), passNodes);
            }
            node.measureContent(context, childContexts);
        }

        return context;
    }

    private LC ensureChildMeasurementContext(L child, Set<L> passNodes) {
        LC context = child.getInFlightContext();
        if (context != null) return context;

        if (child.isSizedByContent() && !child.isContentMeasured()) {
            return measureSingleNode(child, passNodes);
        }
        context = createRenderableContext(child);
        context.initialize(child);
        child.setInFlightContext(context);
        return context;
    }


    // =========================================================================
    // APPLY
    // =========================================================================

    /**
     * Commit calculatedLayout to the renderable. Called at most once per node
     * per pass. node.clear() releases calculatedLayout back to the pool.
     *
     * VISIBILITY FLIP:
     * If the node just became effectively visible, its children have never been
     * laid out while visible. The subtree is injected to the current pass. 
     *
     * DAMAGE:
     * invalidate() fires inside applyLayoutData() via applySpatialChange().
     * Because layoutExecuting is true, Renderable.invalidate() suppresses the
     * render request — damage accumulates and the render fires once from
     * layoutStateListener(false) when the pass completes.
     * 
     */
    private void applyNode(L node) {
        LD calculatedLayout = node.getCalculatedLayout();
        if (calculatedLayout == null) return;

        R renderable = node.getRenderable();
        boolean wasEffectivelyVisible = !renderable.isEffectivelyHidden();

        if (calculatedLayout.hasRegion()) {
            applyParentAbsolutePosition(node, calculatedLayout);
        }

        renderable.applyLayoutData(calculatedLayout);
        renderable.advanceRenderPhase(RenderPhase.APPLYING);

        boolean becameVisible = !wasEffectivelyVisible && !renderable.isEffectivelyHidden();
        boolean becameHidden = wasEffectivelyVisible && renderable.isEffectivelyHidden();

        if (becameVisible) {
            System.out.println(String.format(
                 "[LayoutManager:%s] %s became visible — injecting subtree with content pre-pass",
                containerName, renderable.getName()));
            for (R child : renderable.getChildren()) {
                injectSubtreeWithContentPrePass(child);
            }
            dirtyAffectedAncestors(node);
        } else if (becameHidden) {
            dirtyAffectedAncestors(node);
        }
                
        committingNodes.add(renderable);
        node.clear();
    }


    private void injectSubtreeWithContentPrePass(R renderable) {
        L root = renderableRegistry.get(renderable);
        if (root == null) return;

        Set<L> passNodes = currentPassNodes;
        if (passNodes == null || currentPassTraversal == null) {
            markLayoutDirtySubtree(renderable);
            return;
        }

        // ── Phase 1: register subtree into passNodes + pendingInjections ─────────
        // Must happen before measurement so passNodes.contains() passes in
        // measureSingleNode when it checks managed children.
        int sizeBefore = pendingInjections.size();
        collectSubtreeForCurrentPass(root, pendingInjections, passNodes);

        // ── Phase 2: content pre-pass over the newly added slice ─────────────────
        // Reverse order = bottom-up within the slice. measureSingleNode's own
        // ensureChildMeasurementContext recursion handles deeper chains, so we
        // only need to drive the top-level content-sized nodes here.
        for (int i = pendingInjections.size() - 1; i >= sizeBefore; i--) {
            L node = pendingInjections.get(i);
            if (node.getRenderable().isHiddenForced()) continue;
            if (!node.isSizedByContent())              continue;
            if (node.isContentMeasured())              continue;
            measureSingleNode(node, passNodes);
        }
    }

    private void collectSubtreeForCurrentPass(L node, List<L> injected, Set<L> passNodes) {
        if (passNodes.add(node)) {
            injected.add(node);
        }
        for (L child : node.getChildren()) {
            collectSubtreeForCurrentPass(child, injected, passNodes);
        }
    }


    private void applyParentAbsolutePosition(L node, LD calculatedLayout) {
        L parent = node.getParent();
        if (parent == null) return;

        // Group pass: owner and member are committed together — parentCalculated
        // may still be live if the owner hasn't been cleared yet.
        LD parentCalculated = parent.getCalculatedLayout();
        if (parentCalculated != null && parentCalculated.hasRegion()) {
            calculatedLayout.getSpatialRegion().setParentAbsolutePosition(
                parentCalculated.getSpatialRegion().getAbsolutePosition());
            return;
        }

        // Linear pass: parent was already committed (node.clear() ran) before we
        // reach this child, so getRegion() reflects the freshly applied position.
        // Assert depth ordering held — a child must never appear above its parent.
        assert parent.getDepth() < node.getDepth()
            : "[LayoutManager] depth-sort violated: parent " + parent.getName()
            + " depth=" + parent.getDepth()
            + " >= child " + node.getName()
            + " depth=" + node.getDepth();

        R parentRenderable = parent.getRenderable();
        S parentRegion = parentRenderable.getRegion();
        try {
            calculatedLayout.getSpatialRegion().setParentAbsolutePosition(
                parentRegion.getAbsolutePosition());
        } finally {
            parentRenderable.getRegionPool().recycle(parentRegion);
        }
    }

    // =========================================================================
    // SORTING & EXPANSION
    // =========================================================================

    /** Sort by depth — parents strictly before children. */
    private List<L> depthSort(List<L> nodes) {
        nodes.sort(Comparator.comparingInt(L::getDepth));
        return nodes;
    }

    private void expandDescendantsInto(Set<L> nodes) {
        for (L node : new ArrayList<>(nodes)) collectDescendants(node, nodes);
    }

    private void collectDescendants(L node, Set<L> result) {
        for (L child : node.getChildren()) {
            if (child.getRenderable().isHiddenForced()) continue;
            if (child.isManaged() && result.add(child)) {
                collectDescendants(child, result);
            }
        }
    }

    // =========================================================================
    // LAYOUT EXECUTING FLAG
    // =========================================================================

    /** Read by Renderable.invalidate() to suppress render requests during a pass. */
    public boolean isCurrentlyExecutingLayout() { return layoutExecuting; }

    // =========================================================================
    // RENDER DISPATCH
    // =========================================================================

    public void notifyRenderDispatched() {
        for (R r : committingNodes) r.advanceRenderPhase(RenderPhase.RENDERED);
        committingNodes.clear();
    }
    
    public void clearIdleCommittingNodes() {
        for (R r : committingNodes) r.advanceRenderPhase(RenderPhase.IDLE);
        committingNodes.clear();
    }

    public String summarizeCommittingNodes() { return summarizeRenderables(committingNodes); }

    // =========================================================================
    // FLOATING
    // =========================================================================

    public void registerFloating(R renderable, LCB layoutCallback, R anchor) {
        consumeFocusDesired(renderable);
        L node = createRenderableNode(renderable);
        node.setLayoutCallback(layoutCallback);
        node.setPositionAnchor(anchor);
        floatingRegistry.put(renderable, node);
        floatingLayer.add(renderable);
        renderable.setLayoutManager(new ManagerHandle());
        markFloatingDirty(renderable);
        R renderingParent = renderable.getLogicalParent();
        if (renderingParent != null) renderingParent.invalidate();
    }

    public void migrateToFloating(R renderable, R anchor) {
        L node = renderableRegistry.remove(renderable);
        if (node != null) {
            detachNodeForFloating(node);
            floatingRegistry.put(renderable, node);
            floatingLayer.add(renderable);
            node.setPositionAnchor(anchor);
            markFloatingDirty(renderable);
        }
    }

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

    public void markFloatingDirty(R renderable) {
        L node = floatingRegistry.get(renderable);
        if (node != null) {
            dirtyFloatingNodes.add(node);
            requestLayout();
        } else {
            logFailureDiagnosticThrottled(
                "missing-floating:" + renderable.getName(),
                () -> String.format("[LayoutManager:%s] markFloatingDirty dropped: %s",
                    containerName, renderable.getName()));
        }
    }

    public void unregisterFloating(R renderable) {
        L node = floatingRegistry.remove(renderable);
        if (node != null) {
            damageRenderingParentAtFloatingRegion(renderable);
            dirtyFloatingNodes.remove(node);
            floatingLayer.remove(renderable);
            if (pendingFocusRequest == renderable) pendingFocusRequest = null;
            if (renderRequestedBy == renderable) renderRequestedBy = null;

            renderable.removedFromLayout();
        }
    }

    private void detachNodeForFloating(L node) {
        dirtyLayoutNodes.remove(node);
        dirtyFloatingNodes.remove(node);
        L parent = node.getParent();
        if (parent != null) {
            parent.removeChild(node);
            dirtyAffectedAncestors(parent);
        }
    }

    protected abstract void damageRenderingParentAtFloatingRegion(R renderable);

    // =========================================================================
    // GROUP MANAGEMENT
    // =========================================================================

    public void createGroup(String groupId) {
        uiExecutor.runRentrant(() -> createGroupIfAbsent(groupId));
    }

    private G createGroupIfAbsent(String groupId) {
        return groupRegistry.computeIfAbsent(groupId, id -> createEmptyGroup(id));
    }

    public void addToGroup(R renderable, String groupId) {
        uiExecutor.runRentrant(() -> addToGroupInternal(renderable, groupId));
    }

    private void addToGroupInternal(R renderable, String groupId) {
        L node  = renderableRegistry.get(renderable);
        G group = groupRegistry.computeIfAbsent(groupId, id -> createEmptyGroup(id));
        if (node == null) return;

        if (!bindGroupToMemberParent(node, group, groupId)) {
            return;
        }
        node.setMemberGroup(group);

        // If the group has no layout callback yet, try to recover it from the
        // owning renderable's local childGroups map. This handles the case where
        // a child is added to a stack that was already registered with the manager
        // — registerPendingGroups() only runs once at registration time and will
        /*  not have seen this group if it was created afterward.
        if (!group.hasLayoutCallback() && parent != null) {
            R ownerRenderable = parent.getRenderable();
            GSE pendingState  = ownerRenderable.getChildGroupState(groupId);
            if (pendingState != null && pendingState.getLayoutCallback() != null) {
                group.setLayoutCallback(pendingState.getLayoutCallback());
            }
        }*/

        markLayoutDirty(renderable);
    }

    private boolean bindGroupToMemberParent(L memberNode, G group, String groupId) {
        L parent = memberNode.getParent();
        if (parent == null) {
            logFailureDiagnosticThrottled(
                "group-member-without-parent:" + groupId + ":" + memberNode.getName(),
                () -> String.format(
                    "[LayoutManager:%s] cannot add %s to group '%s': node has no parent",
                    containerName, memberNode.getName(), groupId));
            return false;
        }

        L owner = group.getOwner();
        if (owner == null) {
            parent.addOwnedGroup(group);
            return true;
        }

        if (owner != parent) {
            logFailureDiagnosticThrottled(
                "group-owner-member-parent-mismatch:" + groupId + ":" + memberNode.getName(),
                () -> String.format(
                    "[LayoutManager:%s] cannot add %s to group '%s': owner=%s memberParent=%s",
                    containerName, memberNode.getName(), groupId, owner.getName(), parent.getName()));
            return false;
        }

        if (!parent.getOwnedGroups().contains(group)) {
            parent.addOwnedGroup(group);
        }
        return true;
    }

    public void removeFromGroup(R renderable) {
        uiExecutor.runRentrant(() -> removeFromGroupInternal(renderable));
    }

    private void removeFromGroupInternal(R renderable) {
        L node = renderableRegistry.get(renderable);
        if (node == null) return;
        G group = node.getMemberGroup();
        node.setMemberGroup(null);
        if (group != null && group.isEmpty()) {
            L owner = group.getOwner();
            if (owner != null) owner.removeOwnedGroup(group);
            groupRegistry.remove(group.getGroupId());
        }
    }

    public void destroyGroup(String groupId) {
        uiExecutor.runRentrant(() -> destroyGroupInternal(groupId));
    }

    private void destroyGroupInternal(String groupId) {
        G group = groupRegistry.remove(groupId);
        if (group == null) return;
        L owner = group.getOwner();
        if (owner != null) owner.removeOwnedGroup(group);
        for (L member : new ArrayList<>(group.getMembers())) member.setMemberGroup(null);
        group.cleanup();
    }

    public void setGroupLayoutCallback(String groupId, GCB callback) {
        uiExecutor.runRentrant(() -> setGroupLayoutCallbackInternal(groupId, callback));
    }

    private void setGroupLayoutCallbackInternal(String groupId, GCB callback) {
        G group = groupRegistry.computeIfAbsent(groupId, id -> createEmptyGroup(id));
        group.setLayoutCallback(callback);
    }

    public String getRenderableGroupId(R renderable) {
        L node = renderableRegistry.get(renderable);
        if (node == null) return null;
        G group = node.getMemberGroup();
        return group != null ? group.getGroupId() : null;
    }

    // =========================================================================
    // FOCUS
    // =========================================================================

    public void setFocusRequester(Consumer<R> focusRequester) {
        this.focusRequester = focusRequester;
    }

    private void consumeFocusDesired(R renderable) {
        if (renderable != null && renderable.hasState(RenderableStates.STATE_FOCUS_DESIRED)) {
            renderable.getStateMachine().removeState(RenderableStates.STATE_FOCUS_DESIRED);
            pendingFocusRequest = renderable;
        }
    }

    private void applyPendingFocusRequest() {
        if (pendingFocusRequest == null || focusRequester == null) return;
        R candidate = pendingFocusRequest;
        if (!isRegistered(candidate)) { pendingFocusRequest = null; return; }
        if (candidate.isFocusable()) {
            pendingFocusRequest = null;
            focusRequester.accept(candidate);
        }
    }

    private boolean isRegistered(R r) {
        return renderableRegistry.containsKey(r) || floatingRegistry.containsKey(r);
    }

    // =========================================================================
    // BATCHING
    // =========================================================================

    public void beginRequestBatch() { 
        if(!uiExecutor.isCurrentThread()){
            uiExecutor.runLater(this::beginRequestBatch);
            return;
        }
        batchingRequests = true; 
    }

    public void endRequestBatch() {
        if(!uiExecutor.isCurrentThread()){
            uiExecutor.runLater(this::endRequestBatch);
            return;
        }
        batchingRequests = false;
        if(!dirtyLayoutNodes.isEmpty()                                                                                
              || !dirtyFloatingNodes.isEmpty()                                                                           
              || deferredLayoutRequested
        ) {  
            deferredLayoutRequested = false;
            requestLayout();
        }
    }

    public void batchRequests(Runnable requests) {
        beginRequestBatch();
        try { requests.run(); } finally { endRequestBatch(); }
    }

    // =========================================================================
    // DIAGNOSTICS
    // =========================================================================

    public DiagnosticMode getDiagnosticMode()             { return diagnosticMode; }

    public void setDiagnosticMode(DiagnosticMode mode) {
        DiagnosticMode next = mode != null ? mode : DiagnosticMode.OFF;
        DiagnosticMode prev = this.diagnosticMode;
        this.diagnosticMode = next;
        diagnosticLogTimes.clear();
        if (prev != next) Log.logMsg(String.format(
            "[LayoutManager:%s] diagnostic mode → %s", containerName, next),
            DIAGNOSTIC_LOG_LEVEL);
    }

    public boolean isDiagnosticModeEnabled(DiagnosticMode minimum) {
        return diagnosticMode.includes(minimum);
    }

    private void logDiagnostic(String msg, DiagnosticMode minimum, LogLevel level) {
        if (isDiagnosticModeEnabled(minimum)) Log.logMsg(msg, level);
    }

    private void logSummaryDiagnostic(String msg) {
        logDiagnostic(msg, DiagnosticMode.SUMMARY, ROUTINE_DIAGNOSTIC_LOG_LEVEL);
    }

    private void logFailureDiagnosticThrottled(String key, Supplier<String> msg) {
        if (!isDiagnosticModeEnabled(DiagnosticMode.FAILURES)) return;
        long now = System.currentTimeMillis();
        Long last = diagnosticLogTimes.get(key);
        if (last != null && (now - last) < DIAGNOSTIC_LOG_THROTTLE_MS) return;
        diagnosticLogTimes.put(key, now);
        Log.logMsg(msg.get(), DIAGNOSTIC_LOG_LEVEL);
    }

    public String describeNode(L node) {
        if (node == null) return "node=<null>";
        L parent = node.getParent();
        G group  = node.getMemberGroup();
        return String.format("node=%s, parent=%s, group=%s, floating=%s",
            node.getName(),
            parent != null ? parent.getName() : "<root>",
            group  != null ? group.getGroupId() : "<none>",
            node.isFloating());
    }

    private String summarizeNodes(Collection<L> nodes) {
        if (nodes == null || nodes.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (L node : nodes) {
            if (i > 0) sb.append(", ");
            if (i == DIAGNOSTIC_NODE_SAMPLE_SIZE) { sb.append("..."); break; }
            sb.append(node.getName());
            i++;
        }
        return sb.append(']').toString();
    }

    private String summarizeRenderables(Collection<R> renderables) {
        if (renderables == null || renderables.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (R r : renderables) {
            if (i > 0) sb.append(", ");
            if (i == DIAGNOSTIC_NODE_SAMPLE_SIZE) { sb.append("..."); break; }
            sb.append(r.getName());
            i++;
        }
        return sb.append(']').toString();
    }

    public String getDiagnostics() {
        return String.format(
            "LayoutManager[container=%s, renderables=%d, groups=%d, dirtyLayout=%d, " +
            "debounce=%dms, diagnosticMode=%s]",
            containerName, renderableRegistry.size(), groupRegistry.size(),
            dirtyLayoutNodes.size(), currentDebounceMs, diagnosticMode);
    }

    // =========================================================================
    // DEBOUNCE CONFIG
    // =========================================================================

    public void setDebounceDelay(long delayMs) {
        if (delayMs < 0) throw new IllegalArgumentException("Delay must be non-negative");
        this.minDebounceMs = delayMs;
        this.currentDebounceMs = delayMs;
    }

    private boolean isCurrentThread() { return uiExecutor.isCurrentThread(); }

    // =========================================================================
    // SHUTDOWN
    // =========================================================================

    public void shutdown() {
        renderableRegistry.clear();
        floatingRegistry.clear();
        dirtyLayoutNodes.clear();
        dirtyFloatingNodes.clear();
        groupRegistry.clear();
    }

    // =========================================================================
    // MANAGER HANDLE
    // =========================================================================

    private class ManagerHandle implements RenderableLayoutManagerHandle<R, LCB, G, GCB> {
       
        private void runMutation(Runnable mutation) {
            uiExecutor.runRentrant(() -> RenderableLayoutManager.this.runWhenLayoutIdle(mutation));
        }

        @Override public boolean isInCurrentPass(R r)           { return RenderableLayoutManager.this.isInCurrentPass(r); }
        @Override public void markLayoutDirty(R r)              { RenderableLayoutManager.this.markLayoutDirty(r); }
        @Override public void markLayoutDirtyImmediate(R r)     { RenderableLayoutManager.this.markLayoutDirtyImmediate(r); }
       
       
        @Override public void migrateToFloating(R r, R anchor) { 
            RenderableLayoutManager.this.runWhenLayoutIdle(
                () ->RenderableLayoutManager.this.migrateToFloating(r, anchor)); 
        }
        @Override public void migrateToRegular(R r) { 
            RenderableLayoutManager.this.runWhenLayoutIdle(
                () -> RenderableLayoutManager.this.migrateToRegular(r)); 
        }
        @Override public void registerChild(R child, LCB layoutCb) {
            RenderableLayoutManager.this.runWhenLayoutIdle(
                () -> RenderableLayoutManager.this.registerRenderableInternal(child, layoutCb));
        }
        @Override public void unregister(R r)                   { 
            RenderableLayoutManager.this.runWhenLayoutIdle(
                () -> RenderableLayoutManager.this.unregisterRenderableInternal(r));
        }
        @Override public void requestFocus(R r) {
            uiExecutor.runRentrant(() -> runWhenLayoutIdle(() -> {
                RenderableLayoutManager.this.consumeFocusDesired(r);
                if (focusRequester != null && r != null && r.isFocusable()) {
                    if (pendingFocusRequest == r) pendingFocusRequest = null;
                    focusRequester.accept(r);
                } else {
                    pendingFocusRequest = r;
                }
            }));
        }

        @Override public void createLayoutGroup(String id)              { runMutation(()->RenderableLayoutManager.this.createGroup(id));  }
        @Override public void addToLayoutGroup(R r, String id)          { runMutation(()->RenderableLayoutManager.this.addToGroup(r, id)); }
        @Override public void removeLayoutGroupMember(R r)              { runMutation(()->RenderableLayoutManager.this.removeFromGroup(r)); }
        @Override public void destroyLayoutGroup(String id)             { runMutation(()->RenderableLayoutManager.this.destroyGroup(id)); }
        @Override public void setGroupLayoutCallback(String id, GCB cb) { runMutation(()->RenderableLayoutManager.this.setGroupLayoutCallback(id, cb)); }
        @Override public String getLayoutGroupIdByRenderable(R r)       { return RenderableLayoutManager.this.getRenderableGroupId(r); }
        @Override public void requestRender(R r)                        { RenderableLayoutManager.this.requestRender(r); }
        
        @Override public void beginBatch()                      { RenderableLayoutManager.this.beginRequestBatch(); }
        @Override public void endBatch()                        { RenderableLayoutManager.this.endRequestBatch(); }
        @Override public boolean isLayoutExecuting()            { return RenderableLayoutManager.this.isCurrentlyExecutingLayout(); }
        @Override public void runWhenLayoutIdle(Runnable mutation) { RenderableLayoutManager.this.runWhenLayoutIdle(mutation); }
        @Override public void deferInvalidateWhenLayoutIdle(Runnable invalidation) { RenderableLayoutManager.this.invalidateWhenLayoutIdle(invalidation); }
    }
}
