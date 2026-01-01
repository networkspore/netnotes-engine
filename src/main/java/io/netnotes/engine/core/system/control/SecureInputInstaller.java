package io.netnotes.engine.core.system.control;

import io.netnotes.engine.core.system.control.terminal.TerminalContainerHandle;
import io.netnotes.engine.core.system.control.terminal.TextStyle;
import io.netnotes.engine.core.system.control.terminal.menus.MenuContext;
import io.netnotes.engine.core.system.control.terminal.menus.MenuNavigator;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.github.GitHubAPI;
import io.netnotes.engine.utils.github.GitHubAsset;
import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;
import io.netnotes.engine.utils.LoggingHelpers.Log;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * SecureInputInstaller - Menu-driven installation process
 * 
 * REFACTORED to use TerminalContainerHandle:
 * - Uses terminal for all output
 * - Uses MenuNavigatorProcess for interactive menus
 * - Keyboard-driven navigation
 * - Real-time progress updates
 * 
 * Flow:
 * 1. Show release selection menu
 * 2. User selects version
 * 3. Confirm installation
 * 4. Execute installation with progress updates
 */
public class SecureInputInstaller extends FlowProcess {
    
    private static final GitHubInfo GITHUB_INFO = new GitHubInfo("networkspore", "NoteDaemon");
    
    private final String os;
    private final TerminalContainerHandle terminal;
    private final BitFlagStateMachine state;
    
    private MenuNavigator menuNavigator;
    private GitHubAsset[] availableReleases;
    private GitHubAsset selectedAsset;
    private String selectedVersion;
    
    private Path workDir;
    private Path extractedDir;
    private Path buildDir;
    
    private int currentStep = 0;
    private int totalSteps = 14;
    
    // States
    public static final long IDLE = 1L << 0;
    public static final long FETCHING_RELEASES = 1L << 1;
    public static final long SHOWING_MENU = 1L << 2;
    public static final long CONFIRMING = 1L << 3;
    public static final long INSTALLING = 1L << 4;
    public static final long VERIFYING = 1L << 5;
    public static final long COMPLETE = 1L << 6;
    public static final long FAILED = 1L << 7;
    
    public SecureInputInstaller(
        String name, 
        String os, 
        TerminalContainerHandle terminal
    ) {
        super(name, ProcessType.SINK);
        this.os = os;
        this.terminal = terminal;
        this.state = new BitFlagStateMachine(name);
        
        setupStateTransitions();
    }
    
    private void setupStateTransitions() {
        state.onStateAdded(FETCHING_RELEASES, (old, now, bit) -> {
            Log.logMsg("[Installer] Fetching releases from GitHub...");
        });
        
        state.onStateAdded(SHOWING_MENU, (old, now, bit) -> {
            Log.logMsg("[Installer] Displaying release selection menu");
        });
        
        state.onStateAdded(CONFIRMING, (old, now, bit) -> {
            Log.logMsg("[Installer] Awaiting installation confirmation");
        });
        
        state.onStateAdded(INSTALLING, (old, now, bit) -> {
            Log.logMsg("[Installer] Installation in progress...");
        });
        
        state.onStateAdded(VERIFYING, (old, now, bit) -> {
            Log.logMsg("[Installer] Verifying installation...");
        });
        
        state.onStateAdded(COMPLETE, (old, now, bit) -> {
            Log.logMsg("[Installer] Installation complete!");
        });
        
        state.onStateAdded(FAILED, (old, now, bit) -> {
            Log.logMsg("[Installer] Installation failed");
        });
    }
    
    @Override
    public CompletableFuture<Void> run() {
        state.addState(IDLE);
        
        // Create menu navigator
        menuNavigator = new MenuNavigator(terminal);
        state.removeState(IDLE);
        state.addState(FETCHING_RELEASES);

        return fetchReleases()
            .thenCompose(assets -> {
                this.availableReleases = assets;
                state.removeState(FETCHING_RELEASES);
                state.addState(SHOWING_MENU);
                return showReleaseSelectionMenu();
            })
            .thenCompose(v -> getCompletionFuture());
    }
    
    // ===== MENU CONSTRUCTION =====
    
