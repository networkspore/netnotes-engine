package io.netnotes.engine.networks.ergo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netnotes.engine.AppBox;
import io.netnotes.engine.JsonParametersBox;
import io.netnotes.engine.NoteConstants;
import io.netnotes.engine.NoteInterface;
import io.netnotes.engine.Stages;
import io.netnotes.engine.Utils;

import javafx.beans.binding.Binding;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

public class ErgoExplorersAppBox extends AppBox {
    
    private Stage m_appStage;
    private SimpleObjectProperty<AppBox> m_currentBox = new SimpleObjectProperty<>(null);
    private NoteInterface m_ergoNetworkInterface;
    private VBox m_mainBox;
    private SimpleBooleanProperty m_showExplorers = new SimpleBooleanProperty(false);
    private SimpleObjectProperty<JsonObject> m_defaultExplorer = new SimpleObjectProperty<>(null);
    private String m_locationId = null;

  
    private HBox m_explorerFieldBox;
    private Button m_toggleExplorersBtn;
    private VBox m_explorerBodyPaddingBox = null;
    private JsonParametersBox m_explorerParameterBox = null;

    public static final String SEARCH_TxId = "Tx (TxId)";
    public static final String SEARCH_TxsInputTemplateHash = "Txs (Hash)";
    public static final String SEARCH_TxsAdr = "Txs (Address)";
    public static final String SEARCH_TokenIdInfo = "Token (TokenId)";
    public static final String SEARCH_UnspentByTokenId = "Unspent (TokenId)";
    public static final String SEARCH_UnspentByErgoTree = "Unspent (ErgoTree)";
    public static final String SEARCH_UnspentByTemplateHash = "Unspent (Hash)";
    public static final String SEARCH_UnspentByAddress = "Unspent (Address)";
    public static final String SEARCH_BoxesByTokenId = "Boxes (TokenId)";
    public static final String SEARCH_BoxesByErgoTree = "Boxes (ErgoTree)";
    public static final String SEARCH_BoxesByTemplateHash = "Boxes (Hash)";
    public static final String SEARCH_SORT_ASC = "asc";
    public static final String SEARCH_SORT_DSC = "dsc";

    private Text m_searchText = null;
    private TextField m_searchTextField = null;
    private Binding<String> m_searchTextFieldIdBinding = null;
    private ChangeListener<String> m_searchFieldEnterBtnAddListener = null;
    private EventHandler<ActionEvent> m_searchBtnEnterAction;
    private EventHandler<ActionEvent> m_searchFieldEnterAction;
    private EventHandler<ActionEvent> m_clearBtnAction;
    private Button m_searchEnterBtn = null;
    private Button m_searchClearBtn = null;
    private MenuButton m_searchTypeMenuButton = null;
    private ChangeListener<String> m_searchTypeTextListener = null;
    private MenuItem m_txIdMenuItem = null;
    private MenuItem m_txByAddress = null;
    private MenuItem m_txByTemplateHashMenuItem = null;
    private MenuItem m_tokenIdInfoMenuItem = null;
    private MenuItem m_unspentByTokenIdMenuItem = null;
    private MenuItem m_unspentByErgoTreeMenuItem = null;
    private MenuItem m_unspentByHashMenuItem = null;
    private MenuItem m_unspentByAddressMenuItem = null;
    private MenuItem m_boxesByTokenIdMenuItem = null;
    private MenuItem m_boxesByErgoTreeMenuItem = null;
    private MenuItem m_boxesByHashMenuItem = null;
    private HBox m_searchFieldBox = null;
    private HBox m_searchHBox = null;
    private VBox m_searchVBox = null;
    private JsonParametersBox m_searchResultBox = null;
    private Button m_exportBtn = null;
    private ExtensionFilter m_exportSaveFilter = null;
    private HBox m_exportBtnBox = null;
    private Gson m_gson = null;


    private String m_searchSortMethod = SEARCH_SORT_ASC;
    private HBox m_pageHBox = null;
    private HBox m_pagePaddingBox;
    private TextField m_pageOffsetTextField = null;
    private Text m_pageLimitText = null;
    private Text m_pageOffsetText = null;
    private Text m_pageSortText = null;
    private Button m_pageNextBtn = null;
    private Button m_pagePrevBtn = null;
    private Button m_pageSortBtn = null;
    private TextField m_pageLimitField = null;
    private ChangeListener<String> m_pageLimitFieldListener = null;
    private ChangeListener<String> m_pageOffsetFieldListener = null;
    private MenuButton openMenuBtn = null;

