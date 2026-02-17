package io.netnotes.engine.io.daemon;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.daemon.DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.utils.noteBytes.NoteUUID;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * IODaemonManager - Manages IODaemon configuration and availability
 * 
 * NOT responsible for IODaemon lifecycle
 * IODaemon manages its own lifecycle based on active sessions
 * 
 * Responsibilities:
 * - Detect IODaemon installation and availability
 * - Ensure IODaemon is registered and started (if not already)
 * - Provide socket path configuration
 * - Help containers connect to IODaemon
 * - Update configuration when settings change
 */
public class IODaemonManager {
    private final String ioDaemonName;
    private final ContextPath ioDaemonPath;
    private final ProcessRegistryInterface registry;
    private volatile String ioDaemonSocketPath;
    private volatile ClientSession discoverySession = null;
    
    public IODaemonManager(
        String ioDaemonName,
        ContextPath ioDaemonPath,
        ProcessRegistryInterface registry, 
        String ioDaemonSocketPath) {
        this.ioDaemonName = ioDaemonName;
        this.ioDaemonPath = ioDaemonPath;
        this.registry = registry;
        this.ioDaemonSocketPath = ioDaemonSocketPath;
    }
    
    // ===== DETECTION =====
    
    /**
     * Detect if IODaemon is installed and available
     * Returns detection results with detailed status
     */
    public CompletableFuture<IODaemonDetection.DetectionResult> detect() {
        return IODaemonDetection.detect(VirtualExecutors.getVirtualExecutor())
            .thenApply(result -> {
                Log.logMsg("[IODaemonManager] Detection result:\n" + result);
                return result;
            });
    }
    
    /**
     * Quick check if IODaemon is available
     */
    public CompletableFuture<Boolean> isAvailable() {
        return detect().thenApply(IODaemonDetection.DetectionResult::isAvailable);
    }
    
    /**
     * Check if IODaemon is fully operational (running and socket accessible)
     */
    public CompletableFuture<Boolean> isOperational() {
        return detect().thenApply(IODaemonDetection.DetectionResult::isFullyOperational);
    }
    
    // ===== LIFECYCLE COORDINATION =====
    
