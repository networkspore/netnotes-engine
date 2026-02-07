package io.netnotes.engine.state;

import io.netnotes.noteBytes.NoteBytesReadOnly;
import java.math.BigInteger;
import java.util.*;

/**
 * StateEventRegistry - Unified declaration of all states and events
 * 
 * Similar to NoteMessaging, this centralizes:
 * - State bit positions (for BitFlagStateMachine)
 * - Event types (for input events)
 * - Human-readable names for debugging
 * - Helper functions for translation
 * 
 * Organization:
 * - States use integer indices (0-255+)
 * - Events use byte types (matching EventBytes)
 * - Maps provide text equivalents
 * - Range helpers for categorization
 */
public class StateEventRegistry {
    
    // ===== STATE BIT POSITIONS =====
    
    /**
     * Input State Flags - Organized by functional ranges
     */
    public static class InputStates {
        // Modifier keys (bits 0-7)
        public static final int MOD_SHIFT       = 0;
        public static final int MOD_CONTROL     = 1;
        public static final int MOD_ALT         = 2;
        public static final int MOD_SUPER       = 3;
        public static final int MOD_CAPS_LOCK   = 4;
        public static final int MOD_NUM_LOCK    = 5;
        public static final int MOD_SCROLL_LOCK = 6;
        
        // Mouse buttons (bits 8-15)
        public static final int MOUSE_BUTTON_1  = 8;   // Left
        public static final int MOUSE_BUTTON_2  = 9;   // Right
        public static final int MOUSE_BUTTON_3  = 10;  // Middle
        public static final int MOUSE_BUTTON_4  = 11;
        public static final int MOUSE_BUTTON_5  = 12;
        public static final int MOUSE_BUTTON_6  = 13;
        public static final int MOUSE_BUTTON_7  = 14;
        public static final int MOUSE_BUTTON_8  = 15;
        
        // Input focus state (bits 16-23)
        public static final int FOCUS_WINDOW    = 16;
        public static final int FOCUS_KEYBOARD  = 17;
        public static final int FOCUS_MOUSE     = 18;
        public static final int FOCUS_CAPTURED  = 19;
        
        // Input mode state (bits 24-31)
        public static final int MODE_RAW_EVENTS = 24;
        public static final int MODE_FILTERED   = 25;
        public static final int MODE_DIRECT_GLFW = 26;
        public static final int MODE_SECURE     = 27;
        
        // Process state (bits 32-39)
        public static final int PROCESS_FOREGROUND = 32;
        public static final int PROCESS_BACKGROUND = 33;
        public static final int PROCESS_SUSPENDED  = 34;
        public static final int PROCESS_KILLED     = 35;
        
        // Event state (bits 40-47)
        public static final int EVENT_CONSUMED  = 40;
        public static final int EVENT_BUBBLING  = 41;
        public static final int EVENT_CAPTURING = 42;
        public static final int EVENT_SYNTHETIC = 43;
        public static final int EVENT_RECORDED  = 44;
        public static final int EVENT_REPLAYING = 45;
        
        // Container state (bits 48-55)
        public static final int CONTAINER_ACTIVE   = 48;
        public static final int CONTAINER_ENABLED  = 49;
        public static final int CONTAINER_RECORDING = 50;
        public static final int CONTAINER_PLAYBACK = 51;
        
        // Helper: Convert index to BitFlag
        public static BigInteger toBit(int bitPosition) {
            return BitFlagStateMachine.bit(bitPosition);
        }
        
        // Helper: Convert index to long flag
        public static long toFlag(int bitPosition) {
            return 1L << bitPosition;
        }
        
        // Range checks
        public static boolean isModifier(int bitPosition) {
            return bitPosition >= 0 && bitPosition <= 7;
        }
        
        public static boolean isMouseButton(int bitPosition) {
            return bitPosition >= 8 && bitPosition <= 15;
        }
        
        public static boolean isFocus(int bitPosition) {
            return bitPosition >= 16 && bitPosition <= 23;
        }
        
        public static boolean isMode(int bitPosition) {
            return bitPosition >= 24 && bitPosition <= 31;
        }
    }
    
