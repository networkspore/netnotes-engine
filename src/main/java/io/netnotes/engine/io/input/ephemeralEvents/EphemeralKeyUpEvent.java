package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralKeyUpEvent - Key up with ephemeral data
 */
public class EphemeralKeyUpEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral keyData;
    private final NoteBytesEphemeral scancodeData;
    private int stateFlagsCache = -1;
     private final NoteBytesEphemeral stateFlagsBytes;
    
    public EphemeralKeyUpEvent(ContextPath sourcePath,
                               NoteBytesEphemeral keyData,
                               NoteBytesEphemeral scancodeData,
                               NoteBytesEphemeral stateFlagsBytes) {
        super(sourcePath);
        this.keyData = keyData;
        this.scancodeData = scancodeData;
        this.stateFlagsBytes = stateFlagsBytes;
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
