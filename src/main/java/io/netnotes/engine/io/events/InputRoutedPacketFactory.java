package io.netnotes.engine.io.events;

import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteInteger;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.utils.AtomicSequence;
import io.netnotes.engine.io.InputSourceRegistry;
import io.netnotes.engine.messaging.NoteMessaging.Keys;

/**
 * InputPacket - Binary format for input events
 * Integrates with NoteBytes messaging system
 */
public class InputRoutedPacketFactory {
  
    
    private static final int BASE_BODY_SIZE = 3;

    private final InputSourceRegistry registry;
    private final int sourceId;

    public InputRoutedPacketFactory( InputSourceRegistry registry, int sourceId){
        this.registry = registry;
        this.sourceId = sourceId;
    }
    
    // Mouse event creators
    
   public void createMouseMove(double x, double y, int stateFlags) {
        of(EventBytes.EVENT_MOUSE_MOVE_ABSOLUTE, 
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(x),
            new NoteBytesReadOnly(y));
    }
    
   public void createMouseMoveRelative(double dx, double dy, int stateFlags) {
        of(EventBytes.EVENT_MOUSE_MOVE_RELATIVE,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(dx),
            new NoteBytesReadOnly(dy));
    }
    
   public void createMouseButtonDown(int button, double x, double y, int stateFlags) {
        of(EventBytes.EVENT_MOUSE_BUTTON_DOWN,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(button),
            new NoteBytesReadOnly(x),
            new NoteBytesReadOnly(y));
    }
    
   public void createMouseButtonUp(int button, double x, double y, int stateFlags) {
        of(EventBytes.EVENT_MOUSE_BUTTON_UP,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(button),
            new NoteBytesReadOnly(x),
            new NoteBytesReadOnly(y));
    }
    
   public void createMouseClick(int button, double x, double y, int clickCount, int stateFlags) {
        of(EventBytes.EVENT_MOUSE_CLICK,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(button),
            new NoteBytesReadOnly(x),
            new NoteBytesReadOnly(y),
            new NoteBytesReadOnly(clickCount));
    }
    
   public void createScroll(double xOffset, double yOffset, double mouseX, double mouseY, int stateFlags) {
        of(EventBytes.EVENT_SCROLL,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(xOffset),
            new NoteBytesReadOnly(yOffset),
            new NoteBytesReadOnly(mouseX),
            new NoteBytesReadOnly(mouseY));
    }
    
    // Keyboard event creators
    
   public void createKeyDown(int key, int scancode, int stateFlags) {
        of(EventBytes.EVENT_KEY_DOWN,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(key),
            new NoteBytesReadOnly(scancode));
    }
    
   public void createKeyUp(int key, int scancode, int stateFlags) {
        of(EventBytes.EVENT_KEY_UP,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(key),
            new NoteBytesReadOnly(scancode));
    }
    
   public void createKeyRepeat(int key, int scancode, int stateFlags) {
        of(EventBytes.EVENT_KEY_REPEAT,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(key),
            new NoteBytesReadOnly(scancode));
    }
    
   public void createKeyChar(int codepoint, int stateFlags) {
        of(EventBytes.EVENT_KEY_CHAR,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(codepoint));
    }
    
   public void createKeyCharMods(int codepoint, int stateFlags) {
        of(EventBytes.EVENT_KEY_CHAR_MODS,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            stateFlags,
            new NoteBytesReadOnly(codepoint));
    }
    
    // Focus event creators
    
   public void createFocusGained(NoteBytesReadOnly sourceId) {
        of(EventBytes.EVENT_FOCUS_GAINED,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            0);
    }
    
   public void createFocusLost(NoteBytesReadOnly sourceId) {
        of(EventBytes.EVENT_FOCUS_LOST,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            0);
    }
    
    // Window event creators
    
   public void createFramebufferResize(int width, int height) {
        of(EventBytes.EVENT_FRAMEBUFFER_RESIZE,
            new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
            0,
            new NoteBytesReadOnly(width),
            new NoteBytesReadOnly(height));
    }
    
    
   public void of(NoteBytesReadOnly type, NoteBytesReadOnly atomicSequence, NoteBytesReadOnly... payload) {
        of(type, atomicSequence, 0, payload);
    }
    
    /**
     * Create an input packet
     * @param type InputPacket Type
     * @param atomicSequence Sequence number
     * @param stateFlags State flags (modifiers, buttons, etc.)
     * @param payload Main payload
     * @return Binary packet
     */
   public void of(NoteBytesReadOnly type, NoteBytesReadOnly atomicSequence, int stateFlags, NoteBytesReadOnly... payload) {
        if (type == null) {
            throw new IllegalStateException("Type required");
        }
        
        boolean hasStateFlags = stateFlags != 0;
        boolean hasPayload = payload != null && payload.length > 0;
        
        int size = BASE_BODY_SIZE + (hasStateFlags ? 1 : 0) + (hasPayload ? 1 : 0);
        
        NoteBytesPair[] pairs = new NoteBytesPair[size];
        pairs[0] = new NoteBytesPair(Keys.TYPE_KEY, type);
        pairs[1] = new NoteBytesPair(Keys.SEQUENCE_KEY, atomicSequence);
        
        int index = BASE_BODY_SIZE;
        
        if (hasStateFlags) {
            pairs[index++] = new NoteBytesPair(Keys.STATE_FLAGS_KEY, new NoteInteger(stateFlags));
        }
        if (hasPayload) {
            pairs[index] = new NoteBytesPair(Keys.PAYLOAD_KEY, new NoteBytesArray(payload));
        }
        
        NoteBytesReadOnly packet = new NoteBytesReadOnly(
            NoteBytesObject.noteBytePairsToByteArray(pairs), 
            NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE
        );
        
        registry.emit(sourceId, packet);
    }
    
}