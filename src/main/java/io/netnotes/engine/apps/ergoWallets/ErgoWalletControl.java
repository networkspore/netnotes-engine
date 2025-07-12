package io.netnotes.engine.apps.ergoWallets;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.ergoplatform.appkit.NetworkType;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.netnotes.engine.NoteBytes;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.NoteMsgInterface;
import io.netnotes.engine.NoteStatusMsg;
import io.netnotes.engine.PriceAmount;
import io.netnotes.engine.Stages;
import io.netnotes.engine.StreamInterface;
import io.netnotes.engine.SubmitButton;
import io.netnotes.engine.Utils;
import io.netnotes.engine.NetworksData.NetworkControl;
import io.netnotes.engine.networks.ergo.ErgoBoxInfo;
import io.netnotes.engine.networks.ergo.ErgoConstants;
import io.netnotes.engine.networks.ergo.ErgoCurrency;
import io.netnotes.engine.networks.ergo.ErgoInputData;
import io.netnotes.engine.networks.ergo.ErgoNetwork;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.Duration;
import oshi.util.tuples.Pair;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;

public class ErgoWalletControl {

    private final NoteBytes m_networkId;
    private final NoteBytes m_parentId;
    
    private NoteBytes m_locationId = null;
    private Future<?> m_accessIdFuture = null;

    private SimpleObjectProperty<JsonObject> m_walletsNetworkObject = new SimpleObjectProperty<>(null);
    private StreamInterface m_walletsInterface = null;

    private SimpleStringProperty m_currentAddress = new SimpleStringProperty(null);
    private SimpleObjectProperty<JsonArray> m_walletsArray = new SimpleObjectProperty<>(null);
    private SimpleObjectProperty<JsonArray> m_addressesArray = new SimpleObjectProperty<>(null);
    private SimpleObjectProperty<JsonObject> m_balanceObject = new SimpleObjectProperty<>(null);
    private SimpleObjectProperty<JsonObject> m_walletObject = new SimpleObjectProperty<>();

    private String m_accessId = null;

    private NoteMsgInterface m_walletMsgInterface = null;
    private NoteMsgInterface m_networksDataMsgInterface = null;
    private NetworksData m_networksData = null;
    private NetworkType m_networkType = null;
    private JsonParser m_jsonParser;
    private int m_destinationType  = -1;
    private NoteBytes m_walletsNetworkId;
    private NoteBytes m_ergoNetworkNetworkId;

    private String m_defaultWalletId = null;
    private SimpleBooleanProperty m_disabledProperty = new SimpleBooleanProperty(false);

    private NoteInterface m_controlInterface = null;
    private NoteMsgInterface m_controlMsgInterface = null;

    public ErgoWalletControl(NoteBytes networkId, NoteBytes parentId, NoteBytes walletsNetworkId, NoteBytes ergoNetworkNetworkId, NetworkType networkType, NoteBytes locationId, NetworksData networksData) {
        this(NoteConstants.APPS, networkId, parentId,  walletsNetworkId,  ergoNetworkNetworkId,  networkType,  locationId,  networksData);     
    }

    public ErgoWalletControl(int destinationType, NoteBytes networkId, NoteBytes parentId, NoteBytes walletsNetworkId, NoteBytes ergoNetworkNetworkId, NetworkType networkType, NoteBytes locationId, NetworksData networksData) {
        m_networkId = networkId;
        m_parentId = parentId;
        m_locationId = locationId;
        m_destinationType = destinationType;
        m_walletsNetworkId = walletsNetworkId;
        m_ergoNetworkNetworkId = ergoNetworkNetworkId;
        m_networksData = networksData;
        m_networkType = networkType;
        m_jsonParser = new JsonParser();
        addNetworksDataListener();
        getData();

    }

    public NoteInterface getControlInterface(){
        return m_controlInterface;
    }

    public NoteBytes getNetworkId(){
        return m_networkId;
    }

    public NoteBytes getParentId(){
        return m_parentId;
    }

    public boolean isWalletsAppAvailable(){
        return m_networksData.isAvailable(m_destinationType, m_walletsNetworkId);
    }

    public boolean isDisabled(){
        return m_disabledProperty.get();
    }

    public void setDisabled(boolean disabled){
        m_disabledProperty.set(disabled);
        if(disabled){
            disconnectWallet();
            m_walletObject.set(null);
        }else{
            updateDefaultWallet();
        }
    }

