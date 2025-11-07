package io.netnotes.engine.state;

import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Generic statechart system using BigInteger for unlimited orthogonal states.
 * States are represented as BigInteger values where each bit represents an independent state.
 *
 * Features:
 * - Unlimited independent state bits (not limited to 64)
 * - Transition guards and actions
 * - State change callbacks
 * - Full NoteBytes serialization
 * - Thread-safe
 * - Backward compatible with long-based bit positions
 */
public class BitFlagStateMachine {

    private final String m_id;
    private BigInteger m_state;
    private final Map<BigInteger, List<StateTransition>> m_transitions;
    private final Map<BigInteger, List<BiConsumer<BigInteger, BigInteger>>> m_stateListeners;
    private final List<BiConsumer<BigInteger, BigInteger>> m_globalListeners;
    private final List<StateConstraint> m_constraints;
    private BiConsumer<Exception, BiConsumer<BigInteger, BigInteger>> m_errorHandler;

    public BitFlagStateMachine(String id) {
        this(id, BigInteger.ZERO);
    }

    public BitFlagStateMachine(String id, BigInteger initialState) {
        this.m_id = id;
        this.m_state = initialState;
        this.m_transitions = new ConcurrentHashMap<>();
        this.m_stateListeners = new ConcurrentHashMap<>();
        this.m_globalListeners = new CopyOnWriteArrayList<>();
        this.m_constraints = new CopyOnWriteArrayList<>();
    }

    // Convenience constructor for long-based initial states
    public BitFlagStateMachine(String id, long initialState) {
        this(id, BigInteger.valueOf(initialState));
    }

    // ========== State Queries ==========

    public boolean hasState(BigInteger stateBit) {
        return m_state.and(stateBit).equals(stateBit);
    }

    public boolean hasState(int bitPosition) {
        return m_state.testBit(bitPosition);
    }

    public boolean hasState(long stateBit) {
        return hasState(BigInteger.valueOf(stateBit));
    }

    public boolean hasAnyState(BigInteger... stateBits) {
        for (BigInteger bit : stateBits) {
            if (hasState(bit)) return true;
        }
        return false;
    }

    public boolean hasAnyState(int... bitPositions) {
        for (int pos : bitPositions) {
            if (hasState(pos)) return true;
        }
        return false;
    }

    public boolean hasAllStates(BigInteger... stateBits) {
        for (BigInteger bit : stateBits) {
            if (!hasState(bit)) return false;
        }
        return true;
    }

    public boolean hasAllStates(int... bitPositions) {
        for (int pos : bitPositions) {
            if (!hasState(pos)) return false;
        }
        return true;
    }

    // Convenience aliases
    public boolean hasAllFlags(BigInteger flags) {
        return m_state.and(flags).equals(flags);
    }

    public boolean hasAllFlags(long flags) {
        return hasAllFlags(BigInteger.valueOf(flags));
    }

    public boolean hasAnyFlags(BigInteger flags) {
        return !m_state.and(flags).equals(BigInteger.ZERO);
    }

    public boolean hasAnyFlags(long flags) {
        return hasAnyFlags(BigInteger.valueOf(flags));
    }

    public boolean hasFlag(BigInteger flag) {
        return hasState(flag);
    }

    public boolean hasFlag(int bitPosition) {
        return hasState(bitPosition);
    }

    public boolean hasFlag(long flag) {
        return hasState(flag);
    }

    public BigInteger getState() {
        return m_state;
    }

    public String getId() {
        return m_id;
    }

    // ========== State Mutations ==========

    public boolean addState(BigInteger stateBit) {
        if (hasState(stateBit)) {
            return false; // Already has state
        }

        BigInteger oldState = m_state;
        BigInteger newState = m_state.or(stateBit);
        
        validateStateConstraints(newState);
        m_state = newState;

        notifyStateChange(oldState, m_state, stateBit, true);
        checkTransitions(stateBit, true, oldState);

        return true;
    }

