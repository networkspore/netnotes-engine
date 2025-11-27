package io.netnotes.engine.core.system.control.nodes;


/**
 * NodeInformation - Metadata about an available node
 * (from marketplace/catalog)
 */
public class NodeInformation {
    
    private final String nodeId;
    private final String name;
    private final String category;
    private final String description;
    private final String author;
    private final String latestVersion;
    private final String downloadUrl;
    
    public NodeInformation(
            String nodeId,
            String name,
            String category,
            String description,
            String author,
            String latestVersion,
            String downloadUrl) {
        
        this.nodeId = nodeId;
        this.name = name;
        this.category = category;
        this.description = description;
        this.author = author;
        this.latestVersion = latestVersion;
        this.downloadUrl = downloadUrl;
    }
    
    public String getNodeId() { return nodeId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public String getLatestVersion() { return latestVersion; }
    public String getDownloadUrl() { return downloadUrl; }
}
