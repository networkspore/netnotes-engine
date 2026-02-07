package io.netnotes.engine.io.capabilities;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.noteBytes.*;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.math.BigInteger;
import java.util.*;

/**
 * DeviceCapabilitySet - Hierarchical capability system using BitFlags
 * 
 * Design:
 * - Capabilities are bit flags in a BigInteger (unlimited growth)
 * - Named constants map to bit positions via CapabilityRegistry
 * - Constraints define mutual exclusions and requirements
 * - Serializes efficiently as NoteBigInteger
 * - Supports composite sources (window with child devices)
 * 
 * Example:
 *   Device has: KEYBOARD | MOUSE | TOUCH
 *   User enables: PARSED_MODE (disables RAW_MODE)
 *   Constraints: PARSED_MODE requires DEVICE_TYPE_KNOWN
 *                RAW_MODE excludes PARSED_MODE
 */
public class DeviceCapabilitySet {
    
    // Internal state machines
    private final BitFlagStateMachine availableCapabilities; // What device HAS
    private final BitFlagStateMachine enabledCapabilities;   // What user ENABLED
    
    // Capability registry (shared across all instances)
    private static final CapabilityRegistry REGISTRY = new CapabilityRegistry();
    
    // Constraints for this capability set
    private final CapabilityConstraints constraints;
    
    // Composite pattern - child sources
    private final List<DeviceCapabilitySet> children = new ArrayList<>();
    
    // Metadata
    private final String name;
    private final String deviceType;
    
    public DeviceCapabilitySet(String name, String deviceType) {
        this.name = name;
        this.deviceType = deviceType;
        this.availableCapabilities = new BitFlagStateMachine(name + "-available");
        this.enabledCapabilities = new BitFlagStateMachine(name + "-enabled");
        this.constraints = new CapabilityConstraints();
        
        setupTransitions();
        setupDefaultConstraints();
    }
    
    /**
     * Setup state machine transitions with correct BitFlagStateMachine API
     */
    private void setupTransitions() {
        // When any capability is enabled, validate it's available
        enabledCapabilities.onStateChanged((oldState, newState) -> {
            // Get newly enabled bits
            BigInteger changedBits = newState.andNot(oldState);
            
            // Check each newly enabled bit
            int bitLength = changedBits.bitLength();
            for (int i = 0; i < bitLength; i++) {
                if (changedBits.testBit(i)) {
                   
                    String capName = REGISTRY.getNameForBit(i);
                    
                    if (capName != null && !availableCapabilities.hasFlag(i)) {
                        Log.logError("Warning: Enabled unavailable capability: " + capName);
                    }
                }
            }
        });
        
        // Log all state changes for debugging
        availableCapabilities.onStateChanged((oldState, newState) -> {
            if (!oldState.equals(newState)) {
                Log.logMsg("[" + name + "] Available capabilities updated");
            }
        });
    }
    
    /**
     * Setup default constraints based on capability registry
     */
    private void setupDefaultConstraints() {
        // Mode capabilities are mutually exclusive
        Set<String> modes = REGISTRY.getAllModes();
        List<String> modeList = new ArrayList<>(modes);
        
        for (int i = 0; i < modeList.size(); i++) {
            for (int j = i + 1; j < modeList.size(); j++) {
                constraints.addMutualExclusion(modeList.get(i), modeList.get(j));
            }
        }
        
        // Parsed mode requires device type to be known
        if (modes.contains("parsed_mode")) {
            constraints.addRequirement("parsed_mode", "device_type_known");
        }
        
        // High precision requires device type known
        if (REGISTRY.isRegistered("high_precision")) {
            constraints.addRequirement("high_precision", "device_type_known");
        }
        
        // Encryption enabled requires encryption supported
        if (REGISTRY.isRegistered("encryption_enabled")) {
            constraints.addRequirement("encryption_enabled", "encryption_supported");
        }
        
        // Buffering enabled requires buffering supported
        if (REGISTRY.isRegistered("buffering_enabled")) {
            constraints.addRequirement("buffering_enabled", "buffering_supported");
        }
    }
    
    // ===== CAPABILITY MANAGEMENT =====
    
    /**
     * Mark a capability as available (device has it)
     */
    public void addAvailableCapability(String capability) {
        if (!REGISTRY.isRegistered(capability)) {
            throw new IllegalArgumentException("Unknown capability: " + capability);
        }
        
        int bit = REGISTRY.getBitForName(capability);
        availableCapabilities.addState(bit);
    }
    
    /**
     * Check if capability is available
     */
    public boolean hasCapability(String capability) {
        if (!REGISTRY.isRegistered(capability)) {
            return false;
        }
        
        int bit = REGISTRY.getBitForName(capability);
        return availableCapabilities.hasFlag(bit);
    }
    
