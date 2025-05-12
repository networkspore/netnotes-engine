package io.netnotes.engine.apps.ergoFileWallet;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.satergo.Wallet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.networks.ergo.AddressInformation;
import io.netnotes.engine.networks.ergo.ErgoConstants;
import io.netnotes.engine.networks.ergo.ErgoInputData;
import io.netnotes.engine.networks.ergo.ErgoNetworkUrl;
import io.netnotes.engine.networks.ergo.ErgoTransactionData;
import io.netnotes.engine.networks.ergo.ErgoTransactionData.OutputData;
import io.netnotes.engine.ErgoMarketControl;
import io.netnotes.engine.JsonParametersBox;
import io.netnotes.engine.NamedNodeUrl;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.NoteMsgInterface;
import io.netnotes.engine.PriceAmount;
import io.netnotes.engine.ResizeHelper;
import io.netnotes.engine.Stages;
import io.netnotes.engine.Utils;
import io.netnotes.friendly_id.FriendlyId;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.ErgoToken;
import org.ergoplatform.sdk.SecretString;
public class AddressesData {

   
    
    public final static NetworkInformation NOMARKET = new NetworkInformation("null", "(disabled)","/assets/bar-chart-150.png", "/assets/bar-chart-30.png", "No market selected" );

    private final NetworkType m_networkType;

    private ErgoWalletData m_walletData;

    private ArrayList<AddressData> m_addressDataList;

    private ChangeListener<NoteInterface> m_selectedMarketChanged = null;
    private ChangeListener<NoteInterface> m_selectedTokenMarketChanged = null;
    
    public final static int ADDRESS_IMG_HEIGHT = 40;
    public final static int ADDRESS_IMG_WIDTH = 350;
 
    private SimpleObjectProperty<LocalDateTime> m_lastUpdated = new SimpleObjectProperty<>(LocalDateTime.now());

    private Stage m_promptStage = null;

    private ScheduledFuture<?> m_scheduledFuture = null;

    private NoteMsgInterface m_marketMsgInterface = null;
    private NoteMsgInterface m_tokenMarketMsgInterface = null;

    private long m_balanceTimestamp = 0;
    private String m_walletId;

    private ErgoMarketControl m_marketControl = null;

    public AddressesData(String id,  ArrayList<AddressData> addressDataList, ErgoWalletData walletData, NetworkType networkType) {

        m_walletId = id;
        m_walletData = walletData;
        m_networkType = networkType;
       
        m_addressDataList = addressDataList;
        
     
        for(AddressData addressData : m_addressDataList){
            addressData.setAddressesData(AddressesData.this);
        }

        m_marketControl = new ErgoMarketControl(id, getLocationId(), getNetworksData());
       
        start();
        
    }

    public NetworkType getNetworkType(){
        return m_networkType;
    }


    public String getWalletId(){
        return m_walletId;
    }

    public static ArrayList<PriceAmount> getSendAssetsList(JsonObject jsonObject, NetworkType networkType){
        ArrayList<PriceAmount> assetsList = new ArrayList<>();
        for(Map.Entry<String, JsonElement> entry : jsonObject.entrySet()){
            JsonElement element = entry.getValue();
            if(!element.isJsonNull() && element.isJsonObject()){
                JsonObject assetJson = element.getAsJsonObject();
                assetsList.add(PriceAmount.getByAmountObject(assetJson, networkType));
            }
        }
        return assetsList;
    }

    public JsonObject getBalance(JsonObject note){

        JsonElement addressElement = note.get("address");
        String address = addressElement != null && addressElement.isJsonPrimitive() ? addressElement.getAsString() : null;
        
        AddressData addressData = address!= null ? getAddressData(address) : null;
            
        if(addressData != null){
           
            return addressData.getBalance();
        }

        return null;
    
        
    }


    public Future<?> getTransactionViews(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement addressElement = note.get("address");
        String address = addressElement != null && addressElement.isJsonPrimitive() ? addressElement.getAsString() : null;
        
        AddressData addressData = address!= null ? getAddressData(address) : null;
            
        if(addressData != null){
           
            return addressData.getTransactionViews(note, onSucceeded, onFailed);
        }

        return null;
    }

    public Future<?> getTransactions(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement addressElement = note.get("address");
        String address = addressElement != null && addressElement.isJsonPrimitive() ? addressElement.getAsString() : null;
        
        AddressData addressData = address!= null ? getAddressData(address) : null;
            
        if(addressData != null){
           
            return addressData.getTransactions(note, onSucceeded, onFailed);
        }

        return null;
    }

    public Future<?> updateAddressBoxInfo(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement dataElement = note.get("data");
        if(dataElement != null && dataElement.isJsonObject()){
            JsonObject dataObject = dataElement.getAsJsonObject();
            JsonElement addressElement = dataObject.get("address");
            String address = addressElement != null && addressElement.isJsonPrimitive() ? addressElement.getAsString() : null;
            
            AddressData addressData = address!= null ? getAddressData(address) : null;
                
            if(addressData != null){
            
                return addressData.updateBoxInfo(dataObject, onSucceeded, onFailed);
            }
        }
        return null;
    }


    public ExecutorService getExecService(){
        return m_walletData.getNetworksData().getExecService();
    }

