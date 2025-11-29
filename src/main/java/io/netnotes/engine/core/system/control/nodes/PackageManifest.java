package io.netnotes.engine.core.system.control.nodes;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.noteBytes.processing.NoteBytesMetaData;

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
    public static final NoteBytesReadOnly OSGI_BUNDLE = new NoteBytesReadOnly("osgi-bundle");

    private final String name;
    private final String version;
    private final String type;  // osgi-bundle, script, native, etc.
    private final String entry;  // Entry point class/file
    private final List<String> dependencies;
    private final boolean autoload;
    private final NoteBytesMap metadata;  // Additional metadata
    
    public PackageManifest(
        String name,
        String version,
        String type,
        String entry,
        List<String> dependencies,
        boolean autoload,
        JsonObject metadata
    ) { this(name, version, type, entry, dependencies, autoload, NoteBytes.fromJson(metadata).getAsMap()); }

    public PackageManifest(
        String name,
        String version,
        String type,
        String entry,
        List<String> dependencies,
        boolean autoload,
        NoteBytesMap metadata
    ) {
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
    public NoteBytesMap getMetadata() { return metadata; }
    
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
            dependencies, autoload, NoteBytes.fromJson(json).getAsMap());
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
            JsonArray deps = new JsonArray();
            dependencies.forEach(deps::add);
            json.add("dependencies", deps);
        }
        
        json.addProperty("autoload", autoload);
        
        // Merge additional metadata
        if (metadata != null) {
            for (NoteBytes keyBytes : metadata.keySet()) {
                String key = keyBytes.getAsString();
                if (!json.has(key)) {
                    json.add(key, NoteBytes.toJson(metadata.get(key)));
                }
            }
        }
        
        return json;
    }

     public NoteBytesObject toNoteBytes() {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.NAME, name);
        map.put(Keys.VERSION, version);
        map.put(Keys.TYPE, type);
        if (entry != null) map.put(Keys.ENTRY, entry);
        
        if (!dependencies.isEmpty()) {
            com.google.gson.JsonArray deps = new com.google.gson.JsonArray();
            dependencies.forEach(deps::add);
            map.put(Keys.DEPENDENCIES, deps);
        }
        
        map.put(Keys.AUTOLOAD, autoload);
        
        // Merge additional metadata
        if (metadata != null) {
            for (NoteBytes key : metadata.keySet()) {
                if (!map.has(key)) {
                    map.put(key, metadata.get(key));
                }
            }
        }
        
        return map.getNoteBytesObject();
    }

    public static PackageManifest fromNoteBytes(NoteBytesObject obj) {
        return fromNoteBytes(obj.getAsMap());
    }

    public static PackageManifest fromNoteBytes(NoteBytesMap map) {
        String name = map.get(Keys.NAME).getAsString();
        String version = map.get(Keys.VERSION).getAsString();
        String type = map.getOrDefault(Keys.TYPE, OSGI_BUNDLE).getAsString();
        NoteBytes entryBytes = map.get(Keys.ENTRY);
        String entry = entryBytes != null ? entryBytes.getAsString() : null;
        
        List<String> dependencies = new ArrayList<>();
        NoteBytes dependenciesBytes = map.get(Keys.DEPENDENCIES);

        if (dependenciesBytes != null && dependenciesBytes.getType() == NoteBytesMetaData.NOTE_BYTES_ARRAY_TYPE) {
            NoteBytesArray noteBytesArray = dependenciesBytes.getAsNoteBytesArray();
            NoteBytes[] array = noteBytesArray.getAsArray();

            for(NoteBytes v: array)
                dependencies.add(v.getAsString());
        }
        NoteBytes autoloadBytes = map.get(Keys.AUTOLOAD);
        boolean autoload = autoloadBytes != null ? autoloadBytes.getAsBoolean() : false;        
        return new PackageManifest(name, version, type, entry, 
            dependencies, autoload, map);
    }
}