    /**
     * Enable a capability (user selected mode)
     * @return true if enabled, false if constraints violated
     */
    public boolean enableCapability(String capability) {
        if (!REGISTRY.isRegistered(capability)) {
            throw new IllegalArgumentException("Unknown capability: " + capability);
        }
        
        int bit = REGISTRY.getBitForName(capability);
        
        // Check constraints
        if (!constraints.canEnable(capability, this)) {
            Log.logError("Cannot enable " + capability + ": " + 
                             constraints.getFailureReason(capability, this));
            return false;
        }
        
        // Handle mutual exclusions - disable conflicting capabilities
        Set<String> exclusions = constraints.getExclusions(capability);
        for (String excluded : exclusions) {
            if (isEnabled(excluded)) {
                disableCapability(excluded);
            }
        }
        
        enabledCapabilities.addState(bit);
        return true;
    }
    
    /**
     * Disable a capability
     */
    public void disableCapability(String capability) {
        if (!REGISTRY.isRegistered(capability)) {
            return;
        }
        
        int bit = REGISTRY.getBitForName(capability);
        enabledCapabilities.removeState(bit);
    }
    
    /**
     * Check if capability is enabled
     */
    public boolean isEnabled(String capability) {
        if (!REGISTRY.isRegistered(capability)) {
            return false;
        }
        
        int bit = REGISTRY.getBitForName(capability);
        return enabledCapabilities.hasFlag(bit);
    }
    
    /**
     * Get all available capability names
     */
    public Set<String> getAvailableCapabilities() {
        return REGISTRY.getNamesForState(availableCapabilities.getState());
    }
    
    /**
     * Get all enabled capability names
     */
    public Set<String> getEnabledCapabilities() {
        return REGISTRY.getNamesForState(enabledCapabilities.getState());
    }
    
    /**
     * Get available capabilities as BigInteger
     */
    public BigInteger getAvailableCapabilitiesState() {
        return availableCapabilities.getState();
    }
    
    /**
     * Get enabled capabilities as BigInteger
     */
    public BigInteger getEnabledCapabilitiesState() {
        return enabledCapabilities.getState();
    }
    
    // ===== CONSTRAINTS =====
    
    /**
     * Add mutual exclusion constraint
     * Example: "raw_mode" excludes "parsed_mode"
     */
    public void addMutualExclusion(String capability1, String capability2) {
        constraints.addMutualExclusion(capability1, capability2);
    }
    
    /**
     * Add requirement constraint
     * Example: "parsed_mode" requires "device_type_known"
     */
    public void addRequirement(String capability, String requiredCapability) {
        constraints.addRequirement(capability, requiredCapability);
    }
    
    /**
     * Check if capability can be enabled given current state
     */
    public boolean canEnable(String capability) {
        return constraints.canEnable(capability, this);
    }
    
    /**
     * Get reason why capability cannot be enabled
     */
    public String getEnableFailureReason(String capability) {
        return constraints.getFailureReason(capability, this);
    }
    
    /**
     * Validate consistency of current capability set
     */
    public List<String> validate() {
        return constraints.validateCapabilitySet(this);
    }
    
    // ===== COMPOSITE PATTERN =====
    
    /**
     * Add a child source (for composite devices like windows)
     */
    public void addChild(DeviceCapabilitySet child) {
        children.add(child);
        
        // Mark self as composite
        addAvailableCapability("composite_source");
        
        if (children.size() > 1) {
            addAvailableCapability("multiple_children");
        }
    }
    
    /**
     * Get child sources
     */
    public List<DeviceCapabilitySet> getChildren() {
        return Collections.unmodifiableList(children);
    }
    
    /**
     * Check if this is a composite source
     */
    public boolean isComposite() {
        return !children.isEmpty();
    }
    
    /**
     * Get aggregated capabilities from all children
     */
    public BigInteger getAggregatedAvailableCapabilities() {
        BigInteger aggregate = availableCapabilities.getState();
        for (DeviceCapabilitySet child : children) {
            aggregate = aggregate.or(child.getAggregatedAvailableCapabilities());
        }
        return aggregate;
    }
    
    // ===== SERIALIZATION =====
    
    /**
     * Serialize to NoteBytesObject
     */
    public NoteBytesObject toNoteBytes() {
        NoteBytesObject obj = new NoteBytesObject();
        
        obj.add(Keys.NAME, name);
        obj.add(Keys.ITEM_TYPE, deviceType);
        
        // Serialize capabilities as BigInteger
        obj.add(Keys.AVAILABLE_CAPABILITIES, new NoteBigInteger(availableCapabilities.getState()));
        obj.add(Keys.ENABLED_CAPABILITIES, new NoteBigInteger(enabledCapabilities.getState()));
        
        // Serialize constraints
        obj.add(Keys.CONSTRAINTS, constraints.toNoteBytes());
        
        // Serialize children
        if (!children.isEmpty()) {
            NoteBytes[] childrenArray = children.stream()
                .map(value->value.toNoteBytes())
                .toArray(NoteBytes[]::new);

            obj.add("children", new NoteBytesArray(childrenArray));
        }
        
        NoteBytes[] availableCapabilityNames = getAvailableCapabilities().stream()
            .map(value->new NoteBytes(value))
            .toArray(NoteBytes[]::new);

        obj.add("available_names", new NoteBytesArray(availableCapabilityNames));
        
        NoteBytes[] enabledNames = getEnabledCapabilities().stream()
            .map(value->new NoteBytes(value))
            .toArray(NoteBytes[]::new);

        obj.add("enabled_names", new NoteBytesArray(enabledNames));
        
        return obj;
    }
    
