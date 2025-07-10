package io.netnotes.engine.apps.ergoWallets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.ergoplatform.appkit.NetworkType;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.netnotes.engine.AppBox;
import io.netnotes.engine.ErgoMarketControl;
import io.netnotes.engine.Network;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteMsgInterface;
import io.netnotes.engine.Stages;
import io.netnotes.engine.TabInterface;
import io.netnotes.engine.Utils;
import io.netnotes.engine.WalletLockBox;
import io.netnotes.engine.apps.AppConstants;
import io.netnotes.engine.networks.ergo.ErgoNetwork;
import io.netnotes.engine.networks.ergo.ErgoNetworkControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;

public class ErgoWallets extends Network  {
    public final static String NETWORK_ID = "ERGO_WALLETS";
    public final static String DESCRIPTION = "Ergo Wallet allows you to create and manage and access wallets for the Ergo blockchain.";
    public final static String NAME = "Ergo Wallets";
    public final static String ICON = "/assets/ergo-wallet-30.png";

    public final static ExtensionFilter ergExt = new ExtensionFilter("Ergo Wallet (Satergo compatible)", "*.erg");

    public final static String DONATION_ADDRESS_STRING = "9h123xUZMi26FZrHuzsFfsTpfD3mMuTxQTNEhAjTpD83EPchePU";
    
    public final static NetworkType NETWORK_TYPE = NetworkType.MAINNET;

    private final SimpleLongProperty m_timeStampProperty = new SimpleLongProperty(0);

    private ErgoWalletDataList m_ergoWalletDataList = null;
    private NetworksData m_networksData;
    private String m_locationId = null;

