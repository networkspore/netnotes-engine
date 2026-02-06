package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralKeyDownEvent - Key down with ephemeral data
 */
public class EphemeralKeyDownEvent extends EphemeralKeyboardEvent {
    private final NoteBytesEphemeral keyData;
    private final NoteBytesEphemeral scancodeData;
    
    public EphemeralKeyDownEvent(ContextPath sourcePath,
        NoteBytesEphemeral typeBytes,
        int stateFlags,
        NoteBytesEphemeral keyData,
        NoteBytesEphemeral scancodeData
    ) {
        super(sourcePath, typeBytes, stateFlags);
        this.keyData = keyData;
        this.scancodeData = scancodeData;
    }
    
    /**
     * Get key code bytes (caller must not modify)
     */
    public NoteBytesEphemeral getKeyCodeBytes() {
        return keyData;
    }
    
    /**
     * Get scancode bytes (caller must not modify)
     */
    public NoteBytesEphemeral getScancodeData() {
        return scancodeData;
    }
    

    
    @Override
    public void close() {
        keyData.close();
        scancodeData.close();
        super.close();
    }
}