    /**
     * Client Session States (for daemon protocol)
     */
    public static class ClientStates {
        // Connection state (bits 0-7)
        public static final int CONNECTED           = 0;
        public static final int AUTHENTICATED       = 1;
        public static final int DISCOVERING         = 2;
        public static final int HAS_CLAIMED_DEVICES = 3;
        public static final int STREAMING           = 4;
        public static final int PAUSED              = 5;
        public static final int DISCONNECTING       = 6;
        public static final int ERROR_STATE         = 7;
        
        // Capabilities (bits 8-15)
        public static final int SUPPORTS_ENCRYPTION = 8;
        public static final int SUPPORTS_RAW_MODE   = 9;
        public static final int SUPPORTS_FILTERING  = 10;
        public static final int SUPPORTS_BATCH      = 11;
        
        // Heartbeat state (bits 16-23)
        public static final int HEARTBEAT_ENABLED   = 16;
        public static final int HEARTBEAT_WAITING   = 17;
        public static final int HEARTBEAT_TIMEOUT   = 18;
        
        // Backpressure state (bits 24-31)
        public static final int BACKPRESSURE_ACTIVE = 24;
        public static final int FLOW_CONTROL_PAUSED = 25;
        public static final int QUEUE_FULL          = 26;
        
        public static BigInteger toBit(int bitPosition) {
            return BitFlagStateMachine.bit(bitPosition);
        }
        
        public static long toFlag(int bitPosition) {
            return 1L << bitPosition;
        }
    }
    
    /**
     * Device States (for daemon protocol)
     */
    public static class DeviceStates {
        // Claim state (bits 0-7)
        public static final int CLAIMED             = 0;
        public static final int KERNEL_DETACHED     = 1;
        public static final int INTERFACE_CLAIMED   = 2;
        public static final int EXCLUSIVE_ACCESS    = 3;
        
        // Configuration state (bits 8-15)
        public static final int ENCRYPTION_ENABLED  = 8;
        public static final int FILTER_ENABLED      = 9;
        public static final int RAW_MODE            = 10;
        public static final int PARSED_MODE         = 11;
        
        // Streaming state (bits 16-23)
        public static final int STREAMING           = 16;
        public static final int PAUSED              = 17;
        public static final int BACKPRESSURE_ACTIVE = 18;
        public static final int EVENT_BUFFERING     = 19;
        
        // Error state (bits 24-31)
        public static final int DEVICE_ERROR        = 24;
        public static final int TRANSFER_ERROR      = 25;
        public static final int DISCONNECTED        = 26;
        public static final int STALE               = 27;
        
        public static BigInteger toBit(int bitPosition) {
            return BitFlagStateMachine.bit(bitPosition);
        }
        
        public static long toFlag(int bitPosition) {
            return 1L << bitPosition;
        }
    }
    
    /**
     * Source States (for InputSourceRegistry)
     */
    public static class SourceStates {
        // Source lifecycle (bits 0-7)
        public static final int REGISTERED    = 0;
        public static final int ACTIVE        = 1;
        public static final int PAUSED        = 2;
        public static final int THROTTLED     = 3;
        public static final int ERROR         = 4;
        public static final int DISCONNECTED  = 5;
        public static final int INITIALIZING  = 6;
        public static final int SHUTTING_DOWN = 7;
        
        // Capabilities (bits 8-15)
        public static final int MOUSE_ENABLED    = 8;
        public static final int KEYBOARD_ENABLED = 9;
        public static final int TOUCH_ENABLED    = 10;
        public static final int SCROLL_ENABLED   = 11;
        public static final int GAMEPAD_ENABLED  = 12;
        
        // Operation modes (bits 16-23)
        public static final int HIGH_PRECISION_MODE = 16;
        public static final int RAW_INPUT_MODE      = 17;
        public static final int FILTERED_MODE       = 18;
        public static final int RECORDING           = 19;
        public static final int PLAYBACK            = 20;
        public static final int BROADCASTING        = 21;
        
