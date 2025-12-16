package io.netnotes.engine.core.system.control.containers;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
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
    private Integer width = null;
    private Integer height = null;
    private Integer x = null;
    private Integer y = null;
    private Boolean resizable = null;
    private Boolean closable = null;
    private Boolean movable = null;
    private Boolean minimizable = null;
    private Boolean maximizable = null;
    private NoteBytes icon = null;
    private NoteBytesMap metadata = null;
    
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
    
    public ContainerConfig withIcon(NoteBytes icon) {
        this.icon = icon;
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
    public NoteBytes getIcon() { return icon; }
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
        
        return map.toNoteBytes();
    }

    public static ContainerConfig fromNoteBytes(NoteBytes noteBytes) {
        return fromNoteBytes(noteBytes.getAsMap());
    }
    
    public static ContainerConfig fromNoteBytes(NoteBytesMap map) {
        ContainerConfig config = new ContainerConfig();
        NoteBytes widthBytes = map.get(Keys.WIDTH);
        NoteBytes heightBytes = map.get(Keys.HEIGHT);
        NoteBytes xBytes = map.get(ContainerCommands.X);
        NoteBytes yBytes = map.get(ContainerCommands.Y);
        NoteBytes resizableBytes = map.get(ContainerCommands.RESIZABLE);
        NoteBytes closableBytes = map.get(ContainerCommands.CLOSABLE);
        NoteBytes movableBytes = map.get(ContainerCommands.MOVABLE);
        NoteBytes minimizableBytes = map.get(ContainerCommands.MINIMIZABLE);
        NoteBytes maximizableBytes = map.get(ContainerCommands.MAXIMIZABLE);
        NoteBytes iconBytes = map.get(ContainerCommands.ICON);
        NoteBytes metadataBytes = map.get( ContainerCommands.METADATA);

        if (widthBytes != null) config.width = widthBytes.getAsInt();
        if (heightBytes != null) config.height = heightBytes.getAsInt();
        if (xBytes != null) config.x = xBytes.getAsInt();
        if (yBytes != null) config.y = yBytes.getAsInt();
        if (resizableBytes != null) config.resizable = resizableBytes.getAsBoolean();
        if (closableBytes != null) config.closable = closableBytes.getAsBoolean();
        if (movableBytes != null) config.movable = movableBytes.getAsBoolean();
        if (minimizableBytes != null) config.minimizable = minimizableBytes.getAsBoolean();
        if (maximizableBytes != null) config.maximizable = maximizableBytes.getAsBoolean();
        if (iconBytes != null) config.icon = iconBytes;
        if (metadataBytes != null) config.metadata = metadataBytes.getAsNoteBytesMap();
        
        return config;
    }
}