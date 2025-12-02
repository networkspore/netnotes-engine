package io.netnotes.engine.core.system.control.nodes.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.core.system.SystemProcess;
import io.netnotes.engine.core.system.control.nodes.security.PathCapability.MatchMode;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

import java.util.*;

/**
 * PolicyManifest - Security policy declared in package manifest
 * 
 * Simplified to just request PathCapabilities.
 * No required/optional distinction - user approves all or denies installation.
 * 
 * Example manifest.json:
 * {
 *   "name": "example-node",
 *   "version": "1.0.0",
 *   "security_policy": {
 *     "requested_capabilities": [
 *       {
 *         "path": "/system/services/*",
 *         "service_names": ["io-daemon"],
 *         "operations": ["MESSAGE", "STREAM"],
 *         "reason": "Secure password input"
 *       },
 *       {
 *         "path": "/system/nodes/*",
 *         "operations": ["MESSAGE"],
 *         "reason": "Communicate with other nodes"
 *       }
 *     ]
 *   }
 * }
 */
public class PolicyManifest {
    
    private final List<PathCapability> requestedCapabilities;
    private final ClusterConfig clusterConfig;
    
    public PolicyManifest(
            List<PathCapability> requestedCapabilities,
            ClusterConfig clusterConfig) {
        this.requestedCapabilities = new ArrayList<>(requestedCapabilities);
        this.clusterConfig = clusterConfig;
    }
    
    // ===== GETTERS =====
    
    public List<PathCapability> getRequestedCapabilities() {
        return Collections.unmodifiableList(requestedCapabilities);
    }
    
    public ClusterConfig getClusterConfig() {
        return clusterConfig;
    }
    
    public boolean hasClusterConfig() {
        return clusterConfig != null;
    }
    
    /**
     * Get capabilities that require user approval (not default granted)
     */
    public List<PathCapability> getApprovalRequiredCapabilities() {
        return requestedCapabilities.stream()
            .filter(cap -> !cap.isDefaultGranted())
            .toList();
    }
    
    /**
     * Get capabilities that are granted by default
     */
    public List<PathCapability> getDefaultCapabilities() {
        return requestedCapabilities.stream()
            .filter(PathCapability::isDefaultGranted)
            .toList();
    }
    
    // ===== VALIDATION =====
    
    /**
     * Validate manifest structure
     */
    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        
        // Validate each capability
        for (PathCapability cap : requestedCapabilities) {
            if (cap.getPathPattern() == null || cap.getPathPattern().isEmpty()) {
                errors.add("Capability missing path pattern");
            }
            if (cap.getOperations().isEmpty()) {
                errors.add("Capability missing operations: " + cap.getPathPattern());
            }
            if (cap.getReason() == null || cap.getReason().isEmpty()) {
                errors.add("Capability missing reason: " + cap.getPathPattern());
            }
        }
        
        // Validate cluster config
        if (clusterConfig != null) {
            List<String> clusterErrors = clusterConfig.validate();
            errors.addAll(clusterErrors);
        }
        
