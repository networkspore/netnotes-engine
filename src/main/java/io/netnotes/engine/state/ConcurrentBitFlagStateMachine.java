package io.netnotes.engine.state;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.noteBytes.*;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.collections.NoteBytesPair;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.state.BitFlagStateMachine.StateSnapshot;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.noteBytes.NoteUUID;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

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
public class ConcurrentBitFlagStateMachine {


    private final AtomicLong m_version = new AtomicLong(0);
    private final String m_id;
    private final AtomicReference<BigInteger> m_state = new AtomicReference<>();
    private final Map<BigInteger, List<StateTransition>> m_transitions;
    private final Map<BigInteger, List<BiConsumer<BigInteger, BigInteger>>> m_stateListeners;
    private final List<BiConsumer<BigInteger, BigInteger>> m_globalListeners;
    private final List<StateConstraint> m_constraints;
    private BiConsumer<Exception, BiConsumer<BigInteger, BigInteger>> m_errorHandler;
    private SerializedVirtualExecutor serialExec = null;

    public ConcurrentBitFlagStateMachine(String id) {
        this(id, BigInteger.ZERO);
    }

    public ConcurrentBitFlagStateMachine(String id, BigInteger initialState) {
        this.m_id = id;
        this.m_state.set(initialState);
        this.m_transitions = new ConcurrentHashMap<>();
        this.m_stateListeners = new ConcurrentHashMap<>();
        this.m_globalListeners = new CopyOnWriteArrayList<>();
        this.m_constraints = new CopyOnWriteArrayList<>();
    }

    // Convenience constructor for long-based initial states
    public ConcurrentBitFlagStateMachine(String id, long initialState) {
        this(id, BigInteger.valueOf(initialState));
    }

    public void setSerialExecutor(SerializedVirtualExecutor serialExec){
        this.serialExec = serialExec;
    }

    // ========== State Queries ==========

    public boolean hasState(BigInteger stateBit) {
        return getState().and(stateBit).equals(stateBit);
    }

    public boolean hasState(int bitPosition) {
        return getState().testBit(bitPosition);
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
        return getState().and(flags).equals(flags);
    }

    public boolean hasAllFlags(long flags) {
        return hasAllFlags(BigInteger.valueOf(flags));
    }

    public boolean hasAnyFlags(BigInteger flags) {
        return !getState().and(flags).equals(BigInteger.ZERO);
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
        return m_state.get();
    }

    public String getId() {
        return m_id;
    }

    // ========== State Mutations ==========

    public boolean addState(BigInteger stateBit) {
        while (true) {
            BigInteger oldState = m_state.get();
            if (oldState.and(stateBit).equals(stateBit)) {
                return false;
            }

            BigInteger newState = oldState.or(stateBit);
            validateStateConstraints(newState);

            if (m_state.compareAndSet(oldState, newState)) {
                notifyStateChange(oldState, newState, stateBit, true);
                checkTransitions(stateBit, true, newState, oldState);
                return true;
            }
        }
    }

    public boolean addState(int bitPosition) {
        return addState(bit(bitPosition));
    }

    public boolean addState(long stateBit) {
        return addState(BigInteger.valueOf(stateBit));
    }

