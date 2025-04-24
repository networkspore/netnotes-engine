package io.netnotes.engine;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;

public class LockField extends HBox {
    

    private final double charWidth = Utils.computeTextWidth(Stages.txtFont, " ");

    private final Text m_nameLabel;
    private final HBox m_addressFieldBox;
    private final Button m_unlockBtn;
    private String m_lockString;
    private final HBox m_unlockBtnBox;

    private final MenuButton m_openBtn;

    private final Label m_label;
    private String m_unlockLabelString = "≬ ";
    private String m_lockLabelString = "⚿ ";
    private final Button m_lockBtn;

    private final HBox m_topBox;

    public LockField(ReadOnlyStringProperty walletControlAddress){
        super();
        setAlignment(Pos.CENTER_LEFT);

        String prompt = "[ click to unlock ]";
       
        m_lockString = "Address ";
        
        m_label = new Label(m_lockLabelString);
        m_label.setId("logoBox");

        m_lockBtn = new Button("☓");
        m_lockBtn.setId("lblBtn");

        m_nameLabel = new Text(m_lockString);
        m_nameLabel.setFont(Stages.txtFont);
        m_nameLabel.setFill(Stages.txtColor);

        m_unlockBtn = new Button(prompt);
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
          
            setAddressText(walletControlAddress.get(), w-adrBtnSizeOffset);
            
        });
   
        m_topBox = new HBox();
        HBox.setHgrow(m_topBox, Priority.ALWAYS);

        if(walletControlAddress.get() != null){
            setUnlocked(walletControlAddress.get());
        }

        walletControlAddress.addListener((obs,oldval,newval)->{
            String address = newval;
            if(address != null){
                setUnlocked(address);
                
            }else{
                setLocked();
            }
            double w = m_addressFieldBox.widthProperty().get() - 1 - m_lockBtn.widthProperty().get() -adrBtnSizeOffset;
            setAddressText(address, w);
        });
        
        getChildren().addAll(m_label, textBox, m_unlockBtnBox);
    }

    private void setAddressText(String address, double w){
        if(address!=null){
            m_openBtn.setText(Utils.formatAddressString(address, w, charWidth));
        }else{
            m_openBtn.setText( m_lockString);
        }
    }
    
    private void setLocked(){
        m_label.setText(m_lockLabelString);

        if(!getChildren().contains(m_unlockBtnBox)){
            getChildren().add(m_unlockBtnBox);
        }

        if(getChildren().contains(m_addressFieldBox)){
            getChildren().remove(m_addressFieldBox);
        }

        m_nameLabel.setText(m_lockString);
  
    }

    private void setUnlocked(String address){
        m_label.setText(m_unlockLabelString);
        
        if(getChildren().contains(m_unlockBtnBox)){
            getChildren().remove(m_unlockBtnBox);
        }
        
        if(!getChildren().contains(m_addressFieldBox)){
            getChildren().add(m_addressFieldBox);
        }
            
    } 

    public void setOnLockBtn(EventHandler<ActionEvent> onLockBtn){
        m_lockBtn.setOnAction(onLockBtn);
    }


    public void setPasswordAction( EventHandler<ActionEvent> onAction){
        m_unlockBtn.setOnAction(onAction);
    }

    public void setOnMenuShowing(ChangeListener<Boolean> onShowing){
  
        m_openBtn.showingProperty().addListener(onShowing);
 
    }

    public ObservableList<MenuItem> getItems(){
        return m_openBtn.getItems();
    }

    public StringProperty textProperty(){
        return m_nameLabel.textProperty();
    }

 


}
