package io.netnotes.engine;


import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.VBox;

public class AppBox extends VBox {
    
    private final String m_appId;
    private SimpleStringProperty m_titleString = new SimpleStringProperty();
    private final String m_name;

    public AppBox(String appId, String name){
        super();
        m_appId = appId;
        m_name = name;

        m_titleString.set(name);
        setId("darkBox");
    }




    public String getAppId(){
        return m_appId;
    }


    public SimpleStringProperty titleProperty(){
        return m_titleString;
    }

    public String getName(){
        return m_name;
    }


    public void shutdown(){
      
    }

    
}
