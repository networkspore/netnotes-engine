package io.netnotes.engine.io.input.events;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;

public class BaseEvent extends RoutedEvent {
    private final ContextPath sourcePath;
    private final NoteBytes typeBytes;
    private int flags;
    private final NoteBytes[] payload;

    public BaseEvent(ContextPath sourcePath, NoteBytes typeBytes, int flags, NoteBytes[] payload){
        this.sourcePath = sourcePath;
        this.typeBytes = typeBytes;
        this.flags = flags;
        this.payload = payload;
    }

    @Override
    public ContextPath getSourcePath() {
        return sourcePath;
    }

    @Override
    public NoteBytes getEventTypeBytes() {
        return typeBytes;
    }

    @Override
    public int getStateFlags() {
        return flags;
    }

    @Override
    public void setStateFlags(int flags) {
        this.flags = flags;
    }

    public NoteBytes[] getPayload(){
        return payload;
    }
    
}
