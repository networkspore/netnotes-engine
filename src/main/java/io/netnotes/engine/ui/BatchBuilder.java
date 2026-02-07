package io.netnotes.engine.ui;

import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.noteBytes.NoteBytes;
import io.netnotes.noteBytes.NoteBytesArray;
import io.netnotes.noteBytes.NoteBytesObject;
import io.netnotes.noteBytes.collections.NoteBytesMap;
import io.netnotes.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.ui.containers.ContainerCommands;

import java.util.ArrayDeque;
import java.util.Deque;


/**
 * BatchBuilder - Build atomic batches of container commands
 * 
 * Generic over spatial region type to support 2D and future renderers
 * 
 * @param <S> SpatialRegion type (UIRectangle for 2D)
 */
public abstract class BatchBuilder
    <S extends SpatialRegion<?,S>
> {

    protected final NoteBytesArray commands;
    protected final Deque<S> clipStack;
    
    protected BatchBuilder() {
        this.commands = new NoteBytesArray();
        this.clipStack = new ArrayDeque<>();
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
    public void popClipRegion() {
        if (!clipStack.isEmpty()) {
            clipStack.pop();
        }
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
    public NoteBytesObject build() {
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Keys.CMD, ContainerCommands.CONAINER_BATCH),
            new NoteBytesPair(ContainerCommands.BATCH_COMMANDS, commands)
        });
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
    public boolean isEmpty() {
        return commands.isEmpty();
    }
    
    /**
     * Clear all commands and clip regions
     */
    public void clear() {
        commands.clear();
        clipStack.clear();
    }
}