    public void viewWalletMnemonic(String locationString, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){

        JsonObject authObj = new JsonObject();

        authObj.addProperty("timeStamp", System.currentTimeMillis());

        String title = "Wallet - View Mnemonic - Signature Required";
    
        Stage txStage = new Stage();
        txStage.getIcons().add(Stages.logo);
        txStage.initStyle(StageStyle.UNDECORATED);
        txStage.setTitle(title);

        PasswordField passwordField = new PasswordField();
        Button closeBtn = new Button();

        Scene passwordScene = Stages.getAuthorizationScene(txStage,title,closeBtn, passwordField, authObj, locationString, rowHeight, lblCol);

        txStage.setScene(passwordScene);

        passwordField.setOnAction(e -> {
            if(passwordField.getText().length() > 0){
                SecretString pass = SecretString.create(passwordField.getText());
                passwordField.setText("");
                
                Button cancelBtn = new Button("Cancel");
                cancelBtn.setId("iconBtnSelected");
                Label progressLabel = new Label(m_walletData.isTxAvailable() ? "Opening wallet..." : "Waiting for wallet access...");
                
                Scene waitingScene = Stages.getWaitngScene(progressLabel, cancelBtn, txStage);
                txStage.setScene(waitingScene);
                txStage.centerOnScreen();

                Future<?> walletFuture = m_walletData.startTx(pass, m_walletData.getWalletFile(), getExecService(), onWalletLoaded->{
                    
                    cancelBtn.setDisable(true);
                    cancelBtn.setId("iconBtn");
                    Object walletObject = onWalletLoaded.getSource().getValue();
                    if(walletObject != null && walletObject instanceof Wallet){
                        
                        Wallet wallet = (Wallet) walletObject;
                        try{
                            wallet.key().viewWalletMnemonic(pass);
                            Utils.returnObject(NoteConstants.getJsonObject("result", "success"), getExecService(), onSucceeded);

                        }catch(Exception passFailed){
                            Utils.returnException(passFailed, getExecService(), onFailed);
                        }
                    
                        
                        m_walletData.endTx();      
                        txStage.close();
                    }else{
                        m_walletData.endTx();
                        txStage.setScene(passwordScene);
                        txStage.centerOnScreen();
                    }
                }, onLoadFailed->{
                    Throwable throwable = onLoadFailed.getSource().getException();

                    if(throwable != null){
                        if(!(throwable instanceof InterruptedException)){
                            m_walletData.endTx();
                        }
                        if(throwable instanceof NoSuchFileException){

                            Alert noFileAlert = new Alert(AlertType.ERROR, "Wallet file does not exist, or has been moved.", ButtonType.OK);
                            noFileAlert.setHeaderText("Error");
                            noFileAlert.setTitle("Error: File not found");
                            noFileAlert.show();
                            Utils.returnException((Exception) throwable, getExecService(), onFailed);
                            txStage.close();
                        }else{
                            txStage.setScene(passwordScene);
                            txStage.centerOnScreen();
                        }
                    }else{
                        m_walletData.endTx();

                        Alert unavailableAlert = new Alert(AlertType.ERROR, "Wallet Unavailable", ButtonType.OK);
                        unavailableAlert.setHeaderText("Error");
                        unavailableAlert.setTitle("Error: Wallet Unavailable");
                        unavailableAlert.show();

                        Utils.returnException("Wallet Unavailable", getExecService(), onFailed);
                        txStage.close();
                    }

                });

                cancelBtn.setOnAction(onCancel->{
                    walletFuture.cancel(true);
                });
            }
        });

        
        Runnable sendCanceledJson =()->{
      
            passwordField.setOnAction(null);
            Utils.returnException("Transaction Canceled", getExecService(), onFailed);
            txStage.close();
        };

        closeBtn.setOnAction(e -> sendCanceledJson.run());


        txStage.setOnCloseRequest(e->sendCanceledJson.run());

        passwordScene.focusOwnerProperty().addListener((obs, oldval, newVal) -> {
            if (newVal != null && !(newVal instanceof PasswordField)) {
                Platform.runLater(() -> passwordField.requestFocus());
            }
        });
        
        txStage.show();

        Platform.runLater(()->passwordField.requestFocus());
        
        ResizeHelper.addResizeListener(txStage, 400, 600, Double.MAX_VALUE, Double.MAX_VALUE);

    }

    
    private int lblCol = 170;
    private int rowHeight = 22;

