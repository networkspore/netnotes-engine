package io.netnotes.engine.io.input.ephemeralEvents;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

public class EphemeralEvent extends EphemeralRoutedEvent {
    private final NoteBytesEphemeral[] payload;

    public EphemeralEvent(ContextPath sourcePath, NoteBytesEphemeral type,  int flags,
         NoteBytesEphemeral[] payload
    ){
        super(sourcePath, type, flags);
        this.payload = payload;
    }


    public NoteBytesEphemeral[] getPayload(){
        return payload;
    }

    @Override
    public void close() {
       for(NoteBytesEphemeral item : payload){
            item.close();
       }
       super.close();
    }
}
