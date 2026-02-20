package io.netnotes.engine.ui.containers;

import java.util.HashMap;
import java.util.Map;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.IEventFactory;
import io.netnotes.engine.io.input.events.BaseEvent;
import io.netnotes.engine.io.input.events.EncryptedInputEvent;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyCharEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyRepeatEvent;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyUpEvent;
import io.netnotes.engine.io.input.events.mouseEvents.MouseButtonDownEvent;
import io.netnotes.engine.io.input.events.mouseEvents.MouseButtonUpEvent;
import io.netnotes.engine.io.input.events.mouseEvents.MouseScrollEvent;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.containers.containerEvents.ContainerCloseEvent;
import io.netnotes.engine.ui.containers.containerEvents.ContainerFocusGainedEvent;
import io.netnotes.engine.ui.containers.containerEvents.ContainerFocusLostEvent;
import io.netnotes.engine.ui.containers.containerEvents.ContainerHiddenEvent;
import io.netnotes.engine.ui.containers.containerEvents.ContainerMaximizeEvent;
import io.netnotes.engine.ui.containers.containerEvents.ContainerMinimizeEvent;
import io.netnotes.engine.ui.containers.containerEvents.ContainerRestoreEvent;
import io.netnotes.engine.ui.containers.containerEvents.ContainerShownEvent;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.utils.LoggingHelpers.Log;

