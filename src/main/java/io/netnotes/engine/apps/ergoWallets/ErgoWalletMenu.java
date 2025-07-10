package io.netnotes.engine.apps.ergoWallets;


import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.KeyMenu;
import io.netnotes.engine.KeyMenuItem;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.PriceAmount;
import io.netnotes.engine.PriceAmountMenuItem;
import io.netnotes.engine.Stages;
import io.netnotes.engine.Utils;
import io.netnotes.engine.NetworksData.ManageAppsTab;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.scene.control.Menu;
import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;



public class ErgoWalletMenu extends VBox {

    private ErgoWalletControl m_ergoWalletControl;

    private Menu m_walletMenu = null;
    private Menu m_walletAdrMenu = null;
    private Menu m_walletBalanceMenu = null;

    private final String walletUnavailableString = "Wallet App: (not found)";
    private final String selectWalletString = "[ select wallet ]";
    private final String copyString = "[ copy to clipboard ]";
    private final String selectString = "[ select address ]";
    private final String unlockWalletString = "[ unlock wallet ]";

    private SimpleBooleanProperty m_isInvertProperty = new SimpleBooleanProperty(false);
    private SimpleLongProperty m_walletUpdated = new SimpleLongProperty(0L);
    private MenuButton m_networkMenuBtn = null;
    private Tooltip m_networkTip = null;

    private PauseTransition m_noticePt = null;
    private Tooltip m_noticeToolTip = null;
    private ImageView m_noticeImageView = null;
    public ErgoWalletMenu(ErgoWalletControl walletControl){
        super();

        m_ergoWalletControl = walletControl;

        ImageView networkMenuBtnImageView = new ImageView(Stages.globeImage30);
        networkMenuBtnImageView.setPreserveRatio(true);
        networkMenuBtnImageView.setFitWidth(30);

        m_networkMenuBtn = new MenuButton();
        m_networkMenuBtn.setGraphic(networkMenuBtnImageView);
        m_networkMenuBtn.setGraphicTextGap(10);
        m_networkMenuBtn.setPadding(new Insets(0, 3, 0, 0));



        m_networkMenuBtn.setOnShowing(e->{
            update();
        });
        
    
        update();
   
        
        m_networkTip = new Tooltip(walletUnavailableString);
        m_networkTip.setShowDelay(new javafx.util.Duration(50));
        m_networkTip.setFont(Stages.txtFont);

        m_networkMenuBtn.setTooltip(m_networkTip);


    

        getErgoWalletControl().walletsNetworkObjectProperty().addListener((obs,oldval,newval)->{
            if(newval != null){
                
                NoteConstants.getAppIconFromNetworkObject(newval, getExecService(), onImage->{
                    Object imgObj = onImage.getSource().getValue();
                    if(imgObj != null && imgObj instanceof Image){
                        networkMenuBtnImageView.setImage((Image) imgObj);
                    }else{
                        networkMenuBtnImageView.setImage(Stages.unknownImg );
                    }
                }, onImageFailed->{
                    networkMenuBtnImageView.setImage( Stages.unknownImg);
                });

                m_networkTip.setText(NoteConstants.getNameFromNetworkObject(newval));
            }else{
                m_networkTip.setText("Wallet: (none)");
                networkMenuBtnImageView.setImage(Stages.unknownImg);
            }
        });

        String walletName = m_ergoWalletControl.walletObjectProperty().get() != null ? m_ergoWalletControl.getWalletName() : selectWalletString;
                
        m_walletMenu = new Menu(walletName != null ? walletName : selectWalletString);
    
        updateWalletsMenu();

        getErgoWalletControl().walletObjectProperty().addListener((obs,oldval,newval)->{
            updateWalletsMenu();
            updateAddressesMenu();
        });

        getErgoWalletControl().walletsProperty().addListener((obs,oldval,newval)->updateWalletsMenu());

        m_networkMenuBtn.getItems().add(m_walletMenu);

        
        String currentAddress = m_ergoWalletControl.getCurrentAddress();

        m_walletAdrMenu = new Menu(currentAddress != null ? currentAddress : unlockWalletString);

        getErgoWalletControl().currentAddressProperty().addListener((obs,oldval,newval)->updateAddressesMenu());

        getErgoWalletControl().addressesArrayProperty().addListener((obs,oldval,newval)->updateAddressesMenu(newval));        


        m_walletBalanceMenu = new Menu();



        
        m_networkMenuBtn.textProperty().bind(Bindings.createObjectBinding(()->{
            boolean isDisabled = m_ergoWalletControl.disabledProperty().get();
            boolean isUnlocked = getErgoWalletControl().currentAddressProperty().get() != null;
            boolean isWallet = getErgoWalletControl().walletObjectProperty().get() != null;
            return isWallet ? (isUnlocked ?"🔓": "🔒") :  isDisabled ? "⛔"  : "🚫" ;
            //"∅"
        }, getErgoWalletControl().walletObjectProperty(), m_ergoWalletControl.disabledProperty(), getErgoWalletControl().currentAddressProperty()));
        

        getErgoWalletControl().balanceProperty().addListener((obs,oldval,newval)->updateBalanceMenu());


        getChildren().add(m_networkMenuBtn);
        
    }