        return errors;
    }
    
    /**
     * Check if manifest requests sensitive paths
     */
    public boolean hasSensitivePaths() {
        for (PathCapability cap : requestedCapabilities) {
            ContextPath pattern = cap.getPathPattern();
            
            // System services are sensitive
            if (pattern.startsWith(SystemProcess.SERVICES_PATH)) {
                return true;
            }

            if (pattern.startsWith(SystemProcess.NODES_PATH)){
                return true;
            }

            if (pattern.startsWith(SystemProcess.SHARED_PATH)){
                return true;
            }
            
        }
        return false;
    }
    
    // ===== SERIALIZATION: JSON (External - in package manifest) =====
    
    /**
     * Parse from JSON in package manifest
     */
    public static PolicyManifest fromJson(JsonObject json) {
        List<PathCapability> capabilities = new ArrayList<>();
        ClusterConfig cluster = null;
        
        // Parse requested capabilities
        if (json.has("requested_capabilities") && json.get("requested_capabilities").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("requested_capabilities");
            for (JsonElement elem : arr) {
                JsonObject capJson = elem.getAsJsonObject();
                capabilities.add(parseCapabilityFromJson(capJson));
            }
        }
        
        // Parse cluster config
        if (json.has("cluster")) {
            cluster = ClusterConfig.fromJson(json.getAsJsonObject("cluster"));
        }
        
        return new PolicyManifest(capabilities, cluster);
    }
    
    private static PathCapability parseCapabilityFromJson(JsonObject json) {
        String pathPattern = json.get("path").getAsString();
        String reason = json.has("reason") ? json.get("reason").getAsString() : "";
        String matchModeString = json.has("matchMode") ? json.get("matchMode").getAsString() : "PREFIX";
        // Parse operations
        Set<PathCapability.Operation> operations = new HashSet<>();
        if (json.has("operations") && json.get("operations").isJsonArray()) {
            JsonArray opsArr = json.getAsJsonArray("operations");
            for (JsonElement elem : opsArr) {
                operations.add(PathCapability.Operation.valueOf(elem.getAsString()));
            }
        }
        
        // Parse service names (for /system/services/* paths)
        List<String> serviceNames = null;
        if (json.has("service_names") && json.get("service_names").isJsonArray()) {
            serviceNames = new ArrayList<>();
            JsonArray namesArr = json.getAsJsonArray("service_names");
            for (JsonElement elem : namesArr) {
                serviceNames.add(elem.getAsString());
            }
        }

        MatchMode matchMode = MatchMode.valueOf(matchModeString.toUpperCase());
        
        return new PathCapability(ContextPath.parse(pathPattern), operations, false, reason, serviceNames, matchMode);
    }
    
    /**
     * Convert to JSON for package manifest
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        
        // Requested capabilities
        JsonArray capsArr = new JsonArray();
        for (PathCapability cap : requestedCapabilities) {
            capsArr.add(capabilityToJson(cap));
        }
        json.add("requested_capabilities", capsArr);
        
        // Cluster config
        if (clusterConfig != null) {
            json.add("cluster", clusterConfig.toJson());
        }
        
        return json;
    }
    
    private JsonObject capabilityToJson(PathCapability cap) {
        JsonObject json = new JsonObject();
        json.addProperty("path", cap.getPathPattern().toString());
        
        // Operations
        JsonArray opsArr = new JsonArray();
        cap.getOperations().forEach(op -> opsArr.add(op.name()));
        json.add("operations", opsArr);
        
        json.addProperty("reason", cap.getReason());
        
        // Service names (if applicable)
        if (cap.getServiceNames() != null && !cap.getServiceNames().isEmpty()) {
            JsonArray namesArr = new JsonArray();
            cap.getServiceNames().forEach(namesArr::add);
            json.add("service_names", namesArr);
        }
        
        return json;
    }
    
    // ===== SERIALIZATION: NoteBytes (Internal storage) =====
    
    public NoteBytesObject toNoteBytes() {
        NoteBytesMap map = new NoteBytesMap();
        
        // Requested capabilities
        NoteBytesArray capsArr = new NoteBytesArray();
        requestedCapabilities.forEach(cap -> capsArr.add(cap.toNoteBytes()));
        map.put("requested_capabilities", capsArr);
        
        // Cluster config
        if (clusterConfig != null) {
            map.put("cluster", clusterConfig.toNoteBytes());
        }
        
        return map.getNoteBytesObject();
    }
    
    public static PolicyManifest fromNoteBytes(NoteBytesObject obj) {
        NoteBytesMap map = obj.getAsNoteBytesMap();
        
        List<PathCapability> capabilities = new ArrayList<>();
        ClusterConfig cluster = null;
        
        // Parse capabilities
        NoteBytes capsBytes = map.get("requested_capabilities");
        if (capsBytes != null) {
            NoteBytesArray arr = capsBytes.getAsNoteBytesArray();
            for (NoteBytes b : arr.getAsArray()) {
                capabilities.add(PathCapability.fromNoteBytes(b.getAsNoteBytesObject()));
            }
        }
        
        // Parse cluster
        NoteBytes clusterBytes = map.get("cluster");
        if (clusterBytes != null) {
            cluster = ClusterConfig.fromNoteBytes(clusterBytes.getAsNoteBytesObject());
        }
        
        return new PolicyManifest(capabilities, cluster);
    }
    
    // ===== CLUSTER CONFIG =====
    
    public static class ClusterConfig {
        private final String clusterId;
        private final ClusterRole role;
        private final String sharedPath;
        private final int maxMembers;
        
        public ClusterConfig(String clusterId, ClusterRole role, String sharedPath, int maxMembers) {
            this.clusterId = clusterId;
            this.role = role;
            this.sharedPath = sharedPath;
            this.maxMembers = maxMembers;
        }
        
        public String getClusterId() { return clusterId; }
        public ClusterRole getRole() { return role; }
        public String getSharedPath() { return sharedPath; }
        public int getMaxMembers() { return maxMembers; }
        
        public List<String> validate() {
            List<String> errors = new ArrayList<>();
            
            if (clusterId == null || clusterId.isEmpty()) {
                errors.add("Cluster ID required");
            }
            if (role == null) {
                errors.add("Cluster role required");
            }
            if (sharedPath == null || sharedPath.isEmpty()) {
                errors.add("Cluster shared path required");
            }
            if (maxMembers < 0) {
                errors.add("Max members cannot be negative");
            }
            
            return errors;
        }
        
        public static ClusterConfig fromJson(JsonObject json) {
            return new ClusterConfig(
                json.get("cluster_id").getAsString(),
                ClusterRole.valueOf(json.get("role").getAsString().toUpperCase()),
                json.has("shared_path") ? json.get("shared_path").getAsString() : "",
                json.has("max_members") ? json.get("max_members").getAsInt() : 0
            );
        }
        
        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("cluster_id", clusterId);
            json.addProperty("role", role.name().toLowerCase());
            json.addProperty("shared_path", sharedPath);
            json.addProperty("max_members", maxMembers);
            return json;
        }
        
        public NoteBytesObject toNoteBytes() {
            NoteBytesMap map = new NoteBytesMap();
            map.put("cluster_id", clusterId);
            map.put("role", role.name());
            map.put("shared_path", sharedPath);
            map.put("max_members", maxMembers);
            return map.getNoteBytesObject();
        }
        
        public static ClusterConfig fromNoteBytes(NoteBytesObject obj) {
            NoteBytesMap map = obj.getAsNoteBytesMap();
            return new ClusterConfig(
                map.get("cluster_id").getAsString(),
                ClusterRole.valueOf(map.get("role").getAsString()),
                map.get("shared_path").getAsString(),
                map.get("max_members").getAsInt()
            );
        }
    }
    
    public enum ClusterRole {
        LEADER,         // Creates cluster, coordinates members
        MEMBER,         // Joins existing cluster
        SHARED_LEADER   // Can operate independently or in cluster
    }
    
    // ===== BUILDER =====
    
    public static class Builder {
        private final List<PathCapability> capabilities = new ArrayList<>();
        private ClusterConfig cluster = null;
        
        public Builder requestCapability(PathCapability capability) {
            capabilities.add(capability);
            return this;
        }
        
        public Builder requestServiceAccess(Set<PathCapability.Operation> operations, String reason, String... serviceNames) {
            capabilities.add(PathCapability.accessServices(operations, serviceNames));
            return this;
        }
        
        public Builder requestNodeMessaging(String reason) {
            capabilities.add(new PathCapability(
                SystemProcess.NODES_PATH,
                Set.of(PathCapability.Operation.MESSAGE),
                false,
                reason,null,MatchMode.PREFIX
            ));
            return this;
        }
        
        public Builder requestSharedData(String reason) {
            capabilities.add(PathCapability.sharedUserData());
            return this;
        }
        
        public Builder cluster(String clusterId, ClusterRole role, String sharedPath, int maxMembers) {
            this.cluster = new ClusterConfig(clusterId, role, sharedPath, maxMembers);
            return this;
        }
        
        public PolicyManifest build() {
            return new PolicyManifest(capabilities, cluster);
        }
    }
}