    public Future<?> sendAssets(JsonObject note, String locationString, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        
        JsonElement dataElement = note.get("data");

    

        if(dataElement != null && dataElement.isJsonObject())
        {
            JsonObject dataObject = dataElement.getAsJsonObject();

            JsonElement recipientElement = dataObject.get("recipient");

            JsonObject recipientObject = recipientElement != null && recipientElement.isJsonObject() ? recipientElement.getAsJsonObject() : null;

            if(recipientObject == null){
                return Utils.returnException("No recipient provided", getExecService(), onFailed);
            }

            JsonElement recipientAddressElement = recipientObject.get("address");

            AddressInformation recipientAddressInfo = recipientAddressElement != null && !recipientAddressElement.isJsonNull() ? new AddressInformation(recipientAddressElement.getAsString()) : null;
            
            if(recipientAddressInfo == null){
                return Utils.returnException("No recipient address provided", getExecService(), onFailed);
            }

            if(recipientAddressInfo.getAddress() == null){
                return Utils.returnException("Invalid recipient address", getExecService(), onFailed);
            }

            if(!NoteConstants.checkNetworkType(dataObject, m_networkType)){
                return Utils.returnException("Network type must be " + m_networkType.toString(), getExecService(), onFailed);
            }

            JsonObject walletAddressObject = NoteConstants.getAddressObjectFromDataObject("senderAddress", dataObject);

            String walletAddress = NoteConstants.getAddressFromObject(walletAddressObject);

            if(walletAddress == null){
                return Utils.returnException("No wallet address provided", getExecService(), onFailed);
            }

            AddressData addressData = getAddressData(walletAddress);

            if(addressData == null){
                return Utils.returnException("Address not found in this wallet", getExecService(), onFailed);
            }
            ArrayList<PriceAmount> balanceList = NoteConstants.getBalanceList(addressData.getBalance(),true, m_networkType);


            long feeAmountNanoErgs = NoteConstants.getFeeAmountFromDataObject(dataObject);
            
            if(feeAmountNanoErgs == -1){
                return Utils.returnException("No fee provided", getExecService(), onFailed);
            }

            if(feeAmountNanoErgs < ErgoConstants.MIN_NANO_ERGS){
                return Utils.returnException("Minimum fee of "+ErgoConstants.MIN_NETWORK_FEE+" Erg required ("+ErgoConstants.MIN_NANO_ERGS+" nanoErg)", getExecService(), onFailed);
            }

            long amountToSpendNanoErgs = NoteConstants.getErgAmountFromDataObject(dataObject);
          
            

            PriceAmount[] amountsArray = NoteConstants.getAmountsFromDataObject(dataObject);
            
            for(PriceAmount sendAmount : amountsArray){
                PriceAmount balanceAmount = NoteConstants.getPriceAmountFromList(balanceList, sendAmount.getTokenId().toString());
                if(sendAmount.getLongAmount() > balanceAmount.getLongAmount()){
                    return Utils.returnException("Insufficent " + balanceAmount.getCurrency().getName(), getExecService(), onFailed);
                }
            }
            
            
            

            NamedNodeUrl namedNodeUrl = NoteConstants.getNamedNodeUrlFromDataObject(dataObject);

            if(namedNodeUrl == null){
                return Utils.returnException("Node unavailable", getExecService(), onFailed);
            }

            if(namedNodeUrl.getUrlString() == null){
                return Utils.returnException("No node URL provided", getExecService(), onFailed);
            }

            String nodeUrl = namedNodeUrl.getUrlString();
            String nodeApiKey = namedNodeUrl.getApiKey();

            ErgoNetworkUrl explorerNetworkUrl = NoteConstants.getExplorerUrl(dataObject);

            if(explorerNetworkUrl == null){
                return Utils.returnException("Explorer url not provided", getExecService(), onFailed);
            }

            String explorerUrl = explorerNetworkUrl.getUrlString();

            String title = "Wallet - Send Assets - Signature Required";
    
            Stage txStage = new Stage();
            txStage.getIcons().add(Stages.logo);
            txStage.initStyle(StageStyle.UNDECORATED);
            txStage.setTitle(title);

            PasswordField passwordField = new PasswordField();
            Button closeBtn = new Button();

            Scene passwordScene = Stages.getAuthorizationScene(txStage,title,closeBtn, passwordField, dataObject, locationString, rowHeight, lblCol);

            txStage.setScene(passwordScene);

            passwordField.setOnAction(e -> {
                if(passwordField.getText().length() >0){
                    SecretString pass = SecretString.create(passwordField.getText());
                    passwordField.setText("");
                    
                    Button cancelBtn = new Button("Cancel");
                    cancelBtn.setId("iconBtnSelected");
                    Label progressLabel = new Label(m_walletData.isTxAvailable() ? "Opening wallet..." : "Waiting for wallet access...");
                    
                    Scene waitingScene = Stages.getWaitngScene(progressLabel, cancelBtn, txStage);
                    txStage.setScene(waitingScene);
                    txStage.centerOnScreen();

                    Future<?> walletFuture = m_walletData.startTx(pass, m_walletData.getWalletFile(),getExecService(), onWalletLoaded->{
                        
                        cancelBtn.setDisable(true);
                        cancelBtn.setId("iconBtn");
                        Object walletObject = onWalletLoaded.getSource().getValue();
                        if(walletObject != null && walletObject instanceof Wallet){
                            progressLabel.setText("Executing transaction...");
                            addressData.sendAssets(
                                getExecService(), 
                                (Wallet) walletObject, 
                                nodeUrl, 
                                nodeApiKey, 
                                explorerUrl, 
                                m_networkType, 
                                recipientAddressInfo, 
                                amountToSpendNanoErgs, 
                                feeAmountNanoErgs, 
                                amountsArray, 
                                pass,
                                (onSent)->{
                                
                                    Utils.returnObject(onSent.getSource().getValue(), getExecService(), onSucceeded);
                                    txStage.close();
                                    m_walletData.endTx();
                                }, 
                                (onSendFailed)->{                     
                                
                                    Object sourceException = onSendFailed.getSource().getException();
                                    Exception exception = sourceException instanceof Exception ? (Exception) sourceException : null;
                                    if(exception != null){
                                        Utils.returnException(exception , getExecService(), onFailed);
                                    }else{
                                        String msg = sourceException == null ? "Transaction terminated unexpectedly" : onSendFailed.getSource().getException().toString(); 
                                        Utils.returnException(msg, getExecService(), onFailed);
                                    }
                                    txStage.close();
                                    m_walletData.endTx();
                                }
                            );
                        }else{
                            m_walletData.endTx();
                            txStage.setScene(passwordScene);
                            txStage.centerOnScreen();
                        }
                    }, onLoadFailed->{
                        Throwable throwable = onLoadFailed.getSource().getException();

                        if(throwable != null){
                            if(!(throwable instanceof InterruptedException)){
                                m_walletData.endTx();
                            }
                            if(throwable instanceof NoSuchFileException){

                                Alert noFileAlert = new Alert(AlertType.ERROR, "Wallet file does not exist, or has been moved.", ButtonType.OK);
                                noFileAlert.setHeaderText("Error");
                                noFileAlert.setTitle("Error: File not found");
                                noFileAlert.show();
                                Utils.returnException((Exception) throwable, getExecService(), onFailed);
                                txStage.close();
                            }else{
                                txStage.setScene(passwordScene);
                                txStage.centerOnScreen();
                            }
                        }else{
                            m_walletData.endTx();

                            Alert unavailableAlert = new Alert(AlertType.ERROR, "Transaction Unavailable", ButtonType.OK);
                            unavailableAlert.setHeaderText("Error");
                            unavailableAlert.setTitle("Error: Transaction Unavailable");
                            unavailableAlert.show();

                            Utils.returnException("Transaction Unavailable", getExecService(), onFailed);
                            txStage.close();
                        }

                    });

                    cancelBtn.setOnAction(onCancel->{
                        walletFuture.cancel(true);
                    });
                }
            });

            
            Runnable sendCanceledJson =()->{
          
                passwordField.setOnAction(null);
                Utils.returnException("Transaction Canceled", getExecService(), onFailed);
                txStage.close();
            };

            closeBtn.setOnAction(e -> sendCanceledJson.run());


            txStage.setOnCloseRequest(e->sendCanceledJson.run());

            passwordScene.focusOwnerProperty().addListener((obs, oldval, newVal) -> {
                if (newVal != null && !(newVal instanceof PasswordField)) {
                    Platform.runLater(() -> passwordField.requestFocus());
                }
            });
            
            txStage.show();
    
            Platform.runLater(()->passwordField.requestFocus());
            
            ResizeHelper.addResizeListener(txStage, 400, 600, Double.MAX_VALUE, Double.MAX_VALUE);

            
        }
        return null;

    }



  

