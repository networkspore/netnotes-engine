package io.netnotes.engine;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import javax.naming.NameNotFoundException;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public class NodeController {

    private final AppData appData;
    private final NoteBytesReadOnly controllerId;
    private final NodeBroadcaster m_nodeBroadcaster;
    
    public NodeController(NoteBytesReadOnly controllerId, AppData appData) {
        this.appData = appData;
        this.controllerId = controllerId;
        m_nodeBroadcaster = new NodeBroadcaster(appData.nodeRegistry(), appData.getExecService(), controllerId);
    }
    
    // Core interface that nodes get - just stream routing
    public NodeControllerInterface getNodeControllerInterface(NoteBytesReadOnly nodeId) {
        return new StreamingNodeControllerInterface(nodeId);
    }
    

    // Route message to specific node
    private CompletableFuture<Void> routeToNode(NoteBytes fromId, NoteBytes toId, 
                                                          PipedOutputStream messageStream, 
                                                          PipedOutputStream replyStream) {
        INode targetNode = appData.nodeRegistry().get(toId);
        if (targetNode == null) {
            return CompletableFuture.failedFuture(new NameNotFoundException("Node not found: " + toId));
        }
        try {
            return targetNode.receiveRawMessage(messageStream, replyStream);
   
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
 

    private class StreamingNodeControllerInterface implements NodeControllerInterface {
        private final NoteBytesReadOnly nodeId;


        public StreamingNodeControllerInterface(NoteBytesReadOnly nodeId) {
            this.nodeId = nodeId;
        }
        
   
        @Override
        public NoteBytesReadOnly getControllerId() {
            return controllerId;
        }
        
   
        // Send message to specific node
        @Override
        public CompletableFuture<Void> sendMessage(NoteBytesReadOnly toId, PipedOutputStream messageStream, 
            PipedOutputStream replyStream
        ) {
            return routeToNode(this.nodeId, toId, messageStream, replyStream);
        }
        


        @Override
        public CompletableFuture<Void> sendMessage(NoteBytesReadOnly[] toIds, PipedOutputStream messageStream, 
            PipedOutputStream replyStream
        ) {
            
            return m_nodeBroadcaster.sendMessage(nodeId, toIds, messageStream, replyStream);
        }


        
        @Override
        public CompletableFuture<Void> unregisterNode() {
            return CompletableFuture.runAsync(() -> {
                appData.nodeRegistry().remove(this.nodeId);
            }, appData.getExecService());
        }
        
    }
}