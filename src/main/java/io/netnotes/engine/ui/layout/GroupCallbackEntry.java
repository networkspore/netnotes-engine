package io.netnotes.engine.ui.layout;

import io.netnotes.engine.utils.noteBytes.NoteUUID;

/**
 * GroupCallbackEntry - Encapsulates a group callback with its execution predicate and priority
 * 
 * This class combines:
 * - Unique callback ID
 * - Execution predicate (determines if callback should run)
 * - The callback itself
 * - Priority (lower numbers execute first)
 * 
 * Entries are sorted by priority to guarantee execution order.
 */
public class GroupCallbackEntry<
    G extends LayoutGroup<?,?,?,?,?,?,GCB,?,GCE, G>,
    GCB extends GroupLayoutCallback<?,?,?,?,?,?,?,GCB>,
    GCE extends GroupCallbackEntry<G,GCB,GCE>
> implements Comparable<GCE> {
    
    private final String callbackId;
    private final GroupCallbackPredicate<G> predicate;
    private final GCB callback;
    private final int priority;
    
    public GroupCallbackEntry(String callbackId, GCB callback){
        this.callbackId = callbackId;
        this.predicate = null;
        this.callback = callback;
        this.priority = 0;
    }

    public GroupCallbackEntry( GCB callback) {
        this(createCallbackUUID(), callback);
    }

    protected static String createCallbackUUID(){
        return "GCE-" + NoteUUID.createSafeUUID64();
    }

    public GroupCallbackEntry(
            GroupCallbackPredicate<G> predicate,
            GCB callback) {
        this(createCallbackUUID(), predicate, callback, 0);
    }

    /**
     * Create a callback entry with default priority (0)
     */
    public GroupCallbackEntry(
            String callbackId,
            GroupCallbackPredicate<G> predicate,
            GCB callback) {
        this(callbackId, predicate, callback, 0);
    }
    
    /**
     * Create a callback entry with specified priority
     * 
     * @param callbackId Unique identifier for this callback
     * @param predicate Condition that determines if callback should execute
     * @param callback The callback to execute
     * @param priority Execution priority (lower numbers execute first)
     */
    public GroupCallbackEntry(
            String callbackId,
            GroupCallbackPredicate<G> predicate,
            GCB callback,
            int priority) {
        this.callbackId = callbackId;
        this.predicate = predicate;
        this.callback = callback;
        this.priority = priority;
    }
    
    public String getCallbackId() {
        return callbackId;
    }
    
    public GroupCallbackPredicate<G> getPredicate() {
        return predicate;
    }
    
    public GCB getCallback() {
        return callback;
    }
    
    public int getPriority() {
        return priority;
    }
    
    /**
     * Test if this callback should execute
     */
    public boolean shouldExecute(G group) {
        if(predicate == null){
            return true;
        }
        return predicate.shouldExecute(group, callbackId);
    }
    
    @Override
    public int compareTo(GCE other) {
  
        // Sort by priority (lower priority executes first)
        int priorityCompare = Integer.compare(this.priority, other.getPriority());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        
        // If priorities are equal, sort by callback ID for deterministic ordering
        return this.callbackId.compareTo(other.getCallbackId());
    }
    

    @SuppressWarnings("unchecked")
    @Override
        public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        GCE other = (GCE) obj;
        
        return callbackId.equals(other.getCallbackId());
    }
    
    @Override
    public int hashCode() {
        return callbackId.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("GroupCallbackEntry[id=%s, priority=%d]", callbackId, priority);
    }

 
}