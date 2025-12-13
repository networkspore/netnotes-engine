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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.NoteBytes;

public class LoggingHelpers {
    public enum LogLevel{
        NONE(0),
        GENERAL(1),
        HIGH_PRIORITY(2),
        ERROR(3),
        ALL(4);

        private final int value;

        // Define a private constructor to initialize the field
        private LogLevel(int value) {
            this.value = value;
        }

        // Provide a public getter method to access the value
        public int getValue() {
            return this.value;
        }

    }

    public static class Log {
        public static String logDirName = "logs";
        public static File logDir = new File(logDirName);
        public static String logName = "log";
        public static String logExt = ".txt";
        
        private static File logFile = createTimedLogFile();
        
        private static final Semaphore semaphore = new Semaphore(1);
        
        private static int logLevel = LogLevel.ALL.getValue();

        private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();


    
        public static File createTimedLogFile() {
            return createTimedLogFile(logName);
        }


        public static File createTimedLogFile(String name){
            try{
                Files.createDirectories(logDir.toPath());
                return new File(logDir.getAbsolutePath() + "/" + name + "-" + TimeHelpers.formatDate(System.currentTimeMillis()) + logExt);
            }catch(IOException e){
                return new File(name + "-" + TimeHelpers.formatDate(System.currentTimeMillis()) + logExt);
            }
        }

        public static CompletableFuture<Void> setLogLevel(LogLevel logLevel){
            return setLogLevel(logLevel.getValue());
        }
       
        public static CompletableFuture<Void> setLogLevel(int logLevel){
            return  CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                    try{
                        Log.logLevel = logLevel;
                    }finally{
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.logError("LoggingHelpers.setLogLevel: InterruptedException: " + e.toString());
                    throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
                }
            }, VirtualExecutors.getVirtualExecutor());
        }

        public static CompletableFuture<Void> setLogFile(File logFile){
            return  CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                    try{
                        Log.logFile = logFile;
                    }finally{
                        semaphore.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.logError("LoggingHelpers.setLogFile: InterruptedException: " + e.toString());
                    throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
                }
            }, VirtualExecutors.getVirtualExecutor());
        }

         public static CompletableFuture<Void> log(String scope, String msg, LogLevel level) {
            return enqueue(level.getValue(), () ->
                write(scope + ": " + msg + "\n")
            );
        }

        public static CompletableFuture<Void> logError(String msg) {
            return enqueue(LogLevel.ERROR.getValue(), () ->
                write("[ERROR] " + msg + "\n")
            );
        }

        public static CompletableFuture<Void> logError(String scope, Throwable error) {
            return enqueue(LogLevel.ERROR.getValue(), () ->
                write(scope + ": " + getThrowableMsg(error) + "\n")
            );
        }

        public static CompletableFuture<Void> logError(String scope, String msg, Throwable error) {
            return enqueue(LogLevel.ERROR.getValue(), () ->
                write(scope + ": '" + msg + "' - " + getThrowableMsg(error) + "\n")
            );
        }

        public static CompletableFuture<Void> logJson(String scope, JsonObject json) {
            return enqueue(LogLevel.ALL.getValue(), () ->
                write("**" + scope + "**\n" + gson.toJson(json) + "\n")
            );
        }

        public static CompletableFuture<Void> logNoteBytes(String scope, NoteBytes nb) {
            return enqueue(LogLevel.ALL.getValue(), () -> {
                JsonElement json = NoteBytes.toJson(nb);
                write("**" + scope + "**\n" + gson.toJson(json) + "\n");
            });
        }

        public static CompletableFuture<Void> logMsg(String msg) {
            return enqueue(LogLevel.ALL.getValue(), () -> {
              
                write(msg + "\n");
            });
        }


        // ----------------------------------------------------------
        // Core executor + gating
        // ----------------------------------------------------------
        private static CompletableFuture<Void> enqueue(int priority, Runnable action) {
            if (priority > logLevel) {
                return CompletableFuture.completedFuture(null);
            }

            return CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire();
                    action.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    write("[FATAL LOGGING ERROR] Interrupted\n");
                } finally {
                    semaphore.release();
                }
            }, VirtualExecutors.getVirtualExecutor());
        }


        // ----------------------------------------------------------
        // File creation + raw write
        // ----------------------------------------------------------
 

        private static void write(String text) {
            try {
                Files.writeString(
                    logFile.toPath(),
                    text,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                );
            } catch (Exception e) {
                // avoid recursion — write directly
                System.err.println("Logging failed: " + e.getMessage());
            }
        }

        boolean shouldLog(int priority) {
            return priority >= logLevel;
        }

        boolean shouldLog(LogLevel priority) {
            return priority.getValue() <= logLevel;
        }
    }
    
    

    public static void logJson(File logFile, String heading, JsonObject json){
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(logFile.toPath(), "**"+heading+"**" + gson.toJson(json) +"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            Log.logError("LoggingHelpers.logJson: IOException when printing to file: " + e.toString());
            throw new RuntimeException(NoteMessaging.Error.IO, e);
        }
        
    }
    public static int logJsonArray(File logFile, String heading, JsonArray jsonArray){
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String line = "**"+heading+"**" + gson.toJson(jsonArray) +"\n";
            Files.writeString(logFile.toPath(), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return line.length();
        } catch (Exception e) {
            Log.logError("LoggingHelpers.logJsonArray: IOException when printing to file: " + e.toString());
            return 0;
        }
    }
    public static int writeLogMsg(File logFile, String scope, String msg){
        try {
            String line = scope + ": " + msg + "\n";
            Files.writeString(logFile.toPath(), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return line.length();
        } catch (Exception e) {
            Log.logError("LoggingHelpers.writeLogMsg: IOException when printing to file: " + e.toString());
            return 0;
        }
    }

    public static int writeMsg(File logFile, String msg){
        try {
            String line = msg + "\n";
            Files.writeString(logFile.toPath(), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return line.length();
        } catch (Exception e) {
            Log.logError("LoggingHelpers.writeLogMsg: IOException when printing to file: " + e.toString());
            return 0;
        }
    }
    
    public static int writeLogMsg(File file, String scope, Throwable failed){
        return writeLogMsg(file, scope, getThrowableMsg(failed));
    }

    public static int writeLogMsg(File file, String scope, String msg, Throwable failed){
        return writeLogMsg(file, scope, "'" + msg + "' - " + getThrowableMsg(failed));
    }


    public static String getThrowableMsg(Throwable throwable){
        if(throwable != null){
            return throwable.getMessage();
        }else{
            return NoteMessaging.Error.UNKNOWN;
        }
    }

    public static int writeLogNoteBytes(File logFile, NoteBytes message){
        if(message != null){
            JsonElement json = NoteBytes.toJson(message);
            try {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String line = gson.toJson(json)+"\n";
                Files.writeString(logFile.toPath(), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return line.length();
            } catch (Exception e) {
                return writeLogMsg(logFile, "Logging error", e);
            }  
        }else{
            return writeLogMsg(logFile, "Logging error", "message is null");
        }
    }

}
