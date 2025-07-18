package io.netnotes.engine.core;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import java.security.spec.InvalidKeySpecException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;

import org.apache.commons.io.FileUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import io.netnotes.engine.AppConstants;
import io.netnotes.engine.AppInterface;
import io.netnotes.engine.GitHubAPI;
import io.netnotes.engine.HashData;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NoteBytes;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteListString;
import io.netnotes.engine.NoteUUID;
import io.netnotes.engine.UpdateInformation;
import io.netnotes.engine.Utils;
import io.netnotes.engine.Version;
import io.netnotes.engine.GitHubAPI.GitHubAsset;
import io.netnotes.engine.Stages;

import com.google.gson.JsonParseException;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;


public class AppData {
   // private static File logFile = new File("netnotes-log.txt");
    public static final String SETTINGS_FILE_NAME = "settings.conf";
    public static final File HOME_DIRECTORY = new File(System.getProperty("user.home"));
    public static final File DESKTOP_DIRECTORY = new File(HOME_DIRECTORY + "/Desktop");
  
    private final Semaphore m_dataSemaphore;

    private File m_appDir = null;
    private File m_settingsFile = null;

    private String m_appKey;
    private AppInterface m_appInterface = null;
    
    private File m_appFile = null;
    private HashData m_appHashData = null;
    private Version m_javaVersion = null;
    private SecretKey m_secretKey = null;
    private boolean m_updates = false;
    private final ExecutorService m_execService;
    private final ScheduledExecutorService m_schedualedExecutor;
    
   // private Stage m_persistenceStage = null;


    public AppData(AppInterface appInteface, ExecutorService execService, ScheduledExecutorService schedualedExecService) throws JsonParseException, IOException{
        m_execService = execService;
        m_schedualedExecutor = schedualedExecService;
        m_dataSemaphore = new Semaphore(1);
        m_appInterface = appInteface;
        URL classLocation = Utils.getLocation(AppData.class);
        m_appFile = Utils.urlToFile(classLocation);
        m_appHashData = new HashData(m_appFile);
        m_appDir = m_appFile.getParentFile();
        m_settingsFile = new File(m_appDir.getAbsolutePath() + "/" + SETTINGS_FILE_NAME);

        readFile();

    }


    public Semaphore getDataSemaphore(){
        return m_dataSemaphore;
    }

    public AppInterface getAppInterface(){
        return m_appInterface;
    }

