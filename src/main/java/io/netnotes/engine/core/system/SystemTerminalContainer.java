package io.netnotes.engine.core.system;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.containers.RenderingService;
import io.netnotes.engine.core.system.control.containers.TerminalContainerHandle;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.daemon.ClaimedDevice;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.io.input.events.containers.ContainerResizeEvent;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

/**
 * SystemTerminalContainer
 * 
 * Let the data speak:
 * - systemAccess != null → authenticated, runtime exists
 * - passwordKeyboardId != null → we use a password keyboard
 * - passwordKeyboardId == getDefaultKeyboardId() → same keyboard for both
 * - passwordKeyboardId != getDefaultKeyboardId() → separate keyboards
 * - getClaimedDevice(passwordKeyboardId) != null → password keyboard is claimed
 * 
 * State machine used only for UI flow states, not data states
 */
public class SystemTerminalContainer extends TerminalContainerHandle {
    
    public static final String DEFAULT_IO_DAEMON_SOCKET_PATH = "/var/run/io-daemon.sock";
    
    @FunctionalInterface
    public interface ScreenFactory {
        TerminalScreen create(String id, SystemTerminalContainer terminal);
    }
    
    private final BitFlagStateMachine state;
    private final ProcessRegistryInterface registry;
    private final ContextPath sessionPath;

    // Bootstrap configuration - loaded once from disk
    private String passwordKeyboardId = null;  // null = use GUI keyboard
    private String ioDaemonSocketPath = DEFAULT_IO_DAEMON_SOCKET_PATH;
    private boolean isInRecoveryMode = false;
    private String recoveryReason = null;
 
    private final IODaemonManager ioDaemonManager;
    private final RenderingService renderingService;
    
    // Authentication state - THE source of truth
    private SystemRuntime systemRuntime = null;
    private RuntimeAccess systemAccess = null;  // null = not authenticated
    
    // Authentication timeout
    private CompletableFuture<Void> authTimeoutFuture = null;
    private static final long AUTH_TIMEOUT_SECONDS = 30;
    
    private volatile boolean isVisible = false;
    
    // Current screen
    private TerminalScreen currentScreen;
    private final Map<String, ScreenFactory> screenFactories = new HashMap<>();
    
    // States - UI flow only, not data states
    public static final long INITIALIZING = 1L << 0;
    public static final long SETUP_NEEDED = 1L << 1;
    public static final long SETUP_COMPLETE = 1L << 2;
    public static final long LOCKED = 1L << 3;
    public static final long OPENING = 1L << 4;
    public static final long CHECKING_SETTINGS = 1L << 5;
    public static final long FIRST_RUN = 1L << 6;
    public static final long AUTHENTICATING = 1L << 7;  // UI state: showing auth screen
    public static final long SHOWING_SCREEN = 1L << 8;
    public static final long ERROR = 1L << 9;
    public static final long FAILED_SETTINGS = 1L << 10;
    
    
    public SystemTerminalContainer(RenderingService renderingService, ProcessRegistryInterface registry, ContextPath sessionPath) {
        super("system-terminal");
        this.renderingService = renderingService;
        this.registry = registry;
        this.sessionPath = sessionPath;
        this.state = new BitFlagStateMachine(getName());
        this.ioDaemonManager = new IODaemonManager(this, registry);
        setupStateTransitions();
        registerDefaultScreens();
    }

    private static class ConfigKeys {
        public static final NoteBytesReadOnly PASSWORD_KEYBOARD_ID = new NoteBytesReadOnly("passwordKeyboardId");
        public static final NoteBytesReadOnly IO_DAEMON_SOCKET_PATH = new NoteBytesReadOnly("ioDaemonSocketPath");
        public static final NoteBytesReadOnly RECOVERY_MODE = new NoteBytesReadOnly("recoveryMode");
        public static final NoteBytesReadOnly RECOVERY_REASON = new NoteBytesReadOnly("recoveryReason");
    }

    // ===== DATA QUERIES - let the data speak =====
    
    /**
     * Are we authenticated?
     * Source of truth: systemAccess exists
     */
    public boolean isAuthenticated() {
        return systemAccess != null;
    }
    


