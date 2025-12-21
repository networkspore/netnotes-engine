package io.netnotes.engine.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;

public class LoggingHelpers {
    public enum LogLevel{
        NONE(0),
        GENERAL(1),
        HIGH_PRIORITY(2),
        ERROR(3),
        ALL(4);

        private final int value;

        private LogLevel(int value) {
            this.value = value;
        }

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
        
        // Serialized virtual thread executor for all log writes
        private static final SerializedVirtualExecutor logExecutor = new SerializedVirtualExecutor();
        
        // Timeout for individual log operations
        private static final long LOG_TIMEOUT_MS = 2000;
        
        private static volatile int logLevel = LogLevel.ALL.getValue();

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
       
        public static CompletableFuture<Void> setLogLevel(int level){
            return logExecutor.execute(() -> {
                Log.logLevel = level;
            });
        }

        public static CompletableFuture<Void> setLogFile(File file){
            return logExecutor.execute(() -> {
                Log.logFile = file;
            });
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
            return enqueue(LogLevel.ALL.getValue(), () ->{
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                write("**" + scope + "**\n" + gson.toJson(json) + "\n");
            });
        }

        public static CompletableFuture<Void> logNoteBytes(String scope, NoteBytes nb) {
            if(nb == null){
                return enqueue(LogLevel.ALL.getValue(), () -> {
                    write("**" + scope + "**\n" + "null" + "\n");
                });
            }
            
            return enqueue(LogLevel.ALL.getValue(), () -> {
                writeLogNoteBytes(logFile, nb);
            });
        }

        public static CompletableFuture<Void> logNoteBytes(String scope, NoteBytesMap map) {
            if(map == null){
                return enqueue(LogLevel.ALL.getValue(), () -> {
                    write("**" + scope + "**\n" + "null" + "\n");
                });
            }
   
            return enqueue(LogLevel.ALL.getValue(), () -> {
                writeLogNoteBytes(logFile, map);
            });
        }

        public static CompletableFuture<Void> logMsg(String msg) {
            return enqueue(LogLevel.ALL.getValue(), () -> {
                write(msg + "\n");
            });
        }

        /**
         * Enqueues a log action with priority checking and timeout handling.
         * Returns a CompletableFuture that completes when the log is written,
         * times out, or fails.
         */
        private static CompletableFuture<Void> enqueue(int priority, Runnable action) {
            // Skip if priority too low
            if (priority > logLevel) {
                return CompletableFuture.completedFuture(null);
            }
            
            // Submit to executor with timeout handling
            CompletableFuture<Void> future = logExecutor.execute(action);
            
            // Apply timeout with fallback to stderr
            return future
                .orTimeout(LOG_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .whenComplete((v, ex) -> {
                    // Log timeout or error to stderr as fallback
                    if(ex != null){
                        if (ex instanceof java.util.concurrent.TimeoutException) {
                            System.err.println("[LOG TIMEOUT] Write hung after " + LOG_TIMEOUT_MS + "ms");
                        } else {
                            System.err.println("[LOG ERROR] " + ex.toString());
                        }
                    }
                });
        }

        private static void write(String text) {
            try {
                Files.writeString(
                    logFile.toPath(),
                    text,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
                );
            } catch (Exception e) {
                System.err.println("[LOG WRITE FAILED] " + e.getMessage());
            }
        }

        boolean shouldLog(int priority) {
            return priority >= logLevel;
        }

        boolean shouldLog(LogLevel priority) {
            return priority.getValue() <= logLevel;
        }
        
        /**
         * Initiates graceful shutdown of the logging executor.
         * Queued logs will complete, but new logs will be rejected.
         */
        public static void shutdown() {
            logExecutor.shutdown();
        }
        
        /**
         * Immediately shuts down the logging executor and cancels queued logs.
         */
        public static void shutdownNow() {
            logExecutor.shutdownNow();
        }
        
        /**
         * Waits for all queued logs to complete after shutdown.
         * 
         * @param timeout maximum time to wait
         * @param unit time unit
         * @return true if terminated, false if timeout
         */
        public static boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return logExecutor.awaitTermination(timeout, unit);
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

    public static int writeLogNoteBytes(File logFile, NoteBytesMap message){
        if(message != null){
            NoteBytesObject nbo = message.toNoteBytes();
            JsonElement json = NoteBytes.toJson(nbo);
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