package io.netnotes.engine.core.system.control.containers;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.netnotes.engine.core.system.control.ServicesProcess;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * ContainerHandle - FlowProcess-based container control
 * 
 * Extends FlowProcess to properly integrate with system architecture:
 * - Uses request-reply pattern through reactive streams
 * - Supports message tracing and debugging
 * - Respects backpressure
 * - Can receive events from ContainerService
 * 
 * Usage:
 * <pre>
 * ContainerHandle handle = new ContainerHandle(containerId, containerServicePath);
 * registry.registerChild(ownerPath, handle);  // Register as child of INode
 * registry.startProcess(handle.getContextPath());
 * 
 * handle.show().thenRun(() -> System.out.println("Container shown"));
 * </pre>
 */
public class ContainerHandle extends FlowProcess {
    
    private final ContainerId containerId;
    private final ContextPath containerServicePath;
    private volatile boolean isDestroyed = false;
    
    /**
     * Constructor
     * 
     * @param containerId ID of the container to control
     * @param name name of the container
     * 
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
    }
    
    @Override
    public void onStart() {
        System.out.println("[ContainerHandle] Started for container: " + containerId);
    }
    
    @Override
    public void onStop() {
        System.out.println("[ContainerHandle] Stopped for container: " + containerId);
        isDestroyed = true;
    }
    
    /**
     * Handle incoming messages (container events from ContainerService)
     */
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        NoteBytesMap message = packet.getPayload().getAsNoteBytesMap();
        NoteBytesReadOnly cmd = message.getReadOnly("cmd");
        
        if (cmd == null) {
            System.err.println("[ContainerHandle] No cmd in message");
            return CompletableFuture.completedFuture(null);
        }
        
        // Handle container events
        if (cmd.equals(ContainerCommands.CONTAINER_CLOSED)) {
            handleContainerClosed(message);
        } else if (cmd.equals(ContainerCommands.CONTAINER_RESIZED)) {
            handleContainerResized(message);
        } else if (cmd.equals(ContainerCommands.CONTAINER_FOCUSED)) {
            handleContainerFocused(message);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        // Container handles don't use stream channels currently
        System.err.println("[ContainerHandle] Unexpected stream channel from: " + fromPath);
    }
    
    // ===== CONTAINER OPERATIONS =====
    
    /**
     * Update container properties
     */
    public CompletableFuture<Void> updateContainer(NoteBytesMap updates) {
        NoteBytesMap msg = ContainerProtocol.updateContainer(containerId, updates);
        return sendCommand(msg);
    }
    
    /**
     * Show container (unhide/restore)
     */
    public CompletableFuture<Void> show() {
        NoteBytesMap msg = ContainerProtocol.showContainer(containerId);
        return sendCommand(msg);
    }
    
    /**
     * Hide container (minimize)
     */
    public CompletableFuture<Void> hide() {
        NoteBytesMap msg = ContainerProtocol.hideContainer(containerId);
        return sendCommand(msg);
    }
    
    /**
     * Focus container (bring to front)
     */
    public CompletableFuture<Void> focus() {
        NoteBytesMap msg = ContainerProtocol.focusContainer(containerId);
        return sendCommand(msg);
    }
    
    /**
     * Maximize container
     */
    public CompletableFuture<Void> maximize() {
        NoteBytesMap msg = ContainerProtocol.maximizeContainer(containerId);
        return sendCommand(msg);
    }
    
    /**
     * Restore container (un-maximize)
     */
    public CompletableFuture<Void> restore() {
        NoteBytesMap msg = ContainerProtocol.restoreContainer(containerId);
        return sendCommand(msg);
    }
    
    /**
     * Destroy container
     */
    public CompletableFuture<Void> destroy() {
        if (isDestroyed) {
            return CompletableFuture.completedFuture(null);
        }
        
        NoteBytesMap msg = ContainerProtocol.destroyContainer(containerId);
        return sendCommand(msg)
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
        NoteBytesMap msg = ContainerProtocol.queryContainer(containerId);
        
        // Query can be slightly slower than commands - 1 second timeout
        return request(containerServicePath, msg.toNoteBytesReadOnly(), Duration.ofSeconds(1));
    }
    
    // ===== COMMAND SENDING =====
    
    /**
     * Send command using FlowProcess request-reply
     * Uses reactive streams properly!
     */
    private CompletableFuture<Void> sendCommand(NoteBytesMap command) {
        if (isDestroyed) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Container already destroyed")
            );
        }
        
        // Use FlowProcess's request() method - goes through reactive streams
        // UI commands should be fast - 500ms timeout
        return request(containerServicePath, command.toNoteBytesReadOnly(), Duration.ofMillis(500))
            .thenAccept(reply -> {
                // Validate acknowledgment and propagate errors
                NoteBytesMap response = reply.getPayload().getAsNoteBytesMap();
                NoteBytesReadOnly status = response.getReadOnly(Keys.STATUS);
                
                if (status != null && !status.equals(ProtocolMesssages.SUCCESS)) {
                    NoteBytes errorBytes = response.get(Keys.ERROR_CODE);
                    int errorCode = errorBytes != null
                        ? errorBytes.getAsInt() 
                        : 0;
                    String errorMsg = NoteMessaging.ErrorCodes.getMessage(errorCode);

                    String msg = "[ContainerHandle] Command failed: " + errorMsg;
                    System.err.println(msg);
                    throw new CompletionException(msg, new IOException(errorMsg));
                }
            });
    }
    
    // ===== EVENT HANDLERS =====
    
    /**
     * Handle container closed event (user clicked X)
     * 
     * Emits event to all subscribers (not parent).
     * The process that cares about this container should subscribe.
     */
    private void handleContainerClosed(NoteBytesMap event) {
        System.out.println("[ContainerHandle] Container closed: " + containerId);
        
        // Emit to all subscribers (broadcast pattern)
        NoteBytesMap notification = new NoteBytesMap();
        notification.put("event", "container_closed");
        notification.put("container_id", containerId.toNoteBytes());
        
        emit(notification);
        
        // Cleanup
        isDestroyed = true;
    }
    
    /**
     * Handle container resized event
     */
    private void handleContainerResized(NoteBytesMap event) {
        int width = event.get("width").getAsInt();
        int height = event.get("height").getAsInt();
        
        System.out.println("[ContainerHandle] Container resized: " + 
            containerId + " (" + width + "x" + height + ")");
        
        // Emit to all subscribers
        emit(event);
    }
    
    /**
     * Handle container focused event
     */
    private void handleContainerFocused(NoteBytesMap event) {
        System.out.println("[ContainerHandle] Container focused: " + containerId);
        
        // Emit to all subscribers
        emit(event);
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
}