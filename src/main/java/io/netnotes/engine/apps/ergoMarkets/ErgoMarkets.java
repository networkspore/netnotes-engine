package io.netnotes.engine.apps.ergoMarkets;


import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.scene.image.Image;
import com.google.gson.JsonObject;

import io.netnotes.engine.Network;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.Utils;
import io.netnotes.engine.apps.ergoDex.ErgoDex;
import io.netnotes.engine.networks.ergo.ErgoConstants;
import io.netnotes.engine.networks.ergo.ErgoNetworkData;

import com.google.gson.JsonElement;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.gson.JsonArray;

public class ErgoMarkets extends Network {
    public static final String NETWORK_ID = "ERGO_MARKETS";
    public static final String NAME = "Ergo Markets";
    public static final String DESCRIPTION = "A view of al the Ergo market apps";
  
    private ErgoNetworkData m_ergNetData = null;

    private String m_defaultMarketId = ErgoDex.NETWORK_ID;
    private String m_defaultTokenMarketId = ErgoDex.NETWORK_ID;

    private String m_locationString;

    public ErgoMarkets(String locationString, NetworksData networksData){
        super(new Image(getSmallAppIconString()), "Ergo Markets", NETWORK_ID, networksData);
        getData();
    }

    public Image getSmallAppIcon(){
        return new Image(getSmallAppIconString());
    }

    public static String getAppIconString(){
        return "/assets/bar-chart-150.png";
    }
    public Image getAppIcon(){
        return new Image(getAppIconString());   
    }

    public static String getSmallAppIconString(){
        return "/assets/bar-chart-30.png";
    }


    public String getDescription(){
        return DESCRIPTION;
    }

    public JsonObject getDefaultJson(){
        JsonObject json = new JsonObject();
        json.addProperty("defaultMarketId", m_defaultMarketId);
        json.addProperty("defaulTokenMarketId", m_defaultTokenMarketId);
        return json;
    }
   

    public void clearDefaultMarket(boolean isSave){

        m_defaultMarketId = null;
        if(isSave){
            long timeStamp = System.currentTimeMillis();

            JsonObject json = getDefaultJson();

            sendMessage(NoteConstants.LIST_DEFAULT_CHANGED, timeStamp, NETWORK_ID, json.toString());
            save();
         }
    }

    public void clearDefaultTokenMarket(boolean isSave){

        m_defaultTokenMarketId = null;

        if(isSave){
            long timeStamp = System.currentTimeMillis();

            JsonObject json = getDefaultJson();
            
            sendMessage(NoteConstants.LIST_DEFAULT_CHANGED, timeStamp, NETWORK_ID, json.toString());
            save();
        }

    }



    public String getDefaultMarketId() {
        return m_defaultMarketId;
    }



    public void setDefaultMarketId(String defaultMarketId, boolean isSave) {
        this.m_defaultMarketId = defaultMarketId;
     
        if(isSave){
            JsonObject json = getDefaultJson();
            long timeStamp = System.currentTimeMillis();
            sendMessage(NoteConstants.LIST_DEFAULT_CHANGED, timeStamp, NETWORK_ID, json.toString());

            save();
        }

    }

    public void setDefaulTokenMarketId(String defaultTokenMarketId, boolean isSave) {
        this.m_defaultTokenMarketId = defaultTokenMarketId;
        
        if(isSave){   
            JsonObject json = getDefaultJson();
            long timeStamp = System.currentTimeMillis();
            sendMessage(NoteConstants.LIST_DEFAULT_CHANGED, timeStamp, NETWORK_ID, json.toString());
        
            save();
        }
    }

    public Future<?> setDefaultTokenMarket(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement idElement = note != null ? note.get("id") : null;
  
        if(idElement != null){
            String defaultId = !idElement.isJsonNull() ? idElement.getAsString() : null;
            if(defaultId != null){
                NoteInterface noteInterface = getMarket(defaultId);

                if(noteInterface != null){
                    setDefaulTokenMarketId(defaultId, true);
                    
                }else{
                    Utils.returnException("Id element required", getExecService(), onFailed);
                   
                }
            }else{
                clearDefaultTokenMarket(true);
                
            }
        }else{
            return Utils.returnException("Token market not found", getExecService(), onFailed);
        }

        return Utils.returnObject(getDefaultJson(), getExecService(), onSucceeded);
    }
    
