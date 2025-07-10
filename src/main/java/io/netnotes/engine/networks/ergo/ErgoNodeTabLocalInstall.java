package io.netnotes.engine.networks.ergo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.ergoplatform.appkit.NetworkType;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.AppData;
import io.netnotes.engine.ContentTab;
import io.netnotes.engine.GitHubAPI;
import io.netnotes.engine.GitHubAPI.GitHubAsset;
import io.netnotes.engine.NamedNodeUrl;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.Stages;
import io.netnotes.engine.SubmitButton;
import io.netnotes.engine.Utils;
import io.netnotes.engine.apps.AppConstants;
import io.netnotes.friendly_id.FriendlyId;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Binding;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
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
import javafx.util.Duration;
import javafx.scene.Scene;
import javafx.stage.Window;

public class ErgoNodeTabLocalInstall extends ContentTab {
    public final String USER_CONFIG_FILE = "User: Config File";

    public final String DEFAULT_LOCAL_NODE_IP = "127.0.0.1";
    public final String DEFAULT_NODE_NAME = "Local Node";
    public final String DEFAULT_FULL_NODE = "Default: Full Node";
    public final String DEFAULT_NODE_FOLDER_NAME = DEFAULT_NODE_NAME;
    public final String DEFAULT_CONFIG_NAME = "ergo.conf";
    public final String DEFAULT_FOLDER =  AppData.HOME_DIRECTORY + "/.ergo";

    private SimpleObjectProperty<VBox> currentBox = new SimpleObjectProperty<>(null);
    private Tooltip errorTooltip = new Tooltip();
    private PauseTransition pt = null;
    
    private TextField configModeField = null;
    private TextField nodeNameField = null;
    private TextField advFileModeField = null;
    private TextField apiKeyField = new TextField("");
    private TextField directoryRootField = new TextField();
    private TextField useableField = new TextField(Utils.formatedBytes(AppData.HOME_DIRECTORY.getUsableSpace(), 2));
    private TextField ipTextField = null;
    private TextField portTextField = null;

    private VBox defaultBodyBox = null;
    private PasswordField apiKeyHidden = null;
    private HBox apiKeyFieldBox = null;
    private String m_appDirString = DEFAULT_FOLDER;
    private ErgoNetworkControl m_ergoNetworkControl = null;
    private Image eyeImg = new Image(AppConstants.SHOW_ICON);
    private Image eyeOffImg = new Image(AppConstants.HIDE_ICON);
    private ImageView btnImgView = null;

    private void showErrorTip(String msg, Node ownerNode){
        if(msg != null && msg.length() > 0){
            Point2D p = ownerNode.localToScene(0.0, 0.0);
            errorTooltip.setText(msg);
            int width = Utils.getStringWidth(msg);

            if(errorTooltip.isShowing()){
                errorTooltip.show(ownerNode,
                p.getX() + ownerNode.getScene().getX() + ownerNode.getScene().getWindow().getX() - (width/2),
                (p.getY() + ownerNode.getScene().getY() + ownerNode.getScene().getWindow().getY()
                        - ownerNode.getLayoutBounds().getHeight()) - 35);
            }
            if(pt == null){
                pt = new PauseTransition(Duration.millis(5000));
                pt.setOnFinished(e->{
                    errorTooltip.hide();
                    pt.setOnFinished(null);
                    pt = null;
                });
                 pt.play();
            }else{
                 pt.playFromStart();
            }
           
        }
    }

    private JsonObject m_localObject = null;
    private NamedNodeUrl m_localNamedNode = null;
  
    private SimpleLongProperty m_requiredSpaceLong = new SimpleLongProperty( ErgoConstants.REQUIRED_SPACE);

    private SubmitButton m_submitButton = null;

    private String m_nodeId = null;

    public ErgoNodeTabLocalInstall(String id, JsonObject localObject, String parentId, Image logo, ErgoNetworkControl ergoNetworkControl, SubmitButton submitButton){
        this(FriendlyId.createFriendlyId(), id, localObject, parentId, logo, localObject != null ? "Update local node" : "Add local node", new VBox(),ergoNetworkControl, submitButton);
    
    }
    
    public ErgoNodeTabLocalInstall( String tabId,String id, JsonObject localObject, String parentId, Image logo, String title, VBox layoutVBox, ErgoNetworkControl ergoNetworkControl, SubmitButton submitButton) {
        super(tabId, parentId, logo, title, layoutVBox);
        m_nodeId = id;
        m_ergoNetworkControl = ergoNetworkControl;
        m_ergoNetworkControl.addNetworkConnection();
        m_localObject = localObject;
        m_submitButton = submitButton;

        //configModeString


        Text nodeNameText = new Text(String.format("%-12s", "Name"));
        nodeNameText.setFill(Stages.txtColor);
        nodeNameText.setFont(Stages.txtFont);

        nodeNameField = new TextField();
        
        HBox.setHgrow(nodeNameField, Priority.ALWAYS);

        HBox nodeNameFieldBox = new HBox(nodeNameField);
        HBox.setHgrow(nodeNameFieldBox, Priority.ALWAYS);
        nodeNameFieldBox.setAlignment(Pos.CENTER_LEFT);
        nodeNameFieldBox.setId("bodyBox");
        nodeNameFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);

