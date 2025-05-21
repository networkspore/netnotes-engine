package io.netnotes.engine.apps.ergoWallets;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.ergoplatform.appkit.NetworkType;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import io.netnotes.engine.AmountBoxInterface;
import io.netnotes.engine.AppBox;
import io.netnotes.engine.ErgoMarketControl;
import io.netnotes.engine.JsonParametersBox;
import io.netnotes.engine.LockField;
import io.netnotes.engine.Network;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.NoteMsgInterface;
import io.netnotes.engine.PriceAmount;
import io.netnotes.engine.PriceCurrency;
import io.netnotes.engine.PriceQuote;
import io.netnotes.engine.Stages;
import io.netnotes.engine.TabInterface;
import io.netnotes.engine.Utils;
import io.netnotes.engine.networks.NetworkConstants;
import io.netnotes.engine.networks.ergo.AddressBox;
import io.netnotes.engine.networks.ergo.AddressInformation;
import io.netnotes.engine.networks.ergo.ErgoConstants;
import io.netnotes.engine.networks.ergo.ErgoCurrency;
import io.netnotes.engine.networks.ergo.ErgoNetwork;
import io.netnotes.engine.networks.ergo.ErgoTokenInfo;
import io.netnotes.engine.networks.ergo.ErgoTxViewsBox;
import io.netnotes.engine.networks.ergo.PriceQuoteRow;
import io.netnotes.engine.networks.ergo.PriceQuoteScroll;
import io.netnotes.friendly_id.FriendlyId;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.paint.Color;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.util.Duration;

public class ErgoWallets extends Network  {
    public final static String NETWORK_ID = "ERGO_WALLETS";
    public final static String DESCRIPTION = "Ergo Wallet allows you to create and manage and access wallets for the Ergo blockchain.";
    public final static String NAME = "Ergo Wallets";
    public final static String ICON = "/assets/ergo-wallet-30.png";

    public final static ExtensionFilter ergExt = new ExtensionFilter("Ergo Wallet (Satergo compatible)", "*.erg");

    public final static String DONATION_ADDRESS_STRING = "9h123xUZMi26FZrHuzsFfsTpfD3mMuTxQTNEhAjTpD83EPchePU";
    
    public final static String ERGO_MARKET = "ERGO_MARKET";
    public final static String TOKEN_MARKET = "TOKEN_MARKET";
    public final static NetworkType NETWORK_TYPE = NetworkType.MAINNET;

    private final SimpleLongProperty m_timeStampProperty = new SimpleLongProperty(0);

    private ErgoWalletDataList m_ergoWalletDataList = null;
    private NetworksData m_networksData;
    private String m_locationId = null;

    private ArrayList<String> m_authorizedLocations = new ArrayList<>();
    private ArrayList<String> m_blockedLocations = new ArrayList<>();
    private HashMap<String, String> m_controlIds = new HashMap<>();

    private ArrayList<NoteMsgInterface> m_controlInterfaces = new ArrayList<>();

    private NoteMsgInterface m_networksDataMsgInterface = null;


    public ErgoWallets( String locationId, NetworksData networksData) {
        super(new Image(ICON), NAME, NETWORK_ID, networksData);
        m_locationId = locationId;
        m_networksData = networksData;

        setKeyWords(new String[]{"ergo", "wallet"});
        getData();
    }

    protected void getData(){
        getNetworksData().getData("data", ".", NETWORK_ID, ErgoNetwork.NETWORK_ID, (onSucceeded)->{
            Object obj = onSucceeded.getSource().getValue();
            openJson(obj != null && obj instanceof JsonObject ? (JsonObject) obj : null);
        });
    }

    protected void openJson(JsonObject json){
        if(json != null){


  
            readAuthorizedLocations(json);
            readBlockedLocations(json);
        }else{

            save();
        }
      

        m_ergoWalletDataList = new ErgoWalletDataList(this, NETWORK_TYPE, m_locationId);

    }

    protected void readAuthorizedLocations(JsonObject json){
        JsonElement authorizedElement = json.get("authorizedLocations");
        JsonArray authorizedArray = authorizedElement != null && authorizedElement.isJsonArray() ? authorizedElement.getAsJsonArray() : new JsonArray();
        for(int i = 0; i < authorizedArray.size() ; i++){
            JsonElement element = authorizedArray.get(i);
            if(element != null && !element.isJsonNull() && element.isJsonPrimitive()){
                m_authorizedLocations.add(element.getAsString());
            }
        }
    }

    protected void addAuthorizedLocations(JsonObject json){
        JsonArray authorizedLocations = new JsonArray();
        for(String location : m_authorizedLocations){
            authorizedLocations.add(new JsonPrimitive(location));
        }
        json.add("authorizedLocations", authorizedLocations);
    }

    protected void readBlockedLocations(JsonObject json){
        JsonElement blockedElement = json.get("blockedLocations");
        JsonArray blockedArray = blockedElement != null && blockedElement.isJsonArray() ? blockedElement.getAsJsonArray() : new JsonArray();
        for(int i = 0; i < blockedArray.size() ; i++){
            JsonElement element = blockedArray.get(i);
            if(element != null && !element.isJsonNull() && element.isJsonPrimitive()){
                m_blockedLocations.add(element.getAsString());
            }
        }
    }

    protected void addBlockedLocations(JsonObject json){
        JsonArray blockedLocations = new JsonArray();
        for(String location : m_blockedLocations){
            blockedLocations.add(new JsonPrimitive(location));
        }
        json.add("blockedLocations", blockedLocations);
    }

    @Override
    public JsonObject getJsonObject(){
        JsonObject json = super.getJsonObject();
        addAuthorizedLocations(json);
        addBlockedLocations(json);
        return json;
    }

    public static NetworkInformation getNetworkInformation(){
        return new NetworkInformation(NETWORK_ID, NAME, ICON, getSmallAppIconString(), DESCRIPTION);
    }

    protected void save(){
        getNetworksData().save("data", ".", NETWORK_ID, ErgoNetwork.NETWORK_ID, getJsonObject());
    }

    public String getName(){
        return NAME;
    }

    public Image getIcon(){
        return getSmallAppIcon();
    }




    protected ExecutorService getExecService(){
        return getNetworksData().getExecService();
    }

    



    public NetworksData getNetworksData(){
        return m_networksData;
    }

    public SimpleLongProperty timeStampProperty(){
        return m_timeStampProperty;
    }

    private Image m_smallAppIcon = new Image(getSmallAppIconString());
    public Image getSmallAppIcon() {
        return m_smallAppIcon;
    }


   

    public static String getAppIconString(){
        return "/assets/ergo-wallet.png";
    }

    public static String getSmallAppIconString(){
        return "/assets/ergo-wallet-30.png";
    }


    private boolean isLocationAuthorized(String locationString){
        if(locationString != null){
            return m_authorizedLocations.contains(locationString);
        }
        return false;
    }
    
    private boolean addAuthorizedLocation(String locationString){
        if(locationString != null && !locationString.equals(NetworksData.UNKNOWN_LOCATION) && !m_authorizedLocations.contains(locationString)){
            boolean added = m_authorizedLocations.add(locationString);
            
            save();
            return added;
        }
        return false;
    }

    protected boolean removeAuthorizedLocation(String locationString){
        if(locationString != null){
            boolean removed = m_authorizedLocations.remove(locationString);
            
            save();
            return removed;
        }
        return false;
    }


    public void addNetworksDataListener(){
        if(m_networksDataMsgInterface == null){

            m_networksDataMsgInterface = new NoteMsgInterface() {

                @Override
                public String getId() {
                    
                    return m_locationId;
                }

                @Override
                public void sendMessage(int code, long timestamp, String networkId, String msg) {
                    if(code == NoteConstants.LIST_ITEM_REMOVED && networkId != null && networkId.equals(NetworksData.APPS)){
                      
                        checkLocations(msg); 
                        
                    }
                }

                @Override
                public void sendMessage(int code, long timestamp, String networkId, Number number) {
                    
                }
                
            };
  
            getNetworksData().addMsgListener(m_networksDataMsgInterface);
        }
    }

    private void checkLocations(String msg){
        JsonElement jsonElement = msg != null ? new JsonParser().parse(msg) : null;
        JsonElement nameElement = jsonElement != null && jsonElement.isJsonObject() ? jsonElement.getAsJsonObject().get("name") : null;
        
        if(nameElement != null && !nameElement.isJsonNull() && nameElement.isJsonPrimitive()){
            String locationString = nameElement.getAsString();
            if(m_controlIds.containsValue(locationString)){
                removeControlLocation(locationString);
            }
        }
        
    }

    private void removeControlLocation(String locationString){
        ArrayList<String> keysToRemove = new ArrayList<>();
        for (Map.Entry<String, String> entry : m_controlIds.entrySet()) {
            
            if(entry.getValue().equals(locationString)){
                String key = entry.getKey();
                keysToRemove.add(key);

                NoteMsgInterface controlMsgInterface = getControlMsgInterface(key);
                if(controlMsgInterface != null){
                    removeMsgListener(controlMsgInterface);
                }
            }
        }
        for(String key : keysToRemove){
            m_controlIds.remove(key);
        }

        m_authorizedLocations.remove(locationString);
    }

    private NoteMsgInterface getControlMsgInterface(String key){
        for(int i = 0; i < m_controlInterfaces.size() ; i++){
            
            NoteMsgInterface msgInterface = m_controlInterfaces.get(i);
            if(msgInterface.getId().equals(key)){
                return msgInterface;
            }
        }
        return null;
    }
    

    public Future<?> sendNote(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        JsonElement cmdElement = note.get(NoteConstants.CMD);
        if(m_ergoWalletDataList != null){
            if (cmdElement != null && !cmdElement.isJsonNull() && cmdElement.isJsonPrimitive()) {
                
                JsonElement locationIdElement = note.get("locationId");
                String locationId = locationIdElement != null && locationIdElement.isJsonPrimitive() ? locationIdElement.getAsString() : null; 
        
                String locationString = getNetworksData().getLocationString(locationId);

                boolean isLocation = isLocationAuthorized(locationString);
                boolean isThisApp = m_locationId.equals(locationId);
                String cmd  = cmdElement.getAsString();


                if(isThisApp || isLocation){
                    JsonElement controlIdElement = note.get("controlId");
                   
                    if(controlIdElement == null && cmd.equals("getControlId")){
                        
                        getControlId(locationString, note, onSucceeded, onFailed);

                    }else if(!controlIdElement.isJsonNull() && isControlLocation(controlIdElement.getAsString(), locationString)){
                        
                        switch(cmd){
                            case "getWallets":
                                return Utils.returnObject(m_ergoWalletDataList.getWallets(), getExecService(), onSucceeded);
                            case "getWalletById":
                                return Utils.returnObject(m_ergoWalletDataList.getWalletById(note), getExecService(), onSucceeded);
                            case "openWallet":
                                return m_ergoWalletDataList.openWallet(note, onSucceeded, onFailed);
                            case "openWalletFile":
                                m_ergoWalletDataList.openWalletFile(note, onSucceeded, onFailed);
                            break;
                            case "removeWallets":
                                return Utils.returnObject(m_ergoWalletDataList.removeWallets(note), getExecService(), onSucceeded);
                            case "createWallet":
                                m_ergoWalletDataList.createWallet(note, onSucceeded, onFailed);
                            break;
                            case "getAccessId":
                                m_ergoWalletDataList.getAccessId(locationString, note, onSucceeded, onFailed);
                            break;
                            default:
                                JsonElement accessIdElement = note.get("accessId");
                                String accessId = accessIdElement != null && !accessIdElement.isJsonNull() ? accessIdElement.getAsString() : null;
                                if(accessId != null){
                                    m_ergoWalletDataList.sendNote(cmd, accessId, locationString, note, onSucceeded, onFailed);
                                }
                        }
                    }else{

                        return Utils.returnException("Control required", getExecService(), onFailed);
                    }
                }else if(locationString != null && !locationString.equals(NetworksData.UNKNOWN_LOCATION)){
                    getNetworksData().verifyAppKey("Ergo Wallets", note, locationString, System.currentTimeMillis(), ()->{
                        addAuthorizedLocation(locationString);
                        sendNote(note, onSucceeded, onFailed);
                    });
                }
            
            }else{
                return Utils.returnException(NoteConstants.CMD_NOT_PRESENT, getExecService(), onFailed);
            }
        }else{
            return Utils.returnException(NoteConstants.STATUS_STARTING, getExecService(), onFailed);
        }
       
        return null;

    }

    private boolean addControlId(String controlId, String locationId){
        if(controlId != null && !m_controlIds.containsKey(controlId)){
            m_controlIds.put(controlId, locationId);
            return true;
        }
        return false;
    }

    private boolean removeControlId(String controlId){
        if(controlId != null){
            m_controlIds.remove(controlId);
            ArrayList<NoteMsgInterface> listeners = new ArrayList<>();

            for(int i = 0; i < msgListeners().size(); i++){
                NoteMsgInterface listener = msgListeners().get(i);
                if(listener.getId().startsWith(controlId)){
                    listeners.add(listener);
                }
            }

            for(NoteMsgInterface listener : listeners){
                removeMsgListener(listener);
            }
            return true;
        }
        return false;
    }


    private boolean isControl(String controlId){
        return controlId != null && m_controlIds.containsKey(controlId); 
    }

    private boolean isControlLocation(String controlId, String locationId){
        String controlLocation = controlId != null && locationId != null ? m_controlIds.get(controlId) : null; 
    
        return controlLocation != null && controlLocation.equals(locationId);
    }


    private Future<?> getControlId(String locationString, JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        

        SimpleStringProperty  controlId = new SimpleStringProperty(FriendlyId.createFriendlyId());
        while(isControl(controlId.get())){
            controlId.set(FriendlyId.createFriendlyId());
        }

        addControlId(controlId.get(), locationString);

        JsonObject json = new JsonObject();
        json.addProperty("controlId",  controlId.get());
    
        return Utils.returnObject(json, getExecService(), onSucceeded);
    
    }
    

    public String getDescription(){
        return DESCRIPTION;
    }

    private TabInterface m_tabInterface = null;

    @Override
    public TabInterface getTab(Stage appStage,  SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, Button menuBtn){
        if(m_tabInterface == null){
            m_tabInterface = new ErgoWalletsTab(appStage, heightObject, widthObject, menuBtn);
        }
        return m_tabInterface;
    }

    @Override
    protected void sendMessage(int code, long timeStamp,String networkId, String msg){
        if(networkId != null){
            if(networkId.equals(NETWORK_ID)){
                for(int i = 0; i < m_controlInterfaces.size() ; i++){
                    NoteMsgInterface controlInterface = m_controlInterfaces.get(i);
                    String msgInterfaceId = controlInterface.getId();
    
                    if(isControl(msgInterfaceId)){
                        controlInterface.sendMessage(code, timeStamp, networkId, msg);
                    }
                }
            }else if(m_ergoWalletDataList != null){
                int indexOfColon = networkId.indexOf(":");
       
                String walletId = indexOfColon > 0 ? networkId.substring(0, indexOfColon) : networkId;
                if(walletId != null){
                    for(int i = 0; i < msgListeners().size() ; i++){
                        NoteMsgInterface msgInterface = msgListeners().get(i);
                        String msgInterfaceId = msgInterface.getId();
                        String interfaceWalletId = m_ergoWalletDataList.getWalletIdFromAccessId(msgInterfaceId);
                        if(interfaceWalletId != null && interfaceWalletId.equals(walletId)){
                            msgInterface.sendMessage(code, timeStamp, networkId, msg);
                        }
                    }
                }
                
            }
        }
        if(m_tabInterface != null){
            m_tabInterface.sendMessage(code, timeStamp, networkId, msg);
        }
    }

    @Override
    protected void sendMessage(int code, long timeStamp, String networkId, Number num){
        if(networkId != null){
            if(networkId.equals(NETWORK_ID)){
                for(int i = 0; i < m_controlInterfaces.size() ; i++){
                    NoteMsgInterface controlInterface = m_controlInterfaces.get(i);
                    String msgInterfaceId = controlInterface.getId();
    
                    if(isControl(msgInterfaceId)){
                        controlInterface.sendMessage(code, timeStamp, networkId, num);
                    }
                }
            }else if(m_ergoWalletDataList != null){
                int indexOfColon = networkId.indexOf(":");
       
                String walletId = indexOfColon > -1 ? networkId.substring(0, indexOfColon) : null;
                if(walletId != null){
                    for(int i = 0; i < msgListeners().size() ; i++){
                        NoteMsgInterface msgInterface = msgListeners().get(i);
                        String msgInterfaceId = msgInterface.getId();

                        String interfaceWalletId = m_ergoWalletDataList.getWalletIdFromAccessId(msgInterfaceId);
                        
                        if(interfaceWalletId != null && interfaceWalletId.equals(walletId)){
                            msgInterface.sendMessage(code, timeStamp, networkId, num);
                        }
                    }
                }
            }
        }
    }
    

    @Override
    public void addMsgListener(NoteMsgInterface item) {
        String itemId = item != null ? item.getId() : null;

        if(itemId != null){
            if(isControl(itemId)){
                
                m_controlInterfaces.add(item);
            }else if(m_ergoWalletDataList != null && m_ergoWalletDataList.hasAccess(itemId)){
           
                super.addMsgListener(item);
            }
        }
    }

    @Override
    public boolean removeMsgListener(NoteMsgInterface item){
        if(item != null){
            String itemId = item.getId();
            if(isControl(itemId)){

                m_controlInterfaces.remove(item);
                
                return removeControlId(itemId);
            
            }else{

                boolean removed = msgListeners().remove(item);
                m_ergoWalletDataList.removeAccessId(itemId);
            
                if(msgListeners().size() == 0){
                    stop();
                }
                return removed;
            }
        }

        return false;
    }
   
    

    public class ErgoWalletsTab extends AppBox implements TabInterface {

        private boolean m_current = false;
    
        private final String DISABLED_NETWORK_TEXT = "Ergo Network: (disabled)";
        private final String UNKNOWN_NETWORK_TEXT = "Network: (information unavailable)";
        private MenuButton networkMenuBtn = null;
        private ImageView m_networkMenuBtnImageView = null;
        private Tooltip networkTip = null;

        
        private final ErgoWalletControl m_walletControl;
    
        private SimpleBooleanProperty showWallet = new SimpleBooleanProperty(false);
        private SimpleBooleanProperty showBalance = new SimpleBooleanProperty(false);
    
        private SimpleObjectProperty<AppBox> m_currentBox = new SimpleObjectProperty<>(null);
      
        private VBox m_mainBox;
    
        private Stage m_appStage;
    
        private ErgoWalletAmountBoxes m_amountBoxes = null;
    
        private long m_lastAddressUpdated = 0;
        private LockField m_lockBox;
    
    
        //private TextField m_walletField;    
        private HBox m_walletFieldBox;
        private VBox m_walletBodyBox;
        private Button m_disableWalletBtn;
        private VBox m_selectedAddressBox;
        private MenuButton m_openWalletBtn;
    
        private JsonParser m_jsonParser = new JsonParser();
    
