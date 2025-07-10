package io.netnotes.engine.apps.ergoWallets;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import javafx.util.Duration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.AmountBoxInterface;
import io.netnotes.engine.ContentTab;
import io.netnotes.engine.JsonParametersBox;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.PriceAmount;
import io.netnotes.engine.PriceCurrency;
import io.netnotes.engine.Stages;
import io.netnotes.engine.Utils;
import io.netnotes.engine.networks.ergo.AddressBox;
import io.netnotes.engine.networks.ergo.AddressInformation;
import io.netnotes.engine.networks.ergo.ErgoConstants;
import io.netnotes.engine.networks.ergo.ErgoCurrency;
import io.netnotes.friendly_id.FriendlyId;

import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

public class SendAssetsTab extends ContentTab {
    
    private ErgoWalletAmountSendBoxes m_amountSendBoxes;
    private Button m_sendBtn = new Button("Send");
    private Tooltip m_errTip = new Tooltip();
    private TextField m_feesField;
    private VBox m_sendBodyContentBox;
    private AddressBox m_sendToAddress;
    private VBox m_sendBodyBox;
    private ErgoWalletControl m_walletControl;
    private Stage m_appStage;
    

    public SendAssetsTab(Stage appStage, Image icon, ErgoWalletControl ergoWalletControl){
        this(appStage, FriendlyId.createFriendlyId(), ergoWalletControl.getParentId(), icon, new VBox(), ergoWalletControl);
    }

    private SendAssetsTab(Stage appStage, String tabId, String parentId, Image logo, VBox layoutVBox, ErgoWalletControl walletControl){
        super(tabId, parentId, logo, "Send assets", new VBox());
        m_walletControl = walletControl;
        m_appStage = appStage;

        Label headingText = new Label("Send assets");
        headingText.setFont(Stages.txtFont);
        headingText.setPadding(new Insets(0,0,0,15));

        HBox headingBox = new HBox(headingText);
        headingBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headingBox, Priority.ALWAYS);
        headingBox.setPadding(new Insets(5, 15, 5, 15));
    
        VBox headerBox = new VBox(headingBox);
        headerBox.setPadding(new Insets(0, 5, 0, 0));

        Region hBar = new Region();
        hBar.setPrefWidth(400);
        hBar.setPrefHeight(2);
        hBar.setMinHeight(2);
        hBar.setId("hGradient");

        HBox gBox = new HBox(hBar);
        gBox.setAlignment(Pos.CENTER);
        gBox.setPadding(new Insets(0, 0, 20, 0));


        Label addressText = new Label("Address ");
        addressText.setFont(Stages.txtFont);
        addressText.setMinWidth(90);

        HBox addressTextBox = new HBox(addressText);
        addressTextBox.setAlignment(Pos.CENTER_LEFT);
        addressTextBox.setPadding(new Insets(0,0,2,5));

        Region addressHBar = new Region();
        addressHBar.setPrefWidth(400);
        addressHBar.setPrefHeight(2);
        addressHBar.setMinHeight(2);
        addressHBar.setId("hGradient");

        HBox addressGBox = new HBox(addressHBar);
        addressGBox.setAlignment(Pos.CENTER);
        addressGBox.setPadding(new Insets(0,0,0,0));

        m_sendToAddress = new AddressBox(new AddressInformation(""), m_appStage.getScene(), m_walletControl.getNetworkType());
        HBox.setHgrow(m_sendToAddress, Priority.ALWAYS);
        m_sendToAddress.getInvalidAddressList().add(m_walletControl.getCurrentAddress());
    
        HBox addressFieldBox = new HBox(m_sendToAddress);
        HBox.setHgrow(addressFieldBox, Priority.ALWAYS);
        
        VBox addressPaddingBox = new VBox(addressTextBox, addressGBox, addressFieldBox);
        addressPaddingBox.setPadding(new Insets(0,10,10,10));

        Label amountText = new Label("Amount ");
        amountText.setFont(Stages.txtFont);
        
        HBox amountTextBox = new HBox(amountText);
        amountTextBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(amountTextBox, Priority.ALWAYS);
        amountTextBox.setPadding(new Insets(10, 0,2,5));

        Region amountHBar = new Region();
        amountHBar.setPrefWidth(400);
        amountHBar.setPrefHeight(2);
        amountHBar.setMinHeight(2);
        amountHBar.setId("hGradient");

        HBox amountGBox = new HBox(amountHBar);
        amountGBox.setAlignment(Pos.CENTER);
        amountGBox.setPadding(new Insets(0,0,10,0));

