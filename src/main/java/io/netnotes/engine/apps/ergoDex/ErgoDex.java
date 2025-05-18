package io.netnotes.engine.apps.ergoDex;


import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.ergoplatform.appkit.NetworkType;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netnotes.engine.AppBox;
import io.netnotes.engine.BufferedButton;
import io.netnotes.engine.Drawing;
import io.netnotes.engine.networks.ergo.ErgoCurrency;
import io.netnotes.engine.networks.ergo.ErgoNetwork;
import io.netnotes.engine.Network;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.NoteMsgInterface;
import io.netnotes.engine.PriceQuote;
import io.netnotes.engine.Stages;
import io.netnotes.engine.TabInterface;
import io.netnotes.engine.Utils;
import io.netnotes.engine.apps.AppConstants;
import io.netnotes.engine.apps.TimeSpan;
import io.netnotes.engine.apps.ergoWallet.ErgoWalletControl;
import io.netnotes.engine.apps.ergoWallet.ErgoWalletMenu;
import io.netnotes.engine.apps.ergoWallet.ErgoWallets;
import javafx.beans.binding.Binding;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class ErgoDex extends Network implements NoteInterface {

    public final static String DESCRIPTION = "ErgoDex is an open source decentralized exchange (DEX) for fast, trustless swaps, liquidity provision & liquidity mining on the Ergo Blockchain.";

    public final static String NAME = "ErgoDex";
    public final static String WEB_URL = "https://www.ergodex.io";
    public final static String NETWORK_ID = "ERGO_DEX";
    public final static String API_URL = "https://api.spectrum.fi";

   // public final static String IMAGE_LINK = "https://raw.githubusercontent.com/spectrum-finance/token-logos/master/logos/ergo";


    public static java.awt.Color POSITIVE_HIGHLIGHT_COLOR = new java.awt.Color(0xff3dd9a4, true);
    public static java.awt.Color POSITIVE_COLOR = new java.awt.Color(0xff028A0F, true);

    public static java.awt.Color NEGATIVE_COLOR = new java.awt.Color(0xff9A2A2A, true);
    public static java.awt.Color NEGATIVE_HIGHLIGHT_COLOR = new java.awt.Color(0xffe96d71, true);
    public static java.awt.Color NEUTRAL_COLOR = new java.awt.Color(0x111111);

    public final static String SPF_ID = "9a06d9e545a41fd51eeffc5e20d818073bf820c635e2a9d922269913e0de369d";
    public final static String SIGUSD_ID = "03faf2cb329f2e90d6d23b58d91bbb6c046aa143261cc21f52fbe2824bfcbf04";

    public final static String ERG_SIGUSD_POOL_ID = "9916d75132593c8b07fe18bd8d583bda1652eed7565cf41a4738ddd90fc992ec";
    public final static String ERG_SPF_POOL_ID = "f40afb6f877c40a30c8637dd5362227285738174151ce66d6684bc1b727ab6cf";
    public final static NetworkType NETWORK_TYPE = NetworkType.MAINNET;
    public final static BigDecimal DEFAULT_NITRO =  BigDecimal.valueOf(1.2);
    public final static ErgoCurrency ERGO_CURRENCY = new ErgoCurrency(NETWORK_TYPE);
    public final static SPFCurrency SPF_CURRENCY = new SPFCurrency();


    public final static String MINER_ADDRESS = "2iHkR7CWvD1R4j1yZg5bkeDRQavjAaVPeTDFGGLZduHyfWMuYpmhHocX8GJoaieTx78FntzJbCBVL6rf96ocJoZdmWBL2fci7NqWgAirppPQmZ7fN9V6z13Ay6brPriBKYqLp1bT2Fk4FkFLCfdPpe";

    public final static long ONE_SECOND_MILLIS =    1000L;
    public final static long ONE_MINUTE_MILLIS =    1000L * 60L;
    public final static long ONE_HOUR_MILLIS =      1000L * 60L * 60L;

    public static final long ONE_DAY_MILLIS =       1000L * 60L * 60L * 24;
    public static final long ONE_WEEK_MILLIS =      1000L * 60L * 60L * 24L * 7L;

    public static final long ONE_MONTH_MILLIS =     1000L * 60L * 60L * 24L * 7L * 4L;
    public static final long SIX_MONTH_MILLIS =     1000L * 60L * 60L * 24L * 7L * 4L * 6L;
    public static final long ONE_YEAR_MILLIS =      1000L * 60L * 60L * 24L * 365L;

    public final static long BLOCK_TIME_MILLIS = ONE_MINUTE_MILLIS * 2L;
    
    public final static BigDecimal MIN_SLIPPAGE_TOLERANCE = BigDecimal.valueOf(0.01);
    public final static BigDecimal DEFAULT_SLIPPAGE_TOLERANCE = BigDecimal.valueOf(0.03);
    public static final int SWAP_FEE_DENOM = 1000;
    public static final int POOL_FEE_MAX_DECIMALS = 3;

  


   // private File m_dataFile = null;


    private Stage m_appStage = null;

    public static final String MARKET_DATA_ID = "marketData";
    public static final String TICKER_DATA_ID = "tickerData";

    public static final String MARKETS_LIST = "MARKETS_LIST";
    

    private ArrayList<ErgoDexMarketData> m_marketsList = new ArrayList<>();

    private int m_n2tItems = 0;

    private ScheduledFuture<?> m_scheduledFuture = null;

    private ArrayList<String> m_authorizedLocations = new ArrayList<>();
    
    private String m_locationId;

    public ErgoDex(NetworksData networksData, String locationId) {
        super(new Image(getAppIconString()), NAME, NETWORK_ID, networksData);
        setKeyWords(new String[]{"ergo", "exchange", "usd", "ergo tokens", "dApp", "SigUSD"});
        m_locationId = locationId;
    }


    public static String getAppIconString(){
        return "/assets/ErgoDex-150.png";
    }

 
    public ArrayList<ErgoDexMarketData> marketsList(){
        return m_marketsList;
    }
    
   
    public  Image getSmallAppIcon() {
        return new Image(getSmallAppIconString());
    }

    public static String getSmallAppIconString(){
        return "/assets/ErgoDex-32.png";
    }




    public Stage getAppStage() {
        return m_appStage;
    }

    public ErgoDexMarketData[] getMarketDataArray(){
        ErgoDexMarketData[] dataArray = new ErgoDexMarketData[m_marketsList.size()];
        return dataArray = m_marketsList.toArray(dataArray);
    }


    public static ErgoDexMarketData getMarketDataById(ArrayList<ErgoDexMarketData> dataList, String id) {
        if (id != null) {
            for (ErgoDexMarketData data : dataList) {
                if (data.getId().equals(id) ) {
                    return data;
                }
            }
            
        }
        return null;
    }


    public static ErgoDexMarketData getMarketDataByTickerId(ErgoDexMarketData[] dataList, String tickerId) {
        if (tickerId != null) {
            for (ErgoDexMarketData data : dataList) {
                if (data.getTickerId().equals(tickerId) ) {
                    return data;
                }
            }
            
        }
        return null;
    }

    public static int getMarketDataIndexById(ArrayList<ErgoDexMarketData> dataList, String id) {
        if (id != null) {
            int size = dataList.size();
            for (int i = 0; i < size; i++) {
                ErgoDexMarketData data = dataList.get(i);
                if (data.getId().equals(id) ) {
                    return i;
                }
            }
            
        }
        return -1;
    }


    
    /*public SpectrumMarketItem getMarketItem(String id) {
        if (id != null) {
            
            for (SpectrumMarketItem item : m_marketsList) {
                if (item.getId().equals(id)) {
                    return item;
                }
            }
            
        }
        return null;
    }*/

   

    
    public NoteInterface getNoteInterface(){
       
        return new NoteInterface() {
            
         

            public String getNetworkId(){
                return NETWORK_ID;
            }

    
            public Future<?> sendNote(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
                return ErgoDex.this.sendNote(note, onSucceeded, onFailed);
            }


            public void addMsgListener(NoteMsgInterface listener){
                if(listener != null && listener.getId() != null){
                    ErgoDex.this.addMsgListener(listener);
                }
            }
            public boolean removeMsgListener(NoteMsgInterface listener){
                
                return ErgoDex.this.removeMsgListener(listener);
            }
            
         
        };
    }


    

    private ErgoDexTab m_ergoDexTab = null;


    @Override
    public TabInterface getTab(Stage appStage,  SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, Button menuBtn){
        if(m_ergoDexTab != null){
            return m_ergoDexTab;
        }else{
            m_ergoDexTab = new ErgoDexTab(appStage,  heightObject, widthObject, menuBtn);
            return m_ergoDexTab;
        }
    }

    private class ErgoDexTab extends AppBox implements TabInterface{
        private Button m_menuBtn;
        private ErgoDexDataList m_dexDataList = null;
    
      

        private SimpleObjectProperty<TimeSpan> m_itemTimeSpan = new SimpleObjectProperty<TimeSpan>(new TimeSpan("1day"));
        private SimpleBooleanProperty m_isInvert = new SimpleBooleanProperty(false);
        private SimpleStringProperty m_status = new SimpleStringProperty(NoteConstants.STATUS_STOPPED);
        private HBox m_menuBar;
        private VBox m_bodyPaddingBox;

        private TextField m_lastUpdatedField;

        private SimpleDoubleProperty m_gridHeight;

        private SimpleDoubleProperty m_tabWidth;
        private SimpleDoubleProperty m_heightObject;
        private ScrollPane gridSscrollPane;

        private ErgoWalletMenu m_ergoWalletMenu; 

        public ErgoDexTab(Stage appStage,  SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, Button menuBtn){
            super(getNetworkId());
            
            m_appStage = appStage;
            m_menuBtn = menuBtn;
            
            m_lastUpdatedField = new TextField(); 
            
            m_tabWidth = widthObject;
            m_heightObject = heightObject;

            m_ergoWalletMenu = new ErgoWalletMenu(new ErgoWalletControl(NETWORK_ID, ErgoWallets.NETWORK_ID, ErgoNetwork.NETWORK_ID, NETWORK_TYPE, m_locationId, getNetworksData()));

 
            m_gridHeight = new SimpleDoubleProperty(heightObject.get() - 100);
            setPrefWidth(NetworksData.DEFAULT_STATIC_WIDTH);
            setMaxWidth(NetworksData.DEFAULT_STATIC_WIDTH);

            gridSscrollPane = new ScrollPane();
            prefHeightProperty().bind(heightObject);
            
            getData((onSucceeded)->{
                Object obj = onSucceeded.getSource().getValue();
                JsonObject json = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
                openJson(json); 
          
                layoutTab();
            });

        }

        

        public void layoutTab(){
           
            m_dexDataList = new ErgoDexDataList(m_ergoWalletMenu, m_locationId, m_appStage, ErgoDex.this, m_isInvert, m_tabWidth, m_gridHeight, m_lastUpdatedField, m_itemTimeSpan, gridSscrollPane);


            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            /*
            Tooltip refreshTip = new Tooltip("Refresh");
            refreshTip.setShowDelay(new javafx.util.Duration(100));
            refreshTip.setFont(NoteConstants.txtFont);

        

            BufferedMenuButton sortTypeButton = new BufferedMenuButton("/assets/filter.png", NoteConstants.MENU_BAR_IMAGE_WIDTH);

            MenuItem sortLiquidityItem = new MenuItem(ErgpDexSort.SortType.LIQUIDITY_VOL);
            MenuItem sortBaseVolItem = new MenuItem(ErgpDexSort.SortType.BASE_VOL);
            MenuItem sortQuoteVolItem = new MenuItem(ErgpDexSort.SortType.QUOTE_VOL);
            MenuItem sortLastPriceItem = new MenuItem(ErgpDexSort.SortType.LAST_PRICE);
        
            sortTypeButton.getItems().addAll(sortLiquidityItem, sortBaseVolItem, sortQuoteVolItem, sortLastPriceItem);

            Runnable updateSortTypeSelected = () ->{
                sortLiquidityItem.setId(null);
                sortBaseVolItem.setId(null);
                sortQuoteVolItem.setId(null);
                sortLastPriceItem.setId(null);

                switch(m_dexDataList.getSortMethod().getType()){
                    case ErgpDexSort.SortType.LIQUIDITY_VOL:
                        sortLiquidityItem.setId("selectedMenuItem");
                    break;
                    case ErgpDexSort.SortType.BASE_VOL:
                        sortBaseVolItem.setId("selectedMenuItem");
                    break;
                    case ErgpDexSort.SortType.QUOTE_VOL:
                        sortQuoteVolItem.setId("selectedMenuItem");
                    break;
                    case ErgpDexSort.SortType.LAST_PRICE:
                        sortLastPriceItem.setId("selectedMenuItem");
                    break;
                }

                m_dexDataList.sort();
                m_dexDataList.updateGrid();
            };

        // updateSortTypeSelected.run();

            sortLiquidityItem.setOnAction(e->{
                ErgpDexSort sortMethod = m_dexDataList.getSortMethod();
                sortMethod.setType(sortLiquidityItem.getText());
                updateSortTypeSelected.run();
            });

            sortBaseVolItem.setOnAction(e->{
                ErgpDexSort sortMethod = m_dexDataList.getSortMethod();
                sortMethod.setType(sortBaseVolItem.getText());
                updateSortTypeSelected.run();
            });

            sortQuoteVolItem.setOnAction(e->{
                ErgpDexSort sortMethod = m_dexDataList.getSortMethod();
                sortMethod.setType(sortQuoteVolItem.getText());
                updateSortTypeSelected.run();
            });

            sortLastPriceItem.setOnAction(e->{
                ErgpDexSort sortMethod = m_dexDataList.getSortMethod();
                sortMethod.setType(sortLastPriceItem.getText());
                updateSortTypeSelected.run();
            });


            BufferedButton sortDirectionButton = new BufferedButton(m_dexDataList.getSortMethod().isAsc() ? "/assets/sortAsc.png" : "/assets/sortDsc.png", Stages.MENU_BAR_IMAGE_WIDTH);
            sortDirectionButton.setOnAction(e->{
                ErgpDexSort sortMethod = m_dexDataList.getSortMethod();
                sortMethod.setDirection(sortMethod.isAsc() ? ErgpDexSort.SortDirection.DSC : ErgpDexSort.SortDirection.ASC);
                sortDirectionButton.setImage(new Image(sortMethod.isAsc() ? "/assets/sortAsc.png" : "/assets/sortDsc.png"));
                m_dexDataList.sort();
                m_dexDataList.updateGrid();
            });
            */
            BufferedButton swapTargetButton = new BufferedButton(m_dexDataList.isInvertProperty().get() ? "/assets/targetSwapped.png" : "/assets/targetStandard.png", Stages.MENU_BAR_IMAGE_WIDTH);
            swapTargetButton.setOnAction(e->{
                m_isInvert.set(!m_dexDataList.isInvertProperty().get());
            });

           m_isInvert.addListener((obs,oldval,newval)->{
                swapTargetButton.setImage(new Image(newval ? "/assets/targetSwapped.png" : "/assets/targetStandard.png"));
                save();
            });
            



            TextField searchField = new TextField();
            searchField.setPromptText("Search");
            searchField.setId("urlField");
            searchField.setPrefWidth(200);
            searchField.setPadding(new Insets(2, 10, 3, 10));
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                m_dexDataList.setSearchText(searchField.getText());
            });

            Region menuBarRegion = new Region();
            HBox.setHgrow(menuBarRegion, Priority.ALWAYS);

            
        


            VBox networkMenuBtnBox = new VBox( m_ergoWalletMenu);


    
            VBox.setVgrow(networkMenuBtnBox,Priority.ALWAYS);


            HBox rightSideMenu = new HBox(networkMenuBtnBox);
        
            rightSideMenu.setId("rightSideMenuBar");
            rightSideMenu.setPadding(new Insets(0, 3, 0, 10));
            rightSideMenu.setAlignment(Pos.CENTER_RIGHT);

            Region menuBarRegion1 = new Region();
            menuBarRegion1.setMinWidth(10);

        
            MenuButton timeSpanBtn = new MenuButton();
            timeSpanBtn.setId("arrowMenuButton");
            timeSpanBtn.setMinWidth(100);
            timeSpanBtn.setPrefWidth(100);
            timeSpanBtn.textProperty().bind( m_itemTimeSpan.asString());
            timeSpanBtn.setAlignment(Pos.CENTER_RIGHT);
    
            
            String[] spans = { "1hour", "8hour", "12hour", "1day", "1week", "1month", "6month", "1year" };

            for (int i = 0; i < spans.length; i++) {

                String span = spans[i];
                TimeSpan timeSpan = new TimeSpan(span);
                MenuItem menuItm = new MenuItem(timeSpan.getName());
                menuItm.setId("urlMenuItem");
                menuItm.setUserData(timeSpan);

                menuItm.setOnAction(action -> {
    
                    Object itemObject = menuItm.getUserData();

                    if (itemObject != null && itemObject instanceof TimeSpan) {
                        
                        TimeSpan timeSpanItem = (TimeSpan) itemObject;
                        m_itemTimeSpan.set(timeSpanItem);
                        save();
                    }

                });

                timeSpanBtn.getItems().add(menuItm);

            }


            HBox timeSpanBtnBox = new HBox(timeSpanBtn);
            timeSpanBtnBox.setId("urlMenuButton");
            timeSpanBtnBox.setAlignment(Pos.CENTER_LEFT);

            Region timeSpanSpacer = new Region();
            timeSpanSpacer.setMinWidth(10);

            //sortTypeButton,sortDirectionButton,
            m_menuBar = new HBox(swapTargetButton, menuBarRegion1, searchField, menuBarRegion, timeSpanBtnBox,timeSpanSpacer, rightSideMenu);
            HBox.setHgrow(m_menuBar, Priority.ALWAYS);
            m_menuBar.setAlignment(Pos.CENTER_LEFT);
            m_menuBar.setId("menuBar");
            m_menuBar.setPadding(new Insets(1, 2, 1, 5));
            m_menuBar.setMinHeight(25);


            VBox chartList = m_dexDataList.getLayoutBox();

            gridSscrollPane.setContent(chartList);

            HBox menuBarBox = new HBox(m_menuBar);
            HBox.setHgrow(menuBarBox,Priority.ALWAYS);
            menuBarBox.setPadding(new Insets(0,0,10,0));
            
            m_bodyPaddingBox = new VBox(menuBarBox, gridSscrollPane);
            m_bodyPaddingBox.setPadding(new Insets(0,5,0,5));

            m_lastUpdatedField.setEditable(false);
            m_lastUpdatedField.setId("formFieldSmall");
            m_lastUpdatedField.setPrefWidth(230);

            Binding<String> errorTxtBinding = Bindings.createObjectBinding(()->(m_dexDataList.statusMsgProperty().get().startsWith("Error") ? m_dexDataList.statusMsgProperty().get() : "") ,m_dexDataList.statusMsgProperty());

            Text errorText = new Text("");
            errorText.setFont(Stages.titleFont);
            errorText.setFill(Stages.altColor);
            errorText.textProperty().bind(errorTxtBinding);
            
            Region lastUpdatedRegion = new Region();
            lastUpdatedRegion.setMinWidth(10);
            HBox.setHgrow(lastUpdatedRegion, Priority.ALWAYS);

            HBox footerGradient = new HBox();
            footerGradient.setId("hGradient");
            footerGradient.setMinHeight(1);
            HBox.setHgrow(footerGradient, Priority.ALWAYS);

            HBox lastUpdatedBox = new HBox(errorText, lastUpdatedRegion, m_lastUpdatedField);
            lastUpdatedBox.setAlignment(Pos.CENTER_RIGHT);
            HBox.setHgrow(lastUpdatedBox, Priority.ALWAYS);

            VBox footerVBox = new VBox(footerGradient,lastUpdatedBox);
            HBox.setHgrow(footerVBox, Priority.ALWAYS);
            footerVBox.setPadding(new Insets(0,0,0,5));
            footerVBox.setId("footerBar");


            getChildren().addAll( m_bodyPaddingBox, footerVBox);

         

            m_bodyPaddingBox.prefWidthProperty().bind(widthProperty().subtract(1));
            gridSscrollPane.prefViewportWidthProperty().bind(m_tabWidth);
            gridSscrollPane.prefViewportHeightProperty().bind(m_heightObject.subtract(footerVBox.heightProperty()));

          
        }
   
        @Override
        public String getName() {  
            return ErgoDex.this.getName();
        }

    
        @Override
        public String getStatus() {
            return m_status.get();
        }

        @Override
        public void setStatus(String value) {
            
            switch(value){
                case NoteConstants.STATUS_STOPPED:
                    m_menuBtn.setId("menuTabBtn");
                    shutdown();
                    
                break;
                case NoteConstants.STATUS_MINIMIZED:
                    m_menuBtn.setId("minimizedMenuBtn"); 
                break;
                case NoteConstants.STATUS_STARTED:
                    m_menuBtn.setId("activeMenuBtn");
                break;
                
            }

            m_status.set(value);
            
        }

        @Override
        public void shutdown() {
            

            m_dexDataList.shutdown();

            m_ergoDexTab = null;

            m_appStage = null;

        

        }
 


        public void getData( EventHandler<WorkerStateEvent> onSucceeded){
            getNetworksData().getData("data", ".", "tab", ErgoDex.NETWORK_ID, onSucceeded);
        }

        public void openJson(JsonObject json){

            JsonElement itemTimeSpanElement = json != null ? json.get("itemTimeSpan") : null;
            JsonElement isInvertElement = json != null ? json.get("isInvert") : null;
            TimeSpan timeSpan = itemTimeSpanElement != null && itemTimeSpanElement.isJsonObject() ? new TimeSpan(itemTimeSpanElement.getAsJsonObject()) : new TimeSpan("1day");
            boolean isInvert = isInvertElement != null ? isInvertElement.getAsBoolean() : false;

            m_isInvert.set(isInvert);
            m_itemTimeSpan.set(timeSpan);
        }

        public JsonObject getJsonObject(){
            TimeSpan itemTimeSpan = m_itemTimeSpan == null ? new TimeSpan("1day") : m_itemTimeSpan.get();

            JsonObject json = new JsonObject();
            json.add("itemTimeSpan", itemTimeSpan.getJsonObject());
            json.addProperty("isInvert", m_isInvert.get());
            return json;
        }

        public void save(){
            getNetworksData().save("data", ".", "tab", ErgoDex.NETWORK_ID, getJsonObject());
        }

        @Override
        public SimpleStringProperty titleProperty() {

            return null;
        }
    
   

    
        
      
      
    }

    public Future<?> getNetworkObject(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonObject spectrumObject = ErgoDex.this.getJsonObject();
        spectrumObject.addProperty("apiUrl", API_URL);
        spectrumObject.addProperty("website", WEB_URL);
        spectrumObject.addProperty("description", DESCRIPTION);
        return Drawing.convertImgToHexString(getAppIcon(), getExecService(), onImgHex->{
            spectrumObject.addProperty("appIcon",(String) onImgHex.getSource().getValue());
            Utils.returnObject(spectrumObject, getExecService(), onSucceeded);
        }, onFailed);
    }

    public Future<?> getConnectionStatus(JsonObject note,EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed ){
        return Utils.returnObject(getConnectionStatus(), getExecService(), onSucceeded, onFailed);
    }

    public Future<?> getQuote(JsonObject note,EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed ){
        return Utils.returnObject(getQuote(note), getExecService(), onSucceeded, onFailed);
    }

    public Future<?> getTokenQuoteInErg(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed ){
        return Utils.returnObject(getTokenQuoteInErg(note), getExecService(), onSucceeded, onFailed);
    }

    public Future<?> getTokenArrayQuotesInErg(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed ){
        return Utils.returnObject(getTokenArrayQuotesInErg(note), getExecService(), onSucceeded, onFailed);
    }

    public Future<?> getErgoUSDQuote(JsonObject note,EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed ){
        JsonObject json = getErgoUSDQuote();
        if(json != null){
            return Utils.returnObject(json, getExecService(), onSucceeded, onFailed);
        }else{
            return Utils.returnException("Quote not found",getExecService(), onFailed);
        }
    }

    public Future<?> getQuoteById(JsonObject note,EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed ){
        return Utils.returnObject(getQuoteById(note), getExecService(), onSucceeded, onFailed);
    }

    public Future<?> getQuoteBySymbol(JsonObject note,EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed ){
        return Utils.returnObject(getQuoteBySymbol(note), getExecService(), onSucceeded, onFailed);
    }

    public Future<?> getAvailableQuotes(JsonObject note,EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed ){
        return Utils.returnObject(getAvailableQuotes(note), getExecService(), onSucceeded, onFailed);
    }

    public Future<?> getAvailableQuotesInErg(JsonObject note,EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed ){
        return Utils.returnObject(getAvailableQuotesInErg(note), getExecService(), onSucceeded, onFailed);
    }

    @Override
    public Future<?> sendNote(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        JsonElement subjectElement = note.get(NoteConstants.CMD);
        JsonElement locationIdElement = note.get("locationId");
      
        String cmd = subjectElement != null && subjectElement.isJsonPrimitive() ? subjectElement.getAsString() : null;
        
        if(cmd != null && locationIdElement != null && locationIdElement.isJsonPrimitive()){
            String locationId = locationIdElement.getAsString();
            String locationString = getNetworksData().getLocationString(locationId);
            if(isLocationAuthorized(locationString)){
              
                note.remove("locationString");
                note.addProperty("locationString", locationString);

                switch(cmd){
                    case "getNetworkObject":
                        return getNetworkObject(note, onSucceeded, onFailed);
                    case "getStatus":
                        return getConnectionStatus(note, onSucceeded, onFailed);
                    case "getQuote":
                        return getQuote(note, onSucceeded, onFailed);
                    case "getTokenQuoteInErg":
                        return getTokenQuoteInErg(note, onSucceeded, onFailed);
                    case "getTokenArrayQuotesInErg":
                        return getTokenArrayQuotesInErg(note, onSucceeded, onFailed);
                    case "getErgoUSDQuote":
                        return getErgoUSDQuote(note, onSucceeded, onFailed);
                    case "getQuoteById":
                        return getQuoteById(note, onSucceeded, onFailed);
                    case "getQuoteBySymbol":
                        return getQuoteBySymbol(note, onSucceeded, onFailed);
                    case "getAvailableQuotes":
                        return getAvailableQuotes(note, onSucceeded, onFailed);
                    case "getAvailableQuotesInErg":
                        return getAvailableQuotesInErg(note, onSucceeded, onFailed);
                    case "getPoolSlippage":
                        return getPoolSlippage(note, onSucceeded, onFailed);
                    
                    case "getPoolStats":
                        return getPoolStats(note, onSucceeded, onFailed);
                    
                    case "getPlatformStats":
                        return getPlatformStats(onSucceeded, onFailed);

                    case "getLiquidityPoolStats":
                        return getLiquidityPoolStats(onSucceeded, onFailed);

                    case "getMarkets":
                        return getMarkets(onSucceeded, onFailed);

                    case "getTickers":
                        return getTickers(onSucceeded, onFailed);

                    case "getPoolsSummary":
                        return getPoolsSummary(onSucceeded, onFailed);
                }
            }
        }
        return null;

    }

  
    public Future<?> getPoolSlippage(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement poolIdElement = note != null ? note.get("poolId") : null;
        String poolId = poolIdElement != null && !poolIdElement.isJsonNull() ? poolIdElement.getAsString() : null;
        if(poolId != null){
            return getPoolSlippage(poolId, getExecService(), onSucceeded, onFailed);

        }

        return null;
    }

    public Future<?> getPoolStats(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        JsonElement poolIdElement = note != null ? note.get("poolId") : null;
        String poolId = poolIdElement != null && !poolIdElement.isJsonNull() ? poolIdElement.getAsString() : null;
        
        if(poolId != null){
            return getPoolStats(poolId, getExecService(), onSucceeded, onFailed);

        }

        return null;
    }   

   

  
    public ExecutorService getExecService(){
        return getNetworksData().getExecService();
    }
    // /v1/amm/pools/summary/all

    private Future<?> getPoolsSummary(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        String urlString = API_URL + "/v1/amm/pools/summary/all";

        return Utils.getUrlJsonArray(urlString, getExecService(), onSucceeded, onFailed);
                        
    }

    private Future<?> getMarkets(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        String urlString = API_URL + "/v1/price-tracking/markets";
        
        return Utils.getUrlJsonArray(urlString, getExecService(), onSucceeded, onFailed);
                        
    }
  
      
    public Future<?> getTickers(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        String urlString = API_URL + "/v1/price-tracking/cg/tickers";

        return Utils.getUrlJsonArray(urlString, getNetworksData().getExecService(), onSucceeded, onFailed);                

    }

    public Future<?> getPlatformStats(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        String urlString = API_URL + "/v1/amm/platform/stats";

        return Utils.getUrlJson(urlString, getNetworksData().getExecService(), onSucceeded, onFailed);                

    }

    public Future<?> getPoolSlippage(String poolId, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        String urlString = API_URL + "/v1/amm/pool/" + poolId + "/slippage";

        return Utils.getUrlJson(urlString, execService, onSucceeded, onFailed);
    }

    public Future<?> getPoolStats(String poolId, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        String urlString = API_URL + "/v1/amm/pool/" + poolId + "/stats";

        return Utils.getUrlJson(urlString, execService, onSucceeded, onFailed);
    }

    private static Future<?> parseList(JsonArray jsonArray, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded){
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() {
                ArrayList<ErgoDexMarketData> tmpMarketsList = new ArrayList<>();
                long timeStamp = System.currentTimeMillis();

                SimpleBooleanProperty isChanged = new SimpleBooleanProperty(false);
                for (int i = 0; i < jsonArray.size(); i++) {
            
                    JsonElement marketObjectElement = jsonArray.get(i);
                    if (marketObjectElement != null && marketObjectElement.isJsonObject()) {

                        JsonObject marketDataJson = marketObjectElement.getAsJsonObject();
                        
                        try{
                            
                            ErgoDexMarketData marketData = new ErgoDexMarketData(marketDataJson, timeStamp);
                            
                            int marketIndex = getMarketDataIndexById(tmpMarketsList, marketData.getId());
                            

                            if(marketIndex != -1){
                                ErgoDexMarketData lastData = tmpMarketsList.get(marketIndex);
                                BigDecimal quoteVolume =lastData.getQuoteVolume() != null ? lastData.getQuoteVolume().getBigDecimalAmount() : BigDecimal.ZERO;
                
                                BigDecimal newVolume = marketData.getQuoteVolume() != null ? marketData.getQuoteVolume().getBigDecimalAmount() : BigDecimal.ZERO;

                                if(newVolume.compareTo(quoteVolume) > 0){
                                    tmpMarketsList.set(marketIndex, marketData);
                                }
                            }else{
                                isChanged.set(true);
                                tmpMarketsList.add(marketData);
                            }

                            
                            
                        }catch(Exception e){
                            try {
                                Files.writeString(AppConstants.LOG_FILE.toPath(), "egoDex(updateMarkets): " + e.toString() + " " + marketDataJson.toString() + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            } catch (IOException e1) {
                            
                            }
                        }
                        
                    }

                }

                return tmpMarketsList.toArray(new ErgoDexMarketData[tmpMarketsList.size()]);
            }
        };

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

    private static Future<?> doMerge(JsonArray tickerArray, ErgoDexMarketData[] dataArray, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded){
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() {
                for (int j = 0; j < tickerArray.size(); j++) {
                        
                    JsonElement tickerObjectElement = tickerArray.get(j);
                    if (tickerObjectElement != null && tickerObjectElement.isJsonObject()) {

                        JsonObject tickerDataJson = tickerObjectElement.getAsJsonObject();

                        JsonElement tickerIdElement = tickerDataJson.get("ticker_id");
                        String tickerId = tickerIdElement != null && tickerIdElement.isJsonPrimitive() ? tickerIdElement.getAsString() : null;
                        JsonElement poolIdElement = tickerDataJson.get("pool_id");
                        String poolId = poolIdElement != null && !poolIdElement.isJsonNull() && poolIdElement.isJsonPrimitive() ? poolIdElement.getAsString() : null;

                        if(tickerId != null){
                    
                        
                            ErgoDexMarketData marketData = getMarketDataByTickerId(dataArray, tickerId);
                        
                        
                            
                            if(marketData != null){
                                
                                JsonElement liquidityUsdElement = tickerDataJson.get("liquidity_in_usd");
                            
                                if(liquidityUsdElement != null && liquidityUsdElement.isJsonPrimitive() ){

                                    marketData.setLiquidityUSD(liquidityUsdElement.getAsBigDecimal());

                                }
                                if( poolId != null ){
                                    marketData.setPoolId(poolId);
                                }
                            }

                        }
                    
                        
                    }

                }
                List<ErgoDexMarketData> tmpMarketsList = Arrays.asList(dataArray);

                Collections.sort(tmpMarketsList, Collections.reverseOrder(Comparator.comparing(ErgoDexMarketData::getLiquidityUSD)));

                return dataArray;
            }
        };

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

    private static Future<?> sortData(ErgoDexMarketData[] data, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded){
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() {
                List<ErgoDexMarketData> tmpMarketsList = Arrays.asList(data);

                Collections.sort(tmpMarketsList, Collections.reverseOrder(Comparator.comparing(ErgoDexMarketData::getLiquidityUSD)));

                return tmpMarketsList;
            }
        };

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

    private void mergeTickerData(ErgoDexMarketData[] data){
        if(data.length != 0){
            getTickers((onTickerArray)->{

                Object tickerSourceObject = onTickerArray.getSource().getValue();
                if (tickerSourceObject != null && tickerSourceObject instanceof JsonArray) {
                    JsonArray tickerArray = (JsonArray) tickerSourceObject;

                    doMerge(tickerArray, data,getExecService(), onMerged->{
                        Object mergedObj = onMerged.getSource().getValue();
                        if(mergedObj != null & mergedObj instanceof ErgoDexMarketData[]){
                            ErgoDexMarketData[] mergedData = (ErgoDexMarketData[]) mergedObj;
                            updateMarketList(mergedData);                            
                        }
                    });

                   
                   // Platform.runLater(()->updateMarketArray(completeDataArray));

                }
            }, (onTickersFailed)->{
                try {
                    Files.writeString(AppConstants.LOG_FILE.toPath(), "egoDex (onTickersFailed): " + onTickersFailed.getSource().getException().toString() +"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                   
                }
             
            });
        }
    }

    private void getMarketUpdate(JsonArray jsonArray){
        parseList(jsonArray, getExecService(), onComplete->{
            Object obj = onComplete.getSource().getValue();
            if(obj != null && obj instanceof ErgoDexMarketData[]){
                mergeTickerData((ErgoDexMarketData[]) obj);
            }
        });
    }

    private JsonObject getAvailableQuotes(JsonObject note){
        int size = m_marketsList.size();
        if(size > 0){
            JsonObject json = new JsonObject();
            JsonArray jsonArray = new JsonArray();
            for(int i = 0; i < size; i++){
                ErgoDexMarketData marketData = m_marketsList.get(i);
                jsonArray.add(marketData.getJsonObject());
            }
            json.addProperty("size", jsonArray.size());
            json.add("quotes", jsonArray);
            return json;
        }
        return null;
    }

    private JsonObject getAvailableQuotesInErg(JsonObject note){


        int listSize = m_marketsList.size();
        if(m_n2tItems > 0 && listSize > 0){

            JsonElement offsetElement = note.get("offset");
            JsonElement limitElement = note.get("limit");
            JsonElement filterElement = note.get("filter");

            int offset = offsetElement != null && !offsetElement.isJsonNull() ? offsetElement.getAsInt() : 0;
            int limit = limitElement != null && !limitElement.isJsonNull() ? limitElement.getAsInt() : 50;
            String filter = filterElement != null && !filterElement.isJsonNull() ? filterElement.getAsString() : null;
            

            int maxPages = (int)Math.floor(m_n2tItems / limit);
            int maxOffset = (maxPages * limit);

            limit = limit < 1 ? 1 : limit > m_n2tItems ? m_n2tItems : limit;
            offset = offset < 0 ? 0 : (offset > maxOffset) ? maxOffset : offset;
            
            
            if(filter != null && filter.length() > 0){
                JsonObject json = new JsonObject();
                JsonArray jsonArray = new JsonArray();
                String lowerCaseFilter = filter.toLowerCase();
                List<ErgoDexMarketData> searchList = m_marketsList.stream().filter(item -> item.getSymbol().toLowerCase().indexOf(lowerCaseFilter) != -1 && item.isNative2Token()).collect(Collectors.toList());
                int searchListSize = searchList.size();
           
                int i = 0; 
                int j = 0;
                while(i < searchListSize){
                    ErgoDexMarketData marketData = searchList.get(i);
                    if(j >= offset){
                        jsonArray.add( marketData.getPriceQuote(true).getJsonObject());
                        if(jsonArray.size() >= limit){
                            break;
                        }
                    }
                    j = j + 1;
                    i++;
                }
                json.addProperty("offset", offset);
                json.addProperty("limit", limit);
                json.addProperty("total", searchListSize);
                json.addProperty("size", jsonArray.size());
                json.add("quotes", jsonArray);
                return json;
            }else{
                JsonObject json = new JsonObject();
                JsonArray jsonArray = new JsonArray();
                int i = 0; 
                int j = 0;
                boolean isN2t = false;
                while(i < listSize){
                    ErgoDexMarketData marketData = m_marketsList.get(i);
                    isN2t = marketData.isNative2Token();
                    if(j >= offset && isN2t){
                        jsonArray.add( marketData.getPriceQuote(true).getJsonObject());
                        if(jsonArray.size() >= limit){
                            break;
                        }
                    }
                    j = isN2t ? j + 1 : j;
                    i++;
                }
                json.addProperty("offset", offset);
                json.addProperty("limit", limit);
                json.addProperty("total", m_n2tItems);
                json.addProperty("size", jsonArray.size());
                json.add("quotes", jsonArray);
                return json;
            }
        }
        return null;
          
    }

    

    private void updateMarketList(ErgoDexMarketData[] data){
        int size = data.length;
        long timeStamp = System.currentTimeMillis();

        if(m_marketsList.size() == 0){
            int numN2tItems = 0;
            for(int i = 0; i < size; i++){
                ErgoDexMarketData marketData = data[i];
                if(marketData != null){
                    marketData.setErgoDex(this);
                    m_marketsList.add(marketData);    
                }
                numN2tItems = marketData.isNative2Token() ? numN2tItems + 1 : numN2tItems;
            }

            if(m_marketsList.size() > 0){
                if(getConnectionStatus() != NoteConstants.STARTED){
                    setConnectionStatus(NoteConstants.STARTED);
                }
            }
            m_n2tItems = numN2tItems;
            sendMessage(NoteConstants.LIST_CHANGED, timeStamp,NETWORK_ID, m_marketsList.size());
        }else{
            SimpleBooleanProperty changed = new SimpleBooleanProperty(false);
            int numN2tItems = 0;
            for(int i = 0; i < size; i++){
                ErgoDexMarketData newMarketData = data[i];
                numN2tItems = newMarketData.isNative2Token() ? numN2tItems + 1 : numN2tItems;

                ErgoDexMarketData marketData = getMarketDataById(m_marketsList, newMarketData.getId());
                if(marketData != null){
                    marketData.update(newMarketData);
                }else{
                    changed.set(true);
                    newMarketData.setErgoDex(this);
                    m_marketsList.add(newMarketData);
                   
                }
            }
  
            if(m_marketsList.size() > 0){
                if(getConnectionStatus() != NoteConstants.STARTED){
                    setConnectionStatus(NoteConstants.STARTED);
                }
            }
            m_n2tItems = numN2tItems;
            if(changed.get()){
                sendMessage(NoteConstants.LIST_CHANGED, timeStamp,NETWORK_ID,  m_marketsList.size());
            }else{
                sendMessage(NoteConstants.LIST_UPDATED, timeStamp,NETWORK_ID,  m_marketsList.size());
            }
            
        }
        
    }
    
    
    /*
    public void getTickers(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        
        long currentTime = System.currentTimeMillis();
        long lastChecked = m_tickersLastChecked.getAndSet(currentTime);
        File tickersFile = getIdDataFile(TICKER_DATA_ID);

        if(lastChecked != 0 && (currentTime -lastChecked) < TICKER_DATA_TIMEOUT_SPAN && tickersFile.isFile() && tickersFile.length() > 50 ){
            
            try{
                String fileString = Utils.readStringFile(getAppKey(), tickersFile);
        
                Utils.returnObject(new JsonParser().parse(fileString).getAsJsonArray(), onSucceeded, onSucceeded);

            }catch(IOException | InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException | BadPaddingException | IllegalBlockSizeException e){
                try {
                    Files.writeString(AppConstants.LOG_FILE.toPath(), "\negoDex: getTickersMarkets: " + e.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e1) {

                }
                getTickersLocal(onSucceeded, onFailed);
            }

        }else{
             getTickersLocal(onSucceeded, onFailed);
        }
        
    }*/
    //

    //https://api.spectrum.fi/v1/history/mempool
    /*
     * POST JsonArray [address]
     */
    public void getMemPoolHistory(String address, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        String urlString = API_URL + "/v1/history/mempool";
        
        Utils.getUrlJsonArray(urlString, getExecService(), onSucceeded, onFailed);

    }

    //https://api.spectrum.fi/v1/history/order
    /* POST
        "addresses": 
        [

            "9gEwsJePmqhCXwdtCWVhvoRUgNsnpgWkFQ2kFhLwYhRwW7tMc61"

        ],
        "orderType": "Swap",
        "orderStatus": "Evaluated",
        "txId": "00000111ba9e273590f73830aaeb9ccbb7e75fb57d9d2d3fb1b6482013b2c38f",
        "tokenIds": 
        [

            "0000000000000000000000000000000000000000000000000000000000000000"

        ],
        "tokenPair": 
        {

            "x": "0000000000000000000000000000000000000000000000000000000000000000",
            "y": "03faf2cb329f2e90d6d23b58d91bbb6c046aa143261cc21f52fbe2824bfcbf04"

        }
     */

    public static NetworkInformation getNetworkInformation(){
        return new NetworkInformation(NETWORK_ID, NAME, getAppIconString(), getSmallAppIconString(), DESCRIPTION);
    }

    public void getOrderHistory(String address, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        String urlString = API_URL + "/v1/history/order";
        
        Utils.getUrlJsonArray(urlString, getExecService(), onSucceeded, onFailed);

    }

    @Override
    public String getDescription(){
        return DESCRIPTION;
    }

    // https://api.spectrum.fi/v1/history/addresses?offset=0&limit=100
    /*
        offset	
        integer <int32> >= 0
        limit	
        integer <int32> [ 1 .. 100 ] 
    
    */
    public void getAddressesHistory(int offset, int limit, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        String urlString = API_URL + "/v1/history/addresses?offset="+offset + "&limit=" + limit;
        
        Utils.getUrlJsonArray(urlString, getExecService(), onSucceeded, onFailed);

    }


    /*https://api.spectrum.fi/v1/lm/pools/stats
        "poolId": "9916d75132593c8b07fe18bd8d583bda1652eed7565cf41a4738ddd90fc992ec",
        "compoundedReward": 75,
        "yearProfit": 3700
    */
    public Future<?> getLiquidityPoolStats(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
            String urlString = API_URL + "/v1/lm/pools/stats";
       
            
            return Utils.getUrlJsonArray(urlString, getExecService(), onSucceeded, onFailed);
    
    }
    

   

    public Future<?> getPoolChart(String poolId, long currentTime, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {


        String urlString = API_URL + "/v1/amm/pool/" + poolId + "/chart?from=0&to=" + currentTime;

        return Utils.getUrlJsonArray(urlString, getExecService(), onSucceeded, onFailed);
    }
    
    public Future<?> getPoolChart(String poolId,long fromTime, long currentTime, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {


        String urlString = API_URL + "/v1/amm/pool/" + poolId + "/chart?from=" + fromTime + "&to=" + currentTime;

        return Utils.getUrlJsonArray(urlString, getExecService(), onSucceeded, onFailed);
    }

 


    @Override
    public void stop(){
        setConnectionStatus(NoteConstants.STOPPED);

        m_marketsList.clear();
        if (m_scheduledFuture != null && !m_scheduledFuture.isDone()) {
            m_scheduledFuture.cancel(false);
            
        }

    }
 
    //private static volatile int m_counter = 0;
    @Override
    public void start(){
      
        if(getConnectionStatus() == NoteConstants.STOPPED){

            setConnectionStatus(NoteConstants.STARTING);
            sendMessage(NoteConstants.STARTING, System.currentTimeMillis(), NETWORK_ID, NoteConstants.STARTING);
            ExecutorService executor = getNetworksData().getExecService();
            
            Runnable exec = ()->{
                //FreeMemory freeMem = Utils.getFreeMemory();
            
                getMarkets(success -> {

                    Object sourceObject = success.getSource().getValue();
                    if (sourceObject != null && sourceObject instanceof JsonArray) {
                        JsonArray marketJsonArray = (JsonArray) sourceObject;

                        /*try {
                            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
                            Files.writeString(AppConstants.LOG_FILE.toPath(), gson.toJson(marketJsonArray) +"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        } catch (IOException e) {

                        }*/
        
                       
                        getMarketUpdate(marketJsonArray);
                    } 
                 
                }, (onfailed)->{
                    
                    setConnectionStatus(NoteConstants.ERROR);
                    Throwable throwable = onfailed.getSource().getException();
                    String msg= throwable instanceof java.net.SocketException ? "Connection unavailable" : (throwable instanceof java.net.UnknownHostException ? "Unknown host: Spectrum Finance unreachable" : throwable.toString());
                  

                    sendMessage(NoteConstants.ERROR, System.currentTimeMillis(),NETWORK_ID, msg);
                });
                

                
            };

            Runnable submitExec = ()->executor.submit(exec);
            if(m_scheduledFuture == null || (m_scheduledFuture != null && (m_scheduledFuture.isCancelled() || m_scheduledFuture.isDone()))){
                m_scheduledFuture = getNetworksData().getSchedualedExecService().scheduleAtFixedRate(submitExec, 0, 7000, TimeUnit.MILLISECONDS);
            }

       
           
        }
    }



    private boolean isLocationAuthorized(String locationString){
        
        return locationString.equals(ErgoNetwork.NAME) || m_authorizedLocations.contains(locationString);
    }
    
  



    private List<ErgoDexMarketData> m_searchList ;
    private Optional<ErgoDexMarketData> m_quoteOptional;


    private ErgoDexMarketData findMarketDataById(String baseId, String quoteId){
        if(m_marketsList != null && baseId != null && quoteId != null){
            int size = m_marketsList.size();
            for(int i = 0; i < size ; i++){
                ErgoDexMarketData data = m_marketsList.get(i);
                if(data != null && data.getBaseId().equals(baseId) && data.getQuoteId().equals(quoteId)){
                    data.setExchangeName(NETWORK_ID);
                    return data;
                }
            }
        }
        return null;
    }

    private ErgoDexMarketData findMarketDataBySymbol(String baseSymbol, String quoteSymbol){
        if(m_marketsList != null && baseSymbol != null && quoteSymbol != null){
            int size = m_marketsList.size();
            for(int i = 0; i < size ; i++){
                ErgoDexMarketData data = m_marketsList.get(i);
                if(data != null && data.getBaseSymbol().equals(baseSymbol) && data.getQuoteSymbol().equals(quoteSymbol)){
                    data.setExchangeName(NETWORK_ID);
                    return data;
                }
            }
        }
        return null;
    }

    private JsonObject getQuote(JsonObject json){
        JsonElement baseTypeElement = json.get("baseType");
        JsonElement quoteTypeElement = json.get("quoteType");
        JsonElement baseElement = json.get("base");
        JsonElement quoteElement = json.get("quote");

        String baseType = baseTypeElement != null && baseTypeElement.isJsonPrimitive() ? baseTypeElement.getAsString() : null;
        String quoteType = quoteTypeElement != null && quoteTypeElement.isJsonPrimitive() ? quoteTypeElement.getAsString() : null;
        String baseString = baseElement != null && baseElement.isJsonPrimitive() ? baseElement.getAsString() : null;
        String quoteString = quoteElement != null && quoteElement.isJsonPrimitive() ? quoteElement.getAsString() : null;

        m_searchList = null;
        if(baseType != null && baseString != null){
            switch(baseType){
         
                case "symbol":
                    m_searchList = m_marketsList.stream().filter(item -> item.getBaseSymbol().toLowerCase().equals(baseString.toLowerCase())).collect(Collectors.toList());
                break;
                case "id":
                    m_searchList = m_marketsList.stream().filter(item -> item.getBaseId().equals(baseString)).collect(Collectors.toList());
                 
                    
                    break;
            }
        }


        m_searchList = m_searchList != null ? m_searchList : m_marketsList;

        if(quoteType != null && quoteString != null){
            switch(quoteType){
                case "firstSymbolContains":
                    m_quoteOptional = m_searchList.stream().filter(item -> item.getQuoteSymbol().contains(quoteString)).findFirst();
                    if(m_quoteOptional.isPresent()){
                      
                        return m_quoteOptional.get().getPriceQuote().getJsonObject();
                    }
                break;
                case "firstId":
                    m_quoteOptional = m_searchList.stream().filter(item -> item.getQuoteId().equals(quoteString)).findFirst();
                  
                    
                    if(m_quoteOptional.isPresent()){
                     
                        return m_quoteOptional.get().getPriceQuote().getJsonObject();
                    }
                break;
            }
        }
        m_searchList = null;
        return null;
    }

    private JsonObject getErgoUSDQuote(){
        ErgoDexMarketData marketData = findMarketDataById(ErgoCurrency.TOKEN_ID, SIGUSD_ID);
        if(marketData != null){
            return marketData.getPriceQuote().getJsonObject();
        }
        return null;
    }

    private PriceQuote getTokenQuoteInErg(String tokenId){
        if(tokenId != null){
            ErgoDexMarketData data = findMarketDataById(ErgoCurrency.TOKEN_ID, tokenId);
            if(data != null){
                return data.getPriceQuote(true);
            }
        }
        return null;
    }

    private JsonObject getTokenQuoteInErg(JsonObject note){
        JsonElement idElement = note != null ? note.get("tokenId") : null;
        String tokenId = idElement != null && !idElement.isJsonNull() && idElement.isJsonPrimitive() ? idElement.getAsString() : null;
        if(tokenId != null){
            PriceQuote quote = getTokenQuoteInErg(tokenId);
            return quote != null ? quote.getJsonObject() : null;
        }
        return null;
    }

    private JsonObject getTokenArrayQuotesInErg(JsonObject note){
        JsonElement idsElement = note != null ? note.get("tokenIds") : null;
        JsonArray idsArray = idsElement != null && !idsElement.isJsonNull() && idsElement.isJsonArray() ? idsElement.getAsJsonArray() : null;
        if(idsArray != null){
            JsonObject json = new JsonObject();
            JsonArray quotesArray = new JsonArray();
            for(JsonElement idElement : idsArray){
                if(idElement != null && !idElement.isJsonNull() && idElement.isJsonPrimitive()){
                    String tokenId = idElement.getAsString();
                    PriceQuote quote = getTokenQuoteInErg(tokenId);
                    if(quote != null){
                        quotesArray.add(quote.getJsonObject());
                    }
                }
            }
            json.add("quotes", quotesArray);
            return json;
        }
        return null;
    }
    
    private JsonObject getQuoteById(JsonObject note){
        JsonElement baseIdElement = note != null ? note.get("baseId") : null;
        JsonElement quoteIdElement = note != null ? note.get("quoteId") : null;

        String baseId = baseIdElement != null && !baseIdElement.isJsonNull() && baseIdElement.isJsonPrimitive() ? baseIdElement.getAsString() : ErgoCurrency.TOKEN_ID;
        String quoteId = quoteIdElement != null && !quoteIdElement.isJsonNull() && quoteIdElement.isJsonPrimitive() ? quoteIdElement.getAsString() : null;

        if(quoteId != null){
            return findMarketDataById(baseId, quoteId).getPriceQuote().getJsonObject();
        }
        return null;
    }

    private JsonObject getQuoteBySymbol(JsonObject note){
        JsonElement baseSymbolElement = note != null ? note.get("baseSymbol") : null;
        JsonElement quoteSymbolElement = note != null ? note.get("quoteSymbol") : null;

        String baseSymbol = baseSymbolElement != null && !baseSymbolElement.isJsonNull() && baseSymbolElement.isJsonPrimitive() ? baseSymbolElement.getAsString() : null;
        String quoteSymbol = quoteSymbolElement != null && !quoteSymbolElement.isJsonNull() && quoteSymbolElement.isJsonPrimitive() ? quoteSymbolElement.getAsString() : null;

        if(baseSymbol != null && quoteSymbol != null){
            return findMarketDataBySymbol(baseSymbol, quoteSymbol).getPriceQuote().getJsonObject();
        }
        return null;
    }
  

    @Override
    public void shutdown(){
        super.shutdown();

    
    }
 

   
    
}
