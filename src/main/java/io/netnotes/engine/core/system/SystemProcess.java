package io.netnotes.engine.core.system;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.BootstrapWizardProcess;
import io.netnotes.engine.core.system.control.SecureInputInstaller;
import io.netnotes.engine.core.system.control.ServicesProcess;
import io.netnotes.engine.core.system.control.containers.*;
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
import io.netnotes.engine.utils.VirtualExecutors;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SystemProcess - Bootstrap and service manager
 * 
 * SIMPLIFIED: One SystemTerminalContainer manages everything
 * 
 * Architecture:
 * /system/
 *   bootstrap-config/      # BootstrapConfig singleton
 *   services/              # ServicesProcess
 *     container-service/   # Container management
 *     io-daemon/           # Optional secure input
 *   system-terminal/       # THE terminal (handles auth, screens, SystemRuntime)
 */
public class SystemProcess extends FlowProcess {
    
    public static final class SYSTEM_INIT_CMDS {
        public static final NoteBytesReadOnly GET_SECURE_INPUT_DEVICE = 
            new NoteBytesReadOnly("get_secure_input_device");
        public static final NoteBytesReadOnly RECONFIGURE_BOOTSTRAP = 
            new NoteBytesReadOnly("reconfigure_bootstrap");
        public static final NoteBytesReadOnly INSTALL_SECURE_INPUT = 
            new NoteBytesReadOnly("install_secure_input");
        public static final NoteBytesReadOnly OPEN_TERMINAL = 
            new NoteBytesReadOnly("open_terminal");
    }

    private final FlowProcessService processService;
    private final BitFlagStateMachine state;
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_execMsgMap = 
        new ConcurrentHashMap<>();

    // Core components
    private final KeyboardInput defaultKeyboard;
    private final RendererInfo uiRendererInfo;
    private ContainerService containerService;
    private ServicesProcess servicesProcess;
    private ClientSession systemClientSession;
    
    // THE system terminal (singular, persistent)
    private SystemTerminalContainer systemTerminal;
    
