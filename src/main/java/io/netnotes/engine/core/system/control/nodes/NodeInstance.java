package io.netnotes.engine.core.system.control.nodes;


import io.netnotes.engine.core.NoteFileServiceInterface;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * NodeInstance - Wrapper around INode with lifecycle state
 * 
 * Tracks:
 * - Node implementation (INode)
 * - Installed package metadata
 * - Current state (loading, running, stopping, etc.)
 * - Sandboxed interface
 * - FlowProcess adapter
 * - Load time
 * - Crash count
 */
public class NodeInstance {
    
    private final NoteBytesReadOnly packageId;
    private final InstalledPackage installedPackage;
    private final INode node;
    
    private volatile NodeState state;
    private NoteFileServiceInterface dataInterface;
   
    
    private final long loadTime;
    private int crashCount = 0;
    
    public NodeInstance(
            NoteBytesReadOnly packageId,
            InstalledPackage installedPackage,
            INode node,
            NodeState initialState) {
        
        this.packageId = packageId;
        this.installedPackage = installedPackage;
        this.node = node;
        this.state = initialState;
        this.loadTime = System.currentTimeMillis();
    }
    
    // ===== GETTERS =====
    
    public NoteBytesReadOnly getPackageId() {
        return packageId;
    }
    
    public InstalledPackage getInstalledPackage() {
        return installedPackage;
    }
    
    public INode getNode() {
        return node;
    }
    
    public NodeState getState() {
        return state;
    }
    
    public NoteFileServiceInterface getAppDataInterface() {
        return dataInterface;
    }

    
    public long getLoadTime() {
        return loadTime;
    }
    
    public long getUptime() {
        return System.currentTimeMillis() - loadTime;
    }
    
    public int getCrashCount() {
        return crashCount;
    }
    
    // ===== SETTERS =====
    
    public void setState(NodeState state) {
        NodeState oldState = this.state;
        this.state = state;
        
        System.out.println("[NodeInstance:" + packageId + "] State: " + 
            oldState + " → " + state);
    }
    
    public void setDataInterface(NoteFileServiceInterface sandbox) {
        this.dataInterface = sandbox;
    }
    
    public void incrementCrashCount() {
        this.crashCount++;
    }
    
    // ===== STATUS CHECKS =====
    
    public boolean isRunning() {
        return state == NodeState.RUNNING && node.isActive();
    }
    
    public boolean isHealthy() {
        return isRunning() && node.isAlive();
    }
    
    public boolean isStopped() {
        return state == NodeState.STOPPED;
    }
    
    @Override
    public String toString() {
        return String.format(
            "NodeInstance[id=%s, state=%s, uptime=%ds, crashes=%d]",
            packageId,
            state,
            getUptime() / 1000,
            crashCount
        );
    }
}

