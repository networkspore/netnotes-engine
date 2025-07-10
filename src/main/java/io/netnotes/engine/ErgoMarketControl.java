package io.netnotes.engine;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.apps.AppConstants;
import io.netnotes.engine.apps.ergoDex.ErgoDex;
import io.netnotes.engine.networks.ergo.ErgoNetwork;
import io.netnotes.friendly_id.FriendlyId;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.MenuItem;
import javafx.scene.image.ImageView;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.scene.image.Image;
import javafx.scene.control.Tooltip;

public class ErgoMarketControl {

    private static final String NETWORK_ID = "ERGO_MARKET_CONTROL";
    private final NetworksData m_networksData;
    private final String m_networkId;
    
    private String m_locationId;
    private SimpleObjectProperty<JsonObject> m_marketObject = new SimpleObjectProperty<>(null);
    private SimpleObjectProperty<JsonObject> m_tokenMarketObject = new SimpleObjectProperty<>(null);

    private NoteInterface m_marketInterface = null;
    private NoteInterface m_tokenMarketInterface = null;
   
    private NoteMsgInterface m_marketMsgInterface = null;
    private NoteMsgInterface m_tokenMarketMsgInterface = null;

    private SimpleBooleanProperty m_marketAvailable = new SimpleBooleanProperty();
    private SimpleBooleanProperty m_tokenMarketAvailable = new SimpleBooleanProperty();

    private SimpleLongProperty m_marketLastUpdated = new SimpleLongProperty();
    private SimpleLongProperty m_tokenMarketLastUpdated = new SimpleLongProperty();

    private Future<?> m_tokenConnectFuture = null;
    private Future<?> m_connectFuture = null;

    private SimpleStringProperty m_marketIdProperty = new SimpleStringProperty();
    private SimpleStringProperty m_tokenMarketIdProperty = new SimpleStringProperty();

    private int m_marketConnections = 0;
    private int m_tokenMarketConnections = 0;

    private SimpleLongProperty m_appsChecked = new SimpleLongProperty();

    public ErgoMarketControl(String networkId, String locationId, NetworksData networksData){
        m_networkId = networkId;
        m_networksData = networksData;
        m_locationId = locationId;
        getData();
    }


    protected void getData(){
        if(m_networkId != null){
            m_networksData.getData("data", m_networkId, NETWORK_ID, ErgoNetwork.NETWORK_ID, (onComplete)->{
                Object obj = onComplete.getSource().getValue();
                JsonObject json = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
                openJson(json); 
            });
        }
    }

    protected void openJson(JsonObject json){
        JsonElement marketIdElement = json != null ? json.get("marketId") : null;
        JsonElement tokenMarketIdElement = json != null ? json.get("tokenMarketId") : null;

        String marketId = marketIdElement != null ? (marketIdElement.isJsonNull() ? null : marketIdElement.getAsString() ) : ErgoDex.NETWORK_ID;
        String tokenMarketId = tokenMarketIdElement != null ? (tokenMarketIdElement.isJsonNull() ? null : tokenMarketIdElement.getAsString()) : ErgoDex.NETWORK_ID;
        
        m_marketIdProperty.set(marketId);
        if(marketId != null){
            connectToMarket();
        }
        m_tokenMarketIdProperty.set(tokenMarketId);
        if(tokenMarketId != null){
            connectToTokenMarket();
        }
    }

    protected void save(){
        if(m_networkId != null){
            m_networksData.save("data", m_networkId, NETWORK_ID, ErgoNetwork.NETWORK_ID, getJsonObject());
        }
    }

    public JsonObject getJsonObject(){
        JsonObject json = new JsonObject();
        json.addProperty("marketId", getMarketId());
        json.addProperty("tokenMarketId", getTokenMarketId()) ;
        return json;
    }

    public NetworksData getNetworksData(){
        return m_networksData;
    }

    public ExecutorService getExecService(){
        return getNetworksData().getExecService();
    }

