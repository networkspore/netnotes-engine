package io.netnotes.engine.io.daemon;

/**
 * Commands that ClientSessions can send to IODaemon
 * Executed on IODaemon's serialized executor
 */
public interface IDaemonCommands {
    void claimDevice(String sessionId, String deviceId, String mode);
    void releaseDevice(String sessionId, String deviceId);
    void requestDiscovery(String sessionId);
}
