package io.netnotes.engine.state;

import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Generic statechart system using bitflags for orthogonal states.
 * States are represented as long values where each bit represents an independent state.
 *
 * Features:
 * - 64 independent state bits
 * - Transition guards and actions
 * - State change callbacks
 * - Full NoteBytes serialization
 * - Thread-safe
 */
public class BitFlagStateMachine {

    private final String m_id;
    private long m_state;
    private final Map<Long, List<StateTransition>> m_transitions;
    private final Map<Long, List<BiConsumer<Long, Long>>> m_stateListeners;
    private final List<BiConsumer<Long, Long>> m_globalListeners;
    private BiConsumer<Exception, BiConsumer<Long, Long>> m_errorHandler;

    public BitFlagStateMachine(String id) {
        this(id, 0L);
    }

    public BitFlagStateMachine(String id, long initialState) {
        this.m_id = id;
        this.m_state = initialState;
        this.m_transitions = new ConcurrentHashMap<>();
        this.m_stateListeners = new ConcurrentHashMap<>();
        this.m_globalListeners = new CopyOnWriteArrayList<>();
    }

    // ========== State Queries ==========

    public boolean hasState(long stateBit) {
        return (m_state & stateBit) == stateBit;
    }

    public boolean hasAnyState(long... stateBits) {
        for (long bit : stateBits) {
            if (hasState(bit)) return true;
        }
        return false;
    }

    public boolean hasAllStates(long... stateBits) {
        for (long bit : stateBits) {
            if (!hasState(bit)) return false;
        }
        return true;
    }

    public long getState() {
        return m_state;
    }

    public String getId() {
        return m_id;
    }

    // ========== State Mutations ==========

    public boolean addState(long stateBit) {
        if (hasState(stateBit)) {
            return false; // Already has state
        }

        long oldState = m_state;
        m_state |= stateBit;

        notifyStateChange(oldState, m_state, stateBit, true);
        checkTransitions(stateBit, true, oldState);

        return true;
    }

    public boolean removeState(long stateBit) {
        if (!hasState(stateBit)) {
            return false; // Doesn't have state
        }

        long oldState = m_state;
        m_state &= ~stateBit;

        notifyStateChange(oldState, m_state, stateBit, false);
        checkTransitions(stateBit, false, oldState);

        return true;
    }

    public boolean toggleState(long stateBit) {
        if (hasState(stateBit)) {
            return removeState(stateBit);
        } else {
            return addState(stateBit);
        }
    }

    public void setState(long newState) {
        long oldState = m_state;
        if (oldState == newState) return;

        m_state = newState;
        notifyStateChange(oldState, newState, 0L, false);

        // Check transitions for all changed bits
        long changed = oldState ^ newState;
        for (int i = 0; i < 64; i++) {
            long bit = 1L << i;
            if ((changed & bit) != 0) {
                boolean added = (newState & bit) != 0;
                checkTransitions(bit, added, oldState);
            }
        }
    }

    public void clearAllStates() {
        setState(0L);
    }

    // ========== Transitions ==========

    public static class StateTransition {
        public final long triggerBit;
        public final boolean onAdd;
        public final TransitionGuard guard;
        public final TransitionAction action;

        public StateTransition(long triggerBit, boolean onAdd,
                               TransitionGuard guard, TransitionAction action) {
            this.triggerBit = triggerBit;
            this.onAdd = onAdd;
            this.guard = guard;
            this.action = action;
        }
    }

   
    @FunctionalInterface
    public interface TransitionGuard {
        boolean canTransition(long oldState, long newState, long triggerBit);
    }

    @FunctionalInterface
    public interface TransitionAction {
        void onTransition(long oldState, long newState, long triggerBit);
    }

    public void addTransition(long triggerBit, boolean onAdd,
                              TransitionGuard guard, TransitionAction action) {
        StateTransition transition = new StateTransition(triggerBit, onAdd, guard, action);
        m_transitions.computeIfAbsent(triggerBit, k -> new CopyOnWriteArrayList<>()).add(transition);
    }

    public void onStateAdded(long stateBit, TransitionAction action) {
        addTransition(stateBit, true, (oldState, newState, bit) -> true, action);
    }

    public void onStateRemoved(long stateBit, TransitionAction action) {
        addTransition(stateBit, false, (oldState, newState, bit) -> true, action);
    }

    public void onStateAddedIf(long stateBit, TransitionGuard guard, TransitionAction action) {
        addTransition(stateBit, true, guard, action);
    }

    private void checkTransitions(long triggerBit, boolean isAdd, long oldState) {
        List<StateTransition> transitions = m_transitions.get(triggerBit);
        if (transitions == null) return;

        long newState = m_state;
        for (StateTransition transition : transitions) {
            if (transition.onAdd == isAdd) {
                if (transition.guard == null || transition.guard.canTransition(oldState, newState, triggerBit)) {
                    if (transition.action != null) {
                        transition.action.onTransition(oldState, newState, triggerBit);
                    }
                }
            }
        }
    }

    // ========== Listeners ==========

