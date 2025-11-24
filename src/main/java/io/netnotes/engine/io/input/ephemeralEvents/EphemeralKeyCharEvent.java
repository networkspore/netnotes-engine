package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.Keyboard;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * EphemeralKeyCharEvent - Character event with ephemeral codepoint
 * SECURITY CRITICAL: Never convert to int - keep as bytes
 */
public class EphemeralKeyCharEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral codepointData;
    private final int stateFlags;
    
    public EphemeralKeyCharEvent(ContextPath sourcePath,
                                 NoteBytesEphemeral codepointData,
                                 int stateFlags) {
        super(sourcePath);
        this.codepointData = codepointData;
        this.stateFlags = stateFlags;
    }
    
    /**
     * Get codepoint as bytes (DO NOT convert to int for passwords)
     */
    public NoteBytesEphemeral getCodepointData() {
        return codepointData;
    }

    public NoteBytesReadOnly getUTF8() {
        return Keyboard.CodePointByteRegistry.get(codepointData);
    }
    
    public int getStateFlags() {
        return stateFlags;
    }
    
    @Override
    public void close() {
        codepointData.close();
    }
}