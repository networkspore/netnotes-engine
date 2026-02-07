package io.netnotes.engine.io.daemon;

import io.netnotes.engine.io.capabilities.DeviceCapabilitySet;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.noteBytes.*;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.state.StateEventRegistry.ClientStates;
import io.netnotes.engine.state.StateEventRegistry.DeviceStates;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Daemon Protocol State Management - Refactored with DeviceCapabilitySet integration
 * 
 * Key Design:
 * - ClientSession: Connection lifecycle state
 * - DeviceState: Device lifecycle state + capabilities (what it can do + user preferences)
 * - Capabilities include "modes" as user-selectable options
 * - BitFlagStateMachine tracks state, DeviceCapabilitySet tracks capabilities
 */
public class DaemonProtocolState {
    
    // ===== CLIENT STATE FLAGS =====
    
    /**
     * Client session state flags (per connection)
     * Uses StateEventRegistry.ClientStates for bit positions
     */
    public static class ClientStateFlags {
        // Re-export from StateEventRegistry for convenience
        public static final int CONNECTED           = ClientStates.CONNECTED;
        public static final int AUTHENTICATED       = ClientStates.AUTHENTICATED;
        public static final int DISCOVERING         = ClientStates.DISCOVERING;
        public static final int HAS_CLAIMED_DEVICES = ClientStates.HAS_CLAIMED_DEVICES;
        public static final int STREAMING           = ClientStates.STREAMING;
        public static final int PAUSED              = ClientStates.PAUSED;
        public static final int DISCONNECTING       = ClientStates.DISCONNECTING;
        public static final int ERROR_STATE         = ClientStates.ERROR_STATE;
        
        public static final int SUPPORTS_ENCRYPTION = ClientStates.SUPPORTS_ENCRYPTION;
        public static final int SUPPORTS_RAW_MODE   = ClientStates.SUPPORTS_RAW_MODE;
        public static final int SUPPORTS_FILTERING  = ClientStates.SUPPORTS_FILTERING;
        public static final int SUPPORTS_BATCH      = ClientStates.SUPPORTS_BATCH;
        
        public static final int HEARTBEAT_ENABLED   = ClientStates.HEARTBEAT_ENABLED;
        public static final int HEARTBEAT_WAITING   = ClientStates.HEARTBEAT_WAITING;
        public static final int HEARTBEAT_TIMEOUT   = ClientStates.HEARTBEAT_TIMEOUT;
        
        public static final int BACKPRESSURE_ACTIVE = ClientStates.BACKPRESSURE_ACTIVE;
        public static final int FLOW_CONTROL_PAUSED = ClientStates.FLOW_CONTROL_PAUSED;
        public static final int QUEUE_FULL          = ClientStates.QUEUE_FULL;
        
        public static boolean canDiscover(BitFlagStateMachine sm) {
            return sm.hasState(AUTHENTICATED) && !sm.hasState(DISCONNECTING);
        }
        
        public static boolean canClaim(BitFlagStateMachine sm) {
            return sm.hasState(AUTHENTICATED) && !sm.hasState(DISCONNECTING);
        }
        
        public static boolean canStream(BitFlagStateMachine sm) {
            return sm.hasState(HAS_CLAIMED_DEVICES) && 
                   !sm.hasState(PAUSED) &&
                   !sm.hasState(BACKPRESSURE_ACTIVE) &&
                   !sm.hasState(DISCONNECTING);
        }
        
        public static boolean isHeartbeatHealthy(BitFlagStateMachine sm) {
            return sm.hasState(HEARTBEAT_ENABLED) && 
                   !sm.hasState(HEARTBEAT_TIMEOUT);
        }
    }
    
    // ===== DEVICE STATE FLAGS =====
    
    public static class DeviceStateFlags {
        public static final int CLAIMED             = DeviceStates.CLAIMED;
        public static final int KERNEL_DETACHED     = DeviceStates.KERNEL_DETACHED;
        public static final int INTERFACE_CLAIMED   = DeviceStates.INTERFACE_CLAIMED;
        public static final int EXCLUSIVE_ACCESS    = DeviceStates.EXCLUSIVE_ACCESS;
        
