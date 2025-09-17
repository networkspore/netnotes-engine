package io.netnotes.engine.messaging;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.netnotes.engine.NetworksDataInterface;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReader;
import io.netnotes.engine.noteBytes.NoteBytesWriter;
import io.netnotes.engine.noteBytes.NoteStringArray;
import io.netnotes.engine.utils.Utils;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;



public class StreamUtils {

    public static final int BUFFER_SIZE = 128 * 1024;
    public static final int PIPE_BUFFER_SIZE = 1024 * 1024;


    public static class StreamProgressTracker {
        private final AtomicLong bytesProcessed = new AtomicLong(0);
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile long totalBytes = -1; // -1 means unknown
        
        public long getBytesProcessed() { return bytesProcessed.get(); }
        public void addBytesProcessed(long bytes) { bytesProcessed.addAndGet(bytes); }
        public boolean isCancelled() { return cancelled.get(); }
        public void cancel() { cancelled.set(true); }
        public void setTotalBytes(long total) { this.totalBytes = total; }
        public long getTotalBytes() { return totalBytes; }
        public double getProgress() { 
            return totalBytes > 0 ? (double) bytesProcessed.get() / totalBytes : -1; 
        }
    }


    public static Future<?> writeStreamToStream(PipedOutputStream decryptedOutputStream, PipedOutputStream inParseStream, ExecutorService execService, EventHandler<WorkerStateEvent> onFailed){
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException {

                try(
                    PipedInputStream decryptedInputStream = new PipedInputStream(decryptedOutputStream, PIPE_BUFFER_SIZE)
                ){
  
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int length = 0;
                    while((length = decryptedInputStream.read(buffer)) != -1){
                        inParseStream.write(buffer, 0, length);
                        inParseStream.flush();
                    }
                    inParseStream.close();
                    return null;
                }
            }
        };
        task.setOnFailed((failed)->{
            try {
                inParseStream.close();
            } catch (IOException e) {
                Utils.writeLogMsg("Utils.writeStreamToStream.close", e.toString());
            }
            Utils.returnException(failed, execService, onFailed);
        });
        return execService.submit(task);
    }

    
    public static Future<?> readBroadcastReplyObjects(PipedOutputStream outputStream, ExecutorService execService,  EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
         Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException {
                try(
                    NoteBytesReader reader = new NoteBytesReader(new PipedInputStream(outputStream));
                ){
                    NoteBytesObject repliesObject = new NoteBytesObject();
                    NoteBytes nextNoteBytes = null;
                        
                    while((nextNoteBytes = reader.nextNoteBytes()) != null){
                        repliesObject.add(nextNoteBytes, reader.nextNoteBytes());

                    }
                    return repliesObject;
                }
            }
        };
        task.setOnSucceeded(onSucceeded);
        task.setOnFailed(onFailed);
        return execService.submit(task);
    }

      protected boolean broadcastNoteToSubscribers(NoteStringArray ids, NoteBytesObject noteBytesObject, NetworksDataInterface dataInterface, ExecutorService execService, EventHandler<WorkerStateEvent> onSent, EventHandler<WorkerStateEvent> onReplyComplete, EventHandler<WorkerStateEvent> onFailed){
    
        if(dataInterface != null){
    
            PipedOutputStream sendData = new PipedOutputStream();
            PipedOutputStream replyData = new PipedOutputStream();
        
            dataInterface.sendNote(ids, sendData, replyData, onFailed);
            
            byte[] objectBytes = noteBytesObject.get();

            
            readBroadcastReplyObjects(replyData, execService, onReplyComplete, onFailed);
            return true;
        }else{
            return false;
        }
        
    }


    public static void streamCopy(InputStream input, OutputStream output, 
                StreamProgressTracker progressTracker) throws IOException {
        byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
        int length = 0;
        
        while ((length = input.read(buffer)) != -1) {
            if (progressTracker != null && progressTracker.isCancelled()) {
                throw new IOException("Operation cancelled");
            }
            
            output.write(buffer, 0, length);
            
            if (progressTracker != null) {
                progressTracker.addBytesProcessed(length);
            }
        }
    }

    public static void pipedOutputDuplicate(PipedOutputStream pipedOutput, PipedOutputStream output1, PipedOutputStream output2, 
                StreamProgressTracker progressTracker) throws IOException {
        byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
        int length = 0;
        
        try(PipedInputStream input = new PipedInputStream(pipedOutput, PIPE_BUFFER_SIZE)){
            while ((length = input.read(buffer)) != -1) {
                if (progressTracker != null && progressTracker.isCancelled()) {
                    throw new IOException("Operation cancelled");
                }
                output1.write(buffer, 0, length);
                output2.write(buffer, 0, length);
                if (progressTracker != null) {
                    progressTracker.addBytesProcessed(length);
                }
            }
        }
    }



    public static void safeClose(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                // Log but don't throw - we're cleaning up
                System.err.println("Warning: Error closing resource: " + e.getMessage());
            }
        }
    }
}
