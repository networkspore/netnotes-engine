package io.netnotes.engine.core.bootstrap;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.daemon.DiscoveredDeviceRegistry;
import io.netnotes.engine.io.daemon.IODaemon;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteFiles.FileStreamUtils;
import io.netnotes.engine.utils.JarHelpers;
import io.netnotes.engine.utils.Version;
import io.netnotes.engine.utils.VirtualExecutors;

/**
 * BootstrapManager - System initialization and base services
 * 
 * Process Type: BIDIRECTIONAL
 * Registered at: /system/bootstrap
 * 
 * Responsibilities:
 * - Application file/directory discovery
 * - Bootstrap configuration management
 * - Creates SettingsData (password hash + secret key only)
 * - Manages base services during bootstrap
 * - Transitions to BaseProcessManager after boot
 */
public class BootstrapManager extends FlowProcess {
    // Keys for bootstrap structure
    public static final NoteBytes SYSTEM_KEY = new NoteBytes("system");
    public static final NoteBytes BASE_KEY = new NoteBytes("base");
    public static final NoteBytes SECURE_INPUT_KEY = new NoteBytes("secure-input");
    public static final NoteBytes COMMAND_SHELL_KEY = new NoteBytes("command-shell");

    // ===== APPLICATION DISCOVERY (STATIC) =====
    
    private static File m_appDir = null;
    private static File m_appFile = null;
    private static NoteBytesReadOnly m_appHash = null;
    private static Version m_javaVersion = null;
   
    public static final File HOME_DIRECTORY = new File(System.getProperty("user.home"));
    public static final File DESKTOP_DIRECTORY = new File(HOME_DIRECTORY + "/Desktop");
    public static final File DOWNLOADS_DIRECTORY = new File(HOME_DIRECTORY + "/Downloads");

    private static final String BOOTSTRAP_FILE_NAME = "bootstrap.dat";
    
