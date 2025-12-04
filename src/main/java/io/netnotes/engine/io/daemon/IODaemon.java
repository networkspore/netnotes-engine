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
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.DaemonProtocolState.ClientStateFlags;
import io.netnotes.engine.io.daemon.IODaemonProtocol.AsyncNoteBytesWriter;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.utils.VirtualExecutors;

/**
 * IODaemon - Socket manager for daemon connection
 * 
 * Responsibilities:
 * - Manage socket connection to C++ daemon
 * - Protocol framing (read/write to socket)
 * - Route incoming messages from daemon to correct session/device
 * - Respond to session requests by forwarding to daemon
 * 
 * Multi-session design:
 * - One IODaemon per daemon socket
 * - Multiple ClientSession children (one per session)
 * - Each ClientSession has ClaimedDevice children
 * 
 * Hierarchy: IODaemon → ClientSession → ClaimedDevice
 */
public class IODaemon extends FlowProcess {
    public final NoteBytesReadOnly DAEMON_VERSION = new NoteBytesReadOnly(1);
    private final String socketPath;
    private final String m_UUID = NoteUUID.createSafeUUID128();
    
    private SocketChannel socketChannel;
    private AsyncNoteBytesWriter asyncWriter;
    private NoteBytesReader reader;
    
    // Message dispatch maps
    private final Map<NoteBytesReadOnly, MessageExecutor> m_execMsgMap = new ConcurrentHashMap<>();
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_routedMsgMap = new ConcurrentHashMap<>();
    private final Map<String, StreamChannel> deviceEventChannels = new ConcurrentHashMap<>();
    private final Map<String, AsyncNoteBytesWriter> deviceEventWriters = new ConcurrentHashMap<>();

    private volatile boolean connected = false;
    private volatile boolean running = false;
    
    /**
     * Functional interface for routed message handlers (with packet context for reply)
     */
    
    public IODaemon(String name, String socketPath) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.socketPath = socketPath;
        
        setupMessageMapping();
        setupRoutedMessageMapping();
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
    public void onStart() {
        System.out.println("IODaemon starting");
    }
    