        private static long m_minNanoErgs = ErgoConstants.MIN_NANO_ERGS;
        private static BigDecimal m_minNetworkFee = ErgoConstants.MIN_NETWORK_FEE;

 
    
    
        public ErgoWalletsTab(Stage appStage, SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, Button menuBtn) {
            super();
       
            m_appStage = appStage;
    
            m_networkMenuBtnImageView = new ImageView(new Image(NetworkConstants.NETWORK_ICON));
            m_networkMenuBtnImageView.setPreserveRatio(true);
            m_networkMenuBtnImageView.setFitWidth(30);

            networkTip = new Tooltip(DISABLED_NETWORK_TEXT);
            networkTip.setShowDelay(new javafx.util.Duration(50));
            networkTip.setFont(Stages.txtFont);

            networkMenuBtn = new MenuButton();
            networkMenuBtn.setGraphic(m_networkMenuBtnImageView);
            networkMenuBtn.setPadding(new Insets(0, 3, 0, 0));

            m_walletControl = new ErgoWalletControl("ErgoWalletsTab",ErgoWallets.NETWORK_ID, ErgoNetwork.NETWORK_ID, NETWORK_TYPE, m_locationId, getNetworksData());
      
            m_lockBox = new LockField(m_walletControl.currentAddressProperty());
            
            
    
            ImageView walletIconView = new ImageView(new Image(ICON));
    
            walletIconView.setPreserveRatio(true);
            walletIconView.setFitWidth(18);
    
            HBox topIconBox = new HBox(walletIconView);
            topIconBox.setAlignment(Pos.CENTER_LEFT);
            topIconBox.setMinWidth(30);
            
           
    
            Button toggleShowBalance = new Button(showBalance.get() ? "⏷" : "⏵");
            toggleShowBalance.setId("caretBtn");
            toggleShowBalance.setOnAction(e -> {
                if (m_walletControl.isUnlocked()) {
                    showBalance.set(!showBalance.get());
                } else {
                    m_lockBox.requestFocus();
                }
            });
    
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
    
    
            Button toggleShowWallets = new Button(showWallet.get()? "⏷"  : "⏵");
            toggleShowWallets.setId("caretBtn");
            toggleShowWallets.setOnAction( e -> {
                if (m_walletControl.getCurrentAddress() != null) {
                    showWallet.set(!showWallet.get());
                } else {
                    m_openWalletBtn.show();
                }
            });
        
        
            m_openWalletBtn = new MenuButton();
            m_openWalletBtn.setId("arrowMenuButton");
            m_openWalletBtn.getItems().add(new MenuItem("Error no connection"));
    
    
            MenuButton walletMenuBtn = new MenuButton("⋮");
    
            Text walletTopLabel = new Text("Wallet");
            walletTopLabel.setFont(Stages.txtFont);
            walletTopLabel.setFill(Stages.txtColor);
    
            MenuItem openWalletMenuItem = new MenuItem("⇲   Open…");
    
            MenuItem newWalletMenuItem = new MenuItem("⇱   New…");
    
            MenuItem restoreWalletMenuItem = new MenuItem("⟲   Restore…");
    
            MenuItem removeWalletMenuItem = new MenuItem("🗑   Remove…");
    
           
      
    
            walletMenuBtn.getItems().addAll(newWalletMenuItem, openWalletMenuItem, restoreWalletMenuItem, removeWalletMenuItem);
    
    
            MenuButton adrMenuBtn = new MenuButton("⋮");
    
        
            HBox adrMenuBtnBox = new HBox(adrMenuBtn);
            adrMenuBtnBox.setAlignment(Pos.CENTER_LEFT);
    
    
           
            
            MenuItem sendMenuItem =             new MenuItem("⮩  Send Assets");
            MenuItem txsMenuItem =              new MenuItem("≔ Transactions");
    
            MenuItem copyAdrMenuItem =          new MenuItem("⧉  Copy address to clipbord");
            MenuItem magnifyMenuItem =          new MenuItem("🔍 View Address");
            MenuItem recoverMnemonicMenuItem =  new MenuItem("⥀  View Mnemonic");
            
    
            adrMenuBtn.getItems().addAll(sendMenuItem, txsMenuItem,new SeparatorMenuItem(), copyAdrMenuItem, magnifyMenuItem, recoverMnemonicMenuItem);
            
    
    
            Runnable hideMenus = () ->{
                walletMenuBtn.hide();
                adrMenuBtn.hide();
                m_openWalletBtn.hide();            
            };
            
            copyAdrMenuItem.setOnAction(e->{
                hideMenus.run();
                m_walletControl.copyCurrentAddressToClipboard(adrMenuBtn);
            });
    
    
            sendMenuItem.setOnAction(e->{
                hideMenus.run();
                m_currentBox.set(new SendAppBox());
            });
    
            txsMenuItem.setOnAction(e->{
                hideMenus.run();
                m_currentBox.set(new WalletTxBox());
            });
    
            magnifyMenuItem.setOnAction(e->{
                hideMenus.run();
                m_walletControl.showAddressStage();
            });
    
            recoverMnemonicMenuItem.setOnAction(e->{
                hideMenus.run();
                m_walletControl.showWalletMnemonic();
            });
             
    
            Runnable openWallet = () -> {
                hideMenus.run();
                
                m_walletControl.openWalletFile((onFailed->{

                }));
                
            };
    
            Runnable newWallet = () -> {
                hideMenus.run();
                m_walletControl.createWallet(true, onFailed->{

                });
                
            };
    
            Runnable restoreWallet = () -> {
                hideMenus.run();
                m_walletControl.createWallet(false, onFailed->{

                });
            };
           
            Runnable removeWallet = () ->{
                hideMenus.run();
                m_currentBox.set(new RemoveWalletBox());
            };
     
    
            openWalletMenuItem.setOnAction(e -> openWallet.run());
            newWalletMenuItem.setOnAction(e -> newWallet.run());
            restoreWalletMenuItem.setOnAction(e -> restoreWallet.run());
            removeWalletMenuItem.setOnAction(e -> removeWallet.run());
    
    
    
            m_walletFieldBox = new HBox(m_openWalletBtn);
            HBox.setHgrow(m_walletFieldBox, Priority.ALWAYS);
            m_walletFieldBox.setAlignment(Pos.CENTER_LEFT);
            m_walletFieldBox.setId("bodyBox");
            m_walletFieldBox.setPadding(new Insets(0, 1, 0, 0));
            m_openWalletBtn.prefWidthProperty().bind(m_walletFieldBox.widthProperty().subtract(1));
    
            HBox walletMenuBtnPadding = new HBox(walletMenuBtn);
            walletMenuBtnPadding.setPadding(new Insets(0, 0, 0, 5));
    
            
    
            HBox walletBtnBox = new HBox(m_walletFieldBox, walletMenuBtnPadding);
            walletBtnBox.setPadding(new Insets(2, 2, 0, 5));
            HBox.setHgrow(walletBtnBox, Priority.ALWAYS);
            walletBtnBox.setAlignment(Pos.CENTER_LEFT);
    
         
      
    
            MenuItem openWalletItem = new MenuItem("[Open]");
            openWalletItem.setOnAction(e -> openWallet.run());
    
            MenuItem newWalletItem = new MenuItem("[New]");
            newWalletItem.setOnAction(e -> newWallet.run());
    
            MenuItem restoreWalletItem = new MenuItem("[Restore]                ");
            restoreWalletItem.setOnAction(e -> restoreWallet.run());
    
            MenuItem removeWalletItem = new MenuItem("[Remove]");
            removeWalletItem.setOnAction(e -> removeWallet.run());
    
      
    
            /*m_walletField.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                openWalletBtn.show();
            });*/
    
            m_openWalletBtn.showingProperty().addListener((obs,oldval,newval)->{
                if(newval){
                    m_openWalletBtn.getItems().clear();
                    
                    m_walletControl.getWallets((onWallets)->{
                        Object onWalletsObject = onWallets.getSource().getValue();

                        JsonArray walletIds = onWalletsObject != null && onWalletsObject instanceof JsonArray ? (JsonArray) onWalletsObject : null;
                        if (walletIds != null) {
                            
                            for (JsonElement element : walletIds) {
                                if (element != null && element instanceof JsonObject) {
                                    JsonObject json = element.getAsJsonObject();
        
                                    String name = json.get("name").getAsString();
                                    //String id = json.get("id").getAsString();
        
                                    MenuItem walletItem = new MenuItem(String.format("%-50s", " " + name));
        
                                    walletItem.setOnAction(action -> {
                                        m_walletControl.setWalletObject(json);
                                        m_walletControl.connectToWallet();
                                    });
        
                                    m_openWalletBtn.getItems().add(walletItem);
                                }
                            }
                            m_openWalletBtn.getItems().addAll(newWalletItem, openWalletItem, restoreWalletItem, removeWalletItem);
                        }else{
                            m_openWalletBtn.getItems().add(new MenuItem("Wallets unavailable"));
                        }
        
                     
                    },onFailed->{
                        Throwable failedThrowable = onFailed.getSource().getException();           
                        String msg = failedThrowable != null && failedThrowable.getMessage().length() > 0 ? failedThrowable.getMessage() : "Ergo Wallets: (unavailable)";
                        m_openWalletBtn.getItems().clear();
                        m_openWalletBtn.getItems().add(new MenuItem( msg ));
                    });
    
                   
                }
            });
    
            HBox walletLabelBox = new HBox(walletTopLabel);
            walletLabelBox.setPadding(new Insets(0, 5, 0, 5));
            walletLabelBox.setAlignment(Pos.CENTER_LEFT);
    
            HBox walletsTopBar = new HBox(toggleShowWallets, topIconBox, walletLabelBox, walletBtnBox);
            walletsTopBar.setAlignment(Pos.CENTER_LEFT);
            walletsTopBar.setPadding(new Insets(2));
    
            m_walletBodyBox = new VBox();
            m_walletBodyBox.setPadding(new Insets(0, 0, 0, 5));
    
    
            VBox walletBodyPaddingBox = new VBox();
            HBox.setHgrow(walletBodyPaddingBox, Priority.ALWAYS);
    
            VBox walletLayoutBox = new VBox(walletsTopBar, walletBodyPaddingBox);
            HBox.setHgrow(walletLayoutBox, Priority.ALWAYS);
    
            m_mainBox = new VBox(walletLayoutBox);
            m_mainBox.setPadding(new Insets(0));
    
            m_disableWalletBtn = new Button("☓");
            m_disableWalletBtn.setId("lblBtn");
    
            m_disableWalletBtn.setOnMouseClicked(e -> {
                showWallet.set(false);
                m_walletControl.disconnectWallet();
                m_walletControl.setWalletObject(null);

            });
    
            m_currentBox.addListener((obs, oldval, newval) -> {
                m_mainBox.getChildren().clear();
                if (newval != null) {
                    m_mainBox.getChildren().add(newval);
                } else {
                    m_mainBox.getChildren().add(walletLayoutBox);
                }
    
            });     
    
            Runnable updateShowWallets = () -> {
                boolean isShowWallet = showWallet.get();
                toggleShowWallets.setText(isShowWallet ? "⏷" : "⏵");
    
                if (isShowWallet) {
                    if (!walletBodyPaddingBox.getChildren().contains(m_walletBodyBox)) {
                        walletBodyPaddingBox.getChildren().add(m_walletBodyBox);
                    }
                } else {
                    if (walletBodyPaddingBox.getChildren().contains(m_walletBodyBox)) {
                        walletBodyPaddingBox.getChildren().remove(m_walletBodyBox);
                    }
                }
            };
    
            updateShowWallets.run();
    
            showWallet.addListener((obs, oldval, newval) -> updateShowWallets.run());
            
            m_lockBox.setOnMenuShowing((obs,oldval,newval) -> {
                long currentTime = System.currentTimeMillis();
                long lastUpdated = currentTime - m_lastAddressUpdated; 
                if(newval && lastUpdated > 2000){
                    m_lastAddressUpdated = lastUpdated;
                    m_lockBox.getItems().clear();
                    m_lockBox.getItems().add(new MenuItem("Getting addresses..."));
                    if (m_walletControl.getCurrentAddress() != null) {
                        
                         m_walletControl.getAddresses((onSucceeded)->{
                            Object obj = onSucceeded.getSource().getValue();
                            m_lockBox.getItems().clear();
                            JsonArray jsonArray = obj != null & obj instanceof JsonArray ? (JsonArray) obj : null;
                            if(jsonArray != null){
                                for (int i = 0; i < jsonArray.size(); i++) {
                                    JsonElement element = jsonArray.get(i);
                                    if (element != null && element.isJsonObject()) {
        
                                        JsonObject jsonObj = element.getAsJsonObject();
                                        String address = jsonObj.get("address").getAsString();
                                        String name = jsonObj.get("name").getAsString();
        
                                        MenuItem addressMenuItem = new MenuItem(name + ": " + address);
                                        addressMenuItem.setOnAction(e1 -> {
                                            m_walletControl.setCurrentAddress(address);
                                        });
        
                                        m_lockBox.getItems().add(addressMenuItem);
                                    }
                                }
    
                            }else{
                                m_lockBox.getItems().clear();
                                m_lockBox.getItems().add(new MenuItem("Error: (no addresses)"));
                            }
                        }, onFailed->{
                            Throwable throwable = onFailed.getSource().getException();
                            String msg = throwable != null && throwable.getMessage().length() > 0 ? throwable.getMessage() : "(failed)";
                            m_lockBox.getItems().clear();
                            m_lockBox.getItems().add(new MenuItem("Error: (" + msg + ")"));
                        });
                    }
                }
            });
    
            HBox adrBtnBoxes = new HBox();
            adrBtnBoxes.setAlignment(Pos.CENTER_LEFT);
    
            Tooltip copyToolTip = new Tooltip("Copy Address To Clipboard");
            copyToolTip.setShowDelay(Duration.millis(100));
    
            Button copyBtn = new Button("⧉");
            copyBtn.setId("lblBtn");
            copyBtn.setTooltip(copyToolTip);
            copyBtn.setOnAction(e->{
                m_walletControl.copyCurrentAddressToClipboard(copyBtn);
            });
    
            Tooltip magnifyTip = new Tooltip("🔍 View Address");
            magnifyTip.setShowDelay(Duration.millis(100));
            Button magnifyBtn = new Button("🔍");
            magnifyBtn.setId("lblBtn");
            magnifyBtn.setTooltip(magnifyTip);
            magnifyBtn.setOnAction(e->{
                m_walletControl.showAddressStage();
            });
            
            Tooltip mnemonicToolTip = new Tooltip("⥀ View Mnemonic");
            mnemonicToolTip.setShowDelay(Duration.millis(100));
            Button mnemonicBtn = new Button("⥀");
            mnemonicBtn.setId("lblBtn");
            mnemonicBtn.setTooltip(mnemonicToolTip);
            mnemonicBtn.setOnAction(e->{
                hideMenus.run();
                m_walletControl.showWalletMnemonic();
            });
    
            HBox addressCtlBtns = new HBox(magnifyBtn, copyBtn, mnemonicBtn);
    
            HBox paddingBtnBox = new HBox();
            paddingBtnBox.setPadding(new Insets(2));
    
            HBox addressBtnsBox = new HBox(toggleShowBalance, m_lockBox,paddingBtnBox, adrBtnBoxes);
            addressBtnsBox.setPadding(new Insets(2, 0, 2, 0));
            addressBtnsBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(addressBtnsBox, Priority.ALWAYS);
    
            HBox.setHgrow(m_lockBox,Priority.ALWAYS);
    
            VBox walletBalanceBox = new VBox();
            walletBalanceBox.setPadding(new Insets(2, 0, 2, 5));
            walletBalanceBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(walletBalanceBox, Priority.ALWAYS);
    
            HBox actionBox = new HBox();
            HBox.setHgrow(actionBox, Priority.ALWAYS);
    
            VBox adrBox = new VBox(actionBox, addressBtnsBox, walletBalanceBox);
            adrBox.setPadding(new Insets(0, 2, 5, 5));
            HBox.setHgrow(adrBox, Priority.ALWAYS);
            adrBox.setAlignment(Pos.CENTER_LEFT);
    
            adrBox.setPadding(new Insets(2));
            ///
    
    
            m_selectedAddressBox = new VBox(adrBox);
            HBox.setHgrow(m_selectedAddressBox, Priority.ALWAYS);
            m_selectedAddressBox.setAlignment(Pos.TOP_LEFT);
    
            
            Button sendBtn = new Button("Send");
            sendBtn.setId("lblBtn");
            sendBtn.setOnAction(e->{
                hideMenus.run();
                m_currentBox.set(new SendAppBox());
            });
            
            Region adrCtlBtnSpacer1 = new Region();
            adrCtlBtnSpacer1.setMinWidth(2);
            adrCtlBtnSpacer1.setMinHeight(Stages.MENU_BAR_IMAGE_WIDTH);
            adrCtlBtnSpacer1.setId("vGradient");
    
            HBox adrCtlBtnSpacerBox1 = new HBox(adrCtlBtnSpacer1);
            adrCtlBtnSpacerBox1.setPadding(new Insets(0,5,0,10));
    
            Button txBtn = new Button("Transactions");
            txBtn.setId("lblBtn");
            txBtn.setOnAction(e->{
                hideMenus.run();
                m_currentBox.set(new WalletTxBox());
            });
    
            Region adrCtlBtnSpacer2 = new Region();
            adrCtlBtnSpacer2.setMinWidth(2);
            adrCtlBtnSpacer2.setMinHeight(Stages.MENU_BAR_IMAGE_WIDTH);
            adrCtlBtnSpacer2.setId("vGradient");
    
            HBox adrCtlBtnSpacerBox2 = new HBox(adrCtlBtnSpacer2);
            adrCtlBtnSpacerBox2.setPadding(new Insets(0,5,0,10));
    
    
            Button swapBtn = new Button("Swap");
            swapBtn.setId("lblBtn");
            swapBtn.setOnAction(e->{
                hideMenus.run();
                m_currentBox.set(new WalletSwapBox());
            });
    
            HBox addressCtlExtBtnBox = new HBox(sendBtn,adrCtlBtnSpacerBox1, txBtn, adrCtlBtnSpacerBox2, swapBtn);
            HBox.setHgrow(addressCtlExtBtnBox, Priority.ALWAYS);
            
            HBox addressCtrlBtnBox = new HBox( addressCtlExtBtnBox);
    
            HBox addressControlBox = new HBox(addressCtrlBtnBox);
            HBox.setHgrow(addressControlBox, Priority.ALWAYS);
            addressControlBox.setPadding(new Insets(0,10,0,0));
            
            m_amountBoxes = new ErgoWalletAmountBoxes(true, NETWORK_TYPE, m_walletControl.balanceProperty());
    
            HBox balanceGradient = new HBox();
            balanceGradient.setId("hGradient");
            HBox.setHgrow(balanceGradient, Priority.ALWAYS);
            balanceGradient.setPrefHeight(2);
            balanceGradient.setMinHeight(2);
    
            VBox balancePaddingBox = new VBox(addressControlBox, balanceGradient, m_amountBoxes);
            balancePaddingBox.setPadding(new Insets(0, 0, 0, 0));
            HBox.setHgrow(balancePaddingBox, Priority.ALWAYS);
            
          
    
    
            ChangeListener<String> currentAddressListener = (obs, oldval, newval) -> {
            
                if (oldval != null) {
                    showBalance.set(false);
                }
    
                if (newval != null ) {
                    showBalance.set(true);
                    addLoadingBox();
                    setLoadingMsg("Opening...");
                    if (!adrBtnBoxes.getChildren().contains(adrMenuBtnBox)) {
                        adrBtnBoxes.getChildren().add(adrMenuBtnBox);
                    }
                } else {
                    if (adrBtnBoxes.getChildren().contains(adrMenuBtnBox)) {
                        adrBtnBoxes.getChildren().remove(adrMenuBtnBox);
                    }
                }
            };
    
            m_walletControl.currentAddressProperty().addListener(currentAddressListener);
    
            m_walletControl.walletObjectProperty().addListener((obs, oldVal, newVal) -> {
                if(m_amountBoxes != null){
                    m_amountBoxes.shutdown();
                }
                updateWallet(newVal == null);
            });
    
            m_walletControl.balanceProperty().addListener((obs,oldval,newval)->{
                if(newval != null){
                    removeLoadingBox();
                }
            });
    
            m_lockBox.setOnLockBtn((onLock)->m_walletControl.disconnectWallet());
    
            showBalance.addListener((obs, oldval, newval) -> {
                toggleShowBalance.setText(newval ? "⏷" : "⏵");
               
                if (newval) {
                    
                    if (!walletBalanceBox.getChildren().contains(balancePaddingBox)) {
                        walletBalanceBox.getChildren().add(balancePaddingBox);
                        
                    }
                    if(!paddingBtnBox.getChildren().contains(addressCtlBtns)){
                        paddingBtnBox.getChildren().add(addressCtlBtns);
                    }
    
                } else {
                    
                    if (walletBalanceBox.getChildren().contains(balancePaddingBox)) {
                        walletBalanceBox.getChildren().remove(balancePaddingBox);
                    }
    
                    if(paddingBtnBox.getChildren().contains(addressCtlBtns)){
                        paddingBtnBox.getChildren().remove(addressCtlBtns);
                    }
                }
            });
           
            m_lockBox.setPasswordAction(e -> {
                m_walletControl.connectToWallet();
            });
  
            getChildren().add(m_mainBox);
        }
    
    
        
        private ProgressBar progressBar = null;
        private Label loadingText = null;
        private HBox loadingBarBox = null;
        private HBox loadingTextBox = null;
        private VBox loadingVBox = null;
    
        private void setLoadingMsg(String msg){
            m_loadingMsgProperty.set(msg);
        }
        
        private SimpleStringProperty m_loadingMsgProperty = new SimpleStringProperty("Loading...");
    
        public void addLoadingBox(){
            if(loadingVBox == null){
    
                progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
                progressBar.setPrefWidth(150);
                loadingText = new Label();
                loadingText.textProperty().bind(m_loadingMsgProperty);
    
                loadingBarBox = new HBox(progressBar);
                HBox.setHgrow(loadingBarBox, Priority.ALWAYS);
                loadingBarBox.setAlignment(Pos.CENTER);
    
                loadingTextBox = new HBox(loadingText);
                HBox.setHgrow(loadingTextBox, Priority.ALWAYS);
                loadingTextBox.setAlignment(Pos.CENTER);
    
                loadingVBox = new VBox(loadingBarBox, loadingTextBox);
                VBox.setVgrow(loadingVBox,Priority.ALWAYS);
                HBox.setHgrow(loadingBarBox,Priority.ALWAYS);
                loadingVBox.setAlignment(Pos.CENTER);
                loadingVBox.setMinHeight(200);
                getChildren().add(loadingVBox);
            }
        }
    
        public void removeLoadingBox(){
            if(loadingVBox != null){
        
                if(getChildren().contains(loadingVBox)){
                    getChildren().remove(loadingVBox);
                }
                loadingVBox.getChildren().clear();
                loadingTextBox.getChildren().clear();
                loadingBarBox.getChildren().clear();
    
                loadingText.textProperty().unbind();
                progressBar.setProgress(0);
                progressBar = null;
                loadingText = null;
                loadingBarBox = null;
                loadingTextBox = null;
                loadingVBox = null;
            }
    
        }
    
    
        public void shutdown() {
            m_walletControl.shutdown();
            if(m_amountBoxes != null){
                m_amountBoxes.shutdown();
            }
        }
    
        public void setCurrent(boolean value) {
            m_current = value;
        }
    
        public boolean getCurrent() {
            return m_current;
        }
    
        public String getType() {
            return "RowArea";
        }
    
