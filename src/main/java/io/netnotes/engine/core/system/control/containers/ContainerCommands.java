package io.netnotes.engine.core.system.control.containers;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * ContainerCommands - Command types for container protocol
 * 
 * Similar to UICommands, but for container management
 */
public class ContainerCommands {
    
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
}