    public ReadOnlyObjectProperty<JsonArray> walletsProperty(){
        return m_walletsArray;
    }

   
    public ReadOnlyBooleanProperty disabledProperty(){
        return m_disabledProperty;
    }

    public void addNetworksDataListener(){
       /* getNetworksData().getNetworkId();
        NetworkControl networkControl = getNetworksData().getNetworkControl();
        networkControl.sendStream(NoteConstants.APPLICATION, )
        if(m_networksDataMsgInterface == null){
            updateWalletsInterface();

            m_networksDataMsgInterface = new NoteMsgInterface() {

                @Override
                public String getId() {
                    
                    return m_locationId;
                }

                @Override
                public void sendMessage(int code, long timestamp, String networkId, String msg) {
                    switch(networkId){
                        case NetworksData.APPS:
                            updateWalletsInterface();
                        break;
                    }
                }
                
            };
  
            getNetworksData().addMsgListener(m_networksDataMsgInterface);
        }*/
    }

    private void readStatusMessages(JsonReader statusMessage) throws Exception{
        statusMessage.beginArray();
        

        while(statusMessage.hasNext()){
            if(statusMessage.peek() == JsonToken.BEGIN_OBJECT){
                statusMessage(new NoteStatusMsg(statusMessage));
            }else{
                statusMessage.skipValue();
            }
        }
        statusMessage.endArray();
    }

    private void statusMessage(NoteStatusMsg msg){
        String header = msg != null ? msg.getHeader() : null;
        if(header != null && header.equals(ErgoWallets.NETWORK_ID)){
            switch(msg.getCode()){
                case NoteConstants.LIST_ITEM_REMOVED:
                case NoteConstants.LIST_ITEM_ADDED:
                    updateWallets();
            }                                
        } 
    }
    

    public void getData(){
        m_networksData.getData(m_parentId, m_networkId, m_walletsNetworkId, m_ergoNetworkNetworkId+m_networkType.toString(), (onComplete)->{
            Object obj = onComplete.getSource().getValue();
            JsonObject json = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
            openJson(json); 
           // updateWalletsInterface();
        });
    
    }

    public void save(){
        m_networksData.save(m_parentId, m_networkId, m_walletsNetworkId, m_ergoNetworkNetworkId+m_networkType.toString(), getJsonObject());
    }

    private void openJson(JsonObject json){
        if(json != null){
            JsonElement destinationType = json != null ? json.get("destinationType") : null; 
            JsonElement walletIdElement = json != null ? json.get("walletId") : null;
            JsonElement disabledElement = json != null ? json.get("isDisabled") : null;
            m_destinationType = destinationType != null ? destinationType.getAsInt() : NoteConstants.APPS;
            m_defaultWalletId = walletIdElement != null ? (walletIdElement.isJsonNull() ? null : walletIdElement.getAsString() ) : null;
            boolean disabled = disabledElement != null ? (disabledElement.isJsonNull() ? false : disabledElement.getAsBoolean() ) : false;
            m_disabledProperty.set(disabled);
            updateDefaultWallet();
        }
    }

    public void updateWallets(){
        getWallets((onSucceeded)->{
            Object result = onSucceeded.getSource().getValue();
            if(result != null && result instanceof JsonArray){
                JsonArray walletsArray = (JsonArray) result;
                String walletId = getWalletId();
                if(walletId != null && getWalletObjectFromArray(walletsArray, walletId) == null){
                    disconnectWallet();
                }
                m_walletsArray.set(walletsArray);
            }
        }, (onFailed)->{
            disconnectWallet();
            m_walletsArray.set(null);
        });
    }

    public static JsonObject getWalletObjectFromArray(JsonArray jsonArray, String walletId){
        if (jsonArray != null) {
            int size = jsonArray.size();

            for (int i = 0; i < size; i++) {
                JsonElement objectElement = jsonArray.get(i);
               
                JsonObject json = objectElement != null && objectElement.isJsonObject() ? objectElement.getAsJsonObject() : null;
                JsonElement idElement = json != null ? json.get("id") : null;

                String id = idElement != null && !idElement.isJsonNull() && idElement.isJsonPrimitive()
                        ? idElement.getAsString()
                        : null;

                if (id != null && id.equals(walletId)) {
                    
                    
                    return json;
                }
            
            }
        }

        return null;
    }


