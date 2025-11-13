package io.netnotes.engine.io.daemon;


import io.netnotes.engine.state.BitFlagStateMachine;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Daemon Protocol State Management
 * 
 * Key Design:
 * - Each client session has its own state machine
 * - Phases are not strictly sequential - clients can discover during streaming
 * - Heartbeat (PING/PONG) runs continuously with timeouts
 * - Backpressure limits message rate per device
 * - BitFlags for client state, device state, and stream state
 */
public class DaemonProtocolState {
    
    // ===== CLIENT STATE FLAGS (BitFlagStateMachine) =====
    
    /**
     * Client session state flags (per connection)
     */
    public static class ClientStateFlags {
        // Connection state (bits 0-7)
        public static final long CONNECTED           = 1L << 0;   // Socket connected
        public static final long AUTHENTICATED       = 1L << 1;   // Handshake complete
        public static final long DISCOVERING         = 1L << 2;   // In discovery phase
        public static final long HAS_CLAIMED_DEVICES = 1L << 3;   // Has at least one claimed device
        public static final long STREAMING           = 1L << 4;   // Receiving events
        public static final long PAUSED              = 1L << 5;   // Streaming paused
        public static final long DISCONNECTING       = 1L << 6;   // Shutdown initiated
        public static final long ERROR_STATE         = 1L << 7;   // Error occurred
        
        // Capabilities (bits 8-15)
        public static final long SUPPORTS_ENCRYPTION = 1L << 8;
        public static final long SUPPORTS_RAW_MODE   = 1L << 9;
        public static final long SUPPORTS_FILTERING  = 1L << 10;
        public static final long SUPPORTS_BATCH      = 1L << 11;
        
        // Heartbeat state (bits 16-23)
        public static final long HEARTBEAT_ENABLED   = 1L << 16;
        public static final long HEARTBEAT_WAITING   = 1L << 17;  // Waiting for PONG
        public static final long HEARTBEAT_TIMEOUT   = 1L << 18;  // No PONG received
        
        // Backpressure state (bits 24-31)
        public static final long BACKPRESSURE_ACTIVE = 1L << 24;  // Hit message limit
        public static final long FLOW_CONTROL_PAUSED = 1L << 25;  // Waiting for resume
        public static final long QUEUE_FULL          = 1L << 26;  // Output queue full
        
        /**
         * Check if client can discover (any time except during disconnect)
         */
        public static boolean canDiscover(BitFlagStateMachine sm) {
            return sm.hasFlag(AUTHENTICATED) && !sm.hasFlag(DISCONNECTING);
        }
        
        /**
         * Check if client can claim devices
         */
        public static boolean canClaim(BitFlagStateMachine sm) {
            return sm.hasFlag(AUTHENTICATED) && !sm.hasFlag(DISCONNECTING);
        }
        
        /**
         * Check if client can stream
         */
        public static boolean canStream(BitFlagStateMachine sm) {
            return sm.hasFlag(HAS_CLAIMED_DEVICES) && 
                   !sm.hasFlag(PAUSED) &&
                   !sm.hasFlag(BACKPRESSURE_ACTIVE) &&
                   !sm.hasFlag(DISCONNECTING);
        }
        
        /**
         * Check if heartbeat is healthy
         */
        public static boolean isHeartbeatHealthy(BitFlagStateMachine sm) {
            return sm.hasFlag(HEARTBEAT_ENABLED) && 
                   !sm.hasFlag(HEARTBEAT_TIMEOUT);
        }
    }
    
    // ===== DEVICE STATE FLAGS (per claimed device) =====
    
    /**
     * Device state flags (per claimed device instance)
     */
    public static class DeviceStateFlags {
        // Claim state (bits 0-7)
        public static final long CLAIMED             = 1L << 0;   // Device claimed
        public static final long KERNEL_DETACHED     = 1L << 1;   // Kernel driver detached
        public static final long INTERFACE_CLAIMED   = 1L << 2;   // USB interface claimed
        public static final long EXCLUSIVE_ACCESS    = 1L << 3;   // Exclusive lock
        
