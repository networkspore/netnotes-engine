package io.netnotes.engine.networks.ergo;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.ErrorTooltip;
import io.netnotes.engine.KeyMenu;
import io.netnotes.engine.KeyMenuItem;
import io.netnotes.engine.NamedNodeUrl;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.NoteMsgInterface;
import io.netnotes.engine.Stages;
import io.netnotes.engine.SubmitButton;
import io.netnotes.engine.Utils;
import io.netnotes.engine.apps.AppConstants;
import io.netnotes.engine.apps.ergoDex.ErgoDex;
import io.netnotes.friendly_id.FriendlyId;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.event.EventHandler;


public class ErgoNetworkControl {
    private static final String NETWORK_ID = "ERGO_NETWORK_CONTROL";
   
    private final String networkMenuKey = FriendlyId.createFriendlyId();
    private final String manageNetworksKey = FriendlyId.createFriendlyId();
    private final String nodesMenuKey = FriendlyId.createFriendlyId();
    private final String explorersMenuKey = FriendlyId.createFriendlyId();

    private final String addRemoteKey = String.format("%-20s", "➕    [ Add remote… ]");
    private final String addLocalNodeKey =  String.format("%-20s", "⇲    [ Add local… ]");

    private final String localNodesKey = String.format("%-20s", "🖳   Local");
    private final String remoteNodesKey = String.format("%-20s", "🖧   Remote");

    private final String removeNodeKey = String.format("%-20s", "🗑    [ Remove Nodes… ]");

    public static final String DISABLED_NETWORK_TEXT = "Network: (disabled)";
    public static final String UNAVAILBLE_NETWORK_TEXT = "Network: (unavailable)";
    public static final String NOT_SELECTED = "Not selected";
    public static final Image UNAVAILABLE_ICON = new Image(AppConstants.UNAVAILBLE_ICON);

    private String m_networkId;
    private String m_locationId = null;
    private SimpleObjectProperty<JsonObject> m_networkObject = new SimpleObjectProperty<>(null);
    private SimpleObjectProperty<Image> m_networkImage = new SimpleObjectProperty<>(null);
    private NetworksData m_networksData = null;
    private NoteInterface m_networkInterface = null;
    private Future<?> m_connectFuture = null;
    private NoteMsgInterface m_networkMsgInterface = null;


    private SimpleStringProperty m_ergoNetworkIdProperty = new SimpleStringProperty(ErgoNetwork.NETWORK_ID);
    private SimpleLongProperty m_networkChecked = new SimpleLongProperty();

    private Future<?> m_updateExplorersFuture = null;
    private Future<?> m_updateNodesFuture = null;
    private SimpleObjectProperty<JsonArray> m_nodesArray = new SimpleObjectProperty<>();
    private SimpleObjectProperty<JsonArray> m_explorersArray = new SimpleObjectProperty<>();

    private String m_defaultNodeId = NamedNodeUrl.PULIC_NODE_1;
    private String m_defaultExplorerId = ErgoPlatformExplorerData.ERGO_PLATFORM_EXPLORER;

    private SimpleObjectProperty<JsonObject> m_nodeObjectProperty = new SimpleObjectProperty<>();
    private SimpleObjectProperty<JsonObject> m_explorerObjectProperty = new SimpleObjectProperty<>();

    private int m_networkConnections = 0;

    private ErrorTooltip m_errTooltip = null;

    public ErgoNetworkControl(String networkId, String locationId, NetworksData networksData){
        m_networkId = networkId;
        m_networksData = networksData;
        m_locationId = locationId;
        getData();
    }

    public void setErrorTooltip(ErrorTooltip errTooltip){
        m_errTooltip = errTooltip;
    }

    public ErrorTooltip getErrorTooltip(){
        return m_errTooltip;
    }


    public void getData(){
        if(m_networkId != null){
            m_networksData.getData("data", m_networkId, NETWORK_ID, ErgoNetwork.NETWORK_ID, (onComplete)->{
                Object obj = onComplete.getSource().getValue();
                JsonObject json = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
                openJson(json); 
            });
        }
    }

    protected void openJson(JsonObject json){
        JsonElement networkIdElement = json != null ? json.get("ergoNetworkId") : null;
        JsonElement nodeIdElement = json != null ? json.get("nodeId") : null;
        JsonElement explorerIdElement = json != null ? json.get("explorerId") : null;

        String ergoNetworkId = networkIdElement != null ? (networkIdElement.isJsonNull() ? null : networkIdElement.getAsString() ) : ErgoDex.NETWORK_ID;
        m_defaultNodeId = nodeIdElement != null ? (nodeIdElement.isJsonNull() ? null : nodeIdElement.getAsString()) : NamedNodeUrl.PULIC_NODE_1;
        m_defaultExplorerId = explorerIdElement != null ? (explorerIdElement.isJsonNull() ? null : explorerIdElement.getAsString()) : ErgoPlatformExplorerData.ERGO_PLATFORM_EXPLORER;
        
        m_ergoNetworkIdProperty.set(ergoNetworkId);

        if(m_networkConnections > 0){
            connectToNetwork();
        }
    }


    public void addNetworkConnection(){
        m_networkConnections++;
        if(m_networkConnections == 0){
            connectToNetwork();
        }
    }

    public void removeNetworkConnection(){
        m_networkConnections--;
        if(m_networkConnections == 0){
            disconnectNetwork();
        }
    }

    public void updateDefault(){
        if(m_defaultNodeId != null){
           updateNode();
        }else{
            m_nodeObjectProperty.set(null);
        }
        if(m_defaultExplorerId != null){
            updateExplorer();
        }else{
            m_explorerObjectProperty.set(null);
        }
    }

