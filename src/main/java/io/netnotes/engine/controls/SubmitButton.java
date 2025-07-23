package io.netnotes.engine.controls;

import javafx.scene.control.Button;


import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;

public class SubmitButton extends Button {
    private EventHandler<WorkerStateEvent> m_onSubmit = null;
    private EventHandler<WorkerStateEvent> m_onError = null;


    public SubmitButton(String text){
        super(text);
    }

    public void setOnSubmit( EventHandler<WorkerStateEvent> onSubmit){
        m_onSubmit = onSubmit;
    }

    public EventHandler<WorkerStateEvent> getOnSubmit(){
        return m_onSubmit;
    }

    public void setOnError( EventHandler<WorkerStateEvent> onError){
        m_onError = onError;
    }

    public EventHandler<WorkerStateEvent> getOnError(){
        return m_onError;
    }
}
