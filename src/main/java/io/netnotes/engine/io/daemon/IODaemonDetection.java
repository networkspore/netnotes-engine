package io.netnotes.engine.io.daemon;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import io.netnotes.engine.utils.LoggingHelpers.Log;

/**
 * IODaemonDetection - OS-specific detection for IODaemon availability
 * 
 * Checks:
 * - Binary exists at expected location
 * - Service is installed (if applicable)
 * - Socket is accessible
 * - Process is running
 */
public class IODaemonDetection {
    
    public static final String OS = System.getProperty("os.name").toLowerCase();
    public static final boolean IS_WINDOWS = OS.contains("win");
    public static final boolean IS_MAC = OS.contains("mac");
    public static final boolean IS_LINUX = !IS_WINDOWS && !IS_MAC;
    
    // Standard installation paths
    private static final String LINUX_INSTALL_DIR = "/var/lib/netnotes";
    private static final String LINUX_RUNTIME_DIR = "/run/netnotes";
    private static final String LINUX_SOCKET_PATH = "/run/netnotes/io-daemon.sock";
    private static final String LINUX_BINARY = "/usr/local/bin/io-daemon";
    
    private static final String MAC_INSTALL_DIR = "/Library/Application Support/Netnotes";
    private static final String MAC_RUNTIME_DIR = "/var/run/netnotes";
    private static final String MAC_SOCKET_PATH = "/var/run/netnotes/io-daemon.sock";
    private static final String MAC_BINARY = "/usr/local/bin/io-daemon";
    
    private static final String WIN_INSTALL_DIR = System.getenv("ProgramData") + "\\Netnotes";
    private static final String WIN_RUNTIME_DIR = System.getenv("ProgramData") + "\\Netnotes\\run";
    private static final String WIN_SOCKET_PATH = WIN_RUNTIME_DIR + "\\io-daemon.sock";
    private static final String WIN_BINARY = WIN_INSTALL_DIR + "\\io-daemon.exe";
    
    /**
     * Detection result
     */
    public static class DetectionResult {
        public final boolean binaryExists;
        public final boolean serviceInstalled;
        public final boolean processRunning;
        public final boolean socketAccessible;
        public final String binaryPath;
        public final String socketPath;
        public final String installationPath;
        public final String errorMessage;
        
        public DetectionResult(
                boolean binaryExists,
                boolean serviceInstalled,
                boolean processRunning,
                boolean socketAccessible,
                String binaryPath,
                String socketPath,
                String installationPath,
                String errorMessage) {
            this.binaryExists = binaryExists;
            this.serviceInstalled = serviceInstalled;
            this.processRunning = processRunning;
            this.socketAccessible = socketAccessible;
            this.binaryPath = binaryPath;
            this.socketPath = socketPath;
            this.installationPath = installationPath;
            this.errorMessage = errorMessage;
        }
        
        public boolean isAvailable() {
            return binaryExists && (processRunning || serviceInstalled);
        }
        
        public boolean isFullyOperational() {
            return binaryExists && processRunning && socketAccessible;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("IODaemon Detection:\n");
            sb.append("  Binary exists: ").append(binaryExists ? "✓" : "✗").append("\n");
            sb.append("  Service installed: ").append(serviceInstalled ? "✓" : "✗").append("\n");
            sb.append("  Process running: ").append(processRunning ? "✓" : "✗").append("\n");
            sb.append("  Socket accessible: ").append(socketAccessible ? "✓" : "✗").append("\n");
            if (binaryPath != null) {
                sb.append("  Binary: ").append(binaryPath).append("\n");
            }
            if (socketPath != null) {
                sb.append("  Socket: ").append(socketPath).append("\n");
            }
            if (errorMessage != null) {
                sb.append("  Error: ").append(errorMessage).append("\n");
            }
            return sb.toString();
        }
    }
    
    /**
     * Detect IODaemon availability
     */
    public static CompletableFuture<DetectionResult> detect(ExecutorService executor) {
        return CompletableFuture.supplyAsync(() -> {
            if (IS_LINUX) {
                return detectLinux();
            } else if (IS_MAC) {
                return detectMac();
            } else if (IS_WINDOWS) {
                return detectWindows();
            } else {
                return new DetectionResult(
                    false, false, false, false,
                    null, null, null,
                    "Unsupported operating system: " + OS
                );
            }
        }, executor);
    }
    
    // ===== LINUX DETECTION =====
    