    public void updateNode(){
        updateNode(null);
    }
    public Future<?> updateNode(EventHandler<WorkerStateEvent> onError){
        return getNodeObjectById(m_defaultNodeId, onSucceeded->{
            Object obj = onSucceeded.getSource().getValue();
            m_nodeObjectProperty.set(obj != null && obj instanceof JsonObject ? (JsonObject) obj : null);
            if(onError != null && (obj == null || obj != null && !(obj instanceof JsonObject))){
                Utils.returnException(NoteConstants.ERROR_NOT_FOUND, getExecService(), onError);
            }
        }, onFailed->{
            m_nodeObjectProperty.set(null);
            if(onError != null){
                Throwable throwable = onFailed.getSource().getException();
                if(throwable != null && throwable instanceof Exception){
                    Utils.returnException((Exception) throwable, getExecService(), onError);
                }else{
                    Utils.returnException(throwable != null ? throwable.getMessage() : NoteConstants.ERROR_INVALID, getExecService(), onError);
                }
            }
        });
    }

    public void updateExplorer(){
        updateExplorer(null);
    }
    public Future<?> updateExplorer(EventHandler<WorkerStateEvent> onError){
        return getExplorerObjectById(m_defaultNodeId, onSucceeded->{
            Object obj = onSucceeded.getSource().getValue();
            m_explorerObjectProperty.set(obj != null && obj instanceof JsonObject ? (JsonObject) obj : null);
        
            if(onError != null && (obj == null || obj != null && !(obj instanceof JsonObject))){
                Utils.returnException(NoteConstants.ERROR_NOT_FOUND, getExecService(), onError);
            }
        
        }, onFailed->{
            m_explorerObjectProperty.set(null);
            if(onError != null){
                Throwable throwable = onFailed.getSource().getException();
                if(throwable != null && throwable instanceof Exception){
                    Utils.returnException((Exception) throwable, getExecService(), onError);
                }else{
                    Utils.returnException(throwable != null ? throwable.getMessage() : NoteConstants.ERROR_INVALID, getExecService(), onError);
                }
            }
        });
    }


    protected void save(){
        if(m_networkId != null){
            m_networksData.save("data", m_networkId, NETWORK_ID, ErgoNetwork.NETWORK_ID, getJsonObject());
        }
    }

      public void setErgoNetworkId(String networkId){
        if(m_ergoNetworkIdProperty.get() != null){
            disconnectNetwork();
        }
        m_ergoNetworkIdProperty.set(networkId);

        save();
    }

    public String getErgoNetworkId(){
        return m_ergoNetworkIdProperty.get();
    }

    public ReadOnlyStringProperty ergoNetworkIdProperty(){
        return m_ergoNetworkIdProperty;
    }

    public ReadOnlyObjectProperty<JsonObject> networkObjectProperty(){
        return m_networkObject;
    }

    public ReadOnlyObjectProperty<Image> networkImageProperty(){
        return m_networkImage;
    }

    public String getNodeId(){
        JsonObject nodeObject = getNodeObject();
        return nodeObject != null ? NoteConstants.getJsonId(nodeObject) : null;
    }

    public String getExplorerId(){
        JsonObject explorerObject = getExplorerObject();
        return explorerObject != null ? NoteConstants.getJsonId(explorerObject) : null;
    }

    public JsonObject getJsonObject(){
        JsonObject json = new JsonObject();
        json.addProperty("ergoNetworkId", getErgoNetworkId());
        json.addProperty("defaultNodeId", m_defaultNodeId);
        json.addProperty("defaultExplorerId", m_defaultExplorerId);
        return json;
    }

      public NoteInterface getErgoNetworkInterface(){
        String networkId = getErgoNetworkId();
        if(m_networkInterface == null && networkId != null){
            m_networkInterface = getNetworksData().getNetworkInterface(networkId);
            return m_networkInterface;
        }else{
            if(getNetworksData().getNetworkInterface(networkId) != null){
                return m_networkInterface;
            }else{
                disconnectNetwork();
            }
        }
        return null;
    }

    public NetworksData getNetworksData(){
        return m_networksData;
    }

    public ExecutorService getExecService(){
        return getNetworksData().getExecService();
    }


