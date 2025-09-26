package io.netnotes.engine.messaging.task;

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.messaging.NoteMessaging.General;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteSerializable;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.utils.Utils;
import javafx.concurrent.WorkerStateEvent;

public class TaskMessages {
    public final static NoteBytesReadOnly MESSAGE_KEY = new NoteBytesReadOnly("message");
    public final static NoteBytesReadOnly TYPE_KEY = new NoteBytesReadOnly("type");
    public final static NoteBytesReadOnly EXCEPTION_KEY = new NoteBytesReadOnly("exception");
    public final static NoteBytesReadOnly SCOPE_KEY = new NoteBytesReadOnly("scope");
    public final static NoteBytesReadOnly RESULT_KEY = new NoteBytesReadOnly("result");
    public final static NoteBytesReadOnly TIMESTAM_KEY = new NoteBytesReadOnly("timeStamp");

    public static NoteBytesObject getTaskMessage(String scope, String type, String message){
        NoteBytesObject result = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(new NoteBytes(SCOPE_KEY), new NoteBytes(scope)),
            new NoteBytesPair(new NoteBytes(TYPE_KEY), new NoteBytes(type)),
            new NoteBytesPair(new NoteBytes(MESSAGE_KEY), message),
            new NoteBytesPair(new NoteBytes(TIMESTAM_KEY), new NoteBytes(System.currentTimeMillis()))
        });
        return result;
    }

    public static NoteBytesObject getProgressMessage(String scope, String type, String message){
        NoteBytesObject result = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(new NoteBytes(SCOPE_KEY), new NoteBytes(scope)),
            new NoteBytesPair(new NoteBytes(TYPE_KEY), new NoteBytes(type)),
            new NoteBytesPair(new NoteBytes(MESSAGE_KEY), message),
            new NoteBytesPair(new NoteBytes(TIMESTAM_KEY), new NoteBytes(System.currentTimeMillis()))
        });
        return result;
    }
    
    public static NoteBytesObject createSuccessResult(String scope, String message) {
        NoteBytesObject result = getTaskMessage(scope, General.SUCCESS, message);
        return result;
    }

    public static NoteBytesObject getSerializedSuccessMessage(String scope, String message, Object successObject)  {
        
        Object value = successObject != null && successObject instanceof Serializable ? successObject : null;
        NoteBytesPair successResult = null;
     
        try{
            successResult = value != null ? new NoteBytesPair(RESULT_KEY, new NoteSerializable(value)) : null;
        }catch(IOException ex){
            try{
                successResult = new NoteBytesPair(EXCEPTION_KEY, new NoteSerializable(Utils.getCreateException(ex)));
            }catch(IOException e2){
              
            }
        }
        
        NoteBytesObject result = getTaskMessage(scope,  General.SUCCESS , message);
        if(successResult != null){
            result.add(successResult);
        }
        return result;
    }

    public static NoteBytesObject createErrorMessage(String scope, String message, WorkerStateEvent failedEvent)  {
        NoteBytesObject result = getTaskMessage(scope, General.ERROR, message);
        try{
            result.add("exception", new NoteSerializable(Utils.getCreateException(failedEvent)));
        }catch(IOException ex){
   
        }
        return result;
    }

    public static NoteBytesObject createErrorMessage(String scope, String message, Throwable e)  {
        NoteBytesObject result = getTaskMessage(scope, General.ERROR, message);
        try{
            result.add(EXCEPTION_KEY, new NoteSerializable(e));
        }catch(IOException ex){

        }
        return result;
    }


   
    public static CompletableFuture<Integer> writeErrorAsync(String scope, String message, Throwable e, AsyncNoteBytesWriter asyncWriter){

        return asyncWriter.writeAsync(createErrorMessage(scope, message, e));
    }

}