        // Health/diagnostics (bits 24-31)
        public static final int BUFFER_FULL      = 24;
        public static final int DROPPED_EVENTS   = 25;
        public static final int LATENCY_WARNING  = 26;
        public static final int SYNC_ERROR       = 27;
        
        public static BigInteger toBit(int bitPosition) {
            return BitFlagStateMachine.bit(bitPosition);
        }
        
        public static long toFlag(int bitPosition) {
            return 1L << bitPosition;
        }
    }
    
    // ===== EVENT TYPE CONSTANTS =====
    
    /**
     * Event Types - Byte values for protocol
     * (Duplicates EventBytes for consolidation)
     */
    public static class Events {
        // Mouse events (1-14)
        public static final byte EVENT_MOUSE_MOVE_RELATIVE    = 1;
        public static final byte EVENT_MOUSE_BUTTON_DOWN      = 2;
        public static final byte EVENT_MOUSE_BUTTON_UP        = 3;
        public static final byte EVENT_MOUSE_CLICK            = 4;
        public static final byte EVENT_MOUSE_DOUBLE_CLICK     = 5;
        public static final byte EVENT_SCROLL                 = 6;
        public static final byte EVENT_MOUSE_ENTER            = 7;
        public static final byte EVENT_MOUSE_EXIT             = 8;
        public static final byte EVENT_MOUSE_DRAG_START       = 9;
        public static final byte EVENT_MOUSE_DRAG             = 10;
        public static final byte EVENT_MOUSE_DRAG_END         = 11;
        public static final byte EVENT_MOUSE_MOVE_ABSOLUTE    = 12;
        
        // Keyboard events (15-19)
        public static final byte EVENT_KEY_DOWN               = 15;
        public static final byte EVENT_KEY_UP                 = 16;
        public static final byte EVENT_KEY_REPEAT             = 17;
        public static final byte EVENT_KEY_CHAR               = 18;
        public static final byte EVENT_KEY_CHAR_MODS          = 19;
        
        // Focus events (50-51)
        public static final byte EVENT_FOCUS_GAINED           = 50;
        public static final byte EVENT_FOCUS_LOST             = 51;
        
        // Window events (52-55)
        public static final byte EVENT_WINDOW_RESIZE          = 52;
        public static final byte EVENT_WINDOW_MOVE            = 53;
        public static final byte EVENT_WINDOW_CLOSE           = 54;
        public static final byte EVENT_FRAMEBUFFER_RESIZE     = 55;
        
        // State change events (242-247)
        public static final byte EVENT_RELEASE                = (byte) 242;
        public static final byte EVENT_REMOVED                = (byte) 243;
        public static final byte EVENT_CHANGED                = (byte) 244;
        public static final byte EVENT_CHECKED                = (byte) 245;
        public static final byte EVENT_UPDATED                = (byte) 246;
        public static final byte EVENT_ADDED                  = (byte) 247;
        
        // Protocol control (248-255)
        public static final byte TYPE_ERROR                   = (byte) 248;
        public static final byte TYPE_DISCONNECTED            = (byte) 249;
        public static final byte TYPE_PONG                    = (byte) 250;
        public static final byte TYPE_PING                    = (byte) 251;
        public static final byte TYPE_ACCEPT                  = (byte) 252;
        public static final byte TYPE_HELLO                   = (byte) 253;
        public static final byte TYPE_CMD                     = (byte) 254;
        public static final byte TYPE_SHUTDOWN                = (byte) 255;
        
        // Range checks
        public static boolean isMouseEvent(byte type) {
            return type >= 1 && type <= 14;
        }
        
        public static boolean isKeyboardEvent(byte type) {
            return type >= 15 && type <= 19;
        }
        
        public static boolean isFocusEvent(byte type) {
            return type >= 50 && type <= 51;
        }
        
        public static boolean isWindowEvent(byte type) {
            return type >= 52 && type <= 55;
        }
        
        public static boolean isProtocolControl(byte type) {
            return (type & 0xFF) >= 248;
        }
    }
    
    // ===== NAME MAPS (Text Equivalents) =====
    
