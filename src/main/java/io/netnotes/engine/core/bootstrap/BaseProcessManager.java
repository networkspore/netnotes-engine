package io.netnotes.engine.core.bootstrap;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.daemon.IODaemon;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.FlowProcessRegistry;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.VirtualExecutors;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * BaseProcessManager - System BIOS / Base Service Manager
 * 
 * Registered as Process at: /system/base
 * 
 * Responsibilities:
 * - Bootstrap system initialization
 * - Manage base services (IODaemon, etc.) as child processes
 * - Create/load SettingsData (password management)
 * - Initialize AppData (encrypted services)
 * - Route subscriptions to base services
 * 
 * Boot Sequence:
 * 1. Check if bootstrap config exists
 * 2a. First Run: Configure system → Create password → Save bootstrap
 * 2b. Normal Boot: Load bootstrap → Start services → Verify password
 * 3. Initialize AppData with unlocked SettingsData
 * 4. Start application services
 */
public class BaseProcessManager extends FlowProcess {
    
    private final BootstrapUI ui;
    private final FlowProcessRegistry registry;
    
    private BootstrapManager bootstrapManager;
    private SettingsData settingsData;
    private AppData appData;
    
    private ContextPath systemBasePath;
    
    public BaseProcessManager(BootstrapUI ui) {
        super(ProcessType.BIDIRECTIONAL);
        this.ui = ui;

        this.registry = FlowProcessRegistry.getInstance();
    }
    
