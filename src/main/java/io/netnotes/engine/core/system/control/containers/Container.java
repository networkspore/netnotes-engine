package io.netnotes.engine.core.system.control.containers;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

import java.io.IOException;
import java.io.PipedInputStream;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Container - Abstract base class for all container implementations
 * 
 * Provides:
 * - Core identity and lifecycle
 * - Bidirectional stream handling infrastructure
 * - Message dispatch framework via msgMap
 * - Common state management
 * 
 * Stream Architecture:
 * - Render stream (FROM ContainerHandle): Commands are read and dispatched via msgMap
 * - Event stream (TO ContainerHandle): Events are written via emitEvent()
 * 
 * Subclasses implement:
 * - Renderer-specific logic (ConsoleContainer, WebContainer, etc.)
 * - Command handlers registered in setupMessageMap()
 */
public abstract class Container {
    // ===== CORE IDENTITY =====
    protected final ContainerId id;
    protected final AtomicReference<String> title;
    protected final AtomicReference<ContainerType> type;
    protected final AtomicReference<ContainerState> state;
    protected final AtomicReference<ContainerConfig> config;
    protected final ContextPath ownerPath;
    protected final ContextPath path;
    protected final String rendererId;
    protected final long createdTime;
    
    // ===== STATE FLAGS =====
    protected volatile boolean isCurrent = false;
    protected volatile boolean isHidden = false;
    protected volatile boolean isMaximized = false;
    protected volatile boolean isVisible = false;
    protected volatile boolean active = false;
    
    // ===== STREAM CHANNELS =====
    protected StreamChannel renderStreamChannel = null;
    protected StreamChannel eventStream = null;
    protected NoteBytesWriter eventWriter;
    protected CompletableFuture<Void> renderStreamFuture = new CompletableFuture<>();
    
    // ===== MESSAGE DISPATCH =====
    protected final HashMap<NoteBytesReadOnly, RoutedMessageExecutor> msgMap = new HashMap<>();
    

    /**
     * Full constructor
     */
    protected Container(
        ContainerId id,
        String title,
        ContainerType type,
        ContextPath ownerPath,
        ContainerConfig config,
        String rendererId
    ) {
        this.id = id;
        this.title = new AtomicReference<>(title);
        this.type = new AtomicReference<>(type);
        this.state = new AtomicReference<>(ContainerState.CREATING);
        this.config = new AtomicReference<>(config);
        this.ownerPath = ownerPath;
        this.path = ownerPath != null ? ownerPath.append("container", id.toString()) : null;
        this.rendererId = rendererId;
        this.createdTime = System.currentTimeMillis();
        
        // Setup base message handlers
        setupBaseMessageMap();
        
        // Subclass adds its handlers
        setupMessageMap();
    }
    
    // ===== ABSTRACT METHODS (Subclass Implementation) =====
    
    /**
     * Setup subclass-specific message handlers
     * Called after base constructor
     * 
     * Example:
     * msgMap.put(TerminalCommands.TERMINAL_PRINT, this::handlePrint);
     */
    protected abstract void setupMessageMap();
    
    /**
     * Initialize renderer-specific resources
     */
    protected abstract CompletableFuture<Void> initializeRenderer();
    
    /**
     * Cleanup renderer-specific resources
     */
    protected abstract CompletableFuture<Void> destroyRenderer();
    
    /**
     * Handle renderer-specific show logic
     */
    protected abstract CompletableFuture<Void> showRenderer();
    
    /**
     * Handle renderer-specific hide logic
     */
    protected abstract CompletableFuture<Void> hideRenderer();
    
    /**
     * Handle renderer-specific focus logic
     */
    protected abstract CompletableFuture<Void> focusRenderer();
    
    // ===== BASE MESSAGE MAP =====
    
    /**
     * Setup base message handlers (common to all containers)
     */
    private void setupBaseMessageMap() {
        msgMap.put(ContainerCommands.UPDATE_CONTAINER, this::handleUpdateContainer);
    }
    
    // ===== LIFECYCLE =====
    