        public boolean isStatic() {
            return false;
        }
    
    
        /*
        private class ConfigBox extends AppBox {
    
            private SimpleStringProperty m_name = new SimpleStringProperty();
            private SimpleStringProperty m_fileName = new SimpleStringProperty();
            private String m_configId;
    
            public void shutdown() {
    
            }
    
            @Override
            public void sendMessage(int code, long timeStamp,String networkId, String msg) {
                
                update();
    
            }
    
            private void update() {
                NoteInterface noteInterface = m_selectedWallet.get();
    
                JsonObject note = Utils.getCmdObject("getFileData");
                note.addProperty("locationId", m_locationId);
                note.addProperty("configId", m_configId);
    
                Object obj = noteInterface.sendNote(note);
                if (obj != null && obj instanceof JsonObject) {
                    JsonObject json = (JsonObject) obj;
                    m_name.set(noteInterface.getName());
                    m_fileName.set(json.get("name").getAsString());
                }
    
            }
    
            public ConfigBox(String configId) {
          
                m_configId = configId;
                Label toggleShowSettings = new Label("⏷ ");
                toggleShowSettings.setId("caretBtn");
    
                Label settingsLbl = new Label("⚙");
                settingsLbl.setId("logoBox");
    
                Text settingsText = new Text("Config");
                settingsText.setFont(Stages.txtFont);
                settingsText.setFill(Stages.txtColor);
    
                Tooltip walletInfoTooltip = new Tooltip("Wallet in use");
    
                HBox settingsBtnsBox = new HBox(toggleShowSettings, settingsLbl, settingsText);
                HBox.setHgrow(settingsBtnsBox, Priority.ALWAYS);
                settingsBtnsBox.setAlignment(Pos.CENTER_LEFT);
    
                Label walletNameText = new Label("Name ");
                // walletNameText.setFill(Stages.txtColor);
                walletNameText.setFont(Stages.txtFont);
                walletNameText.setMinWidth(70);
    
                TextField walletNameField = new TextField();
                HBox.setHgrow(walletNameField, Priority.ALWAYS);
    
                walletNameField.setEditable(false);
    
                Label editNameLabel = new Label("✎");
                editNameLabel.setId("lblBtn");
    
                Button walletNameEnterBtn = new Button("[enter]");
                walletNameEnterBtn.setMinHeight(15);
                walletNameEnterBtn.setText("[enter]");
                walletNameEnterBtn.setId("toolBtn");
                walletNameEnterBtn.setPadding(new Insets(0, 5, 0, 5));
                walletNameEnterBtn.setFocusTraversable(false);
    
                walletNameField.setOnAction(e -> walletNameEnterBtn.fire());
    
                Button walletFileEnterBtn = new Button("[enter]");
                walletFileEnterBtn.setText("[enter]");
                walletFileEnterBtn.setId("toolBtn");
                walletFileEnterBtn.setPadding(new Insets(0, 5, 0, 5));
                walletFileEnterBtn.setFocusTraversable(false);
    
                HBox walletNameFieldBox = new HBox(walletNameField, editNameLabel);
                HBox.setHgrow(walletNameFieldBox, Priority.ALWAYS);
                walletNameFieldBox.setId("bodyBox");
                walletNameFieldBox.setPadding(new Insets(0, 5, 0, 0));
                walletNameFieldBox.setMaxHeight(18);
                walletNameFieldBox.setAlignment(Pos.CENTER_LEFT);
    
                Label walletNameLbl = new Label("  ");
                walletNameLbl.setId("logoBtn");
    
                HBox walletNameBox = new HBox(walletNameLbl, walletNameText, walletNameFieldBox);
                walletNameBox.setAlignment(Pos.CENTER_LEFT);
                walletNameBox.setPadding(new Insets(2, 0, 2, 0));
    
                Label walletFileText = new Label("File");
                walletFileText.setFont(Stages.txtFont);
                walletFileText.setMinWidth(70);
    
                TextField walletFileField = new TextField();
                walletFileField.setEditable(false);
                HBox.setHgrow(walletFileField, Priority.ALWAYS);
                walletFileField.setOnAction(e -> walletFileEnterBtn.fire());
    
                Label walletFileOpenLbl = new Label("…");
                walletFileOpenLbl.setId("lblBtn");
    
                Label walletFileEditLbl = new Label("✎");
                walletFileEditLbl.setId("lblBtn");
    
                HBox walletFileFieldBox = new HBox(walletFileField, walletFileOpenLbl, walletFileEditLbl);
                HBox.setHgrow(walletFileFieldBox, Priority.ALWAYS);
                walletFileFieldBox.setId("bodyBox");
                walletFileFieldBox.setAlignment(Pos.CENTER_LEFT);
                walletFileFieldBox.setMaxHeight(18);
                walletFileFieldBox.setPadding(new Insets(0, 5, 0, 0));
    
                walletFileOpenLbl.setOnMouseClicked(e -> {
                    NoteInterface noteInterface = m_selectedWallet.get();
    
                    if (noteInterface != null) {
                        JsonObject note = Utils.getCmdObject("getFileData");
                        note.addProperty("configId", m_configId);
                        note.addProperty("locationId", m_locationId);
    
                        Object obj = noteInterface.sendNote(note);
    
                        JsonObject json = obj != null && obj instanceof JsonObject ? (JsonObject) obj : null;
                        boolean isFile = json != null ? json.get("isFile").getAsBoolean() : false;
                        String path = isFile ? json.get("path").getAsString() : null;
    
                        File currentFile = path != null ? new File(path) : null;
                        File currentDir = currentFile != null ? currentFile.getParentFile() : AppData.HOME_DIRECTORY;
                        // String fileName = currentFile != null ? currentFile.getName() :
                        // noteInteface.getNetworkId() + ".erg";
    
                        FileChooser openFileChooser = new FileChooser();
                        openFileChooser.setTitle("Select wallet (*.erg)");
                        openFileChooser.setInitialDirectory(currentDir);
                        openFileChooser.getExtensionFilters().add(ErgoWallets.ergExt);
                        openFileChooser.setSelectedExtensionFilter(ErgoWallets.ergExt);
    
                        File walletFile = openFileChooser.showOpenDialog(m_appStage);
    
                        if (walletFile != null) {
                            JsonObject updateFileObject = Utils.getCmdObject("updateFile");
                            updateFileObject.addProperty("file", walletFile.getAbsolutePath());
                            updateFileObject.addProperty("locationId", m_locationId);
                            updateFileObject.addProperty("configId", m_configId);
                            noteInterface.sendNote(updateFileObject);
                        }
                    }
                });
    
                Label walletFileLbl = new Label("  ");
                walletFileLbl.setId("logoBtn");
    
                toggleShowSettings.setOnMouseClicked(e -> {
                    m_configBox.set(null);
                });
    
                HBox walletFileBox = new HBox(walletFileLbl, walletFileText, walletFileFieldBox);
                walletFileBox.setAlignment(Pos.CENTER_LEFT);
                walletFileBox.setPadding(new Insets(2, 0, 2, 0));
    
                VBox settingsBodyBox = new VBox(walletNameBox, walletFileBox);
                settingsBodyBox.setPadding(new Insets(0, 5, 0, 30));
                HBox.setHgrow(settingsBodyBox, Priority.ALWAYS);
    
                getChildren().addAll(settingsBtnsBox, settingsBodyBox);
                HBox.setHgrow(this, Priority.ALWAYS);
    
                Runnable setWalletConfigInfo = () -> {
                    NoteInterface noteInterface = m_selectedWallet.get();
    
                    if (noteInterface == null) {
                        m_configBox.set(null);
                        return;
                    }
                    JsonObject note = Utils.getCmdObject("getFileData");
                    note.addProperty("locationId", m_locationId);
                    note.addProperty("configId", m_configId);
    
                    Object obj = noteInterface.sendNote(note);
                    if (obj != null && obj instanceof JsonObject) {
                        JsonObject json = (JsonObject) obj;
    
                        String filePath = json.get("path").getAsString();
    
                        if (json.get("isFile").getAsBoolean()) {
                            File walletFile = new File(filePath);
                            walletFileField.setText(walletFile.getName());
                        } else {
                            walletFileField.setText("(File not found) " + filePath);
                        }
    
                    } else {
                        walletFileField.setText("(Unable to retreive wallet info) ");
                    }
                    walletNameField.setText(noteInterface.getName());
                };
    
                Runnable setWalletSettingsNonEditable = () -> {
    
                    if (walletNameField.isEditable()) {
                        walletNameField.setEditable(false);
                        if (walletNameFieldBox.getChildren().contains(walletNameEnterBtn)) {
                            walletNameFieldBox.getChildren().remove(walletNameEnterBtn);
                        }
                    }
                    if (walletFileField.isEditable()) {
                        walletFileField.setEditable(false);
                        if (walletFileFieldBox.getChildren().contains(walletFileEnterBtn)) {
                            walletFileFieldBox.getChildren().remove(walletFileEnterBtn);
                        }
                    }
    
                    setWalletConfigInfo.run();
                };
    
                editNameLabel.setOnMouseClicked(e -> {
                    NoteInterface noteInterface = m_selectedWallet.get();
                    if (noteInterface == null) {
                        m_configBox.set(null);
                        return;
                    }
    
                    boolean isOpen = noteInterface != null && noteInterface.getConnectionStatus() != 0;
    
                    if (isOpen) {
                        walletInfoTooltip.setText("Wallet in use");
                        walletInfoTooltip.show(editNameLabel, e.getScreenX(), e.getScreenY());
                        PauseTransition pt = new PauseTransition(Duration.millis(1600));
                        pt.setOnFinished(ptE -> {
                            walletInfoTooltip.hide();
                        });
                        pt.play();
                        setWalletSettingsNonEditable.run();
                    } else {
                        if (walletNameField.isEditable()) {
                            setWalletSettingsNonEditable.run();
                        } else {
                            if (!walletNameFieldBox.getChildren().contains(walletNameEnterBtn)) {
                                walletNameFieldBox.getChildren().add(1, walletNameEnterBtn);
                            }
                            walletNameField.setEditable(true);
                            walletNameField.requestFocus();
    
                        }
                    }
                });
    
                walletNameField.focusedProperty().addListener((obs, oldval, newval) -> {
                    if (!newval) {
                        if (walletNameField.isEditable()) {
                            setWalletSettingsNonEditable.run();
                        }
                    }
                });
    
                walletNameEnterBtn.setOnAction(e -> {
                    NoteInterface noteInterface = m_selectedWallet.get();
                    if (noteInterface == null) {
                        setWalletSettingsNonEditable.run();
                        m_configBox.set(null);
                        return;
                    }
    
                    String name = walletNameField.getText();
    
                    JsonObject json = Utils.getCmdObject("updateName");
                    json.addProperty("name", name);
                    json.addProperty("locationId", m_locationId);
                    json.addProperty("configId", m_configId);
                    JsonObject updatedObj = (JsonObject) noteInterface.sendNote(json);
                    if (updatedObj != null) {
                        JsonElement codeElement = updatedObj.get("code");
                        JsonElement msgElement = updatedObj.get("msg");
    
                        int code = codeElement != null && codeElement.isJsonPrimitive() ? codeElement.getAsInt() : -1;
                        String msg = msgElement != null && msgElement.isJsonPrimitive() ? msgElement.getAsString() : null;
                        if (code != Stages.WARNING) {
                            Point2D p = walletNameField.localToScene(0.0, 0.0);
                            walletInfoTooltip.setText(msg != null ? msg : "Error");
                            walletInfoTooltip.show(walletFileEditLbl,
                                    p.getX() + walletFileField.getScene().getX()
                                            + walletFileField.getScene().getWindow().getX()
                                            + walletFileField.getLayoutBounds().getWidth(),
                                    (p.getY() + walletFileField.getScene().getY()
                                            + walletFileField.getScene().getWindow().getY()) - 30);
                            PauseTransition pt = new PauseTransition(Duration.millis(1600));
                            pt.setOnFinished(ptE -> {
                                walletInfoTooltip.hide();
                            });
                            pt.play();
                        }
                        setWalletSettingsNonEditable.run();
                    }
                });
    
                walletFileEditLbl.setOnMouseClicked(e -> {
                    NoteInterface noteInterface = m_selectedWallet.get();
                    if (noteInterface == null) {
                        m_configBox.set(null);
                        return;
                    }
    
                    boolean isOpen = noteInterface != null && noteInterface.getConnectionStatus() != 0;
    
                    if (isOpen) {
                        walletInfoTooltip.setText("Wallet in use");
                        walletInfoTooltip.show(walletFileEditLbl, e.getScreenX(), e.getScreenY());
                        PauseTransition pt = new PauseTransition(Duration.millis(1600));
                        pt.setOnFinished(ptE -> {
                            walletInfoTooltip.hide();
                        });
                        pt.play();
                        setWalletSettingsNonEditable.run();
                    } else {
                        if (walletFileField.isEditable()) {
                            setWalletSettingsNonEditable.run();
                        } else {
                            if (!walletFileFieldBox.getChildren().contains(walletFileEnterBtn)) {
                                walletFileFieldBox.getChildren().add(1, walletFileEnterBtn);
                            }
                            walletFileField.setEditable(true);
                            JsonObject note = Utils.getCmdObject("getFileData");
                            note.addProperty("configId", m_configId);
                            note.addProperty("locationId", m_locationId);
    
                            Object obj = noteInterface.sendNote(note);
                            if (obj != null && obj instanceof JsonObject) {
                                JsonObject json = (JsonObject) obj;
                                walletFileField.setText(json.get("path").getAsString());
                            }
                            walletFileField.requestFocus();
    
                        }
                    }
                });
    
                walletFileField.focusedProperty().addListener((obs, oldval, newval) -> {
                    if (walletFileField.isEditable() && !newval) {
                        setWalletSettingsNonEditable.run();
                    }
                });
    
                walletFileEnterBtn.setOnAction(e -> {
                    NoteInterface noteInterface = m_selectedWallet.get();
                    if (noteInterface == null) {
                        setWalletSettingsNonEditable.run();
                        m_configBox.set(null);
                        return;
                    }
    
                    String fileString = walletFileField.getText();
    
                    if (fileString.length() > 0 && Utils.findPathPrefixInRoots(fileString)) {
    
                        JsonObject note = Utils.getCmdObject("updateFile");
                        note.addProperty("file", fileString);
                        note.addProperty("locationId", m_locationId);
                        note.addProperty("configId", m_configId);
                        Object obj = noteInterface.sendNote(note);
                        if (obj != null && obj instanceof JsonObject) {
                            JsonObject resultObject = (JsonObject) obj;
                            JsonElement codeElement = resultObject.get("code");
                            JsonElement msgElement = resultObject.get("msg");
                            if (codeElement != null && msgElement != null) {
                                int code = codeElement.getAsInt();
                                String msg = codeElement.getAsString();
                                if (code != Stages.WARNING) {
                                    Point2D p = walletNameField.localToScene(0.0, 0.0);
                                    walletInfoTooltip.setText(msg != null ? msg : "Error");
                                    walletInfoTooltip.show(walletFileEditLbl,
                                            p.getX() + walletFileField.getScene().getX()
                                                    + walletFileField.getScene().getWindow().getX()
                                                    + walletFileField.getLayoutBounds().getWidth(),
                                            (p.getY() + walletFileField.getScene().getY()
                                                    + walletFileField.getScene().getWindow().getY()) - 30);
                                    PauseTransition pt = new PauseTransition(Duration.millis(1600));
                                    pt.setOnFinished(ptE -> {
                                        walletInfoTooltip.hide();
                                    });
                                    pt.play();
                                }
                                setWalletSettingsNonEditable.run();
                            } else {
                                setWalletSettingsNonEditable.run();
                                m_configBox.set(null);
                            }
    
                        } else {
    
                        }
    
                    } else {
                        Point2D p = walletFileField.localToScene(0.0, 0.0);
    
                        walletInfoTooltip.setText("File not found");
    
                        walletInfoTooltip.show(walletFileField,
                                p.getX() + walletFileField.getScene().getX() + walletFileField.getScene().getWindow().getX()
                                        + walletFileField.getLayoutBounds().getWidth(),
                                (p.getY() + walletFileField.getScene().getY()
                                        + walletFileField.getScene().getWindow().getY()) - 30);
                        PauseTransition pt = new PauseTransition(Duration.millis(1600));
                        pt.setOnFinished(ptE -> {
                            walletInfoTooltip.hide();
                        });
                        pt.play();
                    }
                });
    
                m_name.addListener((obs, oldval, newval) -> {
                    if (newval != null) {
                        walletNameField.setText(newval);
                    }
                });
    
                m_fileName.addListener((obs, oldval, newval) -> {
                    if (newval != null) {
                        walletFileField.setText(newval);
                    }
                });
    
                update();
    
            }
    
        }*/
    
      
    
        private class RemoveWalletBox extends AppBox {
    
            private Button m_nextBtn = new Button("Remove");
            private Tooltip m_errTip = new Tooltip();
    
            private SimpleObjectProperty<JsonArray> m_walletIds = new SimpleObjectProperty<>(null);
    
            @Override
            public void sendMessage(int code, long timeStamp,String networkId, String str){
                switch(code){
                    case NoteConstants.LIST_ITEM_ADDED:
                    case NoteConstants.LIST_ITEM_REMOVED:
                    case NoteConstants.LIST_UPDATED:
                        updateWalletList();
                    break;
                    case NoteConstants.ERROR:
                        showError(str);
                    break;
                }
            }
    
            private void showError(String errText){
                double stringWidth =  Utils.computeTextWidth(Stages.txtFont, errText);
                Point2D p = m_nextBtn.localToScene(0.0, 0.0);
                double x =  p.getX() + m_nextBtn.getScene().getX() + m_nextBtn.getScene().getWindow().getX() + (m_nextBtn.widthProperty().get() / 2) - (stringWidth/2);
    
                m_errTip.setText(errText);
                m_errTip.show(m_nextBtn,
                        x,
                        (p.getY() + m_nextBtn.getScene().getY()
                                + m_nextBtn.getScene().getWindow().getY()) - 40);
                PauseTransition pt = new PauseTransition(javafx.util.Duration.millis(5000));
                pt.setOnFinished(ptE -> {
                    m_errTip.hide();
                });
                pt.play();
            }
    
            private void updateWalletList(){
                m_walletControl.getWallets(onSucceeded->{
                    Object obj = onSucceeded.getSource().getValue();
                    JsonArray walletsArray = obj != null && obj instanceof JsonArray ? (JsonArray) obj : null;
                    if(walletsArray != null){
                        m_walletIds.set(walletsArray);
                    }else{
                        m_walletIds.set(null);
                    }
                }, onFailed->{
                    m_walletIds.set(null);
                });
    
               
               
            }
    
            public RemoveWalletBox() {
    
    
    
                Label backButton = new Label("🡄");
                backButton.setId("lblBtn");
    
                backButton.setOnMouseClicked(e -> {
                    m_currentBox.set(null);
                });
    
                Label headingText = new Label("Remove Wallet");
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
    
                VBox listBox = new VBox();
                listBox.setPadding(new Insets(10));
                listBox.setId("bodyBox");
    
                ScrollPane listScroll = new ScrollPane(listBox);
                listScroll.setPrefViewportHeight(120);
    
                HBox walletListBox = new HBox(listScroll);
                walletListBox.setPadding(new Insets(0,40,0, 40));
             
                HBox.setHgrow(walletListBox, Priority.ALWAYS);
    
               
    
              
                listScroll.prefViewportWidthProperty().bind(walletListBox.widthProperty().subtract(1));
    
                listScroll.viewportBoundsProperty().addListener((obs,oldval,newval)->{
                    listBox.setMinWidth(newval.getWidth());
                    listBox.setMinHeight(newval.getHeight());
                });
               
                HBox nextBox = new HBox(m_nextBtn);
                nextBox.setAlignment(Pos.CENTER);
                nextBox.setPadding(new Insets(20, 0, 0, 0));
    
    
                Label noticeText = new Label("Notice: ");
                noticeText.setId("smallPrimaryColor");
                noticeText.setMinWidth(58);
    
                TextArea noticeTxt = new TextArea("The associated (.erg) file will not be deleted.");
                noticeTxt.setId("smallSecondaryColor");
                noticeTxt.setWrapText(true);
                noticeTxt.setPrefHeight(40);
                noticeTxt.setEditable(false );
    
                HBox noticeBox = new HBox(noticeText, noticeTxt);
                HBox.setHgrow(noticeBox,Priority.ALWAYS);
                noticeBox.setAlignment(Pos.CENTER);
                noticeBox.setPadding(new Insets(10,20,0,20));
    
                VBox bodyBox = new VBox(gBox, walletListBox,noticeBox, nextBox);
                VBox.setMargin(bodyBox, new Insets(10, 10, 0, 10));
    
    
                VBox layoutVBox = new VBox(headerBox, bodyBox);
    
                JsonArray removeIds = new JsonArray();
    
                
    
                m_walletIds.addListener((obs,oldval, newval)->{
                    listBox.getChildren().clear();
    
                    if (newval != null) {
        
                        for (JsonElement element : newval) {
                            if (element != null && element.isJsonObject()) {
                                JsonObject json = element.getAsJsonObject();
    
                                String name = json.get("name").getAsString();
                             
                                Label nameText = new Label(name);
                                nameText.setFont(Stages.txtFont);
                                nameText.setPadding(new Insets(0,0,0,20));
    
                                Text checkBox = new Text(" ");
                                checkBox.setFill(Color.BLACK);
                                Runnable addItemToRemoveIds = ()->{
                                   
                                    if(!removeIds.contains(element)){
                                        removeIds.add(element);
                                    }
                                };
    
                                Runnable removeItemFromRemoveIds = () ->{
                                    removeIds.remove(element);
                                };
                                //toggleBox
                                //toggleBoxPressed
                                Runnable toggleCheck = ()->{
                                    if(checkBox.getText().equals(" ")){
                                        checkBox.setText("🗶");
                                        addItemToRemoveIds.run();    
                                    }else{
                                        checkBox.setText(" ");
                                        removeItemFromRemoveIds.run();
                                    }
                             
                                };
    
                                HBox checkBoxBox = new HBox(checkBox);
                                checkBoxBox.setId("xBtn");
                                
    
                                HBox walletItem = new HBox(checkBoxBox, nameText);
                                walletItem.setAlignment(Pos.CENTER_LEFT);
                                walletItem.setMinHeight(25);
                                HBox.setHgrow(walletItem, Priority.ALWAYS);
                                walletItem.setId("rowBtn");
                                walletItem.setPadding(new Insets(2,5,2,5));
                                
                                walletItem.addEventFilter(MouseEvent.MOUSE_CLICKED, e->toggleCheck.run());
    
                                listBox.getChildren().add(walletItem);
                            }
                        }
                    }
                });
              
                updateWalletList();
    
                m_nextBtn.setOnAction(e->{
                    if(removeIds.size() == 0){
                        showError("No wallets selected");
                    }else{
                        m_walletControl.removeWallets(removeIds, onSucceeded->{

                        }, onFailed->{
                            Throwable throwable = onFailed.getSource().getException();
                            String msg = throwable != null ? throwable.getMessage() : " unknown error"; 
                            showError("Error: " + msg);
                        });
                    }
                });
    
                getChildren().add(layoutVBox);
            }
        
        }
    
        private class SendAppBox extends AppBox {
    
            private ErgoWalletAmountSendBoxes m_amountSendBoxes;
            private Button m_sendBtn = new Button("Send");
            private Tooltip m_errTip = new Tooltip();
            private TextField m_feesField;
            private VBox m_sendBodyContentBox;
            private AddressBox m_sendToAddress;
            private VBox m_sendBodyBox;
            
