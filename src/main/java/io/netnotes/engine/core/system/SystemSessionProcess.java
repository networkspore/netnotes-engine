package io.netnotes.engine.core.system;

import io.netnotes.engine.core.SystemRuntime;
import io.netnotes.engine.core.RuntimeAccess;
import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.SystemProcess.SYSTEM_INIT_CMDS;
import io.netnotes.engine.core.system.control.*;
import io.netnotes.engine.core.system.control.nodes.InstalledPackage;
import io.netnotes.engine.core.system.control.nodes.NodeInstance;
import io.netnotes.engine.core.system.control.nodes.NodeLoadRequest;
import io.netnotes.engine.core.system.control.nodes.PackageInfo;
import io.netnotes.engine.core.system.control.recovery.RecoveryFlags;
import io.netnotes.engine.core.system.control.ui.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.DiskSpaceValidation;
import io.netnotes.engine.noteFiles.notePath.NoteFileService;
import io.netnotes.engine.noteFiles.notePath.NoteFileService.FileEncryptionAnalysis;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.TimeHelpers;
import io.netnotes.engine.utils.VirtualExecutors;
import io.netnotes.engine.utils.streams.StreamUtils;

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
    
    private SystemRuntime appData = null; 
    private RuntimeAccess systemAccess = null;

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
        UIRenderer uiRenderer
    ) {
        
        super(sessionId, ProcessType.BIDIRECTIONAL);
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
                PasswordCreationSession passwordCreationSession = new PasswordCreationSession(
                    "firstrun-password-creation",
                    device,
                    uiRenderer,
                    "Create master password",
                    "Confirm master password"
                );
                
                // Handle successful password creation
                passwordCreationSession.onPasswordCreated(password -> {
                    return createNewSystem(password)
                        .thenRun(() -> {
                            state.removeState(SystemSessionStates.FIRST_RUN_SETUP);
                            state.addState(SystemSessionStates.READY);
                            
                            uiRenderer.render(UIProtocol.showMessage(
                                "Master password created successfully"));
                         
                        }).exceptionally(ex->{
                            //TODO: Handle failure to create system -> Set state ERROR?
                            uiRenderer.render(UIProtocol.showError(
                                "Error creating new system: " + ex.getMessage()));
                            return null;
                        });
                });
                
                // Handle cancellation
                passwordCreationSession.onCancelled(() -> {
                    uiRenderer.render(UIProtocol.showError(
                        "Password creation cancelled - cannot proceed"));
                });
                
                // Spawn session
                spawnChild(passwordCreationSession)
                    .thenCompose(path -> registry.startProcess(path));
            });
    }


    
   /**
     * Create new system - provide empty access object
     */
    private CompletableFuture<Void> createNewSystem(NoteBytesEphemeral password) {
        return SettingsData.createSettings(password)
            .thenApply(settingsData -> {
                // Create empty access object
                RuntimeAccess access = new RuntimeAccess();
             
                
                // Create AppData - it fills the access object
                SystemRuntime newAppData = new SystemRuntime(
                    contextPath,
                    settingsData,
                    registry,
                    access  // AppData fills this with closures!
                );
                
                // Store both
                this.appData = newAppData;
                this.systemAccess = access; // Now filled with capabilities
                
                return null;
            });
    }
    
    /**
     * Existing settings: Verify password
     * Loads ephemeral map → verifies → creates SettingsData → AppData
     */
    private void startPasswordVerificationSession() {
        getSecureInputDevice()
            .thenAccept(device -> {
                PasswordSessionProcess pwdVerifySession = new PasswordSessionProcess(
                    "existing-password-verification",
                    device,
                    uiRenderer,
                    "Enter master password",
                    0
                );
                
                // Set password verification handler
                pwdVerifySession.onPasswordEntered(password -> {
                    return verifyAndLoadSystem(password)
                        .thenApply(valid -> {
                            if (valid) {
                                
                                state.removeState(SystemSessionStates.SETTINGS_EXIST);
                                state.addState(SystemSessionStates.READY);
                                
                                return true;
                            } else {
                                uiRenderer.render(UIProtocol.showError("Invalid password"));
                                return false;
                            }
                        });
                });
                
                this.passwordSession = pwdVerifySession;
                
                // Spawn session
                spawnChild(pwdVerifySession)
                    .thenCompose(path -> registry.startProcess(path));
            });
    }
        
        
    /**
     * Password verification during initialization
     * NOW uses AppData instead of SettingsData directly
     */
    private CompletableFuture<Boolean> verifyAndLoadSystem(NoteBytesEphemeral password) {
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
                                    RuntimeAccess access = new RuntimeAccess();
                                    // Create AppData (SettingsData becomes private)
                                    SystemRuntime newAppData = new SystemRuntime(
                                        contextPath,
                                        settingsData, 
                                        registry,
                                        access
                                    );

                                    this.appData = newAppData;
                                    this.systemAccess = access; // Now filled with capabilities
                                    System.out.println("[SystemSession] AppData created");
                                    
                                    return true;
                                });
                        } else {
                            System.out.println("[SystemSession] Password invalid");
                            return CompletableFuture.completedFuture(false);
                        }
                    });
            });
    }
        
    
    private void startUnlockPasswordSession() {
        getSecureInputDevice()
            .thenAccept(device -> {
                PasswordSessionProcess pwdUnlockSession = new PasswordSessionProcess(
                    "unlock-password-session",
                    device,
                    uiRenderer,
                    "Enter password to unlock",
                    0
                );
                
                pwdUnlockSession.onPasswordEntered(password -> {
                    return systemAccess.verifyPassword(password)
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
                
                this.passwordSession = pwdUnlockSession;
                spawnChild(pwdUnlockSession)
                    .thenCompose(path -> registry.startProcess(path));
            });
    }
    
    // ===== INPUT DEVICE ACCESS =====
    
    /**
     * Get secure input device from parent (SystemProcess)
     */
    private CompletableFuture<InputDevice> getSecureInputDevice() {
        if (parentPath == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("No parent process"));
        }
        
        // Request device from SystemProcess
        return request(parentPath, Duration.ofSeconds(5),
            new NoteBytesPair(Keys.CMD, SYSTEM_INIT_CMDS.GET_SECURE_INPUT_DEVICE)
        ).thenApply(response -> {
            NoteBytesMap resp = response.getPayload().getAsNoteBytesMap();
            String devicePath = resp.get(Keys.PATH).getAsString();
            
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
        menuNavigator = new MenuNavigatorProcess("system-session-menu-navigator",uiRenderer);
        
        spawnChild(menuNavigator)
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
     * Request secure input installation (delegates to SystemProcess)
     */
    private void requestSecureInputInstallation() {
        if (parentPath == null) {
            uiRenderer.render(UIProtocol.showError("Cannot communicate with system"));
            return;
        }
        
        // Send request to SystemProcess
        NoteBytesMap request = new NoteBytesMap();
        request.put(Keys.CMD, SYSTEM_INIT_CMDS.INSTALL_SECURE_INPUT);
        
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
        
        // Send request to SystemProcess
        NoteBytesMap request = new NoteBytesMap();
        request.put(Keys.CMD, new NoteBytes(SYSTEM_INIT_CMDS.RECCONFIGURE_BOOTSTRAP));
        
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
            
            subMenu.addItem(SYSTEM_INIT_CMDS.INSTALL_SECURE_INPUT.getAsString(), "Install Secure Input", 
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
            uiRenderer.render(UIProtocol.showMessage("Netnotes v1.0.0"));
        });
        
        return menu;
    }
    
    private MenuContext buildMainMenu() {
        ContextPath menuPath = contextPath.append("menu", "main");
        MenuContext menu = new MenuContext(menuPath, "Main Menu", uiRenderer);
        
        menu.addItem("nodes", "Node Manager", () -> {
            showNodeManagerMenu();
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
                PasswordSessionProcess changeVerifySession = new PasswordSessionProcess(
                    "change-password-verify-current",
                    device,
                    uiRenderer,
                    "Enter current password",
                    3
                );
                
                changeVerifySession.onPasswordEntered(currentPassword -> {
                    return systemAccess.verifyPassword(currentPassword)
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
                
                spawnChild(changeVerifySession)
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
        return systemAccess.validateDiskSpaceForReEncryption()
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
        DiskSpaceValidation validation
    ) {
        
        PasswordCreationSession newPasswordSession = new PasswordCreationSession(
            "change-password-session",
            device,
            uiRenderer,
            "Enter new password",
            "Confirm new password"
        );
        
        newPasswordSession.onPasswordCreated(newPassword -> {
            int batchSize = calculateOptimalBatchSize(validation);
            
            System.out.println("[SystemSession] Starting password change with batch size: " + 
                batchSize);
            
            uiRenderer.render(UIProtocol.showMessage(
                "Starting password change. This may take a while..."));
            
            // Create progress tracker (UI feedback only)
            ProgressTrackingProcess progressTracker = new ProgressTrackingProcess("UI-pwd-change-tracker",uiRenderer);
            
            return spawnChild(progressTracker)
                .thenCompose(trackerPath -> registry.startProcess(trackerPath))
                .thenCompose(v -> requestStreamChannel(progressTracker.getContextPath()))
                .thenCompose(progressChannel -> {
                    AsyncNoteBytesWriter progressWriter = new AsyncNoteBytesWriter(
                        progressChannel.getStream()
                    );
                    
                    // Execute password change
                    return systemAccess.changePassword(
                        currentPassword, 
                        newPassword, 
                        batchSize, 
                        progressWriter
                    )
                    .handle((result, exception) -> {
                        // Close resources
                        try {
                            progressWriter.close();
                            progressChannel.close();
                        } catch (IOException e) {
                            System.err.println("[SystemSession] Error closing progress: " + 
                                e.getMessage());
                        }
                        
                        // Return result with exception info
                        return new PasswordChangeResult(
                            progressTracker, 
                            exception
                        );
                    });
                })
                .thenCompose(result -> {
                    // Wait for progress tracker to finish
                    return result.progressTracker.getCompletionFuture()
                        .thenApply(v -> result);
                })
                .thenApply(result -> {
                    // Analyze results
                    return handlePasswordChangeCompletion(result);
                });
        });
        
        newPasswordSession.onCancelled(() -> {
            uiRenderer.render(UIProtocol.showMessage("Password change cancelled"));
            showMainMenu();
        });
        
        return spawnChild(newPasswordSession)
            .thenCompose(path -> registry.startProcess(path))
            .thenApply(v -> true);
    }


    private Void handlePasswordChangeCompletion(PasswordChangeResult result) {
        
        if (result.exception != null) {
            // CRITICAL: Exception during password change
            System.err.println("[SystemSession] Password change FAILED: " + 
                result.exception.getMessage());
            
            // Set recovery flag
            RecoveryFlags.setRecoveryNeeded("password_change_exception: " + 
                result.exception.getMessage());
            
            // Enter recovery mode IMMEDIATELY
            state.addState(SystemSessionStates.RECOVERY_REQUIRED);
            
            uiRenderer.render(UIProtocol.showError(
                "Password change failed with error.\n\n" +
                "The system will now enter recovery mode to assess the situation."));
            
            // Trigger recovery investigation
            investigateAndShowRecoveryMenu();
            
            return null;
        }
        
        // Check progress tracker for partial failures
        if (result.progressTracker != null && result.progressTracker.hasErrors()) {
            // PARTIAL SUCCESS: Some files failed
            int succeeded = result.progressTracker.getCompletedFiles().size();
            int failed = result.progressTracker.getFailedFiles().size();
            
            System.err.println(String.format(
                "[SystemSession] Password change PARTIAL: %d succeeded, %d failed",
                succeeded, failed));
            
            // Set recovery flag
            RecoveryFlags.setRecoveryNeeded(String.format(
                "password_change_partial: %d files failed", failed));
            
            // Enter recovery mode
            state.addState(SystemSessionStates.RECOVERY_REQUIRED);
            
            uiRenderer.render(UIProtocol.showError(
                "Password change completed with errors.\n\n" +
                "Files completed: " + succeeded + "\n" +
                "Files failed: " + failed + "\n\n" +
                "Entering recovery mode to assess file encryption state."));
            
            // Trigger recovery investigation
            investigateAndShowRecoveryMenu();
            
            return null;
        }
        
        // FULL SUCCESS
        System.out.println("[SystemSession] Password change completed successfully");
        
        // Clear any existing recovery flag
        RecoveryFlags.clearRecoveryFlag();
        
        uiRenderer.render(UIProtocol.showMessage(
            "Master password changed successfully!\n\n" +
            "All files have been re-encrypted."));
        
        showMainMenu();
        return null;
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
     */
     private CompletableFuture<Void> checkForIncompletePasswordChange() {
        return CompletableFuture.runAsync(() -> {
            
            // Check for recovery flag
            if (!RecoveryFlags.isRecoveryNeeded()) {
                // No flag, no recovery needed
                return;
            }
            
            String reason = RecoveryFlags.getRecoveryReason();
            System.err.println("[SystemSession] Recovery flag detected: " + reason);
            
            // Show recovery UI
            uiRenderer.render(UIProtocol.showMessage(
                "⚠️ System requires recovery\n\n" +
                "Reason: " + reason + "\n\n" +
                "Investigating file encryption state..."));
            
            // Enter recovery mode
            state.addState(SystemSessionStates.RECOVERY_REQUIRED);
            
            // Investigate and show options
            investigateAndShowRecoveryMenu();
            
        }, VirtualExecutors.getVirtualExecutor());
    }

    private void investigateAndShowRecoveryMenu() {
        
        uiRenderer.render(UIProtocol.showMessage("Analyzing file encryption state..."));
        
        // Check if old key available
        boolean hasOldKey =  systemAccess.hasOldKeyForRecovery();
        
        if (!hasOldKey) {
            // No old key - offer to provide password or skip
            showRecoveryMenuNoOldKey();
            return;
        }
        
        // Old key available - analyze files
        systemAccess.investigateFileEncryption()
            .thenAccept(analysis -> {
                showRecoveryMenu(analysis, true);
            })
            .exceptionally(ex -> {
                System.err.println("[SystemSession] Investigation failed: " + ex.getMessage());
                
                uiRenderer.render(UIProtocol.showError(
                    "Failed to investigate file state: " + ex.getMessage()));
                
                // Offer basic options without analysis
                showRecoveryMenuNoAnalysis();
                return null;
            });
    }

        
    private void showRecoveryMenu(FileEncryptionAnalysis analysis, boolean hasOldKey) {
        
        ContextPath menuPath = contextPath.append("menu", "recovery");
        MenuContext recoveryMenu = new MenuContext(
            menuPath, 
            "⚠️ Recovery Mode", 
            uiRenderer
        );
        
        // Show current state
        recoveryMenu.addInfoItem("summary", analysis.getSummary());
        
        // Show progress if any operations completed
        if (analysis.getCompletionPercentage() > 0) {
            recoveryMenu.addInfoItem("progress", 
                String.format("\nProgress: %.1f%% complete\n", 
                    analysis.getCompletionPercentage()));
        }
        
        recoveryMenu.addSeparator("Recovery Actions");
        
        // OPTION 0: Perform ALL actions at once (if multiple issues exist)
        int issueCount = 0;
        if (!analysis.getFilesNeedingUpdate().isEmpty()) issueCount++;
        if (!analysis.getFilesNeedingSwap().isEmpty()) issueCount++;
        if (!analysis.getFilesNeedingCleanup().isEmpty()) issueCount++;
        
        if (issueCount > 1) {
            recoveryMenu.addItem(
                "do-all",
                "⚡ Perform All Recovery Actions",
                String.format("Recommended: Fix all %d issues at once", issueCount),
                () -> performAllRecoveryActions(analysis)
            );
            
            recoveryMenu.addSeparator("Individual Actions");
        }
        
        // Action 1: Complete partial updates
        if (!analysis.getFilesNeedingUpdate().isEmpty()) {
            recoveryMenu.addItem(
                "complete-update",
                "Complete Re-encryption",
                String.format("Update %d files to current key", 
                    analysis.getFilesNeedingUpdate().size()),
                () -> performCompleteUpdate(analysis)
            );
        }
        
        // Action 2: Finish interrupted swaps
        if (!analysis.getFilesNeedingSwap().isEmpty()) {
            recoveryMenu.addItem(
                "finish-swap",
                "Finish File Swaps",
                String.format("Complete %d interrupted swaps", 
                    analysis.getFilesNeedingSwap().size()),
                () -> performFinishSwaps(analysis)
            );
        }
        
        // Action 3: Cleanup tmp files
        if (!analysis.getFilesNeedingCleanup().isEmpty()) {
            recoveryMenu.addItem(
                "cleanup",
                "Clean Up Temporary Files",
                String.format("Delete %d tmp files", 
                    analysis.getFilesNeedingCleanup().size()),
                () -> performCleanup(analysis)
            );
        }
        
        recoveryMenu.addSeparator("Advanced Options");
        
        // Action 4: Rollback (if old key available)
        if (hasOldKey && !analysis.getAllFilesNeedingUpdate().isEmpty()) {
            recoveryMenu.addItem(
                "rollback",
                "⚠️ Rollback to Old Password",
                "Revert all changes to previous password",
                () -> confirmAndPerformRollback(analysis)
            );
        }
        
        // Action 5: Handle corrupted files
        if (!analysis.getCorruptedFiles().isEmpty()) {
            recoveryMenu.addItem(
                "corrupted",
                "Handle Corrupted Files",
                String.format("Deal with %d corrupted files", 
                    analysis.getCorruptedFiles().size()),
                () -> showCorruptedFilesMenu(analysis)
            );
        }
        
        // Action 6: Re-analyze
        recoveryMenu.addItem(
            "re-analyze",
            "Re-analyze File States",
            "Check current status after manual changes",
            () -> {
                analysis.resetCompletionTracking();
                investigateAndShowRecoveryMenu();
            }
        );
        
        // Action 7: View detailed state
        recoveryMenu.addItem(
            "details",
            "View Detailed File States",
            () -> showDetailedFileStates(analysis)
        );
        
        recoveryMenu.addSeparator("Exit Recovery");
        
        // Action 8: Clear flag if no issues remain
        if (!analysis.needsRecovery()) {
            recoveryMenu.addItem(
                "complete",
                "✓ Complete Recovery",
                "All issues resolved - exit recovery mode",
                () -> {
                    RecoveryFlags.clearRecoveryFlag();
                    systemAccess.clearOldKey();
                    state.removeState(SystemSessionStates.RECOVERY_REQUIRED);
                    
                    uiRenderer.render(UIProtocol.showMessage(
                        "✓ Recovery Complete!\n\n" +
                        "All file encryption issues have been resolved.\n" +
                        "System is now in a consistent state."));
                    
                    showMainMenu();
                }
            );
        } else {
            // Action 9: Clear flag and continue (dangerous)
            recoveryMenu.addItem(
                "ignore",
                "Clear Flag and Continue",
                "⚠️ Exit recovery mode with unresolved issues",
                () -> confirmClearFlagWithIssues(analysis)
            );
        }
        
        menuNavigator.showMenu(recoveryMenu);
    }

    private void performCleanup(FileEncryptionAnalysis analysis) {
        
        uiRenderer.render(UIProtocol.showMessage(
            String.format("Deleting %d temporary files...", analysis.getFilesNeedingCleanup().size())));
        
      
        systemAccess.performTempFileCleanup(analysis).thenAccept(success->{
            
            uiRenderer.render(UIProtocol.showMessage(
                String.format(success ? "✓ Deleted temporary files" : "x Unable to delete all temp files")));
            
            recheckAndClearFlagIfResolved();
            
        });
    }

    /**
     * Confirm rollback action with user
     */
    private void confirmAndPerformRollback(FileEncryptionAnalysis analysis) {
        ContextPath menuPath = contextPath.append("menu", "confirm-rollback");
        MenuContext confirmMenu = new MenuContext(
            menuPath,
            "⚠️ Confirm Rollback",
            uiRenderer,
            menuNavigator.getCurrentMenu()
        );
        
        confirmMenu.addInfoItem("warning",
            "⚠️ WARNING: ROLLBACK TO OLD PASSWORD ⚠️\n\n" +
            "This action will:\n" +
            "  1. Restore the OLD password as active\n" +
            "  2. Re-encrypt updated files back to old key\n" +
            "  3. Discard the NEW password\n\n" +
            String.format("Files to revert: %d\n\n", 
                analysis.getAllFilesNeedingUpdate().size()) +
            "This operation CANNOT be undone.\n" +
            "You will need to use the OLD password after rollback.\n\n" +
            "Are you sure you want to proceed?");
        
        confirmMenu.addSeparator("Confirmation");
        
        confirmMenu.addItem(
            "yes-rollback",
            "⚠️ YES - Perform Rollback",
            () -> performRollback(analysis)
        );
        
        confirmMenu.addItem(
            "no-cancel",
            "NO - Cancel",
            () -> showRecoveryMenu(analysis, true)
        );
        
        menuNavigator.showMenu(confirmMenu);
    }


    /**
     * Confirm clearing recovery flag with unresolved issues
     */
    private void confirmClearFlagWithIssues(FileEncryptionAnalysis analysis) {
        ContextPath menuPath = contextPath.append("menu", "confirm-clear");
        MenuContext confirmMenu = new MenuContext(
            menuPath,
            "⚠️ Confirm Exit Recovery",
            uiRenderer,
            menuNavigator.getCurrentMenu()
        );
        
        StringBuilder warning = new StringBuilder();
        warning.append("⚠️ WARNING: UNRESOLVED ISSUES ⚠️\n\n");
        warning.append("The following issues remain:\n");
        
        if (!analysis.getFilesNeedingUpdate().isEmpty()) {
            warning.append(String.format("  • %d files need re-encryption\n",
                analysis.getFilesNeedingUpdate().size()));
        }
        
        if (!analysis.getFilesNeedingSwap().isEmpty()) {
            warning.append(String.format("  • %d files need swap completion\n",
                analysis.getFilesNeedingSwap().size()));
        }
        
        if (!analysis.getFilesNeedingCleanup().isEmpty()) {
            warning.append(String.format("  • %d temporary files need cleanup\n",
                analysis.getFilesNeedingCleanup().size()));
        }
        
        if (!analysis.getCorruptedFiles().isEmpty()) {
            warning.append(String.format("  • %d corrupted files detected\n",
                analysis.getCorruptedFiles().size()));
        }
        
        warning.append("\nExiting recovery mode with unresolved issues means:\n");
        warning.append("  • Some files may be inaccessible\n");
        warning.append("  • File encryption is in an inconsistent state\n");
        warning.append("  • You will need to manually fix these issues\n\n");
        warning.append("Are you sure you want to exit recovery mode?");
        
        confirmMenu.addInfoItem("warning", warning.toString());
        
        confirmMenu.addSeparator("Confirmation");
        
        confirmMenu.addItem(
            "yes-exit",
            "⚠️ YES - Exit Recovery Mode",
            () -> {
                RecoveryFlags.clearRecoveryFlag();
                state.removeState(SystemSessionStates.RECOVERY_REQUIRED);
                
                uiRenderer.render(UIProtocol.showError(
                    "Recovery flag cleared.\n\n" +
                    "⚠️ Files remain in inconsistent state.\n" +
                    "Some files may be inaccessible."));
                
                showMainMenu();
            }
        );
        
        confirmMenu.addItem(
            "no-back",
            "NO - Return to Recovery",
            () -> showRecoveryMenu(analysis, systemAccess.hasOldKeyForRecovery())
        );
        
        menuNavigator.showMenu(confirmMenu);
    }


    private void performCompleteUpdate(FileEncryptionAnalysis analysis) {
        

        uiRenderer.render(UIProtocol.showMessage("Performing recover"));

        int batchSize = 1;
        
        // Create progress tracker
        ProgressTrackingProcess progressTracker = new ProgressTrackingProcess("recovery-progress",uiRenderer);
            
            spawnChild(progressTracker)
                .thenCompose(trackerPath -> registry.startProcess(trackerPath))
                .thenCompose(v -> requestStreamChannel(progressTracker.getContextPath()))
                .thenCompose(progressChannel -> {
                    AsyncNoteBytesWriter progressWriter = new AsyncNoteBytesWriter( progressChannel.getStream());
                    return systemAccess.performRecovery(analysis, progressWriter, batchSize)
                        .whenComplete((v, ex)->{
                            StreamUtils.safeClose(progressWriter);
                            StreamUtils.safeClose(progressChannel);
                        });
                })
                .thenCompose(success -> progressTracker.getCompletionFuture()
                    .thenApply(v -> success))
                .thenAccept(success -> {
                    if (success) {
                        uiRenderer.render(UIProtocol.showMessage(
                            "✓ Re-encryption completed successfully"));
                        
                        // Clear recovery flag if no more issues
                        recheckAndClearFlagIfResolved();
                    } else {
                        uiRenderer.render(UIProtocol.showError(
                            "Re-encryption completed with errors"));
                        
                        // Re-investigate
                        investigateAndShowRecoveryMenu();
                    }
                })
                .exceptionally(ex -> {
                    uiRenderer.render(UIProtocol.showError(
                        "Re-encryption failed: " + ex.getMessage()));
                    
                    investigateAndShowRecoveryMenu();
                    return null;
                });
    }

    private void performFinishSwaps(FileEncryptionAnalysis analysis) {

        uiRenderer.render(UIProtocol.showMessage("Finishing file swaps"));

        
        // Create progress tracker
        ProgressTrackingProcess progressTracker = new ProgressTrackingProcess("swap-progress", uiRenderer);
            
            spawnChild(progressTracker)
                .thenCompose(trackerPath -> registry.startProcess(trackerPath))
                .thenCompose(v -> requestStreamChannel(progressTracker.getContextPath()))
                .thenCompose(progressChannel -> {
                    AsyncNoteBytesWriter progressWriter = new AsyncNoteBytesWriter( progressChannel.getStream());
                    return systemAccess.performSwap(analysis, progressWriter)
                        .whenComplete((v, ex)->{
                            StreamUtils.safeClose(progressWriter);
                            StreamUtils.safeClose(progressChannel);
                        });
                })
                .thenCompose(success -> progressTracker.getCompletionFuture()
                    .thenApply(v -> success))
                .thenAccept(success -> {
                    if (success) {
                        uiRenderer.render(UIProtocol.showMessage(
                            "✓ File swaps completed successfully"));
                        
                        // Clear recovery flag if no more issues
                        recheckAndClearFlagIfResolved();
                    } else {
                        uiRenderer.render(UIProtocol.showError(
                            "Re-encryption completed with errors"));
                        
                        // Re-investigate
                        investigateAndShowRecoveryMenu();
                    }
                })
                .exceptionally(ex -> {
                    uiRenderer.render(UIProtocol.showError(
                        "File swaps failed: " + ex.getMessage()));
                    
                    investigateAndShowRecoveryMenu();
                    return null;
                });
    }


    /**
     * Show recovery menu when old key is not available
     * Limited options - requires user to provide old password
     */
    private void showRecoveryMenuNoOldKey() {
        ContextPath menuPath = contextPath.append("menu", "recovery-no-key");
        MenuContext recoveryMenu = new MenuContext(
            menuPath, 
            "⚠️ Recovery Mode - Old Key Required", 
            uiRenderer
        );
        
        recoveryMenu.addInfoItem("warning", 
            "Recovery flag is set, but the old encryption key is not available.\n\n" +
            "This happens when:\n" +
            "  • System restarted after incomplete password change\n" +
            "  • Old key was cleared from memory\n\n" +
            "To recover, you must provide the OLD password.");
        
        recoveryMenu.addSeparator("Recovery Actions");
        
        // Action 1: Provide old password
        recoveryMenu.addItem(
            "provide-old-password",
            "Provide Old Password",
            "Enter previous password to unlock recovery",
            () -> requestOldPasswordForRecovery()
        );
        
        // Action 2: Clear flag and continue (dangerous)
        recoveryMenu.addItem(
            "clear-flag",
            "Clear Flag and Continue",
            "⚠️ WARNING: Files may be inaccessible",
            () -> {
                RecoveryFlags.clearRecoveryFlag();
                state.removeState(SystemSessionStates.RECOVERY_REQUIRED);
                
                uiRenderer.render(UIProtocol.showError(
                    "Recovery flag cleared.\n\n" +
                    "WARNING: Encrypted files may be in an inconsistent state.\n" +
                    "Some files may be inaccessible."));
                
                showMainMenu();
            }
        );
        
        menuNavigator.showMenu(recoveryMenu);
    }

    /**
     * Request old password to derive old key for recovery
     */
    private void requestOldPasswordForRecovery() {
        getSecureInputDevice()
            .thenAccept(device -> {
                PasswordSessionProcess session = new PasswordSessionProcess(
                    "verify-old-password",
                    device,
                    uiRenderer,
                    "Enter OLD password (before change)",
                    3
                );
                
                session.onPasswordEntered(oldPassword -> {
                    return systemAccess.verifyOldPassword(oldPassword)
                        .thenApply(valid -> {
                            if (valid) {
                                uiRenderer.render(UIProtocol.showMessage(
                                    "Old password verified. Analyzing file state..."));
                                
                                // Now we can investigate
                                investigateAndShowRecoveryMenu();
                                return true;
                            } else {
                                uiRenderer.render(UIProtocol.showError(
                                    "Invalid old password. Please try again."));
                                return false;
                            }
                        });
                });
                
                spawnChild(session)
                    .thenCompose(path -> registry.startProcess(path));
            });
    }

    /**
     * Show recovery menu when analysis failed
     */
    private void showRecoveryMenuNoAnalysis() {
        ContextPath menuPath = contextPath.append("menu", "recovery-no-analysis");
        MenuContext recoveryMenu = new MenuContext(
            menuPath, 
            "⚠️ Recovery Mode - Analysis Failed", 
            uiRenderer
        );
        
        recoveryMenu.addInfoItem("error", 
            "Unable to analyze file encryption state.\n\n" +
            "This may be due to:\n" +
            "  • Corrupted file ledger\n" +
            "  • Disk read errors\n" +
            "  • Insufficient permissions");
        
        recoveryMenu.addSeparator("Recovery Actions");
        
        // Action 1: Retry analysis
        recoveryMenu.addItem(
            "retry-analysis",
            "Retry Analysis",
            () -> investigateAndShowRecoveryMenu()
        );
        
        // Action 2: Manual inspection
        recoveryMenu.addItem(
            "manual-inspection",
            "Manual File Inspection",
            "Advanced: Check files directly",
            () -> showManualInspectionMenu()
        );
        
        // Action 3: Clear flag
        recoveryMenu.addItem(
            "clear-flag",
            "Clear Flag and Continue",
            "⚠️ Accept current state",
            () -> {
                RecoveryFlags.clearRecoveryFlag();
                state.removeState(SystemSessionStates.RECOVERY_REQUIRED);
                
                uiRenderer.render(UIProtocol.showMessage(
                    "Recovery flag cleared.\n\n" +
                    "Files remain in unknown state."));
                
                showMainMenu();
            }
        );
        
        menuNavigator.showMenu(recoveryMenu);
    }

    /**
     * Show corrupted files submenu
     */
    private void showCorruptedFilesMenu(FileEncryptionAnalysis analysis) {
        ContextPath menuPath = contextPath.append("menu", "corrupted-files");
        MenuContext menu = new MenuContext(
            menuPath,
            "Corrupted Files",
            uiRenderer,
            menuNavigator.getCurrentMenu()
        );
        
        List<String> corruptedFiles = analysis.getCorruptedFiles();
        
        menu.addInfoItem("summary",
            String.format("Found %d corrupted files:\n\n", corruptedFiles.size()) +
            "These files cannot be decrypted with either the current or old key.\n\n" +
            "Possible causes:\n" +
            "  • Disk corruption\n" +
            "  • Incomplete write operations\n" +
            "  • External file modification");
        
        menu.addSeparator("Corrupted Files");
        
        // Show corrupted files (limited display)
        int displayLimit = 10;
        for (int i = 0; i < Math.min(displayLimit, corruptedFiles.size()); i++) {
            String path = corruptedFiles.get(i);
            menu.addInfoItem("file-" + i, "  ✗ " + shortenPath(path));
        }
        
        if (corruptedFiles.size() > displayLimit) {
            menu.addInfoItem("more", 
                String.format("  ... and %d more files", 
                    corruptedFiles.size() - displayLimit));
        }
        
        menu.addSeparator("Actions");
        
        // Action 1: Export list
        menu.addItem(
            "export-list",
            "Export Corrupted Files List",
            () -> exportCorruptedFilesList(corruptedFiles)
        );
        
        // Action 2: Attempt repair
        menu.addItem(
            "attempt-repair",
            "Attempt Automatic Repair",
            "⚠️ May delete unrecoverable files",
            () -> attemptCorruptedFilesRepair(corruptedFiles)
        );
        
        // Action 3: Delete corrupted
        menu.addItem(
            "delete-corrupted",
            "Delete Corrupted Files",
            "⚠️ Permanent deletion",
            () -> deleteCorruptedFiles(corruptedFiles)
        );
        
        menu.addItem("back", "Back to Recovery Menu", () -> {
            investigateAndShowRecoveryMenu();
        });
        
        menuNavigator.showMenu(menu);
    }

    /**
     * Export corrupted files list to a file
     */
    private void exportCorruptedFilesList(List<String> corruptedFiles) {
        CompletableFuture.runAsync(() -> {
            try {
                File exportFile = new File(SettingsData.getDataDir(), 
                    "corrupted_files_" + System.currentTimeMillis() + ".txt");
                
                StringBuilder content = new StringBuilder();
                content.append("Corrupted Files Report\n");
                content.append("Generated: ").append(new java.util.Date()).append("\n");
                content.append("Total: ").append(corruptedFiles.size()).append("\n\n");
                
                for (String path : corruptedFiles) {
                    content.append(path).append("\n");
                }
                
                Files.write(exportFile.toPath(), content.toString().getBytes());
                
                uiRenderer.render(UIProtocol.showMessage(
                    "Corrupted files list exported to:\n" + exportFile.getAbsolutePath()));
                
            } catch (IOException e) {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to export list: " + e.getMessage()));
            }
        }, VirtualExecutors.getVirtualExecutor());
    }

    /**
     * Attempt to repair corrupted files
     */
    private void attemptCorruptedFilesRepair(List<String> corruptedFiles) {
        uiRenderer.render(UIProtocol.showMessage(
            "Corrupted file repair not yet implemented.\n\n" +
            "Please use manual recovery tools or delete the files."));
    }

    /**
     * Delete corrupted files
     */
    private void deleteCorruptedFiles(List<String> corruptedFiles) {
        uiRenderer.render(UIProtocol.showMessage(
            String.format("Deleting %d corrupted files...", corruptedFiles.size())));
        
        systemAccess.deleteCorruptedFiles(corruptedFiles)
            .thenAccept(success -> {
                if (success) {
                    uiRenderer.render(UIProtocol.showMessage(
                        "✓ Corrupted files deleted successfully"));
                    
                    recheckAndClearFlagIfResolved();
                } else {
                    uiRenderer.render(UIProtocol.showError(
                        "Some corrupted files could not be deleted"));
                    
                    investigateAndShowRecoveryMenu();
                }
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to delete corrupted files: " + ex.getMessage()));
                return null;
            });
    }

    /**
     * Show manual inspection menu
     */
    private void showManualInspectionMenu() {
        ContextPath menuPath = contextPath.append("menu", "manual-inspection");
        MenuContext menu = new MenuContext(
            menuPath,
            "Manual File Inspection",
            uiRenderer,
            menuNavigator.getCurrentMenu()
        );
        
        menu.addInfoItem("info",
            "Advanced file inspection tools.\n\n" +
            "Use these tools to manually examine file encryption state.");
        
        menu.addItem(
            "list-files",
            "List All Registered Files",
            () -> listAllRegisteredFiles()
        );
        
        menu.addItem(
            "check-file",
            "Check Specific File",
            () -> checkSpecificFile()
        );
        
        menu.addItem("back", "Back", () -> {
            showRecoveryMenuNoAnalysis();
        });
        
        menuNavigator.showMenu(menu);
    }

    /**
     * List all files registered in the ledger
     */
    private void listAllRegisteredFiles() {
        uiRenderer.render(UIProtocol.showMessage("Listing registered files..."));
        
       systemAccess.getFileCount()
            .thenAccept(count -> {
                uiRenderer.render(UIProtocol.showMessage(
                    String.format("Total registered files: %d\n\n" +
                        "Use recovery analysis for detailed information.", count)));
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to list files: " + ex.getMessage()));
                return null;
            });
    }

    /**
     * Check a specific file's encryption state
     */
    private void checkSpecificFile() {
        uiRenderer.render(UIProtocol.showMessage(
            "Specific file checking not yet implemented.\n\n" +
            "Use the main recovery analysis instead."));
    }

    /**
     * Perform rollback to old password
     */
    private void performRollback(FileEncryptionAnalysis analysis) {
        uiRenderer.render(UIProtocol.showMessage(
            "⚠️ ROLLBACK TO OLD PASSWORD\n\n" +
            "This will:\n" +
            "  1. Swap current/old keys in SettingsData\n" +
            "  2. Re-encrypt files that were updated\n" +
            "  3. Restore system to OLD password\n\n" +
            "Processing..."));
        
        // Step 1: Rollback SettingsData (swap keys)
        systemAccess.rollbackSettingsData()
            .thenCompose(v -> {
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ SettingsData rolled back\n\n" +
                    "Re-encrypting files back to old password..."));
                
                // Step 2: Re-encrypt files back to old key
                // Now the keys are swapped: current=old, old=current
                List<String> filesToRevert = analysis.getFilesNeedingUpdate();
                
                if (filesToRevert.isEmpty()) {
                    return CompletableFuture.completedFuture(true);
                }
                
                int batchSize = calculateAdaptiveBatchSize(analysis, 1024 * 1024); // 1MB mem limit
                
                ProgressTrackingProcess progressTracker = new ProgressTrackingProcess("rollback-progress", uiRenderer);
                
                return spawnChild(progressTracker)
                    .thenCompose(trackerPath -> registry.startProcess(trackerPath))
                    .thenCompose(voidResult -> requestStreamChannel(progressTracker.getContextPath()))
                    .thenCompose(progressChannel -> {
                        AsyncNoteBytesWriter progressWriter = new AsyncNoteBytesWriter(
                            progressChannel.getStream());
                    
                        // Revert: newKey (now old) -> oldKey (now current)
                        return systemAccess.performRollback(analysis, progressWriter, batchSize)
                            .whenComplete((result, ex) -> {
                                StreamUtils.safeClose(progressWriter);
                                StreamUtils.safeClose(progressChannel);
                            });
                    })
                    .thenCompose(success -> progressTracker.getCompletionFuture()
                        .thenApply(v1 -> success));
            })
            .thenAccept(success -> {
                if (success) {
                    // Clear recovery state
                    RecoveryFlags.clearRecoveryFlag();
                    systemAccess.clearOldKey();
                    state.removeState(SystemSessionStates.RECOVERY_REQUIRED);
                    
                    uiRenderer.render(UIProtocol.showMessage(
                        "✓ ROLLBACK COMPLETE\n\n" +
                        "System restored to OLD password.\n" +
                        "All files have been reverted."));
                    
                    showMainMenu();
                } else {
                    uiRenderer.render(UIProtocol.showError(
                        "Rollback completed with errors.\n\n" +
                        "Some files may still be encrypted with the new password."));
                    
                    investigateAndShowRecoveryMenu();
                }
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Rollback failed: " + ex.getMessage() + "\n\n" +
                    "System may be in an inconsistent state."));
                
                investigateAndShowRecoveryMenu();
                return null;
            });
    }

    /**
     * Show detailed file states
     */
    private void showDetailedFileStates(FileEncryptionAnalysis analysis) {
        StringBuilder details = new StringBuilder();
        
        details.append("╔════════════════════════════════════════╗\n");
        details.append("  Detailed File Encryption States\n");
        details.append("╚════════════════════════════════════════╝\n\n");
        
        Map<String, NoteFileService.FileState> fileStates = analysis.getFileStates();
        
        // Group by state
        Map<NoteFileService.FileState, List<String>> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, NoteFileService.FileState> entry : fileStates.entrySet()) {
            grouped.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                .add(entry.getKey());
        }
        
        // Display each group
        for (Map.Entry<NoteFileService.FileState, List<String>> group : grouped.entrySet()) {
            NoteFileService.FileState state = group.getKey();
            List<String> files = group.getValue();
            
            details.append(getStateDescription(state))
                .append(" (").append(files.size()).append(" files)\n");
            details.append("────────────────────────────────────────\n");
            
            // Show first 5 files of each state
            int limit = Math.min(5, files.size());
            for (int i = 0; i < limit; i++) {
                details.append("  ").append(shortenPath(files.get(i))).append("\n");
            }
            
            if (files.size() > limit) {
                details.append("  ... and ").append(files.size() - limit)
                    .append(" more\n");
            }
            
            details.append("\n");
        }
        
        details.append("════════════════════════════════════════\n");
        details.append("Total files: ").append(fileStates.size()).append("\n");
        
        uiRenderer.render(UIProtocol.showMessage(details.toString()));
    }

    /**
     * Get human-readable description of file state
     */
    private String getStateDescription(NoteFileService.FileState state) {
        switch (state) {
            case CURRENT_KEY_OK:
                return "✓ OK - Current Key";
            case NEVER_CREATED:
                return "○ Never Created";
            case OLD_KEY_NEEDS_UPDATE:
                return "⚠ Needs Update - Old Key";
            case OLD_KEY_WITH_CURRENT_TMP:
                return "⚠ Needs Swap - Tmp Ready";
            case TMP_READY_CURRENT_KEY:
                return "⚠ Needs Finalization - Tmp Only";
            case CURRENT_KEY_WITH_TMP:
                return "⚠ Needs Cleanup - Extra Tmp";
            case OLD_KEY_WITH_TMP:
                return "⚠ Needs Investigation - Unclear State";
            case TMP_READY_OLD_KEY:
                return "⚠ Old State - Tmp Old Key";
            case CORRUPT:
                return "✗ CORRUPTED";
            case CORRUPT_WITH_TMP:
                return "✗ CORRUPTED (with tmp)";
            case TMP_CORRUPT:
                return "✗ CORRUPTED (tmp only)";
            default:
                return "? Unknown State";
        }
    }

    /**
     * Calculate adaptive batch size based on available resources
     * 
     * Considers:
     * - Disk space availability
     * - Memory constraints
     * - File sizes in the batch
     * 
     * @param analysis File encryption analysis with sizes
     * @param maxMemoryPerBatch Maximum memory to use per batch (bytes)
     * @return Optimal batch size
     */
    private int calculateAdaptiveBatchSize(
            FileEncryptionAnalysis analysis, 
            long maxMemoryPerBatch) {
        
        // Get files that need processing
        List<String> filesToProcess = new ArrayList<>();
        filesToProcess.addAll(analysis.getFilesNeedingUpdate());
        filesToProcess.addAll(analysis.getFilesNeedingSwap());
        
        if (filesToProcess.isEmpty()) {
            return 1;
        }
        
        // Get file sizes
        List<Long> fileSizes = filesToProcess.stream()
            .map(path -> {
                File file = new File(path);
                return file.exists() ? file.length() : 0L;
            })
            .filter(size -> size > 0)
            .sorted()
            .collect(Collectors.toList());
        
        if (fileSizes.isEmpty()) {
            return 1;
        }
        
        // Calculate average file size
        long totalSize = fileSizes.stream().mapToLong(Long::longValue).sum();
        long avgSize = totalSize / fileSizes.size();
        
        // Get available disk space
        File dataDir = SettingsData.getDataDir();
        long availableDiskSpace = dataDir.getUsableSpace();
        long bufferSpace = 100 * 1024 * 1024; // 100MB buffer
        long safeDiskSpace = availableDiskSpace - bufferSpace;
        
        // Calculate batch size based on disk space
        // Each file needs space for .tmp copy
        int diskBasedBatch = (int) (safeDiskSpace / avgSize);
        
        // Calculate batch size based on memory
        // Assume each concurrent operation uses memory proportional to file size
        int memoryBasedBatch = (int) (maxMemoryPerBatch / avgSize);
        
        // Take the minimum (most conservative)
        int optimalBatch = Math.min(diskBasedBatch, memoryBasedBatch);
        
        // Clamp to reasonable bounds
        int minBatch = 1;
        int maxBatch = 50;
        int finalBatch = Math.max(minBatch, Math.min(maxBatch, optimalBatch));
        
        System.out.println(String.format(
            "[SystemSession] Adaptive batch calculation:\n" +
            "  Files to process: %d\n" +
            "  Avg file size: %.2f MB\n" +
            "  Available disk: %.2f MB\n" +
            "  Memory limit: %.2f MB\n" +
            "  Disk-based batch: %d\n" +
            "  Memory-based batch: %d\n" +
            "  Final batch size: %d",
            filesToProcess.size(),
            avgSize / (1024.0 * 1024.0),
            availableDiskSpace / (1024.0 * 1024.0),
            maxMemoryPerBatch / (1024.0 * 1024.0),
            diskBasedBatch,
            memoryBasedBatch,
            finalBatch
        ));
        
        return finalBatch;
    }

    /**
     * Perform ALL recovery actions at once
     */
    private void performAllRecoveryActions(FileEncryptionAnalysis analysis) {
        uiRenderer.render(UIProtocol.showMessage(
            "Starting comprehensive recovery...\n\n" +
            "This will perform ALL recommended actions:\n" +
            "  1. Complete re-encryption\n" +
            "  2. Finish file swaps\n" +
            "  3. Clean up temporary files\n\n" +
            "Processing..."));
        
        int batchSize = calculateAdaptiveBatchSize(analysis, 2 * 1024 * 1024); // 2MB
        
        // Create progress tracker
        ProgressTrackingProcess progressTracker = new ProgressTrackingProcess("comprehensive-recovery", uiRenderer);
        
        spawnChild(progressTracker)
            .thenCompose(trackerPath -> registry.startProcess(trackerPath))
            .thenCompose(v -> requestStreamChannel(progressTracker.getContextPath()))
            .thenCompose(progressChannel -> {
                AsyncNoteBytesWriter progressWriter = new AsyncNoteBytesWriter(progressChannel.getStream());
                
                return systemAccess.performComprehensiveRecovery(
                    analysis, progressWriter, batchSize)
                    .whenComplete((result, ex) -> {
                        StreamUtils.safeClose(progressWriter);
                        StreamUtils.safeClose(progressChannel);
                    });
            })
            .thenCompose(success -> progressTracker.getCompletionFuture()
                .thenApply(v -> success))
            .thenAccept(success -> {
                if (success) {
                    uiRenderer.render(UIProtocol.showMessage(
                        "✓ Comprehensive recovery completed!\n\n" +
                        "Verifying final state..."));
                    
                    recheckAndClearFlagIfResolved();
                } else {
                    uiRenderer.render(UIProtocol.showError(
                        "Recovery completed with errors.\n\n" +
                        "Review individual recovery options."));
                    
                    investigateAndShowRecoveryMenu();
                }
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Comprehensive recovery failed: " + ex.getMessage()));
                
                investigateAndShowRecoveryMenu();
                return null;
            });
    }

        private void recheckAndClearFlagIfResolved() {
        
        // Re-investigate to see if issues remain
        systemAccess.investigateFileEncryption()
            .thenAccept(analysis -> {
                if (!analysis.needsRecovery() && analysis.getCorruptedFiles().isEmpty()) {
                    // All resolved!
                    RecoveryFlags.clearRecoveryFlag();
                    systemAccess.clearOldKey();
                    
                    state.removeState(SystemSessionStates.RECOVERY_REQUIRED);
                    
                    uiRenderer.render(UIProtocol.showMessage(
                        "✓ All issues resolved!\n\nSystem is now in consistent state."));
                    
                    showMainMenu();
                } else {
                    // Still have issues
                    uiRenderer.render(UIProtocol.showMessage(
                        "Some issues remain. Review recovery options."));
                    
                    showRecoveryMenu(analysis, systemAccess.hasOldKeyForRecovery());
                }
            })
            .exceptionally(ex -> {
                System.err.println("Recheck failed: " + ex.getMessage());
                showRecoveryMenu(null, systemAccess.hasOldKeyForRecovery());
                return null;
            });
    }


    // ═══════════════════════════════════════════════════════════════════════════
    // NODE MANAGEMENT MENUS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Show node manager main menu
     */
    private void showNodeManagerMenu() {
        // Load statistics
        systemAccess.getInstalledPackages()
            .thenCombine(systemAccess.getRunningInstances(), 
                (installed, running) -> {
                    
                    MenuContext menu = new MenuContext(
                        contextPath.append("menu", "nodes"),
                        "Node Manager",
                        uiRenderer,
                        menuNavigator.getCurrentMenu()
                    );
                    
                    menu.addInfoItem("stats", String.format(
                        "Installed Packages: %d | Running Instances: %d",
                        installed.size(),
                        running.size()
                    ));
                    
                    menu.addSeparator("Package Management");
                    
                    menu.addItem("browse",
                        "📦 Browse & Install Packages",
                        "View and install from repositories",
                        this::showBrowsePackagesMenu);
                    
                    menu.addItem("installed",
                        "📋 Manage Installed Packages",
                        "View, configure, or remove packages",
                        this::showInstalledPackagesMenu);
                    
                    menu.addSeparator("Instance Management");
                    
                    menu.addItem("running",
                        "📊 Running Instances",
                        "View and manage active nodes",
                        this::showRunningInstancesMenu);
                    
                    menu.addSeparator("");
                    
                    menu.addItem("back",
                        "← Back to Main Menu",
                        this::showMainMenu);
                    
                    menuNavigator.showMenu(menu);
                    return null;
                })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to load node statistics: " + ex.getMessage()));
                showMainMenu();
                return null;
            });
    }

    /**
     * Start package installation with password confirmation
     * 
     */
    private void startPackageInstallation(PackageInfo pkg) {
        uiRenderer.render(UIProtocol.showMessage(
            "Starting installation: " + pkg.getName()));
        
        // Create installation flow coordinator with password supplier
        InstallationFlowCoordinator flowCoordinator = new InstallationFlowCoordinator(
            uiRenderer,
            contextPath.append("install"),
            systemAccess,  // Pass RuntimeAccess instead of registry
            this::requestPasswordForInstallation  // Password callback
        );
        
        // Run installation flow
        flowCoordinator.startInstallation(pkg)
            .thenCompose(request -> {
                if (request == null) {
                    // User cancelled
                    uiRenderer.render(UIProtocol.showMessage("Installation cancelled"));
                    showNodeManagerMenu();
                    return CompletableFuture.completedFuture(null);
                }
                
                // Execute installation via systemAccess
                return systemAccess.installPackage(request)
                    .thenApply(installedPkg -> {
                        uiRenderer.render(UIProtocol.showMessage(
                            "✓ Package installed: " + pkg.getName()));
                        
                        // Load immediately if requested
                        if (request.shouldLoadImmediately()) {
                            NodeLoadRequest loadRequest = new NodeLoadRequest( installedPkg );
                            
                            return systemAccess.loadNode(loadRequest)
                                .thenApply(instance -> {
                                    uiRenderer.render(UIProtocol.showMessage(
                                        "✓ Node loaded: " + instance.getInstanceId()));
                                    return installedPkg;
                                });
                        }
                        
                        return CompletableFuture.completedFuture(installedPkg);
                    })
                    .thenCompose(f -> f);
            })
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    uiRenderer.render(UIProtocol.showError(
                        "Installation failed: " + ex.getMessage()));
                }
                showNodeManagerMenu();
            });
    }

    /**
     * Browse available packages
     */
    private void showBrowsePackagesMenu() {
        uiRenderer.render(UIProtocol.showMessage("Loading available packages..."));
        
        systemAccess.browseAvailablePackages()
            .thenAccept(packages -> {
                if (packages.isEmpty()) {
                    uiRenderer.render(UIProtocol.showMessage(
                        "No packages available.\n\n" +
                        "Check repository configuration."));
                    showNodeManagerMenu();
                    return;
                }
                
                MenuContext menu = new MenuContext(
                    contextPath.append("menu", "browse-packages"),
                    "Available Packages",
                    uiRenderer,
                    menuNavigator.getCurrentMenu()
                );
                
                menu.addInfoItem("info", packages.size() + " package(s) available");
                menu.addSeparator("Packages");
                
                // Group by category
                Map<String, List<PackageInfo>> byCategory = packages.stream()
                    .collect(java.util.stream.Collectors.groupingBy(PackageInfo::getCategory));
                
                for (Map.Entry<String, List<PackageInfo>> entry : byCategory.entrySet()) {
                    String category = entry.getKey();
                    List<PackageInfo> pkgs = entry.getValue();
                    
                    menu.addSubMenu(category, category, subMenu -> {
                        for (PackageInfo pkg : pkgs) {
                            String displayName = String.format("%s (%s)",
                                pkg.getName(), pkg.getVersion());
                            
                            subMenu.addItem(
                                pkg.getPackageId().toString(),
                                displayName,
                                pkg.getDescription(),
                                () -> startPackageInstallation(pkg)
                            );
                        }
                        return subMenu;
                    });
                }
                
                menu.addItem("back", "← Back", this::showNodeManagerMenu);
                
                menuNavigator.showMenu(menu);
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to load packages: " + ex.getMessage()));
                showNodeManagerMenu();
                return null;
            });
    }
            

    /**
     * Request password for installation
     * 
     * This is the callback given to InstallationFlowCoordinator.
     * Creates a PasswordSessionProcess to get the password.
     * 
     * IMPORTANT: Returns a COPY of the password for the installation to use.
     * The original password is auto-closed by PasswordSessionProcess.
     */
    private CompletableFuture<NoteBytesEphemeral> requestPasswordForInstallation() {
        CompletableFuture<NoteBytesEphemeral> passwordFuture = new CompletableFuture<>();
        
        getSecureInputDevice()
            .thenAccept(device -> {
                PasswordSessionProcess pwdSession = new PasswordSessionProcess(
                    "installation-password-confirm",
                    device,
                    uiRenderer,
                    "Enter system password to confirm installation",
                    3
                );
                
                // Verify password
                pwdSession.onPasswordEntered(password -> {
                    return systemAccess.verifyPassword(password)
                        .thenApply(valid -> {
                            if (valid) {
                                // Password valid! Copy for installation use
                                // (original will be closed after this handler)
                                NoteBytesEphemeral passwordCopy = password.copy();
                                passwordFuture.complete(passwordCopy);
                            } else {
                                uiRenderer.render(UIProtocol.showError("Invalid password"));
                            }
                            return valid;
                        });
                });
                
                pwdSession.onMaxAttemptsReached(() -> {
                    uiRenderer.render(UIProtocol.showError(
                        "Maximum password attempts reached"));
                    passwordFuture.complete(null);
                });
                
                pwdSession.onCancelled(() -> {
                    uiRenderer.render(UIProtocol.showMessage(
                        "Installation cancelled"));
                    passwordFuture.complete(null);
                });
                
                // Spawn password session
                spawnChild(pwdSession)
                    .thenCompose(path -> registry.startProcess(path));
            })
            .exceptionally(ex -> {
                passwordFuture.completeExceptionally(ex);
                return null;
            });
        
        return passwordFuture;
    }

    /**
     * Show installed packages
     */
    private void showInstalledPackagesMenu() {
        systemAccess.getInstalledPackages()
            .thenAccept(installed -> {
                if (installed.isEmpty()) {
                    uiRenderer.render(UIProtocol.showMessage(
                        "No packages installed"));
                    showNodeManagerMenu();
                    return;
                }
                
                MenuContext menu = new MenuContext(
                    contextPath.append("menu", "installed-packages"),
                    "Installed Packages",
                    uiRenderer,
                    menuNavigator.getCurrentMenu()
                );
                
                menu.addInfoItem("info", installed.size() + " package(s) installed");
                menu.addSeparator("Packages");
                
                for (InstalledPackage pkg : installed) {
                    String displayName = String.format("%s %s",
                        pkg.getName(), pkg.getVersion());
                    
                    menu.addItem(
                        pkg.getPackageId().toString(),
                        displayName,
                        pkg.getDescription(),
                        () -> showPackageDetailsMenu(pkg)
                    );
                }
                
                menu.addItem("back", "← Back", this::showNodeManagerMenu);
                
                menuNavigator.showMenu(menu);
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to load installed packages: " + ex.getMessage()));
                showNodeManagerMenu();
                return null;
            });
    }

    /**
     * Show package details with actions
     */
    private void showPackageDetailsMenu(InstalledPackage pkg) {
        MenuContext menu = new MenuContext(
            contextPath.append("menu", "package-details"),
            pkg.getName(),
            uiRenderer,
            menuNavigator.getCurrentMenu()
        );
        
        String details = String.format(
            "Name: %s\n" +
            "Version: %s\n" +
            "Process ID: %s\n" +
            "Repository: %s\n" +
            "Installed: %s\n\n" +
            "%s",
            pkg.getName(),
            pkg.getVersion(),
            pkg.getProcessId(),
            pkg.getRepository(),
            TimeHelpers.formatDate(pkg.getInstalledDate()),
            pkg.getDescription()
        );
        
        menu.addInfoItem("details", details);
        menu.addSeparator("Actions");
        
        menu.addItem("load",
            "▶️ Load Instance",
            "Start a node instance",
            () -> loadNodeInstance(pkg));
        
        menu.addItem("uninstall",
            "🗑️ Uninstall",
            "Remove this package",
            () -> confirmPackageUninstall(pkg));
        
        menu.addItem("back", "← Back", this::showInstalledPackagesMenu);
        
        menuNavigator.showMenu(menu);
    }

    /**
     * Load a node instance from installed package
     */
    private void loadNodeInstance(InstalledPackage pkg) {
        uiRenderer.render(UIProtocol.showMessage(
            "Loading node: " + pkg.getName()));
        
        NodeLoadRequest loadRequest = new NodeLoadRequest(pkg);
        
        systemAccess.loadNode(loadRequest)
            .thenAccept(instance -> {
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Node loaded successfully\n\n" +
                    "Instance ID: " + instance.getInstanceId()));
                showPackageDetailsMenu(pkg);
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to load node: " + ex.getMessage()));
                showPackageDetailsMenu(pkg);
                return null;
            });
    }

    /**
     * Confirm package uninstall
     */
    private void confirmPackageUninstall(InstalledPackage pkg) {
        MenuContext menu = new MenuContext(
            contextPath.append("menu", "confirm-uninstall"),
            "Confirm Uninstall",
            uiRenderer,
            menuNavigator.getCurrentMenu()
        );
        
        menu.addInfoItem("warning",
            "⚠️ Uninstall " + pkg.getName() + "?\n\n" +
            "This will:\n" +
            "  • Stop any running instances\n" +
            "  • Delete package files\n" +
            "  • Remove from installed list\n\n" +
            "This action cannot be undone.");
        
        menu.addSeparator("Confirmation");
        
        menu.addItem("confirm",
            "✓ Yes, Uninstall",
            () -> uninstallPackage(pkg));
        
        menu.addItem("cancel",
            "✗ Cancel",
            () -> showPackageDetailsMenu(pkg));
        
        menuNavigator.showMenu(menu);
    }

    /**
     * Uninstall a package
     */
    private void uninstallPackage(InstalledPackage pkg) {
        uiRenderer.render(UIProtocol.showMessage(
            "Uninstalling " + pkg.getName() + "..."));
        
        systemAccess.uninstallPackage(pkg.getPackageId())
            .thenRun(() -> {
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Package uninstalled: " + pkg.getName()));
                showInstalledPackagesMenu();
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Uninstall failed: " + ex.getMessage()));
                showPackageDetailsMenu(pkg);
                return null;
            });
    }

    /**
     * Show running instances
     */
    private void showRunningInstancesMenu() {
        systemAccess.getRunningInstances()
            .thenAccept(instances -> {
                if (instances.isEmpty()) {
                    uiRenderer.render(UIProtocol.showMessage(
                        "No nodes currently running"));
                    showNodeManagerMenu();
                    return;
                }
                
                MenuContext menu = new MenuContext(
                    contextPath.append("menu", "running-instances"),
                    "Running Node Instances",
                    uiRenderer,
                    menuNavigator.getCurrentMenu()
                );
                
                menu.addInfoItem("info", instances.size() + " instance(s) running");
                menu.addSeparator("Instances");
                
                for (NodeInstance instance : instances) {
                    String displayName = String.format("%s [%s]",
                        instance.getPackage().getName(),
                        instance.getInstanceId());
                    
                    String status = String.format(
                        "State: %s | Uptime: %ds",
                        instance.getState(),
                        instance.getUptime() / 1000
                    );
                    
                    menu.addItem(
                        instance.getInstanceId().toString(),
                        displayName,
                        status,
                        () -> showInstanceDetailsMenu(instance)
                    );
                }
                
                menu.addItem("back", "← Back", this::showNodeManagerMenu);
                
                menuNavigator.showMenu(menu);
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to load instances: " + ex.getMessage()));
                showNodeManagerMenu();
                return null;
            });
    }

    /**
     * Show instance details
     */
    private void showInstanceDetailsMenu(NodeInstance instance) {
        MenuContext menu = new MenuContext(
            contextPath.append("menu", "instance-details"),
            "Instance: " + instance.getInstanceId(),
            uiRenderer,
            menuNavigator.getCurrentMenu()
        );
        
        String details = String.format(
            "Package: %s %s\n" +
            "Instance ID: %s\n" +
            "Process ID: %s\n" +
            "State: %s\n" +
            "Uptime: %ds\n" +
            "Healthy: %s",
            instance.getPackage().getName(),
            instance.getPackage().getVersion(),
            instance.getInstanceId(),
            instance.getPackage().getProcessId(),
            instance.getState(),
            instance.getUptime() / 1000,
            instance.isHealthy() ? "Yes" : "No"
        );
        
        menu.addInfoItem("details", details);
        menu.addSeparator("Actions");
        
        menu.addItem("unload",
            "⏹️ Unload Instance",
            "Stop this node instance",
            () -> confirmInstanceUnload(instance));
        
        menu.addItem("back", "← Back", this::showRunningInstancesMenu);
        
        menuNavigator.showMenu(menu);
    }

    /**
     * Confirm instance unload
     */
    private void confirmInstanceUnload(NodeInstance instance) {
        MenuContext menu = new MenuContext(
            contextPath.append("menu", "confirm-unload"),
            "Confirm Unload",
            uiRenderer,
            menuNavigator.getCurrentMenu()
        );
        
        menu.addInfoItem("warning",
            "⚠️ Unload instance?\n\n" +
            "Instance: " + instance.getInstanceId() + "\n" +
            "Package: " + instance.getPackage().getName() + "\n\n" +
            "This will stop the node process.");
        
        menu.addSeparator("Confirmation");
        
        menu.addItem("confirm",
            "✓ Yes, Unload",
            () -> unloadInstance(instance));
        
        menu.addItem("cancel",
            "✗ Cancel",
            () -> showInstanceDetailsMenu(instance));
        
        menuNavigator.showMenu(menu);
    }

    /**
     * Unload an instance
     */
    private void unloadInstance(NodeInstance instance) {
        uiRenderer.render(UIProtocol.showMessage(
            "Unloading instance..."));
        
        systemAccess.unloadNode(instance.getInstanceId())
            .thenRun(() -> {
                uiRenderer.render(UIProtocol.showMessage(
                    "✓ Instance unloaded"));
                showRunningInstancesMenu();
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Unload failed: " + ex.getMessage()));
                showInstanceDetailsMenu(instance);
                return null;
            });
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
    public SystemRuntime getAppData() {
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
     * Helper class to pass password change result through completion chain
     */
    private static class PasswordChangeResult {
        final ProgressTrackingProcess progressTracker;
        final Throwable exception;
        
        PasswordChangeResult(ProgressTrackingProcess tracker, Throwable exception) {
            this.progressTracker = tracker;
            this.exception = exception;
        }
    }
}