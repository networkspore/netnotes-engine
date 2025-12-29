package io.netnotes.engine.io.input.events;

import java.util.HashMap;
import java.util.Map;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * EventBytes - Standardized event type constants with descriptions
 * 
 * IMPORTANT: All event types are BYTE (0-255) for protocol consistency.
 * 
 * Organization:
 * - Mouse events: 1-14
 * - Keyboard events: 15-19
 * - Container events: 50-59 (focus, resize, close, etc.)
 * - State change events: 242-247
 * - Protocol control: 248-255
 */
public class EventBytes {
    
    // ===== MOUSE EVENTS =====
    public static final NoteBytesReadOnly EVENT_MOUSE_MOVE_RELATIVE =
        new NoteBytesReadOnly("mouse_move_rel");

    public static final NoteBytesReadOnly EVENT_MOUSE_BUTTON_DOWN =
            new NoteBytesReadOnly("mouse_button_down");

    public static final NoteBytesReadOnly EVENT_MOUSE_BUTTON_UP =
            new NoteBytesReadOnly("mouse_button_up");

    public static final NoteBytesReadOnly EVENT_MOUSE_CLICK =
            new NoteBytesReadOnly("mouse_click");

    public static final NoteBytesReadOnly EVENT_MOUSE_DOUBLE_CLICK =
            new NoteBytesReadOnly("mouse_double_click");

    public static final NoteBytesReadOnly EVENT_MOUSE_SCROLL =
            new NoteBytesReadOnly("mouse_scroll");

    public static final NoteBytesReadOnly EVENT_MOUSE_ENTER =
            new NoteBytesReadOnly("mouse_enter");

    public static final NoteBytesReadOnly EVENT_MOUSE_EXIT =
            new NoteBytesReadOnly("mouse_exit");

    public static final NoteBytesReadOnly EVENT_MOUSE_DRAG_START =
            new NoteBytesReadOnly("mouse_drag_start");

    public static final NoteBytesReadOnly EVENT_MOUSE_DRAG =
            new NoteBytesReadOnly("mouse_drag");

    public static final NoteBytesReadOnly EVENT_MOUSE_DRAG_END =
            new NoteBytesReadOnly("mouse_drag_end");

    public static final NoteBytesReadOnly EVENT_MOUSE_MOVE_ABSOLUTE =
            new NoteBytesReadOnly("mouse_move_abs");

    
    // ===== KEYBOARD EVENTS =====
        public static final NoteBytesReadOnly EVENT_KEY_DOWN =
        new NoteBytesReadOnly("key_down");

        public static final NoteBytesReadOnly EVENT_KEY_UP =
                new NoteBytesReadOnly("key_up");

        public static final NoteBytesReadOnly EVENT_KEY_REPEAT =
                new NoteBytesReadOnly("key_repeat");

        public static final NoteBytesReadOnly EVENT_KEY_CHAR =
                new NoteBytesReadOnly("key_char");

        // ===== CONTAINER EVENTS =====
        // Covers all renderer types (terminal, window, web, etc.)
        public static final NoteBytesReadOnly EVENT_CONTAINER_FOCUS_GAINED =
        new NoteBytesReadOnly("container_focus_gained");

        public static final NoteBytesReadOnly EVENT_CONTAINER_FOCUS_LOST =
                new NoteBytesReadOnly("container_focus_lost");

        public static final NoteBytesReadOnly EVENT_CONTAINER_RESIZE =
                new NoteBytesReadOnly("container_resize");

        public static final NoteBytesReadOnly EVENT_CONTAINER_MOVE =
                new NoteBytesReadOnly("container_move");

        public static final NoteBytesReadOnly EVENT_CONTAINER_CLOSE =
                new NoteBytesReadOnly("container_close");

        public static final NoteBytesReadOnly EVENT_CONTAINER_MINIMIZE =
                new NoteBytesReadOnly("container_minimize");

        public static final NoteBytesReadOnly EVENT_CONTAINER_MAXIMIZE =
                new NoteBytesReadOnly("container_maximize");

