package io.netnotes.engine.core.system.control.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.io.RoutedPacket;
import io.netnotes.engine.io.process.FlowProcess;
import io.netnotes.engine.io.process.StreamChannel;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteFiles.NoteFile;

/**
 * InstallationRegistry - Tracks INSTALLED packages (FlowProcess)
 * 
 * REFACTORED to be a FlowProcess:
 * - Receives ProcessRegistryInterface at construction
 * - Can handle messages (queries, install/uninstall commands)
 * - Can emit events (package installed/uninstalled)
 * - Participates in process network
 * 
 * LIFECYCLE:
 * - Created by SystemRuntime during initialization
 * - Registered as child process
 * - Lives for entire session lifetime
 * - Maintains NoteFile reference for efficient access
 * - Closed during shutdown
 * 
 * STORAGE:
 * - Path: {myPath}/installed
 * - Format: NoteBytesMap { packageId -> InstalledPackage }
 * - NoteFile cached as instance field
 */
public class InstallationRegistry extends FlowProcess {
    
    private final ConcurrentHashMap<NoteBytesReadOnly, InstalledPackage> installed;
    
    // Cached NoteFile - expensive to get, cheap to keep
    private NoteFile registryFile = null;

    /**
     * Constructor
     * 
     * @param name Process name (e.g., "registry")
     * @param noteFileService For file access
     */
    public InstallationRegistry(
        String name,
        NoteFile registryFile)
     {
        
        super(name, ProcessType.BIDIRECTIONAL);
        
        if (registryFile == null) {
            throw new IllegalArgumentException("Registry file cannot be null");
        }

        this.registryFile = registryFile;
        this.installed = new ConcurrentHashMap<>();
    }
    
    @Override
    public CompletableFuture<Void> run() {  
        return initialize()
            .thenCompose(v -> getCompletionFuture());
    }
    
    /**
     * Initialize - load installation registry from NoteFile
     * 
     * Gets NoteFile ONCE and stores for entire lifecycle.
     */
    public CompletableFuture<Void> initialize() {
        System.out.println("[InstallationRegistry] Initializing at: " + contextPath);
        
        return loadInstalledPackages()
            .exceptionallyCompose(ex -> {
                // First run - create empty registry
                System.out.println("[InstallationRegistry] Initial registry file created");
                return saveToFile();
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
                
                System.out.println("[InstallationRegistry] Found " + 
                    packagesMap.size() + " installed packages");
                
                for (NoteBytes pkgId : packagesMap.keySet()) {
                    try {
                        NoteBytesMap pkgData = packagesMap.get(pkgId).getAsNoteBytesMap();
                        InstalledPackage pkg = InstalledPackage.fromNoteBytes(pkgData);
                        installed.put(pkgId.readOnly(), pkg);
                        
                        System.out.println("[InstallationRegistry] Loaded: " + 
                            pkg.getName() + " v" + pkg.getVersion());
                            
                    } catch (Exception e) {
                        System.err.println("[InstallationRegistry] Failed to load package " + 
                            pkgId.getAsString() + ": " + e.getMessage());
                    }
                }
            });
    }
    
    /**
     * Register a newly installed package
     */
    public CompletableFuture<Void> registerPackage(InstalledPackage pkg) {
        installed.put(pkg.getPackageId(), pkg);
        System.out.println("[InstallationRegistry] Registered: " + pkg.getName());
        
        // Emit event
        emitPackageEvent("package_installed", pkg.getPackageId());
        
        return saveToFile()
            .thenRun(() -> {
                System.out.println("[InstallationRegistry] Registry saved with " + 
                    installed.size() + " packages");
            });
    }
    
    /**
     * Unregister a package (before deletion)
     */
    public CompletableFuture<Void> unregisterPackage(NoteBytesReadOnly packageId) {
        InstalledPackage removed = installed.remove(packageId);
        
        if (removed != null) {
            System.out.println("[InstallationRegistry] Unregistered: " + removed.getName());
            
            // Emit event
            emitPackageEvent("package_uninstalled", packageId);
            
            return saveToFile();
        }
        
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
    public InstalledPackage getPackage(NoteBytesReadOnly packageId) {
        return installed.get(packageId);
    }
    
    /**
     * Check if package is installed
     */
    public boolean isInstalled(NoteBytesReadOnly packageId) {
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
        
        for (InstalledPackage pkg : installed.values()) {
            packagesMap.put(
                pkg.getPackageId(),
                pkg.toNoteBytes()
            );
        }
        
        return registryFile.write(packagesMap.getNoteBytesObject())
            .exceptionally(ex -> {
                System.err.println("[InstallationRegistry] Failed to save: " + 
                    ex.getMessage());
                throw new RuntimeException("Failed to save installation registry", ex);
            });
    }
    
    /**
     * Emit package event (for subscribers like NodeController)
     */
    private void emitPackageEvent(String eventType, NoteBytesReadOnly packageId) {
        NoteBytesMap event = new NoteBytesMap();
        event.put("event", new NoteBytes(eventType));
        event.put("package_id", packageId);
        event.put("timestamp", new NoteBytes(System.currentTimeMillis()));
        
        emit(event.getNoteBytesObject());
    }
    
    @Override
    public CompletableFuture<Void> handleMessage(RoutedPacket packet) {
        // Handle queries, commands, etc.
        // For now, not implemented
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
    
    public String getStatistics() {
        return String.format("Installed packages: %d", installed.size());
    }
}