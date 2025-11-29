package io.netnotes.engine.core.system.control.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.core.AppDataInterface;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * InstallationRegistry - Tracks INSTALLED packages
 * 
 * STORAGE FORMAT:
 * Path: /system/nodes/registry/installed
 * Format: NoteBytesMap where:
 *   - Key: packageId (String)
 *   - Value: InstalledPackage.toNoteBytes() (NoteBytesMap)
 * 
 * This is SEPARATE from AppData's runtime node registry:
 * - InstallationRegistry: What packages are installed (files on disk)
 * - AppData.nodeRegistry(): What nodes are loaded (active in runtime)
 * 
 * A package can be installed but not loaded.
 * A package must be installed before it can be loaded.
 */
public class InstallationRegistry {
    
    private final AppDataInterface systemInterface;
    private final ConcurrentHashMap<String, InstalledPackage> installed;
    
    // Path where registry is stored
    private static final ContextPath REGISTRY_PATH = 
        NodePaths.REGISTRY.append("installed");
    
    public InstallationRegistry(AppDataInterface systemInterface) {
        this.systemInterface = systemInterface;
        this.installed = new ConcurrentHashMap<>();
    }
    
    /**
     * Initialize - load installation registry from NoteFile
     */
    public CompletableFuture<Void> initialize() {
        System.out.println("[InstallationRegistry] Loading from: " + REGISTRY_PATH);
        
        return systemInterface.getNoteFile(REGISTRY_PATH)
            .thenCompose(file -> file.nextNoteBytes())
            .thenAccept(noteBytesObj -> {
                // Deserialize from NoteBytes format
                NoteBytesMap packagesMap = noteBytesObj.getAsNoteBytesMap();
                
                System.out.println("[InstallationRegistry] Found " + 
                    packagesMap.size() + " installed packages");
                
                // Each entry: packageId -> package data
                for (NoteBytes pkgId : packagesMap.keySet()) {
                    try {
                        NoteBytesMap pkgData = packagesMap.get(pkgId).getAsNoteBytesMap();
                        InstalledPackage pkg = InstalledPackage.fromNoteBytes(pkgData);
                        installed.put(pkgId.getAsString(), pkg);
                        
                        System.out.println("[InstallationRegistry] Loaded: " + 
                            pkg.getName() + " v" + pkg.getVersion());
                            
                    } catch (Exception e) {
                        System.err.println("[InstallationRegistry] Failed to load package " + 
                            pkgId.getAsString() + ": " + e.getMessage());
                    }
                }
            })
            .exceptionally(ex -> {
                // First run - registry doesn't exist yet
                System.out.println("[InstallationRegistry] No existing registry found, starting fresh");
                return null;
            });
    }
    
    /**
     * Register a newly installed package
     */
    public CompletableFuture<Void> registerPackage(InstalledPackage pkg) {
        installed.put(pkg.getPackageId(), pkg);
        System.out.println("[InstallationRegistry] Registered: " + pkg.getName());
        
        return saveToFile()
            .thenRun(() -> {
                System.out.println("[InstallationRegistry] Saved registry with " + 
                    installed.size() + " packages");
            });
    }
    
    /**
     * Unregister a package (before deletion)
     */
    public CompletableFuture<Void> unregisterPackage(String packageId) {
        InstalledPackage removed = installed.remove(packageId);
        
        if (removed != null) {
            System.out.println("[InstallationRegistry] Unregistered: " + removed.getName());
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
    public InstalledPackage getPackage(String packageId) {
        return installed.get(packageId);
    }
    
    /**
     * Check if package is installed
     */
    public boolean isInstalled(String packageId) {
        return installed.containsKey(packageId);
    }
    
    /**
     * Save registry to NoteFile
     * 
     * Format: NoteBytesMap { packageId -> InstalledPackage.toNoteBytes() }
     */
    private CompletableFuture<Void> saveToFile() {
        NoteBytesMap packagesMap = new NoteBytesMap();
        
        // Serialize each package
        for (InstalledPackage pkg : installed.values()) {
            packagesMap.put(
                new NoteBytes(pkg.getPackageId()),
                pkg.toNoteBytes()
            );
        }
        
        // Write to NoteFile
        return systemInterface.getNoteFile(REGISTRY_PATH)
            .thenCompose(file -> file.write(packagesMap.getNoteBytesObject()))
            .exceptionally(ex -> {
                System.err.println("[InstallationRegistry] Failed to save: " + 
                    ex.getMessage());
                throw new RuntimeException("Failed to save installation registry", ex);
            });
    }
    
    /**
     * Shutdown - ensure registry is saved
     */
    public CompletableFuture<Void> shutdown() {
        System.out.println("[InstallationRegistry] Shutting down, saving registry");
        return saveToFile()
            .thenRun(() -> {
                System.out.println("[InstallationRegistry] Shutdown complete");
            });
    }
    
    /**
     * Get registry statistics
     */
    public String getStatistics() {
        return String.format(
            "Installed packages: %d",
            installed.size()
        );
    }
}