package io.netnotes.engine;

import io.netnotes.engine.noteBytes.collections.NoteBytesMapEphemeral;
import io.netnotes.engine.noteBytes.processing.ByteDecoding;
import io.netnotes.engine.noteBytes.processing.NoteBytesReader;
import io.netnotes.engine.noteBytes.processing.NoteBytesWriter;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.security.auth.DestroyFailedException;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.crypto.FileStreamUtils;
import io.netnotes.engine.crypto.FileStreamUtils.BulkUpdateConfig;
import io.netnotes.engine.crypto.FileStreamUtils.BulkUpdateResult;
import io.netnotes.engine.crypto.FileStreamUtils.ProgressUpdate;
import io.netnotes.engine.crypto.HashData;
import io.netnotes.engine.crypto.HashServices;
import io.netnotes.engine.messaging.StreamUtils;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesPair;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteRandom;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.utils.GitHubAPI;
import io.netnotes.engine.utils.UpdateInformation;
import io.netnotes.engine.utils.Utils;
import io.netnotes.engine.utils.Version;
import io.netnotes.engine.utils.GitHubAPI.GitHubAsset;
import io.netnotes.engine.noteBytes.NoteBytesEphemeral;

import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;


public class AppData {
   // private static File logFile = new File("netnotes-log.txt");
    public static final String SETTINGS_FILE_NAME = "settings.dat";
    public static final File HOME_DIRECTORY = new File(System.getProperty("user.home"));
    public static final File DESKTOP_DIRECTORY = new File(HOME_DIRECTORY + "/Desktop");
  
    public static final int SALT_LENGTH = 16;
    private final Semaphore m_dataSemaphore;

    private File m_appDir = null;

    private NoteBytes m_bcryptKey;
    private boolean m_updates = false;
    private NoteBytes m_salt = null;

    private AppDataInterface m_appInterface = null;
    
    private File m_appFile = null;
    private HashData m_appHashData = null;
    private Version m_javaVersion = null;
    private SecretKey m_secretKey = null;
    
    private static final NoteBytes FILE_PATH = new NoteBytes(new byte[]{0x01});
    private static final NoteBytes CREATED = new NoteBytes(new byte[]{0x02});
    
    private final ExecutorService m_execService;
    private final ScheduledExecutorService m_schedualedExecutor;

    private SecretKey m_oldKey = null;

    private final NoteFileRegistry m_noteFileRegistry;
    private final Map<NoteBytesReadOnly, Node> m_nodeRegistry = new ConcurrentHashMap<>();


    public AppData(AppDataInterface appInteface) throws Exception{
        m_execService = Executors.newVirtualThreadPerTaskExecutor();
        ThreadFactory factory = Thread.ofVirtual().factory();
        m_schedualedExecutor = Executors.newScheduledThreadPool(0, factory);
        m_dataSemaphore = new Semaphore(1);
        m_appInterface = appInteface;
        URL classLocation = Utils.getLocation(AppData.class);
        m_appFile = Utils.urlToFile(classLocation);
        m_appHashData = new HashData(m_appFile);
        m_appDir = m_appFile.getParentFile();

        m_noteFileRegistry = new NoteFileRegistry(this);

        readSettings();
    }


    public AppData(AppDataInterface appInterface, NoteBytes password, ExecutorService execService, ScheduledExecutorService schedualedExecService) throws Exception{
        m_appInterface = appInterface;

        m_execService = execService;
        m_schedualedExecutor = schedualedExecService;
        m_dataSemaphore = new Semaphore(1);
        
        URL classLocation = Utils.getLocation(AppData.class);
        m_appFile = Utils.urlToFile(classLocation);
        m_appHashData = new HashData(m_appFile);
        m_appDir = m_appFile.getParentFile();

        m_noteFileRegistry = new NoteFileRegistry(this);

        setAppKey(HashServices.getBcryptHash(password), new NoteRandom(12));

        setKey(CryptoService.createKey(password, getSalt()));
    }

    public ExecutorService getExecService(){
        return m_execService;
    }

    public ScheduledExecutorService getSchedualedExecService(){
        return m_schedualedExecutor;
    }

