package io.netnotes.engine.io.capabilities;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;

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

    public static class DefaultCapabilities{
        public static final NoteBytesReadOnly  KEYBOARD = new NoteBytesReadOnly("keyboard");
        public static final NoteBytesReadOnly  MOUSE = new NoteBytesReadOnly("mouse");
        public static final NoteBytesReadOnly  TOUCH = new NoteBytesReadOnly("touch");
        public static final NoteBytesReadOnly  GAMEPAD = new NoteBytesReadOnly("gamepad");
        public static final NoteBytesReadOnly  PEN = new NoteBytesReadOnly("pen");
        public static final NoteBytesReadOnly  TOUCHPAD = new NoteBytesReadOnly("touchpad");
        public static final NoteBytesReadOnly  SCROLL = new NoteBytesReadOnly("scroll");

        public static final NoteBytesReadOnly  RAW_MODE = new NoteBytesReadOnly("raw_mode");
        public static final NoteBytesReadOnly  PARSED_MODE = new NoteBytesReadOnly("parsed_mode");
        public static final NoteBytesReadOnly  PASSTHROUGH_MODE = new NoteBytesReadOnly("passthrough_mode");
        public static final NoteBytesReadOnly  FILTERED_MODE = new NoteBytesReadOnly("filtered_mode");

        public static final NoteBytesReadOnly  ABSOLUTE_COORDINATES = new NoteBytesReadOnly("absolute_coordinates");
        public static final NoteBytesReadOnly  RELATIVE_COORDINATES = new NoteBytesReadOnly("relative_coordinates");
        public static final NoteBytesReadOnly  SCREEN_COORDINATES = new NoteBytesReadOnly("screen_coordinates");
        public static final NoteBytesReadOnly  NORMALIZED_COORDINATES = new NoteBytesReadOnly("normalized_coordinates");

        public static final NoteBytesReadOnly  HIGH_PRECISION = new NoteBytesReadOnly("high_precision");
        public static final NoteBytesReadOnly  MULTIPLE_DEVICES = new NoteBytesReadOnly("multiple_devices");
        public static final NoteBytesReadOnly  GLOBAL_CAPTURE = new NoteBytesReadOnly("global_capture");
        public static final NoteBytesReadOnly  PROVIDES_SCANCODES = new NoteBytesReadOnly("provides_scancodes");
        public static final NoteBytesReadOnly  NANOSECOND_TIMESTAMPS = new NoteBytesReadOnly("nanosecond_timestamps");

        public static final NoteBytesReadOnly  DEVICE_TYPE_KNOWN = new NoteBytesReadOnly("device_type_known");
        public static final NoteBytesReadOnly  HID_DEVICE = new NoteBytesReadOnly("hid_device");
        public static final NoteBytesReadOnly  USB_DEVICE = new NoteBytesReadOnly("usb_device");
        public static final NoteBytesReadOnly  BLUETOOTH_DEVICE = new NoteBytesReadOnly("bluetooth_device");

        public static final NoteBytesReadOnly  ENCRYPTION_SUPPORTED = new NoteBytesReadOnly("encryption_supported");
        public static final NoteBytesReadOnly  ENCRYPTION_ENABLED = new NoteBytesReadOnly("encryption_enabled");
        public static final NoteBytesReadOnly  BUFFERING_SUPPORTED = new NoteBytesReadOnly("buffering_supported");
        public static final NoteBytesReadOnly  BUFFERING_ENABLED = new NoteBytesReadOnly("buffering_enabled");

        public static final NoteBytesReadOnly  SCENE_LOCATION = new NoteBytesReadOnly("scene_location");
        public static final NoteBytesReadOnly  SCENE_SIZE = new NoteBytesReadOnly("scene_size");
        public static final NoteBytesReadOnly  WINDOW_LIFECYCLE = new NoteBytesReadOnly("window_lifecycle");
        public static final NoteBytesReadOnly  STAGE_POSITION = new NoteBytesReadOnly("stage_position");
        public static final NoteBytesReadOnly  STAGE_SIZE = new NoteBytesReadOnly("stage_size");
        public static final NoteBytesReadOnly  STAGE_FOCUS = new NoteBytesReadOnly("stage_focus");

        public static final NoteBytesReadOnly  COMPOSITE_SOURCE = new NoteBytesReadOnly("composite_source");
        public static final NoteBytesReadOnly  MULTIPLE_CHILDREN = new NoteBytesReadOnly("multiple_children");
    }
    
    // Capability name → bit position
    private final Map<NoteBytes, Integer> capabilityBits = new ConcurrentHashMap<>();
    
    // Bit position → capability name
    private final Map<Integer, NoteBytes> bitToCapability = new ConcurrentHashMap<>();
    
    // Capability name → user-friendly name
    private final Map<NoteBytes, String> userFriendlyNames = new ConcurrentHashMap<>();
    
    // Mode capabilities (mutually exclusive groups)
    private final Set<NoteBytes> modes = ConcurrentHashMap.newKeySet();
    
    // Bit position counter
    private final AtomicInteger nextBit = new AtomicInteger(0);
    
    public CapabilityRegistry() {
        registerDefaultCapabilities();
    }
    
    /**
     * Register all default capabilities
     */
    private void registerDefaultCapabilities() {
        register(DefaultCapabilities.KEYBOARD, "Keyboard Input", false);
        register(DefaultCapabilities.MOUSE, "Mouse Input", false);
        register(DefaultCapabilities.TOUCH, "Touch Input", false);
        register(DefaultCapabilities.GAMEPAD, "Gamepad Input", false);
        register(DefaultCapabilities.PEN, "Pen/Stylus Input", false);
        register(DefaultCapabilities.TOUCHPAD, "Touchpad Input", false);
        register(DefaultCapabilities.SCROLL, "Scroll Wheel", false);

        // Device modes (bits 8-15) - mutually exclusive
        register(DefaultCapabilities.RAW_MODE, "Raw HID Reports", true);
        register(DefaultCapabilities.PARSED_MODE, "Parsed Events", true);
        register(DefaultCapabilities.PASSTHROUGH_MODE, "OS Passthrough", true);
        register(DefaultCapabilities.FILTERED_MODE, "Filtered Events", true);

        // Coordinate systems (bits 16-23)
        register(DefaultCapabilities.ABSOLUTE_COORDINATES, "Absolute Positioning", false);
        register(DefaultCapabilities.RELATIVE_COORDINATES, "Relative Movement", false);
        register(DefaultCapabilities.SCREEN_COORDINATES, "Screen Space", false);
        register(DefaultCapabilities.NORMALIZED_COORDINATES, "Normalized (0-1)", false);

        // Advanced features (bits 24-31)
        register(DefaultCapabilities.HIGH_PRECISION, "High Precision Mode", false);
        register(DefaultCapabilities.MULTIPLE_DEVICES, "Multi-Device Support", false);
        register(DefaultCapabilities.GLOBAL_CAPTURE, "Global Input Capture", false);
        register(DefaultCapabilities.PROVIDES_SCANCODES, "Hardware Scancodes", false);
        register(DefaultCapabilities.NANOSECOND_TIMESTAMPS, "Nanosecond Timestamps", false);

        // Device type detection (bits 32-39)
        register(DefaultCapabilities.DEVICE_TYPE_KNOWN, "Device Type Identified", false);
        register(DefaultCapabilities.HID_DEVICE, "HID Device", false);
        register(DefaultCapabilities.USB_DEVICE, "USB Device", false);
        register(DefaultCapabilities.BLUETOOTH_DEVICE, "Bluetooth Device", false);

        // State capabilities (bits 40-47)
        register(DefaultCapabilities.ENCRYPTION_SUPPORTED, "Encryption Support", false);
        register(DefaultCapabilities.ENCRYPTION_ENABLED, "Encryption Active", false);
        register(DefaultCapabilities.BUFFERING_SUPPORTED, "Event Buffering", false);
        register(DefaultCapabilities.BUFFERING_ENABLED, "Buffering Active", false);

        // Lifecycle (bits 48-55)
        register(DefaultCapabilities.SCENE_LOCATION, "Scene Location Events", false);
        register(DefaultCapabilities.SCENE_SIZE, "Scene Size Events", false);
        register(DefaultCapabilities.WINDOW_LIFECYCLE, "Window Lifecycle Events", false);
        register(DefaultCapabilities.STAGE_POSITION, "Stage Position Events", false);
        register(DefaultCapabilities.STAGE_SIZE, "Stage Size Events", false);
        register(DefaultCapabilities.STAGE_FOCUS, "Stage Focus Events", false);

        // Composite capabilities (bits 56-63)
        register(DefaultCapabilities.COMPOSITE_SOURCE, "Composite Source", false);
        register(DefaultCapabilities.MULTIPLE_CHILDREN, "Multiple Child Sources", false);
    }
    
    /**
     * Register a capability with automatic bit assignment
     */
    public void register(NoteBytes name, String userFriendlyName, boolean isMode) {
        NoteBytesReadOnly readOnlyName = new NoteBytesReadOnly(name);
        if (capabilityBits.containsKey(readOnlyName)) {
            return; // Already registered
        }
        
        int bit = nextBit.getAndIncrement();
        
        capabilityBits.put(readOnlyName, bit);
        bitToCapability.put(bit, readOnlyName);
        userFriendlyNames.put(readOnlyName, userFriendlyName);
        
        if (isMode) {
            modes.add(readOnlyName);
        }
    }
    
    /**
     * Get bit flag for capability name
     */
    public int getBitForName(NoteBytes name) {
        Integer bit = capabilityBits.get(name);
        if (bit == null) {
            throw new IllegalArgumentException("Unknown capability: " + name);
        }
        return bit;
    }
    
    /**
     * Get capability name for bit flag
     */
    public NoteBytes getNameForBit(int bit) {
        return bitToCapability.get(bit);
    }
    
    /**
     * Get all capability names for a state
     */
    public Set<NoteBytes> getNamesForState(BigInteger state) {
        Set<NoteBytes> names = new HashSet<>();
        for (Map.Entry<NoteBytes, Integer> entry : capabilityBits.entrySet()) {
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
    public String getUserFriendlyName(NoteBytes capability) {
        return userFriendlyNames.getOrDefault(capability, capability.getAsString());
    }
    
    /**
     * Check if capability is a mode (mutually exclusive)
     */
    public boolean isMode(NoteBytes capability) {
        return modes.contains(capability);
    }
    
    /**
     * Get all registered capabilities
     */
    public Set<NoteBytes> getAllCapabilities() {
        return new HashSet<>(capabilityBits.keySet());
    }
    
    /**
     * Get all mode capabilities
     */
    public Set<NoteBytes> getAllModes() {
        return new HashSet<>(modes);
    }
    
    /**
     * Check if capability is registered
     */
    public boolean isRegistered(NoteBytes capability) {
        return capabilityBits.containsKey(capability);
    }
}