    public ErgoExplorersAppBox(Stage appStage, String locationId, NoteInterface ergoNetworkInterface){
        super();
        m_ergoNetworkInterface = ergoNetworkInterface;
        m_appStage = appStage;
        m_locationId = locationId;

        final String selectString = "[select]";
    
        ImageView explorerIconView = new ImageView(new Image( ErgoConstants.ERGO_EXPLORERS_ICON));
        explorerIconView.setPreserveRatio(true);
        explorerIconView.setFitHeight(18);

        HBox topIconBox = new HBox(explorerIconView);
        topIconBox.setAlignment(Pos.CENTER_LEFT);
        topIconBox.setMinWidth(30);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ImageView closeImage = Stages.highlightedImageView(Stages.closeImg);
        closeImage.setFitHeight(20);
        closeImage.setFitWidth(20);
        closeImage.setPreserveRatio(true);

        m_toggleExplorersBtn = new Button(m_showExplorers.get() ? "⏷" : "⏵");
        m_toggleExplorersBtn.setId("caretBtn");
        m_toggleExplorersBtn.setOnAction(e->{
            m_showExplorers.set(!m_showExplorers.get());
        });

        MenuButton explorerMenuBtn = new MenuButton("⋮");


        Text explorerTopLabel = new Text(String.format("%-13s","Explorer "));
        explorerTopLabel.setFont(Stages.txtFont);
        explorerTopLabel.setFill(Stages.txtColor);


        openMenuBtn = new MenuButton();
        openMenuBtn.setId("arrowMenuButton");
        openMenuBtn.setOnShowing(e->updateExplorerMenu());

        m_explorerFieldBox = new HBox(openMenuBtn);
        HBox.setHgrow(m_explorerFieldBox, Priority.ALWAYS);
        m_explorerFieldBox.setAlignment(Pos.CENTER_LEFT);
        m_explorerFieldBox.setId("bodyBox");
        m_explorerFieldBox.setPadding(new Insets(0, 1, 0, 0));
        m_explorerFieldBox.setMaxHeight(18);

        openMenuBtn.prefWidthProperty().bind(m_explorerFieldBox.widthProperty().subtract(1));

        HBox explorerMenuBtnPadding = new HBox(explorerMenuBtn);
        explorerMenuBtnPadding.setPadding(new Insets(0, 0, 0, 5));



        HBox explorerBtnBox = new HBox(m_explorerFieldBox, explorerMenuBtnPadding);
        explorerBtnBox.setPadding(new Insets(2, 2, 0, 5));
        HBox.setHgrow(explorerBtnBox, Priority.ALWAYS);

        m_explorerBodyPaddingBox = new VBox();
        HBox.setHgrow(m_explorerBodyPaddingBox, Priority.ALWAYS);
        m_explorerBodyPaddingBox.setPadding(new Insets(0,10,0,5));


        Binding<String> explorerNameBinding = Bindings.createObjectBinding(()->{
            String name = NoteConstants.getJsonName(m_defaultExplorer.get());

            return name != null ? name : selectString;
        }, m_defaultExplorer);

        openMenuBtn.textProperty().bind(explorerNameBinding);

        HBox explorerLabelBox = new HBox(explorerTopLabel);
        explorerLabelBox.setAlignment(Pos.CENTER_LEFT);


        HBox explorersTopBar = new HBox(m_toggleExplorersBtn, topIconBox, explorerLabelBox, explorerBtnBox);
        explorersTopBar.setAlignment(Pos.CENTER_LEFT);
        explorersTopBar.setPadding(new Insets(2));

        VBox explorerLayoutBox = new VBox(explorersTopBar, m_explorerBodyPaddingBox);
        HBox.setHgrow(explorerLayoutBox, Priority.ALWAYS);

        m_showExplorers.addListener((obs, oldval, newval) -> updateShowExplorers());

        m_defaultExplorer.addListener((obs,oldval,newval)->setExplorerInfo(newval));

        setExplorerInfo(m_defaultExplorer.get());
        updateShowExplorers();

        m_mainBox = new VBox(explorerLayoutBox);
        m_mainBox.setPadding(new Insets(0));
        HBox.setHgrow(m_mainBox, Priority.ALWAYS);

        m_currentBox.addListener((obs, oldval, newval) -> {
            m_mainBox.getChildren().clear();
            if (newval != null) {
                m_mainBox.getChildren().add(newval);
            } else {
                m_mainBox.getChildren().add(explorerLayoutBox);
            }

        });

        updateDefaultExplorer();


        getChildren().addAll(m_mainBox);
        setPadding(new Insets(0,0,5,0));
    }