    private static DetectionResult detectLinux() {
        String binaryPath = LINUX_BINARY;
        String socketPath = LINUX_SOCKET_PATH;
        String installPath = LINUX_INSTALL_DIR;
        
        // Check binary exists
        boolean binaryExists = Files.exists(Path.of(binaryPath)) && 
                              Files.isExecutable(Path.of(binaryPath));
        
        // Check systemd service
        boolean serviceInstalled = checkLinuxService();
        
        // Check process running
        boolean processRunning = checkLinuxProcess();
        
        // Check socket accessible
        boolean socketAccessible = Files.exists(Path.of(socketPath)) &&
                                  checkLinuxSocketPermissions(socketPath);
        
        String error = null;
        if (!binaryExists) {
            error = "IODaemon binary not found at " + binaryPath;
        } else if (!processRunning && !serviceInstalled) {
            error = "IODaemon not running and service not installed";
        } else if (processRunning && !socketAccessible) {
            error = "IODaemon running but socket not accessible at " + socketPath;
        }
        
        return new DetectionResult(
            binaryExists,
            serviceInstalled,
            processRunning,
            socketAccessible,
            binaryPath,
            socketPath,
            installPath,
            error
        );
    }
    
    private static boolean checkLinuxService() {
        try {
            String[] cmd = {"sh", "-c", "systemctl is-enabled io-daemon 2>/dev/null"};
            Process proc = Runtime.getRuntime().exec(cmd);
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()));
            String result = reader.readLine();
            proc.waitFor();
            
