package io.netnotes.engine.core.system.control.containers;

import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<String, UIRenderer> renderers = new ConcurrentHashMap<>();
    private final String defaultRendererId;
    
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
    public ContainerService(String name, UIRenderer defaultRenderer) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.state = new BitFlagStateMachine("container-service");
        
        // Register default renderer
        this.defaultRendererId = "default";
        this.renderers.put(defaultRendererId, defaultRenderer);
        setupMsgExcMap();
        setupStateTransitions();
    }
    
    /**
     * Register additional renderer (for multi-renderer scenarios)
     * 
     * Example: Desktop app might have:
     * - "gui" renderer (NanoVG)
     * - "terminal" renderer (for embedded terminal widgets)
     */
    public void registerRenderer(String rendererId, UIRenderer renderer) {
        renderers.put(rendererId, renderer);
        Log.logMsg("[ContainerService] Registered renderer: " + rendererId);
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
        UIRenderer defaultRenderer = renderers.get(defaultRendererId);
        if (defaultRenderer != null && defaultRenderer.isActive()) {
            state.addState(ContainerServiceStates.UI_RENDERER_ACTIVE);
        }
        
        state.removeState(ContainerServiceStates.INITIALIZING);
        state.addState(ContainerServiceStates.READY);
        state.addState(ContainerServiceStates.ACCEPTING_REQUESTS);
        
        return getCompletionFuture();
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
        Log.logError("[ContainerService] Unexpected stream channel from: " + fromPath);
    }
    
    // ===== COMMAND HANDLERS =====
    
    private CompletableFuture<Void> handleCreateContainer(NoteBytesMap msg, RoutedPacket packet) {
        state.addState(ContainerServiceStates.CREATING_CONTAINER);
        
        String title = msg.get(Keys.TITLE).getAsString();
        ContainerType type = ContainerType.valueOf(
            msg.get(Keys.TYPE).getAsString()
        );
        ContextPath ownerPath = ContextPath.parse(
            msg.get(Keys.PATH).getAsString()
        );
        
        ContainerConfig config = null;
        if (msg.has(Keys.CONFIG)) {
            config = ContainerConfig.fromNoteBytes(
                msg.get(Keys.CONFIG).getAsNoteBytesMap()
            );
        }
        
        // Determine which renderer to use
        String rendererId = defaultRendererId;
        if (msg.has("renderer_id")) {
            rendererId = msg.get("renderer_id").getAsString();
        }
        
        UIRenderer containerRenderer = renderers.get(rendererId);
        if (containerRenderer == null) {
            state.removeState(ContainerServiceStates.CREATING_CONTAINER);
            return replyError(packet, "Renderer not found: " + rendererId);
        }
        
        Log.logMsg(String.format(
            "[ContainerService] Creating %s container: %s (owner: %s, renderer: %s)",
            type, title, ownerPath, rendererId
        ));
        
        // Generate container ID
        ContainerId containerId = ContainerId.generate();
        
        // Create container with assigned renderer
        Container container = new Container(
            containerId,
            title,
            type,
            ownerPath,
            config != null ? config : new ContainerConfig(),
            containerRenderer,  // Pass renderer directly
            contextPath         // Service path for requesting streams
        );
        
        // Initialize container (creates stream channel for render commands)
        return container.initialize()
            .thenRun(() -> {
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
                
                // Reply with container ID
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                response.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
                response.put(Keys.PATH, container.getPath().toString());
                
                reply(packet, response.toNoteBytes());
                
                // Send event to owner
                sendEvent(ownerPath, ContainerCommands.containerCreated(
                    containerId, container.getPath()
                ));
            })
            .exceptionally(ex -> {
                state.removeState(ContainerServiceStates.CREATING_CONTAINER);
                String errorMsg = ex.getMessage();
                Log.logError("[ContainerService] Failed to create container: " + 
                    errorMsg);
                
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
                
                // Send event to owner
                sendEvent(container.getOwnerPath(), 
                    ContainerCommands.containerClosed(containerId));
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
                
                // Send focus event to owner
                sendEvent(container.getOwnerPath(),
                    ContainerCommands.containerFocused(containerId));
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
    
    private void sendEvent(ContextPath target, NoteBytesMap event) {
        if (registry.exists(target)) {
            emitTo(target, event.toNoteBytes());
        }
    }
    
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
}