public abstract class ContainerEventsFactory<
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>
> implements IEventFactory {

    private Map<NoteBytesReadOnly, EventDeserializer> registry = new HashMap<>();
    private Map<NoteBytesReadOnly, NoteBytesEventDeserializer> objRegistry = new HashMap<>();

    @FunctionalInterface
    private interface EventDeserializer {
        RoutedEvent create(ContextPath sourcePath, NoteBytesReadOnly type, int stateFlags, NoteBytes[] payload);
    }

    @FunctionalInterface
    private interface NoteBytesEventDeserializer {
        RoutedEvent create(ContextPath sourcePath, NoteBytesReadOnly type, int stateFlags, NoteBytes payload);
    }

    public ContainerEventsFactory(){
        registerConstructors();
    }

    protected void registerConstructors(){

        objRegistry.put(EventBytes.EVENT_CONTAINER_RESIZE, this::onContainerResize);
        objRegistry.put(EventBytes.EVENT_CONTAINER_MOVE, this::onContainerMove);
        // ===== Mouse Events =====
        registry.put(EventBytes.EVENT_MOUSE_BUTTON_DOWN, this::onMouseDown);
        registry.put(EventBytes.EVENT_MOUSE_BUTTON_UP, this::onMouseUp);
        registry.put(EventBytes.EVENT_MOUSE_SCROLL, this::onMouseScroll);
        
        // ===== Keyboard Events =====
        registry.put(EventBytes.EVENT_KEY_DOWN, this::onKeyDown);
        registry.put(EventBytes.EVENT_KEY_UP, this::onKeyUp);
        registry.put(EventBytes.EVENT_KEY_REPEAT, this::onKeyRepeat);
        registry.put(EventBytes.EVENT_KEY_CHAR, this::onKeyChar);
        
        // ===== Container Events =====
        registry.put(EventBytes.EVENT_CONTAINER_FOCUS_GAINED, this::onContainerFocusGained);
        registry.put(EventBytes.EVENT_CONTAINER_FOCUS_LOST, this::onContainerFocusLost);
        registry.put(EventBytes.EVENT_CONTAINER_CLOSED, this::onContainerClosed);
        registry.put(EventBytes.EVENT_CONTAINER_MINIMIZE, this::onContainerMinimize);
        registry.put(EventBytes.EVENT_CONTAINER_MAXIMIZE, this::onContainerMaximize);
        registry.put(EventBytes.EVENT_CONTAINER_RESTORE, this::onContainerRestore);
        registry.put(EventBytes.EVENT_CONTAINER_SHOWN, this::onContainerShown);
        registry.put(EventBytes.EVENT_CONTAINER_HIDDEN, this::onContainerHidden);
    }

    protected RoutedEvent onMouseDown(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new MouseButtonDownEvent(src, type, flags,
                p[0].getAsInt(),
                p[1].getAsDouble(),
                p[2].getAsDouble());
    }

    protected RoutedEvent onMouseUp(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new MouseButtonUpEvent(src, type, flags,
                p[0].getAsInt(),
                p[1].getAsDouble(),
                p[2].getAsDouble());
    }

    protected RoutedEvent onMouseScroll(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new MouseScrollEvent(src, type, flags,
                p[0].getAsDouble(),
                p[1].getAsDouble(),
                p[2].getAsDouble(),
                p[3].getAsDouble());
    }

    // ===== Keyboard Event Methods =====
    protected RoutedEvent onKeyDown(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new KeyDownEvent(src, type, flags, p[0], p[1]);
    }

    protected RoutedEvent onKeyUp(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new KeyUpEvent(src, type, flags, p[0], p[1]);
    }

    protected RoutedEvent onKeyRepeat(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new KeyRepeatEvent(src, type, flags, p[0], p[1]);
    }

    protected RoutedEvent onKeyChar(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new KeyCharEvent(src, type, flags, p[0]);
    }


    // ===== Container Event Methods =====
    protected RoutedEvent onContainerFocusGained(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new ContainerFocusGainedEvent(src, type, flags);
    }

    protected RoutedEvent onContainerFocusLost(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new ContainerFocusLostEvent(src, type, flags);
    }

    protected RoutedEvent onContainerClosed(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new ContainerCloseEvent(src, type, flags);
    }

    protected RoutedEvent onContainerMinimize(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new ContainerMinimizeEvent(src, type, flags);
    }

    protected RoutedEvent onContainerMaximize(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new ContainerMaximizeEvent(src, type, flags);
    }

    protected RoutedEvent onContainerRestore(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new ContainerRestoreEvent(src, type, flags);
    }

    protected RoutedEvent onContainerShown(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new ContainerShownEvent(src, type, flags);
    }

    protected RoutedEvent onContainerHidden(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes[] p){
        return new ContainerHiddenEvent(src, type, flags);
    }

    protected abstract RoutedEvent onContainerMove(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes p);
    protected abstract RoutedEvent onContainerResize(ContextPath src, NoteBytesReadOnly type, int flags, NoteBytes p);
    
    /**
     * Deserializes a RoutedPacket into a typed InputEvent.
     */
    public RoutedEvent from( ContextPath sourcePath, NoteBytes packet) {

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
        NoteBytes stateFlags = body.get(Keys.STATE_FLAGS);
        NoteBytes payloadNote = body.get(Keys.PAYLOAD);

        String description = EventBytes.getEventDescription(typeBytes); // for validation
        Log.logMsg("[RoutedEventFactory.from] Deserializing event: " + description);

        
        int flags = stateFlags != null ?stateFlags.getAsInt() : 0;

        switch(payloadNote.getType()){
            case NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE:
                return fromArray(sourcePath, typeBytes, flags, payloadNote);
            case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                return fromObject(sourcePath, typeBytes, flags, payloadNote);
            default:
                return new BaseEvent(sourcePath, typeBytes, flags, new NoteBytes[] { payloadNote});
        }
    }

    protected RoutedEvent fromArray(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags, NoteBytes payloadNote){
        NoteBytesArrayReadOnly payloadReadOnly = payloadNote != null ? payloadNote.getAsNoteBytesArrayReadOnly() : null;
        NoteBytes[] payloadArray = payloadReadOnly != null ? payloadReadOnly.getAsArray() : new NoteBytes[0];

        EventDeserializer constructor = registry.get(typeBytes);
        if (constructor == null) {
            return new BaseEvent(sourcePath, typeBytes, flags, payloadArray);
        }

        return constructor.create(sourcePath, typeBytes, flags, payloadArray);
    }

    protected RoutedEvent fromObject(ContextPath sourcePath, NoteBytesReadOnly typeBytes, int flags, NoteBytes payloadNote){
  


        NoteBytesEventDeserializer constructor = objRegistry.get(typeBytes);
        if (constructor == null) {
            return new BaseEvent(sourcePath, typeBytes, flags, new NoteBytes[] { payloadNote});
        }

        return constructor.create(sourcePath, typeBytes, flags, payloadNote);
    }

}
