package io.netnotes.engine.core.system;

import io.netnotes.engine.core.system.control.terminal.TerminalCommands;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderState;
import io.netnotes.engine.core.system.control.terminal.TerminalScreen;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.terminal.elements.TerminalTextBox;
import io.netnotes.engine.core.system.control.terminal.input.TerminalInputReader;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.daemon.DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities;
import io.netnotes.engine.io.daemon.IODaemonDetection;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.messaging.NoteMessaging.ItemTypes;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * SystemSetupScreen - REFACTORED for state-driven rendering
 * 
 * KEY CHANGES FROM BEFORE:
 * - All states are in state machine (not volatile fields)
 * - State transitions are declarative (not imperative)
 * - Every state change automatically invalidates
 * - No manual invalidate() calls needed
 * - Event handlers transition state, state drives rendering
 * 
 * State Flow:
 * WELCOME -> (key press) -> DETECTING
 * DETECTING -> (async detection) -> MAIN_MENU or ERROR
 * MAIN_MENU -> (menu selection) -> KEYBOARD_SELECTION / SOCKET_CONFIG / etc.
 * 
 * PATTERN BEFORE (imperative):
 * showWelcome()
 * waitForKey()
 * transitionTo(DETECTING)
 * invalidate() // manual
 * detectIODaemon()
 * 
 * PATTERN NOW (declarative):
 * STATE_WELCOME: show welcome, register key handler
 * Key handler: transitionTo(STATE_DETECTING) // auto-invalidates
 * STATE_DETECTING: show "detecting...", start async detection
 * Detection complete: transitionTo(STATE_MAIN_MENU) // auto-invalidates
 */
public class SystemSetupScreen extends TerminalScreen {
    
    // ===== SCREEN STATES (using state machine) =====
    private static final int STATE_WELCOME = 20;
    private static final int STATE_DETECTING = 21;
    private static final int STATE_MAIN_MENU = 22;
    private static final int STATE_KEYBOARD_SELECTION = 23;
    private static final int STATE_SOCKET_CONFIG = 24;
    private static final int STATE_ERROR = 25;
    
    // ===== SUB-COMPONENTS =====
    private final MenuNavigator menuNavigator;
    private final boolean isFirstRun;
    
    // ===== DETECTION RESULTS (updated by async operations) =====
    private volatile boolean ioDaemonAvailable = false;
    private volatile String socketPath = "/var/run/io-daemon.sock";
    private volatile List<DeviceDescriptorWithCapabilities> availableKeyboards = null;
    private volatile String errorMessage = null;
    
    // ===== INPUT READER (for socket config) =====
    private TerminalInputReader inputReader = null;
    
    public SystemSetupScreen(String id, SystemApplication systemApplication) {
        super(id, systemApplication);
        this.menuNavigator = new MenuNavigator("setupMenu", systemApplication);
        this.isFirstRun = !systemApplication.isAuthenticated();
        
        Log.logMsg("[SystemSetupScreen] created: " + id);
        
        // Set initial state
        if (!isFirstRun) {
            stateMachine.addState(STATE_DETECTING);
        } else {
            stateMachine.addState(STATE_WELCOME);
        }
    }
    
    // ===== STATE TRANSITIONS =====
    
    @Override
    protected void setupAdditionalStateTransitions() {
        // STATE_WELCOME: Show welcome screen, wait for key press
        stateMachine.onStateAdded(STATE_WELCOME, (old, now, bit) -> {
            Log.logMsg("[SystemSetupScreen] STATE_WELCOME - setting up key handler");
            
            // Register key handler that transitions to DETECTING
            registerKeyDownHandler(event -> {
                if (event instanceof KeyDownEvent) {
                    transitionTo(STATE_WELCOME, STATE_DETECTING);
                }
            });
        });
        
        // STATE_DETECTING: Show "Detecting..." and start async detection
        stateMachine.onStateAdded(STATE_DETECTING, (old, now, bit) -> {
            Log.logMsg("[SystemSetupScreen] STATE_DETECTING - starting detection");
            
            // Start async detection
            detectIODaemon();
        });
        
        // STATE_MAIN_MENU: Show main setup menu
        stateMachine.onStateAdded(STATE_MAIN_MENU, (old, now, bit) -> {
            Log.logMsg("[SystemSetupScreen] STATE_MAIN_MENU - showing menu");
            showMainSetupMenu();
        });
        
        // STATE_KEYBOARD_SELECTION: Show keyboard selection menu
        stateMachine.onStateAdded(STATE_KEYBOARD_SELECTION, (old, now, bit) -> {
            Log.logMsg("[SystemSetupScreen] STATE_KEYBOARD_SELECTION");
            showKeyboardSelectionMenu(true);
        });
        
        // STATE_SOCKET_CONFIG: Show socket path input
        stateMachine.onStateAdded(STATE_SOCKET_CONFIG, (old, now, bit) -> {
            Log.logMsg("[SystemSetupScreen] STATE_SOCKET_CONFIG");
            setupSocketConfigInput();
        });
        
        // STATE_ERROR: Show error message
        stateMachine.onStateAdded(STATE_ERROR, (old, now, bit) -> {
            Log.logMsg("[SystemSetupScreen] STATE_ERROR: " + errorMessage);
            
            // Register key handler to go back
            registerKeyDownHandler(event -> {
                if (event instanceof KeyDownEvent) {
                    systemApplication.goBack();
                }
            });
        });
    }
    
