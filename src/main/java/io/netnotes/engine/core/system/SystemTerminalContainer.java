package io.netnotes.engine.core.system;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.control.containers.ContainerId;
import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.process.ProcessRegistryInterface;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;
/**
 * SystemTerminalContainer - THE system terminal
 * 
 * Singular, stateful terminal that manages:
 * - Authentication lifecycle
 * - Screen management
 * - SystemRuntime creation and access
 * 
 * Flow:
 * 1. Terminal opens → check if settings exist
 * 2a. First run → create password → create SettingsData → create SystemRuntime
 * 2b. Existing → verify password → load SettingsData → create SystemRuntime
 * 3. SystemRuntime created → authenticated → show main menu
 * 4. User can close terminal (SystemRuntime persists in memory)
 * 5. Reopen → authenticated, go straight to menu
 * 6. Lock → requires re-auth
 */
public class SystemTerminalContainer extends TerminalContainerHandle {
    
    private final BitFlagStateMachine state;
    private final InputDevice keyboard;
    private final ProcessRegistryInterface registry;
    private final ContextPath sessionPath;
    
    @FunctionalInterface
    public interface ScreenExecutor {
        void execute(TerminalScreen screen);
    }
    

    private final Map<String, ScreenFactory> screenFactories = new HashMap<>();
    
    // Authentication & system state
    private SystemRuntime systemRuntime = null;
    private RuntimeAccess systemAccess = null;
    private volatile boolean isAuthenticated = false;
    private volatile boolean isVisible = false;
    
    // Current screen
    private TerminalScreen currentScreen;
    public static final long CLOSED = 1L << 0;
    public static final long OPENING = 1L << 1;
    public static final long CHECKING_SETTINGS = 1L << 2;
    public static final long FIRST_RUN = 1L << 3;
    public static final long AUTHENTICATING = 1L << 4;
    public static final long AUTHENTICATED = 1L << 5;
    public static final long LOCKED = 1L << 6;
    public static final long SHOWING_SCREEN = 1L << 7;
    public static final long ERROR = 1L << 8;
    public static final long FAILED_SETTINGS = 1L << 9;
    
