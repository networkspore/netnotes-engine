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
    
    public EphemeralMouseButtonDownEvent(ContextPath sourcePath,
        NoteBytesEphemeral eventType,
        int flags,
        NoteBytesEphemeral buttonData,
        NoteBytesEphemeral xData,
        NoteBytesEphemeral yData
    ) {
        super(sourcePath,eventType, flags);
        this.buttonData = buttonData;
        this.xData = xData;
        this.yData = yData;
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
    

    
    
    @Override
    public void close() {
        buttonData.close();
        xData.close();
        yData.close();
        super.close();
    }
}