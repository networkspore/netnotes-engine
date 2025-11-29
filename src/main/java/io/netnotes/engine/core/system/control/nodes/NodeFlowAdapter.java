package io.netnotes.engine.core.system.control.nodes;


/**
 * NodeFlowAdapter - Adapts INode to FlowProcess interface
 * 
 * This bridges the gap between:
 * - FlowProcess network (ProcessRegistry, routing)
 * - INode implementation (plugin/OSGi bundle)
 * 
 * Responsibilities:
 * - Register node in ProcessRegistry
 * - Route messages to/from node
 * - Handle stream channel requests
 * - Track subscribers
 * - Enforce permissions
 */
class NodeFlowAdapter extends io.netnotes.engine.io.process.FlowProcess {
    
    private final INode node;
    private final io.netnotes.engine.io.ContextPath nodePath;
    private final NodeController controller;
    
    public NodeFlowAdapter(
            INode node,
            io.netnotes.engine.io.ContextPath nodePath,
            NodeController controller) {
        
        super(ProcessType.BIDIRECTIONAL);
        this.node = node;
        this.nodePath = nodePath;
        this.controller = controller;
        this.contextPath = nodePath;
    }
    
    @Override
    public java.util.concurrent.CompletableFuture<Void> run() {
        // Node is already initialized by NodeController
        // This is just a network adapter
        return getCompletionFuture();
    }
    
    @Override
    public java.util.concurrent.CompletableFuture<Void> handleMessage(
            io.netnotes.engine.io.RoutedPacket packet) {
        
        // Delegate to node
        return node.handleFlowMessage(packet);
    }
    
    @Override
    public void handleStreamChannel(
            io.netnotes.engine.io.process.StreamChannel channel,
            io.netnotes.engine.io.ContextPath fromPath) {
        
        // Delegate to node
        node.handleStreamChannel(channel, fromPath);
    }
    
    @Override
    public io.netnotes.engine.io.ContextPath getContextPath() {
        return nodePath;
    }
    
    @Override
    public java.util.concurrent.Flow.Subscriber<io.netnotes.engine.io.RoutedPacket> 
            getSubscriber() {
        
        // Delegate to node
        return node.getFlowSubscriber();
    }
    
    public INode getNode() {
        return node;
    }
}