    public void updateShowExplorers(){

        boolean isShow = m_showExplorers.get();
        m_toggleExplorersBtn.setText(isShow ? "⏷" : "⏵");

        if (isShow) {
            addExplorerBoxes();
        } else {
            removeExplorerBoxes();
        }
    
    }
    


    public void addSearchBox(){
        if(m_searchText == null){
            

            m_searchText = new Text("Search ");
            m_searchText.setFill(Stages.txtColor);
            m_searchText.setFont(Stages.txtFont);
            
            m_searchTextField = new TextField();
            HBox.setHgrow(m_searchTextField, Priority.ALWAYS);
            m_searchTextField.setPromptText("");
            m_searchTextFieldIdBinding = Utils.createFormFieldIdBinding(m_searchTextField);
            m_searchTextField.idProperty().bind(m_searchTextFieldIdBinding);
             
            m_searchTypeMenuButton = new MenuButton(SEARCH_TxId);
            resizeMenuBtn();
            m_searchTypeTextListener = (obs, oldval, newval)->updateSearchType();

            m_searchTypeMenuButton.textProperty().addListener(m_searchTypeTextListener);

            m_txIdMenuItem = new MenuItem("Transaction by Tx Id");
            m_txIdMenuItem.setOnAction(e->{
                m_searchTypeMenuButton.setText(SEARCH_TxId);
                resizeMenuBtn();
                removePageBox();
            });
            
            m_txByAddress = new MenuItem("Transactions by Address");
            m_txByAddress.setOnAction(e->{
                m_searchTypeMenuButton.setText(SEARCH_TxsAdr);
                resizeMenuBtn();
                addPageBox();
            });

            m_txByTemplateHashMenuItem = new MenuItem("Transactions by Input script template hash");
            m_txByTemplateHashMenuItem.setOnAction(e->{
                m_searchTypeMenuButton.setText(SEARCH_TxsInputTemplateHash);
                resizeMenuBtn();
                addPageBox();
            });

            m_tokenIdInfoMenuItem = new MenuItem("Token info by Token Id");
            m_tokenIdInfoMenuItem.setOnAction(e->{
                m_searchTypeMenuButton.setText(SEARCH_TokenIdInfo);
                resizeMenuBtn();
                removePageBox();
            });

            m_unspentByTokenIdMenuItem = new MenuItem("Unspent boxes by Token Id");
            m_unspentByTokenIdMenuItem.setOnAction(e->{
                m_searchTypeMenuButton.setText(SEARCH_UnspentByTokenId);
                addPageBox();
            });

            m_unspentByErgoTreeMenuItem = new MenuItem("Unspent boxes by Ergo Tree");
            m_unspentByErgoTreeMenuItem.setOnAction(e->{
                m_searchTypeMenuButton.setText(SEARCH_UnspentByErgoTree);
                resizeMenuBtn();
                addPageBox();
            });

            m_unspentByHashMenuItem = new MenuItem("Unspent boxes by Template Hash");
            m_unspentByHashMenuItem.setOnAction(e->{
                m_searchTypeMenuButton.setText(SEARCH_UnspentByTemplateHash);
                resizeMenuBtn();
                addPageBox();
            });

            m_unspentByAddressMenuItem = new MenuItem("Unspent boxes by Address");
            m_unspentByAddressMenuItem.setOnAction(e->{
                m_searchTypeMenuButton.setText(SEARCH_UnspentByAddress);
                resizeMenuBtn();
                addPageBox();
            });

            m_boxesByTokenIdMenuItem = new MenuItem("Boxes by Token Id");
            m_boxesByTokenIdMenuItem.setOnAction(e->{
                m_searchTypeMenuButton.setText(SEARCH_BoxesByTokenId);
                addPageBox();
            });

            m_boxesByErgoTreeMenuItem = new MenuItem("Boxes by Ergo Tree");
            m_boxesByErgoTreeMenuItem.setOnAction(e->{
                m_searchTypeMenuButton.setText(SEARCH_BoxesByErgoTree);
                resizeMenuBtn();
                addPageBox();
            });

            m_boxesByHashMenuItem = new MenuItem("Boxes by Template Hash");
            m_boxesByHashMenuItem.setOnAction(e->{
                m_searchTypeMenuButton.setText(SEARCH_BoxesByTemplateHash);
                resizeMenuBtn();
                addPageBox();
            });

            m_searchTypeMenuButton.getItems().addAll(m_txIdMenuItem,m_txByAddress, m_txByTemplateHashMenuItem, m_tokenIdInfoMenuItem, m_unspentByTokenIdMenuItem, m_unspentByErgoTreeMenuItem, m_unspentByHashMenuItem, m_unspentByAddressMenuItem, m_boxesByTokenIdMenuItem, m_boxesByErgoTreeMenuItem, m_boxesByHashMenuItem);

            m_searchFieldBox = new HBox(m_searchTextField);
            m_searchFieldBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(m_searchFieldBox, Priority.ALWAYS);
            m_searchFieldBox.setId("bodyBox");

            m_searchEnterBtn = new Button("↵");
            m_searchEnterBtn.setId("toolBtn");
            m_searchBtnEnterAction = (e)->{
                if(m_searchTextField != null && m_searchTypeMenuButton != null){
                    String searchText = m_searchTextField.getText();
                    switch(m_searchTypeMenuButton.getText()){
                        case SEARCH_TxId:
                            search("getTransactionById", searchText);
                        break;
                        case SEARCH_TxsAdr:
                            searchByPage("getTransactionsByAddress",  searchText);
                        break;
                        case SEARCH_TxsInputTemplateHash:
                            searchByPage("getTransactionsByInputsScriptTemplateHash", searchText);
                        break;
                        case SEARCH_TokenIdInfo:
                            search("getTokenInfo", searchText);
                        break;
                        case SEARCH_UnspentByTokenId:
                            searchByPage("getUnspentByTokenId", searchText);
                        break;
                        case SEARCH_UnspentByErgoTree:
                            searchByPage("getUnspentByErgoTree", searchText);
                        break;
                        case SEARCH_UnspentByTemplateHash:
                            searchByPage("getUnspentByErgoTreeTemplateHash", searchText);
                        break;
                        case SEARCH_UnspentByAddress:
                            searchByPage("getUnspentByAddress", searchText);
                        break;
                        case SEARCH_BoxesByTokenId:
                            searchByPage("getBoxesByTokenId", searchText);
                        break;
                        case SEARCH_BoxesByErgoTree:
                            searchByPage("getBoxesByErgoTree", searchText);
                        break;
                        case SEARCH_BoxesByTemplateHash:
                            searchByPage("getBoxesByErgoTreeTemplateHash", searchText);
                        break;
                        
                    }
                }
            };
            m_searchEnterBtn.setOnAction(m_searchBtnEnterAction);

            m_searchFieldEnterAction = (e)->m_searchEnterBtn.fire();
            m_searchTextField.setOnAction(m_searchFieldEnterAction);

         
           

            m_searchFieldEnterBtnAddListener = Utils.createFieldEnterBtnAddListener(m_searchTextField, m_searchFieldBox, m_searchEnterBtn);
            m_searchTextField.textProperty().addListener(m_searchFieldEnterBtnAddListener);

            m_searchHBox = new HBox( m_searchText, m_searchFieldBox, m_searchTypeMenuButton);
            HBox.setHgrow(m_searchHBox, Priority.ALWAYS);
            m_searchHBox.setAlignment(Pos.CENTER_LEFT);
            m_searchHBox.setPadding(new Insets(10,0,10,0));

            m_searchVBox = new VBox(m_searchHBox);


            m_explorerBodyPaddingBox.getChildren().addAll(m_searchVBox);

            updateSearchType();
        }
    }

