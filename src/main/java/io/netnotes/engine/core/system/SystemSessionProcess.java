package io.netnotes.engine.core.system;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.*;
import io.netnotes.engine.core.system.control.ui.*;
import io.netnotes.engine.crypto.CryptoService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.SecretKey;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.messaging.task.TaskMessages;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.DiskSpaceValidation;
import io.netnotes.engine.noteFiles.FileStreamUtils;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.VirtualExecutors;

/**
 * SystemSessionProcess - Manages user session lifecycle
 * 
 * Refined Initialization Flow:
 * 
 * 1. INITIALIZING
 *    Load bootstrap config
 * 
 * 2. BOOTSTRAP_LOADED
 *    Check SettingsData.isSettingsData()
 * 
 * 3a. FIRST_RUN (no settings file)
 *     - Start password creation session
 *     - User enters password twice (matching)
 *     - SettingsData.createSettings(password) → settingsData
 *     - new AppData(settingsData) → appData
 *     → READY
 * 
 * 3b. SETTINGS_EXIST (settings file found)
 *     - Load settingsMap (ephemeral, function-scoped)
 *     - Start password verification session
 *     - User enters password
 *     - SettingsData.verifyPassword(password, map)
 *     - If valid: SettingsData.loadSettingsData(password, map) → settingsData
 *     - new AppData(settingsData) → appData
 *     - Discard settingsMap
 *     → READY
 * 
 * 4. READY (AppData created)
 *    - Check if secure input is required
 *    - If yes: LOCKED
 *    - If no: UNLOCKED
 * 
 * 5. LOCKED → UNLOCKED
 *    - Start password verification
 *    - appData.getSettingsData().verifyPassword(password)
 *    - If valid: UNLOCKED
 * 
 * Key Changes:
 * - settingsMap is ephemeral (function-scoped only)
 * - settingsData is not cached (used only to create AppData)
 * - appData is the primary interface (cached)
 * - All operations go through appData
 */
public class SystemSessionProcess extends FlowProcess {
    
    private final BitFlagStateMachine state;
    private final UIRenderer uiRenderer;
    
    // Configuration
    private NoteBytesMap bootstrapConfig;
    
    // System data (created after password verification)
    private AppData appData; // PRIMARY INTERFACE - cached
    
    // Session info
    private final String sessionId;
    private final SessionType sessionType;
    
    // Child processes
    private PasswordSessionProcess passwordSession;
    private MenuNavigatorProcess menuNavigator;
    
    // Message dispatch
    private final Map<NoteBytesReadOnly, RoutedMessageExecutor> m_routedMsgMap = 
        new ConcurrentHashMap<>();
    
    public SystemSessionProcess(
            String sessionId,
            SessionType sessionType,
            UIRenderer uiRenderer) {
        
        super(ProcessType.BIDIRECTIONAL);
        this.sessionId = sessionId;
        this.sessionType = sessionType;
        this.uiRenderer = uiRenderer;
        this.state = new BitFlagStateMachine("system-session-" + sessionId);
        
        setupMessageMapping();
        setupStateTransitions();
    }
    
    private void setupMessageMapping() {
        // UI commands
        m_routedMsgMap.put(UICommands.UI_MENU_SELECTED, this::handleMenuSelection);
        m_routedMsgMap.put(UICommands.UI_BACK, this::handleBack);
        m_routedMsgMap.put(UICommands.UI_PASSWORD_ENTERED, this::handlePasswordEntered);
        m_routedMsgMap.put(UICommands.UI_CANCELLED, this::handleCancelled);
        
        // System commands
        m_routedMsgMap.put(UICommands.LOCK_SYSTEM, this::handleLockSystem);
        m_routedMsgMap.put(UICommands.UNLOCK_SYSTEM, this::handleUnlockSystem);
        m_routedMsgMap.put(UICommands.CHANGE_PASSWORD, this::handleChangePassword);
    }
    
