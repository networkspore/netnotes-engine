package io.netnotes.engine.apps.ergoWallet;

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
import io.netnotes.engine.NetworksData.ManageAppsTab;
import io.netnotes.engine.networks.ergo.ErgoConstants;
import io.netnotes.engine.networks.ergo.ErgoCurrency;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.geometry.Insets;
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

import javafx.beans.binding.Bindings;

public class ErgoWalletMenu extends VBox {

    private ErgoWalletControl m_ergoWalletControl;

    
    private final String walletUnavailableString = "Wallet: (unavailable)";

     private final String selectWalletString = "[ select wallet ]";
     private final String copyString = "[ copy to clipboard ]";
     private final String selectString = "[ select address ]";

    private SimpleBooleanProperty m_isInvertProperty = new SimpleBooleanProperty(false);
    private SimpleLongProperty m_walletUpdated = new SimpleLongProperty(0L);



    private MenuButton networkMenuBtn = null;

    private Menu m_walletMenu = new Menu(selectWalletString);
    private Menu m_walletAdrMenu = new Menu("");
    private Menu m_walletBalanceMenu = new Menu("");

    public ErgoWalletMenu(ErgoWalletControl walletControl){
        super();
        ImageView networkMenuBtnImageView = new ImageView(Stages.globeImage30);
        networkMenuBtnImageView.setPreserveRatio(true);
        networkMenuBtnImageView.setFitWidth(30);

        networkMenuBtn = new MenuButton();
        networkMenuBtn.setGraphic(networkMenuBtnImageView);
        networkMenuBtn.setGraphicTextGap(10);
        networkMenuBtn.setPadding(new Insets(0, 3, 0, 0));

        networkMenuBtn.getItems().add(new SeparatorMenuItem());
        
        networkMenuBtn.showingProperty().addListener((obs,oldval,newval)->{
            if(newval){
                onMenuShowing();
            }
        });
   
        
        Tooltip networkTip = new Tooltip(walletUnavailableString);
        networkTip.setShowDelay(new javafx.util.Duration(50));
        networkTip.setFont(Stages.txtFont);

        networkMenuBtn.setTooltip(networkTip);


    

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

                networkTip.setText(NoteConstants.getNameFromNetworkObject(newval));
            }else{
                networkMenuBtnImageView.setImage(Stages.unknownImg);
            }
        });

        getErgoWalletControl().walletObjectProperty().addListener((obs,oldval,newval)->{
            if(newval != null){
                String walletName = m_ergoWalletControl.getWalletName();
                m_walletMenu.setText(walletName);
            }else{
                m_walletMenu.setText(selectWalletString);
            }
        });
        m_walletMenu.getItems().add(new SeparatorMenuItem());

        m_walletMenu.setText(getErgoWalletControl().walletObjectProperty().get() != null ? getErgoWalletControl().getWalletName() : selectWalletString);
        m_walletMenu.showingProperty().addListener((obs,oldval,newval)->{
            if(newval){
                walletMenuOnShowing();
            }
        });

        

        

        networkMenuBtn.textProperty().bind(Bindings.createObjectBinding(()->{
            boolean isDisabled = m_ergoWalletControl.disabledProperty().get();
            boolean isUnlocked = getErgoWalletControl().currentAddressProperty().get() != null;
            boolean isWallet = getErgoWalletControl().walletObjectProperty().get() != null;
            return isWallet ? (isUnlocked ?"🔓": "🔒") :  isDisabled ? "⛔"  : "🚫" ;
            //"∅"
        }, getErgoWalletControl().walletObjectProperty(), m_ergoWalletControl.disabledProperty(), getErgoWalletControl().currentAddressProperty()));
        
        m_walletBalanceMenu.getItems().add(new SeparatorMenuItem());

        getErgoWalletControl().balanceProperty().addListener((obs,oldval,newval)->{
            if(m_walletBalanceMenu.isShowing()){
                updateBalanceMenu();
            }
        });

        getErgoWalletControl().currentAddressProperty().addListener((obs,oldval,newval)->{
            if(oldval == null){
                m_walletAdrMenu.setOnAction(null);
            }
            if(newval != null){
                m_walletAdrMenu.setText(newval);
            }else{
                m_walletAdrMenu.setText("[ click to unlock ]");
                m_walletAdrMenu.setOnAction(e->{
                    networkMenuBtn.hide();
                    if(getErgoWalletControl().getWalletId() != null){
                        getErgoWalletControl().connectToWallet();
                    }
                });
            }
        });

        getErgoWalletControl().addressesArrayProperty().addListener((obs,oldval,newval)->updateAddressesMenu(newval));        


        getChildren().add(networkMenuBtn);
        
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

    private PriceAmountMenuItem getPriceAmountMenuItem(String tokenId){
        for(int i = 0; i < m_walletBalanceMenu.getItems().size(); i++){
            MenuItem item = m_walletBalanceMenu.getItems().get(i);
            if(item instanceof PriceAmountMenuItem){
                PriceAmountMenuItem priceItem = (PriceAmountMenuItem) item;
                if(priceItem.getPriceAmount().getTokenId().equals(tokenId)){
                    return priceItem;
                }
            }
        }
        return null;
    }


    private PriceAmountMenuItem removePriceAmountMenuItem(String tokenId){
        for(int i = 0; i < m_walletBalanceMenu.getItems().size(); i++){
            MenuItem item = m_walletBalanceMenu.getItems().get(i);
            if(item instanceof PriceAmountMenuItem){
                PriceAmountMenuItem priceItem = (PriceAmountMenuItem) item;
                if(priceItem.getPriceAmount().getTokenId().equals(tokenId)){
                    return (PriceAmountMenuItem) m_walletBalanceMenu.getItems().remove(i);
                }
            }
        }
        return null;
    }


    public void updateBalanceMenu(){

        JsonObject balanceObject = getErgoWalletControl().balanceProperty().get();

        if(balanceObject != null){
            if(m_walletBalanceMenu.getItems().size() == 1 && m_walletBalanceMenu.getItems().get(0) instanceof SeparatorMenuItem){
                m_walletBalanceMenu.getItems().remove(0);
            }
            ArrayList<PriceAmount> priceAmountList = NoteConstants.getBalanceList(balanceObject,true, getErgoWalletControl().getNetworkType());
            long timeStamp = System.currentTimeMillis();
            if(m_walletBalanceMenu.getItems().size() > 0){
                for(int i = 0; i < priceAmountList.size() ; i++){
                    PriceAmount amount = priceAmountList.get(i);
                    
                    PriceAmountMenuItem item = getPriceAmountMenuItem(amount.getTokenId());
                    if(item != null){
                        item.setPriceAmount(amount, timeStamp);
                    }else{
                        m_walletBalanceMenu.getItems().add(new PriceAmountMenuItem(amount, timeStamp));
                    }
                }
                removeOld(timeStamp);
            }else{
                for(PriceAmount amount : priceAmountList){
                    m_walletBalanceMenu.getItems().add(new PriceAmountMenuItem(amount, timeStamp));
                }
                
            }
            m_walletUpdated.set(timeStamp);
         
        }else{
         
            m_walletBalanceMenu.getItems().clear();
            m_walletBalanceMenu.getItems().add(new SeparatorMenuItem());
            m_walletUpdated.set(0);
        }
   
    }

    private void removeOld(long timeStamp){
        ArrayList<String> removeList  = new ArrayList<>();


        for(int i = 0; i < m_walletBalanceMenu.getItems().size(); i++){
            MenuItem item = m_walletBalanceMenu.getItems().get(i);
            if(item instanceof PriceAmountMenuItem){
                PriceAmountMenuItem priceItem = (PriceAmountMenuItem) item;
                if(priceItem.getTimeStamp() < timeStamp){
                    removeList.add(priceItem.getPriceAmount().getTokenId());        
                }
            }
        }

        for(String tokenId : removeList){
            removePriceAmountMenuItem(tokenId);
        }

    }

    private void onMenuShowing(){
        networkMenuBtn.getItems().clear();
        if(getErgoWalletControl().isWalletsAppAvailable()){
            //Wallet menu
            networkMenuBtn.getItems().add(m_walletMenu);
            //Address menu
            if(getErgoWalletControl().getWalletId() != null){
                networkMenuBtn.getItems().add(m_walletAdrMenu);
            }
            //Balance menu
            if(getErgoWalletControl().balanceProperty().get() != null){
                networkMenuBtn.getItems().add(m_walletBalanceMenu);
            }

            //Manage item
            MenuItem openWalletsItem = new MenuItem("Manage wallets…");
            openWalletsItem.setOnAction(e->{
                networkMenuBtn.hide();
                getNetworksData().openApp(getErgoWalletControl().getErgoWalletsId());
            });

            networkMenuBtn.getItems().addAll(new SeparatorMenuItem(), openWalletsItem);
        }else{
            MenuItem openManageItem = new MenuItem("Manage apps…");
            openManageItem.setOnAction(e->{
                networkMenuBtn.hide();
                getNetworksData().openStatic(ManageAppsTab.NAME);
            });

            networkMenuBtn.getItems().addAll(new SeparatorMenuItem(), openManageItem);
        }

    }

    private void walletMenuOnShowing(){
        m_walletMenu.getItems().clear();
        boolean disabled = getErgoWalletControl().isDisabled();
        if(!disabled){
           m_walletMenu.getItems().add(new MenuItem( "Getting wallets..."));
        }else{
            m_walletMenu.getItems().add(new MenuItem( "(wallet disabled)"));
        }
        m_walletMenu.getItems().add(new SeparatorMenuItem());

        MenuItem disableWallet = new MenuItem(disabled ? "[ enable ]" : "[ disable ]");
        disableWallet.setOnAction(e->{
            getErgoWalletControl().setDisabled(!disabled);
        });


        m_walletMenu.getItems().add(disableWallet);


        if(! disabled){
            getErgoWalletControl().getWallets((onWallets)->{

                Object onWalletsObject = onWallets.getSource().getValue();
                m_walletMenu.getItems().remove(0);
                JsonArray walletIds = onWalletsObject != null && onWalletsObject instanceof JsonArray ? (JsonArray) onWalletsObject : null;
                if (walletIds != null) {
                    

                    if (walletIds != null) {
                        for (int i = 0; i < walletIds.size() ; i++) {
                            JsonElement element = walletIds.get(i);
                            if (element != null && element instanceof JsonObject) {
                                JsonObject json = element.getAsJsonObject();

                                String name = json.get("name").getAsString();
                                //String id = json.get("id").getAsString();

                                MenuItem walletItem = new MenuItem(String.format("%-50s", " " + name));

                                walletItem.setOnAction(action -> {
                                    getErgoWalletControl().setWalletObject(json);
                                    getErgoWalletControl().connectToWallet();
                                });

                                m_walletMenu.getItems().add(i, walletItem);
                            }else{
                                m_walletMenu.getItems().add(i, new MenuItem("Error: (cannot read wallet)"));
                            }
                        }
                    }
                }else{
                    m_walletMenu.getItems().add(0, new MenuItem( "(no wallets)"));
                }
            }, onFailed->{
                Throwable throwable = onFailed.getSource().getException();
                String msg = throwable != null ? throwable.getMessage() : "Error";
                m_walletMenu.getItems().remove(0);
                m_walletMenu.getItems().add(0, new MenuItem("Error: " + msg));
            });
        }

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

    public void updateAddressesMenu(JsonArray jsonArray){  
        
        if(jsonArray != null){
            String currentAddress = getErgoWalletControl().getCurrentAddress();
            long timeStamp = System.currentTimeMillis();
            if(m_walletAdrMenu.getItems().size() == 0){
                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonElement element = jsonArray.get(i);
                    if (element != null && element.isJsonObject()) {
                        JsonObject jsonObj = element.getAsJsonObject();
                        String address = jsonObj.get("address").getAsString();
                        String name = jsonObj.get("name").getAsString();
                        boolean isCurrentAddress = currentAddress != null && currentAddress.equals(address);
                        name = isCurrentAddress ? name + " *" : name + "  ";
                        KeyMenu item = new KeyMenu(address, name, timeStamp);
                        KeyMenuItem copyAddress = new KeyMenuItem(copyString, "", timeStamp);
                        copyAddress.setOnAction(e->{
                            Clipboard clipboard = Clipboard.getSystemClipboard();
                            ClipboardContent content = new ClipboardContent();
                            content.putString(address);
                            clipboard.setContent(content);
                        });
                        item.getItems().add(copyAddress);
                        if(!isCurrentAddress){
                            KeyMenuItem selectAddress = new KeyMenuItem(selectString, "", timeStamp);
                            selectAddress.setOnAction(e->{
                                getErgoWalletControl().setCurrentAddress(address);
                            });
                            item.getItems().add(selectAddress);
                        }
                        m_walletAdrMenu.getItems().add(item);
                    }
                }
            }else{  
                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonElement element = jsonArray.get(i);
                    if (element != null && element.isJsonObject()) {
                        JsonObject jsonObj = element.getAsJsonObject();
                        String address = jsonObj.get("address").getAsString();
                        String name = jsonObj.get("name").getAsString();
                        boolean isCurrentAddress = currentAddress != null && currentAddress.equals(address);
                        name = isCurrentAddress ? name + " *" : name + "  ";
                        

                        KeyMenu existingItem =  KeyMenu.getKeyMenu(m_walletAdrMenu.getItems(), address);
                        
                        if(existingItem != null){
                            existingItem.setValue(name, timeStamp);
                            if(isCurrentAddress){
                                KeyMenuItem.removeKeyItem(existingItem.getItems(), selectString);
                            }else{
                                if(KeyMenuItem.getKeyMenuItem(existingItem.getItems(), selectString) == null){
                                    KeyMenuItem selectAddress = new KeyMenuItem(selectString, "", timeStamp);
                                    selectAddress.setOnAction(e->{
                                        getErgoWalletControl().setCurrentAddress(address);
                                    });
                                    existingItem.getItems().add(selectAddress);
                                }
                            }
                        }else{
                            KeyMenu item = new KeyMenu(address, name, timeStamp);
                            KeyMenuItem copyAddress = new KeyMenuItem(copyString, "", timeStamp);
                            copyAddress.setOnAction(e->{
                                Clipboard clipboard = Clipboard.getSystemClipboard();
                                ClipboardContent content = new ClipboardContent();
                                content.putString(address);
                                clipboard.setContent(content);
                            });
                            item.getItems().add(copyAddress);
                            if(!isCurrentAddress){
                                KeyMenuItem selectAddress = new KeyMenuItem(selectString, "", timeStamp);
                                selectAddress.setOnAction(e->{
                                    getErgoWalletControl().setCurrentAddress(address);
                                });
                                item.getItems().add(selectAddress);
                            }
                            m_walletAdrMenu.getItems().add(item);
                        }
                    }
                }
                

                KeyMenu.removeeOldKeyMenus(m_walletAdrMenu.getItems(), timeStamp);

            }
        }else{
            m_walletAdrMenu.getItems().clear();

        }
        
    }

    
}
