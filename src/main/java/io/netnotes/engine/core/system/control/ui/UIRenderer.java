package io.netnotes.engine.core.system.control.ui;


import io.netnotes.engine.core.system.control.containers.Container;
import io.netnotes.engine.core.system.control.containers.ContainerConfig;
import io.netnotes.engine.core.system.control.containers.ContainerId;
import io.netnotes.engine.core.system.control.containers.ContainerType;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;


/**
 * UIRenderer - Interface for UI implementations with full container management
 * 
 * Responsibilities:
 * - Create and manage containers
 * - Handle container lifecycle (show/hide/focus/destroy)
 * - Process messages for containers
 * - Setup bidirectional streams for containers
 * - Coordinate rendering for multiple containers
 * 
 * Reply Pattern:
 * - All methods return CompletableFuture<Void>
 * - Success: future completes normally
 * - Failure: future completes exceptionally with descriptive message
 * - RenderingService handles converting to SUCCESS/ERROR replies
 * 
 * Implementations:
 * - ConsoleUIRenderer (terminal via JLine3)
 * - WebRenderer (browser via WebSocket)
 * - NanoVGRenderer (native GUI via OpenGL)
 */
public interface UIRenderer {
    
    // ===== CONTAINER MANAGEMENT =====
    
    /**
     * Create a container instance managed by this renderer
     * 
     * @param id Container ID
     * @param title Container title
     * @param type Container type
     * @param ownerPath Owner context path
     * @param config Container configuration
     * @param rendererId Renderer ID
     * @return Container instance
     */
    Container createContainer(
        ContainerId id,
        String title,
        ContainerType type,
        ContextPath ownerPath,
        ContainerConfig config,
        String rendererId
    );
    
    /**
     * Get container by ID
     */
    Container getContainer(ContainerId id);
    
    /**
     * Check if renderer has a specific container
     */
    boolean hasContainer(ContainerId id);
    
    /**
     * Get all containers managed by this renderer
     */
    List<Container> getAllContainers();
    
    /**
     * Get containers by owner path
     */
    List<Container> getContainersByOwner(ContextPath ownerPath);
    
    /**
     * Get number of active containers
     */
    int getContainerCount();
    
    /**
     * Get focused container (if any)
     */
    Container getFocusedContainer();
    
    // ===== MESSAGE HANDLING =====
    
    /**
     * Handle container-related messages
     * Called by RenderingService after routing
     * 
     * Handles:
     * - CREATE_CONTAINER
     * - DESTROY_CONTAINER
     * - SHOW_CONTAINER
     * - HIDE_CONTAINER
     * - FOCUS_CONTAINER
     * - MAXIMIZE_CONTAINER
     * - RESTORE_CONTAINER
     * - QUERY_CONTAINER
     * - LIST_CONTAINERS
     * 
     * @param message Command message
     * @return Future that completes normally on success, exceptionally on failure
     */
    CompletableFuture<Void> handleMessage(NoteBytesMap message, RoutedPacket packet);
    
    void setUIReplyExec(UIReplyExec replier);
    // ===== STREAM HANDLING =====
    
    /**
     * Check if this renderer can handle a stream from the given path
     * Used by RenderingService to route stream channels
     * 
     * @param fromPath Source path of the stream
     * @return true if this renderer manages a container for this path
     */
    boolean canHandleStreamFrom(ContextPath fromPath);
    
    /**
     * Handle incoming stream channel
     * 
     * Flow:
     * 1. Find target container by fromPath (handle path)
     * 2. Pass render stream to container
     * 3. Request event stream back via service
     * 4. Pass event stream to container
     * 
     * @param channel Stream channel from ContainerHandle
     * @param fromPath Source path (ContainerHandle path)
     * @param service RenderingService (for requesting event stream)
     */
    void handleStreamChannel(StreamChannel channel, ContextPath fromPath);

    void handleEventStream(StreamChannel eventChannel, ContextPath fromPath);
    // ===== RENDERER LIFECYCLE =====
    
    /**
     * Check if this renderer is active/available
     */
    boolean isActive();
    
    /**
     * Initialize the renderer
     */
    CompletableFuture<Void> initialize();
    
    /**
     * Shutdown the renderer and all its containers
     */
    CompletableFuture<Void> shutdown();
    
    // ===== RENDERER CAPABILITIES =====
    
    /**
     * Check if renderer supports a container type
     */
    boolean supports(ContainerType type);
    
    /**
     * Get all supported container types
     */
    Set<ContainerType> getSupportedTypes();
    
    /**
     * Get renderer description
     */
    String getDescription();
}