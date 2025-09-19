package io.netnotes.engine;

import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.netnotes.engine.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;


public interface NodeControllerInterface {
    // Basic services

    NoteBytesReadOnly getControllerId();
    
    // Direct messaging

    CompletableFuture<Void> sendMessage(NoteBytesReadOnly toId, PipedOutputStream messageStream, PipedOutputStream replyStream);
    CompletableFuture<Void> sendMessage(NoteBytesArrayReadOnly toId, PipedOutputStream messageStream, PipedOutputStream replyStream);
    
    // Lifecycle
    CompletableFuture<Void> unregisterNode();
}