    // ===== RENDERING (PULL-BASED) =====
    
    @Override
    public TerminalRenderState getRenderState() {
        // Build UI based on current state
        // State machine guarantees only one of these states is active
        
        if (stateMachine.hasState(STATE_WELCOME)) {
            return buildWelcomeState();
        }
        
        if (stateMachine.hasState(STATE_DETECTING)) {
            return buildDetectingState();
        }
        
        if (stateMachine.hasState(STATE_MAIN_MENU)) {
            return buildMainMenuState();
        }
        
        if (stateMachine.hasState(STATE_KEYBOARD_SELECTION)) {
            return buildKeyboardSelectionState();
        }
        
        if (stateMachine.hasState(STATE_SOCKET_CONFIG)) {
            return buildSocketConfigState();
        }
        
        if (stateMachine.hasState(STATE_ERROR)) {
            return buildErrorState();
        }
        
        // Fallback: empty state
        return TerminalRenderState.builder().build();
    }
    
    /**
     * Build welcome screen state
     */
    private TerminalRenderState buildWelcomeState() {
        TerminalTextBox welcomeBox = TerminalTextBox.builder()
            .position(0, 2)
            .title("Welcome to Netnotes", TerminalTextBox.TitlePlacement.INSIDE_CENTER)
            .style(BoxStyle.DOUBLE)
            .titleStyle(TextStyle.BOLD)
            .contentAlignment(TerminalTextBox.ContentAlignment.CENTER)
            .build();
        
        int msgRow = Math.max(allocatedHeight / 2 + 2, 8);
        String msg = "Initial Setup";
        int msgCol = Math.max(0, allocatedWidth / 2 - msg.length() / 2);
        int lineCol = Math.max(0, msgCol - 1);
        
        int promptRow = allocatedHeight / 2 + 5;
        String prompt = TerminalCommands.PRESS_ANY_KEY;
        int promptCol = Math.max(0, allocatedWidth / 2 - prompt.length() / 2);
        
        return TerminalRenderState.builder()
            .add((term)-> term.clear())
            .add(welcomeBox.asRenderElement())
            .add((term) -> {
                term.printAt(msgRow, msgCol, msg, TextStyle.INFO);
                term.drawHLine(msgRow + 1, lineCol, msg.length() + 2);
            })
            .add((term) -> {
                term.printAt(promptRow, promptCol, prompt, TextStyle.INFO);
            })
            .build();
    }
    
    /**
     * Build detecting screen state
     */
    private TerminalRenderState buildDetectingState() {
        int row = allocatedHeight / 2;
        int col = Math.max(0, allocatedWidth / 2 - 15);
        
        return TerminalRenderState.builder()
            .add((term) -> {
                term.printAt(row, col, "Detecting IODaemon...", TextStyle.INFO);
            })
            .build();
    }
    
    /**
     * Build main menu state
     */
    private TerminalRenderState buildMainMenuState() {
        // MenuNavigator handles its own rendering
        return TerminalRenderState.builder()
            .add(batch -> batch.clear())
            .add(menuNavigator.asRenderElement())
            .build();
    }
    
    /**
     * Build keyboard selection state
     */
    private TerminalRenderState buildKeyboardSelectionState() {
        // MenuNavigator handles its own rendering
        return TerminalRenderState.builder()
            .add(batch -> batch.clear())
            .add(menuNavigator.asRenderElement())
            .build();
    }
    
