package io.netnotes.engine.core.system.control;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import io.netnotes.engine.io.input.events.ExecutorConsumer;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;

/**
 * ProgressTrackingProcess - Consumes progress stream and updates UI
 * 
 * Responsibilities:
 * - Read progress messages from stream channel
 * - Update UI renderer with progress
 * - Track file completion for recovery
 * - Write recovery log
 */
public class StreamReader {
    private final PipedOutputStream outputStream;
    private final AsyncNoteBytesWriter writer;

    private Map<String, ExecutorConsumer<NoteBytes>> m_consumerMap = new ConcurrentHashMap<>();

    private CompletableFuture<Void> streamFuture;
 
    public StreamReader(String name) {
        outputStream = new PipedOutputStream();
        this.writer = new AsyncNoteBytesWriter(outputStream);

    }


    public AsyncNoteBytesWriter getWriter(){
        return writer;
    }

    private void emitEvent(NoteBytes nextNoteBytes){
        m_consumerMap.forEach((k,consumer)->consumer.accept(nextNoteBytes));
    }
    
    public void addEventConsumer(String id, ExecutorConsumer<NoteBytes> eventConsumer){
        m_consumerMap.computeIfAbsent(id, (k)->eventConsumer);
    }
    
    public ExecutorConsumer<NoteBytes> removeEventConsumer(String id){
        return m_consumerMap.remove(id);
    }

    public ExecutorConsumer<NoteBytes> getEventConsumer(String id){
        return m_consumerMap.get(id);
    }

    public void start(){
        streamFuture = intiialize();
    }

    public CompletableFuture<Void> getStreamFuture(){
        return streamFuture;
    }


    private CompletableFuture<Void> intiialize() {
        return CompletableFuture.runAsync(() -> {
            try(NoteBytesReader reader = new NoteBytesReader(new PipedInputStream(outputStream)) ) {
          
                NoteBytes nextBytes = reader.nextNoteBytes();
                
                while (nextBytes != null) {
                   
                    emitEvent(nextBytes);

                    nextBytes = reader.nextNoteBytesReadOnly();
                }
                

                System.out.println("[ProgressTracking] Progress stream complete");

                
            } catch (IOException e) {
                System.err.println("[ProgressTracking] Progress stream error: " + 
                    e.getMessage());
            }
        });
    }

    
    /*
    private void updateUI(NoteBytesMap progressMsg) {
        // Throttle UI updates
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastUpdateTime) < UPDATE_INTERVAL_MS) {
            return; // Skip this update
        }
        lastUpdateTime = currentTime;
        
        try {
            // Get message type
            NoteBytes cmd = progressMsg.get(Keys.CMD);
            if (cmd == null) return;
            
  
            MessageExecutor msgExec = m_execMsgMap.get(cmd);

            if(msgExec != null){
                msgExec.execute(progressMsg);
            }

            
        } catch (Exception e) {
            System.err.println("[ProgressTracking] Error updating UI: " + e.getMessage());
        }
    } 
    
    private void handleProgressMessage(NoteBytesMap progressMsg) {
        long completed = ProgressMessage.getCompleted(progressMsg);
        long total = ProgressMessage.getTotal(progressMsg);
        double percentage = ProgressMessage.getPercentage(progressMsg);
        String message = ProgressMessage.getMessage(progressMsg);
        
        if (percentage >= 0) {
            String displayMsg = String.format("%s (%.1f%%)", message, percentage);
            containerHandle.showProgress(displayMsg, (int) percentage);
        } else {
            //TODO: print in specific location
            containerHandle.println(message);
        }
        
        System.out.println(String.format(
            "[ProgressTracking] Progress: %d/%d (%.1f%%) - %s",
            completed, total, percentage, message
        ));
    }
    

    
   
   */
    

}