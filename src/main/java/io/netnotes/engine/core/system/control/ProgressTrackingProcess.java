package io.netnotes.engine.core.system.control;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.netnotes.engine.core.system.control.ui.UIProtocol;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
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
public class ProgressTrackingProcess extends FlowProcess {
    
    private final UIRenderer uiRenderer;

    
    // Tracking
    private final List<String> completedFiles = new CopyOnWriteArrayList<>();
    private final List<String> failedFiles = new CopyOnWriteArrayList<>();
    
    // UI throttling
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 100;
    private final Map<NoteBytesReadOnly, MessageExecutor> m_execMsgMap = new ConcurrentHashMap<>();
    
    public ProgressTrackingProcess(String name, UIRenderer uiRenderer) {
        super(name, ProcessType.SINK);
        this.uiRenderer = uiRenderer;

        setupMessageMap();
    }

    private void setupMessageMap(){

        m_execMsgMap.put(ProtocolMesssages.PROGRESS, this::handleProgressMessage);
        m_execMsgMap.put(ProtocolMesssages.ERROR, this::handleErrorMessage);
        m_execMsgMap.put(ProtocolMesssages.INFO, this::handleInfoMessage);
        m_execMsgMap.put(ProtocolMesssages.UPDATED, this::handleUpdatedMessage);

    }
    
    @Override
    public CompletableFuture<Void> run() {
        
        return getCompletionFuture();
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        System.out.println("[ProgressTracking] Receiving progress stream from: " + fromPath);
        
        channel.startReceiving(input -> {
            try (NoteBytesReader reader = new NoteBytesReader(input)) {
                NoteBytes nextBytes = reader.nextNoteBytes();
                
                while (nextBytes != null) {
                    // Parse progress message
                    NoteBytesMap progressMsg = nextBytes.getAsNoteBytesMap();
                    
                    
                    // Update UI (throttled)
                    updateUI(progressMsg);
                    
                    nextBytes = reader.nextNoteBytesReadOnly();
                }
                

                System.out.println("[ProgressTracking] Progress stream complete");
                complete();
                
            } catch (IOException e) {
                System.err.println("[ProgressTracking] Progress stream error: " + 
                    e.getMessage());
            }
        });
        
        // Signal ready
        channel.getReadyFuture().complete(null);
    }
    
    
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
            }else{
                String msg = ProgressMessage.getMessage(progressMsg);
                if (msg != null && !msg.isEmpty()) {
                    uiRenderer.render(UIProtocol.showMessage(msg));
                }
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
            uiRenderer.render(UIProtocol.showProgress(displayMsg, (int) percentage));
        } else {
            uiRenderer.render(UIProtocol.showMessage(message));
        }
        
        System.out.println(String.format(
            "[ProgressTracking] Progress: %d/%d (%.1f%%) - %s",
            completed, total, percentage, message
        ));
    }
    
    private void handleErrorMessage(NoteBytesMap errorMsg) {
        String message = ProgressMessage.getMessage(errorMsg);
        
        NoteBytes exception = errorMsg.get(Keys.EXCEPTION);
        if (exception != null) {
            try{
                Object e = exception.getAsObject();

                if (e instanceof Throwable t) {
                    message += "\n" + t.getMessage();
                }
            }catch(IOException ex){
                message += "\n" + "Error serialization error";
            }catch(ClassNotFoundException cnfE){
                message += "\n" + "Error class not found";
            }
        }
        
        System.err.println("[ProgressTracking] Error: " + message);
    }
    
    private void handleInfoMessage(NoteBytesMap infoMsg) {
        String message = ProgressMessage.getMessage(infoMsg);
        
        System.out.println("[ProgressTracking] Info: " + message);
        
        // Show important info messages
        if (message.contains("Finding file paths") || 
            message.contains("Created new key") ||
            message.contains("Opening file path ledger")) {
            uiRenderer.render(UIProtocol.showMessage(message));
        }
    }
    
    private void handleUpdatedMessage(NoteBytesMap updateMsg) {
        long completed = ProgressMessage.getCompleted(updateMsg);
        long total = ProgressMessage.getTotal(updateMsg);
        double percentage = ProgressMessage.getPercentage(updateMsg);
        
        if (percentage >= 0) {
            String displayMsg = String.format(
                "Re-encrypting files: %d/%d (%.1f%%)", 
                completed, total, percentage);
            uiRenderer.render(UIProtocol.showProgress(displayMsg, (int) percentage));
        }
    }
    
   
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Not used - we use stream channel
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== GETTERS =====
    
    public List<String> getCompletedFiles() {
        return new ArrayList<>(completedFiles);
    }
    
    public List<String> getFailedFiles() {
        return new ArrayList<>(failedFiles);
    }
    
    public boolean hasErrors() {
        return !failedFiles.isEmpty();
    }
}