    /**
     * Build socket config state
     */
    private TerminalRenderState buildSocketConfigState() {
        int titleRow = 1;
        int pathRow = 3;
        int promptRow = 5;
        int inputPromptRow = 7;
        
        return TerminalRenderState.builder()
            .add((term) -> {
                term.printAt(titleRow, 5, "Socket Path Configuration", TextStyle.BOLD);
            })
            .add((term) -> {
                term.printAt(pathRow, 5, "Current: " + socketPath, TextStyle.NORMAL);
            })
            .add((term) -> {
                term.printAt(promptRow, 5, 
                    "Enter new socket path (or press ESC to cancel):", 
                    TextStyle.NORMAL);
            })
            .add((term) -> {
                term.printAt(inputPromptRow, 5, "> ", TextStyle.NORMAL);
            })
            .build();
    }
    
    /**
     * Build error state
     */
    private TerminalRenderState buildErrorState() {
        String errorText = "Setup error: " + 
            (errorMessage != null ? errorMessage : "Unknown error");
        
        int errorRow = allocatedHeight / 2;
        int promptRow = errorRow + 2;
        
        return TerminalRenderState.builder()
            .add((term) -> {
                term.printAt(errorRow, 5, errorText, TextStyle.ERROR);
                term.printAt(promptRow, 5, "Press any key to return...", 
                    TextStyle.NORMAL);
            })
            .build();
    }
    
    // ===== LIFECYCLE =====
    
    @Override
    public CompletableFuture<Void> onShow() {
        Log.logMsg("[SystemSetupScreen] Showing setup");
        
        // Make this screen active (sets renderable + invalidates)
        return super.onShow();
        
        // State transitions handle the rest:
        // - If STATE_WELCOME: key handler registered, waiting for press
        // - If STATE_DETECTING: detection already started
    }
    
    @Override
    public void onHide() {
        menuNavigator.cleanup();
        if (inputReader != null) {
            inputReader.close();
            inputReader = null;
        }
        super.onHide();
    }
    
    // ===== IODaemon DETECTION (ASYNC) =====
    
    private CompletableFuture<Void> detectIODaemon() {
        return systemApplication.getIoDaemonManager().detect()
            .thenCompose(detectionResult -> {
                ioDaemonAvailable = detectionResult.isAvailable();
                
                if (detectionResult.isFullyOperational()) {
                    Log.logMsg("[SystemSetupScreen] IODaemon fully operational");
                    socketPath = detectionResult.socketPath;
                    
                    return systemApplication.getIoDaemonManager().ensureAvailable()
                        .thenCompose(ioDaemonPath -> discoverKeyboards());
                        
                } else if (detectionResult.binaryExists && !detectionResult.processRunning) {
                    Log.logMsg("[SystemSetupScreen] IODaemon installed but not running");
                    socketPath = detectionResult.socketPath;
                    
                    return systemApplication.getIoDaemonManager().ensureAvailable()
                        .thenCompose(ioDaemonPath -> discoverKeyboards())
                        .exceptionally(ex -> {
                            Log.logError("[SystemSetupScreen] Failed to start IODaemon: " + 
                                ex.getMessage());
                            ioDaemonAvailable = false;
                            return null;
                        });
                        
                } else {
                    Log.logMsg("[SystemSetupScreen] IODaemon not installed");
                    ioDaemonAvailable = false;
                    
                    IODaemonDetection.InstallationPaths paths = 
                        systemApplication.getIoDaemonManager().getInstallationPaths();
                    if (paths != null) {
                        socketPath = paths.socketPath;
                    }
                    
                    return CompletableFuture.completedFuture(null);
                }
            })
            .thenRun(() -> {
                // Detection complete - transition to main menu
                // This automatically invalidates and triggers render
                transitionTo(STATE_DETECTING, STATE_MAIN_MENU);
            })
            .exceptionally(ex -> {
                Log.logError("[SystemSetupScreen] IODaemon detection failed: " + 
                    ex.getMessage());
                
                errorMessage = ex.getMessage() != null ? ex.getMessage() : 
                    ex.getClass().getSimpleName();
                
                // Transition to error state
                transitionTo(STATE_DETECTING, STATE_ERROR);
                return null;
            });
    }
    
