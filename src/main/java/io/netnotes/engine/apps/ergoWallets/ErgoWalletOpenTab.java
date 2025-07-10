package io.netnotes.engine.apps.ergoWallets;


import java.io.File;

import org.ergoplatform.sdk.SecretString;

import com.google.gson.JsonObject;
import com.satergo.Wallet;

import io.netnotes.engine.AppData;
import io.netnotes.engine.ContentTab;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.Stages;
import io.netnotes.engine.SubmitButton;
import io.netnotes.engine.Utils;
import io.netnotes.friendly_id.FriendlyId;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;


public class ErgoWalletOpenTab extends ContentTab {
    private SubmitButton m_submitButton;
    private SimpleObjectProperty<File> m_walletFileProperty = new SimpleObjectProperty<>();
    public ErgoWalletOpenTab (NetworkInformation networkInformation,ErgoWalletControl walletControl, SubmitButton submitButton){
        this(null, networkInformation, "Ergo Wallet: Open", new VBox(),walletControl, submitButton);
    }

    public ErgoWalletOpenTab (File walletFile, NetworkInformation networkInformation,ErgoWalletControl walletControl, SubmitButton submitButton){
        this(walletFile, networkInformation, "Ergo Wallet: Open", new VBox(),walletControl, submitButton);
    }
    

