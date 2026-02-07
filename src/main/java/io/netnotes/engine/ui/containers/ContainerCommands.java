package io.netnotes.engine.ui.containers;

import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.noteBytes.NoteBoolean;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.collections.NoteBytesPair;

/**
 * ContainerCommands - Command types for container protocol
 * 
 * Similar to UICommands, but for container management
 * only for ContainerHandle TO Container
 * EVENT commands FROM container utlize EventBytes, not ContainerCommands
 */
public class ContainerCommands {

    public static final NoteBytesReadOnly CONTAINER_ID  = new NoteBytesReadOnly("container_id");
    public static final NoteBytesReadOnly RENDERER_ID = new NoteBytesReadOnly("renderer_id");
    public static final NoteBytesReadOnly X = new NoteBytesReadOnly("x");
    public static final NoteBytesReadOnly Y = new NoteBytesReadOnly("y");
    
    public static final NoteBytesReadOnly COORDINATES = new NoteBytesReadOnly(   "coordinates");
    public static final NoteBytesReadOnly DIMENSIONS = new NoteBytesReadOnly("dimensions");
    public static final NoteBytesReadOnly REGION = new NoteBytesReadOnly("region");
    
    public static final NoteBytesReadOnly RESIZABLE = new NoteBytesReadOnly("resizable");
    public static final NoteBytesReadOnly CLOSABLE = new NoteBytesReadOnly("closable");
    public static final NoteBytesReadOnly MOVABLE = new NoteBytesReadOnly("movable");
    public static final NoteBytesReadOnly MINIMIZABLE = new NoteBytesReadOnly("minimizable");
    public static final NoteBytesReadOnly MAXIMIZABLE = new NoteBytesReadOnly("maximizable");
    public static final NoteBytesReadOnly ICON = new NoteBytesReadOnly("icon");
    public static final NoteBytesReadOnly METADATA = new NoteBytesReadOnly( "metadata");
    public static final NoteBytesReadOnly AUTO_FOCUS = new NoteBytesReadOnly( "auto_focus");
    public static final NoteBytesReadOnly GENERATION    = new NoteBytesReadOnly("generation");
    public static final NoteBytesReadOnly BATCH_COMMANDS = new NoteBytesReadOnly( "batch_cmds");


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
    
        public static final NoteBytesReadOnly  CONAINER_BATCH = 
        new NoteBytesReadOnly("container_batch");
  

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



    public static final NoteBytesReadOnly REQUEST_CONTAINER_REGION = 
        new NoteBytesReadOnly("container_request_region");


    
    /**
     * Create a new container
     */
    public static NoteBytesReadOnly createContainer(
        ContainerId containerId,
        String title,
        NoteBytesReadOnly rendererId,
        NoteBytes ownerPath,
        ContainerConfig config,
        boolean autoFocus
    ) {
        config = config == null ? new ContainerConfig() : config;
        
        NoteBytesObject msg = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, CREATE_CONTAINER),
            new NoteBytesPair(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes()),
            new NoteBytesPair(Keys.TITLE, new NoteBytes(title)),
            new NoteBytesPair(ContainerCommands.RENDERER_ID, rendererId),
            new NoteBytesPair(Keys.PATH, ownerPath),
            new NoteBytesPair(AUTO_FOCUS, autoFocus ? NoteBoolean.TRUE : NoteBoolean.FALSE),
            new NoteBytesPair(Keys.CONFIG, config.toNoteBytes())
        });
        
        return msg.readOnly();
    }

   
    /**
     * Destroy a container
     */
    public static NoteBytesMap destroyContainer(ContainerId containerId, NoteBytes rendererId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, DESTROY_CONTAINER);
        msg.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        msg.put(RENDERER_ID, rendererId);
        return msg;
    }
    
    /**
     * Update container properties
     */
    public static NoteBytesMap updateContainer(
        ContainerId containerId,
        NoteBytesMap updates, NoteBytes rendererId
    ) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, UPDATE_CONTAINER);
        msg.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        msg.put(Keys.UPDATES, updates);
         msg.put(RENDERER_ID, rendererId);
        return msg;
    }
    
    /**
     * Show container (unhide/unminimize)
     */
    public static NoteBytesMap showContainer(ContainerId containerId, NoteBytes rendererId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, SHOW_CONTAINER);
        msg.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        msg.put(RENDERER_ID, rendererId);
        return msg;
    }
    
    /**
     * Hide container (minimize)
     */
    public static NoteBytesMap hideContainer(ContainerId containerId, NoteBytes rendererId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, HIDE_CONTAINER);
        msg.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        msg.put(RENDERER_ID, rendererId);
        return msg;
    }

    /**
     * Show container (unhide/unminimize)
     */
    public static NoteBytesMap maximizeContainer(ContainerId containerId, NoteBytes rendererId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, MAXIMIZE_CONTAINER);
        msg.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        msg.put(RENDERER_ID, rendererId);
        return msg;
    }


    /**
     * Hide container (minimize)
     */
    public static NoteBytesMap requestContainerRegion(
        ContainerId containerId, 
        NoteBytes rendererId,
        NoteBytes region
    ) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, REQUEST_CONTAINER_REGION);
        msg.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        msg.put(RENDERER_ID, rendererId);
        msg.put(REGION, region);
        return msg;
    }

    
    
    /**
     * Hide container (minimize)
     */
    public static NoteBytesMap restoreContainer(ContainerId containerId, NoteBytes rendererId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, RESTORE_CONTAINER);
        msg.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        msg.put(RENDERER_ID, rendererId);
        return msg;
    }
    
    /**
     * Focus container (bring to front)
     */
    public static NoteBytesMap focusContainer(ContainerId containerId, NoteBytes rendererId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, FOCUS_CONTAINER);
        msg.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        msg.put(RENDERER_ID, rendererId);
        return msg;
    }
    
    /**
     * Query container state
     */
    public static NoteBytesMap queryContainer(ContainerId containerId, NoteBytes rendererId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.CMD, QUERY_CONTAINER);
        msg.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        msg.put(RENDERER_ID, rendererId);
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
     * Container closed event
     */
    public static NoteBytesMap containerClosed(NoteBytes containerId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.EVENT, EventBytes.EVENT_CONTAINER_CLOSED);
        msg.put(ContainerCommands.CONTAINER_ID, containerId);
        return msg;
    }
    
    /**
     * Container focused event
     */
    public static NoteBytesMap containerFocused(NoteBytes containerId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.EVENT, EventBytes.EVENT_CONTAINER_FOCUS_GAINED);
        msg.put(ContainerCommands.CONTAINER_ID, containerId);
        return msg;
    }

     public static NoteBytesMap containerFocusLost(NoteBytes containerId) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.EVENT, EventBytes.EVENT_CONTAINER_FOCUS_LOST);
        msg.put(ContainerCommands.CONTAINER_ID, containerId);
        return msg;
    }
    
     public static NoteBytesMap containerResized(
        ContainerId containerId,
        int width,
        int height
    ) {
        return containerResized(containerId.toNoteBytes(), width, height);
    }
    /**
     * Container resized event
     */
    public static NoteBytesMap containerResized(
        NoteBytes containerId,
        int width,
        int height
    ) {
        NoteBytesMap msg = new NoteBytesMap();
        msg.put(Keys.EVENT, EventBytes.EVENT_CONTAINER_REGION_CHANGED);
        msg.put(ContainerCommands.CONTAINER_ID, containerId);
        msg.put(Keys.PAYLOAD, new NoteBytesArrayReadOnly(new NoteBytes[]{
            new NoteBytes(width),
            new NoteBytes(height)
        }));
        return msg;
    }
    
}