            public SendAppBox(){
                super();
    
    
                Label backButton = new Label("🡄");
                backButton.setId("lblBtn");
    
                backButton.setOnMouseClicked(e -> {
                    m_currentBox.set(null);
                });
    
                Label headingText = new Label("Send assets");
                headingText.setFont(Stages.txtFont);
                headingText.setPadding(new Insets(0,0,0,15));
    
                HBox headingBox = new HBox(backButton, headingText);
                headingBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(headingBox, Priority.ALWAYS);
                headingBox.setPadding(new Insets(5, 15, 5, 15));
            
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
    
      
                Label addressText = new Label("Address ");
                addressText.setFont(Stages.txtFont);
                addressText.setMinWidth(90);
    
                HBox addressTextBox = new HBox(addressText);
                addressTextBox.setAlignment(Pos.CENTER_LEFT);
                addressTextBox.setPadding(new Insets(0,0,2,5));
    
                Region addressHBar = new Region();
                addressHBar.setPrefWidth(400);
                addressHBar.setPrefHeight(2);
                addressHBar.setMinHeight(2);
                addressHBar.setId("hGradient");
    
                HBox addressGBox = new HBox(addressHBar);
                addressGBox.setAlignment(Pos.CENTER);
                addressGBox.setPadding(new Insets(0,0,0,0));
    
                m_sendToAddress = new AddressBox(new AddressInformation(""), m_appStage.getScene(), NETWORK_TYPE);
                HBox.setHgrow(m_sendToAddress, Priority.ALWAYS);
                m_sendToAddress.getInvalidAddressList().add(m_walletControl.getCurrentAddress());
            
                HBox addressFieldBox = new HBox(m_sendToAddress);
                HBox.setHgrow(addressFieldBox, Priority.ALWAYS);
                
                VBox addressPaddingBox = new VBox(addressTextBox, addressGBox, addressFieldBox);
                addressPaddingBox.setPadding(new Insets(0,10,10,10));
        
                Label amountText = new Label("Amount ");
                amountText.setFont(Stages.txtFont);
                
                HBox amountTextBox = new HBox(amountText);
                amountTextBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(amountTextBox, Priority.ALWAYS);
                amountTextBox.setPadding(new Insets(10, 0,2,5));
    
                Region amountHBar = new Region();
                amountHBar.setPrefWidth(400);
                amountHBar.setPrefHeight(2);
                amountHBar.setMinHeight(2);
                amountHBar.setId("hGradient");
    
                HBox amountGBox = new HBox(amountHBar);
                amountGBox.setAlignment(Pos.CENTER);
                amountGBox.setPadding(new Insets(0,0,10,0));
    
                m_amountSendBoxes = new ErgoWalletAmountSendBoxes(m_appStage.getScene(), NetworkType.MAINNET, m_walletControl.balanceProperty());
                HBox.setHgrow(m_amountSendBoxes, Priority.ALWAYS);
       
                VBox walletListBox = new VBox( m_amountSendBoxes);
                walletListBox.setPadding(new Insets(0, 0, 10, 5));
                walletListBox.minHeight(80);
                HBox.setHgrow(walletListBox, Priority.ALWAYS);
    
                Label feesLabel = new Label("Fee");
                feesLabel.setFont(Stages.txtFont);
                feesLabel.setMinWidth(50);
    
                String feesFieldId = FriendlyId.createFriendlyId();
    
                m_feesField = new TextField(m_minNetworkFee + "");
                m_feesField.setPrefWidth(100);
                m_feesField.setUserData(feesFieldId);
                m_feesField.setOnKeyPressed(e->{
                    if (Utils.keyCombCtrZ.match(e) ) { 
                        e.consume();
                    }
                });
                m_feesField.textProperty().addListener((obs,oldval,newval)->{
                    String number = newval.replaceAll("[^0-9.]", "");
          
                    int index = number.indexOf(".");
                    String leftSide = index != -1 ? number.substring(0, index + 1) : number;
                    String rightSide = index != -1 ?  number.substring(index + 1) : "";
                    rightSide = rightSide.length() > 0 ? rightSide.replaceAll("[^0-9]", "") : "";
                    rightSide = rightSide.length() > ErgoCurrency.DECIMALS ? rightSide.substring(0, ErgoCurrency.DECIMALS) : rightSide;
                    m_feesField.setText(leftSide +  rightSide);
                });
                m_feesField.focusedProperty().addListener((obs,oldval,newval)->{
                    if(!newval){
                        checkFeeField();
                    }
                });
          
                
    
                MenuButton feeTypeBtn = new MenuButton();
                feeTypeBtn.setFont(Stages.txtFont);
    
                MenuItem ergFeeTypeItem = new MenuItem(String.format("%-20s", "Ergo")+ "(ERG)");
                ergFeeTypeItem.setOnAction(e->{
                    PriceCurrency currency = new ErgoCurrency(NETWORK_TYPE);
                    feeTypeBtn.setUserData(currency);
                    feeTypeBtn.setText(currency.getSymbol());
                });
    
                feeTypeBtn.getItems().add(ergFeeTypeItem);
    
                Runnable setFee = ()->{
                    String feesString = Utils.isTextZero(m_feesField.getText()) ? "0" : m_feesField.getText();
                    BigDecimal fee = new BigDecimal(feesString);
                    
                    Object feeTypeUserData = feeTypeBtn.getUserData();
                    if(feeTypeUserData != null && feeTypeUserData instanceof PriceCurrency){
                        PriceCurrency currency = (PriceCurrency) feeTypeUserData;
                        m_amountSendBoxes.feeAmountProperty().set(new PriceAmount(fee, currency));
                    }
                };
    
                feeTypeBtn.textProperty().addListener((obs,oldval,newval)->{
                    setFee.run();
                });
             
                SimpleBooleanProperty isFeesFocused = new SimpleBooleanProperty(false);
    
                m_appStage.getScene().focusOwnerProperty().addListener((obs, old, newPropertyValue) -> {
                    if (newPropertyValue != null && newPropertyValue instanceof TextField) {
                        TextField focusedField = (TextField) newPropertyValue;
                        Object userData = focusedField.getUserData();
                        if(userData != null && userData instanceof String){
                            String userDataString = (String) userData;
                            if(userDataString.equals(feesFieldId)){
                                isFeesFocused.set(true);
                            }else{
                                if(isFeesFocused.get()){
                                    isFeesFocused.set(false);
                                    setFee.run();
                                }
                            }
                        }else{
                            if(isFeesFocused.get()){
                                isFeesFocused.set(false);
                                setFee.run();
                            }
                        }
                    }else{
                        if(isFeesFocused.get()){
                            isFeesFocused.set(false);
                            setFee.run();
                        }
                    }
                });
    
                ergFeeTypeItem.fire();
                HBox feeEnterBtnBox = new HBox();
                feeEnterBtnBox.setAlignment(Pos.CENTER_LEFT);
    
                HBox feesFieldBox = new HBox(m_feesField, feeEnterBtnBox, feeTypeBtn);
                feesFieldBox.setId("bodyBox");
                feesFieldBox.setAlignment(Pos.CENTER_LEFT);
                feesFieldBox.setPadding(new Insets(2));
    
                HBox feesBox = new HBox(feesLabel, feesFieldBox);
                HBox.setHgrow(feesBox, Priority.ALWAYS);
                feesBox.setAlignment(Pos.CENTER_RIGHT);
                feesBox.setPadding(new Insets(0,10,20,0));
    
                Region sendHBar = new Region();
                sendHBar.setPrefWidth(400);
                sendHBar.setPrefHeight(2);
                sendHBar.setMinHeight(2);
                sendHBar.setId("hGradient");
    
                HBox sendGBox = new HBox(sendHBar);
                sendGBox.setAlignment(Pos.CENTER);
                sendGBox.setPadding(new Insets(10,0,0,0));
    
                VBox amountPaddingBox = new VBox(amountTextBox, amountGBox, walletListBox, feesBox, sendGBox);
                amountPaddingBox.setPadding(new Insets(0,10,0,10));
            
                HBox nextBox = new HBox(m_sendBtn);
                nextBox.setAlignment(Pos.CENTER);
                nextBox.setPadding(new Insets(20, 0, 5, 0));
    
                m_sendBodyBox = new VBox(addressPaddingBox, amountPaddingBox, nextBox);
    
                m_sendBodyContentBox = new VBox(m_sendBodyBox);
    
                VBox bodyBox = new VBox(gBox, m_sendBodyContentBox);
                VBox.setMargin(bodyBox, new Insets(10, 0, 10, 0));
    
                VBox layoutVBox = new VBox(headerBox, bodyBox);
    
                m_sendBtn.setOnAction(e->{
                
                    m_sendBodyContentBox.getChildren().clear();
    
                    Text sendText = new Text("Sending...");
                    sendText.setFont(Stages.txtFont);
                    sendText.setFill(Stages.txtColor);
    
                    HBox sendTextBox = new HBox(sendText);
                    HBox.setHgrow(sendTextBox, Priority.ALWAYS);
                    sendTextBox.setAlignment(Pos.CENTER);
    
                    ProgressBar progressBar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
                    progressBar.setPrefWidth(400);
                    
                    VBox statusBox = new VBox(sendTextBox, progressBar);
                    HBox.setHgrow(statusBox, Priority.ALWAYS);
                    statusBox.setAlignment(Pos.CENTER);
                    statusBox.setPrefHeight(200);
    
                    m_sendBodyContentBox.getChildren().add(statusBox);
    
                    JsonObject sendObject = new JsonObject();
                    
                    AddressInformation addressInformation = m_sendToAddress.addressInformationProperty().get();
                    if (addressInformation == null  || (addressInformation != null && addressInformation.getAddress() == null))  {
                        
                        addSendBox();
                        showError("Error: Enter valid recipient address");
                        return;
                    }
    
                    if (!m_sendToAddress.isAddressValid().get())  {
                        
                        addSendBox();
                        showError("Error: Address is an invalid recipient");
                        return;
                    }
                    if(m_walletControl.isUnlocked()){
                        addSendBox();
                        showError("Error: No wallet selected");
                        return;
                    }
                    NoteConstants.addWalletAddressToDataObject("senderAddress", m_walletControl.getCurrentAddress(), m_walletControl.getWalletName(), NoteConstants.CURRENT_WALLET_FILE, sendObject);
            
                    JsonObject recipientObject = new JsonObject();
                    recipientObject.addProperty("address", addressInformation.getAddress().toString());
                    recipientObject.addProperty("addressType", addressInformation.getAddressTypeString());
                    
                    sendObject.add("recipient", recipientObject);
    
                    BigDecimal minimumDecimal = m_amountSendBoxes.minimumFeeProperty().get();
                    PriceAmount feePriceAmount = m_amountSendBoxes.feeAmountProperty().get();
                    if(minimumDecimal == null || (feePriceAmount == null || (feePriceAmount != null && feePriceAmount.amountProperty().get().compareTo(minimumDecimal) == -1))){
                        addSendBox();
                        showError("Error: Minimum fee " +(minimumDecimal != null ? ("of " + minimumDecimal) : "unavailable") + " " +feeTypeBtn.getText() + " required");
                        return;
                    }
    
                    if(!NoteConstants.addFeeAmountToDataObject(feePriceAmount, m_minNanoErgs, sendObject)){
                        addSendBox();
                        showError("Babblefees not supported at this time");
                        return;
                    }
                        
                    ErgoWalletAmountSendBox ergoSendBox = (ErgoWalletAmountSendBox) m_amountSendBoxes.getAmountBox(ErgoCurrency.TOKEN_ID);
    
                    if(!ergoSendBox.isSufficientBalance()){
                        addSendBox();
                        showError("Insufficient Ergo balance");
                        return;
                    }
    
                    PriceAmount ergoPriceAmount = ergoSendBox.getSendAmount();
                    long nanoErgs = ergoPriceAmount.getLongAmount();
                    
                    NoteConstants.addNanoErgAmountToDataObject(ergoPriceAmount.getLongAmount(), sendObject);
    
    
                    JsonArray sendAssets = new JsonArray();
    
                    AmountBoxInterface[] amountBoxAray =  m_amountSendBoxes.getAmountBoxArray();
                    
                    for(int i = 0; i < amountBoxAray.length ;i++ ){
                        AmountBoxInterface amountBox =amountBoxAray[i];
                        if(amountBox instanceof ErgoWalletAmountSendTokenBox){
                            ErgoWalletAmountSendTokenBox sendBox = (ErgoWalletAmountSendTokenBox) amountBox;
                            PriceAmount sendAmount = sendBox.getSendAmount();
                            if(sendAmount!= null){
                                if(sendBox.isInsufficientBalance()){
                                    addSendBox();
                                    showError("Insufficient " + sendBox.getCurrency().getName() +" balance");
                                    return;
                                }
                                if(!sendAmount.getTokenId().equals(ErgoCurrency.TOKEN_ID)){
                                    sendAssets.add(sendAmount.getAmountObject());
                                }
                            }
                        }
                        
                    }
            
                    if(nanoErgs == 0 && sendAssets.size() == 0){
                        addSendBox();
                        showError("Enter assets to send");
                        return;
                    }
    
                    if(nanoErgs < 39600){
                        addSendBox();
                        showError("Transactions require a minimum of " + PriceAmount.calculateLongToBigDecimal(39600, ErgoCurrency.DECIMALS).toPlainString() + " ERG");
                        return;
                    }
    
                    sendObject.add("assets", sendAssets);
    
                    NoteConstants.addNetworkTypeToDataObject(NETWORK_TYPE, sendObject);
    
                    m_walletControl.sendNoteData("sendAssets", sendObject, (onComplete)->{
                        Object sourceObject = onComplete.getSource().getValue();
                        if(sourceObject != null && sourceObject instanceof JsonObject){
                            JsonObject receiptJson = (JsonObject) sourceObject;
                            JsonElement txIdElement = receiptJson.get("txId");
                            JsonElement resultElement = receiptJson.get("result");
                            
    
                            boolean isComplete = txIdElement != null && !txIdElement.isJsonNull();
                            String resultString = resultElement != null ? resultElement.getAsString() : isComplete ? "Complete" : "Failed";
                            receiptJson.addProperty("result", resultString);
    
                            String id = isComplete ? "tx_" + txIdElement.getAsString() : "err_" + FriendlyId.createFriendlyId();
                            
                            receiptJson.add("info", sendObject);
    
                            showReceipt(id, isComplete ? NoteConstants.SUCCESS : NoteConstants.ERROR, receiptJson);
                        }
                    }, (onError)->{
                        Throwable throwable = onError.getSource().getException();
                        String errorString = throwable.getMessage();
    
                        String errTxId = "inc_" + FriendlyId.createFriendlyId();
                        
                        JsonObject receiptJson = new JsonObject();
                        receiptJson.addProperty("result","Incomplete");
                        receiptJson.addProperty("description", errorString.equals("") ? throwable.toString() : errorString);
                        receiptJson.addProperty("timeStamp", System.currentTimeMillis());
                        receiptJson.add("info", sendObject);
                        showReceipt(errTxId, NoteConstants.ERROR, receiptJson);
    
                    });
                    
                });
    
                getChildren().add(layoutVBox);
            }
    
            private void addSendBox(){
                m_sendBodyContentBox.getChildren().clear();
                m_sendBodyContentBox.getChildren().add(m_sendBodyBox);
            }
    
            private void resetSend(){
                m_sendBodyContentBox.getChildren().clear();
                m_feesField.setText(m_minNetworkFee + "");
                m_sendToAddress.addressInformationProperty().set(new AddressInformation(""));
                m_amountSendBoxes.reset();
                m_sendBodyContentBox.getChildren().add(m_sendBodyBox);
            };
            
            private void showReceipt(String id, int code, JsonObject receiptJson){
            
                Label sendReceiptText = new Label("Send Receipt");
                sendReceiptText.setFont(Stages.txtFont);
                
                HBox sendReceiptTextBox = new HBox(sendReceiptText);
                sendReceiptTextBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(sendReceiptTextBox, Priority.ALWAYS);
                sendReceiptTextBox.setPadding(new Insets(10, 0,2,5));
    
    
                Region sendReceiptHBar = new Region();
                sendReceiptHBar.setPrefWidth(400);
                sendReceiptHBar.setPrefHeight(2);
                sendReceiptHBar.setMinHeight(2);
                sendReceiptHBar.setId("hGradient");
            
                HBox sendReceiptHBarGBox = new HBox(sendReceiptHBar);
                sendReceiptHBarGBox.setAlignment(Pos.CENTER);
                sendReceiptHBarGBox.setPadding(new Insets(0,0,10,0));
    
                JsonParametersBox sendReceiptJsonBox = new JsonParametersBox((JsonObject) null, Stages.COL_WIDTH);
                HBox.setHgrow(sendReceiptJsonBox, Priority.ALWAYS);
                sendReceiptJsonBox.setPadding(new Insets(2,10,0,10));
                sendReceiptJsonBox.update(receiptJson);
    
    
    
                Button exportBtn = new Button("🖫 Export… (*.json)");
                exportBtn.setOnAction(onSave->{
                    ExtensionFilter txtFilter = new FileChooser.ExtensionFilter("JSON (application/json)", "*.json");
                    FileChooser saveChooser = new FileChooser();
                    saveChooser.setTitle("🖫 Export JSON…");
                    saveChooser.getExtensionFilters().addAll(txtFilter);
                    saveChooser.setSelectedExtensionFilter(txtFilter);
                    saveChooser.setInitialFileName(id + ".json");
                    File saveFile = saveChooser.showSaveDialog(m_appStage);
                    if(saveFile != null){
                        Gson gson = new GsonBuilder().setPrettyPrinting().create();
                        
                        try {
                            Files.writeString(saveFile.toPath(), gson.toJson(receiptJson));
                        } catch (IOException e1) {
                            Alert alert = new Alert(AlertType.NONE, e1.toString(), ButtonType.OK);
                            alert.setTitle("Error");
                            alert.setHeaderText("Error");
                            alert.initOwner(m_appStage);
                            alert.show();
                        }
                    }
                });
    
                HBox exportBtnBox = new HBox(exportBtn);
                exportBtnBox.setAlignment(Pos.CENTER_RIGHT);
                exportBtnBox.setPadding(new Insets(15,15,15,0));
    
                Region receiptBottomHBar = new Region();
                receiptBottomHBar.setPrefWidth(400);
                receiptBottomHBar.setPrefHeight(2);
                receiptBottomHBar.setMinHeight(2);
                receiptBottomHBar.setId("hGradient");
    
                HBox receiptBottomGBox = new HBox(receiptBottomHBar);
                receiptBottomGBox.setAlignment(Pos.CENTER);
                receiptBottomGBox.setPadding(new Insets(10,0,0,0));
    
    
                Button receiptBottomOkBtn = new Button("Ok");
                receiptBottomOkBtn.setOnAction(onOk->{
                    if(code == NoteConstants.SUCCESS){
                        resetSend();
                        m_currentBox.set(null);
                    }else{
                        addSendBox();
                    }
                });
    
    
                HBox receiptBottomOkBox = new HBox(receiptBottomOkBtn);
                receiptBottomOkBox.setAlignment(Pos.CENTER);
                receiptBottomOkBox.setPadding(new Insets(20, 0, 0, 0));
    
    
                VBox receiptContentBox = new VBox(sendReceiptTextBox,sendReceiptHBarGBox, sendReceiptJsonBox, exportBtnBox, receiptBottomGBox, receiptBottomOkBox);
    
                m_sendBodyContentBox.getChildren().clear();
                m_sendBodyContentBox.getChildren().add(receiptContentBox);
                    
                
            }
            public void checkFeeField(){
        
                String feeString = m_feesField.getText();
                if(Utils.isTextZero(feeString)){
                    m_feesField.setText(m_minNetworkFee + "");
                }else{
                    BigDecimal fee = new BigDecimal(feeString);
                    if(fee.compareTo(m_minNetworkFee) == -1){
                        m_feesField.setText(m_minNetworkFee + "");
                    }
                }
            
            }
    
            public void showError(String msg){
                
                double stringWidth =  Utils.computeTextWidth(Stages.txtFont, msg);;
    
                Point2D p = m_sendBtn.localToScene(0.0, 0.0);
                m_errTip.setText(msg);
                m_errTip.show(m_sendBtn,
                        p.getX() + m_sendBtn.getScene().getX()
                                + m_sendBtn.getScene().getWindow().getX() - (stringWidth/2),
                        (p.getY() + m_sendBtn.getScene().getY()
                                + m_sendBtn.getScene().getWindow().getY()) - 40);
                PauseTransition pt = new PauseTransition(Duration.millis(5000));
                pt.setOnFinished(ptE -> {
                    m_errTip.hide();
                });
                pt.play();
            }
    
        }
    
        
        private class WalletTxBox extends AppBox{
    
            private ErgoTxViewsBox m_txViewBox = null;
            private ChangeListener<JsonObject> m_txBalanceChange = null;
            public void getTransactionViews(){
                m_walletControl.getTransactionViews( onSucceeded->{
                    Object obj = onSucceeded.getSource().getValue();
                    if(m_txViewBox != null && obj != null && obj instanceof JsonObject){
                        m_txViewBox.update((JsonObject) obj);
                    }else{
                        m_txViewBox.update(NoteConstants.getJsonObject("info", "No transactions available"));
                    }
                }, onFailed->{
                    Throwable throwable = onFailed.getSource().getException();
                    String msg = throwable != null ? throwable.getMessage() : "Unable to get transactions";
                    m_txViewBox.update(NoteConstants.getJsonObject("error", msg));
                });
            }
            