        public static final NoteBytesReadOnly EVENT_CONTAINER_RESTORE =
                new NoteBytesReadOnly("container_restore");

        public static final NoteBytesReadOnly EVENT_CONTAINER_SHOWN =
                new NoteBytesReadOnly("container_shown");

        public static final NoteBytesReadOnly EVENT_CONTAINER_HIDDEN =
                new NoteBytesReadOnly("container_hidden");



        // ===== SPECIAL INPUT  =====
        public static final NoteBytesReadOnly EVENT_RAW_HID =
        new NoteBytesReadOnly("raw_hid");


        // ===== ENCRYPTION / PROTOCOL =====
        public static final NoteBytesReadOnly TYPE_ENCRYPTION_OFFER =
        new NoteBytesReadOnly("encryption_offer");

        public static final NoteBytesReadOnly TYPE_ENCRYPTION_ACCEPT =
                new NoteBytesReadOnly("encryption_accept");

        public static final NoteBytesReadOnly TYPE_ENCRYPTION_READY =
                new NoteBytesReadOnly("encryption_ready");

        public static final NoteBytesReadOnly TYPE_ENCRYPTED =
                new NoteBytesReadOnly("encrypted");

        public static final NoteBytesReadOnly TYPE_ENCRYPTION_DECLINE =
                new NoteBytesReadOnly("encryption_decline");


        // ===== STATE CHANGE EVENTS =====
        public static final NoteBytesReadOnly EVENT_RELEASE =
        new NoteBytesReadOnly("release");

        public static final NoteBytesReadOnly EVENT_REMOVED =
                new NoteBytesReadOnly("removed");

        public static final NoteBytesReadOnly EVENT_CHANGED =
                new NoteBytesReadOnly("changed");

        public static final NoteBytesReadOnly EVENT_CHECKED =
                new NoteBytesReadOnly("checked");

        public static final NoteBytesReadOnly EVENT_UPDATED =
                new NoteBytesReadOnly("updated");

        public static final NoteBytesReadOnly EVENT_ADDED =
                new NoteBytesReadOnly("added");


        // ===== PROTOCOL CONTROL =====
        public static final NoteBytesReadOnly TYPE_ERROR =
        new NoteBytesReadOnly("error");

        public static final NoteBytesReadOnly TYPE_DISCONNECTED =
                new NoteBytesReadOnly("disconnected");

        public static final NoteBytesReadOnly TYPE_PONG =
                new NoteBytesReadOnly("pong");

        public static final NoteBytesReadOnly TYPE_PING =
                new NoteBytesReadOnly("ping");

        public static final NoteBytesReadOnly TYPE_ACCEPT =
                new NoteBytesReadOnly("accept");

        public static final NoteBytesReadOnly TYPE_HELLO =
                new NoteBytesReadOnly("hello");

        public static final NoteBytesReadOnly TYPE_CMD =
                new NoteBytesReadOnly("cmd");

        public static final NoteBytesReadOnly TYPE_SHUTDOWN =
                new NoteBytesReadOnly("shutdown");