    public void resizeMenuBtn(){
        double w = Utils.computeTextWidth(Stages.txtFont, m_searchTypeMenuButton.getText());
        m_searchTypeMenuButton.setPrefWidth(w);
    }

    public void updateSearchType(){
        
            if(m_searchTextField != null && m_searchTypeMenuButton != null){
                switch(m_searchTypeMenuButton.getText()){
                    case SEARCH_TxId:
                        m_searchTextField.setPromptText("TxId");
                    break;
                    case SEARCH_TokenIdInfo:
                        m_searchTextField.setPromptText("TokenId");
                    break;
                    case SEARCH_UnspentByTokenId:
                        m_searchTextField.setPromptText("TokenId");
                    break;
                    case SEARCH_UnspentByErgoTree:
                        m_searchTextField.setPromptText("ErgoTree Hex");
                    break;
                    case SEARCH_UnspentByTemplateHash:
                        m_searchTextField.setPromptText("Template Hash");
                    break;
                }
            }
        
    }

    

    public void addPageBox(){
        if(m_pageHBox == null){
            double txtWidth = Utils.computeTextWidth(Stages.txtFont, "1000") + 20;
        
            m_pageOffsetText = new Text(" Offset");
            m_pageOffsetText.setFont(Stages.txtFont);
            m_pageOffsetText.setFill(Stages.txtColor);

            m_pageOffsetTextField = new TextField();
            m_pageOffsetTextField.setPromptText("0");
            m_pageOffsetTextField.setPrefWidth(txtWidth);
            m_pageOffsetFieldListener = (obs,oldval,newval)->{
                if(m_pageOffsetTextField != null){
                    String number = newval.replaceAll("[^0-9]", "");
                    m_pageOffsetTextField.setText(number);
                }
            };
            m_pageOffsetTextField.setOnAction(e->{
                if(m_searchTextField.getText().length() > 0){
                    m_searchEnterBtn.fire();
                }
            });
            m_pageOffsetTextField.textProperty().addListener(m_pageOffsetFieldListener);
            
            m_pageLimitText = new Text(" Limit");
            m_pageLimitText.setFont(Stages.txtFont);
            m_pageLimitText.setFill(Stages.txtColor);

            m_pageLimitField = new TextField("100");
            m_pageLimitField.setPrefWidth(txtWidth);
            m_pageLimitFieldListener = (obs,oldval,newval)->{
                String number = newval.replaceAll("[^0-9]", "");
                m_pageLimitField.setText(number);
            };
            m_pageLimitField.setOnAction(e->{
                if(m_searchTextField.getText().length() > 0){
                    m_searchEnterBtn.fire();
                }
            });
            m_pageLimitField.textProperty().addListener(m_pageLimitFieldListener);

            m_pageNextBtn = new Button("⮞");
            m_pageNextBtn.setId("toolBtn");
            m_pageNextBtn.setOnAction(e->{
                if(m_pageLimitField != null && m_pageOffsetTextField != null && m_searchEnterBtn != null){
                    int pageSize = Utils.getIntFromField(m_pageLimitField);
                    if(pageSize > 0){
                        int from = (Utils.getIntFromField(m_pageOffsetTextField) + pageSize);

                        m_pageOffsetTextField.setText( from + "");
                        if(m_searchTextField.getText().length() > 0){
                            m_searchEnterBtn.fire();
                        }
                    }
                }
            });
      
            m_pagePrevBtn = new Button("⮜");
            m_pagePrevBtn.setId("toolBtn");
            m_pagePrevBtn.setOnAction(e->{
                if(m_pageLimitField != null && m_pageOffsetTextField != null && m_searchEnterBtn != null){
                    int pageSize = Utils.getIntFromField(m_pageLimitField);
                    if(pageSize > 0){
                        int from = (Utils.getIntFromField(m_pageOffsetTextField) - pageSize);
                        from = from < 0 ? 0 : from;

                        m_pageOffsetTextField.setText( from + "");
                        if(m_searchTextField.getText().length() > 0){
                            m_searchEnterBtn.fire();
                        }
                    }
                }
            });

            m_pageSortText = new Text(" Sort ");
            m_pageSortText.setFont(Stages.txtFont);
            m_pageSortText.setFill(Stages.txtColor);

            m_pageSortBtn = new Button(m_searchSortMethod.equals(SEARCH_SORT_ASC) ? "Asc" : "Dsc");
            m_pageSortBtn.setId("toolBtn");
            m_pageSortBtn.setOnAction(e->{
                if(m_searchSortMethod != null && m_searchEnterBtn != null){
                    m_searchSortMethod = m_searchSortMethod.equals(SEARCH_SORT_ASC) ? SEARCH_SORT_DSC : SEARCH_SORT_ASC;
                    m_pageSortBtn.setText(m_searchSortMethod.equals(SEARCH_SORT_ASC) ? "Asc" : " Dsc");
                  
                    if(m_searchTextField.getText().length() > 0){
                        m_searchEnterBtn.fire();
                    }
                }
            });
            m_pagePaddingBox = new HBox(m_pageOffsetText, m_pageOffsetTextField, m_pageLimitText, m_pageLimitField, m_pageSortText, m_pageSortBtn, m_pagePrevBtn, m_pageNextBtn);
            m_pagePaddingBox.setAlignment(Pos.CENTER);
            m_pagePaddingBox.setId("footerBox");

            m_pageHBox = new HBox(m_pagePaddingBox);
            m_pageHBox.setPadding(new Insets(2,0, 2,0));
            m_pageHBox.setAlignment(Pos.CENTER);
            HBox.setHgrow(m_pageHBox, Priority.ALWAYS);
            m_pageHBox.setPadding(new Insets(5,0,0, 0));
        
            m_explorerBodyPaddingBox.getChildren().add(m_pageHBox);
            
        }

    }
    public void removePageBox(){
        if(m_pageHBox != null){
 
            m_explorerBodyPaddingBox.getChildren().remove(m_pageHBox);
            m_pagePaddingBox.getChildren().clear();
            m_pageHBox.getChildren().clear();
            m_searchSortMethod = SEARCH_SORT_ASC;
            m_pageSortBtn.setOnAction(null);

            m_pageOffsetTextField.textProperty().removeListener(m_pageOffsetFieldListener);
            m_pageNextBtn.setOnAction(null);
            m_pagePrevBtn.setOnAction(null);
            m_pageLimitField.textProperty().removeListener(m_pageLimitFieldListener);

            m_pageLimitFieldListener = null;
            m_pageOffsetFieldListener = null;

            m_pageOffsetText = null;
            m_pageSortText = null;
            m_pageSortBtn = null;
            m_pagePrevBtn = null;
            m_pageNextBtn = null;
            m_pageLimitField = null;
            m_pageOffsetTextField = null;
            m_pageLimitText = null;
            m_pageHBox = null;
        }
    }

