package io.netnotes.engine.core.system.control.containers;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBoolean;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * ContainerCommands - Command types for container protocol
 * 
 * Similar to UICommands, but for container management
 */
public class ContainerCommands {
    public static final NoteBytesReadOnly RENDERER_ID = new NoteBytesReadOnly("renderer_id");
    public static final NoteBytesReadOnly X = new NoteBytesReadOnly("x");
    public static final NoteBytesReadOnly Y = new NoteBytesReadOnly("y");
    public static final NoteBytesReadOnly RESIZABLE = new NoteBytesReadOnly("resizable");
    public static final NoteBytesReadOnly CLOSABLE = new NoteBytesReadOnly("closable");
    public static final NoteBytesReadOnly MOVABLE = new NoteBytesReadOnly("movable");
    public static final NoteBytesReadOnly MINIMIZABLE = new NoteBytesReadOnly("minimizable");
    public static final NoteBytesReadOnly MAXIMIZABLE = new NoteBytesReadOnly("maximizable");
    public static final NoteBytesReadOnly ICON = new NoteBytesReadOnly("icon");
    public static final NoteBytesReadOnly METADATA = new NoteBytesReadOnly( "metadata");
    public static final NoteBytesReadOnly AUTO_FOCUS = new NoteBytesReadOnly( "auto_focus");
    
    // ===== LIFECYCLE COMMANDS =====
    public static final NoteBytesReadOnly CREATE_CONTAINER = 
        new NoteBytesReadOnly("create_container");
    public static final NoteBytesReadOnly DESTROY_CONTAINER = 
        new NoteBytesReadOnly("destroy_container");
    public static final NoteBytesReadOnly SHOW_CONTAINER = 
        new NoteBytesReadOnly("show_container");
    public static final NoteBytesReadOnly HIDE_CONTAINER = 
        new NoteBytesReadOnly("hide_container");
    
    // ===== UPDATE COMMANDS =====
    public static final NoteBytesReadOnly UPDATE_TITLE = 
        new NoteBytesReadOnly("update_title");
    public static final NoteBytesReadOnly UPDATE_SIZE = 
        new NoteBytesReadOnly("update_size");
    public static final NoteBytesReadOnly UPDATE_POSITION = 
        new NoteBytesReadOnly("update_position");
    public static final NoteBytesReadOnly UPDATE_CONTENT = 
        new NoteBytesReadOnly("update_content");
    
    // ===== QUERY COMMANDS =====
    public static final NoteBytesReadOnly GET_CONTAINER_INFO = 
        new NoteBytesReadOnly("get_container_info");
    public static final NoteBytesReadOnly LIST_CONTAINERS = 
        new NoteBytesReadOnly("list_containers");
    
    // ===== EVENT COMMANDS (from ContainerService to INode) =====
    public static final NoteBytesReadOnly CONTAINER_CLOSED = 
        new NoteBytesReadOnly("container_closed");
    public static final NoteBytesReadOnly CONTAINER_RESIZED = 
        new NoteBytesReadOnly("container_resized");
    public static final NoteBytesReadOnly CONTAINER_FOCUSED = 
        new NoteBytesReadOnly("container_focused");

    public static final NoteBytesReadOnly UPDATE_CONTAINER = 
        new NoteBytesReadOnly("update_container");
    public static final NoteBytesReadOnly FOCUS_CONTAINER = 
        new NoteBytesReadOnly("focus_container");
    public static final NoteBytesReadOnly QUERY_CONTAINER = 
        new NoteBytesReadOnly("query_container");

    public static final NoteBytesReadOnly MAXIMIZE_CONTAINER = 
        new NoteBytesReadOnly("maximize_container");
    public static final NoteBytesReadOnly RESTORE_CONTAINER = 
        new NoteBytesReadOnly("restore_container");


