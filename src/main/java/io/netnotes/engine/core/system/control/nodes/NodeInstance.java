package io.netnotes.engine.core.system.control.nodes;


import io.netnotes.engine.core.AppDataInterface;

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
    
    private final String packageId;
    private final InstalledPackage installedPackage;
    private final INode node;
    
    private volatile NodeState state;
    private AppDataInterface dataInterface;
    private NodeFlowAdapter flowAdapter;
    
    private final long loadTime;
    private int crashCount = 0;
    
    public NodeInstance(
            String packageId,
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
    
    public String getPackageId() {
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
    
    public AppDataInterface getAppDataInterface() {
        return dataInterface;
    }
    
    public NodeFlowAdapter getFlowAdapter() {
        return flowAdapter;
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
    
    public void setDataInterface(AppDataInterface sandbox) {
        this.dataInterface = sandbox;
    }
    
    public void setFlowAdapter(NodeFlowAdapter adapter) {
        this.flowAdapter = adapter;
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

