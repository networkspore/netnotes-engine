package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.Keyboard;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralKeyCharModsEvent - Character with modifiers (ephemeral)
 * SECURITY CRITICAL: The primary event type for password input
 */
public class EphemeralKeyCharModsEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral codepointData;
    private final NoteBytesEphemeral stateFlagsBytes;
    private int stateFlagsCache = -1;
    
    public EphemeralKeyCharModsEvent(ContextPath sourcePath,
                                     NoteBytesEphemeral codepointData,
                                     NoteBytesEphemeral stateFlags) {
        super(sourcePath);
        this.codepointData = codepointData;
        this.stateFlagsBytes = stateFlags;
    }
    
    /**
     * Get codepoint as bytes (DO NOT convert to int for passwords)
     */
    public NoteBytesEphemeral getCodepointData() {
        return codepointData;
    }

    public NoteBytesEphemeral getStateFlagsBytes() {
        return stateFlagsBytes;
    }

    public int getStateFlags(){
        if(stateFlagsCache != -1){
            return stateFlagsCache;
        }
        stateFlagsCache = stateFlagsBytes.getAsInt();
        return stateFlagsCache;
    }
    
    /**
     * secure untraceable UTF8 bytes from registry
     */
    public NoteBytes getUTF8() {
        return Keyboard.CodePointCharsByteRegistry.getCharBytes(codepointData);
    }
    
    @Override
    public void close() {
        codepointData.close();
        stateFlagsBytes.close();
    }
}