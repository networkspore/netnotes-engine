package io.netnotes.engine.ui.renderer;


import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
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
import io.netnotes.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.ui.containers.Container;
import io.netnotes.engine.ui.containers.ContainerCommands;
import io.netnotes.engine.ui.containers.ContainerId;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RenderingService - Message router to UIRenderers
 * 
 * Responsibilities:
 * 1. Register and manage UIRenderers
 * 2. Route messages to appropriate renderer
 * 3. Forward stream channels to renderers
 * 4. Handle replies based on CompletableFuture results
 * 5. Provide convenience methods for renderer queries
 * 
 * Reply Pattern:
 * - AbstractUIRenderer returns CompletableFuture<Void>
 * - Success: future completes normally → reply SUCCESS
 * - Failure: future completes exceptionally → reply ERROR with message
 */
public class RenderingService extends FlowProcess {
    
    private final BitFlagStateMachine state;
    
    // ===== RENDERER MANAGEMENT =====
    private final Map<NoteBytesReadOnly, Renderer<?,?,?,?,?>> renderers = new ConcurrentHashMap<>();
    private final NoteBytesReadOnly systemDefaultRendererId;
    
    // ===== MESSAGE DISPATCH =====
    private final HashMap<NoteBytes, RoutedMessageExecutor> msgExecMap = new HashMap<>();
    
    /**
     * Constructor with system default renderer
     */
    public RenderingService(String name, Renderer<?,?,?,?,?> systemDefaultRenderer) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.state = new BitFlagStateMachine(name);
        
        // Register system default
        this.systemDefaultRendererId = systemDefaultRenderer.getId();
        this.renderers.put(systemDefaultRendererId, systemDefaultRenderer);
        
        

