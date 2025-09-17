package io.netnotes.engine;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.netnotes.engine.NoteFile.NoteFileInterface;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesArray;
import io.netnotes.engine.noteBytes.NoteBytesImage;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesPair;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteBytesReader;
import io.netnotes.engine.noteBytes.NoteBytesWriter;
import io.netnotes.engine.noteBytes.NoteStringArray;
import io.netnotes.engine.noteBytes.NoteStringArrayReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.ByteDecoding.NoteBytesMetaData;
import io.netnotes.engine.noteBytes.NoteLong;
import io.netnotes.engine.utils.Utils;
import javafx.beans.binding.Binding;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.event.ActionEvent;

public class NetworksData {

    public final static long DEFAULT_CYCLE_PERIOD = 7;
    public final static String NETWORK_ID = "NetworksData";
    public final static String NO_NETWORK_ID = "NO_NETWORK";
    public final static long EXECUTION_TIME = 500;
    private final static NoteBytesReadOnly NETWORKS_DATA_LOCATIONS = new NoteBytesReadOnly("locations");
    private final static NoteBytesReadOnly NETWORKS_DATA_SETTINGS = new NoteBytesReadOnly("settings");

    private String m_logging = NoteMessaging.Logging.FULL;

    private Runnable m_onClosing = null;
    private final NoteStringArray noteFilePath = new NoteStringArray(".", "data", "init", "networksData");

    private final AtomicBoolean m_intialized = new AtomicBoolean(false);
    private NoteBytes m_IdentityPrivateKey;
    private NoteBytes m_IdentityPublicKey;

    private NoteFile m_noteFile = null;

    private final HashMap<NoteBytes, NodeSecurity> m_networkLocations = new HashMap<>();

    private final AppInterface m_appInterface;

    public NetworksData(AppInterface appInteface, NoteBytesObject data) {
        m_appInterface = appInteface;
        initData(data);
    }

    private AppInterface getAppInterface(){
        return m_appInterface;
    }

    public void addAppResource(String resource) throws IOException{
        getAppInterface().addAppResource(resource);
    }

    public void removeAppResource(String resource) throws IOException{
        getAppInterface().removeAppResource(resource);
    }
    

    public ScheduledExecutorService getSchedualedExecService(){
        return getAppData().getSchedualedExecService();
    }

    public boolean isNodeSupported(NoteBytes nodeId){
        if(nodeId != null){
            NodeInformation[] supportedNodes = getAppInterface().getSupportedNodes();

            for(int i =0; i < supportedNodes.length ; i++){
                if(supportedNodes[i].getNodeId().equals(nodeId)){
                    return true;
                }
            }
        }
        return false;
    }



    public ExecutorService getExecService(){
        return getAppData().getExecService();
    }

    
    public Image getCharacterImage(String characterString){
        return null;
    }


    private AppData getAppData() {
        return getAppInterface().getAppData();
    }

    AtomicBoolean m_isNoteBytesLoaded = new AtomicBoolean(false);
   


    public void initData(NoteBytesObject data) {

        if (data != null) { 
            NoteBytesPair locationsPair = data.get("locations");
            

        }else{

            NoteBytes[] nodeIds = getAppInterface().getDefaultNodeIds();
            if(nodeIds != null){
                for(NoteBytes nodeId : nodeIds){
                   // installApp(nodeId, false);
                }
            }
          
            save();
            return;
        }
      
    }


    private void initalizeLocations(NoteBytesReader reader, NoteBytesWriter writer, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) throws IOException {
        // stream each location entry out-of-memory, recursively processing the nested "node" NoteInformation
        NoteBytesMetaData locationsMetaData = reader.nextMetaData();
        final int locationsLength = locationsMetaData.getLength();

        if(locationsLength > 0){
            recursivelyReadLocations(0, reader, writer, onSucceeded, onFailed);
        }else{
            Utils.returnObject(0, getExecService(), onSucceeded);
        }
    }
    
    private void recursivelyReadLocations(int bytesRead, NoteBytesReader reader, NoteBytesWriter writer,EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        Task<Object> task = new Task<>() {
            @Override
            public Object call() throws IOException {
                try {
                    
                } catch (Exception e) {
                    throw new IOException("Failed to initialize locations", e);
                }

                return null;
            }
        };

  
        task.setOnFailed(onFailed);
        getExecService().submit(task);
    }

    private void initalizeSettings(NoteBytesReader reader, NoteBytesWriter writer, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) throws IOException {
        
    }

