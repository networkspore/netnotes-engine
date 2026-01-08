package io.netnotes.engine.core.system;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.containers.RenderingService;
import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.ui.Renderable;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.daemon.ClaimedDevice;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

/**
 * SystemApplication - Application Logic Layer
 * 
 * This class wraps a TerminalContainerHandle and provides:
 * - Bootstrap configuration (keyboard selection, IODaemon setup)
 * - Authentication flow (first run, login, unlock)
 * - IODaemon lifecycle management
 * - Screen navigation and coordination
 * 
 * It does NOT own the terminal handle - the handle is created and managed
 * by ClientTerminalRenderManager. SystemApplication just uses it.
 */
public class SystemApplication {
    
    public static final String DEFAULT_IO_DAEMON_SOCKET_PATH = "/var/run/io-daemon.sock";
    
    @FunctionalInterface
    public interface ScreenFactory {
        TerminalScreen create(String id, SystemApplication systemApplication);
    }
    
    // The terminal handle we're using (provided by render manager)
    // Nullable: application can run headless until terminal attaches
    private TerminalContainerHandle terminalHandle;
    private final ProcessRegistryInterface registry;
    private final ContextPath systemContextPath;

    // Bootstrap configuration - loaded once from disk
    private String passwordKeyboardId = null;  // null = use GUI keyboard
    private String ioDaemonSocketPath = DEFAULT_IO_DAEMON_SOCKET_PATH;
    private boolean isInRecoveryMode = false;
    private String recoveryReason = null;
 
    private final IODaemonManager ioDaemonManager;
    private final RenderingService renderingService;
    
    // Authentication state - THE source of truth
    // Cleared on terminal detach for security
    private SystemRuntime systemRuntime = null;
    private RuntimeAccess systemAccess = null;  // null = not authenticated
    
    // Authentication timeout
    private CompletableFuture<Void> authTimeoutFuture = null;
    private static final long AUTH_TIMEOUT_SECONDS = 30;
    
    private volatile boolean isVisible = false;
    
    // Screen persistence - screen to restore when terminal reattaches
    private TerminalScreen currentScreen;
    private String lastScreenName = null;  // Remember screen across detach/attach
    private final Map<String, ScreenFactory> screenFactories = new HashMap<>();
    
    // States - UI flow only, not data states
    private final BitFlagStateMachine stateMachine;
    public static final int INITIALIZING = 63;
    public static final int SETUP_NEEDED = 62;
    public static final int SETUP_COMPLETE = 61;
    public static final int LOCKED = 60;
    public static final int OPENING = 59;
    public static final int CHECKING_SETTINGS = 58;
    public static final int FIRST_RUN = 57;
    public static final int AUTHENTICATING = 56;
    public static final int SHOWING_SCREEN = 55;
    public static final int ERROR = 54;
    public static final int FAILED_SETTINGS = 53;
    
    // Attachment states - for daemon mode support
    public static final int DETACHED = 52;       // No terminal attached (headless)
    public static final int ATTACHED_LOCAL = 51; // Local terminal attached
    public static final int ATTACHED_REMOTE = 50; // Remote client attached
    