            public WalletTxBox(){
                super();
            
    
                Label backButton = new Label("🡄");
                backButton.setId("lblBtn");
    
                backButton.setOnMouseClicked(e -> {
                    m_currentBox.set(null);
                });
    
                Label headingText = new Label("Transactions");
                headingText.setFont(Stages.txtFont);
                headingText.setPadding(new Insets(0,0,0,15));
    
                HBox headingBox = new HBox(backButton, headingText);
                headingBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(headingBox, Priority.ALWAYS);
                headingBox.setPadding(new Insets(5, 15, 5, 15));
            
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
                
    
                m_txViewBox = new ErgoTxViewsBox(m_walletControl.getCurrentAddress(), Stages.COL_WIDTH, m_appStage, m_walletControl);
                m_txViewBox.setPadding(new Insets(10,0,0,0));
                m_txViewBox.update(NoteConstants.getJsonObject("status", "Getting transactions..."));
    
                VBox bodyBox = new VBox(gBox,m_txViewBox);
                VBox.setMargin(bodyBox, new Insets(0, 10, 0, 10));
    
    
                VBox layoutVBox = new VBox(headerBox, bodyBox);
    
                getChildren().add(layoutVBox);
    
                getTransactionViews();
                m_txBalanceChange = (obs,oldVal,newVal)->getTransactionViews();
                m_walletControl.balanceProperty().addListener(m_txBalanceChange);
            }
    
            @Override
            public void shutdown(){
                m_walletControl.balanceProperty().removeListener(m_txBalanceChange);
            }
        }
        
        private class WalletSwapBox extends AppBox{
    
            public static final BigDecimal MIN_SWAP_FEE = m_minNetworkFee.multiply(BigDecimal.valueOf(3));
    
            private final static int MATH_SCALE = 31;
    
            private final SimpleBooleanProperty m_showQuoteInfoProperty = new SimpleBooleanProperty(false);
            private final SimpleBooleanProperty m_showTokenInfoProperty = new SimpleBooleanProperty(false);
            private final SimpleBooleanProperty m_isBuyToken = new SimpleBooleanProperty(true);
            private final SimpleObjectProperty<PriceQuote> m_tokenQuoteInErg = new SimpleObjectProperty<>(null);
     
            private final SimpleObjectProperty<BigDecimal> m_swapFeeProperty = new SimpleObjectProperty<>(MIN_SWAP_FEE);
            private final SimpleObjectProperty<BigDecimal> m_networkFeeProperty = new SimpleObjectProperty<>(m_minNetworkFee);
            private final SimpleObjectProperty<BigDecimal> m_ergoBalanceProperty = new SimpleObjectProperty<>(null);
            private final SimpleObjectProperty<BigDecimal> m_tokenBalanceProperty = new SimpleObjectProperty<>(null);
            
            private BigDecimal m_ergoAmount = BigDecimal.ZERO;
            private BigDecimal m_tokenAmount = BigDecimal.ZERO;
            private BigDecimal m_orderPrice = BigDecimal.ZERO;
    
            private String m_searchFilter = "";
            private HBox m_tokenSearchBox = null;
            private HBox m_tokenSearchBoxHolder = null;
    
            private BigDecimal m_ergPerToken = null;
            private BigDecimal m_feePerToken = null;
            private BigInteger m_feePerTokenNum = BigInteger.ZERO;
            private BigInteger m_feePerTokenDenom = BigInteger.ZERO;
            private BigInteger m_ergPerTokenNum = BigInteger.ZERO;
            private BigInteger m_ergPerTokenDenom = BigInteger.ZERO;
    
            private final JsonParametersBox m_txDetailParamsBox;
            private final SimpleObjectProperty<JsonObject> m_txDetailsJsonProperty = new SimpleObjectProperty<>(null);
    
            private SimpleObjectProperty<ErgoTokenInfo> m_tokenInfoProperty = new SimpleObjectProperty<>(null);
            private SimpleStringProperty m_tokenIdProperty = new SimpleStringProperty();
    
            private VBox m_swapBodyBox = null;
            private VBox m_ergoSwapVBox = null;
            private HBox m_orderPriceBox = null;
            private VBox m_tokenSwapVBox = null;
            private HBox m_swapBuySell = null;
            private Button m_executeSwapBtn = null;
            private SimpleDoubleProperty m_colWidth = new SimpleDoubleProperty(Stages.COL_WIDTH);
            private JsonParametersBox m_tokenQuoteParmsBox = null;
            private TextField m_orderPriceField = null;
            private TextField m_swapfeeField = null;
            private TextField m_networkFeeField = null;
            private HBox m_swapTopSpacer = null;
            private HBox m_swapBotSpacer = null;
            private VBox m_tokenInfoVBox = null;
            private JsonParametersBox m_tokenInfoParamsBox = null;
            private ChangeListener<Boolean> m_tokenMarketAvailableListener = null;
            private ChangeListener<String> m_ergoAmountTextChanged = null;
    
            private Button m_buyBtn = null;
            private Button m_sellBtn = null;
    
            private Button m_ergoMaxBtn = null;
            private HBox m_ergoBalanceFieldBox = null;
    
            private Label m_tokenAmountLbl = null;
            private HBox m_tokenAmountFieldBox = null;
            private HBox m_tokenAmountRow = null;
            private Label m_tokenIdLbl = null;
            private HBox m_tokenIdRow = null;
            private HBox m_tokenIdFieldbox = null;
            private TextField m_tokenIdField = null;
            private TextField m_tokenAmountField = null;
            private PriceQuoteScroll m_priceQuoteScroll = null;
            private VBox m_quoteInfoBox = null;
    
            private Button m_tokenMaxBtn = null;
            private HBox m_tokenBalanceFieldBox = null;
    
    
            private TextField m_ergoAmountField;
    
        
            private VBox m_tokenMenuRow;
            private HBox m_tokenMenuHBox;
            private EventHandler<ActionEvent> m_updateQuotesEvent = null;
            private ChangeListener<Number> m_tokenMarketQuotesUpdateListener = null;
            private ChangeListener<Boolean> m_tokenAmountFocusListener = null;
            private ChangeListener<String> m_tokenAmountTextListener = null;
            
            private Button m_tokenAmountEnterBtn = null;
    