    private String getLocationId(){
        return m_locationId;
    }
    public String getMarketId(){
        return m_marketIdProperty.get();
    }

    public String getTokenMarketId(){
        return m_tokenMarketIdProperty.get();
    }

    public void setMarketId(String marketId){
        if(m_marketIdProperty.get() != null){
            disconnectMarket();
        }
        m_marketIdProperty.set(marketId);
        if(marketId != null){
            connectToMarket();
        }
        save();
    }

    public void setTokenMarketId(String tokenMarketId){
        if(m_tokenMarketIdProperty.get() != null){
            disconnectMarket();
        }
        m_tokenMarketIdProperty.set(tokenMarketId);
        if(tokenMarketId != null){
            connectToTokenMarket();
        }
        save();
    }

    private final String marketMenuKey = "marketMenuKey";
    private final String manageAppsKey = "manageAppsKey";
    public static final Image UNAVAILABLE_ICON = new Image(AppConstants.UNAVAILBLE_ICON);
    
    public static final String DISABLED_MARKET_TEXT = "Ergo Market: (disabled)";
    public static final String UNAVAILBLE_MARKET_TEXT = "Ergo Market: (unavailable)";
    public static final String UNKNOWN_MARKET_TEXT = "Ergo Market: (information unavailable)";

    public static final String DISABLED_TOKEN_MARKET_TEXT = "Token Market: (disabled)";
    public static final String UNAVAILBLE_TOKEN_MARKET_TEXT = "Token Market: (unavailable)";
    public static final String UNKNOWN_TOKEN_MARKET_TEXT = "Token Market: (information unavailable)";


    
    public void updateMarketMenu( boolean isTokenMarket, ObservableList<MenuItem> marketMenu, Tooltip tooltip, ImageView imageView){
        long timeStamp = System.currentTimeMillis();
        String marketId = isTokenMarket ? getTokenMarketId() : getMarketId();
        NoteInterface networkInterface = isTokenMarket ? getTokenMarketInterface() : getMarketInterface();

        KeyMenu isMenuItem = KeyMenu.getKeyMenu(marketMenu, marketMenuKey);
        
        KeyMenu marketMenuItem = isMenuItem != null ? isMenuItem : new KeyMenu(marketMenuKey, isTokenMarket ? UNAVAILBLE_MARKET_TEXT : UNAVAILBLE_MARKET_TEXT, timeStamp, KeyMenu.VALUE_NOT_KEY);

        if(isMenuItem == null){
            marketMenu.add(marketMenuItem);
        }

        if(marketId != null){

            if(networkInterface != null){
                JsonObject networkObject = isTokenMarket ? m_tokenMarketObject.get() : m_marketObject.get();

                String name = NoteConstants.getNameFromNetworkObject(networkObject);

                NoteConstants.getAppIconFromNetworkObject(networkObject, getExecService(), onImage->{
                    Object imgObj = onImage.getSource().getValue();
                    if(imgObj != null && imgObj instanceof Image){
                        
                        setMenuMsg(imageView, (Image) imgObj, name, marketMenuItem, tooltip, timeStamp);
                    }else{
                        setMenuMsg(imageView, Stages.unknownImg, name, marketMenuItem, tooltip, timeStamp);
                    }
                }, onImageFailed->{
                     setMenuMsg(imageView, Stages.unknownImg, name, marketMenuItem, tooltip, timeStamp);
                });
                
                
            }else{
                setMenuMsg(imageView, UNAVAILABLE_ICON, isTokenMarket ? UNAVAILBLE_TOKEN_MARKET_TEXT : UNAVAILBLE_MARKET_TEXT,marketMenuItem, tooltip, timeStamp);
            }
        }else{
            setMenuMsg(imageView,UNAVAILABLE_ICON,isTokenMarket ? DISABLED_TOKEN_MARKET_TEXT : DISABLED_MARKET_TEXT, marketMenuItem, tooltip, timeStamp);
        }

        Utils.removeOldKeys(marketMenu, timeStamp);

        List<NetworkInformation> networkList = isTokenMarket ? getNetworksData().getNetworksContainsAllKeyWords("market", "ergo tokens") : getNetworksData().getNetworksContainsAllKeyWords("ergo", "market");

        for(NetworkInformation networkInfo : networkList){
            KeyMenuItem currentItem = KeyMenuItem.getKeyMenuItem(marketMenuItem.getItems(), networkInfo.getNetworkId());

            String name = (marketId != null && networkInfo.getNetworkId().equals(marketId) ? "* ": "  ") + networkInfo.getNetworkName();

            if(currentItem != null){
                currentItem.setValue(name, timeStamp);
            }else{
                KeyMenuItem newItem = new KeyMenuItem(networkInfo.getNetworkId(), name, timeStamp, KeyMenuItem.VALUE_NOT_KEY);
                newItem.setOnAction(e->{
                    if(marketId == null || (marketId != null && !marketId.equals(networkInfo.getNetworkId()))){
                        if(isTokenMarket){
                            setTokenMarketId(networkInfo.getNetworkId());
                        }else{
                            setMarketId(networkInfo.getNetworkId());
                        }
                    }
                    updateMarketMenu(isTokenMarket, marketMenu, tooltip, imageView);
                });
                marketMenuItem.getItems().add(newItem);
            }
        }
        KeyMenuItem isManageAppssItem = KeyMenuItem.getKeyMenuItem(marketMenuItem.getItems(), manageAppsKey);

        KeyMenuItem manageApps = isManageAppssItem != null ? isManageAppssItem : new KeyMenuItem(manageAppsKey, "Manage apps…", timeStamp, KeyMenuItem.VALUE_NOT_KEY);
        if(isManageAppssItem == null){
            manageApps.setOnAction(e->{
                getNetworksData().openStatic(NetworksData.APPS);
            });
            marketMenuItem.getItems().add(manageApps);
        }else{
            manageApps.setTimeStamp(timeStamp);
        }
        
        Utils.removeOldKeys(marketMenuItem.getItems(), timeStamp);
    }

    
    protected void updateNetworkObject(boolean isTokenMarket, NoteInterface marketInterface){

        getNetworkObject(marketInterface, onNetworkObject->{
            Object obj = onNetworkObject.getSource().getValue();
    
            if(isTokenMarket){
                m_tokenMarketObject.set(obj != null && obj instanceof JsonObject ? (JsonObject) obj : null);
            }else{
                m_marketObject.set(obj != null && obj instanceof JsonObject ? (JsonObject) obj : null);
            }

        }, onFailed->{
            
            if(isTokenMarket){
                m_tokenMarketObject.set( null);
            }else{
                m_marketObject.set(null);
            }
        });
    
    }

