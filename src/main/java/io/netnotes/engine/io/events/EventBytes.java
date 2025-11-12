package io.netnotes.engine.io.events;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * EventBytes - Standardized event type constants.
 * 
 * IMPORTANT: All event types are BYTE (0-255) for protocol consistency.
 * This matches the C++ implementation in event_bytes.h
 * 
 * Organization:
 * - Mouse events: 1-14
 * - Keyboard events: 15-19
 * - Focus events: 50-51
 * - Window events: 52-55
 * - State change events: 242-247
 * - Protocol control: 248-255
 */
public class EventBytes {
    
    // ===== MOUSE EVENTS (1-14) =====
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
    
    // ===== KEYBOARD EVENTS (15-19) =====
    public static final byte EVENT_KEY_DOWN               = 15;
    public static final byte EVENT_KEY_UP                 = 16;
    public static final byte EVENT_KEY_REPEAT             = 17;
    public static final byte EVENT_KEY_CHAR               = 18;
    public static final byte EVENT_KEY_CHAR_MODS          = 19;
    
    // ===== FOCUS EVENTS (50-51) =====
    public static final byte EVENT_FOCUS_GAINED           = 50;
    public static final byte EVENT_FOCUS_LOST             = 51;
    
    // ===== WINDOW EVENTS (52-55) =====
    public static final byte EVENT_WINDOW_RESIZE          = 52;
    public static final byte EVENT_WINDOW_MOVE            = 53;
    public static final byte EVENT_WINDOW_CLOSE           = 54;
    public static final byte EVENT_FRAMEBUFFER_RESIZE     = 55;
    
    // ===== STATE CHANGE EVENTS (242-247) =====
    public static final byte EVENT_RELEASE                = (byte) 242;
    public static final byte EVENT_REMOVED                = (byte) 243;
    public static final byte EVENT_CHANGED                = (byte) 244;
    public static final byte EVENT_CHECKED                = (byte) 245;
    public static final byte EVENT_UPDATED                = (byte) 246;
    public static final byte EVENT_ADDED                  = (byte) 247;
    
    // ===== PROTOCOL CONTROL MESSAGES (248-255) =====
    public static final byte TYPE_ERROR                   = (byte) 248;
    public static final byte TYPE_DISCONNECTED            = (byte) 249;
    public static final byte TYPE_PONG                    = (byte) 250;
    public static final byte TYPE_PING                    = (byte) 251;
    public static final byte TYPE_ACCEPT                  = (byte) 252;  // trust ack
    public static final byte TYPE_HELLO                   = (byte) 253;  // identity bootstrap
    public static final byte TYPE_CMD                     = (byte) 254;
    public static final byte TYPE_SHUTDOWN                = (byte) 255;
    
    // ===== NOTEBYTESREADONLY WRAPPERS =====
        
    public static final NoteBytesReadOnly NB_EVENT_MOUSE_MOVE_RELATIVE = nb(EVENT_MOUSE_MOVE_RELATIVE);
    public static final NoteBytesReadOnly NB_EVENT_MOUSE_BUTTON_DOWN   = nb(EVENT_MOUSE_BUTTON_DOWN);
    public static final NoteBytesReadOnly NB_EVENT_MOUSE_BUTTON_UP     = nb(EVENT_MOUSE_BUTTON_UP);
    public static final NoteBytesReadOnly NB_EVENT_MOUSE_CLICK         = nb(EVENT_MOUSE_CLICK);
    public static final NoteBytesReadOnly NB_EVENT_MOUSE_DOUBLE_CLICK  = nb(EVENT_MOUSE_DOUBLE_CLICK);
    public static final NoteBytesReadOnly NB_EVENT_SCROLL              = nb(EVENT_SCROLL);
    public static final NoteBytesReadOnly NB_EVENT_MOUSE_ENTER         = nb(EVENT_MOUSE_ENTER);
    public static final NoteBytesReadOnly NB_EVENT_MOUSE_EXIT          = nb(EVENT_MOUSE_EXIT);
    public static final NoteBytesReadOnly NB_EVENT_MOUSE_DRAG_START    = nb(EVENT_MOUSE_DRAG_START);
    public static final NoteBytesReadOnly NB_EVENT_MOUSE_DRAG          = nb(EVENT_MOUSE_DRAG);
    public static final NoteBytesReadOnly NB_EVENT_MOUSE_DRAG_END      = nb(EVENT_MOUSE_DRAG_END);
    public static final NoteBytesReadOnly NB_EVENT_MOUSE_MOVE_ABSOLUTE = nb(EVENT_MOUSE_MOVE_ABSOLUTE);

