package io.netnotes.engine.adapters;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.Future;

import com.google.gson.JsonObject;

import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.TabInterface;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class Adapter  {

    private int m_connectionStatus = NoteConstants.STOPPED;
    private String m_networkId;
    private NetworksData m_networksData;
    private SimpleObjectProperty<LocalDateTime> m_lastUpdated = new SimpleObjectProperty<LocalDateTime>(LocalDateTime.now());
    private ChangeListener<LocalDateTime> m_changeListener = null;
    private SimpleObjectProperty<LocalDateTime> m_shutdownNow = new SimpleObjectProperty<>(null);
    public final static long EXECUTION_TIME = 500;


    private ArrayList<AdapterMsgInterface> m_msgListeners = new ArrayList<>();

    private String m_name;
    private Image m_icon;
    private Button m_appBtn;

    public Adapter(Image icon, String name, String id, NetworksData networksData) {
        m_icon = icon;
        m_name = name;
        m_networkId = id;
        m_networksData = networksData;
    }

 

    public Future<?> sendNote(String adapterId, String note, EventHandler<WorkerStateEvent> onReply, EventHandler<WorkerStateEvent> onFailed) {

        return null;
    }

    public Button getButton(double size){
        if(m_appBtn != null){
            return m_appBtn;
        }else{
            Tooltip tooltip = new Tooltip(getName());
            tooltip.setShowDelay(javafx.util.Duration.millis(100));
            ImageView imgView = new ImageView(getAppIcon());
            imgView.setPreserveRatio(true);
            imgView.setFitWidth(size);

            m_appBtn = new Button();
            m_appBtn.setGraphic(imgView);
            m_appBtn.setId("menuTabBtn");
            m_appBtn.setTooltip(tooltip);
            return m_appBtn; 
        }
    }

    
    public void addMsgListener(AdapterMsgInterface item) {
        if (item != null && !m_msgListeners.contains(item)) {
            if(m_connectionStatus != NoteConstants.STARTED){
                start();
            }
            m_msgListeners.add(item);
        }
    }



    private String m_description = null;

    public void setDescpription(String value){
        m_description = value;
    }

    public String getDescription(){
        return m_description;
    }


    protected void setName(String name){
        m_name = name;
        
    }

    public Image getAppIcon(){
        return m_icon;
    }


    public TabInterface getTab(Stage appStage, SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, Button networkBtn){
        return null;
    }

    public boolean removeMsgListener(AdapterMsgInterface item){
        if(item != null){

            boolean removed = m_msgListeners.remove(item);
            
            if(m_msgListeners.size() == 0){
                stop();
            }
            
            return removed;
        }

        return false;
    }

    public Pane getPane(){
        return null;
    }

    public void setConnectionStatus(int status){
        m_connectionStatus = status;
    }

    public int getConnectionStatus(){
        return m_connectionStatus;
    }

    protected void stop(){
        
        setConnectionStatus(NoteConstants.STOPPED);
        
    }
 
    public ArrayList<AdapterMsgInterface> msgListeners(){
        return m_msgListeners;
    }

    protected void start(){
        setConnectionStatus(NoteConstants.STARTED);
    }



    protected void broadcastMessage(long timeStamp, String msg){
        for(int i = 0; i < m_msgListeners.size() ; i++){
            AdapterMsgInterface msgInterface = m_msgListeners.get(i);
            msgInterface.msgReceived(msg, timeStamp, null, null, null);
        }
    }



    protected AdapterMsgInterface getListener(String id) {
        for (int i = 0; i < m_msgListeners.size(); i++) {
            AdapterMsgInterface listener = m_msgListeners.get(i);
            if (listener.getId().equals(id)) {
                return listener;
            }
        }
        return null;
    }



    public void setNetworkId(String id) {
        m_networkId = id;
    }

    public String getNetworkId() {
        return m_networkId;
    }



    public String getName(){
        return m_name;
    }

    public Image getIcon(){
        return m_icon;
    }

    public void setIcon(Image icon){
        m_icon = icon;
    }

    public JsonObject getJsonObject() {
        JsonObject networkObj = new JsonObject();
        networkObj.addProperty("name", getName());
        networkObj.addProperty("networkId", getNetworkId());
        return networkObj;

    }

    public NetworksData getNetworksData() {
        return m_networksData;
    }
    /* 
    public NoteInterface getTunnelNoteInterface(String networkId) {

        for (NoteInterface noteInterface : m_tunnelInterfaceList) {
            if (noteInterface.getNetworkId().equals(networkId)) {
                return noteInterface;
            }
        }
        return null;
    }

    public ArrayList<NoteInterface> getTunnelNoteInterfaces() {
        return m_tunnelInterfaceList;
    }

    public void addTunnelNoteInterface(NoteInterface noteInterface) {
        if (getTunnelNoteInterface(noteInterface.getNetworkId()) == null) {
            m_tunnelInterfaceList.add(noteInterface);
        }
    }

    public void removeTunnelNoteInterface(String id) {
        for (int i = 0; i < m_tunnelInterfaceList.size(); i++) {
            NoteInterface tunnel = m_tunnelInterfaceList.get(i);

            if (tunnel.getNetworkId().equals(id)) {
                m_tunnelInterfaceList.remove(tunnel);
                break;
            }

        }
    }*/

    public SimpleObjectProperty<LocalDateTime> getLastUpdated() {
        return m_lastUpdated;
    }

    public void addUpdateListener(ChangeListener<LocalDateTime> changeListener) {
        m_changeListener = changeListener;
        if (m_changeListener != null) {
            m_lastUpdated.addListener(m_changeListener);

        }
        // m_lastUpdated.addListener();
    }

    public void removeUpdateListener() {
        if (m_changeListener != null) {
            m_lastUpdated.removeListener(m_changeListener);
            m_changeListener = null;
        }
    }

    public void shutdown() {
        shutdownNowProperty().set(LocalDateTime.now());
        
        removeUpdateListener();
        removeAllMsgListeners();
    }

    public void removeAllMsgListeners(){
        while(m_msgListeners.size() > 0){
            removeMsgListener(m_msgListeners.get(0));
        }
    }

    private SimpleObjectProperty<JsonObject> m_cmdProperty = new SimpleObjectProperty<JsonObject>(null);
    private ChangeListener<JsonObject> m_cmdListener;

    public SimpleObjectProperty<JsonObject> cmdProperty() {
        return m_cmdProperty;
    }

    public void addCmdListener(ChangeListener<JsonObject> cmdListener) {
        m_cmdListener = cmdListener;
        if (m_cmdListener != null) {
            m_cmdProperty.addListener(m_cmdListener);
        }
        // m_lastUpdated.addListener();
    }

    public void removeCmdListener() {
        if (m_cmdListener != null) {
            m_cmdProperty.removeListener(m_cmdListener);
            m_cmdListener = null;
        }
    }

    private ChangeListener<LocalDateTime> m_shutdownListener;

    public SimpleObjectProperty<LocalDateTime> shutdownNowProperty() {
        return m_shutdownNow;
    }

    public void addShutdownListener(ChangeListener<LocalDateTime> shutdownListener) {
        m_shutdownListener = shutdownListener;
        if (m_shutdownListener != null) {

            m_shutdownNow.addListener(shutdownListener);
        }
        // m_lastUpdated.addListener();
    }

    public void removeShutdownListener() {
        if (m_shutdownListener != null) {
            m_shutdownNow.removeListener(m_shutdownListener);
            m_shutdownListener = null;
        }
    }



}
