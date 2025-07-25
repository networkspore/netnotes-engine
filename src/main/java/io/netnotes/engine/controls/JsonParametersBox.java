package io.netnotes.engine.controls;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;


import com.google.gson.JsonObject;

import io.netnotes.engine.Utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;

public class JsonParametersBox extends VBox{
    public final static double DEFAULT_COL_WIDTH = 160;

    private HashMap<String, TextInputControl> m_rows = new HashMap<>();
    private HashMap<String, JsonParametersBox> m_paramBoxes = new HashMap<>();
    private Pattern m_regexPattern = Pattern.compile("(?<=.)(?=(\\p{Upper}))");

    private SimpleDoubleProperty m_colWidth;
    private SimpleBooleanProperty m_isOpen = new SimpleBooleanProperty(false);

    public JsonParametersBox( JsonObject json){
        this(json, DEFAULT_COL_WIDTH);
    }

    public JsonParametersBox( JsonArray jsonArray){
        this(jsonArray, DEFAULT_COL_WIDTH);
    }

    public JsonParametersBox( JsonObject json, double colWidth){
        super();
        
        m_colWidth = new SimpleDoubleProperty(colWidth);
        
        setRows(json);
    } 

    public JsonParametersBox(JsonArray jsonArray, double colWidth){
        super();
        m_colWidth = new SimpleDoubleProperty(colWidth);
        setRows(jsonArray);
    }

    public JsonParametersBox(JsonArray jsonArray, SimpleDoubleProperty colWidth){
        super();
        m_colWidth = colWidth;
        setRows(jsonArray);
    }

    public JsonParametersBox(JsonObject jsonObject, SimpleDoubleProperty colWidth){
        super();
        m_colWidth = colWidth;
        setRows(jsonObject);
    }
    
    public SimpleBooleanProperty isOpenProperty(){
        return m_isOpen;
    }

    public HashMap<String, TextInputControl> getRows(){
        return m_rows;
    }

    public HashMap<String, JsonParametersBox> getParamBoxes(){
        return m_paramBoxes;
    }


    public void shutdown(){
        getChildren().clear();
        m_rows.clear();
        
        for(Map.Entry<String, JsonParametersBox> entry : m_paramBoxes.entrySet()){
            entry.getValue().shutdown();
        }

        m_paramBoxes.clear();
    }

    public double getColWidth(){
        return m_colWidth.get();
    }

    public void setColWidth( double width){
        m_colWidth.set(width);
    }


    public void setRows(JsonObject json){
  
        shutdown();
        if(json != null){
            for(Map.Entry<String, JsonElement> entry : json.entrySet()){
                JsonElement jsonElement = entry.getValue();
                String keyString = entry.getKey();
               
                setRow(true, keyString, jsonElement);
            }
        }
    }

