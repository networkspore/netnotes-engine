package io.netnotes.engine.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;

/**
 * IODaemonSource - Connects to NoteDaemon and translates devices into sources.
 * 
 * Architecture:
 * - Thin translation layer between daemon and registry
 * - Registry generates sourceIds
 * - Daemon locks devices to our PID
 * - Routes packets based on INTEGER-prefixed sourceId
 * - Handles OBJECT packets as protocol messages
 */
public class IODaemonSource implements InputSource, AutoCloseable {
    public static final String DEFAULT_UNIX_SOCKET_PATH = "/run/netnotes/notedaemon.sock";
    
    private final String socketPath;
    private final Executor executor;
    private final InputSourceRegistry registry;
    private final String sourceName;
    
    private SocketChannel socketChannel;
    private NoteBytesWriter writer;
    private NoteBytesReader reader;
    private volatile boolean connected = false;
    private volatile boolean running = false;

    private InputSourceCapabilities m_ioDaemonSourceCapabilities;
    
    // Track devices: device_name -> DaemonDevice
    private final Map<String, DaemonDevice> devicesByName = new ConcurrentHashMap<>();
    private final Map<NoteBytesReadOnly, DaemonDevice> devicesById = new ConcurrentHashMap<>();
    
    public IODaemonSource(String socketPath, String name, Executor executor) {
        this.socketPath = socketPath;
        this.executor = executor;
        this.registry = InputSourceRegistry.getInstance();
        this.sourceName = name;
        m_ioDaemonSourceCapabilities =  new InputSourceCapabilities.Builder("IODaemon")
            .withMultiDevice()
            .build();
    }
    
    public String getName(){
        return sourceName;
    }

    /**
     * Connect to daemon and negotiate protocol
     */
    public boolean connect() throws IOException {
        if(socketChannel.isConnected() && connected){
            return true;
        }
        // Connect to Unix domain socket
        Path path = Path.of(socketPath);
        socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX);
        UnixDomainSocketAddress addr = UnixDomainSocketAddress.of(path);
        socketChannel.connect(addr);
        
        if (!socketChannel.isConnected()) {
            return false;
        }
        
        InputStream inputStream = Channels.newInputStream(socketChannel);
        OutputStream outputStream = Channels.newOutputStream(socketChannel);
        
        writer = new NoteBytesWriter(outputStream);
        reader = new NoteBytesReader(inputStream);
        
        connected = true;
        
