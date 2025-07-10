package io.netnotes.engine.apps.ergoSwap;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.ergoplatform.appkit.NetworkType;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.AppBox;
import io.netnotes.engine.ErgoMarketControl;
import io.netnotes.engine.JsonParametersBox;
import io.netnotes.engine.Network;
import io.netnotes.engine.NetworksData;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.PriceAmount;
import io.netnotes.engine.PriceQuote;
import io.netnotes.engine.Stages;
import io.netnotes.engine.TabInterface;
import io.netnotes.engine.Utils;
import io.netnotes.engine.apps.ergoWallets.ErgoWalletControl;
import io.netnotes.engine.apps.ergoWallets.ErgoWallets;
import io.netnotes.engine.apps.ergoWallets.ErgoWallets.ErgoWalletsTab;
import io.netnotes.engine.networks.ergo.ErgoConstants;
import io.netnotes.engine.networks.ergo.ErgoCurrency;
import io.netnotes.engine.networks.ergo.ErgoNetwork;
import io.netnotes.engine.networks.ergo.ErgoTokenInfo;
import io.netnotes.engine.networks.ergo.PriceQuoteRow;
import io.netnotes.engine.networks.ergo.PriceQuoteScroll;

import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.event.ActionEvent;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.control.Label;

public class ErgoAtomicSwap extends Network{
    public final static String NETWORK_ID = "ERGO_SWAP";
    public final static String DESCRIPTION = "Swap Ergo for tokens on the Ergo blockchain.";
    public final static String NAME = "Ergo Swap";
    public final static String ICON = "/assets/ergo-wallet-30.png";
  
    
    public static final BigDecimal MIN_SWAP_FEE = ErgoConstants.MIN_NETWORK_FEE.multiply(BigDecimal.valueOf(3));

    private final static int MATH_SCALE = 31;

    private final SimpleBooleanProperty m_showQuoteInfoProperty = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty m_showTokenInfoProperty = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty m_isBuyToken = new SimpleBooleanProperty(true);
    private final SimpleObjectProperty<PriceQuote> m_tokenQuoteInErg = new SimpleObjectProperty<>(null);

    private final SimpleObjectProperty<BigDecimal> m_swapFeeProperty = new SimpleObjectProperty<>(MIN_SWAP_FEE);
    private final SimpleObjectProperty<BigDecimal> m_networkFeeProperty = new SimpleObjectProperty<>(ErgoConstants.MIN_NETWORK_FEE);
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
    private final String m_locationId;
    private final NetworksData m_networksData;
    
    private TabInterface m_tabInterface = null;
    private ErgoWalletControl m_walletControl = null;
    private ErgoMarketControl m_ergoMarketControl = null;
        
    public ErgoAtomicSwap(String locationId, NetworksData networksData){
        super(new Image(ICON), NAME, NETWORK_ID, networksData);
        m_locationId = locationId;
        m_networksData = networksData;

    }

    private ErgoMarketControl getErgoMarketControl(){
        return m_ergoMarketControl;
    }
 

    @Override
    public TabInterface getTab(Stage appStage,  SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, Button menuBtn){
        if(m_tabInterface == null){
            m_tabInterface = new ErgoSwapTab(appStage, heightObject, widthObject, menuBtn);
        }
        return m_tabInterface;
    }

    
    public class ErgoSwapTab extends AppBox implements TabInterface{
        
        private JsonParametersBox m_txDetailParamsBox;

        public ErgoSwapTab(Stage appStage, SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, Button menuBtn){
            Label headingText = new Label("Swap");
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
                
                        BigDecimal defaultFee = ErgoConstants.MIN_NETWORK_FEE;
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
                    BigDecimal maxAmount = ergoBalance.subtract(m_networkFeeProperty.get()).subtract(m_swapFeeProperty.get()).subtract(ErgoConstants.MIN_NETWORK_FEE);
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

        @Override
        public String getName() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'getName'");
        }

        @Override
        public void setStatus(String status) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'setStatus'");
        }

        @Override
        public String getStatus() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'getStatus'");
        }

        @Override
        public SimpleStringProperty titleProperty() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'titleProperty'");
        }
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
            ArrayList<PriceAmount> amountList = NoteConstants.getBalanceList(balanceObject, true, m_walletControl.getNetworkType());

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
            ArrayList<PriceAmount> balanceList = NoteConstants.getBalanceList(json, true, m_walletControl.getNetworkType());
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


