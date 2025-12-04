package io.netnotes.engine.core.system.control.containers;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * ContainerHandle - Convenience wrapper for container operations
 * 
 * Just wraps the message-sending pattern so INodes don't have to
 * manually build protocol messages every time.
 */
public class ContainerHandle {
    private final ContainerId containerId;
    private final ContextPath containerServicePath;
    private final ProcessRegistryInterface processInterface;
    
    public ContainerHandle(
        ContainerId containerId,
        ContextPath containerServicePath,
        ProcessRegistryInterface processInterface
    ) {
        this.containerId = containerId;
        this.containerServicePath = containerServicePath;
        this.processInterface = processInterface;
    }
    
    // ===== CONVENIENCE METHODS =====
    
    
    public CompletableFuture<Void> updateContainer(NoteBytesMap updates) {
        NoteBytesMap msg = ContainerProtocol.updateContainer(containerId, updates);
        return sendCommand(msg);
    }
    
    public CompletableFuture<Void> show() {
        NoteBytesMap msg = ContainerProtocol.showContainer(containerId);
        return sendCommand(msg);
    }
    
    public CompletableFuture<Void> hide() {
        NoteBytesMap msg = ContainerProtocol.hideContainer(containerId);
        return sendCommand(msg);
    }
    
    public CompletableFuture<Void> destroy() {
        NoteBytesMap msg = ContainerProtocol.destroyContainer(containerId);
        return sendCommand(msg);
    }
    
    public CompletableFuture<RoutedPacket> getInfo() {
        NoteBytesMap msg = ContainerProtocol.queryContainer(containerId);
        return processInterface.request(containerServicePath, msg.readOnlyObject(), Duration.ofSeconds(5));
    }
    
    // ===== HELPERS =====
    // TODO: handle ack?
    private CompletableFuture<Void> sendCommand(NoteBytesMap command) {
         return processInterface.request(containerServicePath, command.readOnlyObject(), Duration.ofSeconds(5))
            .thenApply(v -> null);
    }
    
    public ContainerId getId() {
        return containerId;
    }
}