        // Start protocol negotiation
        negotiateProtocol();
        return true;
    }
    
    /**
     * Protocol negotiation:
     * 1. Send HELLO
     * 2. Request capabilities
     * 3. Receive available devices
     * 4. Register each device with registry (get sourceIds)
     * 5. Send sourceIds + PID back to daemon
     * 6. Start routing loop
     */
    private void negotiateProtocol() throws IOException {
        // 1. Send HELLO
        sendCommand("hello", null);
        
        // 2. Request capabilities
        sendCommand("capabilities", null);
        
        // 3. Read capabilities response (OBJECT packet)
        NoteBytesReadOnly response = reader.nextNoteBytesReadOnly();
        if (response.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            throw new IOException("Expected OBJECT response, got type: " + response.getType());
        }
        
        handleCapabilitiesResponse(response);
        
        // 4. Register each device with registry and send back to daemon
        int ourPid = getPid();
        
        for (DaemonDevice device : devicesByName.values()) {
            // Registry generates the sourceId (as INTEGER NoteBytesReadOnly)
            NoteBytesReadOnly sourceId = registry.registerSource(
                device.name,
                device.capabilities,
                device.contextPath
            );
            
            device.sourceId = sourceId;
            devicesById.put(sourceId, device);
            
            System.out.println("Registered daemon device: " + device.name + 
                             " with sourceId: " + sourceId.getAsInt());
            
            // 5. Send device registration to daemon
            NoteBytesPair[] registrationParams = {
                new NoteBytesPair("device", device.name),
                new NoteBytesPair("source_id", sourceId),
                new NoteBytesPair("pid", ourPid)
            };
            sendCommand("register_device", registrationParams);
            
            // Register reply channel so destinations can talk back to daemon
            registry.registerSourceReplyChannel(sourceId, command -> {
                try {
                    handleDestinationCommand(sourceId, command);
                } catch (IOException e) {
                    System.err.println("Error handling destination command: " + e.getMessage());
                }
            });
        }
        
        // 6. Request keyboard access (example)
        sendCommand("request_keyboard", null);
        
        // 7. Start routing loop
        running = true;
        startRouting();
    }
    
    /**
     * Handle capabilities response from daemon
     */
    private void handleCapabilitiesResponse(NoteBytesReadOnly responseReadOnly) {
        NoteBytesMap response = responseReadOnly.getAsMap();
        
        String cmd = response.get("cmd").getAsString();
        if (!"capabilities".equals(cmd)) {
            throw new IllegalStateException("Expected capabilities response, got: " + cmd);
        }
        
        int version = response.get("version").getAsInt();
        System.out.println("Daemon version: " + version);
        
        // Get available devices
        NoteBytes devicesValue = response.get("devices");
        if (devicesValue != null && devicesValue.getType() == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
            NoteBytesReadOnly[] devicesArray = devicesValue.getAsNoteBytesArrayReadOnly().getAsArray();
            
            for (int i = 0; i < devicesArray.length; i++) {
                NoteBytesReadOnly deviceValue = devicesArray[i];
                if (deviceValue.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                    NoteBytesMap deviceMap = deviceValue.getAsMap();
                    
                    String deviceName = deviceMap.get("name").getAsString();
                    String deviceType = deviceMap.get("type").getAsString();
                    
                    DaemonDevice device = new DaemonDevice();
                    device.name = deviceName;
                    device.deviceType = deviceType;
                    device.capabilities = createCapabilities(deviceType);
                    device.contextPath = ContextPath.of(socketPath, deviceName);
                    
                    devicesByName.put(deviceName, device);
                    
                    System.out.println("Found device: " + deviceName + " (type: " + deviceType + ")");
                }
            }
        }
    }
    
    /**
     * Create capabilities based on device type
     */
    private InputSourceCapabilities createCapabilities(String deviceType) {
        if ("keyboard".equals(deviceType)) {
            return new InputSourceCapabilities.Builder("Daemon Keyboard")
                .enableKeyboard()
                .withScanCodes()
                .build();
        } else if ("mouse".equals(deviceType)) {
            return new InputSourceCapabilities.Builder("Daemon Mouse")
                .enableMouse()
                .providesAbsoluteCoordinates()
                .build();
        } else {
            return new InputSourceCapabilities.Builder("Unknown Device")
                .build();
        }
    }
    
    /**
     * Start routing loop: read packets and route them
     */
    private void startRouting() {
        executor.execute(() -> {
            try {
                while (running && connected) {
                    NoteBytesReadOnly first = reader.nextNoteBytesReadOnly();
                    if (first == null) {
                        break;
                    }
                    
                    if (first.getType() == NoteBytesMetaData.INTEGER_TYPE) {
                        // This IS the sourceId
                        NoteBytesReadOnly sourceId = first;
                        NoteBytesReadOnly packet = reader.nextNoteBytesReadOnly();
                        
                        if (packet == null) {
                            System.err.println("Missing packet after sourceId");
                            continue;
                        }
                        
                        // Create routed packet and route it
                        RoutedPacket routed = new RoutedPacket(sourceId, packet);
                        registry.routePacket(routed);
                        
                    } else if (first.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                        // Protocol message
                        handleProtocolMessage(first);
                    } else {
                        System.err.println("Unexpected packet type: " + first.getType());
                    }
                }
            } catch (Exception e) {
                System.err.println("Routing loop error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                running = false;
                handleDisconnect();
            }
        });
    }
    
    /**
     * Handle protocol messages (OBJECT packets during operation)
     */
    private void handleProtocolMessage(NoteBytesReadOnly messageReadOnly) {
        NoteBytesMap message = messageReadOnly.getAsMap();
        
        int type = message.get("typ").getAsByte();
        
        if (type == 249) { // TYPE_DISCONNECTED
            String deviceName = message.get("device").getAsString();
            System.out.println("Device disconnected: " + deviceName);
            
            DaemonDevice device = devicesByName.get(deviceName);
            if (device != null) {
                registry.setSourceState(device.sourceId, SourceState.DISCONNECTED_BIT, true);
            }
        } else if (type == 248) { // TYPE_ERROR
            int errorCode = message.get("err").getAsInt();
            String errorMsg = message.get("msg").getAsString();
            System.err.println("Daemon error " + errorCode + ": " + errorMsg);
        } else if (type == 252) { // TYPE_ACCEPT
            String status = message.get("sts").getAsString();
            System.out.println("Daemon: " + status);
        }
    }
    
    /**
     * Handle command from destination (sent via registry)
     */
    private void handleDestinationCommand(NoteBytesReadOnly sourceId, NoteBytesReadOnly command) 
            throws IOException {
        
        DaemonDevice device = devicesById.get(sourceId);
        if (device == null) {
            System.err.println("Unknown sourceId: " + sourceId);
            return;
        }
        
        NoteBytesMap commandMap = command.getAsMap();
        String cmd = commandMap.get("cmd").getAsString();
        
        System.out.println("Destination command for " + device.name + ": " + cmd);
        
        if ("enable_encryption".equals(cmd)) {
            // Forward encryption request to daemon
            NoteBytesPair[] params = {
                new NoteBytesPair("device", device.name),
                new NoteBytesPair("phase", commandMap.get("phase").getAsInt()),
                new NoteBytesPair("pub_key", commandMap.get("pub_key"))
            };
            sendCommand("enable_encryption", params);
        }
        // Add other command handlers as needed
    }
    
    /**
     * Send a command to the daemon (OBJECT packet)
     */
    private void sendCommand(String command, NoteBytesPair[] params) throws IOException {
        int pairCount = 2 + (params != null ? params.length : 0);
        NoteBytesPair[] pairs = new NoteBytesPair[pairCount];
        
        pairs[0] = new NoteBytesPair("typ", (byte) 254); // TYPE_CMD
        pairs[1] = new NoteBytesPair("cmd", command);
        
        if (params != null) {
            System.arraycopy(params, 0, pairs, 2, params.length);
        }
        
        writer.writeObject(new NoteBytesObject(pairs));
    }
    
    /**
     * Handle disconnection
     */
    private void handleDisconnect() {
        connected = false;
        
        // Mark all devices as disconnected
        for (DaemonDevice device : devicesByName.values()) {
            if (device.sourceId != null) {
                registry.setSourceState(device.sourceId, SourceState.DISCONNECTED_BIT, true);
            }
        }
        
        System.out.println("Disconnected from daemon");
    }
    
    /**
     * Get list of registered devices
     */
    public List<DaemonDevice> getDevices() {
        return new ArrayList<>(devicesByName.values());
    }
    
    /**
     * Check if connected
     */
    public boolean isConnected() {
        return connected;
    }
    
    /**
     * Get current process PID
     */
    private int getPid() {
        return (int) ProcessHandle.current().pid();
    }
    
    @Override
    public void close() {
        if(connected){
            running = false;
            connected = false;
            
            try {
                if (socketChannel != null) {
                    socketChannel.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            // Unregister all devices
            for (DaemonDevice device : devicesByName.values()) {
                if (device.sourceId != null) {
                    registry.unregisterSource(device.sourceId);
                }
            }
            
            devicesByName.clear();
            devicesById.clear();
        }
    }
    
    /**
     * Represents a device from the daemon
     */
    public static class DaemonDevice {
        public String name;
        public String deviceType;
        public NoteBytesReadOnly sourceId;
        public InputSourceCapabilities capabilities;
        public ContextPath contextPath;
        
        @Override
        public String toString() {
            return "DaemonDevice{" +
                   "name='" + name + '\'' +
                   ", type='" + deviceType + '\'' +
                   ", sourceId=" + (sourceId != null ? sourceId.getAsInt() : "null") +
                   '}';
        }
    }

    @Override
    public void start() throws IOException {
        
        connect();
      
    }

    @Override
    public void stop() {
        close();
    }

    public InputSourceCapabilities getCapabilities(){
        return m_ioDaemonSourceCapabilities;
    }

}