    public void addStateListener(long stateBit, BiConsumer<Long, Long> listener) {
        m_stateListeners.computeIfAbsent(stateBit, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    /** add one listener for multiple bits */
    public void addStateListener(long[] bits, BiConsumer<Long, Long> listener) {
        for (long bit : bits) {
            addStateListener(bit, listener);
        }
    }

    public void addGlobalListener(BiConsumer<Long, Long> listener) {
        m_globalListeners.add(listener);
    }

    /** on any state change (alias of addGlobalListener) */
    public void onStateChanged(BiConsumer<Long, Long> listener) {
        addGlobalListener(listener);
    }

    public void removeStateListener(long stateBit, BiConsumer<Long, Long> listener) {
        List<BiConsumer<Long, Long>> listeners = m_stateListeners.get(stateBit);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public void removeGlobalListener(BiConsumer<Long, Long> listener) {
        m_globalListeners.remove(listener);
    }

    /** clears all listeners and transitions */
    public void clearListeners() {
        m_globalListeners.clear();
        m_stateListeners.clear();
    }

    public void clearTransitions() {
        m_transitions.clear();
    }

    public void setErrorHandler(BiConsumer<Exception, BiConsumer<Long, Long>> handler) {
        this.m_errorHandler = handler;
    }

    private void handleListenerError(Exception e, BiConsumer<Long, Long> listener) {
        if (m_errorHandler != null) {
            m_errorHandler.accept(e, listener);
        } else {
            System.err.println("Error in state machine listener: " + e.getMessage());
        }
    }

    private void notifyStateChange(long oldState, long newState, long changedBit, boolean targetedChange) {
        for (BiConsumer<Long, Long> listener : m_globalListeners) {
            try {
                listener.accept(oldState, newState);
            } catch (Exception e) {
                handleListenerError(e, listener);
            }
        }

        if (targetedChange && changedBit != 0) {
            List<BiConsumer<Long, Long>> listeners = m_stateListeners.get(changedBit);
            if (listeners != null) {
                for (BiConsumer<Long, Long> listener : listeners) {
                    try {
                        listener.accept(oldState, newState);
                    } catch (Exception e) {
                        handleListenerError(e, listener);
                    }
                }
            }
        } else {
            long changed = oldState ^ newState;
            for (int i = 0; i < 64; i++) {
                long bit = 1L << i;
                if ((changed & bit) != 0) {
                    List<BiConsumer<Long, Long>> listeners = m_stateListeners.get(bit);
                    if (listeners != null) {
                        for (BiConsumer<Long, Long> listener : listeners) {
                            try {
                                listener.accept(oldState, newState);
                            } catch (Exception e) {
                                handleListenerError(e, listener);
                            }
                        }
                    }
                }
            }
        }
    }

    // ========== NoteBytes Serialization ==========

    public NoteBytesObject toNoteBytes() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("id", new NoteString(m_id)),
            new NoteBytesPair("state", new NoteLong(m_state))
        });
    }

    public static BitFlagStateMachine fromNoteBytes(NoteBytesObject obj) {
        NoteBytesMap map = obj.getAsNoteBytesMap();
        String id = map.get("id").getAsString();
        long state = map.get("state").getAsLong();
        return new BitFlagStateMachine(id, state);
    }

    // ========== Utility Methods ==========

    public List<Long> getActiveStates() {
        List<Long> active = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            long bit = 1L << i;
            if (hasState(bit)) {
                active.add(bit);
            }
        }
        return active;
    }

    public int getHighestStateBit() {
        return 63 - Long.numberOfLeadingZeros(m_state);
    }

    public int getActiveStateCount() {
        return Long.bitCount(m_state);
    }

    public String getStateString(Map<Long, String> stateNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (int i = 0; i < 64; i++) {
            long bit = 1L << i;
            if (hasState(bit)) {
                if (!first) sb.append(", ");
                String name = stateNames.getOrDefault(bit, "BIT_" + i);
                sb.append(name);
                first = false;
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("StateMachine[id=%s, state=0x%016X, active=%d]",
                m_id, m_state, getActiveStateCount());
    }

    // ========== Static Helpers ==========

    public static long bit(int position) {
        if (position < 0 || position >= 64) {
            throw new IllegalArgumentException("Bit position must be 0-63");
        }
        return 1L << position;
    }

    public static long combine(long... bits) {
        long result = 0L;
        for (long bit : bits) {
            result |= bit;
        }
        return result;
    }

    public static boolean anySet(long state, long mask) {
        return (state & mask) != 0;
    }

    public static boolean allSet(long state, long mask) {
        return (state & mask) == mask;
    }

    /** bits that differ between a and b */
    public static long difference(long a, long b) {
        return a ^ b;
    }

    /** indices of set bits */
    public static List<Integer> bitIndices(long mask) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if (((mask >> i) & 1L) != 0) {
                indices.add(i);
            }
        }
        return indices;
    }

    /** formatted 64-bit binary string */
    public static String bitString(long mask) {
        StringBuilder sb = new StringBuilder(64);
        for (int i = 63; i >= 0; i--) {
            sb.append(((mask >> i) & 1L) == 1L ? '1' : '0');
        }
        return sb.toString();
    }
}
