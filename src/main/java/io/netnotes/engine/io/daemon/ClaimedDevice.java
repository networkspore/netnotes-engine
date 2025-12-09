package io.netnotes.engine.io.daemon;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.MessageExecutor;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.capabilities.DeviceCapabilitySet;
import io.netnotes.engine.io.daemon.DaemonProtocolState.DeviceState;
import io.netnotes.engine.io.daemon.IODaemonProtocol.AsyncNoteBytesWriter;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.events.EventBytes;
import io.netnotes.engine.io.input.events.InputEventFactory;
import io.netnotes.engine.io.input.events.RoutedEvent;
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
    
    private final String deviceId;
    private final ContextPath devicePath;
    private final String deviceType;
    private final ContextPath ioDaemonPath;
    private DeviceState deviceState;
  
    private Consumer<RoutedEvent> m_onRoutedEvent = null;

    // TWO channels: incoming events + outgoing control
    private StreamChannel incomingEventStream;
    private StreamChannel outgoingControlStream;
    private AsyncNoteBytesWriter controlStreamWriter;
    
    private DeviceEncryptionSession encryptionSession;
    private final Map<NoteBytesReadOnly, MessageExecutor> m_execMsgMap = new ConcurrentHashMap<>();
    
    // Backpressure tracking
    private final AtomicInteger processedEvents = new AtomicInteger(0);
    private static final int ACK_BATCH_SIZE = 32;
    
    private volatile boolean active = false;
    
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
        String deviceId, 
        ContextPath devicePath, 
        String deviceType, 
        DeviceCapabilitySet capabilities,
        ContextPath ioDaemonPath
    ) {
        
        super(deviceId, ProcessType.BIDIRECTIONAL);

        this.deviceId = deviceId;
        this.devicePath = devicePath;
        this.deviceType = deviceType;
        this.ioDaemonPath = ioDaemonPath;
        
        this.deviceState = new DeviceState(
            deviceId, 
            (int) ProcessHandle.current().pid(), 
            deviceType, 
            capabilities
        );

        setupMessageMapping();
    }
    
    @Override
    public void onStart() {
        System.out.println("ClaimedDevice starting: " + devicePath);
        
        // Request control stream TO IODaemon (for sending control messages)
        requestStreamChannel(ioDaemonPath)
            .thenAccept(channel -> {
                this.outgoingControlStream = channel;
                this.controlStreamWriter = new AsyncNoteBytesWriter(
                    channel.getOutputStream()
                );
                
                System.out.println("Control stream ready: " + devicePath + " → " + ioDaemonPath);
            })
            .exceptionally(ex -> {
                System.err.println("Failed to setup control stream: " + ex.getMessage());
                return null;
            });
    }

    private void setupMessageMapping() {
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
     * Handle stream channels - distinguishes between event and control streams
     */

        @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        if (!fromPath.equals(ioDaemonPath)) {
            System.err.println("Unexpected stream from: " + fromPath + 
                            " (expected: " + ioDaemonPath + ")");
            return;
        }
        
        // This is the event stream FROM IODaemon
        this.incomingEventStream = channel;
        this.active = true;
        
        channel.getReadyFuture().complete(null);
        
        channel.startReceiving(input -> {
            System.out.println("Event stream ready: " + devicePath);
            
            try (NoteBytesReader reader = new NoteBytesReader(input)) {
                NoteBytesReadOnly nextBytes = reader.nextNoteBytesReadOnly();
                
                while (nextBytes != null && active) {
                    // Read routed events: [STRING:deviceId][OBJECT/ENCRYPTED:event]
                    if (nextBytes.getType() == NoteBytesMetaData.STRING_TYPE) {
                        if (!nextBytes.equalsString(deviceId)) {
                            System.err.println("DeviceId mismatch: expected " + 
                                deviceId + ", got " + nextBytes);
                            break;
                        }
                        
                        NoteBytesReadOnly payload = reader.nextNoteBytesReadOnly();
                        if (payload == null) break;
                        
                        handleIncomingPayload(payload);
                    }
                    
                    nextBytes = reader.nextNoteBytesReadOnly();
                }
                
            } catch (IOException e) {
                System.err.println("Event stream error: " + e.getMessage());
                active = false;
            }
        });
    }
    
    
    /**
     * Handle incoming payload - control message or event data
     */
    private void handleIncomingPayload(NoteBytesReadOnly payload) {
        // Check if this is a control message (encryption negotiation)
        if (payload.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            NoteBytesMap msgMap = payload.getAsNoteBytesMap();
            NoteBytesReadOnly typeBytes = msgMap.getReadOnly(Keys.TYPE);
            
            if (typeBytes != null) {
                // Check if it's an encryption control message
                if (typeBytes.equals(EventBytes.TYPE_ENCRYPTION_OFFER) ||
                    typeBytes.equals(EventBytes.TYPE_ENCRYPTION_READY) ||
                    typeBytes.equals(EventBytes.TYPE_ERROR)) {
                    
                    // Handle encryption control message
                    MessageExecutor executor = m_execMsgMap.get(typeBytes);
                    if (executor != null) {
                        executor.execute(msgMap);
                    }
                    return;
                }
            }
        }
        
        // Check if payload is encrypted event data
        if (payload.getType() == NoteBytesMetaData.NOTE_BYTES_ENCRYPTED_TYPE) {
            if (encryptionSession != null && encryptionSession.isActive()) {
                try {
                    byte[] decrypted = encryptionSession.decrypt(payload.getBytes());
                    NoteBytesReadOnly decryptedPayload = new NoteBytesReadOnly(decrypted);
                    emitEvent(InputEventFactory.from(contextPath, decryptedPayload));
                } catch (Exception e) {
                    System.err.println("Decryption failed: " + e.getMessage());
                }
            } else {
                System.err.println("Received encrypted data but no active encryption session");
            }
        } else {
            // Plaintext event
            emitEvent(InputEventFactory.from(contextPath, payload));
        }
        
        // Track for backpressure ACK
        int processed = processedEvents.incrementAndGet();
        if (processed % ACK_BATCH_SIZE == 0) {
            sendAck(ACK_BATCH_SIZE);
        }
    }




    
    private void emitEvent(RoutedEvent event){
        if(m_onRoutedEvent != null){
            m_onRoutedEvent.accept(event);
        }
    }
    
    public void setEventConsumer(Consumer<RoutedEvent> eventConsumer){
        m_onRoutedEvent = eventConsumer;
    }
    


    public Consumer<RoutedEvent> getEventConsumer(){
        return m_onRoutedEvent;
    }

    // ===== ENCRYPTION NEGOTIATION =====
    
    private void handleEncryptionOffer(NoteBytesMap offer) {
        System.out.println("Encryption offered for device: " + deviceId);
        
        try {
            byte[] serverPublicKey = offer.get(Keys.PUBLIC_KEY).getBytes();
            String cipher = offer.get(Keys.CIPHER).getAsString();
            
            if (!cipher.equals("aes-256-gcm")) {
                System.err.println("Unsupported cipher: " + cipher);
                declineEncryption("Unsupported cipher algorithm");
                return;
            }
            
            encryptionSession = new DeviceEncryptionSession(deviceId, devicePath);
            encryptionSession.acceptOffer(serverPublicKey, cipher);
            
            byte[] clientPublicKey = encryptionSession.getPublicKey();
            NoteBytesObject accept = new NoteBytesObject();
            accept.add(Keys.TYPE, EventBytes.TYPE_ENCRYPTION_ACCEPT);
            accept.add(Keys.SEQUENCE, NoteUUID.getNextUUID64());
            accept.add(Keys.PUBLIC_KEY, clientPublicKey);
            
            sendDeviceControlMessage(accept)
                .thenRun(() -> System.out.println("Encryption accept sent"))
                .exceptionally(ex -> {
                    System.err.println("Failed to send accept: " + ex.getMessage());
                    return null;
                });
            
        } catch (Exception e) {
            System.err.println("Encryption offer failed: " + e.getMessage());
            declineEncryption("Key exchange failed: " + e.getMessage());
        }
    }
    
    private void handleEncryptionReady(NoteBytesMap ready) {
        System.out.println("Encryption ready for device: " + deviceId);
        
        try {
            byte[] serverIV = ready.get(Keys.AES_IV).getBytes();
            encryptionSession.finalizeEncryption(serverIV);
            
            System.out.println("Device encryption active: " + deviceId);
            
        } catch (Exception e) {
            System.err.println("Encryption finalization failed: " + e.getMessage());
            encryptionSession = null;
        }
    }
    
    private void handleEncryptionError(NoteBytesMap error) {
        String reason = error.get(Keys.MSG).getAsString();
        System.err.println("Encryption error for device " + deviceId + ": " + reason);
        encryptionSession = null;
    }
    
    private void declineEncryption(String reason) {
        NoteBytesObject decline = new NoteBytesObject();
        decline.add(Keys.TYPE, EventBytes.TYPE_ENCRYPTION_DECLINE);
        decline.add(Keys.SEQUENCE, NoteUUID.getNextUUID64());
        decline.add(Keys.MSG, reason);
        
        sendDeviceControlMessage(decline);
    }
    
        
    /**
     * Send control message via control stream (already setup in onStart)
     */
     private CompletableFuture<Void> sendDeviceControlMessage(NoteBytesObject message) {
        if (controlStreamWriter == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Control stream not ready")
            );
        }
        
        // Write routed message: [STRING:deviceId][OBJECT:message]
        return controlStreamWriter.writeAsync(new NoteBytes(deviceId))
            .thenCompose(v -> controlStreamWriter.writeAsync(message))
            .exceptionally(ex -> {
                System.err.println("Failed to send control message: " + ex.getMessage());
                return null;
            });
    }
    
    // ===== BACKPRESSURE ACK =====
    
    private CompletableFuture<Void> sendAck(int count) {
        
        return sendDeviceControlMessage(new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.TYPE, EventBytes.TYPE_CMD),
            new NoteBytesPair(Keys.SEQUENCE, NoteUUID.getNextUUID64()),
            new NoteBytesPair(Keys.CMD, ProtocolMesssages.RESUME),
            new NoteBytesPair(Keys.DEVICE_ID, deviceId),
            new NoteBytesPair(Keys.PROCESSED_COUNT, count)
        }));
    }

    private CompletableFuture<Void> sendReleaseNotification() {
        if (controlStreamWriter == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        NoteBytesObject notification = new NoteBytesObject();
        notification.add(Keys.TYPE, EventBytes.TYPE_CMD);
        notification.add(Keys.SEQUENCE, NoteUUID.getNextUUID64());
        notification.add(Keys.CMD, ProtocolMesssages.DEVICE_DISCONNECTED);
        notification.add(Keys.DEVICE_ID, deviceId);
        
        return controlStreamWriter.writeRoutedMessageAsync(deviceId, notification);
    }
    
    /**
     * Release device
     */
    public void release() {
        active = false;
        // Send release notification to IODaemon BEFORE cleanup
        sendReleaseNotification()
            .thenApply((v) -> {
                // Send remaining ACKs
                int remaining = processedEvents.get() % ACK_BATCH_SIZE;
                if (remaining > 0) {
                    return sendAck(remaining);
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenRun(this::cleanup)
            .exceptionally(ex -> {
                System.err.println("Release notification failed, cleaning up anyway");
                cleanup();
                return null;
            });
        
    }

    private void cleanup() {
        
        // Shutdown async writer (drains queue)
        if (controlStreamWriter != null) {
            controlStreamWriter.shutdown();
            try {
                controlStreamWriter.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Clear encryption
        if (encryptionSession != null) {
            encryptionSession.clear();
            encryptionSession = null;
        }
        
        m_onRoutedEvent = null;
        
        // Release device state
        if (deviceState != null) {
            deviceState.release();
            deviceState = null;
        }
        
        // Close streams (writers already shut down)
        if (incomingEventStream != null) {
            try {
                incomingEventStream.close();
            } catch (IOException e) {
                System.err.println("Error closing event stream: " + e.getMessage());
            }
        }
        
        if (outgoingControlStream != null) {
            try {
                outgoingControlStream.close();
            } catch (IOException e) {
                System.err.println("Error closing control stream: " + e.getMessage());
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