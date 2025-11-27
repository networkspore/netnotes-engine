package io.netnotes.engine.core.system.control.nodes;


import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * PackageManifest - JSON manifest that describes how to load a node
 * 
 * Example manifest.json:
 * {
 *   "name": "example-node",
 *   "version": "1.0.0",
 *   "type": "osgi-bundle",
 *   "entry": "com.example.ExampleNode",
 *   "dependencies": ["org.osgi.core"],
 *   "autoload": false,
 *   "requires": {
 *     "api-version": "1.0"
 *   }
 * }
 */
public class PackageManifest {
    private final String name;
    private final String version;
    private final String type;  // osgi-bundle, script, native, etc.
    private final String entry;  // Entry point class/file
    private final List<String> dependencies;
    private final boolean autoload;
    private final JsonObject metadata;  // Additional metadata
    
    public PackageManifest(
            String name,
            String version,
            String type,
            String entry,
            List<String> dependencies,
            boolean autoload,
            JsonObject metadata) {
        
        this.name = name;
        this.version = version;
        this.type = type;
        this.entry = entry;
        this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
        this.autoload = autoload;
        this.metadata = metadata;
    }
    
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getType() { return type; }
    public String getEntry() { return entry; }
    public List<String> getDependencies() { return dependencies; }
    public boolean isAutoload() { return autoload; }
    public JsonObject getMetadata() { return metadata; }
    
    /**
     * Parse manifest from JSON
     */
    public static PackageManifest fromJson(JsonObject json) {
        String name = json.get("name").getAsString();
        String version = json.get("version").getAsString();
        String type = json.has("type") ? 
            json.get("type").getAsString() : "osgi-bundle";
        String entry = json.has("entry") ? 
            json.get("entry").getAsString() : null;
        
        List<String> dependencies = new ArrayList<>();
        if (json.has("dependencies") && json.get("dependencies").isJsonArray()) {
            json.getAsJsonArray("dependencies").forEach(e -> 
                dependencies.add(e.getAsString()));
        }
        
        boolean autoload = json.has("autoload") && 
            json.get("autoload").getAsBoolean();
        
        return new PackageManifest(name, version, type, entry, 
            dependencies, autoload, json);
    }
    
    /**
     * Convert to JSON
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("version", version);
        json.addProperty("type", type);
        if (entry != null) json.addProperty("entry", entry);
        
        if (!dependencies.isEmpty()) {
            com.google.gson.JsonArray deps = new com.google.gson.JsonArray();
            dependencies.forEach(deps::add);
            json.add("dependencies", deps);
        }
        
        json.addProperty("autoload", autoload);
        
        // Merge additional metadata
        if (metadata != null) {
            for (String key : metadata.keySet()) {
                if (!json.has(key)) {
                    json.add(key, metadata.get(key));
                }
            }
        }
        
        return json;
    }
}