        m_amountSendBoxes = new ErgoWalletAmountSendBoxes(m_appStage.getScene(), m_walletControl.getNetworkType(), m_walletControl.balanceProperty());
        HBox.setHgrow(m_amountSendBoxes, Priority.ALWAYS);

        VBox walletListBox = new VBox( m_amountSendBoxes);
        walletListBox.setPadding(new Insets(0, 0, 10, 5));
        walletListBox.minHeight(80);
        HBox.setHgrow(walletListBox, Priority.ALWAYS);

        Label feesLabel = new Label("Fee");
        feesLabel.setFont(Stages.txtFont);
        feesLabel.setMinWidth(50);

        String feesFieldId = FriendlyId.createFriendlyId();

        m_feesField = new TextField(ErgoConstants.MIN_NETWORK_FEE + "");
        m_feesField.setPrefWidth(100);
        m_feesField.setUserData(feesFieldId);
        m_feesField.setOnKeyPressed(e->{
            if (Utils.keyCombCtrZ.match(e) ) { 
                e.consume();
            }
        });
        m_feesField.textProperty().addListener((obs,oldval,newval)->{
            String number = newval.replaceAll("[^0-9.]", "");
  
            int index = number.indexOf(".");
            String leftSide = index != -1 ? number.substring(0, index + 1) : number;
            String rightSide = index != -1 ?  number.substring(index + 1) : "";
            rightSide = rightSide.length() > 0 ? rightSide.replaceAll("[^0-9]", "") : "";
            rightSide = rightSide.length() > ErgoCurrency.DECIMALS ? rightSide.substring(0, ErgoCurrency.DECIMALS) : rightSide;
            m_feesField.setText(leftSide +  rightSide);
        });
        m_feesField.focusedProperty().addListener((obs,oldval,newval)->{
            if(!newval){
                checkFeeField();
            }
        });
  
        

        MenuButton feeTypeBtn = new MenuButton();
        feeTypeBtn.setFont(Stages.txtFont);

        MenuItem ergFeeTypeItem = new MenuItem(String.format("%-20s", "Ergo")+ "(ERG)");
        ergFeeTypeItem.setOnAction(e->{
            PriceCurrency currency = new ErgoCurrency(m_walletControl.getNetworkType());
            feeTypeBtn.setUserData(currency);
            feeTypeBtn.setText(currency.getSymbol());
        });

        feeTypeBtn.getItems().add(ergFeeTypeItem);

        Runnable setFee = ()->{
            String feesString = Utils.isTextZero(m_feesField.getText()) ? "0" : m_feesField.getText();
            BigDecimal fee = new BigDecimal(feesString);
            
            Object feeTypeUserData = feeTypeBtn.getUserData();
            if(feeTypeUserData != null && feeTypeUserData instanceof PriceCurrency){
                PriceCurrency currency = (PriceCurrency) feeTypeUserData;
                m_amountSendBoxes.feeAmountProperty().set(new PriceAmount(fee, currency));
            }
        };

        feeTypeBtn.textProperty().addListener((obs,oldval,newval)->{
            setFee.run();
        });
     
        SimpleBooleanProperty isFeesFocused = new SimpleBooleanProperty(false);

        m_appStage.getScene().focusOwnerProperty().addListener((obs, old, newPropertyValue) -> {
            if (newPropertyValue != null && newPropertyValue instanceof TextField) {
                TextField focusedField = (TextField) newPropertyValue;
                Object userData = focusedField.getUserData();
                if(userData != null && userData instanceof String){
                    String userDataString = (String) userData;
                    if(userDataString.equals(feesFieldId)){
                        isFeesFocused.set(true);
                    }else{
                        if(isFeesFocused.get()){
                            isFeesFocused.set(false);
                            setFee.run();
                        }
                    }
                }else{
                    if(isFeesFocused.get()){
                        isFeesFocused.set(false);
                        setFee.run();
                    }
                }
            }else{
                if(isFeesFocused.get()){
                    isFeesFocused.set(false);
                    setFee.run();
                }
            }
        });

        ergFeeTypeItem.fire();
        HBox feeEnterBtnBox = new HBox();
        feeEnterBtnBox.setAlignment(Pos.CENTER_LEFT);

        HBox feesFieldBox = new HBox(m_feesField, feeEnterBtnBox, feeTypeBtn);
        feesFieldBox.setId("bodyBox");
        feesFieldBox.setAlignment(Pos.CENTER_LEFT);
        feesFieldBox.setPadding(new Insets(2));

