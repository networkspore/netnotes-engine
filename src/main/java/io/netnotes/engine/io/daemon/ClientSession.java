// ===== NEW FILE: ClientSession.java =====

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
 * Responsibilities:
 * - Session lifecycle (auth, heartbeat, backpressure)
 * - Device discovery and claiming
 * - Managing ClaimedDevice children
 * - Forwarding commands to IODaemon socket manager
 * 
 * Hierarchy: IODaemon → ClientSession → ClaimedDevice
 */
public class ClientSession extends FlowProcess {
    
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
            releaseAllDevices();
        });
    }

    private void setupRoutedMessageMapping() {
        m_routedMsgMap.put(ProtocolMesssages.REQUEST_DISCOVERY, this::handleDiscoverCommand);
        m_routedMsgMap.put(ProtocolMesssages.CLAIM_ITEM, this::handleClaimCommand);
        m_routedMsgMap.put(ProtocolMesssages.RELEASE_ITEM, this::handleReleaseCommand);
        m_routedMsgMap.put(ProtocolMesssages.ITEM_LIST, this::handleListDevicesCommand);
    }
    
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
            if(msgExecutor != null){
                return msgExecutor.execute(message, packet);
            } else {
                return CompletableFuture.failedFuture(
                    new UnsupportedOperationException("ClientSession does not support this command"));
            }

        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Handle daemon socket disconnect
     */
    private void handleDaemonDisconnect(NoteBytesMap notification) {
        Log.logError("Daemon disconnected: " + notification.get(Keys.MSG));
        
        state.addState(ClientStateFlags.DISCONNECTING);
        state.addState(ClientStateFlags.ERROR_STATE);
        
        // Release all devices (they'll notify IODaemon, but it's already disconnected)
        releaseAllDevices();
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
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot discover in current state"));
        }
        
        state.addState(ClientStateFlags.DISCOVERING);
        
        // Send discovery request to IODaemon (socket manager)
        return request(parentPath, Duration.ofSeconds(5),
            new NoteBytesPair(Keys.CMD , ProtocolMesssages.REQUEST_DISCOVERY),
            new NoteBytesPair(Keys.SESSION_ID, sessionId)
        ).thenApply(response -> {
            // IODaemon will send device list via handleDeviceList callback
            return discoveredDevices.getAllDevices();
        });
    }
    
    public void handleDeviceList(NoteBytesMap map) {
        discoveredDevices.parseDeviceList(map);
        state.removeState(ClientStateFlags.DISCOVERING);
        Log.logMsg("Device discovery complete: " + 
            discoveredDevices.getAllDevices().size() + " devices found");
    }
    
    private CompletableFuture<Void> handleListDevicesCommand(NoteBytesMap message, RoutedPacket request) {
        List<DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities> devices = 
            discoveredDevices.getAllDevices();
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATE_FLAGS, ProtocolMesssages.SUCCESS);
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
        // Same implementation as IODaemon's version
        NoteBytesObject obj = new NoteBytesObject();
        // ... (copy from IODaemon)
        return obj;
    }
    
    // ===== CLAIM =====
    
    private CompletableFuture<Void> handleClaimCommand(NoteBytesMap command, RoutedPacket request) {
        NoteBytes deviceIdBytes = command.get(Keys.DEVICE_ID);
        NoteBytes modeBytes = command.getOrDefault(Keys.MODE, DeviceMode.getDefault());
        if(deviceIdBytes == null){
            return CompletableFuture.failedFuture(new IllegalArgumentException("Device id requried"));
        }

        String deviceId = deviceIdBytes.getAsString();
        String mode = modeBytes.getAsString();

        return claimDevice(deviceId, mode)
            .thenAccept(devicePath -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                response.put(Keys.ITEM_PATH, new NoteBytes(devicePath.toString()));
                reply(request, response.toNoteBytes());
            })
            .exceptionally(ex -> {
                reply(request, ProtocolObjects.getErrorObject(ex.getMessage()));
                return null;
            });
    }
    
    
    public CompletableFuture<ContextPath> claimDevice(String deviceId, String requestedMode) {
        // --- VALIDATION ---
        if (!ClientStateFlags.canClaim(state)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot claim in current state"));
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

        // --- SETUP ---
        ContextPath claimedDevicePath = contextPath.append(deviceId);

        ClaimedDevice claimedDevice = new ClaimedDevice(
            deviceId,
            claimedDevicePath,
            deviceInfo.usbDevice().get_device_type(),
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
            
            Log.logMsg("Claimed device: " + deviceId);
            return claimedDevicePath;
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
            Log.logMsg("Released device: " + deviceId);
        });
    }
    
        
    private void releaseAllDevices() {
        List<ClaimedDevice> devices = findChildrenByType(ClaimedDevice.class);
        
        Log.logMsg("Releasing " + devices.size() + " devices...");
        
        for (ClaimedDevice device : devices) {
            try {
                device.release();
                registry.unregisterProcess(device.getContextPath());
            } catch (Exception e) {
                Log.logError("Error releasing device " + 
                    device.getDeviceId() + ": " + e.getMessage());
            }
        }
        
        discoveredDevices.clear();
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
    
    // ===== HELPERS =====
    
    public ClaimedDevice getClaimedDevice(String deviceId) {
        return (ClaimedDevice) getChildProcess(deviceId);
    }
    
    public DiscoveredDeviceRegistry getDiscoveredDevices() {
        return discoveredDevices;
    }
}
