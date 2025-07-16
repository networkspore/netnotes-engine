package io.netnotes.engine.core;

import java.io.File;
import java.io.IOException;
import java.io.PipedOutputStream;
import java.nio.file.Files;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.reactfx.util.FxTimer;

import io.netnotes.engine.AppBox;
import io.netnotes.engine.AppConstants;
import io.netnotes.engine.AppInterface;
import io.netnotes.engine.BufferedButton;
import io.netnotes.engine.BufferedMenuButton;
import io.netnotes.engine.ContentTab;
import io.netnotes.engine.HostServicesInterface;
import io.netnotes.engine.Network;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NetworkLocation;
import io.netnotes.engine.NoteBytes;
import io.netnotes.engine.NoteBytesArray;
import io.netnotes.engine.NoteBytesObject;
import io.netnotes.engine.NoteBytesPair;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteFile;
import io.netnotes.engine.NoteListString;
import io.netnotes.engine.NoteMsgInterface;
import io.netnotes.engine.NoteUUID;
import io.netnotes.engine.ResizeHelper;
import io.netnotes.engine.Stages;
import io.netnotes.engine.TabAppBox;
import io.netnotes.engine.Utils;
import io.netnotes.engine.IconButton.IconStyle;
import io.netnotes.engine.NoteFile.NoteFileInterface;
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
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.event.ActionEvent;

public class NetworksData extends Network {

    public final static long DEFAULT_CYCLE_PERIOD = 7;
    public final static NoteBytes NETWORK_ID = new NoteBytes("NetworksData");

    public final static NoteBytes APPS = new NoteBytes("Apps");
    public final static NoteBytes NETWORKS = new NoteBytes("Networks");

    public final static NoteBytes NO_NETWORK_ID = new NoteBytes("NO_NETWORK");

    public final static int BTN_IMG_SIZE = 30;
    public final static long EXECUTION_TIME = 500;
    
    public final static NetworkInformation NO_NETWORK = new NetworkInformation(NO_NETWORK_ID, "(none)", new Image( AppConstants.NETWORK_ICON256), new Image( AppConstants.NETWORK_ICON), "No network selected" );
    
    public static final String UNKNOWN_LOCATION = "Unknown";

    private SimpleObjectProperty<NoteBytes> m_currentNetworkId = new SimpleObjectProperty<>(null);
    
    private ArrayList<NoteMsgInterface> m_msgListeners = new ArrayList<>();

   // private Tooltip m_networkToolTip = new Tooltip("Network");

    private HashMap<NoteBytes, NetworkLocation>  m_networkLocations = new HashMap<>();
 
    private SimpleStringProperty m_stageIconStyle = new SimpleStringProperty(IconStyle.ICON);

    private double m_stageWidth = 1024;
    private double m_stageHeight = 768;
    private double m_stagePrevWidth = 310;
    private double m_stagePrevHeight = 500;
    private boolean m_stageMaximized = false;
    private final AppData m_appData;

    private final Stage m_appStage;
    private StackPane m_staticContent;
    private VBox m_subMenuBox = new VBox();
    private HBox m_topBarBox;
    private HBox m_menuContentBox;
    private SimpleObjectProperty<TabAppBox> m_currentMenuTab = new SimpleObjectProperty<>();
    private BufferedMenuButton m_settingsBtn;
    private BufferedButton m_appsBtn;
    private Tooltip m_appsToolTip;
    private BufferedButton m_networkBtn;
    private AppsMenu m_appsMenu = null;
    /*private SettingsTab m_settingsTab = null;
    private NetworkTab m_networkTab = null;
    private AppsTab m_appsTab = null;*/

    private Label m_tabLabel = new Label("");
    public final static double DEFAULT_STATIC_WIDTH = 500;

    private SimpleDoubleProperty m_staticContentWidth;
    private SimpleDoubleProperty m_staticContentHeight;
    
    private SimpleDoubleProperty m_contentWidth;
    private SimpleDoubleProperty m_contentHeight;

    
   // private String m_localId;

    private SimpleStringProperty m_titleProperty;
    private SimpleDoubleProperty m_menuWidth;
    private VBox m_contentBox;
    private ContentTabs m_contentTabs;
    private Button m_maximizeBtn;
    private Button m_closeBtn;
    private HBox m_menuBox;
    private Scene m_scene;
    private HBox m_footerBox;
    private HBox m_titleBox;
    private HBox m_mainHBox;
    private HBox m_staticContentBox;
    private final HostServicesInterface m_hostServices;

    private NetworkControl m_networkControl = null;

    
    private static final NoteListString noteFilePath = new NoteListString(".", "data", "init", "networksData");
    private NoteFile m_noteFile = null;

    public NetworksData(AppData appData, Stage appStage, HostServicesInterface hostServices) {
        super(Stages.icon, "Netnotes", NETWORK_ID, null);
       
        m_hostServices = hostServices;
        m_appStage = appStage;
        m_appData = appData;

        getNoteFile(noteFilePath, onComplete->{
            Object obj = onComplete.getSource().getValue();
            NoteFile noteFile = obj != null && obj instanceof NoteFile ? (NoteFile) obj : null;
            
            if(noteFile != null){
                m_noteFile = noteFile;
                if(noteFile.isFile()){
                    openData(noteFile, true);
                }else{
                    initLayout();
                    openData(noteFile, false);
                }
                
            }
            
        }, onFailed->{
             Utils.writeLogMsg("System.init", onFailed);
        });
    }

    public NetworkControl getNetworkControl(){
        if(m_networkControl == null){
            m_networkControl = new NetworkControl();
        }
        return m_networkControl;
    }



    public ReadOnlyStringProperty titleProperty(){
        return m_titleProperty;
    }

    public Scene getScene(){
        return m_scene;
    }

    public Stage getStage(){
        return m_appStage;
    }

