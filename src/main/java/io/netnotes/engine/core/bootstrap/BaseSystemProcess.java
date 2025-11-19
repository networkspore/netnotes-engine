package io.netnotes.engine.core.bootstrap;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.IODaemon;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteFiles.FileStreamUtils;
import io.netnotes.engine.utils.JarHelpers;
import io.netnotes.engine.utils.VirtualExecutors;

public class BaseSystemProcess extends FlowProcess {
    
    // === Configuration ===
    private NoteBytesMap bootstrapConfig;
    
    // === UI ===
    private final BootstrapUI ui;
    
    // === Child Services ===
    private final Map<String, FlowProcess> managedServices = new ConcurrentHashMap<>();
    
    /**
     * Private constructor - use bootstrap() factory
     */
    private BaseSystemProcess(BootstrapUI ui) {
        super(ProcessType.BIDIRECTIONAL);
        this.ui = ui;
    }
    
    /**
     * MAIN ENTRY POINT: Bootstrap the system
     * 
     * This is the ONLY way to create BaseSystemProcess.
     * Returns a fully initialized, running system.
     */
    public static CompletableFuture<BaseSystemProcess> bootstrap(BootstrapUI ui) {
        BaseSystemProcess process = new BaseSystemProcess(ui);
        
        return process.initialize()
            .thenApply(v -> process);
    }
    
    /**
     * Initialize the system
     */
    private CompletableFuture<Void> initialize() {
        ui.showBootProgress("Initializing system", 5);
        
        return loadOrCreateBootstrapConfig()
            .thenCompose(config -> {
                this.bootstrapConfig = config;
                return registerSelfInRegistry();
            })
            .thenCompose(v -> determineBootPath())
            .thenCompose(isFirstRun -> 
                isFirstRun ? firstRunBoot() : normalBoot())
            .thenCompose(v -> startConfiguredServices())
            .thenRun(() -> ui.showBootProgress("Base system ready", 80));
    }
    
