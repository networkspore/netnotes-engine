package io.netnotes.engine.io;

import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

public class MessageBuilder {
    public static NoteBytesObject createCommand(NoteBytesReadOnly command, NoteBytesPair... params) {
        NoteBytesObject msg = new NoteBytesObject();
        msg.add(Keys.TYPE, EventBytes.TYPE_CMD);
        msg.add(Keys.SEQUENCE, NoteUUID.getNextUUID64());
        msg.add(Keys.CMD, command);
        
        for (NoteBytesPair param : params) {
            msg.add(param.getKey(), param.getValue());
        }
        
        return msg;
    }
    
    public static NoteBytesObject createError(int errorCode, String message) {
        NoteBytesObject msg = new NoteBytesObject();
        msg.add(Keys.TYPE, EventBytes.TYPE_ERROR);
        msg.add(Keys.SEQUENCE, NoteUUID.getNextUUID64());
        msg.add(Keys.ERROR_CODE, errorCode);
        msg.add(Keys.MSG, message);
        return msg;
    }
    
    public static NoteBytesObject createAccept(String status) {
        NoteBytesObject msg = new NoteBytesObject();
        msg.add(Keys.TYPE, EventBytes.TYPE_ACCEPT);
        msg.add(Keys.SEQUENCE, NoteUUID.getNextUUID64());
        msg.add(Keys.STATUS, status);
        return msg;
    }
    

}