    public Future<?> reclaimBox(JsonObject note,String locationId, String locationString, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement dataElement = note.get("data");

        if(dataElement != null && dataElement.isJsonObject())
        {
            JsonObject dataObject = dataElement.getAsJsonObject();

            JsonElement addressElement = dataObject.get("address");
            JsonElement parentTxIdElement = dataObject.get("txId");
            JsonElement boxIdElement = dataObject.get("boxId");

            String addressString = addressElement != null && !addressElement.isJsonNull() ? addressElement.getAsString() : null;

            if(addressString == null){
                return Utils.returnException("address required", getExecService(), onFailed);
            }

            String boxId = boxIdElement != null && !boxIdElement.isJsonNull() ? boxIdElement.getAsString() : null;
            
            if(boxId == null){
                return Utils.returnException("boxId required", getExecService(), onFailed);
            }

            String parentTxId = parentTxIdElement != null && !parentTxIdElement.isJsonNull() ? parentTxIdElement.getAsString() : null;
            if(parentTxId == null){
                return Utils.returnException("txId required", getExecService(), onFailed);
            }

            if(locationId == null){
                return Utils.returnException("locationId required", getExecService(), onFailed);
            }

            AddressData addressData = getAddressData(addressString);

            if(addressData == null){
                 return Utils.returnException("Address not found in this wallet", getExecService(), onFailed);
            }
          
            NamedNodeUrl namedNodeUrl = NoteConstants.getNamedNodeUrlFromDataObject(dataObject);

            if(namedNodeUrl == null){
                return Utils.returnException("Node unavailable", getExecService(), onFailed);
            }

            if(namedNodeUrl.getUrlString() == null){
                return Utils.returnException("No node URL provided", getExecService(), onFailed);
            }

            String nodeUrl = namedNodeUrl.getUrlString();
            String nodeApiKey = namedNodeUrl.getApiKey();

            ErgoNetworkUrl explorerNetworkUrl = NoteConstants.getExplorerUrl(dataObject);

            if(explorerNetworkUrl == null){
                return Utils.returnException("Explorer url not provided", getExecService(), onFailed);
            }

            String explorerUrl = explorerNetworkUrl.getUrlString();

            String title = "Wallet - Reclaim Box - Signature Required";
    
            Stage txStage = new Stage();
            txStage.getIcons().add(Stages.logo);
            txStage.initStyle(StageStyle.UNDECORATED);
            txStage.setTitle(title);

            PasswordField passwordField = new PasswordField();
            Button closeBtn = new Button();

            Scene passwordScene = Stages.getAuthorizationScene(txStage,title,closeBtn, passwordField, dataObject, locationString, rowHeight, lblCol);

            txStage.setScene(passwordScene);

            passwordField.setOnAction(e -> {
                if(passwordField.getText().length() > 0){
                    SecretString pass = SecretString.create( passwordField.getText());
                    passwordField.setText("");
                    
                    Button cancelBtn = new Button("Cancel");
                    cancelBtn.setId("iconBtnSelected");
                    Label progressLabel = new Label(m_walletData.isTxAvailable() ? "Opening wallet..." : "Waiting for wallet access...");
                    
                    Scene waitingScene = Stages.getWaitngScene(progressLabel, cancelBtn, txStage);
                    txStage.setScene(waitingScene);
                    txStage.centerOnScreen();

                    Future<?> walletFuture = m_walletData.startTx(pass, m_walletData.getWalletFile(),getExecService(), onWalletLoaded->{
                        
                        cancelBtn.setDisable(true);
                        cancelBtn.setId("iconBtn");
                        Object walletObject = onWalletLoaded.getSource().getValue();
                        if(walletObject != null && walletObject instanceof Wallet){
                            progressLabel.setText("Executing transaction...");
                            addressData.reclaimBox(
                                (Wallet) walletObject, 
                                nodeUrl, 
                                nodeApiKey, 
                                explorerUrl, 
                                m_networkType,
                                parentTxId,
                                boxId,
                                pass,
                                locationId,
                                (onSent)->{
                                    Utils.returnObject(onSent.getSource().getValue(), getExecService(), onSucceeded);
                                    txStage.close();
                                    m_walletData.endTx();
                                }, 
                                (onSendFailed)->{                     
                                
                                    Object sourceException = onSendFailed.getSource().getException();
                                    Exception exception = sourceException instanceof Exception ? (Exception) sourceException : null;
                                    if(exception != null){
                                        Utils.returnException(exception , getExecService(), onFailed);
                                    }else{
                                        String msg = sourceException == null ? "Transaction terminated unexpectedly" : onSendFailed.getSource().getException().toString(); 
                                        Utils.returnException(msg, getExecService(), onFailed);
                                    }
                                    txStage.close();
                                    m_walletData.endTx();
                                }
                            );
                        }else{
                            m_walletData.endTx();
                            txStage.setScene(passwordScene);
                            txStage.centerOnScreen();
                        }
                    }, onLoadFailed->{
                        Throwable throwable = onLoadFailed.getSource().getException();

                        if(throwable != null){
                            if(!(throwable instanceof InterruptedException)){
                                m_walletData.endTx();
                            }
                            if(throwable instanceof NoSuchFileException){

                                Alert noFileAlert = new Alert(AlertType.ERROR, "Wallet file does not exist, or has been moved.", ButtonType.OK);
                                noFileAlert.setHeaderText("Error");
                                noFileAlert.setTitle("Error: File not found");
                                noFileAlert.show();
                                Utils.returnException((Exception) throwable, getExecService(), onFailed);
                                txStage.close();
                            }else{
                                txStage.setScene(passwordScene);
                                txStage.centerOnScreen();
                            }
                        }else{
                            m_walletData.endTx();

                            Alert unavailableAlert = new Alert(AlertType.ERROR, "Transaction Unavailable", ButtonType.OK);
                            unavailableAlert.setHeaderText("Error");
                            unavailableAlert.setTitle("Error: Transaction Unavailable");
                            unavailableAlert.show();

                            Utils.returnException("Transaction Unavailable", getExecService(), onFailed);
                            txStage.close();
                        }

                    });

                    cancelBtn.setOnAction(onCancel->{
                        walletFuture.cancel(true);
                    });
                }
            });

            
            Runnable sendCanceledJson =()->{
          
                passwordField.setOnAction(null);
                Utils.returnException("Transaction Canceled", getExecService(), onFailed);
                txStage.close();
            };

            closeBtn.setOnAction(e -> sendCanceledJson.run());


            txStage.setOnCloseRequest(e->sendCanceledJson.run());

            passwordScene.focusOwnerProperty().addListener((obs, oldval, newVal) -> {
                if (newVal != null && !(newVal instanceof PasswordField)) {
                    Platform.runLater(() -> passwordField.requestFocus());
                }
            });
            
            txStage.show();
    
            Platform.runLater(()->passwordField.requestFocus());
            
            ResizeHelper.addResizeListener(txStage, 400, 600, Double.MAX_VALUE, Double.MAX_VALUE);

            
        }
        return null;
    }

   


    
    public Future<?> executeTransaction(JsonObject note, String locationString, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) throws Exception{
        
        JsonElement dataElement = note.get("data");
        int lblCol = 170;
        int rowHeight = 22;

        if(dataElement != null && dataElement.isJsonObject())
        {
            JsonObject txDataObject = dataElement.getAsJsonObject();

            if(txDataObject == null){
                throw new Exception("No Ergo output data provided");
            }


            ErgoTransactionData txData = new ErgoTransactionData(txDataObject, getNetworkType());
            if(txData.getErgoInputData().size() != 1){
                throw new Exception("Limited to currenly requiring one asset input");
            }
            
            if(txData.getFeeInputData() == null){
                throw new Exception("No fee input provided");
            }

            ErgoInputData[] assetInputs = txData.getAssetsInputData();
            
            for(ErgoInputData inputData : assetInputs){
                if(inputData.getWalletType().equals(NoteConstants.CURRENT_WALLET_FILE)){
                    AddressData addressData = getAddressData(inputData.getAddressString());

                    if(addressData == null){
                        throw new Exception("Input address not found in this wallet");
                    }

                    ArrayList<PriceAmount> balanceList = NoteConstants.getBalanceList(addressData.getBalance(), true, getNetworkType());
                    for(ErgoToken sendAmount : inputData.getTokens()){
                        PriceAmount balanceAmount = NoteConstants.getPriceAmountFromList(balanceList, sendAmount.getId().toString());
                        if(sendAmount.getValue() > balanceAmount.getLongAmount()){
                            throw new Exception("Insufficent " + balanceAmount.getCurrency().getName());
                        }
                    }
                }
            }
            
            String title = "Wallet - Send Assets - Signature Required";
    
            Stage txStage = new Stage();
            txStage.getIcons().add(Stages.logo);
            txStage.initStyle(StageStyle.UNDECORATED);
            txStage.setTitle(title);

            PasswordField passwordField = new PasswordField();
            Button closeBtn = new Button();

            Scene passwordScene = Stages.getAuthorizationScene(txStage,title,closeBtn, passwordField, txDataObject, locationString, rowHeight, lblCol);

            txStage.setScene(passwordScene);

            passwordField.setOnAction(e -> {
                if(passwordField.getText().length() > 0){
                    SecretString pass = SecretString.create(passwordField.getText());
                    passwordField.setText("");
                    
                    Button cancelBtn = new Button("Cancel");
                    cancelBtn.setId("iconBtnSelected");
                    Label progressLabel = new Label(m_walletData.isTxAvailable() ? "Opening wallet..." : "Waiting for wallet access...");
                    
                    Scene waitingScene = Stages.getWaitngScene(progressLabel, cancelBtn, txStage);
                    txStage.setScene(waitingScene);
                    txStage.centerOnScreen();

                    Future<?> walletFuture = m_walletData.startTx(pass, m_walletData.getWalletFile(),getExecService(), onWalletLoaded->{
                        
                        cancelBtn.setDisable(true);
                        cancelBtn.setId("iconBtn");
                        Object walletObject = onWalletLoaded.getSource().getValue();
                        if(walletObject != null && walletObject instanceof Wallet){
                            progressLabel.setText("Executing transaction...");
                            executeTransaction(
                                getExecService(),
                                pass,
                                (Wallet) walletObject, 
                                txData,
                                (onSent)->{
                                
                                    Utils.returnObject(onSent.getSource().getValue(), getExecService(), onSucceeded);
                                    txStage.close();
                                    m_walletData.endTx();
                                }, 
                                (onSendFailed)->{                     
                                
                                    Object sourceException = onSendFailed.getSource().getException();
                                    Exception exception = sourceException instanceof Exception ? (Exception) sourceException : null;
                                    if(exception != null){
                                        Utils.returnException(exception , getExecService(), onFailed);
                                    }else{
                                        String msg = sourceException == null ? "Transaction terminated unexpectedly" : onSendFailed.getSource().getException().toString(); 
                                        Utils.returnException(msg, getExecService(), onFailed);
                                    }
                                    txStage.close();
                                    m_walletData.endTx();
                                }
                            );
                        }else{
                            m_walletData.endTx();
                            txStage.setScene(passwordScene);
                            txStage.centerOnScreen();
                        }
                    
                    }, onLoadFailed->{
                        Throwable throwable = onLoadFailed.getSource().getException();

                        if(throwable != null){
                            if(!(throwable instanceof InterruptedException)){
                                m_walletData.endTx();
                            }
                            if(throwable instanceof NoSuchFileException){

                                Alert noFileAlert = new Alert(AlertType.ERROR, "Wallet file does not exist, or has been moved.", ButtonType.OK);
                                noFileAlert.setHeaderText("Error");
                                noFileAlert.setTitle("Error: File not found");
                                noFileAlert.show();
                                Utils.returnException((Exception) throwable, getExecService(), onFailed);
                                txStage.close();
                            }else{
                                txStage.setScene(passwordScene);
                                txStage.centerOnScreen();
                            }
                        }else{
                            m_walletData.endTx();

                            Alert unavailableAlert = new Alert(AlertType.ERROR, "Transaction Unavailable", ButtonType.OK);
                            unavailableAlert.setHeaderText("Error");
                            unavailableAlert.setTitle("Error: Transaction Unavailable");
                            unavailableAlert.show();

                            Utils.returnException("Transaction Unavailable", getExecService(), onFailed);
                            txStage.close();
                        }

                    });

                    cancelBtn.setOnAction(onCancel->{
                        walletFuture.cancel(true);
                    });
                }
            });

            
            Runnable sendCanceledJson =()->{
          
                passwordField.setOnAction(null);
                Utils.returnException("Transaction Canceled", getExecService(), onFailed);
                txStage.close();
            };

            closeBtn.setOnAction(e -> sendCanceledJson.run());


            txStage.setOnCloseRequest(e->sendCanceledJson.run());

            passwordScene.focusOwnerProperty().addListener((obs, oldval, newVal) -> {
                if (newVal != null && !(newVal instanceof PasswordField)) {
                    Platform.runLater(() -> passwordField.requestFocus());
                }
            });
            
            txStage.show();
    
            Platform.runLater(()->passwordField.requestFocus());
            
            ResizeHelper.addResizeListener(txStage, 400, 600, Double.MAX_VALUE, Double.MAX_VALUE);

            
        }
        return null;

    }

