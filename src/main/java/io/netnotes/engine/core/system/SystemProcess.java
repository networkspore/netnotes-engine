package io.netnotes.engine.core.system;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.system.control.containers.RenderingService;
import io.netnotes.engine.core.system.control.terminal.ClientTerminalRenderManager;
import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
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
 * SystemProcess - Integrated with ClientTerminalRenderManager
 * 
 * Architecture:
 * /system/
 *   rendering-service/           # RenderingService (server interface)
 *   render-manager/              # ClientTerminalRenderManager (owns handles)
 *     system-terminal/           # SystemTerminalContainer (handle)
 *     [other-terminals]/         # Additional terminals as needed
 * 
 * Flow:
 * 1. Start UIRenderer
 * 2. Start RenderingService (server interface)
 * 3. Start ClientTerminalRenderManager (client coordinator)
 * 4. Create system terminal through manager
 * 5. Terminal handles bootstrap, auth, IODaemon, screens
 */
public class SystemProcess extends FlowProcess {
    
    private final FlowProcessService processService;
    private final BitFlagStateMachine state;
    
    // Core components
    private final UIRenderer<?> uiRenderer;
    private RenderingService renderingService;
    private ClientTerminalRenderManager renderManager;
    private TerminalContainerHandle systemTerminal;
    
    // States
    public static final int INITIALIZING = 0;
    public static final int RENDERING_STARTING = 1;
    public static final int MANAGER_STARTING = 2;
    public static final int TERMINAL_CREATING = 3;
    public static final int READY = 4;
    public static final int ERROR = 5;
    
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
        
        state.onStateAdded(MANAGER_STARTING, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] MANAGER_STARTING"));
        
        state.onStateAdded(TERMINAL_CREATING, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] TERMINAL_CREATING"));
        
        state.onStateAdded(READY, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] READY - System operational"));
    }

    FlowProcessService getProcessService(){
        return processService;
    }
    
    /**
     * Initialize system
     * UIRenderer → RenderingService → ClientTerminalRenderManager → Ready
     */
    CompletableFuture<Void> initialize() {
        state.addState(INITIALIZING);
        
        return initializeUIRenderer()
            .thenCompose(v -> {
                state.removeState(INITIALIZING);
                state.addState(RENDERING_STARTING);
                return startRenderingService();
            })
            .thenCompose(v -> {
                state.removeState(RENDERING_STARTING);
                state.addState(MANAGER_STARTING);
                return startRenderManager();
            })
            .thenRun(() -> {
                state.removeState(MANAGER_STARTING);
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
     * Start RenderingService (server interface)
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
                registry.connect(contextPath, servicePath);
                registry.connect(servicePath, contextPath);
                
                Log.logMsg("[SystemProcess] RenderingService operational at: " + servicePath);
            });
    }
    
    /**
     * Start ClientTerminalRenderManager (client coordinator)
     * Lives at /system/render-manager
     * 
     * Manager owns and coordinates all terminal handles
     */
    private CompletableFuture<Void> startRenderManager() {
        renderManager = new ClientTerminalRenderManager(
            "render-manager",
            CoreConstants.RENDERING_SERVICE_PATH
        );
        
        registerChild(renderManager);
        ContextPath managerPath = renderManager.getContextPath();
        
        Log.logMsg("[SystemProcess] Starting ClientTerminalRenderManager at: " + managerPath);
        
        return startProcess(managerPath)
            .thenRun(() -> {
                Log.logMsg("[SystemProcess] ClientTerminalRenderManager operational");
                Log.logMsg("[SystemProcess] Render loop active, ready to manage terminals");
            });
    }
    
    // ===== MESSAGE HANDLERS =====
    
    @Override
    public CompletableFuture<Void> run() {
        return getCompletionFuture();
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
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
     * Called by ConsoleApplication after system is ready
     * 
     * Terminal is created through render manager and handles:
     * - Bootstrap configuration (if needed)
     * - Authentication (first run + login + unlock)
     * - IODaemon management (via ServicesManager)
     * - All screens and menus
     */
    public CompletableFuture<Void> openSystemTerminal() {
        if (systemTerminal == null) {
            Log.logMsg("[SystemProcess] Creating system terminal through render manager");
            
            return createSystemTerminal()
                .thenCompose(v -> systemTerminal.show()); // Show instead of "open"
        }
        
        // Terminal exists, just show it
        Log.logMsg("[SystemProcess] Showing existing system terminal");
        return systemTerminal.show();
    }
    
    /**
     * Create THE system terminal through render manager
     * 
     * Manager creates handle as its child and automatically:
     * - Registers it in the process hierarchy
     * - Starts the handle process
     * - Tracks it for rendering
     * - Polls it for render state
     */
    private CompletableFuture<Void> createSystemTerminal() {
        Log.logMsg("[SystemProcess] Creating system terminal through manager");
        
        state.addState(TERMINAL_CREATING);
        
        // Create terminal through manager (manager owns it)
        systemTerminal = renderManager.createTerminal("system-terminal")
            .title("System Terminal")
            .size(80, 24)
            .autoFocus(true)
            .build();
        
        // Manager automatically started the terminal
        // Wait for it to be ready
        return systemTerminal.waitUntilReady()
            .thenRun(() -> {
                state.removeState(TERMINAL_CREATING);
                Log.logMsg("[SystemProcess] System terminal ready: " + 
                    systemTerminal.getContextPath());
                Log.logMsg("[SystemProcess] Terminal owned by render manager, " +
                    "automatic rendering active");
            });
    }
    
    // ===== GETTERS =====
    
    public TerminalContainerHandle getSystemTerminal() {
        return systemTerminal;
    }
    
    public ClientTerminalRenderManager getRenderManager() {
        return renderManager;
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