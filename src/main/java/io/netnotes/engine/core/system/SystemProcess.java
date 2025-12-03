package io.netnotes.engine.core.system;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.BootstrapWizardProcess;
import io.netnotes.engine.core.system.control.SecureInputInstaller;
import io.netnotes.engine.core.system.control.ui.*;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.ClaimedDevice;
import io.netnotes.engine.io.daemon.ClientSession;
import io.netnotes.engine.io.daemon.IODaemon;
import io.netnotes.engine.io.input.KeyboardInput;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.FlowProcessService;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.VirtualExecutors;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SystemProcess - Bootstrap and service manager
 * 
 * Architecture:
 * - GUI keyboard is ALWAYS available (injected)
 * - IODaemon is CONDITIONALLY started (if configured)
 * - UIRenderer is injected (GUI, console, web, etc.)
 * - Input device requests route intelligently
 * 
 * States:
 * - INITIALIZING: Starting up, loading config
 * - BOOTSTRAP_NEEDED: No bootstrap config, wizard needed
 * - BOOTSTRAP_RUNNING: Wizard in progress
 * - SERVICES_STARTING: Starting configured services
 * - READY: Fully operational
 * - ERROR: Fatal error occurred
 */
public class SystemProcess extends FlowProcess {

    public static final class SYSTEM_INIT_CMDS{
        public static final NoteBytesReadOnly GET_SECURE_INPUT_DEVICE   = new NoteBytesReadOnly("get_secure_input_device");
        public static final NoteBytesReadOnly RECCONFIGURE_BOOTSTRAP    = new NoteBytesReadOnly("reconfigure_bootstrap");
        public static final NoteBytesReadOnly INSTALL_SECURE_INPUT      = new NoteBytesReadOnly("install_secure_input");
        public static final NoteBytesReadOnly GET_BOOSTRAP_CONFIG       = new NoteBytesReadOnly("get_bootstrap_config");
        public static final NoteBytesReadOnly UPDATE_BOOSTRAP_CONFIG    = new NoteBytesReadOnly("update_bootstrap_config");
    }

    public static final String NAME = "system";
    public static final String IO_DAEMON_NAME = "io-service";
    public static final String RUNTIME = "runtime";
    public static final String NODE_CONTROLLER = "node-controller";
    public static final String REPOSITORIES = "repositories";
    public static final String NODE_REGISTRY = "node-registry";
    public static final String NODE_LOADER = "node-loader";
    public static final String PACKAGE_STORE = "pkg-store";

    public static final String NODES = "nodes";
    public static final String CONTROLLERS = "controllers";

    public static final ContextPath SYSTEM_PATH = ContextPath.of(NAME);
    public static final ContextPath REPOSITORIES_PATH = SYSTEM_PATH.append(REPOSITORIES);

    public static final ContextPath SERVICES_PATH = SYSTEM_PATH.append("services");
    public static final ContextPath IO_SERVICE_PATH = SERVICES_PATH.append(IO_DAEMON_NAME);

    
    public static final ContextPath RUNTIME_PATH = SYSTEM_PATH.append(RUNTIME);

    public static final ContextPath NODES_PATH = RUNTIME_PATH.append(NODES);

    public static final ContextPath CONTROLLERS_PATH = RUNTIME_PATH.append(CONTROLLERS);
    public static final ContextPath NODE_CONTROLLER_PATH = CONTROLLERS_PATH.append(NODE_CONTROLLER);
    public static final ContextPath NODE_LOADER_PATH = CONTROLLERS_PATH.append(NODE_LOADER);
    public static final ContextPath NODE_REGISTRY_PATH = CONTROLLERS_PATH.append(NODE_REGISTRY);

    //DATA PATHS

    public static final ContextPath NODE_DATA_PATH = ContextPath.of("data");
    