    /**
     * Is password keyboard claimed right now?
     */
    private boolean isPasswordKeyboardClaimed() {
        if (passwordKeyboardId == null) return false;
        if (!hasActiveIODaemonSession()) return false;
        
        ClaimedDevice device = ioDaemonSession.getClaimedDevice(passwordKeyboardId);
        return device != null && device.isActive();
    }

    boolean needsPasswordKeyboardClaim() {
        return passwordKeyboardId != null && 
            !passwordKeyboardId.equals(getDefaultKeyboardId()) &&
            !isPasswordKeyboardClaimed();
    }

    boolean usesSecureKeyboard() {
        return passwordKeyboardId != null;
    }

    boolean isLocked() {
        return state.hasState(LOCKED);
    }
    
    /**
     * Do we use a separate password keyboard?
     * (vs GUI keyboard or same keyboard for everything)
     */
    boolean usesSeparatePasswordKeyboard() {
        if (passwordKeyboardId == null) return false;
        String defaultId = getDefaultKeyboardId();
        return defaultId == null || !passwordKeyboardId.equals(defaultId);
    }
    
    
    private boolean needsPasswordKeyboardRelease() {
        return passwordKeyboardId != null &&
            !passwordKeyboardId.equals(getDefaultKeyboardId()) &&
            isPasswordKeyboardClaimed();
    }
    /**
     * Check if bootstrap config exists
     */
    private boolean needsBootstrap() {
        return !SettingsData.isSystemConfigData();
    }

    @Override
    public CompletableFuture<Void> run() {
        state.addState(INITIALIZING);
        
        Log.logMsg("[SystemTerminal] Initializing");
        return super.run()
            .thenCompose(v -> {
                state.removeState(INITIALIZING);
                
                if (needsBootstrap()) {
                    Log.logMsg("[SystemTerminal] Bootstrap needed");
                    state.addState(SETUP_NEEDED);
                    return CompletableFuture.completedFuture(null);
                } else {
                    return loadBootstrapConfig()
                        .thenRun(() -> {
                            state.addState(SETUP_COMPLETE);
                            Log.logMsg("[SystemTerminal] Bootstrap loaded");
                        })
                        .exceptionally(e -> {
                            Log.logError("[SystemTerminal] Bootstrap load failed", e);
                            state.addState(SETUP_NEEDED);
                            return null;
                        });
                }
            });
    }

