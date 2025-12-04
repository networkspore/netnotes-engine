package io.netnotes.engine.core.system.control.containers;


import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ContainerService - Manages system-level containers
 * 
 * Lives at: /system/services/container-service
 * 
 * Provides:
 * - Container creation/destruction
 * - Container state management (show/hide/focus)
 * - Event propagation to owners
 * - UIRenderer coordination
 * 
 * INodes request containers via standard messages:
 * processInterface.sendMessage(CONTAINER_SERVICE_PATH, createContainer(...))
 */
public class ContainerService extends FlowProcess {
    
    private final UIRenderer uiRenderer;
    private final BitFlagStateMachine state;
    private final Map<ContainerId, Container> containers = new ConcurrentHashMap<>();
    private final Map<ContextPath, List<ContainerId>> ownerContainers = new ConcurrentHashMap<>();
    
    // Track focus
    private ContainerId focusedContainer = null;
    
    public ContainerService(String name, UIRenderer uiRenderer) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.uiRenderer = uiRenderer;
        this.state = new BitFlagStateMachine("container-service");
        
        setupStateTransitions();
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(ContainerServiceStates.INITIALIZING, (old, now, bit) -> {
            System.out.println("[ContainerService] Initializing...");
        });
        
        state.onStateAdded(ContainerServiceStates.READY, (old, now, bit) -> {
            System.out.println("[ContainerService] Ready - accepting requests");
        });
        
        state.onStateAdded(ContainerServiceStates.HAS_CONTAINERS, (old, now, bit) -> {
            System.out.println("[ContainerService] First container created");
        });
        
        state.onStateRemoved(ContainerServiceStates.HAS_CONTAINERS, (old, now, bit) -> {
            System.out.println("[ContainerService] All containers destroyed");
        });
        
        state.onStateAdded(ContainerServiceStates.HAS_FOCUSED_CONTAINER, (old, now, bit) -> {
            System.out.println("[ContainerService] Container focused");
        });
        
        state.onStateAdded(ContainerServiceStates.SHUTTING_DOWN, (old, now, bit) -> {
            System.out.println("[ContainerService] Shutting down...");
        });
        
        state.onStateAdded(ContainerServiceStates.STOPPED, (old, now, bit) -> {
            System.out.println("[ContainerService] Stopped");
        });
        
