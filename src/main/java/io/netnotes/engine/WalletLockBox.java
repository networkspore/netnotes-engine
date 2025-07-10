package io.netnotes.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.apps.ergoWallets.ErgoWalletAmountBoxes;
import io.netnotes.engine.apps.ergoWallets.ErgoWalletControl;
import io.netnotes.engine.apps.ergoWallets.RemoveWalletsTab;
import io.netnotes.engine.apps.ergoWallets.SendAssetsTab;
import io.netnotes.engine.apps.ergoWallets.TransactionsTab;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
import javafx.stage.Stage;

public class WalletLockBox extends VBox {
    
    private final ErgoWalletControl m_walletControl;
    private double m_charWidth = Utils.computeTextWidth(Stages.txtFont, " ");

    private String m_unlockString = "[ click to unlock ]";

    private Text m_nameLabel;
    private HBox m_addressFieldBox;
    private Button m_unlockBtn;
    private String m_lockString;
    private HBox m_unlockBtnBox;

    private MenuButton m_openBtn;

    private Label m_label;
    private String m_unlockLabelString = "≬ ";
    private String m_lockLabelString = "⚿ ";
    private Button m_lockBtn;

    private HBox m_topBox;
    private ErgoWalletControl m_ergoWalletControl;

    private MenuButton m_openWalletBtn;

    private SimpleBooleanProperty showWallet = new SimpleBooleanProperty(false);
    private SimpleBooleanProperty showBalance = new SimpleBooleanProperty(false);
    private HBox m_walletFieldBox;
    private VBox m_walletBodyBox;
    private Button m_disableWalletBtn;
    private VBox m_selectedAddressBox;

    private Stage m_appStage;
    private VBox m_mainBox;
    
    private ErgoWalletAmountBoxes m_amountBoxes = null;
    