        HBox feesBox = new HBox(feesLabel, feesFieldBox);
        HBox.setHgrow(feesBox, Priority.ALWAYS);
        feesBox.setAlignment(Pos.CENTER_RIGHT);
        feesBox.setPadding(new Insets(0,10,20,0));

        Region sendHBar = new Region();
        sendHBar.setPrefWidth(400);
        sendHBar.setPrefHeight(2);
        sendHBar.setMinHeight(2);
        sendHBar.setId("hGradient");

        HBox sendGBox = new HBox(sendHBar);
        sendGBox.setAlignment(Pos.CENTER);
        sendGBox.setPadding(new Insets(10,0,0,0));

        VBox amountPaddingBox = new VBox(amountTextBox, amountGBox, walletListBox, feesBox, sendGBox);
        amountPaddingBox.setPadding(new Insets(0,10,0,10));
    
        HBox nextBox = new HBox(m_sendBtn);
        nextBox.setAlignment(Pos.CENTER);
        nextBox.setPadding(new Insets(20, 0, 5, 0));

        m_sendBodyBox = new VBox(addressPaddingBox, amountPaddingBox, nextBox);

        m_sendBodyContentBox = new VBox(m_sendBodyBox);

        VBox bodyBox = new VBox(gBox, m_sendBodyContentBox);
        VBox.setMargin(bodyBox, new Insets(10, 0, 10, 0));

        layoutVBox.getChildren().addAll(headerBox, bodyBox);

