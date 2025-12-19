package io.netnotes.engine.core.system.control;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.system.BootstrapConfig;
import io.netnotes.engine.core.system.control.containers.RenderingService;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.IODaemon;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * ServicesProcess - Manages system-level services
 * 
 * REFACTORED to use BootstrapConfig singleton:
 * - Accesses config through BootstrapConfig.getInstance()
 * - No stale config - always reads fresh values
 * - Can react to config changes dynamically
 * 
 * Lives at: /system/services
 * 
 * Services:
 * - ContainerService: Window/UI container management (core)
 * - IODaemon: Input/output device management (optional, based on config)
 * - Future: NetworkService, StorageService, etc.
 */
public class ServicesProcess extends FlowProcess {
    
    public static final String NAME = "services";
    public static final ContextPath SERVICES_PATH = CoreConstants.SYSTEM_PATH.append(NAME);
    
    // Service names
    public static final String CONTAINER_SERVICE = "container-service";
    public static final String IO_DAEMON = "io-daemon";
    
    // Service paths (publicly accessible)
    public static final ContextPath CONTAINER_SERVICE_PATH = SERVICES_PATH.append(CONTAINER_SERVICE);
    public static final ContextPath IO_DAEMON_PATH = SERVICES_PATH.append(IO_DAEMON);

    private final BitFlagStateMachine state;
    
    // Services
    private final RenderingService containerService;
    private IODaemon ioDaemon;
    
    public ServicesProcess(RenderingService containerService) {
        super(NAME, ProcessType.BIDIRECTIONAL);
        this.state = new BitFlagStateMachine("services");
        this.containerService = containerService;
        setupStateTransitions();
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(ServicesStates.INITIALIZING, (old, now, bit) -> {
            Log.logMsg("[ServicesProcess] Initializing...");
        });
        
        state.onStateAdded(ServicesStates.STARTING_CORE, (old, now, bit) -> {
            Log.logMsg("[ServicesProcess] Starting core services...");
        });
        
        state.onStateAdded(ServicesStates.STARTING_OPTIONAL, (old, now, bit) -> {
            Log.logMsg("[ServicesProcess] Starting optional services...");
        });
        
        state.onStateAdded(ServicesStates.READY, (old, now, bit) -> {
            Log.logMsg("[ServicesProcess] " + ServicesStates.describe(state));
        });
        
        state.onStateAdded(ServicesStates.CONTAINER_SERVICE_ACTIVE, (old, now, bit) -> {
            Log.logMsg("[ServicesProcess] ContainerService is active");
        });
        
        state.onStateAdded(ServicesStates.IO_DAEMON_ACTIVE, (old, now, bit) -> {
            Log.logMsg("[ServicesProcess] IODaemon is active");
        });
        
        state.onStateAdded(ServicesStates.SHUTTING_DOWN, (old, now, bit) -> {
            Log.logMsg("[ServicesProcess] Shutting down services...");
        });
        
        state.onStateAdded(ServicesStates.STOPPED, (old, now, bit) -> {
            Log.logMsg("[ServicesProcess] All services stopped");
        });
        
        state.onStateAdded(ServicesStates.ERROR, (old, now, bit) -> {
            Log.logError("[ServicesProcess] ERROR: " + ServicesStates.describe(state));
        });
    }
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(ServicesStates.INITIALIZING);
        
