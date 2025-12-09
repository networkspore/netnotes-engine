package io.netnotes.engine.core.system.control.containers;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * ContainerHandle - Lightweight wrapper using owner's FlowProcess
 * 
 * Instead of being a FlowProcess itself, it uses the owner's request/emit methods.
 * This avoids creating a full process for each container handle.
 * 
 * Usage:
 * <pre>
 * // In INode:
 * ContainerHandle handle = new ContainerHandle(
 *     containerId, 
 *     containerServicePath,
 *     this  // Pass the INode itself
 * );
 * 
 * handle.show().thenRun(() -> System.out.println("Container shown"));
 * </pre>
 */
public class ContainerHandleWrapper {
    
    private final ContainerId containerId;
    private final ContextPath containerServicePath;
    private final FlowProcess owner;
    private volatile boolean isDestroyed = false;
    
    /**
     * Constructor
     * 
     * @param containerId ID of the container to control
     * @param containerServicePath Path to ContainerService
     * @param owner The FlowProcess that owns this handle (usually an INode)
     */
    public ContainerHandleWrapper(
        ContainerId containerId,
        ContextPath containerServicePath,
        FlowProcess owner
    ) {
        this.containerId = containerId;
        this.containerServicePath = containerServicePath;
        this.owner = owner;
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
            .thenRun(() -> isDestroyed = true);
    }
    
    /**
     * Query container info
     */
    public CompletableFuture<NoteBytesMap> getInfo() {
        NoteBytesMap msg = ContainerProtocol.queryContainer(containerId);
        
        return sendRequest(msg)
            .thenApply(reply -> reply.getPayload().getAsNoteBytesMap());
    }
    
    // ===== COMMAND SENDING (Uses owner's FlowProcess methods) =====
    
    /**
     * Send command using owner's request() method
     * Properly uses reactive streams!
     */
    private CompletableFuture<Void> sendCommand(NoteBytesMap command) {
        if (isDestroyed) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Container already destroyed")
            );
        }
        
        // Use owner's request() method - goes through reactive streams
        // UI commands should be fast - 500ms timeout
        return owner.request(
            containerServicePath, 
            command.readOnlyObject(), 
            Duration.ofMillis(500)
        )
        .thenAccept(reply -> {
            // Validate acknowledgment and propagate errors
            NoteBytesMap response = reply.getPayload().getAsNoteBytesMap();
            if (response.has("status")) {
                NoteBytes statusBytes = response.get(ProtocolMesssages.STATUS);
                
                if (!statusBytes.equals(ProtocolMesssages.SUCCESS)) {
                    NoteBytes errorBytes = response.get(Keys.ERROR_CODE);
                    int errorCode = errorBytes != null
                        ? errorBytes.getAsInt() 
                        : 0;
                    String errorMsg = NoteMessaging.ErrorCodes.getMessage(errorCode);

                    String msg = "[ContainerHandle] Command failed: " + errorMsg;
                    System.err.println(msg);
                    throw new CompletionException(msg, new IOException(errorMsg));
                }
            }
        });
    }
    
    /**
     * Send request using owner's request() method (with reply)
     */
    private CompletableFuture<RoutedPacket> sendRequest(NoteBytesMap command) {
        if (isDestroyed) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Container already destroyed")
            );
        }
        
        // Query can be slightly slower than commands - 1 second timeout
        return owner.request(
            containerServicePath,
            command.readOnlyObject(),
            Duration.ofSeconds(1)
        );
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
    
    public FlowProcess getOwner() {
        return owner;
    }
}