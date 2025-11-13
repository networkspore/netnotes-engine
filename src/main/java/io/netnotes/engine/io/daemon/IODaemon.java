package io.netnotes.engine.io.daemon;

import io.netnotes.engine.io.*;
import io.netnotes.engine.io.daemon.DaemonProtocolState.BackpressureManager;
import io.netnotes.engine.io.daemon.DaemonProtocolState.ClientSession;
import io.netnotes.engine.io.daemon.DaemonProtocolState.ClientStateFlags;
import io.netnotes.engine.io.daemon.DaemonProtocolState.DeviceState;
import io.netnotes.engine.io.daemon.DaemonProtocolState.DeviceStateFlags;
import io.netnotes.engine.io.daemon.DaemonProtocolState.HeartbeatManager;
import io.netnotes.engine.io.daemon.IODaemonProtocol.AsyncNoteBytesWriter;
import io.netnotes.engine.io.events.EventBytes;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.state.BitFlagStateMachine;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Complete IODaemon Client with State Management
 * 
 * Integrates:
 * - Phased protocol (handshake, discovery, claim, configure, streaming)
 * - State machines for connection and devices
 * - Heartbeat monitoring
 * - Backpressure control
 * - Multi-device support
 */
public class IODaemon implements AutoCloseable {
    
    private final String socketPath;
    private final String sessionId;
    private final InputSourceRegistry registry;
    
    // Connection
    private SocketChannel socketChannel;
    private AsyncNoteBytesWriter asyncWriter;
    private NoteBytesReader reader;
    
    // State management
    private final ClientSession clientSession;
    private final Map<Integer, DeviceState> deviceStates = new ConcurrentHashMap<>();
    
    // Managers
    private final BackpressureManager backpressureManager = new BackpressureManager();
    private final HeartbeatManager heartbeatManager;
    
    // Executors
    private final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    
    // Discovery cache
    private final Map<String, IODaemonProtocol.USBDeviceDescriptor> discoveredDevices = 
        new ConcurrentHashMap<>();
    
    // Running state
    private volatile boolean connected = false;
    private volatile boolean running = false;
    private final Map<NoteBytesReadOnly, MessageExecutor> m_execMsgMap = new ConcurrentHashMap<>();
    @FunctionalInterface
    private interface MessageExecutor {
        void execute(NoteBytesMap map);
    }

    
    public IODaemon(String socketPath, String sessionId) {
        this.socketPath = socketPath;
        this.sessionId = sessionId;
        this.registry = InputSourceRegistry.getInstance();
        
        int pid = (int) ProcessHandle.current().pid();
        this.clientSession = new ClientSession(sessionId, pid);
        
        this.heartbeatManager = new HeartbeatManager();
        this.heartbeatManager.registerSession(clientSession);
        
        setupClientStateTransitions();
        setupMsgMapping();
    }
    
    private void setupClientStateTransitions() {
        // When authenticated, can start discovering
        clientSession.state.onStateAdded(ClientStateFlags.AUTHENTICATED, (old, now, bit) -> {
            System.out.println("Client authenticated, enabling heartbeat");
            clientSession.state.setFlag(ClientStateFlags.HEARTBEAT_ENABLED);
        });
        
        // When backpressure activates, log warning
        clientSession.state.onStateAdded(ClientStateFlags.BACKPRESSURE_ACTIVE, (old, now, bit) -> {
            System.out.println("WARNING: Backpressure active - too many unacknowledged messages");
        });
        
        // When heartbeat timeout, disconnect
        clientSession.state.onStateAdded(ClientStateFlags.HEARTBEAT_TIMEOUT, (old, now, bit) -> {
            System.err.println("FATAL: Heartbeat timeout - server not responding");
            clientSession.state.setFlag(ClientStateFlags.DISCONNECTING);
            close();
        });
    }

   
    
    // ===== CONNECTION LIFECYCLE =====
    
