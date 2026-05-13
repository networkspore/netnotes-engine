package io.netnotes.engine.io.daemon;

import java.util.concurrent.CompletableFuture;

import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesArray;

/**
 * Commands that ClientSessions can send to IODaemon
 * Executed on IODaemon's serialized executor
 */
public interface IODaemonInterface {
    /**
     * Claim a device using module_id for routing
     * @param sessionId The session ID
     * @param moduleId The module to route to (e.g., "note_usb")
     * @param deviceId The device to claim
     * @param mode The mode (e.g., "parsed", "raw")
     */
    CompletableFuture<Void> claimDevice(
        NoteBytes sessionId, 
        NoteBytes moduleId,
        NoteBytes deviceId,
        NoteBytes mode
    );
    
    /**
     * Release a device using module_id for routing
     * @param sessionId The session ID
     * @param moduleId The module to route to (e.g., "note_usb")
     * @param deviceId The device to release
     */
    CompletableFuture<Void> releaseDevice(
        NoteBytes sessionId, 
        NoteBytes moduleId,
        NoteBytes deviceId
    );
    
    void requestDiscovery(NoteBytes sessionId);
    
    /**
     * Request list of available modules from daemon
     * @param sessionId The session ID
     * @return CompletableFuture that completes with NoteBytesArray of module info
     */
    CompletableFuture<NoteBytesArray> getModules(NoteBytes sessionId);
}
