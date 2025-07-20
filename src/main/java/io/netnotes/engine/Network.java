package io.netnotes.engine;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.gson.JsonObject;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Task;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class Network {

    private String m_connectionStatus = NoteConstants.STOPPED;
    private String m_networkId;
    private String m_website = "";
    private String m_description = null;

    private SimpleObjectProperty<LocalDateTime> m_shutdownNow = new SimpleObjectProperty<>(null);
    public final static long EXECUTION_TIME = 500;

    public final static String DEFAULT_IMAGE_URL = "/assets/globe-outline-white-30.png";

    private double m_stagePrevWidth = Stages.DEFAULT_STAGE_WIDTH;
    private double m_stagePrevHeight = Stages.DEFAULT_STAGE_HEIGHT;
    private double m_stageWidth = Stages.DEFAULT_STAGE_WIDTH;
    private double m_stageHeight = Stages.DEFAULT_STAGE_HEIGHT;

    private String[] m_keyWords = null;

    private boolean m_stageMaximized = false;

    private ArrayList<String> m_subscribedIds = new ArrayList<>();

    private Image m_icon = null;
    private Button m_appBtn = null;
    private String m_name = null;
    private NetworksDataInterface m_networksData;

    public Network(Image image, String name, String networkId) {
        m_networkId = networkId;
        m_name = name;
        m_icon = image;
    }

    public void init( NetworksDataInterface networksData){
        if(m_networksData == null){
            m_networksData = networksData;
        }
    }


    protected NetworksDataInterface getNetworksData(){
        return m_networksData;
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

    
    protected void addSubscriber(String id) {
        if (id != null && !m_subscribedIds.contains(id)) {
            if(m_connectionStatus != NoteConstants.STARTED){
                start();
            }
            m_subscribedIds.add(id);
        }
    }

    protected void sendStreamToSubscribers(PipedOutputStream outputStream){
        for(String id : m_subscribedIds){
            getNetworksData().sendNote(id, outputStream);
        }
    }


    protected void sendNoteToSubscribers(NoteBytesObject noteBytesObject){
      
        PipedOutputStream outputStream = new PipedOutputStream();
        sendStreamToSubscribers(outputStream);
        
    
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException {
                try(
                    NoteBytesWriter writer = new NoteBytesWriter(outputStream);
                ){
                   return writer.write(noteBytesObject);
                }
            }
        };
        task.setOnFailed((failed)->{
            Utils.writeLogMsg("network.sendNoteToSubscribers", failed);
            try {
                outputStream.close();
            } catch (IOException e) {
                Utils.writeLogMsg("network.sendNoteToSubscribers.close", e.toString());
            }

        });
        getExecService().submit(task);
    }


    public Future<?> receiveNote(PipedOutputStream outputStream){
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException {

                try(
                    PipedInputStream inputStream = new PipedInputStream(outputStream, Utils.DEFAULT_BUFFER_SIZE)
                ){
  
                   
                    return null;
                }
            }
        };
        task.setOnSucceeded((onValue)->{
            receiveValue(onValue.getSource().getValue());
        });
        task.setOnFailed((failed->{

        }));

        return getExecService().submit(task);
    }

    protected void receiveValue(Object object){

    }

    public void setDescpription(String value){
        m_description = value;
    }

    public String getDescription(){
        return m_description;
    }


    public Image getAppIcon(){
        return m_icon;
    }


    public TabAppBox getTab(Stage appStage, SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, Button networkBtn){
        return null;
    }

    public boolean removeSubscriber(String id){
        if(id != null){
            boolean removed = m_subscribedIds.remove(id);
            
            if(m_subscribedIds.size() == 0){
                stop();
            }
            
            return removed;
        }

        return false;
    }

    public Pane getPane(){
        return null;
    }

    public void setConnectionStatus(String status){
        m_connectionStatus = status;
    }

    public String getConnectionStatus(){
        return m_connectionStatus;
    }


    protected void stop(){
        
        setConnectionStatus(NoteConstants.STOPPED);
        
    }
 
    protected ArrayList<String> subscriberList(){
        return m_subscribedIds;
    }

    protected void start(){
        setConnectionStatus(NoteConstants.STARTED);
    }

  

    public String[] getKeyWords() {
        return m_keyWords;
    }

    public void setKeyWords(String[] value){
        m_keyWords = value;
    }



    public String getNetworkId() {
        return m_networkId;
    }

    public double getStageWidth() {
        return m_stageWidth;
    }

    public void setStageWidth(double width) {
        m_stageWidth = width;

    }

    public void setStageHeight(double height) {
        m_stageHeight = height;
    }

    public double getStageHeight() {
        return m_stageHeight;
    }

    public boolean getStageMaximized() {
        return m_stageMaximized;
    }

    public void setStageMaximized(boolean value) {
        m_stageMaximized = value;
    }

    public double getStagePrevWidth() {
        return m_stagePrevWidth;
    }

    public void setStagePrevWidth(double width) {
        m_stagePrevWidth = width;

    }

    public void setStagePrevHeight(double height) {
        m_stagePrevHeight = height;
    }

    public double getStagePrevHeight() {
        return m_stagePrevHeight;
    }

  

    public String getName(){
        return m_name;
    }

    public Image getIcon(){
        return m_icon;
    }



    public String getWebsite(){
        return m_website;
    }

    public void setWebsite(String website){
        m_website = website;
    }

    public ExecutorService getExecService(){
        return getNetworksData().getExecService();
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


    public void shutdown() {
        shutdownNowProperty().set(LocalDateTime.now());
        
        removeAllSubscribers();
    }

    public void removeAllSubscribers(){
        while(m_subscribedIds.size() > 0){
            removeSubscriber(m_subscribedIds.get(0));
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

    public NetworkInformation getNetworkInformation(){
        return new NetworkInformation(getNetworkId(), getName(), getAppIcon(), getAppIcon(), getDescription());
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
