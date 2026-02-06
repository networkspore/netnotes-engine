package io.netnotes.engine.io.daemon;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.io.daemon.DaemonProtocolState.ClientStateFlags;
import io.netnotes.engine.io.daemon.IODaemonProtocol.DeviceMode;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;



/**
 * ClientSession - One per authenticated client connection
 * 
 * UPDATED: Proper FlowProcess lifecycle management
 * - Uses onStart() for initialization
 * - Uses onStop() for cleanup
 * - Shutdown via kill() or complete()
 * - Automatic device release on stop
 * 
 * Hierarchy: IODaemon → ClientSession → ClaimedDevice
 */
public class ClientSession extends FlowProcess {
    
    public static class Modes {
        public static final NoteBytesReadOnly RAW           = new NoteBytesReadOnly("raw");
        public static final NoteBytesReadOnly PARSED        = new NoteBytesReadOnly("parsed");
        public static final NoteBytesReadOnly PASSTHROUGH   = new NoteBytesReadOnly("passthrough");
        public static final NoteBytesReadOnly FILTERED      = new NoteBytesReadOnly("filtered");
    }

    public final String sessionId;
    public final int clientPid;
    public final BitFlagStateMachine state;
    
    private final DiscoveredDeviceRegistry discoveredDevices = new DiscoveredDeviceRegistry();
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_routedMsgMap = new ConcurrentHashMap<>();

    public volatile long lastPingSent = 0;
    public volatile long lastPongReceived = 0;
    public final AtomicInteger missedPongs = new AtomicInteger(0);
    
    public final AtomicInteger messagesSent = new AtomicInteger(0);
    public final AtomicInteger messagesAcknowledged = new AtomicInteger(0);
    
    public int maxUnacknowledgedMessages = 100;
    public long heartbeatIntervalMs = 5000;
    public long heartbeatTimeoutMs = 15000;
    
    public ClientSession(String sessionId, int clientPid) {
        super(sessionId, ProcessType.BIDIRECTIONAL);
        this.sessionId = sessionId;
        this.clientPid = clientPid;
        this.state = new BitFlagStateMachine("client-" + sessionId);
        
        setupStateTransitions();
        setupRoutedMessageMapping();
    }
    
    // ===== LIFECYCLE HOOKS =====
    
    @Override
    public void onStart() {
        Log.logMsg("[ClientSession:" + sessionId + "] Starting session for PID " + clientPid);
        
        state.addState(ClientStateFlags.CONNECTED);
        state.addState(ClientStateFlags.AUTHENTICATED);
    }
    
    @Override
    public void onStop() {
        Log.logMsg("[ClientSession:" + sessionId + "] Stopping session");
        
        // Mark as disconnecting
        state.addState(ClientStateFlags.DISCONNECTING);
        
        // Release all claimed devices
        releaseAllDevices();
        
        // Clear discovery state
        discoveredDevices.clear();
        state.removeState(ClientStateFlags.HAS_CLAIMED_DEVICES);
        state.removeState(ClientStateFlags.DISCOVERING);
        
        // Disable features
        state.removeState(ClientStateFlags.HEARTBEAT_ENABLED);
        state.removeState(ClientStateFlags.AUTHENTICATED);
        state.removeState(ClientStateFlags.CONNECTED);
        
        Log.logMsg("[ClientSession:" + sessionId + "] Session stopped");
    }
    
    /**
     * Graceful shutdown - completes cleanly
     * Releases devices, notifies IODaemon, then completes
     */
    public CompletableFuture<Void> shutdown() {
        if (!isAlive()) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[ClientSession:" + sessionId + "] Shutdown requested");
        
        state.addState(ClientStateFlags.DISCONNECTING);
        
        // Release all devices (sends notifications to IODaemon)
        List<ClaimedDevice> devices = findChildrenByType(ClaimedDevice.class);
        
        if (devices.isEmpty()) {
            // No devices, complete immediately
            complete();
            return getCompletionFuture();
        }
        
        // Release each device and wait for all to complete
        CompletableFuture<Void> releaseAll = CompletableFuture.allOf(
            devices.stream()
                .map(device -> releaseDevice(device.getDeviceId())
                    .exceptionally(ex -> {
                        Log.logError("[ClientSession:" + sessionId + "] Error releasing " + 
                            device.getDeviceId() + ": " + ex.getMessage());
                        return null;
                    })
                )
                .toArray(CompletableFuture[]::new)
        );
        
        return releaseAll.thenRun(() -> {
            Log.logMsg("[ClientSession:" + sessionId + "] All devices released, completing");
            complete();
        });
    }
    