        // Configuration state (bits 8-15)
        public static final long ENCRYPTION_ENABLED  = 1L << 8;
        public static final long FILTER_ENABLED      = 1L << 9;
        public static final long RAW_MODE            = 1L << 10;
        public static final long PARSED_MODE         = 1L << 11;
        
        // Streaming state (bits 16-23)
        public static final long STREAMING           = 1L << 16;
        public static final long PAUSED              = 1L << 17;
        public static final long BACKPRESSURE_ACTIVE = 1L << 18;  // Client can't keep up
        public static final long EVENT_BUFFERING     = 1L << 19;  // Buffering events
        
        // Error state (bits 24-31)
        public static final long DEVICE_ERROR        = 1L << 24;
        public static final long TRANSFER_ERROR      = 1L << 25;
        public static final long DISCONNECTED        = 1L << 26;  // Device physically disconnected
        public static final long STALE               = 1L << 27;  // No activity for too long
    }
    
    // ===== CLIENT SESSION =====
    
    /**
     * Per-client session state
     */
    public static class ClientSession {
        public final String sessionId;
        public final int clientPid;
        public final BitFlagStateMachine state;
        
        // Claimed devices: sourceId → DeviceState
        public final ConcurrentHashMap<Integer, DeviceState> claimedDevices = 
            new ConcurrentHashMap<>();
        
        // Heartbeat tracking
        public volatile long lastPingSent = 0;
        public volatile long lastPongReceived = 0;
        public final AtomicInteger missedPongs = new AtomicInteger(0);
        
        // Message rate tracking (for backpressure)
        public final AtomicInteger messagesSent = new AtomicInteger(0);
        public final AtomicInteger messagesAcknowledged = new AtomicInteger(0);
        
        // Configuration
        public int maxUnacknowledgedMessages = 100;  // Backpressure threshold
        public long heartbeatIntervalMs = 5000;      // 5 seconds
        public long heartbeatTimeoutMs = 15000;      // 15 seconds (3 missed pings)
        
        public ClientSession(String sessionId, int clientPid) {
            this.sessionId = sessionId;
            this.clientPid = clientPid;
            this.state = new BitFlagStateMachine("client-" + sessionId);
            
            setupStateTransitions();
        }
        
        private void setupStateTransitions() {
            // When authenticated, enable heartbeat
            state.onStateAdded(ClientStateFlags.AUTHENTICATED, (old, now, bit) -> {
                state.setFlag(ClientStateFlags.HEARTBEAT_ENABLED);
            });
            
            // When backpressure activates, pause streaming
            state.onStateAdded(ClientStateFlags.BACKPRESSURE_ACTIVE, (old, now, bit) -> {
                state.setFlag(ClientStateFlags.FLOW_CONTROL_PAUSED);
                System.out.println("Backpressure activated for client " + sessionId);
            });
            
            // When heartbeat times out, mark error state
            state.onStateAdded(ClientStateFlags.HEARTBEAT_TIMEOUT, (old, now, bit) -> {
                state.setFlag(ClientStateFlags.ERROR_STATE);
                System.err.println("Heartbeat timeout for client " + sessionId);
            });
            
            // When disconnecting, release all devices
            state.onStateAdded(ClientStateFlags.DISCONNECTING, (old, now, bit) -> {
                for (DeviceState device : claimedDevices.values()) {
                    device.release();
                }
            });
        }
        
        /**
         * Check if we should apply backpressure
         */
        public boolean shouldApplyBackpressure() {
            int sent = messagesSent.get();
            int acked = messagesAcknowledged.get();
            int unacked = sent - acked;
            
            if (unacked >= maxUnacknowledgedMessages) {
                state.setFlag(ClientStateFlags.BACKPRESSURE_ACTIVE);
                return true;
            }
            
            return false;
        }
        
        /**
         * Message was sent to client
         */
        public void messageSent() {
            messagesSent.incrementAndGet();
            shouldApplyBackpressure();
        }
        
        /**
         * Client acknowledged messages (via RESUME or implicit ACK)
         */
        public void messagesAcknowledged(int count) {
            messagesAcknowledged.addAndGet(count);
            
            int sent = messagesSent.get();
            int acked = messagesAcknowledged.get();
            int unacked = sent - acked;
            
            if (unacked < maxUnacknowledgedMessages / 2) {
                state.clearFlag(ClientStateFlags.BACKPRESSURE_ACTIVE);
                state.clearFlag(ClientStateFlags.FLOW_CONTROL_PAUSED);
            }
        }
        
