package io.netnotes.engine.io.events;

import java.util.HashMap;
import java.util.Map;

import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.messaging.EventBytes;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public final class InputEventFactory {

    private static final Map<NoteBytesReadOnly, EventDeserializer> REGISTRY = new HashMap<>();

    @FunctionalInterface
    private interface EventDeserializer {
        InputEvent create(NoteBytesReadOnly sourceId, int stateFlags, NoteBytes[] payload);
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
        REGISTRY.put(EventBytes.EVENT_KEY_UP, (src, flags, p) ->
            new KeyUpEvent(src,
                p[0].getAsInt(),
                p[1].getAsInt(),
                flags));

        REGISTRY.put(EventBytes.EVENT_KEY_REPEAT, (src, flags, p) ->
            new KeyRepeatEvent(src,
                p[0].getAsInt(),
                p[1].getAsInt(),
                flags));

        REGISTRY.put(EventBytes.EVENT_KEY_CHAR, (src, flags, p) ->
            new KeyCharEvent(src,
                p[0].getAsInt(),
                flags));

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
    public static InputEvent from(RoutedPacket packet) {
        NoteBytesReadOnly sourceId = packet.getSourceId();
        NoteBytesReadOnly bodyNoteBytes = packet.getPacket();

        if (bodyNoteBytes.getType() == NoteBytesMetaData.NOTE_BYTES_ENCRYPTED_TYPE) {
            return new EncryptedInputEvent(sourceId, bodyNoteBytes);
        }

        NoteBytesMap body = bodyNoteBytes.getAsNoteBytesMap();

        NoteBytes typeBytes = body.get(InputRoutedPacketFactory.TYPE_KEY);
        NoteBytes seqBytes = body.get(InputRoutedPacketFactory.SEQUENCE_KEY);

        if (typeBytes == null || seqBytes == null) {
            throw new IllegalStateException("Invalid InputPacket: missing type or sequence");
        }

        NoteBytesReadOnly type = typeBytes.getAsReadOnly();

        int flags = 0;
        NoteBytes stateFlags = body.get(InputRoutedPacketFactory.STATE_FLAGS_KEY);
        if (stateFlags != null) flags = stateFlags.getAsInt();

        NoteBytes payloadNote = body.get(InputRoutedPacketFactory.PAYLOAD_KEY);
        NoteBytesArrayReadOnly payloadReadOnly = payloadNote != null ? payloadNote.getAsNoteBytesArrayReadOnly() : null;
        NoteBytes[] payloadArray = payloadReadOnly != null ? payloadReadOnly.getAsArray() : new NoteBytes[0];

        EventDeserializer constructor = REGISTRY.get(type);
        if (constructor == null) {
            throw new IllegalArgumentException("Unknown InputEvent type: " + type);
        }

        return constructor.create(sourceId, flags, payloadArray);
    }

}
