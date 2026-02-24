// AbstractUIRenderer.java
package io.netnotes.engine.ui.renderer;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.state.ConcurrentBitFlagStateMachine;
import io.netnotes.engine.state.BitFlagStateMachine.StateSnapshot;
import io.netnotes.engine.ui.SpatialPoint;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.containers.*;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AbstractUIRenderer - Base implementation for UIRenderer implementations
 * 
 * Provides:
 * - State machine management with proper lifecycle
 * - Container tracking (by ID and owner)
 * - Focus management
 * - Message routing infrastructure
 * - Common container operations
 * - State transition logging
 * 
 * Subclasses must implement:
 * - doInitialize() - renderer-specific initialization
 * - doShutdown() - renderer-specific cleanup
 * - doCreateContainer() - actual container creation
 * - handleRenderCommand() - renderer-specific rendering
 */
public abstract class Renderer <
    P extends SpatialPoint<P>,
    S extends SpatialRegion<P,S>,
    CLM extends ContainerLayoutManager<S,T>,
    CCFG extends ContainerConfig<S,CCFG>,
    T extends Container<P,S,CCFG,T>
> {

    @FunctionalInterface
    public interface UIReplyExec {
        void reply(RoutedPacket packet, NoteBytesReadOnly message);
    }


    private final NoteBytesReadOnly rendererId;
    
    // ===== STATE MANAGEMENT =====
    protected final ConcurrentBitFlagStateMachine state;
    protected final String name;
    
    // ===== CONTAINER MANAGEMENT =====
    protected final Map<ContainerId, T> containers = new ConcurrentHashMap<>();
    protected final Map<ContextPath, List<ContainerId>> ownerContainers = new ConcurrentHashMap<>();
    protected volatile ContainerId focusedContainerId = null;
    protected final CLM containerLayoutManager;
    // ===== MESSAGE DISPATCH =====
    protected final Map<NoteBytesReadOnly, RoutedMessageExecutor> msgMap = new ConcurrentHashMap<>();
    protected UIReplyExec replyExec;
    
    protected final SerializedVirtualExecutor rendererExecutor = 
        new SerializedVirtualExecutor();

    /**
     * Constructor
     * 
     * @param rendererName Name for this renderer (used in logging and state machine)
     */
    protected Renderer(String rendererName, NoteBytesReadOnly rendererId, CLM containerLayoutManager) {
        this.rendererId = rendererId;
        this.name = rendererName;
        this.state = new ConcurrentBitFlagStateMachine(rendererName + "-state");
        this.containerLayoutManager = containerLayoutManager;
        setupBaseStateTransitions();
        setupRendererStateTransitions();

        setupMessageHandlers();
    }

    public NoteBytesReadOnly getId(){
        return rendererId;
    }
    
    public String getName(){
        return name;
    }

    // ===== STATE TRANSITIONS =====
    
     private void setupBaseStateTransitions() {
        state.onStateAdded(RendererStates.INITIALIZING, (old, now, bit) -> 
            Log.logMsg("[" + name + "] Initializing..."));
        
        state.onStateAdded(RendererStates.READY, (old, now, bit) -> 
            Log.logMsg("[" + name + "] Ready"));
        
        state.onStateAdded(RendererStates.HAS_CONTAINERS, (old, now, bit) -> 
            Log.logMsg("[" + name + "] First container created"));
        
        state.onStateRemoved(RendererStates.HAS_CONTAINERS, (old, now, bit) -> 
            Log.logMsg("[" + name + "] No containers remaining"));
        
        state.onStateAdded(RendererStates.HAS_FOCUSED_CONTAINER, (old, now, bit) -> 
            Log.logMsg("[" + name + "] Container focused"));
        
        state.onStateRemoved(RendererStates.HAS_FOCUSED_CONTAINER, (old, now, bit) -> 
            Log.logMsg("[" + name + "] No container focused"));
        
        state.onStateAdded(RendererStates.SHUTTING_DOWN, (old, now, bit) -> 
            Log.logMsg("[" + name + "] Shutting down..."));
        
        state.onStateAdded(RendererStates.STOPPED, (old, now, bit) -> 
            Log.logMsg("[" + name + "] Stopped"));
        
        state.onStateAdded(RendererStates.ERROR, (old, now, bit) -> 
            Log.logError("[" + name + "] ERROR"));
    }

    protected abstract void setupRendererStateTransitions();
    
    // ===== MESSAGE HANDLERS SETUP =====
    
    private void setupMessageHandlers() {
        msgMap.put(ContainerCommands.CREATE_CONTAINER, this::handleCreateContainer);
        msgMap.put(ContainerCommands.DESTROY_CONTAINER, this::handleDestroyContainer);
        msgMap.put(ContainerCommands.SHOW_CONTAINER, this::handleShowContainer);
        msgMap.put(ContainerCommands.HIDE_CONTAINER, this::handleHideContainer);
        msgMap.put(ContainerCommands.FOCUS_CONTAINER, this::handleFocusContainer);
        msgMap.put(ContainerCommands.UPDATE_CONTAINER, this::handleUpdateContainer);
        msgMap.put(ContainerCommands.MAXIMIZE_CONTAINER, this::handleMaximizeContainer);
        msgMap.put(ContainerCommands.RESTORE_CONTAINER, this::handleRestoreContainer);
        msgMap.put(ContainerCommands.QUERY_CONTAINER, this::handleQueryContainer);
        msgMap.put(ContainerCommands.LIST_CONTAINERS, this::handleListContainers);
    }
    
    // ===== LIFECYCLE (Template Method Pattern) =====
    
    
    public final CompletableFuture<Void> initialize() {
        state.addState(RendererStates.INITIALIZING);
        
        return doInitialize()
            .thenRun(() -> {
                state.removeState(RendererStates.INITIALIZING);
                state.addState(RendererStates.READY);
            })
            .exceptionally(ex -> {
                state.removeState(RendererStates.INITIALIZING);
                state.addState(RendererStates.ERROR);
                throw new RuntimeException(ex);
            });
    }
    
    public final CompletableFuture<Void> shutdown() {

        state.addState(RendererStates.SHUTTING_DOWN);

        // Destroy all containers first
        List<CompletableFuture<Void>> futures = containers.values().stream()
            .map(T::destroyNow)
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenCompose(v -> doShutdown())
            .thenRun(() -> {
                containers.clear();
                ownerContainers.clear();
                focusedContainerId = null;
                
                // Update state
                state.removeState(RendererStates.HAS_CONTAINERS);
                state.removeState(RendererStates.HAS_FOCUSED_CONTAINER);
                state.removeState(RendererStates.HAS_VISIBLE_CONTAINERS);
                state.removeState(RendererStates.SHUTTING_DOWN);
                state.removeState(RendererStates.READY);
                state.addState(RendererStates.STOPPED);
                
                Log.logMsg("[" + name + "] Shutdown complete");
            })
            .exceptionally(ex -> {
                state.addState(RendererStates.ERROR);
                Log.logError("[" + name + "] Shutdown error: " + ex.getMessage());
                return null;
            });
    }
    
    /**
     * Subclass-specific initialization
     */
    protected abstract CompletableFuture<Void> doInitialize();
    
    /**
     * Subclass-specific shutdown
     */
    protected abstract CompletableFuture<Void> doShutdown();
    
    // ===== STATE QUERIES =====
    
    
    public final boolean isReady() {
        return state.hasState(RendererStates.READY) && 
               !state.hasState(RendererStates.ERROR) &&
               !state.hasState(RendererStates.SHUTTING_DOWN);
    }
    
    
    public abstract boolean isActive();
    
    public final ConcurrentBitFlagStateMachine getState() {
        return state;
    }
    
    // ===== CONTAINER MANAGEMENT =====
    
    
    public final T getContainer(ContainerId id) {
        return containers.get(id);
    }
    
    
    public final boolean hasContainer(ContainerId id) {
        return containers.containsKey(id);
    }
    
    
    public final List<T> getAllContainers() {
        return new ArrayList<>(containers.values());
    }
    
    
    public final List<T> getContainersByOwner(ContextPath ownerPath) {
        List<ContainerId> ids = ownerContainers.get(ownerPath);
        if (ids == null) {
            return List.of();
        }
        
        return ids.stream()
            .map(containers::get)
            .filter(Objects::nonNull)
            .toList();
    }
    
    
    public final int getContainerCount() {
        return containers.size();
    }
    
    
    public final T getFocusedContainer() {
        return focusedContainerId != null ? containers.get(focusedContainerId) : null;
    }

    public void onFocusGranted(T container){
      
        if(container != null){
            focusedContainerId = container.getId();
            state.addState(RendererStates.HAS_FOCUSED_CONTAINER);
        }
    }

    public void onFocusRevoked(T container){
      
        if(container != null && focusedContainerId != null && focusedContainerId.equals(container.getId())){
            focusedContainerId = null;
            state.removeState(RendererStates.HAS_FOCUSED_CONTAINER);
        }
    }

    /**
     * Remove a container from all base-class tracking and update renderer state.
     * Subclasses call this from onContainerDestroyed() instead of repeating bookkeeping.
     * After deregistration, calls onContainerUnregistered() as a narrow hook for
     * renderer-specific teardown (e.g. notifying the layout manager).
     */
    public final CompletableFuture<Void> removeContainer(ContainerId containerId) {
        return rendererExecutor.execute(() -> {
            Log.logMsg("[" + name + "] Removing container: " + containerId);

            T container = containers.remove(containerId);
            if (container == null) {
                Log.logError("[" + name + "] removeContainer: not found: " + containerId);
                return;
            }

            if (containers.isEmpty()) {
                state.removeState(RendererStates.HAS_CONTAINERS);
                state.removeState(RendererStates.HAS_VISIBLE_CONTAINERS);
            }

            if (container.getOwnerPath() != null) {
                List<ContainerId> ownerList = ownerContainers.get(container.getOwnerPath());
                if (ownerList != null) {
                    ownerList.remove(containerId);
                    if (ownerList.isEmpty()) {
                        ownerContainers.remove(container.getOwnerPath());
                    }
                }
            }

            if (containerId.equals(focusedContainerId)) {
                focusedContainerId = null;
                state.removeState(RendererStates.HAS_FOCUSED_CONTAINER);
            }

            onContainerUnregistered(container);
        });
    }

    /**
     * Hook called by removeContainer() after all base-class bookkeeping is done.
     * Subclasses override to notify the layout manager or perform renderer-specific cleanup.
     * Default is a no-op so renderers without layout managers work unchanged.
     */
    protected void onContainerUnregistered(T container) {}
    
    // ===== MESSAGE HANDLING =====
    
    
    public final CompletableFuture<Void> handleMessage(NoteBytesMap msg, RoutedPacket packet) {
        // State check
        StateSnapshot snapshot = state.getSnapshot();
        if (!RendererStates.canAcceptRequests(snapshot)) {
            String stateDesc = RendererStates.describe(snapshot);
            return CompletableFuture.failedFuture(
                new IllegalStateException("Renderer not ready (state: " + stateDesc + ")")
            );
        }
        
        NoteBytes cmdBytes = msg.get(Keys.CMD);
        
        if (cmdBytes == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("'cmd' required")
            );
        }
        
        // Dispatch commands
        RoutedMessageExecutor msgExec = msgMap.get(cmdBytes);
        if (msgExec != null) {
            // Re-entrant safe
            return rendererExecutor.execute(()->{
                msgExec.execute(msg, packet).join();
            });
        } else {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Unknown command: " + cmdBytes)
            );
        }
    }
    
    
    public final void setUIReplyExec(UIReplyExec exec) {
        this.replyExec = exec;
    }

    protected abstract CCFG createContainerConfig(NoteBytes config);
    protected abstract CCFG createContainerConfig();

    // ===== CONTAINER COMMAND HANDLERS =====
    
    private CompletableFuture<Void> handleCreateContainer(NoteBytesMap msg, RoutedPacket packet) {
        state.addState(RendererStates.CREATING_CONTAINER);
        Log.logMsg("[UIRenderer "+name+"] creating container...");
        try {
  
            // Parse common parameters
            NoteBytes containerIdBytes = msg.get(ContainerCommands.CONTAINER_ID);
            NoteBytes titleBytes = msg.get(Keys.TITLE);
            NoteBytes pathBytes = msg.get(Keys.PATH);
            NoteBytes configBytes = msg.get(Keys.CONFIG);
            NoteBytes rendererIdBytes = msg.get(ContainerCommands.RENDERER_ID);
            
            if (containerIdBytes == null) {
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("container_id required")
                );
            }
            
            if (rendererIdBytes == null) {
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("rendererId required")
                );
            }
            
            ContainerId containerId = ContainerId.fromNoteBytes(containerIdBytes);
            String title = titleBytes != null ? titleBytes.getAsString() : "Untitled";
           
            ContextPath ownerPath = pathBytes != null ? ContextPath.fromNoteBytes(pathBytes) : null;

            CCFG config = configBytes != null ? createContainerConfig(configBytes) : createContainerConfig();
    
            String rendererId = rendererIdBytes.getAsString();

            // Delegate to subclass
            return doCreateContainer(containerId, title, ownerPath, config, rendererId)
                .thenCompose(container -> {
                    // Track container
                    containers.put(containerId, container);
                    Log.logMsg("[UIRenderer "+name+"] tracking container: " + containerId);
                    // Track by owner
                    ownerContainers.computeIfAbsent(ownerPath, k -> new ArrayList<>())
                        .add(containerId);
                    
                    // Update state
                    if (containers.size() == 1) {
                        state.addState(RendererStates.HAS_CONTAINERS);
                    }
                    
                    return container.initialize()
                        .thenCompose(v->{
                            Log.logMsg("[UiRenderer] container initialized, executing onContainerCreated");
                            return onContainerCreated(container);
                        })
                        .thenAccept(response -> {
                            state.removeState(RendererStates.CREATING_CONTAINER);
                            reply(packet, response);
                        })
                        .thenRun(()->{
                            if (config.isFocused() || focusedContainerId == null) {
                                container.requestFocus();                            
                            }
                        });
                })
                .exceptionally((ex)->{
                    state.removeState(RendererStates.CREATING_CONTAINER);
                    String errorMsg = ex.getCause() != null ? 
                        ex.getCause().getMessage() : ex.getMessage();
                    
                    Log.logError("[" + name + "] Create container error: " + errorMsg);
                    replyError(packet, errorMsg);
                    return null;
                });
                
        } catch (Exception e) {
            state.removeState(RendererStates.CREATING_CONTAINER);
            return CompletableFuture.failedFuture(e);
        }
    }


    private CompletableFuture<Void> handleDestroyContainer(NoteBytesMap msg, RoutedPacket packet) {
        T container = getContainerFromMsg(msg);
        if (container == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Container not found")
            );
        }
        
        state.addState(RendererStates.DESTROYING_CONTAINER);
        
        return container.handleDestroyContainer(msg)
            .thenAccept(v -> replySuccess(packet))
            .exceptionally(ex -> {
                state.removeState(RendererStates.DESTROYING_CONTAINER);
                String errorMsg = ex.getCause() != null ? 
                    ex.getCause().getMessage() : ex.getMessage();
                
                Log.logError("[" + name + "] Destroy error: " + errorMsg);
                replyError(packet, errorMsg);
                
                return null;
            });
    }

    /**
        //Recommended execution:
        // Unregister
        containers.remove(container.getId());
        
        // Update state
        if (containers.isEmpty()) {
            state.removeState(RendererStates.HAS_CONTAINERS);
            state.removeState(RendererStates.HAS_VISIBLE_CONTAINERS);
        }
        
        // Remove from owner tracking
        List<ContainerId> ownerList = ownerContainers.get(container.getOwnerPath());
        if (ownerList != null) {
            ownerList.remove(container.getId());
            if (ownerList.isEmpty()) {
                ownerContainers.remove(container.getOwnerPath());
            }
        }
        
        // Clear focus if this was focused
        if (container.getId().equals(focusedContainerId)) {
            focusedContainerId = null;
            state.removeState(RendererStates.HAS_FOCUSED_CONTAINER);
            onFocusCleared();
        }
        
        state.removeState(RendererStates.DESTROYING_CONTAINER);
        
    */
    protected abstract CompletableFuture<Void> onContainerDestroyed(ContainerId containerId);
    
    private CompletableFuture<Void> handleShowContainer(NoteBytesMap msg, RoutedPacket packet) {
        T container = getContainerFromMsg(msg);
        if (container == null) {
            replyError(packet, "Container not found");
            return CompletableFuture.completedFuture(null);
        }
        
        return container.handleShowContainer(msg)
            .thenAccept(v -> replySuccess(packet))
            .exceptionally(ex -> {
                replyError(packet, ex.getMessage());
                return null;
            });
    }
    
    private CompletableFuture<Void> handleHideContainer(NoteBytesMap msg, RoutedPacket packet) {
        T container = getContainerFromMsg(msg);
        if (container == null) {
            replyError(packet, "Container not found");
            return CompletableFuture.completedFuture(null);
        }
        
        return container.handleHideContainer(msg)
            .thenAccept(v -> replySuccess(packet))
            .exceptionally(ex -> {
                replyError(packet, ex.getMessage());
                return null;
            });
    }
    
    protected CompletableFuture<Void> handleFocusContainer(NoteBytesMap msg, RoutedPacket packet) {
        T container = getContainerFromMsg(msg);
        if (container == null) {
            replyError(packet, "Container not found");
            return CompletableFuture.completedFuture(null);
        }
        
        return container.handleFocusContainer(msg)
            .thenAccept(v -> replySuccess(packet))
            .exceptionally(ex -> {
                replyError(packet, ex.getMessage());
                return null;
            });
    }

    private CompletableFuture<Void> handleUpdateContainer(NoteBytesMap msg, RoutedPacket packet) {
        T container = getContainerFromMsg(msg);
        if (container == null) {
            replyError(packet, "Container not found");
            return CompletableFuture.completedFuture(null);
        }

        return container.handleUpdateContainer(msg)
            .thenAccept(v -> replySuccess(packet))
            .exceptionally(ex -> {
                replyError(packet, ex.getMessage());
                return null;
            });
    }

    
    private CompletableFuture<Void> handleMaximizeContainer(NoteBytesMap msg, RoutedPacket packet) {
        T container = getContainerFromMsg(msg);
        if (container == null) {
            replyError(packet, "Container not found");
            return CompletableFuture.completedFuture(null);
        }

        return container.handleMaximizeContainer(msg);
    }
    
    private CompletableFuture<Void> handleRestoreContainer(NoteBytesMap msg, RoutedPacket packet) {
        T container = getContainerFromMsg(msg);
        if (container == null) {
            replyError(packet, "Container not found");
            return CompletableFuture.completedFuture(null);
        }
        
        return container.handleRestoreContainer(msg)
            .thenAccept(v -> replySuccess(packet))
            .exceptionally(ex -> {
                replyError(packet, ex.getMessage());
                return null;
            });
    }
    
    private CompletableFuture<Void> handleQueryContainer(NoteBytesMap msg, RoutedPacket packet) {
        T container = getContainerFromMsg(msg);
        if (container == null) {
            replyError(packet, "Container not found");
            return CompletableFuture.completedFuture(null);
        }

        NoteBytesObject obj = container.queryContainer();
        reply(packet, obj);
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleListContainers(NoteBytesMap msg, RoutedPacket packet) {
         
        NoteBytes[] constainersArray = containers.values().stream()
            .map(container -> container.getInfo())
            .map(info -> (NoteBytes) info.toNoteBytes())
            .toArray(NoteBytes[]::new);
    
        reply(packet, new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.STATUS, ProtocolMesssages.SUCCESS),
            new NoteBytesPair(Keys.ITEM_COUNT, constainersArray.length),
            new NoteBytesPair(Keys.ITEMS, new NoteBytesArrayReadOnly(constainersArray))
        }));
        
        return CompletableFuture.completedFuture(null);
    }
    
    protected T getContainerFromMsg(NoteBytesMap msg) {
        NoteBytes idBytes = msg.get(ContainerCommands.CONTAINER_ID);
        if (idBytes == null) return null;
        
        ContainerId id = ContainerId.fromNoteBytes(idBytes);
        return containers.get(id);
    }
    
    // ===== REPLY HELPERS =====
    
    private void reply(RoutedPacket packet, NoteBytesObject msg){
        replyExec.reply(packet, msg.readOnly());
    }

    protected final void reply(RoutedPacket packet, NoteBytesReadOnly msg) {
        if (replyExec != null) {
            replyExec.reply(packet, msg);
        }
    }
    
    protected final void replySuccess(RoutedPacket packet) {
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        reply(packet, response.toNoteBytes());
    }
    
    protected final void replyError(RoutedPacket packet, String errorMsg) {
       
        reply(packet, ProtocolObjects.getErrorObject(errorMsg));
    }
    
    // ===== ABSTRACT METHODS FOR SUBCLASSES =====
    
    /**
     * Create renderer-specific container instance
     */
    protected abstract CompletableFuture<T> doCreateContainer(
        ContainerId id,
        String title,
        ContextPath ownerPath,
        CCFG config,
        String rendererId
    );
    
    /**
     * Called after container is created and tracked
     * Return response data to include in CREATE_CONTAINER reply
     */
    protected abstract CompletableFuture<NoteBytesReadOnly> onContainerCreated( T container);
    

    /**
     * Get renderer description
     */
    public abstract String getDescription();
    
    /**
     * Check if this renderer can handle a stream from the given path
     * Used by RenderingService to route stream channels
     * 
     * @param fromPath Source path of the stream
     * @return true if this renderer manages a container for this path
     */
    public abstract boolean canHandleStreamFrom(ContextPath fromPath);


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
    public abstract void handleStreamChannel(StreamChannel channel, ContextPath fromPath);

    public abstract void handleEventStream(StreamChannel eventChannel, ContextPath fromPath);
}