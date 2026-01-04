package io.netnotes.engine.core.system.control.containers;

import java.math.BigInteger;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * ContainerInfo - Current information about a container
 * 
 * Returned in response to queries, contains current state
 */
public class ContainerInfo {
    private final ContainerId id;
    private final String title;
    private final ContainerType type;
    private final BigInteger state;
    private final ContextPath ownerPath;
    private final ContainerConfig config;
    private final long createdTime;
    
    public ContainerInfo(
        ContainerId id,
        String title,
        ContainerType type,
        BigInteger state,
        ContextPath ownerPath,
        ContainerConfig config,
        long createdTime
    ) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.state = state;
        this.ownerPath = ownerPath;
        this.config = config;
        this.createdTime = createdTime;
    }
    
    public ContainerId getId() { return id; }
    public String getTitle() { return title; }
    public ContainerType getType() { return type; }
    public BigInteger getState() { return state; }
    public ContextPath getOwnerPath() { return ownerPath; }
    public ContainerConfig getConfig() { return config; }
    public long getCreatedTime() { return createdTime; }
    
    public NoteBytesObject toNoteBytes() {
        NoteBytesMap map = new NoteBytesMap();
        map.put("id", id.toNoteBytes());
        map.put("title", title);
        map.put("type", type.name());
        map.put("state", state);
        map.put("owner_path", ownerPath.toNoteBytes());
        map.put("config", config.toNoteBytes());
        map.put("created_time", createdTime);
        return map.toNoteBytes();
    }
}