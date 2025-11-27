package io.netnotes.engine.core.system.control.nodes;


import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;

/**
 * NodeInstaller - Downloads and installs nodes
 */
public class NodeInstaller {
    
    private final AppData appData;
    
    public NodeInstaller(AppData appData) {
        this.appData = appData;
    }
    
    public CompletableFuture<NodeMetadata> installNode(
            NodeRelease release, 
            boolean enabled) {
        
        System.out.println("[NodeInstaller] Installing " + 
            release.getNodeInfo().getName() + " version " + release.getVersion());
        
        return appData.getAppDataInterface(
                new NoteBytesReadOnly(NodeRegistry.NODES))
            .getNoteFile(release.getNodePath())
            .thenCompose(noteFile -> downloadToNoteFile(
                release.getDownloadUrl(), noteFile))
            .thenApply(v -> {
                // Create metadata
                NodeMetadata metadata = new NodeMetadata(
                    release.getNodeId(),
                    release.getNodeInfo().getName(),
                    release.getVersion(),
                    release.getNodeInfo().getCategory(),
                    release.getNodeInfo().getDescription(),
                    release.getNodePath(),
                    enabled
                );
                
                System.out.println("[NodeInstaller] Successfully installed " + 
                    metadata.getName());
                
                return metadata;
            });
    }
    
    private CompletableFuture<NoteBytesObject> downloadToNoteFile(
            String downloadUrl, 
            NoteFile noteFile) {
        
        PipedOutputStream outputStream = new PipedOutputStream();
        
        CompletableFuture<Void> downloadFuture = UrlStreamHelpers.transferUrlStream(
            downloadUrl, outputStream, null, appData.getExecService());
        
        CompletableFuture<NoteBytesObject> writeFuture = 
            noteFile.writeOnly(outputStream);
        
        return CompletableFuture.allOf(downloadFuture, writeFuture)
            .thenCompose(v -> writeFuture);
    }
}