        /**
         * Check heartbeat health
         */
        public boolean checkHeartbeat() {
            if (!state.hasFlag(ClientStateFlags.HEARTBEAT_ENABLED)) {
                return true;
            }
            
            long now = System.currentTimeMillis();
            
            // Check if waiting for PONG
            if (state.hasFlag(ClientStateFlags.HEARTBEAT_WAITING)) {
                long timeSincePing = now - lastPingSent;
                
                if (timeSincePing > heartbeatTimeoutMs) {
                    missedPongs.incrementAndGet();
                    
                    if (missedPongs.get() >= 3) {
                        state.setFlag(ClientStateFlags.HEARTBEAT_TIMEOUT);
                        return false;
                    }
                }
            }
            
            return true;
        }
        
        /**
         * Send PING
         */
        public void sendPing() {
            lastPingSent = System.currentTimeMillis();
            state.setFlag(ClientStateFlags.HEARTBEAT_WAITING);
        }
        
        /**
         * Received PONG
         */
        public void receivedPong() {
            lastPongReceived = System.currentTimeMillis();
            state.clearFlag(ClientStateFlags.HEARTBEAT_WAITING);
            missedPongs.set(0);
        }
    }
    
    // ===== DEVICE STATE =====
    
    /**
     * Per-device state (for a claimed device)
     */
    public static class DeviceState {
        public final String deviceId;
        public final int sourceId;
        public final int ownerPid;
        public final BitFlagStateMachine state;
        
        // Backpressure tracking
        public final AtomicInteger pendingEvents = new AtomicInteger(0);
        public final BlockingQueue<byte[]> eventBuffer = new LinkedBlockingQueue<>(1000);
        
        // Statistics
        public volatile long eventsSent = 0;
        public volatile long eventsDropped = 0;
        public volatile long lastEventTime = 0;
        
        public DeviceState(String deviceId, int sourceId, int ownerPid) {
            this.deviceId = deviceId;
            this.sourceId = sourceId;
            this.ownerPid = ownerPid;
            this.state = new BitFlagStateMachine("device-" + sourceId);
            
            setupStateTransitions();
        }
        
        private void setupStateTransitions() {
            // When claimed, mark as streaming
            state.onStateAdded(DeviceStateFlags.CLAIMED, (old, now, bit) -> {
                state.setFlag(DeviceStateFlags.STREAMING);
            });
            
            // When backpressure activates, enable buffering
            state.onStateAdded(DeviceStateFlags.BACKPRESSURE_ACTIVE, (old, now, bit) -> {
                state.setFlag(DeviceStateFlags.EVENT_BUFFERING);
                System.out.println("Backpressure on device " + deviceId);
            });
            
            // When paused, stop streaming
            state.onStateAdded(DeviceStateFlags.PAUSED, (old, now, bit) -> {
                state.clearFlag(DeviceStateFlags.STREAMING);
            });
            
            // When device disconnects, mark error
            state.onStateAdded(DeviceStateFlags.DISCONNECTED, (old, now, bit) -> {
                state.setFlag(DeviceStateFlags.DEVICE_ERROR);
                state.clearFlag(DeviceStateFlags.STREAMING);
            });
        }
        
        /**
         * Queue event for sending
         */
        public boolean queueEvent(byte[] eventData) {
            if (!state.hasFlag(DeviceStateFlags.STREAMING)) {
                return false;
            }
            
            // Check backpressure
            if (pendingEvents.get() > 100) {
                state.setFlag(DeviceStateFlags.BACKPRESSURE_ACTIVE);
            }
            
            // Try to buffer
            if (state.hasFlag(DeviceStateFlags.EVENT_BUFFERING)) {
                if (!eventBuffer.offer(eventData)) {
                    eventsDropped++;
                    return false;
                }
            }
            
            pendingEvents.incrementAndGet();
            eventsSent++;
            lastEventTime = System.currentTimeMillis();
            return true;
        }
        
