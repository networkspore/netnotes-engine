package io.netnotes.engine.apps.ergoWallets;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netnotes.engine.HashData;
import io.netnotes.engine.JsonParametersBox;
import io.netnotes.engine.Network;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.ResizeHelper;
import io.netnotes.engine.Stages;
import io.netnotes.engine.Utils;
import io.netnotes.engine.apps.AppConstants;
import io.netnotes.engine.networks.ergo.ErgoNetworkControl;
import io.netnotes.friendly_id.FriendlyId;

import org.ergoplatform.appkit.Address;
import org.ergoplatform.appkit.NetworkType;
import com.satergo.Wallet;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.ergoplatform.sdk.SecretString;

public class ErgoWalletData  {

    private Semaphore m_txSemaphore = new Semaphore(1); 

    private File m_walletFile = null;
      
    private AddressesData m_addressesData = null;

    private NetworkType m_networkType = NetworkType.MAINNET;

    private ErgoWalletDataList m_ergoWalletsDataList;
    private String m_name;
    private final String m_id;
    // private ErgoWallet m_ergoWallet;
    public ErgoWalletData(String id, String name, File walletFile, NetworkType networkType, ErgoWalletDataList ergoWalletDataList) {
        m_name = name;
        m_id = id;
        m_walletFile = walletFile;
        m_networkType = networkType;
        m_ergoWalletsDataList = ergoWalletDataList;
 

    }

    public String getId(){
        return m_id;
    }
    

    public String getName(){
        return m_name;
    }


    public ErgoWalletDataList getErgoWalletsDataList(){
        return m_ergoWalletsDataList;
    }

    protected String getLocationId(){
        return getErgoWalletsDataList().getLocationId();
    }

    public NetworksData getNetworksData(){
        return getErgoWalletsDataList().getNetworksData();
    }

    public ErgoNetworkControl getErgoNetworkControl(){
        return getErgoWalletsDataList().getErgoNetworkControl();
    }


    public Semaphore getTxSemaphore(){
        return m_txSemaphore;
    }

    public ErgoWallets getErgoWallets(){
        return m_ergoWalletsDataList.getErgoWallets();
    }


    public Image getSmallAppIcon(){
        return getErgoWallets().getSmallAppIcon();
    }

    public File getWalletFile(){
        return m_walletFile;
    }

    public boolean isTxAvailable(){
        return m_txSemaphore.availablePermits() == 1;
    }

    public void startTx() throws InterruptedException{
        m_txSemaphore.acquire();
    }

    public void endTx(){
        m_txSemaphore.release();
    }