    public Map<NoteBytesReadOnly, Node> nodeRegistry(){
        return m_nodeRegistry;
    }

    public NoteBytes getSalt(){
        return m_salt;
    }

    public Semaphore getDataSemaphore(){
        return m_dataSemaphore;
    }

    public AppDataInterface getAppInterface(){
        return m_appInterface;
    }

    public NoteFileRegistry getNoteFileRegistry(){
        return m_noteFileRegistry;
    }

    private File getSettingsFile() throws IOException{
        File dataDir = getDataDir();
        
        return new File(dataDir.getAbsolutePath() + "/" + SETTINGS_FILE_NAME);
        
    }

    private void readSettings()throws NullPointerException, IOException{
        File settingsFile = getSettingsFile();

        if(settingsFile != null && settingsFile.isFile()){
          
            try(
                NoteBytesReader reader = new NoteBytesReader(new FileInputStream(settingsFile));    
            ){
                NoteBytes nextNoteBytes = null;
                
                while((nextNoteBytes = reader.nextNoteBytes()) != null){

                    switch(nextNoteBytes.getAsString()){
                        case "appKey":
                            m_bcryptKey = reader.nextNoteBytes();
                        break;
                        case "updates":
                            m_updates = reader.nextNoteBytes().getAsBoolean();
                        break;
                        case "salt":
                            m_salt = reader.nextNoteBytes();
                        break;
                    }
                }
               
            }

            
        }else{
            throw new FileNotFoundException("Settings file not found.");
        }

    
    }


    public boolean getUpdates(){
        return m_updates;
    }

    public void setUpdates(boolean updates) throws IOException{
        m_updates = updates;
        save();
    }
   

    public File getAppDir(){
        return m_appDir;
    }

    public File getAppFile(){
        return m_appFile;
    }

  
    private void setKey(SecretKey secretKey) throws InvalidKeySpecException, NoSuchAlgorithmException, IOException {
        m_secretKey = secretKey; 
    }


