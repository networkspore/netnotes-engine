package io.netnotes.engine.core.system.control.containers;

import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Container - Represents a single container instance
 * 
 * Architecture (similar to ClaimedDevice):
 * - Has assigned UIRenderer (injected)
 * - Receives render commands via incoming stream
 * - Writes commands directly to UIRenderer
 * - Tracks abstract state (visible, focused, maximized)
 * 
 * Stream-based rendering:
 * - ContainerHandle writes commands to stream
 * - Container reads from stream
 * - Container writes to UIRenderer
 * - No message wrapping/unwrapping needed!
 */
public class Container {
    
    private final ContainerId id;
    private final ContextPath path;
    private final ContextPath ownerPath;
    private final ContextPath servicePath;

    private final UIRenderer uiRenderer;
    
    // Mutable state
    private final AtomicReference<String> title;
    private final AtomicReference<ContainerType> type;
    private final AtomicReference<ContainerState> state;
    private final AtomicReference<ContainerConfig> config;
    
    private final long createdTime;
    
    // Flags (abstract window state)
    private volatile boolean isCurrent = false;     // Has focus
    private volatile boolean isHidden = false;      // Minimized/hidden
    private volatile boolean isMaximized = false;   // Fills screen
    private volatile boolean isVisible = false;     // Shown (not hidden)
    
    // Render stream
    private StreamChannel renderStream;
    private volatile boolean active = false;
    
    
    public Container(
        ContainerId id,
        String title,
        ContainerType type,
        ContextPath ownerPath,
        ContainerConfig config,
        UIRenderer uiRenderer,
        ContextPath servicePath
    ) {
        this.id = id;
        this.path = ownerPath.append("container", id.toString());
        this.ownerPath = ownerPath;
        this.servicePath = servicePath;
        this.uiRenderer = uiRenderer;
        
        this.title = new AtomicReference<>(title);
        this.type = new AtomicReference<>(type);
        this.state = new AtomicReference<>(ContainerState.CREATING);
        this.config = new AtomicReference<>(config);
        
        this.createdTime = System.currentTimeMillis();
    }
    
    // ===== LIFECYCLE =====
    
    /**
     * Initialize container:
     * 1. Render initial UI
     * 2. Request stream channel from ContainerHandle (via service path)
     * 3. Start reading render commands from stream
     */
    public CompletableFuture<Void> initialize() {
        Log.logMsg("[Container] Initializing: " + id + " (" + title.get() + ")");
        
        // Build initial UI command
        NoteBytesMap createCommand = new NoteBytesMap();
        createCommand.put(Keys.CMD, ContainerCommands.CREATE_CONTAINER);
        createCommand.put(Keys.CONTAINER_ID, id.toNoteBytes());
        createCommand.put(Keys.TITLE, title.get());
        createCommand.put(Keys.TYPE, type.get().name());
        createCommand.put(Keys.CONFIG, config.get().toNoteBytes());
        
        // Render to UI
        return uiRenderer.render(createCommand)
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
     * Handle incoming render stream FROM ContainerHandle
     * Similar to ClaimedDevice.handleStreamChannel()
     */
   public void handleRenderStream(StreamChannel channel, ContextPath fromPath) {
        Log.logMsg("[Container] Render stream received from: " + fromPath);
        
        this.renderStream = channel;
        channel.getReadyFuture().complete(null);  // Signal ready
        
        // Start reading render commands
        channel.startReceiving(input -> {
            try (NoteBytesReader reader = new NoteBytesReader(input)) {
                NoteBytesReadOnly nextBytes = reader.nextNoteBytesReadOnly();
                
                while (nextBytes != null && active) {
                    if (nextBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                        NoteBytesMap command = nextBytes.getAsNoteBytesMap();
                        handleRenderCommand(command);
                    }
                    nextBytes = reader.nextNoteBytesReadOnly();
                }
            } catch (IOException e) {
                Log.logError("[Container] Render stream error: " + e.getMessage());
                active = false;
            }
        });
    }
    
    /**
     * Handle render command from stream
     * Write directly to UIRenderer
     */
    private void handleRenderCommand(NoteBytesMap cmdMap) {
        NoteBytes cmd = cmdMap.get(Keys.CMD);
        
        // Update local state if needed
        if (cmd.equals(ContainerCommands.UPDATE_CONTAINER)) {
            NoteBytes updatesBytes = cmdMap.get(Keys.UPDATES);
            if(updatesBytes != null && updatesBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE){
                NoteBytesMap updates = updatesBytes.getAsNoteBytesMap();
                NoteBytes titleBytes = updates.get(Keys.TITLE);
                if (titleBytes != null) {
                    title.set(titleBytes.getAsString());
                }
            }
        }
        
        // Write directly to renderer
        uiRenderer.render(cmdMap)
            .exceptionally(ex -> {
                Log.logError("[Container] Render failed: " + ex.getMessage());
                return null;
            });
    }
    
    /**
     * Destroy container (remove from UI)
     */
    public CompletableFuture<Void> destroy() {
        Log.logMsg("[Container] Destroying: " + id);
        
        state.set(ContainerState.DESTROYING);
        active = false;
        
        // Build destroy command
        NoteBytesMap destroyCommand = new NoteBytesMap();
        destroyCommand.put(Keys.CMD, ContainerCommands.DESTROY_CONTAINER);
        destroyCommand.put(Keys.CONTAINER_ID, id.toNoteBytes());
        
        return uiRenderer.render(destroyCommand)
            .thenRun(() -> {
                state.set(ContainerState.DESTROYED);
                isVisible = false;
                isCurrent = false;
                
                // Close render stream
                if (renderStream != null) {
                    try {
                        renderStream.close();
                    } catch (IOException e) {
                        Log.logError("[Container] Error closing render stream: " + 
                            e.getMessage());
                    }
                }
                
                Log.logMsg("[Container] Destroyed: " + id);
            })
            .exceptionally(ex -> {
                Log.logError("[Container] Failed to destroy: " + ex.getMessage());
                return null;
            });
    }
    
    // ===== STATE OPERATIONS =====
    // These are called by ContainerService, NOT from stream
    
    /**
     * Show container (unhide/restore)
     */
    public CompletableFuture<Void> show() {
        if (isVisible && !isHidden) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[Container] Showing: " + id);
        
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, ContainerCommands.SHOW_CONTAINER);
        command.put(Keys.CONTAINER_ID, id.toNoteBytes());
        
        return uiRenderer.render(command)
            .thenRun(() -> {
                isVisible = true;
                isHidden = false;
                state.set(ContainerState.VISIBLE);
            });
    }
    
