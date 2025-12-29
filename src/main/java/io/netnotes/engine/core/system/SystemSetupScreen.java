package io.netnotes.engine.core.system;

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

/**
 * SystemSetupScreen - Configure IODaemon and keyboard devices
 * 
 * Can be called:
 * - First run (before authentication) - shows welcome message
 * - Post-authentication (from settings) - just shows options
 * 
 * Features:
 * - Detect IODaemon availability
 * - Browse and select keyboards
 * - Configure password keyboard
 * - Configure socket path
 * - Skip IODaemon (use GUI only)
 */
public class SystemSetupScreen extends TerminalScreen {
    
    private final MenuNavigator menuNavigator;
    private final boolean isFirstRun;
    
    // Detection results
    private boolean ioDaemonAvailable = false;
    private String socketPath = "/var/run/io-daemon.sock";
    private List<DeviceDescriptorWithCapabilities> availableKeyboards = null;
    
    public SystemSetupScreen(String id, SystemTerminalContainer terminal) {
        super(id, terminal);
        this.menuNavigator = new MenuNavigator(terminal);
        
        // Check if this is first run (not authenticated yet)
        this.isFirstRun = !terminal.isAuthenticated();
    }
    
    @Override
    public CompletableFuture<Void> onShow() {
        Log.logMsg("[SystemSetupScreen] Showing setup");
        
        return CompletableFuture.completedFuture(null)
            .thenCompose(v -> {
                if (isFirstRun) {
                    return showWelcomeMessage()
                        .thenCompose(v2 -> terminal.waitForKeyPress());
                } else {
                    return CompletableFuture.completedFuture(null);
                }
            })
            .thenCompose(v -> detectIODaemon())
            .thenCompose(v -> showMainSetupMenu())
            .exceptionally(ex -> {
                Log.logError("[SystemSetupScreen] Error: " + ex.getMessage());
                terminal.clear()
                    .thenCompose(v -> terminal.printError("Setup error: " + ex.getMessage()))
                    .thenCompose(v -> terminal.println("\nPress any key to return..."))
                    .thenCompose(v -> terminal.waitForKeyPress())
                    .thenRun(() -> terminal.goBack());
                return null;
            });
    }
    
    @Override
    public void onHide() {
        menuNavigator.cleanup();
    }
    
    @Override
    public CompletableFuture<Void> render() {
        // MenuNavigator handles rendering and resize
        if (menuNavigator != null && menuNavigator.hasMenu()) {
            menuNavigator.refreshMenu();
        }
        return CompletableFuture.completedFuture(null);
    }
    
    // ===== WELCOME MESSAGE (First Run Only) =====
    
    private CompletableFuture<Void> showWelcomeMessage() {
         
        return terminal.clear()
            .thenCompose(v -> terminal.hideCursor())
            .thenCompose(v -> TerminalTextBox.builder()
                    .position(0, 2)
                    .size(terminal.getWidth()-4, 5)
                    .title("Welcome to Netnotes", TerminalTextBox.TitlePlacement.INSIDE_CENTER)
                    .style(BoxStyle.DOUBLE)
                    .titleStyle(TextStyle.BOLD)
                    .contentAlignment(TerminalTextBox.ContentAlignment.CENTER)
                    .build()
                    .render(terminal))
            .thenCompose(v -> {
                int msgRowCalc = terminal.getRows() / 2 + 2;
                int msgRow = Math.max(msgRowCalc, 8);
                String msg = "Initial Setup";
                int msgLength =  msg.length();
                int msgColCalc = terminal.getCols() / 2 - msgLength / 2;
                int msgCol = Math.max(0, msgColCalc);
                int lineCol =  Math.max(0, msgCol -1);
                return terminal.printAt(msgRow, msgCol, msg, TextStyle.INFO)
                    .thenCompose(v1->terminal.drawHLine(msgRow +1, lineCol, msgLength + 2));
            })
            .thenCompose(v -> {
                int promptRow = terminal.getRows() / 2 + 5;
                String prompt = "Press any key to continue...";
                int promptCol = terminal.getCols() / 2 - prompt.length() / 2;
                
                return terminal.printAt(promptRow, promptCol, prompt, TextStyle.INFO);
            });
    }
    
