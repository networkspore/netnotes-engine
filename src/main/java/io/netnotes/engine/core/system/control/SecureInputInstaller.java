package io.netnotes.engine.core.system.control;

import io.netnotes.engine.core.system.control.ui.UIProtocol;
import io.netnotes.engine.core.system.control.ui.UIRenderer;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.state.BitFlagStateMachine;
import io.netnotes.engine.utils.github.GitHubAPI;
import io.netnotes.engine.utils.github.GitHubAsset;
import io.netnotes.engine.utils.github.GitHubInfo;
import io.netnotes.engine.utils.streams.UrlStreamHelpers;
import io.netnotes.engine.utils.VirtualExecutors;

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
 * Flow:
 * 1. Show release selection menu
 * 2. User selects version
 * 3. Confirm installation
 * 4. Execute installation with progress updates
 */
public class SecureInputInstaller extends FlowProcess {
    
    private static final GitHubInfo GITHUB_INFO = new GitHubInfo("networkspore", "NoteDaemon");
    
    private final String os;
    private final UIRenderer uiRenderer;
    private final BitFlagStateMachine state;
    
    private MenuNavigatorProcess menuNavigator;
    private GitHubAsset[] availableReleases;
    private GitHubAsset selectedAsset;
    private String selectedVersion;
    
    private Path workDir;
    private Path extractedDir;
    private Path buildDir;
    
    private int currentStep = 0;
    private int totalSteps = 14;
    
    public SecureInputInstaller(String os, UIRenderer uiRenderer) {
        super(ProcessType.SINK);
        this.os = os;
        this.uiRenderer = uiRenderer;
        this.state = new BitFlagStateMachine("installer");
    }
    
    @Override
    public CompletableFuture<Void> run() {
        // Create menu navigator
        menuNavigator = new MenuNavigatorProcess(uiRenderer);
        
        return spawnChild(menuNavigator, "installer-menu")
            .thenCompose(path -> registry.startProcess(path))
            .thenCompose(v -> fetchReleases())
            .thenCompose(assets -> {
                this.availableReleases = assets;
                return showReleaseSelectionMenu();
            })
            .thenCompose(v -> getCompletionFuture());
    }
    
    // ===== MENU CONSTRUCTION =====
    
    private CompletableFuture<GitHubAsset[]> fetchReleases() {
        uiRenderer.render(UIProtocol.showMessage("Fetching available releases from GitHub..."));
        
        GitHubAPI api = new GitHubAPI(GITHUB_INFO);
        
        return api.getAssetsAllLatestRelease(VirtualExecutors.getVirtualExecutor())
            .thenApply(assets -> {
                // Filter for tar.gz files only
                return java.util.Arrays.stream(assets)
                    .filter(asset -> asset.getName().endsWith(".tar.gz"))
                    .filter(asset -> !asset.getName().contains("checksums"))
                    .toArray(GitHubAsset[]::new);
            });
    }
    
