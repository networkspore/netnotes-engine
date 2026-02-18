// ========== ClientSession.java (De-FlowProcess-ified) ==========
package io.netnotes.engine.io.daemon;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.state.ConcurrentBitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.io.daemon.DaemonProtocolState.ClientStateFlags;
import io.netnotes.engine.io.input.IEventFactory;
import io.netnotes.engine.io.daemon.DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities;

import io.netnotes.noteBytes.*;
import io.netnotes.noteBytes.collections.NoteBytesMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * ClientSession - Manages devices for one client connection
 * 
 * NOT a FlowProcess - just a coordinator/manager
 * 
 * Communication:
 * - TO IODaemon: via IDaemonCommands interface (serialized on daemon's executor)
 * - FROM IODaemon: via direct method calls (handleDeviceList, shutdown, etc.)
 * 
 * Lifecycle:
 * 1. Created by IODaemon.createSession()
 * 2. Initialized via init()
 * 3. Used by ContainerHandle to claim/release devices
 * 4. Shutdown via shutdown() or emergencyShutdown()
 */
public class ClientSession {
    
    // ===== MODE CONSTANTS =====
    public static class Modes {
        public static final NoteBytesReadOnly RAW           = new NoteBytesReadOnly("raw");
        public static final NoteBytesReadOnly PARSED        = new NoteBytesReadOnly("parsed");
        public static final NoteBytesReadOnly PASSTHROUGH   = new NoteBytesReadOnly("passthrough");
        public static final NoteBytesReadOnly FILTERED      = new NoteBytesReadOnly("filtered");
    }

    // ===== CORE IDENTITY =====
    public final NoteBytesReadOnly sessionId;
    public final int clientPid;
    public final ConcurrentBitFlagStateMachine state;
    
    // ===== PARENT REFERENCES =====
    private final IODaemon daemon;
    private final IODaemonInterface daemonInterface;
    
    // ===== DEVICE MANAGEMENT =====
    // NOTE: discoveredDevices is now shared across all sessions in IODaemon.
    // Sessions read from daemon.getDiscoveredDevices() - no per-session discovery needed.
    
    private volatile CompletableFuture<List<DeviceDescriptorWithCapabilities>> discoveryFuture;
    private final Map<NoteBytes, PendingDevice> pendingClaims = new ConcurrentHashMap<>();
    private final Map<NoteBytes, CompletableFuture<Void>> pendingReleases = new ConcurrentHashMap<>();
    private static final int CLAIM_TIMEOUT_SECONDS = 5;
    private static final int RELEASE_TIMEOUT_SECONDS = 5;

    /**
     * Optional callback invoked when the underlying daemon socket disconnects.
     *
     * If a handler IS registered:
     *   - The session is NOT torn down automatically.
     *   - The handler is responsible for deciding whether to attempt reconnect
     *     (via IODaemon.connect() + rebuild session) or to call shutdown().
     *   - Claimed devices will have already received their own DEVICE_DISCONNECTED
     *     notifications via ClaimedDevice.onDeviceDisconnected.
     *
     * If no handler is registered:
     *   - Default behaviour: the session is shut down immediately (legacy path).
     */
    @FunctionalInterface
    public interface DisconnectHandler {
        void onDisconnect(ClientSession session);
    }

    private volatile DisconnectHandler onDisconnect = null;
    
    /**
     * If true, the session will remain alive after daemon socket disconnection
     * regardless of whether an onDisconnect handler is registered. The application
     * is responsible for explicitly calling shutdown() or attempting reconnection.
     * 
     * This is useful for long-running applications that want to survive transient
     * daemon restarts without losing device state.
     */
    private volatile boolean keepAlive = false;

    public void setOnDisconnect(DisconnectHandler handler) {
        this.onDisconnect = handler;
    }
    
    public void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }
    
    public boolean isKeepAlive() {
        return keepAlive;
    }

    private static class PendingDevice{
        final ClaimedDevice device;
        final CompletableFuture<ClaimedDevice> pendingClaim;

        PendingDevice(ClaimedDevice device){
            this.device = device;
            this.pendingClaim = new CompletableFuture<>();
        }

        void complete(){
            pendingClaim.complete(device);
        }
    }

    // ===== CONSTRUCTOR =====
    
    public ClientSession(
            NoteBytes sessionId,
            int clientPid,
            IODaemon daemon,
            IODaemonInterface daemonCommands
    ) {
        
        this.sessionId = sessionId.readOnly();
        this.clientPid = clientPid;
        this.daemon = daemon;
        this.daemonInterface = daemonCommands;

        this.state = new ConcurrentBitFlagStateMachine("client-" + sessionId);
        
        setupStateTransitions();
    }
    
    /**
     * Initialize session - replaces FlowProcess.onStart()
     */
    public void init() {
        Log.logMsg("[ClientSession:" + sessionId + "] Starting session for PID " + clientPid);
        
        state.addState(ClientStateFlags.CONNECTED);
        state.addState(ClientStateFlags.AUTHENTICATED);
    }
    
    // ===== STATE TRANSITIONS =====
    
    private void setupStateTransitions() {
        state.onStateAdded(ClientStateFlags.AUTHENTICATED, (old, now, bit) -> {
            Log.logMsg("[ClientSession:" + sessionId + "] Authenticated");
        });
        
        state.onStateAdded(ClientStateFlags.DISCONNECTING, (old, now, bit) -> {
            Log.logMsg("[ClientSession:" + sessionId + "] Disconnecting...");
        });
        
        state.onStateAdded(ClientStateFlags.ERROR_STATE, (old, now, bit) -> {
            Log.logError("[ClientSession:" + sessionId + "] Entered ERROR state");
        });
    }
    
    // ===== DISCOVERY =====
    
    /**
     * Get discovered devices from shared registry.
     * 
     * **BREAKING CHANGE**: This now returns immediately with the current state
     * of the shared IODaemon registry. The registry is kept in sync automatically
     * by the daemon push model (DEVICE_ATTACHED/DETACHED/ITEM_LIST notifications).
     * 
     * Applications should:
     * 1. Call this method to get the current device list
     * 2. Register a DeviceRegistryChangeListener on IODaemon for live updates
     * 
     * The old discovery model (request→wait→response) is no longer needed.
     */
    public CompletableFuture<List<DeviceDescriptorWithCapabilities>> discoverDevices() {
        if (!ClientStateFlags.canDiscover(state.getSnapshot())) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot discover in current state: " + 
                    state.getState()));
        }
        
        // Return current registry state immediately - no daemon roundtrip
        List<DeviceDescriptorWithCapabilities> devices = daemon.getDiscoveredDevices().getAllDevices();
        return CompletableFuture.completedFuture(devices);
    }
    
    /**
     * Handle device list from daemon (legacy path for backward compatibility).
     * 
     * This is now handled centrally in IODaemon.broadcastDeviceList().
     * Individual sessions no longer parse device lists themselves.
     * 
     * @deprecated Use IODaemon.getDiscoveredDevices() directly
     */
    @Deprecated
    public void handleDeviceList(NoteBytesMap map) {
        // No-op: registry is updated centrally in IODaemon
        Log.logMsg("[ClientSession:" + sessionId + "] handleDeviceList called (deprecated path)");
    }
    
    // ===== CLAIM =====
    private Map<NoteBytes, ClaimedDevice> getClaimedDevices(){
        return daemon.getClaimedDevices();
    }

     public CompletableFuture<ClaimedDevice> claimDevice(
        NoteBytes deviceId, 
        NoteBytes requestedMode,
        IEventFactory eventFactory
    ) {
        return claimDevice(deviceId, requestedMode, CLAIM_TIMEOUT_SECONDS, eventFactory);
    }
    /**
     * Claim a device with event factory
     */
    public CompletableFuture<ClaimedDevice> claimDevice(
        NoteBytes deviceId, 
        NoteBytes requestedMode,
        int timeoutSeconds,
        IEventFactory eventFactory
    ) {
        // Validation
        if (!ClientStateFlags.canClaim(state.getSnapshot())) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot claim in current state: " + 
                    state.getState()));
        }

        DeviceDescriptorWithCapabilities deviceInfo = daemon.getDiscoveredDevices().getDevice(deviceId);
        if (deviceInfo == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Device not found: " + deviceId));
        }

        if (deviceInfo.claimed()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Device already claimed: " + deviceId));
        }

        if (!daemon.getDiscoveredDevices().validateModeCompatibility(deviceId, requestedMode)) {
            String availableModes = daemon.getDiscoveredDevices().getAvailableModes(deviceId)
                .stream().map(NoteBytes::toString)
                .collect(Collectors.joining(", "));

            return CompletableFuture.failedFuture(
                new IllegalArgumentException(
                    "Device does not support mode: " + requestedMode +
                    ". Available: " + availableModes));
        }
        ClaimedDevice existingDevice = getClaimedDevices().get(deviceId);
        if(existingDevice != null){
            if(existingDevice.getSessionId().equals(sessionId)){
                return CompletableFuture.completedFuture(existingDevice);
            }else{
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Device is claimed by another session"));
            }
        }

        PendingDevice existingClaim = pendingClaims.get(deviceId);
        if(existingClaim != null){
            return existingClaim.pendingClaim;
        }
                
        // Setup ClaimedDevice path
        ContextPath claimedDevicePath = daemon.getContextPath()
            .append(sessionId)
            .append(deviceId);

        // Create ClaimedDevice with injected event factory
        ClaimedDevice claimedDevice = new ClaimedDevice(
            this.sessionId,
            deviceId,
            claimedDevicePath,
            deviceInfo.usbDevice().getDeviceType(),
            deviceInfo.capabilities(),
            daemon.getContextPath(),
            eventFactory
        );

        if (!claimedDevice.enableMode(requestedMode)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Failed to enable mode: " + requestedMode));
        }

        // Create confirmation future
        PendingDevice pendingDevice = new PendingDevice(claimedDevice);
        pendingClaims.put(deviceId, pendingDevice);


        // Request daemon claim (async)
        daemonInterface.claimDevice(sessionId, deviceId, requestedMode)
            .exceptionally(ex -> {
                Log.logError("[ClientSession:" + sessionId + "] Claim request failed: " + 
                    ex.getMessage());
                rollbackClaim(deviceId, ex);
                
                return null;
            });

        // Add timeout
        CompletableFuture<ClaimedDevice> timeoutFuture = pendingDevice.pendingClaim
            .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .exceptionally(ex -> {
                if (ex.getCause() instanceof TimeoutException) {
                    Log.logError("[ClientSession:" + sessionId + "] Claim timeout: " + deviceId);
                    rollbackClaim(deviceId, ex);
                    pendingClaims.remove(deviceId);
                }
                throw new CompletionException(ex);
            });

        return timeoutFuture;
    }
    
    // ===== RELEASE =====
    
    /**
     * Release device - returns future that completes when daemon confirms
     */
    public CompletableFuture<Void> releaseDevice(NoteBytes deviceId) {
        ClaimedDevice claimedDevice = getClaimedDevices().get(deviceId);
        if (claimedDevice == null || !claimedDevice.getSessionId().equals(sessionId)) {
            Log.logMsg("[ClientSession:" + sessionId + "] Device not claimed: " + deviceId);
            return CompletableFuture.completedFuture(null);
        }

        // Create confirmation future
        CompletableFuture<Void> confirmationFuture = new CompletableFuture<>();
        pendingReleases.put(deviceId, confirmationFuture);

        // Start local cleanup
        claimedDevice.release();


        
        // Add timeout - more forgiving since local cleanup is done
        CompletableFuture<Void> timeoutFuture = confirmationFuture
            .orTimeout(RELEASE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .exceptionally(ex -> {
                if (ex.getCause() instanceof TimeoutException) {
                    Log.logError("[ClientSession:" + sessionId + "] Release timeout: " + deviceId);
                }
                // Complete anyway - force cleanup
                completeRelease(deviceId);
                pendingReleases.remove(deviceId);
                return null;
            });
            
        // Request daemon release (async)
        daemonInterface.releaseDevice(sessionId, deviceId)
            .exceptionally(ex -> {
                Log.logError("[ClientSession:" + sessionId + "] Release request failed: " + 
                    ex.getMessage());
                timeoutFuture.completeExceptionally(ex);
                return null;
            });


        return timeoutFuture;
    }

    /**
     *   device claimed confirmation from daemon
     * Called by IODaemon when ITEM_CLAIMED message arrives
     */
    void handleDeviceClaimed(NoteBytes deviceId, NoteBytesMap response) {
        PendingDevice pendingDevice = pendingClaims.remove(deviceId);
        if(pendingDevice == null){
            Log.logMsg("[ClientSession:" + sessionId + 
                "] pending device does not exist: " + deviceId);
            releaseDevice(deviceId);
            return;
        }
        CompletableFuture<ClaimedDevice> future = pendingDevice.pendingClaim;

        if (future.isDone()) {
            Log.logMsg("[ClientSession:" + sessionId + 
                "] Claim future already completed for: " + deviceId);
            return;
        }

        // Check for errors in response
        NoteBytes errorCode = response.get(io.netnotes.engine.messaging.NoteMessaging.Keys.ERROR_CODE);
        if (errorCode != null) {
            NoteBytes msgBytes = response.get(io.netnotes.engine.messaging.NoteMessaging.Keys.MSG);
            String errorMsg = msgBytes != null ? msgBytes.getAsString() : "unknown error";
            Log.logError("[ClientSession:" + sessionId + "] Daemon claim failed: " + errorMsg);
            
            RuntimeException ex = new RuntimeException("Daemon claim failed: " + errorMsg);
            
            rollbackClaim(deviceId, ex);
        } else {
            ClaimedDevice claimedDevice = pendingDevice.device;

            // Local setup
            ContextPath devicePath = claimedDevice.getDevicePath();
            daemon.registerClaimedDevice(claimedDevice, devicePath);
           
            daemon.getDiscoveredDevices().markClaimed(deviceId);
            state.addState(ClientStateFlags.HAS_CLAIMED_DEVICES);
            
            daemon.startProcess(devicePath)
                .thenCompose((v)->daemon.addClaimedDevice(claimedDevice))
                .whenComplete((v,ex)->{
                    if(ex != null){
                        Log.logError("[ClientSession]", "error creating stream", ex);
                        
                    }else{
                        pendingDevice.complete();
                    }
                });
        }
    }

    /**
     * Handle device released confirmation from daemon
     * Called by IODaemon when ITEM_RELEASED message arrives
     */
    public void handleDeviceReleased(NoteBytes deviceId) {
        CompletableFuture<Void> future = pendingReleases.remove(deviceId);
        
        completeRelease(deviceId);
        
        if (future != null && !future.isDone()) {
            future.complete(null);
            Log.logMsg("[ClientSession:" + sessionId + "] Device release confirmed: " + deviceId);
        } else if (future == null) {
            Log.logMsg("[ClientSession:" + sessionId + 
                "] Received release confirmation for unknown device: " + deviceId);
        }
    }

    /**
     * Rollback claim operation - cleanup after failure
     */
    private void rollbackClaim(NoteBytes deviceId, Throwable ex) {


        PendingDevice pendingDevice = pendingClaims.remove(deviceId);
        CompletableFuture<ClaimedDevice> future = pendingDevice.pendingClaim;
         if (future != null && !future.isDone()) {
            future.completeExceptionally(ex);
        }

        if (getClaimedDevices().isEmpty()) {
            state.removeState(ClientStateFlags.HAS_CLAIMED_DEVICES);
        }

    }

    /**
     * Complete release operation - final cleanup
     */
    private void completeRelease(NoteBytes deviceId) {
       
        daemon.completeDeviceRelease(deviceId);

        daemon.getDiscoveredDevices().markReleased(deviceId);
        
        if (claimedAnyDevices()) {
            state.removeState(ClientStateFlags.HAS_CLAIMED_DEVICES);
        }
    }

    public boolean claimedAnyDevices(){
        for(ClaimedDevice device : getClaimedDevices().values()){
            if(device.getSessionId().equals(sessionId)){
                return true;
            }
        }
        return false;
    }
    
    /**
     * Release all devices - called during shutdown
     */
    private void releaseAllDevices() {
        if (!claimedAnyDevices()) {
            return;
        }
        
        Log.logMsg("[ClientSession:" + sessionId + "] Releasing " + 
            getClaimedDevices().size() + " devices");
        
        for (ClaimedDevice device : getClaimedDevices().values()) {
            
            NoteBytes sessionId = device.getSessionId();

            if(!sessionId.equals(this.sessionId)){
                continue;
            }
                
            NoteBytes deviceId = device.getDeviceId();
            try {   
                // Notify device to release
                device.release();
                
                // Unregister from registry
                daemon.completeDeviceRelease(deviceId);
                
                // Update discovery state
                daemon.getDiscoveredDevices().markReleased(deviceId);
                
                Log.logMsg("[ClientSession:" + sessionId + "] Released: " + deviceId);
                
            } catch (Exception e) {
                Log.logError("[ClientSession:" + sessionId + "] Error releasing device " + 
                    device.getDeviceId() + ": " + e.getMessage());
            }
        }
        
        getClaimedDevices().clear();
        state.removeState(ClientStateFlags.HAS_CLAIMED_DEVICES);
    }
    
    // ===== LIFECYCLE =====
    
    /**
     * Graceful shutdown - replaces FlowProcess.onStop()
     * Releases devices and notifies daemon
     */
    public CompletableFuture<Void> shutdown() {
        Log.logMsg("[ClientSession:" + sessionId + "] Shutdown requested");
        
        state.addState(ClientStateFlags.DISCONNECTING);
        CompletableFuture<List<DeviceDescriptorWithCapabilities>> pendingDiscovery = discoveryFuture;
        discoveryFuture = null;
        if (pendingDiscovery != null && !pendingDiscovery.isDone()) {
            pendingDiscovery.completeExceptionally(
                new IllegalStateException("Session shutting down"));
        }
        
        if (getClaimedDevices().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Iterator<Entry<NoteBytes, ClaimedDevice>> it = getClaimedDevices().entrySet().iterator();
        ArrayList<CompletableFuture<Void>> releaseFutures = new ArrayList<>();
        while(it.hasNext()){
            Entry<NoteBytes, ClaimedDevice> entry = it.next();
            if(entry.getValue().getSessionId().equals(sessionId)){
                NoteBytes deviceId = entry.getKey();
                releaseFutures
                    .add(releaseDevice(deviceId)
                        .exceptionally(ex -> {
                            Log.logError("[ClientSession:" + sessionId + "] Error releasing " + 
                                deviceId + ": " + ex.getMessage());
                            return null;
                        }));
                it.remove();
            }
        }
        

        return CompletableFuture.allOf(releaseFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                // Do NOT clear the shared registry - it belongs to IODaemon
                state.removeState(ClientStateFlags.DISCOVERING);
                state.removeState(ClientStateFlags.AUTHENTICATED);
                state.removeState(ClientStateFlags.CONNECTED);
                
                Log.logMsg("[ClientSession:" + sessionId + "] Session stopped");
            });
    }
    
    /**
     * Emergency shutdown - when daemon socket is already disconnected
     */
    public void emergencyShutdown() {
        Log.logMsg("[ClientSession:" + sessionId + "] Emergency shutdown");
        
        state.addState(ClientStateFlags.DISCONNECTING);
        state.addState(ClientStateFlags.ERROR_STATE);
   
        
        // Cancel all pending operations
        for (PendingDevice device : pendingClaims.values()) {
            CompletableFuture<ClaimedDevice> future = device.pendingClaim;
            if (!future.isDone()) {
                future.completeExceptionally(
                    new IllegalStateException("Session emergency shutdown"));
            }
        }
        pendingClaims.clear();

        for (CompletableFuture<Void> future : pendingReleases.values()) {
            if (!future.isDone()) {
                future.completeExceptionally(
                    new IllegalStateException("Session emergency shutdown"));
            }
        }
        pendingReleases.clear();
        
        // Force release without waiting for daemon responses
        releaseAllDevices();
        
        disconnected();
    }

    /**
     * Called by IODaemon when the daemon socket has dropped.
     *
     * Behaviour depends on configuration:
     * 
     * 1. If {@link #keepAlive} is true: session is ALWAYS kept alive, handler
     *    is invoked if present, but session is never torn down automatically.
     * 
     * 2. If a {@link DisconnectHandler} is registered (and keepAlive is false):
     *    session is kept alive and the handler is invoked — the application may
     *    choose to reconnect while keeping claimed-device state intact.
     *
     * 3. If neither keepAlive nor a handler is set: immediate emergency shutdown
     *    (legacy / default behaviour).
     */
    void disconnected() {
        // Always fail any pending discovery — the socket is gone.
        CompletableFuture<List<DeviceDescriptorWithCapabilities>> pendingDiscovery = discoveryFuture;
        discoveryFuture = null;
        if (pendingDiscovery != null && !pendingDiscovery.isDone()) {
            pendingDiscovery.completeExceptionally(
                new IllegalStateException("Daemon socket disconnected"));
        }

        DisconnectHandler handler = this.onDisconnect;
        boolean shouldKeepAlive = this.keepAlive || (handler != null);
        
        if (shouldKeepAlive) {
            // Application has opted in to keeping session alive during disconnection
            Log.logMsg("[ClientSession:" + sessionId +
                "] Socket disconnected; " + 
                (keepAlive ? "keepAlive=true" : "onDisconnect handler registered") +
                ", session kept alive");
            state.removeState(ClientStateFlags.CONNECTED);
            
            if (handler != null) {
                try {
                    handler.onDisconnect(this);
                } catch (Exception e) {
                    Log.logError("[ClientSession:" + sessionId + "] onDisconnect handler threw", e);
                }
            }
        } else {
            // No handler and keepAlive=false — default path: tear down immediately.
            Log.logMsg("[ClientSession:" + sessionId +
                "] Socket disconnected; no onDisconnect handler and keepAlive=false, performing emergency shutdown");
            // Note: do NOT clear the shared discoveredDevices registry - it belongs to IODaemon
            emergencyShutdown();
        }
    }

    // ===== DISCOVERED DEVICE HELPERS =====
    
    /**
     * Get device info from discovered registry
     */
    public DeviceDescriptorWithCapabilities getDeviceInfo(NoteBytes deviceId) {
        return daemon.getDiscoveredDevices().getDevice(deviceId);
    }
    
    /**
     * Get all discovered devices
     */
    public List<DeviceDescriptorWithCapabilities> getAllDiscoveredDevices() {
        return daemon.getDiscoveredDevices().getAllDevices();
    }
    
    /**
     * Get unclaimed devices
     */
    public List<DeviceDescriptorWithCapabilities> getUnclaimedDevices() {
        return daemon.getDiscoveredDevices().getUnclaimedDevices();
    }
    
    /**
     * Get available modes for a device
     */
    public Set<NoteBytes> getAvailableModesForDevice(NoteBytes deviceId) {
        return daemon.getDiscoveredDevices().getAvailableModes(deviceId);
    }
    
    /**
     * Validate mode before claiming
     */
    public boolean canClaimWithMode(NoteBytes deviceId, NoteBytes mode) {
        return daemon.getDiscoveredDevices().validateModeCompatibility(deviceId, mode);
    }
    
    /**
     * Log discovered devices for debugging
     */
    public void logDiscoveredDevices() {
        daemon.getDiscoveredDevices().printDevices();
    }
    
    // ===== GETTERS =====
    
    public ClaimedDevice getClaimedDevice(NoteBytes deviceId) {
        ClaimedDevice device = getClaimedDevices().get(deviceId);
        if(device != null){
            if(device.getSessionId().equals(sessionId)){
                return device;
            }else{
                throw new IllegalAccessError("Device does not belong to this session");
            }
        }
        return null;
    }
    
    public DiscoveredDeviceRegistry getDiscoveredDevices() {
        return daemon.getDiscoveredDevices();
    }
    
    /**
     * Get count of claimed devices in this session
     */
    public int getClaimedDeviceCount() {
        return getClaimedDevices().size();
    }

    /**
     * Get all claimed devices
     //TODO copy fields give nutered list
    public List<ClaimedDevice> getClaimedDevices() {
        return List.copyOf(claimedDevices.values());
    }*/

    /**
     * Check if device is claimed by this session
     */
    public boolean hasDevice(NoteBytes deviceId) {
        ClaimedDevice device = getClaimedDevices().get(deviceId);
        return (device != null && device.getSessionId().equals(sessionId));
    }
    
    /**
     * Check if session is in a healthy state
     */
    public boolean isHealthy() {
        return state.hasState(ClientStateFlags.CONNECTED) &&
               state.hasState(ClientStateFlags.AUTHENTICATED) &&
               !state.hasState(ClientStateFlags.ERROR_STATE);
    }
    
    @Override
    public String toString() {
        return String.format(
            "ClientSession{id=%s, pid=%d, devices=%d, state=%d, healthy=%s}",
            sessionId, clientPid, getClaimedDeviceCount(), 
            state.getState().longValue(), isHealthy()
        );
    }
}