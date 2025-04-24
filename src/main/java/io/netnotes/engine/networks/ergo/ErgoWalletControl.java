package io.netnotes.engine.networks.ergo;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.ergoplatform.appkit.NetworkType;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.NoteMsgInterface;
import io.netnotes.engine.PriceAmount;
import io.netnotes.engine.PriceQuote;
import io.netnotes.engine.Stages;
import io.netnotes.engine.Utils;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.util.Duration;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;


public class ErgoWalletControl {

    private String m_locationId;
    private String m_accessId = null;
    private Future<?> m_accessIdFuture = null;

    private NoteInterface m_ergoNetworkInterface;
    private NoteInterface m_walletInterface = null;

    private SimpleStringProperty m_currentAddress = new SimpleStringProperty(null);
    private SimpleObjectProperty<JsonObject> m_balanceObject = new SimpleObjectProperty<>(null);
    private SimpleStringProperty m_walletName = new SimpleStringProperty(null);

    private SimpleBooleanProperty m_isMarket =  new SimpleBooleanProperty(false);
    private SimpleBooleanProperty m_isTokenMarket = new SimpleBooleanProperty(false);

    private SimpleLongProperty m_marketQuotesUpdated = new SimpleLongProperty();
    private SimpleLongProperty m_tokenMarketQuotesUpdated = new SimpleLongProperty();

    private NoteMsgInterface m_walletMsgInterface = null;
   
   
    public ErgoWalletControl(String locationId, NoteInterface ergoNetworkInterface){
        m_locationId = locationId;
        m_ergoNetworkInterface = ergoNetworkInterface;

      
    }

    private String getLocationId(){
        return m_locationId;
    }

    public ReadOnlyStringProperty walletNameProperty(){
        return m_walletName;
    }

    public ReadOnlyStringProperty currentAddressProperty(){
        return m_currentAddress;
    }

    public ReadOnlyObjectProperty<JsonObject> balanceProperty(){
        return m_balanceObject;
    }

    public ReadOnlyBooleanProperty isMarketAvailable(){
        return m_isMarket;
    }

    public ReadOnlyBooleanProperty isTokenMarketAvailable(){
        return m_isTokenMarket;
    }

    public ReadOnlyLongProperty marketQuotesUpdated(){
        return m_marketQuotesUpdated;
    }

    public ReadOnlyLongProperty tokenMarketQuotesUpdate(){
        return m_tokenMarketQuotesUpdated;
    }

    public String getCurrentAddress(){
        return m_currentAddress.get();
    }

    public String getWalletName(){
        return m_walletName.get();
    }

    public Future<?> executeSimpleTransaction(long amountToSpendNanoErgs, long feeNanoErgs, PriceAmount[] tokens, JsonObject outputData, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonObject txData = new JsonObject();
        String currentAddressString = getCurrentAddress();
        String walletName = getWalletName();

        if(currentAddressString == null || walletName == null){
            return Utils.returnException("Wallet is locked", getExecService(), onFailed);
        }

           JsonObject balanceObject = m_balanceObject.get();
        
        if(balanceObject == null){
            return Utils.returnException("Balance is unvailable", getExecService(), onFailed);
        }

        ArrayList<PriceAmount> balanceList = NoteConstants.getBalanceList(balanceObject, true, getNetworkType());

        PriceAmount ergoBalance = NoteConstants.getPriceAmountFromList(balanceList, ErgoCurrency.TOKEN_ID);

        if(ergoBalance == null){
            return Utils.returnException("Ergo balance unavailable", getExecService(), onFailed);
        }

        if(ergoBalance.getLongAmount() < (amountToSpendNanoErgs + feeNanoErgs + ErgoCurrency.getNanoErgsFromErgs(NoteConstants.MIN_NETWORK_FEE))){
            return Utils.returnException("Insufficient ergo with network fee ("+NoteConstants.MIN_NETWORK_FEE+") and token housing ("+NoteConstants.MIN_NETWORK_FEE+")", getExecService(), onFailed);
        }


        if(tokens.length > 0){
            for(PriceAmount tokenAmount : tokens){
                PriceAmount tokenBalance = NoteConstants.getPriceAmountFromList(balanceList, ErgoCurrency.TOKEN_ID);
                if(tokenBalance == null){
                    return Utils.returnException(tokenAmount.getSymbol() + " not available in wallet.", getExecService(), onFailed);
                }
                if(tokenAmount.getLongAmount() > tokenBalance.getLongAmount()){
                    return Utils.returnException("Insufficient " + tokenAmount.getSymbol() + " in wallet (Balance: "+ tokenBalance.getBigDecimalAmount() +")", getExecService(), onFailed);
                }
            }
        }

        ErgoInputData inputData = new ErgoInputData(walletName, NoteConstants.CURRENT_WALLET_FILE, currentAddressString, amountToSpendNanoErgs, ErgoInputData.convertPriceAmountsToErgoTokens(tokens), feeNanoErgs, ErgoInputData.ASSETS_INPUT, ErgoInputData.FEE_INPUT, ErgoInputData.CHANGE_INPUT);

        NoteConstants.addSingleInputToDataObject(inputData, txData);

      
        if(outputData == null){
            return Utils.returnException("No ouptut data provided", getExecService(), onFailed);
        }
        JsonArray outputs = new JsonArray();
        outputs.add(outputData);
 
        NoteConstants.addNetworkTypeToDataObject(getNetworkType(), txData);

        txData.add("outputs", outputs);

        return sendNoteData("executeTransaction", txData,  onSucceeded, onFailed);
    }