            return result != null && result.equals("enabled");
        } catch (Exception e) {
            Log.logError("Failed to check systemd service: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean checkLinuxProcess() {
        try {
            String[] cmd = {"sh", "-c", "pgrep -x io-daemon >/dev/null 2>&1 && echo running"};
            Process proc = Runtime.getRuntime().exec(cmd);
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()));
            String result = reader.readLine();
            proc.waitFor();
            
            return result != null && result.equals("running");
        } catch (Exception e) {
            Log.logError("Failed to check process: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean checkLinuxSocketPermissions(String socketPath) {
        try {
            Path path = Path.of(socketPath);
            if (!Files.exists(path)) {
                return false;
            }
            
            // Try to check if readable (basic check)
            return Files.isReadable(path);
        } catch (Exception e) {
            return false;
        }
    }
    
    // ===== MAC DETECTION =====
    
    private static DetectionResult detectMac() {
        String binaryPath = MAC_BINARY;
        String socketPath = MAC_SOCKET_PATH;
        String installPath = MAC_INSTALL_DIR;
        
        // Check binary exists
        boolean binaryExists = Files.exists(Path.of(binaryPath)) && 
                              Files.isExecutable(Path.of(binaryPath));
        
        // Check launchd service
        boolean serviceInstalled = checkMacService();
        
        // Check process running
        boolean processRunning = checkMacProcess();
        
        // Check socket accessible
        boolean socketAccessible = Files.exists(Path.of(socketPath));
        
        String error = null;
        if (!binaryExists) {
            error = "IODaemon binary not found at " + binaryPath;
        } else if (!processRunning && !serviceInstalled) {
            error = "IODaemon not running and service not installed";
        } else if (processRunning && !socketAccessible) {
            error = "IODaemon running but socket not accessible at " + socketPath;
        }
        
        return new DetectionResult(
            binaryExists,
            serviceInstalled,
            processRunning,
            socketAccessible,
            binaryPath,
            socketPath,
            installPath,
            error
        );
    }
    
    private static boolean checkMacService() {
        try {
            // Check if launchd plist exists
            String[] cmd = {
                "sh", "-c",
                "launchctl list | grep -q io-daemon && echo found"
            };
            Process proc = Runtime.getRuntime().exec(cmd);
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()));
            String result = reader.readLine();
            proc.waitFor();
            
            return result != null && result.equals("found");
        } catch (Exception e) {
            Log.logError("Failed to check launchd service: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean checkMacProcess() {
        try {
            String[] cmd = {"sh", "-c", "pgrep -x io-daemon >/dev/null 2>&1 && echo running"};
            Process proc = Runtime.getRuntime().exec(cmd);
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()));
            String result = reader.readLine();
            proc.waitFor();
            
            return result != null && result.equals("running");
        } catch (Exception e) {
            Log.logError("Failed to check process: " + e.getMessage());
            return false;
        }
    }
    
    // ===== WINDOWS DETECTION =====
    
    private static DetectionResult detectWindows() {
        String binaryPath = WIN_BINARY;
        String socketPath = WIN_SOCKET_PATH;
        String installPath = WIN_INSTALL_DIR;
        
        // Check binary exists
        boolean binaryExists = Files.exists(Path.of(binaryPath));
        
        // Check Windows service
        boolean serviceInstalled = checkWindowsService();
        
        // Check process running
        boolean processRunning = checkWindowsProcess();
        
        // Check socket accessible (Windows uses named pipes)
        boolean socketAccessible = checkWindowsSocket(socketPath);
        
        String error = null;
        if (!binaryExists) {
            error = "IODaemon binary not found at " + binaryPath;
        } else if (!processRunning && !serviceInstalled) {
            error = "IODaemon not running and service not installed";
        } else if (processRunning && !socketAccessible) {
            error = "IODaemon running but socket not accessible";
        }
        
        return new DetectionResult(
            binaryExists,
            serviceInstalled,
            processRunning,
            socketAccessible,
            binaryPath,
            socketPath,
            installPath,
            error
        );
    }
    
    private static boolean checkWindowsService() {
        try {
            String[] cmd = {
                "powershell", "-Command",
                "Get-Service -Name 'IODaemon' -ErrorAction SilentlyContinue | " +
                "Select-Object -ExpandProperty Status"
            };
            Process proc = Runtime.getRuntime().exec(cmd);
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()));
            String status = reader.readLine();
            proc.waitFor();
            
            return status != null && !status.trim().isEmpty();
        } catch (Exception e) {
            Log.logError("Failed to check Windows service: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean checkWindowsProcess() {
        try {
            String[] cmd = {
                "powershell", "-Command",
                "Get-Process -Name 'io-daemon' -ErrorAction SilentlyContinue | " +
                "Select-Object -ExpandProperty ProcessName"
            };
            Process proc = Runtime.getRuntime().exec(cmd);
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()));
            String result = reader.readLine();
            proc.waitFor();
            
            return result != null && result.contains("io-daemon");
        } catch (Exception e) {
            Log.logError("Failed to check process: " + e.getMessage());
            return false;
        }
    }
    
    private static boolean checkWindowsSocket(String socketPath) {
        // On Windows, check if the socket file/pipe exists
        try {
            return Files.exists(Path.of(socketPath));
        } catch (Exception e) {
            return false;
        }
    }
    
    // ===== SERVICE CONTROL =====
    
    /**
     * Start IODaemon service (if installed as service)
     */
    public static CompletableFuture<Boolean> startService(ExecutorService executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (IS_LINUX) {
                    return startLinuxService();
                } else if (IS_MAC) {
                    return startMacService();
                } else if (IS_WINDOWS) {
                    return startWindowsService();
                }
                return false;
            } catch (Exception e) {
                Log.logError("Failed to start service: " + e.getMessage());
                return false;
            }
        }, executor);
    }
    
    private static boolean startLinuxService() throws Exception {
        String[] cmd = {"sh", "-c", "systemctl start io-daemon 2>&1"};
        Process proc = Runtime.getRuntime().exec(cmd);
        
        BufferedReader stderr = new BufferedReader(
            new InputStreamReader(proc.getErrorStream()));
        String error = stderr.readLine();
        
        int exitCode = proc.waitFor();
        
        if (exitCode != 0) {
            Log.logError("Failed to start service: " + error);
            return false;
        }
        
        return true;
    }
    
    private static boolean startMacService() throws Exception {
        String[] cmd = {"sh", "-c", "launchctl start io-daemon 2>&1"};
        Process proc = Runtime.getRuntime().exec(cmd);
        
        int exitCode = proc.waitFor();
        return exitCode == 0;
    }
    
    private static boolean startWindowsService() throws Exception {
        String[] cmd = {
            "powershell", "-Command",
            "Start-Service -Name 'IODaemon'"
        };
        Process proc = Runtime.getRuntime().exec(cmd);
        
        int exitCode = proc.waitFor();
        return exitCode == 0;
    }
    
    // ===== INSTALLATION DETECTION =====
    
    /**
     * Get expected installation paths for current OS
     */
    public static InstallationPaths getInstallationPaths() {
        if (IS_LINUX) {
            return new InstallationPaths(
                LINUX_BINARY,
                LINUX_SOCKET_PATH,
                LINUX_INSTALL_DIR,
                LINUX_RUNTIME_DIR,
                "systemctl enable --now io-daemon"
            );
        } else if (IS_MAC) {
            return new InstallationPaths(
                MAC_BINARY,
                MAC_SOCKET_PATH,
                MAC_INSTALL_DIR,
                MAC_RUNTIME_DIR,
                "launchctl load /Library/LaunchDaemons/io.netnotes.daemon.plist"
            );
        } else if (IS_WINDOWS) {
            return new InstallationPaths(
                WIN_BINARY,
                WIN_SOCKET_PATH,
                WIN_INSTALL_DIR,
                WIN_RUNTIME_DIR,
                "sc.exe start IODaemon"
            );
        }
        return null;
    }
    
    public static class InstallationPaths {
        public final String binaryPath;
        public final String socketPath;
        public final String installDir;
        public final String runtimeDir;
        public final String startCommand;
        
        public InstallationPaths(
                String binaryPath,
                String socketPath,
                String installDir,
                String runtimeDir,
                String startCommand) {
            this.binaryPath = binaryPath;
            this.socketPath = socketPath;
            this.installDir = installDir;
            this.runtimeDir = runtimeDir;
            this.startCommand = startCommand;
        }
    }
}