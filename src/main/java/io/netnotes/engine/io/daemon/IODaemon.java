package io.netnotes.engine.io.daemon;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
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
import io.netnotes.engine.io.MessageBuilder;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.DaemonProtocolState.ClientStateFlags;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
import io.netnotes.engine.messaging.NoteMessaging.Modes;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.utils.VirtualExecutors;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.streams.StreamUtils;

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
    private NoteBytesWriter daemonWriter;
    private NoteBytesReader daemonReader;
    
    // Message dispatch maps
    private final Map<NoteBytesReadOnly, MessageExecutor> m_execMsgMap = new ConcurrentHashMap<>();
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_routedMsgMap = new ConcurrentHashMap<>();
    private final Map<NoteBytes, StreamChannel> deviceEventChannels = new ConcurrentHashMap<>();
    private final Map<NoteBytes, NoteBytesWriter> deviceEventWriters = new ConcurrentHashMap<>();

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
                Log.logMsg("IODaemon running at: " + contextPath);
            });
    }
    
    @Override
    public void onStart() {
        Log.logMsg("IODaemon starting");
    }
    
    @Override
    public void onStop() {
        Log.logMsg("IODaemon stopping");
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
                Log.logError("No cmd field in routed message");
                return CompletableFuture.completedFuture(null);
            }
            
            // Dispatch using routed message map
            RoutedMessageExecutor executor = m_routedMsgMap.get(cmd);
            if (executor != null) {
                return executor.execute(command, packet);
            } else {
                Log.logError("Unknown command: " + cmd);
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
        Log.logMsg("Received control stream from: " + fromPath);
        
        VirtualExecutors.getVirtualExecutor().execute(() -> {
 
            try (NoteBytesReader reader = new NoteBytesReader(new PipedInputStream( channel.getChannelStream(), StreamUtils.PIPE_BUFFER_SIZE))) {
                channel.getReadyFuture().complete(null);
                 Log.logMsg("[IODaemon.handleStreamChannel] active, from: " + fromPath + " waiting for commands...");
                NoteBytesReadOnly nextBytes = reader.nextNoteBytesReadOnly();
                while (nextBytes != null && connected) {
                    if (nextBytes.getType() == NoteBytesMetaData.STRING_TYPE) {
                        NoteBytes deviceIdBytes = nextBytes;
                        
                        NoteBytesReadOnly payload = reader.nextNoteBytesReadOnly();
                        
                        if (payload != null && payload.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                            NoteBytesMap msgMap = payload.getAsNoteBytesMap();
                            NoteBytesReadOnly cmd = msgMap.getReadOnly(Keys.CMD);
                            
                            // Check for device disconnect notification
                            if (cmd != null && cmd.equals(ProtocolMesssages.DEVICE_DISCONNECTED)) {
                                handleDeviceDisconnect(deviceIdBytes);
                            } else {
                                // Forward control message to daemon
                                // Write routed message: [STRING:deviceId][OBJECT:payload]
                                synchronized(daemonWriter) {
                                    daemonWriter.write(deviceIdBytes);
                                    daemonWriter.write(payload);
                                    daemonWriter.flush();
                                }
                                Log.logMsg("Forwarded control message for device: " + deviceIdBytes);
                            }
                        }
                    }
                    
                    nextBytes = reader.nextNoteBytesReadOnly();
                }
            } catch (IOException e) {
                Log.logError("Control stream error: " + e.getMessage());
            }
        });
    }

   

    /**
     * Handle device disconnection from ClaimedDevice
     */
    private void handleDeviceDisconnect(NoteBytes deviceId) {
        Log.logMsg("Device disconnecting: " + deviceId);
        cleanupDeviceStreams(deviceId);
    }

    /**
     * Cleanup streams for a specific device
     */
    private void cleanupDeviceStreams(NoteBytes deviceId) {
        // Shutdown async writer
        deviceEventWriters.remove(deviceId);
     
        // Close stream channel
        StreamChannel channel = deviceEventChannels.remove(deviceId);
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException e) {
                Log.logError("Error closing event channel for " + deviceId + ": " + e.getMessage());
            }
        }
        
        Log.logMsg("Cleaned up streams for device: " + deviceId);
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
                    
                    daemonWriter = new NoteBytesWriter(outputStream);
                    daemonReader = new NoteBytesReader(inputStream);
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
                        NoteBytesObject helloCmd = MessageBuilder.createCommand(
                            ProtocolMesssages.HELLO,
                            new NoteBytesPair(Keys.VERSION, DAEMON_VERSION),
                            new NoteBytesPair("daemon_id", m_UUID)
                        );
                        
                        daemonWriter.write(helloCmd);
                        
                        // Wait for ACCEPT response
                        NoteBytesReadOnly response = daemonReader.nextNoteBytesReadOnly();
                        if (response.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                            throw new IOException("Invalid handshake response");
                        }
                        
                        NoteBytesMap responseMap = response.getAsNoteBytesMap();
                        NoteBytesReadOnly type = responseMap.getReadOnly(Keys.TYPE);
                        
                        if (type == null || !type.equals(EventBytes.TYPE_ACCEPT)) {
                            throw new IOException("Handshake rejected");
                        }
                        
                        Log.logMsg("Connected");
                        connected = true;
                   
                    }catch(IOException e){
                        connected = false;
                        throw new CompletionException("Handshake failed", e);
                    }
                }else{
                    Log.logMsg("Already connected");
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
                    NoteBytesReadOnly first = daemonReader.nextNoteBytesReadOnly();
                    if (first == null) {
                        Log.logMsg("Connection closed by daemon");
                        break;
                    }
                    
                    switch(first.getType()) {
                        case NoteBytesMetaData.STRING_TYPE:
                            // ROUTED MESSAGE: [STRING:deviceId][OBJECT:payload]
                            handleRoutedMessage(first);
                        break;
                        case NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE:
                            // CONTROL MESSAGE: [OBJECT]
                            handleControlMessage(first);
                        break;
                        default:
                            Log.logError("Unexpected message type: " + first.getType());
                    }
                }
            } catch (Exception e) {
                Log.logError("Read loop error: " + e.getMessage());
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
    private void handleRoutedMessage(NoteBytes deviceIdBytes) throws IOException {
        NoteBytesReadOnly payload = daemonReader.nextNoteBytesReadOnly();
        if (payload == null) {
            throw new EOFException("Daemon closed stream");
        }
        
        NoteBytesWriter writer = deviceEventWriters.get(deviceIdBytes);
        if (writer == null) {
            Log.logError("No event writer for device: " + deviceIdBytes);
            return;
        }
        
        // Write routed message: [STRING:deviceId][OBJECT/ENCRYPTED:payload]
        synchronized(writer) {
            writer.write(deviceIdBytes);
            writer.write(payload);
            writer.flush();
        }
    }

    
    /**
     * Handle control message from daemon (protocol messages)
     */
    private void handleControlMessage(NoteBytesReadOnly messageBytes) {
        NoteBytesMap map = messageBytes.getAsNoteBytesMap();
        
        NoteBytes typeBytes = map.get(Keys.TYPE);
        if (typeBytes == null) {
            Log.logError("No type field in control message");
            return;
        }
        
        // Dispatch using message map
        MessageExecutor executor = m_execMsgMap.get(typeBytes);
        if (executor != null) {
            executor.execute(map);
        } else {
            Log.logError("Unknown message type: " + typeBytes);
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
    
        try {
            NoteBytesObject daemonRequest = MessageBuilder.createCommand(
                ProtocolMesssages.REQUEST_DISCOVERY
            );
            synchronized(daemonWriter){
                daemonWriter.write(daemonRequest);
                daemonWriter.flush();
            }
            // Reply immediately - device list comes via broadcast
            reply(request, ProtocolObjects.SUCCESS_OBJECT);
            return CompletableFuture.completedFuture(null);
        } catch (IOException ex) {
            reply(request, ProtocolObjects.getErrorObject(ex.getMessage()));
            return CompletableFuture.failedFuture(ex);
        }
    
    }
    
   /**
     * Handle claim request from session - setup event stream
     * IODaemon requests stream TO ClaimedDevice (for sending events)
     */
    private CompletableFuture<Void> handleClaimRequest(NoteBytesMap command, RoutedPacket request) {
        NoteBytes sessionIdBytes = command.get(Keys.SESSION_ID);
        NoteBytes deviceIdBytes = command.get(Keys.DEVICE_ID);
        NoteBytes modeBytes = command.getOrDefault(Keys.MODE, Modes.PARSED);

        if(deviceIdBytes == null || sessionIdBytes == null){
            reply(request, ProtocolObjects.getErrorObject("Invalid claim request"));
            return CompletableFuture.completedFuture(null);
        }

        String sessionId = sessionIdBytes != null ? sessionIdBytes.getAsString() : null;
        String mode = modeBytes.getAsString();
        
       

        ClientSession session = getSession(sessionId);
        if (session == null) {
            reply(request, ProtocolObjects.getErrorObject("Session not found: " + sessionId));
            return CompletableFuture.completedFuture(null);
        }
        String deviceIdString = deviceIdBytes.getAsString();
        ClaimedDevice claimedDevice = session.getClaimedDevice(deviceIdString);
        if (claimedDevice == null) {
            reply(request, ProtocolObjects.getErrorObject("Device not found in session: " + deviceIdString));
            return CompletableFuture.completedFuture(null);
        }
        
        ContextPath devicePath = claimedDevice.getContextPath();
        
        // IODaemon requests stream TO ClaimedDevice (for sending events)
        return requestStreamChannel(devicePath)
            .thenCompose(eventChannel -> {
                // Store channel
                deviceEventChannels.put(deviceIdBytes, eventChannel);
                
                // Create async writer for this channel
                NoteBytesWriter eventWriter = new NoteBytesWriter(
                    eventChannel.getQueuedOutputStream()
                );
                deviceEventWriters.put(deviceIdBytes, eventWriter);
                
                return eventChannel.getReadyFuture();
            })
            .thenRun(() -> {
                // Send claim request to daemon
                NoteBytesObject daemonRequest = MessageBuilder.createCommand(
                    ProtocolMesssages.CLAIM_ITEM,
                    new NoteBytesPair(Keys.DEVICE_ID, deviceIdBytes),
                    new NoteBytesPair(Keys.PID, session.clientPid),
                    new NoteBytesPair(Keys.MODE, mode)
                );
                
                 try {
                    synchronized(daemonWriter) {
                        daemonWriter.write(daemonRequest);
                        daemonWriter.flush();
                    }
                    
                    // Reply success to session
                    reply(request, ProtocolObjects.SUCCESS_OBJECT);
                    
                    Log.logMsg("Setup event stream for device: " + deviceIdString);
                    
                } catch (IOException e) {
                    Log.logError("Failed to send claim to daemon: " + e.getMessage());
                    reply(request, ProtocolObjects.getErrorObject(e.getMessage()));
                }
            })
            .exceptionally(ex -> {
                reply(request, ProtocolObjects.getErrorObject(ex.getMessage()));
                return null;
            });
    }
    
    /**
     * Handle release request from session - forward to daemon
     */
    private CompletableFuture<Void> handleReleaseRequest(NoteBytesMap command, RoutedPacket request) {
        String deviceId = command.get(Keys.DEVICE_ID).getAsString();
        try {

            NoteBytesObject daemonRequest = MessageBuilder.createCommand(
                ProtocolMesssages.RELEASE_ITEM,
                new NoteBytesPair(Keys.DEVICE_ID, deviceId)
            );
            
            synchronized(daemonWriter) {
                daemonWriter.write(daemonRequest);
                daemonWriter.flush();
            }
            
            reply(request, ProtocolObjects.SUCCESS_OBJECT);
            return CompletableFuture.completedFuture(null);
        } catch (IOException e) {
            reply(request, ProtocolObjects.getErrorObject(e.getMessage()));
            return CompletableFuture.failedFuture(e);
        }

    }

    
    // ===== DAEMON MESSAGE HANDLERS =====
    
    private void handleCommand(NoteBytesMap map) {
        NoteBytes cmdBytes = map.get(Keys.CMD);
        if (cmdBytes == null) {
            Log.logError("No cmd field in TYPE_CMD message");
            return;
        }
        
        // Dispatch command subtype
        MessageExecutor executor = m_execMsgMap.get(cmdBytes);
        if (executor != null) {
            executor.execute(map);
        } else {
            Log.logError("Unknown command: " + cmdBytes);
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
        
        Log.logMsg("Broadcasted device list to " + sessions.size() + " sessions");
    }
    
    private void handleDeviceClaimed(NoteBytesMap map) {
        Log.logMsg("Device claim confirmed by daemon");
    }
    
    private void handleDeviceReleased(NoteBytesMap map) {
        Log.logMsg("Device release confirmed by daemon");
    }
    
    private void handlePing(NoteBytesMap map) {
        try {
            NoteBytesObject pong = MessageBuilder.createCommand(
                ProtocolMesssages.PONG
            );
            
            synchronized(daemonWriter) {
                daemonWriter.write(pong);
                daemonWriter.flush();
            }
        } catch (IOException e) {
            Log.logError("Failed to send pong: " + e.getMessage());
        }
    }
    
    private void handlePong(NoteBytesMap map) {
        // Sessions handle their own pongs
    }
    
    private void handleAccept(NoteBytesMap map) {
        NoteBytes statusBytes = map.get(Keys.STATUS);
        if (statusBytes != null) {
            Log.logMsg("Daemon: " + statusBytes.getAsString());
        }
    }
    
    private void handleError(NoteBytesMap map) {
        int errorCode = map.get(Keys.ERROR_CODE).getAsInt();
        String errorMsg = map.get(Keys.MSG).getAsString();
        Log.logError("Daemon error " + errorCode + ": " + errorMsg);
    }
    
    private void handleDisconnected(NoteBytesMap map) {
        Log.logMsg("Daemon disconnected");
        handleDisconnect();
    }
    
    // ===== CLEANUP =====
    
    private void handleDisconnect() {
        connected = false;
        running = false;
        
        Log.logMsg("Socket disconnected, cleaning up all device streams...");
        
        // Cleanup all device event streams
        for (NoteBytes deviceId : deviceEventChannels.keySet()) {
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
        
        Log.logMsg("Disconnected from daemon, cleaned up " + 
            sessions.size() + " sessions");
    }

    /**
     * Notify session that daemon socket disconnected
     */
    private void notifySessionDisconnect(ClientSession session) {
        if (!session.isAlive() || session.state.hasState(ClientStateFlags.DISCONNECTING)) {
            Log.logMsg("Session " + session.sessionId + " already disconnecting, skipping notification");
            return;
        }

        NoteBytesObject notification = new NoteBytesObject();
        notification.add(Keys.TYPE, EventBytes.TYPE_DISCONNECTED);
        notification.add(Keys.SEQUENCE, NoteUUID.getNextUUID64());
        notification.add(Keys.MSG, "IODaemon socket disconnected");

        try {
            emitTo(session.getContextPath(), notification);
        } catch (Exception e) {
            Log.logError("Failed to notify session: " + e.getMessage());
        }
    }


    @Override
    public void kill() {
        if (connected) {
            NoteBytesObject shutdown = new NoteBytesObject();
            shutdown.add(Keys.TYPE, EventBytes.TYPE_SHUTDOWN);
            shutdown.add(Keys.SEQUENCE, NoteUUID.getNextUUID64());
            
            try {
                synchronized(daemonWriter) {
                    daemonWriter.write(shutdown);
                    daemonWriter.flush();
                }
            } catch (IOException e) {
                // Ignore
            }
            
            handleDisconnect();
            
            try {
                if (socketChannel != null) {
                    socketChannel.close();
                }
            } catch (IOException e) {
                Log.logError("Error closing socket: " + e.getMessage());
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