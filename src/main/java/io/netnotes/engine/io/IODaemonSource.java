package io.netnotes.engine.io;

import io.netnotes.engine.io.events.EventBytes;
import io.netnotes.engine.noteBytes.*;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.state.InputStateFlags;

import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * IODaemonSource - Manages daemon connection and virtual device tunnels.
 * 
 * Architecture:
 * - Parent: Manages socket connection to daemon
 * - Children: Virtual device processes registered in InputSourceRegistry
 * - Each device gets a unique sourceId from registry
 * - ContextPath structure: /daemon/{connection-name}/{device-type}/{sourceId}
 * 
 * Example paths:
 *   /daemon/main/keyboard/123
 *   /daemon/main/mouse/456
 *   /daemon/usb/keyboard/789
 * 
 * Packet Flow:
 *   Daemon Socket → routeToDevice() → registry.emit() → Subscribers
 * 
 * Command Flow:
 *   Subscriber → (via metadata) → sendToDaemon() → Daemon Socket
 */
public class IODaemonSource implements AutoCloseable {
    public static final String DEFAULT_SOCKET_PATH = "/run/netnotes/notedaemon.sock";
    
    private final String socketPath;
    private final String connectionName;
    private final InputSourceRegistry registry;
    private final ContextPath basePath;  // e.g., /daemon/main
    
    private SocketChannel socketChannel;
    private NoteBytesWriter writer;
    private NoteBytesReader reader;
    
    // State management
    private final BitFlagStateMachine connectionState;
    private volatile boolean connected = false;
    private volatile boolean running = false;
    
    // Device tracking: sourceId → device info
    private final Map<Integer, VirtualDevice> devices = new ConcurrentHashMap<>();
    
    // Reverse lookup: deviceName → sourceId
    private final Map<String, Integer> deviceNameToId = new ConcurrentHashMap<>();
    
    // Executor for socket I/O
    private final ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    public IODaemonSource(String socketPath, String connectionName) {
        this.socketPath = socketPath;
        this.connectionName = connectionName;
        this.registry = InputSourceRegistry.getInstance();
        this.basePath = ContextPath.of("daemon", connectionName);
        this.connectionState = new BitFlagStateMachine(
            "daemon-" + connectionName,
            InputStateFlags.CONTAINER_ENABLED
        );
        
        setupStateTransitions();
    }
    
    private void setupStateTransitions() {
        connectionState.onStateAdded(InputStateFlags.CONTAINER_ACTIVE, (old, now, bit) -> {
            System.out.println("Daemon connection active: " + connectionName);
        });
        
        connectionState.onStateAdded(InputStateFlags.PROCESS_KILLED, (old, now, bit) -> {
            System.out.println("Daemon connection killed: " + connectionName);
            close();
        });
    }
    
    // ===== CONNECTION LIFECYCLE =====
    
    /**
     * Connect to daemon and discover devices
     */
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            try {
                establishConnection();
                negotiateProtocol();
                startRoutingLoop();
                
                connectionState.setFlag(InputStateFlags.CONTAINER_ACTIVE);
                System.out.println("IODaemon connected at: " + basePath);
                
            } catch (IOException e) {
                System.err.println("Daemon connection failed: " + e.getMessage());
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
        
        writer = new NoteBytesWriter(outputStream);
        reader = new NoteBytesReader(inputStream);
        
        connected = true;
    }
    
    private void negotiateProtocol() throws IOException {
        // 1. Send HELLO
        sendCommand("hello");
        
        // 2. Request capabilities
        sendCommand("capabilities");
        
        // 3. Read capabilities response
        NoteBytesReadOnly response = reader.nextNoteBytesReadOnly();
        if (response.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            throw new IOException("Expected OBJECT response");
        }
        
        handleCapabilitiesResponse(response);
        
        // 4. Register each discovered device with daemon
        int ourPid = getPid();
        
        for (VirtualDevice device : devices.values()) {
            System.out.println("Registering device with daemon: " + device.name + 
                             " (sourceId=" + device.sourceId + ")");
            
            NoteBytesPair[] params = {
                new NoteBytesPair("device", device.name),
                new NoteBytesPair("source_id", new NoteBytesReadOnly(device.sourceId)),
                new NoteBytesPair("pid", ourPid)
            };
            sendCommand("register_device", params);
        }
        
        // 5. Request initial devices (example: keyboard)
        sendCommand("request_keyboard");
    }
    
