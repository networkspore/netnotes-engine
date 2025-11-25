package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralKeyRepeatEvent - Key repeat with ephemeral data
 */
public class EphemeralKeyRepeatEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral keyData;
    private final NoteBytesEphemeral scancodeData;
    private final NoteBytesEphemeral stateFlagsBytes;
    private int stateFlagsCache = -1;

    public EphemeralKeyRepeatEvent(ContextPath sourcePath,
                                   NoteBytesEphemeral keyData,
                                   NoteBytesEphemeral scancodeData,
                                   NoteBytesEphemeral stateFlags) {
        super(sourcePath);
        this.keyData = keyData;
        this.scancodeData = scancodeData;
        this.stateFlagsBytes = stateFlags;
    }
    
    public NoteBytesEphemeral getKeyData() {
        return keyData;
    }
    
    public NoteBytesEphemeral getScancodeData() {
        return scancodeData;
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
        keyData.close();
        scancodeData.close();
        stateFlagsBytes.close();
    }
}