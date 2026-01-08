package io.netnotes.engine.core.system.control.ui;


import io.netnotes.engine.core.system.control.containers.ContainerCommands;
import io.netnotes.engine.core.system.control.containers.ContainerId;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.collections.NoteBytesMap;

/**
 * BatchBuilder - Build atomic batches of container commands
 * 
 */
public class BatchBuilder {
    
    protected final ContainerId containerId;
    protected final NoteBytes rendererId;
    protected final NoteBytesArray commands;
    protected final long generation;
    
    public BatchBuilder(ContainerId containerId, NoteBytes rendererId, long generation) {
        this.containerId = containerId;
        this.rendererId = rendererId;
        this.generation = generation;
        this.commands = new NoteBytesArray();
    }
    
    /**
     * Build final batch command
     */
    public NoteBytesMap build() {
        NoteBytesMap batchCommand = new NoteBytesMap();
        batchCommand.put(Keys.CMD, ContainerCommands.CONAINER_BATCH);
        batchCommand.put(ContainerCommands.CONTAINER_ID, containerId.toNoteBytes());
        batchCommand.put(ContainerCommands.RENDERER_ID, rendererId);
        batchCommand.put(ContainerCommands.GENERATION, generation);
        batchCommand.put(ContainerCommands.BATCH_COMMANDS, commands);
        return batchCommand;
    }
    
    /**
     * Add raw command to batch
     */
    public BatchBuilder addCommand(NoteBytesMap cmd) {
        commands.add(cmd.toNoteBytes());
        return this;
    }
    
    /**
     * Add raw command bytes to batch
     */
    public BatchBuilder addCommand(NoteBytes cmd) {
        commands.add(cmd);
        return this;
    }
    
    /**
     * Get generation this batch is for
     */
    public long getGeneration() {
        return generation;
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
    
}