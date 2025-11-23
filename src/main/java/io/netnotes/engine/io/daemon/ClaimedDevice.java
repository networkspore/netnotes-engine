package io.netnotes.engine.io.daemon;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteInteger;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.capabilities.DeviceCapabilitySet;
import io.netnotes.engine.io.daemon.DaemonProtocolState.DeviceState;
import io.netnotes.engine.io.events.EventBytes;
import io.netnotes.engine.io.events.ExecutorConsumer;
import io.netnotes.engine.io.events.InputEventFactory;
import io.netnotes.engine.io.events.RoutedEvent;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;

public class ClaimedDevice extends FlowProcess implements InputDevice {
    
    private final String deviceId;
    private final ContextPath devicePath;
    private final String deviceType;
    private ContextPath ioDaemonPath;
    private DeviceState deviceState;
  

    private Map<String, ExecutorConsumer<RoutedEvent>> m_consumerMap = new ConcurrentHashMap<>();

    private StreamChannel streamChannel;
    private DeviceEncryptionSession encryptionSession;
    private final Map<NoteBytesReadOnly, MessageExecutor> m_execMsgMap = new ConcurrentHashMap<>();
    
    // Backpressure tracking
    private final AtomicInteger processedEvents = new AtomicInteger(0);
    private static final int ACK_BATCH_SIZE = 32; // ACK every 32 events
    
    private volatile boolean active = false;
    
    public ClaimedDevice(String deviceId, ContextPath devicePath, String deviceType, DeviceCapabilitySet capabilities) {
        super(ProcessType.BIDIRECTIONAL);

        this.deviceId = deviceId;
        this.devicePath = devicePath;
        this.deviceType = deviceType;
        
        this.deviceState = new DeviceState(deviceId, (int) ProcessHandle.current().pid(), deviceType, capabilities);

        setupMessageMapping();
    }
    
     private void setupMessageMapping() {
        
        // Command subtypes
        m_execMsgMap.put(EventBytes.TYPE_ENCRYPTION_OFFER, this::handleEncryptionOffer);
        m_execMsgMap.put(EventBytes.TYPE_ENCRYPTION_READY, this::handleEncryptionReady);
        m_execMsgMap.put(EventBytes.TYPE_ERROR, this::handleEncryptionError);

    }
    
    public DeviceState getDeviceState(){
        return deviceState;
    }

    public String getDeviceType(){
        return deviceType;
    }

    public boolean enableMode(String mode) {
        return deviceState.enableMode(mode);
    }