    public boolean addState(int bitPosition) {
        return addState(bit(bitPosition));
    }

    public boolean addState(long stateBit) {
        return addState(BigInteger.valueOf(stateBit));
    }

    public boolean removeState(BigInteger stateBit) {
        if (!hasState(stateBit)) {
            return false; // Doesn't have state
        }

        BigInteger oldState = m_state;
        m_state = m_state.andNot(stateBit);

        notifyStateChange(oldState, m_state, stateBit, false);
        checkTransitions(stateBit, false, oldState);

        return true;
    }

    public boolean removeState(int bitPosition) {
        return removeState(bit(bitPosition));
    }

    public boolean removeState(long stateBit) {
        return removeState(BigInteger.valueOf(stateBit));
    }

    public boolean toggleState(BigInteger stateBit) {
        if (hasState(stateBit)) {
            return removeState(stateBit);
        } else {
            return addState(stateBit);
        }
    }

    public boolean toggleState(int bitPosition) {
        return toggleState(bit(bitPosition));
    }

    public boolean toggleState(long stateBit) {
        return toggleState(BigInteger.valueOf(stateBit));
    }

    public void setState(BigInteger newState) {
        BigInteger oldState = m_state;
        if (oldState.equals(newState)) return;

        validateStateConstraints(newState);
        m_state = newState;
        notifyStateChange(oldState, newState, BigInteger.ZERO, false);

        // Check transitions for all changed bits
        BigInteger changed = oldState.xor(newState);
        int bitLength = Math.max(oldState.bitLength(), newState.bitLength());
        
        for (int i = 0; i < bitLength; i++) {
            if (changed.testBit(i)) {
                BigInteger bitValue = bit(i);
                boolean added = newState.testBit(i);
                checkTransitions(bitValue, added, oldState);
            }
        }
    }

    public void setState(long newState) {
        setState(BigInteger.valueOf(newState));
    }

    public void setFlag(BigInteger flag) {
        addState(flag);
    }

    public void setFlag(int bitPosition) {
        addState(bitPosition);
    }

    public void setFlag(long flag) {
        addState(flag);
    }

    public void clearFlag(BigInteger flag) {
        removeState(flag);
    }

    public void clearFlag(int bitPosition) {
        removeState(bitPosition);
    }

    public void clearFlag(long flag) {
        removeState(flag);
    }

    public void clearAllStates() {
        setState(BigInteger.ZERO);
    }

    // ========== State Constraints ==========

    public static class StateConstraint {
        public final BigInteger mutuallyExclusiveStates;
        public final String errorMessage;

        public StateConstraint(BigInteger mutuallyExclusiveStates, String errorMessage) {
            this.mutuallyExclusiveStates = mutuallyExclusiveStates;
            this.errorMessage = errorMessage;
        }
    }

    public void addStateConstraint(BigInteger mutuallyExclusiveStates, String errorMessage) {
        m_constraints.add(new StateConstraint(mutuallyExclusiveStates, errorMessage));
    }

    public void addStateConstraint(long mutuallyExclusiveStates, String errorMessage) {
        addStateConstraint(BigInteger.valueOf(mutuallyExclusiveStates), errorMessage);
    }

    private void validateStateConstraints(BigInteger newState) {
        for (StateConstraint constraint : m_constraints) {
            BigInteger masked = newState.and(constraint.mutuallyExclusiveStates);
            if (masked.bitCount() > 1) {
                throw new IllegalStateException(constraint.errorMessage);
            }
        }
    }

    // ========== Transitions ==========

    public static class StateTransition {
        public final BigInteger triggerBit;
        public final boolean onAdd;
        public final TransitionGuard guard;
        public final TransitionAction action;

        public StateTransition(BigInteger triggerBit, boolean onAdd,
                               TransitionGuard guard, TransitionAction action) {
            this.triggerBit = triggerBit;
            this.onAdd = onAdd;
            this.guard = guard;
            this.action = action;
        }
    }