    /**
     * Initialize container
     * 1. Initialize renderer
     * 2. Update state
     */
    public CompletableFuture<Void> initialize() {
        Log.logMsg("[Container] Initializing: " + id + " (" + title.get() + ")");
        
        return initializeRenderer()
            .thenRun(() -> {
                state.set(ContainerState.VISIBLE);
                isVisible = true;
                active = true;
                Log.logMsg("[Container] Initialized: " + id);
            })
            .exceptionally(ex -> {
                Log.logError("[Container] Failed to initialize: " + ex.getMessage());
                state.set(ContainerState.DESTROYED);
                return null;
            });
    }
    
    /**
     * Destroy container
     */
    public CompletableFuture<Void> destroy() {
        Log.logMsg("[Container] Destroying: " + id);
        
        state.set(ContainerState.DESTROYING);
        active = false;
        
        return destroyRenderer()
            .thenRun(() -> {
                state.set(ContainerState.DESTROYED);
                isVisible = false;
                isCurrent = false;
                
                // Close streams
                closeStreams();
                
                Log.logMsg("[Container] Destroyed: " + id);
            })
            .exceptionally(ex -> {
                Log.logError("[Container] Failed to destroy: " + ex.getMessage());
                return null;
            });
    }
    
    // ===== STREAM HANDLING =====
    
    /**
     * Handle incoming render stream FROM ContainerHandle
     * Sets up input stream reader and dispatches commands via msgMap
     */
    public void handleRenderStream(StreamChannel channel, ContextPath fromPath) {
        Log.logMsg("[Container] Render stream received from: " + fromPath);
        
        // Setup reader on virtual thread, one renderStream per lifecycle
        if(renderStreamChannel == null){
            this.renderStreamChannel = channel;
            Log.logMsg("[Container] reader stream read thread starting");
            CompletableFuture.runAsync(() -> {
                try (
                    NoteBytesReader reader = new NoteBytesReader(
                        new PipedInputStream(channel.getChannelStream(), StreamUtils.PIPE_BUFFER_SIZE)
                    );
                ) {
                    // Signal ready
                    channel.getReadyFuture().complete(null);
                    
                    Log.logMsg("[Container] Render stream reader active, waiting for commands...");
                    
                    // Read and dispatch commands
                    NoteBytesReadOnly nextBytes = reader.nextNoteBytesReadOnly();
                 
                    while (nextBytes != null && active) {
                        if (nextBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                            Log.logMsg("[Container]" + getId() + " render cmd");
                            NoteBytesMap command = nextBytes.getAsNoteBytesMap();
                            dispatchCommand(command);
                        }
                        nextBytes = reader.nextNoteBytesReadOnly();
                    }
                    
                    
                } catch (IOException e) {
                    Log.logError("[Container] Render stream error: " + e.getMessage());
                    active = false;
                    throw new CompletionException(e);
                
                }
            }, VirtualExecutors.getVirtualExecutor())
                .thenRun(()->{

                    Log.logMsg("[Container] Render stream completed");
                    active = false;
                });
        }else{
            Log.logMsg("[Container] renderStreamChannel is not null not handlign stream");
        }
    }
    
    /**
     * Handle outgoing event stream TO ContainerHandle
     * Sets up output stream writer for emitting events
     */
    public void handleEventStream(StreamChannel channel, ContextPath fromPath) {
        Log.logMsg("[Container] Event stream established to: " + fromPath);
        
        this.eventStream = channel;
        this.eventWriter = new NoteBytesWriter(channel.getQueuedOutputStream());
        
    }
    
    /**
     * Dispatch command using message map
     * Command is routed to registered handler based on 'cmd' field
     */
    protected void dispatchCommand(NoteBytesMap command) {
        NoteBytes cmd = command.get(Keys.CMD);
        
        if (cmd == null) {
            Log.logError("[Container] No cmd in command");
            return;
        }
        
        // Look up handler in message map
        RoutedMessageExecutor executor = msgMap.get(cmd);
        
        if (executor != null) {
            try {
                // Execute handler (no packet for stream commands)
                executor.execute(command, null);
            } catch (Exception e) {
                Log.logError("[Container] Error executing command '" + cmd + "': " + e.getMessage());
            }
        } else {
            Log.logError("[Container] Unknown command: " + cmd);
        }
    }
    