    /**
     * Deserialize from NoteBytesObject
     */
    public static DeviceCapabilitySet fromNoteBytes(NoteBytesObject obj) {
        NoteBytesMap map = obj.getAsNoteBytesMap();
        
        String name = map.get(Keys.NAME).getAsString();
        String deviceType = map.get(Keys.ITEM_TYPE).getAsString();
        
        DeviceCapabilitySet caps = new DeviceCapabilitySet(name, deviceType);
        
        // Restore capabilities from BigInteger
        BigInteger available = new BigInteger(map.get(Keys.AVAILABLE_CAPABILITIES).getBytes());
        BigInteger enabled = new BigInteger(map.get(Keys.ENABLED_CAPABILITIES).getBytes());
        
        caps.availableCapabilities.setState(available);
        caps.enabledCapabilities.setState(enabled);
        
        // Restore constraints
        NoteBytes constraintsBytes = map.get(Keys.CONSTRAINTS);
        if (constraintsBytes != null) {
            caps.constraints.fromNoteBytes(constraintsBytes.getAsNoteBytesObject());
        }
        
        // Restore children
        NoteBytes childrenBytes = map.get(Keys.CHILDREN);
        if (childrenBytes != null) {
            NoteBytesArray childArray = childrenBytes.getAsNoteBytesArray();
            
            for (NoteBytes childBytes : childArray.getAsArray()) {
                DeviceCapabilitySet child = fromNoteBytes(childBytes.getAsNoteBytesObject());
                caps.children.add(child);
            }
        }
        
        return caps;
    }
    
    // ===== USER PRESENTATION =====
    
    /**
     * Get user-friendly description
     */
    public CapabilityDescription describe() {
        return new CapabilityDescription(
            name,
            deviceType,
            getAvailableCapabilities(),
            getEnabledCapabilities(),
            getAvailableModes(),
            getEnabledMode(),
            children.stream().map(DeviceCapabilitySet::describe).toList()
        );
    }
    
    /**
     * Get available modes (mutually exclusive capability groups)
     */
    public Set<String> getAvailableModes() {
        Set<String> modes = new HashSet<>();
        for (String cap : getAvailableCapabilities()) {
            if (REGISTRY.isMode(cap)) {
                modes.add(cap);
            }
        }
        return modes;
    }
    
    /**
     * Get currently enabled mode (only one from mutually exclusive group)
     */
    public String getEnabledMode() {
        for (String cap : getEnabledCapabilities()) {
            if (REGISTRY.isMode(cap)) {
                return cap;
            }
        }
        return null;
    }
    
    /**
     * Get user-friendly name for capability
     */
    public static String getUserFriendlyName(String capability) {
        return REGISTRY.getUserFriendlyName(capability);
    }
    
    /**
     * Get capability registry instance
     */
    public static CapabilityRegistry getRegistry() {
        return REGISTRY;
    }
    
    @Override
    public String toString() {
        return String.format("DeviceCapabilitySet{name='%s', type='%s', available=%d, enabled=%d, children=%d}",
            name, deviceType, 
            getAvailableCapabilities().size(),
            getEnabledCapabilities().size(),
            children.size());
    }
    
    // ===== BUILDER =====
    
    public static class Builder {
        private final DeviceCapabilitySet caps;
        
        public Builder(String name, String deviceType) {
            this.caps = new DeviceCapabilitySet(name, deviceType);
        }
        
        public Builder withCapability(String capability) {
            caps.addAvailableCapability(capability);
            return this;
        }
        
        public Builder withCapabilities(String... capabilities) {
            for (String cap : capabilities) {
                caps.addAvailableCapability(cap);
            }
            return this;
        }
        
        public Builder enableCapability(String capability) {
            caps.enableCapability(capability);
            return this;
        }
        
        public Builder addMutualExclusion(String cap1, String cap2) {
            caps.addMutualExclusion(cap1, cap2);
            return this;
        }
        
        public Builder addRequirement(String capability, String required) {
            caps.addRequirement(capability, required);
            return this;
        }
        
        public Builder addChild(DeviceCapabilitySet child) {
            caps.addChild(child);
            return this;
        }
        
        public DeviceCapabilitySet build() {
            return caps;
        }
    }
}
