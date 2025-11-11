package io.netnotes.engine.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.nio.channels.Channels;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

import io.netnotes.engine.messaging.EventBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;

/**
 * IODaemonSource - Connects to NoteDaemon and registers devices as input sources.
 * 
 * Protocol flow:
 * 1. Connect to daemon
 * 2. Request capabilities (what devices daemon controls)
 * 3. Client registers each device with InputSourceRegistry
 * 4. Client sends source IDs back to daemon
 * 5. Daemon starts sending events with assigned source IDs
 * 
 * This allows one daemon to control multiple devices (keyboard, mouse, etc.)
 * and each device gets its own source ID in the client.
 */
public class IODaemonSource implements AutoCloseable {
    public static final String DEFAULT_UNIX_SOCKET_PATH = "/run/netnotes/notedaemon.sock";
    
    private final String socketPath;
    private final Executor executor;
    private final InputIONode ioNode;
    private final InputSourceRegistry registry;
    
    private SocketChannel socket;
    private NoteBytesWriter writer;
    private NoteBytesReader reader;
    private volatile boolean connected = false;
    private volatile boolean running = false;
    
    // Registered devices: device_name -> source_id
    private final List<DaemonDevice> devices = new ArrayList<>();
    
    public IODaemonSource(String socketPath, InputIONode ioNode, Executor executor) {
        this.socketPath = socketPath;
        this.ioNode = ioNode;
        this.executor = executor;
        this.registry = InputSourceRegistry.getInstance();
    }
    
    /**
     * Connect to daemon and discover available devices
     */
    public boolean connect() throws IOException {
        // Connect to Unix domain socket
        Path path = Path.of(socketPath);
        SocketChannel socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX);
        UnixDomainSocketAddress addr = UnixDomainSocketAddress.of(path);
        socketChannel.connect(addr);
        
        if(socketChannel.isConnected()){

            InputStream inputStream = Channels.newInputStream(socketChannel);
            OutputStream outputStream = Channels.newOutputStream(socketChannel);
            
            writer = new NoteBytesWriter(outputStream);
            reader = new NoteBytesReader(inputStream);
            
            connected = true;
            
            // Start protocol negotiation
            negotiateProtocol();
            return true;
        }
        