    /**
     * Emergency shutdown - kills immediately
     * Use when IODaemon socket is already disconnected
     */
    public void emergencyShutdown() {
        Log.logMsg("[ClientSession:" + sessionId + "] Emergency shutdown");
        
        state.addState(ClientStateFlags.DISCONNECTING);
        state.addState(ClientStateFlags.ERROR_STATE);
        
        // Force release without waiting for IODaemon responses
        List<ClaimedDevice> devices = findChildrenByType(ClaimedDevice.class);
        for (ClaimedDevice device : devices) {
            try {
                device.release();
                registry.unregisterProcess(device.getContextPath());
            } catch (Exception e) {
                Log.logError("[ClientSession:" + sessionId + "] Error in emergency release: " + 
                    e.getMessage());
            }
        }
        
        discoveredDevices.clear();
        
        // Kill the process
        kill();
    }
    
    // ===== STATE TRANSITIONS =====
    
    private void setupStateTransitions() {
        state.onStateAdded(ClientStateFlags.AUTHENTICATED, (old, now, bit) -> {
            state.addState(ClientStateFlags.HEARTBEAT_ENABLED);
            Log.logMsg("[ClientSession:" + sessionId + "] Authenticated");
        });
        
        state.onStateAdded(ClientStateFlags.BACKPRESSURE_ACTIVE, (old, now, bit) -> {
            state.addState(ClientStateFlags.FLOW_CONTROL_PAUSED);
            Log.logMsg("[ClientSession:" + sessionId + "] Backpressure activated");
        });
        
        state.onStateAdded(ClientStateFlags.HEARTBEAT_TIMEOUT, (old, now, bit) -> {
            state.addState(ClientStateFlags.ERROR_STATE);
            Log.logError("[ClientSession:" + sessionId + "] Heartbeat timeout - disconnecting");
            
            // Trigger emergency shutdown on heartbeat timeout
            emergencyShutdown();
        });
        
        state.onStateAdded(ClientStateFlags.DISCONNECTING, (old, now, bit) -> {
            Log.logMsg("[ClientSession:" + sessionId + "] Disconnecting...");
            
            // Disable heartbeat
            state.removeState(ClientStateFlags.HEARTBEAT_ENABLED);
            state.removeState(ClientStateFlags.HEARTBEAT_WAITING);
        });
        
        state.onStateAdded(ClientStateFlags.ERROR_STATE, (old, now, bit) -> {
            Log.logError("[ClientSession:" + sessionId + "] Entered ERROR state");
        });
    }

    private void setupRoutedMessageMapping() {
        m_routedMsgMap.put(ProtocolMesssages.REQUEST_DISCOVERY, this::handleDiscoverCommand);
        m_routedMsgMap.put(ProtocolMesssages.CLAIM_ITEM, this::handleClaimCommand);
        m_routedMsgMap.put(ProtocolMesssages.RELEASE_ITEM, this::handleReleaseCommand);
        m_routedMsgMap.put(ProtocolMesssages.ITEM_LIST, this::handleListDevicesCommand);
    }
    
