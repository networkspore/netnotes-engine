package io.netnotes.engine.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.NoteBytesObject;

public class LoggingHelpers {

    public static CompletableFuture<Void> logAsync(Semaphore semaphore, File asyncLogFile, NoteBytesObject taskMessageObject){
        return  CompletableFuture.runAsync(() -> {
            try {
                semaphore.acquire();
                try{
                   writeLogNoteByteObject(asyncLogFile, taskMessageObject);
                }finally{
                   semaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                 System.err.println("LoggingHelpers.logAsync: InterruptedException when printing to file: " + e.toString());
                throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
            }
        });
    }
 
    public static void logJson(File logFile, String heading, JsonObject json){

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(logFile.toPath(), "**"+heading+"**" + gson.toJson(json) +"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("LoggingHelpers.logJson: IOException when printing to file: " + e.toString());
            throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
        }
        
    }
    public static int logJsonArray(File logFile, String heading, JsonArray jsonArray){
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String line = "**"+heading+"**" + gson.toJson(jsonArray) +"\n";
            Files.writeString(logFile.toPath(), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return line.length();
        } catch (IOException e) {
            System.err.println("LoggingHelpers.logJsonArray: IOException when printing to file: " + e.toString());
            return 0;
        }
    }
    public static int writeLogMsg(File logFile, String scope, String msg){
        try {
            String line = scope + ": " + msg + "\n";
            Files.writeString(logFile.toPath(), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return line.length();
        } catch (IOException e) {
            System.err.println("LoggingHelpers.writeLogMsg: IOException when printing to file: " + e.toString());
            return 0;
        }
    }
    
    public static int writeLogMsg(File file, String scope, Throwable failed){
        return writeLogMsg(file, scope, getErrorMsg(failed));
    }

    public static String getErrorMsg(Throwable throwable){
        if(throwable != null){
            return throwable.getMessage();
        }else{
            return NoteMessaging.Error.UNKNOWN;
        }
    }

    public static int writeLogNoteByteObject(File logFile, NoteBytesObject messageObject){
        if(messageObject != null){
            JsonObject json = messageObject.getAsJsonObject();
            try {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String line = gson.toJson(json)+"\n";
                Files.writeString(logFile.toPath(), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return line.length();
            } catch (IOException e) {
                return writeLogMsg(logFile, "Logging error", e);
            }  
        }else{
            return writeLogMsg(logFile, "Logging error", "message is null");
        }
    }

}