        public static final int ENCRYPTION_ENABLED  = DeviceStates.ENCRYPTION_ENABLED;
        public static final int FILTER_ENABLED      = DeviceStates.FILTER_ENABLED;
        
        public static final int STREAMING           = DeviceStates.STREAMING;
        public static final int PAUSED              = DeviceStates.PAUSED;
        public static final int BACKPRESSURE_ACTIVE = DeviceStates.BACKPRESSURE_ACTIVE;
        public static final int EVENT_BUFFERING     = DeviceStates.EVENT_BUFFERING;
        
        public static final int DEVICE_ERROR        = DeviceStates.DEVICE_ERROR;
        public static final int TRANSFER_ERROR      = DeviceStates.TRANSFER_ERROR;
        public static final int DISCONNECTED        = DeviceStates.DISCONNECTED;
        public static final int STALE               = DeviceStates.STALE;
    }
    
    // ===== CLIENT SESSION =====
    /* 
    public static class ClientSession {
        public final String sessionId;
        public final int clientPid;
        public final BitFlagStateMachine state;
        
        public final ConcurrentHashMap<String, DeviceState> claimedDevices = 
            new ConcurrentHashMap<>();
        
        public volatile long lastPingSent = 0;
        public volatile long lastPongReceived = 0;
        public final AtomicInteger missedPongs = new AtomicInteger(0);
        
        public final AtomicInteger messagesSent = new AtomicInteger(0);
        public final AtomicInteger messagesAcknowledged = new AtomicInteger(0);
        
        public int maxUnacknowledgedMessages = 100;
        public long heartbeatIntervalMs = 5000;
        public long heartbeatTimeoutMs = 15000;
        
        public ClientSession(String sessionId, int clientPid) {
            this.sessionId = sessionId;
            this.clientPid = clientPid;
            this.state = new BitFlagStateMachine("client-" + sessionId);
            
            setupStateTransitions();
        }
        
        private void setupStateTransitions() {
            state.onStateAdded(ClientStateFlags.AUTHENTICATED, (old, now, bit) -> {
                state.addState(ClientStateFlags.HEARTBEAT_ENABLED);
            });
            
            state.onStateAdded(ClientStateFlags.BACKPRESSURE_ACTIVE, (old, now, bit) -> {
                state.addState(ClientStateFlags.FLOW_CONTROL_PAUSED);
                Log.logMsg("Backpressure activated for client " + sessionId);
            });
            
            state.onStateAdded(ClientStateFlags.HEARTBEAT_TIMEOUT, (old, now, bit) -> {
                state.addState(ClientStateFlags.ERROR_STATE);
                Log.logError("Heartbeat timeout for client " + sessionId);
            });
            
            state.onStateAdded(ClientStateFlags.DISCONNECTING, (old, now, bit) -> {
                for (DeviceState device : claimedDevices.values()) {
                    device.release();
                }
            });
        }
        
        public boolean shouldApplyBackpressure() {
            int sent = messagesSent.get();
            int acked = messagesAcknowledged.get();
            int unacked = sent - acked;
            
            if (unacked >= maxUnacknowledgedMessages) {
                state.addState(ClientStateFlags.BACKPRESSURE_ACTIVE);
                return true;
            }
            
            return false;
        }
        
        public void messageSent() {
            messagesSent.incrementAndGet();
            shouldApplyBackpressure();
        }
        
        public void messagesAcknowledged(int count) {
            messagesAcknowledged.addAndGet(count);
            
            int sent = messagesSent.get();
            int acked = messagesAcknowledged.get();
            int unacked = sent - acked;
            
            if (unacked < maxUnacknowledgedMessages / 2) {
                state.removeState(ClientStateFlags.BACKPRESSURE_ACTIVE);
                state.removeState(ClientStateFlags.FLOW_CONTROL_PAUSED);
            }
        }
        
        public boolean checkHeartbeat() {
            if (!state.hasState(ClientStateFlags.HEARTBEAT_ENABLED)) {
                return true;
            }
            
            long now = System.currentTimeMillis();
            
            if (state.hasState(ClientStateFlags.HEARTBEAT_WAITING)) {
                long timeSincePing = now - lastPingSent;
                
                if (timeSincePing > heartbeatTimeoutMs) {
                    missedPongs.incrementAndGet();
                    
                    if (missedPongs.get() >= 3) {
                        state.addState(ClientStateFlags.HEARTBEAT_TIMEOUT);
                        return false;
                    }
                }
            }
            
            return true;
        }
        
        public void sendPing() {
            lastPingSent = System.currentTimeMillis();
            state.addState(ClientStateFlags.HEARTBEAT_WAITING);
        }
        
        public void receivedPong() {
            lastPongReceived = System.currentTimeMillis();
            state.removeState(ClientStateFlags.HEARTBEAT_WAITING);
            missedPongs.set(0);
        }
    }
    */
    // ===== DEVICE STATE (REFACTORED) =====
    
