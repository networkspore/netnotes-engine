package io.netnotes.engine.core.system.control.nodes;

import io.netnotes.engine.core.CoreConstants;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

/**
 * PackageInfo - Metadata about an AVAILABLE package (from repository)
 * This is NOT installed yet - just information about what's available
 */
public class PackageInfo {
    private final NoteBytesReadOnly packageId;
    private final String name;
    private final String version;
    private final String category;
    private final String description;
    private final String repository;  // Which repo it came from
    private final String downloadUrl;
    private final long size;
    private final PackageManifest manifest;  // JSON manifest

    public PackageInfo(
        NoteBytesReadOnly packageId,
        String name,
        String version,
        String category,
        String description,
        String repository,
        String downloadUrl,
        long size,
        PackageManifest manifest
    ) {
        
        this.packageId = packageId;
        this.name = name;
        this.version = version;
        this.category = category;
        this.description = description;
        this.repository = repository;
        this.downloadUrl = downloadUrl;
        this.size = size;
        this.manifest = manifest;

    }
    
    public NoteBytesReadOnly getPackageId() { return packageId; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getRepository() { return repository; }
    public String getDownloadUrl() { return downloadUrl; }
    public long getSize() { return size; }
    public PackageManifest getManifest() { return manifest; }

    public ContextPath createInstallPath(){
        return CoreConstants.PACKAGE_STORE_PATH.append(
            name,
            version
        );
    }
}
