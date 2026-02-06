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
    private final BigInteger state;
    private final ContextPath ownerPath;
    private final ContainerConfig config;
    private final long createdTime;
    private final String rendererId;
    
    public ContainerInfo(
        ContainerId id,
        String title,
        String rendererId,
        BigInteger state,
        ContextPath ownerPath,
        ContainerConfig config,
        long createdTime
    ) {
        this.id = id;
        this.title = title;
        this.rendererId = rendererId;
        this.state = state;
        this.ownerPath = ownerPath;
        this.config = config;
        this.createdTime = createdTime;
    }
    
    public ContainerId getId() { return id; }
    public String getTitle() { return title; }
    public String getRendererId() { return rendererId; }
    public BigInteger getState() { return state; }
    public ContextPath getOwnerPath() { return ownerPath; }
    public ContainerConfig getConfig() { return config; }
    public long getCreatedTime() { return createdTime; }
    
    public NoteBytesObject toNoteBytes() {
        NoteBytesMap map = new NoteBytesMap();
        map.put("id", id.toNoteBytes());
        map.put("title", title);
        map.put("rendererId", rendererId);
        map.put("state", state);
        map.put("owner_path", ownerPath.toNoteBytes());
        map.put("config", config.toNoteBytes());
        map.put("created_time", createdTime);
        return map.toNoteBytes();
    }
}