    // ===== MESSAGE HANDLING =====
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        try {
            NoteBytesReadOnly payload = packet.getPayload();
            NoteBytesMap message = payload.getAsNoteBytesMap();
            
            NoteBytesReadOnly type = message.getReadOnly(Keys.EVENT);
            
            // Handle daemon disconnect notification
            if (type != null && type.equals(EventBytes.TYPE_DISCONNECTED)) {
                handleDaemonDisconnect(message);
                return CompletableFuture.completedFuture(null);
            }
            
            NoteBytesReadOnly cmd = message.getReadOnly(Keys.CMD);
            
            RoutedMessageExecutor msgExecutor = m_routedMsgMap.get(cmd);
            if (msgExecutor != null) {
                return msgExecutor.execute(message, packet);
            } else {
                return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Unsupported command: " + cmd));
            }

        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Handle daemon socket disconnect
     * IODaemon calls this when the socket connection is lost
     */
    private void handleDaemonDisconnect(NoteBytesMap notification) {
        Log.logError("[ClientSession:" + sessionId + "] Daemon disconnected: " + 
            notification.get(Keys.MSG));
        
        // Emergency shutdown - can't communicate with daemon anymore
        emergencyShutdown();
    }

    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException("ClientSession does not handle streams");
    }
    
    // ===== DISCOVERY =====
    
