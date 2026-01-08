package io.netnotes.engine.core.system;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.SettingsData;
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
 *     system-terminal/           # TerminalContainerHandle (the UI container)
 * 
 * SystemApplication wraps the handle and provides application logic
 * 
 * Flow:
 * 1. Start UIRenderer
 * 2. Start RenderingService (server interface)
 * 3. Start ClientTerminalRenderManager (client coordinator)
 * 4. Create terminal handle through manager
 * 5. Create SystemApplication with the handle
 * 6. SystemApplication handles bootstrap, auth, IODaemon, screens
 */
public class SystemProcess extends FlowProcess {
    
    private final FlowProcessService processService;
    private final BitFlagStateMachine state;
    
    // Core components
    private final UIRenderer<?> uiRenderer;
    private RenderingService renderingService;
    private ClientTerminalRenderManager renderManager;
    
    // Terminal handle (UI container)
    private TerminalContainerHandle systemTerminalHandle;
    
    // Application logic (wraps the handle)
    private SystemApplication systemApplication;
    
    // Daemon mode flag
    private boolean daemonMode = false;
    
    // States
    public static final int INITIALIZING = 0;
    public static final int RENDERING_STARTING = 1;
    public static final int MANAGER_STARTING = 2;
    public static final int TERMINAL_CREATING = 3;
    public static final int APPLICATION_CREATING = 4;
    public static final int READY = 5;
    public static final int ERROR = 6;
    
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
        
        state.onStateAdded(APPLICATION_CREATING, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] APPLICATION_CREATING"));
        
