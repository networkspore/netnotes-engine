package io.netnotes.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteFiles.SettingsData;
import io.netnotes.engine.noteFiles.notePath.NoteFileRegistry;
import io.netnotes.engine.utils.GitHubAPI;
import io.netnotes.engine.utils.UpdateInformation;
import io.netnotes.engine.utils.Utils;
import io.netnotes.engine.utils.GitHubAPI.GitHubAsset;

import javafx.beans.property.SimpleObjectProperty;


public class AppData {

    private final NoteFileRegistry m_noteFileRegistry;
    private final Map<NoteBytesReadOnly, INode> m_nodeRegistry = new ConcurrentHashMap<>();

    private final SettingsData m_settingsData;

    public AppData(SettingsData settingsData) throws Exception{
        m_noteFileRegistry = new NoteFileRegistry(settingsData);
        m_settingsData = settingsData;
    }


    public ExecutorService getExecService(){
        return m_noteFileRegistry.getExecService();
    }

    public ScheduledExecutorService getSchedualedExecService(){
        return m_noteFileRegistry.getScheduledExecutor();
    }

    public Map<NoteBytesReadOnly, INode> nodeRegistry(){
        return m_nodeRegistry;
    }

    public NoteFileRegistry getNoteFileRegistry(){
        return m_noteFileRegistry;
    }

    public Future<?> checkForUpdates(String gitHubUser, String githubProject, SimpleObjectProperty<UpdateInformation> updateInformation){
        GitHubAPI gitHubAPI = new GitHubAPI(gitHubUser, githubProject);
        return gitHubAPI.getAssetsLatestRelease(getExecService(), (onFinished)->{
            UpdateInformation tmpInfo = new UpdateInformation();

                Object finishedObject = onFinished.getSource().getValue();
                if(finishedObject != null && finishedObject instanceof GitHubAsset[] && ((GitHubAsset[]) finishedObject).length > 0){
            
                    GitHubAsset[] assets = (GitHubAsset[]) finishedObject;
              
                    for(GitHubAsset asset : assets){
                        if(asset.getName().equals("releaseInfo.json")){
                            tmpInfo.setReleaseUrl(asset.getUrl());
                            
                        }else{
                            if(asset.getContentType().equals("application/x-java-archive")){
                                if(asset.getName().startsWith("netnotes-")){
                                   
                                    tmpInfo.setJarName(asset.getName());
                                    tmpInfo.setTagName(asset.getTagName());
                                    tmpInfo.setJarUrl(asset.getUrl());
                                                                
                                }
                            }
                        }
                    }

                    Utils.getUrlJson(tmpInfo.getReleaseUrl(), getExecService(), (onReleaseInfo)->{
                        Object sourceObject = onReleaseInfo.getSource().getValue();
                        if(sourceObject != null && sourceObject instanceof com.google.gson.JsonObject){
                            com.google.gson.JsonObject releaseInfoJson = (com.google.gson.JsonObject) sourceObject;
                            UpdateInformation upInfo = new UpdateInformation(tmpInfo.getJarUrl(),tmpInfo.getTagName(),tmpInfo.getJarName(),null,tmpInfo.getReleaseUrl());
                            upInfo.setReleaseInfoJson(releaseInfoJson);
             
                            updateInformation.set(upInfo);
                        }
                    }, (releaseInfoFailed)->{

                    });
                    
                 

                }
            },(onFailed)->{

            });

    }