    public String getDefaultWalletId(){
        return m_defaultWalletId;
    }

    public void updateDefaultWallet(){
        if(m_walletObject.get() != null){
            disconnectWallet();
        }
        if(m_defaultWalletId != null){
            getWalletById(m_defaultWalletId, onSucceeded->{
                Object obj = onSucceeded.getSource().getValue();
                if(obj != null && obj instanceof JsonObject){
                    m_walletObject.set((JsonObject) obj);
                }
            }, onFailed->{
            });
        }
    }

    public ReadOnlyObjectProperty<JsonObject> walletsNetworkObjectProperty(){
        return m_walletsNetworkObject;
    }


    public void setDefaultWalletId(String walletId){
        m_defaultWalletId = walletId;
        save();
    }

    public JsonObject getJsonObject(){
        JsonObject json = new JsonObject();
        json.addProperty("walletId", m_defaultWalletId);
        json.addProperty("isDisabled", m_disabledProperty.get());
        return json;
    }

    public String getErgoWalletsId(){
        return m_walletsNetworkId;
    }

    public String getErgoNetworkId(){
        return m_ergoNetworkNetworkId;
    }

    public void setErgoWalletsId(String networkId){
        m_walletsNetworkId = networkId;
    }

    public void setErgoNetworkId(String networkId){
        m_ergoNetworkNetworkId = networkId;
    }

    public String getWalletId(){
        JsonObject walletObject = m_walletObject.get();
        if(walletObject != null){
            JsonElement idElement = walletObject.get("id");
            if(idElement != null && !idElement.isJsonNull()){
                return idElement.getAsString();
            }else{
                m_walletObject.set(null);
            }
        }
        
        return null;
    }
    
    public String getWalletName(){
        JsonObject walletObject = m_walletObject.get();
        return NoteConstants.getNameFromObject(walletObject);
    }

    public boolean isConnected(){
        return m_walletMsgInterface != null;
    }

    public ReadOnlyObjectProperty<JsonObject> walletObjectProperty(){
        return m_walletObject;
    }

    public void clearWallet(){
        setWalletObject(null);
    }

    public void setWalletObject(JsonObject json){
        String oldWalletId = getWalletId();

        JsonElement idElement = json != null ? json.get("id") : null;
        String newId = idElement != null && !idElement.isJsonNull() ? idElement.getAsString() : null;
        
        boolean isSame =oldWalletId != null && newId != null && oldWalletId.equals(newId);

        if(!isSame){
            disconnectWallet();
            setDefaultWalletId(newId);
        }

 
        
        if(!isSame){
            m_walletObject.set(json);
        }
    }

    public char[] getLocationId() {
        return m_locationId;
    }

    public ReadOnlyStringProperty currentAddressProperty() {
        return m_currentAddress;
    }

    public ReadOnlyObjectProperty<JsonObject> balanceProperty() {
        return m_balanceObject;
    }


    public String getCurrentAddress() {
        return m_currentAddress.get();
    }

