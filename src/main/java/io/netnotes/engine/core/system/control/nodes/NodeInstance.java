package io.netnotes.engine.core.system.control.nodes;


import io.netnotes.engine.io.ContextPath;
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
    private final InstanceId instanceId;           // Unique per load
    private final InstalledPackage pkg;             // What package
    private final ProcessConfig processConfig;     // What namespace
    private final INode inode;                     // The actual node
    
    private volatile NodeState state;
    private final long loadTime;
    private int crashCount;
    
    public NodeInstance(
        InstalledPackage pkg,
        INode inode
    ) {
        this.instanceId = InstanceId.generate();
        this.pkg = pkg;
        this.processConfig = pkg.getProcessConfig();
        this.inode = inode;
        this.state = NodeState.LOADING;
        this.loadTime = System.currentTimeMillis();
        this.crashCount = 0;
    }
    
    // ===== IDENTITY =====
    
    public InstanceId getInstanceId() { return instanceId; }
    public PackageId getPackageId() { return pkg.getPackageId(); }
    public NoteBytesReadOnly getProcessId() { return processConfig.getProcessId(); }
    
    // ===== CONFIGURATION =====
    
    public InstalledPackage getPackage() { return pkg; }
    public ProcessConfig getProcessConfig() { return processConfig; }
    public ContextPath getDataRootPath() { return processConfig.getDataRootPath(); }
    public ContextPath getFlowBasePath() { return processConfig.getFlowBasePath(); }
    
    // ===== RUNTIME =====
    
    public INode getINode() { return inode; }
    public NodeState getState() { return state; }
    public void setState(NodeState state) { this.state = state; }
    public long getLoadTime() { return loadTime; }
    public long getUptime() { return System.currentTimeMillis() - loadTime; }
    public int getCrashCount() { return crashCount; }
    public void incrementCrashCount() { crashCount++; }

    @Override
    public String toString() {
        return String.format(
            "Instance[id=%s, package=%s, process=%s, state=%s]",
            instanceId,
            pkg.getPackageId(),
            processConfig.getProcessId(),
            state
        );
    }
}
