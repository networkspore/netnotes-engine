package io.netnotes.engine.core.system.control.containers;

import java.io.IOException;
import java.io.PipedInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.ClaimedDevice;
import io.netnotes.engine.io.daemon.ClientSession;
import io.netnotes.engine.io.daemon.IODaemon.SESSION_CMDS;
import io.netnotes.engine.io.daemon.IODaemonProtocol.USBDeviceDescriptor;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.io.input.events.EventHandlerRegistry.RoutedEventHandler;
import io.netnotes.engine.io.input.events.EventsFactory;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.input.events.containers.ContainerMoveEvent;
import io.netnotes.engine.io.input.events.containers.ContainerResizeEvent;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.ItemTypes;
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
 * ContainerHandle - FlowProcess-based container control with Builder pattern
 * 
 * Usage:
 * <pre>
 * ContainerHandle handle = ContainerHandle.builder()
 *     .name("my-terminal")
 *     .type(ContainerType.TERMINAL)
 *     .config(new ContainerConfig().withSize(80, 24))
 *     .autoFocus(true)
 *     .build();
 * 
 * registry.registerChild(ownerPath, handle);
 * registry.startProcess(handle.getContextPath());
 * 
 * handle.waitUntilReady().thenRun(() -> {
 *     // Container is ready to use
 *     handle.show();
 * });
 * </pre>
 */
public class ContainerHandle extends FlowProcess {
    
    // ===== CREATION PARAMETERS (set at construction) =====
    protected final ContainerId containerId;
    private final ContainerType containerType;
    private final ContainerConfig containerConfig;
    private final ContextPath renderingServicePath;
    private final ContextPath containerPath;
    private final String title;
    private final Builder builder;
    // ===== RUNTIME STATE =====
  
    
    protected NoteBytesReadOnly rendererId = null;

    // Stream TO Container (for render commands)
    protected StreamChannel renderStream;
    protected NoteBytesWriter renderWriter;
    protected final AtomicLong renderGeneration = new AtomicLong();

    // Stream FROM Container (for events)
    protected StreamChannel eventChannel = null;
    protected CompletableFuture<Void> eventStreamReadyFuture = new CompletableFuture<>();
    
    // MsgMap allows commands to be sent to the container handle
    protected final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_msgMap = new ConcurrentHashMap<>();
    protected final EventHandlerRegistry eventHandlerRegistry = new EventHandlerRegistry();

    // Current session (if connected to IODaemon)
    protected ClientSession ioDaemonSession = null;
    
    //<NoteMessaging.ItemTypes, id>
    private Map<NoteBytesReadOnly, String> defaultEventHandlers = new ConcurrentHashMap<>();
   
    protected final BitFlagStateMachine stateMachine;
    
    // Event map allows events from the Container to reach the handle
    protected volatile int width;
	protected volatile int height;
    /**
     * Private constructor - use Builder
     */
    protected ContainerHandle(Builder builder) {
        super(builder.name, ProcessType.BIDIRECTIONAL);
        this.containerId = ContainerId.generate();  // Always generate new ID

        this.title = builder.title != null ? builder.title : builder.name;
        this.containerType = builder.containerType;
        this.containerConfig = builder.containerConfig;
        this.renderingServicePath = builder.renderingServicePath;
        this.containerPath = renderingServicePath.append(containerId.toNoteBytes());
        this.builder = builder;
        this.stateMachine = new BitFlagStateMachine("ContainerHandle:" + containerId);
        setupBaseStateHandler();
        setupEventHandlers();
        setupStateTransitions();
    }

    private void setupBaseStateHandler(){
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
    }