    private Future<?> sendNote(String toId, PipedOutputStream sendData, PipedOutputStream responseStream, EventHandler<WorkerStateEvent> onFailed){
  
        if (toId == null && onFailed != null) {
             return Utils.returnException("Invalid network id: " + toId, getExecService(), onFailed);   
        }else if(toId != null) {
            NodeSecurity location = m_networkLocations.get(toId);

            if(location != null){
                Node network = location.getNode();
                PipedOutputStream receiveStream = new PipedOutputStream();
            
                Future<?> future = 
                
                /*NoteMessaging.writeEncryptedStreamToStream(
                    m_IdentityPublicKey, 
                    m_IdentityPrivateKey, 
                    location.requiresSealed(), 
                    location.getPublicKey(), 
                    sendData, receiveStream, getExecService(), 
                    (failed->{
                        Utils.writeLogMsg("networksData.sendNote.writeStreamToStream", failed);
                }));*/
                
                network.receiveNote(receiveStream, responseStream);
                
                return future;
            }else{
                return null;
            }
        }
        return null;
    }


   

    private Future<?> sendNote(NoteStringArray idList, PipedOutputStream outputStream, PipedOutputStream responseStream) {
        if (idList != null) {
            String[] networkIds = idList.getAsStringArray();
            final int totalNetworks = networkIds.length;
            final AtomicInteger responseCounter = new AtomicInteger(0);
            
            Task<Object> task = new Task<Object>() {
                @Override
                public Object call() throws IOException {
                    try {
                        for (int i = 0; i < networkIds.length; i++) {
                            String nodeId = networkIds[i];
                            PipedOutputStream individualOutput = new PipedOutputStream();
                            PipedOutputStream individualResponse = new PipedOutputStream();
                            
                            if (m_networkLocations.get(nodeId) != null) {
                                sendNote(nodeId, individualOutput, individualResponse, sendNoteFailed->{
                                    
                                });
                                
                                Utils.writeStreamToStream(individualOutput, outputStream, getExecService(), (onFailed)->{
                                    handleResponseIncrement(responseCounter, totalNetworks, responseStream);
                                });
                            
                                responseCollector(individualResponse, (onComplete)->{
                                    Task<Object> task = new Task<Object>() {
                                        @Override
                                        public Object call() throws IOException {
                                            Object response = onComplete.getSource().getValue();
                                            
                                            try {
                                                NoteBytesWriter responseWriter = new NoteBytesWriter(responseStream);
                                                if(response != null && response instanceof byte[]){
                                                    byte[] responseBytes = (byte[])response;
                                                    responseWriter.write(new NoteBytesPair(nodeId, responseBytes));
                                                }else{
                                                    NoteBytesObject nbo = new NoteBytesObject(new NoteBytesPair[]{
                                                        new NoteBytesPair(NoteMessaging.General.ERROR, NoteMessaging.Error.INVALID)
                                                    });
                                                    responseWriter.write(new NoteBytesPair(nodeId, nbo));
                                                }
                                            } finally {
                                                handleResponseIncrement(responseCounter, totalNetworks, responseStream);
                                            }
                                        
                                            return null;
                                        }
                                    };
                                    
                                    task.setOnFailed((onFailed)->{
                                        Utils.writeLogMsg("NetworksData.sendNote.collector" + nodeId, onFailed);
                                        handleResponseIncrement(responseCounter, totalNetworks, responseStream);
                                    });
                                    
                                    getExecService().submit(task);
                                }, onFailed->{
                                    handleResponseIncrement(responseCounter, totalNetworks, responseStream);
                                });
                            } else {
                                // Network location not found - still increment counter

                                Task<Object> task = new Task<Object>() {
                                        @Override
                                        public Object call() throws IOException {
                                            
                                            
                                            try {
                                                NoteBytesWriter responseWriter = new NoteBytesWriter(responseStream);
                                                
                                                NoteBytesObject nbo = new NoteBytesObject(new NoteBytesPair[]{
                                                    new NoteBytesPair(NoteMessaging.General.ERROR, NoteMessaging.Error.INVALID)
                                                });
                                                responseWriter.write(new NoteBytesPair(nodeId, nbo));
                                                
                                            } finally {
                                                handleResponseIncrement(responseCounter, totalNetworks, responseStream);
                                            }
                                        
                                            return null;
                                        }
                                    };
                                    
                                    task.setOnFailed((onFailed)->{
                                        Utils.writeLogMsg("NetworksData.sendNote.collector" + nodeId, onFailed);
                                        handleResponseIncrement(responseCounter, totalNetworks, responseStream);
                                    });
                                    
                                    getExecService().submit(task);
                               
                            }
                        }
                        return true;
                    } catch (Exception e) {
                        Utils.writeLogMsg("NetworksData.sendNote", e);
                        try {
                            responseStream.close();
                        } catch (IOException closeEx) {
                            Utils.writeLogMsg("NetworksData.sendNote.close", closeEx);
                        }
                        throw e;
                    }
                }
            };
            
            return getExecService().submit(task);
        }
        return null;
    }
    private void handleResponseIncrement(AtomicInteger counter, int total, PipedOutputStream responseStream) {
        if(counter.incrementAndGet() >= total) {
            try {
                responseStream.close();
            } catch (IOException e) {
                Utils.writeLogMsg("NetworksData.handleResponseIncrement.close", e);
            }
        }
    }
   
