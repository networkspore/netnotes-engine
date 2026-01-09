package io.netnotes.engine.core.system.control.containers;

import java.io.IOException;
import java.io.PipedInputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.system.control.ui.BatchBuilder;
import io.netnotes.engine.core.system.control.ui.RenderElement;
import io.netnotes.engine.core.system.control.ui.RenderState;
import io.netnotes.engine.core.system.control.ui.Renderable;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;
import io.netnotes.engine.noteBytes.NoteBoolean;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * ContainerHandle - Base container with generic rendering and builder support
 * 
 * @param <B> BatchBuilder type for this container
 * @param <E> RenderElement type for this container
 * @param <H> The concrete handle type (self-reference for builder)
 * @param <BLD> The concrete builder type
 */
public abstract class ContainerHandle
<
    B extends BatchBuilder, 
    E extends RenderElement<B>,
    R extends Renderable<B, E>,
    H extends ContainerHandle<B, E, R, H, BLD>,
    BLD extends ContainerHandle.Builder<B, E, R, H, BLD>
> extends FlowProcess {
    
    // ===== CREATION PARAMETERS (set at construction) =====
    protected final ContainerId containerId;
    private final ContainerType containerType;
    private final ContainerConfig containerConfig;
    private final ContextPath renderingServicePath;
    private final String title;
    private final BLD builder;
    // ===== RUNTIME STATE =====
  
    protected volatile R currentRenderable = null;
    
    protected NoteBytesReadOnly rendererId = null;

    // Stream TO Container (for render commands)
    protected StreamChannel renderStream;
    protected NoteBytesWriter renderWriter;
    private volatile boolean renderReadyCache = false;
    protected final AtomicLong renderGeneration = new AtomicLong();

    // Stream FROM Container (for events)
    protected StreamChannel eventChannel = null;
    protected CompletableFuture<Void> eventStreamReadyFuture = new CompletableFuture<>();
    protected Consumer<NoteBytes> onContainerEvent = null;

    // MsgMap allows commands to be sent to the container handle
    protected final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_msgMap = new ConcurrentHashMap<>();
    protected final EventHandlerRegistry eventHandlerRegistry = new EventHandlerRegistry();


    
    //<NoteMessaging.ItemTypes, id>
    private Map<NoteBytesReadOnly, String> defaultEventHandlers = new ConcurrentHashMap<>();
    private Consumer<H> renderRequester;
    protected final BitFlagStateMachine stateMachine;

    // Event map allows events from the Container to reach the handle
    protected volatile int initialWidth;
	protected volatile int initialHeight;
    /**
     * Private constructor - use Builder
     */
    protected ContainerHandle(BLD builder) {
        super(builder.name, ProcessType.BIDIRECTIONAL);
        this.containerId = ContainerId.generate();  // Always generate new ID

        this.title = builder.title != null ? builder.title : builder.name;
        this.containerType = builder.containerType;
        this.containerConfig = builder.containerConfig;
        this.renderingServicePath = builder.renderingServicePath;
        this.builder = builder;
        this.stateMachine = new BitFlagStateMachine("ContainerHandle:" + containerId);
        setupBaseStateHandler();
        setupStateTransitions();
    }

    private void setupBaseStateHandler(){
        stateMachine.onStateAdded(Container.STATE_ACTIVE, (old, now, bit) -> {
             Log.logMsg(String.format("[ContainerHandle:%s] STATE_ACTIVE added - checking render: isDirty=%s, hasRenderable=%s, streamReady=%s",
                containerId, 
                isDirty(), 
                currentRenderable != null,
                isRenderStreamReady()
            ));

            invalidate();
        });


        stateMachine.onStateRemoved(Container.STATE_ACTIVE, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] No longer active");
        });

        stateMachine.onStateAdded(Container.STATE_DESTROYED, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] destroyed");
            if(stateMachine.hasState(Container.STATE_DESTROYING)){
                stateMachine.removeState(Container.STATE_DESTROYING);
            }   
        });

        stateMachine.onStateAdded(Container.STATE_DESTROYING, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] destroyed");
            if(stateMachine.hasState(Container.STATE_DESTROYED)){
                stateMachine.removeState(Container.STATE_DESTROYING);
            }   
        });

        stateMachine.onStateAdded(Container.STATE_VISIBLE, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Now visible");
        });
        
        // HIDDEN: Container is hidden
        stateMachine.onStateAdded(Container.STATE_HIDDEN, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Now hidden");
        });
        
        // FOCUSED: Container has input focus
        stateMachine.onStateAdded(Container.STATE_FOCUSED, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Now focused");
        });
        
        stateMachine.onStateRemoved(Container.STATE_FOCUSED, (old, now, bit) -> {
            Log.logMsg("[ContainerHandle:" + containerId + "] Focus lost");
        });
        
    }

    protected abstract void setupStateTransitions();

   
    public void setOnContainerEvent(Consumer<NoteBytes> onContainerEvent) {
        this.onContainerEvent = onContainerEvent;
    }

    public void setOnRenderRequest(Consumer<H> onRenderRequest){
        this.renderRequester = onRenderRequest;
    }  
    public BitFlagStateMachine getStateMachine() {
        return stateMachine;
    }

    public BitFlagStateMachine.StateSnapshot getStateSnapshot() {
        return stateMachine.getSnapshot();
    }

     /**
     * Check if container is visible (server-confirmed)
     */
    public boolean isContainerVisible() {
        return stateMachine.hasState(Container.STATE_VISIBLE);
    }
    
    /**
     * Check if container is hidden
     */
    public boolean isContainerHidden() {
        return stateMachine.hasState(Container.STATE_HIDDEN);
    }
    
    /**
     * Check if container is focused
     */
    public boolean isContainerFocused() {
        return stateMachine.hasState(Container.STATE_FOCUSED);
    }
    
    /**
     * Check if container is active (ready to render)
     */
    public boolean isContainerActive() {
        return stateMachine.hasState(Container.STATE_ACTIVE);
    }
    
    public boolean isDestroyed(){
        return stateMachine.hasState(Container.STATE_DESTROYED);
    }
    

    /**
     * Check if container is ready to render
     * 
     * All conditions that must be true for rendering:
     * - Has a renderable
     * - Renderable needs render
     * - Container is active (STATE_ACTIVE)
     * - Render stream is ready
     * 
     * @return true if ready to render
     */
    public boolean isReadyToRender() {
        // Must have renderable
        if (currentRenderable == null || !currentRenderable.needsRender()) {
            return false;
        }

        // Must be active
        if (!stateMachine.hasState(Container.STATE_ACTIVE)) {
            return false;
        }
        
        // Stream must be ready
        if (!isRenderStreamReady()) {
            return false;
        }
        
        return true;
    }
   



    /**
     * Builder for ContainerHandle
     */
    /**
     * Base Builder with self-referential generics
     * 
     * @param <B> BatchBuilder type
     * @param <E> RenderElement type
     * @param <H> Handle type being built
     * @param <BLD> Builder type (self-reference)
     */
    public static abstract class Builder<
        B extends BatchBuilder,
        E extends RenderElement<B>,
        R extends Renderable<B, E>,
        H extends ContainerHandle<B, E, R, H, BLD>,
        BLD extends Builder<B, E, R, H, BLD>
    > {
        public String name;
        public String title;
        public ContainerType containerType;
        public NoteBytes rendererId = null;
        public ContainerConfig containerConfig = new ContainerConfig();
        public ContextPath renderingServicePath = CoreConstants.RENDERING_SERVICE_PATH;
        public boolean autoFocus = true;
        
        protected Builder(String name, ContainerType containerType) {
            this.name = name;
            this.title = name;
            this.containerType = containerType;
        }

        protected Builder(ContainerType containerType) {
            this.containerType = containerType;
        }
        
        /**
         * Return self for method chaining
         * Subclasses override to return their concrete builder type
         */
        @SuppressWarnings("unchecked")
        protected BLD self() {
            return (BLD) this;
        }
        
        /**
         * Set the process name
         */
        public BLD name(String name) {
            this.name = name;
            return self();
        }
        
        /**
         * Set the display title
         */
        public BLD title(String title) {
            this.title = title;
            return self();
        }
        
        /**
         * Set the container type
         */
        public BLD type(ContainerType type) {
            this.containerType = type;
            return self();
        }
        
        /**
         * Set the container configuration
         */
        public BLD config(ContainerConfig config) {
            this.containerConfig = config;
            return self();
        }
        
        /**
         * Set the rendering service path
         */
        public BLD renderingService(ContextPath path) {
            this.renderingServicePath = path;
            return self();
        }
        
        /**
         * Set renderer ID
         */
        public BLD render(NoteBytes rendererId) {
            this.rendererId = rendererId;
            return self();
        }
        
        /**
         * Set auto-focus
         */
        public BLD autoFocus(boolean autoFocus) {
            this.autoFocus = autoFocus;
            return self();
        }
        
        /**
         * Build the handle
         * Subclasses must implement to create their specific handle type
         */
        public abstract H build();
        
        /**
         * Validate common builder state
         */
        protected void validate() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("name is required");
            }
            if (containerType == null) {
                throw new IllegalStateException("containerType is required");
            }
        }
    }


    protected Map<NoteBytesReadOnly, RoutedMessageExecutor> getRoutedMsgMap() {
        return m_msgMap;
    }

    public NoteBytes getRendererId(){
        return rendererId;
    }
    

  
    @Override
    public CompletableFuture<Void> run() {
        Log.logMsg("[ContainerHandle] Started, auto-creating container: " + containerId);
        
        registry.connect(contextPath, renderingServicePath);
        registry.connect(renderingServicePath, contextPath);

        // Build CREATE_CONTAINER command with all our parameters
        NoteBytesMap createCmd = new NoteBytesMap();
        createCmd.put(Keys.CMD, ContainerCommands.CREATE_CONTAINER);
        createCmd.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        createCmd.put(Keys.TITLE, new NoteBytes(title));
        createCmd.put(Keys.TYPE, new NoteBytes(containerType.name()));
        createCmd.put(Keys.PATH, getParentPath().toNoteBytes());
        createCmd.put(Keys.CONFIG, containerConfig.toNoteBytes());
        createCmd.put(ContainerCommands.AUTO_FOCUS, builder.autoFocus ? NoteBoolean.TRUE : NoteBoolean.FALSE);
        
        if(builder.rendererId != null){
            createCmd.put(ContainerCommands.RENDERER_ID, builder.rendererId);
        }
        
        // Send CREATE_CONTAINER to RenderingService
        return request(renderingServicePath, createCmd.toNoteBytesReadOnly(), Duration.ofMillis(500))
            .thenCompose(response -> {
                // Verify creation succeeded
                NoteBytesMap responseMap = response.getPayload().getAsNoteBytesMap();
                NoteBytesReadOnly status = responseMap.getReadOnly(Keys.STATUS);
                
                if (status == null || !status.equals(ProtocolMesssages.SUCCESS)) {
                    String errorMsg = ProtocolObjects.getErrMsg(responseMap);
                    throw new RuntimeException("Container creation failed: " + errorMsg);
                }
                NoteBytes rendererId = responseMap.get(ContainerCommands.RENDERER_ID);
                NoteBytes widthBytes = responseMap.get(Keys.WIDTH);
                NoteBytes heightBytes = responseMap.get(Keys.HEIGHT);
                
                this.rendererId = rendererId.readOnly();
                if(widthBytes != null){
                    initialWidth = widthBytes.getAsInt();
                }
                if(heightBytes != null){
                    initialHeight = heightBytes.getAsInt();
                }
                Log.logMsg("[ContainerHandle] Container created successfully: " + containerId);
                
                // Request render stream TO RenderingService
                return requestStreamChannel(renderingServicePath);
            })
            .thenAccept(channel -> {
                Log.logMsg("[ContainerHandle] Render stream established");
                this.renderStream = channel;
                this.renderWriter = new NoteBytesWriter(
                    channel.getChannelStream()
                );
            })
            .exceptionally(ex -> {
                Log.logError("[ContainerHandle] Initialization failed: " + ex.getMessage());
                stateMachine.setState(Container.STATE_DESTROYED);
                return null;
            });
    }

    public int getInitialWidth() {
		return initialWidth;
	}

	public int getInitialHeight() {
		return initialHeight;
	}

    /**
     * Get next generation ID
     * Call this when starting a new screen/state
     */
    public long nextRenderGeneration() {
        long gen = renderGeneration.incrementAndGet();
        Log.logMsg("[ContainerHandle] New render generation: " + gen);
        return gen;
    }

    /**
     * Get render generation
     */
    public AtomicLong getRenderGeneration() {
        return renderGeneration;
    }

    /**
     * Get current generation
     */
    public long getCurrentRenderGeneration() {
        return renderGeneration.get();
    }

    /**
     * Check if generation is still current
     */
    public boolean isRenderGenerationCurrent(long generation) {
        return renderGeneration.get() == generation;
    }
   
    /**
     * Override onStop to cleanup IODaemon connection
     */
    @Override
    public void onStop() {
        Log.logMsg("[ContainerHandle:" + getId() + "] Stopped for container: " + containerId);
        stateMachine.setState(Container.STATE_DESTROYED);
        
        // Close render stream
        if (renderStream != null) {
            try {
                renderStream.close();
            } catch (IOException e) {
                Log.logError("[ContainerHandle:" + getId() + 
                    "] Error closing render stream: " + e.getMessage());
            }
        }

        defaultEventHandlers.clear();
        
     
        super.onStop();
    }

    public CompletableFuture<Void> getEventStreamReadyFuture() { 
        return eventStreamReadyFuture; 
    }

    /**
     * Handle incoming messages (container events from RenderingService)
     */
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        NoteBytesMap message = packet.getPayload().getAsNoteBytesMap();
        NoteBytesReadOnly cmd = message.getReadOnly("cmd");
        
        if (cmd == null) {
            Log.logError("[ContainerHandle] No cmd in message");
            return CompletableFuture.completedFuture(null);
        }
        
        RoutedMessageExecutor msgExec = m_msgMap.get(cmd);
        
        if (msgExec != null) {
            return msgExec.execute(message, packet);
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        if(fromPath == null){
            throw new NullPointerException("[ContainerHandle] handleStreamChannel from path is null");
        }
        
        if (fromPath.equals(renderingServicePath)) {
            Log.logMsg("[ContainerHandle] Event stream received");
        
            this.eventChannel = channel;
            Log.logMsg("[ContainerHandle] Event stream read thread starting");
            // Setup reader on virtual thread
            VirtualExecutors.getVirtualExecutor().execute(() -> {
                Log.logMsg("[ContainerHandle] Event stream read thread started");
                try (
                    NoteBytesReader reader = new NoteBytesReader(
                        new PipedInputStream(channel.getChannelStream(),  StreamUtils.PIPE_BUFFER_SIZE)
                    );
                ) {
                    // Signal ready
                    channel.getReadyFuture().complete(null);
                    eventStreamReadyFuture.complete(null);
                    Log.logMsg("[ContainerHandle] Event stream reader active");
                    
                    // Read and dispatch events
                    NoteBytes nextBytes = reader.nextNoteBytes();
                    
                    while (nextBytes != null && isAlive()) {
                        if (nextBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                            processRoutedEvent(nextBytes);
                        }
                        nextBytes = reader.nextNoteBytes();
                    }
                    
                    Log.logMsg("[ContainerHandle] Event stream reader stopped");
                } catch (IOException e) {
                    Log.logError("[ContainerHandle] Event stream error: " + e.getMessage());
                    throw new CompletionException(e);
                }
            });
            
        }
    }
    

    protected void processRoutedEvent(NoteBytes eventBytes) {
        try{
            Log.logNoteBytes("[ContainerHandle.processRoutedEvent]", eventBytes);

            if (onContainerEvent != null) {
                onContainerEvent.accept(eventBytes);
            } else {
                Log.logMsg("[ContainerHandle] No event handler set, event ignored");
            }
        } catch(Exception ex){
            Log.logError("[ContainerHandle] Failed to process routed event: " + ex.getMessage());
        }
    }
    
    // ===== CONTAINER OPERATIONS =====
 
    /**
     * Show container (unhide/restore)
     */
    public CompletableFuture<Void> show() {
        if(rendererId == null){
            return CompletableFuture.failedFuture(new IllegalStateException( "[ContainerHandle] container is not yet created / rendererId is null"));
        }
        NoteBytesMap msg = ContainerCommands.showContainer(containerId, rendererId);
        return sendToService(msg);
    }
    
    /**
     * Hide container (minimize)
     */
    public CompletableFuture<Void> hide() {
        if(rendererId == null){
            return CompletableFuture.failedFuture(new IllegalStateException( "[ContainerHandle] container is not yet created / rendererId is null"));
        }
        NoteBytesMap msg = ContainerCommands.hideContainer(containerId, rendererId);
        return sendToService(msg);
    }
    
    /**
     * Focus container (bring to front)
     */
    public CompletableFuture<Void> focus() {
         if(rendererId == null){
            return CompletableFuture.failedFuture(new IllegalStateException( "[ContainerHandle] container is not yet created / rendererId is null"));
        }
        NoteBytesMap msg = ContainerCommands.focusContainer(containerId, rendererId);
        return sendToService(msg);
    }
    
    /**
     * Maximize container
     */
    public CompletableFuture<Void> maximize() {
         if(rendererId == null){
            return CompletableFuture.failedFuture(new IllegalStateException( "[ContainerHandle] container is not yet created / rendererId is null"));
        }
        NoteBytesMap msg = ContainerCommands.maximizeContainer(containerId, rendererId);
        return sendToService(msg);
    }
    
    /**
     * Restore container (un-maximize)
     */
    public CompletableFuture<Void> restore() {
         if(rendererId == null){
            return CompletableFuture.failedFuture(new IllegalStateException( "[ContainerHandle] container is not yet created / rendererId is null"));
        }
        NoteBytesMap msg = ContainerCommands.restoreContainer(containerId, rendererId);
        return sendToService(msg);
    }
    
    

    /**
     * Destroy container
     */
    public CompletableFuture<Void> destroy() {
        if (isDestroyed()) {
            return CompletableFuture.completedFuture(null);
        }
        if(rendererId == null){
            return CompletableFuture.failedFuture(new IllegalStateException( "[ContainerHandle] container is not yet created / rendererId is null"));
        }
        NoteBytesMap msg = ContainerCommands.destroyContainer(containerId, rendererId);
        if(!isDestroying()){
            return sendToService(msg)
                .thenRun(() -> {
                    // Self-cleanup after destroy
                    if (registry != null) {
                        registry.unregisterProcess(contextPath);
                    }
                });
        }else{
            if (registry != null) {
                registry.unregisterProcess(contextPath);
            }
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * Query container info
     */
    public CompletableFuture<RoutedPacket> queryContainer() {
        if(rendererId == null){
            return CompletableFuture.failedFuture(new IllegalStateException( "[ContainerHandle] container is not yet created / rendererId is null"));
        }
        NoteBytesMap msg = ContainerCommands.queryContainer(containerId, rendererId);
        return request(renderingServicePath, msg.toNoteBytesReadOnly(), 
            Duration.ofSeconds(1));
    }
    
    // ===== RENDER COMMAND SENDING =====
    


   /**
    * Send render command with generation check
    * 
    * @param command Render command to send
    * @param generation Checks generation before writing
    * @return
    */
    protected CompletableFuture<Void> sendRenderCommand(NoteBytesMap command, long generation) {
        return CompletableFuture.runAsync(() -> {
            Log.logNoteBytes("[ContainerHandle.sendRenderCommand]", command);
            // GENERATION CHECK - prevents stale renders
            if (!isRenderGenerationCurrent(generation)) {
                return;
            }
            // Check if destroyed
            if (isDestroyed()) {
                throw new CompletionException(
                    new IllegalStateException("Container already destroyed")
                );
            }
            
            // Check if stream ready
            if (!isRenderStreamReady()) {
                throw new CompletionException(
                    new IllegalStateException("Render stream not initialized")
                );
            }
            
            // Check if stream active
            if (!renderStream.isActive() || renderStream.isClosed()) {
                Log.logMsg("[ContainerHandle] Render stream is closed, skipping");
                return;
            }
            
            
            
            try {
                NoteBytes noteBytes = command.toNoteBytes();
                renderWriter.write(noteBytes);
                renderWriter.flush();
            } catch (IOException ex) {
                throw new CompletionException(ex);
            }
            
        }, renderStream.getWriteExecutor()); // Single-threaded executor ensures ordering
    }

    /**
     * Convenience method: send with current generation
     */
    protected CompletableFuture<Void> sendRenderCommand(NoteBytesMap command) {
        return sendRenderCommand(command, getCurrentRenderGeneration());
    }
    
     /**
     * Check if container needs rendering
     * 
     * Render manager calls this to check if worth polling
     */
    public boolean isDirty() {
        return this.stateMachine.hasState(Container.STATE_RENDER_REQUESTED);
    }

    /**
     * Clear dirty flag
     * 
     * Render manager calls this after successful render
     */
    public void clearDirtyFlag() {
        this.stateMachine.removeState(Container.STATE_RENDER_REQUESTED);
    }


    public void invalidate() {
        stateMachine.addState(Container.STATE_RENDER_REQUESTED);
        
        // Always try to notify render requester
        if(renderRequester != null){
            try {
                renderRequester.accept(self());
            } catch (Exception ex) {
                Log.logError("[ContainerHandle:" + containerId + "] Render requester failed: " + ex.getMessage());
            }
        } else {
            // No render requester set - log warning in debug mode
            Log.logMsg("[ContainerHandle:" + containerId + "] invalidate() called but no renderRequester set");
        }
    }

    // ===== SERVICE COMMUNICATION =====
    
    /**
     * Send command to RenderingService
     */
    private CompletableFuture<Void> sendToService(NoteBytesMap command) {
        if (isDestroyed()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Container already destroyed")
            );
        }
        
        return request(renderingServicePath, command.toNoteBytesReadOnly(), 
                Duration.ofMillis(500))
            .thenAccept(reply -> {
                NoteBytesMap response = reply.getPayload().getAsNoteBytesMap();
                NoteBytesReadOnly status = response.getReadOnly(Keys.STATUS);
                
                if (status != null && !status.equals(ProtocolMesssages.SUCCESS)) {
                    String errorMsg = ProtocolObjects.getErrMsg(response);
                    throw new RuntimeException("[ContainerHandle] Command failed: " + errorMsg);
                }
            });
    }
    




    public boolean isDestroying(){
        return stateMachine.hasState(Container.STATE_DESTROYING);
    }


    
    // ===== GETTERS =====
    
    public ContainerId getId() {
        return containerId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public ContainerType getType() {
        return containerType;
    }
    
    public ContainerConfig getConfig() {
        return containerConfig;
    }
    
    public ContextPath getRenderingServicePath() {
        return renderingServicePath;
    }
    
    
    public void setRenderable(R renderable) {
        Log.logMsg(String.format("[ContainerHandle:%s] setRenderable() called: old=%s, new=%s",
            containerId, 
            currentRenderable != null ? currentRenderable.getClass().getSimpleName() : "null",
            renderable != null ? renderable.getClass().getSimpleName() : "null"
        ));

        R old = currentRenderable;

        this.currentRenderable = renderable;

        if (old != renderable && old != null) {
            // New renderable - increment generation (layout change)
            nextRenderGeneration();
            
            Log.logMsg(String.format(
                "[TerminalContainerHandle:%s] Renderable changed (gen=%d)",
                getId(), getCurrentRenderGeneration()
            ));
        }
    }

    public R getRenderable() {
        return currentRenderable;
    }

     /**
     * Clear renderable
     */
    public void clearRenderable() {
        this.currentRenderable = null;
        clearDirtyFlag();
    }
    
   
    public CompletableFuture<Void> render() {
        if (currentRenderable == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        if (!isRenderStreamReady()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Render stream not ready")
            );
        }
        
        long gen = getCurrentRenderGeneration();
        RenderState<B, E> state = currentRenderable.getRenderState();
        B batch = state.toBatch(self(), gen);
        
        return executeBatch(batch)
            .thenRun(() -> {
                clearDirtyFlag();
                currentRenderable.clearRenderFlag();
            });
    }
    
    @SuppressWarnings("unchecked")
	public H self() {
        return (H) this;
    }


       /**
     * Execute a batch of commands
     * 
     * This sends the entire batch as a single command over the stream,
     * waits for completion, and returns a single future.
     * 
     * @param batch The batch builder with commands
     * @return CompletableFuture that completes when batch is done
     */
    public CompletableFuture<Void> executeBatch(B batch) {
        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Check generation before sending
        if (!isRenderGenerationCurrent(batch.getGeneration())) {
            Log.logMsg("[TerminalContainerHandle] Skipping batch - stale generation: " + 
                batch.getGeneration() + " (current: " + getCurrentRenderGeneration() + ")");
            return CompletableFuture.completedFuture(null);
        }
        
        NoteBytesMap batchCommand = batch.build();
        return sendRenderCommand(batchCommand, batch.getGeneration());
    }

    public abstract B batch();

    public abstract B batch(long generation);


    public boolean isRenderStreamReady() {
        if(!renderReadyCache){
            renderReadyCache = renderStream != null && 
                renderStream.getReadyFuture().isDone() &&
                !renderStream.getReadyFuture().isCompletedExceptionally();
            return renderReadyCache;
        }else{
            renderReadyCache = renderReadyCache && renderStream != null;
            return renderReadyCache;
        }
    }

    public CompletableFuture<Void> waitUntilReady() {
        if (renderStream == null) {
            // Stream hasn't been created yet, wait for it
            Log.logMsg("[ContainerHandle] waiting for ready");
            return CompletableFuture.runAsync(() -> {
                while (renderStream == null && !isDestroyed()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
                if (isDestroyed()) {
                    throw new IllegalStateException("Container destroyed before ready");
                }
            }, VirtualExecutors.getVirtualExecutor()).thenCompose(v -> {
                Log.logMsg("[ContainerHandle] remderStrea null waiting finished, returning ready future");
                return renderStream.getReadyFuture();
            });
        }
        
        // Stream exists, just return its ready future
        Log.logMsg("[ContainerHandle] returning ready future");
        return renderStream.getReadyFuture();
    }

 
}
