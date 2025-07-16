package io.netnotes.engine;


import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.scene.layout.VBox;

public class AppBox extends VBox {
    
    private final NoteBytes m_appId;
    private SimpleStringProperty m_titleString = new SimpleStringProperty();
    private final String m_name;

    public AppBox(NoteBytes appId, String name){
        super();
        m_appId = appId;
        m_name = name;

        m_titleString.set(name);
        setId("darkBox");
    }




    public NoteBytes getAppId(){
        return m_appId;
    }

    public boolean sendNote(NoteBytesObject note, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        return false;
    }

    public Object sendNote(NoteBytesObject note){
        return null;
    }

    public SimpleStringProperty titleProperty(){
        return m_titleString;
    }

    public String getName(){
        return m_name;
    }

    public void sendMessage(int code, long timeStamp, NoteBytes networkId, String str){
        
    }

    public void shutdown(){
      
    }

    
}