            private final String defaultTokenBtnString = "[Select Token]";

                
            public WalletSwapBox(){
                super();
    
                
    
                Label backButton = new Label("🡄");
                backButton.setId("lblBtn");
    
                backButton.setOnMouseClicked(e -> {
                    m_currentBox.set(null);
                });
    
                Label headingText = new Label("Swap");
                headingText.setFont(Stages.txtFont);
                headingText.setPadding(new Insets(0,0,0,15));
    
                HBox headingBox = new HBox(backButton, headingText);
                headingBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(headingBox, Priority.ALWAYS);
                headingBox.setPadding(new Insets(5, 15, 5, 15));
            
                VBox headerBox = new VBox(headingBox);
                headerBox.setPadding(new Insets(0, 5, 0, 0));
    
                Region hBar = new Region();
                hBar.setPrefWidth(400);
                hBar.setPrefHeight(2);
                hBar.setMinHeight(2);
                hBar.setId("hGradient");
    
                HBox hBox = new HBox(hBar);
                hBox.setAlignment(Pos.CENTER);
                hBox.setPadding(new Insets(0, 0, 20, 0));
    
                m_tokenMenuHBox = new HBox();
                HBox.setHgrow(m_tokenMenuHBox, Priority.ALWAYS);
                m_tokenMenuHBox.setAlignment(Pos.CENTER_LEFT);
                m_tokenMenuHBox.setPadding(new Insets(2,10,2,8 ));
               
    
                Button showTokenInfoBtn = new Button(m_showTokenInfoProperty.get() ? "⏷" : "⏵");
                showTokenInfoBtn.setId("caretBtn");
                showTokenInfoBtn.setMinWidth(25);
                showTokenInfoBtn.setOnAction(e->{
                    if(!m_showTokenInfoProperty.get()){
                        m_showTokenInfoProperty.set(true);
                    }else{
                        m_showTokenInfoProperty.set(false);
                    }
                });
    
                m_tokenIdLbl = new Label("Token Id");
                HBox.setHgrow(m_tokenIdLbl,Priority.ALWAYS);
                m_tokenIdLbl.minWidthProperty().bind(m_colWidth);
                m_tokenIdLbl.maxWidthProperty().bind(m_colWidth);
                m_tokenIdLbl.setId("logoBox");
    
                m_tokenIdEnterBtn = new Button("↵");
                m_tokenIdEnterBtn.setFocusTraversable(true);
                m_tokenIdEnterBtn.setPadding(Insets.EMPTY);
                m_tokenIdEnterBtn.setMinWidth(25);
    
                m_tokenIdField = new TextField(m_tokenIdProperty.get() != null ? m_tokenIdProperty.get() : "");
                HBox.setHgrow(m_tokenIdField, Priority.ALWAYS);
                m_tokenIdField.setPromptText("Enter Id -or- Select");
                m_tokenIdField.setId("textAreaInputEmpty");
                m_tokenIdListener = (obs,oldval,newval)->{
                    if(m_tokenIdField != null){
                        if(newval.length() > 0){
                            m_tokenIdField.setId("textAreaInput");
                        }else{
                            m_tokenIdField.setId("textAreaInputEmpty");
                        }
                        m_tokenIdField.setText(newval == null ? "" : newval);
                    }
                };
                m_tokenIdProperty.addListener(m_tokenIdListener);
    
                m_tokenIdFieldbox = new HBox(m_tokenIdField);
                HBox.setHgrow(m_tokenIdFieldbox, Priority.ALWAYS);
                m_tokenIdFieldbox.setAlignment(Pos.CENTER_RIGHT);
                m_tokenIdFieldbox.setId("bodyBox");
    
                m_tokenIdField.focusedProperty().addListener((obs,oldval,newval)->{
                    if(m_tokenIdField != null && m_tokenIdFieldbox != null && m_tokenIdEnterBtn != null){
                        String str = m_tokenIdField.getText();
                        if(!newval){
                            boolean isValid =  str.length() > 15 ? Utils.checkErgoId(str) : false;
                            setTokenId(isValid ? str : null);
                            if(isValid){
                                getTokenQuoteInErg(str);
                            }
                            m_tokenIdField.setText(isValid ? m_tokenIdProperty.get() : "");
                            if(m_tokenIdFieldbox.getChildren().contains(m_tokenIdEnterBtn)){
                                m_tokenIdFieldbox.getChildren().remove(m_tokenIdEnterBtn);
                            }
                            
                            if(m_tokenIdListener != null){
                                m_tokenIdProperty.addListener(m_tokenIdListener);
                            }
                        }else{
    
                            if(!m_tokenIdFieldbox.getChildren().contains(m_tokenIdEnterBtn)){
                                m_tokenIdFieldbox.getChildren().add(m_tokenIdEnterBtn);
                            }
                            if(m_tokenIdListener != null){
                                m_tokenIdProperty.removeListener(m_tokenIdListener);
                            }
                        }
                    }
                });
    
                m_tokenIdField.setOnAction(e->{
                    Platform.runLater(()->{
                        if(m_tokenIdEnterBtn != null){
                            m_tokenIdEnterBtn.requestFocus();
                        }
                    });
                });
    
                m_tokenIdRow = new HBox(showTokenInfoBtn, m_tokenIdLbl, m_tokenIdFieldbox);
                HBox.setHgrow(m_tokenIdRow, Priority.ALWAYS);
          
                m_tokenIdRow.setPadding(new Insets(20, 10,2,8));
    
                m_tokenInfoVBox = new VBox(m_tokenIdRow);
    
                m_showTokenInfoProperty.addListener((obs,oldval,newval)->{
                    showTokenInfoBtn.setText(newval ? "⏷" : "⏵");
                
                    if(newval){
                        addTokenInfoParamsBox();
                    }else{
                        removeTokenInfoParamsBox();
                    }
                });
    
                Label priceQuoteLbl = new Label("Quote");
                HBox.setHgrow(priceQuoteLbl,Priority.ALWAYS);
                priceQuoteLbl.minWidthProperty().bind(m_colWidth);
                priceQuoteLbl.maxWidthProperty().bind(m_colWidth);
                priceQuoteLbl.setId("logoBox");
    
                TextField priceQuoteField = new TextField("⎯");
                HBox.setHgrow(priceQuoteField, Priority.ALWAYS);
                priceQuoteField.setPadding(new Insets(0,10, 0, 0));
                priceQuoteField.setEditable(false);
                priceQuoteField.setOnMouseClicked(e->{
                    PriceQuote quote = m_tokenQuoteInErg.get();         
                    if(quote != null && quote.getTimeStamp() > 0){
                        BigDecimal orderPrice = new BigDecimal(quote.getAmountString());
                        setOrderPrice(orderPrice);
                        updateOrder();
                    }
                });
    
                HBox priceQuoteFieldBox = new HBox(priceQuoteField);
                HBox.setHgrow(priceQuoteFieldBox, Priority.ALWAYS);
                priceQuoteFieldBox.setAlignment(Pos.CENTER_LEFT);
                priceQuoteFieldBox.setId("bodyBox");
    
                Button showQuoteInfoBtn = new Button(m_showQuoteInfoProperty.get() ? "⏷" : "⏵");
                showQuoteInfoBtn.setId("caretBtn");
                showQuoteInfoBtn.setMinWidth(25);
                showQuoteInfoBtn.setOnAction(e->{
                    if(m_tokenQuoteInErg.get() != null && !m_showQuoteInfoProperty.get()){
                        m_showQuoteInfoProperty.set(true);
                    }else{
                        m_showQuoteInfoProperty.set(false);
                    }
                });
    
    
                HBox quoteInfoRow = new HBox(showQuoteInfoBtn, priceQuoteLbl, priceQuoteFieldBox);
                quoteInfoRow.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(quoteInfoRow, Priority.ALWAYS);
                quoteInfoRow.setPadding(new Insets(0, 0,2,0));
    
                m_quoteInfoBox = new VBox(quoteInfoRow);
                HBox.setHgrow(m_quoteInfoBox, Priority.ALWAYS);
    
                Label ergoAmountLbl = new Label("Ergo Amount");
                HBox.setHgrow(ergoAmountLbl,Priority.ALWAYS);
                ergoAmountLbl.minWidthProperty().bind(m_colWidth);
                ergoAmountLbl.maxWidthProperty().bind(m_colWidth);
                ergoAmountLbl.setId("logoBox");
    
                Button ergoAmountEnterBtn = new Button("↵");
                ergoAmountEnterBtn.setFocusTraversable(true);
                ergoAmountEnterBtn.setPadding(Insets.EMPTY);
                ergoAmountEnterBtn.setMinWidth(25);
    
    
                m_ergoAmountField = new TextField("0");
                HBox.setHgrow(m_ergoAmountField, Priority.ALWAYS);
                m_ergoAmountField.setPadding(new Insets(0,10, 0, 0));
               
    
    
                            
                HBox ergoAmountFieldBox = new HBox(m_ergoAmountField);
                HBox.setHgrow(ergoAmountFieldBox, Priority.ALWAYS);
                ergoAmountFieldBox.setAlignment(Pos.CENTER_LEFT);
                ergoAmountFieldBox.setId("bodyBox");
    
                m_ergoAmountField.setOnKeyPressed(e->{
                    if (Utils.keyCombCtrZ.match(e) ) { 
                        e.consume();
                    }
                });
                
                m_ergoAmountTextChanged = (obs,oldVal,newval)->{
                    if(m_isBuyToken.get() && newval.length() > 0){
                        int decimals = ErgoCurrency.DECIMALS;
                        String number = newval.replaceAll("[^0-9.]", "");
                        int index = number.indexOf(".");
                        String leftSide = index != -1 ? number.substring(0, index + 1) : number;
                        String rightSide = index != -1 ?  number.substring(index + 1) : "";
                        rightSide = rightSide.length() > 0 ? rightSide.replaceAll("[^0-9]", "") : "";
                        rightSide = rightSide.length() > decimals ? rightSide.substring(0, decimals) : rightSide;
                        number = (leftSide + rightSide);
    
                        m_ergoAmountField.setText(number);
                        ErgoTokenInfo tokenInfo = getTokenInfo();       
    
                        BigDecimal ergoAmount = Utils.isTextZero(number) ? BigDecimal.ZERO : new BigDecimal(number.indexOf(".") != number.length() - 1 ? number : number.substring(0, number.length() -1));
                        updateTokensFromErgoAmount(ergoAmount, getOrderPrice(), tokenInfo);
                    }
                }; 
                
                m_ergoAmountField.textProperty().addListener(m_ergoAmountTextChanged);
    
                
    
                m_ergoAmountField.focusedProperty().addListener((obs,oldval,newval)->{
                    if(m_isBuyToken.get()){
                        String str = m_ergoAmountField.getText();
                        boolean isZero = Utils.isTextZero(str);
    
                        if(!newval){
                            BigDecimal ergoAmount = isZero ? BigDecimal.ZERO : new BigDecimal(Utils.formatStringToNumber(str, ErgoCurrency.DECIMALS));
                            setErgoAmount(ergoAmount);
                            updateOrder();
                            if(ergoAmountFieldBox.getChildren().contains(ergoAmountEnterBtn)){
                                ergoAmountFieldBox.getChildren().remove(ergoAmountEnterBtn);
                            }
                        }else{
                            if(isZero){
                                m_ergoAmountField.setText("");
                            }
                            if(!ergoAmountFieldBox.getChildren().contains(ergoAmountEnterBtn)){
                                ergoAmountFieldBox.getChildren().add(ergoAmountEnterBtn);
                            }
                        }
                    }
                });
    
                m_ergoAmountField.setOnAction(e->{
                    Platform.runLater(()->ergoAmountEnterBtn.requestFocus());
                });
    
    
    
                HBox ergoAmountRow = new HBox(ergoAmountLbl, ergoAmountFieldBox);
                HBox.setHgrow(ergoAmountRow, Priority.ALWAYS);
                ergoAmountRow.setPadding(new Insets(2, 0,2,25));
    
                m_ergoSwapVBox = new VBox(ergoAmountRow);
                m_ergoSwapVBox.setPadding(new Insets(5,0,5,0));
    
    
                Label orderPriceLbl = new Label("Order Price");
                HBox.setHgrow(orderPriceLbl,Priority.ALWAYS);
                orderPriceLbl.minWidthProperty().bind(m_colWidth);
                orderPriceLbl.maxWidthProperty().bind(m_colWidth);
                orderPriceLbl.setId("logoBox");
    
                m_orderPriceField = new TextField("0");
                HBox.setHgrow(m_orderPriceField, Priority.ALWAYS);
                m_orderPriceField.setPadding(new Insets(0,10, 0, 0));
                m_orderPriceField.setOnKeyPressed(e->{
                    if (Utils.keyCombCtrZ.match(e) ) { 
                        e.consume();
                    }
                });
                ChangeListener<String> orderPriceTextListener = (obs,oldVal,newval)->{
                    if(newval.length() > 0){
                        String number = newval.replaceAll("[^0-9.]", "");
                        
                        int index = number.indexOf(".");
                        String leftSide = index != -1 ? number.substring(0, index + 1) : number;
                        String rightSide = index != -1 ?  number.substring(index + 1) : "";
                        rightSide = rightSide.length() > 0 ? rightSide.replaceAll("[^0-9]", "") : "";
                        number = (leftSide + rightSide);
                        m_orderPriceField.setText(number);
                        BigDecimal price = Utils.isTextZero(number) ? BigDecimal.ZERO : new BigDecimal(number.indexOf(".") != number.length() - 1 ? number : number.substring(0, number.length() -1));
                        
                        setOrderPrice(price);
                        ErgoTokenInfo tokenInfo = getTokenInfo();
              
                        if(m_isBuyToken.get()){
                            updateTokensFromErgoAmount(m_ergoAmount, price, tokenInfo);
                        }else{
                            updateErgoFromTokenAmount(m_tokenAmount, price, tokenInfo);
                        }
                    
                        
                    }
                };
    
                m_orderPriceField.textProperty().addListener(orderPriceTextListener);
    
    
                Button orderPriceEnterBtn = new Button("↵");
                orderPriceEnterBtn.setFocusTraversable(true);
                orderPriceEnterBtn.setPadding(Insets.EMPTY);
                orderPriceEnterBtn.setMinWidth(25);
    
    
                HBox orderPriceFieldBox = new HBox(m_orderPriceField);
                HBox.setHgrow(orderPriceFieldBox, Priority.ALWAYS);
                orderPriceFieldBox.setAlignment(Pos.CENTER_LEFT);
                orderPriceFieldBox.setId("bodyBox");
    
                m_orderPriceField.focusedProperty().addListener((obs,oldval,newval)->{
                    if(m_orderPriceField != null){
                        String str = m_orderPriceField.getText();
                        boolean isZero = Utils.isTextZero(str);
                        
    
                        if(!newval){
                            BigDecimal orderPrice = isZero ? BigDecimal.ZERO : new BigDecimal(Utils.formatStringToNumber(str));
                            m_orderPriceField.textProperty().removeListener(orderPriceTextListener);
                            m_orderPriceField.setText(orderPrice.toPlainString());
                            setOrderPrice(orderPrice);
                            m_orderPriceField.textProperty().addListener(orderPriceTextListener);
                            updateOrder();
                            if(orderPriceFieldBox.getChildren().contains(orderPriceEnterBtn)){
                                orderPriceFieldBox.getChildren().remove(orderPriceEnterBtn);
                            }
                        }else{
                            if(isZero){
                                m_orderPriceField.setText("");
                            }
                            if(!orderPriceFieldBox.getChildren().contains(orderPriceEnterBtn)){
                                orderPriceFieldBox.getChildren().add(orderPriceEnterBtn);
                            }
                        }
                    }
                });
    
                m_orderPriceBox = new HBox(orderPriceLbl, orderPriceFieldBox);
                HBox.setHgrow(m_orderPriceBox, Priority.ALWAYS);
                m_orderPriceBox.setPadding(new Insets(2, 0,2,25));
                m_orderPriceBox.setAlignment(Pos.CENTER_LEFT);
    
                Region buyLineSpacer = new Region();
                buyLineSpacer.setMaxHeight(2);
                buyLineSpacer.setMinHeight(2);
                buyLineSpacer.setPrefWidth(40);
    
                m_sellBtn = new Button("Sell");
                m_sellBtn.setId( m_isBuyToken.get() ? "iconBtn" : "iconBtnSelected");
                m_sellBtn.setOnAction(e->{
                    if(m_isBuyToken.get()){
                        m_isBuyToken.set(false);
                    }
                });
               
                m_buyBtn = new Button("Buy");
                m_buyBtn.setId( m_isBuyToken.get() ? "iconBtnSelected" : "iconBtn" );
                m_buyBtn.setOnAction(e->{
                    if(!m_isBuyToken.get()){
                        m_isBuyToken.set(true);
                    }
                });
         
                HBox rightBox = new HBox(m_sellBtn, buyLineSpacer, m_buyBtn);
                HBox.setHgrow(rightBox, Priority.ALWAYS);
                rightBox.setAlignment(Pos.CENTER);
               
                m_swapBuySell = new HBox(rightBox);
                HBox.setHgrow(m_swapBuySell, Priority.ALWAYS);
                m_swapBuySell.setAlignment(Pos.CENTER);
                m_swapBuySell.setPadding(new Insets(5,0,5, m_colWidth.get()));
    
                m_tokenSwapVBox = new VBox();
                m_tokenSwapVBox.setPadding(new Insets(5,0,5,0));
               
                Region swapTopSpacerRegion = new Region();
                swapTopSpacerRegion.setId("vGradient");
                swapTopSpacerRegion.setMaxHeight(2);
                swapTopSpacerRegion.setMinHeight(2);
                swapTopSpacerRegion.setPrefWidth(40);
    
                m_swapTopSpacer = new HBox(swapTopSpacerRegion);
                m_swapTopSpacer.setPadding(new Insets(10,0,10, m_colWidth.get()+20));
                m_swapTopSpacer.setAlignment(Pos.CENTER);
                HBox.setHgrow(m_swapTopSpacer, Priority.ALWAYS);
    
                Region swapBotSpacerRegion = new Region();
                swapBotSpacerRegion.setId("vGradient");
                swapBotSpacerRegion.setMaxHeight(2);
                swapBotSpacerRegion.setMinHeight(2);
                swapBotSpacerRegion.setPrefWidth(40);
    
                m_swapBotSpacer = new HBox(swapBotSpacerRegion);
                m_swapBotSpacer.setPadding(new Insets(10,0,10, m_colWidth.get()+20));
                m_swapBotSpacer.setAlignment(Pos.CENTER);
                HBox.setHgrow(m_swapBotSpacer, Priority.ALWAYS);
    
    
                m_swapBodyBox = new VBox();
                HBox.setHgrow(m_swapBodyBox, Priority.ALWAYS);
                m_swapBodyBox.setPadding(new Insets(5,10,5,10));
    
    
    
                Label swapfeeLbl = new Label("Swap Fee");
                HBox.setHgrow(swapfeeLbl,Priority.ALWAYS);
                swapfeeLbl.minWidthProperty().bind(m_colWidth);
                swapfeeLbl.maxWidthProperty().bind(m_colWidth);
                swapfeeLbl.setId("logoBox");
    
                m_swapfeeField = new TextField(m_swapFeeProperty.get().toPlainString());
                HBox.setHgrow(m_swapfeeField, Priority.ALWAYS);
                m_swapfeeField.setPadding(new Insets(0,10, 0, 0));
                m_swapfeeField.setOnKeyPressed(e->{
                    if (Utils.keyCombCtrZ.match(e) ) { 
                        e.consume();
                    }
                });
                m_swapfeeField.textProperty().addListener((obs,oldVal,newval)->{
                    if(newval.length() > 0){
                        String number = newval.replaceAll("[^0-9.]", "");
                        int index = number.indexOf(".");
                        String leftSide = index != -1 ? number.substring(0, index + 1) : number;
                        String rightSide = index != -1 ?  number.substring(index + 1) : "";
                        rightSide = rightSide.length() > 0 ? rightSide.replaceAll("[^0-9]", "") : "";
                        number = (leftSide + rightSide);
                        m_swapfeeField.setText(number);
                        setSwapFee( new BigDecimal(number.indexOf(".") != number.length() - 1 ? number : number.substring(0, number.length() -1)));
                       
                    }
                });
    
                Button swapFeeEnterBtn = new Button("↵");
                swapFeeEnterBtn.setFocusTraversable(true);
                swapFeeEnterBtn.setPadding(Insets.EMPTY);
                swapFeeEnterBtn.setMinWidth(25);
    
                HBox swapFeeFieldBox = new HBox(m_swapfeeField);
                HBox.setHgrow(swapFeeFieldBox, Priority.ALWAYS);
                swapFeeFieldBox.setAlignment(Pos.CENTER_LEFT);
                swapFeeFieldBox.setId("bodyBox");
                ChangeListener<BigDecimal> swapFeeListener = (obs,oldval,newval)->{
                    if(newval != null){
                        m_swapfeeField.setText(newval.toPlainString());
                    }else{
                        BigDecimal defaultFee = MIN_SWAP_FEE;
                        m_swapfeeField.setText(defaultFee.toPlainString());
                    }
                };
                m_swapFeeProperty.addListener(swapFeeListener);
    
    
                m_swapfeeField.focusedProperty().addListener((obs,oldval,newval)->{
                    if(m_swapfeeField != null){
                        
                        if(!newval){
                            String str = m_swapfeeField.getText();
                            BigDecimal defaultFee = MIN_SWAP_FEE;
                            if(Utils.isTextZero(str)){
                                m_swapfeeField.setText(defaultFee.toPlainString());
                            }else{
                                
                                BigDecimal newFee = new BigDecimal(Utils.formatStringToNumber(str, ErgoCurrency.DECIMALS));
                                if(newFee.compareTo(defaultFee) == -1){
                                    setSwapFee(defaultFee);
                                }
                            }
                            if(swapFeeFieldBox.getChildren().contains(swapFeeEnterBtn)){
                                swapFeeFieldBox.getChildren().remove(swapFeeEnterBtn);
                            }
             
                        }else{
    
                            if(!swapFeeFieldBox.getChildren().contains(swapFeeEnterBtn)){
                                swapFeeFieldBox.getChildren().add(swapFeeEnterBtn);
                            }
                        }
                    }
                });
    
                m_swapfeeField.setOnAction(e->{
                    if(swapFeeEnterBtn != null){
                        Platform.runLater(()-> swapFeeEnterBtn.requestFocus());
                    }
                });
                
    
    
                HBox swapFeeRow = new HBox(swapfeeLbl, swapFeeFieldBox);
                HBox.setHgrow(swapFeeRow, Priority.ALWAYS);
                swapFeeRow.setPadding(new Insets(2, 0,2,25));
                
                Label networkFeeLbl = new Label("Network Fee");
                HBox.setHgrow(networkFeeLbl,Priority.ALWAYS);
                networkFeeLbl.minWidthProperty().bind(m_colWidth);
                networkFeeLbl.maxWidthProperty().bind(m_colWidth);
                networkFeeLbl.setId("logoBox");
    
                Button networkFeeEnterBtn = new Button("↵");
                networkFeeEnterBtn.setFocusTraversable(true);
                networkFeeEnterBtn.setPadding(Insets.EMPTY);
                networkFeeEnterBtn.setMinWidth(25);
    
                m_networkFeeField = new TextField(m_networkFeeProperty.get().toPlainString());
                HBox.setHgrow(m_networkFeeField, Priority.ALWAYS);
                m_networkFeeField.setPadding(new Insets(0,10, 0, 0));
                m_networkFeeField.setOnKeyPressed(e->{
                    if (Utils.keyCombCtrZ.match(e) ) { 
                        e.consume();
                    }
                });
                m_networkFeeField.textProperty().addListener((obs,oldVal,newval)->{
                    if(newval.length() > 0){
                        String number = newval.replaceAll("[^0-9.]", "");
                        int index = number.indexOf(".");
                        String leftSide = index != -1 ? number.substring(0, index + 1) : number;
                        String rightSide = index != -1 ?  number.substring(index + 1) : "";
                        rightSide = rightSide.length() > 0 ? rightSide.replaceAll("[^0-9]", "") : "";
                        number = (leftSide + rightSide);
    
                        m_networkFeeField.setText(number);
      
                        setNetworkFee(new BigDecimal( number.indexOf(".") != number.length() - 1 ? number : number.substring(0, number.length() -1)));
                        
                    }
                });
    
                ChangeListener<BigDecimal> networkFeeListener = (obs,oldval,newval)->{
                    if(newval != null){
                        m_networkFeeField.setText(newval.toPlainString());
                    }else{
                        m_networkFeeField.setText("0");
                    }
                };
                m_networkFeeProperty.addListener(networkFeeListener);
    
                m_networkFeeField.setOnAction(e->{
                    if(networkFeeEnterBtn != null){
                        Platform.runLater(()-> networkFeeEnterBtn.requestFocus());
                    }
                });
    
                HBox networkFeeFieldBox = new HBox(m_networkFeeField);
                HBox.setHgrow(networkFeeFieldBox, Priority.ALWAYS);
                networkFeeFieldBox.setAlignment(Pos.CENTER_LEFT);
                networkFeeFieldBox.setId("bodyBox");
    
                m_networkFeeField.focusedProperty().addListener((obs,oldval,newval)->{
                    if(m_networkFeeField != null){
                        String str = m_networkFeeField.getText();
                        boolean isZero = Utils.isTextZero(str);
                        if(!newval){
                      
                            BigDecimal defaultFee = m_minNetworkFee;
                            if(isZero){
                                m_networkFeeField.setText("0");
                            }else{
                                
                                BigDecimal newFee = new BigDecimal(Utils.formatStringToNumber(str, ErgoCurrency.DECIMALS));
                                if(newFee.compareTo(defaultFee) == -1){
                                    setNetworkFee(defaultFee);
                                }
                            }
                            
                          
                            if(networkFeeFieldBox.getChildren().contains(networkFeeEnterBtn)){
                                networkFeeFieldBox.getChildren().remove(networkFeeEnterBtn);
                            }
                  
                        }else{
                            if(!networkFeeFieldBox.getChildren().contains(networkFeeEnterBtn)){
                                networkFeeFieldBox.getChildren().add(networkFeeEnterBtn);
                            }
                          
                        }
                    }
                });
    
                HBox networkFeeRow = new HBox(networkFeeLbl, networkFeeFieldBox);
                HBox.setHgrow(networkFeeRow, Priority.ALWAYS);
                networkFeeRow.setPadding(new Insets(2, 0,2,25));
    
    
                m_executeSwapBtn = new Button("Execute");
                m_executeSwapBtn.setPadding(new Insets(5));
                
                HBox executeBtnBox = new HBox(m_executeSwapBtn);
                HBox.setHgrow(executeBtnBox, Priority.ALWAYS);
                executeBtnBox.setAlignment(Pos.CENTER_RIGHT);
                executeBtnBox.setPadding(new Insets(20,20,10, 10));
    
    
                m_txDetailParamsBox = new JsonParametersBox((JsonObject) null, m_colWidth);
                m_txDetailParamsBox.setPadding(new Insets(5,10,5,15));
                m_txDetailsJsonProperty.addListener((obs,oldval,newval)->{
                    m_txDetailParamsBox.update(newval);
                });
    
        
                VBox swapInfoBox = new VBox(swapFeeRow, networkFeeRow, m_txDetailParamsBox, executeBtnBox);
                swapInfoBox.setPadding(new Insets(0,10,0,10));
    
                m_executeSwapBtn.setOnAction(e->{
    
                });
    
                Region layoutSpacer2 = new Region();
                layoutSpacer2.setId("vGradient");
                layoutSpacer2.setMaxHeight(2);
                layoutSpacer2.setMinHeight(2);
                layoutSpacer2.setPrefWidth(40);
    
                HBox layoutSpacerBox = new HBox(layoutSpacer2);
                layoutSpacerBox.setPadding(new Insets(10,0,10, m_colWidth.get() + 20));
                layoutSpacerBox.setAlignment(Pos.CENTER);
                HBox.setHgrow(layoutSpacerBox, Priority.ALWAYS);
    
    
                /*HBox swapEnabledInfoBox = new HBox(selectTokenLabel);
                HBox.setHgrow(swapEnabledInfoBox, Priority.ALWAYS);
                swapEnabledInfoBox.setAlignment(Pos.CENTER);*/
                
                HBox quoteEnabledBox = new HBox();
                HBox.setHgrow(quoteEnabledBox, Priority.ALWAYS);
                quoteEnabledBox.setAlignment(Pos.CENTER_LEFT);
                quoteEnabledBox.setPadding(new Insets(2, 10, 2, 8));
    
                VBox swapEnabledBox = new VBox();
    
                Label ergoBalanceLbl = new Label("Ergo Balance");
                ergoBalanceLbl.minWidthProperty().bind(m_colWidth);
                ergoBalanceLbl.maxWidthProperty().bind(m_colWidth);
                ergoBalanceLbl.setId("logoBox");
    
                TextField ergoBalanceField = new TextField(m_ergoBalanceProperty.get() != null ? m_ergoBalanceProperty.get().toPlainString() : "");
                HBox.setHgrow(ergoBalanceField, Priority.ALWAYS);
                ergoBalanceField.setPadding(new Insets(0,10, 0, 0));
                ergoBalanceField.setEditable(false);
    
               
                m_ergoBalanceProperty.addListener((obs,oldval, newval)->{
                    if(newval != null){
                        ergoBalanceField.setText(newval.toPlainString());
                    }else{
                        ergoBalanceField.setText("");
                    }
                });
    
                m_ergoMaxBtn = new Button("MAX");
                m_ergoMaxBtn.setOnAction(e->{
                    BigDecimal ergoBalance = m_ergoBalanceProperty.get();
                    if(ergoBalance != null){
                        BigDecimal maxAmount = ergoBalance.subtract(m_networkFeeProperty.get()).subtract(m_swapFeeProperty.get()).subtract(m_minNetworkFee);
                        m_ergoAmountField.setText(maxAmount.compareTo(BigDecimal.ZERO) > -1 ? maxAmount.toPlainString() : "0");
                    }else{
                        m_ergoAmountField.setText("0");
                    }
                });
                
                m_ergoBalanceFieldBox = new HBox(ergoBalanceField);
                m_ergoBalanceFieldBox.setId("bodyBox");
                HBox.setHgrow(m_ergoBalanceFieldBox, Priority.ALWAYS);
                m_ergoBalanceFieldBox.setAlignment(Pos.CENTER_LEFT);
    
                HBox ergoBalanceBox = new HBox(ergoBalanceLbl, m_ergoBalanceFieldBox);
                HBox.setHgrow(ergoBalanceBox, Priority.ALWAYS);
                ergoBalanceBox.setAlignment(Pos.CENTER_LEFT);
                ergoBalanceBox.setPadding(new Insets(2,10,2,35));
    
    
                Label tokenBalanceLbl = new Label("Token Balance");
                tokenBalanceLbl.minWidthProperty().bind(m_colWidth);
                tokenBalanceLbl.maxWidthProperty().bind(m_colWidth);
                tokenBalanceLbl.setId("logoBox");
    
                TextField tokenBalanceField = new TextField(m_tokenBalanceProperty.get() != null ? m_tokenBalanceProperty.get().toPlainString() : "");
                HBox.setHgrow(tokenBalanceField, Priority.ALWAYS);
                tokenBalanceField.setPadding(new Insets(0,10, 0, 0));
                tokenBalanceField.setEditable(false);
                tokenBalanceField.setOnMouseClicked(e->{
                    if(!m_isBuyToken.get()){
                        
                    }
                });
    
                m_tokenMaxBtn = new Button("MAX");
                m_tokenMaxBtn.setOnAction(e->{
                    m_tokenAmountField.setText(m_tokenBalanceProperty.get() != null ? m_tokenBalanceProperty.get().toPlainString() : "0");
                    updateOrder();
                });
                
                m_tokenBalanceFieldBox = new HBox(tokenBalanceField);
                m_tokenBalanceFieldBox.setId("bodyBox");
                m_tokenBalanceFieldBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(m_tokenBalanceFieldBox, Priority.ALWAYS);
    
    
                HBox tokenBalanceBox = new HBox(tokenBalanceLbl, m_tokenBalanceFieldBox);
                HBox.setHgrow(tokenBalanceBox, Priority.ALWAYS);
                tokenBalanceBox.setAlignment(Pos.CENTER_LEFT);
                tokenBalanceBox.setPadding(new Insets(10,10,2,35));
    
                m_tokenBalanceProperty.addListener((obs,oldval, newval)->{
                    if(newval != null){
                        tokenBalanceField.setText(newval.toPlainString());
                    }else{
                        tokenBalanceField.setText("");
                    }
                });
    
                VBox m_balanceBoxes = new VBox();
    
                Label tokenSearchLbl = new Label("Search");
                tokenSearchLbl.minWidthProperty().bind(m_colWidth);
                tokenSearchLbl.maxWidthProperty().bind(m_colWidth);
                tokenSearchLbl.setId("logoBox");
    
                TextField tokenSearchField = new TextField("");
                tokenSearchField.setPromptText("Enter token name");
                HBox.setHgrow(tokenSearchField, Priority.ALWAYS);
                tokenSearchField.setPadding(new Insets(0,10, 0, 0));
                tokenSearchField.setId("textAreaInputEmpty");
                
                HBox tokenSearchFieldBox = new HBox(tokenSearchField);
                tokenSearchFieldBox.setId("bodyBox");
                tokenSearchFieldBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(tokenSearchFieldBox, Priority.ALWAYS);
    
    
    
                m_tokenSearchBox = new HBox(tokenSearchLbl, tokenSearchFieldBox);
                HBox.setHgrow(m_tokenSearchBox, Priority.ALWAYS);
                m_tokenSearchBox.setAlignment(Pos.CENTER_LEFT);
                m_tokenSearchBox.setPadding(new Insets(5,10,2,33));
    
                m_tokenSearchBoxHolder = new HBox();
                HBox.setHgrow(m_tokenSearchBoxHolder, Priority.ALWAYS);
                m_tokenSearchBoxHolder.setAlignment(Pos.CENTER_LEFT);
    
                m_priceQuoteScroll = new PriceQuoteScroll("Quotes",defaultTokenBtnString, m_colWidth);
                
                m_updateQuotesEvent = (e)->updateQuotes();
                m_priceQuoteScroll.setOnUpdate(m_updateQuotesEvent);
                m_priceQuoteScroll.setEmptyText("No quotes available");
                m_priceQuoteScroll.setPadding(new Insets(5,8,2, 8));
                HBox.setHgrow(m_priceQuoteScroll, Priority.ALWAYS);
    
                Button tokenSearchCancelBtn = new Button("☓");
                tokenSearchCancelBtn.setFocusTraversable(true);
                tokenSearchCancelBtn.setPadding(Insets.EMPTY);
                tokenSearchCancelBtn.setMinWidth(25);
                tokenSearchCancelBtn.setOnAction(e->{
                    tokenSearchField.setText("");
                });
    
                tokenSearchField.textProperty().addListener((obs,oldval,newval)->{
                    m_searchFilter = newval;
                    if(m_searchFilter.length() > 0){
                        if(!tokenSearchFieldBox.getChildren().contains(tokenSearchCancelBtn)){
                            tokenSearchFieldBox.getChildren().add(tokenSearchCancelBtn);
                            tokenSearchField.setId("textAreaInput");
                        }
                        
                        m_priceQuoteScroll.getPageBox().setOffest(0);
                        
                    }else{
                        if(tokenSearchFieldBox.getChildren().contains(tokenSearchCancelBtn)){
                            tokenSearchFieldBox.getChildren().remove(tokenSearchCancelBtn);
                        }
                        tokenSearchField.setId("textAreaInputEmpty");
                    }
                    updateQuotes();
    
                });
    
                m_tokenMenuRow = new VBox();
                HBox.setHgrow(m_tokenMenuRow, Priority.ALWAYS);
                m_tokenMenuHBox.getChildren().add(m_tokenMenuRow);
                
                       
      
    
                VBox layoutVBox = new VBox(
                    headerBox, 
                    hBox, 
                    m_swapBuySell, 
                    m_tokenInfoVBox,
                    m_tokenSearchBoxHolder,
                    m_priceQuoteScroll, 
                    m_tokenMenuHBox, 
                    quoteEnabledBox, 
                    m_balanceBoxes, 
                    swapEnabledBox 
                    
                );
    
    
    
    
                m_tokenInfoProperty.addListener((obs,oldval,newval)->{
                   if(newval == null){
                        swapEnabledBox.getChildren().clear();
                        m_balanceBoxes.getChildren().clear();
                   }else{
                        if(m_tokenInfoParamsBox != null){
                            m_tokenInfoParamsBox.update(newval.getJsonObject());
                        }
                        swapEnabledBox.getChildren().clear();
                        swapEnabledBox.getChildren().addAll(m_swapBodyBox, layoutSpacerBox, swapInfoBox);
                        if(m_isBuyToken.get()){
                            if(!m_balanceBoxes.getChildren().contains(ergoBalanceBox)){
                                m_balanceBoxes.getChildren().addAll(ergoBalanceBox);
                            }
                        }else{
                        
                            if(!m_balanceBoxes.getChildren().contains(tokenBalanceBox)){
                                m_balanceBoxes.getChildren().add(tokenBalanceBox);
                            }
    
                  
                        }
         
                   }
                });
                getChildren().add(layoutVBox);
    
                m_showQuoteInfoProperty.addListener((obs,oldval,newval)->{
                    showQuoteInfoBtn.setText(newval ? "⏷" : "⏵");
                    if(newval){
                        addQuoteParamsBox();
                    }else{
                        removeQuoteParamsBox();
                    }
                });
    
                updateSwapLayout(m_isBuyToken.get());
    
                m_isBuyToken.addListener((obs, oldVal, newVal)->updateSwapLayout(newVal));
    
                m_tokenQuoteInErg.addListener((obs,oldval,newval)->{
                    if(newval != null){
                        String amount = newval.getTimeStamp() > 0 ? newval.getAmountString() : "⎯"; 
                        priceQuoteField.setText(amount);
                        quoteEnabledBox.getChildren().clear();
                        quoteEnabledBox.getChildren().add(m_quoteInfoBox);
                        if(m_tokenQuoteParmsBox != null){
                            m_tokenQuoteParmsBox.update(newval.getJsonObject());
                        }
    
                        
                    }else{
                        priceQuoteField.setText("⎯");
                        quoteEnabledBox.getChildren().clear();
                        if(m_tokenQuoteParmsBox != null){
                            m_tokenQuoteParmsBox.shutdown();
                        }
                    }
                    updatePriceQuote(newval);
                });
    
                m_tokenIdProperty.addListener((obs,oldval,newval)->{
                    if(newval != null){
                        m_showTokenInfoProperty.set(true);
                        if(m_tokenInfoParamsBox != null){
                            m_tokenInfoParamsBox.update(NoteConstants.getJsonObject("Info", "Getting token info..."));
                        }
                        m_walletControl.getTokenInfo(newval, onSucceeded->{
                            Object obj = onSucceeded.getSource().getValue();
                            if(obj != null && obj instanceof JsonObject){
                                try{
                                    ErgoTokenInfo tokenInfo = new ErgoTokenInfo((JsonObject) obj);
                                    m_showTokenInfoProperty.set(false);
                                    m_tokenInfoProperty.set(tokenInfo);
                                }catch(Exception e){
                                    m_showTokenInfoProperty.set(true);
                                    if(m_tokenInfoParamsBox != null){
                                        m_tokenInfoParamsBox.update(NoteConstants.getJsonObject("Error", "Token Id Invalid"));
                                    }
                                }
                            
                            }else{
                                m_showTokenInfoProperty.set(true);
                                if(m_tokenInfoParamsBox != null){
                                    m_tokenInfoParamsBox.update(NoteConstants.getJsonObject("Error", "Could not verify token"));
                                }
                            }
                        }, onFailed->{
                            m_showTokenInfoProperty.set(true);
                            if(m_tokenInfoParamsBox != null){
                                Throwable throwable = onFailed.getSource().getException();
                                String msg = throwable != null ? throwable.toString() : "Could not retrieve token";
                                m_tokenInfoParamsBox.update(NoteConstants.getJsonObject("Error", msg));
                            }
                        });
                       
    
                        
                    }else{
                        m_tokenInfoProperty.set(null);
                        m_tokenQuoteInErg.set(null);
                    }
                    updateBalance();
                });
                updateTokenMarketStatus(m_tokenQuoteInErg.get());
                
                m_tokenMarketAvailableListener = (obs,oldval,newval)->updateTokenMarketStatus(m_tokenQuoteInErg.get());
                getErgoMarketControl().isTokenMarketAvailableProperty().addListener(m_tokenMarketAvailableListener);
            
    
    
                m_tokenMarketQuotesUpdateListener = (obs,oldval,newval)->tokenQuotesUpdated();
                getErgoMarketControl().tokenMarketLastUpdated().addListener(m_tokenMarketQuotesUpdateListener);
    
    
                m_walletControl.balanceProperty().addListener((obs,oldval,newval)->updateBalance());
                updateBalance();
            }


