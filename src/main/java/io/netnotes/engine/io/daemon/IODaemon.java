package io.netnotes.engine.io.daemon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.DaemonProtocolState.ClientSession;
import io.netnotes.engine.io.daemon.DaemonProtocolState.ClientStateFlags;
import io.netnotes.engine.io.daemon.DaemonProtocolState.DeviceState;
import io.netnotes.engine.io.daemon.IODaemonProtocol.AsyncNoteBytesWriter;
import io.netnotes.engine.io.events.EventBytes;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.utils.VirtualExecutors;

/**
 * IODaemon - Process wrapper for daemon socket communication
 * 
 * Process Type: BIDIRECTIONAL
 * - Receives: Commands from other processes (claim, release, discover)
 * - Emits: USB device events routed by sourceId
 * 
 * Connects to the io-daemon socket and manages USB devices.
 * Each claimed device gets a child process for routing.
 */
public class IODaemon extends FlowProcess {
    
    private final String socketPath;
    private final String sessionId;
    
    private SocketChannel socketChannel;
    private AsyncNoteBytesWriter asyncWriter;
    private NoteBytesReader reader;
    
    private final ClientSession clientSession;
    private final Map<Integer, DeviceState> deviceStates = new ConcurrentHashMap<>();
    
    // Pre-claim device registry
    private final DiscoveredDeviceRegistry discoveredDevices = new DiscoveredDeviceRegistry();
    
    // Message dispatch map
    private final Map<NoteBytesReadOnly, MessageExecutor> m_execMsgMap = new ConcurrentHashMap<>();
    
    private volatile boolean connected = false;
    private volatile boolean running = false;
    
    @FunctionalInterface
    private interface MessageExecutor {
        void execute(NoteBytesMap message);
    }
    
    public IODaemon(String socketPath, String sessionId) {
        super(ProcessType.BIDIRECTIONAL);
        this.socketPath = socketPath;
        this.sessionId = sessionId;
        
        int pid = (int) ProcessHandle.current().pid();
        this.clientSession = new ClientSession(sessionId, pid);
        
        setupClientStateTransitions();
        setupMessageMapping();
    }
    
