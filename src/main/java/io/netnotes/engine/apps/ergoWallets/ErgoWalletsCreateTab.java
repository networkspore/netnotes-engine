package io.netnotes.engine.apps.ergoWallets;

import java.io.File;

import org.ergoplatform.appkit.Mnemonic;
import org.ergoplatform.sdk.SecretString;

import com.google.gson.JsonObject;
import com.satergo.Wallet;

import io.netnotes.engine.AppData;
import io.netnotes.engine.ContentTab;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.Stages;
import io.netnotes.engine.SubmitButton;
import io.netnotes.engine.Utils;
import io.netnotes.engine.networks.ergo.ErgoConstants;
import io.netnotes.friendly_id.FriendlyId;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.layout.Region;

public class ErgoWalletsCreateTab extends ContentTab {
    
    private SubmitButton m_submitButton;
    private Stage m_passwordStage = null;
    private TextArea mnemonicField = null;

    public ErgoWalletsCreateTab (boolean isNew, NetworkInformation networkInformation, ErgoWalletControl walletControl, SubmitButton submitButton){
        this(isNew, networkInformation, "Wallet: " + (isNew ? "Create" : "Restore"), new VBox(),walletControl, submitButton);
    }

    public ErgoWalletsCreateTab(boolean isNew, NetworkInformation networkInformation,String title, VBox layoutVBox,ErgoWalletControl walletControl, SubmitButton submitButton) {
         super(FriendlyId.createFriendlyId(), networkInformation.getNetworkId(), networkInformation.getSmallIcon(), title, layoutVBox );

        Image smallIcon = networkInformation.getSmallIcon();
        Image largeIcon = networkInformation.getIcon();

        m_submitButton = submitButton;
        
        Label headingText = new Label(title);
        headingText.setFont(Stages.txtFont);
        headingText.setPadding(new Insets(0,0,0,15));

        Button closeBtn = new Button();

        HBox rightBox = new HBox(closeBtn);

        Region rightSpacer = new Region();
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        ImageView headingImageview = new ImageView(smallIcon);
        headingImageview.setPreserveRatio(true);
        headingImageview.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);

        HBox headingBox = new HBox(headingImageview, headingText,rightSpacer, rightBox);
    
    
        VBox headerBox = new VBox(headingBox);
        headerBox.setPadding(new Insets(0, 5, 0, 0));

        mnemonicField = new TextArea(isNew ? Mnemonic.generateEnglishMnemonic() : "");
        mnemonicField.setFont(Stages.txtFont);
        mnemonicField.setId("textFieldCenter");
        mnemonicField.setEditable(!isNew);
        mnemonicField.setWrapText(true);
        mnemonicField.setPrefRowCount(4);
        mnemonicField.setPromptText("(enter mnemonic)");
        HBox.setHgrow(mnemonicField, Priority.ALWAYS);

        Platform.runLater(() -> mnemonicField.requestFocus());

        HBox mnemonicFieldBox = new HBox(mnemonicField);
        mnemonicFieldBox.setId("bodyBox");
        mnemonicFieldBox.setPadding(new Insets(15, 0,0,0));
        HBox.setHgrow(mnemonicFieldBox, Priority.ALWAYS);

        Button nextBtn = new Button("Next");


        HBox nextBox = new HBox(nextBtn);
        nextBox.setAlignment(Pos.CENTER);
        nextBox.setPadding(new Insets(20, 0, 20, 0));




        HBox mnBodyBox = new HBox(mnemonicFieldBox);
        HBox.setHgrow(mnBodyBox, Priority.ALWAYS);
        mnBodyBox.setPadding(new Insets(20,0,0,0));

        VBox bodyBox = new VBox(mnBodyBox, nextBox);
        VBox.setMargin(bodyBox, new Insets(10, 10, 0, 20));

        
        layoutVBox.getChildren().addAll(headerBox, bodyBox);







        closeBtn.setOnAction(e->{
            clear();
            close();
        });


        Tooltip errorToolTip = new Tooltip();

        PauseTransition pt = new PauseTransition(javafx.util.Duration.millis(1600));
        pt.setOnFinished(ptE->{
            errorToolTip.hide();
        });

        char[] chars = Utils.getAsciiCharArray();

