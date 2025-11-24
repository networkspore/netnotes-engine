package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.Keyboard;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * EphemeralKeyCharModsEvent - Character with modifiers (ephemeral)
 * SECURITY CRITICAL: The primary event type for password input
 */
public class EphemeralKeyCharModsEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral codepointData;
    private final int stateFlags;
    
    public EphemeralKeyCharModsEvent(ContextPath sourcePath,
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

    public int getStateFlags() {
        return stateFlags;
    }
    
    /**
     * secure untraceable UTF8 bytes from registry
     */
    public NoteBytesReadOnly getUTF8() {
        return Keyboard.CodePointByteRegistry.get(codepointData);
    }
    
    @Override
    public void close() {
        codepointData.close();
    }
}