package io.netnotes.engine.core.system.control.nodes;

import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

/**
 * NodeMetadata - Information about an installed node
 */
public class NodeMetadata {
    
    private final String nodeId;
    private final String name;
    private final String version;
    private final String category;
    private final String description;
    private final NoteStringArrayReadOnly nodePath;
    private boolean enabled;
    
    public NodeMetadata(
            String nodeId,
            String name,
            String version,
            String category,
            String description,
            NoteStringArrayReadOnly nodePath,
            boolean enabled) {
        
        this.nodeId = nodeId;
        this.name = name;
        this.version = version;
        this.category = category;
        this.description = description;
        this.nodePath = nodePath;
        this.enabled = enabled;
    }
    
    public String getNodeId() { return nodeId; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public NoteStringArrayReadOnly getNodePath() { return nodePath; }
    public boolean isEnabled() { return enabled; }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public NoteBytesObject getNoteBytesObject() {
        return new NoteBytesObject(
            new NoteBytesPair("nodeId", nodeId),
            new NoteBytesPair("name", name),
            new NoteBytesPair("version", version),
            new NoteBytesPair("category", category),
            new NoteBytesPair("description", description),
            new NoteBytesPair("nodePath", nodePath.getAsString())
        );
    }
    
    public static NodeMetadata of(NoteBytesMap map) {
        String nodeId = map.getByString("nodeId").getAsString();
        String name = map.getByString("name").getAsString();
        String version = map.getByString("version").getAsString();
        String category = map.has("category") ? 
            map.getByString("category").getAsString() : "General";
        String description = map.has("description") ? 
            map.getByString("description").getAsString() : "";
        String pathStr = map.getByString("nodePath").getAsString();
        NoteStringArrayReadOnly nodePath = NoteStringArrayReadOnly.parse(pathStr, "/");
        
        return new NodeMetadata(nodeId, name, version, category, description, 
            nodePath, false);
    }
}