    protected void setRow(boolean isJsonObject, String keyString, JsonElement jsonElement){
        int type = Utils.getJsonElementType(jsonElement);

        if(type > 0){

            
            Label nameText = new Label();
       

            if(isJsonObject){
                String[] names = m_regexPattern.split(keyString);
                nameText.setText( names[0].substring(0,1).toUpperCase() + names[0].substring(1));
                for(int i = 1 ; i < names.length ; i++){
                    nameText.setText(nameText.getText() + " " + names[i].substring(0,1).toUpperCase() + names[i].substring(1));
                }
            }else{

                nameText.setText(keyString);
            }

            HBox nodeNameBox = new HBox(nameText);
            nodeNameBox.setAlignment(Pos.TOP_LEFT);
            nodeNameBox.setPadding(new Insets(2,0,2,0));

            VBox rowBox = new VBox(nodeNameBox);
           
            switch(type){
                case 1:
                    Button logoBtnLbl = new Button("⏵");
                    logoBtnLbl.setId("caretBtn");

                    nodeNameBox.getChildren().add(0, logoBtnLbl);
                   
                    JsonParametersBox jsonBox = new JsonParametersBox(jsonElement.getAsJsonObject(), m_colWidth);
                    jsonBox.setPadding(new Insets(0,0,0,10));
                    m_paramBoxes.put(keyString, jsonBox);
                    
                    logoBtnLbl.setOnMouseClicked(e->{
                        jsonBox.isOpenProperty().set(!jsonBox.isOpenProperty().get());
                    });
                    jsonBox.isOpenProperty().addListener((obs,oldval,newval)->{
                        if(newval){
                            logoBtnLbl.setText("⏷");
                            if(!rowBox.getChildren().contains(jsonBox)){
                                rowBox.getChildren().add(jsonBox);
                            }
                        }else{
                            logoBtnLbl.setText("⏵");
                            if(rowBox.getChildren().contains(jsonBox)){
                                rowBox.getChildren().remove(jsonBox);
                            }
                        }
                    });
                break;
                case 2:
               
                    Button logoBtnLbl2 = new Button("⏵");
                    logoBtnLbl2.setId("caretBtn");

                    nodeNameBox.getChildren().add(0, logoBtnLbl2);
                
                    JsonParametersBox jsonArrayBox = new JsonParametersBox(jsonElement.getAsJsonArray(), m_colWidth);
                    jsonArrayBox.setPadding(new Insets(0,0,0,5));
                    m_paramBoxes.put(keyString, jsonArrayBox);

                    logoBtnLbl2.setOnMouseClicked(e->{
                        jsonArrayBox.isOpenProperty().set(!jsonArrayBox.isOpenProperty().get());
                    });
                    jsonArrayBox.isOpenProperty().addListener((obs,oldval,newval)->{
                        if(newval){
                            logoBtnLbl2.setText("⏷");
                            if(!rowBox.getChildren().contains(jsonArrayBox)){
                                rowBox.getChildren().add(jsonArrayBox);
                            }
                        }else{
                            logoBtnLbl2.setText("⏵");
                            if(rowBox.getChildren().contains(jsonArrayBox)){
                                rowBox.getChildren().remove(jsonArrayBox);
                            }
                        }
                    });
                break;
                case 3:
                    String textString = "";
                    nameText.maxWidthProperty().bind(m_colWidth);
                    nameText.minWidthProperty().bind(m_colWidth);
                    
                    switch(keyString){
                        case "timestamp":
                        case "timeStamp":
                           
                            textString = Utils.formatDateTimeString(Utils.milliToLocalTime(jsonElement.getAsLong()));
                        break;
                        default:
                           
                           textString = jsonElement.getAsString();
                            
                    }
                    HBox fieldBox = new HBox();
                    HBox.setHgrow(fieldBox, Priority.ALWAYS);
                    fieldBox.setId("bodyBox");
                    fieldBox.setAlignment(Pos.CENTER_LEFT);
                   

                    switch(keyString){
                        case "description":
                      
                            TextArea textArea = new TextArea(textString);
                            textArea.setEditable(false);
                            textArea.setId("textAreaInput");
                            textArea.setWrapText(true);
                            textArea.setMinHeight(70);
                            HBox.setHgrow(textArea, Priority.ALWAYS);
                        
                            m_rows.put(keyString, textArea);
                            fieldBox.getChildren().add(textArea);
                            fieldBox.setMinHeight(70);
                        break;
                        default:
                            TextField textField = new TextField(textString);
                            textField.setEditable(false);
                            HBox.setHgrow(textField, Priority.ALWAYS);
                        
                            m_rows.put(keyString, textField);
                            fieldBox.getChildren().add(textField);
                           // fieldBox.setPrefHeight(m_maxRowHeight);
                    }
                    
     

                    nodeNameBox.getChildren().add(fieldBox);
                    rowBox.setPadding(new Insets(0,0, 0,5));
                break;
            }
           

    
            getChildren().add(rowBox);
        }
    }


    public void setRows(JsonArray jsonArray){
  
        shutdown();
        if(jsonArray != null){
            for(int index = 0; index < jsonArray.size() ; index++){
                JsonElement jsonElement = jsonArray.get(index);
                String keyString = index + "";
                
                setRow(false, keyString, jsonElement);
            }
        }
    }