    /**
     * Hide container (minimize)
     */
    public CompletableFuture<Void> hide() {
        if (!isVisible || isHidden) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[Container] Hiding: " + id);
        
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, ContainerCommands.HIDE_CONTAINER);
        command.put(Keys.CONTAINER_ID, id.toNoteBytes());
        
        return uiRenderer.render(command)
            .thenRun(() -> {
                isHidden = true;
                isCurrent = false;
                state.set(ContainerState.HIDDEN);
            });
    }
    
    /**
     * Focus container (bring to front)
     */
    public CompletableFuture<Void> focus() {
        if (isCurrent) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[Container] Focusing: " + id);
        
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, ContainerCommands.FOCUS_CONTAINER);
        command.put(Keys.CONTAINER_ID, id.toNoteBytes());
        
        return uiRenderer.render(command)
            .thenRun(() -> {
                isCurrent = true;
                state.set(ContainerState.FOCUSED);
                
                // Show if hidden
                if (isHidden) {
                    isHidden = false;
                    isVisible = true;
                }
            });
    }
    
    /**
     * Unfocus container (lose focus)
     */
    public void unfocus() {
        isCurrent = false;
        if (state.get() == ContainerState.FOCUSED) {
            state.set(ContainerState.VISIBLE);
        }
    }
    
    /**
     * Maximize container
     */
    public CompletableFuture<Void> maximize() {
        if (isMaximized) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[Container] Maximizing: " + id);
        
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, ContainerCommands.MAXIMIZE_CONTAINER);
        command.put(Keys.CONTAINER_ID, id.toNoteBytes());
        
        return uiRenderer.render(command)
            .thenRun(() -> {
                isMaximized = true;
                state.set(ContainerState.MAXIMIZED);
            });
    }
    
    /**
     * Restore container (un-maximize)
     */
    public CompletableFuture<Void> restore() {
        if (!isMaximized) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[Container] Restoring: " + id);
        
        NoteBytesMap command = new NoteBytesMap();
        command.put(Keys.CMD, ContainerCommands.RESTORE_CONTAINER);
        command.put(Keys.CONTAINER_ID, id.toNoteBytes());
        
        return uiRenderer.render(command)
            .thenRun(() -> {
                isMaximized = false;
                state.set(isCurrent ? ContainerState.FOCUSED : ContainerState.VISIBLE);
            });
    }
    
    // ===== GETTERS =====
    
    public ContainerId getId() { return id; }
    public ContextPath getPath() { return path; }
    public ContextPath getOwnerPath() { return ownerPath; }
    public ContextPath getServicePath() { return servicePath; }
    public String getTitle() { return title.get(); }
    public ContainerType getType() { return type.get(); }
    public ContainerState getState() { return state.get(); }
    public ContainerConfig getConfig() { return config.get(); }
    public long getCreatedTime() { return createdTime; }
    
    // State flags
    public boolean isCurrent() { return isCurrent; }
    public boolean isHidden() { return isHidden; }
    public boolean isMaximized() { return isMaximized; }
    public boolean isVisible() { return isVisible; }
    public boolean isActive() { return active; }
    
    /**
     * Get current container info
     */
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
            "Container[id=%s, title=%s, type=%s, state=%s, visible=%s, current=%s]",
            id, title.get(), type.get(), state.get(), isVisible, isCurrent
        );
    }
}