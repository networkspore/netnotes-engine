package io.netnotes.engine.core.system;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.system.control.containers.RenderingService;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.FlowProcessService;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.concurrent.CompletableFuture;

/**
 * SystemProcess (SIMPLIFIED)
 * 
 * Bootstrap removed - SystemTerminalContainer handles its own configuration
 * No message handling - ConsoleApplication calls methods directly
 * 
 * Architecture:
 * /system/
 *   rendering-service/     # RenderingService (started by SystemProcess)
 *   system-terminal/       # SystemTerminalContainer (handles bootstrap + auth + IODaemon)
 * 
 * Flow:
 * 1. Start UIRenderer
 * 2. Start RenderingService
 * 3. Ready - ConsoleApplication calls openSystemTerminal()
 * 4. Terminal handles everything (bootstrap, auth, IODaemon, screens)
 */
public class SystemProcess extends FlowProcess {
    
    private final FlowProcessService processService;
    private final BitFlagStateMachine state;
    
    // Core components
    private final UIRenderer<?> uiRenderer;
    private RenderingService renderingService;
    private SystemTerminalContainer systemTerminal;
    
    // States
    public static final long INITIALIZING = 1L << 0;
    public static final long RENDERING_STARTING = 1L << 1;
    public static final long TERMINAL_CREATING = 1L << 2;
    public static final long READY = 1L << 3;
    public static final long ERROR = 1L << 4;
    
    private SystemProcess(
        FlowProcessService processService,
        UIRenderer<?> uiRenderer
    ) {
        super(CoreConstants.SYSTEM, ProcessType.BIDIRECTIONAL);
        this.processService = processService;
        this.uiRenderer = uiRenderer;
        this.state = new BitFlagStateMachine(CoreConstants.SYSTEM + "-state");
        
        setupStateTransitions();
    }
    
    /**
     * Bootstrap entry point
     */
    public static CompletableFuture<SystemProcess> bootstrap(
        UIRenderer<?> uiRenderer
    ) {
        FlowProcessService flowProcessService = new FlowProcessService();
        ProcessRegistryInterface bootstrapInterface = 
            flowProcessService.getRegistryInterface();
        
        SystemProcess process = new SystemProcess(
            flowProcessService,
            uiRenderer
        );
        
        bootstrapInterface.registerProcess(process, CoreConstants.SYSTEM_PATH, 
            null, bootstrapInterface);
        
        return process.initialize()
            .thenApply(v -> process);
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(INITIALIZING, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] INITIALIZING"));
        
        state.onStateAdded(RENDERING_STARTING, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] RENDERING_STARTING"));
        
        state.onStateAdded(TERMINAL_CREATING, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] TERMINAL_CREATING"));
        
        state.onStateAdded(READY, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] READY - System operational"));
    }
    
    /**
     * Initialize system
     * Just UIRenderer + RenderingService, that's it
     */
    CompletableFuture<Void> initialize() {
        state.addState(INITIALIZING);
        
        return initializeUIRenderer()
            .thenCompose(v -> {
                state.removeState(INITIALIZING);
                state.addState(RENDERING_STARTING);
                return startRenderingService();
            })
            .thenRun(() -> {
                state.removeState(RENDERING_STARTING);
                state.addState(READY);
            })
            .exceptionally(ex -> {
                Log.logError("[SystemProcess] Init failed: " + ex.getMessage());
                state.addState(ERROR);
                return null;
            });
    }
    
    private CompletableFuture<Void> initializeUIRenderer() {
        Log.logMsg("[SystemProcess] Initializing UIRenderer");
        return uiRenderer.initialize()
            .thenRun(() -> Log.logMsg(
                "[SystemProcess] UIRenderer: " + uiRenderer.getClass().getSimpleName()));
    }
    
    /**
     * Start RenderingService
     * Lives at /system/rendering-service
     */
    private CompletableFuture<Void> startRenderingService() {
        renderingService = new RenderingService(
            CoreConstants.RENDERING_SERVICE, 
            uiRenderer
        );
        
        ContextPath servicePath = registerChildAt(renderingService, 
            CoreConstants.RENDERING_SERVICE_PATH);
        
        Log.logMsg("[SystemProcess] Starting RenderingService at: " + servicePath);
        
        return startProcess(servicePath)
            .thenRun(() -> {
                Log.logMsg("[SystemProcess] RenderingService started, connecting streams...");
                
                registry.connect(contextPath, servicePath);
                registry.connect(servicePath, contextPath);
                
                Log.logMsg("[SystemProcess] RenderingService operational at: " + servicePath);
            });
    }
    
    // ===== MESSAGE HANDLERS =====
    
    @Override
    public CompletableFuture<Void> run() {
        return getCompletionFuture();
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // SystemProcess doesn't handle messages
        // All interaction is direct method calls from ConsoleApplication
        Log.logError("[SystemProcess] Unexpected message from: " + 
            packet.getSourcePath());
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException(
            "SystemProcess does not handle streams");
    }
    
    // ===== PUBLIC API (called by ConsoleApplication) =====
    
    /**
     * Create and open system terminal
     * Called directly by ConsoleApplication after system is ready
     * 
     * Terminal handles:
     * - Bootstrap configuration (if needed)
     * - Authentication (first run + login + unlock)
     * - IODaemon management (via ServicesManager)
     * - All screens and menus
     */
    public CompletableFuture<Void> openSystemTerminal() {
        if (systemTerminal == null) {
            Log.logMsg("[SystemProcess] Creating terminal for first time");
            
            return createSystemTerminal()
                .thenCompose(v -> systemTerminal.open());
        }
        
        // Terminal exists, just open it
        Log.logMsg("[SystemProcess] Opening existing terminal");
        return systemTerminal.open();
    }
    
    /**
     * Create THE system terminal (singular, persistent)
     * 
     * Terminal owns:
     * - Bootstrap configuration (passwordKeyboardId, ioDaemonSocketPath, etc)
     * - ServicesManager (manages IODaemon lifecycle)
     * - Authentication flow (first run, login, unlock)
     * - SystemRuntime creation
     * - All screens and menus
     */
    private CompletableFuture<Void> createSystemTerminal() {
        Log.logMsg("[SystemProcess] Creating system terminal");
        
        state.addState(TERMINAL_CREATING);
        
        systemTerminal = new SystemTerminalContainer(
            renderingService,
            registry,
            contextPath
        );
        
        registerChild(systemTerminal);
        ContextPath terminalPath = systemTerminal.getContextPath();
        
        return startProcess(terminalPath)
            .thenRun(() -> {
                registry.connect(contextPath, terminalPath);
                registry.connect(terminalPath, contextPath);
                
                state.removeState(TERMINAL_CREATING);
                Log.logMsg("[SystemProcess] System terminal created: " + terminalPath);
            });
    }
    
    // ===== GETTERS =====
    
    public SystemTerminalContainer getSystemTerminal() {
        return systemTerminal;
    }
    
    public boolean isReady() {
        return state.hasState(READY);
    }
    
    public UIRenderer<?> getUIRenderer() {
        return uiRenderer;
    }
    
    public RenderingService getRenderingService() {
        return renderingService;
    }
}