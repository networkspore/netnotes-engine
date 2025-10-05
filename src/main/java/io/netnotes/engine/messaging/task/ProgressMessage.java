package io.netnotes.engine.messaging.task;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;

public class ProgressMessage {
    public static final String PROGRESS_MESSAGE_TYPE = "Progress";

    public final static NoteBytesReadOnly TOTAL_KEY = new NoteBytesReadOnly("total");
    public final static NoteBytesReadOnly COMPLETED_KEY = new NoteBytesReadOnly("completed");

    public static NoteBytesObject getProgressMessage(String scope, long total, long completed, String message){
        NoteBytesObject messageObject = TaskMessages.getTaskMessage(scope, PROGRESS_MESSAGE_TYPE, message);
        messageObject.add(new NoteBytesPair[]{
            new NoteBytesPair(TOTAL_KEY, total),
            new NoteBytesPair(COMPLETED_KEY, completed)
        });
        return messageObject;
    }
         

    public static NoteBytesObject getProgressMessage(String scope, long total, long completed, String message, 
        NoteBytesPair[] pairs
    ){
        NoteBytesObject messageObject = TaskMessages.getTaskMessage(scope, PROGRESS_MESSAGE_TYPE, message);
        int pairsLength = pairs != null ? pairs.length : 0;
        NoteBytesPair[] newPairs = new NoteBytesPair[pairsLength + 2];
        newPairs[0] = new NoteBytesPair(TOTAL_KEY, total);
        newPairs[1] = new NoteBytesPair(COMPLETED_KEY, completed);

        for(int i = 0 ; i < pairsLength ; i++){
            newPairs[i + 2]  = pairs[i];
        }
        
        return messageObject;
    }

    public static String getMessage(NoteBytesObject taskMessage){
        NoteBytesPair messagePair = taskMessage.get(TaskMessages.MESSAGE_KEY);
        if(messagePair != null){
            return messagePair.getAsString();
        }
        return "";
    }

    public static long getTotal(NoteBytesObject taskMessage){
        NoteBytesPair totalPair = taskMessage.get(TOTAL_KEY);
        if(totalPair != null){
            return totalPair.getAsLong();
        }
        return -1;
    }

    public static long getCompleted(NoteBytesObject taskMessage){
        NoteBytesPair completedPair = taskMessage.get(COMPLETED_KEY);
        if(completedPair != null){
            return completedPair.getAsLong();
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

    public static String taskMessagetoString(NoteBytesObject taskMessage) {
        long total = getTotal(taskMessage);
        long completed = getCompleted(taskMessage);
        double percentage = getPercentage(taskMessage);
        String message = getMessage(taskMessage);
        return percentage > -1 ? String.format("Progress: %d/%d (%.1f%%) - %s", completed, total, percentage, message) : message;
    }

    public static CompletableFuture<Integer> writeAsync(String scope, long total, long completed, String message, AsyncNoteBytesWriter asyncWriter){

        return asyncWriter.writeAsync(getProgressMessage(scope, total, completed, message));
    }

    public static CompletableFuture<Integer> writeAsync(String scope, long total, long completed, String message, 
        NoteBytesPair[] pairs, AsyncNoteBytesWriter asyncWriter){

        return asyncWriter.writeAsync(getProgressMessage(scope, total, completed, message, pairs));
    }
}