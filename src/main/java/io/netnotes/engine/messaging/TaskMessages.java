package io.netnotes.engine.messaging;

import java.io.IOException;
import java.io.Serializable;

import io.netnotes.engine.messaging.NoteMessaging.General;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesPair;
import io.netnotes.engine.noteBytes.NoteSerializable;
import io.netnotes.engine.utils.Utils;
import javafx.concurrent.WorkerStateEvent;

public class TaskMessages {
    
    public static NoteBytesObject getResultMessage(String type, String message){
        NoteBytesObject result = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(new NoteBytes("type"), new NoteBytes(type)),
            new NoteBytesPair(new NoteBytes("message"), message),
            new NoteBytesPair(new NoteBytes("timeStamp"), new NoteBytes(System.currentTimeMillis()))
        });
        return result;
    }
    
    public static NoteBytesObject createSuccessResult(String message) {
        NoteBytesObject result = getResultMessage(General.SUCCESS, message);
        return result;
    }

    public static NoteBytesObject getSerializedSuccessMessage(String message, Object successObject)  {
        
        Object value = successObject != null && successObject instanceof Serializable ? successObject : null;
        NoteBytesPair successResult = null;
     
        try{
            successResult = value != null ? new NoteBytesPair("result", new NoteSerializable(value)) : null;
        }catch(IOException ex){
            try{
                successResult = new NoteBytesPair("exception", new NoteSerializable(Utils.getCreateException(ex)));
            }catch(IOException e2){
                successResult = new NoteBytesPair("exception", new NoteBytes(NoteMessaging.Error.UNKNOWN));
            }
        }
        
        NoteBytesObject result = getResultMessage(successResult != null ? General.SUCCESS : General.ERROR, message);
        if(successResult != null){
            result.add(successResult);
        }
        return result;
    }

    public static NoteBytesObject createErrorMessage(String scope, String message, WorkerStateEvent failedEvent)  {
        NoteBytesObject result = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("scope", scope),
            new NoteBytesPair("type", General.ERROR),
            new NoteBytesPair("message", message),
            new NoteBytesPair("timeStamp", new NoteBytes(System.currentTimeMillis()))
        });
        try{
            result.add("exception", new NoteSerializable(Utils.getCreateException(failedEvent)));
        }catch(IOException ex){
             result.add("exception", new NoteBytes(NoteMessaging.Error.UNKNOWN));
        }
        return result;
    }

    public static NoteBytesObject createErrorMessage(String scope, String message, Exception e)  {
        NoteBytesObject result = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("scope", scope),
            new NoteBytesPair("type", General.ERROR),
            new NoteBytesPair("message", message),
            new NoteBytesPair("timeStamp", new NoteBytes(System.currentTimeMillis()))
        });
        try{
            result.add("exception", new NoteSerializable(e));
        }catch(IOException ex){
             result.add("exception", new NoteBytes(NoteMessaging.Error.UNKNOWN));
        }
        return result;
    }

   
}
