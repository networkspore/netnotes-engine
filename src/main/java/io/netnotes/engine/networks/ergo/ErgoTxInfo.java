package io.netnotes.engine.networks.ergo;

import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;


public class ErgoTxInfo {
    private ErgoBoxInfo[] m_boxInfoArrays = new ErgoBoxInfo[0];
    private String m_txId;
    private long m_timeStamp = 0;



    public ErgoTxInfo(JsonReader reader) throws IOException{
        readJson(reader);
    }

    public ErgoTxInfo(JsonObject json){
        openJson(json);
    }

    public ErgoTxInfo(String txId, long timeStamp, ErgoBoxInfo[] boxInfoArray){
        m_txId = txId;
        m_boxInfoArrays = boxInfoArray;
        m_timeStamp = timeStamp;
    }



    public long getTimeStamp(){
        return m_timeStamp;
    }

    public String getTxId(){
        return m_txId;
    }

    public int getBoxSize(){
        return m_boxInfoArrays.length;
    }


    public void readJson(JsonReader reader) throws IOException{
        reader.beginObject();
        while(reader.hasNext()){
            switch(reader.nextName()){
                case "txId":
                    m_txId = reader.nextString();
                break;
                case "timeStamp":
                    m_timeStamp = reader.nextLong();
                break;
                case "boxes":
                    readBoxes(reader);
                break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }


    public void writeJson(JsonWriter writer) throws IOException{
        writer.beginObject();
        writer.name("txId");
        writer.value(m_txId);
        writer.name("boxes");
        writeBoxes(writer);
        writer.name("timeStamp");
        writer.value(m_timeStamp);
        writer.endObject();
    }

    public ErgoBoxInfo[] getBoxInfoArray(){
        return m_boxInfoArrays;
    }

    public void setBoxInfoArray(ErgoBoxInfo[] boxInfoArray){
        m_boxInfoArrays = boxInfoArray;
    }

    public void updateBoxInfo(ErgoBoxInfo boxInfo){
        int i = getBoxInfoIndex(boxInfo.getBoxId());
        if(i == -1){
            addBoxInfo(boxInfo);
        }else{
            m_boxInfoArrays[i] = boxInfo;
        }
    }

    private void addBoxInfo(ErgoBoxInfo boxInfo){
        ErgoBoxInfo[] boxInfoArray = m_boxInfoArrays;
        int len = boxInfoArray.length;
        ErgoBoxInfo[] newBoxInfoArray = new ErgoBoxInfo[len + 1];
        for(int i =0 ; i < len ; i++){
            newBoxInfoArray[i] = boxInfoArray[i];
        }
        newBoxInfoArray[len] = boxInfo;
        m_boxInfoArrays = newBoxInfoArray;
    }

    public ErgoBoxInfo getBoxInfo(String boxId){
        if(boxId != null){
            for(int i = 0; i < m_boxInfoArrays.length; i++){
                ErgoBoxInfo boxInfo = m_boxInfoArrays[i];
                if(boxInfo.getBoxId().equals(boxId)){
                    return boxInfo;
                }
            }
        }
        return null;
    }

    public int getBoxInfoIndex(String boxId){
        if(boxId != null){
            for(int i = 0; i < m_boxInfoArrays.length; i++){
                ErgoBoxInfo boxInfo = m_boxInfoArrays[i];
                if(boxInfo.getBoxId().equals(boxId)){
                    return i;
                }
            }
        }
        return -1;
    }

    public void readBoxes(JsonReader reader) throws IOException{
        reader.beginArray();
        ArrayList<ErgoBoxInfo> boxList = new ArrayList<>();
        while(reader.hasNext()){
            boxList.add(new ErgoBoxInfo(reader));
        }
        reader.endArray();
        m_boxInfoArrays = boxList.toArray(new ErgoBoxInfo[boxList.size()]);
    }

    public void writeBoxes(JsonWriter writer) throws IOException{
       
        writer.beginArray();
        for(ErgoBoxInfo boxInfo : m_boxInfoArrays){
            boxInfo.writeJson(writer);
        }
        writer.endArray();
    }

    protected void openJson(JsonObject json){
        JsonElement txIdElement = json.get("txId");
        JsonElement boxesElement = json.get("boxes");


        JsonElement timeStampElement = json.get("timeStamp");

        m_txId = txIdElement != null ? txIdElement.getAsString() : null;
        JsonArray boxArray = boxesElement != null && boxesElement.isJsonArray() ? boxesElement.getAsJsonArray() : null;
        if(boxArray != null){
            ArrayList<ErgoBoxInfo> boxList = new ArrayList<>();
            for(JsonElement boxElement : boxArray){
                boxList.add(new ErgoBoxInfo(boxElement.getAsJsonObject()));
            }
            m_boxInfoArrays = boxList.toArray(new ErgoBoxInfo[boxList.size()]);
        }

        m_timeStamp = timeStampElement != null ? timeStampElement.getAsLong() : 0;

     
    }

    public JsonArray getBoxesArray(){
        JsonArray boxesArray = new JsonArray();
        for(ErgoBoxInfo boxInfo : m_boxInfoArrays){
            boxesArray.add(boxInfo.getJsonObject());
        }
        return boxesArray;
    }

    public JsonObject getJsonObject(){
        
        JsonObject json = new JsonObject();
        json.addProperty("txId", m_txId);
        json.add("boxes", getBoxesArray());
        json.addProperty("timeStamp", m_timeStamp);
        return json;
    }

}
