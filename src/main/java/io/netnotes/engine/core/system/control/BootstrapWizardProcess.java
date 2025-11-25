package io.netnotes.engine.core.system.control;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.core.system.BootstrapConfig;
import io.netnotes.engine.core.system.control.ui.*;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

import java.util.concurrent.CompletableFuture;

/**
 * BootstrapWizardProcess - Menu-driven first-run configuration
 * 
 * Flow:
 * 1. Welcome Screen
 * 2. Detect secure input
 * 3. Present installation options as menu
 * 4. Execute selected option
 * 5. Save configuration
 */
public class BootstrapWizardProcess extends FlowProcess {
    
    private final BitFlagStateMachine state;
    private final UIRenderer uiRenderer;
    
    private MenuNavigatorProcess menuNavigator;
    private NoteBytesMap bootstrapConfig;
    private SecureInputDetectionResult detectionResult;
    
    // States
    public static final long DETECTING = 1L << 0;
    public static final long CONFIGURING = 1L << 1;
    public static final long INSTALLING = 1L << 2;
    public static final long VERIFYING = 1L << 3;
    public static final long COMPLETE = 1L << 4;
    
    public BootstrapWizardProcess(UIRenderer uiRenderer) {
        super(ProcessType.BIDIRECTIONAL);
        this.uiRenderer = uiRenderer;
        this.state = new BitFlagStateMachine("bootstrap-wizard");
    }
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(DETECTING);
        
        // Create default config
        bootstrapConfig = BootstrapConfig.createDefault();
        
        // Create menu navigator
        menuNavigator = new MenuNavigatorProcess(uiRenderer);
        
