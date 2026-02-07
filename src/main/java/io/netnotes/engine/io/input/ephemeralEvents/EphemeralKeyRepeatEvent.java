package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralKeyRepeatEvent - Key repeat with ephemeral data
 */
public class EphemeralKeyRepeatEvent extends EphemeralKeyboardEvent {
    private final NoteBytesEphemeral keyData;
    private final NoteBytesEphemeral scancodeData;

    public EphemeralKeyRepeatEvent(ContextPath sourcePath,
        NoteBytesEphemeral typeBytes,
        int stateFlags,
        NoteBytesEphemeral keyData,
        NoteBytesEphemeral scancodeData
    ) {
        super(sourcePath, typeBytes, stateFlags);
        this.keyData = keyData;
        this.scancodeData = scancodeData;
    }
    
    public NoteBytesEphemeral getKeyData() {
        return keyData;
    }
    
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