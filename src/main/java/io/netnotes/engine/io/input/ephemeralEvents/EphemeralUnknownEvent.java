package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

public class EphemeralUnknownEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral[] payload;
    private final NoteBytesEphemeral stateFlagsBytes;
    private int stateFlagsCache = -1;

    public EphemeralUnknownEvent(ContextPath sourcePath, NoteBytesEphemeral flags, NoteBytesEphemeral[] payload){
        super(sourcePath);
        this.payload = payload;
        this.stateFlagsBytes = flags;
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

    public NoteBytesEphemeral[] getPayload(){
        return payload;
    }

    @Override
    public void close() {
       for(NoteBytesEphemeral item : payload){
            item.close();
       }
       stateFlagsBytes.close();
    }
}
