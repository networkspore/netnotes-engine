package io.netnotes.engine.io.daemon;

import java.io.IOException;
import java.io.PipedInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.streams.StreamUtils;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesEphemeral;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.collections.NoteBytesPair;
import io.netnotes.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.noteBytes.processing.NoteBytesReader;
import io.netnotes.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.ui.containers.ContainerHandle.EventDispatcher;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.capabilities.DeviceCapabilitySet;
import io.netnotes.engine.io.daemon.DaemonProtocolState.DeviceState;
import io.netnotes.engine.io.input.IEventFactory;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.process.ChannelWriter;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;


/**
 * ClaimedDevice - Represents a claimed USB device
 * 
 * Dependencies injected via constructor:
 * - deviceId: unique device identifier
 * - devicePath: this process's path in registry
 * - deviceType: "keyboard", "mouse", etc
 * - capabilities: what the device can do
 * - ioDaemonPath: WHERE TO SEND CONTROL MESSAGES (injected!)
 * 
 * Has TWO stream channels:
 * 1. Incoming event stream: receives events from daemon
 * 2. Outgoing control stream: sends control messages to daemon
 */
public class ClaimedDevice extends FlowProcess implements InputDevice {
    

    private final NoteBytesReadOnly deviceId;
    private final ContextPath devicePath;
    private final NoteBytesReadOnly deviceType;
    private final ContextPath ioDaemonPath;
    private final IEventFactory eventFactory; 
    private final NoteBytesReadOnly sessionId;
    private DeviceState deviceState;
    

    // TWO channels: incoming events + outgoing control
    private StreamChannel incomingEventStream;
    private ChannelWriter outgoingControlStream;
    
    private DeviceEncryptionSession encryptionSession;
    private final Map<NoteBytesReadOnly, MessageExecutor> m_execMsgMap = new ConcurrentHashMap<>();
    
    // Backpressure tracking
    private final AtomicInteger processedEvents = new AtomicInteger(0);
    private static final int ACK_BATCH_SIZE = 32;
    
    private volatile boolean active = false;
    private final EventHandlerRegistry eventHandlerRegistry;
    private volatile DeviceDisconnectHandler onDeviceDisconnected = null;

    @FunctionalInterface
    public interface DeviceDisconnectHandler {
        void onDeviceDisconnected(ClaimedDevice device);
    }

  