    /**
     * Register this process in the registry
     * Must happen before spawning children
     */
    private CompletableFuture<Void> registerSelfInRegistry() {
        return CompletableFuture.runAsync(() -> {
            ContextPath basePath = ContextPath.of("system", "base");
            registry.registerProcess(this, basePath, null);
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    // ===== BOOTSTRAP CONFIG MANAGEMENT =====
    
    private CompletableFuture<NoteBytesMap> loadOrCreateBootstrapConfig() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File bootstrapFile = getBootstrapFile();
                
                if (!bootstrapFile.exists()) {
                    NoteBytesMap config = BootstrapConfig.createDefault();
                    // Save async
                    saveBootstrapConfig(config);
                    return config;
                } else {
                    return FileStreamUtils.readFileToMap(bootstrapFile);
                }
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    /**
     * Save bootstrap configuration
     * Called when config changes at runtime
     */
    public CompletableFuture<Void> saveBootstrapConfig() {
        return saveBootstrapConfig(this.bootstrapConfig);
    }
    
    private CompletableFuture<Void> saveBootstrapConfig(NoteBytesMap config) {
        return CompletableFuture.runAsync(() -> {
            try {
                File file = getBootstrapFile();
                NoteBytesObject obj = config.getNoteBytesObject();
                FileStreamUtils.writeFileBytes(file, obj.get());
            } catch (Exception e) {
                throw new CompletionException("Could not save bootstrap", e);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    /**
     * Update configuration value
     */
    public void updateConfig(String path, NoteBytes value) {
        BootstrapConfig.set(bootstrapConfig, path, value);
    }
    
    /**
     * Get configuration value
     */
    public NoteBytes getConfig(String... path) {
        return BootstrapConfig.get(bootstrapConfig, path);
    }
    
    // ===== BOOT SEQUENCE =====
    
    private CompletableFuture<Boolean> determineBootPath() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean hasBootstrap = getBootstrapFile().exists();
                boolean hasSettings = SettingsData.isSettingsData();
                boolean isFirstRun = !hasBootstrap || !hasSettings;
                
                String message = isFirstRun ? 
                    "First time setup detected" : "Loading configuration";
                ui.showBootProgress(message, 10);
                
                return isFirstRun;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    private CompletableFuture<Void> firstRunBoot() {
        ui.showBootProgress("Starting first-time setup", 15);
        
        return ui.showWelcome()
            .thenCompose(v -> configureSecureInput())
            .thenCompose(v -> configureShellInput())
            .thenCompose(v -> saveBootstrapConfig())
            .thenRun(() -> ui.showMessage("Configuration saved"));
    }
    
    private CompletableFuture<Void> normalBoot() {
        ui.showBootProgress("Loading configuration", 20);
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== SERVICE MANAGEMENT =====
    
    /**
     * Start services defined in bootstrap config
     * This is what BaseSystemProcess MANAGES
     */
    private CompletableFuture<Void> startConfiguredServices() {
        List<CompletableFuture<Void>> serviceFutures = new ArrayList<>();
        
        // Check each service in config and start if enabled
        if (BootstrapConfig.isSecureInputInstalled(bootstrapConfig)) {
            serviceFutures.add(startSecureInputService());
        }
        
        // Could have other services:
        // if (BootstrapConfig.isNetworkEnabled(bootstrapConfig)) {
        //     serviceFutures.add(startNetworkService());
        // }
        
        return CompletableFuture.allOf(
            serviceFutures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Start IODaemon service
     * BaseSystemProcess CREATES and MANAGES this
     */
    private CompletableFuture<Void> startSecureInputService() {
        ui.showBootProgress("Starting secure input", 30);
        
        String socketPath = BootstrapConfig.getSecureInputSocketPath(bootstrapConfig);
        String sessionId = "system-" + System.currentTimeMillis();
        
        // Create the service
        IODaemon daemon = new IODaemon(socketPath, sessionId);
        
        // Spawn as child (we manage it)
        return spawnChild(daemon, "secure-input")
            .thenCompose(daemonPath -> {
                // Start the child process
                return registry.startProcess(daemonPath);
            })
            .thenCompose(v -> {
                // Wait for connection
                IODaemon service = (IODaemon) getChildService("secure-input");
                return waitForConnection(service, Duration.ofSeconds(5));
            })
            .thenCompose(v -> {
                // Discover devices
                IODaemon service = (IODaemon) getChildService("secure-input");
                return service.discoverDevices();
            })
            .thenRun(() -> {
                IODaemon service = (IODaemon) getChildService("secure-input");
                int deviceCount = service.getDiscoveredDevices().size();
                
                // Track as managed service
                managedServices.put("secure-input", service);
                
                if (deviceCount == 0) {
                    ui.showMessage("No USB devices found. Using GUI input.");
                } else {
                    ui.showMessage("Secure input ready: " + deviceCount + " devices");
                }
            })
            .exceptionally(ex -> {
                ui.showError("Secure input unavailable: " + ex.getMessage());
                return null;
            });
    }
    
    /**
     * Get a child service we spawned
     */
    private FlowProcess getChildService(String serviceName) {
        ContextPath servicePath = getContextPath().append(serviceName);
        return registry.getProcess(servicePath);
    }
    
    /**
     * Wait for connection without blocking
     */
    private CompletableFuture<Void> waitForConnection(
            IODaemon daemon, 
            Duration timeout) {
        
        CompletableFuture<Void> result = new CompletableFuture<>();
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(0, Thread.ofVirtual().factory());
        
        scheduler.scheduleAtFixedRate(() -> {
            if (daemon.isConnected()) {
                result.complete(null);
                scheduler.shutdown();
            } else if (System.currentTimeMillis() > deadline) {
                result.completeExceptionally(
                    new TimeoutException("Connection timeout"));
                scheduler.shutdown();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        
        return result;
    }
    
    // ===== PROCESS INTERFACE =====
    
    @Override
    public CompletableFuture<Void> run() {
        // BaseSystemProcess monitors its children
        // Could implement health checks, auto-restart, etc.
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Handle system-level commands
        try {
            NoteBytesMap command = packet.getPayload().getAsNoteBytesMap();
            String action = command.get("action").getAsString();
            
            switch (action) {
                case "restart_service":
                    return handleRestartService(command, packet);
                case "service_status":
                    return handleServiceStatus(command, packet);
                case "list_services":
                    return handleListServices(packet);
                case "update_config":
                    return handleUpdateConfig(command, packet);
                default:
                    return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Unknown action: " + action));
            }
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private CompletableFuture<Void> handleRestartService(
            NoteBytesMap command,
            RoutedPacket request) {
        
        String serviceName = command.get("service").getAsString();
        
        // Kill old service
        FlowProcess oldService = managedServices.get(serviceName);
        if (oldService != null) {
            oldService.kill();
        }
        
        // Restart based on service name
        CompletableFuture<Void> restartFuture;
        switch (serviceName) {
            case "secure-input":
                restartFuture = startSecureInputService();
                break;
            default:
                restartFuture = CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unknown service: " + serviceName));
        }
        
        return restartFuture.thenRun(() -> 
            reply(request, new NoteBytes("Service restarted: " + serviceName)));
    }
    
    private CompletableFuture<Void> handleServiceStatus(
            NoteBytesMap command,
            RoutedPacket request) {
        
        String serviceName = command.get("service").getAsString();
        FlowProcess service = managedServices.get(serviceName);
        
        NoteBytesMap response = new NoteBytesMap();
        if (service == null) {
            response.put("status", new NoteBytes("not_found"));
        } else {
            response.put("status", new NoteBytes(service.isAlive() ? "running" : "stopped"));
            response.put("info", new NoteBytes(service.getInfo()));
        }
        
        reply(request, response.getNoteBytesObject());
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleListServices(RoutedPacket request) {
        NoteBytesMap response = new NoteBytesMap();
        response.put("count", new NoteBytes(managedServices.size()));
        
        NoteBytesMap services = new NoteBytesMap();
        managedServices.forEach((name, service) -> {
            NoteBytesMap serviceInfo = new NoteBytesMap();
            serviceInfo.put("alive", new NoteBytes(service.isAlive()));
            serviceInfo.put("type", new NoteBytes(service.getProcessType().toString()));
            services.put(name, serviceInfo);
        });
        response.put("services", services);
        
        reply(request, response.getNoteBytesObject());
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleUpdateConfig(
            NoteBytesMap command,
            RoutedPacket request) {
        
        String path = command.get("path").getAsString();
        NoteBytes value = command.get("value");
        
        updateConfig(path, value);
        
        return saveBootstrapConfig()
            .thenRun(() -> reply(request, new NoteBytes("Config updated")));
    }
    
    // ===== FIRST-TIME CONFIGURATION =====
    
    private CompletableFuture<Void> configureSecureInput() {
        return ui.promptInstallSecureInput()
            .thenApply(install -> {
                BootstrapConfig.setSecureInputInstalled(bootstrapConfig, install);
                return null;
            });
    }
    
    private CompletableFuture<Void> configureShellInput() {
        if (BootstrapConfig.isSecureInputInstalled(bootstrapConfig)) {
            return ui.promptUseSecureInputForShell()
                .thenApply(useSecure -> {
                    String source = useSecure ? 
                        "system/base/secure-input" : "system/gui/native";
                    BootstrapConfig.setShellInputSource(bootstrapConfig, source);
                    return null;
                });
        }
        
        BootstrapConfig.setShellInputSource(bootstrapConfig, "system/gui/native");
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== FILE PATHS (Static Utilities) =====
    
    private static File m_appDir = null;
    private static File m_appFile = null;
    
    static {
        try {
            URL classLocation = JarHelpers.getLocation(BaseSystemProcess.class);
            m_appFile = JarHelpers.urlToFile(classLocation);
            m_appDir = m_appFile.getParentFile();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static File getBootstrapFile() throws IOException {
        File dataDir = getAppDataDir();
        return new File(dataDir, "bootstrap.dat");
    }
    
    private static File getAppDataDir() {
        File dataDir = new File(m_appDir, "data");
        if (!dataDir.isDirectory()) {
            try {
                Files.createDirectory(dataDir.toPath());
            } catch (IOException e) {
                throw new RuntimeException("Cannot create data directory", e);
            }
        }
        return dataDir;
    }
    
    public static File getAppDir() {
        return m_appDir;
    }
    
    public static File getAppFile() {
        return m_appFile;
    }
    
    public static File getDataDir() {
        return getAppDataDir();
    }
    
    // ===== GETTERS =====
    
    public NoteBytesMap getBootstrapConfig() {
        return bootstrapConfig;
    }
    
    public FlowProcess getManagedService(String serviceName) {
        return managedServices.get(serviceName);
    }
    
    public Map<String, FlowProcess> getAllManagedServices() {
        return new HashMap<>(managedServices);
    }
}