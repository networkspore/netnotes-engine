package io.netnotes.engine;

import java.util.ArrayList;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javafx.collections.ObservableList;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;

public class KeyMenuItem extends MenuItem{
    public static final int DEFAULT_COL_SIZE = 20;
    private int m_colSize =DEFAULT_COL_SIZE;
    private long m_timeStamp = 0;
    private String m_key = null;
    private String m_value = null;

    public KeyMenuItem(String key, String value, long timeStamp){
        this(key, value, timeStamp, DEFAULT_COL_SIZE);
    }
    public KeyMenuItem(String key, String value, long timeStamp, int colSize){
        super();
        m_colSize = colSize;
        m_key = key;
        m_value = value;
        m_timeStamp = timeStamp;
        update();
    }

    public void update(){
        boolean isValue = m_value != null && m_value.length() > 0;
        setText(String.format("%-"+m_colSize +"s", m_key + (isValue ? ": " : "")) + (isValue ?  String.format("%"+m_colSize +"s", m_value) : ""));        
    }

    public long getTimeStamp(){
        return m_timeStamp;
    }

    public void setTimeStamp(long timeStamp){
        m_timeStamp = timeStamp;;
    }

    public void setValue(String value, long timeStamp){
        m_value = value;
        m_timeStamp = timeStamp;
        update();
    }

    public String getKey(){
        return m_key;
    }

    public String getValue(){
        return m_value;
    }

    public static KeyMenuItem getKeyMenuItem(ObservableList<MenuItem> items, String key){
        for(int i = 0; i < items.size(); i++){
            MenuItem item = items.get(i);
            if(item instanceof KeyMenuItem){
                KeyMenuItem keyItem = (KeyMenuItem) item;
                if(keyItem.getKey().equals(key)){
                    return keyItem;
                }
            }
        }
        return null;
    }

    public static void removeeOldKeyItems(ObservableList<MenuItem> items, long timeStamp){
         ArrayList<String> removeList  = new ArrayList<>();

        for(int i = 0; i < items.size(); i++){
            MenuItem item = items.get(i);
            if(item instanceof KeyMenuItem){
                KeyMenuItem keyItem = (KeyMenuItem) item;
                if(keyItem.getTimeStamp() < timeStamp){
                    removeList.add(keyItem.getKey());        
                }
            }
        }

        for(String key : removeList){
            removeKeyItem(items, key);
        }
    }

    public static KeyMenuItem removeKeyItem(ObservableList<MenuItem> items, String key){
        for(int i = 0; i < items.size(); i++){
            MenuItem item = items.get(i);
            if(item instanceof KeyMenuItem){
                KeyMenuItem keyItem = (KeyMenuItem) item;
                if(keyItem.getKey().equals(key)){
                    return (KeyMenuItem) items.remove(i);
                }
            }
        }
        return null;
    }

    public static void updateMenu(Menu menu, JsonObject json){
        long timeStamp = System.currentTimeMillis();
            if(menu.getItems().size() == 0){
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    KeyMenuItem item = new KeyMenuItem(entry.getKey(), entry.getValue().toString(), timeStamp);
                    menu.getItems().add(item);
                }
            }else{
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    String key =  entry.getKey();
                    String value = entry.getValue().toString();

                    KeyMenuItem existingItem =  KeyMenuItem.getKeyMenuItem(menu.getItems(), key);
                    
                    if(existingItem != null){
                        existingItem.setValue(value, timeStamp);
                    }else{
                        KeyMenuItem item = new KeyMenuItem(key, value, timeStamp);
                        menu.getItems().add(item);
                    }
                }

                KeyMenuItem.removeeOldKeyItems(menu.getItems(), timeStamp);

            }
    }
}
