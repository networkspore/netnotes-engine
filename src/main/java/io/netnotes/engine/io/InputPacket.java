package io.netnotes.engine.io;

import java.util.concurrent.CompletableFuture;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteInteger;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.utils.AtomicSequence;
import io.netnotes.engine.messaging.EventBytes;

/**
 * InputPacket - Binary format for input events
 * Integrates with NoteBytes messaging system
 */
public final class InputPacket {
    
    

    
    /**
     * Factory for creating input packets
     */
    public static class Factory {
        public static final NoteBytesReadOnly SOURCE_KEY        = new NoteBytesReadOnly("sId");
        public static final NoteBytesReadOnly TYPE_KEY          = new NoteBytesReadOnly("typ");
        public static final NoteBytesReadOnly SEQUENCE_KEY      = new NoteBytesReadOnly("seq");
        public static final NoteBytesReadOnly STATE_FLAGS_KEY   = new NoteBytesReadOnly("stF");
        public static final NoteBytesReadOnly PAYLOAD_KEY       = new NoteBytesReadOnly("pld");
        
        private static final int BASE_BODY_SIZE = 3;
        
        private final NoteBytesReadOnly m_sourceId;
        
        public Factory(int sourceId) {
            this.m_sourceId = new NoteBytesReadOnly(sourceId);
        }
        
        // Mouse event creators
        
        public byte[] createMouseMove(double x, double y, int stateFlags) {
            return of(EventBytes.EVENT_MOUSE_MOVE_ABSOLUTE, 
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(x),
                new NoteBytesReadOnly(y));
        }
        
        public byte[] createMouseMoveRelative(double dx, double dy, int stateFlags) {
            return of(EventBytes.EVENT_MOUSE_MOVE_RELATIVE,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(dx),
                new NoteBytesReadOnly(dy));
        }
        
        public byte[] createMouseButtonDown(int button, double x, double y, int stateFlags) {
            return of(EventBytes.EVENT_MOUSE_BUTTON_DOWN,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(button),
                new NoteBytesReadOnly(x),
                new NoteBytesReadOnly(y));
        }
        
        public byte[] createMouseButtonUp(int button, double x, double y, int stateFlags) {
            return of(EventBytes.EVENT_MOUSE_BUTTON_UP,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(button),
                new NoteBytesReadOnly(x),
                new NoteBytesReadOnly(y));
        }
        
        public byte[] createMouseClick(int button, double x, double y, int clickCount, int stateFlags) {
            return of(EventBytes.EVENT_MOUSE_CLICK,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(button),
                new NoteBytesReadOnly(x),
                new NoteBytesReadOnly(y),
                new NoteBytesReadOnly(clickCount));
        }
        
        public byte[] createScroll(double xOffset, double yOffset, double mouseX, double mouseY, int stateFlags) {
            return of(EventBytes.EVENT_SCROLL,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(xOffset),
                new NoteBytesReadOnly(yOffset),
                new NoteBytesReadOnly(mouseX),
                new NoteBytesReadOnly(mouseY));
        }
        
        // Keyboard event creators
        
        public byte[] createKeyDown(int key, int scancode, int stateFlags) {
            return of(EventBytes.EVENT_KEY_DOWN,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(key),
                new NoteBytesReadOnly(scancode));
        }
        
        public byte[] createKeyUp(int key, int scancode, int stateFlags) {
            return of(EventBytes.EVENT_KEY_UP,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(key),
                new NoteBytesReadOnly(scancode));
        }
        
        public byte[] createKeyRepeat(int key, int scancode, int stateFlags) {
            return of(EventBytes.EVENT_KEY_REPEAT,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(key),
                new NoteBytesReadOnly(scancode));
        }
        
        public byte[] createKeyChar(int codepoint, int stateFlags) {
            return of(EventBytes.EVENT_KEY_CHAR,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(codepoint));
        }
        
        public byte[] createKeyCharMods(int codepoint, int stateFlags) {
            return of(EventBytes.EVENT_KEY_CHAR_MODS,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                stateFlags,
                new NoteBytesReadOnly(codepoint));
        }
        
        // Focus event creators
        
        public byte[] createFocusGained() {
            return of(EventBytes.EVENT_FOCUS_GAINED,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                0);
        }
        
        public byte[] createFocusLost() {
            return of(EventBytes.EVENT_FOCUS_LOST,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                0);
        }
        
        // Window event creators
        
        public byte[] createFramebufferResize(int width, int height) {
            return of(EventBytes.EVENT_FRAMEBUFFER_RESIZE,
                new NoteBytesReadOnly(AtomicSequence.getNextSequence()),
                0,
                new NoteBytesReadOnly(width),
                new NoteBytesReadOnly(height));
        }
        
        // Core factory method
        
        public CompletableFuture<byte[]> ofAsync(NoteBytesReadOnly type) {
            return CompletableFuture.supplyAsync(() -> 
                of(type, new NoteBytesReadOnly(AtomicSequence.getNextSequence())));
        }
        
        public byte[] of(NoteBytesReadOnly type, NoteBytesReadOnly atomicSequence, NoteBytesReadOnly... payload) {
            return of(type, atomicSequence, 0, payload);
        }
        
        /**
         * Create an input packet
         * @param type InputPacket Type
         * @param atomicSequence Sequence number
         * @param stateFlags State flags (modifiers, buttons, etc.)
         * @param payload Main payload
         * @return Binary packet
         */
        public byte[] of(NoteBytesReadOnly type, NoteBytesReadOnly atomicSequence, int stateFlags, NoteBytesReadOnly... payload) {
            if (type == null) {
                throw new IllegalStateException("Type required");
            }
            
            boolean hasStateFlags = stateFlags != 0;
            boolean hasPayload = payload != null && payload.length > 0;
            
            int size = BASE_BODY_SIZE + (hasStateFlags ? 1 : 0) + (hasPayload ? 1 : 0);
            
            NoteBytesPair[] pairs = new NoteBytesPair[size];
            pairs[0] = new NoteBytesPair(SOURCE_KEY, m_sourceId);
            pairs[0] = new NoteBytesPair(TYPE_KEY, type);
            pairs[1] = new NoteBytesPair(SEQUENCE_KEY, atomicSequence);
            
            int index = BASE_BODY_SIZE;
            
            if (hasStateFlags) {
                pairs[index++] = new NoteBytesPair(STATE_FLAGS_KEY, new NoteInteger(stateFlags));
            }
            if (hasPayload) {
                pairs[index] = new NoteBytesPair(PAYLOAD_KEY, new NoteBytesArray(payload));
            }
            
            NoteBytesObject packet = new NoteBytesObject(pairs);
            NoteBytesObject sourceObject = new NoteBytesObject(new NoteBytesPair(m_sourceId, packet));
            
            return sourceObject.get();
        }
    }
}