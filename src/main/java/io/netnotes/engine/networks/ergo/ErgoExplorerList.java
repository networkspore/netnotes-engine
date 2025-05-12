package io.netnotes.engine.networks.ergo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javafx.beans.value.ChangeListener;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.Utils;
import io.netnotes.engine.apps.AppConstants;

public class ErgoExplorerList {
    private ErgoExplorers m_ergoExplorer = null;
    private HashMap<String,ErgoExplorerData> m_dataHashMap = new HashMap<>();
    
    private ChangeListener<LocalDateTime> m_updateListener = null;
    private ErgoNetworkData m_ergoNetworkData;
    private String m_defaultExplorerId = ErgoPlatformExplorerData.ERGO_PLATFORM_EXPLORER;

    public ErgoExplorerList(ErgoExplorers ergoExplorer, ErgoNetworkData ergoNetworkData) {
        m_ergoExplorer = ergoExplorer;
        m_updateListener = (obs, oldval, newVal) -> save();
        m_ergoNetworkData = ergoNetworkData;
        getData();

        
    }

    public String getDefaultExplorerId(){
        return m_defaultExplorerId;
    }

    public void setDefaultExplorerId(String id, boolean isSave){
        m_defaultExplorerId = id;

        
       
        

        if(isSave){
            
             long timeStamp = System.currentTimeMillis();
            save();
           
            JsonObject note = NoteConstants.getJsonObject("networkId", ErgoConstants.EXPLORER_NETWORK);
            if(id != null){
                ErgoExplorerData explorerData = getErgoExplorerData(id);
                note.addProperty("id",  id);
                note.addProperty("name", explorerData.getName());
            }
            note.addProperty("code", NoteConstants.LIST_DEFAULT_CHANGED);
            note.addProperty("timeStamp", timeStamp);
            
            getErgoNetwork().sendMessage(NoteConstants.LIST_DEFAULT_CHANGED, timeStamp, ErgoConstants.EXPLORER_NETWORK, note.toString());
        }
        
    }

    public Future<?> clearDefault(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded){

        m_defaultExplorerId = null;
        long timeStamp = System.currentTimeMillis();
        
        JsonObject msg = NoteConstants.getJsonObject("networkId", ErgoConstants.EXPLORER_NETWORK);
        msg.addProperty("code", NoteConstants.LIST_DEFAULT_CHANGED);
        msg.addProperty("timeStamp", timeStamp);
        getErgoNetwork().sendMessage(NoteConstants.LIST_DEFAULT_CHANGED, timeStamp, ErgoConstants.EXPLORER_NETWORK, msg.toString());
        

        return Utils.returnObject(msg, getExecService(), onSucceeded);
    }

    public Future<?> setDefault(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement idElement = note != null ? note.get("id") : null;
        if(idElement != null){
            String defaultId = idElement.getAsString();
            ErgoExplorerData explorerData = getErgoExplorerData(defaultId);
            if(explorerData != null){
                setDefaultExplorerId(defaultId, true);
                return Utils.returnObject(explorerData.getJsonObject(), getExecService(), onSucceeded);
            }
        }

        return Utils.returnException("Id element required", getExecService(), onFailed);
    }

    public Future<?> getDefaultJson(EventHandler<WorkerStateEvent> onSucceeded){
        ErgoExplorerData explorerData = getErgoExplorerData(getDefaultExplorerId());

        return Utils.returnObject(explorerData != null ? explorerData.getJsonObject() : null, getExecService(), onSucceeded);
    }

    public ErgoExplorerData getDefaultExplorer(){
        ErgoExplorerData explorerData = getErgoExplorerData(getDefaultExplorerId());

        return explorerData != null ? explorerData : null;
    }

    public ErgoExplorers getErgoExplorer(){
        return m_ergoExplorer;
    }

    public ErgoNetworkData getErgoNetworkData(){
        return m_ergoNetworkData;
    }

    public ExecutorService getExecService(){
        return m_ergoExplorer.getExecService();
    }


    public Future<?> getExplorerById(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement idElement = note.get("id");

        if(idElement != null && idElement.isJsonPrimitive()){
            String id = idElement.getAsString() ;
        
            ErgoExplorerData explorerData = getErgoExplorerData(id);
            return Utils.returnObject(explorerData != null ? explorerData.getJsonObject() : null, getExecService(), onSucceeded);
        }
        return Utils.returnException( "Id element required", getExecService(), onFailed);
    }


    private void getData(){
        m_ergoExplorer.getNetworksData().getData("data", ".", ErgoConstants.EXPLORER_NETWORK, ErgoConstants.ERGO_NETWORK_ID, onSucceeded->{
            Object obj = onSucceeded.getSource().getValue();

            JsonObject json = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
           
            if(json != null){
                openJson(json);
            }else{
                addDefault();
            }
        });
      
        
    }

    public void save() {
       
        m_ergoExplorer.getNetworksData().save("data", ".", ErgoConstants.EXPLORER_NETWORK, ErgoConstants.ERGO_NETWORK_ID, getJsonObject());
        
    }

    private void addDefault(){
        
        ErgoPlatformExplorerData ergoExplorerData = new ErgoPlatformExplorerData(this);
        
        m_dataHashMap.put(ergoExplorerData.getId(), ergoExplorerData);
        save();
    }

    public int size(){
        return m_dataHashMap.size();
    }


