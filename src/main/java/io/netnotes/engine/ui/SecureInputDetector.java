package io.netnotes.engine.ui;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.collections.NoteBytesMap;

import java.io.File;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SecureInputDetector - Detect NoteDaemon installation and capabilities
 * 
 * Capabilities:
 * - Check if socket file exists
 * - Test connection to daemon
 * - Query for available keyboards
 * - Detect OS capabilities for auto-install
 */
public class SecureInputDetector {
    
    /**
     * Check if NoteDaemon socket exists
     */
    public static boolean socketExists(String socketPath) {
        try {
            Path path = Path.of(socketPath);
            return Files.exists(path) && !Files.isDirectory(path);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Test connection to NoteDaemon
     * Returns true if we can connect and get a valid response
     */
    public static boolean testConnection(String socketPath) {
        try {
            UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
            
            try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                channel.connect(address);
                
                // Send HELLO message
                NoteBytesMap hello = new NoteBytesMap();
                hello.put(Keys.CMD, ProtocolMesssages.HELLO);
                hello.put(Keys.VERSION, new NoteBytes("1.0.0"));
                
                byte[] helloBytes = hello.toNoteBytes().get();
                ByteBuffer buffer = ByteBuffer.wrap(helloBytes);
                channel.write(buffer);
                
                // Try to read response (with timeout)
                channel.configureBlocking(false);
                ByteBuffer responseBuffer = ByteBuffer.allocate(1024);
                
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < 1000) {
                    int bytesRead = channel.read(responseBuffer);
                    if (bytesRead > 0) {
                        return true; // Got response, daemon is alive
                    }
                    Thread.sleep(50);
                }
                
                return false; // No response
            }
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Detect available keyboards through NoteDaemon
     * Returns count of keyboards, or -1 on error
     */
    public static int detectKeyboards(String socketPath) {
        try {
            UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
            
            try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                channel.connect(address);
                
                // Send DISCOVER_DEVICES message
                NoteBytesMap discover = new NoteBytesMap();
                discover.put(Keys.CMD, ProtocolMesssages.REQUEST_DISCOVERY);
                
                byte[] discoverBytes = discover.toNoteBytes().get();
                ByteBuffer buffer = ByteBuffer.wrap(discoverBytes);
                channel.write(buffer);
                
                // Read response
                channel.configureBlocking(false);
                ByteBuffer responseBuffer = ByteBuffer.allocate(8192);
                
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < 2000) {
                    int bytesRead = channel.read(responseBuffer);
                    if (bytesRead > 0) {
                        responseBuffer.flip();
                        byte[] responseBytes = new byte[responseBuffer.remaining()];
                        responseBuffer.get(responseBytes);
                        
                        // Parse response
                        NoteBytesMap response = new NoteBytesMap(responseBytes);
                        NoteBytes count = response.get(Keys.ITEMS);
                        
                        if (count != null) {
                            return count.getAsInt();
                        }
                    }
                    Thread.sleep(50);
                }
            }
            
        } catch (Exception e) {
            // Silent failure
        }
        
        return 0; // No keyboards or error
    }
    
    /**
     * Check if we can auto-install on this OS
     */
    public static boolean canAutoInstall(String os) {
        if (os == null) return false;
        
        String osLower = os.toLowerCase();
        
        // Linux with apt (Debian/Ubuntu)
        if (osLower.contains("linux")) {
            return hasCommand("apt-get") || hasCommand("apt");
        }
        
        // macOS with Homebrew
        if (osLower.contains("mac")) {
            return hasCommand("brew");
        }
        
        return false;
    }
    
    /**
     * Check if command exists in PATH
     */
    private static boolean hasCommand(String command) {
        try {
            Process process = new ProcessBuilder("which", command)
                .redirectErrorStream(true)
                .start();
            
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get system information for installation
     */
    public static SystemInfo getSystemInfo() {
        SystemInfo info = new SystemInfo();
        
        info.os = System.getProperty("os.name");
        info.arch = System.getProperty("os.arch");
        info.version = System.getProperty("os.version");
        
        // Check if running as root/sudo
        info.hasRootAccess = checkRootAccess();
        
        // Check available disk space
        info.availableSpaceMB = getAvailableSpace();
        
        // Check required tools
        info.hasCMake = hasCommand("cmake");
        info.hasGCC = hasCommand("gcc") || hasCommand("g++");
        info.hasMake = hasCommand("make");
        
        return info;
    }
    
    private static boolean checkRootAccess() {
        try {
            // Try to create a file in a root-only directory
            File testFile = new File("/usr/local/bin/.test-" + System.currentTimeMillis());
            boolean canWrite = testFile.createNewFile();
            if (canWrite) {
                testFile.delete();
            }
            return canWrite;
        } catch (IOException e) {
            return false;
        }
    }
    
    private static long getAvailableSpace() {
        try {
            File root = new File("/");
            return root.getUsableSpace() / (1024 * 1024); // Convert to MB
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * System information for installation decisions
     */
    public static class SystemInfo {
        public String os;
        public String arch;
        public String version;
        public boolean hasRootAccess;
        public long availableSpaceMB;
        public boolean hasCMake;
        public boolean hasGCC;
        public boolean hasMake;
        
        public boolean canBuild() {
            return hasCMake && hasGCC && hasMake && availableSpaceMB > 100;
        }
        
        @Override
        public String toString() {
            return String.format(
                "OS: %s %s (%s)\n" +
                "Root Access: %s\n" +
                "Available Space: %d MB\n" +
                "Build Tools: CMake=%s, GCC=%s, Make=%s",
                os, version, arch,
                hasRootAccess ? "Yes" : "No",
                availableSpaceMB,
                hasCMake, hasGCC, hasMake
            );
        }
    }
}