package io.netnotes.engine;

import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class NoteFileRegistry {
    private final Map<NoteStringArrayReadOnly, ManagedNoteFileInterface> m_registry = new ConcurrentHashMap<>();
    private final AppData m_appData;
    
    public NoteFileRegistry(AppData appData) {
        this.m_appData = appData;
    }
    
    public CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path) {
        return m_appData.getIdDataFile(path)
            .thenApply(file -> {
                ManagedNoteFileInterface noteFileInterface = m_registry.computeIfAbsent(path, 
                    k -> new ManagedNoteFileInterface(file, m_appData, this, path));
                return new NoteFile(path, noteFileInterface);
            });
    }
    
    // Called by ManagedNoteFileInterface when it has no more references
    void cleanupInterface(NoteStringArrayReadOnly path, ManagedNoteFileInterface expectedInterface) {
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
}