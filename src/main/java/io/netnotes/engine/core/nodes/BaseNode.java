package io.netnotes.engine.core.nodes;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import io.netnotes.engine.core.AppDataInterface;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;


/**
 * BaseNode - Extends FlowProcess, implements INode
 * 
 * This is the RECOMMENDED way to implement INode:
 * - Inherit all FlowProcess functionality
 * - Automatic integration with FlowProcessRegistry
 * - Simple to use, hard to misuse
 * 
 * Most nodes should extend this.
 */
public abstract class BaseNode extends FlowProcess implements INode {
    
    private final NoteBytesReadOnly nodeId;
    private NodeControllerInterface nodeController;
    private AppDataInterface appData;
    private volatile boolean nodeActive = false;
    
    protected BaseNode(NoteBytesReadOnly nodeId) {
        super(ProcessType.BIDIRECTIONAL);
        this.nodeId = nodeId;
    }
    
    protected BaseNode(String nodeId) {
        this(new NoteBytesReadOnly(nodeId));
    }
    
    // ===== INode IDENTITY =====
    
    @Override
    public NoteBytesReadOnly getNodeId() {
        return nodeId;
    }
    
    // getContextPath() inherited from FlowProcess
    
    // ===== INode LIFECYCLE =====
    
    @Override
    public final CompletableFuture<Void> initialize(AppDataInterface appInterface) {
        this.appData = appInterface;
        return onInitialize()
            .thenRun(() -> this.nodeActive = true);
    }
    
    @Override
    public void setNodeControllerInterface(NodeControllerInterface controller) {
        this.nodeController = controller;
    }
    
    @Override
    public boolean isActive() {
        return nodeActive && isAlive();
    }
    
    // isAlive() inherited from FlowProcess
    
    @Override
    public final CompletableFuture<Void> shutdown() {
        nodeActive = false;
        return onShutdown()
            .thenRun(this::complete);
    }
    
    // ===== INode FLOWPROCESS CHANNEL =====
    
    @Override
    public CompletableFuture<Void> handleFlowMessage(RoutedPacket packet) {
        return handleMessage(packet); // Delegate to FlowProcess method
    }
    
    @Override
    public void emitFlowEvent(RoutedPacket event) {
        emit(event); // Delegate to FlowProcess method
    }
    
    @Override
    public Flow.Subscriber<RoutedPacket> getFlowSubscriber() {
        return getSubscriber(); // Inherited from FlowProcess
    }
    
    @Override
    public CompletableFuture<RoutedPacket> requestViaFlow(
            ContextPath targetPath,
            NoteBytesReadOnly payload,
            Duration timeout) {
        return request(targetPath, payload, timeout); // Inherited from FlowProcess
    }
    
    @Override
    public void replyViaFlow(RoutedPacket originalRequest, NoteBytesReadOnly payload) {
        reply(originalRequest, payload); // Inherited from FlowProcess
    }
    
    @Override
    public void subscribeToFlow(FlowProcess source) {
        source.subscribe(getSubscriber());
    }
    
    @Override
    public int getFlowSubscriberCount() {
        return getSubscriberCount(); // Inherited from FlowProcess
    }
    
    // ===== INode STREAM CHANNEL =====
    
    /**
     * MUST IMPLEMENT: Handle direct stream messages
     */
    @Override
    public abstract CompletableFuture<Void> receiveRawMessage(
        PipedOutputStream messageStream,
        PipedOutputStream replyStream
    ) throws IOException;
    
    // ===== INode BACKGROUND TASKS =====
    
    @Override
    public CompletableFuture<Void> runBackgroundTasks() {
        return run(); // Delegate to FlowProcess run()
    }
    
    @Override
    public boolean hasBackgroundTasks() {
        // Override if node has background work
        return false;
    }
    
    // ===== FLOWPROCESS OVERRIDES =====
    
    /**
     * Handle FlowProcess messages
     * Override for control messages, negotiation, events
     */
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Default: no-op
        // Override to handle control messages
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Background tasks (heartbeat, cleanup)
     * Override if node needs to run periodic tasks
     */
    @Override
    public CompletableFuture<Void> run() {
        // Default: no background tasks
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== LIFECYCLE HOOKS =====
    
    /**
     * Initialize hook - override for setup logic
     */
    protected CompletableFuture<Void> onInitialize() {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Shutdown hook - override for cleanup
     */
    protected CompletableFuture<Void> onShutdown() {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void onStart() {
        super.onStart();
        System.out.println("Node started: " + nodeId + " at " + getContextPath());
    }
    
    @Override
    public void onStop() {
        super.onStop();
        System.out.println("Node stopped: " + nodeId);
    }
    
    // ===== CONVENIENCE HELPERS =====
    
    /**
     * Send to another Node via direct stream
     */
    protected CompletableFuture<Void> sendStreamToNode(
            NoteBytesReadOnly targetNodeId,
            PipedOutputStream messageStream,
            PipedOutputStream replyStream) {
        
        if (nodeController == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("NodeController not set")
            );
        }
        
        return nodeController.sendMessage(targetNodeId, messageStream, replyStream);
    }
    
    /**
     * Emit simple event (convenience)
     */
    protected void emitEvent(String eventType, NoteBytesMap data) {
        emitFlowEvent(RoutedPacket.create(getContextPath(), data.getNoteBytesObject()));
    }
    
    protected NodeControllerInterface getNodeController() {
        return nodeController;
    }
    
    protected AppDataInterface getAppData() {
        return appData;
    }
    
    @Override
    public String toString() {
        return String.format(
            "Node{id=%s, path=%s, active=%s, alive=%s, subscribers=%d}",
            nodeId, getContextPath(), nodeActive, isAlive(), getFlowSubscriberCount()
        );
    }
}