    public static final NoteBytesReadOnly NB_EVENT_KEY_DOWN            = nb(EVENT_KEY_DOWN);
    public static final NoteBytesReadOnly NB_EVENT_KEY_UP              = nb(EVENT_KEY_UP);
    public static final NoteBytesReadOnly NB_EVENT_KEY_REPEAT          = nb(EVENT_KEY_REPEAT);
    public static final NoteBytesReadOnly NB_EVENT_KEY_CHAR            = nb(EVENT_KEY_CHAR);
    public static final NoteBytesReadOnly NB_EVENT_KEY_CHAR_MODS       = nb(EVENT_KEY_CHAR_MODS);

    public static final NoteBytesReadOnly NB_EVENT_FOCUS_GAINED        = nb(EVENT_FOCUS_GAINED);
    public static final NoteBytesReadOnly NB_EVENT_FOCUS_LOST          = nb(EVENT_FOCUS_LOST);

    public static final NoteBytesReadOnly NB_EVENT_WINDOW_RESIZE       = nb(EVENT_WINDOW_RESIZE);
    public static final NoteBytesReadOnly NB_EVENT_WINDOW_MOVE         = nb(EVENT_WINDOW_MOVE);
    public static final NoteBytesReadOnly NB_EVENT_WINDOW_CLOSE        = nb(EVENT_WINDOW_CLOSE);
    public static final NoteBytesReadOnly NB_EVENT_FRAMEBUFFER_RESIZE  = nb(EVENT_FRAMEBUFFER_RESIZE);

    public static final NoteBytesReadOnly NB_EVENT_RELEASE             = nb(EVENT_RELEASE);
    public static final NoteBytesReadOnly NB_EVENT_REMOVED             = nb(EVENT_REMOVED);
    public static final NoteBytesReadOnly NB_EVENT_CHANGED             = nb(EVENT_CHANGED);
    public static final NoteBytesReadOnly NB_EVENT_CHECKED             = nb(EVENT_CHECKED);
    public static final NoteBytesReadOnly NB_EVENT_UPDATED             = nb(EVENT_UPDATED);
    public static final NoteBytesReadOnly NB_EVENT_ADDED               = nb(EVENT_ADDED);

    public static final NoteBytesReadOnly NB_TYPE_ERROR                = nb(TYPE_ERROR);
    public static final NoteBytesReadOnly NB_TYPE_DISCONNECTED         = nb(TYPE_DISCONNECTED);
    public static final NoteBytesReadOnly NB_TYPE_PONG                 = nb(TYPE_PONG);
    public static final NoteBytesReadOnly NB_TYPE_PING                 = nb(TYPE_PING);
    public static final NoteBytesReadOnly NB_TYPE_ACCEPT               = nb(TYPE_ACCEPT);
    public static final NoteBytesReadOnly NB_TYPE_HELLO                = nb(TYPE_HELLO);
    public static final NoteBytesReadOnly NB_TYPE_CMD                  = nb(TYPE_CMD);
    public static final NoteBytesReadOnly NB_TYPE_SHUTDOWN             = nb(TYPE_SHUTDOWN);

// ===== INTERNAL HELPER =====
private static NoteBytesReadOnly nb(byte value) {
    return new NoteBytesReadOnly(value);
}
    // Add others as needed
    
