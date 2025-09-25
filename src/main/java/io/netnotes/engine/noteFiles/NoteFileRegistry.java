package io.netnotes.engine.noteFiles;

import io.netnotes.engine.ManagedNoteFileInterface;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class NoteFileRegistry extends NotePathFactory {
    private final Map<NoteStringArrayReadOnly, ManagedNoteFileInterface> m_registry = new ConcurrentHashMap<>();

    public NoteFileRegistry(SettingsData settingsData) {
        super(settingsData);
    }
    
    public CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path) {
        return getNoteFilePath(path)
            .thenApply(noteFilePath -> {
                ManagedNoteFileInterface noteFileInterface = m_registry.computeIfAbsent(path, 
                    k -> new ManagedNoteFileInterface(noteFilePath, this, path));
                return new NoteFile(path, noteFileInterface);
            });
    }
    
    // Called by ManagedNoteFileInterface when it has no more references
    public void cleanupInterface(NoteStringArrayReadOnly path, ManagedNoteFileInterface expectedInterface) {
        // Use atomic remove to ensure we only remove the expected interface
        m_registry.remove(path, expectedInterface);
    }
    
    // For key updates - acquire locks on all registered interfaces
    public CompletableFuture<Void> prepareAllForKeyUpdate() {
        List<CompletableFuture<Void>> lockFutures = m_registry.values().stream()
            .map(ManagedNoteFileInterface::prepareForKeyUpdate)
            .collect(Collectors.toList());
            
        return CompletableFuture.allOf(lockFutures.toArray(new CompletableFuture[0]));
    }
    
    public void completeKeyUpdateForAll() {
        m_registry.values().forEach(ManagedNoteFileInterface::completeKeyUpdate);
    }
    
    // Manual cleanup method to remove unused interfaces (for maintenance)
    public int cleanupUnusedInterfaces() {
        int removed = 0;
        Iterator<Map.Entry<NoteStringArrayReadOnly, ManagedNoteFileInterface>> iterator = 
            m_registry.entrySet().iterator();
            
        while (iterator.hasNext()) {
            Map.Entry<NoteStringArrayReadOnly, ManagedNoteFileInterface> entry = iterator.next();
            ManagedNoteFileInterface noteInterface = entry.getValue();
            
            if (noteInterface.getReferenceCount() == 0 && !noteInterface.isLocked()) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }
    
    // Get current registry size for monitoring
    public int getRegistrySize() {
        return m_registry.size();
    }
    
    // Get reference counts for monitoring
    public Map<NoteStringArrayReadOnly, Integer> getReferenceCounts() {
        return m_registry.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().getReferenceCount()
            ));
    }

    @Override
    public CompletableFuture<NoteBytesObject> updateFilePathLedgerEncryption(
        AsyncNoteBytesWriter progressWriter,
        NoteBytesEphemeral oldPassword,
        NoteBytesEphemeral newPassword,
        int batchSize
    ) {
  
            ProgressMessage.writeAsync("noteFileRegistry.updateFilePathLedgerEncryption",0,-1, 
                "Preparing for key update", progressWriter);
            return prepareAllForKeyUpdate()
                .thenCompose(v -> super.updateFilePathLedgerEncryption( progressWriter,
                    oldPassword, newPassword, batchSize))
                .whenComplete((result, throwable) -> {
                    progressWriter.writeAsync(ProgressMessage
                        .getProgressMessage(NoteMessaging.Status.STOPPING, 0, -1, "Releasing locks"));
                    completeKeyUpdateForAll();
                    StreamUtils.safeClose(progressWriter);
                });
        
    }
        
        
                
}