        state.onStateAdded(ContainerServiceStates.ERROR, (old, now, bit) -> {
            System.err.println("[ContainerService] ERROR");
        });
    }
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(ContainerServiceStates.INITIALIZING);
        
        System.out.println("[ContainerService] Started at: " + contextPath);
        
        // Check if UI renderer is active
        if (uiRenderer != null && uiRenderer.isActive()) {
            state.addState(ContainerServiceStates.UI_RENDERER_ACTIVE);
        }
        
        state.removeState(ContainerServiceStates.INITIALIZING);
        state.addState(ContainerServiceStates.READY);
        state.addState(ContainerServiceStates.ACCEPTING_REQUESTS);
        
        return getCompletionFuture();
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
            
            // Dispatch commands
            if (cmdBytes.equals(ContainerProtocol.CREATE_CONTAINER)) {
                return handleCreateContainer(packet);
            } else if (cmdBytes.equals(ContainerProtocol.DESTROY_CONTAINER)) {
                return handleDestroyContainer(packet);
            } else if (cmdBytes.equals(ContainerProtocol.UPDATE_CONTAINER)) {
                return handleUpdateContainer(packet);
            } else if (cmdBytes.equals(ContainerProtocol.SHOW_CONTAINER)) {
                return handleShowContainer(packet);
            } else if (cmdBytes.equals(ContainerProtocol.HIDE_CONTAINER)) {
                return handleHideContainer(packet);
            } else if (cmdBytes.equals(ContainerProtocol.FOCUS_CONTAINER)) {
                return handleFocusContainer(packet);
            } else if (cmdBytes.equals(ContainerProtocol.QUERY_CONTAINER)) {
                return handleQueryContainer(packet);
            } else if (cmdBytes.equals(ContainerProtocol.LIST_CONTAINERS)) {
                return handleListContainers(packet);
            } else {
                System.err.println("[ContainerService] Unknown command: " + cmdBytes);
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unknown command: " + cmdBytes));
            }
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        System.err.println("[ContainerService] Unexpected stream channel from: " + fromPath);
    }
    
    // ===== COMMAND HANDLERS =====
    
    private CompletableFuture<Void> handleCreateContainer(RoutedPacket packet) {
        state.addState(ContainerServiceStates.CREATING_CONTAINER);
        
        NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
        
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
        
        System.out.println(String.format(
            "[ContainerService] Creating %s container: %s (owner: %s)",
            type, title, ownerPath
        ));
        
        // Generate container ID
        ContainerId containerId = ContainerId.generate();
        
        // Create container
        Container container = new Container(
            containerId,
            title,
            type,
            ownerPath,
            config != null ? config : new ContainerConfig(),
            uiRenderer
        );
        
        // Initialize container
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
                
                System.out.println("[ContainerService] Container created: " + containerId);
                
                // Reply with container ID
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                response.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
                response.put(Keys.PATH, container.getPath().toString());
                
                reply(packet, response.getNoteBytesObject());
                
                // Send event to owner
                sendEvent(ownerPath, ContainerProtocol.containerCreated(
                    containerId, container.getPath()
                ));
            })
            .exceptionally(ex -> {
                state.removeState(ContainerServiceStates.CREATING_CONTAINER);
                
                System.err.println("[ContainerService] Failed to create container: " + 
                    ex.getMessage());
                
                NoteBytesMap errorResponse = new NoteBytesMap();
                errorResponse.put(Keys.STATUS, ProtocolMesssages.ERROR);
                errorResponse.put(Keys.MSG, new NoteBytes(ex.getMessage()));
                
                reply(packet, errorResponse.getNoteBytesObject());
                return null;
            });
    }
    
    private CompletableFuture<Void> handleDestroyContainer(RoutedPacket packet) {
        state.addState(ContainerServiceStates.DESTROYING_CONTAINER);
        
        NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
        ContainerId containerId = ContainerId.fromNoteBytes(
            msg.get(Keys.CONTAINER_ID)
        );
        
        Container container = containers.get(containerId);
        if (container == null) {
            state.removeState(ContainerServiceStates.DESTROYING_CONTAINER);
            return replyError(packet, "Container not found");
        }
        
        System.out.println("[ContainerService] Destroying container: " + containerId);
        
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
                
                System.out.println("[ContainerService] Container destroyed: " + containerId);
                
                // Reply success
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                reply(packet, response.getNoteBytesObject());
                
                // Send event to owner
                sendEvent(container.getOwnerPath(), 
                    ContainerProtocol.containerClosed(containerId));
            });
    }
    
    private CompletableFuture<Void> handleUpdateContainer(RoutedPacket packet) {
        NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
        ContainerId containerId = ContainerId.fromNoteBytes(
            msg.get(Keys.CONTAINER_ID)
        );
        NoteBytesMap updates = msg.get(Keys.UPDATES).getAsNoteBytesMap();
        
        Container container = containers.get(containerId);
        if (container == null) {
            return replyError(packet, "Container not found");
        }
        
        state.addState(ContainerServiceStates.RENDERING);
        
        return container.update(updates)
            .thenRun(() -> {
                state.removeState(ContainerServiceStates.RENDERING);
                replySuccess(packet);
            })
            .exceptionally(ex -> {
                state.removeState(ContainerServiceStates.RENDERING);
                replyError(packet, ex.getMessage());
                return null;
            });
    }
    
    private CompletableFuture<Void> handleShowContainer(RoutedPacket packet) {
        NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
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
    
    private CompletableFuture<Void> handleHideContainer(RoutedPacket packet) {
        NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
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
    
    private CompletableFuture<Void> handleFocusContainer(RoutedPacket packet) {
        NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
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
                    ContainerProtocol.containerFocused(containerId));
            });
    }
    
    private CompletableFuture<Void> handleQueryContainer(RoutedPacket packet) {
        NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
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
        
        reply(packet, response.getNoteBytesObject());
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleListContainers(RoutedPacket packet) {
        List<ContainerInfo> infoList = containers.values().stream()
            .map(Container::getInfo)
            .toList();
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        response.put("count", infoList.size());
        response.put("containers", infoList.stream()
            .map(ContainerInfo::toNoteBytes)
            .toArray());
        
        reply(packet, response.getNoteBytesObject());
        
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
            emitTo(target, event.getNoteBytesObject());
        }
    }
    
    private void replySuccess(RoutedPacket packet) {
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        reply(packet, response.getNoteBytesObject());
    }
    
    private CompletableFuture<Void> replyError(RoutedPacket packet, String message) {
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.ERROR);
        response.put(Keys.MSG, new NoteBytes(message));
        reply(packet, response.getNoteBytesObject());
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
        
        System.out.println("[ContainerService] Shutting down, destroying " + 
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
                
                System.out.println("[ContainerService] Shutdown complete");
            });
    }
}