            public ErgoMarketControl getErgoMarketControl(){
                return m_ergoWalletDataList.getErgoMarketControl();
            }

            private void updateQuotes(){
                Utils.delayObject(null, 20, m_walletControl.getNetworksData().getExecService(), onSucceeded->{
                    m_priceQuoteScroll.clearQuotes();
                    if(m_isBuyToken.get()){
                        
                        updateAvailableTokenQuotes();
                    }else{
                        updateAvailableSellTokens();
                    
                    }
                    if(!m_priceQuoteScroll.isShowing()){
                        m_priceQuoteScroll.show();
                    }
                });
            }
    
            private BigInteger[] updateFeePerTokenFractional(BigDecimal feePerToken){
                BigInteger[] fractional = Utils.decimalToFractional(feePerToken);
                m_feePerTokenNum = fractional[0];
                m_feePerTokenDenom = fractional[1];
                return fractional;
            }
    
            private BigInteger[] updateErgPerTokenFractional(BigDecimal ergPerToken){
                BigInteger[] fractional = Utils.decimalToFractional(ergPerToken);
                m_ergPerTokenNum = fractional[0];
                m_ergPerTokenDenom = fractional[1];
                return fractional;
            }
    
            private void updateTxParams(){
                ErgoTokenInfo tokenInfo = m_tokenInfoProperty.get();
                BigDecimal ergPerToken = getErgPerToken();
                BigDecimal feePerToken = getFeePerToken();
                BigInteger feePerTokenNum = m_feePerTokenNum;
                BigInteger feePerTokenDenom = m_feePerTokenDenom;
                BigInteger ergPerTokenNum = m_ergPerTokenNum;
                BigInteger ergPerTokenDenom = m_ergPerTokenDenom;
                
    
                if(tokenInfo != null){
                    BigDecimal ergoAmount = getErgoAmount();
                    BigDecimal tokenAmount = getTokenAmount();
    
                    JsonObject ergAmountObject = new JsonObject();
                    ergAmountObject.addProperty("decimal", ergoAmount);
                    if(ergoAmount != null && !ergoAmount.equals(BigDecimal.ZERO)){
                        ergAmountObject.addProperty("nanoErgs",ErgoCurrency.getNanoErgsFromErgs(ergoAmount));
                    }
    
                    JsonObject tokenAmountObject = new JsonObject();
                    tokenAmountObject.addProperty("decimal", tokenAmount);
                    if(tokenAmount != null && !tokenAmount.equals(BigDecimal.ZERO)){
                        tokenAmountObject.addProperty("tokens",PriceAmount.calculateBigDecimalToLong(tokenAmount, tokenInfo.getDecimals()));
                    }
    
                    JsonObject ergPerTokenObject = new JsonObject();
                    ergPerTokenObject.addProperty("decimal", ergPerToken != null ? ergPerToken.toPlainString() : "0");
                    if(ergPerToken != null && !ergPerToken.equals(BigDecimal.ZERO)){
                        ergPerTokenObject.addProperty("num", ergPerTokenNum);
                        ergPerTokenObject.addProperty("denom", ergPerTokenDenom);
                    }
    
                    JsonObject feePerTokenObject = new JsonObject();
                    feePerTokenObject.addProperty("decimal", feePerToken != null ? feePerToken.toPlainString() : "0");
                    if(feePerToken != null && !feePerToken.equals(BigDecimal.ZERO)){
                        feePerTokenObject.addProperty("num", feePerTokenNum);
                        feePerTokenObject.addProperty("denom", feePerTokenDenom);
                    }
                    JsonObject detailsObject = new JsonObject();
                    detailsObject.add("ergoAmount", ergAmountObject);
                    detailsObject.add("tokenAmount", tokenAmountObject);
                    detailsObject.add("ergPerToken", ergPerTokenObject);
                    detailsObject.add("feePerToken", feePerTokenObject);
                    
                    JsonObject json = new JsonObject();
                    json.add("txDetails", detailsObject);
                    
                    m_txDetailsJsonProperty.set(json);
                }else{
                    m_txDetailsJsonProperty.set(null);
                }
            }
    
    
            
            private void updateBalance(){
                JsonObject balanceObject = m_walletControl.balanceProperty().get();
               
                if(balanceObject != null){
                    ArrayList<PriceAmount> amountList = NoteConstants.getBalanceList(balanceObject, true, NETWORK_TYPE);
    
                    PriceAmount ergoAmount = NoteConstants.getPriceAmountFromList(amountList, ErgoCurrency.TOKEN_ID);
                    m_ergoBalanceProperty.set(ergoAmount != null ? ergoAmount.getBigDecimalAmount() : BigDecimal.ZERO);
                
                    String tokenId = m_tokenIdProperty.get();
                    if(tokenId != null){
                        PriceAmount tokenAmount = NoteConstants.getPriceAmountFromList(amountList, tokenId);
                        m_tokenBalanceProperty.set(tokenAmount != null ? tokenAmount.getBigDecimalAmount() : null);
                    }else{
                        m_tokenBalanceProperty.set(null);
                    }
                }else{
                    m_ergoBalanceProperty.set(null);
                    m_tokenBalanceProperty.set(null);
                }
            }
       
            private void tokenQuotesUpdated(){
                PriceQuote currentQuote = m_tokenQuoteInErg.get();

                if(m_isBuyToken.get()){
                    updateAvailableTokenQuotes();
                }else{
                    updateAvailableSellTokens();
                }
                boolean isMarketNull = getErgoMarketControl().getTokenMarketInterface() != null;
                if(currentQuote != null && !isMarketNull){
                    String tokenId = currentQuote.getBaseId();
                    getTokenQuoteInErg(tokenId);
                 
                }else{
                    if(isMarketNull){
                        m_tokenQuoteInErg.set(null);
                    }
                }
            }
    
            private void updateSwapLayout(boolean isBuy){
                m_buyBtn.setId( m_isBuyToken.get() ? "iconBtnSelected" : "iconBtn");
                m_sellBtn.setId( m_isBuyToken.get() ? "iconBtn" : "iconBtnSelected");
                if(m_priceQuoteScroll != null){
                    m_priceQuoteScroll.hide();
                    m_priceQuoteScroll.clear();
                }
                m_tokenIdProperty.set(null);
                updateTxParams();
                if(isBuy){
                    setBuyTokenLayout();
                }else{
                    setSellTokenLayout();
                }
               
                if( getErgoMarketControl().getTokenMarketInterface() != null ){
                    if(!m_tokenSearchBoxHolder.getChildren().contains(m_tokenSearchBox)){
                        m_tokenSearchBoxHolder.getChildren().add(m_tokenSearchBox);
                    }
                }else{
                    if(m_isBuyToken.get()){
                        if(m_tokenSearchBoxHolder.getChildren().contains(m_tokenSearchBox)){
                            m_tokenSearchBoxHolder.getChildren().remove(m_tokenSearchBox);
                        }
                    }else{
                        if(!m_tokenSearchBoxHolder.getChildren().contains(m_tokenSearchBox)){
                            m_tokenSearchBoxHolder.getChildren().add(m_tokenSearchBox);
                        }
                    }
                }
            }
    
            private void getTokenQuoteInErg(String tokenId){
                getErgoMarketControl().getTokenQuoteInErg(tokenId, onSucceeded->{
                    Object obj = onSucceeded.getSource().getValue();
                    PriceQuote newQuote = obj != null && obj instanceof JsonObject ? new PriceQuote((JsonObject) obj) : null;
                    m_tokenQuoteInErg.set(newQuote);
                }, onFailed->{
                    m_tokenQuoteInErg.set(null);
                });
            }
    
            private void updatePriceQuote(PriceQuote quote){
                if(quote != null){
                    if(m_tokenQuoteParmsBox != null){
                       
                        m_tokenQuoteParmsBox.update(quote.getJsonObject());
                    }
                    String tokenId = m_tokenIdProperty.get();
                    if(tokenId == null || (tokenId != null && !tokenId.equals(quote.getBaseId()))){
                        setTokenId(quote.getBaseId());
                    }
    
               
                }else{
                    m_showQuoteInfoProperty.set(false);  
                    if(m_tokenIdProperty.get() != null){
                        setTokenId(null);
                    }
                }
                updateTokenMarketStatus(quote);
            }
            //m_tokenInfoParmsBox m_tokenInfoVBox
            private void addTokenInfoParamsBox(){
                ErgoTokenInfo tokenInfo = m_tokenInfoProperty.get();
                JsonObject tokenInfoObject = tokenInfo != null ? tokenInfo.getJsonObject() : NoteConstants.getJsonObject("Info", "Token info unavailable");
                if(m_tokenInfoParamsBox != null && m_tokenMenuRow.getChildren().contains(m_tokenInfoParamsBox)){
                    
                    m_tokenInfoParamsBox.update(tokenInfoObject);
                }else{
                    if(m_tokenInfoParamsBox == null){
                        
                        m_tokenInfoParamsBox = new JsonParametersBox(tokenInfoObject, m_colWidth);
                        m_tokenInfoParamsBox.setPadding(new Insets(2,10,2,30));
                    }else{
                        m_tokenInfoParamsBox.update(tokenInfoObject);
                    }
                    
                    m_tokenInfoVBox.getChildren().add(m_tokenInfoParamsBox);
                }
                
            }
    
            private void removeTokenInfoParamsBox(){
                if(m_tokenInfoParamsBox != null && m_tokenInfoVBox.getChildren().contains(m_tokenInfoParamsBox)){
                    m_tokenInfoVBox.getChildren().remove(m_tokenInfoParamsBox);
                }
    
                m_tokenInfoParamsBox = null;
            }
    
            private void addQuoteParamsBox(){
                PriceQuote quote = m_tokenQuoteInErg.get();
                if(quote != null){
                    if(m_tokenQuoteParmsBox != null && m_quoteInfoBox.getChildren().contains(m_tokenQuoteParmsBox)){
                        m_tokenQuoteParmsBox.update(quote.getJsonObject());
                    }else{
                        if(m_tokenQuoteParmsBox == null){
                            m_tokenQuoteParmsBox = new JsonParametersBox(quote.getJsonObject(), m_colWidth);
                            m_tokenQuoteParmsBox.setPadding(new Insets(0,10,0,25));
                        }else{
                            m_tokenQuoteParmsBox.update(quote.getJsonObject());
                        }
                       
                        m_quoteInfoBox.getChildren().add(m_tokenQuoteParmsBox);
                    }
                }else{
                    removeQuoteParamsBox();
                }
            }
    
            private void removeQuoteParamsBox(){
                if(m_tokenQuoteParmsBox != null && m_quoteInfoBox.getChildren().contains(m_tokenQuoteParmsBox)){
                    m_quoteInfoBox.getChildren().remove(m_tokenQuoteParmsBox);
                }
    
                m_tokenQuoteParmsBox = null;
            }
            private Button m_tokenIdEnterBtn = null;
            private ChangeListener<String> m_tokenIdListener = null;
    
    
    
    
    
            private void updateTokenMarketStatus(PriceQuote quote){
                int status = getErgoMarketControl().getTokenMarketConnectionStatus();
  
                m_priceQuoteScroll.setText(quote != null ? quote.getSymbol() : status == -1 ? " -Disabled-" : status != NoteConstants.READY ? NoteConstants.getStatusCodeMsg(status) + "..." : defaultTokenBtnString);
                
                if(status == NoteConstants.READY || status == NoteConstants.WARNING){
                    if(!m_tokenSearchBoxHolder.getChildren().contains(m_tokenSearchBox)){
                        m_tokenSearchBoxHolder.getChildren().add(m_tokenSearchBox);
                    }
                }else{
                    if(m_isBuyToken.get()){
                        if(m_tokenSearchBoxHolder.getChildren().contains(m_tokenSearchBox)){
                            m_tokenSearchBoxHolder.getChildren().remove(m_tokenSearchBox);
                        }
                    }
                }
            }
    
    
            private void updateAvailableSellTokens(){
                if(m_priceQuoteScroll != null){
                    JsonObject json = m_walletControl.balanceProperty().get();
                    ArrayList<PriceAmount> balanceList = NoteConstants.getBalanceList(json, true, NETWORK_TYPE);
                    if(m_searchFilter != null && m_searchFilter.length() > 0){
                        String lowerCaseFilter = m_searchFilter.toLowerCase();
                        List<PriceAmount> searchList = balanceList.stream().filter(item -> item.getSymbol().toLowerCase().indexOf(lowerCaseFilter) != -1 && !item.getTokenId().equals(ErgoCurrency.TOKEN_ID)).collect(Collectors.toList());
                        int size = searchList.size();
                        m_priceQuoteScroll.clear();
    
                        if(size > 0){
                            for(int i = 0; i < size ; i++){
                                PriceAmount priceAmount = searchList.get(i);
                                if(!priceAmount.getTokenId().equals(ErgoCurrency.TOKEN_ID)){
                                    PriceQuote quote = priceAmount.getPriceQuote();
                                    addQuoteRow(quote != null ? quote :  new PriceQuote(BigDecimal.ZERO, priceAmount.getSymbol(), ErgoCurrency.SYMBOL,priceAmount.getTokenId(), ErgoCurrency.TOKEN_ID, 0L), false);
                                }
                            }
                            m_priceQuoteScroll.getPageBox().setMaxItems(size);
                            m_priceQuoteScroll.updatePageBox();
                        }else{
                            m_priceQuoteScroll.setEmptyText("No tokens");
                        }
                    }else{
                        int size = balanceList.size();
                        m_priceQuoteScroll.clear();
    
                        if(size > 0){
                            for(int i = 0; i < size ; i++){
                                PriceAmount priceAmount = balanceList.get(i);
                                if(!priceAmount.getTokenId().equals(ErgoCurrency.TOKEN_ID)){
                                    PriceQuote quote = priceAmount.getPriceQuote();
                                    addQuoteRow(quote != null ? quote :  new PriceQuote(BigDecimal.ZERO, priceAmount.getSymbol(), ErgoCurrency.SYMBOL,priceAmount.getTokenId(), ErgoCurrency.TOKEN_ID, 0L), false);
                                }
                            }
                            m_priceQuoteScroll.updatePageBox();
                        }else{
                            m_priceQuoteScroll.setEmptyText("No tokens available in wallet");
                        }
                    }
                }
            }
    
            public Future<?> getAvailableTokenQuotes(EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
                int offset = m_priceQuoteScroll.getPageBox().getOffset();
                int limit = m_priceQuoteScroll.getPageBox().getLimit();
                String filter = m_searchFilter;
                return getErgoMarketControl().getAvailableTokenQuotes(offset, limit, filter, onQuotes->{
                    Object obj = onQuotes.getSource().getValue();
                    if(obj != null && obj instanceof JsonObject){
                        JsonObject availableQuotesObj = (JsonObject) obj;
                        JsonElement quotesElement = availableQuotesObj != null ? availableQuotesObj.get("quotes") : null;
                        JsonElement totalElement = availableQuotesObj != null ? availableQuotesObj.get("total") : null;
                        
                        m_priceQuoteScroll.getPageBox().setMaxItems(totalElement != null ? totalElement.getAsInt() : -1);

                        JsonArray quotesArray = quotesElement != null && quotesElement.isJsonArray() ? quotesElement.getAsJsonArray() : null;
                        
                        Utils.returnObject(quotesArray, getExecService(), onSucceeded);
                    }else{
                        Utils.returnException("No quotes available", getExecService(), onFailed);
                    }
                }, onFailed);
            }
                    
    
            private void updateAvailableTokenQuotes(){
                boolean hasItems = m_priceQuoteScroll.size() > 0;
                if(getErgoMarketControl().getTokenMarketConnectionStatus() != NoteConstants.DISABLED){
                    getAvailableTokenQuotes(onSucceeded->{
                        Object obj = onSucceeded.getSource().getValue();
                     
                        JsonArray availableQuotes = obj != null && obj instanceof JsonArray ? (JsonArray) obj : null;
                        
                        if(availableQuotes != null){
                            int size = availableQuotes.size();
                            if(size > 0){

                                for(int i = 0; i < size ; i++){
                                    JsonElement quoteElement = availableQuotes.get(i);
                                    if(quoteElement != null && !quoteElement.isJsonNull() && quoteElement.isJsonObject()){
                                        PriceQuote quote = new PriceQuote(quoteElement.getAsJsonObject());
                                        if(!hasItems){
                                            addQuoteRow(quote, false);
                                        }else{
                                            updateQuoteRow(quote);
                                        }
                                    }
                                }
                                m_priceQuoteScroll.updatePageBox();
                            //    m_availableTokensTimeStamp = System.currentTimeMillis();
                            }else{
                                if(hasItems){
                                    m_priceQuoteScroll.clear();
                                }
                                m_priceQuoteScroll.setEmptyText("No tokens found");

                            }
                        }else{
                            if(hasItems){
                                m_priceQuoteScroll.clear();
                            }
                            m_priceQuoteScroll.setEmptyText("Tokens loading...");
                        }
                    }, onFailed->{
                        Throwable error = onFailed.getSource().getException();
                        String msg = error != null ?  error.getMessage() : "Error";
                        if(hasItems){
                            m_priceQuoteScroll.clear();
                        }
                        m_priceQuoteScroll.setEmptyText(msg);
                    });
                    
                }else{         
                    if(hasItems){
                        m_priceQuoteScroll.clear();
                    }
                    m_priceQuoteScroll.setEmptyText("-Token Market Disabled-");
                }
            }
    
    
    
            private void addQuoteRow(PriceQuote quote, boolean update){
                PriceQuoteRow row = new PriceQuoteRow(quote);
                row.setTopRowClicked(e->{
                    PriceQuote rowQuote = row.getPriceQuote();
                    m_tokenQuoteInErg.set(rowQuote);
                    m_orderPriceField.setText(rowQuote.getBigDecimalQuote().toPlainString());
                    m_ergoAmountField.setText("0");
                    m_priceQuoteScroll.hide();
                });
                m_priceQuoteScroll.addRow(row, update);
            }
    
            private void updateQuoteRow(PriceQuote quote){
                PriceQuoteRow existingRow = m_priceQuoteScroll.getRow(quote.getId());
                if(existingRow != null){
                    existingRow.updateQuote(quote);
                }else{
                    addQuoteRow(quote, true);
                }
            }
    