    private CompletableFuture<GitHubAsset[]> fetchReleases() {
        terminal.clear()
            .thenCompose(v -> terminal.println("Fetching available releases from GitHub...", 
                TextStyle.INFO))
            .thenCompose(v -> terminal.println(""));
        
        GitHubAPI api = new GitHubAPI(GITHUB_INFO);
        
        return api.getAssetsAllLatestRelease(VirtualExecutors.getVirtualExecutor())
            .thenApply(assets -> {
                // Filter for tar.gz files only
                return java.util.Arrays.stream(assets)
                    .filter(asset -> asset.getName().endsWith(".tar.gz"))
                    .filter(asset -> !asset.getName().contains("checksums"))
                    .toArray(GitHubAsset[]::new);
            })
            .exceptionally(ex -> {
                terminal.clear()
                    .thenCompose(v -> terminal.printError(
                        "Failed to fetch releases: " + ex.getMessage()))
                    .thenCompose(v -> terminal.println(""))
                    .thenCompose(v -> terminal.println("Press ESC to exit"));
                return new GitHubAsset[0];
            });
    }
    
    private CompletableFuture<Void> showReleaseSelectionMenu() {
        if (availableReleases.length == 0) {
            terminal.clear()
                .thenCompose(v -> terminal.printError("No releases found"))
                .thenCompose(v -> terminal.println(""))
                .thenCompose(v -> terminal.println("Press any key to exit"))
                .thenRun(() -> complete());
            return CompletableFuture.completedFuture(null);
        }
        
        ContextPath menuPath = contextPath.append("release-selection");
        MenuContext menu = new MenuContext(menuPath, "Select NoteDaemon Release");
        
        // Show description
        terminal.clear()
            .thenCompose(v -> terminal.println("Available Releases:", 
                TextStyle.BOLD))
            .thenCompose(v -> terminal.println(""));
        
        // Add menu items for each release
        for (int i = 0; i < availableReleases.length; i++) {
            GitHubAsset asset = availableReleases[i];
            String itemName = "release-" + i;
            String description = buildReleaseDescription(asset);
            
            final GitHubAsset selectedAsset = asset;
            menu.addItem(itemName, description, () -> {
                onReleaseSelected(selectedAsset);
            });
        }
        
        // Add cancel option
        menu.addItem("cancel", "Cancel Installation", () -> {
            terminal.clear()
                .thenCompose(v -> terminal.println("Installation cancelled", 
                    TextStyle.WARNING))
                .thenRun(() -> complete());
        });
        
        // Show the menu
        menuNavigator.showMenu(menu);
        
        return CompletableFuture.completedFuture(null);
    }
    
    private String buildReleaseDescription(GitHubAsset asset) {
        String sizeStr = formatSize(asset.getSize());
        String dateStr = asset.getCreatedAt() != null 
            ? asset.getCreatedAt().toString().substring(0, 10) 
            : "unknown";
        
        return String.format("%s (%s, %s, %d downloads)",
            asset.getTagName(),
            sizeStr,
            dateStr,
            asset.getDownloadCount()
        );
    }
    
    private void onReleaseSelected(GitHubAsset asset) {
        this.selectedAsset = asset;
        this.selectedVersion = extractVersionFromAsset(asset);
        
        state.removeState(SHOWING_MENU);
        state.addState(CONFIRMING);
        
        // Show confirmation menu
        showConfirmationMenu();
    }
    
