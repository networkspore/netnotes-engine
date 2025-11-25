package io.netnotes.engine.core.system;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.BootstrapWizardProcess;
import io.netnotes.engine.core.system.control.SecureInputInstaller;
import io.netnotes.engine.core.system.control.ui.*;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.IODaemon;
import io.netnotes.engine.io.input.KeyboardInput;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BaseSystemProcess - Bootstrap and service manager
 * 
 * Key Changes:
 * - KeyboardInput injected via constructor (dependency injection)
 * - Caller provides GUI keyboard that's compatible with ClaimedDevice
 * - GitHub API integration for dynamic release selection
 * 
 * Architecture:
 * - GUI keyboard is ALWAYS available (injected)
 * - IODaemon is CONDITIONALLY started (if configured)
 * - Input device requests route intelligently
 */
public class BaseSystemProcess extends FlowProcess {
    
    private NoteBytesMap bootstrapConfig;
    private final Map<ContextPath, SystemSessionProcess> activeSessions = 
        new ConcurrentHashMap<>();
    
    // Services
    private IODaemon ioDaemon;
    private final KeyboardInput guiKeyboard; // Injected, always available
    
    // Bootstrap wizard (if needed)
    private BootstrapWizardProcess bootstrapWizard;
    
    /**
     * Private constructor - use factory method
     */
    private BaseSystemProcess(KeyboardInput guiKeyboard) {
        super(ProcessType.BIDIRECTIONAL);
        this.guiKeyboard = guiKeyboard;
        
        if (guiKeyboard == null) {
            throw new IllegalArgumentException("GUI keyboard required (null provided)");
        }
    }
    
    /**
     * Bootstrap entry point with injected GUI keyboard
     * 
     * @param guiKeyboard GUI keyboard input source (must be compatible with ClaimedDevice)
     * @return Initialized BaseSystemProcess
     */
    public static CompletableFuture<BaseSystemProcess> bootstrap(KeyboardInput guiKeyboard) {
        BaseSystemProcess process = new BaseSystemProcess(guiKeyboard);
        
        return process.initialize()
            .thenApply(v -> process);
    }
    
    private CompletableFuture<Void> initialize() {
        return registerSelfInRegistry()
            .thenCompose(v -> startGUIKeyboard()) // Start GUI keyboard first
            .thenCompose(v -> loadOrCreateBootstrapConfig())
            .thenCompose(config -> {
                this.bootstrapConfig = config;
                return startConfiguredServices();
            });
    }
    
    private CompletableFuture<Void> registerSelfInRegistry() {
        ContextPath basePath = ContextPath.of("system", "base");
        registry.registerProcess(this, basePath, null);
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Start GUI keyboard as child process (always available)
     */
    private CompletableFuture<Void> startGUIKeyboard() {
        return spawnChild(guiKeyboard, "gui-keyboard")
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> {
                System.out.println("GUI keyboard available at: " + guiKeyboard.getContextPath());
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
                return SettingsData.loadBootStrapConfig();
            } else {
                // Launch bootstrap wizard
                return launchBootstrapWizard();
            }
            
        } catch (IOException e) {
            System.err.println("Error checking bootstrap config: " + e.toString());
            // Launch wizard on error
            return launchBootstrapWizard();
        }
    }
    
    /**
     * Launch interactive bootstrap wizard
     */
    private CompletableFuture<NoteBytesMap> launchBootstrapWizard() {
        // Create a minimal UI renderer for wizard
        UIRenderer wizardUI = createWizardUIRenderer();
        
        bootstrapWizard = new BootstrapWizardProcess(wizardUI);
        
        return spawnChild(bootstrapWizard, "bootstrap-wizard")
            .thenCompose(path -> registry.startProcess(path))
            .thenCompose(v -> bootstrapWizard.getCompletionFuture())
            .thenApply(v -> {
                NoteBytesMap config = bootstrapWizard.getBootstrapConfig();
                
                // Clean up wizard
                registry.unregisterProcess(bootstrapWizard.getContextPath());
                bootstrapWizard = null;
                
                return config;
            });
    }
    
