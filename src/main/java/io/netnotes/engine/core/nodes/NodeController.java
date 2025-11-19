package io.netnotes.engine.core.nodes;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.NameNotFoundException;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.FlowProcessRegistry;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

public class NodeController extends FlowProcess {
    
    private final AppData appData;
    private final NoteBytesReadOnly controllerId;
    
    // ALL nodes stored as INode interface
    private final Map<NoteBytesReadOnly, INode> nodeRegistry;
    
    private final FlowProcessRegistry processRegistry;
    private final Map<NoteBytesReadOnly, CompletableFuture<Void>> initializationFutures;
    
    public NodeController(
            NoteBytesReadOnly controllerId,
            AppData appData,
            FlowProcessRegistry processRegistry) {
        
        super(ProcessType.BIDIRECTIONAL);
        this.controllerId = NoteUUID.createLocalUUID128();
        this.appData = appData;
        this.nodeRegistry = appData.nodeRegistry();
        this.processRegistry = processRegistry;
        this.initializationFutures = new ConcurrentHashMap<>();
    }
    
    /**
     * Initialize NodeController
     */
    public CompletableFuture<Void> initialize() {
        ContextPath controllerPath = ContextPath.of("system", "controller", "nodes");
        processRegistry.registerProcess(this, controllerPath, null);
        System.out.println("NodeController initialized at: " + controllerPath);
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== NODE REGISTRATION =====
    
    /**
     * Register ANY INode implementation
     * Works with BaseNode, DelegatingNode, or custom implementations
     */
    public CompletableFuture<ContextPath> registerNode(INode node) {
        NoteBytesReadOnly nodeId = node.getNodeId();
        ContextPath nodePath = getContextPath().append(nodeId.toString());
        
        return CompletableFuture.supplyAsync(() -> {
            
            // 1. Register FlowProcess component
            FlowProcess flowProcess = getFlowProcessFromNode(node);
            if (flowProcess != null) {
                processRegistry.registerProcess(
                    flowProcess,
                    nodePath,
                    getContextPath() // NodeController is parent
                );
            }
            
            // 2. Register in node registry (for stream routing)
            nodeRegistry.put(nodeId, node);
            
            // 3. Give Node its controller interface
            node.setNodeControllerInterface(
                new StreamingNodeController(nodeId)
            );
            
            // 4. Initialize Node
            CompletableFuture<Void> initFuture = node.initialize(appData.getAppDataInterface(nodeId));
            initializationFutures.put(nodeId, initFuture);
            
            // Wait for initialization
            initFuture.join();
            
            System.out.println("Node registered: " + nodePath + " (active: " + node.isActive() + ")");
            
            return nodePath;
            
        }, appData.getExecService());
    }
    
    /**
     * Extract FlowProcess from INode implementation
     */
    private FlowProcess getFlowProcessFromNode(INode node) {
        if (node instanceof FlowProcess fp) {
            return fp;
        } else if (node instanceof DelegatingNode dn) {
            return dn.getInternalFlowProcess();
        }
        return null;
    }
    
    /**
     * Start Node's background tasks (if it has subscribers)
     */
    public CompletableFuture<Void> startNode(NoteBytesReadOnly nodeId) {
        INode node = nodeRegistry.get(nodeId);
        if (node == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Node not found: " + nodeId)
            );
        }
        
        // Only start if node has background tasks and subscribers
        if (node.hasBackgroundTasks() && node.hasFlowSubscribers()) {
            FlowProcess flowProcess = getFlowProcessFromNode(node);
            if (flowProcess != null) {
                return processRegistry.startProcess(flowProcess.getContextPath());
            }
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Unregister Node
     */
    public CompletableFuture<Void> unregisterNode(NoteBytesReadOnly nodeId) {
        return CompletableFuture.runAsync(() -> {
            INode node = nodeRegistry.remove(nodeId);
            if (node == null) return;
            
            // Shutdown node
            if (node.isActive()) {
                node.shutdown().join();
            }
            
            // Remove from FlowProcessRegistry
            FlowProcess flowProcess = getFlowProcessFromNode(node);
            if (flowProcess != null) {
                processRegistry.unregisterProcess(flowProcess.getContextPath());
            }
            
            initializationFutures.remove(nodeId);
            
            System.out.println("Node unregistered: " + nodeId);
            
        }, appData.getExecService());
    }
    
    // ===== STREAM ROUTING =====
    
    /**
     * Route direct stream to any INode implementation
     */
    private CompletableFuture<Void> routeStreamToNode(
            NoteBytes fromId,
            NoteBytes toId,
            PipedOutputStream messageStream,
            PipedOutputStream replyStream) {
        
        INode targetNode = nodeRegistry.get(toId);
        if (targetNode == null) {
            return CompletableFuture.failedFuture(
                new NameNotFoundException("Node not found: " + toId)
            );
        }
        
        if (!targetNode.isActive()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Node not active: " + toId)
            );
        }
        
        try {
            return targetNode.receiveRawMessage(messageStream, replyStream);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    // ===== FLOWPROCESS INTERFACE =====
    
    @Override
    public CompletableFuture<Void> run() {
        return CompletableFuture.runAsync(() -> {
            while (isAlive()) {
                sleep(Duration.ofSeconds(30));
                monitorNodes();
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        try {
            NoteBytesMap command = packet.getPayload().getAsNoteBytesMap();
            String action = command.get("action").getAsString();
            
            return switch (action) {
                case "list_nodes" -> handleListNodes(packet);
                case "node_status" -> handleNodeStatus(command, packet);
                case "start_node" -> handleStartNode(command, packet);
                default -> CompletableFuture.completedFuture(null);
            };
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private void monitorNodes() {
        for (Map.Entry<NoteBytesReadOnly, INode> entry : nodeRegistry.entrySet()) {
            INode node = entry.getValue();
            if (!node.isActive()) {
                emit(RoutedPacket
                    .create(
                        contextPath, 
                        new NoteBytesPair("event", "node_inactive"),
                        new NoteBytesPair("node_id", entry.getKey())
                    )
                );
            }
        }
    }
    
    private CompletableFuture<Void> handleListNodes(RoutedPacket request) {
        NoteBytesMap response = new NoteBytesMap();
        response.put("count", new NoteBytes(nodeRegistry.size()));
        
        NoteBytesArray nodeList = new NoteBytesArray();
        for (INode node : nodeRegistry.values()) {
         
            
            nodeList.add(new NoteBytesObject(new NoteBytesPair[]{
                new NoteBytesPair("id", node.getNodeId()),
                new NoteBytesPair("active", new NoteBytes(node.isActive())),
                new NoteBytesPair("subscribers", new NoteBytes(node.getFlowSubscriberCount())),
                new NoteBytesPair("path", new NoteBytes(node.getContextPath().toString()))
            }));
        }
        response.put("nodes", nodeList);
  
        reply(request, response.getNoteBytesObject());
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleNodeStatus(
            NoteBytesMap command,
            RoutedPacket request) {
        
        NoteBytes nodeId = command.get("node_id");
        INode node = nodeRegistry.get(nodeId);
        
        NoteBytesMap response = new NoteBytesMap();
        if (node == null) {
            response.put("status", new NoteBytes("not_found"));
        } else {
            response.put("status", new NoteBytes(node.isActive() ? "active" : "inactive"));
            response.put("id", node.getNodeId());
            response.put("path", new NoteBytes(node.getContextPath().toString()));
            response.put("subscribers", new NoteBytes(node.getFlowSubscriberCount()));
        }
        
        reply(request, response.getNoteBytesObject());
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleStartNode(
            NoteBytesMap command,
            RoutedPacket request) {
        
        NoteBytes nodeId = command.get("node_id");
        return startNode(nodeId.readOnly())
            .thenRun(() -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put("status", new NoteBytes("started"));
                reply(request, response.getNoteBytesObject());
            });
    }
    
    // ===== QUERIES =====
    
    public INode getNode(NoteBytesReadOnly nodeId) {
        return nodeRegistry.get(nodeId);
    }
    
    public Collection<INode> getAllNodes() {
        return nodeRegistry.values();
    }
    
    public int getNodeCount() {
        return nodeRegistry.size();
    }
    
    // ===== NODE CONTROLLER INTERFACE =====
    
    private class StreamingNodeController implements NodeControllerInterface {
        private final NoteBytesReadOnly nodeId;
        
        public StreamingNodeController(NoteBytesReadOnly nodeId) {
            this.nodeId = nodeId;
        }
        
        @Override
        public NoteBytesReadOnly getControllerId() {
            return NodeController.this.controllerId;
        }
        
        @Override
        public CompletableFuture<Void> sendMessage(
                NoteBytesReadOnly toId,
                PipedOutputStream messageStream,
                PipedOutputStream replyStream) {
            return NodeController.this.routeStreamToNode(
                this.nodeId, toId, messageStream, replyStream
            );
        }
       
        @Override
        public CompletableFuture<Void> unregisterNode() {
            return NodeController.this.unregisterNode(this.nodeId);
        }
    }
}