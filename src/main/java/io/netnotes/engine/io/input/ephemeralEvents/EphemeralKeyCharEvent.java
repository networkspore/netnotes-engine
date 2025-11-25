package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.Keyboard;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralKeyCharEvent - Character event with ephemeral codepoint
 * SECURITY CRITICAL: Never convert to int - keep as bytes
 */
public class EphemeralKeyCharEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral codepointData;
    private final NoteBytesEphemeral stateFlagsBytes;

    private int stateFlagsCache = -1;
    
    public EphemeralKeyCharEvent(ContextPath sourcePath,
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

    public NoteBytes getUTF8() {
        return Keyboard.CodePointCharsByteRegistry.getCharBytes(codepointData);
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
    
    @Override
    public void close() {
        codepointData.close();
        stateFlagsBytes.close();
    }
}