package io.netnotes.engine.core.system.control;


import io.netnotes.engine.core.system.BootstrapConfig;
import io.netnotes.engine.core.system.SystemProcess;
import io.netnotes.engine.core.system.control.containers.ContainerService;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.IODaemon;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * ServicesProcess - Manages system-level services
 * 
 * Lives at: /system/services
 * 
 * Services:
 * - ContainerService: Window/UI container management (core)
 * - IODaemon: Input/output device management (optional)
 * - Future: NetworkService, StorageService, etc.
 * 
 * Services are registered as children and can be accessed by INodes
 * via standard message passing to their known paths.
 */
public class ServicesProcess extends FlowProcess {
    
    public static final String NAME = "services";
    public static final ContextPath SERVICES_PATH = SystemProcess.SYSTEM_PATH.append(NAME);
    
    // Service names
    public static final String CONTAINER_SERVICE = "container-service";
    public static final String IO_DAEMON = "io-daemon";
    
    // Service paths (publicly accessible)
    public static final ContextPath CONTAINER_SERVICE_PATH = SERVICES_PATH.append(CONTAINER_SERVICE);
    public static final ContextPath IO_DAEMON_PATH = SERVICES_PATH.append(IO_DAEMON);
    
    private final UIRenderer uiRenderer;
    private final NoteBytesMap bootstrapConfig;
    private final BitFlagStateMachine state;
    
    // Services
    private ContainerService containerService;
    private IODaemon ioDaemon;
    
    public ServicesProcess(
        UIRenderer uiRenderer,
        NoteBytesMap bootstrapConfig
    ) {
        super(NAME, ProcessType.BIDIRECTIONAL);
        this.uiRenderer = uiRenderer;
        this.bootstrapConfig = bootstrapConfig;
        this.state = new BitFlagStateMachine("services");
        
        setupStateTransitions();
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(ServicesStates.INITIALIZING, (old, now, bit) -> {
            System.out.println("[ServicesProcess] Initializing...");
        });
        
        state.onStateAdded(ServicesStates.STARTING_CORE, (old, now, bit) -> {
            System.out.println("[ServicesProcess] Starting core services...");
        });
        
        state.onStateAdded(ServicesStates.STARTING_OPTIONAL, (old, now, bit) -> {
            System.out.println("[ServicesProcess] Starting optional services...");
        });
        
        state.onStateAdded(ServicesStates.READY, (old, now, bit) -> {
            System.out.println("[ServicesProcess] " + ServicesStates.describe(state));
        });
        
        state.onStateAdded(ServicesStates.CONTAINER_SERVICE_ACTIVE, (old, now, bit) -> {
            System.out.println("[ServicesProcess] ContainerService is active");
        });
        
        state.onStateAdded(ServicesStates.IO_DAEMON_ACTIVE, (old, now, bit) -> {
            System.out.println("[ServicesProcess] IODaemon is active");
        });
        
        state.onStateAdded(ServicesStates.SHUTTING_DOWN, (old, now, bit) -> {
            System.out.println("[ServicesProcess] Shutting down services...");
        });
        
        state.onStateAdded(ServicesStates.STOPPED, (old, now, bit) -> {
            System.out.println("[ServicesProcess] All services stopped");
        });
        
        state.onStateAdded(ServicesStates.ERROR, (old, now, bit) -> {
            System.err.println("[ServicesProcess] ERROR: " + ServicesStates.describe(state));
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
                
                System.out.println("[ServicesProcess] All services operational");
            })
            .thenCompose(v -> getCompletionFuture())
            .exceptionally(ex -> {
                System.err.println("[ServicesProcess] Fatal error: " + ex.getMessage());
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
     * Start optional services (based on configuration)
     */
    private CompletableFuture<Void> startOptionalServices() {
        List<CompletableFuture<Void>> optionalFutures = new ArrayList<>();
        
        // IODaemon (if secure input is configured)
        if (BootstrapConfig.isSecureInputInstalled(bootstrapConfig)) {
            optionalFutures.add(startIODaemon());
        } else {
            System.out.println("[ServicesProcess] IODaemon not configured, skipping");
        }
        
        // Future services can be added here
        
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
        System.out.println("[ServicesProcess] Starting ContainerService...");
        
        containerService = new ContainerService(CONTAINER_SERVICE, uiRenderer);
        
        return spawnChild(containerService)
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> {
                state.addState(ServicesStates.CONTAINER_SERVICE_ACTIVE);
                System.out.println("[ServicesProcess] ContainerService started at: " + 
                    CONTAINER_SERVICE_PATH);
            })
            .exceptionally(ex -> {
                System.err.println("[ServicesProcess] FATAL: ContainerService failed to start: " + 
                    ex.getMessage());
                state.addState(ServicesStates.CONTAINER_SERVICE_FAILED);
                state.addState(ServicesStates.ERROR);
                throw new RuntimeException("Core service failed", ex);
            });
    }
    
    /**
     * Start IODaemon
     */
    private CompletableFuture<Void> startIODaemon() {
        System.out.println("[ServicesProcess] Starting IODaemon...");
        
        String socketPath = BootstrapConfig.getSecureInputSocketPath(bootstrapConfig);
        ioDaemon = new IODaemon(IO_DAEMON, socketPath);
        
        return spawnChild(ioDaemon)
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> {
                state.addState(ServicesStates.IO_DAEMON_ACTIVE);
                System.out.println("[ServicesProcess] IODaemon started at: " + 
                    IO_DAEMON_PATH);
            })
            .exceptionally(ex -> {
                System.err.println("[ServicesProcess] IODaemon failed to start: " + 
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
    
    public ContainerService getContainerService() {
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
        System.err.println("[ServicesProcess] Unexpected stream channel from: " + fromPath);
    }
    
    /**
     * Shutdown all services
     */
    public CompletableFuture<Void> shutdown() {
        if (!ServicesStates.canShutdown(state)) {
            return CompletableFuture.completedFuture(null);
        }
        
        state.addState(ServicesStates.SHUTTING_DOWN);
        
        System.out.println("[ServicesProcess] Shutting down services...");
        
        List<CompletableFuture<Void>> shutdownFutures = new ArrayList<>();
        
        // Shutdown in reverse order (optional first, then core)
        if (ioDaemon != null && state.hasState(ServicesStates.IO_DAEMON_ACTIVE)) {
            shutdownFutures.add(CompletableFuture.runAsync(() -> {
                ioDaemon.kill();
                state.removeState(ServicesStates.IO_DAEMON_ACTIVE);
                System.out.println("[ServicesProcess] IODaemon stopped");
            }));
        }
        
        if (containerService != null && state.hasState(ServicesStates.CONTAINER_SERVICE_ACTIVE)) {
            shutdownFutures.add(containerService.shutdown()
                .thenRun(() -> {
                    state.removeState(ServicesStates.CONTAINER_SERVICE_ACTIVE);
                    System.out.println("[ServicesProcess] ContainerService stopped");
                }));
        }
        
        return CompletableFuture.allOf(
            shutdownFutures.toArray(new CompletableFuture[0])
        ).thenRun(() -> {
            state.removeState(ServicesStates.SHUTTING_DOWN);
            state.removeState(ServicesStates.READY);
            state.addState(ServicesStates.STOPPED);
            
            System.out.println("[ServicesProcess] All services stopped");
        });
    }
}