    private void setupStateTransitions() {
        state.onStateAdded(SETUP_NEEDED, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] SETUP_NEEDED");
            showScreen("system-setup");
        });
        
        state.onStateAdded(CHECKING_SETTINGS, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] CHECKING_SETTINGS");
            
            if (isInRecoveryMode) {
                // In recovery mode - go to recovery screen
                Log.logMsg("[SystemTerminal] Recovery mode active: " + recoveryReason);
                state.removeState(CHECKING_SETTINGS);
                state.addState(FAILED_SETTINGS);
                return;
            }
            
            checkSettingsExist()
                .thenAccept(exists -> {
                    state.removeState(CHECKING_SETTINGS);
                    if (exists) {
                        startAuthentication();
                    } else {
                        if (!SettingsData.isIdDataFile()) {
                            state.addState(FIRST_RUN);
                        } else {
                            state.addState(FAILED_SETTINGS);
                        }
                    }
                })
                .exceptionally(ex -> {
                    state.removeState(CHECKING_SETTINGS);
                    state.addState(FAILED_SETTINGS);
                    return null;
                });
        });
        
        state.onStateAdded(FIRST_RUN, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] FIRST_RUN");
            showScreen("first-run-password");
        });

        state.onStateAdded(AUTHENTICATING, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] AUTHENTICATING");
            
            claimPasswordKeyboard()
                .thenRun(() -> {
                    startAuthTimeout();
                    showScreen("login");
                })
                .exceptionally(ex -> {
                    Log.logError("[SystemTerminal] Password keyboard setup failed: " + 
                        ex.getMessage());
                    // Show login anyway (will use GUI keyboard)
                    startAuthTimeout();
                    showScreen("login");
                    return null;
                });
        });
        
        state.onStateAdded(FAILED_SETTINGS, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] FAILED_SETTINGS");
            showScreen("settings-recovery");
        });
    }

    /**
     * Start authentication flow
     * Used for both initial login and unlock
     */
    private void startAuthentication() {
        state.addState(AUTHENTICATING);
    }
    
    /**
     * Start authentication timeout
     */
    private void startAuthTimeout() {
        cancelAuthTimeout();  // Cancel any existing
        
        authTimeoutFuture = CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(AUTH_TIMEOUT_SECONDS);
                Log.logMsg("[SystemTerminal] Authentication timeout");
                
                // Timeout → lock
                releasePasswordKeyboard()
                    .thenRun(() -> {
                        state.removeState(AUTHENTICATING);
                        if (isAuthenticated()) {
                            // Was unlocking → back to locked
                            showScreen("locked");
                        } else {
                            // Was logging in → show timeout message
                            clear()
                                .thenCompose(v -> printError("Authentication timeout"))
                                .thenCompose(v -> waitForKeyPress())
                                .thenRun(() -> showScreen("locked"));
                        }
                    });
                    
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    /**
     * Cancel authentication timeout
     */
    private void cancelAuthTimeout() {
        if (authTimeoutFuture != null) {
            authTimeoutFuture.cancel(true);
            authTimeoutFuture = null;
        }
    }

    /**
     * Load bootstrap config from disk
     */
    private CompletableFuture<Void> loadBootstrapConfig() {
        if (SettingsData.isSystemConfigData()) {
            return SettingsData.loadSettingsMap()
                .thenAccept(map -> {
                    NoteBytes keyboardBytes = map.get(ConfigKeys.PASSWORD_KEYBOARD_ID);
                    NoteBytes socketBytes = map.get(ConfigKeys.IO_DAEMON_SOCKET_PATH);
                    NoteBytes recoveryBytes = map.get(ConfigKeys.RECOVERY_MODE);
                    NoteBytes reasonBytes = map.get(ConfigKeys.RECOVERY_REASON);

                    passwordKeyboardId = keyboardBytes != null ? keyboardBytes.getAsString() : null;
                    ioDaemonSocketPath = socketBytes != null ? socketBytes.getAsString() : DEFAULT_IO_DAEMON_SOCKET_PATH;
                    isInRecoveryMode = recoveryBytes != null ? recoveryBytes.getAsBoolean() : false;
                    recoveryReason = reasonBytes != null ? reasonBytes.getAsString() : null;
                
                    if (isInRecoveryMode) {
                        Log.logMsg("[SystemTerminal] RECOVERY MODE DETECTED: " + recoveryReason);
                    }

                    Log.logMsg("[SystemTerminal] Bootstrap loaded: passwordKeyboard=" + passwordKeyboardId);
                });
        } else {
            return CompletableFuture.failedFuture(new IOException("Config does not exist"));
        }
    }

    /**
     * Save bootstrap config
     */
    private CompletableFuture<Void> saveBootstrapConfig() {
        NoteBytesMap map = new NoteBytesMap();
        
        if (passwordKeyboardId != null) {
            map.put(ConfigKeys.PASSWORD_KEYBOARD_ID, passwordKeyboardId);
        }
        map.put(ConfigKeys.IO_DAEMON_SOCKET_PATH, ioDaemonSocketPath);
        if(isInRecoveryMode){
            map.put(ConfigKeys.RECOVERY_MODE, isInRecoveryMode);
            if (recoveryReason != null) {
                map.put(ConfigKeys.RECOVERY_REASON, recoveryReason);
            }
        }
        
        return SettingsData.saveSystemConfig(map)
            .thenRun(() -> Log.logMsg("[SystemTerminal] Bootstrap saved (recovery=" + isInRecoveryMode + ")"));
    }

    /**
     * Complete bootstrap wizard
     */
    public CompletableFuture<Void> completeBootstrap(
            String selectedKeyboardId) {
        
        this.passwordKeyboardId = selectedKeyboardId;
        
        return saveBootstrapConfig()
            .thenRun(() -> {
                Log.logMsg("[SystemTerminal] Bootstrap complete");
                if (state.hasState(SETUP_NEEDED)) {
                    state.removeState(SETUP_NEEDED);
                    state.addState(SETUP_COMPLETE);
                    state.addState(CHECKING_SETTINGS);
                }
            });
    }

    /**
     * Get password event registry
     * Returns password keyboard registry if claimed, otherwise default
     */
    EventHandlerRegistry getPasswordEventHandlerRegistry() {
        if (passwordKeyboardId != null) {
            EventHandlerRegistry registry = getClaimedDeviceRegistry(passwordKeyboardId);
            if (registry != null) {
                return registry;
            }
        }
        return getEventHandlerRegistry();
    }

    /**
     * Claim password keyboard for authentication
     */
    CompletableFuture<Void> claimPasswordKeyboard() {
        if (!needsPasswordKeyboardClaim()) {
            Log.logMsg("[SystemTerminal] Password keyboard not needed or already claimed");
            return CompletableFuture.completedFuture(null);
        }

        // Ensure IODaemon available
        return ioDaemonManager.ensureAvailable()
            .thenCompose(ioDaemonPath -> connectToIODaemon(ioDaemonPath))
            .thenCompose(session -> session.discoverDevices())
            .thenCompose(devices -> {
                // Verify keyboard exists
                var deviceInfo = devices.stream()
                    .filter(d -> d.usbDevice().getDeviceId().equals(passwordKeyboardId))
                    .findFirst()
                    .orElse(null);
                
                if (deviceInfo == null) {
                    throw new RuntimeException("Password keyboard not found: " + passwordKeyboardId);
                }
                
                return claimDevice(passwordKeyboardId, "parsed");
            })
            .thenRun(() -> {
                Log.logMsg("[SystemTerminal] Password keyboard claimed: " + passwordKeyboardId);
            })
            .exceptionally(ex -> {
                passwordKeyboardId = null;
                Log.logError("[SystemTerminal] Password keyboard claim failed: " + ex.getMessage());
                return null;
            });
    }

    /**
     * Release password keyboard after authentication
     * Only if it's separate from default keyboard
     */
    CompletableFuture<Void> releasePasswordKeyboard() {
        if (!needsPasswordKeyboardRelease()) {
            return CompletableFuture.completedFuture(null);
        }
            
        Log.logMsg("[SystemTerminal] Releasing password keyboard");
        
        return releaseDevice(passwordKeyboardId)
            .thenCompose(v -> {
                // Disconnect session if no devices claimed
                if (ioDaemonSession.getClaimedDevices().isEmpty()) {
                    return disconnectFromIODaemon();
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenRun(() -> Log.logMsg("[SystemTerminal] Password keyboard released"))
            .exceptionally(ex -> {
                Log.logError("[SystemTerminal] Release failed: " + ex.getMessage());
                return null;
            });
    }



    public void registerScreen(String id, ScreenFactory factory) {
        screenFactories.put(id, factory);
    }

    private void registerDefaultScreens() {
        registerScreen("system-setup",          SystemSetupScreen::new);
        registerScreen("first-run-password",    FirstRunPasswordScreen::new);
        registerScreen("login",                 LoginScreen::new);
        registerScreen("locked",                LockedScreen::new);
        registerScreen("main-menu",             MainMenuScreen::new);
        registerScreen("settings",              SettingsScreen::new);
        registerScreen("node-manager",          NodeManagerScreen::new);
        registerScreen("settings-recovery",     FailedSettingsScreen::new);
    }
    
    // ===== LIFECYCLE =====
    
    /**
     * Open terminal
     */
    public CompletableFuture<Void> open() {
        if (isVisible) {
            return CompletableFuture.completedFuture(null);
        }
        
        state.addState(OPENING);
        
        return show()
            .thenRun(() -> {
                state.removeState(OPENING);
                isVisible = true;
                
                // Route based on data
                if (state.hasState(SETUP_NEEDED)) {
                    showScreen("system-setup");
                } else if (!isAuthenticated()) {
                    // No systemAccess → need to create/load it
                    state.addState(CHECKING_SETTINGS);
                } else if (isLocked()) {
                    // Have systemAccess but locked → show locked screen
                    showScreen("locked");
                } else {
                    // Have systemAccess and not locked → go to menu
                    showScreen("main-menu");
                }

            });
    }

    public CompletableFuture<Void> lock(){
        if(isVisible){
            return close();
        }else{
            state.removeState(AUTHENTICATING);
            if (isAuthenticated()) {
                // Lock on close for security
                state.addState(LOCKED);
            }
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * Close terminal
     * Releases password keyboard, keeps runtime in memory
     */
    public CompletableFuture<Void> close() {
        if (!isVisible) {
            return CompletableFuture.completedFuture(null);
        }
        
        if (currentScreen != null) {
            currentScreen.onHide();
        }
        
        cancelAuthTimeout();

        

        
        // Release password keyboard if separate
        return releasePasswordKeyboard()
            .thenCompose(v -> hide())
            .thenRun(() -> {
                isVisible = false;
                state.removeState(OPENING);
                state.removeState(SHOWING_SCREEN);
                state.removeState(AUTHENTICATING);
                if (isAuthenticated()) {
                    // Lock on close for security
                    state.addState(LOCKED);
                }
            })
            .exceptionally(ex -> {
                Log.logError("[SystemTerminal] Close error: " + ex.getMessage());
                return null;
            });
    }
    
    // ===== AUTHENTICATION =====
    
    private CompletableFuture<Boolean> checkSettingsExist() {
        if (SettingsData.isSettingsData()) {
            return SettingsData.loadSettingsMap().thenApply(map -> map != null);
        }
        return CompletableFuture.completedFuture(false);
    }
    
    /**
     * Create new system (first run)
     */
    public CompletableFuture<Void> createNewSystem(NoteBytesEphemeral password) {
        return SettingsData.createSettings(password)
            .thenApply(settingsData -> {
                password.close();
                
                RuntimeAccess access = new RuntimeAccess();
                SystemRuntime runtime = new SystemRuntime(settingsData, registry, access);
                
                // THE state: systemAccess now exists → authenticated
                this.systemRuntime = runtime;
                this.systemAccess = access;
                
                state.removeState(FIRST_RUN);
                cancelAuthTimeout();
                
                // Release password keyboard if separate
                return releasePasswordKeyboard();
            })
            .thenRun(() -> showScreen("main-menu"));
    }

    /**
     * Verify and load system (login or unlock)
     * Single entry point for all authentication
     */
    public CompletableFuture<Boolean> authenticate(NoteBytesEphemeral password) {
        if (systemAccess != null) {
            // Unlocking → verify with runtime
            return systemAccess.verifyPassword(password)
                .thenApply(valid -> {
                    if (valid) {
                        onAuthenticationSuccess();
                    }
                    return valid;
                });
        } else {
            // Logging in → load runtime
            return SettingsData.loadSettingsMap()
                .thenCompose(settingsMap -> 
                    SettingsData.verifyPassword(password, settingsMap)
                        .thenCompose(valid -> {
                            if (valid) {
                                return SettingsData.loadSettingsData(password, settingsMap)
                                    .thenApply(settingsData -> {
                                        RuntimeAccess access = new RuntimeAccess();
                                        SystemRuntime runtime = new SystemRuntime(
                                            settingsData, registry, access);
                                        
                                        // THE state: systemAccess now exists → authenticated
                                        this.systemRuntime = runtime;
                                        this.systemAccess = access;
                                        
                                        onAuthenticationSuccess();
                                        return true;
                                    });
                            }
                            return CompletableFuture.completedFuture(false);
                        })
                );
        }
    }
    
    /**
     * Handle successful authentication
     */
    private void onAuthenticationSuccess() {
        cancelAuthTimeout();
        state.removeState(AUTHENTICATING);
        state.removeState(LOCKED);
        // Release password keyboard if separate
        releasePasswordKeyboard()
            .thenRun(() -> showScreen("main-menu"))
            .exceptionally(ex -> {
                Log.logError("[SystemTerminal] Post-auth cleanup error: " + ex.getMessage());
                showScreen("main-menu");  // Continue anyway
                return null;
            });
    }

    // ====== Recovery ========

    /**
     * Enter recovery mode
     * Called before risky operations like password change
     */
    CompletableFuture<Void> enterRecoveryMode(String reason) {
        this.isInRecoveryMode = true;
        this.recoveryReason = reason;
        
        Log.logMsg("[SystemTerminal] Entering recovery mode: " + reason);
        
        return saveBootstrapConfig();
    }

    /**
     * Exit recovery mode
     * Called after successful completion of risky operations
     */
    CompletableFuture<Void> exitRecoveryMode() {
        this.isInRecoveryMode = false;
        this.recoveryReason = null;
        
        Log.logMsg("[SystemTerminal] Exiting recovery mode");
        
        return saveBootstrapConfig();
    }

    /**
     * Check if in recovery mode
     */
    boolean isInRecoveryMode() {
        return isInRecoveryMode;
    }

    String getRecoveryReason() {
        return recoveryReason;
    }

    /**
     * Recover system with existing settings
     */
    CompletableFuture<Void> recoverSystem(SettingsData settingsData) {
        if (!state.hasState(FAILED_SETTINGS)) {
            throw new SecurityException("Recovery only allowed in FAILED_SETTINGS state");
        }
        
        return CompletableFuture.runAsync(() -> {
            RuntimeAccess access = new RuntimeAccess();
            SystemRuntime runtime = new SystemRuntime(settingsData, registry, access);
            
            // THE state: systemAccess now exists → authenticated
            this.systemRuntime = runtime;
            this.systemAccess = access;
            
            state.removeState(FAILED_SETTINGS);
        }).thenRun(() -> showScreen("main-menu"));
    }
    
    // ===== SCREEN MANAGEMENT =====
    
    public CompletableFuture<Void> showScreen(TerminalScreen screen) {
        screen.setTerminal(this);
        
        if (currentScreen != null && currentScreen != screen) {
            currentScreen.onHide();
        }
        
        currentScreen = screen;
        state.addState(SHOWING_SCREEN);
        return screen.onShow();
    }
    
    public CompletableFuture<Void> showScreen(String screenName) {
        ScreenFactory screenFactory = screenFactories.get(screenName);
        if (screenFactory == null) {
            Log.logError("[SystemTerminal] Screen not found: " + screenName);
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Screen not found: " + screenName));
        }
        TerminalScreen screen = screenFactory.create(screenName, this);
        return showScreen(screen);
    }
    
    TerminalScreen getCurrentScreen() {
        return currentScreen;
    }
    
    @Override
    protected void onContainerResized(ContainerResizeEvent event) {
        setDimensions(event.getWidth(), event.getHeight());
        
        if (currentScreen != null) {
            currentScreen.render();
        }
    }

    @Override
    protected void onContainerClosed() {
        close();
    }
    
    // ===== GETTERS =====
    
    public boolean isVisible() {
        return isVisible;
    }
    
    BitFlagStateMachine getState() {
        return state;
    }
    
    RuntimeAccess getSystemAccess() {
        return systemAccess;
    }

    ContextPath getSessionPath() {
        return sessionPath;
    }

    String getPasswordKeyboardId() {
        return passwordKeyboardId;
    }
    
    String getIODaemonSocketPath() {
        return ioDaemonSocketPath;
    }
    
    public IODaemonManager getIoDaemonManager() {
        return ioDaemonManager;
    }
    
    public boolean isIODaemonHealthy() {
        return ioDaemonManager.isHealthy();
    }
    
    // ===== NAVIGATION =====
    
    public CompletableFuture<Void> goBack() {
        if (currentScreen != null && currentScreen.getParent() != null) {
            return showScreen(currentScreen.getParent().getName());
        }
        return showScreen("main-menu");
    }
    
    public CompletableFuture<Void> showMainMenu() {
        return showScreen("main-menu");
    }
}