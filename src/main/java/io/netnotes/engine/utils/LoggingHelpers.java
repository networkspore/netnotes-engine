package io.netnotes.engine.utils;

import java.io.File;
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
        ALL,
        ERRORS_ONLY,
        NONE
    }

    public static class Log {
        private static File logFile = new File("netnotes-log.txt");
        private static final Semaphore semaphore = new Semaphore(1);
        
        private static LogLevel logLevel = LogLevel.ALL;

        public static CompletableFuture<Void> setLogLevel(LogLevel logLevel){
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

        public static CompletableFuture<Void> log(String scope, String msg){
            if(logLevel == LogLevel.ALL){
                return  CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                        try{
                            LoggingHelpers.writeLogMsg(logFile, scope, msg);
                        }finally{
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.logError("LoggingHelpers.logError: InterruptedException when printing to file: " + e.toString());
                        throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
                    }
                }, VirtualExecutors.getVirtualExecutor());
            }else{
                return CompletableFuture.completedFuture(null);
            }
        }

        public static CompletableFuture<Void> logMsg(String msg){
            if(logLevel == LogLevel.ALL){
                return  CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                        try{
                           writeMsg(logFile, msg);
                        }finally{
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.logError("LoggingHelpers.logError: InterruptedException when printing to file: " + e.toString());
                        throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
                    }
                }, VirtualExecutors.getVirtualExecutor());
            }else{
                return CompletableFuture.completedFuture(null);
            }
        }

        public static CompletableFuture<Void> logError(String msg){
            if(logLevel == LogLevel.ALL || logLevel == LogLevel.ERRORS_ONLY){
                return  CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                        try{
                           writeMsg(logFile, msg);
                        }finally{
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.logError("LoggingHelpers.logError: InterruptedException when printing to file: " + e.toString());
                        throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
                    }
                }, VirtualExecutors.getVirtualExecutor());
            }else{
                return CompletableFuture.completedFuture(null);
            }
        }

        public static CompletableFuture<Void> logError(String scope, Throwable throwable){
             if(logLevel == LogLevel.ALL || logLevel == LogLevel.ERRORS_ONLY){
                return  CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                        try{
                            LoggingHelpers.writeLogMsg(logFile, scope, throwable);
                        }finally{
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.logError("LoggingHelpers.logError: InterruptedException when printing to file: " + e.toString());
                        throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
                    }
                }, VirtualExecutors.getVirtualExecutor());
            }else{
                return CompletableFuture.completedFuture(null);
            }
        }


        public static CompletableFuture<Void> logError(String scope, String msg, Throwable throwable){
            if(logLevel == LogLevel.ALL || logLevel == LogLevel.ERRORS_ONLY){
                return  CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                        try{
                            LoggingHelpers.writeLogMsg(logFile, scope, msg, throwable);
                        }finally{
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.logError("LoggingHelpers.logError: InterruptedException when printing to file: " + e.toString());
                        throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
                    }
                }, VirtualExecutors.getVirtualExecutor());
            }else{
                return CompletableFuture.completedFuture(null);
            }
        }

        public static CompletableFuture<Void> logJson(String scope, JsonObject json){
             if(logLevel == LogLevel.ALL){
                return  CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                        try{
                            LoggingHelpers.logJson(logFile, scope, json);
                        }finally{
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.logError("LoggingHelpers.logJson: InterruptedException when printing to file: " + e.toString());
                        throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
                    }
                }, VirtualExecutors.getVirtualExecutor());
            }else{
                return CompletableFuture.completedFuture(null);
            }
        }

        public static CompletableFuture<Void> logNoteBytes(String scope, NoteBytes noteBytes){
             if(logLevel == LogLevel.ALL){
                return  CompletableFuture.runAsync(() -> {
                    try {
                        semaphore.acquire();
                        try{
                            LoggingHelpers.writeLogNoteBytes(logFile, noteBytes);
                        }finally{
                            semaphore.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Log.logError("LoggingHelpers.logNoteBytes: InterruptedException when printing to file: " + e.toString());
                        throw new RuntimeException(NoteMessaging.Error.INTERRUPTED, e);
                    }
                }, VirtualExecutors.getVirtualExecutor());
            }else{
                return CompletableFuture.completedFuture(null);
            }
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
