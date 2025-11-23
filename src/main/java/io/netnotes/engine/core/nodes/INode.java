package io.netnotes.engine.core.nodes;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import io.netnotes.engine.core.AppDataInterface;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
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
    ContextPath getNodeId();
    
    /**
     * Context path in the FlowProcess network
     * Typically: /system/controller/nodes/{nodeId}
     */
    ContextPath getContextPath();
    
    // ===== LIFECYCLE =====
    
    /**
     * Initialize Node - returns when ready to receive messages
     * After this completes, Node can handle both channels
     */
    CompletableFuture<Void> initialize(AppDataInterface appInterface);
    
    /**
     * Set controller interface for Node-to-Node communication
     */
    
    /**
     * Shutdown Node - cleanup both channels
     */
    CompletableFuture<Void> shutdown();
    
    /**
     * Check if Node is active and ready
     */
    boolean isActive();
    
    /**
     * Check if Node is alive (FlowProcess still running)
     */
    boolean isAlive();
    
    // ===== FLOWPROCESS CHANNEL (Control & Negotiation) =====
    
    /**
     * Handle incoming FlowProcess messages
     * 
     * Use cases:
     * - Negotiation: "Can I send you data?"
     * - Configuration: "Change your settings"
     * - Queries: "What's your status?"
     * - Notifications: "Something happened"
     * 
     * Returns CompletableFuture for async handling
     */
    CompletableFuture<Void> handleFlowMessage(RoutedPacket packet);
    
    /**
     * Emit event to FlowProcess subscribers
     * 
     * Use cases:
     * - Status updates: "I'm busy"
     * - Progress: "50% complete"
     * - Errors: "Something failed"
     * - Notifications: "Data ready for pickup"
     */
    void emitFlowEvent(RoutedPacket event);
    
    /**
     * Subscribe to FlowProcess publisher
     * 
     * Use cases:
     * - Listen to input sources (keyboard, mouse)
     * - Monitor other nodes
     * - Receive system events
     */
    Flow.Subscriber<RoutedPacket> getFlowSubscriber();
    
    /**
     * Request-reply via FlowProcess
     * 
     * Use cases:
     * - Query capabilities: "What can you do?"
     * - Request permission: "Can I connect?"
     * - Configuration queries: "What's your config?"
     */
    CompletableFuture<RoutedPacket> requestViaFlow(
        ContextPath targetPath,
        NoteBytesReadOnly payload,
        Duration timeout
    );
    
    /**
     * Reply to FlowProcess request
     */
    void replyViaFlow(RoutedPacket originalRequest, NoteBytesReadOnly payload);
    
    /**
     * Subscribe this node to another FlowProcess source
     */
    void subscribeToFlow(FlowProcess source);
    
    /**
     * Get number of FlowProcess subscribers
     * Indicates if anyone is listening to this node's events
     */
    int getFlowSubscriberCount();
    
    /**
     * Check if node has FlowProcess subscribers
     */
    default boolean hasFlowSubscribers() {
        return getFlowSubscriberCount() > 0;
    }
    
    // ===== DIRECT STREAM CHANNEL (Data Transfer) =====
    
    /**
     * Receive direct stream message
     * 
     * Use cases:
     * - Large file transfers
     * - Database query results
     * - Continuous data streams
     * - Binary data that shouldn't be packetized
     * 
     * This is a DEDICATED connection - doesn't interfere with FlowProcess traffic
     */
    void handleStreamChannel(StreamChannel channel, ContextPath devicePath);
    
    // ===== OPTIONAL BACKGROUND TASKS =====
    
    /**
     * Start background tasks (heartbeat, cleanup, etc.)
     * 
     * Called when Node has FlowProcess subscribers.
     * Return immediately if no background work needed.
     */
    CompletableFuture<Void> runBackgroundTasks();
    
    /**
     * Check if node has background tasks to run
     */
    default boolean hasBackgroundTasks() {
        return false;
    }
}