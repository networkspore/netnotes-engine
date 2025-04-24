package io.netnotes.engine.networks.ergo;

import java.util.ArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.netnotes.engine.JsonParametersBox;
import io.netnotes.engine.NoteConstants;

import javafx.scene.layout.Region;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ErgoTxViewsBox extends VBox {
    private ArrayList<ErgoTransactionViewRow> m_txViewRows = new ArrayList<>();
    private final String m_parentAddress;
    private JsonParametersBox m_detailsParamsBox = null;
    private SimpleDoubleProperty m_colWidth;
    private int m_currentPage = 0;
    private int m_pageSize = 20;
    private ErgoWalletControl m_walletControl;
    private Stage m_appStage;

    public ErgoTxViewsBox(String parentAddress, double colWidth, Stage appStage, ErgoWalletControl walletControl){
        super();
        m_parentAddress = parentAddress;
        m_colWidth = new SimpleDoubleProperty(colWidth);
        m_walletControl = walletControl;
        m_appStage = appStage;
    }

    private boolean m_doGridUpdate = false;

    public void update(JsonObject json){
        JsonElement txsElement = json.get("txs");
        if(txsElement != null && !txsElement.isJsonNull() && txsElement.isJsonArray()){
            JsonArray txsArray = txsElement.getAsJsonArray();
            int size = txsArray.size();
            boolean isEmpty = m_txViewRows.size() == 0;
            if(size == 0){
                setEmptyParameters(NoteConstants.getJsonObject("info", "No transactions found"));
            }else{

                m_doGridUpdate = isEmpty;
                for(int i = 0; i < size ; i++){
                    if(isEmpty){
                        m_txViewRows.add( new ErgoTransactionViewRow(m_parentAddress, txsArray.get(i).getAsJsonObject(), m_colWidth,m_appStage, m_walletControl));
                    }else{
                        boolean updated = addRowInOrder(txsArray.get(i).getAsJsonObject());
                        m_doGridUpdate = m_doGridUpdate == false && updated ? true : m_doGridUpdate;
                    }
                }
                if(m_doGridUpdate){
                    updateGrid();
                }
            }
            
        }else{
            setEmptyParameters(json);
        }

    }

    public boolean addRowInOrder(JsonObject txViewJson){

        String id = txViewJson.get("id").getAsString();
        ErgoTransactionViewRow existingRow = getTxViewRowItem(id);

        if(existingRow == null){
            ErgoTransactionViewRow row = new ErgoTransactionViewRow(m_parentAddress, txViewJson, m_colWidth, m_appStage, m_walletControl);
            int index = 0;
            int size =  m_txViewRows.size();
            long lastTimeStamp = size > 0 ? m_txViewRows.get(0).getTransactionView().getTimeStamp() : 0;
            long rowTimeStamp = row.getTransactionView().getTimeStamp();

            while(index < size && rowTimeStamp > lastTimeStamp){
                index++;
                lastTimeStamp = index < size ?  m_txViewRows.get(index).getTransactionView().getTimeStamp() : lastTimeStamp;
            }

            if(index >= m_txViewRows.size()){
                m_txViewRows.add(row);
            }else{
                m_txViewRows.add(index, row);
            }
            return true;
        }else{
            existingRow.getTransactionView().updateJson(txViewJson, true);
            return false;
        }
    }

    public void updateGrid(){
        m_doGridUpdate = false;
        getChildren().clear();
        if(m_detailsParamsBox != null){
            m_detailsParamsBox.shutdown();
            m_detailsParamsBox = null;
        }
        int minIndex = getMinIndex();
        int maxIndex = getMaxIndex(minIndex);
        int i = minIndex;
        int size = m_txViewRows.size();
        if(size > m_pageSize){
            getChildren().add(getNavigateRow());
        }
        while(i < maxIndex && i < size){
            ErgoTransactionViewRow row = m_txViewRows.get(i);
            getChildren().add(row);
            i++;
        }
    }

    public int getMinIndex(){
        return m_currentPage * m_pageSize;
    }

    public int getMaxIndex(int minIndex){
        return minIndex + m_pageSize;
    }

    private HBox m_navigateRow = null;
    private Button m_nextPageBtn = null;
    private Button m_prevPageBtn = null;
    private Region m_pageRegion = null;

    private HBox getNavigateRow(){
        
        if(m_navigateRow == null){
            m_navigateRow = new HBox();
        }
        m_navigateRow.getChildren().clear();
        if(m_currentPage > 0){
            m_navigateRow.getChildren().add(getPrevPageBtn());
        }
        m_navigateRow.getChildren().add(getPageRegion());

        if(m_txViewRows.size() > getMaxIndex(getMinIndex())){
            m_navigateRow.getChildren().add(getNextPageBtn());
        }

        return m_navigateRow;
    }

    private Region getPageRegion(){
        if(m_pageRegion == null){
            m_pageRegion = new Region();
            HBox.setHgrow(m_pageRegion, Priority.ALWAYS);
        }
        return m_pageRegion;
    }

    private Button getNextPageBtn(){
        if(m_nextPageBtn == null){
            m_nextPageBtn = new Button("⮞");
            m_nextPageBtn.setId("toolBtn");
            m_nextPageBtn.setOnAction(e->{
    
            });
        }
        return m_nextPageBtn;
    }

    private Button getPrevPageBtn(){
        if(m_prevPageBtn == null){
            m_prevPageBtn = new Button("⮜");
            m_prevPageBtn.setId("toolBtn");
            m_prevPageBtn.setOnAction(e->{
                
            });
        }
        return m_prevPageBtn;
    }



    public ErgoTransactionViewRow getTxViewRowItem(String txId){
        if(txId != null){
            for(int i = 0; i < m_txViewRows.size(); i++){
                ErgoTransactionViewRow row = m_txViewRows.get(i);
                if(row.getTransactionView().getId().equals(txId)){
                    return row;
                }
            }
        }
        return null;
    }



    public void setEmptyParameters(JsonObject json){
        getChildren().clear();
        m_txViewRows.clear();
        if(m_detailsParamsBox == null){
            m_detailsParamsBox = new JsonParametersBox(json, m_colWidth);
        }else{
            m_detailsParamsBox.update(json);  
        }
        getChildren().add(m_detailsParamsBox);
    }
}