    private void handleCapabilitiesResponse(NoteBytesReadOnly responseReadOnly) {
        NoteBytesMap response = responseReadOnly.getAsMap();
        
        String cmd = response.get("cmd").getAsString();
        if (!"capabilities".equals(cmd)) {
            throw new IllegalStateException("Expected capabilities response");
        }
        
        int version = response.get("version").getAsInt();
        System.out.println("Daemon version: " + version);
        
        // Get available devices
        NoteBytes devicesValue = response.get("devices");
        if (devicesValue != null && devicesValue.getType() == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
            NoteBytesReadOnly[] devicesArray = devicesValue.getAsNoteBytesArrayReadOnly().getAsArray();
            
            for (NoteBytesReadOnly deviceValue : devicesArray) {
                if (deviceValue.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                    NoteBytesMap deviceMap = deviceValue.getAsMap();
                    
                    String deviceName = deviceMap.get("name").getAsString();
                    String deviceType = deviceMap.get("type").getAsString();
                    
                    createVirtualDevice(deviceName, deviceType);
                }
            }
        }
    }
    
    /**
     * Create virtual device and register in InputSourceRegistry
     * 
     * Path structure: /daemon/{connectionName}/{deviceType}/{sourceId}
     * Example: /daemon/main/keyboard/123
     */
    private VirtualDevice createVirtualDevice(String deviceName, String deviceType) {
        // Create device capabilities
        InputSourceCapabilities capabilities = createCapabilities(deviceType);
        
        // Register in InputSourceRegistry (registry generates sourceId)
        ContextPath deviceBasePath = basePath.append(deviceType);
        ContextPath fullPath = registry.registerSource(
            deviceBasePath,
            deviceName,
            capabilities
        );
        
        // Extract sourceId from path leaf
        int sourceId = registry.getSourceIdFromPath(fullPath);
        
        // Create virtual device info
        VirtualDevice device = new VirtualDevice(
            sourceId,
            deviceName,
            deviceType,
            fullPath
        );
        
        // Store for routing
        devices.put(sourceId, device);
        deviceNameToId.put(deviceName, sourceId);
        
        System.out.println("Created virtual device: " + fullPath);
        
        return device;
    }
    
    private InputSourceCapabilities createCapabilities(String deviceType) {
        return switch (deviceType) {
            case "keyboard" -> new InputSourceCapabilities.Builder("DaemonKeyboard")
                .enableKeyboard()
                .withScanCodes()
                .build();
            case "mouse" -> new InputSourceCapabilities.Builder("DaemonMouse")
                .enableMouse()
                .providesAbsoluteCoordinates()
                .build();
            default -> new InputSourceCapabilities.Builder("UnknownDevice")
                .build();
        };
    }
    
    // ===== PACKET ROUTING LOOP =====
    
