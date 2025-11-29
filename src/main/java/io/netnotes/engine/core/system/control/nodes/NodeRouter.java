package io.netnotes.engine.core.system.control.nodes;

import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * NodeRouter - Routes messages between nodes
 * 
 * Responsibilities:
 * - Inter-node message routing
 * - Permission checking
 * - Stream channel setup
 * - Message filtering/validation
 * 
 * Security:
 * - Nodes can only communicate if both allow it
 * - Enforce message size limits
 * - Rate limiting per connection
 */
class NodeRouter {
    
    private final NodeController controller;
    
    public NodeRouter(NodeController controller) {
        this.controller = controller;
    }
    
    /**
     * Route message from one node to another
     */
    public CompletableFuture<Void> routeMessage(
            NoteBytesReadOnly fromNodeId,
            NoteBytesReadOnly toNodeId,
            RoutedPacket packet) {
        
        // Get nodes
        NodeInstance fromNode = controller.getNode(fromNodeId);
        NodeInstance toNode = controller.getNode(toNodeId);
        
        if (fromNode == null || toNode == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Node not found"));
        }
        
        // Check permissions
        if (!canCommunicate(fromNodeId, toNodeId)) {
            return CompletableFuture.failedFuture(
                new SecurityException("Communication not allowed between nodes"));
        }
        
        // Route message
        return toNode.getNode().handleFlowMessage(packet);
    }
    
    /**
     * Request stream channel between nodes
     */
    public CompletableFuture<StreamChannel> 
            requestStreamChannel(NoteBytesReadOnly fromNodeId, NoteBytesReadOnly toNodeId) {
        
        // Check permissions
        if (!canCommunicate(fromNodeId, toNodeId)) {
            return CompletableFuture.failedFuture(
                new SecurityException("Stream channel not allowed between nodes"));
        }
        
        // Get nodes
        NodeInstance fromNode = controller.getNode(fromNodeId);
        NodeInstance toNode = controller.getNode(toNodeId);
        
        if (fromNode == null || toNode == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Node not found"));
        }
        
        // Request channel via ProcessRegistry
        return controller.getProcessRegistry().requestStreamChannel(
            fromNode.getNode().getContextPath(),
            toNode.getNode().getContextPath()
        );
    }
    
    /**
     * Check if two nodes can communicate
     * 
     * TODO: Implement permission system
     * - Per-node allow/deny lists
     * - Global communication policies
     * - Category-based permissions
     */
    private boolean canCommunicate(NoteBytesReadOnly fromNodeId, NoteBytesReadOnly toNodeId) {
        // For now, allow all communication
        return true;
    }
}