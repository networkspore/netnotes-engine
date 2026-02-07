package io.netnotes.engine.ui.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

import io.netnotes.engine.ui.BatchBuilder;
import io.netnotes.engine.ui.Renderable;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;

/**
 * LayoutGroup - Collection of renderables calculated together
 * 
 * Groups allow multiple UI elements to be laid out as a cohesive unit,
 * where each element may need information about its siblings.
 * 
 * CALLBACK STORAGE:
 * - Callbacks are registered on the GROUP, not individual nodes
 * - All members share the same callbacks
 * - Predicates determine which callbacks execute
 * 
 * EXECUTION MODEL:
 * - When first member is reached, executeCallbacks() is called
 * - Callbacks execute if their predicates pass
 * - Each callback receives ALL group members
 * - Callbacks set calculatedLayout on each member
 */
public abstract class LayoutGroup<
    B extends BatchBuilder<S>,
    R extends Renderable<B,P,S,LC,LD,?,GCB,GCE,?,G,R>,
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    LD extends LayoutData<B,R,S,LD,?>,
    LC extends LayoutContext<B,R,P,S,LD,?,LC,L>,
    GCB extends GroupLayoutCallback<B,R,P,S,LD,LC,L,GCB>,
    L extends LayoutNode<B,R,P,S,LD,LC,?,GCB,G,L>,
    GCE extends GroupCallbackEntry<G,GCB,GCE> ,
    G extends LayoutGroup<B,R,P,S,LD,LC,GCB,L,GCE,G>
> {
    private final String groupId;
    private final List<L> members = new ArrayList<>();
    
    // Map for fast lookup by callback ID
    private final Map<String, GCE> callbackMap = new HashMap<>();
    
    //LayoutDataName, LayoutData interface (from layoutNode)
    protected final HashMap<String,LayoutDataInterface<LD>> layoutDataInterfaces = new HashMap<>();
    
    // Set for sorted iteration (sorted by priority)
    private final NavigableSet<GCE> callbackSet = new TreeSet<>();
    
    private int passCounter = 0;
    private int minDepth = Integer.MAX_VALUE;
    
    public LayoutGroup(String groupId) {
        this.groupId = groupId;
    }
    
    // ===== MEMBER MANAGEMENT =====
    
    public void addMember(L node) {
        if (!members.contains(node)) {
            members.add(node);

            layoutDataInterfaces.put(node.getName(), new LayoutDataInterface<LD>() {
                private L layoutNode = node;
                
                @Override
                public String getLayoutDataName() {
                    return layoutNode.getName();
                }

                @Override
                public LD getLayoutData() {
                    return layoutNode.calculatedLayout;
                }

                @Override
                public void setLayoutData(LD layoutData) {
                    layoutNode.calculatedLayout = layoutData;
                }
            });

            minDepth = Math.min(minDepth, node.getDepth());
        }
    }
    
    public void removeMember(L node) {
        members.remove(node);
        layoutDataInterfaces.remove(node.getName());
        if (members.isEmpty()) {
            reset();
        } else {
            recalculateMinDepth();
        }
    }

    public void clearMembers(){
        members.clear();
        layoutDataInterfaces.clear();
        reset();
    }

    public void cleanup(){
        clearMembers();
        clearCallbacks();
    }
    
    private void recalculateMinDepth() {
        minDepth = Integer.MAX_VALUE;
        for (L member : members) {
            minDepth = Math.min(minDepth, member.getDepth());
        }
    }
    
    // ===== CALLBACK MANAGEMENT =====

    /**
     * Register a group callback with predicate and priority
     * 
     * @param callbackId Unique identifier for this callback
     * @param predicate Condition that determines if callback should execute
     * @param callback The callback to execute for the group
     * @param priority Execution priority (lower numbers execute first)
     */
    public void registerCallback(GCE entry) {
        // Remove old entry if exists
        GCE oldEntry = callbackMap.remove(entry.getCallbackId());
        if (oldEntry != null) {
            callbackSet.remove(oldEntry);
        }

        callbackMap.put(entry.getCallbackId(),  entry);
        callbackSet.add(entry);
    }
    
    /**
     * Unregister a callback
     */
    public void unregisterCallback(String callbackId) {
        GCE entry = callbackMap.remove(callbackId);
        if (entry != null) {
            callbackSet.remove(entry);
        }
    }
    
    /**
     * Clear all callbacks
     */
    public void clearCallbacks() {
        callbackMap.clear();
        callbackSet.clear();
    }
    
    /**
     * Check if a callback is registered
     */
    public boolean hasCallback(String callbackId) {
        return callbackMap.containsKey(callbackId);
    }
    
    /**
     * Get a callback entry by ID
     */
    public GCE getCallback(String callbackId) {
        return callbackMap.get(callbackId);
    }
    
    /**
     * Execute all callbacks whose predicates pass, in priority order
     * Called when the first group member is encountered during layout
     * 
     * @param context Layout context (from first encoutnered member)
     */
    public void executeCallbacks(LC[] contexts) {
      
        // Execute each callback whose predicate passes
        for (GCE entry : callbackSet) {
            // Check if predicate allows execution
            if (entry.shouldExecute(self())) {
                // Execute the group callback with all members
                entry.getCallback().calculate(contexts, layoutDataInterfaces);
            }
        }
    }

    public boolean hasAnyCallbacks(){
        return !callbackSet.isEmpty();
    }

    LD getLayoutData(String nodeName){
        LayoutDataInterface<LD> dataInterface = layoutDataInterfaces.get(nodeName);
        if(dataInterface != null){
            return dataInterface.getLayoutData();
        }
        return null;
    }

    public interface LayoutDataInterface<LD>{
        String getLayoutDataName();
        LD getLayoutData();
        void setLayoutData(LD layoutData);
    }

    @SuppressWarnings("unchecked")
    protected G self() {
        G self = (G) this;
        return self;
    }
    
    // ===== PASS MANAGEMENT =====
    
    public void incrementPass() {
        passCounter++;
        if (passCounter == members.size()) {
            clearCalculatedData();
        }
    }
    
    private void clearCalculatedData() {
        for (L member : members) {
            member.clearGroupCalculation();
        }
        reset();
    }
    
    public void reset() {
        passCounter = 0;
    }
    
    public int getPassCounter() {
        return passCounter;
    }
    
    // ===== STATE QUERIES =====
    

    public List<L> getMembers() {
        return members;
    }
    
    public int getMemberCount() {
        return members.size();
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public int getMinDepth() {
        return minDepth;
    }
    
    public boolean isEmpty() {
        return members.isEmpty();
    }
    
    public int getCallbackCount() {
        return callbackSet.size();
    }
    
    // == Group LayoutCallback API ==

    public void setLayoutDataForMember(L member, LD layoutData) {
        
        member.calculatedLayout = layoutData;
              
    }
}