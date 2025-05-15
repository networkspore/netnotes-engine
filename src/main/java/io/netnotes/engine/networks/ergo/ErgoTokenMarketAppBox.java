package io.netnotes.engine.networks.ergo;

import java.util.concurrent.Future;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netnotes.engine.AppBox;
import io.netnotes.engine.JsonParametersBox;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.Stages;
import io.netnotes.engine.apps.AppConstants;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class ErgoTokenMarketAppBox extends AppBox {
    private final String selectString = "[disabled]";

    private Stage m_appStage;
    private SimpleObjectProperty<AppBox> m_currentBox = new SimpleObjectProperty<>(null);
    private NoteInterface m_ergoNetworkInterface;
    private VBox m_mainBox;
    
    private SimpleBooleanProperty m_showInformation = new SimpleBooleanProperty(false);
    private SimpleObjectProperty<NoteInterface> m_selectedTokenMarket = new SimpleObjectProperty<>(null);

    private String m_locationId = null;

    private HBox m_tokenMarketsFieldBox;
    private ImageView m_menuBtnImgView;
    private MenuButton m_tokenMarketsMenuBtn;
    private Button m_disableBtn;
    private NetworksData m_networksData;
    
    public ErgoTokenMarketAppBox(Stage appStage, String locationId,NetworksData networksData, NoteInterface ergoNetworkInterface){
        super();
        m_ergoNetworkInterface = ergoNetworkInterface;
        m_appStage = appStage;
        m_locationId = locationId;
        m_networksData = networksData;
    
        ImageView logoIconView = new ImageView(new Image(ErgoConstants.ERGO_MARKETS_ICON));
        logoIconView.setPreserveRatio(true);
        logoIconView.setFitHeight(18);

        
        HBox topIconBox = new HBox(logoIconView);
        topIconBox.setAlignment(Pos.CENTER_LEFT);
        topIconBox.setMinWidth(30);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
       
        ImageView closeImage = Stages.highlightedImageView(Stages.closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);
      
        Button toggleShowOptions = new Button(m_showInformation.get() ? "⏷" : "⏵");
        toggleShowOptions.setId("caretBtn");
        toggleShowOptions.setOnAction(e->{
            m_showInformation.set(!m_showInformation.get());
        });

        MenuButton marketMenuBtn = new MenuButton("⋮");


        Text topLogoText = new Text(String.format("%-13s", "Token Market"));
        topLogoText.setFont(Stages.txtFont);
        topLogoText.setFill(Stages.txtColor);

        m_menuBtnImgView = new ImageView();
        m_menuBtnImgView.setPreserveRatio(true);
        m_menuBtnImgView.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);

        m_tokenMarketsMenuBtn = new MenuButton();
        m_tokenMarketsMenuBtn.setId("arrowMenuButton");
        m_tokenMarketsMenuBtn.setContentDisplay(ContentDisplay.LEFT);
        m_tokenMarketsMenuBtn.setGraphic(m_menuBtnImgView);
        m_tokenMarketsMenuBtn.showingProperty().addListener((obs,oldval,newval)->{
            if(newval){
                updateMarkets();
            }
        });

        m_tokenMarketsFieldBox = new HBox(m_tokenMarketsMenuBtn);
        HBox.setHgrow(m_tokenMarketsFieldBox, Priority.ALWAYS);
        m_tokenMarketsFieldBox.setAlignment(Pos.CENTER_LEFT);
        m_tokenMarketsFieldBox.setId("bodyBox");
        m_tokenMarketsFieldBox.setPadding(new Insets(0, 1, 0, 0));
        m_tokenMarketsFieldBox.setMaxHeight(18);

        m_tokenMarketsMenuBtn.prefWidthProperty().bind(m_tokenMarketsFieldBox.widthProperty().subtract(1));

        HBox marketMenuBtnPadding = new HBox(marketMenuBtn);
        marketMenuBtnPadding.setPadding(new Insets(0, 0, 0, 5));



        HBox tokenMarketsBtnBox = new HBox(m_tokenMarketsFieldBox, marketMenuBtnPadding);
        tokenMarketsBtnBox.setPadding(new Insets(2, 2, 0, 5));
        HBox.setHgrow(tokenMarketsBtnBox, Priority.ALWAYS);

        VBox marketsBodyPaddingBox = new VBox();
        HBox.setHgrow(marketsBodyPaddingBox, Priority.ALWAYS);
        marketsBodyPaddingBox.setPadding(new Insets(0,10,0,0));





        HBox topBar = new HBox(toggleShowOptions, topIconBox, topLogoText, tokenMarketsBtnBox);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(2));

        VBox layoutBox = new VBox(topBar, marketsBodyPaddingBox);
        HBox.setHgrow(layoutBox, Priority.ALWAYS);

         SimpleBooleanProperty showMarketInfo = new SimpleBooleanProperty(false);

        JsonParametersBox marketInfoParamBox = new JsonParametersBox(NoteConstants.getJsonObject("marketInformation", "disabled"), 150);
        marketInfoParamBox.setPadding(new Insets(2,0,0,5));

        Button toggleShowMarketInfo = new Button(showMarketInfo.get() ? "⏷" : "⏵");
        toggleShowMarketInfo.setId("caretBtn");
        toggleShowMarketInfo.setOnAction(e -> showMarketInfo.set(!showMarketInfo.get()));
      
        Label infoLbl = new Label("🛈");
        infoLbl.setId("logoBtn");

        Text marketInfoText = new Text("Market Information");
        marketInfoText.setFont(Stages.txtFont);
        marketInfoText.setFill(Stages.txtColor);

        HBox toggleMarketInfoBox = new HBox(toggleShowMarketInfo, infoLbl, marketInfoText);
        HBox.setHgrow(toggleMarketInfoBox, Priority.ALWAYS);
        toggleMarketInfoBox.setAlignment(Pos.CENTER_LEFT);
        toggleMarketInfoBox.setPadding(new Insets(0,0,2,0));

        VBox marketInfoVBox = new VBox(toggleMarketInfoBox);
        marketInfoVBox.setPadding(new Insets(2));

        showMarketInfo.addListener((obs,oldval,newval)->{
            toggleShowMarketInfo.setText(newval ? "⏷" : "⏵");
            marketInfoVBox.getChildren().add(marketInfoParamBox);
        });

        VBox bodyBox = new VBox(marketInfoVBox);
        marketInfoVBox.setPadding(new Insets(5,0,0,5));

        Runnable setMarketInfo = ()->{
            NoteInterface marketInterface = m_selectedTokenMarket.get();
            marketInterface.sendNote(NoteConstants.getCmdObject("getNetworkObject"), (onNetworkObject)->{
                Object obj = onNetworkObject.getSource().getValue();
                m_tokenMarketsMenuBtn.hide();
                if(obj != null && obj instanceof JsonObject){
                    JsonObject marketJson = (JsonObject) obj;
                    JsonElement nameElement = marketJson.get("name");
                    m_tokenMarketsMenuBtn.setText(nameElement != null && !nameElement.isJsonNull() ? nameElement.getAsString() : "(Unknown market)");
                    NoteConstants.getAppIconFromNetworkObject(marketJson, getNetworksData().getExecService(), onImage ->{
                        Object imageObj = onImage.getSource().getValue();
                        if(imageObj != null && imageObj instanceof Image){
                            m_menuBtnImgView.setImage((Image)imageObj);
                        }else{
                            m_menuBtnImgView.setImage(Stages.unknownImg);
                        }
                    }, onFailed->{
                        m_menuBtnImgView.setImage(Stages.unknownImg);
                    });
                    
                    marketInfoParamBox.update(  marketJson);
                    if (!m_tokenMarketsFieldBox.getChildren().contains(m_disableBtn)) {
                        m_tokenMarketsFieldBox.getChildren().add(m_disableBtn);
                    }
                    
                }else{
                    m_tokenMarketsMenuBtn.setText(selectString);
                    m_menuBtnImgView.setImage(Stages.unknownImg);
                    marketInfoParamBox.update(NoteConstants.getJsonObject("marketInformation", "disabled"));
                    if (m_tokenMarketsFieldBox.getChildren().contains(m_disableBtn)) {
                        m_tokenMarketsFieldBox.getChildren().remove(m_disableBtn);
                    }
                }
            },(onObjectFailed)->{
                m_tokenMarketsMenuBtn.setText(selectString);
                m_menuBtnImgView.setImage(Stages.unknownImg);
                marketInfoParamBox.update(NoteConstants.getJsonObject("marketInformation", "disabled"));
                if (m_tokenMarketsFieldBox.getChildren().contains(m_disableBtn)) {
                    m_tokenMarketsFieldBox.getChildren().remove(m_disableBtn);
                }
            });

        };
        setMarketInfo.run();
      
        
        m_showInformation.addListener((obs, oldval, newval) -> {

            toggleShowOptions.setText(newval ? "⏷" : "⏵");

            if (newval) {
                if (!marketsBodyPaddingBox.getChildren().contains(bodyBox)) {
                    marketsBodyPaddingBox.getChildren().add(bodyBox);
                }
            } else {
                if (marketsBodyPaddingBox.getChildren().contains(bodyBox)) {
                    marketsBodyPaddingBox.getChildren().remove(bodyBox);
                }
            }
        });

        m_selectedTokenMarket.addListener((obs,oldval,newval)->{
            setMarketInfo.run();
        });


        m_disableBtn = new Button("☓");
        m_disableBtn.setId("lblBtn");

        m_disableBtn.setOnMouseClicked(e -> {
            m_showInformation.set(false);
            clearDefault(onCleared->{

            }, onFailed->{
                Throwable exThrowable = onFailed.getSource().getException();
                String msg = exThrowable != null ? exThrowable.getMessage() : "Unable to send message";

                Alert a = new Alert(AlertType.NONE, msg, ButtonType.OK);
                a.setHeaderText("Error");
                a.show();
            });
        });

        m_mainBox = new VBox(layoutBox);
        m_mainBox.setPadding(new Insets(0));
        HBox.setHgrow(m_mainBox, Priority.ALWAYS);

        m_currentBox.addListener((obs, oldval, newval) -> {
            m_mainBox.getChildren().clear();
            if (newval != null) {
                m_mainBox.getChildren().add(newval);
            } else {
                m_mainBox.getChildren().add(layoutBox);
            }

        });

        getDefaultMarketIds();


        getChildren().addAll(m_mainBox);
        setPadding(new Insets(0,0,5,0));
    }

    public NetworksData getNetworksData(){
        return m_networksData;
    }



     public void setDefaultMarket(String id,EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonObject note = NoteConstants.getCmdObject("setDefaultTokenMarket");
        note.addProperty("locationId", m_locationId);
        note.addProperty("id", id);
        
        m_ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
    }


    public void clearDefault(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonObject note = NoteConstants.getCmdObject("clearDefaultTokenMarket");
        note.addProperty("locationId", m_locationId);
        m_ergoNetworkInterface.sendNote(note,onSucceeded, onFailed);
    }

    public Future<?> getDefaultMarketIds(){
        
        JsonObject note = NoteConstants.getCmdObject("getDefaultMarketIds");
        note.addProperty("locationId", m_locationId);
        return m_ergoNetworkInterface.sendNote(note, onSucceeded->{
            Object obj = onSucceeded.getSource().getValue();
            
            JsonObject json = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
            if(json != null){
                JsonElement marketIdElement =  json.get("tokenMarketId");

                m_selectedTokenMarket.set(marketIdElement != null && !marketIdElement.isJsonNull() ? getNetworksData().getApp(marketIdElement.getAsString()) : null);
            
            }else{
              
                m_selectedTokenMarket.set(null);
            }
        }, onFailed->{
            
            m_selectedTokenMarket.set(null);
        });

        
    }



    public Future<?> updateMarkets(){
       
        JsonObject note = NoteConstants.getCmdObject("getTokenMarkets");
        note.addProperty("locationId", m_locationId);

        return m_ergoNetworkInterface.sendNote(note, onMarkets->{
            Object objResult = onMarkets.getSource().getValue();

            m_tokenMarketsMenuBtn.getItems().clear();

            if (objResult != null && objResult instanceof JsonArray) {

                JsonArray explorersArray = (JsonArray) objResult;

                for (JsonElement element : explorersArray) {
                    
                    JsonObject json = element.getAsJsonObject();

                    String name = json.get("name").getAsString();
                    String id = json.get("networkId").getAsString();

                    MenuItem menuItems = new MenuItem(String.format("%-20s", " " + name));
                    menuItems.setOnAction(action -> {
                        m_tokenMarketsMenuBtn.hide();
                        setDefaultMarket(id,onSucceeded->{
                            
                        }, onFailed->{
                            Throwable exThrowable = onFailed.getSource().getException();
                            String msg = exThrowable != null ? exThrowable.getMessage() : "Unable to send message";
            
                            Alert a = new Alert(AlertType.NONE, msg, ButtonType.OK);
                            a.setHeaderText("Error");
                            a.show();
                        });
                    });
                    m_tokenMarketsMenuBtn.getItems().add(menuItems);
                }
                MenuItem disableItem = new MenuItem(String.format("%-20s", " " + "[disabled]"));
                disableItem.setOnAction(action -> {
                    m_tokenMarketsMenuBtn.hide();
                    clearDefault(onCleared->{

                    }, onFailed->{
                        Throwable exThrowable = onFailed.getSource().getException();
                        String msg = exThrowable != null ? exThrowable.getMessage() : "Unable to send message";
        
                        Alert a = new Alert(AlertType.NONE, msg, ButtonType.OK);
                        a.setHeaderText("Error");
                        a.show();
                    });
                });
                m_tokenMarketsMenuBtn.getItems().add(disableItem);
            }else{
                MenuItem explorerItem = new MenuItem(String.format("%-50s", " Unable to find available markets."));
                m_tokenMarketsMenuBtn.getItems().add(explorerItem);
            }
        }, onMarketsFailed->{
            Throwable exThrowable = onMarketsFailed.getSource().getException();
            String msg = exThrowable != null ? exThrowable.getMessage() : "Unable to get markets";

            Alert a = new Alert(AlertType.NONE, msg, ButtonType.OK);
            a.setHeaderText("Error");
            a.show();
            m_tokenMarketsMenuBtn.hide();
            m_tokenMarketsMenuBtn.getItems().clear();
            MenuItem explorerItem = new MenuItem(String.format("%-50s", " Unable to find available markets."));
            m_tokenMarketsMenuBtn.getItems().add(explorerItem);
        });
       

    }

    @Override
    public void sendMessage(int code, long timestamp,String networkId, String msg){
        
        if(networkId != null){

            switch(code){
                
                case NoteConstants.LIST_DEFAULT_CHANGED:
                    getDefaultMarketIds(); 
                break;
              
            }

            AppBox appBox  = m_currentBox.get();
            if(appBox != null){
       
                appBox.sendMessage(code, timestamp,networkId, msg);
                
            }

        }
    
    }


}
