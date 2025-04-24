package io.netnotes.engine.networks.ergo;

import java.util.ArrayList;

import io.netnotes.engine.PageBox;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;

import javafx.geometry.Pos;

import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import javafx.geometry.Insets;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

public class PriceQuoteScroll extends VBox {
    private ArrayList<PriceQuoteRow> m_priceQuotesList = new ArrayList<>();
    private boolean m_showScroll = false;
    private Button m_showScrollBtn = null;
    private SimpleDoubleProperty m_colWidth = null;
    private ScrollPane m_quoteRowScroll = null;
    private VBox m_quoteRowScrollContent = null;
    private VBox m_quoteControlBox = null;
    private Label m_heading = null;
    private SimpleStringProperty m_emptyText = new SimpleStringProperty("Empty");
    private SimpleDoubleProperty m_rowHeight = new SimpleDoubleProperty(27);
    private int m_visibleRows = 20;
    private Button m_titleBtn;
    private HBox m_emptyBox;
    private double m_leftOffset = 20;
    private double m_rightOffset = 0;
    private Button m_updateBtn;
    private PageBox m_pageBox = null;


    public PriceQuoteScroll(String headingText, String text, SimpleDoubleProperty colWidth){
        super();
        m_updateBtn = new Button();
        m_pageBox = new PageBox(m_updateBtn);
        m_colWidth = colWidth;
        m_titleBtn = new Button(text);
        m_titleBtn.setAlignment(Pos.CENTER_LEFT);
        m_titleBtn.setOnAction(e->toggleShow());
        
        HBox titleFieldBox = new HBox(m_titleBtn);
        HBox.setHgrow(titleFieldBox, Priority.ALWAYS);
        titleFieldBox.setAlignment(Pos.CENTER_LEFT);
        titleFieldBox.setId("bodyBox");

        titleFieldBox.widthProperty().addListener((obs,oldval,newval)->{
            m_titleBtn.setPrefWidth(newval.doubleValue()-1);
        });
        

        m_showScrollBtn = new Button(m_showScroll ? "⏷" : "⏵");
        m_showScrollBtn.setId("caretBtn");
        m_showScrollBtn.setMinWidth(25);
        m_showScrollBtn.setOnAction(e->toggleShow());

        m_heading = new Label(headingText);
        m_heading.setId("logoBox");
        m_heading.maxWidthProperty().bind(colWidth);
        m_heading.minWidthProperty().bind(colWidth);


        HBox headingBox = new HBox(m_showScrollBtn, m_heading, titleFieldBox);
        headingBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headingBox,Priority.ALWAYS);