    // ===== PROCESS LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> run() {
        return connect()
            .thenCompose(v -> startReadLoop())
            .thenRun(() -> {
                System.out.println("IODaemon running at: " + contextPath);
            });
    }
    
    @Override
    protected void onStart() {
        System.out.println("IODaemon starting: " + sessionId);
    }
    
    @Override
    protected void onStop() {
        System.out.println("IODaemon stopping: " + sessionId);
        handleDisconnect();
    }
    
    @Override
    protected CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Handle commands from other processes
        try {
            NoteBytesReadOnly payload = packet.getPayload();
            NoteBytesMap command = payload.getAsNoteBytesMap();
            
            String action = command.get("action").getAsString();
            
            switch (action) {
                case "discover":
                    return handleDiscoverCommand(packet);
                case "claim":
                    return handleClaimCommand(command, packet);
                case "release":
                    return handleReleaseCommand(command, packet);
                case "list":
                    return handleListDevicesCommand(packet);
                default:
                    System.err.println("Unknown action: " + action);
                    return CompletableFuture.completedFuture(null);
            }
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    // ===== CONNECTION LIFECYCLE =====
    
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            try {
                establishConnection();
                performHandshake();
                
                clientSession.state.addState(ClientStateFlags.CONNECTED);
                clientSession.state.addState(ClientStateFlags.AUTHENTICATED);
                connected = true;
                
                System.out.println("IODaemon connected: " + sessionId);
                
            } catch (IOException e) {
                System.err.println("Connection failed: " + e.getMessage());
                throw new CompletionException(e);
            }
        }, VirtualExecutors.getVirtualExecutor());
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
        NoteBytesObject hello = IODaemonProtocol.MessageBuilder.createCommand(
            ProtocolMesssages.HELLO,
            new NoteBytesPair("client_version", 1),
            new NoteBytesPair("session_id", sessionId),
            new NoteBytesPair("pid", clientSession.clientPid)
        );
        
        asyncWriter.writeSync(hello);
        
        // Wait for ACCEPT response
        NoteBytesReadOnly response = reader.nextNoteBytesReadOnly();
        if (response.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            throw new IOException("Invalid handshake response");
        }
        
        NoteBytesMap responseMap = response.getAsNoteBytesMap();
        byte type = responseMap.get(Keys.TYPE).getAsByte();
        
        if (type != EventBytes.TYPE_ACCEPT.getAsByte()) {
            throw new IOException("Handshake rejected");
        }
        
        System.out.println("Handshake complete");
    }
    
    /**
     * Main read loop - reads from daemon socket and emits to subscribers
     */
    private CompletableFuture<Void> startReadLoop() {
        running = true;
        
        return CompletableFuture.runAsync(() -> {
            try {
                while (running && connected) {
                    // Read first value - determines message type
                    NoteBytesReadOnly first = reader.nextNoteBytesReadOnly();
                    if (first == null) {
                        System.out.println("Connection closed by server");
                        break;
                    }
                    
                    // Check if routed (has sourceId) or control message
                    if (first.getType() == NoteBytesMetaData.INTEGER_TYPE) {
                        // ROUTED MESSAGE: [INTEGER:sourceId][OBJECT/ENCRYPTED:payload]
                        handleRoutedMessage(first);
                        
                    } else if (first.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                        // CONTROL MESSAGE: [OBJECT]
                        handleControlMessage(first);
                        
                    } else {
                        System.err.println("Unexpected message type: " + first.getType());
                    }
                }
            } catch (Exception e) {
                System.err.println("Read loop error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                handleDisconnect();
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    /**
     * Handle routed message from daemon (USB device event)
     * Format: [INTEGER:sourceId][OBJECT/ENCRYPTED:payload]
     */
    private void handleRoutedMessage(NoteBytesReadOnly sourceIdBytes) throws IOException {
        int sourceId = sourceIdBytes.getAsInt();
        
        // Read payload
        NoteBytesReadOnly payload = reader.nextNoteBytesReadOnly();
        if (payload == null) {
            System.err.println("No payload for routed message");
            return;
        }
        
        // Get device info
        DeviceState device = deviceStates.get(sourceId);
        if (device == null) {
            System.err.println("Unknown sourceId: " + sourceId);
            return;
        }
        
        // Create routed packet with device path
        ContextPath devicePath = contextPath.append(device.getDeviceType())
                                            .append(String.valueOf(sourceId));
        RoutedPacket packet = RoutedPacket.create(devicePath, payload);
        
        // Emit through Process publisher (subscribers will receive this)
        emit(packet);
        
        // Track for backpressure
        clientSession.messagesAcknowledged(1);
    }
    
    /**
     * Handle control message from daemon (protocol messages)
     */
    private void handleControlMessage(NoteBytesReadOnly messageBytes) {
        NoteBytesMap map = messageBytes.getAsNoteBytesMap();
        
        // Get message type
        NoteBytes typeBytes = map.get(Keys.TYPE);
        if (typeBytes == null) {
            System.err.println("No type field in control message");
            return;
        }
        
        // Dispatch using map
        MessageExecutor executor = m_execMsgMap.get(typeBytes);
        if (executor != null) {
            executor.execute(map);
        } else {
            System.err.println("Unknown message type: " + typeBytes);
        }
    }
    
    private void setupClientStateTransitions() {
        clientSession.state.onStateAdded(ClientStateFlags.AUTHENTICATED, (old, now, bit) -> {
            System.out.println("Client authenticated");
        });
        
        clientSession.state.onStateAdded(ClientStateFlags.BACKPRESSURE_ACTIVE, (old, now, bit) -> {
            System.out.println("WARNING: Backpressure active");
        });
    }
    
    private void setupMessageMapping() {
        // Protocol messages
        m_execMsgMap.put(EventBytes.TYPE_PING, this::handlePing);
        m_execMsgMap.put(EventBytes.TYPE_PONG, this::handlePong);
        m_execMsgMap.put(EventBytes.TYPE_ACCEPT, this::handleAccept);
        m_execMsgMap.put(EventBytes.TYPE_ERROR, this::handleError);
        m_execMsgMap.put(EventBytes.TYPE_CMD, this::handleCommand);
        m_execMsgMap.put(EventBytes.TYPE_DISCONNECTED, this::handleDisconnected);
        
        // Command subtypes
        m_execMsgMap.put(ProtocolMesssages.ITEM_LIST, this::handleDeviceList);
        m_execMsgMap.put(ProtocolMesssages.ITEM_CLAIMED, this::handleDeviceClaimed);
        m_execMsgMap.put(ProtocolMesssages.ITEM_RELEASED, this::handleDeviceReleased);
    }
    
    // ===== COMMAND HANDLERS (FROM OTHER PROCESSES) =====
    
    private CompletableFuture<Void> handleDiscoverCommand(RoutedPacket request) {
        return discoverDevices()
            .thenAccept(devices -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put("status", new NoteBytes("success"));
                response.put("device_count", new NoteBytes(devices.size()));
                // TODO: Serialize device list
                
                reply(request, response.getNoteBytesObject());
            });
    }
    
    private CompletableFuture<Void> handleClaimCommand(NoteBytesMap command, RoutedPacket request) {
        String deviceId = command.get("device_id").getAsString();
        String mode = command.get("mode").getAsString();
        
        return claimDevice(deviceId, mode)
            .thenAccept(devicePath -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put("status", new NoteBytes("success"));
                response.put("device_path", new NoteBytes(devicePath.toString()));
                
                reply(request, response.getNoteBytesObject());
            })
            .exceptionally(ex -> {
                NoteBytesMap errorResponse = new NoteBytesMap();
                errorResponse.put("status", new NoteBytes("error"));
                errorResponse.put("message", new NoteBytes(ex.getMessage()));
                
                reply(request, errorResponse.getNoteBytesObject());
                return null;
            });
    }
    
    private CompletableFuture<Void> handleReleaseCommand(NoteBytesMap command, RoutedPacket request) {
        String deviceId = command.get("device_id").getAsString();
        
        return releaseDevice(deviceId)
            .thenAccept(v -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put("status", new NoteBytes("success"));
                
                reply(request, response.getNoteBytesObject());
            });
    }
    
    private CompletableFuture<Void> handleListDevicesCommand(RoutedPacket request) {
        List<DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities> devices = 
            discoveredDevices.getAllDevices();
        
        NoteBytesMap response = new NoteBytesMap();
        response.put("status", new NoteBytes("success"));
        response.put("device_count", new NoteBytes(devices.size()));
        // TODO: Serialize device list
        
        reply(request, response.getNoteBytesObject());
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== DISCOVERY PHASE =====
    
    public CompletableFuture<List<DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities>> discoverDevices() {
        if (!ClientStateFlags.canDiscover(clientSession.state)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot discover in current state"));
        }
        
        clientSession.state.addState(ClientStateFlags.DISCOVERING);
        
        NoteBytesObject request = IODaemonProtocol.MessageBuilder.createCommand(
            ProtocolMesssages.REQUEST_DISCOVERY
        );
        
        return asyncWriter.writeAsync(request)
            .thenApply(v -> {
                // Wait for device list response
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return discoveredDevices.getAllDevices();
            });
    }
    
    public List<DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities> getDiscoveredDevices() {
        return discoveredDevices.getAllDevices();
    }
    
    public DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities getDevice(String deviceId) {
        return discoveredDevices.getDevice(deviceId);
    }
    
    // ===== CLAIM PHASE =====
    
    public CompletableFuture<ContextPath> claimDevice(String deviceId, String requestedMode) {
        if (!ClientStateFlags.canClaim(clientSession.state)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot claim in current state"));
        }
        
        // Get device from discovered registry
        DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities deviceInfo = 
            discoveredDevices.getDevice(deviceId);
        
        if (deviceInfo == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Device not found: " + deviceId));
        }
        
        if (deviceInfo.claimed()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Device already claimed: " + deviceId));
        }
        
        // Validate mode locally BEFORE sending claim
        if (!discoveredDevices.validateModeCompatibility(deviceId, requestedMode)) {
            String availableModes = String.join(", ", 
                discoveredDevices.getAvailableModes(deviceId));
            return CompletableFuture.failedFuture(
                new IllegalArgumentException(
                    "Device does not support mode: " + requestedMode + 
                    ". Available: " + availableModes));
        }
        
        // Generate sourceId (simple: hash of deviceId)
        int sourceId = deviceId.hashCode();
        
        // Create device state
        DeviceState deviceState = new DeviceState(
            deviceId,
            sourceId,
            clientSession.clientPid,
            deviceInfo.usbDevice().get_device_type(),
            deviceInfo.capabilities()
        );
        
        // Enable requested mode
        if (!deviceState.enableMode(requestedMode)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Failed to enable mode: " + requestedMode));
        }
        
        // Send CLAIM command to daemon with sourceId
        NoteBytesObject claimCmd = IODaemonProtocol.MessageBuilder.createCommand(
            ProtocolMesssages.CLAIM_ITEM,
            new NoteBytesPair(Keys.ITEM_ID, deviceId),
            new NoteBytesPair("source_id", sourceId),
            new NoteBytesPair(Keys.PID, clientSession.clientPid),
            new NoteBytesPair("mode", requestedMode)
        );
        
        return asyncWriter.writeAsync(claimCmd)
            .thenApply(v -> {
                // Mark as claimed
                discoveredDevices.markClaimed(deviceId, sourceId);
                
                // Store device state
                deviceStates.put(sourceId, deviceState);
                clientSession.claimedDevices.put(sourceId, deviceState);
                clientSession.state.addState(ClientStateFlags.HAS_CLAIMED_DEVICES);
                
                // Create device path under this process
                ContextPath devicePath = contextPath
                    .append(deviceInfo.usbDevice().get_device_type())
                    .append(String.valueOf(sourceId));
                
                System.out.println("Claimed device: " + deviceId + 
                                 " at " + devicePath +
                                 " with mode: " + requestedMode);
                
                return devicePath;
            });
    }
    
    public CompletableFuture<Void> releaseDevice(String deviceId) {
        Integer sourceId = discoveredDevices.getSourceId(deviceId);
        if (sourceId == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Device not claimed: " + deviceId));
        }
        
        NoteBytesObject releaseCmd = IODaemonProtocol.MessageBuilder.createCommand(
            ProtocolMesssages.RELEASE_ITEM,
            new NoteBytesPair(Keys.ITEM_ID, deviceId),
            new NoteBytesPair("source_id", sourceId)
        );
        
        return asyncWriter.writeAsync(releaseCmd)
            .thenRun(() -> {
                // Remove from device states
                deviceStates.remove(sourceId);
                clientSession.claimedDevices.remove(sourceId);
                
                // Mark as released
                discoveredDevices.markReleased(deviceId);
                
                System.out.println("Released device: " + deviceId);
            });
    }
    
    // ===== DAEMON MESSAGE HANDLERS =====
    
    private void handleCommand(NoteBytesMap map) {
        NoteBytes cmdBytes = map.get(Keys.CMD);
        if (cmdBytes == null) {
            System.err.println("No cmd field in TYPE_CMD message");
            return;
        }
        
        // Dispatch command subtype
        MessageExecutor executor = m_execMsgMap.get(cmdBytes);
        if (executor != null) {
            executor.execute(map);
        } else {
            System.err.println("Unknown command: " + cmdBytes);
        }
    }
    
    private void handleDeviceList(NoteBytesMap map) {
        // Parse into discovered device registry
        discoveredDevices.parseDeviceList(map);
        
        clientSession.state.removeState(ClientStateFlags.DISCOVERING);
        
        System.out.println("Device discovery complete: " + 
            discoveredDevices.getAllDevices().size() + " devices found");
        
        discoveredDevices.printDevices();
    }
    
    private void handleDeviceClaimed(NoteBytesMap map) {
        System.out.println("Device claim confirmed by daemon");
    }
    
    private void handleDeviceReleased(NoteBytesMap map) {
        System.out.println("Device release confirmed by daemon");
    }
    
    private void handlePing(NoteBytesMap map) {
        NoteBytesObject pong = IODaemonProtocol.MessageBuilder.createCommand(
            ProtocolMesssages.PONG
        );
        asyncWriter.writeAsync(pong)
            .thenRun(() -> clientSession.receivedPong());
    }
    
    private void handlePong(NoteBytesMap map) {
        clientSession.receivedPong();
    }
    
    private void handleAccept(NoteBytesMap map) {
        NoteBytes statusBytes = map.get(Keys.STATUS);
        if (statusBytes != null) {
            System.out.println("Server: " + statusBytes.getAsString());
        }
    }
    
    private void handleError(NoteBytesMap map) {
        int errorCode = map.get(Keys.ERROR_CODE).getAsInt();
        String errorMsg = map.get(Keys.MSG).getAsString();
        System.err.println("Server error " + errorCode + ": " + errorMsg);
    }
    
    private void handleDisconnected(NoteBytesMap map) {
        System.out.println("Server disconnected");
        handleDisconnect();
    }
    
    // ===== CLEANUP =====
    
    private void handleDisconnect() {
        connected = false;
        running = false;
        
        clientSession.state.addState(ClientStateFlags.DISCONNECTING);
        
        // Cleanup all claimed devices
        for (DeviceState device : deviceStates.values()) {
            device.release();
        }
        
        deviceStates.clear();
        discoveredDevices.clear();
        
        System.out.println("Disconnected from daemon");
    }
    
    @Override
    public void kill() {
        if (connected) {
            clientSession.state.addState(ClientStateFlags.DISCONNECTING);
            
            NoteBytesObject shutdown = new NoteBytesObject();
            shutdown.add(Keys.TYPE, EventBytes.TYPE_SHUTDOWN);
            shutdown.add(Keys.SEQUENCE, IODaemonProtocol.MessageBuilder.generateSequence());
            
            try {
                asyncWriter.writeSync(shutdown);
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
        }
        
        super.kill();
    }
    
    // ===== QUERIES =====
    
    public ClientSession getClientSession() {
        return clientSession;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public boolean isConnected() {
        return connected && clientSession.state.hasState(ClientStateFlags.CONNECTED);
    }

}