    // Refactored main method with proper CompletableFuture chaining
   


  
    /* 
                 
    public CompletableFuture<Boolean> removeIdDataFile(NoteStringArrayReadOnly path) {
        AtomicBoolean lockAcquired = new AtomicBoolean(false);
        
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    getDataSemaphore().acquire();
                    lockAcquired.set(true);
                    return getIdDataFile();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted", e);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to get ID data file", e);
                }
            }, getExecService())
            .thenCompose(idDataFile -> {
                File dataDir = idDataFile.getParentFile();
                
                PipedOutputStream decryptedOutput = new PipedOutputStream();
                performDecryption(idDataFile, decryptedOutput);
                
                PipedOutputStream parsedOutput = new PipedOutputStream();
                parseIdDataFileRemoveFile(path, dataDir, decryptedOutput, parsedOutput);
            })
            .whenComplete((result, throwable) -> {
                // Always release semaphore
                if (lockAcquired.getAndSet(false)) {
                    getDataSemaphore().release();
                }
                
                if (throwable != null) {
                   // Utils.writeLogMsg("AppData.getIdDataFileAsync", throwable);
                }
            });

   
    }

    private CompletableFuture<Boolean> parseIdDataFileRemoveFile(NoteStringArrayReadOnly path, File dataDir, PipedOutputStream decryptedOutputStream, 
                                           PipedOutputStream parsedOutputStream) {
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException {
                // Validate inputs
                if (path == null || path.byteLength() == 0 || dataDir == null || decryptedOutputStream == null || parsedOutputStream == null) {
                    throw new IllegalArgumentException("Required parameters cannot be null");
                }
                
                List<NoteBytes> targetPath = path.getAsList();
                
                try (
                    PipedInputStream parsedInputStream = new PipedInputStream(decryptedOutputStream);
                    NoteBytesReader reader = new NoteBytesReader(parsedInputStream);
                    NoteBytesWriter writer = new NoteBytesWriter(parsedOutputStream)
                ) {
                    // Read root metadata
                    NoteBytesMetaData rootMetaData = reader.nextMetaData();
                    if (rootMetaData == null) {
                        // Empty file case - nothing to remove
                        if (onSucceeded != null) {
                            Utils.returnObject(false, getExecService(), onSucceeded); // false = not found
                        }
                        return false;
                    }
                    
                    // Parse existing tree and remove target path
                    boolean removed = parseAndRemoveFromTree(reader, writer, rootMetaData, targetPath);
                    
                    if (onSucceeded != null) {
                        Utils.returnObject(removed, getExecService(), onSucceeded);
                    }
                    return removed;
                }
            }
        };
        return getExecService().submit(task);
    }

    private boolean parseAndRemoveFromTree(NoteBytesReader reader, NoteBytesWriter writer, NoteBytesMetaData rootMetaData, 
                                        List<NoteBytes> targetPath) throws IOException {
        
        if (rootMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            throw new IOException("Root must be an object");
        }
        
        // If target path is empty, we're trying to remove the root - delete everything
        if (targetPath.isEmpty()) {
            deleteAllFilesInObject(reader, rootMetaData.getLength());
            // Write empty root object
            NoteBytesObject emptyRoot = new NoteBytesObject();
            writer.write(emptyRoot);
            return true;
        }
        
        // Write root metadata
        writer.write(rootMetaData);
        
        // Parse the root object and remove target path
        return parseObjectAndRemovePath(reader, writer, rootMetaData.getLength(), targetPath, 0);
    }

    private boolean parseObjectAndRemovePath(NoteBytesReader reader, NoteBytesWriter writer, int objectLen, 
                                            List<NoteBytes> targetPath, int pathIndex) throws IOException {
        
        int bytesRead = 0;
        List<NoteBytesPair> pendingPairs = new ArrayList<>();
        boolean foundAndRemoved = false;
        
        // Read all key-value pairs in this object first
        while (bytesRead < objectLen) {
            // Read key metadata
            NoteBytesMetaData keyMetaData = reader.nextMetaData();
            if (keyMetaData == null || keyMetaData.getType() != NoteBytesMetaData.STRING_TYPE) {
                throw new IOException("Expected string key");
            }
            
            // Read key data
            byte[] keyData = reader.readByteAmount(keyMetaData.getLength());
            bytesRead += 5 + keyMetaData.getLength();
            
            // Read value metadata
            NoteBytesMetaData valueMetaData = reader.nextMetaData();
            if (valueMetaData == null) {
                throw new IOException("Expected value after key");
            }
            
            // Read value data
            byte[] valueData = reader.readByteAmount(valueMetaData.getLength());
            bytesRead += 5 + valueMetaData.getLength();
            
            // Create NoteBytes objects and store the pair
            NoteBytes key = new NoteBytes(keyData, ByteDecoding.of(keyMetaData.getType()));
            NoteBytes value = new NoteBytes(valueData, ByteDecoding.of(valueMetaData.getType()));
            pendingPairs.add(new NoteBytesPair(key, value));
        }
        
        // Process pairs - look for target to remove
        for (NoteBytesPair pair : pendingPairs) {
            boolean isTargetKey = (pathIndex < targetPath.size()) && 
                                Arrays.equals(pair.getKey().get(), targetPath.get(pathIndex).get());
            
            if (isTargetKey) {
                if (pathIndex == targetPath.size() - 1) {
                    // This is the target to remove - delete all files in this subtree
                    foundAndRemoved = true;
                    deleteAllFilesInValue(pair.getValue());
                    // Don't write this pair - effectively removing it
                } else {
                    // Continue down the path for removal
                    if (pair.getValue().getByteDecoding().getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                        throw new IOException("Expected object for intermediate path element");
                    }
                    
                    // Process the nested object to remove deeper path
                    boolean removedFromNested = parseNestedObjectAndRemove(pair.getValue().get(), targetPath, pathIndex + 1);
                    
                    if (removedFromNested) {
                        foundAndRemoved = true;
                        
                        // Check if the nested object is now empty after removal
                        if (pair.getValue().isEmpty()) {
                            // Don't write this pair - remove the entire empty branch
                        } else {
                            // Write the key and the modified nested object
                            writer.write(pair.getKey());
                            writeModifiedNestedObject(writer, pair.getValue().get(), targetPath, pathIndex + 1);
                        }
                    } else {
                        // Path not found in nested object - write as-is
                        writer.write(pair.getKey());
                        writer.write(pair.getValue());
                    }
                }
            } else {
                // Not our target key - copy as-is
                writer.write(pair.getKey());
                writer.write(pair.getValue());
            }
        }
        
        return foundAndRemoved;
    }

    private boolean parseNestedObjectAndRemove(byte[] objectData, List<NoteBytes> targetPath, int pathIndex) throws IOException {
        // Create a temporary reader for the nested object data
        ByteArrayInputStream bais = new ByteArrayInputStream(objectData);
        NoteBytesReader nestedReader = new NoteBytesReader(bais);
        
        // Use a ByteArrayOutputStream to capture what we write for the nested object
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NoteBytesWriter nestedWriter = new NoteBytesWriter(baos);
        
        // Parse the nested object content
        return parseObjectContentAndRemove(nestedReader, nestedWriter, objectData.length, targetPath, pathIndex);
    }

    private boolean parseObjectContentAndRemove(NoteBytesReader reader, NoteBytesWriter writer, int objectLen,
                                            List<NoteBytes> targetPath, int pathIndex) throws IOException {
        
        int bytesRead = 0;
        boolean foundAndRemoved = false;
        
        while (bytesRead < objectLen) {
            // Read key metadata
            NoteBytesMetaData keyMetaData = reader.nextMetaData();
            if (keyMetaData == null) break;
            
            // Read key data
            byte[] keyData = reader.readByteAmount(keyMetaData.getLength());
            bytesRead += 5 + keyMetaData.getLength();
            
            // Read value metadata
            NoteBytesMetaData valueMetaData = reader.nextMetaData();
            if (valueMetaData == null) break;
            
            // Read value data
            byte[] valueData = reader.readByteAmount(valueMetaData.getLength());
            bytesRead += 5 + valueMetaData.getLength();
            
            // Check if this is our target key
            boolean isTargetKey = (pathIndex < targetPath.size()) && 
                                Arrays.equals(keyData, targetPath.get(pathIndex).get());
            
            if (isTargetKey) {
                if (pathIndex == targetPath.size() - 1) {
                    // This is the target to remove
                    foundAndRemoved = true;
                    deleteAllFilesInValueData(valueMetaData, valueData);
                    // Don't write this pair - effectively removing it
                } else {
                    // Continue down the path
                    if (valueMetaData.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                        boolean removedFromNested = parseNestedObjectAndRemove(valueData, targetPath, pathIndex + 1);
                        
                        if (removedFromNested) {
                            foundAndRemoved = true;
                            
                            // Check if nested object is now empty
                            if (isObjectDataEmpty(valueData)) {
                                // Don't write this pair - remove empty branch
                            } else {
                                // Write key and modified nested object
                                writer.write(keyMetaData);
                                writer.write(keyData);
                                writeModifiedNestedObjectData(writer, valueData, targetPath, pathIndex + 1);
                            }
                        } else {
                            // Write as-is
                            writer.write(keyMetaData);
                            writer.write(keyData);
                            writer.write(valueMetaData);
                            writer.write(valueData);
                        }
                    } else {
                        // Path mismatch - write as-is
                        writer.write(keyMetaData);
                        writer.write(keyData);
                        writer.write(valueMetaData);
                        writer.write(valueData);
                    }
                }
            } else {
                // Not target key - copy as-is
                writer.write(keyMetaData);
                writer.write(keyData);
                writer.write(valueMetaData);
                writer.write(valueData);
            }
        }
        
        return foundAndRemoved;
    }

    private void writeModifiedNestedObject(NoteBytesWriter writer, byte[] originalObjectData, 
                                        List<NoteBytes> targetPath, int pathIndex) throws IOException {
        // Parse the original object and remove the target path
        ByteArrayInputStream bais = new ByteArrayInputStream(originalObjectData);
        NoteBytesReader nestedReader = new NoteBytesReader(bais);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NoteBytesWriter nestedWriter = new NoteBytesWriter(baos);
        
        parseObjectContentAndRemove(nestedReader, nestedWriter, originalObjectData.length, targetPath, pathIndex);
        
        byte[] modifiedData = baos.toByteArray();
        
        // Write metadata and modified object data
        NoteBytesMetaData objectMetaData = new NoteBytesMetaData(NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE, modifiedData.length);
        writer.write(objectMetaData);
        writer.write(modifiedData);
    }

    private void writeModifiedNestedObjectData(NoteBytesWriter writer, byte[] originalObjectData, 
                                            List<NoteBytes> targetPath, int pathIndex) throws IOException {
        // Similar to writeModifiedNestedObject but for use within parseObjectContentAndRemove
        ByteArrayInputStream bais = new ByteArrayInputStream(originalObjectData);
        NoteBytesReader nestedReader = new NoteBytesReader(bais);
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        NoteBytesWriter nestedWriter = new NoteBytesWriter(baos);
        
        parseObjectContentAndRemove(nestedReader, nestedWriter, originalObjectData.length, targetPath, pathIndex);
        
        byte[] modifiedData = baos.toByteArray();
        
        // Write metadata and modified object data
        NoteBytesMetaData objectMetaData = new NoteBytesMetaData(NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE, modifiedData.length);
        writer.write(objectMetaData);
        writer.write(modifiedData);
    }

    private void deleteAllFilesInObject(NoteBytesReader reader, int objectLen) throws IOException {
        int bytesRead = 0;
        
        while (bytesRead < objectLen) {
            // Read key metadata
            NoteBytesMetaData keyMetaData = reader.nextMetaData();
            if (keyMetaData == null) break;
            
            // Skip key data
            reader.skipData(new byte[StreamUtils.BUFFER_SIZE], 0, keyMetaData.getLength());
            bytesRead += 5 + keyMetaData.getLength();
            
            // Read value metadata
            NoteBytesMetaData valueMetaData = reader.nextMetaData();
            if (valueMetaData == null) break;
            
            // Read value data
            byte[] valueData = reader.readByteAmount(valueMetaData.getLength());
            bytesRead += 5 + valueMetaData.getLength();
            
            deleteAllFilesInValueData(valueMetaData, valueData);
        }
    }

    private void deleteAllFilesInValue(NoteBytes value) throws IOException {
        deleteAllFilesInValueData(
            new NoteBytesMetaData(value.getByteDecoding().getType(), value.byteLength()), 
            value.get()
        );
    }

    private void deleteAllFilesInValueData(NoteBytesMetaData valueMetaData, byte[] valueData) throws IOException {
        if (valueMetaData.getType() == NoteBytesMetaData.STRING_TYPE) {
            // This might be a filePath - we need to check the key to be sure
            // For now, we'll assume if we're deleting a subtree, any string could be a filePath
            // In practice, you might want to track the key name to be more precise
            return; // Can't determine if this is a filePath without context
            
        } else if (valueMetaData.getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            // Recursively delete files in nested object
            deleteAllFilesInObjectData(valueData);
            
        } else if (valueMetaData.getType() == NoteBytesMetaData.LONG_TYPE) {
            // This is likely a timestamp - nothing to delete
            return;
        }
    }

    private void deleteAllFilesInObjectData(byte[] objectData) throws IOException {
        int offset = 0;
        
        while (offset < objectData.length) {
            // Parse key
            NoteBytes key = NoteBytes.readNote(objectData, offset);
            offset += 5 + key.byteLength();
            
            // Parse value
            NoteBytes value = NoteBytes.readNote(objectData, offset);
            offset += 5 + value.byteLength();
            
            // Check if this is a filePath
            if (FILE_PATH.equals(key) && value.getByteDecoding().getType() == NoteBytesMetaData.STRING_TYPE) {
                // This is a file path - delete the file
                String filePath = value.getAsString();
                File file = new File(filePath);
                if (file.exists()) {
                    file.delete();
                }
            } else if (value.getByteDecoding().getType() == NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
                // Recursively delete files in nested object
                deleteAllFilesInObjectData(value.get());
            }
        }
    }


    private boolean isObjectDataEmpty(byte[] objectData) {
        return objectData == null || objectData.length == 0;
    }
  */
    //Data files




    public void shutdown(){
        m_settingsData.shutdown();
    }
}