    private CompletableFuture<Void> discoverKeyboards() {
        return systemApplication.connectToIODaemon()
            .thenCompose(session -> session.discoverDevices())
            .thenAccept(devices -> {
                availableKeyboards = devices.stream()
                    .filter(d -> d.usbDevice().getDeviceType().equals(ItemTypes.KEYBOARD))
                    .toList();
                
                Log.logMsg("[SystemSetupScreen] Found " + availableKeyboards.size() + 
                    " keyboards");
            })
            .exceptionally(ex -> {
                Log.logError("[SystemSetupScreen] Keyboard discovery failed: " + 
                    ex.getMessage());
                availableKeyboards = List.of();
                return null;
            });
    }
    
    // ===== MENU SETUP (state-triggered) =====
    
    private void showMainSetupMenu() {
        ContextPath menuPath = systemApplication.getContextPath().append("system-setup");
        String description = buildSetupDescription();
        
        MenuContext menu = new MenuContext(
            menuPath,
            "System Setup",
            description,
            null
        );
        
        if (ioDaemonAvailable && availableKeyboards != null && 
            !availableKeyboards.isEmpty()) {
            menu.addItem("select-password-keyboard", 
                "Select Password Keyboard",
                () -> transitionTo(STATE_MAIN_MENU, STATE_KEYBOARD_SELECTION));
            
            menu.addItem("use-gui-only",
                "Use UI Keyboard Only (No Secure Input)",
                this::configureGUIOnly);
            
        } else {
            menu.addItem("install-info",
                "How to Install IODaemon",
                this::showInstallationInfo);
            
            menu.addItem("use-gui-only",
                "Continue with UI Keyboard Only",
                this::configureGUIOnly);
            
            menu.addItem("retry-detection",
                "Retry IODaemon Detection",
                this::retryDetection);
        }
        
        menu.addSeparator("Advanced");
        
        menu.addItem("socket-path",
            "Configure Socket Path: " + socketPath,
            () -> transitionTo(STATE_MAIN_MENU, STATE_SOCKET_CONFIG));
        
        if (isFirstRun) {
            menu.addItem("continue",
                "Continue to Password Setup →",
                this::completeSetup);
        } else {
            menu.addItem("back",
                "Back to Settings",
                () -> systemApplication.goBack());
        }
        
        menuNavigator.showMenu(menu);
        // No manual invalidate needed - transitionTo already did it
    }
    
    private String buildSetupDescription() {
        StringBuilder desc = new StringBuilder();
        
        if (ioDaemonAvailable) {
            desc.append("✓ IODaemon is available\n");
            
            if (availableKeyboards != null && !availableKeyboards.isEmpty()) {
                desc.append("✓ Found ").append(availableKeyboards.size())
                    .append(" USB keyboard(s)\n\n");
                desc.append("Secure input provides hardware-level protection\n");
                desc.append("for password entry via direct USB access.\n");
            } else {
                desc.append("⚠ No USB keyboards detected\n\n");
                desc.append("You can use GUI keyboard or connect a USB keyboard.\n");
            }
        } else {
            desc.append("✗ IODaemon not available\n\n");
            
            IODaemonDetection.InstallationPaths paths = 
                systemApplication.getIoDaemonManager().getInstallationPaths();
            
            if (paths != null) {
                desc.append("Expected location: ").append(paths.binaryPath).append("\n");
                desc.append("Socket path: ").append(paths.socketPath).append("\n\n");
            }
            
            desc.append("Install IODaemon for secure password entry via USB keyboards,\n");
            desc.append("or continue with GUI keyboard only.\n");
        }
        
        return desc.toString();
    }
    
    private void showKeyboardSelectionMenu(boolean forPassword) {
        // State transition already happened
        
        ContextPath menuPath = systemApplication.getContextPath().append("keyboard-selection");
        
        String title = forPassword ? "Select Password Keyboard" : "Select Default Keyboard";
        String description = "Choose which USB keyboard to use for " + 
            (forPassword ? "password entry" : "general input");
        
        MenuContext menu = new MenuContext(menuPath, title, description, null);
        
        if (availableKeyboards == null || availableKeyboards.isEmpty()) {
            menu.addInfoItem("no-keyboards", "No keyboards detected");
            menu.addItem("back", "Back", 
                () -> transitionTo(STATE_KEYBOARD_SELECTION, STATE_MAIN_MENU));
        } else {
            for (var device : availableKeyboards) {
                String deviceId = device.usbDevice().getDeviceId();
                String manufacturer = device.usbDevice().manufacturer;
                String product = device.usbDevice().product;
                
                String displayName = product != null ? product : "USB Keyboard";
                if (manufacturer != null && !manufacturer.isEmpty()) {
                    displayName = manufacturer + " " + displayName;
                }
                
                String badge = device.claimed() ? "IN USE" : null;
                
                menu.addItem(deviceId, displayName, badge, () -> {
                    selectKeyboard(deviceId, forPassword);
                });
            }
            
            menu.addSeparator("");
            menu.addItem("refresh", "Refresh Device List", this::refreshKeyboards);
            menu.addItem("back", "Back", 
                () -> transitionTo(STATE_KEYBOARD_SELECTION, STATE_MAIN_MENU));
        }
        
        menuNavigator.showMenu(menu);
        // No manual invalidate needed
    }
    