    @FunctionalInterface
    public interface TransitionGuard {
        boolean canTransition(BigInteger oldState, BigInteger newState, BigInteger triggerBit);
    }

    @FunctionalInterface
    public interface TransitionAction {
        void onTransition(BigInteger oldState, BigInteger newState, BigInteger triggerBit);
    }

    public void addTransition(BigInteger triggerBit, boolean onAdd,
                              TransitionGuard guard, TransitionAction action) {
        StateTransition transition = new StateTransition(triggerBit, onAdd, guard, action);
        m_transitions.computeIfAbsent(triggerBit, k -> new CopyOnWriteArrayList<>()).add(transition);
    }

    public void onStateAdded(BigInteger stateBit, TransitionAction action) {
        addTransition(stateBit, true, (oldState, newState, bit) -> true, action);
    }

    public void onStateAdded(int bitPosition, TransitionAction action) {
        onStateAdded(bit(bitPosition), action);
    }

    public void onStateAdded(long stateBit, TransitionAction action) {
        onStateAdded(BigInteger.valueOf(stateBit), action);
    }

    public void onStateRemoved(BigInteger stateBit, TransitionAction action) {
        addTransition(stateBit, false, (oldState, newState, bit) -> true, action);
    }

    public void onStateRemoved(int bitPosition, TransitionAction action) {
        onStateRemoved(bit(bitPosition), action);
    }

    public void onStateRemoved(long stateBit, TransitionAction action) {
        onStateRemoved(BigInteger.valueOf(stateBit), action);
    }

    public void onStateAddedIf(BigInteger stateBit, TransitionGuard guard, TransitionAction action) {
        addTransition(stateBit, true, guard, action);
    }

    // Convenient transition for exact state matches
    public void addTransition(BigInteger fromState, BigInteger toState, BiConsumer<BigInteger, BigInteger> action) {
        addGlobalListener((oldState, newState) -> {
            if (oldState.equals(fromState) && newState.equals(toState)) {
                action.accept(oldState, newState);
            }
        });
    }

    public void addTransition(long fromState, long toState, BiConsumer<BigInteger, BigInteger> action) {
        addTransition(BigInteger.valueOf(fromState), BigInteger.valueOf(toState), action);
    }