    public void removeSearchBox(){

        if(m_searchTextField != null){
            updateSearchResults(null, null);
            removePageBox();
            m_searchTextField.idProperty().unbind();
            m_searchEnterBtn.setOnAction(null);
            m_searchTextField.setOnAction(null);
            m_searchTextField.textProperty().removeListener(m_searchFieldEnterBtnAddListener);
            m_searchTypeMenuButton.textProperty().removeListener(m_searchTypeTextListener);          
            m_searchVBox.getChildren().clear();
            m_searchHBox.getChildren().clear();
            m_searchFieldBox.getChildren().clear();
            
            m_searchTextFieldIdBinding = null;
            m_searchFieldEnterBtnAddListener = null;
            m_searchBtnEnterAction = null;
            m_searchFieldEnterAction = null;
            m_clearBtnAction = null;
            m_searchTypeTextListener = null;
            m_searchText = null;
            m_searchTextField = null;
            m_searchEnterBtn = null;
            m_searchClearBtn = null;
            m_searchTypeMenuButton = null;
            m_txIdMenuItem = null;
            m_tokenIdInfoMenuItem = null;
            m_unspentByTokenIdMenuItem = null;
            m_unspentByErgoTreeMenuItem = null;
            m_unspentByHashMenuItem = null;
            m_searchFieldBox = null;
            m_searchHBox = null;
            m_searchVBox = null;
        }
        
    }