        // ===== STATE FLAGS =====
        /**
         * State flags for input events.
         * Uses int (32-bit) for compatibility with packets.
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

        public static final int MOUSE_BUTTON_MASK = 0x0000FF00;

        // Event state flags (bits 16-23)
        public static final int STATE_CONSUMED  = 0x010000;
        public static final int STATE_BUBBLING  = 0x020000;
        public static final int STATE_CAPTURING = 0x040000;
        public static final int STATE_SYNTHETIC = 0x080000;
        public static final int STATE_RECORDED  = 0x100000;
        public static final int STATE_REPLAYING = 0x200000;

        public static final int EVENT_STATE_MASK = 0x00FF0000;

        public static boolean hasFlag(int state, int flag) {
                return (state & flag) != 0;
        }

        public static int setFlag(int state, int flag) {
                return state | flag;
        }

        public static int clearFlag(int state, int flag) {
                return state & ~flag;
        }

        public static int getModifiers(int state) {
                return state & MOD_MASK;
        }

        public static boolean hasAnyModifier(int state) {
                return (state & MOD_MASK) != 0;
        }

        public static String describe(int state) {
                StringBuilder sb = new StringBuilder();
                
                if (hasFlag(state, MOD_SHIFT)) sb.append("SHIFT+");
                if (hasFlag(state, MOD_CONTROL)) sb.append("CTRL+");
                if (hasFlag(state, MOD_ALT)) sb.append("ALT+");
                if (hasFlag(state, MOD_SUPER)) sb.append("SUPER+");
                
                if (hasFlag(state, MOUSE_BUTTON_1)) sb.append("BTN1+");
                if (hasFlag(state, MOUSE_BUTTON_2)) sb.append("BTN2+");
                if (hasFlag(state, MOUSE_BUTTON_3)) sb.append("BTN3+");
                
                if (hasFlag(state, STATE_CONSUMED)) sb.append("CONSUMED+");
                
                String result = sb.toString();
                return result.isEmpty() ? "NONE" : result.substring(0, result.length() - 1);
        }
        }

        // ===== EVENT METADATA =====

        /**
         * Event metadata with descriptions
         */
        public static class EventMeta {
        public final NoteBytesReadOnly type;
        public final String name;
        public final String description;
        public final EventCategory category;

        EventMeta(NoteBytesReadOnly type, String name, String description, EventCategory category) {
                this.type = type;
                this.name = name;
                this.description = description;
                this.category = category;
        }
        }

        public enum EventCategory {
        MOUSE,
        KEYBOARD,
        CONTAINER,
        STATE,
        PROTOCOL
        }

        // Event registry with descriptions
        private static final Map<NoteBytesReadOnly, EventMeta> EVENT_META = new HashMap<>();
        private static final Map<String, NoteBytesReadOnly> NAME_TO_TYPE = new HashMap<>();