    public Future<?> executeTransaction(
        ExecutorService execService, 
        SecretString pass,    
        Wallet wallet,  
        ErgoTransactionData txData,
        EventHandler<WorkerStateEvent> onSucceeded,
        EventHandler<WorkerStateEvent> onFailed)
    {
        try{
            txData.prepareContract(wallet);
        }catch(Exception e){
            return Utils.returnException(e, execService, onFailed);
        }

        Task<JsonObject> task = new Task<JsonObject>() {
            @Override
            public JsonObject call() throws Exception {

    
                
                NetworkType networkType = txData.getNetworkType();
                Address changeAddress = txData.getChangeAddress(wallet);
                String nodeUrl = txData.getNamedNodeUrl().getUrlString();
                String nodeApiKey = txData.getNamedNodeUrl().getApiKey();
                String explorerUrl = txData.getErgoExplorerUrl().getUrlString();
   
                ErgoInputData inputData = txData.getAssetsInputData()[0];
                
                ErgoClient ergoClient = RestApiErgoClient.create(nodeUrl, networkType, nodeApiKey, explorerUrl);

                List<Address> addresses = wallet.addressStream(networkType).toList();
          
                UnsignedTransaction unsignedTx = ergoClient.execute(ctx -> {
                    
                    List<InputBox> boxesToSpend =  BoxOperations.createForSenders(addresses, ctx)
                    .withAmountToSpend(inputData.getNanoErgs())
                    .withFeeAmount(inputData.getFeeNanoErgs())
                    .withTokensToSpend(List.of(inputData.getTokens()))
                    .loadTop();

                    UnsignedTransactionBuilder txBuilder = ctx.newTxBuilder();
                    int outBoxSize = txData.getOutputData().size();

                    OutBox[] outBoxes = new OutBox[outBoxSize];

                    for(int i = 0; i < outBoxSize ; i++){
                        OutputData outputData = txData.getOutputData().get(i);
                        outBoxes[i] = outputData.getOutBox(ctx, txBuilder.outBoxBuilder());
                        
                    }
                    
                    return txBuilder
                    .addInputs(boxesToSpend.toArray(new InputBox[0])).addOutputs(outBoxes)
                    .fee(inputData.getFeeNanoErgs())
                    .sendChangeTo(changeAddress)
                    .build();
                
                });

        
                String txId = null; /*unsignedTx != null ?  wallet.transact(ergoClient, ergoClient.execute(ctx -> {
                    try {
                        return wallet.key().signWithPassword(pass, ctx, unsignedTx, wallet.myAddresses.keySet());
                    } catch (WalletKey.Failure ex) {

                        return null;
                    }
                })) : null;*/


                if(txId != null){

                    JsonObject resultObject = new JsonObject();
                    resultObject.addProperty("result","Executed");
                    resultObject.addProperty("txId", txId);
                    resultObject.addProperty("timeStamp", System.currentTimeMillis());

                    try{
                        
                        BlockchainDataSource dataSource = ergoClient != null ? ergoClient.getDataSource() : null;
                        List<BlockHeader> blockHeaderList =  dataSource != null ? dataSource.getLastBlockHeaders(1, true) : null;
                        BlockHeader blockHeader = blockHeaderList != null && blockHeaderList.size() > 0 ? blockHeaderList.get(0)  : null;
                        
                        int blockHeight = blockHeader != null ? blockHeader.getHeight() : -1;
                        long timeStamp = blockHeader != null ? blockHeader.getTimestamp() : -1;

                        JsonObject networkInfoObject = new JsonObject();
                        networkInfoObject.addProperty("networkHeight", blockHeight);
                        networkInfoObject.addProperty("timeStamp", timeStamp);
                        resultObject.add("networkInfo", networkInfoObject);
                    }catch(Exception dataSourcException){
        
                    }
                    
                    return resultObject;
                }else{
                    throw new Exception("Transaction signing failed");
                }
                
            }
        };

        task.setOnSucceeded(onSucceeded);
        task.setOnFailed(onFailed);

        return execService.submit(task);
    }


   

    


