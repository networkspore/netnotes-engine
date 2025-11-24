package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

/**
 * EphemeralMouseButtonEvent - Mouse button with ephemeral data
 */
public class EphemeralMouseButtonDownEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral buttonData;
    private final NoteBytesEphemeral xData;
    private final NoteBytesEphemeral yData;
    private final int stateFlags;
    
    public EphemeralMouseButtonDownEvent(ContextPath sourcePath,
                                         NoteBytesEphemeral buttonData,
                                         NoteBytesEphemeral xData,
                                         NoteBytesEphemeral yData,
                                         int stateFlags) {
        super(sourcePath);
        this.buttonData = buttonData;
        this.xData = xData;
        this.yData = yData;
        this.stateFlags = stateFlags;
    }
    
    public NoteBytesEphemeral getButtonData() {
        return buttonData;
    }
    
    public NoteBytesEphemeral getXData() {
        return xData;
    }
    
    public NoteBytesEphemeral getYData() {
        return yData;
    }
    
    public int getStateFlags() {
        return stateFlags;
    }
    
    @Override
    public void close() {
        buttonData.close();
        xData.close();
        yData.close();
    }
}