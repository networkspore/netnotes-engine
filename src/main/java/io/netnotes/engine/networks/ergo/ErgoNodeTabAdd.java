package io.netnotes.engine.networks.ergo;

import org.ergoplatform.appkit.NetworkType;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.ContentTab;
import io.netnotes.engine.NamedNodeUrl;
import io.netnotes.engine.Stages;
import io.netnotes.engine.SubmitButton;
import io.netnotes.engine.Utils;
import io.netnotes.engine.apps.AppConstants;
import io.netnotes.friendly_id.FriendlyId;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;

import javafx.scene.control.Button;
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
import javafx.util.Duration;


public class ErgoNodeTabAdd extends ContentTab {
 
    private ErgoNetworkControl m_ergoNetworkControl;
    private JsonObject m_nodeJson = null;
    private TextField nodeNameField;
    private TextField hostField;
    private TextField networkTypeField;
    private PasswordField apiKeyHidden;
    private TextField apiKeyField;

    public ErgoNodeTabAdd(String nodeId,JsonObject nodeJson, String parentId, Image logo, ErgoNetworkControl ergoNetworkControl, SubmitButton submitButton){
        this(FriendlyId.createFriendlyId(), nodeId, nodeJson, parentId, logo, nodeJson == null ? "Add Node" : "Edit Node", new VBox(),ergoNetworkControl, submitButton);
    }
    
    public ErgoNodeTabAdd(String tabId, String nodeId, JsonObject nodeJson, String parentId, Image logo, String title, VBox layoutVBox, ErgoNetworkControl ergoNetworkControl, SubmitButton submitButton) {
        super(tabId, parentId, logo, title, layoutVBox);
        m_ergoNetworkControl = ergoNetworkControl;
        m_ergoNetworkControl.addNetworkConnection();
        m_nodeJson = nodeJson;


        Tooltip errorTooltip = new Tooltip();

        layoutVBox.setPrefWidth(ergoNetworkControl.getNetworksData().getContentTabs().bodyWidthProperty().get());
        layoutVBox.setPrefHeight(ergoNetworkControl.getNetworksData().getContentTabs().bodyHeightProperty().get());

        Label headingText = new Label(title);
        headingText.setFont(Stages.txtFont);
        headingText.setPadding(new Insets(0,0,0,15));

        Button undoBtn = new Button("𝧙");
        undoBtn.setId("lblBtn");


        HBox headingRightBox = new HBox(undoBtn);
        headingRightBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(headingRightBox, Priority.ALWAYS);

        HBox headingBox = new HBox(headingText, headingRightBox);
        headingBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headingBox, Priority.ALWAYS);
        headingBox.setPadding(new Insets(10, 15, 0, 15));
    
        VBox headerBox = new VBox(headingBox);
        headerBox.setPadding(new Insets(0, 5, 0, 0));





        Text nodeName = new Text(String.format("%-13s", "Name"));
        nodeName.setFill(Stages.txtColor);
        nodeName.setFont(Stages.txtFont);

        nodeNameField = new TextField();
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
        

        hostField = new TextField("");
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
        
        networkTypeField = new TextField(String.format("%-30s", NetworkType.MAINNET.toString()));
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

        apiKeyHidden = new PasswordField();
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

        apiKeyField = new TextField("");
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

        


        HBox nextBox = new HBox(submitButton);
        nextBox.setAlignment(Pos.CENTER);
        nextBox.setPadding(new Insets(15, 0, 15, 0));


        VBox bodyPaddingBox = new VBox(gBox, customClientOptionsBox,gBox2, nextBox);
        VBox.setMargin(bodyPaddingBox, new Insets(10, 10, 0, 20));


        layoutVBox.getChildren().addAll(headerBox, bodyPaddingBox);

    

        PauseTransition pt = new PauseTransition(Duration.millis(1600));
        pt.setOnFinished(ptE -> {
            errorTooltip.hide();
        });

        Runnable showErrorText = ()->{
            Point2D p = submitButton.localToScene(0.0, 0.0);

            

            errorTooltip.show(submitButton,
            p.getX() + submitButton.getScene().getX() + submitButton.getScene().getWindow().getX() -40,
            (p.getY() + submitButton.getScene().getY() + submitButton.getScene().getWindow().getY()
                    - submitButton.getLayoutBounds().getHeight()) - 10);
            
            
            pt.play();
        };

        submitButton.setOnAction(e->{
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

                            NamedNodeUrl namedNodeUrl = new NamedNodeUrl(nodeId, nameString, hostString, portNumber, apiKeyString, networkType);
              
                            
                            JsonObject json = new JsonObject();
                            json.addProperty("id", nodeId);
                            json.addProperty("name", nameString);
                            json.addProperty("clientType", ErgoNodeData.LIGHT_CLIENT);
                            json.add("namedNode", namedNodeUrl.getJsonObject());
                         
                      

                            Utils.returnObject(json, ergoNetworkControl.getExecService(), submitButton.getOnSubmit());
                   
                            
                        
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

        submitButton.setOnError(onError->{
            Throwable throwable = onError.getSource().getException();

            errorTooltip.setText(throwable != null ? "Ergo Nodes: " + throwable.getMessage() : "Ergo Nodes: Error submitting");
            showErrorText.run();
        });

        resetNodeInfo();
        undoBtn.setOnAction(e->resetNodeInfo());
    }

    public boolean isNodeJson(){
        return m_nodeJson != null;
    }

    public void resetNodeInfo(){
        JsonElement namedNodeElement = m_nodeJson == null ? null :  m_nodeJson.get("namedNode");
        if(namedNodeElement != null && namedNodeElement.isJsonObject()){
            JsonObject namedNodeJson = namedNodeElement.getAsJsonObject() ;

            m_ergoNetworkControl.createNamedNode(namedNodeJson, onNamedNode->{
                Object sourceObject = onNamedNode.getSource().getValue();
                
                updateNamedNodeInfo(sourceObject != null && sourceObject instanceof NamedNodeUrl ? (NamedNodeUrl) sourceObject : null);
                
            }, onError->{
                updateNamedNodeInfo(null);
            });
        }else{
            updateNamedNodeInfo(null);
        }
    }

    private void updateNamedNodeInfo(NamedNodeUrl namedNodeUrl){
        nodeNameField.setText(namedNodeUrl != null ? namedNodeUrl.getName() : "");;
        hostField.setText(namedNodeUrl != null ? namedNodeUrl.getIP() :  NamedNodeUrl.DEFAULT_NODE_IP);
        networkTypeField.setText(namedNodeUrl != null ? namedNodeUrl.getNetworkType().toString() : NetworkType.MAINNET.toString()) ;
        String apiKey = namedNodeUrl != null ? namedNodeUrl.getApiKey() : "";
        apiKeyHidden.setText(apiKey);;
        apiKeyField.setText(apiKey);;
    }


    @Override
    public void shutdown(){
        m_ergoNetworkControl.removeNetworkConnection();
    }
}

