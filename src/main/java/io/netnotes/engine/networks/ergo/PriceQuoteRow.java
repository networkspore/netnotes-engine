package io.netnotes.engine.networks.ergo;

import io.netnotes.engine.PriceQuote;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;

public class PriceQuoteRow extends VBox{
    private SimpleObjectProperty<PriceQuote> m_quoteProperty = new SimpleObjectProperty<>();
    private SimpleDoubleProperty m_colWidth = new SimpleDoubleProperty(60);
    private SimpleDoubleProperty m_rowHeight = new SimpleDoubleProperty(27);
    private TextField m_priceField;
    private Label m_symbolLbl;
    private HBox m_topRow;

    public PriceQuoteRow(PriceQuote quote){
        super();
        m_quoteProperty.set(quote);

        m_symbolLbl = new Label(m_quoteProperty.get().getSymbol());
        HBox.setHgrow(m_symbolLbl, Priority.ALWAYS);
        m_symbolLbl.minWidthProperty().bind(m_colWidth);
        m_symbolLbl.maxWidthProperty().bind(m_colWidth);
        m_symbolLbl.setMouseTransparent(true);

        m_priceField = new TextField(m_quoteProperty.get().getAmountString());
        HBox.setHgrow(m_priceField, Priority.ALWAYS);
        m_priceField.setEditable(true);
        m_priceField.setMouseTransparent(true);

        HBox priceQuoteFieldBox = new HBox(m_priceField);
        HBox.setHgrow(priceQuoteFieldBox, Priority.ALWAYS);
        priceQuoteFieldBox.setAlignment(Pos.CENTER_LEFT);
        priceQuoteFieldBox.setId("bodyBox");

        m_quoteProperty.addListener((obs,oldval,newval)->{
            m_symbolLbl.setText(newval.getSymbol());
            m_priceField.setText(newval.getAmountString());
        });

        m_topRow = new HBox(m_symbolLbl, priceQuoteFieldBox);
        HBox.setHgrow(m_topRow, Priority.ALWAYS);
        m_topRow.setAlignment(Pos.CENTER_LEFT);
        m_topRow.maxHeightProperty().bind(m_rowHeight);
        m_topRow.minHeightProperty().bind(m_rowHeight);
        m_topRow.setId("rowBtn");
        m_topRow.setPadding(new Insets(2,0,2,0));
        getChildren().add(m_topRow);
    }


    public void setTopRowClicked(EventHandler<MouseEvent> e){
        m_topRow.setOnMouseClicked(e);
    }

    public PriceQuote getPriceQuote(){
        return m_quoteProperty.get();
    }

    public String getQuoteId(){
        return m_quoteProperty.get().getQuoteId();
    }

    public String getBaseId(){
        return m_quoteProperty.get().getBaseId();
    }

    public String getMarketId(){
        return m_quoteProperty.get().getId();
    }

    public void updateQuote(PriceQuote quote){
        if(quote != null && getMarketId().equals(quote.getId())){
            m_quoteProperty.set(quote);
        }
    }

    public ReadOnlyObjectProperty<PriceQuote> quoteProperty(){
        return m_quoteProperty;
    }

    public SimpleDoubleProperty colWidth(){
        return m_colWidth;
    }

    public SimpleDoubleProperty rowHeight(){
        return m_rowHeight;
    }
}