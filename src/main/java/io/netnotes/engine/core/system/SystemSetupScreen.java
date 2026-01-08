package io.netnotes.engine.core.system;

import io.netnotes.engine.core.system.control.terminal.TerminalCommands;
import io.netnotes.engine.core.system.control.terminal.TerminalRenderState;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.terminal.elements.TerminalTextBox;
import io.netnotes.engine.core.system.control.terminal.input.TerminalInputReader;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.daemon.DiscoveredDeviceRegistry.DeviceDescriptorWithCapabilities;
import io.netnotes.engine.io.daemon.IODaemonDetection;
import io.netnotes.engine.messaging.NoteMessaging.ItemTypes;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * SystemSetupScreen - REFACTORED for pull-based rendering
 * 
 * KEY CHANGES:
 * - Implements Renderable via TerminalScreen
 * - States stored as fields (not in state machine)
 * - getRenderState() builds UI from current state
 * - invalidate() triggers redraws
 * - MenuNavigator handles menu rendering (already pull-based)
 * - TerminalInputReader handles input rendering
 * 
 * State Flow:
 * WELCOME -> DETECTING -> MAIN_MENU -> KEYBOARD_SELECTION / SOCKET_CONFIG
 */
public class SystemSetupScreen extends TerminalScreen {
    
    private final MenuNavigator menuNavigator;
    private final boolean isFirstRun;
    
    // Detection results (mutable state)
    private volatile boolean ioDaemonAvailable = false;
    private volatile String socketPath = "/var/run/io-daemon.sock";
    private volatile List<DeviceDescriptorWithCapabilities> availableKeyboards = null;
    
    // Current state (mutable)
    private enum SetupState {
        WELCOME,
        DETECTING,
        MAIN_MENU,
        KEYBOARD_SELECTION,
        SOCKET_CONFIG,
        ERROR
    }
    
    private volatile SetupState currentState = SetupState.WELCOME;
    private volatile String errorMessage = null;
    
    // For socket config input
    private TerminalInputReader inputReader = null;
    
    public SystemSetupScreen(String id, SystemApplication systemApplication) {
        super(id, systemApplication);
        this.menuNavigator = new MenuNavigator(systemApplication.getTerminal()).withParent(this);
        this.isFirstRun = !systemApplication.isAuthenticated();
        Log.logMsg("[SystemSetupScreen] created: " + id + ": terminal: " + systemApplication.getTerminal().getId());
        if (!isFirstRun) {
            currentState = SetupState.DETECTING;
        }
    }
    
    // ===== RENDERABLE INTERFACE =====
    
    @Override
    public TerminalRenderState getRenderState() {
        // Build UI based on current state
        return switch (currentState) {
            case WELCOME -> buildWelcomeState();
            case DETECTING -> buildDetectingState();
            case MAIN_MENU -> buildMainMenuState();
            case KEYBOARD_SELECTION -> buildKeyboardSelectionState();
            case SOCKET_CONFIG -> buildSocketConfigState();
            case ERROR -> buildErrorState();
        };
    }
    
    /**
     * Build welcome screen state
     */
    private TerminalRenderState buildWelcomeState() {
        TerminalTextBox welcomeBox = TerminalTextBox.builder()
            .position(0, 2)
            .size(systemApplication.getTerminal().getWidth() - 4, 5)
            .title("Welcome to Netnotes", TerminalTextBox.TitlePlacement.INSIDE_CENTER)
            .style(BoxStyle.DOUBLE)
            .titleStyle(TextStyle.BOLD)
            .contentAlignment(TerminalTextBox.ContentAlignment.CENTER)
            .build();
        
        int msgRow = Math.max(systemApplication.getTerminal().getRows() / 2 + 2, 8);
        String msg = "Initial Setup";
        int msgCol = Math.max(0, systemApplication.getTerminal().getCols() / 2 - msg.length() / 2);
        int lineCol = Math.max(0, msgCol - 1);
        
        int promptRow = systemApplication.getTerminal().getRows() / 2 + 5;
        String prompt = TerminalCommands.PRESS_ANY_KEY;
        int promptCol = Math.max(0, systemApplication.getTerminal().getCols() / 2 - prompt.length() / 2);
        
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
        int row = systemApplication.getTerminal().getRows() / 2;
        int col = Math.max(0, systemApplication.getTerminal().getCols() / 2 - 15);
        
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
        // Return empty state - MenuNavigator is active renderable
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
        // TerminalInputReader handles its own rendering
        // But we need to show the UI around it
        
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
            // InputReader renders itself at its configured position
            .build();
    }
    
