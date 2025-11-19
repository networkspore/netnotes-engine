package io.netnotes.engine.core.nodes;

import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;


public interface NodeControllerInterface {
    // Basic services

    NoteBytesReadOnly getControllerId();
    
    // Direct messaging

    CompletableFuture<Void> sendMessage(NoteBytesReadOnly toId, PipedOutputStream messageStream, PipedOutputStream replyStream);

    // Lifecycle
    CompletableFuture<Void> unregisterNode();
}