    /**
     * Ensure IODaemon is available and registered
     * 
     * This is idempotent - safe to call multiple times
     * - If not installed: returns error with installation info
     * - If installed but not running: attempts to start service
     * - If running but not registered: registers it
     * - If already registered and running: returns existing path
     * 
     * @return CompletableFuture with IODaemon context path, or fails if unavailable
     */
    public CompletableFuture<ContextPath> ensureAvailable() {
        return detect()
            .thenCompose(result -> {
                if (!result.isAvailable()) {
                    // Not installed - provide installation info
                    IODaemonDetection.InstallationPaths paths = 
                        IODaemonDetection.getInstallationPaths();
                    
                    String msg = "IODaemon not installed. " +
                        (paths != null ? "Expected at: " + paths.binaryPath : "");
                    
                    return CompletableFuture.failedFuture(
                        new IllegalStateException(msg));
                }
                
                // Check if already registered in process tree
                ContextPath ioDaemonPath = getIODaemonPath();
                if (ioDaemonPath != null && registry.exists(ioDaemonPath)) {
                    IODaemon existing = (IODaemon) registry.getProcess(ioDaemonPath);
                    
                    if (existing != null && existing.isAlive() && existing.isConnected()) {
                        Log.logMsg("[IODaemonManager] IODaemon already registered and running");
                        return CompletableFuture.completedFuture(ioDaemonPath);
                    } else {
                        // Registered but not healthy - clean it up
                        Log.logMsg("[IODaemonManager] Cleaning up stale registration");
                        registry.unregisterProcess(ioDaemonPath);
                    }
                }
                
                // Try to start service if not running
                if (!result.processRunning && result.serviceInstalled) {
                    Log.logMsg("[IODaemonManager] Service installed but not running, attempting to start");
                    
                    return IODaemonDetection.startService(VirtualExecutors.getVirtualExecutor())
                        .thenCompose(started -> {
                            if (started) {
                                // Wait a moment for startup
                                return CompletableFuture.runAsync(() -> {
                                    try {
                                        Thread.sleep(500);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                });
                            } else {
                                return CompletableFuture.failedFuture(
                                    new RuntimeException("Failed to start IODaemon service"));
                            }
                        })
                        .thenCompose(v -> {
                            ioDaemonSocketPath = result.socketPath;
                            return registerAndStart();
                        });
                } else if (result.processRunning) {
                    ioDaemonSocketPath = result.socketPath;
                    // Already running, just register
                    return registerAndStart();
                } else {
                    // Not running and no service - user needs to start manually
                    return CompletableFuture.failedFuture(
                        new IllegalStateException(
                            "IODaemon installed but not running. " +
                            "Start it manually or install as service."));
                }
            });
    }
    
    /**
     * Register IODaemon in process tree and start it
     */
    private CompletableFuture<ContextPath> registerAndStart() {
        String actualSocketPath = ioDaemonSocketPath;
        
        Log.logMsg("[IODaemonManager] Registering IODaemon at: " + actualSocketPath);
        
        IODaemon ioDaemon = new IODaemon(ioDaemonName, actualSocketPath);
        
        registry.registerProcess(
            ioDaemon, 
            ioDaemonPath, 
            null,  // Root level process
            registry
        );
        
        return registry.startProcess(ioDaemonPath)
            .thenApply(v -> {
                Log.logMsg("[IODaemonManager] IODaemon registered and started at: " + ioDaemonPath);
                return ioDaemonPath;
            })
            .exceptionally(ex -> {
                Log.logError("[IODaemonManager] Failed to start IODaemon: " + ex.getMessage());
                registry.unregisterProcess(ioDaemonPath);
                throw new RuntimeException("Failed to start IODaemon", ex);
            });
    }
    
    // ===== QUERIES =====
    
    /**
     * Get IODaemon path if it exists in registry
     */
    public ContextPath getIODaemonPath() {
        return ioDaemonPath;
    }
    
    /**
     * Check if IODaemon is registered and healthy
     */
    public boolean isHealthy() {
        ContextPath path = getIODaemonPath();
        if (path == null) return false;
        
        IODaemon daemon = (IODaemon) registry.getProcess(path);
        return daemon != null && daemon.isAlive() && daemon.isConnected();
    }
    
    /**
     * Get IODaemon instance if available
     */
    public IODaemon getIODaemon() {
        ContextPath path = getIODaemonPath();
        if (path == null) return null;
        
        return (IODaemon) registry.getProcess(path);
    }
    
    public String getIODaemonSocketPath() {
        return ioDaemonSocketPath;
    }
    
     public void setIODaemonSocketPath(String socketPath) {
        ioDaemonSocketPath = socketPath;
    }
    /**
     * Reconfigure socket path
     * 
     * This requires restarting IODaemon since socket path is immutable.
     * Will fail if any sessions are active.
     */
    public CompletableFuture<Void> reconfigureSocketPath(String newSocketPath) {
        ContextPath path = getIODaemonPath();
        if (path == null) {
            // Not running, nothing to do
            Log.logMsg("[IODaemonManager] IODaemon not running, no reconfiguration needed");
            return CompletableFuture.completedFuture(null);
        }
        
        IODaemon daemon = (IODaemon) registry.getProcess(path);
        if (daemon == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Check if any sessions exist
        if (!daemon.getSessions().isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException(
                    "Cannot reconfigure while sessions are active. " +
                    "Close all connections first."));
        }

        ioDaemonSocketPath = newSocketPath;
        
        Log.logMsg("[IODaemonManager] Reconfiguring socket path to: " + newSocketPath);
        daemon.kill();
        registry.unregisterProcess(path);
    
        // Small delay for cleanup
        return CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        })
        .thenCompose(v -> {
            // Register and start new daemon with new path
            return registerAndStart();
        })
        .thenRun(() -> {
            Log.logMsg("[IODaemonManager] IODaemon reconfigured successfully");
        });
    }
    
    /**
     * Get installation information for current OS
     */
    public IODaemonDetection.InstallationPaths getInstallationPaths() {
        return IODaemonDetection.getInstallationPaths();
    }

    /**
     * Create or get a temporary discovery session
     * Used for device enumeration before handles are configured
     * 
     * This session should NOT claim devices - only discover them
     */
    public CompletableFuture<ClientSession> getDiscoverySession() {
        // If we already have a valid discovery session, reuse it
        if (discoverySession != null && discoverySession.isHealthy()) {
            return CompletableFuture.completedFuture(discoverySession);
        }
        
        // Ensure IODaemon is available first
        return ensureAvailable()
            .thenCompose(path -> {
                IODaemon daemon = getIODaemon();
                if (daemon == null) {
                    return CompletableFuture.failedFuture(
                        new IllegalStateException("IODaemon not available"));
                }
                // Create a new discovery session
                NoteUUID sessionId = NoteUUID.createLocalUUID128();
                int pid = (int) ProcessHandle.current().pid();
                
                return daemon.createSession(sessionId, pid)
                    .thenApply(session -> {
                        discoverySession = session;
                        Log.logMsg("[IODaemonManager] Discovery session created: " + sessionId);
                        return session;
                    });
            });
    }



    /**
     * Discover devices using the discovery session
     * This is the proper way to enumerate devices before handle configuration
     */
    public CompletableFuture<List<DeviceDescriptorWithCapabilities>> discoverDevices() {
        return getDiscoverySession()
            .thenCompose(session -> session.discoverDevices())
            .thenApply(devices -> {
                Log.logMsg("[IODaemonManager] Discovered " + devices.size() + " devices");
                return devices;
            });
    }
    
    /**
     * Discover devices of a specific type
     */
    public CompletableFuture<List<DeviceDescriptorWithCapabilities>> discoverDevicesOfType(
            NoteBytesReadOnly deviceType) {
        return discoverDevices()
            .thenApply(devices -> devices.stream()
                .filter(d -> d.usbDevice().getDeviceType().equals(deviceType))
                .toList());
    }
    
    /**
     * Close the discovery session
     * Should be called when discovery is complete or on application shutdown
     */
    public CompletableFuture<Void> closeDiscoverySession() {
        if (discoverySession == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        ClientSession session = discoverySession;
        discoverySession = null;

        IODaemon daemon = getIODaemon();
        CompletableFuture<Void> shutdownFuture = daemon != null
            ? daemon.destroySession(session.sessionId)
            : session.shutdown();
        
        return shutdownFuture
            .thenRun(() -> {
                Log.logMsg("[IODaemonManager] Discovery session closed");
            })
            .exceptionally(ex -> {
                Log.logError("[IODaemonManager] Discovery session close error: " + 
                    ex.getMessage());
                return null;
            });
    }
    
    /**
     * Check if a specific device is available and unclaimed
     */
    public CompletableFuture<Boolean> isDeviceAvailable(NoteBytes deviceId) {
        return discoverDevices()
            .thenApply(devices -> devices.stream()
                .anyMatch(d -> d.usbDevice().getDeviceId().equals(deviceId) && 
                              !d.claimed()));
    }
}