    public Future<?> executeSimpleTransaction(long amountToSpendNanoErgs, long feeNanoErgs, PriceAmount[] tokens,
            JsonObject outputData, EventHandler<WorkerStateEvent> onSucceeded,
            EventHandler<WorkerStateEvent> onFailed) {
        NoteInterface walletsInterface = getWalletsInterface();
        if (walletsInterface != null) {
            String walletId = getWalletId();
            
            if(walletId != null){
                String accessId = m_accessId;
                
                if (accessId != null) {
                        
                    JsonObject txData = new JsonObject();
                    String currentAddressString = getCurrentAddress();
                    String walletName = getWalletName();

                    if (currentAddressString == null || walletName == null) {
                        return Utils.returnException("Wallet is locked", getExecService(), onFailed);
                    }

                    JsonObject balanceObject = m_balanceObject.get();

                    if (balanceObject == null) {
                        return Utils.returnException("Balance is unvailable", getExecService(), onFailed);
                    }

                    ArrayList<PriceAmount> balanceList = NoteConstants.getBalanceList(balanceObject, true, m_networkType);

                    PriceAmount ergoBalance = NoteConstants.getPriceAmountFromList(balanceList, ErgoCurrency.TOKEN_ID);

                    if (ergoBalance == null) {
                        return Utils.returnException("Ergo balance unavailable", getExecService(), onFailed);
                    }

                    if (ergoBalance.getLongAmount() < (amountToSpendNanoErgs + feeNanoErgs
                            + ErgoCurrency.getNanoErgsFromErgs(ErgoConstants.MIN_NETWORK_FEE))) {
                        return Utils.returnException("Insufficient ergo with network fee (" + ErgoConstants.MIN_NETWORK_FEE
                                + ") and token housing (" + ErgoConstants.MIN_NETWORK_FEE + ")", getExecService(), onFailed);
                    }

                    if (tokens.length > 0) {
                        for (PriceAmount tokenAmount : tokens) {
                            PriceAmount tokenBalance = NoteConstants.getPriceAmountFromList(balanceList, ErgoCurrency.TOKEN_ID);
                            if (tokenBalance == null) {
                                return Utils.returnException(tokenAmount.getSymbol() + " not available in wallet.",
                                        getExecService(), onFailed);
                            }
                            if (tokenAmount.getLongAmount() > tokenBalance.getLongAmount()) {
                                return Utils.returnException("Insufficient " + tokenAmount.getSymbol() + " in wallet (Balance: "
                                        + tokenBalance.getBigDecimalAmount() + ")", getExecService(), onFailed);
                            }
                        }
                    }

                    ErgoInputData inputData = new ErgoInputData(walletName, NoteConstants.CURRENT_WALLET_FILE, currentAddressString,
                            amountToSpendNanoErgs, ErgoInputData.convertPriceAmountsToErgoTokens(tokens), feeNanoErgs,
                            ErgoInputData.ASSETS_INPUT, ErgoInputData.FEE_INPUT, ErgoInputData.CHANGE_INPUT);

                    NoteConstants.addSingleInputToDataObject(inputData, txData);

                    if (outputData == null) {
                        return Utils.returnException("No ouptut data provided", getExecService(), onFailed);
                    }
                    JsonArray outputs = new JsonArray();
                    outputs.add(outputData);

                    NoteConstants.addNetworkTypeToDataObject(m_networkType, txData);

                    txData.add("outputs", outputs);

                    JsonObject note = NoteConstants.getCmdObject("executeTransaction", m_locationId);
                    note.addProperty("accessId", m_accessId);

                    return walletsInterface.sendNote(note, onSucceeded, onFailed);
                }else{
                    return Utils.returnException("No access", getExecService(), onFailed);
                }
            }else{
                return Utils.returnException("No wallet id provided", getExecService(), onFailed);
            }

        }else {
            return Utils.returnException("Ergo Wallets: (unavailable)", getExecService(), onFailed);
        }
    }

    public ExecutorService getExecService() {
        return m_networksData.getExecService();
    }


   


    private void respondToObject(JsonWriter writer, JsonReader reader) throws Exception{
        NoteStatusMsg noteStatusMsg = new NoteStatusMsg(reader);
        
        switch(noteStatusMsg.getCode()){
            case NoteConstants.READY:

            break;
        }

    }