    // ===== PROCESS LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> run() {
        return boot();
    }
    
    @Override
    protected void onStart() {
        System.out.println("BaseProcessManager starting...");
    }
    
    @Override
    protected void onStop() {
        System.out.println("BaseProcessManager stopping...");
        if (appData != null) {
            appData.shutdown(null);
        }
    }
    
    // ===== BOOT SEQUENCE =====
    
    /**
     * Main boot sequence
     */
    public CompletableFuture<Void> boot() {
        systemBasePath = getContextPath(); // Should be /system/base
        
        return initializeBootstrapManager()
            .thenCompose(v -> determineBootPath())
            .thenCompose(isFirstRun -> {
                if (isFirstRun) {
                    return firstRunBoot();
                } else {
                    return normalBoot();
                }
            })
            .thenCompose(v -> initializeAppData())
            .thenCompose(v -> startApplicationServices())
            .thenRun(() -> {
                ui.showBootProgress("System ready", 100);
                System.out.println("Boot complete - system operational");
            });
    }
    
    /**
     * Initialize BootstrapManager
     */
    private CompletableFuture<Void> initializeBootstrapManager() {
        return BootstrapManager.loadBootstrap().thenApply((config) -> {

            ui.showBootProgress("Initializing bootstrap", 5);
     
            bootstrapManager = new BootstrapManager(config, ui);
            return null;
        });
    }
    
    /**
     * Determine if this is first run or normal boot
     */
    private CompletableFuture<Boolean> determineBootPath() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean hasBootstrap = BootstrapManager.hasBootstrapConfig();
                boolean hasSettings = SettingsData.isSettingsData();
                
                // First run if either is missing
                boolean isFirstRun = !hasBootstrap || !hasSettings;
                
                if (isFirstRun) {
                    ui.showBootProgress("First time setup detected", 10);
                } else {
                    ui.showBootProgress("Loading configuration", 10);
                }
                
                return isFirstRun;
                
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    // ===== FIRST RUN BOOT =====
    
    /**
     * First run: Configure system and create password
     */
    private CompletableFuture<Void> firstRunBoot() {
        ui.showBootProgress("Starting first-time setup configuration", 15);
        
        return bootstrapManager.runFirstTimeSetup()
            .thenCompose(v -> {
                ui.showBootProgress("Creating password", 50);
                return bootstrapManager.createSettingsData();
            })
            .thenApply(settings -> {
                this.settingsData = settings;
                ui.showBootProgress("System configured", 80);
                return null;
            });
    }
    
    // ===== NORMAL BOOT =====
    
    /**
     * Normal boot: Load bootstrap, start services, verify password
     */
    private CompletableFuture<Void> normalBoot() {
        ui.showBootProgress("Bootstrap loaded", 20);
        
        return startBaseServices()
            .thenCompose(v -> {
                ui.showBootProgress("Authenticating", 50);
                return bootstrapManager.loadSettingsData();
            })
            .thenApply(settings -> {
                this.settingsData = settings;
                ui.showBootProgress("Authentication successful", 80);
                return null;
            });
    }
    
    /**
     * Start base services configured in bootstrap
     */
    private CompletableFuture<Void> startBaseServices() {
        NoteBytesMap bootstrap = bootstrapManager.getBootstrapConfig();
        
        // Check if secure-input (IODaemon) is installed
        NoteBytes secureInputInstalled = BootstrapConfig.get(bootstrap,
            "system", "base", "secure-input", "installed");
        
        if (secureInputInstalled != null && secureInputInstalled.getAsBoolean()) {
            ui.showBootProgress("Starting secure input", 30);
            return startIODaemonService();
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Start IODaemon as a child process
     */
    private CompletableFuture<Void> startIODaemonService() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                NoteBytesMap bootstrap = bootstrapManager.getBootstrapConfig();
                String socketPath = BootstrapConfig.getSecureInputSocketPath(bootstrap);
                
                String sessionId = "system-" + System.currentTimeMillis();
                
                // IODaemon IS the Process
                IODaemon daemon = new IODaemon(socketPath, sessionId);
                
                // Register as child process
                ContextPath daemonPath = systemBasePath.append("secure-input");
                registry.registerProcess(daemon, daemonPath, systemBasePath);
                
                // Start process (connects and begins reading)
                registry.startProcess(daemonPath);
                
                System.out.println("IODaemon started at " + daemonPath);
                
                return null;
                
            } catch (Exception e) {
                System.err.println("Failed to start IODaemon: " + e.getMessage());
                throw new CompletionException(e);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    // ===== POST-AUTHENTICATION =====
    
    /**
     * Initialize AppData with unlocked SettingsData
     */
    private CompletableFuture<Void> initializeAppData() {
        return CompletableFuture.supplyAsync(() -> {
            ui.showBootProgress("Initializing encrypted services", 85);
            
            if (settingsData == null) {
                throw new IllegalStateException("Cannot initialize AppData - no SettingsData");
            }
            
            // Create AppData (encrypted file system, plugins, etc.)
            appData = new AppData(settingsData);
            
            ui.showBootProgress("Encrypted services ready", 90);
            return null;
            
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    /**
     * Start application-level services
     */
    private CompletableFuture<Void> startApplicationServices() {
        return CompletableFuture.supplyAsync(() -> {
            ui.showBootProgress("Starting application services", 95);
            
            // TODO: Load and start plugins from AppData
            // TODO: Initialize application-level processes
            
            return null;
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    // ===== CHILD PROCESS MANAGEMENT =====
    
    /**
     * Spawn a child process under /system/base
     */
    public CompletableFuture<ContextPath> spawnBaseService(FlowProcess service, String serviceName) {
        return spawnChild(service, serviceName);
    }
    
    /**
     * Get a base service by name
     */
    public FlowProcess getBaseService(String serviceName) {
        ContextPath servicePath = systemBasePath.append(serviceName);
        return registry.getProcess(servicePath);
    }
    
    // ===== GETTERS =====
    
    public BootstrapManager getBootstrapManager() {
        return bootstrapManager;
    }
    
    public SettingsData getSettingsData() {
        if (settingsData == null) {
            throw new IllegalStateException("SettingsData not initialized - system not booted");
        }
        return settingsData;
    }
    
    public AppData getAppData() {
        if (appData == null) {
            throw new IllegalStateException("AppData not initialized - system not fully booted");
        }
        return appData;
    }
    
    public boolean isBooted() {
        return appData != null;
    }
}