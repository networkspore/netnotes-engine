package io.netnotes.engine.core.system.control.containers;

import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;


/**
 * ContainerConfig - Configuration for container creation
 * 
 * Abstract properties that UI implements as appropriate:
 * - Size hints (width/height) → Actual pixels or layout weight
 * - Position hints (x/y) → Screen coords or relative position
 * - Flags (resizable, closable) → UI capabilities
 */
public class ContainerConfig {
    private Integer width;
    private Integer height;
    private Integer x;
    private Integer y;
    private Boolean resizable;
    private Boolean closable;
    private Boolean movable;
    private Boolean minimizable;
    private Boolean maximizable;
    private String icon;
    private NoteBytesMap metadata;
    
    public ContainerConfig() {
        // Defaults
        this.resizable = true;
        this.closable = true;
        this.movable = true;
        this.minimizable = true;
        this.maximizable = true;
    }
    
    // ===== BUILDER STYLE =====
    
    public ContainerConfig withSize(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }
    
    public ContainerConfig withPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }
    
    public ContainerConfig withResizable(boolean resizable) {
        this.resizable = resizable;
        return this;
    }
    
    public ContainerConfig withClosable(boolean closable) {
        this.closable = closable;
        return this;
    }
    
    public ContainerConfig withMovable(boolean movable) {
        this.movable = movable;
        return this;
    }
    
    public ContainerConfig withMinimizable(boolean minimizable) {
        this.minimizable = minimizable;
        return this;
    }
    
    public ContainerConfig withMaximizable(boolean maximizable) {
        this.maximizable = maximizable;
        return this;
    }
    
    public ContainerConfig withIcon(String iconPath) {
        this.icon = iconPath;
        return this;
    }
    
    public ContainerConfig withMetadata(NoteBytesMap metadata) {
        this.metadata = metadata;
        return this;
    }
    
    // ===== GETTERS =====
    
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public Integer getX() { return x; }
    public Integer getY() { return y; }
    public Boolean isResizable() { return resizable; }
    public Boolean isClosable() { return closable; }
    public Boolean isMovable() { return movable; }
    public Boolean isMinimizable() { return minimizable; }
    public Boolean isMaximizable() { return maximizable; }
    public String getIcon() { return icon; }
    public NoteBytesMap getMetadata() { return metadata; }
    
    // ===== SERIALIZATION =====
    
    public NoteBytesObject toNoteBytes() {
        NoteBytesMap map = new NoteBytesMap();
        
        if (width != null) map.put("width", width);
        if (height != null) map.put("height", height);
        if (x != null) map.put("x", x);
        if (y != null) map.put("y", y);
        if (resizable != null) map.put("resizable", resizable);
        if (closable != null) map.put("closable", closable);
        if (movable != null) map.put("movable", movable);
        if (minimizable != null) map.put("minimizable", minimizable);
        if (maximizable != null) map.put("maximizable", maximizable);
        if (icon != null) map.put("icon", icon);
        if (metadata != null) map.put("metadata", metadata);
        
        return map.getNoteBytesObject();
    }
    
    public static ContainerConfig fromNoteBytes(NoteBytesMap map) {
        ContainerConfig config = new ContainerConfig();
        
        if (map.has("width")) config.width = map.get("width").getAsInt();
        if (map.has("height")) config.height = map.get("height").getAsInt();
        if (map.has("x")) config.x = map.get("x").getAsInt();
        if (map.has("y")) config.y = map.get("y").getAsInt();
        if (map.has("resizable")) config.resizable = map.get("resizable").getAsBoolean();
        if (map.has("closable")) config.closable = map.get("closable").getAsBoolean();
        if (map.has("movable")) config.movable = map.get("movable").getAsBoolean();
        if (map.has("minimizable")) config.minimizable = map.get("minimizable").getAsBoolean();
        if (map.has("maximizable")) config.maximizable = map.get("maximizable").getAsBoolean();
        if (map.has("icon")) config.icon = map.get("icon").getAsString();
        if (map.has("metadata")) config.metadata = map.get("metadata").getAsNoteBytesMap();
        
        return config;
    }
}