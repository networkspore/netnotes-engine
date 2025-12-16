package io.netnotes.engine.core.system.control.containers;

import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.ProcessKeys;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBoolean;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ContainerService - Manages system-level containers with multiple renderers
 * 
 * Lives at: /system/services/container-service
 * 
 * Architecture (similar to IODaemon):
 * - Maintains map of UIRenderers (by renderer ID)
 * - Creates containers with assigned renderer
 * - Container reads from stream and writes to its UIRenderer
 * 
 * Provides:
 * - Container creation/destruction
 * - Container state management (show/hide/focus)
 * - Event propagation to owners
 * - Multiple UIRenderer support
 */
public class ContainerService extends FlowProcess {
    
    private final BitFlagStateMachine state;
    
    // Renderer management
    private final Map<String, RendererInfo> renderers = new ConcurrentHashMap<>();
    private final String systemDefaultRendererId;
    private final Map<ContainerType, String> typeDefaultRenderers = new ConcurrentHashMap<>();

    // Container management
    private final Map<ContainerId, Container> containers = new ConcurrentHashMap<>();
    private final Map<ContextPath, List<ContainerId>> ownerContainers = new ConcurrentHashMap<>();
    private final HashMap<NoteBytes, RoutedMessageExecutor> m_msgExecMap = new HashMap<>();

    // Track focus
    private ContainerId focusedContainer = null;
    
    /**
     * Constructor with default renderer
     * 
     * @param name Service name
     * @param defaultRenderer Default UI renderer (e.g., ConsoleUIRenderer)
     */
    public ContainerService(String name, RendererInfo systemDefaultRenderer) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.state = new BitFlagStateMachine("container-service");
        
        // Register system default (supports all types)
        this.systemDefaultRendererId = "system-default";
        this.renderers.put(systemDefaultRendererId, systemDefaultRenderer);
        