    public boolean removeState(BigInteger stateBit) {
        while (true) {
            BigInteger oldState = m_state.get();
            if (!oldState.and(stateBit).equals(stateBit)) {
                return false;
            }

            BigInteger newState = oldState.andNot(stateBit);

            if (m_state.compareAndSet(oldState, newState)) {
                notifyStateChange(oldState, newState, stateBit, false);
                checkTransitions(stateBit, false, newState, oldState);
                return true;
            }
        }
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
        validateStateConstraints(newState);

        BigInteger oldState = m_state.getAndSet(newState);
        if (oldState.equals(newState)) return;

        notifyStateChange(oldState, newState, BigInteger.ZERO, false);

        BigInteger changed = oldState.xor(newState);
        int bitLength = Math.max(oldState.bitLength(), newState.bitLength());

        for (int i = 0; i < bitLength; i++) {
            if (changed.testBit(i)) {
                BigInteger bitValue = bit(i);
                boolean added = newState.testBit(i);
                checkTransitions(bitValue, added, newState, oldState);
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

    private void checkTransitions(BigInteger triggerBit, boolean isAdd, BigInteger newState, BigInteger oldState) {
        execute(() -> {
            List<StateTransition> transitions = m_transitions.get(triggerBit);
            if (transitions == null) return;
            
            //BigInteger newState = getState();
            for (StateTransition transition : transitions) {
                if (transition.onAdd == isAdd) {
                    if (transition.guard == null || transition.guard.canTransition(oldState, newState, triggerBit)) {
                        if (transition.action != null) {
                            transition.action.onTransition(oldState, newState, triggerBit);
                        }
                    }
                }
            }
        });
        
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

    public void removeStateListener(int bitPosition, BiConsumer<BigInteger, BigInteger> listener) {
        removeStateListener(bit(bitPosition), listener);
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
            Log.logError("Error in state machine listener: " + e.getMessage());
        }
    }

    private void notifyStateChange(BigInteger oldState, BigInteger newState, BigInteger changedBit, boolean targetedChange) {
        execute(()->{
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
        });
    }

    // ========== NoteBytes Serialization ==========


 

    // ========== Utility Methods ==========

    public List<BigInteger> getActiveStates() {
        List<BigInteger> active = new ArrayList<>();
        BigInteger state = getState();
        int bitLength = state.bitLength();
        for (int i = 0; i < bitLength; i++) {
            if (state.testBit(i)) {
                active.add(bit(i));
            }
        }
        return active;
    }

    public void update(UnaryOperator<BigInteger> updater) {
        while (true) {
            BigInteger oldState = m_state.get();
            BigInteger newState = updater.apply(oldState);

            if (oldState.equals(newState)) return;

            validateStateConstraints(newState);

            if (m_state.compareAndSet(oldState, newState)) {

                notifyStateChange(oldState, newState, BigInteger.ZERO, false);
                replayTransitions(oldState, newState);
                return;
            }
        }
    }

    private void replayTransitions(BigInteger oldState, BigInteger newState) {
        BigInteger added   = newState.andNot(oldState);
        BigInteger removed = oldState.andNot(newState);

        int maxBits = Math.max(oldState.bitLength(), newState.bitLength());

        // Handle added bits
        for (int i = 0; i < maxBits; i++) {
            if (added.testBit(i)) {
                BigInteger bit = BigInteger.ONE.shiftLeft(i);
                checkTransitions(bit, true, newState, oldState);
            }
        }

        // Handle removed bits
        for (int i = 0; i < maxBits; i++) {
            if (removed.testBit(i)) {
                BigInteger bit = BigInteger.ONE.shiftLeft(i);
                checkTransitions(bit, false, newState, oldState);
            }
        }
    }

     public NoteIntegerArray getActiveBitPositions() {
        NoteIntegerArray positions = new NoteIntegerArray();
        BigInteger state = getState();
        int bitLength = state.bitLength();
        for (int i = 0; i < bitLength; i++) {
            if (state.testBit(i)) {
                positions.add(i);
            }
        }
        return positions;
    }

    public int getHighestStateBit() {
        return getState().bitLength() - 1;
    }

    public int getActiveStateCount() {
        return getState().bitCount();
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
        BigInteger state = getState();
        int bitLength = state.bitLength();
        
        for (int i = 0; i < bitLength; i++) {
            if (state.testBit(i)) {
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
        BigInteger state = getState();
        return String.format("StateMachine[id=%s, state=%s, active=%d, bits=%d]",
                m_id, state.toString(16), getActiveStateCount(), state.bitLength());
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
        BigInteger state = getState();
        if (state.bitLength() > 63) {
            throw new ArithmeticException("State does not fit in long (bitLength=" + state.bitLength() + ")");
        }
        return state.longValue();
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

    /**
     * Get a consistent snapshot of current state and version.
     * This is lock-free but guarantees the state and version match.
     */
    public StateSnapshot getSnapshot() {
        long v1, v2;
        BigInteger state;
        
        do {
            v1 = m_version.get();
            state = m_state.get();
            v2 = m_version.get();
        } while (v1 != v2); // Retry if version changed during read
        
        return new StateSnapshot(state, v1);
    }

    /**
     * Get current version number. Useful for change detection.
     */
    public long getVersion() {
        return m_version.get();
    }

    // ========== Conditional Updates ==========

    /**
     * Apply update only if version hasn't changed.
     * Returns true if applied, false if version changed (conflict).
     * 
     * Pattern for optimistic concurrency:
     * <pre>
     * StateSnapshot snap = machine.getSnapshot();
     * // ... compute based on snap ...
     * boolean success = machine.updateIfVersion(snap.version, state -> {
     *     return state.or(newBits);
     * });
     * </pre>
     */
    public boolean updateIfVersion(long expectedVersion, 
                                    Function<BigInteger, BigInteger> updater) {
        while (true) {
            long currentVersion = m_version.get();
            if (currentVersion != expectedVersion) {
                return false; // Version changed - conflict
            }
            
            BigInteger oldState = m_state.get();
            BigInteger newState = updater.apply(oldState);
            
            if (oldState.equals(newState)) {
                return true; // No change needed
            }
            
            validateStateConstraints(newState);
            
            if (m_state.compareAndSet(oldState, newState)) {
                m_version.incrementAndGet();
                notifyStateChange(oldState, newState, BigInteger.ZERO, false);
                replayTransitions(oldState, newState);
                return true;
            }
            // CAS failed, loop and recheck version
        }
    }

    /**
     * Apply update only if state matches expected state.
     * More specific than version check - useful when you care about exact state.
     */
    public boolean updateIfState(BigInteger expectedState, 
                                  Function<BigInteger, BigInteger> updater) {
        while (true) {
            BigInteger oldState = m_state.get();
            if (!oldState.equals(expectedState)) {
                return false; // State changed - conflict
            }
            
            BigInteger newState = updater.apply(oldState);
            
            if (oldState.equals(newState)) {
                return true; // No change needed
            }
            
            validateStateConstraints(newState);
            
            if (m_state.compareAndSet(oldState, newState)) {
                m_version.incrementAndGet();
                notifyStateChange(oldState, newState, BigInteger.ZERO, false);
                replayTransitions(oldState, newState);
                return true;
            }
        }
    }

    /**
     * Apply update only if condition remains true.
     * Condition is rechecked on each CAS retry.
     * 
     * Example: Only add flag if another flag is still set
     * <pre>
     * machine.updateIf(
     *     state -> state.testBit(ACTIVE_BIT),
     *     state -> state.or(NEW_FLAG)
     * );
     * </pre>
     */
    public boolean updateIf(Predicate<BigInteger> condition,
                            Function<BigInteger, BigInteger> updater) {
        while (true) {
            BigInteger oldState = m_state.get();
            if (!condition.test(oldState)) {
                return false; // Condition no longer true
            }
            
            BigInteger newState = updater.apply(oldState);
            
            if (oldState.equals(newState)) {
                return true; // No change needed
            }
            
            validateStateConstraints(newState);
            
            if (m_state.compareAndSet(oldState, newState)) {
                m_version.incrementAndGet();
                notifyStateChange(oldState, newState, BigInteger.ZERO, false);
                replayTransitions(oldState, newState);
                return true;
            }
        }
    }

    // ========== Batch Operations ==========

    /**
     * Atomically add multiple states.
     * More efficient than individual addState calls.
     */
    public boolean addStates(BigInteger... stateBits) {
        if (stateBits.length == 0) return false;
        BigInteger combined = combine(stateBits);
        return addState(combined);
    }

    public boolean addStates(int... bitPositions) {
        if (bitPositions.length == 0) return false;
        BigInteger combined = combine(bitPositions);
        return addState(combined);
    }

    /**
     * Atomically remove multiple states.
     */
    public boolean removeStates(BigInteger... stateBits) {
        if (stateBits.length == 0) return false;
        BigInteger combined = combine(stateBits);
        return removeState(combined);
    }

    public boolean removeStates(int... bitPositions) {
        if (bitPositions.length == 0) return false;
        BigInteger combined = combine(bitPositions);
        return removeState(combined);
    }

    /**
     * Atomically set specific bits to specific values.
     * Example: setStates(mask, newBits) sets all bits in mask to corresponding bits in newBits
     */
    public void setStates(BigInteger mask, BigInteger newBits) {
        update(state -> {
            // Clear masked bits, then set new bits
            return state.andNot(mask).or(newBits.and(mask));
        });
    }

    // ========== Update existing methods to increment version ==========

    /*
     * IMPORTANT: Add m_version.incrementAndGet() after each successful
     * m_state.compareAndSet in these existing methods:
     * - addState(BigInteger)
     * - removeState(BigInteger)
     * - setState(BigInteger)
     * - update(UnaryOperator<BigInteger>)
     * 
     * Example for addState:
     * 
     * if (m_state.compareAndSet(oldState, newState)) {
     *     m_version.incrementAndGet(); // ADD THIS LINE
     *     notifyStateChange(oldState, newState, stateBit, true);
     *     checkTransitions(stateBit, true, newState, oldState);
     *     return true;
     * }
     */

    // ========== Waiting/Polling Utilities ==========

    /**
     * Result of a wait operation.
     */
    public static class WaitResult {
        public final boolean success;
        public final StateSnapshot snapshot;
        public final long elapsedNanos;

        public WaitResult(boolean success, StateSnapshot snapshot, long elapsedNanos) {
            this.success = success;
            this.snapshot = snapshot;
            this.elapsedNanos = elapsedNanos;
        }
    }

    /**
     * Busy-wait until condition is met or timeout.
     * Returns immediately if condition already true.
     * 
     * Note: This is a spin-wait. For longer waits, consider using listeners instead.
     */
    public WaitResult waitUntil(Predicate<BigInteger> condition, 
                                 long timeoutNanos) {
        long startTime = System.nanoTime();
        long endTime = startTime + timeoutNanos;
        
        while (true) {
            StateSnapshot snap = getSnapshot();
            if (condition.test(snap.state)) {
                return new WaitResult(true, snap, System.nanoTime() - startTime);
            }
            
            if (System.nanoTime() >= endTime) {
                return new WaitResult(false, snap, System.nanoTime() - startTime);
            }
            
            // Yield to avoid burning CPU
            Thread.yield();
        }
    }

    /**
     * Check if state has changed since given version.
     * Useful for efficient polling.
     */
    public boolean hasChangedSince(long version) {
        return m_version.get() != version;
    }

    /**
     * Get snapshot only if version has changed.
     * Returns null if version unchanged - avoids allocation.
     */
    public StateSnapshot getSnapshotIfChanged(long lastVersion) {
        if (!hasChangedSince(lastVersion)) {
            return null;
        }
        return getSnapshot();
    }

    // ========== Serialization Update ==========

    /**
     * Updated toNoteBytes to include version
     */
    public NoteBytesObject toNoteBytes() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.ID, new NoteString(m_id)),
            new NoteBytesPair(Keys.STATE, new NoteBytes(m_state.get())),
            new NoteBytesPair(Keys.VERSION, new NoteLong(m_version.get()))
        });
    }

    /**
     * Updated fromNoteBytes to restore version
     */
    public static ConcurrentBitFlagStateMachine fromNoteBytes(NoteBytes notebytes) {
        if(notebytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
            NoteBytesMap map = notebytes.getAsNoteBytesMap();
            NoteBytes idBytes = map.get(Keys.ID);
            NoteBytes statebytes = map.get(Keys.STATE);
            NoteBytes versionBytes = map.get(Keys.VERSION);
            if(statebytes == null || statebytes.getType() != NoteBytesMetaData.BIG_INTEGER_TYPE){
                throw new IllegalArgumentException("state must be BIG_INTEGER_TYPE");
            }

            BigInteger state = statebytes.getAsBigInteger();
            String id = idBytes != null ? idBytes.getAsString() : NoteUUID.createSafeUUID64();
            ConcurrentBitFlagStateMachine machine = new ConcurrentBitFlagStateMachine(id, state);
            if (versionBytes != null) {
                machine.m_version.set(versionBytes.getAsLong());
            }
            
            return machine;
        }

        throw new IllegalArgumentException("Cannot create state machine from notebytes");
    }

     protected void execute(Runnable run){
        if(serialExec == null){
            run.run();
        }else{
            if(serialExec.isCurrentThread()){
                run.run();
            }else{
                serialExec.executeFireAndForget(run);
            }
        }
    }

    // ========== Debug/Monitoring ==========

    /**
     * Get statistics about state changes.
     * Useful for monitoring contention.
     */
    public StateStats getStats() {
        StateSnapshot snap = getSnapshot();
        return new StateStats(
            snap.version,
            snap.state.bitCount(),
            snap.state.bitLength()
        );
    }

    public static class StateStats {
        public final long version;
        public final int activeStates;
        public final int maxBitPosition;

        public StateStats(long version, int activeStates, int maxBitPosition) {
            this.version = version;
            this.activeStates = activeStates;
            this.maxBitPosition = maxBitPosition;
        }

        @Override
        public String toString() {
            return String.format("StateStats[version=%d, active=%d, maxBit=%d]",
                version, activeStates, maxBitPosition);
        }
    }
}