package io.netnotes.engine.core.system;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.*;
import io.netnotes.engine.core.system.control.ui.*;


import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.daemon.ClaimedDevice;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.RoutedMessageExecutor;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.VirtualExecutors;

/**
 * SystemSessionProcess - Manages user session lifecycle
 * 
 * Initialization Flow:
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
 *     - SettingsData.createSettings(password)
 *     -> READY
 * 
 * 3b. SETTINGS_EXIST (settings file found)
 *     - SettingsData.loadSettingsMap()
 *     - Start password verification session
 *     - User enters password
 *     - SettingsData.verifyPassword(password, map)
 *     - If valid: SettingsData.loadSettingsData(password, map)
 *     READY
 * 
 * 4. READY (SettingsData created)
 *    - Check if secure input is required
 *    - If yes: LOCKED
 *    - If no: UNLOCKED
 * 
 * 5. LOCKED -> UNLOCKED
 *    - Start password verification
 *    - settingsData.verifyPassword(password)
 *    - If valid: UNLOCKED
 */
public class SystemSessionProcess extends FlowProcess {
    
    private final BitFlagStateMachine state;
    private final UIRenderer uiRenderer;
    
    // Configuration
    private NoteBytesMap bootstrapConfig;
    private NoteBytesMap settingsMap; // Loaded settings (contains BCrypt hash)
    