    private ErgoWalletOpenTab(File walletFile, NetworkInformation networkInformation, String title, VBox layoutVBox,ErgoWalletControl walletControl, SubmitButton submitButton){
        super(FriendlyId.createFriendlyId(), networkInformation.getNetworkId(), networkInformation.getSmallIcon(), title, layoutVBox );

        m_submitButton = submitButton;
        m_walletFileProperty.set(walletFile);
        Tooltip noticeToolTip = new Tooltip("");

        PauseTransition noticePt = new PauseTransition(Duration.millis(5600));
        noticePt.setOnFinished(e->{
            noticeToolTip.hide();
        });
  

        Label headingText = new Label(title + " (*.erg)");
        headingText.setFont(Stages.txtFont);
        headingText.setPadding(new Insets(0,0,0,15));

        Button closeBtn = new Button();

        HBox headingBox = new HBox( headingText);
    
    
        VBox headerBox = new VBox(headingBox);
        headerBox.setPadding(new Insets(0, 5, 0, 0));

        Label walletNameText = new Label("Name ");
        walletNameText.setFont(Stages.txtFont);
        walletNameText.setMinWidth(70);

        TextField walletNameField = new TextField();
        HBox.setHgrow(walletNameField, Priority.ALWAYS);

        HBox walletNameFieldBox = new HBox(walletNameField);
        HBox.setHgrow(walletNameFieldBox, Priority.ALWAYS);
        walletNameFieldBox.setId("bodyBox");
        walletNameFieldBox.setPadding(new Insets(0, 5, 0, 0));
        walletNameFieldBox.setMaxHeight(18);
        walletNameFieldBox.setAlignment(Pos.CENTER_LEFT);

        HBox walletNameBox = new HBox(walletNameText, walletNameFieldBox);
        walletNameBox.setAlignment(Pos.CENTER_LEFT);
        walletNameBox.setPadding(new Insets(2, 0, 2, 0));
        HBox.setHgrow(walletNameBox, Priority.ALWAYS);
        

        Label walletFileText = new Label("File");
        walletFileText.setFont(Stages.txtFont);
        walletFileText.setMinWidth(70);
        
        final String selectWalletText = "[ Select Wallet ]";

        Button walletFileField = new Button(m_walletFileProperty.get() == null ? selectWalletText : m_walletFileProperty.get().getAbsolutePath());
        HBox.setHgrow(walletFileField, Priority.ALWAYS);
        walletFileField.setId("tokenBtn");
        m_walletFileProperty.addListener((obs,oldval,newval)->{
            walletFileField.setText(newval == null ? selectWalletText : newval.getAbsolutePath());
        });
      

        Button walletFileOpenBtn = new Button("…");
        walletFileOpenBtn.setId("lblBtn");

        walletFileField.setOnAction(e->walletFileOpenBtn.fire());

        HBox walletFileFieldBox = new HBox(walletFileField, walletFileOpenBtn);
        HBox.setHgrow(walletFileFieldBox, Priority.ALWAYS);
        walletFileFieldBox.setId("bodyBox");
        walletFileFieldBox.setAlignment(Pos.CENTER_LEFT);
        walletFileFieldBox.setMaxHeight(18);
        walletFileFieldBox.setPadding(new Insets(0, 5, 0, 0));

        walletFileField.prefWidthProperty().bind(walletFileFieldBox.widthProperty().subtract(walletFileOpenBtn.widthProperty()).subtract(1));


        HBox walletFileBox = new HBox(walletFileText, walletFileFieldBox);
        walletFileBox.setAlignment(Pos.CENTER_LEFT);
        walletFileBox.setPadding(new Insets(2, 0, 2, 0));
        HBox.setHgrow(walletFileBox, Priority.ALWAYS);

        
 
        m_submitButton.setDisable(true);
        m_submitButton.setId("disableBtn");

  

        Runnable openFile = new Runnable(){
            private File openFile_walletFile = null;

            public void run(){
                Scene scene = layoutVBox.getScene();
                Window window = scene != null ? scene.getWindow() : null;

                if(openFile_walletFile == null && window != null){
                    FileChooser openFileChooser = new FileChooser();
                    openFileChooser.setTitle("Select wallet (*.erg)");
                    openFileChooser.setInitialDirectory(AppData.HOME_DIRECTORY);
                    openFileChooser.getExtensionFilters().add(ErgoWallets.ergExt);
                    openFileChooser.setSelectedExtensionFilter(ErgoWallets.ergExt);

                    openFile_walletFile = openFileChooser.showOpenDialog(window);
                    
                    if (openFile_walletFile != null) {
                        enterFilePassword();
                    }
                }else{
                    Utils.showTip("File disalog: In use - Please select file", walletFileOpenBtn, noticeToolTip, noticePt);
   
                }

            }
        
            private void enterFilePassword(){
          
                Stages.enterPassword("Enter Password - (close to cancel)", networkInformation.getIcon(), networkInformation.getSmallIcon(), "Wallet: " + openFile_walletFile.getName(), walletControl.getExecService(), (onPassword->{
                    Object secretObject = onPassword.getSource().getValue();
                    if(secretObject != null && secretObject instanceof SecretString){
                    
                        try {
                            Wallet.load(openFile_walletFile.toPath(), (SecretString) secretObject);
                            m_walletFileProperty.set(openFile_walletFile);
                            walletFileField.setText(openFile_walletFile.getAbsolutePath());

                            if(walletNameField.getText().length() == 0){
                                walletNameField.setText(walletControl.checkWalletName(openFile_walletFile.getName()));
                            }
                            
                            openFile_walletFile = null;
                        } catch (Exception e1) {
                            enterFilePassword();
                        }
                    
            
                    }
                }), onFailed->{
                    
                    openFile_walletFile = null;
                    
                });
                   
                
            }
        };

        walletFileOpenBtn.setOnAction(e -> openFile.run());


        HBox nextBox = new HBox(m_submitButton);
        nextBox.setAlignment(Pos.CENTER);
        nextBox.setPadding(new Insets(10, 10, 20, 0));

        VBox inputBoxes = new VBox(walletNameBox, walletFileBox);
        inputBoxes.setPadding(new Insets(10, 0,10, 0));

        VBox bodyBox = new VBox(inputBoxes, nextBox);
        VBox.setMargin(bodyBox, new Insets(10, 10, 0, 20));

        
        layoutVBox.getChildren().addAll(headerBox, bodyBox);

  

        closeBtn.setOnAction(e->{
            this.close();
        });



      


        EventHandler<ActionEvent> nextAction = e -> {
            m_submitButton.setDisable(true);
            if(!walletFileField.getText().equals(selectWalletText)){
                
                Object walletFileUserObject = walletFileField.getUserData();
                if(walletFileUserObject != null && walletFileUserObject instanceof File){

                    File file = (File) walletFileUserObject;
                    String pathString = walletFileField.getText();
                    if(file.getAbsolutePath().equals(pathString)){
                        String name = walletNameField.getText().length() > 0 ? walletNameField.getText() : walletFile.getName();
                        JsonObject json = new JsonObject();
                        json.addProperty("name", name);
                        json.addProperty("fileString", pathString);
                        json.addProperty("networkType", ErgoWallets.NETWORK_TYPE.toString());

            
                        Utils.returnObject(json, walletControl.getExecService(), m_submitButton.getOnSubmit());

                    }
                }else{
                    noticeToolTip.setText("Wallet file is not valid");
                }
            }else{
                noticeToolTip.setText(selectWalletText);
            }
            
        };

        walletFileField.textProperty().addListener((obs,oldval,newval)->{
            if(newval.equals(selectWalletText)){
                m_submitButton.setDisable(true);
                m_submitButton.setId("disabledBtn");
                m_submitButton.setOnAction(null);
            }else{
                m_submitButton.setDisable(false);
                m_submitButton.setOnAction(nextAction);
                m_submitButton.setId(null);
            }
        });

        openFile.run();

        m_submitButton.setOnError((onError)->{
            Throwable throwable = onError.getSource().getException();
            if(throwable != null){
                String msg = throwable.getMessage();
                noticeToolTip.setText("Error: " + msg);
            }
        });
    }
    
    @Override
    public void shutdown(){
       
        
        if(m_submitButton != null){
            m_submitButton.setOnAction(null);
            m_submitButton.setOnSubmit(null);
            m_submitButton.setOnError(null);
            m_submitButton = null;
        }
        m_walletFileProperty.set(null);

        super.shutdown();
    }
}