    public void connectToWallet() {

        NoteConnection noteconnection = new NoteConnection(m_locationId)

        String walletId = getWalletId();
        if (!isDisabled() && walletId != null) {

            if (m_accessIdFuture == null || (m_accessIdFuture != null && m_accessIdFuture.isDone())) {
                
                JsonObject getAccessIdNote = NoteConstants.getCmdObject("getAccessId", getLocationId());
                getAccessIdNote.addProperty("id", walletId);

               sendToWallet(getAccessIdNote, onSucceeded -> {
                    Object successObject = onSucceeded.getSource().getValue();

                    if (successObject != null) {
                        JsonObject json = (JsonObject) successObject;
                        // addressesDataObject.set(json);
                        JsonElement walletObjectElement = json.get("wallet");
                        JsonElement accessIdElement = json.get("accessId");

                        if (accessIdElement != null && !accessIdElement.isJsonNull() && walletObjectElement != null && walletObjectElement.isJsonObject()) {

                            m_accessId = accessIdElement.getAsString();
                            JsonObject walletObject = walletObjectElement.getAsJsonObject();

                            m_walletMsgInterface = new NoteMsgInterface() {
                                private String interfaceId = m_accessId;
                                @Override
                                public String getId() {
                                    return interfaceId;
                                }

                                @Override
                                public void sendMessage(int code, long timestamp, String networkId, String msg) {
                                    String currentAddress = m_currentAddress.get();

                                    switch (code) {
                                        case NoteConstants.UPDATED:
                                            if (networkId != null && currentAddress != null && networkId.equals(currentAddress) && msg != null && msg.length() > 0 && msg.startsWith("{")) {
                                                JsonElement balanceElement = m_jsonParser.parse(msg);
                                                
                                                m_balanceObject.set(balanceElement.isJsonObject() ? balanceElement.getAsJsonObject() : null);
                                                
                                            }
                                            break;
                                      

                                    }
                                }

                        
                            };
                            walletsInterface.addMsgListener(m_walletMsgInterface);
                            m_walletsInterface.set(walletsInterface);
                            m_walletObject.set(walletObject);

                           
                            getNetworkObject(walletsInterface, (onNetworkObject)->{
                                Object obj = onNetworkObject.getSource().getValue();
                                m_walletsNetworkObject.set(obj != null && obj instanceof JsonObject ? (JsonObject) obj : null);
                            }, onFailed->{
                                m_walletsNetworkObject.set(null);
                            });

                            getAddresses(onAddresses -> {
                                Object obj = onAddresses.getSource().getValue();
                                JsonArray addressesArray = obj != null && obj instanceof JsonArray ? (JsonArray) obj : null;
                                m_addressesArray.set(addressesArray);
                                JsonElement addressJsonElement = addressesArray != null ? addressesArray.get(0) : null;
                                JsonObject adr0 = addressJsonElement != null && !addressJsonElement.isJsonNull()
                                        && addressJsonElement.isJsonObject() ? addressJsonElement.getAsJsonObject() : null;
                                JsonElement addressElement = adr0 != null ? adr0.get("address") : null;
                                String address = addressElement != null ? addressElement.getAsString() : null;
                
                                if (address != null) {
                                    m_currentAddress.set(address);
                                    updateBalance();
                                } else {
                                    disconnectWallet();
                                }
                            }, onFailed -> {
                                disconnectWallet();
                            });
                        }
                    }
                }, onFailed -> {
                    disconnectWallet();
                });
            }
        } 
    }



    public void createWallet(boolean isNew){
        NoteInterface controlInterface = getControlInterface();
        if(controlInterface != null){
            
        }else{

        }
    }

    public SimpleObjectProperty<JsonArray> addressesArrayProperty(){
        return m_addressesArray;
    }

