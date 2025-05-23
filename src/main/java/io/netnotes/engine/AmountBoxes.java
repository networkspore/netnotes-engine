package io.netnotes.engine;

import java.util.ArrayList;

import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class AmountBoxes extends VBox {

 //   public final static String ADD_TO_LAST_ROW = "ADD_TO_LAST_ROW";
    public final static String ADD_AS_LAST_ROW = "ADD_AS_LAST_ROW";
    

    public final static int IMAGE_WIDTH = 40;

    private ArrayList<AmountBoxInterface> m_amountsList = new ArrayList<>();

    private final HBox m_lastRowPaddingBox = new HBox();

    
    public AmountBoxes(){
        super();

        init((AmountBoxInterface) null);
    }

    public AmountBoxes( AmountBoxInterface... boxes) {
        super();
       //m_addressData = addressData;
      //  HBox.setHgrow(m_listVBox, Priority.ALWAYS);

        init(boxes);

    }

    public void init(AmountBoxInterface... boxes){
       
        HBox.setHgrow(m_lastRowPaddingBox, Priority.ALWAYS);
        m_lastRowPaddingBox.setAlignment(Pos.CENTER_LEFT);
       
    }

    public HBox lastRowHBox(){
        return m_lastRowPaddingBox;
    }


    public void shutdown(){

        m_amountsList.forEach(box ->{
            box.shutdown();
        });
        m_amountsList.clear();
        getChildren().clear();
    }



    public void clear(){
        shutdown();
    }




    public AmountBoxInterface[] getAmountBoxArray(){
        int size = m_amountsList.size();
        if(size == 0){
            return new AmountBoxInterface[0];
        }
        AmountBoxInterface[] amountBoxes = new AmountBoxInterface[size];
        amountBoxes = m_amountsList.toArray(amountBoxes);
        return amountBoxes;
    }


    public void add(AmountBoxInterface amountBox, boolean update){
        if(amountBox != null && amountBox instanceof HBox){
            HBox existingBox = (HBox) getAmountBox(amountBox.getTokenId());
            if(existingBox == null){
                m_amountsList.add(amountBox);    
                if(update){
                    updateGrid();
                }
            }
        }
    }
  
    
    public void removeOld(long timeStamp){
        ArrayList<String> removeList  = new ArrayList<>();
        removeList.clear();
        for(AmountBoxInterface amountBox : m_amountsList){
            if(amountBox.getTimeStamp() < timeStamp){
                removeList.add(amountBox.getTokenId());        
            }
        }

        for(String tokenId : removeList){
            removeAmountBox(tokenId, false);
        }
        if(removeList.size() > 0){
            updateGrid();
            removeList.clear();
        }
        
    }

    public void removeAmountBox(String tokenId, boolean update){
        if(tokenId != null){
            int size = m_amountsList.size();
        

            for(int i = 0; i < size; i++){
                AmountBoxInterface amountBox = m_amountsList.get(i);
                String amountBoxTokenId = amountBox.getTokenId();

                if(amountBoxTokenId != null && amountBoxTokenId.equals(tokenId)){
                    amountBox.shutdown();
                    m_amountsList.remove(amountBox);
                    break;
                }
            }
            if(update){
                updateGrid();
            }
        }
    }

    public AmountBoxInterface getAmountBox(String tokenId){
        if(tokenId != null){
            int size = m_amountsList.size();
        

            for(int i = 0; i < size; i++){
                AmountBoxInterface amountBox = m_amountsList.get(i);
                String amountBoxTokenId = amountBox.getTokenId();

                if(amountBoxTokenId != null && amountBoxTokenId.equals(tokenId)){
                    return amountBox;
                }
            }
        }

        return null;
    }

    public int size(){
        return m_amountsList.size();
    }



    public void updateGrid(){
        getChildren().clear();
        int size = m_amountsList.size();

        for(int i = 0; i < size; i++){

            HBox amountBox = (HBox) m_amountsList.get(i);
            HBox.setHgrow(amountBox, Priority.ALWAYS);
            
       

            HBox paddingBox = new HBox(amountBox);
            paddingBox.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(paddingBox, Priority.ALWAYS);


            
            getChildren().add(paddingBox);


        }
        
        getChildren().add(m_lastRowPaddingBox);
    
       
    }
}
