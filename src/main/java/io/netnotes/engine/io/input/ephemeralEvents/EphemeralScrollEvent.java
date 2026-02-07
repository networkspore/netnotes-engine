package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralScrollEvent - Scroll event with ephemeral data
 */
public class EphemeralScrollEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral xOffsetData;
    private final NoteBytesEphemeral yOffsetData;
    private final NoteBytesEphemeral mouseXData;
    private final NoteBytesEphemeral mouseYData;


    public EphemeralScrollEvent(ContextPath sourcePath,
        NoteBytesEphemeral typeBytes,
        int stateFlags,
        NoteBytesEphemeral xOffsetData,
        NoteBytesEphemeral yOffsetData,
        NoteBytesEphemeral mouseXData,
        NoteBytesEphemeral mouseYData
    ) {
        super(sourcePath, typeBytes, stateFlags);
        this.xOffsetData = xOffsetData;
        this.yOffsetData = yOffsetData;
        this.mouseXData = mouseXData;
        this.mouseYData = mouseYData;
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

    
    
    @Override
    public void close() {
        super.close();
        xOffsetData.close();
        yOffsetData.close();
        mouseXData.close();
        mouseYData.close();
    }
}