    // System data (created after password verification)
    private SettingsData settingsData;
    private AppData appData;
    
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
        m_routedMsgMap.put(new NoteBytesReadOnly("lock_system"), this::handleLockSystem);
        m_routedMsgMap.put(new NoteBytesReadOnly("unlock_system"), this::handleUnlockSystem);
    }
    
    private void setupStateTransitions() {
        // INITIALIZING -> BOOTSTRAP_LOADED
        state.onStateAdded(SystemSessionStates.BOOTSTRAP_LOADED, (old, now, bit) -> {
            System.out.println("Bootstrap config loaded");
            
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
            System.out.println("First run - creating new settings");
            uiRenderer.render(UIProtocol.showMessage("Welcome! Please create a master password."));
            
            // Start password creation session
            startPasswordCreationSession();
        });
        
        // SETTINGS_EXIST (settings file found)
        state.onStateAdded(SystemSessionStates.SETTINGS_EXIST, (old, now, bit) -> {
            System.out.println("Settings found - loading");
            
            // Load settings map
            SettingsData.loadSettingsMap()
                .thenAccept(map -> {
                    this.settingsMap = map;
                    state.addState(SystemSessionStates.SETTINGS_LOADED);
                })
                .exceptionally(ex -> {
                    System.err.println("Failed to load settings: " + ex.getMessage());
                    uiRenderer.render(UIProtocol.showError("Failed to load settings: " + ex.getMessage()));
                    return null;
                });
        });
        
        // SETTINGS_LOADED -> verify password
        state.onStateAdded(SystemSessionStates.SETTINGS_LOADED, (old, now, bit) -> {
            System.out.println("Settings loaded - requesting password");
            
            // Start password verification session
            startPasswordVerificationSession();
        });
        
        // READY (SettingsData created)
        state.onStateAdded(SystemSessionStates.READY, (old, now, bit) -> {
            System.out.println("System ready - SettingsData initialized");
            
            // Check if we should start locked
            if (BootstrapConfig.isSecureInputInstalled(bootstrapConfig)) {
                state.addState(SystemSessionStates.LOCKED);
            } else {
                state.addState(SystemSessionStates.UNLOCKED);
            }
        });
        
        // LOCKED
        state.onStateAdded(SystemSessionStates.LOCKED, (old, now, bit) -> {
            System.out.println("System locked");
            showLockedMenu();
        });
        
        // UNLOCKING
        state.onStateAdded(SystemSessionStates.UNLOCKING, (old, now, bit) -> {
            System.out.println("Unlocking system...");
            startUnlockPasswordSession();
        });
        
        // UNLOCKED
        state.onStateAdded(SystemSessionStates.UNLOCKED, (old, now, bit) -> {
            System.out.println("System unlocked");
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
                System.err.println("Unknown command: " + cmd);
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
                System.err.println("Failed to load bootstrap config: " + ex.getMessage());
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
     * User must enter password twice (matching)
     */
    private void startPasswordCreationSession() {
        // Get secure input device
        getSecureInputDevice()
            .thenAccept(device -> {
                // Create password creation session
                PasswordCreationSession session = new PasswordCreationSession(
                    device,
                    uiRenderer,
                    "Create master password",
                    "Confirm master password"
                );
                
                // Handle successful password creation
                session.onPasswordCreated(password -> {
                    return SettingsData.createSettings(password)
                        .thenAccept(settings -> {
                            this.settingsData = settings;
                            
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
     * Existing settings: Verify password
     * Load SettingsData if password is correct
     */
    private void startPasswordVerificationSession() {
        getSecureInputDevice()
            .thenAccept(device -> {
                PasswordSessionProcess session = new PasswordSessionProcess(
                    device,
                    uiRenderer,
                    "Enter master password",
                    3 // max attempts
                );
                
                // Set password verification handler
                session.onPasswordEntered(password -> {
                    // First verify using the map
                    return SettingsData.verifyPassword(password, settingsMap)
                        .thenCompose(valid -> {
                            if (valid) {
                                // Load full SettingsData
                                return SettingsData.loadSettingsData(password, settingsMap)
                                    .thenApply(settings -> {
                                        this.settingsData = settings;
                                        
                                        state.removeState(SystemSessionStates.SETTINGS_LOADED);
                                        state.addState(SystemSessionStates.READY);
                                        
                                        return true;
                                    });
                            } else {
                                uiRenderer.render(UIProtocol.showError("Invalid password"));
                                return CompletableFuture.completedFuture(false);
                            }
                        });
                });
                /*
                // Handle too many failures
                session.onMaxAttemptsReached(() -> {
                    uiRenderer.render(UIProtocol.showError(
                        "Too many failed attempts - system locked"));
                });*/
                
                this.passwordSession = session;
                
                // Spawn session
                spawnChild(session, "password-verification")
                    .thenCompose(path -> registry.startProcess(path));
            });
    }
    
    /**
     * Unlock from LOCKED state
     * SettingsData already exists, just verify password
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
                    // Verify using existing settingsData
                    return settingsData.verifyPassword(password)
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
    private CompletableFuture<ClaimedDevice> getSecureInputDevice() {
        if (parentPath == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("No parent process"));
        }
        
        // Request device from BaseSystemProcess
        return request(parentPath, Duration.ofSeconds(5),
            new NoteBytesPair("cmd", "get_secure_input_device")
        ).thenApply(response -> {
            NoteBytesMap resp = response.getPayload().getAsNoteBytesMap();
            String devicePath = resp.get("device_path").getAsString();
            
            if (devicePath == null) {
                throw new IllegalStateException("No secure input device available");
            }
            
            ClaimedDevice device = (ClaimedDevice) 
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
        
        menu.addItem("about", "About System", () -> {
            uiRenderer.render(UIProtocol.showMessage("Netnotes v1.0"));
        });
        
        menu.addItem("lock", "Lock System", () -> {
            state.removeState(SystemSessionStates.UNLOCKED);
            state.addState(SystemSessionStates.LOCKED);
        });
        
        // TODO: Add more menu items (settings, plugins, etc.)
        
        return menu;
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
    
    public SettingsData getSettingsData() {
        return settingsData;
    }
    
    public AppData getAppData() {
        return appData;
    }
    
    // ===== SESSION TYPE =====
    
    public enum SessionType {
        PHYSICAL,  // Local UI
        NETWORK    // Remote connection
    }
}