    private void startRoutingLoop() {
        running = true;
        
        ioExecutor.execute(() -> {
            try {
                while (running && connected) {
                    NoteBytesReadOnly first = reader.nextNoteBytesReadOnly();
                    if (first == null) {
                        System.out.println("Daemon connection closed");
                        break;
                    }
                    
                    if (first.getType() == NoteBytesMetaData.INTEGER_TYPE) {
                        // Format: [sourceId:INTEGER][payload:ANY]
                        int sourceId = first.getAsInt();
                        NoteBytesReadOnly payload = reader.nextNoteBytesReadOnly();
                        
                        if (payload == null) {
                            System.err.println("Missing payload after sourceId");
                            continue;
                        }
                        
                        // Route to device via registry
                        routeToDevice(sourceId, payload);
                        
                    } else if (first.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                        // Protocol message
                        handleProtocolMessage(first);
                    }
                }
            } catch (Exception e) {
                System.err.println("Routing loop error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                handleDisconnect();
            }
        });
    }
    
    /**
     * Route packet from daemon to device subscribers via registry
     */
    private void routeToDevice(int sourceId, NoteBytesReadOnly payload) {
        VirtualDevice device = devices.get(sourceId);
        if (device == null) {
            System.err.println("Unknown sourceId from daemon: " + sourceId);
            return;
        }
        
        // Emit through InputSourceRegistry
        // Registry will create RoutedPacket and publish to all subscribers
        registry.emit(sourceId, payload);
    }
    
    private void handleProtocolMessage(NoteBytesReadOnly messageReadOnly) {
        NoteBytesMap message = messageReadOnly.getAsMap();
        int type = message.get("typ").getAsByte();
        
        switch (type) {
            case 249: { // TYPE_DISCONNECTED
                String deviceName = message.get("device").getAsString();
                System.out.println("Device disconnected: " + deviceName);
                
                Integer sourceId = deviceNameToId.get(deviceName);
                if (sourceId != null) {
                    VirtualDevice device = devices.get(sourceId);
                    if (device != null) {
                        registry.setSourceState(device.fullPath, InputStateFlags.PROCESS_SUSPENDED);
                    }
                }
                break;
            }
            case 248: { // TYPE_ERROR
                int errorCode = message.get("err").getAsInt();
                String errorMsg = message.get("msg").getAsString();
                System.err.println("Daemon error " + errorCode + ": " + errorMsg);
                break;
            }
            case 252: { // TYPE_ACCEPT
                String status = message.get("sts").getAsString();
                System.out.println("Daemon: " + status);
                break;
            }
        }
    }
    
    // ===== SEND TO DAEMON =====
    
    /**
     * Send command to daemon (for control operations)
     */
    public CompletableFuture<Void> sendControlCommand(String command, NoteBytesPair... params) {
        return CompletableFuture.runAsync(() -> {
            try {
                NoteBytesPair[] pairs = new NoteBytesPair[params.length + 2];
                pairs[0] = new NoteBytesPair("typ", EventBytes.TYPE_CMD); // TYPE_CMD
                pairs[1] = new NoteBytesPair("cmd", command);
                
                if (params != null && params.length > 0) {
                    System.arraycopy(params, 0, pairs, 2, params.length);
                }
                
                synchronized (writer) {
                    writer.writeObject(new NoteBytesObject(pairs));
                }
                
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, ioExecutor);
    }
    
    private void sendCommand(String command, NoteBytesPair... params) throws IOException {
        int pairCount = 2 + (params != null ? params.length : 0);
        NoteBytesPair[] pairs = new NoteBytesPair[pairCount];
        
        pairs[0] = new NoteBytesPair("typ", EventBytes.TYPE_CMD);
        pairs[1] = new NoteBytesPair("cmd", command);
        
        if (params != null) {
            System.arraycopy(params, 0, pairs, 2, params.length);
        }
        
        synchronized (writer) {
            writer.writeObject(new NoteBytesObject(pairs));
        }
    }
    
    /**
     * Send packet to daemon for a specific device (if device supports bidirectional)
     */
    public CompletableFuture<Void> sendToDevice(int sourceId, NoteBytesReadOnly payload) {
        return CompletableFuture.runAsync(() -> {
            try {
                synchronized (writer) {
                    writer.write(new NoteBytesReadOnly(sourceId));
                    writer.write(payload);
                }
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, ioExecutor);
    }
    
    // ===== QUERIES =====
    
    public List<VirtualDevice> getDevices() {
        return new ArrayList<>(devices.values());
    }
    
    public VirtualDevice getDevice(int sourceId) {
        return devices.get(sourceId);
    }
    
    public VirtualDevice getDeviceByName(String deviceName) {
        Integer sourceId = deviceNameToId.get(deviceName);
        return sourceId != null ? devices.get(sourceId) : null;
    }
    
    public boolean isConnected() {
        return connected && connectionState.hasFlag(InputStateFlags.CONTAINER_ACTIVE);
    }
    
    public ContextPath getBasePath() {
        return basePath;
    }
    
    public BitFlagStateMachine getConnectionState() {
        return connectionState;
    }
    
    // ===== CLEANUP =====
    
    private void handleDisconnect() {
        connected = false;
        running = false;
        
        connectionState.clearFlag(InputStateFlags.CONTAINER_ACTIVE);
        
        // Mark all devices as suspended
        for (VirtualDevice device : devices.values()) {
            registry.setSourceState(device.fullPath, InputStateFlags.PROCESS_SUSPENDED);
        }
        
        System.out.println("Disconnected from daemon: " + connectionName);
    }
    
    @Override
    public void close() {
        if (connected) {
            running = false;
            connected = false;
            
            try {
                if (socketChannel != null) {
                    socketChannel.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
            
            // Unregister all devices
            for (VirtualDevice device : devices.values()) {
                registry.unregisterSource(device.fullPath);
            }
            
            devices.clear();
            deviceNameToId.clear();
            
            connectionState.setFlag(InputStateFlags.PROCESS_KILLED);
            ioExecutor.shutdown();
        }
    }
    
    private int getPid() {
        return (int) ProcessHandle.current().pid();
    }
    
    // ===== VIRTUAL DEVICE INFO =====
    
    /**
     * VirtualDevice - Information about a tunneled daemon device
     */
    public static class VirtualDevice {
        public final int sourceId;
        public final String name;
        public final String type;
        public final ContextPath fullPath;
        
        public VirtualDevice(int sourceId, String name, String type, ContextPath fullPath) {
            this.sourceId = sourceId;
            this.name = name;
            this.type = type;
            this.fullPath = fullPath;
        }
        
        public NoteBytesReadOnly getSourceIdAsNoteBytes() {
            return new NoteBytesReadOnly(sourceId);
        }
        
        @Override
        public String toString() {
            return String.format("VirtualDevice{id=%d, name='%s', type='%s', path=%s}",
                sourceId, name, type, fullPath);
        }
    }
}