    private CompletableFuture<Void> handleDiscoverCommand(NoteBytesMap message, RoutedPacket request) {
        return discoverDevices()
            .thenAccept(devices -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                response.put(Keys.ITEM_COUNT, new NoteBytes(devices.size()));
                
                NoteBytesArray deviceArray = new NoteBytesArray();
                for (var deviceInfo : devices) {
                    NoteBytesObject deviceObj = serializeDeviceDescriptor(deviceInfo);
                    deviceArray.add(deviceObj);
                }
                response.put(Keys.ITEMS, deviceArray);
                
                reply(request, response.toNoteBytes());
            })
            .exceptionally(ex -> {
                reply(request, ProtocolObjects.getErrorObject(ex.getMessage()));
                return null;
            });
    }
    
    public CompletableFuture<List<DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities>> discoverDevices() {
        if (!ClientStateFlags.canDiscover(state)) {
          
            return CompletableFuture.failedFuture(new IllegalStateException("Cannot discover in current state: " + 
                    state.getState() ));
        }
        
        state.addState(ClientStateFlags.DISCOVERING);
        
        // Send discovery request to IODaemon (socket manager)
        return request(parentPath, Duration.ofSeconds(5),
            new NoteBytesPair(Keys.CMD, ProtocolMesssages.REQUEST_DISCOVERY),
            new NoteBytesPair(Keys.SESSION_ID, sessionId)
        ).thenApply(response -> {
            // IODaemon will send device list via handleDeviceList callback
            return discoveredDevices.getAllDevices();
        })
        .exceptionally(ex -> {
            state.removeState(ClientStateFlags.DISCOVERING);
            Log.logError("[ClientSession:" + sessionId + "] Discovery failed: " + 
                ex.getMessage());
            throw new RuntimeException("Discovery failed", ex);
        });
    }
    
    public void handleDeviceList(NoteBytesMap map) {
        discoveredDevices.parseDeviceList(map);
        state.removeState(ClientStateFlags.DISCOVERING);
        
        int deviceCount = discoveredDevices.getAllDevices().size();
        Log.logMsg("[ClientSession:" + sessionId + "] Discovery complete: " + 
            deviceCount + " devices found");
    }
    
    private CompletableFuture<Void> handleListDevicesCommand(NoteBytesMap message, RoutedPacket request) {
        List<DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities> devices = 
            discoveredDevices.getAllDevices();
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        response.put(Keys.ITEM_COUNT, new NoteBytes(devices.size())); 
        
        NoteBytesArray deviceArray = new NoteBytesArray();
        for (var deviceInfo : devices) {
            NoteBytesObject deviceObj = serializeDeviceDescriptor(deviceInfo);
            deviceArray.add(deviceObj);
        }
        response.put(Keys.ITEMS, deviceArray);
        
        reply(request, response.toNoteBytes());
        return CompletableFuture.completedFuture(null);
    }
    
    private NoteBytesObject serializeDeviceDescriptor(
            DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities deviceInfo) {
        NoteBytesObject obj = new NoteBytesObject();
        // Implementation would serialize device info
        // ... (implementation details)
        return obj;
    }
    
    // ===== CLAIM =====
    
    private CompletableFuture<Void> handleClaimCommand(NoteBytesMap command, RoutedPacket request) {
        NoteBytes deviceIdBytes = command.get(Keys.DEVICE_ID);
        NoteBytes modeBytes = command.getOrDefault(Keys.MODE, DeviceMode.getDefault());
        
        if (deviceIdBytes == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Device id required"));
        }

        String deviceId = deviceIdBytes.getAsString();
        String mode = modeBytes.getAsString();

        return claimDevice(deviceId, mode)
            .thenAccept(devicePath -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                response.put(Keys.ITEM_PATH, devicePath.toNoteBytes());
                reply(request, response.toNoteBytes());
            })
            .exceptionally(ex -> {
                reply(request, ProtocolObjects.getErrorObject(ex.getMessage()));
                return null;
            });
    }
    
    
    public CompletableFuture<ContextPath> claimDevice(String deviceId, String requestedMode) {
        // Validation
        if (!ClientStateFlags.canClaim(state)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot claim in current state: " + 
                    state.getState()));
        }

        var deviceInfo = discoveredDevices.getDevice(deviceId);
        if (deviceInfo == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Device not found: " + deviceId));
        }

        if (deviceInfo.claimed()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Device already claimed: " + deviceId));
        }

        if (!discoveredDevices.validateModeCompatibility(deviceId, requestedMode)) {
            String availableModes = String.join(", ",
                discoveredDevices.getAvailableModes(deviceId));
            return CompletableFuture.failedFuture(
                new IllegalArgumentException(
                    "Device does not support mode: " + requestedMode +
                    ". Available: " + availableModes));
        }

        // Setup ClaimedDevice
        ContextPath claimedDevicePath = contextPath.append(deviceId);

        ClaimedDevice claimedDevice = new ClaimedDevice(
            deviceId,
            claimedDevicePath,
            deviceInfo.usbDevice().getDeviceType(),
            deviceInfo.capabilities(),
            parentPath
        );

        if (!claimedDevice.enableMode(requestedMode)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Failed to enable mode: " + requestedMode));
        }

        registerChild(claimedDevice);
        registry.startProcess(claimedDevicePath);

        // Send claim request to IODaemon
        return request(parentPath, Duration.ofSeconds(5),
            new NoteBytesPair(Keys.CMD, ProtocolMesssages.CLAIM_ITEM),
            new NoteBytesPair(Keys.SESSION_ID, sessionId),
            new NoteBytesPair(Keys.DEVICE_ID, deviceId),
            new NoteBytesPair(Keys.MODE, requestedMode)
        ).thenApply(response -> {
            discoveredDevices.markClaimed(deviceId);
            state.addState(ClientStateFlags.HAS_CLAIMED_DEVICES);
            
            Log.logMsg("[ClientSession:" + sessionId + "] Claimed device: " + deviceId);
            return claimedDevicePath;
        })
        .exceptionally(ex -> {
            // Cleanup on failure
            Log.logError("[ClientSession:" + sessionId + "] Claim failed: " + ex.getMessage());
            registry.unregisterProcess(claimedDevicePath);
            throw new RuntimeException("Failed to claim device", ex);
        });
    }

    // ===== RELEASE =====
    
    private CompletableFuture<Void> handleReleaseCommand(NoteBytesMap command, RoutedPacket request) {
        String deviceId = command.get(Keys.DEVICE_ID).getAsString();
        
        return releaseDevice(deviceId)
            .thenAccept(v -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                reply(request, response.toNoteBytes());
            })
            .exceptionally(ex -> {
                reply(request, ProtocolObjects.getErrorObject(ex.getMessage()));
                return null;
            });
    }
    
    public CompletableFuture<Void> releaseDevice(String deviceId) {
        if (!discoveredDevices.isClaimed(deviceId)) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Device not claimed: " + deviceId));
        }
        
        ClaimedDevice claimedDevice = getClaimedDevice(deviceId);
        
        if (claimedDevice != null) {
            claimedDevice.release();
        }
        
        // Send release to IODaemon socket manager
        return request(parentPath, Duration.ofSeconds(5),
            new NoteBytesPair(Keys.CMD, ProtocolMesssages.RELEASE_ITEM),
            new NoteBytesPair(Keys.SESSION_ID, sessionId),
            new NoteBytesPair(Keys.DEVICE_ID, deviceId)
        ).thenRun(() -> {
            discoveredDevices.markReleased(deviceId);
            
            if (claimedDevice != null) {
                registry.unregisterProcess(claimedDevice.getContextPath());
            }
            
            // Check if we still have devices
            if (findChildrenByType(ClaimedDevice.class).isEmpty()) {
                state.removeState(ClientStateFlags.HAS_CLAIMED_DEVICES);
            }
            
            Log.logMsg("[ClientSession:" + sessionId + "] Released device: " + deviceId);
        })
        .exceptionally(ex -> {
            Log.logError("[ClientSession:" + sessionId + "] Release failed: " + 
                ex.getMessage());
            
            // Try to cleanup locally even if IODaemon fails
            if (claimedDevice != null) {
                try {
                    registry.unregisterProcess(claimedDevice.getContextPath());
                } catch (Exception e) {
                    Log.logError("[ClientSession:" + sessionId + "] Cleanup error: " + 
                        e.getMessage());
                }
            }
            
            return null;
        });
    }
    
    /**
     * Release all devices (called during shutdown)
     */
    public void releaseAllDevices() {
        List<ClaimedDevice> devices = findChildrenByType(ClaimedDevice.class);
        
        if (devices.isEmpty()) {
            return;
        }
        
        Log.logMsg("[ClientSession:" + sessionId + "] Releasing " + devices.size() + " devices");
        
        for (ClaimedDevice device : devices) {
            try {
                String deviceId = device.getDeviceId();
                
                // Notify device to release
                device.release();
                
                // Unregister from registry
                registry.unregisterProcess(device.getContextPath());
                
                // Update discovery state
                discoveredDevices.markReleased(deviceId);
                
                Log.logMsg("[ClientSession:" + sessionId + "] Released: " + deviceId);
                
            } catch (Exception e) {
                Log.logError("[ClientSession:" + sessionId + "] Error releasing device " + 
                    device.getDeviceId() + ": " + e.getMessage());
            }
        }
        
        state.removeState(ClientStateFlags.HAS_CLAIMED_DEVICES);
    }
    
    // ===== BACKPRESSURE =====
    
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
    
    // ===== HEARTBEAT =====
    
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
    
    // ===== GETTERS =====
    
    public ClaimedDevice getClaimedDevice(String deviceId) {
        return (ClaimedDevice) getChildProcess(deviceId);
    }
    
    public DiscoveredDeviceRegistry getDiscoveredDevices() {
        return discoveredDevices;
    }
    
    /**
     * Get count of claimed devices in this session
     */
    public int getClaimedDeviceCount() {
        return findChildrenByType(ClaimedDevice.class).size();
    }

    /**
     * Get all claimed devices
     */
    public List<ClaimedDevice> getClaimedDevices() {
        return findChildrenByType(ClaimedDevice.class);
    }

    /**
     * Check if device is claimed by this session
     */
    public boolean hasDevice(String deviceId) {
        return getClaimedDevice(deviceId) != null;
    }
    
    /**
     * Check if session is in a healthy state
     */
    public boolean isHealthy() {
        return isAlive() && 
               state.hasState(ClientStateFlags.CONNECTED) &&
               state.hasState(ClientStateFlags.AUTHENTICATED) &&
               !state.hasState(ClientStateFlags.ERROR_STATE) &&
               !state.hasState(ClientStateFlags.HEARTBEAT_TIMEOUT);
    }
    
    @Override
    public String toString() {
        return String.format(
            "ClientSession{id=%s, pid=%d, devices=%d, state=%d, alive=%s}",
            sessionId, clientPid, getClaimedDeviceCount(), 
            state.getState().longValue(), isAlive()
        );
    }
}
