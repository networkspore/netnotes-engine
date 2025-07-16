package io.netnotes.engine.core;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import javax.crypto.SecretKey;

import io.netnotes.engine.AppConstants;
import io.netnotes.engine.BufferedButton;
import io.netnotes.engine.HashData;
import io.netnotes.engine.HashDataDownloader;
import io.netnotes.engine.NoteBytes;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.Stages;
import io.netnotes.engine.TabAppBox;
import io.netnotes.engine.UpdateInformation;
import io.netnotes.engine.Utils;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Text;

import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

    public class SettingsTab extends TabAppBox  {
        public final static String NAME = "Settings";
        public final static NoteBytes ID = new NoteBytes(NAME);
    
        private Semaphore m_dataSemaphore;
        private String m_status = NoteConstants.STATUS_STOPPED;
        private Stage m_updateStage = null;
        private Stage m_verifyStage = null;
        private Future<?> m_updateFuture = null;

        private AppData m_appData;

        public String getStatus(){
            return m_status;
        } 
    
        private AppData getAppData(){
            return m_appData;
        }

        
    
        public SettingsTab(AppData appData, Stage appStage, SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, MenuButton menuBtn, NetworksData network){
            super(ID, NAME, appStage, heightObject, widthObject, menuBtn, network);
            minHeightProperty().bind(heightObject);
    
            m_appData = appData;

            Button settingsButton = Stages.createImageButton(Stages.logo, "Settings");
    
            HBox settingsBtnBox = new HBox(settingsButton);
            settingsBtnBox.setAlignment(Pos.CENTER);
    
            Text passwordTxt = new Text(String.format("%-18s", "  Password:"));
            passwordTxt.setFill(Stages.txtColor);
            passwordTxt.setFont(Stages.txtFont);
    

            Button passwordBtn = new Button("(click to update)");
            passwordBtn.setAlignment(Pos.CENTER_LEFT);
            passwordBtn.setId("toolBtn");
            passwordBtn.setOnAction(e -> {
                if(m_updateStage == null && m_verifyStage == null){
                    m_verifyStage = getAppData().verifyAppKey(()->{
                        Button closeBtn = new Button();
                        String title = "Netnotes - Password";
                        m_updateStage = new Stage();
                        m_updateStage.getIcons().add(Stages.logo);
                        m_updateStage.initStyle(StageStyle.UNDECORATED);
                        m_updateStage.setTitle(title);
                
                       Stages.createPassword(m_updateStage, title, Stages.logo, Stages.logo, closeBtn, getNetwork().getExecService(), (onSuccess) -> {
                            if(m_updateFuture == null){
                                Object sourceObject = onSuccess.getSource().getValue();
            
                                if (sourceObject != null && sourceObject instanceof NoteBytes) {
                                    NoteBytes pass = (NoteBytes) sourceObject;
            
                                    if (pass.get().length > 0) {
            
                                        Stage statusStage = Stages.getStatusStage("Netnotes - Updating Password...", "Updating Password...");
                                        statusStage.show();
                                        
                                        m_updateFuture = updateAppKey(pass, onFinished ->{
                                            Object finishedObject = onFinished.getSource().getValue();
                                            statusStage.close();
                                            m_updateFuture = null;
                                            if(finishedObject != null && finishedObject instanceof Boolean && (Boolean) finishedObject){
                                                closeBtn.fire();
                                            }
                                        });
                                            
                                    }else{
                                        closeBtn.fire();
                                    }
                                }else{
                                    closeBtn.fire();
                                }
                            }
                    
                        });
                        m_updateStage.show();
                        m_updateStage.setOnCloseRequest(onCloseEvent->{
                            m_updateStage = null;
                        });
                        closeBtn.setOnAction(closeAction ->{
                            if(m_updateStage != null){
                                m_updateStage.close();
                                m_updateStage = null;
                            }
                        });
                    },()->{
                        m_verifyStage = null;
                    });
                }
            });

            
    
            HBox passwordBox = new HBox(passwordTxt, passwordBtn);
            passwordBox.setAlignment(Pos.CENTER_LEFT);
            passwordBox.setPadding(new Insets(10, 0, 10, 10));
            passwordBox.setMinHeight(30);
    
            Tooltip checkForUpdatesTip = new Tooltip();
            checkForUpdatesTip.setShowDelay(new javafx.util.Duration(100));
    
            String checkImageUrlString = AppConstants.CHECKMARK_ICON;
            
    
            BufferedButton checkForUpdatesToggle = new BufferedButton(getAppData().getUpdates() ? checkImageUrlString : null, Stages.MENU_BAR_IMAGE_WIDTH);
            checkForUpdatesToggle.setTooltip(checkForUpdatesTip);
    
            checkForUpdatesToggle.setOnAction(e->{
                boolean wasUpdates = getAppData().getUpdates();
                
                wasUpdates = !wasUpdates;
    
                checkForUpdatesToggle.setImage(wasUpdates ? new Image(checkImageUrlString) : null);
        
                try {
                    getAppData().setUpdates(wasUpdates);
                } catch (IOException e1) {
                    Alert a = new Alert(AlertType.NONE, e1.toString(), ButtonType.CANCEL);
                    a.setTitle("Error: File IO");
                    a.setHeaderText("Error");
                    a.initOwner(getAppStage());
                    a.show();
                }
            });
    
            Text versionTxt = new Text(String.format("%-18s", "  Version:"));
            versionTxt.setFill(Stages.txtColor);
            versionTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            TextField versionField = new TextField(getAppData().getAppInterface().getCurrentVersion() + "");
            versionField.setFont(Stages.txtFont);
            versionField.setId("formField");
            versionField.setEditable(false);
            HBox.setHgrow(versionField, Priority.ALWAYS);
       
            HBox versionBox = new HBox(versionTxt, versionField);
            versionBox.setPadding(new Insets(10,10,5,10));
            versionBox.setAlignment(Pos.CENTER_LEFT);
    
            Text fileTxt = new Text(String.format("%-18s", "  File:"));
            fileTxt.setFill(Stages.txtColor);
            fileTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            TextField fileField = new TextField(m_appData.getAppFile().getName());
            fileField.setFont(Stages.txtFont);
            fileField.setEditable(false);
            fileField.setId("formField");
            HBox.setHgrow(fileField, Priority.ALWAYS);
       
            HBox fileBox = new HBox(fileTxt, fileField);
            HBox.setHgrow(fileBox, Priority.ALWAYS);
            fileBox.setAlignment(Pos.CENTER_LEFT);
            fileBox.setPadding(new Insets(5,10,5,10));
        
            Text hashTxt = new Text(String.format("%-18s", "  Hash (Blake-2b):"));
            hashTxt.setFill(Stages.txtColor);
            hashTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            TextField hashField = new TextField(m_appData.appHashData().getHashStringHex());
            hashField.setFont(Stages.txtFont);
            hashField.setEditable(false);
            hashField.setId("formField");
            HBox.setHgrow(hashField, Priority.ALWAYS);
       
            HBox hashBox = new HBox(hashTxt, hashField);
            HBox.setHgrow(hashBox, Priority.ALWAYS);
            hashBox.setPadding(new Insets(5,10,5,10));
            hashBox.setAlignment(Pos.CENTER_LEFT);
    
            Text passwordHeading = new Text("Password");
            passwordHeading.setFont(Stages.txtFont);
            passwordHeading.setFill(Stages.txtColor);
    
            HBox passHeadingBox = new HBox(passwordHeading);
            HBox.setHgrow(passHeadingBox,Priority.ALWAYS);
            passHeadingBox.setId("headingBox");
            passHeadingBox.setPadding(new Insets(5));
    
            VBox passwordSettingsBox = new VBox(passHeadingBox, passwordBox);
            passwordSettingsBox.setId("bodyBox");
    
            Text appHeading = new Text("App");
            appHeading.setFont(Stages.txtFont);
            appHeading.setFill(Stages.txtColor);
    
            HBox appHeadingBox = new HBox(appHeading);
            HBox.setHgrow(appHeadingBox,Priority.ALWAYS);
            appHeadingBox.setId("headingBox");
            appHeadingBox.setPadding(new Insets(5));
    
            VBox appSettingsBox = new VBox(appHeadingBox, versionBox, fileBox, hashBox);
            appSettingsBox.setId("bodyBox");
            
    
            Text latestVersionTxt = new Text(String.format("%-18s", "  Version:"));
            latestVersionTxt.setFill(Stages.txtColor);
            latestVersionTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            Button latestVersionField = new Button("(Click to get latest info.)");
            latestVersionField.setFont(Stages.txtFont);
            latestVersionField.setId("formField");
            HBox.setHgrow(latestVersionField, Priority.ALWAYS);
       
            HBox latestVersionBox = new HBox(latestVersionTxt, latestVersionField);
            latestVersionBox.setPadding(new Insets(10,10,5,10));
            latestVersionBox.setAlignment(Pos.CENTER_LEFT);
    
            Text latestURLTxt = new Text(String.format("%-18s", "  Url:"));
            latestURLTxt.setFill(Stages.txtColor);
            latestURLTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            TextField latestURLField = new TextField();
            latestURLField.setFont(Stages.txtFont);
            latestURLField.setEditable(false);
            latestURLField.setId("formField");
            HBox.setHgrow(latestURLField, Priority.ALWAYS);
       
            HBox latestURLBox = new HBox(latestURLTxt, latestURLField);
            HBox.setHgrow(latestURLBox, Priority.ALWAYS);
            latestURLBox.setAlignment(Pos.CENTER_LEFT);
            latestURLBox.setPadding(new Insets(5,10,5,10));
    
            Text latestNameTxt = new Text(String.format("%-18s", "  File name:"));
            latestNameTxt.setFill(Stages.txtColor);
            latestNameTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            TextField latestNameField = new TextField();
            latestNameField.setFont(Stages.txtFont);
            latestNameField.setEditable(false);
            latestNameField.setId("formField");
            HBox.setHgrow(latestNameField, Priority.ALWAYS);
       
            HBox latestNameBox = new HBox(latestNameTxt, latestNameField);
            HBox.setHgrow(latestNameBox, Priority.ALWAYS);
            latestNameBox.setAlignment(Pos.CENTER_LEFT);
            latestNameBox.setPadding(new Insets(5,10,5,10));
        
            Text latestHashTxt = new Text(String.format("%-18s", "  Hash (Blake-2b):"));
            latestHashTxt.setFill(Stages.txtColor);
            latestHashTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            TextField latestHashField = new TextField();
            latestHashField.setFont(Stages.txtFont);
            latestHashField.setEditable(false);
            latestHashField.setId("formField");
            HBox.setHgrow(latestHashField, Priority.ALWAYS);
       
            HBox latestHashBox = new HBox(latestHashTxt, latestHashField);
            HBox.setHgrow(latestHashBox, Priority.ALWAYS);
            latestHashBox.setPadding(new Insets(5,10,5,10));
            latestHashBox.setAlignment(Pos.CENTER_LEFT);
    
            
            Text latestHeading = new Text("Latest");
            latestHeading.setFont(Stages.txtFont);
            latestHeading.setFill(Stages.txtColor);
    
            Region latestHeadingSpacer = new Region();
            HBox.setHgrow(latestHeadingSpacer, Priority.ALWAYS);
    
            Button downloadLatestBtn = new Button("Download");
            
            SimpleObjectProperty<UpdateInformation> updateInfoProperty = new SimpleObjectProperty<>();
    
            updateInfoProperty.addListener((obs,oldval,newval)->{
            
                latestHashField.setText(newval.getJarHashData().getHashStringHex());
                latestVersionField.setText(newval.getTagName());
                latestNameField.setText(newval.getJarName());
                latestURLField.setText(newval.getJarUrl());
            
            });
            
            
    
            Button getInfoBtn = new Button("Update");
            getInfoBtn.setId("checkBtn");
            getInfoBtn.setOnAction(e->{
                getAppData().checkForUpdates(getAppData().getAppInterface().getGitHubUser(), getAppData().getAppInterface().getGitHubProject(),  updateInfoProperty);         
            });
            downloadLatestBtn.setOnAction(e->{
                SimpleObjectProperty<UpdateInformation> downloadInformation = new SimpleObjectProperty<>();
                UpdateInformation updateInfo = updateInfoProperty.get();
                File appDir = getAppData().getAppDir();
                if(updateInfo != null && updateInfo.getJarHashData() != null){
                
                    HashData appHashData = updateInfo.getJarHashData();
                    String appName = updateInfo.getJarName();
                    String urlString = updateInfo.getJarUrl();
                 
                    HashDataDownloader dlder = new HashDataDownloader(Stages.logo, urlString, appName, appDir, appHashData, HashDataDownloader.Extensions.getJarFilter());
                    dlder.start(getExecService());
    
                }else{
                    downloadInformation.addListener((obs,oldval,newval)->{
                        if(newval != null){
                            updateInfoProperty.set(newval);
    
                            String urlString = newval.getJarUrl();
                            if(urlString.startsWith("http")){  
                                HashData latestHashData = newval.getJarHashData();
                                HashDataDownloader dlder = new HashDataDownloader(Stages.logo, urlString, latestNameField.getText(),appDir, latestHashData, HashDataDownloader.Extensions.getJarFilter());
                                dlder.start(getExecService());
                            }
                        }
                    });
                    getAppData().checkForUpdates(getAppData().getAppInterface().getGitHubUser(), getAppData().getAppInterface().getGitHubProject(),  downloadInformation);
                }
            });
    
            latestVersionField.setOnAction(e->{
                getInfoBtn.fire();
            });
    
            HBox latestHeadingBox = new HBox(latestHeading, latestHeadingSpacer, getInfoBtn);
            HBox.setHgrow(latestHeadingBox,Priority.ALWAYS);
            latestHeadingBox.setId("headingBox");
            latestHeadingBox.setPadding(new Insets(5,10,5,10));
            latestHeadingBox.setAlignment(Pos.CENTER_LEFT);
         
            
           
            HBox downloadLatestBox = new HBox(downloadLatestBtn);
            downloadLatestBox.setAlignment(Pos.CENTER_RIGHT);
            downloadLatestBox.setPadding(new Insets(5,15,10,10));
    
            VBox latestSettingsBox = new VBox(latestHeadingBox, latestVersionBox, latestNameBox, latestURLBox, latestHashBox, downloadLatestBox);
            latestSettingsBox.setId("bodyBox");
            
            
            Region settingsSpacer1 = new Region();
            settingsSpacer1.setMinHeight(15);
    
            Region settingsSpacer2 = new Region();
            settingsSpacer2.setMinHeight(15);
    
            VBox settingsVBox = new VBox(settingsBtnBox, passwordSettingsBox, settingsSpacer1, appSettingsBox, settingsSpacer2, latestSettingsBox);
            HBox.setHgrow(settingsVBox, Priority.ALWAYS);
    
            settingsVBox.setAlignment(Pos.CENTER_LEFT);
            settingsVBox.setPadding(new Insets(5,10,5,5));
    
    
    
            HBox.setHgrow(settingsVBox, Priority.ALWAYS);
    
            getChildren().add(settingsVBox);
    
         
            prefWidthProperty().bind(widthObject);
    
        }
    
        private SimpleStringProperty m_titleProperty = new SimpleStringProperty(NAME);
    
        public SimpleStringProperty titleProperty(){
            return m_titleProperty;
        }
         
        public String getName(){
            return NAME;
        }
        
     
            
        private Future<?> updateAppKey(NoteBytes newPassword, EventHandler<WorkerStateEvent> onFinished){

            Task<Object> task = new Task<Object>() {
                @Override
                public Object call() throws InterruptedException, IOException, NoSuchAlgorithmException, InvalidKeySpecException{
                    if(newPassword.byteLength() > 0){ 
                        m_dataSemaphore.acquire();
                        SecretKey oldAppKey = getAppData().getSecretKey();
                        String hash = Utils.getBcryptHashString(newPassword.getChars());
                        getAppData().setAppKey(hash);
                        getAppData().createKey(newPassword);
                        
                        getAppData().updateDataEncryption(oldAppKey, getAppData().getSecretKey());
                        m_dataSemaphore.release();
                        return true;
                    }
                    return false;
                }
            };
        
            task.setOnFailed((onFailed)->{
                m_dataSemaphore.release();
                Utils.returnObject(false, getExecService(), onFinished, null);
            });

            task.setOnSucceeded(onFinished);

            return getExecService().submit(task);
            
        }


        public ExecutorService getExecService(){
            return super.getNetwork().getNetworksData(). getExecService();
        }
    }
