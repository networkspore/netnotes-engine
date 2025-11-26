package io.netnotes.engine.core.system;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.*;
import io.netnotes.engine.core.system.control.ui.*;

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

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.DiskSpaceValidation;
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
     * Verify password and load system (ephemeral settingsMap)
     * settingsMap is function-scoped only, discarded after use
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
                                    
                                    // Create AppData
                                    AppData newAppData = new AppData(settingsData);
                                    
                                    // settingsMap goes out of scope here (garbage collected)
                                    System.out.println("[SystemSession] AppData created, settingsMap discarded");
                                    
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
     * AppData already exists, just verify password
     */
    private void startUnlockPasswordSession() {
        getSecureInputDevice()
            .thenAccept(device -> {
                PasswordSessionProcess session = new PasswordSessionProcess(
                    device,
                    uiRenderer,
                    "Enter password to unlock",
                    3
                );
                
                // Set password verification handler
                session.onPasswordEntered(password -> {
                    // Verify using AppData's SettingsData
                    return appData.getSettingsData().verifyPassword(password)
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
    
    private MenuContext buildLockedMenu() {
        ContextPath menuPath = contextPath.append("menu", "locked");
        MenuContext menu = new MenuContext(menuPath, "System Locked", uiRenderer);
        
        menu.addItem("unlock", "Unlock System", () -> {
            state.removeState(SystemSessionStates.LOCKED);
            state.addState(SystemSessionStates.UNLOCKING);
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
                    return appData.getSettingsData().verifyPassword(currentPassword)
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
     * Check for incomplete password change and offer recovery
     */
    private CompletableFuture<Void> checkForIncompletePasswordChange() {
        return CompletableFuture.runAsync(() -> {
            try {
                File dataDir = SettingsData.getDataDir();
                File logFile = new File(dataDir, "password_change_recovery.log");
                
                if (logFile.exists() && logFile.length() > 0) {
                    // Found incomplete password change
                    System.err.println("[SystemSession] Incomplete password change detected");
                    
                    // Read log to get status
                    List<String> logLines = Files.readAllLines(logFile.toPath());
                    
                    int completed = 0;
                    int failed = 0;
                    List<String> failedFiles = new ArrayList<>();
                    
                    for (String line : logLines) {
                        if (line.startsWith("#")) continue; // Skip comments
                        
                        String[] parts = line.split("\\|");
                        if (parts.length >= 3) {
                            String status = parts[0];
                            String filePath = parts[2];
                            
                            if (status.equals("success")) {
                                completed++;
                            } else if (status.equals("error") || status.equals("started")) {
                                failed++;
                                failedFiles.add(filePath);
                            }
                        }
                    }
                    
                    // Show recovery options to user
                    String message = String.format(
                        "An incomplete password change was detected.\n\n" +
                        "Files successfully updated: %d\n" +
                        "Files not completed: %d\n\n" +
                        "Your data may be in an inconsistent state.\n" +
                        "Please contact support for recovery assistance.\n\n" +
                        "Recovery log: %s",
                        completed,
                        failed,
                        logFile.getAbsolutePath()
                    );
                    
                    uiRenderer.render(UIProtocol.showError(message));
                    
                    // Archive the log file
                    File archivedLog = new File(dataDir, 
                        "password_change_recovery_" + System.currentTimeMillis() + ".log");
                    Files.move(logFile.toPath(), archivedLog.toPath());
                    
                    System.out.println("[SystemSession] Recovery log archived to: " + 
                        archivedLog.getAbsolutePath());
                }
                
            } catch (IOException e) {
                System.err.println("[SystemSession] Error checking for incomplete password change: " + 
                    e.getMessage());
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