    /**
     * Constructor - can be created with or without a terminal handle
     * 
     * @param terminalHandle The terminal handle (can be null for headless mode)
     * @param renderingService The rendering service
     * @param registry The process registry
     * @param systemContextPath The system's context path for session data
     */
    public SystemApplication(
        TerminalContainerHandle terminalHandle,
        RenderingService renderingService,
        ProcessRegistryInterface registry,
        ContextPath systemContextPath
    ) {
        this.terminalHandle = terminalHandle;
        this.renderingService = renderingService;
        this.registry = registry;
        this.systemContextPath = systemContextPath;
        this.stateMachine = new BitFlagStateMachine("SystemApplication");
        this.ioDaemonManager = new IODaemonManager(this, registry);
        
        // Set initial attachment state
        if (terminalHandle == null) {
            stateMachine.addState(DETACHED);
        } else {
            stateMachine.addState(ATTACHED_LOCAL);
        }
        
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
    
    public boolean isAuthenticated() {
        return systemAccess != null;
    }
    
    public boolean isTerminalAttached() {
        return terminalHandle != null && !stateMachine.hasState(DETACHED);
    }
    
    public boolean isLocalAttachment() {
        return stateMachine.hasState(ATTACHED_LOCAL);
    }
    
    public boolean isRemoteAttachment() {
        return stateMachine.hasState(ATTACHED_REMOTE);
    }
    
    private boolean isPasswordKeyboardClaimed() {
        if (passwordKeyboardId == null) return false;
        if (!isTerminalAttached()) return false;
        if (!terminalHandle.hasActiveIODaemonSession()) return false;
        
        ClaimedDevice device = terminalHandle.getIODaemonSession().getClaimedDevice(passwordKeyboardId);
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
        return stateMachine.hasState(LOCKED);
    }
    
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
    
    private boolean needsBootstrap() {
        return !SettingsData.isSystemConfigData();
    }

    /**
     * Initialize - called after construction
     * Can initialize without terminal (headless mode)
     */
    public CompletableFuture<Void> initialize() {
        stateMachine.addState(INITIALIZING);
        
        Log.logMsg("[SystemApplication] Initializing (terminal attached: " + isTerminalAttached() + ")");
        
        // If we have a terminal, wait for it to be ready
        CompletableFuture<Void> terminalReady = isTerminalAttached() 
            ? terminalHandle.waitUntilReady()
            : CompletableFuture.completedFuture(null);
        
        return terminalReady.thenCompose(v -> {
                stateMachine.removeState(INITIALIZING);
                
                if (needsBootstrap()) {
                    Log.logMsg("[SystemApplication] Bootstrap needed");
                    stateMachine.addState(SETUP_NEEDED);
                    return CompletableFuture.completedFuture(null);
                } else {
                    return loadBootstrapConfig()
                        .thenRun(() -> {
                            stateMachine.addState(SETUP_COMPLETE);
                            Log.logMsg("[SystemApplication] Bootstrap loaded");
                        })
                        .exceptionally(e -> {
                            Log.logError("[SystemApplication] Bootstrap load failed", e);
                            stateMachine.addState(SETUP_NEEDED);
                            return null;
                        });
                }
            });
    }

    protected void setupStateTransitions() {
        stateMachine.onStateAdded(SETUP_NEEDED, (old, now, bit) -> {
            Log.logMsg("[SystemApplication] SETUP_NEEDED");
            showScreen("system-setup");
        });
        
        stateMachine.onStateAdded(CHECKING_SETTINGS, (old, now, bit) -> {
            Log.logMsg("[SystemApplication] CHECKING_SETTINGS");
            
            if (isInRecoveryMode) {
                Log.logMsg("[SystemApplication] Recovery mode active: " + recoveryReason);
                stateMachine.removeState(CHECKING_SETTINGS);
                stateMachine.addState(FAILED_SETTINGS);
                return;
            }
            
            checkSettingsExist()
                .thenAccept(exists -> {
                    stateMachine.removeState(CHECKING_SETTINGS);
                    if (exists) {
                        startAuthentication();
                    } else {
                        if (!SettingsData.isIdDataFile()) {
                            stateMachine.addState(FIRST_RUN);
                        } else {
                            stateMachine.addState(FAILED_SETTINGS);
                        }
                    }
                })
                .exceptionally(ex -> {
                    stateMachine.removeState(CHECKING_SETTINGS);
                    stateMachine.addState(FAILED_SETTINGS);
                    return null;
                });
        });
        
        stateMachine.onStateAdded(FIRST_RUN, (old, now, bit) -> {
            Log.logMsg("[SystemApplication] FIRST_RUN");
            showScreen("first-run-password");
        });

        stateMachine.onStateAdded(AUTHENTICATING, (old, now, bit) -> {
            Log.logMsg("[SystemApplication] AUTHENTICATING");
            
            claimPasswordKeyboard()
                .thenRun(() -> {
                    startAuthTimeout();
                    showScreen("login");
                })
                .exceptionally(ex -> {
                    Log.logError("[SystemApplication] Password keyboard setup failed: " + 
                        ex.getMessage());
                    startAuthTimeout();
                    showScreen("login");
                    return null;
                });
        });
        
        stateMachine.onStateAdded(FAILED_SETTINGS, (old, now, bit) -> {
            Log.logMsg("[SystemApplication] FAILED_SETTINGS");
            showScreen("settings-recovery");
        });
    }

    private void startAuthentication() {
        stateMachine.addState(AUTHENTICATING);
    }
    
    private void startAuthTimeout() {
        cancelAuthTimeout();
        
        authTimeoutFuture = CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(AUTH_TIMEOUT_SECONDS);
                Log.logMsg("[SystemApplication] Authentication timeout");
                
                releasePasswordKeyboard()
                    .thenRun(() -> {
                        stateMachine.removeState(AUTHENTICATING);
                        if (isAuthenticated()) {
                            showScreen("locked");
                        } else {
                            terminalHandle.clear()
                                .thenCompose(v -> terminalHandle.printError("Authentication timeout"))
                                .thenCompose(v -> terminalHandle.waitForKeyPress())
                                .thenRun(() -> showScreen("locked"));
                        }
                    });
                    
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    private void cancelAuthTimeout() {
        if (authTimeoutFuture != null) {
            authTimeoutFuture.cancel(true);
            authTimeoutFuture = null;
        }
    }

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
                        Log.logMsg("[SystemApplication] RECOVERY MODE DETECTED: " + recoveryReason);
                    }

                    Log.logMsg("[SystemApplication] Bootstrap loaded: passwordKeyboard=" + passwordKeyboardId);
                });
        } else {
            return CompletableFuture.failedFuture(new IOException("Config does not exist"));
        }
    }

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
            .thenRun(() -> Log.logMsg("[SystemApplication] Bootstrap saved (recovery=" + isInRecoveryMode + ")"));
    }

    public CompletableFuture<Void> completeBootstrap(String selectedKeyboardId) {
        this.passwordKeyboardId = selectedKeyboardId;
        
        return saveBootstrapConfig()
            .thenRun(() -> {
                Log.logMsg("[SystemApplication] Bootstrap complete");
                if (stateMachine.hasState(SETUP_NEEDED)) {
                    stateMachine.removeState(SETUP_NEEDED);
                    stateMachine.addState(SETUP_COMPLETE);
                    stateMachine.addState(CHECKING_SETTINGS);
                }
            });
    }

    EventHandlerRegistry getPasswordEventHandlerRegistry() {
        if (!isTerminalAttached()) {
            throw new IllegalStateException("Cannot get event registry - no terminal attached");
        }
        
        if (passwordKeyboardId != null) {
            EventHandlerRegistry registry = terminalHandle.getClaimedDeviceRegistry(passwordKeyboardId);
            if (registry != null) {
                return registry;
            }
        }
        return terminalHandle.getEventHandlerRegistry();
    }

    CompletableFuture<Void> claimPasswordKeyboard() {
        if (!needsPasswordKeyboardClaim()) {
            Log.logMsg("[SystemApplication] Password keyboard not needed or already claimed");
            return CompletableFuture.completedFuture(null);
        }
        
        if (!isTerminalAttached()) {
            Log.logMsg("[SystemApplication] Cannot claim keyboard - no terminal attached");
            return CompletableFuture.completedFuture(null);
        }

        return ioDaemonManager.ensureAvailable()
            .thenCompose(ioDaemonPath -> terminalHandle.connectToIODaemon(ioDaemonPath))
            .thenCompose(session -> session.discoverDevices())
            .thenCompose(devices -> {
                var deviceInfo = devices.stream()
                    .filter(d -> d.usbDevice().getDeviceId().equals(passwordKeyboardId))
                    .findFirst()
                    .orElse(null);
                
                if (deviceInfo == null) {
                    throw new RuntimeException("Password keyboard not found: " + passwordKeyboardId);
                }
                
                return terminalHandle.claimDevice(passwordKeyboardId, "parsed");
            })
            .thenRun(() -> {
                Log.logMsg("[SystemApplication] Password keyboard claimed: " + passwordKeyboardId);
            })
            .exceptionally(ex -> {
                passwordKeyboardId = null;
                Log.logError("[SystemApplication] Password keyboard claim failed: " + ex.getMessage());
                return null;
            });
    }

    CompletableFuture<Void> releasePasswordKeyboard() {
        if (!needsPasswordKeyboardRelease()) {
            return CompletableFuture.completedFuture(null);
        }
        
        if (!isTerminalAttached()) {
            return CompletableFuture.completedFuture(null);
        }
            
        Log.logMsg("[SystemApplication] Releasing password keyboard");
        
        return terminalHandle.releaseDevice(passwordKeyboardId)
            .thenCompose(v -> {
                if (terminalHandle.getIODaemonSession().getClaimedDevices().isEmpty()) {
                    return terminalHandle.disconnectFromIODaemon();
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenRun(() -> Log.logMsg("[SystemApplication] Password keyboard released"))
            .exceptionally(ex -> {
                Log.logError("[SystemApplication] Release failed: " + ex.getMessage());
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
     * Attach a terminal to the application
     * For daemon mode - terminal connects after application is initialized
     * 
     * @param handle The terminal handle to attach
     * @param isRemote True if this is a remote client, false for local terminal
     */
    public CompletableFuture<Void> attachTerminal(TerminalContainerHandle handle, boolean isRemote) {
        if (isTerminalAttached()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Terminal already attached"));
        }
        
        Log.logMsg("[SystemApplication] Attaching terminal (remote: " + isRemote + ")");
        
        this.terminalHandle = handle;
        
        // Update attachment state
        stateMachine.removeState(DETACHED);
        stateMachine.addState(isRemote ? ATTACHED_REMOTE : ATTACHED_LOCAL);
        
        // Wait for terminal to be ready, then open
        return handle.waitUntilReady()
            .thenCompose(v -> open());
    }
    
    /**
     * Detach terminal from the application
     * Application continues running headless
     * Authentication is CLEARED for security
     */
    public CompletableFuture<Void> detachTerminal() {
        if (!isTerminalAttached()) {
            Log.logMsg("[SystemApplication] No terminal attached, nothing to detach");
            return CompletableFuture.completedFuture(null);
        }
        
        Log.logMsg("[SystemApplication] Detaching terminal");
        
        // Remember current screen for when we reattach
        if (currentScreen != null) {
            lastScreenName = currentScreen.getName();
            currentScreen.onHide();
        }
        
        cancelAuthTimeout();
        
        return releasePasswordKeyboard()
            .thenCompose(v -> terminalHandle != null ? terminalHandle.hide() : CompletableFuture.completedFuture(null))
            .thenRun(() -> {
                isVisible = false;
                
                // Clear authentication for security
                Log.logMsg("[SystemApplication] Clearing authentication on detach");
                systemAccess = null;
                systemRuntime = null;
                
                // Clear UI state
                stateMachine.removeState(OPENING);
                stateMachine.removeState(SHOWING_SCREEN);
                stateMachine.removeState(AUTHENTICATING);
                stateMachine.removeState(ATTACHED_LOCAL);
                stateMachine.removeState(ATTACHED_REMOTE);
                stateMachine.addState(DETACHED);
                stateMachine.addState(LOCKED);
                
                // Null out the handle
                terminalHandle = null;
                
                Log.logMsg("[SystemApplication] Terminal detached, running headless");
            })
            .exceptionally(ex -> {
                Log.logError("[SystemApplication] Detach error: " + ex.getMessage());
                return null;
            });
    }
    
    public CompletableFuture<Void> open() {
        if (!isTerminalAttached()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Cannot open - no terminal attached"));
        }
        
        if (isVisible) {
            return CompletableFuture.completedFuture(null);
        }
        
        stateMachine.addState(OPENING);
        
        return terminalHandle.show()
            .thenRun(() -> {
                stateMachine.removeState(OPENING);
                isVisible = true;
                
                // Restore last screen or go to appropriate screen
                if (lastScreenName != null && isAuthenticated()) {
                    // Restore previous screen (user was working on something)
                    Log.logMsg("[SystemApplication] Restoring screen: " + lastScreenName);
                    showScreen(lastScreenName);
                } else if (stateMachine.hasState(SETUP_NEEDED)) {
                    showScreen("system-setup");
                } else if (!isAuthenticated()) {
                    stateMachine.addState(CHECKING_SETTINGS);
                } else if (isLocked()) {
                    showScreen("locked");
                } else {
                    showScreen("main-menu");
                }
            });
    }

    public CompletableFuture<Void> lock(){
        if(isVisible){
            return close();
        }else{
            stateMachine.removeState(AUTHENTICATING);
            if (isAuthenticated()) {
                stateMachine.addState(LOCKED);
            }
            return CompletableFuture.completedFuture(null);
        }
    }
    
    public CompletableFuture<Void> close() {
        if (!isTerminalAttached()) {
            Log.logMsg("[SystemApplication] No terminal attached, nothing to close");
            return CompletableFuture.completedFuture(null);
        }
        
        if (!isVisible) {
            return CompletableFuture.completedFuture(null);
        }
        
        if (currentScreen != null) {
            lastScreenName = currentScreen.getName();
            currentScreen.onHide();
        }
        
        cancelAuthTimeout();
        
        return releasePasswordKeyboard()
            .thenCompose(v -> terminalHandle.hide())
            .thenRun(() -> {
                isVisible = false;
                stateMachine.removeState(OPENING);
                stateMachine.removeState(SHOWING_SCREEN);
                stateMachine.removeState(AUTHENTICATING);
                if (isAuthenticated()) {
                    stateMachine.addState(LOCKED);
                }
            })
            .exceptionally(ex -> {
                Log.logError("[SystemApplication] Close error: " + ex.getMessage());
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
    
    public CompletableFuture<Void> createNewSystem(NoteBytesEphemeral password) {
        return SettingsData.createSettings(password)
            .thenApply(settingsData -> {
                password.close();
                
                RuntimeAccess access = new RuntimeAccess();
                SystemRuntime runtime = new SystemRuntime(settingsData, registry, access);
                
                this.systemRuntime = runtime;
                this.systemAccess = access;
                
                stateMachine.removeState(FIRST_RUN);
                cancelAuthTimeout();
                
                return releasePasswordKeyboard();
            })
            .thenRun(() -> showScreen("main-menu"));
    }

    public CompletableFuture<Boolean> authenticate(NoteBytesEphemeral password) {
        if (systemAccess != null) {
            return systemAccess.verifyPassword(password)
                .thenApply(valid -> {
                    if (valid) {
                        onAuthenticationSuccess();
                    }
                    return valid;
                });
        } else {
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
    
    private void onAuthenticationSuccess() {
        cancelAuthTimeout();
        stateMachine.removeState(AUTHENTICATING);
        stateMachine.removeState(LOCKED);
        releasePasswordKeyboard()
            .thenRun(() -> showScreen("main-menu"))
            .exceptionally(ex -> {
                Log.logError("[SystemApplication] Post-auth cleanup error: " + ex.getMessage());
                showScreen("main-menu");
                return null;
            });
    }

    CompletableFuture<Void> enterRecoveryMode(String reason) {
        this.isInRecoveryMode = true;
        this.recoveryReason = reason;
        
        Log.logMsg("[SystemApplication] Entering recovery mode: " + reason);
        
        return saveBootstrapConfig();
    }

    CompletableFuture<Void> exitRecoveryMode() {
        this.isInRecoveryMode = false;
        this.recoveryReason = null;
        
        Log.logMsg("[SystemApplication] Exiting recovery mode");
        
        return saveBootstrapConfig();
    }

    boolean isInRecoveryMode() {
        return isInRecoveryMode;
    }

    String getRecoveryReason() {
        return recoveryReason;
    }

    CompletableFuture<Void> recoverSystem(SettingsData settingsData) {
        if (!stateMachine.hasState(FAILED_SETTINGS)) {
            throw new SecurityException("Recovery only allowed in FAILED_SETTINGS state");
        }
        
        return CompletableFuture.runAsync(() -> {
            RuntimeAccess access = new RuntimeAccess();
            SystemRuntime runtime = new SystemRuntime(settingsData, registry, access);
            
            this.systemRuntime = runtime;
            this.systemAccess = access;
            
            stateMachine.removeState(FAILED_SETTINGS);
        }).thenRun(() -> showScreen("main-menu"));
    }
    
    // ===== SCREEN MANAGEMENT =====
    
    public CompletableFuture<Void> showScreen(TerminalScreen screen) {
        Log.logMsg("[SystemApplication] showScreen(TerminalScreen) called for: " + screen.getName());
        
        if(screen.isShowing()){
            Log.logMsg("[SystemApplication] Screen already showing");
            return CompletableFuture.completedFuture(null);
        }
        
        
        if (currentScreen != null && currentScreen != screen) {
            Log.logMsg("[SystemApplication] Hiding current screen: " + currentScreen.getName());
            currentScreen.onHide();
        }
        
        currentScreen = screen;
        stateMachine.addState(SHOWING_SCREEN);
        
        Log.logMsg("[SystemApplication] Calling screen.onShow()");
        return screen.onShow();
    }
    
    public CompletableFuture<Void> showScreen(String screenName) {
        Log.logMsg("[SystemApplication] showScreen(String) called for: " + screenName);
        
        ScreenFactory screenFactory = screenFactories.get(screenName);
        if (screenFactory == null) {
            Log.logError("[SystemApplication] Screen not found: " + screenName);
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Screen not found: " + screenName));
        }
        
        Log.logMsg("[SystemApplication] Creating screen: " + screenName);
        TerminalScreen screen = screenFactory.create(screenName, this);
        Log.logMsg("[SystemApplication] Screen created: " + screen.getClass().getSimpleName());
        
        return showScreen(screen);
    }
    
    TerminalScreen getCurrentScreen() {
        return currentScreen;
    }
    
    // ===== GETTERS =====
    
    /**
     * Get terminal handle - throws if not attached
     * Use isTerminalAttached() to check first
     */
    public TerminalContainerHandle getTerminal() {
        if (terminalHandle == null) {
            throw new IllegalStateException("No terminal attached - application is running headless");
        }
        return terminalHandle;
    }

    void invalidate(){
        if (isTerminalAttached()) {
            terminalHandle.invalidate();
        }
    }

    void setRenderable(Renderable renderable){
        if (isTerminalAttached()) {
            terminalHandle.setRenderable(renderable);
        }
    }
    
    public boolean isVisible() {
        return isVisible;
    }
    
    public BitFlagStateMachine getStateMachine() {
        return stateMachine;
    }
    
    RuntimeAccess getSystemAccess() {
        return systemAccess;
    }

    ContextPath getSystemContextPath() {
        return systemContextPath;
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
    
    private String getDefaultKeyboardId() {
        if (!isTerminalAttached()) {
            return null;
        }
        return terminalHandle.getDefaultKeyboardId();
    }

    ContextPath getContextPath(){
        return systemContextPath;
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