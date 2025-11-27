package io.netnotes.engine.core.system.control.nodes;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * NodeGroupManager - Manages grouped view of nodes
 */
public class NodeGroupManager {
    
    private final Map<String, NodeGroup> groups;
    
    public NodeGroupManager() {
        this.groups = new ConcurrentHashMap<>();
    }
    
    public void buildFromRegistry(
            List<NodeMetadata> installedNodes,
            List<NodeInformation> availableNodes) {
        
        groups.clear();
        
        // Create groups from available nodes
        for (NodeInformation nodeInfo : availableNodes) {
            String nodeId = nodeInfo.getNodeId();
            if (!groups.containsKey(nodeId)) {
                groups.put(nodeId, new NodeGroup(nodeId, nodeInfo));
            }
        }
        
        // Add installed versions to groups
        for (NodeMetadata metadata : installedNodes) {
            // Extract base node ID (without version)
            String baseNodeId = extractBaseNodeId(metadata.getNodeId());
            NodeGroup group = groups.get(baseNodeId);
            
            if (group != null) {
                group.addVersion(metadata);
            } else {
                System.out.println("[NodeGroupManager] Installed node '" + 
                    metadata.getName() + "' not found in available list");
            }
        }
    }
    
    private String extractBaseNodeId(String fullNodeId) {
        // Extract base ID from path like "nodes/NodeName/version/hash"
        String[] parts = fullNodeId.split("/");
        return parts.length >= 2 ? parts[0] + "/" + parts[1] : fullNodeId;
    }
    
    public List<NodeGroup> getAllGroups() {
        return new ArrayList<>(groups.values());
    }
    
    public List<NodeGroup> getBrowseGroups() {
        return new ArrayList<>(groups.values());
    }
    
    public List<NodeGroup> getInstalledGroups() {
        return groups.values().stream()
            .filter(NodeGroup::hasInstalledVersions)
            .collect(Collectors.toList());
    }
    
    public NodeGroup getGroup(String nodeId) {
        return groups.get(nodeId);
    }
    
    public int getAvailableCount() {
        return groups.size();
    }
    
    public int getInstalledCount() {
        return (int) groups.values().stream()
            .filter(NodeGroup::hasInstalledVersions)
            .count();
    }
    
    public int getEnabledCount() {
        return (int) groups.values().stream()
            .filter(NodeGroup::isEnabled)
            .count();
    }
    
    public void clear() {
        groups.clear();
    }
}