    private void setupStateTransitions() {
        // INITIALIZING → BOOTSTRAP_LOADED
        state.onStateAdded(SystemSessionStates.BOOTSTRAP_LOADED, (old, now, bit) -> {
            System.out.println("[SystemSession] Bootstrap config loaded");
            
            // Check if settings exist
            checkSettingsExist()
                .thenAccept(exists -> {
                    if (exists) {
                        state.addState(SystemSessionStates.SETTINGS_EXIST);
                    } else {
                        state.addState(SystemSessionStates.FIRST_RUN_SETUP);
                    }
                });
        });
        
        // FIRST_RUN_SETUP (no settings file)
        state.onStateAdded(SystemSessionStates.FIRST_RUN_SETUP, (old, now, bit) -> {
            System.out.println("[SystemSession] First run - creating new settings");
            uiRenderer.render(UIProtocol.showMessage("Welcome! Please create a master password."));
            
            // Start password creation session
            startPasswordCreationSession();
        });
        
        // SETTINGS_EXIST (settings file found)
        state.onStateAdded(SystemSessionStates.SETTINGS_EXIST, (old, now, bit) -> {
            System.out.println("[SystemSession] Settings found - requesting password");
            
            // Start password verification session directly
            // (we'll load the map in the verification handler)
            startPasswordVerificationSession();
        });
        
        // READY (AppData created)
        state.onStateAdded(SystemSessionStates.READY, (old, now, bit) -> {
            System.out.println("[SystemSession] System ready - AppData initialized");
            
            // Check for incomplete password change
            checkForIncompletePasswordChange()
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        System.err.println("[SystemSession] Error checking recovery: " + 
                            ex.getMessage());
                    }
                    
                    /* Check if we should start locked
                    if (BootstrapConfig.isSecureInputInstalled(bootstrapConfig)) {
                        state.addState(SystemSessionStates.LOCKED);
                    } else {
                        state.addState(SystemSessionStates.UNLOCKED);
                    }*/
                });
        });
        
        // LOCKED
        state.onStateAdded(SystemSessionStates.LOCKED, (old, now, bit) -> {
            System.out.println("[SystemSession] System locked");
            showLockedMenu();
        });
                
        // UNLOCKING
        state.onStateAdded(SystemSessionStates.UNLOCKING, (old, now, bit) -> {
            System.out.println("[SystemSession] Unlocking system...");
            startUnlockPasswordSession();
        });
        
        // UNLOCKED
        state.onStateAdded(SystemSessionStates.UNLOCKED, (old, now, bit) -> {
            System.out.println("[SystemSession] System unlocked");
            showMainMenu();
        });
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(SystemSessionStates.INITIALIZING);
        
        // Load bootstrap config
        return loadBootstrapConfig()
            .thenAccept(config -> {
                this.bootstrapConfig = config;
                state.removeState(SystemSessionStates.INITIALIZING);
                state.addState(SystemSessionStates.BOOTSTRAP_LOADED);
            })
            .thenCompose(v -> getCompletionFuture());
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        try {
            NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
            NoteBytesReadOnly cmd = msg.getReadOnly(Keys.CMD);
            
            if (cmd == null) {
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("'cmd' required"));
            }
            
            // Dispatch using map
            RoutedMessageExecutor executor = m_routedMsgMap.get(cmd);
            if (executor != null) {
                return executor.execute(msg, packet);
            } else {
                System.err.println("[SystemSession] Unknown command: " + cmd);
                return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unknown command: " + cmd));
            }
            
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException("SystemSession does not handle streams");
    }
    
    // ===== INITIALIZATION STEPS =====
    
    private CompletableFuture<NoteBytesMap> loadBootstrapConfig() {
        return SettingsData.loadBootStrapConfig()
            .exceptionally(ex -> {
                System.err.println("[SystemSession] Failed to load bootstrap config: " + ex.getMessage());
                // Create default if missing
                return BootstrapConfig.createDefault();
            });
    }
    
    private CompletableFuture<Boolean> checkSettingsExist() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return SettingsData.isSettingsData();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    // ===== PASSWORD SESSIONS =====
    
    /**
     * First run: Create new password
     * Creates SettingsData → AppData in one flow
     */
    private void startPasswordCreationSession() {
        getSecureInputDevice()
            .thenAccept(device -> {
                PasswordCreationSession session = new PasswordCreationSession(
                    device,
                    uiRenderer,
                    "Create master password",
                    "Confirm master password"
                );
                
                // Handle successful password creation
                session.onPasswordCreated(password -> {
                    return createNewSystem(password)
                        .thenAccept(appDataResult -> {
                            this.appData = appDataResult;
                            
                            state.removeState(SystemSessionStates.FIRST_RUN_SETUP);
                            state.addState(SystemSessionStates.READY);
                            
                            uiRenderer.render(UIProtocol.showMessage(
                                "Master password created successfully"));
                        });
                });
                
                // Handle cancellation
                session.onCancelled(() -> {
                    uiRenderer.render(UIProtocol.showError(
                        "Password creation cancelled - cannot proceed"));
                });
                
                // Spawn session
                spawnChild(session, "password-creation")
                    .thenCompose(path -> registry.startProcess(path));
            });
    }
    
    /**
     * Create new system: SettingsData → AppData
     */
    private CompletableFuture<AppData> createNewSystem(NoteBytesEphemeral password) {
        System.out.println("[SystemSession] Creating new system with password");
        
        return SettingsData.createSettings(password)
            .thenApply(settingsData -> {
                System.out.println("[SystemSession] SettingsData created, initializing AppData");
                
                // Create AppData from SettingsData
                AppData newAppData = new AppData(settingsData);
                
                System.out.println("[SystemSession] AppData initialized successfully");
                return newAppData;
            });
    }
    
    /**
     * Existing settings: Verify password
     * Loads ephemeral map → verifies → creates SettingsData → AppData
     */
    private void startPasswordVerificationSession() {
        getSecureInputDevice()
            .thenAccept(device -> {
                PasswordSessionProcess session = new PasswordSessionProcess(
                    device,
                    uiRenderer,
                    "Enter master password",
                    0
                );
                
                // Set password verification handler
                session.onPasswordEntered(password -> {
                    return verifyAndLoadSystem(password)
                        .thenApply(appDataResult -> {
                            if (appDataResult != null) {
                                this.appData = appDataResult;
                                
                                state.removeState(SystemSessionStates.SETTINGS_EXIST);
                                state.addState(SystemSessionStates.READY);
                                
                                return true;
                            } else {
                                uiRenderer.render(UIProtocol.showError("Invalid password"));
                                return false;
                            }
                        });
                });
                
                this.passwordSession = session;
                
                // Spawn session
                spawnChild(session, "password-verification")
                    .thenCompose(path -> registry.startProcess(path));
            });
    }
        
        
    /**
     * Password verification during initialization
     * NOW uses AppData instead of SettingsData directly
     */
    private CompletableFuture<AppData> verifyAndLoadSystem(NoteBytesEphemeral password) {
        System.out.println("[SystemSession] Verifying password and loading system");
        
        // Load settingsMap (ephemeral - only exists in this scope)
        return SettingsData.loadSettingsMap()
            .thenCompose(settingsMap -> {
                System.out.println("[SystemSession] Settings map loaded, verifying password");
                
                // Verify password using the map
                return SettingsData.verifyPassword(password, settingsMap)
                    .thenCompose(valid -> {
                        if (valid) {
                            System.out.println("[SystemSession] Password valid, loading SettingsData");
                            
                            // Load SettingsData
                            return SettingsData.loadSettingsData(password, settingsMap)
                                .thenApply(settingsData -> {
                                    System.out.println("[SystemSession] SettingsData loaded, creating AppData");
                                    
                                    // Create AppData (SettingsData becomes private)
                                    AppData newAppData = new AppData(settingsData);
                                    
                                    System.out.println("[SystemSession] AppData created");
                                    
                                    return newAppData;
                                });
                        } else {
                            System.out.println("[SystemSession] Password invalid");
                            return CompletableFuture.completedFuture(null);
                        }
                    });
            });
    }
        
    
    /**
     * Unlock from LOCKED state
     * NOW uses AppData for password verification
     */
    private void startUnlockPasswordSession() {
        getSecureInputDevice()
            .thenAccept(device -> {
                PasswordSessionProcess session = new PasswordSessionProcess(
                    device,
                    uiRenderer,
                    "Enter password to unlock",
                    0
                );
                
                // Set password verification handler
                session.onPasswordEntered(password -> {
                    // Verify using AppData (not SettingsData directly)
                    return appData.verifyPassword(password)
                        .thenApply(valid -> {
                            if (valid) {
                                state.removeState(SystemSessionStates.UNLOCKING);
                                state.addState(SystemSessionStates.UNLOCKED);
                                
                                uiRenderer.render(UIProtocol.showMessage("System unlocked"));
                            } else {
                                uiRenderer.render(UIProtocol.showError("Invalid password"));
                            }
                            return valid;
                        });
                });
                
                this.passwordSession = session;
                
                // Spawn session
                spawnChild(session, "password-unlock")
                    .thenCompose(path -> registry.startProcess(path));
            });
    }
    
    // ===== INPUT DEVICE ACCESS =====
    
    /**
     * Get secure input device from parent (BaseSystemProcess)
     */
    private CompletableFuture<InputDevice> getSecureInputDevice() {
        if (parentPath == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("No parent process"));
        }
        
        // Request device from BaseSystemProcess
        return request(parentPath, Duration.ofSeconds(5),
            new NoteBytesPair(Keys.CMD, "get_secure_input_device")
        ).thenApply(response -> {
            NoteBytesMap resp = response.getPayload().getAsNoteBytesMap();
            String devicePath = resp.get("device_path").getAsString();
            
            if (devicePath == null) {
                throw new IllegalStateException("No secure input device available");
            }
            
            InputDevice device = (InputDevice) 
                registry.getProcess(ContextPath.parse(devicePath));
            
            if (device == null) {
                throw new IllegalStateException("Device not found: " + devicePath);
            }
            
            return device;
        });
    }
    
    // ===== MENU SYSTEM =====
    
    private void showLockedMenu() {
        MenuContext lockedMenu = buildLockedMenu();
        
        if (menuNavigator == null) {
            createMenuNavigator();
        }
        
        menuNavigator.showMenu(lockedMenu);
        state.addState(SystemSessionStates.SHOWING_MENU);
    }
    
    private void showMainMenu() {
        MenuContext mainMenu = buildMainMenu();
        
        if (menuNavigator == null) {
            createMenuNavigator();
        }
        
        menuNavigator.showMenu(mainMenu);
        state.addState(SystemSessionStates.SHOWING_MENU);
    }
    
    private void createMenuNavigator() {
        menuNavigator = new MenuNavigatorProcess(uiRenderer);
        
        spawnChild(menuNavigator, "menu-navigator")
            .thenCompose(path -> registry.startProcess(path));
    }


    /**
     * Show current bootstrap configuration
     */
    private void showBootstrapConfigInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Bootstrap Configuration\n\n");
        
        boolean secureInputInstalled = 
            BootstrapConfig.isSecureInputInstalled(bootstrapConfig);
        
        info.append("Secure Input Installed: ")
            .append(secureInputInstalled ? "Yes" : "No")
            .append("\n\n");
        
        if (secureInputInstalled) {
            String socketPath = 
                BootstrapConfig.getSecureInputSocketPath(bootstrapConfig);
            info.append("Socket Path: ").append(socketPath).append("\n");
            
            // Check if IODaemon is actually running
            if (parentPath != null) {
                info.append("\nNote: Secure input will be used automatically ")
                    .append("if the daemon is running and keyboards are available.");
            }
        } else {
            info.append("Secure input not installed.\n")
                .append("System will use GUI keyboard for password entry.");
        }
        
        uiRenderer.render(UIProtocol.showMessage(info.toString()));
    }
        
    /**
     * Request secure input installation (delegates to BaseSystemProcess)
     */
    private void requestSecureInputInstallation() {
        if (parentPath == null) {
            uiRenderer.render(UIProtocol.showError("Cannot communicate with system"));
            return;
        }
        
        // Send request to BaseSystemProcess
        NoteBytesMap request = new NoteBytesMap();
        request.put(Keys.CMD, new NoteBytes("install_secure_input"));
        
        emitTo(parentPath, request.getNoteBytesObject());
        
        uiRenderer.render(UIProtocol.showMessage(
            "Installation process started. Please follow prompts.\n\n" +
            "Note: Once installed, secure input will be used automatically " +
            "when available."));
    }


    /**
     * Request full bootstrap reconfiguration
     */
    private void requestBootstrapReconfiguration() {
        if (parentPath == null) {
            uiRenderer.render(UIProtocol.showError("Cannot communicate with system"));
            return;
        }
        
        // Send request to BaseSystemProcess
        NoteBytesMap request = new NoteBytesMap();
        request.put(Keys.CMD, new NoteBytes("reconfigure_bootstrap"));
        
        emitTo(parentPath, request.getNoteBytesObject());
        
        uiRenderer.render(UIProtocol.showMessage(
            "Launching bootstrap wizard..."));
    }
        

    private MenuContext buildLockedMenu() {
        ContextPath menuPath = contextPath.append("menu", "locked");
        MenuContext menu = new MenuContext(menuPath, "System Locked", uiRenderer);
        
        menu.addItem("unlock", "Unlock System", () -> {
            state.removeState(SystemSessionStates.LOCKED);
            state.addState(SystemSessionStates.UNLOCKING);
        });
        
        // NEW: Bootstrap configuration accessible before unlock
        menu.addSubMenu("bootstrap", "Bootstrap Configuration", subMenu -> {
            subMenu.addItem("view_config", "View Current Configuration", () -> {
                showBootstrapConfigInfo();
            });
            
            subMenu.addItem("install_secure_input", "Install Secure Input", 
                "Requires administrator privileges", () -> {
                requestSecureInputInstallation();
            });
            
            subMenu.addItem("reconfigure", "Run Bootstrap Wizard", 
                "⚠️ Advanced: Reconfigure system", () -> {
                requestBootstrapReconfiguration();
            });
            
            return subMenu;
        });
        
        menu.addItem("about", "About", () -> {
            uiRenderer.render(UIProtocol.showMessage("Netnotes v1.0"));
        });
        
        return menu;
    }
    
    private MenuContext buildMainMenu() {
        ContextPath menuPath = contextPath.append("menu", "main");
        MenuContext menu = new MenuContext(menuPath, "Main Menu", uiRenderer);
        
        menu.addItem("nodes", "Node Manager", () -> {
            // TODO: Launch node manager process
            uiRenderer.render(UIProtocol.showMessage("Node Manager coming soon"));
        });
        
        menu.addItem("files", "File Browser", () -> {
            // TODO: Launch file browser process
            uiRenderer.render(UIProtocol.showMessage("File Browser coming soon"));
        });
        
        menu.addItem("settings", "Settings", () -> {
            showSettingsMenu();
        });
        
        menu.addItem("lock", "Lock System", () -> {
            state.removeState(SystemSessionStates.UNLOCKED);
            state.addState(SystemSessionStates.LOCKED);
        });
        
        menu.addItem("about", "About System", () -> {
            uiRenderer.render(UIProtocol.showMessage("Netnotes v1.0"));
        });
        
        return menu;
    }
    
    private void showSettingsMenu() {
        ContextPath menuPath = contextPath.append("menu", "settings");
        MenuContext menu = new MenuContext(menuPath, "Settings", uiRenderer, 
            menuNavigator.getCurrentMenu());
        
        menu.addItem("change_password", "Change Master Password", 
            "⚠️  Re-encrypts all files", () -> {
            startChangePasswordSession();
        });
        
        menu.addItem("bootstrap", "Bootstrap Configuration", () -> {
            uiRenderer.render(UIProtocol.showMessage("Bootstrap config coming soon"));
        });
        
        menu.addItem("back", "Back to Main Menu", () -> {
            showMainMenu();
        });
        
        menuNavigator.showMenu(menu);
    }
    
    // ===== PASSWORD CHANGE =====
    /**
     * Change master password
     * Goes through AppData to update all encrypted files
     */
    private void startChangePasswordSession() {
        getSecureInputDevice()
            .thenAccept(device -> {
                // First verify current password
                PasswordSessionProcess verifySession = new PasswordSessionProcess(
                    device,
                    uiRenderer,
                    "Enter current password",
                    3
                );
                
                verifySession.onPasswordEntered(currentPassword -> {
                    return appData.verifyPassword(currentPassword)
                        .thenCompose(valid -> {
                            if (valid) {
                                // Password valid, validate disk space before proceeding
                                return validateAndRequestNewPassword(device, currentPassword);
                            } else {
                                uiRenderer.render(UIProtocol.showError("Invalid password"));
                                return CompletableFuture.completedFuture(false);
                            }
                        });
                });
                
                spawnChild(verifySession, "verify-current-password")
                    .thenCompose(path -> registry.startProcess(path));
            });
    }

        
    /**
     * Validate disk space and request new password
     */
    private CompletableFuture<Boolean> validateAndRequestNewPassword(
            InputDevice device,
            NoteBytesEphemeral currentPassword) {
        
        // Show validating message
        uiRenderer.render(UIProtocol.showMessage("Validating disk space..."));
        
        // Pre-validate disk space (async)
        return appData.getNoteFileService().validateDiskSpaceForReEncryption()
            .thenCompose(validation -> {
                
                if (!validation.isValid()) {
                    String error = String.format(
                        "Insufficient disk space for password change.\n\n" +
                        "Files to re-encrypt: %d\n" +
                        "Total size: %.2f MB\n" +
                        "Space required: %.2f MB\n" +
                        "Space available: %.2f MB\n" +
                        "Additional space needed: %.2f MB\n\n" +
                        "Please free up disk space and try again.",
                        validation.getNumberOfFiles(),
                        validation.getTotalFileSizes() / (1024.0 * 1024.0),
                        (validation.getRequiredSpace() + validation.getBufferSpace()) / (1024.0 * 1024.0),
                        validation.getAvailableSpace() / (1024.0 * 1024.0),
                        ((validation.getRequiredSpace() + validation.getBufferSpace()) - 
                            validation.getAvailableSpace()) / (1024.0 * 1024.0)
                    );
                    
                    uiRenderer.render(UIProtocol.showError(error));
                    showMainMenu();
                    return CompletableFuture.completedFuture(false);
                }
                
                // Space validated, show summary and proceed
                String summary = String.format(
                    "Disk space validation passed.\n\n" +
                    "Files to re-encrypt: %d\n" +
                    "Total size: %.2f MB\n" +
                    "Available space: %.2f MB\n\n" +
                    "Ready to change password.",
                    validation.getNumberOfFiles(),
                    validation.getTotalFileSizes() / (1024.0 * 1024.0),
                    validation.getAvailableSpace() / (1024.0 * 1024.0)
                );
                
                uiRenderer.render(UIProtocol.showMessage(summary));
                
                // Proceed with password creation
                return startPasswordCreationForChange(device, currentPassword, validation);
            })
            .exceptionally(ex -> {
                System.err.println("[SystemSession] Disk space validation failed: " + 
                    ex.getMessage());
                
                uiRenderer.render(UIProtocol.showError(
                    "Failed to validate disk space: " + ex.getMessage()));
                
                showMainMenu();
                return false;
            });
    }
            
        
    /**
     * Start password creation session after validation passes
     */
    private CompletableFuture<Boolean> startPasswordCreationForChange(
            InputDevice device,
            NoteBytesEphemeral currentPassword,
            DiskSpaceValidation validation) {
        
        PasswordCreationSession newPasswordSession = new PasswordCreationSession(
            device,
            uiRenderer,
            "Enter new password",
            "Confirm new password"
        );
        
        newPasswordSession.onPasswordCreated(newPassword -> {
            // Calculate optimal batch size from validation
            int batchSize = calculateOptimalBatchSize(validation);
            
            System.out.println("[SystemSession] Starting password change with batch size: " + 
                batchSize);
            
            // Show starting message
            uiRenderer.render(UIProtocol.showMessage(
                "Starting password change. This may take a while..."));
            
            // Create progress tracking process
            File dataDir = SettingsData.getDataDir();
            File recoveryLog = new File(dataDir, "password_change_recovery.log");
            ProgressTrackingProcess progressTracker = new ProgressTrackingProcess(
                uiRenderer, 
                recoveryLog
            );
            
            // Spawn and start progress tracker
            return spawnChild(progressTracker, "progress-tracker")
                .thenCompose(trackerPath -> {
                    // Start the tracker process
                    return registry.startProcess(trackerPath)
                        .thenApply(v -> trackerPath);
                })
                .thenCompose(trackerPath -> {
                    // Request stream channel to progress tracker
                    return registry.requestStreamChannel(contextPath, trackerPath)
                        .thenCompose(progressChannel -> {
                            
                            // Create AsyncNoteBytesWriter with the stream channel
                            AsyncNoteBytesWriter progressWriter = new AsyncNoteBytesWriter(
                                progressChannel.getStream()
                            );
                            
                            // Start password change
                            return appData.changePassword(
                                currentPassword, 
                                newPassword, 
                                batchSize, 
                                progressWriter
                            )
                            .thenApply(result -> {
                                System.out.println("[SystemSession] Password change operation completed");
                                return new PasswordChangeResult(progressWriter, progressChannel, progressTracker);
                            })
                            .exceptionally(ex -> {
                                // Password change failed
                                System.err.println("[SystemSession] Password change operation failed: " + 
                                    ex.getMessage());
                                return new PasswordChangeResult(progressWriter, progressChannel, progressTracker, ex);
                            });
                        });
                })
                .thenCompose(result -> {
                    // Close the writer and channel
                    try {
                        if (result.progressWriter != null) {
                            result.progressWriter.close();
                        }
                        if (result.progressChannel != null) {
                            result.progressChannel.close();
                        }
                    } catch (IOException e) {
                        System.err.println("[SystemSession] Error closing progress stream: " + 
                            e.getMessage());
                    }
                    
                    // Wait for progress tracker to complete reading and processing
                    return result.progressTracker.getCompletionFuture()
                        .thenApply(v -> result);
                })
                .thenApply(result -> {
                    // Check results
                    if (result.exception != null) {
                        // Operation failed
                        System.err.println("[SystemSession] Password change failed: " + 
                            result.exception.getMessage());
                        result.exception.printStackTrace();
                        
                        uiRenderer.render(UIProtocol.showError(
                            "Password change failed: " + result.exception.getMessage() + 
                            "\n\nYour data may be in an inconsistent state.\n" +
                            "Check recovery log for details."));
                        
                    } else if (result.progressTracker.hasErrors()) {
                        // Operation completed but with errors
                        System.err.println("[SystemSession] Password change completed with errors");
                        
                        uiRenderer.render(UIProtocol.showError(
                            "Password change completed with errors.\n\n" +
                            "Files completed: " + result.progressTracker.getCompletedFiles().size() + "\n" +
                            "Files failed: " + result.progressTracker.getFailedFiles().size() + "\n\n" +
                            "Your data may be in an inconsistent state.\n" +
                            "Check recovery log for details."));
                        
                    } else {
                        // Success!
                        System.out.println("[SystemSession] Password change completed successfully");
                        
                        uiRenderer.render(UIProtocol.showMessage(
                            "Master password changed successfully!\n\n" +
                            "All " + result.progressTracker.getCompletedFiles().size() + 
                            " files have been re-encrypted."));
                    }
                    
                    // Return to main menu
                    showMainMenu();
                    return null;
                });
        });
        
        newPasswordSession.onCancelled(() -> {
            uiRenderer.render(UIProtocol.showMessage("Password change cancelled"));
            showMainMenu();
        });
        
        return spawnChild(newPasswordSession, "new-password")
            .thenCompose(path -> registry.startProcess(path))
            .thenApply(v -> true);
    }


        
    /**
     * Calculate optimal batch size from disk space validation
     * 
     * The batch size limits concurrent file re-encryption operations.
     * Each operation creates a .tmp file, so batch size is constrained by disk space.
     */
    private int calculateOptimalBatchSize(DiskSpaceValidation validation) {
        int fileCount = validation.getNumberOfFiles();
        long totalSize = validation.getTotalFileSizes();
        long availableSpace = validation.getAvailableSpace();
        long bufferSpace = validation.getBufferSpace();
        
        if (fileCount == 0) {
            return 1; // No files to process
        }
        
        // Calculate safe working space (available - buffer)
        long safeWorkingSpace = availableSpace - bufferSpace;
        
        // Average file size
        long avgFileSize = totalSize / fileCount;
        
        if (avgFileSize == 0) {
            return 1;
        }
        
        // How many files can we process simultaneously?
        // Each file needs space for its .tmp copy
        int maxSimultaneous = (int) (safeWorkingSpace / avgFileSize);
        
        // Clamp to reasonable bounds
        int minBatch = 1;
        int maxBatch = 50; // Don't overwhelm the system
        int optimalBatch = Math.max(minBatch, Math.min(maxBatch, maxSimultaneous));
        
        System.out.println(String.format(
            "[SystemSession] Batch size calculation:\n" +
            "  Files: %d\n" +
            "  Total size: %.2f MB\n" +
            "  Avg file size: %.2f KB\n" +
            "  Available space: %.2f MB\n" +
            "  Buffer space: %.2f MB\n" +
            "  Safe working space: %.2f MB\n" +
            "  Max simultaneous: %d\n" +
            "  Optimal batch size: %d",
            fileCount,
            totalSize / (1024.0 * 1024.0),
            avgFileSize / 1024.0,
            availableSpace / (1024.0 * 1024.0),
            bufferSpace / (1024.0 * 1024.0),
            safeWorkingSpace / (1024.0 * 1024.0),
            maxSimultaneous,
            optimalBatch
        ));
        
        return optimalBatch;
    }

        
            
        
    /**
     * Check for incomplete password change
     * checks AppData for old key availability
     */
    private CompletableFuture<Void> checkForIncompletePasswordChange() {
        return CompletableFuture.runAsync(() -> {
            try {
                File dataDir = SettingsData.getDataDir();
                File logFile = new File(dataDir, "password_change_recovery.log");
                
                if (!logFile.exists() || logFile.length() == 0) {
                    return; // No recovery needed
                }
                
                // Parse recovery log
                PasswordChangeRecoveryLog.RecoveryAnalysis analysis = 
                    PasswordChangeRecoveryLog.parseLog(logFile);
                
                if (analysis == null || !analysis.needsRecovery()) {
                    // Clean up completed log
                    archiveRecoveryLog(logFile);
                    return;
                }
                
                // Check if old key is available (through AppData)
                boolean hasOldKey = appData.hasOldKeyForRecovery();
                
                // Found incomplete password change - show recovery menu
                System.err.println("[SystemSession] Incomplete password change detected");
                showPasswordRecoveryMenu(analysis, logFile, hasOldKey);
                
            } catch (IOException e) {
                System.err.println("[SystemSession] Error checking recovery: " + 
                    e.getMessage());
            }
        }, VirtualExecutors.getVirtualExecutor());
    }


        
    /**
     * Show recovery menu
     * Updated to pass hasOldKey from AppData
     */
    private void showPasswordRecoveryMenu(
            PasswordChangeRecoveryLog.RecoveryAnalysis analysis,
            File logFile,
            boolean hasOldKey) {
        
        ContextPath menuPath = contextPath.append("menu", "recovery");
        MenuContext recoveryMenu = new MenuContext(
            menuPath, 
            "⚠️ Password Change Recovery Required", 
            uiRenderer
        );
        
        String description = String.format(
            "An incomplete password change was detected.\n\n" +
            "Password Status: NEW password is active\n" +
            "Files successfully updated: %d\n" +
            "Files failed: %d\n" +
            "Files interrupted: %d\n\n" +
            "Old encryption key: %s\n\n" +
            "Choose a recovery option:",
            analysis.filesSucceeded,
            analysis.filesFailed,
            analysis.filesInterrupted,
            hasOldKey ? "✓ Available in memory" : "✗ Not available (system was restarted)"
        );
        
        recoveryMenu.addInfoItem("summary", description);
        recoveryMenu.addSeparator("Recovery Options");
        
        // Option 1: Complete re-encryption (finish the job)
        recoveryMenu.addItem(
            "complete",
            "Complete Re-encryption",
            "Finish updating remaining files to NEW password",
            () -> recoveryCompleteReEncryption(analysis, logFile, hasOldKey)
        );
        
        // Option 2: Rollback (restore to old password)
        recoveryMenu.addItem(
            "rollback",
            "Rollback to OLD Password",
            "⚠️ Revert SettingsData and re-encrypt all files back",
            () -> recoveryRollbackToOldPassword(analysis, logFile, hasOldKey)
        );
        
        // Option 3: View details
        recoveryMenu.addItem(
            "details",
            "View Detailed Status",
            () -> showRecoveryDetails(analysis)
        );
        
        // Option 4: Export log and continue (risky)
        recoveryMenu.addItem(
            "export",
            "Export Log and Continue",
            "⚠️ Proceed without recovery (data may be inconsistent)",
            () -> exportLogAndContinue(logFile)
        );
        
        // Create navigator if needed
        if (menuNavigator == null) {
            createMenuNavigator();
        }
        
        // Block other operations until recovery is handled
        state.addState(SystemSessionStates.RECOVERY_REQUIRED);
        menuNavigator.showMenu(recoveryMenu);
    }


        
    /**
     * Recovery Option 1: Complete re-encryption
     * NOW delegates to AppData instead of doing file operations directly
     */
    private void recoveryCompleteReEncryption(
            PasswordChangeRecoveryLog.RecoveryAnalysis analysis,
            File logFile,
            boolean hasOldKey) {
        
        if (hasOldKey) {
            // Old key available in memory - proceed directly
            uiRenderer.render(UIProtocol.showMessage(
                "Old encryption key found in memory.\n" +
                "Re-encrypting remaining files..."));
            
            performRecoveryReEncryption(analysis, null, logFile);
            
        } else {
            // Need user to enter OLD password
            uiRenderer.render(UIProtocol.showMessage(
                "Old encryption key not available.\n\n" +
                "Please enter the OLD password (before the failed change).\n" +
                "This will be used to decrypt files that haven't been updated yet."));
            
            getSecureInputDevice()
                .thenAccept(device -> {
                    PasswordSessionProcess session = new PasswordSessionProcess(
                        device,
                        uiRenderer,
                        "Enter OLD password (before password change)",
                        3
                    );
                    
                    session.onPasswordEntered(oldPassword -> {
                        // Verify old password through AppData
                        return appData.verifyOldPassword(oldPassword)
                            .thenCompose(valid -> {
                                if (!valid) {
                                    uiRenderer.render(UIProtocol.showError(
                                        "Invalid old password"));
                                    return CompletableFuture.completedFuture(false);
                                }
                                
                                // Perform recovery with old password
                                performRecoveryReEncryption(analysis, oldPassword, logFile);
                                return CompletableFuture.completedFuture(true);
                            });
                    });
                    
                    spawnChild(session, "recovery-old-password")
                        .thenCompose(path -> registry.startProcess(path));
                });
        }
    }

    /**
     * Perform recovery re-encryption using AppData API
     */
    private void performRecoveryReEncryption(
            PasswordChangeRecoveryLog.RecoveryAnalysis analysis,
            NoteBytesEphemeral oldPassword,
            File logFile) {
        
        // Collect files that need re-encryption
        List<String> filesToReEncrypt = new ArrayList<>();
        for (PasswordChangeRecoveryLog.FileRecoveryState fileState : 
                analysis.fileStates.values()) {
            if (fileState.isInterrupted() || fileState.failed) {
                filesToReEncrypt.add(fileState.filePath);
            }
        }
        
        if (filesToReEncrypt.isEmpty()) {
            uiRenderer.render(UIProtocol.showMessage(
                "No files need re-encryption. Recovery complete."));
            archiveRecoveryLog(logFile);
            state.removeState(SystemSessionStates.RECOVERY_REQUIRED);
            showMainMenu();
            return;
        }
        
        uiRenderer.render(UIProtocol.showMessage(
            String.format("Re-encrypting %d files from OLD key to NEW key...", 
                filesToReEncrypt.size())));
        
        // Create progress tracker
        File continuationLog = new File(logFile.getParent(), 
            "password_change_recovery_continuation.log");
        ProgressTrackingProcess progressTracker = new ProgressTrackingProcess(
            uiRenderer, 
            continuationLog
        );
        
        // Spawn and execute
        spawnChild(progressTracker, "recovery-progress")
            .thenCompose(trackerPath -> registry.startProcess(trackerPath))
            .thenCompose(v -> registry.requestStreamChannel(
                contextPath, progressTracker.getContextPath()))
            .thenCompose(progressChannel -> {
                AsyncNoteBytesWriter progressWriter = new AsyncNoteBytesWriter(
                    progressChannel.getStream());
                
                // DELEGATE to AppData - it handles all the complexity
                return appData.completePasswordChange(
                    filesToReEncrypt,
                    oldPassword,
                    10,  // batch size
                    progressWriter
                )
                .whenComplete((success, ex) -> {
                    try {
                        progressWriter.close();
                        progressChannel.close();
                    } catch (IOException e) {
                        System.err.println("Error closing progress stream: " + 
                            e.getMessage());
                    }
                });
            })
            .thenCompose(success -> progressTracker.getCompletionFuture()
                .thenApply(v -> success))
            .thenAccept(success -> {
                if (success) {
                    // Archive logs
                    archiveRecoveryLog(logFile);
                    archiveRecoveryLog(continuationLog);
                    
                    state.removeState(SystemSessionStates.RECOVERY_REQUIRED);
                    
                    uiRenderer.render(UIProtocol.showMessage(
                        "✓ Recovery completed successfully!\n\n" +
                        "All files have been updated to the new password."));
                    
                    showMainMenu();
                    
                } else {
                    uiRenderer.render(UIProtocol.showError(
                        "Recovery completed with errors.\n\n" +
                        "Some files may not have been updated.\n" +
                        "Check recovery log for details."));
                }
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Recovery failed: " + ex.getMessage()));
                return null;
            });
    }

        
    /**
     * Recovery Option 2: Rollback to old password
     * NOW delegates to AppData instead of doing operations directly
     */
    private void recoveryRollbackToOldPassword(
            PasswordChangeRecoveryLog.RecoveryAnalysis analysis,
            File logFile,
            boolean hasOldKey) {
        
        // Validate rollback is possible
        if (!hasOldKey && !appData.hasOldKeyForRecovery()) {
            uiRenderer.render(UIProtocol.showError(
                "Cannot rollback: Old key and salt not available.\n\n" +
                "Rollback is only possible if:\n" +
                "1. System hasn't been restarted, OR\n" +
                "2. You can provide the old password"));
            return;
        }
        
        uiRenderer.render(UIProtocol.showMessage(
            "⚠️ ROLLBACK WARNING\n\n" +
            "This will:\n" +
            "1. Restore SettingsData to OLD password\n" +
            "2. Re-encrypt successfully updated files back to OLD key\n\n" +
            "Files to rollback: " + analysis.filesSucceeded + "\n\n" +
            "You will need to enter the OLD password to confirm."));
        
        // Always ask for old password (for verification)
        getSecureInputDevice()
            .thenAccept(device -> {
                PasswordSessionProcess session = new PasswordSessionProcess(
                    device,
                    uiRenderer,
                    "Enter OLD password to confirm rollback",
                    3
                );
                
                session.onPasswordEntered(oldPassword -> {
                    return performRollback(analysis, oldPassword, logFile);
                });
                
                spawnChild(session, "rollback-password")
                    .thenCompose(path -> registry.startProcess(path));
            });
    }
        
    /**
     * Perform complete rollback using AppData API
     * Much simpler - AppData handles coordination
     */
    private CompletableFuture<Boolean> performRollback(
            PasswordChangeRecoveryLog.RecoveryAnalysis analysis,
            NoteBytesEphemeral oldPassword,
            File logFile) {
        
        uiRenderer.render(UIProtocol.showMessage(
            "Starting rollback...\n\n" +
            "AppData will coordinate SettingsData and file rollback"));
        
        // Collect files that were successfully updated (need rollback)
        List<String> filesToRollback = new ArrayList<>();
        for (PasswordChangeRecoveryLog.FileRecoveryState fileState : 
                analysis.fileStates.values()) {
            if (fileState.succeeded) {
                filesToRollback.add(fileState.filePath);
            }
        }
        
        if (filesToRollback.isEmpty()) {
            uiRenderer.render(UIProtocol.showMessage(
                "No files need rollback (none were updated).\n" +
                "Only SettingsData will be rolled back."));
            // Still need to rollback SettingsData
        }
        
        // Create progress tracker
        File rollbackLog = new File(logFile.getParent(), 
            "password_change_rollback.log");
        ProgressTrackingProcess progressTracker = new ProgressTrackingProcess(
            uiRenderer, 
            rollbackLog
        );
        
        // Execute rollback
        return spawnChild(progressTracker, "rollback-progress")
            .thenCompose(trackerPath -> registry.startProcess(trackerPath))
            .thenCompose(v -> registry.requestStreamChannel(
                contextPath, progressTracker.getContextPath()))
            .thenCompose(progressChannel -> {
                AsyncNoteBytesWriter progressWriter = new AsyncNoteBytesWriter(
                    progressChannel.getStream());
                
                // DELEGATE to AppData - it handles everything
                return appData.rollbackPasswordChange(
                    filesToRollback,
                    oldPassword,
                    10,
                    progressWriter
                )
                .whenComplete((success, ex) -> {
                    try {
                        progressWriter.close();
                        progressChannel.close();
                    } catch (IOException e) {
                        System.err.println("Error closing stream: " + e.getMessage());
                    }
                });
            })
            .thenCompose(success -> progressTracker.getCompletionFuture()
                .thenApply(v -> success))
            .thenApply(success -> {
                if (success) {
                    completeRollback(logFile);
                    archiveRecoveryLog(rollbackLog);
                } else {
                    uiRenderer.render(UIProtocol.showError(
                        "Rollback completed with errors.\n" +
                        "Check rollback log for details."));
                }
                return success;
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Rollback failed: " + ex.getMessage()));
                return false;
            });
    }


    /**
     * Complete rollback and return to normal operation
     */
    private void completeRollback(File logFile) {
        archiveRecoveryLog(logFile);
        state.removeState(SystemSessionStates.RECOVERY_REQUIRED);
        
        uiRenderer.render(UIProtocol.showMessage(
            "✓ Rollback completed successfully!\n\n" +
            "System has been restored to the previous password.\n" +
            "All files have been reverted."));
        
        showMainMenu();
    }

    /**
     * Re-encrypt specific files (generic helper)
     * 
     * @param filePaths Files to re-encrypt
     * @param oldKey Key to decrypt with
     * @param newKey Key to encrypt with
     * @param batchSize Concurrent batch size
     * @param progressWriter Progress tracking
     */
    private CompletableFuture<Boolean> reEncryptSpecificFiles(
            List<String> filePaths,
            SecretKey oldKey,
            SecretKey newKey,
            int batchSize,
            AsyncNoteBytesWriter progressWriter) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Semaphore semaphore = new Semaphore(batchSize, true);
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                AtomicInteger completed = new AtomicInteger(0);
                
                for (String filePath : filePaths) {
                    File file = new File(filePath);
                    if (!file.exists() || !file.isFile()) {
                        System.err.println("[Recovery] File not found: " + filePath);
                        continue;
                    }
                    
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        try {
                            semaphore.acquire();
                            try {
                                File tmpFile = new File(file.getAbsolutePath() + ".tmp");
                                
                                ProgressMessage.writeAsync(ProtocolMesssages.STARTING,
                                    completed.get(), filePaths.size(), filePath, 
                                    progressWriter);
                                
                                FileStreamUtils.updateFileEncryption(
                                    oldKey, newKey, file, tmpFile, progressWriter);
                                
                                completed.incrementAndGet();
                                
                                ProgressMessage.writeAsync(ProtocolMesssages.SUCCESS,
                                    completed.get(), filePaths.size(), filePath, 
                                    progressWriter);
                                
                            } finally {
                                semaphore.release();
                            }
                        } catch (Exception e) {
                            TaskMessages.writeErrorAsync(ProtocolMesssages.ERROR,
                                filePath, e, progressWriter);
                            throw new RuntimeException("Failed: " + filePath, e);
                        }
                    }, appData.getExecService());
                    
                    futures.add(future);
                }
                
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                return true;
                
            } catch (Exception e) {
                System.err.println("[Recovery] Re-encryption failed: " + e.getMessage());
                return false;
            }
        }, VirtualExecutors.getVirtualExecutor());
    }

            // ===== MESSAGE HANDLERS =====
    
    private CompletableFuture<Void> handleMenuSelection(
            NoteBytesMap msg, RoutedPacket packet) {
        
        if (menuNavigator != null) {
            menuNavigator.getSubscriber().onNext(packet);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleBack(
            NoteBytesMap msg, RoutedPacket packet) {
        
        if (menuNavigator != null) {
            menuNavigator.getSubscriber().onNext(packet);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handlePasswordEntered(
            NoteBytesMap msg, RoutedPacket packet) {
        
        if (passwordSession != null) {
            passwordSession.getSubscriber().onNext(packet);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleCancelled(
            NoteBytesMap msg, RoutedPacket packet) {
        
        // Cancel current operation
        if (passwordSession != null) {
            passwordSession.cancel();
            registry.unregisterProcess(passwordSession.getContextPath());
            passwordSession = null;
        }
        
        // Return to appropriate state
        if (state.hasState(SystemSessionStates.UNLOCKING)) {
            state.removeState(SystemSessionStates.UNLOCKING);
            state.addState(SystemSessionStates.LOCKED);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleLockSystem(
            NoteBytesMap msg, RoutedPacket packet) {
        
        if (state.hasState(SystemSessionStates.UNLOCKED)) {
            state.removeState(SystemSessionStates.UNLOCKED);
            state.addState(SystemSessionStates.LOCKED);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleUnlockSystem(
            NoteBytesMap msg, RoutedPacket packet) {
        
        if (SystemSessionStates.canUnlock(state)) {
            state.removeState(SystemSessionStates.LOCKED);
            state.addState(SystemSessionStates.UNLOCKING);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> handleChangePassword(
            NoteBytesMap msg, RoutedPacket packet) {
        
        if (state.hasState(SystemSessionStates.UNLOCKED)) {
            startChangePasswordSession();
        } else {
            uiRenderer.render(UIProtocol.showError("System must be unlocked to change password"));
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== GETTERS =====
    
    public BitFlagStateMachine getState() {
        return state;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public SessionType getSessionType() {
        return sessionType;
    }
    
    public boolean isLocked() {
        return state.hasState(SystemSessionStates.LOCKED);
    }
    
    public boolean isUnlocked() {
        return state.hasState(SystemSessionStates.UNLOCKED);
    }
    
    /**
     * Get AppData (primary interface)
     */
    public AppData getAppData() {
        return appData;
    }
    
    /**
     * Check if system is fully initialized
     */
    public boolean isReady() {
        return state.hasState(SystemSessionStates.READY) && appData != null;
    }
    
    // ===== SESSION TYPE =====
    
    public enum SessionType {
        PHYSICAL,  // Local UI
        NETWORK    // Remote connection
    }

    /**
     * Recovery Helper Methods
     * Location: SystemSessionProcess.java
     */

    /**
     * Archive completed recovery log
     * Renames log file with timestamp for historical tracking
     * 
     * @param logFile Recovery log to archive
     */
    private void archiveRecoveryLog(File logFile) {
        if (logFile == null || !logFile.exists()) {
            return;
        }
        
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            String baseName = logFile.getName().replace(".log", "");
            String archivedName = baseName + "_completed_" + timestamp + ".log";
            
            File archivedLog = new File(logFile.getParent(), archivedName);
            
            Files.move(logFile.toPath(), archivedLog.toPath());
            
            System.out.println("[SystemSession] Recovery log archived: " + 
                archivedLog.getName());
            
        } catch (IOException e) {
            System.err.println("[SystemSession] Failed to archive recovery log: " + 
                e.getMessage());
            
            // Try to delete if move failed
            try {
                Files.deleteIfExists(logFile.toPath());
            } catch (IOException deleteEx) {
                System.err.println("[SystemSession] Failed to delete recovery log: " + 
                    deleteEx.getMessage());
            }
        }
    }

    /**
     * Show detailed recovery information to user
     * Displays file-by-file status from recovery analysis
     * 
     * @param analysis Recovery analysis with file states
     */
    private void showRecoveryDetails(PasswordChangeRecoveryLog.RecoveryAnalysis analysis) {
        StringBuilder details = new StringBuilder();
        
        // Header
        details.append("═══════════════════════════════════════\n");
        details.append("  Password Change Recovery Details\n");
        details.append("═══════════════════════════════════════\n\n");
        
        // Summary
        details.append(analysis.getSummary()).append("\n");
        
        // Separator
        details.append("───────────────────────────────────────\n");
        details.append("File Status:\n");
        details.append("───────────────────────────────────────\n\n");
        
        // Group files by status
        List<PasswordChangeRecoveryLog.FileRecoveryState> successFiles = new ArrayList<>();
        List<PasswordChangeRecoveryLog.FileRecoveryState> failedFiles = new ArrayList<>();
        List<PasswordChangeRecoveryLog.FileRecoveryState> interruptedFiles = new ArrayList<>();
        
        for (PasswordChangeRecoveryLog.FileRecoveryState state : 
                analysis.fileStates.values()) {
            if (state.succeeded) {
                successFiles.add(state);
            } else if (state.failed) {
                failedFiles.add(state);
            } else if (state.isInterrupted()) {
                interruptedFiles.add(state);
            }
        }
        
        // Show successful files (collapsed if many)
        if (!successFiles.isEmpty()) {
            details.append("✓ Successfully Updated (").append(successFiles.size())
                .append(" files):\n");
            
            if (successFiles.size() <= 10) {
                for (PasswordChangeRecoveryLog.FileRecoveryState state : successFiles) {
                    details.append("  ✓ ").append(shortenPath(state.filePath))
                        .append(" (").append(formatFileSize(state.fileSize)).append(")\n");
                }
            } else {
                // Show first 5 and last 5
                for (int i = 0; i < Math.min(5, successFiles.size()); i++) {
                    PasswordChangeRecoveryLog.FileRecoveryState state = successFiles.get(i);
                    details.append("  ✓ ").append(shortenPath(state.filePath))
                        .append(" (").append(formatFileSize(state.fileSize)).append(")\n");
                }
                
                if (successFiles.size() > 10) {
                    details.append("  ... (").append(successFiles.size() - 10)
                        .append(" more files)\n");
                }
                
                for (int i = Math.max(5, successFiles.size() - 5); 
                    i < successFiles.size(); i++) {
                    PasswordChangeRecoveryLog.FileRecoveryState state = successFiles.get(i);
                    details.append("  ✓ ").append(shortenPath(state.filePath))
                        .append(" (").append(formatFileSize(state.fileSize)).append(")\n");
                }
            }
            details.append("\n");
        }
        
        // Show failed files (always show all - important!)
        if (!failedFiles.isEmpty()) {
            details.append("✗ Failed (").append(failedFiles.size()).append(" files):\n");
            
            for (PasswordChangeRecoveryLog.FileRecoveryState state : failedFiles) {
                details.append("  ✗ ").append(shortenPath(state.filePath))
                    .append(" (").append(formatFileSize(state.fileSize)).append(")\n");
                
                if (state.errorMessage != null && !state.errorMessage.isEmpty()) {
                    details.append("     Error: ").append(state.errorMessage).append("\n");
                }
            }
            details.append("\n");
        }
        
        // Show interrupted files (always show all - important!)
        if (!interruptedFiles.isEmpty()) {
            details.append("⚠ Interrupted (").append(interruptedFiles.size())
                .append(" files):\n");
            
            for (PasswordChangeRecoveryLog.FileRecoveryState state : interruptedFiles) {
                details.append("  ⚠ ").append(shortenPath(state.filePath))
                    .append(" (").append(formatFileSize(state.fileSize)).append(")\n");
                details.append("     Status: Started but not completed\n");
            }
            details.append("\n");
        }
        
        // Footer
        details.append("───────────────────────────────────────\n");
        details.append("Recovery Actions:\n");
        details.append("  • Complete: Update remaining files to NEW password\n");
        details.append("  • Rollback: Restore all files to OLD password\n");
        details.append("═══════════════════════════════════════\n");
        
        // Display via UI
        uiRenderer.render(UIProtocol.showMessage(details.toString()));
    }

    /**
     * Shorten file path for display (show basename and parent)
     * Example: /very/long/path/to/file.dat → .../to/file.dat
     */
    private String shortenPath(String path) {
        if (path == null || path.length() <= 50) {
            return path;
        }
        
        File file = new File(path);
        String fileName = file.getName();
        String parentName = file.getParentFile() != null ? 
            file.getParentFile().getName() : "";
        
        if (parentName.isEmpty()) {
            return ".../" + fileName;
        } else {
            return ".../" + parentName + "/" + fileName;
        }
    }

    /**
     * Format file size for human-readable display
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Export log and continue (manual recovery path)
     * Archives log and allows system to proceed despite inconsistency
     */
    private void exportLogAndContinue(File logFile) {
        if (logFile == null || !logFile.exists()) {
            uiRenderer.render(UIProtocol.showError("Recovery log not found"));
            return;
        }
        
        try {
            // Archive with special name indicating manual intervention needed
            String timestamp = String.valueOf(System.currentTimeMillis());
            String archivedName = "password_change_MANUAL_RECOVERY_" + timestamp + ".log";
            File archivedLog = new File(logFile.getParent(), archivedName);
            
            Files.move(logFile.toPath(), archivedLog.toPath());
            
            // Clear recovery state
            state.removeState(SystemSessionStates.RECOVERY_REQUIRED);
            
            // Show warning message
            String message = String.format(
                "⚠️ Recovery log exported for manual intervention\n\n" +
                "Location: %s\n\n" +
                "WARNING: Your encrypted files are in an inconsistent state.\n" +
                "Some files use the OLD password, others use the NEW password.\n\n" +
                "Manual Recovery Steps:\n" +
                "1. Review the exported log file\n" +
                "2. Identify which files failed/interrupted\n" +
                "3. Manually re-encrypt using appropriate tools\n\n" +
                "The system will now continue with the NEW password active.\n" +
                "Files that were not updated may be inaccessible.",
                archivedLog.getAbsolutePath()
            );
            
            uiRenderer.render(UIProtocol.showError(message));
            
            // Log the decision
            System.err.println("[SystemSession] User chose manual recovery path");
            System.err.println("[SystemSession] Log exported to: " + archivedLog.getAbsolutePath());
            
            // Return to main menu
            showMainMenu();
            
        } catch (IOException e) {
            uiRenderer.render(UIProtocol.showError(
                "Failed to export recovery log: " + e.getMessage()));
            
            System.err.println("[SystemSession] Failed to export log: " + e.getMessage());
        }
    }

    /**
     * Helper class to pass password change result through completion chain
     */
    private static class PasswordChangeResult {
        final AsyncNoteBytesWriter progressWriter;
        final StreamChannel progressChannel;
        final ProgressTrackingProcess progressTracker;
        final Throwable exception;
        
        PasswordChangeResult(AsyncNoteBytesWriter progressWriter, 
                            StreamChannel progressChannel,
                            ProgressTrackingProcess progressTracker) {
            this(progressWriter, progressChannel, progressTracker, null);
        }
        
        PasswordChangeResult(AsyncNoteBytesWriter progressWriter,
                            StreamChannel progressChannel,
                            ProgressTrackingProcess progressTracker,
                            Throwable exception) {
            this.progressWriter = progressWriter;
            this.progressChannel = progressChannel;
            this.progressTracker = progressTracker;
            this.exception = exception;
        }
    }
}