    public JsonArray getAddressesJson(){
        JsonArray json = new JsonArray();
        
        for(AddressData addressData : m_addressDataList){
            json.add(addressData.getAddressJson());
        }
        return json;
    }



    public ScheduledExecutorService getSchedualedExecService(){
        return getNetworksData().getSchedualedExecService();
    }

    protected ErgoMarketControl getErgoMarketControl(){
        return  getErgoWalletsDataList().getErgoMarketControl();
    }
    
    private void start(){

        if(m_scheduledFuture == null || (m_scheduledFuture != null && (m_scheduledFuture.isCancelled() || m_scheduledFuture.isDone()))){
           
            getErgoMarketControl().addMarketConnection();
            getErgoMarketControl().addTokenMarketConnection();

            m_scheduledFuture = getSchedualedExecService().scheduleAtFixedRate(()->{
                update();
            },0, NoteConstants.POLLING_TIME, TimeUnit.MILLISECONDS);
       
        }
        //market
        /*
         * case ErgoConstants.MARKET_NETWORK:
                                switch(code){
                                
                                    case NoteConstants.LIST_DEFAULT_CHANGED:
                                        getDefaultMarkets();
                                    break;
                                
                                }
                            break;
            case ErgoConstants.TOKEN_MARKET_NETWORK:
                                switch(code){
                                    case NoteConstants.LIST_DEFAULT_CHANGED:
                                        getDefaultMarkets();
                                    break;
                                }
                            break;
         */
    }