    @Override
    public void onStop() {
        System.out.println("IODaemon stopping");
        handleDisconnect();
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Handle requests from ClientSession children
        try {
            NoteBytesReadOnly payload = packet.getPayload();
            NoteBytesMap command = payload.getAsNoteBytesMap();
            
            NoteBytes cmd = command.get(Keys.CMD);
            if (cmd == null) {
                System.err.println("No cmd field in routed message");
                return CompletableFuture.completedFuture(null);
            }
            
            // Dispatch using routed message map
            RoutedMessageExecutor executor = m_routedMsgMap.get(cmd);
            if (executor != null) {
                return executor.execute(command, packet);
            } else {
                System.err.println("Unknown command: " + cmd);
                return CompletableFuture.completedFuture(null);
            }
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    
    /**
     * Handle control stream from ClaimedDevice
     * ClaimedDevice requests this stream to send control messages
     */
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        System.out.println("Received control stream from: " + fromPath);
        
        channel.getReadyFuture().complete(null);
        
        channel.startReceiving(input -> {
            try (NoteBytesReader reader = new NoteBytesReader(input)) {
                NoteBytesReadOnly nextBytes = reader.nextNoteBytesReadOnly();
                
                while (nextBytes != null && connected) {
                    if (nextBytes.getType() == NoteBytesMetaData.STRING_TYPE) {
                        String deviceId = nextBytes.toString();
                        NoteBytesReadOnly payload = reader.nextNoteBytesReadOnly();
                        
                        if (payload != null && payload.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                            NoteBytesMap msgMap = payload.getAsNoteBytesMap();
                            NoteBytesReadOnly cmd = msgMap.getReadOnly(Keys.CMD);
                            
                            // Check for device disconnect notification
                            if (cmd != null && cmd.equals(ProtocolMesssages.DEVICE_DISCONNECTED)) {
                                handleDeviceDisconnect(deviceId);
                            } else {
                                // Forward control message to daemon socket
                                asyncWriter.writeRoutedMessageAsync(deviceId, payload.getAsNoteBytesObject())
                                    .thenRun(() -> System.out.println(
                                        "Forwarded control message for device: " + deviceId))
                                    .exceptionally(ex -> {
                                        System.err.println("Forward failed: " + ex.getMessage());
                                        return null;
                                    });
                            }
                        }
                    }
                    
                    nextBytes = reader.nextNoteBytesReadOnly();
                }
            } catch (IOException e) {
                System.err.println("Control stream error: " + e.getMessage());
            }
        });
    }

    /**
     * Handle device disconnection from ClaimedDevice
     */
    private void handleDeviceDisconnect(String deviceId) {
        System.out.println("Device disconnecting: " + deviceId);
        cleanupDeviceStreams(deviceId);
    }

    /**
     * Cleanup streams for a specific device
     */
    private void cleanupDeviceStreams(String deviceId) {
        // Shutdown async writer
        AsyncNoteBytesWriter writer = deviceEventWriters.remove(deviceId);
        if (writer != null) {
            writer.shutdown();
            try {
                writer.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Close stream channel
        StreamChannel channel = deviceEventChannels.remove(deviceId);
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                System.err.println("Error closing event channel for " + deviceId + ": " + e.getMessage());
            }
        }
        
        System.out.println("Cleaned up streams for device: " + deviceId);
    }
    
    // ===== CONNECTION LIFECYCLE =====
    
    public CompletableFuture<Void> connect() {
        return establishConnection().thenAccept((v)->performHandshake());
    }
    
    private CompletableFuture<Void> establsihConnectionFuture = null;

    private CompletableFuture<Void> establishConnection() {
        if(establsihConnectionFuture == null || establsihConnectionFuture.isDone() && isConnected() == false){
            establsihConnectionFuture =  CompletableFuture.runAsync(()->{
                try{
                    Path path = Path.of(socketPath);
                    socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX);
                    UnixDomainSocketAddress addr = UnixDomainSocketAddress.of(path);
                    socketChannel.connect(addr);
                    
                    if (!socketChannel.isConnected()) {
                        throw new IOException("Connection dropped");
                    }
                    
                    InputStream inputStream = Channels.newInputStream(socketChannel);
                    OutputStream outputStream = Channels.newOutputStream(socketChannel);
                    
                    asyncWriter = new IODaemonProtocol.AsyncNoteBytesWriter(outputStream);
                    reader = new NoteBytesReader(inputStream);
                }catch(IOException e){
                    throw new CompletionException("Could not establish connection", e);
                }
            }, VirtualExecutors.getVirtualExecutor());
            return establsihConnectionFuture;
        }else{
            return establsihConnectionFuture;
        }
    }
    
    private CompletableFuture<Void> handshakeFuture = null;

    private CompletableFuture<Void> performHandshake() {
        if(handshakeFuture == null || handshakeFuture.isDone()){
            handshakeFuture = CompletableFuture.runAsync(()->{
                if(!isConnected()){
                    try{
                        NoteBytesObject hello = IODaemonProtocol.MessageBuilder.createCommand(
                            ProtocolMesssages.HELLO,
                            new NoteBytesPair(Keys.VERSION, DAEMON_VERSION),
                            new NoteBytesPair("daemon_id", m_UUID)
                        );
                        
                        asyncWriter.writeSync(hello);
                        
                        // Wait for ACCEPT response
                        NoteBytesReadOnly response = reader.nextNoteBytesReadOnly();
                        if (response.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                            throw new IOException("Invalid handshake response");
                        }
                        
                        NoteBytesMap responseMap = response.getAsNoteBytesMap();
                        NoteBytesReadOnly type = responseMap.getReadOnly(Keys.TYPE);
                        
                        if (type == null || !type.equals(EventBytes.TYPE_ACCEPT)) {
                            throw new IOException("Handshake rejected");
                        }
                        
                        System.out.println("Connected");
                        connected = true;
                   
                    }catch(IOException e){
                        connected = false;
                        throw new CompletionException("Handshake failed", e);
                    }
                }else{
                    System.out.println("Already connected");
                }

            }, VirtualExecutors.getVirtualExecutor());
            return handshakeFuture;
        }else{
            return handshakeFuture;
        }
    }
    