        return false;
        
    }
    
    /**
     * Protocol negotiation with daemon
     */
    private void negotiateProtocol() throws IOException {
        // 1. Send HELLO
        sendCommand("hello", null);
        
        // 2. Request capabilities
        sendCommand("capabilities", null);
        
        // 3. Read capabilities response
        NoteBytesObject capsResponse = readResponse();
        handleCapabilitiesResponse(capsResponse);
        
        // 4. For each device, register with client registry and send source ID
        for (DaemonDevice device : devices) {
            // Register in client's source registry
            NoteBytesReadOnly sourceId = registry.registerSource(
                device.name,
                device.capabilities,
                device.contextPath
            );
            
            device.sourceId = sourceId.getAsInt();
            
            // Tell daemon the source ID for this device
            NoteBytesPair[] deviceConfig = {
                new NoteBytesPair("device", device.name),
                new NoteBytesPair("source_id", device.sourceId),
            };
            sendCommand("register_device", deviceConfig);
            
            // Register handler in IO node
            ioNode.registerSourceHandler(device.sourceId, 
                createHandlerForDevice(device));
            
            System.out.println("Registered daemon device: " + device.name + 
                             " with source ID: " + device.sourceId);
        }
        
        // 5. Request keyboard access (example)
        sendCommand("request_keyboard", null);
        
        // 6. Start reading events
        running = true;
        startEventLoop();
    }
    
    /**
     * Handle capabilities response from daemon
     */
    private void handleCapabilitiesResponse(NoteBytesObject obj) {
        NoteBytesMap caps = obj.getAsMap();
        String cmd = caps.get("cmd").getAsString();
        if (!"capabilities_response".equals(cmd)) {
            throw new IllegalStateException("Expected capabilities_response, got: " + cmd);
        }
        
        int version = caps.get("version").getAsInt();
        int modes = caps.get("modes").getAsInt();
        boolean encryption = caps.get("encryption").getAsBoolean();
        boolean uidFilter = caps.get("uid_filter").getAsBoolean();
        
        System.out.println("Daemon capabilities:");
        System.out.println("  Version: " + version);
        System.out.println("  Modes: 0x" + Integer.toHexString(modes));
        System.out.println("  Encryption: " + encryption);
        System.out.println("  UID filter: " + uidFilter);
        
        // For now, assume daemon controls keyboard
        // In a real implementation, daemon would advertise available devices
        DaemonDevice keyboard = new DaemonDevice();
        keyboard.name = "notedaemon-keyboard";
        keyboard.deviceType = "keyboard";
        keyboard.capabilities = new InputSourceCapabilities.Builder("NoteDaemon Keyboard")
            .enableKeyboard()
            .withScanCodes()
            .build();
        keyboard.contextPath = ContextPath.of("daemon", "notedaemon", "keyboard");
        keyboard.supportsEncryption = encryption;
        
        devices.add(keyboard);
    }
    
    /**
     * Create a handler for a specific device
     */
    private Function<byte[], InputRecord> createHandlerForDevice(
            DaemonDevice device) {
        if (device.encryptionEnabled && device.encryptionSession != null) {
            return InputPacketReader.createEncryptedHandler(device.encryptionSession);
        } else {
            return InputPacketReader.createPlaintextHandler();
        }
    }
    
    /**
     * Start event reading loop
     */
    private void startEventLoop() {
        executor.execute(() -> {
            try {
                while (running && connected) {
                    // Read packets from daemon
                    // These will have source headers and be routed automatically
                    NoteBytesReadOnly packet = reader.nextNoteBytesReadOnly();
                    if (packet == null) {
                        break;
                    }
                    
                    // Forward to IO node's write queue
                    byte[] packetBytes = packet.get();
                    CompletableFuture<byte[]> future = CompletableFuture.completedFuture(packetBytes);
                    ioNode.getWriteQueue().put(future);
                }
            } catch (Exception e) {
                System.err.println("Event loop error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                running = false;
                handleDisconnect();
            }
        });
    }
    
    /**
     * Send a command to the daemon
     */
    private void sendCommand(String command, NoteBytesPair[] params) throws IOException {
        NoteBytesPair[] pairs = new NoteBytesPair[2 + (params != null ? params.length : 0)];
        pairs[0] = new NoteBytesPair("typ", EventBytes.TYPE_CMD); // TYPE_CMD
        pairs[1] = new NoteBytesPair("cmd", command);
        final int prefixSize = 2;
        if (params != null) {
            // Add params fields to command
            for (int i = 0; i < params.length  ; i++) {
                pairs[i + prefixSize] = params[i];
            }
        }

        writer.writeObject(new NoteBytesObject(pairs));
    }
    
    /**
     * Read a response from daemon
     */
    private NoteBytesObject readResponse() throws IOException {
        NoteBytesReadOnly response = reader.nextNoteBytesReadOnly();
        if (response == null) {
            throw new IOException("No response from daemon");
        }
        
        // Parse as object
        return response.getAsNoteBytesObject();
    }
    
    /**
     * Handle disconnection
     */
    private void handleDisconnect() {
        connected = false;
        
        // Mark all devices as disconnected in registry
        for (DaemonDevice device : devices) {
            registry.setSourceState(
                new NoteBytesReadOnly(device.sourceId),
                SourceState.DISCONNECTED_BIT,
                true
            );
        }
        
        System.out.println("Disconnected from daemon");
    }
    
    /**
     * Enable encryption for a specific device
     */
    public void enableEncryption(String deviceName, 
            InputPacketReader.EncryptionSession encryptionSession) {
        for (DaemonDevice device : devices) {
            if (device.name.equals(deviceName)) {
                device.encryptionEnabled = true;
                device.encryptionSession = encryptionSession;
                
                // Re-register handler with encryption
                ioNode.registerSourceHandler(device.sourceId, 
                    createHandlerForDevice(device));
                
                // Tell daemon to enable encryption
                try {
                    NoteBytesPair[] encConfig = {
                        new NoteBytesPair("device", deviceName),
                        new NoteBytesPair("enable", true)
                    };
                    sendCommand("encryption_init", encConfig);
                } catch (IOException e) {
                    System.err.println("Failed to enable encryption: " + e.getMessage());
                }
                
                break;
            }
        }
    }
    
    /**
     * Get list of registered devices
     */
    public List<DaemonDevice> getDevices() {
        return new ArrayList<>(devices);
    }
    
    /**
     * Check if connected
     */
    public boolean isConnected() {
        return connected;
    }
    
    @Override
    public void close() {
        running = false;
        connected = false;
        
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // Unregister all devices
        for (DaemonDevice device : devices) {
            registry.unregisterSource(new NoteBytesReadOnly(device.sourceId));
            ioNode.unregisterSourceHandler(device.sourceId);
        }
        
        devices.clear();
    }
    
    /**
     * Represents a device controlled by the daemon
     */
    public static class DaemonDevice {
        public String name;
        public String deviceType;
        public int sourceId;
        public InputSourceCapabilities capabilities;
        public ContextPath contextPath;
        public boolean supportsEncryption;
        public boolean encryptionEnabled;
        public InputPacketReader.EncryptionSession encryptionSession;
        
        @Override
        public String toString() {
            return "DaemonDevice{" +
                   "name='" + name + '\'' +
                   ", type='" + deviceType + '\'' +
                   ", sourceId=" + sourceId +
                   ", encryption=" + encryptionEnabled +
                   '}';
        }
    }
}