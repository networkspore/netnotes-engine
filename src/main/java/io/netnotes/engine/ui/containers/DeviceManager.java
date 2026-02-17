package io.netnotes.engine.ui.containers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.daemon.ClaimedDevice;
import io.netnotes.engine.io.input.IEventFactory;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.SerializedVirtualExecutor;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesReadOnly;

/**
 * DeviceManager - Base class for device drivers that attach to ContainerHandles
 * 
 * ARCHITECTURE:
 * - Manages device lifecycle tied to handle lifetime
 * - Handles device claiming/releasing through IODaemon
 * - Configures event filtering at handle level
 * - Provides event routing via EventDispatcher
 * 
 * LIFECYCLE:
 * 1. attachToHandle() - Associate with a handle
 * 2. enable() - Claim device and configure routing
 * 3. disable() - Release device and cleanup
 * 4. detach() - Detach from handle (called on handle close)
 * 
 * THREAD SAFETY:
 * - All operations execute on handle's serialExec
 * - Subclasses don't need synchronization
 * 
 */
public abstract class DeviceManager<
    H extends ContainerHandle<?,H,?,?,?,DM,?,?,?,?,?,?,?>,
    DM extends DeviceManager<H,DM>
> {
    
    // ===== CORE STATE =====
    protected H handle;
    protected NoteBytesReadOnly deviceId;
    protected NoteBytesReadOnly mode;
    protected final NoteBytesReadOnly deviceType;
    protected ContextPath deviceSourcePath;
    protected ContainerHandle.EventDispatcher dispatcher;

    protected final SerializedVirtualExecutor serialExec = VirtualExecutors.getUiExecutor();
    
    // ===== CONSTRUCTION =====
    
    protected DeviceManager(NoteBytes deviceId, NoteBytes mode, NoteBytes deviceType) {
        this.deviceId = deviceId.readOnly();
        this.mode = mode.readOnly();
        this.deviceType = deviceType.readOnly();
    }
    
    // ===== LIFECYCLE MANAGEMENT =====
    
    /**
     * Attach to a handle - called by handle.addDeviceManager()
     * Package-private - only ContainerHandle should call this
     */
    CompletableFuture<Void> attachToHandle(H handle) {
        return serialExec.execute(() -> {
            if (this.handle != null) {
                throw new IllegalStateException("DeviceManager already attached to handle: " + 
                    this.handle.getId());
            }
            this.handle = handle;
            Log.logMsg("[DeviceManager:" + getDeviceType() + "] Attached to handle: " + 
                handle.getId());
            onAttached(handle);
        });
    }
    
    /**
     * Enable device - claim and configure event routing
     * 
     * @return Future that completes when device is enabled
     */
    public final CompletableFuture<ClaimedDevice> enable() {
        if (handle == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("DeviceManager not attached to handle"));
        }
        
        Log.logMsg("[DeviceManager:" + getDeviceType() + "] Enabling device: " + deviceId);
        
        return claimDevice() 
            .thenCompose(device -> {
                if (device == null) {
                    return CompletableFuture.failedFuture(
                        new IllegalStateException("Device claim returned null: " + deviceId));
                }
                
                deviceSourcePath = device.getContextPath();
                
                // Setup event routing on handle's executor
                return setupEventRouting(device)
                    .thenCompose(v -> onDeviceEnabled(device))
                    .thenApply(v -> {
                        Log.logMsg("[DeviceManager:" + device.getDeviceType() + 
                            "] Device enabled, source: " + deviceSourcePath);
                        return device;
                    });
            })
            .exceptionally(ex -> {
                Log.logError("[DeviceManager:" + getDeviceType() + 
                    "] Enable failed: " + ex.getMessage());
                throw new RuntimeException("Failed to enable device: " + deviceId, ex);
            });
    }
    
    /**
     * Disable device - release and cleanup event routing
     * 
     * @return Future that completes when device is disabled
     */
    public final CompletableFuture<Void> disable() {
        if (handle == null || deviceSourcePath == null) {
            Log.logMsg("[DeviceManager:" + getDeviceType() + 
                "] Disable called but not enabled");
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[DeviceManager:" + getDeviceType() + "] Disabling device: " + deviceId);
        
        return serialExec.submit(() -> {
            cleanupEventRouting();
            onDeviceDisabled();
            return null;
        })
        .thenCompose(v -> releaseDevice())
        .thenRun(() -> {
            deviceSourcePath = null;
            Log.logMsg("[DeviceManager:" + getDeviceType() + "] Device disabled");
        })
        .exceptionally(ex -> {
            Log.logError("[DeviceManager:" + getDeviceType() + 
                "] Disable failed: " + ex.getMessage());
            deviceSourcePath = null;
            return null;
        });
    }
    
    /**
     * Detach from handle - called on handle close
     * Package-private - only ContainerHandle should call this
     */
    final CompletableFuture<Void> detach() {
        Log.logMsg("[DeviceManager:" + getDeviceType() + "] Detaching from handle");
        
        return disable().thenRun(() -> {
            handle = null;
        });
    }
    
    // ===== DEVICE CLAIMING =====
    
    /**
     * Claim the device through IODaemon session
     */
    private CompletableFuture<ClaimedDevice> claimDevice() {
        return getHandle() 
            .thenCompose(handle -> {
                if (handle == null) {
                    throw new CompletionException(new NullPointerException("handle is null"));
                }
                return handle.ensureIOSession()
                    .thenCompose(session -> session.claimDevice(deviceId, mode, createEventFactory()));
            });
    }

    protected abstract IEventFactory createEventFactory();


    /*
    */
    /**
     * Release the device
     */
    private CompletableFuture<Void> releaseDevice() {
        return getHandle()
            .thenCompose(h->{
                if(h != null){
                    return h.getIODaemonSession()
                        .thenCompose(session->{
                            if(session != null){
                                return session.releaseDevice(deviceId);
                            }
                            return CompletableFuture.completedFuture(null);
                        });
                }
                return CompletableFuture.completedFuture(null);
            });
    }
    
    // ===== EVENT ROUTING =====
    
    /**
     * Setup event routing - called on handle's serialExec
     */
    private CompletableFuture<Void> setupEventRouting(ClaimedDevice device) {
        // Create dispatcher and attach to device
        return handle.addEventDispatcher().thenAccept(d -> {
            this.dispatcher = d;
            device.setEventDispatcher(dispatcher);
            
            Log.logMsg("[DeviceManager:" + getDeviceType() + "] Event dispatcher attached");
        })
        .thenCompose(v->requiresExclusiveAccess())
        .thenCompose(required->{
            if (required) {
                return configureEventFilters();
            }
            return CompletableFuture.completedFuture(null);
        });
    }
    
    /**
     * Cleanup event routing - called on handle's serialExec
     */
    private CompletableFuture<Void> cleanupEventRouting() {
        return requiresExclusiveAccess()
            .thenCompose(required->{
                if(required){
                    return removeEventFilters();
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenCompose((v)->{
                // Remove dispatcher
                if (dispatcher != null) {
                    return handle.removeEventDispatcher(dispatcher)
                        .thenCompose(v1->handle.getIODaemonSession())
                        .thenAccept(ioSession->{
                            if(ioSession != null){
                                ClaimedDevice device = ioSession.getClaimedDevice(deviceId);
                                if (device != null) {
                                    device.setEventDispatcher(null);
                                }
                            }

                        })
                        .thenRun(()->{
                            dispatcher = null;
                            Log.logMsg("[DeviceManager:" + getDeviceType() + "] Event dispatcher removed");
                        });
                }
                return CompletableFuture.completedFuture(null);
            });
    }

    
    // ===== SUBCLASS HOOKS =====
    
    /**
     * Called when attached to a handle (on handle's serialExec)
     */
    protected abstract CompletableFuture<Void> onAttached(H handle);
    
    /**
     * Called when device is enabled and claimed (on handle's serialExec)
     */
    protected abstract CompletableFuture<Void> onDeviceEnabled(ClaimedDevice device);
    
    /**
     * Called when device is being disabled (on handle's serialExec)
     */
    protected abstract CompletableFuture<Void> onDeviceDisabled();
    
    /**
     * Return true if this device needs exclusive access
     * (blocks other devices of same type)
     */
    protected abstract CompletableFuture<Boolean> requiresExclusiveAccess();
    
    /**
     * Configure event filters on handle (called if requiresExclusiveAccess() is true)
     * Runs on handle's serialExec
     */
    protected abstract CompletableFuture<Void> configureEventFilters();
    
    /**
     * Remove event filters from handle (called if requiresExclusiveAccess() is true)
     * Runs on handle's serialExec
     */
    protected abstract CompletableFuture<Void> removeEventFilters();
    
    
    // ===== QUERIES =====
    
    /**
     * Check if device is currently enabled
     */
    public CompletableFuture<Boolean> isEnabled() {
        return serialExec.submit(()->{
            return deviceSourcePath != null && handle != null;
        });
    }
    
    /**
     * Get the device source path (null if not enabled)
     */
    public CompletableFuture<ContextPath> getDeviceSourcePath() {
        return serialExec.submit(()->{
            return deviceSourcePath;
        });
    }
    
    /**
     * Get the device ID
     */
    public CompletableFuture<NoteBytesReadOnly> getDeviceId() {
        return serialExec.submit(()->{
            return deviceId;
        });
    }
    
    /**
     * Get the device mode
     */
    public CompletableFuture<NoteBytesReadOnly> getMode() {
        return serialExec.submit(()->{
            return mode;
        });
    }

    public NoteBytesReadOnly getDeviceType(){
        return deviceType;
    }
    
    /**
     * Get the attached handle (null if not attached)
     */
    protected CompletableFuture<H> getHandle() {
        return serialExec.submit(()->{
            return handle;
        });
    }
}