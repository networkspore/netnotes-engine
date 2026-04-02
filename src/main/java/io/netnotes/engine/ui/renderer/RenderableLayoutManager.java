package io.netnotes.engine.ui.renderer;

import io.netnotes.engine.ui.FloatingLayerManager;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.renderer.Renderable.GroupStateEntry;
import io.netnotes.engine.ui.renderer.layout.GroupLayoutCallback;
import io.netnotes.engine.ui.renderer.layout.LayoutCallback;
import io.netnotes.engine.ui.renderer.layout.LayoutContext;
import io.netnotes.engine.ui.renderer.layout.LayoutData;
import io.netnotes.engine.ui.renderer.layout.LayoutGroup;
import io.netnotes.engine.ui.renderer.layout.LayoutNode;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.LoggingHelpers.LogLevel;
import io.netnotes.engine.utils.virtualExecutors.SerializedDebouncedExecutor;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

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

    public static final long DEFAULT_DEBOUNCE_MS = 3;
    private static final long   MIN_DEBOUNCE_MS              = 2;
    private static final long   MAX_DEBOUNCE_MS              = 100;
    private static final double EXECUTION_TIME_MULTIPLIER    = 0.5;
    private static final long   DEBOUNCE_PER_NODE_MICROS     = 50;
    private static final double SMOOTHING_ALPHA              = 0.3;

    // ── Infrastructure ────────────────────────────────────────────────────────

    protected final SerializedVirtualExecutor uiExecutor;
    protected final String containerName;

    private long debounceDelayMs;
    private final SerializedDebouncedExecutor layoutDebouncer;
    private double avgExecutionTimeMs  = 0.0;
    private volatile long currentDebounceMs = DEFAULT_DEBOUNCE_MS;

    // ── Registries ────────────────────────────────────────────────────────────

    protected final Map<R, L> renderableRegistry = new ConcurrentHashMap<>();
    protected final Map<R, L> floatingRegistry   = new ConcurrentHashMap<>();
    protected final Map<String, G> groupRegistry = new ConcurrentHashMap<>();

    protected FloatingLayerManager<B,R,P,S,LC,LD,LCB> floatingLayer;

    // ── Dirty tracking ────────────────────────────────────────────────────────

    protected Set<L> dirtyLayoutNodes   = new LinkedHashSet<>();
    protected Set<L> dirtyFloatingNodes = new LinkedHashSet<>();

    // ── Runtime state ─────────────────────────────────────────────────────────

    private volatile boolean batchingRequests = false;
    private boolean drainingLayout            = false;

    /**
     * True while a layout pass is executing. Read by Renderable.invalidate()
     * to suppress render requests — damage still accumulates, render fires
     * once from layoutStateListener(false) when the pass completes.
     */
    private boolean layoutExecuting = false;

    private final Set<R> committingNodes     = new LinkedHashSet<>();
    private Consumer<Boolean> layoutStateListener = null;
    private Consumer<R>       focusRequester      = null;
    private R                 pendingFocusRequest  = null;

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
        this.debounceDelayMs = debounceDelayMs;
        this.layoutDebouncer = new SerializedDebouncedExecutor(
            uiExec, debounceDelayMs, TimeUnit.MILLISECONDS);
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
        if(uiExecutor.isCurrentThread()){
            registerRenderableInternal(renderable, layoutCallback);

        }else{
            uiExecutor.executeFireAndForget(() -> registerRenderableInternal(renderable, layoutCallback));
            return;
        }
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
            ownerNode.addOwnedGroup(group);

            for (R member : groupState.getMembers()) {
                L memberNode = renderableRegistry.get(member);
                if (memberNode != null && memberNode.getMemberGroup() != group) {
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
        if (isCurrentThread()) unregisterRenderableInternal(renderable);
        else uiExecutor.executeFireAndForget(() -> unregisterRenderableInternal(renderable));
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
            if (childNode != null) cleanupNode(childNode);
        }

        dirtyLayoutNodes.remove(node);
        L parent = node.getParent();
        if (parent != null) {
            parent.removeChild(node);
            dirtyAffectedAncestors(parent);
        }
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
        if (!isCurrentThread()) {
            uiExecutor.executeFireAndForget(() -> markLayoutDirtyInternal(renderable));
        } else {
            markLayoutDirtyInternal(renderable);
        }
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
        G group = node.getMemberGroup();
        if (group != null) {
            for (L member : group.getMembers()) {
                dirtyLayoutNodes.add(member);
                dirtyAffectedAncestors(member);
            }
            L owner = group.getOwner();
            if (owner != null) {
                dirtyLayoutNodes.add(owner);
                dirtyAffectedAncestors(owner);
            }
        } else {
            dirtyLayoutNodes.add(node);
            dirtyAffectedAncestors(node);
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

    void setLayoutStateListener(Consumer<Boolean> listener) {
        this.layoutStateListener = listener;
    }

    protected void requestLayout() {
        if (batchingRequests) return;
        layoutDebouncer.submit(this::performUpdate, debounceDelayMs, TimeUnit.MILLISECONDS);
    }

    public CompletableFuture<Void> flushLayout() {
        if (!dirtyLayoutNodes.isEmpty() || !dirtyFloatingNodes.isEmpty()) {
            return layoutDebouncer.executeNow(this::performUpdate);
        }
        return CompletableFuture.completedFuture(null);
    }

    public boolean hasPendingLayout() {
        return !dirtyLayoutNodes.isEmpty() || !dirtyFloatingNodes.isEmpty();
    }

    public boolean isLayoutExecuting() { return drainingLayout; }

    // =========================================================================
    // PASS EXECUTION
    // =========================================================================

    protected final void performUpdate() {
        if (layoutStateListener != null) layoutStateListener.accept(true);
        try {
            performUpdateInternal();
        } finally {
            if (layoutStateListener != null) layoutStateListener.accept(false);
        }
    }

    protected final void performUpdateInternal() {
        long startTime = System.nanoTime();

        // Swap dirty sets — mid-pass mutations go into the next-pass sets
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

        drainingLayout  = true;
        layoutExecuting = true;
        try {
            if (!allDirty.isEmpty()) {
                try {
                    performUnifiedLayoutUpdate(allDirty);
                    onAfterLayoutPass(allDirty);
                } catch (Exception e) {
                    dirtyLayoutNodes.addAll(currentLayoutDirty);
                    dirtyFloatingNodes.addAll(currentFloatingDirty);
                    Log.logError(String.format(
                        "[LayoutManager:%s] layout pass failed (nodes=%s)",
                        containerName, summarizeNodes(allDirty)), e);
                    throw e;
                }
            }
            applyPendingFocusRequest();
        } finally {
            drainingLayout  = false;
            layoutExecuting = false;
        }

        long endTime = System.nanoTime();
        double ms = (endTime - startTime) / 1_000_000.0;
        long nextDebounce = calculateAdaptiveDebounce(ms, dirtyCount);
        setDebounceDelay(nextDebounce);

        if (dirtyCount > 0) {
            logSummaryDiagnostic(String.format(
                "[LayoutManager:%s] pass end %.2fms (nodes=%d, nextDebounce=%dms)",
                containerName, ms, dirtyCount, nextDebounce));
        }

        // If mid-pass mutations produced new dirty nodes, schedule follow-up
        if (!dirtyLayoutNodes.isEmpty() || !dirtyFloatingNodes.isEmpty()) {
            requestLayout();
        }
    }

    protected void onAfterLayoutPass(Set<L> processedNodes) {}

    // =========================================================================
    // UNIFIED LAYOUT PASS
    // =========================================================================

    protected void performUnifiedLayoutUpdate(Set<L> dirtyNodes) {
        // Reset all per-pass state on groups and nodes
        for (G group : groupRegistry.values()) {
            group.resetForPass();
        }

        Set<L> allDirty = new LinkedHashSet<>(dirtyNodes);
        expandDescendantsInto(allDirty);

        // Reset per-pass node state after expansion so newly added nodes
        // are also reset
        for (L node : allDirty) {
            node.resetForPass();
        }

        List<L> sorted = depthSort(new ArrayList<>(allDirty));

        logSummaryDiagnostic(String.format(
            "[LayoutManager:%s] layout pass count=%d nodes=%s",
            containerName, sorted.size(), summarizeNodes(sorted)));

        runContentPrePass(sorted);

        for (L node : sorted) {
            processNode(node);
        }
    }


    private void processNode(L node) {
        // Reuse in-flight context if content pre-pass created one,
        // otherwise create fresh — layout callback reads measuredContentBounds
        LC context = node.getInFlightContext();
        if (context == null) {
            context = createRenderableContext(node);
            context.initialize(node);
            node.setInFlightContext(context);
        }

        node.calculateLayout(context);
        applyNode(node);
        fireOwnedGroups(node);

        context.reset();
        recycleLayoutContext(context);
        node.setInFlightContext(null);
        node.clear();
    }



 
    private void fireOwnedGroups(L ownerNode) {
        for (G group : ownerNode.getOwnedGroups()) {
            if (group.hasAppliedThisPass()) continue;

            List<L> members = group.getMembers();
            LC[] contexts = createContextArray(members.size());

            for (int i = 0; i < members.size(); i++) {
                L member = members.get(i);
                LC ctx = member.getInFlightContext(); // reuse if content-measured
                if (ctx == null) {
                    ctx = createRenderableContext(member);
                    ctx.initialize(member);
                    member.setInFlightContext(ctx);
                }
                contexts[i] = ctx;
            }

            group.executeLayoutCallback(contexts);
            group.markAppliedThisPass();

            // Finalize each member's calculatedLayout after group callback
            // populates it — processNode will applyNode each member when
            // the top-down pass reaches them, contexts released then
            for (L member : members) {
                member.finalizeCalculatedLayout();
            }
        }
    }

    private void runContentPrePass(List<L> dirtyNodes) {
        // dirtyNodes is depth-sorted ascending — reverse gives leaves first,
        // guaranteeing children are measured before parents with no group
        // coordination needed (all members are individually dirty)
        for (int i = dirtyNodes.size() - 1; i >= 0; i--) {
            L node = dirtyNodes.get(i);
            if (!node.isSizedByContent()) continue;
            if (node.isContentMeasured()) continue;
            measureSingleNode(node);
        }
    }


    private void measureSingleNode(L node) {
        if (node.isContentMeasured()) return;
        LC context = createRenderableContext(node);
        context.initialize(node);
        node.setInFlightContext(context);
        node.measureContent(context);
        // context stays alive — released after layout callback in processNode
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
     * laid out while visible. Queue the subtree for a follow-up pass. The node
     * itself is committed — the renderer knows it exists. Children render only
     * after the follow-up pass gives them valid geometry.
     *
     * DAMAGE:
     * invalidate() fires inside applyLayoutData() via applySpatialChange().
     * Because layoutExecuting is true, Renderable.invalidate() suppresses the
     * render request — damage accumulates and the render fires once from
     * layoutStateListener(false) when the pass completes.
     */
    private void applyNode(L node) {
        LD calculatedLayout = node.getCalculatedLayout();
        if (calculatedLayout == null) return;

        R renderable = node.getRenderable();
        boolean wasEffectivelyVisible = renderable.isEffectivelyVisible();

        if (calculatedLayout.hasRegion()) {
            applyParentAbsolutePosition(node, calculatedLayout);
        }

        renderable.applyLayoutData(calculatedLayout);
        renderable.advanceRenderPhase(RenderPhase.APPLYING);

        boolean becameVisible = !wasEffectivelyVisible && renderable.isEffectivelyVisible();
        if (becameVisible) {
            logSummaryDiagnostic(String.format(
                "[LayoutManager:%s] %s became visible — queueing subtree for follow-up pass",
                containerName, renderable.getName()));
            for (R child : renderable.getChildren()) {
                markLayoutDirtySubtree(child);
            }
        }

        committingNodes.add(renderable);
        node.clear();
    }

    private void applyParentAbsolutePosition(L node, LD calculatedLayout) {
        L parent = node.getParent();
        if (parent == null) return;

        LD parentCalculated = parent.getCalculatedLayout();
        if (parentCalculated != null && parentCalculated.hasRegion()) {
            calculatedLayout.getSpatialRegion().setParentAbsolutePosition(
                parentCalculated.getSpatialRegion().getAbsolutePosition());
            return;
        }

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

    void notifyRenderDispatched() {
        for (R r : committingNodes) r.advanceRenderPhase(RenderPhase.RENDERED);
        committingNodes.clear();
    }

    void clearIdleCommittingNodes() {
        for (R r : committingNodes) r.advanceRenderPhase(RenderPhase.IDLE);
        committingNodes.clear();
    }

    String summarizeCommittingNodes() { return summarizeRenderables(committingNodes); }

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
        if (isCurrentThread()) createGroupIfAbsent(groupId);
        else uiExecutor.executeFireAndForget(() -> createGroupIfAbsent(groupId));
    }

    private G createGroupIfAbsent(String groupId) {
        return groupRegistry.computeIfAbsent(groupId, id -> createEmptyGroup(id));
    }

    public void addToGroup(R renderable, String groupId) {
        if (isCurrentThread()) addToGroupInternal(renderable, groupId);
        else uiExecutor.executeFireAndForget(() -> addToGroupInternal(renderable, groupId));
    }

    private void addToGroupInternal(R renderable, String groupId) {
        L node  = renderableRegistry.get(renderable);
        G group = groupRegistry.computeIfAbsent(groupId, id -> createEmptyGroup(id));
        if (node == null) return;
        node.setMemberGroup(group);
        L parent = node.getParent();
        if (parent != null && !parent.getOwnedGroups().contains(group)) {
            parent.addOwnedGroup(group);
        }
        markLayoutDirty(renderable);
    }

    public void removeFromGroup(R renderable) {
        if (isCurrentThread()) removeFromGroupInternal(renderable);
        else uiExecutor.executeFireAndForget(() -> removeFromGroupInternal(renderable));
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
        if (isCurrentThread()) destroyGroupInternal(groupId);
        else uiExecutor.executeFireAndForget(() -> destroyGroupInternal(groupId));
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
        if (isCurrentThread()) setGroupLayoutCallbackInternal(groupId, callback);
        else uiExecutor.executeFireAndForget(() -> setGroupLayoutCallbackInternal(groupId, callback));
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

    void setFocusRequester(Consumer<R> focusRequester) {
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

    public void beginRequestBatch() { batchingRequests = true; }

    public void endRequestBatch() {
        batchingRequests = false;
        if (!dirtyLayoutNodes.isEmpty() || !dirtyFloatingNodes.isEmpty()) requestLayout();
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
            "debounce=%dms, avgExec=%.2fms, diagnosticMode=%s]",
            containerName, renderableRegistry.size(), groupRegistry.size(),
            dirtyLayoutNodes.size(), currentDebounceMs, avgExecutionTimeMs, diagnosticMode);
    }

    // =========================================================================
    // ADAPTIVE DEBOUNCE
    // =========================================================================

    private long calculateAdaptiveDebounce(double executionTimeMs, int dirtyNodeCount) {
        avgExecutionTimeMs = avgExecutionTimeMs == 0.0
            ? executionTimeMs
            : (SMOOTHING_ALPHA * executionTimeMs) + ((1.0 - SMOOTHING_ALPHA) * avgExecutionTimeMs);
        long executionBased  = (long)(avgExecutionTimeMs * EXECUTION_TIME_MULTIPLIER);
        long complexityBased = (dirtyNodeCount * DEBOUNCE_PER_NODE_MICROS) / 1000;
        long adaptive = Math.max(MIN_DEBOUNCE_MS,
            Math.min(MAX_DEBOUNCE_MS, MIN_DEBOUNCE_MS + executionBased + complexityBased));
        currentDebounceMs = adaptive;
        return adaptive;
    }

    public void setDebounceDelay(long delayMs) {
        if (delayMs < 0) throw new IllegalArgumentException("Delay must be non-negative");
        this.debounceDelayMs = delayMs;
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
        @Override public void markLayoutDirty(R r)              { RenderableLayoutManager.this.markLayoutDirty(r); }
        @Override public void markLayoutDirtyImmediate(R r)     { RenderableLayoutManager.this.markLayoutDirtyImmediate(r); }
        @Override public CompletableFuture<Void> flushLayout()  { return RenderableLayoutManager.this.flushLayout(); }
        @Override public void migrateToFloating(R r, R anchor)  { RenderableLayoutManager.this.migrateToFloating(r, anchor); }
        @Override public void migrateToRegular(R r)             { RenderableLayoutManager.this.migrateToRegular(r); }
        @Override public void registerChild(R child, LCB layoutCb) {
            RenderableLayoutManager.this.registerRenderable(child, layoutCb);
        }
        @Override public void unregister(R r)                   { RenderableLayoutManager.this.unregisterRenderable(r); }
        @Override public void beginBatch()                      { RenderableLayoutManager.this.beginRequestBatch(); }
        @Override public void endBatch()                        { RenderableLayoutManager.this.endRequestBatch(); }
        @Override public boolean isLayoutExecuting()            { return RenderableLayoutManager.this.isCurrentlyExecutingLayout(); }

        @Override public void requestFocus(R r) {
            consumeFocusDesired(r);
            if (focusRequester != null && r != null && r.isFocusable()) {
                if (pendingFocusRequest == r) pendingFocusRequest = null;
                focusRequester.accept(r);
            } else {
                pendingFocusRequest = r;
            }
        }

        @Override public void createLayoutGroup(String id)              { RenderableLayoutManager.this.createGroup(id); }
        @Override public void addToLayoutGroup(R r, String id)          { RenderableLayoutManager.this.addToGroup(r, id); }
        @Override public void removeLayoutGroupMember(R r)              { RenderableLayoutManager.this.removeFromGroup(r); }
        @Override public void destroyLayoutGroup(String id)             { RenderableLayoutManager.this.destroyGroup(id); }
        @Override public void setGroupLayoutCallback(String id, GCB cb) { RenderableLayoutManager.this.setGroupLayoutCallback(id, cb); }
        @Override public String getLayoutGroupIdByRenderable(R r)       { return RenderableLayoutManager.this.getRenderableGroupId(r); }
    }
}