    public boolean parse(JsonObject json){
        for(Map.Entry<String, TextInputControl> entry : m_rows.entrySet()){
            String key = entry.getKey();
            if(json.get(key) == null){
                return true;
            }
        }
        for(Map.Entry<String, JsonParametersBox> entry : m_paramBoxes.entrySet()){
            String key = entry.getKey();
            if(json.get(key) == null){
                return true;
            }
        }
        for(Map.Entry<String, JsonElement> entry : json.entrySet()){
            JsonElement jsonElement = entry.getValue();
            String keyString = entry.getKey();

            int type = Utils.getJsonElementType(jsonElement);
            JsonParametersBox parametersBox;
            switch(type){
                case 1:
                    parametersBox =  m_paramBoxes.get(keyString);
                    if(parametersBox != null){
                        if(parametersBox.update( jsonElement.getAsJsonObject())){
                            return true;
                        }
                    }else{
                   
                        return true;
                    }
                break;
                case 2:
                    parametersBox =  m_paramBoxes.get(keyString);
                    if(parametersBox != null){

                        if(parametersBox.updateParameters(jsonElement.getAsJsonArray())){
                            return true;
                        }
                        
                       
                    }else{
                       
                        return true;
                    }
                break;
                case 3:
                    TextInputControl inputControl = m_rows.get(keyString);
                    if(inputControl == null){
                     
                        return true;
                    }
                    switch(keyString){
                        case "timeStamp":
                        case "timestamp":
                        case "currentTime":
                        case "launchTime":
                            inputControl.setText(Utils.formatDateTimeString(Utils.milliToLocalTime(jsonElement.getAsLong())));
                        break;
                        default:
                            inputControl.setText(jsonElement.getAsString());
                    }
                break;
            }
            
        }
        return false;
    }

    public boolean parse(JsonArray jsonArray){
        int size = jsonArray.size();
        if(m_rows.size() + m_paramBoxes.size() != size){
            return true;
        }


        for(int i = 0; i < size; i++ ){
            JsonElement jsonElement = jsonArray.get(i);
            String keyString = i + "";
            int type = Utils.getJsonElementType(jsonElement);
            JsonParametersBox parametersBox;
            switch(type){
                case 1:
                    parametersBox =  m_paramBoxes.get(keyString);
                    if(parametersBox != null){
                        if(parametersBox.update( jsonElement.getAsJsonObject())){
                            return true;
                        }
                    }else{
                   
                        return true;
                    }
                break;
                case 2:
                    parametersBox =  m_paramBoxes.get(keyString);
                    if(parametersBox != null){

                        if(parametersBox.updateParameters(jsonElement.getAsJsonArray())){
                            return true;
                        }
                        
                       
                    }else{
                       
                        return true;
                    }
                break;
                case 3:
                    TextInputControl textField = m_rows.get(keyString);
                    if(textField == null){
                     
                        return true;
                    }
                    switch(keyString){
                        case "timeStamp":
                        case "timestamp":
                        case "currentTime":
                        case "launchTime":
                            textField.setText(Utils.formatDateTimeString(Utils.milliToLocalTime(jsonElement.getAsLong())));
                        break;
                        default:
                            textField.setText(jsonElement.getAsString());
                    }
                break;
            }
            
        }
        return false;
    }

    public boolean update(JsonObject json){

        if(json != null){
         
                

            if(parse(json)){
              
                shutdown();
                setRows(json);
                return true;
            }else{
                return false;
            }
                
                
            
            
        }else{
            shutdown();
            return true;
        }

    }


    public boolean updateParameters(JsonArray jsonArray){

        if(jsonArray != null){
            if(parse(jsonArray)){
                shutdown();
                setRows(jsonArray);
                return true;
            }else{
                return false;
            }
        }else{
            shutdown();
            return true;
        }
    }


    public void openTopLevel(){
        for(Map.Entry<String, JsonParametersBox> entry : m_paramBoxes.entrySet()){
            JsonParametersBox paramBox = entry.getValue();
            paramBox.isOpenProperty().set(true);
        }
    }

    public void closeTopLevel(){
        for(Map.Entry<String, JsonParametersBox> entry : m_paramBoxes.entrySet()){
            JsonParametersBox paramBox = entry.getValue();
            paramBox.isOpenProperty().set(false);
        }
    }

    public void openAll(){
        for(Map.Entry<String, JsonParametersBox> entry : m_paramBoxes.entrySet()){
            JsonParametersBox paramBox = entry.getValue();
            paramBox.isOpenProperty().set(true);
            
            paramBox.openAll();
        }
    }

    public void closeAll(){
        for(Map.Entry<String, JsonParametersBox> entry : m_paramBoxes.entrySet()){
            JsonParametersBox paramBox = entry.getValue();
            paramBox.isOpenProperty().set(false);
            paramBox.closeAll();;
        }
    }
}
