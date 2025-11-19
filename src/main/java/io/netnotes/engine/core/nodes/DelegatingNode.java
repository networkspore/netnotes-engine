package io.netnotes.engine.core.nodes;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import io.netnotes.engine.core.AppDataInterface;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * DelegatingNode - Implements INode by delegating to an internal FlowProcess
 * 
 * Use this if your OSGi plugin must extend another class (like BundleActivator)
 * and can't extend BaseNode.
 * 
 * This maintains the same INode interface while using delegation instead of inheritance.
 */
public abstract class DelegatingNode implements INode {
    
    private final NoteBytesReadOnly nodeId;
    private final InternalFlowProcess flowProcess;
    private NodeControllerInterface nodeController;
    private AppDataInterface appData;
    private volatile boolean active = false;
    
    protected DelegatingNode(NoteBytesReadOnly nodeId) {
        this.nodeId = nodeId;
        this.flowProcess = new InternalFlowProcess(this);
    }
    
    protected DelegatingNode(String nodeId) {
        this(new NoteBytesReadOnly(nodeId));
    }
    
    // ===== INode IDENTITY =====
    
    @Override
    public NoteBytesReadOnly getNodeId() {
        return nodeId;
    }
    
    @Override
    public ContextPath getContextPath() {
        return flowProcess.getContextPath();
    }
    
    // ===== INode LIFECYCLE =====
    
    @Override
    public final CompletableFuture<Void> initialize(AppDataInterface appInterface) {
        this.appData = appInterface;
        return onInitialize()
            .thenRun(() -> this.active = true);
    }
    
    @Override
    public void setNodeControllerInterface(NodeControllerInterface controller) {
        this.nodeController = controller;
    }
    
    @Override
    public boolean isActive() {
        return active && flowProcess.isAlive();
    }
    
    @Override
    public boolean isAlive() {
        return flowProcess.isAlive();
    }
    
    @Override
    public CompletableFuture<Void> shutdown() {
        active = false;
        return onShutdown()
            .thenRun(flowProcess::complete);
    }
    
    // ===== INode FLOWPROCESS CHANNEL =====
    
    @Override
    public CompletableFuture<Void> handleFlowMessage(RoutedPacket packet) {
        // Override in subclass for control messages
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void emitFlowEvent(RoutedPacket event) {
        flowProcess.emit(event);
    }
    
    @Override
    public Flow.Subscriber<RoutedPacket> getFlowSubscriber() {
        return flowProcess.getSubscriber();
    }
    
    @Override
    public CompletableFuture<RoutedPacket> requestViaFlow(
            ContextPath targetPath,
            NoteBytesReadOnly payload,
            Duration timeout) {
        return flowProcess.request(targetPath, payload, timeout);
    }
    
    @Override
    public void replyViaFlow(RoutedPacket originalRequest, NoteBytesReadOnly payload) {
        flowProcess.reply(originalRequest, payload);
    }
    
    @Override
    public void subscribeToFlow(FlowProcess source) {
        source.subscribe(flowProcess.getSubscriber());
    }
    
    @Override
    public int getFlowSubscriberCount() {
        return flowProcess.getSubscriberCount();
    }
    
    // ===== INode STREAM CHANNEL =====
    
    @Override
    public abstract CompletableFuture<Void> receiveRawMessage(
        PipedOutputStream messageStream,
        PipedOutputStream replyStream
    ) throws IOException;
    
    // ===== INode BACKGROUND TASKS =====
    
    @Override
    public CompletableFuture<Void> runBackgroundTasks() {
        // Override if node has background work
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public boolean hasBackgroundTasks() {
        return false;
    }
    
    // ===== LIFECYCLE HOOKS =====
    
    protected CompletableFuture<Void> onInitialize() {
        return CompletableFuture.completedFuture(null);
    }
    
    protected CompletableFuture<Void> onShutdown() {
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== HELPERS =====
    
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
    
    protected NodeControllerInterface getNodeController() {
        return nodeController;
    }
    
    protected AppDataInterface getAppData() {
        return appData;
    }
    
    /**
     * Get internal FlowProcess for registration
     */
    public FlowProcess getInternalFlowProcess() {
        return flowProcess;
    }
    
    // ===== INTERNAL FLOWPROCESS =====
    
    private static class InternalFlowProcess extends FlowProcess {
        private final DelegatingNode parent;
        
        InternalFlowProcess(DelegatingNode parent) {
            super(ProcessType.BIDIRECTIONAL);
            this.parent = parent;
        }
        
        @Override
        public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
            return parent.handleFlowMessage(packet);
        }
        
        @Override
        public CompletableFuture<Void> run() {
            return parent.runBackgroundTasks();
        }
    }
}