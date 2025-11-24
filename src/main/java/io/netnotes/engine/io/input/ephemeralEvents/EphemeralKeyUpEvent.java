package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralKeyUpEvent - Key up with ephemeral data
 */
public class EphemeralKeyUpEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral keyData;
    private final NoteBytesEphemeral scancodeData;
    private final int stateFlags;
    
    public EphemeralKeyUpEvent(ContextPath sourcePath,
                               NoteBytesEphemeral keyData,
                               NoteBytesEphemeral scancodeData,
                               int stateFlags) {
        super(sourcePath);
        this.keyData = keyData;
        this.scancodeData = scancodeData;
        this.stateFlags = stateFlags;
    }
    
    public NoteBytesEphemeral getKeyData() {
        return keyData;
    }
    
    public NoteBytesEphemeral getScancodeData() {
        return scancodeData;
    }
    
    public int getStateFlags() {
        return stateFlags;
    }
    
    @Override
    public void close() {
        keyData.close();
        scancodeData.close();
    }
}
