package io.netnotes.engine.core.system.control.nodes;

import io.netnotes.engine.core.system.control.nodes.security.NodeSecurityPolicy;
import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;


/**
 * InstalledPackage - Metadata about an INSTALLED package
 * 
 * SERIALIZATION:
 * - Internal storage: NoteBytes format (via toNoteBytes/fromNoteBytes)
 * - External sources: JSON format (via fromJson when downloading)
 * 
 * This is stored in /system/nodes/registry/installed as NoteBytesMap
 */
public class InstalledPackage {
    private final PackageId packageId;
    private final String name;
    private final String description;
    private final PackageManifest manifest;
    
    // Process configuration (decided at install)
    private final ProcessConfig processConfig;
    
    // Security (decided at install)
    private final NodeSecurityPolicy securityPolicy;
    
    // Install metadata
    private final String repository;
    private final long installedDate;

    private final ContextPath installPath;
    
    public InstalledPackage(
        PackageId packageId,
        String name,
        String description,
        PackageManifest manifest,
        ProcessConfig processConfig,
        NodeSecurityPolicy securityPolicy,
        String repository,
        long installedDate,
        ContextPath installedPath
    ) {
        this.packageId = packageId;
        this.name = name;
        this.description = description;
        this.manifest = manifest;
        this.processConfig = processConfig;
        this.securityPolicy = securityPolicy;
        this.repository = repository;
        this.installedDate = installedDate;
        this.installPath = installedPath;
    }
    
    public PackageId getPackageId() { return packageId; }
    public NoteBytesReadOnly getProcessId() { return processConfig.getProcessId(); }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public PackageManifest getManifest() { return manifest; }
    public ProcessConfig getProcessConfig() { return processConfig; }
    public NodeSecurityPolicy getSecurityPolicy() { return securityPolicy; }
    public String getRepository() { return repository; }
    public long getInstalledDate() { return installedDate; }
    public String getVersion() { return packageId.getVersion(); }
    public ContextPath getInstallPath() { return installPath; }
    public String getPluginUrl() { return "notefile://" + installPath.toString(); }

    /**
     * Serialize to NoteBytes for storage
     */
    public NoteBytesObject toNoteBytes() {

        //Alternative use NoteBytesMap.put then NoteBytesObject obj = NoteBytesMap.toNoteBytes() 

        return new NoteBytesObject(
            new NoteBytesPair(Keys.PACKAGE_ID, packageId.getId()),
            new NoteBytesPair(Keys.NAME, name),
            new NoteBytesPair(Keys.VERSION, packageId.getVersion()),
            new NoteBytesPair(Keys.DESCRIPTION, description),
            // Manifest
            new NoteBytesPair("manifest", manifest.toNoteBytes()),
            // Process configuration
            new NoteBytesPair("process_config", processConfig.toNoteBytes()),
            // Security policy
            new NoteBytesPair("security_policy", securityPolicy.toNoteBytes()),
            // Install metadata
            new NoteBytesPair("repository", repository),
            new NoteBytesPair("installed_date", installedDate),
            new NoteBytesPair("install_path", installPath.getSegments()) //ContextPath
        );
    }
    
    /**
     * Deserialize from NoteBytes
     */
    public static InstalledPackage fromNoteBytes(NoteBytesMap map) {
        try {
            // Package identity
            NoteBytesReadOnly pkgIdBytes = map.getReadOnly(Keys.PACKAGE_ID);
            String name = map.get(Keys.NAME).getAsString();
            String version = map.get("version").getAsString();
            String description = map.get(Keys.DESCRIPTION).getAsString();
            
            PackageId packageId = new PackageId(pkgIdBytes, version);
            
            // Manifest
            PackageManifest manifest = PackageManifest.fromNoteBytes(
                map.get("manifest").getAsNoteBytesObject()
            );
            
            // Process configuration
            ProcessConfig processConfig = ProcessConfig.fromNoteBytes(
                map.get("process_config").getAsNoteBytesMap()
            );
            
            // Security policy
            NodeSecurityPolicy securityPolicy = NodeSecurityPolicy.fromNoteBytes(
                map.get("security_policy").getAsNoteBytesMap()
            );
            
            // Install metadata
            String repository = map.get("repository").getAsString();
            long installedDate = map.get("installed_date").getAsLong();
            ContextPath installPath = ContextPath.of(map.get("install_path").getAsNoteBytesArrayReadOnly());
            
            return new InstalledPackage(
                packageId,
                name,
                description,
                manifest,
                processConfig,
                securityPolicy,
                repository,
                installedDate,
                installPath
            );
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize RefactoredInstalledPackage", e);
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "InstalledPackage[%s v%s, process=%s]",
            name,
            packageId.getVersion(),
            processConfig.getProcessId()
        );
    }
}


