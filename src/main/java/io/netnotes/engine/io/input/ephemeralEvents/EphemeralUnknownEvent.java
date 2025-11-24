package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

public class EphemeralUnknownEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral[] payload;
    private final int flags;
    public EphemeralUnknownEvent(ContextPath sourcePath, int flags, NoteBytesEphemeral[] payload){
        super(sourcePath);
        this.payload = payload;
        this.flags = flags;
    }

    public int getFlags(){
        return flags;
    }

    public NoteBytesEphemeral[] getPayload(){
        return payload;
    }

    @Override
    public void close() {
       for(NoteBytesEphemeral item : payload){
            item.close();
       }
    }
}