    public WalletLockBox(Stage appStage, Image icon, ErgoWalletControl ergoWalletControl){
        super();
        setAlignment(Pos.CENTER_LEFT);
        m_appStage = appStage;
        m_walletControl = ergoWalletControl;


       
        m_lockString = "Address ";
        
        m_label = new Label(m_lockLabelString);
        m_label.setId("logoBox");

        m_lockBtn = new Button("☓");
        m_lockBtn.setId("lblBtn");

        m_nameLabel = new Text(m_lockString);
        m_nameLabel.setFont(Stages.txtFont);
        m_nameLabel.setFill(Stages.txtColor);

        m_unlockBtn = new Button(m_unlockString);
        m_unlockBtn.setPadding(new Insets(2,15,2,15));
        m_unlockBtn.setAlignment(Pos.CENTER);

        HBox textBox = new HBox(m_nameLabel);
        textBox.setAlignment(Pos.CENTER_LEFT);
        textBox.setPadding(new Insets(0,0,0,0));

        m_unlockBtnBox = new HBox(m_unlockBtn);
        HBox.setHgrow(m_unlockBtnBox, Priority.ALWAYS);
        m_unlockBtnBox.setAlignment(Pos.CENTER_LEFT);
        m_unlockBtnBox.setId("bodyBox");
      
        m_unlockBtn.prefWidthProperty().bind(m_unlockBtnBox.widthProperty().subtract(1));

        m_openBtn = new MenuButton();
        m_openBtn.setId("arrowMenuButton");
       
        m_addressFieldBox = new HBox(m_openBtn, m_lockBtn);
        HBox.setHgrow(m_addressFieldBox, Priority.ALWAYS);
        m_addressFieldBox.setId("bodyBox");
        m_addressFieldBox.setAlignment(Pos.CENTER_LEFT);
        m_addressFieldBox.setPadding(new Insets(0,0,0,10));

        double adrBtnSizeOffset = 50;

        m_addressFieldBox.widthProperty().addListener((obs,oldval,newval)->{
            double w = newval.doubleValue() - 1 - m_lockBtn.widthProperty().get() ;
            m_openBtn.setPrefWidth(w );
          
            setAddressText(getErgoWalletControl().getCurrentAddress(), w-adrBtnSizeOffset);
            
        });
   
   
        updateUnlocked(ergoWalletControl.isUnlocked());
        

        m_ergoWalletControl.currentAddressProperty().addListener((obs,oldval,newval)->{
            String address = newval;
            updateUnlocked(address != null);

            double w = m_addressFieldBox.widthProperty().get() - 1 - m_lockBtn.widthProperty().get() -adrBtnSizeOffset;
            setAddressText(address, w);

        });
        
        m_topBox = new HBox(m_label, textBox, m_unlockBtnBox);
        HBox.setHgrow(m_topBox,Priority.ALWAYS);
        m_topBox.setAlignment(Pos.CENTER_LEFT);

        
        Button toggleShowBalance = new Button(showBalance.get() ? "⏷" : "⏵");
        toggleShowBalance.setId("caretBtn");
        toggleShowBalance.setOnAction(e -> {
            if (getErgoWalletControl().isUnlocked()) {
                showBalance.set(!showBalance.get());
            } else {
               requestFocus();
            }
        });

        
        Button toggleShowWallets = new Button(showWallet.get()? "⏷"  : "⏵");
        toggleShowWallets.setId("caretBtn");
        toggleShowWallets.setOnAction( e -> {
            if (getErgoWalletControl().getCurrentAddress() != null) {
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

        MenuItem openWalletMenuItem = new MenuItem("⇲   Open Wallet File…       (*.erg)");

        MenuItem newWalletMenuItem = new MenuItem("⇱   New…");

        MenuItem restoreWalletMenuItem = new MenuItem("⟲   Restore…");

        MenuItem removeWalletMenuItem = new MenuItem("🗑   Remove…");

        
    

        walletMenuBtn.getItems().addAll(newWalletMenuItem, openWalletMenuItem, restoreWalletMenuItem, removeWalletMenuItem);


        MenuButton adrMenuBtn = new MenuButton("⋮");

    
        HBox adrMenuBtnBox = new HBox(adrMenuBtn);
        adrMenuBtnBox.setAlignment(Pos.CENTER_LEFT);

        MenuItem copyAdrMenuItem =          new MenuItem("⧉  Copy address to clipbord");
        MenuItem magnifyMenuItem =          new MenuItem("🔍 View Address");
        MenuItem recoverMnemonicMenuItem =  new MenuItem("⥀  View Mnemonic");
        
        adrMenuBtn.getItems().addAll(copyAdrMenuItem, magnifyMenuItem, recoverMnemonicMenuItem);
        

        MenuItem sendMenuItem =             new MenuItem("⮩ Send Assets");
        MenuItem txsMenuItem =              new MenuItem("≔ Transactions");

        sendMenuItem.setOnAction(e->openTab(new SendAssetsTab(m_appStage,  icon, ergoWalletControl)));

        txsMenuItem.setOnAction(e->openTab(new TransactionsTab(m_appStage, icon, ergoWalletControl)));



        Runnable hideMenus = () ->{
            walletMenuBtn.hide();
            adrMenuBtn.hide();
            m_openWalletBtn.hide();            
        };
        
        copyAdrMenuItem.setOnAction(e->{
            hideMenus.run();
            getErgoWalletControl().copyCurrentAddressToClipboard(adrMenuBtn);
        });
        

        magnifyMenuItem.setOnAction(e->{
            hideMenus.run();
            getErgoWalletControl().showAddressStage();
        });

        recoverMnemonicMenuItem.setOnAction(e->{
            hideMenus.run();
            getErgoWalletControl().showWalletMnemonic();
        });
            

        Runnable openWalletFile = () -> {
            hideMenus.run();
            
            getErgoWalletControl().openWallet();
            
        };

        Runnable newWallet = () -> {
            hideMenus.run();
            getErgoWalletControl().createWallet(true);
            
        };

        Runnable restoreWallet = () -> {
            hideMenus.run();
            getErgoWalletControl().createWallet(false);
        };
        
    

        openWalletMenuItem.setOnAction(e -> openWalletFile.run());
        newWalletMenuItem.setOnAction(e -> newWallet.run());
        restoreWalletMenuItem.setOnAction(e -> restoreWallet.run());
        removeWalletMenuItem.setOnAction(e -> openTab(new RemoveWalletsTab(icon, ergoWalletControl)));



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

        
    

        MenuItem openWalletItem = new MenuItem("[ Open File ]");
        openWalletItem.setOnAction(e -> openWalletFile.run());

        MenuItem newWalletItem = new MenuItem("[ New ]");
        newWalletItem.setOnAction(e -> newWalletItem.fire());

        MenuItem restoreWalletItem = new MenuItem("[ Restore ]                ");
        restoreWalletItem.setOnAction(e -> restoreWalletItem.fire());

        MenuItem removeWalletItem = new MenuItem("[ Remove ]");
        removeWalletItem.setOnAction(e -> removeWalletMenuItem.fire());

    

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

        ImageView walletIconView = new ImageView(icon);

        walletIconView.setPreserveRatio(true);
        walletIconView.setFitWidth(18);

        HBox topIconBox = new HBox(walletIconView);
        topIconBox.setAlignment(Pos.CENTER_LEFT);
        topIconBox.setMinWidth(30);
        

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
        
        m_openBtn.setOnShowing((e) -> {
            updateAddressMenu();
        
        });



        HBox adrBtnBoxes = new HBox();
        adrBtnBoxes.setAlignment(Pos.CENTER_LEFT);
        adrBtnBoxes.setPadding(new Insets(0,0,0,2));

        Tooltip copyToolTip = new Tooltip("Copy Address To Clipboard");
        copyToolTip.setShowDelay(Duration.millis(100));

        Button copyBtn = new Button("⧉");
        copyBtn.setId("lblBtn");
        copyBtn.setTooltip(copyToolTip);
        copyBtn.setOnAction(e->{
            m_ergoWalletControl.copyCurrentAddressToClipboard(copyBtn);
        });

        Tooltip magnifyTip = new Tooltip("🔍 View Address");
        magnifyTip.setShowDelay(Duration.millis(100));
        Button magnifyBtn = new Button("🔍");
        magnifyBtn.setId("lblBtn");
        magnifyBtn.setTooltip(magnifyTip);
        magnifyBtn.setOnAction(e->{
            m_ergoWalletControl.showAddressStage();
        });
        
        Tooltip mnemonicToolTip = new Tooltip("⥀ View Mnemonic");
        mnemonicToolTip.setShowDelay(Duration.millis(100));
        Button mnemonicBtn = new Button("⥀");
        mnemonicBtn.setId("lblBtn");
        mnemonicBtn.setTooltip(mnemonicToolTip);
        mnemonicBtn.setOnAction(e->{
            hideMenus.run();
            m_ergoWalletControl.showWalletMnemonic();
        });

        HBox addressCtlBtns = new HBox(magnifyBtn, copyBtn, mnemonicBtn);


        HBox paddingBtnBox = new HBox();
        paddingBtnBox.setAlignment(Pos.CENTER_LEFT);
        paddingBtnBox.setPadding(new Insets(0,0,0,2));
        
        HBox addressBtnsBox = new HBox(toggleShowBalance, m_topBox, paddingBtnBox, adrBtnBoxes);
        addressBtnsBox.setPadding(new Insets(2, 0, 2, 0));
        addressBtnsBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(addressBtnsBox, Priority.ALWAYS);


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
            sendMenuItem.fire();
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
            txsMenuItem.fire();
        });

        Region adrCtlBtnSpacer2 = new Region();
        adrCtlBtnSpacer2.setMinWidth(2);
        adrCtlBtnSpacer2.setMinHeight(Stages.MENU_BAR_IMAGE_WIDTH);
        adrCtlBtnSpacer2.setId("vGradient");

        HBox adrCtlBtnSpacerBox2 = new HBox(adrCtlBtnSpacer2);
        adrCtlBtnSpacerBox2.setPadding(new Insets(0,5,0,10));


        HBox addressCtlExtBtnBox = new HBox(sendBtn,adrCtlBtnSpacerBox1, txBtn, adrCtlBtnSpacerBox2);
        HBox.setHgrow(addressCtlExtBtnBox, Priority.ALWAYS);
        
        HBox addressCtrlBtnBox = new HBox( addressCtlExtBtnBox);

        HBox addressControlBox = new HBox(addressCtrlBtnBox);
        HBox.setHgrow(addressControlBox, Priority.ALWAYS);
        addressControlBox.setPadding(new Insets(0,10,0,0));
        
        m_amountBoxes = new ErgoWalletAmountBoxes(true, m_walletControl.getNetworkType(), m_walletControl.balanceProperty());

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

        setOnLockBtn((onLock)->m_walletControl.disconnectWallet());

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
    
        setPasswordAction(e -> {
            m_walletControl.connectToWallet();
        });
  

    }

    private void openTab(ContentTab tab){

    }

    public void updateAddressMenu(){
        long timeStamp = System.currentTimeMillis();
        if (m_ergoWalletControl.getCurrentAddress() != null) {
                    
            JsonArray jsonArray = m_ergoWalletControl.addressesArrayProperty().get();
            if(jsonArray != null){
                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonElement element = jsonArray.get(i);
                    if (element != null && element.isJsonObject()) {

                        JsonObject jsonObj = element.getAsJsonObject();
                        String address = jsonObj.get("address").getAsString();
                        String name = jsonObj.get("name").getAsString();

                        KeyMenuItem currentAddressItem = KeyMenuItem.getKeyMenuItem(m_openBtn.getItems(), address);

                        if(currentAddressItem != null){
                            currentAddressItem.setValue(name, timeStamp);
                        }else{
                            KeyMenuItem addressMenuItem = new KeyMenuItem(address, name, timeStamp, KeyMenuItem.VALUE_AND_KEY);
                            addressMenuItem.setOnAction(e1 -> {
                                m_ergoWalletControl.setCurrentAddress(address);
                            });

                            m_openBtn.getItems().add(addressMenuItem);
                        }
                    }
                }

            }else{

            }
        }

        Utils.removeOldKeys(m_openBtn.getItems(), timeStamp);
    }

