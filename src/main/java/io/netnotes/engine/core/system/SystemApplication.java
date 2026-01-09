package io.netnotes.engine.core.system;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.containers.RenderingService;
import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.daemon.ClaimedDevice;
import io.netnotes.engine.io.input.events.EventHandlerRegistry;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

/**
 * System application - implements authentication and system management
 * Extends IODaemonApplication to add:
 * - Bootstrap configuration management
 * - Authentication flow (first run, login, unlock)
 * - Password keyboard management
 * - Recovery mode
 * - System-specific screens
 */
public class SystemApplication extends TerminalApplication<SystemApplication> {

    public static final String DEFAULT_IO_DAEMON_SOCKET_PATH = "/var/run/io-daemon.sock";
    
    // ===== APPLICATION STATES =====
    public static final int INITIALIZING = 63;
    public static final int SETUP_NEEDED = 62;
    public static final int SETUP_COMPLETE = 61;
    public static final int LOCKED = 60;
    public static final int CHECKING_SETTINGS = 58;
    public static final int FIRST_RUN = 57;
    public static final int AUTHENTICATING = 73;
    public static final int ERROR = 54;
    public static final int FAILED_SETTINGS = 53;
    
    private static final long AUTH_TIMEOUT_SECONDS = 30;
    
    // ===== DEPENDENCIES =====
    private final RenderingService renderingService;
    private final ContextPath systemContextPath;
    
    // ===== IODaemon SETUP (system-level only) =====
    private final IODaemonManager ioDaemonManager;
    
    // ===== AUTHENTICATION STATE =====
    private SystemRuntime systemRuntime;
    private RuntimeAccess systemAccess;
    private CompletableFuture<Void> authTimeoutFuture;
    
    // ===== BOOTSTRAP CONFIGURATION =====
    private String passwordKeyboardId;
    private boolean isInRecoveryMode = false;
    private String recoveryReason;
    
    // ===== CONFIG KEYS =====
    private static class ConfigKeys {
        public static final NoteBytesReadOnly PASSWORD_KEYBOARD_ID = 
            new NoteBytesReadOnly("passwordKeyboardId");
        public static final NoteBytesReadOnly IO_DAEMON_SOCKET_PATH = 
            new NoteBytesReadOnly("ioDaemonSocketPath");
        public static final NoteBytesReadOnly RECOVERY_MODE = 
            new NoteBytesReadOnly("recoveryMode");
        public static final NoteBytesReadOnly RECOVERY_REASON = 
            new NoteBytesReadOnly("recoveryReason");
    }
    
    // ===== CONSTRUCTION =====
    
    public SystemApplication(
        TerminalContainerHandle terminalHandle,
        RenderingService renderingService,
        ProcessRegistryInterface registry,
        ContextPath systemContextPath
    ) {
        super("SystemApplication", terminalHandle, registry);
        
        this.renderingService = renderingService;
        this.systemContextPath = systemContextPath;
        this.ioDaemonManager = new IODaemonManager(registry, DEFAULT_IO_DAEMON_SOCKET_PATH);
        
        setupStateTransitions();
    }
    // ===== SCREEN REGISTRATION =====
    
    @Override
    protected void registerScreens() {
        registerScreen("system-setup", SystemSetupScreen::new);
        registerScreen("first-run-password", FirstRunPasswordScreen::new);
        registerScreen("login", LoginScreen::new);
        registerScreen("locked", LockedScreen::new);
        registerScreen("main-menu", MainMenuScreen::new);
        registerScreen("settings", SettingsScreen::new);
        registerScreen("node-manager", NodeManagerScreen::new);
        registerScreen("settings-recovery", FailedSettingsScreen::new);
    }
    
    public IODaemonManager getIoDaemonManager() {
        return ioDaemonManager;
    }
    
    public boolean isIODaemonHealthy() {
        return ioDaemonManager.isHealthy();
    }
    

    public String getIODaemonSocketPath() {
        return ioDaemonManager.getIODaemonSocketPath();
    }
    // ===== INITIALIZATION =====
    