    public static final ContextPath RUNTIME_DATA            = ContextPath.of("run");
    public static final ContextPath PACKAGE_STORE_PATH      = RUNTIME_DATA.append("pkg");
    public static final ContextPath REPOSITORIES_DATA_PATH  = RUNTIME_DATA.append(REPOSITORIES);
    public static final ContextPath NODE_LOADER_DATA_PATH   = RUNTIME_DATA.append(NODE_LOADER);
    public static final ContextPath NODE_REGISTRY_DATA_PATH = RUNTIME_DATA.append(NODE_REGISTRY);

    //public static final ContextPath PACKAGE_CONTROLLER_PATH = CONTROLLERS_PATH.append("package-controller");
    //public static final ContextPath SECURITY_CONTROLLER_PATH = CONTROLLERS_PATH.append("security-controller");


    private final FlowProcessService processService;
    private final BitFlagStateMachine state;
   // private final ProcessRegistryInterface bootstrapInterface;
    private NoteBytesMap bootstrapConfig;
    private final Map<ContextPath, SystemSessionProcess> activeSessions = new ConcurrentHashMap<>();
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_execMsgMap = new ConcurrentHashMap<>();

 
    // Services
    private IODaemon ioDaemon;
    private ClientSession systemClientSession; // Session for system's own device access
    private final KeyboardInput guiKeyboard; // Injected, always available
    private final UIRenderer uiRenderer; // Injected UI implementation
    
    // Bootstrap wizard (if needed)
    private BootstrapWizardProcess bootstrapWizard;
    
    // States
    public static final long INITIALIZING = 1L << 0;
    public static final long BOOTSTRAP_NEEDED = 1L << 1;
    public static final long BOOTSTRAP_RUNNING = 1L << 2;
    public static final long SERVICES_STARTING = 1L << 3;
    public static final long READY = 1L << 4;
    public static final long ERROR = 1L << 5;
    
    /**
     * Private constructor - use factory method
     */
    private SystemProcess(FlowProcessService processService, KeyboardInput guiKeyboard, UIRenderer uiRenderer) {
        super(NAME, ProcessType.BIDIRECTIONAL);
        this.guiKeyboard = guiKeyboard;
        this.uiRenderer = uiRenderer;
        this.processService = processService;
        

        this.state = new BitFlagStateMachine(contextPath.toString());
        
        if (guiKeyboard == null) {
            throw new IllegalArgumentException("GUI keyboard required (null provided)");
        }
        
        if (uiRenderer == null) {
            throw new IllegalArgumentException("UIRenderer required (null provided)");
        }
        
        setupStateTransitions();
        setupMsgExecutorMap();
    }
    private void setupMsgExecutorMap(){
        m_execMsgMap.put(SYSTEM_INIT_CMDS.GET_SECURE_INPUT_DEVICE, (msg, packet)-> handleGetSecureInputDevice(packet));
        m_execMsgMap.put(SYSTEM_INIT_CMDS.RECCONFIGURE_BOOTSTRAP, (msg, packet)-> handleReconfigureBootstrap(packet));
        m_execMsgMap.put(SYSTEM_INIT_CMDS.INSTALL_SECURE_INPUT, (msg, packet)-> handleInstallSecureInput(packet));
        m_execMsgMap.put(SYSTEM_INIT_CMDS.GET_BOOSTRAP_CONFIG, (msg, packet)-> handleGetBootstrapConfig(packet));
        m_execMsgMap.put(SYSTEM_INIT_CMDS.UPDATE_BOOSTRAP_CONFIG, (msg, packet)-> handleUpdateBootstrapConfig(packet));
    }
    
