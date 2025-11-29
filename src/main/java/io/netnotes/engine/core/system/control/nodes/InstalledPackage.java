package io.netnotes.engine.core.system.control.nodes;

import com.google.gson.JsonObject;

import io.netnotes.engine.io.ContextPath;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

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
    private final NoteBytesReadOnly packageId;
    private final String name;
    private final String version;
    private final String category;
    private final String description;
    private final String repository;
    private final ContextPath installPath;  // Where files are stored
    private final PackageManifest manifest;
    private final long installedDate;
    
    public InstalledPackage(
            NoteBytesReadOnly packageId,
            String name,
            String version,
            String category,
            String description,
            String repository,
            ContextPath installPath,
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
    
    // ===== GETTERS =====
    
    public NoteBytesReadOnly getPackageId() { return packageId; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getRepository() { return repository; }
    public ContextPath getInstallPath() { return installPath; }
    public PackageManifest getManifest() { return manifest; }
    public long getInstalledDate() { return installedDate; }
    
    // ===== INTERNAL SERIALIZATION (NoteBytes) =====
    
    /**
     * Serialize to NoteBytes format for internal storage
     */
    public NoteBytesObject toNoteBytes() {
        NoteBytesMap map = new NoteBytesMap();
        
        map.put(new NoteBytes("package_id"), packageId);
        map.put(new NoteBytes("name"), new NoteBytes(name));
        map.put(new NoteBytes("version"), new NoteBytes(version));
        map.put(new NoteBytes("category"), new NoteBytes(category));
        map.put(new NoteBytes("description"), new NoteBytes(description));
        map.put(new NoteBytes("repository"), new NoteBytes(repository));
        map.put(new NoteBytes("install_path"), installPath.toNoteBytes());
        map.put(new NoteBytes("manifest"), manifest.toNoteBytes());
        map.put(new NoteBytes("installed_date"), new NoteBytes(installedDate));
        return map.getNoteBytesObject();
    }
    
    /**
     * Deserialize from NoteBytes format
     */
    public static InstalledPackage fromNoteBytes(NoteBytesMap map) {
        try {
            NoteBytesReadOnly packageId = map.get(new NoteBytes("package_id")).readOnly();
            String name = map.get(new NoteBytes("name")).getAsString();
            String version = map.get(new NoteBytes("version")).getAsString();
            String category = map.get(new NoteBytes("category")).getAsString();
            String description = map.get(new NoteBytes("description")).getAsString();
            String repository = map.get(new NoteBytes("repository")).getAsString();
            
            // Deserialize ContextPath
            NoteBytes pathBytes = map.get(new NoteBytes("install_path"));
            ContextPath installPath =  ContextPath.fromNoteBytes(pathBytes);
            
            // Deserialize manifest
            NoteBytesMap manifestMap = map.get(new NoteBytes("manifest")).getAsNoteBytesMap();
            PackageManifest manifest = PackageManifest.fromNoteBytes(manifestMap);
            
            long installedDate = map.get(new NoteBytes("installed_date")).getAsLong();
            
            return new InstalledPackage(
                packageId, name, version, category, description,
                repository, installPath, manifest, installedDate
            );
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize InstalledPackage from NoteBytes", e);
        }
    }
    
    // ===== EXTERNAL SERIALIZATION (JSON - when downloading) =====
    
    /**
     * Create from external JSON (when installing from repository)
     * This converts external format → internal format
     */
    public static InstalledPackage fromJson(JsonObject json, 
                                           ContextPath installPath,
                                           long installedDate) {
        try {
            String packageId = json.get("id").getAsString();
            String name = json.get("name").getAsString();
            String version = json.get("version").getAsString();
            String category = json.has("category") ? 
                json.get("category").getAsString() : "uncategorized";
            String description = json.has("description") ? 
                json.get("description").getAsString() : "";
            String repository = json.has("repository") ? 
                json.get("repository").getAsString() : "unknown";
            
            // Parse manifest
            com.google.gson.JsonObject manifestJson = json.getAsJsonObject("manifest");
            PackageManifest manifest = PackageManifest.fromJson(manifestJson);
            
            return new InstalledPackage(
                new NoteBytesReadOnly(packageId), name, version, category, description,
                repository, installPath, manifest, installedDate
            );
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse InstalledPackage from JSON", e);
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "InstalledPackage[id=%s, name=%s, version=%s, installed=%d]",
            packageId, name, version, installedDate
        );
    }
}