    private HashMap<String, NetworkInformation> m_authorizedLocations = new HashMap<>();

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
        }else{

            save();
        }
      

        m_ergoWalletDataList = new ErgoWalletDataList(this, NETWORK_TYPE, m_locationId);

    }

    protected void readAuthorizedLocations(JsonObject json){
        JsonElement authorizedElement = json.get("authorizedLocations");
        JsonArray authorizedArray = authorizedElement != null && authorizedElement.isJsonArray() ? authorizedElement.getAsJsonArray() : new JsonArray();
        m_authorizedLocations.clear();
        for(int i = 0; i < authorizedArray.size() ; i++){
            JsonElement element = authorizedArray.get(i);
            if(element != null && !element.isJsonNull() && element.isJsonPrimitive()){
                addAuthorizedLocation(element.getAsString(), false);
            }
        }
    }

    protected void addAuthorizedLocations(JsonObject json){
        JsonArray authorizedLocations = new JsonArray();
        for (Map.Entry<String, NetworkInformation> entry : m_authorizedLocations.entrySet()) {
        
            authorizedLocations.add(new JsonPrimitive(entry.getValue().getNetworkId()));
        }
        json.add("authorizedLocations", authorizedLocations);
    }


    @Override
    public JsonObject getJsonObject(){
        JsonObject json = super.getJsonObject();
        addAuthorizedLocations(json);
        return json;
    }

    @Override
    public NetworkInformation getNetworkInformation(){
        return new NetworkInformation(NETWORK_ID, NAME, new Image( ICON), new Image( getSmallAppIconString()), DESCRIPTION);
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


    private boolean isLocationAuthorized(String locationNetworkId){
        if(locationNetworkId != null){
           return getAuthorizedLocationByNetworkId(locationNetworkId) != null;
        }
        return false;
    }

    private NetworkInformation getAuthorizedLocationByNetworkId(String networkId){
        return m_authorizedLocations.get(networkId);
    }
   
 
    
    private void addAuthorizedLocation(String networkId, boolean isSave){
        if(networkId != null && !isLocationAuthorized(networkId)){
            NetworkInformation networkInformation = getNetworksData().getLocationNetworkInformationByNeworkId(networkId);

           m_authorizedLocations.put(networkId, networkInformation);
          
            if(isSave){
                save();
            }
           
        }
    
    }



    protected boolean removeAuthorizedLocation(String networkId){
        if(networkId != null){
            boolean removed = m_authorizedLocations.remove(networkId) != null;
            
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
                    if(code == NoteConstants.LIST_ITEM_REMOVED && networkId != null){
                        checkLocations();
                        m_ergoWalletDataList.getErgoMarketControl().checkAvailablility();
                    }

                }
                
            };
  
            getNetworksData().addMsgListener(m_networksDataMsgInterface);
        }
    }

    private void checkLocations(){

        ArrayList<String> removeNetworkIds = new ArrayList<>();

        for (Map.Entry<String, NetworkInformation> entry : m_authorizedLocations.entrySet()) {
              
            String locationNetworkId = entry.getKey();
            if(getNetworksData().getLocationNetworkInformationByNeworkId(locationNetworkId) == null){
                removeNetworkIds.add(locationNetworkId);
            }
        }

        for(String removeId : removeNetworkIds){
            m_authorizedLocations.remove(removeId);
        }
    }

    
    /*
    restoreMnemonicStage
    createwalletFIle
    */
    

    public Future<?> sendNote(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        JsonElement cmdElement = note.get(NoteConstants.CMD);
        if(m_ergoWalletDataList != null){
            if (cmdElement != null && !cmdElement.isJsonNull() && cmdElement.isJsonPrimitive()) {
                
                JsonElement locationIdElement = note.get("locationId");
                String locationId = locationIdElement != null && locationIdElement.isJsonPrimitive() ? locationIdElement.getAsString() : null; 
                NetworkInformation networkInformation = getNetworksData().getLocationNetworkInformation(locationId);

                boolean isLocation = isLocationAuthorized(networkInformation.getNetworkId());
                boolean isThisApp = m_locationId.equals(locationId);
                String cmd  = cmdElement.getAsString();

                if(cmd.equals("getNetworkObject")){
                    return getNetworkObject(onSucceeded, onFailed);
                }else if(isThisApp || isLocation){
                    
                    switch(cmd){
                        case "getWallets":
                            return Utils.returnObject(m_ergoWalletDataList.getWallets(), getExecService(), onSucceeded);
                        case "getWalletById":
                            return Utils.returnObject(m_ergoWalletDataList.getWalletById(note), getExecService(), onSucceeded);
                        case "openWallet":
                            return m_ergoWalletDataList.openWallet(note, onSucceeded, onFailed);
                        case "removeWallets":
                            return Utils.returnObject(m_ergoWalletDataList.removeWallets(note), getExecService(), onSucceeded);
                        case "createWallet":
                            m_ergoWalletDataList.createWallet(note, onSucceeded, onFailed);
                        break;
                        case "getAccessId":
                            m_ergoWalletDataList.getAccessId(networkInformation, note, onSucceeded, onFailed);
                        break;
                        default:
                            JsonElement accessIdElement = note.get("accessId");
                            String accessId = accessIdElement != null && !accessIdElement.isJsonNull() ? accessIdElement.getAsString() : null;
                            if(accessId != null){
                                m_ergoWalletDataList.sendNote(cmd, accessId, networkInformation, note, onSucceeded, onFailed);
                            }
                    }
                
                }else if(networkInformation != null){
                    getNetworksData().verifyAppKey("Ergo Wallets", note, networkInformation, System.currentTimeMillis(), (onVerified)->{
                        addAuthorizedLocation(networkInformation.getNetworkId(), true);
                        sendNote(note, onSucceeded, onFailed);
                    }, onFailed);
                }
            
            }else{
                return Utils.returnException(NoteConstants.CMD_NOT_PRESENT, getExecService(), onFailed);
            }
        }else{
            return Utils.returnException(NoteConstants.STATUS_STARTING, getExecService(), onFailed);
        }
       
        return null;

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
            if(m_ergoWalletDataList != null){
                for(int i = 0; i < msgListeners().size() ; i++){
                    NoteMsgInterface msgInterface = msgListeners().get(i);
                    String msgInterfaceId = msgInterface.getId();
                    if(msgInterfaceId != null ){
                        if(!networkId.equals(NETWORK_ID)){
                            int indexOfColon = networkId.indexOf(":");
                
                            String walletId = indexOfColon > 0 ? networkId.substring(0, indexOfColon) : networkId;
                            if(walletId != null){
                                String interfaceWalletId = m_ergoWalletDataList.getWalletIdFromAccessId(msgInterfaceId);
                                if(interfaceWalletId != null && interfaceWalletId.equals(walletId)){
                                    msgInterface.sendMessage(code, timeStamp, networkId, msg);
                                }
                            }
                                
                        }else{
                            NetworkInformation networkInformation = getNetworksData().getLocationNetworkInformation(msgInterfaceId);
                            if(networkInformation != null && getAuthorizedLocationByNetworkId(networkInformation.getNetworkId()) != null){
                                msgInterface.sendMessage(code, timeStamp, networkId, msg);
                            }
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
            NetworkInformation isInfo = getNetworksData().getLocationNetworkInformation(itemId);
    

            if(getListener(itemId) == null){
                if(isInfo != null){
                    if(getAuthorizedLocationByNetworkId(isInfo.getNetworkId()) != null){

                        super.addMsgListener(item);
                    }else{
                        //TODO: create authorization
                    }
                }else{
                    if(m_ergoWalletDataList.hasAccess(itemId)){
                        super.addMsgListener(item);
                    }
                }   
            }
        }
    }

    @Override
    public boolean removeMsgListener(NoteMsgInterface item){
        if(item != null){
            String itemId = item.getId();
          
            boolean removed = msgListeners().remove(item);
            m_ergoWalletDataList.removeAccessId(itemId);
        
            if(msgListeners().size() == 0){
                stop();
            }
            return removed;
        
        }

        return false;
    }
   
    

    public class ErgoWalletsTab extends AppBox implements TabInterface {

        private boolean m_current = false;


       
        private MenuButton m_networkMenuBtn = null;
        private ImageView m_networkMenuBtnImageView = null;
        private Tooltip m_networkTip = null;

        private MenuButton m_marketMenuBtn = null;
        private ImageView m_marketMenuBtnImageView = null;
        private Tooltip m_marketTip = null;

        private MenuButton m_tokenMarketMenuBtn = null;
        private ImageView m_tokenMarketMenuBtnImageView = null;
        private Tooltip m_tokenMarketTip = null;
        private HBox m_rightSideMenuBar = null;
        private Tooltip m_menuToolTip = new Tooltip();
        private PauseTransition m_menuPauseTransition;

        private Stage m_appStage;
        private WalletLockBox m_lockBox;
    
    
        public ErgoWalletsTab(Stage appStage, SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, Button menuBtn) {
            super();
       
            m_appStage = appStage;
    
            layoutAppMenu();

            addListeners();

            m_lockBox = new WalletLockBox(m_appStage, getSmallAppIcon(), new ErgoWalletControl("ergoWalletsTab",ErgoWallets.NETWORK_ID,ErgoWallets.NETWORK_ID, ErgoNetwork.NETWORK_ID, NETWORK_TYPE, m_locationId, getNetworksData()));
            
            
            getChildren().addAll(m_lockBox);
        }
    

        protected void layoutAppMenu(){
            m_networkMenuBtnImageView = new ImageView(new Image(AppConstants.NETWORK_ICON));
            m_networkMenuBtnImageView.setPreserveRatio(true);
            m_networkMenuBtnImageView.setFitWidth(30);

            m_networkTip = new Tooltip(ErgoNetworkControl.DISABLED_NETWORK_TEXT);
            m_networkTip.setShowDelay(new javafx.util.Duration(50));
            m_networkTip.setFont(Stages.txtFont);

            m_networkMenuBtn = new MenuButton();
            m_networkMenuBtn.setGraphic(m_networkMenuBtnImageView);
            m_networkMenuBtn.setPadding(new Insets(0, 3, 0, 0));


            m_marketMenuBtnImageView = new ImageView(new Image(Stages.UNKNOWN_IMAGE_PATH));
            m_marketMenuBtnImageView.setPreserveRatio(true);
            m_marketMenuBtnImageView.setFitWidth(30);

            m_marketTip = new Tooltip(ErgoMarketControl.DISABLED_MARKET_TEXT);
            m_marketTip.setShowDelay(new javafx.util.Duration(50));
            m_marketTip.setFont(Stages.txtFont);

            m_marketMenuBtn = new MenuButton();
            m_marketMenuBtn.setGraphic(m_marketMenuBtnImageView);
            m_marketMenuBtn.setPadding(new Insets(0, 3, 0, 0));

            m_tokenMarketMenuBtnImageView = new ImageView(new Image(Stages.UNKNOWN_IMAGE_PATH));
            m_tokenMarketMenuBtnImageView.setPreserveRatio(true);
            m_tokenMarketMenuBtnImageView.setFitWidth(30);

            m_tokenMarketTip = new Tooltip(ErgoMarketControl.DISABLED_TOKEN_MARKET_TEXT);
            m_tokenMarketTip.setShowDelay(new javafx.util.Duration(50));
            m_tokenMarketTip.setFont(Stages.txtFont);

            m_tokenMarketMenuBtn = new MenuButton();
            m_tokenMarketMenuBtn.setGraphic(m_tokenMarketMenuBtnImageView);
            m_tokenMarketMenuBtn.setPadding(new Insets(0, 3, 0, 0));

            m_rightSideMenuBar = new HBox(m_tokenMarketMenuBtn, m_marketMenuBtn, m_networkMenuBtn);
            m_rightSideMenuBar.setId("rightSideMenuBar");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox menuBar = new HBox(spacer, m_rightSideMenuBar);
            menuBar.setId("menuBar");
            HBox.setHgrow(menuBar, Priority.ALWAYS);

            m_menuPauseTransition = new PauseTransition(javafx.util.Duration.millis(1600));
            m_menuPauseTransition.setOnFinished(ptE -> {
                m_menuToolTip.hide();
            });

            getChildren().add(0, menuBar);

        }

        protected void addListeners(){
            
            m_ergoWalletDataList.getErgoNetworkControl().updateNetworkMenu(m_networkMenuBtn.getItems(), m_networkTip, m_networkMenuBtnImageView);
            m_ergoWalletDataList.getErgoMarketControl().updateMarketMenu(false, m_marketMenuBtn.getItems(), m_marketTip, m_marketMenuBtnImageView);
            m_ergoWalletDataList.getErgoMarketControl().updateMarketMenu(true, m_tokenMarketMenuBtn.getItems(), m_tokenMarketTip, m_tokenMarketMenuBtnImageView);
        
        
            m_ergoWalletDataList.getErgoNetworkControl().networkObjectProperty().addListener((obs,oldval,newval)->m_ergoWalletDataList.getErgoNetworkControl().updateNetworkMenu(m_networkMenuBtn.getItems(), m_networkTip, m_networkMenuBtnImageView));
            m_ergoWalletDataList.getErgoMarketControl().marketObjectProperty().addListener((obs,oldval,newval)->m_ergoWalletDataList.getErgoMarketControl().updateMarketMenu(false, m_marketMenuBtn.getItems(), m_marketTip, m_marketMenuBtnImageView));
            m_ergoWalletDataList.getErgoMarketControl().tokenMarketObjectProperty().addListener((obs,oldval,newval)->m_ergoWalletDataList.getErgoMarketControl().updateMarketMenu(true, m_tokenMarketMenuBtn.getItems(), m_tokenMarketTip, m_tokenMarketMenuBtnImageView));
        
             m_ergoWalletDataList.getErgoMarketControl().appsChecked().addListener((obs,oldVal,newVal)->{
                if(m_marketMenuBtn.isShowing()){
                    m_ergoWalletDataList.getErgoMarketControl().updateMarketMenu(false, m_marketMenuBtn.getItems(), m_marketTip, m_marketMenuBtnImageView);
                }
                if(m_tokenMarketMenuBtn.isShowing()){
                    m_ergoWalletDataList.getErgoMarketControl().updateMarketMenu(true, m_tokenMarketMenuBtn.getItems(), m_tokenMarketTip, m_tokenMarketMenuBtnImageView);
                }
             });
        }

 
    
        public void shutdown() {
            m_lockBox.shutdown();
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
