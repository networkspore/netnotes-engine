package io.netnotes.engine.core.system.control.nodes;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import io.netnotes.engine.core.NoteFileServiceInterface;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;


/**
 * INode - Interface for Node communication
 * 
 * OSGi plugins implement this interface.
 * Provides BOTH FlowProcess methods AND direct stream methods.
 * 
 * NodeController stores all nodes as INode references.
 */
public interface INode {
    
    // ===== IDENTITY =====
    
    /**
     * Unique Node identifier
     */
    NoteBytesReadOnly getNodeId();
    
    /**
     * Context path in the FlowProcess network
     * Typically: /system/nodes/{nodeId}
     */
    ContextPath getContextPath();
    
    // ===== LIFECYCLE =====
    
    /**
     * Initialize Node with interfaces for both capabilities
     * 
     * @param nodeDataInterface File access 
     * @param processInterface Process network access 
     */
    CompletableFuture<Void> initialize(
        NoteFileServiceInterface nodeDataInterfacem,
        ProcessRegistryInterface processInterface
    );
    
    /**
     * Shutdown Node - cleanup both channels
     */
    CompletableFuture<Void> shutdown();
    
    /**
     * Check if Node is active and ready
     * - Is it accepting work?
     * - Is it enabled?
     * - Has it passed health checks?
     * - Is it administratively marked active?
     */
    boolean isActive();
    
    /**
     * This is a liveness check:
     * - Not crashed
     * - Not stopped
     * - Still executing or at least not terminated
     */
    boolean isAlive();
    
}