    public Future<?> startTx(SecretString pass, File walletFile, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {
                m_txSemaphore.acquire();
                return Wallet.load(walletFile.toPath(), pass);
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

       

    

    

    public void save(){
        m_ergoWalletsDataList.save();
    }

    private Future<?> updateFile(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {

        JsonElement fileElement = note.get("file");


        String fileString = fileElement != null && fileElement.isJsonPrimitive() ? fileElement.getAsString() : null;
        File walletFile = fileString != null && fileString.length() > 1 ? new File(fileString) : null;

        if(walletFile != null && walletFile.isFile()){
            
            if(!walletFile.getAbsolutePath().equals(m_walletFile.getAbsolutePath())){
             
                
                
                Task<Object> task = new Task<Object>() {
                    @Override
                    public Object call() throws InterruptedException {
                        m_txSemaphore.acquire();
                        setWalletFile(walletFile);
                        save();
                        m_txSemaphore.release();
                        return getWalletJson();
                    }
                };
        
                task.setOnSucceeded(onSucceeded);
                task.setOnFailed(onFailed);
        
                return getExecService().submit(task);
            }else{
                return Utils.returnObject(getWalletJson(), getExecService(), onSucceeded) ;
            }   
            
        }else{
            return Utils.returnException("File is unavailable", getExecService(), onFailed);
        }
        
    
    }

    private void setWalletFile(File walletFile){
        
        m_walletFile = walletFile;
        save();
    }

    public File getWalleFile(){
        return m_walletFile;
      
    }


    private void updateName(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement namElement = note.get("name");
  
        String name = namElement != null && namElement.isJsonPrimitive() ? namElement.getAsString() : null;

        if(name != null && name.length() > 1){
            if(!name.equals(getName())){
                

                if(!getErgoWalletsDataList().containsName(name)){
                    m_name = name;
                    save();
                    Utils.returnObject(getWalletJson(), getExecService(), onSucceeded);
                }else{
                    Utils.returnException("Name in use", getExecService(), onFailed);
                }
            }else{
                Utils.returnObject(getWalletJson(), getExecService(), onSucceeded);
            }   
            
        }else{
            Utils.returnException("Invalid name", getExecService(), onFailed);
        }
    
     
    }

    private ExecutorService getExecService(){
        return getNetworksData().getExecService();
    }

    private Future<?> getAccessId(JsonObject note, NetworkInformation networkInformation, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement controlIdElement = note != null ? note.get("controlId") : null;
        String controlId = controlIdElement != null && !controlIdElement.isJsonNull() && controlIdElement.isJsonPrimitive() ? controlIdElement.getAsString() : null;

        if(controlId == null){
            return Utils.returnException(NoteConstants.STATUS_UNAVAILABLE, getExecService(), onFailed);
        }


        Semaphore activeThreadSemaphore = new Semaphore(0);
      
        int lblCol = 180;
        String authorizeString = "Authorize Wallet Access";
        String title = getName() + " - " + authorizeString;

        Stage passwordStage = new Stage();
        passwordStage.getIcons().add(Stages.logo);
        passwordStage.initStyle(StageStyle.UNDECORATED);
        passwordStage.setTitle(title);

        Button closeBtn = new Button();
       
        HBox titleBox = Stages.createTopBar(Stages.icon, title, closeBtn, passwordStage);

        ImageView btnImageView = new ImageView(Stages.logo);
        btnImageView.setPreserveRatio(true);
        btnImageView.setFitHeight(75);

        Label textField = new Label(authorizeString);
        textField.setFont(Stages.mainFont);
        textField.setPadding(new Insets(20,0,20,15));

        VBox imageBox = new VBox(btnImageView, textField);
        imageBox.setAlignment(Pos.CENTER);
        imageBox.setPadding(new Insets(10,0,10,0));

        JsonObject paramsObject = new JsonObject();
        paramsObject.addProperty("location", networkInformation.getNetworkName());
        paramsObject.addProperty("wallet", getName());
        paramsObject.addProperty("timeStamp", System.currentTimeMillis());
        
        JsonParametersBox walletInformationBox = new JsonParametersBox(paramsObject, lblCol);
        HBox.setHgrow(walletInformationBox, Priority.ALWAYS);
        walletInformationBox.setPadding(new Insets(0,0,0,10));

        Text passwordTxt = new Text("Enter password:");
        passwordTxt.setFill(Stages.txtColor);
        passwordTxt.setFont(Stages.txtFont);

        PasswordField passwordField = new PasswordField();
        passwordField.setFont(Stages.txtFont);
        passwordField.setId("passField");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        HBox passwordBox = new HBox(passwordTxt, passwordField);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        passwordBox.setPadding(new Insets(10, 10, 10, 10));

        VBox bodyBox = new VBox(walletInformationBox);
        HBox.setHgrow(bodyBox,Priority.ALWAYS);
        bodyBox.setPadding(new Insets(0,15, 0,0));

        VBox layoutVBox = new VBox(titleBox, imageBox, bodyBox, passwordBox);
        VBox.setVgrow(layoutVBox, Priority.ALWAYS);
    

        double defaultHeight = Stages.STAGE_HEIGHT + 60;
        double defaultWidth = Stages.STAGE_WIDTH + 100;

        Scene passwordScene = new Scene(layoutVBox, defaultWidth , defaultHeight);
        passwordScene.setFill(null);
        passwordScene.getStylesheets().add("/css/startWindow.css");
        passwordStage.setScene(passwordScene);


        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InterruptedException {
                activeThreadSemaphore.acquire();
                activeThreadSemaphore.release();
                return null;
            }
        };

        task.setOnFailed(onInterrupted->{
            passwordField.setOnAction(null);
            Utils.returnException("Canceled", getExecService(), onFailed);
            passwordStage.close();
        });

        Future<?> limitAccessFuture = getNetworksData().getExecService().submit(task);

                
        Runnable releaseAccess = ()->{
            passwordStage.close();
            activeThreadSemaphore.release();
        };

        passwordField.setOnAction(action -> {

            if(passwordField.getText().length() > 0){
                SecretString pass = new SecretString( passwordField.getText().toCharArray());    
                passwordField.setText("");

                Button cancelBtn = new Button("Cancel");
                cancelBtn.setId("iconBtnSelected");
                Label progressLabel = new Label(isTxAvailable() ? "Verifying..." : "Waiting for wallet access...");
                
                Scene waitingScene = Stages.getWaitngScene(progressLabel, cancelBtn, passwordStage);
                passwordStage.setScene(waitingScene);
                passwordStage.centerOnScreen();

                Future<?> txFuture = startTx(pass, m_walletFile, getExecService(), (onWalletLoaded)->{
                    pass.erase();
                    Object loadedObject = onWalletLoaded.getSource().getValue();
                    cancelBtn.setDisable(true);
                    cancelBtn.setId("iconBtn");
                    progressLabel.setText("Opening wallet");
                    if(loadedObject != null && loadedObject instanceof Wallet){
                        Wallet wallet = (Wallet) loadedObject;

                        if(m_addressesData == null){
                            ArrayList<AddressData> addressDataList = new ArrayList<>();
                            wallet.myAddresses.forEach((index, name) -> {

                                try {

                                    Address address = wallet.publicAddress(m_networkType, index);
                                    AddressData addressData = new AddressData(name, index, address.toString(), m_networkType, ErgoWalletData.this);
                                    addressDataList.add(addressData);
                                } catch (Exception e) {
                                    try {
                                        Files.writeString(AppConstants.LOG_FILE.toPath(), "AddressesData - address failure: " + e.toString() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                                    } catch (IOException e1) {

                                    }
                                }
                                

                            });
                        
                            m_addressesData = new AddressesData(getId(), addressDataList, ErgoWalletData.this, m_networkType);
                        }

            
                        JsonObject json = new JsonObject();
                        json.addProperty("accessId", controlId + FriendlyId.createFriendlyId());
                        json.addProperty("walletId", getId());
                    
                        
                        Utils.returnObject(json, getExecService(), onSucceeded);
                    
                        
                    }else{
                    
                        Utils.returnException("Wallet unavailable", getExecService(), onFailed);
                        
                    }
                    endTx();
                    releaseAccess.run();
                    
                }, (onLoadFailed)->{
                    pass.erase();
                    Throwable throwable = onLoadFailed.getSource().getException();
                    if(throwable != null){
                        if(!(throwable instanceof InterruptedException)){
                            endTx();
                        }
                        if(throwable instanceof NoSuchFileException){

                            Alert noFileAlert = new Alert(AlertType.ERROR, "Wallet file does not exist, or has been moved.", ButtonType.OK);
                            noFileAlert.setHeaderText("Error");
                            noFileAlert.setTitle("Error: File not found");
                            noFileAlert.show();
                            Utils.returnException((Exception) throwable, getExecService(), onFailed);
                            releaseAccess.run();
                        }else{
                            passwordStage.setScene(passwordScene);
                            passwordStage.centerOnScreen();
                        }
                    }else{
                        Alert unavailableAlert = new Alert(AlertType.ERROR, "Access unavailable", ButtonType.OK);
                        unavailableAlert.setHeaderText("Error");
                        unavailableAlert.setTitle("Error: Access unavailable");
                        unavailableAlert.show();
                        Utils.returnException("Access unavailable", getExecService(), onFailed);
                        releaseAccess.run();
                    }

                    
        
                });

                cancelBtn.setOnAction(onCancel->{
                    txFuture.cancel(true);
                });
            }
        
        });

        
        closeBtn.setOnAction(e -> {
            Utils.returnException("Authorization cancelled", getExecService(), onFailed);
            releaseAccess.run();
        });

        passwordStage.setOnCloseRequest(e->{
            Utils.returnException("Authorization cancelled", getExecService(), onFailed);
            releaseAccess.run();
        });

        passwordScene.focusOwnerProperty().addListener((obs, oldval, newVal) -> {
            if (newVal != null && !(newVal instanceof PasswordField)) {
                Platform.runLater(() -> passwordField.requestFocus());
            }
        });
        passwordStage.show();

        Platform.runLater(()->passwordField.requestFocus());
        
        ResizeHelper.addResizeListener(passwordStage, defaultWidth, defaultHeight, Double.MAX_VALUE, defaultHeight);

        return limitAccessFuture;
    }




    public Future<?> sendNote(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        // getAddresses
        JsonElement cmdElement = note.get(NoteConstants.CMD);
        JsonElement locationIdElement = note.get("locationId");
   
        String locationId = locationIdElement != null && !locationIdElement.isJsonNull() ? locationIdElement.getAsString() : null;
        String cmd = cmdElement != null ? cmdElement.getAsString() : null;
        NetworkInformation networkInformation = locationId != null ?getNetworksData().getLocationNetworkInformation(locationId) : null;

        if(cmd != null){
            JsonElement accessIdElement = note.get("accessId");
            String accessId = accessIdElement != null && accessIdElement.isJsonPrimitive() ? accessIdElement.getAsString() : null;
    
            if(accessId != null){
        
                    switch(cmd){
                        case "getTransactions":
                            return m_addressesData.getTransactions(note, onSucceeded, onFailed);
                        case "getTransactionViews":
                            return m_addressesData.getTransactionViews(note, onSucceeded, onFailed);
                        case "updateAddressBoxInfo":
                            return m_addressesData.updateAddressBoxInfo(note, onSucceeded, onFailed);
                        case "sendAssets":
                            return m_addressesData.sendAssets(note, networkInformation, onSucceeded, onFailed);
                        case "executeTransaction":
                            try{
                                return m_addressesData.executeTransaction(note, networkInformation, onSucceeded, onFailed);
                            }catch(Exception e){
                                return Utils.returnException(e, getExecService(), onFailed);
                            }
                        case "reclaimBox":
                            return m_addressesData.reclaimBox(note, locationId, networkInformation, onSucceeded, onFailed);
                        case "viewWalletMnemonic":
                            m_addressesData.viewWalletMnemonic(networkInformation, onSucceeded, onFailed);
                        break;
                        case "getAddresses":
                            return Utils.returnObject(m_addressesData.getAddressesJson(), getExecService(), onSucceeded);
                        case "getBalance":
                            return  Utils.returnObject(m_addressesData.getBalance(note), getExecService(), onSucceeded);
                        case "getNetworkType":
                            return Utils.returnObject( m_addressesData.getNetworkType().toString(), getExecService(), onSucceeded);
                        
                        case "getFileData":
                            try{
                                return Utils.returnObject(getFileData(), getExecService(), onSucceeded);
                            }catch(Exception e){
                                return Utils.returnException(e, getExecService(), onFailed);
                            }
                    }       
             
            }else{
                switch (cmd) {
                    case "getAccessId":
                        return getAccessId(note, networkInformation, onSucceeded, onFailed);
                    case "getWallet":
                        return Utils.returnObject(getWalletJson(), getExecService(), onSucceeded);
                    case "updateFile":
                        return updateFile(note, onSucceeded, onFailed);   
                    case "updateName":
                        updateName(note, onSucceeded, onFailed);
                    break;
                }
            }
            
        
        }
        
        return null;
    }



    protected void sendMessage(int code, long timeStamp,String networkId, String msg){
        m_ergoWalletsDataList.getErgoWallets().sendMessage(code,  timeStamp, getId() + ":" + networkId, msg);
    }


    public boolean isFile(){
        return  m_walletFile != null && m_walletFile.isFile();
    }

    




    private HashData m_tmpHashData = null;
    private String m_tmpPath = null;


    private JsonObject getFileData() throws Exception{

        JsonObject json = new JsonObject();
        m_tmpPath = null;


        m_tmpPath = m_walletFile != null ?  m_walletFile.getCanonicalPath() : null;
         
  
        boolean isDrive = m_tmpPath != null ? Utils.findPathPrefixInRoots(m_tmpPath) : false;  
        boolean isFile = m_tmpPath != null && isDrive &&  m_walletFile.isFile();
        json.addProperty("isFile", isFile);
        json.addProperty("isDrive", isDrive);
        json.addProperty("path", m_tmpPath != null ? m_tmpPath :( m_walletFile != null ? m_walletFile.getAbsolutePath() : null));
        json.addProperty("name", m_walletFile != null ? m_walletFile.getName() : null);
      
        m_tmpHashData = null;
        m_tmpHashData = isFile ? new HashData(m_walletFile) : null;
   
        json.add("hashData", m_tmpHashData != null ? m_tmpHashData.getJsonObject() : null);

        m_tmpPath = null;
        m_tmpHashData = null;
        return json;
    }
  

    public JsonObject getJsonObject() {
        JsonObject jsonObject = getWalletJson();
        if(m_walletFile != null){
            jsonObject.addProperty("file", m_walletFile.getAbsolutePath());
            try{
                JsonObject fileData = getFileData();
                jsonObject.add("fileData", fileData);
            }catch(Exception e){
                
            }
        }
        return jsonObject;
    }        

    public JsonObject getWalletJson() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("name", getName());
        jsonObject.addProperty("id", getId());
        jsonObject.addProperty("networkType", m_networkType.toString());
        jsonObject.addProperty("timeStamp", System.currentTimeMillis());
        return jsonObject;
   }   


}
