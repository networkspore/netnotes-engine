package io.netnotes.engine.io.capabilities;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CapabilityRegistry - Central registry for all capability definitions
 * 
 * Responsibilities:
 * - Assign bit positions to capability names
 * - Map capability names to user-friendly descriptions
 * - Track which capabilities are modes (mutually exclusive)
 * - Provide bidirectional name ↔ bit mapping
 */
public class CapabilityRegistry {
    
    // Capability name → bit position
    private final Map<String, Integer> capabilityBits = new ConcurrentHashMap<>();
    
    // Bit position → capability name
    private final Map<Integer, String> bitToCapability = new ConcurrentHashMap<>();
    
    // Capability name → user-friendly name
    private final Map<String, String> userFriendlyNames = new ConcurrentHashMap<>();
    
    // Mode capabilities (mutually exclusive groups)
    private final Set<String> modes = ConcurrentHashMap.newKeySet();
    
    // Bit position counter
    private final AtomicInteger nextBit = new AtomicInteger(0);
    
    public CapabilityRegistry() {
        registerDefaultCapabilities();
    }
    
    /**
     * Register all default capabilities
     */
    private void registerDefaultCapabilities() {
        // Input device types (bits 0-7)
        register("keyboard", "Keyboard Input", false);
        register("mouse", "Mouse Input", false);
        register("touch", "Touch Input", false);
        register("gamepad", "Gamepad Input", false);
        register("pen", "Pen/Stylus Input", false);
        register("touchpad", "Touchpad Input", false);
        register("scroll", "Scroll Wheel", false);
        
        // Device modes (bits 8-15) - mutually exclusive
        register("raw_mode", "Raw HID Reports", true);
        register("parsed_mode", "Parsed Events", true);
        register("passthrough_mode", "OS Passthrough", true);
        register("filtered_mode", "Filtered Events", true);
        
        // Coordinate systems (bits 16-23)
        register("absolute_coordinates", "Absolute Positioning", false);
        register("relative_coordinates", "Relative Movement", false);
        register("screen_coordinates", "Screen Space", false);
        register("normalized_coordinates", "Normalized (0-1)", false);
        
        // Advanced features (bits 24-31)
        register("high_precision", "High Precision Mode", false);
        register("multiple_devices", "Multi-Device Support", false);
        register("global_capture", "Global Input Capture", false);
        register("provides_scancodes", "Hardware Scancodes", false);
        register("nanosecond_timestamps", "Nanosecond Timestamps", false);
        
        // Device type detection (bits 32-39)
        register("device_type_known", "Device Type Identified", false);
        register("hid_device", "HID Device", false);
        register("usb_device", "USB Device", false);
        register("bluetooth_device", "Bluetooth Device", false);
        
        // State capabilities (bits 40-47)
        register("encryption_supported", "Encryption Support", false);
        register("encryption_enabled", "Encryption Active", false);
        register("buffering_supported", "Event Buffering", false);
        register("buffering_enabled", "Buffering Active", false);
        
        // Lifecycle (bits 48-55)
        register("scene_location", "Scene Location Events", false);
        register("scene_size", "Scene Size Events", false);
        register("window_lifecycle", "Window Lifecycle Events", false);
        register("stage_position", "Stage Position Events", false);
        register("stage_size", "Stage Size Events", false);
        register("stage_focus", "Stage Focus Events", false);
        
        // Composite capabilities (bits 56-63)
        register("composite_source", "Composite Source", false);
        register("multiple_children", "Multiple Child Sources", false);
    }
    
    /**
     * Register a capability with automatic bit assignment
     */
    public void register(String name, String userFriendlyName, boolean isMode) {
        if (capabilityBits.containsKey(name)) {
            return; // Already registered
        }
        
        int bit = nextBit.getAndIncrement();
        
        capabilityBits.put(name, bit);
        bitToCapability.put(bit, name);
        userFriendlyNames.put(name, userFriendlyName);
        
        if (isMode) {
            modes.add(name);
        }
    }
    
    /**
     * Get bit flag for capability name
     */
    public int getBitForName(String name) {
        Integer bit = capabilityBits.get(name);
        if (bit == null) {
            throw new IllegalArgumentException("Unknown capability: " + name);
        }
        return bit;
    }
    
    /**
     * Get capability name for bit flag
     */
    public String getNameForBit(int bit) {
        return bitToCapability.get(bit);
    }
    
    /**
     * Get all capability names for a state
     */
    public Set<String> getNamesForState(BigInteger state) {
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, Integer> entry : capabilityBits.entrySet()) {
            Integer bit = entry.getValue();
            if (state.testBit(bit)) {
                names.add(entry.getKey());
            }
        }
        return names;
    }
    
    /**
     * Get user-friendly name
     */
    public String getUserFriendlyName(String capability) {
        return userFriendlyNames.getOrDefault(capability, capability);
    }
    
    /**
     * Check if capability is a mode (mutually exclusive)
     */
    public boolean isMode(String capability) {
        return modes.contains(capability);
    }
    
    /**
     * Get all registered capabilities
     */
    public Set<String> getAllCapabilities() {
        return new HashSet<>(capabilityBits.keySet());
    }
    
    /**
     * Get all mode capabilities
     */
    public Set<String> getAllModes() {
        return new HashSet<>(modes);
    }
    
    /**
     * Check if capability is registered
     */
    public boolean isRegistered(String capability) {
        return capabilityBits.containsKey(capability);
    }
}