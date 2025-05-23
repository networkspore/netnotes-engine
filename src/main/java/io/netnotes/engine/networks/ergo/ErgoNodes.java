package io.netnotes.engine.networks.ergo;



import java.util.concurrent.Future;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netnotes.engine.NetworkInformation;
import io.netnotes.engine.NoteConstants;

import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.scene.image.Image;

public class ErgoNodes {

  //  private File logFile = new File("netnotes-log.txt");

    public final static String NAME = "Ergo Nodes";
    public final static String DESCRIPTION = "Ergo Nodes allows you to configure your access to the Ergo blockchain";

    public final static String SUMMARY = "";

    public final static int MAINNET_PORT = 9053;
    public final static int TESTNET_PORT = 9052;
    public final static int EXTERNAL_PORT = 9030;

    private ErgoNodesList m_ergoNodesList = null;
    private ErgoNetwork m_ergoNetwork;

    public ErgoNodes(ErgoNetworkData ergoNetworkData, ErgoNetwork ergoNetwork) {
        
        m_ergoNetwork = ergoNetwork;
        m_ergoNodesList = new ErgoNodesList(this, ergoNetworkData);
    }

    public ErgoNetwork getErgoNetwork(){
        return m_ergoNetwork;
    }
  
    public String getDescription(){
        return DESCRIPTION;
    }
    
   
    public static String getAppIconString(){
        return "/assets/ergoNodes-100.png";
    }

    public static String getSmallAppIconString(){
        return "/assets/ergoNodes-30.png";
    }

    private Image m_smallAppIcon = new Image(getSmallAppIconString());

    public Image getSmallAppIcon() {
        return m_smallAppIcon;
    }