    public Future<?> setDefaultMarket(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement idElement = note != null ? note.get("id") : null;
  
        if(idElement != null){
            String defaultId = !idElement.isJsonNull() ? idElement.getAsString() : null;
            if(defaultId != null){
                NoteInterface noteInterface = getMarket(defaultId);

                if(noteInterface != null){
                    setDefaultMarketId(defaultId, true);
                    
                }else{
                    return Utils.returnException("Market not found", getExecService(), onFailed);
                   
                }
            }else{
                clearDefaultMarket(true);
            }
        }else{
            return Utils.returnException("Id element required", getExecService(), onFailed);
        }

        return Utils.returnObject(getDefaultJson(), getExecService(), onSucceeded);
    }


  
    public Future<?> sendNote(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        JsonElement cmdElement = note != null ? note.get(NoteConstants.CMD) : null;
        JsonElement idElement = note != null ? note.get("id") : null;
        
        if(cmdElement != null){

            switch(cmdElement.getAsString()){
                //Market

                case "getMarkets":
                    return getMarkets(onSucceeded, onFailed);
                case "setDefaultMarket":
                    return setDefaultMarket(note, onSucceeded, onFailed);
                case "clearDefaultMarket":
                    clearDefaultMarket(true);
                    return Utils.returnObject(getDefaultJson(), getExecService(), onSucceeded);
                //Tokens
                case "getTokenMarkets":
                    return getTokenMarkets(onSucceeded, onFailed);
                case "setDefaultTokenMarket":
                    return setDefaultTokenMarket(note,onSucceeded, onFailed);
                
                case "clearDefaultTokenMarket":
                    clearDefaultTokenMarket(true);
                    return Utils.returnObject(getDefaultJson(), getExecService(), onSucceeded);
                
                default: 
                    String id = idElement != null ? idElement.getAsString() : getDefaultMarketId();
                
                    NoteInterface market = id != null ? getMarket(id) : null;
                
                    if(market != null){
                    
                        return market.sendNote(note, onSucceeded, onFailed);
                    }
            }
        }

        return null;
    }


    public ExecutorService getExecService(){
        return getNetworksData().getExecService();
    }

   
    
    public NetworksData getNetworksData(){
        return m_ergNetData.getNetworksData();
    }


    public NoteInterface getMarket(String id){
  
        if(id != null){

            return  getNetworksData().getApp(id);
         
        }
        return null;
    }



    /*private JsonArray getTokenMarkets(){
    
        List<NoteInterface> list = getNetworksData().getAppsContainsAllKeyWords("ergo", "exchange", "usd", "ergo tokens");
            
        JsonArray jsonArray = new JsonArray();

         for ( NoteInterface data : list) {

            JsonObject result =  data.getJsonObject();
            jsonArray.add(result);

        }
    
        return jsonArray;
    }*/

    private Future<?> getMarkets(EventHandler<WorkerStateEvent> onSucceded, EventHandler<WorkerStateEvent> onFailed){
    
        List<NoteInterface> list = getNetworksData().getAppsContainsAllKeyWords("ergo", "exchange", "usd");
            
        JsonArray jsonArray = new JsonArray();

        Iterator<NoteInterface> it = list.iterator();  

        return NoteConstants.getInterfaceNetworkObjects(it, jsonArray, getExecService(), onSucceded, onFailed);


    }


    private Future<?> getTokenMarkets(EventHandler<WorkerStateEvent> onSucceded, EventHandler<WorkerStateEvent> onFailed){
    
        List<NoteInterface> list = getNetworksData().getAppsContainsAllKeyWords("ergo tokens", "exchange", "usd");
            
        JsonArray jsonArray = new JsonArray();

        Iterator<NoteInterface> it = list.iterator();  

        return NoteConstants.getInterfaceNetworkObjects(it, jsonArray, getExecService(), onSucceded, onFailed);
    }

    private void getData(){
        getNetworksData().getData("data", ".", NETWORK_ID, ErgoConstants.ERGO_NETWORK_ID, onSucceded ->{
            Object obj = onSucceded.getSource().getValue();
        
            JsonObject json = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
            if(json != null){
            
                openJson(json);
            }
        });

       
    }


    public void save() {
       
        getNetworksData().save("data", ".", NETWORK_ID, ErgoConstants.ERGO_NETWORK_ID, getJsonObject());
        
    }

 
  
    public JsonObject getJsonObject() {
        JsonObject json = new JsonObject();
        if(m_defaultMarketId != null){
            json.addProperty("defaultMarketId", m_defaultMarketId);
        }
        if(m_defaultTokenMarketId != null){
            json.addProperty("defaultTokenMarketId", m_defaultTokenMarketId);
        }
        return json;
    }

    private void openJson(JsonObject json){

        JsonElement defaultMarketIdElement = json != null ? json.get("defaultMarketId") : null;
        JsonElement defaultTokenMarketIdElement = json != null ? json.get("defaultTokenMarketId") : null;
        m_defaultMarketId = defaultMarketIdElement != null && !defaultMarketIdElement.isJsonNull() ? defaultMarketIdElement.getAsString() : null;
        m_defaultTokenMarketId = defaultTokenMarketIdElement != null && !defaultTokenMarketIdElement.isJsonNull() ? defaultTokenMarketIdElement.getAsString() : null;
        
    }
}