    public void search(String cmd, String value){
        NoteInterface networkInterface = m_ergoNetworkInterface;
        String id = NoteConstants.getJsonId(m_defaultExplorer.get());
        if(networkInterface != null && id != null){
            JsonObject note = NoteConstants.getCmdObject(cmd, ErgoConstants.EXPLORER_NETWORK, m_locationId);
            note.addProperty("value", value);
            note.addProperty("id", id);
            updateSearchResults(null, NoteConstants.getJsonObject("status", "Searching..."));
            networkInterface.sendNote(note, (onSucceeded)->{
                Object sourceObject = onSucceeded.getSource().getValue();
                if(sourceObject != null){
                    updateSearchResults(cmd, (JsonObject) sourceObject);
                }else{
                    updateSearchResults(null, NoteConstants.getJsonObject("error", "Received invalid result"));
                }
            }, (onFailed)->{
                Throwable throwable = onFailed.getSource().getException();
                updateSearchResults(null, NoteConstants.getJsonObject("error", throwable != null ? throwable.getMessage() : "Unknwon error"));
            });
        }else{
            updateSearchResults(null,NoteConstants.getJsonObject("error", "Explorer disabled"));
        }
    }

    public void searchByPage(String cmd, String value){
        NoteInterface networkInterface = m_ergoNetworkInterface;
        String id = NoteConstants.getJsonId(m_defaultExplorer.get());

        if(networkInterface != null && m_pageOffsetTextField != null && id != null){
            int offset = m_pageOffsetTextField.getText() != "" ? Utils.getIntFromField(m_pageOffsetTextField) : -1;
      
            String sortMethod = m_searchSortMethod;
            
            int limit = m_pageLimitField.getText() != "" ? Utils.getIntFromField(m_pageLimitField) : -1;
            JsonObject note = NoteConstants.getCmdObject(cmd, ErgoConstants.EXPLORER_NETWORK, m_locationId);
            note.addProperty("id", id);
            note.addProperty("value", value);
            if(offset != -1){
                note.addProperty("offset", offset);
            }
            if(limit != -1){
                note.addProperty("limit", limit);
            }
            if(sortMethod.equals("dsc")){
                note.addProperty("sortDirection", m_searchSortMethod);
            }

            updateSearchResults(null, NoteConstants.getJsonObject("status", "Searching..."));
            networkInterface.sendNote(note, (onSucceeded)->{
                Object sourceObject = onSucceeded.getSource().getValue();
                if(sourceObject != null){
                    updateSearchResults(cmd, (JsonObject) sourceObject);
                }else{
                    updateSearchResults(null, NoteConstants.getJsonObject("error", "Received invalid result"));
                }
            }, (onFailed)->{
                Throwable throwable = onFailed.getSource().getException();
                updateSearchResults(null, NoteConstants.getJsonObject("error", throwable != null ? throwable.getMessage() : "Unknwon error"));
            });
        }else{
            updateSearchResults(null,NoteConstants.getJsonObject("error", "Explorer disabled"));
        }
    }