    public static class DeviceState {
        public final String deviceId;
        public final int ownerPid;
        
        public final BitFlagStateMachine state;
        private final DeviceCapabilitySet capabilities;
        private final String deviceType;
        private final Map<String, Object> hardwareInfo;
        
        public final AtomicInteger pendingEvents = new AtomicInteger(0);
        public final BlockingQueue<byte[]> eventBuffer = new LinkedBlockingQueue<>(1000);
        
        public volatile long eventsSent = 0;
        public volatile long eventsDropped = 0;
        public volatile long lastEventTime = 0;
        
        public DeviceState(
                String deviceId, 
                int ownerPid,
                String deviceType,
                DeviceCapabilitySet capabilities) {
            
            this.deviceId = deviceId;
            this.ownerPid = ownerPid;
            this.deviceType = deviceType;
            this.capabilities = capabilities;
            this.hardwareInfo = new HashMap<>();
            this.state = new BitFlagStateMachine(deviceId);
            
            setupStateTransitions();
        }
        
        private void setupStateTransitions() {
            state.onStateAdded(DeviceStateFlags.CLAIMED, (old, now, bit) -> {
                state.addState(DeviceStateFlags.STREAMING);
            });
            
            state.onStateAdded(DeviceStateFlags.BACKPRESSURE_ACTIVE, (old, now, bit) -> {
                state.addState(DeviceStateFlags.EVENT_BUFFERING);
                Log.logMsg("Backpressure on device " + deviceId);
            });
            
            state.onStateAdded(DeviceStateFlags.PAUSED, (old, now, bit) -> {
                state.removeState(DeviceStateFlags.STREAMING);
            });
            
            state.onStateAdded(DeviceStateFlags.DISCONNECTED, (old, now, bit) -> {
                state.addState(DeviceStateFlags.DEVICE_ERROR);
                state.removeState(DeviceStateFlags.STREAMING);
                
                for (String cap : capabilities.getEnabledCapabilities()) {
                    capabilities.disableCapability(cap);
                }
            });
            
            state.onStateAdded(DeviceStateFlags.ENCRYPTION_ENABLED, (old, now, bit) -> {
                if (!capabilities.hasCapability("encryption_supported")) {
                    Log.logError("Device does not support encryption");
                    state.removeState(DeviceStateFlags.ENCRYPTION_ENABLED);
                } else {
                    capabilities.enableCapability("encryption_enabled");
                }
            });
            
            state.onStateAdded(DeviceStateFlags.FILTER_ENABLED, (old, now, bit) -> {
                if (!capabilities.hasCapability("filtered_mode")) {
                    Log.logError("Device does not support filtering");
                    state.removeState(DeviceStateFlags.FILTER_ENABLED);
                } else {
                    capabilities.enableCapability("filtered_mode");
                }
            });
        }
        
        public DeviceCapabilitySet getCapabilities() {
            return capabilities;
        }
        
        public boolean enableMode(String mode) {
            if (!capabilities.hasCapability(mode)) {
                Log.logError("Mode not available: " + mode);
                return false;
            }
            
            boolean enabled = capabilities.enableCapability(mode);
            
            if (enabled) {
                Log.logMsg("Enabled mode '" + mode + "' for device " + deviceId);
            } else {
                Log.logError("Failed to enable mode '" + mode + "': " + 
                                 capabilities.getEnableFailureReason(mode));
            }
            
            return enabled;
        }
        