        return spawnChild(menuNavigator, "wizard-menu")
            .thenCompose(path -> registry.startProcess(path))
            .thenCompose(v -> showWelcomeScreen())
            .thenCompose(v -> detectSecureInput())
            .thenCompose(result -> {
                this.detectionResult = result;
                state.removeState(DETECTING);
                state.addState(CONFIGURING);
                return showSecureInputOptionsMenu();
            })
            .thenCompose(v -> getCompletionFuture());
    }
    
    // ===== WELCOME =====
    
    private CompletableFuture<Void> showWelcomeScreen() {
        NoteBytesMap welcome = UIProtocol.showMessage(
            "Welcome to Netnotes!\n\n" +
            "This wizard will help you configure your system.\n\n" +
            "First, we'll check if you have secure input (NoteDaemon) installed."
        );
        
        return uiRenderer.render(welcome)
            .thenApply(response -> null);
    }
    
    // ===== DETECTION =====
    
    private CompletableFuture<SecureInputDetectionResult> detectSecureInput() {
        uiRenderer.render(UIProtocol.showProgress("Detecting secure input...", 50));
        
        return CompletableFuture.supplyAsync(() -> {
            SecureInputDetectionResult result = new SecureInputDetectionResult();
            
            String socketPath = BootstrapConfig.getSecureInputSocketPath(bootstrapConfig);
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
        });
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
        MenuContext menu = new MenuContext(menuPath, "Secure Input Detected", uiRenderer);
        
        String message = String.format(
            "NoteDaemon is already installed!\n\n" +
            "Found %d USB keyboard(s) available.\n\n" +
            "Secure input allows password entry directly from USB keyboards,\n" +
            "providing protection against keyloggers and screen capture.",
            detectionResult.keyboardCount
        );
        
        uiRenderer.render(UIProtocol.showMessage(message));
        
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
        MenuContext menu = new MenuContext(menuPath, "Secure Input Installation", uiRenderer);
        
        String message = 
            "NoteDaemon is not installed.\n\n" +
            "Secure input provides hardware-level protection for password entry.\n\n" +
            "You can:\n" +
            "- Install it now (requires root access)\n" +
            "- View manual installation instructions\n" +
            "- Skip and use GUI keyboard only";
        
        uiRenderer.render(UIProtocol.showMessage(message));
        
        if (detectionResult.canInstall) {
            menu.addItem("auto-install", "Install Now (Recommended)", () -> {
                startAutomatedInstallation();
            });
        }
        
        menu.addItem("manual", "Show Manual Installation Instructions", () -> {
            showManualInstructions();
        });
        
        menu.addItem("skip", "Skip (Use GUI Keyboard Only)", () -> {
            skipSecureInput();
        });
        
        menu.addItem("advanced", "Advanced Configuration", () -> {
            showAdvancedConfigMenu();
        });
        
        menuNavigator.showMenu(menu);
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Manual installation instructions
     */
    private void showManualInstructions() {
        ContextPath menuPath = contextPath.append("manual-instructions");
        MenuContext menu = new MenuContext(menuPath, "Manual Installation", uiRenderer);
        
        String instructions = generateInstallInstructions(detectionResult.os);
        uiRenderer.render(UIProtocol.showMessage(instructions));
        
        menu.addItem("done", "Done Reading", () -> {
            showInstallationOptionsMenu();
        });
        
        menu.addItem("copy-command", "Copy Quick Install Command", () -> {
            copyToClipboard(getQuickInstallCommand(detectionResult.os));
            uiRenderer.render(UIProtocol.showMessage("Command copied to clipboard!"));
        });
        
        menuNavigator.showMenu(menu);
    }
    
    /**
     * Advanced configuration menu
     */
    private void showAdvancedConfigMenu() {
        ContextPath menuPath = contextPath.append("advanced-config");
        MenuContext menu = new MenuContext(menuPath, "Advanced Configuration", uiRenderer);
        
        menu.addItem("socket-path", "Custom Socket Path", () -> {
            promptForSocketPath();
        });
        
        menu.addItem("network", "Enable Network Services", () -> {
            toggleNetworkServices();
        });
        
        menu.addItem("back", "Back to Main Options", () -> {
            showInstallationOptionsMenu();
        });
        
        menuNavigator.showMenu(menu);
    }
    
    // ===== ACTIONS =====
    
    private void enableSecureInput() {
        BootstrapConfig.setSecureInputInstalled(bootstrapConfig, true);
        BootstrapConfig.setInputSourceEnabled(bootstrapConfig, "secure-input", true);
        
        uiRenderer.render(UIProtocol.showMessage(
            "Secure input enabled!\n\n" +
            "Your passwords will be captured directly from USB keyboards."
        ));
        
        finishConfiguration();
    }
    
    private void skipSecureInput() {
        BootstrapConfig.setSecureInputInstalled(bootstrapConfig, false);
        BootstrapConfig.setInputSourceEnabled(bootstrapConfig, "secure-input", false);
        
        uiRenderer.render(UIProtocol.showMessage(
            "Secure input disabled.\n\n" +
            "You can install NoteDaemon later through system settings."
        ));
        
        finishConfiguration();
    }
    
    private void startAutomatedInstallation() {
        state.removeState(CONFIGURING);
        state.addState(INSTALLING);
        
        String os = detectionResult.os;
        SecureInputInstaller installer = new SecureInputInstaller(os, uiRenderer);
        
        spawnChild(installer, "secure-input-installer")
            .thenCompose(path -> registry.startProcess(path))
            .thenCompose(v -> installer.getCompletionFuture())
            .thenCompose(v -> {
                state.removeState(INSTALLING);
                state.addState(VERIFYING);
                return verifyInstallation();
            })
            .thenAccept(success -> {
                state.removeState(VERIFYING);
                state.addState(CONFIGURING);
                
                if (success) {
                    BootstrapConfig.setSecureInputInstalled(bootstrapConfig, true);
                    BootstrapConfig.setInputSourceEnabled(bootstrapConfig, "secure-input", true);
                    
                    uiRenderer.render(UIProtocol.showMessage(
                        "Installation successful!\n\n" +
                        "NoteDaemon is now running."
                    ));
                    
                    finishConfiguration();
                } else {
                    uiRenderer.render(UIProtocol.showError(
                        "Installation verification failed.\n\n" +
                        "You may need to install manually."
                    ));
                    
                    showInstallationOptionsMenu();
                }
            })
            .exceptionally(ex -> {
                state.removeState(INSTALLING);
                state.addState(CONFIGURING);
                
                uiRenderer.render(UIProtocol.showError(
                    "Installation failed: " + ex.getMessage()
                ));
                
                showInstallationOptionsMenu();
                return null;
            });
    }
    
    private CompletableFuture<Boolean> verifyInstallation() {
        uiRenderer.render(UIProtocol.showProgress("Verifying installation...", 80));
        
        return CompletableFuture.supplyAsync(() -> {
            String socketPath = BootstrapConfig.getSecureInputSocketPath(bootstrapConfig);
            
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
        // This would show a text input dialog
        // For now, just show message
        uiRenderer.render(UIProtocol.showMessage(
            "Socket path configuration not yet implemented.\n\n" +
            "Default: /var/run/io-daemon.sock"
        ));
        
        showAdvancedConfigMenu();
    }
    
    private void toggleNetworkServices() {
        boolean current = BootstrapConfig.isNetworkEnabled(bootstrapConfig);
        BootstrapConfig.set(bootstrapConfig, 
            BootstrapConfig.SYSTEM + "/" + BootstrapConfig.NETWORK + "/" + BootstrapConfig.ENABLED,
            new io.netnotes.engine.noteBytes.NoteBytes(!current)
        );
        
        uiRenderer.render(UIProtocol.showMessage(
            "Network services " + (!current ? "enabled" : "disabled")
        ));
        
        showAdvancedConfigMenu();
    }
    
    private void finishConfiguration() {
        state.removeState(CONFIGURING);
        state.addState(COMPLETE);
        
        SettingsData.saveBootstrapConfig(bootstrapConfig)
            .thenAccept(v -> {
                uiRenderer.render(UIProtocol.showMessage(
                    "Configuration saved!\n\n" +
                    "Your system is now configured."
                ));
                
                complete();
            })
            .exceptionally(ex -> {
                uiRenderer.render(UIProtocol.showError(
                    "Failed to save configuration: " + ex.getMessage()
                ));
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
        // Platform-specific clipboard implementation
        try {
            java.awt.Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(text), null);
        } catch (Exception e) {
            System.err.println("Could not copy to clipboard: " + e.getMessage());
        }
    }
    
    public NoteBytesMap getBootstrapConfig() {
        return bootstrapConfig;
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Menu navigator handles all messages
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException();
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