    /**
     * Start receiving device stream
     */
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath path) {
        this.streamChannel = channel;
        this.active = true;
        this.ioDaemonPath = path;
        channel.getReadyFuture().complete(null);
        channel.startReceiving(input -> {
            System.out.println("Device stream ready: " + devicePath);
            
            try (NoteBytesReader reader = new NoteBytesReader(input)) {
                NoteBytesReadOnly nextBytes = reader.nextNoteBytesReadOnly();
                
                while (nextBytes != null && active) {
                    // Check if this is sourceId prefix
                    if (nextBytes.getType() == NoteBytesMetaData.STRING_TYPE) {
                       
                        
                        if (!nextBytes.equalsString(deviceId)) {
                            System.err.println("SourceId mismatch: expected " + 
                                deviceId + ", got " + nextBytes);
                            break;
                        }
                        
                        // Read payload
                        NoteBytesReadOnly payload = reader.nextNoteBytesReadOnly();
                        if (payload == null) break;
                        
                        emitEvent(InputEventFactory.from(contextPath, payload));
                        
                        // Track for backpressure ACK
                        int processed = processedEvents.incrementAndGet();
                        if (processed % ACK_BATCH_SIZE == 0) {
                            sendAck(ACK_BATCH_SIZE);
                        }
                    }
                    
                    nextBytes = reader.nextNoteBytesReadOnly();
                }
                
            } catch (IOException e) {
                System.err.println("Device stream error: " + e.getMessage());
                active = false;
            }
        });
    }
    
    
   

    private void emitEvent(RoutedEvent event){
        m_consumerMap.forEach((k,consumer)->consumer.accept(event));
    }
    
    public void addEventConsumer(String id, ExecutorConsumer<RoutedEvent> eventConsumer){
        m_consumerMap.computeIfAbsent(id, (k)->eventConsumer);
    }
    
    public ExecutorConsumer<RoutedEvent> removeEventConsumer(String id){
        return m_consumerMap.remove(id);
    }

    public ExecutorConsumer<RoutedEvent> getEventConsumer(String id){
        return m_consumerMap.get(id);
    }

    
    // ===== ENCRYPTION NEGOTIATION =====
    
    private void handleEncryptionOffer(NoteBytesMap offer) {
        System.out.println("Encryption offered for device: " + deviceId);
        
        try {
            // Extract server public key
            byte[] serverPublicKey = offer.get("public_key").getBytes();
            String cipher = offer.get("cipher").getAsString();
            
            // Create encryption session
            encryptionSession = new DeviceEncryptionSession(deviceId, devicePath);
            encryptionSession.acceptOffer(serverPublicKey, cipher);
            
            // Send ACCEPT with our public key
            byte[] clientPublicKey = encryptionSession.getPublicKey();
            NoteBytesObject accept = new NoteBytesObject();
            accept.add(Keys.TYPE, EventBytes.TYPE_ENCRYPTION_ACCEPT);
            accept.add(Keys.SEQUENCE, IODaemonProtocol.MessageBuilder.generateSequence());
            accept.add("public_key", clientPublicKey);
            
            sendDeviceControlMessage(accept);
            
        } catch (Exception e) {
            System.err.println("Encryption offer failed: " + e.getMessage());
            declineEncryption("Key exchange failed");
        }
    }
    
    private void handleEncryptionReady(NoteBytesMap ready) {
        System.out.println("Encryption ready for device: " + deviceId);
        
        try {
            byte[] serverIV = ready.get("iv").getBytes();
            encryptionSession.finalizeEncryption(serverIV);
            
            System.out.println("Device encryption active: " + deviceId);
            
        } catch (Exception e) {
            System.err.println("Encryption finalization failed: " + e.getMessage());
            encryptionSession = null;
        }
    }
    
    private void handleEncryptionError(NoteBytesMap error) {
        String reason = error.get("reason").getAsString();
        System.err.println("Encryption error for device " + deviceId + ": " + reason);
        encryptionSession = null;
    }
    
    private void declineEncryption(String reason) {
        NoteBytesObject decline = new NoteBytesObject();
        decline.add(Keys.TYPE, EventBytes.TYPE_ENCRYPTION_DECLINE);
        decline.add(Keys.SEQUENCE, IODaemonProtocol.MessageBuilder.generateSequence());
        decline.add("reason", reason);
        
        sendDeviceControlMessage(decline);
    }
    
    /**
     * Send control message to device (with sourceId prefix)
     */
    private void sendDeviceControlMessage(NoteBytesObject message) {
        try {
            // For replies, we need to send back through IODaemon
            // Use FlowProcess request-reply pattern
            request(devicePath, message, Duration.ofSeconds(5))
                .exceptionally(ex -> {
                    System.err.println("Failed to send device control: " + ex.getMessage());
                    return null;
                });
            
        } catch (Exception e) {
            System.err.println("Error sending device control: " + e.getMessage());
        }
    }
    
    // ===== BACKPRESSURE ACK =====
    
    private void sendAck(int count) {
        try {

            request(ioDaemonPath, Duration.ofSeconds(1), 
                new NoteBytesPair("action", ProtocolMesssages.RESUME),
                new NoteBytesPair(Keys.DEVICE_ID, deviceId),
                new NoteBytesPair("processed_count", new NoteInteger(count))
            )
                .exceptionally(ex -> {
                    System.err.println("Failed to send ACK: " + ex.getMessage());
                    return null;
                });
            
        } catch (Exception e) {
            System.err.println("Error sending ACK: " + e.getMessage());
        }
    }
    
    /**
     * Release device
     */
    public void release() {
        active = false;
        
        // Send any remaining ACKs
        int remaining = processedEvents.get() % ACK_BATCH_SIZE;
        if (remaining > 0) {
            sendAck(remaining);
        }
        
        // Clear encryption
        if (encryptionSession != null) {
            encryptionSession.clear();
            encryptionSession = null;
        }

        m_consumerMap.clear();
        
        // Release device state
        if (deviceState != null) {
            deviceState.release();
            deviceState = null;
        }
        
        // Close stream
        if (streamChannel != null) {
            try {
                streamChannel.close();
            } catch (IOException e) {
                System.err.println("Error closing stream: " + e.getMessage());
            }
        }
    }
    
    // ===== GETTERS =====
    

    public String getDeviceId() {
        return deviceId;
    }
    
    public ContextPath getDevicePath() {
        return devicePath;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public boolean hasEncryption() {
        return encryptionSession != null && encryptionSession.isActive();
    }
}
