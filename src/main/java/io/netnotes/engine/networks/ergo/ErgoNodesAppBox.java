package io.netnotes.engine.networks.ergo;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.commons.io.FilenameUtils;
import org.ergoplatform.appkit.NetworkType;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netnotes.engine.AppBox;
import io.netnotes.engine.AppData;
import io.netnotes.engine.BufferedButton;
import io.netnotes.engine.GitHubAPI;
import io.netnotes.engine.JsonParametersBox;
import io.netnotes.engine.NamedNodeUrl;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.NoteMsgInterface;
import io.netnotes.engine.Stages;
import io.netnotes.engine.Utils;
import io.netnotes.engine.GitHubAPI.GitHubAsset;
import io.netnotes.engine.apps.AppConstants;
import io.netnotes.friendly_id.FriendlyId;

import javafx.animation.PauseTransition;
import javafx.beans.binding.Binding;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ErgoNodesAppBox extends AppBox {


    private Stage m_appStage;
    private SimpleObjectProperty<AppBox> m_currentBox = new SimpleObjectProperty<>();
    private NoteInterface m_ergoNetworkInterface = null;
    private VBox m_mainBox;

    private SimpleBooleanProperty m_showNodes = new SimpleBooleanProperty(false);

    private String m_locationId = null;

    private SimpleStringProperty m_defaultNodeId = new SimpleStringProperty(null);
    private NoteMsgInterface m_nodeListener = null;

    private ErgoNodeClientControl m_nodeControlBox = null;

    private String m_nodeListenerId = null;
    private NetworksData m_networksData = null; 

    public void sendMessage(int code, long timestamp, String networkId, String msg){
        if(networkId != null && networkId.equals(ErgoConstants.NODE_NETWORK)){

            switch(code){
     
                case NoteConstants.LIST_DEFAULT_CHANGED:
                    updateDefaultNodeId();
                break;
              
            }

            AppBox appBox  = m_currentBox.get();
            if(appBox != null){
                
                appBox.sendMessage(code, timestamp, networkId, msg);
                
            }

        }
    }



    public ErgoNodesAppBox(Stage appStage, String locationId, NetworksData networksData, NoteInterface ergoNetworkInterface){
        super();
        m_ergoNetworkInterface = ergoNetworkInterface;
        m_appStage = appStage;
        m_locationId = locationId;
        m_networksData = networksData;
        
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
            if(m_defaultNodeId.get() == null){
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

        MenuButton nodeMenuBtn = new MenuButton(selectString);
        nodeMenuBtn.setId("arrowMenuButton");

        Button disableNodeBtn = new Button("☓");
        disableNodeBtn.setId("lblBtn");

        disableNodeBtn.setOnAction(e -> {

            clearDefault(onSucceeded->{}, onFailed->{});
       
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

        Runnable addUrl = () ->{

            AddNodeBox addNodeBox = new AddNodeBox();


            m_currentBox.set(addNodeBox);
        };

        Runnable installNode = () -> {

            m_currentBox.set( new InstallNodeBox());    
        };
        

        Runnable importNode = () -> {

 
            
        };



        Runnable removeNodes = ()->{
            nodeOptionsBtn.hide();
            m_currentBox.set(new RemoveNodesBox());
        };
        
        nodeMenuBtn.showingProperty().addListener((obs,oldval,newval)->{
            if(newval){
                nodeMenuBtn.getItems().clear();
                nodeMenuBtn.getItems().add(new MenuItem("Getting nodes..."));

                JsonObject note = NoteConstants.getCmdObject("getNodes");
                note.addProperty("networkId", ErgoConstants.NODE_NETWORK);
                note.addProperty("locationId", m_locationId);

                ergoNetworkInterface.sendNote(note, onNodes ->{
                    Object onNodesObject = onNodes.getSource().getValue();
                    JsonArray nodesArray = onNodesObject != null && onNodesObject instanceof JsonArray ? (JsonArray) onNodesObject : null;
                    if( nodesArray != null && nodesArray.size() > 0){
                
                        for (JsonElement element : nodesArray) {
                            if (element != null && element instanceof JsonObject) {
                                JsonObject json = element.getAsJsonObject();

                                String name = json.get("name").getAsString();
                                String id = json.get("id").getAsString();
                                String clientType = json.get("clientType").getAsString();
                                JsonElement namedNodeElement = json.get("namedNode");
                        
                                String nodeImg = ErgoConstants.getClientImg(clientType);

                                try {
                                    NamedNodeUrl namedNode = namedNodeElement != null && namedNodeElement.isJsonObject() ? new NamedNodeUrl(namedNodeElement.getAsJsonObject()) : null;
                                    MenuItem nodeItem = new MenuItem(String.format("%-50s", nodeImg + " " + name + " - " + namedNode.getUrlString()));

                                    nodeItem.setOnAction(action -> {
                                        nodeMenuBtn.hide();
                                        if(m_defaultNodeId.get() == null || (m_defaultNodeId.get() != null && !m_defaultNodeId.get().equals(id))){
                                            setDefault(id, onSucceeded->{}, onFailed->{});
                                            m_showNodes.set(true);
                                        }

                                    });

                                    nodeMenuBtn.getItems().add(nodeItem);
                                } catch (Exception e1) {
                                    
                                }
                            }
                        }
                
                    }else{

                    }
                }, onNodesFailed->{});

                
            }

            /*
            note = Utils.getCmdObject("getLocalNodes");
            note.addProperty("networkId", App.NODE_NETWORK);
            note.addProperty("locationId", m_locationId);

            JsonArray localNodesArray = (JsonArray) ergoNetworkInterface.sendNote(note);

            if(localNodesArray != null && localNodesArray.size() > 0){    
                for(JsonElement jsonElement : localNodesArray){ 
                    JsonObject json = jsonElement.getAsJsonObject();
                    String name = json.get("name").getAsString();
                    String id = json.get("id").getAsString();

                    MenuItem nodeItem = new MenuItem(String.format("%-20s", "🖳  " + name));
                    nodeItem.setOnAction(action -> {
                        if(m_nodeInterface.get() == null || (m_nodeInterface.get() != null && !m_nodeInterface.get().getNetworkId().equals(id))){
                            setDefault(id);
                        }
                    });
                    
                    nodeMenu.getItems().add(nodeItem);
                }
            }*/


            nodeMenuBtn.getItems().add(new SeparatorMenuItem());

            MenuItem addRemoteItem = new MenuItem(String.format("%-20s", "[ Add remote… ]"));
            addRemoteItem.setOnAction(action -> {
                nodeMenuBtn.hide();
                addUrl.run();
            });
            
            MenuItem intstallNodeItem = new MenuItem(String.format("%-20s", "[ Local Install… ]"));
            intstallNodeItem.setOnAction(action -> {
                nodeMenuBtn.hide();
                installNode.run();
            });

            MenuItem openNodeMenuItem = new MenuItem("[ Import… ]");
            openNodeMenuItem.setOnAction(e->{
                nodeMenuBtn.hide();
                importNode.run();
            });

            MenuItem removeNodeMenuItem = new MenuItem("[ Remove… ]");
            removeNodeMenuItem.setOnAction(e->{
                nodeMenuBtn.hide();
                removeNodes.run();
            });

            nodeMenuBtn.getItems().addAll(addRemoteItem, intstallNodeItem, openNodeMenuItem, removeNodeMenuItem);

        });

        MenuItem addUrlMenuItem = new MenuItem("➕    Add remote…");
        addUrlMenuItem.setOnAction(e->{
            nodeOptionsBtn.hide();
            addUrl.run();
        });

        MenuItem installMenuItem = new MenuItem("🖳   Local Install…");
        installMenuItem.setOnAction(e->{
            nodeOptionsBtn.hide();
            installNode.run();
        });

        MenuItem importMenuItem = new MenuItem("⇲    Import local…    ");
        importMenuItem.setOnAction(e->{
            nodeOptionsBtn.hide();
            importNode.run();
        });

        MenuItem removeMenuItem = new MenuItem("🗑    Remove…");
        removeMenuItem.setOnAction(e->{
            nodeOptionsBtn.hide();
            removeNodes.run();
        });

        nodeOptionsBtn.getItems().addAll(installMenuItem, addUrlMenuItem, importMenuItem, removeMenuItem);
        
    



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
                if (m_nodeControlBox == null && m_defaultNodeId.get() != null) {
                   getNetworkObject(onNetworkObject->{
                        Object resultObj = onNetworkObject.getSource().getValue();    
                        JsonObject nodeObject = resultObj != null && resultObj instanceof JsonObject ? (JsonObject) resultObj : null;
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
                    }, onFailed->{
                        if(m_nodeControlBox != null){
                            m_nodeControlBox.shutdown();
                            nodeBodyPaddingBox.getChildren().clear();
                            m_nodeControlBox = null;
                        }
                    });
                   
                    
                    
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

               
        
        
        m_defaultNodeId.addListener((obs,oldval,newval)->{
            
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


        updateDefaultNodeId();

  
        getChildren().addAll(m_mainBox);
        setPadding(new Insets(0,0,5,0));
    }

    public NetworksData getNetworksData(){
        return m_networksData;
    }

    public ExecutorService getExecService(){
        return getNetworksData().getExecService();
    }

    public Future<?> getNetworkObject(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        String nodeId = m_defaultNodeId.get();
        if(nodeId != null){
            JsonObject note = NoteConstants.getCmdObject("getNetworkObject");
            note.addProperty("networkId", ErgoConstants.NODE_NETWORK);
            note.addProperty("locationId", m_locationId);
            note.addProperty("id", nodeId);

            return m_ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }
        return null;
    }

    public void clearDefault(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonObject setDefaultObject = NoteConstants.getCmdObject("clearDefault");
        setDefaultObject.addProperty("networkId", ErgoConstants.NODE_NETWORK);
        setDefaultObject.addProperty("locationId", m_locationId);
        m_ergoNetworkInterface.sendNote(setDefaultObject, onSucceeded, onFailed);
      
    }

    public void setDefault(String id, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){

        JsonObject setDefaultObject = NoteConstants.getCmdObject("setDefault");
        setDefaultObject.addProperty("networkId", ErgoConstants.NODE_NETWORK);
        setDefaultObject.addProperty("locationId", m_locationId);
        setDefaultObject.addProperty("id", id);
        m_ergoNetworkInterface.sendNote(setDefaultObject, onSucceeded, onFailed);
    
    }

    public void updateDefaultNodeId(){
        
        JsonObject note = NoteConstants.getCmdObject("getDefaultNodeId");
        note.addProperty("networkId", ErgoConstants.NODE_NETWORK);
        note.addProperty("locationId", m_locationId);
        m_ergoNetworkInterface.sendNote(note, onSucceeded->{
            Object defaultIdObj = onSucceeded.getSource().getValue();
            if(defaultIdObj != null && defaultIdObj instanceof JsonObject){
                JsonObject defaultJson = (JsonObject) defaultIdObj;
                JsonElement defaultNodeIdElement = defaultJson.get("defaultNodeId");
                m_defaultNodeId.set(defaultNodeIdElement != null && !defaultNodeIdElement.isJsonNull() ? defaultNodeIdElement.getAsString() : null);
            }else{
                m_defaultNodeId.set(null);
            }         
        }, onFailed->{
            m_defaultNodeId.set(null);
        });
      
        
        
    }


    private class AddNodeBox extends AppBox {
        
        

        public AddNodeBox() {
   
            
            Tooltip errorTooltip = new Tooltip();
            Button backButton = new Button("🠜");
            backButton.setId("lblBtn");

            backButton.setOnAction(e -> {
                m_currentBox.set(null);
            });

            Label headingText = new Label("Add Node");
            headingText.setFont(Stages.txtFont);
            headingText.setPadding(new Insets(0,0,0,15));

            HBox headingBox = new HBox(backButton, headingText);
            headingBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(headingBox, Priority.ALWAYS);
            headingBox.setPadding(new Insets(10, 15, 0, 15));
     
            VBox headerBox = new VBox(headingBox);
            headerBox.setPadding(new Insets(0, 5, 0, 0));



   

            Text nodeName = new Text(String.format("%-13s", "Name"));
            nodeName.setFill(Stages.txtColor);
            nodeName.setFont(Stages.txtFont);

            TextField nodeNameField = new TextField();
            HBox.setHgrow(nodeNameField, Priority.ALWAYS);
            nodeNameField.textProperty().addListener((obs,oldval,newval)->{
                nodeNameField.setId(newval.length() > 0 ? null : "formField");
            });

            HBox nodeNameFieldBox = new HBox(nodeNameField);
            HBox.setHgrow(nodeNameFieldBox, Priority.ALWAYS);
            nodeNameFieldBox.setId("bodyBox");
            nodeNameFieldBox.setAlignment(Pos.CENTER_LEFT);
            nodeNameFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);
      

            HBox nodeNameBox = new HBox(nodeName, nodeNameFieldBox);
            nodeNameBox.setAlignment(Pos.CENTER_LEFT);
            nodeNameBox.setMinHeight(Stages.ROW_HEIGHT);

            Text hostText = new Text(String.format("%-13s", "Host"));
            hostText.setFill(Stages.txtColor);
            hostText.setFont(Stages.txtFont);
            

            TextField hostField = new TextField("");
            HBox.setHgrow(hostField, Priority.ALWAYS);
            hostField.setPromptText(NamedNodeUrl.DEFAULT_NODE_IP);

            hostField.textProperty().addListener((obs,oldval,newval)->{
                hostField.setId(newval.length() > 0 ? null : "formField");
            });

            HBox nodeIpFieldBox = new HBox(hostField);
            HBox.setHgrow(nodeIpFieldBox, Priority.ALWAYS);
            nodeIpFieldBox.setId("bodyBox");
            nodeIpFieldBox.setAlignment(Pos.CENTER_LEFT);
            nodeNameFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);
      

            HBox nodeIpBox = new HBox(hostText, nodeIpFieldBox);
            nodeIpBox.setAlignment(Pos.CENTER_LEFT);
            nodeIpBox.setMinHeight(Stages.ROW_HEIGHT);


            Text networkTypeText = new Text(String.format("%-13s", "Network type"));
            networkTypeText.setFill(Stages.txtColor);
            networkTypeText.setFont(Stages.txtFont);

            ContextMenu networkTypeMenu = new ContextMenu();            
            
            TextField networkTypeField = new TextField(String.format("%-30s", NetworkType.MAINNET.toString()));
            networkTypeField.setEditable(false);
            HBox.setHgrow(networkTypeField, Priority.ALWAYS);


            MenuItem mainnetItem = new MenuItem(String.format("%-30s", NetworkType.MAINNET.toString()));
            mainnetItem.setId("rowBtn");

            MenuItem testnetItem = new MenuItem(String.format("%-30s", NetworkType.TESTNET.toString()));
            testnetItem.setId("rowBtn");

            networkTypeMenu.getItems().addAll(mainnetItem, testnetItem);

            Label networkTypeLblbtn = new Label("⏷");
            networkTypeLblbtn.setId("lblBtn");

            HBox networkTypeFieldBox = new HBox(networkTypeField, networkTypeLblbtn);
            HBox.setHgrow(networkTypeFieldBox, Priority.ALWAYS);
            networkTypeFieldBox.setId("bodyBox");
            networkTypeFieldBox.setAlignment(Pos.CENTER_LEFT);
            networkTypeFieldBox.setMaxHeight(18);

            networkTypeFieldBox.addEventFilter(MouseEvent.MOUSE_CLICKED,e->{
                Point2D p = networkTypeFieldBox.localToScene(0.0, 0.0);
                
    
                networkTypeMenu.show(networkTypeFieldBox,
                    5 + p.getX() + networkTypeFieldBox.getScene().getX() + networkTypeFieldBox.getScene().getWindow().getX(),
                    (p.getY() + networkTypeFieldBox.getScene().getY() + networkTypeFieldBox.getScene().getWindow().getY())
                        + networkTypeFieldBox.getLayoutBounds().getHeight() - 1);
            });
      

            HBox networkTypeBox = new HBox(networkTypeText, networkTypeFieldBox);
            networkTypeBox.setAlignment(Pos.CENTER_LEFT);
            networkTypeBox.setMinHeight(Stages.ROW_HEIGHT);

            Text apiKeyText = new Text(String.format("%-13s", "API Key"));
            apiKeyText.setFill(Stages.txtColor);
            apiKeyText.setFont(Stages.txtFont);

            PasswordField apiKeyHidden = new PasswordField();
            HBox.setHgrow(apiKeyHidden, Priority.ALWAYS);

            Image eyeImg = new Image(AppConstants.SHOW_ICON);
            Image eyeOffImg = new Image(AppConstants.HIDE_ICON);

            ImageView btnImgView = new ImageView(eyeImg);
            btnImgView.setImage(eyeImg);
            btnImgView.setPreserveRatio(true);
            btnImgView.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);

            Button showApiKeyBtn = new Button();
            showApiKeyBtn.setPadding(new Insets(1));
            showApiKeyBtn.setGraphic(btnImgView);

            TextField apiKeyField = new TextField("");
            HBox.setHgrow(apiKeyField, Priority.ALWAYS);


            HBox apiKeyFieldBox = new HBox(apiKeyHidden, showApiKeyBtn);
            HBox.setHgrow(apiKeyFieldBox, Priority.ALWAYS);
            apiKeyFieldBox.setId("bodyBox");
            apiKeyFieldBox.setAlignment(Pos.CENTER_LEFT);
            apiKeyFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);


            apiKeyField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    apiKeyHidden.setText(apiKeyField.getText());
                }
            });

            apiKeyHidden.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    apiKeyField.setText(apiKeyHidden.getText());
                }
            });

            showApiKeyBtn.setOnAction(e->{
                if(apiKeyFieldBox.getChildren().contains(apiKeyHidden)){
                  
                    apiKeyFieldBox.getChildren().remove(apiKeyHidden);
                    apiKeyFieldBox.getChildren().add(0, apiKeyField);
                    btnImgView.setImage(eyeOffImg);
                }else{
                    if(apiKeyFieldBox.getChildren().contains(apiKeyField)){
                        apiKeyFieldBox.getChildren().remove(apiKeyField);
                        apiKeyFieldBox.getChildren().add(0, apiKeyHidden);
                        btnImgView.setImage(eyeImg);
                        
                    }
                }
            });


            HBox apiKeyBox = new HBox(apiKeyText, apiKeyFieldBox);
            apiKeyBox.setAlignment(Pos.CENTER_LEFT);
            apiKeyBox.setMinHeight(Stages.ROW_HEIGHT);


            Text nodePortText = new Text(String.format("%-13s", "Port"));
            nodePortText.setFill(Stages.txtColor);
            nodePortText.setFont(Stages.txtFont);

            TextField nodePortField = new TextField("9053");
            HBox.setHgrow(nodePortField, Priority.ALWAYS);

            HBox nodePortFieldBox = new HBox(nodePortField);
            HBox.setHgrow(nodePortFieldBox, Priority.ALWAYS);
            nodePortFieldBox.setId("bodyBox");
            nodePortFieldBox.setAlignment(Pos.CENTER_LEFT);
            nodePortFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);
            nodePortField.setOnKeyPressed(e->{
                if (Utils.keyCombCtrZ.match(e) ) { 
                    e.consume();
                }
            });
            nodePortField.setOnKeyPressed(e->{
                if (Utils.keyCombCtrZ.match(e) ) { 
                    e.consume();
                }
            });
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


           

            HBox nodePortBox = new HBox(nodePortText, nodePortFieldBox);
            nodePortBox.setAlignment(Pos.CENTER_LEFT);
            nodePortBox.setMinHeight(Stages.ROW_HEIGHT);

            testnetItem.setOnAction((e) -> {
                networkTypeField.setText(testnetItem.getText());
                nodePortField.setText(ErgoConstants.TESTNET_PORT + "");
                nodePortField.setEditable(false);
            });

            mainnetItem.setOnAction((e) -> {
                networkTypeField.setText(mainnetItem.getText());
                nodePortField.setEditable(true);
                int portValue = Integer.parseInt(nodePortField.getText());
                if (portValue == ErgoConstants.TESTNET_PORT) {
                    nodePortField.setText(ErgoConstants.MAINNET_PORT + "");
                }

            });

            

            Region urlSpaceRegion = new Region();
            urlSpaceRegion.setMinHeight(40);

            Insets optionsBoxRowInsets = new Insets(0,0,5,0);

            nodeNameBox.setPadding(optionsBoxRowInsets);
            nodeIpBox.setPadding(optionsBoxRowInsets);
            apiKeyBox.setPadding(optionsBoxRowInsets);
            networkTypeBox.setPadding(optionsBoxRowInsets);
            nodePortBox.setPadding(optionsBoxRowInsets);

            VBox customClientOptionsBox = new VBox(nodeNameBox, nodeIpBox, apiKeyBox, networkTypeBox, nodePortBox);
            

            HBox optionsPaddingBox = new HBox(customClientOptionsBox);
            HBox.setHgrow(optionsPaddingBox, Priority.ALWAYS);
            optionsPaddingBox.setPadding(new Insets(15));

            Region hBar = new Region();
            hBar.setPrefWidth(400);
            hBar.setMinHeight(2);
            hBar.setId("hGradient");

            HBox gBox = new HBox(hBar);
            gBox.setAlignment(Pos.CENTER);
            gBox.setPadding(new Insets(0, 0, 20, 0));


         

            Region hBar2 = new Region();
            hBar2.setPrefWidth(400);
            hBar2.setMinHeight(2);
            hBar2.setId("hGradient");

            HBox gBox2 = new HBox(hBar2);
            gBox2.setAlignment(Pos.CENTER);
            gBox2.setPadding(new Insets(15, 0, 0, 0));

            Button nextBtn = new Button("Next");


            HBox nextBox = new HBox(nextBtn);
            nextBox.setAlignment(Pos.CENTER);
            nextBox.setPadding(new Insets(15, 0, 15, 0));


            VBox bodyPaddingBox = new VBox(gBox, customClientOptionsBox,gBox2, nextBox);
            VBox.setMargin(bodyPaddingBox, new Insets(10, 10, 0, 20));


            VBox layoutVBox = new VBox(headerBox, bodyPaddingBox);

            getChildren().add(layoutVBox);

            PauseTransition pt = new PauseTransition(Duration.millis(1600));
            pt.setOnFinished(ptE -> {
                errorTooltip.hide();
            });

            Runnable showErrorText = ()->{
                Point2D p = nextBtn.localToScene(0.0, 0.0);

                

                errorTooltip.show(nextBtn,
                p.getX() + nextBtn.getScene().getX() + nextBtn.getScene().getWindow().getX() -40,
                (p.getY() + nextBtn.getScene().getY() + nextBtn.getScene().getWindow().getY()
                        - nextBtn.getLayoutBounds().getHeight()) - 10);
                
              
                pt.play();
            };

            nextBtn.setOnAction(e->{
                String nameString = nodeNameField.getText();
                
                if(nameString.length() > 0){
                    String hostString = hostField.getText().length() == 0 ? hostField.getPromptText() : hostField.getText();
                   
                    if(hostString.length() > 0){
                  
                        String portString = nodePortField.getText();
                        
                        int portNumber = portString.length()>0 ? Integer.parseInt(portString) : 0;
                        
                        if(portNumber > ErgoConstants.MIN_PORT_NUMBER){
                            NetworkType networkType = networkTypeField.getText().trim().equals(NetworkType.TESTNET.toString()) ? NetworkType.TESTNET : NetworkType.MAINNET;
                            if(portNumber == ErgoConstants.TESTNET_PORT && networkType == NetworkType.MAINNET || portNumber == ErgoConstants.MAINNET_PORT && networkType == NetworkType.TESTNET){
                                
                                if(portNumber == ErgoConstants.TESTNET_PORT && networkType == NetworkType.MAINNET){
                                    errorTooltip.setText("Error: Port " + portNumber + " invalid for " + NetworkType.MAINNET + ": Port " + ErgoConstants.TESTNET_PORT + " reserved for " + NetworkType.TESTNET);
                                    showErrorText.run();
                                }else{
                                    errorTooltip.setText("Error: Port " + portNumber + " invalid for " + NetworkType.TESTNET + ": Port " + ErgoConstants.MAINNET_PORT + " reserved for " + NetworkType.MAINNET);
                                    showErrorText.run();
                                }
                            
                            }else{

                                String apiKeyString = apiKeyHidden.getText();
                                String nodeId = FriendlyId.createFriendlyId();

                                NamedNodeUrl namedNodeUrl = new NamedNodeUrl(nodeId, nameString, hostString, portNumber, apiKeyString, networkType);

                                JsonObject note = NoteConstants.getCmdObject("addRemoteNode");
                                note.addProperty("networkId", ErgoConstants.NODE_NETWORK);
                                note.addProperty("locationId", m_locationId);

                                note.add("data", namedNodeUrl.getJsonObject());

                                m_ergoNetworkInterface.sendNote(note, onSucceeded->{
                                    Object result = onSucceeded.getSource().getValue();
                                    if (result != null && result instanceof String) {
                                
                                        setDefault((String) result, onDefault->{}, onDefaultFailed->{});

                                    }else{
                                        errorTooltip.setText("Error: Unable to add node");
                                        showErrorText.run();
                                    }
                                }, onFailed->{

                                });
                                
                            }
                            
                            

                        }else{
                            errorTooltip.setText("Valid port required");
                            showErrorText.run();
                        }
                    }else{
                        errorTooltip.setText("Host required");
                        showErrorText.run();
                    }

                }else{
                    
                    errorTooltip.setText("Name required");
                    showErrorText.run();
                }
            });
        }


    }


    private class InstallNodeBox extends AppBox {
        public final String USER_CONFIG_FILE = "User: Config File";

        public final String DEFAULT_FULL_NODE = "Default: Full Node";
        public final String DEFAULT_NODE_FOLDER_NAME = "Local Node";
        public final String DEFAULT_CONFIG_NAME = "ergo.conf";
   
        private SimpleObjectProperty<VBox> currentBox = new SimpleObjectProperty<>(null);
        private Tooltip errorTooltip = new Tooltip();
        private PauseTransition pt = new PauseTransition(Duration.millis(5000));
       
        private TextField configModeField = new TextField(DEFAULT_FULL_NODE);
        private TextField nodeNameField = new TextField("Local Node");
        private TextField advFileModeField = new TextField();
        private TextField apiKeyField = new TextField("");
        private TextField directoryRootField = new TextField();
        private TextField useableField = new TextField(Utils.formatedBytes(AppData.HOME_DIRECTORY.getUsableSpace(), 2));
        private VBox defaultBodyBox;
        private String m_appDirString;

        public InstallNodeBox() {
        
            getAppDir(onSucceeded->{
                Object succeessObject = onSucceeded.getSource().getValue();
                if(succeessObject != null && succeessObject instanceof JsonObject){
                    JsonObject appDirResult = (JsonObject) succeessObject;
                    m_appDirString = appDirResult != null ? appDirResult.get("appDir").getAsString() + "/" + DEFAULT_NODE_FOLDER_NAME : AppData.HOME_DIRECTORY.getAbsolutePath() + "/" + DEFAULT_NODE_FOLDER_NAME;
                    

                    Button backButton = new Button("🠜");
                    backButton.setId("lblBtn");

                    backButton.setOnAction(e -> {
                        if(currentBox.get() == null){
                            m_currentBox.set(null);
                        }else{
                            currentBox.set(null);
                        }
                    });

                    Label headingText = new Label("Install Node");
                    headingText.setFont(Stages.txtFont);
                    headingText.setPadding(new Insets(0,0,0,15));
                // TextField apiKeyField, SimpleObjectProperty<File> configFileOption, SimpleObjectProperty<File> directoryRoot, TextField directoryNameField,
                    //Button nextBtn, MenuButton configModeBtn,

                    HBox headingBox = new HBox(backButton, headingText);
                    headingBox.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(headingBox, Priority.ALWAYS);
                    headingBox.setPadding(new Insets(10, 15, 0, 15));
            
                    VBox headerBox = new VBox(headingBox);
                    headerBox.setPadding(new Insets(0, 5, 0, 0));

                    Region hBar = new Region();
                    hBar.setPrefWidth(400);
                    hBar.setMinHeight(2);
                    hBar.setId("hGradient");

                    HBox gBox = new HBox(hBar);
                    gBox.setAlignment(Pos.CENTER);
                    gBox.setPadding(new Insets(15, 0, 0, 0));

                    
                    init();

                    VBox bodyPaddingBox = new VBox(defaultBodyBox);
                    bodyPaddingBox.setPadding(new Insets(10, 20, 10, 20));

                    getChildren().addAll(headerBox, gBox, bodyPaddingBox);

                    pt.setOnFinished(ptE -> {
                        errorTooltip.hide();
                    });

                    currentBox.addListener((obs,oldval,newval)->{
                        bodyPaddingBox.getChildren().clear();

                        if(newval == null){
                            bodyPaddingBox.getChildren().add(defaultBodyBox);
                        }else{
                            bodyPaddingBox.getChildren().add(newval);
                        }
                    });
                }
            }, onFailed->{});
        }

        private void showErrorTip(Node ownerNode){
            Point2D p = ownerNode.localToScene(0.0, 0.0);
            int width = Utils.getStringWidth(errorTooltip.getText());

            errorTooltip.show(ownerNode,
            p.getX() + ownerNode.getScene().getX() + ownerNode.getScene().getWindow().getX() - (width/2),
            (p.getY() + ownerNode.getScene().getY() + ownerNode.getScene().getWindow().getY()
                    - ownerNode.getLayoutBounds().getHeight()) - 35);
        
            pt.play();
        }

        private Future<?> getAppDir(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
            JsonObject appDirNote = NoteConstants.getCmdObject("getAppDir");
            appDirNote.addProperty("networkId", ErgoConstants.NODE_NETWORK);
            appDirNote.addProperty("locationId", m_locationId);
            return m_ergoNetworkInterface.sendNote(appDirNote, onSucceeded, onFailed);
        }

        private SimpleLongProperty m_requiredSpaceLong = new SimpleLongProperty( ErgoConstants.REQUIRED_SPACE);
        private void init(){


            Text nodeNameText = new Text(String.format("%-12s", "Name"));
            nodeNameText.setFill(Stages.txtColor);
            nodeNameText.setFont(Stages.txtFont);

           
            HBox.setHgrow(nodeNameField, Priority.ALWAYS);
 
            HBox nodeNameFieldBox = new HBox(nodeNameField);
            HBox.setHgrow(nodeNameFieldBox, Priority.ALWAYS);
            nodeNameFieldBox.setAlignment(Pos.CENTER_LEFT);
            nodeNameFieldBox.setId("bodyBox");
            nodeNameFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);

            HBox nodeNameBox = new HBox(nodeNameText, nodeNameFieldBox);
            nodeNameBox.setAlignment(Pos.CENTER_LEFT);
            nodeNameBox.setMinHeight(Stages.ROW_HEIGHT);



            Text configModeText = new Text(String.format("%-12s", "Mode"));
            configModeText.setFont(Stages.txtFont);
            configModeText.setFill(Stages.txtColor);

           
            HBox.setHgrow(configModeField, Priority.ALWAYS);
            configModeField.setEditable(false);
            configModeField.setId("hand");

            
            ContextMenu configModeContextMenu = new ContextMenu();
            configModeContextMenu.setMinWidth(250);
            Label configModeBtn = new Label("⏷");
            configModeBtn.setId("lblBtn");
            

            MenuItem simpleItem = new MenuItem(DEFAULT_FULL_NODE);
            simpleItem.setOnAction(e -> {
                configModeField.setText(simpleItem.getText());
            });
            

            MenuItem advancedItem = new MenuItem(USER_CONFIG_FILE);
            advancedItem.setOnAction(e -> {
                configModeField.setText(advancedItem.getText());
            });

            configModeContextMenu.setMinWidth(150);
            configModeContextMenu.getItems().addAll(simpleItem, advancedItem);
            

            HBox configModeFieldBox = new HBox(configModeField, configModeBtn);
            HBox.setHgrow(configModeFieldBox, Priority.ALWAYS);
            configModeFieldBox.setId("bodyBox");
            configModeFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);
            configModeFieldBox.setAlignment(Pos.CENTER_LEFT);

            HBox configBox = new HBox(configModeText, configModeFieldBox);
            configBox.setAlignment(Pos.CENTER_LEFT);
            configBox.setMinHeight(Stages.ROW_HEIGHT);

            Runnable showConfigModeMenu = () ->{
                Point2D p = configModeFieldBox.localToScene(0.0, 0.0);
                configModeContextMenu.setPrefWidth(configModeFieldBox.getLayoutBounds().getWidth());
    
                configModeContextMenu.show(configModeFieldBox,
                        5 + p.getX() + configModeFieldBox.getScene().getX() + configModeFieldBox.getScene().getWindow().getX(),
                        (p.getY() + configModeFieldBox.getScene().getY() + configModeFieldBox.getScene().getWindow().getY())
                                + configModeFieldBox.getLayoutBounds().getHeight() - 1);
            };

            configModeField.setOnMouseClicked(e->showConfigModeMenu.run());
            configModeBtn.setOnMouseClicked(e->showConfigModeMenu.run());
                

            Text advFileModeText = new Text(String.format("%-12s", "Config File"));
            advFileModeText.setFill(Stages.txtColor);
            advFileModeText.setFont(Stages.txtFont);

            HBox.setHgrow(advFileModeField, Priority.ALWAYS);

            Label advFileModeBtn = new Label("…");
            advFileModeBtn.setId("lblBtn");

       

            HBox advFileModeFieldBox = new HBox(advFileModeField, advFileModeBtn);
            HBox.setHgrow(advFileModeFieldBox, Priority.ALWAYS);
            advFileModeFieldBox.setAlignment(Pos.CENTER_LEFT);
            advFileModeFieldBox.setId("bodyBox");
            advFileModeFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);

            HBox advFileModeBox = new HBox(advFileModeText,  advFileModeFieldBox);
            advFileModeBox.setAlignment(Pos.CENTER_LEFT);
            advFileModeBox.setMinHeight(Stages.ROW_HEIGHT);



            advFileModeBtn.setOnMouseClicked(e -> {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Select Config");
                chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Config  (text)", "*.conf", "*.config", "*.cfg"));
                File file = chooser.showOpenDialog(m_appStage);
                if (file != null && file.isFile()) {
                    
                    advFileModeField.setText(file.getAbsolutePath());
                }
            });

            Text apiKeyText = new Text(String.format("%-12s", "API Key"));
            apiKeyText.setFill(Stages.txtColor);
            apiKeyText.setFont(Stages.txtFont);

            PasswordField apiKeyHidden = new PasswordField();
            HBox.setHgrow(apiKeyHidden, Priority.ALWAYS);

            Image eyeImg = new Image(AppConstants.SHOW_ICON);
            Image eyeOffImg = new Image(AppConstants.HIDE_ICON);

            ImageView btnImgView = new ImageView(eyeImg);
            btnImgView.setImage(eyeImg);
            btnImgView.setPreserveRatio(true);
            btnImgView.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);

            Button showApiKeyBtn = new Button();
            showApiKeyBtn.setPadding(new Insets(1));
            showApiKeyBtn.setGraphic(btnImgView);

            
            HBox.setHgrow(apiKeyField, Priority.ALWAYS);


            HBox apiKeyFieldBox = new HBox(apiKeyHidden, showApiKeyBtn);
            HBox.setHgrow(apiKeyFieldBox, Priority.ALWAYS);
            apiKeyFieldBox.setId("bodyBox");
            apiKeyFieldBox.setAlignment(Pos.CENTER_LEFT);
            apiKeyFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);


            apiKeyField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    apiKeyHidden.setText(apiKeyField.getText());
                }
            });

            apiKeyHidden.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal) {
                    apiKeyField.setText(apiKeyHidden.getText());
                }
            });

            showApiKeyBtn.setOnAction(e->{
                if(apiKeyFieldBox.getChildren().contains(apiKeyHidden)){
                    
                    apiKeyFieldBox.getChildren().remove(apiKeyHidden);
                    apiKeyFieldBox.getChildren().add(0, apiKeyField);
                    btnImgView.setImage(eyeOffImg);
                }else{
                    if(apiKeyFieldBox.getChildren().contains(apiKeyField)){
                        apiKeyFieldBox.getChildren().remove(apiKeyField);
                        apiKeyFieldBox.getChildren().add(0, apiKeyHidden);
                        btnImgView.setImage(eyeImg);
                        
                    }
                }
            });


            HBox apiKeyBox = new HBox(apiKeyText, apiKeyFieldBox);
            apiKeyBox.setAlignment(Pos.CENTER_LEFT);
            apiKeyBox.setMinHeight(Stages.ROW_HEIGHT);

            VBox configBodyBox = new VBox(configBox, apiKeyBox );
            
            configModeField.textProperty().addListener((obs,oldval,newval)->{
                switch(newval){
                    case DEFAULT_FULL_NODE:
                        if(configBodyBox.getChildren().contains(advFileModeBox)){
                            configBodyBox.getChildren().remove(advFileModeBox);
                        }
                    break;
                    case USER_CONFIG_FILE:
                        if(!configBodyBox.getChildren().contains(advFileModeBox)){
                            configBodyBox.getChildren().add(1, advFileModeBox);
                        }
                    break;
                }
            });
            


            Text directoryRootText = new Text(String.format("%-12s", "Folder "));
            directoryRootText.setFill(Stages.txtColor);
            directoryRootText.setFont(Stages.txtFont);

            
            directoryRootField.setText(m_appDirString);
            HBox.setHgrow(directoryRootField, Priority.ALWAYS);
  

            Label directoryRootOpenBtn = new Label("…");
            directoryRootOpenBtn.setId("lblBtn");

            Runnable openDirectoryBtn = ()->{

                File currentLocation = new File(m_appDirString);

                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Select Location");
                chooser.setInitialDirectory(currentLocation);

                File locationDir = chooser.showDialog(m_appStage);
                if (locationDir != null && locationDir.isDirectory()) {
                    directoryRootField.setText(locationDir.getAbsolutePath());
                }
            };

            directoryRootOpenBtn.setOnMouseClicked(e->{
                openDirectoryBtn.run();
            });

            HBox directoryRootFieldBox = new HBox(directoryRootField, directoryRootOpenBtn);
            HBox.setHgrow(directoryRootFieldBox, Priority.ALWAYS);
            directoryRootFieldBox.setAlignment(Pos.CENTER_LEFT);
            directoryRootFieldBox.setId("bodyBox");
            directoryRootFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);



            HBox directoryRootBox = new HBox(directoryRootText, directoryRootFieldBox);
            directoryRootBox.setAlignment(Pos.CENTER_LEFT);
            directoryRootBox.setMinHeight(Stages.ROW_HEIGHT);


            Label useableText = new Label("Available: ");
            useableText.setId("smallPrimaryColor");

            useableField.setId("smallPrimaryColor");
            useableField.setEditable(false);
            HBox.setHgrow(useableField, Priority.ALWAYS);
            useableField.setAlignment(Pos.CENTER_RIGHT);

            directoryRootField.textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal.length() > 1 && Utils.findPathPrefixInRoots(newVal)) {
                    
                    File prefixFolder = new File(FilenameUtils.getPrefix(newVal));

                    useableField.setText(Utils.formatedBytes(prefixFolder.getUsableSpace(), 2));
                } else {
                    useableField.setText("-");
                }
            });

            HBox useableBox = new HBox(useableText, useableField);
            useableBox.setMinHeight(Stages.ROW_HEIGHT);
            useableBox.setPadding(new Insets(0, 0, 0, 15));
            useableBox.setAlignment(Pos.CENTER_LEFT);


            Label requiredText = new Label("Required:");
            requiredText.setId("smallPrimaryColor");
            requiredText.setPadding(new Insets(0,17,0,0));

           

            TextField requiredField = new TextField();
            requiredField.setId("smallPrimaryColor");
            requiredField.setEditable(false);
            HBox.setHgrow(requiredField, Priority.ALWAYS);
            requiredField.setAlignment(Pos.CENTER_RIGHT);
            
            Binding<String> requiredSpaceBinding = Bindings.createObjectBinding(()->{
                long r = m_requiredSpaceLong.get();
                return Utils.formatedBytes(r, 2);
            }, m_requiredSpaceLong);
            requiredField.textProperty().bind(requiredSpaceBinding);

            /* JsonObject networkInfoNote = Utils.getCmdObject("getNetworkState");
            networkInfoNote.addProperty("networkId", ErgoExplorers.NETWORK_ID);
            networkInfoNote.addProperty("locationId", m_locationId);
            m_ergoNetworkInterface.sendNote(networkInfoNote, onSuccess->{
                JsonObject json = (JsonObject) onSuccess.getSource().getValue();
                try {
                    Files.writeString(AppConstants.LOG_FILE.toPath(), "networkState:" + json, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e1) {
        
                }
            },onFailed->{

            } );*/
            

        
            HBox requiredBox = new HBox(requiredText, requiredField);
            requiredBox.setMinHeight(Stages.ROW_HEIGHT);

            requiredBox.setPadding(new Insets(0, 0, 0, 15));
            requiredBox.setAlignment(Pos.CENTER_LEFT);

           
            VBox directorySpaceBox = new VBox(useableBox, requiredBox);
            directorySpaceBox.setId("bodyBox");
            
            HBox direcotrySpacePaddingBox = new HBox(directorySpaceBox);
            HBox.setHgrow(direcotrySpacePaddingBox, Priority.ALWAYS);
            direcotrySpacePaddingBox.setAlignment(Pos.CENTER);
            direcotrySpacePaddingBox.setPadding(new Insets(20,10,20,0));

            VBox directoryBox = new VBox( directoryRootBox, direcotrySpacePaddingBox);
            HBox.setHgrow(directoryBox, Priority.ALWAYS);

            Button nextBtn = new Button("Next");
            HBox nextBox = new HBox(nextBtn);
            nextBox.setAlignment(Pos.CENTER);
            HBox.setHgrow(nextBox, Priority.ALWAYS);


            defaultBodyBox = new VBox(nodeNameBox, configBodyBox, directoryBox, nextBox);

            nextBtn.setOnAction(e->{
                String configMode = configModeField.getText();
                String configFileString = advFileModeField.getText();
 
                boolean isDefault = configMode.equals(DEFAULT_FULL_NODE);

                File[] roots = Utils.getRoots();
              
                File configFile = !configMode.equals(DEFAULT_FULL_NODE) && configFileString.length() > 0 && Utils.findPathPrefixInRoots(roots, configFileString) ? new File(configFileString) : null; 
              
                if(!isDefault && (configFile == null || (configFile != null && !configFile.isFile()))){
                    errorTooltip.setText("Select valid config file");
                    showErrorTip(nextBtn);
                    return;
                }
                String configString  = null;
                try {
                   configString = isDefault ? null : Files.readString(configFile.toPath());
                } catch (IOException e1) {
                    errorTooltip.setText("Cannot read config file: " + e1.toString());
                    showErrorTip(nextBtn);
                    return;
                }
              
                String configFileName =  isDefault ?  DEFAULT_CONFIG_NAME : configFile.getName();
                
                String nodeName = nodeNameField.getText();
                String directoryString = directoryRootField.getText();

                if(nodeName.length() == 0 ){
                    errorTooltip.setText("Name required");
                    showErrorTip(nextBtn);
                    return;
                }
                

                if(directoryString.length() == 0 ){
                    errorTooltip.setText("Install location required");
                    showErrorTip(nextBtn);
                    return;
                }
                File installDir = new File(directoryString);
              
                
                if(!installDir.isDirectory()){
                    Alert installDirAlert = new Alert(AlertType.NONE, installDir.getAbsolutePath() + "\n\n" , ButtonType.OK, ButtonType.CANCEL );
                    installDirAlert.initOwner(m_appStage);
                    installDirAlert.setHeaderText("Create Directory");
                    installDirAlert.setTitle("Create Directory");
                    installDirAlert.setWidth(600);
                    Optional<ButtonType> result = installDirAlert.showAndWait();
                    if(result.isPresent() && result.get() != ButtonType.OK){
                        return;
                    }
                }
        
                long useableSpace = installDir.isDirectory() ? installDir.getUsableSpace() : new File(FilenameUtils.getPrefix(installDir.getAbsolutePath())).getUsableSpace();
                long requiredSpace = m_requiredSpaceLong.get();
               
         
                if (requiredSpace > useableSpace) {

                    errorTooltip.setText("Error: Drive space: " + Utils.formatedBytes(useableSpace, 2) + " - Required: " + Utils.formatedBytes(requiredSpace, 2));
                    showErrorTip(nextBtn);
                    return;
                } 


               
                currentBox.set((VBox)new FinalInstallNodeBox(nodeName, apiKeyField.getText(), configFileName, configString, installDir));
                    
                
            
            });
            
        }

       
        
        public class FinalInstallNodeBox extends VBox{
            private SimpleBooleanProperty getLatestBoolean;
    
         

            private Label latestJarRadio;
            private Text latestJarText;
            private TextField latestJarNameField;
            private TextField latestJarUrlField;
            private Tooltip downloadBtnTip;
            private Label downloadBtn;
            private Region btnSpacer;
            private HBox latestJarBox;
            private Text latestJarNameText;
            private HBox latestJarNameFieldBox;
            private HBox latestJarNameBox;
            private Text latestJarUrlText;
            private HBox latestJarUrlFieldBox;
            private HBox latestJarUrlBox;
            private Label selectJarRadio;
            private Text existingJarText;
            private HBox exisingFileHeadingBox;
            private Text jarFileText;
            private TextField appFileField;
            private Label appFileBtn;
            private HBox appFileFieldBox;
            private HBox jarFileBox;
            private Button installBtn;
            private HBox installBtnBox;
      

            public FinalInstallNodeBox(String nodeName, String apiKey, String configFileName, String configString,  File installDir){
                super();

                getLatestBoolean = new SimpleBooleanProperty(true);
                
                latestJarText = new Text(" Download");
                latestJarText.setFill(Stages.txtColor);
                latestJarText.setFont((Stages.txtFont));

                latestJarText.setOnMouseClicked(e -> {
                    getLatestBoolean.set(true);
                });

               
                latestJarNameField = new TextField("");
                latestJarNameField.setEditable(false);
                HBox.setHgrow(latestJarNameField, Priority.ALWAYS);

                latestJarUrlField = new TextField();
                latestJarUrlField.setEditable(false);
                HBox.setHgrow(latestJarUrlField, Priority.ALWAYS);


                Runnable getLatestUrl = () -> {
                    GitHubAPI gitHubAPI = new GitHubAPI("ergoplatform", "ergo");

                    gitHubAPI.getAssetsLatestRelease(getExecService(), (onSucceded)->{
                        Object assetsObject = onSucceded.getSource().getValue();
                        if(assetsObject != null && assetsObject instanceof GitHubAsset[] && ((GitHubAsset[]) assetsObject).length > 0){
                            GitHubAsset[] assets = (GitHubAsset[]) assetsObject;
                            GitHubAsset latestAsset = assets[0];

                            latestJarNameField.setText(latestAsset.getName());
                            latestJarUrlField.setText(latestAsset.getUrl());
                            getLatestBoolean.set(true);
                        }else{
                            latestJarNameField.setText("Unable to connect to GitHub (try again ->)");
                        }

                    }, onFailed -> {
                        latestJarNameField.setText("Unable to connect to GitHub (try again ->)");
                    });
                };

                downloadBtnTip = new Tooltip("Get GitHub Info");
                downloadBtnTip.setShowDelay(new Duration(200));

                downloadBtn = new Label("↺");
                downloadBtn.setId("lblBtn");
                downloadBtn.setTooltip(downloadBtnTip);
                downloadBtn.setOnMouseClicked(e -> getLatestUrl.run());
        
                getLatestUrl.run();

                latestJarRadio = new Label(Stages.RADIO_BTN);
                latestJarRadio.setId("logoLbl");

                btnSpacer = new Region();
                HBox.setHgrow(btnSpacer, Priority.ALWAYS);

                latestJarBox = new HBox(latestJarRadio, latestJarText, btnSpacer, downloadBtn);
                latestJarBox.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                    getLatestBoolean.set(true);
                });

                
               
                latestJarBox.setAlignment(Pos.CENTER_LEFT);
                latestJarBox.setMinHeight(Stages.ROW_HEIGHT);
               
                latestJarNameText = new Text(String.format("%-6s", "Name"));
                latestJarNameText.setFill(Stages.txtColor);
                latestJarNameText.setFont((Stages.txtFont));

                latestJarNameFieldBox = new HBox(latestJarNameField);
                HBox.setHgrow(latestJarNameFieldBox, Priority.ALWAYS);
                latestJarNameFieldBox.setId("bodyBox");
                latestJarNameFieldBox.setPadding(new Insets(0, 5, 0, 0));
                latestJarNameFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);
                latestJarNameFieldBox.setAlignment(Pos.CENTER_LEFT);

                latestJarNameBox = new HBox(latestJarNameText, latestJarNameFieldBox);
                latestJarNameBox.setAlignment(Pos.CENTER_LEFT);
                latestJarNameBox.setMinHeight(Stages.ROW_HEIGHT);

                latestJarUrlText = new Text(String.format("%-6s", "Url"));
                latestJarUrlText.setFill(Stages.txtColor);
                latestJarUrlText.setFont((Stages.txtFont));

                latestJarUrlFieldBox = new HBox(latestJarUrlField);
                HBox.setHgrow(latestJarUrlFieldBox, Priority.ALWAYS);
                latestJarUrlFieldBox.setId("bodyBox");
                latestJarUrlFieldBox.setPadding(new Insets(0, 5, 0, 0));
                latestJarUrlFieldBox.setMaxHeight(18);
                latestJarUrlFieldBox.setAlignment(Pos.CENTER_LEFT);
                
                latestJarUrlBox = new HBox(latestJarUrlText, latestJarUrlFieldBox);
                latestJarUrlBox.setAlignment(Pos.CENTER_LEFT);
                latestJarUrlBox.setMinHeight(Stages.ROW_HEIGHT);

                selectJarRadio = new Label(Stages.CIRCLE);
                selectJarRadio.setId("logoLbl");

                existingJarText = new Text( " Existing");
                existingJarText.setFill(Stages.txtColor);
                existingJarText.setFont((Stages.txtFont));
                existingJarText.setOnMouseClicked(e -> {
                    getLatestBoolean.set(false);
                });

                exisingFileHeadingBox = new HBox(selectJarRadio, existingJarText);
                exisingFileHeadingBox.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                    getLatestBoolean.set(false);
                });

     

               
                exisingFileHeadingBox.setAlignment(Pos.CENTER_LEFT);
                exisingFileHeadingBox.setPadding(new Insets(15,0,0,0));
               
                jarFileText = new Text(String.format("%-6s", "File"));
                jarFileText.setFill(Stages.txtColor);
                jarFileText.setFont((Stages.txtFont));


                
                appFileField = new TextField();
                HBox.setHgrow(appFileField, Priority.ALWAYS);

                appFileBtn = new Label("…");
                appFileBtn.setId("lblBtn");

                appFileFieldBox = new HBox(appFileField, appFileBtn);
                HBox.setHgrow(appFileFieldBox, Priority.ALWAYS);
                appFileFieldBox.setId("bodyBox");
                appFileFieldBox.setPadding(new Insets(0, 5, 0, 0));
                appFileFieldBox.setMaxHeight(18);
                appFileFieldBox.setAlignment(Pos.CENTER_LEFT);

                appFileBtn.setOnMouseClicked(e -> {

                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Select Ergo Node Jar");
                    chooser.getExtensionFilters().addAll(ErgoConstants.ERGO_JAR_EXT);

                    File appFile = chooser.showOpenDialog(m_appStage);
                    if (appFile != null) {
                        if (Utils.checkJar(appFile)) {
                            appFileField.setText(appFile.getAbsolutePath());
                            getLatestBoolean.set(false);
                        } else {
                            errorTooltip.setText("Cannot open file.");
                            showErrorTip(appFileBtn);
                            return;
                        }
                    }
                });

                jarFileBox = new HBox(jarFileText, appFileFieldBox);
                HBox.setHgrow(jarFileBox, Priority.ALWAYS);
                jarFileBox.setAlignment(Pos.CENTER_LEFT);
                jarFileBox.setMinHeight(Stages.ROW_HEIGHT);



                getLatestBoolean.addListener((obs, oldVal, newVal) -> {
                    if (newVal.booleanValue()) {
                        latestJarRadio.setText(Stages.RADIO_BTN);
                        selectJarRadio.setText(Stages.CIRCLE);
                    } else {
                        latestJarRadio.setText(Stages.CIRCLE);
                        selectJarRadio.setText(Stages.RADIO_BTN);
                    }
                });
                
               
         
   

                VBox latestJarBodyBox = new VBox(latestJarNameBox, latestJarUrlBox);
                latestJarBodyBox.setPadding(new Insets(15,0,0,30));

                VBox existingJarBodyBox = new VBox(jarFileBox);
                existingJarBodyBox.setPadding(new Insets(15,0,0,30));

               


                installBtn = new Button("Install");

                installBtnBox = new HBox(installBtn);
                installBtnBox.setAlignment(Pos.CENTER);
                installBtnBox.setPadding(new Insets(20, 0 ,0, 0));
                HBox.setHgrow(installBtnBox, Priority.ALWAYS);

                installBtn.setOnAction(e->{
                 

                    boolean isGetLatestApp = getLatestBoolean.get();
                    String appFileString = appFileField.getText();

                    File appFile = !isGetLatestApp && appFileString.length() > 0 ? new File(appFileString) : null;
                
                    if(!isGetLatestApp){
                        if(appFile == null){
                            errorTooltip.setText("Ergo (.jar) file required");
                            showErrorTip(installBtn);
                            return;
                        }else if(!Utils.checkJar(appFile)){
                            errorTooltip.setText(appFile.getName() + " is not a valid (.jar) file.");
                            showErrorTip(installBtn);
                            return;
                        }
                    }

                    
                    installBtn.setDisable(true);

                    NamedNodeUrl namedNode = new NamedNodeUrl("",nodeName,"127.0.0.1", ErgoConstants.MAINNET_PORT, apiKey, NetworkType.MAINNET);
            
                    addLocalNode(installDir, namedNode,!isGetLatestApp, appFile, configFileName, configString, onLocalNodeAdded->{
                        Object obj = onLocalNodeAdded.getSource().getValue();
                        JsonObject resultObj = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
                        if(resultObj != null){
                            JsonElement resultObjIdElement = resultObj.get("id");
                            String id  = resultObjIdElement != null ?resultObjIdElement.getAsString() : null;
                            
                            setDefault(id, onSuceeded->{   
            
                            }, onFailed->{});
                            m_currentBox.set(null);
                        }else{
                            installBtn.setDisable(false);
                        
                            errorTooltip.setText( "Failed to add node");
                            showErrorTip(installBtn);
                        }
                        
                    },onLocalNodeFailed->{
                        Throwable throwable = onLocalNodeFailed.getSource().getException();
                        String msg = throwable != null ? throwable.getMessage() : "Failed to add node";
                        installBtn.setDisable(false);
                        
                        errorTooltip.setText(msg);
                        showErrorTip(installBtn);
                    });
       
                    

                        
                      

                });

                FinalInstallNodeBox.this.getChildren().addAll(latestJarBox, latestJarBodyBox, exisingFileHeadingBox, existingJarBodyBox, installBtnBox);

            }

          
        }
    }



    private Future<?> addLocalNode(File dir, NamedNodeUrl namedNode, boolean isAppFile, File appFile, String configFileName, String configString, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        
        JsonObject note = NoteConstants.getCmdObject("addLocalNode");
        note.addProperty("networkId", ErgoConstants.NODE_NETWORK);
        note.addProperty("locationId", m_locationId);

        JsonObject json = new JsonObject();

        if(configString != null){
            json.addProperty("configText", configString);
        }
        json.addProperty("configFileName", configFileName);
        
        json.addProperty("isAppFile", isAppFile);
        if(isAppFile){
            if(appFile != null && appFile.isFile()){
                json.addProperty("appFile", appFile.getAbsolutePath());
            }
        }
        json.add("namedNode", namedNode.getJsonObject());
        json.addProperty("appDir", dir.getAbsolutePath());
        note.add("data", json);

        return m_ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);

    }

   

    private class RemoveNodesBox extends AppBox
    {
        private Label backButton;
        private Label headingText;
        private HBox headingBox;
        private VBox headerBox;
        private Region hBar;
        private HBox gBox;
        private VBox listBox;
        private ScrollPane listScroll;
        private HBox nodeListBox;

        private Button nextBtn;
        private HBox nextBox;

        private Button doneBtn;
        private HBox doneBox;

        private VBox bodyBox;
        private VBox layoutVBox;
        private Insets topPad;

        private JsonArray removeIds;


        public RemoveNodesBox(){

            this.removeIds = new JsonArray();

            this.backButton = new Label("🠜");
            this.backButton.setId("lblBtn");

            this.backButton.setOnMouseClicked(e -> {
                m_currentBox.set(null);
            });

            this.headingText = new Label("Remove Nodes");
            this.headingText.setFont(Stages.txtFont);
            this.headingText.setPadding(new Insets(0,0,0,15));

            this.headingBox = new HBox(backButton, headingText);
            this.headingBox.setAlignment(Pos.CENTER_LEFT);
            this.headingBox.setPadding(new Insets(10, 15, 0, 15));
            HBox.setHgrow(this.headingBox, Priority.ALWAYS);

            this.headerBox = new VBox(this.headingBox);
            this.headerBox.setPadding(new Insets(0, 5, 0, 0));


            this.hBar = new Region();
            this.hBar.setPrefWidth(400);
            this.hBar.setPrefHeight(2);
            this.hBar.setMinHeight(2);
            this.hBar.setId("hGradient");

            this.gBox = new HBox(this.hBar);
            this.gBox.setAlignment(Pos.CENTER);
            this.gBox.setPadding(new Insets(0, 0, 20, 0));

            this.listBox = new VBox();
            this.listBox.setPadding(new Insets(10,0,10,0));
            this.listBox.setId("bodyBox");

            this.listScroll = new ScrollPane(this.listBox);
            this.listScroll.setPrefViewportHeight(100);
            this.listScroll.setId("blackList");
            this.listScroll.setPadding(new Insets(0,10,0,0));
            this.nodeListBox = new HBox(this.listScroll);
            this.nodeListBox.setPadding(new Insets(0,0,0, 10));
            HBox.setHgrow(this.nodeListBox, Priority.ALWAYS);

          

            this.listScroll.prefViewportWidthProperty().bind(this.nodeListBox.widthProperty().subtract(1));

            this.listScroll.viewportBoundsProperty().addListener((obs,oldval,newval)->{
                this.listBox.setMinWidth(newval.getWidth());
                this.listBox.setMinHeight(newval.getHeight());
            });

            this.topPad = new Insets(20, 0, 20, 0);

            this.nextBtn = new Button("Remove");

            this.nextBox = new HBox(this.nextBtn);
            this.nextBox.setAlignment(Pos.CENTER);
            this.nextBox.setPadding(new Insets(0,0,20,0));

            this.doneBtn = new Button("Done");
            this.doneBtn.setId("toolBtn");
            this.doneBtn.setOnAction(e->{
                m_currentBox.set(null);
            });

            this.doneBox = new HBox(this.doneBtn);
            this.doneBox.setAlignment(Pos.BOTTOM_RIGHT);
            this.doneBox.setPadding(new Insets(5));
            HBox.setHgrow(doneBox, Priority.ALWAYS);

            this.bodyBox = new VBox(this.gBox, this.nodeListBox,this.doneBox, this.nextBox);
            VBox.setMargin(bodyBox, new Insets(10, 10, 0, 10));


            this.layoutVBox = new VBox(this.headerBox, this.bodyBox);

            


            Tooltip tooltip = new Tooltip();
          

            this.nextBtn.setOnAction(e->{
                if(removeIds.size() == 0){
                    Point2D p = this.nextBtn.localToScene(0.0, 0.0);
                    tooltip.setText("Select nodes");
                    tooltip.show(this.nextBtn,
                            p.getX() + this.nextBtn.getScene().getX()
                                    + this.nextBtn.getScene().getWindow().getX() - 60,
                            (p.getY() + this.nextBtn.getScene().getY()
                                    + this.nextBtn.getScene().getWindow().getY()) - 40);
                    PauseTransition pt = new PauseTransition(Duration.millis(1600));
                    pt.setOnFinished(ptE -> {
                        tooltip.hide();
                    });
                    pt.play();
                }else{

                    removeNodes(removeIds, onRemoved->{
                        
                        Object sourceObject = onRemoved.getSource().getValue();
                    
                        if(sourceObject != null && sourceObject instanceof JsonObject){
                            JsonObject json = (JsonObject) sourceObject;

                            JsonElement idsElement = json.get("ids");
                            if(idsElement == null){
            
                                Point2D p = this.nextBtn.localToScene(0.0, 0.0);
                                tooltip.setText("Unable to remove nodes");
                                tooltip.show(this.nextBtn,
                                        p.getX() + this.nextBtn.getScene().getX()
                                                + this.nextBtn.getScene().getWindow().getX() - 60,
                                        (p.getY() + this.nextBtn.getScene().getY()
                                                + this.nextBtn.getScene().getWindow().getY()) - 40);
                                PauseTransition pt = new PauseTransition(Duration.millis(1600));
                                pt.setOnFinished(ptE -> {
                                    tooltip.hide();
                                });
                                pt.play();
                            }

                        }else{
                            Point2D p = this.nextBtn.localToScene(0.0, 0.0);
                            tooltip.setText("Unable to process command");
                            tooltip.show(this.nextBtn,
                                    p.getX() + this.nextBtn.getScene().getX()
                                            + this.nextBtn.getScene().getWindow().getX() - 60,
                                    (p.getY() + this.nextBtn.getScene().getY()
                                            + this.nextBtn.getScene().getWindow().getY()) - 40);
                            PauseTransition pt = new PauseTransition(Duration.millis(1600));
                            pt.setOnFinished(ptE -> {
                                tooltip.hide();
                            });
                            pt.play();
                        }

                    }, onFailed->{});
    
                   
                }
            });

            getChildren().add(layoutVBox);

            update();



        }

        public void removeNodes(JsonArray removeIds, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
            JsonObject note = NoteConstants.getCmdObject("removeNodes");
            note.addProperty("locationId", m_locationId);
            note.addProperty("networkId", ErgoConstants.NODE_NETWORK);
            note.add("ids", removeIds);
            m_ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }
        
        @Override
        public void sendMessage(int code, long timestamp,String networkId, String msg){
            

            if(networkId != null && networkId.equals(ErgoConstants.NODE_NETWORK)){
                update();
            }
        }

        public Future<?> getNodes(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){

            JsonObject note = NoteConstants.getCmdObject("getNodes");
            note.addProperty("networkId", ErgoConstants.NODE_NETWORK);
            note.addProperty("locationId", m_locationId);

            return m_ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }
     
        public void update(){
            getNodes(onNodes->{
                Object onNodesObj = onNodes.getSource().getValue();

                JsonArray nodeArray = onNodesObj != null && onNodesObj instanceof JsonArray ? (JsonArray) onNodesObj : null;
          

                this.listBox.getChildren().clear();

                if (nodeArray != null) {

                    for (JsonElement jsonElement : nodeArray) {
            
                        JsonObject json = jsonElement != null && jsonElement.isJsonObject() ? jsonElement.getAsJsonObject() : null;

                        JsonElement idElement = json != null ? json.get("id") : null;
                        JsonElement nameElement = json != null ? json.get("name") : null;
                        JsonElement clientTypeElement = json != null ? json.get("clientType") : null;
                        JsonElement namedNodeElement = json != null ? json.get("namedNode") : null;


                        String id = idElement != null ? idElement.getAsString() : "";
                        String name = nameElement != null ? nameElement.getAsString() : "Unknown";
                        String clientType = clientTypeElement != null ? clientTypeElement.getAsString() : "Unknown";


                    
                        NamedNodeUrl namedNodeUrl = null;
                        
                        try{
                            namedNodeUrl = namedNodeElement != null ? new NamedNodeUrl(namedNodeElement.getAsJsonObject()) : null;
                        }catch(Exception e){

                        }

                        Label nameText = new Label(  ErgoConstants.getClientImg(clientType) + " " + name + " - " + (namedNodeUrl != null ? namedNodeUrl.getIP() +":" + namedNodeUrl.getPort() : "Null"));
                        nameText.setId("itemLbl");
                        nameText.setPadding(new Insets(0,0,0,7));

            
        

                        Runnable removeItemFromRemoveIds = () ->{
                            for(int i =0; i < this.removeIds.size() ; i++){
                                JsonElement element = this.removeIds.get(i);
                                JsonObject obj = element.getAsJsonObject();
                                String checkId = obj.get("id").getAsString();
                                if(checkId.equals(id)){
                                    this.removeIds.remove(i);
                                }
                            }
                        };
                        
                        Label selectedBox = new Label();
                        selectedBox.setId("clearBox");
                        
                    

                        HBox checkBoxBox = new HBox(selectedBox);
                        checkBoxBox.setPadding(new Insets(0,0,0,5));

                        HBox nodeItem = new HBox(checkBoxBox, nameText);
                        nodeItem.setAlignment(Pos.CENTER_LEFT);
                        nodeItem.setMinHeight(25);
                        HBox.setHgrow(nodeItem, Priority.ALWAYS);
                        nodeItem.setId("rowBtn");
                        nodeItem.setPadding(new Insets(2,5,5,0));

                        Runnable toggleCheck = ()->{
                            if(selectedBox.getText().equals("")){
                    
                                nodeItem.setId("rowBtnSelected");
                                
                                
                                for(int i =0; i < this.removeIds.size() ; i++){
                                    JsonElement element = this.removeIds.get(i);
                                    JsonObject obj = element.getAsJsonObject();
                                    String checkId = obj.get("id").getAsString();
                                    if(checkId.equals(id)){
                            
                                        this.removeIds.remove(i);
                                    }
                                }
                                JsonObject removeJson = NoteConstants.getJsonObject("id", id);
                            
                                if(clientType.equals(ErgoConstants.LOCAL_NODE)){
                                    Alert deleteAlert = new Alert(AlertType.NONE, "Delete all node files and folders?", ButtonType.YES, ButtonType.NO);
                                    deleteAlert.setHeaderText("Delete Node");
                                    deleteAlert.setTitle("Delete Node");

                                    Optional<ButtonType> result = deleteAlert.showAndWait();
                                    if(result.isPresent() && result.get() == ButtonType.YES){
                                        selectedBox.setText("🗑");
                                        removeJson.addProperty("isDelete", true);            
                                    }else{
                                        selectedBox.setText("✗");
                                    }
                                }else{
                                    selectedBox.setText("✗");
                                }
                                this.removeIds.add(removeJson);


                            }else{
                                selectedBox.setText("");
                                nodeItem.setId("rowBtn");
                                removeItemFromRemoveIds.run();
                            }
                        
                        };
                        
                        nodeItem.addEventFilter(MouseEvent.MOUSE_CLICKED, e->toggleCheck.run());

                        listBox.getChildren().add(nodeItem);
                        
                    }
                }
            }, onNodesFailed->{
                
            });
       
        }

        @Override
        public void shutdown(){
            getChildren().clear();
            
            this.backButton = null;
            this.headingText = null;
            this.headingBox = null;
            this.headerBox = null;
            this.hBar = null;
            this.gBox = null;
            this.listBox = null;
            this.listScroll = null;
            this.nodeListBox = null;

            this.nextBtn = null;
            this.nextBox = null;

            this.topPad = null;
        }
    }

    private class NodeConfigBox extends AppBox {


       // private SimpleObjectProperty<ErgoNodeConfigData> m_configData = new SimpleObjectProperty<>(null);

        private String m_configId;

        private String m_defaultText = "Local node";
        //private
        SimpleObjectProperty<NetworkType> m_networkTypeOption = new SimpleObjectProperty<NetworkType>(NetworkType.MAINNET);
        SimpleStringProperty m_clientTypeOption = new SimpleStringProperty(ErgoConstants.LOCAL_NODE);

        private SimpleObjectProperty<Image> m_icon = new SimpleObjectProperty<>(new Image(ErgoConstants.ERGO_NODES_ICON));
        private SimpleStringProperty m_nameProperty = new SimpleStringProperty("Local node");


        public void shutdown() {

        }

        @Override
        public void sendMessage(int code, long timeStamp,String networkId, String msg) {
            //TODO: ConfigData updated would you like to reload
            //getConfigData();

        }
        private TextField nodeNameField = new TextField();
     

        private void getConfigData()  {
            
        

        }

        private void updateConfigData(JsonObject data, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
            String nodeId = m_defaultNodeId.get();
            if(nodeId != null){
                JsonObject json = NoteConstants.getCmdObject("updateConfigData");
                json.addProperty("networkId", ErgoConstants.NODE_NETWORK);
                json.addProperty("configId", m_configId);
                json.addProperty("id", nodeId);
                json.add("data", data);
                m_ergoNetworkInterface.sendNote(json, onSucceeded, onFailed);
            }
        }

        

        public NodeConfigBox(String configId) {
            super();
            m_configId = configId;
            

            Label backButton = new Label("🠜");
            backButton.setId("lblBtn");

            backButton.setOnMouseClicked(e -> {
                m_currentBox.set(null);
            });

            Label headingText = new Label("Configure Node");
            headingText.setFont(Stages.txtFont);
            headingText.setPadding(new Insets(0,0,0,15));

            HBox headingBox = new HBox(backButton, headingText);
            headingBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(headingBox, Priority.ALWAYS);
            headingBox.setPadding(new Insets(10, 15, 0, 15));
     
            VBox headerBox = new VBox(headingBox);
            headerBox.setPadding(new Insets(0, 5, 0, 0));

            Region hBar = new Region();
            hBar.setPrefWidth(400);
            hBar.setPrefHeight(2);
            hBar.setMinHeight(2);
            hBar.setId("hGradient");

            HBox gBox = new HBox(hBar);
            gBox.setAlignment(Pos.CENTER);
            gBox.setPadding(new Insets(0, 0, 20, 0));
    


            Label nodeNameText = new Label("Name ");
            // nodeNameText.setFill(Stages.txtColor);
            nodeNameText.setFont(Stages.txtFont);
            nodeNameText.setMinWidth(70);

            
            HBox.setHgrow(nodeNameField, Priority.ALWAYS);

            nodeNameField.setEditable(false);



            HBox nodeNameFieldBox = new HBox(nodeNameField);
            HBox.setHgrow(nodeNameFieldBox, Priority.ALWAYS);
            nodeNameFieldBox.setId("bodyBox");
            nodeNameFieldBox.setPadding(new Insets(0, 5, 0, 0));
            nodeNameFieldBox.setMaxHeight(18);
            nodeNameFieldBox.setAlignment(Pos.CENTER_LEFT);

            Label nodeNameLbl = new Label();
            nodeNameLbl.setId("logoBtn");

            HBox nodeNameBox = new HBox(nodeNameLbl, nodeNameText, nodeNameFieldBox);
            nodeNameBox.setAlignment(Pos.CENTER_LEFT);
            nodeNameBox.setPadding(new Insets(2, 0, 2, 0));

            Label nodeFileText = new Label("File");
            nodeFileText.setFont(Stages.txtFont);
            nodeFileText.setMinWidth(70);

         
            nodeNameField.textProperty().addListener((obs,oldval,newval)->{
               //isUpdated
            });


            Label nodeFileLbl = new Label();
            nodeFileLbl.setId("logoBtn");

  
            HBox nodeFileBox = new HBox(nodeFileLbl, nodeFileText);
            nodeFileBox.setAlignment(Pos.CENTER_LEFT);
            nodeFileBox.setPadding(new Insets(2, 0, 2, 0));

            VBox settingsBodyBox = new VBox(nodeNameBox, nodeFileBox);
            settingsBodyBox.setPadding(new Insets(0, 5, 0, 30));
            HBox.setHgrow(settingsBodyBox, Priority.ALWAYS);

            getChildren().addAll(headerBox,gBox, settingsBodyBox);
            HBox.setHgrow(this, Priority.ALWAYS);

            Runnable setNodeConfigData = () -> {
               // ErgoNodeConfigData configData = m_configData.get();
                
            };

            setNodeConfigData.run();
           // m_configData.addListener((obs,oldval,newval)->setNodeConfigData.run());



            try {
                getConfigData();
            } catch (Exception e1) {
     
            }




        }

        public class LocalNodeConfigBox extends AppBox{
            private TextField nodeFileField = new TextField();
            

            public LocalNodeConfigBox(){
                super();
                nodeFileField.setEditable(false);
                HBox.setHgrow(nodeFileField, Priority.ALWAYS);

                Button nodeFileOpenLbl = new Button("…");
                nodeFileOpenLbl.setId("lblBtn");



                HBox nodeFileFieldBox = new HBox(nodeFileField, nodeFileOpenLbl);
                HBox.setHgrow(nodeFileFieldBox, Priority.ALWAYS);
                nodeFileFieldBox.setId("bodyBox");
                nodeFileFieldBox.setAlignment(Pos.CENTER_LEFT);
                nodeFileFieldBox.setMaxHeight(18);
                nodeFileFieldBox.setPadding(new Insets(0, 5, 0, 0));

                nodeFileOpenLbl.setOnAction(e -> {
                
                    FileChooser openFileChooser = new FileChooser();
                    openFileChooser.setTitle("Select node (*.jar)");
                    openFileChooser.getExtensionFilters().add(ErgoConstants.ERGO_WALLET_EXT);
                    openFileChooser.setSelectedExtensionFilter(ErgoConstants.ERGO_WALLET_EXT);

                    File nodeFile = openFileChooser.showOpenDialog(m_appStage);
                    
                    if(nodeFile != null && nodeFile.isFile()){
                        nodeFileField.setText(nodeFile.getAbsolutePath());

                    }
                
                });

                nodeFileField.textProperty().addListener((obs,oldval,newval)->{
                    //isUpdated
                });
            }
        }
      

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
                terminate();
            }else{
                run();
            }
        });

        m_topPropertiesBox = new VBox();
        HBox.setHgrow(m_topPropertiesBox, Priority.ALWAYS);
        m_updateTooltip.setShowDelay(Duration.millis(100));

        m_updateBtn = new BufferedButton(AppConstants.CLOUD_ICON, Stages.MENU_BAR_IMAGE_WIDTH);
        m_updateBtn.setOnAction(e->updateApp(onSucceeded->{}, onFailed->{}));
        m_updateBtn.setTooltip(m_updateTooltip);

        getStatusPaddingBox().getChildren().add(m_updateBtn);
    
        getChildren().add(1, m_topPropertiesBox);
    }

    public void updateApp(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        String nodeId = m_defaultNodeId.get();
        if(nodeId != null){
            JsonObject note = NoteConstants.getCmdObject("updateApp");
            note.addProperty("locationId", m_locationId);
            note.addProperty("networkId", ErgoConstants.NODE_NETWORK);
            note.addProperty("id", nodeId);
            
            m_ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }
    }

    public Future<?> getStatus(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        String nodeId = m_defaultNodeId.get();
        if(nodeId != null){
            JsonObject note = NoteConstants.getCmdObject("getStatus");
            note.addProperty("locationId", m_locationId);
            note.addProperty("id", nodeId);
            note.addProperty("networkId", ErgoConstants.NODE_NETWORK);

            return m_ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
        }else{
            return Utils.returnException("No node selected", getExecService(), onFailed);
        }
    }

    @Override
    public void getStatus(){
      
            getStatus(onSucceeded->{
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

    public void run(){
        String nodeId = m_defaultNodeId.get();
        if(nodeId != null){
            JsonObject note = NoteConstants.getCmdObject("run");
            note.addProperty("locationId", m_locationId);
            note.addProperty("networkId",ErgoConstants.NODE_NETWORK);
            note.addProperty("id", nodeId);
            m_ergoNetworkInterface.sendNote(note, onSuceeded->{}, onFailed->{});
        }
    }

    public void terminate(){
        String nodeId = m_defaultNodeId.get();
        if(nodeId != null){
            JsonObject note = NoteConstants.getCmdObject("terminate");
            note.addProperty("locationId", m_locationId);
            note.addProperty("networkId",ErgoConstants.NODE_NETWORK);
            note.addProperty("id", nodeId);
            m_ergoNetworkInterface.sendNote(note, onSuceeded->{}, onFailed->{});
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