    public void updateNetworkMenu( ObservableList<MenuItem> networkMenu, Tooltip tooltip, ImageView imageView){
        long timeStamp = System.currentTimeMillis();
       
        String ergoNetworkId = getErgoNetworkId();

        NoteInterface networkInterface = getErgoNetworkInterface();

        boolean isNetworkObj = isNetworkObject();

        KeyMenu isMenuItem = KeyMenu.getKeyMenu(networkMenu, networkMenuKey);
        
        KeyMenu networkMenuItem = isMenuItem != null ? isMenuItem : new KeyMenu(networkMenuKey, UNAVAILBLE_NETWORK_TEXT, timeStamp, KeyMenu.VALUE_NOT_KEY);

        if(isMenuItem == null){
            networkMenu.add(networkMenuItem);
        }

        if(ergoNetworkId != null){

            if(networkInterface != null && isNetworkObj){
                JsonObject networkObject = getNetworkObject();

                String name = NoteConstants.getNameFromNetworkObject(networkObject);

                NoteConstants.getAppIconFromNetworkObject(networkObject, getExecService(), onImage->{
                    Object imgObj = onImage.getSource().getValue();
                    if(imgObj != null && imgObj instanceof Image){
                        
                        setMenuMsg(imageView, (Image) imgObj, name, networkMenuItem, tooltip, timeStamp);
                    }else{
                        setMenuMsg(imageView, Stages.unknownImg, name, networkMenuItem, tooltip, timeStamp);
                    }
                }, onImageFailed->{
                     setMenuMsg(imageView, Stages.unknownImg, name, networkMenuItem, tooltip, timeStamp);
                });
            }else{
                setMenuMsg(imageView, UNAVAILABLE_ICON, UNAVAILBLE_NETWORK_TEXT, networkMenuItem, tooltip, timeStamp);
            }
        }else{
            setMenuMsg(imageView, UNAVAILABLE_ICON, DISABLED_NETWORK_TEXT, networkMenuItem, tooltip, timeStamp);
        }

        Utils.removeOldKeys(networkMenu, timeStamp);

        List<NetworkInformation> networkList = getNetworksData().getNetworksContainsAllKeyWords("ergo","explorers", "nodes");

        for(NetworkInformation networkInfo : networkList){
            KeyMenuItem currentItem = KeyMenuItem.getKeyMenuItem(networkMenuItem.getItems(), networkInfo.getNetworkId());

            String name = (ergoNetworkId != null && networkInfo.getNetworkId().equals(ergoNetworkId) ? "* ": "  ") + networkInfo.getNetworkName();

            if(currentItem != null){
                currentItem.setValue(name, timeStamp);
            }else{
                KeyMenuItem newItem = new KeyMenuItem(networkInfo.getNetworkId(), name, timeStamp, KeyMenuItem.VALUE_NOT_KEY);
                newItem.setOnAction(e->{
                    if(ergoNetworkId == null || (ergoNetworkId != null && !ergoNetworkId.equals(networkInfo.getNetworkId()))){
                       
                        setErgoNetworkId(networkInfo.getNetworkId());
                        
                    }
                    updateNetworkMenu(networkMenu, tooltip, imageView);
                });
                networkMenuItem.getItems().add(newItem);
            }
        }
        KeyMenuItem isManageNetworksItem = KeyMenuItem.getKeyMenuItem(networkMenuItem.getItems(), manageNetworksKey);

        KeyMenuItem manageNetworks = isManageNetworksItem != null ? isManageNetworksItem : new KeyMenuItem(manageNetworksKey, "Manage networks…", timeStamp, KeyMenuItem.VALUE_NOT_KEY);
        if(isManageNetworksItem == null){
            manageNetworks.setOnAction(e->{
                getNetworksData().openStatic(NetworksData.NETWORKS);
            });
            networkMenuItem.getItems().add(manageNetworks);
        }else{
            manageNetworks.setTimeStamp(timeStamp);
        }

        if(isNetworkObj){
            JsonObject nodeObject = getNodeObject();
            

            KeyMenu isNodesMenu = KeyMenu.getKeyMenu(networkMenuItem.getItems(), nodesMenuKey);
            String nodeName = nodeObject != null ? NoteConstants.getJsonName(nodeObject) : "(select node)";
            

            KeyMenu nodesMenu = isNodesMenu != null ? isNodesMenu : new KeyMenu(nodesMenuKey, nodeName, timeStamp, KeyMenu.VALUE_NOT_KEY);
        
            if(isNodesMenu != null){
                nodesMenu.setValue(nodeName, timeStamp);
            }else{
                networkMenuItem.getItems().add(nodesMenu);
            }

            updateNodesMenu(nodesMenu.getItems(), timeStamp);
         
            JsonObject currentExplorerObject = getExplorerObject();

            KeyMenu isExplorerMenu = KeyMenu.getKeyMenu(networkMenuItem.getItems(), explorersMenuKey);
            String explorerName = currentExplorerObject != null ? NoteConstants.getJsonName(currentExplorerObject) : "(select explorer)";
            KeyMenu explorersMenu = isExplorerMenu != null ? isExplorerMenu : new KeyMenu(explorersMenuKey, explorerName, timeStamp, KeyMenu.VALUE_NOT_KEY);
        
            if(isExplorerMenu != null){
                explorersMenu.setValue(explorerName, timeStamp);
            }else{
                networkMenuItem.getItems().add(explorersMenu);
            }

            updateExplorersMenu(explorersMenu.getItems(), timeStamp);
        }
        
        Utils.removeOldKeys(networkMenuItem.getItems(), timeStamp);
    }



    public void updateNodesMenu(ObservableList<MenuItem> nodesMenu, long timeStamp){
           JsonArray nodesArray = getNodesArray();
            
            if(nodesArray != null){
                String nodeId = getNodeId();
                for(JsonElement element : nodesArray){
                    JsonObject json = element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
                    if(json != null){
                       
                        String id = NoteConstants.getJsonId(json);
                        String jsonName = NoteConstants.getJsonName(json);
                            
                        KeyMenuItem isNodeItem = KeyMenuItem.getKeyMenuItem(nodesMenu, id);

                        String name = (nodeId != null && nodeId.equals(id) ? "* " : "  ") + jsonName;

                        if(isNodeItem != null){
                            isNodeItem.setValue(name, timeStamp);
                        }else{
                            KeyMenuItem nodeMenuItem = new KeyMenuItem(id, name, timeStamp, KeyMenuItem.VALUE_NOT_KEY);
                            nodeMenuItem.setOnAction(e->{
                                setNodeObject(json);
                            });
                            nodesMenu.add(nodeMenuItem);
                        }

                      
                    }
                }
            }

            Utils.removeOldKeys(nodesMenu, timeStamp);

    }


    public void updateNodeOptionsMenu(List<MenuItem> nodeMenu){
        updateNodeOptionsMenu(nodeMenu, System.currentTimeMillis());
    }

    

