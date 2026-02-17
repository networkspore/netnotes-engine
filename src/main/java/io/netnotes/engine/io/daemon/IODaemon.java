package io.netnotes.engine.io.daemon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.MessageBuilder;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.DaemonProtocolState.ClientStateFlags;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.io.process.ChannelWriter;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.ProcessKeys;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.ErrorCodes;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.utils.noteBytes.NoteUUID;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.collections.NoteBytesPair;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.noteBytes.processing.NoteBytesReader;
import io.netnotes.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

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
 * - Multiple ClientSession instances (one per session)
 * - ClaimedDevice processes registered under IODaemon (path includes sessionId)
 * 
 * Hierarchy: IODaemon → (sessionId namespace) → ClaimedDevice
 */
public class IODaemon extends FlowProcess {
    public final NoteBytesReadOnly DAEMON_VERSION = new NoteBytesReadOnly(1);

    public static final class SESSION_CMDS {
        public static final NoteBytesReadOnly CREATE_SESSION = 
            new NoteBytesReadOnly("create_session");
        public static final NoteBytesReadOnly DESTROY_SESSION = 
            new NoteBytesReadOnly("destroy_session");
        public static final NoteBytesReadOnly LIST_SESSIONS = 
            new NoteBytesReadOnly("list_sessions");
    }

    private final String socketPath;
    private final String m_UUID = NoteUUID.createSafeUUID128();
    
    private SocketChannel socketChannel;
    private NoteBytesWriter daemonWriter;
    private NoteBytesReader daemonReader;

    //Re-entrant safe serial executors
    private final SerializedVirtualExecutor daemonWriterExec = new SerializedVirtualExecutor();
    private final SerializedVirtualExecutor daemonInternalExec = new SerializedVirtualExecutor();

    private final Map<NoteBytes, ClientSession> sessions = new ConcurrentHashMap<>();

    // Message dispatch maps
    private final Map<NoteBytesReadOnly, MessageExecutor> m_execMsgMap = new HashMap<>();
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_routedMsgMap = new ConcurrentHashMap<>();
    private final Map<NoteBytes, ChannelWriter> deviceStreams = new ConcurrentHashMap<>();
  //  private final Map<NoteBytes, NoteBytesWriter> deviceEventWriters = new ConcurrentHashMap<>();
    private final Map<NoteBytes, ClaimedDevice> claimedDevices = new ConcurrentHashMap<>();

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

    private CompletableFuture<Void> writeToDaemon(NoteBytesObject message) {
        return daemonWriterExec.execute(() -> {
            try {
                // No synchronized needed - already serialized!
                daemonWriter.write(message);
                daemonWriter.flush();
            } catch (IOException e) {
                throw new CompletionException("Failed to write to daemon", e);
            }
        }).whenComplete((v,ex)->{
            if(!socketChannel.isConnected() && connected){
                handleDisconnect();
            }
        });
    }

    private CompletableFuture<Void> writeToDaemon(NoteBytes id,  NoteBytes messageObject) {
        return daemonWriterExec.execute(() -> {

            try {
                daemonWriter.write(id);
                daemonWriter.write(messageObject);
                daemonWriter.flush();
            } catch (IOException e) {
                throw new CompletionException("Failed to write to daemon", e);
            }
        
        }).whenComplete((v,ex)->{
            if(!socketChannel.isConnected() && connected){
                handleDisconnect();
            }
        });
    }

    private boolean writeToDevice(NoteBytes deviceId,  NoteBytes messageObject) {
        if(messageObject == null){
            return false;
        }
        ChannelWriter deviceStream = deviceStreams.get(deviceId);
        if(deviceStream == null){
            Log.logMsg("[IODaemon] deviceStream not available for: " + deviceId);
            return true;
        }
        SerializedVirtualExecutor exec = deviceStream.getWriteExec();

        if(!exec.isShutdown()){
            exec.executeFireAndForget(() -> {
                NoteBytesWriter writer = deviceStream.getWriter();
             
                if (writer == null) {
                    Log.logMsg("[IODaemon] device stream waiting: " + deviceId);
                    try{
                        writer = deviceStream.getReadyWriter()
                            .orTimeout(2, TimeUnit.SECONDS)
                            .join();
                        
                    }catch(Exception e){
                        Log.logError("[IODaemon] device stream timed out: " + deviceId, e);
                        return;
                    }
                }
    
                try {
                    writer.write(messageObject);
                    writer.flush();
                } catch (IOException e) {
                    Log.logError("[IODaemon]", "write to device failed", e);
                    //TODO: handle device write failed
                }
            
            });
        }
        return true;
    }

