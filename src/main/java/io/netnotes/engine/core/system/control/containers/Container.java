package io.netnotes.engine.core.system.control.containers;

import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Container - Represents a single container instance
 * 
 * Wraps UIRenderer calls and tracks state.
 * Abstract enough to work with any UI (desktop, mobile, web, terminal).
 * 
 * State tracking (abstract concepts):
 * - isCurrent: Container has focus
 * - isHidden: Container is minimized/hidden
 * - isMaximized: Container fills screen/parent
 * - isVisible: Container is shown (not hidden)
 * 
 * UI interprets these as appropriate:
 * - Desktop: GLFW window with title bar
 * - Mobile: Full screen or split view
 * - Web: Browser window/tab or iframe
 * - Terminal: Terminal pane in tmux/screen
 */
public class Container {
    
    private final ContainerId id;
    private final ContextPath path;
    private final ContextPath ownerPath;
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
    
    public Container(
        ContainerId id,
        String title,
        ContainerType type,
        ContextPath ownerPath,
        ContainerConfig config,
        UIRenderer uiRenderer
    ) {
        this.id = id;
        this.path = ownerPath.append("container", id.toString());
        this.ownerPath = ownerPath;
        this.uiRenderer = uiRenderer;
        
        this.title = new AtomicReference<>(title);
        this.type = new AtomicReference<>(type);
        this.state = new AtomicReference<>(ContainerState.CREATING);
        this.config = new AtomicReference<>(config);
        
        this.createdTime = System.currentTimeMillis();
    }
    
    // ===== LIFECYCLE =====
    
    /**
     * Initialize container (render to UI)
     */
    public CompletableFuture<Void> initialize() {
        System.out.println("[Container] Initializing: " + id + " (" + title.get() + ")");
        
        // Build UI command based on type
        NoteBytesMap uiCommand = buildCreateCommand();
        
        return uiRenderer.render(uiCommand)
            .thenRun(() -> {
                state.set(ContainerState.VISIBLE);
                isVisible = true;
                
                System.out.println("[Container] Initialized: " + id);
            })
            .exceptionally(ex -> {
                System.err.println("[Container] Failed to initialize: " + ex.getMessage());
                state.set(ContainerState.DESTROYED);
                return null;
            });
    }
    
    /**
     * Destroy container (remove from UI)
     */
    public CompletableFuture<Void> destroy() {
        System.out.println("[Container] Destroying: " + id);
        
        state.set(ContainerState.DESTROYING);
        
        // Build destroy command
        NoteBytesMap uiCommand = buildDestroyCommand();
        
        return uiRenderer.render(uiCommand)
            .thenRun(() -> {
                state.set(ContainerState.DESTROYED);
                isVisible = false;
                isCurrent = false;
                
                System.out.println("[Container] Destroyed: " + id);
            })
            .exceptionally(ex -> {
                System.err.println("[Container] Failed to destroy: " + ex.getMessage());
                return null;
            });
    }
    
    // ===== STATE OPERATIONS =====
    
    /**
     * Show container (unhide/restore)
     */
    public CompletableFuture<Void> show() {
        if (isVisible && !isHidden) {
            return CompletableFuture.completedFuture(null);
        }
        
        System.out.println("[Container] Showing: " + id);
        
        NoteBytesMap uiCommand = buildShowCommand();
        
        return uiRenderer.render(uiCommand)
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
        
        System.out.println("[Container] Hiding: " + id);
        
        NoteBytesMap uiCommand = buildHideCommand();
        
        return uiRenderer.render(uiCommand)
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
        
        System.out.println("[Container] Focusing: " + id);
        
        NoteBytesMap uiCommand = buildFocusCommand();
        
        return uiRenderer.render(uiCommand)
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
        
        System.out.println("[Container] Maximizing: " + id);
        
        NoteBytesMap uiCommand = buildMaximizeCommand();
        
        return uiRenderer.render(uiCommand)
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
        
        System.out.println("[Container] Restoring: " + id);
        
        NoteBytesMap uiCommand = buildRestoreCommand();
        
        return uiRenderer.render(uiCommand)
            .thenRun(() -> {
                isMaximized = false;
                state.set(isCurrent ? ContainerState.FOCUSED : ContainerState.VISIBLE);
            });
    }
    
    /**
     * Update container properties
     */
    public CompletableFuture<NoteBytesMap> update(NoteBytesMap updates) {
        System.out.println("[Container] Updating: " + id);
        
        // Update local state
        if (updates.has("title")) {
            title.set(updates.get("title").getAsString());
        }
        
        if (updates.has("config")) {
            ContainerConfig newConfig = ContainerConfig.fromNoteBytes(
                updates.get("config").getAsNoteBytesMap()
            );
            config.set(newConfig);
        }
        
        // Send update command to UI
        NoteBytesMap uiCommand = buildUpdateCommand(updates);
        
        return uiRenderer.render(uiCommand);
    }
    
    // ===== UI COMMAND BUILDERS =====
    // These build abstract commands that UIRenderer interprets
    
    private NoteBytesMap buildCreateCommand() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put("cmd", "create_container");
        cmd.put("container_id", id.toNoteBytes());
        cmd.put("title", title.get());
        cmd.put("type", type.get().name());
        cmd.put("config", config.get().toNoteBytes());
        return cmd;
    }
    
    private NoteBytesMap buildDestroyCommand() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put("cmd", "destroy_container");
        cmd.put("container_id", id.toNoteBytes());
        return cmd;
    }
    
    private NoteBytesMap buildShowCommand() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put("cmd", "show_container");
        cmd.put("container_id", id.toNoteBytes());
        return cmd;
    }
    
    private NoteBytesMap buildHideCommand() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put("cmd", "hide_container");
        cmd.put("container_id", id.toNoteBytes());
        return cmd;
    }
    
    private NoteBytesMap buildFocusCommand() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put("cmd", "focus_container");
        cmd.put("container_id", id.toNoteBytes());
        return cmd;
    }
    
    private NoteBytesMap buildMaximizeCommand() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put("cmd", "maximize_container");
        cmd.put("container_id", id.toNoteBytes());
        return cmd;
    }
    
    private NoteBytesMap buildRestoreCommand() {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put("cmd", "restore_container");
        cmd.put("container_id", id.toNoteBytes());
        return cmd;
    }
    
    private NoteBytesMap buildUpdateCommand(NoteBytesMap updates) {
        NoteBytesMap cmd = new NoteBytesMap();
        cmd.put("cmd", "update_container");
        cmd.put("container_id", id.toNoteBytes());
        cmd.put("updates", updates);
        return cmd;
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
    
    // State flags
    public boolean isCurrent() { return isCurrent; }
    public boolean isHidden() { return isHidden; }
    public boolean isMaximized() { return isMaximized; }
    public boolean isVisible() { return isVisible; }
    
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