        this.getChildren().add(headingBox);
    }

    public PageBox getPageBox(){
        return m_pageBox;
    }

    public String getText(){
        return m_titleBtn.getText();
    }

    public void setText(String text){
        m_titleBtn.setText(text);
    }

    public void toggleShow(){
        m_showScroll = !m_showScroll;
        if(m_showScroll){
            addScrollBox();
          
        }else{
            removeScrollBox();
        }
        m_showScrollBtn.setText(m_showScroll ? "⏷" : "⏵");
    }

    public void show(){
        if(!m_showScroll){
            m_showScroll = true;
            addScrollBox();
            m_showScrollBtn.setText(m_showScroll ? "⏷" : "⏵");
        }
    }

    public void hide(){
        if(m_showScroll){
            m_showScroll = false;
            removeScrollBox();
            m_showScrollBtn.setText(m_showScroll ? "⏷" : "⏵");
        }
    }

    public void setRowHeight(double height){
        m_rowHeight.set(height);
    }

    public void setVisibleRows(int rows){
        m_visibleRows = rows;
        updatePageBox();
    }

    public int getVisisbleRows(){
        return m_visibleRows;
    }

    public void setHeading(String heading){
        m_heading.setText(heading);
    }

    public String getHeading(){
        return m_heading.getText();
    }

    public void setEmptyText(String emptyText){
        m_emptyText.set(emptyText);
    }

    public String getEmptyText(){
        return m_emptyText.get();
    }
    
    public ReadOnlyStringProperty emptyTextProperty(){
        return m_emptyText;
    }

    public void clear(){
        m_priceQuotesList.clear();
        layoutRows();
    }

    public void addRow(PriceQuoteRow row){
        addRow(row,true);
    }
    public void addRow(PriceQuoteRow row, boolean update){
        row.colWidth().bind(m_colWidth);
        row.rowHeight().bind(m_rowHeight);
        removeEmptyBox();
        m_priceQuotesList.add(row);
        if(m_quoteRowScrollContent != null){
            m_quoteRowScrollContent.getChildren().add(row);
            if(update){
                updatePageBox();
            }
        }
    }

    public void addRow(int index, PriceQuoteRow row){
        row.colWidth().bind(m_colWidth);
        row.rowHeight().bind(m_rowHeight);
        removeEmptyBox();
        if(m_priceQuotesList.size() >= index){
            m_priceQuotesList.add(index, row);
        }else{
            m_priceQuotesList.add(row);
        }

        if(m_quoteRowScrollContent != null){
            if(m_quoteRowScrollContent.getChildren().size() >= index){
                m_quoteRowScrollContent.getChildren().add(index, row);
            }else{
                m_quoteRowScrollContent.getChildren().add(row);
            }
            updatePageBox();
        }
    }

    public PriceQuoteRow getRow(String marketId){
        int size = m_priceQuotesList.size();
        for(int i = 0; i < size ; i++){
            PriceQuoteRow row = m_priceQuotesList.get(i);
            if(row.getMarketId().equals(marketId)){
                return row;
            }
        }
        return null;
    }

    public PriceQuoteRow removeRow(String marketId){
        int size = m_priceQuotesList.size();
        for(int i = 0; i < size ; i++){
            PriceQuoteRow row = m_priceQuotesList.get(i);
            if(row.getMarketId().equals(marketId)){
                if(m_quoteRowScrollContent.getChildren().contains(row)){
                    m_quoteRowScrollContent.getChildren().remove(row);
                    updatePageBox();
                }
                m_priceQuotesList.remove(i);
                return row;
            }
        }
        return null;
    }

    public int size(){
        return m_priceQuotesList.size();
    }



    public void layoutRows(){
        if(m_quoteRowScrollContent != null){
            int size = m_priceQuotesList.size();
            size  = size > m_pageBox.getLimit() ? m_pageBox.getLimit() : size;
            m_quoteRowScrollContent.getChildren().clear();
            if(size > 0){
                removeEmptyBox();
                for(int i = 0; i < size ; i++){
                    PriceQuoteRow quoteRow = m_priceQuotesList.get(i);
                    m_quoteRowScrollContent.getChildren().add(quoteRow);
                }
            }else{
                Label emptyLabel = new Label();
                emptyLabel.textProperty().bind(m_emptyText);
                
                m_emptyBox = new HBox(emptyLabel);
                HBox.setHgrow(m_emptyBox, Priority.ALWAYS);
                m_emptyBox.setAlignment(Pos.CENTER);
                m_emptyBox.setPrefHeight(m_rowHeight.get());
                m_quoteRowScrollContent.getChildren().add(m_emptyBox);
                m_quoteRowScrollContent.setAlignment(Pos.CENTER);
    
            }
            updatePageBox();
        }
    }
    private void removeEmptyBox(){
        if(m_emptyBox != null){
            if(m_quoteRowScrollContent != null && m_quoteRowScrollContent.getChildren().contains(m_emptyBox)){
                m_quoteRowScrollContent.getChildren().remove(m_emptyBox);
            }
            m_emptyBox= null;
        }
    }


    public void clearQuotes(){
        m_priceQuotesList.clear();
        if(m_quoteRowScrollContent != null){
            m_quoteRowScrollContent.getChildren().clear();
        }
    }

    public void updatePageBox(){
        if(m_quoteRowScroll != null){
            int size = m_priceQuotesList.size();
            int visibleRows = size == 0 ? 1 : m_visibleRows;
            m_quoteRowScroll.setPrefViewportHeight((m_rowHeight.get() * (size > visibleRows ? visibleRows : size)) + 2);
            m_quoteRowScrollContent.setMinHeight((m_rowHeight.get() * (size > visibleRows ? visibleRows : size)));
            if(m_pageBox.getMaxItems() > m_pageBox.getLimit()){
                if(m_quoteControlBox != null && !m_quoteControlBox.getChildren().contains(m_pageBox)){
                    m_quoteControlBox.getChildren().add(m_pageBox);
                }
                m_pageBox.show(); 
            }else{
                m_pageBox.clear();
            }
        }
    }

    public void update(){
        m_updateBtn.fire();
    }

    private void addScrollBox(){
        if(m_quoteRowScroll == null){
            update();
            m_quoteRowScrollContent = new VBox();
         
            m_quoteRowScroll = new ScrollPane(m_quoteRowScrollContent);
            m_quoteControlBox = new VBox(m_quoteRowScroll);
            m_quoteControlBox.setPadding(new Insets(2,m_rightOffset,2 ,m_leftOffset));
            layoutRows();

            m_quoteRowScroll.prefViewportWidthProperty().bind(m_quoteControlBox.widthProperty().subtract(1));
            m_quoteRowScrollContent.prefWidthProperty().bind(m_quoteControlBox.widthProperty().subtract(17+m_rightOffset + m_leftOffset));
            this.getChildren().add(m_quoteControlBox);
        }
    }

    private void removeScrollBox(){
        if(m_quoteControlBox != null){
            this.getChildren().remove(m_quoteControlBox);
            m_quoteControlBox.getChildren().clear();
            m_quoteRowScroll.setContent(null);
            m_quoteRowScrollContent.getChildren().clear();
            m_quoteControlBox = null;
            m_quoteRowScroll = null;
            m_quoteRowScrollContent = null;
        }
    }


    public boolean isShowing(){
        return m_showScroll;
    }



    public void setOnUpdate(EventHandler<ActionEvent> e){
        m_updateBtn.setOnAction(e);
    }

    
}