    // ===== IODaemon DETECTION =====
    
    private CompletableFuture<Void> detectIODaemon() {
        return terminal.clear()
            .thenCompose(v -> {
                int row = terminal.getRows() / 2;
                int col = terminal.getCols() / 2 - 15;
                
                return terminal.printAt(row, col, "Detecting IODaemon...", TextStyle.INFO);
            })
            .thenCompose(v -> {
                // Use IODaemonManager's detection
                return terminal.getIoDaemonManager().detect();
            })
            .thenCompose(detectionResult -> {
                ioDaemonAvailable = detectionResult.isAvailable();
                
                if (detectionResult.isFullyOperational()) {
                    Log.logMsg("[SystemSetupScreen] IODaemon fully operational");
                    socketPath = detectionResult.socketPath;
                    
                    // Ensure it's registered and available
                    return terminal.getIoDaemonManager().ensureAvailable()
                        .thenCompose(ioDaemonPath -> {
                            // Now discover keyboards
                            return discoverKeyboards();
                        });
                        
                } else if (detectionResult.binaryExists && !detectionResult.processRunning) {
                    Log.logMsg("[SystemSetupScreen] IODaemon installed but not running");
                    socketPath = detectionResult.socketPath;
                    
                    // Try to start it
                    return terminal.getIoDaemonManager().ensureAvailable()
                        .thenCompose(ioDaemonPath -> discoverKeyboards())
                        .exceptionally(ex -> {
                            Log.logError("[SystemSetupScreen] Failed to start IODaemon: " + 
                                ex.getMessage());
                            ioDaemonAvailable = false;
                            return null;
                        });
                        
                } else if (detectionResult.binaryExists && detectionResult.processRunning && 
                        !detectionResult.socketAccessible) {
                    Log.logMsg("[SystemSetupScreen] IODaemon running but socket not accessible");
                    
                    // Show socket path issue
                    return terminal.clear()
                        .thenCompose(v2 -> terminal.printWarning(
                            "IODaemon is running but socket not accessible:"))
                        .thenCompose(v2 -> terminal.println("  " + detectionResult.socketPath))
                        .thenCompose(v2 -> terminal.println(""))
                        .thenCompose(v2 -> terminal.println(
                            "This may be a permissions issue. Check socket permissions."))
                        .thenCompose(v2 -> terminal.println(""))
                        .thenCompose(v2 -> terminal.println("Press any key to continue..."))
                        .thenCompose(v2 -> terminal.waitForKeyPress());
                        
                } else {
                    Log.logMsg("[SystemSetupScreen] IODaemon not installed");
                    ioDaemonAvailable = false;
                    
                    // Store default socket path
                    IODaemonDetection.InstallationPaths paths = 
                        terminal.getIoDaemonManager().getInstallationPaths();
                    if (paths != null) {
                        socketPath = paths.socketPath;
                    }
                    
                    return CompletableFuture.completedFuture(null);
                } 
          
            })
            .exceptionally(ex -> {
                Log.logError("[SystemSetupScreen] IODaemon detection failed: " + ex.getMessage());
                ioDaemonAvailable = false;
                return null;
            });
    }
    private CompletableFuture<Void> discoverKeyboards() {
        // Connect to IODaemon and discover keyboards
        return terminal.connectToIODaemon()
            .thenCompose(session -> session.discoverDevices())
            .thenAccept(devices -> {
                // Filter to keyboards only
                availableKeyboards = devices.stream()
                    .filter(d -> d.usbDevice().getDeviceType().equals(ItemTypes.KEYBOARD))
                    .toList();
                
                Log.logMsg("[SystemSetupScreen] Found " + availableKeyboards.size() + " keyboards");
            })
            .exceptionally(ex -> {
                Log.logError("[SystemSetupScreen] Keyboard discovery failed: " + ex.getMessage());
                availableKeyboards = List.of();
                return null;
            });
    }
    
