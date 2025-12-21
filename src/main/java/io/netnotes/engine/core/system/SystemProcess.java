package io.netnotes.engine.core.system;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.BootstrapWizardProcess;
import io.netnotes.engine.core.system.control.ServicesProcess;
import io.netnotes.engine.core.system.control.containers.RenderingService;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.ClientSession;
import io.netnotes.engine.io.daemon.IODaemon;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.KeyboardInput;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.FlowProcessService;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolObjects;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SystemProcess - Bootstrap and service manager
 * 
 * Architecture:
 * /system/
 *   bootstrap-config/      # BootstrapConfig singleton
 *   services/              # ServicesProcess
 *     rendering-service/   # Rendering management
 *     io-daemon/           # Optional secure input
 *   system-terminal/       # System terminal path
 */
public class SystemProcess extends FlowProcess {
    
    public static final class SYSTEM_INIT_CMDS {
        public static final NoteBytesReadOnly GET_SECURE_INPUT_DEVICE = 
            new NoteBytesReadOnly("get_secure_input_device");
        public static final NoteBytesReadOnly RECONFIGURE_BOOTSTRAP = 
            new NoteBytesReadOnly("reconfigure_bootstrap");
        public static final NoteBytesReadOnly OPEN_TERMINAL = 
            new NoteBytesReadOnly("open_terminal");
    }

    private final FlowProcessService processService;
    private final BitFlagStateMachine state;
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_execMsgMap = 
        new ConcurrentHashMap<>();

    // Core components
    private final KeyboardInput defaultKeyboard;
    private final UIRenderer<?> uiRenderer;
    private RenderingService renderingService;
    private ServicesProcess servicesProcess;
    private ClientSession systemClientSession;

    BootstrapWizardProcess bootstrapWizard;
    private SystemTerminalContainer systemTerminal;
    

    // States
    public static final long INITIALIZING = 1L << 0;
    public static final long BOOTSTRAP_NEEDED = 1L << 1;
    public static final long BOOTSTRAP_RUNNING = 1L << 2;
    public static final long CONFIG_INITIALIZING = 1L << 3;
    public static final long SERVICES_STARTING = 1L << 4;
    public static final long TERMINAL_CREATING = 1L << 5;
    public static final long READY = 1L << 6;
    public static final long ERROR = 1L << 7;
    
    private SystemProcess(
        FlowProcessService processService,
        KeyboardInput defaultKeyboard,
        UIRenderer<?> uiRenderer
    ) {
        super(CoreConstants.SYSTEM, ProcessType.BIDIRECTIONAL);
        this.processService = processService;
        this.defaultKeyboard = defaultKeyboard;
        this.uiRenderer = uiRenderer;
        this.state = new BitFlagStateMachine(CoreConstants.SYSTEM + "-state");
        
        setupStateTransitions();
        setupMsgExecutorMap();
    }
    
    private void setupMsgExecutorMap() {
        m_execMsgMap.put(SYSTEM_INIT_CMDS.GET_SECURE_INPUT_DEVICE, 
            (msg, packet) -> handleGetSecureInputDevice(packet));
        m_execMsgMap.put(SYSTEM_INIT_CMDS.RECONFIGURE_BOOTSTRAP, 
            (msg, packet) -> handleReconfigureBootstrap(packet));
        m_execMsgMap.put(SYSTEM_INIT_CMDS.OPEN_TERMINAL, 
            (msg, packet) -> handleOpenTerminal(packet));
    }
    
    public static CompletableFuture<SystemProcess> bootstrap(
        KeyboardInput defaultKeyboard,
        UIRenderer<?> uiRendererInfo
    ) {
        FlowProcessService flowProcessService = new FlowProcessService();

        ProcessRegistryInterface bootstrapInterface = 
            flowProcessService.getRegistryInterface();
        
        SystemProcess process = new SystemProcess(
            flowProcessService,
            defaultKeyboard,
            uiRendererInfo
        );

        bootstrapInterface.registerProcess(process, CoreConstants.SYSTEM_PATH, null, bootstrapInterface);
     
        return process.initialize().
            thenApply(v -> {
                return process;
            });
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(INITIALIZING, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] INITIALIZING"));
        
        state.onStateAdded(BOOTSTRAP_NEEDED, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] BOOTSTRAP_NEEDED"));
        
        state.onStateAdded(BOOTSTRAP_RUNNING, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] BOOTSTRAP_RUNNING"));
        
        state.onStateAdded(SERVICES_STARTING, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] SERVICES_STARTING"));
        
        state.onStateAdded(TERMINAL_CREATING, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] TERMINAL_CREATING"));
        
        state.onStateAdded(READY, (old, now, bit) -> 
            Log.logMsg("[SystemProcess] READY - System operational"));
    }
    