    /**
     * Bootstrap entry point with injected dependencies
     * 
     * @param guiKeyboard GUI keyboard input source (must be compatible with ClaimedDevice)
     * @param uiRenderer UI implementation (GUI, console, web, etc.)
     * @return Initialized SystemProcess
     */
    public static CompletableFuture<SystemProcess> bootstrap(
        KeyboardInput guiKeyboard,
        UIRenderer uiRenderer
    ) {
        return CompletableFuture.supplyAsync(() -> {
            // Synchronous construction
            FlowProcessService flowProcessService = new FlowProcessService();
            ProcessRegistryInterface bootstrapInterface = flowProcessService.getRegistryInterface();

            SystemProcess process = new SystemProcess(flowProcessService, guiKeyboard, uiRenderer);
            bootstrapInterface.registerChild(SYSTEM_PATH, process);

            return process;
        }, VirtualExecutors.getVirtualExecutor()) 
            .thenCompose(process ->process.initialize()
            .thenApply(v -> process));
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(INITIALIZING, (old, now, bit) -> {
            System.out.println("[BaseSystem] INITIALIZING - Starting up...");
        });
        
        state.onStateAdded(BOOTSTRAP_NEEDED, (old, now, bit) -> {
            System.out.println("[BaseSystem] BOOTSTRAP_NEEDED - First run detected");
        });
        
        state.onStateAdded(BOOTSTRAP_RUNNING, (old, now, bit) -> {
            System.out.println("[BaseSystem] BOOTSTRAP_RUNNING - Wizard active");
        });
        
        state.onStateAdded(SERVICES_STARTING, (old, now, bit) -> {
            System.out.println("[BaseSystem] SERVICES_STARTING - Starting configured services");
        });
        
        state.onStateAdded(READY, (old, now, bit) -> {
            System.out.println("[BaseSystem] READY - System operational");
        });
        
        state.onStateAdded(ERROR, (old, now, bit) -> {
            System.err.println("[BaseSystem] ERROR - Fatal error occurred");
        });
    }
    
    private CompletableFuture<Void> initialize() {
        state.addState(INITIALIZING);
        
        return startGUIKeyboard()
            .thenCompose(v -> initializeUIRenderer())
            .thenCompose(v -> loadOrCreateBootstrapConfig())
            .thenCompose(config -> {
                this.bootstrapConfig = config;
                state.removeState(INITIALIZING);
                state.addState(SERVICES_STARTING);
                return startConfiguredServices();
            })
            .thenRun(() -> {
                state.removeState(SERVICES_STARTING);
                state.addState(READY);
            })
            .exceptionally(ex -> {
                System.err.println("[BaseSystem] Initialization failed: " + ex.getMessage());
                state.removeState(INITIALIZING);
                state.removeState(SERVICES_STARTING);
                state.addState(ERROR);
                return null;
            });
    }
    
