package io.netnotes.engine.core.system.control.nodes;

import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;

/**
 * InstalledPackage - Metadata about an INSTALLED package
 * This is in the installation registry, but NOT necessarily loaded in runtime
 */
public class InstalledPackage {
    private final String packageId;
    private final String name;
    private final String version;
    private final String category;
    private final String description;
    private final String repository;
    private final NoteStringArrayReadOnly installPath;  // Where files are stored
    private final PackageManifest manifest;
    private final long installedDate;
    
    public InstalledPackage(
            String packageId,
            String name,
            String version,
            String category,
            String description,
            String repository,
            NoteStringArrayReadOnly installPath,
            PackageManifest manifest,
            long installedDate) {
        
        this.packageId = packageId;
        this.name = name;
        this.version = version;
        this.category = category;
        this.description = description;
        this.repository = repository;
        this.installPath = installPath;
        this.manifest = manifest;
        this.installedDate = installedDate;
    }
    
    public String getPackageId() { return packageId; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getRepository() { return repository; }
    public NoteStringArrayReadOnly getInstallPath() { return installPath; }
    public PackageManifest getManifest() { return manifest; }
    public long getInstalledDate() { return installedDate; }
}