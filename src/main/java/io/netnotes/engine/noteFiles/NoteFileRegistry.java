package io.netnotes.engine.noteFiles;

import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;

import java.io.File;
import java.util.ArrayList;
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

        ManagedNoteFileInterface existing = m_registry.get(path);
        if (existing != null) {
            return CompletableFuture.completedFuture(new NoteFile(path, existing));
        }

        return acquireLock()
            .thenCompose(filePathLedger ->super.getNoteFilePath(filePathLedger,path))
            .thenApply(noteFilePath -> {
                ManagedNoteFileInterface noteFileInterface = m_registry.computeIfAbsent(path,
                    k -> new ManagedNoteFileInterface(noteFilePath, this, k));
                return new NoteFile(path, noteFileInterface);
            }).whenComplete((result, failure)->{
                releaseLock();
            });
    }
    
    // Called by ManagedNoteFileInterface when it has no more references
    public void cleanupInterface(NoteStringArrayReadOnly path, ManagedNoteFileInterface expectedInterface) {
        // Use atomic remove to ensure we only remove the expected interface
        m_registry.remove(path, expectedInterface);
    }
    
    // For key updates - acquire locks on all registered interfaces
    public CompletableFuture<File> prepareAllForKeyUpdate() {
        return acquireLock()
            .thenCompose(filePathLedger -> {
                List<CompletableFuture<Void>> lockFutures = m_registry.values().stream()
                    .map(ManagedNoteFileInterface::prepareForKeyUpdate)
                    .collect(Collectors.toList());
                
                return CompletableFuture.allOf(lockFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> filePathLedger);
            });
    }
    
    public void completeKeyUpdateForAll() {
        m_registry.values().forEach(ManagedNoteFileInterface::completeKeyUpdate);
        releaseLock();
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

 
    public CompletableFuture<NoteBytesObject> updateFilePathLedgerEncryption(
        AsyncNoteBytesWriter progressWriter,
        NoteBytesEphemeral oldPassword,
        NoteBytesEphemeral newPassword,
        int batchSize
    ) {
        ProgressMessage.writeAsync(NoteMessaging.Status.STARTING, 0, 4, "Aquiring file locks", 
            progressWriter);

        return prepareAllForKeyUpdate()
            .thenCompose(filePathLedger->
                super.updateFilePathLedgerEncryption(filePathLedger, progressWriter, 
                    oldPassword, newPassword, batchSize)).whenComplete((result, throwable) -> {
                        ProgressMessage.writeAsync(NoteMessaging.Status.STOPPING, 0, -1, "Releasing locks", 
                            progressWriter);
                        completeKeyUpdateForAll();
                        StreamUtils.safeClose(progressWriter);
                });
    }



    public CompletableFuture<Void> deleteNoteFilePath(NoteStringArrayReadOnly path, boolean recursive) {

        return acquireLock().thenCompose((filePathLedger)->{
                if(recursive){
                    List<CompletableFuture<Void>> list = new ArrayList<>();
                    List<NoteStringArrayReadOnly> toRemove = new ArrayList<>();
                    for(Map.Entry<NoteStringArrayReadOnly,ManagedNoteFileInterface> entry : m_registry.entrySet()) {
                        if(entry.getKey().arrayStartsWith(path)){
                            list.add(entry.getValue().perpareForDeletion());
                            toRemove.add(entry.getKey());
                        }
                    }
                    return CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).thenCompose(v->{
                        toRemove.forEach(key->{
                            ManagedNoteFileInterface managedInterface = m_registry.remove(key);
                            managedInterface.releaseLock();
                        });
                        return super.deleteNoteFilePath(filePathLedger, path, recursive);
                    });
                }else{
                    ManagedNoteFileInterface managedInterface = m_registry.get(path);
                    if(managedInterface != null){
                        return managedInterface.perpareForDeletion().thenCompose(v->{
                            m_registry.remove(path, managedInterface);
                            managedInterface.releaseLock();
                            return super.deleteNoteFilePath(filePathLedger, path, recursive);
                        });
                    }else{
                        return super.deleteNoteFilePath(filePathLedger, path, recursive);
                    }
                }
            }).whenComplete((v, ex)->{
                releaseLock();
            });
      

    }
        
        
                
}