package io.netnotes.engine.io.input.events;

import java.util.HashMap;
import java.util.Map;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public final class InputEventFactory {

    private static final Map<NoteBytesReadOnly, EventDeserializer> REGISTRY = new HashMap<>();

    @FunctionalInterface
    private interface EventDeserializer {
        RoutedEvent create(ContextPath sourcePath, int stateFlags, NoteBytes[] payload);
    }

    static {
        // ===== Mouse Events =====
        REGISTRY.put(EventBytes.EVENT_MOUSE_BUTTON_DOWN, (src, flags, p) ->
            new MouseButtonDownEvent(src,
                p[0].getAsInt(),
                p[1].getAsDouble(),
                p[2].getAsDouble(),
                flags));

        REGISTRY.put(EventBytes.EVENT_MOUSE_BUTTON_UP, (src, flags, p) ->
            new MouseButtonUpEvent(src,
                p[0].getAsInt(),
                p[1].getAsDouble(),
                p[2].getAsDouble(),
                flags));

        REGISTRY.put(EventBytes.EVENT_SCROLL, (src, flags, p) ->
            new ScrollEvent(src,
                p[0].getAsDouble(),
                p[1].getAsDouble(),
                p[2].getAsDouble(),
                p[3].getAsDouble(),
                flags));
    
                
        // ===== Keyboard Events =====
        //NoteBytesReadOnly sourceId, int key, int scancode, int stateFlags
        REGISTRY.put(EventBytes.EVENT_KEY_DOWN, (src, flags, p) ->
            new KeyDownEvent(src,
                p[0].getAsInt(),
                p[1].getAsInt(),
                flags));

        REGISTRY.put(EventBytes.EVENT_KEY_UP, (src, flags, p) ->
            new KeyUpEvent(src,
                p[0].getAsInt(),
                p[1].getAsInt(),
                flags));
        //NoteBytesReadOnly sourceId, int key, int scancode, int stateFlags
        REGISTRY.put(EventBytes.EVENT_KEY_REPEAT, (src, flags, p) ->
            new KeyRepeatEvent(src,
                p[0].getAsInt(),
                p[1].getAsInt(),
                flags));

        //NoteBytesReadOnly sourceId, int codepoint, int stateFlags
        REGISTRY.put(EventBytes.EVENT_KEY_CHAR, (src, flags, p) ->
            new KeyCharEvent(src,
                p[0].getAsInt(),
                flags));

        //NoteBytesReadOnly sourceId, int codepoint, int stateFlags
        REGISTRY.put(EventBytes.EVENT_KEY_CHAR_MODS, (src, flags, p) ->
            new KeyCharModsEvent(src,
                p[0].getAsInt(),
                flags));

        // ===== Focus Events =====
        REGISTRY.put(EventBytes.EVENT_FOCUS_GAINED, (src, flags, p) ->
            new FocusGainedEvent(src));

        REGISTRY.put(EventBytes.EVENT_FOCUS_LOST, (src, flags, p) ->
            new FocusLostEvent(src));
    }

    /**
     * Deserializes a RoutedPacket into a typed InputEvent.
     */
    public static RoutedEvent from( ContextPath sourcePath, NoteBytesReadOnly packet) {

        if (packet.getType() == NoteBytesMetaData.NOTE_BYTES_ENCRYPTED_TYPE) {
            return new EncryptedInputEvent(sourcePath, packet);
        }

        NoteBytesMap body = packet.getAsNoteBytesMap();

        NoteBytes typeBytes = body.get(Keys.TYPE);
        NoteBytes seqBytes = body.get(Keys.SEQUENCE);

        if (typeBytes == null || seqBytes == null) {
            throw new IllegalStateException("Invalid InputPacket: missing type or sequence");
        }

        NoteBytesReadOnly type = typeBytes.readOnly();

        int flags = 0;
        NoteBytes stateFlags = body.get(Keys.STATE_FLAGS);
        if (stateFlags != null) flags = stateFlags.getAsInt();

        NoteBytes payloadNote = body.get(Keys.PAYLOAD);
        NoteBytesArrayReadOnly payloadReadOnly = payloadNote != null ? payloadNote.getAsNoteBytesArrayReadOnly() : null;
        NoteBytes[] payloadArray = payloadReadOnly != null ? payloadReadOnly.getAsArray() : new NoteBytes[0];

        EventDeserializer constructor = REGISTRY.get(type);
        if (constructor == null) {
            throw new IllegalArgumentException("Unknown InputEvent type: " + type);
        }

        return constructor.create(sourcePath, flags, payloadArray);
    }

}