    private void setAddressText(String address, double w){
        if(address!=null){
            m_openBtn.setText(Utils.formatAddressString(address, w, m_charWidth));
        }else{
            m_openBtn.setText( m_lockString);
        }
    }

    public ErgoWalletControl getErgoWalletControl(){
        return m_ergoWalletControl;
    }
    
 

    private void updateUnlocked(boolean unlocked){
        if(unlocked){
          
            m_label.setText(m_unlockLabelString);
        
            if(getChildren().contains(m_unlockBtnBox)){
                getChildren().remove(m_unlockBtnBox);
            }
            
            if(!getChildren().contains(m_addressFieldBox)){
                getChildren().add(m_addressFieldBox);
            }
        }else{
            m_label.setText(m_lockLabelString);

            if(!getChildren().contains(m_unlockBtnBox)){
                getChildren().add(m_unlockBtnBox);
            }

            if(getChildren().contains(m_addressFieldBox)){
                getChildren().remove(m_addressFieldBox);
            }

            m_nameLabel.setText(m_lockString);
        }
    } 

    public void setOnLockBtn(EventHandler<ActionEvent> onLockBtn){
        m_lockBtn.setOnAction(onLockBtn);
    }


    public void setPasswordAction( EventHandler<ActionEvent> onAction){
        m_unlockBtn.setOnAction(onAction);
    }




    public StringProperty textProperty(){
        return m_nameLabel.textProperty();
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


    public void shutdown(){
        
        if(m_amountBoxes != null){
            m_amountBoxes.shutdown();
        }

        m_ergoWalletControl.shutdown();
    }


}
