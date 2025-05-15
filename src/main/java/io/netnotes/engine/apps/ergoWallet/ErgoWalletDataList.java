package io.netnotes.engine.apps.ergoWallet;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.ergoplatform.appkit.Mnemonic;
import org.ergoplatform.appkit.MnemonicValidationException;
import org.ergoplatform.appkit.NetworkType;
import org.ergoplatform.sdk.SecretString;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.satergo.Wallet;

import io.netnotes.engine.AppData;
import io.netnotes.engine.ErgoMarketControl;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.Stages;
import io.netnotes.engine.Utils;
import io.netnotes.engine.apps.ergoDex.ErgoDex;
import io.netnotes.engine.networks.ergo.ErgoConstants;
import io.netnotes.engine.networks.ergo.ErgoNetwork;
import io.netnotes.friendly_id.FriendlyId;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.image.Image;

import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class ErgoWalletDataList {

    private ArrayList<ErgoWalletData> m_walletDataList = new ArrayList<>();
    private ErgoWallets m_ergoWallets;
    private SimpleDoubleProperty m_gridWidth;
    private SimpleStringProperty m_iconStyle;
    private String m_locationId;
    


    private HashMap<String, String> m_walletAccessMap = new HashMap<>();

    private ErgoMarketControl m_ergoMarketControl;

    private String m_ergoNetworkId = ErgoNetwork.NETWORK_ID;
    private NetworkType m_networkType;

    private SimpleStringProperty m_marketIdProperty = new SimpleStringProperty();
    private SimpleStringProperty m_tokenMarketIdProperty = new SimpleStringProperty();

    public ErgoWalletDataList(ErgoWallets ergoWallets,NetworkType networkType, String locationId) {
        m_locationId = locationId;
        m_ergoWallets = ergoWallets;
        m_networkType = networkType;
       
        getData();     
         
    }


    public NetworkType getNetworkType(){
        return m_networkType;
    }

    public ErgoWallets getErgoWallets(){
        return m_ergoWallets;
    }

    public NoteInterface getErgoNetworkInterface(){
        return getNetworksData().getNetworkInterface(m_ergoNetworkId);
    }

    private boolean addAccessId(String accessId, String walletId){
        if(!m_walletAccessMap.containsKey(accessId)){
            m_walletAccessMap.put(accessId, walletId);
            return true;
        }
        return false;
    }
    private boolean removeAccessId(String accessId){
        if(accessId != null){
            m_walletAccessMap.remove(accessId);
            return true;
        }
        return false;
    }

    protected String getWalletIdFromAccessId(String accessId){
        return m_walletAccessMap.get(accessId);
    }

    protected boolean hasAccess(String accessId){
        String mappedWalletId =getWalletIdFromAccessId(accessId);
        return  mappedWalletId != null;
    }


    protected Future<?> getAccessId(String locationString, JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        if(locationString != null && note != null && onSucceeded != null && onFailed != null){
            JsonElement walletIdElement = note.get("id");
            String walletId = walletIdElement != null && !walletIdElement.isJsonNull() ? walletIdElement.getAsString() : null;
            if(walletId != null){
                ErgoWalletData walletData = getWallet(walletId);
                if(walletData != null){
                    walletData.sendNote(note, onAccessGranted->{
                        Object obj = onAccessGranted.getSource().getValue();
                        if(obj != null && obj instanceof JsonObject){
                            JsonObject resultObject = (JsonObject) obj;
                            JsonElement accessIdElement = resultObject.get("accessId");
                            if(accessIdElement != null && !accessIdElement.isJsonNull()){
                                String accessId = accessIdElement.getAsString();
                                if(addAccessId(accessId, walletId)){
                                    Utils.returnObject(resultObject, getExecService(), onSucceeded);
                                }else{
                                    Utils.returnException("Error: Access id unavailable", getExecService(), onFailed);
                                }
                            }else{
                                Utils.returnException("Error: No access id returned", getExecService(), onFailed);
                            }
                        }else{
                            Utils.returnException("Unexpected result from ErgoWalletData", getExecService(), onFailed);
                        }
                    }, onFailed);
                }
            }
            /*
                json.addProperty("accessId", id);
                json.addProperty("walletId", getNetworkId());
             */
        }
        return null;
    }

    public Future<?> sendNote(String cmd, String accessId, String locationString, JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        if(cmd != null && accessId != null && locationString != null && note != null && onSucceeded != null && onFailed != null){

        }
        return null;
    }




    public String getLocationId(){
        return m_locationId;
    }



    public SimpleDoubleProperty gridWidthProperty() {
        return m_gridWidth;
    }

    public SimpleStringProperty iconStyleProperty() {
        return m_iconStyle;
    }



    public ExecutorService getExecService(){
        return getErgoWallets().getExecService();
    }


    


    
    public void createWallet(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        
        if(note != null){
            JsonElement isNewElement = note.get("isNew");

            createWalletFile(isNewElement != null && !isNewElement.isJsonNull() ? isNewElement.getAsBoolean() : true, getExecService(), onComplete->{
                Object fileObject = onComplete.getSource().getValue();
                File walletFile = fileObject != null && fileObject instanceof File ? (File) fileObject : null;

                if(walletFile != null && walletFile.isFile()){
                    String id = FriendlyId.createFriendlyId();
                    String name = walletFile.getName();
                    ErgoWalletData walletdata = new ErgoWalletData(id, name, walletFile, m_networkType, this);
                    add(walletdata, true);

                    JsonObject walletObj = new JsonObject();
                    walletObj.addProperty("id",id);
                    walletObj.addProperty("name", name);
                    walletObj.addProperty("networkType", m_networkType.toString());
                    
                    Utils.returnObject(walletObj, getExecService(), onSucceeded);
                }else{
                    Utils.returnException("Unable to add wallet file", getExecService(), onFailed);
                }
            }, onFailed);
        }
    }

    private void getData(){
        
        getNetworksData().getData("data", ".", ErgoWallets.NETWORK_ID, ErgoConstants.ERGO_NETWORK_ID, (onSucceeded)->{
            Object obj = onSucceeded.getSource().getValue();
            JsonObject json = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
            openJson(json); 
        });
        
    }

    public void openJson(JsonObject json) {
        
        JsonElement marketIdElement = json != null ? json.get("marketId") : null;
        JsonElement tokenMarketIdElement = json != null ? json.get("tokenMarketId") : null;

        String marketId = marketIdElement != null ? (marketIdElement.isJsonNull() ? null : marketIdElement.getAsString() ) : ErgoDex.NETWORK_ID;
        String tokenMarketId = tokenMarketIdElement != null ? (tokenMarketIdElement.isJsonNull() ? null : tokenMarketIdElement.getAsString()) : ErgoDex.NETWORK_ID;
    
        m_marketIdProperty.set(marketId);
        m_tokenMarketIdProperty.set(tokenMarketId);
        
        m_ergoMarketControl = new ErgoMarketControl(m_marketIdProperty, m_tokenMarketIdProperty, getLocationId(), getNetworksData());
        if (json != null) {
            
            JsonElement walletsElement = json.get("wallets");

          
            if (walletsElement != null && walletsElement.isJsonArray()) {
                JsonArray jsonArray = walletsElement.getAsJsonArray();

                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonElement jsonElement = jsonArray.get(i);

                    if (jsonElement != null && jsonElement.isJsonObject()) {
                        JsonObject jsonObject = jsonElement.getAsJsonObject();

                        if (jsonObject != null) {
                            JsonElement fileElement = jsonObject.get("file");
                            JsonElement nameElement = jsonObject.get("name");
                            JsonElement idElement = jsonObject.get("id");
                        
                            String id = idElement == null ? FriendlyId.createFriendlyId() : idElement.getAsString();
                            String name = nameElement == null ? "Wallet " + id : nameElement.getAsString();
                            String fileString = fileElement != null && fileElement.isJsonPrimitive() ? fileElement.getAsString() : null;
                           
                            File file = fileString != null ? new File(fileString) : null;


                            ErgoWalletData walletData = new ErgoWalletData(id, name, file,  m_networkType, this);
                            
                            add(walletData, false);

                            
                        }
                    }
                }

            }
           
        }

        
  
        
    }

    public ErgoMarketControl getErgoMarketControl(){
        return m_ergoMarketControl;
    }

    public void save(){
        getNetworksData().save("data", ".", ErgoWallets.NETWORK_ID, ErgoConstants.ERGO_NETWORK_ID, getJsonObject());
    }





    public void add(ErgoWalletData walletData, boolean isSave) {
        m_walletDataList.add(walletData);

        if(isSave){
            save();
            
            long timeStamp = System.currentTimeMillis();

            getErgoWallets().sendMessage(NoteConstants.LIST_ITEM_ADDED,timeStamp, walletData.getId(), "Wallet added");
        }
    }
    
    public NetworksData getNetworksData(){
        return m_ergoWallets.getNetworksData();
    }

    private boolean removeWallet(String id) {
    
        if(id != null){
            for (int i =0; i< m_walletDataList.size(); i++) {
                ErgoWalletData walletData = m_walletDataList.get(i);
                if (walletData.getId().equals(id)) {

                    m_walletDataList.remove(i);
                    
                    return true;
                }
            }
        }
        return false;
    }

    public JsonObject removeWallets(JsonObject note ){
        long timestamp = System.currentTimeMillis();
        JsonElement idsElement = note.get("ids");
          
        JsonArray idsArray = idsElement.getAsJsonArray();
        if(idsArray.size() > 0){
            
            JsonArray jsonArray = new JsonArray();

            for(JsonElement element : idsArray){
                JsonObject idObj = element.getAsJsonObject();
                JsonElement idElement = idObj.get("id");
                String id = idElement.getAsString();
                
                if(removeWallet(id)){
                    jsonArray.add(idObj);
                }
            }

            save();

            JsonObject json = new JsonObject();
            json.add("removedIds", jsonArray);

            getErgoWallets().sendMessage( NoteConstants.LIST_ITEM_REMOVED, timestamp, ErgoWallets.NETWORK_ID, json.toString());


            return NoteConstants.getJsonObject("removed", jsonArray.size());
            
         
        }

        return NoteConstants.getJsonObject("removed", 0);

    }



    public JsonObject getWalletByPath(String path) {
        
        for (ErgoWalletData walletData : m_walletDataList) {
            if(path.equals(walletData.getWalleFile().getAbsolutePath())){
                return walletData.getWalletJson();
            }
        }
        return null;
    }

    public boolean containsName(String name) {
        for (ErgoWalletData walletData : m_walletDataList) {
          
            if(walletData.getName().equals(name)){
                return true;
            }
        }
        return false;
    }



 


    public String restoreMnemonicStage() {
        String titleStr = m_ergoWallets.getName() + " - Restore wallet: Mnemonic phrase";

        Stage mnemonicStage = new Stage();

        mnemonicStage.setTitle(titleStr);

        mnemonicStage.getIcons().add(m_ergoWallets.getIcon());

        mnemonicStage.initStyle(StageStyle.UNDECORATED);

        Button closeBtn = new Button();
        
        HBox titleBox = Stages.createTopBar(m_ergoWallets.getIcon(), titleStr, closeBtn, mnemonicStage);

        Button imageButton = Stages.createImageButton(m_ergoWallets.getIcon(), "Restore wallet");

        HBox imageBox = new HBox(imageButton);
        imageBox.setAlignment(Pos.CENTER);

        Text subTitleTxt = new Text("> Mnemonic phrase:");
        subTitleTxt.setFill(Stages.txtColor);
        subTitleTxt.setFont(Stages.txtFont);

        HBox subTitleBox = new HBox(subTitleTxt);
        subTitleBox.setAlignment(Pos.CENTER_LEFT);

        TextArea mnemonicField = new TextArea();
        mnemonicField.setFont(Stages.txtFont);
        mnemonicField.setId("formField");

        mnemonicField.setWrapText(true);
        mnemonicField.setPrefRowCount(2);
        HBox.setHgrow(mnemonicField, Priority.ALWAYS);

        Platform.runLater(() -> mnemonicField.requestFocus());

        HBox mnemonicBox = new HBox(mnemonicField);
        mnemonicBox.setPadding(new Insets(20, 30, 0, 30));

        Region hBar = new Region();
        hBar.setPrefWidth(400);
        hBar.setPrefHeight(2);
        hBar.setId("hGradient");

        HBox gBox = new HBox(hBar);
        gBox.setAlignment(Pos.CENTER);
        gBox.setPadding(new Insets(15, 0, 0, 0));

        Button nextBtn = new Button("Words left: 15");
        nextBtn.setId("toolBtn");
        nextBtn.setFont(Stages.txtFont);
        nextBtn.setDisable(true);
        nextBtn.setOnAction(nxtEvent -> {
            String mnemonicString = mnemonicField.getText();;

            String[] words = mnemonicString.split("\\s+");

            List<String> mnemonicList = Arrays.asList(words);
            try {
                Mnemonic.checkEnglishMnemonic(mnemonicList);
                mnemonicStage.close();
            } catch (MnemonicValidationException e) {
                Alert a = new Alert(AlertType.NONE, "Error: Mnemonic invalid\n\nPlease correct the mnemonic phrase and try again.", ButtonType.CLOSE);
                a.initOwner(mnemonicStage);
                a.setTitle("Error: Mnemonic invalid.");
            }

        });

        mnemonicField.setOnKeyPressed(e1 -> {
            String mnemonicString = mnemonicField.getText();

            String[] words = mnemonicString.split("\\s+");
            int numWords = words.length;
            if (numWords == 15) {
                nextBtn.setText("Ok");

                List<String> mnemonicList = Arrays.asList(words);
                try {
                    Mnemonic.checkEnglishMnemonic(mnemonicList);
                    nextBtn.setDisable(false);

                } catch (MnemonicValidationException e) {
                    nextBtn.setText("Invalid");
                    nextBtn.setId("toolBtn");
                    nextBtn.setDisable(true);
                }

            } else {
                if (nextBtn.getText().equals("")) {
                    nextBtn.setText("Words left: 15");
                } else {
                    nextBtn.setText("Words left: " + (15 - numWords));
                }

                nextBtn.setId("toolBtn");
                nextBtn.setDisable(true);
            }

        });

        HBox nextBox = new HBox(nextBtn);
        nextBox.setAlignment(Pos.CENTER);
        nextBox.setPadding(new Insets(25, 0, 0, 0));

        VBox bodyBox = new VBox(subTitleBox, mnemonicBox, gBox, nextBox);
        VBox.setMargin(bodyBox, new Insets(5, 10, 0, 20));
        VBox.setVgrow(bodyBox, Priority.ALWAYS);

        VBox layoutVBox = new VBox(titleBox, imageBox, bodyBox);

        Scene mnemonicScene = new Scene(layoutVBox, 600, 425);
        mnemonicScene.setFill(null);
        mnemonicScene.getStylesheets().add("/css/startWindow.css");
        mnemonicStage.setScene(mnemonicScene);

        closeBtn.setOnAction(e -> {

            mnemonicStage.close();
            mnemonicField.setText("");

        });

        mnemonicStage.showAndWait();

        return mnemonicField.getText();

    }

    public JsonObject getWalletById(JsonObject json){
        JsonElement idElement = json.get("id");

        if(idElement != null && idElement.isJsonPrimitive()){
            ErgoWalletData walletData = getWallet(idElement.getAsString());
            return walletData.getWalletJson();
        }

        return null;
    }





    private ErgoWalletData getWallet(String id){
 
        for (ErgoWalletData walletData : m_walletDataList) {
            if (walletData.getId().equals(id)) {
                return walletData;
            }
        }
        return null;
    }
    private String m_tmpStr = "";

    public Object openWallet(JsonObject note){

        JsonElement dataElement = note != null ? note.get("data") : null;

        JsonObject dataObject = dataElement != null && dataElement.isJsonObject() ? dataElement.getAsJsonObject() : null;

        JsonElement pathElement = dataObject != null ? dataObject.get("path") : null;
        JsonElement networkTypeElement = dataObject != null ? dataObject.get("networkType") : null;

        if( pathElement != null && pathElement.isJsonPrimitive()){
            
            String path = pathElement.getAsString();

            if(Utils.findPathPrefixInRoots(path)){

                File file = new File(path);
                

                JsonObject existingWalletJson = getWalletByPath(file.getAbsolutePath());

                if(existingWalletJson != null){
                    return existingWalletJson;
                }

                

                m_tmpStr = file.getName();

                if(!m_tmpStr.equals("")){

                    m_tmpStr = m_tmpStr.endsWith(".erg") ? m_tmpStr.substring(0, m_tmpStr.length()-4) : m_tmpStr;
                    
                    int i = 1;
                    while(containsName(m_tmpStr)){
                        m_tmpStr =  m_tmpStr + " #" + i;
                        i++;
                    }
                    String name = m_tmpStr;
                    

                    m_tmpStr = FriendlyId.createFriendlyId();

                    while(getWallet(m_tmpStr) != null){
                        m_tmpStr = FriendlyId.createFriendlyId(); 
                    }

                    String id = m_tmpStr;
                    m_tmpStr = null;
                    
                    NetworkType networkType = networkTypeElement != null && networkTypeElement.isJsonPrimitive() && networkTypeElement.getAsString().equals(NetworkType.TESTNET.toString()) ? NetworkType.TESTNET : NetworkType.MAINNET;
                    ErgoWalletData walletData = new ErgoWalletData(id, name, file, networkType, this);
                    add(walletData, true);

                   
                    return walletData.getJsonObject();
                }
            }
            
        }

        return null;
    }


    /*public void sendNoteToNetworkId(JsonObject note, String networkId, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {

        m_walletDataList.forEach(walletData -> {
            if (walletData.getNetworkId().equals(networkId)) {

                walletData.sendNote(note, onSucceeded, onFailed);
            }
        });
    }*/

    public int size() {
        return m_walletDataList.size();
    }


    /*
    public void getMenu(MenuButton menuBtn, SimpleObjectProperty<ErgoWalletData> selected){

        menuBtn.getItems().clear();
        ErgoWalletData newSelectedWallet =  selected.get();

        MenuItem noneMenuItem = new MenuItem("(disabled)");
        if(selected.get() == null){
            noneMenuItem.setId("selectedMenuItem");
        }
        noneMenuItem.setOnAction(e->{
            selected.set(null);
        });
        menuBtn.getItems().add(noneMenuItem);

        int numCells = m_walletDataList.size();

        for (int i = 0; i < numCells; i++) {
            
            ErgoWalletData walletData = (ErgoWalletData) m_walletDataList.get(i);

                MenuItem menuItem = new MenuItem(walletData.getName());
            if(newSelectedWallet != null && newSelectedWallet.getId().equals(walletData.getNetworkId())){
                menuItem.setId("selectedMenuItem");
                //  menuItem.setText(menuItem.getText());
            }
            menuItem.setOnAction(e->{
                
                selected.set(walletData);
            });

            menuBtn.getItems().add(menuItem);
        }


    

    }*/





    public JsonArray getWallets(){

        JsonArray jsonArray = new JsonArray();

        for (ErgoWalletData walletData : m_walletDataList) {
            JsonObject result =  walletData.getWalletJson();
            jsonArray.add(result);

        }
        return jsonArray;
    }


    private JsonArray getWalletsJsonArray() {
        JsonArray jsonArray = new JsonArray();

        for (ErgoWalletData walletData : m_walletDataList) {

            JsonObject jsonObj = walletData.getJsonObject();
            jsonArray.add(jsonObj);

        }
        return jsonArray;
    }

    private JsonObject getJsonObject(){
        JsonObject fileObject = new JsonObject();
        fileObject.add("wallets", getWalletsJsonArray());
       
        return fileObject;
    }


    private void createWalletFile(boolean isNew, ExecutorService execService, EventHandler<WorkerStateEvent> complete, EventHandler<WorkerStateEvent> onCanceled) {
        Image smallIcon = new Image(ErgoWallets.ICON);
        Image largeIcon = new Image(ErgoWallets.getSmallAppIconString());

        Stage stage = new Stage();
        stage.setResizable(false);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.getIcons().add(smallIcon);

        
        Label headingText = new Label((isNew ? "New" : "Restore") + " Wallet");
        headingText.setFont(Stages.txtFont);
        headingText.setPadding(new Insets(0,0,0,15));

        Button closeBtn = new Button();

        HBox headingBox = Stages.createTopBar(smallIcon, headingText, closeBtn, stage);
    
    
        VBox headerBox = new VBox(headingBox);
        headerBox.setPadding(new Insets(0, 5, 0, 0));

        TextArea mnemonicField = new TextArea(isNew ? Mnemonic.generateEnglishMnemonic() : "");
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

        
        VBox layoutVBox = new VBox(headerBox, bodyBox);




        Scene scene = new Scene(layoutVBox, 420, 420);
        scene.setFill(null);
        scene.getStylesheets().add(Stages.DEFAULT_CSS);
        
        stage.setScene(scene);

        stage.show();




        Runnable clear = ()->{
            mnemonicField.setText(Mnemonic.generateEnglishMnemonic());
            mnemonicField.setText("");
            mnemonicField.setText(Mnemonic.generateEnglishMnemonic());
            mnemonicField.setText("");
        };

        closeBtn.setOnAction(e->{
            clear.run();
            stage.close();
            Utils.returnException("Canceled", execService, onCanceled);
        });

        stage.setOnCloseRequest(e->{
            clear.run();
            Utils.returnException("Canceled", execService, onCanceled);
        });

        Tooltip errorToolTip = new Tooltip();

        char[] chars = Utils.getAsciiCharArray();

        nextBtn.setOnAction(e -> {
            if(mnemonicField.getText().length() > 0){
                char[] mnemonic = mnemonicField.getText().toCharArray();
                clear.run();
        
                Button closeStageBtn = new Button();

                Button backBtn = new Button();
                backBtn.setText("↩");
                backBtn.setPadding(new Insets(0, 2, 1, 2));
                backBtn.setId("toolBtn");
        
             

                Scene passwordScene = Stages.createPasswordScene(largeIcon, Stages.createTopBar(backBtn, smallIcon, "Create wallet password", closeStageBtn, stage), execService, (onPassword)->{
                    Object resultObj = onPassword.getSource().getValue();
                    SecretString pass = resultObj != null && resultObj instanceof SecretString ? (SecretString) resultObj : null;
                    if(pass != null){
                        Button cancelFileCreation = new Button("Cancel"); 
                        Label creationLbl = new Label("Saving wallet..");
                        Scene waitingScene = Stages.getWaitngScene(creationLbl, cancelFileCreation, stage);
                        stage.setScene(waitingScene);
                        
                        FileChooser saveWalletFileChooser = new FileChooser();
                        saveWalletFileChooser.setTitle("Save wallet (*.erg)");
                        saveWalletFileChooser.setInitialDirectory(AppData.HOME_DIRECTORY);
                        saveWalletFileChooser.getExtensionFilters().add(ErgoConstants.ERGO_WALLET_EXT);
                        saveWalletFileChooser.setSelectedExtensionFilter(ErgoConstants.ERGO_WALLET_EXT);

                        File walletFile =saveWalletFileChooser.showSaveDialog(stage);
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
                                
                                Utils.returnObject(walletFile.getAbsolutePath(), execService, complete);
                                stage.close();

                            }catch(Exception er){
                                backBtn.fire();
                                pass.erase();
                                try{
                                    Utils.fillCharArray(mnemonic, chars);
                                }catch(Exception algoEx){

                                }
                                Point2D p = nextBtn.localToScene(0.0, 0.0);
                    
                                errorToolTip.setText("Canceled: " + er.toString());
                                errorToolTip.show(
                                    nextBtn,  
                                    p.getX() + nextBtn.getScene().getX() + nextBtn.getScene().getWindow().getX() + nextBtn.getLayoutBounds().getWidth()-150, 
                                    (p.getY()+ nextBtn.getScene().getY() + nextBtn.getScene().getWindow().getY())-nextBtn.getLayoutBounds().getHeight()
                                );
                                
                                PauseTransition pt = new PauseTransition(javafx.util.Duration.millis(1600));
                                pt.setOnFinished(ptE->{
                                    errorToolTip.hide();
                                });
                                pt.play();
                            }
                        }else{
                            backBtn.fire();
                            pass.erase();
                            try{
                                Utils.fillCharArray(mnemonic, chars);
                            }catch(Exception algoEx){

                            }
                            Point2D p = nextBtn.localToScene(0.0, 0.0);
                    
                            errorToolTip.setText("Creation canceled");
                            errorToolTip.show(
                                nextBtn,  
                                p.getX() + nextBtn.getScene().getX() + nextBtn.getScene().getWindow().getX() + nextBtn.getLayoutBounds().getWidth()-150, 
                                (p.getY()+ nextBtn.getScene().getY() + nextBtn.getScene().getWindow().getY())-nextBtn.getLayoutBounds().getHeight()
                            );
                            
                            PauseTransition pt = new PauseTransition(javafx.util.Duration.millis(1600));
                            pt.setOnFinished(ptE->{
                                errorToolTip.hide();
                            });
                            pt.play();
                        }
                    }else{
                        backBtn.fire();
                       
                        Point2D p = nextBtn.localToScene(0.0, 0.0);
                
                        errorToolTip.setText("Failed to create password");
                        errorToolTip.show(
                            nextBtn,  
                            p.getX() + nextBtn.getScene().getX() + nextBtn.getScene().getWindow().getX() + nextBtn.getLayoutBounds().getWidth()-150, 
                            (p.getY()+ nextBtn.getScene().getY() + nextBtn.getScene().getWindow().getY())-nextBtn.getLayoutBounds().getHeight()
                        );
                        
                        PauseTransition pt = new PauseTransition(javafx.util.Duration.millis(1600));
                        pt.setOnFinished(ptE->{
                            errorToolTip.hide();
                        });
                        pt.play();
                    }
                });
            
                stage.setScene(passwordScene);

                closeStageBtn.setOnAction(c->{
                    closeBtn.fire();
                });

                backBtn.setOnAction(b ->{
                    stage.setScene(scene);
                    mnemonicField.setText(new String(mnemonic));
                    try{
                        Utils.fillCharArray(mnemonic, chars);
                    }catch(Exception algoEx){

                    }
                });
            }
            
        });

    }
    

    
}