    private void setupSocketConfigInput() {
        // State transition already happened
        
        inputReader = new TerminalInputReader(systemApplication, 7, 7, 60);
        inputReader.setText(socketPath);
        
        inputReader.setOnComplete(this::handleSocketPathComplete);
        inputReader.setOnEscape(v -> {
            if (inputReader != null) {
                inputReader.close();
                inputReader = null;
            }
            transitionTo(STATE_SOCKET_CONFIG, STATE_MAIN_MENU);
        });
    }
    
    // ===== MENU ACTIONS =====
    
    private void selectKeyboard(String deviceId, boolean forPassword) {
        Log.logMsg("[SystemSetupScreen] Selected keyboard: " + deviceId);
        
        if (forPassword) {
            systemApplication.completeBootstrap(deviceId)
                .thenRun(() -> {
                    if (isFirstRun) {
                        completeSetup();
                    } else {
                        transitionTo(STATE_KEYBOARD_SELECTION, STATE_MAIN_MENU);
                    }
                });
        }
    }
    
    private void refreshKeyboards() {
        discoverKeyboards()
            .thenRun(() -> {
                // Stay in keyboard selection, but refresh menu
                showKeyboardSelectionMenu(true);
                // transitionTo already invalidated
            })
            .exceptionally(ex -> {
                errorMessage = "Failed to refresh: " + ex.getMessage();
                transitionTo(STATE_KEYBOARD_SELECTION, STATE_ERROR);
                return null;
            });
    }
    
    private void configureGUIOnly() {
        systemApplication.completeBootstrap(null)
            .thenRun(() -> {
                if (isFirstRun) {
                    completeSetup();
                } else {
                    systemApplication.goBack();
                }
            });
    }
    
    private void handleSocketPathComplete(String newPath) {
        if (inputReader != null) {
            inputReader.close();
            inputReader = null;
        }
        
        if (newPath == null || newPath.trim().isEmpty()) {
            transitionTo(STATE_SOCKET_CONFIG, STATE_MAIN_MENU);
            return;
        }
        
        if (!newPath.startsWith("/")) {
            errorMessage = "Socket path must be absolute (start with /)";
            transitionTo(STATE_SOCKET_CONFIG, STATE_ERROR);
            return;
        }
        
        String oldPath = socketPath;
        socketPath = newPath.trim();
        
        // Update and test connection
        systemApplication.completeBootstrap(systemApplication.getPasswordKeyboardId())
            .thenCompose(v -> systemApplication.getIoDaemonManager()
                .reconfigureSocketPath(socketPath))
            .thenCompose(v -> systemApplication.getIoDaemonManager().detect())
            .thenAccept(result -> {
                if (result.isFullyOperational()) {
                    ioDaemonAvailable = true;
                    discoverKeyboards();
                }
            })
            .thenRun(() -> {
                transitionTo(STATE_SOCKET_CONFIG, STATE_MAIN_MENU);
            })
            .exceptionally(ex -> {
                socketPath = oldPath;
                systemApplication.completeBootstrap(
                    systemApplication.getPasswordKeyboardId());
                
                errorMessage = "Failed to apply new path: " + ex.getMessage();
                transitionTo(STATE_SOCKET_CONFIG, STATE_ERROR);
                return null;
            });
    }
    
    private void showInstallationInfo() {
        // TODO: Show installation info screen
        transitionTo(STATE_MAIN_MENU, STATE_MAIN_MENU);
    }
    
    private void retryDetection() {
        transitionTo(STATE_MAIN_MENU, STATE_DETECTING);
        // STATE_DETECTING transition handler will start detection
    }
    
    private void completeSetup() {
        Log.logMsg("[SystemSetupScreen] Setup complete");
        
        if (isFirstRun) {
            systemApplication.getStateMachine().addState(
                SystemApplication.CHECKING_SETTINGS);
        } else {
            systemApplication.goBack();
        }
    }
}