package io.netnotes.engine.core.system.control;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.BootstrapConfig;
import io.netnotes.engine.core.system.control.containers.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.terminal.TerminalCommands;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.TextStyle.BoxStyle;
import io.netnotes.engine.core.system.control.terminal.elements.TerminalTextBox;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.input.InputDevice;
import io.netnotes.engine.io.input.events.KeyDownEvent;
import io.netnotes.engine.io.input.events.RoutedEvent;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * BootstrapWizardProcess - Menu-driven first-run configuration
 * 
 * REFACTORED to use TerminalContainerHandle + keyboard events:
 * - Uses terminal for all output (no direct UIRenderer)
 * - Uses MenuNavigatorProcess for interactive menus
 * - Keyboard-driven navigation with ExecutorConsumer
 * - Saves config to disk on completion
 * - Terminal handle auto-registers itself (no manual spawn needed)
 * 
 * Flow:
 * 1. Welcome Screen → wait for key
 * 2. Detect secure input
 * 3. Present installation options as menu
 * 4. Execute selected option
 * 5. Save configuration to disk
 * 6. Complete (SystemProcess initializes singleton)
 */
public class BootstrapWizardProcess extends FlowProcess {

    private final BitFlagStateMachine state;
    private TerminalContainerHandle terminal;
    private final InputDevice keyboard;
    
    private MenuNavigator menuNavigator;
    private NoteBytesMap bootstrapConfig;
    private SecureInputDetectionResult detectionResult;
    
    // Keyboard event handling
    private final Consumer<RoutedEvent> keyboardConsumer;
    private volatile boolean waitingForKey = false;
    private CompletableFuture<Void> keyWaitFuture = null;

    // States
    public static final long DETECTING = 1L << 0;
    public static final long CONFIGURING = 1L << 1;
    public static final long INSTALLING = 1L << 2;
    public static final long VERIFYING = 1L << 3;
    public static final long COMPLETE = 1L << 4;
    public static final long WAITING_FOR_KEY = 1L << 5;
    
    public BootstrapWizardProcess(
        String name, 
        InputDevice keyboard
    ) {
        super(name, ProcessType.BIDIRECTIONAL);
        this.keyboard = keyboard;
        this.state = new BitFlagStateMachine("bootstrap-wizard");
        // Create keyboard event consumer
        this.keyboardConsumer = this::handleKeyboardEvent;
    }
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(DETECTING);
        Log.logMsg("[BootstrapWizardProcess] running");