        m_sendBtn.setOnAction(e->{
        
            m_sendBodyContentBox.getChildren().clear();

            Text sendText = new Text("Sending...");
            sendText.setFont(Stages.txtFont);
            sendText.setFill(Stages.txtColor);

            HBox sendTextBox = new HBox(sendText);
            HBox.setHgrow(sendTextBox, Priority.ALWAYS);
            sendTextBox.setAlignment(Pos.CENTER);

            ProgressBar progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
            progressBar.setPrefWidth(400);
            
            VBox statusBox = new VBox(sendTextBox, progressBar);
            HBox.setHgrow(statusBox, Priority.ALWAYS);
            statusBox.setAlignment(Pos.CENTER);
            statusBox.setPrefHeight(200);

            m_sendBodyContentBox.getChildren().add(statusBox);

            JsonObject sendObject = new JsonObject();
            
            AddressInformation addressInformation = m_sendToAddress.addressInformationProperty().get();
            if (addressInformation == null  || (addressInformation != null && addressInformation.getAddress() == null))  {
                
                addSendBox();
                showError("Error: Enter valid recipient address");
                return;
            }

            if (!m_sendToAddress.isAddressValid().get())  {
                
                addSendBox();
                showError("Error: Address is an invalid recipient");
                return;
            }
            if(m_walletControl.isUnlocked()){
                addSendBox();
                showError("Error: No wallet selected");
                return;
            }
            NoteConstants.addWalletAddressToDataObject("senderAddress", m_walletControl.getCurrentAddress(), m_walletControl.getWalletName(), NoteConstants.CURRENT_WALLET_FILE, sendObject);
    
            JsonObject recipientObject = new JsonObject();
            recipientObject.addProperty("address", addressInformation.getAddress().toString());
            recipientObject.addProperty("addressType", addressInformation.getAddressTypeString());
            
            sendObject.add("recipient", recipientObject);

            BigDecimal minimumDecimal = m_amountSendBoxes.minimumFeeProperty().get();
            PriceAmount feePriceAmount = m_amountSendBoxes.feeAmountProperty().get();
            if(minimumDecimal == null || (feePriceAmount == null || (feePriceAmount != null && feePriceAmount.amountProperty().get().compareTo(minimumDecimal) == -1))){
                addSendBox();
                showError("Error: Minimum fee " +(minimumDecimal != null ? ("of " + minimumDecimal) : "unavailable") + " " +feeTypeBtn.getText() + " required");
                return;
            }

            if(!NoteConstants.addFeeAmountToDataObject(feePriceAmount, ErgoConstants.MIN_NANO_ERGS, sendObject)){
                addSendBox();
                showError("Babblefees not supported at this time");
                return;
            }
                
            ErgoWalletAmountSendBox ergoSendBox = (ErgoWalletAmountSendBox) m_amountSendBoxes.getAmountBox(ErgoCurrency.TOKEN_ID);

            if(!ergoSendBox.isSufficientBalance()){
                addSendBox();
                showError("Insufficient Ergo balance");
                return;
            }

            PriceAmount ergoPriceAmount = ergoSendBox.getSendAmount();
            long nanoErgs = ergoPriceAmount.getLongAmount();
            
            NoteConstants.addNanoErgAmountToDataObject(ergoPriceAmount.getLongAmount(), sendObject);


            JsonArray sendAssets = new JsonArray();

            AmountBoxInterface[] amountBoxAray =  m_amountSendBoxes.getAmountBoxArray();
            
            for(int i = 0; i < amountBoxAray.length ;i++ ){
                AmountBoxInterface amountBox =amountBoxAray[i];
                if(amountBox instanceof ErgoWalletAmountSendTokenBox){
                    ErgoWalletAmountSendTokenBox sendBox = (ErgoWalletAmountSendTokenBox) amountBox;
                    PriceAmount sendAmount = sendBox.getSendAmount();
                    if(sendAmount!= null){
                        if(sendBox.isInsufficientBalance()){
                            addSendBox();
                            showError("Insufficient " + sendBox.getCurrency().getName() +" balance");
                            return;
                        }
                        if(!sendAmount.getTokenId().equals(ErgoCurrency.TOKEN_ID)){
                            sendAssets.add(sendAmount.getAmountObject());
                        }
                    }
                }
                
            }
    
            if(nanoErgs == 0 && sendAssets.size() == 0){
                addSendBox();
                showError("Enter assets to send");
                return;
            }

            if(nanoErgs < 39600){
                addSendBox();
                showError("Transactions require a minimum of " + PriceAmount.calculateLongToBigDecimal(39600, ErgoCurrency.DECIMALS).toPlainString() + " ERG");
                return;
            }

            sendObject.add("assets", sendAssets);

            NoteConstants.addNetworkTypeToDataObject(m_walletControl.getNetworkType(), sendObject);

            m_walletControl.sendNoteData("sendAssets", sendObject, (onComplete)->{
                Object sourceObject = onComplete.getSource().getValue();
                if(sourceObject != null && sourceObject instanceof JsonObject){
                    JsonObject receiptJson = (JsonObject) sourceObject;
                    JsonElement txIdElement = receiptJson.get("txId");
                    JsonElement resultElement = receiptJson.get("result");
                    

                    boolean isComplete = txIdElement != null && !txIdElement.isJsonNull();
                    String resultString = resultElement != null ? resultElement.getAsString() : isComplete ? "Complete" : "Failed";
                    receiptJson.addProperty("result", resultString);

                    String id = isComplete ? "tx_" + txIdElement.getAsString() : "err_" + FriendlyId.createFriendlyId();
                    
                    receiptJson.add("info", sendObject);

                    showReceipt(id, isComplete ? NoteConstants.SUCCESS : NoteConstants.ERROR, receiptJson);
                }
            }, (onError)->{
                Throwable throwable = onError.getSource().getException();
                String errorString = throwable.getMessage();

                String errTxId = "inc_" + FriendlyId.createFriendlyId();
                
                JsonObject receiptJson = new JsonObject();
                receiptJson.addProperty("result","Incomplete");
                receiptJson.addProperty("description", errorString.equals("") ? throwable.toString() : errorString);
                receiptJson.addProperty("timeStamp", System.currentTimeMillis());
                receiptJson.add("info", sendObject);
                showReceipt(errTxId, NoteConstants.ERROR, receiptJson);

            });
            
        });

 
    }

    private void addSendBox(){
        m_sendBodyContentBox.getChildren().clear();
        m_sendBodyContentBox.getChildren().add(m_sendBodyBox);
    }

    private void resetSend(){
        m_sendBodyContentBox.getChildren().clear();
        m_feesField.setText(ErgoConstants.MIN_NETWORK_FEE + "");
        m_sendToAddress.addressInformationProperty().set(new AddressInformation(""));
        m_amountSendBoxes.reset();
        m_sendBodyContentBox.getChildren().add(m_sendBodyBox);
    };
    
    private void showReceipt(String id, int code, JsonObject receiptJson){
    
        Label sendReceiptText = new Label("Send Receipt");
        sendReceiptText.setFont(Stages.txtFont);
        
        HBox sendReceiptTextBox = new HBox(sendReceiptText);
        sendReceiptTextBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(sendReceiptTextBox, Priority.ALWAYS);
        sendReceiptTextBox.setPadding(new Insets(10, 0,2,5));


        Region sendReceiptHBar = new Region();
        sendReceiptHBar.setPrefWidth(400);
        sendReceiptHBar.setPrefHeight(2);
        sendReceiptHBar.setMinHeight(2);
        sendReceiptHBar.setId("hGradient");
    
        HBox sendReceiptHBarGBox = new HBox(sendReceiptHBar);
        sendReceiptHBarGBox.setAlignment(Pos.CENTER);
        sendReceiptHBarGBox.setPadding(new Insets(0,0,10,0));

        JsonParametersBox sendReceiptJsonBox = new JsonParametersBox((JsonObject) null, Stages.COL_WIDTH);
        HBox.setHgrow(sendReceiptJsonBox, Priority.ALWAYS);
        sendReceiptJsonBox.setPadding(new Insets(2,10,0,10));
        sendReceiptJsonBox.update(receiptJson);



        Button exportBtn = new Button("🖫 Export… (*.json)");
        exportBtn.setOnAction(onSave->{
            ExtensionFilter txtFilter = new FileChooser.ExtensionFilter("JSON (application/json)", "*.json");
            FileChooser saveChooser = new FileChooser();
            saveChooser.setTitle("🖫 Export JSON…");
            saveChooser.getExtensionFilters().addAll(txtFilter);
            saveChooser.setSelectedExtensionFilter(txtFilter);
            saveChooser.setInitialFileName(id + ".json");
            File saveFile = saveChooser.showSaveDialog(m_appStage);
            if(saveFile != null){
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                
                try {
                    Files.writeString(saveFile.toPath(), gson.toJson(receiptJson));
                } catch (IOException e1) {
                    Alert alert = new Alert(AlertType.NONE, e1.toString(), ButtonType.OK);
                    alert.setTitle("Error");
                    alert.setHeaderText("Error");
                    alert.initOwner(m_appStage);
                    alert.show();
                }
            }
        });

        HBox exportBtnBox = new HBox(exportBtn);
        exportBtnBox.setAlignment(Pos.CENTER_RIGHT);
        exportBtnBox.setPadding(new Insets(15,15,15,0));

        Region receiptBottomHBar = new Region();
        receiptBottomHBar.setPrefWidth(400);
        receiptBottomHBar.setPrefHeight(2);
        receiptBottomHBar.setMinHeight(2);
        receiptBottomHBar.setId("hGradient");

        HBox receiptBottomGBox = new HBox(receiptBottomHBar);
        receiptBottomGBox.setAlignment(Pos.CENTER);
        receiptBottomGBox.setPadding(new Insets(10,0,0,0));


        Button receiptBottomOkBtn = new Button("Ok");
        receiptBottomOkBtn.setOnAction(onOk->{
            if(code == NoteConstants.SUCCESS){
                resetSend();
            }else{
                addSendBox();
            }
        });


        HBox receiptBottomOkBox = new HBox(receiptBottomOkBtn);
        receiptBottomOkBox.setAlignment(Pos.CENTER);
        receiptBottomOkBox.setPadding(new Insets(20, 0, 0, 0));


        VBox receiptContentBox = new VBox(sendReceiptTextBox,sendReceiptHBarGBox, sendReceiptJsonBox, exportBtnBox, receiptBottomGBox, receiptBottomOkBox);

        m_sendBodyContentBox.getChildren().clear();
        m_sendBodyContentBox.getChildren().add(receiptContentBox);
            
        
    }
    public void checkFeeField(){

        String feeString = m_feesField.getText();
        if(Utils.isTextZero(feeString)){
            m_feesField.setText(ErgoConstants.MIN_NETWORK_FEE + "");
        }else{
            BigDecimal fee = new BigDecimal(feeString);
            if(fee.compareTo(ErgoConstants.MIN_NETWORK_FEE) == -1){
                m_feesField.setText(ErgoConstants.MIN_NETWORK_FEE + "");
            }
        }
    
    }

    public void showError(String msg){
        
        double stringWidth =  Utils.computeTextWidth(Stages.txtFont, msg);;

        Point2D p = m_sendBtn.localToScene(0.0, 0.0);
        m_errTip.setText(msg);
        m_errTip.show(m_sendBtn,
                p.getX() + m_sendBtn.getScene().getX()
                        + m_sendBtn.getScene().getWindow().getX() - (stringWidth/2),
                (p.getY() + m_sendBtn.getScene().getY()
                        + m_sendBtn.getScene().getWindow().getY()) - 40);
        PauseTransition pt = new PauseTransition(Duration.millis(5000));
        pt.setOnFinished(ptE -> {
            m_errTip.hide();
        });
        pt.play();
    }

    
}
