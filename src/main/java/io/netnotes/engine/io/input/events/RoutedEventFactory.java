package io.netnotes.engine.io.input.events;

import java.util.HashMap;
import java.util.Map;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.containers.ContainerCloseEvent;
import io.netnotes.engine.io.input.events.containers.ContainerFocusGainedEvent;
import io.netnotes.engine.io.input.events.containers.ContainerFocusLostEvent;
import io.netnotes.engine.io.input.events.containers.ContainerHiddenEvent;
import io.netnotes.engine.io.input.events.containers.ContainerMaximizeEvent;
import io.netnotes.engine.io.input.events.containers.ContainerMinimizeEvent;
import io.netnotes.engine.io.input.events.containers.ContainerMoveEvent;
import io.netnotes.engine.io.input.events.containers.ContainerResizeEvent;
import io.netnotes.engine.io.input.events.containers.ContainerRestoreEvent;
import io.netnotes.engine.io.input.events.containers.ContainerShownEvent;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

public final class RoutedEventFactory {

    private static final Map<NoteBytesReadOnly, EventDeserializer> REGISTRY = new HashMap<>();

    @FunctionalInterface
    private interface EventDeserializer {
        RoutedEvent create(ContextPath sourcePath, NoteBytesReadOnly type, int stateFlags, NoteBytes[] payload);
    }

    static {
        // ===== Mouse Events =====
        REGISTRY.put(EventBytes.EVENT_MOUSE_BUTTON_DOWN, (src, type, flags, p) ->
            new MouseButtonDownEvent(src,
                type,
                flags,
                p[0].getAsInt(),
                p[1].getAsDouble(),
                p[2].getAsDouble()
            ));

        REGISTRY.put(EventBytes.EVENT_MOUSE_BUTTON_UP, (src, type, flags, p) ->
            new MouseButtonUpEvent(src,
                type,
                flags,
                p[0].getAsInt(),
                p[1].getAsDouble(),
                p[2].getAsDouble()
            ));

        REGISTRY.put(EventBytes.EVENT_MOUSE_SCROLL, (src, type, flags, p) ->
            new ScrollEvent(src,
                type,
                flags,
                p[0].getAsDouble(),
                p[1].getAsDouble(),
                p[2].getAsDouble(),
                p[3].getAsDouble()
            ));
    
                
        // ===== Keyboard Events =====
        //NoteBytesReadOnly sourceId, int key, int scancode, int stateFlags
        REGISTRY.put(EventBytes.EVENT_KEY_DOWN, (src, type, flags, p) ->
            new KeyDownEvent(src,
                type,
                flags,
                p[0],
                p[1]
            ));

        REGISTRY.put(EventBytes.EVENT_KEY_UP, (src, type, flags, p) ->
            new KeyUpEvent(src,
                type,
                flags,
                p[0],
                p[1]
                ));
        //NoteBytesReadOnly sourceId, int key, int scancode, int stateFlags
        REGISTRY.put(EventBytes.EVENT_KEY_REPEAT, (src, type, flags, p) ->
            new KeyRepeatEvent(src,
                type,
                flags,
                p[0],
                p[1]
            ));

        //NoteBytesReadOnly sourceId, int codepoint, int stateFlags
        REGISTRY.put(EventBytes.EVENT_KEY_CHAR, (src, type, flags, p) ->
            new KeyCharEvent(src,
                type,
                flags,
                p[0]
            ));

  
          // ===== Container Events =====

        REGISTRY.put(EventBytes.EVENT_CONTAINER_FOCUS_GAINED, (src, type, flags, p) ->
            new ContainerFocusGainedEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_FOCUS_LOST, (src, type, flags, p) ->
            new ContainerFocusLostEvent(src, type, flags));
      
        REGISTRY.put(EventBytes.EVENT_CONTAINER_FOCUS_GAINED, (src, type, flags, p) ->
            new ContainerFocusGainedEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_FOCUS_LOST, (src, type, flags, p) ->
            new ContainerFocusLostEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_RESIZE, (src, type, flags, p) ->
            new ContainerResizeEvent(src, type, flags, p[0].getAsInt(), p[1].getAsInt()));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_MOVE, (src, type, flags, p) ->
            new ContainerMoveEvent(src, type, flags, p[0].getAsInt(), p[1].getAsInt()));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_CLOSE, (src, type, flags, p) ->
            new ContainerCloseEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_MINIMIZE, (src, type, flags, p) ->
            new ContainerMinimizeEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_MAXIMIZE, (src, type, flags, p) ->
            new ContainerMaximizeEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_RESTORE, (src, type, flags, p) ->
            new ContainerRestoreEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_SHOWN, (src, type, flags, p) ->
            new ContainerShownEvent(src, type, flags));

        REGISTRY.put(EventBytes.EVENT_CONTAINER_HIDDEN, (src, type, flags, p) ->
            new ContainerHiddenEvent(src, type, flags));
    }

    /**
     * Deserializes a RoutedPacket into a typed InputEvent.
     */
    public static RoutedEvent from( ContextPath sourcePath, NoteBytes packet) {

        if (packet.getType() == NoteBytesMetaData.NOTE_BYTES_ENCRYPTED_TYPE) {
            return new EncryptedInputEvent(sourcePath, packet);
        }

        if(packet.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
            return new BaseEvent(sourcePath, ProtocolMesssages.UNKNOWN, 0, new NoteBytes[]{packet});
        }

        NoteBytesMap body = packet.getAsNoteBytesMap();

        NoteBytesReadOnly typeBytes = body.getReadOnly(Keys.EVENT);

        if (typeBytes == null) {
            throw new IllegalStateException("Invalid InputPacket: missing EVENT");
        }


        int flags = 0;
        NoteBytes stateFlags = body.get(Keys.STATE_FLAGS);
        if (stateFlags != null) flags = stateFlags.getAsInt();

        NoteBytes payloadNote = body.get(Keys.PAYLOAD);
        NoteBytesArrayReadOnly payloadReadOnly = payloadNote != null ? payloadNote.getAsNoteBytesArrayReadOnly() : null;
        NoteBytes[] payloadArray = payloadReadOnly != null ? payloadReadOnly.getAsArray() : new NoteBytes[0];

        EventDeserializer constructor = REGISTRY.get(typeBytes);
        if (constructor == null) {
            return new BaseEvent(sourcePath, typeBytes, flags, payloadArray);
        }

        return constructor.create(sourcePath, typeBytes, flags, payloadArray);
    }

}
