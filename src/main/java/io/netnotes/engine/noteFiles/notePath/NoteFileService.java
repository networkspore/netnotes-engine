package io.netnotes.engine.noteFiles.notePath;

import io.netnotes.engine.core.SettingsData;
import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.messaging.NoteMessaging.Keys;
import io.netnotes.engine.messaging.NoteMessaging.ProtocolMesssages;
import io.netnotes.engine.messaging.task.ProgressMessage;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;
import io.netnotes.engine.noteBytes.processing.AsyncNoteBytesWriter;
import io.netnotes.engine.noteFiles.ManagedNoteFileInterface;
import io.netnotes.engine.noteFiles.NoteFile;
import io.netnotes.engine.utils.streams.StreamUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class NoteFileService extends NotePathFactory {
    private final Map<NoteStringArrayReadOnly, ManagedNoteFileInterface> m_registry = new ConcurrentHashMap<>();

    public NoteFileService(SettingsData settingsData) {
        super(settingsData);
    }
    
    public CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly notePath) {

        ManagedNoteFileInterface existing = m_registry.get(notePath);
        if (existing != null) {
            return CompletableFuture.completedFuture(new NoteFile(notePath, existing));
        }

        return acquireLock()
            .thenCompose(filePathLedger ->super.getNoteFilePath(filePathLedger, notePath))
            .thenApply(filePath -> {
                ManagedNoteFileInterface noteFileInterface = m_registry.computeIfAbsent(notePath,
                    k -> new ManagedNoteFileInterface(k, filePath, this));
                return new NoteFile(notePath, noteFileInterface);
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

    public CompletableFuture<File> prepareAllFoShutdown() {
        return acquireLock()
            .thenCompose(filePathLedger -> {
                List<CompletableFuture<Void>> lockFutures = m_registry.values().stream()
                    .map(ManagedNoteFileInterface::perpareForShutdown)
                    .collect(Collectors.toList());
                
                return CompletableFuture.allOf(lockFutures.toArray(new CompletableFuture[0])).thenApply(v-> filePathLedger);
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
        ProgressMessage.writeAsync(ProtocolMesssages.STARTING, 0, 4, "Aquiring file locks", 
            progressWriter);

        return prepareAllForKeyUpdate()
            .thenCompose(filePathLedger->
                super.updateFilePathLedgerEncryption(filePathLedger, progressWriter, 
                    oldPassword, newPassword, batchSize)).whenComplete((result, throwable) -> {
                        ProgressMessage.writeAsync(ProtocolMesssages.STOPPING, 0, -1, "Releasing locks", 
                            progressWriter);
                        completeKeyUpdateForAll();
                        StreamUtils.safeClose(progressWriter);
                });
    }


    public CompletableFuture<Void> prepareForShutdown(NoteStringArrayReadOnly path, boolean recursive){
        List<ManagedNoteFileInterface> interfaceList = new ArrayList<>();
        return prepareForShutdown(path, recursive, interfaceList).thenAccept(v->{
            for(ManagedNoteFileInterface managedInterface : interfaceList){
                if(managedInterface.isLocked()){
                    managedInterface.releaseLock();
                }
            }
        });
    }
   
    public CompletableFuture<Void> prepareForShutdown(NoteStringArrayReadOnly path, boolean recursive, List<ManagedNoteFileInterface> interfaceList){
   
        if(recursive){
            List<CompletableFuture<Void>> list = new ArrayList<>();
            
            Iterator<Map.Entry<NoteStringArrayReadOnly,ManagedNoteFileInterface>> iterator = m_registry.entrySet().iterator();
            while(iterator.hasNext()) {
                Map.Entry<NoteStringArrayReadOnly,ManagedNoteFileInterface> entry = iterator.next();
                list.add(entry.getValue().perpareForShutdown());
                if(interfaceList != null){
                    interfaceList.add(entry.getValue());
                }
                iterator.remove(); 
            }
            
            return CompletableFuture.allOf(list.toArray(new CompletableFuture[0]));
        }else{
            ManagedNoteFileInterface managedInterface = m_registry.remove(path);
            if(managedInterface != null){
                if(interfaceList != null){
                    interfaceList.add(managedInterface);
                }
                return managedInterface.perpareForShutdown();
            }else{
                return CompletableFuture.completedFuture(null);
            }
        }
        
    }


    public CompletableFuture<NotePath> deleteNoteFilePath(NoteStringArrayReadOnly path, boolean recursive, 
        AsyncNoteBytesWriter progressWriter
     ) {

        if(path == null ||  path.byteLength() == 0 || path.size() == 0){
            StreamUtils.safeClose(progressWriter);
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid path provided"));
        }

        if(progressWriter != null){
            ProgressMessage.writeAsync(ProtocolMesssages.STARTING,
                    0, 4, "Aquiring lock", progressWriter);
        }
         List<ManagedNoteFileInterface> interfaceList = new ArrayList<>();

        return acquireLock().thenCompose((filePathLedger)->{
            
            if(!filePathLedger.exists() || !filePathLedger.isFile() || 
                filePathLedger.length() <= CryptoService.AES_IV_SIZE){
                throw new IllegalArgumentException("Invalid ledger, inaccessible or insufficient size provided");
            }

            NotePath notePath = new NotePath(filePathLedger, path, recursive, progressWriter);

            notePath.progressMsg(ProtocolMesssages.STARTING,2, 4,
                "Initial lock aquired, preparing registry interfaces");
            return prepareForShutdown(path, recursive, interfaceList).thenCompose(v->deleteNoteFilePath(notePath));
        }).whenComplete((notePath, ex)->{
            int toRemoveSize = interfaceList.size();
            for(int i = 0; i < toRemoveSize; i++){
                ManagedNoteFileInterface managedInterface= interfaceList.get(i);
                boolean isDeleted = !managedInterface.isFile();
                
                if(managedInterface.isLocked()){
                    managedInterface.releaseLock();
                }
                
                notePath.progressMsg(ProtocolMesssages.STOPPING, i, toRemoveSize, managedInterface.getId().getAsString(), new NoteBytesPair[]{
                    new NoteBytesPair(Keys.STATUS_KEY, 
                        !managedInterface.isLocked() && isDeleted ? 
                            ProtocolMesssages.SUCCESS : ProtocolMesssages.FAILED
                    )
                });
                
            }
            
            StreamUtils.safeClose(progressWriter);
            releaseLock();
            
        });
      

    }
        
        
                
}