    /**
     * Start GUI keyboard as child process (always available)
     */
    private CompletableFuture<Void> startGUIKeyboard() {
        return spawnChild(guiKeyboard)
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> {
                System.out.println("[BaseSystem] GUI keyboard available at: " + 
                    guiKeyboard.getContextPath());
            });
    }
    
    /**
     * Initialize UI renderer
     */
    private CompletableFuture<Void> initializeUIRenderer() {
        return uiRenderer.initialize()
            .thenRun(() -> {
                System.out.println("[BaseSystem] UIRenderer initialized: " + 
                    uiRenderer.getClass().getSimpleName());
            });
    }
    
    /**
     * Interactive bootstrap configuration if needed
     */
    private CompletableFuture<NoteBytesMap> loadOrCreateBootstrapConfig() {
        try {
            boolean exists = SettingsData.isBootstrapData();
            
            if (exists) {
                // Load existing config
                System.out.println("[BaseSystem] Loading existing bootstrap config");
                return SettingsData.loadBootStrapConfig();
            } else {
                // Launch bootstrap wizard
                System.out.println("[BaseSystem] No bootstrap config found, launching wizard");
                state.removeState(INITIALIZING);
                state.addState(BOOTSTRAP_NEEDED);
                state.addState(BOOTSTRAP_RUNNING);
                return launchBootstrapWizard();
            }
            
        } catch (IOException e) {
            System.err.println("[BaseSystem] Error checking bootstrap config: " + e.toString());
            // Launch wizard on error
            state.addState(BOOTSTRAP_NEEDED);
            state.addState(BOOTSTRAP_RUNNING);
            return launchBootstrapWizard();
        }
    }
    
    /**
     * Launch interactive bootstrap wizard
     */
    private CompletableFuture<NoteBytesMap> launchBootstrapWizard() {
        bootstrapWizard = new BootstrapWizardProcess("bootstrap-wizard", uiRenderer);
        
        return spawnChild(bootstrapWizard)
            .thenCompose(path -> startProcess(path))
            .thenCompose(v -> bootstrapWizard.getCompletionFuture())
            .thenApply(v -> {
                NoteBytesMap config = bootstrapWizard.getBootstrapConfig();
                
                // Clean up wizard
                unregisterProcess(bootstrapWizard.getContextPath());
                bootstrapWizard = null;
                
                state.removeState(BOOTSTRAP_RUNNING);
                state.removeState(BOOTSTRAP_NEEDED);
                
                System.out.println("[BaseSystem] Bootstrap wizard completed");
                
                return config;
            });
    }
    
    private CompletableFuture<Void> startConfiguredServices() {
        if (BootstrapConfig.isSecureInputInstalled(bootstrapConfig)) {
            System.out.println("[BaseSystem] Starting IODaemon (secure input enabled)");
            return startIODaemon()
                .thenCompose(v -> createSystemSession());
        } else {
            System.out.println("[BaseSystem] Secure input not enabled, skipping IODaemon");
            return CompletableFuture.completedFuture(null);
        }
    }
    
    private CompletableFuture<Void> startIODaemon() {
        String socketPath = BootstrapConfig.getSecureInputSocketPath(bootstrapConfig);
        ioDaemon = new IODaemon(IO_DAEMON_NAME, socketPath);
        registry.registerChild(IO_SERVICE_PATH, ioDaemon);
        return startProcess(IO_SERVICE_PATH)
            .thenRun(() -> {
                System.out.println("[BaseSystem] IODaemon started at: " + socketPath);
            })
            .exceptionally(ex -> {
                System.err.println("[BaseSystem] Failed to start IODaemon: " + ex.getMessage());
                // Don't fail entire bootstrap, just log error
                return null;
            });
    }
    
    /**
     * Create system's own client session for accessing devices
     */
    private CompletableFuture<Void> createSystemSession() {
        if (ioDaemon == null || !ioDaemon.isConnected()) {
            System.err.println("[BaseSystem] Cannot create session - IODaemon not connected");
            return CompletableFuture.completedFuture(null);
        }
        
        String sessionId = IO_DAEMON_NAME + "-session-" + NoteUUID.createSafeUUID128();
        int pid = (int) ProcessHandle.current().pid();
        
        return ioDaemon.createSession(sessionId, pid)
            .thenCompose(sessionPath -> {
                systemClientSession = (ClientSession) processService.getProcess(sessionPath);
                System.out.println("[BaseSystem] System session created: " + sessionPath);
                
                // Discover devices immediately
                return systemClientSession.discoverDevices()
                    .thenRun(() -> {
                        System.out.println("[BaseSystem] Initial device discovery complete");
                    });
            })
            .exceptionally(ex -> {
                System.err.println("[BaseSystem] Failed to create system session: " + 
                    ex.getMessage());
                return null;
            });
    }
    
    /**
     * Create a new system session (local or remote)
     */
    public CompletableFuture<ContextPath> createSession(
            String sessionId,
            SystemSessionProcess.SessionType type,
            UIRenderer sessionUIRenderer) {
        
        SystemSessionProcess session = new SystemSessionProcess(
            sessionId, type, sessionUIRenderer);
        
        return CompletableFuture.supplyAsync(() -> {
            ContextPath sessionPath = registerChild(session);
            activeSessions.put(sessionPath, session);
            
            startProcess(sessionPath);
            
            System.out.println("[BaseSystem] Created session: " + sessionId + 
                " (type: " + type + ")");
            
            return sessionPath;
        });
    }
    
    @Override
    public CompletableFuture<Void> run() {
        return getCompletionFuture();
    }
    
 

    /**
     * Handle input device requests and system commands
     */
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        try {
            NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
            NoteBytes cmdBytes = msg.get(Keys.CMD);
            
            if (cmdBytes == null) {
                System.err.println("[BaseSystem] Received message without 'cmd' field");
                return CompletableFuture.completedFuture(null);
            }
            
            RoutedMessageExecutor msgExec = m_execMsgMap.get(cmdBytes);
            if(msgExec != null){
                return msgExec.execute(msg, packet);
            }else{
                System.err.println("[BaseSystem] Unknown command: " + cmdBytes);
                return CompletableFuture.completedFuture(null);
            }
  
        } catch (Exception e) {
            System.err.println("[BaseSystem] Error handling message: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Route to appropriate input device
     * Priority: IODaemon keyboards > GUI Keyboard (always available)
     * 
     * Flow:
     * 1. Check if IODaemon is available
     * 2. Get discovered keyboards from system session
     * 3. Claim first available keyboard (if not already claimed)
     * 4. Return ClaimedDevice path
     * 5. Fallback to GUI keyboard if no IODaemon
     */
    private CompletableFuture<Void> handleGetSecureInputDevice(RoutedPacket packet) {
        // Try IODaemon first (if installed and active)
        if (ioDaemon != null && ioDaemon.isConnected() && systemClientSession != null) {
            return getOrClaimKeyboard()
                .thenAccept(devicePath -> {
                    if (devicePath != null) {
                        // Got IODaemon keyboard
                        NoteBytesMap response = new NoteBytesMap();
                        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                        response.put(Keys.PATH, new NoteBytes(devicePath.toString()));
                        response.put(Keys.ITEM_TYPE, new NoteBytes("secure"));
                        
                        reply(packet, response.getNoteBytesObject());
                        System.out.println("[BaseSystem] Provided IODaemon keyboard: " + devicePath);
                    } else {
                        // Fall back to GUI keyboard
                        provideGUIKeyboard(packet);
                    }
                })
                .exceptionally(ex -> {
                    System.err.println("[BaseSystem] Error getting IODaemon keyboard: " + 
                        ex.getMessage());
                    // Fall back to GUI keyboard
                    provideGUIKeyboard(packet);
                    return null;
                });
        } else {
            // No IODaemon available, use GUI keyboard
            provideGUIKeyboard(packet);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * Get or claim a keyboard from IODaemon
     * Returns null if no keyboards available
     */
    private CompletableFuture<ContextPath> getOrClaimKeyboard() {
        // Check if we already have a claimed keyboard
        var discoveredDevices = systemClientSession.getDiscoveredDevices();
        var allDevices = discoveredDevices.getAllDevices();
        
        // Look for already claimed keyboard
        for (var deviceInfo : allDevices) {
            if ("keyboard".equals(deviceInfo.usbDevice().get_device_type()) && 
                deviceInfo.claimed()) {
                
                String deviceId = deviceInfo.usbDevice().get_device_id();
                ClaimedDevice claimedDevice = systemClientSession.getClaimedDevice(deviceId);
                if (claimedDevice != null && claimedDevice.isActive()) {
                    System.out.println("[BaseSystem] Reusing claimed keyboard: " + deviceId);
                    return CompletableFuture.completedFuture(claimedDevice.getDevicePath());
                }
            }
        }
        
        // No claimed keyboard, find and claim first available
        for (var deviceInfo : allDevices) {
            if ("keyboard".equals(deviceInfo.usbDevice().get_device_type()) && 
                !deviceInfo.claimed()) {
                
                String deviceId = deviceInfo.usbDevice().get_device_id();
                System.out.println("[BaseSystem] Claiming keyboard: " + deviceId);
                
                return systemClientSession.claimDevice(deviceId, "standard")
                    .thenApply(devicePath -> {
                        System.out.println("[BaseSystem] Claimed keyboard at: " + devicePath);
                        return devicePath;
                    });
            }
        }
        
        // No keyboards available
        System.err.println("[BaseSystem] No keyboards available from IODaemon");
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Provide GUI keyboard as fallback
     */
    private void provideGUIKeyboard(RoutedPacket packet) {
        if (guiKeyboard != null && guiKeyboard.isActive()) {
            ContextPath devicePath = guiKeyboard.getContextPath();
            
            NoteBytesMap response = new NoteBytesMap();
            response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
            response.put(Keys.PATH, new NoteBytes(devicePath.toString()));
            response.put(Keys.ITEM_TYPE, new NoteBytes("gui"));
            
            reply(packet, response.getNoteBytesObject());
            System.out.println("[BaseSystem] Provided GUI keyboard: " + devicePath);
        } else {
            // No input device available at all!
            NoteBytesMap response = new NoteBytesMap();
            response.put(Keys.STATUS, ProtocolMesssages.ERROR);
            response.put(Keys.MSG, new NoteBytes("No input device available"));
            
            reply(packet, response.getNoteBytesObject());
            System.err.println("[BaseSystem] No input device available!");
        }
    }
    
    /**
     * Allow runtime reconfiguration
     */
    private CompletableFuture<Void> handleReconfigureBootstrap(RoutedPacket packet) {
        System.out.println("[BaseSystem] Reconfiguration requested");
        
        // Launch wizard again for reconfiguration
        state.addState(BOOTSTRAP_RUNNING);
        
        return launchBootstrapWizard()
            .thenCompose(newConfig -> {
                this.bootstrapConfig = newConfig;
                
                System.out.println("[BaseSystem] Reconfiguration complete, restarting services");
                
                // Restart services with new config
                return restartServices();
            })
            .thenRun(() -> {
                state.removeState(BOOTSTRAP_RUNNING);
                
                // Send success response
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                reply(packet, response.getNoteBytesObject());
                
                System.out.println("[BaseSystem] Reconfiguration successful");
            })
            .exceptionally(ex -> {
                state.removeState(BOOTSTRAP_RUNNING);
                
                NoteBytesMap errorResponse = new NoteBytesMap();
                errorResponse.put(Keys.STATUS, ProtocolMesssages.ERROR);
                errorResponse.put(Keys.MSG, new NoteBytes(ex.getMessage()));
                reply(packet, errorResponse.getNoteBytesObject());
                
                System.err.println("[BaseSystem] Reconfiguration failed: " + ex.getMessage());
                return null;
            });
    }
    
    /**
     * Install secure input on demand
     */
    private CompletableFuture<Void> handleInstallSecureInput(RoutedPacket packet) {
        System.out.println("[BaseSystem] Secure input installation requested");
        
        String os = System.getProperty("os.name");
        SecureInputInstaller installer = new SecureInputInstaller("secure-input-installer", os, uiRenderer);
        
        return spawnChild(installer)
            .thenCompose(path -> startProcess(installer.getContextPath()))
            .thenCompose(v -> installer.getCompletionFuture())
            .thenCompose(v -> {
                if (!installer.isComplete()) {
                    throw new RuntimeException("Installation failed or cancelled");
                }
                
                // Update config
                BootstrapConfig.setSecureInputInstalled(bootstrapConfig, true);
                return SettingsData.saveBootstrapConfig(bootstrapConfig);
            })
            .thenCompose(v -> {
                // Start IODaemon
                System.out.println("[BaseSystem] Starting IODaemon after installation");
                return startIODaemon();
            })
            .thenRun(() -> {
                // Send success
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                reply(packet, response.getNoteBytesObject());
                
                System.out.println("[BaseSystem] Secure input installation successful");
            })
            .exceptionally(ex -> {
                NoteBytesMap errorResponse = new NoteBytesMap();
                errorResponse.put(Keys.STATUS, ProtocolMesssages.ERROR);
                errorResponse.put(Keys.MSG, new NoteBytes(ex.getMessage()));
                reply(packet, errorResponse.getNoteBytesObject());
                
                System.err.println("[BaseSystem] Secure input installation failed: " + 
                    ex.getMessage());
                return null;
            });
    }
    
    /**
     * Get current bootstrap configuration
     */
    private CompletableFuture<Void> handleGetBootstrapConfig(RoutedPacket packet) {
        NoteBytesMap response = new NoteBytesMap();
        response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
        response.put(Keys.DATA, bootstrapConfig);
        
        reply(packet, response.getNoteBytesObject());
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Update bootstrap configuration
     */
    private CompletableFuture<Void> handleUpdateBootstrapConfig(RoutedPacket packet) {
        try {
            NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
            NoteBytes updatesBytes = msg.get(Keys.DATA);
            
            if (updatesBytes == null) {
                NoteBytesMap errorResponse = new NoteBytesMap();
                errorResponse.put(Keys.STATUS, ProtocolMesssages.ERROR);
                errorResponse.put(Keys.MSG, new NoteBytes("No update data provided"));
                reply(packet, errorResponse.getNoteBytesObject());
                return CompletableFuture.completedFuture(null);
            }
            
            NoteBytesMap updates = updatesBytes.getAsNoteBytesMap();
            BootstrapConfig.merge(bootstrapConfig, updates);
            
            return SettingsData.saveBootstrapConfig(bootstrapConfig)
                .thenRun(() -> {
                    NoteBytesMap response = new NoteBytesMap();
                    response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                    reply(packet, response.getNoteBytesObject());
                    
                    System.out.println("[BaseSystem] Bootstrap config updated");
                });
            
        } catch (Exception e) {
            NoteBytesMap errorResponse = new NoteBytesMap();
            errorResponse.put(Keys.STATUS, ProtocolMesssages.ERROR);
            errorResponse.put(Keys.MSG, new NoteBytes(e.getMessage()));
            reply(packet, errorResponse.getNoteBytesObject());
            
            return CompletableFuture.completedFuture(null);
        }
    }
    
    private CompletableFuture<Void> restartServices() {
        // Release any claimed devices first
        if (systemClientSession != null) {
            var discoveredDevices = systemClientSession.getDiscoveredDevices();
            var allDevices = discoveredDevices.getAllDevices();
            
            for (var deviceInfo : allDevices) {
                if (deviceInfo.claimed()) {
                    String deviceId = deviceInfo.usbDevice().get_device_id();
                    systemClientSession.releaseDevice(deviceId)
                        .exceptionally(ex -> {
                            System.err.println("[BaseSystem] Error releasing device: " + 
                                ex.getMessage());
                            return null;
                        });
                }
            }
            
            systemClientSession = null;
        }
        
        // Stop IODaemon
        if (ioDaemon != null) {
            System.out.println("[BaseSystem] Stopping IODaemon");
            ioDaemon.kill();
            unregisterProcess(ioDaemon.getContextPath());
            ioDaemon = null;
        }
        
        // Start new services based on config
        return startConfiguredServices();
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException("BaseSystem does not handle streams");
    }
    
    // ===== GETTERS =====
    
    public BitFlagStateMachine getState() {
        return state;
    }
    
    public KeyboardInput getGuiKeyboard() {
        return guiKeyboard;
    }
    
    public IODaemon getIODaemon() {
        return ioDaemon;
    }
    
    public UIRenderer getUIRenderer() {
        return uiRenderer;
    }
    
    public NoteBytesMap getBootstrapConfig() {
        return bootstrapConfig;
    }
    
    public boolean isReady() {
        return state.hasState(READY);
    }
    
    public boolean hasSecureInput() {
        return ioDaemon != null && ioDaemon.isConnected();
    }
    
    public Map<ContextPath, SystemSessionProcess> getActiveSessions() {
        return activeSessions;
    }
    
    public ClientSession getSystemClientSession() {
        return systemClientSession;
    }
    
    public Map<String, ClaimedDevice> getClaimedDevices() {
        Map<String, ClaimedDevice> devices = new ConcurrentHashMap<>();
        
        if (systemClientSession != null) {
            var discoveredDevices = systemClientSession.getDiscoveredDevices();
            var allDevices = discoveredDevices.getAllDevices();
            
            for (var deviceInfo : allDevices) {
                if (deviceInfo.claimed()) {
                    String deviceId = deviceInfo.usbDevice().get_device_id();
                    ClaimedDevice claimedDevice = systemClientSession.getClaimedDevice(deviceId);
                    if (claimedDevice != null) {
                        devices.put(deviceId, claimedDevice);
                    }
                }
            }
        }
        
        return devices;
    }
}