        setupExecutors();
        setupStateTransitions();
    }
    
    // ===== RENDERER REGISTRATION =====
    
    public void registerRenderer(Renderer<?,?,?,?,?> renderer) {
        if (renderer == null || renderer.getId() == null) {
            throw new IllegalArgumentException("rendererId and renderer required");
        }
        NoteBytesReadOnly rendererId = renderer.getId();

        if(renderers.containsValue(renderer)){
            Log.logMsg("[RenderingService.registerRenderer] renderer: "+renderer.getName() +":" + rendererId  + " exists, skipping registration" );
            return;
        }

        if(renderers.containsKey(renderer.getId())){
            throw new IllegalStateException("rendererId: " +rendererId +" already exists");
        }
        renderers.put(rendererId, renderer);
        
        Log.logMsg(String.format(
            "[RenderingService] Registered renderer '%s': %s",
            rendererId, renderer.getDescription()
        ));
    }
    
    
    public void unregisterRenderer(NoteBytes rendererId) {
        if (rendererId.equals(systemDefaultRendererId)) {
            throw new IllegalArgumentException("Cannot unregister system default renderer");
        }
        
        Renderer<?,?,?,?,?> renderer = renderers.get(rendererId);
        if (renderer != null && renderer.getContainerCount() > 0) {
            throw new IllegalStateException(
                "Cannot unregister renderer with active containers: " + rendererId
            );
        }
        
        renderers.remove(rendererId);
 
        
        Log.logMsg("[RenderingService] Unregistered renderer: " + rendererId);
    }
    
    /**
     * Get renderer ID for a container type using 3-tier strategy:
     * 1. Explicit rendererId (if provided and valid)
     * 2. Type-specific default (if configured)
     * 3. System default (always available)
     */
    public NoteBytes selectRendererId(NoteBytes rendererId) {
        // TIER 1: Explicit renderer
        if (rendererId != null && !rendererId.isEmpty()) {
            Renderer<?,?,?,?,?> renderer = renderers.get(rendererId);
            
            if (renderer == null) {
                throw new IllegalArgumentException("Renderer not found: " + rendererId);
            }
            
            return rendererId;
        }
        
        // TIER 3: System default
        return systemDefaultRendererId;
    }
    
    // ===== STATE MACHINE SETUP =====
    
    private void setupStateTransitions() {
        state.onStateAdded(RenderingServiceStates.INITIALIZING, (old, now, bit) -> {
            Log.logMsg("[RenderingService] Initializing...");
        });
        
        state.onStateAdded(RenderingServiceStates.READY, (old, now, bit) -> {
            Log.logMsg("[RenderingService] Ready - accepting requests");
        });
        
        state.onStateAdded(RenderingServiceStates.SHUTTING_DOWN, (old, now, bit) -> {
            Log.logMsg("[RenderingService] Shutting down...");
        });
        
        state.onStateAdded(RenderingServiceStates.STOPPED, (old, now, bit) -> {
            Log.logMsg("[RenderingService] Stopped");
        });
        
        state.onStateAdded(RenderingServiceStates.ERROR, (old, now, bit) -> {
            Log.logError("[RenderingService] ERROR");
        });
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(RenderingServiceStates.INITIALIZING);
        
        Log.logMsg("[RenderingService] Started at: " + contextPath);
        
        // Check if default renderer is active
        Renderer<?,?,?,?,?> defaultRenderer = renderers.get(systemDefaultRendererId);
        if (defaultRenderer != null && defaultRenderer.isActive()) {
            Log.logMsg("[RenderingService] Default renderer active");
            state.addState(RenderingServiceStates.UI_RENDERER_ACTIVE);
        }
        
        state.removeState(RenderingServiceStates.INITIALIZING);
        state.addState(RenderingServiceStates.READY);
        state.addState(RenderingServiceStates.ACCEPTING_REQUESTS);
        
        Log.logMsg("[RenderingService] Initialization complete, service running");
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== MESSAGE DISPATCH =====
    
    private void setupExecutors() {
        this.renderers.get(systemDefaultRendererId).setUIReplyExec((packet, msg)->{
            reply(packet, msg);
        });
        // All commands are forwarded to renderer
        msgExecMap.put(ContainerCommands.CREATE_CONTAINER, this::handleContainerCmd);
        msgExecMap.put(ContainerCommands.CREATE_CONTAINER, this::handleContainerCmd);
        msgExecMap.put(ContainerCommands.DESTROY_CONTAINER, this::handleContainerCmd);
        msgExecMap.put(ContainerCommands.SHOW_CONTAINER, this::handleContainerCmd);
        msgExecMap.put(ContainerCommands.HIDE_CONTAINER, this::handleContainerCmd);
        msgExecMap.put(ContainerCommands.FOCUS_CONTAINER, this::handleContainerCmd);
        msgExecMap.put(ContainerCommands.UPDATE_CONTAINER, this::handleContainerCmd);
        msgExecMap.put(ContainerCommands.MAXIMIZE_CONTAINER, this::handleContainerCmd);
        msgExecMap.put(ContainerCommands.RESTORE_CONTAINER, this::handleContainerCmd);
        msgExecMap.put(ContainerCommands.QUERY_CONTAINER, this::handleContainerCmd);

        msgExecMap.put(ContainerCommands.LIST_CONTAINERS, this::handleListContainers);
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        if (!RenderingServiceStates.canAcceptRequests(state)) {
            reply(packet, ProtocolObjects.getErrorObject("Service not accepting requests"));
            return CompletableFuture.completedFuture(null);
        }

        NoteBytesReadOnly payload = packet.getPayload();
        if(payload == null || payload.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
            String msg = "[RenderingService] Invalid payload";
            Log.logMsg(msg);
            reply(packet, ProtocolObjects.getErrorObject(msg));
            return CompletableFuture.completedFuture(null);
        }

        NoteBytesMap msg = payload.getAsNoteBytesMap();
        
        NoteBytes rendererId = msg.get(ContainerCommands.RENDERER_ID);
        
        if (rendererId == null) {
            NoteBytes cmdBytes = msg.get(Keys.CMD);
            RoutedMessageExecutor msgExec = cmdBytes  != null ? msgExecMap.get(cmdBytes) : null;
            if(msgExec != null){
                return msgExec.execute(msg, packet);
            }else{
                String msg1 = "[RenderingService] RendererId required, for this type of command";
                Log.logMsg(msg1);
                reply(packet, ProtocolObjects.getErrorObject(msg1));
                return CompletableFuture.completedFuture(null);
            }
        } else {
            
            return forwardToRenderer(rendererId, msg, packet);
        }
        
    }

    private CompletableFuture<Void> handleContainerCmd(NoteBytesMap msg, RoutedPacket packet) {


        NoteBytes rendererIdBytes = msg.get(ContainerCommands.RENDERER_ID);
        
        NoteBytes rendererId = selectRendererId(rendererIdBytes);
        

        Renderer<?,?,?,?,?> renderer = rendererId != null ? renderers.get(rendererId) : null;
        
        if (renderer == null) {
            reply(packet, ProtocolObjects.getErrorObject("Renderer not found: " + rendererId));
            return CompletableFuture.completedFuture(null);
        }
        Log.logMsg("[RenderingService] fording containerCmd to renderer");
        // Forward to renderer and handle result
        return renderer.handleMessage(msg, packet)
            .exceptionally(ex -> {
                // Failure - reply with ERROR
                String errorMsg = ex.getCause() != null ? 
                    ex.getCause().getMessage() : ex.getMessage();
                
                Log.logError("[RenderingService] Renderer error: " + errorMsg);
                reply(packet, ProtocolObjects.getErrorObject(errorMsg));
                
                return null;
            });

    }


    private CompletableFuture<Void> handleListContainers(NoteBytesMap msg, RoutedPacket packet) {

        NoteBytes[] containers = getAllContainers()
            .stream()
            .map(Container::getInfo)
            .map(info -> (NoteBytes) info.toNoteBytes())
            .toArray(NoteBytes[]::new);
                    
        reply(packet, new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.STATUS, ProtocolMesssages.SUCCESS),
            new NoteBytesPair(Keys.ITEM_COUNT, containers.length),
            new NoteBytesPair(Keys.ITEMS, new NoteBytesArrayReadOnly(containers))
        }));
        
        return CompletableFuture.completedFuture(null);

    }
    
    /**
     * Forward message to appropriate renderer and handle reply
     * 
     * Pattern:
     * 1. Route to correct renderer
     * 2. Call renderer.handleMessage() → returns CompletableFuture<Void>
     * 3. On success: reply SUCCESS
     * 4. On failure: reply ERROR with exception message
     */
    private CompletableFuture<Void> forwardToRenderer(NoteBytes rendererId, NoteBytesMap msg, RoutedPacket packet) {
     
        Renderer<?,?,?,?,?> renderer = renderers.get(rendererId);
        
        if (renderer == null) {
            reply(packet, ProtocolObjects.getErrorObject("Renderer not found: " + rendererId));
            return CompletableFuture.completedFuture(null);
        }
        
        // Forward to renderer and handle result
        return renderer.handleMessage(msg, packet)
            .exceptionally(ex -> {
                // Failure - reply with ERROR
                String errorMsg = ex.getCause() != null ? 
                    ex.getCause().getMessage() : ex.getMessage();
                
                Log.logError("[RenderingService] Renderer error: " + errorMsg);
                reply(packet, ProtocolObjects.getErrorObject(errorMsg));
                
                return null;
            });
       
    }
    
    /**
     * Find which renderer owns a container
   
    private NoteBytes findRendererForContainer(ContainerId containerId) {
        for (Map.Entry<NoteBytesReadOnly, AbstractUIRenderer> entry : renderers.entrySet()) {
            if (entry.getValue().hasContainer(containerId)) {
                return entry.getKey();
            }
        }
        return null;
    }  */
    
    // ===== STREAM CHANNEL HANDLING =====
    
    /**
     * Forward stream channel to appropriate renderer
     * Renderer will handle finding the target container
     */
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {

        if (fromPath == null) {
            throw new IllegalStateException("No source path provided");
        }
        Log.logMsg("[RenderingService] Stream channel received from: " + fromPath);
        // Find which renderer should handle this stream
        // Try each renderer until one accepts it
        boolean handled = false;
        
        for (Map.Entry<NoteBytesReadOnly, Renderer<?,?,?,?,?>> entry : renderers.entrySet()) {
            Renderer<?,?,?,?,?> renderer = entry.getValue();
            
            if (renderer.canHandleStreamFrom(fromPath)) {
                Log.logMsg("[RenderingService] Routing stream to renderer: " + entry.getKey());
                renderer.handleStreamChannel(channel, fromPath);
                // Request event stream back via service
                requestStreamChannel(fromPath)
                    .thenAccept(eventChannel -> {
                        Log.logMsg("[ConsoleUIRenderer] Event stream established, routing to container");
                        renderer.handleEventStream(eventChannel, fromPath);
                    })
                    .exceptionally(ex -> {
                        Log.logError("[ConsoleUIRenderer] Failed to establish event stream: " + 
                            ex.getMessage());
                        return null;
                    });
                handled = true;
                break;
            }
        }
        
        if (!handled) {
            Log.logError("[RenderingService] No renderer can handle stream from: " + fromPath);
            channel.getReadyFuture().completeExceptionally(
                new IllegalStateException("No renderer found for path: " + fromPath)
            );
        }
    }
    
    // ===== CONVENIENCE METHODS =====
    
    /**
     * Get renderer by ID
     */
    public Renderer<?,?,?,?,?> getRenderer(NoteBytesReadOnly rendererId) {
        return renderers.get(rendererId);
    }
    
    /**
     * Get system default renderer
     */
    public Renderer<?,?,?,?,?> getSystemDefaultRenderer() {
        return renderers.get(systemDefaultRendererId);
    }
    
    /**
     * Get all renderer IDs
     */
    public List<NoteBytesReadOnly> getRendererIds() {
        return List.copyOf(renderers.keySet());
    }
    
    /**
     * Get renderer info
     */
    public Map<NoteBytesReadOnly, String> getRendererInfo() {
        Map<NoteBytesReadOnly, String> info = new HashMap<>();
        renderers.forEach((id, renderer) -> 
            info.put(id, renderer.getDescription())
        );
        return info;
    }
    
   
    
    /**
     * Get total container count across all renderers
     */
    public int getTotalContainerCount() {
        return renderers.values().stream()
            .mapToInt(Renderer::getContainerCount)
            .sum();
    }
    
    /**
     * Get all containers across all renderers
     */
    public List<Container<?,?,?,?>> getAllContainers() {
        return renderers.values().stream()
            .<Container<?,?,?,?>>flatMap(renderer -> renderer.getAllContainers().stream())
            .toList();
    }

    /**
     * Get visible containers
     */
    public List<Container<?,?,?,?>> getVisibleContainers() {
        return renderers.values().stream()
            .<Container<?,?,?,?>>flatMap(renderer -> renderer.getAllContainers().stream())
            .filter(Container::isVisible)
            .toList();
    }
    
    /**
     * Get containers by owner across all renderers
     */
    public List<Container<?,?,?,?>> getContainersByOwner(ContextPath ownerPath) {
        return renderers.values().stream()
            .<Container<?,?,?,?>>flatMap(renderer -> renderer.getContainersByOwner(ownerPath).stream())
            .toList();
    }
    
    /**
     * Find container by ID across all renderers
     */
    public Container<?,?,?,?> findContainer(ContainerId containerId) {
        for (Renderer<?,?,?,?,?> renderer : renderers.values()) {
            Container<?,?,?,?> container = renderer.getContainer(containerId);
            if (container != null) {
                return container;
            }
        }
        return null;
    }
    
    // ===== SHUTDOWN =====
    
    public CompletableFuture<Void> shutdown() {
        state.addState(RenderingServiceStates.SHUTTING_DOWN);
        state.removeState(RenderingServiceStates.ACCEPTING_REQUESTS);
        
        Log.logMsg("[RenderingService] Shutting down all renderers...");
        
        // Shutdown all renderers
        List<CompletableFuture<Void>> futures = renderers.values().stream()
            .map(Renderer::shutdown)
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                renderers.clear();
                
                state.removeState(RenderingServiceStates.SHUTTING_DOWN);
                state.removeState(RenderingServiceStates.READY);
                state.addState(RenderingServiceStates.STOPPED);
                
                Log.logMsg("[RenderingService] Shutdown complete");
            });
    }
    
    public boolean isOperational() {
        return RenderingServiceStates.isOperational(state);
    }
    
    public BitFlagStateMachine getState() {
        return state;
    }
}
