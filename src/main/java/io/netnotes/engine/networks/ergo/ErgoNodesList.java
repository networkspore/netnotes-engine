package io.netnotes.engine.networks.ergo;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javafx.event.EventHandler;
import org.apache.commons.io.FileUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.netnotes.engine.FreeMemory;
import io.netnotes.engine.NamedNodeUrl;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.SubmitButton;
import io.netnotes.engine.Utils;
import io.netnotes.engine.apps.AppConstants;
import io.netnotes.friendly_id.FriendlyId;
import javafx.concurrent.WorkerStateEvent;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;

public class ErgoNodesList {

    //options
    public final static String DEFAULT = "DEFAULT";
    public final static String PUBLIC = "PUBLIC";
    public final static String CUSTOM = "CUSTOM";


    private ErgoNodes m_ergoNodes;

    private HashMap<String, ErgoNodeData> m_dataList = new HashMap<>();


    private String m_downloadImgUrl = "/assets/cloud-download-30.png";

    private ErgoNetworkData m_ergoNetworkData;

    public ErgoNodesList( ErgoNodes ergoNodes, ErgoNetworkData ergoNetworkData) {
        m_ergoNodes = ergoNodes;
        m_ergoNetworkData = ergoNetworkData;
        getData();
    }

    private void getData() {
        
   
        getNetworksData().getData("data",".", ErgoConstants.NODE_NETWORK, ErgoConstants.ERGO_NETWORK_ID, onSucceded ->{
            Object obj = onSucceded.getSource().getValue();
        
            JsonObject json = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
            
            openJson(json);
            
        });
    
    }

    public final static String PUBLIC_NODES_LIST_URL = "https://raw.githubusercontent.com/networkspore/Netnotes/main/publicNodes.json";
    
    private void getPublicNodesList(){
        
  
        Utils.getUrlJson(PUBLIC_NODES_LIST_URL, getNetworksData().getExecService(), (onSucceeded) -> {
            Object sourceObject = onSucceeded.getSource().getValue();
            if (sourceObject != null && sourceObject instanceof JsonObject) {
                //openNodeJson((JsonObject) sourceObject);
            }
        }, (onFailed) -> {
            //  setDefaultList();
        });
    
        
    }





    public NetworksData getNetworksData(){
        return m_ergoNetworkData.getNetworksData();
    }

    public ErgoNetworkData getErgoNetworkData(){
        return m_ergoNetworkData;
    }


    private void openJson(JsonObject json) {
      
        JsonElement nodesElement = json != null ? json.get("nodes") : null;

     
        if (nodesElement != null && nodesElement.isJsonArray()) {

   
            JsonArray jsonArray = nodesElement.getAsJsonArray();
         
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement nodeElement = jsonArray.get(i);

                if (nodeElement != null && nodeElement.isJsonObject()) {
                    JsonObject nodeJson = nodeElement.getAsJsonObject();

                    JsonElement namedNodeElement = nodeElement == null ? null : nodeJson.get("namedNode");
                 
                        
                        JsonElement idElement = nodeJson == null ? null : nodeJson.get("id");
                        JsonElement nameElement = nodeJson == null ? null : nodeJson.get("name");
                        JsonElement iconElement = nodeJson == null ? null : nodeJson.get("iconString");
                        JsonElement clientTypeElement = nodeJson == null ? null : nodeJson.get("clientType");
                        

                        if(namedNodeElement != null && idElement != null && nameElement != null && iconElement != null){
                            String clientType = clientTypeElement != null ? clientTypeElement.getAsString() : ErgoNodeData.LIGHT_CLIENT;
                            String id = idElement.getAsString();
                            String name = nameElement.getAsString();
                            String imgString = iconElement.getAsString();

                            try{
                                switch(clientType){
                                    case ErgoNodeData.LOCAL_NODE:
                                        loadLocalNode(id, name, imgString, clientType, nodeJson);
                                    break;
                                    default:
                                        ErgoNodeData ergoNodeData = new ErgoNodeData(imgString, name, id, clientType, this, nodeJson);
                                        addRemoteNode(ergoNodeData , false);
                                    break;
                                }
                            }catch(Exception e){
                                try {
                                    Files.writeString(AppConstants.LOG_FILE.toPath(), e.toString() +"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                                } catch (IOException e1) {
                           
                                }
                            
                            }
                          
                        }   
                    
                }
            }
           
          
        }else{
            NamedNodeUrl defaultUrl = new NamedNodeUrl();
            ErgoNodeData nodeData = new ErgoNodeData(
                defaultUrl.getId(),
                defaultUrl.getName(), 
                ErgoNodeData.LIGHT_CLIENT,
                this, defaultUrl
            );
            
            addRemoteNode(nodeData, false);
        }



       
   
    }

