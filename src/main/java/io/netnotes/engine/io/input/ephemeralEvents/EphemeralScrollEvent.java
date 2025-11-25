package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralScrollEvent - Scroll event with ephemeral data
 */
public class EphemeralScrollEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral xOffsetData;
    private final NoteBytesEphemeral yOffsetData;
    private final NoteBytesEphemeral mouseXData;
    private final NoteBytesEphemeral mouseYData;
    private final NoteBytesEphemeral stateFlagsBytes;
    private int stateFlagsCache = -1;

    public EphemeralScrollEvent(ContextPath sourcePath,
                                NoteBytesEphemeral xOffsetData,
                                NoteBytesEphemeral yOffsetData,
                                NoteBytesEphemeral mouseXData,
                                NoteBytesEphemeral mouseYData,
                                NoteBytesEphemeral stateFlags) {
        super(sourcePath);
        this.xOffsetData = xOffsetData;
        this.yOffsetData = yOffsetData;
        this.mouseXData = mouseXData;
        this.mouseYData = mouseYData;
        this.stateFlagsBytes = stateFlags;
    }
    
    public NoteBytesEphemeral getXOffsetData() {
        return xOffsetData;
    }
    
    public NoteBytesEphemeral getYOffsetData() {
        return yOffsetData;
    }
    
    public NoteBytesEphemeral getMouseXData() {
        return mouseXData;
    }
    
    public NoteBytesEphemeral getMouseYData() {
        return mouseYData;
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
        xOffsetData.close();
        yOffsetData.close();
        mouseXData.close();
        mouseYData.close();
        stateFlagsBytes.close();
    }
}