    private ExecutorService getExecService(){
        return m_ergoNetworkInterface.getNetworksData().getExecService();
    }

    public Future<?> sendNoteData(String cmd, JsonObject noteData, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        if(m_accessId == null){
            return Utils.returnException("No access to wallet", getExecService(), onFailed);
        }

        if(m_locationId == null){
            return Utils.returnException("Location is not initialized", getExecService(), onFailed);
        }

        JsonObject note = NoteConstants.getCmdObject(cmd);
        note.addProperty("accessId", m_accessId);
        note.addProperty("locationId", m_locationId);
        note.add("data", noteData);

        NoteInterface walletInterface = m_walletInterface;
        if(walletInterface != null){
            return walletInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException("Wallet unavilable", getExecService(), onFailed);
        }
   }



   

    

    public void connectToWallet(){
        NoteInterface walletInterface = m_walletInterface;
        if(walletInterface != null && m_accessIdFuture == null || (m_accessIdFuture != null && m_accessIdFuture.isDone())){
            JsonObject getWalletObject = NoteConstants.getCmdObject("getAccessId");
            getWalletObject.addProperty("locationId", getLocationId());

            m_accessIdFuture = walletInterface.sendNote(getWalletObject, onSucceeded->{
                Object successObject = onSucceeded.getSource().getValue();

                if (successObject != null) {
                    JsonObject json = (JsonObject) successObject;
                    // addressesDataObject.set(json);
                    JsonElement codeElement = json.get("code");
                    JsonElement accessIdElement = json.get("accessId");


                    if(accessIdElement != null && codeElement == null){
                    
                        m_accessId = accessIdElement.getAsString();
                        

                        m_walletMsgInterface = new NoteMsgInterface() {

                            public String getId() {
                                return m_accessId;
                            }
                            @Override
                            public void sendMessage(int code, long timestamp,String networkId, Number num) {
                                if(networkId != null){
                                    if(networkId.startsWith(NoteConstants.MARKET_NETWORK + ":")){
                                        boolean status = getMarketConnectionStatus() == NoteConstants.STARTED;
                                        if(m_isMarket.get() != status){
                                            m_isMarket.set(status);
                                        }
                                        if(code == NoteConstants.LIST_CHANGED || code == NoteConstants.LIST_UPDATED){
                                            m_marketQuotesUpdated.set(timestamp);
                                        }
                                        
                                    }else if(networkId.startsWith(NoteConstants.TOKEN_MARKET_NETWORK + ":")){
                                        boolean status = getTokenMarketConnectionStatus() == NoteConstants.STARTED;
                                        if(m_isTokenMarket.get() != status){
                                            m_isTokenMarket.set(status);
                                        }
                                        if(code == NoteConstants.LIST_CHANGED || code == NoteConstants.LIST_UPDATED){
                                            m_tokenMarketQuotesUpdated.set(timestamp);
                                        }
                                    }
                                }
                            }

                            public void sendMessage(int code, long timestamp,String networkId, String msg) {
                            
                                String currentAddress = m_currentAddress.get();
                                    
                                switch (code) {
                                    case NoteConstants.UPDATED:
                                        if (networkId != null && currentAddress != null && networkId.equals(currentAddress)) {
                                            updateBalance();
                                        }
                                        break;
                            
                                }
                            }
                        };
                       
                        walletInterface.addMsgListener(m_walletMsgInterface);
                        boolean status = getTokenMarketConnectionStatus() == NoteConstants.STARTED;
                        if(m_isTokenMarket.get() != status){
                            m_isTokenMarket.set(status);
                        }
                        openWallet();
                    }
                } 
            }, onFailed->{});
        }
    }