            private HBox getTokenAmountBox(boolean isBuy){
                if(m_tokenAmountRow != null){
                    clearTokenAmountRow();
                }
                m_tokenAmountLbl = new Label("Token Amount");
                HBox.setHgrow(m_tokenAmountLbl,Priority.ALWAYS);
                m_tokenAmountLbl.minWidthProperty().bind(m_colWidth);
                m_tokenAmountLbl.maxWidthProperty().bind(m_colWidth);
                m_tokenAmountLbl.setId("logoBox");
    
    
                m_tokenAmountEnterBtn = new Button("↵");
                m_tokenAmountEnterBtn.setFocusTraversable(true);
                m_tokenAmountEnterBtn.setPadding(Insets.EMPTY);
                m_tokenAmountEnterBtn.setMinWidth(25);
    
                m_tokenAmountField = new TextField("0");
                HBox.setHgrow(m_tokenAmountField, Priority.ALWAYS);
                m_tokenAmountField.setPadding(new Insets(0,10, 0, 0));
                m_tokenAmountField.setEditable(!isBuy);
    
                m_tokenAmountField.setOnAction(e->{
                    Platform.runLater(()->{
                        if(m_tokenAmountEnterBtn != null){
                            m_tokenAmountEnterBtn.requestFocus();
                        }
                    });
                });
    
                m_tokenAmountFieldBox = new HBox(m_tokenAmountField);
                HBox.setHgrow(m_tokenAmountFieldBox, Priority.ALWAYS);
                m_tokenAmountFieldBox.setAlignment(Pos.CENTER_LEFT);
                m_tokenAmountFieldBox.setId("textFieldBox");
                m_tokenAmountFieldBox.setOnMouseClicked(e->m_tokenAmountField.requestFocus());
               
                if(!isBuy){
                 
                    m_tokenAmountTextListener = (obs,oldval,newval)->{
                      
                        if(newval.length() > 0){
                            ErgoTokenInfo tokenInfo = getTokenInfo();
                            if( tokenInfo != null){
                                int decimals = tokenInfo.getDecimals();
                                String number = newval.replaceAll("[^0-9.]", "");
                                
                                int index = number.indexOf(".");
                                String leftSide = index != -1 ? number.substring(0, index + 1) : number;
                                String rightSide = index != -1 ?  number.substring(index + 1) : "";
                                rightSide = rightSide.length() > 0 ? rightSide.replaceAll("[^0-9]", "") : "";
                                rightSide = rightSide.length() > decimals ? rightSide.substring(0, decimals) : rightSide;
                                number = (leftSide + rightSide);
                                m_tokenAmountField.setText(number);
    
                                BigDecimal orderPrice = getOrderPrice();
                    
                                BigDecimal tokenAmount = Utils.isTextZero(number) ? BigDecimal.ZERO : new BigDecimal(number.indexOf(".") != number.length() - 1 ? number : number.substring(0, number.length() -1));
                                
                                setTokenAmount(tokenAmount);
                
                                updateErgoFromTokenAmount(tokenAmount, orderPrice, tokenInfo);
                            }
                        }
                       
                    };
                    m_tokenAmountField.setOnKeyPressed(e->{
                        if (Utils.keyCombCtrZ.match(e) ) { 
                            e.consume();
                        }
                    });
                    m_tokenAmountField.textProperty().addListener(m_tokenAmountTextListener);
                    m_tokenAmountFocusListener = (obs,oldval,newval)->{
                        if(m_tokenAmountField != null && m_tokenAmountEnterBtn != null && m_tokenAmountFieldBox != null){
                            if(!m_isBuyToken.get()){
                                String str = m_tokenAmountField.getText();
                                boolean isZero = Utils.isTextZero(str);
                                if(!newval){
                                    if(isZero){
                                        m_tokenAmountField.setText("0");
                                        setTokenAmount(BigDecimal.ZERO);
                                    }else{
                                        int scale = m_tokenInfoProperty.get().getDecimals();
                                        BigDecimal tokenAmount = new BigDecimal(Utils.formatStringToNumber(str, scale));
                                        setTokenAmount(tokenAmount);
                                    }
                                    updateOrder();
                                    if(m_tokenAmountFieldBox.getChildren().contains(m_tokenAmountEnterBtn)){
                                        m_tokenAmountFieldBox.getChildren().remove(m_tokenAmountEnterBtn);
                                    }
                                }else{
                                    if(isZero){
                                        m_tokenAmountField.setText("");
                                    }
                                    if(!m_tokenAmountFieldBox.getChildren().contains(m_tokenAmountEnterBtn)){
                                        m_tokenAmountFieldBox.getChildren().add(m_tokenAmountEnterBtn);
                                    }
                                
                                }
                            }
                        }
                    };
                    m_tokenAmountField.focusedProperty().addListener(m_tokenAmountFocusListener);
    
                
                }
    
    
    
                m_tokenAmountRow = new HBox(m_tokenAmountLbl, m_tokenAmountFieldBox);
                HBox.setHgrow(m_tokenAmountRow, Priority.ALWAYS);
                m_tokenAmountRow.setPadding(new Insets(2, 0,2,25));
            
                return m_tokenAmountRow;
            }
    
            private void setTokenId(String tokenId){
                m_tokenIdProperty.set(tokenId);
            }
    
            private void setErgoAmount(BigDecimal ergoAmount){
                m_ergoAmount = ergoAmount != null ? ergoAmount : BigDecimal.ZERO;
            }
    
            private void updateTokensFromErgoAmount(BigDecimal ergoAmount, BigDecimal orderPrice, ErgoTokenInfo tokenInfo){
                setErgoAmount(ergoAmount);
    
                if(tokenInfo != null && ergoAmount.compareTo(BigDecimal.ZERO) == 1){
                    BigDecimal tokenAmount = calculateDecimalTokenFromErgo(ergoAmount, orderPrice, tokenInfo.getDecimals());
                    setTokenAmount(tokenAmount);
                    m_tokenAmountField.setText(tokenAmount.toPlainString());
                    updatePerToken(ergoAmount, tokenAmount, m_swapFeeProperty.get(), tokenInfo.getDecimals());
                }else{
                    setTokenAmount(BigDecimal.ZERO);
                    m_tokenAmountField.setText("0");
                    updatePerToken(BigDecimal.ZERO, BigDecimal.ZERO, m_swapFeeProperty.get(), 0);
                }
                updateTxParams();
            }
    
            private void updateErgoFromTokenAmount(BigDecimal tokenAmount, BigDecimal orderPrice, ErgoTokenInfo tokenInfo){
                setTokenAmount(tokenAmount);
    
                if(tokenInfo != null && tokenAmount != null && tokenAmount.compareTo(BigDecimal.ZERO) == 1){
                    BigDecimal ergoAmount = calculateDecimalErgoFromToken(tokenAmount, orderPrice);
                    setErgoAmount(ergoAmount);
                    m_ergoAmountField.setText(ergoAmount.toPlainString());
                    updatePerToken(ergoAmount, tokenAmount, m_swapFeeProperty.get(), tokenInfo.getDecimals());
                }else{
                    setErgoAmount(BigDecimal.ZERO);
                    m_ergoAmountField.setText("0");
                    updatePerToken(BigDecimal.ZERO, BigDecimal.ZERO, m_swapFeeProperty.get(), 0);
                }
                updateTxParams();
            }
    
            private static BigDecimal calculateDecimalTokenFromErgo(BigDecimal ergoAmount, BigDecimal orderPrice, int decimals){
      
                if(ergoAmount != null && ergoAmount.compareTo(BigDecimal.ZERO) ==1 && orderPrice != null && orderPrice.compareTo(BigDecimal.ZERO) == 1){
                    BigDecimal tokenAmount = ergoAmount.divide(orderPrice, decimals, RoundingMode.HALF_UP);
                    return tokenAmount;
                }else{
                    return BigDecimal.ZERO;
                }
            }
    
            private static BigDecimal calculateDecimalErgoFromToken(BigDecimal tokenAmount, BigDecimal orderPrice){
                if(tokenAmount != null && tokenAmount.compareTo(BigDecimal.ZERO) ==1 && orderPrice != null && orderPrice.compareTo(BigDecimal.ZERO) == 1){
                    return tokenAmount.multiply(orderPrice).setScale(ErgoCurrency.DECIMALS, RoundingMode.FLOOR);
                }else{
                    return BigDecimal.ZERO;
                }
            }
    
            private BigInteger[] updatePerToken(BigDecimal ergoAmount, BigDecimal tokenAmount, BigDecimal swapFee, int tokenDecimals){
                BigDecimal nanoErgsAmount = ergoAmount != null && ergoAmount.compareTo(BigDecimal.ZERO) == 1 ? getNanoErgBigDecimal(ergoAmount) : null;
                BigDecimal tokenLongAmount = tokenAmount != null && tokenAmount.compareTo(BigDecimal.ZERO) == 1 && tokenDecimals > -1 ? getTokenBigDecimal(tokenAmount, tokenDecimals): null;
                BigDecimal feeNanoErgAmount = swapFee != null && swapFee.compareTo(BigDecimal.ZERO) == 1 ? getNanoErgBigDecimal(swapFee) : null;
    
                BigDecimal ergPerToken = nanoErgsAmount != null && nanoErgsAmount.compareTo(BigDecimal.ZERO) == 1 && tokenLongAmount != null && tokenLongAmount.compareTo(BigDecimal.ZERO) == 1 ? nanoErgsAmount.divide(tokenLongAmount, MATH_SCALE, RoundingMode.FLOOR) : null;
                BigDecimal feePerToken = feeNanoErgAmount != null && feeNanoErgAmount.compareTo(BigDecimal.ZERO) == 1 && tokenLongAmount != null && tokenLongAmount.compareTo(BigDecimal.ZERO) == 1 ? feeNanoErgAmount.divide(tokenLongAmount, MATH_SCALE, RoundingMode.FLOOR) : null;
               
                
                setFeePerToken(feePerToken);
                return setErgPerToken(ergPerToken);
            }
    
            public static BigDecimal getNanoErgBigDecimal(BigDecimal ergoAmount){
                BigDecimal pow = BigDecimal.valueOf(10).pow(ErgoCurrency.DECIMALS);
                return ergoAmount.multiply(pow);
            }
    
            public static BigDecimal getTokenBigDecimal(BigDecimal tokenAmount, int decimals){
                BigDecimal pow = BigDecimal.valueOf(10).pow(decimals);
                return tokenAmount.multiply(pow);
            }
    
            private BigDecimal getErgPerToken(){
                return m_ergPerToken;
            }
    
            private BigDecimal getFeePerToken(){
                return m_feePerToken;
            }
    
            private BigInteger[] setErgPerToken(BigDecimal ergPerToken){
                m_ergPerToken = ergPerToken;
                return updateErgPerTokenFractional(ergPerToken);
            }
    
            private BigInteger[] setFeePerToken(BigDecimal feePerToken){
                m_feePerToken = feePerToken;
                return updateFeePerTokenFractional(feePerToken);
            }
    
            public BigDecimal getErgoAmount(){
                return m_ergoAmount;
            }
    
            private void setOrderPrice(BigDecimal price){
                m_orderPrice = price;
            }
    
            private void updateOrder(){
                ErgoTokenInfo tokenInfo = getTokenInfo();
                BigDecimal price = getOrderPrice();
                if(m_isBuyToken.get()){
                    BigDecimal ergoAmount = getErgoAmount();
    
                    
                    if(tokenInfo != null && ergoAmount != null && ergoAmount.compareTo(BigDecimal.ZERO) == 1 && price != null && price.compareTo(BigDecimal.ZERO) == 1){    
                        int decimals = tokenInfo.getDecimals();
                        BigDecimal tokenAmount = calculateDecimalTokenFromErgo(ergoAmount, price, decimals);
                    
                        BigInteger[] ergPerToken = updatePerToken(ergoAmount, tokenAmount, m_swapFeeProperty.get(), decimals);
                        
                        long nanoErg = Utils.calculateErgFromPerToken(PriceAmount.calculateBigDecimalToLong(tokenAmount, decimals), ergPerToken[0], ergPerToken[1]);
                        BigDecimal newErgoAmount = ErgoCurrency.getErgsFromNanoErgs(nanoErg);
    
                        m_ergoAmountField.textProperty().removeListener(m_ergoAmountTextChanged);
                        m_ergoAmountField.setText(newErgoAmount.toPlainString());
                        m_ergoAmountField.textProperty().addListener(m_ergoAmountTextChanged);
                        setErgoAmount(newErgoAmount);
                        m_tokenAmountField.setText(tokenAmount.toPlainString());
                        setTokenAmount(tokenAmount);
                    }else{
                        setTokenAmount(BigDecimal.ZERO);
                        m_tokenAmountField.setText("0");
                        updatePerToken(BigDecimal.ZERO, BigDecimal.ZERO, m_swapFeeProperty.get(), tokenInfo != null ? tokenInfo.getDecimals() : 0);
                    }
            
                }else{
                    BigDecimal tokenAmount = getTokenAmount();
                    if(tokenInfo != null && tokenAmount != null && tokenAmount.compareTo(BigDecimal.ZERO) == 1 && price != null && price.compareTo(BigDecimal.ZERO) == 1){    
                        int decimals = tokenInfo.getDecimals();
                        BigDecimal ergoAmount = calculateDecimalErgoFromToken(tokenAmount, price);
                    
                        BigInteger[] ergPerToken = updatePerToken(ergoAmount, tokenAmount, m_swapFeeProperty.get(), decimals);
                        
                        long tokens = Utils.calculateTokensFromPerToken(ErgoCurrency.getNanoErgsFromErgs(ergoAmount), ergPerToken[0], ergPerToken[1]);
                        BigDecimal newTokenAmount = PriceAmount.calculateLongToBigDecimal(tokens, decimals);
    
                        m_tokenAmountField.textProperty().removeListener(m_tokenAmountTextListener);
                        m_tokenAmountField.setText(newTokenAmount.toPlainString());
                        m_tokenAmountField.textProperty().addListener(m_tokenAmountTextListener);
                        setTokenAmount(newTokenAmount);
                        m_ergoAmountField.setText(ergoAmount.toPlainString());
                        setErgoAmount(ergoAmount);
                    }else{
                        setErgoAmount(BigDecimal.ZERO);
                        m_ergoAmountField.setText("0");
                        updatePerToken(BigDecimal.ZERO, BigDecimal.ZERO, m_swapFeeProperty.get(), tokenInfo != null ? tokenInfo.getDecimals() : 0);
                    }
                }
    
                updateTxParams();
            }
    
            public BigDecimal getOrderPrice(){
                return m_orderPrice;
            }
            
            private ErgoTokenInfo getTokenInfo(){
                return m_tokenInfoProperty.get();
            }
    
            private void setTokenAmount(BigDecimal tokenAmount){
                m_tokenAmount = tokenAmount != null ? tokenAmount : BigDecimal.ZERO;
            }
    
            public BigDecimal getTokenAmount(){
                return m_tokenAmount;
            }
    
    
            private void setNetworkFee(BigDecimal fee){
                m_networkFeeProperty.set(fee);
            }
    
            private void setSwapFee(BigDecimal fee){
                fee = fee.compareTo(MIN_SWAP_FEE) == -1 ? MIN_SWAP_FEE : fee;
                m_swapFeeProperty.set(fee);
                ErgoTokenInfo tokeninfo  =m_tokenInfoProperty.get();
                if(tokeninfo != null){
                    updatePerToken(getErgoAmount(), getTokenAmount(), fee, tokeninfo.getDecimals());
                }else{
                    updatePerToken(BigDecimal.ZERO, BigDecimal.ZERO, fee, 0);
                }
            }
    
            private void clearTokenAmountRow(){
                if(m_tokenAmountRow != null){
                    m_tokenAmountRow.getChildren().clear();
                    if(m_tokenAmountFieldBox != null){
                        m_tokenAmountFieldBox.setOnMouseClicked(null);
                        m_tokenAmountFieldBox.getChildren().clear();
                    }
                    if(m_tokenAmountLbl != null){
                        m_tokenAmountLbl.minWidthProperty().unbind();
                        m_tokenAmountLbl.maxWidthProperty().unbind();
                    }
                    
                    if(m_tokenAmountField != null){
                        
                        if(m_tokenAmountFocusListener != null){
                            m_tokenAmountField.focusedProperty().removeListener(m_tokenAmountFocusListener);
                            m_tokenAmountFocusListener = null;
                        }
                        if(m_tokenAmountTextListener != null){
                            m_tokenAmountField.textProperty().removeListener(m_tokenAmountTextListener);
                            m_tokenAmountTextListener = null;
                        }
                        m_tokenAmountField.setOnAction(null);
                    }else{
                        m_tokenAmountFocusListener = null;
                        m_tokenAmountTextListener = null;
                    }
                    if(m_tokenAmountEnterBtn != null){
                        m_tokenAmountEnterBtn = null;
                    }
                    m_tokenAmountLbl = null;
                    m_tokenAmountRow = null;
                   
                    m_tokenAmountFieldBox = null;
                    m_tokenAmountField = null;
                }
            }
    
    
           
            private void setBuyTokenLayout(){
                m_swapBodyBox.getChildren().clear();
                m_tokenSwapVBox.getChildren().clear();
              
                m_tokenSwapVBox.getChildren().addAll( getTokenAmountBox(true));
                m_ergoAmountField.setEditable(true);
                m_tokenAmountField.setEditable(false);
                m_ergoSwapVBox.setAlignment(Pos.BOTTOM_CENTER);
                m_swapBodyBox.getChildren().addAll(m_ergoSwapVBox, m_orderPriceBox, m_tokenSwapVBox);
        
                m_executeSwapBtn.setText("Buy Tokens");
                m_ergoAmountField.setText("0");
                m_tokenAmountField.setText("0");
    
                if(!m_ergoBalanceFieldBox.getChildren().contains(m_ergoMaxBtn)){
                    m_ergoBalanceFieldBox.getChildren().add(m_ergoMaxBtn);
                }
                if(m_tokenBalanceFieldBox.getChildren().contains(m_tokenMaxBtn)){
                    m_tokenBalanceFieldBox.getChildren().remove(m_tokenMaxBtn);
                }
                
            }
    
            private void setSellTokenLayout(){
                m_swapBodyBox.getChildren().clear();
                m_tokenSwapVBox.getChildren().clear();
                m_tokenSwapVBox.getChildren().addAll( getTokenAmountBox(false));
                m_ergoAmountField.setEditable(false);
                m_tokenAmountField.setEditable(true);
                m_ergoSwapVBox.setAlignment(Pos.TOP_CENTER);
    
                m_swapBodyBox.getChildren().addAll(m_tokenSwapVBox,  m_orderPriceBox, m_ergoSwapVBox);
               
                m_executeSwapBtn.setText("Sell Tokens");
                m_ergoAmountField.setText("0");
                m_tokenAmountField.setText("0");
    
                if(m_ergoBalanceFieldBox.getChildren().contains(m_ergoMaxBtn)){
                    m_ergoBalanceFieldBox.getChildren().remove(m_ergoMaxBtn);
                }
                if(!m_tokenBalanceFieldBox.getChildren().contains(m_tokenMaxBtn)){
                    m_tokenBalanceFieldBox.getChildren().add(m_tokenMaxBtn);
                }
            }
    
            @Override
            public void shutdown(){
                if(m_tokenMarketQuotesUpdateListener != null){
                    getErgoMarketControl().tokenMarketLastUpdated().removeListener(m_tokenMarketQuotesUpdateListener);
                    m_tokenMarketQuotesUpdateListener = null;
                }
                if(m_tokenMarketAvailableListener != null){
                    getErgoMarketControl().isTokenMarketAvailableProperty().removeListener(m_tokenMarketAvailableListener);
                    m_tokenMarketAvailableListener = null;
                }
            }
        }

        

        protected void updateErgoNetworkObject(){
            NoteInterface ergoNoteInterface = m_ergoWalletDataList.getErgoNetworkInterface();
            String locationId = m_ergoWalletDataList.getLocationId();
            NoteConstants.getNetworkObject(ergoNoteInterface, locationId, getExecService(), onNetworkObject->{
                Object obj = onNetworkObject.getSource().getValue();
                if(obj != null && obj instanceof JsonObject){
                    JsonObject networkObject = (JsonObject) obj;
                    
                    
                    NoteConstants.getAppIconFromNetworkObject(networkObject, getExecService(), onImage->{
                        Object imgObj = onImage.getSource().getValue();
                        if(imgObj != null && imgObj instanceof Image){
                            setNetworkIcon((Image) imgObj);
                        }else{
                            setUnknownNetworkIcon();
                        }
                    }, onImageFailed->{
                        setUnknownNetworkIcon();
                    });
                    setNetworkText(NoteConstants.getNameFromNetworkObject(networkObject));
                }else{
                    setUknownNetworkText();
                    setUnknownNetworkIcon();
                }

            }, onFailed->{
                setUknownNetworkText();
                setUnknownNetworkIcon();
            });
      

        }
    
        
        @Override
        public void sendMessage(int code, long timeStamp,String networkId, String msg){
    
            AppBox appBox  = m_currentBox.get();
            if(appBox != null){
                JsonElement msgElement = msg != null ? m_jsonParser.parse(msg) : null;
                if(msgElement != null && msgElement.isJsonObject()){
                    appBox.sendMessage(code, timeStamp, networkId, msg);
                }
            }
            
        }
    
    
    
    
        private void updateWallet(boolean isNull){
            

            if (isNull) {
                m_openWalletBtn.setText("[select]");
      
                if (m_walletBodyBox.getChildren().contains(m_selectedAddressBox)) {
                    m_walletBodyBox.getChildren().remove(m_selectedAddressBox);
                }
    
                if (m_walletFieldBox.getChildren().contains(m_disableWalletBtn)) {
                  
                    m_walletFieldBox.getChildren().remove(m_disableWalletBtn);
                }
             
            } else {
              
                m_openWalletBtn.setText(m_walletControl.getWalletName());
    
                if (!m_walletBodyBox.getChildren().contains(m_selectedAddressBox)) {
                    m_walletBodyBox.getChildren().add(m_selectedAddressBox);
                }
       
                if (!m_walletFieldBox.getChildren().contains(m_disableWalletBtn)) {
             
                    m_walletFieldBox.getChildren().add(m_disableWalletBtn);
                }
                showWallet.set(true);
            }
    
            
        }

        
        private void setUnknownNetworkIcon(){
            m_networkMenuBtnImageView.setImage(Stages.unknownImg);
        }
   

        private void setNetworkText(String text){
            networkTip.setText(text);
        }

        private void setNetworkIcon(Image icon){
            m_networkMenuBtnImageView.setImage(icon);
        }


        private void setUknownNetworkText(){
            networkTip.setText(UNKNOWN_NETWORK_TEXT);
        }


        @Override
        public String getName() {
            return NAME;
        }

        @Override
        public void setStatus(String status) {
            
        }

        @Override
        public String getStatus() {
            return NoteConstants.getStatusCodeMsg(ErgoWallets.this.getConnectionStatus());
        }

        @Override
        public SimpleStringProperty titleProperty() {
            return null;
        }
    }
        
        
    
}