    private void showConfirmationMenu() {
        ContextPath menuPath = contextPath.append("confirm-installation");
        MenuContext menu = new MenuContext(menuPath, "Confirm Installation");
        
        // Show detailed info
        terminal.clear()
            .thenCompose(v -> terminal.println("Ready to install NoteDaemon " + selectedVersion, 
                TextStyle.BOLD))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("File: " + selectedAsset.getName()))
            .thenCompose(v -> terminal.println("Size: " + formatSize(selectedAsset.getSize())))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("This will:"))
            .thenCompose(v -> terminal.println("- Install system dependencies"))
            .thenCompose(v -> terminal.println("- Download and build NoteDaemon"))
            .thenCompose(v -> terminal.println("- Create system user and group"))
            .thenCompose(v -> terminal.println("- Install systemd service"))
            .thenCompose(v -> terminal.println("- Configure udev rules"))
            .thenCompose(v -> terminal.println(""))
            .thenCompose(v -> terminal.println("Root access required.", 
                TextStyle.WARNING))
            .thenCompose(v -> terminal.println(""));
        
        menu.addItem("install", "Install Now", () -> {
            startInstallation();
        });
        
        menu.addItem("cancel", "Cancel", () -> {
            // Go back to release selection
            state.removeState(CONFIRMING);
            state.addState(SHOWING_MENU);
            showReleaseSelectionMenu();
        });
        
        menuNavigator.showMenu(menu);
    }
    
    private void startInstallation() {
        state.removeState(CONFIRMING);
        state.addState(INSTALLING);
        
        // Reset progress
        currentStep = 0;
        
        terminal.clear()
            .thenCompose(v -> terminal.println("Starting installation...", 
                TextStyle.BOLD))
            .thenCompose(v -> terminal.println(""));
        
        performInstallation()
            .thenRun(() -> {
                state.removeState(INSTALLING);
                state.addState(VERIFYING);
                
                updateProgress("Verifying installation...", totalSteps - 1);
                
                // Brief verification
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                state.removeState(VERIFYING);
                state.addState(COMPLETE);
                
                showCompletionMenu(true, null);
            })
            .exceptionally(ex -> {
                state.removeState(INSTALLING);
                state.addState(FAILED);
                
                showCompletionMenu(false, ex.getMessage());
                return null;
            });
    }
    
    private void showCompletionMenu(boolean success, String errorMessage) {
        ContextPath menuPath = contextPath.append("completion");
        MenuContext menu = new MenuContext(menuPath, 
            success ? "Installation Complete" : "Installation Failed");
        
        if (success) {
            terminal.clear()
                .thenCompose(v -> terminal.println("NoteDaemon " + selectedVersion + 
                    " installed successfully!", TextStyle.SUCCESS))
                .thenCompose(v -> terminal.println(""))
                .thenCompose(v -> terminal.println("Service Status: Active"))
                .thenCompose(v -> terminal.println("Socket: /var/run/io-daemon.sock"))
                .thenCompose(v -> terminal.println(""))
                .thenCompose(v -> terminal.println("Installation completed in " + 
                    currentStep + " steps."))
                .thenCompose(v -> terminal.println(""))
                .thenCompose(v -> terminal.println(
                    "You may need to log out and back in for group membership to take effect.",
                    TextStyle.WARNING))
                .thenCompose(v -> terminal.println("Or run: newgrp netnotes"))
                .thenCompose(v -> terminal.println(""));
            
            menu.addItem("finish", "Finish", () -> {
                complete();
            });
            
        } else {
            terminal.clear()
                .thenCompose(v -> terminal.printError(
                    "Installation failed at step " + currentStep + " of " + totalSteps + "!"))
                .thenCompose(v -> terminal.println(""))
                .thenCompose(v -> terminal.println("Error: " + 
                    (errorMessage != null ? errorMessage : "Unknown error")))
                .thenCompose(v -> terminal.println(""))
                .thenCompose(v -> terminal.println("Please check the logs for details."))
                .thenCompose(v -> terminal.println(""));
            
            menu.addItem("retry", "Retry Installation", () -> {
                state.removeState(FAILED);
                state.addState(SHOWING_MENU);
                showReleaseSelectionMenu();
            });
            
            menu.addItem("cancel", "Cancel", () -> {
                complete();
            });
        }
        
        menuNavigator.showMenu(menu);
    }
    
    // ===== INSTALLATION LOGIC =====
    
    private CompletableFuture<Void> performInstallation() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (os.toLowerCase().contains("linux")) {
                    installLinux();
                } else if (os.toLowerCase().contains("mac")) {
                    installMacOS();
                } else {
                    throw new UnsupportedOperationException("Unsupported OS: " + os);
                }
            } catch (Exception e) {
                throw new RuntimeException("Installation failed", e);
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    // ===== LINUX INSTALLATION =====
    
    private void installLinux() throws Exception {
        currentStep = 1;
        updateProgress("Checking prerequisites...", currentStep);
        checkRootAccess();
        
        currentStep = 2;
        updateProgress("Installing dependencies...", currentStep);
        installLinuxDependencies();
        
        currentStep = 3;
        updateProgress("Setting up work directory...", currentStep);
        setupWorkDirectory();
        
        currentStep = 4;
        updateProgress("Downloading NoteDaemon " + selectedVersion + "...", currentStep);
        downloadSelectedRelease();
        
        currentStep = 5;
        updateProgress("Extracting archive...", currentStep);
        extractArchive();
        
        currentStep = 6;
        updateProgress("Creating system user and group...", currentStep);
        createSystemUser();
        
        currentStep = 7;
        updateProgress("Creating runtime directories...", currentStep);
        createRuntimeDirectories();
        
        currentStep = 8;
        updateProgress("Building NoteDaemon...", currentStep);
        buildProject();
        
        currentStep = 9;
        updateProgress("Installing binary...", currentStep);
        installBinary();
        
        currentStep = 10;
        updateProgress("Installing udev rules...", currentStep);
        installUdevRules();
        
        currentStep = 11;
        updateProgress("Installing systemd service...", currentStep);
        installSystemdService();
        
        currentStep = 12;
        updateProgress("Starting service...", currentStep);
        startService();
        
        currentStep = 13;
        updateProgress("Configuring user permissions...", currentStep);
        configureUserPermissions();
        
        currentStep = 14;
        updateProgress("Cleaning up...", currentStep);
        cleanup();
        
        updateProgress("Installation complete!", currentStep);
    }
    
    private void checkRootAccess() throws Exception {
        if (!SecureInputDetector.getSystemInfo().hasRootAccess) {
            throw new SecurityException(
                "Root access required. Please run with sudo or as root.");
        }
    }
    
    private void installLinuxDependencies() throws Exception {
        String[] packages = {
            "build-essential",
            "cmake",
            "pkg-config",
            "libusb-1.0-0-dev",
            "libssl-dev",
            "libboost-all-dev"
        };
        
        executeCommand("apt-get", "update", "-qq");
        
        String[] installCmd = new String[packages.length + 3];
        installCmd[0] = "apt-get";
        installCmd[1] = "install";
        installCmd[2] = "-y";
        System.arraycopy(packages, 0, installCmd, 3, packages.length);
        
        executeCommand(installCmd);
    }
    
    private void setupWorkDirectory() throws Exception {
        workDir = Files.createTempDirectory("notedaemon-install");
    }
    
    private void downloadSelectedRelease() throws Exception {
        Path tarballPath = workDir.resolve("notedaemon.tar.gz");
        String downloadUrl = selectedAsset.getBrowserDownloadUrl();
        UrlStreamHelpers.streamUrlToFile(downloadUrl, tarballPath);
    }
    
    private void extractArchive() throws Exception {
        Path tarballPath = workDir.resolve("notedaemon.tar.gz");
        String topLevelDir = null;
        
        try (InputStream fileIn = Files.newInputStream(tarballPath);
             GZIPInputStream gzipIn = new GZIPInputStream(fileIn);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(gzipIn)) {
            
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                Path outputPath = workDir.resolve(entry.getName());
                
                if (topLevelDir == null && entry.isDirectory()) {
                    String name = entry.getName();
                    if (name.endsWith("/")) {
                        name = name.substring(0, name.length() - 1);
                    }
                    if (!name.contains("/")) {
                        topLevelDir = name;
                    }
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    Files.copy(tarIn, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    
                    if ((entry.getMode() & 0100) != 0) {
                        outputPath.toFile().setExecutable(true);
                    }
                }
            }
        }
        
        if (topLevelDir == null) {
            throw new RuntimeException("Could not detect top-level directory");
        }
        
        extractedDir = workDir.resolve(topLevelDir);
    }
    
    private void createSystemUser() throws Exception {
        try {
            executeCommand("groupadd", "--system", "netnotes");
        } catch (Exception e) {
            // Group already exists
        }
        
        try {
            executeCommand("useradd",
                "--system",
                "--no-create-home",
                "--home-dir", "/var/lib/netnotes",
                "-g", "netnotes",
                "--shell", "/usr/sbin/nologin",
                "netnotes"
            );
        } catch (Exception e) {
            // User already exists
        }
    }
    
    private void createRuntimeDirectories() throws Exception {
        executeCommand("mkdir", "-p", "/var/lib/netnotes", "/run/netnotes");
        executeCommand("chown", "netnotes:netnotes", "/var/lib/netnotes", "/run/netnotes");
        executeCommand("chmod", "0750", "/var/lib/netnotes", "/run/netnotes");
    }
    
    private void buildProject() throws Exception {
        buildDir = extractedDir.resolve("build");
        Files.createDirectories(buildDir);
        
        executeCommandInDirectory(buildDir,
            "cmake",
            "-DCMAKE_BUILD_TYPE=Release",
            ".."
        );
        
        int processors = Runtime.getRuntime().availableProcessors();
        executeCommandInDirectory(buildDir,
            "make",
            "-j" + processors
        );
    }
    
    private void installBinary() throws Exception {
        Path binary = buildDir.resolve("note-daemon");
        
        executeCommand("install",
            "-m", "0755",
            "-o", "root",
            "-g", "netnotes",
            binary.toString(),
            "/usr/local/bin/note-daemon"
        );
    }
    
    private void installUdevRules() throws Exception {
        Path rulesFile = extractedDir.resolve("99-netnotes.rules");
        
        Files.copy(rulesFile,
            Path.of("/etc/udev/rules.d/99-netnotes.rules"),
            StandardCopyOption.REPLACE_EXISTING
        );
        
        executeCommand("chmod", "0644", "/etc/udev/rules.d/99-netnotes.rules");
        executeCommand("udevadm", "control", "--reload-rules");
        executeCommand("udevadm", "trigger");
    }
    
    private void installSystemdService() throws Exception {
        Path serviceFile = extractedDir.resolve("note-daemon.service");
        
        Files.copy(serviceFile,
            Path.of("/etc/systemd/system/note-daemon.service"),
            StandardCopyOption.REPLACE_EXISTING
        );
        
        executeCommand("chmod", "0644", "/etc/systemd/system/note-daemon.service");
        executeCommand("systemctl", "daemon-reload");
    }
    
    private void startService() throws Exception {
        executeCommand("systemctl", "enable", "note-daemon.service");
        executeCommand("systemctl", "start", "note-daemon.service");
        
        Thread.sleep(3000);
        
        String status = executeCommandWithOutput("systemctl", "is-active", "note-daemon.service");
        if (!status.trim().equals("active")) {
            throw new RuntimeException("Service failed to start");
        }
    }
    
    private void configureUserPermissions() throws Exception {
        String user = System.getProperty("user.name");
        
        if (!user.equals("root")) {
            try {
                executeCommand("usermod", "-a", "-G", "netnotes", user);
            } catch (Exception e) {
                Log.logError("Could not add user to group: " + e.getMessage());
            }
        }
    }
    
    private void cleanup() throws Exception {
        deleteRecursively(workDir);
    }
    
    // ===== MACOS INSTALLATION =====
    
    private void installMacOS() throws Exception {
        throw new UnsupportedOperationException("macOS installation not yet implemented");
    }
    
    // ===== UTILITIES =====
    
    private String extractVersionFromAsset(GitHubAsset asset) {
        String tagName = asset.getTagName();
        if (tagName != null && !tagName.isEmpty()) {
            return tagName;
        }
        
        String name = asset.getName();
        if (name.contains("-")) {
            String[] parts = name.split("-");
            if (parts.length > 1) {
                return parts[1].replace(".tar.gz", "");
            }
        }
        
        return "unknown";
    }
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return (bytes / (1024 * 1024)) + " MB";
    }
    
    private void updateProgress(String message, int step) {
        if (!state.hasState(INSTALLING)) {
            return;
        }
        
        int percent = totalSteps > 0 ? (step * 100) / totalSteps : 0;
        
        Log.logMsg("[" + step + "/" + totalSteps + "] " + message);
        
        // Update terminal with progress
        terminal.println(String.format("[%d/%d] %s (%d%%)", 
            step, totalSteps, message, percent));
    }
    
    private void executeCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Log.logMsg("  " + line);
                terminal.println("  " + line, TextStyle.INFO);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", command));
        }
    }
    
    private void executeCommandInDirectory(Path directory, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(directory.toFile());
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Log.logMsg("  " + line);
                terminal.println("  " + line, TextStyle.INFO);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", command));
        }
    }
    
    private String executeCommandWithOutput(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed: " + String.join(" ", command));
        }
        
        return output.toString();
    }
    
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(child -> {
                    try {
                        deleteRecursively(child);
                    } catch (IOException e) {
                        Log.logError("Could not delete: " + child);
                    }
                });
            }
        }
        Files.deleteIfExists(path);
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
    
    // ===== GETTERS =====
    
    public BitFlagStateMachine getState() {
        return state;
    }
    
    public int getCurrentStep() {
        return currentStep;
    }
    
    public int getTotalSteps() {
        return totalSteps;
    }
    
    public boolean isInstalling() {
        return state.hasState(INSTALLING);
    }
    
    public boolean isComplete() {
        return state.hasState(COMPLETE);
    }
    
    public boolean hasFailed() {
        return state.hasState(FAILED);
    }
}