    CompletableFuture<Void> initialize() {
        state.addState(INITIALIZING);

        return initializeUIRenderer()
            .thenCompose(v -> startDefaultKeyboard())
            .thenCompose(v -> startRenderingService())
            .thenCompose(v -> checkBootstrapNeeded())
            .thenCompose(wizardNeeded -> {
                if (wizardNeeded) {
                    state.addState(BOOTSTRAP_NEEDED);
                    state.addState(BOOTSTRAP_RUNNING);
                    return launchBootstrapWizard();
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenCompose(v -> {
                state.removeState(INITIALIZING);
                state.addState(CONFIG_INITIALIZING);
                return initializeBootstrapConfig();
            })
            .thenCompose(v -> {
                state.removeState(CONFIG_INITIALIZING);
                state.addState(SERVICES_STARTING);
                return startServicesProcess();
            })
            .thenRun(() -> {
                state.removeState(SERVICES_STARTING);
                state.addState(READY);
                
            })
            .exceptionally(ex -> {
                Log.logError("[SystemProcess] Init failed: " + ex.getMessage());
                state.addState(ERROR);
                return null;
            });
    }
    
    private CompletableFuture<Void> startDefaultKeyboard() {
        // Register keyboard as child
        registerChild(defaultKeyboard);
        ContextPath keyboardPath = defaultKeyboard.getContextPath();
        
        return startProcess(keyboardPath)
            .thenRun(() -> {
                // Keyboard only needs to send to system (one-way)
                registry.connect(keyboardPath, contextPath);
                
                Log.logMsg("[SystemProcess] native keyboard: " + keyboardPath);
            });
    }

    
    private CompletableFuture<Void> initializeUIRenderer() {
        Log.logMsg("[SystemProcess] initializing UIRenderer");
        return uiRenderer.initialize()
            .thenRun(() -> Log.logMsg(
                "[SystemProcess] UIRenderer: " + uiRenderer.getClass().getSimpleName()));
    }
    
    private CompletableFuture<Void> startRenderingService() {
        renderingService = new RenderingService(
            CoreConstants.RENDERING_SERVICE, 
            uiRenderer
        );
        
        // Register at explicit path
        ContextPath servicePath = registerChildAt(renderingService, CoreConstants.RENDERING_SERVICE_PATH);
        
        Log.logMsg("[SystemProcess] Starting RenderingService at: " + servicePath);
        
        // Start the service process (this calls run() which sets READY state)
        return startProcess(servicePath)
            .thenRun(() -> {
                Log.logMsg("[SystemProcess] RenderingService started, connecting streams...");
                
                // Now connect after service is running and ready
                registry.connect(contextPath, servicePath);  // system → rendering-service
                registry.connect(servicePath, contextPath);  // rendering-service → system
                
                // Verify
                verifyConnection(contextPath, servicePath, "system → rendering-service");
                verifyConnection(servicePath, contextPath, "rendering-service → system");
                
                Log.logMsg("[SystemProcess] RenderingService operational at: " + servicePath);
            });
    }

    private void verifyConnection(ContextPath from, ContextPath to, String label) {
        FlowProcess fromProcess = registry.getProcess(from);
        FlowProcess toProcess = registry.getProcess(to);
        
        Log.logMsg("[SystemProcess] === Verifying: " + label + " ===");
        Log.logMsg("  From: " + from);
        Log.logMsg("  From exists: " + (fromProcess != null));
        Log.logMsg("  From alive: " + (fromProcess != null && fromProcess.isAlive()));
        Log.logMsg("  From subscribers: " + (fromProcess != null ? fromProcess.getSubscriberCount() : "N/A"));
        
        Log.logMsg("  To: " + to);
        Log.logMsg("  To exists: " + (toProcess != null));
        Log.logMsg("  To alive: " + (toProcess != null && toProcess.isAlive()));
        Log.logMsg("  To subscribers: " + (toProcess != null ? toProcess.getSubscriberCount() : "N/A"));
    }
    
    private CompletableFuture<Boolean> checkBootstrapNeeded() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return !SettingsData.isBootstrapData();
            } catch (IOException e) {
                Log.logError("[SystemProcess] Bootstrap check error: " + 
                    e.getMessage());
                return true;
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    private CompletableFuture<Void> launchBootstrapWizard() {
        Log.logMsg("[SystemProcess] Launching bootstrap wizard");
        bootstrapWizard = new BootstrapWizardProcess("bootstrap-wizard", defaultKeyboard);
        
        // Register wizard as child
        registerChild(bootstrapWizard);
        ContextPath wizardPath = bootstrapWizard.getContextPath();
        
        return startProcess(wizardPath)
            .thenCompose(v -> bootstrapWizard.start())
            .thenCompose(v -> bootstrapWizard.getCompletionFuture())
            .thenRun(() -> {
                unregisterProcess(wizardPath);
                bootstrapWizard = null;
                state.removeState(BOOTSTRAP_RUNNING);
                state.removeState(BOOTSTRAP_NEEDED);
                Log.logMsg("[SystemProcess] Bootstrap wizard complete");
            });
    }
    
    private CompletableFuture<Void> initializeBootstrapConfig() {
        Log.logMsg("[SystemProcess] Initializing BootstrapConfig");
        
        return BootstrapConfig.initialize()
            .thenCompose(config -> {
                registerChild(config);
                return CompletableFuture.completedFuture(config.getContextPath());
            })
            .thenCompose(path -> startProcess(path))
            .thenRun(() -> {
                ContextPath path = BootstrapConfig.BOOTSTRAP_CONFIG_PATH;
                
                registry.connect(contextPath, path);
                registry.connect(path, contextPath);
                
                Log.logMsg("[SystemProcess] BootstrapConfig: " + path);
            });
    }
    
    private CompletableFuture<Void> startServicesProcess() {
        Log.logMsg("[SystemProcess] Starting ServicesProcess");
        
        servicesProcess = new ServicesProcess(renderingService);
        
        // Register services process as child
        registerChild(servicesProcess);
        ContextPath servicesPath = servicesProcess.getContextPath();
        
        return startProcess(servicesPath)
            .thenCompose(v -> {
                registry.connect(contextPath, servicesPath);
                registry.connect(servicesPath, contextPath);
                
                Log.logMsg("[SystemProcess] ServicesProcess: " + CoreConstants.SERVICES_PATH);
                
                if (servicesProcess.getIODaemon() != null) {
                    return createIOClientSession();
                }
                
                return CompletableFuture.completedFuture(null);
            });
    }
    
    private CompletableFuture<Void> createIOClientSession() {
        IODaemon ioDaemon = servicesProcess.getIODaemon();
        
        if (ioDaemon == null || !ioDaemon.isConnected()) {
            return CompletableFuture.completedFuture(null);
        }
        
        String sessionId = "io-session-" + NoteUUID.createSafeUUID128();
        int pid = (int) ProcessHandle.current().pid();
        
        return ioDaemon.createSession(sessionId, pid)
            .thenCompose(sessionPath -> {
                systemClientSession = (ClientSession) processService.getProcess(sessionPath);
                Log.logMsg("[SystemProcess] IODaemon session: " + sessionPath);
                
                return systemClientSession.discoverDevices()
                    .thenRun(() -> {
                        Log.logMsg("[SystemProcess] Initial device discovery complete");
                    });
            });
    }
    
    /**
     * Create THE system terminal (singular, persistent)
     * 
     * This is now called via handleOpenTerminal() command, not during init
     * 
     * Terminal handles:
     * - Authentication (first run + login + unlock)
     * - SystemRuntime creation
     * - All screens and menus
     */
    private CompletableFuture<Void> createSystemTerminal() {
        Log.logMsg("[SystemProcess] Creating system terminal");
        
        state.addState(TERMINAL_CREATING);
        
        systemTerminal = new SystemTerminalContainer(
            getBestInputDevice(),
            registry,
            contextPath
        );
        
        // Register terminal as child
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
    
    private InputDevice getBestInputDevice() {
        // Try to get secure keyboard from IODaemon
        if (systemClientSession != null) {
            var devices = systemClientSession.getDiscoveredDevices().getAllDevices();
            for (var device : devices) {
                if ("keyboard".equals(device.usbDevice().get_device_type()) && 
                    device.claimed()) {
                    
                    String deviceId = device.usbDevice().get_device_id();
                    var claimedDevice = systemClientSession.getClaimedDevice(deviceId);
                    if (claimedDevice != null && claimedDevice.isActive()) {
                        Log.logMsg("[SystemProcess] Using secure keyboard");
                        return claimedDevice;
                    }
                }
            }
        }
        
        // Fallback to GUI keyboard
        Log.logMsg("[SystemProcess] Using GUI keyboard");
        return defaultKeyboard;
    }
    
    // ===== MESSAGE HANDLERS =====
    
    @Override
    public CompletableFuture<Void> run() {
        return getCompletionFuture();
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        try {
            NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
            NoteBytes cmdBytes = msg.get(Keys.CMD);
            
            if (cmdBytes == null) {
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("'cmd' required"));
            }
            
            RoutedMessageExecutor msgExec = m_execMsgMap.get(cmdBytes);
            if (msgExec != null) {
                return msgExec.execute(msg, packet);
            }
            
            Log.logError("[SystemProcess] Unknown command: " + cmdBytes);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException("SystemProcess does not handle streams");
    }
    
    /**
     * Handle OPEN_TERMINAL command
     * 
     * Creates terminal on first call, opens it on subsequent calls
     */
    private CompletableFuture<Void> handleOpenTerminal(RoutedPacket packet) {
        // If terminal doesn't exist yet, create it first
        if (systemTerminal == null) {
            Log.logMsg("[SystemProcess] Creating terminal for first time");
            
            return createSystemTerminal()
                .thenCompose(v -> systemTerminal.open())
                .thenRun(() -> {
                    NoteBytesMap response = new NoteBytesMap();
                    response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                    response.put(Keys.PATH, systemTerminal.getContextPath());
                    reply(packet, response.toNoteBytes());
                })
                .exceptionally(ex -> {
                    Log.logError("[SystemProcess] Failed to create/open terminal: " + 
                        ex.getMessage());
                    
                    reply(packet, ProtocolObjects.getErrorObject("Failed to create terminal: " + ex.getMessage()));
                    return null;
                });
        }
        
        // Terminal already exists, just open it
        Log.logMsg("[SystemProcess] Opening existing terminal");
        
        return systemTerminal.open()
            .thenRun(() -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                response.put(Keys.PATH, systemTerminal.getContextPath());
                reply(packet, response.toNoteBytes());
            })
            .exceptionally(ex -> {
                Log.logError("[SystemProcess] Failed to open terminal: " + 
                    ex.getMessage());
                
                reply(packet, ProtocolObjects.getErrorObject("Failed to open terminal: " + ex.getMessage()));
                return null;
            });
    }
    
    private CompletableFuture<Void> handleGetSecureInputDevice(RoutedPacket packet) {
        InputDevice device = getBestInputDevice();
        if (device != null && device instanceof FlowProcess process) {
            NoteBytesMap response = new NoteBytesMap();
            response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
            response.put(Keys.PATH, process.getContextPath());
            response.put(Keys.ITEM_TYPE, new NoteBytes(
                device == defaultKeyboard ? "native" : "secure"));
            
            reply(packet, response.toNoteBytes());
            return CompletableFuture.completedFuture(null);
        } else {
            String msg = "No input devices available";
            reply(packet, ProtocolObjects.getErrorObject(msg));
            Log.logError("[SystemProcess.handleGetSecureInputDevice] " + msg);
            throw new IllegalStateException(msg);
        }
    }
    
    private CompletableFuture<Void> handleReconfigureBootstrap(RoutedPacket packet) {
        Log.logMsg("[SystemProcess] Reconfiguration requested");
        
        state.addState(BOOTSTRAP_RUNNING);
        
        return launchBootstrapWizard()
            .thenCompose(v -> restartServices())
            .thenRun(() -> {
                state.removeState(BOOTSTRAP_RUNNING);
                
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                reply(packet, response.toNoteBytes());
            });
    }
    
    private CompletableFuture<Void> restartServices() {
        if (servicesProcess != null) {
            return servicesProcess.shutdown()
                .thenCompose(v -> {
                    unregisterProcess(servicesProcess.getContextPath());
                    servicesProcess = null;
                    return startServicesProcess();
                });
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== GETTERS =====
    
    public SystemTerminalContainer getSystemTerminal() {
        return systemTerminal;
    }
    
    public boolean isReady() {
        return state.hasState(READY);
    }
}