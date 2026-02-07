package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.noteBytes.NoteBytesEphemeral;

public abstract class EphemeralKeyboardEvent extends EphemeralRoutedEvent {

    protected EphemeralKeyboardEvent(ContextPath sourcePath, NoteBytesEphemeral eventType, int stateFlags) {
        super(sourcePath, eventType, stateFlags);
    }
    
}