    private void openWallet(){
    
        JsonArray addressesArray = getAddresses();
        JsonElement addressJsonElement = addressesArray != null ? addressesArray.get(0) : null;
        JsonObject adr0 = addressJsonElement != null && !addressJsonElement.isJsonNull() && addressJsonElement.isJsonObject() ? addressJsonElement.getAsJsonObject() : null;
        JsonElement addressElement = adr0 != null ? adr0.get("address") : null;
        String address = addressElement != null ? addressElement.getAsString() : null;

        if(address != null){
            m_currentAddress.set(address);
            updateBalance();
        }else{
            disconnectWallet();
        }
    
    }

    public boolean isAddressInWallet(String walletAddress){
        JsonArray addressesArray = getAddresses();

        if(addressesArray != null){
            int size = addressesArray.size();
            for(int i = 0; i < size ; i++){
                JsonElement addressJsonElement = addressesArray.get(i);
                JsonObject adressObject = addressJsonElement != null && !addressJsonElement.isJsonNull() && addressJsonElement.isJsonObject() ? addressJsonElement.getAsJsonObject() : null;
                JsonElement addressElement = adressObject != null ? adressObject.get("address") : null;
                String address = addressElement != null ? addressElement.getAsString() : null;
                if(address != null && address.equals(walletAddress)){
                    return true;
                }
            }
        }
        return false;
    }

    public void setCurrentAddress(String address){
        if(isAddressInWallet(address)){
            m_currentAddress.set(address);
        }
    }