    protected Future<?> responseCollector(PipedOutputStream individualResponse, EventHandler<WorkerStateEvent> onComplete, EventHandler<WorkerStateEvent> onFailed){
        
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException {
                byte[] result = null;
                try(
                    ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
                    PipedInputStream responseInput = new PipedInputStream(individualResponse);
                ){
                    byte[] buffer = new byte[Utils.DEFAULT_BUFFER_SIZE];
                    int length = 0;
                    while((length = responseInput.read(buffer)) != -1){
                        byteArrayOutput.write(buffer, 0, length);
                    }
                    
                    result = byteArrayOutput.toByteArray();
                }

                return result;
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onComplete);

        return getExecService().submit(task);
    }



    protected NetworksDataInterface getNetworksDataInteface(NoteBytes nodeId){
        return new NetworksDataInterface() {

            @Override
            public void sendNote(NoteBytes toId, PipedOutputStream outputStream, PipedOutputStream responseStream, EventHandler<WorkerStateEvent> onFailed) {
                 Future<?> future = NetworksData.this.sendNote(toId, outputStream, responseStream);
                 if(future == null && onFailed != null){
                     Utils.returnException("Invalid network id: " + toId, getExecService(), onFailed);
                 }
            }

            @Override
            public void sendNote(NoteStringArrayReadOnly toId, PipedOutputStream sendData, PipedOutputStream replyData, EventHandler<WorkerStateEvent> onFailed) {
                Future<?> future = NetworksData.this.sendNote(toId, sendData, replyData);
                if(future == null && onFailed != null){
                    Utils.returnException("Invalid network ids", getExecService(), onFailed); 
                }
            }

            @Override
            public ExecutorService getExecService() {
                return getAppData().getExecService();
            }

            @Override
            public CompletableFuture<NoteFile> getNoteFile(NoteStringArrayReadOnly path) {
                return getAppData().getNoteFileRegistry().getNoteFile(path);
            }

            
            
        };
    }


    private Node createNode(NoteBytes nodeId){
        if(getNode(nodeId) == null){

            Node app = getAppInterface().createNode(nodeId);
            if(app != null){
                app.init(getNetworksDataInteface(nodeId));
                return app;
            }
        }
        return null;
    }



   
    private boolean addNode(Node node, NoteBytes locationUUID, NoteBytes publicKey, boolean requireSealed, boolean isSave) {
        NoteBytes nodeId = node.getNodeId();
        
        if (nodeId == null || nodeId.isEmpty() || nodeId == null) return false;

        NoteBytes uuid = locationUUID != null ? locationUUID : NoteUUID.createLocalUUID128();

        NodeSecurity newLoc = new NodeSecurity(node, uuid, publicKey, requireSealed);
  
        if ( m_networkLocations.putIfAbsent(nodeId, newLoc) == null) {
            
            if(isSave){
                save();
                appUpdated();
  
                /*broadcastNoteToSubscribers(
                    getNoteDataObject(APPS, NoteMessaging.Event.LIST_ITEM_ADDED, nodeId),
                (onSent)->{

                },
                (onReplied)->{
                    
                }, onfailed->{
                    Utils.writeLogMsg("networksData.addapp.onfailed", onfailed);
                });*/
               
            }
            return true;
        }
        return false;
    }


  

    protected NodeInformation getLocationNetworkInformation(String nodeId){
        NodeSecurity location = m_networkLocations.get(nodeId);
        return location != null ? location.getNodeInformation() : null;
    }

 
    public void shutdown() {

        removeAllApps(false);

    }


    


    protected Node getNode(NoteBytes nodeId) {
        
        NodeSecurity location = m_networkLocations.get(nodeId);

        return location != null ? location.getNode() : null;
  
    }



    protected boolean installApp(String nodeId){
        return installApp(nodeId, true);
    }

    protected boolean installApp(String nodeId, boolean save) {
        if(getApp(nodeId) == null && isAppSupported(nodeId)){
           
            return addApp(createApp(nodeId), true);
           
        }
        return false;
    }