    public boolean isAddressInWallet(String walletAddress) {
        JsonArray addressesArray = m_addressesArray.get();
        if (addressesArray != null) {
            int size = addressesArray.size();
            for (int i = 0; i < size; i++) {
                JsonElement addressJsonElement = addressesArray.get(i);
                JsonObject adressObject = addressJsonElement != null && !addressJsonElement.isJsonNull()
                        && addressJsonElement.isJsonObject() ? addressJsonElement.getAsJsonObject() : null;
                JsonElement addressElement = adressObject != null ? adressObject.get("address") : null;
                String address = addressElement != null ? addressElement.getAsString() : null;
                if (address != null && address.equals(walletAddress)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setCurrentAddress(String address) {
        if (isAddressInWallet(address)) {
            m_currentAddress.set(address);
        }
    }




    public Future<?> getNetworkObject(NoteInterface walletsInterface, EventHandler<WorkerStateEvent> onSucceess, EventHandler<WorkerStateEvent> onFailed){
        
        JsonObject note = NoteConstants.getCmdObject("getNetworkObject", m_locationId);

        return walletsInterface.sendNote(note, onSucceess, onFailed);
    }


    public Future<?> getWalletById(String walletId, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface controlInterface = getControlInterface();
        if (controlInterface != null) {
            JsonObject note =  NoteConstants.getCmdObject("getWalletById", m_locationId);

            return controlInterface.sendNote(note, onSucceeded, onFailed);
        } else {
            return Utils.returnException(NoteConstants.STATUS_UNAVAILABLE, getExecService(), onFailed);
        }
    }


    public Future<?> getWallets(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        NoteInterface controlInterface = getControlInterface();
        if (controlInterface != null) {
            JsonObject note =  NoteConstants.getCmdObject("getWallets", m_locationId);
            return controlInterface.sendNote(note, onSucceeded, onFailed);
        } else {
            return Utils.returnException(NoteConstants.STATUS_UNAVAILABLE, getExecService(), onFailed);
        }
    }

    public Future<?> removeWallets(JsonArray removeIds, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        NoteInterface controlInterface = getControlInterface();
        if (controlInterface != null) {

            return null;
        } else {
            return Utils.returnException(NoteConstants.STATUS_UNAVAILABLE, getExecService(), onFailed);
        }
    }

    public Future<?> getAddresses(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        NoteInterface walletsInterface = getWalletsInterface();
        if (walletsInterface != null) {
            String walletId = getWalletId();
            if(walletId != null){
                String accessId = m_accessId;
                
                if (accessId != null) {
                    
                    JsonObject json = NoteConstants.getCmdObject("getAddresses");
                    json.addProperty("accessId", accessId);
                    json.addProperty("id", walletId);
                    json.addProperty("locationId", getLocationId());

                    return walletsInterface.sendNote(json, onSucceeded, onFailed);
                }else{
                    return Utils.returnException("No access", getExecService(), onFailed);
                }
            }else{
                return Utils.returnException("No wallet selected", getExecService(), onFailed);
            }
 
        }else {
            return Utils.returnException("(unavailable)", getExecService(), onFailed);
        }
    }

    public void updateBalance() {
        NoteInterface walletsInterface = getWalletsInterface();
        if (walletsInterface != null) {
            String walletId = getWalletId();
            if(walletId != null){
                String accessId = m_accessId;
                String address = m_currentAddress.get();

                if (accessId != null && address != null) {

                    getBalance(onSucceeded -> {
                        Object obj = onSucceeded.getSource().getValue();
                        JsonObject balanceObject = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
        
                        m_balanceObject.set(balanceObject);
                    }, onFailed -> {
                        m_balanceObject.set(null);
                    });

                } else {
                    m_balanceObject.set(null);
                }
            }else{
                m_balanceObject.set(null);
            }

        }else {
            m_balanceObject.set(null);
        }
    }


    public Future<?> getBalance(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface walletsInterface = getWalletsInterface();
        if(walletsInterface != null){
            if( m_accessId != null){
                String address = m_currentAddress.get();
                if(address != null){
                    JsonObject note = NoteConstants.getCmdObject("getBalance");
                    note.addProperty("locationId", getLocationId());
                    note.addProperty("accessId", m_accessId);
                    note.addProperty("address", m_currentAddress.get());

                    return walletsInterface.sendNote(note, onSucceeded, onFailed);
                }else{
                    return Utils.returnException("No address selected", getExecService(), onFailed);
                }
            }else{
                return Utils.returnException("No access", getExecService(), onFailed);
            }
        }else{
            return Utils.returnException("Balance unavailable", getExecService(), onFailed);
        }
    }
    

    public void showWalletMnemonic() {
        showWalletMnemonic(onSucceeded -> {
        }, onFailed -> {
        });
    }

    public void showWalletMnemonic(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        NoteInterface walletInterface = getWalletsInterface();
        if (walletInterface != null) {
            JsonObject note = NoteConstants.getCmdObject("viewWalletMnemonic", m_locationId);
            note.addProperty("accessId", m_accessId);

            walletInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            Utils.returnException("Ergo Wallets: (unavailable)", getExecService(), onFailed);
        }
    }



    public Future<?> getBox(String boxId, EventHandler<WorkerStateEvent> onComplete,
            EventHandler<WorkerStateEvent> onFailed) {
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if (ergoNetworkInterface != null) {
            JsonObject note = NoteConstants.getCmdObject("getBox");
            note.addProperty("networkId", ErgoConstants.EXPLORER_NETWORK);
            note.addProperty("locationId", getLocationId());
            note.addProperty("value", boxId);
            return ergoNetworkInterface.sendNote(note, onComplete, onFailed);
        }else{
            return Utils.returnException("Ergo Network: (unavailable)", getExecService(), onFailed);
        }
    }

    public Future<?> getTokenInfo(String tokenId, EventHandler<WorkerStateEvent> onComplete,
            EventHandler<WorkerStateEvent> onFailed) {
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if (ergoNetworkInterface != null) {
            JsonObject note = NoteConstants.getCmdObject("getTokenInfo");
            note.addProperty("networkId", ErgoConstants.EXPLORER_NETWORK);
            note.addProperty("locationId", getLocationId());
            note.addProperty("tokenId", tokenId);
            return ergoNetworkInterface.sendNote(note, onComplete, onFailed);
        }else{
            return Utils.returnException("Ergo Network: (unavailable)", getExecService(), onFailed);
        }
    }




    public void copyCurrentAddressToClipboard(Control control) {
        String adrText = m_currentAddress.get();
        if (adrText != null) {

            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(adrText);
            clipboard.setContent(content);

            Point2D p = control.localToScene(0.0, 0.0);
            if (control.tooltipProperty().get() != null) {
                control.tooltipProperty().get().hide();
            }

            Tooltip copiedTooltip = new Tooltip("copied");

            copiedTooltip.show(
                    control,
                    p.getX() + control.getScene().getX() + control.getScene().getWindow().getX(),
                    (p.getY() + control.getScene().getY() + control.getScene().getWindow().getY())
                            - control.getLayoutBounds().getHeight());
            PauseTransition pt = new PauseTransition(Duration.millis(1600));
            pt.setOnFinished(ptE -> {
                copiedTooltip.hide();
            });
            pt.play();
        }
    }

    public void showAddressStage() {
        String adrText = m_currentAddress.get();
        if (adrText != null) {
            Stages.showMagnifyingStage("Wallet: " + getWalletName() + " - Address: " + adrText, adrText);
        }
    }

    public Future<?> getTransactionViews(EventHandler<WorkerStateEvent> onSucceeded,
            EventHandler<WorkerStateEvent> onFailed) {
        NoteInterface walletsInteface = getWalletsInterface();
        if (walletsInteface != null) {
            if(m_accessId != null){
                JsonObject note = NoteConstants.getCmdObject("getTransactionViews", m_locationId);
                note.addProperty("accessId", m_accessId);
                note.addProperty("address", getCurrentAddress());
                return walletsInteface.sendNote(note, onSucceeded, onFailed);
            }else{
                return Utils.returnException("No access", getExecService(), onFailed);
            }
        } else {
            return Utils.returnException("Ergo Wallets: (unavailable)", getExecService(), onFailed);
        }
    }

    public Future<?> updateBoxInfo(String txId, ErgoBoxInfo boxInfo, EventHandler<WorkerStateEvent> onSucceeded,
            EventHandler<WorkerStateEvent> onFailed) {

        NoteInterface walletsInteface = getWalletsInterface();
        if (walletsInteface != null) {
            if(m_accessId != null){

            JsonObject note = NoteConstants.getCmdObject("updateAddressBoxInfo", m_locationId);
            note.addProperty("accessId", m_accessId);
            JsonObject dataObject = new JsonObject();
            dataObject.addProperty("txId", txId);
            dataObject.addProperty("address", getCurrentAddress());
            dataObject.add("boxInfo", boxInfo.getJsonObject());
            note.add("data", dataObject);
            return walletsInteface.sendNote(note, onSucceeded, onFailed);

            }else{
                return Utils.returnException("No access", getExecService(), onFailed);
            }
        } else {
            return Utils.returnException("Ergo Wallets: (unavailable)", getExecService(), onFailed);
        }
    }

    public Future<?> reclaimBox(String txId, String boxId, EventHandler<WorkerStateEvent> onSucceeded,
            EventHandler<WorkerStateEvent> onFailed) {

        NoteInterface walletsInteface = getWalletsInterface();
        if (walletsInteface != null) {
            if(m_accessId != null){

            JsonObject note = NoteConstants.getCmdObject("reclaimBox", m_locationId);
            note.addProperty("accessId", m_accessId);
            JsonObject dataObject = new JsonObject();
            dataObject.addProperty("txId", txId);
            dataObject.addProperty("address", getCurrentAddress());
            dataObject.addProperty("boxId", boxId);

            note.add("data", dataObject);
            return walletsInteface.sendNote(note, onSucceeded, onFailed);

       
            }else{
                return Utils.returnException("No access", getExecService(), onFailed);
            }
        } else {
            return Utils.returnException("Ergo Wallets: (unavailable)", getExecService(), onFailed);
        }

    }

    public boolean isControl(){
        return m_controlInterface != null;
    }

    public void openWallet()  {
        openWallet(null);
    }

    public void openWallet(File walletFile){
        NoteInterface controlInterface = getControlInterface();

        if (controlInterface != null) {

            NetworkInformation networkInformation = getNetworksData().getLocationNetworkInformation(m_locationId);

            SubmitButton submitButton = new SubmitButton("Open");

            ErgoWalletOpenTab openTab = new ErgoWalletOpenTab(walletFile, networkInformation, this, submitButton);
            String openTabId = openTab.getId();

            submitButton.setOnSubmit(onSubmit->{
                Object submitObject = onSubmit.getSource().getValue();
                JsonObject submitJson = submitObject != null && submitObject instanceof JsonObject ? (JsonObject) submitObject : null;
                if(submitJson != null){
                    JsonObject note = NoteConstants.getCmdObject("openWallet", m_locationId);
                    note.add("data", submitJson);

                    controlInterface.sendNote(note, onSucceeded->{
                        getNetworksData().getContentTabs().removeContentTab(openTabId);
                    }, (onOpenFailed)->{
                        Throwable throwable = onOpenFailed.getSource().getException();

                        if(submitButton.getOnError() != null){
                            
                            Utils.returnException("Error:" + (throwable != null ? throwable.getMessage() : " failed") , getExecService(), submitButton.getOnError());
                        }
                    });
                }
            });

            getNetworksData().getContentTabs().addContentTab(openTab);

 

        }

    }

    
    public NetworkType getNetworkType(){
        return m_networkType;
    }


    public NoteInterface getErgoNetworkInterface() {
        return m_networksData.getNetworkInterface(ErgoNetwork.NETWORK_ID);
    }



    public NoteInterface getWalletsInterface() {
        return m_walletsInterface.get();
    }


    public void disconnectWallet() {
        if (m_accessIdFuture != null && (!m_accessIdFuture.isDone() || !m_accessIdFuture.isCancelled())) {
            m_accessIdFuture.cancel(true);
        }
        NoteInterface walletsInterface = getWalletsInterface();

        if (walletsInterface != null && m_walletMsgInterface != null) {
            walletsInterface.removeMsgListener(m_walletMsgInterface);
        }
        m_walletsNetworkObject.set(null);
        m_walletsInterface.set(null);;
        m_walletMsgInterface = null;
        m_addressesArray = null;
        m_currentAddress.set(null);
        m_balanceObject.set(null);
        m_accessId = null;
    }

 
    public Future<?> sendNoteData(String cmd, JsonObject noteData, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        if(m_accessId == null){
            return Utils.returnException("No access to wallet", getExecService(), onFailed);
        }

        if(m_locationId == null){
            return Utils.returnException("Location is not initialized", getExecService(), onFailed);
        }

        NoteInterface walletsInterface = getWalletsInterface();

        if(walletsInterface == null){
            return Utils.returnException("Ergo Wallets: (unavailable)", getExecService(), onFailed);
        }

        JsonObject note = NoteConstants.getCmdObject(cmd, m_locationId);
        note.addProperty("accessId", m_accessId);
        note.add("data", noteData);


        return walletsInterface.sendNote(note, onSucceeded, onFailed);
   }

    public NetworksData getNetworksData() {
        return m_networksData;
    }

    public boolean isUnlocked(){
        return !isDisabled() && m_accessId != null && m_locationId != null && getWalletsInterface() != null;
    }

    public String checkWalletName(String name){

        name = name.endsWith(".erg") ? name.substring(0, name.length()-4) :name;

        SimpleStringProperty nameProperty = new SimpleStringProperty(name);
        
        int i = 1;
        while(containsName(nameProperty.get())){
            nameProperty.set( name + " #" + i);
            i++;
        }
        return nameProperty.get();
    }

    public boolean containsName(String name) {
        JsonArray jsonArray = m_walletsArray.get();
        if(jsonArray != null){
            for (JsonElement element : jsonArray ) {
                JsonObject json = element.isJsonObject() ? element.getAsJsonObject() : null;
                String walletName = json != null ? NoteConstants.getJsonName(json) : null;

                if(walletName.equals(name)){
                    return true;
                }
            }
        }
        return false;
    }

    public void disconnectControl(){
        if(m_controlInterface != null && m_controlMsgInterface != null){
            m_controlInterface.removeMsgListener(m_controlMsgInterface);
            m_controlInterface = null;
            m_controlMsgInterface = null;
        }
    }

    public void shutdown() {
        if(m_networksDataMsgInterface != null){
            getNetworksData().removeMsgListener(m_networksDataMsgInterface);
            m_networksDataMsgInterface = null;
        }
        disconnectWallet();
        disconnectControl();
    }

}