    protected void setupStateTransitions(){

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
     * Create a builder for ContainerHandle
     */
    public static Builder builder(String name, ContainerType containerType) {
        return new Builder(name, containerType);
    }
    
    /**
     * Quick constructor with defaults (for simple cases)
     */
    public static ContainerHandle create(String name, ContainerType type) {
        return builder(name, type).build();
    }

    /**
     * Builder for ContainerHandle
     */
    public static class Builder {
        public String name;
        public String title;
        public ContainerType containerType = null;
        public NoteBytes rendererId = null;
        public ContainerConfig containerConfig = new ContainerConfig();
        public ContextPath renderingServicePath = CoreConstants.RENDERING_SERVICE_PATH;
        public boolean autoFocus = true;
        
     
        public Builder(String name, ContainerType containerType){
            this.name = name;
            this.title = name;
            this.containerType = containerType;
        }
        /**
         * Set the process name (required)
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        /**
         * Set the display title (optional, defaults to name)
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }
        
        /**
         * Set the container type
         */
        public Builder type(ContainerType type) {
            this.containerType = type;
            return this;
        }
        
        /**
         * Set the container configuration
         */
        public Builder config(ContainerConfig config) {
            this.containerConfig = config;
            return this;
        }
        
        /**
         * Set the rendering service path (default: CoreConstants.RENDERING_SERVICE_PATH)
         */
        public Builder renderingService(ContextPath path) {
            this.renderingServicePath = path;
            return this;
        }

        public Builder render(NoteBytes rendererId) {
            this.rendererId = rendererId;
            return this;
        }
        
        /**
         * Set auto-focus (default: false)
         */
        public Builder autoFocus(boolean autoFocus) {
            this.autoFocus = autoFocus;
            return this;
        }
        
        /**
         * Build the ContainerHandle
         */
        public ContainerHandle build() {
            if (name == null || name.isEmpty()) {
                throw new IllegalStateException("name is required");
            }
            if(containerType == null){
                throw new IllegalStateException("containerType is required");
            }
            return new ContainerHandle(this);
        }
    }


    protected Map<NoteBytesReadOnly, RoutedMessageExecutor> getRoutedMsgMap() {
        return m_msgMap;
    }

    public NoteBytes getRendererId(){
        return rendererId;
    }
    

    protected void setupEventHandlers(){
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_RESIZED, this::handleContainerResized);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_CLOSED, this::handleContainerClosed);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_SHOWN, this::handleContainerShown);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_HIDDEN, this::handleContainerHidden);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_FOCUS_GAINED, this::handleContainerFocusGained);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_FOCUS_LOST, this::handleContainerFocusLost);
        eventHandlerRegistry.register(EventBytes.EVENT_CONTAINER_MOVE, this::handleContainerMove);
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
                    width = widthBytes.getAsInt();
                }
                if(heightBytes != null){
                    height = heightBytes.getAsInt();
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

    public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
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
        
        // Disconnect from IODaemon if connected
        if (ioDaemonSession != null) {
            disconnectFromIODaemon()
                .exceptionally(ex -> {
                    Log.logError("[ContainerHandle] Error disconnecting IODaemon: " + ex.getMessage());
                    return null;
                });
        }
        
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
    /**
     * Get the event handler registry for registering event handlers
     */
    public EventHandlerRegistry getEventHandlerRegistry(){
        return eventHandlerRegistry;
    }

    protected void processRoutedEvent(NoteBytes eventBytes) {
        try{
            RoutedEvent event = EventsFactory.from(containerPath, eventBytes);
            eventHandlerRegistry.dispatch(event);
        }catch(Exception ex){
            Log.logError("[ContainerHandle] Failed to deserialize routed event: " + ex.getMessage());
            return;
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

            BitFlagStateMachine.StateSnapshot snap = stateMachine.getSnapshot();
            
            // STATE CHECK - don't send if not active
            if (!snap.hasState(Container.STATE_ACTIVE)) {
                Log.logMsg("[ContainerHandle:" + containerId + "] Skipping render - container not active yet");
                return;
            }

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
        return this.stateMachine.hasState(Container.STATE_UPDATE_REQUESTED);
    }

    /**
     * Clear dirty flag
     * 
     * Render manager calls this after successful render
     */
    public void clearDirtyFlag() {
        this.stateMachine.removeState(Container.STATE_UPDATE_REQUESTED);
    }


    public void invalidate() {
        stateMachine.addState(Container.STATE_UPDATE_REQUESTED);
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
    



    public  NoteBytesReadOnly addKeyCharHandler( Consumer<RoutedEvent> handler){
       
        return  getDefaultKeyboardEventRegistry().register(EventBytes.EVENT_KEY_CHAR, handler);
    }

    public  List<RoutedEventHandler> removeKeyCharHandler(Consumer<RoutedEvent> handler){
       return  getDefaultKeyboardEventRegistry().unregister(EventBytes.EVENT_KEY_CHAR, handler);
    }

    public  List<RoutedEventHandler> removeKeyCharHandler(NoteBytesReadOnly handlerId){
       return  getDefaultKeyboardEventRegistry().unregister(EventBytes.EVENT_KEY_CHAR, handlerId);
    }

    public  NoteBytesReadOnly addKeyDownHandler( Consumer<RoutedEvent> handler){
        return  getDefaultKeyboardEventRegistry().register(EventBytes.EVENT_KEY_DOWN, handler);
    }

    public  NoteBytesReadOnly addKeyDownHandler( RoutedEventHandler handler){
        return getDefaultKeyboardEventRegistry().register(EventBytes.EVENT_KEY_DOWN, handler);
    }
    
    public  List<RoutedEventHandler> removeKeyDownHandler(Consumer<RoutedEvent> consumer){
        return getDefaultKeyboardEventRegistry().unregister(EventBytes.EVENT_KEY_DOWN, consumer);
    }

    public  List<RoutedEventHandler> removeKeyDownHandler(NoteBytesReadOnly id){
        return getDefaultKeyboardEventRegistry().unregister(EventBytes.EVENT_KEY_DOWN, id);
    }

    public  NoteBytesReadOnly  addKeyUpHandler( Consumer<RoutedEvent> handler){
        return getEventHandlerRegistry().register(EventBytes.EVENT_KEY_UP, handler);
    }

    public List<RoutedEventHandler> removeKeyUpHandler(Consumer<RoutedEvent> handler){
        return getEventHandlerRegistry().unregister(EventBytes.EVENT_KEY_UP, handler);
    }

    public List<RoutedEventHandler> removeKeyUpHandler(NoteBytesReadOnly handlerId){
        return getEventHandlerRegistry().unregister(EventBytes.EVENT_KEY_UP, handlerId);
    }

    public  NoteBytesReadOnly addResizeHandler( Consumer<RoutedEvent> handler){
        return getEventHandlerRegistry().register(EventBytes.EVENT_CONTAINER_RESIZED, handler);
    }

    public  List<RoutedEventHandler> removeResizeHandler(Consumer<RoutedEvent> handler){
        return getEventHandlerRegistry().unregister(EventBytes.EVENT_CONTAINER_RESIZED, handler);
    }
    
    public  List<RoutedEventHandler> removeResizeHandler(NoteBytesReadOnly handlerId){
        return getEventHandlerRegistry().unregister(EventBytes.EVENT_CONTAINER_RESIZED, handlerId);
    }



    // ===== IODaemon =====

     /**
     * Connect to IODaemon and create a session
     * 
     * @param ioDaemonPath Path to IODaemon process
     * @return CompletableFuture with ClientSession
     */
    protected CompletableFuture<ClientSession> connectToIODaemon(ContextPath ioDaemonPath) {
        if (ioDaemonSession != null) {
            Log.logMsg("[ContainerHandle] Already connected to IODaemon");
            return CompletableFuture.completedFuture(ioDaemonSession);
        }
        
        Log.logMsg("[ContainerHandle] Connecting to IODaemon at: " + ioDaemonPath);
        
        // Generate unique session ID for this container
        String sessionId = containerId.toString() + "-session";
        int pid = (int) ProcessHandle.current().pid();
        
        // Send create_session request
        NoteBytesMap createSessionCmd = new NoteBytesMap();
        createSessionCmd.put(Keys.CMD, "create_session");
        createSessionCmd.put(Keys.SESSION_ID, sessionId);
        createSessionCmd.put(Keys.PID, pid);
        
        return request(ioDaemonPath, createSessionCmd.toNoteBytesReadOnly(), Duration.ofSeconds(2))
            .thenCompose(reply -> {
                NoteBytesMap response = reply.getPayload().getAsNoteBytesMap();
                NoteBytesReadOnly status = response.getReadOnly(Keys.STATUS);
                
                if (status == null || !status.equals(ProtocolMesssages.SUCCESS)) {
                    String errorMsg = ProtocolObjects.getErrMsg(response);
                    throw new RuntimeException("Failed to create session: " + errorMsg);
                }
                
                // Get session path
                NoteBytes pathBytes = response.get(Keys.PATH);
                if (pathBytes == null) {
                    throw new RuntimeException("No session path in response");
                }
                
                ContextPath sessionPath = ContextPath.fromNoteBytes(pathBytes);
                
                // Get session from registry
                ClientSession session = (ClientSession) registry.getProcess(sessionPath);
                if (session == null) {
                    throw new RuntimeException("Session not found in registry: " + sessionPath);
                }
                
                this.ioDaemonSession = session;
                
                Log.logMsg("[ContainerHandle] Connected to IODaemon session: " + sessionPath);
                
                return CompletableFuture.completedFuture(session);
            });
    }


    /**
     * Simplified method that uses default IODaemon path
     */
    public CompletableFuture<ClientSession> connectToIODaemon() {
        return connectToIODaemon(CoreConstants.IO_DAEMON_PATH);
    }

    /**
     * Check if connected to IODaemon
     */
    public boolean hasIODaemonSession() {
        return ioDaemonSession != null && ioDaemonSession.isAlive();
    }

   
    /**
     * Disconnect from IODaemon session
     * Releases all claimed devices and destroys session
     */
    protected CompletableFuture<Void> disconnectFromIODaemon() {
        if (ioDaemonSession == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[ContainerHandle] Disconnecting from IODaemon session");
        
        ClientSession session = ioDaemonSession;
        this.ioDaemonSession = null;
        
        // Clear default event handlers
        defaultEventHandlers.clear();
        // Release all claimed devices
  
        ContextPath ioDaemonPath = session.getParentPath();
        
        NoteBytesMap destroyCmd = new NoteBytesMap();
        destroyCmd.put(Keys.CMD, SESSION_CMDS.DESTROY_SESSION);
        destroyCmd.put(Keys.SESSION_ID, session.sessionId);
        
        return request(ioDaemonPath, destroyCmd.toNoteBytesReadOnly(),  Duration.ofSeconds(1))
            .thenAccept(packet->{
                
                if(packet.getPayload().getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE ){
                    NoteBytesMap map = packet.getPayload().getAsMap();
                    NoteBytes statusBytes = map.get(Keys.STATUS);
                    if(statusBytes != null && statusBytes.equals(ProtocolMesssages.SUCCESS)){
                        Log.logMsg("[ContainerHandle] Disconnected from IODaemon");
                    }else{
                        String msg = ProtocolObjects.getErrMsg(map);
                        Log.logError("[ContainerHandle] Disconnection from IODaemon failed: " + msg);
                    }

                }else{
                    throw new CompletionException(new IllegalArgumentException("NoteBytesObject required"));
                }

            })
            .exceptionally(ex -> {
                Log.logError("[ContainerHandle] Error during disconnect: " + ex.getMessage());
                return null;
            });
    }

     
    /**
     * Get current IODaemon session (if any)
     */
    protected ClientSession getIODaemonSession() {
        return ioDaemonSession;
    }
    
    // ===== DEVICE CLAIMING =====
    
    /**
     * Claim a device from the IODaemon session
     * Device events will be sent to this container
     * 
     * @param deviceId Device ID to claim
     * @param mode "parsed" or "raw"
     * @return CompletableFuture that completes when device is claimed
     */
    protected CompletableFuture<ContextPath> claimDevice(String deviceId, String mode) {
        if (ioDaemonSession == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Not connected to IODaemon"));
        }
        
        Log.logMsg("[ContainerHandle] Claiming device: " + deviceId);
        
        return ioDaemonSession.claimDevice(deviceId, mode)
            .thenApply(path -> {
                Log.logMsg("[ContainerHandle] Device claimed: " + path);
                return path;
            });
    }
    
    /**
     * Release a claimed device
     */
    protected CompletableFuture<Void> releaseDevice(String deviceId) {
        if (ioDaemonSession == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[ContainerHandle] Releasing device: " + deviceId);
        
        // Remove from default handlers
        defaultEventHandlers.remove(deviceId);

        defaultEventHandlers.values().remove(deviceId);
        
        return ioDaemonSession.releaseDevice(deviceId)
            .thenRun(() -> {
                Log.logMsg("[ContainerHandle] Device released: " + deviceId);
            });
    }




    /**
     * Discover available input devices through this container's session
     */
    public CompletableFuture<List<USBDeviceDescriptor>> discoverUSBInputDevices() {
        if (ioDaemonSession == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Not connected to IODaemon"));
        }
        
        return ioDaemonSession.discoverDevices()
            .thenApply(devicesList -> devicesList
                .stream()
                    .map(d -> d.usbDevice())
                    .toList());
    }
    


    
    /**
     * Check if connected to IODaemon session
     */
    protected boolean hasActiveIODaemonSession() {
        return ioDaemonSession != null && ioDaemonSession.isAlive();
    }

    protected EventHandlerRegistry getClaimedDeviceRegistry(String deviceId){
        ClaimedDevice device = ioDaemonSession.getClaimedDevice(deviceId);
        if(device != null){
            return device.getEventHandlerRegistry();
        }else{
            return null;
        }
    }
    
    /**
     * Set default device for an item type
     * 
     * Example:
     *   setDefaultDevice(ItemTypes.KEYBOARD, "keyboard-123")
     *   
     * Now getDefaultEventRegistry(ItemTypes.KEYBOARD) will return
     * the event registry for that specific keyboard device
     */
    protected void setDefaultDevice(NoteBytesReadOnly itemType, String deviceId) {
        if (ioDaemonSession == null) {
            throw new IllegalStateException("Not connected to IODaemon");
        }
        
        ClaimedDevice device = ioDaemonSession.getClaimedDevice(deviceId);
        if (device == null) {
            throw new IllegalArgumentException("Device not claimed: " + deviceId);
        }
        
        defaultEventHandlers.put(itemType, deviceId);
        Log.logMsg("[ContainerHandle] Set default " + itemType + " to: " + deviceId);
    }

    /**
     * Remove default device for an item type
     */
    protected void clearDefaultDevice(NoteBytesReadOnly itemType) {
        String removed = defaultEventHandlers.remove(itemType);
        if (removed != null) {
            Log.logMsg("[ContainerHandle] Cleared default " + itemType + ": " + removed);
        }
    }

    /**
     * Get the default device ID for an item type
     */
    protected String getDefaultDevice(NoteBytesReadOnly itemType) {
        return defaultEventHandlers.get(itemType);
    }

    /**
     * Get the primary keyboard event registry
     */
    public EventHandlerRegistry getDefaultKeyboardEventRegistry() {
        return getDefaultEventRegistry(ItemTypes.KEYBOARD);
    }

    /**
     * Set default keyboard device
     * Convenience wrapper for setDefaultDevice(ItemTypes.KEYBOARD, deviceId)
     */
    protected boolean setDefaultKeyboard(String deviceId) {
        try {
            setDefaultDevice(ItemTypes.KEYBOARD, deviceId);
            return true;
        } catch (Exception e) {
            Log.logError("[ContainerHandle] Failed to set default keyboard: " + 
                e.getMessage());
            return false;
        }
    }

    /**
     * Clear default keyboard
     */
    protected void clearDefaultKeyboard() {
        clearDefaultDevice(ItemTypes.KEYBOARD);
    }

    /**
     * Get default keyboard device ID
     */
    protected String getDefaultKeyboardId() {
        return getDefaultDevice(ItemTypes.KEYBOARD);
    }



    /**
     * Get event registry for a specific item type
     * 
     * If a default device is set for this type, returns that device's registry
     * Otherwise, returns the native event registry
     */
    public EventHandlerRegistry getDefaultEventRegistry(NoteBytesReadOnly itemType) {
        String defaultDeviceId = defaultEventHandlers.get(itemType);
        
        if (defaultDeviceId == null) {
            // No default set, use native registry
            return eventHandlerRegistry;
        }
        
        // Get device from session
        if (ioDaemonSession == null) {
            Log.logError("[ContainerHandle] Session lost, clearing default " + itemType);
            defaultEventHandlers.remove(itemType);
            return eventHandlerRegistry;
        }
        
        ClaimedDevice device = ioDaemonSession.getClaimedDevice(defaultDeviceId);
        if (device != null) {
            return device.getEventHandlerRegistry();
        } else {
            Log.logError("[ContainerHandle] Device " + defaultDeviceId + " not available, removing from defaults");
            defaultEventHandlers.remove(itemType);
            return eventHandlerRegistry;
        }
    }


    /**
     * Set default mouse device
     */
    protected boolean setDefaultMouse(String deviceId) {
        try {
            setDefaultDevice(ItemTypes.MOUSE, deviceId);
            return true;
        } catch (Exception e) {
            Log.logError("[ContainerHandle] Failed to set default mouse: " + 
                e.getMessage());
            return false;
        }
    }

    /**
     * Get mouse event registry
     */
    public EventHandlerRegistry getMouseEventRegistry() {
        return getDefaultEventRegistry(ItemTypes.MOUSE);
    }



    public List<RoutedEventHandler> getKeyDownHandlers(){
        EventHandlerRegistry defaultRegistry = getDefaultKeyboardEventRegistry();
        return defaultRegistry.getEventHandlers(EventBytes.EVENT_KEY_DOWN);
    }

    public List<RoutedEventHandler> clearKeyDownHandlers(){
        EventHandlerRegistry defaultRegistry = getDefaultKeyboardEventRegistry();
        return defaultRegistry.unregister(EventBytes.EVENT_KEY_DOWN);
    }
    
    protected void handleContainerResized(RoutedEvent event){
        if(event instanceof ContainerResizeEvent resizeEvent){
            onContainerResized(resizeEvent);
        }
    }

    protected void handleContainerClosed(RoutedEvent event){
        onContainerClosed();
    }

    protected void handleContainerShown(RoutedEvent event){
        onContainerShown();
    }
    protected void handleContainerHidden(RoutedEvent event){
        onContainerHidden();
    }
    protected void handleContainerFocusGained(RoutedEvent event){
        onContainerFocusGained();
    }
    protected void handleContainerFocusLost(RoutedEvent event){
        onContainerFocusLost();
    }
    protected void handleContainerMove(RoutedEvent event){
        if(event instanceof ContainerMoveEvent moveEvent){
            onContainerMove(moveEvent);
        }
    }
    /**
     * Handle container resized event
     */
    protected void setDimensions(int width, int height) {
        this.height = height;
        this.width = width;
    }



    protected void onContainerResized(ContainerResizeEvent event) {
        setDimensions(event.getWidth(), event.getHeight());
    }

    public boolean isDestroying(){
        return stateMachine.hasState(Container.STATE_DESTROYING);
    }

    protected void onContainerClosed(){
        if(!isDestroyed()){
            stateMachine.addState(Container.STATE_DESTROYING);
            destroy();
        }
        // Removes active if not already removed
        stateMachine.removeState(Container.STATE_ACTIVE); 
        stateMachine.removeState(Container.STATE_VISIBLE);
        stateMachine.removeState(Container.STATE_ACTIVE);
        stateMachine.removeState(Container.STATE_FOCUSED);
    }

    protected void onContainerShown() {
        stateMachine.addState(Container.STATE_VISIBLE);
        stateMachine.removeState(Container.STATE_HIDDEN);
        Log.logMsg("[ContainerHandle:" + containerId + "] Container shown (server confirmed)");
    }

    protected void onContainerHidden() {
        stateMachine.addState(Container.STATE_HIDDEN);
        stateMachine.removeState(Container.STATE_VISIBLE);
        stateMachine.removeState(Container.STATE_ACTIVE);
        stateMachine.removeState(Container.STATE_FOCUSED);
        Log.logMsg("[ContainerHandle:" + containerId + "] Container hidden (server confirmed)");
    }

    protected void onContainerFocusGained() {
        stateMachine.addState(Container.STATE_FOCUSED);
        stateMachine.addState(Container.STATE_ACTIVE);
        Log.logMsg("[ContainerHandle:" + containerId + "] Container focused (server confirmed)");
    }
    protected void onContainerFocusLost() {
        stateMachine.removeState(Container.STATE_FOCUSED);
        stateMachine.removeState(Container.STATE_ACTIVE);
        Log.logMsg("[ContainerHandle:" + containerId + "] Container focus lost (server confirmed)");
    }

    protected void onContainerMove(ContainerMoveEvent moveEvent){

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
    
    private boolean readyCache = false;

    public boolean isRenderStreamReady() {
        if(!readyCache){
            readyCache = renderStream != null && 
                renderStream.getReadyFuture().isDone() &&
                !renderStream.getReadyFuture().isCompletedExceptionally();
            return readyCache;
        }else{
            readyCache = readyCache && renderStream != null;
            return readyCache;
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