    private JsonObject m_resultsJson = null;
    private String m_resultsName = null;

    public void updateSearchResults(String name, JsonObject resultsObject){
        m_resultsJson = resultsObject;
        m_resultsName = name;
        if(resultsObject != null){
            if(m_searchClearBtn == null){
                m_searchClearBtn = new Button("☓");
                m_clearBtnAction = (e)->{
                    m_searchTextField.setText("");
                    updateSearchResults(null, null);
                };
                m_searchClearBtn.setOnAction(m_clearBtnAction);
                m_searchHBox.getChildren().add(m_searchClearBtn);                
            }
            if(m_searchResultBox == null){
                m_searchResultBox = new JsonParametersBox(resultsObject, Stages.COL_WIDTH);
                m_searchResultBox.setPadding(new Insets(0,0,0,10));
                m_searchVBox.getChildren().add(m_searchResultBox);
            }else{
                m_searchResultBox.update(resultsObject);
            }
            if(name != null){
                if(m_exportBtn == null){
                    m_exportBtn = new Button("🖫 Export… (*.json)");
                    m_exportSaveFilter = new FileChooser.ExtensionFilter("JSON (application/json)", "*.json");
                    m_gson = new GsonBuilder().setPrettyPrinting().create();
                    m_exportBtn.setOnAction(onSave->{
                        if(m_resultsName != null && m_resultsJson != null && m_gson != null){
                            JsonObject results = m_resultsJson;
                            Gson gson = m_gson;

                            FileChooser saveChooser = new FileChooser();
                            saveChooser.setTitle("🖫 Export JSON");
                            saveChooser.getExtensionFilters().addAll(m_exportSaveFilter);
                            saveChooser.setSelectedExtensionFilter(m_exportSaveFilter);
                            saveChooser.setInitialFileName(m_resultsName + ".json");
                            File saveFile = saveChooser.showSaveDialog(m_appStage);
                            if(saveFile != null){
                                
                                
                                try {
                                    Files.writeString(saveFile.toPath(), gson.toJson(results));
                                } catch (IOException e1) {
                                    Alert alert = new Alert(AlertType.NONE, e1.toString(), ButtonType.OK);
                                    alert.setTitle("Error");
                                    alert.setHeaderText("Error");
                                    alert.initOwner(m_appStage);
                                    alert.show();
                                }
                            }
                        }
                    });

                    m_exportBtnBox = new HBox(m_exportBtn);
                    m_exportBtnBox.setAlignment(Pos.CENTER_RIGHT);
                    m_exportBtnBox.setPadding(new Insets(15,0,15,0));
                    m_searchVBox.getChildren().add(m_exportBtnBox);
                }
            }else{
                if(m_searchVBox.getChildren().contains(m_exportBtnBox)){
                    m_searchVBox.getChildren().remove(m_exportBtnBox);
                    m_exportBtnBox.getChildren().clear();
                    m_exportBtnBox = null;
                    m_exportBtn.setOnAction(null);
                    m_exportBtn = null;
                    m_gson = null;
                }
            }
        }else{
            if(m_searchClearBtn != null){
                m_searchClearBtn.setOnAction(null);
                m_clearBtnAction = null;
                m_searchHBox.getChildren().remove(m_searchClearBtn);
                m_searchClearBtn = null;
            }
            if(m_searchResultBox != null){
                m_searchVBox.getChildren().remove(m_searchResultBox);
                m_searchResultBox.shutdown();
                m_searchResultBox = null;
               
            }
            if(m_searchVBox.getChildren().contains(m_exportBtnBox)){
                m_searchVBox.getChildren().remove(m_exportBtnBox);
                m_exportBtnBox.getChildren().clear();
                m_exportBtnBox = null;
                m_exportBtn.setOnAction(null);
                m_exportBtn = null;
                m_gson = null;
            }
        }
        
    }


