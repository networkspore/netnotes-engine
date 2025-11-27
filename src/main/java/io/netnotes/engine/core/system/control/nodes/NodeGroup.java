package io.netnotes.engine.core.system.control.nodes;


import java.util.ArrayList;
import java.util.List;

/**
 * NodeGroup - Groups multiple versions of the same node
 * Similar to PluginGroup pattern
 */
public class NodeGroup {
    
    private final String nodeId;
    private final NodeInformation nodeInfo;
    private final List<NodeMetadata> installedVersions;
    
    public NodeGroup(String nodeId, NodeInformation nodeInfo) {
        this.nodeId = nodeId;
        this.nodeInfo = nodeInfo;
        this.installedVersions = new ArrayList<>();
    }
    
    public void addVersion(NodeMetadata metadata) {
        if (!installedVersions.contains(metadata)) {
            installedVersions.add(metadata);
        }
    }
    
    public String getNodeId() { return nodeId; }
    public NodeInformation getNodeInfo() { return nodeInfo; }
    
    public List<NodeMetadata> getInstalledVersions() {
        return new ArrayList<>(installedVersions);
    }
    
    public NodeMetadata getEnabledVersion() {
        return installedVersions.stream()
            .filter(NodeMetadata::isEnabled)
            .findFirst()
            .orElse(null);
    }
    
    public boolean hasInstalledVersions() {
        return !installedVersions.isEmpty();
    }
    
    public boolean isEnabled() {
        return getEnabledVersion() != null;
    }
}