    Map<NoteBytes, ClaimedDevice> getClaimedDevices(){
        return claimedDevices;
    }
    
    // ===== PROCESS LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> run() {
        return connect()
            .thenRun(() -> startReadLoop());
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
        // Handle legacy routed requests (direct method calls preferred)
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
                            // Forward control message to daemon
                            // Write routed message: [STRING:deviceId][OBJECT:payload]
                            writeToDaemon(deviceIdBytes, payload);
                            Log.logMsg("Forwarded control message for device: " + deviceIdBytes);
                         
                        }
                    }
                    
                    nextBytes = reader.nextNoteBytesReadOnly();
                }
            } catch (IOException e) {
                Log.logError("Control stream error: " + e.getMessage());
            }
        });
    }

   



    
    // ===== CONNECTION LIFECYCLE =====
    
    public CompletableFuture<Void> connect() {
        return establishConnection().thenCompose((v)->performHandshake())
            .exceptionally((ex)->{
                establsihConnectionFuture = null;
                handshakeFuture = null;
                return null;
            });
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
                        writeToDaemon(helloCmd);
                        
                        // Wait for ACCEPT response
                        NoteBytesReadOnly response = daemonReader.nextNoteBytesReadOnly();
                        if (response.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                            throw new IOException("Invalid handshake response");
                        }
                        
                        NoteBytesObject responseObj = response.getAsNoteBytesObject();
                        NoteBytesPair responseEvent = responseObj.get(Keys.EVENT);
                     
                        if (responseEvent == null 
                            || responseEvent.getValue() == null 
                            || !responseEvent
                                .getValue()
                                .equals(EventBytes.TYPE_ACCEPT)
                        ) {
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
                            if(!writeToDevice(first, daemonReader.nextNoteBytesReadOnly())){
                                Log.logError("[IODaemon] Expected payload, EOF signaled");
                                break;
                            }
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
     * Handle control message from daemon (protocol messages)
     */
    private void handleControlMessage(NoteBytesReadOnly messageBytes) {
        NoteBytesMap map = messageBytes.getAsNoteBytesMap();
        
        NoteBytes typeBytes = map.get(Keys.EVENT);
        if (typeBytes == null) {
            Log.logError("No type field in control message");
            return;
        }
        
        // Dispatch using message map
        MessageExecutor executor = m_execMsgMap.get(typeBytes);
        if (executor != null) {
            daemonInternalExec.executeFireAndForget(()->{
                executor.execute(map);
            });
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
        m_execMsgMap.put(EventBytes.TYPE_ACCEPT, this::handleAccept);
        m_execMsgMap.put(EventBytes.TYPE_ERROR, this::handleError);
        
        // Command subtypes (daemon responses)
        m_execMsgMap.put(ProtocolMesssages.ITEM_LIST, this::broadcastDeviceList);
        m_execMsgMap.put(ProtocolMesssages.ITEM_CLAIMED, this::handleDeviceClaimed);
        m_execMsgMap.put(ProtocolMesssages.ITEM_RELEASED, this::handleDeviceReleased);
    }
    
    /**
     * Setup routed message handlers (session requests)
     */
    private void setupRoutedMessageMapping() {
        // Session management commands
        m_routedMsgMap.put(SESSION_CMDS.CREATE_SESSION, this::handleCreateSessionRequest);
        m_routedMsgMap.put(SESSION_CMDS.DESTROY_SESSION, this::handleDestroySessionRequest);
        m_routedMsgMap.put(SESSION_CMDS.LIST_SESSIONS, this::handleListSessionsRequest);
        
    }
    
    // ===== SESSION MANAGEMENT =====
    

    // ===== SESSION MANAGEMENT HANDLERS =====
    
    /**
     * Handle create_session request from ContainerHandle
     * 
     * Expected message format:
     * {
     *   cmd: "create_session",
     *   session_id: "unique-id",
     *   pid: 12345
     * }
     * 
     * Reply format:
     * {
     *   status: "success",
     *   path: "/io-daemon/session-id"
     * }
     */
    private CompletableFuture<Void> handleCreateSessionRequest(
        NoteBytesMap command, RoutedPacket request) {
        
        NoteBytes sessionId = command.get(Keys.SESSION_ID);
        NoteBytes pidBytes = command.get(Keys.PID);
        
        if (sessionId == null) {
            NoteBytesMap error = new NoteBytesMap();
            error.put(Keys.STATUS, ProtocolMesssages.ERROR);
            error.put(Keys.ERROR_MESSAGE, new NoteBytes("session_id required"));
            reply(request, error.toNoteBytes());
            return CompletableFuture.completedFuture(null);
        }
        
        int clientPid = pidBytes != null ? pidBytes.getAsInt() : 
            (int) ProcessHandle.current().pid();
        
        boolean alreadyExists = sessions.containsKey(sessionId);

        if(alreadyExists){
            NoteBytesMap response = new NoteBytesMap();
            response.put(Keys.SESSION_ID, sessionId);
            response.put(Keys.STATUS, ProtocolMesssages.ERROR);
            response.put(Keys.ERROR_CODE, ErrorCodes.ALREADY_EXISTS);
            response.put(Keys.MSG, new NoteBytes("Session already exists"));
            reply(request, response.toNoteBytes());
            return CompletableFuture.completedFuture(null);
        }
        // Create new session (or return existing)
        return createSession(sessionId, clientPid)
            .thenAccept(session -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                response.put(Keys.SESSION_ID, session.sessionId);
                response.put(Keys.PID, new NoteBytes(session.clientPid));
    
                reply(request, response.toNoteBytes());
                
                Log.logMsg("[IODaemon] Created session: " + sessionId);
            })
            .exceptionally(ex -> {
                NoteBytesMap error = new NoteBytesMap();
                error.put(Keys.STATUS, ProtocolMesssages.ERROR);
                error.put(Keys.ERROR_MESSAGE, new NoteBytes(ex.getMessage()));
                error.put(Keys.ERROR_CODE, ErrorCodes.FAILED);
                reply(request, error.toNoteBytes());
                Log.logError("[IODaemon] Failed to create session: " + ex.getMessage());
                return null;
            });
    }
    
    /**
     * Handle destroy_session request
     */
    private CompletableFuture<Void> handleDestroySessionRequest(
            NoteBytesMap command, RoutedPacket request) {
        
        NoteBytes sessionId = command.get(Keys.SESSION_ID);
        if (sessionId == null) {
            return replyError(request, "session_id required");
        }
    
        ClientSession session = getSession(sessionId);
        
        if (session == null) {
            return replyError(request, "Session not found: " + sessionId);
        }
        
        return destroySession(sessionId)
            .thenRun(() -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                reply(request, response.toNoteBytes());
                
                Log.logMsg("[IODaemon] Destroyed session: " + sessionId);
            })
            .exceptionally(ex -> {
                replyError(request, ex.getMessage());
                return null;
            });
    }
    
   
    /**
     * Handle list_sessions request
     */
    private CompletableFuture<Void> handleListSessionsRequest(
            NoteBytesMap command, RoutedPacket request) {
        
        List<ClientSession> sessions = getSessions();
        
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        response.put(Keys.ITEM_COUNT, new NoteBytes(sessions.size()));
        
        // Build session info array
        NoteBytesMap sessionsList = new NoteBytesMap();
        for (int i = 0; i < sessions.size(); i++) {
            ClientSession session = sessions.get(i);
            NoteBytesMap sessionInfo = new NoteBytesMap();
            sessionInfo.put(Keys.SESSION_ID, new NoteBytes(session.sessionId));
            sessionInfo.put(Keys.PID, new NoteBytes(session.clientPid));
            
            sessionsList.put(String.valueOf(i), sessionInfo);
        }
        
        response.put("sessions", sessionsList);
        reply(request, response.toNoteBytes());
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Create a new client session (in-process, not a FlowProcess)
     */
    public CompletableFuture<ClientSession> createSession(NoteBytes sessionId, int clientPid) {
        ClientSession existing = sessions.get(sessionId);
        if (existing != null) {
            if (existing.isHealthy()) {
                return CompletableFuture.completedFuture(existing);
            }
            sessions.remove(sessionId);
        }
        
        ClientSession session = new ClientSession(
            sessionId,
            clientPid,
            this,
            daemonCommands
        );
        session.init();
        sessions.put(sessionId, session);
        
        return CompletableFuture.completedFuture(session);
    }
    
    public CompletableFuture<Void> destroySession(NoteBytes sessionId) {
        ClientSession session = sessions.remove(sessionId);
        if (session == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        session.state.addState(ClientStateFlags.DISCONNECTING);
        return session.shutdown();
    }
    
    public ClientSession getSession(NoteBytes sessionId) {
        return sessions.get(sessionId);
    }
    
    public List<ClientSession> getSessions() {
        return List.copyOf(sessions.values());
    }

    // ===== CLAIMED DEVICE REGISTRATION =====
    
    public ContextPath registerClaimedDevice(ClaimedDevice device, ContextPath devicePath) {
        if (registry == null) {
            throw new IllegalStateException("IODaemon not initialized");
        }
        return registry.registerProcess(device, devicePath, contextPath, registry);
    }

    public CompletableFuture<Void> startClaimedDevice(ContextPath devicePath) {
        if (registry == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("IODaemon not initialized"));
        }
        return registry.startProcess(devicePath);
    }

 
    
    /**
     * Helper to reply with error
     */
    private CompletableFuture<Void> replyError(RoutedPacket request, String message) {
        NoteBytesMap error = new NoteBytesMap();
        error.put(Keys.STATUS, ProtocolMesssages.ERROR);
        error.put(Keys.ERROR_MESSAGE, new NoteBytes(message));
        reply(request, error.toNoteBytes());
        return CompletableFuture.completedFuture(null);
    }
    
    
    CompletableFuture<Void> addClaimedDevice(ClaimedDevice claimedDevice){
        NoteBytes deviceId = claimedDevice.getDeviceId();
        getClaimedDevices().put(claimedDevice.getDeviceId(), claimedDevice);
    
         return requestStreamChannel(claimedDevice.getDevicePath())
            .thenAccept(eventChannel -> {
                // Store channel
                ChannelWriter channelWriter = new ChannelWriter(eventChannel);
                deviceStreams.put(deviceId, channelWriter);
                
                // Create async writer for this channel
             
            });
    }

    
    // ===== DAEMON MESSAGE HANDLERS =====
    
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
        NoteBytes deviceId = map.get(Keys.DEVICE_ID);
        NoteBytes sessionId = map.get(ProcessKeys.CORRELATION_ID);
        if (deviceId == null) {
            IllegalStateException ex = new IllegalStateException("deviceId missing");
            Log.logError("[IODaemon]", "handleDeviceClaimed", ex);
            //TODO: temporary only while testing
            throw ex;
        }
        if(sessionId == null){
            IllegalStateException ex = new IllegalStateException("correlationId missing");
            Log.logError("[IODaemon]", "handleDeviceClaimed", ex);
             //TODO: temporary only while testing
            throw ex;
        }
        ClientSession session = sessions.get(sessionId);
        if(session == null || session.state.hasState(ClientStateFlags.DISCONNECTING)){
            if(session == null){
                Log.logError("Device claimed but no owning session found: " + sessionId  + ":" + deviceId);
            }
            releaseDevice(sessionId, deviceId);
            return;
        }
        
        session.handleDeviceClaimed(deviceId, map);
    }

    private NoteBytes getDeviceSessionId(NoteBytes deviceId){
        ClaimedDevice claimedDevice = claimedDevices.get(deviceId);
        return claimedDevice != null ? claimedDevice.getSessionId() : null;
    }
    
    private void handleDeviceReleased(NoteBytesMap map) {
        NoteBytes deviceId = map.get(Keys.DEVICE_ID);
        NoteBytes sessionId = map.get(ProcessKeys.CORRELATION_ID);

        if (deviceId == null) {
            String msg = "ITEM_RELEASED missing device_id";
            Throwable ex = new IllegalStateException(msg);
            Log.logError("[IODaemon]", msg, ex);
            return;
        }
        
        sessionId = sessionId == null ? getDeviceSessionId(deviceId) : sessionId;

        ClientSession session = sessionId != null ? sessions.get(sessionId) : null;

        if(session != null){
            session.handleDeviceReleased(deviceId);
        }else{
            Log.logMsg("[IODaemon] session not found for release: " + sessionId + ":" + deviceId);
            completeDeviceRelease(deviceId);
        }
        
     
    }
    
    private void handlePing(NoteBytesMap map) {
        NoteBytesObject pong = MessageBuilder.createCommand(
            ProtocolMesssages.PONG
        );
        writeToDaemon(pong);
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
    

    // ===== CLEANUP =====
    
    private void handleDisconnect() {
        connected = false;
        running = false;

        Log.logMsg("Socket disconnected, notifying sessions...");

        // Take a snapshot of sessions before clearing maps.
        List<ClientSession> sessionsSnapshot = getSessions();

        // For sessions that will be torn down (no onDisconnect handler registered),
        // we need to release their devices locally since the daemon socket is gone
        // and we won't receive ITEM_RELEASED confirmations.
        //
        // For sessions with an onDisconnect handler we leave device cleanup to
        // the application — it may reconnect and reclaim the same devices.
        for (ClientSession session : sessionsSnapshot) {
            // notifySessionDisconnect guards against already-disconnecting sessions
            // and delegates to session.disconnected() which honours any registered
            // onDisconnect handler — keeping the session alive or tearing it down.
            notifySessionDisconnect(session);
        }

        // Only clear daemon-side maps for sessions that are fully gone.
        // Sessions kept alive by an onDisconnect handler still exist in `sessions`
        // and will be cleaned up when the application calls shutdown() or when a
        // new connection is established.
        sessions.entrySet().removeIf(entry -> !entry.getValue().isHealthy());

        // Clean up claimed devices that belong to fully-torn-down sessions.
        claimedDevices.entrySet().removeIf(entry -> {
            NoteBytes sessionId = entry.getValue().getSessionId();
            return !sessions.containsKey(sessionId);
        });
        deviceStreams.entrySet().removeIf(entry -> {
            ClaimedDevice dev = claimedDevices.get(entry.getKey());
            return dev == null;
        });

        establsihConnectionFuture = null;
        handshakeFuture = null;
        Log.logMsg("Disconnected from daemon, processed " +
            sessionsSnapshot.size() + " sessions");
    }

    /**
     * Notify session that daemon socket disconnected.
     * Delegates to {@link ClientSession#disconnected()} which honours any
     * registered {@link ClientSession.DisconnectHandler}.
     */
    private void notifySessionDisconnect(ClientSession session) {
        if (!session.isHealthy() || session.state.hasState(ClientStateFlags.DISCONNECTING)) {
            Log.logMsg("Session " + session.sessionId + " already disconnecting, skipping notification");
            return;
        }

        session.disconnected();
    }


    @Override
    public void kill() {
        if (connected) {
            NoteBytesObject shutdown = new NoteBytesObject();
            shutdown.add(Keys.EVENT, EventBytes.TYPE_SHUTDOWN);
     
            
            writeToDaemon(shutdown);
            
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

    public void completeDeviceRelease(NoteBytes deviceId) {
        ClaimedDevice device = claimedDevices.remove(deviceId);
        ChannelWriter deviceStream = deviceStreams.remove(deviceId);
        if(device != null){
            device.release();
            registry.unregisterProcess(device.getDevicePath());
        }
        if(deviceStream != null){
            deviceStream.shutdown();
        }            
        Log.logMsg("Cleaned up streams for device: " + deviceId);
    }

    private CompletableFuture<Void> releaseDevice(NoteBytes sessionId, NoteBytes deviceId){
        NoteBytesObject request = MessageBuilder.createCommand(
        ProtocolMesssages.RELEASE_ITEM,
            new NoteBytesPair(Keys.DEVICE_ID, deviceId),
            new NoteBytesPair(ProcessKeys.CORRELATION_ID, sessionId)
        );
        
        return writeToDaemon(request);
    }

     /**
     * Commands implementation - called by ClientSessions on serialExec
     */
    private final IODaemonInterface daemonCommands = new IODaemonInterface() {
        
        @Override
        public void requestDiscovery(NoteBytes sessionId) {
            NoteBytesObject request = MessageBuilder.createCommand(
                ProtocolMesssages.REQUEST_DISCOVERY
            );
            
            writeToDaemon(request);
        }
        
        @Override
        public CompletableFuture<Void> claimDevice(
            NoteBytes sessionId,
            NoteBytes deviceId, 
            NoteBytes mode
        ) {
            ClientSession session = sessions.get(sessionId);
            if (session == null) {
                String msg = "Session not found: " + sessionId;
                Log.logError(msg);
                return CompletableFuture.failedFuture(new IllegalStateException(msg));
            }

         
            // Send claim to daemon via serialized write
            NoteBytesObject request = MessageBuilder.createCommand(
                ProtocolMesssages.CLAIM_ITEM,
                new NoteBytesPair(Keys.DEVICE_ID, deviceId),
                new NoteBytesPair(Keys.PID, session.clientPid),
                new NoteBytesPair(Keys.MODE, mode),
                new NoteBytesPair(ProcessKeys.CORRELATION_ID, sessionId)
            );
            
            return writeToDaemon(request);
            
        }
        
        @Override
        public CompletableFuture<Void> releaseDevice(NoteBytes sessionId, NoteBytes deviceId) {
            return IODaemon.this.releaseDevice(sessionId, deviceId);
        }
    };


    
}