        nextBtn.setOnAction(e -> {
            if(mnemonicField.getText().length() > 0 && m_passwordStage == null){

                m_passwordStage = new Stage();
                m_passwordStage.getIcons().add(networkInformation.getIcon());
                m_passwordStage.setResizable(false);
                m_passwordStage.initStyle(StageStyle.UNDECORATED);
                m_passwordStage.setTitle(title);

                char[] mnemonic = mnemonicField.getText().toCharArray();
                clear();
        
                Button closeStageBtn = new Button();

                Button backBtn = new Button();
                backBtn.setText("↩");
                backBtn.setPadding(new Insets(0, 2, 1, 2));
                backBtn.setId("toolBtn");
        

                Scene passwordScene = Stages.createPasswordScene(largeIcon, Stages.createTopBar(backBtn, smallIcon, "Create wallet password", closeStageBtn, m_passwordStage), walletControl.getExecService(), (onPassword)->{
                    Object resultObj = onPassword.getSource().getValue();
                    SecretString pass = resultObj != null && resultObj instanceof SecretString ? (SecretString) resultObj : null;
                    if(pass != null){
                        Button cancelFileCreation = new Button("Cancel"); 
                        Label creationLbl = new Label("Saving wallet..");
                        Scene waitingScene = Stages.getWaitngScene(creationLbl, cancelFileCreation, m_passwordStage);
                        m_passwordStage.setScene(waitingScene);
                        
                        FileChooser saveWalletFileChooser = new FileChooser();
                        saveWalletFileChooser.setTitle("Save wallet (*.erg)");
                        saveWalletFileChooser.setInitialDirectory(AppData.HOME_DIRECTORY);
                        saveWalletFileChooser.getExtensionFilters().add(ErgoConstants.ERGO_WALLET_EXT);
                        saveWalletFileChooser.setSelectedExtensionFilter(ErgoConstants.ERGO_WALLET_EXT);

                        File walletFile =saveWalletFileChooser.showSaveDialog(m_passwordStage);
                        if(walletFile != null){
                            try{
                                Wallet.create(
                                    walletFile.toPath(), 
                                    Mnemonic.create(SecretString.create(mnemonic), pass), 
                                    walletFile.getName(), 
                                    pass);
                                backBtn.setOnAction(null);
                                pass.erase();
                                try{
                                    Utils.fillCharArray(mnemonic, chars);
                                }catch(Exception algoEx){

                                }

                                Utils.checkDrive(walletFile, walletControl.getExecService(), onFilePath->
                                {
                                    Object sourceValue = onFilePath.getSource().getValue();
                                    if(sourceValue != null && sourceValue instanceof String){
                                        String filePath = (String) sourceValue;

                                        JsonObject json = new JsonObject(); 
                                        json.addProperty("walletFilePath", filePath);

                                        //walletFile.getAbsolutePath()
                                
                                        Utils.returnObject(json, walletControl.getExecService(), m_submitButton.getOnSubmit());
                                    }else{
                                             Utils.showTip("Drive: not found", nextBtn, errorToolTip, pt);
                              
                                    }
                                }, onFailed->{
                                    Throwable throwable = onFailed.getSource().getException();
                                    Utils.showTip("Drive: " + (throwable != null ? throwable.getMessage() : "not found"), nextBtn, errorToolTip, pt);
                                });

                            }catch(Exception er){
                                backBtn.fire();
                                pass.erase();
                                try{
                                    Utils.fillCharArray(mnemonic, chars);
                                }catch(Exception algoEx){

                                }
                                Utils.showTip("Canceled: " + er.toString(), nextBtn, errorToolTip, pt);
                            
                           
                            }
                        }else{
                            backBtn.fire();
                            pass.erase();
                            try{
                                Utils.fillCharArray(mnemonic, chars);
                            }catch(Exception algoEx){

                            }
                        
                            Utils.showTip("Creation canceled", nextBtn, errorToolTip, pt);
                            
                        }
                    }else{
                        backBtn.fire();
                        Utils.showTip("Failed to create password", nextBtn, errorToolTip, pt);
                    }
                });
            
                m_passwordStage.setScene(passwordScene);

                closeStageBtn.setOnAction(c->{
                    closeBtn.fire();
                });

                backBtn.setOnAction(b ->{
                    m_passwordStage.close();
                    mnemonicField.setText(new String(mnemonic));
                 
                    Utils.fillCharArray(mnemonic, chars);
                   
                });
            }
            
        });

    }


    private void clear(){
            mnemonicField.setText(Mnemonic.generateEnglishMnemonic());
            mnemonicField.setText("");
            mnemonicField.setText(Mnemonic.generateEnglishMnemonic());
            mnemonicField.setText("");
    }
    
    @Override
    public void shutdown(){
        clear();
        super.shutdown();
    }
}