        public String getCurrentMode() {
            return capabilities.getEnabledMode();
        }
        
        public boolean hasCapability(String capability) {
            return capabilities.hasCapability(capability);
        }
        
        public boolean isCapabilityEnabled(String capability) {
            return capabilities.isEnabled(capability);
        }
        
        public String getDeviceType() {
            return deviceType;
        }
        
        public void setHardwareInfo(String key, Object value) {
            hardwareInfo.put(key, value);
        }
        
        public Object getHardwareInfo(String key) {
            return hardwareInfo.get(key);
        }
        
        public Map<String, Object> getAllHardwareInfo() {
            return Collections.unmodifiableMap(hardwareInfo);
        }
        
        public NoteBytesObject toNoteBytes() {
            NoteBytesObject obj = new NoteBytesObject();
            obj.add(Keys.DEVICE_ID, deviceId);
            obj.add("owner_pid", ownerPid);
            obj.add(Keys.ITEM_TYPE, deviceType);
            obj.add("current_mode", getCurrentMode());
            obj.add("capabilities", capabilities.toNoteBytes());
            obj.add(Keys.STATE, new NoteBigInteger(state.getState()));
            obj.add("events_sent", eventsSent);
            obj.add("events_dropped", eventsDropped);
            obj.add("pending_events", pendingEvents.get());
            return obj;
        }
        
        public boolean queueEvent(byte[] eventData) {
            if (!state.hasState(DeviceStateFlags.STREAMING)) {
                return false;
            }
            
            if (pendingEvents.get() > 100) {
                state.addState(DeviceStateFlags.BACKPRESSURE_ACTIVE);
            }
            
            if (state.hasState(DeviceStateFlags.EVENT_BUFFERING)) {
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
        
        public void eventDelivered() {
            int pending = pendingEvents.decrementAndGet();
            
            if (pending < 50) {
                state.removeState(DeviceStateFlags.BACKPRESSURE_ACTIVE);
                state.removeState(DeviceStateFlags.EVENT_BUFFERING);
            }
        }
        
        public void release() {
            state.removeState(DeviceStateFlags.STREAMING);
            state.removeState(DeviceStateFlags.CLAIMED);
            eventBuffer.clear();
            pendingEvents.set(0);
            
            for (String cap : capabilities.getEnabledCapabilities()) {
                capabilities.disableCapability(cap);
            }
        }
        
        @Override
        public String toString() {
            return String.format(
                "DeviceState{id=%s, type=%s, mode=%s, streaming=%s, events=%d}",
                deviceId, deviceType, getCurrentMode(),
                state.hasState(DeviceStateFlags.STREAMING), eventsSent
            );
        }
    }
    
    // ===== HEARTBEAT MANAGER =====
    
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
        
        public void start() {
            scheduler.scheduleAtFixedRate(this::heartbeatTick, 
                1000, 1000, TimeUnit.MILLISECONDS);
        }
        
        private void heartbeatTick() {
            long now = System.currentTimeMillis();
            
            for (ClientSession session : sessions.values()) {
                if (!session.state.hasState(ClientStateFlags.HEARTBEAT_ENABLED)) {
                    continue;
                }
                
                long timeSinceLastPing = now - session.lastPingSent;
                if (timeSinceLastPing > session.heartbeatIntervalMs) {
                    if (!session.state.hasState(ClientStateFlags.HEARTBEAT_WAITING)) {
                        sendPing(session);
                    }
                }
                
                if (!session.checkHeartbeat()) {
                    handleHeartbeatTimeout(session);
                }
            }
        }
        
        private void sendPing(ClientSession session) {
            session.sendPing();
        }
        
        private void handleHeartbeatTimeout(ClientSession session) {
            Log.logError("Heartbeat timeout for session: " + session.sessionId);
            session.state.addState(ClientStateFlags.DISCONNECTING);
        }
        
        public void shutdown() {
            scheduler.shutdown();
        }
    }
}