    static {
        try {
            URL classLocation = JarHelpers.getLocation(BootstrapManager.class);
            m_appFile = JarHelpers.urlToFile(classLocation);
            m_appHash = new NoteBytesReadOnly(HashServices.digestFileToBytes(m_appFile, 16));
            m_appDir = m_appFile.getParentFile();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    // ===== BOOTSTRAP CONFIGURATION =====
    
    private NoteBytesMap m_bootstrapConfig;
    private final BootstrapUI m_ui;
    private final ShellInputSourceRegistry m_inputRegistry;


    
    public BootstrapManager(NoteBytesMap bootstrapConfig, BootstrapUI ui) {
        super(ProcessType.BIDIRECTIONAL);
        this.m_ui = ui;
        m_bootstrapConfig = bootstrapConfig;
        this.m_inputRegistry = new ShellInputSourceRegistry();
        // Register GUI native input (always available)
        m_inputRegistry.registerSource(
            "system/gui/native",
            new ShellInputSourceRegistry.GUINativeInputSource(ui)
        );
    }
    
    @Override
    public CompletableFuture<Void> run() {
        // BootstrapManager doesn't have a long-running loop
        // It's used imperatively by BaseProcessManager
        return CompletableFuture.completedFuture(null);
    }

    @Override
    protected void onStart() {
        System.out.println("BootstrapManager started at: " + contextPath);
    }

    @Override
    protected void onStop() {
        System.out.println("BootstrapManager stopping");
    }

    /**
     * Check if bootstrap configuration exists
     */
    public static boolean hasBootstrapConfig() throws IOException {
        File bootstrapFile = getBootstrapFile();
        return bootstrapFile.exists() && bootstrapFile.isFile();
    }
    
    /**
     * Load bootstrap configuration
     */
    public static CompletableFuture<NoteBytesMap> loadBootstrap() {
     
            try {
                File bootstrapFile = getBootstrapFile();
                
                
                if (!bootstrapFile.exists()) {
                    NoteBytesMap config = BootstrapConfig.createDefault();
                    return saveBootstrap(config).thenCompose(v ->CompletableFuture.completedFuture(config));
                } else {
                    return CompletableFuture.supplyAsync(()->{
                        try{
                            return FileStreamUtils.readFileToMap(bootstrapFile);
                        }catch(Exception e){
                            throw new CompletionException("Unable to read file", e);
                        }
                    });
                }
                
            } catch (IOException e) {
                return CompletableFuture.failedFuture(new CompletionException("Could not access file", e));
            }
        
    }
    public CompletableFuture<Void> saveBootstrap() {
        return saveBootstrap(m_bootstrapConfig);  
    }

    /**
     * Save bootstrap configuration
     */
    public static CompletableFuture<Void> saveBootstrap(NoteBytesMap bootstrapConfig) {
        return CompletableFuture.runAsync(() -> {
            try {
                File file = getBootstrapFile();
                NoteBytesObject obj = bootstrapConfig.getNoteBytesObject();
                FileStreamUtils.writeFileBytes(file, obj.get());
            } catch (Exception e) {
                throw new CompletionException("Could not save bootstrap", e);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    /**
     * Update bootstrap configuration
     */
    public void updateBootstrapConfig(String path, NoteBytes value) {
        BootstrapConfig.set(m_bootstrapConfig, path, value);
    }
    
    public NoteBytes getBootstrapConfig(String... path) {
        return BootstrapConfig.get(m_bootstrapConfig, path);
    }
    
    // ===== FIRST RUN CONFIGURATION =====
    
    /**
     * Run first-time configuration wizard
     * Returns configured BootstrapManager (config saved to disk)
     */
    public CompletableFuture<Void> runFirstTimeSetup() {
        return m_ui.showWelcome()
            .thenCompose(v -> configureSecureInput())
            .thenCompose(v -> configureShellInputSource())
            .thenCompose(v -> saveBootstrap())
            .thenRun(() -> m_ui.showMessage("Bootstrap configuration complete"));
    }
    
     private CompletableFuture<Void> configureSecureInput() {
        return m_ui.promptInstallSecureInput()
            .thenCompose(install -> {
                updateBootstrapConfig("system/base/secure-input/installed", 
                    new NoteBytes(install));
                
                if (install) {
                    // Start IODaemon as permanent sibling process
                    return startSecureInput();
                }
                return CompletableFuture.completedFuture(null);
            });
    }
    
    /**
     * Start IODaemon as a PERMANENT process under /system/base/secure-input
     * This process remains running throughout the application lifecycle
     */
    private CompletableFuture<Void> startSecureInput() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String socketPath = BootstrapConfig.getSecureInputSocketPath(m_bootstrapConfig);
                String sessionId = "system-" + System.currentTimeMillis();
                
                // Create IODaemon Process
                IODaemon daemon = new IODaemon(socketPath, sessionId);
                
                // Register as SIBLING to BaseProcessManager (not child of BootstrapManager)
                // This ensures IODaemon persists after BootstrapManager is shut down
                ContextPath daemonPath = ContextPath.of("system", "base", "secure-input");
                ContextPath basePath = ContextPath.of("system", "base");
                registry.registerProcess(daemon, daemonPath, basePath);
                
                // Start IODaemon process
                registry.startProcess(daemonPath);
                
                // Wait for connection
                int attempts = 0;
                while (!daemon.isConnected() && attempts < 50) {
                    Thread.sleep(100);
                    attempts++;
                }
                
                if (!daemon.isConnected()) {
                    throw new IllegalStateException("IODaemon failed to connect");
                }
                
                // Discover devices
                daemon.discoverDevices().join();
                
                List<DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities> devices = 
                    daemon.getDiscoveredDevices();
                
                if (devices.isEmpty()) {
                    m_ui.showError("No USB devices found. You can still use GUI input.");
                } else {
                    m_ui.showMessage("Secure input ready: " + devices.size() + " devices found");
                }
                
                // Register SecureInputSource with daemon path
                m_inputRegistry.registerSource(
                    "system/base/secure-input",
                    new ShellInputSourceRegistry.SecureInputSource(daemonPath)
                );
                
                return null;
                
            } catch (Exception e) {
                m_ui.showError("Failed to start secure input: " + e.getMessage());
                // Don't throw - user can still use GUI input
                return null;
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    private CompletableFuture<Void> configureShellInputSource() {
        boolean secureInputInstalled = BootstrapConfig.isSecureInputInstalled(m_bootstrapConfig);
        
        if (secureInputInstalled) {
            // Check if IODaemon actually started successfully
            ContextPath daemonPath = ContextPath.of("system", "base", "secure-input");
            FlowProcess daemon = registry.getProcess(daemonPath);
            
            if (daemon != null && daemon.isAlive()) {
                return m_ui.promptUseSecureInputForShell()
                    .thenApply(useSecure -> {
                        String sourcePath = useSecure ? 
                            "system/base/secure-input" : 
                            "system/gui/native";
                        
                        updateBootstrapConfig("system/base/command-shell/input-source",
                            new NoteBytes(sourcePath));
                        
                        m_inputRegistry.setActiveSource(sourcePath);
                        return null;
                    });
            }
        }
        
        // Default to GUI native
        updateBootstrapConfig("system/base/command-shell/input-source",
            new NoteBytes("system/gui/native"));
        m_inputRegistry.setActiveSource("system/gui/native");
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== SETTINGS DATA CREATION (PASSWORD ONLY) =====
    
    /**
     * Create SettingsData (password hash + secret key)
     * This is the ONLY place SettingsData is created during bootstrap
     */
    public CompletableFuture<SettingsData> createSettingsData() {
        return createPassword()
            .thenCompose(password -> buildSettingsData(password));
    }
    
    private CompletableFuture<NoteBytesEphemeral> createPassword() {
        return m_inputRegistry.readPassword(PasswordContext.FIRST_TIME_SETUP)
            .thenCompose(password -> confirmPassword(password));
    }
    
    private CompletableFuture<NoteBytesEphemeral> confirmPassword(NoteBytesEphemeral password) {
        if (password.byteLength() < 6) {
            m_ui.showError("Password must be at least 6 characters");
            password.close();
            return createPassword();
        }
        
        return m_inputRegistry.readPassword(PasswordContext.CONFIRM)
            .thenCompose(confirm -> {
                if (!password.equals(confirm)) {
                    m_ui.showError("Passwords do not match");
                    password.close();
                    confirm.close();
                    return createPassword();
                }
                confirm.close();
                return CompletableFuture.completedFuture(password);
            });
    }
    
    private CompletableFuture<SettingsData> buildSettingsData(NoteBytesEphemeral createdPass) {
        return SettingsData.createSettings(createdPass, VirtualExecutors.getVirtualExecutor());
    }
    
    // ===== NORMAL BOOT (LOAD EXISTING SETTINGS) =====
    
   
    /**
     * Load existing SettingsData (verify password, derive key)
     */
    public CompletableFuture<SettingsData> loadSettingsData() {
        m_ui.showBootProgress("Ready for authentication", 40);
        
        // Prompt for password using configured input source
        return m_inputRegistry.readPassword(PasswordContext.UNLOCK)
            .thenCompose(createdPass -> {
                try{
                    // Load settings map
                    return SettingsData.loadSettingsMap(VirtualExecutors.getVirtualExecutor())
                        .thenCompose(settingsMap -> {
                            try(NoteBytesEphemeral copiedPass = createdPass.copy()){
                                return SettingsData.verifyPassword(copiedPass, settingsMap, VirtualExecutors.getVirtualExecutor())
                                    .thenCompose(verified -> {
                                        try(NoteBytesEphemeral verifiedPass = copiedPass){
                                            if (!verified) {
                                                createdPass.close();
                                                m_ui.showError("Invalid password");
                                                // Retry
                                                return loadSettingsData();
                                            }
                                            
                                            m_ui.showBootProgress("Password verified", 60);
                                            
                                        
                                            return SettingsData.loadSettingsData(verifiedPass, settingsMap, 
                                                VirtualExecutors.getVirtualExecutor());
                                        }
                                    });
                            }
                        });
                }finally{
                    createdPass.close();
                }
            });
    }

    
    /**
     * Start configured input sources for normal boot
     * IODaemon is started here as a PERMANENT process if configured
     */
    public CompletableFuture<Void> startConfiguredInputSources() {
        boolean secureInputInstalled = BootstrapConfig.isSecureInputInstalled(m_bootstrapConfig);
        
        if (secureInputInstalled) {
            return startSecureInput(); // Same method - starts it permanently
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== FILE PATHS =====
    
    private static File getBootstrapFile() throws IOException {
        File dataDir = getAppDataDir();
        return new File(dataDir.getAbsolutePath() + "/" + BOOTSTRAP_FILE_NAME);
    }
    
    private static File getAppDataDir() {
        File dataDir = new File(m_appDir.getAbsolutePath() + "/data");
        if (!dataDir.isDirectory()) {
            try {
                Files.createDirectory(dataDir.toPath());
            } catch (IOException e) {
                throw new RuntimeException("Cannot create data directory", e);
            }
        }
        return dataDir;
    }
    
    // ===== GETTERS =====
    
    public static File getAppDir() {
        return m_appDir;
    }
    
    public static File getAppFile() {
        return m_appFile;
    }
    
    public static NoteBytesReadOnly getAppHash() {
        return m_appHash;
    }
    
    public static File getDataDir() {
        return getAppDataDir();
    }

    public Version getJavaVersion(){
        return m_javaVersion;
    }

    
    public NoteBytesMap getBootstrapConfig() {
        return m_bootstrapConfig;
    }
    
    public ShellInputSourceRegistry getInputRegistry() {
        return m_inputRegistry;
    }
}