    public AppData(AppInterface appInterface, NoteBytes password,  ExecutorService execService, ScheduledExecutorService schedualedExecService)throws NoSuchAlgorithmException, InvalidKeySpecException, IOException{
        m_execService = execService;
        m_schedualedExecutor = schedualedExecService;
        m_dataSemaphore = new Semaphore(1);
        m_appInterface = appInterface;
        URL classLocation = Utils.getLocation(AppData.class);
        m_appFile = Utils.urlToFile(classLocation);
        m_appHashData = new HashData(m_appFile);
        m_appDir = m_appFile.getParentFile();
        m_settingsFile = new File(m_appDir.getAbsolutePath() + "/" + SETTINGS_FILE_NAME);


        m_appKey = Utils.getBcryptHashString(password.getChars());
   
        
        
        
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

  
     public void createKey(NoteBytes password) throws InvalidKeySpecException, NoSuchAlgorithmException {

        m_secretKey = new SecretKeySpec(Utils.createKeyBytes(password), "AES");

    }

    public Future<?> createKey(NoteBytes password, EventHandler<WorkerStateEvent> onComplete, EventHandler<WorkerStateEvent> onFailed)  {
        
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


    


    public void verifyAppKey( String networkName, JsonObject note, NetworkInformation networkInformation, long timeStamp, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        double lblCol = 170;
        double rowHeight = 22;



        if(note.get("timeStamp") == null && note.get("timestamp") == null){
            note.addProperty("timeStamp", timeStamp);
        }

        String title = "Netnotes - " +networkName + " - Authorize: " + networkInformation.getNetworkName();

        Stage passwordStage = new Stage();
        passwordStage.getIcons().add(Stages.logo);
        passwordStage.setResizable(false);
        passwordStage.initStyle(StageStyle.UNDECORATED);
        passwordStage.setTitle(title);

        Button closeBtn = new Button();


        PasswordField passwordField = new PasswordField();
        passwordField.setFont(Stages.txtFont);
        passwordField.setId("passField");
        HBox.setHgrow(passwordField, Priority.ALWAYS);


        Scene passwordScene = Stages.getAuthorizationScene(passwordStage, title, closeBtn, passwordField, note, networkInformation.getNetworkName(), rowHeight, lblCol);
        passwordScene.setFill(null);
        passwordScene.getStylesheets().add(Stages.DEFAULT_CSS);

        passwordStage.setScene(passwordScene);
        passwordField.setOnAction(e -> {
            Stage statusStage = Stages.getStatusStage("Verifying", "Verifying...");

            if ( passwordField.getText().length() < 6) {
                passwordField.setText("");
            } else {
                statusStage.show();
                char[] pass = passwordField.getText().toCharArray();
                passwordField.setText("");
                verifyAppPassword(pass, onVerified->{
                    statusStage.close();
                    passwordStage.close();
                    Utils.returnObject(NoteConstants.VERIFIED, getExecService(), onSucceeded);
                }, onUnverified->{

                    statusStage.close();
                    Utils.returnException(NoteConstants.ERROR_CANCELED, getExecService(), onFailed);
                });
            }
        
        });

        closeBtn.setOnAction(e -> {
            passwordStage.close();
        });

        passwordScene.focusOwnerProperty().addListener((obs, oldval, newVal) -> {
            if (newVal != null && !(newVal instanceof PasswordField)) {
                Platform.runLater(() -> passwordField.requestFocus());
            }
        });
        passwordStage.show();
 
        Platform.runLater(() ->{
       
        
            passwordField.requestFocus();}
        );
    }
    



   
     public Stage verifyAppKey(Runnable runnable, Runnable closing) {

        String title = "Netnotes - Enter Password";

        Stage passwordStage = new Stage();
        passwordStage.getIcons().add(Stages.logo);
        passwordStage.setResizable(false);
        passwordStage.initStyle(StageStyle.UNDECORATED);
        passwordStage.setTitle(title);

        Button closeBtn = new Button();

        HBox titleBox = Stages.createTopBar(Stages.icon, title, closeBtn, passwordStage);

        Button imageButton = Stages.createImageButton(Stages.logo, "Netnotes");

        HBox imageBox = new HBox(imageButton);
        imageBox.setAlignment(Pos.CENTER);

        Text passwordTxt = new Text("Enter password:");
        passwordTxt.setFill(Stages.txtColor);
        passwordTxt.setFont(Stages.txtFont);

        PasswordField passwordField = new PasswordField();
        passwordField.setFont(Stages.txtFont);
        passwordField.setId("passField");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        HBox passwordBox = new HBox(passwordTxt, passwordField);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        passwordBox.setPadding(new Insets(20, 0, 0, 0));

        Button clickRegion = new Button();
        clickRegion.setPrefWidth(Double.MAX_VALUE);
        clickRegion.setId("transparentColor");
        clickRegion.setPrefHeight(500);

        clickRegion.setOnAction(e -> {
            passwordField.requestFocus();

        });

        VBox.setMargin(passwordBox, new Insets(5, 10, 0, 20));

        VBox layoutVBox = new VBox(titleBox, imageBox, passwordBox, clickRegion);
        VBox.setVgrow(layoutVBox, Priority.ALWAYS);

        Scene passwordScene = new Scene(layoutVBox, Stages.STAGE_WIDTH, Stages.STAGE_HEIGHT);
        passwordScene.setFill(null);
        passwordScene.getStylesheets().add("/css/startWindow.css");
        passwordStage.setScene(passwordScene);

        Stage statusStage = Stages.getStatusStage("Verifying - Netnotes", "Verifying...");

        passwordField.setOnKeyPressed(e -> {

            KeyCode keyCode = e.getCode();

            if (keyCode == KeyCode.ENTER) {

                if (passwordField.getText().length() < 6) {
                    passwordField.setText("");
                } else {
          
                    statusStage.show();
                    char[] chars = passwordField.getText().toCharArray();
                    passwordField.setText("");

                    verifyAppPassword(chars, onVerified->{
                        statusStage.close();
                        passwordStage.close();
                        runnable.run();
                    }, onFailed->{
                        statusStage.close();
                    });
                }
            }
        });

        closeBtn.setOnAction(e -> {
            passwordStage.close();
            closing.run();
        });

        passwordScene.focusOwnerProperty().addListener((obs, oldval, newVal) -> {
            if (newVal != null && !(newVal instanceof PasswordField)) {
                Platform.runLater(() -> passwordField.requestFocus());
            }
        });
        
        passwordStage.show();
            
        Platform.runLater(() ->{

            passwordStage.toBack();
            passwordStage.toFront();
            
        }
        );

        passwordStage.setOnCloseRequest(e->closing.run());

        return passwordStage;
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

    
    public void updateDataEncryption(SecretKey oldval, SecretKey newval){
        
        File idDataFile = getIdDataFile();
        if(idDataFile != null && idDataFile.isFile()){
            try {
                JsonObject dataFileJson = Utils.readJsonFile(oldval, idDataFile);
                if(dataFileJson != null){
                    Utils.saveJson(newval, dataFileJson, idDataFile);

                    JsonElement idsArrayElement = dataFileJson.get("ids");
                    if(idsArrayElement != null && idsArrayElement.isJsonArray()){
                        JsonArray idsArray = idsArrayElement.getAsJsonArray();

                        for(int i = 0; i < idsArray.size() ; i++){
                            JsonElement idFileObjectElement = idsArray.get(i);

                            if(idFileObjectElement != null && idFileObjectElement.isJsonObject()){
                                JsonObject idFileObject = idFileObjectElement.getAsJsonObject();
                                JsonElement dataElement = idFileObject.get("data");

                                if(dataElement != null && dataElement.isJsonArray()){
                                    JsonArray dataArray = dataElement.getAsJsonArray();

                                    for(int j = 0; j< dataArray.size(); j++){
                                        JsonElement dataFileObjectElement = dataArray.get(j);

                                        if(dataFileObjectElement != null && dataFileObjectElement.isJsonObject()){
                                            JsonObject dataFileObject = dataFileObjectElement.getAsJsonObject();

                                            JsonElement fileElement = dataFileObject.get("file");
                                            if(fileElement != null && fileElement.isJsonPrimitive()){
                                                File file = new File(fileElement.getAsString());
                                                if(file.isFile()){
                                                   
                                                    File tmpFile = new File(file.getParentFile().getAbsolutePath() + "/" + file.getName() + ".tmp");
                                                    Utils.updateFileEncryption(oldval, newval, file, tmpFile);
                                                    if(tmpFile.isFile()){
                                                        try{
                                                            Files.delete(tmpFile.toPath());
                                                        }catch(IOException deleteException){

                                                        }
                                                    }
                                                    
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException
            | InvalidAlgorithmParameterException | BadPaddingException | IllegalBlockSizeException | IOException e) {
                try {
                Files.writeString(AppConstants.LOG_FILE.toPath(),"Error updating wallets idDataFile key: " + e.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e1) {

                }

            }

        }
    }

    public Future<?> removeData(  String scope, String type, EventHandler<WorkerStateEvent> onFinished){

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InterruptedException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException{
                m_dataSemaphore.acquire();
                String id2 = type + ":" + scope;
                removeData(id2);
                m_dataSemaphore.release();
                return true;
            }
        };

        task.setOnFailed((onFailed)->{
            m_dataSemaphore.release();
            Utils.returnObject(false, getExecService(), onFinished, null);
        });

        task.setOnSucceeded(onFinished);

        return getExecService().submit(task);
    }


    
    public boolean removeData(String id2) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException{
        SimpleBooleanProperty isRemoved = new SimpleBooleanProperty(false);
    
        File idDataFile =  getIdDataFile();
        if(idDataFile.isFile()){
            
            JsonObject json = Utils.readJsonFile(getSecretKey(), idDataFile);
            JsonElement idsElement = json.get("ids");
    
            if(idsElement != null && idsElement.isJsonArray()){
                JsonArray idsArray = idsElement.getAsJsonArray();
                SimpleIntegerProperty indexProperty = new SimpleIntegerProperty(-1);
                for(int i = 0; i < idsArray.size(); i ++){
                    JsonElement dataFileElement = idsArray.get(i);
                    if(dataFileElement != null && dataFileElement.isJsonObject()){
                        JsonObject fileObject = dataFileElement.getAsJsonObject();
                        JsonElement dataIdElement = fileObject.get("id");

                        if(dataIdElement != null && dataIdElement.isJsonPrimitive()){
                            String fileId2String = dataIdElement.getAsString();
                            if(fileId2String.equals(id2)){
                                indexProperty.set(i);
                                JsonElement dataArrayElement = fileObject.get("data");
                                if(dataArrayElement != null && dataArrayElement.isJsonArray()){
                                    JsonArray dataArray = dataArrayElement.getAsJsonArray();
                                    for(int j = 0; j< dataArray.size();j++){
                                        JsonElement fileDataObjectElement = dataArray.get(j);
                                        if(fileDataObjectElement != null && fileDataObjectElement.isJsonObject()){
                                            JsonObject fileDataObject = fileDataObjectElement.getAsJsonObject();
                                            JsonElement fileElement = fileDataObject.get("file");
                                            if(fileElement != null && fileElement.isJsonPrimitive()){
                                                File file = new File(fileElement.getAsString());
                                                if(file.isFile()){
                                                    Files.delete(file.toPath());
                                                    isRemoved.set(true);
                                                }
                                            }
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
                int index = indexProperty.get();
                if(index > -1){
                    idsArray.remove(index);
                    json.remove("ids");
                    json.add("ids",idsArray);
                    Utils.saveJson(getSecretKey(), json, idDataFile);
                }
            }
        }
        return isRemoved.get();
    }

    public File getDataDir(){
        File dataDir = new File(getAppDir().getAbsolutePath() + "/data");
        if(!dataDir.isDirectory()){
            try{
                Files.createDirectory(dataDir.toPath());
            }catch(IOException e){
                try {
                    Files.writeString(AppConstants.LOG_FILE.toPath(),"\ncannot create data directory: " + e.toString()  , StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                } catch (IOException e1) {

                }
                
            }
        }
        return dataDir;
    }

    private File createNewDataFile(File dataDir, JsonObject dataFileJson) {     
        NoteUUID noteUUID = NoteUUID.createLocalUUID128();
        String encodedUUID = noteUUID.getAsUrlSafeString();
        File dataFile = new File(dataDir.getAbsolutePath() + "/" + encodedUUID + ".dat");
        return dataFile;
    }
    

    private File getIdDataFile(){
        File dataDir = getDataDir();

        File idDataFile = new File(dataDir.getAbsolutePath() + "/data.dat");
        return idDataFile;
    }

    
    public boolean doesFileIdExist(String fileId, JsonObject dataFileJson) {
        if(dataFileJson != null){
            
            fileId = "/" + fileId + ".dat";
            JsonElement idsArrayElement = dataFileJson.get("ids");
            if(idsArrayElement != null && idsArrayElement.isJsonArray()){
                JsonArray idsArray = idsArrayElement.getAsJsonArray();

                for(int i = 0; i < idsArray.size() ; i++){
                    JsonElement idFileObjectElement = idsArray.get(i);

                    if(idFileObjectElement != null && idFileObjectElement.isJsonObject()){
                        JsonObject idFileObject = idFileObjectElement.getAsJsonObject();
                        JsonElement dataElement = idFileObject.get("data");

                        if(dataElement != null && dataElement.isJsonArray()){
                            JsonArray dataArray = dataElement.getAsJsonArray();

                            for(int j = 0; j< dataArray.size(); j++){
                                JsonElement dataFileObjectElement = dataArray.get(j);

                                if(dataFileObjectElement != null && dataFileObjectElement.isJsonObject()){
                                    JsonObject dataFileObject = dataFileObjectElement.getAsJsonObject();

                                    JsonElement fileElement = dataFileObject.get("file");
                                    if(fileElement != null && fileElement.isJsonPrimitive()){
                                        if(fileElement.getAsString().endsWith(fileId)){
                                            return true;
                                        }
                                        
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
    
    public File getIdDataFile(NoteListString path) throws IOException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException{
       
        List<NoteBytes> pathList = path.getAsList();
        int pathListSize = pathList.size();
        NoteBytes version = pathListSize > 0 ? pathList.get(0) : new NoteBytes(".");
        NoteBytes id1 =  pathListSize > 1 ? pathList.get(1) : new NoteBytes(".");
        NoteBytes scope = pathListSize > 2 ? pathList.get(2) : new NoteBytes(".");
        NoteBytes type = pathListSize > 3 ? pathList.get(3) : new NoteBytes(".");
        
        String id = id1 +":" + version;
        String id2 = type + ":" + scope;

        File idDataFile = getIdDataFile();
    
        File dataDir = idDataFile.getParentFile();
           
        if(idDataFile.isFile()){
            
            JsonObject json = Utils.readJsonFile(getSecretKey(), idDataFile);
            JsonElement idsElement = json.get("ids");
            json.remove("ids");
            if(idsElement != null && idsElement.isJsonArray()){
                JsonArray idsArray = idsElement.getAsJsonArray();
        
                for(int i = 0; i < idsArray.size(); i ++){
                    JsonElement dataFileElement = idsArray.get(i);
                    if(dataFileElement != null && dataFileElement.isJsonObject()){
                        JsonObject fileObject = dataFileElement.getAsJsonObject();
                        JsonElement dataIdElement = fileObject.get("id");

                        if(dataIdElement != null && dataIdElement.isJsonPrimitive()){
                            String fileId2String = dataIdElement.getAsString();
                            if(fileId2String.equals(id2)){
                                JsonElement dataElement = fileObject.get("data");

                                if(dataElement != null && dataElement.isJsonArray()){
                                    JsonArray dataIdArray = dataElement.getAsJsonArray();
                                    fileObject.remove("data");
                                    for(int j =0; j< dataIdArray.size() ; j++){
                                        JsonElement dataIdArrayElement = dataIdArray.get(j);
                                        if(dataIdArrayElement != null && dataIdArrayElement.isJsonObject()){
                                            JsonObject fileIdObject = dataIdArrayElement.getAsJsonObject();
                                            JsonElement idElement = fileIdObject.get("id");
                                            if(idElement != null && idElement.isJsonPrimitive()){
                                                String fileIdString = idElement.getAsString();
                                                if(fileIdString.equals(id)){
                                                    
                                                    JsonElement fileElement = fileIdObject.get("file");

                                                    if(fileElement != null && fileElement.isJsonPrimitive()){
                                                        File file = new File(fileElement.getAsString());

                                                        return file;
                                                
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    File newFile = createNewDataFile(dataDir, json);
                                    JsonObject fileJson = new JsonObject();
                                    fileJson.addProperty("id", id);
                                    fileJson.addProperty("file", newFile.getCanonicalPath());

                                    dataIdArray.add( fileJson);
                                    
                                    fileObject.add("data", dataIdArray);

                                    idsArray.set(i, fileObject);

                                    json.add("ids", idsArray);

                                    
                                    Utils.saveJson(getSecretKey(), json, idDataFile);
                                    
                                
                                    return newFile; 

                                }

                            }
                        }
                    }
                }

                File newFile = createNewDataFile(dataDir, json);

                JsonObject fileJson = new JsonObject();
                fileJson.addProperty("id", id);
                fileJson.addProperty("file", newFile.getCanonicalPath());

                JsonArray dataIdArray = new JsonArray();
                dataIdArray.add(fileJson);

                JsonObject fileObject = new JsonObject();
                fileObject.addProperty("id", id2);
                fileObject.add("data", dataIdArray);

                idsArray.add(fileObject);
                
                json.add("ids", idsArray);
                
                Utils.saveJson(getSecretKey(), json, idDataFile);
                    
                return newFile;
            }
        }
      
        
   
        File newFile = createNewDataFile(dataDir, null);

        JsonObject fileJson = new JsonObject();
        fileJson.addProperty("id", id);
        fileJson.addProperty("file", newFile.getCanonicalPath());

        JsonArray dataIdArray = new JsonArray();
        dataIdArray.add(fileJson);

        JsonObject fileObject = new JsonObject();
        fileObject.addProperty("id", id2);
        fileObject.add("data", dataIdArray);
        
        JsonArray idsArray = new JsonArray();
        idsArray.add(fileObject);

        JsonObject json = new JsonObject();
        json.add("ids", idsArray);

       
        Utils.saveJson(getSecretKey(), json, idDataFile);
        return newFile;
        
    }

    

    protected Future<?> readEncryptedFile( File file, Semaphore dataSemaphore, PipedOutputStream pipedOutput, Runnable aquired, EventHandler<WorkerStateEvent> onFailed){

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InterruptedException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException{
                try (
                    FileInputStream fileInputStream = new FileInputStream(file);
                ) {
                    dataSemaphore.acquire();
                    aquired.run();
                    if(file.length() > 11){
                        byte[] iV = new byte[12];
                        fileInputStream.read(iV);

                        Cipher decryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
                        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iV);
                        decryptCipher.init(Cipher.DECRYPT_MODE, getSecretKey(), parameterSpec);

                        int length = 0;
                        byte[] readBuffer = new byte[Utils.DEFAULT_BUFFER_SIZE];
                    
                        while ((length = fileInputStream.read(readBuffer)) != -1) {
                            byte[] decryptedBytes = readBuffer != null ? decryptCipher.update(readBuffer, 0, length) : null;
                            if(decryptedBytes != null){
                                pipedOutput.write(decryptedBytes);
                                pipedOutput.flush();
                            }
                        }

                        byte[] outBuffer = decryptCipher.doFinal();
                        if(outBuffer != null){
                            pipedOutput.write(outBuffer);
                            pipedOutput.flush();
                        }
                    }
                   
                }
                pipedOutput.close();
                return true;
            }
        };
        task.setOnFailed(finish->{
            try{
                pipedOutput.close();
            }catch(IOException e){

            }
            Utils.returnException(finish, getExecService(), onFailed);
        });


        return getExecService().submit(task);

    }


    protected Future<?> writeEncryptedFile( File file, File tmpFile, Semaphore semaphore, PipedOutputStream pipedOutputStream, EventHandler<WorkerStateEvent> onError){

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InterruptedException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException{

                try(
                    PipedInputStream pipedInputStream = new PipedInputStream(pipedOutputStream);
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                ){
                    
                    byte[] outIV = Utils.getIV();
                    Cipher encryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
                    GCMParameterSpec outputParameterSpec = new GCMParameterSpec(128, outIV);
                    encryptCipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), outputParameterSpec);

                    fileOutputStream.write(outIV);
                    int length = 0;
                    byte[] readBuffer = new byte[Utils.DEFAULT_BUFFER_SIZE];

                    while((length = pipedInputStream.read(readBuffer)) != -1){
                        byte[] outBytes = encryptCipher.update(readBuffer, 0, length);
                        if(outBytes != null){
                            fileOutputStream.write(outBytes);
                        }
                    }
                    byte[] outBytes = encryptCipher.doFinal();
                    if(outBytes != null){
                        fileOutputStream.write(outBytes);        
                    }
                }
               
                Files.deleteIfExists(file.toPath());
                FileUtils.moveFile(tmpFile, file);
                pipedOutputStream.close();
                semaphore.release();
                return true;
            }
        };


      

        task.setOnFailed(failed->{
            try{
                pipedOutputStream.close();
            }catch(IOException e){
                Utils.writeLogMsg("appData.writeEncryptedFile.pipedOutput.close", e);
            }
            getExecService().execute(()->{
                if(tmpFile.isFile()){
                    try{
                        Files.deleteIfExists(tmpFile.toPath());
                    }catch(IOException e1){       

                    }
                }
                semaphore.release();
                        
                Utils.returnException(failed, getExecService(), onError);
            });  
        });
        return getExecService().submit(task);

    }

    protected Future<?> saveEncrypteFile(File file, Semaphore semaphore,  byte[] bytes, EventHandler<WorkerStateEvent> onComplete, EventHandler<WorkerStateEvent> onError){
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InterruptedException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException{
                semaphore.acquire();
                try(
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                ){
                    
                    byte[] outIV = Utils.getIV();
                    Cipher encryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
                    GCMParameterSpec outputParameterSpec = new GCMParameterSpec(128, outIV);
                    encryptCipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), outputParameterSpec);

                    fileOutputStream.write(outIV);
                    byte[] outBytes = encryptCipher.update(bytes);
                    if(outBytes != null){
                        fileOutputStream.write(outBytes);
                    }
                    
                    byte[] bytes = encryptCipher.doFinal();
                    if(bytes != null){
                        fileOutputStream.write(bytes);        
                    }
                }
                semaphore.release();
                return true;
            }
        };


        task.setOnSucceeded(onComplete);

        task.setOnFailed(failed->{
            Throwable ex = failed.getSource().getException();
            if(ex != null && !(ex instanceof InterruptedException)){
                semaphore.release();
            }
            Utils.returnException(failed, getExecService(), onError);
     
        });
        return getExecService().submit(task);
    }


    protected Future<?> saveEncryptedFile( File file, Semaphore semaphore, PipedOutputStream pipedOutputStream, EventHandler<WorkerStateEvent> onComplete, EventHandler<WorkerStateEvent> onError){

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InterruptedException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException{
                semaphore.acquire();
                int length = 0;  
                byte[] readBuffer = new byte[Utils.DEFAULT_BUFFER_SIZE];
                try(
                    PipedInputStream pipedInputStream = new PipedInputStream(pipedOutputStream);
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                ){
                    
                    byte[] outIV = Utils.getIV();
                    Cipher encryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
                    GCMParameterSpec outputParameterSpec = new GCMParameterSpec(128, outIV);
                    encryptCipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), outputParameterSpec);

                    fileOutputStream.write(outIV);

                    while((length = pipedInputStream.read(readBuffer)) != -1){
                        byte[] outBytes = encryptCipher.update(readBuffer, 0, length);
                        if(outBytes != null){
                            fileOutputStream.write(outBytes);
                        }
                    }
                    byte[] bytes = encryptCipher.doFinal();
                    if(bytes != null){
                        fileOutputStream.write(bytes);        
                    }
                }
                semaphore.release();
                return true;
            }
        };


        task.setOnSucceeded(onComplete);

        task.setOnFailed(failed->{
            Throwable ex = failed.getSource().getException();
            if(ex != null && !(ex instanceof InterruptedException)){
                semaphore.release();
            }
            Utils.returnException(failed, getExecService(), onError);
     
        });
        return getExecService().submit(task);

    }


    public void shutdown(){

        try {
            getSecretKey().destroy();
        } catch (DestroyFailedException e) {
            Utils.writeLogMsg("NetworsData.onClosing", "Cannot destroy");
        }
    }
}