    private UIRenderer createWizardUIRenderer() {
        // TODO: In production, create proper GUI renderer
        // For now, return a simple console renderer
        return new ConsoleUIRenderer();
    }
    
    private CompletableFuture<Void> startConfiguredServices() {
        if (BootstrapConfig.isSecureInputInstalled(bootstrapConfig)) {
            return startIODaemon();
        }
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> startIODaemon() {
        String socketPath = BootstrapConfig.getSecureInputSocketPath(bootstrapConfig);
        ioDaemon = new IODaemon(socketPath);
        
        return spawnChild(ioDaemon, "io-daemon")
            .thenCompose(path -> registry.startProcess(path))
            .thenRun(() -> {
                System.out.println("IODaemon started at: " + socketPath);
            });
    }
    
    /**
     * Create a new system session (local or remote)
     */
    public CompletableFuture<ContextPath> createSession(
            String sessionId,
            SystemSessionProcess.SessionType type,
            UIRenderer uiRenderer) {
        
        SystemSessionProcess session = new SystemSessionProcess(
            sessionId, type, uiRenderer);
        
        ContextPath sessionPath = contextPath.append("sessions", sessionId);
        
        return CompletableFuture.supplyAsync(() -> {
            registry.registerProcess(session, sessionPath, contextPath);
            activeSessions.put(sessionPath, session);
            
            registry.startProcess(sessionPath);
            
            return sessionPath;
        });
    }
    
    @Override
    public CompletableFuture<Void> run() {
        return getCompletionFuture();
    }
    
    /**
     * Handle input device requests
     */
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        try {
            NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
            NoteBytes cmdBytes = msg.get(Keys.CMD);
            
            if (cmdBytes == null) {
                return CompletableFuture.completedFuture(null);
            }
            
            String cmd = cmdBytes.getAsString();
            
            switch (cmd) {
                case "get_secure_input_device":
                    return handleGetSecureInputDevice(packet);
                    
                case "reconfigure_bootstrap":
                    return handleReconfigureBootstrap(packet);
                    
                case "install_secure_input":
                    return handleInstallSecureInput(packet);
                    
                default:
                    System.err.println("Unknown command: " + cmd);
                    return CompletableFuture.completedFuture(null);
            }
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Route to appropriate input device
     * Priority: IODaemon (if available) > GUI Keyboard (always available)
     */
    private CompletableFuture<Void> handleGetSecureInputDevice(RoutedPacket packet) {
        InputDevice device = null;
        String deviceType = "gui"; // Default
        
        // Try IODaemon first (if installed and active)
        if (ioDaemon != null && ioDaemon.isConnected()) {
            // TODO: Get claimed keyboard from IODaemon
            // This requires IODaemon to have a method to get/claim a keyboard
            // For now, fall through to GUI keyboard
        }
        
        // Fallback to GUI keyboard (always available)
        if (device == null && guiKeyboard != null && guiKeyboard.isActive()) {
            device = guiKeyboard;
            deviceType = "gui";
        }
        
        // Build response
        NoteBytesMap response = new NoteBytesMap();
        
        if (device != null) {
            response.put(Keys.STATUS, new NoteBytes("success"));
            
            // Return the device's context path
            ContextPath devicePath = device instanceof FlowProcess 
                ? ((FlowProcess) device).getContextPath()
                : null;
            
            if (devicePath != null) {
                response.put(Keys.PATH, new NoteBytes(devicePath.toString()));
                response.put(Keys.ITEM_TYPE, new NoteBytes(deviceType));
            } else {
                response.put(Keys.STATUS, new NoteBytes("error"));
                response.put(Keys.MSG, new NoteBytes("Device path unavailable"));
            }
        } else {
            response.put(Keys.STATUS, new NoteBytes("error"));
            response.put(Keys.MSG, new NoteBytes("No input device available"));
        }
        
        reply(packet, response.getNoteBytesObject());
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Allow runtime reconfiguration
     */
    private CompletableFuture<Void> handleReconfigureBootstrap(RoutedPacket packet) {
        // Launch wizard again for reconfiguration
        return launchBootstrapWizard()
            .thenAccept(newConfig -> {
                this.bootstrapConfig = newConfig;
                
                // Restart services with new config
                restartServices();
                
                // Send success response
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, new NoteBytes("success"));
                reply(packet, response.getNoteBytesObject());
            })
            .exceptionally(ex -> {
                NoteBytesMap errorResponse = new NoteBytesMap();
                errorResponse.put(Keys.STATUS, new NoteBytes("error"));
                errorResponse.put(Keys.MSG, new NoteBytes(ex.getMessage()));
                reply(packet, errorResponse.getNoteBytesObject());
                return null;
            });
    }
    
    /**
     * Install secure input on demand
     */
    private CompletableFuture<Void> handleInstallSecureInput(RoutedPacket packet) {
        String os = System.getProperty("os.name");
        UIRenderer uiRenderer = createWizardUIRenderer();
        
        SecureInputInstaller installer = new SecureInputInstaller(os, uiRenderer);
        
        return spawnChild(installer, "installer")
            .thenCompose(path -> registry.startProcess(path))
            .thenCompose(v -> installer.getCompletionFuture())
            .thenApply((v) -> {
                // Update config
                BootstrapConfig.setSecureInputInstalled(bootstrapConfig, true);
             
                return SettingsData.saveBootstrapConfig(bootstrapConfig);
    
            })
            .thenCompose(v -> {
                // Start IODaemon
                return startIODaemon();
            })
            .thenAccept(v -> {
                // Send success
                NoteBytesMap response = new NoteBytesMap();
                response.put(Keys.STATUS, ProtocolMesssages.SUCCESS);
                reply(packet, response.getNoteBytesObject());
            })
            .exceptionally(ex -> {
                NoteBytesMap errorResponse = new NoteBytesMap();
                errorResponse.put(Keys.STATUS,  ProtocolMesssages.ERROR);
                errorResponse.put(Keys.MSG, new NoteBytes(ex.getMessage()));
                reply(packet, errorResponse.getNoteBytesObject());
                return null;
            });
    }
    
    private void restartServices() {
        // Stop old services
        if (ioDaemon != null) {
            ioDaemon.kill();
            registry.unregisterProcess(ioDaemon.getContextPath());
            ioDaemon = null;
        }
        
        // Start new services based on config
        startConfiguredServices();
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Get the GUI keyboard (for testing or direct access)
     */
    public KeyboardInput getGuiKeyboard() {
        return guiKeyboard;
    }
    
    /**
     * Get the IODaemon (if running)
     */
    public IODaemon getIODaemon() {
        return ioDaemon;
    }
    
    /**
     * Simple console UI renderer for wizard
     * (In production, use proper GUI renderer)
     */
    private static class ConsoleUIRenderer implements UIRenderer {
        
        @Override
        public CompletableFuture<NoteBytesMap> render(NoteBytesMap command) {
            NoteBytes cmdBytes = command.get(Keys.CMD);
            if (cmdBytes == null) {
                return CompletableFuture.completedFuture(new NoteBytesMap());
            }
            
            String cmd = cmdBytes.getAsString();
            
            // Simple console output
            switch (cmd) {
                case "show_message":
                    NoteBytes msg = command.get(Keys.MSG);
                    if (msg != null) {
                        System.out.println("\n" + msg.getAsString() + "\n");
                    }
                    break;
                    
                case "show_progress":
                    NoteBytes progressMsg = command.get(Keys.PROGRESS_MESSAGE);
                    NoteBytes percent = command.get(Keys.PROGRESS_PERCENT);
                    if (progressMsg != null && percent != null) {
                        System.out.printf("[%d%%] %s\n", 
                            percent.getAsInt(), 
                            progressMsg.getAsString());
                    }
                    break;
            }
            
            return CompletableFuture.completedFuture(new NoteBytesMap());
        }
        
        @Override
        public boolean isActive() {
            return true;
        }
        
        @Override
        public CompletableFuture<Void> initialize() {
            return CompletableFuture.completedFuture(null);
        }
        
        @Override
        public void shutdown() {
            // Nothing to clean up
        }
    }
}