    public void update(){


        for(int i = 0; i < m_addressDataList.size(); i++){
            AddressData addressData = m_addressDataList.get(i);

            addressData.updateBalance();
        }
       

    }

    public void stop(){
       
        if(m_scheduledFuture != null){
            m_scheduledFuture.cancel(false);
            m_scheduledFuture = null;
        }

        getErgoMarketControl().removeMarketConnection();
        getErgoMarketControl().removeTokenMarketConnection();
    }
    

    public SimpleObjectProperty<LocalDateTime> balanceUpdatedProperty(){
        return m_lastUpdated;
    }

    public ErgoWalletData getWalletData() {
        return m_walletData;
    }


    public void addAddress() {

        if (m_promptStage == null) {

            m_promptStage = new Stage();
            m_promptStage.initStyle(StageStyle.UNDECORATED);
            m_promptStage.getIcons().add(new Image("/assets/git-branch-outline-white-30.png"));
            m_promptStage.setTitle("Add Address - " + m_walletData.getName() + " - Ergo Wallets");

            TextField textField = new TextField();
            Button closeBtn = new Button();

            Stages.showGetTextInput("Address name", "Add Address", new Image("/assets/git-branch-outline-white-240.png"), textField, closeBtn, m_promptStage);
            closeBtn.setOnAction(e -> {
                m_promptStage.close();
                m_promptStage = null;
            });
            m_promptStage.setOnCloseRequest(e -> {
                closeBtn.fire();
            });
            textField.setOnKeyPressed(e -> {

              //  KeyCode keyCode = e.getCode();

                /* if (keyCode == KeyCode.ENTER) {
                    String addressName = textField.getText();
                    if (!addressName.equals("")) {
                        int nextAddressIndex = m_wallet.nextAddressIndex();
                        m_wallet.myAddresses.put(nextAddressIndex, addressName);

                        try {

                            Address address = m_wallet.publicAddress(m_networkType, nextAddressIndex);
                            AddressData addressData = new AddressData(addressName, nextAddressIndex, address,m_wallet, m_networkType, this);
                            addAddressData(addressData);
                          
                        } catch (Failure e1) {

                            Alert a = new Alert(AlertType.ERROR, e1.toString(), ButtonType.OK);
                            a.showAndWait();
                        }

                    }
                    closeBtn.fire();
                }*/
            });
        } else {
            if (m_promptStage.isIconified()) {
                m_promptStage.setIconified(false);
            } else {
                if(!m_promptStage.isShowing()){
                    m_promptStage.show();
                }else{
                    Platform.runLater(() -> m_promptStage.toBack());
                    Platform.runLater(() -> m_promptStage.toFront());
                }
                
            }
        }

    }
   






    


