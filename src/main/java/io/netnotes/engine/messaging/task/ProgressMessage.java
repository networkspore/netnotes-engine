package io.netnotes.engine.messaging.task;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;

public class ProgressMessage {

    public static NoteBytesObject getProgressMessage(NoteBytesReadOnly scope, long completed, long total,  NoteBytesReadOnly message){
        return getProgressMessage(scope,  new NoteBytesReadOnly(completed), new NoteBytesReadOnly(total), message);
    }

    public static NoteBytesObject getProgressMessage(String scope, long completed,long total, String message){
        return getProgressMessage(new NoteBytesReadOnly(scope), 
        new NoteBytesReadOnly(completed), new NoteBytesReadOnly(total), 
        new NoteBytesReadOnly(message));
    }

    public static NoteBytesObject getProgressMessage(NoteBytesReadOnly scope, NoteBytesReadOnly completed, NoteBytesReadOnly total, NoteBytesReadOnly message){
        NoteBytesObject messageObject = TaskMessages.getTaskMessage(scope, ProtocolMesssages.PROGRESS, message);
        messageObject.add(new NoteBytesPair[]{
            new NoteBytesPair(Keys.COMPLETED, completed),
            new NoteBytesPair(Keys.TOTAL, total)
        });
        return messageObject;
    }
    
      public static NoteBytesObject getProgressMessage(NoteBytesReadOnly scope, long completed,  long total, NoteBytesReadOnly message,
       NoteBytesPair... pairs){
        return getProgressMessage(scope, new NoteBytesReadOnly(completed), new NoteBytesReadOnly(total), message, pairs);
    }

    public static NoteBytesObject getProgressMessage(NoteBytesReadOnly scope, NoteBytesReadOnly completed, NoteBytesReadOnly total, NoteBytesReadOnly message, 
        NoteBytesPair... pairs
    ){
        NoteBytesObject messageObject = TaskMessages.getTaskMessage(scope, ProtocolMesssages.PROGRESS, message);
        int pairsLength = pairs != null ? pairs.length : 0;
        NoteBytesPair[] newPairs = new NoteBytesPair[pairsLength + 2];
        newPairs[0] = new NoteBytesPair(Keys.COMPLETED, completed);
        newPairs[1] = new NoteBytesPair(Keys.TOTAL, total);
        
        for(int i = 0 ; i < pairsLength ; i++){
            newPairs[i + 2]  = pairs[i];
        }
        
        return messageObject;
    }

    public static String getMessage(NoteBytesMap taskMessage){
        NoteBytes msg = taskMessage.get(Keys.MSG);
        if(msg != null){
            return msg.getAsString();
        }
        return "";
    }

    public static String getMessage(NoteBytesObject taskMessage){
        NoteBytesPair messagePair = taskMessage.get(Keys.MSG);
        if(messagePair != null){
            return messagePair.getAsString();
        }
        return "";
    }

     public static long getTotal(NoteBytesMap taskMessage){
        NoteBytes total = taskMessage.get(Keys.TOTAL);
        if(total != null){
            return total.getAsLong();
        }
        return -1;
    }

    public static long getTotal(NoteBytesObject taskMessage){
        NoteBytesPair totalPair = taskMessage.get(Keys.TOTAL);
        if(totalPair != null){
            return totalPair.getAsLong();
        }
        return -1;
    }

    public static long getCompleted(NoteBytesObject taskMessage){
        NoteBytesPair completedPair = taskMessage.get(Keys.COMPLETED);
        if(completedPair != null){
            return completedPair.getAsLong();
        }
        return -1;
    }

    public static long getCompleted(NoteBytesMap taskMessage){
        NoteBytes completed = taskMessage.get(Keys.COMPLETED);
        if(completed != null){
            return completed.getAsLong();
        }
        return -1;
    }

    public static double getPercentage(NoteBytesObject taskMessage) {
        long total = getTotal(taskMessage);
        long completed = getCompleted(taskMessage);

        return total > 0 && completed > 0 ? 
            BigDecimal.valueOf(completed)
                .divide(BigDecimal.valueOf(total), 3 , RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue() 
            : total == 0 ? 0 : -1;
    }

    public static double getPercentage(NoteBytesMap taskMessage) {
        long total = getTotal(taskMessage);
        long completed = getCompleted(taskMessage);

        return total > 0 && completed > 0 ? 
            BigDecimal.valueOf(completed)
                .divide(BigDecimal.valueOf(total), 3 , RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue() 
            : total == 0 ? 0 : -1;
    }

    public static String taskMessagetoString(NoteBytesObject taskMessage) {
        long total = getTotal(taskMessage);
        long completed = getCompleted(taskMessage);
        double percentage = getPercentage(taskMessage);
        String message = getMessage(taskMessage);
        return percentage > -1 ? String.format("Progress: %d/%d (%.1f%%) - %s", completed, total, percentage, message) : message;
    }

    public static String taskMessagetoString(NoteBytesMap taskMessage) {
        long total = getTotal(taskMessage);
        long completed = getCompleted(taskMessage);
        double percentage = getPercentage(taskMessage);
        String message = getMessage(taskMessage);
        return percentage > -1 ? String.format("Progress: %d/%d (%.1f%%) - %s", completed, total, percentage, message) : message;
    }

    public static CompletableFuture<Integer> writeAsync(String scope, long completed, long total, String message, AsyncNoteBytesWriter asyncWriter){
        return asyncWriter.writeAsync(getProgressMessage(
            new NoteBytesReadOnly(scope),  
            new NoteBytesReadOnly(completed), 
            new NoteBytesReadOnly(total), 
            new NoteBytesReadOnly(message))
        );
    }

    public static CompletableFuture<Integer> writeAsync(NoteBytesReadOnly scope, long completed, long total, String message, AsyncNoteBytesWriter asyncWriter){
        return asyncWriter.writeAsync(getProgressMessage(scope, 
            new NoteBytesReadOnly(completed),
            new NoteBytesReadOnly(total),  
            new NoteBytesReadOnly(message)));
    }


    public static CompletableFuture<Integer> writeAsync(NoteBytesReadOnly scope, NoteBytesReadOnly completed, NoteBytesReadOnly total, NoteBytesReadOnly message, AsyncNoteBytesWriter asyncWriter){

        return asyncWriter.writeAsync(getProgressMessage(scope, completed, total, message));
    }

    public static CompletableFuture<Integer> writeAsync(NoteBytesReadOnly scope, long completed, long total, NoteBytesReadOnly message, AsyncNoteBytesWriter asyncWriter){

        return asyncWriter.writeAsync(getProgressMessage(scope, new NoteBytesReadOnly(completed), new NoteBytesReadOnly(total), message));
    }
    

    public static CompletableFuture<Integer> writeAsync(String scope, long completed, long total, String message, 
         AsyncNoteBytesWriter asyncWriter, NoteBytesPair... pairs){

        return asyncWriter.writeAsync(getProgressMessage(new NoteBytesReadOnly(scope),  
            new NoteBytesReadOnly(completed), 
            new NoteBytesReadOnly(total),
            new NoteBytesReadOnly(message),
            pairs));
    }

    public static CompletableFuture<Integer> writeAsync(NoteBytesReadOnly scope,  
        NoteBytesReadOnly completed,
        NoteBytesReadOnly total, 
        NoteBytesReadOnly message, 
        AsyncNoteBytesWriter asyncWriter,
        NoteBytesPair... pairs
    ){

        return asyncWriter.writeAsync(getProgressMessage(scope, completed, total, 
            new NoteBytesReadOnly(message), pairs));
    }


    
}