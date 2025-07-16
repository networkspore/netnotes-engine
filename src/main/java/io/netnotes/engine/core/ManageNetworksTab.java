package io.netnotes.engine.core;



import java.util.Map;

import io.netnotes.engine.AppConstants;
import io.netnotes.engine.BufferedButton;
import io.netnotes.engine.Network;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NetworkLocation;
import io.netnotes.engine.NoteBytes;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.Stages;
import io.netnotes.engine.TabAppBox;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;


public class ManageNetworksTab extends TabAppBox {
        public static final String NAME = "Manage Networks";
        public static final NoteBytes ID = new NoteBytes(NAME);
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

                        if(!network.isAvailable(NoteConstants.NETWORKS, networkInformation.getNetworkId())){
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
                NoteBytes networkId = info != null ? info.getNetworkId() : null;
                
                if (networkId != null){
                    m_installItemInformation.set(null);
                    network.installNetwork(networkId);
                    if(network.currentNetworkIdProperty().get() == null){
                        network.currentNetworkIdProperty().set(networkId);
                    }
                }
            });
            
        }

        @Override
        public void sendMessage(int code, long timestamp, NoteBytes networkId, String msg) {
             if(code == NoteConstants.LIST_ITEM_ADDED || code == NoteConstants.LIST_ITEM_REMOVED || code == NoteConstants.LIST_CHANGED || code == NoteConstants.LIST_UPDATED){
            if(networkId.equals(NetworksData.NETWORKS)){
                updateNetworkList();
            }
        }
        }

    
        public void updateNetworkList(){

            m_listBox.getChildren().clear();
    
            if(m_networksData.isNetworkInstalled()){
                for (Map.Entry<NoteBytes, NetworkLocation> entry : m_networksData.getNetworkLocations().entrySet()) {
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
                            NoteBytes currentNetworkId = m_networksData.currentNetworkIdProperty().get();
                            boolean selectedNetwork = currentNetworkId != null && currentNetworkId.equals(network.getNetworkId());         
                        
                            if(selectedNetwork){
                                m_networksData.currentNetworkIdProperty().set(null);
                            }else{
                                m_networksData.currentNetworkIdProperty().set(network.getNetworkId());
                                m_networksData.save();
                            }
                        });

                    
                

                        Runnable updateSelectedSwitch = () ->{
                            NoteBytes currentNetworkId = m_networksData.currentNetworkIdProperty().get();
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