    // Bootstrap wizard (temporary)
    private BootstrapWizardProcess bootstrapWizard;
    
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
        RendererInfo uiRendererInfo
    ) {
        super(CoreConstants.SYSTEM, ProcessType.BIDIRECTIONAL);
        this.processService = processService;
        this.defaultKeyboard = defaultKeyboard;
        this.uiRendererInfo = uiRendererInfo;
        this.state = new BitFlagStateMachine(CoreConstants.SYSTEM + "-state");
        
        setupStateTransitions();
        setupMsgExecutorMap();
    }
    
    private void setupMsgExecutorMap() {
        m_execMsgMap.put(SYSTEM_INIT_CMDS.GET_SECURE_INPUT_DEVICE, 
            (msg, packet) -> handleGetSecureInputDevice(packet));
        m_execMsgMap.put(SYSTEM_INIT_CMDS.RECONFIGURE_BOOTSTRAP, 
            (msg, packet) -> handleReconfigureBootstrap(packet));
        m_execMsgMap.put(SYSTEM_INIT_CMDS.INSTALL_SECURE_INPUT, 
            (msg, packet) -> handleInstallSecureInput(packet));
        m_execMsgMap.put(SYSTEM_INIT_CMDS.OPEN_TERMINAL, 
            (msg, packet) -> handleOpenTerminal(packet));
    }
    
    public static CompletableFuture<SystemProcess> bootstrap(
        KeyboardInput defaultKeyboard,
        RendererInfo uiRendererInfo
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
            thenApply(v->{
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

        return startDefaultKeyboard()
            .thenCompose(v -> initializeUIRenderer())
            .thenCompose(v -> startContainerService())
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
                // FIXED: Set READY here - system can now accept commands
                state.removeState(SERVICES_STARTING);
                state.addState(READY);
                
            })
            // Terminal creation is no longer part of initialization
            // It happens via OPEN_TERMINAL command after system is ready
            .exceptionally(ex -> {
                Log.logError("[SystemProcess] Init failed: " + ex.getMessage());
                state.addState(ERROR);
                return null;
            });
    }
    
    private CompletableFuture<Void> startDefaultKeyboard() {
        return spawnChild(defaultKeyboard)
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> {
                ContextPath path = defaultKeyboard.getContextPath();
                
                // Keyboard only needs to send to system (one-way)
                registry.connect(path, contextPath);
                
                Log.logMsg("[SystemProcess] native keyboard: " + path);
            });
    }

    
    private CompletableFuture<Void> initializeUIRenderer() {
        Log.logMsg( "[SystemProcess] initializing UIRenderer");
        return uiRendererInfo.getRenderer().initialize()
            .thenRun(() -> Log.logMsg(
                "[SystemProcess] UIRenderer: " + uiRendererInfo.getRenderer().getClass().getSimpleName()));
    }
    
    private CompletableFuture<Void> startContainerService() {
       

        containerService = new ContainerService(
            CoreConstants.CONTAINER_SERVICE, 
            uiRendererInfo
        );
        
        return spawnChild(containerService)
            .thenCompose(path -> {
                // START the process first, THEN connect
                return registry.startProcess(path);
            })
            .thenRun(() -> {
                ContextPath path = containerService.getContextPath();
                
                Log.logMsg("[SystemProcess] Connecting streams with ContainerService (after start)...");
                
                // Now connect after both are running
                registry.connect(contextPath, path);  // system → container-service
                registry.connect(path, contextPath);  // container-service → system
                
                // Verify
                verifyConnection(contextPath, path, "system → container-service");
                verifyConnection(path, contextPath, "container-service → system");
                
                Log.logMsg("[SystemProcess] ContainerService: " + containerService.getContextPath());
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
        
        return createWizardContainer()
            .thenCompose(containerHandle -> {
                  Log.logMsg("[SystemProcess.launchBootstrapWizard] instantiating BootstrapWizardProcess");
                bootstrapWizard = new BootstrapWizardProcess(
                    "bootstrap-wizard",
                    containerHandle,
                    defaultKeyboard
                );
                Log.logMsg("[SystemProcess.launchBootstrapWizard] BootstrapWizardProcess instantiated");
                return spawnChild(bootstrapWizard);
            })
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> {
                ContextPath path = bootstrapWizard.getContextPath();
                
                registry.connect(contextPath, path);
                registry.connect(path, contextPath);
            })
            .thenCompose(v -> bootstrapWizard.start())
            .thenCompose(v -> bootstrapWizard.getCompletionFuture())
            .thenRun(() -> {
                unregisterProcess(bootstrapWizard.getContextPath());
                bootstrapWizard = null;
                state.removeState(BOOTSTRAP_RUNNING);
                state.removeState(BOOTSTRAP_NEEDED);
                Log.logMsg("[SystemProcess] Bootstrap wizard complete");
            });
    }
    
    private CompletableFuture<TerminalContainerHandle> createWizardContainer() {
        NoteBytesReadOnly createMsg = ContainerCommands.createAndFocusContainer(
            "Bootstrap Wizard",
            ContainerType.TERMINAL,
            contextPath,
            new ContainerConfig()
                .withClosable(false)
                .withResizable(true)
        ).toNoteBytesReadOnly();
        
        Log.logMsg("[SystemProcess.createWizardContainer] creating container");
        return request(containerService.getContextPath(), 
                createMsg, 
                Duration.ofMillis(500))
            .thenCompose(response -> {
                NoteBytesMap resp = response.getPayload().getAsNoteBytesMap();
                ContainerId containerId = ContainerId.fromNoteBytes(
                    resp.get(Keys.CONTAINER_ID)
                );
                Log.logMsg("[SystemProcess.createWizardContainer] creating handle:" + containerId);
                
                TerminalContainerHandle handle = new TerminalContainerHandle(
                    containerId,
                    "bootstrap-wizard-container",
                    containerService.getContextPath()
                );
                
                // Register and start the handle
                return spawnChild(handle)
                    .thenCompose(path -> registry.startProcess(path))
                    .thenApply(v -> {
                        Log.logMsg("[SystemProcess.createWizardContainer] handle started, returning");
                        // Don't wait here - let terminal operations wait as needed
                        return handle;
                    });
            });
    }
    
    private CompletableFuture<Void> initializeBootstrapConfig() {
        Log.logMsg("[SystemProcess] Initializing BootstrapConfig");
        
        return BootstrapConfig.initialize()
            .thenCompose(config -> spawnChild(config))
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> {
                ContextPath path = BootstrapConfig.BOOTSTRAP_CONFIG_PATH;
                
                registry.connect(contextPath, path);
                registry.connect(path, contextPath);
                
                Log.logMsg("[SystemProcess] BootstrapConfig: " + path);
            });
    }
    
    private CompletableFuture<Void> startServicesProcess() {
        Log.logMsg("[SystemProcess] Starting ServicesProcess");
        
        servicesProcess = new ServicesProcess(containerService);
        
        return spawnChild(servicesProcess)
            .thenCompose(path -> registry.startProcess(path))
            .thenCompose(v -> {
                ContextPath path = servicesProcess.getContextPath();
                
                registry.connect(contextPath, path);
                registry.connect(path, contextPath);
                
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
        
        NoteBytesMap createMsg = ContainerCommands.createContainer(
            CoreConstants.SYSTEM_CONTAINER_NAME,
            ContainerType.TERMINAL,
            contextPath,
            new ContainerConfig()
                .withClosable(true)
                .withResizable(true)
        );
        
        return request(containerService.getContextPath(),
                createMsg.toNoteBytesReadOnly(),
                Duration.ofMillis(500))
            .thenCompose(response -> {
                NoteBytesMap resp = response.getPayload().getAsNoteBytesMap();
                ContainerId containerId = ContainerId.fromNoteBytes(
                    resp.get(Keys.CONTAINER_ID)
                );
                
                InputDevice terminalKeyboard = getBestInputDevice();
                
                systemTerminal = new SystemTerminalContainer(
                    containerId,
                    "system-terminal-container",  // Process name
                    containerService.getContextPath(),
                    terminalKeyboard,
                    registry,
                    contextPath
                );
                
                // Register and start the handle
                return spawnChild(systemTerminal);
            })
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> {
                ContextPath path = systemTerminal.getContextPath();
                
                registry.connect(contextPath, path);
                registry.connect(path, contextPath);
                
                state.removeState(TERMINAL_CREATING);
                Log.logMsg("[SystemProcess] System terminal created: " + path);
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
            Log.logNoteBytes("[SystemProcess.handleMessage] from:"+ packet.getSourcePath() +"\n", packet.getPayload());
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
        if(device != null && device instanceof FlowProcess process){
            NoteBytesMap response = new NoteBytesMap();
            response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
            response.put(Keys.PATH, process.getContextPath());
            response.put(Keys.ITEM_TYPE, new NoteBytes(
                device == defaultKeyboard ? "native" : "secure"));
            
            reply(packet, response.toNoteBytes());
            return CompletableFuture.completedFuture(null);
        }else{
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
    
    private CompletableFuture<Void> handleInstallSecureInput(RoutedPacket packet) {
        Log.logMsg("[SystemProcess] Secure input installation requested");
        
        String os = System.getProperty("os.name");
        
        return createWizardContainer()
            .thenCompose(terminal -> {
       
                SecureInputInstaller installer = new SecureInputInstaller(
                    "secure-input-installer",
                    os,
                    terminal,
                    defaultKeyboard
                );
                return spawnChild(installer)
                    .thenCompose(path -> startProcess(path))
                    .thenCompose(v -> installer.getCompletionFuture())
                    .thenCompose(v -> {
                        if (!installer.isComplete()) {
                            throw new RuntimeException("Installation failed");
                        }
                        
                        // Update config
                        NoteBytesMap setCmd = BootstrapConfig.CMD.set(
                            BootstrapConfig.SYSTEM + "/" + BootstrapConfig.BASE + "/" +
                            BootstrapConfig.SECURE_INPUT + "/" + BootstrapConfig.INSTALLED,
                            new NoteBytes(true)
                        );
                        
                        return request(BootstrapConfig.BOOTSTRAP_CONFIG_PATH,
                            setCmd.toNoteBytes(),
                            Duration.ofMillis(500));
                    });
            })
            .thenCompose(v -> restartServices())
            .thenRun(() -> {
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