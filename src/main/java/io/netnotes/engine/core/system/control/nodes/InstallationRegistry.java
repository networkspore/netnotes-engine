package io.netnotes.engine.core.system.control.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.core.AppDataInterface;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteFiles.NoteFile;

/**
 * InstallationRegistry - Tracks INSTALLED packages (System Service)
 * 
 * LIFECYCLE:
 * - Created by AppData during system initialization
 * - Lives for entire application lifetime
 * - Maintains NoteFile reference for efficient access
 * - Closed during AppData shutdown
 * 
 * STORAGE:
 * - Path: /system/nodes/registry/installed
 * - Format: NoteBytesMap { packageId -> InstalledPackage }
 * - NoteFile cached as instance field (no repeated ledger access)
 * 
 * SEPARATION:
 * - InstallationRegistry: What packages are installed (metadata)
 * - NodeController: What nodes are loaded (runtime)
 * 
 * A package can be installed but not loaded.
 * A package must be installed before it can be loaded.
 */
public class InstallationRegistry {
    private final ContextPath myPath;
    private final AppDataInterface dataInterface;


    private final ConcurrentHashMap<NoteBytesReadOnly, InstalledPackage> installed;
    
    // Cached NoteFile - expensive to get, cheap to keep
    // Lasts for lifecycle of InstalltionRegistry
    private NoteFile registryFile = null;
    /**
     * Constructor - called by AppData
     * 
     * @param appData Parent AppData instance
     */
    public InstallationRegistry(
            ContextPath myPath,
            AppDataInterface dataInterface) {
        if (myPath == null) {
            throw new IllegalArgumentException("myPath cannot be null");
        }
        
        if (dataInterface == null) {
            throw new IllegalArgumentException("dataInterface cannot be null");
        }

        this.myPath = myPath;  // Parent tells me where I live
        this.dataInterface = dataInterface;
        this.installed = new ConcurrentHashMap<>();
    }
    
    /**
     * Initialize - load installation registry from NoteFile
     * 
     * Gets NoteFile ONCE and stores for entire lifecycle.
     * This avoids repeated ledger access on every save.
     */
    public CompletableFuture<Void> initialize() {
        
        
         // I know MY path, so I create files under ME
        ContextPath installedFile = myPath.append("installed");
        
        return dataInterface.getNoteFile(installedFile)
            .thenAccept(file -> {
                this.registryFile = file;
                System.out.println("[InstallationRegistry] NoteFile acquired and cached");
            })
            .thenCompose(v -> loadInstalledPackages())
            .exceptionallyCompose(ex -> saveToFile().thenRun(()->{
                System.out.println("[InstallationRegistry] Initial registry file created");
            }));
              
    }
    
    /**
     * Load installed packages from cached NoteFile
     */
    private CompletableFuture<Void> loadInstalledPackages() {
        return registryFile.readNoteBytes()
            .thenAccept(noteBytesObj -> {
                if(noteBytesObj == null) {
                    throw new IllegalStateException("Registry file does not exist");
                }
                // Deserialize from NoteBytes format
                NoteBytesMap packagesMap = noteBytesObj.getAsNoteBytesMap();
                
                System.out.println("[InstallationRegistry] Found " + 
                    packagesMap.size() + " installed packages");
                
                // Each entry: packageId -> package data
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
     * 
     * ✅ Uses cached NoteFile - no ledger access!
     * This is called frequently (every install/uninstall).
     * 
     * Format: NoteBytesMap { packageId -> InstalledPackage.toNoteBytes() }
     */
    private CompletableFuture<Void> saveToFile() {
        if (registryFile == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Registry not initialized - NoteFile: registryFile is null"));
        }
        
        NoteBytesMap packagesMap = new NoteBytesMap();
        
        // Serialize each package
        for (InstalledPackage pkg : installed.values()) {
            packagesMap.put(
                pkg.getPackageId(),
                pkg.toNoteBytes()
            );
        }
        
        // Write to cached NoteFile (no ledger access!)
        return registryFile.write(packagesMap.getNoteBytesObject())
            .exceptionally(ex -> {
                System.err.println("[InstallationRegistry] Failed to save: " + 
                    ex.getMessage());
                throw new RuntimeException("Failed to save installation registry", ex);
            });
    }
    
    /**
     * Shutdown - ensure registry is saved and NoteFile closed
     */
    public CompletableFuture<Void> shutdown() {
        System.out.println("[InstallationRegistry] Shutting down, saving registry");
        
        return saveToFile()
            .whenComplete((v, ex) -> {
                if (ex != null) {
                    System.err.println("[InstallationRegistry] Error during shutdown save: " + 
                        ex.getMessage());
                }
            
                if (registryFile != null) {
                    registryFile.close();
                    System.out.println("[InstallationRegistry] NoteFile closed");
                }
            })
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

    /**
     * Get my path (for debugging)
     */
    public ContextPath getPath() {
        return myPath;
    }
   
}