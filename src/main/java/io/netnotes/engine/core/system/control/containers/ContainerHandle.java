package io.netnotes.engine.core.system.control.containers;

import java.io.IOException;
import java.io.PipedInputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
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
    private final ContainerId containerId;
    private final ContainerType containerType;
    private final ContainerConfig containerConfig;
    private final ContextPath renderingServicePath;
    private final String title;
    private final Builder builder;
    // ===== RUNTIME STATE =====
    private volatile boolean isDestroyed = false;
    
    protected NoteBytesReadOnly rendererId = null;

    // Stream TO Container (for render commands)
    protected StreamChannel renderStream;
    protected NoteBytesWriter renderWriter;

    // Stream FROM Container (for events)
    protected StreamChannel eventChannel = null;
    protected CompletableFuture<Void> eventStreamReadyFuture = new CompletableFuture<>();
    
    // MsgMap allows commands to be sent to the container handle
    protected final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_msgMap = new ConcurrentHashMap<>();

    // Event map allows events from the Container to reach the handle
    protected final Map<NoteBytesReadOnly, MessageExecutor> m_eventMap = new ConcurrentHashMap<>();
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
        this.builder = builder;
        setupEventMap();
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
        protected String name;
        protected String title;
        protected ContainerType containerType = null;
        protected NoteBytes rendererId = null;
        protected ContainerConfig containerConfig = new ContainerConfig();
        protected ContextPath renderingServicePath = CoreConstants.RENDERING_SERVICE_PATH;
        protected boolean autoFocus = true;
        
     
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

    /**
     * Default event handlers
     */
    protected void setupEventMap() {
        m_eventMap.put(ContainerCommands.CONTAINER_CLOSED, this::handleContainerClosed);
        m_eventMap.put(ContainerCommands.CONTAINER_RESIZED, this::handleContainerResized);
        m_eventMap.put(ContainerCommands.CONTAINER_FOCUSED, this::handleContainerFocused);
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
        createCmd.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
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
                    channel.getQueuedOutputStream()
                );
            })
            .exceptionally(ex -> {
                Log.logError("[ContainerHandle] Initialization failed: " + ex.getMessage());
                isDestroyed = true;
                return null;
            });
    }

    public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

    
    @Override
    public void onStop() {
        Log.logMsg("[ContainerHandle] Stopped for container: " + containerId);
        isDestroyed = true;
        
        if (renderStream != null) {
            try {
                renderStream.close();
            } catch (IOException e) {
                Log.logError("[ContainerHandle] Error closing render stream: " + 
                    e.getMessage());
            }
        }
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
           Executors.newVirtualThreadPerTaskExecutor().execute(() -> {
                Log.logMsg("[ContainerHandle] Event stream read thread started");
                try (
                    NoteBytesReader reader = new NoteBytesReader(
                        new PipedInputStream(channel.getChannelStream(), 
                            StreamUtils.PIPE_BUFFER_SIZE)
                    );
                ) {
                    // Signal ready
                    channel.getReadyFuture().complete(null);
                    eventStreamReadyFuture.complete(null);
                    Log.logMsg("[ContainerHandle] Event stream reader active");
                    
                    // Read and dispatch events
                    NoteBytesReadOnly nextBytes = reader.nextNoteBytesReadOnly();
                    
                    while (nextBytes != null && isAlive()) {
                        if (nextBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                                Log.logMsg("[ContainerHandle]" + getId() + " event received");
                            NoteBytesMap event = nextBytes.getAsNoteBytesMap();
                            dispatchEvent(event);
                        }
                        nextBytes = reader.nextNoteBytesReadOnly();
                    }
                    
                    Log.logMsg("[ContainerHandle] Event stream reader stopped");
                } catch (IOException e) {
                    Log.logError("[ContainerHandle] Event stream error: " + e.getMessage());
                    throw new CompletionException(e);
                }
            });
            
        }
    }

    protected void dispatchEvent(NoteBytesMap event) {
        NoteBytes eventBytes = event.get(Keys.EVENT);

        if (eventBytes != null) {
            MessageExecutor eventExec = m_eventMap.get(eventBytes);
            if (eventExec != null) {
                eventExec.execute(event);
            } else {
                Log.logMsg("[ContainerHandle] Event is not handled: " + eventBytes);
            }
        } else {
            Log.logMsg("[ContainerHandle] Event map does not contain event");
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
        if (isDestroyed) {
            return CompletableFuture.completedFuture(null);
        }
        if(rendererId == null){
            return CompletableFuture.failedFuture(new IllegalStateException( "[ContainerHandle] container is not yet created / rendererId is null"));
        }
        NoteBytesMap msg = ContainerCommands.destroyContainer(containerId, rendererId);
        return sendToService(msg)
            .thenRun(() -> {
                // Self-cleanup after destroy
                if (registry != null) {
                    registry.unregisterProcess(contextPath);
                }
            });
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
     * Send render command directly to Container via stream
     * NO WRAPPING - just write the command!
     * 
     * This is used by subclasses like TerminalContainerHandle
     */
    protected CompletableFuture<Void> sendRenderCommand(NoteBytesMap command) {
        if (isDestroyed) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Container already destroyed")
            );
        }
        
        // Stream must be ready at this point
        if (renderWriter == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Render stream not initialized")
            );
        }

        if(renderStream.isActive() && !renderStream.isClosed()){
        
            try {
                NoteBytes noteBytes = command.toNoteBytes();
                Log.logMsg("[Container]" + getId() + " send render cmd");
                renderWriter.write(noteBytes);
                return CompletableFuture.completedFuture(null);
            } catch (IOException ex) {
                return CompletableFuture.failedFuture(ex);
            }
        }else{
            Log.logMsg("[ContainerHandle] reader stream is closed");
            return CompletableFuture.completedFuture(null);
        }
    }
    
    // ===== SERVICE COMMUNICATION =====
    
    /**
     * Send command to RenderingService
     */
    private CompletableFuture<Void> sendToService(NoteBytesMap command) {
        if (isDestroyed) {
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
    
    // ===== EVENT HANDLERS =====
    
    /**
     * Handle container closed event
     */
    protected CompletableFuture<Void> handleContainerClosed(NoteBytesMap message) {
        NoteBytesMap notification = new NoteBytesMap();
        notification.put("event", "container_closed");
        notification.put("container_id", containerId.toNoteBytes());
        
        handleContainerClosed();
        emit(notification);
        
        // Cleanup
        isDestroyed = true;

        return CompletableFuture.completedFuture(null);
    }

    protected void handleContainerClosed() {
        Log.logMsg("[ContainerHandle] Container closed: " + containerId);
    }
    
    /**
     * Handle container resized event
     */
    protected void handleContainerResized(NoteBytesMap message) {
        NoteBytes widthBytes = message.get(Keys.WIDTH);
        NoteBytes heightBytes = message.get(Keys.HEIGHT);

        if (widthBytes != null && heightBytes != null) {
            int width = widthBytes.getAsInt();
            int height = heightBytes.getAsInt();
           
            handleContainerResized(width, height);
            emit(message);
        }
    }

    protected void handleContainerResized(int changedWidth, int changedHeight) {
        Log.logMsg("[ContainerHandle] Container resized: " + 
            containerId + " (" + changedWidth + "x" + changedHeight + ")");
    }
    
    /**
     * Handle container focused event
     */
    protected void handleContainerFocused(NoteBytesMap message) {
        handleContainerFocused();
        emit(message);
        CompletableFuture.completedFuture(null);
    }

    protected void handleContainerFocused() {
        Log.logMsg("[ContainerHandle] Container focused: " + containerId);
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
    
    public boolean isDestroyed() {
        return isDestroyed;
    }

    public boolean isRenderStreamReady() {
        return renderStream != null && 
               renderStream.getReadyFuture().isDone() &&
               !renderStream.getReadyFuture().isCompletedExceptionally();
    }

    public CompletableFuture<Void> waitUntilReady() {
        if (renderStream == null) {
            // Stream hasn't been created yet, wait for it
            Log.logMsg("[ContainerHandle] waiting for ready");
            return CompletableFuture.runAsync(() -> {
                while (renderStream == null && !isDestroyed) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
                if (isDestroyed) {
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