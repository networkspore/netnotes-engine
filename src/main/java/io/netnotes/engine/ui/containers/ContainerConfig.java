package io.netnotes.engine.ui.containers;

import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.noteBytes.NoteBoolean;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.collections.NoteBytesMap;


/**
 * ContainerConfig - Configuration for container creation
 * 
 * Abstract properties that UI implements as appropriate:
 * - Size hints (width/height) → Actual pixels or layout weight
 * - Position hints (x/y) → Screen coords or relative position
 * - Flags (resizable, closable) → UI capabilities
 */
public abstract class ContainerConfig <
    S extends SpatialRegion<?,S>,
    CCFG extends ContainerConfig<S,CCFG>
> {
    private S initialRegion = null;
    private Boolean resizable = null;
    private Boolean closable = null;
    private Boolean movable = null;
    private Boolean minimizable = null;
    private Boolean maximizable = null;
    private NoteBytes icon = null;
    private NoteBytesMap metadata = null;
    private boolean isVisible = true;
    private boolean isFocused = true;
    
    public ContainerConfig(NoteBytesMap map){
        if(map == null){
            return;
        }
        NoteBytes regionBytes = map.get(ContainerCommands.REGION);
        NoteBytes resizableBytes = map.get(ContainerCommands.RESIZABLE);
        NoteBytes closableBytes = map.get(ContainerCommands.CLOSABLE);
        NoteBytes movableBytes = map.get(ContainerCommands.MOVABLE);
        NoteBytes minimizableBytes = map.get(ContainerCommands.MINIMIZABLE);
        NoteBytes maximizableBytes = map.get(ContainerCommands.MAXIMIZABLE);
        NoteBytes iconBytes = map.get(ContainerCommands.ICON);
        NoteBytes metadataBytes = map.get( ContainerCommands.METADATA);
        NoteBytes isVisibleBytes = map.get(ContainerCommands.IS_VISIBLE);
        NoteBytes isFocusedBytes = map.get(ContainerCommands.IS_FOCUSED);

        if(regionBytes != null) initialRegion = createRegionFromnNoteBytes(regionBytes);

        if (resizableBytes != null) resizable = resizableBytes.getAsBoolean();
        if (closableBytes != null) closable = closableBytes.getAsBoolean();
        if (movableBytes != null) movable = movableBytes.getAsBoolean();
        if (minimizableBytes != null) minimizable = minimizableBytes.getAsBoolean();
        if (maximizableBytes != null) maximizable = maximizableBytes.getAsBoolean();
        if (iconBytes != null) icon = iconBytes;
        if (metadataBytes != null) metadata = metadataBytes.getAsNoteBytesMap();
        if (isVisibleBytes != null) isVisible = isVisibleBytes.getAsBoolean();
        if (isFocusedBytes != null) isFocused = isFocusedBytes.getAsBoolean();
        
    }

    protected abstract S createRegionFromnNoteBytes(NoteBytes noteBytes);

    public ContainerConfig() {
        // Defaults
        this.resizable = true;
        this.closable = true;
        this.movable = true;
        this.minimizable = true;
        this.maximizable = true;
    }

    @SuppressWarnings("unchecked")
    protected CCFG self(){
        return (CCFG) this;
    }
    
    // ===== BUILDER STYLE =====
    public boolean isVisible() { return isVisible; }
    public CCFG isVisible(boolean v) { this.isVisible = v; return self(); }
    
    public boolean isFocused() { return isFocused; }
    public CCFG autoFocus(boolean v) { this.isFocused = v; return self(); }
    
    public CCFG withInitialRegion(S region) {
        this.initialRegion = region;
        return self();
    }
    

    public CCFG withResizable(boolean resizable) {
        this.resizable = resizable;
        return self();
    }
    
    public CCFG withClosable(boolean closable) {
        this.closable = closable;
        return self();
    }
    
    public CCFG withMovable(boolean movable) {
        this.movable = movable;
        return self();
    }
    
    public CCFG withMinimizable(boolean minimizable) {
        this.minimizable = minimizable;
        return self();
    }
    
    public CCFG withMaximizable(boolean maximizable) {
        this.maximizable = maximizable;
        return self();
    }
    
    public CCFG withIcon(NoteBytes icon) {
        this.icon = icon;
        return self();
    }
    
    public CCFG withMetadata(NoteBytesMap metadata) {
        this.metadata = metadata;
        return self();
    }
    
    // ===== GETTERS =====
    
    public S initialRegion() { return initialRegion; }
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
        
        if (initialRegion != null) map.put(ContainerCommands.REGION, initialRegion.toNoteBytes());
        if (resizable != null) map.put(ContainerCommands.RESIZABLE, resizable);
        if (closable != null) map.put(ContainerCommands.CLOSABLE, closable);
        if (movable != null) map.put(ContainerCommands.MOVABLE, movable);
        if (minimizable != null) map.put(ContainerCommands.MINIMIZABLE, minimizable);
        if (maximizable != null) map.put(ContainerCommands.MAXIMIZABLE, maximizable);
        if (icon != null) map.put(ContainerCommands.ICON, icon);
        if (metadata != null) map.put(ContainerCommands.METADATA, metadata);
        if (isFocused != true) map.put(ContainerCommands.IS_FOCUSED, NoteBoolean.FALSE);
        if (isVisible != true) map.put(ContainerCommands.IS_VISIBLE, NoteBoolean.FALSE);
        return map.toNoteBytes();
    }

    
}