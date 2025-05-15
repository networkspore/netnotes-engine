package io.netnotes.engine;

import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class PriceAmountMenuItem extends Menu {
    private PriceAmount m_priceAmount = null;
    private long m_timeStamp = 0;
    private MenuItem m_copyAmountItem;
    private Menu m_currencyMenu;
    private Menu m_quoteMenu = null;

    public PriceAmountMenuItem(PriceAmount amount, long timeStamp){
        super();
        m_priceAmount = amount;
        m_timeStamp = timeStamp;

        m_currencyMenu = new Menu(amount.getCurrency().getName());

        updateCurrencyMenu();

        m_copyAmountItem = new MenuItem("(copy balance)");
        m_copyAmountItem.setOnAction(e->{
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(m_priceAmount.getAmountString());
            clipboard.setContent(content);
        });
        this.getItems().addAll(m_currencyMenu, new SeparatorMenuItem(), m_copyAmountItem);
        update();
    }

    public PriceAmount getPriceAmount(){
        return m_priceAmount;
    }

    public void update(){
        setText(String.format("%-15s", m_priceAmount.getName()) + String.format("%20s", m_priceAmount.getAmountString()));
        updateCurrencyMenu();
        updateQuoteMenu();
    }

    public void setPriceAmount(PriceAmount amount, long timeStamp){
        m_priceAmount = amount;
        m_timeStamp = timeStamp;
        update();
     
    }

    public long getTimeStamp(){
        return m_timeStamp;
    }

    public void setTimeStamp(long timeStamp){
        m_timeStamp = timeStamp;;
    }

    public void updateCurrencyMenu(){
  
        JsonObject json = m_priceAmount.getJsonObject();
        KeyMenuItem.updateMenu(m_currencyMenu, json);

    }

    public void updateQuoteMenu(){
        PriceQuote quote = m_priceAmount.getPriceQuote();
        if(quote != null){
            if(m_quoteMenu == null){
                m_quoteMenu = new Menu();
                getItems().add(0, m_quoteMenu);
            }
            m_quoteMenu.setText("Quote: " + quote.toString());
            JsonObject json = quote.getJsonObject();
            KeyMenuItem.updateMenu(m_quoteMenu, json);
        }else{
            if(m_quoteMenu != null){
                getItems().remove(m_quoteMenu);
                m_quoteMenu = null;
            }
        }

    }

    
}