    private void showNotice(Image img, String msg){
        Scene scene = this.getScene();
        Window window = scene != null ? this.getScene().getWindow() : null;
  
        if(window != null){
            Point2D p = m_networkMenuBtn.localToScene(0.0, 0.0);
            m_networkTip.hide();
            if(m_noticeToolTip == null || m_noticePt == null){
                
                m_noticeImageView = new ImageView();
                m_noticeImageView.setFitWidth(Stages.MENU_BAR_IMAGE_WIDTH);
                m_noticeImageView.setPreserveRatio(true);
                m_noticeImageView.setImage(img);

                m_noticeToolTip = new Tooltip(msg);
                m_noticeToolTip.setGraphic(m_noticeImageView);
                
                m_noticeToolTip.show(
                        m_networkMenuBtn,
                        p.getX() + scene.getX() + scene.getWindow().getX(),
                        (p.getY() + m_networkMenuBtn.getScene().getY() + scene.getWindow().getY())
                                - m_networkMenuBtn.getLayoutBounds().getHeight());
                m_noticePt = new PauseTransition(Duration.millis(5600));
                m_noticePt.play();
            }else{
                m_noticePt.playFromStart();
            }
           

            m_noticePt.setOnFinished(noticePtE -> {
                
                m_noticeToolTip.hide();
                m_noticeToolTip.setGraphic(null);
                m_noticePt = null;
                m_noticeImageView.setImage(null);
                m_noticeImageView = null;
                m_noticeToolTip = null;
            });
           
        }
    }


    public String getLocationId(){
        return getErgoWalletControl().getLocationId();
    }
    
    public ExecutorService getExecService(){
        return getErgoWalletControl().getExecService();
    }

    public ErgoWalletControl getErgoWalletControl(){
        return m_ergoWalletControl;
    }

    public SimpleLongProperty walletUpdatedProperty(){
        return m_walletUpdated;
    }

    public boolean isInvert(){
        return m_isInvertProperty.get();
    }

    public SimpleBooleanProperty isInvertProperty(){
        return m_isInvertProperty;
    }

    public void setInvert(boolean isInvert){
        m_isInvertProperty.set(isInvert);
        updateBalanceMenu();
    }