    // ===== MAIN SETUP MENU =====
    
    private CompletableFuture<Void> showMainSetupMenu() {
        ContextPath menuPath = terminal.getContextPath().append("system-setup");
        
        String description = buildSetupDescription();
        
        MenuContext menu = new MenuContext(
            menuPath,
            "System Setup",
            description,
            null
        );
        
        if (ioDaemonAvailable && availableKeyboards != null && !availableKeyboards.isEmpty()) {
            // IODaemon working - offer keyboard selection
            menu.addItem("select-password-keyboard", 
                "Select Password Keyboard",
                () -> showKeyboardSelectionMenu(true));
            
            menu.addItem("use-gui-only",
                "Use UI Keyboard Only (No Secure Input)",
                () -> configureGUIOnly());
            
        } else {
            // IODaemon not available - show options
            menu.addItem("install-info",
                "How to Install IODaemon",
                () -> showInstallationInfo());
            
            menu.addItem("use-gui-only",
                "Continue with UI Keyboard Only",
                () -> configureGUIOnly());
            
            menu.addItem("retry-detection",
                "Retry IODaemon Detection",
                () -> retryDetection());
        }
        
        menu.addSeparator("Advanced");
        
        menu.addItem("socket-path",
            "Configure Socket Path: " + socketPath,
            () -> configureSocketPath());
        
        if (isFirstRun) {
            menu.addItem("continue",
                "Continue to Password Setup →",
                () -> completeSetup());
        } else {
            
            menu.addItem("back",
                "Back to Settings",
                () -> terminal.goBack());
        }
        
        menuNavigator.showMenu(menu);
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Updated buildSetupDescription to show more detailed status
     */
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
            
            // Add installation hint
            IODaemonDetection.InstallationPaths paths = 
                terminal.getIoDaemonManager().getInstallationPaths();
            
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
        ContextPath menuPath = terminal.getContextPath().append("keyboard-selection");
        
        String title = forPassword ? "Select Password Keyboard" : "Select Default Keyboard";
        String description = "Choose which USB keyboard to use for " + 
            (forPassword ? "password entry" : "general input");
        
        MenuContext menu = new MenuContext(menuPath, title, description, null);
        
        if (availableKeyboards == null || availableKeyboards.isEmpty()) {
            menu.addInfoItem("no-keyboards", "No keyboards detected");
            menu.addItem("back", "Back", () -> showMainSetupMenu());
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
            menu.addItem("refresh", "Refresh Device List", () -> refreshKeyboards());
            menu.addItem("back", "Back", () -> showMainSetupMenu());
        }
        
        menuNavigator.showMenu(menu);
    }
    
    private void selectKeyboard(String deviceId, boolean forPassword) {
        Log.logMsg("[SystemSetupScreen] Selected keyboard: " + deviceId);
        
        if (forPassword) {
            // Store in terminal's bootstrap config
            terminal.completeBootstrap(deviceId)
                .thenRun(() -> {
                    terminal.clear()
                        .thenCompose(v -> terminal.printSuccess(
                            "Password keyboard configured: " + deviceId))
                        .thenCompose(v -> terminal.println("\nPress any key to continue..."))
                        .thenCompose(v -> terminal.waitForKeyPress())
                        .thenRun(() -> {
                            if (isFirstRun) {
                                completeSetup();
                            } else {
                                showMainSetupMenu();
                            }
                        });
                });
        }
    }
    