    /**
     * Emit event to ContainerHandle via event stream
     * 
     * Example usage in subclass:
     * NoteBytesMap event = ContainerCommands.containerResized(id, width, height);
     * emitEvent(event);
     */
    protected void emitEvent(NoteBytesMap event) {
        if (eventWriter == null) {
            Log.logError("[Container] Cannot emit event - no event stream");
            return;
        }
         Log.logMsg("[Container] emmitting event");
        try {
            eventWriter.write(event.toNoteBytes());
        } catch (IOException e) {
            Log.logError("[Container] Error emitting event: " + e.getMessage());
        }
    }
    
    // ===== STATE OPERATIONS =====
    
    public CompletableFuture<Void> show() {
        if (isVisible && !isHidden) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[Container] Showing: " + id);
        
        return showRenderer()
            .thenRun(() -> {
                isVisible = true;
                isHidden = false;
                state.set(ContainerState.VISIBLE);
            });
    }
    
    public CompletableFuture<Void> hide() {
        if (!isVisible || isHidden) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[Container] Hiding: " + id);
        
        return hideRenderer()
            .thenRun(() -> {
                isHidden = true;
                isCurrent = false;
                state.set(ContainerState.HIDDEN);
            });
    }
    
    public CompletableFuture<Void> focus() {
        if (isCurrent) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[Container] Focusing: " + id);
        
        return focusRenderer()
            .thenRun(() -> {
                isCurrent = true;
                state.set(ContainerState.FOCUSED);
                
                if (isHidden) {
                    isHidden = false;
                    isVisible = true;
                }
            });
    }
    
    public void unfocus() {
        isCurrent = false;
        if (state.get() == ContainerState.FOCUSED) {
            state.set(ContainerState.VISIBLE);
        }
    }
    
    public CompletableFuture<Void> maximize() {
        if (isMaximized) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[Container] Maximizing: " + id);
        isMaximized = true;
        state.set(ContainerState.MAXIMIZED);
        
        return CompletableFuture.completedFuture(null);
    }
    
    public CompletableFuture<Void> restore() {
        if (!isMaximized) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[Container] Restoring: " + id);
        isMaximized = false;
        state.set(isCurrent ? ContainerState.FOCUSED : ContainerState.VISIBLE);
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== COMMON MESSAGE HANDLERS =====
    
    protected CompletableFuture<Void> handleUpdateContainer(NoteBytesMap command, RoutedPacket packet) {
        NoteBytes updatesBytes = command.get(Keys.UPDATES);
        if (updatesBytes != null && 
            updatesBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            
            NoteBytesMap updates = updatesBytes.getAsNoteBytesMap();
            NoteBytes titleBytes = updates.get(Keys.TITLE);
            if (titleBytes != null) {
                title.set(titleBytes.getAsString());
            }
        }
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== HELPERS =====
    
    protected void closeStreams() {
        if (renderStreamChannel != null) {
            try {
                renderStreamChannel.close();
            } catch (IOException e) {
                Log.logError("[Container] Error closing render stream: " + e.getMessage());
            }
        }
        
        if (eventStream != null) {
            try {
                eventStream.close();
            } catch (IOException e) {
                Log.logError("[Container] Error closing event stream: " + e.getMessage());
            }
        }
    }
    
    // ===== GETTERS =====
    
    public ContainerId getId() { return id; }
    public ContextPath getPath() { return path; }
    public ContextPath getOwnerPath() { return ownerPath; }
    public String getTitle() { return title.get(); }
    public ContainerType getType() { return type.get(); }
    public ContainerState getState() { return state.get(); }
    public ContainerConfig getConfig() { return config.get(); }
    public long getCreatedTime() { return createdTime; }
    public String getRendererId() { return rendererId; }
    
    public boolean isCurrent() { return isCurrent; }
    public boolean isHidden() { return isHidden; }
    public boolean isMaximized() { return isMaximized; }
    public boolean isVisible() { return isVisible; }
    public boolean isActive() { return active; }
    
    public CompletableFuture<Void> getRenderStreamFuture() { return renderStreamFuture; }
    
    public ContainerInfo getInfo() {
        return new ContainerInfo(
            id,
            title.get(),
            type.get(),
            state.get(),
            ownerPath,
            config.get(),
            createdTime
        );
    }
    
    @Override
    public String toString() {
        return String.format(
            "Container[id=%s, title=%s, type=%s, state=%s, visible=%s, current=%s, renderer=%s]",
            id, title.get(), type.get(), state.get(), isVisible, isCurrent, rendererId
        );
    }
}