    private void addAllApps(boolean isSave) {
        NodeInformation[] supportedApps = getAppInterface().getSupportedApps();
        NoteStringArray addedArray = new NoteStringArray();

        for (NodeInformation networkInfo : supportedApps) {
            String nodeId = networkInfo.getNetworkId();
            if(installApp(nodeId, false)){

                addedArray.add(nodeId);
            }
        }
        if(isSave){
            save();
            appUpdated();
            
            broadcastNoteToSubscribers(
                getNoteDataObject(APPS, NoteMessaging.Event.LIST_ITEM_ADDED, addedArray),
                (onSent)->{
                    
                }, 
            (onSucceeded)->{
                //write complete
            }, onfailed->{
                Utils.writeLogMsg("networksData.removeNetwork.onfailed", onfailed);
            });
        }
    }

    protected void removeAllApps(boolean isSave) {
        Iterator<Map.Entry<String, NodeSecurity>> it =  m_networkLocations.entrySet().iterator();
        NoteStringArray removedArray = new NoteStringArray();

        while (it.hasNext()) {
            Map.Entry<String, NodeSecurity> entry = it.next(); 
            NodeSecurity location = entry.getValue();
            if(location.isApp()){
                String id = entry.getKey();
                if(removeApp(id, false)){
                    removedArray.add(id);
                }
            }
        }
        if(isSave){
            save();
            appUpdated();
       
            broadcastNoteToSubscribers(
                getNoteDataObject(APPS, NoteMessaging.Event.LIST_ITEM_REMOVED, removedArray),
                (onSent)->{
                    
                }, 
            (onSucceeded)->{
                //write complete
            }, onfailed->{
                Utils.writeLogMsg("networksData.removeNetwork.onfailed", onfailed);
            });
        }
    }


  
    protected boolean removeApp(String nodeId) {
        return removeApp(nodeId, true);
    }


    protected boolean removeApp(String nodeId, boolean isSave) {

        NodeSecurity location = m_networkLocations.get(nodeId);

        if(location != null && location.isApp()){
            Node network = location.getNetwork();
            network.shutdown();
            m_contentTabs.removeByParentId(nodeId);
            
            m_networkLocations.remove(nodeId);
      
           
            if(isSave){
                save();
                appUpdated();

                broadcastNoteToSubscribers(
                    getNoteDataObject(NETWORKS, NoteMessaging.Event.LIST_ITEM_REMOVED, nodeId), 
                    (onSent)->{
                    
                    },
                    (onSucceeded)->{
                        //write complete
                    }, onfailed->{
                        Utils.writeLogMsg("networksData.removeNetwork.onfailed", onfailed);
                });
            }
            return true;
        }

        return false;
    }



    protected void save() {
     
    }


   
  
    public List<NodeInformation> getAppsContainsAllKeyWords(String... keyWords){
        return getLocationContainsAllKeyWords(APPS, keyWords);
    }

    public List<NodeInformation> getNetworksContainsAllKeyWords(String... keyWords){
        return getLocationContainsAllKeyWords(NETWORKS, keyWords);
    }

    public List<NodeInformation> getLocationContainsAllKeyWords(String type, String... keyWords){
        ArrayList<NodeInformation> list = new ArrayList<>();
        
        for (Map.Entry<String, NodeSecurity> entry : m_networkLocations.entrySet()) {
            NodeSecurity location = entry.getValue();
            Node network = location.getNetwork();
            if(location.getLocationType() == type && containsAllKeyWords(network, keyWords)){
                list.add(network.getNodeInformation());
            }
        }

        return list;
    }


    public static boolean containsAllKeyWords(Node item, String... keywords){
       
            
        Node app = (Node) item;
        String[] appKeyWords = app.getKeyWords();

        SimpleBooleanProperty found = new SimpleBooleanProperty(false);
        
        int appKeyWordsLength = appKeyWords.length;
        int keyWordsLength = keywords.length;

        for(int i = 0; i < keyWordsLength; i++){
            String keyWord = keywords[i];
            found.set(false);
            for(int j = 0; j < appKeyWordsLength ; j++){
                if(appKeyWords[j].equals(keyWord)){
                    found.set(true);
                    break;
                }
            }
            if(found.get() != true){
                return false;
            }
    
        }

        return true;
        
    }






    public File getAssetsDir() throws IOException{
        File assetsDir = new File(getAppData().getDataDir().getAbsolutePath() + "/assets");
        if(!assetsDir.isDirectory()){
          
            Files.createDirectory(assetsDir.toPath());
          
        }
        return assetsDir;
    }

   
   





  
     
}
