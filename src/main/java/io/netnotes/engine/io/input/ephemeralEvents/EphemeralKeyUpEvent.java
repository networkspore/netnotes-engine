package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralKeyUpEvent - Key up with ephemeral data
 */
public class EphemeralKeyUpEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral keyData;
    private final NoteBytesEphemeral scancodeData;
    
    public EphemeralKeyUpEvent(ContextPath sourcePath,
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