        static {
        // Mouse events
        register(EVENT_MOUSE_MOVE_RELATIVE, "mouse_move_rel", "Mouse moved (relative)", EventCategory.MOUSE);
        register(EVENT_MOUSE_BUTTON_DOWN, "mouse_down", "Mouse button pressed", EventCategory.MOUSE);
        register(EVENT_MOUSE_BUTTON_UP, "mouse_up", "Mouse button released", EventCategory.MOUSE);
        register(EVENT_MOUSE_CLICK, "mouse_click", "Mouse clicked", EventCategory.MOUSE);
        register(EVENT_MOUSE_DOUBLE_CLICK, "mouse_dblclick", "Mouse double-clicked", EventCategory.MOUSE);
        register(EVENT_MOUSE_SCROLL, "mouse_scroll", "Mouse wheel scrolled", EventCategory.MOUSE);
        register(EVENT_MOUSE_ENTER, "mouse_enter", "Mouse entered area", EventCategory.MOUSE);
        register(EVENT_MOUSE_EXIT, "mouse_exit", "Mouse exited area", EventCategory.MOUSE);
        register(EVENT_MOUSE_DRAG_START, "mouse_drag_start", "Drag started", EventCategory.MOUSE);
        register(EVENT_MOUSE_DRAG, "mouse_drag", "Dragging", EventCategory.MOUSE);
        register(EVENT_MOUSE_DRAG_END, "mouse_drag_end", "Drag ended", EventCategory.MOUSE);
        register(EVENT_MOUSE_MOVE_ABSOLUTE, "mouse_move_abs", "Mouse moved (absolute)", EventCategory.MOUSE);

        // Keyboard events
        register(EVENT_KEY_DOWN, "key_down", "Key pressed", EventCategory.KEYBOARD);
        register(EVENT_KEY_UP, "key_up", "Key released", EventCategory.KEYBOARD);
        register(EVENT_KEY_REPEAT, "key_repeat", "Key repeated", EventCategory.KEYBOARD);
        register(EVENT_KEY_CHAR, "key_char", "Character with modifiers", EventCategory.KEYBOARD);

        // Container events
        register(EVENT_CONTAINER_FOCUS_GAINED, "focus_gained", "Container gained focus", EventCategory.CONTAINER);
        register(EVENT_CONTAINER_FOCUS_LOST, "focus_lost", "Container lost focus", EventCategory.CONTAINER);
        register(EVENT_CONTAINER_RESIZE, "resize", "Container resized", EventCategory.CONTAINER);
        register(EVENT_CONTAINER_MOVE, "move", "Container moved", EventCategory.CONTAINER);
        register(EVENT_CONTAINER_CLOSE, "close", "Container closed", EventCategory.CONTAINER);
        register(EVENT_CONTAINER_MINIMIZE, "minimize", "Container minimized", EventCategory.CONTAINER);
        register(EVENT_CONTAINER_MAXIMIZE, "maximize", "Container maximized", EventCategory.CONTAINER);
        register(EVENT_CONTAINER_RESTORE, "restore", "Container restored", EventCategory.CONTAINER);
        register(EVENT_CONTAINER_SHOWN, "shown", "Container shown", EventCategory.CONTAINER);
        register(EVENT_CONTAINER_HIDDEN, "hidden", "Container hidden", EventCategory.CONTAINER);

        // Special
        register(EVENT_RAW_HID, "raw_hid", "Raw HID input", EventCategory.KEYBOARD);

        // State changes
        register(EVENT_RELEASE, "release", "Resource released", EventCategory.STATE);
        register(EVENT_REMOVED, "removed", "Item removed", EventCategory.STATE);
        register(EVENT_CHANGED, "changed", "State changed", EventCategory.STATE);
        register(EVENT_CHECKED, "checked", "Item checked", EventCategory.STATE);
        register(EVENT_UPDATED, "updated", "Item updated", EventCategory.STATE);
        register(EVENT_ADDED, "added", "Item added", EventCategory.STATE);

        // Protocol
        register(TYPE_ERROR, "error", "Error occurred", EventCategory.PROTOCOL);
        register(TYPE_DISCONNECTED, "disconnected", "Disconnected", EventCategory.PROTOCOL);
        register(TYPE_SHUTDOWN, "shutdown", "Shutdown request", EventCategory.PROTOCOL);
        }

        private static void register(NoteBytesReadOnly type, String name, String desc, EventCategory cat) {
        EventMeta meta = new EventMeta(type, name, desc, cat);
        EVENT_META.put(type, meta);
        NAME_TO_TYPE.put(name, type);
        }

        // ===== UTILITY METHODS =====

        public static boolean isMouseEvent(NoteBytes type) {
        EventMeta meta = EVENT_META.get(type);
        return meta != null && meta.category == EventCategory.MOUSE;
        }

        public static boolean isKeyboardEvent(NoteBytes type) {
        EventMeta meta = EVENT_META.get(type);
        return meta != null && meta.category == EventCategory.KEYBOARD;
        }

        public static boolean isContainerEvent(NoteBytes type) {
        EventMeta meta = EVENT_META.get(type);
        return meta != null && meta.category == EventCategory.CONTAINER;
        }

        public static String getEventName(NoteBytes type) {
        EventMeta meta = EVENT_META.get(type);
        return meta != null ? meta.name : "unknown_" + type.toString();
        }

        public static String getEventDescription(NoteBytes type) {
        EventMeta meta = EVENT_META.get(type);
        return meta != null ? meta.description : "Unknown event";
        }

        public static EventCategory getEventCategory(NoteBytes type) {
        EventMeta meta = EVENT_META.get(type);
        return meta != null ? meta.category : null;
        }

        public static NoteBytes getTypeByName(String name) {
        return NAME_TO_TYPE.get(name);
        }

        public static EventMeta getMetadata(NoteBytes type) {
        return EVENT_META.get(type);
        }

        @FunctionalInterface
        public interface NoteBytesEmmitter {
                public void emit(NoteBytes event);
        }
}