    public Future<?> getNetworkObject(NoteInterface marketInterface, EventHandler<WorkerStateEvent> onSucceess, EventHandler<WorkerStateEvent> onFailed){
        
        JsonObject note = NoteConstants.getCmdObject("getNetworkObject", m_locationId);

        return marketInterface.sendNote(note, onSucceess, onFailed);
    }


    
    private void setMenuMsg(ImageView imageView, Image img, String msg, KeyMenu keyMenu, Tooltip tooltip, long timeStamp){
        imageView.setImage(Stages.unknownImg);
        if(keyMenu.getTimeStamp() < timeStamp){
            keyMenu.setValue(msg, timeStamp);
            tooltip.setText(msg);
        }
    }

    public int getTokenMarketConnectionStatus(){
        long timeStamp = m_tokenMarketLastUpdated.get();
        boolean isAvailable = getTokenMarketNetworkObject() != null;
        boolean isInterface = getTokenMarketInterface() != null;
        boolean isEnabled = getTokenMarketId() != null;

        if(timeStamp > 0 && isAvailable && isInterface && isEnabled){

            if((System.currentTimeMillis() - timeStamp) < (ErgoDex.ONE_SECOND_MILLIS * 3)){
                return NoteConstants.READY;
            }else{
                return NoteConstants.WARNING;
            }
        }else{
            if(isAvailable || isInterface){
                return NoteConstants.STARTING;
            }else if(!isEnabled){
                return NoteConstants.DISABLED;
            }else{
                return NoteConstants.STOPPED;
            }
        }
    }

