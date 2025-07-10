package io.netnotes.engine;


import java.io.File;
import java.io.FileNotFoundException;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;

import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netnotes.engine.GitHubAPI.GitHubAsset;
import com.google.gson.JsonParseException;

import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

import org.ergoplatform.sdk.SecretString;

public class AppData {
   // private static File logFile = new File("netnotes-log.txt");
    public static final String SETTINGS_FILE_NAME = "settings.conf";
    public static final File HOME_DIRECTORY = new File(System.getProperty("user.home"));
    public static final File DESKTOP_DIRECTORY = new File(HOME_DIRECTORY + "/Desktop");
  

    private File m_appDir = null;
    private File m_settingsFile = null;

    private String m_appKey;
    
    private File m_appFile = null;
    private HashData m_appHashData = null;
    private Version m_javaVersion = null;
    private SecretKey m_secretKey = null;
    private boolean m_updates = false;
    private final ExecutorService m_execService;
    private final ScheduledExecutorService m_schedualedExecutor;
    
   // private Stage m_persistenceStage = null;


    public AppData(ExecutorService execService, ScheduledExecutorService schedualedExecService) throws JsonParseException, IOException{
        m_execService = execService;
        m_schedualedExecutor = schedualedExecService;
        URL classLocation = Utils.getLocation(AppData.class);
        m_appFile = Utils.urlToFile(classLocation);
        m_appHashData = new HashData(m_appFile);
        m_appDir = m_appFile.getParentFile();
        m_settingsFile = new File(m_appDir.getAbsolutePath() + "/" + SETTINGS_FILE_NAME);

        readFile();

    }




    public AppData(SecretString password, ExecutorService execService, ScheduledExecutorService schedualedExecService)throws NoSuchAlgorithmException, InvalidKeySpecException, IOException{
        m_execService = execService;
        m_schedualedExecutor = schedualedExecService;
        URL classLocation = Utils.getLocation(AppData.class);
        m_appFile = Utils.urlToFile(classLocation);
        m_appHashData = new HashData(m_appFile);
        m_appDir = m_appFile.getParentFile();
        m_settingsFile = new File(m_appDir.getAbsolutePath() + "/" + SETTINGS_FILE_NAME);


        m_appKey = Utils.getBcryptHashString(password.getData());
   
        
        
        
        save();
        createKey(password);
   
    }

    public ExecutorService getExecService(){
        return m_execService;
    }

    public ScheduledExecutorService getSchedualedExecService(){
        return m_schedualedExecutor;
    }

    private void readFile()throws NullPointerException, JsonParseException, IOException{

        if(m_settingsFile != null && m_settingsFile.isFile()){
        
            openJson(new JsonParser().parse(Files.readString(m_settingsFile.toPath())).getAsJsonObject());
        }else{
            throw new FileNotFoundException("Settings file not found.");
        }

    
    }


    private void openJson(JsonObject dataObject) throws NullPointerException, JsonParseException{
        
        JsonElement appkeyElement = dataObject.get("appKey");
        JsonElement updatesElement = dataObject.get("updates");
        if (appkeyElement != null && appkeyElement.isJsonPrimitive()) {

            m_appKey = appkeyElement.getAsString();
            m_updates = updatesElement != null && updatesElement.isJsonPrimitive() ? updatesElement.getAsBoolean() : false;
       } else {
            throw new JsonParseException("Null appKey");
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

  
     public void createKey(SecretString password) throws InvalidKeySpecException, NoSuchAlgorithmException {

        m_secretKey = new SecretKeySpec(Utils.createKeyBytes(password), "AES");

    }

    public Future<?> createKey(SecretString password, EventHandler<WorkerStateEvent> onComplete, EventHandler<WorkerStateEvent> onFailed)  {
        
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws NoSuchAlgorithmException, InvalidKeySpecException {
                return new SecretKeySpec(Utils.createKeyBytes(password), "AES");
            }
        };

        task.setOnSucceeded(e->{
            Object obj = e.getSource().getValue();
            if(obj != null && obj instanceof SecretKey){
                m_secretKey = (SecretKey) obj;
                Utils.returnObject(true, m_execService, onComplete);
            }else{
                Utils.returnException("Unknown error", m_execService, onFailed);
            }
          
        });

        task.setOnFailed(onFailed);

        return m_execService.submit(task);
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

    
    public String getAppKey() {
        return m_appKey;
    }

    public byte[] getAppKeyBytes() {
        return m_appKey.getBytes();
    }

    public void setAppKey(String keyHash) throws IOException {
        m_appKey = keyHash;
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

    public JsonObject getJson() {
        JsonObject dataObject = new JsonObject();
        dataObject.addProperty("appKey", m_appKey);
        dataObject.addProperty("updates", m_updates);
        return dataObject;
    }

    public void save() throws IOException {
        String jsonString = getJson().toString();
        Files.writeString(m_settingsFile.toPath(), jsonString);
    }

    
}
