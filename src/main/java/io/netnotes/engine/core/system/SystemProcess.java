package io.netnotes.engine.core.system;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.BootstrapWizardProcess;
import io.netnotes.engine.core.system.control.SecureInputInstaller;
import io.netnotes.engine.core.system.control.ServicesProcess;
import io.netnotes.engine.core.system.control.containers.*;
import io.netnotes.engine.core.system.control.ui.*;
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
import io.netnotes.engine.messaging.NoteMessaging.ErrorCodes;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.VirtualExecutors;

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
    private final KeyboardInput guiKeyboard;
    private final UIRenderer uiRenderer;
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
        KeyboardInput guiKeyboard,
        UIRenderer uiRenderer
    ) {
        super(CoreConstants.NAME, ProcessType.BIDIRECTIONAL);
        this.processService = processService;
        this.guiKeyboard = guiKeyboard;
        this.uiRenderer = uiRenderer;
        this.state = new BitFlagStateMachine(contextPath.toString());
        
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
        KeyboardInput guiKeyboard,
        UIRenderer uiRenderer
    ) {
        return CompletableFuture.supplyAsync(() -> {
            FlowProcessService flowProcessService = new FlowProcessService();
            ProcessRegistryInterface bootstrapInterface = 
                flowProcessService.getRegistryInterface();
            
            SystemProcess process = new SystemProcess(
                flowProcessService,
                guiKeyboard,
                uiRenderer
            );
            
            bootstrapInterface.registerChild(CoreConstants.SYSTEM_PATH, process);
            return process;
            
        }, VirtualExecutors.getVirtualExecutor())
            .thenCompose(process -> process.initialize()
                .thenApply(v -> process));
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(INITIALIZING, (old, now, bit) -> 
            System.out.println("[SystemProcess] INITIALIZING"));
        
        state.onStateAdded(BOOTSTRAP_NEEDED, (old, now, bit) -> 
            System.out.println("[SystemProcess] BOOTSTRAP_NEEDED"));
        
        state.onStateAdded(BOOTSTRAP_RUNNING, (old, now, bit) -> 
            System.out.println("[SystemProcess] BOOTSTRAP_RUNNING"));
        
        state.onStateAdded(SERVICES_STARTING, (old, now, bit) -> 
            System.out.println("[SystemProcess] SERVICES_STARTING"));
        
        state.onStateAdded(TERMINAL_CREATING, (old, now, bit) -> 
            System.out.println("[SystemProcess] TERMINAL_CREATING"));
        
        state.onStateAdded(READY, (old, now, bit) -> 
            System.out.println("[SystemProcess] READY - System operational"));
    }
    
    private CompletableFuture<Void> initialize() {
        state.addState(INITIALIZING);
        
        return startGUIKeyboard()
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
            .thenCompose(v -> {
                state.removeState(SERVICES_STARTING);
                state.addState(TERMINAL_CREATING);
                return createSystemTerminal();
            })
            .thenRun(() -> {
                state.removeState(TERMINAL_CREATING);
                state.addState(READY);
            })
            .exceptionally(ex -> {
                System.err.println("[SystemProcess] Init failed: " + ex.getMessage());
                state.addState(ERROR);
                return null;
            });
    }
    
    private CompletableFuture<Void> startGUIKeyboard() {
        return spawnChild(guiKeyboard)
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> System.out.println(
                "[SystemProcess] GUI keyboard: " + guiKeyboard.getContextPath()));
    }
    
    private CompletableFuture<Void> initializeUIRenderer() {
        return uiRenderer.initialize()
            .thenRun(() -> System.out.println(
                "[SystemProcess] UIRenderer: " + uiRenderer.getClass().getSimpleName()));
    }
    
    private CompletableFuture<Void> startContainerService() {
        containerService = new ContainerService(
            CoreConstants.CONTAINER_SERVICE, 
            uiRenderer
        );
        
        return spawnChild(containerService)
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> System.out.println(
                "[SystemProcess] ContainerService: " + containerService.getContextPath()));
    }
    
    private CompletableFuture<Boolean> checkBootstrapNeeded() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return !SettingsData.isBootstrapData();
            } catch (IOException e) {
                System.err.println("[SystemProcess] Bootstrap check error: " + 
                    e.getMessage());
                return true;
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    private CompletableFuture<Void> launchBootstrapWizard() {
        System.out.println("[SystemProcess] Launching bootstrap wizard");
        
        return createWizardContainer()
            .thenCompose(containerHandle -> {
                bootstrapWizard = new BootstrapWizardProcess(
                    "bootstrap-wizard",
                    containerHandle,
                    guiKeyboard
                );
                
                return spawnChild(bootstrapWizard)
                    .thenCompose(path -> startProcess(path))
                    .thenCompose(v -> bootstrapWizard.getCompletionFuture())
                    .thenRun(() -> {
                        unregisterProcess(bootstrapWizard.getContextPath());
                        bootstrapWizard = null;
                        state.removeState(BOOTSTRAP_RUNNING);
                        state.removeState(BOOTSTRAP_NEEDED);
                        System.out.println("[SystemProcess] Bootstrap wizard complete");
                    });
            });
    }
    
    private CompletableFuture<TerminalContainerHandle> createWizardContainer() {
        NoteBytesMap createMsg = ContainerProtocol.createContainer(
            "Bootstrap Wizard",
            ContainerType.TERMINAL,
            contextPath,
            new ContainerConfig()
                .withClosable(false)
                .withResizable(true)
        );
        
        return request(containerService.getContextPath(), 
                createMsg.toNoteBytesReadOnly(), 
                Duration.ofMillis(500))
            .thenApply(response -> {
                NoteBytesMap resp = response.getPayload().getAsNoteBytesMap();
                ContainerId containerId = ContainerId.fromNoteBytes(
                    resp.get(Keys.CONTAINER_ID)
                );
                
                return new TerminalContainerHandle(
                    containerId,
                    "Bootstrap Wizard",
                    containerService.getContextPath()
                );
            });
    }
    
    private CompletableFuture<Void> initializeBootstrapConfig() {
        System.out.println("[SystemProcess] Initializing BootstrapConfig");
        
        return BootstrapConfig.initialize()
            .thenCompose(config -> spawnChild(config)
                .thenCompose(path -> registry.startProcess(path))
                .thenRun(() -> System.out.println(
                    "[SystemProcess] BootstrapConfig: " + 
                    BootstrapConfig.BOOTSTRAP_CONFIG_PATH)));
    }
    
    private CompletableFuture<Void> startServicesProcess() {
        System.out.println("[SystemProcess] Starting ServicesProcess");
        
        servicesProcess = new ServicesProcess(containerService);
        
        return spawnChild(servicesProcess)
            .thenCompose(path -> registry.startProcess(path))
            .thenCompose(v -> {
                System.out.println("[SystemProcess] ServicesProcess: " + 
                    CoreConstants.SERVICES_PATH);
                
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
                System.out.println("[SystemProcess] IODaemon session: " + sessionPath);
                
                return systemClientSession.discoverDevices()
                    .thenRun(() -> {
                        System.out.println("[SystemProcess] Initial device discovery complete");
                    });
            });
    }
    

 
    /**
     * Create THE system terminal (singular, persistent)
     * 
     * Terminal handles:
     * - Authentication (first run + login + unlock)
     * - SystemRuntime creation
     * - All screens and menus
     */
    private CompletableFuture<Void> createSystemTerminal() {
        System.out.println("[SystemProcess] Creating system terminal");
        
        NoteBytesMap createMsg = ContainerProtocol.createContainer(
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
            .thenApply(response -> {
                NoteBytesMap resp = response.getPayload().getAsNoteBytesMap();
                ContainerId containerId = ContainerId.fromNoteBytes(
                    resp.get(Keys.CONTAINER_ID)
                );
                
                // Get best input device (secure if available)
                InputDevice terminalKeyboard = getBestInputDevice();
                
                // Create stateful terminal
                // Terminal will handle authentication and SystemRuntime creation
                systemTerminal = new SystemTerminalContainer(
                    containerId,
                    CoreConstants.SYSTEM_CONTAINER_NAME,
                    containerService.getContextPath(),
                    terminalKeyboard,
                    registry,
                    contextPath  // Session path for SystemRuntime
                );
                
                return systemTerminal;
            })
            .thenCompose(terminal -> spawnChild(terminal)
                .thenCompose(path -> registry.startProcess(path))
                .thenRun(() -> System.out.println(
                    "[SystemProcess] System terminal: " + 
                    systemTerminal.getContextPath())));
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
                        System.out.println("[SystemProcess] Using secure keyboard");
                        return claimedDevice;
                    }
                }
            }
        }
        
        // Fallback to GUI keyboard
        System.out.println("[SystemProcess] Using GUI keyboard");
        return guiKeyboard;
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
            
            System.err.println("[SystemProcess] Unknown command: " + cmdBytes);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException("SystemProcess does not handle streams");
    }
    
    private CompletableFuture<Void> handleOpenTerminal(RoutedPacket packet) {
        if (systemTerminal == null) {
            NoteBytesMap error = new NoteBytesMap();
            error.put(Keys.STATUS, ProtocolMesssages.ERROR);
            error.put(Keys.MSG, new NoteBytes("Terminal not initialized"));
            reply(packet, error.toNoteBytes());
            return CompletableFuture.completedFuture(null);
        }
        
        return systemTerminal.open()
            .thenRun(() -> {
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                response.put(Keys.PATH, new NoteBytes(
                    systemTerminal.getContextPath().toString()));
                reply(packet, response.toNoteBytes());
            });
    }
    
    private CompletableFuture<Void> handleGetSecureInputDevice(RoutedPacket packet) {
        InputDevice device = getBestInputDevice();
        if(device != null && device instanceof FlowProcess process){
            NoteBytesMap response = new NoteBytesMap();
            response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
            response.put(Keys.PATH, process.getContextPath());
            response.put(Keys.ITEM_TYPE, new NoteBytes(
                device == guiKeyboard ? "gui" : "secure"));
            
            reply(packet, response.toNoteBytes());
            return CompletableFuture.completedFuture(null);
        }else{
            return replyErrorCode(packet, ErrorCodes.UNKNOWN);
        }
    }

    public CompletableFuture<Void> replyErrorCode(RoutedPacket packet, int errorCode){
        reply(packet, 
            new NoteBytesPair(Keys.STATUS, ProtocolMesssages.FAILED),
            new NoteBytesPair(Keys.ERROR_CODE, errorCode));
        
        return CompletableFuture.completedFuture(null);
    }
    
    
    
    private CompletableFuture<Void> handleReconfigureBootstrap(RoutedPacket packet) {
        System.out.println("[SystemProcess] Reconfiguration requested");
        
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
        System.out.println("[SystemProcess] Secure input installation requested");
        
        String os = System.getProperty("os.name");
        
        return createWizardContainer()
            .thenCompose(terminal -> {
                SecureInputInstaller installer = new SecureInputInstaller(
                    "secure-input-installer",
                    os,
                    terminal,
                    guiKeyboard
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