    public void initLayout(){




        m_staticContentWidth = new SimpleDoubleProperty(DEFAULT_STATIC_WIDTH);
        m_staticContentHeight = new SimpleDoubleProperty(200);
        
        m_contentWidth = new SimpleDoubleProperty();
        m_contentHeight = new SimpleDoubleProperty();

        m_closeBtn = new Button();
        m_maximizeBtn = new Button();

        m_menuBox = new HBox();
    
        m_contentBox = new VBox();

        m_staticContentBox = new HBox();

        m_mainHBox = new HBox(m_menuBox, m_staticContentBox, m_contentBox);
    
        m_staticContent = new StackPane();
        m_staticContent.setId("darkBox");

        m_footerBox = new HBox();
        m_footerBox.setAlignment(Pos.CENTER_LEFT);


        m_titleBox = Stages.createTopBar(Stages.icon, m_maximizeBtn, m_closeBtn, m_appStage);


        VBox layout = new VBox(m_titleBox, m_mainHBox, m_footerBox);


        m_menuWidth = new SimpleDoubleProperty(50);

        m_subMenuBox.setAlignment(Pos.TOP_LEFT);
  
        m_tabLabel.setPadding(new Insets(2,0,2,5));
        m_tabLabel.setFont(Stages.titleFont);

      //  m_networkToolTip.setShowDelay(new javafx.util.Duration(100));

        HBox vBar = new HBox();
        vBar.setAlignment(Pos.CENTER);
        vBar.setId("vGradient");
        vBar.setMinWidth(1);
        VBox.setVgrow(vBar, Priority.ALWAYS);
       
        Region menuVBar = new Region();
        VBox.setVgrow(menuVBar, Priority.ALWAYS);
        menuVBar.setPrefWidth(2);
        menuVBar.setMinWidth(2);
        menuVBar.setId("vGradient");
        
        HBox menuVBarBox = new HBox(menuVBar);
        VBox.setVgrow(menuVBarBox, Priority.ALWAYS);
        menuVBarBox.setMinWidth(2);
        menuVBarBox.setAlignment(Pos.CENTER_LEFT);

        Region logoGrowRegion = new Region();
        HBox.setHgrow(logoGrowRegion, Priority.ALWAYS);

        BufferedButton minimizeTabBtn = new BufferedButton(AppConstants.MINIMIZE_ICON, 16);
        minimizeTabBtn.setId("toolBtn");
        minimizeTabBtn.setOnAction(e->{
            TabAppBox tab = m_currentMenuTab.get();
            if(tab instanceof ManageAppsTab || tab instanceof ManageNetworksTab || tab instanceof SettingsTab){
               
                m_currentMenuTab.set(null);
            }else{
                tab.setIsMinimized(true);
                m_currentMenuTab.set(null);
            }
        });

        BufferedButton closeTabBtn = new BufferedButton(AppConstants.CLOSE_ICON, 16);
        closeTabBtn.setPadding(new Insets(0, 2, 0, 2));
        closeTabBtn.setId("closeBtn");
        closeTabBtn.setOnAction(e->{
            m_currentMenuTab.set(null);
        });

        m_topBarBox = new HBox(m_tabLabel, logoGrowRegion, minimizeTabBtn, closeTabBtn);
        HBox.setHgrow(m_topBarBox, Priority.ALWAYS);
        m_topBarBox.setAlignment(Pos.CENTER_LEFT);
        m_topBarBox.setId("networkTopBar");

        m_menuContentBox = new HBox();
        m_menuContentBox.setId("darkBox");
        
        Region hBar = new Region();
        hBar.setPrefWidth(400);
        hBar.setPrefHeight(2);
        hBar.setMinHeight(2);
        hBar.setId("hGradient");

        HBox gBox = new HBox(hBar);
        gBox.setAlignment(Pos.CENTER);
        gBox.setPadding(new Insets(0, 0, 10, 0));
        
        m_currentMenuTab.addListener((obs,oldval,newval)->{

            if(oldval != null){
                //TODO: whatdafuq
                /*if(!oldval.isMinimized().equals(NoteConstants.STATUS_MINIMIZED)){
                    oldval.setStatus(NoteConstants.STATUS_STOPPED);
                    //oldval.shutdown();
                   
                }*/
            }

            m_subMenuBox.getChildren().clear();
            
            m_menuContentBox.getChildren().clear();

            if(newval != null){
                m_tabLabel.setText(newval.getName());
       
                m_subMenuBox.getChildren().addAll(m_topBarBox, gBox, (Pane) newval);
                m_menuContentBox.getChildren().addAll(m_subMenuBox, vBar);
                if(!m_staticContent.getChildren().contains(m_menuContentBox)){
                    m_staticContent.getChildren().add( m_menuContentBox );
                }
         
               
            }else{
                m_staticContent.getChildren().clear();
            }
          

        }); 

 

        m_scene = new Scene(layout, getStageWidth(), getStageHeight());
        m_scene.setFill(null);
        m_scene.getStylesheets().add("/css/startWindow.css");

        m_titleProperty = new SimpleStringProperty("Netnotes");

        m_appStage.titleProperty().bind(m_titleProperty);
        m_appStage.setScene(m_scene);

        m_appStage.centerOnScreen();
        
        ResizeHelper.addResizeListener(m_appStage, 720, 480, Double.MAX_VALUE, Double.MAX_VALUE);

        

        m_appsMenu = new AppsMenu();
        m_contentTabs = new ContentTabs();

        m_menuBox.getChildren().addAll( m_appsMenu, menuVBarBox);
        
        m_contentBox.getChildren().add(m_contentTabs);

        m_staticContent.setMinWidth(DEFAULT_STATIC_WIDTH + Stages.VIEWPORT_WIDTH_OFFSET);
        m_staticContent.prefHeightProperty().bind(m_appStage.heightProperty().subtract(m_titleBox.heightProperty()).subtract(m_footerBox.heightProperty()));
        
        m_staticContentHeight.bind(m_scene.heightProperty().subtract(m_titleBox.heightProperty()).subtract(m_footerBox.heightProperty()).subtract(45));

        m_menuBox.prefHeightProperty().bind(m_scene.heightProperty().subtract(m_titleBox.heightProperty()).subtract(m_footerBox.heightProperty()).subtract(2));

        m_mainHBox.prefWidthProperty().bind(m_scene.widthProperty());
        m_mainHBox.prefHeightProperty().bind(m_scene.heightProperty().subtract(m_titleBox.heightProperty()).subtract(m_footerBox.heightProperty()).subtract(1));


        
        m_contentWidth.bind(m_scene.widthProperty().subtract(m_menuBox.widthProperty()).subtract(m_staticContentBox.widthProperty()).subtract(1));
        m_contentHeight.bind(m_scene.heightProperty().subtract(m_titleBox.heightProperty()).subtract(m_footerBox.heightProperty()).subtract(1));

        if(getStageMaximized()){
            m_appStage.setMaximized(true);
        }

        m_scene.widthProperty().addListener((obs, oldval, newVal) -> {
            setStageWidth(newVal.doubleValue());
      
            if (m_lastExecution != null && !(m_lastExecution.isDone())) {
                m_lastExecution.cancel(false);
            }

            m_lastExecution = getSchedualedExecService().schedule(()->save(), EXECUTION_TIME, TimeUnit.MILLISECONDS);
        });

        m_scene.heightProperty().addListener((obs, oldval, newVal) -> {
            setStageHeight(newVal.doubleValue());

            if (m_lastExecution != null && !(m_lastExecution.isDone())) {
                m_lastExecution.cancel(false);
            }

            m_lastExecution = getSchedualedExecService().schedule(()->save(), EXECUTION_TIME, TimeUnit.MILLISECONDS);
        });


        m_maximizeBtn.setOnAction(maxEvent -> {
            boolean maximized = m_appStage.isMaximized();
            setStageMaximized(!maximized);

            if (!maximized) {
                setStagePrevWidth(m_appStage.getWidth());
                setStagePrevHeight(m_appStage.getHeight());
                
            }
             
            m_appStage.setMaximized(!maximized);
            FxTimer.runLater(Duration.ofMillis(200), ()->save());
        });

        menuTabProperty().addListener((obs,oldval,newval)->{
            if(newval != null){
                if(!m_staticContentBox.getChildren().contains(m_staticContent)){
                    m_staticContentBox.getChildren().add(m_staticContent);
                }
            }else{
                if(m_staticContentBox.getChildren().contains(m_staticContent)){
                    m_staticContentBox.getChildren().remove(m_staticContent);
                }
            }
        });

      


        m_closeBtn.setOnAction(e ->onClosing());

        m_appStage.setOnCloseRequest(e -> onClosing());
       
    }

    public ReadOnlyDoubleProperty contentWidthProperty(){
        return m_contentWidth;
    }
    public ReadOnlyDoubleProperty contentHeightProperty(){
        return m_contentHeight;
    }