    /**
     * State Names - Human-readable descriptions
     */
    public static class StateNames {
        private static final Map<Integer, String> INPUT_STATE_NAMES;
        private static final Map<Integer, String> CLIENT_STATE_NAMES;
        private static final Map<Integer, String> DEVICE_STATE_NAMES;
        private static final Map<Integer, String> SOURCE_STATE_NAMES;
        
        static {
            // Input states
            INPUT_STATE_NAMES = new HashMap<>();
            INPUT_STATE_NAMES.put(InputStates.MOD_SHIFT, "SHIFT");
            INPUT_STATE_NAMES.put(InputStates.MOD_CONTROL, "CTRL");
            INPUT_STATE_NAMES.put(InputStates.MOD_ALT, "ALT");
            INPUT_STATE_NAMES.put(InputStates.MOD_SUPER, "SUPER");
            INPUT_STATE_NAMES.put(InputStates.MOUSE_BUTTON_1, "LEFT_BUTTON");
            INPUT_STATE_NAMES.put(InputStates.MOUSE_BUTTON_2, "RIGHT_BUTTON");
            INPUT_STATE_NAMES.put(InputStates.MOUSE_BUTTON_3, "MIDDLE_BUTTON");
            INPUT_STATE_NAMES.put(InputStates.FOCUS_WINDOW, "WINDOW_FOCUS");
            INPUT_STATE_NAMES.put(InputStates.FOCUS_KEYBOARD, "KEYBOARD_FOCUS");
            INPUT_STATE_NAMES.put(InputStates.CONTAINER_ACTIVE, "ACTIVE");
            INPUT_STATE_NAMES.put(InputStates.CONTAINER_ENABLED, "ENABLED");
            
            // Client states
            CLIENT_STATE_NAMES = new HashMap<>();
            CLIENT_STATE_NAMES.put(ClientStates.CONNECTED, "CONNECTED");
            CLIENT_STATE_NAMES.put(ClientStates.AUTHENTICATED, "AUTHENTICATED");
            CLIENT_STATE_NAMES.put(ClientStates.DISCOVERING, "DISCOVERING");
            CLIENT_STATE_NAMES.put(ClientStates.HAS_CLAIMED_DEVICES, "HAS_DEVICES");
            CLIENT_STATE_NAMES.put(ClientStates.STREAMING, "STREAMING");
            CLIENT_STATE_NAMES.put(ClientStates.PAUSED, "PAUSED");
            CLIENT_STATE_NAMES.put(ClientStates.HEARTBEAT_ENABLED, "HEARTBEAT");
            CLIENT_STATE_NAMES.put(ClientStates.BACKPRESSURE_ACTIVE, "BACKPRESSURE");
            
            // Device states
            DEVICE_STATE_NAMES = new HashMap<>();
            DEVICE_STATE_NAMES.put(DeviceStates.CLAIMED, "CLAIMED");
            DEVICE_STATE_NAMES.put(DeviceStates.STREAMING, "STREAMING");
            DEVICE_STATE_NAMES.put(DeviceStates.PAUSED, "PAUSED");
            DEVICE_STATE_NAMES.put(DeviceStates.RAW_MODE, "RAW_MODE");
            DEVICE_STATE_NAMES.put(DeviceStates.PARSED_MODE, "PARSED_MODE");
            DEVICE_STATE_NAMES.put(DeviceStates.DISCONNECTED, "DISCONNECTED");
            
            // Source states
            SOURCE_STATE_NAMES = new HashMap<>();
            SOURCE_STATE_NAMES.put(SourceStates.REGISTERED, "REGISTERED");
            SOURCE_STATE_NAMES.put(SourceStates.ACTIVE, "ACTIVE");
            SOURCE_STATE_NAMES.put(SourceStates.PAUSED, "PAUSED");
            SOURCE_STATE_NAMES.put(SourceStates.KEYBOARD_ENABLED, "KEYBOARD");
            SOURCE_STATE_NAMES.put(SourceStates.MOUSE_ENABLED, "MOUSE");
        }
        
        public static String getInputStateName(int bitPosition) {
            return INPUT_STATE_NAMES.getOrDefault(bitPosition, "BIT_" + bitPosition);
        }
        
