package io.netnotes.engine.io.daemon;

import io.netnotes.engine.io.capabilities.DeviceCapabilitySet;
import io.netnotes.engine.io.capabilities.CapabilityRegistry.DefaultCapabilities;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.noteBytes.*;
import io.netnotes.engine.state.ConcurrentBitFlagStateMachine;
import io.netnotes.engine.state.BitFlagStateMachine.StateSnapshot;
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
        
      

        public static boolean canDiscover(StateSnapshot sm) {
            return sm.hasState(AUTHENTICATED) && !sm.hasState(DISCONNECTING);
        }
        
        public static boolean canClaim(StateSnapshot sm) {
            return sm.hasState(AUTHENTICATED) && !sm.hasState(DISCONNECTING);
        }
        
        public static boolean canStream(StateSnapshot sm) {
            return sm.hasState(HAS_CLAIMED_DEVICES) && 
                   !sm.hasState(PAUSED) &&
                   !sm.hasState(BACKPRESSURE_ACTIVE) &&
                   !sm.hasState(DISCONNECTING);
        }
        
        public static boolean isHeartbeatHealthy(StateSnapshot sm) {
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
    
    // ===== DEVICE STATE (REFACTORED) =====
    
    public static class DeviceState {
        public final NoteBytes deviceId;
        public final int ownerPid;
        
        public final ConcurrentBitFlagStateMachine state;
        private final DeviceCapabilitySet capabilities;
        private final NoteBytes deviceType;
        private final Map<String, Object> hardwareInfo;
        
        public final AtomicInteger pendingEvents = new AtomicInteger(0);
        public final BlockingQueue<byte[]> eventBuffer = new LinkedBlockingQueue<>(1000);
        
        public volatile long eventsSent = 0;
        public volatile long eventsDropped = 0;
        public volatile long lastEventTime = 0;
        
        public DeviceState(
                NoteBytes deviceId, 
                int ownerPid,
                NoteBytes deviceType,
                DeviceCapabilitySet capabilities) {
            
            this.deviceId = deviceId;
            this.ownerPid = ownerPid;
            this.deviceType = deviceType;
            this.capabilities = capabilities;
            this.hardwareInfo = new HashMap<>();
            this.state = new ConcurrentBitFlagStateMachine(deviceId.getAsString());
            
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
                
                for (NoteBytes cap : capabilities.getEnabledCapabilities()) {
                    capabilities.disableCapability(cap);
                }
            });
            
            state.onStateAdded(DeviceStateFlags.ENCRYPTION_ENABLED, (old, now, bit) -> {
                if (!capabilities.hasCapability(DefaultCapabilities.ENCRYPTION_SUPPORTED)) {
                    Log.logError("Device does not support encryption");
                    state.removeState(DeviceStateFlags.ENCRYPTION_ENABLED);
                } else {
                    capabilities.enableCapability(DefaultCapabilities.ENCRYPTION_ENABLED);
                }
            });
            
            state.onStateAdded(DeviceStateFlags.FILTER_ENABLED, (old, now, bit) -> {
                if (!capabilities.hasCapability( DefaultCapabilities.FILTERED_MODE)) {
                    Log.logError("Device does not support filtering");
                    state.removeState(DeviceStateFlags.FILTER_ENABLED);
                } else {
                    capabilities.enableCapability(DefaultCapabilities.FILTERED_MODE);
                }
            });
        }
        
        public DeviceCapabilitySet getCapabilities() {
            return capabilities;
        }
        
        public boolean enableMode(NoteBytes mode) {
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
        
        public NoteBytes getCurrentMode() {
            return capabilities.getEnabledMode();
        }
        
        public boolean hasCapability(NoteBytes capability) {
            return capabilities.hasCapability(capability);
        }
        
        public boolean isCapabilityEnabled(NoteBytes capability) {
            return capabilities.isEnabled(capability);
        }
        
        public NoteBytes getDeviceType() {
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

        public static class ProtocolKeys{
            public static final NoteBytesReadOnly OWNER_PID = new NoteBytesReadOnly("owner_pid");
            public static final NoteBytesReadOnly CURRENT_MODE = new NoteBytesReadOnly("current_mode");
            public static final NoteBytesReadOnly CAPABILITIES = new NoteBytesReadOnly("capabilities");
            public static final NoteBytesReadOnly EVENTS_SENT = new NoteBytesReadOnly("events_sent");
            public static final NoteBytesReadOnly EVENTS_DROPPED = new NoteBytesReadOnly("events_dropped");
            public static final NoteBytesReadOnly PENDING_EVENTS = new NoteBytesReadOnly("pending_events");
        }
        
        public NoteBytesObject toNoteBytes() {
            NoteBytesObject obj = new NoteBytesObject();
            obj.add(Keys.DEVICE_ID, deviceId);
            obj.add(ProtocolKeys.OWNER_PID, ownerPid);
            obj.add(Keys.ITEM_TYPE, deviceType);
            obj.add(ProtocolKeys.CURRENT_MODE, getCurrentMode());
            obj.add(ProtocolKeys.CAPABILITIES, capabilities.toNoteBytes());
            obj.add(Keys.STATE, new NoteBigInteger(state.getState()));
            obj.add(ProtocolKeys.EVENTS_SENT, eventsSent);
            obj.add(ProtocolKeys.EVENTS_DROPPED, eventsDropped);
            obj.add(ProtocolKeys.PENDING_EVENTS, pendingEvents.get());
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
            
            for (NoteBytes cap : capabilities.getEnabledCapabilities()) {
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
    /*
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
 */
    }