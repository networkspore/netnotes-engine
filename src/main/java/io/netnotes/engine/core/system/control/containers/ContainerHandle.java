package io.netnotes.engine.core.system.control.containers;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.system.control.ServicesProcess;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * ContainerHandle - FlowProcess-based container control
 * 
 * Architecture (similar to ClaimedDevice):
 * - Has stream TO Container (for sending render commands)
 * - Writes commands directly to stream (no wrapping!)
 * - Receives events from ContainerService
 * 
 * Usage:
 * <pre>
 * ContainerHandle handle = new ContainerHandle(containerId, containerServicePath);
 * registry.registerChild(ownerPath, handle);
 * registry.startProcess(handle.getContextPath());
 * 
 * handle.show().thenRun(() -> Log.logMsg("Container shown"));
 * </pre>
 */
public class ContainerHandle extends FlowProcess {
    
    private final ContainerId containerId;
    private final ContextPath containerServicePath;
    private volatile boolean isDestroyed = false;
    
    // Stream TO Container (for render commands)
    private StreamChannel renderStream;
    private NoteBytesWriter renderWriter;

    private final HashMap<NoteBytesReadOnly, RoutedMessageExecutor> m_msgMap = new HashMap<>();
    /**
     * Constructor
     * 
     * @param containerId ID of the container to control
     * @param name name of the container
     */
    public ContainerHandle(ContainerId containerId, String name){
        this(containerId, name, ServicesProcess.CONTAINER_SERVICE_PATH);
    }