    // ===== STATE FLAGS =====
    /**
     * State flags for input events.
     * Uses int (32-bit) for compatibility with packets.
     * Matches C++ EventBytes::StateFlags
     */
    public static class StateFlags {
        // Modifier keys (bits 0-7)
        public static final int MOD_SHIFT       = 0x0001;
        public static final int MOD_CONTROL     = 0x0002;
        public static final int MOD_ALT         = 0x0004;
        public static final int MOD_SUPER       = 0x0008;  // Windows/Command key
        public static final int MOD_CAPS_LOCK   = 0x0010;
        public static final int MOD_NUM_LOCK    = 0x0020;
        public static final int MOD_SCROLL_LOCK = 0x0040;
        
        // Modifier mask
        public static final int MOD_MASK = 0x000000FF;
        
        // Mouse buttons (bits 8-15)
        public static final int MOUSE_BUTTON_1  = 0x0100;  // Left
        public static final int MOUSE_BUTTON_2  = 0x0200;  // Right
        public static final int MOUSE_BUTTON_3  = 0x0400;  // Middle
        public static final int MOUSE_BUTTON_4  = 0x0800;
        public static final int MOUSE_BUTTON_5  = 0x1000;
        public static final int MOUSE_BUTTON_6  = 0x2000;
        public static final int MOUSE_BUTTON_7  = 0x4000;
        public static final int MOUSE_BUTTON_8  = 0x8000;
        
        // Mouse button mask
        public static final int MOUSE_BUTTON_MASK = 0x0000FF00;
        
        // Event state flags (bits 16-23)
        public static final int STATE_CONSUMED  = 0x010000;  // Event has been handled
        public static final int STATE_BUBBLING  = 0x020000;  // Event is bubbling up
        public static final int STATE_CAPTURING = 0x040000;  // Event is in capture phase
        public static final int STATE_SYNTHETIC = 0x080000;  // Generated, not from OS
        public static final int STATE_RECORDED  = 0x100000;  // Event was recorded
        public static final int STATE_REPLAYING = 0x200000;  // Event is from playback
        
        // Event state mask
        public static final int EVENT_STATE_MASK = 0x00FF0000;
        
        /**
         * Check if flag is set
         */
        public static boolean hasFlag(int state, int flag) {
            return (state & flag) != 0;
        }
        
        /**
         * Set flag
         */
        public static int setFlag(int state, int flag) {
            return state | flag;
        }
        
        /**
         * Clear flag
         */
        public static int clearFlag(int state, int flag) {
            return state & ~flag;
        }
        
        /**
         * Toggle flag
         */
        public static int toggleFlag(int state, int flag) {
            return state ^ flag;
        }
        
        /**
         * Get modifier flags only
         */
        public static int getModifiers(int state) {
            return state & MOD_MASK;
        }
        
        /**
         * Get mouse button flags only
         */
        public static int getMouseButtons(int state) {
            return state & MOUSE_BUTTON_MASK;
        }
        
        /**
         * Get event state flags only
         */
        public static int getEventState(int state) {
            return state & EVENT_STATE_MASK;
        }
        
        /**
         * Check if any modifier is pressed
         */
        public static boolean hasAnyModifier(int state) {
            return (state & MOD_MASK) != 0;
        }
        
        /**
         * Check if any mouse button is pressed
         */
        public static boolean hasAnyMouseButton(int state) {
            return (state & MOUSE_BUTTON_MASK) != 0;
        }
        