    public JsonArray getWallets(){
        NoteInterface ergoNetworkInterface = m_ergoNetworkInterface;
        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getWallets");
            note.addProperty("networkId", NoteConstants.WALLET_NETWORK);
            note.addProperty("locationId", getLocationId());

            Object obj = ergoNetworkInterface.sendNote(note);
            if(obj != null && obj instanceof JsonArray){
                return (JsonArray) obj;
            }
        }
        return null;
    }

    public void removeWallets(JsonArray removeIds){
        JsonObject note = NoteConstants.getCmdObject("removeWallet");
        note.addProperty("locationId", m_locationId);
        note.addProperty("networkId", NoteConstants.WALLET_NETWORK);
        note.add("ids", removeIds);
        
        m_ergoNetworkInterface.sendNote(note);
    }

    public JsonArray getAddresses(){
        NoteInterface walletInterface = m_walletInterface;
        String accessId = m_accessId;
        if(accessId != null &&  walletInterface != null){
            JsonObject json = NoteConstants.getCmdObject("getAddresses");
            json.addProperty("accessId", accessId);
            json.addProperty("locationId", getLocationId());

            Object successObject = walletInterface.sendNote(json);

            if (successObject != null && successObject instanceof JsonArray) {
                JsonArray jsonArray = (JsonArray) successObject;
                if (jsonArray.size() > 0) {
                    return jsonArray;
                }
            }
        }
        return null;
    }
    
    public void updateBalance(){
        String accessId = m_accessId;
        String address = m_currentAddress.get();
        NoteInterface walletInterface = m_walletInterface;
        if(accessId != null && address != null && walletInterface != null){
            
            JsonObject note = NoteConstants.getCmdObject("getBalance");
            note.addProperty("locationId", getLocationId());
            note.addProperty("accessId", accessId);
            note.addProperty("address", address);
            Object obj = walletInterface.sendNote(note);
            
            JsonObject balanceObject = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
            
            m_balanceObject.set(balanceObject);
        }else{
            m_balanceObject.set(null);
        }
    }



    public NetworkType getNetworkType(){
        String accessId = m_accessId;
        NoteInterface walletInterface = m_walletInterface;

        JsonObject note = NoteConstants.getCmdObject("getNetworkType");
        note.addProperty("locationId", getLocationId());
        note.addProperty("accessId", accessId);
        Object obj = walletInterface.sendNote(note);

        if(obj != null && obj instanceof NetworkType){
            return (NetworkType) obj;
        }
        return null;
    }

    public void setDefaultWallet(String walletId){
        m_balanceObject.set(null);
        JsonObject getWalletObject = NoteConstants.getCmdObject(  walletId != null ?"setDefault" : "clearDefault");
   
        getWalletObject.addProperty("locationId", m_locationId);
        getWalletObject.addProperty("networkId", NoteConstants.WALLET_NETWORK);
        
        if(walletId != null){
            getWalletObject.addProperty("id", walletId);
        }

        m_ergoNetworkInterface.sendNote(getWalletObject);

    }

    public void clearDefault(){
        m_balanceObject.set(null);
        JsonObject setDefaultObject = NoteConstants.getCmdObject("clearDefault");
        setDefaultObject.addProperty("networkId", NoteConstants.WALLET_NETWORK);
        setDefaultObject.addProperty("locationId", m_locationId);
        m_ergoNetworkInterface.sendNote(setDefaultObject);
    }

    
    public void getDefaultWallet(){
        NoteInterface ergoNetworkInterface = m_ergoNetworkInterface;
         
        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getDefaultInterface");
            note.addProperty("networkId", NoteConstants.WALLET_NETWORK);
            note.addProperty("locationId", getLocationId());
           
          
            Object obj = ergoNetworkInterface.sendNote(note);
            NoteInterface walletInterface = obj != null && obj instanceof NoteInterface ? (NoteInterface) obj : null;
            setWalletInterface(walletInterface);
        }else{
            setWalletInterface((NoteInterface) null);
        }
        
    }

    public void showWalletMnemonic(){
        showWalletMnemonic(onSucceeded->{}, onFailed->{});
    }

    public void showWalletMnemonic(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface walletInterface = m_walletInterface;
        if(walletInterface != null){
            JsonObject note = NoteConstants.getCmdObject("viewWalletMnemonic");
            note.addProperty("locationId", m_locationId);
            note.addProperty("accessId", m_accessId);

            walletInterface.sendNote(note, onSucceeded, onFailed);
        }
    }

    private void setWalletInterface(NoteInterface walletInterface){
        if(m_walletInterface != null){
            disconnectWallet();
        }
        m_walletInterface = walletInterface;
        m_walletName.set(m_walletInterface != null ? m_walletInterface.getName() : null);

        if(getTokenMarketConnectionStatus() == NoteConstants.STARTED){
            m_isTokenMarket.set(true);
        }

        if(getMarketConnectionStatus() == NoteConstants.STARTED){
            m_isMarket.set(true);
        }

    }

    public Future<?> getBox(String boxId, EventHandler<WorkerStateEvent> onComplete, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = m_ergoNetworkInterface;

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getBox");
            note.addProperty("networkId", NoteConstants.EXPLORER_NETWORK);
            note.addProperty("locationId", getLocationId());
            note.addProperty("value", boxId);
            return ergoNetworkInterface.sendNote(note, onComplete, onFailed);
        }

        return null;
    }

    public Future<?> getTokenInfo(String tokenId, EventHandler<WorkerStateEvent> onComplete, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = m_ergoNetworkInterface;

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getTokenInfo");
            note.addProperty("networkId", NoteConstants.EXPLORER_NETWORK);
            note.addProperty("locationId", getLocationId());
            note.addProperty("tokenId", tokenId);
            return ergoNetworkInterface.sendNote(note, onComplete, onFailed);
        }

        return null;
    }



    public void setWalletInterface(String id){
        NoteInterface ergoNetworkInterface = m_ergoNetworkInterface;

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getWalletInterface");
            note.addProperty("networkId", NoteConstants.WALLET_NETWORK);
            note.addProperty("locationId", getLocationId());
            note.addProperty("id", id);
            Object obj = ergoNetworkInterface.sendNote(note);
            NoteInterface walletInterface = obj != null && obj instanceof NoteInterface ? (NoteInterface) obj : null;

            setWalletInterface(walletInterface);
        }else{
            setWalletInterface((NoteInterface) null);
        }
        
    }

    public void openWalletFile(File walletFile, NetworkType networkType){

        JsonObject note = NoteConstants.getCmdObject("openWallet");
        note.addProperty("networkId", NoteConstants.WALLET_NETWORK);
        note.addProperty("locationId", m_locationId);

        JsonObject walletData = new JsonObject();
        walletData.addProperty("networkType", networkType.toString());
        walletData.addProperty("path", walletFile.getAbsolutePath());

        note.add("data", walletData);
        Object result = m_ergoNetworkInterface.sendNote(note);

        if (result != null && result instanceof JsonObject) {
            JsonObject walletJson = (JsonObject) result;
            JsonElement idElement = walletJson.get("id");
            if (idElement != null && idElement.isJsonPrimitive()) {
                
                setDefaultWallet(idElement.getAsString());
                
            }
                
        }

    }

 

    public void walletRemoved(String msg){
        String walletId = m_walletInterface != null ? m_walletInterface.getNetworkId() : null;
        if(walletId != null){
            JsonParser jsonParser = msg != null ? new JsonParser() : null;
            JsonElement jsonElement = jsonParser != null ? jsonParser.parse(msg) : null;
            JsonObject json = jsonElement != null && !jsonElement.isJsonNull() && jsonElement.isJsonObject() ? jsonElement.getAsJsonObject() : null;
            JsonElement idsElement = json != null ? json.get("ids") : null;
            JsonArray idsArray = idsElement != null && !idsElement.isJsonNull() && idsElement.isJsonArray() ? idsElement.getAsJsonArray() : null;
            
            if(idsArray != null){
                int idsArraySize = idsArray.size();
                
                for(int i = 0; i < idsArraySize ; i++){
                    JsonElement idElement = idsArray.get(i);

                    String id = idElement != null && !idElement.isJsonNull() && idElement.isJsonPrimitive() ? idElement.getAsString() : null;

                    if(id != null && id.equals(walletId)){
                        clearWallet();
                        return;
                    }
                }

            }
        }
    }

    public void copyCurrentAddressToClipboard(Control control){
        String adrText = m_currentAddress.get();
        if(adrText != null){

            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(adrText);
            clipboard.setContent(content);

            Point2D p = control.localToScene(0.0, 0.0);
            if(control.tooltipProperty().get() != null){
                control.tooltipProperty().get().hide();
            }

            Tooltip copiedTooltip = new Tooltip("copied");

            copiedTooltip.show(
                control,  
                p.getX() + control.getScene().getX() + control.getScene().getWindow().getX(), 
                (p.getY()+ control.getScene().getY() + control.getScene().getWindow().getY())-control.getLayoutBounds().getHeight()
            );
            PauseTransition pt = new PauseTransition(Duration.millis(1600));
            pt.setOnFinished(ptE->{
                copiedTooltip.hide();
            });
            pt.play();
        }
    }

    public void showAddressStage(){
        String adrText = m_currentAddress.get();
        if(adrText != null){
            Stages.showMagnifyingStage("Wallet: " + m_walletName.get() +" - Address: " + adrText, adrText);
        }
    }


    public Future<?> getTransactionViews( EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){

        if(isUnlocked()){
         
            JsonObject note = NoteConstants.getCmdObject("getTransactionViews");
            note.addProperty("locationId", m_locationId);
            note.addProperty("accessId", m_accessId);
            note.addProperty("address", getCurrentAddress());
            return m_walletInterface.sendNote(note, onSucceeded, onFailed);
            
        }else{
            Utils.returnException("Wallet is locked", getExecService(), onFailed);
            return null;
        }
    }

    public Future<?> updateBoxInfo(String txId, ErgoBoxInfo boxInfo,  EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        if(isUnlocked()){
         
            JsonObject note = NoteConstants.getCmdObject("updateAddressBoxInfo");
            note.addProperty("locationId", m_locationId);
            note.addProperty("accessId", m_accessId);
            JsonObject dataObject = new JsonObject();
            dataObject.addProperty("txId", txId);
            dataObject.addProperty("address", getCurrentAddress());
            dataObject.add("boxInfo", boxInfo.getJsonObject());
            note.add("data", dataObject);
            return m_walletInterface.sendNote(note, onSucceeded, onFailed);
            
        }else{
            Utils.returnException("Wallet is locked", getExecService(), onFailed);
            return null;
        }
    }

    public Future<?> reclaimBox(String txId, String boxId, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){

        if(isUnlocked()){
         
            JsonObject note = NoteConstants.getCmdObject("reclaimBox");
            note.addProperty("locationId", m_locationId);
            note.addProperty("accessId", m_accessId);
            JsonObject dataObject = new JsonObject();
            dataObject.addProperty("txId", txId);
            dataObject.addProperty("address", getCurrentAddress());
            dataObject.addProperty("boxId", boxId);

            note.add("data", dataObject);
            return m_walletInterface.sendNote(note, onSucceeded, onFailed);
            
        }else{
            Utils.returnException("Wallet is locked", getExecService(), onFailed);
            return null;
        }
    }

    
    public void setErgoNetworkInterface(NoteInterface ergoNetworkInterface){
        clearWallet();
        m_ergoNetworkInterface = ergoNetworkInterface;
        if(ergoNetworkInterface != null){
            getDefaultWallet();
        }
    }

    public NoteInterface getMarketInterface(){
        JsonObject note = NoteConstants.getCmdObject("getDefaultMarketInterface");
        note.addProperty("networkId", NoteConstants.MARKET_NETWORK);
        note.addProperty("locationId", getLocationId());
        Object obj = m_ergoNetworkInterface != null ? m_ergoNetworkInterface.sendNote(note) : null;
        
         return (obj != null && obj instanceof NoteInterface ? (NoteInterface) obj : null);
    }

    public NoteInterface getTokenMarketInterface(){
        JsonObject note = NoteConstants.getCmdObject("getDefaultTokenInterface");
        note.addProperty("networkId", NoteConstants.MARKET_NETWORK);
        note.addProperty("locationId", getLocationId());
        Object obj = m_ergoNetworkInterface != null ? m_ergoNetworkInterface.sendNote(note) : null;
        
         return (obj != null && obj instanceof NoteInterface ? (NoteInterface) obj : null);
    }

    public int getMarketConnectionStatus(){
        NoteInterface marketInterface = getMarketInterface();
        if(marketInterface != null){
            return marketInterface.getConnectionStatus();
        }
        return -1;
    }

    public int getTokenMarketConnectionStatus(){
        NoteInterface tokenMarketInterface = getTokenMarketInterface();
        if(tokenMarketInterface != null){
            return tokenMarketInterface.getConnectionStatus();
        }
        return -1;
    }


    public JsonObject getAvailableTokenQuotes(NoteInterface tokenMarketInterface, int offset, int limit, String filter){
        if(tokenMarketInterface != null){
        
            JsonObject note = NoteConstants.getCmdObject("getAvailableQuotesInErg");
            note.addProperty("offset", offset);
            note.addProperty("limit", limit);
            if(filter != null && filter.length() > 0){
                note.addProperty("filter", filter);
            }
            note.addProperty("locationId", getLocationId());
            Object obj = tokenMarketInterface.sendNote(note);
            return (obj != null && obj instanceof JsonObject ? (JsonObject) obj : null);
        }
        return null;
    }



    public PriceQuote getTokenQuoteInErg(NoteInterface tokenMarketInterface, String tokenId){
        if(tokenMarketInterface != null){
        
            JsonObject note = NoteConstants.getCmdObject("getTokenQuoteInErg");
            note.addProperty("locationId", getLocationId());
            note.addProperty("tokenId", tokenId);
            Object obj = tokenMarketInterface.sendNote(note);
            return (obj != null && obj instanceof PriceQuote ? (PriceQuote) obj : null);
        }
        return null;
    }


    public NoteInterface getErgoNetworkInterface(){
        return m_ergoNetworkInterface;
    }
    

    public void clearWallet(){
        setWalletInterface((NoteInterface) null);
    }
    

    public void disconnectWallet(){
        if(m_accessIdFuture != null && (!m_accessIdFuture.isDone() || !m_accessIdFuture.isCancelled())){
            m_accessIdFuture.cancel(true);
        }
        if(m_walletInterface != null && m_walletMsgInterface != null){
            m_walletInterface.removeMsgListener(m_walletMsgInterface);
        }      

        m_currentAddress.set(null);
        m_balanceObject.set(null);
        m_accessId = null;
    }

    public boolean isUnlocked(){
        return m_accessId != null && m_walletInterface != null;
    }

    public NetworksData getNetworksData(){
        return m_ergoNetworkInterface.getNetworksData();
    }



    public void shutdown(){
        clearWallet();
    }

   
}
