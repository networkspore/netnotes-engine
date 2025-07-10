package io.netnotes.engine.apps.ergoWallets;

import io.netnotes.engine.ContentTab;

import com.google.gson.JsonObject;

import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.Stages;
import io.netnotes.engine.networks.ergo.ErgoTxViewsBox;
import io.netnotes.friendly_id.FriendlyId;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.layout.Region;
import javafx.beans.value.ChangeListener;

public class TransactionsTab extends ContentTab{
    
    private ErgoTxViewsBox m_txViewBox = null;
    private ChangeListener<JsonObject> m_txBalanceChange = null;
    private ErgoWalletControl m_walletControl;
    private Stage m_appStage;
    
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
    
    public TransactionsTab(Stage appStage, Image icon, ErgoWalletControl walletControl){
        this(appStage, FriendlyId.createFriendlyId(), walletControl.getParentId(), icon, new VBox(), walletControl);
    }

    private TransactionsTab(Stage appStage, String tabId, String parentId,Image icon, VBox layoutVBox, ErgoWalletControl ergoWalletControl) {
        super(tabId, parentId, icon, "Remove Wallets", layoutVBox);
    
        m_appStage = appStage;

        Label headingText = new Label("Transactions");
        headingText.setFont(Stages.txtFont);
        headingText.setPadding(new Insets(0,0,0,15));

        HBox headingBox = new HBox(headingText);
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


        layoutVBox.getChildren().addAll(headerBox, bodyBox);


        getTransactionViews();
        m_txBalanceChange = (obs,oldVal,newVal)->getTransactionViews();
        m_walletControl.balanceProperty().addListener(m_txBalanceChange);
    }


}

