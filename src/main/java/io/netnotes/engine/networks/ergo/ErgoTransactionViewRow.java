package io.netnotes.engine.networks.ergo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.netnotes.engine.HostServicesInterface;
import io.netnotes.engine.JsonParametersBox;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.Stages;
import io.netnotes.engine.Utils;
import io.netnotes.engine.apps.AppConstants;
import io.netnotes.engine.networks.ergo.ErgoTransactionPartner.PartnerType;
import io.netnotes.engine.networks.ergo.ErgoTransactionView.TransactionStatus;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.collections.ObservableList;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public class ErgoTransactionViewRow extends VBox{
    private final ErgoTransactionView m_txView;
    private final SimpleBooleanProperty m_showSubMenuProperty = new SimpleBooleanProperty(false);

    private SimpleDoubleProperty m_colWidth;
    private SimpleStringProperty m_txTitleProperty = new SimpleStringProperty();
    private JsonParametersBox m_txViewBox = null;
    private HBox m_reclaimBox = null;
    private HBox m_reclaimingBox = null;
    
    private ErgoWalletControl m_walletControl;
    private VBox m_bodyBox = null;
    
    private  VBox m_layoutBox;
    
    private Tooltip m_refundToolTip = null;
    private MenuButton m_reclaimBtn = null;
    private ObservableList<String> m_reclaimableBoxIdList = FXCollections.observableArrayList();
    private HashMap<String, Future<?>> m_isBoxFuture = new HashMap<>();
    private JsonParametersBox m_refundParmsBox = null;
    private SimpleObjectProperty<JsonObject> m_refundObject = new SimpleObjectProperty<>(); 
    private VBox m_refundVBox = null;

    public ErgoTransactionViewRow(String parentAddress, JsonObject txViewJson, SimpleDoubleProperty colWidth,Stage appStage, ErgoWalletControl walletControl){
        super();
        m_txView = new ErgoTransactionView(parentAddress, txViewJson, true);
        m_colWidth = colWidth;
        m_walletControl = walletControl;
        
        Button toggleShowSubMenuBtn = new Button(m_showSubMenuProperty.get() ? "⏷" : "⏵");
        toggleShowSubMenuBtn.setId("caretBtn");
        toggleShowSubMenuBtn.setMinWidth(25);
        toggleShowSubMenuBtn.setOnAction(e->m_showSubMenuProperty.set(!m_showSubMenuProperty.get()));
        
        Label txTypeLbl = new Label(getTypeString());
        HBox.setHgrow(txTypeLbl,Priority.ALWAYS);
        txTypeLbl.setMinWidth(105);
        txTypeLbl.setMaxWidth(105);
        txTypeLbl.setId("logoBox");

        Label txExtLbl = new Label(getTypeExtString());
        txExtLbl.setMinWidth(60);
        txExtLbl.setMaxWidth(60);
        txExtLbl.setId("logoBox");
        TextField txTitleField = new TextField();
        txTitleField.setEditable(false);
        txTitleField.setAlignment(Pos.CENTER_LEFT);


        HBox txTitleFieldbox = new HBox(txTitleField);
        HBox.setHgrow(txTitleFieldbox, Priority.ALWAYS);
        txTitleFieldbox.setAlignment(Pos.CENTER_RIGHT);
        txTitleFieldbox.setId("bodyBox");


        MenuButton txMenu = new MenuButton("⋮");

        HBox txMenuBox = new HBox(txMenu);
        txMenuBox.setMinWidth(30);
        txMenuBox.setAlignment(Pos.CENTER);

       
        TextField txIdField = new TextField(m_txView.getId());
        txIdField.setEditable(false);
        txIdField.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(txIdField, Priority.ALWAYS);

        Label txIdLbl = new Label("Tx Id");
        HBox.setHgrow(txIdLbl,Priority.ALWAYS);
        txIdLbl.setMinWidth(00);
        txIdLbl.setMaxWidth(60);
        txIdLbl.setId("logoBox");

        HBox txIdFieldbox = new HBox(txIdField);
        HBox.setHgrow(txIdFieldbox, Priority.ALWAYS);
        txIdFieldbox.setAlignment(Pos.CENTER_RIGHT);
        txIdFieldbox.setId("bodyBox");

        HBox topRow1 = new HBox(toggleShowSubMenuBtn,txIdLbl, txIdFieldbox, txMenuBox);
        HBox.setHgrow(topRow1, Priority.ALWAYS);
        topRow1.setPadding(new Insets(2, 0,2,0));

        m_reclaimBox = new HBox();
        m_reclaimBox.setMinWidth(30);
        m_reclaimBox.setAlignment(Pos.CENTER);

        m_reclaimingBox = new HBox();
        m_reclaimingBox.setAlignment(Pos.CENTER);

        HBox topRow2 = new HBox(txExtLbl, txTitleFieldbox, m_reclaimBox, m_reclaimingBox);
        HBox.setHgrow(topRow2,Priority.ALWAYS);
        topRow2.setAlignment(Pos.CENTER_LEFT);
        topRow2.setPadding(new Insets(5, 0, 2, 25));

        VBox topBox = new VBox(topRow1, topRow2);
     

        String txUrl = m_txView.getTxLink();
        if(txUrl != null){

            MenuItem openWebsiteUrl = new MenuItem("🢅  Open Explorer URL in Browser…");
            openWebsiteUrl.setOnAction(e->{
                getHostServices().showDocument(txUrl);
            });
            txMenu.getItems().add(openWebsiteUrl);
        }

        String apiUrl = m_txView.getApiLink();
        if(apiUrl != null){

            MenuItem openApiUrl = new MenuItem("🢅  Open JSON URL in Browser…");
            openApiUrl.setOnAction(e->{
                getHostServices().showDocument(apiUrl);
            });
            txMenu.getItems().add(openApiUrl);
            ExtensionFilter jsonFilter = new FileChooser.ExtensionFilter("JSON (application/json)", "*.json");
                   
            MenuItem saveApiUrl = new MenuItem("🢃  Download JSON…");
            saveApiUrl.setOnAction(e->{
                Image walletImage = new Image(ErgoConstants.ERGO_WALLETS_ICON);
                FileChooser saveChooser = new FileChooser();
                saveChooser.setTitle("🢃  Download (*.json)");
                saveChooser.getExtensionFilters().addAll(jsonFilter);
                saveChooser.setSelectedExtensionFilter(jsonFilter);
                saveChooser.setInitialFileName(m_txView.getId() + ".json");
                File saveFile = saveChooser.showSaveDialog(appStage);
                if(saveFile != null){
                    Utils.getUrlFile(apiUrl, walletImage, "Downloading…", saveFile, getExecService(), onComplete->{
                        Button closeBtn = new Button();
                        Stage stage = Stages.getFileLocationStage(saveFile.getName(), walletImage,closeBtn, "Transaction JSON", saveFile);
                        stage.show();
                        closeBtn.setOnAction(action->{
                            stage.close();
                        });
                    }, onFailed->{
                        Throwable throwable = onFailed.getSource().getException();
                        String msg = throwable != null ? throwable.getMessage() : "Download failed";
                        Alert alert = new Alert(AlertType.NONE,  msg, ButtonType.OK);
                        alert.setTitle("Error");
                        alert.setHeaderText("Error");
                        alert.initOwner(appStage);
                        alert.show();
                    });
                }
            });
            txMenu.getItems().add(saveApiUrl);
        }


        HBox.setHgrow(topBox, Priority.ALWAYS);
        topBox.setAlignment(Pos.CENTER_LEFT);
        topBox.setPadding(new Insets(0,0,1,0));
     
        //////Body
        /// 

        m_layoutBox = new VBox(topBox);
        HBox.setHgrow(m_layoutBox, Priority.ALWAYS);
        m_layoutBox.setPrefHeight(18);
        m_layoutBox.setPadding(new Insets(1,0,1,0));

        m_txTitleProperty.addListener((obs,oldval,newVal)->{
            if(newVal == null){
                txTitleField.setText("Info Unavailable");
            }else{
                double titleWidth = Utils.computeTextWidth(Stages.txtFont, newVal);
                txTitleField.setPrefWidth(titleWidth + 20);
                txTitleField.setText(newVal);
            }
        });

        update();
        m_txView.addUpdateListener((obs,oldval,newval)->update());

        if(m_showSubMenuProperty.get()){
            addBodyBoxToLayout();
        }

        m_showSubMenuProperty.addListener((obs,oldval,newval)->{
            toggleShowSubMenuBtn.setText(newval ? "⏷" : "⏵");
            if(newval){
                addBodyBoxToLayout();
            }else{
                clearBodyBoxFromLayout();
            }
        });

        m_refundObject.addListener((obs,oldval,newval)->{
            if(newval != null){
                if(m_refundParmsBox == null){
                    addRefundBoxToLayout();
                }else{
                    m_refundParmsBox.update(newval);
                }
            }else{
                if(m_refundParmsBox != null){
                    removeReclaimBox();
                }
            }
        });


        m_reclaimableBoxIdList.addListener((ListChangeListener.Change<? extends String> c)->{
            if(m_reclaimableBoxIdList.size() > 0){
                addReclaimBtn();
            }else{
                removeReclaimBtn();
            }

            if(m_reclaimBtn != null){
                m_reclaimBtn.getItems().clear();
                for(String boxId : m_reclaimableBoxIdList){
                    MenuItem reclaimBoxItem = new MenuItem("↺  Reclaim box: " + boxId);
                 
                    reclaimBoxItem.setOnAction(e->{
                        m_reclaimBtn.hide();
                        reclaimBox(boxId);
                    });
                    m_reclaimBtn.getItems().add(reclaimBoxItem);
                }
            }
        });

        
        getChildren().add(m_layoutBox);
        setPadding(new Insets(0,0,15,0));

        checkReclaimable();
    }





    public void addReclaimBtn(){
        if(m_reclaimBtn == null){
            m_refundToolTip = new Tooltip("Reclaim Assets");
            m_refundToolTip.setShowDelay(Duration.millis(100));

            m_reclaimBtn = new MenuButton("↺");
            m_reclaimBtn.setTooltip(m_refundToolTip);
            m_reclaimBox.getChildren().add(m_reclaimBtn);
        }
    }

    public void removeReclaimBtn(){
        if(m_reclaimBtn != null){
            m_reclaimBtn.setTooltip(null);
            m_reclaimBtn.getItems().clear();
            m_reclaimBox.getChildren().clear();
            m_refundToolTip = null;
            m_reclaimBtn = null;
        }
    }


    public ErgoTxInfo updateReclaimingBox(String boxId){
        ErgoTxInfo txInfo = m_txView.getTxInfo() == null ? new ErgoTxInfo(m_txView.getId(), System.currentTimeMillis(), new ErgoBoxInfo[0]) : m_txView.getTxInfo();
        ErgoBoxInfo boxInfo = txInfo.getBoxInfo(boxId);
        if(boxInfo == null){
            ErgoBoxInfo newBoxInfo = new ErgoBoxInfo(boxId, TransactionStatus.CREATED, TransactionStatus.PENDING, System.currentTimeMillis());
            txInfo.updateBoxInfo(newBoxInfo);
        }else{
            boxInfo.setStatus(TransactionStatus.CREATED);
            boxInfo.setTimeStamp(System.currentTimeMillis());
            boxInfo.setTxId(TransactionStatus.PENDING);
        }

        return txInfo;
    }
    

    public void reclaimBox(String boxId){
        removeReclaimableBox(boxId);
        ErgoTxInfo txInfo = updateReclaimingBox(boxId);
        m_txView.setTxInfo(txInfo);
        removeReclaimableBox(boxId);

        Future<?> future = m_walletControl.reclaimBox(m_txView.getId(), boxId, onSucceeded ->{
            m_isBoxFuture.remove(boxId);
            Object obj =  onSucceeded.getSource().getValue();
            if(obj != null && obj instanceof JsonObject){
                m_txView.setTxInfo(new ErgoTxInfo((JsonObject) obj));
            }
        }, onFailed->{
            m_isBoxFuture.remove(boxId);
            checkReclaimable();
        });

        if(m_isBoxFuture.get(boxId) == null){
            m_isBoxFuture.put(boxId, future);
        }
        
    }

    public void checkReclaimable(){
        String[] boxIds = m_txView.getReclaimableOutBoxIds();
        if(boxIds != null){
            for(String boxId : boxIds){  
                if(m_isBoxFuture.get(boxId) == null && !m_reclaimableBoxIdList.contains(boxId)){
                    Future<?> checkingBox = m_walletControl.getBox(boxId, onSucceeded->{
                        Object obj = onSucceeded.getSource().getValue();
             
                        if(obj != null && obj instanceof JsonObject){
                            ErgoBox box = new ErgoBox((JsonObject) obj);
                            if(box.getSpentTransactionId() == null){
                                addRefundableBox(box.getBoxId());
                            }else{
                                removeReclaimableBox(box.getBoxId());
                                ErgoTxInfo tmpInfo =  m_txView.getTxInfo();
                                ErgoBoxInfo tmpBoxInfo = new ErgoBoxInfo(box.getBoxId(), TransactionStatus.CONFIRMED,box.getSpentTransactionId(), System.currentTimeMillis() );
                                if(tmpInfo == null){
                                    m_txView.setTxInfo(new ErgoTxInfo(m_txView.getId(), System.currentTimeMillis(), new ErgoBoxInfo[]{tmpBoxInfo})); 
                                }else{
                                    ErgoBoxInfo boxInfo = tmpInfo.getBoxInfo(boxId);
                                    boxInfo.setTxId(box.getSpentTransactionId());
                                }
                                markRefunableBox(box.getBoxId(), box.getSpentTransactionId(), onMarked->{
                                    m_isBoxFuture.remove(boxId);
                                    Object object = onMarked.getSource().getValue();
                                    if(object == null && object instanceof JsonObject){
                                        ErgoTxInfo txInfo = new ErgoTxInfo((JsonObject) object);
                                        m_txView.setTxInfo(txInfo);
                                    }
                                }, onFailed->{
                                    
                                    m_isBoxFuture.remove(boxId);
                                    Throwable throwable = onFailed.getSource().getException();
                                    String msg = throwable != null ? throwable.getMessage() : "Mark refundable box failed";
                                    try {
                                        Files.writeString(AppConstants.LOG_FILE.toPath(), "checkCancellable: " + msg, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                                    } catch (IOException e) {
                                    
                                    }
                                    update();
                                });
                            }
                        }else{
                            m_isBoxFuture.remove(boxId);
                        }
                    }, onFailed->{
                        m_isBoxFuture.remove(boxId);
                    });
                    if(checkingBox != null){
                        m_isBoxFuture.put(boxId, checkingBox);
                    }
                }
            }
        }
    }

    public ReadOnlyObjectProperty<JsonObject> refundObject(){
        return m_refundObject;
    }

    public void addRefundableBox(String boxId){
        if(!m_reclaimableBoxIdList.contains(boxId)){
            m_reclaimableBoxIdList.add(boxId);
        }
    }

    public void removeReclaimableBox(String boxId){
        if(m_reclaimableBoxIdList.contains(boxId)){
            m_reclaimableBoxIdList.remove(boxId);
        }
    }

    public void markRefunableBox(String boxId, String spentTransactionId, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        ErgoTxInfo txInfo = m_txView.getTxInfo();
        if(txInfo == null || (txInfo != null && txInfo.getBoxInfoIndex(boxId) == -1)){
            ErgoBoxInfo boxInfo = new ErgoBoxInfo(boxId, TransactionStatus.PENDING, spentTransactionId, System.currentTimeMillis());
           
            m_walletControl.updateBoxInfo(m_txView.getId(), boxInfo, onSucceeded, onFailed);

        }
    }

    private ExecutorService getExecService(){
        return m_walletControl.getErgoNetworkInterface().getNetworksData().getExecService();
    }

    private HostServicesInterface getHostServices(){
        return m_walletControl.getErgoNetworkInterface().getNetworksData().getHostServices();
    }

    private void addBodyBoxToLayout(){
        if(m_bodyBox != null){
            return;
        }
        m_bodyBox = new VBox( );
        HBox.setHgrow(m_bodyBox,Priority.ALWAYS);
        m_bodyBox.setAlignment(Pos.CENTER_LEFT);
        m_bodyBox.setPadding(new Insets(0,25,0,25));
        m_bodyBox.getChildren().add(getTxInfoBox());
        addRefundBoxToLayout();  
        m_layoutBox.getChildren().add(m_bodyBox);
    }


    private void addRefundBoxToLayout(){
        if(m_bodyBox == null){
            return;
        }
        if(m_refundObject.get() == null){
            return;
        }
        m_refundVBox = new VBox();
   
        m_refundParmsBox = new JsonParametersBox(m_refundObject.get(), m_colWidth);

        m_refundVBox.getChildren().add(m_refundParmsBox);
        m_bodyBox.getChildren().add(0, m_refundVBox);
    }

    public void removeReclaimBox(){
        if(m_refundVBox != null){
            m_refundVBox.getChildren().clear();
            m_refundParmsBox.shutdown();
            if(m_bodyBox.getChildren().contains(m_refundVBox)){
                m_bodyBox.getChildren().remove(m_refundVBox);
            }
            m_refundVBox = null;
        }
    }

    private void clearBodyBoxFromLayout(){
        if(m_bodyBox == null){
            return;
        }    

        m_bodyBox.getChildren().clear();
        if(m_txViewBox != null){
            m_txViewBox.shutdown();
            m_txViewBox = null;
        }

        if(m_refundVBox != null){
            m_refundVBox.getChildren().clear();
            if(m_refundParmsBox != null){
                m_refundParmsBox.shutdown();
                m_refundParmsBox = null;    
            }
        }
        m_layoutBox.getChildren().remove(m_bodyBox);
        m_bodyBox = null;
    }


    public JsonParametersBox getTxInfoBox(){
        if(m_txViewBox == null){
            m_txViewBox = new JsonParametersBox(getDetails0bject(), m_colWidth);
        }
        return m_txViewBox;
    }

    public JsonObject getDetails0bject(){
        JsonObject json = new JsonObject();
        json.addProperty("ergo",  m_txView.getParentErgoAmount().getAmountString());
        if(m_txView.getParentTokens().length > 0){
            json.add("tokens", ErgoTransactionView.getTokenJsonArray(m_txView.getParentTokens()));
        }
        JsonObject txViewJson = m_txView.getJsonObject();
        json.add("Info", txViewJson);
        return json;
        
    }

    public String getTypeString(){
        switch(m_txView.getPartnerType()){
            case PartnerType.MINER:
                return "Mined";
            case PartnerType.RECEIVER:
                return "Received";
            case PartnerType.SENDER:
                return "Transmitted";
            default:
                return "Unknown";
        }
    }

    public String getTypeExtString(){
        switch(m_txView.getPartnerType()){
            case PartnerType.MINER:
                return "";
            case PartnerType.RECEIVER:
                return "From";
            case PartnerType.SENDER:
                return "To";
            default:
                return "Unknown";
        }
    }

    public String getTxTitle(){
        switch(m_txView.getPartnerType()){
            case PartnerType.RECEIVER:
                return m_txView.getSender().getAddressString();
            case PartnerType.SENDER:
                return m_txView.getRecipientAddressCommaSeperated();
            default:
                return null;
        }
        
    }


    public ErgoTransactionView getTransactionView(){
        return m_txView;
    }

    public void update(){
        m_txTitleProperty.set(getTxTitle());
        ErgoTxInfo txInfo = m_txView.getTxInfo();
        if(txInfo != null){
            if(m_refundParmsBox == null){
                addRefundBoxToLayout();
            }
            ErgoBoxInfo[] infoArray = txInfo.getBoxInfoArray();
            if(infoArray.length > 0){
                JsonObject listObject = new JsonObject();
                JsonArray jsonArray = new JsonArray();
                for(ErgoBoxInfo boxInfo : infoArray){
                    if(!boxInfo.getStatus().equals(TransactionStatus.CONFIRMED) && ((System.currentTimeMillis() - boxInfo.getTimeStamp()) > 5 * 60 * 1000)){
                        addRefundableBox(boxInfo.getBoxId());
                    }else{
                        removeReclaimableBox(boxInfo.getBoxId());
                    }
                    jsonArray.add(boxInfo.getJsonObject());
                }
                listObject.add("boxTxs", jsonArray);
                m_refundObject.set(listObject);
            }
        }else if(m_refundParmsBox != null){
            removeReclaimBox();
        }
    }
}