    public int getMarketConnectionStatus(){
        long timeStamp = m_marketLastUpdated.get();
        boolean isAvailable = getMarketNetworkObject() != null;
        boolean isInterface = getMarketInterface() != null;
        boolean isEnabled = getMarketId() != null;

        if(timeStamp > 0 && isAvailable && isInterface && isEnabled){

            if((System.currentTimeMillis() - timeStamp) < (ErgoDex.ONE_SECOND_MILLIS * 3)){
                return NoteConstants.READY;
            }else{
                return NoteConstants.WARNING;
            }
        }else{
            if(isAvailable || isInterface){
                return NoteConstants.STARTING;
            }else if(!isEnabled){
                return NoteConstants.DISABLED;
            }else{
                return NoteConstants.STOPPED;
            }
        }
    }

    public void checkAvailablility(){
        NetworksData networksData = getNetworksData();
        
        String marketId = getMarketId();
        String tokenMarketId = getTokenMarketId();

        boolean marketChanged = marketId != null && networksData.getApp(marketId) == null;
        boolean tokenMarketChanged = tokenMarketId != null && networksData.getApp(tokenMarketId) == null;

        if(marketChanged){
            disconnectMarket();
        }
        if(tokenMarketChanged){
            disconnectTokenMarket();
        }
        m_appsChecked.set(System.currentTimeMillis());
    }

    public ReadOnlyLongProperty appsChecked(){
        return m_appsChecked;
    }
 

    public ReadOnlyBooleanProperty isMarketAvailableProperty(){
        return m_marketAvailable;
    }

    public ReadOnlyBooleanProperty isTokenMarketAvailableProperty(){
        return m_tokenMarketAvailable;
    }

    public boolean isMarketAvailable(){
        return m_marketAvailable.get();
    }

    public boolean isTokenMarketAvailable(){
        return m_tokenMarketAvailable.get();
    }


    public ReadOnlyObjectProperty<JsonObject> marketObjectProperty(){
        return m_marketObject;
    }

    public ReadOnlyObjectProperty<JsonObject> tokenMarketObjectProperty(){
        return m_tokenMarketObject;
    }

    public ReadOnlyLongProperty marketLastUpdated(){
        return m_marketLastUpdated;
    }


    public ReadOnlyLongProperty tokenMarketLastUpdated(){
        return m_tokenMarketLastUpdated;
    }

    public NoteInterface getMarketInterface(){
        String marketId = getMarketId();
        if(m_marketInterface == null && marketId != null){
            m_marketInterface = getNetworksData().getApp(marketId);
            return m_marketInterface;
        }else{
            if(getNetworksData().getApp(marketId) != null){
                return m_marketInterface;
            }else{
                disconnectMarket();
            }
        }
        return null;
    }

    public NoteInterface getTokenMarketInterface(){
        String tokenMarketId = getTokenMarketId();
        if(m_tokenMarketInterface == null && tokenMarketId != null){
            m_tokenMarketInterface = getNetworksData().getApp(tokenMarketId);
            return m_marketInterface;
        }else if(m_tokenMarketInterface != null){
            if(getNetworksData().getApp(tokenMarketId) != null){
                return m_tokenMarketInterface;
            }else{
                disconnectTokenMarket();
            }
        }
        return null;
    }

    public JsonObject getMarketNetworkObject(){
        return m_marketObject.get();
    }