        /**
         * Event was delivered to client
         */
        public void eventDelivered() {
            int pending = pendingEvents.decrementAndGet();
            
            // Clear backpressure if queue is draining
            if (pending < 50) {
                state.clearFlag(DeviceStateFlags.BACKPRESSURE_ACTIVE);
                state.clearFlag(DeviceStateFlags.EVENT_BUFFERING);
            }
        }
        
        /**
         * Release device
         */
        public void release() {
            state.clearFlag(DeviceStateFlags.STREAMING);
            state.clearFlag(DeviceStateFlags.CLAIMED);
            eventBuffer.clear();
            pendingEvents.set(0);
        }
    }
    
    // ===== HEARTBEAT MANAGER =====
    
    /**
     * Manages heartbeat for all client sessions
     */
    public static class HeartbeatManager {
        private final ScheduledExecutorService scheduler = 
            Executors.newScheduledThreadPool(1);
        
        private final ConcurrentHashMap<String, ClientSession> sessions = 
            new ConcurrentHashMap<>();
        
        public void registerSession(ClientSession session) {
            sessions.put(session.sessionId, session);
        }
        
        public void unregisterSession(String sessionId) {
            sessions.remove(sessionId);
        }
        
        /**
         * Start heartbeat loop
         */
        public void start() {
            scheduler.scheduleAtFixedRate(this::heartbeatTick, 
                1000, 1000, TimeUnit.MILLISECONDS);
        }
        
        private void heartbeatTick() {
            long now = System.currentTimeMillis();
            
            for (ClientSession session : sessions.values()) {
                if (!session.state.hasFlag(ClientStateFlags.HEARTBEAT_ENABLED)) {
                    continue;
                }
                
                // Check if it's time to send PING
                long timeSinceLastPing = now - session.lastPingSent;
                if (timeSinceLastPing > session.heartbeatIntervalMs) {
                    if (!session.state.hasFlag(ClientStateFlags.HEARTBEAT_WAITING)) {
                        sendPing(session);
                    }
                }
                
                // Check heartbeat health
                if (!session.checkHeartbeat()) {
                    handleHeartbeatTimeout(session);
                }
            }
        }
        
        private void sendPing(ClientSession session) {
            session.sendPing();
            // Actual PING message sending done by protocol layer
        }
        
        private void handleHeartbeatTimeout(ClientSession session) {
            System.err.println("Heartbeat timeout for session: " + session.sessionId);
            session.state.setFlag(ClientStateFlags.DISCONNECTING);
            // Trigger disconnect
        }
        
        public void shutdown() {
            scheduler.shutdown();
        }
    }
    
    // ===== BACKPRESSURE MANAGER =====
    
    /**
     * Manages backpressure across all devices and clients
     */
    public static class BackpressureManager {
        private final ConcurrentHashMap<String, ClientSession> sessions = 
            new ConcurrentHashMap<>();
        
        /**
         * Check if we should send more messages to a client
         */
        public boolean canSendToClient(ClientSession session) {
            if (session.state.hasFlag(ClientStateFlags.BACKPRESSURE_ACTIVE)) {
                return false;
            }
            
            if (session.state.hasFlag(ClientStateFlags.FLOW_CONTROL_PAUSED)) {
                return false;
            }
            
            return true;
        }
        
        /**
         * Check if device can emit events
         */
        public boolean canEmitFromDevice(DeviceState device) {
            if (device.state.hasFlag(DeviceStateFlags.BACKPRESSURE_ACTIVE)) {
                // Can still buffer if buffering enabled
                return device.state.hasFlag(DeviceStateFlags.EVENT_BUFFERING);
            }
            
            if (device.state.hasFlag(DeviceStateFlags.PAUSED)) {
                return false;
            }
            
            return true;
        }
        
        /**
         * Client sent RESUME message
         */
        public void handleClientResume(ClientSession session, int ackedCount) {
            session.messagesAcknowledged(ackedCount);
            
            // Resume all devices for this client
            for (DeviceState device : session.claimedDevices.values()) {
                if (device.state.hasFlag(DeviceStateFlags.BACKPRESSURE_ACTIVE)) {
                    device.state.clearFlag(DeviceStateFlags.BACKPRESSURE_ACTIVE);
                }
            }
        }
    }
}