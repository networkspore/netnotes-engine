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
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

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
public final class EphemeralEventsFactory {

   

    @FunctionalInterface
    private interface EphemeralEventDeserializer {
        EphemeralRoutedEvent create(ContextPath sourcePath, 
                                    NoteBytesEphemeral typeBytes,
                                    int stateFlags, 
                                    NoteBytesEphemeral[] payload);
    }

    private static final Map<NoteBytesReadOnly, EphemeralEventDeserializer> REGISTRY = new HashMap<>();

    static {
        // ===== Keyboard Events (Ephemeral) =====
        REGISTRY.put(EventBytes.EVENT_KEY_DOWN, (src, type, flags, p) ->
            new EphemeralKeyDownEvent(src,type, flags, p[0], p[1]));

        REGISTRY.put(EventBytes.EVENT_KEY_UP, (src, type, flags, p) ->
            new EphemeralKeyUpEvent(src, type, flags, p[0], p[1]));

        REGISTRY.put(EventBytes.EVENT_KEY_REPEAT, (src, type, flags, p) ->
            new EphemeralKeyRepeatEvent(src, type, flags, p[0], p[1]));

        REGISTRY.put(EventBytes.EVENT_KEY_CHAR, (src, type, flags, p) ->
            new EphemeralKeyCharEvent(src, type, flags, p[0]));

        // ===== Mouse Events (Ephemeral) =====
        REGISTRY.put(EventBytes.EVENT_MOUSE_BUTTON_DOWN, (src, type, flags, p) ->
            new EphemeralMouseButtonDownEvent(src, type, flags, p[0], p[1], p[2]));

        REGISTRY.put(EventBytes.EVENT_MOUSE_SCROLL, (src, type, flags, p) ->
            new EphemeralScrollEvent(src, type, flags, p[0], p[1], p[2], p[3]));

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
    public static EphemeralRoutedEvent from(ContextPath sourcePath, NoteBytesEphemeral noteBytes) {
        if(noteBytes.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
            return new EphemeralEvent(sourcePath,  new NoteBytesEphemeral("unknown"), 0, new NoteBytesEphemeral[]{ noteBytes});
        }
        // Deserialize into ephemeral object
        try (
            NoteBytesObjectEphemeral body = new NoteBytesObjectEphemeral(noteBytes.get());
            NoteBytesPairEphemeral typePair = body.get(Keys.EVENT);
            NoteBytesPairEphemeral flagsPair = body.get(Keys.STATE_FLAGS);
            NoteBytesPairEphemeral payloadPair = body.get(Keys.PAYLOAD);
        ) {
            NoteBytesEphemeral typeBytes = typePair.getValue().copy();
            
            // Extract state flags (optional)
            int flags = flagsPair != null ? flags = flagsPair.getValue().getAsInt() :0;
            
            // Extract payload array (ephemeral)
            // payloadPair.getValue().get() cleaned up in above try (close warning suppressed)
            
            NoteBytesEphemeral[] payloadArray = payloadPair != null 
                ? new NoteBytesArrayEphemeral(payloadPair.getValue().get()).getAsArray()
                : new NoteBytesEphemeral[0];
            
      
            // Lookup constructor for event type (payload ownership transferred to event)
            @SuppressWarnings("unlikely-arg-type")
            EphemeralEventDeserializer constructor = REGISTRY.get(typeBytes);

            if (constructor == null) {
                return new EphemeralEvent(sourcePath,typeBytes, flags, payloadArray);
            }
                
            // Create event 
            return constructor.create(sourcePath, typeBytes, flags, payloadArray);
            
        }catch(NullPointerException e){
            throw new IllegalStateException("Missing type field in encrypted event", e);
        }
    }
    
  
}