    // ===== EVENTS (from ContainerService to INode) =====
    public static final NoteBytesReadOnly CONTAINER_CREATED = 
        new NoteBytesReadOnly("container_created");
    public static final NoteBytesReadOnly CONTAINER_MOVED = 
        new NoteBytesReadOnly("container_moved");
    public static final NoteBytesReadOnly CONTAINER_MINIMIZED = 
        new NoteBytesReadOnly("container_minimized");
    public static final NoteBytesReadOnly CONTAINER_MAXIMIZED = 
        new NoteBytesReadOnly("container_maximized");
    public static final NoteBytesReadOnly CONTAINER_RESTORED = 
        new NoteBytesReadOnly("container_restored");

    
    /**
     * Create a new container
     */
    public static NoteBytesMap createContainer(
        String title,
        ContainerType type,
        ContextPath ownerPath,
        ContainerConfig config,
        boolean autoFocus
    ) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, CREATE_CONTAINER);
        msg.put(Keys.TITLE, new NoteBytes(title));
        msg.put(Keys.TYPE, new NoteBytes(type.name()));
        msg.put(Keys.PATH, ownerPath.getSegments());
        msg.put(AUTO_FOCUS, autoFocus ? NoteBoolean.TRUE : NoteBoolean.FALSE); // Flag for ContainerService
        if (config != null) {
            msg.put(Keys.CONFIG, config.toNoteBytes());
        }
        
        return msg;
    }

   
    /**
     * Destroy a container
     */
    public static NoteBytesMap destroyContainer(ContainerId containerId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, DESTROY_CONTAINER);
        msg.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
        return msg;
    }
    
    /**
     * Update container properties
     */
    public static NoteBytesMap updateContainer(
        ContainerId containerId,
        NoteBytesMap updates
    ) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, UPDATE_CONTAINER);
        msg.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
        msg.put(Keys.UPDATES, updates);
        return msg;
    }
    
    /**
     * Show container (unhide/unminimize)
     */
    public static NoteBytesMap showContainer(ContainerId containerId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, SHOW_CONTAINER);
        msg.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
        return msg;
    }
    
    /**
     * Hide container (minimize)
     */
    public static NoteBytesMap hideContainer(ContainerId containerId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, HIDE_CONTAINER);
        msg.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
        return msg;
    }

    /**
     * Show container (unhide/unminimize)
     */
    public static NoteBytesMap maximizeContainer(ContainerId containerId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, MAXIMIZE_CONTAINER);
        msg.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
        return msg;
    }
    
    /**
     * Hide container (minimize)
     */
    public static NoteBytesMap restoreContainer(ContainerId containerId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, RESTORE_CONTAINER);
        msg.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
        return msg;
    }
    
    /**
     * Focus container (bring to front)
     */
    public static NoteBytesMap focusContainer(ContainerId containerId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, FOCUS_CONTAINER);
        msg.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
        return msg;
    }
    
    /**
     * Query container state
     */
    public static NoteBytesMap queryContainer(ContainerId containerId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, QUERY_CONTAINER);
        msg.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
        return msg;
    }
    
    /**
     * List all containers (admin/debug)
     */
    public static NoteBytesMap listContainers() {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, LIST_CONTAINERS);
        return msg;
    }
    
    // ===== RESPONSE/EVENT BUILDERS =====
    
    /**
     * Container created response
     */
    public static NoteBytesMap containerCreated(
        ContainerId containerId,
        ContextPath containerPath
    ) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, CONTAINER_CREATED);
        msg.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
        msg.put(Keys.PATH, containerPath.getSegments());
        return msg;
    }
    
    /**
     * Container closed event
     */
    public static NoteBytesMap containerClosed(ContainerId containerId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, CONTAINER_CLOSED);
        msg.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
        return msg;
    }
    
    /**
     * Container focused event
     */
    public static NoteBytesMap containerFocused(ContainerId containerId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, CONTAINER_FOCUSED);
        msg.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
        return msg;
    }
    
    /**
     * Container resized event
     */
    public static NoteBytesMap containerResized(
        ContainerId containerId,
        int width,
        int height
    ) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, CONTAINER_RESIZED);
        msg.put(Keys.CONTAINER_ID, containerId.toNoteBytes());
        msg.put(Keys.WIDTH, width);
        msg.put(Keys.HEIGHT, height);
        return msg;
    }
}