    public void openJson(JsonObject json){
        
        JsonElement dataElement = json.get("data");

        if (dataElement != null && dataElement.isJsonArray()) {
            com.google.gson.JsonArray dataArray = dataElement.getAsJsonArray();

            for (int i = 0; i < dataArray.size(); i++) {
                JsonElement dataItem = dataArray.get(i);
                JsonObject explorerJson = dataItem != null && dataItem.isJsonObject() ? dataItem.getAsJsonObject() : null;
                
                if(explorerJson != null){
                    JsonElement explorerIdElement = explorerJson.get("id");
                    if(explorerIdElement != null && explorerIdElement.isJsonPrimitive()){
                        String explorerId = explorerIdElement.getAsString();
                        if(getErgoExplorerData(explorerId) == null){
                            ErgoExplorerData explorerData = null;
                            switch(explorerId){
                                case ErgoPlatformExplorerData.ERGO_PLATFORM_EXPLORER:
                                    explorerData = new ErgoPlatformExplorerData(this);
                                    break;
                                default:
                                    try{
                                        explorerData = new ErgoExplorerData(explorerId, explorerJson, this);
                                    }catch(Exception e){
                                       try {
                                            Files.writeString(AppConstants.LOG_FILE.toPath(), "\nErgoExplorerList cannot open data: " + e.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                                        } catch (IOException e1) {

                                        }
                                    }
                            }
                            
                            if(explorerData != null){
                                m_dataHashMap.put(explorerData.getId(), explorerData);
                            }
                            
                        }
                        
                    }
                }
            }
        }
        

        JsonElement explorerIdElement = json != null ? json.get("defaultId") : null;
        setDefaultExplorerId( explorerIdElement != null && explorerIdElement.isJsonPrimitive() ? explorerIdElement.getAsString() : null, false);
      

        
    }

 
 
    public void add(ErgoExplorerData explorerData){
        add(explorerData, true);
    }

    public void add(ErgoExplorerData ergoExplorerData, boolean doSave) {
        if (ergoExplorerData != null) {
            m_dataHashMap.put(ergoExplorerData.getId(), ergoExplorerData);
       
            ergoExplorerData.addUpdateListener(m_updateListener);
            if (doSave) {
                long timeStamp = System.currentTimeMillis();
                save();
                JsonObject note = NoteConstants.getJsonObject("networkId", ErgoConstants.EXPLORER_NETWORK);
                note.addProperty("id",  ergoExplorerData.getId());
                note.addProperty("name", ergoExplorerData.getName());
                note.addProperty("code", NoteConstants.LIST_ITEM_ADDED);
                note.addProperty("timeStamp", timeStamp);
                
                getErgoNetwork().sendMessage(NoteConstants.LIST_ITEM_ADDED, timeStamp, ErgoConstants.EXPLORER_NETWORK, note.toString());
            }
        }
    }

    public ErgoNetwork getErgoNetwork(){
        return m_ergoNetworkData.getErgoNetwork();
    }

    public boolean remove(String id, boolean doSave){
        if (id != null) {
            ErgoExplorerData explorerData = m_dataHashMap.remove(id);
            if (explorerData != null) {
                
                if(doSave){
                    explorerData.removeUpdateListener();
                    save();

                    JsonObject note = NoteConstants.getJsonObject("networkId", ErgoConstants.EXPLORER_NETWORK);
                    note.addProperty("id",  explorerData.getId());
                    note.addProperty("code", NoteConstants.LIST_ITEM_REMOVED);
                            
                    long timeStamp = System.currentTimeMillis();
                    note.addProperty("timeStamp", timeStamp);
                    
                    getErgoNetwork().sendMessage(NoteConstants.LIST_ITEM_REMOVED, timeStamp, ErgoConstants.EXPLORER_NETWORK, note.toString());
                }
                return true;
            }
            
        }
        return false;
    }

    public ErgoExplorerData getErgoExplorerData(String id) {
        if (id != null && m_dataHashMap != null) {
       
            ErgoExplorerData ergoExplorerData = m_dataHashMap.get(id);
            
            return ergoExplorerData;     
        }
        return null;
    }

     private JsonArray getDataJsonArray() {
        JsonArray jsonArray = new JsonArray();
     
        for (Map.Entry<String, ErgoExplorerData> entry : m_dataHashMap.entrySet()) {
            ErgoExplorerData data = entry.getValue();
            JsonObject jsonObj = data.getJsonObject();
            jsonArray.add(jsonObj);

        }
        return jsonArray;
    }



    public JsonObject getJsonObject() {
        JsonObject json = new JsonObject();
        if(m_dataHashMap.size() > 0){
            json.addProperty("defaultId", getDefaultExplorerId());
            json.add("data", getDataJsonArray());
        }

        return json;
    }


    public Future<?> getExplorers(EventHandler<WorkerStateEvent> onSucceeded){
        JsonArray jsonArray = new JsonArray();

        for (Map.Entry<String, ErgoExplorerData> entry : m_dataHashMap.entrySet()) {
            
            ErgoExplorerData data = entry.getValue();
            JsonObject jsonObj = NoteConstants.getJsonObject("name", data.getName());
            jsonObj.addProperty("id", data.getId());
            jsonObj.addProperty("isDefault", getDefaultExplorerId() != null ? data.getId().equals(getDefaultExplorerId()) : false);
            jsonArray.add(jsonObj);

        }
        return Utils.returnObject(jsonArray, getExecService(), onSucceeded);
    }


}