    public Future<?> sendNote(JsonObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {

        JsonElement cmdElement = note != null ? note.get(NoteConstants.CMD) : null;
        JsonElement idElement = note != null ? note.get("id") : null;
        
        if(cmdElement != null){


            switch (cmdElement.getAsString()) {
                case "getNodes":
                    return m_ergoNodesList.getNodes(onSucceeded);
                case "addRemoteNode":
                    return m_ergoNodesList.addRemoteNode(note, onSucceeded, onFailed);
                case "getRemoteNodes":
                    return m_ergoNodesList.getRemoteNodes(note, onSucceeded);
                case "getLocalNodes":
                    return m_ergoNodesList.getLocalNodes(note, onSucceeded);
                case "getDefaultJson":
                    return m_ergoNodesList.getDefaultJson(onSucceeded);
                case "setDefault":
                    return m_ergoNodesList.setDefault(note, onSucceeded, onFailed);
                case "clearDefault":
                    return m_ergoNodesList.clearDefault(onSucceeded);
                case "getDefaultNodeId":
                    return m_ergoNodesList.getDefaultNodeId(onSucceeded);
                case "removeNodes":
                    return m_ergoNodesList.removeNodes(note, onSucceeded, onFailed);
                case "addLocalNode":
                    return m_ergoNodesList.addLocalNode(note, onSucceeded, onFailed);
                default: 
                    String id = idElement != null ? idElement.getAsString() : m_ergoNodesList.getDefaultNodeId();
                
                    ErgoNodeData nodeData = m_ergoNodesList.getNodeById(id);
                
                    if(nodeData != null){
                    
                        return nodeData.sendNote(note, onSucceeded, onFailed);
                    }
            }
        }

        return null;
    }

  /*


    private Stage m_stage = null;

   
    public void showStage() {
        if (m_stage == null) {
            String title = getName();

            double buttonHeight = 70;

      

            m_stage = new Stage();
            m_stage.getIcons().add(getIcon());
            m_stage.setResizable(false);
            m_stage.initStyle(StageStyle.UNDECORATED);
            m_stage.setTitle(title);

            Button closeBtn = new Button();

            Button maximizeBtn = new Button();

            HBox titleBox = NoteConstants.createTopBar(getSmallAppIcon(), maximizeBtn, closeBtn, m_stage);

            ScrollPane scrollPane = new ScrollPane();
            scrollPane.setId("bodyBox");

        
            
        
            Button addBtn = new Button("Add");
            addBtn.setId("menuBarBtn");
            addBtn.setPadding(new Insets(2, 6, 2, 6));
            addBtn.setPrefWidth(getStageWidth() / 2);
            addBtn.setPrefHeight(buttonHeight);
            addBtn.setOnAction(e->{
                m_ergoNodesList.showAddNodeStage();
            });


            Button removeBtn = new Button("Remove");
            removeBtn.setId("menuBarBtnDisabled");
            removeBtn.setPadding(new Insets(2, 6, 2, 6));
            removeBtn.setDisable(true);
            removeBtn.setPrefWidth(getStageWidth() / 2);
            removeBtn.setPrefHeight(buttonHeight);

            removeBtn.setOnAction(e->{
                String selectedId = m_ergoNodesList.selectedIdProperty().get();
                ErgoNodeData ergoNodeData = m_ergoNodesList.getErgoNodeData(selectedId);
                ergoNodeData.remove();
            });

            HBox menuBox = new HBox(addBtn, removeBtn);
            menuBox.setId("blackMenu");
            menuBox.setAlignment(Pos.CENTER_LEFT);
            menuBox.setPadding(new Insets(5, 5, 5, 5));
            menuBox.setPrefHeight(buttonHeight);
            menuBox.setMinHeight(buttonHeight);

            m_ergoNodesList.selectedIdProperty().addListener((obs,oldval,newval)->{
                if(newval != null){
                    removeBtn.setDisable(false);
                    
                    removeBtn.setId("menuBarBtn");

                }else{
                    removeBtn.setDisable(true);
                    removeBtn.setId("menuBarBtnDisabled");
                }
            });


            VBox bodyBox = new VBox(scrollPane,menuBox);
            bodyBox.setPadding(new Insets(0,3,2,3));
            VBox layoutBox = new VBox(titleBox, bodyBox);
  
          
            Scene mainScene = new Scene(layoutBox, getStageWidth(), getStageHeight());
            mainScene.setFill(null);
            mainScene.getStylesheets().add("/css/startWindow.css");
            m_stage.setScene(mainScene);

 

            scrollPane.prefViewportWidthProperty().bind(mainScene.widthProperty().subtract(4));
            scrollPane.prefViewportHeightProperty().bind(mainScene.heightProperty().subtract(titleBox.heightProperty()).subtract(menuBox.heightProperty()));
            scrollPane.setPadding(new Insets(5, 5, 5, 5));
            scrollPane.setOnMouseClicked(e -> {
       
            });

            SimpleDoubleProperty gridWidth = new SimpleDoubleProperty(m_stage.getWidth());
            SimpleDoubleProperty scrollWidth = new SimpleDoubleProperty(0);
            gridWidth.bind(mainScene.widthProperty().subtract(20));
            m_stage.show();

            VBox gridBox = m_ergoNodesList.getGridBox(gridWidth, scrollWidth);

    

          

            ResizeHelper.addResizeListener(m_stage, 300, 300, Double.MAX_VALUE, Double.MAX_VALUE);

            scrollPane.setContent(gridBox);

            addBtn.prefWidthProperty().bind(mainScene.widthProperty().divide(2));
            removeBtn.prefWidthProperty().bind(mainScene.widthProperty().divide(2));


            m_stage.setOnCloseRequest(e -> {
                shutdownNowProperty().set(LocalDateTime.now());
            });

            closeBtn.setOnAction(closeEvent -> {
                shutdownNowProperty().set(LocalDateTime.now());
            });
            shutdownNowProperty().addListener((obs, oldVal, newVal) -> {
                m_ergoNodesList.shutdown();
                
                if(m_stage != null){
                    m_stage.close();
                    m_stage = null;
                }
            });

            Runnable updateScrollWidth = () -> {
                double val = gridBox.heightProperty().doubleValue();
                if (val > scrollPane.prefViewportHeightProperty().doubleValue()) {
                    scrollWidth.set(40);
                } else {
                    scrollWidth.set(0);
                }
            };

            gridBox.heightProperty().addListener((obs, oldVal, newVal) -> updateScrollWidth.run());

            

            updateScrollWidth.run();

            if (getStageMaximized()) {
                m_stage.setMaximized(true);
            }

        } else {
            if (m_stage.isIconified()) {
                m_stage.setIconified(false);
            }
            if(!m_stage.isShowing()){
                m_stage.show();
            }else{
                Platform.runLater(()->m_stage.toBack());
                Platform.runLater(()->m_stage.toFront());
            }
         
        }

    } */


    public void shutdown(){
        if(m_ergoNodesList != null){
            m_ergoNodesList.shutdown();
        }
    }

    public NetworkInformation getNetworkInformation(){
        return new NetworkInformation(ErgoConstants.NODE_NETWORK, NAME, getAppIconString(), getSmallAppIconString(), DESCRIPTION);
    }
}
