package io.netnotes.engine.io.daemon;

import java.util.concurrent.CompletableFuture;

import io.netnotes.noteBytes.NoteBytes;

/**
 * Commands that ClientSessions can send to IODaemon
 * Executed on IODaemon's serialized executor
 */
public interface IODaemonInterface {
    CompletableFuture<Void> claimDevice(
        NoteBytes sessionId, 
        NoteBytes deviceId,
        NoteBytes mode
    );
    CompletableFuture<Void> releaseDevice(
        NoteBytes sessionId, 
        NoteBytes deviceId
    );
    void requestDiscovery(NoteBytes sessionId);
}