        state.onStateAdded(READY, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] READY - System operational"));
    }

    FlowProcessService getProcessService(){
        return processService;
    }
    
    /**
     * Initialize system (standard mode - with terminal)
     * UIRenderer → RenderingService → ClientTerminalRenderManager → Ready
     */
    CompletableFuture<Void> initialize() {
        state.addState(INITIALIZING);
        this.daemonMode = false;
        
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
    
    /**
     * Initialize system in daemon mode (headless - no terminal initially)
     * UIRenderer → RenderingService → ClientTerminalRenderManager → Application → Ready
     * 
     * Application is created WITHOUT a terminal and can run headless
     */
    CompletableFuture<Void> initializeDaemon() {
        state.addState(INITIALIZING);
        this.daemonMode = true;
        
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
            .thenCompose(v -> {
                state.removeState(MANAGER_STARTING);
                state.addState(APPLICATION_CREATING);
                return createSystemApplicationHeadless();
            })
            .thenCompose(v -> {
                state.removeState(APPLICATION_CREATING);
                return systemApplication.initialize();
            })
            .thenRun(() -> {
                state.addState(READY);
                Log.logMsg("[SystemProcess] Daemon mode initialized (headless)");
            })
            .exceptionally(ex -> {
                Log.logError("[SystemProcess] Daemon init failed: " + ex.getMessage());
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
        renderManager = new ClientTerminalRenderManager("renderManager",
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
     * Create and open system terminal (standard mode)
     * Called by ConsoleApplication after system is ready
     * 
     * Creates:
     * 1. Terminal handle (UI container) through render manager
     * 2. SystemApplication (wraps handle with application logic)
     * 3. Initializes and opens the application
     */
    public CompletableFuture<Void> openSystemTerminal() {
        if (daemonMode) {
            // In daemon mode, use attach instead
            return attachLocalTerminal();
        }
        
        if (systemApplication == null) {
            Log.logMsg("[SystemProcess] Creating system terminal and application");
            
            return createSystemTerminal()
                .thenCompose(v -> createSystemApplication())
                .thenCompose(v -> systemApplication.initialize())
                .thenCompose(v -> systemApplication.open());
        }
        
        // Application exists, just open it
        Log.logMsg("[SystemProcess] Opening existing system application");
        return systemApplication.open();
    }
    
    /**
     * Attach local terminal to running daemon
     * Terminal is created and attached to existing application
     */
    public CompletableFuture<Void> attachLocalTerminal() {
        if (!daemonMode) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Not running in daemon mode"));
        }
        
        if (systemApplication == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Application not initialized"));
        }
        
        if (systemApplication.isTerminalAttached()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Terminal already attached"));
        }
        
        Log.logMsg("[SystemProcess] Attaching local terminal to daemon");
        
        // Create terminal if needed
        if (systemTerminalHandle == null) {
            return createSystemTerminal()
                .thenCompose(v -> systemApplication.attachTerminal(systemTerminalHandle, false));
        }
        
        return systemApplication.attachTerminal(systemTerminalHandle, false);
    }
    
    /**
     * Detach local terminal from daemon
     * Application continues running headless
     */
    public CompletableFuture<Void> detachLocalTerminal() {
        if (!daemonMode) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Not running in daemon mode"));
        }
        
        if (systemApplication == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[SystemProcess] Detaching local terminal from daemon");
        return systemApplication.detachTerminal();
    }
    
    /**
     * Attach remote client terminal to daemon
     * Creates a virtual terminal for the remote client
     * 
     * SECURITY: Only allows connection to already-configured systems.
     * Initial setup MUST be done via local terminal.
     * 
     * @param clientId Unique identifier for the remote client
     */
    public CompletableFuture<Void> attachRemoteClient(String clientId) {
        if (!daemonMode) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Not running in daemon mode"));
        }
        
        if (systemApplication == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Application not initialized"));
        }
        
        // CRITICAL: Verify system is configured before allowing remote access
        if (!SettingsData.isSettingsData()) {
            Log.logMsg("[SystemProcess] Remote client rejected - system not configured");
            return CompletableFuture.failedFuture(
                new SecurityException(
                    "System not configured. Initial setup must be performed via local terminal."));
        }
        
        if (systemApplication.isTerminalAttached()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Another terminal is already attached"));
        }
        
        Log.logMsg("[SystemProcess] Attaching remote client: " + clientId);
        
        // Create virtual terminal for remote client
        TerminalContainerHandle remoteHandle = renderManager.createTerminal(clientId)
            .title("Remote Client - " + clientId)
            .size(80, 24)
            .build();
        
        return remoteHandle.waitUntilReady()
            .thenCompose(v -> systemApplication.attachTerminal(remoteHandle, true));
    }
    
    /**
     * Detach remote client terminal
     * Application continues running headless
     */
    public CompletableFuture<Void> detachRemoteClient() {
        if (!daemonMode) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Not running in daemon mode"));
        }
        
        if (systemApplication == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        if (!systemApplication.isRemoteAttachment()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("No remote client attached"));
        }
        
        Log.logMsg("[SystemProcess] Detaching remote client");
        return systemApplication.detachTerminal();
    }
    
    /**
     * Create the terminal handle through render manager
     * 
     * Manager creates handle as its child and automatically:
     * - Registers it in the process hierarchy
     * - Starts the handle process
     * - Tracks it for rendering
     * - Polls it for render state
     */
    private CompletableFuture<Void> createSystemTerminal() {
        Log.logMsg("[SystemProcess] Creating system terminal handle through manager");
        
        state.addState(TERMINAL_CREATING);
        
        // Create terminal handle through manager (manager owns it)
        systemTerminalHandle = renderManager.createTerminal("system-terminal")
            .title("System Terminal")
            .size(80, 24)
            .autoFocus(true)
            .build();
        
        // Manager automatically started the terminal
        // Wait for it to be ready
        return systemTerminalHandle.waitUntilReady()
            .thenRun(() -> {
                state.removeState(TERMINAL_CREATING);
                Log.logMsg("[SystemProcess] Terminal handle ready: " + 
                    systemTerminalHandle.getContextPath());
                Log.logMsg("[SystemProcess] Terminal owned by render manager, " +
                    "automatic rendering active");
            });
    }
    
    /**
     * Create SystemApplication (wraps the terminal handle)
     * Standard mode - application created with terminal attached
     * 
     * SystemApplication provides:
     * - Bootstrap configuration management
     * - Authentication flow (first run, login, unlock)
     * - IODaemon management
     * - Screen navigation and lifecycle
     */
    private CompletableFuture<Void> createSystemApplication() {
        Log.logMsg("[SystemProcess] Creating SystemApplication with terminal");
        
        state.addState(APPLICATION_CREATING);
        
        // Create application WITH the terminal handle
        systemApplication = new SystemApplication(
            systemTerminalHandle,
            renderingService,
            registry,
            contextPath  // System's context path for session data
        );
        
        state.removeState(APPLICATION_CREATING);
        Log.logMsg("[SystemProcess] SystemApplication created (terminal attached)");
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Create SystemApplication in headless mode (daemon)
     * Application created WITHOUT a terminal
     */
    private CompletableFuture<Void> createSystemApplicationHeadless() {
        Log.logMsg("[SystemProcess] Creating SystemApplication (headless)");
        
        // Create application WITHOUT a terminal handle (null)
        systemApplication = new SystemApplication(
            null,  // No terminal in daemon mode
            renderingService,
            registry,
            contextPath
        );
        
        Log.logMsg("[SystemProcess] SystemApplication created (headless)");
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== GETTERS =====
    
    public TerminalContainerHandle getSystemTerminalHandle() {
        return systemTerminalHandle;
    }
    
    public SystemApplication getSystemApplication() {
        return systemApplication;
    }
    
    public ClientTerminalRenderManager getRenderManager() {
        return renderManager;
    }
    
    public boolean isReady() {
        return state.hasState(READY);
    }
    
    public boolean isDaemonMode() {
        return daemonMode;
    }
    
    public UIRenderer<?> getUIRenderer() {
        return uiRenderer;
    }
    
    public RenderingService getRenderingService() {
        return renderingService;
    }
}