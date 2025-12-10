package io.netnotes.engine.core.system.control.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteFiles.NoteFile;

/**
 * InstallationRegistry - Tracks INSTALLED packages (FlowProcess)
 * 
 * RESPONSIBILITIES:
 * - Track which packages are installed
 * - Persist to NoteFile
 * - Emit events on install/uninstall
 * - Answer queries about installed packages
 * 
 * LIFECYCLE:
 * - Created by SystemRuntime during initialization
 * - Registered as child process at /system/nodes/registry
 * - Initialized (loads from NoteFile)
 * - Lives for entire session
 * - Closed during shutdown
 * 
 * STORAGE:
 * - Path: /node-packages/registry
 * - Format: NoteBytesMap { packageId -> InstalledPackage }
 * - NoteFile cached as instance field
 */
public class InstallationRegistry extends FlowProcess {
    
    private final ConcurrentHashMap<PackageId, InstalledPackage> installed;
    private final NoteFile registryFile;
    
    public InstallationRegistry(String name, NoteFile registryFile) {
        super(name, ProcessType.BIDIRECTIONAL);
        
        if (registryFile == null) {
            throw new IllegalArgumentException("Registry file cannot be null");
        }
        
        this.registryFile = registryFile;
        this.installed = new ConcurrentHashMap<>();
    }
    
    @Override
    public CompletableFuture<Void> run() {
        // Initialize will be called separately by SystemRuntime
        return getCompletionFuture();
    }
    
    /**
     * Initialize - load installation registry from NoteFile
     */
    public CompletableFuture<Void> initialize() {
        System.out.println("[InstallationRegistry] Initializing at: " + contextPath);
        
        return loadInstalledPackages()
            .exceptionally(ex -> {
                // First run - create empty registry
                System.out.println("[InstallationRegistry] First run - creating empty registry");
                return saveToFile().join(); // Create empty file
            });
    }
    
    /**
     * Load installed packages from cached NoteFile
     */
    private CompletableFuture<Void> loadInstalledPackages() {
        return registryFile.readNoteBytes()
            .thenAccept(noteBytesObj -> {
                if (noteBytesObj == null) {
                    throw new IllegalStateException("Registry file does not exist");
                }
                
                NoteBytesMap packagesMap = noteBytesObj.getAsNoteBytesMap();
                
                System.out.println("[InstallationRegistry] Loading " + 
                    packagesMap.size() + " installed packages");
                
                for (NoteBytes pkgIdBytes : packagesMap.keySet()) {
                    try {
                        NoteBytesMap pkgData = packagesMap.get(pkgIdBytes).getAsNoteBytesMap();
                        InstalledPackage pkg = 
                            InstalledPackage.fromNoteBytes(pkgData);
                        
                        installed.put(pkg.getPackageId(), pkg);
                        
                        System.out.println("[InstallationRegistry]   Loaded: " + 
                            pkg.getName() + " v" + pkg.getVersion());
                            
                    } catch (Exception e) {
                        System.err.println("[InstallationRegistry]   Failed to load package " + 
                            pkgIdBytes.getAsString() + ": " + e.getMessage());
                    }
                }
                
                System.out.println("[InstallationRegistry] Successfully loaded " + 
                    installed.size() + " packages");
            });
    }
    
    /**
     * Register a newly installed package
     */
    public CompletableFuture<Void> registerPackage(InstalledPackage pkg) {
        installed.put(pkg.getPackageId(), pkg);
        
        System.out.println("[InstallationRegistry] Registered: " + pkg.getName() + 
            " (processId: " + pkg.getProcessId() + ")");
        
        // Emit event
        emitPackageEvent("package_installed", pkg.getPackageId().getId());
        
        return saveToFile()
            .thenRun(() -> {
                System.out.println("[InstallationRegistry] Registry saved (" + 
                    installed.size() + " packages)");
            });
    }
    
    /**
     * Unregister a package (before deletion)
     */
    public CompletableFuture<Void> unregisterPackage(PackageId packageId) {
        InstalledPackage removed = installed.remove(packageId);
        
        if (removed != null) {
            System.out.println("[InstallationRegistry] Unregistered: " + removed.getName());
            
            // Emit event
            emitPackageEvent("package_uninstalled", packageId.getId());
            
            return saveToFile()
                .thenRun(() -> {
                    System.out.println("[InstallationRegistry] Registry saved after removal");
                });
        }
        
        System.out.println("[InstallationRegistry] Package not found: " + packageId);
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Get all installed packages
     */
    public List<InstalledPackage> getInstalledPackages() {
        return new ArrayList<>(installed.values());
    }
    
    /**
     * Get specific package
     */
    public InstalledPackage getPackage(PackageId packageId) {
        return installed.get(packageId);
    }
    
    /**
     * Check if package is installed
     */
    public boolean isInstalled(PackageId packageId) {
        return installed.containsKey(packageId);
    }
    
    /**
     * Save registry to cached NoteFile
     */
    private CompletableFuture<Void> saveToFile() {
        if (registryFile == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Registry not initialized"));
        }
        
        NoteBytesMap packagesMap = new NoteBytesMap();
        
        // Serialize each package
        for (InstalledPackage pkg : installed.values()) {
            packagesMap.put(
                pkg.getPackageId().getId(),
                pkg.toNoteBytes()
            );
        }
        
        return registryFile.write(packagesMap.toNoteBytes())
            .thenRun(() -> {
                System.out.println("[InstallationRegistry] Saved " + 
                    installed.size() + " packages to file");
            })
            .exceptionally(ex -> {
                System.err.println("[InstallationRegistry] Failed to save: " + 
                    ex.getMessage());
                throw new RuntimeException("Failed to save installation registry", ex);
            });
    }
    
    /**
     * Emit package event (for subscribers like NodeController)
     */
    private void emitPackageEvent(String eventType, NoteBytes packageId) {
        NoteBytesMap event = new NoteBytesMap();
        event.put("event", new NoteBytes(eventType));
        event.put(Keys.PACKAGE_ID, packageId);
        event.put(Keys.TIMESTAMP, new NoteBytes(System.currentTimeMillis()));
        
        emit(event.toNoteBytes());
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Could handle queries like:
        // - list_installed
        // - get_package
        // - is_installed
        // For now, not implemented (direct method calls used)
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public void handleStreamChannel(StreamChannel channel, ContextPath fromPath) {
        throw new UnsupportedOperationException(
            "InstallationRegistry does not support stream channels");
    }
    
    /**
     * Shutdown - ensure registry is saved and NoteFile closed
     */
    public CompletableFuture<Void> shutdown() {
        System.out.println("[InstallationRegistry] Shutting down");
        
        return saveToFile()
            .whenComplete((v, ex) -> {
                if (ex != null) {
                    System.err.println("[InstallationRegistry] Error during shutdown: " + 
                        ex.getMessage());
                }
                
                if (registryFile != null) {
                    registryFile.close();
                    System.out.println("[InstallationRegistry] NoteFile closed");
                }
            })
            .thenRun(() -> {
                complete();
                System.out.println("[InstallationRegistry] Shutdown complete");
            });
    }
    
    /**
     * Get statistics
     */
    public String getStatistics() {
        return String.format("Installed packages: %d", installed.size());
    }
}