    /**
     * Constructor
     * 
     * @param containerId ID of the container to control
     * @param name name of the container
     * @param containerServicePath Path to ContainerService
     */
    public ContainerHandle(ContainerId containerId, String name, ContextPath containerServicePath) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.containerId = containerId;
        this.containerServicePath = containerServicePath;
        setupMsgMap();
    }


    

    private void setupMsgMap(){
        m_msgMap.put(ContainerCommands.CONTAINER_CLOSED, this::handleContainerClosed);
        m_msgMap.put(ContainerCommands.CONTAINER_RESIZED, this::handleContainerResized);
        m_msgMap.put(ContainerCommands.CONTAINER_FOCUSED, this::handleContainerFocused);
    }
    
    protected HashMap<NoteBytesReadOnly, RoutedMessageExecutor> getRoutedMsgMap(){
        return m_msgMap;
    }
    
    @Override
      public CompletableFuture<Void> run() {
        Log.logMsg("[ContainerHandle] Started for container: " + containerId);
        
        // Request render stream TO ContainerService (not container path!)
        requestStreamChannel(containerServicePath)
            .thenAccept(channel -> {
                Log.logMsg("[ContainerHandle] creating writer");
                this.renderStream = channel;
                this.renderWriter = new NoteBytesWriter(
                    channel.getQueuedOutputStream()
                );
                
                Log.logMsg("[ContainerHandle] Stream channel setup complete");
            })
            .exceptionally(ex -> {
                Log.logError("[ContainerHandle] Failed to setup render stream: " + ex.getMessage());
                return null;
            });
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void onStop() {
        Log.logMsg("[ContainerHandle] Stopped for container: " + containerId);
        isDestroyed = true;
        
        if (renderStream != null) {
            try {
                renderStream.close();
            } catch (IOException e) {
                Log.logError("[ContainerHandle] Error closing render stream: " + 
                    e.getMessage());
            }
        }
    }

    
    /**
     * Handle incoming messages (container events from ContainerService)
     */
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        NoteBytesMap message = packet.getPayload().getAsNoteBytesMap();
        NoteBytesReadOnly cmd = message.getReadOnly("cmd");
        
        if (cmd == null) {
            Log.logError("[ContainerHandle] No cmd in message");
            return CompletableFuture.completedFuture(null);
        }
        
        RoutedMessageExecutor msgExec = m_msgMap.get(cmd);
        
        if(msgExec != null){
            return msgExec.execute(message, packet);
        }

        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {

       throw new IllegalStateException("ContainerHandle doesn't receive streams currently");
    }
    
    // ===== CONTAINER OPERATIONS =====
    // These now send directly through ContainerService (not through stream)
    
    /**
     * Show container (unhide/restore)
     */
    public CompletableFuture<Void> show() {
        NoteBytesMap msg = ContainerCommands.showContainer(containerId);
        return sendToService(msg);
    }
    
    /**
     * Hide container (minimize)
     */
    public CompletableFuture<Void> hide() {
        NoteBytesMap msg = ContainerCommands.hideContainer(containerId);
        return sendToService(msg);
    }
    
    /**
     * Focus container (bring to front)
     */
    public CompletableFuture<Void> focus() {
        NoteBytesMap msg = ContainerCommands.focusContainer(containerId);
        return sendToService(msg);
    }
    
    /**
     * Maximize container
     */
    public CompletableFuture<Void> maximize() {
        NoteBytesMap msg = ContainerCommands.maximizeContainer(containerId);
        return sendToService(msg);
    }
    
    /**
     * Restore container (un-maximize)
     */
    public CompletableFuture<Void> restore() {
        NoteBytesMap msg = ContainerCommands.restoreContainer(containerId);
        return sendToService(msg);
    }
    
    /**
     * Destroy container
     */
    public CompletableFuture<Void> destroy() {
        if (isDestroyed) {
            return CompletableFuture.completedFuture(null);
        }
        
        NoteBytesMap msg = ContainerCommands.destroyContainer(containerId);
        return sendToService(msg)
            .thenRun(() -> {
                // Self-cleanup after destroy
                if (registry != null) {
                    registry.unregisterProcess(contextPath);
                }
            });
    }
    
    /**
     * Query container info
     */
    public CompletableFuture<RoutedPacket> queryContainer() {
        NoteBytesMap msg = ContainerCommands.queryContainer(containerId);
        return request(containerServicePath, msg.toNoteBytesReadOnly(), Duration.ofSeconds(1));
    }
    
    // ===== RENDER COMMAND SENDING =====
    
    /**
     * Send render command directly to Container via stream
     * NO WRAPPING - just write the command!
     * 
     * This is used by subclasses like TerminalContainerHandle
     */
    protected CompletableFuture<Void> sendRenderCommand(NoteBytesMap command) {

        if (isDestroyed) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Container already destroyed")
            );
        }
        
        // Stream must be ready at this point
        if (renderWriter == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Render stream not initialized")
            );
        }
        
        try{
            renderWriter.write(command.toNoteBytes());
            return CompletableFuture.completedFuture(null);
        }catch(IOException ex){
            return CompletableFuture.failedFuture(ex);
        }
    
    }

   
    
    // ===== SERVICE COMMUNICATION =====
    
    /**
     * Send lifecycle command to ContainerService
     * (show/hide/focus/destroy - not render commands)
     */
    private CompletableFuture<Void> sendToService(NoteBytesMap command) {
        if (isDestroyed) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Container already destroyed")
            );
        }
        
        return request(containerServicePath, command.toNoteBytesReadOnly(), 
                Duration.ofMillis(500))
            .thenAccept(reply -> {
                NoteBytesMap response = reply.getPayload().getAsNoteBytesMap();
                NoteBytesReadOnly status = response.getReadOnly(Keys.STATUS);
                
                if (status != null && !status.equals(ProtocolMesssages.SUCCESS)) {
                   
                    String errorMsg = ProtocolObjects.getErrMsg(response);
                    throw new RuntimeException("[ContainerHandle] Command failed: " + errorMsg);
                }
            });
    }
    
    // ===== EVENT HANDLERS =====
    
    /**
     * Handle container closed event (user clicked X)
     */
    protected CompletableFuture<Void> handleContainerClosed(NoteBytesMap message, RoutedPacket packet) {
        Log.logMsg("[ContainerHandle] Container closed: " + containerId);
        
        // Emit to all subscribers
        NoteBytesMap notification = new NoteBytesMap();
        notification.put("event", "container_closed");
        notification.put("container_id", containerId.toNoteBytes());
        
        emit(notification);
        
        // Cleanup
        isDestroyed = true;

        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Handle container resized event
     */
    protected CompletableFuture<Void> handleContainerResized(NoteBytesMap message, RoutedPacket packet) {
        NoteBytes widthBytes = message.get(Keys.WIDTH);
        NoteBytes heightBytes = message.get(Keys.HEIGHT);

        if(widthBytes != null && heightBytes != null){
            int width = widthBytes.getAsInt();
            int height = heightBytes.getAsInt();
           
            emit(message);
            
            return handleContainerResized(width, height);
        }
        return CompletableFuture.failedFuture(new IllegalArgumentException("width and height required"));
    }

    protected CompletableFuture<Void> handleContainerResized(int changedWidth, int changedHeight){
        Log.logMsg("[ContainerHandle] Container resized: " + 
            containerId + " (" + changedWidth + "x" + changedHeight + ")");
        return CompletableFuture.completedFuture(null);
    }

   
    
    /**
     * Handle container focused event
     */
    protected CompletableFuture<Void> handleContainerFocused(NoteBytesMap message, RoutedPacket packet) {
        Log.logMsg("[ContainerHandle] Container focused: " + containerId);
        emit(message);
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== GETTERS =====
    
    public ContainerId getId() {
        return containerId;
    }
    
    public ContextPath getContainerServicePath() {
        return containerServicePath;
    }
    
    public boolean isDestroyed() {
        return isDestroyed;
    }

 
    public boolean isRenderStreamReady() {
        return renderStream != null && 
               renderStream.getReadyFuture().isDone() &&
               !renderStream.getReadyFuture().isCompletedExceptionally();
    }

    public CompletableFuture<Void> waitUntilReady() {
        if (renderStream == null) {
            // Stream hasn't been created yet, wait for it
            return CompletableFuture.runAsync(() -> {
                while (renderStream == null && !isDestroyed) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
                if (isDestroyed) {
                    throw new IllegalStateException("Container destroyed before ready");
                }
            }).thenCompose(v -> renderStream.getReadyFuture());
        }
        
        // Stream exists, just return its ready future
        return renderStream.getReadyFuture();
    }
}