    public void addExplorerBoxes(){

        
        
        if(m_explorerParameterBox == null){
            m_explorerParameterBox = new JsonParametersBox(getExplorerInfo(m_defaultExplorer.get()), Stages.COL_WIDTH);
            m_explorerBodyPaddingBox.getChildren().add(m_explorerParameterBox);
            
        }
        addSearchBox();
    }

    public void removeExplorerBoxes(){
        
        removeSearchBox();
        if(m_explorerParameterBox != null){
            if(m_explorerBodyPaddingBox.getChildren().contains(m_explorerParameterBox)) {
                m_explorerBodyPaddingBox.getChildren().remove(m_explorerParameterBox);
            }
            m_explorerParameterBox.shutdown();
            m_explorerParameterBox = null;
        }
    }



    public void setExplorerInfo(JsonObject json){
        if(m_explorerParameterBox != null){        
            m_explorerParameterBox.update(getExplorerInfo(json));   
        }
    }

    public JsonObject getExplorerInfo(JsonObject defaultJson){
        if(defaultJson != null){
            JsonObject infoJson = new JsonObject();
            infoJson.add("info", defaultJson);
            return infoJson;
        }else{
            return NoteConstants.getJsonObject("info", "(disabled)");
        }
    }

    public void setDefaultExplorer(String id, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed ){
        JsonObject note = NoteConstants.getCmdObject("setDefault", ErgoConstants.EXPLORER_NETWORK, m_locationId);
        note.addProperty("id", id);
        
        m_ergoNetworkInterface.sendNote(note, onSucceeded, onFailed);
    }

    public void updateDefaultExplorer(){
        JsonObject getDefaultObject = NoteConstants.getCmdObject("getDefaultJson",  ErgoConstants.EXPLORER_NETWORK, m_locationId);

        m_ergoNetworkInterface.sendNote(getDefaultObject, onSucceeded->{
            Object obj = onSucceeded.getSource().getValue();
            if(obj != null && obj instanceof JsonObject){
                m_defaultExplorer.set((JsonObject) obj);
            }else{
                m_defaultExplorer.set(null);
                m_showExplorers.set(false);
            }
        }, onFailed->{
            m_defaultExplorer.set(null);
            m_showExplorers.set(false);
        });
       
       
        
    }

    public void updateExplorerMenu(){
        openMenuBtn.getItems().clear();
        openMenuBtn.getItems().add(new MenuItem("Getting explorers..."));
        JsonObject note = NoteConstants.getCmdObject("getExplorers", ErgoConstants.EXPLORER_NETWORK, m_locationId);

        
        m_ergoNetworkInterface.sendNote(note, onSucceeded->{
            Object objResult = onSucceeded.getSource().getValue();
            openMenuBtn.getItems().clear();
            if (objResult != null && objResult instanceof JsonArray) {

                JsonArray explorersArray = (JsonArray) objResult;
    
                for (JsonElement element : explorersArray) {
                    
                    JsonObject json = element.getAsJsonObject();
    
                    String name = json.get("name").getAsString();
    
                    MenuItem explorerItem = new MenuItem(String.format("%-50s", " " + name));
    
                    explorerItem.setOnAction(action -> {
                        m_defaultExplorer.set(json);
                    });
    
                    openMenuBtn.getItems().add(explorerItem);
                    
                }
           
           
            }else{
                MenuItem explorerItem = new MenuItem(String.format("%-50s", " Error: Unable to get available explorers."));
                openMenuBtn.getItems().add(explorerItem);
            }
        }, onFailed->{

        });


       
    }

    @Override
    public void sendMessage(int code, long timestamp,String networkId, String msg){
        
        if(networkId != null && networkId.equals(ErgoConstants.EXPLORER_NETWORK)){

            switch(code){
                
                case NoteConstants.LIST_DEFAULT_CHANGED:
                    updateDefaultExplorer();
                break;
              
            }

            AppBox appBox  = m_currentBox.get();
            if(appBox != null){
       
                appBox.sendMessage(code, timestamp,networkId, msg);
                
            }

        }
    
    }


}