    public void updateBalanceMenu(){

        JsonObject balanceObject = getErgoWalletControl().balanceProperty().get();


        if(balanceObject != null){
            if(!m_networkMenuBtn.getItems().contains(m_walletBalanceMenu)){
                m_networkMenuBtn.getItems().add(m_walletBalanceMenu);
            }

            ArrayList<PriceAmount> priceAmountList = NoteConstants.getBalanceList(balanceObject,true, getErgoWalletControl().getNetworkType());
            long timeStamp = System.currentTimeMillis();
            if(m_walletBalanceMenu.getItems().size() > 0){
                for(int i = 0; i < priceAmountList.size() ; i++){
                    PriceAmount amount = priceAmountList.get(i);
                    
                    Object keyObject =  Utils.getKeyObject(m_walletBalanceMenu.getItems(), amount.getTokenId());
                    if(keyObject != null && keyObject instanceof PriceAmountMenuItem ){
                        PriceAmountMenuItem item = (PriceAmountMenuItem) keyObject;
                        item.setPriceAmount(amount, timeStamp);
                    }else{
                        m_walletBalanceMenu.getItems().add(new PriceAmountMenuItem(amount, timeStamp));
                    }
                }
                Utils.removeOldKeys(m_walletBalanceMenu.getItems(), timeStamp);
            }else{
                for(PriceAmount amount : priceAmountList){
                    m_walletBalanceMenu.getItems().add(new PriceAmountMenuItem(amount, timeStamp));
                }
                
            }
            m_walletUpdated.set(timeStamp);
         
        }else{
            if(m_networkMenuBtn.getItems().contains(m_walletBalanceMenu)){
                m_networkMenuBtn.getItems().remove(m_walletBalanceMenu);
            }
        }
   
    }



    private void update(){


        m_networkMenuBtn.getItems().clear();
        if(getErgoWalletControl().isWalletsAppAvailable()){
            m_networkMenuBtn.setId("xBtnAvailable"); 
            //Wallet menu
            m_networkMenuBtn.getItems().add(m_walletMenu);
            //Address menu
            if(getErgoWalletControl().getWalletId() != null){
                m_networkMenuBtn.getItems().add(m_walletAdrMenu);
            }
            //Balance menu
            if(getErgoWalletControl().balanceProperty().get() != null){
                m_networkMenuBtn.getItems().add(m_walletBalanceMenu);
            }

            //Manage item
            MenuItem openWalletsItem = new MenuItem("Manage wallets…");
            openWalletsItem.setOnAction(e->{
                m_networkMenuBtn.hide();
                getNetworksData().openApp(getErgoWalletControl().getErgoWalletsId());
            });

            m_networkMenuBtn.getItems().addAll(new SeparatorMenuItem(), openWalletsItem);

        }else{
            showNotice(Stages.globeImage30, walletUnavailableString);
            m_networkMenuBtn.setId("xBtnUnavailable"); 
            MenuItem openManageItem = new MenuItem("Manage apps…");
            openManageItem.setOnAction(e->{
                m_networkMenuBtn.hide();
                getNetworksData().openStatic(ManageAppsTab.NAME);
            });
            openManageItem.setId("regularItem");
            m_networkMenuBtn.getItems().addAll(new SeparatorMenuItem(), openManageItem);
        }

    }

    public Menu getWalletMenu(long timeStamp){
       

        String walletName = m_ergoWalletControl.walletObjectProperty().get() != null ? m_ergoWalletControl.getWalletName() : selectWalletString;
                
        m_walletMenu.setText(walletName != null ? walletName : selectWalletString);
        return m_walletMenu;
    }

    private void updateWalletsMenu(){
        updateWalletsMenu(System.currentTimeMillis());
    }

