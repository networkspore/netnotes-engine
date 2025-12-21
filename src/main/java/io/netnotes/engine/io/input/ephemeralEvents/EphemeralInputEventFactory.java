package io.netnotes.engine.io.input.ephemeralEvents;


import java.util.HashMap;
import java.util.Map;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytesArrayEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObjectEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesPairEphemeral;

/**
 * EphemeralInputEventFactory - Creates ephemeral events from decrypted data
 * 
 * SECURITY CRITICAL:
 * - All decrypted bytes are wiped after event creation
 * - Events contain NoteBytesEphemeral objects
 * - Caller must close events with try-with-resources
 * 
 * Flow:
 * 1. Decrypt encrypted packet → byte[]
 * 2. Deserialize to NoteBytesObjectEphemeral
 * 3. Extract fields as ephemeral data
 * 4. Create ephemeral event
 * 5. Close NoteBytesObjectEphemeral (wipes decrypted bytes)
 */
public final class EphemeralInputEventFactory {

    @FunctionalInterface
    private interface EphemeralEventDeserializer {
        EphemeralRoutedEvent create(ContextPath sourcePath, 
                                    NoteBytesEphemeral stateFlags, 
                                    NoteBytesEphemeral[] payload);
    }

    private static final Map<NoteBytesReadOnly, EphemeralEventDeserializer> REGISTRY = new HashMap<>();

    static {
        // ===== Keyboard Events (Ephemeral) =====
        REGISTRY.put(EventBytes.EVENT_KEY_DOWN, (src, flags, p) ->
            new EphemeralKeyDownEvent(src, p[0], p[1], flags));

        REGISTRY.put(EventBytes.EVENT_KEY_UP, (src, flags, p) ->
            new EphemeralKeyUpEvent(src, p[0], p[1], flags));

        REGISTRY.put(EventBytes.EVENT_KEY_REPEAT, (src, flags, p) ->
            new EphemeralKeyRepeatEvent(src, p[0], p[1], flags));

        REGISTRY.put(EventBytes.EVENT_KEY_CHAR, (src, flags, p) ->
            new EphemeralKeyCharEvent(src, p[0], flags));

        REGISTRY.put(EventBytes.EVENT_KEY_CHAR_MODS, (src, flags, p) ->
            new EphemeralKeyCharModsEvent(src, p[0], flags));

        // ===== Mouse Events (Ephemeral) =====
        REGISTRY.put(EventBytes.EVENT_MOUSE_BUTTON_DOWN, (src, flags, p) ->
            new EphemeralMouseButtonDownEvent(src, p[0], p[1], p[2], flags));

        REGISTRY.put(EventBytes.EVENT_SCROLL, (src, flags, p) ->
            new EphemeralScrollEvent(src, p[0], p[1], p[2], p[3], flags));

        // Note: Focus events don't need ephemeral versions (no sensitive data)
    }

    /**
     * Create ephemeral event from decrypted bytes
     * 
     * @param sourcePath Source device path
     * @param decryptedBytes Decrypted event packet bytes (will be wiped)
     * @return Ephemeral event (caller must close)
     * @throws RuntimeException if deserialization fails
     */

    @SuppressWarnings("resource")
    public static EphemeralRoutedEvent fromDecryptedBytes(
            ContextPath sourcePath, 
            byte[] decryptedBytes) {
        
        // Deserialize into ephemeral object
        try (
            NoteBytesObjectEphemeral body = new NoteBytesObjectEphemeral(decryptedBytes);
            NoteBytesPairEphemeral typePair = body.get(Keys.EVENT);
            NoteBytesEphemeral typeBytes = typePair.getValue();
            NoteBytesPairEphemeral seqPair = body.get(Keys.SEQUENCE);
            NoteBytesPairEphemeral flagsPair = body.get(Keys.STATE_FLAGS);
            NoteBytesPairEphemeral payloadPair = body.get(Keys.PAYLOAD);
        ) {
         
            
            // Extract state flags (optional)
            NoteBytesEphemeral flags = flagsPair != null ? flags = flagsPair.getValue() : new NoteBytesEphemeral(0);
            
            // Extract payload array (ephemeral)
            // payloadPair.getValue().get() cleaned up in above try (close warning suppressed)
            NoteBytesEphemeral[] payloadArray = payloadPair != null 
                ? new NoteBytesArrayEphemeral(payloadPair.getValue().get()).getAsArray()
                : new NoteBytesEphemeral[0];
            
      
            // Lookup constructor for event type (payload ownership transferred to event)
            EphemeralEventDeserializer constructor = REGISTRY.get(typeBytes);

            if (constructor == null) {
                return new EphemeralUnknownEvent(sourcePath, flags, payloadArray);
            }
                
            // Create event 
            return constructor.create(sourcePath, flags, payloadArray);
            
        }catch(NullPointerException e){
            throw new IllegalStateException("Missing type field in encrypted event", e);
        }
    }
    
    /**
     * Helper: Check if event type should be processed ephemerally
     */
    public static boolean isEncryptableEventType(byte eventType) {
        // All keyboard and mouse events should be encrypted
        return eventType >= EventBytes.EVENT_MOUSE_MOVE_RELATIVE.getAsByte() && 
               eventType <= EventBytes.EVENT_KEY_CHAR_MODS.getAsByte();
    }
}
