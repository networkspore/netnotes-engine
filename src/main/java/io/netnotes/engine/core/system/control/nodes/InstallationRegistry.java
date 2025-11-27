package io.netnotes.engine.core.system.control.nodes;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.core.AppData;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;

/**
 * InstallationRegistry - Tracks INSTALLED packages
 * 
 * This is SEPARATE from AppData's runtime node registry
 * 
 * NodeManager's registry: What packages are installed (files on disk)
 * AppData's registry: What nodes are loaded (active in runtime)
 * 
 * A package can be installed but not loaded
 * A package must be installed before it can be loaded
 */
public class InstallationRegistry {
    public static final NoteStringArrayReadOnly INSTALLATION_REGISTRY_PATH = 
        new NoteStringArrayReadOnly("node-packages", "installation-registry");
    
    private final AppData appData;
    private final ConcurrentHashMap<String, InstalledPackage> installed;
    
    public InstallationRegistry(AppData appData) {
        this.appData = appData;
        this.installed = new ConcurrentHashMap<>();
    }
    
    public CompletableFuture<Void> initialize() {
        // Load installation registry from NoteFile
        // For now, start empty
        return CompletableFuture.completedFuture(null);
    }
    
    public CompletableFuture<Void> registerPackage(InstalledPackage pkg) {
        installed.put(pkg.getPackageId(), pkg);
        System.out.println("[InstallationRegistry] Registered: " + pkg.getName());
        // TODO: Save to NoteFile
        return CompletableFuture.completedFuture(null);
    }
    
    public CompletableFuture<Void> unregisterPackage(String packageId) {
        InstalledPackage removed = installed.remove(packageId);
        if (removed != null) {
            System.out.println("[InstallationRegistry] Unregistered: " + removed.getName());
        }
        // TODO: Save to NoteFile
        return CompletableFuture.completedFuture(null);
    }
    
    public List<InstalledPackage> getInstalledPackages() {
        return new ArrayList<>(installed.values());
    }
    
    public InstalledPackage getPackage(String packageId) {
        return installed.get(packageId);
    }
    
    public boolean isInstalled(String packageId) {
        return installed.containsKey(packageId);
    }
    
    public void shutdown() {
        // Save to NoteFile
    }
}