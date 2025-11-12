package io.netnotes.engine.io.events;

import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteInteger;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.utils.AtomicSequence;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.messaging.EventBytes;

/**
 * InputPacket - Binary format for input events
 * Integrates with NoteBytes messaging system
 */
public  class InputRoutedPacketFactory {
    
    public static final NoteBytesReadOnly SOURCE_KEY        = new NoteBytesReadOnly("sId");
    public static final NoteBytesReadOnly TYPE_KEY          = new NoteBytesReadOnly("typ");
    public static final NoteBytesReadOnly SEQUENCE_KEY      = new NoteBytesReadOnly("seq");
    public static final NoteBytesReadOnly STATE_FLAGS_KEY   = new NoteBytesReadOnly("stF");
    public static final NoteBytesReadOnly PAYLOAD_KEY       = new NoteBytesReadOnly("pld");
    
    private static final int BASE_BODY_SIZE = 3;
    
    // Mouse event creators
    
    public static RoutedPacket createMouseMove(NoteBytesReadOnly sourceId, double x, double y, int stateFlags) {
        return of(sourceId, EventBytes.EVENT_MOUSE_MOVE_ABSOLUTE, 
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(x),
            new NoteBytesReadOnly(y));
    }
    
    public static RoutedPacket createMouseMoveRelative(NoteBytesReadOnly sourceId, double dx, double dy, int stateFlags) {
        return of(sourceId, EventBytes.EVENT_MOUSE_MOVE_RELATIVE,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(dx),
            new NoteBytesReadOnly(dy));
    }
    
    public static RoutedPacket createMouseButtonDown(NoteBytesReadOnly sourceId, int button, double x, double y, int stateFlags) {
        return of(sourceId, EventBytes.EVENT_MOUSE_BUTTON_DOWN,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(button),
            new NoteBytesReadOnly(x),
            new NoteBytesReadOnly(y));
    }
    
    public static RoutedPacket createMouseButtonUp(NoteBytesReadOnly sourceId, int button, double x, double y, int stateFlags) {
        return of(sourceId, EventBytes.EVENT_MOUSE_BUTTON_UP,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(button),
            new NoteBytesReadOnly(x),
            new NoteBytesReadOnly(y));
    }
    
    public static RoutedPacket createMouseClick(NoteBytesReadOnly sourceId, int button, double x, double y, int clickCount, int stateFlags) {
        return of(sourceId, EventBytes.EVENT_MOUSE_CLICK,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(button),
            new NoteBytesReadOnly(x),
            new NoteBytesReadOnly(y),
            new NoteBytesReadOnly(clickCount));
    }
    
    public static RoutedPacket createScroll(NoteBytesReadOnly sourceId, double xOffset, double yOffset, double mouseX, double mouseY, int stateFlags) {
        return of(sourceId, EventBytes.EVENT_SCROLL,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(xOffset),
            new NoteBytesReadOnly(yOffset),
            new NoteBytesReadOnly(mouseX),
            new NoteBytesReadOnly(mouseY));
    }
    
    // Keyboard event creators
    
    public static RoutedPacket createKeyDown(NoteBytesReadOnly sourceId, int key, int scancode, int stateFlags) {
        return of(sourceId, EventBytes.EVENT_KEY_DOWN,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(key),
            new NoteBytesReadOnly(scancode));
    }
    
    public static RoutedPacket createKeyUp(NoteBytesReadOnly sourceId, int key, int scancode, int stateFlags) {
        return of(sourceId, EventBytes.EVENT_KEY_UP,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(key),
            new NoteBytesReadOnly(scancode));
    }
    
    public static RoutedPacket createKeyRepeat(NoteBytesReadOnly sourceId, int key, int scancode, int stateFlags) {
        return of(sourceId, EventBytes.EVENT_KEY_REPEAT,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(key),
            new NoteBytesReadOnly(scancode));
    }
    
    public static RoutedPacket createKeyChar(NoteBytesReadOnly sourceId, int codepoint, int stateFlags) {
        return of(sourceId, EventBytes.EVENT_KEY_CHAR,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(codepoint));
    }
    
    public static RoutedPacket createKeyCharMods(NoteBytesReadOnly sourceId, int codepoint, int stateFlags) {
        return of(sourceId, EventBytes.EVENT_KEY_CHAR_MODS,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(codepoint));
    }
    
    // Focus event creators
    
    public static RoutedPacket createFocusGained(NoteBytesReadOnly sourceId) {
        return of(sourceId, EventBytes.EVENT_FOCUS_GAINED,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            0);
    }
    
    public static RoutedPacket createFocusLost(NoteBytesReadOnly sourceId) {
        return of(sourceId, EventBytes.EVENT_FOCUS_LOST,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            0);
    }
    
    // Window event creators
    
    public static RoutedPacket createFramebufferResize(NoteBytesReadOnly sourceId, int width, int height) {
        return of(sourceId, EventBytes.EVENT_FRAMEBUFFER_RESIZE,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            0,
            new NoteBytesReadOnly(width),
            new NoteBytesReadOnly(height));
    }
    
    
    public static RoutedPacket of(NoteBytesReadOnly sourceId, NoteBytesReadOnly type, NoteBytesReadOnly atomicSequence, NoteBytesReadOnly... payload) {
        return of(sourceId, type, atomicSequence, 0, payload);
    }
    
    /**
     * Create an input packet
     * @param type InputPacket Type
     * @param atomicSequence Sequence number
     * @param stateFlags State flags (modifiers, buttons, etc.)
     * @param payload Main payload
     * @return Binary packet
     */
    public static RoutedPacket of(NoteBytesReadOnly sourceId, NoteBytesReadOnly type, NoteBytesReadOnly atomicSequence, int stateFlags, NoteBytesReadOnly... payload) {
        if (type == null) {
            throw new IllegalStateException("Type required");
        }
        
        boolean hasStateFlags = stateFlags != 0;
        boolean hasPayload = payload != null && payload.length > 0;
        
        int size = BASE_BODY_SIZE + (hasStateFlags ? 1 : 0) + (hasPayload ? 1 : 0);
        
        NoteBytesPair[] pairs = new NoteBytesPair[size];
        pairs[0] = new NoteBytesPair(TYPE_KEY, type);
        pairs[1] = new NoteBytesPair(SEQUENCE_KEY, atomicSequence);
        
        int index = BASE_BODY_SIZE;
        
        if (hasStateFlags) {
            pairs[index++] = new NoteBytesPair(STATE_FLAGS_KEY, new NoteInteger(stateFlags));
        }
        if (hasPayload) {
            pairs[index] = new NoteBytesPair(PAYLOAD_KEY, new NoteBytesArray(payload));
        }
        
        NoteBytesReadOnly packet = new NoteBytesReadOnly(
            NoteBytesObject.noteBytePairsToByteArray(pairs), 
            NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE
        );
        
        
        return new RoutedPacket(sourceId, packet);
    }
    
}