    public void updateNodeOptionsMenu(List<MenuItem> nodeOptionsMenu, long timeStamp){

   



             KeyMenu currentRemoteNodesItem = KeyMenu.getKeyMenu( nodeOptionsMenu, remoteNodesKey);
        boolean isRemoteNodesItem = currentRemoteNodesItem != null;
        KeyMenu remoteNodesMenu = isRemoteNodesItem ? currentRemoteNodesItem : new KeyMenu(remoteNodesKey, "", timeStamp, KeyMenuItem.KEY_NOT_VALUE);
        
        if(!isRemoteNodesItem){

            nodeOptionsMenu.add(remoteNodesMenu);
        }else{
            remoteNodesMenu.setTimeStamp(timeStamp);
        }

        KeyMenu currentIntstallNodeItem = KeyMenu.getKeyMenu( nodeOptionsMenu, localNodesKey);
        boolean isIntstallNodeItem = currentIntstallNodeItem != null;
        KeyMenu localNodesMenu = isIntstallNodeItem ? currentIntstallNodeItem : new KeyMenu(localNodesKey, "", timeStamp, KeyMenu.KEY_NOT_VALUE);
        
        if(!isIntstallNodeItem){
            JsonArray nodesArray = m_nodesArray.get();

            for(JsonElement nodeElement : nodesArray){
                JsonObject nodeObject = nodeElement.isJsonObject() ? nodeElement.getAsJsonObject() : null;

                boolean isNodeLocal = ErgoNodeLocalData.isClientLocal(nodeObject);
                 String nodeId = NoteConstants.getJsonId(nodeObject);
                  if(nodeId != null){
                    String nodeName = NoteConstants.getJsonName(nodeObject);
                    if(isNodeLocal){
                        KeyMenuItem currentNodeItem = KeyMenuItem.getKeyMenuItem(localNodesMenu.getItems(), nodeId);
                        boolean isNodeItem = currentNodeItem != null;

                        KeyMenuItem nodeItem = isNodeItem ? currentNodeItem : new KeyMenuItem(nodeId, nodeName, timeStamp, KeyMenuItem.VALUE_NOT_KEY);
                        if(!isNodeItem){
                            nodeItem.setOnAction(action -> {
                                manageLocalNode(nodeId);
                            });

                            localNodesMenu.getItems().add(nodeItem);
                        }else{
                            nodeItem.setValue(nodeName, timeStamp);
                        }
                        
                    
                    }else{
                        KeyMenuItem currentNodeItem = KeyMenuItem.getKeyMenuItem(remoteNodesMenu.getItems(), nodeId);
                        boolean isNodeItem = currentNodeItem != null;

                        KeyMenuItem nodeItem = isNodeItem ? currentNodeItem : new KeyMenuItem(nodeId, nodeName, timeStamp, KeyMenuItem.VALUE_NOT_KEY);
                        if(!isNodeItem){
                            nodeItem.setOnAction(action -> {
                                manageRemoteNode(nodeId);
                            });

                            remoteNodesMenu.getItems().add(nodeItem);
                        }else{
                            nodeItem.setValue(nodeName, timeStamp);
                        }
                    }
                }

            }

        }else{
            localNodesMenu.setTimeStamp(timeStamp);
        }
     

        KeyMenuItem currentOpenNodeMenuItem = KeyMenuItem.getKeyMenuItem( localNodesMenu.getItems(), addLocalNodeKey);
        boolean isOpenNodeMenuItem = currentOpenNodeMenuItem != null;
        KeyMenuItem openNodeMenuItem = isOpenNodeMenuItem ? currentOpenNodeMenuItem : new KeyMenuItem(addLocalNodeKey,"", timeStamp, KeyMenuItem.KEY_NOT_VALUE);
          
        if(!isOpenNodeMenuItem){
            openNodeMenuItem.setOnAction(action -> {
               addLocalNode();
            });
            localNodesMenu.getItems().add(openNodeMenuItem);
        }else{
            openNodeMenuItem.setTimeStamp(timeStamp);
        }


        KeyMenuItem currentAddRemoteItem = KeyMenuItem.getKeyMenuItem( remoteNodesMenu.getItems(), addRemoteKey);
        boolean isAddRemoteItem = currentAddRemoteItem != null;
        KeyMenuItem addRemoteItem = isAddRemoteItem ? currentAddRemoteItem : new KeyMenuItem(addRemoteKey, "", timeStamp, KeyMenuItem.KEY_NOT_VALUE);
        
        if(!isAddRemoteItem){
            addRemoteItem.setOnAction(action -> {
                addRemoteNode();
            });
            remoteNodesMenu.getItems().add(addRemoteItem);
        }else{
            addRemoteItem.setTimeStamp(timeStamp);
        }
        
        Utils.removeOldKeys(remoteNodesMenu.getItems(), timeStamp); 

        Utils.removeOldKeys(localNodesMenu.getItems(), timeStamp); 

        KeyMenuItem currentRemoveNodeMenuItem = KeyMenuItem.getKeyMenuItem( nodeOptionsMenu, removeNodeKey);
        boolean istRemoveNodeMenuItem = currentRemoveNodeMenuItem != null;
        KeyMenuItem removeNodeMenuItem = istRemoveNodeMenuItem ? currentRemoveNodeMenuItem : new KeyMenuItem(removeNodeKey, "", timeStamp, KeyMenuItem.KEY_NOT_VALUE);
        

       if(!isOpenNodeMenuItem){
            removeNodeMenuItem.setOnAction(e->{
              //  removeNodes();
            });
            nodeOptionsMenu.add(removeNodeMenuItem);
        }else{
            removeNodeMenuItem.setTimeStamp(timeStamp);
        }



        Utils.removeOldKeys(nodeOptionsMenu, timeStamp);
    }

    public void updateExplorersMenu(ObservableList<MenuItem> explorersMenu, long timeStamp){
        String explorerId = getExplorerId();

        JsonArray explorersArray = getExplorersArray();

        if(explorersArray != null){
            for(JsonElement element : explorersArray){
                JsonObject json = element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
                String id = NoteConstants.getJsonId(json);
                String jsonName = NoteConstants.getJsonName(json);
                if(json != null && id != null){
                    
                    String name = (explorerId != null && explorerId.equals(id) ? "* " : "  ") + (jsonName != null ? jsonName :  "unnamed node");
                    
                    KeyMenuItem isNodeItem = KeyMenuItem.getKeyMenuItem(explorersMenu, id);

                    if(isNodeItem != null){
                        isNodeItem.setValue(name, timeStamp);
                    }else{
                        KeyMenuItem explorerMenuItem = new KeyMenuItem(id, name, timeStamp, KeyMenuItem.VALUE_NOT_KEY);
                        explorerMenuItem.setOnAction(e->{
                            setExplorerObject(json);
                        });
                        explorersMenu.add(explorerMenuItem);
                    }

                
                }
            }
        }

        Utils.removeOldKeys(explorersMenu, timeStamp);

    }

    public void setExplorerObject(JsonObject explorerObject){
        setDefaultExplorerId(explorerObject != null ? NoteConstants.getJsonId(explorerObject) : null);
        m_explorerObjectProperty.set(explorerObject);
    }