    public int getMarketConnections(){
        return m_marketConnections;
    }

    public void addMarketConnection(){
        m_marketConnections++;
        connectToMarket();
    }

    public void removeMarketConnection(){
        m_marketConnections--;
        if( m_tokenMarketConnections == 0){
            disconnectMarket();
        }
    }

    public int getTokenMarketConnections(){
        return m_tokenMarketConnections;
    }

    public void addTokenMarketConnection(){
        m_tokenMarketConnections++;
        connectToTokenMarket();
    }

    public void removeTokenMarketConnection(){
        m_tokenMarketConnections--;
        if( m_tokenMarketConnections == 0){
            disconnectTokenMarket();
        }
    }


    private void connectToMarket() {
        if(m_marketConnections > 0){
            NoteInterface marketInterface = getMarketInterface();
            if (marketInterface != null && m_marketMsgInterface == null && m_connectFuture == null || (marketInterface != null && (m_connectFuture != null && m_connectFuture.isDone()) && getMarketNetworkObject() == null)) {
                if(m_marketMsgInterface != null){
                    marketInterface.removeMsgListener(m_marketMsgInterface);
                    m_marketMsgInterface = null;
                }

                m_connectFuture = marketInterface.sendNote(NoteConstants.getCmdObject("getNetworkObject", getLocationId()), onSucceeded -> {
                    Object successObject = onSucceeded.getSource().getValue();

                    if (successObject != null) {
                        JsonObject networkObject = (JsonObject) successObject;
                        // addressesDataObject.set(json);
                    
            
                        m_marketMsgInterface = new NoteMsgInterface() {
                            private String interfaceId = FriendlyId.createFriendlyId();
                            @Override
                            public String getId() {
                                return interfaceId;
                            }

                            @Override
                            public void sendMessage(int code, long timestamp, String networkId, String msg) {
                                 switch (code) {
                                    case NoteConstants.LIST_UPDATED:
                                    case NoteConstants.LIST_CHANGED:
                                        m_marketLastUpdated.set(timestamp);
                                        m_marketAvailable.set(true);
                                    break;

                                }
                            }
                        };
                        marketInterface.addMsgListener(m_marketMsgInterface);
                        
                        m_marketObject.set(networkObject);
                    
                        
                    }
                }, onFailed -> {
                    disconnectMarket();
                });
            }
        }else{
            disconnectMarket();
        }
        
    }

    public JsonObject getTokenMarketNetworkObject(){
        return m_tokenMarketObject.get();
    }

    private void connectToTokenMarket(){
        connectToTokenMarket(null);
    }

    private void connectToTokenMarket(EventHandler<WorkerStateEvent> onFailed) {
        if(m_tokenMarketConnections > 0 ){
            NoteInterface tokenMarketInterface = getTokenMarketInterface();

            if (tokenMarketInterface != null && m_tokenMarketMsgInterface == null && m_tokenConnectFuture == null || (m_tokenConnectFuture != null && m_tokenConnectFuture.isDone()) && getTokenMarketNetworkObject() == null) {
                

                m_tokenConnectFuture = tokenMarketInterface.sendNote(NoteConstants.getCmdObject("getNetworkObject", getLocationId()), onSucceeded -> {
                    Object successObject = onSucceeded.getSource().getValue();

                    if (successObject != null) {
                        JsonObject networkObject = (JsonObject) successObject;
                        // addressesDataObject.set(json);
                    
            
                        m_tokenMarketMsgInterface = new NoteMsgInterface() {
                            private String interfaceId = FriendlyId.createFriendlyId();
                            @Override
                            public String getId() {
                                return interfaceId;
                            }

                            @Override
                            public void sendMessage(int code, long timestamp, String networkId, String msg) {
                                switch (code) {
                                    case NoteConstants.LIST_UPDATED:
                                    case NoteConstants.LIST_CHANGED:
                                        m_tokenMarketLastUpdated.set(timestamp);
                                    break;

                                }
                            }

                        };
                        tokenMarketInterface.addMsgListener(m_tokenMarketMsgInterface);
                        
                        m_tokenMarketObject.set(networkObject);
                    
                        
                    }
                }, onError -> {
                    disconnectTokenMarket();
                    if(onFailed != null){
                        Throwable throwable = onError.getSource().getException();
                        Exception err = throwable != null && throwable instanceof Exception ? (Exception) throwable : null;
                        if(err != null){
                            Utils.returnException(err, getExecService(), onFailed);
                        }else{
                            Utils.returnException(throwable != null ? throwable.getMessage() : NoteConstants.ERROR_INVALID, getExecService(), onFailed);
                        }
                    }
                });
            }
        }else{
            disconnectTokenMarket();
        }
        
    }


