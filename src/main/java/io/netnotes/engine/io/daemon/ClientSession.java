// ========== ClientSession.java (De-FlowProcess-ified) ==========
package io.netnotes.engine.io.daemon;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.io.daemon.DaemonProtocolState.ClientStateFlags;
import io.netnotes.engine.io.input.IEventFactory;
import io.netnotes.engine.io.daemon.DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities;

import io.netnotes.noteBytes.*;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

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
    public final String sessionId;
    public final int clientPid;
    public final BitFlagStateMachine state;
    
    // ===== PARENT REFERENCES =====
    private final IODaemon daemon;
    private final IDaemonCommands daemonCommands;
    private final SerializedVirtualExecutor daemonExecutor;
    
    // ===== DEVICE MANAGEMENT =====
    private final DiscoveredDeviceRegistry discoveredDevices = new DiscoveredDeviceRegistry();
    private final Map<String, ClaimedDevice> claimedDevices = new ConcurrentHashMap<>();
    private volatile CompletableFuture<List<DeviceDescriptorWithCapabilities>> discoveryFuture;
    private final Map<String, CompletableFuture<ClaimedDevice>> pendingClaims = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> pendingReleases = new ConcurrentHashMap<>();
    private static final int CLAIM_TIMEOUT_SECONDS = 5;
    private static final int RELEASE_TIMEOUT_SECONDS = 5;

    // ===== CONSTRUCTOR =====
    
    public ClientSession(
            String sessionId,
            int clientPid,
            IODaemon daemon,
            IDaemonCommands daemonCommands,
            SerializedVirtualExecutor daemonExecutor) {
        
        this.sessionId = sessionId;
        this.clientPid = clientPid;
        this.daemon = daemon;
        this.daemonCommands = daemonCommands;
        this.daemonExecutor = daemonExecutor;
        this.state = new BitFlagStateMachine("client-" + sessionId);
        
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
     * Request device discovery from daemon
     */
    public CompletableFuture<List<DeviceDescriptorWithCapabilities>> discoverDevices() {
        if (!ClientStateFlags.canDiscover(state)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot discover in current state: " + 
                    state.getState()));
        }

        CompletableFuture<List<DeviceDescriptorWithCapabilities>> existing = discoveryFuture;
        if (existing != null && !existing.isDone()) {
            return existing;
        }
        
        state.addState(ClientStateFlags.DISCOVERING);
        CompletableFuture<List<DeviceDescriptorWithCapabilities>> future = new CompletableFuture<>();
        discoveryFuture = future;
        
        // Send discovery request to daemon via interface
        return daemonExecutor.execute(() -> daemonCommands.requestDiscovery(sessionId))
            .thenCompose(v -> future)
            .exceptionally(ex -> {
                state.removeState(ClientStateFlags.DISCOVERING);
                if (!future.isDone()) {
                    future.completeExceptionally(ex);
                }
                discoveryFuture = null;
                Log.logError("[ClientSession:" + sessionId + "] Discovery failed: " + 
                    ex.getMessage());
                throw new RuntimeException("Discovery failed", ex);
            });
    }
    
    /**
     * Handle device list from daemon
     * Called directly by IODaemon when device list arrives
     */
    public void handleDeviceList(NoteBytesMap map) {
        discoveredDevices.parseDeviceList(map);
        state.removeState(ClientStateFlags.DISCOVERING);
        CompletableFuture<List<DeviceDescriptorWithCapabilities>> future = discoveryFuture;
        discoveryFuture = null;
        if (future != null && !future.isDone()) {
            future.complete(discoveredDevices.getAllDevices());
        }
        
        int deviceCount = discoveredDevices.getAllDevices().size();
        Log.logMsg("[ClientSession:" + sessionId + "] Discovery complete: " + 
            deviceCount + " devices found");
    }
    
    // ===== CLAIM =====
    
    /**
     * Claim a device with event factory
     */
    public CompletableFuture<ClaimedDevice> claimDevice(
        String deviceId, 
        String requestedMode,
        IEventFactory eventFactory
    ) {
        // Validation
        if (!ClientStateFlags.canClaim(state)) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot claim in current state: " + 
                    state.getState()));
        }

        var deviceInfo = discoveredDevices.getDevice(deviceId);
        if (deviceInfo == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Device not found: " + deviceId));
        }

        if (deviceInfo.claimed()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Device already claimed: " + deviceId));
        }

        if (!discoveredDevices.validateModeCompatibility(deviceId, requestedMode)) {
            String availableModes = String.join(", ",
                discoveredDevices.getAvailableModes(deviceId));
            return CompletableFuture.failedFuture(
                new IllegalArgumentException(
                    "Device does not support mode: " + requestedMode +
                    ". Available: " + availableModes));
        }

        // Create confirmation future
        CompletableFuture<ClaimedDevice> confirmationFuture = new CompletableFuture<>();
        pendingClaims.put(deviceId, confirmationFuture);

        // Setup ClaimedDevice path
        ContextPath claimedDevicePath = daemon.getContextPath()
            .append(sessionId)
            .append(deviceId);

        // Create ClaimedDevice with injected event factory
        ClaimedDevice claimedDevice = new ClaimedDevice(
            deviceId,
            claimedDevicePath,
            deviceInfo.usbDevice().getDeviceType(),
            deviceInfo.capabilities(),
            daemon.getContextPath(),
            eventFactory
        );

        if (!claimedDevice.enableMode(requestedMode)) {
            pendingClaims.remove(deviceId);
            return CompletableFuture.failedFuture(
                new IllegalStateException("Failed to enable mode: " + requestedMode));
        }

        claimedDevice.setOnDisconnect(daemon::handleDeviceDisconnect);

        // Local setup
        try {
            daemon.registerClaimedDevice(claimedDevice, claimedDevicePath);
            claimedDevices.put(deviceId, claimedDevice);
            discoveredDevices.markClaimed(deviceId);
            state.addState(ClientStateFlags.HAS_CLAIMED_DEVICES);
        } catch (Exception e) {
            pendingClaims.remove(deviceId);
            return CompletableFuture.failedFuture(e);
        }

        // Request daemon claim (async)
        daemonExecutor.execute(() -> 
            daemonCommands.claimDevice(sessionId, deviceId, requestedMode))
            .exceptionally(ex -> {
                Log.logError("[ClientSession:" + sessionId + "] Claim request failed: " + 
                    ex.getMessage());
                rollbackClaim(deviceId, claimedDevice);
                CompletableFuture<ClaimedDevice> future = pendingClaims.remove(deviceId);
                if (future != null && !future.isDone()) {
                    future.completeExceptionally(ex);
                }
                return null;
            });

        // Add timeout
        CompletableFuture<ClaimedDevice> timeoutFuture = confirmationFuture
            .orTimeout(CLAIM_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(ex -> {
                if (ex.getCause() instanceof java.util.concurrent.TimeoutException) {
                    Log.logError("[ClientSession:" + sessionId + "] Claim timeout: " + deviceId);
                    rollbackClaim(deviceId, claimedDevice);
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
    public CompletableFuture<Void> releaseDevice(String deviceId) {
        ClaimedDevice claimedDevice = claimedDevices.get(deviceId);
        if (claimedDevice == null) {
            Log.logMsg("[ClientSession:" + sessionId + "] Device not claimed: " + deviceId);
            return CompletableFuture.completedFuture(null);
        }

        // Create confirmation future
        CompletableFuture<Void> confirmationFuture = new CompletableFuture<>();
        pendingReleases.put(deviceId, confirmationFuture);

        // Start local cleanup
        claimedDevice.release();

        // Request daemon release (async)
        daemonExecutor.execute(() ->
            daemonCommands.releaseDevice(sessionId, deviceId))
            .exceptionally(ex -> {
                Log.logError("[ClientSession:" + sessionId + "] Release request failed: " + 
                    ex.getMessage());
                // Complete anyway - local cleanup already started
                completeRelease(deviceId);
                return null;
            });

        // Add timeout - more forgiving since local cleanup is done
        CompletableFuture<Void> timeoutFuture = confirmationFuture
            .orTimeout(RELEASE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(ex -> {
                if (ex.getCause() instanceof java.util.concurrent.TimeoutException) {
                    Log.logError("[ClientSession:" + sessionId + "] Release timeout: " + deviceId);
                }
                // Complete anyway - force cleanup
                completeRelease(deviceId);
                pendingReleases.remove(deviceId);
                return null;
            });

        return timeoutFuture;
    }

    /**
     * Handle device claimed confirmation from daemon
     * Called by IODaemon when ITEM_CLAIMED message arrives
     */
    public void handleDeviceClaimed(String deviceId, NoteBytesMap response) {
        CompletableFuture<ClaimedDevice> future = pendingClaims.remove(deviceId);
        if (future == null) {
            Log.logMsg("[ClientSession:" + sessionId + 
                "] Received claim confirmation for unknown device: " + deviceId);
            return;
        }

        if (future.isDone()) {
            Log.logMsg("[ClientSession:" + sessionId + 
                "] Claim future already completed for: " + deviceId);
            return;
        }

        // Check for errors in response
        NoteBytes errorCode = response.get(io.netnotes.engine.messaging.NoteMessaging.Keys.ERROR_CODE);
        if (errorCode != null) {
            String errorMsg = response.get(io.netnotes.engine.messaging.NoteMessaging.Keys.MSG).getAsString();
            Log.logError("[ClientSession:" + sessionId + "] Daemon claim failed: " + errorMsg);
            
            ClaimedDevice device = claimedDevices.get(deviceId);
            rollbackClaim(deviceId, device);
            future.completeExceptionally(
                new RuntimeException("Daemon claim failed: " + errorMsg));
        } else {
            ClaimedDevice device = claimedDevices.get(deviceId);
            if (device != null) {
                future.complete(device);
                Log.logMsg("[ClientSession:" + sessionId + "] Device claim confirmed: " + deviceId);
            } else {
                future.completeExceptionally(
                    new IllegalStateException("Device not found after claim: " + deviceId));
            }
        }
    }

    /**
     * Handle device released confirmation from daemon
     * Called by IODaemon when ITEM_RELEASED message arrives
     */
    public void handleDeviceReleased(String deviceId) {
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
    private void rollbackClaim(String deviceId, ClaimedDevice device) {
        if (device != null) {
            try {
                device.release();
                daemon.unregisterClaimedDevice(device.getContextPath());
            } catch (Exception e) {
                Log.logError("[ClientSession:" + sessionId + 
                    "] Rollback unregister failed: " + e.getMessage());
            }
        }
        
        claimedDevices.remove(deviceId);
        discoveredDevices.markReleased(deviceId);
        
        if (claimedDevices.isEmpty()) {
            state.removeState(ClientStateFlags.HAS_CLAIMED_DEVICES);
        }
    }

    /**
     * Complete release operation - final cleanup
     */
    private void completeRelease(String deviceId) {
        ClaimedDevice device = claimedDevices.remove(deviceId);
        if (device != null) {
            try {
                daemon.unregisterClaimedDevice(device.getContextPath());
            } catch (Exception e) {
                Log.logError("[ClientSession:" + sessionId + 
                    "] Release cleanup failed: " + e.getMessage());
            }
        }
        
        discoveredDevices.markReleased(deviceId);
        
        if (claimedDevices.isEmpty()) {
            state.removeState(ClientStateFlags.HAS_CLAIMED_DEVICES);
        }
    }
    
    /**
     * Release all devices - called during shutdown
     */
    private void releaseAllDevices() {
        if (claimedDevices.isEmpty()) {
            return;
        }
        
        Log.logMsg("[ClientSession:" + sessionId + "] Releasing " + 
            claimedDevices.size() + " devices");
        
        for (ClaimedDevice device : claimedDevices.values()) {
            try {
                String deviceId = device.getDeviceId();
                
                // Notify device to release
                device.release();
                
                // Unregister from registry
                daemon.unregisterClaimedDevice(device.getContextPath());
                
                // Update discovery state
                discoveredDevices.markReleased(deviceId);
                
                Log.logMsg("[ClientSession:" + sessionId + "] Released: " + deviceId);
                
            } catch (Exception e) {
                Log.logError("[ClientSession:" + sessionId + "] Error releasing device " + 
                    device.getDeviceId() + ": " + e.getMessage());
            }
        }
        
        claimedDevices.clear();
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
        
        if (claimedDevices.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Release each device
        CompletableFuture<Void> releaseAll = CompletableFuture.allOf(
            claimedDevices.keySet().stream()
                .map(deviceId -> releaseDevice(deviceId)  // Now returns confirmation future
                    .exceptionally(ex -> {
                        Log.logError("[ClientSession:" + sessionId + "] Error releasing " + 
                            deviceId + ": " + ex.getMessage());
                        return null;
                    })
                )
                .toArray(CompletableFuture[]::new)
        );
        
        return releaseAll.thenRun(() -> {
            // Clear discovery state
            discoveredDevices.clear();
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
        CompletableFuture<List<DeviceDescriptorWithCapabilities>> pendingDiscovery = discoveryFuture;
        discoveryFuture = null;
        if (pendingDiscovery != null && !pendingDiscovery.isDone()) {
            pendingDiscovery.completeExceptionally(
                new IllegalStateException("Daemon disconnected"));
        }
        
        // Cancel all pending operations
        for (CompletableFuture<ClaimedDevice> future : pendingClaims.values()) {
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
        
        discoveredDevices.clear();
    }
    
    /**
     * Handle daemon disconnect notification
     * Called directly by IODaemon
     */
    public void handleDaemonDisconnect(NoteBytesMap notification) {
        Log.logError("[ClientSession:" + sessionId + "] Daemon disconnected: " + 
            notification.get(io.netnotes.engine.messaging.NoteMessaging.Keys.MSG));
        
        // Emergency shutdown - can't communicate with daemon anymore
        emergencyShutdown();
    }
    
    // ===== DISCOVERED DEVICE HELPERS =====
    
    /**
     * Get device info from discovered registry
     */
    public DeviceDescriptorWithCapabilities getDeviceInfo(String deviceId) {
        return discoveredDevices.getDevice(deviceId);
    }
    
    /**
     * Get all discovered devices
     */
    public List<DeviceDescriptorWithCapabilities> getAllDiscoveredDevices() {
        return discoveredDevices.getAllDevices();
    }
    
    /**
     * Get unclaimed devices
     */
    public List<DeviceDescriptorWithCapabilities> getUnclaimedDevices() {
        return discoveredDevices.getUnclaimedDevices();
    }
    
    /**
     * Get available modes for a device
     */
    public Set<String> getAvailableModesForDevice(String deviceId) {
        return discoveredDevices.getAvailableModes(deviceId);
    }
    
    /**
     * Validate mode before claiming
     */
    public boolean canClaimWithMode(String deviceId, String mode) {
        return discoveredDevices.validateModeCompatibility(deviceId, mode);
    }
    
    /**
     * Print discovered devices for debugging
     */
    public void printDiscoveredDevices() {
        discoveredDevices.printDevices();
    }
    
    // ===== GETTERS =====
    
    public ClaimedDevice getClaimedDevice(String deviceId) {
        return claimedDevices.get(deviceId);
    }
    
    public DiscoveredDeviceRegistry getDiscoveredDevices() {
        return discoveredDevices;
    }
    
    /**
     * Get count of claimed devices in this session
     */
    public int getClaimedDeviceCount() {
        return claimedDevices.size();
    }

    /**
     * Get all claimed devices
     */
    public List<ClaimedDevice> getClaimedDevices() {
        return List.copyOf(claimedDevices.values());
    }

    /**
     * Check if device is claimed by this session
     */
    public boolean hasDevice(String deviceId) {
        return claimedDevices.containsKey(deviceId);
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
