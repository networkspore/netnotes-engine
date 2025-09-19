package io.netnotes.engine;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;


import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public interface Node {
    NoteBytesReadOnly getNodeId();
    CompletableFuture<Void> initialize(AppDataInterface appInterface);
    void setNodeControllerInterface(NodeControllerInterface nodeControllerInterface);
    // Receives any kind of message stream - node decides how to handle based on header
    CompletableFuture<Void> receiveRawMessage(PipedOutputStream messageStream, PipedOutputStream replyStream) throws IOException;
    
    // Lifecycle
    CompletableFuture<Void> shutdown();
    boolean isActive();
}
