package io.netnotes.engine.core;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PipedOutputStream;
import java.nio.file.Files;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.reactfx.util.FxTimer;

import io.netnotes.engine.AppBox;
import io.netnotes.engine.AppConstants;
import io.netnotes.engine.AppInterface;
import io.netnotes.engine.BufferedButton;
import io.netnotes.engine.BufferedMenuButton;
import io.netnotes.engine.ContentTab;
import io.netnotes.engine.HashData;
import io.netnotes.engine.HashDataDownloader;
import io.netnotes.engine.HostServicesInterface;
import io.netnotes.engine.Network;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NetworkLocation;
import io.netnotes.engine.NetworksDataInterface;
import io.netnotes.engine.NoteBytes;
import io.netnotes.engine.NoteBytesArray;
import io.netnotes.engine.NoteBytesObject;
import io.netnotes.engine.NoteBytesPair;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteFile;
import io.netnotes.engine.NoteInteger;
import io.netnotes.engine.NoteListString;
import io.netnotes.engine.NoteLong;
import io.netnotes.engine.ResizeHelper;
import io.netnotes.engine.Stages;
import io.netnotes.engine.TabAppBox;
import io.netnotes.engine.UpdateInformation;
import io.netnotes.engine.Utils;
import io.netnotes.engine.IconButton.IconStyle;
import io.netnotes.engine.NoteFile.NoteFileInterface;
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

public class NetworksData extends Network {

    public final static long DEFAULT_CYCLE_PERIOD = 7;
    public final static String NETWORK_ID = "NetworksData";

    public final static String APPS = "Apps";
    public final static String NETWORKS = "Networks";

    public final static String NO_NETWORK_ID = "NO_NETWORK";

    public final static int BTN_IMG_SIZE = 30;
    public final static long EXECUTION_TIME = 500;
    
    public final static NetworkInformation NO_NETWORK = new NetworkInformation(NO_NETWORK_ID, "(none)", new Image( AppConstants.NETWORK_ICON256), new Image( AppConstants.NETWORK_ICON), "No network selected" );
    
    public static final String UNKNOWN_LOCATION = "Unknown";

    private SimpleObjectProperty<String> m_currentNetworkId = new SimpleObjectProperty<>(null);

    private HashMap<String, NetworkLocation>  m_networkLocations = new HashMap<>();
 
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
    
    private static final NoteListString noteFilePath = new NoteListString(".", "data", "init", "networksData");
    private NoteFile m_noteFile = null;

    public NetworksData(AppData appData, Stage appStage, HostServicesInterface hostServices) {
        super(Stages.icon, "Netnotes", NETWORK_ID);
       
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

    public boolean isAppSupported(String networkId){
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

    public boolean isNetworkSupported(String networkId){
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
                        Network network = createNetwork(noteBytes.getAsString());
                        if(network != null){
                            addNetwork(network, false);
                        }
                    }
                    
                    NoteBytesPair currentNetworkIdPair = networksTree.get("currentNetworkId");
                    String currentNetworkId = currentNetworkIdPair != null && currentNetworkIdPair.getValue().byteLength() > 0 ? currentNetworkIdPair.getAsString() : null; 
                
                    if(currentNetworkId != null && getNetwork(currentNetworkId) != null){
                        
                        m_currentNetworkId.set(currentNetworkId); 
                    }else{
                        m_currentNetworkId.set(null);
                    }
                    
                
            

                    NoteBytes[] appsArray = appsArrayElement != null  ? appsArrayElement.getValueAsNoteBytesArray().getAsArray() : new NoteBytes[0];
                    
                    for (NoteBytes noteBytes : appsArray) {

                        Network app = createApp(noteBytes.getAsString());
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
                appUpdated();
            }, (onBytesFailed)->{
                initLayout();
            });

        }else{

            String[] appIds = getAppInterface().getDefaultAppIds();
            if(appIds != null){
                for(String appId : appIds){
                    installApp(appId, false);
                }
            }
            String[] networkIds = getAppInterface().getDefaultNetworkIds();
            if(networkIds != null){
                for(String networkId : networkIds){
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

  

    protected Future<?> sendNote(String toId, PipedOutputStream outputStream){
        if(toId != null){
            PipedOutputStream locationOutputStream = getLocationOuputStream(toId);
            
            Future<?> future = Utils.writeStreamToStream(outputStream, locationOutputStream, getExecService(), (failed->{
                Utils.writeLogMsg("networksData.sendNote.writeStreamToStream", failed);
            }));
          
            return future;
        }
        return null;
    }

    protected PipedOutputStream getLocationOuputStream(String id){
        if(id != null){
            NetworkLocation location = m_networkLocations.get(id);
            if(location != null){
                PipedOutputStream outputStream = new PipedOutputStream();
                location.getNetwork().receiveNote(outputStream);
                return outputStream;
            }
        }

        return null;
    }

    @Override
    protected NetworksDataInterface getNetworksData(){
        return new NetworksDataInterface() {

            @Override
            public void sendNote(String toId, PipedOutputStream outputStream) {
                 NetworksData.this.sendNote(toId, outputStream);
            }

            @Override
            public ExecutorService getExecService() {
                return getAppData().getExecService();
            }
            
        };
    }


    private Network createApp(String networkId){
        if(getApp(networkId) == null){

            Network app = getAppInterface().createApp(networkId);
            if(app != null){
                app.init(getNetworksData());
                return app;
            }
        }
        return null;
    }

    private Network createNetwork(String networkId){
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


    public static NoteBytesObject getNoteDataObject(String type, String code, String... data){
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("type", type),
            new NoteBytesPair("code", code),
            new NoteBytesPair("timeStamp", new NoteLong(System.currentTimeMillis())), 
            new NoteBytesPair("data", new NoteListString(data))
        });
    }

    public static NoteBytesObject getNoteDataObject(String type, String code, NoteBytesArray data){
        return new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair("type", type),
            new NoteBytesPair("code", code),
            new NoteBytesPair("timeStamp", new NoteLong(System.currentTimeMillis())), 
            new NoteBytesPair("data", data)
        });
    }


   
    private boolean addApp(Network app, boolean isSave) {
        String networkId = app.getNetworkId();
        if (getApp(networkId) == null) {
            
            NetworkLocation networkLocation = new NetworkLocation(app, APPS);
            m_networkLocations.put( networkId, networkLocation);
            
            if(isSave){
                save();
                appUpdated();
                sendNoteToSubscribers(getNoteDataObject(APPS, NoteConstants.LIST_ITEM_ADDED, networkId));

            }
            return true;
        }
        return false;
    }

    protected Map<String, NetworkLocation> getNetworkLocations(){
        return m_networkLocations;
    }

    private boolean addNetwork(Network network, boolean isSave) {
        String networkId = network.getNetworkId();
        if (getNetwork(networkId) == null) {
            NetworkLocation networkLocation = new NetworkLocation(network, NETWORKS);
            m_networkLocations.put( networkId, networkLocation);
           
            if(isSave){
                networkUpdated();
                sendNoteToSubscribers(getNoteDataObject(NETWORKS, NoteConstants.LIST_ITEM_ADDED, networkId));
                save();
            }

            return true;
        }
        return false;
    }

