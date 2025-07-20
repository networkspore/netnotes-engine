package io.netnotes.engine;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.stage.Stage;

public class TabAppBox extends AppBox{
    private Network m_network;
    private SimpleBooleanProperty m_isMinimized = new SimpleBooleanProperty(false);
    private Stage m_appStage;
    private SimpleDoubleProperty m_heightObject;
    private SimpleDoubleProperty m_widthObject;
    private Button m_appBtn = null;
    private MenuButton m_menuBtn = null;

    public TabAppBox(String id, String name, Stage appStage, SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, Button btn, Network network){
        super(id, name);
        m_network = network;
        m_appStage = appStage;
        m_heightObject = heightObject;
        m_widthObject = widthObject;
        m_appBtn = btn;
    }

     public TabAppBox(String id, String name,Stage appStage, SimpleDoubleProperty heightObject, SimpleDoubleProperty widthObject, MenuButton menuBtn, Network network){
        super(id, name );
        m_network = network;
        m_appStage = appStage;
        m_heightObject = heightObject;
        m_widthObject = widthObject;
        m_menuBtn = menuBtn;
    }


    public void setIsMinimized(boolean minimized){
        if(m_appBtn != null){
            if(minimized){
                m_appBtn.setId("menuTabBtn");
            }else{
                m_appBtn.setId("activeMenuBtn");
            }
        }
        if(m_menuBtn != null){
            if(minimized){
                m_menuBtn.setId("menuTabBtn");
            }else{
                m_menuBtn.setId("activeMenuBtn");
            }
        }
        m_isMinimized.set(minimized);
    }

    public boolean isMinimized(){
        return m_isMinimized.get();
    }

    protected ReadOnlyBooleanProperty isMinimizedProperty(){
        return m_isMinimized;
    }


    protected Stage getAppStage(){
        return m_appStage;
    }

    protected ReadOnlyDoubleProperty heightObject(){
        return m_heightObject;
    }

    protected ReadOnlyDoubleProperty widthObject(){
        return m_widthObject;
    }

    protected Button getMenuBtn(){
        return m_appBtn;
    }

 
    protected Network getNetwork(){
        return m_network;
    }
}
