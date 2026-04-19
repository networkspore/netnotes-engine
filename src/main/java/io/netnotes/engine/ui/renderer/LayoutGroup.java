package io.netnotes.engine.ui.renderer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.renderer.layout.GroupLayoutCallback;

/**
 * LayoutGroup - A set of sibling renderables laid out together by a shared
 * callback, owned by their common parent node.
 *
 * TWO EXECUTION MODES:
 *
 *   executeContentCallbacks() — fires each member's contentCallback on-demand
 *     during bottom-up measurement. Triggered when a parent asks for a
 *     content-sized member's size via context.getMeasuredSize(). Gated by
 *     contentMeasuredThisPass so the group only measures once per pass
 *     regardless of how many ancestors request measurement.
 *
 *   executeLayoutCallback() — fires the group's single layout callback
 *     top-down after the owner node is committed. The callback distributes
 *     space across all members, reading content-sized members' already-
 *     measured sizes as fixed constraints and setting remaining axes freely.
 *
 * NO PREDICATE SYSTEM:
 * The old priority-sorted predicate infrastructure is replaced by the two
 * execution modes. The predicate was always implicitly "is this a content
 * measurement or a layout pass?" which is now structural.
 *
 * OWNERSHIP:
 * Each group has exactly one owner — the parent LayoutNode whose direct
 * children are the group's members. The owner fires the layout callback
 * after its own commit. Content callbacks are triggered on-demand via
 * getMeasuredSize() in LayoutContext.
 *
 * LAYOUT DATA ACCESS:
 * The LayoutDataInterface pattern avoids generic pollution by closing over
 * the correctly-typed node in an anonymous implementation. The interface is
 * declared at the right generic scope so no casting is needed anywhere.
 *
 * SINGLE COMMIT INVARIANT:
 * Group callbacks only populate members' calculatedLayout. applyNode()
 * commits each member exactly once in depth order. Groups never call apply.
 */
public abstract class LayoutGroup<
    B extends BatchBuilder<S>,
    R extends Renderable<B,P,S,L,LC,LD,?,GCB,?,G,R>,
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    LD extends LayoutData<B,R,S,LD,?>,
    LC extends LayoutContext<B,R,P,S,LD,?,LC,L>,
    GCB extends GroupLayoutCallback<B,R,P,S,LD,LC,L,GCB>,
    L extends LayoutNode<B,R,P,S,LD,LC,?,GCB,G,L>,
    G extends LayoutGroup<B,R,P,S,LD,LC,GCB,L,G>
> {
    private final String groupId;

    /** The parent node that owns this group. Set at registration. */
    private L owner = null;

    private final List<L> members = new ArrayList<>();

    /**
     * Interface-based access to each member's calculatedLayout, keyed by name.
     * Anonymous implementations close over the correctly-typed L node so no
     * casting is needed. Passed to the layout callback so it can read and write
     * member geometry without needing direct access to LayoutNode internals.
     */
    private final Map<String, LayoutDataInterface<LD>> layoutDataInterfaces = new LinkedHashMap<>();

    /**
     * The single layout callback for this group.
     * Fires top-down after the owner node is committed.
     * Receives all member contexts and the layoutData interface map.
     */
    private GCB layoutCallback = null;


    public LayoutGroup(String groupId) {
        this.groupId = groupId;
    }

    // ── Owner ─────────────────────────────────────────────────────────────────

    public void setOwner(L owner)  { this.owner = owner; }
    public L    getOwner()         { return owner; }

    // ── Member management ─────────────────────────────────────────────────────

    public void addMember(L node) {
        if (members.contains(node)) return;
        members.add(node);

        // Anonymous implementation closes over the correctly-typed node.
        // No casting required anywhere — generics stay clean.
        layoutDataInterfaces.put(node.getName(), new LayoutDataInterface<LD>() {
            private final L layoutNode = node;

            @Override
            public String getName() {
                return layoutNode.getName();
            }

            @Override
            public boolean hasLayoutData() {
                return layoutNode.calculatedLayout != null;
            }

            @Override
            public LD getLayoutData() {
                return layoutNode.calculatedLayout;
            }

            @Override
            public void setLayoutData(LD data) {
                layoutNode.calculatedLayout = data;
            }
        });
    }

    public void removeMember(L node) {
        members.remove(node);
        layoutDataInterfaces.remove(node.getName());
    }

    public void clearMembers() {
        members.clear();
        layoutDataInterfaces.clear();
    }

    public void cleanup() {
        clearMembers();
        layoutCallback = null;
        owner = null;
    }

    // ── Layout callback ───────────────────────────────────────────────────────

    public void setLayoutCallback(GCB callback)  { this.layoutCallback = callback; }
    public GCB  getLayoutCallback()              { return layoutCallback; }
    public boolean hasLayoutCallback()           { return layoutCallback != null; }

    // ── Content execution (bottom-up, on-demand) ──────────────────────────────

    // ── Layout execution (top-down, after owner commits) ──────────────────────

    /**
     * Fire the layout callback for this group.
     *
     * Called by the manager after the owner node is committed, so the callback
     * has valid parent geometry. Content-sized members will already have their
     * content axes populated in calculatedLayout from the bottom-up measurement
     * phase. The callback reads those as fixed constraints via hasLayoutData()
     * and getLayoutData(), and sets remaining axes freely via setLayoutData().
     *
     * All decisions about what to read and write belong to the callback.
     * The layout system enforces nothing about axis overlap.
     *
     * @param contexts one freshly initialised context per member, in member order
     */
    public void executeLayoutCallback(LC[] contexts) {
        if (layoutCallback == null) return;
        layoutCallback.calculate(contexts, layoutDataInterfaces);
    }


    // ── Queries ───────────────────────────────────────────────────────────────

    public List<L>                              getMembers()              { return members; }
    public int                                  getMemberCount()          { return members.size(); }
    public String                               getGroupId()              { return groupId; }
    public boolean                              isEmpty()                 { return members.isEmpty(); }
    public Map<String, LayoutDataInterface<LD>> getLayoutDataInterfaces() { return layoutDataInterfaces; }

    @SuppressWarnings("unchecked")
    protected G self() { return (G) this; }

    // ── LayoutDataInterface ───────────────────────────────────────────────────

    /**
     * Interface giving the layout callback read/write access to a member's
     * calculatedLayout without exposing LayoutNode internals or requiring casts.
     *
     * hasLayoutData() lets the callback detect content-sized members that were
     * already measured bottom-up. The callback reads that measurement as a
     * fixed size constraint and decides what to set on remaining axes —
     * the layout system enforces nothing about what may or may not be written.
     * All axis decisions belong to the callback.
     */
    public interface LayoutDataInterface<LD> {
        String  getName();
        boolean hasLayoutData();
        LD      getLayoutData();
        void    setLayoutData(LD data);
    }
}