    public SystemTerminalContainer(
        ContainerId containerId,
        String name,
        ContextPath containerServicePath,
        InputDevice keyboard,
        ProcessRegistryInterface registry,
        ContextPath sessionPath
    ) {
        super(containerId, name, containerServicePath);
        this.keyboard = keyboard;
        this.registry = registry;
        this.sessionPath = sessionPath;
        this.state = new BitFlagStateMachine("system-terminal");
        
        setupStateTransitions();
        registerDefaultScreens();
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(CLOSED, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] Terminal closed");
            isVisible = false;
        });
        
        state.onStateAdded(OPENING, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] Opening terminal...");
            isVisible = true;
        });
        
        state.onStateAdded(CHECKING_SETTINGS, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] Checking if settings exist...");
            checkSettingsExist()
                .thenAccept(exists -> {
                    state.removeState(CHECKING_SETTINGS);
                    if (exists) {
                        state.addState(AUTHENTICATING);
                    } else {
                        if(!SettingsData.isIdDataFile()){
                            state.addState(FIRST_RUN);
                        }else{
                            state.addState(FAILED_SETTINGS);
                        }
                    }
                })
                .exceptionally(ex ->{
                    state.removeState(CHECKING_SETTINGS);
                    state.addState(FAILED_SETTINGS);
                    return null;
                });
        });
        
        state.onStateAdded(FIRST_RUN, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] First run - creating password");
            showScreen("first-run-password");
        });
        
        state.onStateAdded(AUTHENTICATING, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] Authenticating user...");
            showScreen("login");
        });
        
        state.onStateAdded(AUTHENTICATED, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] User authenticated");
            isAuthenticated = true;
            // Check for recovery needs, then show main menu
            checkRecovery().thenRun(() -> showScreen("main-menu"));
        });
        
        state.onStateAdded(LOCKED, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] Terminal locked");
            isAuthenticated = false;
            showScreen("locked");
        });

        state.onStateAdded(FAILED_SETTINGS, (old, now, bit) -> {
            Log.logMsg("[SystemTerminal] Settings error - troubleshooting settings");
            showScreen("settings-recovery");
        });
    }
    
    @FunctionalInterface
    public interface ScreenFactory {
        TerminalScreen create(String id, SystemTerminalContainer terminal, InputDevice keyboard);
    }

    public void registerScreen(String id, ScreenFactory factory) {
        screenFactories.put(id, factory);
    }


    private void registerDefaultScreens() {
        // First run password creation
        registerScreen("first-run-password",  FirstRunPasswordScreen::new);
        registerScreen("login",               LoginScreen::new);
        registerScreen("locked",              LockedScreen::new);
        registerScreen("main-menu",           MainMenuScreen::new);
        registerScreen("settings",            SettingsScreen::new);
        registerScreen("bootstrap",           BootstrapConfigScreen::new);
        registerScreen("node-manager",        NodeManagerScreen::new);
        registerScreen("settings-recovery",   FailedSettingsScreen::new);
    }
    
    // ===== LIFECYCLE =====
    
    /**
     * Open terminal - shows and handles authentication
     */
    public CompletableFuture<Void> open() {
        if (isVisible) {
            return CompletableFuture.completedFuture(null);
        }
        
        state.removeState(CLOSED);
        state.addState(OPENING);
        
        return show()
            .thenCompose(v -> {
                state.removeState(OPENING);
                
                if (systemRuntime != null && isAuthenticated) {
                    // Already have runtime and authenticated
                    state.addState(AUTHENTICATED);
                    return CompletableFuture.completedFuture(null);
                } else if (systemRuntime != null && !isAuthenticated) {
                    // Have runtime but locked - need re-auth
                    state.addState(LOCKED);
                    return CompletableFuture.completedFuture(null);
                } else {
                    // No runtime - check if first run
                    state.addState(CHECKING_SETTINGS);
                    return CompletableFuture.completedFuture(null);
                }
            });
    }
    
    /**
     * Close terminal - hides but keeps state
     */
    public CompletableFuture<Void> close() {
        if (!isVisible) {
            return CompletableFuture.completedFuture(null);
        }
        
        if (currentScreen != null) {
            currentScreen.onHide();
        }
        
        return hide()
            .thenRun(() -> {
                state.removeState(OPENING);
                state.removeState(SHOWING_SCREEN);
                state.addState(CLOSED);
            });
    }
    
    /**
     * Lock terminal - requires re-authentication
     */
    public CompletableFuture<Void> lock() {
        state.removeState(AUTHENTICATED);
        state.addState(LOCKED);
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Unlock terminal after successful re-auth
     */
    public CompletableFuture<Void> unlock() {
        state.removeState(LOCKED);
        state.removeState(AUTHENTICATING);
        state.addState(AUTHENTICATED);
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== AUTHENTICATION & SYSTEM CREATION =====
    
    private CompletableFuture<Boolean> checkSettingsExist() {
    
        if(SettingsData.isSettingsData())
        {
            return SettingsData.loadSettingsMap().thenApply(map -> map != null);
        }
        return CompletableFuture.completedFuture(false);
           
    }
    
    /**
     * Create new system (first run)
     * Called by FirstRunPasswordScreen after password creation
     */
    public CompletableFuture<Void> createNewSystem(NoteBytesEphemeral password) {
        return SettingsData.createSettings(password)
            .thenApply(settingsData -> {
                password.close();
                
                // Create empty access object
                RuntimeAccess access = new RuntimeAccess();
                
                // Create SystemRuntime - it fills the access object
                SystemRuntime runtime = new SystemRuntime(
                    sessionPath,
                    settingsData,
                    registry,
                    access
                );
                
                // Store both
                this.systemRuntime = runtime;
                this.systemAccess = access;
                
                // Mark authenticated
                state.removeState(FIRST_RUN);
                state.addState(AUTHENTICATED);
                
                return null;
            });
    }

    CompletableFuture<Void> recoverSystem(SettingsData settingsData) {
        if( state.hasState(SystemTerminalContainer.FAILED_SETTINGS)){
            state.removeState(FAILED_SETTINGS);
      
            return CompletableFuture.runAsync(()->{
                    // Create empty access object
                    RuntimeAccess access = new RuntimeAccess();
                    
                    // Create SystemRuntime - it fills the access object
                    SystemRuntime runtime = new SystemRuntime(
                        sessionPath,
                        settingsData,
                        registry,
                        access
                    );
                    
                    // Store both
                    this.systemRuntime = runtime;
                    this.systemAccess = access;
                    
                    state.addState(AUTHENTICATED);
        
                });
        }else{
            throw new SecurityException("System recovery accessed when not in recovery mode");
        }
    }
    
    /**
     * Verify password and load system (existing system)
     * Called by LoginScreen after password entry
     */
    public CompletableFuture<Boolean> verifyAndLoadSystem(NoteBytesEphemeral password) {
        Log.logMsg("[SystemTerminal] Verifying password and loading system");
        
        // Load settingsMap (ephemeral)
        return SettingsData.loadSettingsMap()
            .thenCompose(settingsMap -> {
                Log.logMsg("[SystemTerminal] Settings map loaded, verifying");
                
                // Verify password
                return SettingsData.verifyPassword(password, settingsMap)
                    .thenCompose(valid -> {
                        if (valid) {
                            Log.logMsg("[SystemTerminal] Password valid, loading");
                            
                            // Load SettingsData
                            return SettingsData.loadSettingsData(password, settingsMap)
                                .thenApply(settingsData -> {
                                    Log.logMsg("[SystemTerminal] Creating SystemRuntime");
                                    
                                    RuntimeAccess access = new RuntimeAccess();
                                    
                                    // Create SystemRuntime
                                    SystemRuntime runtime = new SystemRuntime(
                                        sessionPath,
                                        settingsData,
                                        registry,
                                        access
                                    );
                                    
                                    // Store both
                                    this.systemRuntime = runtime;
                                    this.systemAccess = access;
                                    
                                    // Mark authenticated
                                    state.removeState(AUTHENTICATING);
                                    state.addState(AUTHENTICATED);
                                    
                                    return true;
                                });
                        } else {
                            Log.logMsg("[SystemTerminal] Password invalid");
                            return CompletableFuture.completedFuture(false);
                        }
                    });
            });
    }
    
    /**
     * Verify password for unlock (system already loaded)
     * Called by LockedScreen
     */
    public CompletableFuture<Boolean> verifyPasswordForUnlock(NoteBytesEphemeral password) {
        if (systemAccess == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        // Use RuntimeAccess for verification (doesn't hit disk)
        return systemAccess.verifyPassword(password)
            .thenApply(valid -> {
                if (valid) {
                    unlock();
                }
                return valid;
            });
    }
    
    /**
     * Internal password verification for initial authentication
     * Uses SettingsData when SystemRuntime doesn't exist yet
     */
    CompletableFuture<Boolean> verifyPasswordInternal(NoteBytesEphemeral password) {
        if (systemAccess != null) {
            // Use RuntimeAccess if available
            return systemAccess.verifyPassword(password);
        } else {
            // Fall back to SettingsData verification
            return SettingsData.loadSettingsMap()
                .thenCompose(settingsMap -> 
                    SettingsData.verifyPassword(password, settingsMap));
        }
    }
    
    /**
     * Check for recovery needs
     */
    private CompletableFuture<Void> checkRecovery() {
        // TODO: Implement recovery check
        // For now, just proceed
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== SCREEN MANAGEMENT =====

 
    public CompletableFuture<Void> showScreen( TerminalScreen screen) {
        screen.setTerminal(this);

        // Hide current screen
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
                new IllegalArgumentException("Screen not found: " + screenName)
            );
        }
        TerminalScreen screen = screenFactory.create(screenName, this, keyboard);
        // Show new screen
        return showScreen(screen);
    }
    
    public TerminalScreen getCurrentScreen() {
        return currentScreen;
    }
    
    // ===== MESSAGE HANDLING =====
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        NoteBytesMap msg = packet.getPayload().getAsNoteBytesMap();
        String cmd = msg.get(Keys.CMD) != null ? msg.get(Keys.CMD).getAsString() : null;
        
        if ("container_closed".equals(cmd)) {
            return close();
        } else if ("container_resized".equals(cmd)) {
            int rows = msg.get("rows").getAsInt();
            int cols = msg.get("cols").getAsInt();
            setDimensions(rows, cols);
            
            if (currentScreen != null) {
                return currentScreen.onResize(rows, cols);
            }
        }
        
        // Forward to current screen
        if (currentScreen != null) {
            return currentScreen.handleMessage(packet);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== GETTERS =====
    
    public boolean isAuthenticated() {
        return isAuthenticated;
    }
    
    public boolean isLocked() {
        return state.hasState(LOCKED);
    }
    
    public boolean isVisible() {
        return isVisible;
    }
    
    public BitFlagStateMachine getState() {
        return state;
    }
    
    public SystemRuntime getSystemRuntime() {
        return systemRuntime;
    }
    
    RuntimeAccess getSystemAccess() {
        return systemAccess;
    }
    
    public InputDevice getKeyboard() {
        return keyboard;
    }
    
    public ProcessRegistryInterface getRegistry() {
        return registry;
    }
    
    public ContextPath getSessionPath() {
        return sessionPath;
    }
    
    // ===== SCREEN NAVIGATION =====
    
    public CompletableFuture<Void> goBack() {
        if (currentScreen != null && currentScreen.getParent() != null) {
            return showScreen(currentScreen.getParent().getName());
        }
        return showScreen("main-menu");
    }
    
    public CompletableFuture<Void> showMainMenu() {
        return showScreen("main-menu");
    }
    
    /**
     * Helper to spawn password process as child
     */
    CompletableFuture<ContextPath> spawnPasswordProcess(
        io.netnotes.engine.io.process.FlowProcess process
    ) {
        ContextPath path = registry.registerChild(sessionPath, process);
        
        return registry.startProcess(path)
                .thenApply(v -> path);
    }




}

