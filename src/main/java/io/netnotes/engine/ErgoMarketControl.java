package io.netnotes.engine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
import javafx.beans.value.ChangeListener;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public class ErgoMarketControl {
    private static final String NETWORK_ID = "ERGO_MARKET_CONTROL";
    private final NetworksData m_networksData;
    private final String m_networkId;
    
    private String m_locationId;
    private SimpleObjectProperty<JsonObject> m_marketObject = new SimpleObjectProperty<>(null);
    private SimpleObjectProperty<JsonObject> m_tokenMarketObject = new SimpleObjectProperty<>(null);

    private NoteInterface m_marketInterface = null;
    private NoteInterface m_tokenMarketInterface = null;
   
    private String m_marketId = null;
    private String m_tokenMarketId = null;

    private NoteMsgInterface m_marketMsgInterface = null;
    private NoteMsgInterface m_tokenMarketMsgInterface = null;

    private SimpleBooleanProperty m_marketAvailable = new SimpleBooleanProperty();
    private SimpleBooleanProperty m_tokenMarketAvailable = new SimpleBooleanProperty();

    private SimpleLongProperty m_marketLastUpdated = new SimpleLongProperty();
    private SimpleLongProperty m_tokenMarketLastUpdated = new SimpleLongProperty();

    private Future<?> m_tokenConnectFuture = null;
    private Future<?> m_connectFuture = null;

    private SimpleStringProperty m_marketIdProperty = null;
    private ChangeListener<String> m_marketIdListener = null;
    private SimpleStringProperty m_tokenMarketIdProperty = null;
    private ChangeListener<String> m_tokenMarketIdListener = null;

    private int m_marketConnections = 0;
    private int m_tokenMarketConnections = 0;

    public ErgoMarketControl(String networkId, String locationId, NetworksData networksData){
        m_networkId = networkId;
        m_networksData = networksData;
        m_locationId = locationId;
        getData();
    }

    public ErgoMarketControl(SimpleStringProperty marketIdProperty, SimpleStringProperty tokenMarketIdProperty, String locationId, NetworksData networksData){
        m_networkId = null;
        m_networksData = networksData;
        m_locationId = locationId;
        m_marketIdProperty = marketIdProperty;
        m_tokenMarketIdProperty = tokenMarketIdProperty;

        addLisetners();


    }

    protected void addLisetners(){
        if(m_marketIdListener == null && m_marketIdProperty != null){
            m_marketIdListener = (obs,oldval,newval)->{
                if(oldval != null){
                    disconnectMarket();
                }
                if(newval != null){
                    connectToMarket();
                }
            };
            m_marketIdProperty.addListener(m_marketIdListener);
        }
        if(m_tokenMarketIdListener == null && m_tokenMarketIdProperty != null){
            m_tokenMarketIdListener = (obs,oldval,newval)->{
                if(oldval != null){
                    disconnectTokenMarket();
                }
                if(newval != null){
                    connectToTokenMarket();
                }
            };

            m_tokenMarketIdProperty.addListener(m_tokenMarketIdListener);
        }
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

        m_marketId = marketIdElement != null ? (marketIdElement.isJsonNull() ? null : marketIdElement.getAsString() ) : ErgoDex.NETWORK_ID;
        m_tokenMarketId = tokenMarketIdElement != null ? (tokenMarketIdElement.isJsonNull() ? null : tokenMarketIdElement.getAsString()) : ErgoDex.NETWORK_ID;
        if(json == null){
            save();

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
        return m_marketIdProperty == null ? m_marketId : m_marketIdProperty.get();
    }

    public String getTokenMarketId(){
        return m_tokenMarketIdProperty == null ? m_tokenMarketId : m_tokenMarketIdProperty.get();
    }

    public void setMarketId(String marketId){
        if(m_marketIdProperty == null){
            if(getMarketNetworkObject() != null){
                disconnectMarket();
            }
            m_marketId = marketId;
            save();
        }
    }

    public void setTokenMarketId(String tokenMarketId){
        if(m_tokenMarketIdProperty == null){
            if(getTokenMarketNetworkObject() != null){
                disconnectTokenMarket();
            }
            m_tokenMarketId = tokenMarketId;
            save();
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
            if (marketInterface != null && m_marketMsgInterface == null && m_connectFuture == null || (m_connectFuture != null && m_connectFuture.isDone()) && getMarketNetworkObject() == null) {
                

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
                            
                            }

                            @Override
                            public void sendMessage(int code, long timestamp, String networkId, Number number) {
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



    private void connectToTokenMarket() {
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
                            
                            }

                            @Override
                            public void sendMessage(int code, long timestamp, String networkId, Number number) {
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
                }, onFailed -> {
                    disconnectTokenMarket();
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

        if(m_marketIdProperty != null && m_marketIdListener != null){
            m_marketIdProperty.removeListener(m_marketIdListener);
            m_marketIdListener = null;
        }

        if(m_tokenMarketIdProperty != null && m_tokenMarketIdListener != null){
            m_tokenMarketIdProperty.removeListener(m_tokenMarketIdListener);
            m_tokenMarketIdListener = null;
        }
        
        disconnectMarket();
        disconnectTokenMarket();

    }
}
