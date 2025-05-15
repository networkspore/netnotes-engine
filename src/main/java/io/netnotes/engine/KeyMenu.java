package io.netnotes.engine;

import java.util.ArrayList;
import javafx.collections.ObservableList;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;

public class KeyMenu extends Menu{
    public static final int DEFAULT_COL_SIZE = 20;
    private int m_colSize =DEFAULT_COL_SIZE;
    private long m_timeStamp = 0;
    private String m_key = null;
    private String m_value = null;

    public KeyMenu(String key, String value, long timeStamp){
        this(key, value, timeStamp, DEFAULT_COL_SIZE);
    }
    public KeyMenu(String key, String value, long timeStamp, int colSize){
        super();
        m_colSize = colSize;
        m_key = key;
        m_value = value;
        m_timeStamp = timeStamp;
        update();
    }

    public void update(){
        setText(String.format("%-"+m_colSize +"s", m_key + ": ") + String.format("%"+m_colSize +"s", m_value));        
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

    public static KeyMenu getKeyMenu(ObservableList<MenuItem> items, String key){
        for(int i = 0; i < items.size(); i++){
            MenuItem item = items.get(i);
            if(item instanceof KeyMenu){
                KeyMenu keyItem = (KeyMenu) item;
                if(keyItem.getKey().equals(key)){
                    return keyItem;
                }
            }
        }
        return null;
    }

    public static void removeeOldKeyMenus(ObservableList<MenuItem> items, long timeStamp){
         ArrayList<String> removeList  = new ArrayList<>();

        for(int i = 0; i < items.size(); i++){
            MenuItem item = items.get(i);
            if(item instanceof KeyMenu){
                KeyMenu keyItem = (KeyMenu) item;
                if(keyItem.getTimeStamp() < timeStamp){
                    removeList.add(keyItem.getKey());        
                }
            }
        }

        for(String key : removeList){
            removeKeyMenu(items, key);
        }
    }

    public static KeyMenu removeKeyMenu(ObservableList<MenuItem> items, String key){
        for(int i = 0; i < items.size(); i++){
            MenuItem item = items.get(i);
            if(item instanceof KeyMenu){
                KeyMenu keyItem = (KeyMenu) item;
                if(keyItem.getKey().equals(key)){
                    return (KeyMenu) items.remove(i);
                }
            }
        }
        return null;
    }


}