    private void updateWalletsMenu(long timeStamp){
        Menu walletMenu = m_walletMenu;
        
        boolean disabled = getErgoWalletControl().isDisabled();
        KeyMenuItem disabledItem = KeyMenuItem.getKeyMenuItem(walletMenu.getItems(), NoteConstants.STATUS_DISABLED);
        boolean isDisabledItem = disabledItem != null;
        if(disabled){
            if(!isDisabledItem){
                walletMenu.getItems().add(0, new KeyMenuItem(NoteConstants.STATUS_DISABLED, "(control disabled)", timeStamp, KeyMenuItem.NOT_KEY_VALUE));
            }else{
                disabledItem.setTimeStamp(timeStamp);
            }

        }else{
            if(isDisabledItem){
                KeyMenuItem.removeKeyItem(walletMenu.getItems(),  NoteConstants.STATUS_DISABLED);
            }
        }

        String disableValue = disabled ? "[ enable ]" : "[ disable ]";
        String disableKey =  NoteConstants.CMD + "disable";

        KeyMenuItem existingDisableWalletItem = KeyMenuItem.getKeyMenuItem(walletMenu.getItems(), disableKey);

        if(existingDisableWalletItem == null){

            KeyMenuItem disableWallet = new KeyMenuItem(disableKey, disableValue, timeStamp, KeyMenuItem.NOT_KEY_VALUE);
            disableWallet.setOnAction(e->{
                getErgoWalletControl().setDisabled(!getErgoWalletControl().isDisabled());
            });

            walletMenu.getItems().addAll(disableWallet,  new SeparatorMenuItem());
        }else{
            existingDisableWalletItem.setValue(disableValue, timeStamp);
        }

        JsonArray walletsArray = m_ergoWalletControl.walletsProperty().get();
        String setCurrentKey = "setCurrent";
        if (walletsArray != null && !disabled) {
            String currentWalletId = getErgoWalletControl().getWalletId();

            for (int i = 0; i < walletsArray.size() ; i++) {
                JsonElement element = walletsArray.get(i);
                if (element != null && element instanceof JsonObject) {
                    JsonObject json = element.getAsJsonObject();

                    String name = json.get("name").getAsString();
                    String id = json.get("id").getAsString();

                    boolean isCurrentWallet = currentWalletId != null && currentWalletId.equals(id);

                    String value = isCurrentWallet ? "* +" + name : name;

                    KeyMenu existingItem = KeyMenu.getKeyMenu(walletMenu.getItems(), id);
                    if(existingItem == null){
                        KeyMenu walletItem = new KeyMenu(id, value, timeStamp, KeyMenu.VALUE_NOT_KEY);
                        
                        KeyMenuItem setCurrentItem = new KeyMenuItem(setCurrentKey, isCurrentWallet ? "(selected)" : "(select wallet)", timeStamp, KeyMenu.VALUE_NOT_KEY);
                        setCurrentItem.setOnAction(action -> {
                            getErgoWalletControl().setWalletObject(json);
                            if(!getErgoWalletControl().isConnected()){
                                getErgoWalletControl().connectToWallet();
                            }
                        });
                        walletItem.getItems().add(setCurrentItem);
                        


                        walletMenu.getItems().add(walletItem);
                    }else{

                        existingItem.setValue(value, timeStamp);

                        KeyMenuItem setCurrentItem = KeyMenuItem.getKeyMenuItem(existingItem.getItems(), setCurrentKey);
                        setCurrentItem.setValue( isCurrentWallet ? "(selected)" : "(select wallet)", timeStamp);
                        
                        
                    }
                }
            }
            
        }else{
            String noWalletsKey = NoteConstants.STATUS_UNAVAILABLE + "walletsArray";
            String noWalletsValue = "(no wallets)";

            KeyMenuItem existingNoWalletsItem = KeyMenuItem.getKeyMenuItem(walletMenu.getItems(), noWalletsKey);
            if(existingNoWalletsItem == null){
                KeyMenuItem newNoWalletsItem = new KeyMenuItem(noWalletsKey, noWalletsValue, timeStamp, KeyMenuItem.NOT_KEY_VALUE);
                walletMenu.getItems().add(newNoWalletsItem);
            }else{
                existingNoWalletsItem.setTimeStamp(timeStamp);
            }
        }

        Utils.removeOldKeys(walletMenu.getItems(), timeStamp);
    }

    



    public PriceAmount getBalancePriceAmountByTokenId(String tokenId){
        JsonObject balanceObject = getErgoWalletControl().balanceProperty().get();

        ArrayList<PriceAmount> priceAmountList = NoteConstants.getBalanceList(balanceObject,true, getErgoWalletControl().getNetworkType());
            
            
        return NoteConstants.getPriceAmountFromList(priceAmountList, tokenId);
      
    }


    public NetworksData getNetworksData(){
        return getErgoWalletControl().getNetworksData();
    }
 