    protected NetworkInformation getLocationNetworkInformation(String networkId){
        NetworkLocation location = m_networkLocations.get(networkId);
        return location != null ? location.getNetworkInformation() : null;
    }

    protected boolean removeNetwork(String networkId, boolean isSave){       
    
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
                    save();
                    networkUpdated();
                    sendNoteToSubscribers(getNoteDataObject(NETWORKS, NoteConstants.LIST_ITEM_REMOVED, networkId));
                }

                return true;
            }
        }
     
        return false;
        
    }

    protected SimpleObjectProperty<String> currentNetworkIdProperty(){
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


    protected Network getApp(String networkId) {
        
        NetworkLocation location = m_networkLocations.get(networkId);

        return location != null ? location.getNetwork() : null;
  
    }


    protected boolean installNetwork(String networkId){
        return installNetwork(networkId, true);
    }

    protected boolean installNetwork(String networkId, boolean isSave){
        if(isNetworkSupported(networkId)){           
            return addNetwork(createNetwork(networkId), isSave);

        }
        return false;
    }

    protected boolean installApp(String networkId){
        return installApp(networkId, true);
    }

    protected boolean installApp(String networkId, boolean save) {
        if(getApp(networkId) == null && isAppSupported(networkId)){
           
            return addApp(createApp(networkId), true);
           
        }
        return false;
    }


    private void addAllApps(boolean isSave) {
        NetworkInformation[] supportedApps = getAppInterface().getSupportedApps();
        NoteBytesArray addedArray = new NoteBytesArray();

        for (NetworkInformation networkInfo : supportedApps) {
            String networkId = networkInfo.getNetworkId();
            if(installApp(networkId, false)){

                addedArray.add(networkId);
            }
        }
        if(isSave){
            save();
            appUpdated();
            sendNoteToSubscribers(getNoteDataObject(APPS, NoteConstants.LIST_ITEM_ADDED, addedArray));
        }
    }

    protected void removeAllApps(boolean isSave) {
        Iterator<Map.Entry<String, NetworkLocation>> it =  m_networkLocations.entrySet().iterator();
        NoteBytesArray removedArray = new NoteBytesArray();

        while (it.hasNext()) {
            Map.Entry<String, NetworkLocation> entry = it.next(); 
            NetworkLocation location = entry.getValue();
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
            sendNoteToSubscribers(getNoteDataObject(APPS, NoteConstants.LIST_ITEM_REMOVED, removedArray));
        }
    }

    protected void removeAllNetworks(boolean isSave) {
        Iterator<Map.Entry<String, NetworkLocation>> it =  m_networkLocations.entrySet().iterator();
        NoteBytesArray removedArray = new NoteBytesArray();

        while (it.hasNext()) {
            Map.Entry<String, NetworkLocation> entry = it.next(); 
            NetworkLocation location = entry.getValue();
            if(location.isNetwork()){
                String id = entry.getKey();

                if(removeNetwork(id, false)){
                    removedArray.add(id);
                }
            }
        }
        if(isSave){
            save();
            networkUpdated();
            sendNoteToSubscribers(getNoteDataObject(NETWORKS, NoteConstants.LIST_ITEM_REMOVED, removedArray));
        }
    }


  
    protected boolean removeApp(String networkId) {
        return removeApp(networkId, true);
    }


    protected boolean removeApp(String networkId, boolean isSave) {

        NetworkLocation location = m_networkLocations.get(networkId);

        if(location != null && location.isApp()){
            Network network = location.getNetwork();
            network.shutdown();
            m_contentTabs.removeByParentId(networkId);
            
            m_networkLocations.remove(networkId);
      
           
            if(isSave){
                save();
                appUpdated();
                sendNoteToSubscribers(getNoteDataObject(NETWORKS, NoteConstants.LIST_ITEM_REMOVED, networkId));
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

    protected boolean isAvailable(String type, String networkId){
        NetworkLocation location = m_networkLocations.get(networkId);
        return location != null && location.getLocationType() == type;
    }

    protected Network getNetwork(String networkId) {
        NetworkLocation location = getNetworkLocation(networkId);
        return location != null ? location.getNetwork() : null;
    }

    protected NetworkLocation getNetworkLocation(String id){
        return m_networkLocations.get(id);
    }

    private AppBox getLocationTab(String networkId){
           
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

        for (Map.Entry<String, NetworkLocation> entry : m_networkLocations.entrySet()) {
            
            NetworkLocation networkLocation = entry.getValue();
            String type = networkLocation.getLocationType();
            Network network = networkLocation.getNetwork();

            switch(type){
                case APPS:
                    appsArray.add(network.getNetworkId());
                break;
                case NETWORKS:
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

    protected void openStatic(String networkId){
        TabAppBox currentTab = m_currentMenuTab.get();

        String currentTabId = currentTab != null ? currentTab.getAppId() : null;

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

   
    protected void openNetwork(String networkId){
        TabAppBox currentTab = m_currentMenuTab.get();
        
        String currentTabId = currentTab != null ? currentTab.getAppId() : null;

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

    protected void openApp(String networkId){
        TabAppBox currentTab = m_currentMenuTab.get();

        String currentTabId = currentTab != null ? currentTab.getAppId() : null;

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
        return getLocationContainsAllKeyWords(APPS, keyWords);
    }

    public List<NetworkInformation> getNetworksContainsAllKeyWords(String... keyWords){
        return getLocationContainsAllKeyWords(NETWORKS, keyWords);
    }

    public List<NetworkInformation> getLocationContainsAllKeyWords(String type, String... keyWords){
        ArrayList<NetworkInformation> list = new ArrayList<>();
        
        for (Map.Entry<String, NetworkLocation> entry : m_networkLocations.entrySet()) {
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
        for(Map.Entry<String, NetworkLocation> entry : m_networkLocations.entrySet()){
            if(entry.getValue().isNetwork()){
                return true;
            }
        }

        return false;
    }

    protected boolean isAppInstalled(){
        for(Map.Entry<String, NetworkLocation> entry : m_networkLocations.entrySet()){
            if(entry.getValue().isApp()){
                return true;
            }
        }

        return false;
    }


    private TabAppBox getStaticTab(String networkId){
        if(m_currentMenuTab.get() != null && m_currentMenuTab.get().getAppId().equals(networkId)){
            return m_currentMenuTab.get();
        } 
        switch(networkId){
            case ManageAppsTab.ID:
                 return new ManageAppsTab(getAppData(), m_appStage, m_staticContentHeight, m_staticContentWidth, m_settingsBtn, this);
            case SettingsTab.ID:
                return new SettingsTab(getAppData(), m_appStage, m_staticContentHeight, m_staticContentWidth, m_settingsBtn, this);
            case ManageNetworksTab.ID:
                return new ManageNetworksTab(getAppData(), m_appStage,m_staticContentHeight, m_staticContentWidth, m_settingsBtn, this);
        }      
        return null;
    }

    private void networkUpdated(){
        TabAppBox tab = m_currentMenuTab.get();
        if(tab != null){
            String networkId = tab.getAppId();
            if(networkId.equals(ManageNetworksTab.ID)){
                ManageNetworksTab manageNetworks = (ManageNetworksTab) tab;
                manageNetworks.updateNetworkList();
            }
        }
    }

    private void appUpdated(){
        TabAppBox tab = m_currentMenuTab.get();
        if(tab != null){
            String networkId = tab.getAppId();
            if(networkId.equals( ManageAppsTab.ID)){
                ManageAppsTab manageApps = (ManageAppsTab) tab;
                manageApps.updateAppList();
            }
        }
        m_appsMenu.updateAppsMenu();
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
                for (Map.Entry<String, NetworkLocation> entry : m_networkLocations.entrySet()) {
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
    

        public void updateAppsMenu(){
       

            m_listBox.getChildren().clear();

            if (m_networkLocations.size() != 0) {
                if(m_appsBtn != null){
                    m_appsBtn = null;
                    m_appsToolTip = null;
                }
    
                for (Map.Entry<String, NetworkLocation> entry : m_networkLocations.entrySet()) {
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

       
        private HashMap<String, ContentTab> m_itemTabs = new HashMap<>();

        private SimpleObjectProperty<String> m_currentId = new SimpleObjectProperty<>(null);
        private SimpleObjectProperty<String> m_lastRemovedTabId = new SimpleObjectProperty<>();

        public ReadOnlyObjectProperty<String> lastRemovedTabIdProperty(){
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
            String id = tab.getId();
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


        public void removeContentTab(String id){
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

                for (Map.Entry<String, ContentTab> entry : m_itemTabs.entrySet()) {
                    
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
            
            for (Map.Entry<String, ContentTab> entry : m_itemTabs.entrySet()) {
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

        public void removeByParentId(String id){
            for (Map.Entry<String, ContentTab> entry : m_itemTabs.entrySet()) {
                ContentTab contentTab = entry.getValue();
                
                if(contentTab.getParentId().equals(id)){
                    removeContentTab(contentTab.getId());
                }
            }        
        }

    } 



    public class SettingsTab extends TabAppBox  {
        public final static String NAME = "Settings";
        public final static String ID = NAME;
    
        private Semaphore m_dataSemaphore;
        private String m_status = NoteConstants.STATUS_STOPPED;
        private Stage m_updateStage = null;
        private Stage m_verifyStage = null;
        private Future<?> m_updateFuture = null;

        private AppData m_appData;

        public String getStatus(){
            return m_status;
        } 
    
        private AppData getAppData(){
            return m_appData;
        }

        
    
        public SettingsTab(AppData appData, Stage appStage, SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, MenuButton menuBtn, NetworksData network){
            super(ID, NAME, appStage, heightObject, widthObject, menuBtn, network);
            minHeightProperty().bind(heightObject);
    
            m_appData = appData;

            Button settingsButton = Stages.createImageButton(Stages.logo, "Settings");
    
            HBox settingsBtnBox = new HBox(settingsButton);
            settingsBtnBox.setAlignment(Pos.CENTER);
    
            Text passwordTxt = new Text(String.format("%-18s", "  Password:"));
            passwordTxt.setFill(Stages.txtColor);
            passwordTxt.setFont(Stages.txtFont);
    

            Button passwordBtn = new Button("(click to update)");
            passwordBtn.setAlignment(Pos.CENTER_LEFT);
            passwordBtn.setId("toolBtn");
            passwordBtn.setOnAction(e -> {
                if(m_updateStage == null && m_verifyStage == null){
                    m_verifyStage = getAppData().verifyAppKey(()->{
                        Button closeBtn = new Button();
                        String title = "Netnotes - Password";
                        m_updateStage = new Stage();
                        m_updateStage.getIcons().add(Stages.logo);
                        m_updateStage.initStyle(StageStyle.UNDECORATED);
                        m_updateStage.setTitle(title);
                
                       Stages.createPassword(m_updateStage, title, Stages.logo, Stages.logo, closeBtn, getNetwork().getExecService(), (onSuccess) -> {
                            if(m_updateFuture == null){
                                Object sourceObject = onSuccess.getSource().getValue();
            
                                if (sourceObject != null && sourceObject instanceof NoteBytes) {
                                    NoteBytes pass = (NoteBytes) sourceObject;
            
                                    if (pass.get().length > 0) {
            
                                        Stage statusStage = Stages.getStatusStage("Netnotes - Updating Password...", "Updating Password...");
                                        statusStage.show();

                                  
                                        
                                        m_updateFuture = getAppData().updateAppKey(pass, onFinished ->{
                                            Object finishedObject = onFinished.getSource().getValue();
                                            statusStage.close();
                                            m_updateFuture = null;
                                            if(finishedObject != null && finishedObject instanceof Boolean && (Boolean) finishedObject){
                                                closeBtn.fire();
                                            }
                                        });
                                            
                                    }else{
                                        closeBtn.fire();
                                    }
                                }else{
                                    closeBtn.fire();
                                }
                            }
                    
                        });
                        m_updateStage.show();
                        m_updateStage.setOnCloseRequest(onCloseEvent->{
                            m_updateStage = null;
                        });
                        closeBtn.setOnAction(closeAction ->{
                            if(m_updateStage != null){
                                m_updateStage.close();
                                m_updateStage = null;
                            }
                        });
                    },()->{
                        m_verifyStage = null;
                    });
                }
            });

            
    
            HBox passwordBox = new HBox(passwordTxt, passwordBtn);
            passwordBox.setAlignment(Pos.CENTER_LEFT);
            passwordBox.setPadding(new Insets(10, 0, 10, 10));
            passwordBox.setMinHeight(30);
    
            Tooltip checkForUpdatesTip = new Tooltip();
            checkForUpdatesTip.setShowDelay(new javafx.util.Duration(100));
    
            String checkImageUrlString = AppConstants.CHECKMARK_ICON;
            
    
            BufferedButton checkForUpdatesToggle = new BufferedButton(getAppData().getUpdates() ? checkImageUrlString : null, Stages.MENU_BAR_IMAGE_WIDTH);
            checkForUpdatesToggle.setTooltip(checkForUpdatesTip);
    
            checkForUpdatesToggle.setOnAction(e->{
                boolean wasUpdates = getAppData().getUpdates();
                
                wasUpdates = !wasUpdates;
    
                checkForUpdatesToggle.setImage(wasUpdates ? new Image(checkImageUrlString) : null);
        
                try {
                    getAppData().setUpdates(wasUpdates);
                } catch (IOException e1) {
                    Alert a = new Alert(AlertType.NONE, e1.toString(), ButtonType.CANCEL);
                    a.setTitle("Error: File IO");
                    a.setHeaderText("Error");
                    a.initOwner(getAppStage());
                    a.show();
                }
            });
    
            Text versionTxt = new Text(String.format("%-18s", "  Version:"));
            versionTxt.setFill(Stages.txtColor);
            versionTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            TextField versionField = new TextField(getAppData().getAppInterface().getCurrentVersion() + "");
            versionField.setFont(Stages.txtFont);
            versionField.setId("formField");
            versionField.setEditable(false);
            HBox.setHgrow(versionField, Priority.ALWAYS);
       
            HBox versionBox = new HBox(versionTxt, versionField);
            versionBox.setPadding(new Insets(10,10,5,10));
            versionBox.setAlignment(Pos.CENTER_LEFT);
    
            Text fileTxt = new Text(String.format("%-18s", "  File:"));
            fileTxt.setFill(Stages.txtColor);
            fileTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            TextField fileField = new TextField(m_appData.getAppFile().getName());
            fileField.setFont(Stages.txtFont);
            fileField.setEditable(false);
            fileField.setId("formField");
            HBox.setHgrow(fileField, Priority.ALWAYS);
       
            HBox fileBox = new HBox(fileTxt, fileField);
            HBox.setHgrow(fileBox, Priority.ALWAYS);
            fileBox.setAlignment(Pos.CENTER_LEFT);
            fileBox.setPadding(new Insets(5,10,5,10));
        
            Text hashTxt = new Text(String.format("%-18s", "  Hash (Blake-2b):"));
            hashTxt.setFill(Stages.txtColor);
            hashTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            TextField hashField = new TextField(m_appData.appHashData().getHashStringHex());
            hashField.setFont(Stages.txtFont);
            hashField.setEditable(false);
            hashField.setId("formField");
            HBox.setHgrow(hashField, Priority.ALWAYS);
       
            HBox hashBox = new HBox(hashTxt, hashField);
            HBox.setHgrow(hashBox, Priority.ALWAYS);
            hashBox.setPadding(new Insets(5,10,5,10));
            hashBox.setAlignment(Pos.CENTER_LEFT);
    
            Text passwordHeading = new Text("Password");
            passwordHeading.setFont(Stages.txtFont);
            passwordHeading.setFill(Stages.txtColor);
    
            HBox passHeadingBox = new HBox(passwordHeading);
            HBox.setHgrow(passHeadingBox,Priority.ALWAYS);
            passHeadingBox.setId("headingBox");
            passHeadingBox.setPadding(new Insets(5));
    
            VBox passwordSettingsBox = new VBox(passHeadingBox, passwordBox);
            passwordSettingsBox.setId("bodyBox");
    
            Text appHeading = new Text("App");
            appHeading.setFont(Stages.txtFont);
            appHeading.setFill(Stages.txtColor);
    
            HBox appHeadingBox = new HBox(appHeading);
            HBox.setHgrow(appHeadingBox,Priority.ALWAYS);
            appHeadingBox.setId("headingBox");
            appHeadingBox.setPadding(new Insets(5));
    
            VBox appSettingsBox = new VBox(appHeadingBox, versionBox, fileBox, hashBox);
            appSettingsBox.setId("bodyBox");
            
    
            Text latestVersionTxt = new Text(String.format("%-18s", "  Version:"));
            latestVersionTxt.setFill(Stages.txtColor);
            latestVersionTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            Button latestVersionField = new Button("(Click to get latest info.)");
            latestVersionField.setFont(Stages.txtFont);
            latestVersionField.setId("formField");
            HBox.setHgrow(latestVersionField, Priority.ALWAYS);
       
            HBox latestVersionBox = new HBox(latestVersionTxt, latestVersionField);
            latestVersionBox.setPadding(new Insets(10,10,5,10));
            latestVersionBox.setAlignment(Pos.CENTER_LEFT);
    
            Text latestURLTxt = new Text(String.format("%-18s", "  Url:"));
            latestURLTxt.setFill(Stages.txtColor);
            latestURLTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            TextField latestURLField = new TextField();
            latestURLField.setFont(Stages.txtFont);
            latestURLField.setEditable(false);
            latestURLField.setId("formField");
            HBox.setHgrow(latestURLField, Priority.ALWAYS);
       
            HBox latestURLBox = new HBox(latestURLTxt, latestURLField);
            HBox.setHgrow(latestURLBox, Priority.ALWAYS);
            latestURLBox.setAlignment(Pos.CENTER_LEFT);
            latestURLBox.setPadding(new Insets(5,10,5,10));
    
            Text latestNameTxt = new Text(String.format("%-18s", "  File name:"));
            latestNameTxt.setFill(Stages.txtColor);
            latestNameTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            TextField latestNameField = new TextField();
            latestNameField.setFont(Stages.txtFont);
            latestNameField.setEditable(false);
            latestNameField.setId("formField");
            HBox.setHgrow(latestNameField, Priority.ALWAYS);
       
            HBox latestNameBox = new HBox(latestNameTxt, latestNameField);
            HBox.setHgrow(latestNameBox, Priority.ALWAYS);
            latestNameBox.setAlignment(Pos.CENTER_LEFT);
            latestNameBox.setPadding(new Insets(5,10,5,10));
        
            Text latestHashTxt = new Text(String.format("%-18s", "  Hash (Blake-2b):"));
            latestHashTxt.setFill(Stages.txtColor);
            latestHashTxt.setFont(Stages.txtFont);
            //LATEST_RELEASE_URL
    
            TextField latestHashField = new TextField();
            latestHashField.setFont(Stages.txtFont);
            latestHashField.setEditable(false);
            latestHashField.setId("formField");
            HBox.setHgrow(latestHashField, Priority.ALWAYS);
       
            HBox latestHashBox = new HBox(latestHashTxt, latestHashField);
            HBox.setHgrow(latestHashBox, Priority.ALWAYS);
            latestHashBox.setPadding(new Insets(5,10,5,10));
            latestHashBox.setAlignment(Pos.CENTER_LEFT);
    
            
            Text latestHeading = new Text("Latest");
            latestHeading.setFont(Stages.txtFont);
            latestHeading.setFill(Stages.txtColor);
    
            Region latestHeadingSpacer = new Region();
            HBox.setHgrow(latestHeadingSpacer, Priority.ALWAYS);
    
            Button downloadLatestBtn = new Button("Download");
            
            SimpleObjectProperty<UpdateInformation> updateInfoProperty = new SimpleObjectProperty<>();
    
            updateInfoProperty.addListener((obs,oldval,newval)->{
            
                latestHashField.setText(newval.getJarHashData().getHashStringHex());
                latestVersionField.setText(newval.getTagName());
                latestNameField.setText(newval.getJarName());
                latestURLField.setText(newval.getJarUrl());
            
            });
            
            
    
            Button getInfoBtn = new Button("Update");
            getInfoBtn.setId("checkBtn");
            getInfoBtn.setOnAction(e->{
                getAppData().checkForUpdates(getAppData().getAppInterface().getGitHubUser(), getAppData().getAppInterface().getGitHubProject(),  updateInfoProperty);         
            });
            downloadLatestBtn.setOnAction(e->{
                SimpleObjectProperty<UpdateInformation> downloadInformation = new SimpleObjectProperty<>();
                UpdateInformation updateInfo = updateInfoProperty.get();
                File appDir = getAppData().getAppDir();
                if(updateInfo != null && updateInfo.getJarHashData() != null){
                
                    HashData appHashData = updateInfo.getJarHashData();
                    String appName = updateInfo.getJarName();
                    String urlString = updateInfo.getJarUrl();
                 
                    HashDataDownloader dlder = new HashDataDownloader(Stages.logo, urlString, appName, appDir, appHashData, HashDataDownloader.Extensions.getJarFilter());
                    dlder.start(getExecService());
    
                }else{
                    downloadInformation.addListener((obs,oldval,newval)->{
                        if(newval != null){
                            updateInfoProperty.set(newval);
    
                            String urlString = newval.getJarUrl();
                            if(urlString.startsWith("http")){  
                                HashData latestHashData = newval.getJarHashData();
                                HashDataDownloader dlder = new HashDataDownloader(Stages.logo, urlString, latestNameField.getText(),appDir, latestHashData, HashDataDownloader.Extensions.getJarFilter());
                                dlder.start(getExecService());
                            }
                        }
                    });
                    getAppData().checkForUpdates(getAppData().getAppInterface().getGitHubUser(), getAppData().getAppInterface().getGitHubProject(),  downloadInformation);
                }
            });
    
            latestVersionField.setOnAction(e->{
                getInfoBtn.fire();
            });
    
            HBox latestHeadingBox = new HBox(latestHeading, latestHeadingSpacer, getInfoBtn);
            HBox.setHgrow(latestHeadingBox,Priority.ALWAYS);
            latestHeadingBox.setId("headingBox");
            latestHeadingBox.setPadding(new Insets(5,10,5,10));
            latestHeadingBox.setAlignment(Pos.CENTER_LEFT);
         
            
           
            HBox downloadLatestBox = new HBox(downloadLatestBtn);
            downloadLatestBox.setAlignment(Pos.CENTER_RIGHT);
            downloadLatestBox.setPadding(new Insets(5,15,10,10));
    
            VBox latestSettingsBox = new VBox(latestHeadingBox, latestVersionBox, latestNameBox, latestURLBox, latestHashBox, downloadLatestBox);
            latestSettingsBox.setId("bodyBox");
            
            
            Region settingsSpacer1 = new Region();
            settingsSpacer1.setMinHeight(15);
    
            Region settingsSpacer2 = new Region();
            settingsSpacer2.setMinHeight(15);
    
            VBox settingsVBox = new VBox(settingsBtnBox, passwordSettingsBox, settingsSpacer1, appSettingsBox, settingsSpacer2, latestSettingsBox);
            HBox.setHgrow(settingsVBox, Priority.ALWAYS);
    
            settingsVBox.setAlignment(Pos.CENTER_LEFT);
            settingsVBox.setPadding(new Insets(5,10,5,5));
    
    
    
            HBox.setHgrow(settingsVBox, Priority.ALWAYS);
    
            getChildren().add(settingsVBox);
    
         
            prefWidthProperty().bind(widthObject);
    
        }
    
        private SimpleStringProperty m_titleProperty = new SimpleStringProperty(NAME);
    
        public SimpleStringProperty titleProperty(){
            return m_titleProperty;
        }
         
        public String getName(){
            return NAME;
        }
        
     
            
        


    }



    public class ManageAppsTab extends TabAppBox  {
    public static final int PADDING = 10;
    public static final String NAME = "Manage Apps";
    public static final String ID = NAME;

    private final String installDefaultText = "(Install App)";

    private MenuButton m_installMenuBtn;

    private SimpleStringProperty m_selectedAppId = new SimpleStringProperty(null);
    private VBox m_installedListBox = new VBox();
    private VBox m_notInstalledListBox = new VBox();
    private SimpleObjectProperty<NetworkInformation> m_installItemInformation = new SimpleObjectProperty<>(null);
    private HBox m_installFieldBox;

    private AppData m_appData;
    private NetworksData m_networksData;

    public ManageAppsTab(AppData appData, Stage appStage, SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, MenuButton menuBtn, NetworksData network){
        super(ID, NAME, appStage, heightObject, widthObject, menuBtn, network );

        minHeightProperty().bind(heightObject);
        minWidthProperty().bind(widthObject);
        maxWidthProperty().bind(widthObject);
        setAlignment(Pos.TOP_CENTER);
        m_appData = appData;
        m_networksData = network;
        m_installMenuBtn = new MenuButton(installDefaultText);

        
        m_installedListBox.setPadding(new Insets(10));

        Label intstalledAppsLabel = new Label("Installed");
        HBox intalledAppsTitleBox = new HBox(intstalledAppsLabel);
        intalledAppsTitleBox.setAlignment(Pos.CENTER_LEFT);
        intalledAppsTitleBox.setPadding(new Insets(10,0,0,10));

        ScrollPane listScroll = new ScrollPane(m_installedListBox);
        
        listScroll.setId("bodyBox");

        HBox installAppsListBox = new HBox(listScroll);
        installAppsListBox.setPadding(new Insets(10,20,0, 20));
        

        HBox.setHgrow(installAppsListBox, Priority.ALWAYS);
        VBox.setVgrow(installAppsListBox, Priority.ALWAYS);

        Binding<Number> listWidthBinding = installAppsListBox.widthProperty().subtract(1);
        Binding<Number> listHeightBinding = getAppStage().getScene().heightProperty().subtract(250).add( intalledAppsTitleBox.heightProperty().multiply(2)).divide(2).subtract(5);
        
        listScroll.prefViewportWidthProperty().bind(listWidthBinding);
        listScroll.prefViewportHeightProperty().bind(listHeightBinding);
        listScroll.viewportBoundsProperty().addListener((obs,oldval,newval)->{
            m_installedListBox.setMinWidth(newval.getWidth());
            m_installedListBox.setMinHeight(newval.getHeight());
        });

        HBox appsOptionsBox = new HBox();
        appsOptionsBox.setAlignment(Pos.CENTER);
        HBox.setHgrow(appsOptionsBox, Priority.ALWAYS);
        appsOptionsBox.setPadding(new Insets(0,0,0,0));

        //notInstalledApps
        Label notIntstalledAppsLabel = new Label("Available Apps");
        HBox notIntalledAppsTitleBox = new HBox(notIntstalledAppsLabel);
        notIntalledAppsTitleBox.setAlignment(Pos.CENTER_LEFT);
        notIntalledAppsTitleBox.setPadding(new Insets(10,0,0,10));

        ScrollPane notInstalledListScroll = new ScrollPane(m_notInstalledListBox);
        notInstalledListScroll.setId("bodyBox");

        HBox installableAppsListBox = new HBox(notInstalledListScroll);
        installableAppsListBox.setPadding(new Insets(10,20,0, 20));
        

        HBox.setHgrow(installableAppsListBox, Priority.ALWAYS);
        VBox.setVgrow(installableAppsListBox, Priority.ALWAYS);

        notInstalledListScroll.prefViewportWidthProperty().bind(listWidthBinding);
        notInstalledListScroll.prefViewportHeightProperty().bind(listHeightBinding);
        notInstalledListScroll.viewportBoundsProperty().addListener((obs,oldval,newval)->{
            m_notInstalledListBox.setMinWidth(newval.getWidth());
            m_notInstalledListBox.setMinHeight(newval.getHeight());
        });



        VBox bodyBox = new VBox(intalledAppsTitleBox, installAppsListBox,notIntalledAppsTitleBox, installableAppsListBox, appsOptionsBox);
        HBox.setHgrow(bodyBox, Priority.ALWAYS);
        VBox.setVgrow(bodyBox,Priority.ALWAYS);
        
        updateAppList();

        Text installText = new Text("Apps: ");
        installText.setFont(Stages.txtFont);
        installText.setFill(Stages.txtColor);

        

        ImageView installFieldImgView = new ImageView();
        installFieldImgView.setPreserveRatio(true);
        installFieldImgView.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);

        
        m_installMenuBtn.setId("arrowMenuButton");
        m_installMenuBtn.setGraphic(installFieldImgView);
        m_installMenuBtn.setContentDisplay(ContentDisplay.LEFT);
        
        m_installMenuBtn.showingProperty().addListener((obs,oldval,newval)->{
            if(newval){
                m_installMenuBtn.getItems().clear();
                NetworkInformation[] supportedApps = appData.getAppInterface().getSupportedApps();

                for(int i = 0; i < supportedApps.length; i++){
                    NetworkInformation networkInformation = supportedApps[i];
                
                    if(!network.isAvailable(APPS, networkInformation.getNetworkId()) ){
                        ImageView intallItemImgView = new ImageView();
                        intallItemImgView.setPreserveRatio(true);
                        intallItemImgView.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);
                        intallItemImgView.setImage(networkInformation.getSmallIcon());
                        
                        MenuItem installItem = new MenuItem(String.format("%-30s",networkInformation.getNetworkName()), intallItemImgView);
                        installItem.setOnAction(e->{
                            m_installItemInformation.set(networkInformation);
                        });

                        m_installMenuBtn.getItems().add(installItem);
                    }
                }
                if(m_installMenuBtn.getItems().size() == 0){
                    MenuItem installItem = new MenuItem(String.format("%-30s", "(none available)"));
                    m_installMenuBtn.getItems().add(installItem);
                }
            }
        });


        m_installFieldBox = new HBox(m_installMenuBtn);
        HBox.setHgrow(m_installFieldBox, Priority.ALWAYS);
        m_installFieldBox.setId("bodyBox");
        m_installFieldBox.setPadding(new Insets(2, 5, 2, 2));
        m_installFieldBox.setMaxHeight(18);
        m_installFieldBox.setAlignment(Pos.CENTER_LEFT);

        m_installMenuBtn.prefWidthProperty().bind(m_installFieldBox.widthProperty().subtract(1));

        Button installBtn = new Button("Install");
        installBtn.setMinWidth(110);

        HBox installBox = new HBox(installText, m_installFieldBox);
        HBox.setHgrow(installBox,Priority.ALWAYS);
        installBox.setAlignment(Pos.CENTER);
        installBox.setPadding(new Insets(10,20,10,20));

        m_installItemInformation.addListener((obs,oldval,newval)->{
            if(newval != null){
                m_installMenuBtn.setText(newval.getNetworkName());
                installFieldImgView.setImage(newval.getSmallIcon());
                
                if(!m_installFieldBox.getChildren().contains(installBtn)){
                    m_installFieldBox.getChildren().add(installBtn);
                }
            }else{
                m_installMenuBtn.setText(installDefaultText);
                installFieldImgView.setImage(null);
                if(m_installFieldBox.getChildren().contains(installBtn)){
                    m_installFieldBox.getChildren().remove(installBtn);
                }
            }
        });


        Region topRegion = new Region();
        
        VBox.setVgrow(topRegion, Priority.ALWAYS);
            
        Region hBar1 = new Region();
        hBar1.setPrefWidth(400);
        hBar1.setMinHeight(2);
        hBar1.setId("hGradient");

        HBox gBox1 = new HBox(hBar1);
        gBox1.setAlignment(Pos.CENTER);
        gBox1.setPadding(new Insets(30, 30, 20, 30));

                                    
        HBox botRegionBox = new HBox();
        botRegionBox.setMinHeight(40);
        getChildren().addAll(  bodyBox, gBox1, installBox,botRegionBox);


        installBtn.setOnAction(e->{
            NetworkInformation info = m_installItemInformation.get();
            String networkId = info != null ? info.getNetworkId() : null;
            
            if (networkId != null){
                m_installItemInformation.set(null);
                network.installApp(networkId);
                if(m_selectedAppId.get() == null){
                    m_selectedAppId.set(networkId);
                }
            }
        });
        

        m_selectedAppId.addListener((obs,oldval,newval)->{
            updateAppList();
        });


        

    }


    public void updateAppList(){
        m_installedListBox.getChildren().clear();
        m_notInstalledListBox.getChildren().clear();


        if(m_networksData.isAppInstalled()){
            for (Map.Entry<String, NetworkLocation> entry : m_networksData.getNetworkLocations().entrySet()) {
                NetworkLocation location = entry.getValue();
                Network app = location.isApp() ? location.getNetwork() : null;
                
                if(app != null){
                    ImageView appImgView = new ImageView();
                    appImgView.setPreserveRatio(true);
                    appImgView.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);
                    appImgView.setImage(app.getAppIcon());

                    Label nameText = new Label(app.getName());
                    nameText.setFont(Stages.txtFont);
                    nameText.setPadding(new Insets(0,0,0,10));

                    int topMargin = 15;

                    Region marginRegion = new Region();
                    marginRegion.setMinWidth(topMargin);

                    Region growRegion = new Region();
                    HBox.setHgrow(growRegion, Priority.ALWAYS);

                    MenuButton appListmenuBtn = new MenuButton("⋮");

                    MenuItem openItem = new MenuItem("⇲   Open…");
                    openItem.setOnAction(e->{
                        appListmenuBtn.hide();

                        m_networksData.openApp(app.getNetworkId());
                    });

                    MenuItem removeItem = new MenuItem("🗑   Uninstall");
                    removeItem.setOnAction(e->{
                        appListmenuBtn.hide();
                            m_networksData.removeApp(app.getNetworkId());
                    });

                    appListmenuBtn.getItems().addAll(openItem, removeItem);

                    HBox networkItemTopRow = new HBox( appImgView, nameText, growRegion, appListmenuBtn);
                    HBox.setHgrow(networkItemTopRow, Priority.ALWAYS);
                    networkItemTopRow.setAlignment(Pos.CENTER_LEFT);
                    networkItemTopRow.setPadding(new Insets(2,0,2,0));

                    VBox networkItem = new VBox(networkItemTopRow);
                    networkItem.setFocusTraversable(true);
                    networkItem.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(networkItem, Priority.ALWAYS);
                    networkItem.setId("rowBtn");
                    networkItem.setPadding(new Insets(2,5,2,5));

                    networkItemTopRow.setOnMouseClicked(e->{
                        if(e.getClickCount() == 2){
                            openItem.fire();
                        }
                    });


                    m_installedListBox.getChildren().add(networkItem);
                }

            }

            m_installMenuBtn.getItems().clear();
            NetworkInformation[] supportedApps = m_appData.getAppInterface().getSupportedApps();

            for(int i = 0; i < supportedApps.length; i++){
                NetworkInformation networkInformation = supportedApps[i];
                
                if(! m_networksData.isAvailable(APPS, networkInformation.getNetworkId())){

                    ImageView appImgView = new ImageView();
                    appImgView.setPreserveRatio(true);
                    appImgView.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);
                    appImgView.setImage(networkInformation.getIcon());

                    Label nameText = new Label(networkInformation.getNetworkName());
                    nameText.setFont(Stages.txtFont);
                    nameText.setPadding(new Insets(0,0,0,10));

                    int topMargin = 15;

                    Region marginRegion = new Region();
                    marginRegion.setMinWidth(topMargin);

                    Region growRegion = new Region();
                    HBox.setHgrow(growRegion, Priority.ALWAYS);

                    MenuButton appListmenuBtn = new MenuButton("⋮");

                    MenuItem installItem = new MenuItem("⇱   Install");
                    installItem.setOnAction(e->{
                        appListmenuBtn.hide();
                        m_networksData.installApp(networkInformation.getNetworkId());
                    });

                    appListmenuBtn.getItems().addAll(installItem);

                    HBox networkItemTopRow = new HBox( appImgView, nameText, growRegion, appListmenuBtn);
                    HBox.setHgrow(networkItemTopRow, Priority.ALWAYS);
                    networkItemTopRow.setAlignment(Pos.CENTER_LEFT);
                    networkItemTopRow.setPadding(new Insets(2,0,2,0));

                    VBox networkItem = new VBox(networkItemTopRow);
                    networkItem.setFocusTraversable(true);
                    networkItem.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(networkItem, Priority.ALWAYS);
                    networkItem.setId("rowBtn");
                    networkItem.setPadding(new Insets(2,5,2,5));

                    networkItemTopRow.setOnMouseClicked(e->{
                        m_installItemInformation.set(networkInformation);
                    });


                    m_notInstalledListBox.getChildren().add(networkItem);
                }
            }
        

            
        }else{
            BufferedButton emptyAddAppBtn = new BufferedButton(AppConstants.SETTINGS_ICON, 75);
            emptyAddAppBtn.setText("Install App");
            emptyAddAppBtn.setContentDisplay(ContentDisplay.TOP);
            emptyAddAppBtn.setOnAction(e->{
                m_installMenuBtn.show();
            });

            HBox addBtnBox = new HBox(emptyAddAppBtn);
            HBox.setHgrow(addBtnBox, Priority.ALWAYS);
            VBox.setVgrow(addBtnBox, Priority.ALWAYS);
            addBtnBox.setAlignment(Pos.CENTER);
            m_installedListBox.getChildren().add(addBtnBox);
        }
    }




    @Override
    public void shutdown(){
        this.prefWidthProperty().unbind();
    }


    }

    public class ManageNetworksTab extends TabAppBox {
        public static final String NAME = "Manage Networks";
        public static final String ID = NAME;
        private VBox m_listBox = new VBox();

        private SimpleObjectProperty<NetworkInformation> m_installItemInformation = new SimpleObjectProperty<>(null);
        private HBox m_installFieldBox;
        private MenuButton m_installMenuBtn;

        private NetworksData m_networksData;



        public ManageNetworksTab(AppData appData, Stage appStage, SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, MenuButton menuBtn, NetworksData network){
            super(ID, NAME, appStage, heightObject, widthObject, menuBtn, network );
           
            m_networksData = network;

            prefWidthProperty().bind(widthObject);
            prefHeightProperty().bind(heightObject);
            setAlignment(Pos.CENTER);
            minHeightProperty().bind(heightObject);
    
           
            m_listBox.setPadding(new Insets(10));
         

            ScrollPane listScroll = new ScrollPane(m_listBox);
           
            listScroll.setId("bodyBox");

            HBox networkListBox = new HBox(listScroll);
            networkListBox.setPadding(new Insets(20,40,0, 40));
        

            HBox.setHgrow(networkListBox, Priority.ALWAYS);
            VBox.setVgrow(networkListBox, Priority.ALWAYS);

            listScroll.prefViewportWidthProperty().bind(networkListBox.widthProperty().subtract(1));
            listScroll.prefViewportHeightProperty().bind(getAppStage().getScene().heightProperty().subtract(250));

            listScroll.viewportBoundsProperty().addListener((obs,oldval,newval)->{
                m_listBox.setMinWidth(newval.getWidth());
                m_listBox.setMinHeight(newval.getHeight());
            });

            HBox networkOptionsBox = new HBox();
            networkOptionsBox.setAlignment(Pos.CENTER);
            HBox.setHgrow(networkOptionsBox, Priority.ALWAYS);
            networkOptionsBox.setPadding(new Insets(0,0,0,0));

    
           

            VBox bodyBox = new VBox(networkListBox, networkOptionsBox);
            HBox.setHgrow(bodyBox, Priority.ALWAYS);
            VBox.setVgrow(bodyBox,Priority.ALWAYS);

            updateNetworkList();

            Text installText = new Text("Networks: ");
            installText.setFont(Stages.txtFont);
            installText.setFill(Stages.txtColor);

            String emptyInstallstring = "(Click to select network)";
            m_installMenuBtn = new MenuButton(emptyInstallstring);
            m_installMenuBtn.setId("arrowMenuButton");

            m_installMenuBtn.showingProperty().addListener((obs,oldval,newval)->{
                if(newval){
                    m_installMenuBtn.getItems().clear();
                    NetworkInformation[] supportedNetworks = appData.getAppInterface().getSupportedNetworks();

                    for(int i = 0; i < supportedNetworks.length; i++){
                        NetworkInformation networkInformation = supportedNetworks[i];

                        if(!network.isAvailable(NETWORKS, networkInformation.getNetworkId())){
                            ImageView intallItemImgView = new ImageView();
                            intallItemImgView.setPreserveRatio(true);
                            intallItemImgView.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);
                            intallItemImgView.setImage(networkInformation.getSmallIcon());
                            MenuItem installItem = new MenuItem(String.format("%-30s",networkInformation.getNetworkName()), intallItemImgView);
                        
                            installItem.setOnAction(e->{
                                m_installItemInformation.set(networkInformation);
                            });
        
                            m_installMenuBtn.getItems().add(installItem);
                        }
                    }
                    if(m_installMenuBtn.getItems().size() == 0){
                        MenuItem installItem = new MenuItem(String.format("%-30s","(none available)"));
                        m_installMenuBtn.getItems().add(installItem);
                    }
                }
            });

            ImageView installFieldImgView = new ImageView();
            installFieldImgView.setPreserveRatio(true);
            installFieldImgView.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);

            m_installFieldBox = new HBox(m_installMenuBtn);
            HBox.setHgrow(m_installFieldBox, Priority.ALWAYS);
            m_installFieldBox.setId("bodyBox");
            m_installFieldBox.setPadding(new Insets(2, 5, 2, 2));
            m_installFieldBox.setMaxHeight(18);
            m_installFieldBox.setAlignment(Pos.CENTER_LEFT);

            m_installMenuBtn.prefWidthProperty().bind(m_installFieldBox.widthProperty().subtract(-1));

            Button installBtn = new Button("Install");
      
            HBox installBox = new HBox(installText, m_installFieldBox);
            HBox.setHgrow(installBox,Priority.ALWAYS);
            installBox.setAlignment(Pos.CENTER);
            installBox.setPadding(new Insets(10,20,10,20));

            m_installItemInformation.addListener((obs,oldval,newval)->{
                if(newval != null){
                    m_installMenuBtn.setText(newval.getNetworkName());
                    installFieldImgView.setImage(newval.getSmallIcon());
                    if(!m_installFieldBox.getChildren().contains(installFieldImgView)){
                        m_installFieldBox.getChildren().add(0, installFieldImgView);
                    }
                    if(!m_installFieldBox.getChildren().contains(installBtn)){
                        m_installFieldBox.getChildren().add(installBtn);
                    }
                }else{
                    m_installMenuBtn.setText(emptyInstallstring);
                    installFieldImgView.setImage(null);
                    if(m_installFieldBox.getChildren().contains(installFieldImgView)){
                        m_installFieldBox.getChildren().remove(installFieldImgView);
                    }
                    if(m_installFieldBox.getChildren().contains(installBtn)){
                        m_installFieldBox.getChildren().remove(installBtn);
                    }
                }
            });

            Region topRegion = new Region();
            
  
            VBox.setVgrow(topRegion, Priority.ALWAYS);
          
            Region hBar1 = new Region();
            hBar1.setPrefWidth(400);
            hBar1.setMinHeight(2);
            hBar1.setId("hGradient");
    
            HBox gBox1 = new HBox(hBar1);
            gBox1.setAlignment(Pos.CENTER);
            gBox1.setPadding(new Insets(30, 30, 20, 30));

                                        
            HBox botRegionBox = new HBox();
            botRegionBox.setMinHeight(40);
            getChildren().addAll( bodyBox, gBox1, installBox,botRegionBox);
    
        
            installBtn.setOnAction(e->{
                NetworkInformation info = m_installItemInformation.get();
                String networkId = info != null ? info.getNetworkId() : null;
                
                if (networkId != null){
                    m_installItemInformation.set(null);
                    network.installNetwork(networkId);
                    if(network.currentNetworkIdProperty().get() == null){
                        network.currentNetworkIdProperty().set(networkId);
                    }
                }
            });
            
        }

  

    
        public void updateNetworkList(){

            m_listBox.getChildren().clear();
    
            if(m_networksData.isNetworkInstalled()){
                for (Map.Entry<String, NetworkLocation> entry : m_networksData.getNetworkLocations().entrySet()) {
                    NetworkLocation location = entry.getValue();
                    Network network = location.isNetwork() ? location.getNetwork() : null;
                    
                    if(network != null){
                        ImageView networkImgView = new ImageView();
                        networkImgView.setPreserveRatio(true);
                        networkImgView.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);
                        networkImgView.setImage(network.getAppIcon());

                        Label nameText = new Label(network.getName());
                        nameText.setFont(Stages.txtFont);
                        nameText.setPadding(new Insets(0,0,0,10));



                        Tooltip selectedTooltip = new Tooltip();
                        selectedTooltip.setShowDelay(javafx.util.Duration.millis(100));

                        Label selectedBtn = new Label();
                        selectedBtn.setTooltip(selectedTooltip);
                        selectedBtn.setId("lblBtn");
                        
                        selectedBtn.setOnMouseClicked(e->{
                            String currentNetworkId = m_networksData.currentNetworkIdProperty().get();
                            boolean selectedNetwork = currentNetworkId != null && currentNetworkId.equals(network.getNetworkId());         
                        
                            if(selectedNetwork){
                                m_networksData.currentNetworkIdProperty().set(null);
                            }else{
                                m_networksData.currentNetworkIdProperty().set(network.getNetworkId());
                                m_networksData.save();
                            }
                        });

                    
                

                        Runnable updateSelectedSwitch = () ->{
                            String currentNetworkId = m_networksData.currentNetworkIdProperty().get();
                            boolean selectedNetwork = currentNetworkId != null && currentNetworkId.equals(network.getNetworkId());         
                            
                            selectedBtn.setText(selectedNetwork ? "🟊" : "⚝");
                
                            selectedTooltip.setText(selectedNetwork ? "Selected" : "Select network");
                        

                        };
        
                        updateSelectedSwitch.run();
                
                
                        int topMargin = 15;

                        Region marginRegion = new Region();
                        marginRegion.setMinWidth(topMargin);


                        Region growRegion = new Region();
                        HBox.setHgrow(growRegion, Priority.ALWAYS);

                    
                        if(m_networksData.isNetworkInstalled()){
                            MenuButton menuBtn = new MenuButton("⋮");
                    
                        

                            MenuItem openItem = new MenuItem("⇲   Open…");
                            openItem.setOnAction(e->{
                                menuBtn.hide();
                                m_networksData.openNetwork(network.getNetworkId());
                            });

                            MenuItem removeItem = new MenuItem("🗑   Uninstall");
                            removeItem.setOnAction(e->{
                                menuBtn.hide();
                                m_networksData.removeNetwork(network.getNetworkId(), true);
                                
                            });

                            menuBtn.getItems().addAll(openItem, removeItem);

                    

                            HBox networkItemTopRow = new HBox(selectedBtn,marginRegion, networkImgView, nameText, growRegion, menuBtn);
                            HBox.setHgrow(networkItemTopRow, Priority.ALWAYS);
                            networkItemTopRow.setAlignment(Pos.CENTER_LEFT);
                            networkItemTopRow.setPadding(new Insets(2,0,2,0));


            

                            VBox networkItem = new VBox(networkItemTopRow);
                            networkItem.setFocusTraversable(true);
                            networkItem.setAlignment(Pos.CENTER_LEFT);
                            HBox.setHgrow(networkItem, Priority.ALWAYS);
                            networkItem.setId("rowBtn");
                            networkItem.setPadding(new Insets(2,5,2,5));

                            networkItemTopRow.setOnMouseClicked(e->{
                                if(e.getClickCount() == 2){
                                    openItem.fire();
                                }
                            });

                            m_listBox.getChildren().add(networkItem);

                        }
            
                    }
    
                }
            }else{
                
                BufferedButton emptyAddAppBtn = new BufferedButton(AppConstants.SETTINGS_ICON, 75);
                emptyAddAppBtn.setText("Install Network");
                emptyAddAppBtn.setContentDisplay(ContentDisplay.TOP);
                emptyAddAppBtn.setOnAction(e->{
                    m_installMenuBtn.show();
                });
                HBox addBtnBox = new HBox(emptyAddAppBtn);
                HBox.setHgrow(addBtnBox, Priority.ALWAYS);
                addBtnBox.setAlignment(Pos.CENTER);
                addBtnBox.setPrefHeight(300);
                m_listBox.getChildren().add(addBtnBox);
            
            }

        }

    }
}