    /**
     * Build error state
     */
    private TerminalRenderState buildErrorState() {
        String errorText = "Setup error: " + 
            (errorMessage != null ? errorMessage : "Unknown error");
        
        int errorRow = systemApplication.getTerminal().getRows() / 2;
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
        
        // Make this screen active
        super.onShow();
        
        // Start the flow
        if (currentState == SetupState.WELCOME) {
            // Wait for keypress, then move to detecting
            return systemApplication.getTerminal().waitForKeyPress()
                .thenRun(() -> {
                    currentState = SetupState.DETECTING;
                    invalidate();
                    detectIODaemon();
                });
        } else {
            // Skip welcome, go straight to detecting
            currentState = SetupState.DETECTING;
            invalidate();
            return detectIODaemon();
        }
    }
    
    @Override
    public void onHide() {
        menuNavigator.cleanup();
        if (inputReader != null) {
            inputReader.close();
            inputReader = null;
        }
    }
    
    // ===== IODaemon DETECTION =====
    
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
                // Detection complete - show main menu
                currentState = SetupState.MAIN_MENU;
                showMainSetupMenu();
            })
            .exceptionally(ex -> {
                Log.logError("[SystemSetupScreen] IODaemon detection failed: " + 
                    ex.getMessage());
                
                // Extract root cause
                Throwable cause = ex;
                while (cause instanceof CompletionException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                
                errorMessage = cause.getMessage();
                if (errorMessage == null || errorMessage.isEmpty()) {
                    errorMessage = cause.getClass().getSimpleName();
                }
                
                currentState = SetupState.ERROR;
                invalidate();
                
                // Wait for keypress, then go back
                systemApplication.getTerminal().waitForKeyPress()
                    .thenRun(() -> systemApplication.goBack());
                
                return null;
            });
    }
    
    private CompletableFuture<Void> discoverKeyboards() {
        return systemApplication.getTerminal().connectToIODaemon()
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
    
    // ===== MAIN SETUP MENU =====
    
    private void showMainSetupMenu() {
        currentState = SetupState.MAIN_MENU;
        
        ContextPath menuPath = systemApplication.getTerminal().getContextPath().append("system-setup");
        String description = buildSetupDescription();
        
        MenuContext menu = new MenuContext(
            menuPath,
            "System Setup",
            description,
            null
        );
        
        if (ioDaemonAvailable && availableKeyboards != null && !availableKeyboards.isEmpty()) {
            menu.addItem("select-password-keyboard", 
                "Select Password Keyboard",
                () -> showKeyboardSelectionMenu(true));
            
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
            this::configureSocketPath);
        
        if (isFirstRun) {
            menu.addItem("continue",
                "Continue to Password Setup →",
                this::completeSetup);
        } else {
            menu.addItem("back",
                "Back to Settings",
                () -> systemApplication.goBack());
        }
        
        // MenuNavigator becomes the active renderable
        menuNavigator.showMenu(menu);
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
    
    // ===== KEYBOARD SELECTION =====
    
    private void showKeyboardSelectionMenu(boolean forPassword) {
        currentState = SetupState.KEYBOARD_SELECTION;
        
        ContextPath menuPath = systemApplication.getTerminal().getContextPath().append("keyboard-selection");
        
        String title = forPassword ? "Select Password Keyboard" : "Select Default Keyboard";
        String description = "Choose which USB keyboard to use for " + 
            (forPassword ? "password entry" : "general input");
        
        MenuContext menu = new MenuContext(menuPath, title, description, null);
        
        if (availableKeyboards == null || availableKeyboards.isEmpty()) {
            menu.addInfoItem("no-keyboards", "No keyboards detected");
            menu.addItem("back", "Back", () -> {
                currentState = SetupState.MAIN_MENU;
                showMainSetupMenu();
            });
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
            menu.addItem("back", "Back", () -> {
                currentState = SetupState.MAIN_MENU;
                showMainSetupMenu();
            });
        }
        
        menuNavigator.showMenu(menu);
    }
    
    private void selectKeyboard(String deviceId, boolean forPassword) {
        Log.logMsg("[SystemSetupScreen] Selected keyboard: " + deviceId);
        
        if (forPassword) {
            systemApplication.completeBootstrap(deviceId)
                .thenRun(() -> {
                    if (isFirstRun) {
                        completeSetup();
                    } else {
                        currentState = SetupState.MAIN_MENU;
                        showMainSetupMenu();
                    }
                });
        }
    }
    
    private void refreshKeyboards() {
        discoverKeyboards()
            .thenRun(() -> showKeyboardSelectionMenu(true))
            .exceptionally(ex -> {
                errorMessage = "Failed to refresh: " + ex.getMessage();
                currentState = SetupState.ERROR;
                invalidate();
                
                systemApplication.getTerminal().waitForKeyPress()
                    .thenRun(() -> showKeyboardSelectionMenu(true));
                return null;
            });
    }
    
    // ===== CONFIGURATION OPTIONS =====
    
    private void configureGUIOnly() {
        // This transitions to a different screen
        // We don't render here - just update state and navigate
        systemApplication.completeBootstrap(null)
            .thenRun(() -> {
                if (isFirstRun) {
                    completeSetup();
                } else {
                    systemApplication.goBack();
                }
            });
    }
    
    private void configureSocketPath() {
        currentState = SetupState.SOCKET_CONFIG;
        invalidate();
        
        // Create input reader
        inputReader = new TerminalInputReader(systemApplication.getTerminal(), 7, 7, 60);
        inputReader.setText(socketPath);
        
        inputReader.setOnComplete(newPath -> {
            handleSocketPathComplete(newPath);
        });
        
        inputReader.setOnEscape(v -> {
            if (inputReader != null) {
                inputReader.close();
                inputReader = null;
            }
            currentState = SetupState.MAIN_MENU;
            showMainSetupMenu();
        });
    }
    
    private void handleSocketPathComplete(String newPath) {
        if (inputReader != null) {
            inputReader.close();
            inputReader = null;
        }
        
        if (newPath == null || newPath.trim().isEmpty()) {
            currentState = SetupState.MAIN_MENU;
            showMainSetupMenu();
            return;
        }
        
        if (!newPath.startsWith("/")) {
            errorMessage = "Socket path must be absolute (start with /)";
            currentState = SetupState.ERROR;
            invalidate();
            
            systemApplication.getTerminal().waitForKeyPress()
                .thenRun(this::configureSocketPath);
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
                currentState = SetupState.MAIN_MENU;
                showMainSetupMenu();
            })
            .exceptionally(ex -> {
                // Revert on failure
                socketPath = oldPath;
                systemApplication.completeBootstrap(systemApplication.getPasswordKeyboardId());
                
                errorMessage = "Failed to apply new path: " + ex.getMessage();
                currentState = SetupState.ERROR;
                invalidate();
                
                systemApplication.getTerminal().waitForKeyPress()
                    .thenRun(() -> {
                        currentState = SetupState.MAIN_MENU;
                        showMainSetupMenu();
                    });
                return null;
            });
    }
    
    private void showInstallationInfo() {
        // This would be another screen or a large text box
        // For now, just go back
        currentState = SetupState.MAIN_MENU;
        showMainSetupMenu();
    }
    
    private void retryDetection() {
        currentState = SetupState.DETECTING;
        invalidate();
        
        detectIODaemon()
            .thenRun(() -> {
                currentState = SetupState.MAIN_MENU;
                showMainSetupMenu();
            });
    }
    
    private void completeSetup() {
        Log.logMsg("[SystemSetupScreen] Setup complete");
        
        if (isFirstRun) {
            systemApplication.getStateMachine().addState(SystemApplication.CHECKING_SETTINGS);
        } else {
            systemApplication.goBack();
        }
    }
}