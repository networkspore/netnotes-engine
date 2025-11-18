package io.netnotes.engine.io.daemon;

import io.netnotes.engine.io.daemon.DaemonProtocolState.*;
import io.netnotes.engine.state.StateEventRegistry.ClientStates;
import io.netnotes.engine.state.StateEventRegistry.DeviceStates;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Backpressure Manager - Flow control for daemon protocol
 * 
 * Responsibilities:
 * - Track message rates per client and device
 * - Detect when clients can't keep up with event streams
 * - Trigger PAUSE when backpressure threshold reached
 * - Detect when clients are ready to resume
 * - Handle stale connections (no activity)
 */
public class BackpressureManager {
    
    // Configuration
    private static final int DEFAULT_MAX_UNACKED = 100;
    private static final int RESUME_THRESHOLD = 50; // Resume when unacked drops below this
    private static final long STALE_CONNECTION_MS = 30000; // 30 seconds
    private static final long CHECK_INTERVAL_MS = 1000; // Check every second
    
    // Tracked sessions and devices
    private final Map<String, SessionMetrics> sessionMetrics = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
        r -> Thread.ofVirtual().name("BackpressureMonitor").unstarted(r)
    );
    
    private volatile boolean running = false;
    
    /**
     * Per-session metrics tracking
     */
    private static class SessionMetrics {
        final ClientSession session;
        final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());
        final Map<Integer, DeviceMetrics> deviceMetrics = new ConcurrentHashMap<>();
        
        SessionMetrics(ClientSession session) {
            this.session = session;
        }
        
        void recordActivity() {
            lastActivityTime.set(System.currentTimeMillis());
        }
        
        boolean isStale() {
            long now = System.currentTimeMillis();
            long lastActivity = lastActivityTime.get();
            return (now - lastActivity) > STALE_CONNECTION_MS;
        }
        
        int getUnacknowledgedCount() {
            return session.messagesSent.get() - session.messagesAcknowledged.get();
        }
    }
    
    /**
     * Per-device metrics tracking
     */
    private static class DeviceMetrics {
        final DeviceState device;
        final AtomicLong lastEventTime = new AtomicLong(System.currentTimeMillis());
        final AtomicLong eventsInWindow = new AtomicLong(0);
        final long windowStartTime;
        
        DeviceMetrics(DeviceState device) {
            this.device = device;
            this.windowStartTime = System.currentTimeMillis();
        }
        
        void recordEvent() {
            lastEventTime.set(System.currentTimeMillis());
            eventsInWindow.incrementAndGet();
        }
        
        boolean isStale() {
            long now = System.currentTimeMillis();
            long lastEvent = lastEventTime.get();
            return (now - lastEvent) > STALE_CONNECTION_MS;
        }
        
        int getPendingCount() {
            return device.pendingEvents.get();
        }
    }
    
    /**
     * Start monitoring
     */
    public void start() {
        if (running) {
            return;
        }
        
        running = true;
        scheduler.scheduleAtFixedRate(
            this::checkBackpressure,
            CHECK_INTERVAL_MS,
            CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        System.out.println("BackpressureManager started");
    }
    
    /**
     * Stop monitoring
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // ===== SESSION MANAGEMENT =====
    
    /**
     * Register a client session for monitoring
     */
    public void registerSession(ClientSession session) {
        sessionMetrics.put(session.sessionId, new SessionMetrics(session));
    }
    
    /**
     * Unregister a client session
     */
    public void unregisterSession(String sessionId) {
        sessionMetrics.remove(sessionId);
    }
    
    /**
     * Register a device for monitoring
     */
    public void registerDevice(ClientSession session, DeviceState device) {
        SessionMetrics metrics = sessionMetrics.get(session.sessionId);
        if (metrics != null) {
            metrics.deviceMetrics.put(device.sourceId, new DeviceMetrics(device));
        }
    }
    
    /**
     * Unregister a device
     */
    public void unregisterDevice(String sessionId, int sourceId) {
        SessionMetrics metrics = sessionMetrics.get(sessionId);
        if (metrics != null) {
            metrics.deviceMetrics.remove(sourceId);
        }
    }
    
    // ===== ACTIVITY TRACKING =====
    
    /**
     * Record that a message was sent to client
     */
    public void recordMessageSent(ClientSession session) {
        session.messageSent();
        
        SessionMetrics metrics = sessionMetrics.get(session.sessionId);
        if (metrics != null) {
            metrics.recordActivity();
        }
    }
    
    /**
     * Record that a device emitted an event
     */
    public void recordDeviceEvent(ClientSession session, DeviceState device) {
        SessionMetrics metrics = sessionMetrics.get(session.sessionId);
        if (metrics != null) {
            metrics.recordActivity();
            
            DeviceMetrics deviceMetrics = metrics.deviceMetrics.get(device.sourceId);
            if (deviceMetrics != null) {
                deviceMetrics.recordEvent();
            }
        }
    }
    
    /**
     * Record that client acknowledged messages
     */
    public void recordAcknowledgment(ClientSession session, int count) {
        session.messagesAcknowledged(count);
        
        SessionMetrics metrics = sessionMetrics.get(session.sessionId);
        if (metrics != null) {
            metrics.recordActivity();
        }
    }
    
    // ===== BACKPRESSURE CHECKS =====
    
    /**
     * Check if we can send more messages to a client
     */
    public boolean canSendToClient(ClientSession session) {
        // Check state flags first
        if (session.state.hasFlag(ClientStates.BACKPRESSURE_ACTIVE)) {
            return false;
        }
        
        if (session.state.hasFlag(ClientStates.FLOW_CONTROL_PAUSED)) {
            return false;
        }
        
        if (session.state.hasFlag(ClientStates.DISCONNECTING)) {
            return false;
        }
        
        // Check unacknowledged message count
        SessionMetrics metrics = sessionMetrics.get(session.sessionId);
        if (metrics != null) {
            int unacked = metrics.getUnacknowledgedCount();
            if (unacked >= session.maxUnacknowledgedMessages) {
                session.state.setFlag(ClientStates.BACKPRESSURE_ACTIVE);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if device can emit events
     */
    public boolean canEmitFromDevice(DeviceState device) {
        // Check device state
        if (device.state.hasFlag(DeviceStates.PAUSED)) {
            return false;
        }
        
        if (device.state.hasFlag(DeviceStates.DEVICE_ERROR)) {
            return false;
        }
        
        if (device.state.hasFlag(DeviceStates.DISCONNECTED)) {
            return false;
        }
        
        // Check backpressure - can still buffer if buffering enabled
        if (device.state.hasFlag(DeviceStates.BACKPRESSURE_ACTIVE)) {
            return device.state.hasFlag(DeviceStates.EVENT_BUFFERING);
        }
        
        return device.state.hasFlag(DeviceStates.STREAMING);
    }
    
    /**
     * Check if client should send RESUME
     * Called by client after processing events
     */
    public boolean shouldSendResume(ClientSession session) {
        if (!session.state.hasFlag(ClientStates.BACKPRESSURE_ACTIVE)) {
            return false; // No backpressure, no need to resume
        }
        
        SessionMetrics metrics = sessionMetrics.get(session.sessionId);
        if (metrics == null) {
            return false;
        }
        
        int unacked = metrics.getUnacknowledgedCount();
        
        // Resume if we've processed enough messages
        return unacked <= RESUME_THRESHOLD;
    }
    
    /**
     * Check if server should send PAUSE to client
     */
    public boolean shouldSendPause(ClientSession session) {
        SessionMetrics metrics = sessionMetrics.get(session.sessionId);
        if (metrics == null) {
            return false;
        }
        
        int unacked = metrics.getUnacknowledgedCount();
        
        // Pause if client is falling behind
        if (unacked >= session.maxUnacknowledgedMessages) {
            return !session.state.hasFlag(ClientStates.BACKPRESSURE_ACTIVE);
        }
        
        return false;
    }
    
    // ===== PERIODIC MONITORING =====
    
    /**
     * Periodic check for backpressure conditions
     */
    private void checkBackpressure() {
        try {
            for (SessionMetrics metrics : sessionMetrics.values()) {
                checkSessionBackpressure(metrics);
                checkSessionStale(metrics);
                checkDeviceBackpressure(metrics);
            }
        } catch (Exception e) {
            System.err.println("Error in backpressure check: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Check if session has backpressure
     */
    private void checkSessionBackpressure(SessionMetrics metrics) {
        ClientSession session = metrics.session;
        int unacked = metrics.getUnacknowledgedCount();
        
        // Activate backpressure if threshold exceeded
        if (unacked >= session.maxUnacknowledgedMessages) {
            if (!session.state.hasFlag(ClientStates.BACKPRESSURE_ACTIVE)) {
                session.state.setFlag(ClientStates.BACKPRESSURE_ACTIVE);
                System.out.println("Backpressure activated for session: " + 
                    session.sessionId + " (unacked=" + unacked + ")");
            }
        }
        // Clear backpressure if back below threshold
        else if (unacked < RESUME_THRESHOLD) {
            if (session.state.hasFlag(ClientStates.BACKPRESSURE_ACTIVE)) {
                session.state.clearFlag(ClientStates.BACKPRESSURE_ACTIVE);
                session.state.clearFlag(ClientStates.FLOW_CONTROL_PAUSED);
                System.out.println("Backpressure cleared for session: " + 
                    session.sessionId + " (unacked=" + unacked + ")");
            }
        }
    }
    
    /**
     * Check if session has gone stale
     */
    private void checkSessionStale(SessionMetrics metrics) {
        if (metrics.isStale()) {
            ClientSession session = metrics.session;
            if (!session.state.hasFlag(ClientStates.ERROR_STATE)) {
                System.out.println("Session stale: " + session.sessionId);
                session.state.setFlag(ClientStates.ERROR_STATE);
                session.state.setFlag(ClientStates.DISCONNECTING);
            }
        }
    }
    
    /**
     * Check device backpressure
     */
    private void checkDeviceBackpressure(SessionMetrics metrics) {
        for (DeviceMetrics deviceMetrics : metrics.deviceMetrics.values()) {
            DeviceState device = deviceMetrics.device;
            int pending = deviceMetrics.getPendingCount();
            
            // Activate device backpressure if too many pending events
            if (pending > 100) {
                if (!device.state.hasFlag(DeviceStates.BACKPRESSURE_ACTIVE)) {
                    device.state.setFlag(DeviceStates.BACKPRESSURE_ACTIVE);
                    System.out.println("Device backpressure activated: sourceId=" + 
                        device.sourceId + " (pending=" + pending + ")");
                }
            }
            // Clear device backpressure
            else if (pending < 50) {
                if (device.state.hasFlag(DeviceStates.BACKPRESSURE_ACTIVE)) {
                    device.state.clearFlag(DeviceStates.BACKPRESSURE_ACTIVE);
                    device.state.clearFlag(DeviceStates.EVENT_BUFFERING);
                    System.out.println("Device backpressure cleared: sourceId=" + 
                        device.sourceId + " (pending=" + pending + ")");
                }
            }
            
            // Check if device has gone stale
            if (deviceMetrics.isStale() && 
                device.state.hasFlag(DeviceStates.STREAMING)) {
                System.out.println("Device stale: sourceId=" + device.sourceId);
                device.state.setFlag(DeviceStates.STALE);
            }
        }
    }
    
    // ===== CLIENT RESUME HANDLING =====
    
    /**
     * Handle client RESUME message
     */
    public void handleClientResume(ClientSession session, int ackedCount) {
        recordAcknowledgment(session, ackedCount);
        
        // Resume all devices for this client
        for (DeviceState device : session.claimedDevices.values()) {
            if (device.state.hasFlag(DeviceStates.BACKPRESSURE_ACTIVE)) {
                device.state.clearFlag(DeviceStates.BACKPRESSURE_ACTIVE);
                device.state.clearFlag(DeviceStates.EVENT_BUFFERING);
            }
            
            if (device.state.hasFlag(DeviceStates.PAUSED)) {
                device.state.clearFlag(DeviceStates.PAUSED);
                device.state.setFlag(DeviceStates.STREAMING);
            }
        }
        
        System.out.println("Client resumed: " + session.sessionId + 
            " (acked=" + ackedCount + ")");
    }
    
    // ===== STATISTICS =====
    
    /**
     * Get statistics for a session
     */
    public Map<String, Object> getSessionStats(String sessionId) {
        SessionMetrics metrics = sessionMetrics.get(sessionId);
        if (metrics == null) {
            return Collections.emptyMap();
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("session_id", sessionId);
        stats.put("unacknowledged", metrics.getUnacknowledgedCount());
        stats.put("last_activity", metrics.lastActivityTime.get());
        stats.put("is_stale", metrics.isStale());
        stats.put("has_backpressure", 
            metrics.session.state.hasFlag(ClientStates.BACKPRESSURE_ACTIVE));
        
        List<Map<String, Object>> deviceStats = new ArrayList<>();
        for (DeviceMetrics dm : metrics.deviceMetrics.values()) {
            Map<String, Object> ds = new HashMap<>();
            ds.put("source_id", dm.device.sourceId);
            ds.put("pending_events", dm.getPendingCount());
            ds.put("events_sent", dm.device.eventsSent);
            ds.put("events_dropped", dm.device.eventsDropped);
            ds.put("last_event_time", dm.lastEventTime.get());
            ds.put("has_backpressure", 
                dm.device.state.hasFlag(DeviceStates.BACKPRESSURE_ACTIVE));
            deviceStats.add(ds);
        }
        stats.put("devices", deviceStats);
        
        return stats;
    }
    
    /**
     * Get all session statistics
     */
    public List<Map<String, Object>> getAllStats() {
        List<Map<String, Object>> allStats = new ArrayList<>();
        for (String sessionId : sessionMetrics.keySet()) {
            allStats.add(getSessionStats(sessionId));
        }
        return allStats;
    }
}