        /**
         * Describe flags as string (for debugging)
         */
        public static String describe(int state) {
            StringBuilder sb = new StringBuilder();
            
            // Modifiers
            if (hasFlag(state, MOD_SHIFT)) sb.append("SHIFT+");
            if (hasFlag(state, MOD_CONTROL)) sb.append("CTRL+");
            if (hasFlag(state, MOD_ALT)) sb.append("ALT+");
            if (hasFlag(state, MOD_SUPER)) sb.append("SUPER+");
            if (hasFlag(state, MOD_CAPS_LOCK)) sb.append("CAPS+");
            if (hasFlag(state, MOD_NUM_LOCK)) sb.append("NUM+");
            
            // Mouse buttons
            if (hasFlag(state, MOUSE_BUTTON_1)) sb.append("BTN1+");
            if (hasFlag(state, MOUSE_BUTTON_2)) sb.append("BTN2+");
            if (hasFlag(state, MOUSE_BUTTON_3)) sb.append("BTN3+");
            
            // Event state
            if (hasFlag(state, STATE_CONSUMED)) sb.append("CONSUMED+");
            if (hasFlag(state, STATE_BUBBLING)) sb.append("BUBBLING+");
            if (hasFlag(state, STATE_SYNTHETIC)) sb.append("SYNTHETIC+");
            
            String result = sb.toString();
            return result.isEmpty() ? "NONE" : result.substring(0, result.length() - 1);
        }
    }
    
    // ===== UTILITY METHODS =====
    
    /**
     * Check if event type is a mouse event
     */
    public static boolean isMouseEvent(byte eventType) {
        return eventType >= EVENT_MOUSE_MOVE_RELATIVE && eventType <= EVENT_MOUSE_MOVE_ABSOLUTE;
    }
    
    /**
     * Check if event type is a keyboard event
     */
    public static boolean isKeyboardEvent(byte eventType) {
        return eventType >= EVENT_KEY_DOWN && eventType <= EVENT_KEY_CHAR_MODS;
    }
    
    /**
     * Check if event type is a focus event
     */
    public static boolean isFocusEvent(byte eventType) {
        return eventType == EVENT_FOCUS_GAINED || eventType == EVENT_FOCUS_LOST;
    }
    
    /**
     * Check if event type is a window event
     */
    public static boolean isWindowEvent(byte eventType) {
        return eventType >= EVENT_WINDOW_RESIZE && eventType <= EVENT_FRAMEBUFFER_RESIZE;
    }
    
    /**
     * Check if event type is a protocol message
     */
    public static boolean isProtocolMessage(byte eventType) {
        return (eventType & 0xFF) >= 248;  // 248-255
    }
    
    /**
     * Get human-readable event name
     */
    public static String getEventName(byte eventType) {
        return switch (eventType) {
            case EVENT_MOUSE_MOVE_RELATIVE -> "MOUSE_MOVE_RELATIVE";
            case EVENT_MOUSE_BUTTON_DOWN -> "MOUSE_BUTTON_DOWN";
            case EVENT_MOUSE_BUTTON_UP -> "MOUSE_BUTTON_UP";
            case EVENT_MOUSE_CLICK -> "MOUSE_CLICK";
            case EVENT_MOUSE_DOUBLE_CLICK -> "MOUSE_DOUBLE_CLICK";
            case EVENT_SCROLL -> "SCROLL";
            case EVENT_MOUSE_MOVE_ABSOLUTE -> "MOUSE_MOVE_ABSOLUTE";
            
            case EVENT_KEY_DOWN -> "KEY_DOWN";
            case EVENT_KEY_UP -> "KEY_UP";
            case EVENT_KEY_REPEAT -> "KEY_REPEAT";
            case EVENT_KEY_CHAR -> "KEY_CHAR";
            case EVENT_KEY_CHAR_MODS -> "KEY_CHAR_MODS";
            
            case EVENT_FOCUS_GAINED -> "FOCUS_GAINED";
            case EVENT_FOCUS_LOST -> "FOCUS_LOST";
            
            case EVENT_WINDOW_RESIZE -> "WINDOW_RESIZE";
            case EVENT_WINDOW_CLOSE -> "WINDOW_CLOSE";
            case EVENT_FRAMEBUFFER_RESIZE -> "FRAMEBUFFER_RESIZE";
            
            case TYPE_ERROR -> "ERROR";
            case TYPE_DISCONNECTED -> "DISCONNECTED";
            case TYPE_ACCEPT -> "ACCEPT";
            case TYPE_CMD -> "CMD";
            case TYPE_SHUTDOWN -> "SHUTDOWN";
            
            default -> "UNKNOWN(" + (eventType & 0xFF) + ")";
        };
    }
}