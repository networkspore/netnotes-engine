package io.netnotes.engine;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.MenuItem;
import javafx.scene.control.Menu;

public class KeyMenu extends Menu implements KeyInterface{
    public static final String KEY_AND_VALUE = "keyAndValue";
    public static final String KEY_NOT_VALUE = "keyNotValue";
    public static final String NOT_KEY_VALUE = "notKeyValue";
    public static final String VALUE_AND_KEY = "valueAndKey";
    public static final String VALUE_NOT_KEY = "valueNotKey";


    public static final int DEFAULT_COL_SIZE = 20;
    private int m_colSize =DEFAULT_COL_SIZE;
    private long m_timeStamp = 0;
    private NoteBytes m_key = null;
    private String m_value = null;

    private String m_style = KEY_AND_VALUE;

    public KeyMenu(NoteBytes key, String value, long timeStamp, String style){
        this(key, value, timeStamp, DEFAULT_COL_SIZE, style);
    }

    public KeyMenu(NoteBytes key, String value, long timeStamp, int colSize, String style){
        super();
        m_colSize = colSize;
        m_key = key;
        m_value = value;
        m_timeStamp = timeStamp;
        m_style = style;
        update();
    }


    public KeyMenu(NoteBytes key, String value, long timeStamp){
        this(key, value, timeStamp, DEFAULT_COL_SIZE);
    }
    public KeyMenu(NoteBytes key, String value, long timeStamp, int colSize){
        super();
        m_colSize = colSize;
        m_key = key;
        m_value = value;
        m_timeStamp = timeStamp;
        update();
    }

    public void update(){
 
        m_style = m_style == null ? KEY_AND_VALUE : m_style;

        switch(m_style){
            case KEY_AND_VALUE:
                setText( String.format("%-"+m_colSize +"s", m_key + ": ") + String.format("%"+m_colSize +"s", m_value));
            break;
            case KEY_NOT_VALUE:
                setText( String.format("%-"+m_colSize +"s", m_key));
            break;
            case NOT_KEY_VALUE:
                setText(String.format("%"+m_colSize +"s", m_value));
            break;
            case VALUE_AND_KEY:
                setText(String.format("%"+m_colSize +"s", m_value + ": ") + String.format("%-"+m_colSize +"s", m_key));
            break;
            case VALUE_NOT_KEY:
                setText(String.format("%"+m_colSize +"s", m_value));
            break;   
        }
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

    public NoteBytes getKey(){
        return m_key;
    }

    public String getValue(){
        return m_value;
    }

    public static KeyMenu getKeyMenu(List<MenuItem> items, NoteBytes key){
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

    public static void removeOldKeyMenus(List<MenuItem> items, long timeStamp){
         ArrayList<NoteBytes> removeList  = new ArrayList<>();

        for(int i = 0; i < items.size(); i++){
            MenuItem item = items.get(i);
            if(item instanceof KeyMenu){
                KeyMenu keyItem = (KeyMenu) item;
                if(keyItem.getTimeStamp() < timeStamp){
                    removeList.add(keyItem.getKey());        
                }
            }
        }

        for(NoteBytes key : removeList){
            removeKeyMenu(items, key);
        }
    }

    public static KeyMenu removeKeyMenu(List<MenuItem> items, NoteBytes key){
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