    /**
     * Main read loop - reads from daemon socket and routes to sessions/devices
     */
    private CompletableFuture<Void> startReadLoop() {
        running = true;
        
        return CompletableFuture.runAsync(() -> {
            try {
                while (running && connected) {
                    NoteBytesReadOnly first = reader.nextNoteBytesReadOnly();
                    if (first == null) {
                        System.out.println("Connection closed by daemon");
                        break;
                    }
                    
                    if (first.getType() == NoteBytesMetaData.STRING_TYPE) {
                        // ROUTED MESSAGE: [STRING:deviceId][OBJECT:payload]
                        handleRoutedMessage(first.toString());
                        
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
     * Handle routed message from daemon (device data)
     * Format: [STRING:deviceId][OBJECT:payload]
     * 
     */
     private void handleRoutedMessage(String deviceId) throws IOException {
        NoteBytesReadOnly payload = reader.nextNoteBytesReadOnly();
        if (payload == null) {
            System.err.println("No payload for routed message");
            return;
        }
        
        AsyncNoteBytesWriter writer = deviceEventWriters.get(deviceId);
        if (writer == null) {
            System.err.println("No event writer for device: " + deviceId);
            return;
        }
        
        // Write routed message asynchronously
        writer.writeAsync(new NoteBytes(deviceId))
            .thenCompose(v -> writer.writeAsync(payload))
            .exceptionally(ex -> {
                System.err.println("Failed to forward to event stream: " + ex.getMessage());
                return null;
            });
    }

    
    /**
     * Handle control message from daemon (protocol messages)
     */
    private void handleControlMessage(NoteBytesReadOnly messageBytes) {
        NoteBytesMap map = messageBytes.getAsNoteBytesMap();
        
        NoteBytes typeBytes = map.get(Keys.TYPE);
        if (typeBytes == null) {
            System.err.println("No type field in control message");
            return;
        }
        
        // Dispatch using message map
        MessageExecutor executor = m_execMsgMap.get(typeBytes);
        if (executor != null) {
            executor.execute(map);
        } else {
            System.err.println("Unknown message type: " + typeBytes);
        }
    }
    
    // ===== MESSAGE MAPPING SETUP =====
    
    /**
     * Setup message handlers for daemon control messages
     */
    private void setupMessageMapping() {
        // Protocol messages from daemon
        m_execMsgMap.put(EventBytes.TYPE_PING, this::handlePing);
        m_execMsgMap.put(EventBytes.TYPE_PONG, this::handlePong);
        m_execMsgMap.put(EventBytes.TYPE_ACCEPT, this::handleAccept);
        m_execMsgMap.put(EventBytes.TYPE_ERROR, this::handleError);
        m_execMsgMap.put(EventBytes.TYPE_CMD, this::handleCommand);
        m_execMsgMap.put(EventBytes.TYPE_DISCONNECTED, this::handleDisconnected);
        
        // Command subtypes (daemon responses)
        m_execMsgMap.put(ProtocolMesssages.ITEM_LIST, this::broadcastDeviceList);
        m_execMsgMap.put(ProtocolMesssages.ITEM_CLAIMED, this::handleDeviceClaimed);
        m_execMsgMap.put(ProtocolMesssages.ITEM_RELEASED, this::handleDeviceReleased);
    }
    
    /**
     * Setup routed message handlers (session requests)
     */
    private void setupRoutedMessageMapping() {
        // Session commands (forwarded to daemon)
        m_routedMsgMap.put(ProtocolMesssages.REQUEST_DISCOVERY, this::handleDiscoveryRequest);
        m_routedMsgMap.put(ProtocolMesssages.CLAIM_ITEM, this::handleClaimRequest);
        m_routedMsgMap.put(ProtocolMesssages.RELEASE_ITEM, this::handleReleaseRequest);
    }
    
    // ===== SESSION MANAGEMENT =====
    
    /**
     * Create a new client session
     */
    public CompletableFuture<ContextPath> createSession(String sessionId, int clientPid) {
        ContextPath sessionPath = contextPath.append(sessionId);
        
        if (registry.exists(sessionPath)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Session already exists: " + sessionId));
        }
        
        ClientSession session = new ClientSession(sessionId, clientPid);
     

        return spawnChild(session).thenApply(path->{
            session.state.addState(ClientStateFlags.CONNECTED);
            session.state.addState(ClientStateFlags.AUTHENTICATED);
            
            registry.startProcess(sessionPath);
            
            // Bidirectional connection for request-reply
            registry.connect(contextPath, sessionPath);
            registry.connect(sessionPath, contextPath);
            return path;
        });
      
       
    }
    
    public ClientSession getSession(String sessionId) {
        ContextPath sessionPath = contextPath.append(sessionId);
        return (ClientSession) registry.getProcess(sessionPath);
    }
    
    public List<ClientSession> getSessions() {
        return findChildrenByType(ClientSession.class);
    }
    
    // ===== SESSION REQUEST HANDLERS (ROUTED) =====
    
    /**
     * Handle discovery request from session - forward to daemon
     */
    private CompletableFuture<Void> handleDiscoveryRequest(NoteBytesMap command, RoutedPacket request) {
        NoteBytesObject daemonRequest = IODaemonProtocol.MessageBuilder.createCommand(
            ProtocolMesssages.REQUEST_DISCOVERY
        );
        
        return asyncWriter.writeAsync(daemonRequest)
            .thenRun(() -> {
                // Reply immediately - device list comes via broadcast
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                reply(request, response.getNoteBytesObject());
            })
            .exceptionally(ex -> {
                NoteBytesMap error = new NoteBytesMap();
                error.put(Keys.STATUS, ProtocolMesssages.ERROR);
                error.put(Keys.MSG, ex.getMessage());
                reply(request, error.getNoteBytesObject());
                return null;
            });
    }
    
   /**
     * Handle claim request from session - setup event stream
     * IODaemon requests stream TO ClaimedDevice (for sending events)
     */
    private CompletableFuture<Void> handleClaimRequest(NoteBytesMap command, RoutedPacket request) {
        String sessionId = command.get("session_id").getAsString();
        String deviceId = command.get(Keys.DEVICE_ID).getAsString();
        String mode = command.get(Keys.MODE).getAsString();
        
        ClientSession session = getSession(sessionId);
        if (session == null) {
            NoteBytesMap error = new NoteBytesMap();
            error.put(Keys.STATUS, ProtocolMesssages.ERROR);
            error.put(Keys.MSG, "Session not found: " + sessionId);
            reply(request, error.getNoteBytesObject());
            return CompletableFuture.completedFuture(null);
        }
        
        ClaimedDevice claimedDevice = session.getClaimedDevice(deviceId);
        if (claimedDevice == null) {
            NoteBytesMap error = new NoteBytesMap();
            error.put(Keys.STATUS, ProtocolMesssages.ERROR);
            error.put(Keys.MSG, "Device not found in session: " + deviceId);
            reply(request, error.getNoteBytesObject());
            return CompletableFuture.completedFuture(null);
        }
        
        ContextPath devicePath = claimedDevice.getContextPath();
        
        // IODaemon requests stream TO ClaimedDevice (for sending events)
        return requestStreamChannel(devicePath)
            .thenCompose(eventChannel -> {
                // Store channel
                deviceEventChannels.put(deviceId, eventChannel);
                
                // Create async writer for this channel
                AsyncNoteBytesWriter eventWriter = new AsyncNoteBytesWriter(
                    eventChannel.getOutputStream()
                );
                deviceEventWriters.put(deviceId, eventWriter);
                
                return eventChannel.getReadyFuture();
            })
            .thenCompose(v -> {
                // Send claim request to daemon
                NoteBytesObject daemonRequest = IODaemonProtocol.MessageBuilder.createCommand(
                    ProtocolMesssages.CLAIM_ITEM,
                    new NoteBytesPair(Keys.DEVICE_ID, deviceId),
                    new NoteBytesPair(Keys.PID, session.clientPid),
                    new NoteBytesPair(Keys.MODE, mode)
                );
                
                return asyncWriter.writeAsync(daemonRequest);
            })
            .thenRun(() -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                reply(request, response.getNoteBytesObject());
                
                System.out.println("Setup event stream for device: " + deviceId);
            })
            .exceptionally(ex -> {
                NoteBytesMap error = new NoteBytesMap();
                error.put(Keys.STATUS, ProtocolMesssages.ERROR);
                error.put(Keys.MSG, ex.getMessage());
                reply(request, error.getNoteBytesObject());
                return null;
            });
    }
    
    /**
     * Handle release request from session - forward to daemon
     */
    private CompletableFuture<Void> handleReleaseRequest(NoteBytesMap command, RoutedPacket request) {
        String deviceId = command.get(Keys.DEVICE_ID).getAsString();
        
        NoteBytesObject daemonRequest = IODaemonProtocol.MessageBuilder.createCommand(
            ProtocolMesssages.RELEASE_ITEM,
            new NoteBytesPair(Keys.DEVICE_ID, deviceId)
        );
        
        return asyncWriter.writeAsync(daemonRequest)
            .thenRun(() -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                reply(request, response.getNoteBytesObject());
            })
            .exceptionally(ex -> {
                NoteBytesMap error = new NoteBytesMap();
                error.put(Keys.STATUS, ProtocolMesssages.ERROR);
                error.put(Keys.MSG, ex.getMessage());
                reply(request, error.getNoteBytesObject());
                return null;
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
    
    /**
     * Broadcast device list to all discovering sessions
     */
    private void broadcastDeviceList(NoteBytesMap map) {
        List<ClientSession> sessions = getSessions();
        
        for (ClientSession session : sessions) {
            if (session.state.hasState(ClientStateFlags.DISCOVERING)) {
                session.handleDeviceList(map);
            }
        }
        
        System.out.println("Broadcasted device list to " + sessions.size() + " sessions");
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
        asyncWriter.writeAsync(pong);
    }
    
    private void handlePong(NoteBytesMap map) {
        // Sessions handle their own pongs
    }
    
    private void handleAccept(NoteBytesMap map) {
        NoteBytes statusBytes = map.get(Keys.STATUS);
        if (statusBytes != null) {
            System.out.println("Daemon: " + statusBytes.getAsString());
        }
    }
    
    private void handleError(NoteBytesMap map) {
        int errorCode = map.get(Keys.ERROR_CODE).getAsInt();
        String errorMsg = map.get(Keys.MSG).getAsString();
        System.err.println("Daemon error " + errorCode + ": " + errorMsg);
    }
    
    private void handleDisconnected(NoteBytesMap map) {
        System.out.println("Daemon disconnected");
        handleDisconnect();
    }
    
    // ===== CLEANUP =====
    
    private void handleDisconnect() {
        connected = false;
        running = false;
        
        System.out.println("Socket disconnected, cleaning up all device streams...");
        
        // Cleanup all device event streams
        for (String deviceId : deviceEventChannels.keySet()) {
            cleanupDeviceStreams(deviceId);
        }
        
        // Cleanup all sessions (they'll handle their ClaimedDevice cleanup)
        List<ClientSession> sessions = getSessions();
        for (ClientSession session : sessions) {
            // Notify session of disconnect
            notifySessionDisconnect(session);

            session.state.addState(ClientStateFlags.DISCONNECTING);
            
            registry.unregisterProcess(session.getContextPath());
        }
        
        System.out.println("Disconnected from daemon, cleaned up " + 
            sessions.size() + " sessions");
    }

    /**
     * Notify session that daemon socket disconnected
     */
    private void notifySessionDisconnect(ClientSession session) {
        if (!session.isAlive() || session.state.hasState(ClientStateFlags.DISCONNECTING)) {
            System.out.println("Session " + session.sessionId + " already disconnecting, skipping notification");
            return;
        }

        NoteBytesObject notification = new NoteBytesObject();
        notification.add(Keys.TYPE, EventBytes.TYPE_DISCONNECTED);
        notification.add(Keys.SEQUENCE, NoteUUID.getNextUUID64());
        notification.add(Keys.MSG, "IODaemon socket disconnected");

        try {
            emitTo(session.getContextPath(), notification);
        } catch (Exception e) {
            System.err.println("Failed to notify session: " + e.getMessage());
        }
    }


    @Override
    public void kill() {
        if (connected) {
            NoteBytesObject shutdown = new NoteBytesObject();
            shutdown.add(Keys.TYPE, EventBytes.TYPE_SHUTDOWN);
            shutdown.add(Keys.SEQUENCE, NoteUUID.getNextUUID64());
            
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
            
            // Shutdown socket writer
            asyncWriter.shutdown();
            try {
                asyncWriter.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        super.kill();
    }
    
    // ===== QUERIES =====
    
    public boolean isConnected() {
        return connected;
    }
    
    public String getSocketPath() {
        return socketPath;
    }
}