        public static String getClientStateName(int bitPosition) {
            return CLIENT_STATE_NAMES.getOrDefault(bitPosition, "BIT_" + bitPosition);
        }
        
        public static String getDeviceStateName(int bitPosition) {
            return DEVICE_STATE_NAMES.getOrDefault(bitPosition, "BIT_" + bitPosition);
        }
        
        public static String getSourceStateName(int bitPosition) {
            return SOURCE_STATE_NAMES.getOrDefault(bitPosition, "BIT_" + bitPosition);
        }
        
        /**
         * Get map for BitFlagStateMachine.getStateString()
         */
        public static Map<Integer, String> getInputStateMap() {
            return Collections.unmodifiableMap(INPUT_STATE_NAMES);
        }
        
        public static Map<Integer, String> getClientStateMap() {
            return Collections.unmodifiableMap(CLIENT_STATE_NAMES);
        }
        
        public static Map<Integer, String> getDeviceStateMap() {
            return Collections.unmodifiableMap(DEVICE_STATE_NAMES);
        }
        
        public static Map<Integer, String> getSourceStateMap() {
            return Collections.unmodifiableMap(SOURCE_STATE_NAMES);
        }
    }
    
    /**
     * Event Names - Human-readable descriptions
     */
    public static class EventNames {
        private static final Map<Byte, String> EVENT_NAME_MAP;
        
        static {
            EVENT_NAME_MAP = new HashMap<>();
            EVENT_NAME_MAP.put(Events.EVENT_MOUSE_MOVE_RELATIVE, "MOUSE_MOVE_RELATIVE");
            EVENT_NAME_MAP.put(Events.EVENT_MOUSE_BUTTON_DOWN, "MOUSE_BUTTON_DOWN");
            EVENT_NAME_MAP.put(Events.EVENT_MOUSE_BUTTON_UP, "MOUSE_BUTTON_UP");
            EVENT_NAME_MAP.put(Events.EVENT_KEY_DOWN, "KEY_DOWN");
            EVENT_NAME_MAP.put(Events.EVENT_KEY_UP, "KEY_UP");
            EVENT_NAME_MAP.put(Events.EVENT_FOCUS_GAINED, "FOCUS_GAINED");
            EVENT_NAME_MAP.put(Events.EVENT_FOCUS_LOST, "FOCUS_LOST");
            EVENT_NAME_MAP.put(Events.TYPE_PING, "PING");
            EVENT_NAME_MAP.put(Events.TYPE_PONG, "PONG");
            EVENT_NAME_MAP.put(Events.TYPE_ERROR, "ERROR");
            EVENT_NAME_MAP.put(Events.TYPE_CMD, "COMMAND");
        }
        
        public static String getEventName(byte eventType) {
            return EVENT_NAME_MAP.getOrDefault(eventType, "UNKNOWN_" + (eventType & 0xFF));
        }
        
        public static String getEventName(NoteBytesReadOnly eventType) {
            return getEventName(eventType.getAsByte());
        }
    }
    
    // ===== HELPER FUNCTIONS =====
    
    /**
     * Describe a state as human-readable string
     */
    public static String describeInputState(BigInteger state) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        
        for (int i = 0; i < state.bitLength(); i++) {
            if (state.testBit(i)) {
                if (!first) sb.append(", ");
                sb.append(StateNames.getInputStateName(i));
                first = false;
            }
        }
        
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Describe client state
     */
    public static String describeClientState(BigInteger state) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        
        for (int i = 0; i < state.bitLength(); i++) {
            if (state.testBit(i)) {
                if (!first) sb.append(", ");
                sb.append(StateNames.getClientStateName(i));
                first = false;
            }
        }
        
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Describe device state
     */
    public static String describeDeviceState(BigInteger state) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        
        for (int i = 0; i < state.bitLength(); i++) {
            if (state.testBit(i)) {
                if (!first) sb.append(", ");
                sb.append(StateNames.getDeviceStateName(i));
                first = false;
            }
        }
        
        sb.append("]");
        return sb.toString();
    }
}