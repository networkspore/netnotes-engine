package io.netnotes.engine.ui.renderer;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesArray;
import io.netnotes.noteBytes.NoteBytesArrayReadOnly;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.engine.ui.SpatialRegion;
import io.netnotes.engine.ui.SpatialRegionPool;
import io.netnotes.engine.ui.containers.ContainerCommands;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;


/**
 * BatchBuilder - Build atomic batches of container commands
 * 
 * Generic over spatial region type to support 2D and future renderers
 * 
 * @param <S> SpatialRegion type (UIRectangle for 2D)
 */
public abstract class BatchBuilder
    <S extends SpatialRegion<?,S>
> implements AutoCloseable {

    protected final NoteBytesArray commands;
    protected final Deque<S> clipStack;
    protected final SpatialRegionPool<S> regionPool;
    
    protected BatchBuilder(SpatialRegionPool<S> regionPool) {
        this.commands = new NoteBytesArray();
        this.clipStack = new ArrayDeque<>();
        this.regionPool = regionPool;
    }
    
    /**
     * Push a clip region onto the stack
     * All subsequent rendering operations should respect this clip region
     */
    public void pushClipRegion(S region) {
        clipStack.push(region);
    }
    
    /**
     * Pop the current clip region
     */
    public S popClipRegion() {
        return !clipStack.isEmpty() ? clipStack.pop() : null;
    }
    
    /**
     * Get the current clip region (null if none)
     */
    public S getCurrentClipRegion() {
        return clipStack.isEmpty() ? null : clipStack.peek();
    }
    
    /**
     * Check if a region intersects the current clip region
     * Returns true if no clip region is set
     */
    protected boolean intersectsClip(S region) {
        if (clipStack.isEmpty()) return true;
        return clipStack.peek().intersects(region);
    }
    
    /**
     * Check if fully outside current clip region
     */
    protected boolean outsideClip(S region) {
        if (clipStack.isEmpty()) return false;
        return !clipStack.peek().intersects(region);
    }

    /**
     * Build final batch command
     */
    public NoteBytesObject build(S contentBounds, List<S> damageRegions) {
        NoteBytesMap map = new NoteBytesMap();
        map.put(Keys.CMD, ContainerCommands.CONAINER_BATCH);
        if(contentBounds != null){
            map.put(ContainerCommands.CONTENT_BOUNDS, contentBounds.toNoteBytes());
        }
        if(damageRegions != null && !damageRegions.isEmpty()){
            NoteBytes[] regionArray = new NoteBytes[damageRegions.size()];
            for(int i = 0; i< damageRegions.size() ; i++){
                S region = damageRegions.get(i);
                regionArray[i] = region.toNoteBytes();
            }
            map.put(ContainerCommands.DAMAGE_REGIONS, new NoteBytesArrayReadOnly(regionArray));
        }
        map.put(ContainerCommands.BATCH_COMMANDS, commands);
    
        return map.toNoteBytes();
    }
    
    /**
     * Add raw command to batch
     */
    public void addCommand(NoteBytesMap cmd) {
        commands.add(cmd.toNoteBytes());
    }
    
    /**
     * Add raw command bytes to batch
     */
    public void addCommand(NoteBytes cmd) {
        commands.add(cmd);
    }
    
    /**
     * Get number of commands in batch
     */
    public int getCommandCount() {
        return commands.size();
    }
    
    /**
     * Check if batch is empty
     */
    public boolean isBatchEmpty() {
        return commands.isEmpty();
    }
    
    /**
     * Clear all commands and clip regions
     */
    public void clearBatch() {
        commands.clear();
        while (!clipStack.isEmpty()) {
            regionPool.recycle(clipStack.pop());
        }
    }

    @Override
    public void close(){
        clearBatch();
    }
}