package io.netnotes.engine.core.system.control.nodes;


import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;

/**
 * NodeRelease - Specific version release information
 */
public class NodeRelease {
    
    private final NodeInformation nodeInfo;
    private final String version;
    private final String downloadUrl;
    private final long size;
    private final String releaseNotes;
    
    public NodeRelease(
            NodeInformation nodeInfo,
            String version,
            String downloadUrl,
            long size,
            String releaseNotes) {
        
        this.nodeInfo = nodeInfo;
        this.version = version;
        this.downloadUrl = downloadUrl;
        this.size = size;
        this.releaseNotes = releaseNotes;
    }
    
    public NodeInformation getNodeInfo() { return nodeInfo; }
    public String getVersion() { return version; }
    public String getDownloadUrl() { return downloadUrl; }
    public long getSize() { return size; }
    public String getReleaseNotes() { return releaseNotes; }
    
    public NoteStringArrayReadOnly getNodePath() {
        return new NoteStringArrayReadOnly(
            NodeRegistry.NODES,
            nodeInfo.getName(),
            version,
            String.valueOf(downloadUrl.hashCode())
        );
    }
    
    public String getNodeId() {
        return getNodePath().getAsString();
    }
}