    private void disconnectMarket() {
        if (m_connectFuture != null && (!m_connectFuture.isDone() || !m_connectFuture.isCancelled())) {
            m_connectFuture.cancel(true);
        }
        if (m_marketInterface != null && m_marketMsgInterface != null) {
            m_marketInterface.removeMsgListener(m_marketMsgInterface);
            m_marketAvailable.set(false);
        }
        m_marketInterface = null;
        m_marketMsgInterface = null;
        m_marketObject.set(null);
    }

    private void disconnectTokenMarket() {
        if (m_tokenConnectFuture != null && (!m_tokenConnectFuture.isDone() || !m_tokenConnectFuture.isCancelled())) {
            m_tokenConnectFuture.cancel(true);
        }
        if (m_tokenMarketInterface != null && m_tokenMarketMsgInterface != null) {
            m_tokenMarketInterface.removeMsgListener(m_tokenMarketMsgInterface);
        }
        m_tokenMarketInterface = null;
        m_tokenMarketMsgInterface = null;
        m_tokenMarketObject.set(null);
    }

    public Future<?> getAvailableTokenQuotes(int offset, int limit, String filter, 
    EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        
        NoteInterface tokenMarketInterface = getTokenMarketInterface();

        if (tokenMarketInterface != null) {

            JsonObject note = NoteConstants.getCmdObject("getAvailableQuotesInErg", getLocationId());
            note.addProperty("offset", offset);
            note.addProperty("limit", limit);
            if (filter != null && filter.length() > 0) {
                note.addProperty("filter", filter);
            }

            return tokenMarketInterface.sendNote(note, onSucceeded, onFailed);
        }else {
            return Utils.returnException("Token Market: (unavailable)", getExecService(), onFailed);
        }
    }


    
    public Future<?> getErgoUSDQuote(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface marketInterface = getMarketInterface();
        if(marketInterface != null){
          
            JsonObject note = NoteConstants.getCmdObject("getErgoUSDQuote", getLocationId());
            return marketInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(new NullPointerException("Ergo Market: (disabled)"), getExecService(), onFailed);
        }
    }

    public Future<?> getTokenQuoteInErg(String tokenId, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface tokenMarketInterface = getTokenMarketInterface();
        if(tokenMarketInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getTokenQuoteInErg", getLocationId());
            note.addProperty("tokenId",tokenId);
            return  tokenMarketInterface.sendNote(note, onSucceeded, onFailed);
            
        }else{
            return Utils.returnException(new NullPointerException("Market: (disabled)"), getExecService(), onFailed);
        }
    }

    public Future<?> getTokenArrayQuotesInErg(JsonArray tokenIds, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface tokenMarketInterface = getTokenMarketInterface();
        if(tokenMarketInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getTokenArrayQuotesInErg",  getLocationId());
            note.add("tokenIds",tokenIds);
            return  tokenMarketInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(new NullPointerException("Token Market: (Disabled)"), getExecService(), onFailed);
        }
    }



    public void shutdown(){
        disconnectMarket();
        disconnectTokenMarket();

    }
}
