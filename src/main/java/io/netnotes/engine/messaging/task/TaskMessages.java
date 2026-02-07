package io.netnotes.engine.messaging.task;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.NoteSerializable;
import io.netnotes.noteBytes.collections.NoteBytesPair;
import io.netnotes.noteBytes.processing.AsyncNoteBytesWriter;

public class TaskMessages {

     public static NoteBytesObject getTaskMessage(NoteBytesReadOnly scope, NoteBytesReadOnly type, String message){
        return getTaskMessage(scope, type, new NoteBytesReadOnly(message));
    }

    public static NoteBytesObject getTaskMessage(String scope, String type, String message){
        return getTaskMessage(new NoteBytesReadOnly(scope), new NoteBytesReadOnly(type), new NoteBytesReadOnly(message));
    }

    public static NoteBytesObject getTaskMessage(NoteBytesReadOnly scope, NoteBytesReadOnly type, NoteBytesReadOnly message){
        NoteBytesObject result = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(new NoteBytes(Keys.SCOPE), new NoteBytes(scope)),
            new NoteBytesPair(new NoteBytes(Keys.TYPE), new NoteBytes(type)),
            new NoteBytesPair(new NoteBytes(Keys.MSG), message),
            new NoteBytesPair(new NoteBytes(Keys.TIMESTAMP), new NoteBytes(System.currentTimeMillis()))
        });
        return result;
    }
    public static NoteBytesObject getProgressMessage(String scope, String type, String message){
        return getProgressMessage(new NoteBytesReadOnly(scope),new NoteBytesReadOnly(type),new NoteBytesReadOnly(message));
    }

    public static NoteBytesObject getProgressMessage(NoteBytesReadOnly scope, NoteBytesReadOnly type, NoteBytesReadOnly message){
        NoteBytesObject result = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(new NoteBytes(Keys.SCOPE), scope),
            new NoteBytesPair(new NoteBytes(Keys.TYPE), type),
            new NoteBytesPair(new NoteBytes(Keys.MSG), message),
            new NoteBytesPair(new NoteBytes(Keys.TIMESTAMP), new NoteBytes(System.currentTimeMillis()))
        });
        return result;
    }
    
    public static NoteBytesObject createSuccessResult(String scope, String message) {
        NoteBytesObject result = getTaskMessage(new NoteBytesReadOnly(scope), ProtocolMesssages.SUCCESS, new NoteBytesReadOnly(message));
        return result;
    }

     public static NoteBytesObject createSuccessResult(String scope, NoteBytesReadOnly message) {
        NoteBytesObject result = getTaskMessage(new NoteBytesReadOnly(scope), ProtocolMesssages.SUCCESS, message);
        return result;
    }


 

    public static NoteBytesObject createErrorMessage(String scope, String message, Throwable e)  {
        NoteBytesObject result = getTaskMessage(new NoteBytesReadOnly(scope), ProtocolMesssages.ERROR, new NoteBytesReadOnly(message));
        if(e != null){
            try{
                result.add(Keys.EXCEPTION, new NoteSerializable(e));
            }catch(IOException ex){

            }
        }
        return result;
    }

     public static NoteBytesObject createErrorMessage(NoteBytesReadOnly scope, String message, Throwable e)  {
        NoteBytesObject result = getTaskMessage(scope, ProtocolMesssages.ERROR, new NoteBytesReadOnly(message));
        if(e != null){
            try{
                result.add(Keys.EXCEPTION, new NoteSerializable(e));
            }catch(IOException ex){

            }
        }
        return result;
    }

     public static NoteBytesObject createErrorMessage(NoteBytesReadOnly scope, NoteBytesReadOnly message, Throwable e)  {
        NoteBytesObject result = getTaskMessage(scope, ProtocolMesssages.ERROR, message);
        if(e != null){
            try{
                result.add(Keys.EXCEPTION, new NoteSerializable(e));
            }catch(IOException ex){

            }
        }
        return result;
    }


    public static CompletableFuture<Integer> writeErrorAsync(NoteBytesReadOnly scope, String message, Throwable e, AsyncNoteBytesWriter asyncWriter){

        return asyncWriter.writeAsync(createErrorMessage(scope, message, e));
    }
   
    public static CompletableFuture<Integer> writeErrorAsync(String scope, String message, Throwable e, AsyncNoteBytesWriter asyncWriter){

        return asyncWriter.writeAsync(createErrorMessage(scope, message, e));
    }

}
