package io.netnotes.engine.io.input.ephemeralEvents;


import io.netnotes.engine.crypto.RandomService;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * EphemeralRoutedEvent - Base class for ephemeral events
 * These events contain sensitive data that must be wiped after use
 * SECURITY CRITICAL: Always use try-with-resources
 */
public abstract class EphemeralRoutedEvent implements RoutedEvent, AutoCloseable {
    private final ContextPath sourcePath;
    protected final NoteBytesEphemeral eventType;
    protected final int[] flag = new int[1];


    protected EphemeralRoutedEvent(ContextPath sourcePath, NoteBytesEphemeral eventType, int stateFlags) {
        this.sourcePath = sourcePath;
        this.eventType = eventType;
        this.flag[0] = stateFlags;
    }
    
    public ContextPath getSourcePath(){
        return sourcePath;
    }

    @Override
    public NoteBytesEphemeral getEventTypeBytes(){
        return eventType;
    }
    @Override
    public int getStateFlags(){
        return flag[0];
    }
    @Override
    public void setStateFlags(int flags){
        flag[0] = flags;
    }

    /**
     * Close and wipe all sensitive data
     */
    @Override
    public void close(){
        eventType.close();
        flag[0] = RandomService.getRandomInt(0, Integer.MAX_VALUE);
        flag[0] *= 0;
        if(flag[0] != 0){
            Log.logMsg("Flag not updated:" + flag[0]);
        }
    }
}