    private void refreshKeyboards() {
        discoverKeyboards()
            .thenRun(() -> showKeyboardSelectionMenu(true))
            .exceptionally(ex -> {
                terminal.clear()
                    .thenCompose(v -> terminal.printError(
                        "Failed to refresh: " + ex.getMessage()))
                    .thenCompose(v -> terminal.println("\nPress any key..."))
                    .thenCompose(v -> terminal.waitForKeyPress())
                    .thenRun(() -> showKeyboardSelectionMenu(true));
                return null;
            });
    }
    
    // ===== CONFIGURATION OPTIONS =====
    
    private void configureGUIOnly() {
        terminal.clear()
            .thenCompose(v -> terminal.println("Configuring GUI keyboard only...", 
                TextStyle.INFO))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println(
                "Passwords will be entered using the GUI keyboard."))
            .thenCompose(v -> terminal.println(
                "You can change this later in settings."))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Press any key to continue..."))
            .thenCompose(v -> terminal.waitForKeyPress())
            .thenCompose(v -> {
                // Configure to not use secure input
                return terminal.completeBootstrap(null);
            })
            .thenRun(() -> {
                if (isFirstRun) {
                    completeSetup();
                } else {
                    terminal.goBack();
                }
            });
    }
    
    private void configureSocketPath() {
        terminal.clear()
            .thenCompose(v -> terminal.println("Socket Path Configuration", TextStyle.BOLD))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Current: " + socketPath))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Enter new socket path (or press ESC to cancel):"))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.print("> "))
            .thenRun(this::startSocketPathInput);
    }
    
    private void showInstallationInfo() {
        IODaemonDetection.InstallationPaths paths = 
            terminal.getIoDaemonManager().getInstallationPaths();

        terminal.clear()
            .thenCompose(v -> terminal.println("IODaemon Installation", TextStyle.BOLD))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println(
                "IODaemon provides secure password entry via USB keyboards."))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Installation:", TextStyle.BOLD))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> {
                if (paths != null) {
                    return terminal.println("Installation Paths:", TextStyle.BOLD)
                        .thenCompose(v2 -> terminal.println(""))
                        .thenCompose(v2 -> terminal.println("  Binary: " + paths.binaryPath))
                        .thenCompose(v2 -> terminal.println("  Socket: " + paths.socketPath))
                        .thenCompose(v2 -> terminal.println("  Install: " + paths.installDir))
                        .thenCompose(v2 -> terminal.println(""))
                        .thenCompose(v2 -> terminal.println("Start command:", TextStyle.BOLD))
                        .thenCompose(v2 -> terminal.println("  " + paths.startCommand))
                        .thenCompose(v2 -> terminal.println(""));
                } else {
                    return CompletableFuture.completedFuture(null);
                }
            })
            .thenCompose(v -> terminal.println("Installation:", TextStyle.BOLD))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(termVoid->{
                if(IODaemonDetection.IS_LINUX){
                    return terminal.println("Linux:")
                        .thenCompose(v -> terminal.println(
                            "  curl -fsSL https://raw.githubusercontent.com/networkspore/"))
                        .thenCompose(v -> terminal.println(
                            "    NoteDaemon/master/download-install.sh | sudo bash"))
                        .thenCompose(v -> terminal.println(""));
                }else if(IODaemonDetection.IS_MAC){
                    return terminal.println("macOS:")
                    .thenCompose(v -> terminal.println(
                        "  brew install cmake boost libusb openssl"))
                    .thenCompose(v -> terminal.println(
                        "  git clone https://github.com/networkspore/NoteDaemon.git"))
                    .thenCompose(v -> terminal.println(
                        "  cd NoteDaemon && mkdir build && cd build"))
                    .thenCompose(v -> terminal.println(
                        "  cmake .. && make && sudo make install"))
                    .thenCompose(v -> terminal.println(""));

                }else{
                    return  terminal.println("Windows:")
                    .thenCompose(v -> terminal.println(
                        "  Download installer from releases page"))
                    .thenCompose(v -> terminal.println(""));
                }
               
            })
            .thenCompose(v -> terminal.println(
                "More info: https://github.com/networkspore/NoteDaemon/releases"))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Press any key to go back..."))
            .thenCompose(v -> terminal.waitForKeyPress())
            .thenRun(() -> showMainSetupMenu());
    }
    
    /**
     * Updated retryDetection with better feedback
     */
    private void retryDetection() {
        terminal.clear()
            .thenCompose(v -> {
                int row = terminal.getRows() / 2;
                int col = terminal.getCols() / 2 - 12;
                return terminal.printAt(row, col, "Re-detecting IODaemon...", TextStyle.INFO);
            })
            .thenCompose(v -> detectIODaemon())
            .thenCompose(v -> {
                // Show brief result message
                String message;
                if (ioDaemonAvailable) {
                    message = "✓ IODaemon detected successfully!";
                } else {
                    message = "✗ IODaemon not found";
                }
                
                int row = terminal.getRows() / 2 + 2;
                int col = terminal.getCols() / 2 - message.length() / 2;
                
                return terminal.printAt(
                    row, col, message, 
                    ioDaemonAvailable ? TextStyle.SUCCESS : TextStyle.ERROR
                );
            })
            .thenCompose(v -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return CompletableFuture.completedFuture(null);
            })
            .thenRun(() -> showMainSetupMenu())
            .exceptionally(ex -> {
                terminal.clear()
                    .thenCompose(v -> terminal.printError(
                        "Detection failed: " + ex.getMessage()))
                    .thenCompose(v -> terminal.println("\nPress any key..."))
                    .thenCompose(v -> terminal.waitForKeyPress())
                    .thenRun(() -> showMainSetupMenu());
                return null;
            });
    }

    
    
    private void startSocketPathInput() {
        TerminalInputReader inputReader = new TerminalInputReader(
            terminal, terminal.getRows() - 2, 2, 60);
        
        // Pre-fill with current value
        inputReader.setText(socketPath);
        
        inputReader.setOnComplete(newPath -> {
            inputReader.close();
            
            if (newPath != null && !newPath.trim().isEmpty()) {
                // Validate path
                if (!newPath.startsWith("/")) {
                    terminal.clear()
                        .thenCompose(v -> terminal.printError(
                            "Socket path must be absolute (start with /)"))
                        .thenCompose(v -> terminal.println(""))
                        .thenCompose(v -> terminal.println("Press any key to try again..."))
                        .thenCompose(v -> terminal.waitForKeyPress())
                        .thenRun(() -> configureSocketPath());
                    return;
                }
                
                String oldPath = socketPath;
                socketPath = newPath.trim();
                
                terminal.clear()
                    .thenCompose(v -> terminal.println("Socket path updated!", TextStyle.SUCCESS))
                    .thenCompose(v -> terminal.println(""))
                    .thenCompose(v -> terminal.println("Old: " + oldPath))
                    .thenCompose(v -> terminal.println("New: " + socketPath))
                    .thenCompose(v -> terminal.println(""))
                    .thenCompose(v -> terminal.println("Saving configuration..."))
                    .thenCompose(v -> {
                        // Update terminal's bootstrap config
                        return terminal.completeBootstrap(
                            terminal.getPasswordKeyboardId()
                        );
                    })
                    .thenCompose(v -> terminal.println("✓ Configuration saved", TextStyle.SUCCESS))
                    .thenCompose(v -> terminal.println(""))
                    .thenCompose(v -> terminal.println("Testing new connection..."))
                    .thenCompose(v -> {
                        // Use manager to reconfigure
                        return terminal.getIoDaemonManager().reconfigureSocketPath(socketPath);
                    })
                    .thenCompose(v -> {
                        // Check if healthy now
                        return terminal.getIoDaemonManager().detect();
                    })
                    .thenCompose(result -> {
                        if (result.isFullyOperational()) {
                            return terminal.println("✓ Connected successfully!", TextStyle.SUCCESS)
                                .thenCompose(v2 -> {
                                    ioDaemonAvailable = true;
                                    // Discover keyboards with new connection
                                    return discoverKeyboards();
                                });
                        } else if (result.isAvailable()) {
                            return terminal.printWarning(
                                "⚠ IODaemon detected but not fully operational")
                                .thenCompose(v2 -> terminal.println("  Status: " + 
                                    (result.processRunning ? "Running" : "Not running")))
                                .thenCompose(v2 -> terminal.println("  Socket: " + 
                                    (result.socketAccessible ? "Accessible" : "Not accessible")));
                        } else {
                            return terminal.printWarning(
                                "⚠ Could not connect to IODaemon at new path")
                                .thenCompose(v2 -> {
                                    if (result.errorMessage != null) {
                                        return terminal.println("  " + result.errorMessage);
                                    }
                                    return CompletableFuture.completedFuture(null);
                                })
                                .thenCompose(v2 -> terminal.println(""))
                                .thenCompose(v2 -> terminal.println(
                                    "You may need to start IODaemon manually."));
                        }
                    })
                    .thenCompose(v -> terminal.println(""))
                    .thenCompose(v -> terminal.println("Press any key to continue..."))
                    .thenCompose(v -> terminal.waitForKeyPress())
                    .thenRun(() -> showMainSetupMenu())
                    .exceptionally(ex -> {
                        // Reconfiguration failed - revert
                        terminal.clear()
                            .thenCompose(v -> terminal.printError(
                                "Failed to apply new path: " + ex.getMessage()))
                            .thenCompose(v -> terminal.println(""))
                            .thenCompose(v -> terminal.println("Reverting to: " + oldPath))
                            .thenCompose(v -> {
                                socketPath = oldPath;
                                return terminal.completeBootstrap(
                                    terminal.getPasswordKeyboardId()
                                );
                            })
                            .thenCompose(v -> terminal.println("✓ Reverted", TextStyle.SUCCESS))
                            .thenCompose(v -> terminal.println(""))
                            .thenCompose(v -> terminal.println("Press any key..."))
                            .thenCompose(v -> terminal.waitForKeyPress())
                            .thenRun(() -> showMainSetupMenu());
                        return null;
                    });
            } else {
                // Cancelled or empty
                showMainSetupMenu();
            }
        });

        inputReader.setOnEscape((v) -> {
            inputReader.close();
            showMainSetupMenu();
        });
    }

    
    // ===== COMPLETION =====
    
    private void completeSetup() {
        Log.logMsg("[SystemSetupScreen] Setup complete");
        
        if (isFirstRun) {
            // Transition to password creation
            terminal.clear()
                .thenCompose(v -> terminal.println("System setup complete!", 
                    TextStyle.SUCCESS))
                .thenCompose(v -> terminal.println(""))
                .thenCompose(v -> terminal.println("Next: Create your master password"))
                .thenCompose(v -> terminal.println(""))
                .thenCompose(v -> terminal.println("Press any key to continue..."))
                .thenCompose(v -> terminal.waitForKeyPress())
                .thenRun(() -> {
                    // Terminal will transition to CHECKING_SETTINGS -> FIRST_RUN
                    terminal.getState().addState(SystemTerminalContainer.CHECKING_SETTINGS);
                });
        } else {
            terminal.goBack();
        }
    }
    /*
    private void saveConfiguration() {
        Log.logMsg("[SystemSetupScreen] Saving configuration");
        
        terminal.clear()
            .thenCompose(v -> terminal.println("Saving configuration...", 
                TextStyle.INFO))
            .thenCompose(v -> {
                // Configuration already saved via completeBootstrap()
                return CompletableFuture.completedFuture(null);
            })
            .thenCompose(v -> terminal.println("Configuration saved!", 
                TextStyle.SUCCESS))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Press any key to return..."))
            .thenCompose(v -> terminal.waitForKeyPress())
            .thenRun(() -> terminal.goBack());
    }*/
}