    private void onClosing(){
        shutdown();
        m_appStage.close();
        
        
    }

    public HostServicesInterface getHostServices(){
        return m_hostServices;
    }

    private AppInterface getAppInterface(){
        return m_appData.getAppInterface();
    }

    public void addAppResource(String resource) throws IOException{
        getAppInterface().addAppResource(resource);
    }

    public void removeAppResource(String resource) throws IOException{
        getAppInterface().removeAppResource(resource);
    }
    
    private ScheduledFuture<?> m_lastExecution = null;

    public ScheduledExecutorService getSchedualedExecService(){
        return m_appData.getSchedualedExecService();
    }

    public boolean isAppSupported(NoteBytes networkId){
        if(networkId != null){
            NetworkInformation [] supportedApps = getAppInterface().getSupportedApps();

            for(int i =0; i < supportedApps.length ; i++){
                if(supportedApps[i].getNetworkId().equals(networkId)){
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isNetworkSupported(NoteBytes networkId){
        if(networkId != null){
            NetworkInformation [] supportedNetworks = getAppInterface().getSupportedApps();

            for(int i =0; i < supportedNetworks.length ; i++){
                if(supportedNetworks[i].getNetworkId().equals(networkId)){
                    return true;
                }
            }
        }
        return false;
    }



    public ExecutorService getExecService(){
        return m_appData.getExecService();
    }

    
    public Image getCharacterImage(String characterString){
        return null;
    }



    private AppData getAppData() {
        return m_appData;
    }


    private void openData(NoteFile noteFile, boolean isInit) {

        if (isInit) { 
            noteFile.getFileBytes((onBytes)->{
                Object bytesObject = onBytes.getSource().getValue();
                byte[] bytes = bytesObject != null && bytesObject instanceof byte[] ? (byte[]) bytesObject : null;
                if(bytes != null){
                    NoteBytesObject networksTree = new NoteBytesObject(bytes);

                    NoteBytesPair locationsPair = networksTree != null ? networksTree.get("locations") : null;
                    NoteBytesObject locationsTree = locationsPair.getValueAsNoteBytesObject();

                    NoteBytesPair jsonNetArrayElement = locationsTree.get("networks") ;
                    NoteBytesPair appsArrayElement = locationsTree.get("apps");


                    NoteBytes[] noteBytesArray = jsonNetArrayElement != null  ? jsonNetArrayElement.getValueAsNoteBytesArray().getAsArray() : new NoteBytes[0];
                    
                    for (NoteBytes noteBytes : noteBytesArray) {
                        Network network = createNetwork(noteBytes);
                        if(network != null){
                            addNetwork(network, false);
                        }
                    }
                    
                    NoteBytesPair currentNetworkIdPair = networksTree.get("currentNetworkId");
                    NoteBytes currentNetworkId = currentNetworkIdPair != null && currentNetworkIdPair.getValue().byteLength() > 0 ? currentNetworkIdPair.getValue() : null; 
                
                    if(currentNetworkId != null && getNetwork(currentNetworkId) != null){
                        
                        m_currentNetworkId.set(currentNetworkId); 
                    }else{
                        m_currentNetworkId.set(null);
                    }
                    
                
            

                    NoteBytes[] appsArray = appsArrayElement != null  ? appsArrayElement.getValueAsNoteBytesArray().getAsArray() : new NoteBytes[0];
                    
                    for (NoteBytes noteBytes : appsArray) {

                        Network app = createApp(noteBytes);
                        if(app != null){
                            addApp(app, false);
                        }
                    }
                    NoteBytesPair stagePair = networksTree.get("stage");
                    
                    if (stagePair != null) {

                        NoteBytesObject stageTree = stagePair.getValueAsNoteBytesObject();

                        NoteBytesPair stagePrevXElement = stageTree.get("prevX");
                        NoteBytesPair stagePrevYElement = stageTree.get("prevY");
                        NoteBytesPair stageWidthElement = stageTree.get("width");
                        NoteBytesPair stageHeightElement = stageTree.get("height");
                        NoteBytesPair stagePrevWidthElement = stageTree.get("prevWidth");
                        NoteBytesPair stagePrevHeightElement = stageTree.get("prevHeight");
                        NoteBytesPair iconStyleElement = stageTree.get("iconStyle");
                        NoteBytesPair stageMaximizedElement = stageTree.get("maximized");

                        boolean maximized = stageMaximizedElement == null ? false : stageMaximizedElement.getValue().getAsBoolean();
                        String iconStyle = iconStyleElement != null ? iconStyleElement.getValue().getAsString() : IconStyle.ICON;
                        m_prevX = stagePrevXElement != null ? stagePrevXElement.getValue().getAsDouble() : -1;
                        m_prevY = stagePrevYElement != null ? stagePrevYElement.getValue().getAsDouble() : -1;

                        m_stageIconStyle.set(iconStyle);
                        setStagePrevWidth(Stages.DEFAULT_STAGE_WIDTH);
                        setStagePrevHeight(Stages.DEFAULT_STAGE_HEIGHT);

                        if (!maximized) {

                            setStageWidth(stageWidthElement.getValue().getAsDouble());
                            setStageHeight(stageHeightElement.getValue().getAsDouble());
                        } else {
                            double prevWidth = stagePrevWidthElement != null  ? stagePrevWidthElement.getValue().getAsDouble() : Stages.DEFAULT_STAGE_WIDTH;
                            double prevHeight = stagePrevHeightElement != null ? stagePrevHeightElement.getValue().getAsDouble() : Stages.DEFAULT_STAGE_HEIGHT;
                            setStageWidth(prevWidth);
                            setStageHeight(prevHeight);
                            setStagePrevWidth(prevWidth);
                            setStagePrevHeight(prevHeight);
                        }
                        setStageMaximized(maximized);
                    } 
                }
                initLayout();
            }, (onBytesFailed)->{
                initLayout();
            });

        }else{

            NoteBytes[] appIds = getAppInterface().getDefaultAppIds();
            if(appIds != null){
                for(NoteBytes appId : appIds){
                    installApp(appId, false);
                }
            }
            NoteBytes[] networkIds = getAppInterface().getDefaultNetworkIds();
            if(networkIds != null){
                for(NoteBytes networkId : networkIds){
                    installNetwork(networkId, false);
                }
                if(networkIds.length == 1){
                    m_currentNetworkId.set(networkIds[0]);
                }
            }


            save();
            return;
        }
      
    }




    private Network createApp(NoteBytes networkId){
        if(getApp(networkId) == null){

            Network app = getAppInterface().createApp(networkId);
          
            if(app != null){
                return app;
            }
        }
        return null;
    }

    private Network createNetwork(NoteBytes networkId){
        if(getNetwork(networkId) == null){
            Network network = getAppInterface().createNetwork(networkId);
            if(network != null){
                return network;
            }
        }
        return null;
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

    public SimpleStringProperty iconStyleProperty() {
        return m_stageIconStyle;
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

    public NoteBytesObject getStageTree() {
        NoteBytesObject tree = new NoteBytesObject();
        tree.add("prevX", m_prevX);
        tree.add("prevY", m_prevY);
        tree.add("maximized", getStageMaximized());
        tree.add("width", getStageWidth());
        tree.add("height", getStageHeight());
        tree.add("prevWidth", getStagePrevWidth());
        tree.add("prevHeight", getStagePrevHeight());
        return tree;
    }




    public boolean removeMsgListener(NoteMsgInterface item){
        if(item != null){
            return m_msgListeners.remove(item);
        }

        return false;
    }

    @Override
    public void sendMessage(int code, long timeStamp, NoteBytes networkId, String msg){
        m_appsMenu.sendMessage(code, timeStamp, networkId, msg);
        

        for(int i = 0; i < m_msgListeners.size() ; i++){
            m_msgListeners.get(i).sendMessage(code, timeStamp, networkId, msg);
        }

        TabAppBox tabInterface = m_currentMenuTab.get();
        if( tabInterface != null && (tabInterface instanceof ManageAppsTab || tabInterface instanceof ManageNetworksTab)){
            tabInterface.sendMessage(code, timeStamp, networkId, msg);
        }
        
    }
    
    private boolean addApp(Network app, boolean isSave) {
        NoteBytes networkId = app.getNetworkId();
        if (getApp(networkId) == null) {
            
            NetworkLocation networkLocation = new NetworkLocation(app, NoteConstants.APPS);
            m_networkLocations.put( networkId, networkLocation);
            
            if(isSave){
                save();
                long timestamp = System.currentTimeMillis();

                sendMessage( NoteConstants.LIST_ITEM_ADDED, timestamp, APPS, "");

            }
            return true;
        }
        return false;
    }

    protected Map<NoteBytes, NetworkLocation> getNetworkLocations(){
        return m_networkLocations;
    }

    private boolean addNetwork(Network network, boolean isSave) {
        NoteBytes networkId = network.getNetworkId();
        if (getNetwork(networkId) == null) {
            NetworkLocation networkLocation = new NetworkLocation(network, NoteConstants.NETWORKS);
            m_networkLocations.put( networkId, networkLocation);
           
            if(isSave){
                long timestamp = System.currentTimeMillis();
                sendMessage( NoteConstants.LIST_ITEM_ADDED, timestamp, NETWORKS, "");
                save();
            }

            return true;
        }
        return false;
    }

    protected NetworkInformation getLocationNetworkInformation(NoteBytes networkId){
        NetworkLocation location = m_networkLocations.get(networkId);
        return location != null ? location.getNetworkInformation() : null;
    }

    protected boolean removeNetwork(NoteBytes networkId, boolean isSave){       
    
        if(networkId != null) {
            NetworkLocation location = m_networkLocations.remove(networkId);
          
            if (location != null) {
                Network network = location.getNetwork();
                network.shutdown();

                if(m_currentNetworkId.get() != null && m_currentNetworkId.get().equals(networkId)){
                    m_currentNetworkId.set(null);
                }
         
                if(m_currentMenuTab.get() != null && m_currentMenuTab.get().getAppId().equals(networkId)){
                    m_currentMenuTab.set(null);
                }

                m_contentTabs.removeByParentId(networkId);
                
                if(isSave){
                    long timestamp = System.currentTimeMillis();
                    sendMessage( NoteConstants.LIST_ITEM_REMOVED, timestamp, NETWORKS, "");
                    save();
                }

                return true;
            }
        }
     
        return false;
        
    }

    protected SimpleObjectProperty<NoteBytes> currentNetworkIdProperty(){
        return m_currentNetworkId;
    }

    @Override
    public void shutdown() {

        removeAllApps(false);

        removeAllNetworks(false);


    }


    


    private double m_prevX = -1;
    private double m_prevY = -1;
  

    public double getPrevX(){
        return m_prevX;
    }

    public double getPrevY(){
        return m_prevY;
    }

    public void setPrevX(double value){
        m_prevX = value;
    }

    public void setPrevY(double value){
        m_prevY = value;
    }


    protected Network getApp(NoteBytes networkId) {
        
        NetworkLocation location = m_networkLocations.get(networkId);

        return location != null ? location.getNetwork() : null;
  
    }


    protected void installNetwork(NoteBytes networkId){
        installNetwork(networkId, true);
    }

    protected void installNetwork(NoteBytes networkId, boolean isSave){
        if(isNetworkSupported(networkId)){           
            addNetwork(createNetwork(networkId), isSave);
        }
    }

    protected void installApp(NoteBytes networkId){
        installApp(networkId, true);
    }

    protected void installApp(NoteBytes networkId, boolean save) {
        if(getApp(networkId) == null && isAppSupported(networkId)){
           
            addApp(createApp(networkId), true);
           
        }
    }


    private void addAllApps(boolean isSave) {
        NetworkInformation[] supportedApps = getAppInterface().getSupportedApps();
        for (NetworkInformation networkInfo : supportedApps) {
            if (getApp(networkInfo.getNetworkId()) == null) {
                installApp(networkInfo.getNetworkId(), false);
            }
        }
        if(isSave){
            save();
        }
    }

    protected void removeAllApps(boolean isSave) {
        for (Map.Entry<NoteBytes, NetworkLocation> entry : m_networkLocations.entrySet()) {
            NetworkLocation location = entry.getValue();
            if(location.isApp()){
                removeApp(entry.getKey(), false);
            }
        }
        if(isSave){
            long timestamp = System.currentTimeMillis();
            sendMessage( NoteConstants.LIST_ITEM_REMOVED, timestamp, APPS, "");
            save();
        }
    }

    protected void removeAllNetworks(boolean isSave) {
        for (Map.Entry<NoteBytes, NetworkLocation> entry : m_networkLocations.entrySet()) {
            NetworkLocation location = entry.getValue();
            if(location.isNetwork()){
                removeNetwork(entry.getKey(), false);
            }
        }
        if(isSave){
            long timestamp = System.currentTimeMillis();
            sendMessage( NoteConstants.LIST_ITEM_REMOVED, timestamp, NETWORKS, "");
            save();
        }
    }


  
    protected boolean removeApp(NoteBytes networkId) {
        return removeApp(networkId, true);
    }


    protected boolean removeApp(NoteBytes networkId, boolean isSave) {

        NetworkLocation location = m_networkLocations.get(networkId);

        if(location != null && location.isApp()){
            Network network = location.getNetwork();
            network.shutdown();
            m_contentTabs.removeByParentId(networkId);
            
            m_networkLocations.remove(networkId);
      
           
            if(isSave){
                long timestamp = System.currentTimeMillis();
    
                sendMessage( NoteConstants.LIST_ITEM_REMOVED, timestamp, APPS, "");

                save();
            }
            return true;
        }

        return false;
    }

    /*public void broadcastNote(JsonObject note) {

        m_noteInterfaceList.forEach(noteInterface -> {

            noteInterface.sendNote(note, null, null);

        });

    }

    public void broadcastNoteToNetworkIds(JsonObject note, ArrayList<String> networkIds) {

        networkIds.forEach(id -> {
            m_noteInterfaceList.forEach(noteInterface -> {

                int index = id.indexOf(":");
                String networkId = index == -1 ? id : id.substring(0, index);
                if (noteInterface.getNetworkId().equals(networkId)) {

                    note.addProperty("uuid", id);
                    noteInterface.sendNote(note, null, null);
                }
            });
        });
    }*/

    protected boolean isAvailable(int type, NoteBytes networkId){
        NetworkLocation location = m_networkLocations.get(networkId);
        return location != null && location.getLocationType() == type;
    }

    protected Network getNetwork(NoteBytes networkId) {
        NetworkLocation location = getNetworkLocation(networkId);
        return location != null ? location.getNetwork() : null;
    }

    protected NetworkLocation getNetworkLocation(NoteBytes id){
        return m_networkLocations.get(id);
    }

    private AppBox getLocationTab(NoteBytes networkId){
           
        NetworkLocation networkLocation = getNetworkLocation(networkId);
      
        if(networkLocation != null){
            Network network = networkLocation.getNetwork();
            return network.getTab(m_appStage, m_staticContentHeight, m_staticContentWidth, m_networkBtn);
        }else{

        }
        return null;
    }

    

    /*public void sendNoteToNetworkId(JsonObject note, String networkId, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        m_noteInterfaceList.forEach(noteInterface -> {
            if (noteInterface.getNetworkId().equals(networkId)) {

                noteInterface.sendNote(note, onSucceeded, onFailed);
            }
        });
    }*/

    
    public NoteBytesObject getLocationsTree(){
        NoteBytesArray networksArray = new NoteBytesArray();
        NoteBytesArray appsArray = new NoteBytesArray();

        for (Map.Entry<NoteBytes, NetworkLocation> entry : m_networkLocations.entrySet()) {
            
            NetworkLocation networkLocation = entry.getValue();
            int type = networkLocation.getLocationType();
            Network network = networkLocation.getNetwork();

            switch(type){
                case NoteConstants.APPS:
                    appsArray.add(network.getNetworkId());
                break;
                case NoteConstants.NETWORKS:
                    networksArray.add(network.getNetworkId());
                break;
            }
        }

        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(APPS, appsArray),
            new NoteBytesPair(NETWORKS, networksArray)
        });
    }



    private NoteBytesObject getSaveTree(){
        
        NoteBytesObject tree = new NoteBytesObject();

        if(m_currentNetworkId.get() != null){
            tree.add("currentNetworkId", m_currentNetworkId.get());
        }
        tree.add("locations", getLocationsTree());
        tree.add("stage", getStageTree());
        return tree;
    }
    
    protected void save() {
        m_noteFile.saveFileBytes(getSaveTree().get(), onSucceeded->{}, onFailed->{
            Utils.writeLogMsg("NetworksData.save", onFailed);
        });
    }

    protected void openStatic(NoteBytes networkId){
        TabAppBox currentTab = m_currentMenuTab.get();

        NoteBytes currentTabId = currentTab != null ? currentTab.getAppId() : null;

        if(networkId == null || (currentTabId != null &&  currentTabId.equals(networkId))){
            return;
        }

        TabAppBox tab = getStaticTab(networkId);
    
        if(tab != null){
            if(currentTab != null){
                currentTab.setIsMinimized(true);
            }
            m_currentMenuTab.set(tab);
        }
   
    }

   
    protected void openNetwork(NoteBytes networkId){
        TabAppBox currentTab = m_currentMenuTab.get();
        
        NoteBytes currentTabId = currentTab != null ? currentTab.getAppId() : null;

        if(networkId == null || (currentTabId != null &&  currentTabId.equals(networkId))){
            if(currentTab != null){
                currentTab.setIsMinimized(true);
            }
            m_currentMenuTab.set(null);
            return;
        }
      
        Network network = getNetwork(networkId);
        
        if(network != null){
            if(currentTab != null){
                currentTab.setIsMinimized(true);
            }
         

            TabAppBox tab = network != null ? network.getTab(m_appStage, m_staticContentHeight, m_staticContentWidth, m_networkBtn) : null;
    
            
            m_currentMenuTab.set(tab);
            tab.setIsMinimized(false);
            if(m_currentNetworkId.get() == null || (m_currentNetworkId.get() != null && !m_currentNetworkId.get().equals(networkId))){
                m_currentNetworkId.set(networkId);
                save();
            }
        }
    }

    protected void openApp(NoteBytes networkId){
        TabAppBox currentTab = m_currentMenuTab.get();

        NoteBytes currentTabId = currentTab != null ? currentTab.getAppId() : null;

        if(networkId == null || (currentTabId != null &&  currentTabId.equals(networkId))){
            if(currentTab != null){
                currentTab.setIsMinimized(true);
            }
            m_currentMenuTab.set(null);
            return;
        }
      

        Network appNetwork = getApp(networkId);


        if(appNetwork != null){
            if(currentTab != null){
                currentTab.setIsMinimized(true);
            }

            TabAppBox tab = appNetwork.getTab(m_appStage, m_staticContentHeight, m_staticContentWidth, appNetwork.getButton(BTN_IMG_SIZE));
            
            m_currentMenuTab.set( tab);
            tab.setIsMinimized(false);
        }
    }
  
    public List<NetworkInformation> getAppsContainsAllKeyWords(String... keyWords){
        return getLocationContainsAllKeyWords(NoteConstants.APPS, keyWords);
    }

    public List<NetworkInformation> getNetworksContainsAllKeyWords(String... keyWords){
        return getLocationContainsAllKeyWords(NoteConstants.NETWORKS, keyWords);
    }

    public List<NetworkInformation> getLocationContainsAllKeyWords(int type, String... keyWords){
        ArrayList<NetworkInformation> list = new ArrayList<>();
        
        for (Map.Entry<NoteBytes, NetworkLocation> entry : m_networkLocations.entrySet()) {
            NetworkLocation location = entry.getValue();
            Network network = location.getNetwork();
            if(location.getLocationType() == type && containsAllKeyWords(network, keyWords)){
                list.add(network.getNetworkInformation());
            }
        }

        return list;
    }


    public static boolean containsAllKeyWords(Network item, String... keywords){
       
            
        Network app = (Network) item;
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

    protected boolean isNetworkInstalled(){
        for(Map.Entry<NoteBytes, NetworkLocation> entry : m_networkLocations.entrySet()){
            if(entry.getValue().isNetwork()){
                return true;
            }
        }

        return false;
    }

    protected boolean isAppInstalled(){
        for(Map.Entry<NoteBytes, NetworkLocation> entry : m_networkLocations.entrySet()){
            if(entry.getValue().isApp()){
                return true;
            }
        }

        return false;
    }


    private TabAppBox getStaticTab(NoteBytes networkId){

        if(m_currentMenuTab.get() != null && m_currentMenuTab.get().getAppId().equals(networkId)){
            return m_currentMenuTab.get();
        }else if(networkId.equals( ManageAppsTab.ID)){
            return new ManageAppsTab(getAppData(), m_appStage, m_staticContentHeight, m_staticContentWidth, m_settingsBtn, this);
        }else if(networkId.equals(SettingsTab.ID)){
            return new SettingsTab(getAppData(), m_appStage, m_staticContentHeight, m_staticContentWidth, m_settingsBtn, this);
        }else if(networkId.equals(ManageNetworksTab.ID)){
            return new ManageNetworksTab(getAppData(), m_appStage,m_staticContentHeight, m_staticContentWidth, m_settingsBtn, this);
        }
     
                
        return null;
    }


  
    protected SimpleObjectProperty<TabAppBox> menuTabProperty() {
        return m_currentMenuTab;
    };

    public void toggleMaximized(){
        m_maximizeBtn.fire();
    }

    protected boolean isStageMaximized(){
        return m_appStage.isMaximized();
    }
    
    public File getAssetsDir() throws IOException{
        File assetsDir = new File(getAppData().getDataDir().getAbsolutePath() + "/assets");
        if(!assetsDir.isDirectory()){
          
            Files.createDirectory(assetsDir.toPath());
          
        }
        return assetsDir;
    }

    public Future<?> getNoteFile(NoteListString path, EventHandler<WorkerStateEvent> onComplete, EventHandler<WorkerStateEvent> onFailed){

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException, InterruptedException{
                getAppData().getDataSemaphore().acquire();
                File idDataFile = getAppData().getIdDataFile(path);
                getAppData().getDataSemaphore().release();

                return new NoteFile(path, idDataFile, getNoteFileInterface());
            }
        };

        task.setOnFailed((onTaskFailed)->{
            if(!(onTaskFailed.getSource().getException() instanceof InterruptedException)){
                getAppData().getDataSemaphore().release();
            }
            Utils.returnException(onTaskFailed, getExecService(), onComplete);
        });

        task.setOnSucceeded(onComplete);

        return getExecService().submit(task);
        
    }
   

    private NoteFileInterface getNoteFileInterface(){
        return new NoteFileInterface(){
            @Override
            public ExecutorService getExecService() {
                return getExecService();
            }
            @Override
            public Future<?> readEncryptedFile(NoteFile noteFile, PipedOutputStream pipedOutput, Runnable aquired, EventHandler<WorkerStateEvent> onFailed) {
                return getAppData().readEncryptedFile(noteFile.getFile(), noteFile.getSemaphore(), pipedOutput, aquired, onFailed);
            }
            @Override
            public Future<?> writeEncryptedFile(NoteFile noteFile, PipedOutputStream pipedOutputStream, EventHandler<WorkerStateEvent> onFailed) {
                return getAppData().writeEncryptedFile(noteFile.getFile(), noteFile.getTmpFile(), noteFile.getSemaphore(), pipedOutputStream, onFailed);
            }
            @Override
            public Future<?> saveEncryptedFile(NoteFile noteFile, PipedOutputStream pipedOutputStream, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
                return getAppData().saveEncryptedFile(noteFile.getFile(), noteFile.getSemaphore(), pipedOutputStream, onSucceeded, onFailed);
            }
            @Override
            public Future<?> saveEncryptedFile(NoteFile noteFile, byte[] bytes, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
                return getAppData().saveEncrypteFile(noteFile.getFile(), noteFile.getSemaphore(), bytes, onSucceeded, onFailed);
            }
        };
    }


    public class AppsMenu extends VBox {
        //public static final int PADDING = 10;
       // public static final String NAME = "Apps";
  
        private VBox m_listBox;
    
        public AppsMenu(){
            super();
            
            Tooltip settingsTooltip = new Tooltip("Settings");
            settingsTooltip.setShowDelay(new javafx.util.Duration(100));

            MenuItem settingsManageAppsItem = new MenuItem("Manage Apps…");
            settingsManageAppsItem.setOnAction(e->{
                openStatic(ManageAppsTab.ID);
            });
            MenuItem settingsManageNetworksItem = new MenuItem("Manage Networks…");
            settingsManageNetworksItem.setOnAction(e->{
                openStatic(ManageNetworksTab.ID);
            });
  
            MenuItem settingsAppItem = new MenuItem("Settings…");
            settingsAppItem.setOnAction(e->{
                openStatic(SettingsTab.ID);
            });

            SeparatorMenuItem seperatorItem = new SeparatorMenuItem();

            
            m_settingsBtn = new BufferedMenuButton(AppConstants.SETTINGS_ICON, BTN_IMG_SIZE);
            m_settingsBtn.setTooltip(settingsTooltip);
            m_settingsBtn.setId("menuTabBtn");

            m_settingsBtn.getItems().addAll(settingsManageAppsItem, settingsManageNetworksItem,seperatorItem, settingsAppItem);
        //  m_appTabsBox = new VBox();
        // m_menuContentBox = new VBox(m_appTabsBox);

            m_networkBtn =new BufferedButton(NO_NETWORK.getSmallIcon(), BTN_IMG_SIZE);
            m_networkBtn.disablePressedEffects();
            m_networkBtn.setId("menuTabBtn");

            m_listBox = new VBox();
            HBox.setHgrow(m_listBox, Priority.ALWAYS);
            m_listBox.setPadding(new Insets(0,0,2,0));
            m_listBox.setAlignment(Pos.TOP_CENTER);
          

            
                
            
            ContextMenu networkContextMenu = new ContextMenu();



            BufferedButton networkMenuBtn = new BufferedButton(AppConstants.CARET_DOWN_ICON, 10);
            networkMenuBtn.setId("iconBtnDark");
        

            HBox networkMenuBtnBox = new HBox(networkMenuBtn);
            networkMenuBtnBox.setId("hand");
            VBox.setVgrow(networkMenuBtnBox, Priority.ALWAYS);
            HBox.setHgrow(networkMenuBtnBox, Priority.ALWAYS);
            networkMenuBtnBox.setAlignment(Pos.TOP_RIGHT);
            networkMenuBtnBox.setOnMouseClicked(e->m_networkBtn.fire());


            HBox socketBox = new HBox();
            socketBox.setId("socketBox");
            socketBox.setMaxHeight(27);
            socketBox.setMaxWidth(27);
            socketBox.setMouseTransparent(true);
       

            HBox socketPaddingBox = new HBox(socketBox);
            socketPaddingBox.setMouseTransparent(true);
            socketPaddingBox.setPadding(new Insets(0,0,0,5));

            StackPane currentNetworkBox = new StackPane(m_networkBtn, socketPaddingBox, networkMenuBtnBox);
            currentNetworkBox.setMaxWidth(57);
            currentNetworkBox.setMaxHeight(57);
            currentNetworkBox.setMinWidth(57);
            currentNetworkBox.setMinHeight(57);
            currentNetworkBox.setAlignment(Pos.CENTER);

            

            Runnable showNetworkMenu = () ->{
    
                networkContextMenu.getItems().clear();
                for (Map.Entry<NoteBytes, NetworkLocation> entry : m_networkLocations.entrySet()) {
                    NetworkLocation location = entry.getValue();

                    Network network = location.isApp() ? location.getNetwork() : null;

                    if(network != null){
                        ImageView menuItemImg = new ImageView();
                        menuItemImg.setPreserveRatio(true);
                        menuItemImg.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);
                        menuItemImg.setImage(network.getAppIcon());

                        MenuItem menuItem = new MenuItem(network.getName(), menuItemImg);
                        menuItem.setOnAction(e->{
                        
                            openNetwork(network.getNetworkId());
                        });
                        networkContextMenu.getItems().add(menuItem);
                    }
                
                }

                MenuItem manageMenuItem = new MenuItem("Manage networks...");
                manageMenuItem.setOnAction(e->{
                    openStatic(ManageNetworksTab.ID);
                });

                SeparatorMenuItem separatorMenuItem = new SeparatorMenuItem();

                networkContextMenu.getItems().addAll(separatorMenuItem, manageMenuItem);

                Point2D p = networkMenuBtn.localToScene(0.0, 0.0);

                networkContextMenu.show(networkMenuBtn,
                        p.getX() + networkMenuBtn.getScene().getX() + networkMenuBtn.getScene().getWindow().getX() + networkMenuBtn.getLayoutBounds().getWidth(),
                        (p.getY() + networkMenuBtn.getScene().getY() + networkMenuBtn.getScene().getWindow().getY()));
            };

            
            networkMenuBtn.setOnAction(e->showNetworkMenu.run());


            Tooltip currentNetworkTooltip = new Tooltip();
            currentNetworkTooltip.setShowDelay(javafx.util.Duration.millis(150));

            m_networkBtn.setTooltip(currentNetworkTooltip);        


            m_networkBtn.setOnAction(e->{
                Network currentNetwork = getNetwork(m_currentNetworkId.get());

                if(currentNetwork != null){
                    openNetwork(currentNetwork.getNetworkId());
                }else{
                    openStatic(ManageNetworksTab.ID);
                }

            });

            Runnable updateCurrentNetwork = ()->{
                Network currentNetwork = getNetwork(m_currentNetworkId.get());
        

                if(currentNetwork != null){
                    m_networkBtn.setImage(currentNetwork.getAppIcon());
                    currentNetworkTooltip.setText(currentNetwork.getName());
                }else{
                    m_networkBtn.setImage( NO_NETWORK.getSmallIcon());
                    currentNetworkTooltip.setText("Select network");
                }
            };

            updateCurrentNetwork.run();

            m_currentNetworkId.addListener((obs,oldval,newval)->updateCurrentNetwork.run());



            
            HBox listBoxPadding = new HBox(m_listBox);
            listBoxPadding.minHeightProperty().bind(m_appStage.getScene().heightProperty().subtract(50).subtract(m_settingsBtn.heightProperty()).subtract(currentNetworkBox.heightProperty()));
            //listBoxPadding.setId("appMenuBox");
            VBox scrollContentBox = new VBox(listBoxPadding);

            ScrollPane listScroll = new ScrollPane(scrollContentBox);
            listScroll.minViewportWidthProperty().bind(m_menuWidth);
            listScroll.prefViewportWidthProperty().bind(m_menuWidth);
            listScroll.prefViewportHeightProperty().bind(m_appStage.getScene().heightProperty().subtract(50).subtract(m_settingsBtn.heightProperty()).subtract(currentNetworkBox.heightProperty()));
           // listScroll.setId("appMenuBox");

            /*Region hBar = new Region();
            hBar.prefWidthProperty().bind(m_widthObject.subtract(40));
            hBar.setPrefHeight(2);
            hBar.setMinHeight(2);
            hBar.setId("hGradient");*/

            Button manageBtn = new Button("Manage Apps");
            manageBtn.prefWidthProperty().bind(m_staticContentWidth);
            manageBtn.setId("rowBtn");
            manageBtn.setOnAction(e->{
                openStatic(ManageAppsTab.ID);
            });

            HBox manageBtnBox = new HBox(manageBtn);
            HBox.setHgrow(manageBtnBox, Priority.ALWAYS);
            manageBtnBox.setAlignment(Pos.CENTER);
            manageBtnBox.setPadding(new Insets(5,0,0,0));


    
            getChildren().addAll(listScroll, m_settingsBtn, currentNetworkBox);
            setId("appMenuBox");

            updateAppsMenu();

            
        }
    
 
        public void sendMessage(int code, long timestamp, NoteBytes networkId, String msg){
            if(code == NoteConstants.LIST_ITEM_ADDED || code == NoteConstants.LIST_ITEM_REMOVED || code == NoteConstants.LIST_CHANGED || code == NoteConstants.LIST_UPDATED){
                if(networkId.equals(NetworksData.APPS)){
                    updateAppsMenu();
                }
            }
        }

        public void updateAppsMenu(){
       

            m_listBox.getChildren().clear();

            if (m_networkLocations.size() != 0) {
                if(m_appsBtn != null){
                    m_appsBtn = null;
                    m_appsToolTip = null;
                }
    
                for (Map.Entry<NoteBytes, NetworkLocation> entry : m_networkLocations.entrySet()) {
                    NetworkLocation location = entry.getValue();

                    Network app = location.isApp() ? location.getNetwork() : null;
                    if(app != null){
                        Button appBtn = app.getButton(BTN_IMG_SIZE);
                        appBtn.setOnAction(e->{
                        
                            openApp(app.getNetworkId());
                            
                        });
                        m_listBox.getChildren().add(appBtn);
                    }
                }
            
            }else{
                
                if(m_appsToolTip == null){
                    m_appsToolTip = new Tooltip("Manage Apps");
                    m_appsToolTip.setShowDelay(new javafx.util.Duration(100));

                    
                    m_appsBtn = new BufferedButton(AppConstants.APP_ICON, BTN_IMG_SIZE);
                    m_appsBtn.setId("menuTabBtn");
                    m_appsBtn.setTooltip(m_appsToolTip);
                    m_appsBtn.setOnAction(e -> {
                
                        openStatic(ManageAppsTab.ID);
                        
                    });
                }

                if(!m_listBox.getChildren().contains(m_appsBtn)){
                    m_listBox.getChildren().add(m_appsBtn);
                }
            }
        }
    
      
    }
    /*
                    double imageWidth = 75;
                    double cellPadding = 15;
                    double cellWidth = imageWidth + (cellPadding * 2);
    
                    int floor = (int) Math.floor(width / cellWidth);
                    int numCol = floor == 0 ? 1 : floor;
                    // currentNumCols.set(numCol);
                  //  int numRows = numCells > 0 && numCol != 0 ? (int) Math.ceil(numCells / (double) numCol) : 1;
    
                  ArrayList<HBox> rowsBoxes = new ArrayList<HBox>();
    
                  ItemIterator grid = new ItemIterator();
                  //j = row
                  //i = col
      
                  for (Map.Entry<String, NoteInterface> entry : m_apps.entrySet()) {
                        NoteInterface noteInterface = entry.getValue();
    
                        if(rowsBoxes.size() < (grid.getJ() + 1)){
                            HBox newHBox = new HBox();
                            rowsBoxes.add(newHBox);
                            m_listBox.getChildren().add(newHBox);
                        }
    
                        HBox rowBox = rowsBoxes.get(grid.getJ());
    
                        IconButton iconButton = new IconButton(noteInterface.getAppIcon(), noteInterface.getName(), IconStyle.ICON);
    
                        rowBox.getChildren().add(iconButton);
        
                        if (grid.getI() < numCol) {
                            grid.setI(grid.getI() + 1);
                        } else {
                            grid.setI(0);
                            grid.setJ(grid.getJ() + 1);
                        }
                  } */

    public ContentTabs getContentTabs(){
        return m_contentTabs;
    }




    public class ContentTabs extends VBox{
        public final static double TAB_SCROLL_HEIGHT = 19;
        public final static double EXTENDED_TAB_SCROLL_HEIGHT = 28;

        private HBox m_tabsBox;
        private ScrollPane m_tabsScroll;
       // private ScrollPane m_bodyScroll;
       // private SimpleDoubleProperty m_tabsHeight = new SimpleDoubleProperty(30);
        private StackPane m_bodyBox;

        private SimpleDoubleProperty m_bodyHeight;

        public ReadOnlyDoubleProperty bodyWidthProperty(){
            return m_contentWidth;
        }

        public ReadOnlyDoubleProperty bodyHeightProperty(){
            return m_bodyHeight;
        }

       
        private HashMap<NoteBytes, ContentTab> m_itemTabs = new HashMap<>();

        private SimpleObjectProperty<NoteBytes> m_currentId = new SimpleObjectProperty<>(null);
        private SimpleObjectProperty<NoteBytes> m_lastRemovedTabId = new SimpleObjectProperty<>();

        public ReadOnlyObjectProperty<NoteBytes> lastRemovedTabIdProperty(){
            return m_lastRemovedTabId;
        }
        
        public ContentTabs(){
            setAlignment(Pos.TOP_LEFT);
            setPadding(new Insets(0,2,2,2));
            setId("darkBox");

            m_bodyHeight = new SimpleDoubleProperty();
            
            m_tabsBox = new HBox();
            m_tabsBox.setAlignment(Pos.CENTER_LEFT);

            

            m_tabsScroll = new ScrollPane(m_tabsBox);
            m_tabsScroll.prefViewportWidthProperty().bind(m_contentWidth);
            
            m_tabsScroll.setPrefViewportHeight(TAB_SCROLL_HEIGHT);
            m_tabsScroll.setId("tabsBox");

            m_tabsBox.widthProperty().addListener((obs,oldval,newval)->{
                if(newval.doubleValue() > m_tabsScroll.viewportBoundsProperty().get().getWidth()){
                    if(m_tabsScroll.getPrefViewportHeight() != EXTENDED_TAB_SCROLL_HEIGHT){
                        m_tabsScroll.setPrefViewportHeight(EXTENDED_TAB_SCROLL_HEIGHT);
                    }
                    if(newval.doubleValue() > oldval.doubleValue()){
                        m_tabsScroll.setHvalue(m_tabsScroll.getHmax());
                    }
                }else{
                 
                    m_tabsScroll.setPrefViewportHeight(TAB_SCROLL_HEIGHT);
                    
                   
                }

            });
            m_bodyHeight.bind(m_contentHeight.subtract(m_tabsScroll.heightProperty()).subtract(1));

            m_bodyBox = new StackPane();
            /* 
            m_bodyScroll = new ScrollPane();
            m_bodyScroll.prefViewportWidthProperty().bind(m_contentBox.widthProperty().subtract(1));
            m_bodyScroll.prefViewportHeightProperty().bind(m_contentBox.heightProperty().subtract(m_tabsScroll.heightProperty()).subtract(1));
            */

            m_currentId.addListener((obs,oldval,newval)->{
                m_bodyBox.getChildren().clear();
                if(newval != null){
                    ContentTab tab = m_itemTabs.get(newval);
                    if(tab != null){
                        m_bodyBox.getChildren().add(tab.getPane());
                    }else{
                        m_currentId.set(null);
                    }
                }
            });

     
        }

        public ContentTab getTab(String id){
            return m_itemTabs.get(id);
        }

        public boolean containsId(String id){
            return m_itemTabs.get(id) != null;
        }

        public void addContentTab(ContentTab tab){
            Pane tabPane = tab.getPane();
            NoteBytes id = tab.getId();
            if(tabPane != null && id != null){
                m_itemTabs.put(id, tab);
                tab.currentIdProperty().bind(m_currentId);
                m_tabsBox.getChildren().add(tab.getTabBox());
                EventHandler<ActionEvent> onCloseHandler = onClose->{
                    removeContentTab(id);
                    tab.onCloseBtn(null);
                };
                tab.onCloseBtn(onCloseHandler);
                tab.onTabClicked(e->{
                    m_currentId.set(tab.getId());
                    
                });
       
                tabPane.prefWidthProperty().bind(m_contentWidth);
                tabPane.prefHeightProperty().bind(m_bodyHeight);
                m_currentId.set(id);
            }
            if(m_itemTabs.size() > 0){
                if(!getChildren().contains(m_tabsScroll)){
                    getChildren().addAll( m_tabsScroll, m_bodyBox);
                }
            }
        }


        public void removeContentTab(NoteBytes id){
            boolean isCurrentTab = m_currentId.get() != null && m_currentId.get().equals(id);
            if(isCurrentTab){
                m_currentId.set(null);
            }
           
            ContentTab tab = m_itemTabs.remove(id);
            if(tab != null){

                tab.currentIdProperty().unbind();
                tab.onCloseBtn(null);
                tab.onTabClicked(null);
                 
                Pane tabPane = tab.getPane();
                tabPane.prefWidthProperty().unbind();
                tabPane.prefHeightProperty().unbind();

                tab.shutdown();
                m_lastRemovedTabId.set(tab.getId());
                
                m_tabsBox.getChildren().remove( tab.getTabBox());
               
            }

            if(m_itemTabs.size() == 0){
                if(getChildren().contains(m_tabsScroll)){
                    getChildren().clear();
                }
            }

            if(isCurrentTab){

                for (Map.Entry<NoteBytes, ContentTab> entry : m_itemTabs.entrySet()) {
                    
                    m_currentId.set(entry.getKey());
                    if(m_tabsBox.widthProperty().get() > m_tabsScroll.viewportBoundsProperty().get().getWidth()){
                        ContentTab currentTab = entry.getValue();
                        HBox tabBox = currentTab.getTabBox();
                        double x = tabBox.getLayoutX();

                        double hPos = x / tabBox.widthProperty().get();

                        m_tabsScroll.setHvalue(hPos);
                    }

                    break;
                }
            }
        }

    
        public ArrayList<ContentTab> getContentTabByParentId(NoteBytes parentId){
            ArrayList<ContentTab> result = new ArrayList<>();
            
            for (Map.Entry<NoteBytes, ContentTab> entry : m_itemTabs.entrySet()) {
                ContentTab contentTab = entry.getValue();
                if(parentId != null){   
                    if(contentTab.getParentId() != null && contentTab.getParentId().equals(parentId)){
                        result.add(contentTab);
                    }
                }else{
                    if(contentTab.getParentId() == null){
                        result.add(contentTab);
                    }
                }
            }
            
            return result;
        }

        public void removeByParentId(NoteBytes id){
            for (Map.Entry<NoteBytes, ContentTab> entry : m_itemTabs.entrySet()) {
                ContentTab contentTab = entry.getValue();
                
                if(contentTab.getParentId().equals(id)){
                    removeContentTab(contentTab.getId());
                }
            }        
        }

    } 



    public class NetworkControl {
        private SimpleObjectProperty <NoteUUID> m_networkId = new SimpleObjectProperty<>();

        public NetworkControl(){
            
        }
        
 
        private Future<?> sendToApps(Network app, String destinationId, int senderType, String senderId, PipedOutputStream outputStream, EventHandler<WorkerStateEvent> onSucceeded,  EventHandler<WorkerStateEvent> onFailed){
            if(app != null){

                return app.sendStream(outputStream, senderType, senderId); 
               
            }else{
                return Utils.returnException(NoteConstants.ERROR_NOT_FOUND, getExecService(), onFailed);
            }
        }


        private Future<?> sendToNetworks(Network network, String destinationId,int senderType, String senderId, PipedOutputStream outputStream, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
            if(network != null){
                return network.sendStream(outputStream, senderType, senderId); 
            }else{
                return Utils.returnException(NoteConstants.ERROR_NOT_FOUND, getExecService(), onFailed);
            }
        } 
    } 


}