    public CompletableFuture<Void> initialize() {
        stateMachine.addState(INITIALIZING);
        
        Log.logMsg("[SystemApplication] Initializing (terminal attached: " + 
            isTerminalAttached() + ")");
        
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
    
    private boolean needsBootstrap() {
        return !SettingsData.isSystemConfigData();
    }
    
    // ===== STATE TRANSITIONS =====
    
    private void setupStateTransitions() {
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
    
    @Override
    protected void onOpened() {
        Log.logMsg("[SystemApplication] Terminal shown, determining which screen to show");
        
        if (lastScreenName != null && isAuthenticated()) {
            Log.logMsg("[SystemApplication] Restoring screen: " + lastScreenName);
            showScreen(lastScreenName);
        } else if (stateMachine.hasState(SETUP_NEEDED)) {
            Log.logMsg("[SystemApplication] Setup needed, showing system-setup");
            showScreen("system-setup");
        } else if (!isAuthenticated()) {
            Log.logMsg("[SystemApplication] Not authenticated, checking settings");
            stateMachine.addState(CHECKING_SETTINGS);
        } else if (isLocked()) {
            Log.logMsg("[SystemApplication] Locked, showing locked screen");
            showScreen("locked");
        } else {
            Log.logMsg("[SystemApplication] Showing main menu");
            showScreen("main-menu");
        }
    }
    
    @Override
    protected CompletableFuture<Void> onBeforeDetach() {
        // Clear authentication on detach for security
        Log.logMsg("[SystemApplication] Clearing authentication on detach");
        systemAccess = null;
        systemRuntime = null;
        stateMachine.removeState(AUTHENTICATING);
        stateMachine.addState(LOCKED);
        
        cancelAuthTimeout();
        
        return releasePasswordKeyboard()
            .thenCompose(v -> super.onBeforeDetach());
    }
    
    @Override
    protected CompletableFuture<Void> onBeforeClose() {
        if (isAuthenticated()) {
            stateMachine.addState(LOCKED);
        }
        cancelAuthTimeout();
        
        return releasePasswordKeyboard();
    }
    
    // ===== BOOTSTRAP CONFIGURATION =====
    
    private CompletableFuture<Void> loadBootstrapConfig() {
        if (!SettingsData.isSystemConfigData()) {
            return CompletableFuture.failedFuture(
                new IOException("Config does not exist"));
        }
        
        return SettingsData.loadSettingsMap()
            .thenAccept(map -> {
                NoteBytes keyboardBytes = map.get(ConfigKeys.PASSWORD_KEYBOARD_ID);
                NoteBytes socketBytes = map.get(ConfigKeys.IO_DAEMON_SOCKET_PATH);
                NoteBytes recoveryBytes = map.get(ConfigKeys.RECOVERY_MODE);
                NoteBytes reasonBytes = map.get(ConfigKeys.RECOVERY_REASON);
                
                passwordKeyboardId = keyboardBytes != null ? 
                    keyboardBytes.getAsString() : null;
                String ioDaemonSocketPath = socketBytes != null ? 
                    socketBytes.getAsString() : DEFAULT_IO_DAEMON_SOCKET_PATH;
                isInRecoveryMode = recoveryBytes != null ? 
                    recoveryBytes.getAsBoolean() : false;
                recoveryReason = reasonBytes != null ? 
                    reasonBytes.getAsString() : null;
                
                ioDaemonManager.setIODaemonSocketPath(ioDaemonSocketPath);

                if (isInRecoveryMode) {
                    Log.logMsg("[SystemApplication] RECOVERY MODE DETECTED: " + 
                        recoveryReason);
                }
                
                Log.logMsg("[SystemApplication] Bootstrap loaded: passwordKeyboard=" + 
                    passwordKeyboardId);
            });
    }
    
    private CompletableFuture<Void> saveBootstrapConfig() {
        NoteBytesMap map = new NoteBytesMap();
        
        if (passwordKeyboardId != null) {
            map.put(ConfigKeys.PASSWORD_KEYBOARD_ID, passwordKeyboardId);
        }
        map.put(ConfigKeys.IO_DAEMON_SOCKET_PATH, ioDaemonManager.getIODaemonSocketPath());
        
        if (isInRecoveryMode) {
            map.put(ConfigKeys.RECOVERY_MODE, isInRecoveryMode);
            if (recoveryReason != null) {
                map.put(ConfigKeys.RECOVERY_REASON, recoveryReason);
            }
        }
        
        return SettingsData.saveSystemConfig(map)
            .thenRun(() -> Log.logMsg("[SystemApplication] Bootstrap saved (recovery=" + 
                isInRecoveryMode + ")"));
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
    
    // ===== PASSWORD KEYBOARD MANAGEMENT =====
    
    private boolean needsPasswordKeyboardClaim() {
        return passwordKeyboardId != null && 
            !passwordKeyboardId.equals(getDefaultKeyboardId()) &&
            !isPasswordKeyboardClaimed();
    }
    
    private boolean needsPasswordKeyboardRelease() {
        return passwordKeyboardId != null &&
            !passwordKeyboardId.equals(getDefaultKeyboardId()) &&
            isPasswordKeyboardClaimed();
    }
    
    private boolean isPasswordKeyboardClaimed() {
        if (passwordKeyboardId == null) return false;
        if (!isTerminalAttached()) return false;
        if (!hasIODaemonSession()) return false;
        
        ClaimedDevice device = getIODaemonSession()
            .getClaimedDevice(passwordKeyboardId);
        return device != null && device.isActive();
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
            .thenCompose(ioDaemonPath -> connectToIODaemon(ioDaemonPath))
            .thenCompose(session -> session.discoverDevices())
            .thenCompose(devices -> {
                var deviceInfo = devices.stream()
                    .filter(d -> d.usbDevice().getDeviceId().equals(passwordKeyboardId))
                    .findFirst()
                    .orElse(null);
                
                if (deviceInfo == null) {
                    throw new RuntimeException("Password keyboard not found: " + 
                        passwordKeyboardId);
                }
                
                return claimDevice(passwordKeyboardId, "parsed");
            })
            .thenRun(() -> {
                Log.logMsg("[SystemApplication] Password keyboard claimed: " + 
                    passwordKeyboardId);
            })
            .exceptionally(ex -> {
                passwordKeyboardId = null;
                Log.logError("[SystemApplication] Password keyboard claim failed: " + 
                    ex.getMessage());
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
        
        return releaseDevice(passwordKeyboardId)
            .thenCompose(v -> {
                if (getIODaemonSession().getClaimedDevices().isEmpty()) {
                    return disconnectFromIODaemon();
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenRun(() -> Log.logMsg("[SystemApplication] Password keyboard released"))
            .exceptionally(ex -> {
                Log.logError("[SystemApplication] Release failed: " + ex.getMessage());
                return null;
            });
    }
    
    EventHandlerRegistry getPasswordEventHandlerRegistry() {
        if (!isTerminalAttached()) {
            throw new IllegalStateException("Cannot get event registry - no terminal attached");
        }
        
        if (passwordKeyboardId != null) {
            EventHandlerRegistry registry = getClaimedDeviceRegistry(passwordKeyboardId);
            if (registry != null) {
                return registry;
            }
        }
        return getEventHandlerRegistry();
    }
    
    // ===== AUTHENTICATION =====
    
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
                                .thenCompose(v -> waitForKeyPress())
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
                Log.logError("[SystemApplication] Post-auth cleanup error: " + 
                    ex.getMessage());
                showScreen("main-menu");
                return null;
            });
    }
    
    public CompletableFuture<Void> lock() {
        if (isVisible) {
            return close();
        } else {
            stateMachine.removeState(AUTHENTICATING);
            if (isAuthenticated()) {
                stateMachine.addState(LOCKED);
            }
            return CompletableFuture.completedFuture(null);
        }
    }
    
    // ===== RECOVERY MODE =====
    
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
    
    // ===== QUERIES =====
    
    public boolean isAuthenticated() {
        return systemAccess != null;
    }
    
    public boolean isLocked() {
        return stateMachine.hasState(LOCKED);
    }
    
    public boolean isInRecoveryMode() {
        return isInRecoveryMode;
    }
    
    public String getRecoveryReason() {
        return recoveryReason;
    }
    
    public String getPasswordKeyboardId() {
        return passwordKeyboardId;
    }
    
    public boolean usesSecureKeyboard() {
        return passwordKeyboardId != null;
    }
    
    public boolean usesSeparatePasswordKeyboard() {
        if (passwordKeyboardId == null) return false;
        String defaultId = getDefaultKeyboardId();
        return defaultId == null || !passwordKeyboardId.equals(defaultId);
    }
    
    RuntimeAccess getSystemAccess() {
        return systemAccess;
    }
    
    public ContextPath getSystemContextPath() {
        return systemContextPath;
    }
    
    public ContextPath getContextPath() {
        return systemContextPath;
    }
    
    public RenderingService getRenderingService() {
        return renderingService;
    }
}