    /**
     * Connect and complete handshake
     */
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            try {
                establishConnection();
                performHandshake();
                startHeartbeat();
                startReadLoop();
                
                clientSession.state.setFlag(ClientStateFlags.CONNECTED);
                clientSession.state.setFlag(ClientStateFlags.AUTHENTICATED);
                connected = true;
                
                System.out.println("IODaemon client connected: " + sessionId);
                
            } catch (IOException e) {
                System.err.println("Connection failed: " + e.getMessage());
                throw new CompletionException(e);
            }
        }, ioExecutor);
    }
    
    private void establishConnection() throws IOException {
        Path path = Path.of(socketPath);
        socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX);
        UnixDomainSocketAddress addr = UnixDomainSocketAddress.of(path);
        socketChannel.connect(addr);
        
        if (!socketChannel.isConnected()) {
            throw new IOException("Failed to connect to daemon");
        }
        
        InputStream inputStream = Channels.newInputStream(socketChannel);
        OutputStream outputStream = Channels.newOutputStream(socketChannel);
        
        asyncWriter = new IODaemonProtocol.AsyncNoteBytesWriter(outputStream);
        reader = new NoteBytesReader(inputStream);
    }
    
    private void performHandshake() throws IOException {
        // Send HELLO
        NoteBytesObject hello = IODaemonProtocol.MessageBuilder.createCommand(
            IODaemonProtocol.Commands.HELLO,
            new NoteBytesPair("client_version", 1),
            new NoteBytesPair("session_id", sessionId),
            new NoteBytesPair("pid", clientSession.clientPid)
        );
        
        asyncWriter.writeSync(hello);
        
        // Wait for ACCEPT
        NoteBytesReadOnly response = reader.nextNoteBytesReadOnly();
        if (response.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            throw new IOException("Invalid handshake response");
        }
        
        NoteBytesMap responseMap = response.getAsNoteBytesObject().getAsNoteBytesMap();
        byte type = responseMap.get(Keys.TYPE_KEY).getAsByte();
        
        if (type != EventBytes.TYPE_ACCEPT.getAsByte()) {
            throw new IOException("Handshake failed");
        }
        
        System.out.println("Handshake complete");
    }
    
    private void startHeartbeat() {
        heartbeatManager.start();
        
        // Also start local heartbeat responder
        heartbeatExecutor.scheduleAtFixedRate(this::checkHeartbeat, 
            1000, 1000, TimeUnit.MILLISECONDS);
    }
    
    private void checkHeartbeat() {
        if (!clientSession.checkHeartbeat()) {
            System.err.println("Heartbeat check failed");
        }
    }
    
    private void startReadLoop() {
        running = true;
        
        ioExecutor.execute(() -> {
            try {
                while (running && connected) {
                    NoteBytesReadOnly first = reader.nextNoteBytesReadOnly();
                    if (first == null) {
                        System.out.println("Connection closed by server");
                        break;
                    }
                    
                    // Check if this is a routed packet [INTEGER:sourceId][OBJECT:event]
                    if (first.getType() == NoteBytesMetaData.INTEGER_TYPE) {
                        int sourceId = first.getAsInt();
                        NoteBytesReadOnly payload = reader.nextNoteBytesReadOnly();
                        
                        if (payload != null) {
                            handleDeviceEvent(sourceId, payload);
                            
                            // Implicit acknowledgment
                            clientSession.messagesAcknowledged(1);
                        }
                    } 
                    // Protocol message [OBJECT]
                    else if (first.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                        handleProtocolMessage(first);
                    }
                }
            } catch (Exception e) {
                System.err.println("Read loop error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                handleDisconnect();
            }
        });
    }
    
    // ===== DISCOVERY PHASE =====
    
    /**
     * Request full device discovery
     */
    public CompletableFuture<List<IODaemonProtocol.USBDeviceDescriptor>> discoverDevices() {
        if (!ClientStateFlags.canDiscover(clientSession.state)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot discover in current state"));
        }
        
        clientSession.state.setFlag(ClientStateFlags.DISCOVERING);
        
        NoteBytesObject request = IODaemonProtocol.MessageBuilder.createCommand(
            IODaemonProtocol.Commands.REQUEST_DISCOVERY
        );
        
        return asyncWriter.writeAsync(request)
            .thenApply(v -> {
                // Response will come via read loop
                return waitForDeviceList();
            });
    }
    
    private List<IODaemonProtocol.USBDeviceDescriptor> waitForDeviceList() {
        // Wait for DEVICE_LIST response (handled in protocol message handler)
        // This is simplified - real implementation would use CompletableFuture
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new ArrayList<>(discoveredDevices.values());
    }
    
    // ===== CLAIM PHASE =====
    
    /**
     * Claim a device
     */
    public CompletableFuture<ContextPath> claimDevice(
            String deviceId, 
            IODaemonProtocol.DeviceMode mode) {
        
        if (!ClientStateFlags.canClaim(clientSession.state)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot claim in current state"));
        }
        
        IODaemonProtocol.USBDeviceDescriptor device = discoveredDevices.get(deviceId);
        if (device == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Device not found: " + deviceId));
        }
        
        // Register in InputSourceRegistry (gets sourceId)
        ContextPath basePath = ContextPath.of("daemon", sessionId, device.get_device_type());
        InputSourceCapabilities capabilities = createCapabilities(device);
        ContextPath fullPath = registry.registerSource(basePath, device.product, capabilities);
        
        int sourceId = registry.getSourceIdFromPath(fullPath);
        
        // Create device state
        DeviceState deviceState = new DeviceState(deviceId, sourceId, clientSession.clientPid);
        deviceStates.put(sourceId, deviceState);
        clientSession.claimedDevices.put(sourceId, deviceState);
        
        // Send CLAIM command to daemon
        NoteBytesObject claimCmd = IODaemonProtocol.MessageBuilder.createCommand(
            IODaemonProtocol.Commands.CLAIM_DEVICE,
            new NoteBytesPair("device_id", deviceId),
            new NoteBytesPair("source_id", new NoteBytesReadOnly(sourceId)),
            new NoteBytesPair("pid", clientSession.clientPid),
            new NoteBytesPair("mode", mode.name().toLowerCase())
        );
        
        return asyncWriter.writeAsync(claimCmd)
            .thenApply(v -> {
                deviceState.state.setFlag(DeviceStateFlags.CLAIMED);
                clientSession.state.setFlag(ClientStateFlags.HAS_CLAIMED_DEVICES);
                
                System.out.println("Claimed device: " + deviceId + " as sourceId " + sourceId);
                return fullPath;
            });
    }
    
    private InputSourceCapabilities createCapabilities(IODaemonProtocol.USBDeviceDescriptor device) {
        String deviceType = device.get_device_type();
        return switch (deviceType) {
            case "keyboard" -> new InputSourceCapabilities.Builder("USB Keyboard")
                .enableKeyboard()
                .withScanCodes()
                .build();
            case "mouse" -> new InputSourceCapabilities.Builder("USB Mouse")
                .enableMouse()
                .providesAbsoluteCoordinates()
                .build();
            default -> new InputSourceCapabilities.Builder("USB HID Device")
                .build();
        };
    }
    
    // ===== STREAMING PHASE =====
    
    /**
     * Start streaming from a claimed device
     */
    public CompletableFuture<Void> startStreaming(int sourceId) {
        DeviceState device = deviceStates.get(sourceId);
        if (device == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Device not claimed: " + sourceId));
        }
        
        if (!device.state.hasFlag(DeviceStateFlags.CLAIMED)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Device not claimed"));
        }
        
        device.state.setFlag(DeviceStateFlags.STREAMING);
        clientSession.state.setFlag(ClientStateFlags.STREAMING);
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Pause streaming
     */
    public CompletableFuture<Void> pauseDevice(int sourceId) {
        DeviceState device = deviceStates.get(sourceId);
        if (device != null) {
            device.state.setFlag(DeviceStateFlags.PAUSED);
            
            NoteBytesObject pauseCmd = IODaemonProtocol.MessageBuilder.createCommand(
                IODaemonProtocol.Commands.PAUSE_DEVICE,
                new NoteBytesPair("source_id", new NoteBytesReadOnly(sourceId))
            );
            
            return asyncWriter.writeAsync(pauseCmd);
        }
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Resume streaming
     */
    public CompletableFuture<Void> resumeDevice(int sourceId) {
        DeviceState device = deviceStates.get(sourceId);
        if (device != null) {
            device.state.clearFlag(DeviceStateFlags.PAUSED);
            
            // Send RESUME with acknowledgment count
            int processed = clientSession.messagesSent.get() - clientSession.messagesAcknowledged.get();
            
            NoteBytesObject resumeCmd = IODaemonProtocol.MessageBuilder.createCommand(
                IODaemonProtocol.Commands.RESUME_DEVICE,
                new NoteBytesPair("source_id", new NoteBytesReadOnly(sourceId)),
                new NoteBytesPair("processed_count", processed)
            );
            
            return asyncWriter.writeAsync(resumeCmd)
                .thenRun(() -> clientSession.messagesAcknowledged(processed));
        }
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== MESSAGE HANDLING =====
    
    private void handleDeviceEvent(int sourceId, NoteBytesReadOnly payload) {
        DeviceState device = deviceStates.get(sourceId);
        if (device == null) {
            System.err.println("Event for unknown device: " + sourceId);
            return;
        }
        
        // Check backpressure
        if (!backpressureManager.canEmitFromDevice(device)) {
            device.eventsDropped++;
            return;
        }
        
        // Emit through InputSourceRegistry
        registry.emit(sourceId, payload);
        
        // Track delivery
        device.eventDelivered();
        
        // Check if we need to send RESUME
        if (backpressureManager.shouldSendResume(clientSession)) {
            resumeAll();
        }
    }
  
    private void setupMsgMapping(){
        m_execMsgMap.put( EventBytes.TYPE_PING, (map)->handlePing(map));
        m_execMsgMap.put( EventBytes.TYPE_ACCEPT, (map)->handleAccept(map));
        m_execMsgMap.put( EventBytes.TYPE_ERROR, (map)->handleError(map));
        m_execMsgMap.put( EventBytes.TYPE_CMD, (map)->handleCommand(map));
        m_execMsgMap.put( EventBytes.TYPE_DISCONNECTED, (map)->handleDeviceDisconnected(map));
    }
 
    
    private void handleProtocolMessage(NoteBytesReadOnly messageBytes) {
        NoteBytesMap message = messageBytes.getAsNoteBytesObject().getAsNoteBytesMap();
        NoteBytes type = message.get(Keys.TYPE_KEY);
        
        MessageExecutor msgExecutor = m_execMsgMap.get(type);

        if(msgExecutor != null){
            msgExecutor.execute(message);
        }else{
            System.err.println("Cannot execute protocol message type: " + type);
        }
    }
    
    private void handlePing(NoteBytesMap message) {
        // Send PONG immediately
        NoteBytesObject pong = IODaemonProtocol.MessageBuilder.createCommand(
            "pong"
        );
        
        asyncWriter.writeAsync(pong)
            .thenRun(() -> clientSession.receivedPong());
    }
    
    private void handleAccept(NoteBytesMap message) {
        String status = message.get(Keys.STATUS_KEY).getAsString();
        System.out.println("Server: " + status);
    }
    
    private void handleError(NoteBytesMap message) {
        int errorCode = message.get(Keys.ERROR_KEY).getAsInt();
        String errorMsg = message.get(Keys.MSG_KEY).getAsString();
        System.err.println("Server error " + errorCode + ": " + errorMsg);
    }
    
    private void handleCommand(NoteBytesMap message) {
        String cmd = message.get(Keys.CMD_KEY).getAsString();
        
        switch (cmd) {
            case IODaemonProtocol.Commands.DEVICE_LIST -> handleDeviceList(message);
            case IODaemonProtocol.Commands.DEVICE_CLAIMED -> handleDeviceClaimed(message);
            case IODaemonProtocol.Commands.DEVICE_RELEASED -> handleDeviceReleased(message);
        }
    }
    
    private void handleDeviceList(NoteBytesMap message) {
        NoteBytes devicesBytes = message.get("devices");
        if (devicesBytes != null && devicesBytes.getType() == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
            NoteBytesReadOnly[] devicesArray = devicesBytes.getAsNoteBytesArrayReadOnly().getAsArray();
            
            discoveredDevices.clear();
            
            for (NoteBytesReadOnly deviceBytes : devicesArray) {
                if (deviceBytes.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                    IODaemonProtocol.USBDeviceDescriptor device = 
                        IODaemonProtocol.USBDeviceDescriptor.fromNoteBytesObject(
                            deviceBytes.getAsNoteBytesObject());
                    
                    discoveredDevices.put(device.deviceId, device);
                }
            }
            
            System.out.println("Discovered " + discoveredDevices.size() + " devices");
            clientSession.state.clearFlag(ClientStateFlags.DISCOVERING);
        }
    }
    
    private void handleDeviceClaimed(NoteBytesMap message) {
        // Device claim confirmed by server
        System.out.println("Device claim confirmed");
    }
    
    private void handleDeviceReleased(NoteBytesMap message) {
        // Device released by server
        System.out.println("Device released");
    }
    
    private void handleDeviceDisconnected(NoteBytesMap message) {
        String deviceName = message.get("device").getAsString();
        System.out.println("Device disconnected: " + deviceName);
        
        // Find and mark device as disconnected
        for (DeviceState device : deviceStates.values()) {
            if (device.deviceId.equals(deviceName)) {
                device.state.setFlag(DeviceStateFlags.DISCONNECTED);
                registry.setSourceState(device.sourceId, DeviceStateFlags.DISCONNECTED);
            }
        }
    }
    
    // ===== BACKPRESSURE MANAGEMENT =====
    
    private void resumeAll() {
        int totalProcessed = clientSession.messagesSent.get() - 
                           clientSession.messagesAcknowledged.get();
        
        if (totalProcessed > 0) {
            NoteBytesObject resumeCmd = IODaemonProtocol.MessageBuilder.createCommand(
                "resume",
                new NoteBytesPair("processed_count", totalProcessed)
            );
            
            asyncWriter.writeAsync(resumeCmd)
                .thenRun(() -> clientSession.messagesAcknowledged(totalProcessed));
        }
    }
    
    // ===== CLEANUP =====
    
    private void handleDisconnect() {
        connected = false;
        running = false;
        
        clientSession.state.setFlag(ClientStateFlags.DISCONNECTING);
        
        // Release all devices
        for (DeviceState device : deviceStates.values()) {
            device.release();
            registry.unregisterSource(device.sourceId);
        }
        
        System.out.println("Disconnected from daemon");
    }
    
    @Override
    public void close() {
        if (connected) {
            clientSession.state.setFlag(ClientStateFlags.DISCONNECTING);
            
            // Send disconnect
            NoteBytesObject disconnect = new NoteBytesObject();
            disconnect.add(Keys.TYPE_KEY, EventBytes.TYPE_SHUTDOWN);
            disconnect.add(Keys.SEQUENCE_KEY, 
                         IODaemonProtocol.MessageBuilder.generateSequence());
            
            try {
                asyncWriter.writeSync(disconnect);
            } catch (IOException e) {
                // Ignore
            }
            
            handleDisconnect();
            
            try {
                if (socketChannel != null) {
                    socketChannel.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
            
            asyncWriter.shutdown();
            heartbeatManager.unregisterSession(sessionId);
            heartbeatExecutor.shutdown();
            ioExecutor.shutdown();
        }
    }
    
    // ===== QUERIES =====
    
    public ClientSession getClientSession() {
        return clientSession;
    }
    
    public Map<Integer, DeviceState> getDeviceStates() {
        return Collections.unmodifiableMap(deviceStates);
    }
    
    public List<IODaemonProtocol.USBDeviceDescriptor> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices.values());
    }
    
    public boolean isConnected() {
        return connected && clientSession.state.hasFlag(ClientStateFlags.CONNECTED);
    }
    
    public boolean canDiscover() {
        return ClientStateFlags.canDiscover(clientSession.state);
    }
    
    public boolean canClaim() {
        return ClientStateFlags.canClaim(clientSession.state);
    }
    
    public boolean canStream() {
        return ClientStateFlags.canStream(clientSession.state);
    }
}