    private void checkTransitions(BigInteger triggerBit, boolean isAdd, BigInteger oldState) {
        List<StateTransition> transitions = m_transitions.get(triggerBit);
        if (transitions == null) return;

        BigInteger newState = m_state;
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

    public void addStateListener(BigInteger stateBit, BiConsumer<BigInteger, BigInteger> listener) {
        m_stateListeners.computeIfAbsent(stateBit, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void addStateListener(int bitPosition, BiConsumer<BigInteger, BigInteger> listener) {
        addStateListener(bit(bitPosition), listener);
    }

    public void addStateListener(long stateBit, BiConsumer<BigInteger, BigInteger> listener) {
        addStateListener(BigInteger.valueOf(stateBit), listener);
    }

    /** add one listener for multiple bits */
    public void addStateListener(BigInteger[] bits, BiConsumer<BigInteger, BigInteger> listener) {
        for (BigInteger bit : bits) {
            addStateListener(bit, listener);
        }
    }

    public void addStateListener(int[] bitPositions, BiConsumer<BigInteger, BigInteger> listener) {
        for (int pos : bitPositions) {
            addStateListener(pos, listener);
        }
    }

    public void addStateListener(long[] stateBits, BiConsumer<BigInteger, BigInteger> listener) {
        for (long bit : stateBits) {
            addStateListener(bit, listener);
        }
    }

    public void addGlobalListener(BiConsumer<BigInteger, BigInteger> listener) {
        m_globalListeners.add(listener);
    }

    /** on any state change (alias of addGlobalListener) */
    public void onStateChanged(BiConsumer<BigInteger, BigInteger> listener) {
        addGlobalListener(listener);
    }

    public void removeStateListener(BigInteger stateBit, BiConsumer<BigInteger, BigInteger> listener) {
        List<BiConsumer<BigInteger, BigInteger>> listeners = m_stateListeners.get(stateBit);
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    public void removeGlobalListener(BiConsumer<BigInteger, BigInteger> listener) {
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

    public void setErrorHandler(BiConsumer<Exception, BiConsumer<BigInteger, BigInteger>> handler) {
        this.m_errorHandler = handler;
    }

    private void handleListenerError(Exception e, BiConsumer<BigInteger, BigInteger> listener) {
        if (m_errorHandler != null) {
            m_errorHandler.accept(e, listener);
        } else {
            System.err.println("Error in state machine listener: " + e.getMessage());
        }
    }

    private void notifyStateChange(BigInteger oldState, BigInteger newState, BigInteger changedBit, boolean targetedChange) {
        for (BiConsumer<BigInteger, BigInteger> listener : m_globalListeners) {
            try {
                listener.accept(oldState, newState);
            } catch (Exception e) {
                handleListenerError(e, listener);
            }
        }

        if (targetedChange && !changedBit.equals(BigInteger.ZERO)) {
            List<BiConsumer<BigInteger, BigInteger>> listeners = m_stateListeners.get(changedBit);
            if (listeners != null) {
                for (BiConsumer<BigInteger, BigInteger> listener : listeners) {
                    try {
                        listener.accept(oldState, newState);
                    } catch (Exception e) {
                        handleListenerError(e, listener);
                    }
                }
            }
        } else {
            BigInteger changed = oldState.xor(newState);
            int bitLength = Math.max(oldState.bitLength(), newState.bitLength());
            
            for (int i = 0; i < bitLength; i++) {
                if (changed.testBit(i)) {
                    BigInteger bitValue = bit(i);
                    List<BiConsumer<BigInteger, BigInteger>> listeners = m_stateListeners.get(bitValue);
                    if (listeners != null) {
                        for (BiConsumer<BigInteger, BigInteger> listener : listeners) {
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
            new NoteBytesPair("state", new NoteString(m_state.toString()))
        });
    }

    public static BitFlagStateMachine fromNoteBytes(NoteBytesObject obj) {
        NoteBytesMap map = obj.getAsNoteBytesMap();
        String id = map.get("id").getAsString();
        BigInteger state = new BigInteger(map.get("state").getAsString());
        return new BitFlagStateMachine(id, state);
    }

    // ========== Utility Methods ==========

    public List<BigInteger> getActiveStates() {
        List<BigInteger> active = new ArrayList<>();
        int bitLength = m_state.bitLength();
        for (int i = 0; i < bitLength; i++) {
            if (m_state.testBit(i)) {
                active.add(bit(i));
            }
        }
        return active;
    }

   /*public List<Integer> getActiveBitPositions() {
        List<Integer> positions = new ArrayList<>();
        int bitLength = m_state.bitLength();
        for (int i = 0; i < bitLength; i++) {
            if (m_state.testBit(i)) {
                positions.add(i);
            }
        }
        return positions;
    }*/

     public NoteIntegerArray getActiveBitPositions() {
        NoteIntegerArray positions = new NoteIntegerArray();
        int bitLength = m_state.bitLength();
        for (int i = 0; i < bitLength; i++) {
            if (m_state.testBit(i)) {
                positions.add(i);
            }
        }
        return positions;
    }

    public int getHighestStateBit() {
        return m_state.bitLength() - 1;
    }

    public int getActiveStateCount() {
        return m_state.bitCount();
    }

    public String getStateString(Map<Long, String> stateNames) {
        Map<BigInteger, String> bigIntNames = new HashMap<>();
        if (stateNames != null) {
            for (Map.Entry<Long, String> entry : stateNames.entrySet()) {
                bigIntNames.put(BigInteger.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return getStateString(bigIntNames, null);
    }

    public String getStateString(Map<BigInteger, String> stateNames, Map<Integer, String> positionNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        int bitLength = m_state.bitLength();
        
        for (int i = 0; i < bitLength; i++) {
            if (m_state.testBit(i)) {
                if (!first) sb.append(", ");
                
                BigInteger bitValue = bit(i);
                String name = null;
                
                if (stateNames != null) {
                    name = stateNames.get(bitValue);
                }
                if (name == null && positionNames != null) {
                    name = positionNames.get(i);
                }
                if (name == null) {
                    name = "BIT_" + i;
                }
                
                sb.append(name);
                first = false;
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("StateMachine[id=%s, state=%s, active=%d, bits=%d]",
                m_id, m_state.toString(16), getActiveStateCount(), m_state.bitLength());
    }

    // ========== Static Helpers ==========

    public static BigInteger bit(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("Bit position must be non-negative");
        }
        return BigInteger.ONE.shiftLeft(position);
    }

    public static BigInteger combine(BigInteger... bits) {
        BigInteger result = BigInteger.ZERO;
        for (BigInteger bit : bits) {
            result = result.or(bit);
        }
        return result;
    }

    public static BigInteger combine(long... bits) {
        BigInteger result = BigInteger.ZERO;
        for (long bit : bits) {
            result = result.or(BigInteger.valueOf(bit));
        }
        return result;
    }

    public static BigInteger combine(int... positions) {
        BigInteger result = BigInteger.ZERO;
        for (int pos : positions) {
            result = result.or(bit(pos));
        }
        return result;
    }

    public static boolean anySet(BigInteger state, BigInteger mask) {
        return !state.and(mask).equals(BigInteger.ZERO);
    }

    public static boolean anySet(BigInteger state, long mask) {
        return anySet(state, BigInteger.valueOf(mask));
    }

    public static boolean allSet(BigInteger state, BigInteger mask) {
        return state.and(mask).equals(mask);
    }

    public static boolean allSet(BigInteger state, long mask) {
        return allSet(state, BigInteger.valueOf(mask));
    }

    /** bits that differ between a and b */
    public static BigInteger difference(BigInteger a, BigInteger b) {
        return a.xor(b);
    }

    /** indices of set bits */
    public static List<Integer> bitIndices(BigInteger mask) {
        List<Integer> indices = new ArrayList<>();
        int bitLength = mask.bitLength();
        for (int i = 0; i < bitLength; i++) {
            if (mask.testBit(i)) {
                indices.add(i);
            }
        }
        return indices;
    }

    /** formatted binary string (truncated if too long) */
    public static String bitString(BigInteger mask) {
        return bitString(mask, -1);
    }

    /** formatted binary string with optional max length */
    public static String bitString(BigInteger mask, int maxLength) {
        String binary = mask.toString(2);
        if (maxLength > 0 && binary.length() > maxLength) {
            return "..." + binary.substring(binary.length() - maxLength);
        }
        return binary;
    }

    // For backward compatibility with 64-bit longs
    public static String bitString(long mask) {
        return Long.toBinaryString(mask);
    }

    // ========== Migration Helpers ==========

    /**
     * Convert from long-based state to BigInteger state machine
     */
    public static BitFlagStateMachine fromLongState(String id, long state) {
        return new BitFlagStateMachine(id, BigInteger.valueOf(state));
    }

    /**
     * Get state as long (only safe if state fits in 64 bits)
     */
    public long getStateAsLong() {
        if (m_state.bitLength() > 63) {
            throw new ArithmeticException("State does not fit in long (bitLength=" + m_state.bitLength() + ")");
        }
        return m_state.longValue();
    }

    /**
     * Safely get state as long, with fallback
     */
    public long getStateAsLongOrDefault(long defaultValue) {
        try {
            return getStateAsLong();
        } catch (ArithmeticException e) {
            return defaultValue;
        }
    }
}