    public AddressData getAddressData(String address){
        if(address != null){
            for(AddressData addressData :  m_addressDataList){
            
                if(addressData.getAddressString().equals(address)){
                    return addressData;
                }
            }
        }
        return null;
    }



    public long getBalanceTimeStamp(){
        return m_balanceTimestamp;
    }



    public void shutdown() {
      
       

        stop();
         
     
    }



    public void connectToMarket(boolean connect, NoteInterface marketInterface ){
     
        if(connect && marketInterface != null){
       
            m_marketMsgInterface = new NoteMsgInterface() {
                private String m_msgId = FriendlyId.createFriendlyId();

                public String getId(){
                    return m_msgId;
                }

                public void sendMessage(int code, long timeStamp, String networkId, Number num){
                  
                 

                    m_walletData.sendMessage(code, timeStamp, ErgoWallets.ERGO_MARKET + ":" + networkId, num);
        
                      
                }
            
                public void sendMessage(int code, long timeStamp, String networkId, String msg){
                    m_walletData.sendMessage(code, timeStamp,  networkId, msg);
                }

            };
    
            marketInterface.addMsgListener(m_marketMsgInterface);
            
        }else{
            
            if(m_marketMsgInterface != null && marketInterface != null){
              
                marketInterface.removeMsgListener(m_marketMsgInterface);  
            }
            m_marketMsgInterface = null;
        }
    }

    public void connectToTokenMarket(boolean connect, NoteInterface tokenMarketInterface ){
     
        if(connect && tokenMarketInterface != null){

            m_tokenMarketMsgInterface = new NoteMsgInterface() {
                private String m_msgId = FriendlyId.createFriendlyId();

                public String getId(){
                    return m_msgId;
                }

                public void sendMessage(int code, long timeStamp, String id, Number num){
                    
                    m_walletData.sendMessage(code, timeStamp, ErgoWallets.TOKENM_MARKET + ":"+ id, num);
                        
                }
            
                public void sendMessage(int code, long timeStamp, String networkId, String msg){
                    m_walletData.sendMessage(code, timeStamp, networkId, msg);
                }

        

            };
    
            tokenMarketInterface.addMsgListener(m_tokenMarketMsgInterface);
            
        }else{

            if(m_tokenMarketMsgInterface != null && tokenMarketInterface != null){
                
                tokenMarketInterface.removeMsgListener(m_tokenMarketMsgInterface);  
            }
            m_tokenMarketMsgInterface = null;
        }
    }




    public String getLocationId(){
    
        return getWalletData().getErgoWalletsDataList().getLocationId();
    }

    public NetworksData getNetworksData(){
        return getWalletData().getNetworksData();
    }

    public ErgoWalletDataList getErgoWalletsDataList() {
        return getWalletData().getErgoWalletsDataList();
    }

}