    private void loadLocalNode(String id, String name, String imgString, String clientType, JsonObject nodeJson) throws Exception{
        ErgoNodeLocalData localData = new ErgoNodeLocalData(imgString, id, name, clientType, nodeJson, this);

        addNode(localData, false);
    }

    private void addNode(ErgoNodeData nodeData, boolean isSave){
        if(nodeData != null && nodeData.getId() != null){
          
            String id = nodeData.getId();
            m_dataList.put(id, nodeData);

          

            if(isSave){
                long timeStamp = System.currentTimeMillis();

                getErgoNetwork().sendMessage(NoteConstants.LIST_ITEM_ADDED, timeStamp, ErgoConstants.NODE_NETWORK, id);

               
            }
        }
    }

   


    public String getDownloadImgUrl() {
        return m_downloadImgUrl;
    }


    public ErgoNodeData getRemoteNode(String id) {
        if (id != null) {
       
            return m_dataList.get(id);
            
            
        }
        return null;
    }


    public void addRemoteNode(ErgoNodeData ergoNodeData, boolean isSave) {
   
        if (ergoNodeData != null && ergoNodeData.getId() != null) {
            if(getNodeById(ergoNodeData.getId()) == null){
                
                addNode(ergoNodeData, isSave);
                
            }
        }
    }

    public ErgoNetwork getErgoNetwork(){
        return m_ergoNodes.getErgoNetwork();
    }

