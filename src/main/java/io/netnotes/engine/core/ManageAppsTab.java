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
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.beans.binding.Binding;



public class ManageAppsTab extends TabAppBox  {
    public static final int PADDING = 10;
    public static final String NAME = "Manage Apps";
    public static final NoteBytes ID = new NoteBytes(NAME);
    
    private final String installDefaultText = "(Install App)";

    private MenuButton m_installMenuBtn;

    private SimpleObjectProperty<NoteBytes> m_selectedAppId = new SimpleObjectProperty<>(null);
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
                
                    if(!network.isAvailable(NoteConstants.APPS, networkInformation.getNetworkId()) ){
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
            NoteBytes networkId = info != null ? info.getNetworkId() : null;
            
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
            for (Map.Entry<NoteBytes, NetworkLocation> entry : m_networksData.getNetworkLocations().entrySet()) {
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
                
                if(! m_networksData.isAvailable(NoteConstants.APPS, networkInformation.getNetworkId())){

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
    public void sendMessage(int code, long timestamp, NoteBytes networkId, String msg){
        if(code == NoteConstants.LIST_ITEM_ADDED || code == NoteConstants.LIST_ITEM_REMOVED || code == NoteConstants.LIST_CHANGED || code == NoteConstants.LIST_UPDATED){
            if(networkId.equals(NetworksData.APPS)){
                updateAppList();
            }
        }
    }

    

    @Override
    public void shutdown(){
        this.prefWidthProperty().unbind();
    }

  
}