    public Future<?> checkForUpdates(String gitHubUser, String githubProject, SimpleObjectProperty<UpdateInformation> updateInformation){
        GitHubAPI gitHubAPI = new GitHubAPI(gitHubUser, githubProject);
        return gitHubAPI.getAssetsLatestRelease(m_execService, (onFinished)->{
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

                    Utils.getUrlJson(tmpInfo.getReleaseUrl(), m_execService, (onReleaseInfo)->{
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

    
    public NoteBytes getAppKey() {
        return m_bcryptKey;
    }

    public byte[] getAppKeyBytes() {
        return m_bcryptKey.getBytes();
    }

    public void setAppKey(NoteBytes hash, NoteBytes salt) throws IOException {
        m_bcryptKey = hash;
        m_salt = salt;
        save();
    }




    public Version getJavaVersion(){
        return m_javaVersion;
    }

    public HashData appHashData(){
        return m_appHashData;
    }

    public File appFile(){
        return m_appFile;
    }

    public SecretKey getSecretKey() {
        return m_secretKey;
    }

    public void setSecretKey(SecretKey secretKey) {
        m_secretKey = secretKey;
    }

   

    public void save() throws IOException {
        File file = getSettingsFile();
        NoteBytesObject obj = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("appKey", m_bcryptKey),
            new NoteBytesPair("updates", m_updates),
            new NoteBytesPair("salt", m_salt)
        });
        FileStreamUtils.writeFileBytes(file, obj.get());
    }


    
    public Future<?> verifyAppPassword(char[] chars, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        byte[] appKeyBytes = getAppKeyBytes();

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {

                BCrypt.Result result = BCrypt.verifyer(BCrypt.Version.VERSION_2A, LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)).verify(chars, appKeyBytes);
                if(result.verified){
                    return true;
                }else{
                    throw new Exception("Unverified");
                }
            }
        };

        task.setOnSucceeded(onSucceeded);
        task.setOnFailed(onFailed);

        return getExecService().submit(task);
    }

  
    public File getDataDir() throws IOException{
        File dataDir = new File(getAppDir().getAbsolutePath() + "/data");
        if(!dataDir.isDirectory()){
         
            Files.createDirectory(dataDir.toPath());
        
        }
        return dataDir;
    }

    private File createNewDataFile(File dataDir) {     
        NoteUUID noteUUID = NoteUUID.createLocalUUID128();
        String encodedUUID = noteUUID.getAsUrlSafeString();
        File dataFile = new File(dataDir.getAbsolutePath() + "/" + encodedUUID + ".dat");
        return dataFile;
    }
    

    private File getIdDataFile() throws IOException{
        File dataDir = getDataDir();

        File idDataFile = new File(dataDir.getAbsolutePath() + "/data.dat");
        return idDataFile;
    }

    //read and get an idDataFile
    // Main method - simplified return type and better resource management
    protected CompletableFuture<NoteBytesObject> startRead(PipedOutputStream pipedOutput) {
        AtomicBoolean lockAcquired = new AtomicBoolean(false);
        
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    getDataSemaphore().acquire();
                    lockAcquired.set(true);
                    return getIdDataFile();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted while acquiring semaphore", e);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to get ID data file", e);
                }
            }, getExecService())
            .thenCompose(file -> FileStreamUtils.performDecryption(file, pipedOutput, getSecretKey(), getExecService()))
            .whenComplete((result, throwable) -> {
                // Always release semaphore regardless of success or failure
                if (lockAcquired.getAndSet(false)) {
                    getDataSemaphore().release();
                }
            });
    }

    // Refactored main method with proper CompletableFuture chaining
    public CompletableFuture<File> getIdDataFile(NoteStringArrayReadOnly path) {
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
                
                if (idDataFile.isFile()) {
                    // File exists - process it
                    return processExistingDataFile(idDataFile, dataDir, path);
                } else {
                    // File doesn't exist - create new structure
                    return createNewDataStructure(dataDir, path);
                }
            })
            .whenComplete((result, throwable) -> {
                // Always release semaphore
                if (lockAcquired.getAndSet(false)) {
                    getDataSemaphore().release();
                }
                
                if (throwable != null) {
                    Utils.writeLogMsg("AppData.getIdDataFileAsync", throwable);
                }
            });
    }

    private CompletableFuture<File> processExistingDataFile(File idDataFile, File dataDir, NoteStringArrayReadOnly path) {
        return CompletableFuture
            .supplyAsync(() -> {
                try {
                    // Set up piped streams for concurrent processing
                    PipedOutputStream decryptedOutput = new PipedOutputStream();
                    PipedOutputStream parsedOutput = new PipedOutputStream();
                    
                    // Chain the operations: decrypt -> parse -> encrypt -> return file
                    CompletableFuture<NoteBytesObject> decryptFuture = 
                        FileStreamUtils.performDecryption(idDataFile, decryptedOutput, getSecretKey(), getExecService());
                    
                    CompletableFuture<File> parseFuture = 
                        parseIdDataFileUpdate(path, dataDir, decryptedOutput, parsedOutput);
                    
                    CompletableFuture<NoteBytesObject> saveFuture = 
                        FileStreamUtils.saveEncryptedFileSwap(idDataFile, getSecretKey(), parsedOutput);
                    
                    // Wait for all operations to complete and return the result file
                    return CompletableFuture.allOf(decryptFuture, parseFuture, saveFuture)
                        .thenCompose(v -> parseFuture) // Return the parsed file result
                        .join(); // Block for this async operation
                        
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process existing data file", e);
                }
            }, getExecService());
    }

    private CompletableFuture<File> createNewDataStructure(File dataDir, NoteStringArrayReadOnly path) {
        return CompletableFuture
            .supplyAsync(() -> {
                try (NoteBytesMapEphemeral rootMap = new NoteBytesMapEphemeral()) {
                    File newFile = createNewDataFile(dataDir);
                    
                    // Create initial tree structure using ephemeral maps
                    buildPathStructureWithEphemeral(rootMap, path.getAsList(), 0, newFile);
                    
                    // TODO: Save the initial tree structure here
                    // saveInitialTree(rootMap.get()); // Serialize when ready to save
                    
                    return newFile;
                    
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create new data structure", e);
                }
            }, getExecService());
    }

    // Helper method to build path structure using ephemeral maps
    private void buildPathStructureWithEphemeral(NoteBytesMapEphemeral currentMap, List<NoteBytes> targetPath, 
                                            int pathIndex, File resultFile) throws Exception {
        if (pathIndex >= targetPath.size()) {
            return;
        }
        
        NoteBytes currentKey = targetPath.get(pathIndex);
        
        if (pathIndex == targetPath.size() - 1) {
            // Final element - create file object
            try (NoteBytesMapEphemeral fileMap = new NoteBytesMapEphemeral()) {
                fileMap.put(FILE_PATH, new NoteBytesEphemeral(new NoteBytes(resultFile.getCanonicalPath())));
                fileMap.put(CREATED, new NoteBytesEphemeral(new NoteBytes(System.currentTimeMillis())));
                currentMap.put(currentKey, fileMap.getNoteBytesEphemeral()); // Serialize the file map
            }
        } else {
            // Intermediate element - create nested structure
            try (NoteBytesMapEphemeral nestedMap = new NoteBytesMapEphemeral()) {
                buildPathStructureWithEphemeral(nestedMap, targetPath, pathIndex + 1, resultFile);
                currentMap.put(currentKey, nestedMap.getNoteBytesEphemeral()); // Serialize the nested map
            }
        }
    }

    // Refactored parsing method using ephemeral maps
    private CompletableFuture<File> parseIdDataFileUpdate(NoteStringArrayReadOnly path, File dataDir, 
                                                        PipedOutputStream decryptedInputStream, 
                                                        PipedOutputStream parsedOutputStream) {
        
        return CompletableFuture
            .supplyAsync(() -> {
                // Validate inputs
                if (path == null || path.byteLength() == 0 || dataDir == null || 
                    decryptedInputStream == null || parsedOutputStream == null) {
                    throw new IllegalArgumentException("Required parameters cannot be null");
                }
                
                List<NoteBytes> targetPath = path.getAsList();
                
                try (NoteBytesReader reader = new NoteBytesReader(new PipedInputStream(decryptedInputStream, StreamUtils.PIPE_BUFFER_SIZE));
                    NoteBytesWriter writer = new NoteBytesWriter(parsedOutputStream)) {
                    
                    // Read root metadata
                    NoteBytesMetaData rootMetaData = reader.nextMetaData();
                    if (rootMetaData == null) {
                        // Empty file case - create new structure
                        File newFile = createNewDataFile(dataDir);
                        try (NoteBytesMapEphemeral rootMap = new NoteBytesMapEphemeral()) {
                            buildPathStructureWithEphemeral(rootMap, targetPath, 0, newFile);
                            try(NoteBytesEphemeral ephemeralBytes = rootMap.getNoteBytesEphemeral()){
                                writer.write(ephemeralBytes); // Serialize and write
                            }
                        }
                        return newFile;
                    }
                    
                    // Parse existing tree and look for target path
                    File foundFile = parseAndWriteTreeWithEphemeral(reader, writer, rootMetaData, targetPath, dataDir);
                    return foundFile;
                    
                } catch (Exception e) {
                    throw new RuntimeException("Failed to parse ID data file", e);
                }
            }, getExecService());
    }

    private File parseAndWriteTreeWithEphemeral(NoteBytesReader reader, NoteBytesWriter writer, 
                                            NoteBytesMetaData rootMetaData, List<NoteBytes> targetPath, 
                                            File dataDir) throws IOException {
        
        if (rootMetaData.getType() != NoteBytesMetaData.NOTE_BYTES_OBJECT_TYPE) {
            throw new IOException("Root must be an object");
        }
        
        // Read the entire root object into an ephemeral map for easier processing
        byte[] rootData = reader.readByteAmount(rootMetaData.getLength());

        try(NoteBytesEphemeral ephemeralRoot = new NoteBytesEphemeral(rootData)){
            try (NoteBytesMapEphemeral rootMap = new NoteBytesMapEphemeral(ephemeralRoot)) {
                // Process the map to find/create the target path
                FileResult result = new FileResult();
                processMapForPath(rootMap, targetPath, 0, dataDir, result);
                
                // Write the modified root metadata and data
                writer.write(rootMetaData);
                try(NoteBytesEphemeral modifiedMap = rootMap.getNoteBytesEphemeral()){
                    writer.write(modifiedMap); // Serialize the modified map
                }
                
                return result.getFile();
            }
        }catch(Exception e){
            throw new IOException(e);
        }
    }

    // Helper class to hold file result from parsing
    private static class FileResult {
        private File file;
        
        public void setFile(File file) {
            this.file = file;
        }
        
        public File getFile() {
            return file;
        }
        
    }

    private void processMapForPath(NoteBytesMapEphemeral currentMap, List<NoteBytes> targetPath, 
                                int pathIndex, File dataDir, FileResult result) throws Exception {
        
        if (pathIndex >= targetPath.size()) {
            return;
        }
        
        NoteBytes currentKey = targetPath.get(pathIndex);
        NoteBytesEphemeral existingValue = currentMap.get(currentKey);
        
        if (existingValue != null) {
            // Key exists - check if we're at the final path element
            if (pathIndex == targetPath.size() - 1) {
                // Final element - should contain file info
                File existingFile = extractFileFromEphemeralData(existingValue);
                result.setFile(existingFile);
            } else {
                // Intermediate element - recurse into nested map
                try (NoteBytesMapEphemeral nestedMap = new NoteBytesMapEphemeral(existingValue)) {
                    processMapForPath(nestedMap, targetPath, pathIndex + 1, dataDir, result);
                    // Update the current map with any changes made to the nested map
                    currentMap.put(currentKey, nestedMap.getNoteBytesEphemeral());
                }
            }
        } else {
            // Key doesn't exist - create the remaining path
            File newFile = createNewDataFile(dataDir);
            result.setFile(newFile);
            
            if (pathIndex == targetPath.size() - 1) {
                // Final element - create file object directly
                try (NoteBytesMapEphemeral fileMap = new NoteBytesMapEphemeral()) {
                    fileMap.put(FILE_PATH, newFile.getCanonicalPath());
                    fileMap.put(CREATED, System.currentTimeMillis());
                    currentMap.put(currentKey, fileMap.getNoteBytesEphemeral());
                }
            } else {
                // Create nested structure for remaining path
                try (NoteBytesMapEphemeral nestedMap = new NoteBytesMapEphemeral()) {
                    buildPathStructureWithEphemeral(nestedMap, targetPath, pathIndex + 1, newFile);
                    currentMap.put(currentKey, nestedMap.getNoteBytesEphemeral());
                }
            }
        }
    }

    private File extractFileFromEphemeralData(NoteBytesEphemeral objectData) throws Exception {
        try (NoteBytesMapEphemeral fileMap = new NoteBytesMapEphemeral(objectData)) {
            NoteBytes filePath = fileMap.get(FILE_PATH);
            
            if (filePath == null) {
                throw new IOException("No valid filePath found in object");
            }
            
            return new File(filePath.getAsString());
        }
    }
   
 


    public CompletableFuture<BulkUpdateResult> updateAllFileEncryptionWithProgress(
            NoteBytes newPassword, BulkUpdateConfig config,
            Consumer<ProgressUpdate> progressCallback) {
        
        if (newPassword.byteLength() > 0) {
            AtomicBoolean lockAcquired = new AtomicBoolean(false);
            
            try {
                SecretKey oldAppKey = getSecretKey();
                NoteBytes hash = HashServices.getBcryptHash(newPassword);
                NoteBytes salt = new NoteRandom(SALT_LENGTH);

                m_oldKey = oldAppKey;

                SecretKey newAppKey = CryptoService.createKey(newPassword, salt);
                setAppKey(hash, salt);
                setKey(newAppKey);

                return CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            getDataSemaphore().acquire();
                            lockAcquired.set(true);
                            return getIdDataFile();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Thread interrupted while acquiring semaphore", e);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to get ID data file", e);
                        }
                    }, getExecService())
                    .thenCompose(idDataFile -> {
                        if (!idDataFile.isFile()) {
                            progressCallback.accept(new ProgressUpdate(0, 0, "No data file found"));
                            return CompletableFuture.completedFuture(new BulkUpdateResult(0, 0, Collections.emptyList()));
                        }

                        return FileStreamUtils.processDataFileForBulkUpdateWithProgress(idDataFile, oldAppKey, newAppKey, config, progressCallback, FILE_PATH, getExecService());
                    })
                    .whenComplete((result, throwable) -> {
                        if (lockAcquired.getAndSet(false)) {
                            getDataSemaphore().release();
                        }

                        if (throwable != null) {
                            Utils.writeLogMsg("AppData.updateAllFileEncryptionWithProgress", throwable);
                            progressCallback.accept(new ProgressUpdate(-1, -1, "Error: " + throwable.getMessage()));
                        } else if (result != null) {
                            progressCallback.accept(new ProgressUpdate(result.getTotalFiles(), result.getTotalFiles(),
                                "Completed: " + result.toString()));
                        }
                    });
            } catch (Exception e) {
                CompletableFuture<BulkUpdateResult> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(new RuntimeException("Could not start update", e));
                return failedFuture;
            }
        } else {
            CompletableFuture<BulkUpdateResult> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Password invalid"));
            return failedFuture;
        }
    }
    
    public Future<?> removeIdDataFile(NoteStringArrayReadOnly path, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        AtomicBoolean isAquired = new AtomicBoolean();
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InterruptedException, IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, ShortBufferException  {
                getDataSemaphore().acquire();
                isAquired.set(true);
                File idDataFile = getIdDataFile();
                File dataDir = idDataFile.getParentFile();

                if (idDataFile.isFile()) {
                    // Set up piped streams for concurrent processing
                    PipedOutputStream decryptedOutput = new PipedOutputStream();
                    FileStreamUtils.readEncryptedDataStream(idDataFile, getSecretKey(), getExecService(), decryptedOutput);
                    
                    PipedOutputStream parsedOutput = new PipedOutputStream();
                    parseIdDataFileRemoveFile(path, dataDir, decryptedOutput, parsedOutput, onSucceeded);
                   
                    File tmpFile = new File(dataDir.getAbsolutePath() + "/" +  NoteUUID.createSafeUUID128() + ".tmp");
                    FileStreamUtils.writeEncryptedDataStream( idDataFile,tmpFile,getSecretKey(), parsedOutput, getExecService(), onSucceeded->{
                        if(isAquired.getAndSet(false)){
                            getDataSemaphore().release();
                        }
                    }, onError->{
                        if(isAquired.getAndSet(false)){
                            getDataSemaphore().release();
                        }
                    });
                }

                return true;
            }
        };
       
        task.setOnFailed(onFailed);
        return getExecService().submit(task);
    }

    private Future<?> parseIdDataFileRemoveFile(NoteStringArrayReadOnly path, File dataDir, PipedOutputStream decryptedOutputStream, 
                                           PipedOutputStream parsedOutputStream, EventHandler<WorkerStateEvent> onSucceeded) {
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

    //Data files


   public CompletableFuture<NoteBytesObject> performDecryption(
        File file, 
        PipedOutputStream pipedOutput
    ){
        return FileStreamUtils.performDecryption(file, pipedOutput, getSecretKey(), getExecService());
    }
  
    public CompletableFuture<NoteBytesObject> saveEncryptedFileSwap(
        File file,
        PipedOutputStream pipedOutputStream
    ) {
        if(file.exists() && file.isFile()){
            return FileStreamUtils.saveEncryptedFileSwap(file, getSecretKey(), pipedOutputStream);
        }else{
            return FileStreamUtils.saveEncryptedFile(file, getSecretKey(), pipedOutputStream);
        }
    }



    public void shutdown(){

        try {
            getSecretKey().destroy();
        } catch (DestroyFailedException e) {
            Utils.writeLogMsg("NetworsData.onClosing", "Cannot destroy");
        }
    }
}