    private CompletableFuture<Void> showReleaseSelectionMenu() {
        ContextPath menuPath = contextPath.append("release-selection");
        MenuContext menu = new MenuContext(menuPath, "Select NoteDaemon Release", uiRenderer);
        
        // Add menu items for each release
        for (int i = 0; i < availableReleases.length; i++) {
            GitHubAsset asset = availableReleases[i];
            String itemName = "release-" + i;
            String description = buildReleaseDescription(asset);
            
            menu.addItem(itemName, description, () -> {
                onReleaseSelected(asset);
            });
        }
        
        // Add cancel option
        menu.addItem("cancel", "Cancel Installation", () -> {
            uiRenderer.render(UIProtocol.showMessage("Installation cancelled"));
            complete();
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
        
        return String.format("%s | Tag: %s | %s | %s | %d downloads",
            asset.getName(),
            asset.getTagName(),
            sizeStr,
            dateStr,
            asset.getDownloadCount()
        );
    }
    
    private void onReleaseSelected(GitHubAsset asset) {
        this.selectedAsset = asset;
        this.selectedVersion = extractVersionFromAsset(asset);
        
        // Show confirmation menu
        showConfirmationMenu();
    }
    
    private void showConfirmationMenu() {
        ContextPath menuPath = contextPath.append("confirm-installation");
        MenuContext menu = new MenuContext(menuPath, "Confirm Installation", uiRenderer);
        
        String message = String.format(
            "Ready to install NoteDaemon %s\n\n" +
            "File: %s\n" +
            "Size: %s\n\n" +
            "This will:\n" +
            "- Install system dependencies\n" +
            "- Download and build NoteDaemon\n" +
            "- Create system user and group\n" +
            "- Install systemd service\n" +
            "- Configure udev rules\n\n" +
            "Root access required.\n\n" +
            "Continue?",
            selectedVersion,
            selectedAsset.getName(),
            formatSize(selectedAsset.getSize())
        );
        
        uiRenderer.render(UIProtocol.showMessage(message));
        
        menu.addItem("install", "Install Now", () -> {
            startInstallation();
        });
        
        menu.addItem("cancel", "Cancel", () -> {
            // Go back to release selection
            showReleaseSelectionMenu();
        });
        
        menuNavigator.showMenu(menu);
    }
    
    private void startInstallation() {
        uiRenderer.render(UIProtocol.showMessage("Starting installation..."));
        
        performInstallation()
            .thenRun(() -> {
                showCompletionMenu(true, null);
            })
            .exceptionally(ex -> {
                showCompletionMenu(false, ex.getMessage());
                return null;
            });
    }
    
    private void showCompletionMenu(boolean success, String errorMessage) {
        ContextPath menuPath = contextPath.append("completion");
        MenuContext menu = new MenuContext(menuPath, "Installation Complete", uiRenderer);
        
        if (success) {
            String message = String.format(
                "NoteDaemon %s installed successfully!\n\n" +
                "Service Status: Active\n" +
                "Socket: /var/run/io-daemon.sock\n\n" +
                "You may need to log out and back in for group membership to take effect.\n" +
                "Or run: newgrp netnotes",
                selectedVersion
            );
            
            uiRenderer.render(UIProtocol.showMessage(message));
            
            menu.addItem("finish", "Finish", () -> {
                complete();
            });
            
        } else {
            String message = String.format(
                "Installation failed!\n\n" +
                "Error: %s\n\n" +
                "Please check the logs for details.",
                errorMessage != null ? errorMessage : "Unknown error"
            );
            
            uiRenderer.render(UIProtocol.showError(message));
            
            menu.addItem("retry", "Retry", () -> {
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
        updateProgress("Checking prerequisites...", 1);
        checkRootAccess();
        
        updateProgress("Installing dependencies...", 2);
        installLinuxDependencies();
        
        updateProgress("Setting up work directory...", 3);
        setupWorkDirectory();
        
        updateProgress("Downloading NoteDaemon " + selectedVersion + "...", 4);
        downloadSelectedRelease();
        
        updateProgress("Extracting archive...", 5);
        extractArchive();
        
        updateProgress("Creating system user and group...", 6);
        createSystemUser();
        
        updateProgress("Creating runtime directories...", 7);
        createRuntimeDirectories();
        
        updateProgress("Building NoteDaemon...", 8);
        buildProject();
        
        updateProgress("Installing binary...", 9);
        installBinary();
        
        updateProgress("Installing udev rules...", 10);
        installUdevRules();
        
        updateProgress("Installing systemd service...", 11);
        installSystemdService();
        
        updateProgress("Starting service...", 12);
        startService();
        
        updateProgress("Configuring user permissions...", 13);
        configureUserPermissions();
        
        updateProgress("Cleaning up...", 14);
        cleanup();
        
        updateProgress("Installation complete!", 14);
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
                System.err.println("Could not add user to group: " + e.getMessage());
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
        currentStep = step;
        int percent = totalSteps > 0 ? (step * 100) / totalSteps : 0;
        
        System.out.println("[" + step + "/" + totalSteps + "] " + message);
        uiRenderer.render(UIProtocol.showProgress(message, percent));
    }
    
    private void executeCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("  " + line);
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
                System.out.println("  " + line);
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
                        System.err.println("Could not delete: " + child);
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
}