    public boolean remove(String id, boolean isDelete, boolean isSave) {
        if(id != null ){

            ErgoNodeData nodeData = m_dataList.remove(id);

            if(nodeData != null){
                if(isDelete){
                    if(nodeData != null && nodeData instanceof ErgoNodeLocalData){
                        File nodeAppDir = ((ErgoNodeLocalData) nodeData).getAppDir();
                        if(nodeAppDir != null){
                     
                            try {
                                FileUtils.deleteDirectory(nodeAppDir);
                            } catch (IOException e) {
                                try {
                                    Files.writeString(AppConstants.LOG_FILE.toPath(), "Error deleting node files: " + e.toString() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                                } catch (IOException e1) {
                   
                                }
                            }

                        }
                    }
                }
                if(isSave){
                    save();
        
                    JsonObject note = NoteConstants.getJsonObject("networkId", ErgoConstants.NODE_NETWORK);
                    
                    JsonArray jsonArray = new JsonArray();
                    jsonArray.add(nodeData.getJsonObject());

                    note.add("ids", jsonArray);
                    note.addProperty("code", NoteConstants.LIST_ITEM_REMOVED);
                            
                    long timeStamp = System.currentTimeMillis();
                    note.addProperty("timeStamp", timeStamp);
                    
                    getErgoNetwork() .sendMessage(NoteConstants.LIST_ITEM_REMOVED, timeStamp, ErgoConstants.NODE_NETWORK, note.toString());
                }

                return true;
            }
        }
        return false;
        
    }

    public void removeNodes(JsonObject note,NetworkInformation networkInformation, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        
        long timeStamp = System.currentTimeMillis();

        JsonElement idsElement = note.get("ids");

        if(idsElement != null && idsElement.isJsonArray()){
       
            JsonArray idsArray = idsElement.getAsJsonArray();
            if(idsArray.size() > 0){
                getNetworksData().verifyAppKey(ErgoNodes.NAME, note, networkInformation, timeStamp, onVerified->{
                    
                    JsonArray jsonArray = new JsonArray();

                    for(JsonElement element : idsArray){
                        JsonObject idObj = element.getAsJsonObject();

                        JsonElement idElement = idObj.get("id");

                        String id = idElement != null ? idElement.getAsString() : null;

                        JsonElement deleteElement = id != null ? idObj.get("isDelete") : null;
                        
                        boolean isDelete = deleteElement != null && deleteElement.isJsonPrimitive() ? deleteElement.getAsBoolean() : false;
                
                        boolean isRemove = id != null ? remove(id, isDelete, false) : false;
                        if(isRemove){
                            jsonArray.add(new JsonPrimitive(id)); 
                        }
                    }
                    
                    if(jsonArray.size() > 0){
                        save();
                        getErgoNetwork().sendMessage( NoteConstants.LIST_ITEM_REMOVED, timeStamp, ErgoConstants.NODE_NETWORK, jsonArray.toString());
                    }
                    Utils.returnObject(NoteConstants.VERIFIED, getExecService(), onSucceeded);
                },onFailed);
            }

        }else{
            Utils.returnException("Id element required", getExecService(), onSucceeded);
        }

       
    }

    public ErgoNodeData getNodeById(String id){
        if(id != null){
            return m_dataList.get(id);
        
        }
        return null;
    }

    public Future<?> getNodeObjectById(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement idElement = note.get("id");

        String id = idElement != null && !idElement.isJsonNull() ? idElement.getAsString() : null;

        if(id != null){
            ErgoNodeData nodeData = getNodeById(id);
            if(nodeData != null){
                return Utils.returnObject(nodeData.getJsonObject(), getExecService(), onSucceeded);
            }else{
                return Utils.returnException(NoteConstants.ERROR_NOT_FOUND, getExecService(), onFailed);
            }
        }else{
            return Utils.returnException(NoteConstants.ERROR_INVALID, getExecService(), onFailed);
        }
    }

    public JsonObject getLocalNodeByFile(File file){
        if (m_dataList.size() > 0 && file != null) {
            
            file = file.isFile() ? file.getParentFile() : (file.isDirectory() ? file : null);

            if ( file != null) {
              
                for (Map.Entry<String, ErgoNodeData> entry : m_dataList.entrySet()) {
                    ErgoNodeData nodeData = entry.getValue();
                    if(nodeData instanceof ErgoNodeLocalData){
                        ErgoNodeLocalData localData = (ErgoNodeLocalData) nodeData;
                        
                        if (localData.getAppDir().getAbsolutePath().equals(file.getAbsolutePath())){
                            return localData.getJsonObject();
                        }
            
                    }
                }
            }
        }
        return null;
    }

    public JsonObject getNodeByName(String name){
        if (m_dataList.size() > 0 && name != null) {
            
              
            for (Map.Entry<String, ErgoNodeData> entry : m_dataList.entrySet()) {
                ErgoNodeData nodeData = entry.getValue();
      
                
                if (nodeData.getName().equals(name) ){
                    return nodeData.getJsonObject();
                }
    
                
            }
            
        }
        return null;
    }

            
    public JsonArray getNodesJsonArray() {
        JsonArray jsonArray = new JsonArray();

        for (Map.Entry<String, ErgoNodeData> entry : m_dataList.entrySet()) {
            ErgoNodeData data = entry.getValue();
            JsonObject jsonObj = data.getJsonObject();
            jsonArray.add(jsonObj);
        }

        return jsonArray;
    }


    public Future<?> getRemoteNodes(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded) {

        JsonArray jsonArray = new JsonArray();

        for (Map.Entry<String, ErgoNodeData> entry : m_dataList.entrySet()) {
            ErgoNodeData ergNodeData = entry.getValue();
            if(!(ergNodeData instanceof ErgoNodeLocalData)){
                JsonObject jsonObj = ergNodeData.getJsonObject();
                jsonArray.add(jsonObj);
            }
        }
      
        return Utils.returnObject(jsonArray, getExecService(), onSucceeded);
    }

    public Future<?> getLocalNodes(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded) {
        JsonArray jsonArray = new JsonArray();

        for (Map.Entry<String, ErgoNodeData> entry : m_dataList.entrySet()) {
            ErgoNodeData ergNodeData = entry.getValue();

            if(ergNodeData instanceof ErgoNodeLocalData){
                JsonObject jsonObj = ergNodeData.getJsonObject();
                jsonArray.add(jsonObj);
            }

        }

        return Utils.returnObject(jsonArray, getExecService(), onSucceeded);
    }

    public Future<?> getNodes(EventHandler<WorkerStateEvent> onSucceeded){
         JsonArray jsonArray = new JsonArray();

        for (Map.Entry<String, ErgoNodeData> entry : m_dataList.entrySet()) {
            ErgoNodeData data = entry.getValue();
            JsonObject jsonObj = data.getJsonObject();
            jsonArray.add(jsonObj);
        }

        return Utils.returnObject(jsonArray, getExecService(), onSucceeded);
    }


     public JsonObject getDataJson() {
        JsonObject json = new JsonObject();

        json.add("nodes", getNodesJsonArray());

        return json;
    }


    

  


    public void save() {
  
        getNetworksData().save("data",".", ErgoConstants.NODE_NETWORK, ErgoConstants.ERGO_NETWORK_ID, getDataJson());
        
 
    }

  
    /*
    public VBox getGridBox(SimpleDoubleProperty width, SimpleDoubleProperty scrollWidth) {
        VBox gridBox = new VBox();

        Runnable updateGrid = () -> {
            gridBox.getChildren().clear();
          
            if(m_localDataList != null){
                HBox fullNodeRowItem = m_localDataList.getRowItem();
                fullNodeRowItem.prefWidthProperty().bind(width.subtract(scrollWidth));
                gridBox.getChildren().add(fullNodeRowItem);
            }            
            
            int numCells = m_dataList.size();

            for (int i = 0; i < numCells; i++) {
                ErgoNodeData nodeData = m_dataList.get(i);
                HBox rowItem = nodeData.getRowItem();
                rowItem.prefWidthProperty().bind(width.subtract(scrollWidth));
                gridBox.getChildren().add(rowItem);
            }

        };

        updateGrid.run();

        m_doGridUpdate.addListener((obs, oldval, newval) -> updateGrid.run());

        return gridBox;
    } */

    /*
    public void getMenu(MenuButton menuBtn, SimpleObjectProperty<ErgoNodeData> selectedNode){

        Runnable updateMenu = () -> {
            menuBtn.getItems().clear();
            ErgoNodeData newSelectedNodedata = selectedNode.get();

           

            MenuItem noneMenuItem = new MenuItem("(disabled)");
            if(selectedNode.get() == null){
                noneMenuItem.setId("selectedMenuItem");
            }
            noneMenuItem.setOnAction(e->{
                selectedNode.set(null);
            });
            menuBtn.getItems().add(noneMenuItem);

            if(m_localDataList != null){
                MenuItem localNodeMenuItem = new MenuItem( m_localDataList.getName());
                if(m_localDataList != null && newSelectedNodedata != null && m_localDataList.getId().equals(newSelectedNodedata.getId())){
                    localNodeMenuItem.setId("selectedMenuItem");
                    localNodeMenuItem.setText(localNodeMenuItem.getText());
                }
                localNodeMenuItem.setOnAction(e->{
                    selectedNode.set(m_localDataList);
                });

                menuBtn.getItems().add( localNodeMenuItem );
            }            
            
            int numCells = m_dataList.size();

            for (int i = 0; i < numCells; i++) {
                
                ErgoNodeData nodeData = m_dataList.get(i);
                 MenuItem menuItem = new MenuItem(nodeData.getName());
                if(newSelectedNodedata != null && newSelectedNodedata.getId().equals(nodeData.getId())){
                    menuItem.setId("selectedMenuItem");
                    menuItem.setText(menuItem.getText());
                }
                menuItem.setOnAction(e->{
                    selectedNode.set(nodeData);
                });

                menuBtn.getItems().add(menuItem);
            }



        };

        updateMenu.run();

        selectedNode.addListener((obs,oldval, newval) -> updateMenu.run());

        m_doGridUpdate.addListener((obs, oldval, newval) -> updateMenu.run());

    }
 */
    
   

    

    public void shutdown() {
        for (Map.Entry<String, ErgoNodeData> entry : m_dataList.entrySet()) {
            ErgoNodeData ergNodeData = entry.getValue();
            ergNodeData.shutdown();
        }

    }

    /*private void setDefaultNodeOption(String option) {
        m_defaultAddType = option;
        save();
    }*/

    private String m_tmpId = null;

    private String getNewId(){
        m_tmpId = FriendlyId.createFriendlyId();

        while(getNodeById(m_tmpId) != null){
            m_tmpId = FriendlyId.createFriendlyId();
        }

        return m_tmpId;
    }



    public ErgoNodes getErgoNodes() {
        return m_ergoNodes;
    }

    public String getMemoryInfoString(FreeMemory freeMemory){
        int nodeMemRequired = ErgoNodeLocalData.DEFAULT_MEM_GB_REQUIRED;
        int memoryFootPrint = (int) ((freeMemory.getMemTotalGB() - freeMemory.getMemFreeGB()) + nodeMemRequired ) ;                

        double required = freeMemory.getMemFreeGB() - nodeMemRequired;
        required = required > 0 ? 0 : Math.abs(required);
        String availableMemoryString = String.format("%.1f", freeMemory.getMemAvailableGB()) + " GB";
        String memoryString = freeMemory == null ? " - " :  String.format("%.1f", freeMemory.getMemFreeGB()) + " GB /  " + String.format("%-8s",availableMemoryString) + " / " + String.format("%.1f",freeMemory.getMemTotalGB())+ " GB";
        String swapString = freeMemory == null ? " - " : String.format("%.1f",freeMemory.getSwapFreeGB());
        
//String.format("%.1f", required)

        String textAreaString = "Memory Requirements";
        textAreaString += "\n";
        textAreaString += "\nUsage:       " + nodeMemRequired + " GB / " + memoryFootPrint + " GB";
        textAreaString += "\n                   (Peak / Total)";
        textAreaString += "\nSystem:    " + String.format("%-50s",  memoryString) + "Swap: " + swapString + " GB";
        textAreaString += "\n" + String.format("%-70s","                   (Free / Available / Total)")+"             (Total)";
        textAreaString += "\n";
        if(required > 0){
            textAreaString += "\nWarning: " + String.format("%.1f", freeMemory.getMemFreeGB()) + " GB free memory, " + nodeMemRequired + " GB recommended.";
            textAreaString += "\nSwap usage: " + String.format("%.2f",required) + " GB";
            
            int swapSize = getSwapSize(freeMemory);

            if(swapSize > -1){
                textAreaString += "\n\n*Recommended: Increase swap file size to:" + swapSize + " GB*";
            }    
        }
        return textAreaString;
    }

    public int getMemoryFootPrint(FreeMemory freeMemory){
        int nodeMemRequired = ErgoNodeLocalData.DEFAULT_MEM_GB_REQUIRED;
        return (int) ((freeMemory.getMemTotalGB() - freeMemory.getMemFreeGB()) + nodeMemRequired ) ;                
    }

    public int getSwapSize(FreeMemory freeMemory){
        int memoryFootPrint = getMemoryFootPrint(freeMemory);
        
        if(freeMemory.getSwapTotalGB() < (freeMemory.getMemTotalGB() + 2) || (freeMemory.getSwapTotalGB() < memoryFootPrint + 2)){
            return (Math.ceil(memoryFootPrint + 2) > Math.ceil(freeMemory.getMemTotalGB() + 2) ? (int)Math.ceil(memoryFootPrint + 2) : (int) Math.ceil(freeMemory.getMemTotalGB() + 2)) ;
        }

        return -1; 
    }

    public ErgoNodeLocalData getLocalNode(String id) {

        ErgoNodeData nodeData = m_dataList.get(id);
            
        return nodeData instanceof ErgoNodeLocalData ? (ErgoNodeLocalData) nodeData : null;
        
    }



    public ErgoNodeData getRemoteNodeByUrl(String urlString){
        if (urlString != null) {
              
            for (Map.Entry<String, ErgoNodeData> entry : m_dataList.entrySet()) {
                ErgoNodeData nodeData = entry.getValue();

                if(nodeData.getNamedNodeUrl().getUrlString().equals(urlString)){
                    return nodeData;
                }
            }
            
        }
        return null;
    }


    public void editLocalNode(JsonObject note, NetworkInformation networkInformation,  EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        if(note != null && note.get("data") != null){
            addEditLocalNode(true, note, networkInformation, onSucceeded, onFailed);
        }else{
            Utils.returnException("Data element required", getExecService(), onFailed);
        }
    }

    public void addLocalNode(JsonObject note, NetworkInformation networkInformation,  EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        if(note != null && note.get("data") != null){
            addEditLocalNode(false, note, networkInformation, onSucceeded, onFailed);
        }else{
            Utils.returnException("Data element required", getExecService(), onFailed);
        }
    }

    private void addEditLocalNode(boolean isEdit, JsonObject note, NetworkInformation networkInformation,  EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        
        JsonElement dataElement = note.get("data");
        JsonObject json = dataElement != null && dataElement.isJsonObject() ? dataElement.getAsJsonObject() : null;
        
        if(json != null){
            String jsonId = isEdit ? NoteConstants.getJsonId(json) : null;
            ErgoNodeLocalData currentNodeData = jsonId != null && isEdit ? getLocalNode(jsonId) : null;

            boolean isNodeData = currentNodeData != null && isEdit;

            if(isEdit && (currentNodeData != null && !(currentNodeData instanceof ErgoNodeLocalData) || currentNodeData == null)){
                Utils.returnException(NoteConstants.ERROR_NOT_FOUND+":"+jsonId, getExecService(), onFailed);
                return;
            }

            JsonElement namedNodeElement = json.get("namedNode");
            if(namedNodeElement != null && namedNodeElement.isJsonObject()){
                long timeStamp = System.currentTimeMillis();
                String nodeId = isNodeData ? jsonId : getNewId();
                json.remove("id");
                json.addProperty("id", nodeId);
                
                
                getNetworksData().verifyAppKey(ErgoNodes.NAME, note, networkInformation, timeStamp, onVerified->{

                    JsonElement appDirElement = json.get("appDir");
                    String appDirString = appDirElement != null && !appDirElement.isJsonNull() ? appDirElement.getAsString() : null;
                    File appDir = appDirString != null ? new File(appDirString) : null;

                    Utils.checkDrive(appDir, getExecService(), onPath->{
                        Object pathObject = onPath.getSource().getValue();
                        String path = pathObject != null && pathObject instanceof String  ? (String)pathObject : null;
                        if(path != null && !appDir.isDirectory()){
                            
                            try {
                                Files.createDirectories(appDir.toPath());
                                if (!appDir.isDirectory()) {
                                
                                    Utils.returnException(NoteConstants.ERROR_NOT_FOUND+":" + path, getExecService(), onFailed);
                                    
                                    return;
                                }

                            } catch (IOException e1) {
                        
                                Utils.returnException(e1, getExecService(), onFailed);
                                
                                return;
                            }
                        }
                        
                        if(getLocalNodeByFile(appDir) != null){
                      
                                Utils.returnException("Directory contains an existing node", getExecService(), onFailed);
                            
                            return;
                        }


                        JsonElement isAppFileElement = json.get("isAppFile");
                        JsonElement appFileElement = json.get("appFile");

                        boolean isAppFile = isAppFileElement != null && !isAppFileElement.isJsonNull() ? isAppFileElement.getAsBoolean() : false;
                        String appFileString = appFileElement != null && !appFileElement.isJsonNull() ? appFileElement.getAsString() : null;
                    
                        JsonElement configTextElement = json.get("configText");
                        JsonElement configFileNameElement = json.get("configFileName");
                    
                        String configText = configTextElement != null && !configTextElement.isJsonNull() ? configTextElement.getAsString() : null;
                        String configFileNameString = configFileNameElement != null && !configFileNameElement.isJsonNull() ? configFileNameElement.getAsString() : null;

                      
                    
                        JsonObject namedNodeJson = namedNodeElement != null && namedNodeElement.isJsonObject() ? namedNodeElement.getAsJsonObject() : null;
                            
                        getErgoNetworkData().getErgoNetworkControl().createNamedNode(namedNodeJson, onNamedNode->{
                            Object namedNodeObject = onNamedNode.getSource().getValue();
                            if(namedNodeObject != null && namedNodeObject instanceof NamedNodeUrl){
                                NamedNodeUrl namedNodeUrl = (NamedNodeUrl) namedNodeObject;
                                    addEditLocalNode(isEdit, nodeId, appDir, isAppFile, isAppFile ? new File(appFileString) : null
                                    , configFileNameString, configText, appFileString, namedNodeUrl, currentNodeData, onSucceeded, onFailed);
                                }
                    
                            }, onFailed);
                        }, (checkFailed)->{
                            
                                Throwable throwable = checkFailed.getSource().getException();

                                Utils.returnException("Invalid directory : "  + throwable != null ? throwable.getMessage() : "err", getExecService(), onFailed);
                            
                        });
                    
                }, onCanceled->{
          
                    Throwable throwable = onCanceled.getSource().getException();

                    Utils.returnException("Not Verified" + (throwable != null ? (": " + throwable.getMessage()) : ""), getExecService(), onFailed);
                
                });
                 
            }else{
                Utils.returnException(NoteConstants.ERROR_INVALID, getExecService(), onFailed);
            }
        }else{
            Utils.returnException(NoteConstants.ERROR_INVALID, getExecService(), onFailed);
        }
    }

    private void addEditLocalNode(
        boolean isEdit,
        String nodeId, 
        File appDir, 
        boolean isAppFile, 
        File appFile, 
        String configName, 
        String configText, 
        String appFileString, 
        NamedNodeUrl namednode, 
        ErgoNodeLocalData currentNodeData, 
        EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        
        if(isEdit){
            
            currentNodeData.updateData(appDir, isAppFile, appFile, configName, configText, namednode,onFinished->{
                save();
                Utils.returnObject(NoteConstants.VERIFIED, getExecService(), onSucceeded);
            
            }, onFailed);
            
            return;
            
        
        }else{

            try {
                ErgoNodeLocalData localNodeData = new ErgoNodeLocalData(nodeId, appDir, isAppFile, appFile, configName, configText, namednode, this);
                addNode(localNodeData, true);

                Utils.returnObject(NoteConstants.VERIFIED, getExecService(), onSucceeded);
                
                return;
            } catch (Exception e) {
                
                Utils.returnException(e, getExecService(), onFailed);
                return;
                
            }
        }

    }

    public void editRemoteNode(JsonObject note, NetworkInformation networkInformation,  EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        if(note != null && note.get("data") != null){
            addEditRemoteNode(true, note, networkInformation, onSucceeded, onFailed);
        }else{
            Utils.returnException("Data element required", getExecService(), onFailed);
        }
    }

    public void addRemoteNode(JsonObject note, NetworkInformation networkInformation,  EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        if(note != null && note.get("data") != null){
            addEditRemoteNode(false, note, networkInformation, onSucceeded, onFailed);
        }else{
            Utils.returnException("Data element required", getExecService(), onFailed);
        }
    }


    
    public void addEditRemoteNode(boolean isEdit, JsonObject note, NetworkInformation networkInformation, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        if(note != null && note.get("data") != null){
            
            JsonElement dataElement = note.get("data");
            JsonObject dataJson = dataElement != null ? dataElement.getAsJsonObject() : null;
            long timeStamp = System.currentTimeMillis();
            String dataId = isEdit ? NoteConstants.getJsonId(dataJson) : null;

            JsonElement namedNodeElement = dataJson != null ? dataJson.get("namedNode") : null;
            JsonObject namedNodeObject = namedNodeElement != null && namedNodeElement.isJsonObject() ? namedNodeElement.getAsJsonObject() : null;

            if(namedNodeObject != null && (isEdit ? dataId != null : true)){
                
                getNetworksData().verifyAppKey(ErgoNodes.NAME, note, networkInformation, timeStamp, onVerified->{
                    

                     m_ergoNetworkData.getErgoNetworkControl().createNamedNode(namedNodeObject, onCreated->{
                        Object sourceObject = onCreated.getSource().getValue();
                        if(sourceObject != null && sourceObject instanceof NamedNodeUrl){
                            
                            NamedNodeUrl nodeUrl = (NamedNodeUrl) sourceObject;        
                            
                            
                            ErgoNodeData existingNode = isEdit ? getNodeById(dataId) : getRemoteNodeByUrl(nodeUrl.getUrlString());
                
                            if(isEdit){
                                if(existingNode != null){

                                    existingNode.updateUrl(nodeUrl);
                                    save();
                                    getErgoNetwork().sendMessage(NoteConstants.LIST_UPDATED, timeStamp, ErgoConstants.NODE_NETWORK, dataId);
                                }else{
                                    Utils.returnException(NoteConstants.ERROR_NOT_FOUND+":"+dataId, getExecService(), onFailed);
                                    return;
                                }

                                
                            }else{
                                if(existingNode != null){
                                    Utils.returnException(NoteConstants.ERROR_EXISTS + ":" +existingNode.getNetworkId(), getExecService(), onFailed);
                                    return;
                                }
                                
                                String nodeId = getNewId();
                                ErgoNodeData ergoNodeData = new ErgoNodeData(nodeId, nodeUrl.getName(), ErgoNodeData.LIGHT_CLIENT, this, nodeUrl);

                            
                                addRemoteNode(ergoNodeData, true);

                                Utils.returnObject(ergoNodeData.getJsonObject(), getExecService(), onSucceeded);
                            }
                            

                            
                        
                        }
                     }, onFailed);
    
                }, onFailed);
                
            }
        }else{
            Utils.returnException("Data element required", getExecService(), onFailed);
        }

    }
 
    public ExecutorService getExecService(){
        return getNetworksData().getExecService();
    }



}
