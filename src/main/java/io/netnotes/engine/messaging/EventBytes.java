package io.netnotes.engine.messaging;


import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * Event Bytes - organized by category
 */
public class EventBytes {

    public static final NoteBytesReadOnly EVENT_MOUSE_MOVE_RELATIVE    = new NoteBytesReadOnly((short) 1);
    public static final NoteBytesReadOnly EVENT_MOUSE_BUTTON_DOWN      = new NoteBytesReadOnly((short) 2);
    public static final NoteBytesReadOnly EVENT_MOUSE_BUTTON_UP        = new NoteBytesReadOnly((short) 3);
    public static final NoteBytesReadOnly EVENT_MOUSE_CLICK            = new NoteBytesReadOnly((short) 4);
    public static final NoteBytesReadOnly EVENT_MOUSE_DOUBLE_CLICK     = new NoteBytesReadOnly((short) 5);
    public static final NoteBytesReadOnly EVENT_SCROLL                 = new NoteBytesReadOnly((short) 6);
    public static final NoteBytesReadOnly EVENT_MOUSE_ENTER            = new NoteBytesReadOnly((short) 7);
    public static final NoteBytesReadOnly EVENT_MOUSE_EXIT             = new NoteBytesReadOnly((short) 8);
    public static final NoteBytesReadOnly EVENT_MOUSE_DRAG_START       = new NoteBytesReadOnly((short) 9);
    public static final NoteBytesReadOnly EVENT_MOUSE_DRAG             = new NoteBytesReadOnly((short) 10);
    public static final NoteBytesReadOnly EVENT_MOUSE_DRAG_END         = new NoteBytesReadOnly((short) 11);
    public static final NoteBytesReadOnly EVENT_MOUSE_MOVE_ABSOLUTE    = new NoteBytesReadOnly((short) 12);
    

    public static final NoteBytesReadOnly EVENT_KEY_DOWN               = new NoteBytesReadOnly((short) 15);
    public static final NoteBytesReadOnly EVENT_KEY_UP                 = new NoteBytesReadOnly((short) 16);
    public static final NoteBytesReadOnly EVENT_KEY_REPEAT             = new NoteBytesReadOnly((short) 17);
    public static final NoteBytesReadOnly EVENT_KEY_CHAR               = new NoteBytesReadOnly((short) 18);
    public static final NoteBytesReadOnly EVENT_KEY_CHAR_MODS          = new NoteBytesReadOnly((short) 19);
    

    public static final NoteBytesReadOnly EVENT_FOCUS_GAINED           = new NoteBytesReadOnly((short) 50);
    public static final NoteBytesReadOnly EVENT_FOCUS_LOST             = new NoteBytesReadOnly((short) 51);
    

    public static final NoteBytesReadOnly EVENT_WINDOW_RESIZE          = new NoteBytesReadOnly((short) 52);
    public static final NoteBytesReadOnly EVENT_WINDOW_MOVE            = new NoteBytesReadOnly((short) 53);
    public static final NoteBytesReadOnly EVENT_WINDOW_CLOSE           = new NoteBytesReadOnly((short) 54);
    public static final NoteBytesReadOnly EVENT_FRAMEBUFFER_RESIZE     = new NoteBytesReadOnly((short) 55);



    public static final NoteBytesReadOnly EVENT_RELEASE = new NoteBytesReadOnly((short) 242);
    public static final NoteBytesReadOnly EVENT_REMOVED = new NoteBytesReadOnly((short) 243);
    public static final NoteBytesReadOnly EVENT_CHANGED = new NoteBytesReadOnly((short) 244);
    public static final NoteBytesReadOnly EVENT_CHECKED = new NoteBytesReadOnly((short) 245);
    public static final NoteBytesReadOnly EVENT_UPDATED = new NoteBytesReadOnly((short) 246);
    public static final NoteBytesReadOnly EVENT_ADDED    = new NoteBytesReadOnly((short) 247);

    public static final NoteBytesReadOnly TYPE_SHUTDOWN         = new NoteBytesReadOnly((short) 255);
    public static final NoteBytesReadOnly TYPE_CMD              = new NoteBytesReadOnly((short) 254);
    public static final NoteBytesReadOnly TYPE_HELLO            = new NoteBytesReadOnly((short) 253);   // identity bootstrap
    public static final NoteBytesReadOnly TYPE_ACCEPT           = new NoteBytesReadOnly((short) 252);  // trust ack
    public static final NoteBytesReadOnly TYPE_PING             = new NoteBytesReadOnly((short) 251);
    public static final NoteBytesReadOnly TYPE_PONG             = new NoteBytesReadOnly((short) 250);
    public static final NoteBytesReadOnly TYPE_DISCONNECTED     = new NoteBytesReadOnly((short) 249);
    public static final NoteBytesReadOnly TYPE_ERROR            = new NoteBytesReadOnly((short) 248);
    

    public static final class StateFlags {
        // Modifier keys (bits 0-7)
        public static final int MOD_SHIFT       = 0x0001;
        public static final int MOD_CONTROL     = 0x0002;
        public static final int MOD_ALT         = 0x0004;
        public static final int MOD_SUPER       = 0x0008;  // Windows/Command key
        public static final int MOD_CAPS_LOCK   = 0x0010;
        public static final int MOD_NUM_LOCK    = 0x0020;
        
        // Mouse buttons (bits 8-15)
        public static final int MOUSE_BUTTON_1  = 0x0100;  // Left
        public static final int MOUSE_BUTTON_2  = 0x0200;  // Right
        public static final int MOUSE_BUTTON_3  = 0x0400;  // Middle
        public static final int MOUSE_BUTTON_4  = 0x0800;
        public static final int MOUSE_BUTTON_5  = 0x1000;
        
        // Event state flags (bits 16-23)
        public static final int STATE_CONSUMED  = 0x010000;  // Event has been handled
        public static final int STATE_BUBBLING  = 0x020000;  // Event is bubbling up
        public static final int STATE_CAPTURING = 0x040000;  // Event is in capture phase
        public static final int STATE_SYNTHETIC = 0x080000;  // Generated, not from OS
        
        public static boolean hasFlag(int state, int flag) {
            return (state & flag) != 0;
        }
        
        public static int setFlag(int state, int flag) {
            return state | flag;
        }
        
        public static int clearFlag(int state, int flag) {
            return state & ~flag;
        }
    }
}