    public void setNodeObject(JsonObject nodeObject){
        setDefaultNodeId(nodeObject != null ? NoteConstants.getJsonId(nodeObject) : null);
        m_nodeObjectProperty.set(nodeObject);
    }

    public void setDefaultNodeId(String id){
        m_defaultNodeId = id;
        save();
    }

    public void setDefaultExplorerId(String id){
        m_defaultExplorerId = id;
        save();
    }

    public ReadOnlyObjectProperty<JsonObject> nodeObjectProperty(){
        return m_nodeObjectProperty;
    }

    public JsonObject getNodeObject(){
        return m_nodeObjectProperty.get();
    }

    public String getNodeApiUrl(){
        JsonObject nodeObject = getNodeObject();
        if(nodeObject != null){
            JsonElement urlElement = nodeObject.get("apiUrl");
            if(urlElement != null && !urlElement.isJsonNull() && urlElement.isJsonPrimitive()){
                return urlElement.getAsString();
            }
        }
        return null;
    }

    public ReadOnlyObjectProperty<JsonObject> explorerObjectProperty(){
        return m_explorerObjectProperty;
    }

    public JsonObject getExplorerObject(){
        return m_explorerObjectProperty.get();
    }

    public String getExplorerApiUrl(){
        JsonObject explorerObject = getExplorerObject();
        if(explorerObject != null){
            JsonElement urlElement = explorerObject.get("apiUrl");
            if(urlElement != null && !urlElement.isJsonNull() && urlElement.isJsonPrimitive()){
                return urlElement.getAsString();
            }
        }
        return null;
    }

    private void setMenuMsg(ImageView imageView, Image img, String msg, KeyMenu keyMenu, Tooltip tooltip, long timeStamp){
        imageView.setImage(Stages.unknownImg);
        if(keyMenu.getTimeStamp() < timeStamp){
            keyMenu.setValue(msg, timeStamp);
            tooltip.setText(msg);
        }
    }

     public void checkNetwork(){
        NetworksData networksData = getNetworksData();
        
        String networkId = getErgoNetworkId();

        boolean networkChanged = networkId != null && networksData.getApp(networkId) == null;

        if(networkChanged){
            disconnectNetwork();
        }

        m_networkChecked.set(System.currentTimeMillis());
    }

    public String getLocationId(){
        return m_locationId;
    }

    public Future<?> searchExplorer(String cmd, String value, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();
        String explorerId = getExplorerId();

        if(ergoNetworkInterface != null){

            JsonObject note = NoteConstants.getCmdObject(cmd, ErgoConstants.EXPLORER_NETWORK, m_locationId);
            note.addProperty("value", value);
            note.addProperty("id", explorerId);
            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }
    }