        HBox nodeNameBox = new HBox(nodeNameText, nodeNameFieldBox);
        nodeNameBox.setAlignment(Pos.CENTER_LEFT);
        nodeNameBox.setMinHeight(Stages.ROW_HEIGHT);

        Text ipText = new Text(String.format("%-12s", "Host IP"));
        ipText.setFont(Stages.txtFont);
        ipText.setFill(Stages.txtColor);

        ipTextField = new TextField( );

        HBox.setHgrow(ipTextField, Priority.ALWAYS);
        ipTextField.setEditable(false);
        ipTextField.setId("hand");

        portTextField = new TextField( );
        ipTextField.setId("logoBtn");

         portTextField.textProperty().addListener((obs, oldval, newVal) -> {

            if (!newVal.matches("\\d*")) {
                newVal = newVal.replaceAll("[^\\d]", "");

            }
            int intVal = Utils.isTextZero(newVal) ? 0 : Integer.parseInt(newVal);

            if (intVal > 65535) {
                intVal = 65535;
            }

            portTextField.setText(intVal + "");

        });

        portTextField.focusedProperty().addListener((obs, oldval, newVal) -> {
            if (!newVal) {
                String portString = portTextField.getText();
                int intVal = Integer.parseInt(portString);

                if (intVal < 1025) {
                    /*(if (networkTypeOption.get().equals(NetworkType.TESTNET)) {
                        nodePortField.setText(ErgoNodes.TESTNET_PORT + "");
                    } else {*/
                        portTextField.setText(ErgoNodes.MAINNET_PORT + "");
                    //}

                    showErrorTip("Minimum value: " + ErgoNodes.MAINNET_PORT + "", portTextField);
                }

            }
        });



        Button defaultIpBtn = new Button("↩");
        defaultIpBtn.setId("caretBtn");
        Label separatorBox = new Label(":");
         HBox ipTextFieldBox = new HBox(ipTextField,separatorBox, portTextField, defaultIpBtn);
        HBox.setHgrow(ipTextFieldBox, Priority.ALWAYS);
        ipTextFieldBox.setId("bodyBox");
        ipTextFieldBox.setMaxHeight(Stages.MAX_ROW_HEIGHT);
        ipTextFieldBox.setAlignment(Pos.CENTER_LEFT);

        defaultIpBtn.setOnAction(e->{
            portTextField.setText(getResetNodePort() + "");
            ipTextField.setText(getResetNodeIP());
        });



        Text configModeText = new Text(String.format("%-12s", "Mode"));
        configModeText.setFont(Stages.txtFont);
        configModeText.setFill(Stages.txtColor);

        configModeField = new TextField( );

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

        advFileModeField = new TextField( );

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
            Scene scene = layoutVBox.getScene();
            Window window = scene != null ? scene.getWindow() : null;

