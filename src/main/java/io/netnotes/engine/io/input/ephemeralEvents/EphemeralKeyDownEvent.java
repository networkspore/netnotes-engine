package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralKeyDownEvent - Key down with ephemeral data
 */
public class EphemeralKeyDownEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral keyData;
    private final NoteBytesEphemeral scancodeData;
    private final int stateFlags;
    
    public EphemeralKeyDownEvent(ContextPath sourcePath, 
                                 NoteBytesEphemeral keyData,
                                 NoteBytesEphemeral scancodeData,
                                 int stateFlags) {
        super(sourcePath);
        this.keyData = keyData;
        this.scancodeData = scancodeData;
        this.stateFlags = stateFlags;
    }
    
    /**
     * Get key code bytes (caller must not modify)
     */
    public NoteBytesEphemeral getKeyData() {
        return keyData;
    }
    
    /**
     * Get scancode bytes (caller must not modify)
     */
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