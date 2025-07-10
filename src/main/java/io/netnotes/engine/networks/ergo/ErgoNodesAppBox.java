package io.netnotes.engine.networks.ergo;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.ergoplatform.appkit.NetworkType;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netnotes.engine.AppBox;
import io.netnotes.engine.BufferedButton;
import io.netnotes.engine.JsonParametersBox;
import io.netnotes.engine.KeyMenu;
import io.netnotes.engine.KeyMenuItem;
import io.netnotes.engine.NamedNodeUrl;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.NoteMsgInterface;
import io.netnotes.engine.Stages;
import io.netnotes.engine.Utils;
import io.netnotes.engine.apps.AppConstants;
import io.netnotes.friendly_id.FriendlyId;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Alert.AlertType;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ErgoNodesAppBox extends AppBox {


    private Stage m_appStage;
    private SimpleObjectProperty<AppBox> m_currentBox = new SimpleObjectProperty<>();
    private NoteInterface m_ergoNetworkInterface = null;
    private VBox m_mainBox;

    private SimpleBooleanProperty m_showNodes = new SimpleBooleanProperty(false);

    private String m_locationId = null;

    private ErgoNodeClientControl m_nodeControlBox = null;

    private String m_nodeListenerId = null;
    private NetworksData m_networksData = null;

    

    private final String selectNodeMenu = "selectNodeMenu";

    private ErgoNetworkControl m_ergoNetworkControl = null;
    private SeparatorMenuItem m_nodeMenuCmdSeparator = null;
    private MenuButton nodeMenuBtn = null;


    private void addNode(){
        if(m_ergoNetworkControl != null){
            JsonObject networkObject = m_ergoNetworkControl.getNetworkObject();
            
            m_ergoNetworkControl.addRemoteNode(onExecuted->{}, onFailed->{});
            
        }
    };

    private void installNode(){


    

    };
    
    private void importNode(){

    }

    private void removeNodes(){

    }


    public void updateNodeMenu(){
        long timeStamp = System.currentTimeMillis();
        if(nodeMenuBtn == null){
            return;
        }
        ObservableList<MenuItem> nodeMenu = nodeMenuBtn.getItems();

        JsonObject nodeObject = m_ergoNetworkControl.getNodeObject();
        String nodeName = nodeObject != null ? NoteConstants.getJsonName(nodeObject) : null;
        
        String name = nodeName != null ? nodeName : "(select node)";
        nodeMenuBtn.setText(name);

        KeyMenu currentKeyMenu = KeyMenu.getKeyMenu(nodeMenuBtn.getItems(), selectNodeMenu);
        boolean isKeyMenu = currentKeyMenu != null;
        KeyMenu selectNodeMenuItem = isKeyMenu ? currentKeyMenu : new KeyMenu(selectNodeMenu, name, timeStamp, KeyMenu.VALUE_NOT_KEY);

        if(!isKeyMenu){
            nodeMenu.add(selectNodeMenuItem);
        }else{
            selectNodeMenuItem.setValue(name, timeStamp);
        }

        m_ergoNetworkControl.updateNodesMenu(selectNodeMenuItem.getItems(), timeStamp);
    
        if(m_nodeMenuCmdSeparator == null){
            m_nodeMenuCmdSeparator = new SeparatorMenuItem();
            nodeMenu.add(m_nodeMenuCmdSeparator);
        }
        m_ergoNetworkControl.updateNodeOptionsMenu(nodeMenu, timeStamp);
    }


    public ErgoNodesAppBox(Stage appStage, ErgoNetworkControl ergoNetworkControl){
        super();

        
   
        final String selectString = "[select]";

        ImageView nodeIconView = new ImageView(new Image(ErgoConstants.ERGO_NODES_ICON));
        nodeIconView.setPreserveRatio(true);
        nodeIconView.setFitHeight(18);

        HBox topIconBox = new HBox(nodeIconView);
        topIconBox.setAlignment(Pos.CENTER_LEFT);
        topIconBox.setMinWidth(30);


        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ImageView closeImage = Stages.highlightedImageView(Stages.closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);

        Button toggleShowNodes = new Button(m_showNodes.get() ? "⏷" : "⏵");
        toggleShowNodes.setId("caretBtn");
        toggleShowNodes.setOnAction(e->{
            if(m_ergoNetworkControl.getNodeId() == null){
                m_showNodes.set(false);
            }else{
                m_showNodes.set(!m_showNodes.get());
            }
          
        });
        MenuButton nodeOptionsBtn = new MenuButton("⋮");

        HBox nodeMenuBtnPadding = new HBox(nodeOptionsBtn);
        nodeMenuBtnPadding.setPadding(new Insets(0, 0, 0, 5));

        Text nodeTopLabel = new Text(String.format("%-13s","Node"));
        nodeTopLabel.setFont(Stages.txtFont);
        nodeTopLabel.setFill(Stages.txtColor);

        nodeMenuBtn = new MenuButton(selectString);
        nodeMenuBtn.setId("arrowMenuButton");

        Button disableNodeBtn = new Button("☓");
        disableNodeBtn.setId("lblBtn");

        disableNodeBtn.setOnAction(e -> {

            
       
        });


        HBox nodeFieldBox = new HBox(nodeMenuBtn);
        HBox.setHgrow(nodeFieldBox, Priority.ALWAYS);
        nodeFieldBox.setAlignment(Pos.CENTER_LEFT);
        nodeFieldBox.setId("bodyBox");
        nodeFieldBox.setPadding(new Insets(0, 1, 0, 0));
        nodeFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);

        nodeMenuBtn.prefWidthProperty().bind(nodeFieldBox.widthProperty().subtract(1));


        HBox nodeBtnBox = new HBox(nodeFieldBox, nodeMenuBtnPadding);
        nodeBtnBox.setPadding(new Insets(2, 2, 0, 5));
        HBox.setHgrow(nodeBtnBox, Priority.ALWAYS);

        VBox nodeBodyPaddingBox = new VBox();
        HBox.setHgrow(nodeBodyPaddingBox, Priority.ALWAYS);


        
        nodeMenuBtn.showingProperty().addListener((obs,oldval,newval)->updateNodeMenu());

        nodeOptionsBtn.setOnShowing(e->m_ergoNetworkControl.updateNodeOptionsMenu(nodeOptionsBtn.getItems())); 
        
    
        HBox nodesTopBar = new HBox(toggleShowNodes, topIconBox, nodeTopLabel, nodeBtnBox);
        nodesTopBar.setAlignment(Pos.CENTER_LEFT);
        nodesTopBar.setPadding(new Insets(2));

        VBox nodeLayoutBox = new VBox(nodesTopBar, nodeBodyPaddingBox);
        HBox.setHgrow(nodeLayoutBox, Priority.ALWAYS);
        nodeLayoutBox.setPadding(new Insets(0,0,0,0));

        Runnable updateShowNodes = ()->{
            boolean isShow = m_showNodes.get();

            toggleShowNodes.setText(isShow ? "⏷" : "⏵");

            if (isShow) {
                JsonObject nodeObject = m_ergoNetworkControl.getNodeObject();
                String ergoNodeId = NoteConstants.getJsonId(nodeObject);

                if (m_nodeControlBox == null && ergoNodeId != null) { 
                    JsonElement namedNodeElement = nodeObject != null ? nodeObject.get("namedNode") : null;

                    if(namedNodeElement != null && !namedNodeElement.isJsonNull() && namedNodeElement.isJsonObject()){
                        JsonElement clientTypeElement = nodeObject.get("clientType");
                
                        String clientType = clientTypeElement != null && !clientTypeElement.isJsonNull() ? clientTypeElement.getAsString() : ErgoConstants.REMOTE_NODE;
                
                        try {
                            NamedNodeUrl namedNodeUrl = new NamedNodeUrl(namedNodeElement.getAsJsonObject());
                        
                            switch(clientType){
                                case ErgoConstants.LOCAL_NODE:
                                                
                                    
                                    m_nodeControlBox = new ErgoNodeLocalControl(namedNodeUrl, clientType, m_nodeListenerId); 
                                break;
                                default:
                                    m_nodeControlBox = new ErgoNodeClientControl(namedNodeUrl, clientType, m_nodeListenerId);
                            
                            }

                            m_nodeControlBox.setPadding(new Insets(0,0,0,5));
                            HBox.setHgrow(m_nodeControlBox, Priority.ALWAYS);
                            nodeBodyPaddingBox.getChildren().add(m_nodeControlBox);
                        } catch (Exception e) {
                                    
                            try {
                                Files.writeString(AppConstants.LOG_FILE.toPath(),  e.toString() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            } catch (IOException e1) {

                            }
                            if(m_nodeControlBox != null){
                                m_nodeControlBox.shutdown();
                                nodeBodyPaddingBox.getChildren().clear();
                                m_nodeControlBox = null;
                            }
                        }
                        
                    }else{
                        if(m_nodeControlBox != null){
                            m_nodeControlBox.shutdown();
                            nodeBodyPaddingBox.getChildren().clear();
                            m_nodeControlBox = null;
                        }
                    }
                   
                    
                    
                }
            } else {
                if(m_nodeControlBox != null){
                    m_nodeControlBox.shutdown();
                    nodeBodyPaddingBox.getChildren().clear();
                    m_nodeControlBox = null;
                }
            }
            
        };


        updateShowNodes.run();
        
        m_showNodes.addListener((obs, oldval, newval) -> {
            updateShowNodes.run();
        });

               
        
        
        m_ergoNetworkControl.nodeObjectProperty().addListener((obs,oldval,newval)->{
            
            if(newval != null){
                if(!nodeFieldBox.getChildren().contains(disableNodeBtn)){
                    nodeFieldBox.getChildren().add(disableNodeBtn);
                }
            }else{
                nodeMenuBtn.setText(selectString);
                if(nodeFieldBox.getChildren().contains(disableNodeBtn)){
                    nodeFieldBox.getChildren().remove(disableNodeBtn);
                }
            }
            updateShowNodes.run();
           
        });
        

        m_mainBox = new VBox(nodeLayoutBox);
        m_mainBox.setPadding(new Insets(0));
        HBox.setHgrow(m_mainBox, Priority.ALWAYS);

        m_currentBox.addListener((obs, oldval, newval) -> {
            m_mainBox.getChildren().clear();
            if (newval != null) {
                m_mainBox.getChildren().add(newval);
            } else {
                m_mainBox.getChildren().add(nodeLayoutBox);
            }

        });


        
        getChildren().addAll(m_mainBox);
        setPadding(new Insets(0,0,5,0));
    }

    public NetworksData getNetworksData(){
        return m_networksData;
    }

    public ExecutorService getExecService(){
        return getNetworksData().getExecService();
    }        

    public class ErgoNodeClientControl extends AppBox{
        private Tooltip m_nodeControlIndicatorTooltip = new Tooltip();
        private Label m_nodeControlIndicator = new Label("⬤");
        private Text m_nodeControlClientTypeText = new Text("");
        private TextField m_nodeControlLabelField = new TextField();
        private Label m_nodeControlShowBtn = new Label("⏵");
        private SimpleBooleanProperty m_showSubControl = new SimpleBooleanProperty(false);
        private JsonParametersBox m_paramsBox = null;
        private Label m_connectBtn = new Label("🗘");
        private VBox m_propertiesBox;
        private HBox m_headingBtnBox;
        private HBox m_statusPaddingBox;
        private Tooltip m_connectBtnTooltip;

        public HBox getStatusPaddingBox() {
            return m_statusPaddingBox;
        } 
        
        public Tooltip getConnectBtnTooltip() {
            return m_connectBtnTooltip;
        }


        public HBox getHeadingBtnBox() {
            return m_headingBtnBox;
        }




        public VBox getTopPropertiesBox() {
            return m_propertiesBox;
        }







        public Tooltip getNodeControlIndicatorTooltip() {
            return m_nodeControlIndicatorTooltip;
        }




        public Label getNodeControlIndicator() {
            return m_nodeControlIndicator;
        }


        public Text getNodeControlClientTypeText() {
            return m_nodeControlClientTypeText;
        }


        public TextField getNodeControlLabelField() {
            return m_nodeControlLabelField;
        }


        public Label getNodeControlShowBtn() {
            return m_nodeControlShowBtn;
        }



        public SimpleBooleanProperty getShowSubControl() {
            return m_showSubControl;
        }



        public JsonParametersBox getParamsBox() {
            return m_paramsBox;
        }




        public Label getConnectBtn() {
            return m_connectBtn;
        }



        

        @Override
        public void shutdown() {
        
            if (m_paramsBox != null) {
            
                m_paramsBox.shutdown();
            
                m_propertiesBox.getChildren().remove(m_paramsBox);
                m_paramsBox = null;
            
            }
        }


        public void getStatus(){
            JsonObject note = NoteConstants.getCmdObject("getStatus");
            note.addProperty("locationId", m_locationId); 
            note.addProperty("networkId", ErgoConstants.NODE_NETWORK);

            //m_nodeInterface
            m_ergoNetworkInterface.sendNote(note, (onSucceeded)->{
                Object onStatusObj = onSucceeded.getSource().getValue();
                JsonObject statusObject = onStatusObj != null && onStatusObj instanceof JsonObject ? (JsonObject) onStatusObj : null;
                JsonElement namedNodeElement = statusObject != null ? statusObject.get("namedNode") : null;
                NamedNodeUrl namedNode = null;
                try {
                    namedNode = namedNodeElement != null && !namedNodeElement.isJsonNull() && namedNodeElement.isJsonObject() ? (new NamedNodeUrl(namedNodeElement.getAsJsonObject())) : null;
                } catch (Exception e) {

                }
                if(namedNode != null){
                
                    m_nodeControlLabelField.setText(namedNode != null ? namedNode.getUrlString() : "Setup required");
                    m_nodeControlIndicator.setId("lblGrey");

                    JsonElement syncedElement = statusObject.get("synced");
                    boolean synced = syncedElement != null ? syncedElement.getAsBoolean() : false;
                    if(m_paramsBox != null){
                        m_paramsBox.update(statusObject);
                    };
                    
                    
                    m_nodeControlIndicatorTooltip.setText(synced ? "Synced" : "Unsynced");
                    m_nodeControlIndicator.setId(synced ? "lblGreen" : "lblGrey");
                    m_nodeControlLabelField.setId("smallPrimaryColor");
                        
                }else{
                    m_showSubControl.set(false);

                    m_nodeControlIndicatorTooltip.setText( "Unavailable");
                    m_nodeControlIndicator.setId("lblBlack");
                    m_nodeControlLabelField.setId("smallSecondaryColor");
                }
                
            }, (onFailed)->{
                m_showSubControl.set(false);
                m_nodeControlIndicator.setId("lblBlack");
                m_nodeControlLabelField.setId("smallSecondaryColor");
                m_nodeControlIndicatorTooltip.setText("Error: " + onFailed.getSource().getException().toString());
            });

        }

        

        public ErgoNodeClientControl( NamedNodeUrl namedNodeUrl, String clientType, String accessId){
            super();
            m_connectBtnTooltip = new Tooltip("Refresh");
            m_connectBtnTooltip.setShowDelay(Duration.millis(100));

        

            m_nodeControlShowBtn.setId("caretBtn");
            m_nodeControlShowBtn.setMinWidth(25);
            
            m_nodeControlShowBtn.setOnMouseClicked(e->{
            
                m_showSubControl.set(!m_showSubControl.get());
                
            });


            m_nodeControlIndicatorTooltip.setShowDelay(new Duration(100));
            m_nodeControlIndicatorTooltip.setText("Unavailable");

            m_nodeControlIndicator.setTooltip(m_nodeControlIndicatorTooltip);
            m_nodeControlIndicator.setId("lblBlack");
            m_nodeControlIndicator.setPadding(new Insets(0,0,3,0));


            m_nodeControlLabelField.setText(namedNodeUrl.getUrlString());
            m_nodeControlLabelField.setEditable(false);
            m_nodeControlLabelField.setId("smallSecondaryColor");
            m_nodeControlLabelField.setPadding(new Insets(0,10,0,0));
            HBox.setHgrow(m_nodeControlLabelField,Priority.ALWAYS);
        
            Region nodeControlGrowRegion = new Region();
            HBox.setHgrow(nodeControlGrowRegion, Priority.ALWAYS);

            m_nodeControlClientTypeText.setText(clientType);
            m_nodeControlClientTypeText.setFont(Stages.txtFont);
            m_nodeControlClientTypeText.setFill(Stages.txtColor);

            
            
            m_connectBtn.setId("lblBtn");
            m_connectBtn.setOnMouseClicked(e->getStatus());
            m_connectBtn.setTooltip(getConnectBtnTooltip());

            m_headingBtnBox = new HBox(m_connectBtn);
            m_headingBtnBox.setPadding(new Insets(0, 5, 0, 5));
            m_headingBtnBox.setAlignment(Pos.CENTER);

            m_statusPaddingBox = new HBox(m_nodeControlLabelField, m_nodeControlIndicator);
            m_statusPaddingBox.setAlignment(Pos.CENTER_LEFT);
            m_statusPaddingBox.setId("bodyBox");
            m_statusPaddingBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);
            m_statusPaddingBox.setPadding(new Insets(2,5,2,0));

            HBox.setHgrow(m_statusPaddingBox, Priority.ALWAYS);

            Region clientTypeTextSpacer = new Region();
            clientTypeTextSpacer.setMinWidth(10);
            
            HBox topBox = new HBox(m_nodeControlShowBtn, m_nodeControlClientTypeText, clientTypeTextSpacer, m_statusPaddingBox, m_headingBtnBox);
            topBox.setAlignment(Pos.CENTER_LEFT);
            topBox.setMinHeight(Stages.ROW_HEIGHT);
            HBox.setHgrow(topBox, Priority.ALWAYS);



        

            m_propertiesBox = new VBox();
            getChildren().addAll(topBox, m_propertiesBox);


            m_showSubControl.addListener((obs,oldval,newval)->{
                m_nodeControlShowBtn.setText(newval ? "⏷" : "⏵");
                
                if (newval) {
                    if(m_paramsBox == null){
                        m_paramsBox = new JsonParametersBox(NoteConstants.getJsonObject("Status", "Updating..."), 230);
                        m_paramsBox.setPadding(new Insets(5,10,0,5));
        
                        m_propertiesBox.getChildren().add(m_paramsBox);
                        getStatus();
                    }
                } else {
                
                shutdown();
            
                }
            });

        

            getStatus();
        }

        
        
        }

    public class ErgoNodeLocalControl extends ErgoNodeClientControl {

        private BufferedButton m_updateBtn;
        private VBox m_topPropertiesBox = null;
        private Tooltip m_updateTooltip = new Tooltip("Update"); 
        private ProgressBar m_progressBar = null;

        public BufferedButton getUpdateBtn() {
            return m_updateBtn;
        }

        public VBox getTopPropertiesBox() {
            return m_topPropertiesBox;
        }

        public ProgressBar getProgressBar() {
            return m_progressBar;
        }

        public ErgoNodeLocalControl(NamedNodeUrl nodeUrl, String clientType, String accessId){
            super( nodeUrl,clientType, accessId);

            getConnectBtn().setText(Stages.PLAY);
            getConnectBtn().setOnMouseClicked(e->{
                if(getConnectBtn().getText().equals(Stages.STOP)){
                    m_ergoNetworkControl.terminate();
                }else{
                    m_ergoNetworkControl.run();
                }
            });

            m_topPropertiesBox = new VBox();
            HBox.setHgrow(m_topPropertiesBox, Priority.ALWAYS);
            m_updateTooltip.setShowDelay(Duration.millis(100));

            m_updateBtn = new BufferedButton(AppConstants.CLOUD_ICON, Stages.MENU_BAR_IMAGE_WIDTH);
            m_updateBtn.setOnAction(e->m_ergoNetworkControl.updateApp((onSucceed)->{}, onFailed->{}));
            m_updateBtn.setTooltip(m_updateTooltip);

            getStatusPaddingBox().getChildren().add(m_updateBtn);
        
            getChildren().add(1, m_topPropertiesBox);
        }

        @Override
        public void getStatus(){
        
            m_ergoNetworkControl.getStatus(onSucceeded->{
                Object obj = onSucceeded.getSource().getValue();
                if(obj != null && obj instanceof  JsonObject ){

                    updateStatus((JsonObject) obj);
                        
                }else{

                    getShowSubControl().set(false);

                    getNodeControlIndicatorTooltip().setText("Unavailable");
                    getNodeControlIndicator().setId("lblBlack");
                    getNodeControlLabelField().setId("smallSecondaryColor");
                    updateParamsBox(null);
                }
            }, onFailed->{
                getShowSubControl().set(false);

                getNodeControlIndicatorTooltip().setText("Unavailable");
                getNodeControlIndicator().setId("lblBlack");
                getNodeControlLabelField().setId("smallSecondaryColor");
                updateParamsBox(null);
            });
                
            
        }

        public void updateParamsBox(JsonObject json){
            if(getParamsBox() != null){

                getParamsBox().update(json);
            }
        }
        

        public void updateProgressBar(JsonObject statusObject){
        

            JsonElement networkBlockHeightElement = statusObject != null ? statusObject.get("maxPeerHeight") : null;
            JsonElement nodeBlockHeightElement = statusObject != null ? statusObject.get("fullHeight") : null;
            JsonElement headersBlockHeightElement = statusObject != null ? statusObject.get("headersHeight") : null;

            
        
            BigDecimal networkBlockHeight = networkBlockHeightElement != null && !networkBlockHeightElement.isJsonNull() ?  networkBlockHeightElement.getAsBigDecimal() : null;
            BigDecimal headersBlockHeight = headersBlockHeightElement != null && !headersBlockHeightElement.isJsonNull() ? headersBlockHeightElement.getAsBigDecimal() : null;
            BigDecimal nodeBlockHeight = nodeBlockHeightElement != null && !nodeBlockHeightElement.isJsonNull() ?  nodeBlockHeightElement.getAsBigDecimal() : null;
            


            networkBlockHeight = networkBlockHeight != null ? networkBlockHeight.multiply(BigDecimal.valueOf(2)) : null;
            
            boolean isNetworkBlockHeight = networkBlockHeight != null && networkBlockHeight.compareTo(BigDecimal.ZERO) > 0;
            boolean isHeadersBlockHeight = headersBlockHeight != null && headersBlockHeight.compareTo(BigDecimal.ZERO) > 0;
            boolean isNodeBlockHeight = nodeBlockHeight != null && nodeBlockHeight.compareTo(BigDecimal.ZERO) > 0;


            if(isNetworkBlockHeight && isHeadersBlockHeight){

                BigDecimal progressHeight = headersBlockHeight.add( isNodeBlockHeight? nodeBlockHeight : BigDecimal.ZERO);
                // double progressPercent = progressHeight.divide(networkBlockHeight) .doubleValue();
                BigDecimal progress = BigDecimal.ZERO;
                try {
                    progress = progressHeight.divide(networkBlockHeight, 5, RoundingMode.HALF_UP);
                
                    m_progressBar.setProgress(progress.doubleValue());
                
                } catch ( ArithmeticException e) {
                    m_progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                }

            
            }else{
                
                m_progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            } 
            
        }

        public void updateStatus(JsonObject statusObject){
            if(m_topPropertiesBox == null){
                return;
            }
            JsonElement statusCodeElement = statusObject != null ? statusObject.get("statusCode") : null;

            if(statusCodeElement != null && statusCodeElement.isJsonPrimitive()){
                JsonElement syncedElement = statusObject.get("synced");
                JsonElement statusElement = statusObject.get("status");

                boolean synced = syncedElement != null ? syncedElement.getAsBoolean() : false;

                String lblColor = "smallSecondaryColor";
                String indicatorColor = "lblBlack";
                String indicatorString = "Unavailable";
            
                int statusCode = statusCodeElement.getAsInt();
                String msgString = statusElement != null ? statusElement.getAsString() : indicatorString;

            

                if(!synced && statusCode != NoteConstants.STOPPED && statusCode != NoteConstants.UPDATING){
                    if(m_progressBar == null){
                        m_progressBar = new ProgressBar();
                    
                        m_progressBar.prefWidthProperty().bind(this.m_topPropertiesBox.widthProperty().subtract(10));
                        m_topPropertiesBox.getChildren().add(m_progressBar);
                        
                    }
                    JsonElement infoElement = statusObject.get("information");
    
                    if(infoElement != null && infoElement.isJsonObject()){
                        updateProgressBar(infoElement.getAsJsonObject());
                    }
                    
                }else{
                    if(m_progressBar != null){
                        m_topPropertiesBox.getChildren().remove(m_progressBar);
                        m_progressBar = null;
                
                    }

                }
            
                switch(statusCode){
                    case NoteConstants.STARTED:
                        lblColor = "smallPrimaryColor";
                        indicatorColor = synced ? "lblGreen" : "lblWhite";
                        indicatorString = "Running";
                        updateParamsBox(statusObject);
                    
                        if( !getConnectBtn().getText().equals(Stages.STOP)){
                            getConnectBtn().setText(Stages.STOP);
                            getConnectBtnTooltip().setText("Stop");
                        }
                    break;
                    case NoteConstants.STARTING:
                        lblColor = "smallSecondaryColor";
                        indicatorColor = "lblGrey";
                        indicatorString = "Starting";
                        updateParamsBox(statusObject);
                        if( !getConnectBtn().getText().equals(Stages.STOP)){
                            getConnectBtn().setText(Stages.STOP);
                            getConnectBtnTooltip().setText("Stop");
                        }
                    break;
                    case NoteConstants.UPDATING:
                        
                        lblColor = "smallSecondaryColor";
                        indicatorColor = "lblYellow";
                        indicatorString = "Updating";

                        if( !getConnectBtn().getText().equals(Stages.STOP)){
                            getConnectBtn().setText(Stages.STOP);
                            getConnectBtnTooltip().setText("Stop");
                        }
                        updateParamsBox(statusObject);
                    break;
                    case NoteConstants.SUCCESS:
                    case NoteConstants.STOPPED:
                        lblColor = "smallSecondaryColor";
                        indicatorColor = "lblBlack";
                        indicatorString = "Stopped";
                        if(!getConnectBtn().getText().equals(Stages.PLAY)){
                            getConnectBtn().setText(Stages.PLAY);
                            getConnectBtnTooltip().setText("Start");
                        }
                        updateParamsBox(statusObject);
                    break;
                    default:
                        if( !getConnectBtn().getText().equals(Stages.STOP)){
                            getConnectBtn().setText(Stages.STOP);
                            getConnectBtnTooltip().setText("Stop");
                        }
                        updateParamsBox(null);
                }
                
                getNodeControlLabelField().setText(msgString);
                getNodeControlIndicatorTooltip().setText(indicatorString);
                getNodeControlIndicator().setId(indicatorColor);
                getNodeControlLabelField().setId(lblColor);
                
            
                
            }else{
                getShowSubControl().set(false);
                getNodeControlIndicatorTooltip().setText("Unavailable");
                getNodeControlIndicator().setId("lblBlack");
                getNodeControlLabelField().setId("smallSecondaryColor");
                updateParamsBox(null);
            }
        }


        


        @Override
        public void shutdown(){
            super.shutdown();
        }

        public void updateError(JsonObject json){
            JsonElement msgElement = json != null ? json.get("status") : null;
            String errorString = msgElement != null ? msgElement.getAsString() : "Code 0";
            getNodeControlLabelField().setText("Error: " + errorString);
            
        }
        JsonParser m_jsonParser = new JsonParser();

        @Override
        public void sendMessage(int code, long timestamp, String networkId, String msg) {
        
            JsonElement msgElement = msg != null ? m_jsonParser.parse(msg) : null;
            JsonObject dataObject = msgElement != null && msgElement.isJsonObject() ? msgElement.getAsJsonObject() : null;

            switch(code){
                case NoteConstants.ERROR:
                    updateError(dataObject != null  ? dataObject : null);       
                break;
                case NoteConstants.STATUS:
                
                    updateStatus(dataObject != null  ? dataObject : null);
                break;
            }
            
        }

        @Override
        public void sendMessage(int code, long timestamp,String networkId, Number number) {
            switch(code){
                case 100:
                    if(!getNodeControlIndicatorTooltip().getText().equals("Updating")){
                        getNodeControlIndicatorTooltip().setText("Updating");
                        
                        getNodeControlIndicator().setId("lblYellow");
                        getNodeControlLabelField().setId("smallSecondaryColor");
                        getStatus();
                        
                    }
                    getNodeControlLabelField().setText("Updating: " + String.format("%.1f", number.doubleValue() * 100) + "%");
                break;
            }

        }
        
    }

}


    /*
    
            if (m_settingsStage == null) {

                m_settingsStage = new Stage();
                Runnable close = () -> {
                    if(m_settingsStage != null){
                        m_settingsStage.close();
                        m_settingsStage = null;
                    }
                };
                m_settingsStage.getIcons().add(getIcon());
                m_settingsStage.setResizable(false);
                m_settingsStage.initStyle(StageStyle.UNDECORATED);

                
                SimpleDoubleProperty rowHeight = new SimpleDoubleProperty(40);

                NamedNodeUrl namedNode = m_namedNodeUrlProperty.get();
                SimpleObjectProperty<NetworkType> networkTypeOption = new SimpleObjectProperty<NetworkType>(namedNode.getNetworkType());
                Button closeBtn = new Button();



                HBox titleBox = Stages.createTopBar(m_ergoNodesList.getErgoNodes().getSmallAppIcon(), "Edit - Remote Node Config - Ergo Nodes", closeBtn, m_settingsStage);
                
                Text headingText = new Text("Node Config");
                headingText.setFont(Stages.txtFont);
                headingText.setFill(Color.WHITE);
    
                HBox headingBox = new HBox(headingText);
                headingBox.prefHeight(40);
                headingBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(headingBox, Priority.ALWAYS);
                headingBox.setPadding(new Insets(10, 10, 10, 10));
                headingBox.setId("headingBox");
    
                HBox headingPaddingBox = new HBox(headingBox);
    
                headingPaddingBox.setPadding(new Insets(5, 0, 2, 0));
    
                VBox headerBox = new VBox(titleBox, headingPaddingBox);
    
                headerBox.setPadding(new Insets(0, 5, 0, 5));


                Text nodeName = new Text(String.format("%-13s", "Name"));
                nodeName.setFill(Stages.txtColor);
                nodeName.setFont(Stages.txtFont);

                TextField nodeNameField = new TextField(namedNode.getName());
                nodeNameField.setFont(Stages.txtFont);
                nodeNameField.setId("formField");
                HBox.setHgrow(nodeNameField, Priority.ALWAYS);

                HBox nodeNameBox = new HBox(nodeName, nodeNameField);
                nodeNameBox.setAlignment(Pos.CENTER_LEFT);
                nodeNameBox.setMinHeight(rowHeight);

                Text networkTypeText = new Text(String.format("%-13s", "Network Type"));
                networkTypeText.setFill(Stages.txtColor);
                networkTypeText.setFont(Stages.txtFont);

                MenuButton networkTypeBtn = new MenuButton(namedNode.getNetworkType().toString());
                networkTypeBtn.setFont(Stages.txtFont);
                networkTypeBtn.setId("formField");
                networkTypeBtn.setUserData(namedNode.getNetworkType());
                HBox.setHgrow(networkTypeBtn, Priority.ALWAYS);

                MenuItem mainnetItem = new MenuItem(NetworkType.MAINNET.toString());
                mainnetItem.setId("rowBtn");
        
                MenuItem testnetItem = new MenuItem(NetworkType.TESTNET.toString());
                testnetItem.setId("rowBtn");
        
                networkTypeBtn.getItems().addAll(mainnetItem, testnetItem);

                HBox networkTypeBox = new HBox(networkTypeText, networkTypeBtn);
                networkTypeBox.setAlignment(Pos.CENTER_LEFT);
                networkTypeBox.setMinHeight(rowHeight);

                    
                Text apiKeyText = new Text(String.format("%-14s", "API Key"));
                apiKeyText.setFill(getPrimaryColor());
                apiKeyText.setFont((Stages.txtFont));

                TextField apiKeyField = new TextField(namedNode.getApiKey());
                apiKeyField.setId("formField");
                HBox.setHgrow(apiKeyField, Priority.ALWAYS);

                Button showKeyBtn = new Button("(Click to view)");
                showKeyBtn.setId("rowBtn");
                showKeyBtn.setPrefWidth(250);
                showKeyBtn.setPrefHeight(30);
                showKeyBtn.setAlignment(Pos.CENTER_LEFT);


                Runnable updateKey = ()->{
                    String keyString = apiKeyField.getText();

                        try {

                            NamedNodeUrl newNamedNodeUrl = getNamedNodeUrl();
                            newNamedNodeUrl.setApiKey(keyString);
                            setNamedNodeUrl(newNamedNodeUrl);
                            
                        } catch (Exception e1) {
                    
                        }
                };

                Tooltip randomApiKeyTip = new Tooltip("Random API Key");

                BufferedButton hideKeyBtn = new BufferedButton("/assets/eye-off-30.png", Stages.MENU_BAR_IMAGE_WIDTH);
                BufferedButton saveKeyBtn = new BufferedButton("/assets/save-30.png", Stages.MENU_BAR_IMAGE_WIDTH);
                BufferedButton randomApiKeyBtn = new BufferedButton("/assets/d6-30.png", Stages.MENU_BAR_IMAGE_WIDTH);

            

                randomApiKeyBtn.setTooltip(randomApiKeyTip);
                randomApiKeyBtn.setOnAction(e -> {
                    try {
                        int length = Utils.getRandomInt(12, 20);
                        char key[] = new char[length];
                        for (int i = 0; i < length; i++) {
                            key[i] = (char) Utils.getRandomInt(33, 126);
                        }
                        String keyString = new String(key);
                        apiKeyField.setText(keyString);
                    
                    } catch (NoSuchAlgorithmException e1) {
                        Alert a = new Alert(AlertType.NONE, e1.toString(), ButtonType.CANCEL);
                        a.initOwner(m_settingsStage);
                        a.setHeaderText("Error");
                        a.setTitle("Error");
                        a.show();
                    }
                });

                HBox apiKeyBox = new HBox(apiKeyText, showKeyBtn);
                apiKeyBox.setPadding(new Insets(0));;
                apiKeyBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(apiKeyBox, Priority.ALWAYS);

            
                Runnable hideKey = ()->{
                
                    apiKeyBox.getChildren().removeAll(apiKeyField, hideKeyBtn, randomApiKeyBtn, saveKeyBtn);
            
                    apiKeyBox.getChildren().add(showKeyBtn);
                
                };

                Runnable showKey = ()->{
                    apiKeyField.setText(namedNodeUrlProperty().get().getApiKey());
                    apiKeyBox.getChildren().remove(showKeyBtn);
                    apiKeyBox.getChildren().addAll(apiKeyField, hideKeyBtn, randomApiKeyBtn, saveKeyBtn);
                };

                hideKeyBtn.setOnAction(e->{
                    hideKey.run();
                });
            
                saveKeyBtn.setOnAction(e->{
                    updateKey.run();
                    hideKey.run();
                });

                showKeyBtn.setOnAction(e ->{
                    getNetworksData().verifyAppKey(()->{
                        showKey.run();
                    });
                    
                });

                Text nodePortText = new Text(String.format("%-13s", "Port"));
                nodePortText.setFill(Stages.txtColor);
                nodePortText.setFont(Stages.txtFont);

                TextField nodePortField = new TextField("9053");
                nodePortField.setId("formField");
                HBox.setHgrow(nodePortField, Priority.ALWAYS);

                nodePortField.textProperty().addListener((obs, oldval, newVal) -> {

                    if (!newVal.matches("\\d*")) {
                        newVal = newVal.replaceAll("[^\\d]", "");

                    }
                    int intVal = Integer.parseInt(newVal);

                    if (intVal > 65535) {
                        intVal = 65535;
                    }

                    nodePortField.setText(intVal + "");

                });

                nodePortField.focusedProperty().addListener((obs, oldval, newVal) -> {
                    if (!newVal) {
                        String portString = nodePortField.getText();
                        int intVal = Integer.parseInt(portString);

                        if (intVal < 1025) {
                            if (networkTypeOption.get().equals(NetworkType.TESTNET)) {
                                nodePortField.setText(ErgoNodes.TESTNET_PORT + "");
                            } else {
                                nodePortField.setText(ErgoNodes.MAINNET_PORT + "");
                            }

                            Alert portSmallAlert = new Alert(AlertType.NONE, "The minimum port value which may be assigned is: 1025\n\n(Default value used.)", ButtonType.CLOSE);
                            portSmallAlert.initOwner(m_settingsStage);
                            portSmallAlert.setHeaderText("Invalid Port");
                            portSmallAlert.setTitle("Invalid Port");
                            portSmallAlert.show();
                        }

                    }
                });

                HBox nodePortBox = new HBox(nodePortText, nodePortField);
                nodePortBox.setAlignment(Pos.CENTER_LEFT);
                nodePortBox.setMinHeight(rowHeight);

                testnetItem.setOnAction((e) -> {
                    networkTypeBtn.setText(testnetItem.getText());
                    networkTypeOption.set(NetworkType.TESTNET);
                    int portValue = Integer.parseInt(nodePortField.getText());
                    if (portValue == ErgoNodes.MAINNET_PORT) {
                        nodePortField.setText(ErgoNodes.TESTNET_PORT + "");
                    }
                });

                mainnetItem.setOnAction((e) -> {
                    networkTypeBtn.setText(mainnetItem.getText());
                    networkTypeOption.set(NetworkType.MAINNET);

                    int portValue = Integer.parseInt(nodePortField.getText());
                    if (portValue == ErgoNodes.TESTNET_PORT) {
                        nodePortField.setText(ErgoNodes.MAINNET_PORT + "");
                    }

                });

                Text nodeUrlText = new Text(String.format("%-13s", "IP"));
                nodeUrlText.setFill(Stages.txtColor);
                nodeUrlText.setFont(Stages.txtFont);

                TextField nodeUrlField = new TextField(namedNode.getIP());
                nodeUrlField.setFont(Stages.txtFont);
                nodeUrlField.setId("formField");
                HBox.setHgrow(nodeUrlField, Priority.ALWAYS);

                HBox nodeUrlBox = new HBox(nodeUrlText, nodeUrlField);
                nodeUrlBox.setAlignment(Pos.CENTER_LEFT);
                nodeUrlBox.setMinHeight(rowHeight);

                Region urlSpaceRegion = new Region();
                urlSpaceRegion.setMinHeight(40);

                Button okButton = new Button("Save");
                okButton.setPrefWidth(100);

                HBox okBox = new HBox(okButton);
                okBox.setAlignment(Pos.CENTER_RIGHT);
                HBox.setHgrow(okBox,Priority.ALWAYS);
                okBox.setPadding(new Insets(10));

                VBox customClientOptionsBox = new VBox(nodeNameBox, networkTypeBox, nodeUrlBox, nodePortBox, apiKeyBox);
                customClientOptionsBox.setPadding(new Insets(15));
                customClientOptionsBox.setId("bodyBox");


                VBox bodyBox = new VBox(customClientOptionsBox, okBox);
                bodyBox.setPadding(new Insets(5));
                bodyBox.setId("bodyBox");
                HBox.setHgrow(bodyBox, Priority.ALWAYS);

                VBox bodyPaddingBox = new VBox(bodyBox);
                bodyPaddingBox.setPadding(new Insets(0,5,5,5));

                Runnable onClose = () ->{
                    if(m_settingsStage != null){
                        m_settingsStage.close();
                        m_settingsStage = null;
                    }
                };

                okButton.setOnAction(e->{
                    try {

                        NamedNodeUrl newNamedNodeUrl = getNamedNodeUrl();
                        newNamedNodeUrl.setName( nodeNameField.getText());
                        newNamedNodeUrl.setIp(nodeUrlField.getText());
                        newNamedNodeUrl.setPort(Integer.parseInt(nodePortField.getText()));
                        newNamedNodeUrl.setApiKey(apiKeyField.getText());
                        newNamedNodeUrl.setNetworkType(networkTypeOption.get());
                        setNamedNodeUrl(newNamedNodeUrl);
                        
                    } catch (Exception e1) {
                
                    }


                    m_ergoNodesList.save();
                    onClose.run();
                });

                closeBtn.setOnAction(e->{
                    onClose.run();
                });

                m_settingsStage.setOnCloseRequest(e->onClose.run());

                VBox layoutBox = new VBox(headerBox, bodyPaddingBox);

                Scene scene = new Scene(layoutBox, SETUP_STAGE_WIDTH, 350);
                scene.setFill(null);
                scene.getStylesheets().add("/css/startWindow.css");

                m_settingsStage.setScene(scene);
                m_settingsStage.setOnCloseRequest(e -> close.run());
                m_settingsStage.show();
          
           
            } else {
                if (m_settingsStage.isIconified()) {
                    m_settingsStage.setIconified(false);
                }
                if(!m_settingsStage.isShowing()){
                    m_settingsStage.show();
                }else{
                    Platform.runLater(()->m_settingsStage.toBack());
                    Platform.runLater(()->m_settingsStage.toFront());
                }
                
            }

    public void showAddNodeStage() {
        if (m_addStage == null) {
            String friendlyId = FriendlyId.createFriendlyId();

            SimpleStringProperty nodeOption = new SimpleStringProperty(m_ergoLocalNode == null ? ErgoNodeLocalData.LOCAL_NODE : PUBLIC);

            // Alert a = new Alert(AlertType.NONE, "updates: " + updatesEnabled, ButtonType.CLOSE);
            //a.show();
          
            //private
            SimpleObjectProperty<NetworkType> networkTypeOption = new SimpleObjectProperty<NetworkType>(NetworkType.MAINNET);
            SimpleStringProperty clientTypeOption = new SimpleStringProperty(ErgoNodeData.LIGHT_CLIENT);

            Image icon = new Image(ErgoNodes.getSmallAppIconString());
            String name = m_ergoNodes.getName();

            VBox layoutBox = new VBox();

            m_addStage = new Stage();
            m_addStage.getIcons().add(icon);
            m_addStage.setResizable(false);
            m_addStage.initStyle(StageStyle.UNDECORATED);

            double minWidth = 600;
            double minHeight = 500;

            Scene addNodeScene = new Scene(layoutBox, m_addStageWidth, m_addStageHeight);
            addNodeScene.setFill(null);
            String heading = "Add Node";
            Button closeBtn = new Button();

            String titleString = heading + " - " + name;
            m_addStage.setTitle(titleString);

            Button maximizeBtn = new Button();

            HBox titleBox = Stages.createTopBar(icon, maximizeBtn, closeBtn, m_addStage);
            Text headingText = new Text(heading);
            headingText.setFont(Stages.txtFont);
            headingText.setFill(Color.WHITE);

            HBox headingBox = new HBox(headingText);
            headingBox.prefHeight(40);
            headingBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(headingBox, Priority.ALWAYS);
            headingBox.setPadding(new Insets(10, 10, 10, 10));
            headingBox.setId("headingBox");

            HBox headingPaddingBox = new HBox(headingBox);

            headingPaddingBox.setPadding(new Insets(5, 0, 2, 0));

            VBox headerBox = new VBox(titleBox, headingPaddingBox);

            headerBox.setPadding(new Insets(0, 5, 0, 5));

            SimpleDoubleProperty rowHeight = new SimpleDoubleProperty(40);

            Text nodeTypeText = new Text("Type ");
            nodeTypeText.setFill(Stages.txtColor);
            nodeTypeText.setFont(Stages.txtFont);

            MenuButton typeBtn = new MenuButton();
            typeBtn.setId("bodyRowBox");
            typeBtn.setMinWidth(300);
            typeBtn.setAlignment(Pos.CENTER_LEFT);

            MenuItem defaultClientItem = new MenuItem("Public node (Remote client)");
            defaultClientItem.setOnAction((e) -> {

                nodeOption.set(PUBLIC);

            });
            defaultClientItem.setId("rowBtn");

            MenuItem configureItem = new MenuItem("Custom (Remote client)");
            configureItem.setOnAction((e) -> {

                nodeOption.set(CUSTOM);

            });
            configureItem.setId("rowBtn");

            MenuItem localNodeItem = new MenuItem("Local Node (Local host)");
            localNodeItem.setOnAction(e->{});
            localNodeItem.setId("rowBtn");
            localNodeItem.setOnAction(e->{
                nodeOption.set(ErgoNodeLocalData.LOCAL_NODE);
            });
            Runnable addLocalNodeOption = ()->{
                if(m_ergoLocalNode == null){
                    if(!typeBtn.getItems().contains(localNodeItem)){
                        typeBtn.getItems().add(0,localNodeItem);
                    }
                }else{
                    if(typeBtn.getItems().contains(localNodeItem)){
                        typeBtn.getItems().remove(localNodeItem);
                    }
                }
            };
            addLocalNodeOption.run();

            m_doGridUpdate.addListener((obs,oldval,newval)->addLocalNodeOption.run());            

            typeBtn.getItems().addAll(defaultClientItem, configureItem);

            Text publicNodesText = new Text("Public Nodes");
            publicNodesText.setFill(Stages.txtColor);
            publicNodesText.setFont(Stages.txtFont);

            Tooltip enableUpdatesTip = new Tooltip("Update");
            enableUpdatesTip.setShowDelay(new javafx.util.Duration(100));

            BufferedButton getNodesListBtn = new BufferedButton(getDownloadImgUrl(), 30);
            getNodesListBtn.setTooltip(enableUpdatesTip);
            final String updateEffectId = "UPDATE_DISABLED";
            Runnable updateEnableEffect = () -> {
                boolean updatesEnabled = false;

                enableUpdatesTip.setText("Updates settings: " + (updatesEnabled ? "Enabled" : "Disabled"));
                if (!updatesEnabled) {
                    if (getNodesListBtn.getBufferedImageView().getEffect(updateEffectId) == null) {
                        getNodesListBtn.getBufferedImageView().applyEffect(new InvertEffect(updateEffectId, 0.7));
                    }
                } else {
                    getNodesListBtn.getBufferedImageView().removeEffect(updateEffectId);
                }
            };

            getNodesListBtn.setOnAction((e) -> {
                getNodesListUpdate();
            });

        

            updateEnableEffect.run();
            Region btnSpacerRegion = new Region();
            HBox.setHgrow(btnSpacerRegion, Priority.ALWAYS);

            HBox publicNodesBox = new HBox(publicNodesText, btnSpacerRegion, getNodesListBtn);
            publicNodesBox.setAlignment(Pos.CENTER_LEFT);
            publicNodesBox.setMinHeight(40);
          

            HBox nodeTypeBox = new HBox(nodeTypeText, typeBtn);
            // Binding<Double> viewportWidth = Bindings.createObjectBinding(()->settingsScroll.viewportBoundsProperty().get().getWidth(), settingsScroll.viewportBoundsProperty());


            typeBtn.minWidthProperty().bind(nodeTypeBox.widthProperty().subtract(nodeTypeText.layoutBoundsProperty().get().getWidth()).subtract(5));
            nodeTypeBox.setAlignment(Pos.CENTER_LEFT);
            nodeTypeBox.setPadding(new Insets(0));
            nodeTypeBox.setMinHeight(rowHeight);
            HBox.setHgrow(nodeTypeBox, Priority.ALWAYS);
            VBox namedNodesGridBox = new VBox();

            ScrollPane namedNodesScroll = new ScrollPane(namedNodesGridBox);
            namedNodesScroll.setId("darkBox");
            namedNodesScroll.setPadding(new Insets(10));

            HBox nodeScrollBox = new HBox(namedNodesScroll);
            nodeScrollBox.setPadding(new Insets(0, 15, 0, 0));

            Text namedNodeText = new Text("Node ");
            namedNodeText.setFill(Stages.altColor);
            namedNodeText.setFont(Stages.txtFont);

            Button namedNodeBtn = new Button();
            namedNodeBtn.setId("darkBox");
            namedNodeBtn.setAlignment(Pos.CENTER_LEFT);
            namedNodeBtn.setPadding(new Insets(5, 5, 5, 10));

            Runnable updateSelectedBtn = () -> {
                String selectedId = nodesList.selectedNamedNodeIdProperty().get();
                if (selectedId == null) {
                    namedNodeBtn.setText("(select node)");
                } else {
                    NamedNodeUrl namedNodeUrl = nodesList.getNamedNodeUrl(selectedId);
                    if (namedNodeUrl != null) {
                        namedNodeBtn.setText(namedNodeUrl.getName());
                    } else {
                        namedNodeBtn.setText("(select node)");
                    }
                }
            };

            updateSelectedBtn.run();

            nodesList.selectedNamedNodeIdProperty().addListener((obs, oldval, newVal) -> updateSelectedBtn.run());

            HBox nodesBox = new HBox(namedNodeText, namedNodeBtn);
            nodesBox.setAlignment(Pos.CENTER_LEFT);
            nodesBox.setMinHeight(40);
            nodesBox.setPadding(new Insets(10, 0, 0, 0));

            namedNodeBtn.prefWidthProperty().bind(nodesBox.widthProperty().subtract(namedNodeText.layoutBoundsProperty().get().getWidth()).subtract(15));
            namedNodesScroll.prefViewportWidthProperty().bind(nodesBox.widthProperty());

            SimpleDoubleProperty scrollWidth = new SimpleDoubleProperty(0);

            namedNodesGridBox.heightProperty().addListener((obs, oldVal, newVal) -> {
                double scrollViewPortHeight = namedNodesScroll.prefViewportHeightProperty().doubleValue();
                double gridBoxHeight = newVal.doubleValue();

                if (gridBoxHeight > scrollViewPortHeight) {
                    scrollWidth.set(40);
                }

            });

            nodesList.gridWidthProperty().bind(nodesBox.widthProperty().subtract(40).subtract(scrollWidth));

            VBox lightClientOptions = new VBox(publicNodesBox, nodeScrollBox, nodesBox);
            lightClientOptions.setId("bodyBox");
            lightClientOptions.setPadding(new Insets(5,10,10,20));
  

            Text nodeName = new Text(String.format("%-13s", "Name"));
            nodeName.setFill(Stages.txtColor);
            nodeName.setFont(Stages.txtFont);

            TextField nodeNameField = new TextField("Node #" + friendlyId);
            nodeNameField.setFont(Stages.txtFont);
            nodeNameField.setId("formField");
            HBox.setHgrow(nodeNameField, Priority.ALWAYS);

            HBox nodeNameBox = new HBox(nodeName, nodeNameField);
            nodeNameBox.setAlignment(Pos.CENTER_LEFT);
            nodeNameBox.setMinHeight(rowHeight);

            Text networkTypeText = new Text(String.format("%-13s", "Network Type"));
            networkTypeText.setFill(Stages.txtColor);
            networkTypeText.setFont(Stages.txtFont);

            MenuButton networkTypeBtn = new MenuButton("MAINNET");
            networkTypeBtn.setFont(Stages.txtFont);
            networkTypeBtn.setId("formField");
            HBox.setHgrow(networkTypeBtn, Priority.ALWAYS);

            MenuItem mainnetItem = new MenuItem("MAINNET");
            mainnetItem.setId("rowBtn");

            MenuItem testnetItem = new MenuItem("TESTNET");
            testnetItem.setId("rowBtn");

            networkTypeBtn.getItems().addAll(mainnetItem, testnetItem);

            HBox networkTypeBox = new HBox(networkTypeText, networkTypeBtn);
            networkTypeBox.setAlignment(Pos.CENTER_LEFT);
            networkTypeBox.setMinHeight(rowHeight);

            Text apiKeyText = new Text(String.format("%-13s", "API Key"));
            apiKeyText.setFill(Stages.txtColor);
            apiKeyText.setFont(Stages.txtFont);

            TextField apiKeyField = new TextField("");
            apiKeyField.setFont(Stages.txtFont);
            apiKeyField.setId("formField");
            HBox.setHgrow(apiKeyField, Priority.ALWAYS);

            HBox apiKeyBox = new HBox(apiKeyText, apiKeyField);
            apiKeyBox.setAlignment(Pos.CENTER_LEFT);
            apiKeyBox.setMinHeight(rowHeight);

            Text nodePortText = new Text(String.format("%-13s", "Port"));
            nodePortText.setFill(Stages.txtColor);
            nodePortText.setFont(Stages.txtFont);

            TextField nodePortField = new TextField("9053");
            nodePortField.setId("formField");
            HBox.setHgrow(nodePortField, Priority.ALWAYS);

            nodePortField.textProperty().addListener((obs, oldval, newVal) -> {

                if (!newVal.matches("\\d*")) {
                    newVal = newVal.replaceAll("[^\\d]", "");

                }
                int intVal = Integer.parseInt(newVal);

                if (intVal > 65535) {
                    intVal = 65535;
                }

                nodePortField.setText(intVal + "");

            });

            nodePortField.focusedProperty().addListener((obs, oldval, newVal) -> {
                if (!newVal) {
                    String portString = nodePortField.getText();
                    int intVal = Integer.parseInt(portString);

                    if (intVal < 1025) {
                        if (networkTypeOption.get().equals(NetworkType.TESTNET)) {
                            nodePortField.setText(ErgoNodes.TESTNET_PORT + "");
                        } else {
                            nodePortField.setText(ErgoNodes.MAINNET_PORT + "");
                        }

                        Alert portSmallAlert = new Alert(AlertType.NONE, "The minimum port value which may be assigned is: 1025\n\n(Default value used.)", ButtonType.CLOSE);
                        portSmallAlert.initOwner(m_addStage);
                        portSmallAlert.setHeaderText("Invalid Port");
                        portSmallAlert.setTitle("Invalid Port");
                        portSmallAlert.show();
                    }

                }
            });

            HBox nodePortBox = new HBox(nodePortText, nodePortField);
            nodePortBox.setAlignment(Pos.CENTER_LEFT);
            nodePortBox.setMinHeight(rowHeight);

            testnetItem.setOnAction((e) -> {
                networkTypeBtn.setText(testnetItem.getText());
                networkTypeOption.set(NetworkType.TESTNET);
                int portValue = Integer.parseInt(nodePortField.getText());
                if (portValue == ErgoNodes.MAINNET_PORT) {
                    nodePortField.setText(ErgoNodes.TESTNET_PORT + "");
                }
            });

            mainnetItem.setOnAction((e) -> {
                networkTypeBtn.setText(mainnetItem.getText());
                networkTypeOption.set(NetworkType.MAINNET);

                int portValue = Integer.parseInt(nodePortField.getText());
                if (portValue == ErgoNodes.TESTNET_PORT) {
                    nodePortField.setText(ErgoNodes.MAINNET_PORT + "");
                }

            });

            Text nodeUrlText = new Text(String.format("%-13s", "IP"));
            nodeUrlText.setFill(Stages.txtColor);
            nodeUrlText.setFont(Stages.txtFont);

            TextField nodeUrlField = new TextField("127.0.0.1");
            nodeUrlField.setFont(Stages.txtFont);
            nodeUrlField.setId("formField");
            HBox.setHgrow(nodeUrlField, Priority.ALWAYS);

            HBox nodeUrlBox = new HBox(nodeUrlText, nodeUrlField);
            nodeUrlBox.setAlignment(Pos.CENTER_LEFT);
            nodeUrlBox.setMinHeight(rowHeight);

            Region urlSpaceRegion = new Region();
            urlSpaceRegion.setMinHeight(40);

            VBox customClientOptionsBox = new VBox(nodeNameBox, networkTypeBox, nodeUrlBox, nodePortBox, apiKeyBox);
            customClientOptionsBox.setPadding(new Insets(15, 0, 0, 15));
            customClientOptionsBox.setId("bodyBox");
        
            Text localNodeHeadingText = new Text("Local Node");
            localNodeHeadingText.setFont(Stages.txtFont);
            localNodeHeadingText.setFill(Stages.txtColor);

            HBox localNodeHeadingBox = new HBox(localNodeHeadingText);
            headingBox.prefHeight(40);
            headingBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(headingBox, Priority.ALWAYS);
            headingBox.setPadding(new Insets(10, 10, 10, 10));
            headingBox.setId("headingBox");

            HBox localNodeHeadingPaddingBox = new HBox(localNodeHeadingBox);
            localNodeHeadingPaddingBox.setPadding(new Insets(5, 0, 2, 0));


            TextArea localNodeTextArea = new TextArea(ErgoNodeLocalData.DESCRIPTION);
            localNodeTextArea.setWrapText(true);
            localNodeTextArea.setEditable(false);
            VBox.setVgrow(localNodeTextArea, Priority.ALWAYS);

    

    



            VBox localNodeTextAreaBox = new VBox(localNodeTextArea);
            localNodeTextAreaBox.setPadding(new Insets(10));
            localNodeTextArea.setId("bodyBox");
            HBox.setHgrow(localNodeTextAreaBox, Priority.ALWAYS);
            VBox.setVgrow(localNodeTextAreaBox,Priority.ALWAYS);

            final String swapIncreaseUrl = Utils.getIncreseSwapUrl();
            Tooltip swapIncreaseTooltip = new Tooltip("Open Url: " + swapIncreaseUrl);
            swapIncreaseTooltip.setShowDelay(new Duration(50));

            BufferedButton swapIncreaseBtn = new BufferedButton("/assets/warning-30.png", 30);
            swapIncreaseBtn.setTooltip(swapIncreaseTooltip);
            swapIncreaseBtn.setText("Increase swap size");
            swapIncreaseBtn.setGraphicTextGap(10);
            swapIncreaseBtn.setTextAlignment(TextAlignment.RIGHT);
            swapIncreaseBtn.setOnAction((e)->{
                m_ergoNodes.getNetworksData().getHostServices().showDocument(swapIncreaseUrl);
            });
            HBox swapBox = new HBox(swapIncreaseBtn);
            HBox.setHgrow(swapBox, Priority.ALWAYS);
            swapBox.setMinHeight(30);
            swapBox.setAlignment(Pos.CENTER_RIGHT);
            swapBox.setPadding(new Insets(0,10,0,10));

            VBox localNodeOptionsBox = new VBox(localNodeHeadingBox, localNodeTextAreaBox);
            localNodeOptionsBox.setPadding(new Insets(15,0,0,15));
            VBox.setVgrow(localNodeOptionsBox, Priority.ALWAYS);
            

            VBox bodyOptionBox = new VBox(m_ergoLocalNode == null ? localNodeOptionsBox : lightClientOptions);
            VBox.setVgrow(bodyOptionBox, Priority.ALWAYS);
            bodyOptionBox.setPadding(new Insets(0, 0, 15, 15));

            Button nextBtn = new Button("Add");
            nextBtn.setPadding(new Insets(5, 15, 5, 15));

            HBox nextBox = new HBox(nextBtn);
            nextBox.setPadding(new Insets(0, 0, 0, 0));
            nextBox.setMinHeight(50);
            nextBox.setAlignment(Pos.CENTER_RIGHT);
            HBox.setHgrow(nextBox, Priority.ALWAYS);

            VBox bodyBox = new VBox(nodeTypeBox, bodyOptionBox, nextBox);
            bodyBox.setId("bodyBox");
            bodyBox.setPadding(new Insets(0, 10, 0, 10));
            VBox.setVgrow(bodyBox, Priority.ALWAYS);
            VBox bodyPaddingBox = new VBox(bodyBox);
            bodyPaddingBox.setPadding(new Insets(0, 5, 0, 5));
            VBox.setVgrow(bodyPaddingBox, Priority.ALWAYS);

            Region footerSpacer = new Region();
            footerSpacer.setMinHeight(5);

            VBox footerBox = new VBox(footerSpacer);

            layoutBox.getChildren().addAll(headerBox, bodyPaddingBox, footerBox);
        
            namedNodesScroll.prefViewportHeightProperty().bind(m_addStage.heightProperty().subtract(headerBox.heightProperty()).subtract(footerBox.heightProperty()).subtract(publicNodesBox.heightProperty()).subtract(nodesBox.heightProperty()));

            rowHeight.bind(m_addStage.heightProperty().subtract(headerBox.heightProperty()).subtract(nodeTypeBox.heightProperty()).subtract(footerBox.heightProperty()).subtract(95).divide(5));

            addNodeScene.getStylesheets().add("/css/startWindow.css");
            m_addStage.setScene(addNodeScene);
            m_addStage.show();

            ChangeListener<? super Node> listFocusListener = (obs, oldval, newVal) -> {
                if (newVal != null && newVal instanceof IconButton) {
                    IconButton iconButton = (IconButton) newVal;
                    String btnId = iconButton.getButtonId();
                    if (btnId != null) {
                        nodesList.selectedNamedNodeIdProperty().set(btnId);
                    }
                }
            };

            Runnable setPublic = () -> {

                bodyOptionBox.getChildren().clear();

                bodyOptionBox.getChildren().add(lightClientOptions);

                addNodeScene.focusOwnerProperty().addListener(listFocusListener);
                typeBtn.setText(defaultClientItem.getText());
       
            };

            Runnable setCuston = () -> {
                bodyOptionBox.getChildren().clear();

                bodyOptionBox.getChildren().add(customClientOptionsBox);

                addNodeScene.focusOwnerProperty().removeListener(listFocusListener);
                typeBtn.setText(configureItem.getText());
               
            };

            Runnable setLocal = () -> {


                bodyOptionBox.getChildren().clear();
                bodyOptionBox.getChildren().add(localNodeOptionsBox);

                addNodeScene.focusOwnerProperty().removeListener(listFocusListener);
                typeBtn.setText(localNodeItem.getText());
                if(m_addStage.isMaximized()){
                    maximizeBtn.fire();
                }

            };

            Runnable switchPublic = () -> {
                switch (nodeOption.get()) {
                    case CUSTOM:
                        setCuston.run();
                        break;
                    case ErgoNodeLocalData.LOCAL_NODE:
                        setLocal.run();
                        break;
                    default:
                        setPublic.run();
                        break;
                }
            };

            nodeOption.addListener((obs, oldVal, newVal) -> {
                switchPublic.run();
              
            });

            switchPublic.run();

    

            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1, new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = Executors.defaultThreadFactory().newThread(r);
                    t.setDaemon(true);
                    return t;
                }
            });

            Runnable setUpdated = () -> {
                save();
            };

            addNodeScene.widthProperty().addListener((obs, oldVal, newVal) -> {
                m_addStageWidth = newVal.doubleValue();

                if (m_lastExecution != null && !(m_lastExecution.isDone())) {
                    m_lastExecution.cancel(false);
                }

                m_lastExecution = executor.schedule(setUpdated, EXECUTION_TIME, TimeUnit.MILLISECONDS);
            });

            addNodeScene.heightProperty().addListener((obs, oldVal, newVal) -> {
                m_addStageHeight = newVal.doubleValue();

                if (m_lastExecution != null && !(m_lastExecution.isDone())) {
                    m_lastExecution.cancel(false);
                }

                m_lastExecution = executor.schedule(setUpdated, EXECUTION_TIME, TimeUnit.MILLISECONDS);
            });

            ResizeHelper.addResizeListener(m_addStage, minWidth, minHeight, Double.MAX_VALUE, Double.MAX_VALUE);

            maximizeBtn.setOnAction(maxEvent -> {
                boolean maximized = m_addStage.isMaximized();

                m_addStageMaximized = !maximized;

                if (!maximized) {
                    m_prevAddStageWidth = m_addStage.getWidth();
                    m_prevAddStageHeight = m_addStage.getHeight();
                }
                save();
                m_addStage.setMaximized(m_addStageMaximized);
            });

            Runnable doClose = () -> {
                if(m_addStage != null){
                    m_addStage.close();
                    m_addStage = null;
                }
            };
            Runnable showNoneSelect = () -> {
                Alert a = new Alert(AlertType.NONE, "Select a node.", ButtonType.OK);
                a.setTitle("Select a node");
                a.initOwner(m_addStage);
                a.show();
            };

            Runnable setupLocalNode = ()->{
               //TODO: m_ergoLocalNode.setup();
            };
            
            nextBtn.setOnAction((e) -> {
                switch (nodeOption.get()) {
                    case CUSTOM:
                        add(new ErgoNodeData(this, clientTypeOption.get(), new NamedNodeUrl(friendlyId, nodeNameField.getText(), nodeUrlField.getText(), Integer.parseInt(nodePortField.getText()), apiKeyField.getText(), networkTypeOption.get())), true);
                        m_doGridUpdate.set(LocalDateTime.now());
                        doClose.run();
                        break;
                    case ErgoNodeLocalData.LOCAL_NODE:
                        addLocalNode(null);
                        setupLocalNode.run();
                        
                        doClose.run();
                    break;
                    default:
                        String nodeId = nodesList.selectedNamedNodeIdProperty().get();
                        if (nodeId != null) {
                            NamedNodeUrl namedNodeUrl = nodesList.getNamedNodeUrl(nodeId);

                            add(new ErgoNodeData(this, ErgoNodeData.LIGHT_CLIENT, namedNodeUrl), true);
                            m_doGridUpdate.set(LocalDateTime.now());
                            doClose.run();
                        } else {
                            showNoneSelect.run();
                        }
                        break;
                }
            });

            m_ergoNodes.shutdownNowProperty().addListener((obs, oldVal, newVal) -> {
                doClose.run();
            });

            m_addStage.setOnCloseRequest((e) -> doClose.run());

            closeBtn.setOnAction((e) -> doClose.run());
        } else {
            if (m_addStage.isIconified()) {
                m_addStage.setIconified(false);
            }
            m_addStage.show();
      
            
        }
    }*/