        return startCoreServices()
            .thenCompose(v -> {
                state.removeState(ServicesStates.INITIALIZING);
                state.removeState(ServicesStates.STARTING_CORE);
                state.addState(ServicesStates.STARTING_OPTIONAL);
                
                return startOptionalServices();
            })
            .thenRun(() -> {
                state.removeState(ServicesStates.STARTING_OPTIONAL);
                state.addState(ServicesStates.READY);
                
                Log.logMsg("[ServicesProcess] All services operational");
            })
            .thenCompose(v -> getCompletionFuture())
            .exceptionally(ex -> {
                Log.logError("[ServicesProcess] Fatal error: " + ex.getMessage());
                state.addState(ServicesStates.ERROR);
                return null;
            });
    }
    
    /**
     * Start core services (always required)
     */
    private CompletableFuture<Void> startCoreServices() {
        state.addState(ServicesStates.STARTING_CORE);
        
        List<CompletableFuture<Void>> coreFutures = new ArrayList<>();
        
        // 1. ContainerService (always needed for UI)
        coreFutures.add(startContainerService());
        
        return CompletableFuture.allOf(
            coreFutures.toArray(new CompletableFuture[0])
        );
    }
    
    /**
     * Start optional services (based on configuration from singleton)
     */
    private CompletableFuture<Void> startOptionalServices() {
        List<CompletableFuture<Void>> optionalFutures = new ArrayList<>();
        
        // Check if BootstrapConfig singleton is initialized
        if (!BootstrapConfig.isInitialized()) {
            Log.logError("[ServicesProcess] BootstrapConfig not initialized!");
            return CompletableFuture.completedFuture(null);
        }
        
        // IODaemon (if secure input is configured)
        // Access config through singleton - always fresh!
        BootstrapConfig config = BootstrapConfig.getInstance();
        
        if (config.isSecureInputInstalled()) {
            Log.logMsg("[ServicesProcess] Secure input configured, starting IODaemon");
            optionalFutures.add(startIODaemon());
        } else {
            Log.logMsg("[ServicesProcess] Secure input not configured, skipping IODaemon");
        }
        
        // Future services can be added here
        // Example:
        // if (config.isNetworkEnabled()) {
        //     optionalFutures.add(startNetworkService());
        // }
        
        if (optionalFutures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return CompletableFuture.allOf(
            optionalFutures.toArray(new CompletableFuture[0])
        );
    }
    
    /**
     * Start ContainerService
     */
    private CompletableFuture<Void> startContainerService() {
        Log.logMsg("[ServicesProcess] Starting ContainerService...");
        
        
        return spawnChild(containerService)
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> {
                state.addState(ServicesStates.CONTAINER_SERVICE_ACTIVE);
                Log.logMsg("[ServicesProcess] ContainerService started at: " + 
                    CONTAINER_SERVICE_PATH);
            })
            .exceptionally(ex -> {
                Log.logError("[ServicesProcess] FATAL: ContainerService failed to start: " + 
                    ex.getMessage());
                state.addState(ServicesStates.CONTAINER_SERVICE_FAILED);
                state.addState(ServicesStates.ERROR);
                throw new RuntimeException("Core service failed", ex);
            });
    }
    
    /**
     * Start IODaemon (accesses config through singleton)
     */
    private CompletableFuture<Void> startIODaemon() {
        Log.logMsg("[ServicesProcess] Starting IODaemon...");
        
        // Get socket path from singleton - always fresh!
        BootstrapConfig config = BootstrapConfig.getInstance();
        String socketPath = config.getSecureInputSocketPath();
        
        Log.logMsg("[ServicesProcess] Using socket path: " + socketPath);
        
        ioDaemon = new IODaemon(IO_DAEMON, socketPath);
        
        return spawnChild(ioDaemon)
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> {
                state.addState(ServicesStates.IO_DAEMON_ACTIVE);
                Log.logMsg("[ServicesProcess] IODaemon started at: " + 
                    IO_DAEMON_PATH);
            })
            .exceptionally(ex -> {
                Log.logError("[ServicesProcess] IODaemon failed to start: " + 
                    ex.getMessage());
                state.addState(ServicesStates.IO_DAEMON_FAILED);
                // Don't fail entire startup if IODaemon fails
                return null;
            });
    }
    
    /**
     * Get service status
     */
    public boolean isOperational() {
        return ServicesStates.isOperational(state);
    }
    
    public boolean hasCoreServices() {
        return ServicesStates.hasCoreServices(state);
    }
    
    public boolean hasIODaemon() {
        return ServicesStates.hasIODaemon(state);
    }
    
    public int getActiveServiceCount() {
        return ServicesStates.getActiveServiceCount(state);
    }
    
    public String getStateDescription() {
        return ServicesStates.describe(state);
    }
    
    public RenderingService getContainerService() {
        return containerService;
    }
    
    public IODaemon getIODaemon() {
        return ioDaemon;
    }
    
    public BitFlagStateMachine getState() {
        return state;
    }
    
    /**
     * Handle service queries (optional - services are usually accessed directly)
     */
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Could implement service discovery/status queries here
        // For now, services are accessed by their known paths
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        Log.logError("[ServicesProcess] Unexpected stream channel from: " + fromPath);
    }
    
    /**
     * Shutdown all services
     */
    public CompletableFuture<Void> shutdown() {
        if (!ServicesStates.canShutdown(state)) {
            return CompletableFuture.completedFuture(null);
        }
        
        state.addState(ServicesStates.SHUTTING_DOWN);
        
        Log.logMsg("[ServicesProcess] Shutting down services...");
        
        List<CompletableFuture<Void>> shutdownFutures = new ArrayList<>();
        
        // Shutdown in reverse order (optional first, then core)
        if (ioDaemon != null && state.hasState(ServicesStates.IO_DAEMON_ACTIVE)) {
            shutdownFutures.add(CompletableFuture.runAsync(() -> {
                ioDaemon.kill();
                state.removeState(ServicesStates.IO_DAEMON_ACTIVE);
                Log.logMsg("[ServicesProcess] IODaemon stopped");
            }));
        }
        
        if (containerService != null && state.hasState(ServicesStates.CONTAINER_SERVICE_ACTIVE)) {
            shutdownFutures.add(containerService.shutdown()
                .thenRun(() -> {
                    state.removeState(ServicesStates.CONTAINER_SERVICE_ACTIVE);
                    Log.logMsg("[ServicesProcess] ContainerService stopped");
                }));
        }
        
        return CompletableFuture.allOf(
            shutdownFutures.toArray(new CompletableFuture[0])
        ).thenRun(() -> {
            state.removeState(ServicesStates.SHUTTING_DOWN);
            state.removeState(ServicesStates.READY);
            state.addState(ServicesStates.STOPPED);
            
            Log.logMsg("[ServicesProcess] All services stopped");
        });
    }
}