        return SettingsData.getOrDefaultBootstrapConfig()
            .thenAccept((config) -> {
                bootstrapConfig = config;
            })
            .thenCompose((v) -> {
                // Create terminal handle - it will auto-register in its run()
                this.terminal = TerminalContainerHandle.builder("bootstrap-terminal")
                    .autoFocus(true)
                    .build();
                
                // Register as child - this gives it a path under us
                return CompletableFuture.completedFuture(registerChild(terminal));
            })
            .thenCompose(terminalPath -> {
                // Start the terminal process
                Log.logMsg("[BootstrapWizardProcess] Starting terminal at: " + terminalPath);
                return startProcess(terminalPath);
            })
            .thenCompose(v -> {
                // Create menu navigator after terminal is registered
                this.menuNavigator = new MenuNavigator(terminal, keyboard);
                return CompletableFuture.completedFuture(null);
            })
            .thenRun(() -> {
                Log.logMsg("[BootstrapWizardProcess] Terminal started and menu navigator created");
            });
    }

    public CompletableFuture<Void> start() {
        Log.logMsg("[BootstrapWizardProcess] start called");
        
        // Wait for terminal streams to be fully ready before showing anything
        return terminal.waitUntilReady()
            .thenCompose(v -> {
                Log.logMsg("[BootstrapWizardProcess] terminal render stream Ready");
                return terminal.getEventStreamReadyFuture();
            })
            .thenCompose(v -> CompletableFuture.runAsync(() -> {
                Log.logMsg("[BootstrapWizardProcess] event stream Ready");
                try { Thread.sleep(100); } 
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }))
            .thenCompose(v -> showWelcomeScreen())
            .thenCompose(v -> detectSecureInput())
            .thenCompose(result -> {
                this.detectionResult = result;
                state.removeState(DETECTING);
                state.addState(CONFIGURING);
                
                // Give clear a moment to take effect
                return CompletableFuture.runAsync(() -> {
                    try { Thread.sleep(100); } 
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }).thenCompose(v2 -> showSecureInputOptionsMenu());
            })
            .thenRun(() -> {
                Log.logMsg("[BootstrapWizardProcess] Wizard flow started");
            })
            .exceptionally(ex -> {
                Log.logError("[BootstrapWizardProcess] Start failed: " + ex.getMessage());
                ex.printStackTrace();
                return null;
            });
    }
    
    // ===== KEYBOARD EVENT HANDLING =====
    
    /**
     * Handle keyboard events from input device
     */
    private void handleKeyboardEvent(RoutedEvent event) {
        if (!(event instanceof KeyDownEvent)) {
            return;
        }
        
        // If waiting for any key press
        if (state.hasState(WAITING_FOR_KEY) && waitingForKey) {
            waitingForKey = false;
            state.removeState(WAITING_FOR_KEY);
            
            // Complete the waiting future
            if (keyWaitFuture != null) {
                keyWaitFuture.complete(null);
                keyWaitFuture = null;
            }
            
            // Unregister keyboard consumer
            keyboard.setEventConsumer(null);
        }
    }
    
    /**
     * Wait for any key press
     */
    private CompletableFuture<Void> waitForKeyPress() {
        state.addState(WAITING_FOR_KEY);
        waitingForKey = true;
        keyWaitFuture = new CompletableFuture<>();
        
        // Register keyboard consumer
        keyboard.setEventConsumer(keyboardConsumer);
        
        return keyWaitFuture;
    }
    
    // ===== WELCOME =====
    
    private CompletableFuture<Void> showWelcomeScreen() {
        Log.logMsg("[BootstrapWizardProcess] **Showing Welcome Screen**");
        return terminal.beginBatch()
            .thenCompose(v -> terminal.clear())
            .thenCompose(v ->
                TerminalTextBox.builder()
                .position(0, 1)
                .size(terminal.getCols(), 5)
                .title("Welcome to Netnotes", TerminalTextBox.TitlePlacement.INSIDE_CENTER)
                .style(BoxStyle.DOUBLE)
                .titleStyle(TextStyle.BOLD)
                .contentAlignment(TerminalTextBox.ContentAlignment.CENTER)
                .build()
                .render(terminal))
            .thenCompose(v -> {
                int width = 60;
                int startCol = (terminal.getCols() - width) / 2;
                int startRow = terminal.getRows() > 12 ? (terminal.getRows() - 12) / 2 : 6;
                
                // Content inside box
                return terminal.printAt(startRow + 2, startCol + 4, 
                    "This wizard will help you configure your system.")
                    .thenCompose(v2 -> terminal.printAt(startRow + 5, startCol + 4,
                        TerminalCommands.PRESS_ANY_KEY, 
                        TextStyle.INFO))
                    .thenCompose(v2 -> 
                        terminal.moveCursor(startRow + 5, startCol + 4 + TerminalCommands.PRESS_ANY_KEY.length()));
            })
            .thenCompose(v -> waitForKeyPress());
    }
    
    // ===== DETECTION =====
    
    private CompletableFuture<SecureInputDetectionResult> detectSecureInput() {
        Log.logMsg("[BootstrapWizardProcess] detecting secure input");
        
        return terminal.clear()
            .thenCompose(v -> terminal.hideCursor())
            .thenCompose(v -> {
                // Show detection in progress
                int row = terminal.getRows() / 2;
                int col = (terminal.getCols() - 30) / 2;
                
                return terminal.printAt(row, col, 
                    "Detecting secure input...", 
                    TextStyle.INFO);
            })
            .thenCompose(v -> CompletableFuture.supplyAsync(() -> {
                SecureInputDetectionResult result = new SecureInputDetectionResult();
                
                String socketPath = BootstrapConfig.getString(bootstrapConfig, 
                    BootstrapConfig.SYSTEM + "/" + BootstrapConfig.BASE + "/" + 
                    BootstrapConfig.SECURE_INPUT + "/" + BootstrapConfig.SOCKET_PATH,
                    BootstrapConfig.DEFAULT_SOCKET_PATH);
                
                result.socketExists = SecureInputDetector.socketExists(socketPath);
                
                if (result.socketExists) {
                    result.canConnect = SecureInputDetector.testConnection(socketPath);
                    
                    if (result.canConnect) {
                        result.keyboardCount = SecureInputDetector.detectKeyboards(socketPath);
                    }
                }
                
                result.os = System.getProperty("os.name").toLowerCase();
                result.canInstall = SecureInputDetector.canAutoInstall(result.os);
                
                return result;
            }));
    }
    
    // ===== MENU CONSTRUCTION =====
    
    private CompletableFuture<Void> showSecureInputOptionsMenu() {
        if (detectionResult.socketExists && detectionResult.canConnect) {
            return showAlreadyInstalledMenu();
        } else {
            return showInstallationOptionsMenu();
        }
    }
    
    /**
     * Already installed - offer to enable
     */
    private CompletableFuture<Void> showAlreadyInstalledMenu() {
        ContextPath menuPath = contextPath.append("secure-input-detected");
        
        StringBuilder desc = new StringBuilder();
        desc.append("✓ NoteDaemon is already installed!\n\n");
        desc.append(String.format("Found %d USB keyboard(s) available.\n\n", 
            detectionResult.keyboardCount));
        desc.append("Secure input provides protection against keyloggers\n");
        desc.append("and screen capture during password entry.\n");
        
        MenuContext menu = new MenuContext(
            menuPath, 
            "Secure Input Detected",
            desc.toString(),
            null
        );
        
        menu.addItem("enable", "Enable Secure Input (Recommended)", () -> {
            enableSecureInput();
        });
        
        menu.addItem("skip", "Skip (Use GUI Keyboard Only)", () -> {
            skipSecureInput();
        });
  
        menuNavigator.showMenu(menu);
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Not installed - offer installation options
     */
    private CompletableFuture<Void> showInstallationOptionsMenu() {
        ContextPath menuPath = contextPath.append("installation-options");
        
        String desc = "⚠ NoteDaemon is not installed.\n\n" +
            "Secure input provides hardware-level protection for password entry.\n" +
            "You can install it now or skip and use GUI keyboard.\n";
        
        MenuContext menuWithDesc = new MenuContext(
            menuPath, 
            "Secure Input Installation",
            desc,
            null
        );
       
        if (detectionResult.canInstall) {
            menuWithDesc.addItem("auto-install", "Install Now (Recommended)", () -> {
                startAutomatedInstallation();
            });
        }
        
        menuWithDesc.addItem("manual", "Show Manual Installation Instructions", () -> {
            showManualInstructions();
        });
        
        menuWithDesc.addItem("skip", "Skip (Use GUI Keyboard Only)", () -> {
            skipSecureInput();
        });
        
        menuWithDesc.addItem("advanced", "Advanced Configuration", () -> {
            showAdvancedConfigMenu();
        });

        menuNavigator.showMenu(menuWithDesc);
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Manual installation instructions
     */
    private void showManualInstructions() {
        ContextPath menuPath = contextPath.append("manual-instructions");
        MenuContext menu = new MenuContext(menuPath, "Manual Installation");
        
        String instructions = generateInstallInstructions(detectionResult.os);
        
        terminal.clear()
            .thenCompose(v -> terminal.println(instructions));
        
        menu.addItem("done", "Done Reading", () -> {
            showInstallationOptionsMenu();
        });
        
        menu.addItem("copy-command", "Copy Quick Install Command", () -> {
            copyToClipboard(getQuickInstallCommand(detectionResult.os));
            terminal.clear()
                .thenCompose(v -> terminal.println("Command copied to clipboard!", 
                    TextStyle.SUCCESS))
                .thenCompose(v -> terminal.println(""))
                .thenCompose(v -> terminal.println("Press any key to continue..."))
                .thenCompose(v -> waitForKeyPress())
                .thenRun(() -> showManualInstructions());
        });
        
        menuNavigator.showMenu(menu);
    }
    
    /**
     * Advanced configuration menu
     */
    private void showAdvancedConfigMenu() {
        ContextPath menuPath = contextPath.append("advanced-config");
        MenuContext menu = new MenuContext(menuPath, "Advanced Configuration");
        
        menu.addItem("socket-path", "Custom Socket Path", () -> {
            promptForSocketPath();
        });
        
        menu.addItem("back", "Back to Main Options", () -> {
            showInstallationOptionsMenu();
        });
        
        menuNavigator.showMenu(menu);
    }
    
    // ===== ACTIONS =====
    
    private void enableSecureInput() {
        BootstrapConfig.set(bootstrapConfig,
            BootstrapConfig.SYSTEM + "/" + BootstrapConfig.BASE + "/" + 
            BootstrapConfig.SECURE_INPUT + "/" + BootstrapConfig.INSTALLED,
            new NoteBytes(true)
        );
        
        BootstrapConfig.set(bootstrapConfig,
            BootstrapConfig.SYSTEM + "/" + BootstrapConfig.BASE + "/" + 
            BootstrapConfig.INPUT + "/" + BootstrapConfig.SOURCES + "/" + 
            BootstrapConfig.SECURE_INPUT + "/" + BootstrapConfig.ENABLED,
            new NoteBytes(true)
        );
        
        terminal.clear()
            .thenCompose(v -> terminal.println("Secure input enabled!", 
                TextStyle.SUCCESS))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println(
                "Your passwords will be captured directly from USB keyboards."))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Press any key to continue..."))
            .thenCompose(v -> waitForKeyPress())
            .thenRun(() -> finishConfiguration());
    }
    
    private void skipSecureInput() {
        BootstrapConfig.set(bootstrapConfig,
            BootstrapConfig.SYSTEM + "/" + BootstrapConfig.BASE + "/" + 
            BootstrapConfig.SECURE_INPUT + "/" + BootstrapConfig.INSTALLED,
            new NoteBytes(false)
        );
        
        BootstrapConfig.set(bootstrapConfig,
            BootstrapConfig.SYSTEM + "/" + BootstrapConfig.BASE + "/" + 
            BootstrapConfig.INPUT + "/" + BootstrapConfig.SOURCES + "/" + 
            BootstrapConfig.SECURE_INPUT + "/" + BootstrapConfig.ENABLED,
            new NoteBytes(false)
        );
        
        terminal.clear()
            .thenCompose(v -> terminal.println("Secure input disabled.", 
                TextStyle.INFO))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println(
                "You can install NoteDaemon later through system settings."))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Press any key to continue..."))
            .thenCompose(v -> waitForKeyPress())
            .thenRun(() -> finishConfiguration());
    }
    
    private void startAutomatedInstallation() {
        state.removeState(CONFIGURING);
        state.addState(INSTALLING);
        
        String os = detectionResult.os;
        
        // Create installer with terminal and keyboard
        SecureInputInstaller installer = new SecureInputInstaller(
            "secure-input-installer", 
            os, 
            terminal,
            keyboard
        );
        
        // Register and start installer
        registerChild(installer);
        ContextPath installerPath = installer.getContextPath();
        
        startProcess(installerPath)
            .thenCompose(v -> installer.getCompletionFuture())
            .thenCompose(v -> {
                state.removeState(INSTALLING);
                state.addState(VERIFYING);
                return verifyInstallation();
            })
            .thenCompose(success -> {
                state.removeState(VERIFYING);
                state.addState(CONFIGURING);
                
                if (success) {
                    BootstrapConfig.set(bootstrapConfig,
                        BootstrapConfig.SYSTEM + "/" + BootstrapConfig.BASE + "/" + 
                        BootstrapConfig.SECURE_INPUT + "/" + BootstrapConfig.INSTALLED,
                        new NoteBytes(true)
                    );
                    
                    BootstrapConfig.set(bootstrapConfig,
                        BootstrapConfig.SYSTEM + "/" + BootstrapConfig.BASE + "/" + 
                        BootstrapConfig.INPUT + "/" + BootstrapConfig.SOURCES + "/" + 
                        BootstrapConfig.SECURE_INPUT + "/" + BootstrapConfig.ENABLED,
                        new NoteBytes(true)
                    );
                    
                    return terminal.clear()
                        .thenCompose(v -> terminal.println("Installation successful!", 
                            TextStyle.SUCCESS))
                        .thenCompose(v -> terminal.println(""))
                        .thenCompose(v -> terminal.println("NoteDaemon is now running."))
                        .thenCompose(v -> terminal.println(""))
                        .thenCompose(v -> terminal.println("Press any key to continue..."))
                        .thenCompose(v -> waitForKeyPress())
                        .thenRun(() -> finishConfiguration());
                } else {
                    return terminal.clear()
                        .thenCompose(v -> terminal.printError(
                            "Installation verification failed."))
                        .thenCompose(v -> terminal.println(""))
                        .thenCompose(v -> terminal.println(
                            "You may need to install manually."))
                        .thenCompose(v -> terminal.println(""))
                        .thenCompose(v -> terminal.println("Press any key to continue..."))
                        .thenCompose(v -> waitForKeyPress())
                        .thenRun(() -> showInstallationOptionsMenu());
                }
            })
            .exceptionally(ex -> {
                state.removeState(INSTALLING);
                state.addState(CONFIGURING);
                
                terminal.clear()
                    .thenCompose(v -> terminal.printError(
                        "Installation failed: " + ex.getMessage()))
                    .thenCompose(v -> terminal.println(""))
                    .thenCompose(v -> terminal.println("Press any key to continue..."))
                    .thenCompose(v -> waitForKeyPress())
                    .thenRun(() -> showInstallationOptionsMenu());
                
                return null;
            });
    }
    
    private CompletableFuture<Boolean> verifyInstallation() {
        terminal.clear()
            .thenCompose(v -> terminal.println("Verifying installation...", 
                TextStyle.INFO))
            .thenCompose(v -> terminal.println(""));
        
        return CompletableFuture.supplyAsync(() -> {
            String socketPath = BootstrapConfig.getString(bootstrapConfig,
                BootstrapConfig.SYSTEM + "/" + BootstrapConfig.BASE + "/" + 
                BootstrapConfig.SECURE_INPUT + "/" + BootstrapConfig.SOCKET_PATH,
                BootstrapConfig.DEFAULT_SOCKET_PATH);
            
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            boolean socketExists = SecureInputDetector.socketExists(socketPath);
            if (!socketExists) return false;
            
            boolean canConnect = SecureInputDetector.testConnection(socketPath);
            return canConnect;
        });
    }
    
    private void promptForSocketPath() {
        terminal.clear()
            .thenCompose(v -> terminal.println("Socket path configuration not yet implemented.", 
                TextStyle.WARNING))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Default: /var/run/io-daemon.sock"))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Press any key to continue..."))
            .thenCompose(v -> waitForKeyPress())
            .thenRun(() -> showAdvancedConfigMenu());
    }
    
    private void finishConfiguration() {
        state.removeState(CONFIGURING);
        state.addState(COMPLETE);
        
        // Save to disk - BootstrapConfig singleton will load it
        SettingsData.saveBootstrapConfig(bootstrapConfig)
            .thenCompose(v -> terminal.clear())
            .thenCompose(v -> terminal.println("Configuration saved!", 
                TextStyle.SUCCESS))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Your system is now configured."))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Starting system..."))
            .thenRun(() -> complete())
            .exceptionally(ex -> {
                terminal.clear()
                    .thenCompose(v -> terminal.printError(
                        "Failed to save configuration: " + ex.getMessage()))
                    .thenCompose(v -> terminal.println(""))
                    .thenCompose(v -> terminal.println("Press any key to retry..."))
                    .thenCompose(v -> waitForKeyPress())
                    .thenRun(() -> finishConfiguration());
                return null;
            });
    }
    
    // ===== UTILITIES =====
    
    private String generateInstallInstructions(String os) {
        if (os.contains("linux")) {
            return """
                NoteDaemon Installation Instructions (Linux)
                =============================================
                
                Option 1: Quick Install (Recommended)
                --------------------------------------
                Run this command in your terminal:
                
                curl -fsSL https://raw.githubusercontent.com/networkspore/NoteDaemon/master/download-install.sh | sudo bash
                
                Option 2: Manual Build
                ----------------------
                1. Install dependencies:
                   sudo apt-get install build-essential cmake pkg-config \\
                        libusb-1.0-0-dev libssl-dev libboost-all-dev
                
                2. Download and build:
                   wget https://github.com/networkspore/NoteDaemon/archive/refs/tags/v1.0.0-beta.2.tar.gz
                   tar -xzf v1.0.0-beta.2.tar.gz
                   cd NoteDaemon-1.0.0-beta.2
                   mkdir build && cd build
                   cmake -DCMAKE_BUILD_TYPE=Release ..
                   make -j$(nproc)
                   sudo make install
                
                3. Configure service:
                   sudo systemctl enable note-daemon.service
                   sudo systemctl start note-daemon.service
                
                4. Add your user to netnotes group:
                   sudo usermod -a -G netnotes $USER
                   newgrp netnotes
                
                More info: https://github.com/networkspore/NoteDaemon/releases
                
                After installation, restart this wizard to detect NoteDaemon.
                """;
        } else if (os.contains("mac")) {
            return """
                NoteDaemon Installation Instructions (macOS)
                =============================================
                
                1. Install Homebrew (if not installed):
                   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
                
                2. Install dependencies:
                   brew install cmake boost libusb openssl
                
                3. Download and build:
                   git clone https://github.com/networkspore/NoteDaemon.git
                   cd NoteDaemon
                   mkdir build && cd build
                   cmake -DCMAKE_BUILD_TYPE=Release ..
                   make -j$(sysctl -n hw.ncpu)
                   sudo make install
                
                4. Start the daemon:
                   sudo note-daemon --socket /var/run/io-daemon.sock
                
                More info: https://github.com/networkspore/NoteDaemon/releases
                """;
        } else {
            return """
                NoteDaemon Installation
                =======================
                
                Visit: https://github.com/networkspore/NoteDaemon/releases
                
                Download and follow the installation instructions for your platform.
                """;
        }
    }
    
    private String getQuickInstallCommand(String os) {
        if (os.contains("linux")) {
            return "curl -fsSL https://raw.githubusercontent.com/networkspore/NoteDaemon/master/download-install.sh | sudo bash";
        } else if (os.contains("mac")) {
            return "brew install cmake boost libusb openssl && git clone https://github.com/networkspore/NoteDaemon.git";
        }
        return "";
    }
    
    private void copyToClipboard(String text) {
        try {
            java.awt.Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
        } catch (Exception e) {
            Log.logError("Could not copy to clipboard: " + e.getMessage());
        }
    }
    
    public NoteBytesMap getBootstrapConfig() {
        return bootstrapConfig;
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException();
    }
    
    // ===== CLEANUP =====
    
    @Override
    public void onStop() {
        // Unregister keyboard consumer if still registered
        if (keyboard != null && waitingForKey) {
            keyboard.setEventConsumer(null);
        }
        
        super.onStop();
    }
    
    // ===== RESULT HOLDER =====
    
    public static class SecureInputDetectionResult {
        public boolean socketExists = false;
        public boolean canConnect = false;
        public int keyboardCount = 0;
        public String os;
        public boolean canInstall = false;
    }
}