    public void shutdown(){
        m_ergoWalletControl.shutdown();
    }

    public void updateAddressesMenu(){
        updateAddressesMenu(m_ergoWalletControl.addressesArrayProperty().get());
    }

    private void updateAddressesMenu(JsonArray jsonArray){  
        long timeStamp = System.currentTimeMillis();
        
        String walletId = m_ergoWalletControl.getWalletId();
        
        if(walletId != null){
          
            if(!m_networkMenuBtn.getItems().contains(m_walletAdrMenu)){
                m_networkMenuBtn.getItems().add(1, m_walletAdrMenu);
            }
        }else{
            if(m_networkMenuBtn.getItems().contains(m_walletAdrMenu)){
                m_networkMenuBtn.getItems().remove(m_walletAdrMenu);
            }
        }

        String currentAddress = m_ergoWalletControl.getCurrentAddress();

        m_walletAdrMenu.setText(currentAddress != null ? currentAddress : "(locked)");

        if(jsonArray != null){
           
            String selectStringKey = NoteConstants.CMD+selectString;
            String copyStringKey = NoteConstants.CMD+copyString;

            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement element = jsonArray.get(i);
                if (element != null && element.isJsonObject()) {
                    JsonObject jsonObj = element.getAsJsonObject();
                    JsonElement addressElement = jsonObj.get("address");
                    JsonElement nameElement =jsonObj.get("name");
                    String address = addressElement != null && !addressElement.isJsonNull() ? addressElement.getAsString() :null;
                    String name =  nameElement != null && !nameElement.isJsonNull() ? nameElement.getAsString() : null;

                    if(address != null && name != null){
                        boolean isCurrentAddress = currentAddress != null && currentAddress.equals(address);
                    
                        name = isCurrentAddress ? name + " *" : name + "  ";

                        KeyMenu currentAddressItem = KeyMenu.getKeyMenu(m_walletAdrMenu.getItems(), address);
                        
                        if(currentAddressItem != null){
                            currentAddressItem.setValue(name, timeStamp);
                        }else{
                            KeyMenu newAddressitem = new KeyMenu(address, name, timeStamp);

                            KeyMenuItem copyAddress = new KeyMenuItem(copyStringKey, copyString, timeStamp, KeyMenuItem.NOT_KEY_VALUE);
                            copyAddress.setOnAction(e->{
                                Clipboard clipboard = Clipboard.getSystemClipboard();
                                ClipboardContent content = new ClipboardContent();
                                content.putString(address);
                                clipboard.setContent(content);
                            });
            

                            KeyMenuItem selectAddress = new KeyMenuItem(selectStringKey, selectString, timeStamp, KeyMenuItem.NOT_KEY_VALUE);
                            selectAddress.setOnAction(e->{
                                getErgoWalletControl().setCurrentAddress(address);
                            });
                            newAddressitem.getItems().addAll(copyAddress, selectAddress);
                        
                            m_walletAdrMenu.getItems().add(newAddressitem);
                        }
                    }
                }
            }
        }else{
            if(getErgoWalletControl().isControl() && walletId != null){
                String openWalletKey = NoteConstants.CMD + unlockWalletString;
                KeyMenuItem unlockWalletItem = KeyMenuItem.getKeyMenuItem(m_walletAdrMenu.getItems(), openWalletKey);
                if(unlockWalletItem != null){
                    unlockWalletItem.setTimeStamp(timeStamp);
                }else{
                    KeyMenuItem newUnlockWalletItem = new KeyMenuItem(openWalletKey, unlockWalletString, timeStamp, KeyMenuItem.NOT_KEY_VALUE);
                    newUnlockWalletItem.setOnAction(e->{
                        if(!getErgoWalletControl().isUnlocked()){
                            getErgoWalletControl().connectToWallet();
                        }
                    });
                    m_walletAdrMenu.getItems().add(newUnlockWalletItem);
                }
            }
        }
        Utils.removeOldKeys(m_walletAdrMenu.getItems(), timeStamp);
    }

    
}