            if(window != null){
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Select Config");
                chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Config  (text)", "*.conf", "*.config", "*.cfg"));
                File file = chooser.showOpenDialog(window);
                if (file != null && file.isFile()) {
                    
                    advFileModeField.setText(file.getAbsolutePath());
                }
            }
        });

        Text apiKeyText = new Text(String.format("%-12s", "API Key"));
        apiKeyText.setFill(Stages.txtColor);
        apiKeyText.setFont(Stages.txtFont);

        apiKeyHidden = new PasswordField();
        HBox.setHgrow(apiKeyHidden, Priority.ALWAYS);

        


        btnImgView = new ImageView(eyeImg);
        btnImgView.setImage(eyeImg);
        btnImgView.setPreserveRatio(true);
        btnImgView.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);

        Button showApiKeyBtn = new Button();
        showApiKeyBtn.setPadding(new Insets(1));
        showApiKeyBtn.setGraphic(btnImgView);

        
        HBox.setHgrow(apiKeyField, Priority.ALWAYS);


        apiKeyFieldBox = new HBox(apiKeyHidden, showApiKeyBtn);
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

        showApiKeyBtn.setOnAction(e->toggleShowApiKey());


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
            Scene scene = layoutVBox.getScene();
            Window window = scene != null ? scene.getWindow() : null;

            if(window != null){
                File currentLocation = new File(m_appDirString);

                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Select Location");
                chooser.setInitialDirectory(currentLocation);

                File locationDir = chooser.showDialog(window);
                if (locationDir != null && locationDir.isDirectory()) {
                    directoryRootField.setText(locationDir.getAbsolutePath());
                }
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
           
                showErrorTip("Select valid config file", nextBtn);
                return;
            }
            String configString  = null;
            try {
                configString = isDefault ? null : Files.readString(configFile.toPath());
            } catch (IOException e1) {
                showErrorTip("Cannot read config file: " + e1.toString(), nextBtn);
                return;
            }
            
            String configFileName =  isDefault ?  DEFAULT_CONFIG_NAME : configFile.getName();
            
            String nodeName = nodeNameField.getText();
            String directoryString = directoryRootField.getText();

            if(nodeName.length() == 0 ){
                showErrorTip("Name required", nextBtn);
                return;
            }
            

            if(directoryString.length() == 0 ){
                showErrorTip("Install location required", nextBtn);
                return;
            }

            File installDir = new File(directoryString);
            
            
            if(!installDir.isDirectory()){
                Scene scene = layoutVBox.getScene();
                Window window = scene != null ? scene.getWindow() : null;

                Alert installDirAlert = new Alert(AlertType.NONE, installDir.getAbsolutePath() + "\n\n" , ButtonType.OK, ButtonType.CANCEL );
                if(window != null){
                    installDirAlert.initOwner(window);
                }
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

                showErrorTip("Error: Drive space: " + Utils.formatedBytes(useableSpace, 2) + " - Required: " + Utils.formatedBytes(requiredSpace, 2), nextBtn);
                return;
            } 
            String portText = portTextField.getText();

            int port = 0;

            FinalInstallNodeBox finalInstallBox = new FinalInstallNodeBox(nodeName,ipTextField.getText(), port, apiKeyField.getText(), configFileName, configString, installDir.getAbsolutePath());
            
            currentBox.set(finalInstallBox);
                
            
        
        });

     

        currentBox.addListener((obs,oldval,newval)->{
            if(oldval != null && oldval instanceof FinalInstallNodeBox){
                FinalInstallNodeBox finstall = (FinalInstallNodeBox) oldval;
                finstall.shutdown();
            }
            layoutVBox.getChildren().clear();
            if(newval == null){
                layoutVBox.getChildren().add(defaultBodyBox);
            }else{
                layoutVBox.getChildren().add(newval);
            }
        });

        if(currentBox.get() == null){
            currentBox.set(defaultBodyBox);
        }
  
        resetConfigData();
        
        
    }


    public int getResetNodePort(){
        return m_localNamedNode != null ? m_localNamedNode.getPort()  : ErgoConstants.MAINNET_PORT ;
    }

    public String getResetNodeIP(){
        return m_localNamedNode != null?  m_localNamedNode.getIP() : DEFAULT_LOCAL_NODE_IP;
    }

    private JsonObject getConfigObject(){
        if(m_localObject != null){
            JsonElement configElement = m_localObject.get("config");
            return configElement != null && configElement.isJsonObject() ? configElement.getAsJsonObject() : null;

        }else{
            return null;
        }
    }
    private String getConfigText(JsonObject json){
        JsonElement configTextElement = json != null ? json.get("configText") : null;
        return configTextElement != null && !configTextElement.isJsonNull() ? configTextElement.getAsString() : null;
    
    }

    private String getConfigFileName(JsonObject json){
        JsonElement configFileNameElement = json != null ? json.get("configFileName") : null;
        return configFileNameElement != null && !configFileNameElement.isJsonNull() ? configFileNameElement.getAsString() : null;
    
    }

    private String getPrevAppDir(){
        JsonElement appDirElement = m_localObject != null ? m_localObject.get("appDir") : null;
        return appDirElement != null && !appDirElement.isJsonNull() ? appDirElement.getAsString() : null;
    }


    public void resetConfigData(){
        boolean isLocalData = m_localNamedNode != null;
        JsonObject configObject = isLocalData ? getConfigObject() : null;

        String localObjectName = isLocalData ? NoteConstants.getJsonName(m_localObject) : null;
        String configFileName = configObject != null ? getConfigFileName(configObject) : null;
        String configText = configObject != null ? getConfigText(configObject) : null;
        String appDir = isLocalData ? getPrevAppDir() : null;

        String defaultNodeName = localObjectName != null ? localObjectName :  DEFAULT_NODE_NAME;
        String configModeString = isLocalData ? USER_CONFIG_FILE :  DEFAULT_FULL_NODE ; 
        String defaultNodeIp = getResetNodeIP();
        int defaultNodePort = getResetNodePort();



        String defaultApiKey = isLocalData  ? m_localNamedNode.getApiKey() : "";
    
        m_appDirString = isLocalData && appDir != null ? appDir : DEFAULT_FOLDER;
        
        directoryRootField.setText(m_appDirString);

        ipTextField.setText(defaultNodeIp);
        portTextField.setText(defaultNodePort + "");
        nodeNameField.setText(defaultNodeName);
        configModeField.setText( configModeString);
        apiKeyHidden.setText(defaultApiKey);
        if(isLocalData && configFileName != null && appDir != null){
            String filePath = appDir + "/" + configFileName;
            File configFile = new File(filePath);
            if(configFile.isFile()){
                advFileModeField.setText(filePath);
            }
        }

    }

    private boolean isApiKeyVisible(){
        return apiKeyFieldBox.getChildren().contains(apiKeyHidden);
    }

    

    public void toggleShowApiKey(){
        if(isApiKeyVisible()){
            
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
            private HBox installBtnBox;
      

        public FinalInstallNodeBox(String nodeName, String ip, int port, String apiKey, String configFileName, String configString,  String installDirString){
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

                gitHubAPI.getAssetsLatestRelease(m_ergoNetworkControl.getExecService(), (onSucceded)->{
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
                Scene scene = getPane().getScene();
                Window window = scene != null ? scene.getWindow() : null;
                if(window != null){
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Select Ergo Node Jar");
                    chooser.getExtensionFilters().addAll(ErgoConstants.ERGO_JAR_EXT);
              
                    File appFile = chooser.showOpenDialog(window);
                    if (appFile != null) {
                        if (Utils.checkJar(appFile)) {
                            appFileField.setText(appFile.getAbsolutePath());
                            getLatestBoolean.set(false);
                        } else {
                         
                            showErrorTip("Cannot open file.", appFileBtn);
                            return;
                        }
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

            


     

            installBtnBox = new HBox(m_submitButton);
            installBtnBox.setAlignment(Pos.CENTER);
            installBtnBox.setPadding(new Insets(20, 0 ,0, 0));
            HBox.setHgrow(installBtnBox, Priority.ALWAYS);

            m_submitButton.setOnAction(e->{
                

                boolean isGetLatestApp = getLatestBoolean.get();
                String appFileString = appFileField.getText();

                File appFile = !isGetLatestApp && appFileString.length() > 0 ? new File(appFileString) : null;
            
                if(!isGetLatestApp){
                    if(appFile == null){
                        showErrorTip("Ergo (.jar) file required", m_submitButton);
                        return;
                    }else if(!Utils.checkJar(appFile)){
                        showErrorTip(appFile.getName() + " is not a valid (.jar) file.", m_submitButton);
                        return;
                    }
                }

                
                m_submitButton.setDisable(true);
                NamedNodeUrl namedNode = new NamedNodeUrl(m_nodeId, nodeName,ip, port, apiKey, NetworkType.MAINNET);
                JsonObject nodeObject = ErgoNetworkControl.getLocalNodeObject(installDirString, namedNode,!isGetLatestApp, appFileString, configFileName, configString);             

                Utils.returnObject(nodeObject, m_ergoNetworkControl.getExecService(), m_submitButton.getOnSubmit());
            });

            m_submitButton.setOnError(onError->{
                Throwable throwable = onError.getSource().getException();
                showErrorTip("Error: " + (throwable != null ? throwable.getMessage() : m_submitButton.getText() + " failed"), m_submitButton);
                m_submitButton.setDisable(false);
            });

            FinalInstallNodeBox.this.getChildren().addAll(latestJarBox, latestJarBodyBox, exisingFileHeadingBox, existingJarBodyBox, installBtnBox);
            resetData();
        }

        private String getAppFileName(){
            if(m_localObject != null){
                JsonElement appFileNameElement = m_localObject.get("appFileName");
                return appFileNameElement != null && !appFileNameElement.isJsonNull() ? appFileNameElement.getAsString() : null;
            }
            return null;
        }

        private void resetData(){
            boolean isEdit = m_localObject != null;
            String appFileNameString = isEdit ? getAppFileName() : null;
            String appDir = isEdit ? getPrevAppDir() : null;

            String appFilePath = appDir != null && appFileNameString != null ? appDir + "/" + appFileNameString : null;

            File appFile = appFilePath != null ? new File(appFilePath) : null;
            appFileField.setText(appFile != null && appFile.isFile() ?  appFilePath : "");


        }

        public void shutdown(){
            if(installBtnBox != null){
                installBtnBox.getChildren().clear();
            }
            if(m_submitButton != null){
                m_submitButton.setOnAction(null);
                m_submitButton.setOnError(null);
                m_submitButton.setDisable(false);
            }
        }
        
    }

    @Override
    public void shutdown(){
        currentBox.set(null);
        m_ergoNetworkControl.removeNetworkConnection();
    }

}