    public Future<?> getExplorerObjectById(String id, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getExplorerObjectById",  ErgoConstants.EXPLORER_NETWORK, getLocationId());
            note.addProperty("id", id);
            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }
    }
    
    public Future<?> getNodeObjectById(String id, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getNodeObjectById",  ErgoConstants.NODE_NETWORK, getLocationId());
            note.addProperty("id", id);
            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }

    }



    public Future<?> addRemoteNode( EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){

            JsonObject note = NoteConstants.getCmdObject("addRemoteNode", ErgoConstants.NODE_NETWORK, getLocationId());
            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }

    }

    

    public Future<?> getBalance(String addressString, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getBalance",  ErgoConstants.EXPLORER_NETWORK, getLocationId());
            note.addProperty("address", addressString);
            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }

    }




    public Future<?> getTransactionsByAddress(String addressString, JsonObject detailsObject, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getTransactionsByAddress", ErgoConstants.EXPLORER_NETWORK, getLocationId());
            note.addProperty("address", addressString);

            JsonElement startIndexElement = detailsObject != null ? detailsObject.get("startIndex") : null;
            JsonElement limitElement = detailsObject != null ?  detailsObject.get("limit"): null;
            JsonElement conciseElement = detailsObject != null ?  detailsObject.get("concise"): null;
            JsonElement fromHeightElement = detailsObject != null ?  detailsObject.get("fromHeight"): null;
            JsonElement toHeightElement = detailsObject != null ?  detailsObject.get("toHeight"): null;

            if(startIndexElement != null){
                note.add("startIndex", startIndexElement);
            }
            if(limitElement != null){
                note.add("limit",limitElement);
            }
            if(conciseElement != null){
                note.add("concise", conciseElement);
            }
            if(fromHeightElement != null){
                note.add("fromHeight", fromHeightElement);
            }
            if(toHeightElement != null){
                note.add("toHeight", toHeightElement);
            }


            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        } 

    }

    public Future<?> getTransactionViewsByAddress(String addressString, JsonObject detailsObject, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){

            JsonObject txNote = NoteConstants.getCmdObject("getTransactionViewsByAddress", ErgoConstants.EXPLORER_NETWORK, getLocationId());


            txNote.addProperty("address", addressString);

            JsonElement startIndexElement = detailsObject != null ? detailsObject.get("startIndex") : null;
            JsonElement limitElement = detailsObject != null ? detailsObject.get("limit") : null;
            JsonElement conciseElement = detailsObject != null ? detailsObject.get("concise") : null;
            JsonElement fromHeightElement = detailsObject != null ? detailsObject.get("fromHeight") : null;
            JsonElement toHeightElement = detailsObject != null ? detailsObject.get("toHeight") : null;

            if(startIndexElement != null){
                txNote.add("startIndex", startIndexElement);
            }
            if(limitElement != null){
                txNote.add("limit",limitElement);
            }
            if(conciseElement != null){
                txNote.add("concise", conciseElement);
            }
            if(fromHeightElement != null){
                txNote.add("fromHeight", fromHeightElement);
            }
            if(toHeightElement != null){
                txNote.add("toHeight", toHeightElement);
            }

            return ergoNetworkInterface.sendNote(txNote, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }
    }

    public Future<?> getUnspentBoxesByAddress(String addressString, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getUnspentByAddress", ErgoConstants.EXPLORER_NETWORK, getLocationId());
            note.addProperty("value", addressString);
            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }

    }

    public JsonObject getNetworkObject(){
        return m_networkObject.get();
    }

    public boolean isNetworkObject(){
        return m_networkObject.get() != null;
    }

    public void connectToNetwork(){
        connectToNetwork(null);
    }

    public Future<?> getNetworkObject(NoteInterface networkInterface, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
       
        return networkInterface.sendNote(NoteConstants.getCmdObject("getNetworkObject", getLocationId()),onSucceeded, onFailed);
    }

    public void connectToNetwork(EventHandler<WorkerStateEvent> onFailed){

        NoteInterface networkInterface = getErgoNetworkInterface();
        if (networkInterface != null && m_networkMsgInterface == null && m_connectFuture == null || (networkInterface != null && (m_connectFuture != null && m_connectFuture.isDone()) && getNetworkObject() == null)) {
            removeNetworkMsgInterface(networkInterface);
            updateDefault();
            m_connectFuture = getNetworkObject(networkInterface, onNetworkObject->{
                    
                Object successObject = onNetworkObject.getSource().getValue();

                if (successObject != null) {
                    JsonObject networkObject = (JsonObject) successObject;
                    setNetworkObject(networkObject);
                   

                    m_networkMsgInterface = new NoteMsgInterface() {
                        private String interfaceId = FriendlyId.createFriendlyId();
                        @Override
                        public String getId() {
                            return interfaceId;
                        }

                        @Override
                        public void sendMessage(int code, long timestamp, String networkId, String msg) {
                            switch(code){
                                case NoteConstants.LIST_ITEM_ADDED:
                                case NoteConstants.LIST_ITEM_REMOVED:
                               
                                 case NoteConstants.UPDATED:
                                    switch(networkId){
                                        case ErgoConstants.NODE_NETWORK:
                                            updateNodes();
                                        break;
                                        case ErgoConstants.EXPLORER_NETWORK:
                                            updateExplorers();
                                        break;
                                    }
                                    if(code == NoteConstants.UPDATED){
                                        updateNetworkObject();
                                    }

                            }
                        }
                        
                    };
                    
                    updateNodes();
                    updateExplorers();
                    networkInterface.addMsgListener(m_networkMsgInterface);
                }
            },  onError ->{});
            
        }
        
    }

    private void setNetworkObject(JsonObject networkObject){
        m_networkObject.set(networkObject);
        if(networkObject != null){
            NoteConstants.getAppIconFromNetworkObject(networkObject, getExecService(), onImage->{
                Object onImageObject = onImage.getSource().getValue();

                m_networkImage.set(onImageObject != null && onImageObject instanceof Image ? (Image) onImageObject : null);
            }, onImageFailed->{
                    m_networkImage.set(null);
            });
        }else{
            m_networkImage.set(null);
        }
    }

    public ReadOnlyObjectProperty<JsonArray> nodesArrayProperty(){
        return m_nodesArray;
    }

    public JsonArray getNodesArray(){
        return m_nodesArray.get();
    }

    public ReadOnlyObjectProperty<JsonArray> explorersArrayProperty(){
        return m_explorersArray;
    }

    public JsonArray getExplorersArray(){
        return m_explorersArray.get();
    }

    public void updateNodes(){
        updateNodes(null);
    }

    public void updateNodes(EventHandler<WorkerStateEvent> onError){
        if(m_updateNodesFuture == null || (m_updateNodesFuture != null && (m_updateNodesFuture.isDone() || m_updateNodesFuture.isCancelled()))){
            m_updateNodesFuture = getNodes(onNodes->{
                Object obj = onNodes.getSource().getValue();
                if(obj != null && obj instanceof JsonArray){
                    m_nodesArray.set((JsonArray) obj);
                }else{
                    m_nodesArray.set(null);
                }
            }, onFailed->{
                m_nodesArray.set(null);
                if(onError != null){
                Throwable throwable = onFailed.getSource().getException();
                if(throwable != null && throwable instanceof Exception){
                    Utils.returnException((Exception) throwable, getExecService(), onError);
                }else{
                    Utils.returnException(throwable != null ? throwable.getMessage() : NoteConstants.ERROR_INVALID, getExecService(), onError);
                }
            }
            });
        }
    }

    public void updateNetworkObject(){
        updateNetworkObject(null);
    }

    public void setNetworkImageFromNetworkObject(JsonObject networkObject, EventHandler<WorkerStateEvent> onError){
         NoteConstants.getAppIconFromNetworkObject(networkObject, getExecService(), onImage->{
            Object imageObject = onImage.getSource().getValue();
            m_networkImage.set(imageObject != null && imageObject instanceof JsonObject ? (Image) imageObject : null);

         },onError != null ? onError : onFailed->{
            m_networkImage.set(null);
         });
    }

    public Future<?> updateNetworkObject(EventHandler<WorkerStateEvent> onError){
            NoteInterface networkInterface = getErgoNetworkInterface();

            return getNetworkObject(networkInterface, onNetworkObject->{
                    
                Object successObject = onNetworkObject.getSource().getValue();
                setNetworkObject(successObject != null && successObject instanceof JsonObject ? (JsonObject) successObject : null);
        
            }, onFailed ->{
                setNetworkObject(null);
                if(onError != null){
                Throwable throwable = onFailed.getSource().getException();
                if(throwable != null && throwable instanceof Exception){
                    Utils.returnException((Exception) throwable, getExecService(), onError);
                }else{
                    Utils.returnException(throwable != null ? throwable.getMessage() : NoteConstants.ERROR_INVALID, getExecService(), onError);
                }
            }
            });
    }

    public void updateExplorers(){
        updateExplorers(null);
    }

    public void updateExplorers(EventHandler<WorkerStateEvent> onError){
        if(m_updateExplorersFuture == null || (m_updateExplorersFuture != null && (m_updateExplorersFuture.isDone() || m_updateExplorersFuture.isCancelled()))){
            m_updateExplorersFuture = getExplorers(onSucceeded->{
                Object obj = onSucceeded.getSource().getValue();
                if(obj != null && obj instanceof JsonArray){
                    m_explorersArray.set((JsonArray) obj);
                }else{
                    m_explorersArray.set(null);
                    if(onError != null){
                        Utils.returnException(NoteConstants.ERROR_NOT_FOUND, getExecService(), onError);
                    }
                }
            }, onFailed->{
                m_explorersArray.set(null);
                if(onError != null){
                Throwable throwable = onFailed.getSource().getException();
                if(throwable != null && throwable instanceof Exception){
                    Utils.returnException((Exception) throwable, getExecService(), onError);
                }else{
                    Utils.returnException(throwable != null ? throwable.getMessage() : NoteConstants.ERROR_INVALID, getExecService(), onError);
                }
            }
            });
        }
    }

    public Future<?> getExplorers(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getExplorers", ErgoConstants.EXPLORER_NETWORK, getLocationId());

            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }

    }

    public Future<?> getNodes(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("getNodes", ErgoConstants.NODE_NETWORK, getLocationId());

            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }

    }

    public Future<?> getNodesAppDir(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();
        if(ergoNetworkInterface != null){
             JsonObject appDirNote = NoteConstants.getCmdObject("getAppDir", ErgoConstants.NODE_NETWORK, getLocationId());
             return ergoNetworkInterface.sendNote(appDirNote, onSucceeded, onFailed);
        }else{
             return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }

    }

    private void removeNetworkMsgInterface(NoteInterface networkInterface){
        if(networkInterface != null && m_networkMsgInterface != null){
            networkInterface.removeMsgListener(m_networkMsgInterface);
            m_networkMsgInterface = null;
        }
    }


    public void disconnectNetwork(){
        if (m_connectFuture != null && (!m_connectFuture.isDone() || !m_connectFuture.isCancelled())) {
            m_connectFuture.cancel(true);
        }
        setNetworkObject(null);
        removeNetworkMsgInterface(m_networkInterface);
        m_networkInterface = null;
        m_nodeObjectProperty.set(null);
        m_explorerObjectProperty.set(null);
    }

    public Future<?> updateLocalNode(String dir, NamedNodeUrl namedNode, boolean isAppFile, String appFile, String configFileName, String configString, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();
        
        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("updateLocalNode", ErgoConstants.NODE_NETWORK, getLocationId());

            JsonObject json = new JsonObject();

            if(configString != null){
                json.addProperty("configText", configString);
            }
            json.addProperty("configFileName", configFileName);
            
            json.addProperty("isAppFile", isAppFile);
            if(isAppFile){
                json.addProperty("appFile", appFile);
            }
            json.add("namedNode", namedNode.getJsonObject());
            json.addProperty("appDir", dir);
            note.add("data", json);

            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);

        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }
    }


    public static JsonObject getLocalNodeObject(String dir, NamedNodeUrl namedNode, boolean isAppFile, String appFile, String configFileName, String configString){
   
        JsonObject json = new JsonObject();

        if(configString != null){
            json.addProperty("configText", configString);
        }
        json.addProperty("configFileName", configFileName);
        
        json.addProperty("isAppFile", isAppFile);
        if(isAppFile){
            json.addProperty("appFile", appFile);
        }
        json.add("namedNode", namedNode.getJsonObject());
        json.addProperty("appDir", dir);

        return json;
    }

    public Future<?> createNamedNode(JsonObject namedNodeJson, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
          
        
        Task<NamedNodeUrl> task = new Task<NamedNodeUrl>() {
            @Override
            public NamedNodeUrl call() throws Exception {
                if(namedNodeJson != null){
                    return new NamedNodeUrl(namedNodeJson);
                }
                throw new NullPointerException("Url data unavailable");
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return getExecService().submit(task);       
    }


    public Future<?> updateApp(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();
        
        if(ergoNetworkInterface != null){
        
            String nodeId = getNodeId();

            if(nodeId != null){
                JsonObject note = NoteConstants.getCmdObject("updateApp");
                note.addProperty("locationId", m_locationId);
                note.addProperty("networkId", ErgoConstants.NODE_NETWORK);
                note.addProperty("id", nodeId);
                
                return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
                
            }else{
                return Utils.returnException(NOT_SELECTED, getExecService(), onFailed);
            }

        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }
    }

    public Future<?> searchExplorerByPage(String cmd, String value, int offset, String sortDirection, int limit,  EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface networkInterface = getErgoNetworkInterface();
        String id = getExplorerId();

        if(networkInterface != null){
           
            JsonObject note = NoteConstants.getCmdObject(cmd, ErgoConstants.EXPLORER_NETWORK, getLocationId());
            note.addProperty("id", id);
            note.addProperty("value", value);
            if(offset != -1){
                note.addProperty("offset", offset);
            }
            if(limit != -1){
                note.addProperty("limit", limit);
            }
            if(sortDirection != null){
                note.addProperty("sortDirection", sortDirection);
            }

            return networkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }
    }

    public Future<?> getStatus(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();
        
        if(ergoNetworkInterface != null){
        
            String nodeId = getNodeId();
            
            if(nodeId != null){
                JsonObject note = NoteConstants.getCmdObject("getStatus");
                note.addProperty("locationId", m_locationId);
                note.addProperty("id", nodeId);
                note.addProperty("networkId", ErgoConstants.NODE_NETWORK);

                return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
            }else{
                return Utils.returnException(NOT_SELECTED, getExecService(), onFailed);
            }

        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }
    }

    public Future<?> editLocalNode(JsonObject nodeObject, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("editLocalNode", ErgoConstants.NODE_NETWORK, getLocationId());
            note.add("data", note);
            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }
    }

    public Future<?> addLocalNode(JsonObject nodeObject, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("addLocalNode", ErgoConstants.NODE_NETWORK, getLocationId());
            note.add("data", note);
            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }
    }


   
    public void run(){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();
        
        if(ergoNetworkInterface != null){
            
            String nodeId = getNodeId();
            if(nodeId != null){
                JsonObject note = NoteConstants.getCmdObject("run");
                note.addProperty("locationId", m_locationId);
                note.addProperty("networkId",ErgoConstants.NODE_NETWORK);
                note.addProperty("id", nodeId);
                ergoNetworkInterface.sendNote(note, onSuceeded->{}, onFailed->{});
            }
        }
    }

    public void terminate(){
         NoteInterface ergoNetworkInterface = getErgoNetworkInterface();
        
        if(ergoNetworkInterface != null){
            String nodeId = getNodeId();
            if(nodeId != null){
                JsonObject note = NoteConstants.getCmdObject("terminate");
                note.addProperty("locationId", m_locationId);
                note.addProperty("networkId",ErgoConstants.NODE_NETWORK);
                note.addProperty("id", nodeId);
                ergoNetworkInterface.sendNote(note, onSuceeded->{}, onFailed->{});
            }
        }
    }

    public void addLocalNode(){
        manageLocalNode(null);
    }

    public NetworkInformation getNetworkInformation(){
        return getNetworksData().getLocationNetworkInformation(m_locationId);
    }
  

    public void manageLocalNode(String nodeId){

        JsonObject currentNodeObject = nodeId != null ? NoteConstants.getJsonObjectById(nodeId, getNodesArray()) : null;
        boolean isLocal = nodeId != null ? ErgoNodeLocalData.isClientLocal(currentNodeObject) : null;
        boolean isEdit = nodeId != null;
        if((isEdit && isLocal) || !isEdit ){
            SubmitButton submitButton = new SubmitButton(isEdit ? "Update" : "Install");
            
            NetworkInformation networkInformation = getNetworkInformation();

            if(networkInformation == null){
                if(getErrorTooltip() != null){
                    getErrorTooltip().showErrorTooltip("Parent network not available");
                }
                return;
            }

            ErgoNodeTabLocalInstall installNodeTab = new ErgoNodeTabLocalInstall(nodeId, currentNodeObject, networkInformation.getNetworkId(), networkInformation.getSmallIcon(), this, submitButton);
            String tabId = installNodeTab.getId();

            submitButton.setOnSubmit(onAddNode->{
                Object tabObj = onAddNode.getSource().getValue();
                if(tabObj != null && tabObj instanceof JsonObject){
                    JsonObject json = (JsonObject) tabObj;
                    
                    if(isEdit){
                        editLocalNode(json, onComplete->{
                            getNetworksData().getContentTabs().removeContentTab(tabId);
                            if(m_networkMsgInterface == null){
                                updateNodes();
                            }
                        }, onError->{
                            updateNodes();
                        });
                    }else{
                        addLocalNode(json, onComplete->{
                            getNetworksData().getContentTabs().removeContentTab(tabId);
                            if(m_networkMsgInterface == null){
                                updateNodes();
                            }
                        }, onError->{
                            updateNodes();
                        });
                    }
                    
                }

            });

            getNetworksData().getContentTabs().addContentTab(installNodeTab);
           
        }else{
            updateNodes();
        }
       
        
    }

    public Future<?> editRemoteNode(JsonObject nodeObject, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("editRemoteNode", ErgoConstants.NODE_NETWORK, getLocationId());
            note.add("data", note);
            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }
    }

    public Future<?> addRemoteNode(JsonObject nodeObject, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        NoteInterface ergoNetworkInterface = getErgoNetworkInterface();

        if(ergoNetworkInterface != null){
            JsonObject note = NoteConstants.getCmdObject("addRemoteNode", ErgoConstants.NODE_NETWORK, getLocationId());
            note.add("data", note);
            return ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException(UNAVAILBLE_NETWORK_TEXT, getExecService(), onFailed);
        }
    }

    public void addRemoteNode(){
        manageRemoteNode(null);
    }

    public void manageRemoteNode(String nodeId){

        JsonObject currentNodeObject = nodeId != null ? NoteConstants.getJsonObjectById(nodeId, getNodesArray()) : null;
        boolean isLocal = ErgoNodeLocalData.isClientLocal(currentNodeObject);

        if(!isLocal){
            nodeId = currentNodeObject == null ? null : nodeId;
            boolean isNodeData = currentNodeObject != null;
            
            SubmitButton submitButton = new SubmitButton(isNodeData ? "Update" : "Add");
            NetworkInformation networkInformation = getNetworkInformation();

            ErgoNodeTabAdd addTab = new ErgoNodeTabAdd(nodeId, currentNodeObject, networkInformation.getNetworkId(), networkInformation.getSmallIcon(), this, submitButton);
            String tabId = addTab.getId();
           
            submitButton.setOnSubmit(onAddNode->{
                Object tabObj = onAddNode.getSource().getValue();
                if(tabObj != null && tabObj instanceof JsonObject){
                    JsonObject json = (JsonObject) tabObj;

                    if(isNodeData){
                        editRemoteNode(json, onEdited ->{
                            getNetworksData().getContentTabs().removeContentTab(tabId);

                        }, submitButton.getOnError() != null ? submitButton.getOnError() : onFailed->{
                            getNetworksData().getContentTabs().removeContentTab(tabId);
                        }); 
                    }else{
                        addRemoteNode(json, onEdited ->{
                            getNetworksData().getContentTabs().removeContentTab(tabId);

                        }, submitButton.getOnError() != null ? submitButton.getOnError() : onFailed->{
                            getNetworksData().getContentTabs().removeContentTab(tabId);
                        });
                    }
                   
                     
                }else{
                    if(submitButton.getOnError() != null){
                        Utils.returnException(NoteConstants.ERROR_INVALID, getExecService(), submitButton.getOnError());
                    }
                }
            });

            getNetworksData().getContentTabs().addContentTab(addTab);
        }
    }


}
