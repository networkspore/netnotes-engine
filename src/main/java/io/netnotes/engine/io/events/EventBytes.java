package io.netnotes.engine.io.events;

import java.util.HashMap;
import java.util.Map;

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
    public static final NoteBytesReadOnly EVENT_MOUSE_MOVE_RELATIVE    = new NoteBytesReadOnly((byte)1);
    public static final NoteBytesReadOnly EVENT_MOUSE_BUTTON_DOWN      = new NoteBytesReadOnly((byte)2);
    public static final NoteBytesReadOnly EVENT_MOUSE_BUTTON_UP        = new NoteBytesReadOnly((byte)3);
    public static final NoteBytesReadOnly EVENT_MOUSE_CLICK            = new NoteBytesReadOnly((byte)4);
    public static final NoteBytesReadOnly EVENT_MOUSE_DOUBLE_CLICK     = new NoteBytesReadOnly((byte)5);
    public static final NoteBytesReadOnly EVENT_SCROLL                 = new NoteBytesReadOnly((byte)6);
    public static final NoteBytesReadOnly EVENT_MOUSE_ENTER            = new NoteBytesReadOnly((byte)7);
    public static final NoteBytesReadOnly EVENT_MOUSE_EXIT             = new NoteBytesReadOnly((byte)8);
    public static final NoteBytesReadOnly EVENT_MOUSE_DRAG_START       = new NoteBytesReadOnly((byte)9);
    public static final NoteBytesReadOnly EVENT_MOUSE_DRAG             = new NoteBytesReadOnly((byte)10);
    public static final NoteBytesReadOnly EVENT_MOUSE_DRAG_END         = new NoteBytesReadOnly((byte)11);
    public static final NoteBytesReadOnly EVENT_MOUSE_MOVE_ABSOLUTE    = new NoteBytesReadOnly((byte)12);
    
    // ===== new NoteBytesReadOnly((byte)KEYBOARD EVENTS (15-19) =====
    public static final NoteBytesReadOnly EVENT_KEY_DOWN               = new NoteBytesReadOnly((byte)15);
    public static final NoteBytesReadOnly EVENT_KEY_UP                 = new NoteBytesReadOnly((byte)16);
    public static final NoteBytesReadOnly EVENT_KEY_REPEAT             = new NoteBytesReadOnly((byte)17);
    public static final NoteBytesReadOnly EVENT_KEY_CHAR               = new NoteBytesReadOnly((byte)18);
    public static final NoteBytesReadOnly EVENT_KEY_CHAR_MODS          = new NoteBytesReadOnly((byte)19);
    
    // ===== new NoteBytesReadOnly((byte)FOCUS EVENTS (50-51) =====
    public static final NoteBytesReadOnly EVENT_FOCUS_GAINED           = new NoteBytesReadOnly((byte)50);
    public static final NoteBytesReadOnly EVENT_FOCUS_LOST             = new NoteBytesReadOnly((byte)51);
    
    // ===== new NoteBytesReadOnly((byte)WINDOW EVENTS (52-55) =====
    public static final NoteBytesReadOnly EVENT_WINDOW_RESIZE          = new NoteBytesReadOnly((byte)52);
    public static final NoteBytesReadOnly EVENT_WINDOW_MOVE            = new NoteBytesReadOnly((byte)53);
    public static final NoteBytesReadOnly EVENT_WINDOW_CLOSE           = new NoteBytesReadOnly((byte)54);
    public static final NoteBytesReadOnly EVENT_FRAMEBUFFER_RESIZE     = new NoteBytesReadOnly((byte)55);

    public static final NoteBytesReadOnly EVENT_RAW_HID                 = new NoteBytesReadOnly((byte)60);


    public static final NoteBytesReadOnly TYPE_ENCRYPTION_OFFER        = new NoteBytesReadOnly((byte) 225);
    public static final NoteBytesReadOnly TYPE_ENCRYPTION_ACCEPT       = new NoteBytesReadOnly((byte) 226);
    public static final NoteBytesReadOnly TYPE_ENCRYPTION_READY        = new NoteBytesReadOnly((byte) 227);
    public static final NoteBytesReadOnly TYPE_ENCRYPTED               = new NoteBytesReadOnly((byte) 228);
    public static final NoteBytesReadOnly TYPE_ENCRYPTION_DECLINE      = new NoteBytesReadOnly((byte) 229);

    
    // ===== new NoteBytesReadOnly((byte)STATE CHANGE EVENTS (242-247) =====
    public static final NoteBytesReadOnly EVENT_RELEASE                = new NoteBytesReadOnly((byte) 242);
    public static final NoteBytesReadOnly EVENT_REMOVED                = new NoteBytesReadOnly((byte) 243);
    public static final NoteBytesReadOnly EVENT_CHANGED                = new NoteBytesReadOnly((byte) 244);
    public static final NoteBytesReadOnly EVENT_CHECKED                = new NoteBytesReadOnly((byte) 245);
    public static final NoteBytesReadOnly EVENT_UPDATED                = new NoteBytesReadOnly((byte) 246);
    public static final NoteBytesReadOnly EVENT_ADDED                  = new NoteBytesReadOnly((byte) 247);
    
    public static final NoteBytesReadOnly TYPE_ERROR                    = new NoteBytesReadOnly((byte)248);
    public static final NoteBytesReadOnly TYPE_DISCONNECTED             = new NoteBytesReadOnly((byte)249);
    public static final NoteBytesReadOnly TYPE_PONG                     = new NoteBytesReadOnly((byte)250);
    public static final NoteBytesReadOnly TYPE_PING                     = new NoteBytesReadOnly((byte)251);
    public static final NoteBytesReadOnly TYPE_ACCEPT                   = new NoteBytesReadOnly((byte)252);       // trust ack
    public static final NoteBytesReadOnly TYPE_HELLO                    = new NoteBytesReadOnly((byte)253);        // identity bootstrap
    public static final NoteBytesReadOnly TYPE_CMD                      = new NoteBytesReadOnly((byte)254);
    public static final NoteBytesReadOnly TYPE_SHUTDOWN                 = new NoteBytesReadOnly((byte)255);

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
        return eventType >= EVENT_MOUSE_MOVE_RELATIVE.getAsByte() && eventType <= EVENT_MOUSE_MOVE_ABSOLUTE.getAsByte();
    }
    
    /**
     * Check if event type is a keyboard event
     */
    public static boolean isKeyboardEvent(byte eventType) {
        return eventType >= EVENT_KEY_DOWN.getAsByte() && eventType <= EVENT_KEY_CHAR_MODS.getAsByte();
    }
    
    /**
     * Check if event type is a focus event
     */
    public static boolean isFocusEvent(byte eventType) {
        return eventType == EVENT_FOCUS_GAINED.getAsByte() || eventType == EVENT_FOCUS_LOST.getAsByte();
    }
    
    /**
     * Check if event type is a window event
     */
    public static boolean isWindowEvent(byte eventType) {
        return eventType >= EVENT_WINDOW_RESIZE.getAsByte() && eventType <= EVENT_FRAMEBUFFER_RESIZE.getAsByte();
    }
    
    /**
     * Check if event type is a protocol message
     */
    public static boolean isProtocolMessage(byte eventType) {
        return (eventType & 0xFF) >= 248;  // 248-255
    }
    
    public static final Map<NoteBytesReadOnly, String> EVENT_NAME_MAP;

    static{
        EVENT_NAME_MAP = new HashMap<>();
        EVENT_NAME_MAP.put(EVENT_MOUSE_MOVE_RELATIVE,"MOUSE_MOVE_RELATIVE");
        EVENT_NAME_MAP.put(EVENT_MOUSE_BUTTON_DOWN,"MOUSE_BUTTON_DOWN");
        EVENT_NAME_MAP.put(EVENT_MOUSE_BUTTON_UP,"MOUSE_BUTTON_UP");
        EVENT_NAME_MAP.put(EVENT_MOUSE_CLICK,"MOUSE_CLICK");
        EVENT_NAME_MAP.put(EVENT_MOUSE_DOUBLE_CLICK,"MOUSE_DOUBLE_CLICK");
        EVENT_NAME_MAP.put(EVENT_SCROLL,"SCROLL");
        EVENT_NAME_MAP.put(EVENT_MOUSE_MOVE_ABSOLUTE,"MOUSE_MOVE_ABSOLUTE");
            
        EVENT_NAME_MAP.put(EVENT_KEY_DOWN,"KEY_DOWN");
        EVENT_NAME_MAP.put(EVENT_KEY_UP,"KEY_UP");
        EVENT_NAME_MAP.put(EVENT_KEY_REPEAT,"KEY_REPEAT");
        EVENT_NAME_MAP.put(EVENT_KEY_CHAR,"KEY_CHAR");
        EVENT_NAME_MAP.put(EVENT_KEY_CHAR_MODS,"KEY_CHAR_MODS");
            
        EVENT_NAME_MAP.put(EVENT_FOCUS_GAINED,"FOCUS_GAINED");
        EVENT_NAME_MAP.put(EVENT_FOCUS_LOST,"FOCUS_LOST");
            
        EVENT_NAME_MAP.put(EVENT_WINDOW_RESIZE,"WINDOW_RESIZE");
        EVENT_NAME_MAP.put(EVENT_WINDOW_CLOSE,"WINDOW_CLOSE");
        EVENT_NAME_MAP.put(EVENT_FRAMEBUFFER_RESIZE,"FRAMEBUFFER_RESIZE");
    }
    /**
     * Get human-readable event name
     */
    public static String getEventName(NoteBytesReadOnly eventType) {
        String name = EVENT_NAME_MAP.get(eventType);
        return name != null ? name : "UNKNOWN(" + (eventType.getAsByte() & 0xFF) + ")";
    }
}