        setupMsgExcMap();
        setupStateTransitions();
    }

    public void registerRenderer(
        String rendererId,
        UIRenderer renderer,
        Set<ContainerType> supportedTypes,
        String description
    ) {
        
        if (rendererId == null || renderer == null) {
            throw new IllegalArgumentException("rendererId and renderer required");
        }
        
        renderers.put(rendererId, new RendererInfo(renderer, supportedTypes, description));
        
        Log.logMsg(String.format(
            "[ContainerService] Registered renderer '%s': %s (supports: %s)",
            rendererId, description, supportedTypes
        ));
    }

    public void setDefaultRendererForType(ContainerType type, String rendererId) {
        RendererInfo info = renderers.get(rendererId);
        if (info == null) {
            throw new IllegalArgumentException("Renderer not found: " + rendererId);
        }
        
        if (!info.supports(type)) {
            throw new IllegalArgumentException(String.format(
                "Renderer '%s' does not support container type %s",
                rendererId, type
            ));
        }
        
        typeDefaultRenderers.put(type, rendererId);
        
        Log.logMsg(String.format(
            "[ContainerService] Default renderer for %s: %s",
            type, rendererId
        ));
    }
    
    /**
     * Remove renderer (fails if containers are using it)
     */
    public void unregisterRenderer(String rendererId) {
        if (rendererId.equals(systemDefaultRendererId)) {
            throw new IllegalArgumentException("Cannot unregister system default renderer");
        }
        
        // Check if any containers are using this renderer
        boolean inUse = containers.values().stream()
            .anyMatch(c -> c.getRendererId().equals(rendererId));
        
        if (inUse) {
            throw new IllegalStateException(
                "Cannot unregister renderer in use: " + rendererId
            );
        }
        
        renderers.remove(rendererId);
        
        // Remove from type defaults
        typeDefaultRenderers.entrySet()
            .removeIf(entry -> entry.getValue().equals(rendererId));
        
        Log.logMsg("[ContainerService] Unregistered renderer: " + rendererId);
    }

    /**
     * Select renderer using 3-tier strategy:
     * 1. Explicit rendererId (if provided and valid)
     * 2. Type-specific default (if configured)
     * 3. System default (always available)
     */
    private RendererSelection selectRenderer(
            String explicitRendererId,
            ContainerType containerType) {
        
        // TIER 1: Explicit renderer specified
        if (explicitRendererId != null && !explicitRendererId.isEmpty()) {
            RendererInfo info = renderers.get(explicitRendererId);
            
            if (info == null) {
                throw new IllegalArgumentException(
                    "Renderer not found: " + explicitRendererId
                );
            }
            
            if (!info.supports(containerType)) {
                throw new IllegalArgumentException(String.format(
                    "Renderer '%s' does not support container type %s (supports: %s)",
                    explicitRendererId, containerType, info.getSupportedTypes()
                ));
            }
            
            Log.logMsg(String.format(
                "[ContainerService] Using explicit renderer '%s' for %s container",
                explicitRendererId, containerType
            ));
            
            return new RendererSelection(explicitRendererId, info);
        }
        
        // TIER 2: Type-specific default
        String typeDefaultId = typeDefaultRenderers.get(containerType);
        if (typeDefaultId != null) {
            RendererInfo info = renderers.get(typeDefaultId);
            if (info != null) {
                Log.logMsg(String.format(
                    "[ContainerService] Using type default renderer '%s' for %s container",
                    typeDefaultId, containerType
                ));
                return new RendererSelection(typeDefaultId, info);
            }
        }
        
        // TIER 3: System default
        RendererInfo systemDefault = renderers.get(systemDefaultRendererId);
        Log.logMsg(String.format(
            "[ContainerService] Using system default renderer for %s container",
            containerType
        ));
        return new RendererSelection(systemDefaultRendererId, systemDefault);
    }
    
    
    private void setupStateTransitions() {
        state.onStateAdded(ContainerServiceStates.INITIALIZING, (old, now, bit) -> {
            Log.logMsg("[ContainerService] Initializing...");
        });
        
        state.onStateAdded(ContainerServiceStates.READY, (old, now, bit) -> {
            Log.logMsg("[ContainerService] Ready - accepting requests");
        });
        
        state.onStateAdded(ContainerServiceStates.HAS_CONTAINERS, (old, now, bit) -> {
            Log.logMsg("[ContainerService] First container created");
        });
        
        state.onStateRemoved(ContainerServiceStates.HAS_CONTAINERS, (old, now, bit) -> {
            Log.logMsg("[ContainerService] All containers destroyed");
        });
        
        state.onStateAdded(ContainerServiceStates.HAS_FOCUSED_CONTAINER, (old, now, bit) -> {
            Log.logMsg("[ContainerService] Container focused");
        });
        
        state.onStateAdded(ContainerServiceStates.SHUTTING_DOWN, (old, now, bit) -> {
            Log.logMsg("[ContainerService] Shutting down...");
        });
        
        state.onStateAdded(ContainerServiceStates.STOPPED, (old, now, bit) -> {
            Log.logMsg("[ContainerService] Stopped");
        });
        
        state.onStateAdded(ContainerServiceStates.ERROR, (old, now, bit) -> {
            Log.logError("[ContainerService] ERROR");
        });
    }
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(ContainerServiceStates.INITIALIZING);
        
        Log.logMsg("[ContainerService] Started at: " + contextPath);
        
        // Check if default renderer is active
        RendererInfo defaultRenderer = renderers.get(systemDefaultRendererId);
        if (defaultRenderer != null && defaultRenderer.getRenderer().isActive()) {
            Log.logMsg("[ContainerService] renderer active");
            state.addState(ContainerServiceStates.UI_RENDERER_ACTIVE);
        }
        
        state.removeState(ContainerServiceStates.INITIALIZING);
        state.addState(ContainerServiceStates.READY);
        state.addState(ContainerServiceStates.ACCEPTING_REQUESTS);
        
        // Service is now running and will stay alive until shutdown()
        Log.logMsg("[ContainerService] Initialization complete, service running");
        return CompletableFuture.completedFuture(null);
    }

    

    private void setupMsgExcMap(){
        m_msgExecMap.put(ContainerCommands.CREATE_CONTAINER, this::handleCreateContainer);
        m_msgExecMap.put(ContainerCommands.DESTROY_CONTAINER, this::handleDestroyContainer);
        m_msgExecMap.put(ContainerCommands.SHOW_CONTAINER, this::handleShowContainer);
        m_msgExecMap.put(ContainerCommands.HIDE_CONTAINER, this::handleHideContainer);
        m_msgExecMap.put(ContainerCommands.FOCUS_CONTAINER, this::handleFocusContainer);
        m_msgExecMap.put(ContainerCommands.QUERY_CONTAINER, this::handleQueryContainer);
        m_msgExecMap.put(ContainerCommands.LIST_CONTAINERS, this::handleListContainers);
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        Log.logMsg("[ContainerService] handleMessage called");
        Log.logMsg("[ContainerService] Packet source: " + packet.getSourcePath());
        Log.logMsg("[ContainerService] Packet dest: " + packet.getDestinationPath());
        Log.logMsg("[ContainerService] Has correlationId: " + packet.hasMetadata(ProcessKeys.CORRELATION_ID));
    
        if (!ContainerServiceStates.canAcceptRequests(state)) {
            return replyError(packet, "Service not accepting requests");
        }
        

        try {
            NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
            NoteBytes cmdBytes = msg.get(Keys.CMD);
            
            if (cmdBytes == null) {
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("'cmd' required"));
            }
            
            RoutedMessageExecutor msgExec = m_msgExecMap.get(cmdBytes);
            // Dispatch commands
            if(msgExec != null){
                return msgExec.execute(msg, packet);
    
            } else {
                Log.logError("[ContainerService] Unknown command: " + cmdBytes);
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unknown command: " + cmdBytes));
            }
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        
        Log.logMsg("[ContainerService] Stream channel received from: " + fromPath);
        
        if (fromPath == null) {
            Log.logError("[ContainerService] fromPath is null!");
            channel.getReadyFuture().completeExceptionally(
                new IllegalStateException("fromPath is null")
            );
            return;
        }
        
        // Find container by looking for handles that are children of owners
        // fromPath will be something like /system/bootstrap-wizard-container
        // We need to find its parent (the owner) and then find that owner's container
        
        Container targetContainer = null;
        
        // Try to find container where the handle's parent is the owner
        ContextPath handleParent = registry.getParent(fromPath);
        Log.logMsg("[ContainerService] Handle parent: " + handleParent);
        
        if (handleParent != null) {
            for (Container container : containers.values()) {
                Log.logMsg("[ContainerService] Checking container owner: " + 
                    container.getOwnerPath() + " vs " + handleParent);
                if (container.getOwnerPath().equals(handleParent)) {
                    targetContainer = container;
                    break;
                }
            }
        }
        
        if (targetContainer == null) {
            Log.logError("[ContainerService] No container found for handle: " + fromPath + 
                " (parent: " + handleParent + ")");
            channel.getReadyFuture().completeExceptionally(
                new IllegalStateException("No container found for handle: " + fromPath)
            );
            return;
        }
        
        Log.logMsg("[ContainerService] Routing stream to container: " + 
            targetContainer.getId());
        
        // Pass stream to Container
        targetContainer.handleRenderStream(channel, fromPath);
    }
    
    // ===== COMMAND HANDLERS =====
    
    private CompletableFuture<Void> handleCreateContainer(NoteBytesMap msg, RoutedPacket packet) {
        state.addState(ContainerServiceStates.CREATING_CONTAINER);
        
        // Parse command
        NoteBytes rendererIdBytes = msg.get(ContainerCommands.RENDERER_ID);
        NoteBytes titleBytes = msg.get(Keys.TITLE);
        NoteBytes typeBytes = msg.get(Keys.TYPE);
        NoteBytes pathBytes = msg.get(Keys.PATH);
        NoteBytes configBytes = msg.get(Keys.CONFIG);
        NoteBytes autoFocusBytes = msg.getOrDefault(ContainerCommands.AUTO_FOCUS, NoteBoolean.FALSE);

        String title = titleBytes != null ? titleBytes.getAsString() : "Untitled";
        ContainerType type = typeBytes != null ? 
            ContainerType.valueOf(typeBytes.getAsString()) : ContainerType.TERMINAL;
        ContextPath ownerPath = pathBytes != null ? 
            ContextPath.fromNoteBytes(pathBytes) : null;
        ContainerConfig config = configBytes != null ? 
            ContainerConfig.fromNoteBytes(configBytes) : new ContainerConfig();
        boolean autoFocus = autoFocusBytes.getAsBoolean();
        
        // NEW: Explicit renderer ID (optional)
        String explicitRendererId = rendererIdBytes != null ? 
            rendererIdBytes.getAsString() : null;
        
        // SELECT RENDERER using 3-tier strategy
        RendererSelection selection;
        
        try {
            selection = selectRenderer(explicitRendererId, type);
        } catch (IllegalArgumentException e) {
            state.removeState(ContainerServiceStates.CREATING_CONTAINER);
            return replyError(packet, e.getMessage());
        }
        
        Log.logMsg(String.format(
            "[ContainerService] Creating %s container: %s (owner: %s, renderer: %s)",
            type, title, ownerPath, selection.rendererId
        ));
        
        // Generate container ID
        ContainerId containerId = ContainerId.generate();
        
        // Create container with selected renderer
        Container container = new Container(
            containerId,
            title,
            type,
            ownerPath,
            config,
            selection.info.getRenderer(),
            selection.rendererId,  // NEW: Pass renderer ID
            contextPath
        );
        
        // Initialize container
        return container.initialize()
            .thenCompose(v -> {
                // Register container
                containers.put(containerId, container);
                
                // Track by owner
                ownerContainers.computeIfAbsent(ownerPath, k -> new ArrayList<>())
                    .add(containerId);
                
                // Update states
                if (containers.size() == 1) {
                    state.addState(ContainerServiceStates.HAS_CONTAINERS);
                }
                
                if (container.isVisible()) {
                    updateVisibilityState();
                }
                
                state.removeState(ContainerServiceStates.CREATING_CONTAINER);
                
                Log.logMsg("[ContainerService] Container created: " + containerId);
                
                return autoFocus 
                    ? autoFocusContainer(container)
                    : CompletableFuture.completedFuture(false);
            })
            .thenAccept(isAutoFocus -> {
                // Build response with renderer info
                if (isAutoFocus) {
                    reply(packet,  
                        new NoteBytesPair(Keys.STATUS, ProtocolMesssages.SUCCESS),
                        new NoteBytesPair(Keys.CONTAINER_ID, containerId.toNoteBytes()),
                        new NoteBytesPair(Keys.PATH, container.getPath().getSegments()),
                        new NoteBytesPair(ContainerCommands.RENDERER_ID, selection.rendererId),  
                        new NoteBytesPair(ContainerCommands.AUTO_FOCUS, true)
                    );
                }else{
                    reply(packet,                  
                        new NoteBytesPair(Keys.STATUS, ProtocolMesssages.SUCCESS),
                        new NoteBytesPair(Keys.CONTAINER_ID, containerId.toNoteBytes()),
                        new NoteBytesPair(Keys.PATH, container.getPath().getSegments()),
                        new NoteBytesPair(ContainerCommands.RENDERER_ID, selection.rendererId)  
                    );
                }
                
                
            })
            .exceptionally(ex -> {
                state.removeState(ContainerServiceStates.CREATING_CONTAINER);
                String errorMsg = ex.getMessage();
                Log.logError("[ContainerService] Failed to create container: " + errorMsg);
                reply(packet, ProtocolObjects.getErrorObject(errorMsg));
                return null;
            });
    }
    
    private CompletableFuture<Void> handleDestroyContainer(NoteBytesMap msg, RoutedPacket packet) {
        state.addState(ContainerServiceStates.DESTROYING_CONTAINER);
        
        ContainerId containerId = ContainerId.fromNoteBytes(
            msg.get(Keys.CONTAINER_ID)
        );
        
        Container container = containers.get(containerId);
        if (container == null) {
            state.removeState(ContainerServiceStates.DESTROYING_CONTAINER);
            return replyError(packet, "Container not found");
        }
        
        Log.logMsg("[ContainerService] Destroying container: " + containerId);
        
        return container.destroy()
            .thenRun(() -> {
                // Unregister
                containers.remove(containerId);
                
                // Remove from owner tracking
                List<ContainerId> ownerList = ownerContainers.get(container.getOwnerPath());
                if (ownerList != null) {
                    ownerList.remove(containerId);
                    if (ownerList.isEmpty()) {
                        ownerContainers.remove(container.getOwnerPath());
                    }
                }
                
                // Clear focus if this was focused
                if (containerId.equals(focusedContainer)) {
                    focusedContainer = null;
                    state.removeState(ContainerServiceStates.HAS_FOCUSED_CONTAINER);
                }
                
                // Update states
                if (containers.isEmpty()) {
                    state.removeState(ContainerServiceStates.HAS_CONTAINERS);
                    state.removeState(ContainerServiceStates.HAS_VISIBLE_CONTAINERS);
                } else {
                    updateVisibilityState();
                }
                
                state.removeState(ContainerServiceStates.DESTROYING_CONTAINER);
                
                Log.logMsg("[ContainerService] Container destroyed: " + containerId);
                
                // Reply success
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                reply(packet, response.toNoteBytes());
                
                /*
                sendEvent(container.getOwnerPath(), 
                    ContainerCommands.containerClosed(containerId));*/
            });
    }
    
    private CompletableFuture<Void> handleShowContainer(NoteBytesMap msg, RoutedPacket packet) {

        ContainerId containerId = ContainerId.fromNoteBytes(
            msg.get(Keys.CONTAINER_ID)
        );
        
        Container container = containers.get(containerId);
        if (container == null) {
            return replyError(packet, "Container not found");
        }
        
        state.addState(ContainerServiceStates.RENDERING);
        
        return container.show()
            .thenRun(() -> {
                updateVisibilityState();
                state.removeState(ContainerServiceStates.RENDERING);
                replySuccess(packet);
            });
    }
    
    private CompletableFuture<Void> handleHideContainer(NoteBytesMap msg, RoutedPacket packet) {

        ContainerId containerId = ContainerId.fromNoteBytes(
            msg.get(Keys.CONTAINER_ID)
        );
        
        Container container = containers.get(containerId);
        if (container == null) {
            return replyError(packet, "Container not found");
        }
        
        state.addState(ContainerServiceStates.RENDERING);
        
        return container.hide()
            .thenRun(() -> {
                updateVisibilityState();
                state.removeState(ContainerServiceStates.RENDERING);
                replySuccess(packet);
            });
    }
    
    private CompletableFuture<Boolean> autoFocusContainer(Container container){
        ContainerId containerId = container.getId();
        ContainerId previousFocus = focusedContainer;
        focusedContainer = containerId;
        state.addState(ContainerServiceStates.HAS_FOCUSED_CONTAINER);

        if (previousFocus != null && !previousFocus.equals(containerId)) {
            Container prevContainer = containers.get(previousFocus);
            if (prevContainer != null) {
                prevContainer.unfocus();
            }
        }

        return container.focus()
            .thenApply((v) -> {
                updateVisibilityState();
                state.removeState(ContainerServiceStates.RENDERING);
                return true;
            });
    }
    

    private CompletableFuture<Void> handleFocusContainer(NoteBytesMap msg, RoutedPacket packet) {
  
        ContainerId containerId = ContainerId.fromNoteBytes(
            msg.get(Keys.CONTAINER_ID)
        );
        
        Container container = containers.get(containerId);
        if (container == null) {
            return replyError(packet, "Container not found");
        }
        
        // Update focus tracking
        ContainerId previousFocus = focusedContainer;
        focusedContainer = containerId;
        state.addState(ContainerServiceStates.HAS_FOCUSED_CONTAINER);
        
        // Unfocus previous
        if (previousFocus != null && !previousFocus.equals(containerId)) {
            Container prevContainer = containers.get(previousFocus);
            if (prevContainer != null) {
                prevContainer.unfocus();
            }
        }
        
        state.addState(ContainerServiceStates.RENDERING);
        
        return container.focus()
            .thenRun(() -> {
                updateVisibilityState();
                state.removeState(ContainerServiceStates.RENDERING);
                replySuccess(packet);
            });
    }
    
    private CompletableFuture<Void> handleQueryContainer(NoteBytesMap msg, RoutedPacket packet) {

        ContainerId containerId = ContainerId.fromNoteBytes(
            msg.get(Keys.CONTAINER_ID)
        );
        
        Container container = containers.get(containerId);
        if (container == null) {
            return replyError(packet, "Container not found");
        }
        
        ContainerInfo info = container.getInfo();
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        response.put(Keys.DATA, info.toNoteBytes());
        
        reply(packet, response.toNoteBytes());
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleListContainers(NoteBytesMap msg, RoutedPacket packet) {
        List<ContainerInfo> infoList = containers.values().stream()
            .map(Container::getInfo)
            .toList();
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        response.put("count", infoList.size());
        response.put("containers", infoList.stream()
            .map(ContainerInfo::toNoteBytes)
            .toArray(NoteBytes[]::new));
        
        reply(packet, response.toNoteBytes());
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== STATE HELPERS =====
    
    private void updateVisibilityState() {
        boolean hasVisible = containers.values().stream()
            .anyMatch(Container::isVisible);
        
        if (hasVisible) {
            state.addState(ContainerServiceStates.HAS_VISIBLE_CONTAINERS);
        } else {
            state.removeState(ContainerServiceStates.HAS_VISIBLE_CONTAINERS);
        }
    }
    
    // ===== HELPERS =====
    
    /*private void sendEvent(ContextPath target, NoteBytesMap event) {
        if (registry.exists(target)) {
            emitTo(target, event.toNoteBytes());
        }
    }*/
    
    private void replySuccess(RoutedPacket packet) {
        reply(packet, ProtocolObjects.SUCCESS_OBJECT);
    }
    
    private CompletableFuture<Void> replyError(RoutedPacket packet, String message) {
        
        reply(packet, ProtocolObjects.getErrorObject(message));
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== QUERIES =====
    
    public boolean isOperational() {
        return ContainerServiceStates.isOperational(state);
    }
    
    public int getContainerCount() {
        return containers.size();
    }
    
    public List<Container> getAllContainers() {
        return new ArrayList<>(containers.values());
    }
    
    public List<Container> getContainersByOwner(ContextPath ownerPath) {
        List<ContainerId> ids = ownerContainers.get(ownerPath);
        if (ids == null) {
            return List.of();
        }
        
        return ids.stream()
            .map(containers::get)
            .filter(c -> c != null)
            .toList();
    }
    
    public Container getFocusedContainer() {
        return focusedContainer != null ? containers.get(focusedContainer) : null;
    }
    
    public String getStateDescription() {
        return ContainerServiceStates.describe(state);
    }
    
    public String getDetailedStatus() {
        return ContainerServiceStates.describeDetailed(state, containers.size());
    }
    
    public BitFlagStateMachine getState() {
        return state;
    }

    /**
     * Get all registered renderers
     */
    public Map<String, String> getRendererInfo() {
        Map<String, String> info = new HashMap<>();
        renderers.forEach((id, rendererInfo) -> 
            info.put(id, rendererInfo.getDescription())
        );
        return info;
    }

    /**
     * Get default renderer for a type
     */
    public String getDefaultRendererForType(ContainerType type) {
        return typeDefaultRenderers.getOrDefault(type, systemDefaultRendererId);
    }

    /**
     * Check if renderer supports type
     */
    public boolean rendererSupportsType(String rendererId, ContainerType type) {
        RendererInfo info = renderers.get(rendererId);
        return info != null && info.supports(type);
    }

    /**
     * Get containers by renderer
     */
    public List<Container> getContainersByRenderer(String rendererId) {
        return containers.values().stream()
            .filter(c -> c.getRendererId().equals(rendererId))
            .toList();
    }
    
    // ===== SHUTDOWN =====
    
    public CompletableFuture<Void> shutdown() {
        state.addState(ContainerServiceStates.SHUTTING_DOWN);
        state.removeState(ContainerServiceStates.ACCEPTING_REQUESTS);
        
        Log.logMsg("[ContainerService] Shutting down, destroying " + 
            containers.size() + " containers");
        
        List<CompletableFuture<Void>> futures = containers.values().stream()
            .map(Container::destroy)
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                containers.clear();
                ownerContainers.clear();
                focusedContainer = null;
                
                state.removeState(ContainerServiceStates.SHUTTING_DOWN);
                state.removeState(ContainerServiceStates.READY);
                state.removeState(ContainerServiceStates.HAS_CONTAINERS);
                state.removeState(ContainerServiceStates.HAS_VISIBLE_CONTAINERS);
                state.removeState(ContainerServiceStates.HAS_FOCUSED_CONTAINER);
                state.addState(ContainerServiceStates.STOPPED);
                
                Log.logMsg("[ContainerService] Shutdown complete");
            });
    }

    private static class RendererSelection {
        final String rendererId;
        final RendererInfo info;
        
        RendererSelection(String rendererId, RendererInfo info) {
            this.rendererId = rendererId;
            this.info = info;
        }
    }
}