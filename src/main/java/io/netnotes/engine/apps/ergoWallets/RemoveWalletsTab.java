package io.netnotes.engine.apps.ergoWallets;

import io.netnotes.engine.ContentTab;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.Stages;
import io.netnotes.engine.Utils;
import io.netnotes.friendly_id.FriendlyId;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseEvent;

public class RemoveWalletsTab extends ContentTab {

    private ErgoWalletControl m_walletControl;

    private Button m_nextBtn = new Button("Remove");
    private Tooltip m_errTip = new Tooltip();

    private SimpleObjectProperty<JsonArray> m_walletIds = new SimpleObjectProperty<>(null);


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

    public RemoveWalletsTab(Image icon, ErgoWalletControl walletControl){
        this(FriendlyId.createFriendlyId(), walletControl.getParentId(), icon, new VBox(), walletControl);
    }

    private RemoveWalletsTab(String tabId, String parentId,Image icon, VBox layoutVBox, ErgoWalletControl ergoWalletControl) {
        super(tabId, parentId, icon, "Remove Wallets", layoutVBox);

        m_walletControl = ergoWalletControl;

        Label headingText = new Label("Remove Wallet");
        headingText.setFont(Stages.txtFont);
        headingText.setPadding(new Insets(0,0,0,15));

        HBox headingBox = new HBox(headingText);
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


        layoutVBox.getChildren().addAll(headerBox, bodyBox);

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

   
    }

}