    private final byte[] ackBytes;
    /**
     * Constructor with dependency injection
     * 
     * @param deviceId Device identifier (e.g., "046d:c52b-1-3")
     * @param devicePath This process's path in registry
     * @param deviceType Device type ("keyboard", "mouse", etc)
     * @param capabilities What the device supports
     * @param ioDaemonPath Where to send control messages
     */
    public ClaimedDevice(
        NoteBytes sessionId,
        NoteBytes deviceId, 
        ContextPath devicePath, 
        NoteBytes deviceType, 
        DeviceCapabilitySet capabilities,
        ContextPath ioDaemonPath,
        IEventFactory eventFatory
    ) {
        
        super(deviceId.getAsString(), ProcessType.BIDIRECTIONAL);
        this.deviceId = deviceId.readOnly();
        this.sessionId = sessionId.readOnly();
        this.devicePath = devicePath;
        this.deviceType = deviceType.readOnly();
        this.ioDaemonPath = ioDaemonPath;
        this.eventFactory = eventFatory;
        this.eventHandlerRegistry = new EventHandlerRegistry();
        
        this.deviceState = new DeviceState(
            deviceId, 
            (int) ProcessHandle.current().pid(), 
            deviceType, 
            capabilities
        );

        ackBytes = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.EVENT, EventBytes.TYPE_CMD),
            new NoteBytesPair(Keys.CMD, ProtocolMesssages.RESUME),
            new NoteBytesPair(Keys.DEVICE_ID, deviceId),
            new NoteBytesPair(Keys.PROCESSED_COUNT, 0)
        }).get();

        setupMessageMapping();
    }

   public NoteBytesReadOnly getSessionId(){
        return sessionId;
   }

    

    public void setOnDeviceDisconnected(DeviceDisconnectHandler handler) {
        this.onDeviceDisconnected = handler;
    }

    
    @Override
    public void onStart() {
        Log.logMsg("ClaimedDevice starting: " + devicePath);
        
        // Request control stream TO IODaemon (for sending control messages)
        requestStreamChannel(ioDaemonPath)
            .thenAccept(channel -> {
                this.outgoingControlStream = new ChannelWriter(channel);
            })
            .exceptionally(ex -> {
                Log.logError("Failed to setup control stream: " + ex.getMessage());
                return null;
            });
    }

    private void setupMessageMapping() {
        m_execMsgMap.put(EventBytes.TYPE_ENCRYPTION_OFFER, this::handleEncryptionOffer);
        m_execMsgMap.put(EventBytes.TYPE_ENCRYPTION_READY, this::handleEncryptionReady);
        m_execMsgMap.put(EventBytes.TYPE_ERROR, this::handleEncryptionError);
        m_execMsgMap.put(ProtocolMesssages.DEVICE_DISCONNECTED, this::handleDeviceDisconnectedNotification);
    }
    
    public DeviceState getDeviceState(){
        return deviceState;
    }

    public NoteBytesReadOnly getDeviceType(){
        return deviceType;
    }

    public boolean enableMode(NoteBytes mode) {
        return deviceState.enableMode(mode);
    }

    /**
     * Handle stream channels - distinguishes between event and control streams
     */

    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        if (!fromPath.equals(ioDaemonPath)) {
            Log.logError("Unexpected stream from: " + fromPath + 
                            " (expected: " + ioDaemonPath + ")");
            return;
        }
        
        // This is the event stream FROM IODaemon
        this.incomingEventStream = channel;
        this.active = true;
        VirtualExecutors.getVirtualExecutor().execute(() -> {
       
        
            Log.logMsg("Event stream ready: " + devicePath);
            
            try (NoteBytesReader reader = new NoteBytesReader(new PipedInputStream(channel.getChannelStream(),StreamUtils.PIPE_BUFFER_SIZE))) {
                channel.getReadyFuture().complete(null);
                NoteBytesReadOnly nextBytes = reader.nextNoteBytesReadOnly();
                
                while (nextBytes != null && active) {
                    handleIncomingPayload(nextBytes);
                    nextBytes = reader.nextNoteBytesReadOnly();
                }
            } catch (IOException e) {
                Log.logError("Event stream error: " + e.getMessage());
                active = false;
            }
        });
    }
    @FunctionalInterface
    public interface EventCreator{
        RoutedEvent createEvent(NoteBytes noteBytes);
    }

    private EventCreator m_onCreateEvent = null;

    public void setOnCreateEvent(EventCreator onCreateEvent){
        m_onCreateEvent = onCreateEvent;
    }
    
    private EventDispatcher eventDispatcher = null;

    public void setEventDispatcher(EventDispatcher dispatcher){
        this.eventDispatcher = dispatcher;
    }


    /**
     * Handle DEVICE_DISCONNECTED notification from daemon.
     *
     * The physical USB device has gone away.  This is informational: the
     * ClaimedDevice session infrastructure stays alive.  We mark ourselves
     * inactive so that event-stream reads stop cleanly, then invoke the
     * registered disconnect handler so the application layer can decide what
     * to do (wait, retry, give up, show UI, etc.).
     *
     * If no handler is registered we log and leave the instance idle.  The
     * session will stay open; the application can still call releaseDevice()
     * at any time.
     */
    private void handleDeviceDisconnectedNotification(NoteBytesMap msg) {
        Log.logMsg("[ClaimedDevice:" + deviceId + "] Physical USB disconnect received from daemon");

        // Stop the incoming event stream — there will be no more events until
        // the device is reclaimed after a reattach.
        active = false;

        DeviceDisconnectHandler handler = this.onDeviceDisconnected;
        if (handler != null) {
            try {
                handler.onDeviceDisconnected(this);
            } catch (Exception e) {
                Log.logError("[ClaimedDevice:" + deviceId + "] onDeviceDisconnected handler threw", e);
            }
        } else {
            Log.logMsg("[ClaimedDevice:" + deviceId +
                "] No onDeviceDisconnected handler registered; device is idle until released or reclaimed");
        }
    }


    private void createEvent(NoteBytes event)
    {
        RoutedEvent routedEvent = m_onCreateEvent != null
            ? m_onCreateEvent.createEvent(event)
            : eventFactory.from(getContextPath(), event);

        if(eventDispatcher != null){
            eventDispatcher.dispatchEvent(routedEvent);
        }else{
            this.eventHandlerRegistry.dispatch(routedEvent);
        }
    }


    public EventHandlerRegistry getEventHandlerRegistry(){
        return eventHandlerRegistry;
    }
    
    /**
     * Handle incoming payload - control message or event data
     */
    private void handleIncomingPayload(NoteBytesReadOnly payload) {
   
        // Check if this is a control message (encryption negotiation)
        if (payload.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            NoteBytesMap msgMap = payload.getAsNoteBytesMap();
            NoteBytesReadOnly typeBytes = msgMap.getReadOnly(Keys.EVENT);
            
            if (typeBytes != null) {
                // Handle encryption control message
                MessageExecutor executor = m_execMsgMap.get(typeBytes);
                if (executor != null) {
                    executor.execute(msgMap);
                    return;
                }
            }
        }
        
        // Check if payload is encrypted event data
        if (payload.getType() == NoteBytesMetaData.NOTE_BYTES_ENCRYPTED_TYPE) {
            if (encryptionSession != null && encryptionSession.isActive()) {
                try {
                    byte[] decrypted = encryptionSession.decrypt(payload.getBytes());
                    NoteBytesEphemeral decryptedPayload = new NoteBytesEphemeral(decrypted);
                    createEvent(decryptedPayload);
                } catch (Exception e) {
                    Log.logError("Decryption failed: " + e.getMessage());
                }
            } else {
                Log.logError("Received encrypted data but no active encryption session");
            }
        } else {
            // Plaintext event
            createEvent(payload);
        }
        
        // Track for backpressure ACK
        int processed = processedEvents.incrementAndGet();
        if (processed % ACK_BATCH_SIZE == 0) {
            sendAck(ACK_BATCH_SIZE);
        }
    }




    // ===== ENCRYPTION NEGOTIATION =====
    
    private void handleEncryptionOffer(NoteBytesMap offer) {
        Log.logMsg("Encryption offered for device: " + getName());
        
        try {
            byte[] serverPublicKey = offer.get(Keys.PUBLIC_KEY).getBytes();
            String cipher = offer.get(Keys.CIPHER).getAsString();
            
            if (!cipher.equals("aes-256-gcm")) {
                Log.logError("Unsupported cipher: " + cipher);
                declineEncryption("Unsupported cipher algorithm");
                return;
            }
            
            encryptionSession = new DeviceEncryptionSession(getName(), devicePath);
            encryptionSession.acceptOffer(serverPublicKey, cipher);
            
            byte[] clientPublicKey = encryptionSession.getPublicKey();
            NoteBytesObject accept = new NoteBytesObject(new NoteBytesPair[]{
                new NoteBytesPair(Keys.EVENT, EventBytes.TYPE_ENCRYPTION_ACCEPT),
                new NoteBytesPair(Keys.PUBLIC_KEY, new NoteBytes(clientPublicKey))
            });
            sendDeviceControlMessage(accept);
            Log.logMsg("Encryption accept sent");
            
        } catch (Exception e) {
            Log.logError("Encryption offer failed: " + e.getMessage());
            declineEncryption("Key exchange failed: " + e.getMessage());
        }
    }
    
    private void handleEncryptionReady(NoteBytesMap ready) {
        Log.logMsg("Encryption ready for device: " + getName());
        
        try {
            byte[] serverIV = ready.get(Keys.AES_IV).getBytes();
            encryptionSession.finalizeEncryption(serverIV);
            
            Log.logMsg("Device encryption active: " + getName());
            
        } catch (Exception e) {
            Log.logError("Encryption finalization failed: " + e.getMessage());
            encryptionSession = null;
        }
    }
    
    private void handleEncryptionError(NoteBytesMap error) {
        String reason = ProtocolObjects.getErrMsg(error);
       
        Log.logError("Encryption error for device " + getName() + ": " + reason);
        encryptionSession = null;
    }
    
    private void declineEncryption(String reason) {
        NoteBytesObject decline = new NoteBytesObject();
        decline.add(Keys.EVENT, EventBytes.TYPE_ENCRYPTION_DECLINE);
        decline.add(Keys.MSG, reason);
        
        sendDeviceControlMessage(decline);
    }
    
        
    /**
     * Send control message via control stream
     */
    private void sendDeviceControlMessage(NoteBytesObject message) {
        SerializedVirtualExecutor controlWriteExec =outgoingControlStream  != null ? outgoingControlStream.getWriteExec() : null;

        if(controlWriteExec != null && controlWriteExec.isShutdown()){
            return;
        }
        
        controlWriteExec.executeFireAndForget(()->{
            NoteBytesWriter controlStreamWriter = outgoingControlStream.getWriter();
            if (controlStreamWriter == null) {
                
                Log.logMsg("[ClaiemdDevice] Control stream waiting: " + deviceId);
                try{
                    controlStreamWriter = outgoingControlStream.getReadyWriter()
                        .orTimeout(2, TimeUnit.SECONDS)
                        .join();
                }catch(Exception e){
                    Log.logError("[ClaiemdDevice] Control stream timed out: " + deviceId, e);
                }
            
            }else{
                Log.logError("[ClaiemdDevice] Control stream unavailable: " + deviceId);
                return;
            }
            try {
                controlStreamWriter.write(deviceId);
                controlStreamWriter.write(message);
                controlStreamWriter.flush();
            } catch (IOException e) {
                Log.logError("Failed to send control message: " + e.getMessage());
            }
        });
    }

 
    
    // ===== BACKPRESSURE ACK =====

    
    private byte[] getAckBytes(int count){
        int offset = ackBytes.length - 4;
        ackBytes[offset]     = (byte) (count >> 24);
        ackBytes[offset + 1] = (byte) (count >> 16);
        ackBytes[offset + 2] = (byte) (count >> 8);
        ackBytes[offset + 3] = (byte) (count);
        return ackBytes;
    }
    
    private void sendAck(int count) {
        SerializedVirtualExecutor controlWriteExec =outgoingControlStream  != null ? outgoingControlStream.getWriteExec() : null;

        if(controlWriteExec != null && controlWriteExec.isShutdown()){
            return;
        }
        
        controlWriteExec.executeFireAndForget(()->{
            NoteBytesWriter controlStreamWriter = outgoingControlStream.getWriter();
            if (controlStreamWriter == null) {
                
                Log.logMsg("[ClaiemdDevice] Control stream waiting: " + deviceId);
                try{
                    controlStreamWriter = outgoingControlStream.getReadyWriter()
                        .orTimeout(2, TimeUnit.SECONDS)
                        .join();
                }catch(Exception e){
                    Log.logError("[ClaiemdDevice] Control stream timed out: " + deviceId, e);
                }
            
            }

            try {
                controlStreamWriter.write(deviceId);
                controlStreamWriter.write(getAckBytes(count));
                controlStreamWriter.flush();
            } catch (IOException e) {
                Log.logError("Failed to send control message: " + e.getMessage());
            }
        });
    }


    /**
     * Release device called to cleanup device
     */
    public void release() {
        active = false;
        StreamUtils.safeClose(incomingEventStream);
        // Shutdown async writer (drains queue)
        if (outgoingControlStream != null) {
            outgoingControlStream.shutdown();
            outgoingControlStream = null;
        }
        
        // Clear encryption
        if (encryptionSession != null) {
            encryptionSession.clear();
            encryptionSession = null;
        }
        
        
        // Release device state
        if (deviceState != null) {
            deviceState.release();
            deviceState = null;
        }
        
    }
    
    // ===== GETTERS =====
    
    public NoteBytes getDeviceId() {
        return deviceId;
    }
    
    public ContextPath getDevicePath() {
        return devicePath;
    }
    
    public ContextPath getIODaemonPath() {
        return ioDaemonPath;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public boolean hasEncryption() {
        return encryptionSession != null && encryptionSession.isActive();
    }
}
